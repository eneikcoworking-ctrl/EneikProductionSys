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

    // 2026-08-20: the property the previous test could not see. Interval WIDTH is symmetric - it measures
    // how well the success probability is known and not which side of the scale it sits on - so under the
    // old (1 - width) rule six consecutive failures and six consecutive successes produced the identical
    // delay, 14.3 h on a 24 h base. That is the arithmetic that made this factory look least often at the
    // product it was most certain was broken, while a merged fix sat unobserved. This test fails under that
    // rule and passes under the lower-bound rule; it is the whole reason for the change.
    @Test
    void beingCertainSomethingIsBrokenMustNotBuyTheSameSilenceAsBeingCertainItWorks() {
        Duration base = Duration.ofHours(24);
        Duration floor = Duration.ofHours(1);

        BetaPosterior sixFailures = BetaPosterior.UNINFORMATIVE_PRIOR;
        BetaPosterior sixSuccesses = BetaPosterior.UNINFORMATIVE_PRIOR;
        for (int i = 0; i < 6; i++) {
            sixFailures = sixFailures.update(false);
            sixSuccesses = sixSuccesses.update(true);
        }

        // Identical interval width - which is exactly why width alone cannot decide cadence.
        assertEquals(sixFailures.credibleIntervalWidth(), sixSuccesses.credibleIntervalWidth(), 1e-9,
                "width is symmetric between a confidently broken and a confidently working product");

        Duration afterFailures = sixFailures.nextCheckDelay(base, floor);
        Duration afterSuccesses = sixSuccesses.nextCheckDelay(base, floor);

        assertEquals(floor, afterFailures,
                "a product repeatedly observed broken must be re-checked as soon as the floor allows");
        assertTrue(afterSuccesses.compareTo(afterFailures.multipliedBy(6)) > 0,
                "a product repeatedly observed working has earned a far longer silence");
    }

    // Rarity is earned by evidence of health, so the delay must rise monotonically with that evidence and
    // never with its absence.
    @Test
    void everyAdditionalFailureShortensOrHoldsTheDelayAndEveryAdditionalSuccessLengthensIt() {
        Duration base = Duration.ofHours(24);
        Duration floor = Duration.ofHours(1);

        BetaPosterior failing = BetaPosterior.UNINFORMATIVE_PRIOR;
        Duration previousFailing = failing.nextCheckDelay(base, floor);
        for (int i = 0; i < 5; i++) {
            failing = failing.update(false);
            Duration now = failing.nextCheckDelay(base, floor);
            assertTrue(now.compareTo(previousFailing) <= 0,
                    "a further failure must never push the next check further away");
            previousFailing = now;
        }

        BetaPosterior working = BetaPosterior.UNINFORMATIVE_PRIOR;
        Duration previousWorking = working.nextCheckDelay(base, floor);
        for (int i = 0; i < 5; i++) {
            working = working.update(true);
            Duration now = working.nextCheckDelay(base, floor);
            assertTrue(now.compareTo(previousWorking) >= 0,
                    "a further success must never shorten the interval it has earned");
            previousWorking = now;
        }
        assertTrue(previousWorking.compareTo(base) <= 0, "and never exceeds the base delay");
    }

}
