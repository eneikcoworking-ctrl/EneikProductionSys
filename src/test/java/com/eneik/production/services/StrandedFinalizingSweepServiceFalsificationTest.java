package com.eneik.production.services;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Falsification test suite for {@link StrandedFinalizingSweepService}.
 * <p>
 * Philosophical anchors from RAG corpus:
 * <ul>
 *   <li>{@code PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER} [D007]: Empirical belief revision from observed duration samples in DefectJournal.</li>
 *   <li>{@code UESLI_SELMON_02_CAUSAL_PROCESS_TRACE} [D013]: Causal age tracing via dedicated finalizingSince timestamp, preventing premature release.</li>
 *   <li>{@code RONALD_DVORKIN_04_PRINCIPLED_INTEGRITY} [D012]: Non-interference with concurrent workers via CAS and principled clamp protection.</li>
 * </ul>
 */
class StrandedFinalizingSweepServiceFalsificationTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private DefectJournalRepository defectJournalRepository;
    private StrandedFinalizingSweepService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        service = new StrandedFinalizingSweepService(projectRepository, wishlistRepository, null);
        service.setDefectJournalRepository(defectJournalRepository);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "maxAgeMinutes", 3L);
        service.setMinSamplesForDataDriven(5);
        service.setSafetyMultiplier(10.0);
    }

    @Test
    @DisplayName("PITER_GERDENFORS_01: Even sample size median calculation with safety multiplier")
    void beliefUpdateLedger_evenSampleSizeCalculatesExactMedian() {
        // 6 samples: 1000, 2000, 3000, 5000, 6000, 8000 -> median of (3000+5000)/2 = 4000.0 ms.
        // 4000 * 10 = 40,000 ms.
        List<DefectJournalEntity> entries = List.of(
                sample(8000.0), sample(1000.0), sample(6000.0),
                sample(2000.0), sample(5000.0), sample(3000.0)
        );
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(entries);

        Duration duration = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMillis(40_000L), duration);
    }

    @Test
    @DisplayName("PITER_GERDENFORS_01: Non-positive and null durations are strictly filtered out")
    void beliefUpdateLedger_ignoresNullAndNonPositiveMetrics() {
        // 6 entries, but 2 are non-positive/null -> only 4 valid samples.
        // 4 valid samples < minSamplesForDataDriven (5) -> must fallback to maxAgeMinutes (3m = 180s).
        List<DefectJournalEntity> entries = List.of(
                sample(1000.0), sample(null), sample(0.0),
                sample(-500.0), sample(2000.0), sample(3000.0)
        );
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(entries);

        Duration duration = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMinutes(3), duration);
    }

    @Test
    @DisplayName("RONALD_DVORKIN_04: Lease duration is clamped to safety floor of 30 seconds")
    void principledIntegrity_clampsLeaseDurationToThirtySecondsFloor() {
        // Fast observed runs: 5 samples of 500 ms -> median 500 ms * 10 = 5000 ms.
        // Must clamp to Math.max(30_000L, 5000L) = 30,000 ms (30s floor).
        List<DefectJournalEntity> fastEntries = List.of(
                sample(500.0), sample(500.0), sample(500.0), sample(500.0), sample(500.0)
        );
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(fastEntries);

        Duration duration = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMillis(30_000L), duration);
    }

    @Test
    @DisplayName("UESLI_SELMON_02: Ancient createdAt does not cause premature sweep if finalizingSince is fresh")
    void causalProcessTrace_ancientCreatedAtDoesNotCausePrematureSweep() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity entity = new WishlistEntity();
        entity.setId(wishlistId);
        entity.setProjectId(projectId);
        entity.setStatus(WishlistStatus.finalizing);
        entity.setCreatedAt(Instant.now().minus(7, ChronoUnit.DAYS)); // Ancient creation
        entity.setLastCompileDispatchedAt(Instant.now().minus(2, ChronoUnit.HOURS)); // Ancient dispatch
        entity.setFinalizingSince(Instant.now().minus(30, ChronoUnit.SECONDS)); // Fresh finalizing lease (< 3 min)

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(entity));

        service.sweepProject(project);

        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    @DisplayName("UESLI_SELMON_02: Expired finalizingSince releases claim to pending status via CAS")
    void causalProcessTrace_expiredFinalizingSinceReleasesToPending() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID strandedId = UUID.randomUUID();
        WishlistEntity stranded = new WishlistEntity();
        stranded.setId(strandedId);
        stranded.setProjectId(projectId);
        stranded.setStatus(WishlistStatus.finalizing);
        stranded.setFinalizingSince(Instant.now().minus(10, ChronoUnit.MINUTES)); // Well past 3 min lease

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(stranded));
        when(wishlistRepository.compareAndSetStatus(strandedId, WishlistStatus.finalizing, WishlistStatus.pending))
                .thenReturn(1);

        service.sweepProject(project);

        verify(wishlistRepository).compareAndSetStatus(strandedId, WishlistStatus.finalizing, WishlistStatus.pending);
    }

    @Test
    @DisplayName("RONALD_DVORKIN_04: CAS loss due to concurrent worker completion is handled gracefully without error")
    void principledIntegrity_concurrentWorkerFinishingGracefullyLosesCAS() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID wishlistId = UUID.randomUUID();
        WishlistEntity entity = new WishlistEntity();
        entity.setId(wishlistId);
        entity.setProjectId(projectId);
        entity.setStatus(WishlistStatus.finalizing);
        entity.setFinalizingSince(Instant.now().minus(15, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(entity));
        // CAS returns 0 because concurrent worker changed status from finalizing to done/failed
        when(wishlistRepository.compareAndSetStatus(wishlistId, WishlistStatus.finalizing, WishlistStatus.pending))
                .thenReturn(0);

        service.sweepProject(project);

        // Verification: CAS was attempted, returned 0, no exception thrown, project sweep continues
        verify(wishlistRepository).compareAndSetStatus(wishlistId, WishlistStatus.finalizing, WishlistStatus.pending);
        verify(wishlistRepository, never()).delete(any());
    }

    private static DefectJournalEntity sample(Double metricValue) {
        DefectJournalEntity entity = new DefectJournalEntity();
        entity.setMetricValue(metricValue);
        return entity;
    }
}
