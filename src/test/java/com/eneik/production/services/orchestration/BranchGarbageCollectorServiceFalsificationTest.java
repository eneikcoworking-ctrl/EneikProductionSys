package com.eneik.production.services.orchestration;

import com.eneik.production.models.persistence.FeatureThreadEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.SessionLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for BranchGarbageCollectorService.
 * Validates epistemic and architectural invariants:
 * - D004: Varzi Part-Whole Ownership (session token lineage, project task isolation, single active branch).
 * - D006: Raz Prohibition As Code (clean retirement: close PR, delete branch, supersede conflict, re-queue).
 * - D013: Wittgenstein Anti-Mirror (telemetry overrides title testimony, protection of live closeout and persistent workers).
 */
class BranchGarbageCollectorServiceFalsificationTest {

    private GitHubPullRequestService gitHubPullRequestService;
    private TaskRepository taskRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesSessionRepository julesSessionRepository;
    private SessionLifecycleService sessionLifecycleService;
    private ProjectFlowService projectFlowService;
    private FeatureThreadRepository featureThreadRepository;
    private BranchGarbageCollectorService service;

    @BeforeEach
    void setUp() {
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        taskRepository = mock(TaskRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        sessionLifecycleService = mock(SessionLifecycleService.class);
        projectFlowService = mock(ProjectFlowService.class);
        featureThreadRepository = mock(FeatureThreadRepository.class);

        when(taskRepository.writeStatusUnlessTerminal(any(), any())).thenReturn(1);

        service = new BranchGarbageCollectorService(
                gitHubPullRequestService,
                taskRepository,
                taskConflictRepository,
                julesSessionRepository,
                sessionLifecycleService,
                projectFlowService,
                featureThreadRepository
        );
    }

    @Test
    @DisplayName("D004 Varzi Mereology: Sweep scopes session search strictly to project tasks via findByTaskIdIn")
    void falsifyVarziPartWholeOwnership_branchAndSessionBelongStrictlyToProjectTasks() {
        ProjectEntity project = createProject();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = createTask(project, taskId, TaskStatus.claimed);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));

        var openPr = createPr(10, "Feature A", "feat/branch-tok123", Instant.now().minus(20, ChronoUnit.MINUTES));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/tok123");
        session.setLastProgressAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(julesSessionRepository.findByTaskIdIn(List.of(taskId))).thenReturn(List.of(session));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertThat(cleaned).isZero();
        verify(julesSessionRepository).findByTaskIdIn(List.of(taskId));
        verify(julesSessionRepository, never()).findAll();
    }

    @Test
    @DisplayName("D004 Varzi Mereology: Retires ONLY the session matching the retired branch and spares concurrent sessions")
    void falsifyVarziPartWholeOwnership_retireOnlyMatchingSessionAndNeverUnrelatedSessionsOnSameTask() {
        ProjectEntity project = createProject();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = createTask(project, taskId, TaskStatus.claimed);

        UUID staleSessionId = UUID.randomUUID();
        JulesSessionEntity staleSession = new JulesSessionEntity();
        staleSession.setId(staleSessionId);
        staleSession.setTaskId(taskId);
        staleSession.setExternalSessionId("sessions/stale-session-token");
        staleSession.setStatus("pr_opened");

        UUID liveSessionId = UUID.randomUUID();
        JulesSessionEntity liveSession = new JulesSessionEntity();
        liveSession.setId(liveSessionId);
        liveSession.setTaskId(taskId);
        liveSession.setExternalSessionId("sessions/live-session-token");
        liveSession.setStatus("pr_opened");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(staleSession, liveSession));
        when(gitHubPullRequestService.fetchPullRequestByNumber(project, 42))
                .thenReturn(Optional.of(createPr(42, "Stale PR", "jules-stale-session-token-branch", Instant.now())));

        boolean retired = service.retireAbandonedBranchAndPR(
                project, task, "jules-stale-session-token-branch", 42, "Stale branch test");

        assertThat(retired).isTrue();
        verify(sessionLifecycleService).retireSessionOnly(eq(staleSessionId), anyString());
        verify(sessionLifecycleService, never()).retireSessionOnly(eq(liveSessionId), anyString());
    }

    @Test
    @DisplayName("D006 Raz Prohibition: Deontic cleanup executes full chain: close PR, delete branch, supersede conflict, re-queue")
    void falsifyRazProhibitionAsCode_retireAbandonedBranchClosesPrDeletesBranchSupersedesConflictsAndRequeuesTask() {
        ProjectEntity project = createProject();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = createTask(project, taskId, TaskStatus.claimed);

        // Pending conflict must be marked superseded with resolutionAttempts=99
        TaskConflictEntity pendingConflict = new TaskConflictEntity();
        pendingConflict.setTask(task);
        pendingConflict.setResolutionStatus("pending");
        when(taskConflictRepository.findFirstByTaskIdAndResolutionStatus(taskId, "pending"))
                .thenReturn(Optional.of(pendingConflict));

        when(gitHubPullRequestService.fetchPullRequestByNumber(project, 99))
                .thenReturn(Optional.of(createPr(99, "Abandoned PR", "feat/abandoned", Instant.now())));
        when(gitHubPullRequestService.deleteBranch(project, "feat/abandoned")).thenReturn(true);

        boolean result = service.retireAbandonedBranchAndPR(project, task, "feat/abandoned", 99, "Abandoned test");

        assertThat(result).isTrue();
        verify(gitHubPullRequestService).closeSinglePullRequest(eq(project), any(), contains("Abandoned test"));
        verify(gitHubPullRequestService).deleteBranch(project, "feat/abandoned");
        assertThat(pendingConflict.getResolutionStatus()).isEqualTo("superseded");
        assertThat(pendingConflict.getResolutionAttempts()).isEqualTo(99);
        verify(taskConflictRepository).save(pendingConflict);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.queued);
        assertThat(task.getPriority()).isEqualTo(100);
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("D006 Raz Prohibition: Terminal tasks are protected and never re-queued")
    void falsifyRazProhibitionAsCode_terminalTaskIsNotRequeued() {
        ProjectEntity project = createProject();
        UUID taskId = UUID.randomUUID();
        TaskEntity doneTask = createTask(project, taskId, TaskStatus.done);

        boolean retired = service.retireAbandonedBranchAndPR(
                project, doneTask, "feat/done-branch", 100, "Should not re-queue done task");

        assertThat(retired).isFalse();
        verify(taskRepository, never()).save(doneTask);
        verify(taskRepository, never()).writeStatusUnlessTerminal(any(), any());
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Closeout PR without confirmed supersession is protected from deletion")
    void falsifyWittgensteinAntiMirror_closeoutPrWithoutConfirmedSupersessionIsProtectedFromDeletion() {
        ProjectEntity project = createProject();
        UUID featureId = UUID.randomUUID();
        String prUrl = "https://github.com/org/repo/pull/55";
        var closeoutPr = new GitHubPullRequestService.GitHubPullRequest(
                prUrl, 55, "Closeout: integrate feature " + featureId + " into main",
                "closeout-branch", "author", false, "main", false, Instant.now());

        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(closeoutPr));

        // Durable reality: Feature thread confirms this PR is STILL the live unmerged closeout attempt
        FeatureThreadEntity thread = new FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setCloseoutPrUrl(prUrl);
        thread.setMergedToMainAt(null);
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertThat(cleaned).isZero();
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
    }

    @Test
    @DisplayName("D013 Wittgenstein Anti-Mirror: Persistent worker carrier tasks are exempt from stagnation cleanup")
    void falsifyWittgensteinAntiMirror_persistentWorkerCarrierTaskIsNeverGarbageCollected() {
        ProjectEntity project = createProject();
        UUID taskId = UUID.randomUUID();
        TaskEntity carrierTask = createTask(project, taskId, TaskStatus.claimed);
        when(projectFlowService.isPersistentWorkerCarrierTask(carrierTask)).thenReturn(true);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(carrierTask));

        var openPr = createPr(77, "Philosophical audit worker", "feat/audit-worker-xyz", Instant.now().minus(3, ChronoUnit.HOURS));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/audit-worker-xyz");
        session.setLastProgressAt(Instant.now().minus(2, ChronoUnit.HOURS));
        when(julesSessionRepository.findByTaskIdIn(List.of(taskId))).thenReturn(List.of(session));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertThat(cleaned).isZero();
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
    }

    private ProjectEntity createProject() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName("test-fiftieth");
        return p;
    }

    private TaskEntity createTask(ProjectEntity project, UUID taskId, TaskStatus status) {
        TaskEntity t = new TaskEntity();
        t.setId(taskId);
        t.setProject(project);
        t.setTitle("Task " + taskId);
        t.setStatus(status);
        return t;
    }

    private GitHubPullRequestService.GitHubPullRequest createPr(int number, String title, String headRef, Instant createdAt) {
        return new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/" + number,
                number,
                title,
                headRef,
                "eneikdru",
                false,
                "main",
                false,
                createdAt
        );
    }
}
