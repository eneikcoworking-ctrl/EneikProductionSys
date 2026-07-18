package com.eneik.production.services.dashboard;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesRoleCapabilities;
import com.eneik.production.services.task.TaskTitleBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectOperationalContextService {
    private static final String APPROVAL_TOKEN = "CORE ARCHITECTURE VERIFIED. APPROVED.";

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;
    private final WishlistRepository wishlistRepository;
    private final AccountRepository accountRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final BottleneckDetectionService bottleneckDetectionService;
    private final SystemStatusService systemStatusService;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ObjectMapper objectMapper;
    private final EmsMetricsService emsMetricsService;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;

    public ProjectOperationalContextService(ProjectRepository projectRepository,
                                            TaskRepository taskRepository,
                                            JulesSessionRepository julesSessionRepository,
                                            PrReviewRepository prReviewRepository,
                                            WishlistRepository wishlistRepository,
                                            AccountRepository accountRepository,
                                            TaskConflictRepository taskConflictRepository,
                                            BottleneckDetectionService bottleneckDetectionService,
                                            SystemStatusService systemStatusService,
                                            GitHubPullRequestService gitHubPullRequestService,
                                            ObjectMapper objectMapper,
                                            EmsMetricsService emsMetricsService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.wishlistRepository = wishlistRepository;
        this.accountRepository = accountRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.bottleneckDetectionService = bottleneckDetectionService;
        this.systemStatusService = systemStatusService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.objectMapper = objectMapper;
        this.emsMetricsService = emsMetricsService;
    }

    @Transactional(readOnly = true)
    public ProjectOperationalContext build(UUID projectId, String fallbackProjectName) {
        ProjectEntity project = resolveProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        Set<UUID> taskIds = tasks.stream().map(TaskEntity::getId).collect(Collectors.toSet());
        Map<UUID, TaskEntity> tasksById = tasks.stream().collect(Collectors.toMap(TaskEntity::getId, Function.identity()));

        List<JulesSessionEntity> sessions = julesSessionRepository.findAll().stream()
                .filter(session -> taskIds.contains(session.getTaskId()))
                .sorted(Comparator.comparing(JulesSessionEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Set<UUID> sessionIds = sessions.stream().map(JulesSessionEntity::getId).collect(Collectors.toSet());

        List<PrReviewEntity> reviews = prReviewRepository.findAll().stream()
                .filter(review -> sessionIds.contains(review.getJulesSessionId()))
                .sorted(Comparator.comparing(PrReviewEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<WishlistEntity> wishlist = wishlistRepository.findByProjectId(project.getId()).stream()
                .sorted(Comparator.comparing(WishlistEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TaskConflictEntity> conflicts = taskConflictRepository.findAll().stream()
                .filter(conflict -> conflict.getTask() != null && taskIds.contains(conflict.getTask().getId()))
                .sorted(Comparator.comparing(TaskConflictEntity::getDetectedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<AccountEntity> accounts = accountRepository.findAll().stream()
                .filter(account -> account.getStatus() != AccountStatus.decommissioned)
                .filter(account -> account.getCurrentProjectId() == null || project.getId().equals(account.getCurrentProjectId()))
                .sorted(Comparator.comparing(AccountEntity::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        GitHubPullRequestService.PullRequestSnapshot pullRequests = gitHubPullRequestService.pullRequestSnapshot(project);
        PrStats prStats = prStats(pullRequests, reviews);
        Map<String, Object> systemStatus = systemStatusService.getStatus(project.getId());
        Object sixSigmaControl = augmentSixSigmaWithGithubPrDefects(sectionData(systemStatus.get("sixSigma")), pullRequests);

        Map<String, Object> factPack = new LinkedHashMap<>();
        factPack.put("scope", "selected_project_only");
        factPack.put("generatedAt", Instant.now().toString());
        factPack.put("project", projectFact(project, fallbackProjectName));
        factPack.put("githubPullRequestsLive", githubFact(pullRequests));
        factPack.put("tasks", taskFacts(tasks));
        factPack.put("emsMetrics", emsMetricsService.build(tasks, wishlist));
        factPack.put("julesSessions", sessionFacts(sessions, tasksById, accounts));
        factPack.put("databasePrReviews", reviewFacts(reviews, sessions, tasksById));
        factPack.put("wishlist", wishlistFacts(wishlist));
        factPack.put("accountsAvailableForProject", accountFacts(accounts, sessions));
        factPack.put("julesUniversalRoleCapacity", julesCapacityFacts(tasks, accounts, sessions));
        factPack.put("conflicts", conflictFacts(conflicts));
        factPack.put("bottlenecks", bottleneckFacts(project.getId()));
        factPack.put("sixSigmaControl", sixSigmaControl);
        factPack.put("systemStatusProjectOnly", systemStatus);
        factPack.put("rules", List.of(
                "No global system data is included in this fact pack.",
                "Every enabled Jules account is universal-role capable for all BARCAN-TAG-00..11 roles.",
                "BARCAN roles are thinking lenses and ownership tags, not permission boundaries. Never use a role as an excuse to avoid necessary Eneik Management System action.",
                "Do not diagnose missing role capability for Jules accounts; diagnose shared session slots, account enabled/status, stuck sessions, API errors, or dispatch failures instead.",
                "If the operator cannot name a concrete task, owner, and role for the next step, the correct action is to create or compile a precise English wishlist/work item and then orchestrate or dispatch when the user asked to act.",
                "Repository/environment boundary work is mandatory early project bootstrap work. Missing .git, empty workspace, unknown setup commands, or unclear frontend/backend boundaries are system work, not a human operator chore.",
                "sixSigmaControl is EMS production telemetry for the makers of the production system. It is not the project operator's execution concern unless the user explicitly asks about production quality.",
                "Recorded failed attempts are internal defect-counter evidence only. They are not active project work and should not be mentioned in normal project guidance.",
                "Use githubPullRequestsLive for current GitHub open/closed PR counts.",
                "Closed-but-unmerged PRs are already closed historical scrap/COPQ evidence. Never say they need to be closed again; only live open PRs are actionable.",
                "Use databasePrReviews for review decisions and merge results.",
                "If a fact is absent from this pack, say it is not available instead of guessing."
        ));

        return new ProjectOperationalContext(project.getId(), project.getName(), factPack, toJson(factPack), prStats);
    }

    private Optional<ProjectEntity> resolveProject(UUID projectId) {
        if (projectId != null) {
            return projectRepository.findById(projectId);
        }
        return projectRepository.findFirstByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
    }

    private Map<String, Object> projectFact(ProjectEntity project, String fallbackProjectName) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", project.getId());
        fact.put("name", project.getName() == null || project.getName().isBlank() ? fallbackProjectName : project.getName());
        fact.put("slug", project.getSlug());
        fact.put("status", project.getStatus());
        fact.put("repositoryName", project.getRepositoryName());
        fact.put("repositoryUrl", project.getRepositoryUrl());
        fact.put("defaultBranch", project.getDefaultBranch());
        fact.put("workspacePath", project.getWorkspacePath());
        fact.put("createdAt", text(project.getCreatedAt()));
        fact.put("acceptedAt", text(project.getAcceptedAt()));
        return fact;
    }

    private Object sectionData(Object section) {
        if (section instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return section;
    }

    private Map<String, Object> githubFact(GitHubPullRequestService.PullRequestSnapshot snapshot) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("available", snapshot.available());
        fact.put("owner", snapshot.owner());
        fact.put("repo", snapshot.repo());
        fact.put("error", snapshot.error());
        fact.put("openCount", snapshot.open().size());
        fact.put("closedCount", snapshot.closed().size());
        fact.put("closedUnmergedCount", snapshot.closedUnmergedCount());
        fact.put("open", snapshot.open().stream().map(this::pullRequestFact).toList());
        fact.put("closed", snapshot.closed().stream().map(this::pullRequestFact).toList());
        fact.put("closedUnmerged", snapshot.closed().stream().filter(pr -> !pr.merged()).map(this::pullRequestFact).toList());
        return fact;
    }

    private Map<String, Object> pullRequestFact(GitHubPullRequestService.GitHubPullRequest pr) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("number", pr.number());
        fact.put("title", pr.title());
        fact.put("author", pr.author());
        fact.put("headRef", pr.headRef());
        fact.put("url", pr.url());
        fact.put("merged", pr.merged());
        return fact;
    }

    @SuppressWarnings("unchecked")
    private Object augmentSixSigmaWithGithubPrDefects(Object rawSixSigma,
                                                       GitHubPullRequestService.PullRequestSnapshot pullRequests) {
        if (!(rawSixSigma instanceof Map<?, ?> rawMap)) {
            return rawSixSigma;
        }
        Map<String, Object> sixSigma = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> sixSigma.put(String.valueOf(key), value));
        long closedUnmerged = pullRequests == null ? 0 : pullRequests.closedUnmergedCount();
        if (closedUnmerged <= 0) {
            return sixSigma;
        }

        long totalOpportunities = longValue(sixSigma.get("totalOpportunities")) + closedUnmerged;
        long totalDefects = longValue(sixSigma.get("totalDefects")) + closedUnmerged;
        sixSigma.put("totalOpportunities", totalOpportunities);
        sixSigma.put("totalDefects", totalDefects);
        sixSigma.put("closedUnmergedPrDefects", closedUnmerged);
        sixSigma.put("dpmo", round(totalOpportunities == 0 ? 0.0 : (totalDefects / (double) totalOpportunities) * 1_000_000.0));
        sixSigma.put("yieldRate", round(totalOpportunities == 0 ? 0.0 : (totalOpportunities - totalDefects) / (double) totalOpportunities));
        sixSigma.put("copqProxy", longValue(sixSigma.get("copqProxy")) + (closedUnmerged * 4));

        List<Map<String, Object>> pareto = new ArrayList<>();
        Object rawPareto = sixSigma.get("ctqPareto");
        if (rawPareto instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    pareto.add(copy);
                }
            }
        }
        Map<String, Object> githubRow = new LinkedHashMap<>();
        githubRow.put("source", "github_pr");
        githubRow.put("ctq", "closed_unmerged_pr");
        githubRow.put("name", "Closed unmerged PR");
        githubRow.put("opportunities", Math.max(1, pullRequests.closed().size()));
        githubRow.put("defects", closedUnmerged);
        githubRow.put("defectShare", round(totalDefects == 0 ? 0.0 : closedUnmerged / (double) totalDefects));
        githubRow.put("dpmo", round((closedUnmerged / (double) Math.max(1, pullRequests.closed().size())) * 1_000_000.0));
        pareto.add(githubRow);
        pareto.sort(Comparator.comparingLong(row -> -longValue(row.get("defects"))));
        sixSigma.put("ctqPareto", pareto.stream().limit(8).toList());
        sixSigma.put("statusLabel", "critical");
        sixSigma.put("interpretation", "Closed-but-unmerged PRs are historical integration scrap for EMS production telemetry, not active project work.");
        sixSigma.put("recommendedAction", "Use this as maker-side production feedback. For the project operator, continue with live open PR cleanup, queued/review dispatch, environment readiness, and fresh atomic project work.");
        return sixSigma;
    }

    private Map<String, Object> taskFacts(List<TaskEntity> tasks) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        tasks.stream()
                .collect(Collectors.groupingBy(TaskEntity::getStatus, () -> new EnumMap<>(TaskStatus.class), Collectors.counting()))
                .forEach(counts::put);

        long internalBlockedDefectCounter = counts.getOrDefault(TaskStatus.blocked, 0L);
        List<TaskEntity> activeProjectTasks = tasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.blocked)
                .toList();
        Map<TaskStatus, Long> userVisibleCounts = new EnumMap<>(counts);
        userVisibleCounts.put(TaskStatus.blocked, 0L);

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", activeProjectTasks.size());
        fact.put("countsByStatus", userVisibleCounts);
        fact.put("internalProductionDefectCounter", Map.of(
                "blockedAttempts", internalBlockedDefectCounter,
                "visibility", "internal_only_do_not_report_by_default",
                "meaning", "Recorded failed attempts for EMS production learning; not active project work."
        ));
        fact.put("items", activeProjectTasks.stream().map(this::taskFact).toList());
        return fact;
    }

    private Map<String, Object> taskFact(TaskEntity task) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", task.getId());
        fact.put("status", task.getStatus());
        fact.put("roleTag", task.getRole() != null ? task.getRole().getTag() : null);
        fact.put("title", TaskTitleBuilder.displayTitle(task));
        fact.put("description", trim(task.getDescription(), 900));
        fact.put("priority", task.getPriority());
        fact.put("retryCount", task.getRetryCount());
        fact.put("qualityGatePassed", task.isQualityGatePassed());
        fact.put("julesSessionName", task.getJulesSessionName());
        fact.put("julesDispatchStatus", task.getJulesDispatchStatus());
        fact.put("linearIssueId", task.getLinearIssueId());
        fact.put("fileScope", trim(task.getFileScope(), 400));
        fact.put("cynefinDomain", task.getCynefinDomain());
        fact.put("dependsOn", task.getDependsOn() != null ? task.getDependsOn().getId() : null);
        fact.put("createdAt", text(task.getCreatedAt()));
        fact.put("updatedAt", text(task.getUpdatedAt()));
        fact.put("payload", jsonNodeText(task.getPayload(), 1200));
        fact.put("qualityGateReport", jsonNodeText(task.getQualityGateReport(), 1200));
        return fact;
    }

    private Map<String, Object> sessionFacts(List<JulesSessionEntity> sessions, Map<UUID, TaskEntity> tasksById, List<AccountEntity> accounts) {
        Map<UUID, AccountEntity> accountsById = accounts.stream()
                .filter(account -> account.getId() != null)
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity(), (a, b) -> a));

        Map<String, Long> counts = sessions.stream()
                .collect(Collectors.groupingBy(JulesSessionEntity::getStatus, LinkedHashMap::new, Collectors.counting()));
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", sessions.size());
        fact.put("countsByStatus", counts);
        fact.put("items", sessions.stream().map(session -> sessionFact(session, tasksById, accountsById)).toList());
        return fact;
    }

    private Map<String, Object> sessionFact(JulesSessionEntity session, Map<UUID, TaskEntity> tasksById, Map<UUID, AccountEntity> accountsById) {
        TaskEntity task = tasksById.get(session.getTaskId());
        AccountEntity account = session.getAccountId() == null ? null : accountsById.get(session.getAccountId());
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", session.getId());
        fact.put("externalSessionId", session.getExternalSessionId());
        fact.put("status", session.getStatus());
        fact.put("prUrl", session.getPrUrl());
        fact.put("taskId", session.getTaskId());
        fact.put("taskStatus", task != null && task.getStatus() == TaskStatus.blocked
                ? "recorded_failed_attempt"
                : task != null ? task.getStatus() : null);
        fact.put("roleTag", task != null && task.getRole() != null ? task.getRole().getTag() : null);
        fact.put("taskTitle", task != null ? TaskTitleBuilder.displayTitle(task) : null);
        fact.put("taskDescription", task != null ? trim(task.getDescription(), 300) : null);
        fact.put("accountId", session.getAccountId());
        fact.put("accountName", account != null ? account.getName() : null);
        fact.put("accountGithubUsername", account != null ? account.getGithubUsername() : null);
        fact.put("createdAt", text(session.getCreatedAt()));
        fact.put("updatedAt", text(session.getUpdatedAt()));
        fact.put("lastStatusCheckAt", text(session.getLastStatusCheckAt()));
        fact.put("closedAt", text(session.getClosedAt()));
        fact.put("closureReason", trim(session.getClosureReason(), 1200));
        return fact;
    }

    private Map<String, Object> reviewFacts(List<PrReviewEntity> reviews, List<JulesSessionEntity> sessions, Map<UUID, TaskEntity> tasksById) {
        Map<UUID, JulesSessionEntity> sessionsById = sessions.stream()
                .collect(Collectors.toMap(JulesSessionEntity::getId, Function.identity(), (a, b) -> a));
        Map<String, Object> fact = new LinkedHashMap<>();
        PrStats stats = prStats(null, reviews);
        fact.put("uniquePullRequests", stats.reviewedPullRequests());
        fact.put("recordsTotal", stats.reviewRecords());
        fact.put("approved", stats.approved());
        fact.put("rejected", stats.rejected());
        fact.put("merged", stats.merged());
        fact.put("approvedButNotMerged", stats.pendingApproved());
        fact.put("items", reviews.stream().map(review -> reviewFact(review, sessionsById, tasksById)).toList());
        return fact;
    }

    private Map<String, Object> reviewFact(PrReviewEntity review, Map<UUID, JulesSessionEntity> sessionsById, Map<UUID, TaskEntity> tasksById) {
        JulesSessionEntity session = sessionsById.get(review.getJulesSessionId());
        TaskEntity task = session == null ? null : tasksById.get(session.getTaskId());
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", review.getId());
        fact.put("prUrl", review.getPrUrl());
        fact.put("decision", reviewDecision(review));
        fact.put("merged", Boolean.TRUE.equals(review.getMerged()));
        fact.put("ciStatus", review.getCiStatus());
        fact.put("riskLevel", review.getRiskLevel());
        fact.put("linesChanged", review.getLinesChanged());
        fact.put("filesChanged", review.getFilesChanged());
        fact.put("hasTestChanges", review.getHasTestChanges());
        fact.put("julesSessionId", review.getJulesSessionId());
        fact.put("externalSessionId", session != null ? session.getExternalSessionId() : null);
        fact.put("sessionStatus", session != null ? session.getStatus() : null);
        fact.put("taskId", task != null ? task.getId() : null);
        fact.put("taskStatus", task != null ? task.getStatus() : null);
        fact.put("roleTag", task != null && task.getRole() != null ? task.getRole().getTag() : null);
        fact.put("summary", trim(review.getDiffSummary(), 1000));
        fact.put("createdAt", text(review.getCreatedAt()));
        return fact;
    }

    private Map<String, Object> wishlistFacts(List<WishlistEntity> wishlist) {
        Map<String, Long> counts = wishlist.stream()
                .collect(Collectors.groupingBy(item -> item.getStatus().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", wishlist.size());
        fact.put("countsByStatus", counts);
        fact.put("items", wishlist.stream().map(this::wishlistFact).toList());
        return fact;
    }

    private Map<String, Object> wishlistFact(WishlistEntity item) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", item.getId());
        fact.put("source", item.getSource());
        fact.put("sourceRoleTag", item.getSourceRoleTag());
        fact.put("status", item.getStatus());
        fact.put("content", trim(item.getContent(), 900));
        fact.put("jtbd", trim(item.getJtbd(), 500));
        fact.put("acceptanceCriteria", trim(item.getAcceptanceCriteria(), 900));
        fact.put("dod", trim(item.getDod(), 500));
        fact.put("leanValue", item.getLeanValue());
        fact.put("tocConstraintRef", item.getTocConstraintRef());
        fact.put("sixSigmaMetric", item.getSixSigmaMetric());
        fact.put("compiledByRole", item.getCompiledByRole());
        fact.put("createdAt", text(item.getCreatedAt()));
        return fact;
    }

    private Map<String, Object> accountFacts(List<AccountEntity> accounts, List<JulesSessionEntity> sessions) {
        Map<UUID, Long> activeCapacitySessionsByAccount = sessions.stream()
                .filter(session -> session.getAccountId() != null)
                .filter(session -> List.of("queued", "running", "revising", "stuck").contains(session.getStatus()))
                .collect(Collectors.groupingBy(JulesSessionEntity::getAccountId, Collectors.counting()));
        Map<UUID, Long> trackedSessionsByAccount = sessions.stream()
                .filter(session -> session.getAccountId() != null)
                .filter(session -> List.of("queued", "running", "revising", "stuck", "pr_opened").contains(session.getStatus()))
                .collect(Collectors.groupingBy(JulesSessionEntity::getAccountId, Collectors.counting()));
        Map<String, Long> counts = accounts.stream()
                .collect(Collectors.groupingBy(account -> account.getStatus().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", accounts.size());
        fact.put("countsByStatus", counts);
        fact.put("dailyLimited", counts.getOrDefault(AccountStatus.daily_limited.name(), 0L));
        fact.put("apiBlocked", counts.getOrDefault(AccountStatus.api_blocked.name(), 0L));
        fact.put("effectiveOperational", accounts.stream()
                .filter(AccountEntity::isEnabled)
                .filter(account -> account.getStatus() != AccountStatus.decommissioned)
                .filter(account -> account.getStatus() != AccountStatus.offline)
                .filter(account -> account.getStatus() != AccountStatus.daily_limited)
                .filter(account -> account.getStatus() != AccountStatus.api_blocked)
                .count());
        fact.put("universalRoleInvariant", "Every enabled Jules account is capable of every BARCAN-TAG-00..11 role.");
        fact.put("maxConcurrentSessionsPerAccount", maxConcurrentJulesSessionsPerAccount);
        fact.put("items", accounts.stream().map(account -> accountFact(account, activeCapacitySessionsByAccount, trackedSessionsByAccount)).toList());
        return fact;
    }

    private Map<String, Object> accountFact(AccountEntity account,
                                           Map<UUID, Long> activeCapacitySessionsByAccount,
                                           Map<UUID, Long> trackedSessionsByAccount) {
        long capacitySessions = activeCapacitySessionsByAccount.getOrDefault(account.getId(), 0L);
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", account.getId());
        fact.put("name", account.getName());
        fact.put("status", account.getStatus());
        fact.put("effectiveStatus", account.getStatus() == AccountStatus.daily_limited
                ? "daily_limited_no_new_sessions"
                : account.getStatus() == AccountStatus.api_blocked
                ? "jules_api_blocked_no_new_sessions"
                : account.getStatus().name());
        fact.put("enabled", account.isEnabled());
        fact.put("currentProjectId", account.getCurrentProjectId());
        fact.put("githubUsername", account.getGithubUsername());
        fact.put("capabilities", account.getCapabilities());
        fact.put("effectiveCapabilities", JulesRoleCapabilities.ALL_ROLE_TAGS);
        fact.put("activeCapacitySessions", capacitySessions);
        fact.put("trackedActiveProjectSessions", trackedSessionsByAccount.getOrDefault(account.getId(), 0L));
        fact.put("maxConcurrentSessions", maxConcurrentJulesSessionsPerAccount);
        fact.put("freeSessionSlots", account.getStatus() == AccountStatus.daily_limited || account.getStatus() == AccountStatus.api_blocked
                ? 0
                : Math.max(0, maxConcurrentJulesSessionsPerAccount - capacitySessions));
        fact.put("lastHeartbeat", text(account.getLastHeartbeat()));
        return fact;
    }

    private Map<String, Object> julesCapacityFacts(List<TaskEntity> tasks,
                                                   List<AccountEntity> accounts,
                                                   List<JulesSessionEntity> sessions) {
        List<AccountEntity> enabledAccounts = accounts.stream()
                .filter(AccountEntity::isEnabled)
                .filter(account -> account.getStatus() != AccountStatus.decommissioned)
                .filter(account -> account.getStatus() != AccountStatus.offline)
                .filter(account -> account.getStatus() != AccountStatus.daily_limited)
                .filter(account -> account.getStatus() != AccountStatus.api_blocked)
                .toList();
        Map<UUID, AccountEntity> enabledById = enabledAccounts.stream()
                .filter(account -> account.getId() != null)
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity(), (a, b) -> a));
        long activeCapacitySessions = sessions.stream()
                .filter(session -> session.getAccountId() != null)
                .filter(session -> enabledById.containsKey(session.getAccountId()))
                .filter(session -> List.of("queued", "running", "revising", "stuck").contains(session.getStatus()))
                .count();
        long sharedSlotsTotal = (long) enabledAccounts.size() * maxConcurrentJulesSessionsPerAccount;
        long sharedSlotsFree = Math.max(0, sharedSlotsTotal - activeCapacitySessions);
        Map<String, Long> queuedByRole = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.queued)
                .filter(task -> task.getRole() != null)
                .collect(Collectors.groupingBy(task -> task.getRole().getTag(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("universalRolePool", true);
        fact.put("rule", "Every enabled Jules account can take every BARCAN role; role tag is execution context, not account eligibility.");
        fact.put("allRoleTags", JulesRoleCapabilities.ALL_ROLE_TAGS);
        fact.put("maxConcurrentSessionsPerAccount", maxConcurrentJulesSessionsPerAccount);
        fact.put("enabledAccounts", enabledAccounts.size());
        fact.put("dailyLimitedAccounts", accounts.stream().filter(account -> account.getStatus() == AccountStatus.daily_limited).count());
        fact.put("apiBlockedAccounts", accounts.stream().filter(account -> account.getStatus() == AccountStatus.api_blocked).count());
        fact.put("sharedSlotsTotal", sharedSlotsTotal);
        fact.put("sharedSlotsUsed", activeCapacitySessions);
        fact.put("sharedSlotsFree", sharedSlotsFree);
        fact.put("queuedTasksByRole", queuedByRole);
        fact.put("interpretationRules", List.of(
                "If sharedSlotsFree > 0, do not claim a Jules capacity shortage.",
                "Accounts with status daily_limited have zero effective new-session capacity until their explicit Jules quota/rate limit resets.",
                "Accounts with status api_blocked are not daily-limited; inspect the latest create-session HTTP error, repository access, sourceContext, and API authorization.",
                "If queuedTasksByRole is non-empty and sharedSlotsFree > 0, the next diagnostic target is dispatch flow, stuck claims, API errors, or project/task conflict rules.",
                "Never diagnose missing BARCAN capability unless universalRolePool is false."
        ));
        return fact;
    }

    private Map<String, Object> conflictFacts(List<TaskConflictEntity> conflicts) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", conflicts.size());
        fact.put("active", conflicts.stream().filter(c -> !"auto_resolved".equals(c.getResolutionStatus())).count());
        fact.put("items", conflicts.stream().map(this::conflictFact).toList());
        return fact;
    }

    private Map<String, Object> conflictFact(TaskConflictEntity conflict) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", conflict.getId());
        fact.put("taskId", conflict.getTask() != null ? conflict.getTask().getId() : null);
        fact.put("roleTag", conflict.getTask() != null && conflict.getTask().getRole() != null ? conflict.getTask().getRole().getTag() : null);
        fact.put("taskStatus", conflict.getTask() != null ? conflict.getTask().getStatus() : null);
        fact.put("prUrl", conflict.getPrUrl());
        fact.put("conflictType", conflict.getConflictType());
        fact.put("resolutionStatus", conflict.getResolutionStatus());
        fact.put("resolutionAttempts", conflict.getResolutionAttempts());
        fact.put("conflictingFiles", trim(conflict.getConflictingFiles(), 700));
        fact.put("detectedAt", text(conflict.getDetectedAt()));
        fact.put("resolvedAt", text(conflict.getResolvedAt()));
        return fact;
    }

    private List<Map<String, Object>> bottleneckFacts(UUID projectId) {
        return bottleneckDetectionService.detect(projectId).stream().map(bottleneck -> {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", bottleneck.type());
            fact.put("tag", bottleneck.tag());
            fact.put("accountId", bottleneck.accountId());
            fact.put("reason", bottleneck.reason());
            return fact;
        }).toList();
    }

    private PrStats prStats(GitHubPullRequestService.PullRequestSnapshot snapshot, List<PrReviewEntity> reviews) {
        Map<String, PrReviewEntity> latestByPrUrl = new LinkedHashMap<>();
        reviews.stream()
                .sorted(Comparator.comparing(PrReviewEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(review -> latestByPrUrl.putIfAbsent(reviewKey(review), review));
        List<PrReviewEntity> latestReviews = new ArrayList<>(latestByPrUrl.values());

        long approved = latestReviews.stream().filter(this::isApproved).count();
        long rejected = latestReviews.stream().filter(this::isRejected).count();
        long merged = latestReviews.stream().filter(review -> Boolean.TRUE.equals(review.getMerged())).count();
        long pendingApproved = latestReviews.stream().filter(this::isApproved).filter(review -> !Boolean.TRUE.equals(review.getMerged())).count();

        List<String> mergedLines = latestReviews.stream()
                .filter(review -> Boolean.TRUE.equals(review.getMerged()))
                .map(review -> prNumber(review.getPrUrl()) + " merged")
                .distinct()
                .toList();

        List<String> approvedPendingLines = latestReviews.stream()
                .filter(this::isApproved)
                .filter(review -> !Boolean.TRUE.equals(review.getMerged()))
                .map(review -> prNumber(review.getPrUrl()) + " approved")
                .distinct()
                .toList();

        List<String> rejectedLines = latestReviews.stream()
                .filter(this::isRejected)
                .map(review -> prNumber(review.getPrUrl()) + " rejected")
                .distinct()
                .limit(12)
                .toList();

        List<String> openPrLines = snapshot == null
                ? List.of()
                : snapshot.open().stream()
                        .map(pr -> "#" + pr.number() + " " + pr.title())
                        .toList();

        return new PrStats(
                snapshot != null && snapshot.available(),
                snapshot == null ? 0 : snapshot.open().size(),
                snapshot == null ? 0 : snapshot.closed().size(),
                snapshot == null ? "" : snapshot.error(),
                latestReviews.size(),
                reviews.size(),
                approved,
                rejected,
                merged,
                pendingApproved,
                mergedLines,
                approvedPendingLines,
                rejectedLines,
                openPrLines
        );
    }

    private String reviewKey(PrReviewEntity review) {
        if (review.getPrUrl() != null && !review.getPrUrl().isBlank()) {
            return review.getPrUrl();
        }
        return review.getId() == null ? "unknown" : review.getId().toString();
    }

    private boolean isApproved(PrReviewEntity review) {
        return review.getDiffSummary() != null && review.getDiffSummary().contains(APPROVAL_TOKEN);
    }

    private boolean isRejected(PrReviewEntity review) {
        return review.getDiffSummary() != null && review.getDiffSummary().startsWith("REVIEW REJECTED");
    }

    private String reviewDecision(PrReviewEntity review) {
        if (Boolean.TRUE.equals(review.getMerged())) {
            return "merged";
        }
        if (isApproved(review)) {
            return "approved";
        }
        if (isRejected(review)) {
            return "rejected";
        }
        return "unknown";
    }

    private String prNumber(String prUrl) {
        if (prUrl == null || prUrl.isBlank()) {
            return "PR unknown";
        }
        int index = prUrl.lastIndexOf('/');
        return index >= 0 ? "PR #" + prUrl.substring(index + 1) : prUrl;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String firstSentence(String value) {
        String clean = trim(value == null ? "" : value.replace("REVIEW REJECTED.", "").trim(), 180);
        int newline = clean.indexOf('\n');
        if (newline >= 0) {
            clean = clean.substring(0, newline).trim();
        }
        return clean;
    }

    private String jsonNodeText(JsonNode node, int maxLength) {
        if (node == null || node.isNull()) {
            return null;
        }
        return trim(node.toString(), maxLength);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("\\s+", " ").trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public record ProjectOperationalContext(
            UUID projectId,
            String projectName,
            Map<String, Object> facts,
            String promptJson,
            PrStats prStats
    ) {}

    public record PrStats(
            boolean githubAvailable,
            int githubOpen,
            int githubClosed,
            String githubError,
            long reviewedPullRequests,
            long reviewRecords,
            long approved,
            long rejected,
            long merged,
            long pendingApproved,
            List<String> mergedLines,
            List<String> approvedPendingLines,
            List<String> rejectedLines,
            List<String> openPrLines
    ) {}
}
