package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Answers "how much of what the client actually asked for has really shipped" - shipped meaning a
 * merged PR, not just a Jules session that reached the review stage (TaskStatus.done is set at review
 * approval by ClaimService.complete, independently of whether AutoMergeService's later merge attempt
 * ever succeeds - confirmed live in test-twenty-eighth: tasks marked done with zero of their PRs merged).
 *
 * A "client deliverable" is a wishlist row with source=client that has actually been compiled into a
 * task (compiledByRole != null) - this excludes the raw client brief container item itself, which is
 * never compiled directly, only decomposed into one or more of these.
 *
 * Shared by FalsificationCycleService (readiness gate before auditing) and the BUILD/MAINTENANCE phase
 * gate (ProjectFlowService) so both use the exact same definition of "done".
 */
@Service
public class ClientDeliverableReadinessService {

    // "The project should be fully built within the first ~10 real tasks; everything after that is
    // patches" (operator directive following the test-twenty-eighth post-mortem, where 82% of task
    // volume never traced back to the client brief at all). This is an absolute count of merged client
    // deliverables, independent of how many total client wishlist items a given brief decomposes into.
    @org.springframework.beans.factory.annotation.Value("${project.build-phase-deliverable-count:10}")
    private int buildPhaseDeliverableCount;

    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;

    public ClientDeliverableReadinessService(WishlistRepository wishlistRepository,
                                             TaskRepository taskRepository,
                                             JulesSessionRepository julesSessionRepository,
                                             PrReviewRepository prReviewRepository) {
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
    }

    /**
     * True while the project has not yet merged its first buildPhaseDeliverableCount client deliverables
     * - OR, for a brief that decomposes into fewer than that many deliverables in total (the common case:
     * test-twenty-eighth's brief was exactly 2), until every one of them has merged. Comparing only
     * against the raw threshold would permanently trap any small brief in BUILD phase forever, since
     * mergedDeliverables could never reach 10 - worse than the problem this gate was meant to fix.
     */
    public boolean isBuildPhase(UUID projectId) {
        Readiness readiness = computeForProject(projectId);
        if (readiness.totalDeliverables() == 0) {
            return true;
        }
        boolean reachedCap = readiness.mergedDeliverables() >= buildPhaseDeliverableCount;
        boolean allClientWorkMerged = readiness.mergedDeliverables() >= readiness.totalDeliverables();
        return !(reachedCap || allClientWorkMerged);
    }

    public record Readiness(int totalDeliverables, int mergedDeliverables, double ratio) {
        public static Readiness none() {
            return new Readiness(0, 0, 0.0);
        }
    }

    public Readiness computeForProject(UUID projectId) {
        List<WishlistEntity> clientDeliverables = wishlistRepository.findByProjectId(projectId).stream()
                .filter(w -> w.getSource() == WishlistSource.client && w.getCompiledByRole() != null)
                .toList();
        if (clientDeliverables.isEmpty()) {
            return Readiness.none();
        }

        List<UUID> wishlistIds = clientDeliverables.stream().map(WishlistEntity::getId).toList();
        List<TaskEntity> derivedTasks = taskRepository.findBySourceWishlistIdIn(wishlistIds);

        Map<UUID, Boolean> mergedByWishlistId = new HashMap<>();
        for (TaskEntity task : derivedTasks) {
            if (task.getSourceWishlistId() == null) {
                continue;
            }
            boolean merged = isTaskMerged(task.getId());
            mergedByWishlistId.merge(task.getSourceWishlistId(), merged, (a, b) -> a || b);
        }

        long mergedCount = clientDeliverables.stream()
                .filter(w -> Boolean.TRUE.equals(mergedByWishlistId.get(w.getId())))
                .count();
        int total = clientDeliverables.size();
        return new Readiness(total, (int) mergedCount, (double) mergedCount / total);
    }

    private boolean isTaskMerged(UUID taskId) {
        List<UUID> sessionIds = julesSessionRepository.findByTaskId(taskId).stream()
                .map(session -> session.getId())
                .toList();
        if (sessionIds.isEmpty()) {
            return false;
        }
        return prReviewRepository.existsByJulesSessionIdInAndMergedTrue(sessionIds);
    }
}
