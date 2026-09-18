package com.eneik.production.services.design;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.repositories.DesignShopCycleRepository;
import com.eneik.production.services.logging.ProjectLogFlushQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test shield enforcing Prescription 51 / Achille Varzi's BOUNDARY_TOPOLOGY (D006, Varzi 1999):
 * Every claim requires a temporal boundary and sweeping closure.
 * Stranded design shop cycle claims must be released automatically without manual intervention.
 */
class StrandedDesignCycleSweepServiceTest {

    private DesignShopCycleRepository designShopCycleRepository;
    private DefectJournalService defectJournalService;
    private DefectJournalRepository defectJournalRepository;
    private StrandedDesignCycleSweepService service;

    @BeforeEach
    void setUp() {
        ProjectLogFlushQueue.drain(10_000);
        designShopCycleRepository = mock(DesignShopCycleRepository.class);
        defectJournalService = mock(DefectJournalService.class);
        defectJournalRepository = mock(DefectJournalRepository.class);

        service = new StrandedDesignCycleSweepService(designShopCycleRepository, null);
        service.setDefectJournalService(defectJournalService);
        service.setDefectJournalRepository(defectJournalRepository);
        ReflectionTestUtils.setField(service, "self", service);
        service.setMaxAgeMinutes(15L);
        service.setMinSamplesForDataDriven(5);
        service.setSafetyMultiplier(3.0);
    }

    @AfterEach
    void tearDown() {
        ProjectLogFlushQueue.drain(10_000);
    }

    @Test
    @DisplayName("Falsification harness: Stranded design cycle older than TTL is automatically swept and released")
    void sweepsStrandedDesignCycleOlderThanCutoff() {
        UUID projectId = UUID.randomUUID();
        Instant strandedAt = Instant.now().minus(25, ChronoUnit.MINUTES);

        DesignShopCycleEntity strandedCycle = new DesignShopCycleEntity();
        strandedCycle.setProjectId(projectId);
        strandedCycle.setStartCycleClaimedAt(strandedAt);
        strandedCycle.setLastWasReady(false);

        when(designShopCycleRepository.findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore(any(Instant.class)))
                .thenReturn(List.of(strandedCycle));
        when(designShopCycleRepository.compareAndReleaseStrandedClaim(eq(projectId), eq(strandedAt)))
                .thenReturn(1);

        int released = service.sweep();

        assertEquals(1, released, "Stranded cycle must be released");
        verify(designShopCycleRepository).compareAndReleaseStrandedClaim(projectId, strandedAt);

        // Verify institutional audit recorded
        verify(defectJournalService).recordInstitutionalAudit(
                eq(projectId),
                eq("DesignShopCycleRepository"),
                eq("STRANDED_DESIGN_CYCLE_CLAIM_RELEASED"),
                contains("Released stranded design cycle claim"),
                anyDouble()
        );

        // Verify project log received entry
        List<ProjectLogFlushQueue.PendingEntry> logEntries = ProjectLogFlushQueue.drain(10);
        assertEquals(1, logEntries.size());
        assertEquals(projectId, logEntries.get(0).projectId());
        assertEquals("WARN", logEntries.get(0).level());
        assertTrue(logEntries.get(0).message().contains("BOUNDARY-TOPOLOGY"));
    }

    @Test
    @DisplayName("Active design cycle within lease duration is NOT swept")
    void doesNotSweepActiveClaimWithinLease() {
        when(designShopCycleRepository.findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        int released = service.sweep();

        assertEquals(0, released);
        verify(designShopCycleRepository, never()).compareAndReleaseStrandedClaim(any(), any());
        verify(defectJournalService, never()).recordInstitutionalAudit(any(), any(), any(), any(), anyDouble());
    }

    @Test
    @DisplayName("CAS failure handled safely when concurrent worker finishes and clears claim first")
    void casFailureHandledSafely() {
        UUID projectId = UUID.randomUUID();
        Instant strandedAt = Instant.now().minus(30, ChronoUnit.MINUTES);

        DesignShopCycleEntity strandedCycle = new DesignShopCycleEntity();
        strandedCycle.setProjectId(projectId);
        strandedCycle.setStartCycleClaimedAt(strandedAt);

        when(designShopCycleRepository.findByStartCycleClaimedAtIsNotNullAndStartCycleClaimedAtBefore(any(Instant.class)))
                .thenReturn(List.of(strandedCycle));
        // CAS returns 0 because another worker released or finished concurrently
        when(designShopCycleRepository.compareAndReleaseStrandedClaim(eq(projectId), eq(strandedAt)))
                .thenReturn(0);

        int released = service.sweep();

        assertEquals(0, released, "Released count must be 0 when CAS returns 0");
        verify(designShopCycleRepository).compareAndReleaseStrandedClaim(projectId, strandedAt);
        verify(defectJournalService, never()).recordInstitutionalAudit(any(), any(), any(), any(), anyDouble());
    }

    @Test
    @DisplayName("Data-driven lease duration calculated from DefectJournal observations")
    void calculatesDataDrivenLeaseDuration() {
        // Provide 5 observations of generation duration (e.g. 120_000 ms = 2 min)
        List<DefectJournalEntity> observations = List.of(
                createDurationEntity(100_000.0),
                createDurationEntity(110_000.0),
                createDurationEntity(120_000.0), // median
                createDurationEntity(130_000.0),
                createDurationEntity(140_000.0)
        );
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("DESIGN_CYCLE_GENERATION_DURATION"))
                .thenReturn(observations);

        Duration effective = service.calculateEffectiveLeaseDuration();
        // median = 120_000 ms * safetyMultiplier (3.0) = 360_000 ms (6 minutes)
        assertEquals(360_000L, effective.toMillis());
    }

    private DefectJournalEntity createDurationEntity(Double durationMillis) {
        DefectJournalEntity entity = new DefectJournalEntity();
        entity.setDefectType("DESIGN_CYCLE_GENERATION_DURATION");
        entity.setMetricValue(durationMillis);
        return entity;
    }
}
