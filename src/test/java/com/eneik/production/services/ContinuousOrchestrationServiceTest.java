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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void duplicateGenerationVelocityDetectsExcessiveCompilerSessionsWithoutBlockingSystem() {
        ProjectEntity project = project(UUID.randomUUID(), "test-forty-second", ProjectStatus.active);
        TaskRepository taskRepository = mock(TaskRepository.class);
        JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        var defectJournalService = mock(com.eneik.production.kaizen.service.DefectJournalService.class);
        var defectJournalRepository = mock(com.eneik.production.kaizen.repository.DefectJournalRepository.class);

        String sharedContentKey = "compile:" + project.getId() + ":sha256abc";
        // V137: a single compiler task row exists in the database
        TaskEntity compilerTask = task(project, TaskStatus.done);
        compilerTask.setContentKey(sharedContentKey);
        compilerTask.setDescription("Compile into task graph: .eneik/records/task-plan-" + UUID.randomUUID() + ".json");

        // 4 Jules sessions dispatched in the 2-hour window (exceeding COMPILE_ATTEMPT_BUDGET = 3)
        Instant now = Instant.now();
        JulesSessionEntity s1 = new JulesSessionEntity(); s1.setTaskId(compilerTask.getId()); s1.setCreatedAt(now.minusSeconds(3000));
        JulesSessionEntity s2 = new JulesSessionEntity(); s2.setTaskId(compilerTask.getId()); s2.setCreatedAt(now.minusSeconds(2000));
        JulesSessionEntity s3 = new JulesSessionEntity(); s3.setTaskId(compilerTask.getId()); s3.setCreatedAt(now.minusSeconds(1000));
        JulesSessionEntity s4 = new JulesSessionEntity(); s4.setTaskId(compilerTask.getId()); s4.setCreatedAt(now.minusSeconds(200));
        List<JulesSessionEntity> sessions = List.of(s1, s2, s3, s4);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(compilerTask));
        when(taskRepository.findByProjectIdAndContentKeyStartingWith(project.getId(), "compile:")).thenReturn(List.of(compilerTask));
        when(taskRepository.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(project.getId()), org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(List.of());
        when(julesSessionRepository.findByTaskIdIn(List.of(compilerTask.getId()))).thenReturn(sessions);
        when(defectJournalRepository.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(project.getId()), org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(List.of());

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class), mock(ProjectFlowService.class), mock(AccountRepository.class),
                julesSessionRepository, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                wishlistRepository, mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class),
                taskRepository, new SystemProgressTracker(), mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class), mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class), mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class));

        service.setDefectJournalService(defectJournalService);
        service.setDefectJournalRepository(defectJournalRepository);

        // 1. In-flight stuck duplicate check does NOT trip because compiler task is terminal
        boolean duplicated = ReflectionTestUtils.invokeMethod(service, "checkForDuplicateTaskContent", project);
        assertEquals(false, duplicated);

        // 2. Velocity check detects 4 sessions > budget 3, and records defect with rootCausePatternId = null
        service.checkDuplicateGenerationVelocity(project);

        verify(defectJournalService, times(1)).recordDefect(
                org.mockito.ArgumentMatchers.eq(project.getId()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), // rootCausePatternId must be null
                org.mockito.ArgumentMatchers.eq("HIGH"),
                org.mockito.ArgumentMatchers.eq("WASTE_REDUCTION"),
                org.mockito.ArgumentMatchers.eq("ContinuousOrchestrationService"),
                org.mockito.ArgumentMatchers.eq("DUPLICATE_GENERATION_VELOCITY"),
                org.mockito.ArgumentMatchers.contains(sharedContentKey),
                org.mockito.ArgumentMatchers.eq(4.0)
        );

        // 3. Second call within the same window when defect is already present does NOT duplicate record
        var recordedDefect = new com.eneik.production.kaizen.model.DefectJournalEntity(
                project.getId(), null, null, "HIGH", "WASTE_REDUCTION", "ContinuousOrchestrationService",
                "DUPLICATE_GENERATION_VELOCITY", "Threshold exceeded for contentKey: '" + sharedContentKey + "'", 4.0);
        when(defectJournalRepository.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(project.getId()), org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(List.of(recordedDefect));

        service.checkDuplicateGenerationVelocity(project);
        // still 1 invocation total
        verify(defectJournalService, times(1)).recordDefect(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void duplicateGenerationVelocityAllowsLawfulRecoveryDepthAndThrowsOnNullBeans() {
        ProjectEntity project = project(UUID.randomUUID(), "test-forty-third", ProjectStatus.active);
        TaskRepository taskRepository = mock(TaskRepository.class);
        var defectJournalService = mock(com.eneik.production.kaizen.service.DefectJournalService.class);
        var defectJournalRepository = mock(com.eneik.production.kaizen.repository.DefectJournalRepository.class);

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

        // Fail-closed setters
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.setDefectJournalService(null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.setDefectJournalRepository(null));

        service.setDefectJournalService(defectJournalService);
        service.setDefectJournalRepository(defectJournalRepository);

        // 1 base task + 2 recovery tasks = 3 tasks total with identical slice_title
        String sliceTitle = "Auth slice work";
        TaskEntity t1 = taskWithSliceTitle(project, TaskStatus.failed, sliceTitle);
        TaskEntity t2 = taskWithSliceTitle(project, TaskStatus.failed, sliceTitle);
        TaskEntity t3 = taskWithSliceTitle(project, TaskStatus.done, sliceTitle);
        List<TaskEntity> lawfulBatch = List.of(t1, t2, t3);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(lawfulBatch);
        when(taskRepository.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(project.getId()), org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(lawfulBatch);

        // Lawful budget (3 attempts) does NOT trigger defect
        service.checkDuplicateGenerationVelocity(project);
        verify(defectJournalService, never()).recordDefect(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void duplicateGenerationVelocityIgnoresLifetimeAttemptsAndOlderSessions() {
        ProjectEntity project = project(UUID.randomUUID(), "test-forty-fourth", ProjectStatus.active);
        TaskRepository taskRepository = mock(TaskRepository.class);
        var julesSessionRepository = mock(JulesSessionRepository.class);
        var defectJournalService = mock(com.eneik.production.kaizen.service.DefectJournalService.class);
        var defectJournalRepository = mock(com.eneik.production.kaizen.repository.DefectJournalRepository.class);
        var wishlistRepository = mock(WishlistRepository.class);

        String sharedContentKey = "compile:" + project.getId() + ":sha256xyz";
        TaskEntity compilerTask = task(project, TaskStatus.done);
        compilerTask.setContentKey(sharedContentKey);

        // 4 sessions in lifetime, but 3 occurred outside the 2-hour window (only 1 inside window)
        Instant now = Instant.now();
        JulesSessionEntity s1 = new JulesSessionEntity(); s1.setTaskId(compilerTask.getId()); s1.setCreatedAt(now.minusSeconds(15000));
        JulesSessionEntity s2 = new JulesSessionEntity(); s2.setTaskId(compilerTask.getId()); s2.setCreatedAt(now.minusSeconds(12000));
        JulesSessionEntity s3 = new JulesSessionEntity(); s3.setTaskId(compilerTask.getId()); s3.setCreatedAt(now.minusSeconds(9000));
        JulesSessionEntity s4 = new JulesSessionEntity(); s4.setTaskId(compilerTask.getId()); s4.setCreatedAt(now.minusSeconds(600)); // only 1 in window
        List<JulesSessionEntity> sessions = List.of(s1, s2, s3, s4);

        // Wishlist with 4 lifetime attempts on a raised ceiling of 5
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setCompileAttempts(4);
        wishlist.setCompileAttemptCeiling(5); // ceiling = 5

        when(taskRepository.findByProjectIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(project.getId()), org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndContentKeyStartingWith(project.getId(), "compile:"))
                .thenReturn(List.of(compilerTask));
        when(julesSessionRepository.findByTaskIdIn(List.of(compilerTask.getId()))).thenReturn(sessions);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(wishlist));

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class), mock(ProjectFlowService.class), mock(AccountRepository.class),
                julesSessionRepository, mock(com.eneik.production.services.jules.JulesDispatchService.class),
                wishlistRepository, mock(TechnicalLeadCompiler.class), mock(MLPredictionServiceClient.class),
                taskRepository, new SystemProgressTracker(), mock(SystemSettingsService.class),
                mock(PlannedWorkRecoveryService.class), mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class), mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class));

        service.setDefectJournalService(defectJournalService);
        service.setDefectJournalRepository(defectJournalRepository);

        service.checkDuplicateGenerationVelocity(project);

        // No defect recorded: 1 session in window <= budget 3, and lifetime attempts are not falsely reported as 2-hour rate
        verify(defectJournalService, never()).recordDefect(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void continuousOrchestrateDelegatesVerdictObservationForActiveProjects() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectEntity activeProject = project(UUID.randomUUID(), "active-test", ProjectStatus.active);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        var verdictObserver = mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
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
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                verdictObserver
        );

        service.continuousOrchestrate();

        verify(verdictObserver, times(1)).observe(activeProject.getId());
    }

    // ANTI_MIRROR_TELEMETRY (D013, LYUDVIG_VITGENSHTEYN_14) / Prescription 11:
    // Factory self-reports must rely on genuine external deliverables (dispatch, merge),
    // never on internal stories, polls, audits or boot timestamps.

    @Test
    void startupWithoutDeliverablesDoesNotReportOk() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectEntity activeProject = project(UUID.randomUUID(), "active-test", ProjectStatus.active);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskEntity queuedTask = task(activeProject, TaskStatus.queued);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId()))
                .thenReturn(List.of(queuedTask));

        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountEntity idleAccount = new AccountEntity();
        idleAccount.setEnabled(true);
        idleAccount.setStatus(AccountStatus.idle);
        when(accountRepository.findAll()).thenReturn(List.of(idleAccount));

        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        SystemProgressTracker freshTracker = new SystemProgressTracker(); // startedAt=now, lastProgressAt=null

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                accountRepository,
                mock(JulesSessionRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                taskRepository,
                freshTracker,
                settingsService,
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class)
        );

        ReflectionTestUtils.invokeMethod(service, "checkForSystemStall");

        verify(settingsService, times(1)).save("system_stall_status", "undetermined");
        verify(settingsService, never()).save("system_stall_status", "ok");
    }

    @Test
    void windowElapsedWithoutDeliverableReportsStalledEvenIfAuditsRan() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectEntity activeProject = project(UUID.randomUUID(), "active-test", ProjectStatus.active);
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskEntity queuedTask = task(activeProject, TaskStatus.queued);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(activeProject.getId()))
                .thenReturn(List.of(queuedTask));

        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountEntity idleAccount = new AccountEntity();
        idleAccount.setEnabled(true);
        idleAccount.setStatus(AccountStatus.idle);
        when(accountRepository.findAll()).thenReturn(List.of(idleAccount));

        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        // Tracker started 60 minutes ago, no external progress recorded (internal audits/compilations don't call recordProgress)
        SystemProgressTracker staleTracker = new SystemProgressTracker(java.time.Instant.now().minus(java.time.Duration.ofMinutes(60)));

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                projectRepository,
                mock(ProjectFlowService.class),
                accountRepository,
                mock(JulesSessionRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(WishlistRepository.class),
                mock(TechnicalLeadCompiler.class),
                mock(MLPredictionServiceClient.class),
                taskRepository,
                staleTracker,
                settingsService,
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class)
        );

        ReflectionTestUtils.invokeMethod(service, "checkForSystemStall");

        verify(settingsService, times(1)).save("system_stall_status", "stalled");
        verify(settingsService, never()).save("system_stall_status", "ok");
    }

    @Test
    void genuineDeliverableReportsOkWithinWindow() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        SystemProgressTracker tracker = new SystemProgressTracker();
        tracker.recordProgress(java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)));

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
                tracker,
                settingsService,
                mock(PlannedWorkRecoveryService.class),
                mock(BranchGarbageCollectorService.class),
                mock(GitHubPullRequestService.class),
                mock(OperationalPolicyService.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                mock(com.eneik.production.services.runtime.ProductLaunchabilityService.class),
                mock(com.eneik.production.services.runtime.ClientRuntimeObservabilityService.class),
                mock(com.eneik.production.services.judgment.DeliveredWorkJudgmentService.class),
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class)
        );

        ReflectionTestUtils.invokeMethod(service, "checkForSystemStall");

        verify(settingsService, times(1)).save("system_stall_status", "ok");
    }

    @Test
    void resetDailyLimitedAccounts_resetsAccountsAndRequeuesUntestedTasks() {
        com.eneik.production.services.accounts.AccountHealthService accountHealthService =
                mock(com.eneik.production.services.accounts.AccountHealthService.class);
        ProjectFlowService projectFlowService = mock(ProjectFlowService.class);
        AccountRepository accountRepository = mock(AccountRepository.class);

        when(accountHealthService.resetDailyLimitedAccounts()).thenReturn(2);

        ContinuousOrchestrationService service = new ContinuousOrchestrationService(
                mock(ProjectRepository.class),
                projectFlowService,
                accountRepository,
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
                mock(com.eneik.production.services.toc.TocSubordinationLever.class),
                mock(com.eneik.production.services.verdict.AutonomousVerdictObservationService.class)
        );

        service.resetDailyLimitedAccounts();

        verify(accountHealthService).resetDailyLimitedAccounts();
        verify(accountRepository).resetDailySessionCounts();
        verify(projectFlowService).requeueUntestedTasksOnRestoredCapacity();
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
