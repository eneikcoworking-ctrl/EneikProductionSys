package com.eneik.production.services.verdict;

/**
 * One layer's ruling on one DECLARED proposition, with the reason and the evidence it rests on.
 *
 * @param layer       which layer ruled - so a refusal can be traced to something that can be argued with
 * @param proposition the declared thing being ruled on, stable across ticks so "not yet decided" and
 *                    "never considered" stay distinguishable (see {@link VerdictLayer})
 * @param verdict     the ruling
 * @param reason      why, in the layer's own terms. Required for anything other than PERMIT: a refusal a
 *                    human cannot check is an accusation, not evidence
 * @param evidence    what the ruling rests on - a measurement, an observation id, a count. Carried so a
 *                    verdict can be re-examined when its referent changes rather than standing forever on
 *                    a fact that has since been superseded, which is how philosophy stayed subordinated to
 *                    a launch observation taken before two repairs that fixed it
 */
public record Judgement(
        String layer,
        String proposition,
        Verdict verdict,
        String reason,
        String evidence
) {

    public static Judgement permit(String layer, String proposition, String evidence) {
        return new Judgement(layer, proposition, Verdict.PERMIT, "", evidence);
    }

    public static Judgement withhold(String layer, String proposition, String reason, String evidence) {
        return new Judgement(layer, proposition, Verdict.WITHHOLD, reason, evidence);
    }

    /**
     * @param reason why this could not be established. Required - an abstention with no stated reason is
     *               indistinguishable from a layer that simply never ran, which is the ambiguity the
     *               declared-proposition rule exists to remove.
     */
    public static Judgement abstain(String layer, String proposition, String reason) {
        return new Judgement(layer, proposition, Verdict.ABSTAIN, reason, "");
    }
}
