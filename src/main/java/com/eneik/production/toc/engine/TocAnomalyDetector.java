package com.eneik.production.toc.engine;

import com.eneik.production.toc.model.AnomalyReport;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Anomaly Detection Engine for TOC Sentinel.
 * Real-time detection of:
 * - Directed Call Cycles & Loops
 * - Dynamic Stalls & Timeouts (Little's Law & Probabilistic Limits)
 * - Resource Contention & WFG Deadlocks (Coffman conditions)
 */
@Component
public class TocAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(TocAnomalyDetector.class);

    private final TocExecutionGraph graph;

    // Resource Ownership Map: resourceId -> holderTokenId
    private final Map<String, String> resourceHolders = new ConcurrentHashMap<>();
    // Audit log of recent anomaly reports
    private final List<AnomalyReport> anomalyLog = new CopyOnWriteArrayList<>();

    private volatile double sensitivityMultiplier = 3.0;
    private volatile double defaultTimeoutFloorMs = 5000.0;

    public TocAnomalyDetector(TocExecutionGraph graph) {
        this.graph = graph;
    }

    /**
     * Checks for directed call stack cycles upon entering a node.
     */
    public boolean checkAndRegisterStepEnter(TocToken token, String nodeName) {
        String currentActiveNode = token.getActiveNode();
        boolean pushed = token.pushNode(nodeName);

        if (!pushed) {
            // Cycle detected!
            String reportId = UUID.randomUUID().toString();
            String details = String.format("Token '%s' attempted re-entry into node '%s'. Active stack path: %s",
                    token.getTokenId(), nodeName, token.getCallStack());
            String action = String.format("Aborted token '%s' execution to prevent infinite cycle loop.", token.getTokenId());

            log.warn("[TOC-SENTINEL][CYCLE_DETECTED] {}", details);
            log.warn("[TOC-SENTINEL][CYCLE_ACTION] {}", action);

            token.setStatus(TocToken.TokenStatus.CYCLE_ABORTED);
            graph.unregisterToken(token.getTokenId());

            AnomalyReport report = new AnomalyReport(
                    reportId,
                    AnomalyReport.AnomalyType.CYCLE_DETECTED,
                    token.getTokenId(),
                    nodeName,
                    null,
                    details,
                    action,
                    Instant.now()
            );
            recordAnomaly(report);
            return false;
        }

        if (currentActiveNode != null) {
            graph.recordTransition(currentActiveNode, nodeName);
        }

        TocNode node = graph.getOrCreateNode(nodeName);
        node.incrementInFlight();
        return true;
    }

    /**
     * Scans active tokens for dynamic duration stalls.
     */
    public List<AnomalyReport> scanForStalls() {
        List<AnomalyReport> detected = new ArrayList<>();

        for (TocNode node : graph.getAllNodes()) {
            node.setStallBottleneck(false);
        }

        for (TocToken token : graph.getActiveTokens()) {
            if (token.getStatus() != TocToken.TokenStatus.ACTIVE) {
                continue;
            }

            String activeNodeName = token.getActiveNode();
            if (activeNodeName == null) {
                continue;
            }

            TocNode node = graph.getNode(activeNodeName);
            if (node == null) {
                continue;
            }

            double dynamicLimitMs = node.getDynamicTimeoutLimitMs(sensitivityMultiplier, defaultTimeoutFloorMs);
            long dwellMs = token.getActiveNodeDwellTimeMs();

            if (dwellMs > dynamicLimitMs) {
                node.setStallBottleneck(true);

                String reportId = UUID.randomUUID().toString();
                String details = String.format(
                        "Token '%s' stalled in node '%s' for %d ms. Dynamic Limit: %.2f ms (mu: %.2f ms, stdDev: %.2f ms).",
                        token.getTokenId(), activeNodeName, dwellMs, dynamicLimitMs, node.getMeanDurationMs(), node.getStdDevMs()
                );
                String action = String.format("Flagged node '%s' as STALL_BOTTLENECK.", activeNodeName);

                if (dwellMs > 2.5 * dynamicLimitMs) {
                    token.setStatus(TocToken.TokenStatus.STALLED);
                    action += String.format(" Marked token '%s' status as STALLED.", token.getTokenId());
                }

                log.warn("[TOC-SENTINEL][STALL_DETECTED] {}", details);
                log.warn("[TOC-SENTINEL][STALL_ACTION] {}", action);

                AnomalyReport report = new AnomalyReport(
                        reportId,
                        AnomalyReport.AnomalyType.STALL_DETECTED,
                        token.getTokenId(),
                        activeNodeName,
                        null,
                        details,
                        action,
                        Instant.now()
                );
                recordAnomaly(report);
                detected.add(report);
            }
        }

        return detected;
    }

    /**
     * Records resource acquisition by a token.
     */
    public void registerResourceAcquired(TocToken token, String resourceId) {
        resourceHolders.put(resourceId, token.getTokenId());
        token.acquireResource(resourceId);
        log.info("[TOC-SENTINEL][RESOURCE_ACQUIRED] Token '{}' acquired resource '{}'.", token.getTokenId(), resourceId);
    }

    /**
     * Records resource release by a token.
     */
    public void registerResourceReleased(TocToken token, String resourceId) {
        resourceHolders.remove(resourceId, token.getTokenId());
        token.releaseResource(resourceId);
        log.info("[TOC-SENTINEL][RESOURCE_RELEASED] Token '{}' released resource '{}'.", token.getTokenId(), resourceId);
    }

    /**
     * Registers that a token is waiting for a resource and performs Wait-For Graph (WFG) deadlock analysis.
     */
    public boolean registerResourceWaitingAndCheckDeadlock(TocToken waitingToken, String resourceId) {
        waitingToken.setWaitingResource(resourceId);
        String holderId = resourceHolders.get(resourceId);

        if (holderId == null) {
            return false; // Resource free
        }

        log.info("[TOC-SENTINEL][RESOURCE_WAIT] Token '{}' is waiting for resource '{}' held by token '{}'.",
                waitingToken.getTokenId(), resourceId, holderId);

        // Build Wait-For Graph (WFG)
        Map<String, String> wfgWaitingToHoldingToken = new HashMap<>();
        for (TocToken t : graph.getActiveTokens()) {
            String waitingRes = t.getWaitingResource();
            if (waitingRes != null) {
                String currentHolder = resourceHolders.get(waitingRes);
                if (currentHolder != null && !currentHolder.equals(t.getTokenId())) {
                    wfgWaitingToHoldingToken.put(t.getTokenId(), currentHolder);
                }
            }
        }

        // Cycle detection on WFG
        List<String> cycle = findWfgCycle(wfgWaitingToHoldingToken);
        if (!cycle.isEmpty()) {
            String reportId = UUID.randomUUID().toString();
            String details = String.format("Deadlock cycle detected in Wait-For Graph: %s", cycle);

            // Select victim (token with lowest priority or newest creation)
            TocToken victim = selectDeadlockVictim(cycle);
            String victimId = victim != null ? victim.getTokenId() : waitingToken.getTokenId();
            String action = String.format("Selected victim token '%s' to abort deadlock and force resource release.", victimId);

            log.error("[TOC-SENTINEL][DEADLOCK_DETECTED] {}", details);
            log.error("[TOC-SENTINEL][DEADLOCK_ACTION] {}", action);

            if (victim != null) {
                victim.setStatus(TocToken.TokenStatus.DEADLOCK_ABORTED);
                for (String res : new HashSet<>(victim.getHeldResources())) {
                    registerResourceReleased(victim, res);
                }
                graph.unregisterToken(victim.getTokenId());
            }

            AnomalyReport report = new AnomalyReport(
                    reportId,
                    AnomalyReport.AnomalyType.DEADLOCK_DETECTED,
                    victimId,
                    victim != null ? victim.getActiveNode() : null,
                    resourceId,
                    details,
                    action,
                    Instant.now()
            );
            recordAnomaly(report);
            return true;
        }

        return false;
    }

    private List<String> findWfgCycle(Map<String, String> wfg) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        List<String> cyclePath = new ArrayList<>();

        for (String node : wfg.keySet()) {
            if (!visited.contains(node)) {
                if (dfsWfg(node, wfg, visited, recStack, cyclePath)) {
                    return cyclePath;
                }
            }
        }
        return Collections.emptyList();
    }

    private boolean dfsWfg(String curr, Map<String, String> wfg, Set<String> visited, Set<String> recStack, List<String> path) {
        visited.add(curr);
        recStack.add(curr);
        path.add(curr);

        String next = wfg.get(curr);
        if (next != null) {
            if (recStack.contains(next)) {
                path.add(next);
                return true;
            }
            if (!visited.contains(next)) {
                if (dfsWfg(next, wfg, visited, recStack, path)) {
                    return true;
                }
            }
        }

        recStack.remove(curr);
        path.remove(path.size() - 1);
        return false;
    }

    private TocToken selectDeadlockVictim(List<String> cycleTokenIds) {
        TocToken victim = null;
        for (String id : cycleTokenIds) {
            TocToken token = graph.getToken(id);
            if (token != null) {
                if (victim == null || token.getPriority() < victim.getPriority()) {
                    victim = token;
                }
            }
        }
        return victim;
    }

    private void recordAnomaly(AnomalyReport report) {
        if (!anomalyLog.isEmpty()) {
            AnomalyReport last = anomalyLog.get(anomalyLog.size() - 1);
            if (last.type() == report.type() &&
                    Objects.equals(last.nodeName(), report.nodeName()) &&
                    Objects.equals(last.resourceName(), report.resourceName()) &&
                    last.timestamp().isAfter(Instant.now().minus(5, java.time.temporal.ChronoUnit.MINUTES))) {
                return;
            }
        }
        anomalyLog.add(report);
    }

    public List<AnomalyReport> getRecentAnomalies() {
        return Collections.unmodifiableList(anomalyLog);
    }

    public void setSensitivityMultiplier(double sensitivityMultiplier) {
        this.sensitivityMultiplier = sensitivityMultiplier;
    }

    public void setDefaultTimeoutFloorMs(double defaultTimeoutFloorMs) {
        this.defaultTimeoutFloorMs = defaultTimeoutFloorMs;
    }
}
