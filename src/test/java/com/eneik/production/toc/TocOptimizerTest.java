package com.eneik.production.toc;

import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.DbrStatus;
import com.eneik.production.toc.model.TocNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laws and invariant tests for {@link TocOptimizer}.
 *
 * Grounding:
 * - ALFRED_TARSKIY_01_FALSIFICATION_HARNESS (D008 False green): A check that cannot fail
 *   has no evidentiary value. When the execution graph has only 1 instrumented stage or
 *   the buffer cannot stretch, the optimizer must report undetermined / unmeasured flow,
 *   never falsely claiming "System flow optimal".
 * - ALONZO_CHERCH_21_DERIVED_CUTOFF (D010 Data lineage loss): Buffer capacity is configurable
 *   and updates Drum-Buffer-Rope recommendations dynamically.
 */
class TocOptimizerTest {

    private TocExecutionGraph graph;
    private TocOptimizer optimizer;

    @BeforeEach
    void setUp() {
        graph = new TocExecutionGraph();
        optimizer = new TocOptimizer(graph);
    }

    @Test
    @DisplayName("Initial baseline status before evaluation must not claim system flow optimal")
    void initialBaselineStatusDoesNotClaimSystemFlowOptimal() {
        DbrStatus initial = optimizer.getLatestDbrStatus();
        assertThat(initial.primaryConstraintNode()).isEqualTo("NONE");
        assertThat(initial.ropeThrottlingActive()).isFalse();
        assertThat(initial.recommendation()).doesNotContain("optimal");
        assertThat(initial.recommendation()).contains("Flow unmeasured");
    }

    @Test
    @DisplayName("Evaluation on empty graph reports flow unmeasured with status undetermined")
    void emptyGraphEvaluationReportsFlowUnmeasured() {
        DbrStatus status = optimizer.evaluateConstraintsAndDbr();
        assertThat(status.primaryConstraintNode()).isEqualTo("NONE");
        assertThat(status.ropeThrottlingActive()).isFalse();
        assertThat(status.recommendation()).doesNotContain("optimal");
        assertThat(status.recommendation()).contains("no instrumented stages present");
    }

    @Test
    @DisplayName("ALFRED_TARSKIY_01_FALSIFICATION_HARNESS: Single instrumented stage refutes 'System flow optimal'")
    void singleInstrumentedStageRefutesSystemFlowOptimal() {
        // Reproduce live Hetzner factory condition: single stage AUTOMERGE_PROCESSING
        TocNode node = graph.getOrCreateNode("AUTOMERGE_PROCESSING");
        node.recordExecution(17_461_000_000L, true); // ~17.4s observed mean

        DbrStatus status = optimizer.evaluateConstraintsAndDbr();

        assertThat(status.primaryConstraintNode()).isEqualTo("AUTOMERGE_PROCESSING");
        assertThat(status.ropeThrottlingActive()).isFalse();
        // Crucial invariant: must NOT claim optimal when buffer limit (15) cannot be stretched by single sequential stage
        assertThat(status.recommendation()).doesNotContain("optimal");
        assertThat(status.recommendation())
                .isEqualTo("Flow unmeasured: single instrumented stage ('AUTOMERGE_PROCESSING') with in-flight capacity <= 1 cannot stretch buffer capacity 15; flow status undetermined.");
    }

    @Test
    @DisplayName("Single instrumented stage enforces throttling when buffer limit is actually breached")
    void singleInstrumentedStageWhenBufferExceededEnforcesThrottling() {
        optimizer.setMaxBufferCapacity(3);
        TocNode node = graph.getOrCreateNode("HEAVY_CALC");
        node.incrementInFlight();
        node.incrementInFlight();
        node.incrementInFlight(); // inFlight = 3 >= 3

        DbrStatus status = optimizer.evaluateConstraintsAndDbr();

        assertThat(status.primaryConstraintNode()).isEqualTo("HEAVY_CALC");
        assertThat(status.ropeThrottlingActive()).isTrue();
        assertThat(status.recommendation())
                .isEqualTo("Throttling active! Elevate priority of work targeting node 'HEAVY_CALC' and defer non-critical jobs.");
        assertThat(optimizer.shouldAdmit("NORMAL_WORKFLOW", 10)).isFalse();
        assertThat(optimizer.shouldAdmit("VIP_WORKFLOW", 90)).isTrue();
    }

    @Test
    @DisplayName("Multi-stage pipeline operating within capacity reports system flow optimal")
    void multiStageFlowWithinBufferLimitReportsSystemFlowOptimal() {
        // Multi-stage pipeline: DISPATCH -> COMPILE -> REVIEW
        TocNode dispatch = graph.getOrCreateNode("DISPATCH");
        TocNode compile = graph.getOrCreateNode("COMPILE");
        TocNode review = graph.getOrCreateNode("REVIEW");

        dispatch.recordExecution(100_000_000L, true);
        compile.recordExecution(5_000_000_000L, true);
        review.recordExecution(1_000_000_000L, true);

        // All within buffer limit 15
        DbrStatus status = optimizer.evaluateConstraintsAndDbr();

        assertThat(status.ropeThrottlingActive()).isFalse();
        assertThat(status.primaryConstraintNode()).isEqualTo("COMPILE");
        assertThat(status.recommendation())
                .isEqualTo("System flow optimal. Primary constraint: 'COMPILE'.");
    }

    @Test
    @DisplayName("ALONZO_CHERCH_21_DERIVED_CUTOFF: setMaxBufferCapacity updates DBR recommendation dynamically")
    void setMaxBufferCapacityUpdatesRecommendationDynamically() {
        TocNode node = graph.getOrCreateNode("AUTOMERGE_PROCESSING");
        optimizer.evaluateConstraintsAndDbr();

        // Dynamically tune capacity to 20
        optimizer.setMaxBufferCapacity(20);
        assertThat(optimizer.getMaxBufferCapacity()).isEqualTo(20);
        assertThat(optimizer.getLatestDbrStatus().maxBufferCapacity()).isEqualTo(20);
        assertThat(optimizer.getLatestDbrStatus().recommendation())
                .contains("cannot stretch buffer capacity 20");

        // Dynamically tune capacity to 0 (ignored by configured setter)
        optimizer.setConfiguredMaxBufferCapacity(0);
        assertThat(optimizer.getMaxBufferCapacity()).isEqualTo(20);

        // Dynamically tune capacity to 5 via configured setter
        optimizer.setConfiguredMaxBufferCapacity(5);
        assertThat(optimizer.getMaxBufferCapacity()).isEqualTo(5);
        assertThat(optimizer.getLatestDbrStatus().maxBufferCapacity()).isEqualTo(5);
        assertThat(optimizer.getLatestDbrStatus().recommendation())
                .contains("cannot stretch buffer capacity 5");
    }

    @Test
    @DisplayName("computeRecommendation truth table coverage")
    void computeRecommendationTruthTable() {
        // Throttling active always overrides
        assertThat(TocOptimizer.computeRecommendation(true, "NODE_X", 1, 15))
                .isEqualTo("Throttling active! Elevate priority of work targeting node 'NODE_X' and defer non-critical jobs.");

        // Stage count 0 or NONE
        assertThat(TocOptimizer.computeRecommendation(false, "NONE", 0, 15))
                .isEqualTo("Flow unmeasured: no instrumented stages present in TOC graph; flow status undetermined.");
        assertThat(TocOptimizer.computeRecommendation(false, "NONE", 2, 15))
                .isEqualTo("Flow unmeasured: no instrumented stages present in TOC graph; flow status undetermined.");

        // Single stage
        assertThat(TocOptimizer.computeRecommendation(false, "SINGLE_STAGE", 1, 15))
                .isEqualTo("Flow unmeasured: single instrumented stage ('SINGLE_STAGE') with in-flight capacity <= 1 cannot stretch buffer capacity 15; flow status undetermined.");

        // Multiple stages within limit
        assertThat(TocOptimizer.computeRecommendation(false, "BOTTLENECK", 3, 15))
                .isEqualTo("System flow optimal. Primary constraint: 'BOTTLENECK'.");
    }
}
