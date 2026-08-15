package com.eneik.production.services.verdict;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Process quality - and, for now, a layer that owes an abstention rather than a number.
 *
 * This is the clearest instance of the rule that a layer which cannot justify a verdict must abstain.
 *
 * Its classification was repaired on 2026-08-15: `isDefectWork` used to substring-match free text against
 * two different word lists in two places, so a FEATURE named "Self-Service Account Recovery" was defect
 * work by its name and any acceptance criterion describing error handling contained "failure" - giving
 * DPMO 954,545 on a project that merged 22 of 22 with 8 of 8 features complete. That is fixed; the count
 * now reads the declared `WishlistSource`.
 *
 * But a trustworthy count is not yet a verdict. Nothing in this system declares WHAT DPMO warrants
 * withholding advancement: no threshold has been established from evidence, and inventing one here would
 * repeat precisely the error just removed - a number standing in for a judgement nobody made. The honest
 * answer is that this layer has not established anything, so it says so.
 *
 * Abstention blocks, deliberately. That is the mechanism working as intended: an unfounded measure becomes
 * visible debt that must be discharged by declaring a real rule, instead of quietly permitting everything
 * while looking rigorous. It is the same reason the corpus keeps `hypothesis` entries inert.
 */
@Component
public class SixSigmaVerdictLayer implements VerdictLayer {

    static final String PROPOSITION_QUALITY = "process defect density is within a declared bound";

    @Override
    public String layerName() {
        return "six-sigma";
    }

    @Override
    public List<String> declaredPropositions(UUID projectId) {
        return List.of(PROPOSITION_QUALITY);
    }

    @Override
    public List<Judgement> judge(UUID projectId) {
        return List.of(Judgement.abstain(layerName(), PROPOSITION_QUALITY,
                "no bound has been declared for this measure. The defect classification was repaired on "
                        + "2026-08-15 (it now reads WishlistSource rather than matching substrings), but a "
                        + "trustworthy count is not a verdict: nothing states what density warrants "
                        + "withholding advancement. Inventing a threshold here would repeat the error just "
                        + "removed - a number standing in for a judgement nobody made."));
    }
}
