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

class DennettMultipleDraftsSelectorTest {

    @Test
    @DisplayName("Multiple Drafts Selection: draft #1 fails compilation, draft #2 passes and is selected")
    void testMultipleDraftsSelection(@TempDir Path tempDir) throws Exception {
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

        // Draft 1 response and failure
        DeepSeekResponse draft1 = new DeepSeekResponse("Draft 1", "barcan", ScopeStatus.SUFFICIENT, List.of(), List.of(), 100, 50, 20, "deepseek-chat", "raw1");
        // Draft 2 response and success
        DeepSeekResponse draft2 = new DeepSeekResponse("Draft 2", "barcan", ScopeStatus.SUFFICIENT, List.of(), List.of(), 100, 50, 20, "deepseek-chat", "raw2");

        when(mockClient.generateSolution(any(), any(), any(), isNull())).thenReturn(draft1);
        when(mockClient.generateSolution(any(), any(), any(), anyString())).thenReturn(draft2);

        // Verification: 1st draft fails, 2nd draft passes
        VerificationResult failResult = VerificationResult.failure(1, "Syntax error", "SYNTAX_ERR");
        VerificationResult passResult = VerificationResult.success("BUILD SUCCESS");

        when(mockVerifier.runVerification(any(), any()))
                .thenReturn(failResult)
                .thenReturn(passResult);

        when(mockPrService.createPullRequest(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new GitHubPullRequestService.GitHubPullRequest("https://github.com/pull/42", 42, "feat", "worker/task", "bot", false, "main", false, null)));

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
        task.setTitle("Multiple Drafts Test");
        ProjectEntity project = new ProjectEntity();
        project.setName("TestProject");
        task.setProject(project);

        WorkerDispatchResult result = engine.dispatch(task, null, "IMPLEMENTER");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(BelnapStatus.TRUE, result.belnapStatus());
        assertEquals("https://github.com/pull/42", result.prUrl());

        // Verify client called twice (both drafts)
        verify(mockClient, times(2)).generateSolution(any(), any(), any(), any());
        // Verify verifier called twice
        verify(mockVerifier, times(2)).runVerification(any(), any());
        // Verify worktree rollback called after draft 1 failure
        verify(mockEnforcer, times(1)).rollbackWorktree(any());
    }
}
