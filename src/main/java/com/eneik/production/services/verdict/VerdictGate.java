package com.eneik.production.services.verdict;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
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
     * Prescription 9 (FACTORY_MECHANISMS.md XVI §9 / DZHOZEF_RAZ_01_PROHIBITION_AS_CODE / D006):
     * Executable denial path with explainable reason, named policy rule, and non-absorbing reversibility.
     * Prohibits operational actions (e.g. DISPATCH_QUEUED_TASKS, EXPAND_FEATURE) on verified inputs
     * (such as infrastructure database health or doctrine unrecovered failures) per proposition rule.
     */
    public record ActionProhibition(
            boolean prohibited,
            String layer,
            String proposition,
            String ruleName,
            String explanation,
            boolean exemptsRecoveryWork
    ) {
        public ActionProhibition(boolean prohibited, String layer, String proposition, String ruleName, String explanation) {
            this(prohibited, layer, proposition, ruleName, explanation, false);
        }

        public static ActionProhibition permitted() {
            return new ActionProhibition(false, "", "", "", "Action is permitted by verdict gate", false);
        }

        public static ActionProhibition denied(String layer, String proposition, String ruleName, String reason) {
            return new ActionProhibition(true, layer, proposition, ruleName,
                    "Action denied by " + layer + " layer [" + ruleName + "]: " + proposition + " - " + reason, false);
        }

        public static ActionProhibition deniedWithRecoveryExemption(String layer, String proposition, String ruleName, String reason) {
            return new ActionProhibition(true, layer, proposition, ruleName,
                    "Action denied by " + layer + " layer [" + ruleName + "]: " + proposition + " - " + reason, true);
        }

        public boolean allowsTask(boolean isRecoveryTask) {
            if (!prohibited) {
                return true;
            }
            return exemptsRecoveryWork && isRecoveryTask;
        }
    }

    /**
     * Evaluates actionable prohibitions per proposition rule with verified inputs rather than a blanket advance block.
     *
     * @param project the project under evaluation
     * @param action the operational action to evaluate (e.g. DISPATCH_QUEUED_TASKS, EXPAND_FEATURE, ORCHESTRATE)
     * @return ActionProhibition indicating whether the action is forbidden, with rule name and explainable reason
     */
    public ActionProhibition evaluateActionProhibition(ProjectEntity project, String action) {
        return evaluateActionProhibition(project, action, false);
    }

    /**
     * Evaluates actionable prohibitions, allowing explicit exemption for recovery work so that prohibitions do
     * not lock their own recovery path (D006 / PROHIBITION_AS_CODE).
     */
    public ActionProhibition evaluateActionProhibition(ProjectEntity project, String action, boolean isRecoveryWork) {
        if (project == null || !activeFor(project)) {
            return ActionProhibition.permitted();
        }
        try {
            VerdictReconciliation.Reconciliation r = reconciliation.reconcile(project.getId());
            if (r == null || r.judgements().isEmpty()) {
                return ActionProhibition.permitted();
            }

            for (Judgement j : r.judgements()) {
                if (j == null || j.verdict() != Verdict.WITHHOLD) {
                    continue;
                }
                // Rule 1: Infrastructure health prohibition (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE):
                // If orchestrator DB is unhealthy or runtime launcher unreachable, deny task dispatch.
                if ("infrastructure".equalsIgnoreCase(j.layer())
                        && "DISPATCH_QUEUED_TASKS".equalsIgnoreCase(action)) {
                    return ActionProhibition.denied(
                            j.layer(),
                            j.proposition(),
                            "INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION",
                            j.reason() == null ? "infrastructure health refused" : j.reason()
                    );
                }
                // Rule 2: Doctrine unrecovered failure prohibition (DZHOZEF_RAZ_01_PROHIBITION_AS_CODE):
                // If doctrine layer refuses because of active unrecovered failed work, deny feature expansion
                // (ORCHESTRATE / EXPAND_FEATURE) and regular task dispatch, BUT EXPLICITLY EXEMPT recovery work
                // so that the failure can be repaired and the prohibition lifted (preventing self-locking).
                boolean isUnrecoveredDoctrineFailure = "doctrine".equalsIgnoreCase(j.layer())
                        && ("UNRECOVERED_FAILED_WORK".equals(j.reasonCode())
                                || (j.reason() != null && j.reason().contains("unrecovered failed work")));
                if (isUnrecoveredDoctrineFailure
                        && ("DISPATCH_QUEUED_TASKS".equalsIgnoreCase(action)
                                || "EXPAND_FEATURE".equalsIgnoreCase(action)
                                || "ORCHESTRATE".equalsIgnoreCase(action))) {
                    if (isRecoveryWork && "DISPATCH_QUEUED_TASKS".equalsIgnoreCase(action)) {
                        // Recovery work is explicitly exempted from prohibition: recovery tasks must be dispatched
                        // to resolve the failure.
                        continue;
                    }
                    return ActionProhibition.deniedWithRecoveryExemption(
                            j.layer(),
                            j.proposition(),
                            "DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION",
                            j.reason()
                    );
                }
            }
            return ActionProhibition.permitted();
        } catch (RuntimeException e) {
            log.warn("VerdictGate: evaluating action prohibition failed for project {}, standing aside: {}",
                    project.getId(), e.getMessage());
            return ActionProhibition.permitted();
        }
    }

    /**
     * Evaluates task-specific prohibition: recovery tasks are explicitly allowed to dispatch even when
     * DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION is active on the project.
     */
    public ActionProhibition evaluateTaskProhibition(ProjectEntity project, TaskEntity task) {
        boolean isRecovery = isRecoveryTask(task);
        return evaluateActionProhibition(project, "DISPATCH_QUEUED_TASKS", isRecovery);
    }

    public boolean isRecoveryTask(TaskEntity task) {
        if (task == null) {
            return false;
        }
        if (task.getRetryCount() > 0) {
            return true;
        }
        com.fasterxml.jackson.databind.JsonNode payload = task.getPayload();
        if (payload != null) {
            if (payload.has("ems_defect_work") && payload.get("ems_defect_work").asBoolean(false)) {
                return true;
            }
            if (payload.has("is_recovery") && payload.get("is_recovery").asBoolean(false)) {
                return true;
            }
        }
        return false;
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
