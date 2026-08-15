package com.eneik.production.services.verdict;

import java.util.List;
import java.util.UUID;

/**
 * One source of judgement about a project.
 *
 * A layer declares up front the finite set of propositions it rules on, then rules on them. That order is
 * the point, and it is the Barcan condition applied here: this factory is named after quantified modal
 * logic, whose central formula holds when the domain does not grow across possible worlds - nothing comes
 * into existence merely by moving to another world.
 *
 * Without a declared domain, {@link Verdict#ABSTAIN} is ambiguous between "declared and not yet decided"
 * and "never considered at all" - and the second is INVISIBLE. Every silent gap found on 2026-08-15 was of
 * the second kind: nobody had declared that a bootstrap must deliver its scaffold, that a generated design
 * must be implementable, or that a launch verdict must be re-taken after a repair, so nothing could report
 * their absence. With the domain fixed in advance, the two are distinct and the second cannot occur.
 */
public interface VerdictLayer {

    /** Stable identifier, used in every {@link Judgement} this layer produces. */
    String layerName();

    /**
     * The finite set of propositions this layer rules on, declared BEFORE any of them is decided.
     *
     * Must be stable for a given project state rather than reflecting whatever the layer happened to notice
     * this tick - a domain that shrinks when a check fails to run would hide the failure as an absence.
     */
    List<String> declaredPropositions(UUID projectId);

    /**
     * Rule on every declared proposition. The result must contain exactly one judgement per declared
     * proposition; {@link VerdictReconciliation} treats a missing one as an abstention, since an
     * unanswered declared proposition is by definition unestablished.
     *
     * A layer that cannot justify a ruling owes {@link Verdict#ABSTAIN} with its reason, never a guess.
     * Implementations must not throw: a layer that fails loudly enough to break the reconciliation would
     * make the observer the outage it exists to prevent.
     */
    List<Judgement> judge(UUID projectId);
}
