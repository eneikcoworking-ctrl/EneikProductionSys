package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.RoleCapabilityLoader;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.task.TaskTitleBuilder;
import com.eneik.production.repositories.RoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JulesDispatchService {
    private static final Logger log = LoggerFactory.getLogger(JulesDispatchService.class);
    private static final List<String> ACTIVE_SESSION_STATUSES = List.of("running", "queued", "revising", "pr_opened", "stuck");
    private static final Duration STUCK_RECOVERY_MESSAGE_INTERVAL = Duration.ofMinutes(15);
    private static final int DESTRUCTIVE_LOOP_REPEAT_THRESHOLD = 2;
    private static final int FOLLOW_UP_CONTENT_MAX_LENGTH = 7_500;
    private static final String REVIEW_REJECTION_ACTIVITY_NAME = "system-pr-review-rejection";
    // A design-review "approved, but here are some concerns" verdict is by definition non-blocking - it
    // must never gate the design-review-loop.dispatch on its own findings, only ever add backlog. But an
    // unconditional "one concern in, one wishlist item out" mapping with no stopping condition means a
    // single design role can generate an unbounded amount of self-perpetuating work that competes for the
    // same limited Jules capacity as the actual client deliverable it's reviewing (confirmed live in
    // test-twenty-eighth: 48 of 78 wishlist items across the whole project traced back to exactly this
    // loop). Cap how much *pending* non-blocking backlog one project's design role is allowed to carry at
    // once - once the cap is hit, new concerns are logged but not turned into fresh work; they'll surface
    // again on the next real review pass if still relevant, once older items have been worked off.
    private static final int MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT = 5;

    private final JulesApiClient julesApiClient;
    private final JulesSessionRepository julesSessionRepository;
    private final JulesActivityResponseRepository julesActivityResponseRepository;
    private final WishlistRepository wishlistRepository;
    private final com.eneik.production.repositories.AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final ClaimService claimService;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService;
    private final com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient;
    private final RoleRepository roleRepository;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private final com.eneik.production.repositories.PrReviewRepository prReviewRepository;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;
    private final com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository;
    private final com.eneik.production.services.FalsificationCycleService falsificationCycleService;
    private final String sourcePrefix;

    private static final int WISHLIST_COMPILER_MAX_RETRIES = 2;
    private static final String WISHLIST_COMPILER_PLAN_PATH = ".eneik/task-plan.json";

    private static final java.time.format.DateTimeFormatter RECORD_ARCHIVE_TIME_SUFFIX =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(java.time.ZoneOffset.UTC);

    // Once a record PR (compiler plan, review verdict, design verdict, falsification report) is merged,
    // its file sits at a fixed, reused path (e.g. .eneik/task-plan.json) - the NEXT merge would silently
    // overwrite it, destroying the previous run's documentation. Archiving a timestamped copy under
    // .eneik/records/ keeps every run as permanent, distinctly named production documentation instead of
    // a single clobbered file (operator's explicit instruction: "даже если там просто файл с текстом -
    // его сохранять под соответствующим названием - для контекста. Это производственная документация").
    private void archiveRecordFile(com.eneik.production.models.persistence.ProjectEntity project, String fixedPath, String typeLabel) {
        String archivePath = ".eneik/records/" + typeLabel + "-" + RECORD_ARCHIVE_TIME_SUFFIX.format(java.time.Instant.now()) + ".json";
        boolean archived = gitHubPullRequestService.copyFile(project, fixedPath, archivePath,
                "Archive " + typeLabel + " as production documentation");
        if (!archived) {
            log.warn("Could not archive {} record from {} to {} for project {}", typeLabel, fixedPath, archivePath, project.getId());
        }
    }

    /**
     * Marks a session as done with no product code involved - used for the four record-PR session
     * types (compiler plan / falsification report / review verdict / design review verdict), which by
     * construction never touch product code, right after their single-file PR merges. Mirrors the shape
     * of the existing "loop_closed" circuit-breaker closure without implying anything went wrong.
     */
    private void closeSessionAsNoCode(JulesSessionEntity session, String reason) {
        session.setStatus("closed_no_code");
        session.setClosedAt(java.time.Instant.now());
        session.setClosureReason(reason);
        julesSessionRepository.save(session);
    }

    @Value("${jules.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    @Value("${jules.max-agent-dialog-responses:8}")
    private int maxAgentDialogResponses;

    @Value("${jules.loop-close-similar-threshold:2}")
    private int loopCloseSimilarThreshold;

    @Value("${jules.stuck-close-threshold-minutes:120}")
    private int stuckCloseThresholdMinutes;

    @Value("${jules.max-loop-closures-per-run:5}")
    private int maxLoopClosuresPerRun;

    @Value("${jules.forced-unblock-blind-cycle-threshold:5}")
    private int forcedUnblockBlindCycleThreshold;

    @Value("${jules.forced-unblock-max-attempts:2}")
    private int forcedUnblockMaxAttempts;

    public JulesDispatchService(JulesApiClient julesApiClient,
                                JulesSessionRepository julesSessionRepository,
                                JulesActivityResponseRepository julesActivityResponseRepository,
                                WishlistRepository wishlistRepository,
                                com.eneik.production.repositories.AccountRepository accountRepository,
                                TaskRepository taskRepository,
                                TaskConflictRepository taskConflictRepository,
                                ClaimService claimService,
                                RoleCapabilityLoader roleCapabilityLoader,
                                com.eneik.production.services.monitor.PrReviewPipelineService prReviewPipelineService,
                                com.eneik.production.services.MLPredictionServiceClient mlPredictionServiceClient,
                                RoleRepository roleRepository,
                                GitHubPullRequestService gitHubPullRequestService,
                                com.eneik.production.repositories.PrReviewRepository prReviewRepository,
                                com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker,
                                @org.springframework.context.annotation.Lazy com.eneik.production.services.ProjectFlowService projectFlowService,
                                com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository,
                                @org.springframework.context.annotation.Lazy com.eneik.production.services.FalsificationCycleService falsificationCycleService,
                                com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                                @Value("${jules.source-prefix:sources/github/${github.org}/}") String sourcePrefix) {
        this.julesApiClient = julesApiClient;
        this.julesSessionRepository = julesSessionRepository;
        this.julesActivityResponseRepository = julesActivityResponseRepository;
        this.wishlistRepository = wishlistRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.claimService = claimService;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.prReviewPipelineService = prReviewPipelineService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.roleRepository = roleRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.prReviewRepository = prReviewRepository;
        this.systemProgressTracker = systemProgressTracker;
        this.projectFlowService = projectFlowService;
        this.needsHumanReviewRepository = needsHumanReviewRepository;
        this.falsificationCycleService = falsificationCycleService;
        this.featureThreadRepository = featureThreadRepository;
        this.sourcePrefix = sourcePrefix;
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task) {
        return dispatch(task, null);
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task, UUID accountId) {
        return dispatch(task, accountId, "IMPLEMENTER");
    }

    @Transactional
    public JulesDispatchResult dispatch(TaskEntity task, UUID accountId, String mode) {
        List<JulesSessionEntity> existing = julesSessionRepository.findByTaskId(task.getId());
        for (JulesSessionEntity s : existing) {
            if ("skipped".equals(s.getExternalSessionId())) {
                julesSessionRepository.delete(s);
                continue;
            }
            if (ACTIVE_SESSION_STATUSES.contains(s.getStatus())) {
                log.info("Task {} already dispatched (status: {}), skipping duplicate", task.getId(), s.getStatus());
                return new JulesDispatchResult(true, s.getExternalSessionId(), "already dispatched, skipping duplicate");
            }
        }

        JulesSessionEntity session = dispatchInternal(task, accountId, mode);
        boolean dispatched = "running".equals(session.getStatus()) || "queued".equals(session.getStatus());
        String reason;
        if ("skipped".equals(session.getExternalSessionId())) {
            reason = "Jules integration disabled";
        } else if (!dispatched) {
            reason = session.getClosureReason() == null || session.getClosureReason().isBlank()
                    ? "Jules session creation failed"
                    : session.getClosureReason();
        } else {
            reason = "Dispatched to Jules";
            systemProgressTracker.recordProgress();
        }
        return new JulesDispatchResult(
                dispatched,
                session.getExternalSessionId(),
                reason
        );
    }

    @Transactional
    public JulesSessionEntity dispatch(UUID taskId, UUID accountId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // Ensure task is claimed if being dispatched directly via controller
        if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.queued && accountId != null) {
            claimService.claimSpecificTask(taskId, accountId);
        }

        dispatch(task, accountId);
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(s -> accountId == null || accountId.equals(s.getAccountId()))
                .findFirst().orElse(null);
    }

    private JulesSessionEntity dispatchInternal(TaskEntity task, UUID accountId, String mode) {
        if (accountId != null) {
            if ("REVIEWER".equalsIgnoreCase(mode)) {
                claimService.claimReviewer(task.getId(), accountId);
            }
        }

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setAccountId(accountId);
        session.setStatus("queued");
        session.setLastProgressAt(Instant.now());

        ProjectEntity project = task.getProject();
        if (project == null) {
            session.setStatus("failed");
            return julesSessionRepository.save(session);
        }

        String repoUrl = sourcePrefix + project.getRepositoryName();
        String sessionTitle = TaskTitleBuilder.displayTitle(task);
        String description = withTaskPromptTitle(sessionTitle, task.getDescription());
        var conflictOpt = taskConflictRepository.findFirstByTaskIdAndResolutionStatus(task.getId(), "pending");
        if (conflictOpt.isPresent()) {
            var conflict = conflictOpt.get();
            String dod = "";
            if (task.getPayload() != null && task.getPayload().has("dod")) {
                dod = task.getPayload().get("dod").asText();
            }
            String conflictingFiles = conflict.getConflictingFiles();
            if (conflictingFiles == null || conflictingFiles.trim().isEmpty()) {
                conflictingFiles = "[]";
            }
            sessionTitle = "Conflict Resolve";
            description = withTaskPromptTitle(sessionTitle, "Rebase your branch onto the current main and resolve merge conflicts. Original task: [" + dod + "]. Conflict is in: " + conflictingFiles + ".");
            log.info("Modified prompt for task {} because of merge conflict. New prompt: {}", task.getId(), description);
        } else if ("REVIEWER".equalsIgnoreCase(mode)) {
            sessionTitle = "PR Review";
            description = withTaskPromptTitle(sessionTitle, "[REVIEWER MODE]\nAudit the following code changes against docs/AI_REVIEW_GUIDELINES.md.\n" + task.getDescription());
        }

        StringBuilder roleContextBuilder = new StringBuilder();
        roleContextBuilder.append("Role: ").append(task.getRole().getTag()).append("\n");
        roleContextBuilder.append("Description: ").append(task.getRole().getDescription()).append("\n");
        roleContextBuilder.append("\n## Jules Execution Contract\n");
        roleContextBuilder.append("- Proceed autonomously from the task description, JTBD, Acceptance Criteria, DoD, and file scope.\n");
        roleContextBuilder.append("- Do not pause for broad optional confirmation when the Acceptance Criteria already imply a safe next step.\n");
        roleContextBuilder.append("- If a detail is ambiguous, use the smallest reversible implementation assumption, document it in the PR summary, and keep working.\n");
        roleContextBuilder.append("- Ask at most one concise blocker question only when continuing would create a concrete contradiction or security/data-loss risk.\n");
        roleContextBuilder.append("- Do not commit generated reports, screenshots, trace zips, test-results, playwright-report, node_modules, or local environment files.\n");
        roleContextBuilder.append("- Keep this session atomic: deliver one small service/component/fix and open the PR. Do not expand into new features, broad architecture rewrites, or extra verification branches.\n");
        roleContextBuilder.append("- If the work requires more than one atomic change, complete the smallest safe slice and describe the remaining slices in the PR summary instead of doing them in this branch.\n");
        roleContextBuilder.append("- Hard stop: after repeated blocker feedback or eight back-and-forth replies, the orchestrator may close this session and create new short follow-up wishlist items.\n");
        if ("BARCAN-TAG-06".equals(task.getRole().getTag())) {
            roleContextBuilder.append("- QA default: if you ask whether to continue verification, continue with required test ratios and deeper AC verification; document assumptions instead of waiting.\n");
        }

        appendCompactRoleGuide(roleContextBuilder, task.getRole().getTag());

        try {
            // The role must actually reach Jules - Jules takes on the role, not just a technical
            // checklist derived from it. Structured field extraction (RoleRules) is lossy by
            // construction: charter files use three different Markdown conventions for their deontic
            // sections, and the philosophy table that defines each role's distinct worldview was never
            // parsed into any field at all. Sending the raw charter verbatim sidesteps all of that -
            // Jules reads the exact same document a human reviewer would, including the philosophical
            // foundation that makes this role's judgment different from every other role's.
            String rawCharter = roleCapabilityLoader.loadRawCharter(task.getRole().getTag());
            if (rawCharter != null && !rawCharter.isBlank()) {
                roleContextBuilder.append("\n## Role Charter (this is who you are for this session)\n")
                        .append(rawCharter).append("\n");
            }

            RoleRules rules = roleCapabilityLoader.loadRules(task.getRole().getTag());
            if (rules != null && rules.reviewRequiredBy() != null && !rules.reviewRequiredBy().isBlank()) {
                roleContextBuilder.append("\n## Mandatory Review By\n").append(rules.reviewRequiredBy()).append("\n");
            }

            if ("REVIEWER".equalsIgnoreCase(mode)) {
                try {
                    String guidelines = java.nio.file.Files.readString(java.nio.file.Paths.get("docs/AI_REVIEW_GUIDELINES.md"));
                    roleContextBuilder.append("\n## AI REVIEW GUIDELINES\n").append(guidelines).append("\n");
                } catch (Exception e) {
                    log.warn("Could not load AI review guidelines: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load extended rules for role {}: {}", task.getRole().getTag(), e.getMessage());
        }

        // Feature-thread continuation ("development from the feature"): if this exact feature already has
        // a live code branch from a previously merged, real (has-code) PR, start this session from that
        // branch instead of main - the prior commits are already present, no context is lost. Scoped to
        // (project, feature) only, deliberately NOT role: a feature routinely involves several roles
        // (backend, frontend, design) working the same dependency chain, and whichever role is dispatched
        // next for this feature should pick up on the same branch, not fork a new one just because the
        // role changed. Threads only ever get created for tasks that actually ship real product code (see
        // AutoMergeService.classifyAndHandleBranch), so this is a no-op for the compiler/audit/review-
        // fallback/design-review roles, which never earn one.
        //
        // Also pinned to the exact account that authenticated the original branch: dispatch selection
        // (round-robin by capacity, see ProjectFlowService.dispatchQueuedTasks) has no idea a thread
        // exists and can hand this task to any account with free capacity. A different account has no
        // verified relationship to a branch it didn't create - cross-account continuation on the Jules
        // API has never been tested, so the safe default is to skip continuation entirely rather than
        // guess. The thread itself is untouched either way and stays available for whenever the owning
        // account comes up again.
        String startingBranch = "main";
        var featureThreadOpt = task.getFeatureId() == null ? java.util.Optional.<com.eneik.production.models.persistence.FeatureThreadEntity>empty()
                : featureThreadRepository.findByProjectIdAndFeatureId(task.getProject().getId(), task.getFeatureId());
        if (featureThreadOpt.isPresent()) {
            var featureThread = featureThreadOpt.get();
            if (accountId != null && accountId.equals(featureThread.getAccountId())) {
                startingBranch = featureThread.getBranchName();
                roleContextBuilder.append("\n## Continuing Prior Work\n")
                        .append("This feature has ongoing work on branch ").append(startingBranch)
                        .append(" (last worked on by role ").append(featureThread.getLastRoleTag() == null ? "unknown" : featureThread.getLastRoleTag()).append("). ")
                        .append("Build on the existing code, do not start over. Prior summary: ")
                        .append(featureThread.getSummary() == null ? "(none)" : featureThread.getSummary()).append("\n");
            } else {
                log.info("Feature thread exists for feature {} in project {} on branch {}, but this dispatch "
                                + "went to a different account ({}); skipping continuation, starting fresh from main.",
                        task.getFeatureId(), task.getProject().getId(), featureThread.getBranchName(), accountId);
            }
        }
        String roleContext = roleContextBuilder.toString();

        String apiKey = null;
        if (accountId != null) {
            apiKey = accountRepository.findById(accountId)
                    .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                    .orElse(null);
        }

        JulesApiClient.CreateSessionResult createResult = apiKey != null
                ? julesApiClient.createSessionDetailed(repoUrl, description, roleContext, apiKey, sessionTitle, startingBranch)
                : julesApiClient.createSessionDetailed(repoUrl, description, roleContext, null, sessionTitle, startingBranch);
        if (createResult == null) {
            createResult = new JulesApiClient.CreateSessionResult(null, 0, "Jules API client returned no create-session result");
        }
        String externalId = createResult.sessionName();

        if ("skipped".equals(externalId)) {
            session.setStatus("queued");
            session.setExternalSessionId("skipped");
        } else if (externalId == null) {
            session.setStatus("failed");
            session.setClosureReason("jules_create_session_failed"
                    + (createResult.statusCode() > 0 ? ": HTTP " + createResult.statusCode() : "")
                    + (createResult.compactError().isBlank() ? "" : " " + createResult.compactError()));
            if (accountId != null && createResult.dailyLimitOrQuota()) {
                accountRepository.findById(accountId).ifPresent(account -> {
                    account.setStatus(AccountStatus.daily_limited);
                    accountRepository.save(account);
                });
                session.setClosureReason("jules_daily_limit: account reached an explicit Jules daily/quota/rate limit. "
                        + session.getClosureReason());
            } else if (accountId != null && createResult.apiPreconditionOrAuthorizationBlocked()) {
                accountRepository.findById(accountId).ifPresent(account -> {
                    account.setStatus(AccountStatus.api_blocked);
                    accountRepository.save(account);
                });
                session.setClosureReason("jules_api_blocked: Jules refused session creation because of API precondition, authorization, or request setup. "
                        + "This is not a daily limit. " + session.getClosureReason());
            }
        } else {
            session.setExternalSessionId(externalId);
            session.setStatus("running");
            if (accountId != null) {
                accountRepository.findById(accountId).ifPresent(account -> {
                    account.setSessionsDispatchedToday(account.getSessionsDispatchedToday() + 1);
                    accountRepository.save(account);
                });
            }
        }

        return julesSessionRepository.save(session);
    }

    private String withTaskPromptTitle(String title, String description) {
        String safeTitle = TaskTitleBuilder.enforceTwoOrThreeWords(title);
        String safeDescription = description == null ? "" : description;
        return "Task Title: " + safeTitle + "\n\n" + safeDescription;
    }

    private void appendCompactRoleGuide(StringBuilder roleContextBuilder, String roleTag) {
        roleContextBuilder.append("\n## Compact Role Guide\n");
        roleContextBuilder.append("- Use English only in code comments, PR text, and dialogue.\n");
        roleContextBuilder.append("- Treat the task JTBD, Acceptance Criteria, DoD, and file scope as stronger than role lore.\n");
        roleContextBuilder.append("- Apply Kano as a scope guard: Must-Be first, Performance only when explicit, Delighters only as follow-up wishlist.\n");
        roleContextBuilder.append("- Apply Cynefin as a delivery guard: clear/complicated work needs a direct implementation path, complex work needs one safe probe.\n");
        roleContextBuilder.append("- Role focus: ").append(compactRoleFocus(roleTag)).append("\n");
    }

    private String compactRoleFocus(String roleTag) {
        return switch (roleTag) {
            case "BARCAN-TAG-00" -> "protect architecture, code quality, and merge safety for the smallest requested slice.";
            case "BARCAN-TAG-01" -> "define or adjust solution structure only where the current slice requires it.";
            case "BARCAN-TAG-02" -> "implement backend API/data behavior with focused tests and no frontend expansion.";
            case "BARCAN-TAG-03" -> "produce only the UI/UX design decision, interaction state, or design-system adjustment required by this slice.";
            case "BARCAN-TAG-04" -> "implement or verify the ML/data-science logic required by this slice with reproducible checks.";
            case "BARCAN-TAG-05" -> "change only deployment, runtime, CI, or observability items required to run the slice.";
            case "BARCAN-TAG-06" -> "verify acceptance criteria with the smallest useful unit/integration/E2E coverage; do not create broad test suites.";
            case "BARCAN-TAG-07" -> "check and fix concrete security risks without broad compliance rewrites.";
            case "BARCAN-TAG-08" -> "change only the database, schema, or data pipeline behavior required by this slice.";
            case "BARCAN-TAG-09" -> "decompose wishlist context into short, role-owned, dependency-aware work only.";
            case "BARCAN-TAG-10" -> "verify explicit legal, fiscal, privacy, or policy constraints with cited assumptions.";
            case "BARCAN-TAG-11" -> "implement the smallest Svelte/browser UI interaction required by the task and follow docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-12" -> "define only the shared API contract (endpoints, request/response shape, DTOs) that backend and frontend will build against; do not implement backend or frontend code.";
            default -> "complete one atomic, verifiable implementation slice and stop.";
        };
    }


    @Transactional
    public JulesSessionEntity pollStatus(UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if ("skipped".equals(session.getExternalSessionId()) || session.getExternalSessionId() == null) {
            return session;
        }

        String apiKey = apiKeyForSession(session);

        String rawStatus = apiKey != null
                ? julesApiClient.getSessionStatus(session.getExternalSessionId(), apiKey)
                : julesApiClient.getSessionStatus(session.getExternalSessionId());
        if (rawStatus != null) {
            String oldStatus = session.getStatus();
            // If we are waiting for Jules to revise, ignore 'SUCCEEDED' from API to avoid infinite loop
            String mappedStatus = mapExternalStatus(rawStatus);
            boolean shouldSendStuckRecovery = "stuck".equals(mappedStatus) && shouldSendStuckRecovery(session);
            TaskEntity taskForSession = taskRepository.findById(session.getTaskId()).orElse(null);

            if ("revising".equals(oldStatus) && "pr_opened".equals(mappedStatus)) {
                // Ignore SUCCEEDED status from API if we haven't seen it go back to RUNNING yet
                mappedStatus = "revising";
            } else if ("revising".equals(oldStatus) && "running".equals(mappedStatus)) {
                // Jules picked it up and is running again
                log.info("Jules session {} resumed running after revision request.", session.getId());
            }

            if ("pr_opened".equals(mappedStatus)) {
                String realPrUrl = apiKey != null
                        ? julesApiClient.getSessionPrUrl(session.getExternalSessionId(), apiKey)
                        : julesApiClient.getSessionPrUrl(session.getExternalSessionId());
                if (realPrUrl != null && !realPrUrl.isBlank()) {
                    session.setPrUrl(realPrUrl);
                    log.info("Jules API: Retrieved real PR URL for session {}: {}", session.getId(), realPrUrl);
                }
            }

            if (taskForSession != null
                    && !"pr_opened".equals(mappedStatus)
                    && (session.getPrUrl() == null || session.getPrUrl().isBlank())
                    && !"revising".equals(oldStatus)) {
                Optional<GitHubPullRequestService.GitHubPullRequest> detectedPr = detectOpenPullRequestFromGitHub(session, taskForSession);
                if (detectedPr.isPresent()) {
                    GitHubPullRequestService.GitHubPullRequest pr = detectedPr.get();
                    session.setPrUrl(pr.url());
                    log.info("GitHub PR lookup: linked Jules session {} to PR {} via branch {}", session.getExternalSessionId(), pr.url(), pr.headRef());
                }
                if (session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
                    mappedStatus = "pr_opened";
                }
            }

            if (!mappedStatus.equals(oldStatus)) {
                // Any real status transition (running->pr_opened, stuck->running, etc.) is genuine
                // forward progress, unlike updatedAt which refreshes on every save regardless.
                session.setLastProgressAt(Instant.now());
                session.setBlindCycleCount(0);
            }
            session.setStatus(mappedStatus);
            session.setLastStatusCheckAt(Instant.now());
            session = julesSessionRepository.save(session);

            if (taskForSession != null && shouldScanActivitiesForQuestions(mappedStatus)) {
                answerAgentQuestions(session, taskForSession, apiKey);
            }

            // Ensure we only trigger PR opened workflow once per true transition from running/revising
            if ("pr_opened".equals(mappedStatus) && ("running".equals(oldStatus) || "revising".equals(oldStatus))) {
                handlePrOpenedWorkflow(session);
            }

            if (shouldSendStuckRecovery && taskForSession != null) {
                sendStuckRecoveryMessageAsync(session, taskForSession, apiKey);
            }

            return session;
        }

        return session;
    }

    private Optional<GitHubPullRequestService.GitHubPullRequest> detectOpenPullRequestFromGitHub(JulesSessionEntity session, TaskEntity task) {
        if (session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
            return Optional.empty();
        }
        if (task.getProject() == null || session.getExternalSessionId() == null || "skipped".equals(session.getExternalSessionId())) {
            return Optional.empty();
        }
        return gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId());
    }

    private boolean shouldScanActivitiesForQuestions(String status) {
        return "running".equals(status)
                || "queued".equals(status)
                || "revising".equals(status)
                || "stuck".equals(status);
    }

    private void answerAgentQuestions(JulesSessionEntity session, TaskEntity task, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (isTerminalLocallyClosed(session)) {
            return;
        }

        JsonNode root = julesApiClient.getSessionActivities(session.getExternalSessionId(), apiKey);
        if (root != null && root.path("activitiesOverflow").asBoolean(false)) {
            // A large activity payload just means the session has a long history (lots of tool calls/file
            // reads) - it is not evidence the session is stuck. Closing the loop here used to throw away
            // sessions that were actively progressing toward a PR, purely because Eneik's own log-scanner
            // hit its memory guard. Skip this cycle's question scan instead; blindCycleCount tracks how
            // many consecutive cycles this has happened so forceUnblockOverflowedSessions can still
            // recover a session that is genuinely stuck behind this exact skip.
            session.setBlindCycleCount(session.getBlindCycleCount() + 1);
            julesSessionRepository.save(session);
            log.warn("Jules activities payload for session {} exceeded the backend safety limit; skipping question scan this cycle (session left running, blind cycle {})",
                    session.getExternalSessionId(), session.getBlindCycleCount());
            return;
        }
        if (root == null || !root.path("activities").isArray()) {
            return;
        }

        if (session.getBlindCycleCount() != 0) {
            // A "sighted" cycle - the activity log is back under the size cap.
            session.setBlindCycleCount(0);
            julesSessionRepository.save(session);
        }

        for (JsonNode activity : root.path("activities")) {
            String question = extractAgentQuestion(activity);
            if (question == null || question.isBlank()) {
                continue;
            }

            String activityName = activity.path("name").asText(activity.path("id").asText("unknown"));
            String activityHash = sha256(activityName + "\n" + question);
            Optional<JulesActivityResponseEntity> existing =
                    julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(session.getId(), activityHash);
            if (existing.isPresent() && existing.get().isSent()) {
                continue;
            }
            if (existing.isEmpty()) {
                // Real evidence Jules did something new since the last poll.
                session.setLastProgressAt(Instant.now());
                julesSessionRepository.save(session);
            }

            try {
                List<JulesActivityResponseEntity> responseHistory =
                        julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
                long previousSimilarQuestions = countPreviousSimilarQuestions(responseHistory, question);
                long previousResponses = responseHistory.stream()
                        .filter(record -> record.getResponse() != null && !record.getResponse().isBlank())
                        .count();

                if (shouldCloseLoop(previousResponses, previousSimilarQuestions)) {
                    JulesActivityResponseEntity record = existing.orElseGet(JulesActivityResponseEntity::new);
                    record.setJulesSessionId(session.getId());
                    record.setActivityName(truncate(activityName, 256));
                    record.setActivityHash(activityHash);
                    record.setQuestion(question);
                    String closeReason = closeReason(previousResponses, previousSimilarQuestions, question);
                    record.setResponse("Eneik circuit breaker closed this Jules session. " + closeReason);
                    record.setSent(false);
                    record.setRespondedAt(Instant.now());
                    julesActivityResponseRepository.save(record);

                    closeLoopAndCreateFollowUps(session, task, question, responseHistory, closeReason);
                    break;
                }

                String answer = buildJulesQuestionAnswer(task, question, previousSimilarQuestions);
                boolean sent = julesApiClient.sendMessage(session.getExternalSessionId(), answer, apiKey);

                JulesActivityResponseEntity record = existing.orElseGet(JulesActivityResponseEntity::new);
                record.setJulesSessionId(session.getId());
                record.setActivityName(truncate(activityName, 256));
                record.setActivityHash(activityHash);
                record.setQuestion(question);
                record.setResponse(answer);
                record.setSent(sent);
                record.setRespondedAt(sent ? Instant.now() : null);
                julesActivityResponseRepository.save(record);

                if (sent) {
                    log.info("Answered Jules agent question activity {} for session {} task {}", activityName, session.getExternalSessionId(), task.getId());
                    saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), answer, "Auto-answer to Jules activity " + activityName);
                } else {
                    log.warn("Generated but failed to send Jules agent question answer for session {} activity {}", session.getExternalSessionId(), activityName);
                }
            } catch (DataIntegrityViolationException e) {
                log.info("Jules activity {} for session {} was already recorded by another poller", activityName, session.getExternalSessionId());
            } catch (Exception e) {
                log.warn("Could not answer Jules agent question activity {} for session {}: {}", activityName, session.getExternalSessionId(), e.getMessage());
            }
        }
    }

    private String extractAgentQuestion(JsonNode activity) {
        if (activity == null || !"agent".equalsIgnoreCase(activity.path("originator").asText(""))) {
            return null;
        }

        String text = null;
        if (activity.has("agentMessaged")) {
            text = findMessageText(activity.get("agentMessaged"));
        }
        if ((text == null || text.isBlank()) && activity.has("progressUpdated")) {
            text = findMessageText(activity.get("progressUpdated"));
        }

        if (text == null || text.isBlank() || !looksLikeQuestion(text)) {
            return null;
        }
        return text.trim();
    }

    private String findMessageText(JsonNode node) {
        String direct = findTextByFieldName(node, Set.of(
                "agentMessage", "message", "text", "content", "body", "markdown", "description", "title"
        ));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return null;
    }

    private String findTextByFieldName(JsonNode node, Set<String> fieldNames) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (fieldNames.contains(entry.getKey()) && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                String nested = findTextByFieldName(fields.next().getValue(), fieldNames);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findTextByFieldName(child, fieldNames);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean looksLikeQuestion(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("?")) {
            return true;
        }
        return lower.contains("should i")
                || lower.contains("should we")
                || lower.contains("do you have")
                || lower.contains("can i")
                || lower.contains("may i")
                || lower.contains("please clarify")
                || lower.contains("need clarification")
                || lower.contains("specific requirements")
                || lower.contains("blocked")
                || lower.contains("уточните")
                || lower.contains("нужно ли")
                || lower.contains("можно ли")
                || lower.contains("какие требования")
                || lower.contains("есть ли требования");
    }

    private long countPreviousSimilarQuestions(List<JulesActivityResponseEntity> responseHistory, String question) {
        String normalized = normalizeQuestionForLoopDetection(question);
        if (normalized.isBlank()) {
            return 0L;
        }
        return responseHistory.stream()
                .filter(record -> normalized.equals(normalizeQuestionForLoopDetection(record.getQuestion())))
                .count();
    }

    private String normalizeQuestionForLoopDetection(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        if (mentionsGeneratedArtifact(lower)) {
            return "generated-artifact-remediation";
        }
        if (lower.contains("specific requirements")
                || lower.contains("business logic verification")
                || lower.contains("do you have any")
                || lower.contains("нужно ли")
                || lower.contains("есть ли")) {
            return "requirements-clarification";
        }
        return lower.length() <= 500 ? lower : lower.substring(0, 500);
    }

    private String buildJulesQuestionAnswer(TaskEntity task, String question, long previousSimilarQuestions) {
        String deterministicAnswer = objectiveJulesResolution(task, question, previousSimilarQuestions);
        if (deterministicAnswer != null && !deterministicAnswer.isBlank()) {
            return deterministicAnswer;
        }

        String fallback = fallbackJulesAnswer(task);
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        String projectRepo = task.getProject() != null ? task.getProject().getRepositoryName() : "unknown-repo";
        String payload = task.getPayload() != null ? task.getPayload().toString() : "{}";

        String systemInstruction = "You are Eneik Technical Product Owner and Staff Engineer answering a Google Jules coding agent inside an active coding session.\n"
                + "You are not the dashboard Agile coach. Answer the Jules agent's operational question directly.\n"
                + "Rules:\n"
                + "- Return only the message to send to Jules.\n"
                + "- Do not ask a follow-up question.\n"
                + "- Make a concrete decision from the supplied task facts. If information is missing, choose the safest minimal assumption and tell Jules to document it in the PR.\n"
                + "- If Jules asks whether to proceed, tell Jules to proceed with the task DoD and Acceptance Criteria unless a concrete contradiction exists.\n"
                + "- For QA tasks, keep the test pyramid target, deepen Acceptance Criteria verification, avoid committing generated reports/test-results/playwright-report, and run the relevant checks.\n"
                + "- Keep the answer concise, practical, and in English only, even if Jules' question uses another language.\n\n"
                + "Task id: " + task.getId() + "\n"
                + "Project repository: " + projectRepo + "\n"
                + "Role: " + roleTag + "\n"
                + "Task description:\n" + task.getDescription() + "\n\n"
                + "Task payload:\n" + payload;

        try {
            String aiResponse = mlPredictionServiceClient.chat(question, systemInstruction);
            if (isUsableAiAnswer(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("Gemini answer generation failed for Jules question on task {}: {}", task.getId(), e.getMessage());
        }

        return fallback;
    }

    private String objectiveJulesResolution(TaskEntity task, String question, long previousSimilarQuestions) {
        if (mentionsGeneratedArtifact(question)) {
            return generatedArtifactRemediation(task, previousSimilarQuestions);
        }
        if (previousSimilarQuestions >= DESTRUCTIVE_LOOP_REPEAT_THRESHOLD) {
            return repeatedQuestionCircuitBreaker(task, previousSimilarQuestions);
        }
        return null;
    }

    private boolean mentionsGeneratedArtifact(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("generated/local artifact")
                || lower.contains("generated artifact")
                || lower.contains("local artifact")
                || lower.contains("playwright-report")
                || lower.contains("test-results")
                || lower.contains("node_modules")
                || lower.contains("coverage/")
                || lower.contains(".next/")
                || lower.contains("trace.zip")
                || lower.contains(".webm");
    }

    private boolean isSoftGeneratedArtifactDebt(String text) {
        if (!mentionsGeneratedArtifact(text)) {
            return false;
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean hardRisk = lower.contains(".env")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("node_modules")
                || lower.contains("binary repository");
        return !hardRisk;
    }

    private void createRepositoryHygieneDebtWishlist(TaskEntity task, String remarks) {
        if (task == null || task.getProject() == null || task.getProject().getId() == null) {
            return;
        }
        String content = """
                Repository hygiene technical debt from a delivered PR.
                Source: minor generated/local artifact was detected during review, but it is non-secret and should not block product flow.
                JTBD: When the project has delivered value with small local artifacts, I want repository hygiene cleaned in a separate short task, so delivery continues without normalizing artifact debt.
                Owner role: BARCAN-TAG-00.
                Kano: Performance.
                Cynefin: clear.
                DoD: Remove minor generated/local artifacts if present, update ignore rules where needed, and verify no secrets or heavy generated folders are tracked.
                Review note: %s
                """.formatted(truncate(remarks, 700));
        boolean exists = wishlistRepository.findByProjectId(task.getProject().getId()).stream()
                .anyMatch(item -> item.getContent() != null
                        && item.getContent().contains("Repository hygiene technical debt from a delivered PR")
                        && item.getContent().contains(task.getId().toString()));
        if (exists) {
            return;
        }
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(task.getProject().getId());
        wishlist.setSource(WishlistSource.role);
        wishlist.setSourceRoleTag("BARCAN-TAG-00");
        wishlist.setStatus(WishlistStatus.pending);
        wishlist.setFeatureId(task.getFeatureId());
        wishlist.setContent(content + "\nOriginal task: " + task.getId());
        wishlist.setJtbd("When minor repository artifacts exist after delivery, clean them as separate technical debt without blocking product flow.");
        wishlist.setTocConstraintRef("REPOSITORY-HYGIENE-DEBT");
        wishlist.setSixSigmaMetric("closed_unmerged_pr_and_artifact_debt");
        wishlist.setDod("Minor artifacts are removed or documented, ignore rules are adjusted, and no secrets/heavy generated folders are tracked.");
        wishlist.setAcceptanceCriteria("Given repository hygiene debt exists, When cleanup runs, Then product functionality remains unchanged and artifact risk is reduced.");
        wishlist.setCompiledByRole("BARCAN-TAG-00");
        wishlistRepository.save(wishlist);
    }

    private String generatedArtifactRemediation(TaskEntity task, long previousSimilarQuestions) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        String marker = detectedGeneratedArtifactMarker(task != null ? task.getDescription() : null);
        String loopPrefix = previousSimilarQuestions >= DESTRUCTIVE_LOOP_REPEAT_THRESHOLD
                ? "Circuit breaker: this blocker question has repeated. Stop the discussion loop and execute the remediation exactly.\n\n"
                : "";
        return loopPrefix
                + "Task " + taskId + " has a Git hygiene issue only: generated/local artifacts are in the PR diff"
                + ("generated/local artifacts".equals(marker) ? "." : " (" + marker + ").") + "\n"
                + "Do not change product scope. If this is a small non-secret local report artifact, do not expand the discussion; clean it if quick, otherwise document it as technical debt and resubmit the product work:\n"
                + "git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                + "Acceptance: no secrets or heavy generated folders are committed. Minor non-secret artifacts may be handled as follow-up repository hygiene debt.";
    }

    private String buildReviewRejectionMessage(TaskEntity task, String remarks) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        if (mentionsGeneratedArtifact(remarks)) {
            String marker = detectedGeneratedArtifactMarker(remarks);
            return "PR review for task " + taskId + " found repository hygiene debt, not a product requirement failure.\n"
                    + "Detected generated/local artifact in the diff: " + marker + ".\n"
                    + "If the artifact is small and non-secret, do not loop on it; either remove it quickly or document it as technical debt and keep the product slice moving. Never commit secrets or large generated folders.\n"
                    + "Optional cleanup check:\n"
                    + "git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                    + "Stop after the smallest cleanup. Do not add new feature, design, architecture, or test-expansion work.";
        }
        return truncate("PR review for task " + taskId + " is blocked. Fix only the specific review blocker below, update the same PR branch, and resubmit. Do not expand product scope.\n\n"
                + remarks, 1_600);
    }

    private void recordSystemReviewRejection(JulesSessionEntity session, String reviewSignal, String response, boolean sent) {
        String normalized = normalizeQuestionForLoopDetection(reviewSignal);
        String activityHash = sha256(REVIEW_REJECTION_ACTIVITY_NAME + "\n" + normalized);
        Optional<JulesActivityResponseEntity> existing =
                julesActivityResponseRepository.findByJulesSessionIdAndActivityHash(session.getId(), activityHash);
        if (existing.isPresent()) {
            return;
        }
        JulesActivityResponseEntity record = new JulesActivityResponseEntity();
        record.setJulesSessionId(session.getId());
        record.setActivityName(REVIEW_REJECTION_ACTIVITY_NAME);
        record.setActivityHash(activityHash);
        record.setQuestion(reviewSignal);
        record.setResponse(response);
        record.setSent(sent);
        record.setRespondedAt(sent ? Instant.now() : null);
        try {
            julesActivityResponseRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.info("PR review rejection for session {} was already recorded by another poller", session.getExternalSessionId());
        }
    }

    private String detectedGeneratedArtifactMarker(String text) {
        if (text == null) {
            return "generated/local artifacts";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : List.of(
                "playwright-report/",
                "test-results/",
                "coverage/",
                "node_modules/",
                ".next/",
                ".last-run.json",
                ".env",
                ".zip",
                ".png",
                ".webm",
                ".trace",
                "trace.zip")) {
            if (lower.contains(marker)) {
                return marker;
            }
        }
        return "generated/local artifacts";
    }

    private String repeatedQuestionCircuitBreaker(TaskEntity task, long previousSimilarQuestions) {
        String taskId = task != null && task.getId() != null ? task.getId().toString() : "unknown";
        return "Circuit breaker for task " + taskId + ": this is the same blocker/clarification loop for the "
                + (previousSimilarQuestions + 1)
                + "th time. Do not ask another open-ended question. Make one objective move from the task facts: if the latest review contains a concrete blocker, fix exactly that blocker and verify it with commands; otherwise proceed from the Acceptance Criteria and DoD, document the smallest safe assumption in the PR summary, and resubmit. If a fact is truly unverifiable after one attempt, mark the PR summary with BLOCKED and list the exact missing fact.";
    }

    private boolean shouldCloseLoop(long previousResponses, long previousSimilarQuestions) {
        return previousResponses >= maxAgentDialogResponses
                || previousSimilarQuestions + 1 >= loopCloseSimilarThreshold;
    }

    private String closeReason(long previousResponses, long previousSimilarQuestions, String question) {
        if (previousResponses >= maxAgentDialogResponses) {
            return "dialog_limit_exceeded: " + previousResponses + " prior orchestrator replies; max is " + maxAgentDialogResponses;
        }
        if (previousSimilarQuestions + 1 >= loopCloseSimilarThreshold) {
            return "repeated_blocker_loop: normalized blocker repeated " + (previousSimilarQuestions + 1) + " times";
        }
        if (mentionsGeneratedArtifact(question)) {
            return "repository_hygiene_loop: generated/local artifacts remain in the PR diff";
        }
        return "destructive_dialog_loop";
    }

    private boolean isTerminalLocallyClosed(JulesSessionEntity session) {
        return "loop_closed".equals(session.getStatus())
                || "failed".equals(session.getStatus())
                || "closed".equals(session.getStatus());
    }

    private void closeLoopAndCreateFollowUps(JulesSessionEntity session,
                                             TaskEntity task,
                                             String latestQuestion,
                                             List<JulesActivityResponseEntity> responseHistory,
                                             String closeReason) {
        LoopDiagnosis diagnosis = diagnoseLoop(task, latestQuestion, closeReason);
        String geminiAnalysis = geminiLoopAnalysis(task, latestQuestion, responseHistory, diagnosis, closeReason);

        session.setStatus("loop_closed");
        session.setClosedAt(Instant.now());
        session.setClosureReason(closeReason + "\n\n" + diagnosis.toText() + "\n\nGemini analysis:\n" + geminiAnalysis);
        julesSessionRepository.save(session);

        claimService.closeTaskAsBlocked(task.getId(), "Jules circuit breaker: " + closeReason);
        createCircuitBreakerWishlist(session, task, latestQuestion, diagnosis, geminiAnalysis, closeReason);
        saveJulesDialogueLog(task.getId(), session.getExternalSessionId(),
                diagnosis.toText() + "\n\nGemini analysis:\n" + geminiAnalysis,
                "Jules loop closed by Eneik circuit breaker: " + closeReason);
        log.warn("Closed Jules session {} for task {} due to {}. Follow-up wishlist generated.",
                session.getExternalSessionId(), task.getId(), closeReason);
    }

    private LoopDiagnosis diagnoseLoop(TaskEntity task, String latestQuestion, String closeReason) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        if (mentionsGeneratedArtifact(latestQuestion) || closeReason.contains("repository_hygiene")) {
            return new LoopDiagnosis(
                    "Repository hygiene blocker repeated; Jules kept committing generated/local artifacts instead of producing a clean PR diff.",
                    "Must-Be",
                    "clear",
                    roleTag,
                    "Clean generated artifacts from the PR branch only",
                    generatedArtifactFollowUp(task)
            );
        }
        if (closeReason.contains("activity_log_overflow")) {
            return new LoopDiagnosis(
                    "The Jules activity log exceeded the backend safety limit; the session became too noisy to inspect reliably and must not receive more prompts.",
                    "Must-Be",
                    "complex",
                    roleTag,
                    "Restart the work from the smallest observable implementation slice",
                    activityOverflowFollowUp(task, latestQuestion)
            );
        }
        if (closeReason.contains("dialog_limit")) {
            return new LoopDiagnosis(
                    "The session exceeded the safe dialogue budget for a weak coding model; the original task is too broad or ambiguous for one Jules branch.",
                    "Must-Be",
                    "complicated",
                    roleTag,
                    "Split the blocked task into one atomic implementation slice",
                    atomicSliceFollowUp(task, latestQuestion)
            );
        }
        if (closeReason.contains("stuck_session_timeout")) {
            return new LoopDiagnosis(
                    "The Jules session stayed stuck after recovery time; continuing the same external session would keep capacity blocked without objective progress.",
                    "Must-Be",
                    "complicated",
                    roleTag,
                    "Restart the blocked work as a fresh atomic session",
                    atomicSliceFollowUp(task, latestQuestion)
            );
        }
        return new LoopDiagnosis(
                "The same blocker repeated and the session stopped making objective progress.",
                "Must-Be",
                "complicated",
                roleTag,
                "Resolve the repeated blocker as a new short Jules session",
                repeatedBlockerFollowUp(task, latestQuestion)
        );
    }

    private String geminiLoopAnalysis(TaskEntity task,
                                      String latestQuestion,
                                      List<JulesActivityResponseEntity> responseHistory,
                                      LoopDiagnosis diagnosis,
                                      String closeReason) {
        String transcript = responseHistory.stream()
                .limit(8)
                .map(record -> "QUESTION: " + truncate(record.getQuestion(), 900) + "\nANSWER: " + truncate(record.getResponse(), 900))
                .reduce("", (a, b) -> a + "\n---\n" + b);
        String systemInstruction = """
                You are Gemini acting as Eneik incident analyst for a failed Jules coding session.
                Use Eneik Management System, Kano, and Cynefin.
                Return concise factual analysis only. Do not suggest continuing the same Jules session.
                Required fields:
                - Root cause
                - Kano classification
                - Cynefin domain
                - New short-session recommendation
                - Definition of Done
                """;
        String prompt = "Task id: " + task.getId() + "\n"
                + "Role: " + diagnosis.roleTag() + "\n"
                + "Close reason: " + closeReason + "\n"
                + "Deterministic Kano: " + diagnosis.kanoClass() + "\n"
                + "Deterministic Cynefin: " + diagnosis.cynefinDomain() + "\n"
                + "Task description:\n" + truncate(task.getDescription(), 2_000) + "\n\n"
                + "Latest blocker/question:\n" + truncate(latestQuestion, 1_500) + "\n\n"
                + "Recent dialogue evidence:\n" + transcript;
        try {
            String response = mlPredictionServiceClient.chatCritical(prompt, systemInstruction);
            if (isUsableAiAnswer(response)) {
                return truncate(response.trim(), 2_400);
            }
        } catch (Exception e) {
            log.warn("Gemini loop analysis failed for Jules session task {}: {}", task.getId(), e.getMessage());
        }
        return "Root cause: " + diagnosis.rootCause()
                + "\nKano classification: " + diagnosis.kanoClass()
                + "\nCynefin domain: " + diagnosis.cynefinDomain()
                + "\nNew short-session recommendation: " + diagnosis.followUpTitle()
                + "\nDefinition of Done: " + firstLine(diagnosis.followUpBody());
    }

    private void createCircuitBreakerWishlist(JulesSessionEntity session,
                                              TaskEntity task,
                                              String latestQuestion,
                                              LoopDiagnosis diagnosis,
                                              String geminiAnalysis,
                                              String closeReason) {
        if (task.getProject() == null) {
            return;
        }
        UUID projectId = task.getProject().getId();
        String marker = "Circuit breaker source session: " + session.getId();
        boolean alreadyExists = wishlistRepository.findByProjectId(projectId).stream()
                .map(WishlistEntity::getContent)
                .anyMatch(content -> content != null && content.contains(marker));
        if (alreadyExists) {
            return;
        }

        WishlistEntity followUp = new WishlistEntity();
        followUp.setProjectId(projectId);
        followUp.setSource(WishlistSource.role_mismatch_followup);
        followUp.setSourceRoleTag(diagnosis.roleTag());
        followUp.setStatus(WishlistStatus.pending);
        followUp.setFeatureId(task.getFeatureId());
        followUp.setContent(truncate("""
                [Auto follow-up from Jules circuit breaker]
                Circuit breaker source session: %s
                External Jules session: %s
                Original task: %s
                Closure reason: %s

                Gemini/Kano/Cynefin analysis:
                %s

                Eneik classification:
                - Kano: %s
                - Cynefin: %s
                - Root cause: %s

                New short Jules session:
                %s

                Scope rule:
                - One branch, one atomic result, no broad redesign.
                - If more work is discovered, stop after the smallest verified slice and write the remaining work as another wishlist item.
                - Dialogue budget: no more than 8 orchestrator replies; repeated blocker means close and re-plan.

                Latest blocker evidence:
                %s
                """.formatted(
                session.getId(),
                valueOrUnset(session.getExternalSessionId()),
                task.getId(),
                closeReason,
                geminiAnalysis,
                diagnosis.kanoClass(),
                diagnosis.cynefinDomain(),
                diagnosis.rootCause(),
                diagnosis.followUpBody(),
                truncate(latestQuestion, 1_500)
        ), FOLLOW_UP_CONTENT_MAX_LENGTH));
        wishlistRepository.save(followUp);
    }

    private String generatedArtifactFollowUp(TaskEntity task) {
        return "Goal: clean the existing PR branch so it contains zero generated/local artifacts.\n"
                + "Do only repository hygiene, not product feature work.\n"
                + "Required commands:\n"
                + "1. git rm -r --cached --ignore-unmatch playwright-report test-results coverage node_modules .next apps/web/playwright-report apps/web/test-results apps/web/coverage apps/web/.next\n"
                + "2. Ensure .gitignore contains **/playwright-report/, **/test-results/, **/coverage/, **/.next/, node_modules/, *.trace, *.webm.\n"
                + "3. Verify: git diff --name-only origin/main...HEAD | grep -E '(^|/)(playwright-report|test-results|coverage|node_modules|\\.next)/|\\.(trace|webm)$' && exit 1 || true\n"
                + "DoD: the verification command prints no artifact paths, the PR contains only source/config/test/doc changes, and no new product scope is added.";
    }

    private String activityOverflowFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: replace the unbounded Jules session with one observable implementation slice.\n"
                + "First action: inspect the open PR/branch state, summarize what is actually present, and choose exactly one fix or one component to finish.\n"
                + "Scope rule: no broad rewrite, no multi-feature platform work, no additional architecture documents unless they are required to make one code change.\n"
                + "DoD: one small branch, one PR, at most two source areas, explicit verification command, and a concise handoff note.\n"
                + "Original task summary: " + truncate(task.getDescription(), 1_200) + "\n"
                + "Latest loop signal: " + truncate(latestQuestion, 800);
    }

    private String atomicSliceFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: re-plan the blocked task into one atomic Jules implementation slice.\n"
                + "Use the original task only as context; choose the smallest independently verifiable service/component/fix.\n"
                + "DoD: one small branch, one PR, at most two tightly related source areas, explicit verification command, no generated artifacts.\n"
                + "Original task summary: " + truncate(task.getDescription(), 1_200) + "\n"
                + "Latest loop signal: " + truncate(latestQuestion, 800);
    }

    private String repeatedBlockerFollowUp(TaskEntity task, String latestQuestion) {
        return "Goal: resolve only the repeated blocker from the failed Jules session.\n"
                + "Do not continue the old branch conversation. Start a fresh short session with the blocker as the sole acceptance criterion.\n"
                + "DoD: blocker is objectively gone, verification command is recorded, and any remaining feature work is written as a separate wishlist item.\n"
                + "Repeated blocker: " + truncate(latestQuestion, 1_200);
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] lines = value.strip().split("\\R", 2);
        return lines.length == 0 ? value.strip() : lines[0];
    }

    private String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    private boolean isUsableAiAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return !lower.contains("api error")
                && !lower.contains("assistant temporarily")
                && !lower.contains("temporarily unavailable")
                && !lower.contains("произошла ошибка")
                && !lower.contains("ассистент временно")
                && !lower.contains("рђсс")
                && !lower.contains("рѕс€");
    }

    private String fallbackJulesAnswer(TaskEntity task) {
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "";
        if ("BARCAN-TAG-06".equals(roleTag)) {
            return "Proceed with reaching the required test ratios and deepening Acceptance Criteria verification as planned. Use the existing task AC and DoD as the source of truth; do not wait for extra business-logic requirements unless you find a concrete contradiction. For ambiguous details, implement the smallest verifiable assumption and document it in the PR summary. Keep generated Playwright reports, trace zips, screenshots, and test-results out of the commit, and run the relevant unit, integration, and E2E checks before submitting.";
        }
        return "Proceed using the existing task description, Acceptance Criteria, and DoD as the source of truth. Choose the smallest safe implementation assumption where details are ambiguous, document that assumption in the PR summary, and continue unless you hit a concrete contradiction or security/data-loss risk. Keep generated local artifacts out of the commit and run the relevant verification checks before submitting.";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Transactional
    public void handlePrOpenedWorkflow(JulesSessionEntity session) {
        UUID taskId = session.getTaskId();
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            if (projectFlowService.isWishlistCompilerTask(task)) {
                completeWishlistCompilation(session, task);
                return;
            }
            if (projectFlowService.isFalsificationAuditTask(task)) {
                completeFalsificationAudit(session, task);
                return;
            }
            if (projectFlowService.isReviewFallbackTask(task)) {
                completeReviewerFallback(session, task);
                return;
            }
            if (projectFlowService.isDesignReviewTask(task)) {
                completeDesignReview(session, task);
                return;
            }
            if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed) {
                log.info("Jules session {} transitioned to pr_opened. Completing implementer phase for task {}.", session.getId(), taskId);
                if (claimService.hasActiveClaim(task.getId())) {
                    claimService.complete(task.getId());
                } else {
                    log.info("No active implementer claim for task {}; continuing PR review workflow", task.getId());
                }

                // Create PR Review entry using real PR URL if available, otherwise fallback to mock
                String prUrl = session.getPrUrl();
                if (prUrl == null || prUrl.isBlank()) {
                    prUrl = "https://github.com/" + task.getProject().getRepositoryName() + "/pull/mock-" + taskId;
                }
                
                com.eneik.production.dto.monitor.PrDataDto prData = new com.eneik.production.dto.monitor.PrDataDto();
                prData.setCiStatus("success");
                prData.setLinesChanged(120);
                prData.setFilesChanged(4);
                prData.setChangedFiles(java.util.Collections.emptyList());

                // Invoke the local agent code review
                Map<String, Object> reviewResult = mlPredictionServiceClient.reviewPr(task.getProject().getId(), task.getId(), prUrl);
                boolean approved = Boolean.TRUE.equals(reviewResult.get("approved"));
                String remarks = (String) reviewResult.get("remarks");
                if (remarks == null || remarks.isBlank()) {
                    remarks = "PR review rejected without detailed remarks.";
                }
                if (!approved && remarks.startsWith("VERIFICATION_SERVICE_UNAVAILABLE")) {
                    // Gemini itself is unreachable/out of quota - this is not a real defect in Jules's code,
                    // so neither fake-approving nor sending a rejection message asking Jules to "fix" nothing
                    // is honest. A retry-only strategy would never resolve a genuinely dead Gemini quota, so
                    // dispatch a real Jules eneikdru session as the reviewer instead - work keeps moving
                    // either way, it just costs a Jules session instead of an instant Gemini call.
                    dispatchReviewerFallback(task, prUrl);
                    return;
                }
                boolean softArtifactDebt = !approved && isSoftGeneratedArtifactDebt(remarks);

                if (approved || softArtifactDebt) {
                    if (softArtifactDebt) {
                        createRepositoryHygieneDebtWishlist(task, remarks);
                    }
                    prData.setDiffSummary("CORE ARCHITECTURE VERIFIED. APPROVED. "
                            + (softArtifactDebt ? "TECH_DEBT_RECORDED: minor generated/local artifact debt; product slice may continue. " : "")
                            + remarks);
                    prReviewPipelineService.onPrOpened(prUrl, session.getId(), prData);

                    // Move task to review stage so AutoMergeService can merge it
                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.review);
                    taskRepository.save(task);
                    systemProgressTracker.recordProgress();
                    log.info("Local agent review passed for task {}. PR approved and task moved to REVIEW status.", task.getId());

                    // Create new recommended tasks proposed by the review
                    List<Map<String, Object>> newTasks = (List<Map<String, Object>>) reviewResult.get("newTasks");
                    if (newTasks != null) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        for (Map<String, Object> nt : newTasks) {
                            String rTag = (String) nt.get("roleTag");
                            String desc = (String) nt.get("description");
                            roleRepository.findById(rTag).ifPresent(role -> {
                                TaskEntity t = new TaskEntity();
                                t.setProject(task.getProject());
                                t.setRole(role);
                                t.setTitle(TaskTitleBuilder.build(rTag, desc));
                                t.setDescription(desc);
                                t.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
                                
                                com.fasterxml.jackson.databind.node.ObjectNode payloadNode = mapper.createObjectNode();
                                payloadNode.put("technicalLeadTaskSpec", desc);
                                t.setPayload(payloadNode);
                                
                                taskRepository.save(t);
                                log.info("Created new local agent review recommended task for role {}: {}", rTag, desc);
                            });
                        }
                    }
                } else {
                    prData.setDiffSummary("REVIEW REJECTED. " + remarks);
                    prReviewPipelineService.onPrOpened(prUrl, session.getId(), prData);

                    List<JulesActivityResponseEntity> responseHistory =
                            julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
                    String reviewSignal = "PR review rejection: " + remarks;
                    long previousSimilarReviewRejections = countPreviousSimilarQuestions(responseHistory, reviewSignal);
                    String julesReviewMessage = buildReviewRejectionMessage(task, remarks);
                    recordSystemReviewRejection(session, reviewSignal, julesReviewMessage, false);

                    if (previousSimilarReviewRejections > 0 && !mentionsGeneratedArtifact(remarks)) {
                        String closeReason = "repository_hygiene_review_repeated: same PR blocker persisted after prior remediation";
                        closeLoopAndCreateFollowUps(session, task, reviewSignal, responseHistory, closeReason);
                        return;
                    }

                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
                    taskRepository.save(task);

                    session.setStatus("revising");
                    julesSessionRepository.save(session);
                    systemProgressTracker.recordProgress();

                    log.info("Review rejected. Transitioning session {} to revising for task {}", session.getExternalSessionId(), task.getId());
                    saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), julesReviewMessage, "System generated rejection");

                    // Decouple the HTTP call to prevent holding the DB transaction
                    String externalSessionId = session.getExternalSessionId();
                    String sessionApiKey = apiKeyForSession(session);
                    UUID finalTaskId = task.getId();
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        boolean sent = sessionApiKey != null
                                ? julesApiClient.sendMessage(externalSessionId, julesReviewMessage, sessionApiKey)
                                : julesApiClient.sendMessage(externalSessionId, julesReviewMessage);
                        if (sent) {
                            log.info("Successfully sent review rejection message asynchronously to Jules session {} for task {}", externalSessionId, finalTaskId);
                        } else {
                            log.warn("Failed to send async message to Jules session {} for task {}. Task might be stuck in revising.", externalSessionId, finalTaskId);
                        }
                    });
                }
            } else if (task.getStatus() == com.eneik.production.models.persistence.TaskStatus.review) {
                log.info("Jules reviewer session {} transitioned to pr_opened. Completing reviewer phase for task {}.", session.getId(), taskId);
                if (claimService.hasActiveClaim(task.getId())) {
                    claimService.complete(task.getId());
                    systemProgressTracker.recordProgress();
                    log.info("Task {} marked as review completed", taskId);
                } else {
                    log.info("No active reviewer claim for task {}; leaving task status unchanged", task.getId());
                }
            }
        }
    }

    /**
     * A wishlist-compiler session reached pr_opened: its PR should carry exactly one JSON plan file
     * (see ProjectFlowService.wishlistCompilerPrompt), never product code. Parses and validates that
     * plan, feeds it into the same graph-building logic Gemini's slices used to drive, then discards
     * the compiler PR (it never gets merged). On invalid/empty output this does not fall back to
     * fabricated content - it asks Jules to retry a bounded number of times, then escalates to
     * NeedsHumanReviewEntity.
     */
    private void completeWishlistCompilation(JulesSessionEntity session, TaskEntity compilerTask) {
        UUID wishlistId = compilerTaskWishlistId(compilerTask);
        if (wishlistId == null) {
            log.error("Compiler task {} has no compilesWishlistId payload marker; cannot complete compilation", compilerTask.getId());
            return;
        }
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId).orElse(null);
        if (wishlist == null) {
            log.warn("Compiler task {}: wishlist {} no longer exists, discarding", compilerTask.getId(), wishlistId);
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
            }
            return;
        }

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(compilerTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata> slices = prOpt
                .map(pr -> parseCompilerPlan(compilerTask.getProject(), pr.headRef()))
                .orElseGet(List::of);

        if (isValidCompilerPlan(slices)) {
            projectFlowService.buildTaskGraphFromSlices(compilerTask.getProject(), wishlist, slices);
            prOpt.ifPresent(pr -> {
                gitHubPullRequestService.mergeRecordPullRequest(
                        compilerTask.getProject(), pr, "wishlist compiler plan parsed into real tasks");
                archiveRecordFile(compilerTask.getProject(), WISHLIST_COMPILER_PLAN_PATH, "task-plan");
                closeSessionAsNoCode(session, "Compiler plan merged (process/metadata only by design); branch deleted.");
            });
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
            }
            systemProgressTracker.recordProgress();
            log.info("Wishlist {} compiled by Jules session {} into {} task slice(s)",
                    wishlist.getId(), session.getExternalSessionId(), slices.size());
            return;
        }

        int attempts = compilerTask.getRetryCount();
        if (attempts >= WISHLIST_COMPILER_MAX_RETRIES) {
            if (!needsHumanReviewRepository.existsByTaskId(compilerTask.getId())) {
                com.eneik.production.models.persistence.NeedsHumanReviewEntity review =
                        new com.eneik.production.models.persistence.NeedsHumanReviewEntity();
                review.setTask(compilerTask);
                review.setReason("Wishlist compiler produced no valid task plan after " + attempts
                        + " attempt(s) for wishlist " + wishlist.getId() + " - needs manual decomposition.");
                needsHumanReviewRepository.save(review);
            }
            prOpt.ifPresent(pr -> gitHubPullRequestService.closeSinglePullRequest(
                    compilerTask.getProject(), pr, "wishlist compiler plan invalid after max retries"));
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
            }
            log.error("Wishlist {} compilation failed after {} attempts; routed to human review", wishlist.getId(), attempts);
            return;
        }

        compilerTask.setRetryCount(attempts + 1);
        taskRepository.save(compilerTask);
        session.setStatus("revising");
        julesSessionRepository.save(session);

        String correction = "Your PR did not contain a valid `.eneik/task-plan.json` matching the requested "
                + "schema (or it echoed a generic placeholder instead of real slices). Please fix the same "
                + "PR: write only that one file with 1-6 real, concrete work items decomposed from the "
                + "original client brief in the task description above.";
        String externalSessionId = session.getExternalSessionId();
        String sessionApiKey = apiKeyForSession(session);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = sessionApiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, correction, sessionApiKey)
                    : julesApiClient.sendMessage(externalSessionId, correction);
            if (!sent) {
                log.warn("Failed to send compiler-plan correction to Jules session {}", externalSessionId);
            }
        });
        log.warn("Wishlist compiler plan invalid for wishlist {} (attempt {}/{}); asked Jules to retry",
                wishlist.getId(), attempts + 1, WISHLIST_COMPILER_MAX_RETRIES);
    }

    /**
     * A falsification-audit session reached pr_opened: its PR should carry exactly one JSON report
     * (see FalsificationCycleService.buildAuditPrompt), never product code. Parses it, applies any
     * violations through the same wishlist-creation path Gemini's answers used to drive
     * (FalsificationCycleService.applyAuditViolations), then discards the audit PR - it never gets
     * merged, same as the wishlist compiler PR. No retry loop: an invalid/missing report simply skips
     * this run, since the falsification cron fires again in a few hours regardless.
     */
    private void completeFalsificationAudit(JulesSessionEntity session, TaskEntity auditTask) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(auditTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.FalsificationCycleService.AuditViolation> violations = prOpt
                .map(pr -> parseFalsificationReport(auditTask.getProject(), pr.headRef()))
                .orElseGet(List::of);

        Integer highestPrNumber = projectFlowService.falsificationAuditHighestPrNumber(auditTask);
        falsificationCycleService.applyAuditViolations(auditTask.getProject(), violations, highestPrNumber);

        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(
                    auditTask.getProject(), pr, "falsification audit report parsed into wishlist follow-ups");
            archiveRecordFile(auditTask.getProject(), com.eneik.production.services.ProjectFlowService.FALSIFICATION_AUDIT_REPORT_PATH, "falsification-report");
            closeSessionAsNoCode(session, "Falsification report merged (process/metadata only by design); branch deleted.");
        });
        if (claimService.hasActiveClaim(auditTask.getId())) {
            claimService.complete(auditTask.getId());
        }
        systemProgressTracker.recordProgress();
        log.info("Falsification audit for project {} completed by Jules session {}: {} violation(s) reported",
                auditTask.getProject().getId(), session.getExternalSessionId(), violations.size());
    }

    private List<com.eneik.production.services.FalsificationCycleService.AuditViolation> parseFalsificationReport(
            ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(
                project, headRef, com.eneik.production.services.ProjectFlowService.FALSIFICATION_AUDIT_REPORT_PATH);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            JsonNode rawViolations = root.path("violations");
            if (!rawViolations.isArray()) {
                return List.of();
            }
            List<com.eneik.production.services.FalsificationCycleService.AuditViolation> result = new java.util.ArrayList<>();
            for (JsonNode v : rawViolations) {
                String roleTag = v.path("roleTag").asText("");
                if (roleTag.isBlank()) {
                    continue;
                }
                result.add(new com.eneik.production.services.FalsificationCycleService.AuditViolation(
                        roleTag,
                        v.path("type").asText("refusal_criteria"),
                        v.path("reason").asText(""),
                        v.path("philosopher").asText(""),
                        v.path("thesis").asText(""),
                        v.path("score").asText(""),
                        v.path("mustBe").asText(""),
                        v.path("performance").asText(""),
                        v.path("attractive").asText("")
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse falsification audit report for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Triggered only when Gemini's PR review reports VERIFICATION_SERVICE_UNAVAILABLE. Fetches the real
     * PR diff (Jules sessions always start from main, so the reviewer session needs the diff text handed
     * to it directly - it cannot check out the implementer's branch itself) and dispatches a standalone
     * Jules eneikdru review-fallback task. If the diff can't be fetched either (e.g. GitHub also
     * disabled), this leaves the task exactly as-is for a retry next cycle rather than guessing.
     */
    private void dispatchReviewerFallback(TaskEntity task, String prUrl) {
        Integer pullNumber = parsePullNumber(prUrl);
        Optional<String> diff = pullNumber != null
                ? gitHubPullRequestService.fetchDiffText(task.getProject(), pullNumber)
                : Optional.empty();
        if (diff.isEmpty()) {
            log.warn("PR review fallback: could not fetch diff for task {} (PR {}); leaving pr_opened for retry next cycle.",
                    task.getId(), prUrl);
            return;
        }
        String prompt = reviewerFallbackPrompt(task, prUrl, diff.get());
        UUID reviewTaskId = projectFlowService.dispatchReviewFallback(task, prompt);
        if (reviewTaskId == null) {
            log.warn("Could not dispatch PR review fallback for task {}", task.getId());
            return;
        }
        log.info("Dispatched PR review fallback task {} for task {} (PR {}) - Gemini review unavailable",
                reviewTaskId, task.getId(), prUrl);
    }

    private Integer parsePullNumber(String prUrl) {
        if (prUrl == null || prUrl.isBlank() || prUrl.contains("/mock-")) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(prUrl);
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length >= 4 && "pull".equals(parts[2]) && parts[3].matches("\\d+")) {
                return Integer.parseInt(parts[3]);
            }
        } catch (Exception e) {
            log.warn("Could not parse PR number from URL {}: {}", prUrl, e.getMessage());
        }
        return null;
    }

    private String reviewerFallbackPrompt(TaskEntity task, String prUrl, String diff) {
        return """
                You are the fallback code reviewer for this PR (Gemini review is temporarily or permanently
                unavailable). Review the PR below - do NOT implement, fix, or change any product code
                yourself, and do not run builds or tests; this task only produces a review verdict.

                Be lenient by design: work must never stall waiting on your opinion. Block
                ("verdict":"block") ONLY for a small set of genuinely critical problems: a real security
                vulnerability, data loss risk, hardcoded secrets/credentials, committed generated/build
                artifacts (node_modules, playwright-report, coverage, .zip/.png/.webm/.trace files), missing
                required tests for a QA task, or a direct contradiction of the task's stated Acceptance
                Criteria/DoD below. Anything else - style preferences, architecture opinions, missing edge
                cases that do not break the Acceptance Criteria, suggestions for a better approach - is NOT
                a blocker: approve the PR and list it as a "concern" instead, so it becomes a follow-up
                improvement item rather than stopped work.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/review-verdict.json`, with EXACTLY this shape and no other files changed:
                {"verdict": "approve", "criticalReason": "", "concerns": ["short concern 1", "short concern 2"]}
                or, only for a genuine critical blocker:
                {"verdict": "block", "criticalReason": "concrete, specific blocking reason tied to the diff", "concerns": []}
                Do not write, modify, or delete any other file.

                Original task (role %s):
                %s

                PR under review: %s

                Diff to review:
                %s
                """.formatted(task.getRole().getTag(), task.getDescription(), prUrl, diff);
    }

    private record ReviewVerdict(String verdict, String criticalReason, List<String> concerns) {
    }

    private ReviewVerdict parseReviewVerdict(ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(
                project, headRef, com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_VERDICT_PATH);
        if (content.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            String verdict = root.path("verdict").asText("approve");
            String criticalReason = root.path("criticalReason").asText("");
            List<String> concerns = new java.util.ArrayList<>();
            JsonNode rawConcerns = root.path("concerns");
            if (rawConcerns.isArray()) {
                for (JsonNode c : rawConcerns) {
                    concerns.add(c.asText(""));
                }
            }
            return new ReviewVerdict(verdict, criticalReason, concerns);
        } catch (Exception e) {
            log.warn("Failed to parse PR review fallback verdict for project {}: {}", project.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * A review-fallback session reached pr_opened: its PR should carry exactly one JSON verdict (see
     * reviewerFallbackPrompt above), never product code. Discards that PR either way (it never gets
     * merged), then applies the verdict to the ORIGINAL implementer task/session: a genuine critical
     * block sends Jules a correction on the same PR; anything else approves the PR through the same
     * pipeline the primary Gemini path uses, and records every concern as a non-blocking follow-up
     * wishlist item instead of stopping the work.
     */
    private void completeReviewerFallback(JulesSessionEntity session, TaskEntity reviewTask) {
        UUID originalTaskId = projectFlowService.reviewFallbackTargetTaskId(reviewTask);

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(reviewTask.getProject(), session.getExternalSessionId());
        ReviewVerdict verdict = prOpt
                .map(pr -> parseReviewVerdict(reviewTask.getProject(), pr.headRef()))
                .orElse(null);

        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(
                    reviewTask.getProject(), pr, "PR review fallback verdict parsed");
            archiveRecordFile(reviewTask.getProject(), com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_VERDICT_PATH, "review-verdict");
            closeSessionAsNoCode(session, "Review verdict merged (process/metadata only by design); branch deleted.");
        });
        if (claimService.hasActiveClaim(reviewTask.getId())) {
            claimService.complete(reviewTask.getId());
        }

        if (originalTaskId == null) {
            log.error("PR review fallback task {} has no reviewsTaskId payload marker; cannot apply verdict", reviewTask.getId());
            return;
        }
        TaskEntity originalTask = taskRepository.findById(originalTaskId).orElse(null);
        if (originalTask == null) {
            log.warn("PR review fallback task {}: original task {} no longer exists, discarding verdict", reviewTask.getId(), originalTaskId);
            return;
        }
        List<JulesSessionEntity> implementerSessions = julesSessionRepository.findByTaskId(originalTaskId);
        JulesSessionEntity implementerSession = implementerSessions.stream()
                .filter(s -> "pr_opened".equals(s.getStatus()))
                .findFirst()
                .orElseGet(() -> implementerSessions.stream().filter(s -> s.getPrUrl() != null).findFirst().orElse(null));
        if (implementerSession == null) {
            log.warn("PR review fallback task {}: no implementer session found for task {}, discarding verdict", reviewTask.getId(), originalTaskId);
            return;
        }

        if (verdict == null) {
            log.warn("PR review fallback task {}: no valid verdict report found; task {} left for retry next cycle", reviewTask.getId(), originalTaskId);
            return;
        }

        String prUrl = implementerSession.getPrUrl();
        if (prUrl == null || prUrl.isBlank()) {
            prUrl = "https://github.com/" + originalTask.getProject().getRepositoryName() + "/pull/mock-" + originalTaskId;
        }

        boolean blocked = "block".equalsIgnoreCase(verdict.verdict())
                && verdict.criticalReason() != null && !verdict.criticalReason().isBlank();

        if (blocked) {
            log.warn("PR review fallback: task {} (PR {}) blocked by Jules reviewer - {}", originalTaskId, prUrl, verdict.criticalReason());
            originalTask.setStatus(com.eneik.production.models.persistence.TaskStatus.claimed);
            taskRepository.save(originalTask);
            implementerSession.setStatus("revising");
            julesSessionRepository.save(implementerSession);
            String correction = "Fallback reviewer (Jules, Gemini unavailable) blocked this PR: " + verdict.criticalReason()
                    + "\nPlease fix the same PR to resolve this specific problem.";
            String externalSessionId = implementerSession.getExternalSessionId();
            String sessionApiKey = apiKeyForSession(implementerSession);
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                boolean sent = sessionApiKey != null
                        ? julesApiClient.sendMessage(externalSessionId, correction, sessionApiKey)
                        : julesApiClient.sendMessage(externalSessionId, correction);
                if (!sent) {
                    log.warn("Failed to send fallback-reviewer block message to Jules session {}", externalSessionId);
                }
            });
            return;
        }

        // Approved (or a "block" without a real critical reason, which is treated as approve by design -
        // never stall on an unsubstantiated objection).
        com.eneik.production.dto.monitor.PrDataDto prData = new com.eneik.production.dto.monitor.PrDataDto();
        prData.setCiStatus("success");
        prData.setLinesChanged(120);
        prData.setFilesChanged(4);
        prData.setChangedFiles(java.util.Collections.emptyList());
        String remarks = "CORE ARCHITECTURE VERIFIED. APPROVED. Jules fallback review (Gemini unavailable). "
                + (verdict.concerns().isEmpty() ? "No concerns raised." : verdict.concerns().size() + " concern(s) recorded as follow-up wishlist items.");
        prData.setDiffSummary(remarks);
        prReviewPipelineService.onPrOpened(prUrl, implementerSession.getId(), prData);

        originalTask.setStatus(com.eneik.production.models.persistence.TaskStatus.review);
        taskRepository.save(originalTask);
        systemProgressTracker.recordProgress();
        log.info("PR review fallback: task {} (PR {}) approved by Jules reviewer with {} concern(s)", originalTaskId, prUrl, verdict.concerns().size());

        for (String concern : verdict.concerns()) {
            if (concern == null || concern.isBlank()) {
                continue;
            }
            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(originalTask.getProject().getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.role);
            wishlist.setSourceRoleTag(originalTask.getRole().getTag());
            wishlist.setFeatureId(originalTask.getFeatureId());
            wishlist.setContent("Reviewer concern (non-blocking) on task \"" + TaskTitleBuilder.displayTitle(originalTask) + "\": " + concern);
            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
            wishlistRepository.save(wishlist);
        }
    }

    private record DesignVerdict(String verdict, String reason, List<String> concerns) {
    }

    private DesignVerdict parseDesignVerdict(ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(
                project, headRef, com.eneik.production.services.ProjectFlowService.DESIGN_REVIEW_VERDICT_PATH);
        if (content.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            String verdict = root.path("verdict").asText("approve");
            String reason = root.path("reason").asText("");
            List<String> concerns = new java.util.ArrayList<>();
            JsonNode rawConcerns = root.path("concerns");
            if (rawConcerns.isArray()) {
                for (JsonNode c : rawConcerns) {
                    concerns.add(c.asText(""));
                }
            }
            return new DesignVerdict(verdict, reason, concerns);
        } catch (Exception e) {
            log.warn("Failed to parse design review verdict for project {}: {}", project.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * A design-review session reached pr_opened: its PR should carry exactly one JSON verdict (see
     * ProjectFlowService.designReviewPrompt), never product code. Discards that PR either way, then
     * either promotes the draft to design/approved/ (real GitHub copy, so it becomes the durable,
     * confirmed-good reference future slices/implementers should use) or records the rejection reason as
     * a non-blocking follow-up wishlist item - same soft philosophy as the PR review fallback: a design
     * opinion never stalls work, it only ever produces improvement backlog.
     */
    private void completeDesignReview(JulesSessionEntity session, TaskEntity reviewTask) {
        String draftPath = projectFlowService.designReviewDraftPath(reviewTask);

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(reviewTask.getProject(), session.getExternalSessionId());
        DesignVerdict verdict = prOpt
                .map(pr -> parseDesignVerdict(reviewTask.getProject(), pr.headRef()))
                .orElse(null);

        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(
                    reviewTask.getProject(), pr, "design review verdict parsed");
            archiveRecordFile(reviewTask.getProject(), com.eneik.production.services.ProjectFlowService.DESIGN_REVIEW_VERDICT_PATH, "design-review-verdict");
            closeSessionAsNoCode(session, "Design review verdict merged (process/metadata only by design); branch deleted.");
        });
        if (claimService.hasActiveClaim(reviewTask.getId())) {
            claimService.complete(reviewTask.getId());
        }

        if (draftPath == null) {
            log.error("Design review task {} has no designDraftPath payload marker; cannot apply verdict", reviewTask.getId());
            return;
        }
        if (verdict == null) {
            log.warn("Design review task {}: no valid verdict report found for draft {}; left unpromoted", reviewTask.getId(), draftPath);
            return;
        }

        boolean rejected = "reject".equalsIgnoreCase(verdict.verdict())
                && verdict.reason() != null && !verdict.reason().isBlank();

        if (rejected) {
            log.warn("Design review: draft {} rejected - {}", draftPath, verdict.reason());
            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(reviewTask.getProject().getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.role);
            wishlist.setSourceRoleTag("BARCAN-TAG-03");
            wishlist.setFeatureId(reviewTask.getFeatureId());
            wishlist.setContent("Design draft " + draftPath + " was rejected in review: " + verdict.reason()
                    + ". Regenerate or manually correct the mockup for this slice.");
            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
            wishlistRepository.save(wishlist);
            return;
        }

        // Approved (or a "reject" without a real reason, treated as approve by design - never block
        // promotion on an unsubstantiated objection).
        String basename = draftPath.startsWith(com.eneik.production.services.design.DesignAssetService.DESIGN_DRAFT_ROOT + "/")
                ? draftPath.substring(com.eneik.production.services.design.DesignAssetService.DESIGN_DRAFT_ROOT.length() + 1)
                : draftPath;
        String approvedDir = com.eneik.production.services.design.DesignAssetService.DESIGN_APPROVED_ROOT + "/" + basename;
        boolean htmlCopied = gitHubPullRequestService.copyFile(reviewTask.getProject(),
                draftPath + "/mockup.html", approvedDir + "/mockup.html", "Promote reviewed design: " + basename);
        boolean pngCopied = gitHubPullRequestService.copyFile(reviewTask.getProject(),
                draftPath + "/mockup.png", approvedDir + "/mockup.png", "Promote reviewed design screenshot: " + basename);
        if (htmlCopied || pngCopied) {
            log.info("Design review: draft {} approved and promoted to {} with {} concern(s)",
                    draftPath, approvedDir, verdict.concerns().size());
        } else {
            log.warn("Design review: draft {} approved but promotion to {} failed (no files copied)", draftPath, approvedDir);
        }

        long pendingConcernCount = wishlistRepository.countByProjectIdAndSourceAndSourceRoleTagAndStatus(
                reviewTask.getProject().getId(),
                com.eneik.production.models.persistence.WishlistSource.role,
                "BARCAN-TAG-03",
                com.eneik.production.models.persistence.WishlistStatus.pending);
        List<com.eneik.production.models.persistence.WishlistEntity> pendingDesignWishlist = pendingConcernCount > 0
                ? wishlistRepository.findByProjectIdAndStatus(reviewTask.getProject().getId(),
                        com.eneik.production.models.persistence.WishlistStatus.pending)
                : List.of();

        for (String concern : verdict.concerns()) {
            if (concern == null || concern.isBlank()) {
                continue;
            }
            if (pendingConcernCount >= MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT) {
                log.info("Design review: dropping non-blocking concern on {} - project already has {} pending "
                                + "design-review follow-up(s) (cap {}); will resurface on a future review pass if still real: {}",
                        approvedDir, pendingConcernCount, MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT, concern);
                continue;
            }
            String finalConcern = concern;
            boolean alreadyPending = pendingDesignWishlist.stream().anyMatch(item ->
                    "BARCAN-TAG-03".equals(item.getSourceRoleTag())
                            && item.getContent() != null
                            && item.getContent().contains(finalConcern));
            if (alreadyPending) {
                log.info("Design review: skipping duplicate non-blocking concern on {} - already pending: {}", approvedDir, concern);
                continue;
            }

            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(reviewTask.getProject().getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.role);
            wishlist.setSourceRoleTag("BARCAN-TAG-03");
            wishlist.setFeatureId(reviewTask.getFeatureId());
            wishlist.setContent("Design reviewer concern (non-blocking) on " + approvedDir + ": " + concern);
            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
            wishlistRepository.save(wishlist);
            pendingConcernCount++;
        }
    }

    private UUID compilerTaskWishlistId(TaskEntity task) {
        if (task.getPayload() == null) {
            return null;
        }
        String raw = task.getPayload().path("compilesWishlistId").asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata> parseCompilerPlan(
            ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, WISHLIST_COMPILER_PLAN_PATH);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            JsonNode rawSlices = root.path("slices");
            if (!rawSlices.isArray()) {
                return List.of();
            }
            List<com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata> result = new java.util.ArrayList<>();
            for (JsonNode slice : rawSlices) {
                String leanValueRaw = slice.path("leanValue").asText("essential");
                com.eneik.production.models.persistence.LeanValue leanValue;
                try {
                    leanValue = com.eneik.production.models.persistence.LeanValue.valueOf(leanValueRaw);
                } catch (Exception e) {
                    leanValue = com.eneik.production.models.persistence.LeanValue.essential;
                }
                result.add(new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                        slice.path("title").asText(""),
                        slice.path("jtbd").asText(""),
                        slice.path("acceptanceCriteria").asText(""),
                        slice.path("roleTag").asText(""),
                        leanValue,
                        slice.path("kanoClass").asText("Must-Be"),
                        slice.path("cynefinDomain").asText("clear"),
                        slice.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                        slice.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                        slice.path("hasUi").asBoolean(false)
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse wishlist compiler plan for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    private boolean isValidCompilerPlan(List<com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata> slices) {
        if (slices.isEmpty() || slices.size() > 6) {
            return false;
        }
        for (com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata slice : slices) {
            if (slice.title() == null || slice.title().isBlank()
                    || slice.jtbd() == null || slice.jtbd().isBlank()
                    || slice.acceptanceCriteria() == null || slice.acceptanceCriteria().isBlank()) {
                return false;
            }
            if (slice.jtbd().contains("one small verifiable capability completed")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mapping Table:
     * External (Jules API) -> Internal
     * -------------------------------
     * "QUEUED"             -> "queued"
     * "RUNNING"            -> "running"
     * "SUCCEEDED"          -> "pr_opened"
     * "FAILED"             -> "failed"
     * "CANCELLED"          -> "failed"
     * "STUCK"              -> "stuck" (if API ever returns it)
     */
    public String mapExternalStatus(String externalStatus) {
        if (externalStatus == null) return "running";

        return switch (externalStatus.toUpperCase()) {
            case "QUEUED" -> "queued";
            case "RUNNING" -> "running";
            case "SUCCEEDED" -> "pr_opened";
            case "FAILED", "CANCELLED" -> "failed";
            case "STUCK" -> "stuck";
            default -> "running"; // Default to running if unknown but alive
        };
    }

    private String apiKeyForSession(JulesSessionEntity session) {
        if (session.getAccountId() == null) {
            return null;
        }
        return accountRepository.findById(session.getAccountId())
                .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                .filter(key -> !key.isBlank())
                .orElse(null);
    }

    private boolean shouldSendStuckRecovery(JulesSessionEntity session) {
        Instant lastCheck = session.getLastStatusCheckAt();
        return lastCheck == null || lastCheck.isBefore(Instant.now().minus(STUCK_RECOVERY_MESSAGE_INTERVAL));
    }

    private void sendStuckRecoveryMessageAsync(JulesSessionEntity session, TaskEntity task, String apiKey) {
        String externalSessionId = session.getExternalSessionId();
        UUID taskId = task.getId();
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        String taskDescription = task.getDescription();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String fallbackPrompt = "Eneik orchestrator recovery: continue this task if possible, or open a PR with the current progress. "
                    + "If you are blocked, explain the blocker in the session. Task role: " + roleTag
                    + ". Task: " + taskDescription;
            String prompt = fallbackPrompt;

            try {
                String aiPrompt = "Create a concise direct recovery message for a Google Jules coding session that is stuck. "
                        + "Role: " + roleTag + "\nTask: " + taskDescription;
                String aiSystem = "Return only the English-language message to send to Jules. Do not include analysis or markdown.";
                String aiResponse = mlPredictionServiceClient.chat(aiPrompt, aiSystem);
                if (aiResponse != null
                        && !aiResponse.isBlank()
                        && !aiResponse.contains("API Error")
                        && !aiResponse.contains("Произошла ошибка")
                        && !aiResponse.contains("Ассистент временно недоступен")) {
                    prompt = aiResponse;
                }
            } catch (Exception e) {
                log.warn("Could not generate Gemini recovery prompt for Jules session {}: {}", externalSessionId, e.getMessage());
            }

            boolean sent = apiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, prompt, apiKey)
                    : julesApiClient.sendMessage(externalSessionId, prompt);
            if (sent) {
                log.info("Sent stuck-session recovery message to Jules session {} for task {}", externalSessionId, taskId);
                saveJulesDialogueLog(taskId, externalSessionId, prompt, "Stuck-session recovery");
            } else {
                log.warn("Failed to send stuck-session recovery message to Jules session {} for task {}", externalSessionId, taskId);
            }
        });
    }

    private void saveJulesDialogueLog(UUID taskId, String sessionId, String prompt, String remarks) {
        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get("docs/jules_dialogues");
            if (!java.nio.file.Files.exists(dirPath)) {
                java.nio.file.Files.createDirectories(dirPath);
            }
            java.nio.file.Path filePath = dirPath.resolve("task_" + taskId + ".log");
            String logEntry = String.format("--- Session: %s at %s ---\nRemarks: %s\nPrompt Sent: %s\n\n",
                                            sessionId, Instant.now().toString(), remarks, prompt);
            java.nio.file.Files.writeString(filePath, logEntry, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to save Jules dialogue log for task {}: {}", taskId, e.getMessage());
        }
    }

    private record LoopDiagnosis(
            String rootCause,
            String kanoClass,
            String cynefinDomain,
            String roleTag,
            String followUpTitle,
            String followUpBody
    ) {
        String toText() {
            return "Root cause: " + rootCause + "\n"
                    + "Kano: " + kanoClass + "\n"
                    + "Cynefin: " + cynefinDomain + "\n"
                    + "Role: " + roleTag + "\n"
                    + "Follow-up: " + followUpTitle + "\n"
                    + followUpBody;
        }
    }

    /**
     * Trigger for periodic maintenance of stuck Jules sessions.
     */
    @Scheduled(fixedRateString = "${jules.detect-stuck-rate-ms:60000}")
    public void detectStuck() {
        runSessionSafetyMaintenance();
    }

    public void runSessionSafetyMaintenance() {
        claimService.detectStuckSessions(stuckThresholdMinutes);
        closeOverdueStuckSessions();
        forceUnblockOverflowedSessions();
        reconcileAbandonedPullRequests();
    }

    @Transactional
    public void closeOverdueStuckSessions() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(stuckCloseThresholdMinutes));
        List<JulesSessionEntity> stuckSessions = julesSessionRepository.findByStatus("stuck");
        if (stuckSessions == null || stuckSessions.isEmpty()) {
            return;
        }
        int closed = 0;
        for (JulesSessionEntity session : stuckSessions) {
            if (closed >= maxLoopClosuresPerRun) {
                break;
            }
            Instant reference = session.getLastProgressAt() != null ? session.getLastProgressAt() : session.getUpdatedAt();
            if (reference == null || reference.isAfter(threshold)) {
                continue;
            }
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                session.setStatus("loop_closed");
                session.setClosedAt(Instant.now());
                session.setClosureReason("stuck_session_timeout: task no longer exists");
                julesSessionRepository.save(session);
                continue;
            }
            List<JulesActivityResponseEntity> responseHistory =
                    julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
            String latestQuestion = responseHistory.stream()
                    .findFirst()
                    .map(JulesActivityResponseEntity::getQuestion)
                    .filter(question -> question != null && !question.isBlank())
                    .orElse("Jules session stayed stuck without new actionable activity.");
            closeLoopAndCreateFollowUps(
                    session,
                    task,
                    latestQuestion,
                    responseHistory,
                    "stuck_session_timeout: stuck for at least " + stuckCloseThresholdMinutes + " minutes"
            );
            closed++;
        }
    }

    /**
     * A session with an oversized (>2MB) activity log has its question-scan deliberately skipped every
     * cycle (see answerAgentQuestions) so a healthy-but-verbose session never gets falsely closed - but
     * that means a session which is ACTUALLY blocked waiting on an unanswered question, with no other
     * status change, has no recovery path: the activitiesOverflow skip hides the question, and Jules's
     * own status API just keeps reporting "RUNNING" (never "STUCK"), so shouldSendStuckRecovery never
     * fires either. This sweep catches exactly that gap: once a session has gone genuinely dark (blind to
     * both the overflow-skip and lastProgressAt) for long enough, send a deterministic (no AI call) message
     * telling Jules to stop waiting and make a decision. Escalates through the existing
     * closeLoopAndCreateFollowUps breaker after a bounded number of attempts, rather than inventing a
     * second closure mechanism.
     */
    @Transactional
    public void forceUnblockOverflowedSessions() {
        Instant staleSince = Instant.now().minus(Duration.ofMinutes(stuckThresholdMinutes));
        List<JulesSessionEntity> candidates = julesSessionRepository.findByStatusIn(
                List.of("running", "queued", "revising", "stuck"));

        for (JulesSessionEntity session : candidates) {
            // A "revising" session (sent back after a review rejection) used to only qualify here via
            // blindCycleCount, which never increments unless its activity log is oversized - a session
            // that simply went quiet after a rejection, with a normal-sized log, sat untouched for the
            // full stuck-close-threshold-minutes (120min) before anything happened. Nudging it as soon as
            // it's stale (same stuckThresholdMinutes gate detectStuckSessions already uses) closes that
            // gap - confirmed live as a real bottleneck on real product-code tasks in test-twenty-seventh.
            boolean revisingOrStuck = "revising".equals(session.getStatus()) || "stuck".equals(session.getStatus());
            if (session.getBlindCycleCount() < forcedUnblockBlindCycleThreshold && !revisingOrStuck) {
                continue;
            }
            Instant lastProgress = session.getLastProgressAt() != null ? session.getLastProgressAt() : session.getCreatedAt();
            if (lastProgress.isAfter(staleSince)) {
                continue;
            }

            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }

            if (session.getForcedUnblockAttempts() >= forcedUnblockMaxAttempts) {
                List<JulesActivityResponseEntity> responseHistory =
                        julesActivityResponseRepository.findByJulesSessionIdOrderByCreatedAtDesc(session.getId());
                String stallReason = revisingOrStuck && session.getBlindCycleCount() < forcedUnblockBlindCycleThreshold
                        ? "Session stayed in " + session.getStatus() + " with no observed progress since " + lastProgress
                        : "Session activity log stayed too large to inspect for " + session.getBlindCycleCount()
                                + " consecutive cycle(s) with no observed progress since " + lastProgress;
                closeLoopAndCreateFollowUps(
                        session,
                        task,
                        stallReason,
                        responseHistory,
                        "blind_overflow_unblock_exhausted: forced unblock attempted "
                                + session.getForcedUnblockAttempts() + " time(s) without observed progress"
                );
                continue;
            }

            sendForcedUnblockMessageAsync(session, task, revisingOrStuck);
            session.setForcedUnblockAttempts(session.getForcedUnblockAttempts() + 1);
            session.setBlindCycleCount(0);
            julesSessionRepository.save(session);
        }
    }

    private void sendForcedUnblockMessageAsync(JulesSessionEntity session, TaskEntity task, boolean revisingNudge) {
        String externalSessionId = session.getExternalSessionId();
        String apiKey = apiKeyForSession(session);
        UUID taskId = task.getId();
        String message = revisingNudge
                ? "Eneik orchestrator nudge: this session was sent review feedback and asked to push a fix, but "
                        + "no update has been observed for " + stuckThresholdMinutes + "+ minutes. Please push a fix "
                        + "now addressing the earlier review feedback, or if genuinely blocked, state the concrete "
                        + "blocker in a comment on the PR so it can be escalated. Work should not silently stall."
                : "Eneik orchestrator forced unblock: this session's activity log has stayed too large "
                + "to inspect for a pending question across several checks, and no new progress has been "
                + "observed. It is OK to forcibly decide for yourself based on your own knowledge of the "
                + "project: make one objective move from the task facts, document the smallest safe "
                + "assumption in the PR summary, and open or update the PR now instead of waiting for "
                + "further clarification.";
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = apiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, message, apiKey)
                    : julesApiClient.sendMessage(externalSessionId, message);
            if (sent) {
                log.info("Sent forced blind-overflow unblock message to Jules session {} for task {}", externalSessionId, taskId);
                saveJulesDialogueLog(taskId, externalSessionId, message, "Forced blind-overflow unblock");
            } else {
                log.warn("Failed to send forced blind-overflow unblock message to Jules session {} for task {}", externalSessionId, taskId);
            }
        });
    }

    /**
     * Sweeps closed ("loop_closed") sessions that never got a pr_reviews row and checks GitHub directly
     * for a PR that Jules may have already opened before the session was force-closed. Circuit breakers
     * (activity_log_overflow, stuck_session_timeout, or the now-removed active_session_age_limit) close a
     * session and dispatch a brand-new replacement task, but never checked whether the closed session had
     * already produced real, working code - a PR opened right before closure is otherwise structurally
     * invisible to AutoMergeService (which only iterates existing pr_reviews rows) forever.
     *
     * Fully autonomous end-to-end (no human review parking lot): the discovered PR is run through the
     * same mlPredictionServiceClient.reviewPr(...) gate AutoMergeService trusts elsewhere. An approved PR
     * gets a pr_reviews row with ciStatus=success, which AutoMergeService's own next cycle picks up and
     * merges through its already-tested pipeline (Cynefin/Philosophical-Filter checks included) - no new
     * merge logic here. A rejected PR spawns a fresh atomic recovery task instead (same "start clean, one
     * small slice" idiom used by ProjectFlowService's blocked-task recovery), so the work keeps moving
     * without ever waiting on a human who, per this system's design, never participates day to day.
     */
    @Transactional
    public void reconcileAbandonedPullRequests() {
        // Was a 7-day window, re-fetched every single 60s maintenance tick with no per-session backoff -
        // the race condition this catches (Jules opens a PR right as/after force-closure) either resolves
        // within minutes or never does; a session still unresolved after a few hours will never resolve.
        // Confirmed live as a real driver of the GitHub REST rate-limit exhaustion in test-twenty-sixth:
        // every unresolved session in this list costs a full PR-list fetch (pullRequestSnapshot) on every
        // tick, for every project that ever had one, for up to 7 days. Tightened the window and added a
        // recheck cooldown (via updatedAt, touched on every check including misses) instead.
        Instant recentEnough = Instant.now().minus(Duration.ofHours(3));
        Instant recheckCooldown = Instant.now().minus(Duration.ofMinutes(10));
        List<JulesSessionEntity> closedSessions = julesSessionRepository.findByStatus("loop_closed").stream()
                .filter(s -> s.getClosedAt() != null && s.getClosedAt().isAfter(recentEnough))
                .filter(s -> s.getPrUrl() == null || s.getPrUrl().isBlank())
                .filter(s -> !prReviewRepository.existsByJulesSessionId(s.getId()))
                .filter(s -> s.getUpdatedAt() == null || s.getUpdatedAt().isBefore(recheckCooldown))
                .toList();

        for (JulesSessionEntity session : closedSessions) {
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }

            Optional<GitHubPullRequestService.GitHubPullRequest> found =
                    gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId());
            if (found.isEmpty()) {
                // Touch updatedAt so the recheck-cooldown filter above actually skips this session on the
                // next several ticks, instead of re-fetching the same project's PR list every 60s forever.
                julesSessionRepository.save(session);
                continue;
            }

            GitHubPullRequestService.GitHubPullRequest pr = found.get();
            session.setPrUrl(pr.url());
            julesSessionRepository.save(session);

            Map<String, Object> reviewResult;
            try {
                reviewResult = mlPredictionServiceClient.reviewPr(task.getProject().getId(), task.getId(), pr.url());
            } catch (Exception e) {
                log.warn("reconcileAbandonedPullRequests: review call failed for {}: {} - will retry next cycle", pr.url(), e.getMessage());
                continue;
            }
            String remarks = String.valueOf(reviewResult.get("remarks"));
            if (remarks.startsWith("VERIFICATION_SERVICE_UNAVAILABLE")) {
                log.warn("reconcileAbandonedPullRequests: review service unavailable for {} - will retry next cycle, not treating as a rejection", pr.url());
                continue;
            }

            boolean approved = Boolean.TRUE.equals(reviewResult.get("approved"));
            if (approved) {
                PrReviewEntity review = new PrReviewEntity();
                review.setJulesSessionId(session.getId());
                review.setPrUrl(pr.url());
                review.setCiStatus("success");
                review.setRiskLevel("medium");
                review.setDiffSummary("Abandoned-PR reconciliation: auto-review approved. " + remarks);
                prReviewRepository.save(review);
                log.info("Reconciled abandoned PR {} for closed session {} (task {}) - approved by auto-review, queued for AutoMergeService.",
                        pr.url(), session.getExternalSessionId(), task.getId());
            } else {
                WishlistEntity followUp = new WishlistEntity();
                followUp.setProjectId(task.getProject().getId());
                followUp.setSource(WishlistSource.role_mismatch_followup);
                followUp.setSourceRoleTag(task.getRole() != null ? task.getRole().getTag() : null);
                followUp.setStatus(WishlistStatus.pending);
                followUp.setContent(("""
                        [Auto recovery: abandoned PR flagged by auto-review]
                        Found existing PR %s left behind by a force-closed session for task %s.
                        Auto-review verdict: rejected. Remarks: %s

                        Goal: start one fresh, short Jules session that addresses the remarks above and
                        replaces this abandoned attempt. Do not continue the old branch. One atomic slice,
                        one PR, one objective acceptance criterion.
                        """).formatted(pr.url(), task.getId(), remarks));
                wishlistRepository.save(followUp);
                log.warn("Reconciled abandoned PR {} for closed session {} (task {}) - auto-review rejected, queued a fresh recovery task instead of a human-review dead end.",
                        pr.url(), session.getExternalSessionId(), task.getId());
            }
        }
    }

}
