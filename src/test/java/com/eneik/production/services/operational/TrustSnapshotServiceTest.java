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

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private com.eneik.production.repositories.InvariantStatusChangeRepository invariantChangeRepository;
    private TrustSnapshotService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        operationalTruthService = mock(OperationalTruthService.class);
        snapshotRepository = mock(TrustSignalSnapshotRepository.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        invariantChangeRepository = mock(com.eneik.production.repositories.InvariantStatusChangeRepository.class);
        service = new TrustSnapshotService(projectRepository, operationalTruthService, snapshotRepository,
                readinessService, invariantChangeRepository);
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

    // 2026-08-20: the seven Charter invariants were evaluated here every two hours and thrown away with
    // the DTO. Without a stored previous value `pass -> warn` is undetectable in principle, so the one
    // event meaning "this factory has stopped being right about itself" could not be acted on.
    @Test
    void recordsAnInvariantTransitionTheFirstTimeItIsSeen() {
        UUID projectId = UUID.randomUUID();
        when(operationalTruthService.build(projectId)).thenReturn(dtoWith(1, 1, 0, 0, 0, 0.7, false));
        when(invariantChangeRepository.findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(any(), any()))
                .thenReturn(java.util.Optional.empty());

        service.captureSnapshot(projectId);

        verify(invariantChangeRepository, atLeastOnce())
                .save(any(com.eneik.production.models.persistence.InvariantStatusChangeEntity.class));
    }

    // A repeated evaluation of an unchanged status is a confirmation. Confirmations are free and
    // unbounded; recording them would repeat, at birth, the KAIZEN_PROPOSALS defect measured the same day
    // - 347 rows carrying 10 distinct identities because the write path had no identity.
    @Test
    void writesNothingWhenEveryInvariantStatusIsUnchanged() {
        UUID projectId = UUID.randomUUID();
        OperationalTruthDto dto = dtoWith(1, 1, 0, 0, 0, 0.7, false);
        when(operationalTruthService.build(projectId)).thenReturn(dto);
        when(invariantChangeRepository.findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(any(), any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(1);
                    String status = dto.invariants().stream()
                            .filter(i -> i.key().equals(key))
                            .map(OperationalTruthDto.InvariantStatus::status)
                            .findFirst().orElse("pass");
                    var previous = new com.eneik.production.models.persistence.InvariantStatusChangeEntity();
                    previous.setStatus(status);
                    return java.util.Optional.of(previous);
                });

        service.captureSnapshot(projectId);

        verify(invariantChangeRepository, never())
                .save(any(com.eneik.production.models.persistence.InvariantStatusChangeEntity.class));
    }

    // The transition carries what it moved FROM. A status alone cannot be read as a refutation - only the
    // pair (previous, current) says whether the factory just started being wrong or just stopped.
    @Test
    void aTransitionCarriesTheStatusItMovedFrom() {
        UUID projectId = UUID.randomUUID();
        when(operationalTruthService.build(projectId)).thenReturn(dtoWith(1, 1, 0, 0, 0, 0.7, true));
        var previous = new com.eneik.production.models.persistence.InvariantStatusChangeEntity();
        previous.setStatus("pass");
        when(invariantChangeRepository.findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(any(), any()))
                .thenReturn(java.util.Optional.of(previous));

        service.captureSnapshot(projectId);

        ArgumentCaptor<com.eneik.production.models.persistence.InvariantStatusChangeEntity> saved =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.InvariantStatusChangeEntity.class);
        verify(invariantChangeRepository, atLeastOnce()).save(saved.capture());
        assertEquals("pass", saved.getValue().getPreviousStatus());
        assertNotEquals("pass", saved.getValue().getStatus());
    }

}
