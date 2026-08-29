package com.eneik.production.toc;

import com.eneik.production.toc.model.TocNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Action plan 4.17. Measured 2026-08-29 on the live circuit: AUTOMERGE_PROCESSING logged
 * {@code Dynamic Limit: 5000.00 ms (mu: 41146.00 ms, stdDev: 0.00 ms)} five times and flagged dwells of
 * 11950, 11989, 13952 and 13989 ms as stalls - a third of the node's own measured mean. Eleven false stall
 * flags in twenty minutes, the loudest warning in the log, because one completed observation was treated as
 * none.
 */
class TocNodeTimeoutTest {

    private static final double FLOOR = 5000.0;
    private static final double SENSITIVITY = 3.0;

    @Test
    void withNothingObservedTheFloorStillDecides() {
        TocNode node = new TocNode("AUTOMERGE_PROCESSING");
        assertEquals(FLOOR, node.getDynamicTimeoutLimitMs(SENSITIVITY, FLOOR),
                "with no observation at all there is nothing to derive a limit from");
    }

    @Test
    void oneObservationIsEvidenceAndIsUsed() {
        TocNode node = new TocNode("AUTOMERGE_PROCESSING");
        node.recordExecution(41_146L * 1_000_000L, true);

        double limit = node.getDynamicTimeoutLimitMs(SENSITIVITY, FLOOR);

        assertEquals(41146.0, limit, 0.5,
                "the mean is known after one pass - Welford - so the limit is the mean, not the floor");
        assertTrue(limit > 13989, "a dwell of 13989 ms must no longer read as a stall on this node");
    }

    @Test
    void theFloorStillWinsWhenTheObservationIsFasterThanIt() {
        TocNode node = new TocNode("FAST_NODE");
        node.recordExecution(200L * 1_000_000L, true);

        assertEquals(FLOOR, node.getDynamicTimeoutLimitMs(SENSITIVITY, FLOOR),
                "defaultFloorMs goes on being a floor");
    }

    @Test
    void deviationStillWidensTheLimitOnceThereIsSpread() {
        TocNode node = new TocNode("AUTOMERGE_PROCESSING");
        node.recordExecution(30_000L * 1_000_000L, true);
        node.recordExecution(40_000L * 1_000_000L, true);

        assertTrue(node.getDynamicTimeoutLimitMs(SENSITIVITY, FLOOR) > 40000,
                "with spread the limit is mean plus sensitivity times deviation, as before");
    }
}
