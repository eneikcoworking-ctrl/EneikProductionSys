package com.eneik.production.services.worker.deepseek;

/**
 * Thrown when DeepSeek API returns auth/quota/rate-limit errors (401, 402, 429).
 * Drives Dennett's 'Cranes, not Skyhooks' homeostasis to switch accounts or fallback to Jules.
 */
public class DeepSeekQuotaExhaustedException extends RuntimeException {
    private final int statusCode;

    public DeepSeekQuotaExhaustedException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
