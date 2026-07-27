package com.eneik.production.toc.model;

import java.time.Instant;

/**
 * Record describing an anomaly detected by TOC Sentinel Service.
 */
public record AnomalyReport(
        String id,
        AnomalyType type,
        String tokenId,
        String nodeName,
        String resourceId,
        String details,
        String actionTaken,
        Instant timestamp
) {
    public enum AnomalyType {
        CYCLE_DETECTED,
        STALL_DETECTED,
        DEADLOCK_DETECTED,
        BUFFER_OVERFLOW
    }
}
