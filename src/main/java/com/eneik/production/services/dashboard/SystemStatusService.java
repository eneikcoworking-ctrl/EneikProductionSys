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
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final WishlistRepository wishlistRepository;
    private final EmsMetricsService emsMetricsService;
    private final GoogleAiResourceService googleAiResourceService;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker;

    public SystemStatusService(SystemSettingsService settingsService,
                               AccountRepository accountRepository,
                               TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               LinearIssueMetadataRepository linearIssueMetadataRepository,
                               JdbcTemplate jdbcTemplate,
                               PrReviewRepository prReviewRepository,
                               TaskConflictRepository taskConflictRepository,
                               WishlistRepository wishlistRepository,
                               EmsMetricsService emsMetricsService,
                               GoogleAiResourceService googleAiResourceService,
                               com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker,
                               com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker) {
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.linearIssueMetadataRepository = linearIssueMetadataRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.wishlistRepository = wishlistRepository;
        this.emsMetricsService = emsMetricsService;
        this.googleAiResourceService = googleAiResourceService;
        this.systemProgressTracker = systemProgressTracker;
        this.aiHealthTracker = aiHealthTracker;
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
        status.put("emsMetrics", safeSection(() -> emsMetrics(projectId)));
        status.put("sixSigma", safeSection(() -> sixSigma(projectId)));
        status.put("aiResources", safeSection(googleAiResourceService::resourceMatrix));
        status.put("systemHealth", safeSection(this::systemHealth));
        status.put("aiHealth", safeSection(aiHealthTracker::snapshot));
        return status;
    }

    public Map<String, Object> getStatus() {
        return getStatus(null);
    }

    private Map<String, Object> accounts(UUID projectId) {
        List<AccountEntity> accounts = accountRepository.findAll();
        if (projectId != null) {
            accounts = accounts.stream()
                    .filter(a -> a.getStatus() != AccountStatus.decommissioned)
                    .filter(a -> a.getCurrentProjectId() == null || projectId.equals(a.getCurrentProjectId()))
                    .collect(Collectors.toList());
        }
        Map<String, Long> summary = accounts.stream()
                .collect(Collectors.groupingBy(account -> account.getStatus().name(), Collectors.counting()));
        long decommissioned = summary.getOrDefault(AccountStatus.decommissioned.name(), 0L);
        long dailyLimited = summary.getOrDefault(AccountStatus.daily_limited.name(), 0L);
        long apiBlocked = summary.getOrDefault(AccountStatus.api_blocked.name(), 0L);
        long operational = accounts.size() - decommissioned;
        long effectiveOperational = operational - dailyLimited - apiBlocked - summary.getOrDefault(AccountStatus.offline.name(), 0L);
        long apiKeyConfigured = accounts.stream()
                .filter(account -> account.getStatus() != AccountStatus.decommissioned)
                .filter(account -> account.getApiKey() != null && !account.getApiKey().isBlank())
                .count();

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("total", accounts.size());
        section.put("operational", operational);
        section.put("effectiveOperational", Math.max(0, effectiveOperational));
        section.put("apiKeyConfigured", apiKeyConfigured);
        section.put("idle", summary.getOrDefault(AccountStatus.idle.name(), 0L));
        section.put("busy", summary.getOrDefault(AccountStatus.busy.name(), 0L));
        section.put("offline", summary.getOrDefault(AccountStatus.offline.name(), 0L));
        section.put("dailyLimited", dailyLimited);
        section.put("apiBlocked", apiBlocked);
        section.put("decommissioned", decommissioned);
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
        long passedChecks = 0;
        long failedChecks = 0;
        Map<String, CtqAccumulator> ctq = new LinkedHashMap<>();
        List<Map<String, Object>> defectItems = new ArrayList<>();

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
                    String checkName = check.path("name").asText("unknown_check");
                    boolean passed = check.path("passed").asBoolean(false);
                    CtqAccumulator item = ctq.computeIfAbsent(checkName, key -> new CtqAccumulator(key, "quality_gate"));
                    item.opportunities++;
                    if (!check.path("passed").asBoolean()) {
                        totalDefects++;
                        failedChecks++;
                        item.defects++;
                        if (defectItems.size() < 20) {
                            Map<String, Object> defect = new LinkedHashMap<>();
                            defect.put("taskId", task.getId());
                            defect.put("roleTag", task.getRole() == null ? "unknown-role" : task.getRole().getTag());
                            defect.put("checkName", checkName);
                            defect.put("failureReasons", failureReasons(check));
                            defect.put("taskDescription", truncate(task.getDescription(), 220));
                            defectItems.add(defect);
                        }
                    } else {
                        passedChecks++;
                    }
                }
            }
        }

        double dpmo = totalOpportunities == 0 ? 0 : (double) totalDefects / totalOpportunities * 1_000_000;
        double yieldRate = yieldRate(totalDefects, totalOpportunities);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("totalAttempts", totalAttempts);
        section.put("totalOpportunities", totalOpportunities);
        section.put("defects", totalDefects);
        section.put("passedChecks", passedChecks);
        section.put("failedChecks", failedChecks);
        section.put("dpmo", round(dpmo));
        section.put("yieldRate", round(yieldRate));
        section.put("sigmaLevel", round(sigmaLevel(dpmo)));
        section.put("firstPassYield", totalAttempts == 0 ? 0.0 : round(tasks.stream()
                .filter(task -> task.getQualityGateReport() != null && task.getQualityGateReport().has("checks"))
                .filter(TaskEntity::isQualityGatePassed)
                .count() / (double) totalAttempts));
        long qualityDefectTotal = totalDefects;
        section.put("ctqBreakdown", ctq.values().stream()
                .map(acc -> ctqMap(acc, qualityDefectTotal))
                .sorted((a, b) -> Long.compare(((Number) b.get("defects")).longValue(), ((Number) a.get("defects")).longValue()))
                .toList());
        section.put("defectItems", defectItems);
        return section;
    }

    private Map<String, Object> systemHealth() {
        long minutesSinceProgress = java.time.Duration.between(
                systemProgressTracker.lastProgressAt(), java.time.Instant.now()).toMinutes();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("lastProgressAt", systemProgressTracker.lastProgressAt());
        section.put("minutesSinceProgress", minutesSinceProgress);
        section.put("status", settingsService.effectiveValue("system_stall_status"));
        return section;
    }

    // Same marker EmsMetricsService.isSystemMetaTask() uses: compiler/falsification-audit/review-fallback/
    // design-review/coverage-audit carrier tasks are dispatched under the orchestrator role and never
    // produce user-facing work. This section feeds the Metrics tab's "System Pipeline" chart (task status
    // breakdown), which the operator explicitly wants scoped to real work only (2026-07-21: "меня не
    // интересуют задачи без кода или без полезного для пользователя контента") - counting carrier tasks
    // here would silently reintroduce the exact debris this directive was about.
    private boolean isSystemMetaTask(TaskEntity task) {
        return task.getPayload() != null && task.getPayload().has("taskType");
    }

    private Map<String, Object> tasks(UUID projectId) {
        List<TaskEntity> allTasks = projectId != null
                ? taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : taskRepository.findAll();
        List<TaskEntity> realWorkTasks = allTasks.stream().filter(t -> !isSystemMetaTask(t)).toList();

        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, realWorkTasks.stream().filter(t -> t.getStatus() == status).count());
        }
        Map<String, Object> section = new LinkedHashMap<>();
        counts.forEach((status, count) -> section.put(status.name(), count));
        return section;
    }

    private Object emsMetrics(UUID projectId) {
        List<TaskEntity> tasks = projectId == null
                ? taskRepository.findAll()
                : taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        var wishlist = projectId == null
                ? wishlistRepository.findAll()
                : wishlistRepository.findByProjectId(projectId);
        return emsMetricsService.build(tasks, wishlist);
    }

    private Map<String, Object> sixSigma(UUID projectId) {
        Map<String, Object> quality = qualityGate(projectId);
        Map<String, Object> conflicts = conflictDpmo(projectId);

        long qualityOpportunities = longValue(quality.get("totalOpportunities"));
        long qualityDefects = longValue(quality.get("defects"));
        long mergeOpportunities = longValue(conflicts.get("totalMergeAttempts"));
        long mergeDefects = longValue(conflicts.get("conflicts"));
        long totalOpportunities = qualityOpportunities + mergeOpportunities;
        long totalDefects = qualityDefects + mergeDefects;
        double dpmo = totalOpportunities == 0 ? 0.0 : (double) totalDefects / totalOpportunities * 1_000_000.0;
        double qualityYield = yieldRate(qualityDefects, qualityOpportunities);
        double mergeYield = yieldRate(mergeDefects, mergeOpportunities);
        double rolledThroughputYield = totalOpportunities == 0 ? 0.0 : qualityYield * mergeYield;

        List<Map<String, Object>> pareto = new ArrayList<>();
        Object ctqBreakdown = quality.get("ctqBreakdown");
        if (ctqBreakdown instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map) {
                    pareto.add(copyMap(map));
                }
            }
        }
        Object conflictTypes = conflicts.get("conflictTypePareto");
        if (conflictTypes instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map) {
                    Map<String, Object> row = copyMap(map);
                    row.put("source", "merge_conflict");
                    row.put("ctq", "Merge Conflict: " + row.getOrDefault("name", "unknown"));
                    row.put("opportunities", mergeOpportunities);
                    row.put("dpmo", mergeOpportunities == 0 ? 0.0 : round((longValue(row.get("defects")) / (double) mergeOpportunities) * 1_000_000.0));
                    pareto.add(row);
                }
            }
        }
        pareto.sort(Comparator.comparingLong(row -> -longValue(row.get("defects"))));

        long activeConflicts = listSize(conflicts.get("activeConflicts"));
        long copqProxy = qualityDefects + (mergeDefects * 3) + (activeConflicts * 5);

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("method", "Six Sigma DMAIC control view");
        section.put("unit", "project production opportunity");
        section.put("totalOpportunities", totalOpportunities);
        section.put("totalDefects", totalDefects);
        section.put("dpmo", round(dpmo));
        section.put("yieldRate", round(yieldRate(totalDefects, totalOpportunities)));
        section.put("sigmaLevel", round(sigmaLevel(dpmo)));
        section.put("qualityGateSigma", quality.get("sigmaLevel"));
        section.put("mergeSigma", conflicts.get("sigmaLevel"));
        section.put("firstPassYield", quality.get("firstPassYield"));
        section.put("rolledThroughputYield", round(rolledThroughputYield));
        section.put("copqProxy", copqProxy);
        section.put("ctqPareto", pareto.stream().limit(8).toList());
        section.put("statusLabel", sixSigmaStatus(sigmaLevel(dpmo), totalOpportunities, activeConflicts));
        section.put("interpretation", sixSigmaInterpretation(totalOpportunities, dpmo, activeConflicts));
        section.put("recommendedAction", sixSigmaAction(pareto, activeConflicts));
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
        double yieldAllTime = yieldRate(conflictsAllTime, totalAttemptsAllTime);

        long mergedLast7Days = allReviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getMerged()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();
        long conflictsLast7Days = allConflicts.stream()
                .filter(c -> c.getDetectedAt() != null && c.getDetectedAt().isAfter(sevenDaysAgo))
                .count();
        long totalAttemptsLast7Days = mergedLast7Days + conflictsLast7Days;
        double dpmoLast7Days = totalAttemptsLast7Days > 0 ? (double) conflictsLast7Days / totalAttemptsLast7Days * 1_000_000 : 0;
        double yieldLast7Days = yieldRate(conflictsLast7Days, totalAttemptsLast7Days);

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

        List<Map<String, Object>> conflictTypePareto = pareto(allConflicts.stream()
                .map(conflict -> blankToUnknown(conflict.getConflictType()))
                .toList(), totalAttemptsAllTime);
        List<Map<String, Object>> resolutionStatusPareto = pareto(allConflicts.stream()
                .map(conflict -> blankToUnknown(conflict.getResolutionStatus()))
                .toList(), totalAttemptsAllTime);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dpmo", round(dpmoAllTime));
        data.put("dpmoLast7Days", round(dpmoLast7Days));
        data.put("totalMergeAttempts", totalAttemptsAllTime);
        data.put("conflicts", conflictsAllTime);
        data.put("yieldRate", round(yieldAllTime));
        data.put("sigmaLevel", round(sigmaLevel(dpmoAllTime)));
        data.put("last7Days", Map.of(
                "totalMergeAttempts", totalAttemptsLast7Days,
                "conflicts", conflictsLast7Days,
                "dpmo", round(dpmoLast7Days),
                "yieldRate", round(yieldLast7Days),
                "sigmaLevel", round(sigmaLevel(dpmoLast7Days))
        ));
        data.put("conflictTypePareto", conflictTypePareto);
        data.put("resolutionStatusPareto", resolutionStatusPareto);
        data.put("activeConflicts", activeList);
        return data;
    }

    private List<String> failureReasons(JsonNode check) {
        List<String> reasons = new ArrayList<>();
        JsonNode node = check.path("failureReasons");
        if (node.isArray()) {
            for (JsonNode reason : node) {
                reasons.add(reason.asText(""));
            }
        }
        return reasons;
    }

    private Map<String, Object> ctqMap(CtqAccumulator acc, long totalDefects) {
        double dpmo = acc.opportunities == 0 ? 0.0 : (acc.defects / (double) acc.opportunities) * 1_000_000.0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", acc.source);
        row.put("ctq", acc.name);
        row.put("name", acc.name);
        row.put("opportunities", acc.opportunities);
        row.put("defects", acc.defects);
        row.put("defectShare", totalDefects == 0 ? 0.0 : round(acc.defects / (double) totalDefects));
        row.put("dpmo", round(dpmo));
        row.put("yieldRate", round(yieldRate(acc.defects, acc.opportunities)));
        row.put("sigmaLevel", round(sigmaLevel(dpmo)));
        return row;
    }

    private List<Map<String, Object>> pareto(List<String> values, long opportunities) {
        Map<String, Long> counts = values.stream()
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("defects", entry.getValue());
                    row.put("opportunities", opportunities);
                    row.put("dpmo", opportunities == 0 ? 0.0 : round((entry.getValue() / (double) opportunities) * 1_000_000.0));
                    return row;
                })
                .toList();
    }

    private String sixSigmaStatus(double sigma, long opportunities, long activeConflicts) {
        if (opportunities == 0) {
            return "no_data";
        }
        if (activeConflicts > 0 || sigma < 3.0) {
            return "critical";
        }
        if (sigma < 4.0) {
            return "improve";
        }
        if (sigma < 5.0) {
            return "controlled";
        }
        return "excellent";
    }

    private String sixSigmaInterpretation(long opportunities, double dpmo, long activeConflicts) {
        if (opportunities == 0) {
            return "No measurable CTQ opportunities are available yet. Run quality gates and PR reviews before interpreting Six Sigma health.";
        }
        if (activeConflicts > 0) {
            return "The process has active merge-conflict defects. Treat them as escaped integration defects before adding new feature scope.";
        }
        if (dpmo >= 100_000) {
            return "The process is below a stable Six Sigma control band. Use DMAIC: define the leading CTQ defect, remove its root cause, then remeasure.";
        }
        if (dpmo >= 10_000) {
            return "The process is improving but still loses quality at a visible rate. Prioritize the Pareto-leading CTQ.";
        }
        return "The current measured process is controlled for the visible CTQs. Continue monitoring sample size and trend.";
    }

    private String sixSigmaAction(List<Map<String, Object>> pareto, long activeConflicts) {
        if (activeConflicts > 0) {
            return "Resolve active merge conflicts first; they are live escaped defects and distort throughput.";
        }
        if (pareto.isEmpty()) {
            return "Collect more CTQ data by running task quality gates and PR review cycles.";
        }
        Map<String, Object> top = pareto.get(0);
        return "Run DMAIC on top CTQ: " + top.getOrDefault("ctq", top.getOrDefault("name", "unknown")) + ".";
    }

    private double yieldRate(long defects, long opportunities) {
        if (opportunities <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - defects / (double) opportunities));
    }

    private double sigmaLevel(double dpmo) {
        if (dpmo <= 0.0) {
            return 6.0;
        }
        if (dpmo >= 1_000_000.0) {
            return 0.0;
        }
        double yield = Math.max(0.000001, Math.min(0.999999, 1.0 - dpmo / 1_000_000.0));
        return Math.max(0.0, Math.min(6.0, inverseNormalCdf(yield) + 1.5));
    }

    private double inverseNormalCdf(double p) {
        double low = -6.0;
        double high = 6.0;
        for (int i = 0; i < 80; i++) {
            double mid = (low + high) / 2.0;
            if (normalCdf(mid) < p) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    private double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private double erf(double x) {
        double sign = Math.signum(x);
        double abs = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * abs);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-abs * abs);
        return sign * y;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class CtqAccumulator {
        private final String name;
        private final String source;
        private long opportunities;
        private long defects;

        private CtqAccumulator(String name, String source) {
            this.name = name;
            this.source = source;
        }
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
