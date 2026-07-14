package com.eneik.production.services;

public class OrchestrationCooldownException extends RuntimeException {
    private final long retryAfterSeconds;

    public OrchestrationCooldownException(long retryAfterSeconds) {
        super("Orchestrate is rate-limited. Retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
