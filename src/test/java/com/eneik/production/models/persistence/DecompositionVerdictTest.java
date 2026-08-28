package com.eneik.production.models.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spending the budget means two different things, and the difference decides whether a project can move.
 *
 * <p>Measured on test-fiftieth, 2026-08-28: six briefs had spent all three attempts while the single
 * persistent compiler worker was busy, so not one message was ever sent. F42 nonetheless recorded "the
 * brief needs a human reading, not another retry" - a claim about the BRIEF drawn from evidence about the
 * FACTORY - and left the status at `pending` on the stated reasoning that "no downstream consumer changes
 * behaviour". Two consumers did: FlowSpineService counts pending into pendingWishlist, and
 * ClientDeliverableReadinessService requires every root to be converted_to_task or dismissed. Either alone
 * pins the project in DECOMPOSING permanently, which denies RECOVER_FAILED_FRONTIER while 27 tasks wait.
 *
 * <p>Charter invariant 12 is the reason the witness exists at all: the dispatcher cannot be the only party
 * testifying that the compiler was asked.
 */
class DecompositionVerdictTest {

    private WishlistEntity brief(int attempts, Instant reachedAt) {
        WishlistEntity w = new WishlistEntity();
        w.setCompileAttempts(attempts);
        w.setLastCompileReachedAt(reachedAt);
        return w;
    }

    @Test
    void withBudgetLeftNeitherVerdictApplies() {
        WishlistEntity w = brief(WishlistEntity.COMPILE_ATTEMPT_BUDGET - 1, null);
        assertFalse(w.decompositionExhausted());
        assertFalse(w.decompositionRefused());
        assertFalse(w.decompositionUnreached());
    }

    @Test
    void askedAndSilentIsAboutTheBrief() {
        // The compiler was genuinely reached and produced no decomposition inside the budget. This is
        // absorbing and must leave the flow-state denominators, or the project never leaves DECOMPOSING.
        WishlistEntity w = brief(WishlistEntity.COMPILE_ATTEMPT_BUDGET, Instant.now());
        assertTrue(w.decompositionExhausted());
        assertTrue(w.decompositionRefused());
        assertFalse(w.decompositionUnreached());
    }

    @Test
    void neverAskedIsAboutTheFactoryAndMustNotBeAbsorbing() {
        // The exact measured case: budget spent against a busy worker, nothing ever sent. Nothing is
        // established about the brief, so it must NOT be excluded as "can never reach done" - that would
        // be invariant 8 applied in the wrong direction, hiding the brief instead of the blockage.
        WishlistEntity w = brief(WishlistEntity.COMPILE_ATTEMPT_BUDGET, null);
        assertTrue(w.decompositionExhausted());
        assertFalse(w.decompositionRefused());
        assertTrue(w.decompositionUnreached());
    }

    @Test
    void theTwoVerdictsAreMutuallyExclusiveAndCoverExhaustion() {
        for (Instant reached : new Instant[]{null, Instant.now()}) {
            WishlistEntity w = brief(WishlistEntity.COMPILE_ATTEMPT_BUDGET + 5, reached);
            assertTrue(w.decompositionRefused() ^ w.decompositionUnreached(),
                    "exhaustion must resolve to exactly one verdict");
        }
    }

    @Test
    void aReachedBriefStillInsideItsBudgetIsNotYetRefused() {
        // Being reached is not the same as being answered exhaustively: the verdict needs BOTH the budget
        // spent and the channel witness.
        WishlistEntity w = brief(1, Instant.now());
        assertFalse(w.decompositionRefused());
    }
}
