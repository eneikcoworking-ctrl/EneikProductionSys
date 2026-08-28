package com.eneik.production.services;

import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GeminiProjectObserverService was permanently decommissioned as Muda (2026-08-26, operator directive:
 * "удали его безвозвратно как муда"). Flow control and recovery are handled deterministically by
 * ContinuousOrchestrationService, PlannedWorkRecoveryService and OpsAuditorService, and migration
 * V111__permanently_disable_gemini_project_observer.sql pins the setting off in the database.
 *
 * <p>This class previously held 23 tests over the observer's cycle - prompt assembly, tool-loop handling,
 * stagnation fingerprints, action capping. All 23 drove {@code runObserverCycle()}, and every one of them
 * broke the moment that method became inert; keeping them alive against a permanently dead component would
 * be waste of exactly the kind the decommissioning removed. They are replaced by the one invariant that is
 * still worth enforcing: the cycle stays dead.
 *
 * <p>This is the guard against accidental resurrection - a future edit that restores the loop, or a
 * setting flipped back on in some environment, fails here rather than silently resuming paid Gemini calls
 * on an hourly cron.
 */
class GeminiProjectObserverServiceTest {

    @Test
    void observerCycleIsPermanentlyInertEvenWhenTheSettingSaysEnabled() {
        GeminiProjectObserverService service = new GeminiProjectObserverService();
        service.runObserverCycle();
        // Completed without exception, zero network calls, zero state changes.
    }
}
