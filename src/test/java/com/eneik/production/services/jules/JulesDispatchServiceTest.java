package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JulesDispatchServiceTest {

    private JulesApiClient julesApiClient;
    private JulesSessionRepository julesSessionRepository;
    private com.eneik.production.repositories.JulesActivityResponseRepository julesActivityResponseRepository;
    private WishlistRepository wishlistRepository;
    private com.eneik.production.repositories.AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private com.eneik.production.services.ClaimService claimService;
    private com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient;
    private com.eneik.production.services.RoleCapabilityLoader roleCapabilityLoader;
    private com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private com.eneik.production.services.ClientDeliverableReadinessService readinessService;
    private com.eneik.production.services.ProjectFlowService projectFlowService;
    private com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private com.eneik.production.services.GeminiContextService geminiContextService;
    private com.eneik.production.repositories.ReviewConcernRepository reviewConcernRepository;
    private com.eneik.production.repositories.RoleRepository roleRepository;
    private com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService;
    private com.eneik.production.services.FalsificationCycleService falsificationCycleService;
    private JulesDispatchService julesDispatchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        julesApiClient = mock(JulesApiClient.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        julesActivityResponseRepository = mock(com.eneik.production.repositories.JulesActivityResponseRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        accountRepository = mock(com.eneik.production.repositories.AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(com.eneik.production.services.ClaimService.class);
        roleCapabilityLoader = mock(com.eneik.production.services.RoleCapabilityLoader.class);
        com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService = mock(com.eneik.production.services.monitor.PrReviewPipelineService.class);
        mlPredictionServiceClient = mock(com.eneik.production.services.MLPredictionServiceClient.class);
        roleRepository = mock(com.eneik.production.repositories.RoleRepository.class);
        com.eneik.production.repositories.TaskConflictRepository taskConflictRepository = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        gitHubPullRequestService = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository = mock(com.eneik.production.repositories.PrReviewRepository.class);
        featureThreadRepository = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        readinessService = mock(com.eneik.production.services.ClientDeliverableReadinessService.class);
        projectFlowService = mock(com.eneik.production.services.ProjectFlowService.class);
        geminiContextService = mock(com.eneik.production.services.GeminiContextService.class);
        reviewConcernRepository = mock(com.eneik.production.repositories.ReviewConcernRepository.class);
        persistentWorkerSessionService = mock(com.eneik.production.services.PersistentWorkerSessionService.class);
        falsificationCycleService = mock(com.eneik.production.services.FalsificationCycleService.class);
        julesDispatchService = new JulesDispatchService(
            julesApiClient, julesSessionRepository, julesActivityResponseRepository, wishlistRepository, accountRepository, taskRepository, taskConflictRepository, claimService, roleCapabilityLoader,
            prReviewPipelineService, mlPredictionServiceClient, roleRepository, gitHubPullRequestService, prReviewRepository,
            mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
            projectFlowService,
            falsificationCycleService,
            featureThreadRepository, readinessService,
            persistentWorkerSessionService,
            mock(com.eneik.production.repositories.ProjectRepository.class),
            mock(com.eneik.production.services.WishlistContentSimilarityMatcher.class),
            mock(com.eneik.production.services.settings.SystemSettingsService.class),
            geminiContextService,
            reviewConcernRepository,
            mock(com.eneik.production.services.accounts.AccountHealthService.class),
            // Real instance, not a mock: cancelSession delegates the actual local-status mutation +
            // remote-delete attempt to this service (2026-08-01, single choke point for "session is done").
            // Wired to the SAME julesSessionRepository/accountRepository/taskRepository mocks this test
            // class already uses, so it observably behaves the same way production wiring does - a mock
            // here would silently no-op the mutation these tests assert on.
            newSessionLifecycleServiceForTest(),
            mock(com.eneik.production.repositories.DesignShopCycleRepository.class),
            mock(com.eneik.production.services.stitch.StitchClient.class),
            "prefix/",
            null
        );
        ReflectionTestUtils.setField(julesDispatchService, "self", julesDispatchService);
        // 2026-08-14 (bug-hunt sweep): handlePrOpenedWorkflow now claims a mutual-exclusion flag via
        // julesSessionRepository.claimPrOpenedWorkflow before doing anything else - default this mock to
        // "claim succeeds" (1) so every existing handlePrOpenedWorkflow test below still reaches its real
        // handler logic; Mockito's own default for an unstubbed int-returning method is 0, which would
        // silently skip all of them. A dedicated test overrides this to exercise the "already claimed" path.
        when(julesSessionRepository.claimPrOpenedWorkflow(any(), any(), any())).thenReturn(1);
        ReflectionTestUtils.setField(julesDispatchService, "stuckThresholdMinutes", 30);
        ReflectionTestUtils.setField(julesDispatchService, "stuckCloseThresholdMinutes", 120);
        ReflectionTestUtils.setField(julesDispatchService, "maxAgentDialogResponses", 8);
        ReflectionTestUtils.setField(julesDispatchService, "loopCloseSimilarThreshold", 3);
        ReflectionTestUtils.setField(julesDispatchService, "forcedUnblockBlindCycleThreshold", 5);
        ReflectionTestUtils.setField(julesDispatchService, "forcedUnblockMaxAttempts", 2);
        ReflectionTestUtils.setField(julesDispatchService, "davidsonVetoCeilingMinutes", 360);
        ReflectionTestUtils.setField(julesDispatchService, "activitiesPageSize", 20);
        ReflectionTestUtils.setField(julesDispatchService, "maxActivityPagesPerCycle", 20);
    }

    // 2026-08-14 (bug-hunt sweep): SessionLifecycleService now needs its own self-proxy field (same
    // REQUIRES_NEW-via-self pattern as JulesDispatchService.self, for the same reason) - constructed with
    // null then wired via ReflectionTestUtils, mirroring exactly how julesDispatchService's own self is
    // set up above.
    private SessionLifecycleService newSessionLifecycleServiceForTest() {
        SessionLifecycleService service = new SessionLifecycleService(
                julesSessionRepository, accountRepository, taskRepository, mock(JulesApiClient.class), null);
        ReflectionTestUtils.setField(service, "self", service);
        return service;
    }

    /**
     * Guard for the loop-invariant rule (2026-08-29, plan §4.25): a value whose only argument is the
     * PROJECT is observed once per sweep, not once per element of the sweep.
     *
     * <p>Measured before it was forbidden: this sweep asked GitHub for the same project's pull requests
     * once per non-terminal task and once more per done-but-unmerged task - nine calls on the live
     * circuit, two paginated fetches each, where one call answers for all of them. Beyond the cost it was
     * also less correct: nine calls observe nine different states of the repository, so two tasks decided
     * within one sweep could be decided against two different realities (Charter invariant 9).
     *
     * <p>The assertion is on the COUNT rather than on any outcome, because the defect is invisible in any
     * single outcome - every individual call returned the right answer.
     */
    private ProjectEntity activeProjectForReconciliation() {
        var settingsService = (com.eneik.production.services.settings.SystemSettingsService)
                ReflectionTestUtils.getField(julesDispatchService, "settingsService");
        when(settingsService.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");
        project.setStatus(ProjectStatus.active);
        return project;
    }

    private TaskEntity taskWithClosedUnmergedPr(ProjectEntity project, TaskStatus status, int prNumber) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(status);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        session.setExternalSessionId("sessions/" + prNumber);
        session.setStatus("pr_opened");
        session.setCreatedAt(Instant.now());
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(claimService.hasActiveClaim(task.getId())).thenReturn(true);

        var closedPr = new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/" + prNumber, prNumber, "title", "jules-" + prNumber,
                "author", false, "main", true, Instant.now());
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(
                new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                        true, "org", "repo", List.of(), List.of(closedPr), ""));
        return task;
    }

    /**
     * Plan §4.27. A live claim vetoes this sweep because an implementer whose PR closed can push a new
     * branch and open another one - true while it is implementing, false once pending_review says its
     * phase completed. Measured live: one such task held the whole project in BLOCKED_BY_REVIEW, which
     * denies ORCHESTRATE, which is what compiles wishlists - six ticks with nothing compiled at all.
     */
    @Test
    void closedUnmergedPrIsActedOnOnceTheImplementerPhaseIsCompleteEvenWithALiveClaim() {
        ProjectEntity project = activeProjectForReconciliation();
        TaskEntity task = taskWithClosedUnmergedPr(project, TaskStatus.pending_review, 418);
        when(taskRepository.findByStatusIn(any())).thenReturn(List.of(task));
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of());
        when(taskRepository.writeStatusUnlessTerminal(task.getId(), TaskStatus.failed)).thenReturn(1);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verify(taskRepository).writeStatusUnlessTerminal(task.getId(), TaskStatus.failed);
    }

    /**
     * The other half, and it is not optional: while the implementer can still act, the claim must go on
     * vetoing this sweep. Without this case the test above would also pass if the veto were removed
     * altogether.
     */
    @Test
    void closedUnmergedPrIsStillLeftAloneWhileTheImplementerCanOpenAnotherOne() {
        ProjectEntity project = activeProjectForReconciliation();
        TaskEntity task = taskWithClosedUnmergedPr(project, TaskStatus.claimed, 419);
        when(taskRepository.findByStatusIn(any())).thenReturn(List.of(task));
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of());

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verify(taskRepository, never()).writeStatusUnlessTerminal(task.getId(), TaskStatus.failed);
    }

    @Test
    void reconciliationSweepObservesGitHubOncePerProjectNotOncePerTask() {
        var settingsService = (com.eneik.production.services.settings.SystemSettingsService)
                ReflectionTestUtils.getField(julesDispatchService, "settingsService");
        when(settingsService.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");
        project.setStatus(ProjectStatus.active);

        List<TaskEntity> nonTerminal = new ArrayList<>();
        List<TaskEntity> done = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TaskEntity task = new TaskEntity();
            task.setId(UUID.randomUUID());
            task.setProject(project);
            task.setStatus(i < 3 ? TaskStatus.queued : TaskStatus.done);
            (i < 3 ? nonTerminal : done).add(task);

            JulesSessionEntity session = new JulesSessionEntity();
            session.setId(UUID.randomUUID());
            session.setTaskId(task.getId());
            session.setExternalSessionId("sessions/" + i);
            session.setCreatedAt(Instant.now());
            when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        }
        when(taskRepository.findByStatusIn(any())).thenReturn(nonTerminal);
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(done);
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(
                new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                        false, "owner", "repo", List.of(), List.of(), "unavailable for this test"));

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        // Five tasks of one project, one observation. Not "at most one call per task".
        verify(gitHubPullRequestService, times(1)).pullRequestSnapshot(project);
    }

    @Test
    void testMapExternalStatus() {
        assertEquals("queued", julesDispatchService.mapExternalStatus("QUEUED"));
        assertEquals("running", julesDispatchService.mapExternalStatus("RUNNING"));
        assertEquals("pr_opened", julesDispatchService.mapExternalStatus("SUCCEEDED"));
        assertEquals("failed", julesDispatchService.mapExternalStatus("FAILED"));
        assertEquals("cancelled_externally", julesDispatchService.mapExternalStatus("CANCELLED"));
        assertEquals("running", julesDispatchService.mapExternalStatus("UNKNOWN"));
        assertEquals("running", julesDispatchService.mapExternalStatus(null));
    }

    @Test
    void failedPreconditionIsApiBlockedNotDailyLimit() {
        JulesApiClient.CreateSessionResult result = new JulesApiClient.CreateSessionResult(
                null,
                400,
                "{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"message\":\"Repository access is not ready\"}}"
        );

        assertFalse(result.dailyLimitOrQuota());
        assertTrue(result.apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void explicitQuotaErrorIsDailyLimit() {
        JulesApiClient.CreateSessionResult result = new JulesApiClient.CreateSessionResult(
                null,
                429,
                "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"daily quota exceeded\"}}"
        );

        assertTrue(result.dailyLimitOrQuota());
        assertFalse(result.apiPreconditionOrAuthorizationBlocked());
    }

    @Test
    void testDetectStuck() {
        // When
        julesDispatchService.detectStuck();

        // Then
        verify(claimService, times(1)).detectStuckSessions(60);
    }

    @Test
    void closesLoopWithoutCreatingWishlistWhenDialogueBudgetExceeded() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/abc");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-06");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Oversized QA task");

        List<com.eneik.production.models.persistence.JulesActivityResponseEntity> history = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            com.eneik.production.models.persistence.JulesActivityResponseEntity record =
                    new com.eneik.production.models.persistence.JulesActivityResponseEntity();
            record.setQuestion("Generated/local artifact detected in PR diff: .env");
            record.setResponse("Remove artifacts");
            record.setSent(true);
            history.add(record);
        }

        String activities = """
                {
                  "activities": [
                    {
                      "originator": "agent",
                      "name": "question-9",
                      "agentMessaged": {
                        "message": "Generated/local artifact detected in PR diff: playwright-report/. What should I do?"
                      }
                    }
                  ]
                }
                """;

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/abc", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/abc"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.readTree(activities));
        when(julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(eq(sessionId), anyString())).thenReturn(Optional.empty());
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(history);
        when(mlPredictionServiceClient.chat(anyString(), anyString())).thenReturn("Root cause: repeated artifact blocker\nKano classification: Must-Be\nCynefin domain: clear");
        // Pre-close deep-read classification (operator directive 2026-07-24: read everything Jules wrote
        // before closing) - this scenario is a genuinely repeated identical blocker across 8 rounds with no
        // new reasoning, so the classifier should read it as STUCK, not silently default there.
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("VERDICT: STUCK\nBLOCKER: n/a\nFIX: n/a");
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("loop_closed", result.getStatus());
        verify(julesApiClient, never()).sendMessage(eq("sessions/abc"), anyString(), eq("jules-key"));
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("dialog_limit_exceeded"));
        verify(wishlistRepository, never()).save(any(WishlistEntity.class));
    }

    @Test
    void skipsQuestionScanWithoutClosingLoopWhenActivitiesOverflow() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/overflow");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Oversized frontend task");

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/overflow", "jules-key")).thenReturn("RUNNING");
        // Overflows at every page size down to 1 - the true last-resort case (adaptive shrink-and-retry
        // exhausted every step: 20 -> 10 -> 5 -> 2 -> 1).
        when(julesApiClient.getSessionActivities(eq("sessions/overflow"), eq("jules-key"), anyInt(), isNull()))
                .thenReturn(objectMapper.createObjectNode()
                        .put("activitiesOverflow", true)
                        .put("maxBytes", 2_097_152));

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        // A large activity payload is not evidence the session is stuck - it just means the session has a
        // long history. The session must be left running so it can keep progressing toward a PR; genuinely
        // stuck/runaway sessions are still caught by the independent, time-based stuck_session_timeout and
        // active_session_age_limit circuit breakers.
        assertEquals("running", result.getStatus());
        verify(julesApiClient, never()).sendMessage(eq("sessions/overflow"), anyString(), eq("jules-key"));
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
        verify(wishlistRepository, never()).save(any(WishlistEntity.class));
    }

    /**
     * Regression coverage for the 2026-08-04 ghost-session incident (test-forty-first, task 1fbb3086): a
     * session logged a definitive sessionCompleted activity while /sessions/{id} kept reporting a
     * non-terminal state for over 24h - no PR ever appeared on GitHub, and the task stayed stuck 'claimed'
     * forever because nothing checked the activities feed's own terminal event type. This asserts the
     * activity's own sessionCompleted field is now enough on its own to close the loop, without needing the
     * silence-based classifyBeforeClosing/Davidson-veto machinery (no mlPredictionServiceClient stub is
     * provided at all - if the fix accidentally routed through that path, this test would NPE/fail loudly).
     */
    @Test
    void closesSessionAndBlocksTaskWhenActivityLogReportsCompletionButNoPrEverExisted() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/ghost");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Design Brief that never produced a PR");

        String activities = """
                {
                  "activities": [
                    {
                      "originator": "agent",
                      "name": "activity-completed",
                      "createTime": "%s",
                      "sessionCompleted": {}
                    }
                  ]
                }
                """.formatted(Instant.now().minus(2, ChronoUnit.HOURS));

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/ghost", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/ghost"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.readTree(activities));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/ghost")).thenReturn(Optional.empty());

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("loop_closed", result.getStatus());
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("session_completed_no_deliverable"));
        verify(mlPredictionServiceClient, never()).chatCritical(anyString(), anyString());
    }

    /**
     * Direct coverage for the exact race the operator flagged reviewing the fix above: a session that
     * finishes and pushes its PR in close succession must not be permanently blocked just because this
     * poll cycle's cursor happened to reach the sessionCompleted activity before GitHub's own PR listing
     * caught up. A terminal activity newer than the Davidson trust window must be left unacted-on, not
     * closed - regardless of whether GitHub currently shows a PR or not.
     */
    @Test
    void doesNotCloseOnAFreshSessionCompletedActivityEvenWithNoPrYetVisible() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/just-finished");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Design Brief that just finished seconds ago");

        String activities = """
                {
                  "activities": [
                    {
                      "originator": "agent",
                      "name": "activity-completed",
                      "createTime": "%s",
                      "sessionCompleted": {}
                    }
                  ]
                }
                """.formatted(Instant.now().minus(30, ChronoUnit.SECONDS));

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/just-finished", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/just-finished"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.readTree(activities));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/just-finished")).thenReturn(Optional.empty());

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("running", result.getStatus());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
    }

    @Test
    void reconcilesRealPrInsteadOfClosingWhenGitHubHasOneEvenThoughActivityLogReportsCompletion() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/late-pr");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Design Brief whose PR sync just lagged behind completion");

        String activities = """
                {
                  "activities": [
                    {
                      "originator": "agent",
                      "name": "activity-completed",
                      "createTime": "%s",
                      "sessionCompleted": {}
                    }
                  ]
                }
                """.formatted(Instant.now().minus(2, ChronoUnit.HOURS));

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/late-pr", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/late-pr"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.readTree(activities));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/late-pr"))
                .thenReturn(Optional.of(new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/7", 7, "Late PR", "jules-late-pr", "jules",
                        false, "main", false, Instant.now())));

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("pr_opened", result.getStatus());
        assertEquals("https://github.com/org/repo/pull/7", result.getPrUrl());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
    }

    /**
     * Regression coverage for the 2026-08-03 blind-cycle incident (test-forty-first): the old
     * unparameterized /activities call always returned Jules's own default (oldest-first) page and never
     * advanced, so a long-running session's recent activity was never actually scanned. This walks a
     * two-page session end to end and asserts the cursor lands on the last non-blank pageToken used (the
     * position to resume/re-check from next cycle), not blank - see answerAgentQuestions's javadoc for why
     * blank would incorrectly restart the whole walk from page 1.
     */
    @Test
    void walksForwardAcrossMultiplePagesInOneCycleAndPersistsTheTailCursor() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/paged");
        session.setStatus("running");
        session.setActivitiesPageCursor(null);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Multi-page QA task");

        String page1 = """
                {"activities": [{"originator": "agent", "name": "q1",
                    "agentMessaged": {"message": "Generated/local artifact detected in PR diff: .env. What should I do?"}}],
                 "nextPageToken": "tok1"}
                """;
        String page2 = """
                {"activities": [{"originator": "agent", "name": "q2",
                    "agentMessaged": {"message": "Generated/local artifact detected in PR diff: dist/. What should I do?"}}],
                 "nextPageToken": ""}
                """;

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/paged", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/paged"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.readTree(page1));
        when(julesApiClient.getSessionActivities(eq("sessions/paged"), eq("jules-key"), eq(20), eq("tok1")))
                .thenReturn(objectMapper.readTree(page2));
        when(julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(eq(sessionId), anyString())).thenReturn(Optional.empty());
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(julesApiClient.sendMessage(eq("sessions/paged"), anyString(), eq("jules-key"))).thenReturn(true);

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        verify(julesApiClient, times(2)).sendMessage(eq("sessions/paged"), anyString(), eq("jules-key"));
        verify(julesApiClient).getSessionActivities(eq("sessions/paged"), eq("jules-key"), eq(20), isNull());
        verify(julesApiClient).getSessionActivities(eq("sessions/paged"), eq("jules-key"), eq(20), eq("tok1"));
        assertEquals("tok1", result.getActivitiesPageCursor(),
                "cursor must land on the last non-blank pageToken used, not blank - blank would restart the whole walk from page 1 next cycle");
        assertEquals(0, result.getBlindCycleCount());
    }

    /**
     * Regression coverage: a session pre-seeded with a cursor from a previous cycle must resume from that
     * position, not restart from page 1 - the entire point of persisting the cursor.
     */
    @Test
    void resumesFromThePersistedCursorOnASubsequentCycle() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/resume");
        session.setStatus("running");
        session.setActivitiesPageCursor("tok1");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Resumed QA task");

        String page2 = """
                {"activities": [], "nextPageToken": ""}
                """;

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/resume", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/resume"), eq("jules-key"), eq(20), eq("tok1")))
                .thenReturn(objectMapper.readTree(page2));

        julesDispatchService.pollStatus(sessionId);

        verify(julesApiClient, never()).getSessionActivities(eq("sessions/resume"), eq("jules-key"), anyInt(), isNull());
        verify(julesApiClient).getSessionActivities(eq("sessions/resume"), eq("jules-key"), eq(20), eq("tok1"));
    }

    /**
     * Regression coverage: an individual activity can embed a large artifact (git diff, screenshot, bash
     * output) that overflows the byte cap even at a small page size, independent of session length - see
     * JulesApiClient.getSessionActivities's javadoc. Halving the page size before giving up must eventually
     * succeed rather than treating every overflow as unrecoverable.
     */
    @Test
    void shrinksPageSizeOnOverflowUntilAPageFits() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/shrink");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Screenshot-heavy QA task");

        String fittingPage = """
                {"activities": [{"originator": "agent", "name": "q1",
                    "agentMessaged": {"message": "Generated/local artifact detected in PR diff: .env. What should I do?"}}],
                 "nextPageToken": ""}
                """;

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/shrink", "jules-key")).thenReturn("RUNNING");
        when(julesApiClient.getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(20), isNull()))
                .thenReturn(objectMapper.createObjectNode().put("activitiesOverflow", true).put("maxBytes", 2_097_152));
        when(julesApiClient.getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(10), isNull()))
                .thenReturn(objectMapper.createObjectNode().put("activitiesOverflow", true).put("maxBytes", 2_097_152));
        when(julesApiClient.getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(5), isNull()))
                .thenReturn(objectMapper.readTree(fittingPage));
        when(julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(eq(sessionId), anyString())).thenReturn(Optional.empty());
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(julesApiClient.sendMessage(eq("sessions/shrink"), anyString(), eq("jules-key"))).thenReturn(true);

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        verify(julesApiClient).getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(20), isNull());
        verify(julesApiClient).getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(10), isNull());
        verify(julesApiClient).getSessionActivities(eq("sessions/shrink"), eq("jules-key"), eq(5), isNull());
        verify(julesApiClient, times(1)).sendMessage(eq("sessions/shrink"), anyString(), eq("jules-key"));
        assertEquals(0, result.getBlindCycleCount(), "a page that eventually fit is a successful cycle, not a blind one");
    }

    /**
     * Regression coverage: a session with a large activity backlog (e.g. the first scan of an
     * already-long-running session after this feature ships) must not turn into one unbounded synchronous
     * walk - it should stop after maxActivityPagesPerCycle pages and resume from the saved cursor next
     * cycle.
     */
    @Test
    void stopsAtThePageCapAndResumesNextCycleInsteadOfWalkingForever() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/deep-history");
        session.setStatus("running");

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-03");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Deep-history QA task");

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/deep-history", "jules-key")).thenReturn("RUNNING");
        // Every page has an empty activities array and a genuine (non-blank) nextPageToken equal to its own
        // input pageToken bumped by one - an endless chain that would run forever without the page cap.
        when(julesApiClient.getSessionActivities(eq("sessions/deep-history"), eq("jules-key"), eq(20), any()))
                .thenAnswer(invocation -> {
                    String inputToken = invocation.getArgument(3);
                    int next = inputToken == null ? 1 : Integer.parseInt(inputToken) + 1;
                    return objectMapper.createObjectNode()
                            .put("nextPageToken", String.valueOf(next))
                            .set("activities", objectMapper.createArrayNode());
                });
        ReflectionTestUtils.setField(julesDispatchService, "maxActivityPagesPerCycle", 3);

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        verify(julesApiClient, times(3)).getSessionActivities(eq("sessions/deep-history"), eq("jules-key"), eq(20), any());
        assertEquals("3", result.getActivitiesPageCursor(),
                "must stop at the page cap and persist wherever it got to, resuming from there next cycle rather than looping forever");
    }

    @Test
    void dispatchSendsBothTheCompactGuideAndTheFullRoleCharter() {
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-11");
        role.setDescription("Frontend Engineer");
        role.setRulesPath("BARCAN-TAG-11_CLIENT-PERCEPTION.md");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setTitle("UI Slice");
        task.setDescription("Implement one dashboard UI slice.");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(julesApiClient.createSessionDetailed(eq("prefix/repo"), contains("Implement one dashboard UI slice."), anyString(), isNull(), eq("UI Slice"), eq("main")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleCapabilityLoader.loadRules("BARCAN-TAG-11")).thenReturn(null);
        when(roleCapabilityLoader.loadRawCharter("BARCAN-TAG-11"))
                .thenReturn("# BARCAN-TAG-11 · CLIENT-PERCEPTION\n## ФИЛОСОФСКИЙ ФУНДАМЕНТ\n| 1 | **Патриция Черчланд** | ... |\n## КРИТЕРИИ ОТКАЗА (REFUSAL CRITERIA)\nsome refusal criteria text");

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());
        // Jules takes on the role - the compact guide alone is not enough, the full charter (including
        // the philosophy table that gives this role its distinct worldview) must reach the prompt too.
        verify(julesApiClient).createSessionDetailed(eq("prefix/repo"), contains("Task Title: UI Slice"), argThat(context ->
                context.contains("## Compact Role Guide")
                        && context.contains("Use English only")
                        && context.contains("## Role Charter")
                        && context.contains("ФИЛОСОФСКИЙ ФУНДАМЕНТ")
                        && context.contains("Патриция Черчланд")
                        && context.contains("REFUSAL CRITERIA")
        ), isNull(), eq("UI Slice"), eq("main"));
    }

    @Test
    void dispatchUsesActualProjectRepositoryOwnerForJulesSource() {
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("test-fortieth");
        project.setRepositoryUrl("https://github.com/eneikdru/test-fortieth");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-09");
        role.setDescription("Wishlist Compiler");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setTitle("Compile Wishlist");
        task.setDescription("Compile the client wishlist into atomic work.");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(julesApiClient.createSessionDetailed(anyString(), anyString(), anyString(), isNull(), eq("Compile Wishlist"), eq("main")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());
        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        verify(julesApiClient).createSessionDetailed(sourceCaptor.capture(), anyString(), anyString(), isNull(), eq("Compile Wishlist"), eq("main"));
        assertEquals("sources/github/eneikdru/test-fortieth", sourceCaptor.getValue());
    }

    @Test
    void successfulDispatchUnDismissesTheTasksFeatureInCaseAnEarlierCleanupWronglyDismissedIt() {
        // Live incident, 2026-08-07 (test-forty-third): the epic-cleanup cron dismissed real epics during a
        // project-wide dispatch freeze, then real work went on to complete under them once dispatch resumed
        // - permanently invisible everywhere that filters on dismissedAt. A task actually dispatching under
        // a feature is itself proof any dismissal no longer holds, so every successful dispatch must call
        // the self-healing un-dismiss - see ClientDeliverableReadinessService.unDismissFeatureIfNeeded.
        UUID taskId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("test-fortieth");
        project.setRepositoryUrl("https://github.com/eneikdru/test-fortieth");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setDescription("Backend Engineer");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setTitle("API Slice");
        task.setDescription("Implement the smallest backend change for this slice.");
        task.setFeatureId(featureId);

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(julesApiClient.createSessionDetailed(anyString(), anyString(), anyString(), isNull(), eq("API Slice"), eq("main")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());
        verify(readinessService).unDismissFeatureIfNeeded(featureId);
    }

    @Test
    void skippingAnAlreadyDispatchedTaskDoesNotTouchFeatureDismissal() {
        // The early "already dispatched, skipping duplicate" path never reaches real dispatch - it must not
        // call the self-healing un-dismiss, since no new work is actually starting.
        UUID taskId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setFeatureId(featureId);

        JulesSessionEntity existing = new JulesSessionEntity();
        existing.setTaskId(taskId);
        existing.setExternalSessionId("sessions/already-running");
        existing.setStatus("running");
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(existing));

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());
        assertEquals("already dispatched, skipping duplicate", result.reason());
        verify(readinessService, never()).unDismissFeatureIfNeeded(any());
    }

    @Test
    void adHocDispatchUsesActualProjectRepositoryOwnerForJulesSource() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("test-fortieth");
        project.setRepositoryUrl("https://github.com/eneikdru/test-fortieth");

        AccountEntity account = new AccountEntity();
        account.setApiKey("jules-key");

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(julesApiClient.createSessionDetailed(anyString(), eq("Resolve the branch issue."), eq(""), eq("jules-key"), eq("Branch Fix"), eq("repair-branch")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));

        julesDispatchService.dispatchAdHocSessionToBranch(project, "repair-branch", "Resolve the branch issue.", "Branch Fix");

        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        verify(julesApiClient).createSessionDetailed(sourceCaptor.capture(), eq("Resolve the branch issue."), eq(""), eq("jules-key"), eq("Branch Fix"), eq("repair-branch"));
        assertEquals("sources/github/eneikdru/test-fortieth", sourceCaptor.getValue());
    }

    @Test
    void dispatchInjectsRetrievedSystemKnowledgeIntoJulesRoleContext() {
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setDescription("Backend Engineer");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setTitle("API Slice");
        task.setDescription("Implement contract-first account ingestion without merge conflicts.");

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(julesApiClient.createSessionDetailed(eq("prefix/repo"), anyString(), anyString(), isNull(), eq("API Slice"), eq("main")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleCapabilityLoader.loadRules("BARCAN-TAG-02")).thenReturn(null);
        // Tarski demarcation L0 / L_factory (2026-08-27): a task whose targetContext is PRODUCT_CODEBASE is
        // a product worker, and its retrieval must go through buildProductWorkerContextBlock, which filters
        // the factory's own metalanguage out of the corpus before the agent ever sees it. Only
        // factory-scoped tasks take the unfiltered buildContextBlock path. This task sets no targetContext,
        // which JulesDispatchService.appendRetrievedSystemKnowledge treats as PRODUCT_CODEBASE - so the
        // product-worker overload is the one under test here.
        when(geminiContextService.buildProductWorkerContextBlock(any(RoleEntity.class), anyString())).thenReturn("""
                RELEVANT SYSTEM KNOWLEDGE (retrieved from the indexed knowledge base):
                - [01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md] Single Writer Ownership and Contract-First Parallelism.
                """);

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiContextService).buildProductWorkerContextBlock(eq(role), queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("roleTag=BARCAN-TAG-02"));
        assertTrue(queryCaptor.getValue().contains("contract-first account ingestion"));
        // The unfiltered corpus must never be consulted for a product task - that is the demarcation.
        verify(geminiContextService, never()).buildContextBlock(anyString());

        verify(julesApiClient).createSessionDetailed(eq("prefix/repo"), anyString(), argThat(context ->
                context.contains("## Retrieved System Knowledge")
                        && context.contains("pattern memory from the indexed Eneik system corpus")
                        && context.contains("never overrides the task description")
                        && context.contains("Single Writer Ownership")
                        && context.contains("Contract-First Parallelism")
        ), isNull(), eq("API Slice"), eq("main"));
    }

    @Test
    void threadForADifferentFeatureIsNeverUsedAsStartingBranch() {
        UUID taskId = UUID.randomUUID();
        UUID featureA = UUID.randomUUID();
        UUID featureB = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setDescription("Backend Engineer");
        role.setRulesPath(null);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setTitle("API Slice");
        task.setDescription("Implement feature B's endpoint.");
        task.setFeatureId(featureB);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        // A thread exists on the same account, but for a DIFFERENT feature (featureA, not featureB) -
        // if the lookup ever ignored featureId, this thread's branch would leak into featureB's
        // dispatch. It must not: the featureB lookup below is stubbed separately and returns nothing,
        // so startingBranch must fall back to "main".
        com.eneik.production.models.persistence.FeatureThreadEntity featureAThread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        featureAThread.setBranchName("feature-a-branch");
        featureAThread.setAccountId(accountId);
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureA))
                .thenReturn(Optional.of(featureAThread));
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureB))
                .thenReturn(Optional.empty());

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(julesApiClient.createSessionDetailed(eq("prefix/repo"), anyString(), anyString(), eq("jules-key"), eq("API Slice"), eq("main")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleCapabilityLoader.loadRules("BARCAN-TAG-02")).thenReturn(null);

        JulesDispatchResult result = julesDispatchService.dispatch(task, accountId);

        assertTrue(result.dispatched());
        verify(julesApiClient).createSessionDetailed(eq("prefix/repo"), anyString(), anyString(), eq("jules-key"), eq("API Slice"), eq("main"));
    }

    @Test
    void aDifferentRoleOnTheSameFeatureDoesContinueTheThread() {
        // The core correction: a feature's thread is NOT role-scoped. Backend shipped code on this
        // feature under BARCAN-TAG-02; now a frontend (BARCAN-TAG-11) task for the SAME feature, on the
        // SAME account, should pick up that same branch rather than starting fresh from main.
        UUID taskId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("repo");

        RoleEntity frontendRole = new RoleEntity();
        frontendRole.setTag("BARCAN-TAG-11");
        frontendRole.setDescription("Frontend Engineer");
        frontendRole.setRulesPath(null);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(frontendRole);
        task.setTitle("UI Slice");
        task.setDescription("Wire the frontend to the endpoint backend just shipped.");
        task.setFeatureId(featureId);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        thread.setBranchName("feature-shared-branch");
        thread.setAccountId(accountId);
        thread.setLastRoleTag("BARCAN-TAG-02");
        thread.setSummary("Backend endpoint implemented.");
        when(featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId))
                .thenReturn(Optional.of(thread));

        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(julesApiClient.createSessionDetailed(eq("prefix/repo"), anyString(), anyString(), eq("jules-key"), eq("UI Slice"), eq("feature-shared-branch")))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/new", 200, ""));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleCapabilityLoader.loadRules("BARCAN-TAG-11")).thenReturn(null);

        JulesDispatchResult result = julesDispatchService.dispatch(task, accountId);

        assertTrue(result.dispatched());
        verify(julesApiClient).createSessionDetailed(eq("prefix/repo"), anyString(),
                contains("Backend endpoint implemented."), eq("jules-key"), eq("UI Slice"), eq("feature-shared-branch"));
    }

    @Test
    void executeCodeReviewAlwaysRoutesToJulesFallbackNeverCallsGemini() {
        // Direct regression test for the 2026-07-25 emergency cost incident ("она за несколько часов
        // потратила месячный бюджет, при этом по проекту ничего не сдвинулось") - Gemini PR review is
        // permanently disabled; every PR, approved-looking or not, queues into the Jules-reviewer fallback
        // path (already proven for Gemini-outage recovery, now the only path) instead of paying for a
        // pro-tier diff review on every single resubmission.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-11");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Implement a personal dashboard UI slice.");
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/review-loop");
        session.setPrUrl("https://github.com/org/repo/pull/12");
        session.setStatus("pr_opened");

        List<JulesDispatchService.PendingFallbackReview> fallbackCollector = new java.util.ArrayList<>();
        julesDispatchService.executeCodeReview(task, session, "https://github.com/org/repo/pull/12", List.of(), fallbackCollector);

        assertEquals(1, fallbackCollector.size());
        assertEquals(taskId, fallbackCollector.get(0).task().getId());
        assertEquals("https://github.com/org/repo/pull/12", fallbackCollector.get(0).prUrl());
        verifyNoInteractions(mlPredictionServiceClient);
        // Nothing was written yet - the actual approve/block decision only lands once the Jules reviewer
        // session responds, via applyReviewVerdictToTask (covered by its own dedicated tests).
        assertEquals(TaskStatus.claimed, task.getStatus());
    }

    @Test
    void forceUnblockTrustsSilentSessionForFullDavidsonWindow() {
        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(UUID.randomUUID());
        session.setExternalSessionId("sessions/charitable-silence");
        session.setStatus("running");
        session.setBlindCycleCount(0);
        session.setLastProgressAt(Instant.now().minus(45, ChronoUnit.MINUTES));

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("running", session.getStatus());
        assertEquals(0, session.getForcedUnblockAttempts());
        verifyNoInteractions(taskRepository);
        verify(julesApiClient, never()).sendMessage(anyString(), anyString());
        verify(julesApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void forceUnblockSendsDeterministicMessageAfterDavidsonWindow() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/blind");
        session.setStatus("running");
        session.setBlindCycleCount(6);
        session.setForcedUnblockAttempts(0);
        session.setLastProgressAt(Instant.now().minus(65, ChronoUnit.MINUTES));

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(julesApiClient.sendMessage(eq("sessions/blind"), anyString(), eq("jules-key"))).thenReturn(true);

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals(1, session.getForcedUnblockAttempts());
        assertEquals(0, session.getBlindCycleCount());
        verify(julesApiClient, timeout(2000)).sendMessage(eq("sessions/blind"), contains("forcibly decide for yourself"), eq("jules-key"));
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
    }

    @Test
    void forceUnblockEscalatesToLoopClosureAfterMaxAttempts() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/blind-exhausted");
        session.setStatus("running");
        session.setBlindCycleCount(7);
        session.setForcedUnblockAttempts(2);
        session.setLastProgressAt(Instant.now().minus(130, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Task stuck behind an oversized activity log");

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Pre-close deep-read classification: empty activity history and a genuinely exhausted blind-cycle
        // overflow is a real STUCK signal, not a technical AI-unavailable case.
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString())).thenReturn("VERDICT: STUCK\nBLOCKER: n/a\nFIX: n/a");

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("loop_closed", session.getStatus());
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("blind_overflow_unblock_exhausted"));
        verify(julesApiClient, never()).sendMessage(eq("sessions/blind-exhausted"), anyString(), anyString());
    }

    @Test
    void forceUnblockDoesNotCloseAfterNudgesBeforeLongCloseWindow() {
        UUID taskId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/still-charitable");
        session.setStatus("running");
        session.setBlindCycleCount(7);
        session.setForcedUnblockAttempts(2);
        session.setLastProgressAt(Instant.now().minus(90, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("running", session.getStatus());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
        verify(julesApiClient, never()).sendMessage(anyString(), anyString());
        verify(julesApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    // --- Testimony-vs-evidence Phase 1: branch-fallback evidence (2026-07-25) -----------------------------

    @Test
    void forceUnblockOpensRecoveryPrWhenBranchHasRealEvidenceButNoOpenPrYet() {
        // Direct regression test for the PR#72/PR#77 incident shape: a session finishes real work and
        // pushes it to its own branch, but never opens a PR for it. No open PR exists, so the PR-based
        // evidence check alone would see nothing and the session would be wrongly treated as stalled.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant sessionCreatedAt = Instant.now().minus(90, ChronoUnit.MINUTES);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/recoverable-1234");
        session.setStatus("running");
        session.setBlindCycleCount(6);
        session.setCreatedAt(sessionCreatedAt);
        session.setLastProgressAt(Instant.now().minus(65, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/recoverable-1234"))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.findBranchBySession(project, "sessions/recoverable-1234"))
                .thenReturn(Optional.of("jules-recoverable-1234-abcd"));
        when(gitHubPullRequestService.latestCommitTime(project, "jules-recoverable-1234-abcd"))
                .thenReturn(Optional.of(sessionCreatedAt.plus(10, ChronoUnit.MINUTES)));
        when(gitHubPullRequestService.createPullRequest(eq(project), eq("jules-recoverable-1234-abcd"), eq("main"), anyString(), anyString()))
                .thenReturn(Optional.of(new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/99", 99, "Auto-recovered", "jules-recoverable-1234-abcd", "eneikdru", false, "main", false, Instant.now())));

        julesDispatchService.forceUnblockOverflowedSessions();

        verify(gitHubPullRequestService).createPullRequest(eq(project), eq("jules-recoverable-1234-abcd"), eq("main"), anyString(), anyString());
        assertEquals("running", session.getStatus());
        assertEquals(0, session.getForcedUnblockAttempts());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
    }

    @Test
    void forceUnblockDoesNotOpenPrForAStaleBranchThatPredatesTheSession() {
        // A branch matching the session token exists, but its latest commit is BEFORE the session was
        // created - an untouched fork-point branch, not real work from THIS session. Must not be treated
        // as evidence, and must never trigger a PR open for noise.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant sessionCreatedAt = Instant.now().minus(90, ChronoUnit.MINUTES);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/stale-branch-5678");
        session.setStatus("running");
        session.setBlindCycleCount(6);
        session.setCreatedAt(sessionCreatedAt);
        session.setLastProgressAt(Instant.now().minus(65, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(any())).thenReturn(Optional.empty());
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/stale-branch-5678"))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.findBranchBySession(project, "sessions/stale-branch-5678"))
                .thenReturn(Optional.of("jules-stale-branch-5678-old"));
        when(gitHubPullRequestService.latestCommitTime(project, "jules-stale-branch-5678-old"))
                .thenReturn(Optional.of(sessionCreatedAt.minus(30, ChronoUnit.MINUTES)));

        julesDispatchService.forceUnblockOverflowedSessions();

        verify(gitHubPullRequestService, never()).createPullRequest(any(), anyString(), anyString(), anyString(), anyString());
    }

    // --- Plan L-2: the UNAVAILABLE deferral is bounded (2026-08-21) --------------------------------------
    //
    // No stub for mlPredictionServiceClient.chatCritical is provided in either test below: an unstubbed
    // mock returns null, isUsableAiAnswer(null) is false, and classifyBeforeClosing therefore returns
    // UNAVAILABLE - exactly the shape of a reviewer that is not answering. That is the condition under
    // test, so it is produced the same way production reaches it rather than by forcing an enum in.

    @Test
    void deferralIsPreservedWhileTheClassifierHasOnlyBeenUnavailableForAFewCycles() {
        // An unreachable reviewer says nothing about the session, so the charitable reading must hold -
        // this is the half of the rule that was already right and must not be broken by bounding it.
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/unavailable-early");
        session.setStatus("revising");
        session.setBlindCycleCount(0);
        session.setForcedUnblockAttempts(2);
        session.setCreatedAt(Instant.now().minus(400, ChronoUnit.MINUTES));
        session.setLastProgressAt(Instant.now().minus(300, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(gitHubPullRequestService.findOpenPullRequestBySession(eq(project), anyString()))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.findBranchBySession(eq(project), anyString()))
                .thenReturn(Optional.empty());

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("revising", session.getStatus());
        assertEquals(1, session.getBlindCycleCount());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), anyString());
    }

    @Test
    void deferralEndsOnceTheClassifierHasBeenUnavailableForTheWholeBlindCycleThreshold() {
        // The other half: unbounded, "retry next cycle" is an absorbing state once the reviewer is
        // permanently gone, and the task defers forever. Measured live 2026-08-20/21 - one task sat
        // `claimed` for 24h with SYSTEM_STALLED firing throughout, because the adjudicator's provider had
        // been switched off and nothing counted how long it had been silent.
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/unavailable-exhausted");
        session.setStatus("running");
        session.setBlindCycleCount(5); // already at jules.forced-unblock-blind-cycle-threshold
        session.setForcedUnblockAttempts(2);
        session.setCreatedAt(Instant.now().minus(400, ChronoUnit.MINUTES));
        session.setLastProgressAt(Instant.now().minus(300, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(gitHubPullRequestService.findOpenPullRequestBySession(eq(project), anyString()))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.findBranchBySession(eq(project), anyString()))
                .thenReturn(Optional.empty());

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("loop_closed", session.getStatus());
        verify(claimService).closeTaskAsBlocked(eq(taskId), anyString());
        // The closure must say WHY it fired, so a later reader is not misled into thinking the session's
        // own writing was read and found wanting - it was not read at all.
        assertTrue(session.getClosureReason().contains("classifier unavailable for"),
                "closure reason must record that this closed on the reviewer's absence, not on evidence "
                        + "about the session; was: " + session.getClosureReason());
    }

    // --- O-17 (2026-08-21): the Davidson veto is generous, but it is not infinite ------------------------
    //
    // Driven straight at closeLoopAndCreateFollowUps, which is where the reset actually lives: the code
    // reaches it only AFTER the forced-unblock budget is spent and it has decided to close. The classifier
    // then says PROGRESSING, the budget is restored, and nothing closes - which is exactly how the live
    // counter ran 1,2,1,2 for 373 minutes. An earlier version of these tests drove the whole
    // forceUnblockOverflowedSessions path instead and never reached this branch at all; the paired
    // below-the-ceiling test is what exposed that, which is the reason both directions are pinned.

    private Object[] davidsonProgressingScenario(int sessionAgeMinutes) {
        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/davidson-veto");
        session.setStatus("revising");
        session.setForcedUnblockAttempts(2); // budget already spent - this is why it is closing
        session.setBlindCycleCount(0);
        session.setCreatedAt(Instant.now().minus(sessionAgeMinutes, ChronoUnit.MINUTES));
        session.setLastProgressAt(Instant.now().minus(300, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(i -> i.getArgument(0));
        // The session's own writing reads as real ongoing reasoning - the case the veto exists for.
        when(mlPredictionServiceClient.chatCritical(anyString(), anyString()))
                .thenReturn("VERDICT: PROGRESSING\nBLOCKER: n/a\nFIX: n/a");
        return new Object[]{session, task};
    }

    @Test
    void pastItsCeilingTheDavidsonVetoStopsRestoringTheForcedUnblockBudget() {
        // Measured live 2026-08-21: task 47aa70cb held IMPLEMENTING for 373 minutes against a 90-minute SLA
        // without ever producing a PR, because this branch handed its budget back every time. That budget is
        // the ONLY thing that closes a stale session and frees the WIP slot, and Flow Core correctly refuses
        // to dispatch anything new while the slot is held - so one session blocked the whole project.
        Object[] scenario = davidsonProgressingScenario(400); // > jules.davidson-veto-ceiling-minutes
        JulesSessionEntity session = (JulesSessionEntity) scenario[0];

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                julesDispatchService, "closeLoopAndCreateFollowUps",
                session, scenario[1], "why are you quiet?", List.of(), "stuck_session_timeout");

        assertEquals(2, session.getForcedUnblockAttempts(),
                "past the ceiling the budget must NOT be restored, or mu never decreases and the circuit "
                        + "breaker can never fire");
    }

    @Test
    void belowItsCeilingTheDavidsonVetoStillRestoresTheBudget() {
        // The charity is deliberate and must survive. The ceiling bounds the veto; it does not remove it,
        // and a later edit that deletes the charity instead of bounding it must fail here.
        Object[] scenario = davidsonProgressingScenario(30); // well under the ceiling
        JulesSessionEntity session = (JulesSessionEntity) scenario[0];

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                julesDispatchService, "closeLoopAndCreateFollowUps",
                session, scenario[1], "why are you quiet?", List.of(), "stuck_session_timeout");

        assertEquals(0, session.getForcedUnblockAttempts(),
                "below the ceiling the veto must still reset the budget - removing the charity was never "
                        + "the intent");
    }

    // --- O-16 (2026-08-21): a terminal compiler task must not strand the brief it compiled ---------------

    private JulesSessionEntity terminalCompilerScenario(ProjectEntity project, TaskEntity compilerTask,
                                                         WishlistEntity wishlist) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(compilerTask.getId());
        session.setExternalSessionId("sessions/compiler-terminal");
        session.setStatus("running");
        session.setBlindCycleCount(5);
        session.setForcedUnblockAttempts(2);
        session.setCreatedAt(Instant.now().minus(400, ChronoUnit.MINUTES));
        session.setLastProgressAt(Instant.now().minus(300, ChronoUnit.MINUTES));

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(compilerTask.getId())).thenReturn(Optional.of(compilerTask));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(wishlistRepository.findById(wishlist.getId())).thenReturn(Optional.of(wishlist));
        when(projectFlowService.isWishlistCompilerTask(compilerTask)).thenReturn(true);
        when(projectFlowService.compilerPlanPath(compilerTask)).thenReturn("docs/plan.json");
        return session;
    }

    private static TaskEntity terminalCompilerTask(ProjectEntity project, UUID wishlistId,
                                                    com.fasterxml.jackson.databind.ObjectMapper mapper) {
        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(UUID.randomUUID());
        compilerTask.setProject(project);
        compilerTask.setStatus(TaskStatus.done); // terminal - this is the state the defect happens in
        var payload = mapper.createObjectNode();
        payload.putArray("compilesWishlistIds").add(wishlistId.toString());
        compilerTask.setPayload(payload);
        return compilerTask;
    }

    private static ProjectEntity activeProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.active);
        return project;
    }

    @Test
    void aTerminalCompilerTaskRebuildsItsStrandedBriefFromThePlanAlreadyMergedOnMain() {
        // Measured live 2026-08-21: AutoMergeService's poka-yoke drove the compiler task to `done` from the
        // merged PR before the session was ever seen in pr_opened, so completeWishlistCompilation - the one
        // place a brief becomes converted_to_task or honestly dismissed - never ran. The compile really
        // happened; its plan is on main; nothing read it.
        ProjectEntity project = activeProject();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.compiling); // stranded exactly as measured

        TaskEntity compilerTask = terminalCompilerTask(project, wishlist.getId(), objectMapper);
        terminalCompilerScenario(project, compilerTask, wishlist);

        when(gitHubPullRequestService.fetchFileContent(project, "main", "docs/plan.json"))
                .thenReturn(Optional.of("{\"epics\":[]}"));
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics = List.of(
                new com.eneik.production.services.MLPredictionServiceClient.EpicPlan(
                        null, "Make the product start", "so it answers", "must_be", "clear",
                        "launchability", "product_not_launchable", 0, List.of()));
        when(projectFlowService.parseCompilerPlanContent(anyString(), eq(project))).thenReturn(epics);

        julesDispatchService.forceUnblockOverflowedSessions();

        // The batch is this task's OWN ids, in payload order: epicPlan.sourceIndex() is positional, so a
        // batch collected any other way would attach these epics to a different brief.
        verify(projectFlowService).buildTaskGraphFromSlices(eq(project), eq(List.of(wishlist)), eq(epics));
    }

    @Test
    void aTerminalCompilerTaskThatStrandedNothingIsNotTouched() {
        // The repair must be restrictive: a brief the normal completion path already resolved must not be
        // re-decomposed, and no GitHub call should be spent looking for a plan nobody needs.
        ProjectEntity project = activeProject();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(project.getId());
        wishlist.setStatus(WishlistStatus.converted_to_task); // already resolved normally

        TaskEntity compilerTask = terminalCompilerTask(project, wishlist.getId(), objectMapper);
        terminalCompilerScenario(project, compilerTask, wishlist);

        julesDispatchService.forceUnblockOverflowedSessions();

        verify(gitHubPullRequestService, never()).fetchFileContent(any(), anyString(), anyString());
        verify(projectFlowService, never()).buildTaskGraphFromSlices(any(), any(), any());
    }

    @Test
    void forceUnblockCompletesTaskWhenPrAlreadyMergedInsteadOfRetryingRecoveryPr() {
        // Regression test for the test-fortieth/task 51ab7e20 incident (2026-07-30): Jules opened a real
        // PR and it was merged through the normal pipeline, but the session's local status never made the
        // running -> pr_opened jump (only caught if a poll lands exactly on Jules reporting SUCCEEDED).
        // Every hourly stall check afterward found no OPEN PR, assumed none was ever opened, and retried a
        // doomed "open a new PR" call against a branch already fully merged into main (HTTP 422 "No commits
        // between main and branch"), forever, instead of completing the task. Must find the merged PR and
        // route to real completion instead of retrying that doomed open.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        Instant sessionCreatedAt = Instant.now().minus(4, ChronoUnit.HOURS);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);
        var compilerTaskPayload = objectMapper.createObjectNode();
        compilerTaskPayload.putArray("compilesWishlistIds").add(wishlistId.toString());
        compilerTask.setPayload(compilerTaskPayload);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/already-merged-9999");
        session.setStatus("running");
        session.setBlindCycleCount(6);
        session.setCreatedAt(sessionCreatedAt);
        session.setLastProgressAt(Instant.now().minus(65, ChronoUnit.MINUTES));

        com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest mergedPr =
                new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/1", 1, "Decompose brief",
                        "feature/decompose-brief-already-merged-9999", "eneikdru", true, "main", true, Instant.now());

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(compilerTask));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectFlowService.isWishlistCompilerTask(compilerTask)).thenReturn(true);
        // Wishlist no longer exists by the time this evidence check runs - the simplest completeWishlistCompilation
        // exit path, sufficient to prove real completion logic ran rather than just a cosmetic status flip.
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty());
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/already-merged-9999"))
                .thenReturn(Optional.empty());
        when(gitHubPullRequestService.findMergedPullRequestBySession(project, "sessions/already-merged-9999"))
                .thenReturn(Optional.of(mergedPr));

        julesDispatchService.forceUnblockOverflowedSessions();

        verify(gitHubPullRequestService, never()).createPullRequest(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("pr_opened", session.getStatus());
        assertEquals("https://github.com/org/repo/pull/1", session.getPrUrl());
    }

    // --- GitHub budget guard: skip non-active projects in the maintenance sweeps (2026-07-30) --------------

    @Test
    void forceUnblockSkipsAStalledSessionInAFrozenProjectWithoutCallingGitHub() {
        // Live-measured regression (2026-07-30): 45 of 82 GitHub calls in a 20-minute window went to
        // frozen/accepted projects with leftover non-terminal sessions, competing with the one genuinely
        // active project for the same shared rate-limit budget. This is the highest-frequency sweep (every
        // minute) and the most likely single biggest source of that waste.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant sessionCreatedAt = Instant.now().minus(90, ChronoUnit.MINUTES);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.frozen);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/frozen-project-leftover");
        session.setStatus("running");
        session.setBlindCycleCount(6);
        session.setCreatedAt(sessionCreatedAt);
        session.setLastProgressAt(Instant.now().minus(65, ChronoUnit.MINUTES));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        julesDispatchService.forceUnblockOverflowedSessions();

        verifyNoInteractions(gitHubPullRequestService);
        assertEquals("running", session.getStatus());
        verify(julesSessionRepository, never()).save(any());
    }

    @Test
    void reconcileStrandedPrOpenedWorkflowsSkipsAnAcceptedProjectWithoutTouchingTheTask() {
        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.accepted);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/repo/pull/50");
        session.setStatus("pr_opened");

        when(julesSessionRepository.findByStatus("pr_opened")).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertEquals(0, julesDispatchService.reconcileStrandedPrOpenedWorkflows());
        verify(claimService, never()).complete(any());
        verify(taskRepository, never()).save(any());
    }

    // --- Testimony-vs-evidence Phase 2: periodic GitHub-truth reconciliation (2026-07-25) ------------------

    private com.eneik.production.services.settings.SystemSettingsService settingsServiceMock() {
        return (com.eneik.production.services.settings.SystemSettingsService)
                org.springframework.test.util.ReflectionTestUtils.getField(julesDispatchService, "settingsService");
    }

    @Test
    void reconciliationSweepDoesNothingWhenFeatureFlagIsOff() {
        // Mockito default for an unstubbed boolean-returning method is false - this is the "flag off" case
        // without any explicit stubbing, proving the sweep is genuinely opt-in.
        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verifyNoInteractions(taskRepository);
    }

    @Test
    void reconciliationSkipsAnAcceptedProjectsTaskWithoutCallingGitHub() {
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.accepted);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(false);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verifyNoInteractions(gitHubPullRequestService);
    }

    @Test
    void reconciliationMarksTaskFailedWhenItsPrWasClosedWithoutMergeAndNoActiveClaimRemains() {
        // Direct regression test for the ca41509f incident: PR#78 was closed without merge (operator
        // decision made directly on GitHub), and the task had no active claim/session left to observe it.
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/orphaned-flyway-fix");
        session.setCreatedAt(Instant.now().minus(3, ChronoUnit.HOURS));

        var closedUnmergedPr = new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/78", 78, "Fix Flyway migration versioning",
                "jules-orphaned-flyway-fix-9999", "eneikdru", false, "main", true, Instant.now());
        var snapshot = new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(), List.of(closedUnmergedPr), "");

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(false);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(snapshot);
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.failed)).thenReturn(1);
        TaskEntity freshCopy = new TaskEntity();
        freshCopy.setId(taskId);
        freshCopy.setProject(project);
        freshCopy.setStatus(TaskStatus.failed);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(freshCopy));

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.failed);
        ArgumentCaptor<TaskEntity> savedCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(savedCaptor.capture());
        assertTrue(savedCaptor.getValue().getJulesDispatchStatus().contains("PR#78"));
        assertTrue(savedCaptor.getValue().getJulesDispatchStatus().contains("closed without merge"));
    }

    @Test
    void reconciliationNeverTouchesATaskWithAnActiveClaim() {
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verifyNoInteractions(gitHubPullRequestService);
        verify(taskRepository, never()).writeStatusUnlessTerminal(any(), any());
    }

    @Test
    void reconciliationLeavesATaskWithAnOpenPrAlone() {
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/still-open-1111");
        session.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));

        var openPr = new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/55", 55, "Real work in progress",
                "jules-still-open-1111-aaaa", "eneikdru", false, "main", false, Instant.now());
        var snapshot = new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(openPr), List.of(), "");

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(false);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(snapshot);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verify(taskRepository, never()).writeStatusUnlessTerminal(any(), any());
        verify(gitHubPullRequestService, never()).createPullRequest(any(), anyString(), anyString(), anyString(), anyString());
    }

    // --- Extension to `done` tasks (2026-07-25, live incident: the new dashboard widget first surfaced
    // done-but-unmerged tasks that turned out to be auxiliary/pending-closeout, not real bugs) -----------

    @Test
    void doneReconciliationSkipsAuxiliaryTasksEvenWhenNotReachedMain() {
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.done);

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of());
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(true);
        when(readinessService.reachedMain(task)).thenReturn(false);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verifyNoInteractions(gitHubPullRequestService);
        verify(julesSessionRepository, never()).findByTaskId(taskId);
    }

    @Test
    void doneReconciliationSkipsTasksThatAlreadyReachedMain() {
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.done);

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of());
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(readinessService.reachedMain(task)).thenReturn(true);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verifyNoInteractions(gitHubPullRequestService);
    }

    @Test
    void doneReconciliationNeverWritesAnyStatusEvenWithAConfirmedClosedUnmergedPr() {
        // The core invariant this sweep must never violate: `done` is CAS-protected
        // (TaskRepository.writeStatusUnlessTerminal refuses to overwrite it) - a done task with a closed-
        // unmerged PR is loud evidence worth logging, but this sweep must not attempt any write at all.
        var settings = settingsServiceMock();
        when(settings.effectiveBoolean("github_truth_reconciliation_enabled")).thenReturn(true);

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.done);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/done-but-closed-unmerged");
        session.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));

        var closedUnmergedPr = new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/99", 99, "Some work",
                "jules-done-but-closed-unmerged-1234", "eneikdru", false, "main", true, Instant.now());
        var snapshot = new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                true, "org", "repo", List.of(), List.of(closedUnmergedPr), "");

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of());
        when(taskRepository.findByStatus(TaskStatus.done)).thenReturn(List.of(task));
        when(readinessService.isAuxiliaryTask(task)).thenReturn(false);
        when(readinessService.reachedMain(task)).thenReturn(false);
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(snapshot);

        julesDispatchService.reconcileTaskStatusAgainstGitHubTruth();

        verify(taskRepository, never()).writeStatusUnlessTerminal(any(), any());
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(gitHubPullRequestService, never()).createPullRequest(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void closesActiveSessionForTerminalTaskWithoutRecoveryOrStatusMutation() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/already-done");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        julesDispatchService.closeSessionsForTerminalTasks();

        assertEquals("closed_terminal_task", session.getStatus());
        assertEquals(TaskStatus.done, task.getStatus());
        verify(claimService).releaseTerminalClaim(taskId);
        verify(julesApiClient, never()).sendMessage(anyString(), anyString(), anyString());
        verify(claimService, never()).closeTaskAsFailed(any(), anyString());
        verify(claimService, never()).closeTaskAsBlocked(any(), anyString());
    }

    /**
     * 2026-08-11 (live incident: worker 924b2c9f stayed batch-in-flight for 14+ hours after its carrier
     * session died via exactly this path - closeSessionForTerminalTask closed the session but nothing ever
     * told PersistentWorkerSessionService the carrier died, so isIdleAndFresh's isBatchInFlight() check
     * short-circuited forever). This is the root-cause fix: retire the worker at the exact moment its
     * carrier task is first recognized as terminal, for any persistent-worker purpose.
     */
    @Test
    void closingSessionForTerminalCarrierTaskAlsoRetiresItsPersistentWorker() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/carrier-died");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);

        var worker = new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectFlowService.isPersistentWorkerCarrierTask(task)).thenReturn(true);
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));

        julesDispatchService.closeSessionsForTerminalTasks();

        assertEquals("closed_terminal_task", session.getStatus());
        verify(persistentWorkerSessionService).retire(eq(worker), anyString());
    }

    /**
     * 2026-08-13 (live incident: test-forty-fourth) - retiring the worker row alone left its claimed
     * wishlists permanently stranded in `finalizing`, since releaseUnfinishedClaims only fires on the
     * SAME session's own completion path and registerFreshWorker only ever runs for `pending` wishlists.
     * This verifies the fix: any wishlist ids the dying worker's batch was holding get compare-and-swapped
     * back from finalizing to pending so the compiler can pick them up again under a fresh worker.
     */
    @Test
    void closingSessionForTerminalCarrierTaskReleasesWorkersClaimedWishlistsBackToPending() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/carrier-died-with-claim");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);

        var worker = new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectFlowService.isPersistentWorkerCarrierTask(task)).thenReturn(true);
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));
        when(persistentWorkerSessionService.peekCurrentBatch(worker)).thenReturn(List.of(wishlistId));
        when(wishlistRepository.compareAndSetStatus(wishlistId,
                com.eneik.production.models.persistence.WishlistStatus.finalizing,
                com.eneik.production.models.persistence.WishlistStatus.pending)).thenReturn(1);

        julesDispatchService.closeSessionsForTerminalTasks();

        assertEquals("closed_terminal_task", session.getStatus());
        verify(wishlistRepository).compareAndSetStatus(wishlistId,
                com.eneik.production.models.persistence.WishlistStatus.finalizing,
                com.eneik.production.models.persistence.WishlistStatus.pending);
        verify(persistentWorkerSessionService).retire(eq(worker), anyString());
    }

    @Test
    void closingSessionForTerminalNonCarrierTaskNeverTouchesPersistentWorkerService() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/ordinary-task");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectFlowService.isPersistentWorkerCarrierTask(task)).thenReturn(false);

        julesDispatchService.closeSessionsForTerminalTasks();

        assertEquals("closed_terminal_task", session.getStatus());
        verify(persistentWorkerSessionService, never()).findByCarrierTaskId(any());
        verify(persistentWorkerSessionService, never()).retire(any(), anyString());
    }

    @Test
    void closesStaleActiveSessionForBlockedTaskWithoutReopeningTask() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/already-blocked");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.blocked);

        when(julesSessionRepository.findByStatusIn(anyList())).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        julesDispatchService.closeSessionsForTerminalTasks();

        assertEquals("closed_terminal_task", session.getStatus());
        assertEquals(TaskStatus.blocked, task.getStatus());
        verify(julesApiClient, never()).getSessionStatus(anyString());
        verify(claimService, never()).closeTaskAsFailed(any(), anyString());
        verify(claimService, never()).closeTaskAsBlocked(any(), anyString());
    }

    @Test
    void pollStatusNeverCallsExternalApiForTerminalTask() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/terminal-before-poll");
        session.setStatus("running");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.failed);

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("closed_terminal_task", result.getStatus());
        verify(julesApiClient, never()).getSessionStatus(anyString());
        verify(julesApiClient, never()).getSessionStatus(anyString(), anyString());
        verify(claimService).releaseTerminalClaim(taskId);
    }

    @Test
    void pollStatusNeverResurrectsALocallyStuckSessionOnJulesOwnUnreliableSelfReport() {
        // Live incident, 2026-08-09 (test-forty-third, session 381fc207): ClaimService.detectStuckSessions
        // marked a session "stuck" (real evidence - lastProgressAt stale 60+ min), but the very next poll
        // saw Jules's raw API still self-report "RUNNING" (unchanged, no real evidence) and treated the mere
        // local label flip stuck->running as "genuine forward progress", resetting lastProgressAt to now().
        // That reset the 60-minute stuck-detection clock every cycle FOREVER, so the session never once
        // stayed "stuck" long enough for closeOverdueStuckSessions' 120-minute close threshold to ever see
        // it - a real 3+ hour project-wide stall (SYSTEM STALLED) traced directly to this.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Instant staleProgress = Instant.now().minus(90, ChronoUnit.MINUTES);
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setAccountId(accountId);
        session.setExternalSessionId("sessions/stuck-resurrection");
        session.setStatus("stuck");
        session.setLastProgressAt(staleProgress);

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(julesApiClient.getSessionStatus("sessions/stuck-resurrection", "jules-key")).thenReturn("RUNNING");

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("stuck", result.getStatus(),
                "Jules's own unreliable self-report of RUNNING must never overwrite a locally-confirmed stuck status");
        assertEquals(staleProgress, result.getLastProgressAt(),
                "lastProgressAt must NOT be reset just because the local label flipped - that was the exact mechanism that prevented the 120-minute close threshold from ever being reached");
    }

    @Test
    void nonChaoticPrOpenedDefersToPendingReviewInsteadOfReviewingInline() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/non-chaotic");
        session.setPrUrl("https://github.com/org/repo/pull/40");
        session.setStatus("pr_opened");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        julesDispatchService.handlePrOpenedWorkflow(session);

        assertEquals(com.eneik.production.models.persistence.TaskStatus.pending_review, task.getStatus());
        verifyNoInteractions(mlPredictionServiceClient);
    }

    // 2026-08-14 (bug-hunt sweep): the novel, risk-bearing logic of this fix - a genuinely concurrent
    // second invocation for the same session must do NO work at all, not even read the task, since the
    // whole point is closing the window where two callers both apply the same critique/merge-record work
    // before either one's completion write lands.
    @Test
    void handlePrOpenedWorkflowSkipsAllWorkWhenClaimIsAlreadyHeldByAConcurrentInvocation() {
        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(UUID.randomUUID());
        session.setStatus("pr_opened");

        when(julesSessionRepository.claimPrOpenedWorkflow(eq(sessionId), any(), any())).thenReturn(0);

        julesDispatchService.handlePrOpenedWorkflow(session);

        verifyNoInteractions(taskRepository);
        verify(julesSessionRepository, never()).releasePrOpenedWorkflowClaim(any());
    }

    // A genuine crash mid-processing must not permanently strand the session - reconcileStrandedPrOpenedWorkflows'
    // crash-recovery replay depends on the claim being released, not left set forever by a failed attempt.
    @Test
    void handlePrOpenedWorkflowReleasesClaimOnFailureSoRetryRemainsPossible() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setStatus("pr_opened");

        when(julesSessionRepository.claimPrOpenedWorkflow(eq(sessionId), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenThrow(new RuntimeException("simulated DB failure mid-processing"));

        assertThrows(RuntimeException.class, () -> julesDispatchService.handlePrOpenedWorkflow(session));

        verify(julesSessionRepository).releasePrOpenedWorkflowClaim(sessionId);
    }

    @Test
    void replaysClaimedPrOpenedWorkflowAfterMissedDurableEdge() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-08");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/missed-edge");
        session.setPrUrl("https://github.com/org/repo/pull/42");
        session.setStatus("pr_opened");

        when(julesSessionRepository.findByStatus("pr_opened")).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int replayed = julesDispatchService.reconcileStrandedPrOpenedWorkflows();

        assertEquals(1, replayed);
        assertEquals(TaskStatus.pending_review, task.getStatus());
        verify(claimService).complete(taskId);
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void prOpenedReplayIsIdempotentOnceTaskLeftClaimed() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.pending_review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/repo/pull/43");
        session.setStatus("pr_opened");

        when(julesSessionRepository.findByStatus("pr_opened")).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertEquals(0, julesDispatchService.reconcileStrandedPrOpenedWorkflows());
        verify(claimService, never()).complete(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void prOpenedReplaySkipsIdlePersistentWorkerCarrier() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/repo/pull/44");
        session.setStatus("pr_opened");

        when(julesSessionRepository.findByStatus("pr_opened")).thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectFlowService.isPersistentWorkerCarrierTask(task)).thenReturn(true);

        assertEquals(0, julesDispatchService.reconcileStrandedPrOpenedWorkflows());
        verify(claimService, never()).complete(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void chaoticDomainPrOpenedRoutesToJulesFallbackNeverCallsGemini() {
        // 2026-07-25 emergency cost incident: even the chaotic-domain immediate-review path (previously
        // Gemini's fastest lane) must never call Gemini review anymore - it queues into the same
        // Jules-reviewer fallback dispatch as every other PR.
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setStatus(TaskStatus.claimed);
        task.setCynefinDomain("chaotic");

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/chaotic");
        session.setPrUrl("https://github.com/org/repo/pull/41");
        session.setStatus("pr_opened");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubPullRequestService.fetchDiffText(project, 41)).thenReturn(java.util.Optional.of("diff --git a/x b/x"));

        julesDispatchService.handlePrOpenedWorkflow(session);

        // The Jules-fallback dispatch reached the point of fetching the real diff for PR #41 - proof the
        // task was queued into the fallback path, not silently dropped.
        verify(gitHubPullRequestService).fetchDiffText(project, 41);
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void batchedReviewGroupsSiblingsIntoTheSameJulesFallbackBatchNeverCallsGemini() {
        // Was "...ThreadsTheirPrUrls" - the sibling-PR-context feature was specific to the Gemini review
        // call, now removed (2026-07-25 emergency cost incident). Same-project tasks (siblings included)
        // still end up together in ONE Jules-fallback dispatch call, they just no longer thread through
        // Gemini at all.
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        UUID taskCId = UUID.randomUUID();
        UUID sessionAId = UUID.randomUUID();
        UUID sessionBId = UUID.randomUUID();
        UUID sessionCId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        RoleEntity backendRole = new RoleEntity();
        backendRole.setTag("BARCAN-TAG-02");
        RoleEntity frontendRole = new RoleEntity();
        frontendRole.setTag("BARCAN-TAG-11");
        RoleEntity soloRole = new RoleEntity();
        soloRole.setTag("BARCAN-TAG-08");

        TaskEntity taskA = new TaskEntity();
        taskA.setId(taskAId);
        taskA.setProject(project);
        taskA.setRole(backendRole);
        taskA.setFeatureId(featureId);
        taskA.setStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);

        TaskEntity taskB = new TaskEntity();
        taskB.setId(taskBId);
        taskB.setProject(project);
        taskB.setRole(frontendRole);
        taskB.setFeatureId(featureId);
        taskB.setStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);

        TaskEntity taskC = new TaskEntity();
        taskC.setId(taskCId);
        taskC.setProject(project);
        taskC.setRole(soloRole);
        taskC.setFeatureId(null);
        taskC.setStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);

        JulesSessionEntity sessionA = new JulesSessionEntity();
        sessionA.setId(sessionAId);
        sessionA.setTaskId(taskAId);
        sessionA.setExternalSessionId("sessions/a");
        sessionA.setPrUrl("https://github.com/org/repo/pull/50");
        sessionA.setStatus("pr_opened");
        sessionA.setUpdatedAt(Instant.now());

        JulesSessionEntity sessionB = new JulesSessionEntity();
        sessionB.setId(sessionBId);
        sessionB.setTaskId(taskBId);
        sessionB.setExternalSessionId("sessions/b");
        sessionB.setPrUrl("https://github.com/org/repo/pull/51");
        sessionB.setStatus("pr_opened");
        sessionB.setUpdatedAt(Instant.now());

        JulesSessionEntity sessionC = new JulesSessionEntity();
        sessionC.setId(sessionCId);
        sessionC.setTaskId(taskCId);
        sessionC.setExternalSessionId("sessions/c");
        sessionC.setPrUrl("https://github.com/org/repo/pull/52");
        sessionC.setStatus("pr_opened");
        sessionC.setUpdatedAt(Instant.now());

        when(taskRepository.findByStatus(com.eneik.production.models.persistence.TaskStatus.pending_review))
                .thenReturn(List.of(taskA, taskB, taskC));
        when(julesSessionRepository.findByTaskId(taskAId)).thenReturn(List.of(sessionA));
        when(julesSessionRepository.findByTaskId(taskBId)).thenReturn(List.of(sessionB));
        when(julesSessionRepository.findByTaskId(taskCId)).thenReturn(List.of(sessionC));
        when(claimService.hasActiveClaim(any())).thenReturn(false);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubPullRequestService.fetchDiffText(eq(project), anyInt())).thenReturn(java.util.Optional.of("diff --git a/x b/x"));

        julesDispatchService.processPendingReviewBatch();

        // All three tasks reached the Jules-fallback dispatch (real diff fetched for each PR) - proof they
        // were queued, not silently dropped - and Gemini was never called for any of them.
        verify(gitHubPullRequestService).fetchDiffText(project, 50);
        verify(gitHubPullRequestService).fetchDiffText(project, 51);
        verify(gitHubPullRequestService).fetchDiffText(project, 52);
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void batchedReviewFallsBackToPrReviewEvidenceWhenSessionSelfReportsFailedAfterOpeningARealPr() {
        // Direct regression test for a live incident on test-thirty-seventh (2026-07-25): a session opened
        // a real PR (CI green, genuinely still open on GitHub) but later self-reported status "failed" for
        // an unrelated reason - the old latestOpenPrSession trusted ONLY that self-reported status and
        // silently skipped this task forever. The fix falls back to real PrReviewEntity evidence.
        com.eneik.production.repositories.PrReviewRepository prReviewRepository =
                (com.eneik.production.repositories.PrReviewRepository) ReflectionTestUtils.getField(julesDispatchService, "prReviewRepository");

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-07");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setRole(role);
        task.setFeatureId(null);
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/self-reported-failed-but-real-pr");
        session.setPrUrl("https://github.com/org/repo/pull/95");
        session.setStatus("failed"); // self-reported - must NOT be trusted on its own
        session.setUpdatedAt(Instant.now());

        com.eneik.production.models.persistence.PrReviewEntity realEvidence = new com.eneik.production.models.persistence.PrReviewEntity();
        realEvidence.setJulesSessionId(sessionId);
        realEvidence.setPrUrl("https://github.com/org/repo/pull/95");
        realEvidence.setCiStatus("success");
        realEvidence.setMerged(false);

        when(taskRepository.findByStatus(com.eneik.production.models.persistence.TaskStatus.pending_review))
                .thenReturn(List.of(task));
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionId(sessionId)).thenReturn(List.of(realEvidence));
        when(claimService.hasActiveClaim(any())).thenReturn(false);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubPullRequestService.fetchDiffText(project, 95)).thenReturn(java.util.Optional.of("diff --git a/x b/x"));

        julesDispatchService.processPendingReviewBatch();

        // The task reached the Jules-fallback dispatch (real diff fetched for PR #95) despite the
        // session's own self-reported "failed" status - proof latestOpenPrSession's evidence fallback
        // still found it via the real PrReviewEntity row, not the untrustworthy self-report.
        verify(gitHubPullRequestService).fetchDiffText(project, 95);
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void duplicateCompilerSessionForAlreadyCompiledWishlistDoesNotReDecompose() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);
        var compilerTaskPayload = objectMapper.createObjectNode();
        compilerTaskPayload.putArray("compilesWishlistIds").add(wishlistId.toString());
        compilerTask.setPayload(compilerTaskPayload);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        // Already compiled by another session - this is the exact live-incident state: a second compiler
        // task/session dispatched against a brief that was already turned into real tasks.
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.converted_to_task);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/duplicate-compiler");
        session.setStatus("pr_opened");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(compilerTask));
        when(projectFlowService.isWishlistCompilerTask(compilerTask)).thenReturn(true);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        when(gitHubPullRequestService.findOpenPullRequestBySession(eq(project), eq("sessions/duplicate-compiler")))
                .thenReturn(Optional.empty());

        julesDispatchService.handlePrOpenedWorkflow(session);

        verify(projectFlowService, never()).buildTaskGraphFromSlices(any(), any(), any());
    }

    @Test
    void firstCompilerSessionForAWishlistStillCompilingIsNotTreatedAsADuplicate() {
        // Regression guard: dispatchWishlistCompiler flips the wishlist to `compiling` at DISPATCH time,
        // before any session completes - so the FIRST (and only) session to reach completion also sees a
        // non-pending status. A guard that rejects anything "!= pending" would wrongly discard this
        // legitimate completion too, exactly like the live incident where a wishlist got stuck looping
        // forever (compile -> wrongly discarded -> blocked -> recovery -> compile -> discarded again).
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);
        var compilerTaskPayload = objectMapper.createObjectNode();
        compilerTaskPayload.putArray("compilesWishlistIds").add(wishlistId.toString());
        compilerTask.setPayload(compilerTaskPayload);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.compiling);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/first-completion");
        session.setStatus("pr_opened");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(compilerTask));
        when(projectFlowService.isWishlistCompilerTask(compilerTask)).thenReturn(true);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        when(wishlistRepository.compareAndSetStatusWithTimestamp(eq(wishlistId),
                eq(com.eneik.production.models.persistence.WishlistStatus.compiling),
                eq(com.eneik.production.models.persistence.WishlistStatus.finalizing),
                any())).thenReturn(1);
        when(gitHubPullRequestService.findOpenPullRequestBySession(eq(project), eq("sessions/first-completion")))
                .thenReturn(Optional.empty());

        julesDispatchService.handlePrOpenedWorkflow(session);

        verify(gitHubPullRequestService, never()).mergeRecordPullRequest(
                any(), any(), eq("duplicate wishlist compiler run discarded (wishlist already compiled)"));
        // The claim must have been won (PROCEED, not IN_PROGRESS_ELSEWHERE) - confirmed by the invalid-plan
        // path releasing it back to `compiling` rather than leaving it stuck in `finalizing`.
        verify(wishlistRepository).compareAndSetStatus(wishlistId,
                com.eneik.production.models.persistence.WishlistStatus.finalizing,
                com.eneik.production.models.persistence.WishlistStatus.compiling);
    }

    @Test
    void secondConcurrentCompletionForTheSameWishlistIsExcludedByTheCompareAndSwapClaim() {
        // Regression guard for a same-night bug: the first version of admitWishlistCompilationCompletion
        // only READ wishlist status and released its lock before the real converted_to_task/dismissed WRITE
        // (which happens later, inside buildTaskGraphFromSlices) - leaving a real window where a replayed
        // completion (reconcileStrandedPrOpenedWorkflows's ~60s poll, or a duplicate webhook) would see
        // "still not converted" a second time and independently rebuild the same task graph. Confirmed live,
        // test-forty-third: one wishlist decomposed 3 times in ~70s. The fix is an atomic compare-and-swap
        // claim (compiling -> finalizing); this test proves a second concurrent call is excluded by it.
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.compiling);

        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        // First call wins the compare-and-swap (1 row affected); a second, concurrent call for the exact
        // same wishlist loses it (0 rows affected, since the real row is no longer `compiling`).
        when(wishlistRepository.compareAndSetStatusWithTimestamp(eq(wishlistId),
                eq(com.eneik.production.models.persistence.WishlistStatus.compiling),
                eq(com.eneik.production.models.persistence.WishlistStatus.finalizing),
                any()))
                .thenReturn(1, 0);

        JulesDispatchService.CompilerCompletionAdmission firstAdmission =
                julesDispatchService.admitWishlistCompilationCompletion(compilerTask, List.of(wishlistId));
        JulesDispatchService.CompilerCompletionAdmission secondAdmission =
                julesDispatchService.admitWishlistCompilationCompletion(compilerTask, List.of(wishlistId));

        assertEquals(JulesDispatchService.CompilationAdmissionOutcome.PROCEED, firstAdmission.outcome());
        assertEquals(JulesDispatchService.CompilationAdmissionOutcome.IN_PROGRESS_ELSEWHERE, secondAdmission.outcome());
        assertTrue(secondAdmission.claimedIds().isEmpty());
    }

    @Test
    void admitWishlistCompilationCompletionClaimsPendingWishlistsWhenCompilingWasReset() {
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);

        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        // CAS from compiling fails (returns 0) because earlier recovery or sweep reset it to pending.
        when(wishlistRepository.compareAndSetStatusWithTimestamp(eq(wishlistId),
                eq(com.eneik.production.models.persistence.WishlistStatus.compiling),
                eq(com.eneik.production.models.persistence.WishlistStatus.finalizing),
                any()))
                .thenReturn(0);
        // CAS from pending succeeds (returns 1), allowing this valid completion to claim it.
        when(wishlistRepository.compareAndSetStatusWithTimestamp(eq(wishlistId),
                eq(com.eneik.production.models.persistence.WishlistStatus.pending),
                eq(com.eneik.production.models.persistence.WishlistStatus.finalizing),
                any()))
                .thenReturn(1);

        JulesDispatchService.CompilerCompletionAdmission admission =
                julesDispatchService.admitWishlistCompilationCompletion(compilerTask, List.of(wishlistId));

        assertEquals(JulesDispatchService.CompilationAdmissionOutcome.PROCEED, admission.outcome());
        assertTrue(admission.claimedIds().contains(wishlistId));
    }

    @Test
    void completeWishlistCompilationReleasesPrOpenedClaimWhenInProgressElsewhere() {
        UUID projectId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(taskId);
        compilerTask.setProject(project);
        var compilerTaskPayload = objectMapper.createObjectNode();
        compilerTaskPayload.putArray("compilesWishlistIds").add(wishlistId.toString());
        compilerTask.setPayload(compilerTaskPayload);

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(wishlistId);
        wishlist.setProjectId(projectId);
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.finalizing);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/in-progress-elsewhere");
        session.setStatus("pr_opened");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(compilerTask));
        when(projectFlowService.isWishlistCompilerTask(compilerTask)).thenReturn(true);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        // CAS from compiling and pending both return 0 (another worker holds finalizing)
        when(wishlistRepository.compareAndSetStatusWithTimestamp(eq(wishlistId), any(), eq(com.eneik.production.models.persistence.WishlistStatus.finalizing), any()))
                .thenReturn(0);

        julesDispatchService.handlePrOpenedWorkflow(session);

        // When admitWishlistCompilationCompletion returns IN_PROGRESS_ELSEWHERE, the session claim
        // must be released immediately so the 5-minute lease does not lock out subsequent reconcile sweeps.
        verify(julesSessionRepository).releasePrOpenedWorkflowClaim(sessionId);
    }

    @Test
    void activeReviewFallbackPreventsDuplicateDispatchForSameOriginalTask() {
        UUID projectId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity activeFallback = new TaskEntity();
        activeFallback.setId(UUID.randomUUID());
        activeFallback.setProject(project);
        activeFallback.setStatus(TaskStatus.claimed);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(activeFallback));
        when(projectFlowService.isReviewFallbackTask(activeFallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(activeFallback)).thenReturn(List.of(targetTaskId));

        assertEquals(Set.of(targetTaskId), julesDispatchService.reviewFallbackTargetsInFlight(projectId));
    }

    @Test
    void completedReviewFallbackPreventsAutomaticRetryForSameTarget() {
        UUID projectId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity completedFallback = new TaskEntity();
        completedFallback.setId(UUID.randomUUID());
        completedFallback.setProject(project);
        completedFallback.setStatus(TaskStatus.done);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(completedFallback));
        when(projectFlowService.isReviewFallbackTask(completedFallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(completedFallback)).thenReturn(List.of(targetTaskId));
        when(projectFlowService.reviewFallbackTargetPrUrls(completedFallback)).thenReturn(List.of("https://github.com/org/repo/pull/1"));
        when(projectFlowService.reviewFallbackTargetDiffHashes(completedFallback)).thenReturn(List.of("abc123"));

        assertTrue(julesDispatchService.reviewFallbackTargetsInFlight(projectId).isEmpty());
        assertEquals(Set.of(targetTaskId + "::https://github.com/org/repo/pull/1::abc123"),
                julesDispatchService.reviewFallbackTargetsEverAttempted(projectId));
    }

    @Test
    void targetWithUnresolvedNullVerdictBelowCapIsExcludedFromBlockingSet() {
        // The actual bug (2026-07-26, test-thirty-eighth): a completed batch with no verdict entry for one
        // target left it in pending_review with its poka-yoke key already burned, so it could never be
        // re-reviewed. This asserts the fix: while the per-target retry counter is under the cap and the
        // task is still pending_review, its key must NOT be in the blocking set, so the next
        // processPendingReviewBatch tick can legitimately re-dispatch it.
        UUID projectId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity completedFallback = new TaskEntity();
        completedFallback.setId(UUID.randomUUID());
        completedFallback.setProject(project);
        completedFallback.setStatus(TaskStatus.done);

        TaskEntity target = new TaskEntity();
        target.setId(targetTaskId);
        target.setStatus(TaskStatus.pending_review);

        // The target belongs to this project, so it is in the project's own task list - which is what the
        // production query returns and what the caller now indexes instead of asking per target (§4.25).
        target.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(completedFallback, target));
        when(projectFlowService.isReviewFallbackTask(completedFallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(completedFallback)).thenReturn(List.of(targetTaskId));
        when(projectFlowService.reviewFallbackTargetPrUrls(completedFallback)).thenReturn(List.of("https://github.com/org/repo/pull/1"));
        when(projectFlowService.reviewFallbackTargetDiffHashes(completedFallback)).thenReturn(List.of("abc123"));
        when(projectFlowService.reviewFallbackNullVerdictRetryCount(target)).thenReturn(1);

        assertTrue(julesDispatchService.reviewFallbackTargetsEverAttempted(projectId).isEmpty());
    }

    @Test
    void targetWithNullVerdictRetriesAtCapStaysBlocked() {
        // Once the cap is reached, applyReviewVerdictToTask stops retrying and marks the task blocked
        // instead (so it's no longer pending_review) - but reviewFallbackTargetsEverAttempted's own cap
        // check is asserted directly here too, as a belt-and-suspenders guard against the poka-yoke ever
        // re-admitting a target that has already exhausted its retries while still pending_review.
        UUID projectId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity completedFallback = new TaskEntity();
        completedFallback.setId(UUID.randomUUID());
        completedFallback.setProject(project);
        completedFallback.setStatus(TaskStatus.done);

        TaskEntity target = new TaskEntity();
        target.setId(targetTaskId);
        target.setStatus(TaskStatus.pending_review);

        target.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(completedFallback, target));
        when(projectFlowService.isReviewFallbackTask(completedFallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(completedFallback)).thenReturn(List.of(targetTaskId));
        when(projectFlowService.reviewFallbackTargetPrUrls(completedFallback)).thenReturn(List.of("https://github.com/org/repo/pull/1"));
        when(projectFlowService.reviewFallbackTargetDiffHashes(completedFallback)).thenReturn(List.of("abc123"));
        when(projectFlowService.reviewFallbackNullVerdictRetryCount(target)).thenReturn(3);

        assertEquals(Set.of(targetTaskId + "::https://github.com/org/repo/pull/1::abc123"),
                julesDispatchService.reviewFallbackTargetsEverAttempted(projectId));
    }

    @Test
    void resolvedTargetWithStaleRetryCounterStaysBlocked() {
        // A target that has already left pending_review (approved/blocked/done elsewhere) must never be
        // excluded just because an old retry counter is still sitting in its payload - the exclusion is
        // scoped to "currently unresolved", not "has ever had a null verdict".
        UUID projectId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        TaskEntity completedFallback = new TaskEntity();
        completedFallback.setId(UUID.randomUUID());
        completedFallback.setProject(project);
        completedFallback.setStatus(TaskStatus.done);

        TaskEntity target = new TaskEntity();
        target.setId(targetTaskId);
        target.setStatus(TaskStatus.review);

        target.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(completedFallback, target));
        when(projectFlowService.isReviewFallbackTask(completedFallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(completedFallback)).thenReturn(List.of(targetTaskId));
        when(projectFlowService.reviewFallbackTargetPrUrls(completedFallback)).thenReturn(List.of("https://github.com/org/repo/pull/1"));
        when(projectFlowService.reviewFallbackTargetDiffHashes(completedFallback)).thenReturn(List.of("abc123"));

        assertEquals(Set.of(targetTaskId + "::https://github.com/org/repo/pull/1::abc123"),
                julesDispatchService.reviewFallbackTargetsEverAttempted(projectId));
    }

    @Test
    void cancellingLateSessionDoesNotDowngradeCompletedTask() {
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/late-duplicate");
        session.setStatus("running");

        when(julesSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        julesDispatchService.cancelSession(sessionId, "superseded");

        assertEquals("cancelled", session.getStatus());
        assertEquals(TaskStatus.done, task.getStatus());
        verify(claimService).releaseTerminalClaim(taskId);
        verify(claimService, never()).closeTaskAsFailed(any(), anyString());
    }

    @Test
    void reviewFallbackBecomesObsoleteWhenAllTargetsAreTerminal() {
        UUID targetId = UUID.randomUUID();
        TaskEntity fallback = new TaskEntity();
        fallback.setId(UUID.randomUUID());
        fallback.setStatus(TaskStatus.claimed);
        TaskEntity target = new TaskEntity();
        target.setId(targetId);
        target.setStatus(TaskStatus.done);

        when(projectFlowService.isReviewFallbackTask(fallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(fallback)).thenReturn(List.of(targetId));
        when(taskRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertTrue(julesDispatchService.reviewFallbackTargetsAreTerminal(fallback));
    }

    @Test
    void activeTargetKeepsReviewFallbackAlive() {
        UUID targetId = UUID.randomUUID();
        TaskEntity fallback = new TaskEntity();
        fallback.setId(UUID.randomUUID());
        fallback.setStatus(TaskStatus.claimed);
        TaskEntity target = new TaskEntity();
        target.setId(targetId);
        target.setStatus(TaskStatus.pending_review);

        when(projectFlowService.isReviewFallbackTask(fallback)).thenReturn(true);
        when(projectFlowService.reviewFallbackTargetTaskIds(fallback)).thenReturn(List.of(targetId));
        when(taskRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertFalse(julesDispatchService.reviewFallbackTargetsAreTerminal(fallback));
    }

    @Test
    void compilerPlanRequiresCompleteRequirementCoverage() {
        var slice = new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                "Implement API", "When implementing API for this epic, I want validation, so the flow is safe",
                "Given valid input, When submitted, Then it is stored\nGiven invalid input, When submitted, Then it is rejected",
                "BARCAN-TAG-02", LeanValue.essential, "complicated", "API", "all tests pass", false,
                List.of("R1", "R2"));
        var epic = new com.eneik.production.services.MLPredictionServiceClient.EpicPlan(
                null, "Campaigns", "When an operator runs campaigns, I want safe orchestration, so outreach is controlled",
                "Must-Be", "complicated", "zero invalid campaigns", "campaign integrity", 0,
                List.of("R1: create campaigns", "R2: reject invalid campaigns"), true, List.of(slice));

        assertTrue(JulesDispatchService.isValidCompilerPlan(List.of(epic), 1));
        assertEquals("", JulesDispatchService.compilerPlanRejection(List.of(epic), 1),
                "a usable plan reports no rejection");
        // Action plan 8.3: only ONE rejection is evidence about the brief - the compiler answered and the
        // answer was empty. It is named, so the caller can tell it apart from a refusal by this factory's
        // own check and decline to spend the brief's budget on the latter.
        assertEquals(JulesDispatchService.EMPTY_COMPILER_ANSWER,
                JulesDispatchService.compilerPlanRejection(List.of(), 1),
                "an empty answer must report the one rejection that speaks about the brief");
    }

    /**
     * Plan §4.35. The condition that rejected a compilation decides the brief's fate - an empty compiler
     * answer spends its budget, our own validator's refusal returns it - and it used to exist only as a
     * log line. Measured live 2026-08-29: the client's own brief came back to `pending` with two attempts
     * spent and no epics, and the only durable record on its carrier read "Dispatched to Jules", because
     * the log of the run that rejected it had gone with the restart.
     */
    @Test
    void aRejectedCompilationLeavesItsReasonWhereItOutlivesTheProcess() {
        TaskEntity carrier = new TaskEntity();
        carrier.setId(UUID.randomUUID());
        carrier.setJulesDispatchStatus("Dispatched to Jules");

        julesDispatchService.recordCompilerRejection(carrier, "Plan rejected (attempt 1/2): the answer carried no epic at all");

        verify(taskRepository).save(carrier);
        assertTrue(carrier.getJulesDispatchStatus().contains("carried no epic at all"),
                "the durable record must name the condition that actually rejected the plan");
    }

    /** The other half: a carrier nobody rejected keeps its own record untouched. */
    @Test
    void aCompilationThatWasNotRejectedKeepsItsRecordUntouched() {
        TaskEntity carrier = new TaskEntity();
        carrier.setId(UUID.randomUUID());
        carrier.setJulesDispatchStatus("Dispatched to Jules");

        julesDispatchService.recordCompilerRejection(null, "never mind");

        assertEquals("Dispatched to Jules", carrier.getJulesDispatchStatus());
        verify(taskRepository, never()).save(carrier);
    }

    private TaskEntity carrierAt(ProjectEntity project, int retryCount) {
        TaskEntity carrier = new TaskEntity();
        carrier.setId(UUID.randomUUID());
        carrier.setProject(project);
        carrier.setRetryCount(retryCount);
        return carrier;
    }

    /**
     * Plan §4.36. Measured 2026-08-29: of 116 compiler carriers, 108 needed no correction round, two
     * reached one and six reached two - the whole bound - and not one of the eight recorded whether it had
     * succeeded there or given up. A plan accepted on the last round the bound allows is the only evidence
     * that the bound is reachable, and therefore that it has never been tested from above.
     */
    @Test
    void aPlanAcceptedOnTheLastRoundAllowedRaisesTheCompilerRetryCeiling() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity carrier = carrierAt(project, julesDispatchService.compilerRetryCeiling(carrierAt(project, 0)));

        julesDispatchService.raiseCompilerRetryCeilingIfTheProbeSurvivedAtTheBoundary(carrier);

        assertEquals(carrier.getRetryCount() + 1, project.getCompilerRetryCeiling());
    }

    /**
     * The other half, and it is not optional: a plan accepted with rounds to spare refutes nothing about
     * the bound, so the belief must not move. Without this case the rule would raise the ceiling on every
     * accepted plan - a constant growing by itself rather than a belief revised by evidence.
     */
    @Test
    void aPlanAcceptedWithRoundsToSpareLeavesTheCompilerRetryCeilingAlone() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        julesDispatchService.raiseCompilerRetryCeilingIfTheProbeSurvivedAtTheBoundary(carrierAt(project, 0));

        assertNull(project.getCompilerRetryCeiling());
    }

    @Test
    void compilerPlanRejectsCoverageClaimWithUnmappedRequirement() {
        var slice = new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                "Implement API", "When implementing API for this epic, I want validation, so the flow is safe",
                "Given valid input, When submitted, Then it is stored",
                "BARCAN-TAG-02", LeanValue.essential, "complicated", "API", "all tests pass", false,
                List.of("R1"));
        var epic = new com.eneik.production.services.MLPredictionServiceClient.EpicPlan(
                null, "Campaigns", "When an operator runs campaigns, I want safe orchestration, so outreach is controlled",
                "Must-Be", "complicated", "zero invalid campaigns", "campaign integrity", 0,
                List.of("R1: create campaigns", "R2: reject invalid campaigns"), true, List.of(slice));

        assertFalse(JulesDispatchService.isValidCompilerPlan(List.of(epic), 1));
        // 2026-08-29: the reason has to name the condition, not repeat that something was wrong. There are
        // fourteen ways to fail in here, and the compiler task went to human review twice with the single
        // word "invalid" while six briefs settled as refused behind it.
        assertFalse(JulesDispatchService.compilerPlanRejection(List.of(epic), 1).isBlank(),
                "a rejected plan must say which condition rejected it");
    }

    @Test
    void compilerPlanRejectsOmittedInputBrief() {
        var slice = new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                "Implement API", "When implementing API for this epic, I want validation, so the flow is safe",
                "Given valid input, When submitted, Then it is stored",
                "BARCAN-TAG-02", LeanValue.essential, "clear", "API", "all tests pass", false,
                List.of("R1"));
        var epic = new com.eneik.production.services.MLPredictionServiceClient.EpicPlan(
                null, "Campaigns", "When an operator runs campaigns, I want safe orchestration, so outreach is controlled",
                "Must-Be", "clear", "zero invalid campaigns", "campaign integrity", 0,
                List.of("R1: create campaigns"), true, List.of(slice));

        assertFalse(JulesDispatchService.isValidCompilerPlan(List.of(epic), 2));
        assertTrue(JulesDispatchService.compilerPlanRejection(List.of(epic), 2).contains("got no epic at all"),
                "a brief with no epic must be named as the reason");
    }

    // --- Agent-dialog answering never calls Gemini (2026-07-26, operator directive: "не согласен. может
    // быть детерменированный ответ. 'Следуй своим предпостениям и рекомендациям'" - we trust Jules; ANY
    // question not caught by a specific pattern gets the universal "use your own judgment, document your
    // assumption" answer instead of an LLM adjudicating for Jules) ------------------------------------------

    @Test
    void genericProceedQuestionGetsDeterministicAnswerNeverCallsGemini() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setDescription("Implement the feature.");

        String answer = julesDispatchService.buildJulesQuestionAnswer(task, "Should I proceed?", 0);

        assertTrue(answer != null && answer.contains("Proceed using the existing task description"));
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void questionWithARealForkStillGetsDeterministicAnswerNeverCallsGemini() {
        // The operator's correction: even a substantive question with a real fork between named
        // alternatives does NOT need Gemini to adjudicate - trust Jules to pick the most reasonable option
        // from the task facts and document the assumption, same as any other unmatched question.
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setDescription("Implement the feature.");

        String answer = julesDispatchService.buildJulesQuestionAnswer(task,
                "Should I proceed with a REST endpoint or a GraphQL resolver for this feature?", 0);

        assertTrue(answer != null && answer.contains("Proceed using the existing task description"));
        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void artifactHygieneQuestionStillUsesItsOwnSpecificDeterministicAnswer() {
        // The more specific patterns (artifact hygiene, repeated-question circuit breaker) still take
        // priority over the universal fallback - they carry concrete remediation commands the generic
        // "use your judgment" message doesn't.
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setDescription("Implement the feature.");

        String answer = julesDispatchService.buildJulesQuestionAnswer(task,
                "Generated/local artifact detected in PR diff: playwright-report/.", 0);

        assertTrue(answer.contains("Git hygiene issue only"));
        verifyNoInteractions(mlPredictionServiceClient);
    }

    // 2026-08-09 (live incident, operator-flagged: "проверь что все философы высказались"): BARCAN-TAG-12
    // was sent a follow-up at 09:00:14 and the whole 13-role philosophical discussion was merged/closed by
    // 09:00:23 - 9 seconds later - with the archived report never containing a single critique for that
    // role. Root cause: "covered" used to be an append-only marker on the carrier task's payload, written
    // the moment a role batch was SENT, not once Jules's answer actually existed. Fix: completePersistent-
    // PhilosophicalAuditCycle now derives "covered" by parsing the report file's real current content -
    // these two tests pin that behavior down directly.

    private TaskEntity philosophicalCarrierTask(UUID taskId, ProjectEntity project) {
        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setId(taskId);
        carrierTask.setProject(project);
        return carrierTask;
    }

    private RoleEntity activeRole(String tag) {
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setActive(true);
        return role;
    }

    @Test
    void philosophicalAuditNeverClosesWhenTheJustRequestedRoleHasNoRealCritiqueYet() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        TaskEntity carrierTask = philosophicalCarrierTask(taskId, project);

        com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker =
                new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setCarrierTaskId(taskId);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/live-discussion");
        session.setPrUrl("https://github.com/org/repo/pull/193");
        session.setStatus("pr_opened");

        com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest openPr =
                new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/193", 193, "Philosophical Product Falsification",
                "task-falsification-live", "eneikdru", false, "main", false, Instant.now());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(carrierTask));
        when(projectFlowService.isPersistentWorkerCarrierTask(carrierTask)).thenReturn(true);
        when(projectFlowService.isPhilosophicalAuditTask(carrierTask)).thenReturn(true);
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));
        when(persistentWorkerSessionService.consumeCurrentBatch(worker)).thenReturn(List.of(UUID.randomUUID()));
        when(projectFlowService.philosophicalAuditReportPath(carrierTask))
                .thenReturn(".eneik/records/philosophical-falsification-x.json");
        // Only BARCAN-TAG-11 has a real critique on the branch - BARCAN-TAG-12 (just asked about) does not,
        // exactly like the live incident.
        when(roleRepository.findAll()).thenReturn(List.of(activeRole("BARCAN-TAG-11"), activeRole("BARCAN-TAG-12")));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/live-discussion"))
                .thenReturn(Optional.of(openPr));
        when(gitHubPullRequestService.fetchFileContent(project, "task-falsification-live",
                ".eneik/records/philosophical-falsification-x.json"))
                .thenReturn(Optional.of("""
                        {"critiques":[{"roleTag":"BARCAN-TAG-11","philosopher":"Test Philosopher",
                        "worldview":"w","critique":"c","proposal":"p","dislike":"d","kanoClass":"must-be",
                        "confidence":"high","evidence":"e","screenshotFile":""}]}
                        """));

        julesDispatchService.handlePrOpenedWorkflow(session);

        verify(falsificationCycleService, never()).applyPhilosophicalCritiques(any(), any(), any());
        verify(gitHubPullRequestService, never()).mergeRecordPullRequest(any(), any(), any());
        verify(persistentWorkerSessionService, never()).retire(any(), any());
        verify(claimService, never()).complete(any());
    }

    @Test
    void philosophicalAuditClosesOnlyOnceEveryActiveRoleHasAGenuineCritiqueInTheRealReport() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("repo");

        TaskEntity carrierTask = philosophicalCarrierTask(taskId, project);

        com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker =
                new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setCarrierTaskId(taskId);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/live-discussion-done");
        session.setPrUrl("https://github.com/org/repo/pull/200");
        session.setStatus("pr_opened");

        com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest openPr =
                new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/200", 200, "Philosophical Product Falsification",
                "task-falsification-done", "eneikdru", false, "main", false, Instant.now());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(carrierTask));
        when(projectFlowService.isPersistentWorkerCarrierTask(carrierTask)).thenReturn(true);
        when(projectFlowService.isPhilosophicalAuditTask(carrierTask)).thenReturn(true);
        when(persistentWorkerSessionService.findByCarrierTaskId(taskId)).thenReturn(Optional.of(worker));
        when(persistentWorkerSessionService.consumeCurrentBatch(worker)).thenReturn(List.of(UUID.randomUUID()));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);
        when(projectFlowService.philosophicalAuditReportPath(carrierTask))
                .thenReturn(".eneik/records/philosophical-falsification-y.json");
        when(roleRepository.findAll()).thenReturn(List.of(activeRole("BARCAN-TAG-11"), activeRole("BARCAN-TAG-12")));
        when(gitHubPullRequestService.findOpenPullRequestBySession(project, "sessions/live-discussion-done"))
                .thenReturn(Optional.of(openPr));
        when(gitHubPullRequestService.fetchFileContent(project, "task-falsification-done",
                ".eneik/records/philosophical-falsification-y.json"))
                .thenReturn(Optional.of("""
                        {"critiques":[
                        {"roleTag":"BARCAN-TAG-11","philosopher":"A","worldview":"w","critique":"c","proposal":"p",
                        "dislike":"d","kanoClass":"must-be","confidence":"high","evidence":"e","screenshotFile":""},
                        {"roleTag":"BARCAN-TAG-12","philosopher":"B","worldview":"w","critique":"c","proposal":"p",
                        "dislike":"d","kanoClass":"attractive","confidence":"high","evidence":"e","screenshotFile":""}
                        ]}
                        """));

        julesDispatchService.handlePrOpenedWorkflow(session);

        verify(falsificationCycleService).applyPhilosophicalCritiques(eq(project), argThat(critiques ->
                critiques.size() == 2), any());
        verify(gitHubPullRequestService).mergeRecordPullRequest(eq(project), eq(openPr), any());
        verify(persistentWorkerSessionService).retire(eq(worker), any());
        verify(claimService).complete(taskId);
    }

    // --- §9: a PR closed without a merge is not worth a review session ---------------------------------
    //
    // Observed 2026-08-28: seven Jules sessions reviewing test-fiftieth PR 170, which was CLOSED and never
    // merged. The dedup key is keyed on the diff hash, so each new revision of the same PR legitimately
    // unlocked one more review - a bound the bounded thing could raise. Closure is the fact that ends it,
    // and no predicate read it. These four pin both halves of invariant 10: that the predicate is right,
    // AND that it is applied where the dispatch decision is made.

    private com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest pr(
            String url, int number, boolean merged) {
        return new com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest(
                url, number, "PR " + number, "jules/" + number, "jules", merged, "main", false, Instant.now());
    }

    private void snapshot(ProjectEntity project,
            List<com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest> open,
            List<com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest> closed) {
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(
                new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                        true, "org", "repo", open, closed, null));
    }

    @Test
    void reviewableSetHoldsOpenAndMergedPrsButNotOnesClosedWithoutAMerge() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        snapshot(project,
                List.of(pr("https://github.com/org/repo/pull/1", 1, false)),
                List.of(pr("https://github.com/org/repo/pull/2", 2, true),
                        pr("https://github.com/org/repo/pull/170", 170, false)));

        Set<String> reviewable = julesDispatchService.reviewablePrUrls(project);

        assertTrue(reviewable.contains("https://github.com/org/repo/pull/1"), "an open PR is reviewable");
        assertTrue(reviewable.contains("https://github.com/org/repo/pull/2"), "a merged PR is reviewable");
        assertFalse(reviewable.contains("https://github.com/org/repo/pull/170"),
                "a PR closed without a merge carries its verdict already");
    }

    @Test
    void unreachableGitHubMeansUnknownRatherThanNothingIsReviewable() {
        // An empty set here would turn a GitHub outage into a permanent halt of all review. Null is read
        // by the caller as "do not block", the same policy the diff fetch already follows.
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        when(gitHubPullRequestService.pullRequestSnapshot(project)).thenReturn(
                new com.eneik.production.services.github.GitHubPullRequestService.PullRequestSnapshot(
                        false, null, null, null, null, "rate limited"));

        assertNull(julesDispatchService.reviewablePrUrls(project));
    }

    @Test
    void closedUnmergedPrNeverEvenGetsItsDiffFetched() {
        // Application, not just the predicate: the check sits ahead of the diff fetch, so a settled PR
        // costs neither a GitHub call nor a session.
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.review);
        snapshot(project, List.of(), List.of(pr("https://github.com/org/repo/pull/170", 170, false)));

        julesDispatchService.dispatchReviewerFallbackBatch(List.of(
                new JulesDispatchService.PendingFallbackReview(task, "https://github.com/org/repo/pull/170")));

        verify(gitHubPullRequestService, never()).fetchDiffText(any(), anyInt());
    }

    /**
     * Plan §4.28. The observer that establishes the verdict records it, through the one place that owns
     * the record. Before this, the review sweep reasoned the conclusion out in full every fifteen minutes
     * and left the task in pending_review, where it went on counting as a failing review and held the
     * whole project in BLOCKED_BY_REVIEW - and only the hourly GitHub-truth sweep could write it down.
     */
    @Test
    void aPrFoundClosedWithoutAMergeRetiresItsTaskThroughTheOnePlaceThatOwnsThatRecord() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.pending_review);
        snapshot(project, List.of(), List.of(pr("https://github.com/org/repo/pull/421", 421, false)));
        when(taskRepository.writeStatusUnlessTerminal(task.getId(), TaskStatus.failed)).thenReturn(1);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        julesDispatchService.dispatchReviewerFallbackBatch(List.of(
                new JulesDispatchService.PendingFallbackReview(task, "https://github.com/org/repo/pull/421")));

        verify(taskRepository).writeStatusUnlessTerminal(task.getId(), TaskStatus.failed);
    }

    /**
     * The other half, and it is not optional. Falling out of the reviewable set has two causes: the PR was
     * closed without a merge, or it was never found at all. Only the first is evidence. Without this case
     * the test above would also pass if the branch acted on the absence of evidence.
     */
    @Test
    void aPrThatIsSimplyNotInTheSnapshotWritesNoStatusAtAll() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.pending_review);
        snapshot(project, List.of(pr("https://github.com/org/repo/pull/1", 1, false)), List.of());

        julesDispatchService.dispatchReviewerFallbackBatch(List.of(
                new JulesDispatchService.PendingFallbackReview(task, "https://github.com/org/repo/pull/999")));

        verify(taskRepository, never()).writeStatusUnlessTerminal(any(), any());
        verify(gitHubPullRequestService, never()).fetchDiffText(any(), anyInt());
    }

    @Test
    void openPrStillReachesTheDiffFetch() {
        // The guard must not be so strict that it stops real work - the failure mode opposite to the one
        // it was written for.
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.review);
        snapshot(project, List.of(pr("https://github.com/org/repo/pull/9", 9, false)), List.of());
        when(gitHubPullRequestService.fetchDiffText(project, 9)).thenReturn(Optional.empty());

        julesDispatchService.dispatchReviewerFallbackBatch(List.of(
                new JulesDispatchService.PendingFallbackReview(task, "https://github.com/org/repo/pull/9")));

        verify(gitHubPullRequestService).fetchDiffText(project, 9);
    }

    @Test
    void admitReviewFallbackBatchCreatesTaskUnderAdmissionMutex() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-01");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setRole(role);

        UUID carrierTaskId = UUID.randomUUID();
        when(persistentWorkerSessionService.isEnabled()).thenReturn(false);
        when(projectFlowService.createReviewFallbackBatchTask(anyList(), anyList(), anyList(), anyString(), anyString()))
                .thenReturn(carrierTaskId);

        JulesDispatchService.ReviewFallbackAdmission admission = julesDispatchService.admitReviewFallbackBatch(
                projectId,
                List.of(task),
                List.of("https://github.com/org/repo/pull/1"),
                List.of("hash123")
        );

        assertNotNull(admission);
        assertEquals(carrierTaskId, admission.carrierTaskId());
        assertEquals(1, admission.tasks().size());
        verify(projectFlowService).createReviewFallbackBatchTask(
                eq(List.of(task)),
                eq(List.of("https://github.com/org/repo/pull/1")),
                eq(List.of("hash123")),
                anyString(),
                anyString()
        );
    }

    @Test
    void admitAndDispatchReviewFallbackBatchDelegatesAdmissionThenDispatchesOutside() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-01");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setRole(role);

        UUID carrierTaskId = UUID.randomUUID();
        when(persistentWorkerSessionService.isEnabled()).thenReturn(false);
        when(projectFlowService.createReviewFallbackBatchTask(anyList(), anyList(), anyList(), anyString(), anyString()))
                .thenReturn(carrierTaskId);
        when(projectFlowService.dispatchReviewFallbackTask(carrierTaskId)).thenReturn(carrierTaskId);

        julesDispatchService.admitAndDispatchReviewFallbackBatch(
                projectId,
                List.of(task),
                List.of("https://github.com/org/repo/pull/1"),
                List.of("hash123")
        );

        verify(projectFlowService).dispatchReviewFallbackTask(carrierTaskId);
    }
}
