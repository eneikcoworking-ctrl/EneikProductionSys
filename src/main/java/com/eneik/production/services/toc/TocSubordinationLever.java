package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.LeverObservation;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import com.eneik.production.services.lever.LeverStage;
import com.eneik.production.services.operational.OperationalAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Set;

/**
 * Theory of Constraints, step 3 - subordinate - measured in shadow first, and allowed to decide only once
 * its own promotion ladder has carried it past `observe_only` on accumulated evidence.
 *
 * The factory identifies its constraint and then files it as an item in the ordinary queue, where it waits
 * its turn: `WishlistEntity` has no priority field and nothing orders selection by `leanValue`. But a
 * constraint is not a high-priority item. It is what the throughput of the whole is limited by, and
 * everything else is slack. Goldratt's *subordinate* means the literal thing: non-constraints idle if that
 * is what it takes for the constraint never to wait.
 *
 * **What counts as the constraint being open.** Not "a `product_not_launchable` wishlist exists" - a
 * status speaks about a record, and `existsByProjectIdAndSource` blocks re-filing regardless of status, so
 * a dismissed one would keep the constraint permanently unfileable while the product stayed broken. Only
 * an observation speaks about now: **the constraint is open exactly while the latest real observation of
 * the running product is unhealthy**, and it is cleared by a fresh healthy one. Instrument failures are
 * skipped for the same reason they are skipped by the posterior (V104) - a launch nobody answered is not a
 * launch that failed.
 *
 * **Why it observed first, and what changed (2026-08-21, plan L-6).** A subordination gate that is wrong
 * freezes an entire project - measured exactly once already, when one task stuck in `pending_review` put
 * the whole flow into `SYSTEM_STALLED` and the policy denied dispatch project-wide. The operational-math
 * document's own promotion policy therefore requires `observe_only` first: compute and display, promote on
 * evidence. That is still exactly what happens - what was missing was the second half. The ladder
 * (`LeverStage`, `LeverPromotionService.evaluatePromotions`) already walks a lever from `observe_only` to
 * `soft_gate` on its own accumulated agreement rate, and this lever was the one thing on it that could
 * never arrive: it returned the incumbent's answer unconditionally, so no amount of evidence could ever
 * change a decision. A promotion nothing acts on is not a promotion.
 *
 * So the rule now DECIDES, from `soft_gate` upward, and only in one direction: it may **remove** a
 * permission the policy granted, never grant one the policy denied. Subordination means non-constraints
 * idle; it can never mean a non-constraint is allowed something the flow state forbids. Below `soft_gate`
 * the behaviour is bit-for-bit what it was - record the pair, return the incumbent - and promotion is
 * reached only by agreeing with the policy at least `AGREEMENT_THRESHOLD` of the time over
 * `MIN_RESOLVED_SAMPLES` real decisions, so what it enforces first are the rare disagreements of a rule
 * that has already been measured to be right nearly always.
 */
@Service
public class TocSubordinationLever {

    private static final Logger log = LoggerFactory.getLogger(TocSubordinationLever.class);

    /** Rigid designator - a renamed key silently starts a fresh, evidence-less lever (Frege, TAG-08). */
    public static final String T1_TOC_SUBORDINATION = "T1_TOC_SUBORDINATION";

    /**
     * Actions that serve a launchability constraint, i.e. that can move the product toward starting again.
     * Everything absent from this set is slack while the constraint is open.
     *
     * `ADD_WISHLIST` is here because the constraint's own remedy enters as a wishlist; `DISPATCH_*` and
     * `MERGE_PR` because the remedy has to reach main to change anything; `CHECK_LAUNCHABILITY` and
     * `OBSERVE` because the constraint can only be cleared by a fresh observation, so forbidding them would
     * make the constraint permanent - subordination must never suppress the very act that ends it.
     */
    private static final Set<OperationalAction> SERVES_A_LAUNCH_CONSTRAINT = Set.of(
            OperationalAction.OBSERVE,
            OperationalAction.ADD_WISHLIST,
            OperationalAction.ORCHESTRATE,
            OperationalAction.DISPATCH_QUEUED_TASKS,
            OperationalAction.DISPATCH_REVIEW_TASKS,
            OperationalAction.MERGE_PR,
            OperationalAction.SYNC_GITHUB,
            OperationalAction.CHECK_LAUNCHABILITY,
            OperationalAction.RECOVER_FAILED_FRONTIER,
            OperationalAction.REVIVE_FAILED_TASK,
            OperationalAction.NUDGE_SESSION);

    private final ClientRuntimeObservationRepository observationRepository;
    private final LeverPromotionService leverPromotionService;

    /**
     * Optional so every existing construction site keeps compiling; without it the lever records as before
     * and simply never resolves, which is the state it was already in.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.repositories.LeverObservationRepository leverObservationRepository;

    /** How many pending observations one call may resolve. Bounded for the reason the query documents. */
    static final int RESOLVE_BATCH = 100;

    public TocSubordinationLever(ClientRuntimeObservationRepository observationRepository,
                                  LeverPromotionService leverPromotionService) {
        this.observationRepository = observationRepository;
        this.leverPromotionService = leverPromotionService;
    }

    /**
     * Records one incumbent/candidate decision pair and returns the decision that should actually be acted
     * on. Below {@link LeverStage#SOFT_GATE} that is the incumbent's own answer, unchanged; from
     * {@code soft_gate} upward it is the incumbent's answer AND-ed with subordination's, so the rule can
     * take a permission away and can never grant one.
     *
     * <p>Renamed from {@code observe} on 2026-08-21 with the change that gave it teeth: a method called
     * "observe" that decides is a designator with a bearer it does not have, which is the same defect
     * ACP-102 records against {@code recentObservations} elsewhere in this system. The name has to move
     * when the behaviour does.
     */
    public boolean subordinate(ProjectEntity project, OperationalAction action, boolean incumbentAllowed) {
        try {
            resolvePendingObservations(project);
            if (!constraintIsOpen(project)) {
                return incumbentAllowed;
            }
            boolean candidateAllowed = SERVES_A_LAUNCH_CONSTRAINT.contains(action);
            // NEITHER, because at decision time nothing has happened yet that could say who was right
            // (2026-08-28). This line previously wrote TRUE when the two rules merely COINCIDED and FALSE
            // when they differed - a different variable from the one the promotion ladder measures.
            // LeverPromotionService counts TRUE as "the candidate was right and the incumbent was not";
            // supplying rule-coincidence instead meant the rate measured redundancy, and promotion could
            // only ever be earned by echoing the policy - the opposite of the reason this lever exists.
            // Measured before the change: TRUE 1408, FALSE 1978, rate 0.416 against a 0.80 threshold.
            // The truth arrives later, from the product itself - see resolvePendingObservations.
            LeverAgreement agreement = LeverAgreement.compare(
                    incumbentAllowed ? "allow" : "deny",
                    candidateAllowed ? "allow" : "deny",
                    null);
            leverPromotionService.recordObservation(
                    T1_TOC_SUBORDINATION,
                    project.getId() + ":" + action.name(),
                    incumbentAllowed ? "allow" : "deny",
                    candidateAllowed ? "allow" : "deny",
                    agreement,
                    null);
            // The lever decides only from soft_gate upward, and only ever by taking a permission away:
            // `incumbentAllowed && candidateAllowed` can turn an allow into a deny and can never turn a
            // deny into an allow. Goldratt's subordinate is "non-constraints idle so the constraint never
            // waits", which is a restriction on slack - it is never a licence for slack to do something
            // the flow state already forbids.
            LeverStage stage = leverPromotionService.currentStage(T1_TOC_SUBORDINATION);
            // Null-safe on purpose: a lever with no state row yet has no evidence, and the safe reading
            // of "no evidence" is the one that changes nothing.
            boolean deciding = stage != null && stage.atLeast(LeverStage.SOFT_GATE);
            boolean subordinated = deciding ? (incumbentAllowed && candidateAllowed) : incumbentAllowed;
            if (agreement == LeverAgreement.FALSE) {
                log.info("[TOC-SUBORDINATION][{}] project {} action {} - policy says {}, subordination says "
                                + "{} while the product's last real observation is unhealthy. Effective "
                                + "decision: {}.",
                        deciding ? stage.wireValue() : "shadow",
                        project.getId(), action, incumbentAllowed ? "allow" : "deny",
                        candidateAllowed ? "allow" : "deny", subordinated ? "allow" : "deny");
            }
            return subordinated;
        } catch (Exception e) {
            // Measuring or enforcing must never be the reason a decision fails to be made: on any failure
            // in here the incumbent's answer stands, exactly as before this rule existed.
            log.warn("[TOC-SUBORDINATION] lever failed for {} on project {}; the policy's own answer stands: {}",
                    action, project.getId(), e.getMessage());
            return incumbentAllowed;
        }
    }

    /**
     * Open exactly while the latest observation that actually observed the product came back unhealthy.
     * Rows marked as instrument failures are skipped - nothing was learned from them (V104).
     */
    /**
     * Ground truth for one subordination decision, and how it is obtained.
     *
     * <p>The lever only ever records while the constraint is open - the product's latest real observation
     * came back unhealthy. What the decision claimed was: non-constraint work should idle until that
     * clears. The next real observation says whether it cleared:
     *
     * <pre>
     *   truth = "deny"   the product is STILL unhealthy  -> the constraint persisted, withholding
     *                                                       slack work was the right call
     *   truth = "allow"  the product became healthy      -> the constraint cleared anyway, withholding
     *                                                       it was unnecessary
     * </pre>
     *
     * <p>Then {@link LeverAgreement#compare} turns the pair into the quantity the ladder actually
     * measures: TRUE only when the candidate was right and the policy was not.
     *
     * <p><b>This is a declared definition, not a measurement</b>, and it is falsifiable: if a promoted
     * lever starts removing permissions on projects whose product recovered regardless, the definition is
     * wrong and the ladder's own demotion (first FALSE after promotion) will pull it back down. That is
     * the intended way for it to be refuted.
     *
     * <p>Instrument failures are skipped, for the same reason the posterior skips them (V104): a launch
     * nobody answered is not a launch that failed.
     */
    private void resolvePendingObservations(ProjectEntity project) {
        if (leverObservationRepository == null) {
            return;
        }
        ClientRuntimeObservationEntity latest = latestRealObservation(project);
        if (latest == null || latest.getObservedAt() == null) {
            return;
        }
        String truth = isHealthy(latest) ? "allow" : "deny";
        List<LeverObservation> pending = leverObservationRepository
                .findByLeverKeyAndSubjectIdStartingWithAndGroundTruthOutcomeIsNullAndObservedAtBeforeOrderByObservedAtAsc(
                        T1_TOC_SUBORDINATION, project.getId() + ":", latest.getObservedAt(),
                        Limit.of(RESOLVE_BATCH));
        if (pending.isEmpty()) {
            return;
        }
        for (LeverObservation observation : pending) {
            observation.setGroundTruthOutcome(truth);
            observation.setAgreement(LeverAgreement.compare(
                    observation.getIncumbentDecision(), observation.getCandidateDecision(), truth).name());
        }
        leverObservationRepository.saveAll(pending);
        log.info("[TOC-SUBORDINATION][RESOLVE] project {} - {} observation(s) resolved against a {} product "
                        + "observation at {}",
                project.getId(), pending.size(), truth.equals("allow") ? "healthy" : "still-unhealthy",
                latest.getObservedAt());
    }

    /** Newest observation that actually observed the product, or null if there is none. */
    private ClientRuntimeObservationEntity latestRealObservation(ProjectEntity project) {
        for (ClientRuntimeObservationEntity row
                : observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId(), Limit.of(20))) {
            if (!row.isInstrumentFailure()) {
                return row;
            }
        }
        return null;
    }

    /** One definition of health, shared by the constraint predicate and by truth resolution. */
    private static boolean isHealthy(ClientRuntimeObservationEntity row) {
        return row.isLaunchSuccess()
                && row.getHealthStatusCode() != null
                && row.getHealthStatusCode() >= 200
                && row.getHealthStatusCode() < 300;
    }

    private boolean constraintIsOpen(ProjectEntity project) {
        // Bounded read (2026-08-21): this now runs on the orchestrator's per-action policy check rather
        // than once per shadow sample, and the unbounded variant loads the project's ENTIRE observation
        // history to look at its newest rows. Only the newest non-instrument row can decide the answer, so
        // a window is sufficient; an unscoped full-table read on a hot path is the exact shape that
        // contributed to a real H2 out-of-memory here once already.
        List<ClientRuntimeObservationEntity> history =
                observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId(), Limit.of(20));
        for (ClientRuntimeObservationEntity row : history) {
            if (row.isInstrumentFailure()) {
                continue;
            }
            return !isHealthy(row);
        }
        return false; // never observed - nothing is known, so nothing is subordinated
    }
}
