package com.eneik.production.services.lever;

/**
 * Belnap's four-valued diagnostic states (BARCAN-TAG-06, philosopher 3) applied to "did the candidate
 * decision turn out right against real ground truth" - explicitly a DIAGNOSTIC status, never itself the
 * final promotion verdict (that stays single-valued: LeverPromotionStateEntity.currentStage). A lever
 * observation can be TRUE/FALSE only once real ground truth resolves; before that it is NEITHER (no
 * evidence yet - not "unknown counted as false"). BOTH means the candidate and incumbent agreed with each
 * other AND with ground truth - real but uninformative for the candidate's distinct value, so it does not
 * count toward the promotion threshold either way (see LeverPromotionService.evaluatePromotions).
 */
public enum LeverAgreement {
    TRUE,
    FALSE,
    BOTH,
    NEITHER;

    /**
     * Generic helper for levers whose incumbent/candidate decisions are directly, literally comparable to
     * ground truth (e.g. D3's "duplicate" / "not duplicate"). Levers whose ground truth is not a literal
     * string match against their own decision vocabulary (e.g. F1, whose candidate is a checkName but
     * whose ground truth is a KaizenProposal status) must compute their own LeverAgreement instead of
     * using this helper - forcing every lever through one generic string-equality rule would silently
     * misjudge those cases.
     */
    public static LeverAgreement compare(String incumbentDecision, String candidateDecision, String groundTruthOutcome) {
        if (groundTruthOutcome == null) {
            return NEITHER;
        }
        boolean incumbentRight = groundTruthOutcome.equals(incumbentDecision);
        boolean candidateRight = groundTruthOutcome.equals(candidateDecision);
        if (incumbentRight && candidateRight) {
            return BOTH;
        }
        if (candidateRight) {
            return TRUE;
        }
        if (incumbentRight) {
            return FALSE;
        }
        // Both wrong: real evidence exists but says nothing about the candidate's value RELATIVE to the
        // incumbent (the only question the promotion threshold cares about) - folded into NEITHER rather
        // than invented as a 5th state, since it carries no promotion-relevant signal either way.
        return NEITHER;
    }
}
