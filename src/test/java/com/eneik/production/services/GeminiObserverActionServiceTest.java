package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Every one of her five actions must go through the same operational policy gate as any other mutating
 * path in the system (2026-07-30) - no side door around the enforced Flow Core, exactly the gap found while
 * diagnosing why she was silently doing nothing on a hard-stopped project.
 */
class GeminiObserverActionServiceTest {

    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesDispatchService julesDispatchService;
    private FalsificationCycleService falsificationCycleService;
    private GeminiObserverActionRepository actionRepository;
    private OperationalPolicyService operationalPolicyService;
    private PlannedWorkRecoveryService plannedWorkRecoveryService;
    private com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;
    private com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private PersistentWorkerSessionService persistentWorkerSessionService;
    private GeminiObserverActionService service;

    private void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesDispatchService = mock(JulesDispatchService.class);
        falsificationCycleService = mock(FalsificationCycleService.class);
        actionRepository = mock(GeminiObserverActionRepository.class);
        operationalPolicyService = mock(OperationalPolicyService.class);
        plannedWorkRecoveryService = mock(PlannedWorkRecoveryService.class);
        branchGarbageCollectorService = mock(com.eneik.production.services.orchestration.BranchGarbageCollectorService.class);
        featureThreadRepository = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        gitHubPullRequestService = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        persistentWorkerSessionService = mock(PersistentWorkerSessionService.class);
        service = new GeminiObserverActionService(wishlistRepository, taskRepository, taskConflictRepository,
                julesDispatchService, falsificationCycleService, actionRepository, operationalPolicyService,
                plannedWorkRecoveryService, branchGarbageCollectorService, featureThreadRepository,
                gitHubPullRequestService, persistentWorkerSessionService);
    }

    private ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        return p;
    }

    private OperationalPolicyService.OperationalDecision decision(ProjectEntity project, OperationalAction action,
                                                                    boolean allowed, String reason) {
        return new OperationalPolicyService.OperationalDecision(
                project.getId(), action, allowed, "SOME_STATE", "STATUS", reason, List.of(), null);
    }

    @Test
    void policyDenialBlocksTheActionAndNeverTouchesTheWishlist() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.DISMISS_WISHLIST))
                .thenReturn(decision(project, OperationalAction.DISMISS_WISHLIST, false,
                        "Operational action DISMISS_WISHLIST denied by Flow Core state GITHUB_RATE_LIMITED."));

        String outcome = service.dismissWishlist(project, wishlistId.toString(), "looks dead");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(wishlistRepository);
        ArgumentCaptor<GeminiObserverActionEntity> captor = ArgumentCaptor.forClass(GeminiObserverActionEntity.class);
        verify(actionRepository).save(captor.capture());
        assertEquals("denied", captor.getValue().getOutcome());
        assertTrue(captor.getValue().isVerified());
    }

    @Test
    void policyAllowLetsTheActionProceedAsBefore() {
        setUp();
        ProjectEntity project = project();
        UUID wishlistId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.DISMISS_WISHLIST))
                .thenReturn(decision(project, OperationalAction.DISMISS_WISHLIST, true, "allowed"));
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.pending);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));

        String outcome = service.dismissWishlist(project, wishlistId.toString(), "looks dead");

        assertEquals("success", outcome);
        assertEquals(WishlistStatus.dismissed, wishlist.getStatus());
        verify(wishlistRepository).save(wishlist);
    }

    @Test
    void triggerFalsificationRunIsGatedByTheAuditPipelineAction() {
        // Reuses RUN_PROJECT_AUDIT_PIPELINE rather than a dedicated action type - it IS that same pipeline,
        // just pulled forward on her request.
        setUp();
        ProjectEntity project = project();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RUN_PROJECT_AUDIT_PIPELINE))
                .thenReturn(decision(project, OperationalAction.RUN_PROJECT_AUDIT_PIPELINE, false, "not ready"));

        String outcome = service.triggerFalsificationRun(project, project.getId().toString(), "readiness gate met");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(falsificationCycleService);
    }

    @Test
    void abandonConflictDeniedByPolicyNeverTouchesTheConflict() {
        setUp();
        ProjectEntity project = project();
        UUID conflictId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.ABANDON_CONFLICT))
                .thenReturn(decision(project, OperationalAction.ABANDON_CONFLICT, false, "project terminal"));

        String outcome = service.abandonConflict(project, conflictId.toString(), "beyond resolving");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(taskConflictRepository);
    }

    @Test
    void reviveFailedTaskDeniedByPolicyNeverTouchesPlannedWorkRecovery() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.REVIVE_FAILED_TASK))
                .thenReturn(decision(project, OperationalAction.REVIVE_FAILED_TASK, false, "GitHub rate limited"));

        String outcome = service.reviveFailedTask(project, taskId.toString(), "PR closed without merge");

        assertTrue(outcome.startsWith("denied:"));
        verifyNoInteractions(plannedWorkRecoveryService);
    }

    @Test
    void reviveFailedTaskCallsThePlannedWorkRecoveryAtomicResumePath() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.REVIVE_FAILED_TASK))
                .thenReturn(decision(project, OperationalAction.REVIVE_FAILED_TASK, true, "allowed"));
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.failed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(plannedWorkRecoveryService.resumeTask(taskId)).thenReturn(true);

        String outcome = service.reviveFailedTask(project, taskId.toString(), "PR closed without merge");

        assertEquals("success", outcome);
        verify(plannedWorkRecoveryService).resumeTask(taskId);
    }

    /**
     * Regression coverage for the 2026-08-03 incident (task 074efcb3/PR#38): resolveOrphanedPr must
     * re-verify against the real candidate list at execution time, never just trust the target id she was
     * given - a candidate that already resolved itself between snapshot and action must be skipped, not
     * blindly acted on (testimony vs evidence, same discipline as abandonConflict's own re-check).
     */
    @Test
    void resolveOrphanedPrSkipsWhenTheCandidateNoLongerExists() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RESOLVE_ORPHANED_PR))
                .thenReturn(decision(project, OperationalAction.RESOLVE_ORPHANED_PR, true, "allowed"));
        when(branchGarbageCollectorService.findOrphanedPrCandidates(project)).thenReturn(List.of());

        String outcome = service.resolveOrphanedPr(project, taskId.toString(), "owning session terminal");

        assertTrue(outcome.startsWith("skipped"));
        verify(branchGarbageCollectorService, never()).retireAbandonedBranchAndPR(any(), any(), any(), any(), anyString());
    }

    @Test
    void resolveOrphanedPrReusesRetireAbandonedBranchAndPrForAConfirmedCandidate() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RESOLVE_ORPHANED_PR))
                .thenReturn(decision(project, OperationalAction.RESOLVE_ORPHANED_PR, true, "allowed"));
        var candidate = new com.eneik.production.services.orchestration.BranchGarbageCollectorService.OrphanedPrCandidate(
                taskId, 38, "https://github.com/eneikdru/test-forty-first/pull/38",
                "jules-5354685196398021436-be7bfa86", "cancelled", task);
        when(branchGarbageCollectorService.findOrphanedPrCandidates(project)).thenReturn(List.of(candidate));
        when(branchGarbageCollectorService.retireAbandonedBranchAndPR(
                eq(project), eq(task), eq("jules-5354685196398021436-be7bfa86"), eq(38), anyString()))
                .thenReturn(true);

        String outcome = service.resolveOrphanedPr(project, taskId.toString(), "owning session terminal");

        assertEquals("success", outcome);
        verify(branchGarbageCollectorService).retireAbandonedBranchAndPR(
                eq(project), eq(task), eq("jules-5354685196398021436-be7bfa86"), eq(38), anyString());
    }

    /**
     * 2026-08-07 (test-forty-third live incident): collapseDuplicateTask is the only action that can clear
     * BLOCKED_BY_DUPLICATE_CONTENT, so it must never trust the target id alone - it has to independently
     * confirm the task is still part of a genuine 3+ non-terminal cluster sharing the same content key
     * before touching anything, exactly mirroring FlowSpineService.duplicateContent's own threshold.
     */
    @Test
    void collapseDuplicateTaskRefusesWhenNotPartOfAGenuineThreeWayCluster() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity target = new TaskEntity();
        target.setId(taskId);
        target.setProject(project);
        target.setStatus(TaskStatus.queued);
        target.setPayload(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("slice_title", "Frontend UI implementation"));
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.COLLAPSE_DUPLICATE_TASK))
                .thenReturn(decision(project, OperationalAction.COLLAPSE_DUPLICATE_TASK, true, "allowed"));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(target));
        // Only the target itself shares its key - no genuine cluster.
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(target));

        String outcome = service.collapseDuplicateTask(project, taskId.toString(), "looked like a duplicate");

        assertTrue(outcome.startsWith("skipped"));
        assertEquals(TaskStatus.queued, target.getStatus());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void collapseDuplicateTaskBlocksAConfirmedDuplicateFromAGenuineThreeWayCluster() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        com.fasterxml.jackson.databind.node.ObjectNode sameTitle =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("slice_title", "Frontend UI implementation");
        TaskEntity target = new TaskEntity();
        target.setId(taskId);
        target.setProject(project);
        target.setStatus(TaskStatus.queued);
        target.setPayload(sameTitle);
        TaskEntity sibling1 = new TaskEntity();
        sibling1.setId(UUID.randomUUID());
        sibling1.setStatus(TaskStatus.queued);
        sibling1.setPayload(sameTitle);
        TaskEntity sibling2 = new TaskEntity();
        sibling2.setId(UUID.randomUUID());
        sibling2.setStatus(TaskStatus.queued);
        sibling2.setPayload(sameTitle);
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.COLLAPSE_DUPLICATE_TASK))
                .thenReturn(decision(project, OperationalAction.COLLAPSE_DUPLICATE_TASK, true, "allowed"));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(target));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(target, sibling1, sibling2));

        String outcome = service.collapseDuplicateTask(project, taskId.toString(), "3-way duplicate confirmed");

        assertEquals("success", outcome);
        assertEquals(TaskStatus.blocked, target.getStatus());
        verify(taskRepository).save(target);
    }

    /**
     * 2026-08-08 (test-forty-third live incident, feature 1ad15184): retryAbandonedCloseout's whole job is
     * clearing a stale closeoutPrUrl so AutoMergeService.progressCloseout's own existing cycle re-opens a
     * fresh PR - never a new PR-opening code path of its own.
     */
    @Test
    void retryAbandonedCloseoutClearsTheStalePrUrlWhenTheThreadIsStillGenuinelyLive() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        var thread = new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setBranchName("jules-11503086808993865276-3ddbe98e");
        thread.setCloseoutPrUrl("https://github.com/eneikdru/test-forty-third/pull/53");
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETRY_FEATURE_CLOSEOUT))
                .thenReturn(decision(project, OperationalAction.RETRY_FEATURE_CLOSEOUT, true, "allowed"));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));
        when(gitHubPullRequestService.branchExists(project, "jules-11503086808993865276-3ddbe98e")).thenReturn(true);

        String outcome = service.retryAbandonedCloseout(project, featureId.toString(), "closed by branch GC bug, feature never merged");

        assertEquals("success", outcome);
        assertEquals(null, thread.getCloseoutPrUrl());
        verify(featureThreadRepository).save(thread);
    }

    @Test
    void retryAbandonedCloseoutRefusesWhenTheThreadWasFormallyAbandoned() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        var thread = new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setBranchName("jules-abandoned-branch");
        thread.setAbandonedAt(java.time.Instant.now());
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETRY_FEATURE_CLOSEOUT))
                .thenReturn(decision(project, OperationalAction.RETRY_FEATURE_CLOSEOUT, true, "allowed"));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        String outcome = service.retryAbandonedCloseout(project, featureId.toString(), "trying anyway");

        assertTrue(outcome.startsWith("skipped"));
        verify(featureThreadRepository, never()).save(any());
        verifyNoInteractions(gitHubPullRequestService);
    }

    @Test
    void retryAbandonedCloseoutRefusesWhenAlreadyMerged() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        var thread = new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setMergedToMainAt(java.time.Instant.now());
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETRY_FEATURE_CLOSEOUT))
                .thenReturn(decision(project, OperationalAction.RETRY_FEATURE_CLOSEOUT, true, "allowed"));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        String outcome = service.retryAbandonedCloseout(project, featureId.toString(), "trying anyway");

        assertTrue(outcome.startsWith("skipped"));
        verify(featureThreadRepository, never()).save(any());
    }

    @Test
    void retryAbandonedCloseoutRefusesWhenTheBranchIsGone() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        var thread = new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setBranchName("jules-deleted-branch");
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETRY_FEATURE_CLOSEOUT))
                .thenReturn(decision(project, OperationalAction.RETRY_FEATURE_CLOSEOUT, true, "allowed"));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));
        when(gitHubPullRequestService.branchExists(project, "jules-deleted-branch")).thenReturn(false);

        String outcome = service.retryAbandonedCloseout(project, featureId.toString(), "trying anyway");

        assertTrue(outcome.startsWith("skipped"));
        verify(featureThreadRepository, never()).save(any());
    }

    /**
     * 2026-08-11 (live incident: worker 924b2c9f stayed batch-in-flight 14+ hours after its carrier session
     * died). retireStuckWorker must re-verify against the real worker row at execution time, not just trust
     * the target id she was given, and must be a no-op if it already got retired between snapshot and act.
     */
    @Test
    void retireStuckWorkerRetiresAnUnretiredWorkerFoundForTheCarrierTask() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETIRE_STUCK_WORKER))
                .thenReturn(decision(project, OperationalAction.RETIRE_STUCK_WORKER, true, "allowed"));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        var worker = new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));

        String outcome = service.retireStuckWorker(project, taskId.toString(), "batch-in-flight for 14+ hours, carrier session dead");

        assertEquals("success", outcome);
        verify(persistentWorkerSessionService).retire(eq(worker), anyString());
    }

    @Test
    void retireStuckWorkerSkipsWhenAlreadyRetired() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        when(operationalPolicyService.authorize(project.getId(), OperationalAction.RETIRE_STUCK_WORKER))
                .thenReturn(decision(project, OperationalAction.RETIRE_STUCK_WORKER, true, "allowed"));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        var worker = new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        worker.setRetiredAt(java.time.Instant.now());
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));

        String outcome = service.retireStuckWorker(project, taskId.toString(), "trying anyway");

        assertTrue(outcome.startsWith("skipped"));
        verify(persistentWorkerSessionService, never()).retire(any(), anyString());
    }
}
