package com.eneik.production.controllers;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/quality")
public class QualityMetricsController {

    private final PrReviewRepository prReviewRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final OnboardingAuditFindingRepository onboardingAuditFindingRepository;

    public QualityMetricsController(PrReviewRepository prReviewRepository,
                                    TaskConflictRepository taskConflictRepository,
                                    JulesSessionRepository julesSessionRepository,
                                    TaskRepository taskRepository,
                                    ProjectRepository projectRepository,
                                    OnboardingAuditFindingRepository onboardingAuditFindingRepository) {
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
    }

    @GetMapping("/conflict-dpmo")
    public Map<String, Object> getConflictDpmo() {
        long mergedAllTime = prReviewRepository.countByMergedTrue();
        long conflictsAllTime = taskConflictRepository.count();
        long totalAttemptsAllTime = mergedAllTime + conflictsAllTime;
        double dpmoAllTime = totalAttemptsAllTime > 0 ? (double) conflictsAllTime / totalAttemptsAllTime * 1_000_000 : 0;

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long mergedLast7Days = prReviewRepository.countByMergedTrueAndCreatedAtAfter(sevenDaysAgo);
        long conflictsLast7Days = taskConflictRepository.countByDetectedAtAfter(sevenDaysAgo);
        long totalAttemptsLast7Days = mergedLast7Days + conflictsLast7Days;
        double dpmoLast7Days = totalAttemptsLast7Days > 0 ? (double) conflictsLast7Days / totalAttemptsLast7Days * 1_000_000 : 0;

        // Calculate project breakdown
        List<ProjectEntity> projects = projectRepository.findAll();
        List<Map<String, Object>> byProjectList = new ArrayList<>();

        if (!projects.isEmpty()) {
            List<PrReviewEntity> mergedReviews = prReviewRepository.findByMergedTrueAndJulesSessionIdIsNotNull();
            List<UUID> sessionIds = mergedReviews.stream().map(PrReviewEntity::getJulesSessionId).filter(Objects::nonNull).distinct().toList();
            List<JulesSessionEntity> sessions = julesSessionRepository.findAllById(sessionIds);
            List<UUID> taskIds = sessions.stream().map(JulesSessionEntity::getTaskId).filter(Objects::nonNull).distinct().toList();
            List<TaskEntity> tasks = taskRepository.findAllById(taskIds);

            Map<UUID, UUID> taskToProjectMap = tasks.stream()
                    .filter(t -> t.getProject() != null)
                    .collect(java.util.stream.Collectors.toMap(TaskEntity::getId, t -> t.getProject().getId(), (a, b) -> a));

            Map<UUID, UUID> sessionToProjectMap = new HashMap<>();
            for (JulesSessionEntity s : sessions) {
                if (s.getTaskId() != null && taskToProjectMap.containsKey(s.getTaskId())) {
                    sessionToProjectMap.put(s.getId(), taskToProjectMap.get(s.getTaskId()));
                }
            }

            List<TaskConflictEntity> allConflicts = taskConflictRepository.findAll();

            for (ProjectEntity project : projects) {
                UUID projId = project.getId();

                long projMergedAll = mergedReviews.stream()
                        .filter(r -> projId.equals(sessionToProjectMap.get(r.getJulesSessionId())))
                        .count();
                long projConflictsAll = allConflicts.stream()
                        .filter(c -> c.getTask() != null && c.getTask().getProject() != null && projId.equals(c.getTask().getProject().getId()))
                        .count();
                long projTotalAttemptsAll = projMergedAll + projConflictsAll;
                double projDpmoAll = projTotalAttemptsAll > 0 ? (double) projConflictsAll / projTotalAttemptsAll * 1_000_000 : 0;

                long projMerged7 = mergedReviews.stream()
                        .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenDaysAgo))
                        .filter(r -> projId.equals(sessionToProjectMap.get(r.getJulesSessionId())))
                        .count();
                long projConflicts7 = allConflicts.stream()
                        .filter(c -> c.getDetectedAt() != null && c.getDetectedAt().isAfter(sevenDaysAgo))
                        .filter(c -> c.getTask() != null && c.getTask().getProject() != null && projId.equals(c.getTask().getProject().getId()))
                        .count();
                long projTotalAttempts7 = projMerged7 + projConflicts7;
                double projDpmo7 = projTotalAttempts7 > 0 ? (double) projConflicts7 / projTotalAttempts7 * 1_000_000 : 0;

                byProjectList.add(Map.of(
                        "projectId", projId,
                        "projectName", project.getName(),
                        "totalMergeAttempts", projTotalAttemptsAll,
                        "conflicts", projConflictsAll,
                        "dpmo", projDpmoAll,
                        "last7Days", Map.of(
                                "totalMergeAttempts", projTotalAttempts7,
                                "conflicts", projConflicts7,
                                "dpmo", projDpmo7
                        )
                ));
            }
        }

        return Map.of(
                "totalMergeAttempts", totalAttemptsAllTime,
                "conflicts", conflictsAllTime,
                "dpmo", dpmoAllTime,
                "last7Days", Map.of(
                        "totalMergeAttempts", totalAttemptsLast7Days,
                        "conflicts", conflictsLast7Days,
                        "dpmo", dpmoLast7Days
                ),
                "byProject", byProjectList
        );
    }

    @GetMapping("/defect-summary")
    public Map<String, Object> getDefectSummary() {
        List<TaskConflictEntity> conflicts = taskConflictRepository.findAll();

        List<TaskEntity> tasksWithReport = taskRepository.findByQualityGateReportIsNotNull();
        long qualityGateDefects = 0;
        List<Map<String, Object>> qualityGateItems = new ArrayList<>();
        for (TaskEntity task : tasksWithReport) {
            com.fasterxml.jackson.databind.JsonNode report = task.getQualityGateReport();
            if (report != null && report.has("checks")) {
                for (com.fasterxml.jackson.databind.JsonNode check : report.get("checks")) {
                    if (!check.path("passed").asBoolean(true)) {
                        qualityGateDefects++;
                        qualityGateItems.add(Map.of(
                                "taskId", task.getId(),
                                "checkName", check.path("name").asText("unknown"),
                                "description", check.path("description").asText("")
                        ));
                    }
                }
            }
        }

        List<OnboardingAuditFindingEntity> onboardingFindings = onboardingAuditFindingRepository.findAll();

        long totalDefects = conflicts.size() + qualityGateDefects + onboardingFindings.size();

        return Map.of(
                "totalDefects", totalDefects,
                "conflicts", Map.of(
                        "total", conflicts.size(),
                        "items", conflicts.stream().map(c -> Map.of(
                                "id", c.getId(),
                                "taskId", c.getTask() != null ? c.getTask().getId() : "",
                                "conflictType", c.getConflictType() != null ? c.getConflictType() : "",
                                "status", c.getResolutionStatus() != null ? c.getResolutionStatus() : ""
                        )).toList()
                ),
                "qualityGate", Map.of(
                        "total", qualityGateDefects,
                        "items", qualityGateItems
                ),
                "onboarding", Map.of(
                        "total", onboardingFindings.size(),
                        "items", onboardingFindings.stream().map(f -> Map.of(
                                "id", f.getId(),
                                "projectId", f.getProject() != null ? f.getProject().getId() : "",
                                "roleTag", f.getRoleTag(),
                                "severity", f.getSeverity(),
                                "filePath", f.getFilePath() != null ? f.getFilePath() : "",
                                "lineNumber", f.getLineNumber() != null ? f.getLineNumber() : 0,
                                "findingText", f.getFindingText()
                        )).toList()
                )
        );
    }
}
