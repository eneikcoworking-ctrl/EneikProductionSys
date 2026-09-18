package com.eneik.production.controllers.github;

import com.eneik.production.config.GithubConfig;
import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Screen for Prescription 41: Validated GitHub Webhook Ingress
 * (DEREK_PARFIT_02_CAUSAL_PROCESS_TRACE [D013] / ALVA_NOE_01_PERCEPTION_ACTION_LOOP [D011]).
 */
class GithubWebhookControllerTest {

    private PrReviewPipelineService prReviewPipelineService;
    private JulesDispatchService julesDispatchService;
    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private ClaimService claimService;
    private GithubConfig githubConfig;
    private ProjectRepository projectRepository;
    private JulesSessionRepository julesSessionRepository;
    private ObjectMapper objectMapper;

    private GithubWebhookController controller;

    @BeforeEach
    void setUp() {
        prReviewPipelineService = mock(PrReviewPipelineService.class);
        julesDispatchService = mock(JulesDispatchService.class);
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        claimService = mock(ClaimService.class);
        githubConfig = mock(GithubConfig.class);
        projectRepository = mock(ProjectRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        objectMapper = new ObjectMapper();

        controller = new GithubWebhookController(
                prReviewPipelineService,
                julesDispatchService,
                taskRepository,
                accountRepository,
                claimService,
                objectMapper,
                githubConfig,
                projectRepository,
                julesSessionRepository
        );
    }

    private String computeSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("missing X-GitHub-Event header returns 400 Bad Request")
    void missingEventHeaderReturns400() {
        ResponseEntity<String> response = controller.handleWebhook("{}", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing X-GitHub-Event");
    }

    @Test
    @DisplayName("missing webhook payload returns 400 Bad Request")
    void missingPayloadReturns400() {
        ResponseEntity<String> response = controller.handleWebhook("", "pull_request", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing webhook payload");
    }

    @Test
    @DisplayName("ping event returns 200 pong")
    void pingEventReturnsPong() {
        ResponseEntity<String> response = controller.handleWebhook("{}", "ping", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("pong");
    }

    @Test
    @DisplayName("invalid signature when secret configured returns 401 Unauthorized")
    void invalidSignatureReturns401() {
        when(githubConfig.getWebhookSecret()).thenReturn("super-secret-key");

        ResponseEntity<String> response = controller.handleWebhook(
                "{\"action\":\"opened\"}", "pull_request", "sha256=invalid-signature");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid webhook signature");
    }

    @Test
    @DisplayName("valid HMAC-SHA256 signature when secret configured is accepted")
    void validSignatureIsAccepted() throws Exception {
        String secret = "super-secret-key";
        when(githubConfig.getWebhookSecret()).thenReturn(secret);

        String payload = "{\"action\":\"opened\",\"pull_request\":{\"html_url\":\"https://github.com/org/repo/pull/1\",\"head\":{\"ref\":\"main\"},\"additions\":10,\"deletions\":5,\"changed_files\":2},\"repository\":{\"name\":\"repo\"}}";
        String signature = computeSignature(payload, secret);

        ResponseEntity<String> response = controller.handleWebhook(payload, "pull_request", signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(prReviewPipelineService).onPrOpened(eq("https://github.com/org/repo/pull/1"), any(UUID.class), any(PrDataDto.class));
    }

    @Test
    @DisplayName("PR opened resolves task via JulesSession PR URL lineage and dispatches reviewer")
    void prOpenedResolvesTaskViaSessionLineage() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("test-repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        session.setPrUrl("https://github.com/org/test-repo/pull/42");

        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());

        when(julesSessionRepository.findByPrUrlIn(List.of("https://github.com/org/test-repo/pull/42")))
                .thenReturn(List.of(session));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(accountRepository.lockNextIdleAccountForProject(projectId)).thenReturn(Optional.of(account));

        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "html_url": "https://github.com/org/test-repo/pull/42",
                        "head": { "ref": "feature-branch" },
                        "additions": 100,
                        "deletions": 20,
                        "changed_files": 4
                    },
                    "repository": { "name": "test-repo" }
                }
                """;

        ResponseEntity<String> response = controller.handleWebhook(payload, "pull_request", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Review Dispatched for task " + taskId);

        verify(claimService).complete(taskId);
        verify(julesDispatchService).dispatch(task, account.getId(), "REVIEWER");
    }

    @Test
    @DisplayName("PR opened resolves task via branch UUID lineage when session not found")
    void prOpenedResolvesTaskViaBranchUuid() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setRepositoryName("test-repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.claimed);

        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());

        when(julesSessionRepository.findByPrUrlIn(anyList())).thenReturn(List.of());
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(accountRepository.lockNextIdleAccountForProject(projectId)).thenReturn(Optional.of(account));

        String payload = String.format("""
                {
                    "action": "opened",
                    "pull_request": {
                        "html_url": "https://github.com/org/test-repo/pull/99",
                        "head": { "ref": "feature/jules-%s-impl" },
                        "additions": 50,
                        "deletions": 10,
                        "changed_files": 2
                    },
                    "repository": { "name": "test-repo" }
                }
                """, taskId);

        ResponseEntity<String> response = controller.handleWebhook(payload, "pull_request", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Review Dispatched for task " + taskId);
        verify(claimService).complete(taskId);
        verify(julesDispatchService).dispatch(task, account.getId(), "REVIEWER");
    }

    @Test
    @DisplayName("PR opened with missing PR URL returns 400 Bad Request")
    void prOpenedWithMissingUrlReturns400() {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "html_url": "",
                        "head": { "ref": "feat" }
                    },
                    "repository": { "name": "repo" }
                }
                """;

        ResponseEntity<String> response = controller.handleWebhook(payload, "pull_request", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing required repository or PR URL");
    }
}
