package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Adapter wrapping legacy JulesDispatchService as a WorkerExecutionEngine.
 */
@Service
public class JulesExecutionEngine implements WorkerExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(JulesExecutionEngine.class);

    private final JulesDispatchService julesDispatchService;

    public JulesExecutionEngine(@org.springframework.context.annotation.Lazy JulesDispatchService julesDispatchService) {
        this.julesDispatchService = julesDispatchService;
    }

    @Override
    public WorkerEngineType getEngineType() {
        return WorkerEngineType.JULES;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return true;
    }

    @Override
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        UUID accountId = account != null ? account.getId() : null;
        log.info("JulesExecutionEngine: delegating task {} to Jules cloud (account {})", task.getId(), accountId);
        JulesDispatchResult result = julesDispatchService.dispatch(task, accountId, mode);

        if (result.dispatched()) {
            return WorkerDispatchResult.ok(null, result.sessionName(), WorkerEngineType.JULES, null);
        } else {
            return WorkerDispatchResult.failed(result.reason(), WorkerEngineType.JULES);
        }
    }

    @Override
    public WorkerPollResult poll(UUID sessionId) {
        JulesSessionEntity session = julesDispatchService.pollStatus(sessionId);
        if (session == null) {
            return WorkerPollResult.failed(sessionId, "Session not found");
        }
        String status = session.getStatus();
        if ("pr_opened".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            return WorkerPollResult.success(sessionId, session.getPrUrl());
        }
        if ("failed".equalsIgnoreCase(status) || "closed_no_code".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            return WorkerPollResult.failed(sessionId, session.getClosureReason());
        }
        return WorkerPollResult.inProgress(sessionId, status);
    }

    @Override
    public void cancel(UUID sessionId, String reason) {
        julesDispatchService.cancelSession(sessionId, reason);
    }
}
