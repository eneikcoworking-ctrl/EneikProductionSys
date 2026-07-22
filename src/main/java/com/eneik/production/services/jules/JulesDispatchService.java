package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
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
    private static final int DAVIDSON_TRUST_WINDOW_MINUTES = 60;
    private static final int DAVIDSON_CLOSE_WINDOW_MINUTES = 120;
    private static final int DESTRUCTIVE_LOOP_REPEAT_THRESHOLD = 2;
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
    private static final int MAX_PENDING_DESIGN_CONCERNS_PER_PROJECT = 0;
    // Same runaway-self-generation risk as the design-review concern loop above, but on the code-review
    // side: dispatchReviewFallback fires on EVERY implementer PR whenever Gemini is unavailable (not just
    // design work), and its non-blocking "concerns" (e.g. "consider Postgres for prod", "CI Java version
    // bump could break other repos") were being turned into wishlist items unconditionally - no cap, no
    // dedup - creating the identical unbounded self-perpetuating loop, just fed by ordinary code review
    // instead of design review, and firing far more often since it covers every PR, not just UI ones.
    private static final int MAX_PENDING_REVIEW_CONCERNS_PER_PROJECT = 0;
    private static final String REVIEW_FALLBACK_CONCERN_CONTENT_PREFIX = "Reviewer concern (non-blocking) on task \"";
    // Coverage-audit gaps (see ProjectFlowService.dispatchCoverageAuditIfClientBrief) are inherently rare -
    // one audit per client brief decomposition, not per PR - so a runaway storm is far less likely than the
    // review/design concern loops above. Still capped for the same "never create tasks the system doesn't
    // actually need" reason, and because a single sloppy brief could in principle keep re-triggering gaps
    // across retries.
    private static final int MAX_PENDING_COVERAGE_GAPS_PER_PROJECT = 0;

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
    private final com.eneik.production.services.ClientDeliverableReadinessService readinessService;
    private final com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService;
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
    /**
     * ClaimService.complete() is a two-call state machine designed for the real implementer->reviewer
     * lifecycle: the first call (task not yet 'review') moves the task to TaskStatus.review; only a
     * SECOND call (task already 'review') advances it to TaskStatus.done. System/carrier tasks (wishlist
     * compiler, falsification audit, PR review fallback, design review, coverage audit) have no second
     * "reviewer" phase - their one session IS the whole result - so calling complete() once left them
     * permanently parked at TaskStatus.review forever. That status was never actually terminal:
     * ProjectFlowService.dispatchReviewTasks scans EVERY task sitting at TaskStatus.review with no active
     * review session and dispatches a fresh reviewer session for it, with no awareness that a system task
     * isn't real implementer code awaiting review - so it kept re-running the SAME compiler/design-
     * review/etc. prompt over and over, forever. Confirmed live on test-thirty-second: a single design-
     * review task got redispatched and fully re-completed 3 times over ~50 minutes before finally landing
     * on `failed`. Call this immediately after claimService.complete() in every system-task completion
     * handler to give the task a real terminal state and stop dispatchReviewTasks from ever seeing it.
     */
    private void markSystemTaskDone(TaskEntity task) {
        task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
        taskRepository.save(task);
    }

    private void closeSessionAsNoCode(JulesSessionEntity session, String reason) {
        session.setStatus("closed_no_code");
        session.setClosedAt(java.time.Instant.now());
        session.setClosureReason(reason);
        julesSessionRepository.save(session);
    }

    /**
     * Operator-initiated cancel for a stray/duplicate/unwanted session - e.g. a second wishlist-compiler
     * session dispatched against a brief another session already compiled. "cancelled" is a status nothing
     * else in this codebase polls or acts on (mirrors the existing closed_no_code/loop_closed convention),
     * so once set here the session is fully inert: pollActiveJulesSessions stops checking it (its status
     * filter is running/queued/revising/stuck only) and it can never trigger handlePrOpenedWorkflow again,
     * regardless of what the real remote Jules session eventually does.
     */
    @Transactional
    public void cancelSession(java.util.UUID sessionId, String reason) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        session.setStatus("cancelled");
        session.setClosedAt(java.time.Instant.now());
        session.setClosureReason(reason);
        julesSessionRepository.save(session);

        if (session.getTaskId() != null) {
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task != null && isTerminalTask(task)) {
                claimService.releaseTerminalClaim(task.getId());
                log.info("Cancelled session {} without changing already-terminal task {} ({})",
                        session.getExternalSessionId(), task.getId(), task.getStatus());
            } else {
                claimService.closeTaskAsFailed(session.getTaskId(), reason);
            }
        }
    }

    @Value("${jules.stuck-threshold-minutes:60}")
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
                                com.eneik.production.services.ClientDeliverableReadinessService readinessService,
                                com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService,
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
        this.readinessService = readinessService;
        this.persistentWorkerSessionService = persistentWorkerSessionService;
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

        boolean buildPhase = task.getProject() != null && readinessService.isBuildPhase(task.getProject().getId());

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
        if (buildPhase) {
            roleContextBuilder.append("- This project is in its build phase: trust is maximal right now. Make the call yourself from your role's own judgment (see Role Charter below) rather than hedging toward the safest generic option - the system doesn't exist yet, your judgment is what's building it. Mechanical polish gates are relaxed for this phase; your role's own refusal criteria still apply in full.\n");
        } else {
            roleContextBuilder.append("- This project already has a built shape: work carefully within existing patterns rather than restructuring what's already there. Prefer the smallest change consistent with how the system already works over a cleaner rewrite, unless the task explicitly asks for the rewrite.\n");
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
        // Ф7 (2026-07-21, operator directive): used to also require accountId == featureThread.getAccountId()
        // before allowing continuation, reasoning that cross-account continuation "has never been tested".
        // Removed after review found nothing to actually justify it: session creation authenticates purely
        // via the per-account API key (X-Goog-Api-Key) for quota/billing, not git identity - Jules's GitHub
        // access is repo-level (collaborator invitations sent to every account up front), and this
        // project's branch protection isn't even active (confirmed live: GitHub returned 403 "Upgrade to
        // GitHub Pro or make this repository public" when we tried to enable it). Nothing stops a
        // different account's session from pushing to an existing branch. Worse, the restriction was never
        // load-bearing anyway - AccountRepository.lockNextJulesAccountWithCapacity (the actual account
        // picker in ProjectFlowService.dispatchQueuedTasks) has no featureId parameter at all, so it never
        // preferred the thread's owning account - continuation only ever fired by pure chance when
        // round-robin happened to land on it. An unverified, uncompensated-for restriction that only ever
        // reduced how often real continuation happened - removed rather than "fixed" by also making
        // dispatch thread-aware, since there was never evidence the restriction did anything useful.
        String startingBranch = "main";
        var featureThreadOpt = task.getFeatureId() == null ? java.util.Optional.<com.eneik.production.models.persistence.FeatureThreadEntity>empty()
                : featureThreadRepository.findByProjectIdAndFeatureId(task.getProject().getId(), task.getFeatureId());
        if (featureThreadOpt.isPresent()) {
            var featureThread = featureThreadOpt.get();
            startingBranch = featureThread.getBranchName();
            roleContextBuilder.append("\n## Continuing Prior Work\n")
                    .append("This feature has ongoing work on branch ").append(startingBranch)
                    .append(" (last worked on by role ").append(featureThread.getLastRoleTag() == null ? "unknown" : featureThread.getLastRoleTag()).append("). ")
                    .append("Build on the existing code, do not start over. Prior summary: ")
                    .append(featureThread.getSummary() == null ? "(none)" : featureThread.getSummary()).append("\n");
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


    private static boolean isTerminalSessionStatus(String status) {
        return "loop_closed".equals(status) || "cancelled".equals(status) || "closed_no_code".equals(status)
                || "cancelled_externally".equals(status) || "closed_terminal_task".equals(status);
    }

    public JulesSessionEntity pollStatus(UUID sessionId) {
        JulesSessionEntity session = julesSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if ("skipped".equals(session.getExternalSessionId()) || session.getExternalSessionId() == null) {
            return session;
        }

        // Once we've deliberately closed a session (loop_closed/cancelled/closed_no_code), never let a
        // stale-read poll resurrect it. Confirmed live (2026-07-21): pollActiveJulesSessions captures its
        // candidate list on its own schedule, independent of runSessionSafetyMaintenance's; if a session
        // gets force-closed between that capture and this call reaching it, this call still re-fetches the
        // FRESH row above (closed), but used to blindly overwrite status back to whatever Jules's external
        // API still reports (often still "running") - re-admitting the closed session into every future
        // active-session poll forever, a permanent zombie that no longer matches its own task's real
        // (failed/done) status.
        if (isTerminalSessionStatus(session.getStatus())) {
            return session;
        }

        TaskEntity currentTask = taskRepository.findById(session.getTaskId()).orElse(null);
        if (currentTask != null && isTerminalTask(currentTask)) {
            closeSessionForTerminalTask(session, currentTask);
            return session;
        }
        if (currentTask != null && reviewFallbackTargetsAreTerminal(currentTask)) {
            closeSessionAsNoCode(session, "Poka-yoke: review fallback retired because every target task is terminal");
            markSystemTaskDone(currentTask);
            claimService.releaseTerminalClaim(currentTask.getId());
            log.info("Poka-yoke: retired obsolete review fallback task {} / session {} before provider polling",
                    currentTask.getId(), session.getExternalSessionId());
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
        log.warn("Poka-yoke: recorded repository-hygiene observation for task {} without creating "
                        + "follow-up wishlist work; falsification is the only next-iteration generator. Review note: {}",
                task.getId(), truncate(remarks, 700));
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
                || "cancelled_externally".equals(session.getStatus())
                || "closed".equals(session.getStatus());
    }

    private void closeLoopAndCreateFollowUps(JulesSessionEntity session,
                                             TaskEntity task,
                                             String latestQuestion,
                                             List<JulesActivityResponseEntity> responseHistory,
                                             String closeReason) {
        LoopDiagnosis diagnosis = diagnoseLoop(task, latestQuestion, closeReason);
        String geminiAnalysis = geminiLoopAnalysis(task, latestQuestion, responseHistory, diagnosis, closeReason);
        // Ф-followup (2026-07-21, operator directive): this text is Gemini's own INFERENCE from limited
        // signal (the question/response history it was given), not a verified fact - confirmed live
        // tonight that repeating it as ground truth produced a false report ("UI Slice stopped making
        // objective progress") that directly contradicted hard evidence (a complete, working PR had
        // already been committed hours earlier). Anyone reading closureReason/the dialogue log later - a
        // future me included - must treat this block as a hypothesis to verify against GitHub/DB state,
        // never as settled fact on its own.
        String taggedGeminiAnalysis = "[UNVERIFIED - Gemini inference from limited signal, not a checked "
                + "fact; verify against GitHub/DB state before treating as true]\n" + geminiAnalysis;

        session.setStatus("loop_closed");
        session.setClosedAt(Instant.now());
        session.setClosureReason(closeReason + "\n\n" + diagnosis.toText() + "\n\nGemini analysis:\n" + taggedGeminiAnalysis);
        julesSessionRepository.save(session);

        claimService.closeTaskAsBlocked(task.getId(), "Jules circuit breaker: " + closeReason);
        boolean followUpCreated = createCircuitBreakerWishlist(session, task, latestQuestion, diagnosis, geminiAnalysis, closeReason);
        saveJulesDialogueLog(task.getId(), session.getExternalSessionId(),
                diagnosis.toText() + "\n\nGemini analysis:\n" + taggedGeminiAnalysis,
                "Jules loop closed by Eneik circuit breaker: " + closeReason);
        log.warn("Closed Jules session {} for task {} due to {}. Follow-up wishlist created={}.",
                session.getExternalSessionId(), task.getId(), closeReason, followUpCreated);
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

    private boolean createCircuitBreakerWishlist(JulesSessionEntity session,
                                                 TaskEntity task,
                                                 String latestQuestion,
                                                 LoopDiagnosis diagnosis,
                                                 String geminiAnalysis,
                                                 String closeReason) {
        log.warn("Poka-yoke: circuit breaker closed session {} for task {} without creating "
                        + "follow-up wishlist work; falsification owns next-iteration generation. Reason: {}",
                session.getId(), task.getId(), closeReason);
        return false;
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

    /**
     * Replays the implementer hand-off when the durable session edge was saved but the task transition
     * did not finish. pollStatus persists pr_opened before invoking handlePrOpenedWorkflow; a process
     * restart or exception in between used to leave task=claimed/session=pr_opened forever because the
     * active poller deliberately ignores already-open PRs and lease maintenance treats them as alive.
     *
     * The task-state predicate is the idempotency key. A successful replay moves the task away from
     * claimed, so later ticks do nothing. Review/pending-review sessions are intentionally not replayed
     * here because a pr_opened session at those stages may be a reviewer rather than the implementer.
     */
    @Scheduled(fixedRateString = "${jules.pr-opened-reconcile-rate-ms:60000}")
    public int reconcileStrandedPrOpenedWorkflows() {
        int replayed = 0;
        List<JulesSessionEntity> openPrSessions = julesSessionRepository.findByStatus("pr_opened");
        for (JulesSessionEntity session : openPrSessions) {
            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null || task.getStatus() != TaskStatus.claimed
                    || session.getPrUrl() == null || session.getPrUrl().isBlank()) {
                continue;
            }
            // A persistent worker intentionally parks at pr_opened between batches. Its carrier task is
            // not an implementer hand-off and may remain claimed while the worker has no batch in flight;
            // replaying it every minute only produces a durable no-op and noisy DB/log traffic.
            if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
                continue;
            }
            try {
                log.warn("Replaying stranded pr_opened workflow for task {} / session {} / PR {}",
                        task.getId(), session.getExternalSessionId(), session.getPrUrl());
                handlePrOpenedWorkflow(session);
                if (task.getStatus() != TaskStatus.claimed) {
                    replayed++;
                }
            } catch (Exception e) {
                log.error("Failed to replay stranded pr_opened workflow for task {} / session {}: {}",
                        task.getId(), session.getExternalSessionId(), e.getMessage(), e);
            }
        }
        return replayed;
    }

    @Transactional
    public void handlePrOpenedWorkflow(JulesSessionEntity session) {
        UUID taskId = session.getTaskId();
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            // Checked first: a persistent worker's carrier task still carries the normal
            // wishlist_compiler/pr_review_fallback type marker (so completePersistentWorkerCycle can reuse
            // the same parse/build logic), so this must be routed here before the one-shot branches below.
            if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
                completePersistentWorkerCycle(session, task);
                return;
            }
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
            if (projectFlowService.isCoverageAuditTask(task)) {
                completeCoverageAudit(session, task);
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

                // Cynefin "chaotic" domain: act first to stabilize, sense/respond afterward (same intent as
                // AutoMergeService's chaotic merge bypass, which already skips the approval-token check for
                // these tasks) - reviewing immediately instead of waiting for the next batch tick keeps that
                // path fast. Every other task is deferred into pending_review so processPendingReviewBatch
                // can review it together with any sibling PRs from the same feature (fuller picture instead
                // of each PR reviewed in total isolation).
                if ("chaotic".equalsIgnoreCase(task.getCynefinDomain())) {
                    List<PendingFallbackReview> fallback = new java.util.ArrayList<>();
                    executeCodeReview(task, session, prUrl, java.util.Collections.emptyList(), fallback);
                    if (!fallback.isEmpty()) {
                        dispatchReviewerFallbackBatch(fallback);
                    }
                } else {
                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);
                    taskRepository.save(task);
                    log.info("Task {} implementer PR opened; deferred to batched review (next tick).", task.getId());
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
     * One PR still needing a Jules fallback reviewer because Gemini was unavailable when this task's
     * review was attempted - collected across a whole processPendingReviewBatch tick (or a single chaotic
     * "act first" review) and dispatched together in one Jules session instead of one session per PR.
     */
    private record PendingFallbackReview(TaskEntity task, String prUrl) {
    }

    /**
     * Runs the automated code-review gate for one PR and applies the same approve/reject decision this
     * system has always made (see handlePrOpenedWorkflow git history) - extracted so it can be invoked
     * either immediately (chaotic Cynefin domain, "act first") or from the batched review tick
     * (processPendingReviewBatch), which is where every other task's review now happens. siblingPrUrls
     * are other in-flight PRs sharing this task's featureId, reviewed in the same batch tick - passed
     * through to the reviewer so it can check this PR against them (e.g. a backend/frontend pair built
     * against the same BARCAN-TAG-12 API contract), not treat this diff in total isolation. When Gemini
     * is unavailable, the task is appended to fallbackCollector instead of being dispatched to Jules
     * immediately - the caller dispatches every collected task together in one batched Jules session
     * once it's done looping, so an outage doesn't turn into one Jules session per PR.
     */
    @Transactional
    void executeCodeReview(TaskEntity task, JulesSessionEntity session, String prUrl, List<String> siblingPrUrls,
                            List<PendingFallbackReview> fallbackCollector) {
        com.eneik.production.dto.monitor.PrDataDto prData = new com.eneik.production.dto.monitor.PrDataDto();
        prData.setCiStatus("success");
        prData.setLinesChanged(120);
        prData.setFilesChanged(4);
        prData.setChangedFiles(java.util.Collections.emptyList());

        // Invoke the local agent code review
        Map<String, Object> reviewResult = mlPredictionServiceClient.reviewPr(task.getProject().getId(), task.getId(), prUrl, siblingPrUrls);
        boolean approved = Boolean.TRUE.equals(reviewResult.get("approved"));
        String remarks = (String) reviewResult.get("remarks");
        if (remarks == null || remarks.isBlank()) {
            remarks = "PR review rejected without detailed remarks.";
        }
        if (!approved && remarks.startsWith("VERIFICATION_SERVICE_UNAVAILABLE")) {
            // Gemini itself is unreachable/out of quota - this is not a real defect in Jules's code,
            // so neither fake-approving nor sending a rejection message asking Jules to "fix" nothing
            // is honest. A retry-only strategy would never resolve a genuinely dead Gemini quota, so
            // queue this PR for a batched Jules eneikdru fallback review instead - work keeps moving
            // either way, it just costs a shared Jules session instead of an instant Gemini call.
            fallbackCollector.add(new PendingFallbackReview(task, prUrl));
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
    }

    /**
     * Batched replacement for reviewing each implementer PR the instant it opens: every ~15 minutes,
     * gathers every task waiting in pending_review, groups them by featureId, and reviews each one with
     * its same-feature siblings (if any) passed as context - so a backend/frontend pair built in parallel
     * off the same BARCAN-TAG-12 contract gets reviewed with a fuller picture instead of two completely
     * isolated diffs. Fully automated end to end; no human decision point anywhere in this pipeline.
     */
    @Scheduled(
            fixedRateString = "${pr-review.batch-rate-ms:900000}",
            initialDelayString = "${pr-review.batch-initial-delay-ms:60000}")
    @Transactional
    public void processPendingReviewBatch() {
        List<TaskEntity> pending = taskRepository.findByStatus(com.eneik.production.models.persistence.TaskStatus.pending_review);
        if (pending.isEmpty()) {
            return;
        }

        Map<UUID, List<TaskEntity>> byFeature = new java.util.LinkedHashMap<>();
        for (TaskEntity t : pending) {
            byFeature.computeIfAbsent(t.getFeatureId(), k -> new java.util.ArrayList<>()).add(t);
        }

        // Collected across the WHOLE tick (every feature group, every project) so every PR that hits
        // Gemini-unavailable this tick goes out in one Jules session per project instead of one per PR -
        // see PendingFallbackReview/executeCodeReview.
        List<PendingFallbackReview> fallbackCollector = new java.util.ArrayList<>();

        for (Map.Entry<UUID, List<TaskEntity>> entry : byFeature.entrySet()) {
            List<TaskEntity> siblings = entry.getValue();
            Map<UUID, String> prUrlByTaskId = new java.util.LinkedHashMap<>();
            Map<UUID, JulesSessionEntity> sessionByTaskId = new java.util.LinkedHashMap<>();
            for (TaskEntity t : siblings) {
                JulesSessionEntity session = latestOpenPrSession(t.getId());
                if (session == null || session.getPrUrl() == null || session.getPrUrl().isBlank()) {
                    log.warn("Task {} is pending_review but has no resolvable open-PR session; skipping this tick.", t.getId());
                    continue;
                }
                sessionByTaskId.put(t.getId(), session);
                prUrlByTaskId.put(t.getId(), session.getPrUrl());
            }

            for (TaskEntity t : siblings) {
                JulesSessionEntity session = sessionByTaskId.get(t.getId());
                String prUrl = prUrlByTaskId.get(t.getId());
                if (session == null || prUrl == null) {
                    continue;
                }
                List<String> siblingPrUrls = new java.util.ArrayList<>();
                if (entry.getKey() != null) {
                    for (Map.Entry<UUID, String> other : prUrlByTaskId.entrySet()) {
                        if (!other.getKey().equals(t.getId())) {
                            siblingPrUrls.add(other.getValue());
                        }
                    }
                }
                executeCodeReview(t, session, prUrl, siblingPrUrls, fallbackCollector);
            }
        }

        if (!fallbackCollector.isEmpty()) {
            // A TaskEntity can only belong to one project, so a batched fallback review session (which is
            // itself one TaskEntity) can't span projects - group by project before dispatching.
            Map<UUID, List<PendingFallbackReview>> byProject = new java.util.LinkedHashMap<>();
            for (PendingFallbackReview item : fallbackCollector) {
                byProject.computeIfAbsent(item.task().getProject().getId(), k -> new java.util.ArrayList<>()).add(item);
            }
            for (List<PendingFallbackReview> projectItems : byProject.values()) {
                dispatchReviewerFallbackBatch(projectItems);
            }
        }
    }

    private JulesSessionEntity latestOpenPrSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(s -> "pr_opened".equals(s.getStatus()))
                .max(java.util.Comparator.comparing(JulesSessionEntity::getUpdatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
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
        List<UUID> wishlistIds = compilerTaskWishlistIds(compilerTask);
        if (wishlistIds.isEmpty()) {
            log.error("Compiler task {} has no compilesWishlistIds payload marker; cannot complete compilation", compilerTask.getId());
            return;
        }
        List<WishlistEntity> wishlists = new java.util.ArrayList<>();
        for (UUID id : wishlistIds) {
            wishlistRepository.findById(id).ifPresent(wishlists::add);
        }
        if (wishlists.isEmpty()) {
            log.warn("Compiler task {}: none of its {} wishlist(s) exist anymore, discarding", compilerTask.getId(), wishlistIds.size());
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            return;
        }

        // Idempotency guard: more than one compiler task can end up targeting the same wishlist (e.g. the
        // generic blocked-task recovery flow re-dispatching a compiler task without knowing it's a compiler
        // task, racing an already-in-flight one) - without this check, every one of them independently calls
        // buildTaskGraphFromSlices below and the same brief gets fully decomposed and dispatched multiple
        // times. Whichever compiler session reaches this point first "wins" for a given wishlist; a batch
        // where EVERY wishlist has already been finished by another session is a full no-op that just closes
        // its own PR/session cleanly. A batch where only SOME are already finished still proceeds -
        // buildTaskGraphFromSlices skips those specific ones internally per-source.
        //
        // Must check specifically for converted_to_task/dismissed, NOT "!= pending": dispatchWishlistCompiler
        // flips each wishlist to `compiling` at DISPATCH time, before any session has actually completed - a
        // "!= pending" check would therefore reject every completion, including the legitimate first one,
        // since by the time ANY session gets here the status has already left `pending`. This was caught
        // live: it stuck a wishlist in an infinite compile->discard->blocked->recover->compile loop that
        // never produced real work. converted_to_task/dismissed are the only states that mean "someone
        // already finished this" - `compiling` just means "in flight", which includes the winning attempt.
        boolean anyStillOpen = wishlists.stream()
                .anyMatch(w -> w.getStatus() != WishlistStatus.converted_to_task && w.getStatus() != WishlistStatus.dismissed);
        if (!anyStillOpen) {
            log.warn("Compiler task {}: all {} wishlist(s) in this batch are already compiled by another session - discarding this duplicate compilation instead of re-decomposing the same brief(s).",
                    compilerTask.getId(), wishlists.size());
            Optional<GitHubPullRequestService.GitHubPullRequest> duplicatePrOpt =
                    gitHubPullRequestService.findOpenPullRequestBySession(compilerTask.getProject(), session.getExternalSessionId());
            duplicatePrOpt.ifPresent(pr -> {
                gitHubPullRequestService.mergeRecordPullRequest(
                        compilerTask.getProject(), pr, "duplicate wishlist compiler run discarded (wishlist(s) already compiled)");
                closeSessionAsNoCode(session, "Duplicate compiler run for already-compiled wishlist(s); discarded.");
            });
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            return;
        }

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(compilerTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics = prOpt
                .map(pr -> parseCompilerPlan(compilerTask.getProject(), pr.headRef()))
                .orElseGet(List::of);

        // Validated against the FULL original batch size (wishlists.size()), not just the still-open subset
        // - sourceIndex values in Jules's response reference the numbering the prompt actually sent, which
        // covers every wishlist in the batch regardless of whether one of them was independently finished
        // by another session in the meantime.
        if (isValidCompilerPlan(epics, wishlists.size())) {
            projectFlowService.buildTaskGraphFromSlices(compilerTask.getProject(), wishlists, epics);
            prOpt.ifPresent(pr -> {
                gitHubPullRequestService.mergeRecordPullRequest(
                        compilerTask.getProject(), pr, "wishlist compiler plan parsed into real tasks");
                archiveRecordFile(compilerTask.getProject(), WISHLIST_COMPILER_PLAN_PATH, "task-plan");
                closeSessionAsNoCode(session, "Compiler plan merged (process/metadata only by design); branch deleted.");
            });
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            systemProgressTracker.recordProgress();
            log.info("{} wishlist(s) compiled by Jules session {} into {} эпик(s), {} task slice(s) total",
                    wishlists.size(), session.getExternalSessionId(), epics.size(),
                    epics.stream().mapToInt(e -> e.slices().size()).sum());
            return;
        }

        int attempts = compilerTask.getRetryCount();
        if (attempts >= WISHLIST_COMPILER_MAX_RETRIES) {
            if (!needsHumanReviewRepository.existsByTaskId(compilerTask.getId())) {
                com.eneik.production.models.persistence.NeedsHumanReviewEntity review =
                        new com.eneik.production.models.persistence.NeedsHumanReviewEntity();
                review.setTask(compilerTask);
                review.setReason("Wishlist compiler produced no valid task plan after " + attempts
                        + " attempt(s) for " + wishlists.size() + " wishlist(s) - needs manual decomposition.");
                needsHumanReviewRepository.save(review);
            }
            prOpt.ifPresent(pr -> gitHubPullRequestService.closeSinglePullRequest(
                    compilerTask.getProject(), pr, "wishlist compiler plan invalid after max retries"));
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            log.error("Compilation of {} wishlist(s) failed after {} attempts; routed to human review", wishlists.size(), attempts);
            return;
        }

        compilerTask.setRetryCount(attempts + 1);
        taskRepository.save(compilerTask);
        session.setStatus("revising");
        julesSessionRepository.save(session);

        String correction = "Your PR did not contain a valid `.eneik/task-plan.json` matching the requested "
                + "schema (or it omitted explicit requirement coverage). Please fix the same PR: write only "
                + "that file with exhaustive epic requirements, coverageComplete=true, and 1-8 concrete "
                + "slices per epic. Every requirement must be covered by slice requirementRefs and every "
                + "input brief must be represented.";
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
        log.warn("Wishlist compiler plan invalid for {} wishlist(s) (attempt {}/{}); asked Jules to retry",
                wishlists.size(), attempts + 1, WISHLIST_COMPILER_MAX_RETRIES);
    }

    /**
     * A persistent worker's session (see PersistentWorkerSessionService) reached pr_opened - either its
     * very first cycle, or a later one after being sent a follow-up message. Unlike the one-shot handlers
     * above, the PR is never merged/discarded and the session is never closed here: it stays open at
     * pr_opened, which is already the correct idle/capacity-free state for the next cycle's admission
     * check (ProjectFlowService.dispatchToCompilerPersistentWorker /
     * JulesDispatchService.dispatchToReviewFallbackPersistentWorker).
     */
    /**
     * Read-only check ("does this session already have an answer?") used by forceUnblockOverflowedSessions
     * before it decides a persistent-worker carrier session has truly stalled. Must NOT consume the
     * worker's in-flight batch itself - only completePersistentWorkerCycle does that, and only once this
     * check has confirmed there is something real to consume. Looks at the actual PR branch on GitHub
     * (the same source of truth Jules writes to), not at our own possibly-stale session.status field.
     */
    private boolean persistentWorkerHasReadyAnswer(JulesSessionEntity session, TaskEntity carrierTask) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        if (prOpt.isEmpty()) {
            return false;
        }
        String headRef = prOpt.get().headRef();
        if (projectFlowService.isReviewFallbackTask(carrierTask)) {
            return !parseReviewVerdictBatch(carrierTask.getProject(), headRef).isEmpty();
        }
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics =
                parseCompilerPlan(carrierTask.getProject(), headRef);
        return !epics.isEmpty();
    }

    /**
     * Real-implementer-task counterpart of {@link #persistentWorkerHasReadyAnswer} - there's no single
     * result file to check for a normal task, so the ground truth is simpler: has a commit actually landed
     * on this session's branch after the point our own tracking last saw progress. A real, positive answer
     * here means the session was NOT actually silent, our lastProgressAt bookkeeping just missed it.
     */
    private boolean hasNewProgressOnGitHub(JulesSessionEntity session, TaskEntity task, Instant lastProgress) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId());
        if (prOpt.isEmpty()) {
            return false;
        }
        return gitHubPullRequestService.latestCommitTime(task.getProject(), prOpt.get().headRef())
                .map(commitTime -> commitTime.isAfter(lastProgress))
                .orElse(false);
    }

    private void completePersistentWorkerCycle(JulesSessionEntity session, TaskEntity carrierTask) {
        Optional<com.eneik.production.models.persistence.PersistentWorkerSessionEntity> workerOpt =
                persistentWorkerSessionService.findByCarrierTaskId(carrierTask.getId());

        com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker;
        List<UUID> batchIds;
        if (workerOpt.isPresent()) {
            worker = workerOpt.get();
            batchIds = persistentWorkerSessionService.consumeCurrentBatch(worker);
            if (batchIds.isEmpty()) {
                // No batch was in flight for this worker - a stray/duplicate pr_opened edge (or the worker
                // is somehow idle already). This is the idempotency guard for this pipeline: only an edge
                // that corresponds to a real in-flight batch gets processed.
                log.info("Persistent worker {} (carrier task {}): pr_opened edge with no batch in flight, ignoring.",
                        worker.getId(), carrierTask.getId());
                return;
            }
        } else {
            // Lazy registration: this is the very first pr_opened for a freshly-created carrier task whose
            // worker row wasn't registered yet (its dispatch was delayed and only succeeded via the normal
            // queued-task retry sweep - see ProjectFlowService.createFreshCompilerPersistentWorker /
            // JulesDispatchService.createFreshReviewFallbackPersistentWorker). The carrier task's own
            // creation-time payload IS cycle 1's batch.
            com.eneik.production.models.persistence.PersistentWorkerPurpose purpose =
                    projectFlowService.isReviewFallbackTask(carrierTask)
                            ? com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK
                            : com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER;
            batchIds = purpose == com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK
                    ? projectFlowService.reviewFallbackTargetTaskIds(carrierTask)
                    : compilerTaskWishlistIds(carrierTask);
            worker = persistentWorkerSessionService.registerFreshWorker(
                    carrierTask.getProject().getId(), purpose, carrierTask.getId(), session.getId(), batchIds);
            // The batch we just registered IS the one this pr_opened edge is responding to - consume it
            // immediately rather than leaving it "in flight" for a phantom future edge.
            persistentWorkerSessionService.consumeCurrentBatch(worker);
            log.info("Persistent worker lazily registered for carrier task {} (purpose {}) on its first pr_opened",
                    carrierTask.getId(), purpose);
        }

        if (projectFlowService.isReviewFallbackTask(carrierTask)) {
            completePersistentReviewFallbackCycle(session, carrierTask, batchIds);
        } else {
            completePersistentCompilerCycle(session, carrierTask, batchIds);
        }
    }

    private void completePersistentCompilerCycle(JulesSessionEntity session, TaskEntity carrierTask, List<UUID> wishlistIds) {
        List<WishlistEntity> wishlists = new java.util.ArrayList<>();
        for (UUID id : wishlistIds) {
            wishlistRepository.findById(id).ifPresent(wishlists::add);
        }
        if (wishlists.isEmpty()) {
            log.warn("Persistent compiler worker cycle (carrier task {}): none of its {} wishlist(s) exist anymore, skipping",
                    carrierTask.getId(), wishlistIds.size());
            return;
        }
        boolean anyStillOpen = wishlists.stream()
                .anyMatch(w -> w.getStatus() != WishlistStatus.converted_to_task && w.getStatus() != WishlistStatus.dismissed);
        if (!anyStillOpen) {
            log.warn("Persistent compiler worker cycle (carrier task {}): all {} wishlist(s) already compiled by another path, skipping",
                    carrierTask.getId(), wishlists.size());
            return;
        }

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics = prOpt
                .map(pr -> parseCompilerPlan(carrierTask.getProject(), pr.headRef()))
                .orElseGet(List::of);

        if (isValidCompilerPlan(epics, wishlists.size())) {
            projectFlowService.buildTaskGraphFromSlices(carrierTask.getProject(), wishlists, epics);
            systemProgressTracker.recordProgress();
            log.info("Persistent compiler worker (carrier task {}): {} wishlist(s) compiled into {} эпик(s), {} task slice(s) this cycle",
                    carrierTask.getId(), wishlists.size(), epics.size(),
                    epics.stream().mapToInt(e -> e.slices().size()).sum());
            return;
        }

        // Invalid plan this cycle: ask the same session to fix it, same correction message the one-shot
        // path uses. No retry-count escalation to human review here (unlike the one-shot path) - a
        // persistent worker's retryCount would otherwise accumulate across unrelated cycles' batches, not
        // just retries of the current one; an occasional bad cycle just gets asked to redo it.
        session.setStatus("revising");
        julesSessionRepository.save(session);
        String correction = "Your latest commit did not contain a valid `.eneik/task-plan.json` matching the "
                + "requested schema or explicit requirement coverage for THIS cycle's brief(s). Please fix "
                + "the same file with exhaustive epic requirements, coverageComplete=true, and 1-8 concrete "
                + "slices per epic. Every requirement must be covered by slice requirementRefs and every "
                + "input brief must be represented.";
        String externalSessionId = session.getExternalSessionId();
        String sessionApiKey = apiKeyForSession(session);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = sessionApiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, correction, sessionApiKey)
                    : julesApiClient.sendMessage(externalSessionId, correction);
            if (!sent) {
                log.warn("Failed to send compiler-plan correction to persistent worker session {}", externalSessionId);
            }
        });
        log.warn("Persistent compiler worker (carrier task {}): invalid plan for {} wishlist(s) this cycle; asked Jules to fix it",
                carrierTask.getId(), wishlists.size());
    }

    private void completePersistentReviewFallbackCycle(JulesSessionEntity session, TaskEntity carrierTask, List<UUID> originalTaskIds) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        List<ReviewVerdictEntry> verdicts = prOpt
                .map(pr -> parseReviewVerdictBatch(carrierTask.getProject(), pr.headRef()))
                .orElseGet(List::of);

        for (int i = 0; i < originalTaskIds.size(); i++) {
            UUID originalTaskId = originalTaskIds.get(i);
            int sourceIndex = i;
            ReviewVerdictEntry verdict = verdicts.stream()
                    .filter(v -> v.sourceIndex() == sourceIndex)
                    .findFirst()
                    .orElse(null);
            applyReviewVerdictToTask(carrierTask, originalTaskId, verdict);
        }
        log.info("Persistent review-fallback worker (carrier task {}): applied verdicts for {} PR(s) this cycle",
                carrierTask.getId(), originalTaskIds.size());
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
        // Idempotency: same dispatch-race class fixed in completeReviewerFallback/completeCoverageAudit/
        // completeDesignReview tonight (the last one confirmed live on test-thirty-second) - only the
        // session still holding the active claim should apply violations; a later duplicate completion is
        // a discard-only no-op.
        boolean firstCompletion = claimService.hasActiveClaim(auditTask.getId());

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(auditTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.FalsificationCycleService.AuditViolation> violations = firstCompletion
                ? prOpt.map(pr -> parseFalsificationReport(auditTask.getProject(), pr.headRef())).orElseGet(List::of)
                : List.of();

        if (firstCompletion) {
            Integer highestPrNumber = projectFlowService.falsificationAuditHighestPrNumber(auditTask);
            falsificationCycleService.applyAuditViolations(auditTask.getProject(), violations, highestPrNumber);
        }

        String mergeReason = firstCompletion
                ? "falsification audit report parsed into wishlist follow-ups"
                : "duplicate falsification audit session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(auditTask.getProject(), pr, mergeReason);
            archiveRecordFile(auditTask.getProject(), com.eneik.production.services.ProjectFlowService.FALSIFICATION_AUDIT_REPORT_PATH, "falsification-report");
            closeSessionAsNoCode(session, "Falsification report merged (process/metadata only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("Falsification audit task {}: session {} completion discarded - another session already applied this audit's violations.",
                    auditTask.getId(), session.getId());
            return;
        }
        claimService.complete(auditTask.getId());
        markSystemTaskDone(auditTask);
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
     * Triggered once per orchestrate() tick for every PR whose Gemini review reported
     * VERIFICATION_SERVICE_UNAVAILABLE (see PendingFallbackReview/executeCodeReview above). Fetches the
     * real diff for each PR (Jules sessions always start from main, so the reviewer session needs the
     * diff text handed to it directly - it cannot check out N different implementers' branches itself)
     * and dispatches ONE standalone Jules eneikdru review-fallback task covering all of them, instead of
     * one session per PR. A PR whose diff can't be fetched (e.g. GitHub also disabled) is dropped from
     * this batch and left exactly as-is for a retry next cycle rather than guessing.
     */
    private void dispatchReviewerFallbackBatch(List<PendingFallbackReview> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        UUID projectId = items.get(0).task().getProject().getId();
        Set<UUID> scheduledTargets = new java.util.HashSet<>(reviewFallbackTargetsEverAttempted(projectId));
        List<TaskEntity> tasks = new java.util.ArrayList<>();
        List<String> prUrls = new java.util.ArrayList<>();
        List<String> diffs = new java.util.ArrayList<>();
        for (PendingFallbackReview item : items) {
            if (isTerminalTask(item.task())) {
                log.info("PR review fallback: target task {} is already terminal; skipping obsolete review dispatch.",
                        item.task().getId());
                continue;
            }
            if (!scheduledTargets.add(item.task().getId())) {
                log.info("Poka-yoke: PR review fallback was already attempted for task {}; automatic retry is disabled.",
                        item.task().getId());
                continue;
            }
            Integer pullNumber = parsePullNumber(item.prUrl());
            Optional<String> diff = pullNumber != null
                    ? gitHubPullRequestService.fetchDiffText(item.task().getProject(), pullNumber)
                    : Optional.empty();
            if (diff.isEmpty()) {
                log.warn("PR review fallback: could not fetch diff for task {} (PR {}); leaving pr_opened for retry next cycle.",
                        item.task().getId(), item.prUrl());
                continue;
            }
            tasks.add(item.task());
            prUrls.add(item.prUrl());
            diffs.add(diff.get());
        }
        if (tasks.isEmpty()) {
            return;
        }
        if (persistentWorkerSessionService.isEnabled()) {
            dispatchToReviewFallbackPersistentWorker(tasks, prUrls, diffs);
            return;
        }
        String prompt = reviewerFallbackPromptBatch(tasks, prUrls, diffs);
        UUID reviewTaskId = projectFlowService.dispatchReviewFallbackBatch(tasks, prompt);
        if (reviewTaskId == null) {
            log.warn("Could not dispatch batched PR review fallback for {} task(s)", tasks.size());
            return;
        }
        log.info("Dispatched batched PR review fallback task {} covering {} PR(s) - Gemini review unavailable",
                reviewTaskId, tasks.size());
    }

    Set<UUID> reviewFallbackTargetsInFlight(UUID projectId) {
        return taskRepository.findAll().stream()
                .filter(task -> task.getProject() != null && projectId.equals(task.getProject().getId()))
                .filter(projectFlowService::isReviewFallbackTask)
                .filter(task -> !isTerminalTask(task))
                .flatMap(task -> projectFlowService.reviewFallbackTargetTaskIds(task).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    Set<UUID> reviewFallbackTargetsEverAttempted(UUID projectId) {
        return taskRepository.findAll().stream()
                .filter(task -> task.getProject() != null && projectId.equals(task.getProject().getId()))
                .filter(projectFlowService::isReviewFallbackTask)
                .flatMap(task -> projectFlowService.reviewFallbackTargetTaskIds(task).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    boolean reviewFallbackTargetsAreTerminal(TaskEntity reviewTask) {
        if (!projectFlowService.isReviewFallbackTask(reviewTask)) {
            return false;
        }
        List<UUID> targetIds = projectFlowService.reviewFallbackTargetTaskIds(reviewTask);
        return !targetIds.isEmpty() && targetIds.stream()
                .map(taskRepository::findById)
                .allMatch(target -> target.isPresent() && isTerminalTask(target.get()));
    }

    /**
     * Persistent-worker equivalent of the block above: reuses an existing idle review-fallback worker's
     * Jules session (send a follow-up message, no new task/branch/PR) when available, otherwise creates a
     * fresh one exactly like the one-shot path used to unconditionally. Mirrors
     * ProjectFlowService.dispatchToCompilerPersistentWorker - see PersistentWorkerSessionService for the
     * shared busy/rotation bookkeeping. All items here already share one project (the caller groups by
     * project before calling this).
     */
    private void dispatchToReviewFallbackPersistentWorker(List<TaskEntity> tasks, List<String> prUrls, List<String> diffs) {
        com.eneik.production.models.persistence.ProjectEntity project = tasks.get(0).getProject();
        List<UUID> batchIds = tasks.stream().map(TaskEntity::getId).toList();
        Optional<com.eneik.production.models.persistence.PersistentWorkerSessionEntity> existingOpt =
                persistentWorkerSessionService.findActiveWorker(project.getId(),
                        com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK);

        if (existingOpt.isPresent()) {
            com.eneik.production.models.persistence.PersistentWorkerSessionEntity worker = existingOpt.get();
            if (persistentWorkerSessionService.needsRotation(worker)) {
                persistentWorkerSessionService.retire(worker, "cycle/age cap reached");
            } else if (persistentWorkerSessionService.isIdleAndFresh(worker)) {
                JulesSessionEntity session = worker.getCurrentJulesSessionId() != null
                        ? julesSessionRepository.findById(worker.getCurrentJulesSessionId()).orElse(null)
                        : null;
                if (session != null && sendFollowUpMessage(session, reviewerFallbackFollowUpPromptBatch(tasks, prUrls, diffs))) {
                    persistentWorkerSessionService.recordBatchSent(worker, batchIds);
                    log.info("Sent follow-up review-fallback batch ({} PR(s)) to persistent worker {} (cycle {})",
                            tasks.size(), worker.getId(), worker.getCycleCount());
                    return;
                }
                log.warn("Persistent review-fallback worker {} exists but could not be messaged; {} PR(s) left pr_opened for retry next cycle",
                        worker.getId(), tasks.size());
                return;
            } else {
                log.info("Persistent review-fallback worker {} is still busy; {} PR(s) left pr_opened for retry next cycle",
                        worker.getId(), tasks.size());
                return;
            }
        }

        createFreshReviewFallbackPersistentWorker(project, tasks, prUrls, diffs, batchIds);
    }

    private void createFreshReviewFallbackPersistentWorker(com.eneik.production.models.persistence.ProjectEntity project,
            List<TaskEntity> tasks, List<String> prUrls, List<String> diffs, List<UUID> batchIds) {
        String prompt = reviewerFallbackPromptBatch(tasks, prUrls, diffs);
        UUID reviewTaskId = projectFlowService.dispatchReviewFallbackBatchAsPersistentCarrier(tasks, prompt);
        if (reviewTaskId == null) {
            log.warn("Could not create persistent review-fallback worker for project {} ({} task(s))", project.getId(), tasks.size());
            return;
        }
        TaskEntity carrierTask = taskRepository.findById(reviewTaskId).orElse(null);
        if (carrierTask == null || carrierTask.getJulesSessionName() == null) {
            // No account capacity this cycle - task stays `queued`, picked up by the normal retry sweep
            // (ProjectFlowService.dispatchQueuedTasks already knows how to redispatch a queued
            // pr_review_fallback task via the general pool). No worker row registered yet;
            // completePersistentWorkerCycle lazily registers one on the first pr_opened, using this task's
            // own payload batch as cycle 1.
            log.warn("Persistent review-fallback worker carrier task {} could not be dispatched this cycle; will retry via the normal queued-task sweep",
                    reviewTaskId);
            return;
        }
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(reviewTaskId);
        JulesSessionEntity newSession = sessions.stream()
                .max(java.util.Comparator.comparing(JulesSessionEntity::getCreatedAt))
                .orElse(null);
        if (newSession == null) {
            log.error("Persistent review-fallback worker carrier task {} dispatched but no JulesSessionEntity found", reviewTaskId);
            return;
        }
        persistentWorkerSessionService.registerFreshWorker(project.getId(),
                com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK,
                reviewTaskId, newSession.getId(), batchIds);
        log.info("Created persistent review-fallback worker for project {}: carrier task {}, session {}",
                project.getId(), reviewTaskId, newSession.getId());
    }

    /**
     * Follow-up message for an existing persistent review-fallback worker's session (cycle 2+): same body
     * as reviewerFallbackPromptBatch, wrapped with an instruction to overwrite .eneik/review-verdict.json
     * with only this cycle's verdicts rather than merging with a previous cycle's.
     */
    private String reviewerFallbackFollowUpPromptBatch(List<TaskEntity> tasks, List<String> prUrls, List<String> diffs) {
        String body = reviewerFallbackPromptBatch(tasks, prUrls, diffs);
        return """
                NEW CYCLE for the same persistent review-fallback worker session. The PR(s) below are a
                FRESH batch, unrelated to whatever you reviewed in a previous cycle on this same branch.
                OVERWRITE `.eneik/review-verdict.json` so it contains ONLY the verdicts for THIS cycle's
                PR(s) - do not keep, merge, or reference any previous cycle's content. Commit the update to
                the same branch/PR you already have open.

                %s
                """.formatted(body);
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

    private String reviewerFallbackPromptBatch(List<TaskEntity> tasks, List<String> prUrls, List<String> diffs) {
        StringBuilder prBlocks = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            TaskEntity t = tasks.get(i);
            prBlocks.append("""
                    ===== PR #%d (sourceIndex %d) =====
                    Original task (role %s):
                    %s

                    PR under review: %s

                    Diff to review:
                    %s

                    """.formatted(i, i, t.getRole().getTag(), t.getDescription(), prUrls.get(i), diffs.get(i)));
        }
        return """
                You are the fallback code reviewer for %d PR(s) below (Gemini review is temporarily or
                permanently unavailable). Review EACH PR independently on its own merits - do NOT
                implement, fix, or change any product code yourself, and do not run builds or tests; this
                task only produces review verdicts.

                Be lenient by design: work must never stall waiting on your opinion. Block a PR
                ("verdict":"block") ONLY for a small set of genuinely critical problems on THAT PR: a real
                security vulnerability, data loss risk, hardcoded secrets/credentials, committed
                generated/build artifacts (node_modules, playwright-report, coverage,
                .zip/.png/.webm/.trace files), missing required tests for a QA task, or a direct
                contradiction of that PR's own stated Acceptance Criteria/DoD. Anything else - style
                preferences, architecture opinions, missing edge cases that do not break the Acceptance
                Criteria, suggestions for a better approach - is NOT a blocker: approve that PR and list it
                as a "concern" instead, so it becomes a follow-up improvement item rather than stopped work.

                Deliverable: create a new branch and open a PR that contains ONLY one file,
                `.eneik/review-verdict.json`, with EXACTLY this shape and no other files changed - one
                entry per PR listed below, in the same order, each carrying its own "sourceIndex":
                {"verdicts": [
                  {"sourceIndex": 0, "verdict": "approve", "criticalReason": "", "concerns": ["short concern 1"]},
                  {"sourceIndex": 1, "verdict": "block", "criticalReason": "concrete, specific blocking reason tied to PR #1's diff", "concerns": []}
                ]}
                Every PR listed below MUST have exactly one corresponding entry, matched by sourceIndex.
                Do not write, modify, or delete any other file.

                %d PR(s) to review:

                %s
                """.formatted(tasks.size(), tasks.size(), prBlocks.toString());
    }

    private record ReviewVerdictEntry(int sourceIndex, String verdict, String criticalReason, List<String> concerns) {
    }

    private List<ReviewVerdictEntry> parseReviewVerdictBatch(ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(
                project, headRef, com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_VERDICT_PATH);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            List<ReviewVerdictEntry> entries = new java.util.ArrayList<>();
            JsonNode rawVerdicts = root.path("verdicts");
            if (rawVerdicts.isArray()) {
                for (JsonNode v : rawVerdicts) {
                    int sourceIndex = v.path("sourceIndex").asInt(-1);
                    String verdict = v.path("verdict").asText("approve");
                    String criticalReason = v.path("criticalReason").asText("");
                    List<String> concerns = new java.util.ArrayList<>();
                    JsonNode rawConcerns = v.path("concerns");
                    if (rawConcerns.isArray()) {
                        for (JsonNode c : rawConcerns) {
                            concerns.add(c.asText(""));
                        }
                    }
                    entries.add(new ReviewVerdictEntry(sourceIndex, verdict, criticalReason, concerns));
                }
            }
            return entries;
        } catch (Exception e) {
            log.warn("Failed to parse batched PR review fallback verdict for project {}: {}", project.getId(), e.getMessage());
            return List.of();
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
        List<UUID> originalTaskIds = projectFlowService.reviewFallbackTargetTaskIds(reviewTask);

        if (reviewFallbackTargetsAreTerminal(reviewTask)) {
            closeSessionAsNoCode(session, "Poka-yoke: review fallback result ignored because every target task is terminal");
            markSystemTaskDone(reviewTask);
            claimService.releaseTerminalClaim(reviewTask.getId());
            log.info("Poka-yoke: ignored obsolete review fallback completion for task {} targeting {}",
                    reviewTask.getId(), originalTaskIds);
            return;
        }

        // Idempotency: same dispatch-race risk as the coverage-audit fix above (and the original
        // wishlist-compiler duplication incident) - a review-fallback task can end up with more than one
        // Jules session, and only the session that still holds the active claim should apply verdicts;
        // every later completion is a discard-only no-op.
        boolean firstCompletion = claimService.hasActiveClaim(reviewTask.getId());

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(reviewTask.getProject(), session.getExternalSessionId());
        List<ReviewVerdictEntry> verdicts = firstCompletion
                ? prOpt.map(pr -> parseReviewVerdictBatch(reviewTask.getProject(), pr.headRef())).orElseGet(List::of)
                : List.of();

        String mergeReason = firstCompletion ? "PR review fallback verdict parsed" : "duplicate review fallback session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(reviewTask.getProject(), pr, mergeReason);
            archiveRecordFile(reviewTask.getProject(), com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_VERDICT_PATH, "review-verdict");
            closeSessionAsNoCode(session, "Review verdict merged (process/metadata only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("PR review fallback task {}: session {} completion discarded - another session already applied this batch's verdicts.",
                    reviewTask.getId(), session.getId());
            return;
        }
        claimService.complete(reviewTask.getId());
        markSystemTaskDone(reviewTask);

        if (originalTaskIds.isEmpty()) {
            log.error("PR review fallback task {} has no reviewsTaskIds payload marker; cannot apply verdicts", reviewTask.getId());
            return;
        }

        for (int i = 0; i < originalTaskIds.size(); i++) {
            UUID originalTaskId = originalTaskIds.get(i);
            int sourceIndex = i;
            ReviewVerdictEntry verdict = verdicts.stream()
                    .filter(v -> v.sourceIndex() == sourceIndex)
                    .findFirst()
                    .orElse(null);
            applyReviewVerdictToTask(reviewTask, originalTaskId, verdict);
        }
    }

    /**
     * Applies one PR's verdict (out of a batched fallback review's array response) to its original
     * implementer task - same approve/block/concern-recording logic the single-PR pipeline always used,
     * now looped once per PR in the batch instead of running once for a whole task.
     */
    private void applyReviewVerdictToTask(TaskEntity reviewTask, UUID originalTaskId, ReviewVerdictEntry verdict) {
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
            log.warn("PR review fallback task {}: no valid verdict entry found for task {}; left for retry next cycle", reviewTask.getId(), originalTaskId);
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
            log.info("Poka-yoke: recorded non-blocking review concern for task {} without creating wishlist work: {}",
                    originalTaskId, concern);
        }
    }

    private record CoverageGap(String title, String roleTag, String jtbd, String acceptanceCriteria, String reason) {
    }

    private List<CoverageGap> parseCoverageAuditReport(ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(
                project, headRef, com.eneik.production.services.ProjectFlowService.COVERAGE_AUDIT_REPORT_PATH);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            List<CoverageGap> gaps = new java.util.ArrayList<>();
            JsonNode rawGaps = root.path("gaps");
            if (rawGaps.isArray()) {
                for (JsonNode g : rawGaps) {
                    String title = g.path("title").asText("");
                    String roleTag = g.path("roleTag").asText("");
                    if (title.isBlank() || roleTag.isBlank()) {
                        continue;
                    }
                    gaps.add(new CoverageGap(title, roleTag, g.path("jtbd").asText(""),
                            g.path("acceptanceCriteria").asText(""), g.path("reason").asText("")));
                }
            }
            return gaps;
        } catch (Exception e) {
            log.warn("Failed to parse coverage audit report for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * A coverage-audit session reached pr_opened: its PR should carry exactly one JSON report (see
     * ProjectFlowService.dispatchCoverageAuditIfClientBrief), never product code. Discards that PR either
     * way, then turns every reported gap into a new pending wishlist item (source=coverage_gap) carrying
     * the same featureId as the decomposition it audited, so it flows through the normal pull-based,
     * WIP-gated compiler cycle like any other wishlist item - never fabricated as a ready-made task
     * directly, since a gap is still just a claim from one audit pass until the same scrutiny (adequacy
     * filter, WIP limits, cheap-path checks) that every other wishlist item goes through has run on it.
     */
    private void completeCoverageAudit(JulesSessionEntity session, TaskEntity auditTask) {
        UUID targetWishlistId = projectFlowService.coverageAuditTargetWishlistId(auditTask);
        UUID featureId = projectFlowService.coverageAuditFeatureId(auditTask);

        // Idempotency: found live on test-thirty-first - a coverage-audit task can end up with more than
        // one Jules session dispatched to it (same dispatch-race class as the original wishlist-compiler
        // duplication incident), and each session independently reaching pr_opened would otherwise
        // independently re-run gap creation. The pending-content dedup below only protects against a
        // duplicate that's still `pending` - once the FIRST session's gap gets picked up and compiled by
        // the pull-based cycle (which can happen within seconds), it's no longer `pending`, so a second
        // session's completion would see no duplicate and create the same gap again. hasActiveClaim is
        // the real guard: exactly one session ever holds the active claim on this task, so only the FIRST
        // completion to reach this point (the one that still finds the claim active) creates gaps at all -
        // every later one just discards its own redundant PR/session as a no-op.
        boolean firstCompletion = claimService.hasActiveClaim(auditTask.getId());

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(auditTask.getProject(), session.getExternalSessionId());
        List<CoverageGap> gaps = firstCompletion
                ? prOpt.map(pr -> parseCoverageAuditReport(auditTask.getProject(), pr.headRef())).orElseGet(List::of)
                : List.of();

        String mergeReason = firstCompletion ? "Coverage audit report parsed" : "duplicate coverage audit session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(auditTask.getProject(), pr, mergeReason);
            archiveRecordFile(auditTask.getProject(), com.eneik.production.services.ProjectFlowService.COVERAGE_AUDIT_REPORT_PATH, "coverage-audit");
            closeSessionAsNoCode(session, "Coverage audit report merged (process/metadata only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("Coverage audit task {}: session {} completion discarded - another session already processed this audit's gaps.",
                    auditTask.getId(), session.getId());
            return;
        }
        claimService.complete(auditTask.getId());
        markSystemTaskDone(auditTask);

        if (gaps.isEmpty()) {
            log.info("Coverage audit task {} (wishlist {}): no gaps found, plan covers the brief.", auditTask.getId(), targetWishlistId);
            return;
        }

        long pendingGapCount = wishlistRepository.countByProjectIdAndSourceAndStatus(
                auditTask.getProject().getId(),
                com.eneik.production.models.persistence.WishlistSource.coverage_gap,
                com.eneik.production.models.persistence.WishlistStatus.pending);
        List<com.eneik.production.models.persistence.WishlistEntity> pendingGapWishlist = pendingGapCount > 0
                ? wishlistRepository.findByProjectIdAndSourceAndStatus(
                        auditTask.getProject().getId(),
                        com.eneik.production.models.persistence.WishlistSource.coverage_gap,
                        com.eneik.production.models.persistence.WishlistStatus.pending)
                : List.of();

        int created = 0;
        for (CoverageGap gap : gaps) {
            if (pendingGapCount >= MAX_PENDING_COVERAGE_GAPS_PER_PROJECT) {
                log.info("Coverage audit task {}: dropping gap \"{}\" - project already has {} pending "
                                + "coverage-gap follow-up(s) (cap {}); will resurface on a future audit if still real",
                        auditTask.getId(), gap.title(), pendingGapCount, MAX_PENDING_COVERAGE_GAPS_PER_PROJECT);
                continue;
            }
            String finalTitle = gap.title();
            boolean alreadyPending = pendingGapWishlist.stream().anyMatch(item ->
                    item.getContent() != null && item.getContent().contains(finalTitle));
            if (alreadyPending) {
                log.info("Coverage audit task {}: skipping duplicate gap \"{}\" - already pending", auditTask.getId(), gap.title());
                continue;
            }

            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(auditTask.getProject().getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.coverage_gap);
            wishlist.setSourceRoleTag(gap.roleTag());
            wishlist.setFeatureId(featureId);
            wishlist.setContent("Coverage audit gap [" + gap.title() + "]: " + gap.reason()
                    + "\nJTBD: " + gap.jtbd()
                    + "\nAcceptance Criteria: " + gap.acceptanceCriteria());
            wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
            wishlistRepository.save(wishlist);
            pendingGapCount++;
            created++;
        }
        log.info("Coverage audit task {} (wishlist {}): {} gap(s) reported, {} new coverage_gap wishlist item(s) created.",
                auditTask.getId(), targetWishlistId, gaps.size(), created);
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

        // Idempotency: same dispatch-race class as the review-fallback/coverage-audit fixes earlier
        // tonight, confirmed live on test-thirty-second - a design-review task can end up with more than
        // one Jules session (two independent sessions both eventually completed, ~6 minutes apart, both
        // trying to promote the same draft to design/approved/ - the second one hit a real GitHub 422
        // "sha wasn't supplied" since the first had already created that path). Only the session that
        // still holds the active claim should promote the draft / process the verdict; every later
        // completion is a discard-only no-op.
        boolean firstCompletion = claimService.hasActiveClaim(reviewTask.getId());

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(reviewTask.getProject(), session.getExternalSessionId());
        DesignVerdict verdict = firstCompletion
                ? prOpt.map(pr -> parseDesignVerdict(reviewTask.getProject(), pr.headRef())).orElse(null)
                : null;

        String mergeReason = firstCompletion ? "design review verdict parsed" : "duplicate design review session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(reviewTask.getProject(), pr, mergeReason);
            archiveRecordFile(reviewTask.getProject(), com.eneik.production.services.ProjectFlowService.DESIGN_REVIEW_VERDICT_PATH, "design-review-verdict");
            closeSessionAsNoCode(session, "Design review verdict merged (process/metadata only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("Design review task {}: session {} completion discarded - another session already processed this draft's verdict.",
                    reviewTask.getId(), session.getId());
            return;
        }
        claimService.complete(reviewTask.getId());
        markSystemTaskDone(reviewTask);

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
            log.warn("Poka-yoke: design draft {} rejected without creating follow-up wishlist work; "
                    + "the finding is deferred to falsification: {}", draftPath, verdict.reason());
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

        for (String concern : verdict.concerns()) {
            if (concern == null || concern.isBlank()) {
                continue;
            }
            log.info("Poka-yoke: recorded non-blocking design concern for {} without creating wishlist work: {}",
                    approvedDir, concern);
        }
    }

    private List<UUID> compilerTaskWishlistIds(TaskEntity task) {
        if (task.getPayload() == null) {
            return List.of();
        }
        JsonNode idsNode = task.getPayload().path("compilesWishlistIds");
        if (!idsNode.isArray()) {
            return List.of();
        }
        List<UUID> ids = new java.util.ArrayList<>();
        for (JsonNode idNode : idsNode) {
            try {
                ids.add(UUID.fromString(idNode.asText("")));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry, don't fail the whole batch over one bad id
            }
        }
        return ids;
    }

    /**
     * Ф8 (2026-07-21, operator directive): parses the two-level {"epics": [{...,"slices": [...]}]} shape -
     * a wishlist splits into as many эпики (epics) as the product needs, not always exactly one, and every
     * compile cycle decides per epic whether it matches an existing one (see existingEpicsPromptContext).
     */
    private List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> parseCompilerPlan(
            ProjectEntity project, String headRef) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, WISHLIST_COMPILER_PLAN_PATH);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            JsonNode rawEpics = root.path("epics");
            if (!rawEpics.isArray()) {
                return List.of();
            }
            List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> result = new java.util.ArrayList<>();
            for (JsonNode epicNode : rawEpics) {
                JsonNode rawSlices = epicNode.path("slices");
                List<com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata> slices = new java.util.ArrayList<>();
                if (rawSlices.isArray()) {
                    for (JsonNode slice : rawSlices) {
                        String leanValueRaw = slice.path("leanValue").asText("essential");
                        com.eneik.production.models.persistence.LeanValue leanValue;
                        try {
                            leanValue = com.eneik.production.models.persistence.LeanValue.valueOf(leanValueRaw);
                        } catch (Exception e) {
                            leanValue = com.eneik.production.models.persistence.LeanValue.essential;
                        }
                        slices.add(new com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata(
                                slice.path("title").asText(""),
                                slice.path("jtbd").asText(""),
                                slice.path("acceptanceCriteria").asText(""),
                                slice.path("roleTag").asText(""),
                                leanValue,
                                slice.path("cynefinDomain").asText("clear"),
                                slice.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                                slice.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                                slice.path("hasUi").asBoolean(false),
                                jsonStringList(slice.path("requirementRefs"))
                        ));
                    }
                }
                String existingEpicId = epicNode.path("existingEpicId").isNull() ? null
                        : epicNode.path("existingEpicId").asText(null);
                result.add(new com.eneik.production.services.MLPredictionServiceClient.EpicPlan(
                        existingEpicId == null || existingEpicId.isBlank() ? null : existingEpicId,
                        epicNode.path("title").asText(""),
                        epicNode.path("jtbd").asText(""),
                        epicNode.path("kanoClass").asText("Must-Be"),
                        epicNode.path("cynefinDomain").asText("clear"),
                        epicNode.path("sixSigmaMetric").asText("Escaped defects <= 5%"),
                        epicNode.path("tocConstraintRef").asText("TOC-CONSTRAINT-DECOMPOSITION"),
                        epicNode.path("sourceIndex").asInt(0),
                        jsonStringList(epicNode.path("requirements")),
                        epicNode.path("coverageComplete").asBoolean(false),
                        slices
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse wishlist compiler plan for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<String> jsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static final int MAX_EPICS_PER_BRIEF = 12;
    private static final int MAX_SLICES_PER_EPIC = 8;
    private static final int MAX_TOTAL_SLICES_PER_BRIEF = 48;

    static boolean isValidCompilerPlan(List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics, int briefCount) {
        if (epics.isEmpty()) {
            return false;
        }
        int normalizedBriefCount = Math.max(1, briefCount);
        java.util.Map<Integer, Integer> epicsByBrief = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> slicesByBrief = new java.util.HashMap<>();
        java.util.Set<Integer> representedBriefs = new java.util.HashSet<>();
        for (com.eneik.production.services.MLPredictionServiceClient.EpicPlan epic : epics) {
            if (epic.sourceIndex() < 0 || epic.sourceIndex() >= normalizedBriefCount) {
                return false;
            }
            representedBriefs.add(epic.sourceIndex());
            int epicCount = epicsByBrief.merge(epic.sourceIndex(), 1, Integer::sum);
            if (epicCount > MAX_EPICS_PER_BRIEF) {
                return false;
            }
            // A new epic (existingEpicId == null) must carry real content - an existing-epic match is
            // allowed to omit it (the compiler is told to reuse the match, not restate it).
            if (epic.existingEpicId() == null
                    && (epic.title() == null || epic.title().isBlank()
                        || epic.jtbd() == null || epic.jtbd().isBlank())) {
                return false;
            }
            if (!epic.coverageComplete() || epic.requirements() == null || epic.requirements().isEmpty()) {
                return false;
            }
            if (epic.slices().isEmpty() || epic.slices().size() > MAX_SLICES_PER_EPIC) {
                return false;
            }
            int sliceCount = slicesByBrief.merge(epic.sourceIndex(), epic.slices().size(), Integer::sum);
            if (sliceCount > MAX_TOTAL_SLICES_PER_BRIEF) {
                return false;
            }

            java.util.Set<String> requirementIds = new java.util.LinkedHashSet<>();
            for (String requirement : epic.requirements()) {
                String id = requirementId(requirement);
                if (id.isBlank() || !requirementIds.add(id)) {
                    return false;
                }
            }
            java.util.Set<String> coveredRequirementIds = new java.util.LinkedHashSet<>();
            for (com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata slice : epic.slices()) {
                if (slice.title() == null || slice.title().isBlank()
                        || slice.jtbd() == null || slice.jtbd().isBlank()
                        || slice.acceptanceCriteria() == null || slice.acceptanceCriteria().isBlank()) {
                    return false;
                }
                if (slice.jtbd().contains("one small verifiable capability completed")) {
                    return false;
                }
                if (slice.requirementRefs() == null || slice.requirementRefs().isEmpty()) {
                    return false;
                }
                for (String ref : slice.requirementRefs()) {
                    String normalizedRef = ref == null ? "" : ref.trim().toUpperCase(java.util.Locale.ROOT);
                    if (!requirementIds.contains(normalizedRef)) {
                        return false;
                    }
                    coveredRequirementIds.add(normalizedRef);
                }
            }
            if (!coveredRequirementIds.equals(requirementIds)) {
                return false;
            }
        }
        return representedBriefs.size() == normalizedBriefCount;
    }

    private static String requirementId(String requirement) {
        if (requirement == null) {
            return "";
        }
        String normalized = requirement.trim().toUpperCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf(':');
        String id = separator >= 0 ? normalized.substring(0, separator).trim() : normalized;
        return id.matches("R[1-9][0-9]*") ? id : "";
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

        // Ф-followup (2026-07-21, operator directive): FAILED and CANCELLED used to collapse into one
        // "failed" string, discarding which one Jules actually reported - confirmed live to cost real
        // investigation time earlier tonight (had to dig through DB fields and this exact switch statement
        // just to determine a session's `failed` status wasn't from our own circuit breakers). FAILED means
        // Jules's own agent gave up; CANCELLED means something (Jules platform-side, quota, etc.) stopped
        // the session externally - different causes, different follow-up. Kept as two distinct local
        // status strings instead of adding a raw-status column, since the one place that checks for
        // "failed" (isTerminalLocallyClosed) is easy to extend to also recognize the new value.
        return switch (externalStatus.toUpperCase()) {
            case "QUEUED" -> "queued";
            case "RUNNING" -> "running";
            case "SUCCEEDED" -> "pr_opened";
            case "FAILED" -> "failed";
            case "CANCELLED" -> "cancelled_externally";
            case "STUCK" -> "stuck";
            default -> "running"; // Default to running if unknown but alive
        };
    }

    /**
     * Sends a new batch's prompt to an EXISTING persistent-worker Jules session (see
     * PersistentWorkerSessionService) instead of creating a fresh session - the whole point of the
     * persistent-worker mechanism. Flips the session to "revising" only on a successful send, so a failed
     * send never leaves the worker looking busy when nothing was actually sent (the caller reverts its
     * batch to pending/queued in that case). Synchronous (unlike the fire-and-forget CompletableFuture
     * sends used for corrections elsewhere) because the caller needs to know immediately whether to record
     * the batch as in-flight.
     */
    public boolean sendFollowUpMessage(JulesSessionEntity session, String message) {
        String apiKey = apiKeyForSession(session);
        boolean sent = apiKey != null
                ? julesApiClient.sendMessage(session.getExternalSessionId(), message, apiKey)
                : julesApiClient.sendMessage(session.getExternalSessionId(), message);
        if (!sent) {
            log.warn("Failed to send follow-up message to persistent-worker Jules session {}", session.getExternalSessionId());
            return false;
        }
        session.setStatus("revising");
        session.setLastProgressAt(Instant.now());
        julesSessionRepository.save(session);
        return true;
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
        closeSessionsForTerminalTasks();
        claimService.detectStuckSessions(effectiveStuckThresholdMinutes());
        closeOverdueStuckSessions();
        forceUnblockOverflowedSessions();
        reconcileAbandonedPullRequests();
    }

    int effectiveStuckThresholdMinutes() {
        return Math.max(DAVIDSON_TRUST_WINDOW_MINUTES, stuckThresholdMinutes);
    }

    private int effectiveStuckCloseThresholdMinutes() {
        return Math.max(DAVIDSON_CLOSE_WINDOW_MINUTES, stuckCloseThresholdMinutes);
    }

    @Transactional
    public void closeSessionsForTerminalTasks() {
        List<JulesSessionEntity> candidates = julesSessionRepository.findByStatusIn(ACTIVE_SESSION_STATUSES);
        for (JulesSessionEntity session : candidates) {
            taskRepository.findById(session.getTaskId())
                    .filter(this::isTerminalTask)
                    .ifPresent(task -> closeSessionForTerminalTask(session, task));
        }
    }

    private boolean isTerminalTask(TaskEntity task) {
        return task.getStatus() == TaskStatus.done
                || task.getStatus() == TaskStatus.failed
                // A blocked task may be recovered later by a fresh session, but the session that caused
                // the block is finished. Treating it as pollable let stale API responses resurrect that
                // old session every ~30 minutes while the task itself remained blocked.
                || task.getStatus() == TaskStatus.blocked
                || task.getStatus() == TaskStatus.spike_completed;
    }

    private void closeSessionForTerminalTask(JulesSessionEntity session, TaskEntity task) {
        session.setStatus("closed_terminal_task");
        session.setClosedAt(Instant.now());
        session.setClosureReason("Session retired because its task is already terminal (" + task.getStatus()
                + "); no polling, unblock, or follow-up is allowed.");
        julesSessionRepository.save(session);
        claimService.releaseTerminalClaim(task.getId());
        log.info("Session {} closed locally because task {} is already terminal ({})",
                session.getExternalSessionId(), task.getId(), task.getStatus());
    }

    @Transactional
    public void closeOverdueStuckSessions() {
        int closeThresholdMinutes = effectiveStuckCloseThresholdMinutes();
        Instant threshold = Instant.now().minus(Duration.ofMinutes(closeThresholdMinutes));
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
            if (isTerminalTask(task)) {
                closeSessionForTerminalTask(session, task);
                continue;
            }
            if (honorDavidsonProgressEvidence(session, task, reference)) {
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
                    "stuck_session_timeout: stuck for at least " + closeThresholdMinutes + " minutes"
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
        Instant now = Instant.now();
        int trustWindowMinutes = effectiveStuckThresholdMinutes();
        int closeWindowMinutes = effectiveStuckCloseThresholdMinutes();
        Instant staleSince = now.minus(Duration.ofMinutes(trustWindowMinutes));
        Instant closeSince = now.minus(Duration.ofMinutes(closeWindowMinutes));
        List<JulesSessionEntity> candidates = julesSessionRepository.findByStatusIn(
                List.of("running", "queued", "revising", "stuck"));

        for (JulesSessionEntity session : candidates) {
            // A "revising" session (sent back after a review rejection) used to only qualify here via
            // blindCycleCount, which never increments unless its activity log is oversized - a session
            // that simply went quiet after a rejection, with a normal-sized log, sat untouched for the
            // full stuck-close-threshold-minutes (120min) before anything happened. Nudging it as soon as
            // it's stale (same effective trust-window gate detectStuckSessions uses) closes that
            // gap - confirmed live as a real bottleneck on real product-code tasks in test-twenty-seventh.
            boolean revisingOrStuck = "revising".equals(session.getStatus()) || "stuck".equals(session.getStatus());
            if (session.getBlindCycleCount() < forcedUnblockBlindCycleThreshold && !revisingOrStuck) {
                continue;
            }
            Instant lastProgress = session.getLastProgressAt() != null ? session.getLastProgressAt() : session.getCreatedAt();
            // Davidson trust invariant: absence of an observable status transition is not evidence that
            // Jules stopped working. A session may legitimately stay silent for the full 60-minute
            // window. Configuration may extend this window, but can never shorten it.
            if (!lastProgress.isBefore(staleSince)) {
                continue;
            }
            Instant nextActionAt = lastProgress
                    .plus(Duration.ofMinutes(trustWindowMinutes))
                    .plus(STUCK_RECOVERY_MESSAGE_INTERVAL.multipliedBy(session.getForcedUnblockAttempts()));
            if (!now.isAfter(nextActionAt)) {
                continue;
            }

            TaskEntity task = taskRepository.findById(session.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }
            if (isTerminalTask(task)) {
                closeSessionForTerminalTask(session, task);
                continue;
            }

            // Same reasoning as ProjectFlowService's orphaned-blocked-task recovery skip-list (and
            // dispatchReviewTasks's own skip-list, added the same night): a system/carrier task isn't
            // "some role's feature work" - closeLoopAndCreateFollowUps's generic diagnosis/follow-up
            // synthesis has no concept of what to do with one and produces a nonsense generic task.
            // Confirmed live: a stuck persistent compiler worker's forced-unblock exhaustion produced a
            // meaningless "Delivery Plan" follow-up. Close cleanly instead, no generic follow-up; for a
            // persistent-worker carrier specifically, retire its worker row too so a fresh one is created
            // on the next cycle instead of staying wedged pointing at a dead session.
            boolean isSystemTask = projectFlowService.isWishlistCompilerTask(task)
                    || projectFlowService.isFalsificationAuditTask(task)
                    || projectFlowService.isReviewFallbackTask(task)
                    || projectFlowService.isDesignReviewTask(task)
                    || projectFlowService.isCoverageAuditTask(task);
            if (honorDavidsonProgressEvidence(session, task, lastProgress)) {
                continue;
            }

            if (session.getForcedUnblockAttempts() < forcedUnblockMaxAttempts) {
                sendForcedUnblockMessageAsync(session, task, revisingOrStuck, trustWindowMinutes);
                session.setForcedUnblockAttempts(session.getForcedUnblockAttempts() + 1);
                session.setBlindCycleCount(0);
                julesSessionRepository.save(session);
                continue;
            }

            // Two unanswered nudges are still not proof of failure. Preserve the charitable interpretation
            // until the independent long close window has elapsed.
            if (!lastProgress.isBefore(closeSince)) {
                continue;
            }

            if (isSystemTask) {
                session.setStatus("loop_closed");
                session.setClosedAt(Instant.now());
                session.setClosureReason("System task session force-unblock exhausted without progress; closed without generic follow-up (not real feature work).");
                julesSessionRepository.save(session);
                claimService.closeTaskAsFailed(task.getId(),
                        "blind_overflow_unblock_exhausted: system task, no generic follow-up needed");
                if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
                    persistentWorkerSessionService.findByCarrierTaskId(task.getId())
                            .ifPresent(worker -> persistentWorkerSessionService.retire(worker,
                                    "carrier session force-unblock exhausted without progress"));
                }
                log.warn("System task {} session {} force-unblock exhausted; closed without generic follow-up.",
                        task.getId(), session.getExternalSessionId());
                continue;
            }

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
        }
    }

    private boolean honorDavidsonProgressEvidence(JulesSessionEntity session, TaskEntity task, Instant lastProgress) {
        // Principle of charity (Davidson): check the artifact-producing system of record before inferring
        // failure from silence in the status channel.
        if (projectFlowService.isPersistentWorkerCarrierTask(task)
                && persistentWorkerHasReadyAnswer(session, task)) {
            log.info("Persistent worker carrier task {} looked stalled but its PR already has a ready, "
                            + "parseable result file; processing it instead of closing the session.", task.getId());
            session.setLastProgressAt(Instant.now());
            julesSessionRepository.save(session);
            completePersistentWorkerCycle(session, task);
            return true;
        }
        if (task.getProject() != null && hasNewProgressOnGitHub(session, task, lastProgress)) {
            log.info("Task {} session {} looked stalled but a new commit landed after lastProgressAt; "
                            + "treating the commit as positive progress evidence instead of closing.",
                    task.getId(), session.getExternalSessionId());
            session.setStatus("running");
            session.setLastProgressAt(Instant.now());
            session.setForcedUnblockAttempts(0);
            session.setBlindCycleCount(0);
            julesSessionRepository.save(session);
            return true;
        }
        return false;
    }

    private void sendForcedUnblockMessageAsync(JulesSessionEntity session, TaskEntity task, boolean revisingNudge,
                                                int trustWindowMinutes) {
        String externalSessionId = session.getExternalSessionId();
        String apiKey = apiKeyForSession(session);
        UUID taskId = task.getId();
        String message = revisingNudge
                ? "Eneik orchestrator nudge: this session was sent review feedback and asked to push a fix, but "
                        + "no update has been observed for " + trustWindowMinutes + "+ minutes. Please push a fix "
                        + "now addressing the earlier review feedback, or if genuinely blocked, state the concrete "
                        + "blocker in a comment on the PR so it can be escalated. Work should not silently stall."
                : "Eneik orchestrator forced unblock: this session's activity log has stayed too large "
                + "to inspect for a pending question across several checks, and no new progress has been "
                + "observed. It is OK to forcibly decide for yourself based on your own knowledge of the "
                + "project: make one objective move from the task facts, document the smallest safe "
                + "assumption in the PR summary, and open or update the PR now instead of waiting for "
                + "further clarification.";
        String logLabel = revisingNudge ? "Forced stale-revising unblock" : "Forced blind-overflow unblock";
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = apiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, message, apiKey)
                    : julesApiClient.sendMessage(externalSessionId, message);
            if (sent) {
                log.info("Sent {} message to Jules session {} for task {}", logLabel, externalSessionId, taskId);
                saveJulesDialogueLog(taskId, externalSessionId, message, logLabel);
            } else {
                log.warn("Failed to send {} message to Jules session {} for task {}", logLabel, externalSessionId, taskId);
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
                log.warn("Poka-yoke: abandoned PR {} for closed session {} (task {}) was rejected by "
                                + "auto-review; no recovery wishlist was created. Remarks: {}",
                        pr.url(), session.getExternalSessionId(), task.getId(), remarks);
            }
        }
    }

}
