package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.TaskEntity;

import java.util.UUID;

/**
 * Unified execution engine contract (GEMINI.md §0).
 * Provides polymorphism between cloud Jules and sovereign DeepSeek Worktree engines.
 */
public interface WorkerExecutionEngine {

    WorkerEngineType getEngineType();

    boolean supports(TaskEntity task);

    WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode);

    WorkerPollResult poll(UUID sessionId);

    void cancel(UUID sessionId, String reason);
}
