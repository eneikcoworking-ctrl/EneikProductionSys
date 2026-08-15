package com.eneik.production.services.verdict;

/**
 * What one layer says about one declared proposition: may the project advance on this count?
 *
 * Three values, not two, because "I have not established this" is a distinct answer from "this is fine"
 * and must not be silently rounded into it. On 2026-08-15 four of thirteen doctrine roles stood at
 * `unknown` while the flow dispatched normally and reported 82% progress - the flow had no way to say
 * "undecided" so it behaved as though undecided meant permitted.
 *
 * Why this exists at all: five layers reported `82%`, `blocked`, `954545`, `0.57` and
 * `launchSuccess=false` about one project, and nothing reconciled them. They cannot be averaged because
 * they are not measurements of one quantity - the task pipeline speaks of actuality, the doctrine layer
 * deontically, Six Sigma of frequency, the graph of structure. Averaging a deontic claim with a frequency
 * is a category error, and it is the same category error, one level up, that produced both the conflict
 * entropy calculator (which averaged where safety required conjunction) and DPMO (which counted where the
 * question was classification).
 *
 * The unification is therefore not one NUMBER but one TYPE: every layer answers the single question it can
 * answer in its own terms, mapping its native measure to one of these three by its own declared rule.
 * Thresholds do not disappear - they become local, singular and auditable inside the layer that owns the
 * measure, instead of being smuggled into a global score nobody can inspect.
 */
public enum Verdict {

    /** This layer has established that the project may advance on this proposition. */
    PERMIT,

    /** This layer refuses. One refusal is enough - see {@link #and(Verdict)}. */
    WITHHOLD,

    /**
     * This layer has NOT established anything about this proposition, and says so.
     *
     * Blocks advancement, deliberately. One cannot prove a state safe by failing to find a problem, so
     * absence of a refutation is not a verification. A layer that cannot justify a verdict owes this rather
     * than a guess - which converts an unfounded measurement into visible debt instead of hiding it, and,
     * because it blocks, forces the repair rather than deferring it.
     */
    ABSTAIN;

    /**
     * Strong three-valued conjunction (Kleene). Never a weighted sum.
     *
     * <pre>
     *   WITHHOLD ∧ anything  = WITHHOLD
     *   PERMIT   ∧ ABSTAIN   = ABSTAIN
     *   ABSTAIN  ∧ ABSTAIN   = ABSTAIN
     *   PERMIT   ∧ PERMIT    = PERMIT
     * </pre>
     *
     * Three properties follow, each fixing a defect structurally rather than one site at a time:
     * <ul>
     *   <li><b>No approval outvotes a refusal.</b> A mean can never express "all of them must hold" at any
     *       threshold - which is precisely why entropy could not express the conflict requirement.</li>
     *   <li><b>Abstention is not permission.</b> See {@link #ABSTAIN}.</li>
     *   <li><b>Monotone.</b> Adding a layer can only make advancing harder, never easier, so the factory
     *       may grow its own verification with no risk that a new check accidentally unblocks something.
     *       That is what makes autonomous self-extension safe.</li>
     * </ul>
     */
    public Verdict and(Verdict other) {
        if (other == null) {
            // A missing verdict is not a permitting one: an unanswered layer is an unestablished
            // proposition, which is exactly what ABSTAIN means.
            return this == WITHHOLD ? WITHHOLD : ABSTAIN;
        }
        if (this == WITHHOLD || other == WITHHOLD) {
            return WITHHOLD;
        }
        if (this == ABSTAIN || other == ABSTAIN) {
            return ABSTAIN;
        }
        return PERMIT;
    }
}
