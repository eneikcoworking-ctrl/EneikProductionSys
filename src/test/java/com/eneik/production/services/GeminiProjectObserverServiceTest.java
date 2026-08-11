package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testimony-vs-evidence, applied to the periodic observer itself (2026-07-25 redesign): it reasons over a
 * structured evidence snapshot of the project's real current state, never the backend's own internal log,
 * and keeps its OWN journal for cross-cycle continuity rather than consuming the backend's. Every raised
 * finding is still deduped against already-live wishlists the same way every other generative track is.
 *
 * 2026-08-05 (Phase 5): mlPredictionServiceClient.chat(...) replaced by chatWithTools(...) in production
 * code - a fully-mocked mlPredictionServiceClient here means chatWithTools' real loop body (which would
 * invoke the executor/continuation lambdas) never runs; Mockito just returns the stubbed ToolLoopResult
 * directly, so these tests exercise everything AROUND the LLM call exactly as before, just with the new
 * method name/return type.
 */
class GeminiProjectObserverServiceTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private ClientDeliverableReadinessService readinessService;
    private GeminiObserverJournalRepository journalRepository;
    private GeminiObserverActionRepository actionRepository;
    private GeminiContextService geminiContextService;
    private MLPredictionServiceClient mlPredictionServiceClient;
    private WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private GeminiObserverActionService actionService;
    private FalsificationCycleService falsificationCycleService;
    private SystemSettingsService settingsService;
    private com.eneik.production.services.github.GitHubApiBudgetService gitHubApiBudgetService;
    private com.eneik.production.services.operational.OperationalFlowCoreService operationalFlowCoreService;
    private com.eneik.production.kaizen.service.KaizenService kaizenService;
    private com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;
    private ProjectEventLogService projectEventLogService;
    private com.eneik.production.services.audit.SixSigmaAuditService sixSigmaAuditService;
    private EvidenceNodeRepository evidenceNodeRepository;
    private CoherenceRunRepository coherenceRunRepository;
    private com.eneik.production.repositories.CoherenceRunNodeResultRepository coherenceRunNodeResultRepository;
    private com.eneik.production.repositories.OperationalRealityFindingRepository operationalRealityFindingRepository;
    private PersistentWorkerSessionService persistentWorkerSessionService;
    private GeminiProjectObserverService service;

    private void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        readinessService = mock(ClientDeliverableReadinessService.class);
        journalRepository = mock(GeminiObserverJournalRepository.class);
        actionRepository = mock(GeminiObserverActionRepository.class);
        geminiContextService = mock(GeminiContextService.class);
        mlPredictionServiceClient = mock(MLPredictionServiceClient.class);
        wishlistContentSimilarityMatcher = mock(WishlistContentSimilarityMatcher.class);
        actionService = mock(GeminiObserverActionService.class);
        falsificationCycleService = mock(FalsificationCycleService.class);
        settingsService = mock(SystemSettingsService.class);
        gitHubApiBudgetService = mock(com.eneik.production.services.github.GitHubApiBudgetService.class);
        operationalFlowCoreService = mock(com.eneik.production.services.operational.OperationalFlowCoreService.class);
        kaizenService = mock(com.eneik.production.kaizen.service.KaizenService.class);
        branchGarbageCollectorService = mock(com.eneik.production.services.orchestration.BranchGarbageCollectorService.class);
        projectEventLogService = mock(ProjectEventLogService.class);
        sixSigmaAuditService = mock(com.eneik.production.services.audit.SixSigmaAuditService.class);
        evidenceNodeRepository = mock(EvidenceNodeRepository.class);
        coherenceRunRepository = mock(CoherenceRunRepository.class);
        coherenceRunNodeResultRepository = mock(com.eneik.production.repositories.CoherenceRunNodeResultRepository.class);
        operationalRealityFindingRepository = mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class);
        persistentWorkerSessionService = mock(PersistentWorkerSessionService.class);
        when(actionRepository.findTop5ByProjectIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(falsificationCycleService.philosophicalReadinessInfo(any()))
                .thenReturn(new FalsificationCycleService.PhilosophicalReadinessInfo(0.9, false));
        // Both new evidence-enrichment calls are wrapped in try/catch in production code (best-effort, like
        // the hotspot lookups elsewhere) specifically so an unstubbed mock here (-> null -> NPE inside the
        // try) degrades to "signal omitted", not a test failure - deliberately NOT stubbed by default so
        // every existing test keeps passing unchanged; only the two tests that care about this new signal
        // stub it explicitly. Same reasoning applies to findOrphanedPrCandidates/projectEventLogService.since
        // below - unstubbed, Mockito's default for a List-returning method is an empty list anyway, so both
        // degrade to "no signal" with no extra stubbing needed for every other existing test.
        service = new GeminiProjectObserverService(projectRepository, wishlistRepository, taskRepository,
                readinessService, journalRepository, actionRepository, geminiContextService, mlPredictionServiceClient,
                wishlistContentSimilarityMatcher, actionService, falsificationCycleService, settingsService,
                gitHubApiBudgetService, operationalFlowCoreService, kaizenService, branchGarbageCollectorService,
                projectEventLogService, sixSigmaAuditService, evidenceNodeRepository, coherenceRunRepository,
                coherenceRunNodeResultRepository, operationalRealityFindingRepository, persistentWorkerSessionService);
    }

    private ProjectEntity project() {
        ProjectEntity p = new ProjectEntity();
        p.setId(UUID.randomUUID());
        p.setName("observed-project");
        p.setStatus(ProjectStatus.active);
        p.setCreatedAt(Instant.now().minusSeconds(3600));
        return p;
    }

    private void stubCommonEvidence(ProjectEntity project) {
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");
    }

    private static MLPredictionServiceClient.ToolLoopResult toolResult(String text) {
        return new MLPredictionServiceClient.ToolLoopResult(text, 1, false);
    }

    private static void stubChat(MLPredictionServiceClient client, String jsonText) {
        when(client.chatWithTools(anyString(), anyString(), any(), any(), any(), anyInt()))
                .thenReturn(toolResult(jsonText));
    }

    @Test
    void doesNothingWhenFeatureFlagIsOff() {
        setUp();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project()));

        service.runObserverCycle();

        verify(mlPredictionServiceClient, never()).chatWithTools(anyString(), anyString(), any(), any(), any(), anyInt());
        verifyNoInteractions(taskRepository);
    }

    @Test
    void firstCycleAlwaysCallsGeminiToEstablishABaselineJournal() {
        // No prior journal entry exists yet - the very first cycle for a project must always call Gemini
        // once (even with zero task activity) so a baseline journal entry exists for later cycles to skip
        // against. Uses the cheap flash tier, not chatCritical/pro - this is a conservative noticing
        // task, not a merge-gating decision.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        stubChat(mlPredictionServiceClient, "{\"journalEntry\": \"Nothing notable this cycle.\", \"findings\": []}");

        service.runObserverCycle();

        verify(mlPredictionServiceClient).chatWithTools(anyString(), anyString(), any(), any(), any(), anyInt());
        ArgumentCaptor<GeminiObserverJournalEntity> captor = ArgumentCaptor.forClass(GeminiObserverJournalEntity.class);
        verify(journalRepository).save(captor.capture());
        assertEquals("Nothing notable this cycle.", captor.getValue().getEntry());
        assertEquals(0, captor.getValue().getFindingsCount());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void skipsGeminiCallEntirelyWhenNothingChangedSinceLastVisit() {
        // Real cost lever (2026-07-25 operator directive): once a baseline journal entry exists, a cycle
        // with zero done/failed tasks since then must not call Gemini at all - not even to hear "nothing
        // new". This is the biggest token saver for a stable/idle project.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: everything looked healthy.");
        priorEntry.setFindingsCount(0);
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        service.runObserverCycle();

        // No LLM call - but a cheap, code-only skip marker is still written (2026-07-30) so the readiness-
        // ratio/anomaly-fingerprint history keeps accumulating across skipped cycles instead of the
        // stagnation detector being permanently starved of the 3+ observations it needs.
        verifyNoInteractions(mlPredictionServiceClient);
        ArgumentCaptor<GeminiObserverJournalEntity> captor = ArgumentCaptor.forClass(GeminiObserverJournalEntity.class);
        verify(journalRepository).save(captor.capture());
        assertEquals(false, captor.getValue().isGeminiCalled());
        assertEquals("", captor.getValue().getEntry());
    }

    @Test
    void neverWritesToHerJournalWhenGeminiResponseCannotBeParsed() {
        // Direct regression for the operator's correction: "так теперь ты сам ничего не пиши в его лог" -
        // a parse failure must skip the cycle entirely, never substitute a Claude-authored fallback string
        // into her journal.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        stubChat(mlPredictionServiceClient, "not valid JSON at all");

        service.runObserverCycle();

        verify(journalRepository, never()).save(any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void promptIncludesEvidenceSnapshotAndOwnPriorJournalNotRawLog() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        TaskEntity failedTask = new TaskEntity();
        failedTask.setTitle("Flaky migration task");
        failedTask.setStatus(TaskStatus.failed);
        failedTask.setUpdatedAt(Instant.now());
        failedTask.setJulesDispatchStatus("PR closed without merge");
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(failedTask));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(2, 1, 0.5));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: everything looked healthy.");
        priorEntry.setFindingsCount(0);
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        stubChat(mlPredictionServiceClient, "{\"journalEntry\": \"Noted the failed migration task.\", \"findings\": []}");

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chatWithTools(promptCaptor.capture(), anyString(), any(), any(), any(), anyInt());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Previously: everything looked healthy."));
        assertTrue(prompt.contains("Flaky migration task"));
        assertTrue(prompt.contains("PR closed without merge"));
        assertTrue(prompt.contains("1/2 individual work items merged"));
    }

    @Test
    void raisesAWishlistForEachGenuinelyNewFinding() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        when(wishlistContentSimilarityMatcher.findLikelyDuplicate(any(), anyString())).thenReturn(java.util.Optional.empty());
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Found a real recurring failure.",
                 "findings": [{"summary": "Task X has failed the same way 6 times in a row", "evidence": "6 identical failures", "severity": "high"}]}
                """);

        service.runObserverCycle();

        ArgumentCaptor<WishlistEntity> captor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(captor.capture());
        WishlistEntity saved = captor.getValue();
        assertEquals(WishlistSource.gemini_observer, saved.getSource());
        assertEquals(WishlistStatus.pending, saved.getStatus());
        assertEquals(project.getId(), saved.getProjectId());
        assertTrue(saved.getContent().contains("failed the same way 6 times"));

        ArgumentCaptor<GeminiObserverJournalEntity> journalCaptor = ArgumentCaptor.forClass(GeminiObserverJournalEntity.class);
        verify(journalRepository).save(journalCaptor.capture());
        assertEquals(1, journalCaptor.getValue().getFindingsCount());
    }

    @Test
    void platformScopeFindingGoesToKaizenNeverBecomesAWishlist() {
        // 2026-08-01 regression test: a finding about the orchestrator/factory's OWN code (e.g. "Fix
        // pending_review state transition logic") used to become a normal gemini_observer wishlist, which
        // dispatched a Jules session against THIS project's own repo - where the thing it's meant to fix
        // does not exist, an unfixable no-op. Must go to KaizenService's review-only queue instead.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        when(wishlistContentSimilarityMatcher.findLikelyDuplicate(any(), anyString())).thenReturn(java.util.Optional.empty());
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Found a bug in the orchestrator itself, not this project.",
                 "findings": [{"summary": "pending_review tasks never transition automatically", "evidence": "task stuck 6h", "severity": "high", "scope": "platform"}]}
                """);

        service.runObserverCycle();

        verify(wishlistRepository, never()).save(any());
        verify(kaizenService).recordSystemicDefectProposal(eq(project.getId()), eq(project.getName()),
                contains("pending_review tasks never transition automatically"), anyString());
    }

    @Test
    void skipsAFindingThatDuplicatesAnAlreadyLiveWishlist() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");
        WishlistEntity existing = new WishlistEntity();
        existing.setId(UUID.randomUUID());
        existing.setStatus(WishlistStatus.pending);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of(existing));
        when(wishlistContentSimilarityMatcher.findLikelyDuplicate(any(), anyString())).thenReturn(java.util.Optional.of(existing.getId()));
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Same known problem as before.",
                 "findings": [{"summary": "Already known problem", "evidence": "same as before", "severity": "low"}]}
                """);

        service.runObserverCycle();

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void duplicateDescriptionWarningForcesACycleEvenWithNoRecentActivity() {
        // Direct regression test for the 2026-07-26 live incident: a hardcoded bug generated 9 tasks with
        // the exact same description over 2 hours - invisible to the old evidence snapshot (status counts
        // only). This deterministic check must both surface the pattern in the snapshot text AND force a
        // real Gemini cycle even when nothingChanged would otherwise skip it (no done/failed tasks).
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        String junkDescription = "Kano Refactoring: Implement Redis caching for API queries to optimize performance";
        List<TaskEntity> duplicates = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TaskEntity t = new TaskEntity();
            t.setId(UUID.randomUUID());
            t.setDescription(junkDescription);
            t.setStatus(TaskStatus.review);
            duplicates.add(t);
        }
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(duplicates);
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        // A prior journal entry exists - without the duplicate check, nothingChanged would be true here
        // (no done/failed tasks) and the cycle would be skipped entirely.
        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: nothing notable.");
        priorEntry.setFindingsCount(0);
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");
        when(wishlistContentSimilarityMatcher.findLikelyDuplicate(any(), anyString())).thenReturn(java.util.Optional.empty());
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Found 3 tasks sharing an identical description - flagging as a real bug.",
                 "findings": [{"summary": "3 duplicate tasks detected", "evidence": "identical description", "severity": "high"}]}
                """);

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chatWithTools(promptCaptor.capture(), anyString(), any(), any(), any(), anyInt());
        assertTrue(promptCaptor.getValue().contains("DUPLICATE TASK WARNING"));
        assertTrue(promptCaptor.getValue().contains("3 tasks share the same content key"));
        // 2026-08-07: collapseDuplicateTask needs a real target id, not just a count/snippet - the warning
        // must expose the actual task ids so Gemini can act on it, not only report it.
        for (TaskEntity duplicate : duplicates) {
            assertTrue(promptCaptor.getValue().contains(duplicate.getId().toString()));
        }
    }

    @Test
    void stagnationWarningFiresOnlyWhenReadinessRatioHeldFlatAcrossSeveralPriorCycles() {
        // 2026-07-26 addition: a project that's genuinely making progress but happens to have zero
        // done/failed status changes right this cycle must NOT be flagged as stagnant - only when her own
        // last several journal entries recorded essentially the same ratio as right now.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(2, 1, 0.5));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        List<GeminiObserverJournalEntity> flatHistory = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            GeminiObserverJournalEntity entry = new GeminiObserverJournalEntity();
            entry.setProjectId(project.getId());
            entry.setCreatedAt(Instant.now().minusSeconds(1800L * (i + 1)));
            entry.setEntry("Cycle " + i + ": readiness unchanged.");
            entry.setReadinessRatio(0.5);
            flatHistory.add(entry);
        }
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(flatHistory);
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Confirmed readiness has not moved - flagging stagnation.",
                 "findings": [], "actions": []}
                """);

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chatWithTools(promptCaptor.capture(), anyString(), any(), any(), any(), anyInt());
        assertTrue(promptCaptor.getValue().contains("STAGNATION WARNING"));
    }

    @Test
    void executesAProposedActionThroughTheActionServiceAndCapsPerRun() {
        // 2026-07-26 operator directive ("даем ей все полномочия - кроме кода"): a proposed action must be
        // dispatched to the real, guarded action service - never executed inline, never trusted blindly.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        UUID wishlistId = UUID.randomUUID();
        when(actionService.dismissWishlist(eq(project), eq(wishlistId.toString()), anyString()))
                .thenReturn("success");
        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Dismissed a clearly dead wishlist item.", "findings": [],
                 "actions": [{"tool": "dismissWishlist", "targetId": "%s", "reason": "listed as stale, superseded"}]}
                """.formatted(wishlistId));

        service.runObserverCycle();

        verify(actionService).dismissWishlist(eq(project), eq(wishlistId.toString()), anyString());
    }

    // --- Journal continuity fix regression tests (2026-07-30, test-fortieth incident) -----------------------

    @Test
    void stagnationDetectorCountsSkipMarkersNotJustRealCycles() {
        // Direct regression test for the bug this whole redesign was for: before this fix, a skipped cycle
        // wrote NOTHING to her journal at all, so the stagnation detector (needs 3+ readiness-ratio history
        // points) could never accumulate enough history once the first skip happened - a project that went
        // quiet was silenced forever. One real entry followed by 3 skip markers (impossible before this fix
        // - skips wrote nothing) must be enough history to trigger stagnation on the next cycle.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(2, 1, 0.5));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity realEntry = new GeminiObserverJournalEntity();
        realEntry.setProjectId(project.getId());
        realEntry.setCreatedAt(Instant.now().minusSeconds(4000));
        realEntry.setEntry("Earlier real cycle.");
        realEntry.setGeminiCalled(true);
        realEntry.setReadinessRatio(0.5);

        List<GeminiObserverJournalEntity> mixedHistory = new java.util.ArrayList<>();
        mixedHistory.add(realEntry);
        for (int i = 0; i < 3; i++) {
            GeminiObserverJournalEntity marker = new GeminiObserverJournalEntity();
            marker.setProjectId(project.getId());
            marker.setCreatedAt(Instant.now().minusSeconds(600L * (i + 1)));
            marker.setEntry("");
            marker.setGeminiCalled(false);
            marker.setReadinessRatio(0.5);
            mixedHistory.add(marker);
        }
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(mixedHistory);
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(realEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(realEntry));

        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Confirmed readiness has not moved across skip cycles too.", "findings": [], "actions": []}
                """);

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chatWithTools(promptCaptor.capture(), anyString(), any(), any(), any(), anyInt());
        assertTrue(promptCaptor.getValue().contains("STAGNATION WARNING"));
    }

    @Test
    void alreadyKnownStuckTaskDoesNotAloneForceARealCycle() {
        // Content-based dedup, not a hardcoded re-notify interval: a stuck task whose fingerprint (id +
        // status) was already shown to her last real visit must not force another paid call on its own -
        // repeating an unchanged fact on a timer is waste, not vigilance.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID stuckTaskId = UUID.randomUUID();
        TaskEntity stuckTask = new TaskEntity();
        stuckTask.setId(stuckTaskId);
        stuckTask.setStatus(TaskStatus.blocked);
        stuckTask.setTitle("Long-stuck task");
        stuckTask.setUpdatedAt(Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(stuckTask));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: saw this same stuck task already.");
        priorEntry.setGeminiCalled(true);
        priorEntry.setReadinessRatio(0.0);
        priorEntry.setAnomalyFingerprints("[\"task:" + stuckTaskId + ":blocked\"]");
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        service.runObserverCycle();

        verifyNoInteractions(mlPredictionServiceClient);
    }

    @Test
    void claimedTaskFrozenPastThresholdNowSurfacesAsAStuckCandidate() {
        // Direct regression test for the 2026-08-06 incident: a task frozen at "claimed" (a Jules session
        // stuck mid-flight while the real GitHub PR sat open+mergeable for 90+ minutes) was structurally
        // invisible to STUCK_CANDIDATE_STATUSES before this fix - it excluded "claimed" entirely.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        TaskEntity frozenClaimedTask = new TaskEntity();
        frozenClaimedTask.setId(UUID.randomUUID());
        frozenClaimedTask.setStatus(TaskStatus.claimed);
        frozenClaimedTask.setTitle("Philosophical falsification audit, session stuck");
        frozenClaimedTask.setUpdatedAt(Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(frozenClaimedTask));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: no stuck candidates.");
        priorEntry.setGeminiCalled(true);
        priorEntry.setReadinessRatio(0.0);
        priorEntry.setAnomalyFingerprints("[]");
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        stubChat(mlPredictionServiceClient, "{\"journalEntry\": \"Found a task frozen at claimed.\", \"findings\": [], \"actions\": []}");

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chatWithTools(promptCaptor.capture(), anyString(), any(), any(), any(), anyInt());
        assertTrue(promptCaptor.getValue().contains("STUCK/BLOCKED TASK CANDIDATES"));
        assertTrue(promptCaptor.getValue().contains("Philosophical falsification audit, session stuck"));
    }

    @Test
    void newlyAppearedStuckTaskForcesARealCycleEvenIfNothingElseChanged() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        UUID stuckTaskId = UUID.randomUUID();
        TaskEntity stuckTask = new TaskEntity();
        stuckTask.setId(stuckTaskId);
        stuckTask.setStatus(TaskStatus.blocked);
        stuckTask.setTitle("Newly stuck task");
        stuckTask.setUpdatedAt(Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(stuckTask));
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Previously: no stuck candidates.");
        priorEntry.setGeminiCalled(true);
        priorEntry.setReadinessRatio(0.0);
        priorEntry.setAnomalyFingerprints("[]");
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "New stuck candidate found.", "findings": [], "actions": []}
                """);

        service.runObserverCycle();

        verify(mlPredictionServiceClient).chatWithTools(anyString(), anyString(), any(), any(), any(), anyInt());
    }

    @Test
    void unverifiedPriorActionForcesARealCycleEvenWhenNothingElseChangedAndGetsMarkedVerifiedAfterward() {
        // Direct fix for "она нашла дефект, попыталась что-то сделать и больше никогда не проверяла, что у
        // неё получилось" - her own action must always get a follow-up check on the next cycle, independent
        // of the normal content-based dedup, since a failed action leaves the evidence fingerprint
        // unchanged and would otherwise never resurface.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(0, 0, 0.0));
        when(readinessService.listEpicDiagnostics(project.getId())).thenReturn(List.of());
        when(geminiContextService.buildContextBlock(anyString())).thenReturn("");

        GeminiObserverJournalEntity priorEntry = new GeminiObserverJournalEntity();
        priorEntry.setProjectId(project.getId());
        priorEntry.setCreatedAt(Instant.now().minusSeconds(1800));
        priorEntry.setEntry("Nudged a stuck session last cycle.");
        priorEntry.setGeminiCalled(true);
        priorEntry.setReadinessRatio(0.0);
        when(journalRepository.findTop5ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(priorEntry));
        when(journalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(priorEntry));
        when(journalRepository.findFirstByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(project.getId()))
                .thenReturn(java.util.Optional.of(priorEntry));

        when(actionRepository.existsByProjectIdAndVerifiedFalse(project.getId())).thenReturn(true);
        GeminiObserverActionEntity pendingAction = new GeminiObserverActionEntity();
        pendingAction.setProjectId(project.getId());
        pendingAction.setTool("nudgeStuckSession");
        pendingAction.setOutcome("success");
        when(actionRepository.findByProjectIdAndVerifiedFalse(project.getId())).thenReturn(List.of(pendingAction));

        stubChat(mlPredictionServiceClient, """
                {"journalEntry": "Checked the outcome of my prior nudge - session is still stuck.", "findings": [], "actions": []}
                """);

        service.runObserverCycle();

        verify(mlPredictionServiceClient).chatWithTools(anyString(), anyString(), any(), any(), any(), anyInt());
        assertTrue(pendingAction.isVerified());
        verify(actionRepository).saveAll(List.of(pendingAction));
    }

    // --- Phase 5 tool-loop wiring: real executor/continuation extracted from the chatWithTools call -------

    @SuppressWarnings("unchecked")
    private MLPredictionServiceClient.ToolExecutor capturedExecutor(ProjectEntity project) {
        stubCommonEvidence(project);
        ArgumentCaptor<MLPredictionServiceClient.ToolExecutor> executorCaptor =
                ArgumentCaptor.forClass(MLPredictionServiceClient.ToolExecutor.class);
        when(mlPredictionServiceClient.chatWithTools(anyString(), anyString(), any(), executorCaptor.capture(), any(), anyInt()))
                .thenReturn(toolResult("{\"journalEntry\": \"ok\", \"findings\": []}"));
        service.runObserverCycle();
        return executorCaptor.getValue();
    }

    @Test
    void readRecentEvidenceNodesToolReturnsRealNodeIdsFromTheRepository() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        com.eneik.production.models.persistence.EvidenceNodeEntity node =
                new com.eneik.production.models.persistence.EvidenceNodeEntity();
        UUID nodeId = UUID.randomUUID();
        node.setId(nodeId);
        node.setPolarity(com.eneik.production.models.persistence.EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        node.setSummaryText("a real finding");
        node.setKaizenProposalId("kz-1");
        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(eq(project.getId()), any()))
                .thenReturn(List.of(node));

        MLPredictionServiceClient.ToolExecutor executor = capturedExecutor(project);
        Map<String, Object> result = executor.execute("readRecentEvidenceNodes", Map.of());

        assertEquals(List.of(nodeId.toString()), result.get("nodeIds"));
    }

    @Test
    void readRecentEvidenceNodesExcludesNodesRejectedByTheLatestCoherenceRun() {
        // 2026-08-09 regression: live incident on test-forty-third - a self-contamination NEGATIVE_FINDING
        // kept resurfacing in Gemini's journal for 7+ hours after the real incident was fixed and a fresh
        // POSITIVE_CONFIRMATION had already made the coherence engine reject it, because this tool never
        // consulted the coherence verdict at all - it just returned every node in the flat 24h window.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        com.eneik.production.models.persistence.EvidenceNodeEntity staleRejected =
                new com.eneik.production.models.persistence.EvidenceNodeEntity();
        UUID staleId = UUID.randomUUID();
        staleRejected.setId(staleId);
        staleRejected.setPolarity(com.eneik.production.models.persistence.EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        staleRejected.setSummaryText("stale, already-superseded finding");
        staleRejected.setKaizenProposalId("kz-stale");

        com.eneik.production.models.persistence.EvidenceNodeEntity liveAccepted =
                new com.eneik.production.models.persistence.EvidenceNodeEntity();
        UUID liveId = UUID.randomUUID();
        liveAccepted.setId(liveId);
        liveAccepted.setPolarity(com.eneik.production.models.persistence.EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        liveAccepted.setSummaryText("still-live finding");
        liveAccepted.setKaizenProposalId("kz-live");

        when(evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(eq(project.getId()), any()))
                .thenReturn(List.of(staleRejected, liveAccepted));

        com.eneik.production.models.persistence.CoherenceRunEntity latestRun =
                new com.eneik.production.models.persistence.CoherenceRunEntity();
        UUID runId = UUID.randomUUID();
        latestRun.setId(runId);
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(project.getId())).thenReturn(List.of(latestRun));

        com.eneik.production.models.persistence.CoherenceRunNodeResultEntity rejectedResult =
                new com.eneik.production.models.persistence.CoherenceRunNodeResultEntity();
        rejectedResult.setEvidenceNodeId(staleId);
        rejectedResult.setAccepted(false);
        com.eneik.production.models.persistence.CoherenceRunNodeResultEntity acceptedResult =
                new com.eneik.production.models.persistence.CoherenceRunNodeResultEntity();
        acceptedResult.setEvidenceNodeId(liveId);
        acceptedResult.setAccepted(true);
        when(coherenceRunNodeResultRepository.findByCoherenceRunId(runId))
                .thenReturn(List.of(rejectedResult, acceptedResult));

        MLPredictionServiceClient.ToolExecutor executor = capturedExecutor(project);
        Map<String, Object> result = executor.execute("readRecentEvidenceNodes", Map.of());

        assertEquals(List.of(liveId.toString()), result.get("nodeIds"));
    }

    @Test
    void readLastCoherenceRunToolReturnsFoundFalseWhenNoRunExistsYet() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        when(coherenceRunRepository.findByProjectIdOrderByRanAtDesc(project.getId())).thenReturn(List.of());

        MLPredictionServiceClient.ToolExecutor executor = capturedExecutor(project);
        Map<String, Object> result = executor.execute("readLastCoherenceRun", Map.of());

        assertEquals(false, result.get("found"));
    }

    @Test
    void unknownToolNameReturnsAnErrorInsteadOfThrowing() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));

        MLPredictionServiceClient.ToolExecutor executor = capturedExecutor(project);
        Map<String, Object> result = executor.execute("notARealTool", Map.of());

        assertTrue(((String) result.get("error")).contains("unknown tool"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void continuationStopsWhenEvidenceNodesToolReturnsNoNewIds() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        ArgumentCaptor<MLPredictionServiceClient.ToolLoopContinuation> continuationCaptor =
                ArgumentCaptor.forClass(MLPredictionServiceClient.ToolLoopContinuation.class);
        when(mlPredictionServiceClient.chatWithTools(anyString(), anyString(), any(), any(), continuationCaptor.capture(), anyInt()))
                .thenReturn(toolResult("{\"journalEntry\": \"ok\", \"findings\": []}"));
        service.runObserverCycle();
        MLPredictionServiceClient.ToolLoopContinuation continuation = continuationCaptor.getValue();

        boolean keepGoing = continuation.shouldContinue(1, "readRecentEvidenceNodes", Map.of("nodeIds", List.of()));

        assertEquals(false, keepGoing);
    }

    @SuppressWarnings("unchecked")
    @Test
    void continuationKeepsGoingWhenEvidenceNodesToolReturnsANewId() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        ArgumentCaptor<MLPredictionServiceClient.ToolLoopContinuation> continuationCaptor =
                ArgumentCaptor.forClass(MLPredictionServiceClient.ToolLoopContinuation.class);
        when(mlPredictionServiceClient.chatWithTools(anyString(), anyString(), any(), any(), continuationCaptor.capture(), anyInt()))
                .thenReturn(toolResult("{\"journalEntry\": \"ok\", \"findings\": []}"));
        service.runObserverCycle();
        MLPredictionServiceClient.ToolLoopContinuation continuation = continuationCaptor.getValue();

        boolean keepGoing = continuation.shouldContinue(1, "readRecentEvidenceNodes", Map.of("nodeIds", List.of("new-id-1")));

        assertEquals(true, keepGoing);
    }

    @SuppressWarnings("unchecked")
    @Test
    void continuationStopsAfterCoherenceScoreStaysFlatAcrossMinMatchingRounds() {
        // Reuses the SAME stagnation idiom (3 consecutive near-identical observations) already proven for
        // readiness-ratio drift, applied here to successive within-cycle tool results instead of successive
        // multi-hour journal entries.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        ArgumentCaptor<MLPredictionServiceClient.ToolLoopContinuation> continuationCaptor =
                ArgumentCaptor.forClass(MLPredictionServiceClient.ToolLoopContinuation.class);
        when(mlPredictionServiceClient.chatWithTools(anyString(), anyString(), any(), any(), continuationCaptor.capture(), anyInt()))
                .thenReturn(toolResult("{\"journalEntry\": \"ok\", \"findings\": []}"));
        service.runObserverCycle();
        MLPredictionServiceClient.ToolLoopContinuation continuation = continuationCaptor.getValue();

        Map<String, Object> sameScore = Map.of("found", true, "coherenceScore", 0.5, "totalNodes", 4, "acceptedNodes", 3);
        assertEquals(true, continuation.shouldContinue(1, "readLastCoherenceRun", sameScore));
        assertEquals(true, continuation.shouldContinue(2, "readLastCoherenceRun", sameScore));
        assertEquals(false, continuation.shouldContinue(3, "readLastCoherenceRun", sameScore));
    }
}
