package com.eneik.production.services.design.sovereign.telemetry;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service boundary for collecting, recording, and aggregating telemetry metrics
 * for the Autonomous Sovereign Designer Engine.
 */
public interface DesignTelemetryCollector {

    /**
     * Records a generation success event.
     */
    TelemetryEvent recordGenerationSuccess(UUID sessionId, ProductArchetype archetype, int iterationCount, double auditScore, Map<String, String> metadata);

    /**
     * Records a generation failure event.
     */
    TelemetryEvent recordGenerationFailure(UUID sessionId, ProductArchetype archetype, int iterationCount, String failureReason, Map<String, String> metadata);

    /**
     * Records a visual audit completion event.
     */
    TelemetryEvent recordAuditCompleted(UUID sessionId, ProductArchetype archetype, int roundIndex, double auditScore, boolean deonticGatePassed, Map<String, String> metadata);

    /**
     * Computes and returns the GQM telemetry summary across all recorded events.
     */
    DesignTelemetrySummary getSummary();

    /**
     * Returns an unmodifiable list of all recorded telemetry events.
     */
    List<TelemetryEvent> getRecordedEvents();

    /**
     * Clears all recorded events (primarily for testing/reset).
     */
    void clear();
}
