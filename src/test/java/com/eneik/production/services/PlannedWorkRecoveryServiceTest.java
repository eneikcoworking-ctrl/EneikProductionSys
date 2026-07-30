package com.eneik.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class PlannedWorkRecoveryServiceTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
    private final ClaimService claimService = mock(ClaimService.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);
    private final PlannedWorkRecoveryService service = new PlannedWorkRecoveryService(
            taskRepository, wishlistRepository, sessionRepository, claimService, readinessService, new ObjectMapper());

    @Test
    void resumesSameRootTaskOnlyOnceWithoutCreatingWork() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity task = retiredTask(project, source.getId());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(sessionRepository.findByTaskId(task.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(task.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(task.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);

        assertEquals(1, service.resumeNextFrontier(project));
        assertEquals(TaskStatus.queued, task.getStatus());
        assertNull(task.getJulesSessionName());
        assertEquals(1, task.getPayload().path("ems_bounded_plan_resume_count").asInt());
        verify(taskRepository, times(1)).save(task);

        task.setStatus(TaskStatus.failed);
        assertEquals(0, service.resumeNextFrontier(project));
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    void waitsForDependencyFrontierInsteadOfRequeueingWholeGraph() {
        ReflectionTestUtils.setField(service, "frontierResumeLimit", 3);
        ProjectEntity project = project();
        WishlistEntity source = source(project.getId());
        TaskEntity dependency = retiredTask(project, UUID.randomUUID());
        TaskEntity child = retiredTask(project, source.getId());
        child.setDependsOn(dependency);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(child));
        when(wishlistRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(readinessService.isDependencySatisfied(dependency)).thenReturn(false);

        assertEquals(0, service.resumeNextFrontier(project));
        verify(taskRepository, never()).save(any());

        when(readinessService.isDependencySatisfied(dependency)).thenReturn(true);
        when(sessionRepository.findByTaskId(child.getId())).thenReturn(List.of());
        when(readinessService.isTaskMerged(child.getId())).thenReturn(false);
        when(taskRepository.compareAndSetStatus(child.getId(), TaskStatus.failed, TaskStatus.queued)).thenReturn(1);
        assertEquals(1, service.resumeNextFrontier(project));
    }

    @Test
    void cleanoutDoesNotCompleteCompilerTaskBeforeRealFeatureDecomposition() {
        ProjectEntity project = project();
        TaskEntity bootstrap = new TaskEntity();
        bootstrap.setId(UUID.randomUUID());
        bootstrap.setProject(project);
        bootstrap.setTitle("Runtime Contract");
        bootstrap.setStatus(TaskStatus.done);

        TaskEntity compiler = new TaskEntity();
        compiler.setId(UUID.randomUUID());
        compiler.setProject(project);
        compiler.setTitle("Compile 1 wishlist(s) into task graph");
        compiler.setStatus(TaskStatus.queued);

        when(readinessService.computeForProject(project.getId()))
                .thenReturn(ClientDeliverableReadinessService.Readiness.none());
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(compiler, bootstrap));

        assertEquals(0, service.cleanoutOrphanedMetaTasksWhenProductComplete(project));
        assertEquals(TaskStatus.queued, compiler.getStatus());
        verify(taskRepository, never()).save(compiler);
        verify(claimService, never()).releaseTerminalClaim(compiler.getId());
    }

    @Test
    void cleanoutCompletesMetaTaskOnlyAfterRealFeatureDecompositionExists() {
        ProjectEntity project = project();
        TaskEntity productTask = new TaskEntity();
        productTask.setId(UUID.randomUUID());
        productTask.setProject(project);
        productTask.setTitle("Product feature task");
        productTask.setStatus(TaskStatus.done);

        TaskEntity compiler = new TaskEntity();
        compiler.setId(UUID.randomUUID());
        compiler.setProject(project);
        compiler.setTitle("Compile 1 wishlist(s) into task graph");
        compiler.setStatus(TaskStatus.queued);

        when(readinessService.computeForProject(project.getId()))
                .thenReturn(new ClientDeliverableReadinessService.Readiness(1, 1, 1, 1, 1.0, true));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of(compiler, productTask));

        assertEquals(1, service.cleanoutOrphanedMetaTasksWhenProductComplete(project));
        assertEquals(TaskStatus.done, compiler.getStatus());
        verify(taskRepository).save(compiler);
        verify(claimService).releaseTerminalClaim(compiler.getId());
    }

    @Test
    void stuckCompilingWishlistDoesNotConvertBecauseAnotherWishlistConverted() {
        ProjectEntity project = project();
        WishlistEntity stuck = source(project.getId());
        stuck.setStatus(WishlistStatus.compiling);
        stuck.setCompiledByRole(null);
        stuck.setFeatureId(null);

        WishlistEntity unrelatedConverted = source(project.getId());
        unrelatedConverted.setStatus(WishlistStatus.converted_to_task);
        unrelatedConverted.setSource(WishlistSource.role);
        unrelatedConverted.setSourceRoleTag("BARCAN-TAG-01");

        when(wishlistRepository.findByProjectIdAndStatus(project.getId(), WishlistStatus.compiling))
                .thenReturn(List.of(stuck));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(wishlistRepository.findByProjectId(project.getId()))
                .thenReturn(List.of(stuck, unrelatedConverted));
        when(readinessService.computeForProject(project.getId(), stuck.getId()))
                .thenReturn(ClientDeliverableReadinessService.Readiness.none());

        assertEquals(1, service.recoverStuckCompilingWishlists(project));
        assertEquals(WishlistStatus.pending, stuck.getStatus());
        verify(wishlistRepository).save(stuck);
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        return project;
    }

    private WishlistEntity source(UUID projectId) {
        WishlistEntity source = new WishlistEntity();
        source.setId(UUID.randomUUID());
        source.setProjectId(projectId);
        source.setSource(WishlistSource.client);
        source.setCompiledByRole("BARCAN-TAG-08");
        source.setFeatureId(UUID.randomUUID());
        source.setContent("planned slice");
        return source;
    }

    private TaskEntity retiredTask(ProjectEntity project, UUID sourceWishlistId) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setSourceWishlistId(sourceWishlistId);
        task.setFeatureId(UUID.randomUUID());
        task.setStatus(TaskStatus.failed);
        task.setJulesSessionName("sessions/old");
        task.setJulesDispatchStatus(
                "Blocked task retired; auto-recovery follow-up disabled during task-expansion incident");
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("ems_semantic_key", "ems:test");
        task.setPayload(payload);
        return task;
    }
}
