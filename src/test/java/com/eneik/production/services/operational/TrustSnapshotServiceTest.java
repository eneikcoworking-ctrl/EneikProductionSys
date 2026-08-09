package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.OperationalTruthDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TrustSignalSnapshotEntity;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TrustSignalSnapshotRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026-08-08 (ML-update patch, Phase 3): Stage-1 data collection only - real snapshot capture and real
 * outcome backfill, no candidate weighting yet (honestly not enough labeled history to fit one).
 */
class TrustSnapshotServiceTest {

    private ProjectRepository projectRepository;
    private OperationalTruthService operationalTruthService;
    private TrustSignalSnapshotRepository snapshotRepository;
    private ClientDeliverableReadinessService readinessService;
    private TrustSnapshotService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        operationalTruthService = mock(OperationalTruthService.class);
        snapshotRepository = mock(TrustSignalSnapshotRepository.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        service = new TrustSnapshotService(projectRepository, operationalTruthService, snapshotRepository, readinessService);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private OperationalTruthDto dtoWith(int mergedReviews, int qgPassed, int qgFailed, int failingReviews,
                                         int recentDefects, double trustScore, boolean duplicateContentWarn) {
        var evidence = new OperationalTruthDto.EvidenceSummary(
                mergedReviews, 0, 0, failingReviews, qgPassed, qgFailed, 0, List.of());
        var defects = new OperationalTruthDto.DefectSummary(recentDefects, List.of());
        var trust = new OperationalTruthDto.Trust(trustScore, "watch", List.of(), List.of());
        var invariants = List.of(new OperationalTruthDto.InvariantStatus(
                "duplicate_content_blocks_throughput_trust", duplicateContentWarn ? "warn" : "pass", "", ""));
        return new OperationalTruthDto(null, null, null, null, trust, null, null, evidence, defects, null,
                null, invariants, null, null, null);
    }

    @Test
    void captureSnapshotRecordsTheRealDtoInputsIncludingDuplicateContentFromInvariants() {
        UUID projectId = UUID.randomUUID();
        when(operationalTruthService.build(projectId)).thenReturn(dtoWith(5, 3, 2, 1, 4, 0.62, true));

        service.captureSnapshot(projectId);

        var captor = org.mockito.ArgumentCaptor.forClass(TrustSignalSnapshotEntity.class);
        verify(snapshotRepository).save(captor.capture());
        TrustSignalSnapshotEntity saved = captor.getValue();
        assertEquals(projectId, saved.getProjectId());
        assertEquals(5, saved.getMergedReviews());
        assertEquals(3, saved.getQualityGatePassed());
        assertEquals(2, saved.getQualityGateFailed());
        assertEquals(1, saved.getFailingReviews());
        assertEquals(4, saved.getRecentDefectsCount());
        assertEquals(0.62, saved.getComputedScore(), 1e-9);
        assertEquals(true, saved.isDuplicateContent());
        assertNull(saved.getEventualOutcome());
    }

    @Test
    void backfillResolvesToDeliveredWhenAllFeaturesAreComplete() {
        TrustSignalSnapshotEntity unresolved = new TrustSignalSnapshotEntity();
        UUID projectId = UUID.randomUUID();
        unresolved.setProjectId(projectId);
        when(snapshotRepository.findByEventualOutcomeIsNull()).thenReturn(List.of(unresolved));

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(readinessService.computeForProject(projectId)).thenReturn(
                new ClientDeliverableReadinessService.Readiness(3, 3, 10, 10, 1.0, true, 1.0));

        service.backfillResolvedOutcomes();

        assertEquals("delivered", unresolved.getEventualOutcome());
    }

    @Test
    void backfillResolvesToAbandonedWhenProjectIsFrozenWithoutFullDelivery() {
        TrustSignalSnapshotEntity unresolved = new TrustSignalSnapshotEntity();
        UUID projectId = UUID.randomUUID();
        unresolved.setProjectId(projectId);
        when(snapshotRepository.findByEventualOutcomeIsNull()).thenReturn(List.of(unresolved));

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.frozen);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(readinessService.computeForProject(projectId)).thenReturn(
                new ClientDeliverableReadinessService.Readiness(5, 2, 10, 4, 0.4, true, 0.4));

        service.backfillResolvedOutcomes();

        assertEquals("abandoned", unresolved.getEventualOutcome());
    }

    @Test
    void backfillLeavesAStillActiveIncompleteProjectUnresolved() {
        TrustSignalSnapshotEntity unresolved = new TrustSignalSnapshotEntity();
        UUID projectId = UUID.randomUUID();
        unresolved.setProjectId(projectId);
        when(snapshotRepository.findByEventualOutcomeIsNull()).thenReturn(List.of(unresolved));

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(readinessService.computeForProject(projectId)).thenReturn(
                new ClientDeliverableReadinessService.Readiness(5, 2, 10, 4, 0.4, true, 0.4));

        service.backfillResolvedOutcomes();

        assertNull(unresolved.getEventualOutcome());
    }
}
