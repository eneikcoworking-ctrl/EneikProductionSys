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
        optimizer.setMaxBufferCapacity(3);

        // Simulate 5 tokens queuing in HEAVY_CALC
        for (int i = 0; i < 5; i++) {
            TocToken t = sentinelService.startExecution("BATCH", 10);
            sentinelService.enterStep(t, "HEAVY_CALC");
        }

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
}
