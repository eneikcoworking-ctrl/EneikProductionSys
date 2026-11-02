package com.eneik.production.services.design.sovereign.telemetry;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Thread-safe, in-memory implementation of {@link DesignTelemetryCollector}.
 * Supports seedable/injectable time source (Clock or Instant Supplier) for reproducible tests.
 */
@Component
public class InMemoryDesignTelemetryCollector implements DesignTelemetryCollector {

    private final ConcurrentLinkedQueue<TelemetryEvent> events;
    private final Supplier<Instant> clockSupplier;
    private final Supplier<UUID> uuidSupplier;

    public InMemoryDesignTelemetryCollector() {
        this(Clock.systemUTC());
    }

    public InMemoryDesignTelemetryCollector(Clock clock) {
        this(Objects.requireNonNull(clock, "clock cannot be null")::instant, UUID::randomUUID);
    }

    public InMemoryDesignTelemetryCollector(Supplier<Instant> clockSupplier, Supplier<UUID> uuidSupplier) {
        this.events = new ConcurrentLinkedQueue<>();
        this.clockSupplier = Objects.requireNonNull(clockSupplier, "clockSupplier cannot be null");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier cannot be null");
    }

    @Override
    public TelemetryEvent recordGenerationSuccess(UUID sessionId, ProductArchetype archetype, int iterationCount, double auditScore, Map<String, String> metadata) {
        UUID eventId = uuidSupplier.get();
        Instant now = clockSupplier.get();
        TelemetryEvent event = TelemetryEvent.ofSuccess(eventId, sessionId, archetype, iterationCount, auditScore, now, metadata);
        events.add(event);
        return event;
    }

    @Override
    public TelemetryEvent recordGenerationFailure(UUID sessionId, ProductArchetype archetype, int iterationCount, String failureReason, Map<String, String> metadata) {
        UUID eventId = uuidSupplier.get();
        Instant now = clockSupplier.get();
        Map<String, String> mergedMeta = new HashMap<>();
        if (metadata != null) {
            mergedMeta.putAll(metadata);
        }
        if (failureReason != null) {
            mergedMeta.put("failureReason", failureReason);
        }
        TelemetryEvent event = TelemetryEvent.ofFailure(eventId, sessionId, archetype, iterationCount, now, mergedMeta);
        events.add(event);
        return event;
    }

    @Override
    public TelemetryEvent recordAuditCompleted(UUID sessionId, ProductArchetype archetype, int roundIndex, double auditScore, boolean deonticGatePassed, Map<String, String> metadata) {
        UUID eventId = uuidSupplier.get();
        Instant now = clockSupplier.get();
        TelemetryEvent event = TelemetryEvent.ofAuditCompleted(eventId, sessionId, archetype, roundIndex, auditScore, deonticGatePassed, now, metadata);
        events.add(event);
        return event;
    }

    @Override
    public List<TelemetryEvent> getRecordedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    @Override
    public void clear() {
        events.clear();
    }

    @Override
    public DesignTelemetrySummary getSummary() {
        List<TelemetryEvent> currentEvents = new ArrayList<>(events);

        long totalRequests = 0;
        long successCount = 0;
        long failureCount = 0;
        long auditCount = 0;

        double sumIterations = 0.0;
        long iterationCountEvents = 0;

        double sumAuditScore = 0.0;
        long auditScoreEvents = 0;

        Map<ProductArchetype, ArchetypeStats> archetypeStatsMap = new EnumMap<>(ProductArchetype.class);

        for (TelemetryEvent event : currentEvents) {
            ProductArchetype arch = event.archetype();

            ArchetypeStats stats = archetypeStatsMap.computeIfAbsent(arch, ArchetypeStats::new);

            if (event.eventType() == TelemetryEventType.GENERATION_SUCCESS) {
                totalRequests++;
                successCount++;
                stats.totalRequests++;
                stats.successCount++;

                sumIterations += event.iterationCount();
                iterationCountEvents++;
                stats.sumIterations += event.iterationCount();
                stats.iterationEventsCount++;

                if (event.auditScore() != null) {
                    sumAuditScore += event.auditScore();
                    auditScoreEvents++;
                    stats.sumAuditScore += event.auditScore();
                    stats.auditScoreEventsCount++;
                }
            } else if (event.eventType() == TelemetryEventType.GENERATION_FAILURE) {
                totalRequests++;
                failureCount++;
                stats.totalRequests++;
                stats.failureCount++;

                sumIterations += event.iterationCount();
                iterationCountEvents++;
                stats.sumIterations += event.iterationCount();
                stats.iterationEventsCount++;
            } else if (event.eventType() == TelemetryEventType.AUDIT_COMPLETED) {
                auditCount++;
                stats.auditCount++;

                if (event.auditScore() != null) {
                    sumAuditScore += event.auditScore();
                    auditScoreEvents++;
                    stats.sumAuditScore += event.auditScore();
                    stats.auditScoreEventsCount++;
                }
            }
        }

        double overallSuccessRate = totalRequests > 0 ? (double) successCount / totalRequests : 0.0;
        double averageIterations = iterationCountEvents > 0 ? sumIterations / iterationCountEvents : 0.0;
        double averageAuditScore = auditScoreEvents > 0 ? sumAuditScore / auditScoreEvents : 0.0;

        Map<ProductArchetype, ArchetypeTelemetrySummary> archetypeSummaries = new EnumMap<>(ProductArchetype.class);
        for (Map.Entry<ProductArchetype, ArchetypeStats> entry : archetypeStatsMap.entrySet()) {
            ArchetypeStats st = entry.getValue();
            double archSuccessRate = st.totalRequests > 0 ? (double) st.successCount / st.totalRequests : 0.0;
            double archAvgIterations = st.iterationEventsCount > 0 ? st.sumIterations / st.iterationEventsCount : 0.0;
            double archAvgAuditScore = st.auditScoreEventsCount > 0 ? st.sumAuditScore / st.auditScoreEventsCount : 0.0;

            archetypeSummaries.put(entry.getKey(), new ArchetypeTelemetrySummary(
                    entry.getKey(),
                    st.totalRequests,
                    st.successCount,
                    st.failureCount,
                    st.auditCount,
                    archSuccessRate,
                    archAvgIterations,
                    archAvgAuditScore
            ));
        }

        return new DesignTelemetrySummary(
                totalRequests,
                successCount,
                failureCount,
                auditCount,
                overallSuccessRate,
                averageIterations,
                averageAuditScore,
                archetypeSummaries
        );
    }

    private static class ArchetypeStats {
        final ProductArchetype archetype;
        long totalRequests = 0;
        long successCount = 0;
        long failureCount = 0;
        long auditCount = 0;
        double sumIterations = 0.0;
        long iterationEventsCount = 0;
        double sumAuditScore = 0.0;
        long auditScoreEventsCount = 0;

        ArchetypeStats(ProductArchetype archetype) {
            this.archetype = archetype;
        }
    }
}
