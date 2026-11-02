package com.eneik.production.services.design.sovereign.telemetry;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain event capturing design engine telemetry data according to the Goal-Question-Metric framework.
 */
public record TelemetryEvent(
        UUID eventId,
        TelemetryEventType eventType,
        UUID sessionId,
        ProductArchetype archetype,
        int iterationCount,
        Double auditScore,
        Boolean deonticGatePassed,
        Instant timestamp,
        Map<String, String> metadata
) {
    public TelemetryEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
        if (iterationCount < 0) {
            throw new IllegalArgumentException("iterationCount cannot be negative");
        }
        if (auditScore != null && (auditScore < 0.0 || auditScore > 10.0)) {
            throw new IllegalArgumentException("auditScore must be between 0.0 and 10.0");
        }
    }

    public static TelemetryEvent ofSuccess(UUID eventId, UUID sessionId, ProductArchetype archetype, int iterationCount, double auditScore, Instant timestamp, Map<String, String> metadata) {
        return new TelemetryEvent(eventId, TelemetryEventType.GENERATION_SUCCESS, sessionId, archetype, iterationCount, auditScore, true, timestamp, metadata);
    }

    public static TelemetryEvent ofFailure(UUID eventId, UUID sessionId, ProductArchetype archetype, int iterationCount, Instant timestamp, Map<String, String> metadata) {
        return new TelemetryEvent(eventId, TelemetryEventType.GENERATION_FAILURE, sessionId, archetype, iterationCount, null, false, timestamp, metadata);
    }

    public static TelemetryEvent ofAuditCompleted(UUID eventId, UUID sessionId, ProductArchetype archetype, int iterationCount, double auditScore, boolean deonticGatePassed, Instant timestamp, Map<String, String> metadata) {
        return new TelemetryEvent(eventId, TelemetryEventType.AUDIT_COMPLETED, sessionId, archetype, iterationCount, auditScore, deonticGatePassed, timestamp, metadata);
    }
}
