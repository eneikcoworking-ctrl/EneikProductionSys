package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Releases a wishlist claim stranded in the transient {@code finalizing} state.
 *
 * {@code finalizing} is a guard, not a resting place. {@code JulesDispatchService} sets
 * {@code compiling -> finalizing} before doing the slow GitHub/parse work so a concurrent replay of the
 * same completion backs off instead of duplicating it, and moves it out again when that work finishes or
 * fails. Both transitions belong to the same code path. **If that path dies mid-work - crash, restart, an
 * OOM-killed JVM - nothing else ever moves the row**, and the codebase says so in two places:
 *
 *   JulesDispatchService:2308  "leaving the wishlist permanently stuck in `finalizing` with no other
 *                               recovery path"
 *   JulesDispatchService:2432  "stuck in `finalizing`, which would make every future admission's
 *                               compare-and-swap fail forever"
 *
 * The consequence is not local. A stranded root makes {@code everyRootCompiled} false, which makes
 * {@code decompositionComplete} false, which holds the project in Flow Core state {@code DECOMPOSING},
 * in which {@code RECOVER_FAILED_FRONTIER}, {@code DISPATCH_QUEUED_TASKS} and {@code DISPATCH_REVIEW_TASKS}
 * are all denied. **One row halts the whole project.** Measured on test-forty-ninth 2026-08-18: wishlist
 * ca1c6413 stranded since 2026-08-16T09:39:17Z - 38 hours - with zero tasks in flight the entire time and
 * every orchestration tick logging denials that all cited that one state.
 *
 * Why the existing recovery does not reach it: {@code GeminiObserverActionService.retireStuckWorker}
 * releases claims listed in a persistent worker's {@code currentBatchIds}, and the 2026-08-13 diagnostic in
 * InternalGeminiObserverController records that neither of this project's workers held the stranded
 * wishlists - the real membership for a compiler completion lives in a {@code compilesWishlistIds} marker
 * on the compiler task's own payload. So the tool looks in the wrong place, which is why the same failure
 * was investigated by hand five days earlier and left unfixed.
 *
 * This sweep does not need to find the holder at all, which is the point. {@code finalizing} is documented
 * as covering one bounded piece of work; a claim older than any such work could take is stranded **by
 * definition of the state**, whoever set it. That is the referent test - ask whether the thing that set it
 * still exists - and it answers without a reverse pointer the schema never had.
 *
 * Three properties that make this safe:
 *
 *   - **Compare-and-swap, never read-then-write** (Charter invariant 1). If the real holder is alive and
 *     finishes concurrently, the CAS finds a status other than {@code finalizing}, changes nothing, and
 *     reports zero. A live worker can never be robbed of its claim.
 *   - **A sweep, not a retry** (D2). It must never carry an attempt budget: bounding a periodic sweep is
 *     how monitoring stops without anyone noticing. Each release is terminal for that row, so there is no
 *     descending chain to prove - the well-foundedness is per-row and immediate.
 *   - **Idempotent** (Charter invariant 4). A row already moved on is simply not in {@code finalizing},
 *     which is "already done, nothing to do" rather than an error.
 *
 * Released to {@code pending}, the same target {@code GeminiObserverActionService} uses, so the wishlist
 * re-enters compilation through the ordinary admission path rather than skipping it.
 */
@Service
public class StrandedFinalizingSweepService {

    private static final Logger log = LoggerFactory.getLogger(StrandedFinalizingSweepService.class);

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;

    /**
     * Self-proxy, the idiom OpsAuditorService already uses here. compareAndSetStatus is a @Modifying query
     * and needs an actual transaction; a plain this.sweepProject(...) call bypasses the Spring proxy, so the
     * @Transactional never applies and the write fails with "No EntityManager with actual transaction
     * available" - which is exactly how the first deployment of this class failed at 00:10:00Z. @Lazy breaks
     * the constructor cycle that self-injection would otherwise create.
     */
    private final StrandedFinalizingSweepService self;

    /**
     * How long {@code finalizing} may legitimately last. It covers one GitHub fetch plus a parse, so 3
     * minutes is still generous by two orders of magnitude (plan parse takes < 2 seconds). The cost of
     * waiting 30 minutes was a project frozen in DECOMPOSING whenever a compiler node got stranded.
     */
    @Value("${stranded-finalizing.max-age-minutes:3}")
    private long maxAgeMinutes;

    public StrandedFinalizingSweepService(ProjectRepository projectRepository,
                                           WishlistRepository wishlistRepository,
                                           @org.springframework.context.annotation.Lazy StrandedFinalizingSweepService self) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.self = self;
    }

    @Scheduled(cron = "${stranded-finalizing.cron:0 * * * * ?}")
    public void sweep() {
        for (ProjectEntity project : projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.active)
                .toList()) {
            LogScope.project(project.getId());
            try {
                self.sweepProject(project);
            } catch (Exception e) {
                log.error("StrandedFinalizingSweepService: failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void sweepProject(ProjectEntity project) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(maxAgeMinutes));
        for (WishlistEntity w : wishlistRepository.findByProjectIdAndStatus(project.getId(), WishlistStatus.finalizing)) {
            // lastCompileDispatchedAt is the closest available referent for "when this claim was taken" -
            // the dispatch that leads to finalizing. createdAt is the fallback for a row that predates the
            // column being populated; it can only ever be older, so it never releases something too early.
            Instant since = w.getLastCompileDispatchedAt() != null ? w.getLastCompileDispatchedAt() : w.getCreatedAt();
            if (since == null || since.isAfter(cutoff)) {
                continue;
            }
            long ageMinutes = Duration.between(since, Instant.now()).toMinutes();
            int released = wishlistRepository.compareAndSetStatus(
                    w.getId(), WishlistStatus.finalizing, WishlistStatus.pending);
            if (released == 1) {
                log.warn("StrandedFinalizingSweepService: released wishlist {} from finalizing after {} minutes "
                                + "- the transient guard outlived any work it could cover, so whatever set it is gone. "
                                + "Returned to pending for ordinary re-admission (project {})",
                        w.getId(), ageMinutes, project.getName());
            } else {
                // The CAS lost, which means a live holder moved it on between the read and the write. That is
                // the mechanism working, not a failure.
                log.info("StrandedFinalizingSweepService: wishlist {} left finalizing on its own before release "
                        + "- a live holder finished concurrently (project {})", w.getId(), project.getName());
            }
        }
    }
}
