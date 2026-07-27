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
        String recommendation
) {}
