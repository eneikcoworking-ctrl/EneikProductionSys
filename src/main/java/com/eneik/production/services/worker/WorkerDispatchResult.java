package com.eneik.production.services.worker;

import java.util.UUID;

public record WorkerDispatchResult(
        boolean success,
        UUID sessionId,
        String externalSessionId,
        String status,
        String errorMessage,
        WorkerEngineType engineType,
        String prUrl,
        BelnapStatus belnapStatus
) {
    public static WorkerDispatchResult ok(UUID sessionId, String externalSessionId, WorkerEngineType engineType, String prUrl) {
        return new WorkerDispatchResult(true, sessionId, externalSessionId, "running", null, engineType, prUrl, BelnapStatus.NEITHER);
    }

    public static WorkerDispatchResult completed(UUID sessionId, String externalSessionId, WorkerEngineType engineType, String prUrl) {
        return new WorkerDispatchResult(true, sessionId, externalSessionId, "pr_opened", null, engineType, prUrl, BelnapStatus.TRUE);
    }

    public static WorkerDispatchResult failed(String errorMessage, WorkerEngineType engineType) {
        return new WorkerDispatchResult(false, null, null, "failed", errorMessage, engineType, null, BelnapStatus.FALSE);
    }

    public static WorkerDispatchResult failed(UUID sessionId, String errorMessage, WorkerEngineType engineType) {
        return new WorkerDispatchResult(false, sessionId, null, "failed", errorMessage, engineType, null, BelnapStatus.FALSE);
    }
}
