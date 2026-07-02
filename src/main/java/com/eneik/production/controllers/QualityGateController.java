package com.eneik.production.controllers;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality-gate")
public class QualityGateController {

    private final TaskRepository taskRepository;

    public QualityGateController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/defect-rate")
    public Map<String, Object> getDefectRate() {
        List<TaskEntity> allTasks = taskRepository.findAll();
        long totalAttempts = 0;
        long totalDefects = 0;

        for (TaskEntity task : allTasks) {
            JsonNode report = task.getQualityGateReport();
            if (report != null && report.has("checks")) {
                totalAttempts++;
                JsonNode checks = report.get("checks");
                for (JsonNode check : checks) {
                    if (!check.get("passed").asBoolean()) {
                        totalDefects++;
                    }
                }
            }
        }

        double dpmo = 0;
        if (totalAttempts > 0) {
            // Formula: M / (N * 5) * 1_000_000
            // Assuming 5 opportunities per task (the 5 base checks)
            dpmo = (double) totalDefects / (totalAttempts * 5) * 1_000_000;
        }

        return Map.of(
            "totalAttempts", totalAttempts,
            "defects", totalDefects,
            "dpmo", dpmo
        );
    }
}
