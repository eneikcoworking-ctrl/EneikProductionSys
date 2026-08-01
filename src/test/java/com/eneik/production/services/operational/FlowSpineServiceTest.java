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
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus);

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
        when(reviews.findAll()).thenReturn(List.of(deadReview));
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
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus);

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
        when(reviews.findAll()).thenReturn(List.of(liveReview));
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
        FlowSpineService service = new FlowSpineService(
                projects, tasks, wishlists, sessions, reviews, events, readiness, systemStatus);

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
        when(reviews.findAll()).thenReturn(List.of(deadReview));
        when(readiness.computeForProject(projectId)).thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(systemStatus.getStatus(projectId)).thenReturn(
                Map.of("systemHealth", Map.of("data", Map.of("status", "ok"))));

        FlowSpineDto dto = service.build(projectId);

        assertNotEquals("BLOCKED_BY_REVIEW", dto.currentState());
        assertEquals(0, dto.evidence().failingReviews());
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
                openReviews, failingReviews, qualityGatePassed, qualityGateFailed, totalFeatures, completeFeatures,
                totalDeliverables, mergedDeliverables, decompositionComplete, systemStatus, duplicateContentDetected);
    }
}
