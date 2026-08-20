package com.eneik.production.services.runtime;

import org.apache.commons.math3.distribution.BetaDistribution;

import java.time.Duration;

/**
 * Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: the real math behind
 * "when to check the active product's launchability again," worked through with the operator and
 * agreed as the mandatory shape - never a fixed schedule, always derived from accumulated evidence.
 *
 * Belief about the product's true launch-success probability is a Beta(alpha, beta) posterior,
 * starting from the same uninformative prior (alpha=1, beta=1) already used elsewhere in this
 * codebase for Beta-Bernoulli estimation (AccountHealthService's F2 lever). Each observation updates
 * it by one count - no smoothing, no decay, exact conjugate Bayesian update.
 *
 * The 95% credible-interval WIDTH is the uncertainty signal that drives cadence:
 * nextCheckDelay = baseDelay * width(alpha, beta). Wide interval (little/unstable evidence) -> check
 * soon. Narrow, stable interval -> check rarely. This process has no terminal state - width never
 * reaches exactly zero, so a delay is always eventually scheduled again, however long. This is
 * deliberately NOT a normal-distribution approximation: for beta staying at 1 (an unbroken run of
 * successes), Beta(alpha,1) is heavily left-skewed and bounded at 1 - a symmetric normal approximation
 * would put probability mass above 1, which is meaningless for a probability. The real Beta quantile
 * function (via Apache Commons Math's BetaDistribution) is used instead.
 */
public record BetaPosterior(double alpha, double beta) {

    public static final BetaPosterior UNINFORMATIVE_PRIOR = new BetaPosterior(1.0, 1.0);
    private static final double CREDIBLE_LEVEL = 0.95;
    private static final double TAIL = (1.0 - CREDIBLE_LEVEL) / 2.0;

    public BetaPosterior {
        if (alpha <= 0 || beta <= 0) {
            throw new IllegalArgumentException("alpha and beta must be strictly positive, got alpha=" + alpha + " beta=" + beta);
        }
    }

    public BetaPosterior update(boolean success) {
        return success ? new BetaPosterior(alpha + 1, beta) : new BetaPosterior(alpha, beta + 1);
    }

    /** Posterior mean estimate of the true success probability. */
    public double mean() {
        return alpha / (alpha + beta);
    }

    /**
     * Width of the real 95% Beta credible interval - the honest uncertainty measure this whole plan
     * is built on. 0 observations (alpha=1,beta=1): width ~0.95 (almost total uncertainty). Many
     * consistent observations: width shrinks toward 0, never reaching it exactly.
     */
    public double credibleIntervalWidth() {
        BetaDistribution distribution = new BetaDistribution(alpha, beta);
        double lower = distribution.inverseCumulativeProbability(TAIL);
        double upper = distribution.inverseCumulativeProbability(1.0 - TAIL);
        return upper - lower;
    }

    /**
     * Lower end of the same 95% credible interval: the pessimistic estimate of the true success
     * probability - how well the product can be shown to work, never how well it might. 0 observations
     * (alpha=1,beta=1): ~0.025. An unbroken run of successes drives it toward 1, never reaching it.
     */
    public double credibleIntervalLowerBound() {
        return new BetaDistribution(alpha, beta).inverseCumulativeProbability(TAIL);
    }

    /**
     * The adaptive-cadence rule itself, agreed with the operator as the mandatory replacement for any
     * hard-coded schedule: delay is proportional to current uncertainty, bounded below so a truly
     * saturated posterior still checks eventually rather than functionally stopping forever.
     */
    public Duration nextCheckDelay(Duration baseDelay, Duration minimumDelay) {
        // 2026-08-19: the multiplier is (1 - width), not width. This method's own contract, stated eleven
        // lines above, is "Wide interval (little/unstable evidence) -> check soon. Narrow, stable interval
        // -> check rarely." The arithmetic did the opposite: delay = base * width means a wide interval
        // produced the LONGEST delay and a narrow one the shortest, so the system sampled least exactly
        // when it knew least - and uncertainty that is not sampled never resolves.
        //
        // Measured consequence on test-forty-ninth: one observation, launch_success=false, posterior
        // Beta(1,2), width ~0.83 -> next check ~20 hours. A product that has NEVER launched successfully
        // was checked once a day, while a product launching reliably (width -> 0) would have been checked
        // every hour under the minimum clamp. The factory idled for a day with a known, unaddressed
        // blocker because the constraint could not be re-observed.
        //
        // With (1 - width) the documented contract holds exactly: width -> 1 gives a delay below the
        // minimum and is clamped to it (check soon), width -> 0 gives the full base delay (check rarely).
        // No new constant, no new branch - the same two numbers, one of them subtracted from one.
        // 2026-08-20: the multiplier is the credible interval's LOWER BOUND, not (1 - width). Interval
        // width measures how well the success probability is KNOWN and says nothing about which side of the
        // scale it sits on, so "confidently working" and "confidently broken" produced the identical delay -
        // computed across this posterior's whole range, six consecutive successes and six consecutive
        // failures both gave 14.3 hours on a 24 h base. Measured consequence on test-forty-ninth: four
        // recorded failures narrowed the interval to 0.596 and pushed the next check to 9.7 hours, so the
        // product the factory was most certain was broken became the one it looked at least often - while a
        // fix for it was already merged and waiting to be seen.
        //
        // The lower bound is the pessimistic estimate: rarity has to be EARNED by evidence of health, and
        // uncertainty always counts against the product rather than for it. Same asymmetry the whole
        // falsification stance rests on - a claim is never proven, only not yet refuted, so the interval's
        // lower end is the only end that may buy silence. Beta(1,5) (four failures) now gives ~0.005 -> the
        // floor; Beta(7,1) (six successes) gives ~0.59 -> 14.2 h. Ignorance, Beta(1,1), gives ~0.025 -> the
        // floor, which is correct: nothing is known, so look soon.
        //
        // Rollback is this one expression: restore `(1.0 - credibleIntervalWidth())` and the previous
        // behaviour returns exactly, with no other state to undo.
        double confidenceItWorks = credibleIntervalLowerBound();
        Duration scaled = Duration.ofMillis(Math.round(baseDelay.toMillis() * confidenceItWorks));
        return scaled.compareTo(minimumDelay) < 0 ? minimumDelay : scaled;
    }
}
