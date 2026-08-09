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
    private com.eneik.production.services.ProjectFlowService projectFlowService;
    private com.eneik.production.services.jules.SessionLifecycleService sessionLifecycleService;
    private com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private BranchGarbageCollectorService service;

    private void setUp() {
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        taskRepository = mock(TaskRepository.class);
        taskConflictRepository = mock(TaskConflictRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        projectFlowService = mock(com.eneik.production.services.ProjectFlowService.class);
        sessionLifecycleService = mock(com.eneik.production.services.jules.SessionLifecycleService.class);
        featureThreadRepository = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        service = new BranchGarbageCollectorService(gitHubPullRequestService, taskRepository,
                taskConflictRepository, julesSessionRepository,
                sessionLifecycleService,
                projectFlowService,
                featureThreadRepository);
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

    /**
     * Direct regression test for the 2026-08-08 incident (feature 1ad15184, test-forty-third, PR#53): the
     * old version of this branch closed ANY open PR whose title merely started with "Closeout", on every
     * ~1-2 minute sweep, with no check that the feature was actually already merged - a real closeout PR
     * got destroyed under two minutes after opening, before it ever had a chance to merge. This test proves
     * a closeout PR whose feature thread confirms it is STILL the live, unmerged closeout attempt must be
     * left alone.
     */
    @Test
    void leavesALiveCloseoutPrAloneWhenItsFeatureThreadConfirmsItIsStillTheRealAttempt() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        String prUrl = "https://github.com/org/repo/pull/11";
        var closeoutPr = new GitHubPullRequestService.GitHubPullRequest(prUrl, 11,
                "Closeout: integrate feature " + featureId + " into main", "closeout-branch", "eneikdru",
                false, "main", false, Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(closeoutPr));

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        thread.setCloseoutPrUrl(prUrl);
        thread.setMergedToMainAt(null);
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(0, cleaned);
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
        verifyNoInteractions(julesSessionRepository);
    }

    @Test
    void closesACloseoutPrOnlyWhenItsFeatureThreadConfirmsItIsGenuinelySuperseded() {
        setUp();
        ProjectEntity project = project();
        UUID featureId = UUID.randomUUID();
        var staleClosePr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/11", 11,
                "Closeout: integrate feature " + featureId + " into main", "closeout-branch", "eneikdru",
                false, "main", false, Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(staleClosePr));

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setProjectId(project.getId());
        thread.setFeatureId(featureId);
        // Thread's own record says already merged elsewhere - this specific still-open PR is stale.
        thread.setMergedToMainAt(Instant.now().minusSeconds(60));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(1, cleaned);
        verify(gitHubPullRequestService).closeSinglePullRequest(project, staleClosePr,
                "Branch GC: Orphaned closeout PR superseded by main");
        verifyNoInteractions(julesSessionRepository);
    }

    @Test
    void leavesACloseoutTitledPrAloneWhenNoFeatureThreadRecordCanConfirmSupersession() {
        // Unparseable title or no matching thread row - cannot positively confirm supersession, so this
        // must NOT default to closing (that default was the exact 2026-08-08 bug this whole fix closes).
        setUp();
        ProjectEntity project = project();
        var closeoutPr = pr(11, "Closeout: feature thread finished", "closeout-branch", Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(closeoutPr));

        int cleaned = service.cleanOrphanedAndStagnatedPullRequests(project);

        assertEquals(0, cleaned);
        verify(gitHubPullRequestService, never()).closeSinglePullRequest(any(), any(), anyString());
        verifyNoInteractions(julesSessionRepository);
    }

    /**
     * Direct regression test for the 2026-08-03 incident (task 074efcb3, PR#37 stale/PR#38 real and
     * successful, both on the same task): retireAbandonedBranchAndPR's own session cleanup ("Step 3.5")
     * used to cancel EVERY active session on the task, not just the one tied to the branch/PR actually
     * being retired - collaterally cancelling a completely unrelated, genuinely successful PR's session the
     * moment a different stale PR on the same task got cleaned up.
     */
    @Test
    void retireAbandonedBranchAndPrOnlyCancelsTheSessionMatchingTheRetiredBranch() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity staleSession = new JulesSessionEntity();
        staleSession.setId(UUID.randomUUID());
        staleSession.setTaskId(taskId);
        staleSession.setExternalSessionId("sessions/17697520428508506718");
        staleSession.setStatus("pr_opened");

        JulesSessionEntity unrelatedLiveSession = new JulesSessionEntity();
        unrelatedLiveSession.setId(UUID.randomUUID());
        unrelatedLiveSession.setTaskId(taskId);
        unrelatedLiveSession.setExternalSessionId("sessions/5354685196398021436");
        unrelatedLiveSession.setStatus("pr_opened");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(staleSession, unrelatedLiveSession));
        when(gitHubPullRequestService.fetchPullRequestByNumber(project, 37))
                .thenReturn(Optional.of(pr(37, "Stale attempt", "jules-17697520428508506718-1fb880b9", Instant.now())));

        boolean retired = service.retireAbandonedBranchAndPR(project, task,
                "jules-17697520428508506718-1fb880b9", 37, "Branch GC: Cleaning stagnated PR #37");

        assertEquals(true, retired);
        verify(sessionLifecycleService).retireSessionOnly(eq(staleSession.getId()), anyString());
        verify(sessionLifecycleService, never()).retireSessionOnly(eq(unrelatedLiveSession.getId()), anyString());
    }

    @Test
    void retireAbandonedBranchAndPrSkipsSessionCleanupWhenBranchNameIsBlank() {
        // We have no way to know which session is ours to touch without a branch name - skip rather than
        // guess (guessing "cancel everything on the task" was exactly the 2026-08-03 bug).
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/abc");
        session.setStatus("pr_opened");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));

        boolean retired = service.retireAbandonedBranchAndPR(project, task, null, null, "no branch known");

        assertEquals(true, retired);
        verifyNoInteractions(sessionLifecycleService);
    }

    /**
     * findOrphanedPrCandidates coverage: a session whose owning task's PR is still open on GitHub but whose
     * own status already ended terminal (cancelled/closed_terminal_task/failed) is a real orphan - nothing
     * else in the system will ever pick it back up, since every other sweep filters by active session
     * status. A session that is still genuinely live (running/pr_opened/etc.) is not an orphan candidate -
     * flagging it would be a false positive on real, ongoing work.
     */
    @Test
    void findOrphanedPrCandidatesDetectsATerminalSessionWithAStillOpenPr() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(38, "Moodle Financial Visibility Verification",
                "jules-5354685196398021436-be7bfa86", Instant.now().minus(5, ChronoUnit.HOURS));
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity cancelledSession = new JulesSessionEntity();
        cancelledSession.setTaskId(taskId);
        cancelledSession.setExternalSessionId("sessions/5354685196398021436");
        cancelledSession.setStatus("cancelled");
        when(julesSessionRepository.findAll()).thenReturn(List.of(cancelledSession));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        List<BranchGarbageCollectorService.OrphanedPrCandidate> candidates = service.findOrphanedPrCandidates(project);

        assertEquals(1, candidates.size());
        assertEquals(38, candidates.get(0).pullNumber());
        assertEquals(taskId, candidates.get(0).taskId());
    }

    @Test
    void findOrphanedPrCandidatesIgnoresAGenuinelyLiveSession() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(40, "Still in progress", "jules-999-abc", Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity liveSession = new JulesSessionEntity();
        liveSession.setTaskId(taskId);
        liveSession.setExternalSessionId("sessions/999");
        liveSession.setStatus("pr_opened");
        when(julesSessionRepository.findAll()).thenReturn(List.of(liveSession));

        List<BranchGarbageCollectorService.OrphanedPrCandidate> candidates = service.findOrphanedPrCandidates(project);

        assertEquals(0, candidates.size());
    }

    @Test
    void findOrphanedPrCandidatesExcludesDoneTasksConsistentWithTheOtherSweep() {
        setUp();
        ProjectEntity project = project();
        UUID taskId = UUID.randomUUID();
        var openPr = pr(41, "Already done elsewhere", "jules-777-abc", Instant.now());
        when(gitHubPullRequestService.fetchOpenPullRequests(project)).thenReturn(List.of(openPr));

        JulesSessionEntity terminalSession = new JulesSessionEntity();
        terminalSession.setTaskId(taskId);
        terminalSession.setExternalSessionId("sessions/777");
        terminalSession.setStatus("failed");
        when(julesSessionRepository.findAll()).thenReturn(List.of(terminalSession));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.done);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        List<BranchGarbageCollectorService.OrphanedPrCandidate> candidates = service.findOrphanedPrCandidates(project);

        assertEquals(0, candidates.size());
    }
}
