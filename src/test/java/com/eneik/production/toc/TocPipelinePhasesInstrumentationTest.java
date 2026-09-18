package com.eneik.production.toc;

import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.AutoMergeService;
import com.eneik.production.services.BottleneckAwarePriorityService;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ContinuousOrchestrationService;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocStages;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verification & Falsification harness for Prescription 46 (D007 INUS_FACTOR_CHECK).
 *
 * Grounding:
 * - BARCAN-TAG-05_NECESSARY-IDENTITY:02:dzh-l-makki / INUS_FACTOR_CHECK [D007, Mackie 1974]:
 *   A constraint cannot be identified without comparative measurement among candidate factors.
 *   Eliminates single-sensor bottleneck bias where AUTOMERGE_PROCESSING was predetermined as
 *   the system bottleneck solely due to being the only instrumented node.
 * - ALFRED_TARSKIY_01_FALSIFICATION_HARNESS (D008):
 *   Falsifies the previous single-sensor condition by showing that when pipeline phases are
 *   instrumented, the primary constraint dynamically shifts to stages with higher latency/utilization.
 */
class TocPipelinePhasesInstrumentationTest {

    private TocExecutionGraph graph;
    private TocAnomalyDetector anomalyDetector;
    private TocOptimizer optimizer;
    private TocSentinelService tocSentinelService;

    @BeforeEach
    void setUp() {
        graph = new TocExecutionGraph();
        anomalyDetector = new TocAnomalyDetector(graph);
        optimizer = new TocOptimizer(graph, 15);
        tocSentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);
    }

    @Test
    @DisplayName("INUS_FACTOR_CHECK: Pipeline phases populate execution graph with multiple nodes with non-zero history")
    void pipelinePhasesPopulateExecutionGraphWithMultipleNodesAndNonZeroHistory() {
        // 1. Continuous orchestration phase
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        when(projectRepo.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(Collections.emptyList());
        ContinuousOrchestrationService orchestrationService = new ContinuousOrchestrationService(
                projectRepo,
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                mock(JulesSessionRepository.class),
                mock(JulesDispatchService.class),
                mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class),
                mock(com.eneik.production.services.MLPredictionServiceClient.class),
                mock(TaskRepository.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(SystemSettingsService.class),
                mock(com.eneik.production.services.PlannedWorkRecoveryService.class),
                mock(com.eneik.production.services.orchestration.BranchGarbageCollectorService.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class),
                mock(DefectJournalService.class),
                mock(DefectJournalRepository.class)
        );
        orchestrationService.setTocSentinelService(tocSentinelService);
        orchestrationService.continuousOrchestrate();

        // 2. Automerge phase
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(false);
        AutoMergeService autoMergeService = new AutoMergeService(
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                mock(JulesSessionRepository.class),
                mock(TaskRepository.class),
                settingsService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(JulesDispatchService.class),
                mock(com.eneik.production.services.RoleCapabilityLoader.class),
                mock(WishlistRepository.class),
                mock(com.eneik.production.services.MLPredictionServiceClient.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                mock(com.eneik.production.services.github.GitHubApiBudgetService.class),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(com.eneik.production.services.CodeChangeClassifier.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class),
                mock(ProjectRepository.class),
                mock(com.eneik.production.services.ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class)
        );
        autoMergeService.setTocSentinelService(tocSentinelService);
        autoMergeService.processAutoMerge();

        // 3. Project flow phases (orchestrate, dispatch queued, dispatch review)
        com.eneik.production.toc.model.TocToken orchToken = tocSentinelService.startExecution("ORCHESTRATE_CYCLE", 45);
        tocSentinelService.enterStep(orchToken, TocStages.ORCHESTRATE_PROCESSING);
        tocSentinelService.exitStep(orchToken, TocStages.ORCHESTRATE_PROCESSING, true);
        tocSentinelService.endExecution(orchToken, true);

        com.eneik.production.toc.model.TocToken dispatchToken = tocSentinelService.startExecution("DISPATCH_QUEUED_TASKS", 50);
        tocSentinelService.enterStep(dispatchToken, TocStages.DISPATCH_PROCESSING);
        tocSentinelService.exitStep(dispatchToken, TocStages.DISPATCH_PROCESSING, true);
        tocSentinelService.endExecution(dispatchToken, true);

        com.eneik.production.toc.model.TocToken reviewToken = tocSentinelService.startExecution("DISPATCH_REVIEW_TASKS", 50);
        tocSentinelService.enterStep(reviewToken, TocStages.REVIEW_DISPATCH_PROCESSING);
        tocSentinelService.exitStep(reviewToken, TocStages.REVIEW_DISPATCH_PROCESSING, true);
        tocSentinelService.endExecution(reviewToken, true);

        com.eneik.production.toc.model.TocToken julesToken = tocSentinelService.startExecution("JULES_DISPATCH_CYCLE", 50);
        tocSentinelService.enterStep(julesToken, TocStages.JULES_DISPATCH_PROCESSING);
        tocSentinelService.exitStep(julesToken, TocStages.JULES_DISPATCH_PROCESSING, true);
        tocSentinelService.endExecution(julesToken, true);

        // Verification: Graph now contains multiple candidate nodes with non-zero history
        assertThat(graph.getAllNodes().size()).isGreaterThanOrEqualTo(5);

        TocNode automergeNode = graph.getNode(TocStages.AUTOMERGE_PROCESSING);
        TocNode orchestrationNode = graph.getNode(TocStages.ORCHESTRATION_PROCESSING);
        TocNode orchestrateNode = graph.getNode(TocStages.ORCHESTRATE_PROCESSING);
        TocNode dispatchNode = graph.getNode(TocStages.DISPATCH_PROCESSING);
        TocNode reviewDispatchNode = graph.getNode(TocStages.REVIEW_DISPATCH_PROCESSING);
        TocNode julesDispatchNode = graph.getNode(TocStages.JULES_DISPATCH_PROCESSING);

        assertThat(automergeNode).isNotNull();
        assertThat(orchestrationNode).isNotNull();
        assertThat(orchestrateNode).isNotNull();
        assertThat(dispatchNode).isNotNull();
        assertThat(reviewDispatchNode).isNotNull();
        assertThat(julesDispatchNode).isNotNull();

        assertThat(automergeNode.getCompletedCount()).isGreaterThan(0);
        assertThat(orchestrationNode.getCompletedCount()).isGreaterThan(0);
        assertThat(orchestrateNode.getCompletedCount()).isGreaterThan(0);
        assertThat(dispatchNode.getCompletedCount()).isGreaterThan(0);
        assertThat(reviewDispatchNode.getCompletedCount()).isGreaterThan(0);
        assertThat(julesDispatchNode.getCompletedCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("FALSIFICATION: Primary constraint shifts away from AUTOMERGE_PROCESSING when other stage has higher latency")
    void primaryConstraintShiftsAwayFromAutomergeWhenOtherStageHasHigherLatency() {
        // Fast automerge: 10 ms mean
        TocNode automerge = graph.getOrCreateNode(TocStages.AUTOMERGE_PROCESSING);
        automerge.recordExecution(10_000_000L, true);

        // Fast orchestration: 20 ms mean
        TocNode orchestration = graph.getOrCreateNode(TocStages.ORCHESTRATION_PROCESSING);
        orchestration.recordExecution(20_000_000L, true);

        // Heavy compiler/wishlist orchestration: 4500 ms mean
        TocNode orchestrate = graph.getOrCreateNode(TocStages.ORCHESTRATE_PROCESSING);
        orchestrate.recordExecution(4_500_000_000L, true);

        // Slow external Jules dispatch: 12000 ms mean
        TocNode julesDispatch = graph.getOrCreateNode(TocStages.JULES_DISPATCH_PROCESSING);
        julesDispatch.recordExecution(12_000_000_000L, true);

        DbrStatus status = optimizer.evaluateConstraintsAndDbr();

        // Direct falsification of single-sensor bias:
        // AUTOMERGE_PROCESSING was the previous predetermined bottleneck.
        // With comparative candidate measurement, JULES_DISPATCH_PROCESSING is identified as the true constraint!
        assertThat(status.primaryConstraintNode()).isNotEqualTo(TocStages.AUTOMERGE_PROCESSING);
        assertThat(status.primaryConstraintNode()).isEqualTo(TocStages.JULES_DISPATCH_PROCESSING);
        assertThat(status.recommendation()).contains("System flow optimal");
        assertThat(status.recommendation()).contains(TocStages.JULES_DISPATCH_PROCESSING);
    }

    @Test
    @DisplayName("FALSIFICATION: Shift to DISPATCH_PROCESSING when queued dispatch has in-flight backlog")
    void primaryConstraintShiftsToDispatchProcessingWhenBacklogAccumulates() {
        TocNode automerge = graph.getOrCreateNode(TocStages.AUTOMERGE_PROCESSING);
        automerge.recordExecution(5_000_000L, true);

        TocNode dispatch = graph.getOrCreateNode(TocStages.DISPATCH_PROCESSING);
        dispatch.recordExecution(50_000_000L, true);
        // Simulate in-flight queue accumulating at dispatch
        dispatch.incrementInFlight();
        dispatch.incrementInFlight();
        dispatch.incrementInFlight();

        DbrStatus status = optimizer.evaluateConstraintsAndDbr();

        assertThat(status.primaryConstraintNode()).isEqualTo(TocStages.DISPATCH_PROCESSING);
        assertThat(status.constraintQueueLength()).isEqualTo(3);
    }

    @Test
    @DisplayName("BottleneckAwarePriorityService recognizes shifted constraint and prioritizes work")
    void bottleneckAwarePriorityServiceRecognizesShiftedConstraint() {
        BottleneckDetectionService bottleneckDetectionService = mock(BottleneckDetectionService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ClaimService claimService = mock(ClaimService.class);

        // Setup candidate stages in graph
        TocNode automerge = graph.getOrCreateNode(TocStages.AUTOMERGE_PROCESSING);
        automerge.recordExecution(10_000_000L, true);

        TocNode orchestrate = graph.getOrCreateNode(TocStages.ORCHESTRATE_PROCESSING);
        orchestrate.recordExecution(8_000_000_000L, true);

        optimizer.evaluateConstraintsAndDbr();
        assertThat(tocSentinelService.getCurrentConstraintName()).isEqualTo(TocStages.ORCHESTRATE_PROCESSING);

        BottleneckAwarePriorityService priorityService = new BottleneckAwarePriorityService(
                bottleneckDetectionService,
                taskRepository,
                claimService,
                tocSentinelService
        );

        int priorityForOrchestrate = priorityService.computePriority(TocStages.ORCHESTRATE_PROCESSING);
        int priorityForAutomerge = priorityService.computePriority(TocStages.AUTOMERGE_PROCESSING);

        assertThat(priorityForOrchestrate).isEqualTo(100);
        assertThat(priorityForAutomerge).isEqualTo(0);
    }
}
