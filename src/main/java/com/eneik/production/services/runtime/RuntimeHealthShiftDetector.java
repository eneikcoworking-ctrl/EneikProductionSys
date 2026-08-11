package com.eneik.production.services.runtime;

import org.apache.commons.math3.distribution.BinomialDistribution;

import java.util.List;

/**
 * Phase 2 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: "is this a real shift, or
 * just bad luck" - a genuinely different mathematical question from Phase 1's BetaPosterior (which
 * estimates a single stable rate). This answers whether the rate itself has changed.
 *
 * The plan's original draft proposed reusing ProcessControlService's u-chart machinery. On inspection
 * that class's subgroup unit is an эпик (FeatureEntity), sequenced by completion order WITHIN one
 * project - a structurally different thing from a time-ordered series of individual runtime
 * observations. Forcing this into that shape would have been reuse for its own sake, not real fit -
 * so this is a small, separately correct tool instead, same statistical family (binomial rate testing)
 * but the right shape for THIS data.
 *
 * The test itself, with real numbers (matches what was walked through with the operator): given a
 * historical baseline failure rate p0 estimated from observations BEFORE the recent window, and k
 * failures observed in the most recent w observations, compute P(X >= k) where X ~ Binomial(w, p0) -
 * the exact probability of seeing at least this many failures by chance alone if nothing had actually
 * changed. A small p-value (default threshold 0.001) means this is not noise.
 */
public final class RuntimeHealthShiftDetector {

    public static final int DEFAULT_RECENT_WINDOW = 5;
    public static final int DEFAULT_MINIMUM_BASELINE_SAMPLES = 10;
    public static final double DEFAULT_SIGNIFICANCE_THRESHOLD = 0.001;
    public static final double DEFAULT_EXPECTED_SUCCESS_RATE = 0.9;
    public static final double DEFAULT_ABSOLUTE_SIGNIFICANCE_THRESHOLD = 0.01;

    private RuntimeHealthShiftDetector() {
    }

    public record ShiftVerdict(
            boolean hasEnoughData,
            boolean shiftDetected,
            double baselineFailureRate,
            int recentFailures,
            int recentWindowSize,
            double pValue
    ) {
        static ShiftVerdict insufficientData() {
            return new ShiftVerdict(false, false, 0, 0, 0, 1.0);
        }
    }

    /**
     * @param chronologicalOutcomes launch-success observations in OLDEST-FIRST order (matches
     *                              ClientRuntimeObservationEntity replay order elsewhere in this package)
     */
    public static ShiftVerdict detect(List<Boolean> chronologicalOutcomes, int recentWindow,
                                       int minimumBaselineSamples, double significanceThreshold) {
        int total = chronologicalOutcomes.size();
        int baselineSize = total - recentWindow;
        if (baselineSize < minimumBaselineSamples) {
            return ShiftVerdict.insufficientData();
        }

        List<Boolean> baseline = chronologicalOutcomes.subList(0, baselineSize);
        List<Boolean> recent = chronologicalOutcomes.subList(baselineSize, total);

        long baselineFailures = baseline.stream().filter(success -> !success).count();
        double baselineFailureRate = (double) baselineFailures / baselineSize;

        long recentFailures = recent.stream().filter(success -> !success).count();

        // 2026-08-09 (live test failure, caught before deploy): a genuine IMPROVEMENT (fewer failures
        // than baseline predicts, e.g. 0/5 against a 75% baseline) is just as real a shift as a
        // regression - an earlier version of this method only tested the upper tail (too many failures)
        // and hard-coded p=1.0 whenever recentFailures was 0, so it could never detect an improvement at
        // all. Two-sided exact binomial test instead: the real p-value is twice whichever tail the
        // observation actually falls in, capped at 1.0 - the standard "doubling" convention for an exact
        // two-sided test, not an approximation.
        double p = Math.min(Math.max(baselineFailureRate, 1e-9), 1 - 1e-9);
        BinomialDistribution distribution = new BinomialDistribution(recentWindow, p);
        double upperTail = 1.0 - distribution.cumulativeProbability((int) recentFailures - 1); // P(X >= k)
        double lowerTail = distribution.cumulativeProbability((int) recentFailures); // P(X <= k)
        double pValue = Math.min(1.0, 2 * Math.min(upperTail, lowerTail));
        boolean shift = pValue < significanceThreshold;

        return new ShiftVerdict(true, shift, baselineFailureRate, (int) recentFailures, recentWindow, pValue);
    }

    public static ShiftVerdict detect(List<Boolean> chronologicalOutcomes) {
        return detect(chronologicalOutcomes, DEFAULT_RECENT_WINDOW, DEFAULT_MINIMUM_BASELINE_SAMPLES, DEFAULT_SIGNIFICANCE_THRESHOLD);
    }

    public record AbsoluteVerdict(
            boolean shiftDetected,
            int successes,
            int total,
            double expectedSuccessRate,
            double pValue
    ) {
    }

    /**
     * Complementary to {@link #detect}, not a replacement: that test is RELATIVE (has this project's
     * own rate moved from its own history) and needs minimumBaselineSamples of "before" data before it
     * can say anything - structurally blind to a project whose rate was never good in the first place
     * (e.g. 0/3 successes starting from the very first observation, live-confirmed on test-forty-third
     * 2026-08-09..11 - a real 100% failure run that the relative test above could never flag, since
     * there was no working baseline to shift away from). Same statistical family (exact binomial),
     * applied against a pre-registered acceptable rate instead of the project's own empirical history -
     * works from the very first observation, no minimum sample count required.
     *
     * Deliberately one-sided: only "worse than the expected rate" is actionable here. An observed rate
     * significantly BETTER than expected is not a defect worth escalating (unlike detect()'s two-sided
     * test, where an unexpected IMPROVEMENT is itself informative about the relative baseline).
     */
    public static AbsoluteVerdict detectBelowExpectedRate(List<Boolean> chronologicalOutcomes,
                                                           double expectedSuccessRate,
                                                           double significanceThreshold) {
        int total = chronologicalOutcomes.size();
        if (total == 0) {
            return new AbsoluteVerdict(false, 0, 0, expectedSuccessRate, 1.0);
        }
        int successes = (int) chronologicalOutcomes.stream().filter(Boolean::booleanValue).count();
        double p = Math.min(Math.max(expectedSuccessRate, 1e-9), 1 - 1e-9);
        BinomialDistribution distribution = new BinomialDistribution(total, p);
        double pValue = distribution.cumulativeProbability(successes); // P(X <= observed successes)
        boolean shift = pValue < significanceThreshold;
        return new AbsoluteVerdict(shift, successes, total, expectedSuccessRate, pValue);
    }

    public static AbsoluteVerdict detectBelowExpectedRate(List<Boolean> chronologicalOutcomes) {
        return detectBelowExpectedRate(chronologicalOutcomes, DEFAULT_EXPECTED_SUCCESS_RATE, DEFAULT_ABSOLUTE_SIGNIFICANCE_THRESHOLD);
    }
}
