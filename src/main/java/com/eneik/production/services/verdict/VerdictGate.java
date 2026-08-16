package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 18. The lattice stops being read-only - but only over what the factory CLAIMS.
 *
 * <h2>What it may gate, and what it may never gate</h2>
 *
 * It constrains the readiness the factory REPORTS. It does not touch {@code acceptProject}, and that is a
 * decision, not an omission: acceptance is the client's act of ending an engagement, not a statement that
 * the product is ready. A lattice that abstains must never be able to stop someone ending their own
 * engagement. Nor does it gate task dispatch - a layer saying "the product does not launch" is an argument
 * FOR dispatching repair work, so gating dispatch on it would make the system unable to fix the very thing
 * being refused.
 *
 * So the object of the gate is exactly the proposition the layers actually judge: <i>is this product
 * demonstrably working and shown?</i> - which is a claim, and claims are what a verdict governs.
 *
 * <h2>The arithmetic was already there</h2>
 *
 * CommandDashboardService already answered in three values: `ready` when four conditions hold, `unknown`
 * when any is unmeasurable, `not ready` when one fails. That is Kleene conjunction, written out by hand and
 * never named. The lattice is therefore not bolted on beside it - it is one more conjunct:
 *
 * <pre>
 *   report(P) = construction(P) ∧ ⋀_ℓ verdict_ℓ(P)
 * </pre>
 *
 * By monotonicity of {@link Verdict#and}, adding it can only make the report harder to earn, never easier.
 * That is what makes turning it on safe in the only sense that matters: no configuration of the lattice can
 * cause the factory to claim readiness it would not have claimed before.
 *
 * <h2>Three ways it declines to act</h2>
 *
 * The flag off, a different project, or an empty lattice. The last is the interesting one: the conjunction
 * over no propositions is PERMIT, which is correct arithmetic and a dangerous default, so an empty lattice
 * does not "permit" here - it declines to participate, leaving the construction verdict exactly as it was.
 * A gate that approves because it has nothing to say would be worse than no gate.
 */
@Service
public class VerdictGate {
    private static final Logger log = LoggerFactory.getLogger(VerdictGate.class);

    static final String FLAG = "verdict_gating_enabled";
    static final String PROJECT_SLUG = "verdict_gating_project_slug";

    private final VerdictReconciliation reconciliation;
    private final SystemSettingsService settingsService;

    public VerdictGate(VerdictReconciliation reconciliation, SystemSettingsService settingsService) {
        this.reconciliation = reconciliation;
        this.settingsService = settingsService;
    }

    /**
     * @param verdict  the answer after the lattice has had its say - equal to {@code construction} whenever
     *                 the gate declined to act
     * @param applied  whether the lattice actually contributed. Reported rather than inferred from the
     *                 verdict: "the gate ran and agreed" and "the gate never ran" are different facts, and
     *                 a rollout cannot be judged if they look the same.
     * @param reasons  one line per proposition that is refused or unestablished, in the layer's own words
     */
    public record Decision(Verdict verdict, boolean applied, List<String> reasons) {
    }

    /**
     * @param construction what the caller established on its own evidence, already as a verdict
     */
    public Decision constrain(ProjectEntity project, Verdict construction) {
        Verdict base = construction == null ? Verdict.ABSTAIN : construction;
        if (project == null || !activeFor(project)) {
            return new Decision(base, false, List.of());
        }
        try {
            VerdictReconciliation.Reconciliation r = reconciliation.reconcile(project.getId());
            if (r.judgements().isEmpty()) {
                // Empty conjunction is PERMIT. Correct, and exactly the case where permitting would be a
                // statement made out of silence - so the gate stands aside instead.
                log.warn("VerdictGate: project {} has an empty lattice - no layer declared anything, so the "
                        + "gate is standing aside rather than permitting out of silence", project.getId());
                return new Decision(base, false, List.of());
            }
            List<String> reasons = new ArrayList<>();
            for (Judgement j : r.judgements()) {
                if (j.verdict() != Verdict.PERMIT) {
                    reasons.add(j.layer() + " " + (j.verdict() == Verdict.WITHHOLD ? "refuses" : "cannot say")
                            + ": " + j.proposition()
                            + (j.reason() == null || j.reason().isBlank() ? "" : " - " + j.reason()));
                }
            }
            return new Decision(base.and(r.advance()), true, List.copyOf(reasons));
        } catch (RuntimeException e) {
            // The gate must never become the outage it exists to prevent. Declining is not the same as
            // permitting: the caller's own verdict stands untouched, which is what would have happened if
            // this class did not exist.
            log.warn("VerdictGate: reconciliation failed for project {}, leaving the report unchanged: {}",
                    project.getId(), e.getMessage());
            return new Decision(base, false, List.of());
        }
    }

    /**
     * Deliberately scoped to ONE named project while the gate is being trusted.
     *
     * An empty slug means no project, never every project. A scoping value that falls back to "all" turns
     * the first careless deploy into a factory-wide change, which is the opposite of what a staged rollout
     * is for.
     */
    private boolean activeFor(ProjectEntity project) {
        try {
            if (!settingsService.effectiveBoolean(FLAG)) {
                return false;
            }
            String scoped = settingsService.effectiveValue(PROJECT_SLUG);
            if (scoped == null || scoped.isBlank()) {
                return false;
            }
            return scoped.trim().equalsIgnoreCase(project.getSlug() == null ? "" : project.getSlug().trim());
        } catch (RuntimeException e) {
            // An unreadable flag is not an enabled flag.
            return false;
        }
    }
}
