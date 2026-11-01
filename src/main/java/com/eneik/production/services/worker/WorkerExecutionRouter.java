package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Worker Execution Router with Dennett's 'Cranes, not Skyhooks' Homeostasis.
 * Dynamically resolves engines between JULES, DEEPSEEK_WORKTREE, and HYBRID mode.
 * Catches 401/402/429/503 errors and transparently fails over without stalling the factory.
 */
@Service
public class WorkerExecutionRouter {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutionRouter.class);

    private final DeepSeekWorktreeExecutionEngine deepSeekEngine;
    private final JulesExecutionEngine julesEngine;
    private final SystemSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final JulesSessionRepository sessionRepository;

    public WorkerExecutionRouter(
            DeepSeekWorktreeExecutionEngine deepSeekEngine,
            JulesExecutionEngine julesEngine,
            SystemSettingsService settingsService,
            AccountRepository accountRepository,
            JulesSessionRepository sessionRepository) {
        this.deepSeekEngine = deepSeekEngine;
        this.julesEngine = julesEngine;
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
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
            case HYBRID -> {
                // Dennett's Cranes: Prefer sovereign DeepSeek if available, fallback to Jules
                if (hasAvailableDeepSeekAccountOrKey()) {
                    yield deepSeekEngine;
                } else {
                    log.info("WorkerExecutionRouter: No active DeepSeek credentials in pool; routing to Jules");
                    yield julesEngine;
                }
            }
        };
    }

    /**
     * Dispatches task with Cranes Homeostasis: upon 401/402/429/503 DeepSeek failures,
     * marks account in cooldown and falls back seamlessly to alternative engine.
     */
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        WorkerEngineType engineMode = resolveEngineMode();

        if (engineMode == WorkerEngineType.JULES) {
            return julesEngine.dispatch(task, account, mode);
        }

        if (engineMode == WorkerEngineType.DEEPSEEK_WORKTREE) {
            return deepSeekEngine.dispatch(task, account, mode);
        }

        // HYBRID MODE with Cranes, not Skyhooks Homeostasis
        AccountEntity targetAccount = account != null ? account : findAvailableDeepSeekAccount().orElse(null);
        try {
            log.info("WorkerExecutionRouter (HYBRID): Attempting dispatch via sovereign DeepSeek Worktree...");
            return deepSeekEngine.dispatch(task, targetAccount, mode);
        } catch (DeepSeekQuotaExhaustedException e) {
            log.warn("Cranes Homeostasis triggered! DeepSeek account {} quota/rate limit error ({}): {}. Falling back to Jules...",
                    targetAccount != null ? targetAccount.getName() : "default", e.getStatusCode(), e.getMessage());

            if (targetAccount != null) {
                targetAccount.setStatus(AccountStatus.api_blocked);
                targetAccount.setStatusChangedAt(java.time.Instant.now());
                accountRepository.save(targetAccount);
            }

            // Seamless failover to Jules engine
            return julesEngine.dispatch(task, null, mode);
        } catch (Exception e) {
            log.warn("DeepSeek execution encountered error: {}. Cranes fallback to Jules...", e.getMessage());
            return julesEngine.dispatch(task, null, mode);
        }
    }

    public WorkerPollResult poll(UUID sessionId) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            if ("DEEPSEEK_WORKTREE".equalsIgnoreCase(session.getWorkerEngine())) {
                return deepSeekEngine.poll(sessionId);
            }
        }
        return julesEngine.poll(sessionId);
    }

    public void cancel(UUID sessionId, String reason) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            if ("DEEPSEEK_WORKTREE".equalsIgnoreCase(session.getWorkerEngine())) {
                deepSeekEngine.cancel(sessionId, reason);
                return;
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
                .filter(a -> a.getStatus() == AccountStatus.idle && a.isEnabled() && a.getApiKey() != null && !a.getApiKey().isBlank())
                .findFirst();
    }
}
