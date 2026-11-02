package com.eneik.production.services.worker.agy;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import com.eneik.production.services.worker.verification.LocalVerificationRunner;
import com.eneik.production.services.worker.worktree.FileScopeEnforcer;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalAgyWorktreeExecutionEngineTest {

    @Test
    @DisplayName("dispatch initializes session with non-null externalSessionId and skips maven for carrier task")
    void testDispatch(@TempDir Path tempDir) throws Exception {
        LocalAgyCliClient agyClient = mock(LocalAgyCliClient.class);
        GitWorktreeExecutionManager worktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer fileScopeEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner verificationRunner = mock(LocalVerificationRunner.class);
        JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        GitHubPullRequestService githubPrService = mock(GitHubPullRequestService.class);

        LocalAgyWorktreeExecutionEngine engine = new LocalAgyWorktreeExecutionEngine(
                agyClient, worktreeManager, fileScopeEnforcer, verificationRunner, sessionRepository, accountRepository, githubPrService
        );

        TaskEntity task = new TaskEntity();
        UUID taskId = UUID.randomUUID();
        task.setId(taskId);
        task.setTitle("Compiler Task");
        task.setFileScope("[\".eneik/records/task-plan-1.json\"]");
        ProjectEntity project = new ProjectEntity();
        project.setName("test-product");
        task.setProject(project);

        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.createObjectNode();
        payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, TaskEntity.WISHLIST_COMPILER_TASK_TYPE);
        task.setPayload(payload);

        AccountEntity account = new AccountEntity();
        account.setName("remotecli3");

        when(sessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> {
            JulesSessionEntity s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        when(worktreeManager.resolveBaseRepository(any())).thenReturn(tempDir);
        when(worktreeManager.createWorktree(any(), any(), any())).thenReturn(tempDir);
        when(fileScopeEnforcer.parseDeclaredScope(any())).thenReturn(List.of(".eneik/records/task-plan-1.json"));
        when(worktreeManager.readScopedFiles(any(), any())).thenReturn("{}");

        DeepSeekResponse mockResponse = new DeepSeekResponse("Compiled", "OK", ScopeStatus.SUFFICIENT, List.of(), List.of(), 0, 0, 0, "gemini", "{}");
        when(agyClient.generateSolution(any(), any(), any(), any())).thenReturn(mockResponse);

        when(githubPrService.createPullRequest(any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of(new GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/owner/repo/pull/1", 1, "title", "head", "author", false, "base", false, java.time.Instant.now()
                )));

        var result = engine.dispatch(task, account, "NORMAL");

        // Verify session was saved with non-null externalSessionId
        ArgumentCaptor<JulesSessionEntity> captor = ArgumentCaptor.forClass(JulesSessionEntity.class);
        verify(sessionRepository, atLeastOnce()).save(captor.capture());
        JulesSessionEntity firstSaved = captor.getAllValues().get(0);
        assertNotNull(firstSaved.getExternalSessionId(), "ExternalSessionId must never be null");
        assertTrue(firstSaved.getExternalSessionId().startsWith("local-agy-"));

        // Verify verificationRunner was NEVER called because task is carrier/compiler
        verify(verificationRunner, never()).runVerification(any(), any());

        assertNotNull(result);
        assertNotNull(result.externalSessionId());
        assertTrue(result.externalSessionId().startsWith("local-agy-"));
    }
}
