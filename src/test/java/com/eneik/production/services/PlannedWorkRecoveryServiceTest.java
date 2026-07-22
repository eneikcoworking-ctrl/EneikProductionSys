package com.eneik.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
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
        assertEquals(1, service.resumeNextFrontier(project));
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
