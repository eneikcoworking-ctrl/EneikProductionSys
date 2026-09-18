package com.eneik.production.services.quality;

import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.ProcessControlSnapshotEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProcessControlSnapshotRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.ReviewConcernRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.audit.SixSigmaAuditService.DefectOpportunityCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Layer 1 (Six Sigma / Measure) math verification - u-chart Phase 1 baseline lock, Phase 2 monitoring
 * against a FIXED centerline, 3σ excursion detection, and the Western Electric 8-consecutive-same-side
 * rule. Numbers below are chosen so the control limits are hand-verifiable: UCL/LCL = ū ± 3√(ū/n).
 */
public class ProcessControlServiceTest {

    private FeatureRepository featureRepository;
    private TaskRepository taskRepository;
    private ProcessControlSnapshotRepository snapshotRepository;
    private SixSigmaAuditService sixSigmaAuditService;
    private ReviewConcernRepository reviewConcernRepository;
    private DefectJournalRepository defectJournalRepository;
    private KaizenService kaizenService;
    private ProjectRepository projectRepository;

    private ProcessControlService service;
    private UUID projectId;

    private PrReviewRepository prReviewRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesSessionRepository julesSessionRepository;

    @BeforeEach
    void setUp() {
        featureRepository = mock(FeatureRepository.class);
        taskRepository = mock(TaskRepository.class);
        snapshotRepository = mock(ProcessControlSnapshotRepository.class);
        sixSigmaAuditService = mock(SixSigmaAuditService.class);
        reviewConcernRepository = mock(ReviewConcernRepository.class);
        prReviewRepository = mock(PrReviewRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        kaizenService = mock(KaizenService.class);
        projectRepository = mock(ProjectRepository.class);

        projectId = UUID.randomUUID();

        when(snapshotRepository.findByProjectIdAndStreamOrderBySequenceIndexAsc(any(), any())).thenReturn(Collections.emptyList());
        when(snapshotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewConcernRepository.findByFeatureId(any())).thenReturn(Collections.emptyList());
        when(defectJournalRepository.findByFeatureId(any())).thenReturn(Collections.emptyList());
        when(projectRepository.findById(any())).thenReturn(Optional.empty());
        when(sixSigmaAuditService.computePrConflictCounts(any(), any())).thenReturn(new DefectOpportunityCount(0, 0));
        when(sixSigmaAuditService.computePrConflictCounts(any(), any(), any(), any()))
                .thenReturn(new DefectOpportunityCount(0, 0));
        when(sixSigmaAuditService.computeQualityGateCounts(any(), any())).thenReturn(new DefectOpportunityCount(0, 10));
        when(julesSessionRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());
        when(prReviewRepository.findByJulesSessionIdIn(any())).thenReturn(Collections.emptyList());
        when(taskConflictRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());

        service = new ProcessControlService(featureRepository, taskRepository, snapshotRepository,
                sixSigmaAuditService, reviewConcernRepository, prReviewRepository,
                taskConflictRepository, julesSessionRepository,
                defectJournalRepository, kaizenService, projectRepository, null);
        ReflectionTestUtils.setField(service, "baselineEpicCount", 2);
    }

    private FeatureEntity epic(UUID id, ProjectEntity project) {
        FeatureEntity f = new FeatureEntity();
        f.setId(id);
        f.setProjectId(project.getId());
        f.setCreatedAt(Instant.now());
        return f;
    }

    private FeatureEntity epic(UUID id, ProjectEntity project, String sixSigmaMetric) {
        FeatureEntity f = epic(id, project);
        f.setSixSigmaMetric(sixSigmaMetric);
        return f;
    }

    private void stubCompletedEpic(UUID featureId, ProjectEntity project, Instant completedAt) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setStatus(TaskStatus.done);
        task.setCreatedAt(completedAt.minusSeconds(60));
        task.setUpdatedAt(completedAt);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(task));
    }

    @Test
    void evidencePacketScopedToProjectCompletedEpicsAndNeverUsesFindAll() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId))
                .thenReturn(List.of(epic(f1, project), epic(f2, project), epic(f3, project)));
        stubCompletedEpic(f1, project, t0);
        stubCompletedEpic(f2, project, t0.plus(1, ChronoUnit.DAYS));
        stubCompletedEpic(f3, project, t0.plus(2, ChronoUnit.DAYS));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), any()))
                .thenReturn(new DefectOpportunityCount(1, 10));

        service.recomputeForProject(projectId);

        // Section VII (ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010, Goldman; ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK / D010):
        // Evidence packet is scoped to tasks under completed epics; findAll() is eliminated.
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
        verify(julesSessionRepository, times(1)).findByTaskIdIn(any());
        verify(taskConflictRepository, times(1)).findByTaskIdIn(any());
    }

    @Test
    void projectScopedEvidencePacketPreventsCrossProjectReviewAndConflictLeakage() {
        // Project A
        ProjectEntity projectA = new ProjectEntity();
        projectA.setId(projectId);
        UUID epicAId = UUID.randomUUID();
        UUID taskAId = UUID.randomUUID();
        UUID sessionAId = UUID.randomUUID();

        TaskEntity taskA = new TaskEntity();
        taskA.setId(taskAId);
        taskA.setProject(projectA);
        taskA.setFeatureId(epicAId);
        taskA.setStatus(TaskStatus.done);
        taskA.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        taskA.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        JulesSessionEntity sessionA = new JulesSessionEntity();
        sessionA.setId(sessionAId);
        sessionA.setTaskId(taskAId);

        PrReviewEntity reviewA = new PrReviewEntity();
        reviewA.setJulesSessionId(sessionAId);
        reviewA.setMerged(true);

        TaskConflictEntity conflictA = new TaskConflictEntity();
        conflictA.setTask(taskA);

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId))
                .thenReturn(List.of(epic(epicAId, projectA)));
        when(taskRepository.findByFeatureId(epicAId)).thenReturn(List.of(taskA));
        when(julesSessionRepository.findByTaskIdIn(List.of(taskAId))).thenReturn(List.of(sessionA));
        when(prReviewRepository.findByJulesSessionIdIn(List.of(sessionAId))).thenReturn(List.of(reviewA));
        when(taskConflictRepository.findByTaskIdIn(List.of(taskAId))).thenReturn(List.of(conflictA));

        // Project B artifacts (must NEVER leak into Project A recompute)
        UUID projectBId = UUID.randomUUID();
        UUID epicBId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        UUID sessionBId = UUID.randomUUID();
        JulesSessionEntity sessionB = new JulesSessionEntity();
        sessionB.setId(sessionBId);
        sessionB.setTaskId(taskBId);
        PrReviewEntity reviewB = new PrReviewEntity();
        reviewB.setJulesSessionId(sessionBId);
        reviewB.setMerged(true);
        TaskConflictEntity conflictB = new TaskConflictEntity();
        TaskEntity taskB = new TaskEntity();
        taskB.setId(taskBId);
        conflictB.setTask(taskB);

        // Execute Project A recompute
        service.recomputeForProject(projectId);

        // Falsification check (ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010, Goldman; ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK):
        // 1. Scoped queries were called strictly with Project A IDs
        verify(julesSessionRepository).findByTaskIdIn(List.of(taskAId));
        verify(prReviewRepository).findByJulesSessionIdIn(List.of(sessionAId));
        verify(taskConflictRepository).findByTaskIdIn(List.of(taskAId));

        // 2. Global full-table reads are NEVER invoked
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();

        // 3. Evidence passed to Six Sigma audit contains only Project A reviews/conflicts
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PrReviewEntity>> reviewsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<TaskConflictEntity>> conflictsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(sixSigmaAuditService).computePrConflictCounts(isNull(), eq(epicAId), reviewsCaptor.capture(), conflictsCaptor.capture());

        assertThat(reviewsCaptor.getValue()).containsExactly(reviewA);
        assertThat(reviewsCaptor.getValue()).doesNotContain(reviewB);
        assertThat(conflictsCaptor.getValue()).containsExactly(conflictA);
        assertThat(conflictsCaptor.getValue()).doesNotContain(conflictB);
    }

    @Test
    void emptyCompletedEpicsPerformsNoEvidenceQueriesAndReturnsEmpty() {
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(Collections.emptyList());

        List<ProcessControlSnapshotEntity> snapshots = service.recomputeForProject(projectId);

        assertThat(snapshots).isEmpty();
        verify(julesSessionRepository, never()).findByTaskIdIn(any());
        verify(prReviewRepository, never()).findByJulesSessionIdIn(any());
        verify(taskConflictRepository, never()).findByTaskIdIn(any());
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
    }

    @Test
    void duplicateEpicRolledIntoCanonicalResolvesTasksAndPreservesLineage() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        UUID canonicalEpicId = UUID.randomUUID();
        UUID duplicateEpicId = UUID.randomUUID();

        FeatureEntity canonicalEpic = epic(canonicalEpicId, project);
        FeatureEntity duplicateEpic = epic(duplicateEpicId, project);
        duplicateEpic.setCanonicalFeatureId(canonicalEpicId);

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId))
                .thenReturn(List.of(canonicalEpic, duplicateEpic));

        UUID task1Id = UUID.randomUUID();
        UUID task2Id = UUID.randomUUID();

        TaskEntity task1 = new TaskEntity();
        task1.setId(task1Id);
        task1.setProject(project);
        task1.setFeatureId(canonicalEpicId);
        task1.setStatus(TaskStatus.done);
        task1.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        task1.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        TaskEntity task2 = new TaskEntity();
        task2.setId(task2Id);
        task2.setProject(project);
        task2.setFeatureId(duplicateEpicId);
        task2.setStatus(TaskStatus.done);
        task2.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        task2.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(taskRepository.findByFeatureId(canonicalEpicId)).thenReturn(List.of(task1));
        when(taskRepository.findByFeatureId(duplicateEpicId)).thenReturn(List.of(task2));

        UUID session1Id = UUID.randomUUID();
        JulesSessionEntity session1 = new JulesSessionEntity();
        session1.setId(session1Id);
        session1.setTaskId(task1Id);

        UUID session2Id = UUID.randomUUID();
        JulesSessionEntity session2 = new JulesSessionEntity();
        session2.setId(session2Id);
        session2.setTaskId(task2Id);

        when(julesSessionRepository.findByTaskIdIn(any())).thenReturn(List.of(session1, session2));

        PrReviewEntity review1 = new PrReviewEntity();
        review1.setJulesSessionId(session1Id);
        review1.setMerged(true);

        PrReviewEntity review2 = new PrReviewEntity();
        review2.setJulesSessionId(session2Id);
        review2.setMerged(true);

        when(prReviewRepository.findByJulesSessionIdIn(any())).thenReturn(List.of(review1, review2));

        service.recomputeForProject(projectId);

        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();
        // One canonical epic point produced, rolling in duplicate epic's tasks
        verify(sixSigmaAuditService, times(1)).computePrConflictCounts(isNull(), eq(canonicalEpicId), any(), any());
    }

    @Test
    void baselineLockedThenMonitoringFlagsExcursionAboveUcl() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);

        List<FeatureEntity> epics = List.of(epic(f1, project), epic(f2, project), epic(f3, project));
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(epics);

        stubCompletedEpic(f1, project, t0);
        stubCompletedEpic(f2, project, t0.plus(1, ChronoUnit.DAYS));
        stubCompletedEpic(f3, project, t0.plus(2, ChronoUnit.DAYS));

        // Baseline: u=0.1 for both f1 and f2 -> pooled centerline = (1+1)/(10+10) = 0.1
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f1))).thenReturn(new DefectOpportunityCount(1, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f2))).thenReturn(new DefectOpportunityCount(1, 10));
        // Monitoring: u=0.8, far above UCL = 0.1 + 3*sqrt(0.1/10) = 0.4
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f3))).thenReturn(new DefectOpportunityCount(8, 10));

        List<ProcessControlSnapshotEntity> saved = service.recomputeForProject(projectId).stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted((a, b) -> Integer.compare(a.getSequenceIndex(), b.getSequenceIndex()))
                .toList();

        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getPhase()).isEqualTo("BASELINE");
        assertThat(saved.get(1).getPhase()).isEqualTo("BASELINE");
        assertThat(saved.get(2).getPhase()).isEqualTo("MONITORING");

        assertThat(saved.get(2).getCenterLine()).isEqualTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(saved.get(2).getUpperControlLimit()).isEqualTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(saved.get(2).getU()).isEqualTo(0.8, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(saved.get(2).isOutOfControl()).isTrue();
        assertThat(saved.get(0).isOutOfControl()).isFalse();

        // Loop-closing: no rootCausePatternId on record for f3 -> systemic defect, not a known pattern
        verify(kaizenService, times(1)).recordSystemicDefectProposal(eq(projectId), any(), any(), any());
    }

    @Test
    void knownPatternViolationProposalRecordedWhenDefectEventsCarryRootCausePatternId() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setName("TestProject");
        when(projectRepository.findById(projectId)).thenReturn(java.util.Optional.of(project));

        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);

        List<FeatureEntity> epics = List.of(epic(f1, project), epic(f2, project), epic(f3, project));
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(epics);

        stubCompletedEpic(f1, project, t0);
        stubCompletedEpic(f2, project, t0.plus(1, ChronoUnit.DAYS));
        stubCompletedEpic(f3, project, t0.plus(2, ChronoUnit.DAYS));

        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f1))).thenReturn(new DefectOpportunityCount(1, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f2))).thenReturn(new DefectOpportunityCount(1, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f3))).thenReturn(new DefectOpportunityCount(8, 10));

        // Stub defects for f3 carrying rootCausePatternId = 6 (Category errors at serialization boundaries)
        com.eneik.production.kaizen.model.DefectJournalEntity defect1 =
                new com.eneik.production.kaizen.model.DefectJournalEntity(projectId, f3, 6, "high",
                        "ontological_stratification", "AutoMergeService", "contaminated", "PR has factory file", 1.0);
        com.eneik.production.kaizen.model.DefectJournalEntity defect2 =
                new com.eneik.production.kaizen.model.DefectJournalEntity(projectId, f3, 6, "high",
                        "ontological_stratification", "AutoMergeService", "blocker_pr", "PR announces refusal", 1.0);
        when(defectJournalRepository.findByFeatureId(f3)).thenReturn(List.of(defect1, defect2));

        List<ProcessControlSnapshotEntity> saved = service.recomputeForProject(projectId).stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted((a, b) -> Integer.compare(a.getSequenceIndex(), b.getSequenceIndex()))
                .toList();

        assertThat(saved).hasSize(3);
        assertThat(saved.get(2).isOutOfControl()).isTrue();

        // Loop-closing with known pattern: dominant is 6 -> KNOWN_PATTERN_VIOLATION with Charter name
        verify(kaizenService, times(1)).recordKnownPatternViolationProposal(
                eq(projectId),
                eq("TestProject"),
                eq(6),
                eq("Category errors at serialization boundaries"),
                any(),
                any()
        );
        verify(kaizenService, never()).recordSystemicDefectProposal(eq(projectId), any(), any(), any());
    }

    // --- sixSigmaMetric closes the loop onto a real measured stream (2026-08-07, Kaizen audit follow-on) --

    @Test
    void epicsOwnSixSigmaMetricIsCarriedOntoItsSnapshotsAndIntoTheOutOfControlProposal() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);

        List<FeatureEntity> epics = List.of(
                epic(f1, project, "100% integration test coverage for external API adapters"),
                epic(f2, project, "100% integration test coverage for external API adapters"),
                epic(f3, project, "Zero unauthorized document access incidents"));
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(epics);

        stubCompletedEpic(f1, project, t0);
        stubCompletedEpic(f2, project, t0.plus(1, ChronoUnit.DAYS));
        stubCompletedEpic(f3, project, t0.plus(2, ChronoUnit.DAYS));

        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f1))).thenReturn(new DefectOpportunityCount(1, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f2))).thenReturn(new DefectOpportunityCount(1, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(f3))).thenReturn(new DefectOpportunityCount(8, 10));

        List<ProcessControlSnapshotEntity> saved = service.recomputeForProject(projectId).stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted((a, b) -> Integer.compare(a.getSequenceIndex(), b.getSequenceIndex()))
                .toList();

        assertThat(saved.get(0).getSixSigmaMetricLabel()).isEqualTo("100% integration test coverage for external API adapters");
        assertThat(saved.get(2).getSixSigmaMetricLabel()).isEqualTo("Zero unauthorized document access incidents");

        // The out-of-control proposal for f3's excursion must surface f3's OWN metric text, not f1/f2's -
        // a human reviewer needs to know what quality target was actually missed.
        org.mockito.ArgumentCaptor<String> descriptionCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kaizenService, times(1)).recordSystemicDefectProposal(eq(projectId), any(), any(), descriptionCaptor.capture());
        assertThat(descriptionCaptor.getValue()).contains("Zero unauthorized document access incidents");
    }

    @Test
    void eightConsecutiveSameSideTriggersWesternElectricSignal() {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        Instant t0 = Instant.now().minus(20, ChronoUnit.DAYS);
        List<FeatureEntity> epics = new ArrayList<>();
        List<UUID> featureIds = new ArrayList<>();

        // 2 baseline эпики at u=0.2 -> centerline 0.2, UCL = 0.2 + 3*sqrt(0.02) ≈ 0.624, LCL = 0
        for (int i = 0; i < 2; i++) {
            UUID fid = UUID.randomUUID();
            featureIds.add(fid);
            epics.add(epic(fid, project));
            stubCompletedEpic(fid, project, t0.plus(i, ChronoUnit.DAYS));
            when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(fid))).thenReturn(new DefectOpportunityCount(2, 10));
        }
        // 8 monitoring эпики at u=0.1, all below centerline but within [LCL, UCL] - no single-point excursion
        for (int i = 0; i < 8; i++) {
            UUID fid = UUID.randomUUID();
            featureIds.add(fid);
            epics.add(epic(fid, project));
            stubCompletedEpic(fid, project, t0.plus(2 + i, ChronoUnit.DAYS));
            when(sixSigmaAuditService.computeQualityGateCounts(eq(null), eq(fid))).thenReturn(new DefectOpportunityCount(1, 10));
        }
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(epics);

        List<ProcessControlSnapshotEntity> saved = service.recomputeForProject(projectId).stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted((a, b) -> Integer.compare(a.getSequenceIndex(), b.getSequenceIndex()))
                .toList();

        assertThat(saved).hasSize(10);
        // First 7 monitoring points (index 2..8) haven't accumulated 8 same-side points yet
        for (int i = 2; i <= 8; i++) {
            assertThat(saved.get(i).getWesternElectricSignal()).isNull();
        }
        // The 8th monitoring point (index 9) completes the run of 8 consecutive below-centerline points
        assertThat(saved.get(9).getWesternElectricSignal()).isEqualTo("8_CONSECUTIVE_SAME_SIDE");
        assertThat(saved.get(9).isOutOfControl()).isTrue();
        // No single point exceeded 3σ limits on its own
        assertThat(saved.get(9).getU()).isLessThan(saved.get(9).getUpperControlLimit());
    }
}
