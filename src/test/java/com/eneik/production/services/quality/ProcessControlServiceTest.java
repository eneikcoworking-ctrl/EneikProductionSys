package com.eneik.production.services.quality;

import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.ProcessControlSnapshotEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProcessControlSnapshotRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.ReviewConcernRepository;
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

    @BeforeEach
    void setUp() {
        featureRepository = mock(FeatureRepository.class);
        taskRepository = mock(TaskRepository.class);
        snapshotRepository = mock(ProcessControlSnapshotRepository.class);
        sixSigmaAuditService = mock(SixSigmaAuditService.class);
        reviewConcernRepository = mock(ReviewConcernRepository.class);
        PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
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

        service = new ProcessControlService(featureRepository, taskRepository, snapshotRepository,
                sixSigmaAuditService, reviewConcernRepository, prReviewRepository, julesSessionRepository,
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

    private void stubCompletedEpic(UUID featureId, ProjectEntity project, Instant completedAt) {
        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setStatus(TaskStatus.done);
        task.setCreatedAt(completedAt.minusSeconds(60));
        task.setUpdatedAt(completedAt);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(task));
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
