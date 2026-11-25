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
            case JULES -> {
                log.warn("WorkerExecutionRouter: mode=JULES requested but Google Jules is decommissioned.");
                yield null;
            }
            case DEEPSEEK_WORKTREE -> deepSeekEngine;
            case LOCAL_AGY, LOCAL_AGY_CLI -> localAgyEngine;
            case HYBRID -> {
                // Sovereign Cranes (WO #13): Strictly DeepSeek -> LOCAL_AGY (Google Jules removed).
                if (hasAvailableDeepSeekAccountOrKey()) {
                    yield deepSeekEngine;
                } else if (localAgyEngine != null) {
                    yield localAgyEngine;
                } else {
                    log.warn("WorkerExecutionRouter: No active sovereign engines available (Google Jules decommissioned).");
                    yield null;
                }
            }
        };
    }

    /**
     * Dispatches task with sovereign Cranes Homeostasis:
     * DEEPSEEK -> LOCAL_AGY (Google Jules permanently removed from execution chain, WO #13).
     */
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        WorkerEngineType engineMode = resolveEngineMode();

        if (engineMode == WorkerEngineType.JULES) {
            log.warn("WorkerExecutionRouter: rejecting dispatch for task {} because GOOGLE_JULES_DECOMMISSIONED",
                    task != null ? task.getId() : null);
            return WorkerDispatchResult.failed(
                    "GOOGLE_JULES_DECOMMISSIONED: Google Jules is removed from the execution chain",
                    WorkerEngineType.JULES);
        }

        if (engineMode == WorkerEngineType.LOCAL_AGY || engineMode == WorkerEngineType.LOCAL_AGY_CLI) {
            if (localAgyEngine != null) {
                return localAgyEngine.dispatch(task, account, mode);
            }
            log.warn("LOCAL_AGY requested but engine bean is null (Google Jules decommissioned)");
            return WorkerDispatchResult.failed(
                    "NO_SOVEREIGN_WORKER_AVAILABLE: LOCAL_AGY engine is not available",
                    WorkerEngineType.LOCAL_AGY);
        }

        if (engineMode == WorkerEngineType.DEEPSEEK_WORKTREE) {
            return deepSeekEngine.dispatch(task, account, mode);
        }

        // HYBRID MODE with sovereign Cranes Homeostasis: DeepSeek -> LOCAL_AGY (never Google Jules)
        if (hasAvailableDeepSeekAccountOrKey()) {
            AccountEntity targetAccount = account != null ? account : findAvailableDeepSeekAccount().orElse(null);
            try {
                log.info("WorkerExecutionRouter (HYBRID): Attempting dispatch via sovereign DeepSeek Worktree...");
                return deepSeekEngine.dispatch(task, targetAccount, mode);
            } catch (DeepSeekQuotaExhaustedException e) {
                log.warn("Cranes Homeostasis triggered! DeepSeek account {} quota/rate limit error ({}): {}. Falling back to LOCAL_AGY...",
                        targetAccount != null ? targetAccount.getName() : "default", e.getStatusCode(), e.getMessage());

                if (targetAccount != null) {
                    targetAccount.setStatus(AccountStatus.api_blocked);
                    targetAccount.setStatusChangedAt(java.time.Instant.now());
                    accountRepository.save(targetAccount);
                }

                // Sovereign failover to LOCAL_AGY only (never Jules)
                return dispatchLocalAgyOnly(task, account, mode, "DeepSeek quota exhausted (" + e.getStatusCode() + ")");
            } catch (Exception e) {
                log.warn("DeepSeek execution encountered error: {}. Cranes fallback to LOCAL_AGY...", e.getMessage());
                return dispatchLocalAgyOnly(task, account, mode, "DeepSeek error: " + e.getMessage());
            }
        }

        // No DeepSeek keys -> directly engage zero-cost sovereign LOCAL_AGY
        return dispatchLocalAgyOnly(task, account, mode, "DeepSeek unavailable");
    }

    private WorkerDispatchResult dispatchLocalAgyOnly(TaskEntity task, AccountEntity account, String mode, String priorReason) {
        if (localAgyEngine != null) {
            try {
                log.info("WorkerExecutionRouter: Dispatching via Zero-Cost LOCAL_AGY sovereign engine...");
                WorkerDispatchResult agyResult = localAgyEngine.dispatch(task, account, mode);
                if (agyResult != null && agyResult.success()) {
                    return agyResult;
                }
                String agyErr = agyResult != null && agyResult.errorMessage() != null
                        ? agyResult.errorMessage()
                        : "LOCAL_AGY returned unconfirmed/failed result";
                log.warn("LOCAL_AGY dispatch failed ({}); Google Jules is decommissioned — releasing task to queue.", agyErr);
                return WorkerDispatchResult.failed(
                        "NO_SOVEREIGN_WORKER_AVAILABLE: " + priorReason + "; LOCAL_AGY failed: " + agyErr,
                        WorkerEngineType.LOCAL_AGY);
            } catch (Exception e) {
                log.warn("LOCAL_AGY encountered exception ({}); Google Jules is decommissioned — releasing task to queue.", e.getMessage());
                return WorkerDispatchResult.failed(
                        "NO_SOVEREIGN_WORKER_AVAILABLE: " + priorReason + "; LOCAL_AGY exception: " + e.getMessage(),
                        WorkerEngineType.LOCAL_AGY);
            }
        }
        log.warn("WorkerExecutionRouter: No sovereign worker available ({}; LOCAL_AGY bean is null). Google Jules is decommissioned.", priorReason);
        return WorkerDispatchResult.failed(
                "NO_SOVEREIGN_WORKER_AVAILABLE: " + priorReason + "; LOCAL_AGY engine is not available",
                WorkerEngineType.LOCAL_AGY);
    }

    public WorkerPollResult poll(UUID sessionId) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            String engine = session.getWorkerEngine();
            if ("DEEPSEEK_WORKTREE".equalsIgnoreCase(engine)) {
                return deepSeekEngine.poll(sessionId);
            } else if ("LOCAL_AGY".equalsIgnoreCase(engine) || "LOCAL_AGY_CLI".equalsIgnoreCase(engine)) {
                return localAgyEngine != null
                        ? localAgyEngine.poll(sessionId)
                        : WorkerPollResult.failed(sessionId, "LOCAL_AGY_UNAVAILABLE");
            }
        }
        return WorkerPollResult.failed(sessionId, "GOOGLE_JULES_DECOMMISSIONED");
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
        log.info("WorkerExecutionRouter.cancel: skipping remote Jules cancel for session {} (GOOGLE_JULES_DECOMMISSIONED)", sessionId);
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
