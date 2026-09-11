package com.eneik.production.toc.service;

import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.AnomalyReport;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocEdge;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * TOC Sentinel Service - Independent Theory of Constraints Analytics & Real-Time Operational Engine.
 * Intercepts scenario execution events, maintains a dynamic state machine graph, detects anomalies
 * (cycles, stalls, resource deadlocks), identifies bottlenecks, and controls flow using Drum-Buffer-Rope.
 *
 * Invariants enforced by Antigravity (L2):
 * - Anti-Mirror Telemetry (LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY / D013): Observation does not mutate
 *   the observed. getDbrStatus() returns the cached latestDbrStatus snapshot without recomputing graph node
 *   utilizations, changing node primary constraint flags, or resetting timestamps.
 * - Derived Dynamic Cadence (ALONZO_CHERCH_21_DERIVED_CUTOFF / D008): Watchdog cadence is dynamically derived
 *   via Spring Trigger as half of the shortest observed step duration in the graph (Nyquist-Shannon sampling
 *   criterion), strictly clamped between declared min-cadence and max-cadence bounds. In the absence of
 *   step observations, the watchdog relaxes to max-cadence to eliminate idle polling waste.
 * - Single-Writer Lifecycle Ownership (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004): Node in-flight counters
 *   are owned and mutated exclusively by TocSentinelService (incrementInFlight in enterStep, decrementInFlight
 *   in exitStep). Leaky component getters (getGraph, getAnomalyDetector, getOptimizer) are eliminated, and all
 *   operational queries/mutations (buffer capacity, unmodifiable node/edge collections) are encapsulated on the service.
 */
@Service
public class TocSentinelService implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(TocSentinelService.class);

    public static final long DEFAULT_MIN_CADENCE_MS = 250L;
    public static final long DEFAULT_MAX_CADENCE_MS = 10000L;

    private final TocExecutionGraph graph;
    private final TocAnomalyDetector anomalyDetector;
    private final TocOptimizer optimizer;

    private long minCadenceMs = DEFAULT_MIN_CADENCE_MS;
    private long maxCadenceMs = DEFAULT_MAX_CADENCE_MS;

    public TocSentinelService(TocExecutionGraph graph,
                              TocAnomalyDetector anomalyDetector,
                              TocOptimizer optimizer) {
        this.graph = graph;
        this.anomalyDetector = anomalyDetector;
        this.optimizer = optimizer;
        // Initial evaluation establishes baseline DbrStatus snapshot without waiting for first scheduled tick
        this.optimizer.evaluateConstraintsAndDbr();
        log.info("[TOC-SENTINEL][INIT] TOC Sentinel Service initialized successfully.");
    }

    @Value("${eneik.toc.sentinel.min-cadence-ms:250}")
    public void setMinCadenceMs(long minCadenceMs) {
        this.minCadenceMs = minCadenceMs;
    }

    @Value("${eneik.toc.sentinel.max-cadence-ms:10000}")
    public void setMaxCadenceMs(long maxCadenceMs) {
        this.maxCadenceMs = maxCadenceMs;
    }

    public long getMinCadenceMs() {
        return minCadenceMs;
    }

    public long getMaxCadenceMs() {
        return maxCadenceMs;
    }

    public void setCadenceBounds(long minMs, long maxMs) {
        this.minCadenceMs = minMs;
        this.maxCadenceMs = maxMs;
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
            TocNode node = graph.getOrCreateNode(stepName);
            node.incrementInFlight();
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

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::periodicWatchdog,
                triggerContext -> {
                    long delayMs = computeDerivedWatchdogCadenceMs();
                    Instant lastCompletion = triggerContext.lastCompletion();
                    Instant base = (lastCompletion != null) ? lastCompletion : Instant.now();
                    return base.plusMillis(delayMs);
                }
        );
    }

    /**
     * Computes the dynamic watchdog cadence derived from observed step durations in the execution graph
     * (ALONZO_CHERCH_21_DERIVED_CUTOFF / D008).
     *
     * In accordance with the Nyquist-Shannon sampling criterion, tracking bottleneck migrations and queue
     * buildup reliably without aliasing requires an inspection cadence at least twice as fast as the
     * shortest step cycle:
     *     cadence = min(meanDurationMs) / 2
     *
     * When no step observations exist in the graph, the cadence relaxes to maxCadenceMs to eliminate
     * idle polling waste. When step observations exist, the period scales dynamically with actual throughput
     * and is strictly clamped between minCadenceMs and maxCadenceMs.
     */
    public long computeDerivedWatchdogCadenceMs() {
        double shortestMeanMs = -1.0;

        for (TocNode node : graph.getAllNodes()) {
            if (node.getCompletedCount() > 0) {
                double mean = node.getMeanDurationMs();
                if (mean > 0 && (shortestMeanMs < 0 || mean < shortestMeanMs)) {
                    shortestMeanMs = mean;
                }
            }
        }

        if (shortestMeanMs < 0) {
            // No completed step observations in graph: relax to max bound
            return maxCadenceMs;
        }

        long derivedMs = Math.round(shortestMeanMs / 2.0);
        return Math.max(minCadenceMs, Math.min(maxCadenceMs, derivedMs));
    }

    /**
     * Periodic background watchdog task executed dynamically via Spring Trigger.
     * Evaluates dynamic stalls, identifies primary constraint, and updates Drum-Buffer-Rope (DBR) state.
     */
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

    /**
     * Returns the cached Drum-Buffer-Rope (DBR) status snapshot.
     * Pure read operation: does not mutate graph nodes, recalculate arrival rates, or alter constraint flags.
     * (LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY / D013)
     */
    public DbrStatus getDbrStatus() {
        return optimizer.getLatestDbrStatus();
    }

    /**
     * Forces an explicit re-evaluation of constraints and DBR parameters.
     */
    public DbrStatus refreshDbrStatus() {
        return optimizer.evaluateConstraintsAndDbr();
    }

    /**
     * Returns the maximum capacity of the buffer before the primary constraint.
     * Encapsulates optimizer state (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004).
     */
    public long getMaxBufferCapacity() {
        return optimizer.getMaxBufferCapacity();
    }

    /**
     * Updates the maximum capacity of the buffer before the primary constraint.
     * Encapsulates optimizer state (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004).
     */
    public void setMaxBufferCapacity(long capacity) {
        optimizer.setMaxBufferCapacity(capacity);
    }

    public List<AnomalyReport> getRecentAnomalies() {
        return anomalyDetector.getRecentAnomalies();
    }

    /**
     * Retrieves an active execution token by its identifier.
     */
    public TocToken getToken(String tokenId) {
        return graph.getToken(tokenId);
    }

    /**
     * Retrieves a graph node by name.
     */
    public TocNode getNode(String nodeName) {
        return graph.getNode(nodeName);
    }

    /**
     * Returns an unmodifiable snapshot collection of all known graph nodes.
     */
    public Collection<TocNode> getAllNodes() {
        return graph.getAllNodes();
    }

    /**
     * Returns an unmodifiable snapshot collection of all graph edges.
     */
    public Collection<TocEdge> getEdges() {
        return graph.getEdges();
    }

    /**
     * Returns the count of currently active execution tokens.
     */
    public int getActiveTokenCount() {
        return graph.getActiveTokens().size();
    }

    /**
     * Returns the global token arrival rate per second.
     */
    public double getGlobalArrivalRatePerSec() {
        return graph.getGlobalArrivalRatePerSec();
    }

    /**
     * Returns the total count of completed executions across all nodes.
     */
    public long getCompletedCountAllNodes() {
        return graph.getCompletedCountAllNodes();
    }
}
