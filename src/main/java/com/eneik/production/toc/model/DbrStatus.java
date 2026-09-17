package com.eneik.production.toc.model;

import java.time.Instant;

/**
 * Record describing current Drum-Buffer-Rope operational status.
 */
public record DbrStatus(
        String primaryConstraintNode,
        long constraintQueueLength,
        double constraintUtilization,
        double constraintMeanDurationMs,
        long bufferSize,
        long maxBufferCapacity,
        boolean ropeThrottlingActive,
        Instant lastEvaluatedAt,
        String recommendation,
        long throttleActivations
) {
    public DbrStatus(
            String primaryConstraintNode,
            long constraintQueueLength,
            double constraintUtilization,
            double constraintMeanDurationMs,
            long bufferSize,
            long maxBufferCapacity,
            boolean ropeThrottlingActive,
            Instant lastEvaluatedAt,
            String recommendation
    ) {
        this(primaryConstraintNode, constraintQueueLength, constraintUtilization,
                constraintMeanDurationMs, bufferSize, maxBufferCapacity,
                ropeThrottlingActive, lastEvaluatedAt, recommendation, 0L);
    }

    public boolean isLimiterVerified() {
        return throttleActivations > 0;
    }

    public String limiterStatus() {
        return throttleActivations > 0
                ? "VERIFIED (" + throttleActivations + " throttle events observed)"
                : "UNVERIFIED (0 throttle events observed; limiter has never been tested against overload under ALFRED_TARSKIY_01_FALSIFICATION_HARNESS)";
    }
}
