package com.eneik.production.toc.engine;

import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    public static final long DEFAULT_MAX_BUFFER_CAPACITY = 15L;

    private final TocExecutionGraph graph;

    private volatile long maxBufferCapacity = DEFAULT_MAX_BUFFER_CAPACITY;
    private volatile boolean ropeThrottlingActive = false;
    private volatile String currentConstraintName = "NONE";
    private volatile Instant lastEvaluatedAt = Instant.now();
    private volatile DbrStatus latestDbrStatus;

    public TocOptimizer(TocExecutionGraph graph) {
        this(graph, DEFAULT_MAX_BUFFER_CAPACITY);
    }

    @Autowired
    public TocOptimizer(TocExecutionGraph graph,
                        @Value("${eneik.toc.max-buffer-capacity:15}") long maxBufferCapacity) {
        this.graph = graph;
        this.maxBufferCapacity = maxBufferCapacity;
        this.latestDbrStatus = new DbrStatus(
                "NONE",
                0,
                0.0,
                0.0,
                0,
                this.maxBufferCapacity,
                false,
                this.lastEvaluatedAt,
                computeRecommendation(false, "NONE", 0, this.maxBufferCapacity)
        );
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

        String recommendation = computeRecommendation(ropeThrottlingActive, constraintName, allNodes.size(), maxBufferCapacity);

        lastEvaluatedAt = Instant.now();

        DbrStatus status = new DbrStatus(
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
        this.latestDbrStatus = status;

        return status;
    }

    /**
     * Returns the cached Drum-Buffer-Rope status snapshot from the most recent evaluation.
     * Pure read operation: does not mutate graph nodes, change utilization, or alter constraint state.
     * (LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY / D013)
     */
    public DbrStatus getLatestDbrStatus() {
        DbrStatus current = this.latestDbrStatus;
        return current != null ? current : evaluateConstraintsAndDbr();
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

    @Value("${eneik.toc.max-buffer-capacity:15}")
    public void setConfiguredMaxBufferCapacity(long capacity) {
        if (capacity > 0) {
            setMaxBufferCapacity(capacity);
        }
    }

    public void setMaxBufferCapacity(long maxBufferCapacity) {
        this.maxBufferCapacity = maxBufferCapacity;
        DbrStatus current = this.latestDbrStatus;
        if (current != null) {
            boolean throttle = current.bufferSize() >= maxBufferCapacity;
            this.ropeThrottlingActive = throttle;
            int nodeCount = graph != null ? graph.getAllNodes().size() : 0;
            String rec = computeRecommendation(throttle, current.primaryConstraintNode(), nodeCount, maxBufferCapacity);
            this.latestDbrStatus = new DbrStatus(
                    current.primaryConstraintNode(),
                    current.constraintQueueLength(),
                    current.constraintUtilization(),
                    current.constraintMeanDurationMs(),
                    current.bufferSize(),
                    maxBufferCapacity,
                    throttle,
                    current.lastEvaluatedAt(),
                    rec
            );
        }
    }

    /**
     * Derives Drum-Buffer-Rope recommendation.
     * Enforces ALFRED_TARSKIY_01_FALSIFICATION_HARNESS (D008 False green):
     * A check that cannot fail has no evidentiary value. When the graph has fewer than
     * two instrumented stages or in-flight capacity cannot saturate the buffer, flow optimality
     * is unmeasured/undetermined, not optimal.
     */
    public static String computeRecommendation(boolean throttled, String constraintName, int stageCount, long bufferLimit) {
        if (throttled) {
            return String.format("Throttling active! Elevate priority of work targeting node '%s' and defer non-critical jobs.", constraintName);
        }
        if (stageCount == 0 || "NONE".equals(constraintName)) {
            return "Flow unmeasured: no instrumented stages present in TOC graph; flow status undetermined.";
        }
        if (stageCount == 1) {
            return String.format("Flow unmeasured: single instrumented stage ('%s') with in-flight capacity <= 1 cannot stretch buffer capacity %d; flow status undetermined.",
                    constraintName, bufferLimit);
        }
        return String.format("System flow optimal. Primary constraint: '%s'.", constraintName);
    }
}
