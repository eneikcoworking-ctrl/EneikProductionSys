package com.eneik.production.services;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test screen for Task 25 (GitHub sweep budget efficiency / Laws 8, 11, 13, 25).
 *
 * <p>Enforces the 5 properties stated in CLAUDE_COORDINATION_NOTE.md:
 * 1. Upon N reviews whose PRs are terminal on GitHub, exactly 0 calls are made to them in a window.
 * 2. No external call is addressed to a repository without a corresponding project row.
 * 3. Unattributable review is recorded once, drops from polling, and stays in the decision set.
 * 4. Pull request snapshot for a project is fetched at most once per tick across all consumers.
 * 5. Reverse case: live unverified review of an active project is polled as before.
 */
class GitHubSweepBudgetEfficiencyTest {

    private PrReviewRepository prReviewRepository;
    private JulesSessionRepository julesSessionRepository;
    private TaskRepository taskRepository;
    private ProjectRepository projectRepository;
    private SystemSettingsService settingsService;
    private GitHubPullRequestService gitHubPullRequestService;
    private AutoMergeService autoMergeService;

    @BeforeEach
    void setUp() {
        prReviewRepository = mock(PrReviewRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        taskRepository = mock(TaskRepository.class);
        projectRepository = mock(ProjectRepository.class);
        settingsService = mock(SystemSettingsService.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);

        when(settingsService.effectiveBoolean("github_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("github_token")).thenReturn("test-token");

        autoMergeService = new AutoMergeService(
                prReviewRepository,
                julesSessionRepository,
                taskRepository,
                settingsService,
                new ObjectMapper(),
                mock(com.eneik.production.services.advice.RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.services.jules.JulesDispatchService.class),
                mock(RoleCapabilityLoader.class),
                mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHubPullRequestService,
                new GitHubApiBudgetService(),
                mock(com.eneik.production.services.video.VideoAssetService.class),
                mock(com.eneik.production.services.dashboard.ProjectOperationalContextService.class),
                mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
                mock(CodeChangeClassifier.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class),
                projectRepository,
                mock(ClientDeliverableReadinessService.class),
                mock(GeminiContextService.class),
                mock(ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class)
        );
    }

    private ProjectEntity createActiveProject(String repoName) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName(repoName);
        project.setSlug(repoName);
        project.setRepositoryName(repoName);
        project.setStatus(ProjectStatus.active);
        return project;
    }

    private PrReviewEntity createReview(String prUrl, String ciStatus, Boolean merged) {
        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());
        review.setPrUrl(prUrl);
        review.setCiStatus(ciStatus);
        review.setMerged(merged);
        return review;
    }

    @Test
    @DisplayName("Property 1: N reviews whose PRs are terminal on GitHub yield exactly 0 external calls in sweep")
    void property1_terminalReviewsYieldZeroCalls() {
        ProjectEntity activeProject = createActiveProject("test-fiftieth");
        when(projectRepository.findAll()).thenReturn(List.of(activeProject));

        // 5 reviews already marked as closed_unmerged
        List<PrReviewEntity> terminalReviews = List.of(
                createReview("https://github.com/eneikdru/test-fiftieth/pull/1", "closed_unmerged", false),
                createReview("https://github.com/eneikdru/test-fiftieth/pull/2", "closed_unmerged", false),
                createReview("https://github.com/eneikdru/test-fiftieth/pull/3", "closed_unmerged", null),
                createReview("https://github.com/eneikdru/test-fiftieth/pull/4", "closed_unmerged", false),
                createReview("https://github.com/eneikdru/test-fiftieth/pull/5", "closed_unmerged", false)
        );
        when(prReviewRepository.findByMergedFalseOrMergedIsNull()).thenReturn(terminalReviews);

        // Neither resurrectAlreadyMergedReviews nor reconcileTerminalGithubStateForReviews should call GitHub
        autoMergeService.resurrectAlreadyMergedReviews();
        int reconciled = autoMergeService.reconcileTerminalGithubStateForReviews();

        assertEquals(0, reconciled);
        verifyNoInteractions(gitHubPullRequestService);
    }

    @Test
    @DisplayName("Property 1 (dynamic terminalization): newly observed closed PR is terminalized and never polled again")
    void property1_newlyObservedClosedPrIsTerminalizedAndNeverPolledAgain() {
        ProjectEntity activeProject = createActiveProject("test-fiftieth");
        when(projectRepository.findAll()).thenReturn(List.of(activeProject));

        PrReviewEntity pendingReview = createReview("https://github.com/eneikdru/test-fiftieth/pull/10", "pending", false);
        when(prReviewRepository.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(pendingReview));

        // GitHub reports the PR is closed and unmerged
        GitHubPullRequestService.GitHubPullRequest closedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-fiftieth/pull/10",
                10,
                "stale task",
                "branch-10",
                "jules",
                false,
                "main",
                true,
                Instant.now()
        );
        when(gitHubPullRequestService.fetchPullRequestByNumber("eneikdru", "test-fiftieth", 10))
                .thenReturn(Optional.of(closedPr));

        // Reconcile pass detects closed PR and terminalizes local review
        int reconciled = autoMergeService.reconcileTerminalGithubStateForReviews();
        assertEquals(1, reconciled);
        assertEquals("closed_unmerged", pendingReview.getCiStatus());
        verify(prReviewRepository).save(pendingReview);

        // Subsequent resurrect and reconcile passes must make 0 additional calls
        autoMergeService.resurrectAlreadyMergedReviews();
        autoMergeService.reconcileTerminalGithubStateForReviews();

        // Exactly 1 call was made throughout the entire sequence
        verify(gitHubPullRequestService, times(1))
                .fetchPullRequestByNumber("eneikdru", "test-fiftieth", 10);
    }

    @Test
    @DisplayName("Property 2: No call is addressed to a repository without a corresponding project row")
    void property2_noCallToRepositoryWithoutProjectRow() {
        // DB only contains project "test-fiftieth", while orphaned reviews point to deleted "test-twentieth" & "test-twenty-first"
        ProjectEntity fiftieth = createActiveProject("test-fiftieth");
        when(projectRepository.findAll()).thenReturn(List.of(fiftieth));

        PrReviewEntity orphan1 = createReview("https://github.com/eneikdru/test-twentieth/pull/5", "pending", false);
        PrReviewEntity orphan2 = createReview("https://github.com/eneikdru/test-twenty-first/pull/8", "success", false);
        when(prReviewRepository.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(orphan1, orphan2));

        autoMergeService.reconcileTerminalGithubStateForReviews();
        autoMergeService.resurrectAlreadyMergedReviews();

        verify(gitHubPullRequestService, never()).fetchPullRequestByNumber(eq("eneikdru"), eq("test-twentieth"), anyInt());
        verify(gitHubPullRequestService, never()).fetchPullRequestByNumber(eq("eneikdru"), eq("test-twenty-first"), anyInt());
        verifyNoInteractions(gitHubPullRequestService);
    }

    @Test
    @DisplayName("Property 3: Unattributable review is recorded once, spend is 0, and remains in decision set")
    void property3_unattributableReviewRecordedOnceSpendZeroStaysInDecisionSet() {
        when(projectRepository.findAll()).thenReturn(List.of());

        UUID reviewId = UUID.randomUUID();
        PrReviewEntity unattributableReview = createReview("https://github.com/eneikdru/unknown-repo/pull/1", "pending", false);
        unattributableReview.setId(reviewId);

        // Multiple evaluation passes (e.g. across successive ticks)
        for (int i = 0; i < 5; i++) {
            boolean active = autoMergeService.belongsToActiveProject(unattributableReview);
            assertFalse(active, "spend must be 0 (returns false) for unattributable row");
        }

        // Recorded/logged once
        assertTrue(autoMergeService.getLoggedUnattributableReviews().contains(reviewId));
        assertEquals(1, autoMergeService.getLoggedUnattributableReviews().size());

        // Stays in decision set: ciStatus is untouched (not failed, not invalid), merged is untouched
        assertEquals("pending", unattributableReview.getCiStatus());
        assertEquals(false, unattributableReview.getMerged());
        verify(prReviewRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Property 4: Pull request snapshot is fetched at most once per tick across all consumers")
    void property4_snapshotFetchedAtMostOncePerTickAcrossAllConsumers() throws Exception {
        GithubConfig githubConfig = mock(GithubConfig.class);
        when(githubConfig.getOrganization()).thenReturn("eneikdru");
        when(githubConfig.getToken()).thenReturn("ghp_test_token");
        when(githubConfig.getApiBaseUrl()).thenReturn("https://api.github.com");

        HttpClient httpClient = mock(HttpClient.class);
        GitHubApiBudgetService budgetService = new GitHubApiBudgetService();

        GitHubPullRequestService serviceWithCache = new GitHubPullRequestService(
                githubConfig, settingsService, new ObjectMapper(), budgetService, httpClient
        );

        Instant baseTime = Instant.parse("2026-09-05T02:00:00Z");
        Clock testClock = Clock.fixed(baseTime, ZoneId.of("UTC"));
        serviceWithCache.setClock(testClock);

        ProjectEntity project = createActiveProject("test-fiftieth");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[]");
        when(mockResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // 14 independent callers request snapshot within the same tick
        for (int i = 0; i < 14; i++) {
            GitHubPullRequestService.PullRequestSnapshot snapshot = serviceWithCache.pullRequestSnapshot(project);
            assertTrue(snapshot.available());
            assertEquals("test-fiftieth", snapshot.repo());
        }

        // Exactly 2 HTTP calls were made (1 open PRs listing + 1 closed PRs listing) instead of 14 * 2 = 28 calls!
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Advance clock within tick (< 20s TTL) -> still cached
        serviceWithCache.setClock(Clock.fixed(baseTime.plusSeconds(10), ZoneId.of("UTC")));
        serviceWithCache.pullRequestSnapshot(project);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Advance clock past tick TTL (e.g. 25s) -> next tick fetches fresh snapshot
        serviceWithCache.setClock(Clock.fixed(baseTime.plusSeconds(25), ZoneId.of("UTC")));
        serviceWithCache.pullRequestSnapshot(project);
        verify(httpClient, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Explicit cache invalidation upon merge or close also forces fresh fetch
        serviceWithCache.invalidateSnapshotCache(project.getId());
        serviceWithCache.pullRequestSnapshot(project);
        verify(httpClient, times(6)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Property 5 (Reverse Case): Live unverified review of an active project is polled as before")
    void property5_liveUnverifiedReviewOfActiveProjectIsPolledAsBefore() {
        ProjectEntity activeProject = createActiveProject("test-fiftieth");
        when(projectRepository.findAll()).thenReturn(List.of(activeProject));

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(activeProject);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setStatus("pr_opened");

        PrReviewEntity liveReview = createReview("https://github.com/eneikdru/test-fiftieth/pull/42", "pending", false);
        liveReview.setJulesSessionId(sessionId);

        when(prReviewRepository.findByMergedFalseOrMergedIsNull()).thenReturn(List.of(liveReview));
        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        GitHubPullRequestService.GitHubPullRequest openPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-fiftieth/pull/42",
                42,
                "live active work",
                "branch-42",
                "jules",
                false,
                "main",
                false,
                Instant.now()
        );
        when(gitHubPullRequestService.fetchPullRequestByNumber("eneikdru", "test-fiftieth", 42))
                .thenReturn(Optional.of(openPr));

        // Live review belongs to active project
        assertTrue(autoMergeService.belongsToActiveProject(liveReview));

        // Reconcile pass polls GitHub for the live review
        autoMergeService.reconcileTerminalGithubStateForReviews();
        verify(gitHubPullRequestService).fetchPullRequestByNumber("eneikdru", "test-fiftieth", 42);

        // Since PR is open, review is NOT terminalized
        assertEquals("pending", liveReview.getCiStatus());
        assertEquals(false, liveReview.getMerged());
    }

    @Test
    @DisplayName("Property 1 (multi-tick terminalization): closed PR terminalized in tick 1 is not rewritten or re-polled in tick 2")
    void property1_multiTickTerminalizationSurvivesBetweenTicks() {
        ProjectEntity activeProject = createActiveProject("test-fiftieth");
        when(projectRepository.findAll()).thenReturn(List.of(activeProject));
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(activeProject));

        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(activeProject);
        task.setStatus(TaskStatus.done);

        UUID staleSessionId = UUID.randomUUID();
        JulesSessionEntity staleSession = new JulesSessionEntity();
        staleSession.setId(staleSessionId);
        staleSession.setTaskId(taskId);
        staleSession.setStatus("closed_rejected");
        staleSession.setPrUrl("https://github.com/eneikdru/test-fiftieth/pull/10");

        UUID winningSessionId = UUID.randomUUID();
        JulesSessionEntity winningSession = new JulesSessionEntity();
        winningSession.setId(winningSessionId);
        winningSession.setTaskId(taskId);
        winningSession.setStatus("merged");
        winningSession.setPrUrl("https://github.com/eneikdru/test-fiftieth/pull/100");

        PrReviewEntity staleReview = createReview("https://github.com/eneikdru/test-fiftieth/pull/10", "superseded", false);
        staleReview.setJulesSessionId(staleSessionId);

        PrReviewEntity winningReview = createReview("https://github.com/eneikdru/test-fiftieth/pull/100", "success", true);
        winningReview.setJulesSessionId(winningSessionId);

        when(julesSessionRepository.findById(staleSessionId)).thenReturn(Optional.of(staleSession));
        when(julesSessionRepository.findById(winningSessionId)).thenReturn(Optional.of(winningSession));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        when(prReviewRepository.findByMergedFalseOrMergedIsNull()).thenAnswer(inv -> List.of(staleReview));
        when(prReviewRepository.findByJulesSessionIdIsNotNull()).thenAnswer(inv -> List.of(staleReview, winningReview));
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(staleSession, winningSession));

        // GitHub snapshot for project
        GitHubPullRequestService.GitHubPullRequest mergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-fiftieth/pull/100",
                100, "merged work", "b-100", "jules", true, "main", true, Instant.now()
        );
        GitHubPullRequestService.GitHubPullRequest closedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/eneikdru/test-fiftieth/pull/10",
                10, "stale work", "b-10", "jules", false, "main", true, Instant.now()
        );

        GitHubPullRequestService.PullRequestSnapshot snapshot = new GitHubPullRequestService.PullRequestSnapshot(
                true, "eneikdru", "test-fiftieth", List.of(), List.of(mergedPr, closedPr), null
        );
        when(gitHubPullRequestService.pullRequestSnapshot(activeProject)).thenReturn(snapshot);
        when(julesSessionRepository.findByPrUrlIn(any())).thenReturn(List.of(winningSession));

        when(gitHubPullRequestService.fetchPullRequestByNumber("eneikdru", "test-fiftieth", 10))
                .thenReturn(Optional.of(closedPr));

        // --- TICK 1 ---
        autoMergeService.reconcileMergedGitHubPullRequests();
        autoMergeService.resurrectAlreadyMergedReviews();

        // Tick 1 terminalized staleReview to closed_unmerged
        assertEquals("closed_unmerged", staleReview.getCiStatus());
        verify(gitHubPullRequestService, times(1))
                .fetchPullRequestByNumber("eneikdru", "test-fiftieth", 10);

        // --- TICK 2 ---
        // Simulating second tick: reconcile and resurrect run again
        autoMergeService.reconcileMergedGitHubPullRequests();
        autoMergeService.resurrectAlreadyMergedReviews();

        // Stale review MUST REMAIN closed_unmerged (not overwritten to superseded!)
        assertEquals("closed_unmerged", staleReview.getCiStatus());

        // Zero additional calls to GitHub in tick 2! Still exactly 1 call total.
        verify(gitHubPullRequestService, times(1))
                .fetchPullRequestByNumber("eneikdru", "test-fiftieth", 10);
    }
}
