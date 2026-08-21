package com.eneik.production.services.toc;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The one place the launchability constraint is identified.
 *
 * It used to live inside {@code FalsificationCycleService.executePhilosophicalCycleForProject}, behind
 * five gates that all belong to philosophical review, on a cron that fires every two days - and its one
 * accelerator, the Gemini observer, was switched off. So the constraint the whole architecture is meant
 * to subordinate to was identified only as a side effect of the very process that must subordinate to
 * it. §7 of the plan named that inversion; this is the half of it that can be removed without building
 * the policy predicate.
 *
 * Measured 2026-08-21: 95 wishlist rows for the active project and, until that morning, zero
 * `product_not_launchable` - while the product had never once answered a health check. Every filing that
 * day happened because a human triggered the cycle by hand. An idle factory with an unrefuted product is
 * not "everything is done"; by §1 it means the system stopped looking.
 *
 * Identification now happens where the EVIDENCE is produced. `ClientRuntimeObservabilityService` writes
 * an observation roughly hourly and knows immediately whether the product answered; that is both the
 * freshest possible cause and the moment §7's rule applies - a constraint is cleared by a fresh healthy
 * observation, not by a status. The philosophical cycle still calls this too, so its subordination
 * behaviour is unchanged; it is simply no longer the only door.
 */
@Service
public class LaunchabilityConstraintService {

    private static final Logger log = LoggerFactory.getLogger(LaunchabilityConstraintService.class);

    private final WishlistRepository wishlistRepository;

    /** How long a finished, ineffective constraint attempt is left alone before the constraint is re-filed. */
    @Value("${falsification-cycle.constraint-refile-cooldown-minutes:45}")
    private long constraintRefileCooldownMinutes;

    public LaunchabilityConstraintService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public void ensureOpen(ProjectEntity project, String observedCause) {
        // 2026-08-21 (plan L-8): the guard used to be existsByProjectIdAndSource, which blocks re-filing
        // REGARDLESS OF STATUS. §7 of the plan wrote down why that is wrong - "a constraint is cleared by a
        // fresh healthy observation, not by a status" - and it went unconnected to what was on the
        // dashboard until the factory sat idle with the constraint open.
        //
        // Measured live: the constraint was filed 00:44Z, compiled 01:15Z, its derived work item finished
        // 01:27Z, and at 02:09Z the product still answered nothing. The attempt had failed, the row was
        // `converted_to_task`, and nothing could ever file it again - so the factory had zero queued work
        // while its own highest-priority work was open. An idle factory with an unrefuted product is not
        // "everything is done"; by §1 it means the system stopped looking.
        //
        // The caller reaches this method only when the newest REAL observation came back unhealthy, so
        // "the constraint is open" is established by fresh evidence rather than by bookkeeping. What is
        // guarded here is duplication of work in flight, not the constraint's existence.
        List<WishlistEntity> existing = wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                project.getId(), WishlistSource.product_not_launchable,
                List.of(WishlistStatus.pending, WishlistStatus.compiling, WishlistStatus.finalizing,
                        WishlistStatus.converted_to_task, WishlistStatus.dismissed));
        boolean alreadyBeingWorked = existing.stream().anyMatch(w -> w.getStatus() == WishlistStatus.pending
                || w.getStatus() == WishlistStatus.compiling || w.getStatus() == WishlistStatus.finalizing);
        if (alreadyBeingWorked) {
            return; // an attempt is in flight - never a second one beside it
        }
        // A finished attempt that did not fix the product may be retried, but not on the very next tick:
        // the cooldown is what keeps "keep working the constraint" from becoming a compile loop.
        Instant newest = existing.stream()
                .map(WishlistEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (newest != null && newest.isAfter(Instant.now().minus(Duration.ofMinutes(constraintRefileCooldownMinutes)))) {
            log.info("LaunchabilityConstraintService: project {} - the launchability constraint is still open, but the "
                            + "last attempt was filed at {}; waiting out the {}-minute cooldown before re-filing",
                    project.getId(), newest, constraintRefileCooldownMinutes);
            return;
        }
        if (!existing.isEmpty()) {
            log.info("LaunchabilityConstraintService: project {} - the launchability constraint is STILL open after "
                            + "{} finished attempt(s); re-filing it rather than leaving the factory idle",
                    project.getId(), existing.size());
        }
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.product_not_launchable);
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setLeanValue(LeanValue.essential);
        wishlist.setCynefinDomain("clear");
        // 2026-08-19: carry the OBSERVED CAUSE, not only the fact of failure. The launcher already
        // records exactly why the launch died in ClientRuntimeObservationEntity.errorText, and that text
        // sits in this very summary's productObservations - measured live on test-forty-ninth:
        // "object-storage Error failed to resolve reference minio/minio:RELEASE.2023-09-20T22-40-07Z:
        // not found". Filing "it is not healthy" while holding that string asks the worker to rediscover
        // what the system already knows, and it is the same defect the auditor's ABSTAIN fix removed on
        // 2026-08-18: a claim must arrive with its witness, or the reader cannot act on it.
        wishlist.setContent("The delivered product's most recent runtime observation was not healthy "
                + "(launch failed, or launched but its health check failed). Fix this before any further "
                + "philosophical review - reviewing a product that doesn't actually run produces no real "
                + "evidence, only guesses."
                + ((observedCause == null || observedCause.isBlank()) ? ""
                        : "\n\nObserved failure, exactly as the launcher recorded it - this is evidence, not a "
                                + "hypothesis, so start here rather than by re-deriving it:\n" + observedCause));
        wishlist.setJtbd("When the product doesn't currently launch or respond healthily, I want that "
                + "fixed before anything else, so all other evaluation (philosophical, design, feature "
                + "work) is grounded in a real, working product");
        wishlist.setAcceptanceCriteria("Given the project's runtime observation history, When the next "
                + "observation cycle runs, Then launchSuccess=true and the health check returns 2xx");
        // 2026-08-19: address this to the INTEGRATION role explicitly. Until now it named no role, so
        // TechnicalLeadCompiler.targetRoleForWishlist fell through to keyword inference over this text -
        // which contains no "merge"/"integration"/"artifact" - and routed it to whoever the wording
        // happened to resemble. Measured on test-forty-ninth: the MinIO blocker became a TAG-05
        // "Build Pipeline" task. Operations correctly fixed a symbol; the defect was an ASSEMBLY defect,
        // and the assembly had no owner. BARCAN-TAG-00 (CODE-GUARDIAN, INTEGRATION, stage 70) is that
        // owner and had 0 tasks on this project, 4 across the factory's whole history - not because it is
        // unroutable, but because integration is nobody's requirement and a requirement-pulled
        // decomposition cannot produce it. A product that will not run is the one case where the assembly
        // itself is the work, so this is where the role gets reached.
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setDod("BARCAN-TAG-00: the product's artifacts agree with its declared runtime contract. "
                + "The contract (docs/architecture/adr-002-runtime-contract.md, owned by BARCAN-TAG-01) is "
                + "the single source of truth for which services this product runs against; docker-compose.yml, "
                + "the build manifest and the application configuration must all be derivable from it. Where "
                + "the contract does not yet name a service the product depends on, extending the contract is "
                + "part of this work - do not pick a stack unilaterally in one artifact. Done when the product "
                + "launches and its health check passes.");
        wishlistRepository.save(wishlist);
        log.info("LaunchabilityConstraintService: created product_not_launchable wishlist for project {}", project.getId());
    }
}
