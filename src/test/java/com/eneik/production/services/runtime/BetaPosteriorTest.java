package com.eneik.production.services.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pins down the exact worked examples given to the operator (and independently verified by hand:
// Beta(alpha,1) has closed-form CDF(x) = x^alpha, so its quantiles are p^(1/alpha) - no library trust
// required, these are checked against a derivation, not just "whatever the code returns").
class BetaPosteriorTest {

    private static final double TOLERANCE = 0.01;

    @Test
    void uninformativePriorHasAlmostTheFullUnitIntervalAsItsCredibleInterval() {
        // Beta(1,1) is exactly uniform on [0,1] - its 95% credible interval is exactly [0.025, 0.975].
        assertEquals(0.95, BetaPosterior.UNINFORMATIVE_PRIOR.credibleIntervalWidth(), TOLERANCE);
    }

    @Test
    void tenConsecutiveSuccessesNarrowTheIntervalToRoughlyAQuarter() {
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 10; i++) {
            posterior = posterior.update(true);
        }
        assertEquals(11.0, posterior.alpha());
        assertEquals(1.0, posterior.beta());
        // Hand-derived: CDF(x) = x^11, quantiles 0.025^(1/11)=0.715, 0.975^(1/11)=0.998, width=0.283.
        assertEquals(0.283, posterior.credibleIntervalWidth(), TOLERANCE);
    }

    @Test
    void fiftyConsecutiveSuccessesNarrowTheIntervalToRoughlySevenPercent() {
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 50; i++) {
            posterior = posterior.update(true);
        }
        // Hand-derived: CDF(x) = x^51, quantiles 0.025^(1/51)=0.930, 0.975^(1/51)=0.9995, width=0.069.
        assertEquals(0.069, posterior.credibleIntervalWidth(), TOLERANCE);
    }

    @Test
    void aConsistentRunOfTheSameOutcomeMonotonicallyNarrowsTheInterval() {
        // Deliberately NOT true of mixed evidence in general (caught by an earlier, wrong version of
        // this test): a surprising observation - e.g. a failure right after a long run of successes -
        // can genuinely WIDEN the interval for that one step, because it shifts the distribution's
        // shape (Beta variance is maximized near alpha=beta), not just its sample size. Monotonic
        // narrowing only holds, provably, for a consistent run of the same outcome.
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        double previousWidth = posterior.credibleIntervalWidth();
        for (int i = 0; i < 30; i++) {
            posterior = posterior.update(true);
            double width = posterior.credibleIntervalWidth();
            assertTrue(width <= previousWidth + 1e-9,
                    "width must never increase after adding a consistent observation: " + previousWidth + " -> " + width);
            previousWidth = width;
        }
    }

    @Test
    void aSurprisingObservationAfterAConsistentRunCanWidenTheIntervalForOneStep() {
        // The subtlety above, pinned down explicitly rather than left as a discovered surprise: this is
        // correct Beta-distribution behavior, not a bug - a genuine failure after 20 straight successes
        // is real, informative evidence that the true rate is NOT as extreme as the run suggested, and
        // the honest posterior must reflect that with more spread, not less.
        BetaPosterior posterior = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 20; i++) {
            posterior = posterior.update(true);
        }
        double widthBeforeSurprise = posterior.credibleIntervalWidth();
        BetaPosterior afterSurprise = posterior.update(false);
        assertTrue(afterSurprise.credibleIntervalWidth() > widthBeforeSurprise,
                "a failure right after a long success run should widen the interval, not narrow it");
    }

    @Test
    void nextCheckDelayIsProportionalToUncertaintyAndNeverBelowTheFloor() {
        Duration base = Duration.ofHours(24);
        Duration floor = Duration.ofHours(1);

        // Wide interval (no evidence yet) -> close to the full base delay.
        Duration wideDelay = BetaPosterior.UNINFORMATIVE_PRIOR.nextCheckDelay(base, floor);
        assertTrue(wideDelay.toHours() >= 20, "uninformed posterior should check almost as often as the base delay");

        // Narrow interval (lots of stable evidence) -> much shorter than base, but never below the floor.
        BetaPosterior confident = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 200; i++) {
            confident = confident.update(true);
        }
        Duration narrowDelay = confident.nextCheckDelay(base, floor);
        assertTrue(narrowDelay.compareTo(wideDelay) < 0, "more evidence must shorten the delay, never lengthen it");
        assertTrue(narrowDelay.compareTo(floor) >= 0, "delay must never drop below the floor, however confident");
    }

    @Test
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BetaPosterior(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BetaPosterior(1, -1));
    }
}
