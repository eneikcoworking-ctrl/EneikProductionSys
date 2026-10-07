package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for ClientDeliverableReadinessService.
 * Validates epistemic and architectural delivery-witness invariants:
 * - D002 Austin Category Error Scan: TaskStatus.done != delivery evidence; finished factory task cannot substitute for merge-to-main with real code.
 * - D008 Popper Falsification Harness: A client requirement cannot be fulfilled by an unrelated merged task in the same epic; closure requires strict item/repair lineage.
 * - D012 Popper Truth Status Table: Feature-level readiness ratio (completeFeatures / totalFeatures) distinguishes fully delivered features from partial items, zero-deliverable auxiliary scopes, and uncompiled roots.
 */
class ClientDeliverableReadinessServiceFalsificationTest {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
    private final FeatureThreadRepository featureThreadRepository = mock(FeatureThreadRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final OperationalPolicyService operationalPolicyService = mock(OperationalPolicyService.class);

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            wishlistRepository, featureRepository, taskRepository, julesSessionRepository, prReviewRepository,
            featureThreadRepository, projectRepository, operationalPolicyService);

    @BeforeEach
    void setUp() {
        when(operationalPolicyService.authorize(any(UUID.class), eq(OperationalAction.DISPATCH_QUEUED_TASKS)))
                .thenReturn(new OperationalPolicyService.OperationalDecision(
                        null, OperationalAction.DISPATCH_QUEUED_TASKS,
                        true, "ACTIVE", "authorized", "allowed", List.of(), null));
    }

    @Test
    @DisplayName("D002 Austin Category Error: Task done status does NOT grant delivery readiness without merge-to-main code evidence")
    void falsifyAustinCategoryError_taskDoneWithoutMergeEvidenceIsNotDelivered() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        FeatureEntity feature = createFeature(projectId, featureId, rootId);
        WishlistEntity plannedItem = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "DEVELOPER", WishlistSource.client);

        TaskEntity task = createTask(projectId, featureId, plannedItem.getId(), "BARCAN-TAG-02", TaskStatus.done);

        stubEnvironment(projectId, List.of(root), List.of(feature), List.of(plannedItem), List.of(task));

        // Case 1: Task is done, but has NO sessions or reviews at all -> reachedMain = false -> unfulfilled
        ClientDeliverableReadinessService.Readiness readiness1 = service.computeForProject(projectId);
        assertThat(readiness1.mergedDeliverables()).isEqualTo(0);
        assertThat(readiness1.completeFeatures()).isEqualTo(0);
        assertThat(readiness1.ratio()).isEqualTo(0.0);
        assertThat(service.hasRequiredMergeEvidence(task)).isFalse();

        // Case 2: Task has a merged PR, but merged review has hasCode = false for a code-requiring role (TAG-02)
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        PrReviewEntity noCodeReview = new PrReviewEntity();
        noCodeReview.setJulesSessionId(session.getId());
        noCodeReview.setMerged(true);
        noCodeReview.setHasCode(false);
        noCodeReview.setBaseRef("main");
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(noCodeReview));

        ClientDeliverableReadinessService.Readiness readiness2 = service.computeForProject(projectId);
        assertThat(readiness2.mergedDeliverables()).isEqualTo(0);
        assertThat(readiness2.completeFeatures()).isEqualTo(0);
        assertThat(service.hasRequiredMergeEvidence(task)).isFalse();
    }

    @Test
    @DisplayName("D002 Austin Category Error: Merged PR sitting in unmerged feature-thread branch does not satisfy reachedMain")
    void falsifyAustinCategoryError_unmergedFeatureThreadDoesNotReachMain() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        FeatureEntity feature = createFeature(projectId, featureId, rootId);
        WishlistEntity plannedItem = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "DEVELOPER", WishlistSource.client);

        TaskEntity task = createTask(projectId, featureId, plannedItem.getId(), "BARCAN-TAG-02", TaskStatus.done);
        stubEnvironment(projectId, List.of(root), List.of(feature), List.of(plannedItem), List.of(task));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        PrReviewEntity branchMergedReview = new PrReviewEntity();
        branchMergedReview.setJulesSessionId(session.getId());
        branchMergedReview.setMerged(true);
        branchMergedReview.setHasCode(true);
        branchMergedReview.setBaseRef("feat/thread-feature-branch");
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(branchMergedReview));

        // FeatureThreadEntity exists but mergedToMainAt is null (branch not yet folded to main)
        FeatureThreadEntity thread = new FeatureThreadEntity();
        thread.setProjectId(projectId);
        thread.setFeatureId(featureId);
        thread.setMergedToMainAt(null);
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, featureId))
                .thenReturn(Optional.of(thread));

        assertThat(service.reachedMain(task)).isFalse();
        assertThat(service.hasRequiredMergeEvidence(task)).isFalse();
        assertThat(service.computeForProject(projectId).mergedDeliverables()).isEqualTo(0);
    }

    @Test
    @DisplayName("D008 Popper Falsification: A planned item cannot be fulfilled by an unrelated merged task in the same epic")
    void falsifyPopperFalsificationHarness_unrelatedMergedTaskInSameEpicCannotFulfillPlannedItem() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        FeatureEntity feature = createFeature(projectId, featureId, rootId);

        // Two distinct planned items in the same epic
        WishlistEntity item1 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "DEVELOPER", WishlistSource.client);
        WishlistEntity item2 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "DEVELOPER", WishlistSource.client);

        // Item 1 has a merged task with valid code
        TaskEntity task1 = createTask(projectId, featureId, item1.getId(), "BARCAN-TAG-02", TaskStatus.done);
        // Item 2 has a task that is NOT merged
        TaskEntity task2 = createTask(projectId, featureId, item2.getId(), "BARCAN-TAG-02", TaskStatus.in_progress);

        stubEnvironment(projectId, List.of(root), List.of(feature), List.of(item1, item2), List.of(task1, task2));
        stubTaskMergedToMainWithCode(task1);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        // Verification: Only item1 is fulfilled; item2 is NOT fulfilled by task1 despite being in the same feature
        assertThat(readiness.totalDeliverables()).isEqualTo(2);
        assertThat(readiness.mergedDeliverables()).isEqualTo(1);
        assertThat(readiness.totalFeatures()).isEqualTo(1);
        assertThat(readiness.completeFeatures()).isEqualTo(0); // Epic is incomplete because item2 is open!
        assertThat(readiness.ratio()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("D008 Popper Falsification: Repair closure strictly fulfills original planned item across repair chain")
    void falsifyPopperFalsificationHarness_repairClosureFulfillsFailedPlannedItem() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        FeatureEntity feature = createFeature(projectId, featureId, rootId);
        WishlistEntity plannedItem = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "DEVELOPER", WishlistSource.client);

        // Attempt 1: Failed task
        TaskEntity failedAttempt = createTask(projectId, featureId, plannedItem.getId(), "BARCAN-TAG-02", TaskStatus.failed);

        // Repair wishlist linked to failed attempt (source is delivery_never_reached_main, not PRODUCT_ITERATION_SOURCES)
        UUID repairWishlistId = UUID.randomUUID();
        WishlistEntity repairWishlist = createWishlist(projectId, repairWishlistId, WishlistStatus.converted_to_task, featureId, "BARCAN-TAG-00", WishlistSource.delivery_never_reached_main);
        repairWishlist.setSourceTaskId(failedAttempt.getId());

        // Attempt 2: Task spawned from repair wishlist, successfully merged to main
        TaskEntity repairTask = createTask(projectId, featureId, repairWishlistId, "BARCAN-TAG-02", TaskStatus.done);

        stubEnvironment(projectId, List.of(root, repairWishlist), List.of(feature), List.of(plannedItem), List.of(failedAttempt));
        when(taskRepository.findBySourceWishlistIdIn(List.of(repairWishlistId))).thenReturn(List.of(repairTask));
        stubTaskMergedToMainWithCode(repairTask);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        // Verification: Only the client plannedItem is in the denominator (totalDeliverables = 1); it is fulfilled via repair closure
        assertThat(readiness.totalDeliverables()).isEqualTo(1);
        assertThat(readiness.mergedDeliverables()).isEqualTo(1);
        assertThat(readiness.completeFeatures()).isEqualTo(1);
        assertThat(readiness.ratio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: Feature ratio is feature-based and distinguishes full delivery from partial progress")
    void falsifyPopperTruthStatusTable_featureRatioDistinguishesFullDeliveryFromPartialProgress() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        UUID feature1Id = UUID.randomUUID();
        UUID feature2Id = UUID.randomUUID();
        FeatureEntity feature1 = createFeature(projectId, feature1Id, rootId);
        FeatureEntity feature2 = createFeature(projectId, feature2Id, rootId);

        // Feature 1 has 2 items; Feature 2 has 2 items
        WishlistEntity f1Item1 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, feature1Id, "DEVELOPER", WishlistSource.client);
        WishlistEntity f1Item2 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, feature1Id, "DEVELOPER", WishlistSource.client);
        WishlistEntity f2Item1 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, feature2Id, "DEVELOPER", WishlistSource.client);
        WishlistEntity f2Item2 = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, feature2Id, "DEVELOPER", WishlistSource.client);

        TaskEntity t1 = createTask(projectId, feature1Id, f1Item1.getId(), "BARCAN-TAG-02", TaskStatus.done);
        TaskEntity t2 = createTask(projectId, feature1Id, f1Item2.getId(), "BARCAN-TAG-02", TaskStatus.done);
        TaskEntity t3 = createTask(projectId, feature2Id, f2Item1.getId(), "BARCAN-TAG-02", TaskStatus.done);
        TaskEntity t4 = createTask(projectId, feature2Id, f2Item2.getId(), "BARCAN-TAG-02", TaskStatus.in_progress);

        stubEnvironment(projectId, List.of(root), List.of(feature1, feature2),
                List.of(f1Item1, f1Item2, f2Item1, f2Item2), List.of(t1, t2, t3, t4));

        stubTaskMergedToMainWithCode(t1);
        stubTaskMergedToMainWithCode(t2);
        stubTaskMergedToMainWithCode(t3); // Feature 2 only has 1 of 2 merged

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        // Invariant: 3 of 4 deliverables are merged, BUT ratio must strictly equal 1/2 = 0.5 (feature value ratio)
        assertThat(readiness.totalDeliverables()).isEqualTo(4);
        assertThat(readiness.mergedDeliverables()).isEqualTo(3);
        assertThat(readiness.totalFeatures()).isEqualTo(2);
        assertThat(readiness.completeFeatures()).isEqualTo(1);
        assertThat(readiness.ratio()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: Auxiliary-only scope returns ratio 1.0 without misleading deliverable counts")
    void falsifyPopperTruthStatusTable_auxiliaryOnlyScopeReportsDecompositionWithoutZeroDenominatorError() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();

        WishlistEntity root = createWishlist(projectId, rootId, WishlistStatus.converted_to_task, null, null, WishlistSource.client);
        FeatureEntity feature = createFeature(projectId, featureId, rootId);

        // A decision/spike/review planned item with DECISION role (BARCAN-TAG-09) or complex Cynefin domain
        WishlistEntity spikeItem = createWishlist(projectId, UUID.randomUUID(), WishlistStatus.converted_to_task, featureId, "ARCHITECT", WishlistSource.client);
        TaskEntity spikeTask = createTask(projectId, featureId, spikeItem.getId(), "BARCAN-TAG-09", TaskStatus.spike_completed);

        stubEnvironment(projectId, List.of(root), List.of(feature), List.of(spikeItem), List.of(spikeTask));

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        // Auxiliary tasks are excluded from code-bearing deliverables; total deliverables = 0
        assertThat(readiness.totalDeliverables()).isEqualTo(0);
        assertThat(readiness.mergedDeliverables()).isEqualTo(0);
        assertThat(readiness.decompositionComplete()).isTrue();
        assertThat(readiness.ratio()).isEqualTo(1.0);
    }

    // Helper stubs
    private WishlistEntity createWishlist(UUID projectId, UUID id, WishlistStatus status, UUID featureId, String compiledByRole, WishlistSource source) {
        WishlistEntity w = new WishlistEntity();
        w.setId(id);
        w.setProjectId(projectId);
        w.setStatus(status);
        w.setSource(source);
        w.setFeatureId(featureId);
        w.setCompiledByRole(compiledByRole);
        return w;
    }

    private FeatureEntity createFeature(UUID projectId, UUID id, UUID rootWishlistId) {
        FeatureEntity f = new FeatureEntity();
        f.setId(id);
        f.setProjectId(projectId);
        f.setRootWishlistId(rootWishlistId);
        f.setTitle("Feature-" + id);
        return f;
    }

    private TaskEntity createTask(UUID projectId, UUID featureId, UUID sourceWishlistId, String roleTag, TaskStatus status) {
        TaskEntity t = new TaskEntity();
        t.setId(UUID.randomUUID());
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        t.setProject(project);
        t.setFeatureId(featureId);
        t.setSourceWishlistId(sourceWishlistId);
        t.setStatus(status);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        t.setRole(role);
        return t;
    }

    private void stubEnvironment(UUID projectId, List<WishlistEntity> wishlists, List<FeatureEntity> features,
                                 List<WishlistEntity> plannedItems, List<TaskEntity> tasks) {
        List<WishlistEntity> allWishlists = new ArrayList<>(wishlists);
        plannedItems.forEach(pi -> {
            if (!allWishlists.contains(pi)) allWishlists.add(pi);
        });

        when(wishlistRepository.findByProjectId(projectId)).thenReturn(allWishlists);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(features);
        features.forEach(f -> when(featureRepository.findById(f.getId())).thenReturn(Optional.of(f)));

        List<UUID> plannedItemIds = plannedItems.stream().map(WishlistEntity::getId).toList();
        when(taskRepository.findBySourceWishlistIdIn(plannedItemIds)).thenReturn(tasks);
    }

    private void stubTaskMergedToMainWithCode(TaskEntity task) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTaskId(task.getId());
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        PrReviewEntity review = new PrReviewEntity();
        review.setJulesSessionId(session.getId());
        review.setMerged(true);
        review.setHasCode(true);
        review.setBaseRef("main");
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId()))).thenReturn(List.of(review));
    }
}
