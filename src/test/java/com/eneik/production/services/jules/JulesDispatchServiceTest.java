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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        julesDispatchService = new JulesDispatchService(
            julesApiClient, julesSessionRepository, julesActivityResponseRepository, wishlistRepository, accountRepository, taskRepository, taskConflictRepository, claimService, roleCapabilityLoader,
            prReviewPipelineService, mlPredictionServiceClient, roleRepository, gitHubPullRequestService, "prefix/"
        );
        ReflectionTestUtils.setField(julesDispatchService, "stuckThresholdMinutes", 30);
        ReflectionTestUtils.setField(julesDispatchService, "maxAgentDialogResponses", 8);
        ReflectionTestUtils.setField(julesDispatchService, "loopCloseSimilarThreshold", 3);
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
    void closesLoopAndCreatesWishlistWhenActivitiesOverflow() {
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
        when(mlPredictionServiceClient.chat(anyString(), anyString())).thenReturn("Root cause: activity log overflow\nKano classification: Must-Be\nCynefin domain: complex");
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of());

        JulesSessionEntity result = julesDispatchService.pollStatus(sessionId);

        assertEquals("loop_closed", result.getStatus());
        verify(julesApiClient, never()).sendMessage(eq("sessions/overflow"), anyString(), eq("jules-key"));
        verify(claimService).closeTaskAsBlocked(eq(taskId), contains("activity_log_overflow"));
        verify(wishlistRepository).save(argThat(item ->
                item instanceof WishlistEntity
                        && ((WishlistEntity) item).getContent().contains("Cynefin: complex")
                        && ((WishlistEntity) item).getContent().contains("activities payload exceeded")
        ));
    }
}
