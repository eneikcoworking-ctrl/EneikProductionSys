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

    public QualityMetricsController(PrReviewRepository prReviewRepository,
                                    TaskConflictRepository taskConflictRepository,
                                    JulesSessionRepository julesSessionRepository,
                                    TaskRepository taskRepository,
                                    ProjectRepository projectRepository) {
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/conflict-dpmo")
    public Map<String, Object> getConflictDpmo() {
        List<PrReviewEntity> allReviews = prReviewRepository.findAll();
        List<TaskConflictEntity> allConflicts = taskConflictRepository.findAll();

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // Pre-build lookups
        Map<UUID, UUID> sessionToProjectMap = new HashMap<>();
        List<JulesSessionEntity> allSessions = julesSessionRepository.findAll();
        for (JulesSessionEntity session : allSessions) {
            if (session.getTaskId() != null) {
                taskRepository.findById(session.getTaskId()).ifPresent(task -> {
                    if (task.getProject() != null) {
                        sessionToProjectMap.put(session.getId(), task.getProject().getId());
                    }
                });
            }
        }

        // Calculate all time metrics
        long mergedAllTime = allReviews.stream().filter(r -> Boolean.TRUE.equals(r.getMerged())).count();
        long conflictsAllTime = allConflicts.size();
        long totalAttemptsAllTime = mergedAllTime + conflictsAllTime;
        double dpmoAllTime = totalAttemptsAllTime > 0 ? (double) conflictsAllTime / totalAttemptsAllTime * 1_000_000 : 0;

        // Calculate last 7 days metrics
        long mergedLast7Days = allReviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getMerged()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();
        long conflictsLast7Days = allConflicts.stream()
                .filter(c -> c.getDetectedAt() != null && c.getDetectedAt().isAfter(sevenDaysAgo))
                .count();
        long totalAttemptsLast7Days = mergedLast7Days + conflictsLast7Days;
        double dpmoLast7Days = totalAttemptsLast7Days > 0 ? (double) conflictsLast7Days / totalAttemptsLast7Days * 1_000_000 : 0;

        // Calculate project breakdown
        List<ProjectEntity> projects = projectRepository.findAll();
        List<Map<String, Object>> byProjectList = new ArrayList<>();

        for (ProjectEntity project : projects) {
            UUID projId = project.getId();

            long projMergedAll = allReviews.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getMerged()))
                    .filter(r -> projId.equals(sessionToProjectMap.get(r.getJulesSessionId())))
                    .count();
            long projConflictsAll = allConflicts.stream()
                    .filter(c -> c.getTask() != null && c.getTask().getProject() != null && projId.equals(c.getTask().getProject().getId()))
                    .count();
            long projTotalAttemptsAll = projMergedAll + projConflictsAll;
            double projDpmoAll = projTotalAttemptsAll > 0 ? (double) projConflictsAll / projTotalAttemptsAll * 1_000_000 : 0;

            long projMerged7 = allReviews.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getMerged()))
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
}
