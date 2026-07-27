package com.eneik.production.toc.service;

import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.AnomalyReport;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TOC Sentinel Service - Independent Theory of Constraints Analytics & Real-Time Operational Engine.
 * Intercepts scenario execution events, maintains a dynamic state machine graph, detects anomalies
 * (cycles, stalls, resource deadlocks), identifies bottlenecks, and controls flow using Drum-Buffer-Rope.
 */
@Service
public class TocSentinelService {

    private static final Logger log = LoggerFactory.getLogger(TocSentinelService.class);

    private final TocExecutionGraph graph;
    private final TocAnomalyDetector anomalyDetector;
    private final TocOptimizer optimizer;

    public TocSentinelService(TocExecutionGraph graph,
                              TocAnomalyDetector anomalyDetector,
                              TocOptimizer optimizer) {
        this.graph = graph;
        this.anomalyDetector = anomalyDetector;
        this.optimizer = optimizer;
        log.info("[TOC-SENTINEL][INIT] TOC Sentinel Service initialized successfully.");
    }

    /**
     * Starts tracking a new execution instance (Token).
     * Checks Drum-Buffer-Rope (DBR) admission gate.
     */
    public TocToken startExecution(String scenarioName, int priority) {
        String tokenId = "tok-" + UUID.randomUUID().toString().substring(0, 8);
        return startExecutionWithId(tokenId, scenarioName, priority);
    }

    public TocToken startExecutionWithId(String tokenId, String scenarioName, int priority) {
        boolean admitted = optimizer.shouldAdmit(scenarioName, priority);
        TocToken token = new TocToken(tokenId, scenarioName, priority);

        if (!admitted) {
            token.setStatus(TocToken.TokenStatus.THROTTLED);
            log.warn("[TOC-SENTINEL][START_EXECUTION] Token '{}' for scenario '{}' was THROTTLED by DBR Rope.", tokenId, scenarioName);
            return token;
        }

        graph.registerToken(token);
        log.info("[TOC-SENTINEL][START_EXECUTION] Token '{}' started for scenario '{}' with priority {}.", tokenId, scenarioName, priority);
        return token;
    }

    /**
     * Called when an execution token enters a step/node.
     * Performs Directed Cycle / Infinite Loop Detection.
     */
    public boolean enterStep(TocToken token, String stepName) {
        if (token == null || token.getStatus() != TocToken.TokenStatus.ACTIVE) {
            return false;
        }

        boolean allowed = anomalyDetector.checkAndRegisterStepEnter(token, stepName);
        if (allowed) {
            log.info("[TOC-SENTINEL][STEP_ENTER] Token '{}' entered step '{}'. Active path: {}",
                    token.getTokenId(), stepName, token.getCallStack());
        }
        return allowed;
    }

    /**
     * Called when an execution token completes a step/node.
     */
    public void exitStep(TocToken token, String stepName, boolean success) {
        if (token == null) {
            return;
        }

        long durationNanos = 0;
        if (token.getActiveNodeEnteredAt() != null) {
            durationNanos = (Instant.now().toEpochMilli() - token.getActiveNodeEnteredAt().toEpochMilli()) * 1_000_000L;
        }

        token.popNode(stepName);

        TocNode node = graph.getNode(stepName);
        if (node != null) {
            node.decrementInFlight();
            node.recordExecution(durationNanos, success);
            log.info("[TOC-SENTINEL][STEP_EXIT] Token '{}' exited step '{}' (Duration: {} ms, Success: {}). In-Flight: {}.",
                    token.getTokenId(), stepName, String.format("%.2f", durationNanos / 1_000_000.0), success, node.getInFlightCount());
        }
    }

    /**
     * Ends execution tracking for a token.
     */
    public void endExecution(TocToken token, boolean success) {
        if (token == null) {
            return;
        }

        if (token.getStatus() == TocToken.TokenStatus.ACTIVE) {
            token.setStatus(success ? TocToken.TokenStatus.COMPLETED : TocToken.TokenStatus.FAILED);
        }

        // Release any remaining held resources
        for (String res : token.getHeldResources()) {
            anomalyDetector.registerResourceReleased(token, res);
        }

        graph.unregisterToken(token.getTokenId());
        log.info("[TOC-SENTINEL][END_EXECUTION] Token '{}' finished with status '{}'.", token.getTokenId(), token.getStatus());
    }

    /**
     * Resource lock acquisition telemetry.
     */
    public void acquireResource(TocToken token, String resourceId) {
        if (token != null) {
            anomalyDetector.registerResourceAcquired(token, resourceId);
        }
    }

    /**
     * Resource lock waiting telemetry (triggers WFG Deadlock Detection).
     */
    public boolean waitResource(TocToken token, String resourceId) {
        if (token == null) {
            return false;
        }
        return anomalyDetector.registerResourceWaitingAndCheckDeadlock(token, resourceId);
    }

    /**
     * Resource lock release telemetry.
     */
    public void releaseResource(TocToken token, String resourceId) {
        if (token != null) {
            anomalyDetector.registerResourceReleased(token, resourceId);
        }
    }

    /**
     * Scheduled periodic background watchdog task.
     * Evaluates dynamic stalls, identifies primary constraint, and updates Drum-Buffer-Rope (DBR) state.
     */
    @Scheduled(fixedRate = 2000)
    public void periodicWatchdog() {
        try {
            anomalyDetector.scanForStalls();
            optimizer.evaluateConstraintsAndDbr();
        } catch (Exception e) {
            log.error("[TOC-SENTINEL][WATCHDOG_ERROR] Watchdog execution encountered error: ", e);
        }
    }

    public String getCurrentConstraintName() {
        return optimizer.getCurrentConstraintName();
    }

    public DbrStatus getDbrStatus() {
        return optimizer.evaluateConstraintsAndDbr();
    }

    public List<AnomalyReport> getRecentAnomalies() {
        return anomalyDetector.getRecentAnomalies();
    }

    public TocExecutionGraph getGraph() {
        return graph;
    }

    public TocAnomalyDetector getAnomalyDetector() {
        return anomalyDetector;
    }

    public TocOptimizer getOptimizer() {
        return optimizer;
    }
}
