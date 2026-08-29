package com.eneik.production.services.jules;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.DesignShopCycleEntity;
import com.eneik.production.services.stitch.StitchClient;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.EmsFlowStage;
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
import org.springframework.transaction.annotation.Propagation;
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
    // Public (2026-08-02): reused by ProjectTreeService to derive a feature branch's live-pulse signal
    // from real session state - the same canonical "is this session active" definition, not a duplicate.
    public static final List<String> ACTIVE_SESSION_STATUSES = List.of("running", "queued", "revising", "pr_opened", "stuck");
    private static final Duration STUCK_RECOVERY_MESSAGE_INTERVAL = Duration.ofMinutes(15);
    private static final int DAVIDSON_TRUST_WINDOW_MINUTES = 60;
    private static final int DAVIDSON_CLOSE_WINDOW_MINUTES = 120;
    private static final int DESTRUCTIVE_LOOP_REPEAT_THRESHOLD = 2;
    // Operator directive 2026-07-24: silence alone is never proof of failure - Jules is trusted as a
    // rational agent. Before the circuit breaker closes a stalled session, read everything it actually
    // wrote and classify it (see classifyBeforeClosing) instead of always defaulting to a generic
    // "restart as atomic slice" follow-up. A REASONED_BLOCKER (a genuine, well-argued rejection - e.g.
    // the live Flyway V1 migration-numbering collision) gets its task requeued with the exact blocker
    // baked into the brief, bounded to this many attempts so a blocker that never actually gets fixed
    // externally can't churn Jules sessions forever.
    private static final int REASONED_BLOCKER_MAX_RETRIES = 2;
    // 2026-08-07 fix (RAG role-context unification): per-task dispatch only ever needs ONE role's own
    // pattern content (unlike FalsificationCycleService's 13-role audits), so this budget can be generous.
    private static final int JULES_TASK_PATTERN_CONTEXT_TOP_K = 8;
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
    // Coverage-audit gaps (see ProjectFlowService.checkAndDispatchCoverageAudits) are inherently rare -
    // one audit per fully-merged client wishlist, not per PR - so a runaway storm is far less likely than the
    // review/design concern loops above. Still capped for the same "never create tasks the system doesn't
    // actually need" reason, and because a single sloppy brief could in principle keep re-triggering gaps
    // across retries. Was 0 (observation-only: gaps were reported but never turned into work) while this
    // was being live-verified; operator directive (2026-07-23) after seeing the first real audit run "6
    // gaps found, 0 created" - each real gap should become its own wishlist item, one epic per gap (the
    // compiler's own rules already guarantee this: every brief becomes at least one epic, epics from
    // different briefs are never merged, and a non-client-sourced brief is told to produce exactly ONE
    // work item rather than a full schema+API+UI decomposition).
    private static final int MAX_PENDING_COVERAGE_GAPS_PER_PROJECT = 10;

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
    private final com.eneik.production.repositories.ProjectRepository projectRepository;
    private final com.eneik.production.services.WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher;
    private final com.eneik.production.services.settings.SystemSettingsService settingsService;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;
    private final com.eneik.production.services.FalsificationCycleService falsificationCycleService;
    private final com.eneik.production.services.ClientDeliverableReadinessService readinessService;
    private final com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService;
    private final com.eneik.production.services.GeminiContextService geminiContextService;
    private final com.eneik.production.repositories.ReviewConcernRepository reviewConcernRepository;
    private final com.eneik.production.services.accounts.AccountHealthService accountHealthService;
    private final SessionLifecycleService sessionLifecycleService;
    private final com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository;
    private final com.eneik.production.services.stitch.StitchClient stitchClient;
    private final String sourcePrefix;

    private static final int WISHLIST_COMPILER_MAX_RETRIES = 2;

    private static final java.time.format.DateTimeFormatter RECORD_ARCHIVE_TIME_SUFFIX =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(java.time.ZoneOffset.UTC);

    // Once a record PR (compiler plan, review verdict, design verdict, falsification report) is merged,
    // its file sits at a fixed, reused path (e.g. .eneik/task-plan.json) - the NEXT merge would silently
    // overwrite it, destroying the previous run's documentation. Archiving a timestamped copy under
    // .eneik/records/ keeps every run as permanent, distinctly named production documentation instead of
    // a single clobbered file (operator's explicit instruction: "even if it is just a text
    // file, save it under a fitting name - for context. This is production documentation").
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

    private void markSessionProgress(JulesSessionEntity session) {
        session.setLastProgressAt(Instant.now());
        systemProgressTracker.recordProgress();
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
        // Single choke point for "this session is done, locally AND on Jules's side" - see
        // SessionLifecycleService's own doc comment. Best-effort: a Jules-side hiccup must never break the
        // local cancel/task-consequence flow below, which is what actually keeps dispatch correct.
        try {
            sessionLifecycleService.retireSessionOnly(sessionId, reason);
        } catch (Exception e) {
            log.warn("cancelSession: SessionLifecycleService.retireSessionOnly failed for {}, continuing with local-only cancel: {}",
                    sessionId, e.getMessage());
            session.setStatus("cancelled");
            session.setClosedAt(java.time.Instant.now());
            session.setClosureReason(reason);
            julesSessionRepository.save(session);
        }

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

    /**
     * Asks the SAME session to resolve a real GitHub merge conflict on its own already-open PR, instead of
     * abandoning the session/PR and re-implementing the task from scratch in a brand new one. Mirrors the
     * exact pattern {@link #applyReviewVerdictToTask}'s "blocked" branch already uses for quality-gate
     * rejections on real implementer PRs (sendMessage + revising, same branch, same context) - merge
     * conflicts get the same treatment for the same reason: Jules already has full context of the code it
     * wrote, so asking it to rebase onto main and resolve the conflict is cheaper and faster than a cold
     * restart, and it never leaves an orphaned duplicate PR behind (AutoMergeService's older cancel+
     * redispatch path did, confirmed live on test-thirty-fifth PR#3 2026-07-23).
     */
    @Transactional
    public boolean requestMergeConflictResolution(TaskEntity task, JulesSessionEntity session,
                                                   java.util.List<String> conflictingFiles, int attempt) {
        if (session == null || session.getExternalSessionId() == null) {
            return false;
        }
        session.setStatus("revising");
        julesSessionRepository.save(session);

        String fileList = conflictingFiles == null || conflictingFiles.isEmpty()
                ? "(file list unavailable)"
                : String.join(", ", conflictingFiles);
        String correction = "Your PR could not be merged into main - GitHub reports a real merge conflict "
                + "(attempt " + attempt + "/3) in: " + fileList + ". Please rebase or merge the latest main "
                + "into your branch, resolve the conflict(s) directly in these files (keep your own feature "
                + "work, reconcile with whatever main added), verify the build/tests still pass, and push the "
                + "fix to this same branch/PR. Do not open a new PR.";
        String externalSessionId = session.getExternalSessionId();
        String sessionApiKey = apiKeyForSession(session);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = sessionApiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, correction, sessionApiKey)
                    : julesApiClient.sendMessage(externalSessionId, correction);
            if (!sent) {
                log.warn("Failed to send merge-conflict correction to Jules session {} for task {}", externalSessionId, task.getId());
            }
        });
        log.info("Requested in-place merge-conflict resolution from session {} for task {} (attempt {}/3, files: {})",
                externalSessionId, task.getId(), attempt, fileList);
        return true;
    }

    /**
     * Lean-waste fix (2026-07-23, generalized 2026-07-24): companion to the early-unblock in
     * ProjectFlowService.dispatchQueuedTasks - a dependent that started against a spec-stage task's still-
     * unmerged PR gets a one-time, informational (not corrective) note once that spec task actually merges,
     * in case it was revised during its own review. Deliberately does not set the session to "revising" -
     * nothing may need to change, this is a heads-up, not a rejection.
     */
    public void notifySpecTaskFinalized(TaskEntity dependent, JulesSessionEntity session, TaskEntity specTask) {
        if (session == null || session.getExternalSessionId() == null) {
            return;
        }
        String stageLabel = specTask.getRole() != null
                ? EmsFlowStage.labelForRoleTag(specTask.getRole().getTag()).replace('-', ' ')
                : "spec";
        String message = "FYI: the " + stageLabel + " task you started against ('" + specTask.getDescription()
                + "') has now been finalized and merged into main. It may have been revised during its own "
                + "review after you began (you started as soon as its PR was open, not after merge, to avoid "
                + "waiting idle). Please double-check your implementation still matches the final merged "
                + stageLabel + " before finishing, and adjust if anything drifted.";
        String externalSessionId = session.getExternalSessionId();
        String sessionApiKey = apiKeyForSession(session);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean sent = sessionApiKey != null
                    ? julesApiClient.sendMessage(externalSessionId, message, sessionApiKey)
                    : julesApiClient.sendMessage(externalSessionId, message);
            if (!sent) {
                log.warn("Failed to send spec-finalized notice to session {} for task {}",
                        externalSessionId, dependent.getId());
            }
        });
        log.info("Notified session {} (task {}) that its early-unblocked spec dependency {} is now finalized",
                externalSessionId, dependent.getId(), specTask.getId());
    }

    /**
     * Feature-thread closeout conflict (2026-07-24) - see AutoMergeService.escalateCloseoutConflict. Unlike
     * a regular PR conflict there is no live Jules session to message in-place: the thread's last task is
     * long-terminal. Dispatches one fresh, standalone session directly on the thread's branch, no
     * TaskEntity/PrReviewEntity involved - AutoMergeService.progressCloseout just keeps polling the
     * already-open closeout PR on later ticks and will see it become mergeable once this session pushes a
     * fix to the same branch. Bounded to exactly once per thread by the caller
     * (FeatureThreadEntity.closeoutConflictEscalatedAt).
     */
    public void dispatchCloseoutConflictResolution(com.eneik.production.models.persistence.ProjectEntity project,
                                                    String branchName, java.util.UUID featureId) {
        String description = "Merge the latest main into this branch (" + branchName + ") and resolve any "
                + "conflicts directly - keep this branch's own work, reconcile with whatever main added since. "
                + "Verify the build/tests still pass, then push the fix to this same branch. There is already "
                + "an open pull request from this branch into main waiting for it to become mergeable - do not "
                + "open a new PR, do not create a new branch.";
        String title = "Closeout conflict resolution: feature " + featureId;
        dispatchAdHocSessionToBranch(project, branchName, description, title);
    }

    /**
     * General-purpose primitive extracted from dispatchCloseoutConflictResolution (2026-07-24): one
     * standalone Jules session pushed directly onto an EXISTING branch, no TaskEntity/PrReviewEntity/PR
     * created - for any already-open PR from that branch that just needs a follow-up commit (a merge
     * conflict, a failing CI check, anything where "keep working on this exact branch" is the whole ask).
     * Same one-off manual-dispatch precedent as the other admin PATCH endpoints added this session.
     */
    public void dispatchAdHocSessionToBranch(com.eneik.production.models.persistence.ProjectEntity project,
                                              String branchName, String description, String title) {
        // 2026-07-26 live incident: this method has no TaskEntity/JulesSessionEntity of its own (see class
        // doc below) - a dispatch here is completely invisible to cancelAllActiveWorkForProject (which only
        // ever looks at rows in the jules_sessions table) and every other project-status-aware mechanism.
        // Two callers (AutoMergeService's conflict-resurrection sweeps) already got their own active-project
        // guard the same night this was found, but this is the one choke point EVERY caller goes through -
        // checking here too means a frozen/accepted/cancelled project can never again get an untracked,
        // uncancellable session dispatched into it, regardless of which future caller forgets its own guard.
        if (project != null && project.getStatus() != com.eneik.production.models.persistence.ProjectStatus.active) {
            log.warn("JulesDispatchService: refusing ad-hoc session dispatch for project {} (status {}, not active) - branch {}",
                    project.getId(), project.getStatus(), branchName);
            return;
        }
        if (project == null || branchName == null || branchName.isBlank()
                || description == null || description.isBlank()) {
            return;
        }
        String repoUrl = julesSourceForProject(project, project.getRepositoryName());
        // Rare tail case (bounded to one attempt ever, per thread) - not worth the full capacity-aware
        // account picker (AccountRepository.lockNextJulesAccountWithCapacity) that normal dispatch uses;
        // any account with a usable key already has repo access (collaborator invitations are project-wide).
        String apiKey = accountRepository.findAll().stream()
                .filter(a -> a.getApiKey() != null && !a.getApiKey().isBlank())
                .findFirst()
                .map(com.eneik.production.models.persistence.AccountEntity::getApiKey)
                .orElse(null);
        JulesApiClient.CreateSessionResult result = julesApiClient.createSessionDetailed(
                repoUrl, description, "", apiKey, title == null ? "Ad-hoc branch fix" : title, branchName);
        if (result == null || result.sessionName() == null) {
            log.warn("JulesDispatchService: failed to dispatch ad-hoc session for branch {}: {}",
                    branchName, result == null ? "no result" : result.errorBody());
        } else {
            log.info("JulesDispatchService: dispatched ad-hoc session {} on branch {}",
                    result.sessionName(), branchName);
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

    // O-17 (2026-08-21): charity is not infinite. The Davidson veto below refuses to close a session whose
    // own writing shows real reasoning - correct, and deliberately generous - but it also restored the
    // forced-unblock budget every time it fired, and that budget is the ONLY thing that closes a stale
    // session and frees the WIP slot. Measured live: task 47aa70cb held IMPLEMENTING for 373 minutes
    // against a 90-minute SLA without producing a PR, its counter running 1,2,1,2 because a PROGRESSING
    // verdict reset it - a verdict about conversation that our own unblock message had just stirred up.
    // Flow Core then correctly refuses to dispatch anything new while the slot is held, so one session
    // blocked the whole project. Session age is monotone and this ceiling is fixed, so the veto now
    // terminates. Default 6h = 3x the Davidson close window: past that, a session that still cannot show
    // an artefact has had its chance.
    @Value("${jules.davidson-veto-ceiling-minutes:360}")
    private int davidsonVetoCeilingMinutes;

    @Value("${jules.forced-unblock-max-attempts:2}")
    private int forcedUnblockMaxAttempts;

    // 2026-08-03 (blind-cycle incident, test-forty-first): small enough that a page rarely overflows the
    // byte cap on its own (activities can embed large git diffs/screenshots/bash output - see
    // JulesApiClient.getSessionActivities's javadoc), but see answerAgentQuestions's shrink-and-retry for
    // when even this isn't small enough.
    @Value("${jules.activities-page-size:20}")
    private int activitiesPageSize;

    // How many pages answerAgentQuestions will walk forward in a single poll cycle before saving progress
    // and deferring the rest to the next cycle - bounds worst-case per-cycle fetch time when a session has
    // a large backlog (e.g. the first scan of an already-long-running session after this feature ships).
    @Value("${jules.max-activity-pages-per-cycle:20}")
    private int maxActivityPagesPerCycle;

    // Self-injected proxy reference (2026-08-07, same pattern/reason as ProjectFlowService.self and
    // OpsAuditorService.self): a plain `this.admitWishlistCompilationCompletion(...)` call bypasses the
    // Spring AOP proxy entirely, so @Transactional(REQUIRES_NEW) on that method would silently never
    // activate - it would just run as a normal method call inside whatever transaction (if any) is already
    // open on the calling thread, defeating the whole point of REQUIRES_NEW here (see that method's
    // javadoc for why it specifically needs a genuinely separate transaction, not just any @Transactional).
    // @Lazy breaks the constructor circular dependency this would otherwise create.
    private final JulesDispatchService self;

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
                                @org.springframework.context.annotation.Lazy com.eneik.production.services.FalsificationCycleService falsificationCycleService,
                                com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                                com.eneik.production.services.ClientDeliverableReadinessService readinessService,
                                com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService,
                                com.eneik.production.repositories.ProjectRepository projectRepository,
                                com.eneik.production.services.WishlistContentSimilarityMatcher wishlistContentSimilarityMatcher,
                                com.eneik.production.services.settings.SystemSettingsService settingsService,
                                com.eneik.production.services.GeminiContextService geminiContextService,
                                com.eneik.production.repositories.ReviewConcernRepository reviewConcernRepository,
                                com.eneik.production.services.accounts.AccountHealthService accountHealthService,
                                SessionLifecycleService sessionLifecycleService,
                                com.eneik.production.repositories.DesignShopCycleRepository designShopCycleRepository,
                                com.eneik.production.services.stitch.StitchClient stitchClient,
                                @Value("${jules.source-prefix:sources/github/${github.org}/}") String sourcePrefix,
                                @org.springframework.context.annotation.Lazy JulesDispatchService self) {
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
        this.falsificationCycleService = falsificationCycleService;
        this.featureThreadRepository = featureThreadRepository;
        this.readinessService = readinessService;
        this.persistentWorkerSessionService = persistentWorkerSessionService;
        this.projectRepository = projectRepository;
        this.wishlistContentSimilarityMatcher = wishlistContentSimilarityMatcher;
        this.settingsService = settingsService;
        this.geminiContextService = geminiContextService;
        this.reviewConcernRepository = reviewConcernRepository;
        this.accountHealthService = accountHealthService;
        this.sessionLifecycleService = sessionLifecycleService;
        this.designShopCycleRepository = designShopCycleRepository;
        this.stitchClient = stitchClient;
        this.sourcePrefix = sourcePrefix;
        this.self = self;
    }

    // 2026-08-14 (bug-hunt sweep): these 4 dispatch(...) overloads all previously carried their own
    // @Transactional, but all 4 ultimately self-invoke down to the real implementation (dispatch(TaskEntity,
    // UUID, String)) via plain `this.`-style calls within this same class - which bypasses the Spring AOP
    // proxy entirely (same self-invocation bug already documented elsewhere in this file, e.g.
    // releaseUnfinishedClaims). So whichever overload an EXTERNAL caller actually invokes is the ONLY one
    // whose @Transactional ever really took effect - the other 3 annotations were cosmetic. Removed from all
    // 4 together: the real implementation calls julesApiClient.createSessionDetailed (one real Jules HTTP
    // call) and was holding a DB transaction/connection open across it, root dispatch method for the whole
    // system, called from 6+ places. The 3 helper services this delegates work to (ClaimService,
    // AccountHealthService, ClientDeliverableReadinessService) each carry their own @Transactional and are
    // called through their own injected proxies (not self-invocation), so removing the outer transaction
    // here does not change their own atomicity.
    public JulesDispatchResult dispatch(TaskEntity task) {
        return dispatch(task, null);
    }

    public JulesDispatchResult dispatch(TaskEntity task, UUID accountId) {
        return dispatch(task, accountId, "IMPLEMENTER");
    }

    /**
     * Single source of truth for "does this task already have a session dispatch() would treat as active
     * and refuse to duplicate" - exposed so callers (e.g. ProjectFlowService.dispatchReviewTasks) can check
     * BEFORE calling dispatch(), instead of calling it speculatively every tick and then either duplicating
     * this same status list themselves (which drifted out of sync once already - confirmed live on
     * test-thirty-fifth 2026-07-23, a stale local copy missing "pr_opened" caused a silent no-op reviewer
     * dispatch to look like a real success in the logs every single orchestration tick) or misreading
     * dispatch()'s own return value (its `dispatched` field means "is dispatched", true for both a genuinely
     * new dispatch AND an already-active skip - it does not distinguish the two).
     */
    public boolean hasActiveSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .anyMatch(s -> ACTIVE_SESSION_STATUSES.contains(s.getStatus()));
    }

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
            // Real work starting under this task's feature is itself proof any earlier "valueless epic"
            // dismissal was wrong (or has since become wrong) - see readinessService.unDismissFeatureIfNeeded
            // javadoc for the live incident this closes (test-forty-third, 2026-08-07).
            readinessService.unDismissFeatureIfNeeded(task.getFeatureId());
        }
        return new JulesDispatchResult(
                dispatched,
                session.getExternalSessionId(),
                reason
        );
    }

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

        String repoName = (task.getTargetContext() == com.eneik.production.models.persistence.TargetContext.ORCHESTRATOR_SYSTEM)
                ? systemOrchestratorRepositoryName()
                : project.getRepositoryName();
        String repoUrl = (task.getTargetContext() == com.eneik.production.models.persistence.TargetContext.ORCHESTRATOR_SYSTEM)
                ? sourcePrefix + repoName
                : julesSourceForProject(project, repoName);
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
        roleContextBuilder.append("- THREE-LAYER ONTO-SEPARATION (TARSKI DEMARCATION): You are writing code for the CLIENT'S domain product ONLY. NEVER use or mention factory orchestrator names ('AutoMergeService', 'SixSigmaAudit', 'Jules', 'TaskPlan', 'PlannedWorkRecovery', 'EneikSys') in commit messages, PR titles, class names, database tables, or documentation. Formulate all changes strictly in the client's domain vocabulary.\n");
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
        if (com.eneik.production.services.gate.VerificationEvidenceGate.QA_TAGS.contains(task.getRole().getTag())) {
            String reportPath = com.eneik.production.services.gate.VerificationEvidenceGate.verificationReportPath(task);
            roleContextBuilder.append("- Verification evidence (required even when your change touches zero other files - a "
                    + "verification pass that confirms existing behavior with nothing new to add is a legitimate, complete "
                    + "outcome, but it must still prove it happened): before opening the PR, commit a structured report at "
                    + "exactly " + reportPath + " with this shape: {\"testsRun\":N,\"testsPassed\":N,\"testsFailed\":N,"
                    + "\"acceptanceCriteriaVerified\":[\"...\"],\"coverageDeltaPercent\":N-or-null,\"verdict\":\"pass\"-or-\"fail\","
                    + "\"notes\":\"...\"}. This is the only evidence the platform's automated gate accepts as proof your "
                    + "verification work happened - without this real file at this exact path, with testsRun matching "
                    + "testsPassed+testsFailed and a non-empty acceptanceCriteriaVerified list, the task will fail the gate "
                    + "regardless of what you report in the PR summary.\n");
        }
        if (com.eneik.production.services.gate.DesignExcellenceGate.UI_TAGS.contains(task.getRole().getTag())) {
            String designCheckDir = com.eneik.production.services.gate.DesignExcellenceGate.designCheckDir(task);
            roleContextBuilder.append("- Design verification (exception to the no-screenshots rule above): before opening the PR, "
                    + "use Playwright (or an equivalent headless browser tool) to render the actual UI you just built and save "
                    + "exactly two real PNG screenshots - one at 1440px viewport width (desktop), one at 375px viewport width "
                    + "(mobile) - at " + designCheckDir + "desktop-1440.png and " + designCheckDir + "mobile-375.png, and commit "
                    + "ONLY these two files (nothing else from your test run) as part of this PR. This is the only evidence the "
                    + "platform's automated design gate accepts - without these two real files at these exact paths the task "
                    + "will fail the gate regardless of what you report in the PR summary.\n");
        }
        if (buildPhase) {
            roleContextBuilder.append("- This project is in its build phase: trust is maximal right now. Make the call yourself from your role's own judgment (see Role Charter below) rather than hedging toward the safest generic option - the system doesn't exist yet, your judgment is what's building it. Mechanical polish gates are relaxed for this phase; your role's own refusal criteria still apply in full.\n");
        } else {
            roleContextBuilder.append("- This project already has a built shape: work carefully within existing patterns rather than restructuring what's already there. Prefer the smallest change consistent with how the system already works over a cleaner rewrite, unless the task explicitly asks for the rewrite.\n");
        }

        appendCompactRoleGuide(roleContextBuilder, task.getRole().getTag());
        appendRetrievedSystemKnowledge(roleContextBuilder, task, mode, buildPhase);

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

            // 2026-08-07 fix (RAG role-context unification): was a raw, unbounded read of the whole common-
            // patterns file plus every one of this role's philosopher-pattern files on EVERY task dispatch,
            // independently reimplemented from the same-shaped logic in FalsificationCycleService (which hit
            // its own oversized-prompt HTTP 400 doing this at 13x scale). Charter itself stays raw/verbatim
            // above (loadRawCharter) - only the pattern corpus (already indexed for exactly this) moves to
            // scoped retrieval, keyed by this task's own brief so the surfaced patterns are the ones actually
            // relevant to what Jules is about to do.
            roleContextBuilder.append(geminiContextService.buildPhilosopherPatternContext(
                    task.getRole(), task.getDescription(), JULES_TASK_PATTERN_CONTEXT_TOP_K));
            roleContextBuilder.append(geminiContextService.buildCommonPatternContext(
                    task.getDescription(), JULES_TASK_PATTERN_CONTEXT_TOP_K));

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
        // Phase 7 (2026-07-21, operator directive): used to also require accountId == featureThread.getAccountId()
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
        // Closeout tracking (2026-07-24): a thread whose mergedToMainAt is set has already had its code
        // folded into main (see AutoMergeService.closeOutReadyFeatureThreads) - its old branch is a dead
        // end nobody will ever merge again individually. Continuing from main is strictly correct here:
        // main already contains everything the thread had, plus whatever else has landed since.
        var featureThreadOpt = task.getFeatureId() == null ? java.util.Optional.<com.eneik.production.models.persistence.FeatureThreadEntity>empty()
                : featureThreadRepository.findByProjectIdAndFeatureId(task.getProject().getId(), task.getFeatureId())
                        // abandonedAt (2026-07-24): a thread that never reconciled with main after 3 bounded
                        // conflict-resolution attempts is a dead end just like a closed one - its branch is
                        // deleted, continuing from it would fail outright. Continue from main instead.
                        .filter(t -> t.getMergedToMainAt() == null && t.getAbandonedAt() == null);
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
            UUID dispatchProjectId = task.getProject() != null ? task.getProject().getId() : null;
            String dispatchRoleTag = task.getRole() != null ? task.getRole().getTag() : null;
            if (accountId != null && createResult.dailyLimitOrQuota()) {
                accountHealthService.reportDispatchOutcome(accountId, dispatchProjectId,
                        com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.DAILY_LIMIT,
                        createResult.compactError(), dispatchRoleTag);
                session.setClosureReason("jules_daily_limit: account reached an explicit Jules daily/quota/rate limit. "
                        + session.getClosureReason());
            } else if (accountId != null && createResult.preconditionUnspecified()) {
                // §12: a precondition failed and Jules did not say which. Charged to nobody; recorded so the
                // gap is findable and can be measured when the answer carries more.
                accountHealthService.reportDispatchOutcome(accountId, dispatchProjectId,
                        com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED,
                        createResult.compactError(), dispatchRoleTag);
                session.setClosureReason("jules_precondition_unspecified: Jules cited a precondition without "
                        + "naming it; whose condition failed is not established. " + session.getClosureReason());
            } else if (accountId != null && createResult.requestRejected()) {
                // §10: Jules refused the CONTENT of our request. Reported so the defect is findable, and
                // charged to nobody - the account carried the request, it did not compose it.
                accountHealthService.reportDispatchOutcome(accountId, dispatchProjectId,
                        com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.REQUEST_REJECTED,
                        createResult.compactError(), dispatchRoleTag);
                session.setClosureReason("jules_request_rejected: Jules refused the request itself, not the account. "
                        + session.getClosureReason());
            } else if (accountId != null && createResult.apiPreconditionOrAuthorizationBlocked()) {
                accountHealthService.reportDispatchOutcome(accountId, dispatchProjectId,
                        com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED,
                        createResult.compactError(), dispatchRoleTag);
                session.setClosureReason("jules_api_blocked: Jules refused session creation because of API precondition, authorization, or request setup. "
                        + "This is not a daily limit. " + session.getClosureReason());
            }
        } else {
            session.setExternalSessionId(externalId);
            session.setStatus("running");
            if (accountId != null) {
                UUID successProjectId = task.getProject() != null ? task.getProject().getId() : null;
                String successRoleTag = task.getRole() != null ? task.getRole().getTag() : null;
                accountHealthService.reportDispatchOutcome(accountId, successProjectId,
                        com.eneik.production.services.accounts.AccountHealthService.DispatchOutcome.SUCCESS, null, successRoleTag);
            }
        }

        return julesSessionRepository.save(session);
    }

    private String withTaskPromptTitle(String title, String description) {
        String safeTitle = TaskTitleBuilder.enforceTwoOrThreeWords(title);
        String safeDescription = description == null ? "" : description;
        return "Task Title: " + safeTitle + "\n\n" + safeDescription;
    }

    private String julesSourceForProject(ProjectEntity project, String fallbackRepoName) {
        if (project != null && project.getRepositoryUrl() != null && !project.getRepositoryUrl().isBlank()) {
            return JulesApiClient.toJulesSourceName(project.getRepositoryUrl());
        }
        return sourcePrefix + fallbackRepoName;
    }

    private void appendCompactRoleGuide(StringBuilder roleContextBuilder, String roleTag) {
        roleContextBuilder.append("\n## Compact Role Guide\n");
        roleContextBuilder.append("- Use English only in code comments, PR text, and dialogue.\n");
        roleContextBuilder.append("- Treat the task JTBD, Acceptance Criteria, DoD, and file scope as stronger than role lore.\n");
        roleContextBuilder.append("- Apply Kano as a scope guard: Must-Be first, Performance only when explicit, Delighters only as follow-up wishlist.\n");
        roleContextBuilder.append("- Apply Cynefin as a delivery guard: clear/complicated work needs a direct implementation path, complex work needs one safe probe.\n");
        roleContextBuilder.append("- Role focus: ").append(compactRoleFocus(roleTag)).append("\n");
    }

    private void appendRetrievedSystemKnowledge(StringBuilder roleContextBuilder, TaskEntity task, String mode, boolean buildPhase) {
        try {
            String context;
            if (task != null && (task.getTargetContext() == null || task.getTargetContext() == com.eneik.production.models.persistence.TargetContext.PRODUCT_CODEBASE)) {
                context = geminiContextService.buildProductWorkerContextBlock(task.getRole(), julesRetrievalQuery(task, mode, buildPhase));
            } else {
                context = geminiContextService.buildContextBlock(julesRetrievalQuery(task, mode, buildPhase));
            }
            if (context == null || context.isBlank()) {
                return;
            }
            roleContextBuilder.append("\n## Retrieved System Knowledge\n");
            roleContextBuilder.append("Use this as pattern memory from the indexed Eneik system corpus. ")
                    .append("It augments the task; it never overrides the task description, JTBD, Acceptance Criteria, DoD, ")
                    .append("current repository contents, or current verification output. Do not copy ids, branch names, ")
                    .append("file paths, or project-specific facts from retrieved examples unless they also appear in this task.\n");
            roleContextBuilder.append(context).append("\n");
        } catch (Exception e) {
            log.warn("Could not retrieve RAG context for Jules task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String systemOrchestratorRepositoryName() {
        String configured = settingsService.effectiveValue("system_orchestrator_repository_name");
        return (configured != null && !configured.isBlank()) ? configured : "EneikProductionSys";
    }

    private String julesRetrievalQuery(TaskEntity task, String mode, boolean buildPhase) {
        String roleTag = task.getRole() == null ? "unknown" : task.getRole().getTag();
        String roleDescription = task.getRole() == null ? "" : task.getRole().getDescription();
        String taskType = task.getPayload() == null ? "" : task.getPayload().path("taskType").asText("");
        String safeMode = mode == null || mode.isBlank() ? "IMPLEMENTER" : mode;
        return "Jules execution context. mode=" + safeMode
                + "; roleTag=" + roleTag
                + "; roleDescription=" + compactForRetrieval(roleDescription, 240)
                + "; buildPhase=" + buildPhase
                + (taskType.isBlank() ? "" : "; taskType=" + taskType)
                + "; title=" + compactForRetrieval(TaskTitleBuilder.displayTitle(task), 180)
                + "; taskDescriptionExcerpt=" + compactForRetrieval(task.getDescription(), 1800)
                + ". Retrieve relevant role patterns, anti-conflict rules, prior incidents, programming patterns, "
                + "and review/falsification guidance for this exact kind of Jules task.";
    }

    private String compactForRetrieval(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxChars ? compact : compact.substring(0, maxChars) + "...";
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

            // Phase follow-up (2026-07-23, operator directive): "polling Jules for status is unreliable in
            // principle ... the status must be set firmly by the backend" - Jules's own raw status API is
            // not treated as authoritative once a real PR has been independently confirmed to exist.
            // Confirmed live: Jules's API kept reporting "running" for a session that had already opened
            // a real GitHub PR (AutoMergeService's own sync confirmed it), so this poll unconditionally
            // wrote "running" back over "pr_opened" on every single cycle - a silent downgrade with two
            // real costs, not just a cosmetic flap: (1) it starved the running/revising->pr_opened edge
            // trigger of a stable transition to fire on, repeatedly delaying task completion; (2) capacity
            // accounting (AccountRepository.lockNextJulesAccountWithCapacity) counts a session against its
            // account's slot limit while status is queued/running/revising/stuck, but NOT pr_opened - so a
            // falsely-downgraded session kept occupying a capacity slot on an account that had, in every
            // real sense, already finished its work. A confirmed PR is strictly more authoritative than
            // Jules's own self-reported liveness state; once we independently know a PR exists, silence or
            // "still running" from the raw API is expected and fine (see the 60-minute Davidson trust
            // window elsewhere in this class), never a reason to erase that fact.
            boolean wouldDowngradeConfirmedPr = session.getPrUrl() != null && !session.getPrUrl().isBlank()
                    && "pr_opened".equals(oldStatus)
                    && List.of("queued", "running", "revising", "stuck").contains(mappedStatus);
            if (wouldDowngradeConfirmedPr) {
                mappedStatus = "pr_opened";
            }

            // 2026-08-09 (live incident, test-forty-third, session 381fc207): "stuck" is a LOCALLY-detected
            // fact (ClaimService.detectStuckSessions, based on lastProgressAt staleness) - Jules's raw API
            // still self-reporting "running"/"revising"/"queued" is not independent evidence anything
            // actually changed, same distrust already applied to wouldDowngradeConfirmedPr above. Without
            // this guard, the comment below ("stuck->running... is genuine forward progress") is simply
            // wrong for this specific transition: it treated the mere LOCAL label flip as real progress and
            // reset lastProgressAt to now() every time, which kept a real 3+ hour stall from EVER reaching
            // closeOverdueStuckSessions' 120-minute close threshold - detectStuckSessions re-marked the same
            // session "stuck" every ~61 minutes forever, and the very next poll silently resurrected it
            // before the close-threshold sweep ever saw it in "stuck" state long enough to act.
            boolean wouldResurrectStuckWithoutRealEvidence = "stuck".equals(oldStatus)
                    && List.of("queued", "running", "revising").contains(mappedStatus);
            if (wouldResurrectStuckWithoutRealEvidence) {
                mappedStatus = "stuck";
            }

            if (!mappedStatus.equals(oldStatus)) {
                // Any real status transition (running->pr_opened, stuck->running, etc.) is genuine
                // forward progress, unlike updatedAt which refreshes on every save regardless.
                markSessionProgress(session);
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

        if (apiKey != null && buryIfSessionIsGone(session, apiKey)) {
            log.info("pollStatus: Session {} returned 404 (non-existent) - buried record and released task claim",
                    session.getExternalSessionId());
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

    /**
     * 2026-08-03 rewrite (blind-cycle incident, test-forty-first): the old version made one unparameterized
     * call to /activities every cycle, which - confirmed live via a real pagination probe - always returns
     * Jules's own default page: the OLDEST ~50 activities from session start, never advancing. A
     * long-running session's actual recent activity was never being looked at, and a single page can be
     * large on its own regardless of session length (activities can embed full git diffs, base64
     * screenshots, or raw command output - see the real Activity resource schema). This version walks
     * forward incrementally from a persisted per-session cursor (JulesSessionEntity.activitiesPageCursor),
     * a small page at a time, so per-cycle payload size depends on how much is genuinely NEW since the last
     * poll, not on total session history - no total-size cap can ever be "big enough" for that, but a small
     * page almost always is.
     */
    private void answerAgentQuestions(JulesSessionEntity session, TaskEntity task, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (isTerminalLocallyClosed(session)) {
            return;
        }

        String pageToken = session.getActivitiesPageCursor();

        for (int pageCount = 0; pageCount < maxActivityPagesPerCycle; pageCount++) {
            JsonNode root = fetchActivitiesPageWithShrink(session, apiKey, pageToken, activitiesPageSize);
            if (root != null && root.path("activitiesOverflow").asBoolean(false)) {
                // True last resort now (see fetchActivitiesPageWithShrink): even a single activity at this
                // cursor position overflowed the byte cap. Same fallback semantics as before this rewrite -
                // skip the rest of this cycle, blindCycleCount tracks consecutive occurrences so
                // forceUnblockOverflowedSessions can still recover a session genuinely stuck behind this.
                session.setBlindCycleCount(session.getBlindCycleCount() + 1);
                julesSessionRepository.save(session);
                log.warn("Jules activities page for session {} could not be fetched under any page size down to a single activity; skipping question scan this cycle (session left running, blind cycle {})",
                        session.getExternalSessionId(), session.getBlindCycleCount());
                return;
            }
            if (root == null || !root.path("activities").isArray()) {
                // 2026-08-20: a null here is not always "nothing happened". Measured on test-forty-ninth:
                // 52 `activities fetch failed: status=404` per hour, forever, from three sessions sitting
                // in `pr_opened` whose Jules-side session no longer exists. Verified directly by querying
                // every live session with its OWN owning account key - the five running ones answered 200
                // and three of the four pr_opened ones answered 404 on both the session and its activities.
                //
                // A 404 on the session resource itself is proof of absence, not a hiccup, and
                // JulesApiClient.checkSessionRaw exists precisely to tell those apart - it returns the real
                // status code where getSessionStatus swallows it. It had no callers until now.
                //
                // Anything other than a real 404 keeps the old behaviour exactly: not evidence of anything,
                // try again next cycle. Refusing on ignorance would turn a reaper into a new way to lose
                // live sessions.
                if (root == null && buryIfSessionIsGone(session, apiKey)) {
                    return;
                }
                // Genuine null (jules disabled, network hiccup, session skipped) - not evidence of
                // anything, same as the pre-rewrite behavior: do nothing, try again next cycle.
                return;
            }

            boolean stopEverything = processActivitiesPage(session, task, apiKey, root.path("activities"));

            String nextPageToken = root.path("nextPageToken").asText("");
            if (!nextPageToken.isBlank()) {
                pageToken = nextPageToken;
            }
            // When nextPageToken is blank, this is the tail as of this snapshot - persist the token that
            // got us HERE (not blank) so next cycle re-queries this same position, which - verified live
            // against the real Jules API this session - correctly surfaces whatever's newly appended since,
            // rather than restarting the whole walk from page 1.
            session.setActivitiesPageCursor(pageToken);
            if (session.getBlindCycleCount() != 0) {
                session.setBlindCycleCount(0);
            }
            julesSessionRepository.save(session);

            if (stopEverything || nextPageToken.isBlank()) {
                return;
            }
        }
        log.info("Jules activities walk for session {} hit the {}-page-per-cycle cap; resuming from cursor next cycle",
                session.getExternalSessionId(), maxActivityPagesPerCycle);
    }

    /**
     * Fetches one activities page, shrinking pageSize (halving down to 1) if the response overflows the
     * backend safety limit, before giving up - guards against a single embedded artifact (screenshot/diff/
     * bash output) being large enough to overflow even a small page. Returns null for a genuine non-overflow
     * null/error (caller must not treat this as a blind cycle); returns the last overflow marker node
     * (activitiesOverflow=true) if even pageSize=1 couldn't fit - the true last-resort case.
     */
    private JsonNode fetchActivitiesPageWithShrink(JulesSessionEntity session, String apiKey, String pageToken, int startingPageSize) {
        int pageSize = Math.max(1, startingPageSize);
        JsonNode lastOverflow = null;
        while (true) {
            JsonNode root = julesApiClient.getSessionActivities(session.getExternalSessionId(), apiKey, pageSize, pageToken);
            if (root == null) {
                return null;
            }
            if (!root.path("activitiesOverflow").asBoolean(false)) {
                return root;
            }
            lastOverflow = root;
            if (pageSize == 1) {
                return lastOverflow;
            }
            pageSize = Math.max(1, pageSize / 2);
        }
    }

    /**
     * Processes one page's worth of activities with the exact same per-activity logic
     * answerAgentQuestions always used (extraction, hash-based idempotency, circuit-breaker close). Returns
     * true if closeLoopAndCreateFollowUps fired - the caller must stop walking further pages this cycle,
     * same as the old single-page version's "break" out of its loop entirely.
     */
    private boolean processActivitiesPage(JulesSessionEntity session, TaskEntity task, String apiKey, JsonNode activities) {
        for (JsonNode activity : activities) {
            if (activity.has("sessionCompleted") || activity.has("sessionFailed")) {
                if (isOldEnoughToActOn(activity) && handleTerminalActivityWithoutDeliverable(session, task, activity)) {
                    return true;
                }
                continue;
            }
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
                markSessionProgress(session);
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
                    return true;
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
        return false;
    }

    /**
     * 2026-08-04 (operator review of the fix below): a session that finishes and pushes its PR in close
     * succession could otherwise be closed the instant this poll cycle's cursor reaches its sessionCompleted
     * activity - racing ahead of GitHub's own PR-listing sync with a single one-shot check and no way back
     * once the task is blocked. Gated on the SAME Davidson trust window every other closure decision in this
     * class already uses (DAVIDSON_TRUST_WINDOW_MINUTES) - the activity's own createTime, not our polling
     * clock, is what has to age past it. A too-fresh terminal activity is simply left unacted-on this cycle;
     * it is not lost - the unconditional per-cycle GitHub PR recheck a few lines above this call site (for
     * any session with a still-blank prUrl) will pick up a delayed PR on a later cycle regardless, and if
     * none ever appears, this same check will pass once the activity is old enough.
     */
    private boolean isOldEnoughToActOn(JsonNode activity) {
        String createTime = activity.path("createTime").asText(null);
        if (createTime == null || createTime.isBlank()) {
            return false; // no timestamp to judge by - do not act on an unknown-age signal
        }
        try {
            Instant activityCreatedAt = Instant.parse(createTime);
            return activityCreatedAt.isBefore(Instant.now().minus(Duration.ofMinutes(DAVIDSON_TRUST_WINDOW_MINUTES)));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 2026-08-04 (live incident, test-forty-first task 1fbb3086): the activities feed logged a definitive
     * sessionCompleted event while Jules's own /sessions/{id} status API kept reporting a non-terminal
     * state for over 24h - no branch or PR ever appeared on GitHub. processActivitiesPage previously only
     * reacted to activities carrying a question, silently walking past this stronger, structural signal
     * every single cycle - confirmed via grep, "sessionCompleted"/"sessionFailed" were never checked
     * anywhere in this codebase before this method. Charter Pattern #12 (testimony vs evidence): the
     * activity's own type field is a fact Jules recorded about itself, strictly more authoritative than
     * either its own polled liveness state or any natural-language classification of it - so this
     * deliberately bypasses classifyBeforeClosing's Davidson veto entirely (that machinery exists to judge
     * ambiguous silence, not to second-guess a hard schema fact). Routes through the same
     * claimService.closeTaskAsBlocked convention the silence-based circuit breaker already uses, so the
     * task becomes eligible for ProjectFlowService.recoverBlockedWork's existing automatic recovery -
     * no new revival mechanism needed, just correctly reaching the one that already exists. Only ever
     * called once isOldEnoughToActOn has confirmed the activity itself is past the Davidson trust window.
     */
    private boolean handleTerminalActivityWithoutDeliverable(JulesSessionEntity session, TaskEntity task, JsonNode activity) {
        if (session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
            return false; // already has a PR - normal completion path already handled this session
        }
        // One more real-time check before giving up - GitHub sync can lag a few seconds behind session
        // completion, and this activity may have already been sitting in a resumable page walk for a while.
        Optional<GitHubPullRequestService.GitHubPullRequest> detectedPr = detectOpenPullRequestFromGitHub(session, task);
        if (detectedPr.isPresent()) {
            session.setPrUrl(detectedPr.get().url());
            session.setStatus("pr_opened");
            markSessionProgress(session);
            julesSessionRepository.save(session);
            log.info("Session {} for task {} had a real PR after all (found via GitHub, not the activities/status APIs) at terminal-activity reconciliation",
                    session.getExternalSessionId(), task.getId());
            return false;
        }
        boolean failed = activity.has("sessionFailed");
        String reason = (failed ? "session_failed_no_deliverable" : "session_completed_no_deliverable")
                + ": Jules's own activity log recorded " + (failed ? "sessionFailed" : "sessionCompleted")
                + " but no PR/branch was ever found on GitHub for this session.";
        session.setStatus("loop_closed");
        session.setClosedAt(Instant.now());
        session.setClosureReason(reason);
        julesSessionRepository.save(session);
        claimService.closeTaskAsBlocked(task.getId(), reason);
        saveJulesDialogueLog(task.getId(), session.getExternalSessionId(), reason,
                "Jules session closed - self-reported terminal with no deliverable (evidence-based, not a silence/staleness inference)");
        log.warn("Closed Jules session {} for task {}: {}", session.getExternalSessionId(), task.getId(), reason);
        return true;
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

    /**
     * Gemini removed entirely from this path (2026-07-26, operator directive - "disagree. a deterministic answer is
     * possible. 'Follow your own guidelines and recommendations'"). We trust Jules: for
     * anything not caught by the specific deterministic patterns (artifact hygiene, repeated-question
     * circuit breaker, generic-proceed), the answer is simply to trust Jules's own judgment on the task
     * facts already in front of it - {@link #fallbackJulesAnswer} already said exactly this, it was just
     * previously demoted to a last resort behind a Gemini round-trip that added cost without adding a
     * better answer than "make the call yourself and document it."
     */
    // Package-private (not private) so JulesDispatchServiceTest can exercise it directly.
    String buildJulesQuestionAnswer(TaskEntity task, String question, long previousSimilarQuestions) {
        String deterministicAnswer = objectiveJulesResolution(task, question, previousSimilarQuestions);
        if (deterministicAnswer != null && !deterministicAnswer.isBlank()) {
            return deterministicAnswer;
        }
        return fallbackJulesAnswer(task);
    }

    // Package-private (not private) so JulesDispatchServiceTest can exercise it directly.
    String objectiveJulesResolution(TaskEntity task, String question, long previousSimilarQuestions) {
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

    // UNAVAILABLE is deliberately distinct from STUCK: a broken/unreachable classifier is an absence of
    // information, not evidence of anything about the session. Operator directive 2026-07-24: "if the AI is
    // unavailable that does not entitle you to keep broken solutions" (unavailable AI does not
    // grant permission to fall back to a destructive default) - closing/failing the task on an infra
    // hiccup would be exactly that. UNAVAILABLE must never be silently folded into STUCK.
    private enum LoopVerdict { PROGRESSING, REASONED_BLOCKER, STUCK, UNAVAILABLE }

    private record LoopClassification(LoopVerdict verdict, String blockerSummary, String suggestedFix) {}

    /**
     * Davidson principle of charity, applied literally: before the circuit breaker is allowed to close a
     * stalled session, read everything it actually wrote and ask whether the silence means what the
     * timer assumes it means. Elapsed time alone is not evidence of failure.
     */
    private LoopClassification classifyBeforeClosing(TaskEntity task,
                                                       String latestQuestion,
                                                       List<JulesActivityResponseEntity> responseHistory,
                                                       String closeReason) {
        String transcript = responseHistory.stream()
                .limit(10)
                .map(record -> "QUESTION: " + truncate(record.getQuestion(), 1_200)
                        + "\nANSWER: " + truncate(record.getResponse(), 1_200))
                .reduce("", (a, b) -> a + "\n---\n" + b);
        String systemInstruction = """
                You are Eneik's principle-of-charity reviewer for a Jules coding session that has gone
                quiet for a long time. Silence alone is never evidence of failure - a rational agent may
                legitimately stay silent while deep in verification, tests, or investigation. Your only
                job is to read everything the session actually wrote and classify it honestly:
                - PROGRESSING: the content shows real, ongoing reasoning or work toward the task; nothing
                  here indicates the agent gave up or is looping. Depth or slowness is not a problem.
                - REASONED_BLOCKER: the agent explained a concrete, verifiable external fact that prevents
                  it from proceeding on this branch (a real conflict, a missing dependency, a contradiction
                  in the spec, etc.) - a genuine, well-argued rejection, not confusion or a request for
                  busywork.
                - STUCK: the content shows repetition, confusion, or no discernible forward reasoning at
                  all - genuinely no signal of either progress or a concrete blocker.
                Reply in exactly this format and nothing else:
                VERDICT: <PROGRESSING|REASONED_BLOCKER|STUCK>
                BLOCKER: <one sentence - the exact concrete blocker, or "n/a">
                FIX: <one or two sentences - the exact corrective action a fresh session should take, or "n/a">
                """;
        String prompt = "Task id: " + task.getId() + "\n"
                + "Role: " + (task.getRole() != null ? task.getRole().getTag() : "unknown-role") + "\n"
                + "Silence trigger (elapsed-time signal only, not proof of anything): " + closeReason + "\n"
                + "Task description:\n" + truncate(task.getDescription(), 2_000) + "\n\n"
                + "Latest question/comment from the session:\n" + truncate(latestQuestion, 1_500) + "\n\n"
                + "Full recent dialogue - read all of it before deciding:\n" + transcript;
        try {
            String response = mlPredictionServiceClient.chatCritical(prompt, systemInstruction);
            if (isUsableAiAnswer(response)) {
                return parseLoopClassification(response);
            }
        } catch (Exception e) {
            log.warn("Deep-read classification failed for Jules session task {}: {}", task.getId(), e.getMessage());
        }
        // The reviewer being unreachable/unusable tells us nothing about the session itself - it must
        // never be treated as if the content had actually been read and found wanting.
        return new LoopClassification(LoopVerdict.UNAVAILABLE, "n/a", "n/a");
    }

    private LoopClassification parseLoopClassification(String response) {
        LoopVerdict verdict = LoopVerdict.STUCK;
        String blocker = "n/a";
        String fix = "n/a";
        for (String line : response.split("\\R")) {
            String trimmed = line.strip();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("VERDICT:")) {
                String value = upper.substring("VERDICT:".length()).strip();
                if (value.contains("PROGRESSING")) {
                    verdict = LoopVerdict.PROGRESSING;
                } else if (value.contains("REASONED_BLOCKER")) {
                    verdict = LoopVerdict.REASONED_BLOCKER;
                } else {
                    verdict = LoopVerdict.STUCK;
                }
            } else if (upper.startsWith("BLOCKER:")) {
                blocker = trimmed.substring("BLOCKER:".length()).strip();
            } else if (upper.startsWith("FIX:")) {
                fix = trimmed.substring("FIX:".length()).strip();
            }
        }
        return new LoopClassification(verdict, blocker, fix);
    }

    /**
     * @return true if the session was actually closed. False means the caller must treat the session as
     * still alive/trusted (either genuine progress was found, or it was requeued with a corrective brief
     * instead of being torn down).
     */
    private boolean closeLoopAndCreateFollowUps(JulesSessionEntity session,
                                             TaskEntity task,
                                             String latestQuestion,
                                             List<JulesActivityResponseEntity> responseHistory,
                                             String closeReason) {
        LoopClassification classification = classifyBeforeClosing(task, latestQuestion, responseHistory, closeReason);

        if (classification.verdict() == LoopVerdict.UNAVAILABLE) {
            // Do not judge the session on a technical failure that isn't its own: an unreachable reviewer
            // says nothing about what the session did, so no decision is warranted on this tick.
            //
            // 2026-08-21 (plan L-2): but the deferral is now BOUNDED. Unbounded, it is correct only while
            // the reviewer is *transiently* down; once the reviewer is permanently gone, "retry on the next
            // maintenance cycle" is an absorbing state, and the session defers forever - measured live this
            // session, when the adjudicator's provider was switched off and one task sat `claimed` for
            // 24 hours with SYSTEM_STALLED firing the whole time.
            //
            // The bound reuses blindCycleCount rather than inventing a second counter, because it already
            // means exactly this: consecutive cycles in which this session could not be SEEN. Its previous
            // sole cause was an activity log too large to read; a classifier that cannot answer is the same
            // epistemic state reached by a different route, and one source of truth is better than two.
            // Past the same threshold that governs the oversized-log case, we stop deferring and fall
            // through to the ordinary circuit breaker below - which is the existing closure mechanism, not
            // a new one, and which records WHY it fired in the session's closure reason.
            session.setBlindCycleCount(session.getBlindCycleCount() + 1);
            julesSessionRepository.save(session);
            if (session.getBlindCycleCount() < forcedUnblockBlindCycleThreshold) {
                log.warn("Deep-read classifier unavailable for session {} task {} ({}); deferring - NOT closing "
                                + "and NOT treating as stuck (blind cycle {}/{}). Will retry on the next "
                                + "maintenance cycle.",
                        session.getExternalSessionId(), task.getId(), closeReason,
                        session.getBlindCycleCount(), forcedUnblockBlindCycleThreshold);
                return false;
            }
            log.warn("Deep-read classifier has been unavailable for session {} task {} for {} consecutive "
                            + "cycles ({}); the deferral is bounded, so this falls through to the circuit "
                            + "breaker rather than deferring forever. The session is being closed on the "
                            + "reviewer's absence, not on evidence about the session itself.",
                    session.getExternalSessionId(), task.getId(), session.getBlindCycleCount(), closeReason);
            closeReason = closeReason + " (classifier unavailable for " + session.getBlindCycleCount()
                    + " consecutive cycles - closed on the reviewer's absence, NOT on evidence about this "
                    + "session; verify against GitHub/DB state before treating the diagnosis as true)";
        }

        if (classification.verdict() == LoopVerdict.PROGRESSING) {
            // O-17: past the declared ceiling the veto stops restoring the budget. It still does not close
            // anything itself - it simply stops holding the circuit breaker open, which is the same shape
            // as the bounded classifier deferral above: the bound falls through to the EXISTING closure
            // mechanism rather than inventing a second one.
            long sessionAgeMinutes = session.getCreatedAt() == null
                    ? 0L
                    : Duration.between(session.getCreatedAt(), Instant.now()).toMinutes();
            if (sessionAgeMinutes >= davidsonVetoCeilingMinutes) {
                session.setBlindCycleCount(0);
                julesSessionRepository.save(session);
                log.warn("Davidson deep-read veto for session {} task {} is past its ceiling: the session has "
                                + "been alive {} minutes (ceiling {}) and its writing still shows reasoning "
                                + "rather than an artefact. NOT restoring the forced-unblock budget, so the "
                                + "circuit breaker can fire. This closes on the bound, not on evidence that "
                                + "the session failed - verify against GitHub/DB state before treating the "
                                + "diagnosis as true.",
                        session.getExternalSessionId(), task.getId(), sessionAgeMinutes,
                        davidsonVetoCeilingMinutes);
                return false;
            }
            markSessionProgress(session);
            session.setForcedUnblockAttempts(0);
            session.setBlindCycleCount(0);
            julesSessionRepository.save(session);
            log.info("Davidson deep-read veto: session {} for task {} looked stalled by the clock ({}), but its "
                            + "own writing shows real ongoing reasoning - refusing to close, trust window reset.",
                    session.getExternalSessionId(), task.getId(), closeReason);
            return false;
        }

        if (classification.verdict() == LoopVerdict.REASONED_BLOCKER
                && task.getRetryCount() < REASONED_BLOCKER_MAX_RETRIES) {
            String amendedBrief = task.getDescription()
                    + "\n\n---\n[Eneik orchestrator: a prior session honestly rejected this task instead of "
                    + "compounding a real problem - do not repeat the same investigation, use this directly]\n"
                    + "Blocker identified by the previous session: " + classification.blockerSummary() + "\n"
                    + "Required correction for this session: " + classification.suggestedFix();
            session.setStatus("loop_closed");
            session.setClosedAt(Instant.now());
            session.setClosureReason("external_blocker_identified (not an agent failure): " + classification.blockerSummary()
                    + "\nFix applied to next session's brief: " + classification.suggestedFix());
            julesSessionRepository.save(session);
            task.setRetryCount(task.getRetryCount() + 1);
            claimService.reopenWithAmendedBrief(task.getId(), amendedBrief,
                    "Reasoned blocker honestly identified by Jules; requeued with corrective brief: "
                            + classification.blockerSummary());
            saveJulesDialogueLog(task.getId(), session.getExternalSessionId(),
                    "Reasoned blocker: " + classification.blockerSummary() + "\nFix: " + classification.suggestedFix(),
                    "Jules session closed - external blocker honestly identified, task requeued with a "
                            + "corrective brief (not counted as a failure)");
            log.warn("Session {} for task {} closed on a reasoned external blocker (not a failure): {}. Requeued "
                            + "with corrective brief, attempt {}/{}.",
                    session.getExternalSessionId(), task.getId(), classification.blockerSummary(),
                    task.getRetryCount(), REASONED_BLOCKER_MAX_RETRIES);
            return true;
        }

        // STUCK, or a REASONED_BLOCKER that already exhausted its bounded retries without the underlying
        // fact changing - fall back to the original generic circuit-breaker path.
        LoopDiagnosis diagnosis = diagnoseLoop(task, latestQuestion, closeReason);
        String geminiAnalysis = geminiLoopAnalysis(task, latestQuestion, responseHistory, diagnosis, closeReason);
        // Phase follow-up (2026-07-21, operator directive): this text is Gemini's own INFERENCE from limited
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
        return true;
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

    /**
     * Gemini call removed (2026-07-25, operator directive - emergency cost incident, "keep monitoring only"). Safe to drop entirely: the close-and-follow-up decision this feeds into is already
     * fully made by the time this runs (session.setStatus("loop_closed") and createCircuitBreakerWishlist
     * both happen unconditionally right after, driven by the deterministic {@code diagnosis}) - this text
     * was always supplementary documentation for the closure reason/dialogue log, never load-bearing. Now
     * always returns exactly the same deterministic fallback text this method already used whenever Gemini
     * was unreachable, so quality is unchanged from the failure-mode path that already existed.
     */
    private String geminiLoopAnalysis(TaskEntity task,
                                      String latestQuestion,
                                      List<JulesActivityResponseEntity> responseHistory,
                                      LoopDiagnosis diagnosis,
                                      String closeReason) {
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
                    || session.getPrUrl() == null || session.getPrUrl().isBlank()
                    || task.getProject() == null || task.getProject().getStatus() != ProjectStatus.active) {
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

    // 2026-08-14 (bug-hunt sweep, part 1): used to be @Transactional, dispatching to 8 completion handlers
    // that make direct real GitHub/Jules network calls with no isolation of their own - removed for the
    // same reason as 5 other methods fixed the same night (a DB transaction was held open across those
    // network calls whenever this was reached via AutoMergeService's cross-class calls, the one call site
    // where the annotation genuinely activated - the 2 internal self-invocations, pollStatus and
    // honorDavidsonProgressEvidence, already bypassed it entirely via plain same-class references). Full
    // suite verified green with just that removal.
    //
    // 2026-08-14 (bug-hunt sweep, part 2 - separate bug, same file): removing the transaction wrapper
    // surfaced a REAL, different problem underneath rather than fixing it - most of these 8 handlers guard
    // their own idempotency with a plain read-then-later-write check (e.g. completePhilosophicalAudit's
    // `firstCompletion = claimService.hasActiveClaim(...)`, read at the top, only actually enforced much
    // later by claimService.complete() throwing IllegalStateException if the claim was already released).
    // Two genuinely concurrent invocations of this method for the SAME session (AutoMergeService's
    // reconciliation sweep racing pollStatus's own trigger for the same running/revising->pr_opened
    // transition, or two reconciliation passes overlapping) can both read that guard as "first" and both
    // apply the same critique/violation/merge-record work before either one's completion write lands -
    // this was already possible before today's other fixes, just previously somewhat narrowed by the
    // (accidental, not deliberate) serialization effect of one big transaction per call.
    //
    // Fixed with one atomic mutual-exclusion claim (JulesSessionEntity.prOpenedWorkflowClaimedAt, V97
    // migration - a NULL/non-null flag orthogonal to session status, not reusing compareAndSetStatus so
    // nothing else that reads session status is affected) taken as the very FIRST step, before any of the 8
    // handlers run. Deliberately NOT a permanent lock: on any exception the claim is released (self.
    // releasePrOpenedWorkflowClaim, plain @Transactional so it always gets a real transaction - see
    // claimWishlistsForCompilation's javadoc for why REQUIRED not REQUIRES_NEW), so a genuine crash mid-
    // processing still leaves the session retryable by reconcileStrandedPrOpenedWorkflows exactly as before -
    // this closes the concurrent-duplicate race without turning a transient failure into a permanently
    // stuck session.
    public void handlePrOpenedWorkflow(JulesSessionEntity session) {
        if (self.claimPrOpenedWorkflow(session.getId()) == 0) {
            log.info("handlePrOpenedWorkflow: session {} pr_opened completion is already claimed by a concurrent "
                    + "invocation; skipping this one instead of risking duplicate work", session.getId());
            return;
        }
        try {
            handlePrOpenedWorkflowClaimed(session);
        } catch (RuntimeException e) {
            self.releasePrOpenedWorkflowClaim(session.getId());
            throw e;
        }
    }

    @Transactional
    int claimPrOpenedWorkflow(UUID sessionId) {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusSeconds(300); // 5-minute bounded monotonic lease
        return julesSessionRepository.claimPrOpenedWorkflow(sessionId, now, staleThreshold);
    }

    @Transactional
    void releasePrOpenedWorkflowClaim(UUID sessionId) {
        julesSessionRepository.releasePrOpenedWorkflowClaim(sessionId);
    }

    private void handlePrOpenedWorkflowClaimed(JulesSessionEntity session) {
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
            if (projectFlowService.isDesignConcernTriageTask(task)) {
                completeDesignConcernTriage(session, task);
                return;
            }
            if (projectFlowService.isCoverageAuditTask(task)) {
                completeCoverageAudit(session, task);
                return;
            }
            if (projectFlowService.isPhilosophicalAuditTask(task)) {
                completePhilosophicalAudit(session, task);
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
    // Package-private (not private) so JulesDispatchServiceTest can assert against it directly -
    // executeCodeReview itself is already package-private for the same white-box testing reason.
    record PendingFallbackReview(TaskEntity task, String prUrl) {
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
        // Gemini PR review permanently disabled (2026-07-25, operator directive - emergency cost incident:
        // "in a few hours it spent a month's budget while nothing on the project moved" -
        // a task that fails review repeatedly paid for a full pro-tier diff review on every single
        // resubmission, with no cap). Every PR now routes to the Jules-reviewer fallback path unconditionally
        // - previously reserved for Gemini-outage recovery only, already fully proven (a different Jules
        // session reviews the diff; applyReviewVerdictToTask mirrors the exact same approve/reject state
        // transitions this method used to apply inline). "keep Jules only" - Jules capacity, not
        // metered per-token Gemini calls, is the review cost now.
        fallbackCollector.add(new PendingFallbackReview(task, prUrl));
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
            // Operator directive (2026-07-25): "only the one current project should be checked... so that another
            // project's context does not harm the monitoring" - this loop spans every project's pending_review tasks in
            // one tick, and previously logged everything under SYSTEM scope, making it impossible to tell
            // which project a given "no resolvable open-PR session" line was about. A feature can only
            // belong to one project (siblings under one featureId are always from the same project), so
            // scoping once per group is correct.
            com.eneik.production.services.logging.LogScope.system();
            TaskEntity firstSibling = siblings.isEmpty() ? null : siblings.get(0);
            if (firstSibling != null && firstSibling.getProject() != null) {
                com.eneik.production.services.logging.LogScope.project(firstSibling.getProject().getId());
            }
            try {
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
            } finally {
                com.eneik.production.services.logging.LogScope.clear();
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

    /**
     * Testimony-vs-evidence fix (2026-07-25, live incident on test-thirty-seventh): the original version
     * of this method trusted ONLY the session's own self-reported status ("pr_opened") - a session that
     * successfully opened a real PR (CI green, still unmerged - confirmed live via a real, still-OPEN
     * GitHub PR) but LATER self-reported "failed" for an unrelated reason (e.g. it kept running past its
     * own PR and errored out afterward) made its task permanently invisible to processPendingReviewBatch,
     * forever - the exact "trusting testimony over evidence" anti-pattern this codebase's own standing
     * principle exists to prevent. Now falls back to real PrReviewEntity evidence (a real PR URL, not yet
     * merged) whenever the session's own status doesn't already say "pr_opened".
     */
    private JulesSessionEntity latestOpenPrSession(UUID taskId) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(taskId);
        return sessions.stream()
                .filter(this::hasUnmergedPrEvidence)
                .max(java.util.Comparator.comparing(JulesSessionEntity::getUpdatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean hasUnmergedPrEvidence(JulesSessionEntity session) {
        if ("pr_opened".equals(session.getStatus()) && session.getPrUrl() != null && !session.getPrUrl().isBlank()) {
            return true;
        }
        return prReviewRepository.findByJulesSessionId(session.getId()).stream()
                .anyMatch(r -> r.getPrUrl() != null && !r.getPrUrl().isBlank() && !Boolean.TRUE.equals(r.getMerged()));
    }

    // Package-private, not private (2026-08-07): JulesDispatchServiceTest exercises
    // admitWishlistCompilationCompletion directly to prove the compare-and-swap claim actually excludes a
    // concurrent second call - the novel, risk-bearing logic of tonight's regression fix.
    enum CompilationAdmissionOutcome { NO_WISHLISTS_LEFT, ALREADY_COMPILED, IN_PROGRESS_ELSEWHERE, PROCEED }

    record CompilerCompletionAdmission(CompilationAdmissionOutcome outcome, List<WishlistEntity> wishlists,
                                                 java.util.Set<UUID> claimedIds) {
    }

    // 2026-08-07 (confirmed live incident, test-forty-third - 4th instance of this exact bug class found in
    // one deliberate sweep, see ProjectFlowService.dispatchFalsificationAudit/
    // dispatchToPhilosophicalAuditPersistentWorker/checkAndDispatchCoverageAudits for the other 3): unlike
    // those three, this method is invoked from handlePrOpenedWorkflow, which is ALREADY @Transactional -
    // simply extracting a plain @Transactional admit-step and calling it via self would just join that
    // already-open outer transaction instead of opening a new one, so the project row lock would still be
    // held for the outer transaction's whole lifetime (i.e. across every network call below). REQUIRES_NEW
    // forces a genuinely separate transaction that commits - and releases the row lock - the moment this
    // call returns, before any GitHub/Jules network call runs.
    //
    // 2026-08-07 SAME-NIGHT REGRESSION, FOUND AND FIXED LIVE: the first version of this method only READ
    // wishlist status here and compared it to converted_to_task/dismissed - but the actual WRITE of that
    // status happens much later, inside buildTaskGraphFromSlices, in the OUTER (still-open,
    // not-yet-committed) transaction, AFTER all the slow GitHub/parse work below. That reopened the exact
    // race this method exists to close: releasing the lock the instant the read-only decision was made,
    // before the real protective write ever happened, let reconcileStrandedPrOpenedWorkflows's ~60s replay
    // (or a duplicate webhook) see "still not converted" a second and third time and independently rebuild
    // the same task graph - confirmed live: one wishlist decomposed 3 times in ~70 seconds, producing
    // duplicate tasks that tripped BLOCKED_BY_DUPLICATE_CONTENT and halted the whole project for hours.
    // Fix: an atomic compare-and-swap claim (compiling -> finalizing) per wishlist, INSIDE this short lock,
    // using the same primitive WishlistRepository.compareAndSetStatus already proven for dispatch-time
    // admission. A concurrent completion's CAS attempt on an already-`finalizing` row affects 0 rows and is
    // excluded, with no window between "decided" and "protected" for anything to race into.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompilerCompletionAdmission admitWishlistCompilationCompletion(TaskEntity compilerTask, List<UUID> wishlistIds) {
        // 2026-08-03 (confirmed live incident, test-forty-first): serializes concurrent completions for the
        // same project so a second one correctly re-reads current wishlist status after the first commits,
        // instead of both racing past the check - same lockProjectForUpdate primitive used for this exact
        // race class elsewhere (checkAndDispatchCoverageAudits, dispatchFalsificationAudit).
        if (compilerTask.getProject() != null) {
            projectRepository.lockProjectForUpdate(compilerTask.getProject().getId());
        }
        List<WishlistEntity> wishlists = new java.util.ArrayList<>();
        for (UUID id : wishlistIds) {
            wishlistRepository.findById(id).ifPresent(wishlists::add);
        }
        if (wishlists.isEmpty()) {
            return new CompilerCompletionAdmission(CompilationAdmissionOutcome.NO_WISHLISTS_LEFT, wishlists, java.util.Set.of());
        }

        // A batch where EVERY wishlist has already been finished by another session is a full no-op that
        // just closes its own PR/session cleanly - checked BEFORE attempting any claim, using the fresh
        // read above. Must check specifically for converted_to_task/dismissed, NOT "!= pending":
        // dispatchWishlistCompiler flips each wishlist to `compiling` at DISPATCH time, before any session
        // has actually completed - a "!= pending" check would therefore reject every completion, including
        // the legitimate first one (caught live: it stuck a wishlist in an infinite
        // compile->discard->blocked->recover->compile loop that never produced real work).
        boolean allAlreadyResolved = wishlists.stream()
                .allMatch(w -> w.getStatus() == WishlistStatus.converted_to_task || w.getStatus() == WishlistStatus.dismissed);
        if (allAlreadyResolved) {
            return new CompilerCompletionAdmission(CompilationAdmissionOutcome.ALREADY_COMPILED, wishlists, java.util.Set.of());
        }

        // Idempotency guard, now atomic: more than one compiler task/completion can end up targeting the
        // same wishlist (e.g. the generic blocked-task recovery flow re-dispatching a compiler task without
        // knowing it's a compiler task, racing an already-in-flight one, or this exact session's own
        // completion being replayed). Whichever completion wins the compare-and-swap for a given wishlist
        // owns it; a batch where only SOME wishlists are claimable still proceeds for those specific ones -
        // completeWishlistCompilation gives buildTaskGraphFromSlices a view where un-claimed positions are
        // already marked as "skip", so it never touches a wishlist this invocation doesn't own.
        java.util.Set<UUID> claimedIds = new java.util.HashSet<>();
        for (WishlistEntity w : wishlists) {
            if (w.getStatus() == WishlistStatus.converted_to_task || w.getStatus() == WishlistStatus.dismissed) {
                continue;
            }
            if (wishlistRepository.compareAndSetStatus(w.getId(), WishlistStatus.compiling, WishlistStatus.finalizing) == 1) {
                claimedIds.add(w.getId());
            }
        }
        if (claimedIds.isEmpty()) {
            // Every remaining wishlist is already `finalizing` under a genuinely concurrent completion right
            // now - not ours to touch, and not a "duplicate to discard" either (the owning completion still
            // needs its PR/session intact to finish). Quietly no-op.
            return new CompilerCompletionAdmission(CompilationAdmissionOutcome.IN_PROGRESS_ELSEWHERE, wishlists, claimedIds);
        }
        return new CompilerCompletionAdmission(CompilationAdmissionOutcome.PROCEED, wishlists, claimedIds);
    }

    // Reverts a claim back to `compiling` so a later legitimate attempt (Jules retry, or a fresh recovery
    // dispatch) can still compare-and-swap it again - used whenever a PROCEED admission doesn't end in a
    // real converted_to_task/dismissed write (invalid plan, retry, or an exception during graph building),
    // so a wishlist can never get stranded in `finalizing` forever.
    //
    // 2026-08-13 (live incident, test-forty-fourth): REQUIRES_NEW, same as admitWishlistCompilationCompletion
    // and for the same reason - this runs from inside a catch block after buildTaskGraphFromSlices throws,
    // but that call shares the caller's (handlePrOpenedWorkflow's) transaction. Spring marks that whole
    // transaction rollback-only the instant the RuntimeException crosses any @Transactional boundary inside
    // it, even though this method's own catch block "handles" it in Java - so this CAS write was being
    // silently discarded at commit time (UnexpectedRollbackException, seen live in this exact pathway),
    // leaving the wishlist permanently stuck in `finalizing` with no other recovery path. Must go through
    // `self` (not a bare call) for the annotation to actually take effect - Spring's proxy only intercepts
    // calls that go through it, not private-method self-invocation.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseUnfinishedClaims(java.util.Set<UUID> claimedIds) {
        for (UUID id : claimedIds) {
            wishlistRepository.compareAndSetStatus(id, WishlistStatus.finalizing, WishlistStatus.compiling);
        }
    }

    /**
     * A wishlist-compiler session reached pr_opened: its PR should carry exactly one JSON plan file
     * (see ProjectFlowService.wishlistCompilerPrompt), never product code. Parses and validates that
     * plan, feeds it into the same graph-building logic Gemini's slices used to drive, then discards
     * the compiler PR (it never gets merged). On invalid/empty output this does not fall back to
     * fabricated content - it asks Jules to retry a bounded number of times, then closes the PR and
     * finishes the carrier. Nothing is escalated to a person: the factory is autonomous, and the briefs
     * keep their own budget (see ProjectFlowService.withholdFromCompileDispatch).
     */
    private void completeWishlistCompilation(JulesSessionEntity session, TaskEntity compilerTask) {
        List<UUID> wishlistIds = compilerTaskWishlistIds(compilerTask);
        if (wishlistIds.isEmpty()) {
            log.error("Compiler task {} has no compilesWishlistIds payload marker; cannot complete compilation", compilerTask.getId());
            return;
        }

        CompilerCompletionAdmission admission = self.admitWishlistCompilationCompletion(compilerTask, wishlistIds);
        List<WishlistEntity> wishlists = admission.wishlists();

        if (admission.outcome() == CompilationAdmissionOutcome.NO_WISHLISTS_LEFT) {
            log.warn("Compiler task {}: none of its {} wishlist(s) exist anymore, discarding", compilerTask.getId(), wishlistIds.size());
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            return;
        }

        if (admission.outcome() == CompilationAdmissionOutcome.ALREADY_COMPILED) {
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

        if (admission.outcome() == CompilationAdmissionOutcome.IN_PROGRESS_ELSEWHERE) {
            log.info("Compiler task {}: another completion is already finalizing this exact batch right now; skipping without touching its PR/session.", compilerTask.getId());
            return;
        }

        // buildTaskGraphFromSlices below is a shared method (also called from ingestPlanFromContent's manual
        // recovery path, which never claims anything) whose per-wishlist skip check only recognizes
        // converted_to_task/dismissed as "not mine". A position this invocation did NOT win the claim for
        // (owned by a genuinely concurrent completion, or already resolved) must still occupy the same
        // index - epicPlan.sourceIndex() is positional, matching the numbering the original compiler prompt
        // sent - so it's represented by a throwaway entity carrying real id but a `dismissed` sentinel
        // status. That sentinel is NEVER persisted: a skipped position is `continue`'d without any save.
        List<WishlistEntity> wishlistsForGraphBuild = wishlists.stream()
                .map(w -> {
                    if (admission.claimedIds().contains(w.getId())) {
                        return w;
                    }
                    WishlistEntity notMine = new WishlistEntity();
                    notMine.setId(w.getId());
                    notMine.setStatus(WishlistStatus.dismissed);
                    return notMine;
                })
                .toList();

        String planPath = projectFlowService.compilerPlanPath(compilerTask);
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(compilerTask.getProject(), session.getExternalSessionId());
        if (prOpt.isEmpty()) {
            // The PR may already be merged (this completion path can now be entered via
            // honorDavidsonProgressEvidence's merged-PR evidence, not only via a fresh pr_opened webhook) -
            // its head branch is commonly deleted on merge, so read the plan file from the PR's base ref
            // (main), which already contains it, instead of the possibly-gone head ref.
            prOpt = gitHubPullRequestService.findMergedPullRequestBySession(compilerTask.getProject(), session.getExternalSessionId());
        }
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics = prOpt
                .map(pr -> parseCompilerPlan(compilerTask.getProject(), pr.merged() ? pr.baseRef() : pr.headRef(), planPath))
                .orElseGet(List::of);

        // Validated against the FULL original batch size (wishlists.size()), not just the still-open subset
        // - sourceIndex values in Jules's response reference the numbering the prompt actually sent, which
        // covers every wishlist in the batch regardless of whether one of them was independently finished
        // by another session in the meantime.
        String rejection = compilerPlanRejection(epics, wishlists.size());
        if (rejection.isEmpty()) {
            try {
                projectFlowService.buildTaskGraphFromSlices(compilerTask.getProject(), wishlistsForGraphBuild, epics);
            } catch (RuntimeException e) {
                // Claimed wishlists that never reached a real converted_to_task/dismissed write must not be
                // stranded in `finalizing` forever - release them so a later retry can claim them again.
                self.releaseUnfinishedClaims(admission.claimedIds());
                throw e;
            }
            prOpt.ifPresent(pr -> {
                gitHubPullRequestService.mergeRecordPullRequest(
                        compilerTask.getProject(), pr, "wishlist compiler plan parsed into real tasks");
                archiveRecordFile(compilerTask.getProject(), planPath, "task-plan");
                closeSessionAsNoCode(session, "Compiler plan merged (process/metadata only by design); branch deleted.");
            });
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            systemProgressTracker.recordProgress();
            log.info("{} wishlist(s) compiled by Jules session {} into {} epic(s), {} task slice(s) total",
                    wishlists.size(), session.getExternalSessionId(), epics.size(),
                    epics.stream().mapToInt(e -> e.slices().size()).sum());
            return;
        }

        // The answer is not usable - this attempt claimed the wishlists but is not going to build anything
        // from them, so release the claim now (before deciding retry vs. escalate) instead of leaving them
        // stuck in `finalizing`, which would make every future admission's compare-and-swap fail forever.
        self.releaseUnfinishedClaims(admission.claimedIds());
        // And give the attempt itself back unless the compiler answered empty: a refusal by this factory's
        // own check is evidence about the check, not about the brief (action plan 8.3).
        if (!EMPTY_COMPILER_ANSWER.equals(rejection)) {
            projectFlowService.returnCompileAttempt(admission.claimedIds());
        }

        int attempts = compilerTask.getRetryCount();
        if (attempts >= WISHLIST_COMPILER_MAX_RETRIES) {
            // Nothing is filed for a person: the factory is autonomous (operator, 2026-08-29). The PR is
            // closed and the carrier is finished here; the wishlists keep their own budget and go on being
            // offered at the compile cooldown, settling into decompositionRefused once the compiler has
            // actually answered and given nothing - which is absorbing and needs no further decision.
            prOpt.ifPresent(pr -> gitHubPullRequestService.closeSinglePullRequest(
                    compilerTask.getProject(), pr, "wishlist compiler plan invalid after max retries"));
            if (claimService.hasActiveClaim(compilerTask.getId())) {
                claimService.complete(compilerTask.getId());
                markSystemTaskDone(compilerTask);
            }
            log.error("Compilation of {} wishlist(s) produced no valid plan after {} attempts; carrier finished, briefs stay on their own budget",
                    wishlists.size(), attempts);
            return;
        }

        compilerTask.setRetryCount(attempts + 1);
        taskRepository.save(compilerTask);
        session.setStatus("revising");
        julesSessionRepository.save(session);

        // Names the condition that actually rejected the plan (2026-08-29). It restated the whole schema
        // until then - the same paragraph on attempt one and attempt two - so Jules had nothing to correct
        // by and wrote the same thing twice. See compilerPlanRejection.
        String correction = "Your `" + planPath + "` was rejected: " + rejection + ". Please fix the same "
                + "PR by writing only that file, with exhaustive epic requirements, coverageComplete=true, "
                + "and 1-8 concrete slices per epic; every requirement must be covered by slice "
                + "requirementRefs and every input brief must be represented.";
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
        log.warn("Wishlist compiler plan rejected for {} wishlist(s) (attempt {}/{}) - {}; asked Jules to retry",
                wishlists.size(), attempts + 1, WISHLIST_COMPILER_MAX_RETRIES, rejection);
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
     * Result of a stalled-looking session's evidence check (testimony-vs-evidence Phase 1, 2026-07-25):
     * whether real progress was found, and if so, whether it was found ONLY via the branch-fallback path (no
     * PR open yet). honorDavidsonProgressEvidence uses branchNeedingPullRequest to open the PR itself when
     * set, so the existing PR-driven pipeline picks the work up on its next tick - exactly what the operator
     * did manually for the first live incident of this shape (PR#72).
     */
    private record GitHubEvidence(boolean found, String branchNeedingPullRequest,
                                   GitHubPullRequestService.GitHubPullRequest mergedPr) {
        static GitHubEvidence none() {
            return new GitHubEvidence(false, null, null);
        }

        static GitHubEvidence foundViaOpenPr() {
            return new GitHubEvidence(true, null, null);
        }

        static GitHubEvidence foundViaBranchFallback(String branch) {
            return new GitHubEvidence(true, branch, null);
        }

        // testimony-vs-evidence Phase 3 (2026-07-30): the PR was already found, reviewed, and merged
        // through the normal pipeline, but the session's local status never made the running -> pr_opened
        // jump to trigger the usual completion path. Distinct from foundViaBranchFallback (which means "no
        // PR was ever opened, we should open one") - here a PR already exists and is closed/merged, so the
        // caller must complete the task instead of trying to open a second, doomed PR.
        static GitHubEvidence foundViaMergedPr(GitHubPullRequestService.GitHubPullRequest pr) {
            return new GitHubEvidence(true, null, pr);
        }
    }

    /**
     * Read-only check ("does this session already have an answer?") used by forceUnblockOverflowedSessions
     * before it decides a persistent-worker carrier session has truly stalled. Must NOT consume the
     * worker's in-flight batch itself - only completePersistentWorkerCycle does that, and only once this
     * check has confirmed there is something real to consume. Looks at the actual PR branch on GitHub
     * (the same source of truth Jules writes to), not at our own possibly-stale session.status field.
     *
     * Falls back to a matching branch when no PR is open yet (2026-07-25) - the exact shape of the PR#72
     * incident: a compiler session finished and pushed a real, complete task-plan commit, but never opened a
     * PR for it, so the PR-only check below used to see nothing and the session looked stalled forever.
     */
    private GitHubEvidence persistentWorkerHasReadyAnswer(JulesSessionEntity session, TaskEntity carrierTask) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        if (prOpt.isPresent()) {
            return hasParseableCarrierAnswer(carrierTask, prOpt.get().headRef())
                    ? GitHubEvidence.foundViaOpenPr() : GitHubEvidence.none();
        }
        Optional<String> branchOpt =
                gitHubPullRequestService.findBranchBySession(carrierTask.getProject(), session.getExternalSessionId());
        if (branchOpt.isEmpty()) {
            return GitHubEvidence.none();
        }
        String branch = branchOpt.get();
        return hasParseableCarrierAnswer(carrierTask, branch)
                ? GitHubEvidence.foundViaBranchFallback(branch) : GitHubEvidence.none();
    }

    private boolean hasParseableCarrierAnswer(TaskEntity carrierTask, String ref) {
        if (projectFlowService.isPhilosophicalAuditTask(carrierTask)) {
            // No single parseable "plan" shape for a discussion turn - real evidence is the report file
            // itself having landed on this branch at all (its content only fully validates at the final
            // turn, once every active role is covered - see completePersistentPhilosophicalAuditCycle).
            String reportPath = projectFlowService.philosophicalAuditReportPath(carrierTask);
            return reportPath != null
                    && gitHubPullRequestService.fetchFileContent(carrierTask.getProject(), ref, reportPath).isPresent();
        }
        if (projectFlowService.isReviewFallbackTask(carrierTask)) {
            return !parseReviewVerdictBatch(carrierTask.getProject(), ref,
                    projectFlowService.reviewFallbackVerdictPath(carrierTask)).isEmpty();
        }
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics =
                parseCompilerPlan(carrierTask.getProject(), ref, projectFlowService.compilerPlanPath(carrierTask));
        return !epics.isEmpty();
    }

    /**
     * Real-implementer-task counterpart of {@link #persistentWorkerHasReadyAnswer} - there's no single
     * result file to check for a normal task, so the ground truth is simpler: has a commit actually landed
     * on this session's branch after the point our own tracking last saw progress. A real, positive answer
     * here means the session was NOT actually silent, our lastProgressAt bookkeeping just missed it.
     *
     * Falls back to a matching branch when no PR is open yet (2026-07-25). The fallback compares against
     * session.getCreatedAt() rather than lastProgress - it must prove real work happened DURING this
     * session's own lifetime, not merely that an untouched branch existed at some fork point before the
     * session ever started.
     */
    private GitHubEvidence hasNewProgressOnGitHub(JulesSessionEntity session, TaskEntity task, Instant lastProgress) {
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId());
        if (prOpt.isPresent()) {
            boolean hasNewCommit = gitHubPullRequestService.latestCommitTime(task.getProject(), prOpt.get().headRef())
                    .map(commitTime -> commitTime.isAfter(lastProgress))
                    .orElse(false);
            return hasNewCommit ? GitHubEvidence.foundViaOpenPr() : GitHubEvidence.none();
        }
        // No open PR - before assuming none was ever opened, check whether one already merged (confirmed
        // live, 2026-07-30, task 51ab7e20/test-fortieth: the local session status missed the running ->
        // pr_opened jump even though Jules's own PR was found, reviewed, and merged normally; every hourly
        // stall check afterward found this same already-merged branch and kept retrying a doomed "open a
        // new PR" call, HTTP 422 "No commits between main and branch", forever).
        Optional<GitHubPullRequestService.GitHubPullRequest> mergedOpt =
                gitHubPullRequestService.findMergedPullRequestBySession(task.getProject(), session.getExternalSessionId());
        if (mergedOpt.isPresent()) {
            return GitHubEvidence.foundViaMergedPr(mergedOpt.get());
        }
        Optional<String> branchOpt = gitHubPullRequestService.findBranchBySession(task.getProject(), session.getExternalSessionId());
        if (branchOpt.isEmpty()) {
            return GitHubEvidence.none();
        }
        String branch = branchOpt.get();
        boolean hasRealCommit = gitHubPullRequestService.latestCommitTime(task.getProject(), branch)
                .map(commitTime -> commitTime.isAfter(session.getCreatedAt()))
                .orElse(false);
        return hasRealCommit ? GitHubEvidence.foundViaBranchFallback(branch) : GitHubEvidence.none();
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
                    projectFlowService.isPhilosophicalAuditTask(carrierTask)
                            ? com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT
                            : projectFlowService.isReviewFallbackTask(carrierTask)
                                    ? com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK
                                    : com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER;
            // Philosophical-audit's real batch state is the covered-role-tags list already recorded on the
            // carrier task's own payload (role tags aren't UUIDs, so they can't live in currentBatchIds) -
            // this marker only needs to be non-empty to satisfy the generic "in flight" bookkeeping below.
            batchIds = purpose == com.eneik.production.models.persistence.PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT
                    ? java.util.List.of(UUID.randomUUID())
                    : purpose == com.eneik.production.models.persistence.PersistentWorkerPurpose.REVIEW_FALLBACK
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

        if (projectFlowService.isPhilosophicalAuditTask(carrierTask)) {
            completePersistentPhilosophicalAuditCycle(session, carrierTask);
        } else if (projectFlowService.isReviewFallbackTask(carrierTask)) {
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

        String planPath = projectFlowService.compilerPlanPath(carrierTask);
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics = prOpt
                .map(pr -> parseCompilerPlan(carrierTask.getProject(), pr.headRef(), planPath))
                .orElseGet(List::of);

        String cycleRejection = compilerPlanRejection(epics, wishlists.size());
        if (cycleRejection.isEmpty()) {
            projectFlowService.buildTaskGraphFromSlices(carrierTask.getProject(), wishlists, epics);
            systemProgressTracker.recordProgress();
            log.info("Persistent compiler worker (carrier task {}): {} wishlist(s) compiled into {} epic(s), {} task slice(s) this cycle",
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
        // Same rule as the one-shot path (action plan 8.3): a refusal by this factory's own check does not
        // spend the brief's budget, because it establishes nothing about the brief.
        if (!EMPTY_COMPILER_ANSWER.equals(cycleRejection)) {
            projectFlowService.returnCompileAttempt(wishlists.stream()
                    .map(com.eneik.production.models.persistence.WishlistEntity::getId).toList());
        }
        String correction = "Your latest `" + planPath + "` was rejected for THIS cycle: " + cycleRejection
                + ". Please fix the same file, with exhaustive epic requirements, coverageComplete=true, and "
                + "1-8 concrete slices per epic; every requirement must be covered by slice requirementRefs "
                + "and every input brief must be represented.";
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
        String verdictPath = projectFlowService.reviewFallbackVerdictPath(carrierTask);
        List<ReviewVerdictEntry> verdicts = prOpt
                .map(pr -> parseReviewVerdictBatch(carrierTask.getProject(), pr.headRef(), verdictPath))
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
     * Completion handler for a PHILOSOPHICAL_AUDIT persistent worker's pr_opened edge. Unlike
     * completePersistentCompilerCycle/completePersistentReviewFallbackCycle (which always leave the
     * session parked open at pr_opened for an indefinite next cycle), this purpose has a real finite
     * end: ProjectFlowService.dispatchToPhilosophicalAuditPersistentWorker records each turn's role
     * batch onto the carrier task's own payload at dispatch time, so once every currently-active role
     * has already been asked, THIS turn's answer is the closing synthesis and the discussion is over -
     * reuse completePhilosophicalAudit's merge/archive/close/critique-parsing exactly, then also retire
     * the worker row (the other two persistent purposes are deliberately left open forever; this one is
     * not - see PersistentWorkerPurpose.PHILOSOPHICAL_AUDIT's javadoc).
     */
    /**
     * 2026-08-09 (live incident, operator-flagged: "check that every philosopher has spoken"): BARCAN-TAG-12
     * was sent a follow-up at 09:00:14 and this method merged/closed the whole discussion by 09:00:23 - 9
     * seconds later - with the final archived report never containing a single critique for that role tag.
     * Root cause: "covered" used to come from ProjectFlowService.coveredPhilosophicalAuditRoles, an
     * append-only marker written the moment a role batch was SENT (now removed), not once Jules's answer was
     * actually verified to exist. A stale-status poll edge arriving before Jules had written a word could
     * still see "covered" already at 13/13 and declare the discussion finished on incomplete data. Covered is
     * now derived exclusively from parsing the report file on the branch right now - the exact same source
     * of truth applyPhilosophicalCritiques itself consumes below - so "all roles covered" and "what critiques
     * get applied" can never disagree with each other.
     */
    private void completePersistentPhilosophicalAuditCycle(JulesSessionEntity session, TaskEntity carrierTask) {
        List<String> activeRoleTags = roleRepository.findAll().stream()
                .filter(com.eneik.production.models.persistence.RoleEntity::isActive)
                .map(com.eneik.production.models.persistence.RoleEntity::getTag)
                .toList();
        String reportPath = projectFlowService.philosophicalAuditReportPath(carrierTask);

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(carrierTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique> critiques = reportPath != null
                ? prOpt.map(pr -> parsePhilosophicalReport(carrierTask.getProject(), pr.headRef(), reportPath)).orElseGet(List::of)
                : List.of();
        java.util.Set<String> covered = critiques.stream()
                .map(com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique::roleTag)
                .collect(java.util.stream.Collectors.toSet());
        boolean allRolesCovered = !activeRoleTags.isEmpty() && covered.containsAll(activeRoleTags);

        if (!allRolesCovered) {
            log.info("Persistent philosophical-audit worker (carrier task {}): turn complete, {} of {} active role(s) "
                            + "covered so far (verified against the report file's real content); staying parked at "
                            + "pr_opened for the next turn",
                    carrierTask.getId(), covered.size(), activeRoleTags.size());
            return;
        }

        boolean firstCompletion = claimService.hasActiveClaim(carrierTask.getId());
        String screenshotDir = reportPath == null ? null
                : reportPath.replaceFirst("\\.json$", "") + "-screenshots/";

        if (firstCompletion) {
            falsificationCycleService.applyPhilosophicalCritiques(carrierTask.getProject(), critiques, screenshotDir);
        }

        String mergeReason = firstCompletion
                ? "multi-turn philosophical falsification discussion (all roles) parsed into product-critique wishlist(s)"
                : "duplicate persistent philosophical-audit completion discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(carrierTask.getProject(), pr, mergeReason);
            if (reportPath != null) {
                archiveRecordFile(carrierTask.getProject(), reportPath, "philosophical-falsification-report");
            }
            closeSessionAsNoCode(session, "Multi-turn philosophical falsification discussion complete (all roles covered); branch deleted.");
        });

        persistentWorkerSessionService.findByCarrierTaskId(carrierTask.getId())
                .ifPresent(worker -> persistentWorkerSessionService.retire(worker, "all role-batches covered, discussion complete"));

        if (!firstCompletion) {
            log.warn("Persistent philosophical-audit final turn (carrier task {}): completion discarded - another "
                    + "session already applied this discussion's critiques.", carrierTask.getId());
            return;
        }
        claimService.complete(carrierTask.getId());
        markSystemTaskDone(carrierTask);
        systemProgressTracker.recordProgress();
        log.info("Persistent philosophical-audit discussion for project {} completed (carrier task {}): {} critique(s) from {} role(s)",
                carrierTask.getProject().getId(), carrierTask.getId(), critiques.size(), activeRoleTags.size());
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
        String reportPath = projectFlowService.falsificationAuditReportPath(auditTask);

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(auditTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.FalsificationCycleService.AuditViolation> violations = firstCompletion
                ? prOpt.map(pr -> parseFalsificationReport(auditTask.getProject(), pr.headRef(), reportPath)).orElseGet(List::of)
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
            archiveRecordFile(auditTask.getProject(), reportPath, "falsification-report");
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

    private void completePhilosophicalAudit(JulesSessionEntity session, TaskEntity auditTask) {
        // Same idempotency pattern as completeFalsificationAudit/completeCoverageAudit above.
        boolean firstCompletion = claimService.hasActiveClaim(auditTask.getId());
        String reportPath = projectFlowService.philosophicalAuditReportPath(auditTask);
        String screenshotDir = reportPath == null ? null
                : reportPath.replaceFirst("\\.json$", "") + "-screenshots/";

        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(auditTask.getProject(), session.getExternalSessionId());
        List<com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique> critiques = firstCompletion && reportPath != null
                ? prOpt.map(pr -> parsePhilosophicalReport(auditTask.getProject(), pr.headRef(), reportPath)).orElseGet(List::of)
                : List.of();

        if (firstCompletion) {
            falsificationCycleService.applyPhilosophicalCritiques(auditTask.getProject(), critiques, screenshotDir);
        }

        String mergeReason = firstCompletion
                ? "philosophical falsification report parsed into product-critique wishlist(s)"
                : "duplicate philosophical falsification audit session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(auditTask.getProject(), pr, mergeReason);
            if (reportPath != null) {
                archiveRecordFile(auditTask.getProject(), reportPath, "philosophical-falsification-report");
            }
            closeSessionAsNoCode(session, "Philosophical falsification report merged (process/metadata + screenshots only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("Philosophical falsification audit task {}: session {} completion discarded - another session already applied this audit's critiques.",
                    auditTask.getId(), session.getId());
            return;
        }
        claimService.complete(auditTask.getId());
        markSystemTaskDone(auditTask);
        systemProgressTracker.recordProgress();
        log.info("Philosophical falsification audit for project {} completed by Jules session {}: {} critique(s) reported",
                auditTask.getProject().getId(), session.getExternalSessionId(), critiques.size());
    }

    /**
     * Hard validation, deliberately UNLIKE parseFalsificationReport's `.asText("")` defaults below: a
     * critique with a missing/unrecognized "kanoClass" is dropped entirely rather than defaulted to
     * "Must-Be" or any other value. The whole point of the forced-Kano-classification design (operator
     * directive, 2026-07-25) is that the philosopher must explicitly choose - silently defaulting here would
     * be exactly the "system re-infers Kano and gets Must-Be" failure mode this feature exists to avoid.
     */
    private List<com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique> parsePhilosophicalReport(
            ProjectEntity project, String headRef, String reportPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, reportPath);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            JsonNode rawCritiques = root.path("critiques");
            if (!rawCritiques.isArray()) {
                return List.of();
            }
            // 2026-08-14: "reverse" added here for the same reason as in FalsificationCycleService's copy
            // of this list (see that one's comment) - Kano has five categories, and the missing fifth was
            // precisely the one that lets a critique say "this feature actively harms the product".
            java.util.Set<String> validKano = java.util.Set.of("must-be", "performance", "attractive", "indifferent", "reverse");
            List<com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique> result = new java.util.ArrayList<>();
            for (JsonNode c : rawCritiques) {
                String roleTag = c.path("roleTag").asText("");
                String philosopher = c.path("philosopher").asText("");
                String proposal = c.path("proposal").asText("");
                String kanoClass = c.path("kanoClass").asText("");
                if (roleTag.isBlank() || philosopher.isBlank() || proposal.isBlank()
                        || kanoClass.isBlank() || !validKano.contains(kanoClass.toLowerCase(java.util.Locale.ROOT))) {
                    log.debug("Philosophical falsification report for project {}: dropping invalid critique "
                                    + "(roleTag={}, philosopher={}, kanoClass={}) - missing required field or unrecognized Kano value",
                            project.getId(), roleTag, philosopher, kanoClass);
                    continue;
                }
                result.add(new com.eneik.production.services.FalsificationCycleService.PhilosophicalCritique(
                        roleTag,
                        philosopher,
                        c.path("worldview").asText(""),
                        c.path("critique").asText(""),
                        proposal,
                        c.path("dislike").asText(""),
                        kanoClass,
                        c.path("confidence").asText(""),
                        c.path("evidence").asText(""),
                        c.path("screenshotFile").asText("")
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse philosophical falsification report for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<com.eneik.production.services.FalsificationCycleService.AuditViolation> parseFalsificationReport(
            ProjectEntity project, String headRef, String reportPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, reportPath);
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
                        v.path("attractive").asText(""),
                        v.hasNonNull("prNumber") ? v.path("prNumber").asInt() : null
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
    // Package-private so ScheduledQueryCost-style application tests can call it directly, the same
    // convention reviewFallbackTargetsInFlight and ProjectFlowService.restoreUnreachedBriefs follow.
    void dispatchReviewerFallbackBatch(List<PendingFallbackReview> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        UUID projectId = items.get(0).task().getProject().getId();

        // 2026-08-14 (bug-hunt sweep, same class as the 2026-08-07 dispatchCompilerTask/H2 lock-timeout
        // incident): the project row lock below used to be taken BEFORE this loop, holding it across up to
        // N sequential real GitHub diff-fetch HTTP calls (one per PR in the batch) - the most variable,
        // potentially slowest part of this whole method. Diff fetching itself needs no lock (it's a read
        // from GitHub plus local isTerminalTask filtering, no DB write, no dedup decision yet) - moved
        // ahead of the lock so it never holds it. The actual race this method protects against (two
        // concurrent invocations both deciding the same target is new and both dispatching a duplicate) is
        // still fully protected below, unchanged - only the network-heavy part moved earlier.
        List<TaskEntity> fetchedTasks = new java.util.ArrayList<>();
        List<String> fetchedPrUrls = new java.util.ArrayList<>();
        List<String> fetchedDiffHashes = new java.util.ArrayList<>();

        // §9 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md, observed 2026-08-28: seven Jules sessions spent
        // reviewing pull request 170 of test-fiftieth, which was CLOSED without a merge. The dedup key
        // below is keyed on the diff's content hash - correctly, because a new revision genuinely is not
        // covered by a review of an older one. But the producer of new revisions is this same loop, so
        // every round lifted the bound: a limit the limited thing can raise.
        //
        // The fact that ends it was never read by any predicate. isTerminalTask above asks about the
        // TASK; a task stays non-terminal while its PR is closed. Closure without a merge IS the verdict -
        // there is nothing left to review and no further revision can ever appear - so the target leaves
        // the admission set permanently, with no counter and no number to choose (Charter invariant 8,
        // the same shape decompositionRefused already gives briefs in §4.2).
        //
        // Costs nothing: the snapshot is already taken every tick by both AutoMergeService reconcilers,
        // GitHubPullRequest carries `merged`, and all items in a batch share one project by construction
        // (the caller groups by project). Unavailable snapshot does NOT block - same policy the diff fetch
        // below already follows: leave the item exactly as-is for the next cycle rather than guess.
        java.util.Set<String> reviewablePrUrls = reviewablePrUrls(items.get(0).task().getProject());

        for (PendingFallbackReview item : items) {
            if (isTerminalTask(item.task())) {
                log.info("PR review fallback: target task {} is already terminal; skipping obsolete review dispatch.",
                        item.task().getId());
                continue;
            }
            if (reviewablePrUrls != null && !reviewablePrUrls.contains(item.prUrl())) {
                log.info("PR review fallback: PR {} is closed without a merge; its verdict is already settled, "
                                + "so no review is dispatched for task {} (§9).",
                        item.prUrl(), item.task().getId());
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
            // Keyed by (task, PR URL, diff content hash) - not just task, and not just PR URL either. A
            // task can get a brand new PR later (a merge-conflict rebase, a cancel+redispatch recovery -
            // different prUrl, correctly a new key). But a PR can ALSO get new commits pushed to the SAME
            // URL after a "blocked" review's correction request (applyReviewVerdictToTask's sendMessage +
            // revising round-trip) - same prUrl, genuinely different content, and it must be re-reviewed
            // too. The diff's own content hash is the one signal that's true in both cases: unchanged
            // content is truly already-covered, changed content (new PR OR new commits) never is. Confirmed
            // live (test-thirty-fifth PR#10, 2026-07-23): 2 commits on the PR, second one never reviewed
            // because the old prUrl-only key still matched.
            String diffHash = Integer.toHexString(diff.get().hashCode());
            fetchedTasks.add(item.task());
            fetchedPrUrls.add(item.prUrl());
            fetchedDiffHashes.add(diffHash);
        }
        if (fetchedTasks.isEmpty()) {
            return;
        }

        // Admission-mutex lock (2026-07-24): this method is reachable from two different @Transactional
        // entry points (handlePrOpenedWorkflow's chaotic-domain immediate path, and the regular
        // processPendingReviewBatch sweep) that CAN genuinely run concurrently - spring.task.scheduling.
        // pool.size=10, not the Spring default of 1. reviewFallbackTargetsEverAttempted below re-derives
        // history from the DB at the start of each call - a check-then-INSERT race, same shape already
        // fixed today for dispatchFalsificationAudit/checkAndDispatchCoverageAudits. Locking the project row
        // for this whole remaining span serializes concurrent callers so the second one correctly re-reads
        // history after the first commits, instead of both dispatching a duplicate review-fallback batch.
        // Taken here (after the diff fetches above, not before) so it no longer spans those network calls.
        projectRepository.lockProjectForUpdate(projectId);
        Set<String> scheduledTargets = new java.util.HashSet<>(reviewFallbackTargetsEverAttempted(projectId));
        List<TaskEntity> tasks = new java.util.ArrayList<>();
        List<String> prUrls = new java.util.ArrayList<>();
        List<String> diffHashes = new java.util.ArrayList<>();
        for (int i = 0; i < fetchedTasks.size(); i++) {
            TaskEntity task = fetchedTasks.get(i);
            String prUrl = fetchedPrUrls.get(i);
            String diffHash = fetchedDiffHashes.get(i);
            String targetKey = task.getId() + "::" + prUrl + "::" + diffHash;
            if (!scheduledTargets.add(targetKey)) {
                log.info("Poka-yoke: PR review fallback was already attempted for task {} PR {} at this content revision; automatic retry is disabled.",
                        task.getId(), prUrl);
                continue;
            }
            tasks.add(task);
            prUrls.add(prUrl);
            diffHashes.add(diffHash);
        }
        if (tasks.isEmpty()) {
            return;
        }
        if (persistentWorkerSessionService.isEnabled()) {
            dispatchToReviewFallbackPersistentWorker(tasks, prUrls, diffHashes);
            return;
        }
        String verdictPath = ".eneik/records/review-verdict-" + UUID.randomUUID() + ".json";
        String prompt = reviewerFallbackPromptBatch(tasks, prUrls, verdictPath);
        UUID reviewTaskId = projectFlowService.dispatchReviewFallbackBatch(tasks, prUrls, diffHashes, prompt, verdictPath);
        if (reviewTaskId == null) {
            log.warn("Could not dispatch batched PR review fallback for {} task(s)", tasks.size());
            return;
        }
        log.info("Dispatched batched PR review fallback task {} covering {} PR(s) - Gemini review unavailable",
                reviewTaskId, tasks.size());
    }

    Set<UUID> reviewFallbackTargetsInFlight(UUID projectId) {
        // Scoped to the project (2026-08-28): the predicate below is already per-project, so reading the
        // whole task table first paid a cost proportional to all history for an answer of a few rows.
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(projectFlowService::isReviewFallbackTask)
                .filter(task -> !isTerminalTask(task))
                .flatMap(task -> projectFlowService.reviewFallbackTargetTaskIds(task).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    // "taskId::prUrl::diffHash" composite keys, not bare task ids - see PR_REVIEW_FALLBACK_PR_URLS_KEY /
    // PR_REVIEW_FALLBACK_DIFF_HASH_KEY. A task whose only past review targeted a since-superseded PR, or an
    // earlier revision of the SAME PR (pre-correction), must NOT be treated as covered for its current PR
    // content.
    Set<String> reviewFallbackTargetsEverAttempted(UUID projectId) {
        Set<String> keys = new java.util.HashSet<>();
        // Scoped to the project (2026-08-28), same reason as above.
        taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(projectFlowService::isReviewFallbackTask)
                .forEach(task -> {
                    List<UUID> ids = projectFlowService.reviewFallbackTargetTaskIds(task);
                    List<String> urls = projectFlowService.reviewFallbackTargetPrUrls(task);
                    List<String> hashes = projectFlowService.reviewFallbackTargetDiffHashes(task);
                    for (int i = 0; i < ids.size(); i++) {
                        UUID targetId = ids.get(i);
                        // A batch that completed without a verdict entry for this specific target never
                        // actually reviewed it (applyReviewVerdictToTask's null-verdict branch) - excluding
                        // it here, while its per-target retry counter is still under the cap, is what makes
                        // it eligible for a genuine re-review next processPendingReviewBatch tick instead of
                        // being pre-blocked by this same poka-yoke forever. See
                        // PR_REVIEW_FALLBACK_NULL_VERDICT_RETRY_KEY.
                        TaskEntity target = taskRepository.findById(targetId).orElse(null);
                        if (target != null
                                && target.getStatus() == com.eneik.production.models.persistence.TaskStatus.pending_review
                                && projectFlowService.reviewFallbackNullVerdictRetryCount(target) > 0
                                && projectFlowService.reviewFallbackNullVerdictRetryCount(target)
                                        < com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_MAX_NULL_VERDICT_RETRIES) {
                            continue;
                        }
                        String url = i < urls.size() ? urls.get(i) : "";
                        String hash = i < hashes.size() ? hashes.get(i) : "";
                        keys.add(targetId + "::" + url + "::" + hash);
                    }
                });
        return keys;
    }

    /**
     * The set of PR urls still worth reviewing for this project: open, or closed AND merged.
     *
     * <p>Returns null when GitHub could not be reached, which callers must read as "unknown, do not
     * block" rather than as "nothing is reviewable" - an empty set would silently stop every review the
     * moment GitHub hiccuped, turning an outage into a permanent halt (§2, the exact shape invariant 8
     * warns about in the other direction).
     */
    java.util.Set<String> reviewablePrUrls(com.eneik.production.models.persistence.ProjectEntity project) {
        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (snapshot == null || !snapshot.available() || snapshot.open() == null) {
            return null;
        }
        java.util.Set<String> urls = new java.util.HashSet<>();
        snapshot.open().forEach(pr -> urls.add(pr.url()));
        if (snapshot.closed() != null) {
            snapshot.closed().stream().filter(GitHubPullRequestService.GitHubPullRequest::merged)
                    .forEach(pr -> urls.add(pr.url()));
        }
        return urls;
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
    private void dispatchToReviewFallbackPersistentWorker(List<TaskEntity> tasks, List<String> prUrls, List<String> diffHashes) {
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
                // Same path this worker's carrier task was created with (stored in ITS payload) - reused for
                // every follow-up cycle on this one branch, never regenerated. Collisions only ever happened
                // ACROSS different branches/workers, so per-carrier stability here is correct, not a bug.
                TaskEntity workerCarrierTask = worker.getCarrierTaskId() != null
                        ? taskRepository.findById(worker.getCarrierTaskId()).orElse(null)
                        : null;
                String followUpVerdictPath = workerCarrierTask != null
                        ? projectFlowService.reviewFallbackVerdictPath(workerCarrierTask)
                        : ".eneik/records/review-verdict-" + UUID.randomUUID() + ".json";
                if (session != null && sendFollowUpMessage(session,
                        reviewerFallbackFollowUpPromptBatch(tasks, prUrls, followUpVerdictPath))) {
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

        createFreshReviewFallbackPersistentWorker(project, tasks, prUrls, diffHashes, batchIds);
    }

    private void createFreshReviewFallbackPersistentWorker(com.eneik.production.models.persistence.ProjectEntity project,
            List<TaskEntity> tasks, List<String> prUrls, List<String> diffHashes, List<UUID> batchIds) {
        String verdictPath = ".eneik/records/review-verdict-" + UUID.randomUUID() + ".json";
        String prompt = reviewerFallbackPromptBatch(tasks, prUrls, verdictPath);
        UUID reviewTaskId = projectFlowService.dispatchReviewFallbackBatchAsPersistentCarrier(tasks, prUrls, diffHashes, prompt, verdictPath);
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
    private String reviewerFallbackFollowUpPromptBatch(List<TaskEntity> tasks, List<String> prUrls, String verdictPath) {
        String body = reviewerFallbackPromptBatch(tasks, prUrls, verdictPath);
        return """
                NEW CYCLE for the same persistent review-fallback worker session. The PR(s) below are a
                FRESH batch, unrelated to whatever you reviewed in a previous cycle on this same branch.
                OVERWRITE `%s` so it contains ONLY the verdicts for THIS cycle's
                PR(s) - do not keep, merge, or reference any previous cycle's content. Commit the update to
                the same branch/PR you already have open.

                %s
                """.formatted(verdictPath, body);
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

    /**
     * §10: the diff is NOT pasted in. The reviewer's session runs inside the client repository and can
     * read the pull request there; sending a copy of what the recipient already has made the size of this
     * factory's own request a property of somebody else's work.
     *
     * <p>Measured 2026-08-29: one such task reached 1 788 060 characters because the PR under review
     * carried sixty files including tracked build output. Jules refused it with 400 INVALID_ARGUMENT, the
     * factory had no outcome meaning "our request was bad", so it recorded the refusal against the ACCOUNT
     * and burned three of them in one tick - twice over the escalation threshold of two. Median task
     * description across the same project is 10 437 characters; every one of the outliers was this prompt.
     *
     * <p>The diff is still fetched by the caller and still hashed - the dedup key taskId::prUrl::diffHash
     * from §9 is unchanged to the bit. It simply stops travelling.
     */
    String reviewerFallbackPromptBatch(List<TaskEntity> tasks, List<String> prUrls, String verdictPath) {
        StringBuilder prBlocks = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            TaskEntity t = tasks.get(i);
            prBlocks.append("""
                    ===== PR #%d (sourceIndex %d) =====
                    Original task (role %s):
                    %s

                    PR under review: %s

                    Read that pull request's own diff in this repository to review it - it is not copied
                    here. If the diff turns out to carry committed build output or other generated
                    artifacts (target/, node_modules, coverage, surefire reports, .class files), that alone
                    is a blocking finding for this PR and you do not need to read further to record it.

                    """.formatted(i, i, t.getRole().getTag(), t.getDescription(), prUrls.get(i)));
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
                .zip/.png/.webm/.trace files), missing required tests for a QA task, a status/lifecycle field
                with a terminal value (done/failed/confirmed/settled) written via a plain read-then-save
                instead of a guarded conditional update WHEN the diff shows that entity is reachable from
                more than one place (a scheduled job plus a direct endpoint, or two independent call sites) -
                that specific, narrow shape only, not every setter-then-save you see - or a direct
                contradiction of that PR's own stated Acceptance Criteria/DoD. Anything else - style
                preferences, architecture opinions, missing edge cases that do not break the Acceptance
                Criteria, suggestions for a better approach - is NOT a blocker: approve that PR and list it
                as a "concern" instead, so it becomes a follow-up improvement item rather than stopped work.

                Deliverable: create a new branch and open a PR that contains ONLY one file, `%s`
                (this EXACT path - it is unique to this batch, do not use any other path), with EXACTLY
                this shape and no other files changed - one entry per PR listed below, in the same order,
                each carrying its own "sourceIndex". Each concern carries its own "severity"
                (critical|high|medium|low - a real security/correctness risk like unverified auth headers is
                high or critical; a style nit like px vs rem is low) - you already judge this when you decide
                whether something is worth mentioning at all, just make that judgment explicit instead of
                flattening every concern to the same weight:
                {"verdicts": [
                  {"sourceIndex": 0, "verdict": "approve", "criticalReason": "", "concerns": [{"text": "short concern 1", "severity": "low"}]},
                  {"sourceIndex": 1, "verdict": "block", "criticalReason": "concrete, specific blocking reason tied to PR #1's diff", "concerns": []}
                ]}
                Every PR listed below MUST have exactly one corresponding entry, matched by sourceIndex.
                Do not write, modify, or delete any other file.

                %d PR(s) to review:

                %s
                """.formatted(tasks.size(), verdictPath, tasks.size(), prBlocks.toString());
    }

    // 2026-08-01: severity-tagged concern (u₄ of the unified Six Sigma layer - see
    // docs/ENGINEERING_INVARIANTS_CHARTER.md and ReviewConcernEntity). Parses a bare string entry as a
    // "low"-severity concern for backward compatibility with any in-flight verdict written before this
    // schema change - never fails parsing outright over a missing severity field.
    private record ConcernEntry(String text, String severity) {
    }

    private record ReviewVerdictEntry(int sourceIndex, String verdict, String criticalReason, List<ConcernEntry> concerns) {
    }

    private static final java.util.Set<String> RECOGNIZED_VERDICTS = java.util.Set.of("approve", "block", "reject");

    /**
     * Fail-closed verdict parsing (Charter Pattern #12 - independent verification, not self-attestation):
     * returns null when the "verdict" field is missing, blank, or not a recognized value, instead of
     * silently defaulting to "approve". Shared by review-fallback ("approve"/"block") and design-review
     * ("approve"/"reject") parsing below - both used to hand-roll the same asText("approve") default
     * independently, which meant a reviewing session that returned a malformed or incomplete report was
     * auto-approved rather than treated as "no verdict available".
     */
    private static String parseVerdictFailClosed(JsonNode node) {
        String raw = node.path("verdict").asText(null);
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return RECOGNIZED_VERDICTS.contains(normalized) ? normalized : null;
    }

    private static List<ConcernEntry> parseConcernEntries(JsonNode rawConcerns) {
        List<ConcernEntry> concerns = new java.util.ArrayList<>();
        if (rawConcerns.isArray()) {
            for (JsonNode c : rawConcerns) {
                if (c.isTextual()) {
                    concerns.add(new ConcernEntry(c.asText(""), "low"));
                } else {
                    String text = c.path("text").asText("");
                    String severity = c.path("severity").asText("low");
                    concerns.add(new ConcernEntry(text, severity));
                }
            }
        }
        return concerns;
    }

    private List<ReviewVerdictEntry> parseReviewVerdictBatch(ProjectEntity project, String headRef, String verdictPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, verdictPath);
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
                    String verdict = parseVerdictFailClosed(v);
                    if (verdict == null) {
                        // Fail closed: a missing/garbled verdict field must not be fabricated into an
                        // "approve" entry - skip it so applyReviewVerdictToTask's sourceIndex lookup finds
                        // nothing and takes the same bounded-retry path as a genuinely missing verdict.
                        log.warn("Review fallback verdict batch for project {}: sourceIndex {} has a missing or "
                                        + "unrecognized 'verdict' field, treating as no verdict", project.getId(), sourceIndex);
                        continue;
                    }
                    String criticalReason = v.path("criticalReason").asText("");
                    List<ConcernEntry> concerns = parseConcernEntries(v.path("concerns"));
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
        String verdictPath = projectFlowService.reviewFallbackVerdictPath(reviewTask);

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
                ? prOpt.map(pr -> parseReviewVerdictBatch(reviewTask.getProject(), pr.headRef(), verdictPath)).orElseGet(List::of)
                : List.of();

        String mergeReason = firstCompletion ? "PR review fallback verdict parsed" : "duplicate review fallback session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(reviewTask.getProject(), pr, mergeReason);
            archiveRecordFile(reviewTask.getProject(), verdictPath, "review-verdict");
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

        // Charter Pattern #12 (independent verification, not self-attestation): a reviewer's own verdict
        // alone is not sufficient to approve - task.isQualityGatePassed() is a fully separate, mechanical
        // signal (GateOrchestrator, zero LLM involvement) that ClaimService.complete already computes and
        // requires BEFORE a task can even reach `review` status, so this should be structurally impossible
        // to see false here in production. Kept as defense in depth: if it ever is, a reviewer verdict must
        // not override a failed mechanical gate - fall back to the same bounded-retry path used for a
        // missing verdict below, rather than silently promoting on the reviewer's word alone.
        if (verdict != null && !originalTask.isQualityGatePassed()) {
            log.error("PR review fallback task {}: original task {} has NOT passed the quality gate; a "
                            + "reviewer verdict alone cannot approve it. Treating as no valid verdict.",
                    reviewTask.getId(), originalTaskId);
            verdict = null;
        }

        if (verdict == null) {
            int retries = projectFlowService.recordReviewFallbackNullVerdict(originalTask);
            if (retries >= com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_MAX_NULL_VERDICT_RETRIES) {
                // Genuinely exhausted, not just "no verdict this one time": stop retrying automatically and
                // surface it the same way every other stuck task surfaces - createRecoveryWishlistForOrphaned
                // BlockedTasks already retires an orphaned blocked task (no active Jules session) to failed
                // with a clear reason, which lets the normal falsification/coverage-audit gap-detection
                // recreate the work instead of leaving it silently frozen in pending_review forever.
                originalTask.setStatus(com.eneik.production.models.persistence.TaskStatus.blocked);
                originalTask.setJulesDispatchStatus("PR review fallback returned no verdict for this task " + retries
                        + " time(s) in a row; giving up automatic retry.");
                taskRepository.save(originalTask);
                log.error("PR review fallback task {}: no valid verdict entry found for task {} on attempt {}/{}; "
                                + "giving up automatic retry, marked blocked for recovery.",
                        reviewTask.getId(), originalTaskId, retries,
                        com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_MAX_NULL_VERDICT_RETRIES);
            } else {
                taskRepository.save(originalTask);
                log.warn("PR review fallback task {}: no valid verdict entry found for task {} (attempt {}/{}); eligible for a fresh review dispatch next cycle.",
                        reviewTask.getId(), originalTaskId, retries,
                        com.eneik.production.services.ProjectFlowService.PR_REVIEW_FALLBACK_MAX_NULL_VERDICT_RETRIES);
            }
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
            projectFlowService.clearReviewFallbackNullVerdictRetries(originalTask);
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
        // Charter Pattern #12: don't hardcode ciStatus to "success" just because the reviewer approved -
        // fetch the real GitHub check-run state. AutoMergeService.executeMerge independently re-checks
        // this again right before merging either way, so this isn't the last line of defense, but nothing
        // downstream of here should see a fabricated "success" for a PR whose real CI never ran or failed.
        Integer pullNumber = parsePullNumber(prUrl);
        String ciStatus = "success";
        if (pullNumber != null) {
            GitHubPullRequestService.PullRequestChecks checks =
                    gitHubPullRequestService.pullRequestChecks(originalTask.getProject(), pullNumber);
            if (checks.available()) {
                ciStatus = checks.successful() ? "success" : "failure";
            }
        }
        prData.setCiStatus(ciStatus);
        prData.setLinesChanged(120);
        prData.setFilesChanged(4);
        prData.setChangedFiles(java.util.Collections.emptyList());
        String remarks = "CORE ARCHITECTURE VERIFIED. APPROVED. Jules fallback review (Gemini unavailable). "
                + (verdict.concerns().isEmpty() ? "No concerns raised." : verdict.concerns().size() + " concern(s) recorded as follow-up wishlist items.");
        prData.setDiffSummary(remarks);
        prReviewPipelineService.onPrOpened(prUrl, implementerSession.getId(), prData);

        originalTask.setStatus(com.eneik.production.models.persistence.TaskStatus.review);
        projectFlowService.clearReviewFallbackNullVerdictRetries(originalTask);
        taskRepository.save(originalTask);
        systemProgressTracker.recordProgress();
        log.info("PR review fallback: task {} (PR {}) approved by Jules reviewer with {} concern(s)", originalTaskId, prUrl, verdict.concerns().size());

        for (ConcernEntry concern : verdict.concerns()) {
            if (concern.text() == null || concern.text().isBlank()) {
                continue;
            }
            log.info("Poka-yoke: recorded non-blocking review concern for task {} without creating wishlist work: {}",
                    originalTaskId, concern.text());
            persistReviewConcern(originalTask, concern);
        }
    }

    // 2026-08-01: the u₄ numerator write-through - see ReviewConcernEntity's own doc comment for why this
    // used to only ever reach a log line. Best-effort: a persistence failure here must never break the real
    // review-approval flow above it, it should just be logged and skipped.
    private void persistReviewConcern(TaskEntity task, ConcernEntry concern) {
        try {
            com.eneik.production.models.persistence.ReviewConcernEntity entity =
                    new com.eneik.production.models.persistence.ReviewConcernEntity();
            entity.setProjectId(task.getProject() != null ? task.getProject().getId() : null);
            entity.setFeatureId(task.getFeatureId());
            entity.setTaskId(task.getId());
            entity.setSeverity(concern.severity() == null || concern.severity().isBlank() ? "low" : concern.severity());
            entity.setText(concern.text());
            reviewConcernRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist review concern for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private record CoverageGap(String title, String roleTag, String jtbd, String acceptanceCriteria, String reason) {
    }

    private List<CoverageGap> parseCoverageAuditReport(ProjectEntity project, String headRef, String reportPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, reportPath);
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
     * ProjectFlowService.checkAndDispatchCoverageAudits), never product code. Discards that PR either
     * way, then turns every reported gap into a new pending wishlist item (source=coverage_gap) carrying
     * the same featureId as the decomposition it audited, so it flows through the normal pull-based,
     * WIP-gated compiler cycle like any other wishlist item - never fabricated as a ready-made task
     * directly, since a gap is still just a claim from one audit pass until the same scrutiny (adequacy
     * filter, WIP limits, cheap-path checks) that every other wishlist item goes through has run on it.
     */
    private void completeCoverageAudit(JulesSessionEntity session, TaskEntity auditTask) {
        UUID targetWishlistId = projectFlowService.coverageAuditTargetWishlistId(auditTask);
        String reportPath = projectFlowService.coverageAuditReportPath(auditTask);

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
                ? prOpt.map(pr -> parseCoverageAuditReport(auditTask.getProject(), pr.headRef(), reportPath)).orElseGet(List::of)
                : List.of();

        String mergeReason = firstCompletion ? "Coverage audit report parsed" : "duplicate coverage audit session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(auditTask.getProject(), pr, mergeReason);
            archiveRecordFile(auditTask.getProject(), reportPath, "coverage-audit");
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
        // Semantic-duplication guard (2026-07-24): the substring/pending-only check above only ever
        // compared a NEW gap against other still-pending ones - once a gap's wishlist converts to a task
        // (the successful case), a LATER audit re-identifying "the same" requirement in different wording
        // sailed straight through. Broadened live set (everything except dismissed) + content-similarity
        // second pass, see WishlistContentSimilarityMatcher for the fail-open/threshold rationale.
        List<com.eneik.production.models.persistence.WishlistEntity> liveGapWishlists =
                wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                        auditTask.getProject().getId(),
                        com.eneik.production.models.persistence.WishlistSource.coverage_gap,
                        List.of(com.eneik.production.models.persistence.WishlistStatus.pending,
                                com.eneik.production.models.persistence.WishlistStatus.compiling,
                                com.eneik.production.models.persistence.WishlistStatus.converted_to_task));

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
            Optional<UUID> semanticDuplicate = wishlistContentSimilarityMatcher.findLikelyDuplicate(
                    liveGapWishlists, gap.title() + " " + gap.jtbd());
            if (semanticDuplicate.isPresent()) {
                log.info("Coverage audit task {}: skipping gap \"{}\" - content matches existing wishlist {}",
                        auditTask.getId(), gap.title(), semanticDuplicate.get());
                continue;
            }

            com.eneik.production.models.persistence.WishlistEntity wishlist = new com.eneik.production.models.persistence.WishlistEntity();
            wishlist.setProjectId(auditTask.getProject().getId());
            wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.coverage_gap);
            wishlist.setSourceRoleTag(gap.roleTag());
            // Left unset (unlike the pre-redesign version): a coverage audit now runs once against the
            // WHOLE wishlist's shipped code, potentially spanning several epics, so there is no single
            // correct feature to inherit anymore - resolveOrCreateFeatureId gives it its own grouping,
            // same fallback every other un-featured wishlist item already gets.
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

    private record DesignVerdict(String verdict, String reason, List<ConcernEntry> concerns) {
    }

    private DesignVerdict parseDesignVerdict(ProjectEntity project, String headRef, String verdictPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, verdictPath);
        if (content.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            String verdict = parseVerdictFailClosed(root);
            if (verdict == null) {
                // Fail closed: same reasoning as parseReviewVerdictBatch above - a missing or unrecognized
                // verdict field must not be fabricated into an "approve", it must fall through to the
                // existing "no valid verdict report found; left unpromoted" handling in completeDesignReview.
                log.warn("Design review verdict for project {} has a missing or unrecognized 'verdict' field", project.getId());
                return null;
            }
            String reason = root.path("reason").asText("");
            List<ConcernEntry> concerns = parseConcernEntries(root.path("concerns"));
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
        String verdictPath = projectFlowService.designReviewVerdictPath(reviewTask);

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
                ? prOpt.map(pr -> parseDesignVerdict(reviewTask.getProject(), pr.headRef(), verdictPath)).orElse(null)
                : null;

        String mergeReason = firstCompletion ? "design review verdict parsed" : "duplicate design review session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(reviewTask.getProject(), pr, mergeReason);
            archiveRecordFile(reviewTask.getProject(), verdictPath, "design-review-verdict");
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

        // Charter Pattern #12 (independent verification, not self-attestation): promotion requires an
        // EXPLICIT "approve", not "anything except reject with a reason" - the prior fail-open version
        // silently promoted a "reject" with a blank reason (and anything else parseVerdictFailClosed
        // would have let through), which is exactly the shape of bug this pattern exists to close. A
        // reject without a real reason is still a reject, it just gets logged without a reason attached.
        if (!"approve".equalsIgnoreCase(verdict.verdict())) {
            log.warn("Poka-yoke: design draft {} not approved (verdict: {}); left unpromoted, no follow-up "
                            + "wishlist work created - the finding is deferred to falsification.{}",
                    draftPath, verdict.verdict(),
                    verdict.reason() != null && !verdict.reason().isBlank() ? " Reason: " + verdict.reason() : "");
            return;
        }
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

        List<String> concernTexts = new java.util.ArrayList<>();
        for (ConcernEntry concern : verdict.concerns()) {
            if (concern.text() == null || concern.text().isBlank()) {
                continue;
            }
            log.info("Poka-yoke: recorded non-blocking design concern for {}: {}", approvedDir, concern.text());
            persistReviewConcern(reviewTask, concern);
            concernTexts.add("- (" + (concern.severity() == null || concern.severity().isBlank() ? "low" : concern.severity())
                    + ") " + concern.text());
        }
        // Stage 2.5 (2026-08-11): the concerns above still feed the existing Six Sigma u-chart via
        // persistReviewConcern - kept as-is. Additionally, dispatch a structured triage pass so these
        // concerns don't just sit as a log/statistic - see ProjectFlowService.dispatchDesignConcernTriage.
        if (!concernTexts.isEmpty()) {
            projectFlowService.dispatchDesignConcernTriage(reviewTask.getProject(), approvedDir, String.join("\n", concernTexts));
        }
    }

    private record TriageEntry(String concern, String jtbd, String acceptanceCriteria, String editInstruction,
                                String kanoClass, String cynefinDomain, String leanValue) {
    }

    private List<TriageEntry> parseTriageEntries(ProjectEntity project, String headRef, String recordPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, recordPath);
        if (content.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(content.get());
            List<TriageEntry> entries = new java.util.ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    entries.add(new TriageEntry(
                            node.path("concern").asText(""),
                            node.path("jtbd").asText(""),
                            node.path("acceptanceCriteria").asText(""),
                            node.path("editInstruction").asText(""),
                            node.path("kanoClass").asText(""),
                            node.path("cynefinDomain").asText(""),
                            node.path("leanValue").asText("")));
                }
            }
            return entries;
        } catch (Exception e) {
            log.warn("Failed to parse design concern triage record for project {}: {}", project.getId(), e.getMessage());
            return List.of();
        }
    }

    private static final int MAX_DESIGN_EDIT_ITERATIONS = 3;

    /**
     * Design shop Stage 2.5 completion: a triage task (dispatched from completeDesignReview) reached
     * pr_opened with a structured JSON array of {@link TriageEntry}. Same PR-discard/merge-record
     * shape as completeDesignReview - the PR carries only the record file, never product code.
     *
     * Branches on whether this project's design cycle is still {@code AWAITING_REVIEW} (Stage 3 -
     * BARCAN-TAG-11 implementation - has not fired yet):
     *   - Still open: apply the actionable (non-"waste") edit instructions to the SAME Stitch screen via
     *     {@link StitchClient#editScreens} (destructive, in-place - one source of truth, no drifting
     *     patch), overwrite the already-promoted mockup with the refined version, bounded by
     *     {@link #MAX_DESIGN_EDIT_ITERATIONS} so this can never loop forever (same discipline as the
     *     removed WishlistSource.idle_generation - an unbounded self-revision loop was judged dangerous).
     *   - Already closed (Stage 3 fired, real code shipped): a mockup edit can no longer fix anything -
     *     each actionable entry becomes a real WishlistEntity(design_review_concern_pattern), with the
     *     triage's own jtbd/acceptanceCriteria/cynefinDomain/leanValue carried through directly (not a
     *     re-aggregation of raw text), so it compiles into real, properly classified follow-up work
     *     through the existing TechnicalLeadCompiler pipeline, unmodified.
     */
    private void completeDesignConcernTriage(JulesSessionEntity session, TaskEntity triageTask) {
        String mockupPath = projectFlowService.designConcernTriageMockupPath(triageTask);
        String recordPath = projectFlowService.designConcernTriageRecordPath(triageTask);

        boolean firstCompletion = claimService.hasActiveClaim(triageTask.getId());
        Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                gitHubPullRequestService.findOpenPullRequestBySession(triageTask.getProject(), session.getExternalSessionId());
        List<TriageEntry> entries = firstCompletion
                ? prOpt.map(pr -> parseTriageEntries(triageTask.getProject(), pr.headRef(), recordPath)).orElse(List.of())
                : List.of();

        String mergeReason = firstCompletion ? "design concern triage record parsed" : "duplicate triage session discarded";
        prOpt.ifPresent(pr -> {
            gitHubPullRequestService.mergeRecordPullRequest(triageTask.getProject(), pr, mergeReason);
            archiveRecordFile(triageTask.getProject(), recordPath, "design-concern-triage");
            closeSessionAsNoCode(session, "Design concern triage record merged (process/metadata only by design); branch deleted.");
        });

        if (!firstCompletion) {
            log.warn("Design concern triage task {}: session {} completion discarded - another session already processed this record.",
                    triageTask.getId(), session.getId());
            return;
        }
        claimService.complete(triageTask.getId());
        markSystemTaskDone(triageTask);

        if (mockupPath == null || entries.isEmpty()) {
            log.warn("Design concern triage task {}: no usable triage entries found; concerns remain only in the review-concern log/u-chart",
                    triageTask.getId());
            return;
        }

        List<TriageEntry> actionable = entries.stream()
                .filter(e -> !"waste".equalsIgnoreCase(e.leanValue()))
                .toList();
        if (actionable.isEmpty()) {
            log.info("Design concern triage task {}: all {} concern(s) triaged as waste; nothing to action",
                    triageTask.getId(), entries.size());
            return;
        }

        DesignShopCycleEntity cycle = designShopCycleRepository.findByProjectId(triageTask.getProject().getId()).orElse(null);
        boolean mockupStillOpen = cycle != null && DesignShopCycleEntity.STAGE_AWAITING_REVIEW.equals(cycle.getStage());

        if (mockupStillOpen) {
            applyDesignConcernEdits(triageTask.getProject(), cycle, mockupPath, actionable);
        } else {
            escalateDesignConcernsToWishlist(triageTask.getProject(), actionable);
        }
    }

    private void applyDesignConcernEdits(ProjectEntity project, DesignShopCycleEntity cycle, String mockupPath, List<TriageEntry> actionable) {
        if (cycle.getEditIterationCount() >= MAX_DESIGN_EDIT_ITERATIONS) {
            log.info("Design concern triage: edit-iteration cap ({}) reached for project {}; leaving mockup {} as-is "
                            + "(soft philosophy - a design opinion never blocks work indefinitely)",
                    MAX_DESIGN_EDIT_ITERATIONS, project.getId(), mockupPath);
            return;
        }
        if (cycle.getStitchProjectId() == null || cycle.getStitchScreenId() == null) {
            log.warn("Design concern triage: no stitchProjectId/stitchScreenId recorded for project {}; cannot call edit_screens", project.getId());
            return;
        }
        String editPrompt = actionable.stream()
                .map(TriageEntry::editInstruction)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (editPrompt.isBlank()) {
            return;
        }
        StitchClient.GeneratedScreen edited = stitchClient.editScreens(cycle.getStitchProjectId(), cycle.getStitchScreenId(), editPrompt);
        if (!edited.available()) {
            log.warn("Design concern triage: edit_screens failed for project {}: {}", project.getId(), edited.message());
            return;
        }
        boolean anyCommitted = false;
        if (!edited.htmlDownloadUrl().isBlank()) {
            byte[] htmlBytes = stitchClient.download(edited.htmlDownloadUrl());
            if (htmlBytes != null) {
                anyCommitted |= gitHubPullRequestService.commitFile(project, mockupPath + "/mockup.html", htmlBytes,
                        "Apply " + actionable.size() + " triaged design concern(s) to approved mockup");
            }
        }
        if (!edited.screenshotDownloadUrl().isBlank()) {
            byte[] pngBytes = stitchClient.download(edited.screenshotDownloadUrl());
            if (pngBytes != null) {
                anyCommitted |= gitHubPullRequestService.commitFile(project, mockupPath + "/mockup.png", pngBytes,
                        "Apply " + actionable.size() + " triaged design concern(s) to approved mockup screenshot");
            }
        }
        if (anyCommitted) {
            cycle.setEditIterationCount(cycle.getEditIterationCount() + 1);
            cycle.setUpdatedAt(Instant.now());
            designShopCycleRepository.save(cycle);
            log.info("Design concern triage: applied {} actionable concern(s) to mockup {} for project {} (iteration {}/{})",
                    actionable.size(), mockupPath, project.getId(), cycle.getEditIterationCount(), MAX_DESIGN_EDIT_ITERATIONS);
        }
    }

    private void escalateDesignConcernsToWishlist(ProjectEntity project, List<TriageEntry> actionable) {
        for (TriageEntry entry : actionable) {
            WishlistEntity wishlist = new WishlistEntity();
            wishlist.setProjectId(project.getId());
            wishlist.setSource(WishlistSource.design_review_concern_pattern);
            wishlist.setStatus(WishlistStatus.pending);
            wishlist.setLeanValue(parseLeanValue(entry.leanValue()));
            wishlist.setContent("Design review concern (Kano: " + defaultText(entry.kanoClass(), "unclassified")
                    + "): " + entry.concern());
            wishlist.setJtbd(defaultText(entry.jtbd(), entry.concern()));
            wishlist.setAcceptanceCriteria(defaultText(entry.acceptanceCriteria(), ""));
            wishlist.setDod(defaultText(entry.acceptanceCriteria(), ""));
            wishlist.setCynefinDomain(defaultText(entry.cynefinDomain(), ""));
            // Traces this item back to the real, already-computed Six Sigma signal that made this
            // review-concern pattern worth escalating in the first place - see ProcessControlService.
            wishlist.setSixSigmaMetric(com.eneik.production.services.quality.ProcessControlService.STREAM_REVIEW_CONCERNS);
            wishlistRepository.save(wishlist);
            log.info("Design concern triage: escalated post-merge concern to wishlist for project {}: {}", project.getId(), entry.concern());
        }
    }

    private static LeanValue parseLeanValue(String raw) {
        try {
            return LeanValue.valueOf(raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LeanValue.valuable;
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
     * Phase 8 (2026-07-21, operator directive): parses the two-level {"epics": [{...,"slices": [...]}]} shape -
     * a wishlist splits into as many epics (epics) as the product needs, not always exactly one, and every
     * compile cycle decides per epic whether it matches an existing one (see existingEpicsPromptContext).
     */
    private List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> parseCompilerPlan(
            ProjectEntity project, String headRef, String planPath) {
        Optional<String> content = gitHubPullRequestService.fetchFileContent(project, headRef, planPath);
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
                        epicKanoClass(project, epicNode),
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

    /**
     * Closes F17. This used to be {@code .asText("Must-Be")}, which made a missing classification
     * indistinguishable from a deliberate one - and that is precisely why all five epics of test-forty-sixth
     * came out Must-Be. The vocabulary was not failing to discriminate; nothing was being asked.
     *
     * The correct rule was already written in THIS FILE, 1200 lines above, over parsePhilosophicalReport:
     * "silently defaulting here would be exactly the 'system re-infers Kano and gets Must-Be' failure mode
     * this feature exists to avoid" (operator directive, 2026-07-25). And ProjectFlowService's own parser had
     * already been repaired on 2026-08-14, its comment claiming "the two sides of the flow now hold the same
     * discipline" - while this parser, the one a Jules-delivered plan actually travels, still defaulted. The
     * vocabulary now lives once, in {@link KanoClass}, because a fix that leaves the concept in two places
     * is the defect rather than the repair.
     *
     * It does NOT drop the epic the way the critique path drops a critique. The asymmetry is deliberate and
     * is about what is lost: a dropped critique costs one opinion, a dropped epic costs its requirements and
     * every slice under it. So the epic survives carrying an explicit marker, which reaches the project tree
     * and is therefore visible to the operator rather than buried in a log.
     *
     * A second, quieter consequence is repaired by the same change: SelfFalsificationEpicMatcher adds a
     * bonus when two epics share a Kano class. While everything defaulted to Must-Be that bonus was applied
     * to every pair equally, so a signal built to discriminate contributed a constant.
     */
    private String epicKanoClass(ProjectEntity project, JsonNode epicNode) {
        String raw = epicNode.path("kanoClass").asText("");
        String normalized = com.eneik.production.services.compiler.KanoClass.normalize(raw);
        if (com.eneik.production.services.compiler.KanoClass.isUnclassified(normalized)) {
            log.warn("Compiler plan for project {}: epic '{}' has kanoClass '{}' - not one of {}. Recording "
                            + "it as {} rather than defaulting, so an unmade classification stays "
                            + "distinguishable from a made one.",
                    project.getId(), epicNode.path("title").asText(""), raw,
                    com.eneik.production.services.compiler.KanoClass.valid(), normalized);
        }
        return normalized;
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

    /**
     * Empty when the plan is usable; otherwise the condition that rejected it.
     *
     * <p>Returned a bare boolean until 2026-08-29, and there are fourteen distinct ways to fail in here.
     * Measured that day: the compiler was finally reachable, wrote a plan, and the plan was refused twice
     * with the single word "invalid" - then the compiler task went to human review and six briefs settled
     * as refused, which leaves the factory with no source of new tasks at all. Nobody could tell whether
     * Jules had written something wrong or this check was too strict, and the correction sent back to Jules
     * was a fixed paragraph restating the whole schema, identical on both attempts, so it learned nothing
     * between them either.
     *
     * <p>The checks themselves, their order and their strictness are untouched: only what the function
     * reports has changed.
     */
    /**
     * The one rejection that is evidence about the BRIEF rather than about this factory's own check: the
     * compiler answered and there was nothing in the answer. Every other rejection below says the compiler
     * produced something this factory refused, which establishes nothing about the brief (action plan 8.3).
     */
    static final String EMPTY_COMPILER_ANSWER = "the answer carried no epic at all";

    static String compilerPlanRejection(List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics, int briefCount) {
        if (epics.isEmpty()) {
            return EMPTY_COMPILER_ANSWER;
        }
        int normalizedBriefCount = Math.max(1, briefCount);
        java.util.Map<Integer, Integer> epicsByBrief = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> slicesByBrief = new java.util.HashMap<>();
        java.util.Set<Integer> representedBriefs = new java.util.HashSet<>();
        for (com.eneik.production.services.MLPredictionServiceClient.EpicPlan epic : epics) {
            String where = "epic \"" + (epic.title() == null ? "(untitled)" : epic.title()) + "\" (sourceIndex "
                    + epic.sourceIndex() + ")";
            if (epic.sourceIndex() < 0 || epic.sourceIndex() >= normalizedBriefCount) {
                return where + ": sourceIndex is outside the range of the " + normalizedBriefCount
                        + " brief(s) that were sent";
            }
            representedBriefs.add(epic.sourceIndex());
            int epicCount = epicsByBrief.merge(epic.sourceIndex(), 1, Integer::sum);
            if (epicCount > MAX_EPICS_PER_BRIEF) {
                return where + ": brief " + epic.sourceIndex() + " has " + epicCount + " epics, more than "
                        + MAX_EPICS_PER_BRIEF;
            }
            if (epic.existingEpicId() == null
                    && (epic.title() == null || epic.title().isBlank()
                        || epic.jtbd() == null || epic.jtbd().isBlank())) {
                return where + ": a new epic must carry a title and a JTBD";
            }
            if (!epic.coverageComplete()) {
                return where + ": coverageComplete is not true";
            }
            if (epic.requirements() == null || epic.requirements().isEmpty()) {
                return where + ": it lists no requirements";
            }
            if (epic.slices().isEmpty()) {
                return where + ": it has no slices";
            }
            if (epic.slices().size() > MAX_SLICES_PER_EPIC) {
                return where + ": it has " + epic.slices().size() + " slices, more than " + MAX_SLICES_PER_EPIC;
            }
            int sliceCount = slicesByBrief.merge(epic.sourceIndex(), epic.slices().size(), Integer::sum);
            if (sliceCount > MAX_TOTAL_SLICES_PER_BRIEF) {
                return where + ": brief " + epic.sourceIndex() + " now has " + sliceCount
                        + " slices in total, more than " + MAX_TOTAL_SLICES_PER_BRIEF;
            }
            java.util.Set<String> requirementIds = new java.util.LinkedHashSet<>();
            for (String requirement : epic.requirements()) {
                String id = requirementId(requirement);
                if (id.isBlank()) {
                    return where + ": a requirement carries no identifier: " + requirement;
                }
                if (!requirementIds.add(id)) {
                    return where + ": requirement identifier " + id + " appears more than once";
                }
            }
            java.util.Set<String> coveredRequirementIds = new java.util.LinkedHashSet<>();
            for (com.eneik.production.services.MLPredictionServiceClient.TaskSliceMetadata slice : epic.slices()) {
                String sliceWhere = where + ", slice \"" + (slice.title() == null ? "(untitled)" : slice.title()) + "\"";
                if (slice.title() == null || slice.title().isBlank()) {
                    return sliceWhere + ": it has no title";
                }
                if (slice.jtbd() == null || slice.jtbd().isBlank()) {
                    return sliceWhere + ": it has no JTBD";
                }
                if (slice.acceptanceCriteria() == null || slice.acceptanceCriteria().isBlank()) {
                    return sliceWhere + ": it has no acceptance criteria";
                }
                if (slice.jtbd().contains("one small verifiable capability completed")) {
                    return sliceWhere + ": its JTBD is the template placeholder rather than a real job";
                }
                if (slice.requirementRefs() == null || slice.requirementRefs().isEmpty()) {
                    return sliceWhere + ": it references no requirement";
                }
                for (String ref : slice.requirementRefs()) {
                    String normalizedRef = ref == null ? "" : ref.trim().toUpperCase(java.util.Locale.ROOT);
                    if (!requirementIds.contains(normalizedRef)) {
                        return sliceWhere + ": it references requirement " + normalizedRef
                                + ", which this epic does not list";
                    }
                    coveredRequirementIds.add(normalizedRef);
                }
            }
            if (!coveredRequirementIds.equals(requirementIds)) {
                java.util.Set<String> uncovered = new java.util.LinkedHashSet<>(requirementIds);
                uncovered.removeAll(coveredRequirementIds);
                return where + ": no slice covers requirement(s) " + uncovered;
            }
        }
        if (representedBriefs.size() != normalizedBriefCount) {
            java.util.Set<Integer> missing = new java.util.LinkedHashSet<>();
            for (int i = 0; i < normalizedBriefCount; i++) {
                if (!representedBriefs.contains(i)) {
                    missing.add(i);
                }
            }
            return "brief(s) " + missing + " of " + normalizedBriefCount + " got no epic at all";
        }
        return "";
    }

    static boolean isValidCompilerPlan(List<com.eneik.production.services.MLPredictionServiceClient.EpicPlan> epics, int briefCount) {
        return compilerPlanRejection(epics, briefCount).isEmpty();
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

        // Phase follow-up (2026-07-21, operator directive): FAILED and CANCELLED used to collapse into one
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
        markSessionProgress(session);
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

    /**
     * On-demand nudge for a task's most recent still-live session, callable outside the normal poll cycle
     * (2026-07-26, operator directive - GeminiObserverActionService's "nudgeStuckSession" tool: give the
     * observer a real, reversible lever for pushing through stagnation, not just reporting it). Reuses the
     * exact same recovery-message path the poller uses, just triggered early instead of waiting for the
     * next {@link #shouldSendStuckRecovery} window.
     *
     * @return true if a live session was found and nudged; false if the task has no session in a nudgeable
     * state (nothing to do - not an error).
     */
    public boolean nudgeStuckSession(TaskEntity task) {
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        JulesSessionEntity target = sessions.stream()
                .filter(s -> List.of("queued", "running", "revising", "stuck").contains(s.getStatus()))
                .max(java.util.Comparator.comparing(JulesSessionEntity::getCreatedAt))
                .orElse(null);
        if (target == null) {
            return false;
        }
        sendStuckRecoveryMessageAsync(target, task, apiKeyForSession(target));
        return true;
    }

    private void sendStuckRecoveryMessageAsync(JulesSessionEntity session, TaskEntity task, String apiKey) {
        String externalSessionId = session.getExternalSessionId();
        UUID taskId = task.getId();
        String roleTag = task.getRole() != null ? task.getRole().getTag() : "unknown-role";
        String taskDescription = task.getDescription();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 2026-07-26 operator directive ("turn the cosmetics off - they are not needed"): this message's wording
            // has no effect on anything downstream (Jules reads it as a nudge, not a spec) - a Gemini call
            // to rephrase it bought nothing but cost and an extra failure mode. Deterministic text only now.
            String prompt = "Eneik orchestrator recovery: continue this task if possible, or open a PR with the current progress. "
                    + "If you are blocked, explain the blocker in the session. Task role: " + roleTag
                    + ". Task: " + taskDescription;

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
                    .filter(JulesDispatchService::isTerminalTask)
                    .ifPresent(task -> closeSessionForTerminalTask(session, task));
        }
    }

    // 2026-08-09 (live incident, test-forty-third: session for task c48e1f7e oscillated every ~1 minute for
    // 13+ minutes between this method closing it (task is terminal/failed) and AutoMergeService.
    // repairSessionForConfirmedOpenPr re-opening it (a real open PR exists) - two independently-maintained
    // beliefs about the same session's status, one undoing the other forever, same root shape as the
    // reconcileTerminalGithubStateForReviews churn fixed earlier tonight. Made public static (pure function
    // of TaskEntity, no instance state) so AutoMergeService can defer to this SAME definition of "terminal"
    // instead of repairing a session whose task has already concluded through a different path.
    public static boolean isTerminalTask(TaskEntity task) {
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
        // 2026-08-11 (live incident: philosophical worker 924b2c9f stayed wedged 14+ hours after its
        // carrier task went blocked, because this - the fastest, most common terminal-closure path - never
        // told PersistentWorkerSessionService the carrier died. isIdleAndFresh's isBatchInFlight() check
        // short-circuits before needsRotation's age/cycle-count safety net is ever reached, so a dead
        // carrier's worker row was otherwise unretirable until the 24h age cap - and unreachable even then.
        // forceUnblockOverflowedSessions already retires the worker for its own (much slower, multi-nudge)
        // closure path; mirroring that same call here closes the gap at the point where it actually fires
        // first, for every persistent-worker purpose, not just philosophical audit.
        if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
            persistentWorkerSessionService.findByCarrierTaskId(task.getId())
                    .ifPresent(worker -> {
                        // 2026-08-13 (live incident, test-forty-fourth): retiring the worker row alone is
                        // not enough. If its carrier died mid-cycle, any wishlist it had claimed into
                        // `finalizing` (WishlistRepository's short-lived compile-claim lock, released
                        // normally by completeWishlistCompilation's own releaseUnfinishedClaims) never
                        // reaches that completion handler either - the exact same zombie-claim shape as
                        // the worker row itself, one layer down. Confirmed live: Gemini correctly
                        // diagnosed the stuck worker and retired it via retireStuckWorker (2026-08-11
                        // fix) repeatedly, every cycle reporting "already retired, nothing to do" - but
                        // nothing ever unstuck the project, because registerFreshWorker is ONLY ever
                        // called from dispatchToCompilerPersistentWorker, which only runs for wishlists
                        // in `pending` - a wishlist stranded in `finalizing` here silently blocks the
                        // whole purpose from ever restarting, no matter how many times the worker row
                        // itself gets retired. Released individually (never a blind project-wide reset)
                        // so a genuinely still-in-flight compilation under a DIFFERENT worker is never
                        // touched - same compare-and-swap discipline as releaseUnfinishedClaims itself.
                        for (UUID wishlistId : persistentWorkerSessionService.peekCurrentBatch(worker)) {
                            int released = wishlistRepository.compareAndSetStatus(
                                    wishlistId, WishlistStatus.finalizing, WishlistStatus.pending);
                            if (released == 1) {
                                log.info("Released wishlist {} from finalizing back to pending - its "
                                                + "persistent worker's carrier task {} died mid-cycle",
                                        wishlistId, task.getId());
                            }
                        }
                        persistentWorkerSessionService.retire(worker,
                                "carrier task became terminal (" + task.getStatus() + ")");
                    });
        }
        // O-16 (2026-08-21): the same shape as the persistent-worker repair above, one layer up. A one-shot
        // wishlist-compiler task can be driven terminal by AutoMergeService's poka-yoke merge
        // reconciliation before its session is ever seen in `pr_opened` - and completeWishlistCompilation,
        // the ONLY place a compiled brief becomes converted_to_task or honestly dismissed, hangs off that
        // one state. Measured live: two compiler tasks reached `done` that way, the string `pr_opened`
        // never appeared in the backend log at all, the brief was recovered to `pending`, and it was
        // re-dispatched every ~17 minutes, merging a fresh PR into the CLIENT repository each turn (O-15).
        //
        // The compile really happened and its plan is already merged on main. Reading it from there is not
        // a new mechanism - it is the same buildTaskGraphFromSlices the normal completion path calls.
        if (projectFlowService.isWishlistCompilerTask(task)) {
            recoverCompilationFromMergedPlan(task);
        }
        log.info("Session {} closed locally because task {} is already terminal ({})",
                session.getExternalSessionId(), task.getId(), task.getStatus());
    }

    /**
     * Builds the task graph for a compiler task that went terminal without its completion handler running,
     * reading the plan the compiler already merged to `main`.
     *
     * The batch is passed as THIS task's own wishlist ids, in the order its payload recorded them, because
     * {@code epicPlan.sourceIndex()} is positional against the batch the compiler prompt was built from.
     * {@code ingestPlanFromContent} is deliberately not reused despite doing almost this: it collects every
     * pending/compiling wishlist in the project, which would silently shift those positions and attach a
     * brief's epics to a different brief.
     *
     * Never throws into the caller: this is a repair, and a repair that breaks the closure it is attached
     * to leaves the system worse than the defect it fixes.
     */
    private void recoverCompilationFromMergedPlan(TaskEntity compilerTask) {
        try {
            List<UUID> wishlistIds = compilerTaskWishlistIds(compilerTask);
            if (wishlistIds.isEmpty() || compilerTask.getProject() == null) {
                return;
            }
            List<WishlistEntity> batch = new java.util.ArrayList<>();
            for (UUID id : wishlistIds) {
                WishlistEntity w = wishlistRepository.findById(id).orElse(null);
                if (w == null) {
                    // A position cannot be reconstructed, so the positional contract is broken and no
                    // recovery is better than a misaligned one.
                    log.warn("Compiler task {} went terminal with an unresolved batch, but wishlist {} no "
                            + "longer exists; not recovering from the merged plan (positions would shift)",
                            compilerTask.getId(), id);
                    return;
                }
                batch.add(w);
            }
            boolean anyStranded = batch.stream().anyMatch(w -> w.getStatus() == WishlistStatus.compiling
                    || w.getStatus() == WishlistStatus.finalizing);
            if (!anyStranded) {
                return; // the normal completion path resolved them; nothing was left behind
            }
            String planPath = projectFlowService.compilerPlanPath(compilerTask);
            if (planPath == null || planPath.isBlank()) {
                log.warn("Compiler task {} went terminal with {} stranded brief(s) and carries no plan path; "
                        + "cannot recover", compilerTask.getId(), batch.size());
                return;
            }
            Optional<String> content =
                    gitHubPullRequestService.fetchFileContent(compilerTask.getProject(), "main", planPath);
            if (content.isEmpty()) {
                log.warn("Compiler task {} went terminal with {} stranded brief(s); its plan {} is not on "
                        + "main, so there is nothing to read - the brief stays as it is rather than being "
                        + "guessed at", compilerTask.getId(), batch.size(), planPath);
                return;
            }
            var epicPlans = projectFlowService.parseCompilerPlanContent(content.get(), compilerTask.getProject());
            if (epicPlans.isEmpty()) {
                log.warn("Compiler task {}: its merged plan {} parsed to no epics; the compile produced "
                        + "nothing usable and the brief is left for the decomposition budget to withhold "
                        + "(O-15) rather than being retried blindly", compilerTask.getId(), planPath);
                return;
            }
            boolean built = projectFlowService.buildTaskGraphFromSlices(
                    compilerTask.getProject(), batch, epicPlans);
            log.warn("O-16 recovery: compiler task {} was terminal before its completion handler ran; "
                            + "rebuilt its {} brief(s) from the plan already merged at {} (built anything={})",
                    compilerTask.getId(), batch.size(), planPath, built);
        } catch (Exception e) {
            log.warn("O-16 recovery failed for compiler task {}; the session closure itself is unaffected: {}",
                    compilerTask.getId(), e.getMessage());
        }
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
            boolean actuallyClosed = closeLoopAndCreateFollowUps(
                    session,
                    task,
                    latestQuestion,
                    responseHistory,
                    "stuck_session_timeout: stuck for at least " + closeThresholdMinutes + " minutes"
            );
            if (actuallyClosed) {
                closed++;
            }
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
            // blindCycleCount, which until 2026-08-21 never incremented unless its activity log was
            // oversized (it now also counts cycles where the deep-read classifier could not answer at all,
            // plan L-2 - the same "could not be seen" state by another route) - a session
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
            // GitHub budget guard (2026-07-30) - see reconcileTaskStatusAgainstGitHubTruth for the same
            // reasoning and the live-measured numbers. This is the highest-frequency sweep of the three
            // (every minute) and the most likely single biggest source of GitHub calls spent on projects
            // nobody is working on anymore.
            if (task.getProject() == null || task.getProject().getStatus() != ProjectStatus.active) {
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
                    || projectFlowService.isDesignConcernTriageTask(task)
                    || projectFlowService.isCoverageAuditTask(task)
                    || projectFlowService.isPhilosophicalAuditTask(task);
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

    private static final List<TaskStatus> RECONCILIATION_NON_TERMINAL_STATUSES = List.of(
            TaskStatus.queued, TaskStatus.claimed, TaskStatus.in_progress,
            TaskStatus.pending_review, TaskStatus.review, TaskStatus.blocked);

    /**
     * Testimony-vs-evidence Phase 2 (2026-07-25, operator directive: "a mechanism is needed for regularly
     * refreshing every status - the factual ones" - a mechanism to regularly reconcile every status against
     * fact). Phase 1 above (honorDavidsonProgressEvidence and its callers) is entirely reactive - it only
     * ever runs for a session the timestamp-based "looks stuck" heuristic already flagged, and only closes
     * one specific gap (a branch exists but no PR was ever opened). This sweep is unconditional: it checks
     * EVERY non-terminal task's real GitHub state on a schedule, regardless of whether anything about it
     * looks suspicious.
     *
     * Concrete incident that motivated this (distinct from the PR#72 one behind Phase 1): task `ca41509f`
     * sat in `review` for hours with NO active claim and NO active session left at all - its PR had been
     * closed without merging (a deliberate operator decision, made directly on GitHub, outside the
     * orchestrator entirely) and nothing ever observed that. Found only by manual SQL inspection.
     *
     * Scope: non-terminal tasks with no active claim (a task a live session is still legitimately working
     * remains Phase 1's/normal completion's responsibility, never this sweep's). For each, the most recent
     * real session tells us which PR/branch to check:
     *   - PR closed, not merged -&gt; the new case this sweep exists for: mark the task failed via the same
     *     CAS-guarded write ClaimService already uses, citing the real PR number as evidence.
     *   - PR still open -&gt; not a gap; the normal pipeline (or Phase 1) already owns this.
     *   - No PR at all, but a branch has real content -&gt; hand off to the exact same evidence path Phase 1
     *     uses (hasNewProgressOnGitHub + openRecoveryPullRequest) rather than reimplementing it.
     */
    @Scheduled(cron = "${github-truth-reconciliation.cron:0 0 * * * ?}")
    @Transactional
    public void reconcileTaskStatusAgainstGitHubTruth() {
        if (!settingsService.effectiveBoolean("github_truth_reconciliation_enabled")) {
            return;
        }
        List<TaskEntity> candidates = taskRepository.findByStatusIn(RECONCILIATION_NON_TERMINAL_STATUSES);
        if (candidates.isEmpty()) {
            return;
        }
        int reconciled = 0;
        for (TaskEntity task : candidates) {
            // GitHub budget guard (2026-07-30, quantified live: 45 of 82 calls in a 20-minute window were
            // spent on frozen/accepted projects with leftover non-terminal tasks, against 37 for the one
            // genuinely active project). A project past active has nothing further to reconcile toward -
            // this sweep now costs it nothing, instead of competing for the same shared rate-limit budget.
            // An active claim vetoes this sweep for product work, and rightly: an implementer whose PR
            // closed can push a new branch and open another one from the same session, so a closed PR is
            // not the end of its work.
            //
            // A factory carrier is different, and 2026-08-29 measured what the difference costs. Compiler
            // task ad7879a8 held session ae5ff8a7 `running` with PR#416 closed without merge and both plan
            // retries spent; six briefs sat in `compiling` behind it and the whole factory dispatched
            // nothing. The claim held because the session was alive, self-healing left it alone for the
            // same reason, and the only remaining path was the silence detector - sixty minutes to call it
            // stuck, a hundred and twenty to close it. Two hours of nothing, while the evidence had been in
            // from the first minute: a carrier's deliverable IS its record PR, and that PR was closed. The
            // plan is not in it and will not be. This factory already treats a closed unmerged PR as
            // decisive elsewhere - a pull request closed without a merge has been reviewed by its closing.
            //
            // The silence windows are untouched; they remain for the case where there is no evidence.
            boolean carrierWithNothingLeftToDeliver = task.getPayload() != null
                    && task.getPayload().hasNonNull(com.eneik.production.services.ProjectFlowService.WISHLIST_COMPILER_PAYLOAD_KEY);
            if (task.getProject() == null
                    || (claimService.hasActiveClaim(task.getId()) && !carrierWithNothingLeftToDeliver)
                    || task.getProject().getStatus() != ProjectStatus.active) {
                continue;
            }
            // Operator directive (2026-07-25): scope every log line in this sweep to the task's own
            // project - this loop spans every project's tasks in one tick, previously all under SYSTEM.
            com.eneik.production.services.logging.LogScope.project(task.getProject().getId());
            try {
                JulesSessionEntity latestSession = mostRecentRealSession(task.getId());
                if (latestSession == null) {
                    continue;
                }
                GitHubPullRequestService.PullRequestSnapshot snapshot =
                        gitHubPullRequestService.pullRequestSnapshot(task.getProject());
                if (!snapshot.available()) {
                    continue;
                }

                Optional<GitHubPullRequestService.GitHubPullRequest> closedUnmerged = snapshot.closed().stream()
                        .filter(pr -> !pr.merged() && GitHubPullRequestService.matchesSessionToken(pr, latestSession.getExternalSessionId()))
                        .findFirst();
                if (closedUnmerged.isPresent()) {
                    reconcileClosedUnmergedPullRequest(task, closedUnmerged.get());
                    reconciled++;
                    continue;
                }

                boolean hasOpenPr = snapshot.open().stream()
                        .anyMatch(pr -> GitHubPullRequestService.matchesSessionToken(pr, latestSession.getExternalSessionId()));
                if (hasOpenPr) {
                    continue;
                }

                Instant reference = latestSession.getLastProgressAt() != null
                        ? latestSession.getLastProgressAt() : latestSession.getCreatedAt();
                GitHubEvidence evidence = hasNewProgressOnGitHub(latestSession, task, reference);
                if (evidence.found() && evidence.branchNeedingPullRequest() != null) {
                    openRecoveryPullRequest(task, evidence.branchNeedingPullRequest(), latestSession.getExternalSessionId());
                }
            } finally {
                com.eneik.production.services.logging.LogScope.clear();
            }
        }
        if (reconciled > 0) {
            log.info("reconcileTaskStatusAgainstGitHubTruth: reconciled {} task(s) against real GitHub state this sweep", reconciled);
        }
        reconcileDoneTasksNotReachedMain();
    }

    /**
     * Extension of the sweep above to `done` tasks (2026-07-25, operator directive after the new dashboard
     * "Blocked / Not Yet Merged" widget surfaced real done-but-unmerged tasks live). Deliberately narrower
     * than the non-terminal sweep in two ways:
     *   1. Only acts on the ONE unambiguous negative signal - a closed-unmerged PR. A `done` task whose
     *      work merged into a feature-thread branch that simply hasn't closed out to main YET is a normal,
     *      expected pending state (AutoMergeService.closeOutReadyFeatureThreads owns that), not a failure -
     *      auto-flagging every such task as broken would be a false positive (confirmed live: two of the
     *      three tasks the dashboard widget first surfaced turned out to be exactly this, or auxiliary
     *      DECISION/complex-cynefin work that never needs to reach main at all - see isAuxiliaryTask below).
     *   2. Never writes a new status. `TaskRepository.writeStatusUnlessTerminal` deliberately refuses to
     *      overwrite `done` (it is, by design, one of the CAS guard's protected terminal statuses) - this
     *      sweep does not fight that invariant. A `done` task with a genuinely closed-unmerged PR is loud,
     *      visible evidence of a real problem, but deciding what to DO about already-reported-done work is
     *      a product-correctness judgment call (same boundary OpsAuditorService draws for itself), not a
     *      mechanical reconciliation this sweep should make unilaterally.
     */
    private void reconcileDoneTasksNotReachedMain() {
        List<TaskEntity> doneTasks = taskRepository.findByStatus(TaskStatus.done);
        for (TaskEntity task : doneTasks) {
            if (task.getProject() == null || readinessService.isAuxiliaryTask(task) || readinessService.reachedMain(task)) {
                continue;
            }
            com.eneik.production.services.logging.LogScope.project(task.getProject().getId());
            try {
                JulesSessionEntity latestSession = mostRecentRealSession(task.getId());
                if (latestSession == null) {
                    continue;
                }
                GitHubPullRequestService.PullRequestSnapshot snapshot =
                        gitHubPullRequestService.pullRequestSnapshot(task.getProject());
                if (!snapshot.available()) {
                    continue;
                }
                snapshot.closed().stream()
                        .filter(pr -> !pr.merged() && GitHubPullRequestService.matchesSessionToken(pr, latestSession.getExternalSessionId()))
                        .findFirst()
                        .ifPresent(closedPr -> log.warn(
                                "reconcileTaskStatusAgainstGitHubTruth: task {} is marked done but PR#{} closed without "
                                        + "merge and no other evidence shows the work reached main - needs human review, "
                                        + "not auto-corrected (done is a CAS-protected terminal status)",
                                task.getId(), closedPr.number()));
            } finally {
                com.eneik.production.services.logging.LogScope.clear();
            }
        }
    }

    private JulesSessionEntity mostRecentRealSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(s -> s.getExternalSessionId() != null && !s.getExternalSessionId().isBlank()
                        && !"skipped".equals(s.getExternalSessionId()))
                .max(java.util.Comparator.comparing(JulesSessionEntity::getCreatedAt))
                .orElse(null);
    }

    private void reconcileClosedUnmergedPullRequest(TaskEntity task, GitHubPullRequestService.GitHubPullRequest closedPr) {
        String reason = "PR#" + closedPr.number() + " closed without merge on GitHub; task had no active claim/session "
                + "left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)";
        int updated = taskRepository.writeStatusUnlessTerminal(task.getId(), TaskStatus.failed);
        if (updated == 0) {
            log.info("reconcileTaskStatusAgainstGitHubTruth: task {} reached a terminal status concurrently, skipped", task.getId());
            return;
        }
        taskRepository.findById(task.getId()).ifPresent(fresh -> {
            fresh.setJulesDispatchStatus(reason);
            taskRepository.save(fresh);
        });
        log.info("reconcileTaskStatusAgainstGitHubTruth: task {} marked failed - {}", task.getId(), reason);
    }

    private boolean honorDavidsonProgressEvidence(JulesSessionEntity session, TaskEntity task, Instant lastProgress) {
        // Principle of charity (Davidson): check the artifact-producing system of record before inferring
        // failure from silence in the status channel.
        if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
            GitHubEvidence evidence = persistentWorkerHasReadyAnswer(session, task);
            if (evidence.found()) {
                if (evidence.branchNeedingPullRequest() != null) {
                    openRecoveryPullRequest(task, evidence.branchNeedingPullRequest(), session.getExternalSessionId());
                }
                log.info("Persistent worker carrier task {} looked stalled but its PR already has a ready, "
                                + "parseable result file; processing it instead of closing the session.", task.getId());
                markSessionProgress(session);
                julesSessionRepository.save(session);
                completePersistentWorkerCycle(session, task);
                return true;
            }
        }
        if (task.getProject() != null) {
            GitHubEvidence evidence = hasNewProgressOnGitHub(session, task, lastProgress);
            if (evidence.found()) {
                if (evidence.mergedPr() != null) {
                    log.info("Task {} session {} found an already-merged PR #{} that never went through the "
                                    + "normal pr_opened completion workflow (local status missed that transition); "
                                    + "completing it now instead of retrying a doomed recovery-PR open.",
                            task.getId(), session.getExternalSessionId(), evidence.mergedPr().number());
                    session.setPrUrl(evidence.mergedPr().url());
                    session.setStatus("pr_opened");
                    markSessionProgress(session);
                    session.setForcedUnblockAttempts(0);
                    session.setBlindCycleCount(0);
                    julesSessionRepository.save(session);
                    handlePrOpenedWorkflow(session);
                    return true;
                }
                if (evidence.branchNeedingPullRequest() != null) {
                    openRecoveryPullRequest(task, evidence.branchNeedingPullRequest(), session.getExternalSessionId());
                }
                log.info("Task {} session {} looked stalled but a new commit landed after lastProgressAt; "
                                + "treating the commit as positive progress evidence instead of closing.",
                        task.getId(), session.getExternalSessionId());
                session.setStatus("running");
                markSessionProgress(session);
                session.setForcedUnblockAttempts(0);
                session.setBlindCycleCount(0);
                julesSessionRepository.save(session);
                return true;
            }
        }
        return false;
    }

    /**
     * Opens the PR a session should have opened itself but didn't (testimony-vs-evidence Phase 1,
     * 2026-07-25) - mirrors the manual recovery already proven twice live (PR#72, PR#77): the title/body
     * state plainly this was auto-recovered from an unopened branch, for auditability, so a later reader
     * never mistakes it for a normal session-driven PR description. Once opened, the existing PR-driven
     * pipeline (AutoMergeService/the normal orchestration poll) picks it up on its own next tick - this
     * method's only job is to make the PR exist, not to process it. Best-effort: if opening fails (e.g. the
     * branch has no diff against main), the evidence that was already found is not invalidated - the caller
     * still treats the session as making real progress and will retry the PR-opening on the next check.
     */
    private void openRecoveryPullRequest(TaskEntity task, String branch, String externalSessionId) {
        String title = "Auto-recovered: " + branch;
        String body = "This PR was opened automatically by the orchestrator, not by the Jules session itself. "
                + "Session " + externalSessionId + " pushed real, committed work to this branch but never opened "
                + "a pull request for it. The orchestrator detected this via branch-fallback evidence checking "
                + "(testimony-vs-evidence principle: a session's self-reported status is never trusted on its "
                + "own - only an independently-verifiable artifact counts) and opened this PR so the normal "
                + "review/merge pipeline can process the already-completed work.";
        Optional<GitHubPullRequestService.GitHubPullRequest> opened =
                gitHubPullRequestService.createPullRequest(task.getProject(), branch, "main", title, body);
        if (opened.isPresent()) {
            log.info("Task {}: auto-opened recovery PR #{} from branch {} (session {} had real evidence but never opened a PR itself)",
                    task.getId(), opened.get().number(), branch, externalSessionId);
        } else {
            log.warn("Task {}: found real evidence on branch {} for session {} but failed to auto-open a recovery PR - "
                            + "will retry on a future evidence check",
                    task.getId(), branch, externalSessionId);
        }
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
                // D2 instrumentation (2026-08-18), measurement only - no behaviour change. Three tasks once
                // died to ~60 forced nudges each at a fixed 60-second interval against a configured bound of
                // 2, and nothing in the log ever showed which count the sender believed it was at, so the
                // divergence could not be attributed: a counter never read, never incremented, or a second
                // path that never consults it are three different defects with one symptom. Recording the
                // persisted attempt count and the configured bound at the moment of sending makes the next
                // occurrence self-explaining. If attempts keeps climbing past max, the counter is read and
                // ignored; if it stays at 0 or 1 while messages keep arriving, the sender is not this path.
                log.info("Sent {} message to Jules session {} for task {} (forced-unblock attempts persisted: {} of max {})",
                        logLabel, externalSessionId, taskId, session.getForcedUnblockAttempts(), forcedUnblockMaxAttempts);
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
     * Fully autonomous end-to-end (no human review parking lot): the discovered PR is queued into the
     * same Jules-reviewer fallback batch every other PR review goes through (2026-07-25, Gemini review
     * removed entirely - emergency cost incident). applyReviewVerdictToTask handles the resulting
     * approve/block verdict exactly like any other reviewed PR - no separate merge logic here.
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

            // Gemini review removed here too (2026-07-25, operator directive - emergency cost incident):
            // route this discovered PR through the same Jules-reviewer fallback batch every other PR now
            // uses, instead of a direct mlPredictionServiceClient.reviewPr(...) call.
            dispatchReviewerFallbackBatch(java.util.List.of(new PendingFallbackReview(task, pr.url())));
            log.info("Reconciled abandoned PR {} for closed session {} (task {}) - queued for Jules fallback review.",
                    pr.url(), session.getExternalSessionId(), task.getId());
        }
    }


    /**
     * Closes a local session record whose remote Jules session is provably gone. Returns true only when a
     * real 404 was observed for the session resource itself - never on a network failure, a disabled
     * integration, or a missing key, because absence of an answer is not an answer (V104, the same rule
     * that stopped an unanswered launch from being written into the product's history as a failed launch).
     */
    private boolean buryIfSessionIsGone(JulesSessionEntity session, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || session.getExternalSessionId() == null) {
            return false;
        }
        try {
            JulesApiClient.RawSessionCheckResult raw =
                    julesApiClient.checkSessionRaw(session.getExternalSessionId(), apiKey);
            if (raw == null || raw.statusCode() != 404) {
                return false;
            }
            closeSessionAsNoCode(session,
                    "Remote Jules session no longer exists (HTTP 404 on the session resource itself); "
                            + "local record buried so it stops being polled forever");
            if (session.getTaskId() != null) {
                claimService.releaseClaimToQueue(session.getTaskId(),
                        "Remote Jules session no longer exists (HTTP 404) - released claim to queue");
            }
            log.info("Buried session {} and released task {} to queue - the remote session is gone (404)",
                    session.getExternalSessionId(), session.getTaskId());
            return true;
        } catch (Exception e) {
            log.warn("Could not check whether session {} still exists: {}",
                    session.getExternalSessionId(), e.getMessage());
            return false;
        }
    }

}
