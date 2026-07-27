package com.eneik.production.toc.engine;

import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;

/**
 * Theory of Constraints (TOC) Optimizer and Drum-Buffer-Rope (DBR) Controller.
 * Identifies system bottlenecks dynamically and regulates input workflow rate.
 */
@Component
public class TocOptimizer {

    private static final Logger log = LoggerFactory.getLogger(TocOptimizer.class);
    private static final int HIGH_PRIORITY_BYPASS = 80;

    private final TocExecutionGraph graph;

    private volatile long maxBufferCapacity = 15;
    private volatile boolean ropeThrottlingActive = false;
    private volatile String currentConstraintName = "NONE";
    private volatile Instant lastEvaluatedAt = Instant.now();

    public TocOptimizer(TocExecutionGraph graph) {
        this.graph = graph;
    }

    /**
     * Evaluates all nodes in the state machine graph to find the primary TOC constraint
     * and calculate Drum-Buffer-Rope parameters.
     */
    public synchronized DbrStatus evaluateConstraintsAndDbr() {
        Collection<TocNode> allNodes = graph.getAllNodes();
        double lambda = graph.getGlobalArrivalRatePerSec();

        TocNode primaryConstraint = null;
        double maxConstraintScore = -1.0;

        for (TocNode node : allNodes) {
            double meanSec = node.getMeanDurationMs() / 1000.0;
            double u = lambda * meanSec;
            node.setUtilization(u);

            double queueWeight = node.getInFlightCount();
            double stallWeight = node.isStallBottleneck() ? 4.0 : 1.0;
            double score = (u + 0.05) * (queueWeight + 1.0) * (meanSec + 0.001) * stallWeight;

            if (score > maxConstraintScore) {
                maxConstraintScore = score;
                primaryConstraint = node;
            }
            node.setPrimaryConstraint(false);
        }

        long bufferSize = 0;
        double utilization = 0.0;
        double meanMs = 0.0;
        String constraintName = "NONE";

        if (primaryConstraint != null) {
            primaryConstraint.setPrimaryConstraint(true);
            constraintName = primaryConstraint.getName();
            bufferSize = primaryConstraint.getInFlightCount();
            utilization = primaryConstraint.getUtilization();
            meanMs = primaryConstraint.getMeanDurationMs();

            if (!constraintName.equals(this.currentConstraintName)) {
                log.info("[TOC-SENTINEL][CONSTRAINT_IDENTIFIED] Primary Constraint shifted to Node '{}' (Utilization: {}%, In-Flight Queue: {}, Mean Latency: {} ms). System throughput is bounded by this node.",
                        constraintName, String.format("%.2f", utilization * 100.0), bufferSize, String.format("%.2f", meanMs));
                this.currentConstraintName = constraintName;
            }
        }

        // Drum-Buffer-Rope (DBR) Logic
        boolean previousRopeState = ropeThrottlingActive;
        ropeThrottlingActive = bufferSize >= maxBufferCapacity;

        if (ropeThrottlingActive && !previousRopeState) {
            log.warn("[TOC-SENTINEL][DBR_ROPE_ACTION] Buffer before constraint '{}' at capacity ({}/{}). Throttling incoming new executions (Rope tight end) to prevent overload.",
                    constraintName, bufferSize, maxBufferCapacity);
        } else if (!ropeThrottlingActive && previousRopeState) {
            log.info("[TOC-SENTINEL][DBR_ROPE_ACTION] Buffer before constraint '{}' cleared ({}/{}). Resuming normal execution admissions.",
                    constraintName, bufferSize, maxBufferCapacity);
        }

        String recommendation = ropeThrottlingActive
                ? String.format("Throttling active! Elevate priority of work targeting node '%s' and defer non-critical jobs.", constraintName)
                : String.format("System flow optimal. Primary constraint: '%s'.", constraintName);

        lastEvaluatedAt = Instant.now();

        return new DbrStatus(
                constraintName,
                bufferSize,
                utilization,
                meanMs,
                bufferSize,
                maxBufferCapacity,
                ropeThrottlingActive,
                lastEvaluatedAt,
                recommendation
        );
    }

    /**
     * DBR Admission Gate check.
     */
    public boolean shouldAdmit(String scenarioName, int priority) {
        if (!ropeThrottlingActive) {
            return true;
        }

        if (priority >= HIGH_PRIORITY_BYPASS) {
            log.info("[TOC-SENTINEL][DBR_BYPASS] Admitted high-priority execution for scenario '{}' (Priority: {}).", scenarioName, priority);
            return true;
        }

        log.warn("[TOC-SENTINEL][DBR_THROTTLE] Throttling admission for scenario '{}' (Priority: {}). Rope pulled back due to constraint buffer overflow at '{}'.",
                scenarioName, priority, currentConstraintName);
        return false;
    }

    public String getCurrentConstraintName() {
        return currentConstraintName;
    }

    public boolean isRopeThrottlingActive() {
        return ropeThrottlingActive;
    }

    public long getMaxBufferCapacity() {
        return maxBufferCapacity;
    }

    public void setMaxBufferCapacity(long maxBufferCapacity) {
        this.maxBufferCapacity = maxBufferCapacity;
    }
}
