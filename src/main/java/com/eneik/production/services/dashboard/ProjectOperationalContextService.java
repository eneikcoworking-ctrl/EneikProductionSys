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
        factPack.put("systemStatusProjectOnly", systemStatusService.getStatus(project.getId()));
        factPack.put("rules", List.of(
                "No global system data is included in this fact pack.",
                "Every enabled Jules account is universal-role capable for all BARCAN-TAG-00..11 roles.",
                "Do not diagnose missing role capability for Jules accounts; diagnose shared session slots, account enabled/status, stuck sessions, API errors, or dispatch failures instead.",
                "Blocked tasks are terminal evidence for failed attempts, not a request for the human operator to choose IDs. If sharedSlotsFree > 0, the recovery path is to create/compile fresh atomic recovery work and dispatch it.",
                "Use githubPullRequestsLive for current GitHub open/closed PR counts.",
                "Use databasePrReviews for review decisions and merge results.",
                "If a fact is absent from this pack, say it is not available instead of guessing."
        ));

        return new ProjectOperationalContext(project.getId(), project.getName(), factPack, toJson(factPack), prStats);
    }

    public boolean isPrReviewQuestion(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        boolean asksAboutPullRequests = lower.contains("pr")
                || lower.contains("pull request")
                || lower.contains("\u043f\u0443\u043b\u043b")
                || lower.contains("\u043f\u0443\u043b \u0440\u0435\u043a\u0432\u0435\u0441\u0442")
                || lower.contains("\u043f\u0443\u043b\u043b-\u0440\u0435\u043a\u0432\u0435\u0441\u0442")
                || lower.contains("pull-request");
        boolean asksAboutReviewResult = lower.contains("\u0440\u0435\u0432\u044c\u044e")
                || lower.contains("review")
                || lower.contains("\u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442")
                || lower.contains("\u0441\u043a\u043e\u043b\u044c\u043a\u043e")
                || lower.contains("\u043e\u0442\u043a\u0440\u044b\u0442\u043e")
                || lower.contains("\u0437\u0430\u043a\u0440\u044b\u0442\u043e")
                || lower.contains("\u0441\u043c\u0451\u0440\u0436")
                || lower.contains("\u0441\u043c\u0435\u0440\u0436")
                || lower.contains("merge");
        return asksAboutPullRequests && asksAboutReviewResult;
    }

    public String answerPrReviewQuestion(ProjectOperationalContext context) {
        PrStats stats = context.prStats();
        StringBuilder answer = new StringBuilder();
        answer.append("\u041f\u043e \u0442\u0435\u043a\u0443\u0449\u0435\u043c\u0443 \u043f\u0440\u043e\u0435\u043a\u0442\u0443 `").append(context.projectName()).append("`:\n");
        if (stats.githubAvailable()) {
            answer.append("- GitHub \u0441\u0435\u0439\u0447\u0430\u0441: open PR = ").append(stats.githubOpen())
                    .append(", closed PR = ").append(stats.githubClosed()).append(".\n");
        } else {
            answer.append("- GitHub live PR count \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d: ").append(stats.githubError()).append(".\n");
        }
        answer.append("- \u0423\u043d\u0438\u043a\u0430\u043b\u044c\u043d\u044b\u0445 PR \u0441 review: ").append(stats.reviewedPullRequests())
                .append(" (\u0437\u0430\u043f\u0438\u0441\u0435\u0439 review \u0432 backend: ").append(stats.reviewRecords()).append(")")
                .append(". Approved = ").append(stats.approved())
                .append(", rejected = ").append(stats.rejected())
                .append(", merged = ").append(stats.merged())
                .append(", approved-but-not-merged = ").append(stats.pendingApproved()).append(".\n");

        if (!stats.mergedLines().isEmpty()) {
            answer.append("- \u0423\u0436\u0435 \u0441\u043c\u0451\u0440\u0436\u0435\u043d\u043e: ").append(String.join("; ", stats.mergedLines())).append(".\n");
        }
        if (!stats.approvedPendingLines().isEmpty()) {
            answer.append("- \u041e\u0434\u043e\u0431\u0440\u0435\u043d\u043e, \u043d\u043e \u0435\u0449\u0435 \u043d\u0435 \u0441\u043c\u0451\u0440\u0436\u0435\u043d\u043e: ").append(String.join("; ", stats.approvedPendingLines())).append(".\n");
        }
        if (!stats.rejectedLines().isEmpty()) {
            answer.append("- \u041d\u0430 \u0434\u043e\u0440\u0430\u0431\u043e\u0442\u043a\u0435 \u043f\u043e\u0441\u043b\u0435 review: ").append(String.join("; ", stats.rejectedLines())).append(".\n");
        }
        if (!stats.openPrLines().isEmpty()) {
            answer.append("- \u041e\u0442\u043a\u0440\u044b\u0442\u044b\u0435 PR \u043d\u0430 GitHub: ").append(String.join("; ", stats.openPrLines())).append(".");
        }
        return answer.toString();
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

    private Map<String, Object> githubFact(GitHubPullRequestService.PullRequestSnapshot snapshot) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("available", snapshot.available());
        fact.put("owner", snapshot.owner());
        fact.put("repo", snapshot.repo());
        fact.put("error", snapshot.error());
        fact.put("openCount", snapshot.open().size());
        fact.put("closedCount", snapshot.closed().size());
        fact.put("open", snapshot.open().stream().map(this::pullRequestFact).toList());
        fact.put("closed", snapshot.closed().stream().map(this::pullRequestFact).toList());
        return fact;
    }

    private Map<String, Object> pullRequestFact(GitHubPullRequestService.GitHubPullRequest pr) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("number", pr.number());
        fact.put("title", pr.title());
        fact.put("author", pr.author());
        fact.put("headRef", pr.headRef());
        fact.put("url", pr.url());
        return fact;
    }

    private Map<String, Object> taskFacts(List<TaskEntity> tasks) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        tasks.stream()
                .collect(Collectors.groupingBy(TaskEntity::getStatus, () -> new EnumMap<>(TaskStatus.class), Collectors.counting()))
                .forEach(counts::put);

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("total", tasks.size());
        fact.put("countsByStatus", counts);
        fact.put("items", tasks.stream().map(this::taskFact).toList());
        return fact;
    }

    private Map<String, Object> taskFact(TaskEntity task) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", task.getId());
        fact.put("status", task.getStatus());
        fact.put("roleTag", task.getRole() != null ? task.getRole().getTag() : null);
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
        fact.put("taskStatus", task != null ? task.getStatus() : null);
        fact.put("roleTag", task != null && task.getRole() != null ? task.getRole().getTag() : null);
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
        fact.put("enabled", account.isEnabled());
        fact.put("currentProjectId", account.getCurrentProjectId());
        fact.put("githubUsername", account.getGithubUsername());
        fact.put("capabilities", account.getCapabilities());
        fact.put("effectiveCapabilities", JulesRoleCapabilities.ALL_ROLE_TAGS);
        fact.put("activeCapacitySessions", capacitySessions);
        fact.put("trackedActiveProjectSessions", trackedSessionsByAccount.getOrDefault(account.getId(), 0L));
        fact.put("maxConcurrentSessions", maxConcurrentJulesSessionsPerAccount);
        fact.put("freeSessionSlots", Math.max(0, maxConcurrentJulesSessionsPerAccount - capacitySessions));
        fact.put("lastHeartbeat", text(account.getLastHeartbeat()));
        return fact;
    }

    private Map<String, Object> julesCapacityFacts(List<TaskEntity> tasks,
                                                   List<AccountEntity> accounts,
                                                   List<JulesSessionEntity> sessions) {
        List<AccountEntity> enabledAccounts = accounts.stream()
                .filter(AccountEntity::isEnabled)
                .filter(account -> account.getStatus() != AccountStatus.decommissioned)
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
        fact.put("sharedSlotsTotal", sharedSlotsTotal);
        fact.put("sharedSlotsUsed", activeCapacitySessions);
        fact.put("sharedSlotsFree", sharedSlotsFree);
        fact.put("queuedTasksByRole", queuedByRole);
        fact.put("interpretationRules", List.of(
                "If sharedSlotsFree > 0, do not claim a Jules capacity shortage.",
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
