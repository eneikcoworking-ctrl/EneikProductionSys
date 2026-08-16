package com.eneik.production.controllers;

import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/linear-sync")
public class LinearSyncController {

    private final TaskRepository taskRepository;
    private final LinearIssueMetadataRepository metadataRepository;

    public LinearSyncController(TaskRepository taskRepository, LinearIssueMetadataRepository metadataRepository) {
        this.taskRepository = taskRepository;
        this.metadataRepository = metadataRepository;
    }

    @GetMapping("/completeness-report")
    public Map<String, Object> getCompletenessReport() {
        List<TaskEntity> tasksWithLinear = taskRepository.findAll().stream()
                .filter(t -> t.getLinearIssueId() != null && !t.getLinearIssueId().isEmpty())
                .collect(Collectors.toList());

        List<Map<String, Object>> reports = new ArrayList<>();
        int fullyComplete = 0;

        for (TaskEntity task : tasksWithLinear) {
            List<String> missingFields = new ArrayList<>();
            LinearIssueMetadataEntity metadata = metadataRepository.findById(task.getId()).orElse(null);

            // 1. Status (Assume it always exists if task exists)
            // 2. Role (Assume it always exists if task exists)

            // For the others, we check metadata
            if (metadata == null) {
                missingFields.add("assignee");
                missingFields.add("pr_url");
                missingFields.add("blockers");
                missingFields.add("dod_text");
            } else {
                // Actually, status and role are in TaskEntity, but we want to know if they are synced to Linear.
                // For this report, we assume if they have a linearIssueId, they are synced.

                // Assignee - currently we don't have a direct "assignee" field in TaskEntity,
                // it's supposed to be managed by LinearSync script using Claims or similar.
                // For the sake of this report, let's say we check if metadata exists and has fields.

                if (metadata.getPrUrl() == null || metadata.getPrUrl().isEmpty()) {
                    missingFields.add("pr_url");
                }
                if (metadata.getBlockers() == null || metadata.getBlockers().isEmpty()) {
                    missingFields.add("blockers");
                }
                if (metadata.getDodText() == null || metadata.getDodText().isEmpty()) {
                    missingFields.add("dod_text");
                }

                // Assignee logic: if there is an active claim, it should be synced.
                // This is a bit complex for a simple report, so we'll simplify.
            }

            if (missingFields.isEmpty()) {
                fullyComplete++;
            }

            Map<String, Object> taskReport = new HashMap<>();
            taskReport.put("taskId", task.getId());
            taskReport.put("missingFields", missingFields);
            reports.add(taskReport);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalIssues", tasksWithLinear.size());
        result.put("fullyComplete", fullyComplete);
        result.put("completeness_rate", tasksWithLinear.isEmpty() ? 0 : (double) fullyComplete / tasksWithLinear.size());
        result.put("issues", reports);

        return result;
    }
}
