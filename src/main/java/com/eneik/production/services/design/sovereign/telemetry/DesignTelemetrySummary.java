package com.eneik.production.services.design.sovereign.telemetry;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;

import java.util.Collections;
import java.util.Map;

/**
 * Top-level telemetry summary representing overall design engine GQM framework metrics.
 */
public record DesignTelemetrySummary(
        long totalRequests,
        long successCount,
        long failureCount,
        long auditCount,
        double overallSuccessRate,
        double averageIterations,
        double averageAuditScore,
        Map<ProductArchetype, ArchetypeTelemetrySummary> archetypeSummaries
) {
    public DesignTelemetrySummary {
        archetypeSummaries = archetypeSummaries == null ? Collections.emptyMap() : Map.copyOf(archetypeSummaries);
    }
}
