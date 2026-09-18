package com.eneik.production.services.verdict;

import java.time.Instant;
import java.util.Objects;

/**
 * One layer's ruling on one DECLARED proposition, with the reason and the evidence it rests on,
 * along with its belief update history (prior verdict, prior reason, prior evidence, and timestamp).
 *
 * Implements AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER (D007 Evidence gap / Isaac Levi 1980):
 * Any change in doxastic commitment must record what changed it, when it changed, and what the
 * belief stood on previously.
 *
 * @param layer            which layer ruled - so a refusal can be traced to something that can be argued with
 * @param proposition      the declared thing being ruled on, stable across ticks so "not yet decided" and
 *                         "never considered" stay distinguishable (see {@link VerdictLayer})
 * @param verdict          the ruling
 * @param reason           why, in the layer's own terms. Required for anything other than PERMIT: a refusal a
 *                         human cannot check is an accusation, not evidence
 * @param evidence         what the ruling rests on - a measurement, an observation id, a count. Carried so a
 *                         verdict can be re-examined when its referent changes rather than standing forever on
 *                         a fact that has since been superseded, which is how philosophy stayed subordinated to
 *                         a launch observation taken before two repairs that fixed it
 * @param reasonCode       machine-readable classification code or empty string
 * @param previousVerdict  the prior ruling on this proposition, or null if first observation
 * @param previousReason   the prior reason on this proposition, or empty string if first observation
 * @param previousEvidence the prior evidence on this proposition, or empty string if first observation
 * @param decidedAt        timestamp when this judgement was determined / recorded
 */
public record Judgement(
        String layer,
        String proposition,
        Verdict verdict,
        String reason,
        String evidence,
        String reasonCode,
        Verdict previousVerdict,
        String previousReason,
        String previousEvidence,
        Instant decidedAt
) {

    public Judgement(String layer, String proposition, Verdict verdict, String reason, String evidence) {
        this(layer, proposition, verdict, reason, evidence, "", null, "", "", Instant.now());
    }

    public Judgement(String layer, String proposition, Verdict verdict, String reason, String evidence, String reasonCode) {
        this(layer, proposition, verdict, reason, evidence, reasonCode, null, "", "", Instant.now());
    }

    public Judgement {
        reason = reason == null ? "" : reason;
        evidence = evidence == null ? "" : evidence;
        reasonCode = reasonCode == null ? "" : reasonCode;
        previousReason = previousReason == null ? "" : previousReason;
        previousEvidence = previousEvidence == null ? "" : previousEvidence;
        decidedAt = decidedAt == null ? Instant.now() : decidedAt;
    }

    public static Judgement permit(String layer, String proposition, String evidence) {
        return new Judgement(layer, proposition, Verdict.PERMIT, "", evidence, "");
    }

    public static Judgement withhold(String layer, String proposition, String reason, String evidence) {
        return new Judgement(layer, proposition, Verdict.WITHHOLD, reason, evidence, "");
    }

    public static Judgement withhold(String layer, String proposition, String reasonCode, String reason, String evidence) {
        return new Judgement(layer, proposition, Verdict.WITHHOLD, reason, evidence, reasonCode == null ? "" : reasonCode);
    }

    /**
     * @param reason why this could not be established. Required - an abstention with no stated reason is
     *               indistinguishable from a layer that simply never ran, which is the ambiguity the
     *               declared-proposition rule exists to remove.
     */
    public static Judgement abstain(String layer, String proposition, String reason) {
        return new Judgement(layer, proposition, Verdict.ABSTAIN, reason, "", "");
    }

    public static Judgement abstain(String layer, String proposition, String reasonCode, String reason) {
        return new Judgement(layer, proposition, Verdict.ABSTAIN, reason, "", reasonCode == null ? "" : reasonCode);
    }

    /**
     * Returns a new Judgement linked to the prior belief state for this proposition.
     */
    public Judgement withPriorBelief(Judgement prior, Instant decidedAt) {
        if (prior == null) {
            return new Judgement(layer, proposition, verdict, reason, evidence, reasonCode,
                    null, "", "", decidedAt != null ? decidedAt : this.decidedAt);
        }
        return new Judgement(layer, proposition, verdict, reason, evidence, reasonCode,
                prior.verdict(), prior.reason(), prior.evidence(),
                decidedAt != null ? decidedAt : this.decidedAt);
    }

    public Judgement withPriorBelief(Verdict prevVerdict, String prevReason, String prevEvidence, Instant decidedAt) {
        return new Judgement(layer, proposition, verdict, reason, evidence, reasonCode,
                prevVerdict, prevReason, prevEvidence,
                decidedAt != null ? decidedAt : this.decidedAt);
    }

    /**
     * Returns true if the verdict transitioned from a prior known ruling to a different ruling.
     */
    public boolean hasTransition() {
        return previousVerdict != null && previousVerdict != verdict;
    }

    /**
     * Returns true if any aspect of the belief (verdict, reason, or evidence) changed from the prior state.
     */
    public boolean hasBeliefUpdate() {
        return previousVerdict != null && (
                previousVerdict != verdict
                || !Objects.equals(previousReason, reason)
                || !Objects.equals(previousEvidence, evidence)
        );
    }

    @Override
    public String reasonCode() {
        return reasonCode == null ? "" : reasonCode;
    }

    @Override
    public String previousReason() {
        return previousReason == null ? "" : previousReason;
    }

    @Override
    public String previousEvidence() {
        return previousEvidence == null ? "" : previousEvidence;
    }
}
