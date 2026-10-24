package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.dto.TaskShortDto;
import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.judgment.DeliveredWorkJudgmentService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalFlowCoreService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.orchestration.BranchGarbageCollectorService;
import com.eneik.production.services.runtime.ClientRuntimeObservabilityService;
import com.eneik.production.services.runtime.ProductLaunchabilityService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.toc.TocSubordinationLever;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Harness for 100% Autonomous Self-Healing & Dennettian Multiple Drafts Flow
 * (Zero Cartesian Theater / Zero External Homunculus).
 *
 * Corpus-verified philosophical patterns:
 * - GILBERT_RAYL_03_CATEGORY_ERROR_SCAN / DZHON_OSTIN_02_CATEGORY_ERROR_SCAN [D002]:
 *   {@code ProjectStatus.stalled} is a telemetry observation demanding autonomous forward action,
 *   never a lifecycle freeze or eviction from OperationalPolicyService / OperationalFlowCoreService.
 * - NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012] & NUEL_BELNAP_07_INUS_FACTOR_CHECK [D007]:
 *   {@code stalled} remains mutually exclusive from {@code active} while {@code isOperational()} unites both,
 *   and dependency-blocked queued children never trigger false stall verdicts while parents run.
 * - ENDI_KLARK_01_PERCEPTION_ACTION_LOOP [D011] & ALONZO_CHERCH_21_DERIVED_CUTOFF [D010]:
 *   Bounded exponential homeostatic recovery epochs (15m, 60m, 240m, ceiling = 3) revive starving root
 *   frontier tasks and terminate strictly at epoch 3.
 * - RUT_MILLIKEN_06_TELEOSEMANTIC_FEEDBACK [D011] & KARL_POPPER_01_FALSIFICATION_HARNESS [D008]:
 *   Transitive dead-dependency propagation + immediate same-tick Multiple Drafts handoff to
 *   {@link DeliveryRealityProducerService#produceForProject(ProjectEntity)}.
 */
class DennettAutonomyFalsificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("D002/D012: ProjectStatus.stalled is operational and authorizes recovery/merge/dispatch while frozen/accepted remain blocked")
    void stalledProjectAuthorizesAutonomousRecoveryMergeAndDispatchWhileFrozenRemainsBlocked() {
        assertTrue(ProjectStatus.active.isOperational());
        assertTrue(ProjectStatus.stalled.isOperational());
        assertFalse(ProjectStatus.stalled.isActive(), "Belnap truth-status separation must remain intact");
        assertTrue(ProjectStatus.stalled.isStalled());
        assertFalse(ProjectStatus.frozen.isOperational());
        assertFalse(ProjectStatus.accepted.isOperational());
        assertFalse(ProjectStatus.archived.isOperational());
        assertFalse(ProjectStatus.waiting.isOperational());
        assertFalse(ProjectStatus.analyzing.isOperational());

        OperationalPolicyService policyService = new OperationalPolicyService(mock(OperationalFlowCoreService.class));

        FlowCoreDto stalledCore = buildCore("SYSTEM_STALLED", "stalled", 2, 1, 1, 1, 0, 2, 5, 2);
        assertNotEquals("ENFORCED_PROJECT_NOT_MUTABLE", stalledCore.authorization().status(),
                "Stalled project must never be classified as ENFORCED_PROJECT_NOT_MUTABLE");
        assertTrue(policyService.authorize(stalledCore, OperationalAction.RECOVER_FAILED_FRONTIER).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.ORCHESTRATE).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.DISPATCH_REVIEW_TASKS).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.MERGE_PR).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.SYNC_GITHUB).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.CHECK_COVERAGE_AUDITS).allowed());
        assertTrue(policyService.authorize(stalledCore, OperationalAction.RUN_PROJECT_AUDIT_PIPELINE).allowed());

        // Popperian refutation: genuinely non-operational statuses (frozen, accepted) still deny all autonomous mutation
        FlowCoreDto frozenCore = buildCore("FROZEN", "frozen", 2, 1, 1, 1, 0, 2, 5, 2);
        assertEquals("ENFORCED_PROJECT_NOT_MUTABLE", frozenCore.authorization().status());
        assertFalse(policyService.authorize(frozenCore, OperationalAction.RECOVER_FAILED_FRONTIER).allowed());
        assertFalse(policyService.authorize(frozenCore, OperationalAction.ORCHESTRATE).allowed());
        assertFalse(policyService.authorize(frozenCore, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        assertFalse(policyService.authorize(frozenCore, OperationalAction.MERGE_PR).allowed());
    }

    @Test
    @DisplayName("D008/D010: Transitive dead-dependency propagation across multi-hop chains (A -> B -> C)")
    void transitiveDeadDependencyPropagatesAcrossMultiHopChains() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
        ClaimService claimService = mock(ClaimService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        PlannedWorkRecoveryService recovery = new PlannedWorkRecoveryService(
                taskRepository, wishlistRepository, sessionRepository, claimService, readinessService, objectMapper);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.stalled);

        WishlistEntity brief = new WishlistEntity();
        brief.setId(UUID.randomUUID());
        brief.setProjectId(project.getId());
        brief.setSource(WishlistSource.client);
        when(wishlistRepository.findById(brief.getId())).thenReturn(Optional.of(brief));

        TaskEntity rootA = failedProductTask(project, brief.getId(), 1); // exhausted!
        TaskEntity midB = failedProductTask(project, brief.getId(), 0);  // unspent, depends on exhausted A
        midB.setDependsOn(rootA);
        TaskEntity leafC = failedProductTask(project, brief.getId(), 0); // unspent, depends on B
        leafC.setDependsOn(midB);

        when(readinessService.isDependencySatisfied(rootA)).thenReturn(false);
        when(readinessService.isDependencySatisfied(midB)).thenReturn(false);

        assertFalse(recovery.mayStillBeResumed(rootA));
        assertFalse(recovery.mayStillBeResumed(midB),
                "Child B whose parent A exhausted its resume budget cannot come back on its own");
        assertFalse(recovery.mayStillBeResumed(leafC),
                "Grandchild C behind B -> exhausted A must also be recognized as transitively unresumable");

        Map<UUID, WishlistEntity> briefMap = Map.of(brief.getId(), brief);
        assertTrue(PlannedWorkRecoveryService.isDeadForGood(rootA, briefMap));
        assertTrue(PlannedWorkRecoveryService.isDeadForGood(midB, briefMap));
        assertTrue(PlannedWorkRecoveryService.isDeadForGood(leafC, briefMap));

        // Popperian refutation: when root A still has its resume budget (0), both B and C CAN still be resumed after A
        ((ObjectNode) rootA.getPayload()).put("ems_bounded_plan_resume_count", 0);
        assertTrue(recovery.mayStillBeResumed(rootA));
        assertTrue(recovery.mayStillBeResumed(midB));
        assertTrue(recovery.mayStillBeResumed(leafC));
        assertFalse(PlannedWorkRecoveryService.isDeadForGood(midB, briefMap));
    }

    @Test
    @DisplayName("D007: Queued task waiting on an actively running parent session does NOT trigger false SYSTEM_STALLED")
    void dependencyBlockedQueuedChildDoesNotCountAsStalledIdleWork() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("dmitryefremov-site");
        project.setStatus(ProjectStatus.active);

        TaskEntity parentInFlight = new TaskEntity();
        parentInFlight.setId(UUID.randomUUID());
        parentInFlight.setProject(project);
        parentInFlight.setStatus(TaskStatus.claimed);

        TaskEntity queuedChild = new TaskEntity();
        queuedChild.setId(UUID.randomUUID());
        queuedChild.setProject(project);
        queuedChild.setStatus(TaskStatus.queued);
        queuedChild.setDependsOn(parentInFlight);

        JulesSessionEntity liveSession = new JulesSessionEntity();
        liveSession.setTaskId(parentInFlight.getId());
        liveSession.setStatus("running");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.stalled)).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(parentInFlight, queuedChild));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(julesSessionRepository.findByTaskId(parentInFlight.getId())).thenReturn(List.of(liveSession));

        ContinuousOrchestrationService orchestration = new ContinuousOrchestrationService(
                projectRepository, mock(ProjectFlowService.class), mock(AccountRepository.class),
                julesSessionRepository, mock(JulesDispatchService.class), wishlistRepository,
                mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class), taskRepository,
                new SystemProgressTracker(), mock(SystemSettingsService.class), mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class), mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class), mock(AccountHealthService.class),
                mock(ProductLaunchabilityService.class), mock(ClientRuntimeObservabilityService.class),
                mock(DeliveredWorkJudgmentService.class), mock(TocSubordinationLever.class));

        ContinuousOrchestrationService.SystemWorkSnapshot waitingSnapshot = orchestration.systemWorkSnapshot();
        assertEquals(0, waitingSnapshot.queuedTasks(),
                "Queued child waiting on active parent must not be counted as stalled dispatchable work");
        assertEquals(0, waitingSnapshot.activeNonTerminalTasks());
        assertFalse(waitingSnapshot.hasActionableWork());

        // Popperian refutation: once parent finishes (done), queued child immediately becomes actionable work
        parentInFlight.setStatus(TaskStatus.done);
        ContinuousOrchestrationService.SystemWorkSnapshot readySnapshot = orchestration.systemWorkSnapshot();
        assertEquals(1, readySnapshot.queuedTasks());
        assertTrue(readySnapshot.hasActionableWork());
    }

    @Test
    @DisplayName("D011/D010: Bounded exponential homeostatic recovery epochs (15m, 60m, 240m) terminate strictly at ceiling=3")
    void homeostaticStallRecoveryGrantsBoundedExponentialEpochsAndTerminatesAtCeiling() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
        ClaimService claimService = mock(ClaimService.class);
        ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
        PlannedWorkRecoveryService recovery = new PlannedWorkRecoveryService(
                taskRepository, wishlistRepository, sessionRepository, claimService, readinessService, objectMapper);
        ReflectionTestUtils.setField(recovery, "frontierResumeLimit", 3);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.stalled);

        WishlistEntity brief = new WishlistEntity();
        brief.setId(UUID.randomUUID());
        brief.setProjectId(project.getId());
        brief.setSource(WishlistSource.client);
        brief.setStatus(WishlistStatus.converted_to_task);

        Instant t0 = Instant.parse("2026-10-24T00:00:00Z");
        TaskEntity root = failedProductTask(project, brief.getId(), 1);
        ((ObjectNode) root.getPayload()).put("ems_bounded_plan_resume_at", t0.toString());

        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(5, 2, 10, 4, 0.4, true));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(root));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(brief));
        when(wishlistRepository.findById(brief.getId())).thenReturn(Optional.of(brief));
        when(sessionRepository.findByTaskId(root.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(root.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(root.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);

        // Before 15m cooldown -> refuses
        assertEquals(0, recovery.recoverStarvingFrontierForAutonomy(project, t0.plus(Duration.ofMinutes(10))));

        // Epoch 1 at t0 + 16m (>= 15m)
        Instant t1 = t0.plus(Duration.ofMinutes(16));
        assertEquals(1, recovery.recoverStarvingFrontierForAutonomy(project, t1));
        assertEquals(TaskStatus.queued, root.getStatus());
        assertEquals(1, recovery.homeostaticEpoch(root));

        String retiredAgainReason = "PR#42 closed without merge on GitHub; task had no active claim/session "
                + "left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)";

        // Fails again -> Epoch 2 requires 60m cooldown
        root.setStatus(TaskStatus.failed);
        root.setJulesDispatchStatus(retiredAgainReason);
        assertEquals(0, recovery.recoverStarvingFrontierForAutonomy(project, t1.plus(Duration.ofMinutes(45))));
        Instant t2 = t1.plus(Duration.ofMinutes(61));
        assertEquals(1, recovery.recoverStarvingFrontierForAutonomy(project, t2));
        assertEquals(2, recovery.homeostaticEpoch(root));

        // Fails again -> Epoch 3 requires 240m cooldown
        root.setStatus(TaskStatus.failed);
        root.setJulesDispatchStatus(retiredAgainReason);
        assertEquals(0, recovery.recoverStarvingFrontierForAutonomy(project, t2.plus(Duration.ofMinutes(180))));
        Instant t3 = t2.plus(Duration.ofMinutes(241));
        assertEquals(1, recovery.recoverStarvingFrontierForAutonomy(project, t3));
        assertEquals(3, recovery.homeostaticEpoch(root));
        assertFalse(recovery.hasRemainingHomeostaticEpoch(root));

        // Fails 4th time -> Hard Church cutoff: never revives again regardless of elapsed time
        root.setStatus(TaskStatus.failed);
        root.setJulesDispatchStatus(retiredAgainReason);
        assertEquals(0, recovery.recoverStarvingFrontierForAutonomy(project, t3.plus(Duration.ofDays(7))));
    }

    @Test
    @DisplayName("D011: Exhausted starving stalled project triggers Multiple Drafts wishlist, compiles, refreshes snapshot, and dispatches in 1 tick")
    void exhaustedStarvingStalledProjectHandsOffToMultipleDraftsAndDispatchesInSameTick() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        PlannedWorkRecoveryService plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
        OperationalPolicyService operationalPolicyService = mock(OperationalPolicyService.class);
        DeliveryRealityProducerService deliveryRealityProducerService = mock(DeliveryRealityProducerService.class);
        TocSubordinationLever subordinationLever = mock(TocSubordinationLever.class);

        when(subordinationLever.subordinate(any(), any(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(2));

        ProjectEntity stalledProject = new ProjectEntity();
        stalledProject.setId(UUID.randomUUID());
        stalledProject.setName("dmitryefremov-site");
        stalledProject.setSlug("dmitryefremov-site");
        stalledProject.setStatus(ProjectStatus.stalled);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of());
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.stalled)).thenReturn(List.of(stalledProject));

        // Snapshot progression across the single tick:
        // 1) Initial snapshot: starving stalled project (0 queued, 0 pending wishlist, 3 failed tasks, 2/5 features)
        FlowCoreDto initialStarving = buildCore("SYSTEM_STALLED", "stalled", 0, 0, 0, 0, 0, 0, 5, 2);
        // 2) After DeliveryRealityProducerService files Multiple Draft wishlist -> pendingWishlist = 1
        FlowCoreDto afterDraftFiled = buildCore("SYSTEM_STALLED", "stalled", 0, 0, 0, 1, 0, 0, 5, 2);
        // 3) After orchestrate() compiles the draft into a fresh queued task -> queuedTasks = 1
        FlowCoreDto afterCompiled = buildCore("SYSTEM_STALLED", "stalled", 1, 0, 0, 0, 0, 0, 5, 2);

        AtomicInteger snapshotCall = new AtomicInteger(0);
        when(operationalPolicyService.snapshot(stalledProject.getId())).thenAnswer(inv -> {
            int n = snapshotCall.incrementAndGet();
            if (n == 1) return initialStarving;
            if (n == 2) return afterDraftFiled;
            return afterCompiled;
        });

        OperationalPolicyService realAuthorizer = new OperationalPolicyService(mock(OperationalFlowCoreService.class));
        when(operationalPolicyService.authorize(any(FlowCoreDto.class), any(OperationalAction.class)))
                .thenAnswer(inv -> realAuthorizer.authorize(
                        (FlowCoreDto) inv.getArgument(0),
                        (OperationalAction) inv.getArgument(1)));

        when(plannedWorkRecoveryService.recoverStarvingFrontierForAutonomy(stalledProject)).thenReturn(0);
        when(projectFlowService.orchestrate(stalledProject.getId())).thenReturn(
                new OrchestrationResultDto(stalledProject.getId(), 1, List.of(mock(TaskShortDto.class)), "compiled"));

        ContinuousOrchestrationService orchestration = new ContinuousOrchestrationService(
                projectRepository, projectFlowService, mock(AccountRepository.class),
                mock(JulesSessionRepository.class), mock(JulesDispatchService.class), mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class), mock(TaskRepository.class),
                new SystemProgressTracker(), mock(SystemSettingsService.class), plannedWorkRecoveryService,
                mock(BranchGarbageCollectorService.class), mock(GitHubPullRequestService.class),
                operationalPolicyService, mock(AccountHealthService.class),
                mock(ProductLaunchabilityService.class), mock(ClientRuntimeObservabilityService.class),
                mock(DeliveredWorkJudgmentService.class), subordinationLever);
        orchestration.setDeliveryRealityProducerService(deliveryRealityProducerService);

        orchestration.executeContinuousOrchestrate();

        verify(plannedWorkRecoveryService, times(1)).recoverStarvingFrontierForAutonomy(stalledProject);
        verify(deliveryRealityProducerService, times(1)).produceForProject(stalledProject);
        verify(projectFlowService, times(1)).orchestrate(stalledProject.getId());
        verify(projectFlowService, times(1)).dispatchQueuedTasks(stalledProject.getId());
        verify(operationalPolicyService, times(1)).closeTick();
    }

    private TaskEntity failedProductTask(ProjectEntity project, UUID sourceWishlistId, int resumeCount) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setSourceWishlistId(sourceWishlistId);
        task.setFeatureId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setCreatedAt(Instant.parse("2026-10-23T12:00:00Z"));
        task.setUpdatedAt(Instant.parse("2026-10-23T12:00:00Z"));
        task.setJulesDispatchStatus(
                "Blocked task retired by iteration-admission poka-yoke; no child work created");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ems_bounded_plan_resume_count", resumeCount);
        task.setPayload(payload);
        return task;
    }

    private FlowCoreDto buildCore(String state,
                                  String projectStatus,
                                  long queuedTasks,
                                  long reviewTasks,
                                  int openReviews,
                                  long pendingWishlist,
                                  long compilingWishlist,
                                  long failedTasksResumable,
                                  int totalFeatures,
                                  int completeFeatures) {
        FlowSpineDto.Transition transition = new FlowSpineDto.Transition(
                state, "NEXT", "ContinuousOrchestrationService", "recover", List.of("evidence"), "reason");
        FlowSpineDto snapshot = new FlowSpineDto(
                Instant.EPOCH,
                "observe_only",
                new FlowSpineDto.ProjectRef(UUID.fromString("1695f808-0df7-4393-ace9-d5a79e62ef41"),
                        "dmitryefremov-site", projectStatus, "dmitryefremov-site"),
                state,
                "value_in_progress",
                "SYSTEM_STALLED".equals(state) ? "System status is stalled." : "",
                transition,
                List.of(transition),
                List.of(),
                List.of(),
                List.of(),
                new FlowSpineDto.EvidenceVector(0, openReviews, 0, 0, 0,
                        pendingWishlist, compilingWishlist, 0,
                        "stalled".equalsIgnoreCase(projectStatus) ? "stalled" : "ok", false),
                new FlowSpineDto.FlowCounts(queuedTasks, 0, reviewTasks, 10, failedTasksResumable, 3, 0,
                        totalFeatures, completeFeatures, totalFeatures, completeFeatures, completeFeatures,
                        "success", true, pendingWishlist == 0 && compilingWishlist == 0, 10, 0, 0, 0, 0.0),
                List.of(),
                new FlowSpineDto.JournalSummary(null, null, null, null, "hash", false, 0),
                "deterministic precedence"
        );
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);
        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);
        return new FlowCoreDto(
                Instant.EPOCH,
                "flow_core_enforced",
                snapshot.project(),
                snapshot,
                decision,
                authorization,
                new FlowCoreDto.MathematicalContract("facts", "decision", "precedence", "safety", List.of()),
                new FlowCoreDto.Journal(null, null, false, 0)
        );
    }
}
