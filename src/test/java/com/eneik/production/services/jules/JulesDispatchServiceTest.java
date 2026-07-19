package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        com.eneik.production.repositories.RoleRepository roleRepository = mock(com.eneik.production.repositories.RoleRepository.class);
        com.eneik.production.repositories.TaskConflictRepository taskConflictRepository = mock(com.eneik.production.repositories.TaskConflictRepository.class);
        com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository = mock(com.eneik.production.repositories.PrReviewRepository.class);
        featureThreadRepository = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        julesDispatchService = new JulesDispatchService(
            julesApiClient, julesSessionRepository, julesActivityResponseRepository, wishlistRepository, accountRepository, taskRepository, taskConflictRepository, claimService, roleCapabilityLoader,
            prReviewPipelineService, mlPredictionServiceClient, roleRepository, gitHubPullRequestService, prReviewRepository,
            mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
            mock(com.eneik.production.services.ProjectFlowService.class),
            mock(com.eneik.production.repositories.NeedsHumanReviewRepository.class),
            mock(com.eneik.production.services.FalsificationCycleService.class),
            featureThreadRepository, "prefix/"
        );
        ReflectionTestUtils.setField(julesDispatchService, "stuckThresholdMinutes", 30);
        ReflectionTestUtils.setField(julesDispatchService, "maxAgentDialogResponses", 8);
        ReflectionTestUtils.setField(julesDispatchService, "loopCloseSimilarThreshold", 3);
        ReflectionTestUtils.setField(julesDispatchService, "forcedUnblockBlindCycleThreshold", 5);
        ReflectionTestUtils.setField(julesDispatchService, "forcedUnblockMaxAttempts", 2);
    }

    @Test
    void testMapExternalStatus() {
        assertEquals("queued", julesDispatchService.mapExternalStatus("QUEUED"));
        assertEquals("running", julesDispatchService.mapExternalStatus("RUNNING"));
        assertEquals("pr_opened", julesDispatchService.mapExternalStatus("SUCCEEDED"));
        assertEquals("failed", julesDispatchService.mapExternalStatus("FAILED"));
        assertEquals("failed", julesDispatchService.mapExternalStatus("CANCELLED"));
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
        verify(claimService, times(1)).detectStuckSessions(30);
    }

    @Test
    void closesLoopAndCreatesWishlistWhenDialogueBudgetExceeded() throws Exception {
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
        when(julesApiClient.getSessionActivities("sessions/abc", "jules-key")).thenReturn(objectMapper.readTree(activities));
        when(julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(eq(sessionId), anyString())).thenReturn(Optional.empty());
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(history);
        when(mlPredictionServiceClient.chat(anyString(), anyString())).thenReturn("Root cause: repeated artifact blocker\nKano classification: Must-Be\nCynefin domain: clear");
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("loop_closed", result.getStatus());
        verify(julesApiClient, never()).sendMessage(eq("sessions/abc"), anyString(), eq("jules-key"));
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("dialog_limit_exceeded"));
        verify(wishlistRepository).save(argThat(item ->
                item instanceof WishlistEntity
                        && ((WishlistEntity) item).getContent().contains("Kano: Must-Be")
                        && ((WishlistEntity) item).getContent().contains("Cynefin: clear")
                        && ((WishlistEntity) item).getContent().contains("one atomic result")
        ));
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
        when(julesApiClient.getSessionActivities("sessions/overflow", "jules-key"))
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
    void repeatedPrReviewArtifactBlockerCreatesDebtInsteadOfStoppingFlow() {
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
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/review-loop");
        session.setPrUrl("https://github.com/org/repo/pull/12");
        session.setStatus("pr_opened");

        com.eneik.production.models.persistence.JulesActivityResponseEntity previous =
                new com.eneik.production.models.persistence.JulesActivityResponseEntity();
        previous.setJulesSessionId(sessionId);
        previous.setActivityName("system-pr-review-rejection");
        previous.setActivityHash("hash");
        previous.setQuestion("PR review rejection: Generated/local artifact detected in PR diff: playwright-report/.");
        previous.setResponse("Clean artifacts");
        previous.setSent(true);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(claimService.hasActiveClaim(taskId)).thenReturn(true);
        when(mlPredictionServiceClient.reviewPr(projectId, taskId, "https://github.com/org/repo/pull/12"))
                .thenReturn(Map.of(
                        "approved", false,
                        "remarks", "Generated/local artifact detected in PR diff: playwright-report/.",
                        "newTasks", List.of()
                ));
        when(julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of(previous));
        when(julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(eq(sessionId), anyString())).thenReturn(Optional.empty());
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(mlPredictionServiceClient.chat(anyString(), anyString()))
                .thenReturn("Root cause: repeated artifact blocker\nKano classification: Must-Be\nCynefin domain: clear");

        julesDispatchService.handlePrOpenedWorkflow(session);

        assertEquals("pr_opened", session.getStatus());
        assertEquals(com.eneik.production.models.persistence.TaskStatus.review, task.getStatus());
        verify(julesApiClient, never()).sendMessage(anyString(), anyString());
        verify(julesApiClient, never()).sendMessage(anyString(), anyString(), anyString());
        verify(claimService, never()).closeTaskAsBlocked(eq(taskId), contains("repository_hygiene_review_repeated"));
        verify(wishlistRepository).save(argThat(item ->
                item instanceof WishlistEntity
                        && ((WishlistEntity) item).getContent().contains("Repository hygiene technical debt")
                        && ((WishlistEntity) item).getContent().contains("minor generated/local artifact")
        ));
    }

    @Test
    void forceUnblockSendsDeterministicMessageWhenBlindAndStale() {
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
        session.setLastProgressAt(Instant.now().minus(45, ChronoUnit.MINUTES));

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("jules-key");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);

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
        session.setLastProgressAt(Instant.now().minus(90, ChronoUnit.MINUTES));

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

        julesDispatchService.forceUnblockOverflowedSessions();

        assertEquals("loop_closed", session.getStatus());
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("blind_overflow_unblock_exhausted"));
        verify(julesApiClient, never()).sendMessage(eq("sessions/blind-exhausted"), anyString(), anyString());
    }
}
