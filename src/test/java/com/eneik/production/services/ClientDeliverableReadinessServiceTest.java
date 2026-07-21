package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Ф5/Д4 (2026-07-21): a client deliverable's derived task can get abandoned (merge-conflict escalation,
 * force-unblock exhaustion) and replaced by a new task under a new recovery wishlist with a DIFFERENT
 * sourceWishlistId - the original computeForProject implementation matched merged-status by literal
 * sourceWishlistId and would never see that replacement merge, permanently pinning the deliverable at
 * "not merged" even after the real work shipped. This test proves the featureId-based join fixes that.
 */
class ClientDeliverableReadinessServiceTest {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            wishlistRepository, taskRepository, julesSessionRepository, prReviewRepository);

    @Test
    void recoveryTaskMergingUnderSameFeatureIdCountsTowardOriginalDeliverable() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID originalWishlistId = UUID.randomUUID();
        UUID originalTaskId = UUID.randomUUID();
        UUID recoveryTaskId = UUID.randomUUID();

        WishlistEntity clientWishlist = new WishlistEntity();
        clientWishlist.setId(originalWishlistId);
        clientWishlist.setProjectId(projectId);
        clientWishlist.setSource(WishlistSource.client);
        clientWishlist.setCompiledByRole("BARCAN-TAG-08");
        clientWishlist.setFeatureId(featureId);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(clientWishlist));

        // Original task abandoned (failed, never merged) - its own sourceWishlistId is the original one.
        TaskEntity originalTask = new TaskEntity();
        originalTask.setId(originalTaskId);
        originalTask.setSourceWishlistId(originalWishlistId);
        originalTask.setFeatureId(featureId);
        originalTask.setStatus(TaskStatus.failed);

        // Recovery task: DIFFERENT sourceWishlistId (its own recovery wishlist), SAME featureId - and it
        // actually merged.
        TaskEntity recoveryTask = new TaskEntity();
        recoveryTask.setId(recoveryTaskId);
        recoveryTask.setSourceWishlistId(UUID.randomUUID());
        recoveryTask.setFeatureId(featureId);
        recoveryTask.setStatus(TaskStatus.done);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(originalTask, recoveryTask));

        UUID originalSessionId = UUID.randomUUID();
        UUID recoverySessionId = UUID.randomUUID();
        JulesSessionEntity originalSession = new JulesSessionEntity();
        originalSession.setId(originalSessionId);
        JulesSessionEntity recoverySession = new JulesSessionEntity();
        recoverySession.setId(recoverySessionId);
        when(julesSessionRepository.findByTaskId(originalTaskId)).thenReturn(List.of(originalSession));
        when(julesSessionRepository.findByTaskId(recoveryTaskId)).thenReturn(List.of(recoverySession));

        when(prReviewRepository.existsByJulesSessionIdInAndMergedTrue(List.of(originalSessionId)))
                .thenReturn(false);
        when(prReviewRepository.existsByJulesSessionIdInAndMergedTrue(List.of(recoverySessionId)))
                .thenReturn(true);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertEquals(1, readiness.totalDeliverables());
        assertEquals(1, readiness.mergedDeliverables(),
                "recovery task's merge should count toward the original client deliverable via featureId, "
                        + "even though its sourceWishlistId differs from the original wishlist");
    }

    @Test
    void deliverableStaysUnmergedWhenNothingUnderItsFeatureHasMerged() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID wishlistId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        WishlistEntity clientWishlist = new WishlistEntity();
        clientWishlist.setId(wishlistId);
        clientWishlist.setProjectId(projectId);
        clientWishlist.setSource(WishlistSource.client);
        clientWishlist.setCompiledByRole("BARCAN-TAG-08");
        clientWishlist.setFeatureId(featureId);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(clientWishlist));

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setSourceWishlistId(wishlistId);
        task.setFeatureId(featureId);
        task.setStatus(TaskStatus.review);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(task));
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertEquals(1, readiness.totalDeliverables());
        assertEquals(0, readiness.mergedDeliverables());
    }

    @Test
    void isTaskMergedIsPubliclyReusable() {
        UUID taskId = UUID.randomUUID();
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        assertFalse(service.isTaskMerged(taskId), "a task with no sessions can't have merged");
    }

    private TaskEntity taskWith(UUID id, UUID projectId, UUID featureId, String roleTag, TaskStatus status) {
        TaskEntity t = new TaskEntity();
        t.setId(id);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        t.setProject(project);
        t.setFeatureId(featureId);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        t.setRole(role);
        t.setStatus(status);
        return t;
    }

    @Test
    void dependencySatisfiedWhenLiterallyMerged() {
        UUID depId = UUID.randomUUID();
        TaskEntity dep = taskWith(depId, UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-08", TaskStatus.review);
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        when(julesSessionRepository.findByTaskId(depId)).thenReturn(List.of(session));
        when(prReviewRepository.existsByJulesSessionIdInAndMergedTrue(List.of(session.getId()))).thenReturn(true);

        assertTrue(service.isDependencySatisfied(dep));
    }

    @Test
    void dependencyNotSatisfiedWhenAbandonedWithNoReplacement() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID depId = UUID.randomUUID();
        TaskEntity dep = taskWith(depId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.failed);
        when(julesSessionRepository.findByTaskId(depId)).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(dep));

        assertFalse(service.isDependencySatisfied(dep));
    }

    @Test
    void dependencySatisfiedViaMergedReplacementUnderSameFeatureAndRole() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID abandonedId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();

        TaskEntity abandoned = taskWith(abandonedId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.failed);
        TaskEntity replacement = taskWith(replacementId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.done);

        when(julesSessionRepository.findByTaskId(abandonedId)).thenReturn(List.of());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(abandoned, replacement));
        JulesSessionEntity replacementSession = new JulesSessionEntity();
        replacementSession.setId(UUID.randomUUID());
        when(julesSessionRepository.findByTaskId(replacementId)).thenReturn(List.of(replacementSession));
        when(prReviewRepository.existsByJulesSessionIdInAndMergedTrue(List.of(replacementSession.getId())))
                .thenReturn(true);

        assertTrue(service.isDependencySatisfied(abandoned));
    }

    @Test
    void dependencySatisfiedThroughChainedAbandonment() {
        // A abandoned -> B (same feature/role) also abandoned -> C (same feature/role) merged.
        // isDependencySatisfied(A) should find C, not just look one hop ahead.
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();

        TaskEntity a = taskWith(aId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.failed);
        TaskEntity b = taskWith(bId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.failed);
        TaskEntity c = taskWith(cId, projectId, featureId, "BARCAN-TAG-08", TaskStatus.done);

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(a, b, c));
        when(julesSessionRepository.findByTaskId(aId)).thenReturn(List.of());
        when(julesSessionRepository.findByTaskId(bId)).thenReturn(List.of());
        JulesSessionEntity cSession = new JulesSessionEntity();
        cSession.setId(UUID.randomUUID());
        when(julesSessionRepository.findByTaskId(cId)).thenReturn(List.of(cSession));
        when(prReviewRepository.existsByJulesSessionIdInAndMergedTrue(List.of(cSession.getId()))).thenReturn(true);

        assertTrue(service.isDependencySatisfied(a));
    }
}
