package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.ClientRuntimeObservationRepository;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import com.eneik.production.services.operational.OperationalAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Theory of Constraints, step 3 - subordinate - measured in shadow before it is allowed to decide anything.
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
 * **Why this only observes.** A subordination gate that is wrong freezes an entire project - measured
 * exactly once already, when one task stuck in `pending_review` put the whole flow into `SYSTEM_STALLED`
 * and the policy denied dispatch project-wide. The operational-math document's own promotion policy
 * requires `observe_only` first: compute and display, promote on evidence. This class records what
 * subordination WOULD have decided against what the policy actually decided, into the same
 * `LeverPromotionService` machinery every other candidate rule uses, and changes nothing.
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

    public TocSubordinationLever(ClientRuntimeObservationRepository observationRepository,
                                  LeverPromotionService leverPromotionService) {
        this.observationRepository = observationRepository;
        this.leverPromotionService = leverPromotionService;
    }

    /**
     * Records one incumbent/candidate decision pair. Never changes what the caller does - the return value
     * is the incumbent decision, passed straight back, so a call site can wrap its existing check without
     * altering behaviour.
     */
    public boolean observe(ProjectEntity project, OperationalAction action, boolean incumbentAllowed) {
        try {
            if (!constraintIsOpen(project)) {
                return incumbentAllowed;
            }
            boolean candidateAllowed = SERVES_A_LAUNCH_CONSTRAINT.contains(action);
            LeverAgreement agreement = incumbentAllowed == candidateAllowed
                    ? LeverAgreement.TRUE
                    : LeverAgreement.FALSE;
            leverPromotionService.recordObservation(
                    T1_TOC_SUBORDINATION,
                    project.getId() + ":" + action.name(),
                    incumbentAllowed ? "allow" : "deny",
                    candidateAllowed ? "allow" : "deny",
                    agreement,
                    null);
            if (agreement == LeverAgreement.FALSE) {
                log.info("[TOC-SUBORDINATION][shadow] project {} action {} - policy says {}, subordination "
                                + "would say {} while the product's last real observation is unhealthy. "
                                + "Recorded only; nothing was blocked.",
                        project.getId(), action, incumbentAllowed ? "allow" : "deny",
                        candidateAllowed ? "allow" : "deny");
            }
        } catch (Exception e) {
            // A shadow measurement must never affect the decision it is measuring.
            log.warn("[TOC-SUBORDINATION] shadow record failed for {} on project {}: {}",
                    action, project.getId(), e.getMessage());
        }
        return incumbentAllowed;
    }

    /**
     * Open exactly while the latest observation that actually observed the product came back unhealthy.
     * Rows marked as instrument failures are skipped - nothing was learned from them (V104).
     */
    private boolean constraintIsOpen(ProjectEntity project) {
        List<ClientRuntimeObservationEntity> history =
                observationRepository.findByProjectIdOrderByObservedAtDesc(project.getId());
        for (ClientRuntimeObservationEntity row : history) {
            if (row.isInstrumentFailure()) {
                continue;
            }
            boolean healthy = row.isLaunchSuccess()
                    && row.getHealthStatusCode() != null
                    && row.getHealthStatusCode() >= 200
                    && row.getHealthStatusCode() < 300;
            return !healthy;
        }
        return false; // never observed - nothing is known, so nothing is subordinated
    }
}
