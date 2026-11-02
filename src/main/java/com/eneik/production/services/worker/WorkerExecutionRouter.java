package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.agy.LocalAgyWorktreeExecutionEngine;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Worker Execution Router with Dennett's 'Cranes, not Skyhooks' Homeostasis.
 * Dynamically resolves engines across three sovereign and reserve tiers:
 *   Tier 1: Zero-Cost Sovereign LOCAL_AGY (remotecli3 / root profiles via agy CLI)
 *   Tier 2: High-Reasoning Sovereign DEEPSEEK (V3/R1 with Context Caching)
 *   Tier 3: External Reserve GOOGLE_JULES (pool of Jules accounts)
 * Catches 401/402/429/503 errors and transparently fails over without stalling the factory.
 */
@Service
public class WorkerExecutionRouter {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutionRouter.class);

    private final DeepSeekWorktreeExecutionEngine deepSeekEngine;
    private final JulesExecutionEngine julesEngine;
    private final LocalAgyWorktreeExecutionEngine localAgyEngine;
    private final SystemSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final JulesSessionRepository sessionRepository;

    @Autowired
    public WorkerExecutionRouter(
            DeepSeekWorktreeExecutionEngine deepSeekEngine,
            JulesExecutionEngine julesEngine,
            @Autowired(required = false) LocalAgyWorktreeExecutionEngine localAgyEngine,
            SystemSettingsService settingsService,
            AccountRepository accountRepository,
            JulesSessionRepository sessionRepository) {
        this.deepSeekEngine = deepSeekEngine;
        this.julesEngine = julesEngine;
        this.localAgyEngine = localAgyEngine;
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
    }

    public WorkerExecutionRouter(
            DeepSeekWorktreeExecutionEngine deepSeekEngine,
            JulesExecutionEngine julesEngine,
            SystemSettingsService settingsService,
            AccountRepository accountRepository,
            JulesSessionRepository sessionRepository) {
        this(deepSeekEngine, julesEngine, null, settingsService, accountRepository, sessionRepository);
    }

    public WorkerEngineType resolveEngineMode() {
        if (settingsService != null) {
            String modeStr = settingsService.effectiveValue("worker_engine_mode");
            if (modeStr != null && !modeStr.isBlank()) {
                return WorkerEngineType.parse(modeStr, WorkerEngineType.HYBRID);
            }
        }
        String envMode = System.getenv("WORKER_ENGINE_MODE");
        return WorkerEngineType.parse(envMode, WorkerEngineType.HYBRID);
    }

    public WorkerExecutionEngine resolveEngine(TaskEntity task) {
        WorkerEngineType mode = resolveEngineMode();
        return switch (mode) {
            case JULES -> julesEngine;
            case DEEPSEEK_WORKTREE -> deepSeekEngine;
            case LOCAL_AGY, LOCAL_AGY_CLI -> (localAgyEngine != null ? localAgyEngine : julesEngine);
            case HYBRID -> {
                // Dennett's Cranes: Prefer sovereign DeepSeek if key available, else LOCAL_AGY, fallback to Jules
                if (hasAvailableDeepSeekAccountOrKey()) {
                    yield deepSeekEngine;
                } else if (localAgyEngine != null) {
                    yield localAgyEngine;
                } else {
                    log.info("WorkerExecutionRouter: No active sovereign engines; routing to Jules");
                    yield julesEngine;
                }
            }
        };
    }

    /**
     * Dispatches task with Cranes Homeostasis across tiers:
     * LOCAL_AGY <-> DEEPSEEK <-> GOOGLE_JULES.
     */
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        WorkerEngineType engineMode = resolveEngineMode();

        if (engineMode == WorkerEngineType.JULES) {
            return julesEngine.dispatch(task, account, mode);
        }

        if (engineMode == WorkerEngineType.LOCAL_AGY || engineMode == WorkerEngineType.LOCAL_AGY_CLI) {
            if (localAgyEngine != null) {
                return localAgyEngine.dispatch(task, account, mode);
            }
            log.warn("LOCAL_AGY requested but engine bean is null, falling back to Jules");
            return julesEngine.dispatch(task, account, mode);
        }

        if (engineMode == WorkerEngineType.DEEPSEEK_WORKTREE) {
            return deepSeekEngine.dispatch(task, account, mode);
        }

        // HYBRID MODE with Cranes, not Skyhooks Homeostasis
        if (hasAvailableDeepSeekAccountOrKey()) {
            AccountEntity targetAccount = account != null ? account : findAvailableDeepSeekAccount().orElse(null);
            try {
                log.info("WorkerExecutionRouter (HYBRID): Attempting dispatch via sovereign DeepSeek Worktree...");
                return deepSeekEngine.dispatch(task, targetAccount, mode);
            } catch (DeepSeekQuotaExhaustedException e) {
                log.warn("Cranes Homeostasis triggered! DeepSeek account {} quota/rate limit error ({}): {}. Falling back to Tier 1 LOCAL_AGY...",
                        targetAccount != null ? targetAccount.getName() : "default", e.getStatusCode(), e.getMessage());

                if (targetAccount != null) {
                    targetAccount.setStatus(AccountStatus.api_blocked);
                    targetAccount.setStatusChangedAt(java.time.Instant.now());
                    accountRepository.save(targetAccount);
                }

                // Seamless failover to Tier 1 (LOCAL_AGY) or Tier 3 (Jules)
                return dispatchLocalAgyOrJules(task, account, mode);
            } catch (Exception e) {
                log.warn("DeepSeek execution encountered error: {}. Cranes fallback to Tier 1 LOCAL_AGY...", e.getMessage());
                return dispatchLocalAgyOrJules(task, account, mode);
            }
        }

        // No DeepSeek keys -> directly engage zero-cost sovereign Tier 1 (LOCAL_AGY)
        return dispatchLocalAgyOrJules(task, account, mode);
    }

    private WorkerDispatchResult dispatchLocalAgyOrJules(TaskEntity task, AccountEntity account, String mode) {
        if (localAgyEngine != null) {
            try {
                log.info("WorkerExecutionRouter: Dispatching via Zero-Cost LOCAL_AGY sovereign engine...");
                WorkerDispatchResult agyResult = localAgyEngine.dispatch(task, account, mode);
                if (agyResult != null && agyResult.success()) {
                    return agyResult;
                }
                log.warn("LOCAL_AGY dispatch returned failure/unconfirmed. Falling back to Jules reserve...");
            } catch (Exception e) {
                log.warn("LOCAL_AGY encountered exception ({}). Cranes fallback to Jules reserve...", e.getMessage());
            }
        }
        log.info("WorkerExecutionRouter: Dispatching via GOOGLE_JULES cloud reserve...");
        return julesEngine.dispatch(task, null, mode);
    }

    public WorkerPollResult poll(UUID sessionId) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            String engine = session.getWorkerEngine();
            if ("DEEPSEEK_WORKTREE".equalsIgnoreCase(engine)) {
                return deepSeekEngine.poll(sessionId);
            } else if ("LOCAL_AGY".equalsIgnoreCase(engine) || "LOCAL_AGY_CLI".equalsIgnoreCase(engine)) {
                return localAgyEngine != null ? localAgyEngine.poll(sessionId) : julesEngine.poll(sessionId);
            }
        }
        return julesEngine.poll(sessionId);
    }

    public void cancel(UUID sessionId, String reason) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            String engine = session.getWorkerEngine();
            if ("DEEPSEEK_WORKTREE".equalsIgnoreCase(engine)) {
                deepSeekEngine.cancel(sessionId, reason);
                return;
            } else if ("LOCAL_AGY".equalsIgnoreCase(engine) || "LOCAL_AGY_CLI".equalsIgnoreCase(engine)) {
                if (localAgyEngine != null) {
                    localAgyEngine.cancel(sessionId, reason);
                    return;
                }
            }
        }
        julesEngine.cancel(sessionId, reason);
    }

    public boolean hasAvailableDeepSeekAccountOrKey() {
        if (settingsService != null) {
            String key = settingsService.effectiveValue("deepseek_api_key");
            if (key != null && !key.isBlank()) {
                return true;
            }
        }
        if (System.getenv("DEEPSEEK_API_KEY") != null && !System.getenv("DEEPSEEK_API_KEY").isBlank()) {
            return true;
        }
        return findAvailableDeepSeekAccount().isPresent();
    }

    public Optional<AccountEntity> findAvailableDeepSeekAccount() {
        return accountRepository.findAll().stream()
                .filter(a -> "DEEPSEEK".equalsIgnoreCase(a.getProvider()))
                .filter(AccountEntity::isEnabled)
                .filter(a -> a.getStatus() == AccountStatus.idle)
                .findFirst();
    }
}
