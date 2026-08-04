package com.eneik.production.services.audit;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OnboardingAuditFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SixSigmaAuditServiceTest {

    private PrReviewRepository prReviewRepository;
    private TaskConflictRepository taskConflictRepository;
    private TaskRepository taskRepository;
    private OnboardingAuditFindingRepository onboardingAuditFindingRepository;
    private TocSentinelService tocSentinelService;
    private FeatureRepository featureRepository;

    private SixSigmaAuditService auditService;

    @BeforeEach
    void setUp() {
        prReviewRepository = mock(PrReviewRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        taskRepository = mock(TaskRepository.class);
        onboardingAuditFindingRepository = mock(OnboardingAuditFindingRepository.class);
        featureRepository = mock(FeatureRepository.class);

        TocExecutionGraph graph = new TocExecutionGraph();
        TocAnomalyDetector anomalyDetector = new TocAnomalyDetector(graph);
        TocOptimizer optimizer = new TocOptimizer(graph);
        tocSentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);

        when(prReviewRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskConflictRepository.findAll()).thenReturn(Collections.emptyList());
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());
        when(onboardingAuditFindingRepository.findAll()).thenReturn(Collections.emptyList());

        ProjectRepository projectRepository = mock(ProjectRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        auditService = new SixSigmaAuditService(
                prReviewRepository,
                taskConflictRepository,
                taskRepository,
                onboardingAuditFindingRepository,
                projectRepository,
                julesSessionRepository,
                tocSentinelService,
                featureRepository
        );
    }

    @Test
    void testSigmaLevelCalculationFormula() {
        // 3.4 DPMO = 6.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(3.4)).isEqualTo(6.0);

        // 233 DPMO = 5.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(233)).isEqualTo(5.0);

        // 6210 DPMO = 4.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(6210)).isEqualTo(4.0);

        // 66807 DPMO = 3.0 Sigma
        assertThat(SixSigmaAuditService.calculateSigmaLevel(66807)).isEqualTo(3.0);
    }

    @Test
    void testFullAuditExecutionWithZeroDefects() {
        var report = auditService.calculateFullSixSigmaAudit();

        assertThat(report).isNotNull();
        assertThat(report.totalDefects()).isEqualTo(0);
        assertThat(report.dpmo()).isEqualTo(0.0);
        assertThat(report.yieldRatePercent()).isEqualTo(100.0);
        assertThat(report.sigmaLevel()).isEqualTo(6.0);
        assertThat(report.qualityTier()).isEqualTo("WORLD_CLASS_SIX_SIGMA");
    }

    // --- 3-layer Factory/Delivery/Product model (2026-08-04) ------------------------------------------

    @Test
    void factoryLayerIsGenuinelyCrossProjectNotAnAliasForOneActiveProject() {
        // Regression test for the real bug this refactor fixed: calculateFullSixSigmaAudit() used to
        // call calculateProjectSixSigmaAudit(getActiveProjectId()), which coerced a null projectId to a
        // specific project BEFORE the factory-wide (targetProjectId==null) branches ever ran - those
        // branches were unreachable dead code. Now it must call the internal calc with a real null.
        var report = auditService.calculateFullSixSigmaAudit();

        assertThat(report.projectId()).isNull();
        assertThat(report.projectName()).isEqualTo("FACTORY_WIDE_ALL_PROJECTS");
        // Runtime anomalies (Category D) are only ever counted when targetProjectId is genuinely null -
        // their presence here is proof the factory-wide branch actually ran.
        assertThat(report.defectBreakdown()).containsKey("runtimeAnomalies");
    }

    @Test
    void deliveryLayerStaysScopedToOneProjectAndExcludesRuntimeAnomalies() {
        UUID projectId = UUID.randomUUID();

        var report = auditService.calculateProjectSixSigmaAudit(projectId);

        assertThat(report.projectId()).isEqualTo(projectId);
        // Layer 2 "Delivery" deliberately never mixes in factory-wide runtime anomalies - that's Layer 1
        // only, per calculateSixSigmaAuditInternal's targetProjectId==null guard.
        assertThat(report.defectBreakdown()).doesNotContainKey("runtimeAnomalies");
    }

    @Test
    void productLayerSumsOnlyNonDismissedFeaturesAndReportsShippedEpicCount() {
        UUID projectId = UUID.randomUUID();
        FeatureEntity feature1 = new FeatureEntity();
        feature1.setId(UUID.randomUUID());
        FeatureEntity feature2 = new FeatureEntity();
        feature2.setId(UUID.randomUUID());
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature1, feature2));
        when(taskRepository.findByFeatureId(feature1.getId())).thenReturn(Collections.emptyList());
        when(taskRepository.findByFeatureId(feature2.getId())).thenReturn(Collections.emptyList());

        var report = auditService.calculateProductLayerSixSigmaAudit(projectId);

        assertThat(report.projectId()).isEqualTo(projectId);
        assertThat(report.tocOperationalMetrics()).containsEntry("shippedEpicCount", 2);
        // A dismissed feature was never in the featureRepository stub above, so it can't have
        // contributed - the query itself (findByProjectIdAndDismissedAtIsNull) is what enforces this.
    }

    @Test
    void productLayerWithNoFeaturesIsNotApplicableNotZeroDefects() {
        UUID projectId = UUID.randomUUID();
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(Collections.emptyList());

        var report = auditService.calculateProductLayerSixSigmaAudit(projectId);

        assertThat(report.tocOperationalMetrics()).containsEntry("shippedEpicCount", 0);
        assertThat(report.sigmaLevel()).isEqualTo(6.0);
    }
}
