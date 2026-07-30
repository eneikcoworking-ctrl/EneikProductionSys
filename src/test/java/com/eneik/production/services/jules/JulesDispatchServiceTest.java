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
        gitHubPullRequestService = mock(com.eneik.production.services.github.GitHubPullRequestService.class);
        com.eneik.production.repositories.PrReviewRepository prReviewRepository = mock(com.eneik.production.repositories.PrReviewRepository.class);
        featureThreadRepository = mock(com.eneik.production.repositories.FeatureThreadRepository.class);
        readinessService = mock(com.eneik.production.services.ClientDeliverableReadinessService.class);
        projectFlowService = mock(com.eneik.production.services.ProjectFlowService.class);
        geminiContextService = mock(com.eneik.production.services.GeminiContextService.class);
        julesDispatchService = new JulesDispatchService(
            julesApiClient, julesSessionRepository, julesActivityResponseRepository, wishlistRepository, accountRepository, taskRepository, taskConflictRepository, claimService, roleCapabilityLoader,
            prReviewPipelineService, mlPredictionServiceClient, roleRepository, gitHubPullRequestService, prReviewRepository,
            mock(com.eneik.production.services.monitor.SystemProgressTracker.class),
            projectFlowService,
            mock(com.eneik.production.repositories.NeedsHumanReviewRepository.class),
            mock(com.eneik.production.services.FalsificationCycleService.class),
            featureThreadRepository, readinessService,
            mock(com.eneik.production.services.PersistentWorkerSessionService.class),
            mock(com.eneik.production.repositories.ProjectRepository.class),
            mock(com.eneik.production.services.WishlistContentSimilarityMatcher.class),
            mock(com.eneik.production.services.settings.SystemSettingsService.class),
            geminiContextService,
            "prefix/"
        );
        ReflectionTestUtils.setField(julesDispatchService, "stuckThresholdMinutes", 30);
        ReflectionTestUtils.setField(julesDispatchService, "stuckCloseThresholdMinutes", 120);
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
        when(julesApiClient.getSessionActivities("sessions/abc", "jules-key")).thenReturn(objectMapper.readTree(activities));
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
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("""
                RELEVANT SYSTEM KNOWLEDGE (retrieved from the indexed knowledge base):
                - [01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md] Single Writer Ownership and Contract-First Parallelism.
                """);

        JulesDispatchResult result = julesDispatchService.dispatch(task);

        assertTrue(result.dispatched());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiContextService).buildContextBlock(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("roleTag=BARCAN-TAG-02"));
        assertTrue(queryCaptor.getValue().contains("contract-first account ingestion"));

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
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);

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
        assertEquals(com.eneik.production.models.persistence.TaskStatus.claimed, task.getStatus());
    }

    @Test
    void forceUnblockTrustsSilentSessionForFullDavidsonWindow() {
        UUID sessionId = UUID.randomUUID();
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(UUID.randomUUID());
        session.setExternalSessionId("sessions/charitable-silence");
        session.setStatus("running");
        session.setBlindCycleCount(6);
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
                        "https://github.com/org/repo/pull/99", 99, "Auto-recovered", "jules-recoverable-1234-abcd", "eneikdru", false, "main", false)));

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
                "jules-orphaned-flyway-fix-9999", "eneikdru", false, "main", true);
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
                "jules-still-open-1111-aaaa", "eneikdru", false, "main", false);
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
                "jules-done-but-closed-unmerged-1234", "eneikdru", false, "main", true);
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
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);

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
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
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
        compilerTask.setPayload(objectMapper.createObjectNode().put("compilesWishlistId", wishlistId.toString()));

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
        compilerTask.setPayload(objectMapper.createObjectNode().put("compilesWishlistId", wishlistId.toString()));

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
        when(gitHubPullRequestService.findOpenPullRequestBySession(eq(project), eq("sessions/first-completion")))
                .thenReturn(Optional.empty());

        julesDispatchService.handlePrOpenedWorkflow(session);

        verify(gitHubPullRequestService, never()).mergeRecordPullRequest(
                any(), any(), eq("duplicate wishlist compiler run discarded (wishlist already compiled)"));
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

        when(taskRepository.findAll()).thenReturn(List.of(activeFallback));
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

        when(taskRepository.findAll()).thenReturn(List.of(completedFallback));
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

        when(taskRepository.findAll()).thenReturn(List.of(completedFallback));
        when(taskRepository.findById(targetTaskId)).thenReturn(Optional.of(target));
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

        when(taskRepository.findAll()).thenReturn(List.of(completedFallback));
        when(taskRepository.findById(targetTaskId)).thenReturn(Optional.of(target));
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

        when(taskRepository.findAll()).thenReturn(List.of(completedFallback));
        when(taskRepository.findById(targetTaskId)).thenReturn(Optional.of(target));
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
}
