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
