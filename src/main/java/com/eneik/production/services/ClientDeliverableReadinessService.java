package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
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

        // Ф5 (2026-07-21 review): a wishlist's derived task can get abandoned (merge-conflict escalation,
        // force-unblock exhaustion) and replaced by a NEW task under a NEW recovery wishlist - matching
        // ONLY by the original wishlist's literal id would never see that replacement merge, permanently
        // pinning this deliverable at "not merged" even after the real work ships. The recovery wishlist
        // always inherits the original task's featureId (AutoMergeService's escalation /
        // JulesDispatchService's follow-up synthesis both set it explicitly, and
        // FeatureService.resolveOrCreateFeatureId preserves an already-set featureId rather than minting a
        // new one), so matching by featureId in ADDITION to sourceWishlistId correctly follows the
        // deliverable through any number of recovery hops.
        //
        // Kept the ORIGINAL sourceWishlistId join alongside the new featureId one, not instead of it - a
        // wishlist/task pair with no featureId set at all (confirmed live: TaskClaimServiceTest and
        // GateOrchestratorIntegrationTest's build-phase fixtures construct entities directly without ever
        // routing through featureService.resolveOrCreateFeatureId, so featureId stays null - a real,
        // legitimate case, not just test artifice) must still match exactly as it always did. featureId
        // matching is additive coverage for the recovery-chain case, not a replacement for the base case.
        List<UUID> wishlistIds = clientDeliverables.stream().map(WishlistEntity::getId).toList();
        List<TaskEntity> tasksBySourceWishlistId = taskRepository.findBySourceWishlistIdIn(wishlistIds);

        Map<UUID, UUID> featureIdByWishlistId = new HashMap<>();
        for (WishlistEntity w : clientDeliverables) {
            if (w.getFeatureId() != null) {
                featureIdByWishlistId.put(w.getId(), w.getFeatureId());
            }
        }
        java.util.Set<UUID> relevantFeatureIds = new java.util.HashSet<>(featureIdByWishlistId.values());
        List<TaskEntity> tasksByFeatureId = relevantFeatureIds.isEmpty()
                ? List.of()
                : taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                        .filter(t -> t.getFeatureId() != null && relevantFeatureIds.contains(t.getFeatureId()))
                        .toList();

        Map<UUID, Boolean> mergedByWishlistId = new HashMap<>();
        for (TaskEntity task : tasksBySourceWishlistId) {
            if (task.getSourceWishlistId() == null) {
                continue;
            }
            mergedByWishlistId.merge(task.getSourceWishlistId(), isTaskMerged(task.getId()), (a, b) -> a || b);
        }
        Map<UUID, Boolean> mergedByFeatureId = new HashMap<>();
        for (TaskEntity task : tasksByFeatureId) {
            mergedByFeatureId.merge(task.getFeatureId(), isTaskMerged(task.getId()), (a, b) -> a || b);
        }

        long mergedCount = clientDeliverables.stream()
                .filter(w -> {
                    if (Boolean.TRUE.equals(mergedByWishlistId.get(w.getId()))) {
                        return true;
                    }
                    UUID featureId = featureIdByWishlistId.get(w.getId());
                    return featureId != null && Boolean.TRUE.equals(mergedByFeatureId.get(featureId));
                })
                .count();
        int total = clientDeliverables.size();
        return new Readiness(total, (int) mergedCount, (double) mergedCount / total);
    }

    /**
     * Ф4/Д3 (2026-07-21): a dependsOn edge is satisfied if the dependency genuinely merged, OR - if the
     * dependency was abandoned (TaskStatus.failed, via merge-conflict escalation or force-unblock
     * exhaustion) - if a REPLACEMENT task has merged instead. The replacement lives under a brand-new
     * task/wishlist id (recovery wishlists always inherit the abandoned task's featureId - see
     * computeForProject's doc - and the same role, since the recovery wishlist copies
     * `task.getRole().getTag()` as its own sourceRoleTag), so a literal dependsOn task-id match would
     * never see it and the downstream task would sit in `queued` forever with no visibility. This is
     * naturally self-healing through any number of re-abandonments: each check re-scans for the CURRENT
     * best replacement rather than requiring anything to repoint a foreign key at escalation time (which
     * isn't even possible then - the replacement task doesn't exist yet at the moment of escalation, only
     * a wishlist that hasn't been compiled into a task).
     */
    public boolean isDependencySatisfied(TaskEntity dependency) {
        if (dependency == null) {
            return true;
        }
        if (isTaskMerged(dependency.getId())) {
            return true;
        }
        if (dependency.getStatus() != TaskStatus.failed || dependency.getFeatureId() == null
                || dependency.getRole() == null || dependency.getProject() == null) {
            return false;
        }
        String roleTag = dependency.getRole().getTag();
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(dependency.getProject().getId()).stream()
                .filter(t -> !t.getId().equals(dependency.getId()))
                .filter(t -> t.getRole() != null && roleTag.equals(t.getRole().getTag()))
                .filter(t -> dependency.getFeatureId().equals(t.getFeatureId()))
                .anyMatch(t -> isTaskMerged(t.getId()));
    }

    /**
     * Whether a task's PR genuinely merged - not TaskStatus.done, which is set at review approval
     * independently of whether the merge itself ever succeeded (see class doc). Public so any admission
     * gate that needs "did this specific dependency's code actually land" - not just "was it approved" -
     * shares one definition instead of each call site drifting to its own (in)correct one.
     */
    public boolean isTaskMerged(UUID taskId) {
        List<UUID> sessionIds = julesSessionRepository.findByTaskId(taskId).stream()
                .map(session -> session.getId())
                .toList();
        if (sessionIds.isEmpty()) {
            return false;
        }
        return prReviewRepository.existsByJulesSessionIdInAndMergedTrue(sessionIds);
    }
}
