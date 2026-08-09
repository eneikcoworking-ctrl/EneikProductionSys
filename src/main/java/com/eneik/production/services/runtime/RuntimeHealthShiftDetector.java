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
}
