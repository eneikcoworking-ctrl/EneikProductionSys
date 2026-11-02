package com.eneik.production.services.design.sovereign;

import com.eneik.production.services.design.sovereign.domain.ProductArchetype;
import com.eneik.production.services.design.sovereign.telemetry.ArchetypeTelemetrySummary;
import com.eneik.production.services.design.sovereign.telemetry.DesignTelemetryCollector;
import com.eneik.production.services.design.sovereign.telemetry.DesignTelemetrySummary;
import com.eneik.production.services.design.sovereign.telemetry.InMemoryDesignTelemetryCollector;
import com.eneik.production.services.design.sovereign.telemetry.TelemetryEvent;
import com.eneik.production.services.design.sovereign.telemetry.TelemetryEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DesignTelemetryCollectorTest {

    private Instant fixedInstant;
    private UUID fixedUuid;
    private InMemoryDesignTelemetryCollector collector;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-27T10:00:00Z");
        fixedUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        collector = new InMemoryDesignTelemetryCollector(() -> fixedInstant, () -> fixedUuid);
    }

    @Test
    @DisplayName("Should record generation success event with deterministic timestamp and ID")
    void testRecordGenerationSuccess() {
        UUID sessionId = UUID.randomUUID();
        TelemetryEvent event = collector.recordGenerationSuccess(
                sessionId,
                ProductArchetype.DEV_TOOLS,
                2,
                9.5,
                Map.of("client", "test")
        );

        assertNotNull(event);
        assertEquals(fixedUuid, event.eventId());
        assertEquals(TelemetryEventType.GENERATION_SUCCESS, event.eventType());
        assertEquals(sessionId, event.sessionId());
        assertEquals(ProductArchetype.DEV_TOOLS, event.archetype());
        assertEquals(2, event.iterationCount());
        assertEquals(9.5, event.auditScore(), 0.0001);
        assertTrue(event.deonticGatePassed());
        assertEquals(fixedInstant, event.timestamp());
        assertEquals("test", event.metadata().get("client"));

        List<TelemetryEvent> recorded = collector.getRecordedEvents();
        assertEquals(1, recorded.size());
        assertEquals(event, recorded.get(0));
    }

    @Test
    @DisplayName("Should record generation failure event with reason in metadata")
    void testRecordGenerationFailure() {
        UUID sessionId = UUID.randomUUID();
        TelemetryEvent event = collector.recordGenerationFailure(
                sessionId,
                ProductArchetype.FIN_TECH,
                3,
                "Horizontal scroll detected at 375px",
                Map.of("attempt", "1")
        );

        assertEquals(TelemetryEventType.GENERATION_FAILURE, event.eventType());
        assertEquals(sessionId, event.sessionId());
        assertEquals(ProductArchetype.FIN_TECH, event.archetype());
        assertEquals(3, event.iterationCount());
        assertNull(event.auditScore());
        assertFalse(event.deonticGatePassed());
        assertEquals("Horizontal scroll detected at 375px", event.metadata().get("failureReason"));
        assertEquals("1", event.metadata().get("attempt"));
    }

    @Test
    @DisplayName("Should record visual audit completion event")
    void testRecordAuditCompleted() {
        UUID sessionId = UUID.randomUUID();
        TelemetryEvent event = collector.recordAuditCompleted(
                sessionId,
                ProductArchetype.EDITORIAL,
                1,
                8.8,
                true,
                Map.of()
        );

        assertEquals(TelemetryEventType.AUDIT_COMPLETED, event.eventType());
        assertEquals(sessionId, event.sessionId());
        assertEquals(ProductArchetype.EDITORIAL, event.archetype());
        assertEquals(1, event.iterationCount());
        assertEquals(8.8, event.auditScore(), 0.0001);
        assertTrue(event.deonticGatePassed());
    }

    @Test
    @DisplayName("Should aggregate GQM telemetry summary correctly across multiple archetypes")
    void testGetSummaryAggregation() {
        AtomicInteger seq = new AtomicInteger(100);
        DesignTelemetryCollector seqCollector = new InMemoryDesignTelemetryCollector(
                () -> fixedInstant,
                () -> new UUID(0, seq.incrementAndGet())
        );

        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        UUID s4 = UUID.randomUUID();

        // DEV_TOOLS: 2 successes (score 9.5 and 9.3, iterations 1 and 2), 1 audit event (score 8.0)
        seqCollector.recordGenerationSuccess(s1, ProductArchetype.DEV_TOOLS, 1, 9.5, null);
        seqCollector.recordAuditCompleted(s1, ProductArchetype.DEV_TOOLS, 0, 8.0, false, null);
        seqCollector.recordGenerationSuccess(s2, ProductArchetype.DEV_TOOLS, 2, 9.3, null);

        // FIN_TECH: 1 failure (iterations 3)
        seqCollector.recordGenerationFailure(s3, ProductArchetype.FIN_TECH, 3, "Contrast ratio < 7:1", null);

        // CONSUMER: 1 success (score 9.4, iterations 1)
        seqCollector.recordGenerationSuccess(s4, ProductArchetype.CONSUMER, 1, 9.4, null);

        DesignTelemetrySummary summary = seqCollector.getSummary();

        // Total requests = 2 success (DEV_TOOLS) + 1 fail (FIN_TECH) + 1 success (CONSUMER) = 4
        assertEquals(4, summary.totalRequests());
        assertEquals(3, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(1, summary.auditCount());
        assertEquals(0.75, summary.overallSuccessRate(), 0.0001);

        // Average iterations for generation events (1 + 2 + 3 + 1) / 4 = 1.75
        assertEquals(1.75, summary.averageIterations(), 0.0001);

        // Average audit score across all events with score: (9.5 + 8.0 + 9.3 + 9.4) / 4 = 9.05
        assertEquals(9.05, summary.averageAuditScore(), 0.0001);

        // Archetype specific summaries
        Map<ProductArchetype, ArchetypeTelemetrySummary> archMap = summary.archetypeSummaries();
        assertEquals(3, archMap.size());

        ArchetypeTelemetrySummary devTools = archMap.get(ProductArchetype.DEV_TOOLS);
        assertEquals(2, devTools.totalRequests());
        assertEquals(2, devTools.successCount());
        assertEquals(0, devTools.failureCount());
        assertEquals(1, devTools.auditCount());
        assertEquals(1.0, devTools.successRate(), 0.0001);
        assertEquals(1.5, devTools.averageIterations(), 0.0001); // (1 + 2) / 2
        assertEquals(8.9333, devTools.averageAuditScore(), 0.001); // (9.5 + 8.0 + 9.3) / 3

        ArchetypeTelemetrySummary finTech = archMap.get(ProductArchetype.FIN_TECH);
        assertEquals(1, finTech.totalRequests());
        assertEquals(0, finTech.successCount());
        assertEquals(1, finTech.failureCount());
        assertEquals(0.0, finTech.successRate(), 0.0001);
        assertEquals(3.0, finTech.averageIterations(), 0.0001);
        assertEquals(0.0, finTech.averageAuditScore(), 0.0001);
    }

    @Test
    @DisplayName("Given a suite of simulated generation requests, telemetry system accurately reflects success and failure rates")
    void testSimulatedGenerationRequestsTelemetrySuccessAndFailureRates() {
        // Simulate a suite of 10 requests: 7 successes, 3 failures
        for (int i = 0; i < 7; i++) {
            collector.recordGenerationSuccess(
                    UUID.randomUUID(),
                    i % 2 == 0 ? ProductArchetype.DEV_TOOLS : ProductArchetype.FIN_TECH,
                    1,
                    9.5,
                    Map.of("requestIndex", String.valueOf(i))
            );
        }
        for (int i = 0; i < 3; i++) {
            collector.recordGenerationFailure(
                    UUID.randomUUID(),
                    ProductArchetype.CONSUMER,
                    2,
                    "Deontic gate failed: Leica noise detected",
                    Map.of("requestIndex", String.valueOf(i))
            );
        }

        DesignTelemetrySummary summary = collector.getSummary();

        assertEquals(10, summary.totalRequests());
        assertEquals(7, summary.successCount());
        assertEquals(3, summary.failureCount());
        assertEquals(0.70, summary.overallSuccessRate(), 0.0001);

        Map<ProductArchetype, ArchetypeTelemetrySummary> summaries = summary.archetypeSummaries();
        assertEquals(3, summaries.size());

        ArchetypeTelemetrySummary consumerSummary = summaries.get(ProductArchetype.CONSUMER);
        assertEquals(3, consumerSummary.totalRequests());
        assertEquals(0, consumerSummary.successCount());
        assertEquals(3, consumerSummary.failureCount());
        assertEquals(0.0, consumerSummary.successRate(), 0.0001);

        ArchetypeTelemetrySummary devToolsSummary = summaries.get(ProductArchetype.DEV_TOOLS);
        assertEquals(4, devToolsSummary.totalRequests());
        assertEquals(4, devToolsSummary.successCount());
        assertEquals(1.0, devToolsSummary.successRate(), 0.0001);
    }

    @Test
    @DisplayName("Should handle empty telemetry state gracefully without divide by zero")
    void testEmptySummary() {
        DesignTelemetrySummary summary = collector.getSummary();

        assertEquals(0, summary.totalRequests());
        assertEquals(0, summary.successCount());
        assertEquals(0, summary.failureCount());
        assertEquals(0, summary.auditCount());
        assertEquals(0.0, summary.overallSuccessRate(), 0.0001);
        assertEquals(0.0, summary.averageIterations(), 0.0001);
        assertEquals(0.0, summary.averageAuditScore(), 0.0001);
        assertTrue(summary.archetypeSummaries().isEmpty());
    }

    @Test
    @DisplayName("Should clear recorded events")
    void testClear() {
        collector.recordGenerationSuccess(UUID.randomUUID(), ProductArchetype.DEV_TOOLS, 1, 9.5, null);
        assertFalse(collector.getRecordedEvents().isEmpty());

        collector.clear();
        assertTrue(collector.getRecordedEvents().isEmpty());
        assertEquals(0, collector.getSummary().totalRequests());
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent event recording")
    void testConcurrentRecording() throws InterruptedException {
        int threads = 10;
        int eventsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        DesignTelemetryCollector concurrentCollector = new InMemoryDesignTelemetryCollector();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        concurrentCollector.recordGenerationSuccess(
                                UUID.randomUUID(),
                                ProductArchetype.DEV_TOOLS,
                                1,
                                9.5,
                                Map.of("thread", Thread.currentThread().getName())
                        );
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threads * eventsPerThread, concurrentCollector.getRecordedEvents().size());
        DesignTelemetrySummary summary = concurrentCollector.getSummary();
        assertEquals(threads * eventsPerThread, summary.totalRequests());
        assertEquals(1.0, summary.overallSuccessRate(), 0.0001);
    }
}
