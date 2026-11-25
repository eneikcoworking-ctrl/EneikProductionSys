package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.RoleCapabilityLoader;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.worker.agy.LocalAgyCliClient;
import com.eneik.production.services.worker.agy.LocalAgyWorktreeExecutionEngine;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import com.eneik.production.services.worker.verification.LocalVerificationRunner;
import com.eneik.production.services.worker.verification.VerificationResult;
import com.eneik.production.services.worker.worktree.FileScopeEnforcer;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Harness (D008 / D014 / D010 / D001 / D006) for Mechanism #14:
 * Memorilla (K=16) and dynamic BARCAN role charter wiring into Sovereign Workers (DeepSeek & LOCAL_AGY).
 */
class SovereignWorkerMemorillaWiringTest {

    // Guard 5-1 (DeepSeek Context Cache Invariance, D006)
    @Test
    void deepSeekSystemPromptPreservesExactOntologicalCachePrefixAtOffsetZeroAcrossRoles() {
        GeminiContextService gemini = mock(GeminiContextService.class);
        RoleCapabilityLoader charterLoader = mock(RoleCapabilityLoader.class);

        when(charterLoader.loadRawCharter("BARCAN-TAG-02")).thenReturn("# BARCAN-TAG-02 BACKEND CHARTER\nEnforce SQLite WAL mode.");
        when(charterLoader.loadRawCharter("BARCAN-TAG-06")).thenReturn("# BARCAN-TAG-06 QA CHARTER\nWrite Popperian falsification tests.");
        when(gemini.buildProductWorkerLatentMemoryBlock(argThat(r -> r != null && "BARCAN-TAG-02".equals(r.getTag())), anyString()))
                .thenReturn("MEMORILLA PRODUCT DOCTRINE MEMORY (K=2 orthogonal query-conditioned slots, Tarski L0-stratified):\n- [Slot #01 | BARCAN-TAG-02 | sim=0.91] D003 rigid API contract\n");
        when(gemini.buildProductWorkerLatentMemoryBlock(argThat(r -> r != null && "BARCAN-TAG-06".equals(r.getTag())), anyString()))
                .thenReturn("MEMORILLA PRODUCT DOCTRINE MEMORY (K=2 orthogonal query-conditioned slots, Tarski L0-stratified):\n- [Slot #01 | BARCAN-TAG-06 | sim=0.94] D008 falsification harness\n");

        SovereignWorkerContextAssembler assembler = new SovereignWorkerContextAssembler(gemini, charterLoader);
        DeepSeekApiClient apiClient = new DeepSeekApiClient();

        TaskEntity backendTask = buildTask("BARCAN-TAG-02", "Backend Developer", TargetContext.PRODUCT_CODEBASE);
        TaskEntity qaTask = buildTask("BARCAN-TAG-06", "QA Verifier", TargetContext.PRODUCT_CODEBASE);

        String backendDoctrine = assembler.assemble(backendTask, "BUILD");
        String qaDoctrine = assembler.assemble(qaTask, "BUILD");

        String backendPrompt = apiClient.buildSystemPrompt(backendTask, backendDoctrine);
        String qaPrompt = apiClient.buildSystemPrompt(qaTask, qaDoctrine);

        String prefix = DeepSeekApiClient.getOntologicalCachePrefix();
        assertTrue(backendPrompt.startsWith(prefix), "Backend systemPrompt must start byte-for-byte with BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX");
        assertTrue(qaPrompt.startsWith(prefix), "QA systemPrompt must start byte-for-byte with BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX");
        assertEquals(prefix, backendPrompt.substring(0, prefix.length()));
        assertEquals(prefix, qaPrompt.substring(0, prefix.length()));
        assertNotEquals(backendPrompt, qaPrompt, "Tail after cache prefix must differ per dynamic role and Memorilla slots");
    }

    // Guard 5-2 (Dynamic Role + Memorilla in DeepSeek, D001 / D014)
    @Test
    void deepSeekWorkerPromptAndEngineIncludeDynamicRoleCharterAndMemorillaBlock() throws Exception {
        GeminiContextService gemini = mock(GeminiContextService.class);
        RoleCapabilityLoader charterLoader = mock(RoleCapabilityLoader.class);

        when(charterLoader.loadRawCharter("BARCAN-TAG-02"))
                .thenReturn("## BARCAN-TAG-02 Backend Charter\n*Закон:* Every REST endpoint validates input DTOs.");
        when(gemini.buildProductWorkerLatentMemoryBlock(any(RoleEntity.class), anyString()))
                .thenReturn("MEMORILLA PRODUCT DOCTRINE MEMORY (K=1 orthogonal query-conditioned slots, Tarski L0-stratified):\n"
                        + "- [Slot #01 | BARCAN-TAG-02_BACKEND.md | sim=0.93] D003 contract-first Spring Boot endpoint\n");

        SovereignWorkerContextAssembler assembler = new SovereignWorkerContextAssembler(gemini, charterLoader);
        TaskEntity task = buildTask("BARCAN-TAG-02", "Backend Developer", TargetContext.PRODUCT_CODEBASE);

        String doctrine = assembler.assemble(task, "BUILD");
        DeepSeekApiClient realApiClient = new DeepSeekApiClient();
        String systemPrompt = realApiClient.buildSystemPrompt(task, doctrine);

        assertTrue(systemPrompt.contains("BARCAN-TAG-02"));
        assertTrue(systemPrompt.contains("Every REST endpoint validates input DTOs."));
        assertTrue(systemPrompt.contains("MEMORILLA PRODUCT DOCTRINE MEMORY"));
        assertTrue(systemPrompt.contains("D003 contract-first Spring Boot endpoint"));

        // Verify DeepSeekWorktreeExecutionEngine calls assembler once and passes doctrine to DeepSeekApiClient
        DeepSeekApiClient mockClient = mock(DeepSeekApiClient.class);
        GitWorktreeExecutionManager worktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer fileScopeEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner verificationRunner = mock(LocalVerificationRunner.class);
        JulesSessionRepository sessionRepo = mock(JulesSessionRepository.class);
        AccountRepository accountRepo = mock(AccountRepository.class);
        GitHubPullRequestService githubPrService = mock(GitHubPullRequestService.class);

        when(sessionRepo.findByTaskId(any())).thenReturn(List.of());
        when(sessionRepo.save(any(JulesSessionEntity.class))).thenAnswer(inv -> {
            JulesSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(worktreeManager.resolveBaseRepository(any())).thenReturn(Path.of("/tmp/base"));
        when(worktreeManager.createWorktree(any(), any(), any())).thenReturn(Path.of("/tmp/wt"));
        when(worktreeManager.listRepositoryFiles(any())).thenReturn(List.of("pom.xml", "src/main/java/App.java"));
        when(fileScopeEnforcer.parseDeclaredScope(any())).thenReturn(List.of("src/main/java/App.java"));
        when(worktreeManager.readScopedFiles(any(), any())).thenReturn("// App.java");
        when(mockClient.generateSolution(eq(task), anyString(), any(), isNull(), anyString()))
                .thenReturn(new DeepSeekResponse("ok", "ok", ScopeStatus.SUFFICIENT, List.of(), List.of(), 100, 200, 50, "deepseek-chat", "{}"));
        when(verificationRunner.runVerification(any(), any())).thenReturn(VerificationResult.success(10, "ok"));
        when(githubPrService.createPullRequest(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/42", 42, "feat: test", "open", "worker/branch", false, "clean", false, Instant.now())));

        DeepSeekWorktreeExecutionEngine engine = new DeepSeekWorktreeExecutionEngine(
                mockClient, worktreeManager, fileScopeEnforcer, verificationRunner,
                sessionRepo, accountRepo, githubPrService, assembler);

        WorkerDispatchResult result = engine.dispatch(task, new AccountEntity(), "BUILD");
        assertTrue(result.success());

        ArgumentCaptor<String> doctrineCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).generateSolution(eq(task), anyString(), any(), isNull(), doctrineCaptor.capture());
        assertTrue(doctrineCaptor.getValue().contains("MEMORILLA PRODUCT DOCTRINE MEMORY"));
        assertTrue(doctrineCaptor.getValue().contains("Every REST endpoint validates input DTOs."));
    }

    // Guard 5-3 (Dynamic Role + Memorilla in LOCAL_AGY, D001 / D013 — falsifies prior role blindness)
    @Test
    void localAgyPromptAndEngineInjectRoleTagCharterAndMemorillaBlock() throws Exception {
        GeminiContextService gemini = mock(GeminiContextService.class);
        RoleCapabilityLoader charterLoader = mock(RoleCapabilityLoader.class);

        when(charterLoader.loadRawCharter("BARCAN-TAG-06"))
                .thenReturn("## BARCAN-TAG-06 QA Verifier Charter\n*Идеал:* Falsify every acceptance criterion.");
        when(gemini.buildProductWorkerLatentMemoryBlock(any(RoleEntity.class), anyString()))
                .thenReturn("MEMORILLA PRODUCT DOCTRINE MEMORY (K=1 orthogonal query-conditioned slots, Tarski L0-stratified):\n"
                        + "- [Slot #01 | BARCAN-TAG-06_QA.md | sim=0.95] D008 Popper severe test invariant\n");

        SovereignWorkerContextAssembler assembler = new SovereignWorkerContextAssembler(gemini, charterLoader);
        TaskEntity task = buildTask("BARCAN-TAG-06", "QA Verifier", TargetContext.PRODUCT_CODEBASE);

        String doctrine = assembler.assemble(task, "VERIFY");
        LocalAgyCliClient realClient = new LocalAgyCliClient();
        String prompt = realClient.buildAgyPrompt(task, "// file content", null, doctrine);

        assertTrue(prompt.contains("BARCAN-TAG-06"), "LOCAL_AGY prompt must include dynamic BARCAN role tag");
        assertTrue(prompt.contains("QA Verifier"), "LOCAL_AGY prompt must include role description");
        assertTrue(prompt.contains("Falsify every acceptance criterion."), "LOCAL_AGY prompt must include Role Charter");
        assertTrue(prompt.contains("MEMORILLA PRODUCT DOCTRINE MEMORY"), "LOCAL_AGY prompt must include Memorilla K=16 block");
        assertTrue(prompt.contains("D008 Popper severe test invariant"));

        // Verify LocalAgyWorktreeExecutionEngine passes assembled doctrine to LocalAgyCliClient
        LocalAgyCliClient mockAgyClient = mock(LocalAgyCliClient.class);
        GitWorktreeExecutionManager worktreeManager = mock(GitWorktreeExecutionManager.class);
        FileScopeEnforcer fileScopeEnforcer = mock(FileScopeEnforcer.class);
        LocalVerificationRunner verificationRunner = mock(LocalVerificationRunner.class);
        JulesSessionRepository sessionRepo = mock(JulesSessionRepository.class);
        AccountRepository accountRepo = mock(AccountRepository.class);
        GitHubPullRequestService githubPrService = mock(GitHubPullRequestService.class);

        when(sessionRepo.findByTaskId(any())).thenReturn(List.of());
        when(sessionRepo.save(any(JulesSessionEntity.class))).thenAnswer(inv -> {
            JulesSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(worktreeManager.resolveBaseRepository(any())).thenReturn(Path.of("/tmp/base"));
        when(worktreeManager.createWorktree(any(), any(), any())).thenReturn(Path.of("/tmp/wt"));
        when(worktreeManager.listRepositoryFiles(any())).thenReturn(List.of("pom.xml"));
        when(fileScopeEnforcer.parseDeclaredScope(any())).thenReturn(List.of("pom.xml"));
        when(worktreeManager.readScopedFiles(any(), any())).thenReturn("<project/>");
        when(mockAgyClient.generateSolution(eq(task), anyString(), any(), isNull(), anyString()))
                .thenReturn(new DeepSeekResponse("ok", "ok", ScopeStatus.SUFFICIENT, List.of(), List.of(), 0, 0, 0, "local-agy", "{}"));
        when(verificationRunner.runVerification(any(), any())).thenReturn(VerificationResult.success(10, "ok"));
        when(githubPrService.createPullRequest(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new GitHubPullRequestService.GitHubPullRequest(
                        "https://github.com/org/repo/pull/7", 7, "feat: test", "open", "worker/branch", false, "clean", false, Instant.now())));

        LocalAgyWorktreeExecutionEngine engine = new LocalAgyWorktreeExecutionEngine(
                mockAgyClient, worktreeManager, fileScopeEnforcer, verificationRunner,
                sessionRepo, accountRepo, githubPrService, assembler);

        WorkerDispatchResult result = engine.dispatch(task, new AccountEntity(), "VERIFY");
        assertTrue(result.success());

        ArgumentCaptor<String> doctrineCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockAgyClient, times(1)).generateSolution(eq(task), anyString(), any(), isNull(), doctrineCaptor.capture());
        assertTrue(doctrineCaptor.getValue().contains("Falsify every acceptance criterion."));
        assertTrue(doctrineCaptor.getValue().contains("MEMORILLA PRODUCT DOCTRINE MEMORY"));
    }

    // Guard 5-4 (Tarski L0 Stratification, ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK D010)
    @Test
    void tarskiL0StratificationPreventsFactoryMetalanguageLeakageIntoProductWorkers() {
        GeminiContextService gemini = mock(GeminiContextService.class);
        RoleCapabilityLoader charterLoader = mock(RoleCapabilityLoader.class);
        when(charterLoader.loadRawCharter(anyString())).thenReturn("");
        when(gemini.buildProductWorkerLatentMemoryBlock(any(), anyString()))
                .thenReturn("MEMORILLA PRODUCT DOCTRINE MEMORY (K=1 orthogonal query-conditioned slots, Tarski L0-stratified):\n"
                        + "- [Slot #01 | engineering_charter | sim=0.90] Clean product boundary\n");
        when(gemini.buildLatentSemanticMemoryBlock(anyString()))
                .thenReturn("MEMORILLA LATENT SEMANTIC MEMORY (K=1 orthogonal query-conditioned slots from standing doctrine & mechanisms corpus):\n"
                        + "- [Slot #01 | observer_log | sim=0.96] Factory internal incident in AutoMergeService\n");

        SovereignWorkerContextAssembler assembler = new SovereignWorkerContextAssembler(gemini, charterLoader);

        // 1. PRODUCT_CODEBASE -> strictly product-worker stratified method
        TaskEntity productTask = buildTask("BARCAN-TAG-02", "Backend Developer", TargetContext.PRODUCT_CODEBASE);
        String productBlock = assembler.assemble(productTask, "BUILD");
        verify(gemini, times(1)).buildProductWorkerLatentMemoryBlock(eq(productTask.getRole()), anyString());
        verify(gemini, never()).buildLatentSemanticMemoryBlock(anyString());
        assertFalse(productBlock.contains("observer_log"), "observer_log must NEVER leak into product worker prompt");
        assertFalse(productBlock.contains("factory_mechanisms"), "factory_mechanisms must NEVER leak into product worker prompt");

        // 2. null / UNDETERMINED -> fail-safe to product-worker stratified method (never factory metalanguage)
        clearInvocations(gemini);
        TaskEntity nullContextTask = buildTask("BARCAN-TAG-02", "Backend Developer", null);
        String nullBlock = assembler.assemble(nullContextTask, "BUILD");
        verify(gemini, times(1)).buildProductWorkerLatentMemoryBlock(eq(nullContextTask.getRole()), anyString());
        verify(gemini, never()).buildLatentSemanticMemoryBlock(anyString());
        assertFalse(nullBlock.contains("observer_log"));

        // 3. ORCHESTRATOR_SYSTEM -> factory latent semantic memory block
        clearInvocations(gemini);
        TaskEntity factoryTask = buildTask("BARCAN-TAG-01", "System Architect", TargetContext.ORCHESTRATOR_SYSTEM);
        String factoryBlock = assembler.assemble(factoryTask, "BUILD");
        verify(gemini, times(1)).buildLatentSemanticMemoryBlock(anyString());
        verify(gemini, never()).buildProductWorkerLatentMemoryBlock(any(), anyString());
        assertTrue(factoryBlock.contains("MEMORILLA LATENT SEMANTIC MEMORY"));
    }

    // Guard 5-5 (Graceful Degradation & Full Description Preservation)
    @Test
    void gracefulDegradationNeverThrowsAndPreservesFullTaskDescriptionWhileCappingRoleCharter() {
        String longDescription = "CRITICAL_REQUIREMENT_START " + "detail ".repeat(900) + "CRITICAL_REQUIREMENT_END_MARKER";
        String hugeCharter = "# HUGE CHARTER\n" + "rule ".repeat(1500);

        // Case A: null dependencies
        SovereignWorkerContextAssembler nullAssembler = new SovereignWorkerContextAssembler(null, null);
        TaskEntity task = buildTask("BARCAN-TAG-02", "Backend Developer", TargetContext.PRODUCT_CODEBASE);
        task.setDescription(longDescription);

        String emptyDoctrine = assertDoesNotThrow(() -> nullAssembler.assemble(task, "BUILD"));
        assertEquals("", emptyDoctrine);

        DeepSeekApiClient dsClient = new DeepSeekApiClient();
        String dsUserPrompt = dsClient.buildUserPrompt(task, "", null);
        assertTrue(dsUserPrompt.contains(longDescription), "DeepSeek user prompt must preserve 100% of task.getDescription()");

        LocalAgyCliClient agyClient = new LocalAgyCliClient();
        String agyPrompt = agyClient.buildAgyPrompt(task, "", null, emptyDoctrine);
        assertTrue(agyPrompt.contains(longDescription), "LOCAL_AGY prompt must preserve 100% of task.getDescription()");

        // Case B: throwing dependencies -> graceful degradation
        GeminiContextService failingGemini = mock(GeminiContextService.class);
        RoleCapabilityLoader failingLoader = mock(RoleCapabilityLoader.class);
        when(failingGemini.buildProductWorkerLatentMemoryBlock(any(), anyString())).thenThrow(new RuntimeException("ML offline"));
        when(failingLoader.loadRawCharter(anyString())).thenThrow(new RuntimeException("Rules file missing"));

        SovereignWorkerContextAssembler resilientAssembler = new SovereignWorkerContextAssembler(failingGemini, failingLoader);
        String degraded = assertDoesNotThrow(() -> resilientAssembler.assemble(task, "BUILD"));
        assertEquals("", degraded);

        // Case C: oversized role charter is capped <= 4000 chars while task description remains untouched
        GeminiContextService emptyGemini = mock(GeminiContextService.class);
        RoleCapabilityLoader bigCharterLoader = mock(RoleCapabilityLoader.class);
        when(emptyGemini.buildProductWorkerLatentMemoryBlock(any(), anyString())).thenReturn("");
        when(bigCharterLoader.loadRawCharter("BARCAN-TAG-02")).thenReturn(hugeCharter);

        SovereignWorkerContextAssembler cappedAssembler = new SovereignWorkerContextAssembler(emptyGemini, bigCharterLoader);
        String cappedDoctrine = cappedAssembler.assemble(task, "BUILD");
        assertTrue(cappedDoctrine.contains("charter capped at 4000 chars"));
        assertTrue(cappedDoctrine.length() < 4300, "Assembled charter block must stay bounded around 4000 chars");
        assertEquals(longDescription, task.getDescription(), "Task description must never be mutated or truncated");
    }

    private TaskEntity buildTask(String roleTag, String roleDesc, TargetContext targetContext) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("sample-product");
        project.setDefaultBranch("main");

        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        role.setDescription(roleDesc);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setRole(role);
        task.setTargetContext(targetContext);
        task.setTitle("Implement Chat API");
        task.setDescription("Implement /api/chat endpoint with SQLite persistence and validation.");
        task.setFileScope("[\"src/main/java/App.java\"]");
        task.setCynefinDomain("complicated");
        return task;
    }
}
