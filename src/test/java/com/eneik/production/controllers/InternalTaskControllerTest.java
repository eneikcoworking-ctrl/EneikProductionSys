package com.eneik.production.controllers;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InternalTaskController} enforcing:
 * <ul>
 *   <li>PRINCIPLED_INTEGRITY (D012): Bounded task queries and truthful contracts.</li>
 *   <li>CATEGORY_ERROR_SCAN (D002): No full-table scans disguised as exact lookups.</li>
 * </ul>
 */
class InternalTaskControllerTest {

    private TaskRepository taskRepository;
    private LinearIssueMetadataRepository metadataRepository;
    private ClaimRepository claimRepository;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;

    private InternalTaskController controller;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        metadataRepository = mock(LinearIssueMetadataRepository.class);
        claimRepository = mock(ClaimRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();

        controller = new InternalTaskController(
                taskRepository,
                metadataRepository,
                claimRepository,
                jdbcTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("getAllTasks with projectId returns paged project tasks and never calls findAll()")
    void getAllTasksWithProjectIdReturnsPagedProjectTasksAndNeverCallsFindAll() {
        UUID projectId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of(task));

        List<TaskEntity> result = controller.getAllTasks(projectId, 25, 2, null);

        assertThat(result).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository, times(1)).findByProjectIdOrderByCreatedAtDesc(eq(projectId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);

        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllTasks without projectId clamps limit to MAX_LIMIT (200) and never calls findAll()")
    void getAllTasksWithoutProjectIdClampsLimitToMaxLimitAndNeverCallsFindAll() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());

        when(taskRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(task));

        // Attempting to request 10,000 tasks must be clamped to MAX_LIMIT (200)
        List<TaskEntity> result = controller.getAllTasks(null, 10000, 0, null);

        assertThat(result).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository, times(1)).findAllByOrderByCreatedAtDesc(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(InternalTaskController.MAX_LIMIT);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);

        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllTasks calculates page number correctly when offset is provided")
    void getAllTasksCalculatesPageNumberCorrectlyWhenOffsetIsProvided() {
        when(taskRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(List.of());

        // limit=50, offset=150 -> page = 150 / 50 = 3
        controller.getAllTasks(null, 50, 0, 150);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAllByOrderByCreatedAtDesc(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("getTaskById returns task when found and 404 when absent without scanning full table")
    void getTaskByIdReturnsTaskWhenFoundAnd404WhenAbsent() {
        UUID existingId = UUID.randomUUID();
        UUID absentId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(existingId);

        when(taskRepository.findById(existingId)).thenReturn(Optional.of(task));
        when(taskRepository.findById(absentId)).thenReturn(Optional.empty());

        ResponseEntity<TaskEntity> foundResponse = controller.getTaskById(existingId);
        assertThat(foundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(foundResponse.getBody()).isSameAs(task);

        ResponseEntity<TaskEntity> absentResponse = controller.getTaskById(absentId);
        assertThat(absentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("getTaskByLinearId uses findFirstByLinearIssueId and never performs full table scan")
    void getTaskByLinearIdUsesRepositoryLookupAndNeverCallsFindAll() {
        String linearId = "LIN-42";
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setLinearIssueId(linearId);

        when(taskRepository.findFirstByLinearIssueId(linearId)).thenReturn(Optional.of(task));
        when(taskRepository.findFirstByLinearIssueId("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<TaskEntity> foundResponse = controller.getTaskByLinearId(linearId);
        assertThat(foundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(foundResponse.getBody()).isSameAs(task);

        ResponseEntity<TaskEntity> absentResponse = controller.getTaskByLinearId("UNKNOWN");
        assertThat(absentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("updateTask rejects overwriting terminal status with CONFLICT (Law 20)")
    void updateTaskRejectsOverwritingTerminalStatusWithConflict() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.done);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        ResponseEntity<?> response = controller.updateTask(taskId, Map.of("status", "in_progress"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(taskRepository, never()).save(any());
    }
}
