package com.eneik.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * GeminiProjectObserverService was permanently decommissioned as Muda (2026-08-26, operator directive:
 * "удали его безвозвратно как муда").
 *
 * <p>Deterministic flow control, self-healing and recovery are handled by ContinuousOrchestrationService,
 * PlannedWorkRecoveryService and OpsAuditorService, and migration
 * V111__permanently_disable_gemini_project_observer.sql permanently locks this setting in the database.
 *
 * <p>This stub is maintained to preserve Spring Context dependency injection contracts without dragging in
 * 23 heavy repositories and unused dependencies.
 */
@Service
public class GeminiProjectObserverService {

    private static final Logger log = LoggerFactory.getLogger(GeminiProjectObserverService.class);

    public GeminiProjectObserverService() {
        // Zero-dependency decommissioned stub.
    }

    public void runObserverCycle() {
        // Permanently inert.
    }
}

