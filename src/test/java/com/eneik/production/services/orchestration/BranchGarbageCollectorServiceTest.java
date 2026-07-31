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
 * Regression suite for the 2026-07-31 incident: a real, 36-second-old PR with genuine committed work
 * (Flyway migration) was closed by this sweep purely because its branch was named "feat/..." instead of
 * "jules-..." - a naming-convention check standing in for real evidence. Every case here proves the
 * decision now depends only on independently-verifiable facts (a live session/task, and real elapsed time),
 * never on how Jules happened to name a branch or title a PR.
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
                taskConflictRepository, julesSessionRepository);
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
    void neverTouchesAPrYoungerThanTheGracePeriodRegardlessOfBranchName() {
        // Direct regression test for the incident shape: a descriptively-named branch, 36 seconds old,
        // must not be touched at all - not even inspected for a live session - until it clears the grace
        // period.
        setUp();
        ProjectEntity project = project();
        var freshPr = pr(3, "Runtime Contract: Schema and Persistence Implementation",
                "feat/data-schema-strategy-20666c21-abc", Instant.now().minusSeconds(36));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(freshPr));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(0, cleaned);
        verifyNoInteractions(julesSessionRepository);
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
    }

    @Test
    void doesNotCloseADescriptivelyNamedBranchWithARecentLiveSession() {
        // The core fix: a branch NOT named "jules-..." must still get the same live-task lookup as one
        // that is - naming was never legitimate evidence of anything.
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(3, "Runtime Contract: Schema and Persistence Implementation",
                "feat/data-schema-strategy-20666c21-abc", Instant.now().minus(20, ChronoUnit.MINUTES));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/repo/pull/3");
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
        var openPr = pr(7, "Some feature", "feat/genuinely-abandoned-work",
                Instant.now().minus(3, ChronoUnit.HOURS));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/repo/pull/7");
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
    void closesATrulyOrphanedPrWithNoSessionAnywhereOnceItClearsTheGracePeriod() {
        setUp();
        ProjectEntity project = project();
        var openPr = pr(9, "Abandoned work", "some-random-branch-name",
                Instant.now().minus(1, ChronoUnit.HOURS));
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
