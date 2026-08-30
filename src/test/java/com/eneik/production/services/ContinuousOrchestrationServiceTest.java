package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.orchestration.BranchGarbageCollectorService;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContinuousOrchestrationServiceTest {

    @Test
    void systemWorkSnapshotCountsOnlyActiveProjects() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        ProjectEntity activeProject = project(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "active", ProjectStatus.active);
        ProjectEntity frozenProject = project(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "frozen", ProjectStatus.frozen);
        TaskEntity queued = task(activeProject, TaskStatus.queued);
        TaskEntity review = task(activeProject, TaskStatus.review);
        TaskEntity frozenQueued = task(frozenProject, TaskStatus.queued);
        WishlistEntity pending = wishlist(activeProject.getId(), WishlistStatus.pending);
        JulesSessionEntity reviewSession = new JulesSessionEntity();
        reviewSession.setTaskId(review.getId());
        reviewSession.setPrUrl("https://github.com/org/repo/pull/1");
        // Finished, so the review task is work this factory can still move (2026-08-30, plan §4.43).
        reviewSession.setStatus("closed_terminal_task");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(activeProject));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId())).thenReturn(List.of(queued, review));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(frozenProject.getId())).thenReturn(List.of(frozenQueued));
        when(wishlistRepository.findByProjectId(activeProject.getId())).thenReturn(List.of(pending));
        when(julesSessionRepository.findByTaskId(review.getId())).thenReturn(List.of(reviewSession));

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                julesSessionRepository,
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                wishlistRepository,
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                taskRepository,
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class)
        );

        ContinuousOrchestrationService.SystemWorkSnapshot snapshot = service.systemWorkSnapshot();

        assertEquals(1, snapshot.queuedTasks());
        assertEquals(1, snapshot.pendingWishlists());
        assertEquals(1, snapshot.activeNonTerminalTasks());
        assertEquals(1, snapshot.reviewTasksWithPr());
        assertTrue(snapshot.hasActionableWork());
    }

    /**
     * Plan §4.43. `stalled` is a globally blocking state: it denies ORCHESTRATE and both dispatch actions.
     * The predicate behind it counted a task already handed to a live Jules session as work the factory was
     * failing to move. Measured overnight 29->30.08: 122 identical SYSTEM STALLED errors, each printing
     * queuedTasks=0, pendingOrCompilingWishlists=0, activeNonTerminalTasks=1 - one session running and
     * nothing else - and 241 ticks spent in that state, which is where the client requirements returned to
     * the queue at 00:29 stopped being compiled. Sessions here run from two minutes to twenty hours.
     */
    @Test
    void aTaskAliveInsideAJulesSessionIsNotWorkTheFactoryIsFailingToMove() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        ProjectEntity activeProject = project(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "active", ProjectStatus.active);
        TaskEntity inFlight = task(activeProject, TaskStatus.claimed);
        JulesSessionEntity liveSession = new JulesSessionEntity();
        liveSession.setTaskId(inFlight.getId());
        liveSession.setStatus("running");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(activeProject));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId())).thenReturn(List.of(inFlight));
        when(wishlistRepository.findByProjectId(activeProject.getId())).thenReturn(List.of());
        when(julesSessionRepository.findByTaskId(inFlight.getId())).thenReturn(List.of(liveSession));

        ContinuousOrchestrationService service = serviceOver(projectRepository, taskRepository,
                wishlistRepository, julesSessionRepository);

        ContinuousOrchestrationService.SystemWorkSnapshot snapshot = service.systemWorkSnapshot();

        assertEquals(0, snapshot.activeNonTerminalTasks());
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.hasActionableWork(),
                "a session is running; the factory is waiting on another system, not stalling");
    }

    /**
     * The other half, and it is not optional: the same task with no live session IS work the factory should
     * be moving, and a stall verdict on it is real. Without this the change would simply switch the
     * detector off.
     */
    @Test
    void theSameTaskWithNoLiveSessionIsStillActionable() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);

        ProjectEntity activeProject = project(UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "active", ProjectStatus.active);
        TaskEntity stranded = task(activeProject, TaskStatus.claimed);
        JulesSessionEntity finished = new JulesSessionEntity();
        finished.setTaskId(stranded.getId());
        finished.setStatus("closed_terminal_task");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(activeProject));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId())).thenReturn(List.of(stranded));
        when(wishlistRepository.findByProjectId(activeProject.getId())).thenReturn(List.of());
        when(julesSessionRepository.findByTaskId(stranded.getId())).thenReturn(List.of(finished));

        ContinuousOrchestrationService.SystemWorkSnapshot snapshot = serviceOver(projectRepository,
                taskRepository, wishlistRepository, julesSessionRepository).systemWorkSnapshot();

        assertEquals(1, snapshot.activeNonTerminalTasks());
        assertTrue(snapshot.hasActionableWork());
    }

    private ContinuousOrchestrationService serviceOver(ProjectRepository projectRepository,
            TaskRepository taskRepository, WishlistRepository wishlistRepository,
            JulesSessionRepository julesSessionRepository) {
        return new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                julesSessionRepository,
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                wishlistRepository,
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                taskRepository,
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class));
    }

    /**
     * 2026-08-01: the recovery cooldown math itself moved entirely to AccountHealthService (see
     * AccountHealthServiceTest for the data-driven median+z*sigma backoff verification) - this class is
     * now only a scheduled trigger, so its own test only verifies delegation, not the math.
     */
    @Test
    void recoverStaleBlockedAccountsDelegatesToAccountHealthService() {
        var accountHealthService = mock(com.eneik.production.services.accounts.AccountHealthService.class);
        when(accountHealthService.recoverEligibleAccounts()).thenReturn(2);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class),
                mock(ProjectFlowService.class),
                mock(AccountRepository.class),
                mock(JulesSessionRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                mock(TaskRepository.class),
                new SystemProgressTracker(),
                mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                accountHealthService,
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class)
        );

        service.recoverStaleBlockedAccounts();

        verify(accountHealthService, times(1)).recoverEligibleAccounts();
    }

    // --- checkForDuplicateTaskContent (2026-08-04, live incident: test-forty-first stuck for hours in
    // BLOCKED_BY_DUPLICATE_CONTENT, a hard-stop state with no recovery path, purely because 4 pairs of
    // long-since-`done` duplicate tasks sat in the last-30-tasks window) --------------------------------

    private TaskEntity taskWithSliceTitle(ProjectEntity project, TaskStatus status, String sliceTitle) {
        TaskEntity task = task(project, status);
        com.fasterxml.jackson.databind.node.ObjectNode payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        payload.put("slice_title", sliceTitle);
        task.setPayload(payload);
        return task;
    }

    @Test
    void duplicateContentAmongOnlyTerminalTasksDoesNotBlock() {
        ProjectEntity project = project(UUID.randomUUID(), "test-forty-first", ProjectStatus.active);
        TaskRepository taskRepository = mock(TaskRepository.class);
        List<TaskEntity> duplicates = List.of(
                taskWithSliceTitle(project, TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(project, TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(project, TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"),
                taskWithSliceTitle(project, TaskStatus.done, "Internal work item 2 (BARCAN-TAG-06) from wishlist"));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(duplicates);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class), mock(ProjectFlowService.class), mock(AccountRepository.class),
                mock(JulesSessionRepository.class), mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class), mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class),
                taskRepository, new SystemProgressTracker(), mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class), mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class), mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class));

        boolean duplicated = ReflectionTestUtils.invokeMethod(service, "checkForDuplicateTaskContent", project);

        assertEquals(false, duplicated);
    }

    @Test
    void duplicateContentAmongActiveTasksStillBlocks() {
        // Regression guard: the fix must only exempt already-resolved duplicates, not disable the
        // detector entirely - a real, currently-active generation fallback must still be caught.
        ProjectEntity project = project(UUID.randomUUID(), "test-forty-first", ProjectStatus.active);
        TaskRepository taskRepository = mock(TaskRepository.class);
        List<TaskEntity> duplicates = List.of(
                taskWithSliceTitle(project, TaskStatus.queued, "Coverage gap falsification"),
                taskWithSliceTitle(project, TaskStatus.queued, "Coverage gap falsification"),
                taskWithSliceTitle(project, TaskStatus.review, "Coverage gap falsification"));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(duplicates);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class), mock(ProjectFlowService.class), mock(AccountRepository.class),
                mock(JulesSessionRepository.class), mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class), mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class),
                taskRepository, new SystemProgressTracker(), mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class), mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class), mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class));

        boolean duplicated = ReflectionTestUtils.invokeMethod(service, "checkForDuplicateTaskContent", project);

        assertEquals(true, duplicated);
    }

    private ProjectEntity project(UUID id, String name, ProjectStatus status) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setName(name);
        project.setStatus(status);
        return project;
    }

    private TaskEntity task(ProjectEntity project, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(status);
        return task;
    }

    private WishlistEntity wishlist(UUID projectId, WishlistStatus status) {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(projectId);
        wishlist.setStatus(status);
        return wishlist;
    }
}
