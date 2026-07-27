package com.eneik.production.services.audit;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SixSigmaAuditServiceTest {

    private PrReviewRepository prReviewRepository;
    private TaskConflictRepository taskConflictRepository;
    private TaskRepository taskRepository;
    private OnboardingAuditFindingRepository onboardingAuditFindingRepository;
    private TocSentinelService tocSentinelService;

    private SixSigmaAuditService auditService;

    @BeforeEach
    void setUp() {
        prReviewRepository = mock(PrReviewRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        taskRepository = mock(TaskRepository.class);
        onboardingAuditFindingRepository = mock(OnboardingAuditFindingRepository.class);

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
                tocSentinelService
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
}
