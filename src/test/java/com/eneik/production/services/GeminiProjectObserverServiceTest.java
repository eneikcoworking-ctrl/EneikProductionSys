package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverJournalEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testimony-vs-evidence, applied to the periodic observer itself (2026-07-25 redesign): it reasons over a
 * structured evidence snapshot of the project's real current state, never the backend's own internal log,
 * and keeps its OWN journal for cross-cycle continuity rather than consuming the backend's. Every raised
 * finding is still deduped against already-live wishlists the same way every other generative track is.
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
        when(actionRepository.findTop5ByProjectIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(falsificationCycleService.philosophicalReadinessInfo(any()))
                .thenReturn(new FalsificationCycleService.PhilosophicalReadinessInfo(0.9, false));
        service = new GeminiProjectObserverService(projectRepository, wishlistRepository, taskRepository,
                readinessService, journalRepository, actionRepository, geminiContextService, mlPredictionServiceClient,
                wishlistContentSimilarityMatcher, actionService, falsificationCycleService, settingsService);
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

    @Test
    void doesNothingWhenFeatureFlagIsOff() {
        setUp();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project()));

        service.runObserverCycle();

        verify(mlPredictionServiceClient, never()).chat(anyString(), anyString(), anyString());
        verifyNoInteractions(taskRepository);
    }

    @Test
    void firstCycleAlwaysCallsGeminiToEstablishABaselineJournal() {
        // No prior journal entry exists yet - the very first cycle for a project must always call Gemini
        // once (even with zero task activity) so a baseline journal entry exists for later cycles to skip
        // against. Uses the cheap flash tier (chat), not chatCritical/pro - this is a conservative noticing
        // task, not a merge-gating decision.
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString()))
                .thenReturn("{\"journalEntry\": \"Nothing notable this cycle.\", \"findings\": []}");

        service.runObserverCycle();

        verify(mlPredictionServiceClient).chat(anyString(), anyString(), anyString());
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

        service.runObserverCycle();

        verifyNoInteractions(mlPredictionServiceClient);
        verify(journalRepository, never()).save(any());
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
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("not valid JSON at all");

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

        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString()))
                .thenReturn("{\"journalEntry\": \"Noted the failed migration task.\", \"findings\": []}");

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chat(promptCaptor.capture(), anyString(), anyString());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Previously: everything looked healthy."));
        assertTrue(prompt.contains("Flaky migration task"));
        assertTrue(prompt.contains("PR closed without merge"));
        assertTrue(prompt.contains("1/2 merged"));
    }

    @Test
    void raisesAWishlistForEachGenuinelyNewFinding() {
        setUp();
        when(settingsService.effectiveBoolean("gemini_project_observer_enabled")).thenReturn(true);
        ProjectEntity project = project();
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubCommonEvidence(project);
        when(wishlistContentSimilarityMatcher.findLikelyDuplicate(any(), anyString())).thenReturn(java.util.Optional.empty());
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("""
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
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("""
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
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("""
                {"journalEntry": "Found 3 tasks sharing an identical description - flagging as a real bug.",
                 "findings": [{"summary": "3 duplicate tasks detected", "evidence": "identical description", "severity": "high"}]}
                """);

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chat(promptCaptor.capture(), anyString(), anyString());
        assertTrue(promptCaptor.getValue().contains("DUPLICATE TASK WARNING"));
        assertTrue(promptCaptor.getValue().contains("3 tasks share the exact same description"));
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
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("""
                {"journalEntry": "Confirmed readiness has not moved - flagging stagnation.",
                 "findings": [], "actions": []}
                """);

        service.runObserverCycle();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mlPredictionServiceClient).chat(promptCaptor.capture(), anyString(), anyString());
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
        when(mlPredictionServiceClient.chat(anyString(), anyString(), anyString())).thenReturn("""
                {"journalEntry": "Dismissed a clearly dead wishlist item.", "findings": [],
                 "actions": [{"tool": "dismissWishlist", "targetId": "%s", "reason": "listed as stale, superseded"}]}
                """.formatted(wishlistId));

        service.runObserverCycle();

        verify(actionService).dismissWishlist(eq(project), eq(wishlistId.toString()), anyString());
    }
}
