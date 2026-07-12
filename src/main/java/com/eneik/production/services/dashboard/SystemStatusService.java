package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SystemStatusService {

    private final SystemSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final LinearIssueMetadataRepository linearIssueMetadataRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PrReviewRepository prReviewRepository;
    private final TaskConflictRepository taskConflictRepository;

    public SystemStatusService(SystemSettingsService settingsService,
                               AccountRepository accountRepository,
                               TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               LinearIssueMetadataRepository linearIssueMetadataRepository,
                               JdbcTemplate jdbcTemplate,
                               PrReviewRepository prReviewRepository,
                               TaskConflictRepository taskConflictRepository) {
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.linearIssueMetadataRepository = linearIssueMetadataRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
    }

    public Map<String, Object> getStatus(UUID projectId) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("integrations", safeSection(() -> settingsService.listSettings()));
        status.put("accounts", safeSection(() -> accounts(projectId)));
        status.put("githubAccess", safeSection(this::latestGithubAccess));
        status.put("linearCompleteness", safeSection(() -> linearCompleteness(projectId)));
        status.put("julesSessions", safeSection(() -> julesSessions(projectId)));
        status.put("qualityGate", safeSection(() -> qualityGate(projectId)));
        status.put("tasks", safeSection(() -> tasks(projectId)));
        status.put("conflictDpmo", safeSection(() -> conflictDpmo(projectId)));
        return status;
    }

    public Map<String, Object> getStatus() {
        return getStatus(null);
    }

    private Map<String, Object> accounts(UUID projectId) {
        List<AccountEntity> accounts = accountRepository.findAll();
        if (projectId != null) {
            accounts = accounts.stream()
                    .filter(a -> projectId.equals(a.getCurrentProjectId()))
                    .collect(Collectors.toList());
        }
        Map<String, Long> summary = accounts.stream()
                .collect(Collectors.groupingBy(account -> account.getStatus().name(), Collectors.counting()));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("total", accounts.size());
        section.put("idle", summary.getOrDefault(AccountStatus.idle.name(), 0L));
        section.put("busy", summary.getOrDefault(AccountStatus.busy.name(), 0L));
        section.put("offline", summary.getOrDefault(AccountStatus.offline.name(), 0L));
        section.put("items", accounts.stream().map(this::accountItem).toList());
        return section;
    }

    private Map<String, Object> accountItem(AccountEntity account) {
        String masked = null;
        if (account.getApiKey() != null && !account.getApiKey().isBlank()) {
            String raw = account.getApiKey();
            masked = raw.length() > 8 ? raw.substring(0, 4) + "..." + raw.substring(raw.length() - 4) : "****";
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", account.getId());
        item.put("name", account.getName());
        item.put("status", account.getStatus());
        item.put("currentProjectId", account.getCurrentProjectId());
        item.put("capabilities", account.getCapabilities());
        item.put("lastHeartbeat", account.getLastHeartbeat());
        item.put("apiKeyMasked", masked);
        item.put("githubUsername", account.getGithubUsername());
        item.put("enabled", account.isEnabled());
        return item;
    }

    private Map<String, Object> latestGithubAccess() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM github_access_status ORDER BY checked_at DESC LIMIT 1"
        );
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("latest", rows.isEmpty() ? null : rows.get(0));
        return section;
    }

    private Map<String, Object> linearCompleteness(UUID projectId) {
        List<TaskEntity> tasksWithLinear = taskRepository.findAll().stream()
                .filter(task -> task.getLinearIssueId() != null && !task.getLinearIssueId().isBlank())
                .filter(task -> projectId == null || (task.getProject() != null && projectId.equals(task.getProject().getId())))
                .toList();

        List<Map<String, Object>> reports = new ArrayList<>();
        int fullyComplete = 0;

        for (TaskEntity task : tasksWithLinear) {
            List<String> missingFields = new ArrayList<>();
            LinearIssueMetadataEntity metadata = linearIssueMetadataRepository.findById(task.getId()).orElse(null);

            if (metadata == null) {
                missingFields.add("assignee");
                missingFields.add("pr_url");
                missingFields.add("blockers");
                missingFields.add("dod_text");
            } else {
                if (metadata.getPrUrl() == null || metadata.getPrUrl().isBlank()) {
                    missingFields.add("pr_url");
                }
                if (metadata.getBlockers() == null || metadata.getBlockers().isBlank()) {
                    missingFields.add("blockers");
                }
                if (metadata.getDodText() == null || metadata.getDodText().isBlank()) {
                    missingFields.add("dod_text");
                }
            }

            if (missingFields.isEmpty()) {
                fullyComplete++;
            }

            Map<String, Object> taskReport = new LinkedHashMap<>();
            taskReport.put("taskId", task.getId());
            taskReport.put("missingFields", missingFields);
            reports.add(taskReport);
        }

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("totalIssues", tasksWithLinear.size());
        section.put("fullyComplete", fullyComplete);
        section.put("completeness_rate", tasksWithLinear.isEmpty() ? 0 : (double) fullyComplete / tasksWithLinear.size());
        section.put("issues", reports);
        return section;
    }

    private Map<String, Object> julesSessions(UUID projectId) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findAll();
        if (projectId != null) {
            List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            Set<UUID> projectTaskIds = projectTasks.stream().map(TaskEntity::getId).collect(Collectors.toSet());
            sessions = sessions.stream()
                    .filter(s -> projectTaskIds.contains(s.getTaskId()))
                    .collect(Collectors.toList());
        }
        Map<String, Long> counts = sessions.stream()
                .collect(Collectors.groupingBy(JulesSessionEntity::getStatus, Collectors.counting()));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("total", sessions.size());
        section.put("queued", counts.getOrDefault("queued", 0L));
        section.put("running", counts.getOrDefault("running", 0L));
        section.put("pr_opened", counts.getOrDefault("pr_opened", 0L));
        section.put("failed", counts.getOrDefault("failed", 0L));
        section.put("stuck", counts.getOrDefault("stuck", 0L));
        return section;
    }

    private Map<String, Object> qualityGate(UUID projectId) {
        long totalAttempts = 0;
        long totalOpportunities = 0;
        long totalDefects = 0;

        List<TaskEntity> tasks = taskRepository.findAll();
        if (projectId != null) {
            tasks = tasks.stream()
                    .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                    .collect(Collectors.toList());
        }

        for (TaskEntity task : tasks) {
            JsonNode report = task.getQualityGateReport();
            if (report != null && report.has("checks")) {
                totalAttempts++;
                JsonNode checks = report.get("checks");
                totalOpportunities += checks.size();
                for (JsonNode check : checks) {
                    if (!check.path("passed").asBoolean()) {
                        totalDefects++;
                    }
                }
            }
        }

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("totalAttempts", totalAttempts);
        section.put("totalOpportunities", totalOpportunities);
        section.put("defects", totalDefects);
        section.put("dpmo", totalOpportunities == 0 ? 0 : (double) totalDefects / totalOpportunities * 1_000_000);
        return section;
    }

    private Map<String, Object> tasks(UUID projectId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            if (projectId != null) {
                counts.put(status, taskRepository.countByProjectIdAndStatus(projectId, status));
            } else {
                counts.put(status, taskRepository.countByStatus(status));
            }
        }
        Map<String, Object> section = new LinkedHashMap<>();
        counts.forEach((status, count) -> section.put(status.name(), count));
        return section;
    }

    private Map<String, Object> conflictDpmo(UUID projectId) {
        List<PrReviewEntity> allReviews = prReviewRepository.findAll();
        List<TaskConflictEntity> allConflicts = taskConflictRepository.findAll();

        if (projectId != null) {
            List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            Set<UUID> projectTaskIds = projectTasks.stream().map(TaskEntity::getId).collect(Collectors.toSet());

            List<JulesSessionEntity> projectSessions = julesSessionRepository.findAll().stream()
                    .filter(s -> projectTaskIds.contains(s.getTaskId()))
                    .collect(Collectors.toList());
            Set<UUID> projectSessionIds = projectSessions.stream().map(JulesSessionEntity::getId).collect(Collectors.toSet());

            allReviews = allReviews.stream()
                    .filter(r -> projectSessionIds.contains(r.getJulesSessionId()))
                    .collect(Collectors.toList());

            allConflicts = allConflicts.stream()
                    .filter(c -> c.getTask() != null && c.getTask().getProject() != null && projectId.equals(c.getTask().getProject().getId()))
                    .collect(Collectors.toList());
        }

        java.time.Instant sevenDaysAgo = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);

        long mergedAllTime = allReviews.stream().filter(r -> Boolean.TRUE.equals(r.getMerged())).count();
        long conflictsAllTime = allConflicts.size();
        long totalAttemptsAllTime = mergedAllTime + conflictsAllTime;
        double dpmoAllTime = totalAttemptsAllTime > 0 ? (double) conflictsAllTime / totalAttemptsAllTime * 1_000_000 : 0;

        long mergedLast7Days = allReviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getMerged()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();
        long conflictsLast7Days = allConflicts.stream()
                .filter(c -> c.getDetectedAt() != null && c.getDetectedAt().isAfter(sevenDaysAgo))
                .count();
        long totalAttemptsLast7Days = mergedLast7Days + conflictsLast7Days;
        double dpmoLast7Days = totalAttemptsLast7Days > 0 ? (double) conflictsLast7Days / totalAttemptsLast7Days * 1_000_000 : 0;

        List<TaskConflictEntity> activeConflicts = allConflicts.stream()
                .filter(c -> !"auto_resolved".equals(c.getResolutionStatus()))
                .collect(Collectors.toList());

        List<Map<String, Object>> activeList = new java.util.ArrayList<>();
        for (TaskConflictEntity conflict : activeConflicts) {
            Map<String, Object> cMap = new java.util.LinkedHashMap<>();
            cMap.put("id", conflict.getId());
            cMap.put("taskId", conflict.getTask().getId());
            cMap.put("taskDescription", conflict.getTask().getDescription());
            cMap.put("prUrl", conflict.getPrUrl());
            cMap.put("detectedAt", conflict.getDetectedAt());
            cMap.put("conflictType", conflict.getConflictType());
            cMap.put("resolutionAttempts", conflict.getResolutionAttempts());
            cMap.put("resolutionStatus", conflict.getResolutionStatus());
            cMap.put("conflictingFiles", conflict.getConflictingFiles());
            activeList.add(cMap);
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dpmo", dpmoAllTime);
        data.put("dpmoLast7Days", dpmoLast7Days);
        data.put("totalMergeAttempts", totalAttemptsAllTime);
        data.put("conflicts", conflictsAllTime);
        data.put("activeConflicts", activeList);
        return data;
    }

    private Object safeSection(SectionSupplier supplier) {
        try {
            Object data = supplier.get();
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("available", true);
            section.put("data", data);
            return section;
        } catch (Exception e) {
            Map<String, Object> section = new HashMap<>();
            section.put("available", false);
            section.put("data", null);
            section.put("error", e.getClass().getSimpleName());
            return section;
        }
    }

    @FunctionalInterface
    private interface SectionSupplier {
        Object get();
    }
}
