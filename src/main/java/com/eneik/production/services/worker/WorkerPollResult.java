package com.eneik.production.services.worker;

import java.util.UUID;

public record WorkerPollResult(
        UUID sessionId,
        String status,
        String prUrl,
        boolean isCompleted,
        boolean isFailed,
        BelnapStatus belnapStatus,
        String errorMessage
) {
    public static WorkerPollResult inProgress(UUID sessionId, String status) {
        return new WorkerPollResult(sessionId, status, null, false, false, BelnapStatus.NEITHER, null);
    }

    public static WorkerPollResult success(UUID sessionId, String prUrl) {
        return new WorkerPollResult(sessionId, "pr_opened", prUrl, true, false, BelnapStatus.TRUE, null);
    }

    public static WorkerPollResult failed(UUID sessionId, String errorMessage) {
        return new WorkerPollResult(sessionId, "closed_no_code", null, false, true, BelnapStatus.FALSE, errorMessage);
    }

    public static WorkerPollResult conflict(UUID sessionId, String errorMessage) {
        return new WorkerPollResult(sessionId, "git_conflict_detected", null, false, false, BelnapStatus.BOTH, errorMessage);
    }
}
