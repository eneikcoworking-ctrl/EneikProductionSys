package com.eneik.production.services;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Measures shipped product scope through the real hierarchy:
 * root wishlist -> features -> planned work items -> tasks -> merged PR evidence.
 *
 * A feature is complete only when every planned work item under it has its own valid merge. A merge
 * from an unrelated task in the same feature cannot satisfy the item. Engineering roles require a
 * merged PR classified as code; BARCAN-TAG-09 decision records require a real merge but may be record-only.
 */
@Service
public class ClientDeliverableReadinessService {

    private static final Set<WishlistSource> PRODUCT_ITERATION_SOURCES = EnumSet.of(
            WishlistSource.client,
            WishlistSource.coverage_gap,
            WishlistSource.self_falsification
    );

    @org.springframework.beans.factory.annotation.Value("${project.build-phase-deliverable-count:10}")
    private int buildPhaseDeliverableCount;

    private final WishlistRepository wishlistRepository;
    private final FeatureRepository featureRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;

    public ClientDeliverableReadinessService(WishlistRepository wishlistRepository,
                                             FeatureRepository featureRepository,
                                             TaskRepository taskRepository,
                                             JulesSessionRepository julesSessionRepository,
                                             PrReviewRepository prReviewRepository) {
        this.wishlistRepository = wishlistRepository;
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
    }

    // Deliberately scoped to CLIENT-sourced work only, not computeForProject's full
    // PRODUCT_ITERATION_SOURCES (client + coverage_gap + self_falsification). Confirmed live
    // (test-thirty-fifth, 2026-07-23): the moment a self_falsification wishlist gets decomposed into its
    // own tasks, computeForProject's project-wide totalDeliverables jumps from "just the client's items"
    // to "client + self-generated items", which can flip a project that had genuinely finished 100% of the
    // client's own brief back to <100% (or below buildPhaseDeliverableCount, for any brief smaller than
    // that threshold) - and since THIS SAME isBuildPhase gate is what keeps self-generated work from ever
    // dispatching in the first place, that self-generated work can never merge, so the denominator it
    // just inflated can never catch up. A real deadlock: 8 client tasks (100% merged, correctly out of
    // build phase) + falsification's own 8 follow-up tasks (0% merged) reads as 8/16 = 50%, permanently
    // re-entering build phase and freezing the very follow-up work that caused the reading to change.
    // "Has the client's own brief shipped" must never be measured using a denominator the answer to that
    // exact question controls.
    public boolean isBuildPhase(UUID projectId) {
        Readiness readiness = computeForClientBriefOnly(projectId);
        if (!readiness.decompositionComplete() || readiness.totalDeliverables() == 0) {
            return true;
        }
        boolean reachedCap = readiness.mergedDeliverables() >= buildPhaseDeliverableCount;
        boolean allPlannedWorkMerged = readiness.mergedDeliverables() >= readiness.totalDeliverables();
        return !(reachedCap || allPlannedWorkMerged);
    }

    public record Readiness(
            int totalFeatures,
            int completeFeatures,
            int totalDeliverables,
            int mergedDeliverables,
            double ratio,
            boolean decompositionComplete
    ) {
        // Compatibility for focused tests and callers that only need a ready/not-ready stub.
        public Readiness(int totalDeliverables, int mergedDeliverables, double ratio) {
            this(0, 0, totalDeliverables, mergedDeliverables, ratio, true);
        }

        public static Readiness none() {
            return new Readiness(0, 0, 0, 0, 0.0, false);
        }
    }

    public Readiness computeForProject(UUID projectId) {
        return computeForProject(projectId, null);
    }

    /**
     * Same hierarchy as {@link #computeForProject(UUID)}, scoped to the features rooted at exactly one
     * wishlist when {@code rootWishlistId} is non-null - used to decide "is THIS wishlist's own work fully
     * merged", independent of everything else going on in the project (e.g. gating a coverage audit that
     * must run once per wishlist, not once for the whole project's aggregate progress).
     */
    public Readiness computeForProject(UUID projectId, UUID rootWishlistId) {
        return computeForSources(projectId, rootWishlistId, PRODUCT_ITERATION_SOURCES);
    }

    private static final Set<WishlistSource> CLIENT_BRIEF_SOURCE = EnumSet.of(WishlistSource.client);

    /** See the comment on {@link #isBuildPhase}: excludes self-generated (coverage_gap, self_falsification) work. */
    private Readiness computeForClientBriefOnly(UUID projectId) {
        return computeForSources(projectId, null, CLIENT_BRIEF_SOURCE);
    }

    private Readiness computeForSources(UUID projectId, UUID rootWishlistId, Set<WishlistSource> sources) {
        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        Map<UUID, WishlistEntity> wishlistById = new HashMap<>();
        for (WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }

        List<FeatureEntity> productFeatures = featureRepository.findByProjectId(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && sources.contains(root.getSource());
                })
                .filter(feature -> rootWishlistId == null || rootWishlistId.equals(feature.getRootWishlistId()))
                .toList();
        Set<UUID> productFeatureIds = productFeatures.stream()
                .map(FeatureEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Raw iteration wishlists are containers. Derived work-item wishlists always carry compiledByRole.
        List<WishlistEntity> iterationRoots = allWishlist.stream()
                .filter(w -> sources.contains(w.getSource()))
                .filter(w -> w.getCompiledByRole() == null)
                .filter(w -> rootWishlistId == null || rootWishlistId.equals(w.getId()))
                .toList();
        boolean everyRootCompiled = !iterationRoots.isEmpty() && iterationRoots.stream()
                .allMatch(w -> w.getStatus() == WishlistStatus.converted_to_task
                        || w.getStatus() == WishlistStatus.dismissed);

        List<WishlistEntity> plannedItems = allWishlist.stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> sources.contains(w.getSource()))
                .filter(w -> w.getFeatureId() != null && productFeatureIds.contains(w.getFeatureId()))
                .toList();
        if (productFeatures.isEmpty() || plannedItems.isEmpty()) {
            return Readiness.none();
        }

        List<UUID> plannedItemIds = plannedItems.stream().map(WishlistEntity::getId).toList();
        List<TaskEntity> tasks = taskRepository.findBySourceWishlistIdIn(plannedItemIds);
        Map<UUID, List<TaskEntity>> tasksByPlannedItem = tasks.stream()
                .filter(t -> t.getSourceWishlistId() != null)
                .collect(java.util.stream.Collectors.groupingBy(TaskEntity::getSourceWishlistId));

        Map<UUID, Boolean> fulfilledByPlannedItem = new HashMap<>();
        for (WishlistEntity plannedItem : plannedItems) {
            boolean fulfilled = tasksByPlannedItem.getOrDefault(plannedItem.getId(), List.of()).stream()
                    .anyMatch(this::hasRequiredMergeEvidence);
            fulfilledByPlannedItem.put(plannedItem.getId(), fulfilled);
        }

        int mergedCount = (int) plannedItems.stream()
                .filter(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())))
                .count();
        int completeFeatures = (int) productFeatures.stream()
                .filter(feature -> {
                    List<WishlistEntity> featureItems = plannedItems.stream()
                            .filter(w -> feature.getId().equals(w.getFeatureId()))
                            .toList();
                    return !featureItems.isEmpty() && featureItems.stream()
                            .allMatch(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())));
                })
                .count();
        boolean everyFeaturePlanned = productFeatures.stream().allMatch(feature -> plannedItems.stream()
                .anyMatch(w -> feature.getId().equals(w.getFeatureId())));
        boolean decompositionComplete = everyRootCompiled && everyFeaturePlanned;
        int total = plannedItems.size();
        return new Readiness(productFeatures.size(), completeFeatures, total, mergedCount,
                (double) mergedCount / total, decompositionComplete);
    }

    private boolean hasRequiredMergeEvidence(TaskEntity task) {
        List<PrReviewEntity> mergedReviews = mergedReviews(task.getId());
        if (mergedReviews.isEmpty()) {
            return false;
        }
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        if ("BARCAN-TAG-09".equals(roleTag)) {
            return true;
        }
        return mergedReviews.stream().anyMatch(review -> Boolean.TRUE.equals(review.getHasCode()));
    }

    private List<PrReviewEntity> mergedReviews(UUID taskId) {
        List<UUID> sessionIds = julesSessionRepository.findByTaskId(taskId).stream()
                .map(session -> session.getId())
                .toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return prReviewRepository.findByJulesSessionIdInAndMergedTrue(sessionIds);
    }

    /**
     * Lean-waste fix (2026-07-23, operator directive): an API-contract-stage dependency
     * ({@link EmsFlowStage#API_CONTRACT}) is a small, isolated spec deliverable, not "a huge chunk of
     * code" - a dependent only actually needs the contract's content, which exists as soon as its PR is
     * open, not once it's fully merged. Deliberately scoped to this ONE stage: every other dependency edge
     * (data-model -> contract, implementation -> operations, etc.) still requires
     * {@link #isDependencySatisfied} (full merge) - see the dispatch-gate call site in
     * ProjectFlowService.dispatchQueuedTasks for how the two are composed. `review`/`pending_review`/`done`
     * is the existing "a PR has been opened for this task" signal used throughout the pipeline - no new
     * status was introduced for this.
     */
    public boolean isApiContractPrOpenButUnmerged(TaskEntity dependency) {
        if (dependency == null || dependency.getRole() == null) {
            return false;
        }
        if (EmsFlowStage.forRoleTag(dependency.getRole().getTag()) != EmsFlowStage.API_CONTRACT) {
            return false;
        }
        return dependency.getStatus() == TaskStatus.review
                || dependency.getStatus() == TaskStatus.pending_review
                || dependency.getStatus() == TaskStatus.done;
    }

    /**
     * A failed dependency can be satisfied by a replacement only when it carries the exact same semantic
     * work-item key. Feature+role matching is too broad because one feature can contain several slices
     * owned by the same role.
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
        String semanticKey = payloadText(dependency, "ems_semantic_key");
        if (semanticKey.isBlank()) {
            return false;
        }
        String roleTag = dependency.getRole().getTag();
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(dependency.getProject().getId()).stream()
                .filter(t -> !t.getId().equals(dependency.getId()))
                .filter(t -> t.getRole() != null && roleTag.equals(t.getRole().getTag()))
                .filter(t -> dependency.getFeatureId().equals(t.getFeatureId()))
                .filter(t -> semanticKey.equals(payloadText(t, "ems_semantic_key")))
                .anyMatch(t -> isTaskMerged(t.getId()));
    }

    private String payloadText(TaskEntity task, String key) {
        if (task == null || task.getPayload() == null) {
            return "";
        }
        return task.getPayload().path(key).asText("");
    }

    /** Real merged state, independent of TaskStatus.done. */
    public boolean isTaskMerged(UUID taskId) {
        return !mergedReviews(taskId).isEmpty();
    }
}
