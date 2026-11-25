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
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeepSeekWorktreeExecutionEngineTest {

    @Test
    @DisplayName("Gate 4A-1 (D010): PR creation failure (422 no history in common) never returns fake local-repo/pulls URL and resyncs mirror")
    void prCreationFailureNeverFakesLocalRepoUrlAndTriggersMirrorResync(@TempDir Path tempDir) throws Exception {
        DeepSeekApiClient mockClient = mock(DeepSeekApiClient.class);
        GitWorktreeExecutionManager mockWorktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer mockEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner mockVerifier = mock(LocalVerificationRunner.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        GitHubPullRequestService mockPrService = mock(GitHubPullRequestService.class);

        List<JulesSessionEntity> savedStates = new ArrayList<>();
        when(mockSessionRepo.save(any())).thenAnswer(inv -> {
            JulesSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            savedStates.add(s);
            return s;
        });

        when(mockWorktreeManager.resolveBaseRepository(any())).thenReturn(tempDir);
        when(mockWorktreeManager.createWorktree(any(), any(), any())).thenReturn(tempDir);
        when(mockWorktreeManager.listRepositoryFiles(any())).thenReturn(List.of("pom.xml", "src/main/java/App.java"));
        when(mockEnforcer.parseDeclaredScope(any())).thenReturn(List.of("src/main/java/App.java"));

        DeepSeekResponse response = new DeepSeekResponse(
                "Fix backend", "rationale", ScopeStatus.SUFFICIENT, List.of(),
                List.of(new PatchOperation("src/main/java/App.java", "MODIFY", "FULL_CONTENT", "class App {}", List.of())),
                100, 50, 20, "deepseek-chat", "{}"
        );
        when(mockClient.generateSolution(any(), any(), any(), any())).thenReturn(response);
        when(mockVerifier.runVerification(any(), any())).thenReturn(VerificationResult.success("BUILD SUCCESS"));

        // Simulate GitHub HTTP 422 "no history in common with main"
        when(mockPrService.createPullRequest(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(mockPrService.lastCreatePullRequestFailure()).thenReturn(Optional.of(
                new GitHubPullRequestService.PullRequestCreationFailure(422, "Validation Failed: no history in common with main", true)));

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient, mockWorktreeManager, mockEnforcer, mockVerifier,
                mockSessionRepo, mockAccountRepo, mockPrService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Backend fix");
        task.setProject(project);

        WorkerDispatchResult result = engine.dispatch(task, null, "IMPLEMENTER");

        assertNotNull(result);
        assertFalse(result.success(), "Must NOT return COMPLETED when GitHub PR creation fails");
        assertEquals(BelnapStatus.FALSE, result.belnapStatus());
        assertNull(result.prUrl(), "Must NEVER synthesize fake local-repo/pulls URL");
        assertTrue(result.errorMessage().contains("422"), "Error message must carry HTTP 422 diagnostic");
        assertTrue(result.errorMessage().contains("no history in common"), "Error message must mention no history in common");

        verify(mockWorktreeManager, times(1)).resyncMirrorWithOriginMain(project);

        JulesSessionEntity lastSaved = savedStates.get(savedStates.size() - 1);
        assertEquals("pr_creation_failed", lastSaved.getStatus());
        assertEquals(BelnapStatus.FALSE.name(), lastSaved.getBelnapStatus());
        assertNull(lastSaved.getPrUrl());
        assertFalse(String.valueOf(lastSaved.getPrUrl()).contains("local-repo/pulls"));
    }

    @Test
    @DisplayName("Gate 4A-1b (D010): git push failure aborts PR publication and never fakes success")
    void pushFailureAbortsPullRequestCreation(@TempDir Path tempDir) throws Exception {
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
        when(mockWorktreeManager.listRepositoryFiles(any())).thenReturn(List.of("pom.xml"));
        doThrow(new RuntimeException("git push failed with exit code 128")).when(mockWorktreeManager).push(any(), any());

        DeepSeekResponse response = new DeepSeekResponse(
                "Fix", "rat", ScopeStatus.SUFFICIENT, List.of(), List.of(), 10, 10, 10, "deepseek-chat", "{}");
        when(mockClient.generateSolution(any(), any(), any(), any())).thenReturn(response);
        when(mockVerifier.runVerification(any(), any())).thenReturn(VerificationResult.success("OK"));

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient, mockWorktreeManager, mockEnforcer, mockVerifier,
                mockSessionRepo, mockAccountRepo, mockPrService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Push fail test");
        task.setProject(project);

        WorkerDispatchResult result = engine.dispatch(task, null, "IMPLEMENTER");

        assertFalse(result.success());
        assertNull(result.prUrl());
        verify(mockPrService, never()).createPullRequest(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Gate 4B (D003/D002): Passes repo file tree and classifies foreign *.py SCOPE_INSUFFICIENT in Java/Maven repo as POISONED_CROSS_STACK_SCOPE_INSUFFICIENT")
    void classifiesCrossStackPythonScopeInsufficientInJavaMavenRepo(@TempDir Path tempDir) throws Exception {
        DeepSeekApiClient mockClient = mock(DeepSeekApiClient.class);
        GitWorktreeExecutionManager mockWorktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer mockEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner mockVerifier = mock(LocalVerificationRunner.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        GitHubPullRequestService mockPrService = mock(GitHubPullRequestService.class);

        List<JulesSessionEntity> savedStates = new ArrayList<>();
        when(mockSessionRepo.save(any())).thenAnswer(inv -> {
            JulesSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            savedStates.add(s);
            return s;
        });

        when(mockWorktreeManager.resolveBaseRepository(any())).thenReturn(tempDir);
        when(mockWorktreeManager.createWorktree(any(), any(), any())).thenReturn(tempDir);
        when(mockWorktreeManager.listRepositoryFiles(any())).thenReturn(List.of(
                "pom.xml",
                "src/main/java/ru/crie/CrieApplication.java",
                "src/main/java/ru/crie/api/ChatController.java"
        ));
        when(mockEnforcer.parseDeclaredScope(any())).thenReturn(List.of());

        DeepSeekResponse scopeResponse = new DeepSeekResponse(
                "Missing python files", "rat", ScopeStatus.SCOPE_INSUFFICIENT,
                List.of("crie_persistent_memory.py", "crie_document_parser.py", "crie_assistant_api.py"),
                List.of(), 100, 50, 20, "deepseek-chat", "{}"
        );
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        when(mockClient.generateSolution(any(), contextCaptor.capture(), any(), any())).thenReturn(scopeResponse);

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient, mockWorktreeManager, mockEnforcer, mockVerifier,
                mockSessionRepo, mockAccountRepo, mockPrService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Legacy Python task");
        task.setProject(project);

        WorkerDispatchResult result = engine.dispatch(task, null, "IMPLEMENTER");

        assertTrue(contextCaptor.getValue().contains("REPOSITORY FILE TREE"),
                "Prompt context must include git ls-files repository tree");
        assertTrue(contextCaptor.getValue().contains("pom.xml"));

        assertFalse(result.success());
        assertTrue(result.errorMessage().startsWith("POISONED_CROSS_STACK_SCOPE_INSUFFICIENT"),
                "Expected POISONED_CROSS_STACK_SCOPE_INSUFFICIENT, got: " + result.errorMessage());

        JulesSessionEntity lastSaved = savedStates.get(savedStates.size() - 1);
        assertEquals("scope_insufficient", lastSaved.getStatus());
        assertEquals(BelnapStatus.FALSE.name(), lastSaved.getBelnapStatus());
        assertTrue(lastSaved.getClosureReason().contains("POISONED_CROSS_STACK_SCOPE_INSUFFICIENT"));
    }

    @Test
    @DisplayName("Gate 4A-1c: Reuses pre-saved local-agy-* session from JulesDispatchService without leaving orphaned running session")
    void reusesPreSavedSessionFromJulesDispatchService(@TempDir Path tempDir) throws Exception {
        DeepSeekApiClient mockClient = mock(DeepSeekApiClient.class);
        GitWorktreeExecutionManager mockWorktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer mockEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner mockVerifier = mock(LocalVerificationRunner.class);
        JulesSessionRepository mockSessionRepo = mock(JulesSessionRepository.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        GitHubPullRequestService mockPrService = mock(GitHubPullRequestService.class);

        UUID taskId = UUID.randomUUID();
        UUID preSavedId = UUID.randomUUID();
        JulesSessionEntity preSaved = new JulesSessionEntity();
        preSaved.setId(preSavedId);
        preSaved.setTaskId(taskId);
        preSaved.setStatus("running");
        preSaved.setExternalSessionId("local-agy-" + UUID.randomUUID());
        preSaved.setWorkerEngine("HYBRID");

        when(mockSessionRepo.findByTaskId(taskId)).thenReturn(List.of(preSaved));
        when(mockSessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mockWorktreeManager.resolveBaseRepository(any())).thenReturn(tempDir);
        when(mockWorktreeManager.createWorktree(any(), any(), any())).thenReturn(tempDir);
        when(mockWorktreeManager.listRepositoryFiles(any())).thenReturn(List.of("pom.xml"));

        DeepSeekResponse response = new DeepSeekResponse(
                "Done", "rat", ScopeStatus.SUFFICIENT, List.of(), List.of(), 10, 10, 10, "deepseek-chat", "{}");
        when(mockClient.generateSolution(any(), any(), any(), any())).thenReturn(response);
        when(mockVerifier.runVerification(any(), any())).thenReturn(VerificationResult.success("OK"));
        when(mockPrService.createPullRequest(any(), any(), any(), any(), any())).thenReturn(Optional.of(
                new GitHubPullRequestService.GitHubPullRequest("https://github.com/org/repo/pull/7", 7, "feat", "worker/t", "bot", false, "main", false, null)));

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient, mockWorktreeManager, mockEnforcer, mockVerifier,
                mockSessionRepo, mockAccountRepo, mockPrService);

        ProjectEntity project = new ProjectEntity();
        project.setName("knowledge-base-crie");
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setTitle("Reuse session");
        task.setProject(project);

        WorkerDispatchResult result = engine.dispatch(task, null, "IMPLEMENTER");

        assertTrue(result.success());
        assertEquals(preSavedId, result.sessionId(), "Must reuse pre-saved session ID");
        assertEquals("pr_opened", preSaved.getStatus());
        assertEquals("DEEPSEEK_WORKTREE", preSaved.getWorkerEngine());
        assertTrue(preSaved.getExternalSessionId().startsWith("deepseek-"));
    }
}
