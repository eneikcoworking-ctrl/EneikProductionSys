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

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            wishlistRepository, featureRepository, taskRepository, julesSessionRepository, prReviewRepository);

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
        assertEquals(0.25, readiness.ratio(), 0.0001);
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
    void deliveryDecisionRecordMayCountWithoutCode() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-09");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        assertEquals(1, service.computeForProject(projectId).mergedDeliverables());
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
        when(featureRepository.findByProjectId(projectId)).thenReturn(List.of(feature));
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

    private void stubPlan(UUID projectId, WishlistEntity root, FeatureEntity feature,
                          List<WishlistEntity> items, List<TaskEntity> tasks) {
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectId(projectId)).thenReturn(List.of(feature));
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
}
