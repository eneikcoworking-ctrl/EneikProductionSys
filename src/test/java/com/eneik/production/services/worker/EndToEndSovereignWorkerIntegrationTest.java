package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.PatchOperation;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import com.eneik.production.services.worker.verification.LocalVerificationRunner;
import com.eneik.production.services.worker.verification.VerificationResult;
import com.eneik.production.services.worker.worktree.FileScopeEnforcer;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EndToEndSovereignWorkerIntegrationTest {

    @Test
    @DisplayName("End-to-End Sovereign Worker: full lifecycle from task claim to PR creation")
    void testEndToEndSovereignWorkerLifecycle(@TempDir Path tempDir) throws Exception {
        DeepSeekApiClient mockClient = mock(DeepSeekApiClient.class);
        GitWorktreeExecutionManager mockWorktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer mockEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner mockVerifier = mock(LocalVerificationRunner.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        GitHubPullRequestService mockPrService = mock(GitHubPullRequestService.class);

        when(mockSessionRepo.save(any())).thenAnswer(inv -> {
            JulesSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        when(mockWorktreeManager.resolveBaseRepository(any())).thenReturn(tempDir);
        when(mockWorktreeManager.createWorktree(any(), any(), any())).thenReturn(tempDir);

        PatchOperation patch = new PatchOperation("src/main/A.java", "CREATE", "FULL_CONTENT", "class A{}", List.of());
        DeepSeekResponse mockResponse = new DeepSeekResponse(
                "Implemented feature A",
                "BARCAN-TAG-02 architectural alignment",
                ScopeStatus.SUFFICIENT,
                List.of(),
                List.of(patch),
                12000,
                500,
                150,
                "deepseek-chat",
                "{}"
        );

        when(mockClient.generateSolution(any(), any(), any(), any())).thenReturn(mockResponse);
        when(mockVerifier.runVerification(any(), any())).thenReturn(VerificationResult.success("ALL TESTS GREEN"));
        when(mockPrService.createPullRequest(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new GitHubPullRequestService.GitHubPullRequest("https://github.com/org/repo/pull/101", 101, "feat: Task", "worker/task", "bot", false, "main", false, null)));

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient,
                mockWorktreeManager,
                mockEnforcer,
                mockVerifier,
                mockSessionRepo,
                mockAccountRepo,
                mockPrService
        );

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Sovereign Feature A");
        task.setFileScope("[\"src/main/A.java\"]");
        task.setCynefinDomain("complicated");
        ProjectEntity project = new ProjectEntity();
        project.setName("client-product");
        task.setProject(project);

        AccountEntity account = new AccountEntity();
        account.setName("deepseek-worker-1");
        account.setProvider("DEEPSEEK");
        account.setApiKey("sk-test");

        // 1. Dispatch
        WorkerDispatchResult result = engine.dispatch(task, account, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(BelnapStatus.TRUE, result.belnapStatus());
        assertEquals("https://github.com/org/repo/pull/101", result.prUrl());

        // 2. Poll
        when(mockSessionRepo.findById(result.sessionId())).thenReturn(Optional.of(new JulesSessionEntity() {{
            setId(result.sessionId());
            setStatus("pr_opened");
            setBelnapStatus(BelnapStatus.TRUE.name());
            setPrUrl(result.prUrl());
        }}));

        WorkerPollResult pollResult = engine.poll(result.sessionId());
        assertNotNull(pollResult);
        assertTrue(pollResult.isCompleted());
        assertEquals(BelnapStatus.TRUE, pollResult.belnapStatus());
        assertEquals("https://github.com/org/repo/pull/101", pollResult.prUrl());

        // Verify worktree remove called in finally
        verify(mockWorktreeManager).removeWorktree(eq(tempDir), eq(tempDir));
    }
}
