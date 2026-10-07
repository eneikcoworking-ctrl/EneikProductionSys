package com.eneik.production.services.quality;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.audit.SixSigmaAuditService.DefectOpportunityCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for ProcessControlService.
 * Validates epistemic, process-control, and architectural measurement invariants under Stage 4:
 * - D010 Alvin Goldman Level of Abstraction Lock (ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK):
 *     Strict demarcation of u-chart subgroups by completed epics WITHIN one project. Global/factory-wide
 *     scans (findAll on reviews and conflicts) are strictly eliminated in favor of project-scoped
 *     ScopedEvidencePacket, preventing cross-project data leakage and ensuring purity of measurement.
 * - D008 Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS):
 *     Fixed baseline centerline and limits (Phase 1/Phase 2 discipline). The centerline and 3-sigma control
 *     limits are computed once from the first baselineEpicCount completed epics and held fixed; a chart
 *     that recalculates its limits on every point would normalize process drift and fail to falsify it.
 * - D006 Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE):
 *     Deontic signaling to KaizenService: 3-sigma excursions and Western Electric run violations are
 *     flagged as out-of-control conditions, prompting systemic defect investigations without silent
 *     auto-suppression.
 */
class ProcessControlServiceFalsificationTest {

    private FeatureRepository featureRepository;
    private TaskRepository taskRepository;
    private ProcessControlSnapshotRepository snapshotRepository;
    private SixSigmaAuditService sixSigmaAuditService;
    private ReviewConcernRepository reviewConcernRepository;
    private PrReviewRepository prReviewRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesSessionRepository julesSessionRepository;
    private DefectJournalRepository defectJournalRepository;
    private KaizenService kaizenService;
    private ProjectRepository projectRepository;

    private ProcessControlService service;
    private final UUID projectId = UUID.randomUUID();

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

        when(snapshotRepository.findByProjectIdAndStreamOrderBySequenceIndexAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewConcernRepository.findByFeatureId(any())).thenReturn(Collections.emptyList());
        when(defectJournalRepository.findByFeatureId(any())).thenReturn(Collections.emptyList());
        when(projectRepository.findById(any())).thenReturn(Optional.empty());

        when(sixSigmaAuditService.computeQualityGateCounts(any(), any()))
                .thenReturn(new DefectOpportunityCount(0, 10));
        when(sixSigmaAuditService.computePrConflictCounts(any(), any(), any(), any()))
                .thenReturn(new DefectOpportunityCount(0, 0));

        when(julesSessionRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());
        when(prReviewRepository.findByJulesSessionIdIn(any())).thenReturn(Collections.emptyList());
        when(taskConflictRepository.findByTaskIdIn(any())).thenReturn(Collections.emptyList());

        service = new ProcessControlService(
                featureRepository, taskRepository, snapshotRepository,
                sixSigmaAuditService, reviewConcernRepository, prReviewRepository,
                taskConflictRepository, julesSessionRepository, defectJournalRepository,
                kaizenService, projectRepository, null
        );
        ReflectionTestUtils.setField(service, "baselineEpicCount", 2);
    }

    private FeatureEntity createEpic(UUID id) {
        FeatureEntity f = new FeatureEntity();
        f.setId(id);
        f.setProjectId(projectId);
        f.setCreatedAt(Instant.now());
        return f;
    }

    private void completeEpicWithTerminalTasks(UUID featureId, Instant completedAt) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setFeatureId(featureId);
        task.setStatus(TaskStatus.done);
        task.setCreatedAt(completedAt.minusSeconds(100));
        task.setUpdatedAt(completedAt);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(task));
    }

    // =========================================================================
    // D010: Alvin Goldman Level of Abstraction Lock (ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK)
    // =========================================================================

    @Test
    @DisplayName("D010 Goldman Abstraction Lock: ScopedEvidencePacket strictly scopes reviews and conflicts without global findAll")
    void falsifyGoldmanAbstractionLock_scopedEvidencePacketPreventsCrossProjectLeakage() {
        UUID epic1 = UUID.randomUUID();
        UUID epic2 = UUID.randomUUID();
        Instant t0 = Instant.now().minus(2, ChronoUnit.DAYS);

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId))
                .thenReturn(List.of(createEpic(epic1), createEpic(epic2)));
        completeEpicWithTerminalTasks(epic1, t0);
        completeEpicWithTerminalTasks(epic2, t0.plus(1, ChronoUnit.DAYS));

        service.recomputeForProject(projectId);

        // Verification: Full-table scans are strictly banned to prevent cross-project pollution
        verify(prReviewRepository, never()).findAll();
        verify(taskConflictRepository, never()).findAll();

        // Verification: Scoped repositories queried strictly through task/session IDs
        verify(julesSessionRepository, atLeastOnce()).findByTaskIdIn(any());
        verify(taskConflictRepository, atLeastOnce()).findByTaskIdIn(any());
    }

    // =========================================================================
    // D008: Karl Popper Falsification Harness (KARL_POPPER_01_FALSIFICATION_HARNESS)
    // =========================================================================

    @Test
    @DisplayName("D008 Popper Falsification: Centerline is fixed from baseline epics and does not drift with monitoring points")
    void falsifyPopperFalsification_fixedCenterlineDoesNotRenormalizeDrift() {
        UUID epic1 = UUID.randomUUID();
        UUID epic2 = UUID.randomUUID();
        UUID epic3 = UUID.randomUUID(); // Monitoring point with defect surge
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId))
                .thenReturn(List.of(createEpic(epic1), createEpic(epic2), createEpic(epic3)));
        completeEpicWithTerminalTasks(epic1, t0);
        completeEpicWithTerminalTasks(epic2, t0.plus(1, ChronoUnit.DAYS));
        completeEpicWithTerminalTasks(epic3, t0.plus(2, ChronoUnit.DAYS));

        // Baseline: Epic 1 has 2/10 defects, Epic 2 has 2/10 defects -> pooled u_bar = 4/20 = 0.20
        // Monitoring: Epic 3 has massive surge: 9/10 defects (u = 0.90)
        when(sixSigmaAuditService.computeQualityGateCounts(isNull(), eq(epic1)))
                .thenReturn(new DefectOpportunityCount(2, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(isNull(), eq(epic2)))
                .thenReturn(new DefectOpportunityCount(2, 10));
        when(sixSigmaAuditService.computeQualityGateCounts(isNull(), eq(epic3)))
                .thenReturn(new DefectOpportunityCount(9, 10));

        List<ProcessControlSnapshotEntity> snapshots = service.recomputeForProject(projectId);

        List<ProcessControlSnapshotEntity> qgSnapshots = snapshots.stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted(Comparator.comparingInt(ProcessControlSnapshotEntity::getSequenceIndex))
                .toList();

        assertThat(qgSnapshots).hasSize(3);

        ProcessControlSnapshotEntity snap1 = qgSnapshots.get(0);
        ProcessControlSnapshotEntity snap2 = qgSnapshots.get(1);
        ProcessControlSnapshotEntity snap3 = qgSnapshots.get(2);

        // Center line must remain exactly 0.20 for all points (Phase 1 baseline locked!)
        assertThat(snap1.getCenterLine()).isEqualTo(0.20);
        assertThat(snap2.getCenterLine()).isEqualTo(0.20);
        assertThat(snap3.getCenterLine())
                .as("Popperian test: Centerline must NOT drift to renormalize the surge; it stays locked at baseline 0.20")
                .isEqualTo(0.20);

        // Epic 3 has u = 0.90 > UCL (~0.62), so it must be marked outOfControl
        assertThat(snap3.isOutOfControl()).isTrue();
        assertThat(snap3.getU()).isEqualTo(0.90);
        assertThat(snap3.getUpperControlLimit()).isLessThan(0.90);
    }

    // =========================================================================
    // D006: Joseph Raz Deontic Prohibition As Code (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE)
    // =========================================================================

    @Test
    @DisplayName("D006 Raz Prohibition As Code: Western Electric run of 8 consecutive points on one side signals out-of-control")
    void falsifyRazProhibitionAsCode_westernElectricRunSignalsOutOfControl() {
        // baselineEpicCount = 2, followed by 8 consecutive monitoring points above centerline
        int totalEpics = 10;
        List<FeatureEntity> epics = new ArrayList<>();
        Instant t0 = Instant.now().minus(15, ChronoUnit.DAYS);

        for (int i = 0; i < totalEpics; i++) {
            UUID id = UUID.randomUUID();
            epics.add(createEpic(id));
            completeEpicWithTerminalTasks(id, t0.plus(i, ChronoUnit.DAYS));

            if (i < 2) {
                // Baseline points: 5 defects out of 100 opportunities -> centerLine = 0.05
                when(sixSigmaAuditService.computeQualityGateCounts(isNull(), eq(id)))
                        .thenReturn(new DefectOpportunityCount(5, 100));
            } else {
                // 8 consecutive monitoring points with u = 0.07 > centerLine 0.05, but within 3-sigma UCL (~0.117)
                when(sixSigmaAuditService.computeQualityGateCounts(isNull(), eq(id)))
                        .thenReturn(new DefectOpportunityCount(7, 100));
            }
        }

        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(epics);

        List<ProcessControlSnapshotEntity> snapshots = service.recomputeForProject(projectId);
        List<ProcessControlSnapshotEntity> qgSnapshots = snapshots.stream()
                .filter(s -> ProcessControlService.STREAM_QUALITY_GATE.equals(s.getStream()))
                .sorted(Comparator.comparingInt(ProcessControlSnapshotEntity::getSequenceIndex))
                .toList();

        assertThat(qgSnapshots).hasSize(10);

        // Point index 9 is the 8th consecutive monitoring point above centerline -> Western Electric rule triggers!
        ProcessControlSnapshotEntity lastPoint = qgSnapshots.get(9);
        assertThat(lastPoint.isOutOfControl()).isTrue();
        assertThat(lastPoint.getWesternElectricSignal()).isEqualTo("8_CONSECUTIVE_SAME_SIDE");
    }
}
