package com.eneik.production.services.toc;

import com.eneik.production.services.lever.LeverAgreement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the promotion ladder measures, and what this lever must therefore supply.
 *
 * <p>Until 2026-08-28 TocSubordinationLever wrote {@code agreement = TRUE} whenever the policy and the
 * subordination rule COINCIDED. LeverPromotionService reads TRUE as "the candidate was right and the
 * incumbent was not" and counts only TRUE/FALSE toward the threshold. Those are different variables, and
 * under the wrong one the lever could only reach 0.80 by being redundant - measured 1408 TRUE / 1978
 * FALSE, rate 0.416, against a threshold it could never approach for the right reason.
 *
 * <p>These tests pin the four cases of the quantity that actually counts, so a future edit cannot quietly
 * substitute rule-coincidence again.
 */
class TocSubordinationTruthTest {

    @Test
    void noTruthYetIsNeitherRatherThanAGuess() {
        // At decision time nothing has happened that could say who was right. NEITHER is not "unknown
        // counted as false" - it is excluded from the threshold entirely.
        assertEquals(LeverAgreement.NEITHER, LeverAgreement.compare("allow", "deny", null));
    }

    @Test
    void candidateRightAndPolicyWrongIsTheOnlyThingThatEarnsPromotion() {
        // The constraint persisted, so withholding slack work was right; the policy had allowed it.
        assertEquals(LeverAgreement.TRUE, LeverAgreement.compare("allow", "deny", "deny"));
    }

    @Test
    void policyRightAndCandidateWrongIsWhatDemotes() {
        // The product recovered anyway, so withholding was unnecessary.
        assertEquals(LeverAgreement.FALSE, LeverAgreement.compare("allow", "deny", "allow"));
    }

    @Test
    void bothRightIsRealButUninformativeAboutThisRulesOwnValue() {
        // This is the case the old code recorded as TRUE. It says nothing about whether subordination adds
        // anything over the policy, and the ladder deliberately excludes it from the threshold.
        assertEquals(LeverAgreement.BOTH, LeverAgreement.compare("deny", "deny", "deny"));
    }

    @Test
    void bothWrongCarriesNoSignalAboutTheCandidateRelativeToThePolicy() {
        assertEquals(LeverAgreement.NEITHER, LeverAgreement.compare("allow", "allow", "deny"));
    }
}
