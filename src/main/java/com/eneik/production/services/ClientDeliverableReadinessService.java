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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClientDeliverableReadinessService.class);

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
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;

    public ClientDeliverableReadinessService(WishlistRepository wishlistRepository,
                                             FeatureRepository featureRepository,
                                             TaskRepository taskRepository,
                                             JulesSessionRepository julesSessionRepository,
                                             PrReviewRepository prReviewRepository,
                                             com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository) {
        this.wishlistRepository = wishlistRepository;
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.featureThreadRepository = featureThreadRepository;
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

        // Orphan-feature fix (2026-07-24, live incident: PessimisticLockingFailureException retry storm
        // during wishlist decomposition left stray FeatureEntity rows behind whose insert survived a failed
        // transaction's rollback while nothing ever attached a wishlist to them - confirmed on test-thirty-
        // seventh, 12 FeatureEntity rows vs 5 real эпики reconstructed from the actual task graph). An
        // orphan is unreferenced by construction: no wishlist row's featureId ever points at it. Filtering
        // on that signal (not a new column/flag - none exists) removes exactly the orphans without touching
        // any real feature, since every real feature's OWN root wishlist gets its featureId set to itself at
        // creation time (FeatureService.resolveOrCreateFeatureId).
        Set<UUID> featureIdsWithWishlistWork = allWishlist.stream()
                .map(WishlistEntity::getFeatureId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<FeatureEntity> sourceMatchedFeatures = featureRepository.findByProjectId(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && sources.contains(root.getSource());
                })
                .filter(feature -> rootWishlistId == null || rootWishlistId.equals(feature.getRootWishlistId()))
                .toList();
        List<FeatureEntity> nonOrphanFeatures = sourceMatchedFeatures.stream()
                .filter(feature -> featureIdsWithWishlistWork.contains(feature.getId()))
                .toList();
        if (nonOrphanFeatures.size() != sourceMatchedFeatures.size()) {
            List<UUID> orphanIds = sourceMatchedFeatures.stream()
                    .map(FeatureEntity::getId)
                    .filter(id -> !featureIdsWithWishlistWork.contains(id))
                    .toList();
            log.warn("ClientDeliverableReadinessService: excluded {} orphaned FeatureEntity row(s) with no "
                            + "wishlist ever referencing them (project {}): {}",
                    orphanIds.size(), projectId, orphanIds);
        }
        List<FeatureEntity> productFeatures = deduplicateFeaturesByTitle(nonOrphanFeatures, allWishlist, projectId);
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

        // Live bug found 2026-07-24: a work-item wishlist that the compiler already stamped with
        // compiledByRole, then later got correctly recognized as a semantic duplicate and marked
        // `dismissed` (never produced a task, never will), was still counted in the denominator here - no
        // status filter at all. `dismissed` is this codebase's own established meaning for "this row never
        // led to any real work" (see deduplicateFeaturesByTitle/buildTaskGraphFromSlices callers) - counting
        // it here permanently caps a feature's readiness below 100% with no path to ever close the gap.
        // Confirmed live: 3 dismissed duplicate slices from the earlier coverage-audit self-loop incident
        // (already-fixed root cause) were still dragging "Anti-Ban and Account Warm-up System" to a
        // permanent 4/8 on test-thirty-seventh.
        List<WishlistEntity> plannedItems = allWishlist.stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
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

        // Operator directive 2026-07-24, sharpened over two rounds of correction: "формулу надо считать
        // только по задачам с кодом, а не спайкам, ревью и прочему вспомогательному" (this ratio must only
        // count tasks that produce code, not spikes/reviews/other auxiliary work). Confirmed live: a
        // legitimate `complex`-Cynefin spike (AutoMergeService deliberately never merges its PR - its
        // deliverable is a decision record, not code) permanently stuck the denominator via the exact same
        // "requires a merged review" check duplicate-task pollution does, with zero relation to
        // duplication. `everyRootCompiled`/`everyFeaturePlanned` above stay computed against the FULL
        // (unfiltered) plannedItems - decomposition-completeness means "has this been planned", not "how
        // much of it merged", and must not be held hostage by items this metric is about to exclude.
        List<WishlistEntity> codeProducingItems = plannedItems.stream()
                .filter(w -> !isAuxiliaryPlannedItem(tasksByPlannedItem.getOrDefault(w.getId(), List.of())))
                .toList();

        int mergedCount = (int) codeProducingItems.stream()
                .filter(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())))
                .count();
        int completeFeatures = (int) productFeatures.stream()
                .filter(feature -> {
                    List<WishlistEntity> featureItems = codeProducingItems.stream()
                            .filter(w -> feature.getId().equals(w.getFeatureId()))
                            .toList();
                    return !featureItems.isEmpty() && featureItems.stream()
                            .allMatch(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())));
                })
                .count();
        boolean everyFeaturePlanned = productFeatures.stream().allMatch(feature -> plannedItems.stream()
                .anyMatch(w -> feature.getId().equals(w.getFeatureId())));
        boolean decompositionComplete = everyRootCompiled && everyFeaturePlanned;
        int total = codeProducingItems.size();
        if (total == 0) {
            // Every planned item for this scope is auxiliary (decision/spike/review work only) - there is
            // nothing this metric can measure, not "0% done". Report via decompositionComplete alone rather
            // than a misleading 0/0 ratio.
            return new Readiness(productFeatures.size(), completeFeatures, 0, 0, 1.0, decompositionComplete);
        }
        return new Readiness(productFeatures.size(), completeFeatures, total, mergedCount,
                (double) mergedCount / total, decompositionComplete);
    }

    /**
     * Dedup fix (2026-07-24, live incident confirmed via the new /epics verification endpoint): the same
     * PessimisticLockingFailureException retry storm that left orphaned FeatureEntity rows (see the filter
     * above) ALSO left non-orphaned duplicates - 7 extra "Account and Session Management" rows, all sharing
     * the same rootWishlistId, created one minute apart during the exact incident window, each with exactly
     * one spurious wishlist item attached (so the orphan filter alone doesn't catch them - they DO have a
     * wishlist reference, just a bogus one). Grouped by (rootWishlistId, title) - a real semantic-duplicate
     * signal for this specific incident shape, not a general content-similarity matcher (that's
     * SelfFalsificationEpicMatcher's job, used elsewhere for a different problem: deciding whether a NEW
     * epic matches an EXISTING one at decomposition time, not cleaning up already-created duplicates).
     * Within a group, keeps the row with the most real wishlist items attached (the retry storm's spurious
     * duplicates only ever got exactly one), tie-broken by earliest createdAt - never deletes the losing
     * rows, only excludes them from the count, same non-destructive pattern as the orphan filter.
     */
    private List<FeatureEntity> deduplicateFeaturesByTitle(List<FeatureEntity> features,
            List<WishlistEntity> allWishlist, UUID projectId) {
        Map<UUID, Long> itemCountByFeatureId = allWishlist.stream()
                .filter(w -> w.getFeatureId() != null)
                .collect(java.util.stream.Collectors.groupingBy(WishlistEntity::getFeatureId, java.util.stream.Collectors.counting()));
        Map<String, List<FeatureEntity>> byKey = features.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        f -> f.getRootWishlistId() + "::" + f.getTitle(),
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<FeatureEntity> result = new java.util.ArrayList<>();
        List<UUID> excludedIds = new java.util.ArrayList<>();
        for (List<FeatureEntity> group : byKey.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }
            FeatureEntity winner = group.stream()
                    .max(java.util.Comparator
                            .<FeatureEntity>comparingLong(f -> itemCountByFeatureId.getOrDefault(f.getId(), 0L))
                            .thenComparing(FeatureEntity::getCreatedAt, java.util.Comparator.reverseOrder()))
                    .orElseThrow();
            result.add(winner);
            for (FeatureEntity f : group) {
                if (!f.getId().equals(winner.getId())) {
                    excludedIds.add(f.getId());
                }
            }
        }
        if (!excludedIds.isEmpty()) {
            log.warn("ClientDeliverableReadinessService: excluded {} duplicate FeatureEntity row(s) sharing "
                            + "(rootWishlistId, title) with a more-complete sibling row (project {}): {}",
                    excludedIds.size(), projectId, excludedIds);
        }
        return result;
    }

    public record EpicDiagnostic(UUID id, String title, UUID rootWishlistId, java.time.Instant createdAt,
            boolean countedInTotalFeatures, boolean complete, int codeProducingItemCount, int mergedItemCount) {
    }

    /**
     * Operator directive 2026-07-24 ("перечислить все 12 эпиков и докажи что ты не лжёшь") - a real,
     * read-only listing of the exact FeatureEntity rows behind {@code productReadiness.totalFeatures}/
     * {@code completeFeatures}, so those numbers can be checked against ground truth instead of taken on
     * faith. Deliberately mirrors {@link #computeForSources}'s own filtering step for step (same
     * PRODUCT_ITERATION_SOURCES scope, same orphan exclusion, same per-feature completeness check) rather
     * than calling it, since that method returns only aggregate counts - if computeForSources's filtering
     * logic ever changes, this must be updated alongside it or the two will drift.
     */
    public List<EpicDiagnostic> listEpicDiagnostics(UUID projectId) {
        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        Map<UUID, WishlistEntity> wishlistById = new HashMap<>();
        for (WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }
        Set<UUID> featureIdsWithWishlistWork = allWishlist.stream()
                .map(WishlistEntity::getFeatureId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<FeatureEntity> sourceMatchedFeatures = featureRepository.findByProjectId(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && PRODUCT_ITERATION_SOURCES.contains(root.getSource());
                })
                .toList();
        List<FeatureEntity> nonOrphanFeatures = sourceMatchedFeatures.stream()
                .filter(feature -> featureIdsWithWishlistWork.contains(feature.getId()))
                .toList();
        List<FeatureEntity> productFeatures = deduplicateFeaturesByTitle(nonOrphanFeatures, allWishlist, projectId);
        Set<UUID> productFeatureIds = productFeatures.stream().map(FeatureEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Same dismissed-exclusion fix as computeForSources (2026-07-24) - kept in sync since this method
        // deliberately mirrors that one's filtering instead of calling it (see class javadoc above).
        List<WishlistEntity> plannedItems = allWishlist.stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
                .filter(w -> PRODUCT_ITERATION_SOURCES.contains(w.getSource()))
                .filter(w -> w.getFeatureId() != null && productFeatureIds.contains(w.getFeatureId()))
                .toList();
        List<UUID> plannedItemIds = plannedItems.stream().map(WishlistEntity::getId).toList();
        List<TaskEntity> tasks = plannedItemIds.isEmpty() ? List.of() : taskRepository.findBySourceWishlistIdIn(plannedItemIds);
        Map<UUID, List<TaskEntity>> tasksByPlannedItem = tasks.stream()
                .filter(t -> t.getSourceWishlistId() != null)
                .collect(java.util.stream.Collectors.groupingBy(TaskEntity::getSourceWishlistId));

        Map<UUID, Boolean> fulfilledByPlannedItem = new HashMap<>();
        for (WishlistEntity plannedItem : plannedItems) {
            boolean fulfilled = tasksByPlannedItem.getOrDefault(plannedItem.getId(), List.of()).stream()
                    .anyMatch(this::hasRequiredMergeEvidence);
            fulfilledByPlannedItem.put(plannedItem.getId(), fulfilled);
        }
        List<WishlistEntity> codeProducingItems = plannedItems.stream()
                .filter(w -> !isAuxiliaryPlannedItem(tasksByPlannedItem.getOrDefault(w.getId(), List.of())))
                .toList();

        List<EpicDiagnostic> result = new java.util.ArrayList<>();
        for (FeatureEntity feature : sourceMatchedFeatures) {
            boolean counted = productFeatureIds.contains(feature.getId());
            List<WishlistEntity> featureItems = codeProducingItems.stream()
                    .filter(w -> feature.getId().equals(w.getFeatureId()))
                    .toList();
            int mergedItemCount = (int) featureItems.stream()
                    .filter(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())))
                    .count();
            boolean complete = counted && !featureItems.isEmpty() && mergedItemCount == featureItems.size();
            result.add(new EpicDiagnostic(feature.getId(), feature.getTitle(), feature.getRootWishlistId(),
                    feature.getCreatedAt(), counted, complete, featureItems.size(), mergedItemCount));
        }
        return result;
    }

    /**
     * A planned item is "auxiliary" (excluded from the code-merge readiness ratio entirely, not just
     * exempted from the check) when every task it has produced so far structurally can never produce
     * mergeable code. NOTE: this deliberately reads the TASK's own role
     * ({@code task.getRole().getTag()}), not the wishlist row's {@code compiledByRole} - that field is the
     * COMPILER's role tag (near-always BARCAN-TAG-09, "Technical Lead", regardless of what role actually
     * executes the resulting task), not the executing role. Using it here would have misclassified nearly
     * every planned item as auxiliary regardless of its real task's role - caught via the existing test
     * suite's {@code plannedItems()} helper, which stamps every wishlist row BARCAN-TAG-09 for unrelated
     * reasons while giving tasks their own distinct role.
     * A task is auxiliary when:
     * - its role belongs to {@link EmsFlowStage#DECISION} (pure delivery/decision work, e.g. BARCAN-TAG-09
     *   "Delivery Plan" tasks), or
     * - its Cynefin domain is `complex` (a spike; AutoMergeService deliberately never merges its PR - see
     *   the earlier spike-dependency-deadlock fix in isDependencySatisfied).
     * A planned item with no tasks yet is not excluded pre-emptively - it is simply pending, same as
     * before this fix, and gets re-evaluated once real tasks exist.
     */
    private boolean isAuxiliaryPlannedItem(List<TaskEntity> itemTasks) {
        return !itemTasks.isEmpty() && itemTasks.stream().allMatch(this::isAuxiliaryTask);
    }

    private boolean isAuxiliaryTask(TaskEntity task) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : null;
        if (EmsFlowStage.forRoleTag(roleTag) == EmsFlowStage.DECISION) {
            return true;
        }
        return "complex".equals(task.getCynefinDomain());
    }

    private boolean hasRequiredMergeEvidence(TaskEntity task) {
        if (!reachedMain(task)) {
            return false;
        }
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        if ("BARCAN-TAG-09".equals(roleTag)) {
            return true;
        }
        return mergedReviews(task.getId()).stream().anyMatch(review -> Boolean.TRUE.equals(review.getHasCode()));
    }

    /**
     * Dashboard-number correctness (2026-07-24, live incident: 44% of "merged" PRs on a real project sat in
     * orphaned feature-thread branches that never reached main - see FeatureThreadEntity/
     * AutoMergeService.closeOutReadyFeatureThreads). A task's PR being merged is not the same claim as its
     * work having actually shipped to the product - it may have merged into a feature-thread branch that
     * itself hasn't (yet) folded into main. Deliberately used ONLY by hasRequiredMergeEvidence (dashboard/
     * isBuildPhase/falsification-eligibility) - isTaskMerged/isDependencySatisfied stay untouched, since
     * those gate real task dispatch and making cross-feature dependents wait for an entire upstream
     * feature's closeout would be a large, unintended latency regression nobody asked for.
     */
    public boolean reachedMain(TaskEntity task) {
        List<PrReviewEntity> merged = mergedReviews(task.getId());
        if (merged.isEmpty()) {
            return false;
        }
        // null baseRef = legacy data predating this fix, or local/mock-merge mode (github disabled) - fail
        // OPEN rather than retroactively flipping every historical dashboard to "not shipped" with no way
        // to backfill the missing field. Only a KNOWN non-main base is treated as "not yet in main."
        boolean anyDirectlyToMain = merged.stream()
                .anyMatch(r -> r.getBaseRef() == null || "main".equals(r.getBaseRef()));
        if (anyDirectlyToMain) {
            return true;
        }
        if (task.getFeatureId() == null || task.getProject() == null) {
            return false;
        }
        return featureThreadRepository.findByProjectIdAndFeatureId(task.getProject().getId(), task.getFeatureId())
                .map(t -> t.getMergedToMainAt() != null)
                .orElse(false);
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
     * Lean-waste fix (2026-07-23, operator directive; generalized 2026-07-24 from API_CONTRACT-only to
     * every {@link EmsFlowStage#isSpecStage spec stage}): a spec-stage dependency is a small, isolated
     * reference-document deliverable, not "a huge chunk of code" - a dependent only actually needs the
     * artifact's content, which exists as soon as its PR is open, not once it's fully merged. Deliberately
     * scoped to spec stages only (DECISION, ARCHITECTURE, API_CONTRACT, COMPLIANCE): every other dependency
     * edge (data-model -> contract, implementation -> operations, etc.) still requires
     * {@link #isDependencySatisfied} (full merge) - see the dispatch-gate call site in
     * ProjectFlowService.dispatchQueuedTasks for how the two are composed. `review`/`pending_review`/`done`
     * is the existing "a PR has been opened for this task" signal used throughout the pipeline - no new
     * status was introduced for this.
     */
    public boolean isSpecDependencyPrOpenButUnmerged(TaskEntity dependency) {
        if (dependency == null || dependency.getRole() == null) {
            return false;
        }
        if (!EmsFlowStage.isSpecStage(dependency.getRole().getTag())) {
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
        // A `complex`-Cynefin spike's deliverable IS its decision record/handoff note, not shippable code -
        // AutoMergeService deliberately never merges a spike's PR (see its "Cynefin Domain complex ...
        // Not merging branch" branch), so isTaskMerged above can never become true for one. Without this
        // case, any dependent chain rooted in a spike deadlocks permanently (confirmed live 2026-07-23,
        // test-thirty-sixth: a 5-task chain stuck in `queued` forever behind a `spike_completed` root).
        if (dependency.getStatus() == TaskStatus.spike_completed) {
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

    /**
     * Feature-thread closeout gate (2026-07-24) - see AutoMergeService.closeOutReadyFeatureThreads. A
     * feature is ready to fold its accumulation branch back into main once every task under it has reached
     * a terminal status and no wishlist item for it is still being turned into a task. `blocked` counts as
     * terminal here deliberately - a permanently blocked task must not hold its already-finished sibling
     * work hostage from ever reaching main.
     *
     * This can never be a provably final fact (the compiler can re-attach a brand-new root wishlist to an
     * existing feature at any later time - see FeatureService.findExistingEpic) - that's fine, a reopened
     * feature resets its thread's closeout state on its next has-code merge (see
     * AutoMergeService.classifyAndHandleBranch), so an early closeout is self-correcting, not a permanent
     * mistake.
     */
    public boolean isFeatureReadyForCloseout(UUID projectId, UUID featureId) {
        List<TaskEntity> featureTasks = taskRepository.findByFeatureId(featureId);
        if (featureTasks.isEmpty()) {
            return false;
        }
        boolean allTerminal = featureTasks.stream().allMatch(t -> switch (t.getStatus()) {
            case done, failed, spike_completed, blocked -> true;
            default -> false;
        });
        if (!allTerminal) {
            return false;
        }
        return wishlistRepository.findByFeatureId(featureId).stream()
                .noneMatch(w -> w.getStatus() == WishlistStatus.pending || w.getStatus() == WishlistStatus.compiling);
    }
}
