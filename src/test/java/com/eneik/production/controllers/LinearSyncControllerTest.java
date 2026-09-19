package com.eneik.production.controllers;

import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class LinearSyncControllerTest {

    private TaskRepository taskRepository;
    private LinearIssueMetadataRepository metadataRepository;
    private LinearSyncController controller;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        metadataRepository = mock(LinearIssueMetadataRepository.class);
        controller = new LinearSyncController(taskRepository, metadataRepository);
    }

    @Test
    @DisplayName("getCompletenessReport uses findByLinearIssueIdIsNotNull and batch resolves metadata")
    void getCompletenessReportUsesScopedTasksAndBatchMetadata() {
        UUID taskId1 = UUID.randomUUID();
        TaskEntity task1 = new TaskEntity();
        task1.setId(taskId1);
        task1.setLinearIssueId("LIN-101");

        UUID taskId2 = UUID.randomUUID();
        TaskEntity task2 = new TaskEntity();
        task2.setId(taskId2);
        task2.setLinearIssueId("LIN-102");

        when(taskRepository.findByLinearIssueIdIsNotNull()).thenReturn(List.of(task1, task2));

        LinearIssueMetadataEntity meta1 = new LinearIssueMetadataEntity();
        meta1.setTaskId(taskId1);
        meta1.setLinearIssueId("LIN-101");
        meta1.setPrUrl("https://github.com/org/repo/pull/101");
        meta1.setBlockers("None");
        meta1.setDodText("Fully tested and verified");

        LinearIssueMetadataEntity meta2 = new LinearIssueMetadataEntity();
        meta2.setTaskId(taskId2);
        meta2.setLinearIssueId("LIN-102");
        // missing prUrl, blockers, dodText

        when(metadataRepository.findAllById(List.of(taskId1, taskId2))).thenReturn(List.of(meta1, meta2));

        Map<String, Object> report = controller.getCompletenessReport();

        assertThat(report.get("totalIssues")).isEqualTo(2);
        assertThat(report.get("fullyComplete")).isEqualTo(1);
        assertThat(report.get("completeness_rate")).isEqualTo(0.5);

        verify(taskRepository).findByLinearIssueIdIsNotNull();
        verify(taskRepository, never()).findAll();
        verify(metadataRepository).findAllById(List.of(taskId1, taskId2));
        verify(metadataRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getCompletenessReport falls back to findAll when findByLinearIssueIdIsNotNull fails")
    void getCompletenessReportFallbackWhenScopedQueryFails() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setLinearIssueId("LIN-201");

        when(taskRepository.findByLinearIssueIdIsNotNull()).thenThrow(new RuntimeException("DB error"));
        when(taskRepository.findAll()).thenReturn(List.of(task));

        LinearIssueMetadataEntity meta = new LinearIssueMetadataEntity();
        meta.setTaskId(taskId);
        meta.setLinearIssueId("LIN-201");
        meta.setPrUrl("https://github.com/org/repo/pull/201");
        meta.setBlockers("None");
        meta.setDodText("Verified");

        when(metadataRepository.findAllById(anyList())).thenReturn(List.of(meta));

        Map<String, Object> report = controller.getCompletenessReport();

        assertThat(report.get("totalIssues")).isEqualTo(1);
        assertThat(report.get("fullyComplete")).isEqualTo(1);

        verify(taskRepository).findAll();
    }
}
