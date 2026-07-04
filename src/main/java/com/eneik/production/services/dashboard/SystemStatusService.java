package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.TaskRepository;
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
import java.util.stream.Collectors;

@Service
public class SystemStatusService {

    private final SystemSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final LinearIssueMetadataRepository linearIssueMetadataRepository;
    private final JdbcTemplate jdbcTemplate;

    public SystemStatusService(SystemSettingsService settingsService,
                               AccountRepository accountRepository,
                               TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               LinearIssueMetadataRepository linearIssueMetadataRepository,
                               JdbcTemplate jdbcTemplate) {
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.linearIssueMetadataRepository = linearIssueMetadataRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("integrations", safeSection(() -> settingsService.listSettings()));
        status.put("accounts", safeSection(this::accounts));
        status.put("githubAccess", safeSection(this::latestGithubAccess));
        status.put("linearCompleteness", safeSection(this::linearCompleteness));
        status.put("julesSessions", safeSection(this::julesSessions));
        status.put("qualityGate", safeSection(this::qualityGate));
        status.put("tasks", safeSection(this::tasks));
        return status;
    }

    private Map<String, Object> accounts() {
        List<AccountEntity> accounts = accountRepository.findAll();
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

    private Map<String, Object> linearCompleteness() {
        List<TaskEntity> tasksWithLinear = taskRepository.findAll().stream()
                .filter(task -> task.getLinearIssueId() != null && !task.getLinearIssueId().isBlank())
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

    private Map<String, Object> julesSessions() {
        List<JulesSessionEntity> sessions = julesSessionRepository.findAll();
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

    private Map<String, Object> qualityGate() {
        long totalAttempts = 0;
        long totalOpportunities = 0;
        long totalDefects = 0;

        for (TaskEntity task : taskRepository.findAll()) {
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

    private Map<String, Object> tasks() {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, taskRepository.countByStatus(status));
        }
        Map<String, Object> section = new LinkedHashMap<>();
        counts.forEach((status, count) -> section.put(status.name(), count));
        return section;
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
