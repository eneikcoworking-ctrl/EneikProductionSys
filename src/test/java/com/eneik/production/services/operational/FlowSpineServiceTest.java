package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FlowSpineEventRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.dashboard.SystemStatusService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowSpineServiceTest {

    @Test
    void frozenProjectDominatesUnderlyingWork() {
        FlowSpineService.StateInputs input = input(ProjectStatus.frozen, 4, 2, 1, 0, 3, 1,
                1, 0, 2, 1, 1, 1, 5, 2, 1, 0, 5, 1, false, "stalled", true);

        assertEquals("FROZEN", FlowSpineService.decideState(input));
        assertTrue(FlowSpineService.isBlockingState("FROZEN"));
    }

    @Test
    void localDuplicateContentBlocksBeforeDispatch() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 3, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", true);

        assertEquals("BLOCKED_BY_DUPLICATE_CONTENT", FlowSpineService.decideState(input));
    }

    @Test
    void githubRateLimitBlocksBeforeReviewWhenNoLocalHardBlocker() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "github_rate_limited", false);

        assertEquals("GITHUB_RATE_LIMITED", FlowSpineService.decideState(input));
        assertTrue(FlowSpineService.isBlockingState("GITHUB_RATE_LIMITED"));
    }

    @Test
    void duplicateContentStillDominatesGithubRateLimit() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 3, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "github_rate_limited", true);

        assertEquals("BLOCKED_BY_DUPLICATE_CONTENT", FlowSpineService.decideState(input));
    }

    @Test
    void failedFrontierWithoutLiveWorkIsNotIdle() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 0, 0, 7, 0,
                0, 0, 0, 1, 0, 0, 4, 2, 1, 0, 5, 1, true, "ok", false);

        assertEquals("BLOCKED_BY_FAILED_FRONTIER", FlowSpineService.decideState(input));
    }

    @Test
    void failingReviewBlocksBeforeUnderReview() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 2, 0, 0, 0,
                0, 0, 0, 0, 2, 1, 0, 1, 2, 0, 8, 2, true, "ok", false);

        assertEquals("BLOCKED_BY_REVIEW", FlowSpineService.decideState(input));
    }

    @Test
    void queuedAndImplementingAndReviewStatesAreStable() {
        assertEquals("QUEUED", FlowSpineService.decideState(input(ProjectStatus.active, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
        assertEquals("IMPLEMENTING", FlowSpineService.decideState(input(ProjectStatus.active, 0, 1, 0, 0, 0, 0,
                0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
        assertEquals("UNDER_REVIEW", FlowSpineService.decideState(input(ProjectStatus.active, 0, 0, 1, 0, 0, 0,
                0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 3, 0, true, "ok", false)));
    }

    @Test
    void pendingClientScopeDominatesQueuedHousekeepingBeforeDecomposition() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 1, 0, 0, 2, 0, 0,
                1, 0, 0, 0, 0, 0, 1, 2, 0, 0, 0, 0, false, "ok", false);

        assertEquals("DECOMPOSING", FlowSpineService.decideState(input));
    }

    @Test
    void deliveredRequiresAllFeaturesComplete() {
        FlowSpineService.StateInputs input = input(ProjectStatus.active, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 3, 0, 0, 5, 0, 2, 2, 6, 6, true, "ok", false);

        assertEquals("DELIVERED", FlowSpineService.decideState(input));
        assertEquals("client_value_delivered", FlowSpineService.valueStatus("DELIVERED", input));
        assertFalse(FlowSpineService.isBlockingState("DELIVERED"));
    }

    @Test
    void transitionMatrixContainsDeterministicPrecedenceRows() {
        assertEquals(16, FlowSpineService.transitionMatrix().size());
        assertEquals("FROZEN", FlowSpineService.transitionMatrix().get(0).to());
        assertEquals("GITHUB_RATE_LIMITED", FlowSpineService.transitionMatrix().get(3).to());
        assertEquals("DECOMPOSING", FlowSpineService.transitionMatrix().get(7).to());
        assertEquals("QUEUED", FlowSpineService.transitionMatrix().get(8).to());
        assertEquals("IDLE_NO_ACTIONABLE_WORK", FlowSpineService.transitionMatrix().get(15).to());
    }

    @Test
    void bottleneckTaxonomySeparatesReviewAndRuntimeDefects() {
        assertEquals("review_bottleneck", FlowSpineService.bottleneckType("BLOCKED_BY_REVIEW", "ok"));
        assertEquals("github_rate_limit_bottleneck", FlowSpineService.bottleneckType("GITHUB_RATE_LIMITED", "github_rate_limited"));
        assertEquals("runtime_status_bottleneck", FlowSpineService.bottleneckType("UNKNOWN", "content_defect"));
        assertEquals("", FlowSpineService.bottleneckType("DELIVERED", "ok"));
    }

    @Test
    void slaSpecsMakeBlockingReviewHighUrgency() {
        FlowSpineService.SlaSpec review = FlowSpineService.slaForState("BLOCKED_BY_REVIEW");
        FlowSpineService.SlaSpec github = FlowSpineService.slaForState("GITHUB_RATE_LIMITED");
        FlowSpineService.SlaSpec queued = FlowSpineService.slaForState("QUEUED");
        FlowSpineService.SlaSpec delivered = FlowSpineService.slaForState("DELIVERED");

        assertEquals(30, review.minutes());
        assertEquals("high", review.severity());
        assertEquals(0, github.minutes());
        assertEquals("high", github.severity());
        assertEquals(15, queued.minutes());
        assertEquals(-1, delivered.minutes());
    }

    @Test
    void aTerminallyFailedTasksLongDeadReviewNoLongerBlocksTheWholeProjectForever() {
        // Regression test for the 2026-07-31 incident: task 529e5252 (test-fortieth) failed once, its PR
        // review reached ciStatus=closed_unmerged, and that ONE historical, already-resolved failure kept
        // the whole project in BLOCKED_BY_REVIEW indefinitely - blocking dispatch, merge, and orchestration
        // for everything else - because failingReviews/openReviews were computed over the project's ENTIRE
        // history with no exclusion for tasks whose fate is already decided.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID deadTaskId = UUID.randomUUID();
        TaskEntity deadTask = new TaskEntity();
        deadTask.setId(deadTaskId);
        deadTask.setStatus(TaskStatus.failed);
        deadTask.setDescription("Runtime Contract 20666c21");

        JulesSessionEntity deadSession = new JulesSessionEntity();
        deadSession.setId(UUID.randomUUID());
        deadSession.setTaskId(deadTaskId);
        deadSession.setStatus("stuck");

        PrReviewEntity deadReview = new PrReviewEntity();
        deadReview.setJulesSessionId(deadSession.getId());
        deadReview.setCiStatus("closed_unmerged");
        deadReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(deadTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(deadTaskId))).thenReturn(List.of(deadSession));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(deadReview));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertNotEquals("BLOCKED_BY_REVIEW", dto.currentState());
        assertEquals(0, dto.evidence().failingReviews());
        assertEquals(0, dto.evidence().openReviews());
    }

    @Test
    void aFailingReviewOnAStillLiveTaskStillBlocksTheProject() {
        // Same setup, but the task is still non-terminal (claimed) - this failing review IS live evidence
        // of a real, actionable problem, and must still block exactly as before.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID liveTaskId = UUID.randomUUID();
        TaskEntity liveTask = new TaskEntity();
        liveTask.setId(liveTaskId);
        liveTask.setStatus(TaskStatus.claimed);
        liveTask.setDescription("Still being worked on");

        JulesSessionEntity liveSession = new JulesSessionEntity();
        liveSession.setId(UUID.randomUUID());
        liveSession.setTaskId(liveTaskId);
        liveSession.setStatus("stuck");

        PrReviewEntity liveReview = new PrReviewEntity();
        liveReview.setJulesSessionId(liveSession.getId());
        liveReview.setCiStatus("conflict");
        liveReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(liveTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(liveTaskId))).thenReturn(List.of(liveSession));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(liveReview));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertEquals("BLOCKED_BY_REVIEW", dto.currentState());
        assertEquals(1, dto.evidence().failingReviews());
    }

    @Test
    void aSupersededSessionsDeadReviewNoLongerBlocksAfterBranchGcRequeuedTheTask() {
        // Regression test for the 2026-08-01 incident: test-fortieth/PR#119, task 72ec0f54. Branch GC
        // cancelled the stale session (status="cancelled") and re-queued the TASK for a fresh attempt - the
        // task itself never went terminal (queued, not done/failed/spike_completed), so the prior fix above
        // didn't exclude it. The old session's real "closed_unmerged" review still counted as live evidence
        // and kept BLOCKED_BY_REVIEW stuck even though the task had already moved on to retry.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID requeuedTaskId = UUID.randomUUID();
        TaskEntity requeuedTask = new TaskEntity();
        requeuedTask.setId(requeuedTaskId);
        requeuedTask.setStatus(TaskStatus.queued);
        requeuedTask.setDescription("API Slice 9a624cbf");

        JulesSessionEntity supersededSession = new JulesSessionEntity();
        supersededSession.setId(UUID.randomUUID());
        supersededSession.setTaskId(requeuedTaskId);
        supersededSession.setStatus("cancelled");

        PrReviewEntity deadReview = new PrReviewEntity();
        deadReview.setJulesSessionId(supersededSession.getId());
        deadReview.setCiStatus("closed_unmerged");
        deadReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(requeuedTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(requeuedTaskId))).thenReturn(List.of(supersededSession));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(deadReview));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertNotEquals("BLOCKED_BY_REVIEW", dto.currentState());
        assertEquals(0, dto.evidence().failingReviews());
    }

    /**
     * Plan §4.29, measured live 2026-08-29. PlannedWorkRecoveryService deliberately reuses the original
     * task identity when it revives a failed task ("reusing the original planned task identity"), so the
     * previous attempt's session and its closed_unmerged review stay attached to a task that is
     * non-terminal again. That session is closed - status closed_terminal_task - but the liveness filter
     * asked "is it literally cancelled", which it is not, so the dead attempt's review put the project
     * back into BLOCKED_BY_REVIEW one minute after the new attempt was already running, and wishlist
     * compilation was denied again.
     */
    @Test
    void aRevivedTasksPreviousAttemptDoesNotBlockItsNewOne() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID revivedTaskId = UUID.randomUUID();
        TaskEntity revivedTask = new TaskEntity();
        revivedTask.setId(revivedTaskId);
        revivedTask.setStatus(TaskStatus.claimed);
        revivedTask.setDescription("Backend API e126653e");

        JulesSessionEntity previousAttempt = new JulesSessionEntity();
        previousAttempt.setId(UUID.randomUUID());
        previousAttempt.setTaskId(revivedTaskId);
        previousAttempt.setStatus("closed_terminal_task");

        JulesSessionEntity currentAttempt = new JulesSessionEntity();
        currentAttempt.setId(UUID.randomUUID());
        currentAttempt.setTaskId(revivedTaskId);
        currentAttempt.setStatus("running");

        PrReviewEntity previousAttemptsReview = new PrReviewEntity();
        previousAttemptsReview.setJulesSessionId(previousAttempt.getId());
        previousAttemptsReview.setCiStatus("closed_unmerged");
        previousAttemptsReview.setMerged(false);

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(revivedTask));
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(List.of(revivedTaskId))).thenReturn(List.of(previousAttempt, currentAttempt));
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(previousAttemptsReview));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertEquals(0, dto.evidence().failingReviews());
        assertNotEquals("BLOCKED_BY_REVIEW", dto.currentState());
    }

    private FlowSpineService spineOver(List<TaskEntity> tasks, ProjectEntity project) {
        var projects = mock(ProjectRepository.class);
        var taskRepo = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        when(projects.findById(project.getId())).thenReturn(java.util.Optional.of(project));
        when(taskRepo.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(tasks);
        when(wishlists.findByProjectId(project.getId())).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        // Decomposition is complete and not everything is delivered, so the earlier DECOMPOSING and
        // DELIVERED branches fall through and the frontier rule is the one under test.
        when(readiness.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 0, 1, 0, 0.0, true, 0.0));
        when(systemStatus.getStatus(project.getId())).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));
        return new FlowSpineService(projects, taskRepo, wishlists, sessions, reviews, events, readiness,
                systemStatus, mock(com.eneik.production.services.MLPredictionServiceClient.class),
                mock(com.eneik.production.services.lever.LeverPromotionService.class));
    }

    private TaskEntity failedPlannedTask(int resumeCount) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setSourceWishlistId(UUID.randomUUID());
        task.setFeatureId(UUID.randomUUID());
        // The resolver's domain includes WHY the task was retired, so a fixture meant to be inside that
        // domain has to say it (model rule 8.6 - the gate quantifies over what the resolver can act on).
        task.setJulesDispatchStatus(
                "Blocked task retired; auto-recovery follow-up disabled during task-expansion incident");
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        payload.put("ems_bounded_plan_resume_count", resumeCount);
        task.setPayload(payload);
        return task;
    }

    /**
     * Plan §4.39. BLOCKED_BY_FAILED_FRONTIER denies ORCHESTRATE, DISPATCH_QUEUED_TASKS and
     * DISPATCH_REVIEW_TASKS - the whole path from brief to execution. Its resolver is
     * PlannedWorkRecoveryService, which allows one automatic resume per task and refuses thereafter, so a
     * task past its budget can never be removed from a gate that still counts it. Measured live
     * 2026-08-30: 35 failed tasks, 17 resumable in principle, 13 of those already past their only resume.
     */
    @Test
    void aFailedTaskPastItsOnlyResumeNoLongerHoldsTheFrontierClosed() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);

        FlowSpineDto dto = spineOver(List.of(failedPlannedTask(1)), project).build(project.getId());

        assertNotEquals("BLOCKED_BY_FAILED_FRONTIER", dto.currentState());
    }

    /**
     * The other half, and it is not optional: a failure the resolver can still act on must go on holding
     * the state, or the failure would stop meaning anything at all.
     */
    @Test
    void aFailedTaskWithItsResumeStillUnspentHoldsTheFrontierClosed() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);

        FlowSpineDto dto = spineOver(List.of(failedPlannedTask(0)), project).build(project.getId());

        assertEquals("BLOCKED_BY_FAILED_FRONTIER", dto.currentState());
    }

    /**
     * Model rule 8.6. The resolver's domain is not just structural eligibility and an unspent budget: it
     * also depends on why the task was retired. A task that failed for a reason this resolver cannot act
     * on can never be removed by it, so counting such a task holds the project in a state with no exit.
     * Measured live 2026-08-30: four such tasks, and the project denied ORCHESTRATE and both dispatch
     * actions with nothing able to clear them.
     */
    @Test
    void aFailedTaskTheResolverWillNeverTouchDoesNotHoldTheFrontierClosed() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        TaskEntity task = failedPlannedTask(0);
        task.setJulesDispatchStatus("Poka-yoke: out-of-cycle generated work is quarantined");

        FlowSpineDto dto = spineOver(List.of(task), project).build(project.getId());

        assertNotEquals("BLOCKED_BY_FAILED_FRONTIER", dto.currentState());
    }

    @Test
    void duplicateContentAmongOnlyTerminalTasksDoesNotBlockLiveState() {
        // Regression test for the 2026-08-04 incident: test-forty-first stuck for hours in
        // BLOCKED_BY_DUPLICATE_CONTENT with no recovery path, purely because 4 pairs of long-since-`done`
        // duplicate tasks sat in the last-30-tasks window. This mirrors
        // ContinuousOrchestrationServiceTest#duplicateContentAmongOnlyTerminalTasksDoesNotBlock but exercises
        // FlowSpineService.duplicateContent - the actual authority OperationalPolicyService gates on, which
        // has its own separate (and, until this fix, separately-unpatched) duplicate-detection logic.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        List<TaskEntity> duplicates = List.of(
                taskWithSliceTitle(TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"));

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(duplicates);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertNotEquals("BLOCKED_BY_DUPLICATE_CONTENT", dto.currentState());
    }

    @Test
    void duplicateContentAmongActiveTasksStillBlocksLiveState() {
        // Regression guard: the fix must only exempt already-resolved duplicates, not disable the detector
        // entirely - a real, currently-active generation fallback must still be caught.
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var wishlists = mock(WishlistRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var reviews = mock(PrReviewRepository.class);
        var events = mock(FlowSpineEventRepository.class);
        var readiness = mock(ClientDeliverableReadinessService.class);
        var systemStatus = mock(SystemStatusService.class);
        var mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverPromotionService = mock(com.eneik.production.services.lever.LeverPromotionService.class);
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus,
                mlPredictionServiceClient, leverPromotionService);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        List<TaskEntity> duplicates = List.of(
                taskWithSliceTitle(TaskStatus.queued, "Coverage gap falsification"),
                taskWithSliceTitle(TaskStatus.queued, "Coverage gap falsification"),
                taskWithSliceTitle(TaskStatus.review, "Coverage gap falsification"));

        when(projects.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(duplicates);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of());
        when(sessions.findByTaskIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(reviews.findByJulesSessionIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertEquals("BLOCKED_BY_DUPLICATE_CONTENT", dto.currentState());
    }

    private TaskEntity taskWithSliceTitle(TaskStatus status, String sliceTitle) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setStatus(status);
        task.setDescription(sliceTitle);
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        payload.put("slice_title", sliceTitle);
        task.setPayload(payload);
        return task;
    }

    private FlowSpineService.StateInputs input(ProjectStatus projectStatus,
                                               long queuedTasks,
                                               long activeTasks,
                                               long reviewTasks,
                                               long doneTasks,
                                               long failedTasks,
                                               long blockedTasks,
                                               long pendingWishlist,
                                               long compilingWishlist,
                                               long openSessions,
                                               int mergedReviews,
                                               int openReviews,
                                               int failingReviews,
                                               int qualityGatePassed,
                                               int qualityGateFailed,
                                               int totalFeatures,
                                               int completeFeatures,
                                               int totalDeliverables,
                                               int mergedDeliverables,
                                               boolean decompositionComplete,
                                               String systemStatus,
                                               boolean duplicateContentDetected) {
        return new FlowSpineService.StateInputs(projectStatus, queuedTasks, activeTasks, reviewTasks, doneTasks,
                failedTasks, blockedTasks, pendingWishlist, compilingWishlist, openSessions, mergedReviews,
                openReviews, 0L, failingReviews, qualityGatePassed, qualityGateFailed, totalFeatures, completeFeatures,
                totalDeliverables, mergedDeliverables, decompositionComplete, systemStatus, duplicateContentDetected);
    }

    // 2026-08-08 (ML-update patch, Phase 2): D3_EMBEDDING_DUPLICATE_DETECTION shadow check - decoupled from
    // the hot duplicateContent() path, runs on its own schedule against active projects only.

    private FlowSpineService serviceWithMocks(ProjectRepository projects, TaskRepository tasks,
                                               com.eneik.production.services.MLPredictionServiceClient mlClient,
                                               com.eneik.production.services.lever.LeverPromotionService leverService) {
        return new FlowSpineService(projects, tasks, mock(WishlistRepository.class), mock(JulesSessionRepository.class),
                mock(PrReviewRepository.class), mock(FlowSpineEventRepository.class),
                mock(ClientDeliverableReadinessService.class), mock(SystemStatusService.class), mlClient, leverService);
    }

    @Test
    void candidateFindsASemanticDuplicateExactMatchMissesAndRecordsAStrongObservation() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var mlClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverService = mock(com.eneik.production.services.lever.LeverPromotionService.class);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        when(projects.findAll()).thenReturn(List.of(project));

        TaskEntity taskA = new TaskEntity();
        taskA.setId(UUID.randomUUID());
        taskA.setStatus(TaskStatus.queued);
        taskA.setDescription("Frontend UI implementation for the billing module");
        TaskEntity taskB = new TaskEntity();
        taskB.setId(UUID.randomUUID());
        taskB.setStatus(TaskStatus.queued);
        taskB.setDescription("Svelte UI slice covering billing screens");
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(taskA, taskB));

        // Identical vectors -> cosine similarity 1.0, above both the detection and confirmation thresholds.
        float[] vector = new float[]{1f, 0f, 0f};
        when(mlClient.embed(taskA.getDescription())).thenReturn(vector);
        when(mlClient.embed(taskB.getDescription())).thenReturn(vector);

        serviceWithMocks(projects, tasks, mlClient, leverService).shadowCheckEmbeddingDuplicatesAcrossActiveProjects();

        org.mockito.Mockito.verify(leverService).recordObservation(
                org.mockito.ArgumentMatchers.eq(FlowSpineService.D3_LEVER_KEY),
                org.mockito.ArgumentMatchers.eq(projectId.toString()),
                org.mockito.ArgumentMatchers.eq("not_duplicate"),
                org.mockito.ArgumentMatchers.eq("duplicate"),
                org.mockito.ArgumentMatchers.eq(com.eneik.production.services.lever.LeverAgreement.TRUE),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noObservationRecordedWithFewerThanTwoUniqueKeyedCandidates() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var mlClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverService = mock(com.eneik.production.services.lever.LeverPromotionService.class);

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        when(projects.findAll()).thenReturn(List.of(project));

        TaskEntity onlyTask = new TaskEntity();
        onlyTask.setId(UUID.randomUUID());
        onlyTask.setStatus(TaskStatus.queued);
        onlyTask.setDescription("Only one candidate task");
        when(tasks.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(onlyTask));

        serviceWithMocks(projects, tasks, mlClient, leverService).shadowCheckEmbeddingDuplicatesAcrossActiveProjects();

        org.mockito.Mockito.verifyNoInteractions(mlClient);
        org.mockito.Mockito.verify(leverService, org.mockito.Mockito.never())
                .recordObservation(org.mockito.ArgumentMatchers.eq(FlowSpineService.D3_LEVER_KEY),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonActiveProjectsAreSkippedEntirely() {
        var projects = mock(ProjectRepository.class);
        var tasks = mock(TaskRepository.class);
        var mlClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        var leverService = mock(com.eneik.production.services.lever.LeverPromotionService.class);

        ProjectEntity frozenProject = new ProjectEntity();
        frozenProject.setId(UUID.randomUUID());
        frozenProject.setStatus(ProjectStatus.frozen);
        when(projects.findAll()).thenReturn(List.of(frozenProject));

        serviceWithMocks(projects, tasks, mlClient, leverService).shadowCheckEmbeddingDuplicatesAcrossActiveProjects();

        org.mockito.Mockito.verifyNoInteractions(tasks, mlClient, leverService);
    }
}
