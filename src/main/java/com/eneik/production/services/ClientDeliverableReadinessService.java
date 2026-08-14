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
import com.eneik.production.services.logging.LogScope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
    private final com.eneik.production.repositories.ProjectRepository projectRepository;
    // @Lazy: OperationalPolicyService -> OperationalFlowCoreService -> FlowSpineService ->
    // ClientDeliverableReadinessService (readinessService.computeForProject) is a real cycle back to this
    // class. Eager constructor injection here would fail Spring startup ("bean currently in creation");
    // a lazy proxy defers resolution past both beans' construction, same as this codebase's established
    // @Lazy self-injection pattern elsewhere.
    private final com.eneik.production.services.operational.OperationalPolicyService operationalPolicyService;

    public ClientDeliverableReadinessService(WishlistRepository wishlistRepository,
                                             FeatureRepository featureRepository,
                                             TaskRepository taskRepository,
                                             JulesSessionRepository julesSessionRepository,
                                             PrReviewRepository prReviewRepository,
                                             com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                                             com.eneik.production.repositories.ProjectRepository projectRepository,
                                             @org.springframework.context.annotation.Lazy
                                             com.eneik.production.services.operational.OperationalPolicyService operationalPolicyService) {
        this.wishlistRepository = wishlistRepository;
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.featureThreadRepository = featureThreadRepository;
        this.projectRepository = projectRepository;
        this.operationalPolicyService = operationalPolicyService;
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
    // Same transactional reasoning as computeForProject above - reaches the identical lazy dependsOn walk
    // via computeForClientBriefOnly -> computeForSources.
    @Transactional(readOnly = true)
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
            boolean decompositionComplete,
            // Self-referential-deadlock fix (2026-08-06, live incident test-forty-second: task 5ac0b91b
            // retired by iteration-admission poka-yoke with "no child work created", 2 dependents stuck
            // queued forever - see isDependencySatisfied's own comment admitting a failed dependency with
            // no replacement leaves a dependent "stuck in queued forever"). ratio()/completeFeatures() must
            // stay strictly "actually merged" - FlowSpineService/OperationalTruthService derive the
            // client-facing DELIVERED status from them, and already carry an invariant that warns if
            // completeFeatures reaches totalFeatures without matching merge evidence. self_falsification is
            // this codebase's OWN designated (and ONLY authorized - see "fix: enforce falsification-gated
            // product iterations") mechanism for producing replacement work for a permanently-failed task,
            // but its own readiness gate used ratio() too - a permanently-dead task can never itself count
            // as "feature complete", which permanently held the ratio below threshold, which permanently
            // blocked the one mechanism allowed to fix it. This field credits a work item toward
            // FALSIFICATION READINESS ONLY (never toward ratio/DELIVERED) when it is fulfilled OR its task
            // chain terminates in a real `failed` task - i.e. self_falsification has real, stable, terminal
            // material to examine, not "some of this project is still legitimately in flight".
            double selfFalsificationReadyRatio
    ) {
        // Compatibility for focused tests and callers that only need a ready/not-ready stub.
        public Readiness(int totalDeliverables, int mergedDeliverables, double ratio) {
            this(0, 0, totalDeliverables, mergedDeliverables, ratio, true, ratio);
        }

        // Compatibility for existing 6-arg call sites (production and tests) that don't yet know about
        // selfFalsificationReadyRatio - defaults it to ratio itself, i.e. "no dead-end credit", exactly
        // the old (bugged but at-least-not-silently-wrong) behavior for anything not explicitly computing
        // the real value below.
        public Readiness(int totalFeatures, int completeFeatures, int totalDeliverables, int mergedDeliverables,
                double ratio, boolean decompositionComplete) {
            this(totalFeatures, completeFeatures, totalDeliverables, mergedDeliverables, ratio,
                    decompositionComplete, ratio);
        }

        public static Readiness none() {
            return new Readiness(0, 0, 0, 0, 0.0, false, 0.0);
        }
    }

    // 2026-08-07 (confirmed live incident, test-forty-third): computeForSources below walks
    // TaskEntity.dependsOn, a @ManyToOne(fetch=LAZY) self-reference, via isTerminalOrAwaitingDeadDependency.
    // Without an active transaction spanning the whole call, each repository fetch inside gets its own
    // short-lived Hibernate session that closes before the lazy walk runs, so resolving a dependsOn proxy
    // not already resident in that session's L1 cache throws LazyInitializationException ("no Session") -
    // confirmed live from GeminiProjectObserverService.observeProject (itself uncransactional), which has no
    // special exposure of its own; any of this method's 10 real callers could hit the exact same failure,
    // it's just a matter of which task happens to need a fresh proxy resolution. @Transactional here (not on
    // the private computeForSources - a private method can never be proxied, see the Spring self-invocation
    // note throughout this codebase) closes the gap for every caller at once via REQUIRED propagation,
    // joining an existing transaction harmlessly where one already exists.
    @Transactional(readOnly = true)
    public Readiness computeForProject(UUID projectId) {
        return computeForProject(projectId, null);
    }

    /**
     * Same hierarchy as {@link #computeForProject(UUID)}, scoped to the features rooted at exactly one
     * wishlist when {@code rootWishlistId} is non-null - used to decide "is THIS wishlist's own work fully
     * merged", independent of everything else going on in the project (e.g. gating a coverage audit that
     * must run once per wishlist, not once for the whole project's aggregate progress).
     */
    @Transactional(readOnly = true)
    public Readiness computeForProject(UUID projectId, UUID rootWishlistId) {
        return computeForSources(projectId, rootWishlistId, PRODUCT_ITERATION_SOURCES);
    }

    /**
     * All tasks belonging to features rooted at the given wishlist - same feature-resolution rootWishlistId
     * semantics computeForSources uses internally (a feature's own rootWishlistId, not its featureId),
     * exposed directly for a caller that needs the underlying task list itself rather than aggregate counts.
     * Built for ProjectFlowService.checkAndDispatchCoverageAudits (2026-07-26 operator directive: "привязать
     * к целому вишлисту" - the coverage-audit re-trigger watermark must only look at merges belonging to
     * THIS wishlist's own features, not any merge anywhere in the project, which previously made the audit
     * re-fire every time ANY unrelated work merged and kept creating new coverage_gap wishlists in response -
     * a self-perpetuating loop that could keep decompositionComplete from ever stabilizing).
     */
    public Set<UUID> findWishlistTreeIds(UUID projectId, UUID rootWishlistId) {
        if (rootWishlistId == null) return Set.of();
        Set<UUID> tree = new java.util.HashSet<>();
        tree.add(rootWishlistId);
        return tree;
    }

    public List<TaskEntity> listTasksForRootWishlist(UUID projectId, UUID rootWishlistId) {
        if (rootWishlistId == null) return List.of();
        Set<UUID> treeIds = findWishlistTreeIds(projectId, rootWishlistId);
        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        Set<UUID> featureIds = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId).stream()
                .filter(feature -> treeIds.contains(feature.getRootWishlistId()) ||
                        allWishlist.stream().anyMatch(w -> treeIds.contains(w.getId()) && feature.getId().equals(w.getFeatureId())))
                .map(FeatureEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (featureIds.isEmpty()) {
            return taskRepository.findBySourceWishlistIdIn(treeIds.stream().toList());
        }
        List<UUID> plannedItemIds = allWishlist.stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getFeatureId() != null && featureIds.contains(w.getFeatureId()))
                .map(WishlistEntity::getId)
                .toList();
        if (plannedItemIds.isEmpty()) {
            return taskRepository.findBySourceWishlistIdIn(treeIds.stream().toList());
        }
        return taskRepository.findBySourceWishlistIdIn(plannedItemIds);
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

        Set<UUID> treeIds = rootWishlistId != null ? findWishlistTreeIds(projectId, rootWishlistId) : Set.of();

        Set<UUID> featureIdsWithWishlistWork = allWishlist.stream()
                .map(WishlistEntity::getFeatureId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<FeatureEntity> sourceMatchedFeatures = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && sources.contains(root.getSource());
                })
                .filter(feature -> rootWishlistId == null || treeIds.contains(feature.getRootWishlistId()) ||
                        allWishlist.stream().anyMatch(w -> treeIds.contains(w.getId()) && feature.getId().equals(w.getFeatureId())))
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
        DedupResult dedup = deduplicateFeaturesByTitle(nonOrphanFeatures, allWishlist);
        List<FeatureEntity> productFeatures = dedup.winners();
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
                .filter(w -> w.getFeatureId() != null && productFeatureIds.contains(dedup.canonicalOrSelf(w.getFeatureId())))
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
                            .filter(w -> feature.getId().equals(dedup.canonicalOrSelf(w.getFeatureId())))
                            .toList();
                    return !featureItems.isEmpty() && featureItems.stream()
                            .allMatch(w -> Boolean.TRUE.equals(fulfilledByPlannedItem.get(w.getId())));
                })
                .count();
        boolean everyFeaturePlanned = productFeatures.stream().allMatch(feature -> plannedItems.stream()
                .anyMatch(w -> feature.getId().equals(dedup.canonicalOrSelf(w.getFeatureId()))));
        boolean decompositionComplete = everyRootCompiled && everyFeaturePlanned;
        int total = codeProducingItems.size();
        int totalFeatures = productFeatures.size();

        // See the Readiness.selfFalsificationReadyRatio javadoc: a work item counts toward THIS metric
        // when it's fulfilled OR its task chain bottoms out at a real `failed` task with no replacement -
        // self_falsification has real, terminal material to look at either way. Never reused for
        // completeFeatures/ratio itself (those must stay "actually merged" for DELIVERED-status callers).
        Map<UUID, Boolean> falsificationReadyByPlannedItem = new HashMap<>();
        for (WishlistEntity plannedItem : codeProducingItems) {
            if (Boolean.TRUE.equals(fulfilledByPlannedItem.get(plannedItem.getId()))) {
                falsificationReadyByPlannedItem.put(plannedItem.getId(), true);
                continue;
            }
            List<TaskEntity> itemTasks = tasksByPlannedItem.getOrDefault(plannedItem.getId(), List.of());
            boolean ready = !itemTasks.isEmpty() && itemTasks.stream()
                    .allMatch(t -> isTerminalOrAwaitingDeadDependency(t, new java.util.HashSet<>()));
            falsificationReadyByPlannedItem.put(plannedItem.getId(), ready);
        }
        int falsificationReadyFeatures = (int) productFeatures.stream()
                .filter(feature -> {
                    List<WishlistEntity> featureItems = codeProducingItems.stream()
                            .filter(w -> feature.getId().equals(w.getFeatureId()))
                            .toList();
                    return !featureItems.isEmpty() && featureItems.stream()
                            .allMatch(w -> Boolean.TRUE.equals(falsificationReadyByPlannedItem.get(w.getId())));
                })
                .count();
        double selfFalsificationReadyRatio = totalFeatures == 0
                ? 1.0 : (double) falsificationReadyFeatures / totalFeatures;
        // 2026-07-26 operator directive ("считать по фичам, а не по таскам!", repeated - this metric had
        // drifted to deliverable-granularity): ratio() now reflects completeFeatures/totalFeatures, not
        // mergedDeliverables/totalDeliverables. totalDeliverables/mergedDeliverables stay in the record as
        // informational detail (still shown), they just no longer DRIVE ratio() or anything gated on it
        // (falsification/coverage-audit thresholds, her journal, the dashboard). A project with 5 real
        // features and 1 fully complete now genuinely reads 20%, not "5 of 19 individual work items merged"
        // - a client brief is delivered feature by feature, not item by item.
        double ratio = totalFeatures == 0 ? 1.0 : (double) completeFeatures / totalFeatures;
        if (total == 0) {
            // Every planned item for this scope is auxiliary (decision/spike/review work only) - there is
            // nothing this metric can measure, not "0% done". Report via decompositionComplete alone rather
            // than a misleading 0/0 ratio.
            return new Readiness(totalFeatures, completeFeatures, 0, 0, 1.0, decompositionComplete, 1.0);
        }
        return new Readiness(totalFeatures, completeFeatures, total, mergedCount, ratio, decompositionComplete,
                selfFalsificationReadyRatio);
    }

    /**
     * True when task is a genuinely terminal `failed` outcome, or is queued/blocked behind a
     * {@code dependsOn} chain that (transitively) bottoms out at one, with no live replacement anywhere in
     * the chain (isDependencySatisfied already searches for a same-semantic-key merged replacement at each
     * hop). Visited-set guards a pathological depends_on cycle from recursing forever; a cycle is real data
     * corruption, not a dead-end-credit signal, so it returns false (never silently grants readiness credit
     * for something this check cannot actually prove terminal).
     */
    private boolean isTerminalOrAwaitingDeadDependency(TaskEntity task, Set<UUID> visiting) {
        if (task == null) {
            return false;
        }
        if (task.getStatus() == TaskStatus.failed) {
            return true;
        }
        if (task.getStatus() != TaskStatus.queued && task.getStatus() != TaskStatus.blocked) {
            return false;
        }
        TaskEntity dependency = task.getDependsOn();
        if (dependency == null || !visiting.add(task.getId())) {
            return false;
        }
        if (isDependencySatisfied(dependency)) {
            // A real, live replacement exists (or the dependency itself resolved) - this task is simply
            // waiting its normal turn, not stuck behind a dead end.
            return false;
        }
        return isTerminalOrAwaitingDeadDependency(dependency, visiting);
    }

    /**
     * 2026-08-07 (OpsAuditorService orphaned-dependency-chain recovery): same walk and the same
     * isDependencySatisfied checks as isTerminalOrAwaitingDeadDependency above, but returns the actual
     * `failed` task at the bottom of the chain instead of a bare boolean - a caller that wants to create a
     * real replacement (not just know one is needed) has to know exactly which task's role/featureId/
     * semantic key to copy. Null whenever the chain is not actually dead-ended (a live replacement exists
     * somewhere in it, or the dependency isn't in a terminal-or-waiting state).
     *
     * @Transactional here only protects the RECURSIVE dependsOn walk this method itself performs - if the
     * caller already loaded {@code dependency} in a transaction that has since closed, the incoming
     * argument is a detached entity whose own lazy fields are tied to that closed session regardless of any
     * new transaction opened here (a proxy is bound to the session that created it, not to "whatever
     * transaction happens to be active"). Every real caller must load its own starting `dependency` and
     * call this method within the SAME transaction (see OpsAuditorService.gatherOrphanedDependencyChainEvidence).
     */
    @Transactional(readOnly = true)
    public TaskEntity findDeadDependencyRoot(TaskEntity dependency, Set<UUID> visiting) {
        if (dependency == null || !visiting.add(dependency.getId())) {
            return null;
        }
        if (isDependencySatisfied(dependency)) {
            return null;
        }
        if (dependency.getStatus() == TaskStatus.failed) {
            return dependency;
        }
        if (dependency.getStatus() != TaskStatus.queued && dependency.getStatus() != TaskStatus.blocked) {
            return null;
        }
        return findDeadDependencyRoot(dependency.getDependsOn(), visiting);
    }

    /**
     * Union-find `find` (engineering invariant #13, Kripke rigid designation), READ-ONLY: follows
     * canonicalFeatureId pointers to the root without writing anything back. Deliberately does not do
     * write-side path compression here - this is called from readOnly=true call paths (computeForProject,
     * isBuildPhase) where a write could silently no-op (Hibernate readOnly sessions may skip flushing) or,
     * worse, be rejected outright depending on the JDBC driver's readOnly enforcement. Path compression
     * (the actual write) happens once, deliberately, in reconcileDuplicateFeatureUnions's writable
     * transaction instead - by construction every stored canonicalFeatureId already points directly at its
     * root (see unionDuplicateFeature), so in practice this loop only ever takes 0 or 1 hops; the walk
     * exists purely as a defensive fallback, not a hot path.
     */
    public UUID resolveCanonical(UUID featureId) {
        if (featureId == null) {
            return null;
        }
        Set<UUID> visiting = new HashSet<>();
        UUID current = featureId;
        while (visiting.add(current)) {
            FeatureEntity node = featureRepository.findById(current).orElse(null);
            if (node == null || node.getCanonicalFeatureId() == null) {
                return current;
            }
            current = node.getCanonicalFeatureId();
        }
        return current; // cycle guard tripped (data corruption; union() never creates one) - stop, don't loop
    }

    /**
     * Union-find `union` (engineering invariant #13). Persists that loserId's real work now attributes to
     * winnerId's canonical id. Deliberately never touches the loser's wishlist rows or tasks - the loser's
     * real merged work is preserved and rolled up under the canonical id by every read consumer (see
     * DedupResult.canonicalOf below), never hidden the way the old ephemeral excludedIds list used to make
     * it. Rigid once set: if loserId already has a canonicalFeatureId, this is a no-op - the first union
     * decision for a given duplicate is permanent, never re-baptized by a later tie-break run. Callers must
     * hold a real writable transaction (see reconcileDuplicateFeatureUnions, its only caller) - this method
     * is intentionally NOT @Transactional itself, so it can never accidentally look like it opens its own
     * write transaction when self-invoked (this codebase's established Spring self-invocation gotcha).
     */
    public void unionDuplicateFeature(UUID loserId, UUID winnerId) {
        if (loserId == null || winnerId == null || loserId.equals(winnerId)) {
            return;
        }
        FeatureEntity loser = featureRepository.findById(loserId).orElse(null);
        if (loser == null || loser.getCanonicalFeatureId() != null) {
            return;
        }
        UUID canonicalWinner = resolveCanonical(winnerId);
        if (canonicalWinner.equals(loserId)) {
            return; // winner already (transitively) points back at loser - leave the existing union rigid
        }
        loser.setCanonicalFeatureId(canonicalWinner);
        featureRepository.save(loser);
        log.info("ClientDeliverableReadinessService: unioned duplicate epic '{}' ({}) into canonical epic {} - "
                        + "its real work now attributes to the canonical epic, never hidden",
                loser.getTitle(), loserId, canonicalWinner);
    }

    /**
     * Write-side reconciliation (engineering invariant #13): the one place a duplicate-epic group's winner
     * gets DECIDED and PERSISTED via unionDuplicateFeature. Called from deleteValuelessEpicsForProject,
     * which already runs inside deleteValuelessEpics's writable (non-readOnly) @Scheduled transaction - see
     * resolveCanonical's javadoc for why this can never safely happen from a readOnly=true call path.
     * Bounds the old "recomputed fresh on every call" flapping window to at most one epic-cleanup cron
     * cycle: a brand-new duplicate pair reads as an ephemeral (unioned) tie-break via deduplicateFeaturesByTitle
     * until this next runs, then becomes permanently rigid.
     */
    private void reconcileDuplicateFeatureUnions(List<FeatureEntity> features, List<WishlistEntity> allWishlist) {
        Map<UUID, Long> itemCountByFeatureId = productItemCountByFeatureId(allWishlist);
        for (List<FeatureEntity> group : groupByRootWishlistAndTitle(features).values()) {
            if (group.size() < 2) {
                continue;
            }
            FeatureEntity winner = pickWinner(group, itemCountByFeatureId);
            for (FeatureEntity f : group) {
                if (!f.getId().equals(winner.getId())) {
                    unionDuplicateFeature(f.getId(), winner.getId());
                }
            }
        }
    }

    private Map<UUID, Long> productItemCountByFeatureId(List<WishlistEntity> allWishlist) {
        return allWishlist.stream()
                .filter(w -> w.getFeatureId() != null)
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
                .filter(w -> PRODUCT_ITERATION_SOURCES.contains(w.getSource()))
                .collect(java.util.stream.Collectors.groupingBy(WishlistEntity::getFeatureId, java.util.stream.Collectors.counting()));
    }

    private Map<String, List<FeatureEntity>> groupByRootWishlistAndTitle(List<FeatureEntity> features) {
        return features.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        f -> f.getRootWishlistId() + "::" + f.getTitle(),
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    // 2026-08-03 (confirmed live incident, test-forty-first): counts only real, still-in-scope planned work
    // (compiled, not dismissed, product-sourced - same filter listEpicDiagnostics itself applies to
    // plannedItems), not raw wishlist-row count - see productItemCountByFeatureId's own history for why.
    private FeatureEntity pickWinner(List<FeatureEntity> group, Map<UUID, Long> itemCountByFeatureId) {
        // Rigid designation: a row already carrying a canonicalFeatureId already lost a PERSISTED union
        // decision - never reconsider it. Only still-canonical rows compete for the tie-break, so a
        // brand-new third duplicate showing up later gets folded into the already-established winner
        // instead of reopening a question that was already answered.
        List<FeatureEntity> stillCanonical = group.stream().filter(f -> f.getCanonicalFeatureId() == null).toList();
        List<FeatureEntity> candidates = stillCanonical.isEmpty() ? group : stillCanonical;
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates.stream()
                .max(java.util.Comparator
                        .<FeatureEntity>comparingLong(f -> itemCountByFeatureId.getOrDefault(f.getId(), 0L))
                        .thenComparing(FeatureEntity::getCreatedAt, java.util.Comparator.reverseOrder()))
                .orElseThrow();
    }

    /** @see #deduplicateFeaturesByTitle */
    private record DedupResult(List<FeatureEntity> winners, Map<UUID, UUID> canonicalOf) {
        UUID canonicalOrSelf(UUID featureId) {
            return featureId == null ? null : canonicalOf.getOrDefault(featureId, featureId);
        }
    }

    /**
     * Dedup fix (2026-07-24, live incident confirmed via the new /epics verification endpoint), rebuilt
     * around persisted union-find canonicalization (2026-08-07, engineering invariant #13) after a live
     * follow-on incident showed the original ephemeral version was itself part of the same bug class: the
     * same PessimisticLockingFailureException retry storm that left orphaned FeatureEntity rows (see the
     * filter above) ALSO left non-orphaned duplicates. Grouped by (rootWishlistId, title) - a real
     * semantic-duplicate signal for this specific incident shape, not a general content-similarity matcher
     * (that's SelfFalsificationEpicMatcher's job: deciding whether a NEW epic matches an EXISTING one at
     * decomposition time, not cleaning up already-created duplicates).
     *
     * READ-ONLY: honors any already-persisted canonicalFeatureId (set by reconcileDuplicateFeatureUnions)
     * as rigid and never overrides it; for a group with no persisted decision yet, computes the same
     * winner reconcileDuplicateFeatureUnions would, WITHOUT writing it (see resolveCanonical's javadoc for
     * why a write here would be unsafe) - so a fresh duplicate still reads sensibly immediately, and
     * becomes permanently rigid once the next cleanup cycle persists it. Returns both the winner list
     * (`productFeatures`, unchanged contract for existing callers) and a canonicalOf map so a caller can
     * roll a loser's real wishlist items/tasks up under the winner's id instead of losing them the way the
     * old excludedIds-only version did (Frege substitutivity: querying "is this epic's work done" via
     * either the winner's or a loser's id must agree).
     */
    private DedupResult deduplicateFeaturesByTitle(List<FeatureEntity> features, List<WishlistEntity> allWishlist) {
        Map<UUID, Long> itemCountByFeatureId = productItemCountByFeatureId(allWishlist);
        Map<UUID, UUID> canonicalOf = new HashMap<>();
        List<FeatureEntity> winners = new java.util.ArrayList<>();
        for (List<FeatureEntity> group : groupByRootWishlistAndTitle(features).values()) {
            if (group.size() == 1) {
                winners.add(group.get(0));
                continue;
            }
            FeatureEntity winner = pickWinner(group, itemCountByFeatureId);
            winners.add(winner);
            for (FeatureEntity f : group) {
                if (!f.getId().equals(winner.getId())) {
                    canonicalOf.put(f.getId(), winner.getId());
                }
            }
        }
        return new DedupResult(winners, canonicalOf);
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

        List<FeatureEntity> sourceMatchedFeatures = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && PRODUCT_ITERATION_SOURCES.contains(root.getSource());
                })
                .toList();
        List<FeatureEntity> nonOrphanFeatures = sourceMatchedFeatures.stream()
                .filter(feature -> featureIdsWithWishlistWork.contains(feature.getId()))
                .toList();
        DedupResult dedup = deduplicateFeaturesByTitle(nonOrphanFeatures, allWishlist);
        List<FeatureEntity> productFeatures = dedup.winners();
        Set<UUID> productFeatureIds = productFeatures.stream().map(FeatureEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Same dismissed-exclusion fix as computeForSources (2026-07-24) - kept in sync since this method
        // deliberately mirrors that one's filtering instead of calling it (see class javadoc above).
        List<WishlistEntity> plannedItems = allWishlist.stream()
                .filter(w -> w.getCompiledByRole() != null)
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
                .filter(w -> PRODUCT_ITERATION_SOURCES.contains(w.getSource()))
                .filter(w -> w.getFeatureId() != null && productFeatureIds.contains(dedup.canonicalOrSelf(w.getFeatureId())))
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

    public record UiCodeEpic(UUID featureId, String title) {}

    /**
     * FALSIFICATION-stage design eligibility (2026-08-04, Phase B of the design/QA acceptance redesign):
     * an epic whose real UI code has already merged to main - the OPPOSITE condition from
     * hasRequiredMergeEvidence's BARCAN-TAG-03 build-time exemption above, which deliberately accepts
     * hasCode=false design drafts. This requires hasCode==true specifically, read directly from
     * mergedReviews, because Stitch's design-system application (DesignSystemFalsificationService) is
     * meant to run AFTER real shipped UI exists, not against a speculative build-time mockup. Reuses
     * DesignExcellenceGate.UI_TAGS for the role set instead of redefining TAG-03/TAG-11 a third time.
     * Deliberately re-derives its own productFeatures scope rather than sharing computeForSources'/
     * listEpicDiagnostics' scaffolding - same established precedent as listEpicDiagnostics' own javadoc:
     * independent read paths for different questions, not merged behind a harder-to-reason-about helper.
     */
    public List<UiCodeEpic> listEpicsWithMergedUiCode(UUID projectId) {
        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        Map<UUID, WishlistEntity> wishlistById = new HashMap<>();
        for (WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }
        Set<UUID> featureIdsWithWishlistWork = allWishlist.stream()
                .map(WishlistEntity::getFeatureId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<FeatureEntity> sourceMatchedFeatures = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && PRODUCT_ITERATION_SOURCES.contains(root.getSource());
                })
                .toList();
        List<FeatureEntity> nonOrphanFeatures = sourceMatchedFeatures.stream()
                .filter(feature -> featureIdsWithWishlistWork.contains(feature.getId()))
                .toList();
        DedupResult dedup = deduplicateFeaturesByTitle(nonOrphanFeatures, allWishlist);
        List<FeatureEntity> productFeatures = dedup.winners();

        // Frege substitutivity (engineering invariant #13): TaskEntity.featureId is a one-time snapshot
        // (never rewritten after task creation - see JulesDispatchService/TaskEntity history), so a task
        // created while its epic was still a not-yet-unioned "losing" duplicate carries the LOSER's id
        // forever, not the winner's. Querying tasks by feature.getId() alone would miss any such task's
        // real merged UI code entirely. Roll every group member's tasks up under the winner here, the same
        // way computeForSources rolls wishlist items up via dedup.canonicalOrSelf.
        Map<UUID, List<UUID>> memberIdsByCanonical = new HashMap<>();
        for (FeatureEntity f : nonOrphanFeatures) {
            memberIdsByCanonical.computeIfAbsent(dedup.canonicalOrSelf(f.getId()), k -> new java.util.ArrayList<>())
                    .add(f.getId());
        }

        List<UiCodeEpic> result = new java.util.ArrayList<>();
        for (FeatureEntity feature : productFeatures) {
            List<UUID> memberIds = memberIdsByCanonical.getOrDefault(feature.getId(), List.of(feature.getId()));
            boolean hasMergedUiCode = memberIds.stream()
                    .flatMap(id -> taskRepository.findByFeatureId(id).stream())
                    .filter(t -> t.getRole() != null
                            && com.eneik.production.services.gate.DesignExcellenceGate.UI_TAGS.contains(t.getRole().getTag()))
                    .filter(this::reachedMain)
                    .anyMatch(t -> mergedReviews(t.getId()).stream().anyMatch(r -> Boolean.TRUE.equals(r.getHasCode())));
            if (hasMergedUiCode) {
                result.add(new UiCodeEpic(feature.getId(), feature.getTitle()));
            }
        }
        return result;
    }

    /**
     * Deletes epics with zero real code value (2026-07-25, operator directive: "эпик - это реальная фича с
     * jtbd для пользователей - там не может не быть реальной ценности. ценность - это код"). An epic ends
     * up here two ways: its only wishlist item(s) were dismissed (abandoned scope, never attempted), or
     * every task its wishlist item(s) produced was auxiliary (a DECISION-stage/complex-cynefin item that
     * structurally never produces mergeable code - see isAuxiliaryPlannedItem). Both leave
     * codeProducingItemCount permanently at 0, and per listEpicDiagnostics's own completion formula
     * ({@code !featureItems.isEmpty()}), such an epic can never become "complete" - it would otherwise sit
     * forever dragging down completeFeatures/totalFeatures with no way to resolve itself (confirmed live,
     * 2026-07-25: two such epics, one from a dismissed wishlist and one whose only task was a resolved
     * DECISION-stage record, both stuck at 0/9 forever until manually found and deleted).
     * Deliberately conservative: only deletes an epic when NOTHING is still pending/compiling for it (a
     * brand-new epic with items still moving through the compiler is left alone), and only once it has
     * existed for a while (see EPIC_CLEANUP_MIN_AGE_MINUTES) - never race a wishlist-compile transaction
     * that just created the epic and hasn't linked its items to it yet.
     */
    private static final long EPIC_CLEANUP_MIN_AGE_MINUTES = 10;

    @Scheduled(cron = "${epic-cleanup.cron:0 20 * * * ?}")
    @Transactional
    public void deleteValuelessEpics() {
        List<com.eneik.production.models.persistence.ProjectEntity> activeProjects = projectRepository
                .findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
        for (com.eneik.production.models.persistence.ProjectEntity project : activeProjects) {
            LogScope.project(project.getId());
            try {
                deleteValuelessEpicsForProject(project.getId());
            } catch (Exception e) {
                log.error("ClientDeliverableReadinessService: valueless-epic cleanup failed for project {}: {}",
                        project.getId(), e.getMessage(), e);
            } finally {
                LogScope.clear();
            }
        }
    }

    // Live incident, 2026-08-07 (test-forty-third): this cron fired 5x during a ~4.5h project-wide
    // BLOCKED_BY_DUPLICATE_CONTENT freeze, when dispatch itself was denied for every task in the project.
    // "Zero code-producing items yet" is meaningless evidence of valuelessness while nothing can dispatch
    // at all - it dismissed real epics whose tasks simply hadn't been allowed to start. Once dispatch
    // resumed, nothing re-validated those dismissals, so real work went on to complete under them anyway,
    // permanently invisible to /epics, /tree, and productReadiness (dismissedAt-filtered everywhere). Skip
    // this project's cleanup entirely for the cycle when dispatch is currently blocked project-wide -
    // reuses the exact same gate real dispatch already obeys (ProjectFlowService.dispatchQueuedTasks),
    // not a separate ad hoc condition that could drift out of sync with it.
    private void deleteValuelessEpicsForProject(UUID projectId) {
        if (!operationalPolicyService.authorize(projectId, com.eneik.production.services.operational.OperationalAction.DISPATCH_QUEUED_TASKS).allowed()) {
            log.info("ClientDeliverableReadinessService: skipping valueless-epic cleanup for project {} - "
                    + "dispatch is currently blocked project-wide, zero code-producing items is not real evidence right now",
                    projectId);
            return;
        }
        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        Map<UUID, List<WishlistEntity>> wishlistByFeature = allWishlist.stream()
                .filter(w -> w.getFeatureId() != null)
                .collect(java.util.stream.Collectors.groupingBy(WishlistEntity::getFeatureId));

        // Engineering invariant #13 (rigid designation): the one place a duplicate-epic group's winner gets
        // permanently decided and persisted - see reconcileDuplicateFeatureUnions's own javadoc for why it
        // can only safely run here (a real writable transaction), not from any of the readOnly=true call
        // paths that also compute a dedup winner ephemerally for that one read.
        Map<UUID, WishlistEntity> wishlistById = new HashMap<>();
        for (WishlistEntity item : allWishlist) {
            wishlistById.put(item.getId(), item);
        }
        Set<UUID> featureIdsWithWishlistWork = allWishlist.stream()
                .map(WishlistEntity::getFeatureId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        List<FeatureEntity> sourceMatchedForUnion = featureRepository.findByProjectIdAndDismissedAtIsNull(projectId).stream()
                .filter(feature -> {
                    WishlistEntity root = wishlistById.get(feature.getRootWishlistId());
                    return root != null && PRODUCT_ITERATION_SOURCES.contains(root.getSource());
                })
                .filter(feature -> featureIdsWithWishlistWork.contains(feature.getId()))
                .toList();
        reconcileDuplicateFeatureUnions(sourceMatchedForUnion, allWishlist);

        java.time.Instant now = java.time.Instant.now();
        for (EpicDiagnostic diagnostic : listEpicDiagnostics(projectId)) {
            if (diagnostic.codeProducingItemCount() > 0) {
                continue; // has real value - never touch
            }
            if (diagnostic.createdAt() != null
                    && java.time.Duration.between(diagnostic.createdAt(), now).toMinutes() < EPIC_CLEANUP_MIN_AGE_MINUTES) {
                continue; // too new to judge - give the compiler a chance to still link real work to it
            }
            List<WishlistEntity> ownItems = wishlistByFeature.getOrDefault(diagnostic.id(), List.of());
            boolean stillActive = ownItems.stream()
                    .anyMatch(w -> w.getStatus() == WishlistStatus.pending || w.getStatus() == WishlistStatus.compiling);
            if (stillActive) {
                continue; // might still produce real code - not resolved yet
            }
            featureThreadRepository.findByProjectIdAndFeatureId(projectId, diagnostic.id())
                    .ifPresent(featureThreadRepository::delete);
            // 2026-08-04 (3-layer model): soft-delete, not featureRepository.deleteById - a hard delete
            // destroyed originFeatureId lineage entirely (confirmed live earlier today: manual SQL repair
            // was needed after this exact call wiped 3 real features during a dedup tie-break incident).
            // The row survives with its lineage intact for Layer 2 "Delivery" history; it just stops
            // counting toward active readiness via the dismissedAt-filtered queries above.
            featureRepository.findById(diagnostic.id()).ifPresent(feature -> {
                feature.setDismissedAt(now);
                featureRepository.save(feature);
            });
            log.info("ClientDeliverableReadinessService: dismissed valueless epic '{}' ({}) for project {} - "
                            + "zero code-producing items, nothing left pending for it (soft-deleted, lineage preserved)",
                    diagnostic.title(), diagnostic.id(), projectId);
        }
    }

    /**
     * Self-healing counterpart to {@link #deleteValuelessEpicsForProject}, called from
     * {@code JulesDispatchService.dispatch} at the single choke point every dispatch path funnels through,
     * right when a task is actually about to start real work. Live incident, 2026-08-07 (test-forty-third):
     * the cleanup cron dismissed epics during a project-wide dispatch freeze, using a zero-code-producing-
     * items snapshot that later proved wrong once dispatch resumed and real tasks under those same epics
     * went on to complete - permanently invisible everywhere that filters on dismissedAt, since nothing
     * ever re-checked. A task dispatching under a dismissed feature is itself the proof the earlier
     * "valueless" judgment no longer holds (or never held) - reversing it here means the epic reappears the
     * moment real work resumes, not only after everything eventually merges. This is a general safety net,
     * independent of the specific freeze-window cause deleteValuelessEpicsForProject now also guards
     * against directly - defense in depth against any other path that could produce the same false dismissal.
     */
    @Transactional
    public void unDismissFeatureIfNeeded(UUID featureId) {
        if (featureId == null) {
            return;
        }
        featureRepository.findById(featureId).ifPresent(feature -> {
            if (feature.getDismissedAt() != null) {
                feature.setDismissedAt(null);
                featureRepository.save(feature);
                log.info("ClientDeliverableReadinessService: un-dismissed epic '{}' ({}) - real work is "
                                + "dispatching under it now, so the earlier 'valueless' judgment no longer holds",
                        feature.getTitle(), featureId);
            }
        });
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

    // Public (2026-07-25): the dashboard's blocked-items widget needs this exact exclusion too - a
    // DECISION-stage or "complex"-cynefin task is never expected to reach main on its own (that's the
    // whole reason it's excluded from this class's own deliverable-ratio counting), so flagging one as
    // "done but not merged" would be a false positive, not a real blocker.
    public boolean isAuxiliaryTask(TaskEntity task) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : null;
        if (EmsFlowStage.forRoleTag(roleTag) == EmsFlowStage.DECISION) {
            return true;
        }
        return "complex".equals(task.getCynefinDomain());
    }

    // Roles whose build-time deliverable is real, legitimate work that is not source code by design -
    // reachedMain alone is sufficient evidence; a mergedReviews().hasCode()==false is EXPECTED for these,
    // not a sign anything went wrong. BARCAN-TAG-09 = delivery/decision record. BARCAN-TAG-03 (2026-08-04
    // live incident, "Core Knowledge Base Portal" epic stuck at 6/11): its build-time deliverable is a
    // static design mockup (image+HTML) committed under design/draft/... via DesignAssetService -
    // CodeChangeClassifier correctly never counts that as code. Deliberately narrower than widening
    // isAuxiliaryTask/EmsFlowStage.EXPERIENCE: BARCAN-TAG-11 shares TAG-03's EXPERIENCE stage but writes
    // real frontend code and must keep requiring hasCode=true, and isAuxiliaryTask has two external
    // callers (ProjectFlowService, JulesDispatchService) whose behavior must not change for TAG-03/TAG-11.
    // Design's REAL completion signal now lives later, at the falsification stage, once real UI has
    // actually merged - see DesignSystemFalsificationService; this exemption only covers the build-time
    // draft, which was never meant to be the final acceptance moment.
    // BARCAN-TAG-03 stays a separate, explicit exemption (not folded into isSpecStage below) - its reason
    // is genuinely different: a design mockup (image+HTML), not a reference document nobody merges code
    // from. Every OTHER exemption (2026-08-08, confirmed live incident, test-forty-third: "LMS and
    // Messenger Integrations" epic stuck at 3/5 despite every real planned item's PR being genuinely
    // merged, root-caused to BARCAN-TAG-12/API Contract's PR containing only
    // docs/contracts/Integrations.openapi.yaml - a spec document CodeChangeClassifier correctly never
    // counts as code) now derives from EmsFlowStage.isSpecStage instead of a second, hand-maintained tag
    // list that can silently drift from it - a role newly marked specOnly=true (ARCHITECTURE, COMPLIANCE
    // already are; any future one) is exempted automatically, closing the whole class instead of the one
    // instance found tonight.
    private static final Set<String> HAS_CODE_EXEMPT_ROLE_TAGS = Set.of("BARCAN-TAG-03");

    private boolean hasRequiredMergeEvidence(TaskEntity task) {
        if (!reachedMain(task)) {
            return false;
        }
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        if (HAS_CODE_EXEMPT_ROLE_TAGS.contains(roleTag) || EmsFlowStage.isSpecStage(roleTag)) {
            return true;
        }
        boolean hasCode = mergedReviews(task.getId()).stream().anyMatch(review -> Boolean.TRUE.equals(review.getHasCode()));
        // Phase C (2026-08-04, design/QA acceptance redesign): QA (BARCAN-TAG-06) is Delivery-layer work
        // (verifying the product), not Product-layer work (producing it) - a real, correct QA outcome can
        // legitimately merge a PR with ZERO file changes (nothing new to test), which CodeChangeClassifier
        // always classifies as hasCode=false. QA gets an ADDITIONAL acceptance path - real verification
        // evidence via VerificationEvidenceGate - on top of the normal hasCode path, not INSTEAD of it.
        // Live regression (2026-08-04, caught immediately after deploy): an earlier version of this branch
        // used `return hasPassingGateCheck(...)` here, which replaced the hasCode check entirely for every
        // TAG-06 task - silently un-counting every pre-existing QA task that legitimately touched real code
        // (hasCode=true) but predates VerificationEvidenceGate and so has no verification_evidence entry
        // yet. Confirmed live: two previously-100%-complete epics ("LMS and Messenger Integration",
        // "Financial and HR Configuration in Moodle") dropped from 3/3 and 5/5 to 2/3 and 2/5 the moment
        // this deployed. A QA task must pass via EITHER real code evidence OR real verification evidence,
        // never lose the path it already had.
        if (hasCode) {
            return true;
        }
        if (com.eneik.production.services.gate.VerificationEvidenceGate.QA_TAGS.contains(roleTag)) {
            return hasPassingGateCheck(task, com.eneik.production.services.gate.VerificationEvidenceGate.CHECK_NAME);
        }
        return false;
    }

    // Deliberately reads the SPECIFIC check's own passed flag from the checks array, not
    // task.getQualityGatePassed() (the aggregate across every gate, e.g. BusinessValueGate/DoDGate) - a QA
    // task must not fail readiness because of an unrelated gate's formatting nit.
    private boolean hasPassingGateCheck(TaskEntity task, String checkName) {
        com.fasterxml.jackson.databind.JsonNode report = task.getQualityGateReport();
        if (report == null || !report.has("checks")) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode check : report.get("checks")) {
            if (checkName.equals(check.path("name").asText(""))) {
                return check.path("passed").asBoolean(false);
            }
        }
        return false;
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
     * Feature-thread closeout gate (2026-07-24, relaxed 2026-07-26) - see
     * AutoMergeService.closeOutReadyFeatureThreads. Originally required EVERY task under a feature to reach
     * a terminal status before folding ANY of the thread's already-merged work into main - confirmed live
     * (test-thirty-eighth) this could hold a feature's genuinely-done work hostage indefinitely behind
     * ordinary siblings still in review, keeping completeFeatures at 0 for hours despite real progress.
     * Operator directive: "убрать блокировку закрытия" - a review/pending_review sibling has not yet added
     * its own commits to the thread branch at all (see classifyAndHandleBranch), so there is nothing of
     * theirs to wait for; closing out now just means "fold what's actually on the branch", not "declare the
     * whole feature finished". `blocked`/`failed` still count as terminal (must never hold up real work),
     * and a feature with real merged work is now ready as soon as that work exists, regardless of siblings.
     *
     * This can never be a provably final fact (the compiler can re-attach a brand-new root wishlist to an
     * existing feature at any later time - see FeatureService.findExistingEpic) - that's fine, a reopened
     * feature resets its thread's closeout state on its next has-code merge (see
     * AutoMergeService.classifyAndHandleBranch), so an early closeout is self-correcting, not a permanent
     * mistake - confirmed unconditional in that method (mergedToMainAt/closeoutPrUrl reset on every single
     * has-code merge, whether or not the thread had already closed out once before).
     */
    public boolean isFeatureReadyForCloseout(UUID projectId, UUID featureId) {
        List<TaskEntity> featureTasks = taskRepository.findByFeatureId(featureId);
        if (featureTasks.isEmpty()) {
            return false;
        }
        boolean anyTerminalTaskHasRealMergedWork = featureTasks.stream()
                .filter(t -> switch (t.getStatus()) {
                    case done, failed, spike_completed, blocked -> true;
                    default -> false;
                })
                .anyMatch(t -> mergedReviews(t.getId()).stream().anyMatch(r -> Boolean.TRUE.equals(r.getHasCode())));
        if (!anyTerminalTaskHasRealMergedWork) {
            return false;
        }
        return wishlistRepository.findByFeatureId(featureId).stream()
                .noneMatch(w -> w.getStatus() == WishlistStatus.pending || w.getStatus() == WishlistStatus.compiling);
    }
}
