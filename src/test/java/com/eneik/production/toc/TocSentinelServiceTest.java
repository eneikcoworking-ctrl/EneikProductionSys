package com.eneik.production.toc;

import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.AnomalyReport;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import com.eneik.production.toc.model.TocToken;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TocSentinelServiceTest {

    private TocExecutionGraph graph;
    private TocAnomalyDetector anomalyDetector;
    private TocOptimizer optimizer;
    private TocSentinelService sentinelService;

    @BeforeEach
    void setUp() {
        graph = new TocExecutionGraph();
        anomalyDetector = new TocAnomalyDetector(graph);
        optimizer = new TocOptimizer(graph);
        sentinelService = new TocSentinelService(graph, anomalyDetector, optimizer);
    }

    /**
     * Plan §4.32. A stall report claims a dwell is anomalous FOR THIS NODE, and it does more than log: it
     * sets stallBottleneck, which the TOC machinery reorders the queue by. Measured live 2026-08-29: five
     * reports in one run against a single token, every one printing "mu: 0.00" beside a limit it called
     * dynamic - raised before the node had ever finished a pass, on a node that entered and exited four
     * times in the same three minutes. The counters are in-memory only, so that window reopens on every
     * restart.
     */
    @Test
    void aNodeThatHasNeverFinishedAPassIsNotCalledABottleneck() throws InterruptedException {
        anomalyDetector.setDefaultTimeoutFloorMs(0.0);
        TocToken token = sentinelService.startExecution("WORKFLOW_BLIND", 10);
        sentinelService.enterStep(token, "NEVER_COMPLETED_NODE");
        Thread.sleep(5);

        assertThat(anomalyDetector.scanForStalls()).isEmpty();
        assertThat(graph.getNode("NEVER_COMPLETED_NODE").isStallBottleneck()).isFalse();
    }

    /**
     * The other half, and it is not optional: once the node has finished a pass there IS something to be
     * anomalous against, and the detector must go on saying so. Without this case the change above would
     * also pass with the detector switched off altogether.
     */
    @Test
    void aNodeWithAnObservedDurationIsStillCalledABottleneckWhenADwellExceedsIt() throws InterruptedException {
        anomalyDetector.setDefaultTimeoutFloorMs(0.0);
        anomalyDetector.setSensitivityMultiplier(0.0);
        TocToken token = sentinelService.startExecution("WORKFLOW_OBSERVED", 10);
        sentinelService.enterStep(token, "OBSERVED_NODE");
        graph.getNode("OBSERVED_NODE").recordExecution(0L, true);
        Thread.sleep(5);

        assertThat(anomalyDetector.scanForStalls()).isNotEmpty();
        assertThat(graph.getNode("OBSERVED_NODE").isStallBottleneck()).isTrue();
    }

    @Test
    void testCycleDetectionAndLoopBreak() {
        TocToken token = sentinelService.startExecution("WORKFLOW_A", 10);

        assertThat(sentinelService.enterStep(token, "STEP_1")).isTrue();
        assertThat(sentinelService.enterStep(token, "STEP_2")).isTrue();
        assertThat(sentinelService.enterStep(token, "STEP_3")).isTrue();

        // Attempting to re-enter STEP_1 while still in active call stack (STEP_1 -> STEP_2 -> STEP_3 -> STEP_1)
        boolean allowed = sentinelService.enterStep(token, "STEP_1");
        assertThat(allowed).isFalse();
        assertThat(token.getStatus()).isEqualTo(TocToken.TokenStatus.CYCLE_ABORTED);

        List<AnomalyReport> anomalies = sentinelService.getRecentAnomalies();
        assertThat(anomalies).isNotEmpty();
        assertThat(anomalies.get(0).type()).isEqualTo(AnomalyReport.AnomalyType.CYCLE_DETECTED);
        assertThat(anomalies.get(0).actionTaken()).contains("Aborted token");
    }

    @Test
    void testDynamicStallDetection() throws InterruptedException {
        anomalyDetector.setDefaultTimeoutFloorMs(50.0);
        anomalyDetector.setSensitivityMultiplier(1.0);

        // Record two very fast executions (e.g. ~5ms) to establish baseline mean/stdDev for STEP_FAST
        TocToken prep1 = sentinelService.startExecution("PREP", 5);
        sentinelService.enterStep(prep1, "STEP_FAST");
        Thread.sleep(10);
        sentinelService.exitStep(prep1, "STEP_FAST", true);

        TocToken prep2 = sentinelService.startExecution("PREP", 5);
        sentinelService.enterStep(prep2, "STEP_FAST");
        Thread.sleep(10);
        sentinelService.exitStep(prep2, "STEP_FAST", true);

        // Now start a token that hangs in STEP_FAST
        TocToken slowToken = sentinelService.startExecution("SLOW_JOB", 5);
        sentinelService.enterStep(slowToken, "STEP_FAST");

        // Wait longer than dynamic threshold
        Thread.sleep(200);

        List<AnomalyReport> stalls = anomalyDetector.scanForStalls();
        assertThat(stalls).isNotEmpty();

        TocNode node = graph.getNode("STEP_FAST");
        assertThat(node).isNotNull();
        assertThat(node.isStallBottleneck()).isTrue();
    }

    @Test
    void testResourceDeadlockDetectionInWaitForGraph() {
        TocToken token1 = sentinelService.startExecution("TX_1", 10);
        TocToken token2 = sentinelService.startExecution("TX_2", 20); // Higher priority

        sentinelService.enterStep(token1, "NODE_X");
        sentinelService.enterStep(token2, "NODE_Y");

        sentinelService.acquireResource(token1, "RES_ALPHA");
        sentinelService.acquireResource(token2, "RES_BETA");

        // Token 1 waits for RES_BETA
        boolean deadlock1 = sentinelService.waitResource(token1, "RES_BETA");
        assertThat(deadlock1).isFalse();

        // Token 2 waits for RES_ALPHA -> completes cycle: Token1 -> Token2 -> Token1
        boolean deadlock2 = sentinelService.waitResource(token2, "RES_ALPHA");
        assertThat(deadlock2).isTrue();

        // Token 1 (lower priority) should be chosen as victim and aborted
        assertThat(token1.getStatus()).isEqualTo(TocToken.TokenStatus.DEADLOCK_ABORTED);
        assertThat(token2.getStatus()).isEqualTo(TocToken.TokenStatus.ACTIVE);

        List<AnomalyReport> anomalies = sentinelService.getRecentAnomalies();
        assertThat(anomalies.stream().anyMatch(a -> a.type() == AnomalyReport.AnomalyType.DEADLOCK_DETECTED)).isTrue();
    }

    @Test
    void testTocConstraintIdentificationAndDbrThrottling() {
        sentinelService.setMaxBufferCapacity(3);

        // Simulate 5 tokens queuing in HEAVY_CALC
        for (int i = 0; i < 5; i++) {
            TocToken t = sentinelService.startExecution("BATCH", 10);
            sentinelService.enterStep(t, "HEAVY_CALC");
        }

        sentinelService.periodicWatchdog();
        DbrStatus status = sentinelService.getDbrStatus();
        assertThat(status.primaryConstraintNode()).isEqualTo("HEAVY_CALC");
        assertThat(status.ropeThrottlingActive()).isTrue();

        // Normal priority token (10) should be throttled
        TocToken lowPrio = sentinelService.startExecution("LOW_PRIO_JOB", 10);
        assertThat(lowPrio.getStatus()).isEqualTo(TocToken.TokenStatus.THROTTLED);

        // High priority token (90) bypasses DBR throttle
        TocToken highPrio = sentinelService.startExecution("VIP_JOB", 90);
        assertThat(highPrio.getStatus()).isEqualTo(TocToken.TokenStatus.ACTIVE);
    }

    /**
     * Flaw 1: getDbrStatus() must be a pure read and must not mutate graph or constraint flags.
     * (LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY / D013)
     */
    @Test
    void getDbrStatusDoesNotMutateGraphOrConstraintState() {
        TocToken token = sentinelService.startExecution("INVARIANT_WORKFLOW", 10);
        sentinelService.enterStep(token, "STAGE_X");
        sentinelService.periodicWatchdog();

        DbrStatus initialStatus = sentinelService.getDbrStatus();
        TocNode node = sentinelService.getNode("STAGE_X");
        assertThat(node).isNotNull();

        double initialUtilization = node.getUtilization();
        boolean initialConstraintFlag = node.isPrimaryConstraint();
        java.time.Instant initialEvaluatedAt = initialStatus.lastEvaluatedAt();

        // 10 successive reads without watchdog ticks must be completely pure
        for (int i = 0; i < 10; i++) {
            DbrStatus readStatus = sentinelService.getDbrStatus();
            assertThat(readStatus.primaryConstraintNode()).isEqualTo(initialStatus.primaryConstraintNode());
            assertThat(readStatus.lastEvaluatedAt()).isEqualTo(initialEvaluatedAt);
            assertThat(readStatus.constraintUtilization()).isEqualTo(initialStatus.constraintUtilization());
            assertThat(readStatus.bufferSize()).isEqualTo(initialStatus.bufferSize());
            assertThat(node.getUtilization()).isEqualTo(initialUtilization);
            assertThat(node.isPrimaryConstraint()).isEqualTo(initialConstraintFlag);
        }
    }

    /**
     * Flaw 2: Leaky component getters (getGraph, getAnomalyDetector, getOptimizer) are eliminated.
     * Encapsulation is enforced and callers use guarded query methods.
     * (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004)
     */
    @Test
    void partWholeEncapsulationEnforcedWithoutLeakyComponentGetters() {
        // Reflection check: getters getGraph, getAnomalyDetector, getOptimizer must not exist
        Class<?> clazz = sentinelService.getClass();
        assertThat(java.util.Arrays.stream(clazz.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("getGraph", "getAnomalyDetector", "getOptimizer");

        // Encapsulated operations work cleanly through TocSentinelService
        sentinelService.setMaxBufferCapacity(42);
        assertThat(sentinelService.getMaxBufferCapacity()).isEqualTo(42);

        TocToken token = sentinelService.startExecution("ENCAPSULATION_TEST", 15);
        assertThat(sentinelService.getToken(token.getTokenId())).isNotNull();
        assertThat(sentinelService.getActiveTokenCount()).isGreaterThanOrEqualTo(1);

        sentinelService.enterStep(token, "STEP_ENCAPSULATED");
        assertThat(sentinelService.getAllNodes()).isNotEmpty();
        assertThat(sentinelService.getNode("STEP_ENCAPSULATED")).isNotNull();

        // Collections returned must be unmodifiable (safe snapshots)
        var nodes = sentinelService.getAllNodes();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () ->
                nodes.add(new TocNode("ILLEGAL_EXTERNAL_MUTATION"))
        );

        var edges = sentinelService.getEdges();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () ->
                edges.add(new com.eneik.production.toc.model.TocEdge("A", "B"))
        );

        sentinelService.exitStep(token, "STEP_ENCAPSULATED", true);
        sentinelService.endExecution(token, true);
        assertThat(sentinelService.getCompletedCountAllNodes()).isGreaterThanOrEqualTo(1);
    }

    /**
     * (1) Node with past completions but zero active work in flight relaxes to max bound (idle watchdog).
     * Prevents watchdog from spinning when the workflow has stopped.
     */
    @Test
    void nodeWithCompletionsAndZeroWorkInFlightRelaxesToMaxBound() {
        // Complete 5 passes on STEP_DONE to establish mean duration of 1000ms
        TocNode node = graph.getOrCreateNode("STEP_DONE");
        for (int i = 0; i < 5; i++) {
            node.recordExecution(1_000_000_000L, true);
        }
        assertThat(node.getCompletedCount()).isEqualTo(5L);
        assertThat(node.getMeanDurationMs()).isEqualTo(1000.0);

        // All tokens finished: active token count is strictly zero
        assertThat(sentinelService.getActiveTokenCount()).isEqualTo(0);

        // When nothing is in flight, watchdog MUST relax to maxCadenceMs
        assertThat(sentinelService.computeDerivedWatchdogCadenceMs()).isEqualTo(sentinelService.getMaxCadenceMs());
        assertThat(sentinelService.computeDerivedWatchdogCadenceMs()).isEqualTo(10000L);
    }

    /**
     * (2) Node in flight with observed mean duration of 1000ms derives cadence of 500ms (min(mean)/2).
     */
    @Test
    void nodeInFlightWithMean1000msDerivesCadence500ms() {
        // Establish observed mean of 1000ms on STEP_IN_FLIGHT
        TocNode node = graph.getOrCreateNode("STEP_IN_FLIGHT");
        node.recordExecution(1_000_000_000L, true);
        assertThat(node.getMeanDurationMs()).isEqualTo(1000.0);

        // Start active token in flight on this node
        TocToken token = sentinelService.startExecution("ACTIVE_FLOW", 10);
        sentinelService.enterStep(token, "STEP_IN_FLIGHT");
        assertThat(sentinelService.getActiveTokenCount()).isEqualTo(1);
        assertThat(node.getInFlightCount()).isEqualTo(1L);

        // Derived cadence is 1000 / 2 = 500ms
        assertThat(sentinelService.computeDerivedWatchdogCadenceMs()).isEqualTo(500L);
    }

    /**
     * (3) Active work with mean 100ms clamps to lower bound (250ms); mean 60000ms clamps to upper bound (10000ms).
     */
    @Test
    void activeWorkClampsToLowerAndUpperBounds() {
        sentinelService.setCadenceBounds(250L, 10000L);

        // Fast step: mean 100ms -> derived 50ms -> clamps to min (250ms)
        TocNode fastNode = graph.getOrCreateNode("FAST_STEP");
        fastNode.recordExecution(100_000_000L, true);
        TocToken fastToken = sentinelService.startExecution("FAST_FLOW", 10);
        sentinelService.enterStep(fastToken, "FAST_STEP");
        assertThat(sentinelService.computeDerivedWatchdogCadenceMs()).isEqualTo(250L);

        // Exit fast token
        sentinelService.exitStep(fastToken, "FAST_STEP", true);
        sentinelService.endExecution(fastToken, true);

        // Slow step in isolated graph: mean 60000ms -> derived 30000ms -> clamps to max (10000ms)
        TocExecutionGraph slowGraph = new TocExecutionGraph();
        TocSentinelService slowSentinel = new TocSentinelService(slowGraph, new TocAnomalyDetector(slowGraph), new TocOptimizer(slowGraph));
        slowSentinel.setCadenceBounds(250L, 10000L);

        TocNode slowNode = slowGraph.getOrCreateNode("SLOW_STEP");
        slowNode.recordExecution(60_000_000_000L, true);
        TocToken slowToken = slowSentinel.startExecution("SLOW_FLOW", 10);
        slowSentinel.enterStep(slowToken, "SLOW_STEP");
        assertThat(slowSentinel.computeDerivedWatchdogCadenceMs()).isEqualTo(10000L);
    }

    /**
     * (4) Step rejected as cycle does NOT increment node in-flight counter.
     */
    @Test
    void rejectedCycleStepDoesNotIncrementInFlightCounter() {
        TocToken token = sentinelService.startExecution("CYCLE_FLOW", 10);
        sentinelService.enterStep(token, "NODE_1");
        sentinelService.enterStep(token, "NODE_2");

        TocNode node1 = sentinelService.getNode("NODE_1");
        assertThat(node1.getInFlightCount()).isEqualTo(1L);

        // Attempting to re-enter NODE_1 triggers cycle detection and rejection
        boolean allowed = sentinelService.enterStep(token, "NODE_1");
        assertThat(allowed).isFalse();
        assertThat(token.getStatus()).isEqualTo(TocToken.TokenStatus.CYCLE_ABORTED);

        // inFlightCount of NODE_1 must NOT be incremented
        assertThat(node1.getInFlightCount()).isEqualTo(1L);
    }

    /**
     * Watchdog cadence adapts dynamically to actual observed step throughput.
     * Refutation test: Cadence MUST change when observed step durations change.
     */
    @Test
    void derivedCadenceAdaptsDynamicallyToObservedStepDurations() {
        sentinelService.setCadenceBounds(100L, 10000L);

        TocToken token = sentinelService.startExecution("ADAPT_FLOW", 10);
        sentinelService.enterStep(token, "STEP_A");

        // Record execution of 1200ms on STEP_A -> half is 600ms
        TocNode nodeA = graph.getOrCreateNode("STEP_A");
        nodeA.recordExecution(1_200_000_000L, true);

        long cadence1 = sentinelService.computeDerivedWatchdogCadenceMs();
        assertThat(cadence1).isEqualTo(600L);

        // Record slower execution of 3000ms on STEP_B -> shortest is still STEP_A (1200ms), cadence remains 600ms
        TocNode nodeB = graph.getOrCreateNode("STEP_B");
        nodeB.recordExecution(3_000_000_000L, true);
        assertThat(sentinelService.computeDerivedWatchdogCadenceMs()).isEqualTo(600L);

        // Record faster execution of 500ms on STEP_C -> shortest becomes 500ms, cadence must adapt to 250ms
        TocNode nodeC = graph.getOrCreateNode("STEP_C");
        nodeC.recordExecution(500_000_000L, true);

        long cadence2 = sentinelService.computeDerivedWatchdogCadenceMs();
        assertThat(cadence2).isEqualTo(250L);
        assertThat(cadence2).isNotEqualTo(cadence1); // Direct proof of dynamic adaptability
    }

    /**
     * Single-Writer Lifecycle Ownership (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004).
     * Node inFlight counter is owned and mutated strictly by TocSentinelService (enterStep/exitStep).
     */
    @Test
    void inFlightCounterOwnedExclusivelyBySentinelServiceLifecycle() {
        TocToken token = sentinelService.startExecution("OWNERSHIP_FLOW", 10);
        assertThat(token.getStatus()).isEqualTo(TocToken.TokenStatus.ACTIVE);

        // Step enter increments inFlight strictly within TocSentinelService
        sentinelService.enterStep(token, "STAGE_EXCLUSIVE");
        TocNode node = sentinelService.getNode("STAGE_EXCLUSIVE");
        assertThat(node.getInFlightCount()).isEqualTo(1L);

        // Step exit decrements inFlight strictly within TocSentinelService
        sentinelService.exitStep(token, "STAGE_EXCLUSIVE", true);
        assertThat(node.getInFlightCount()).isEqualTo(0L);
        assertThat(node.getCompletedCount()).isEqualTo(1L);
    }

    @Test
    void watchdogThrottlingSubordinationAndExplicitRefresh() {
        sentinelService.setMaxBufferCapacity(2);

        TocToken t1 = sentinelService.startExecution("FLOW_1", 10);
        sentinelService.enterStep(t1, "BOTTLENECK_STEP");

        DbrStatus beforeRefresh = sentinelService.getDbrStatus();
        assertThat(beforeRefresh.ropeThrottlingActive()).isFalse();

        // Explicit refresh recalculates constraints immediately
        DbrStatus refreshed = sentinelService.refreshDbrStatus();
        assertThat(refreshed.primaryConstraintNode()).isEqualTo("BOTTLENECK_STEP");

        TocToken t2 = sentinelService.startExecution("FLOW_2", 10);
        sentinelService.enterStep(t2, "BOTTLENECK_STEP");

        sentinelService.periodicWatchdog();
        DbrStatus statusAfterBufferExceeded = sentinelService.getDbrStatus();
        assertThat(statusAfterBufferExceeded.ropeThrottlingActive()).isTrue();

        // Throttling subordinations hold
        TocToken blocked = sentinelService.startExecution("FLOW_3", 10);
        assertThat(blocked.getStatus()).isEqualTo(TocToken.TokenStatus.THROTTLED);
    }

    /**
     * ALFRED_TARSKIY_01_FALSIFICATION_HARNESS (D008 False green):
     * Single instrumented stage with buffer capacity 15 cannot stretch the rope, so flow status
     * must be reported as unmeasured/undetermined, not falsely claimed as 'System flow optimal'.
     */
    @Test
    void singleInstrumentedStageDoesNotClaimSystemFlowOptimal() {
        TocToken token = sentinelService.startExecution("FLOW_SINGLE", 10);
        sentinelService.enterStep(token, "STAGE_SOLO");
        sentinelService.periodicWatchdog();

        DbrStatus status = sentinelService.getDbrStatus();
        assertThat(status.primaryConstraintNode()).isEqualTo("STAGE_SOLO");
        assertThat(status.ropeThrottlingActive()).isFalse();
        assertThat(status.recommendation()).doesNotContain("optimal");
        assertThat(status.recommendation()).contains("Flow unmeasured: single instrumented stage ('STAGE_SOLO') with in-flight capacity <= 1 cannot stretch buffer capacity");
    }

    /**
     * When multiple pipeline stages exist and flow is within buffer capacity,
     * the system flow is legitimately optimal.
     */
    @Test
    void multiStageFlowWithinCapacityReportsSystemFlowOptimal() {
        TocToken t1 = sentinelService.startExecution("FLOW_MULTI_1", 10);
        sentinelService.enterStep(t1, "STAGE_DISPATCH");

        TocToken t2 = sentinelService.startExecution("FLOW_MULTI_2", 10);
        sentinelService.enterStep(t2, "STAGE_COMPILE");

        sentinelService.periodicWatchdog();

        DbrStatus status = sentinelService.getDbrStatus();
        assertThat(status.ropeThrottlingActive()).isFalse();
        assertThat(status.recommendation()).contains("System flow optimal");
    }
}
