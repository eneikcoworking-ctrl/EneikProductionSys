package com.eneik.production.services.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real worked numbers, independently hand-computed (not just "whatever the code returns"), TWO-SIDED
// (the exact test doubles whichever tail the observation falls in - a fix made after this test file
// caught the first version only testing for "too many failures," never "suspiciously too few"):
// Binomial(5, p=0.05): P(X>=3)=0.001158 one-sided -> two-sided p=0.002316 (NOT significant at 0.001).
// P(X>=4)=0.0000300 one-sided -> two-sided p=0.0000600 (clearly significant).
class RuntimeHealthShiftDetectorTest {

    private List<Boolean> outcomes(int successCount, int failureCount) {
        List<Boolean> list = new ArrayList<>();
        for (int i = 0; i < successCount; i++) list.add(true);
        for (int i = 0; i < failureCount; i++) list.add(false);
        return list;
    }

    @Test
    void refusesToJudgeWithFewerThanTheMinimumBaselineSamples() {
        // Only 8 baseline observations, minimum is 10 - must honestly say "not enough data" rather than guess.
        List<Boolean> tooFewBaseline = outcomes(8, 0);
        tooFewBaseline.addAll(outcomes(0, 5)); // 5 recent failures on top
        var verdict = RuntimeHealthShiftDetector.detect(tooFewBaseline);

        assertFalse(verdict.hasEnoughData());
        assertFalse(verdict.shiftDetected());
    }

    @Test
    void fourOutOfFiveRecentFailuresAgainstAFivePercentBaselineIsClearlySignificant() {
        List<Boolean> history = outcomes(19, 1); // 20 baseline observations, 1 failure = 5% baseline rate
        history.addAll(List.of(false, false, false, false, true)); // 4 failures in the last 5
        var verdict = RuntimeHealthShiftDetector.detect(history);

        assertTrue(verdict.hasEnoughData());
        assertEquals(0.05, verdict.baselineFailureRate(), 0.001);
        assertEquals(4, verdict.recentFailures());
        assertTrue(verdict.shiftDetected(), "p=" + verdict.pValue() + " should be well under 0.001");
        assertEquals(0.00006, verdict.pValue(), 0.00002);
    }

    @Test
    void threeOutOfFiveRecentFailuresAgainstTheSameBaselineIsNotSignificant() {
        // The operator's own worked example ("3 of 5, 60%") - independently computed by hand:
        // one-sided p=0.001158, doubled (two-sided) to p=0.002316. Looks alarming (60% failure rate!)
        // but is honestly NOT statistically distinguishable from noise at this sample size - exactly the
        // discipline the whole plan is built on: never call it broken before the math says so.
        List<Boolean> history = outcomes(19, 1);
        history.addAll(List.of(false, false, false, true, true));
        var verdict = RuntimeHealthShiftDetector.detect(history);

        assertTrue(verdict.hasEnoughData());
        assertEquals(3, verdict.recentFailures());
        assertFalse(verdict.shiftDetected(), "p=" + verdict.pValue() + " is not below 0.001");
        assertEquals(0.002316, verdict.pValue(), 0.00002);
    }

    @Test
    void allRecentSuccessesAgainstAFailingBaselineIsAlsoDetectedAsAShift() {
        // The detector must be symmetric - a genuine IMPROVEMENT is just as much a real shift as a
        // regression, both deserve a real falsification cycle citing what actually changed. Caught a
        // real bug here first: an earlier version hard-coded p=1.0 whenever recentFailures was 0,
        // so it could never detect an improvement no matter how extreme - fixed to a proper two-sided
        // test. A 90% baseline (not 75%) is used so the two-sided p-value has a robust margin below
        // 0.001, not another near-boundary case.
        List<Boolean> history = outcomes(2, 18); // 20 baseline, 90% failure rate
        history.addAll(outcomes(5, 0)); // 5 clean recent successes
        var verdict = RuntimeHealthShiftDetector.detect(history);

        assertTrue(verdict.hasEnoughData());
        assertEquals(0, verdict.recentFailures());
        assertTrue(verdict.shiftDetected(), "p=" + verdict.pValue() + " should be well under 0.001");
    }

    @Test
    void aStableRateNeverFalselyFlags() {
        List<Boolean> stable = new ArrayList<>(outcomes(19, 1));
        stable.addAll(List.of(true, true, true, true, false)); // 1/5 recent - consistent with 5% baseline
        var verdict = RuntimeHealthShiftDetector.detect(stable);

        assertTrue(verdict.hasEnoughData());
        assertFalse(verdict.shiftDetected());
    }
}
