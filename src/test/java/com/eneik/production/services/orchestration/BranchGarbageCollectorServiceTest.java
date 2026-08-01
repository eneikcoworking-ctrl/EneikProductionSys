package com.eneik.production.services.orchestration;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression suite for two related 2026-07-31 incidents on this sweep. First: a naming-convention check
 * ("jules-..." branches only) skipped the live-session lookup entirely for a descriptively-named branch,
 * closing real committed work purely because of how it was named. Second, after that first fix: the
 * replacement lookup matched a PR to its session by exact `session.prUrl` string equality - a field only
 * written back to our DB in a separate round trip after Jules reports pr_opened, racing this very sweep. A
 * 13-second-old PR with real, mergeable work (PR#3, task ff03b176) was closed by exactly that race, papered
 * over at the time with a fixed 10-minute grace period rather than a real fix. Every case here proves the
 * decision now depends only on the session-token embedded in the branch name (available from dispatch time
 * onward, with no such race) and real elapsed time since actual progress - never on branch naming, PR title,
 * or whether a separate DB field happened to have been written back yet.
 */
class BranchGarbageCollectorServiceTest {

    private GitHubPullRequestService gitHubPullRequestService;
    private TaskRepository taskRepository;
    private TaskConflictRepository taskConflictRepository;
    private JulesSessionRepository julesSessionRepository;
    private BranchGarbageCollectorService service;

    private void setUp() {
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        taskRepository = mock(TaskRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        service = new BranchGarbageCollectorService(gitHubPullRequestService, taskRepository,
                taskConflictRepository, julesSessionRepository,
                mock(com.eneik.production.services.jules.SessionLifecycleService.class));
    }

    private ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName("test-fortieth");
        return p;
    }

    private GitHubPullRequestService.GitHubPullRequest pr(int number, String title, String headRef, Instant createdAt) {
        return new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/" + number, number, title, headRef, "eneikdru",
                false, "main", false, createdAt);
    }

    @Test
    void protectsABrandNewDescriptivelyNamedBranchViaTokenMatchWithNoWaitingAtAll() {
        // Direct regression test for the ff03b176/PR#3 incident shape: a PR only 13 seconds old, whose
        // branch carries a live session's token, must never be touched - immediately, with no grace period
        // needed, because the session (and its externalSessionId) already existed before the PR did.
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var freshPr = pr(3, "Runtime Contract: Schema and Persistence Implementation",
                "feat/data-schema-strategy-20666c21-3279867026003486022", Instant.now().minusSeconds(13));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(freshPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/3279867026003486022");
        session.setLastProgressAt(Instant.now());
        when(julesSessionRepository.findAll()).thenReturn(List.of(session));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(0, cleaned);
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void doesNotCloseADescriptivelyNamedBranchWithARecentLiveSession() {
        // The naming-convention fix: a branch NOT named "jules-..." must still get the same live-task
        // lookup as one that is - naming was never legitimate evidence of anything.
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(3, "Runtime Contract: Schema and Persistence Implementation",
                "feat/data-schema-strategy-20666c21-abc", Instant.now().minus(20, ChronoUnit.MINUTES));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/20666c21-abc");
        session.setLastProgressAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(julesSessionRepository.findAll()).thenReturn(List.of(session));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(0, cleaned);
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void retiresADescriptivelyNamedBranchOnlyWhenItsSessionIsGenuinelyStale() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(7, "Some feature", "feat/genuinely-abandoned-work-xyz789",
                Instant.now().minus(3, ChronoUnit.HOURS));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/xyz789");
        session.setLastProgressAt(Instant.now().minus(2, ChronoUnit.HOURS));
        when(julesSessionRepository.findAll()).thenReturn(List.of(session));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        // retireAbandonedBranchAndPR re-fetches the PR fresh by number before closing it.
        when(gitHubPullRequestService.fetchPullRequestByNumber(project, 7)).thenReturn(Optional.of(openPr));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(1, cleaned);
        verify(gitHubPullRequestService).closeSinglePullRequest(eq(project), eq(openPr), anyString());
        assertEquals(TaskStatus.queued, task.getStatus());
    }

    @Test
    void closesATrulyOrphanedPrWithNoSessionAnywhereImmediately() {
        // No grace period needed here either: a session's token exists from dispatch time onward, so if
        // literally no session anywhere - live or historical - was ever dispatched with a token matching
        // this branch, nothing will ever come along later to claim it either.
        setUp();
        ProjectEntity project = project();
        var openPr = pr(9, "Abandoned work", "some-random-branch-name", Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));
        when(julesSessionRepository.findAll()).thenReturn(List.of());

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(1, cleaned);
        verify(gitHubPullRequestService).closeSinglePullRequest(project, openPr, "Branch GC: Orphaned PR without active task");
        verify(gitHubPullRequestService).deleteBranch(project, "some-random-branch-name");
    }

    @Test
    void closeoutTitledPrsAreStillClosedImmediately() {
        // Unchanged behavior sanity check - this path is system-generated (our own title, not Jules's
        // branch naming) and is intentionally left as-is.
        setUp();
        ProjectEntity project = project();
        var closeoutPr = pr(11, "Closeout: feature thread finished", "closeout-branch", Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(closeoutPr));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(1, cleaned);
        verify(gitHubPullRequestService).closeSinglePullRequest(project, closeoutPr,
                "Branch GC: Orphaned closeout PR superseded by main");
        verifyNoInteractions(julesSessionRepository);
    }
}
