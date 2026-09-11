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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StrandedFinalizingSweepServiceTest {

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
        // Wire self-reference
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "maxAgeMinutes", 3L);
        service.setMinSamplesForDataDriven(5);
        service.setSafetyMultiplier(10.0);
    }

    @Test
    void sweepsStrandedFinalizingWishlistsOlderThanMaxAgeToPending() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID strandedWishlistId = UUID.randomUUID();
        WishlistEntity stranded = new WishlistEntity();
        stranded.setId(strandedWishlistId);
        stranded.setProjectId(projectId);
        stranded.setStatus(WishlistStatus.finalizing);
        stranded.setFinalizingSince(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(stranded));
        when(wishlistRepository.compareAndSetStatus(strandedWishlistId, WishlistStatus.finalizing, WishlistStatus.pending))
                .thenReturn(1);

        service.sweepProject(project);

        verify(wishlistRepository).compareAndSetStatus(
                strandedWishlistId, WishlistStatus.finalizing, WishlistStatus.pending);
    }

    @Test
    void doesNotSweepFinalizingWishlistsWithinLeaseWindow() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID activeWishlistId = UUID.randomUUID();
        WishlistEntity active = new WishlistEntity();
        active.setId(activeWishlistId);
        active.setProjectId(projectId);
        active.setStatus(WishlistStatus.finalizing);
        active.setFinalizingSince(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(active));

        service.sweepProject(project);

        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    @Test
    void sweepIteratesAllActiveProjects() {
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));
        when(wishlistRepository.findByProjectIdAndStatus(activeProject.getId(), WishlistStatus.finalizing))
                .thenReturn(List.of());

        service.sweep();

        verify(projectRepository).findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        verify(projectRepository, never()).findAll();
        verify(wishlistRepository).findByProjectIdAndStatus(activeProject.getId(), WishlistStatus.finalizing);
    }

    /**
     * Prescription 34 Falsification test (CAUSAL_PROCESS_TRACE / D013):
     * The old implementation measured age against lastCompileDispatchedAt, which in the live incident
     * was 65 minutes old, causing immediate premature reclamation of a fresh finalizing entry.
     * With dedicated finalizingSince, the wishlist is protected and never swept.
     */
    @Test
    void wishlistWithOldDispatchTime_butFreshFinalizingSince_isNotSwept() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID freshId = UUID.randomUUID();
        WishlistEntity fresh = new WishlistEntity();
        fresh.setId(freshId);
        fresh.setProjectId(projectId);
        fresh.setStatus(WishlistStatus.finalizing);
        fresh.setLastCompileDispatchedAt(Instant.now().minus(65, ChronoUnit.MINUTES)); // Old dispatch
        fresh.setFinalizingSince(Instant.now().minus(10, ChronoUnit.SECONDS)); // Fresh finalizing lease

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(fresh));

        service.sweepProject(project);

        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    /**
     * Prescription 17 Falsification test (PRINCIPLED_INTEGRITY / D012):
     * A live worker executing slow PR discovery / network calls actively renews the finalizing lease.
     * Even if total elapsed time since initial dispatch exceeds the base maxAge, the renewed lease
     * guarantees the live worker is NEVER robbed of its claim.
     */
    @Test
    void liveWorkerWithRenewedLease_isNotSweptEvenIfWorkExceedsBaseLease() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID activeId = UUID.randomUUID();
        WishlistEntity active = new WishlistEntity();
        active.setId(activeId);
        active.setProjectId(projectId);
        active.setStatus(WishlistStatus.finalizing);
        // Initially entered 10 minutes ago, but lease renewed 30 seconds ago
        active.setLastCompileDispatchedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        active.setFinalizingSince(Instant.now().minus(30, ChronoUnit.SECONDS));

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(active));

        service.sweepProject(project);

        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    /**
     * Historical wishlist lacking finalizingSince (e.g. pre-migration) is initialized to now
     * and saved, rather than being prematurely or immediately released.
     */
    @Test
    void historicalWishlistWithoutFinalizingSince_isInitializedToNowAndNotSweptImmediately() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID historicalId = UUID.randomUUID();
        WishlistEntity historical = new WishlistEntity();
        historical.setId(historicalId);
        historical.setProjectId(projectId);
        historical.setStatus(WishlistStatus.finalizing);
        historical.setFinalizingSince(null);

        when(wishlistRepository.findByProjectIdAndStatus(projectId, WishlistStatus.finalizing))
                .thenReturn(List.of(historical));

        service.sweepProject(project);

        assertNotNull(historical.getFinalizingSince());
        verify(wishlistRepository).save(historical);
        verify(wishlistRepository, never()).compareAndSetStatus(any(), any(), any());
    }

    /**
     * Prescription 17 & 34: BELIEF_UPDATE_LEDGER (D007).
     * Lease duration is empirically derived from observed FINALIZING_DURATION samples in DefectJournal.
     */
    @Test
    void calculateEffectiveLeaseDuration_usesObservedDurationsWhenAvailable() {
        DefectJournalEntity e1 = new DefectJournalEntity(); e1.setMetricValue(2000.0);
        DefectJournalEntity e2 = new DefectJournalEntity(); e2.setMetricValue(3000.0);
        DefectJournalEntity e3 = new DefectJournalEntity(); e3.setMetricValue(4000.0);
        DefectJournalEntity e4 = new DefectJournalEntity(); e4.setMetricValue(5000.0);
        DefectJournalEntity e5 = new DefectJournalEntity(); e5.setMetricValue(6000.0);

        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(List.of(e1, e2, e3, e4, e5));

        // Median is 4000 ms. 4000 * 10 = 40,000 ms = 40 seconds (clamped to at least 30s floor).
        Duration effective = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMillis(40_000L), effective);
    }

    @Test
    void calculateEffectiveLeaseDuration_fallsBackToDefaultWhenInsufficientSamples() {
        DefectJournalEntity e1 = new DefectJournalEntity(); e1.setMetricValue(2000.0);
        when(defectJournalRepository.findByDefectTypeOrderByCreatedAtDesc("FINALIZING_DURATION"))
                .thenReturn(List.of(e1));

        // With only 1 sample (< 5 minSamples), falls back to base maxAgeMinutes (3 minutes = 180s)
        Duration effective = service.calculateEffectiveLeaseDuration();
        assertEquals(Duration.ofMinutes(3), effective);
    }
}
