package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.googleai.GoogleAiResourceService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;
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
    private final ProjectRepository projectRepository;
    private final EmsMetricsService emsMetricsService;
    private final GoogleAiResourceService googleAiResourceService;
    private final GitHubApiBudgetService githubApiBudgetService;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker;
    private final Environment environment;
    private final com.eneik.production.services.audit.SixSigmaAuditService sixSigmaAuditService;

    public SystemStatusService(SystemSettingsService settingsService,
                               AccountRepository accountRepository,
                               TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               LinearIssueMetadataRepository linearIssueMetadataRepository,
                               JdbcTemplate jdbcTemplate,
                               PrReviewRepository prReviewRepository,
                               TaskConflictRepository taskConflictRepository,
                               WishlistRepository wishlistRepository,
                               ProjectRepository projectRepository,
                               EmsMetricsService emsMetricsService,
                               GoogleAiResourceService googleAiResourceService,
                               GitHubApiBudgetService githubApiBudgetService,
                               com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker,
                               com.eneik.production.services.monitor.AiHealthTracker aiHealthTracker,
                               Environment environment,
                               com.eneik.production.services.audit.SixSigmaAuditService sixSigmaAuditService) {
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.linearIssueMetadataRepository = linearIssueMetadataRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.prReviewRepository = prReviewRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.wishlistRepository = wishlistRepository;
        this.projectRepository = projectRepository;
        this.emsMetricsService = emsMetricsService;
        this.googleAiResourceService = googleAiResourceService;
        this.githubApiBudgetService = githubApiBudgetService;
        this.systemProgressTracker = systemProgressTracker;
        this.aiHealthTracker = aiHealthTracker;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.environment = environment;
    }

    public Map<String, Object> getStatus(UUID projectId) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("integrations", safeSection(() -> settingsService.listSettings()));
        status.put("accounts", safeSection(() -> accounts(projectId)));
        status.put("githubAccess", safeSection(this::latestGithubAccess));
        status.put("githubApiBudget", safeSection(() -> githubApiBudgetService.snapshot().asMap()));
        status.put("linearCompleteness", safeSection(() -> linearCompleteness(projectId)));
        status.put("julesSessions", safeSection(() -> julesSessions(projectId)));
        status.put("qualityGate", safeSection(() -> qualityGate(projectId)));
        status.put("tasks", safeSection(() -> tasks(projectId)));
        status.put("conflictDpmo", safeSection(() -> conflictDpmo(projectId)));
        status.put("emsMetrics", safeSection(() -> emsMetrics(projectId)));
        status.put("sixSigma", safeSection(() -> sixSigma(projectId)));
        status.put("aiResources", safeSection(googleAiResourceService::resourceMatrix));
        status.put("systemHealth", safeSection(this::systemHealth));
        status.put("operationalBlockers", safeSection(() -> operationalBlockers(projectId)));
        status.put("runtimeSource", safeSection(this::runtimeSource));
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
                    if (!check.path("passed").asBoolean()) {
                        totalDefects++;
                        failedChecks++;
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
        // 2026-08-08 (ML-update patch, Phase 1): ctqBreakdown now comes from SixSigmaAuditService's shared
        // computeCtqBreakdown - same per-check-name Pareto KaizenService's F1_KAIZEN_CTQ_TARGETING lever
        // reads, so the dashboard and the Kaizen proposal generator can never silently diverge (invariant #14).
        long qualityDefectTotal = totalDefects;
        section.put("ctqBreakdown", sixSigmaAuditService.computeCtqBreakdown(projectId).stream()
                .map(entry -> ctqMap(new CtqAccumulator(entry.checkName(), "quality_gate", entry.opportunities(), entry.defects()), qualityDefectTotal))
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

    private Map<String, Object> operationalBlockers(UUID projectId) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        String stallStatus = settingsService.effectiveValue("system_stall_status");
        if (stallStatus != null && !stallStatus.isBlank()
                && !Set.of("ok", "idle_no_actionable_work", "busy_with_actionable_work", "content_defect")
                .contains(stallStatus.toLowerCase(java.util.Locale.ROOT))) {
            blockers.add(blocker("system_status", stallStatus, "high",
                    "system_stall_status=" + stallStatus));
        }

        Object budget = githubApiBudgetService.snapshot().asMap();
        if (budget instanceof Map<?, ?> budgetMap) {
            Object available = budgetMap.get("available");
            Object status = budgetMap.get("status");
            if (Boolean.FALSE.equals(available) || "exhausted".equals(String.valueOf(status))) {
                blockers.add(blocker("github_api_budget", "github_rate_limited", "high",
                        "GitHub API budget is unavailable; GitHub-dependent truth must wait."));
            }
        }

        List<ProjectEntity> projects = projectId == null
                ? projectRepository.findAll()
                : projectRepository.findById(projectId).map(List::of).orElse(List.of());
        List<JulesSessionEntity> allSessions = julesSessionRepository.findAll();
        for (ProjectEntity project : projects) {
            List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
            if (project.getStatus() == ProjectStatus.active && duplicateContent(projectTasks)) {
                blockers.add(blocker("duplicate_content", "content_defect", "critical",
                        "project=" + project.getName() + "; duplicate task content threshold reached"));
            }
            if (project.getStatus() != ProjectStatus.active) {
                long staleTasks = projectTasks.stream().filter(this::isNonTerminalTask).count();
                long staleWishlists = wishlistRepository.findByProjectId(project.getId()).stream()
                        .filter(com.eneik.production.models.persistence.WishlistEntity::movable)
                        .count();
                Set<UUID> taskIds = projectTasks.stream().map(TaskEntity::getId).collect(Collectors.toSet());
                long staleSessions = allSessions.stream()
                        .filter(session -> taskIds.contains(session.getTaskId()))
                        .filter(session -> Set.of("queued", "running", "pr_opened", "revising", "stuck")
                                .contains(session.getStatus()))
                        .count();
                if (staleTasks > 0 || staleWishlists > 0 || staleSessions > 0) {
                    blockers.add(blocker("terminal_project_wip", project.getStatus().name(), "medium",
                            "project=" + project.getName()
                                    + "; nonTerminalTasks=" + staleTasks
                                    + "; pendingOrCompilingWishlists=" + staleWishlists
                                    + "; openSessions=" + staleSessions));
                }
            }
        }

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", blockers.isEmpty() ? "ok" : "blocked");
        section.put("count", blockers.size());
        section.put("items", blockers);
        section.put("rule", "multi-blocker list; scalar system_stall_status is retained only for backward compatibility");
        return section;
    }

    private Map<String, Object> blocker(String type, String status, String severity, String evidence) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("status", status);
        item.put("severity", severity);
        item.put("evidence", evidence);
        return item;
    }

    private boolean isNonTerminalTask(TaskEntity task) {
        return task.getStatus() != TaskStatus.done
                && task.getStatus() != TaskStatus.failed
                && task.getStatus() != TaskStatus.spike_completed;
    }

    private boolean duplicateContent(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .limit(30)
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
        return counts.values().stream().anyMatch(count -> count >= 3);
    }

    private String duplicateKey(TaskEntity task) {
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle;
            }
        }
        return task.getDescription();
    }

    private Map<String, Object> runtimeSource() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("gitSha", buildProperty("git-sha"));
        section.put("gitDirty", buildProperty("git-dirty"));
        section.put("buildTime", buildProperty("time"));
        section.put("knownRevision", !"unknown".equals(buildProperty("git-sha")));
        return section;
    }

    private String buildProperty(String name) {
        String value = environment.getProperty("eneik.build." + name);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    // Same marker EmsMetricsService.isSystemMetaTask() uses: compiler/falsification-audit/review-fallback/
    // design-review/coverage-audit carrier tasks are dispatched under the orchestrator role and never
    // produce user-facing work. This section feeds the Metrics tab's "System Pipeline" chart (task status
    // breakdown), which the operator explicitly wants scoped to real work only (2026-07-21: "I am not
    // interested in tasks with no code and no content useful to a user") - counting carrier tasks
    // here would silently reintroduce the exact debris this directive was about.
    private boolean isSystemMetaTask(TaskEntity task) {
        return task != null && task.isCarrier();
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

            // By identity, not by dereference (2026-08-29, plan §4.26). The id set was already built two
            // statements above for sessions; the conflict filter walked the lazy task proxy instead and so
            // asked whether a REFERENCE was present rather than whether its REFERENT exists - the same
            // reading that killed the Kaizen cycle in SixSigmaAuditService against 92 conflict rows whose
            // tasks are gone.
            allConflicts = allConflicts.stream()
                    .filter(c -> projectTaskIds.contains(c.getTask().getId()))
                    .collect(Collectors.toList());
        }

        java.time.Instant sevenDaysAgo = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);

        long mergedAllTime = allReviews.stream().filter(r -> Boolean.TRUE.equals(r.getMerged())).count();
        long conflictsAllTime = allConflicts.size();
        long totalAttemptsAllTime = mergedAllTime + conflictsAllTime;
        double dpmoAllTime = totalAttemptsAllTime > 0 ? (double) conflictsAllTime / totalAttemptsAllTime * 1_000_000 : 0;
        Double yieldAllTime = yieldRate(conflictsAllTime, totalAttemptsAllTime);

        long mergedLast7Days = allReviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getMerged()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();
        long conflictsLast7Days = allConflicts.stream()
                .filter(c -> c.getDetectedAt() != null && c.getDetectedAt().isAfter(sevenDaysAgo))
                .count();
        long totalAttemptsLast7Days = mergedLast7Days + conflictsLast7Days;
        double dpmoLast7Days = totalAttemptsLast7Days > 0 ? (double) conflictsLast7Days / totalAttemptsLast7Days * 1_000_000 : 0;
        Double yieldLast7Days = yieldRate(conflictsLast7Days, totalAttemptsLast7Days);

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
        data.put("yieldRate", yieldAllTime == null ? null : round(yieldAllTime));
        data.put("sigmaLevel", round(sigmaLevel(dpmoAllTime)));
        Map<String, Object> last7DaysMap = new java.util.LinkedHashMap<>();
        last7DaysMap.put("totalMergeAttempts", totalAttemptsLast7Days);
        last7DaysMap.put("conflicts", conflictsLast7Days);
        last7DaysMap.put("dpmo", round(dpmoLast7Days));
        last7DaysMap.put("yieldRate", yieldLast7Days == null ? null : round(yieldLast7Days));
        last7DaysMap.put("sigmaLevel", round(sigmaLevel(dpmoLast7Days)));
        data.put("last7Days", last7DaysMap);
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

    // Nullable (not a primitive double) on purpose: 0 opportunities means "no data to measure yet," not
    // "0% yield" - conflating the two previously made this tile show the worst possible score for the
    // exact same condition sigmaLevel() already treats as the BEST possible score (6.0), a direct
    // contradiction on the same dashboard. Fixed 2026-07-24; callers must render null as "N/A", not "0%".
    private Double yieldRate(long defects, long opportunities) {
        if (opportunities <= 0) {
            return null;
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

        private CtqAccumulator(String name, String source, long opportunities, long defects) {
            this.name = name;
            this.source = source;
            this.opportunities = opportunities;
            this.defects = defects;
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
