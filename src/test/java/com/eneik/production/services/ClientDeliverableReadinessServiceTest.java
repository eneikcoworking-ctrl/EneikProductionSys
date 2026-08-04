package com.eneik.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientDeliverableReadinessServiceTest {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository =
            mock(com.eneik.production.repositories.FeatureThreadRepository.class);
    private final com.eneik.production.repositories.ProjectRepository projectRepository =
            mock(com.eneik.production.repositories.ProjectRepository.class);

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            wishlistRepository, featureRepository, taskRepository, julesSessionRepository, prReviewRepository,
            featureThreadRepository, projectRepository);

    @Test
    void oneMergedTaskDoesNotCompleteFourItemFeature() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 4);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertTrue(readiness.decompositionComplete());
        assertEquals(1, readiness.totalFeatures());
        assertEquals(0, readiness.completeFeatures());
        assertEquals(4, readiness.totalDeliverables());
        assertEquals(1, readiness.mergedDeliverables());
        // 2026-07-26 operator directive ("считать по фичам, а не по таскам!"): ratio() is now
        // completeFeatures/totalFeatures, not mergedDeliverables/totalDeliverables - the feature isn't
        // complete (only 1 of its 4 items merged), so ratio is 0, even though mergedDeliverables=1.
        assertEquals(0.0, readiness.ratio(), 0.0001);
    }

    @Test
    void featureCompletesOnlyWhenEveryPlannedItemHasOwnMerge() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 3);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        tasks.forEach(task -> stubMerged(task, true));

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertEquals(1, readiness.completeFeatures());
        assertEquals(3, readiness.mergedDeliverables());
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void engineeringMergeWithoutCodeDoesNotCount() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
    }

    @Test
    void mergeIntoOpenFeatureThreadDoesNotCountUntilThreadClosesIntoMain() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(true);
        review.setBaseRef("feat/some-thread-branch");
        when(julesSessionRepository.findByTaskId(tasks.get(0).getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId()))
                .thenReturn(java.util.Optional.of(thread));

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
        assertFalse(service.reachedMain(tasks.get(0)));

        thread.setMergedToMainAt(java.time.Instant.now());

        assertEquals(1, service.computeForProject(projectId).mergedDeliverables());
        assertTrue(service.reachedMain(tasks.get(0)));
    }

    @Test
    void featureIsReadyForCloseoutAsSoonAsAnyTerminalTaskHasRealMergedWorkEvenWithSiblingsStillInReview() {
        // 2026-07-26 operator directive ("убрать блокировку закрытия"): closeout used to wait for EVERY
        // sibling task to reach a terminal status before folding ANY already-merged work into main -
        // confirmed live (test-thirty-eighth) holding completeFeatures at 0 indefinitely behind ordinary
        // review-stage siblings. A sibling still in review has no commits on the thread branch yet, so
        // there is nothing of theirs to wait for.
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity mergedDone = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stubMerged(mergedDone, true);
        TaskEntity siblingStillInReview = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        siblingStillInReview.setStatus(TaskStatus.review);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(mergedDone, siblingStillInReview));
        when(wishlistRepository.findByFeatureId(featureId)).thenReturn(List.of());

        assertTrue(service.isFeatureReadyForCloseout(projectId, featureId));
    }

    @Test
    void featureIsNotReadyForCloseoutWhenNoTerminalTaskHasRealMergedWork() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity doneWithoutCode = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stubMerged(doneWithoutCode, false);
        TaskEntity stillInReview = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stillInReview.setStatus(TaskStatus.review);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(doneWithoutCode, stillInReview));

        assertFalse(service.isFeatureReadyForCloseout(projectId, featureId));
    }

    @Test
    void deliveryDecisionRecordIsExcludedFromCodeMergeRatioEntirely() {
        // Operator directive 2026-07-24 (sharpened over two rounds of correction): the readiness ratio
        // must only count tasks that produce code, not decision/spike/review/other auxiliary work.
        // Superseded behavior: a BARCAN-TAG-09 (EmsFlowStage.DECISION) task used to count as "merged"
        // via a special-case in hasRequiredMergeEvidence even without code - now it is excluded from the
        // ratio entirely (contributes to neither total nor merged), not force-counted as done.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-09");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(0, readiness.totalDeliverables());
        assertEquals(0, readiness.mergedDeliverables());
        // Nothing left in scope to measure - must read as "not applicable", never as "0% done".
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void spikeTaskIsExcludedFromCodeMergeRatioEvenThoughItsPrNeverMerges() {
        // The live bug that triggered this fix: AutoMergeService deliberately never merges a
        // `complex`-Cynefin spike's PR (its deliverable is a decision record, not shippable code), so the
        // old formula's "requires a merged review" check could never pass for it - permanently deflating
        // the denominator with zero relation to duplication or any other confirmed bug. A code-producing
        // role (BARCAN-TAG-02) makes this deliberate, not a DECISION-stage coincidence.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        tasks.get(0).setCynefinDomain("complex");
        tasks.get(0).setStatus(TaskStatus.spike_completed);
        stubPlan(projectId, root, feature, items, tasks);
        // Deliberately no stubMerged() - a spike's review is never merged=true by design.

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(0, readiness.totalDeliverables());
        assertEquals(0, readiness.mergedDeliverables());
        assertTrue(readiness.decompositionComplete());
    }

    @Test
    void pendingIterationRootBlocksFalsificationEvenIfExistingPlanMerged() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.pending);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertFalse(readiness.decompositionComplete());
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void dismissedAuditRootDoesNotBlockCompletedProductDecomposition() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        WishlistEntity dismissedAudit = root(projectId, UUID.randomUUID(), WishlistStatus.dismissed);
        dismissedAudit.setSource(WishlistSource.coverage_gap);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.add(dismissedAudit);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(items.stream().map(WishlistEntity::getId).toList()))
                .thenReturn(tasks);

        assertTrue(service.computeForProject(projectId).decompositionComplete());
    }

    @Test
    void dependencyReplacementMustShareSemanticKey() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity failed = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        failed.setStatus(TaskStatus.failed);
        failed.setPayload(payload("ems:one"));
        TaskEntity otherSlice = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        otherSlice.setPayload(payload("ems:other"));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(failed, otherSlice));
        stubMerged(otherSlice, true);

        assertFalse(service.isDependencySatisfied(failed));

        otherSlice.setPayload(payload("ems:one"));
        assertTrue(service.isDependencySatisfied(failed));
    }

    @Test
    void taskWithoutSessionCannotBeMerged() {
        UUID taskId = UUID.randomUUID();
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        assertFalse(service.isTaskMerged(taskId));
    }

    @Test
    void apiContractTaskWithOpenPrIsEarlyUnblockable() {
        TaskEntity contractTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        contractTask.setStatus(TaskStatus.review);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.pending_review);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.done);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));
    }

    @Test
    void apiContractTaskStillQueuedIsNotEarlyUnblockable() {
        TaskEntity contractTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        contractTask.setStatus(TaskStatus.queued);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.claimed);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.failed);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));
    }

    @Test
    void nonSpecRoleIsNeverEarlyUnblockableEvenInReview() {
        TaskEntity backendTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-02");
        backendTask.setStatus(TaskStatus.review);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(backendTask));
    }

    @Test
    void nullDependencyOrRoleIsNeverEarlyUnblockable() {
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(null));

        TaskEntity noRole = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        noRole.setRole(null);
        noRole.setStatus(TaskStatus.review);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(noRole));
    }

    @Test
    void decisionArchitectureAndComplianceRolesAreEarlyUnblockableWhenPrIsOpen() {
        for (String roleTag : List.of("BARCAN-TAG-09", "BARCAN-TAG-01", "BARCAN-TAG-10")) {
            TaskEntity specTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roleTag);

            specTask.setStatus(TaskStatus.review);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " in review");

            specTask.setStatus(TaskStatus.pending_review);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " in pending_review");

            specTask.setStatus(TaskStatus.done);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " when done");
        }
    }

    @Test
    void operationsAndVerificationRolesAreNeverEarlyUnblockableEvenInReview() {
        // Not previously covered at all - explicit re-confirmation these two stay excluded (real deploy
        // config / real test results, not a reference document a dependent can build against pre-merge).
        for (String roleTag : List.of("BARCAN-TAG-05", "BARCAN-TAG-06")) {
            TaskEntity task = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roleTag);
            task.setStatus(TaskStatus.review);
            assertFalse(service.isSpecDependencyPrOpenButUnmerged(task), roleTag + " must stay excluded");
        }
    }

    private void stubPlan(UUID projectId, WishlistEntity root, FeatureEntity feature,
                          List<WishlistEntity> items, List<TaskEntity> tasks) {
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(items.stream().map(WishlistEntity::getId).toList()))
                .thenReturn(tasks);
    }

    private FeatureEntity feature(UUID projectId, UUID rootId) {
        FeatureEntity feature = new FeatureEntity();
        feature.setId(UUID.randomUUID());
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootId);
        return feature;
    }

    private WishlistEntity root(UUID projectId, UUID rootId, WishlistStatus status) {
        WishlistEntity root = new WishlistEntity();
        root.setId(rootId);
        root.setProjectId(projectId);
        root.setSource(WishlistSource.client);
        root.setContent("root brief");
        root.setStatus(status);
        return root;
    }

    private List<WishlistEntity> plannedItems(UUID projectId, UUID featureId, int count) {
        List<WishlistEntity> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WishlistEntity item = new WishlistEntity();
            item.setId(UUID.randomUUID());
            item.setProjectId(projectId);
            item.setSource(WishlistSource.client);
            item.setContent("planned item " + i);
            item.setStatus(WishlistStatus.converted_to_task);
            item.setCompiledByRole("BARCAN-TAG-09");
            item.setFeatureId(featureId);
            items.add(item);
        }
        return items;
    }

    private List<TaskEntity> tasksFor(UUID projectId, UUID featureId, List<WishlistEntity> items, String roleTag) {
        return items.stream().map(item -> task(UUID.randomUUID(), projectId, featureId, item.getId(), roleTag)).toList();
    }

    private TaskEntity task(UUID id, UUID projectId, UUID featureId, UUID wishlistId, String roleTag) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setSourceWishlistId(wishlistId);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        task.setRole(role);
        task.setStatus(TaskStatus.done);
        return task;
    }

    private void stubMerged(TaskEntity task, boolean hasCode) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(hasCode);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));
    }

    private ObjectNode payload(String semanticKey) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("ems_semantic_key", semanticKey);
        return payload;
    }

    // --- Valueless-epic cleanup (2026-07-25, live incident: two epics permanently stuck at 0 code items -
    // one from a dismissed wishlist, one whose only task was a resolved DECISION-stage record - dragged
    // down completeFeatures/totalFeatures forever with no way to resolve themselves) -------------------

    private static final java.time.Instant OLD_ENOUGH = java.time.Instant.now().minus(java.time.Duration.ofHours(1));

    @Test
    void deleteValuelessEpicsRemovesAnEpicWhoseOnlySourceWishlistWasDismissed() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        WishlistEntity dismissedItem = plannedItems(projectId, feature.getId(), 1).get(0);
        dismissedItem.setStatus(WishlistStatus.dismissed);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(dismissedItem), List.of());
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId())).thenReturn(java.util.Optional.empty());
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.deleteValuelessEpics();

        // 2026-08-04 (3-layer model): soft-delete now, not deleteById - the row survives with its
        // originFeatureId lineage intact, only dismissedAt marks it as excluded from active readiness.
        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository).save(feature);
        assertNotNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsRemovesAnEpicWhoseOnlyTaskIsAuxiliaryAndAlsoDeletesItsFeatureThread() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        // BARCAN-TAG-09 = DECISION stage - structurally never produces mergeable code (isAuxiliaryTask).
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-09");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, items, tasks);
        FeatureThreadEntity thread = new FeatureThreadEntity();
        thread.setFeatureId(feature.getId());
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId())).thenReturn(java.util.Optional.of(thread));
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.deleteValuelessEpics();

        verify(featureThreadRepository).delete(thread);
        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository).save(feature);
        assertNotNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsNeverTouchesAnEpicWithRealCodeValue() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02"); // real code role

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, items, tasks);

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
        assertNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsLeavesATooNewEpicAloneEvenWithZeroItems() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(java.time.Instant.now()); // freshly created - give the compiler a chance

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(), List.of());

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
    }

    @Test
    void deleteValuelessEpicsLeavesAnEpicAloneWhileAWishlistItemIsStillPendingCompilation() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        WishlistEntity pendingItem = plannedItems(projectId, feature.getId(), 1).get(0);
        pendingItem.setStatus(WishlistStatus.pending); // still might produce real code later

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(pendingItem), List.of());

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
    }
}
