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
    void uncertaintyIsSampledSoonerAndCertaintyIsSampledRarely() {
        Duration base = Duration.ofHours(24);
        Duration floor = Duration.ofHours(1);

        // 2026-08-19: this test previously asserted the opposite, and it was the assertion that was wrong.
        // nextCheckDelay's own contract - stated in its class javadoc - is "Wide interval (little/unstable
        // evidence) -> check soon. Narrow, stable interval -> check rarely." The arithmetic implemented the
        // reverse and this test locked it in, so doc and code contradicted each other with the test siding
        // with the code.
        //
        // The decisive argument is not which reading is prettier, it is which one the observed behaviour
        // punishes. Under the old arithmetic a HEALTHY product hit the one-hour floor and was probed every
        // hour, while a product that had never launched successfully waited ~20 hours between attempts -
        // measured live on test-forty-ninth, where the factory idled a full day with a known blocker it
        // could not re-observe. Uncertainty that is not sampled never resolves.

        // No evidence at all: maximum uncertainty -> sample as soon as the floor allows.
        // The uninformative prior's width is 0.95 (asserted above), so the delay is 24h * 0.05 = 1h12m -
        // just above the floor rather than clamped to it. Asserting exact equality with the floor was my
        // own over-tight expectation, not the contract: what the contract requires is "soon", and near the
        // floor is soon.
        Duration uninformed = BetaPosterior.UNINFORMATIVE_PRIOR.nextCheckDelay(base, floor);
        assertTrue(uninformed.compareTo(floor.multipliedBy(2)) < 0,
                "with no evidence the next check must come close to the floor, not near the base delay");
        assertTrue(uninformed.compareTo(floor) >= 0, "and never below it");

        // One failure: still very uncertain -> still far below the base delay.
        Duration afterOneFailure = BetaPosterior.UNINFORMATIVE_PRIOR.update(false).nextCheckDelay(base, floor);
        assertTrue(afterOneFailure.compareTo(base.dividedBy(2)) < 0,
                "a single failure leaves enough uncertainty that the next check must be well inside half the base delay");

        // Lots of stable evidence: little left to learn -> check rarely, approaching the base delay.
        BetaPosterior confident = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 200; i++) {
            confident = confident.update(true);
        }
        Duration settled = confident.nextCheckDelay(base, floor);
        assertTrue(settled.compareTo(afterOneFailure) > 0,
                "settled evidence must lengthen the delay, not shorten it");
        assertTrue(settled.compareTo(floor) >= 0, "delay must never drop below the floor");
        assertTrue(settled.compareTo(base) <= 0, "delay must never exceed the base delay");
    }

    @Test
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BetaPosterior(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BetaPosterior(1, -1));
    }
}
