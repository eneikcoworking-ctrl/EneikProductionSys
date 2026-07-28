package com.eneik.production.services;

import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.FeatureThreadEntity;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.logging.LogScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

@Service
public class AutoMergeService {
    private static final Logger log = LoggerFactory.getLogger(AutoMergeService.class);
    private static final String APPROVAL_TOKEN = "CORE ARCHITECTURE VERIFIED. APPROVED.";
    private static final String EARLY_UNBLOCK_SPEC_NOTIFIED_KEY = "earlyUnblockedSpecNotified";
    private final PrReviewRepository prReviewRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.repositories.TaskRepository taskRepository;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService;
    private final TaskConflictRepository taskConflictRepository;
    private final JulesDispatchService julesDispatchService;
    private final RoleCapabilityLoader roleCapabilityLoader;
    private final WishlistRepository wishlistRepository;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.video.VideoAssetService videoAssetService;
    private final com.eneik.production.services.dashboard.ProjectOperationalContextService contextService;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final CodeChangeClassifier codeChangeClassifier;
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private final ClaimService claimService;
    private final com.eneik.production.repositories.ProjectRepository projectRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final GeminiContextService geminiContextService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.toc.service.TocSentinelService tocSentinelService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.automerge.ConflictEntropyCalculator conflictEntropyCalculator;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;

    public AutoMergeService(PrReviewRepository prReviewRepository,
                            com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                            com.eneik.production.repositories.TaskRepository taskRepository,
                            SystemSettingsService settingsService,
                            ObjectMapper objectMapper,
                            com.eneik.production.services.advice.RoleAdviceLoopService roleAdviceLoopService,
                            TaskConflictRepository taskConflictRepository,
                            JulesDispatchService julesDispatchService,
                            RoleCapabilityLoader roleCapabilityLoader,
                            WishlistRepository wishlistRepository,
                            MLPredictionServiceClient mlPredictionServiceClient,
                            GitHubPullRequestService gitHubPullRequestService,
                            com.eneik.production.services.video.VideoAssetService videoAssetService,
                            com.eneik.production.services.dashboard.ProjectOperationalContextService contextService,
                            com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker,
                            CodeChangeClassifier codeChangeClassifier,
                            com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                            ClaimService claimService,
                            com.eneik.production.repositories.ProjectRepository projectRepository,
                            ClientDeliverableReadinessService readinessService,
                            GeminiContextService geminiContextService) {
        this.prReviewRepository = prReviewRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.roleAdviceLoopService = roleAdviceLoopService;
        this.taskConflictRepository = taskConflictRepository;
        this.julesDispatchService = julesDispatchService;
        this.roleCapabilityLoader = roleCapabilityLoader;
        this.wishlistRepository = wishlistRepository;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.videoAssetService = videoAssetService;
        this.contextService = contextService;
        this.systemProgressTracker = systemProgressTracker;
        this.codeChangeClassifier = codeChangeClassifier;
        this.featureThreadRepository = featureThreadRepository;
        this.claimService = claimService;
        this.projectRepository = projectRepository;
        this.readinessService = readinessService;
        this.geminiContextService = geminiContextService;
    }

    @Scheduled(fixedRateString = "${automerge.rate-ms:60000}")
    public void processAutoMerge() {
        if (tocSentinelService != null) {
            com.eneik.production.toc.model.TocToken token = tocSentinelService.startExecution("AUTOMERGE_CYCLE", 40);
            if (token.getStatus() == com.eneik.production.toc.model.TocToken.TokenStatus.THROTTLED) {
                log.info("[AUTOMERGE] Cycle throttled by TOC Sentinel DBR Rope due to constraint buffer overflow.");
                return;
            }
            try {
                tocSentinelService.enterStep(token, "AUTOMERGE_PROCESSING");
                executeAutoMergeCycle();
            } finally {
                tocSentinelService.exitStep(token, "AUTOMERGE_PROCESSING", true);
                tocSentinelService.endExecution(token, true);
            }
        } else {
            executeAutoMergeCycle();
        }
    }

    public void executeAutoMergeCycle() {
        syncOpenPullRequestsFromGitHub();
        reconcileMergedTaskOutcomes();
        reconcileMergedGitHubPullRequests();
        reconcileCleanOpenGitHubPullRequests();
        reconcileLocalMockTasksWithoutReviews();
        resurrectTriviallyEscalatedConflicts();
        resurrectEscalatedConflictsWithRealCode();
        resurrectAlreadyMergedReviews();
        closeOutReadyFeatureThreads();
        List<PrReviewEntity> pendingReviews = prReviewRepository.findByMergedFalseOrMergedIsNull().stream()
                .filter(AutoMergeService::isReviewPollCandidate)
                .filter(r -> !isAlreadyResolvedSpike(r))
                .toList();

        for (PrReviewEntity review : pendingReviews) {
            boolean isChaotic = false;
            com.eneik.production.models.persistence.TaskEntity task = null;
            if (review.getJulesSessionId() != null) {
                var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
                if (sessionOpt.isPresent()) {
                    var taskOpt = taskRepository.findById(sessionOpt.get().getTaskId());
                    if (taskOpt.isPresent()) {
                        task = taskOpt.get();
                        isChaotic = "chaotic".equalsIgnoreCase(task.getCynefinDomain());
                    }
                }
            }

            if (task != null && task.getProject() != null) {
                LogScope.project(task.getProject().getId());
            } else {
                LogScope.system();
            }
            try {
                if (task != null && reconcileReviewAgainstTaskOutcome(review, task)) {
                    continue;
                }
                if (isChaotic) {
                    // Cynefin "chaotic" domain: act first to stabilize, sense/respond afterward — the merge
                    // proceeds on green CI alone, and executeMerge() below unconditionally records a
                    // high-priority chaotic_debt wishlist item so the bypass is always followed up on review.
                    executeMerge(review);
                } else if (review.getDiffSummary() != null && review.getDiffSummary().contains(APPROVAL_TOKEN)) {
                    executeMerge(review);
                }
            } finally {
                LogScope.clear();
            }
        }
    }

    // Systemic fix (2026-07-23, confirmed live on test-thirty-fifth, task 17e3f9ae / PR#28): "conflict" used
    // to be EXCLUDED here, alongside genuinely terminal statuses like "unowned"/"invalid_pr"/"superseded".
    // That was a real self-inflicted deadlock, not a deliberate terminal state: handleMergeConflict sets
    // ciStatus="conflict" the moment it detects a conflict and asks Jules to resolve it in place - but the
    // ONLY code that re-attempts the real merge (which both verifies whether that fix actually worked
    // AND, on a fresh 405/409, drives the next resolution attempt) is executeMerge, which this exact filter
    // prevented from ever running again for that review. Confirmed live: Jules pushed a conflict-resolution
    // commit and CI went green, but GitHub's own mergeable check still reported a real conflict afterward -
    // and the review sat invisible to executeMerge from that point on, so nothing ever re-checked GitHub's
    // verdict or asked for a second attempt. There is no separate "did the fix actually work" check to add -
    // the real merge attempt against live GitHub IS that check, it just needs to be allowed to run again.
    // "conflict" must stay a poll candidate for as long as its TaskConflictEntity is still "pending" (i.e.
    // within handleMergeConflict's own 3-attempt cap) - see the "escalated" terminal status set
    // once that cap is reached, which correctly falls OUT of this candidate set once retries are genuinely
    // exhausted, instead of retrying (or silently sitting stuck) forever.
    static boolean isReviewPollCandidate(PrReviewEntity review) {
        String ciStatus = review.getCiStatus();
        if (ciStatus == null || ciStatus.isBlank()) {
            return true;
        }
        return java.util.Set.of("success", "pending", "unavailable", "conflict")
                .contains(ciStatus.toLowerCase(java.util.Locale.ROOT));
    }

    // Operator directive 2026-07-24 (live incident, 6 simultaneous escalations on test-thirty-seventh, all
    // traced to orchestrator-owned files not real code - "чинить это сейчас и навсегда"): the 3-attempt
    // escalation path above is correct to stop burning Jules retries on a conflict that keeps failing, but
    // once ciStatus="escalated" the review is permanently excluded from isReviewPollCandidate - a genuine
    // dead end even for the class of conflict the widened fast path (see handleMergeConflict) can now
    // resolve for free. This runs every processAutoMerge tick (cheap - escalated rows are rare by
    // definition) and gives exactly that class one more chance: re-fetches the PR's CURRENT file list from
    // GitHub and syncs whichever files fall in the auto-resolvable set (`.eneik/*`, root `.gitignore`) to
    // main, then re-queues the review for a normal merge attempt next cycle.
    //
    // Same correction as Ф1 in handleMergeConflict's fast path (2026-07-21) applies here, and was missed in
    // the first version of this method: `fetchPrFiles` returns the PR's FULL changed-file list, not just
    // the files actually in conflict - a real feature PR always has dozens of legitimate Java/SQL files
    // alongside the one or two stray orchestrator files, so requiring `allMatch` on the WHOLE list meant
    // this never fired for any real PR (confirmed live: zero resurrections in the first ~5 minutes after
    // deploy, all 6 escalated PRs have 20-35 real files each). Fixed to act on the auto-resolvable SUBSET
    // regardless of what else changed - real code is never touched by this path, and if the actual conflict
    // turns out to be in one of those other files too, the next merge attempt simply fails 405 again and
    // falls straight back through the normal rebase/escalation path, so widening this is safe.
    private void resurrectTriviallyEscalatedConflicts() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return;
        }
        List<PrReviewEntity> escalated = prReviewRepository.findByMergedFalseOrMergedIsNull().stream()
                .filter(r -> "escalated".equalsIgnoreCase(r.getCiStatus()))
                .toList();
        for (PrReviewEntity review : escalated) {
            try {
                PullRequestTarget target = resolvePullRequestTarget(review);
                if (target == null || review.getJulesSessionId() == null) {
                    continue;
                }
                List<String> allFiles = fetchPrFiles(token, target.owner(), target.repo(), target.pullNumber());

                if (conflictEntropyCalculator != null) {
                    var entropyResult = conflictEntropyCalculator.calculateEntropy(allFiles);
                    log.info("[AUTOMERGE-ENTROPY] Conflict entropy H(C) = {} for PR {} (Trivial: {})",
                            entropyResult.shannonEntropy(), target.pullNumber(), entropyResult.isTrivialOrchestratorConflict());
                }

                List<String> files = allFiles.stream()
                        .filter(f -> f.startsWith(".eneik/") || f.equals(".gitignore"))
                        .toList();
                if (files.isEmpty()) {
                    continue;
                }
                var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
                if (sessionOpt.isEmpty()) {
                    continue;
                }
                UUID taskId = sessionOpt.get().getTaskId();
                var taskOpt = taskRepository.findById(taskId);
                if (taskOpt.isEmpty()) {
                    continue;
                }
                var task = taskOpt.get();
                // 2026-07-26: same guard as resurrectEscalatedConflictsWithRealCode below - a frozen/
                // accepted/cancelled project gets no further automated work, not even a housekeeping-file
                // sync commit.
                if (task.getProject() == null
                        || task.getProject().getStatus() != com.eneik.production.models.persistence.ProjectStatus.active) {
                    continue;
                }
                String branch = gitHubPullRequestService.fetchPullRequestByNumber(
                                task.getProject(), Integer.parseInt(target.pullNumber()))
                        .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                        .orElse(null);
                if (branch == null) {
                    continue;
                }
                boolean allResolved = files.stream()
                        .allMatch(f -> gitHubPullRequestService.resolveFileConflictWithMain(task.getProject(), branch, f));
                if (!allResolved) {
                    log.warn("AutoMergeService: resurrection attempt for escalated PR {} failed to sync all "
                            + "orchestrator-owned files; leaving escalated", review.getPrUrl());
                    continue;
                }
                taskConflictRepository.findFirstByTaskIdAndResolutionStatus(taskId, "escalated").ifPresent(c -> {
                    c.setResolutionStatus("pending");
                    c.setResolutionAttempts(0);
                    taskConflictRepository.save(c);
                });
                review.setCiStatus("conflict");
                prReviewRepository.save(review);
                log.info("Poka-yoke: resurrected escalated review for task {} (PR {}) - its conflict was entirely "
                                + "in orchestrator-owned files ({}), now synced to main and re-queued for a normal "
                                + "merge attempt next cycle; no Jules session spent.",
                        taskId, review.getPrUrl(), files);
            } catch (Exception e) {
                log.warn("AutoMergeService: error while attempting to resurrect escalated review {}: {}",
                        review.getPrUrl(), e.getMessage());
            }
        }
    }

    /**
     * Real-code escalation resurrection (2026-07-25, operator: "мы сделали проверку на истинность статусов,
     * но автомерж работает плохо" - concrete live incident, PR#87/task e4b1bc9e). resurrectTriviallyEscalated
     * Conflicts above only ever resurrects a conflict whose files are ENTIRELY orchestrator-owned (`.eneik/`,
     * root `.gitignore`) - its own `if (files.isEmpty()) continue;` guard is unreachable whenever the
     * conflict touches real product code, which is the common case. handleMergeConflict's own escalation
     * comment claims it will "spawn one fresh atomic recovery task" once 3 in-place attempts fail - it never
     * actually did; escalation just marks the review `ciStatus=escalated` and stops. Confirmed live: PR#87
     * (frontend/src/App.svelte, a genuine 6-line textual conflict) exhausted 3 in-place attempts against the
     * SAME session and was found stuck with zero further automated activity only by manual SQL inspection.
     *
     * Fix: give every escalated real-code conflict exactly ONE more try, on a BRAND NEW session (not the
     * exhausted original - a fresh session, unburdened by whatever context confused the first 3 in-place
     * attempts, is strictly more likely to succeed), via the same dispatchAdHocSessionToBranch mechanism
     * already proven for PR#67's flaky-test fix. Bounded to one attempt ever per conflict
     * (resolutionStatus transitions escalated -&gt; escalated_fresh_dispatch, a distinct terminal value so
     * this method's own `"escalated".equalsIgnoreCase(...)` filter never re-selects it) - if this fresh
     * attempt also fails to produce a mergeable PR, that is a genuine case for a human, not another retry
     * loop.
     */
    private void resurrectEscalatedConflictsWithRealCode() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        List<TaskConflictEntity> escalated = taskConflictRepository.findAll().stream()
                .filter(c -> "escalated".equalsIgnoreCase(c.getResolutionStatus()))
                .toList();
        for (TaskConflictEntity conflict : escalated) {
            try {
                // 2026-07-26 fix: conflict.getTask() is a Hibernate proxy from a repository call whose own
                // transaction/session already closed by the time this loop body runs (processAutoMerge, the
                // @Scheduled entry point, is deliberately NOT @Transactional - this tick makes several slow
                // GitHub HTTP calls per iteration, and holding one DB transaction open across all of them
                // for the whole tick would be worse). Accessing the proxy's id alone is safe (Hibernate
                // proxies always know their own id without hitting the DB), but any real field access
                // (.getProject(), .getTitle()) on it throws "could not initialize proxy - no Session" -
                // confirmed live on every restart, logged as a caught warning for several conflicts. Fetch a
                // fully-initialized TaskEntity explicitly instead of dereferencing the stale proxy.
                UUID conflictTaskId = conflict.getTask() != null ? conflict.getTask().getId() : null;
                com.eneik.production.models.persistence.TaskEntity task = conflictTaskId != null
                        ? taskRepository.findById(conflictTaskId).orElse(null)
                        : null;
                if (task == null || task.getProject() == null) {
                    continue;
                }
                // 2026-07-26 live incident (found by the operator minutes after the Hibernate fix above
                // shipped): this sweep never checked project status at all - it had been silently throwing
                // before reaching this point for months, which accidentally "protected" frozen/accepted
                // projects from unwanted new work. The moment the Hibernate bug was fixed, this fired for
                // real against a frozen project (test-thirty-second) and an already-ACCEPTED one
                // (test-thirty-third) - dispatching a brand new Jules session to push a fix into a project
                // the client already took delivery of. Never resurrect conflicts for a project that isn't
                // active - a frozen/accepted/cancelled project gets no further automated work, period.
                if (task.getProject().getStatus() != com.eneik.production.models.persistence.ProjectStatus.active) {
                    log.info("Poka-yoke: skipping real-code resurrection for conflict {} (task {}) - project {} is {}, not active",
                            conflict.getId(), task.getId(), task.getProject().getId(), task.getProject().getStatus());
                    continue;
                }
                PullRequestTarget target = parseGithubPullRequestUrl(conflict.getPrUrl());
                if (target == null) {
                    continue;
                }
                String branch = gitHubPullRequestService.fetchPullRequestByNumber(
                                task.getProject(), Integer.parseInt(target.pullNumber()))
                        .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                        .orElse(null);
                if (branch == null) {
                    continue;
                }

                List<String> files;
                try {
                    files = objectMapper.readValue(conflict.getConflictingFiles(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {
                    files = List.of();
                }
                String fileList = files.isEmpty() ? "(file list unavailable - inspect the PR diff directly)" : String.join(", ", files);

                String instruction = "Your previous session's PR (" + conflict.getPrUrl() + ", branch " + branch
                        + ") has a real merge conflict with main in: " + fileList + ". Three in-place resolution "
                        + "attempts by the original session did not produce a mergeable PR, so this is a fresh "
                        + "session with no prior context. Check out this exact branch, merge (or rebase onto) "
                        + "the latest main, resolve the conflict(s) directly in these files - keep this branch's "
                        + "own feature work, reconcile with whatever main added since - verify the build/tests "
                        + "still pass, and push the fix to this SAME branch. Do not open a new PR; do not revert "
                        + "or remove the feature this branch implements.";

                julesDispatchService.dispatchAdHocSessionToBranch(task.getProject(), branch, instruction,
                        "Conflict resolution (fresh session): " + task.getTitle());

                conflict.setResolutionStatus("escalated_fresh_dispatch");
                taskConflictRepository.save(conflict);
                log.info("Poka-yoke: escalated conflict for task {} (PR {}) involved real code ({}) - dispatched "
                                + "ONE fresh session (unrelated to the exhausted original) to resolve it in place; "
                                + "this conflict will not be retried again automatically.",
                        task.getId(), conflict.getPrUrl(), fileList);
            } catch (Exception e) {
                log.warn("AutoMergeService: error while attempting real-code resurrection for conflict {} (task {}): {}",
                        conflict.getId(), conflict.getTask() != null ? conflict.getTask().getId() : null, e.getMessage());
            }
        }
    }

    // Operator directive 2026-07-24 (live incident: a feature-thread branch accumulates every subsequent
    // task's merges but NOTHING ever opened a PR from it back to main - confirmed live, 11 of 25 merged
    // PRs on a real project sat in 4 different thread branches, diverged from main by dozens of commits
    // each, indefinitely). Self-contained by design: does NOT touch PrReviewEntity/JulesSessionEntity at
    // all, tracked only via FeatureThreadEntity.closeoutPrUrl - reusing the normal processAutoMerge
    // review/session pipeline for a closeout PR was verified unsafe (its head branch is the last task's own
    // Jules branch name, so syncOpenPullRequestsFromGitHub's session matcher would silently overwrite that
    // already-terminal session's prUrl to point at the closeout PR instead, while the closeout PR itself
    // never gets a PrReviewEntity and so never enters the merge loop - it would sit open forever while
    // corrupting unrelated session data as a side effect).
    private void closeOutReadyFeatureThreads() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        List<com.eneik.production.models.persistence.ProjectEntity> activeProjects = projectRepository
                .findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
        for (com.eneik.production.models.persistence.ProjectEntity project : activeProjects) {
            for (FeatureThreadEntity thread : featureThreadRepository.findByProjectIdAndMergedToMainAtIsNullAndAbandonedAtIsNull(project.getId())) {
                try {
                    // Continuous drift prevention (Part 3): keep every still-open thread from wandering far
                    // from main before its eventual closeout - a real merge commit via GitHub's own "merge a
                    // branch" endpoint, not the Contents-API single-file patch already proven unreliable for
                    // making a PR's own cached mergeable field trust a branch (that lesson was specifically
                    // about the /pulls/{n}/merge endpoint's stale cache - this /merges call always performs a
                    // fresh git computation, no cache involved, so it's not subject to the same problem).
                    var syncResult = gitHubPullRequestService.mergeBranch(project, thread.getBranchName(), "main",
                            "Sync main into feature thread " + thread.getFeatureId());
                    // Operator directive 2026-07-24 (live incident: PR#35 sat CONFLICTING for hours purely on
                    // root .gitignore, re-diverging every time ANY other PR landed on main, because this drift-
                    // sync had no way to auto-resolve a real textual conflict, only to prevent one starting from
                    // zero). Root .gitignore is orchestrator-owned, not product code (see the task-brief
                    // boundary added earlier today) - safe to unconditionally sync it to main's content and
                    // retry once, same reasoning as the regular-PR trivial-conflict fast path.
                    if (syncResult == GitHubPullRequestService.MergeBranchResult.CONFLICT) {
                        boolean synced = gitHubPullRequestService.resolveFileConflictWithMain(project, thread.getBranchName(), ".gitignore");
                        if (synced) {
                            var retry = gitHubPullRequestService.mergeBranch(project, thread.getBranchName(), "main",
                                    "Sync main into feature thread " + thread.getFeatureId() + " (retry after .gitignore sync)");
                            if (retry == GitHubPullRequestService.MergeBranchResult.MERGED) {
                                log.info("AutoMergeService: thread {} (feature {}) drift-sync conflict was only root .gitignore - synced and merged",
                                        thread.getBranchName(), thread.getFeatureId());
                            }
                        }
                    }
                    // Operator-found gap (2026-07-24 live incident, PR#37): dispatchCloseoutConflictResolution
                    // opens a standalone Jules session with no TaskEntity/PrReviewEntity, so nothing was ever
                    // watching for the fix PR it produces - it sat open forever needing a manual merge. That
                    // fix PR's base is always thread.branchName (Jules opens branch+PR against whatever
                    // startingBranch it was given, regardless of prompt wording asking it to push directly).
                    if (thread.getCloseoutConflictEscalatedAt() != null && thread.getMergedToMainAt() == null) {
                        mergeConflictFixPrIfReady(project, thread);
                    }
                    progressCloseout(project, thread);
                } catch (Exception e) {
                    log.warn("AutoMergeService: closeout tick failed for thread {} (feature {}): {}",
                            thread.getId(), thread.getFeatureId(), e.getMessage());
                }
            }
        }
    }

    // Package-private (not private) so AutoMergeServiceTest can drive it directly, same convention already
    // used by handleMergeConflict below.
    void progressCloseout(com.eneik.production.models.persistence.ProjectEntity project, FeatureThreadEntity thread) {
        if (thread.getCloseoutPrUrl() == null) {
            if (!readinessService.isFeatureReadyForCloseout(project.getId(), thread.getFeatureId())) {
                return;
            }
            var opened = gitHubPullRequestService.createPullRequest(project, thread.getBranchName(), "main",
                    "Closeout: integrate feature " + thread.getFeatureId() + " into main",
                    "Automated closeout PR - brings feature " + thread.getFeatureId()
                            + "'s accumulated work (last role: " + thread.getLastRoleTag()
                            + ") from its continuation branch into main. "
                            + (thread.getSummary() == null ? "" : thread.getSummary()));
            if (opened.isEmpty()) {
                // Self-healing (2026-07-25 live incident): the accumulation branch may already be gone
                // because the feature's last task's own PR merged directly into main and deleted it (the
                // standard --delete-branch convention) - there is nothing left to close out in that case,
                // and retrying createPullRequest against a permanently-dead branch forever is exactly the
                // bug this guards against. Only treat this as "already integrated" when the branch is
                // CONFIRMED gone (branchExists returns false); any inconclusive/transient case still just
                // retries next cycle as before.
                if (!gitHubPullRequestService.branchExists(project, thread.getBranchName())) {
                    thread.setMergedToMainAt(java.time.Instant.now());
                    featureThreadRepository.save(thread);
                    log.info("AutoMergeService: closeout branch {} for feature {} no longer exists (already merged "
                                    + "and cleaned up elsewhere) - treating the feature as closed out without a separate closeout PR",
                            thread.getBranchName(), thread.getFeatureId());
                    return;
                }
                log.warn("AutoMergeService: closeout PR open failed for thread {} (feature {}); will retry next cycle",
                        thread.getId(), thread.getFeatureId());
                return;
            }
            thread.setCloseoutPrUrl(opened.get().url());
            featureThreadRepository.save(thread);
            log.info("AutoMergeService: opened closeout PR {} for feature {} (branch {} -> main)",
                    opened.get().url(), thread.getFeatureId(), thread.getBranchName());
            return; // give GitHub a tick to compute mergeable/checks before acting on it
        }

        PullRequestTarget target = parseGithubPullRequestUrl(thread.getCloseoutPrUrl());
        if (target == null) {
            return;
        }
        int pullNumber;
        try {
            pullNumber = Integer.parseInt(target.pullNumber());
        } catch (NumberFormatException e) {
            return;
        }

        // Live incident, 2026-07-24: a closeout PR merged manually by the operator via the GitHub web UI
        // (not through mergePullRequest below) silently stayed "not closed out" forever - every subsequent
        // tick called mergePullRequest again, got a 405 (already merged, GitHub refuses a second merge
        // call), returned false, and never reached the mergedToMainAt assignment. Checking real merged
        // state FIRST makes this idempotent regardless of who actually performed the merge.
        var prState = gitHubPullRequestService.fetchPullRequestByNumber(project, pullNumber);
        if (prState.isPresent() && prState.get().merged()) {
            thread.setMergedToMainAt(java.time.Instant.now());
            featureThreadRepository.save(thread);
            log.info("AutoMergeService: closeout PR {} was already merged (not necessarily by this service) - feature {} is now in main",
                    thread.getCloseoutPrUrl(), thread.getFeatureId());
            return;
        }

        var checks = gitHubPullRequestService.pullRequestChecks(project, pullNumber);
        if (!checks.available() || !checks.successful()) {
            return; // still pending/failing/unavailable - leave it, recheck next cycle
        }
        var mergeableState = gitHubPullRequestService.mergeableState(project, pullNumber);
        if (mergeableState.isPresent() && mergeableState.get().mergeable() == null) {
            return; // GitHub still computing mergeable - same race the trivial-conflict fast path handles
        }
        if (gitHubPullRequestService.mergePullRequest(project, pullNumber)) {
            thread.setMergedToMainAt(java.time.Instant.now());
            featureThreadRepository.save(thread);
            log.info("AutoMergeService: closeout PR {} merged - feature {} is now in main",
                    thread.getCloseoutPrUrl(), thread.getFeatureId());
            return;
        }
        if (mergeableState.isPresent() && Boolean.FALSE.equals(mergeableState.get().mergeable())) {
            escalateCloseoutConflict(project, thread);
        }
    }

    // Finds and merges the fix PR a closeout-conflict-resolution session opened against thread.branchName
    // (see escalateCloseoutConflict/dispatchCloseoutConflictResolution) - same CI-gated authority as every
    // other merge in this class. Once this merges, thread.branchName contains the conflict fix, so the next
    // tick's normal progressCloseout attempt on the real closeout PR (thread -> main) should succeed on its
    // own with no further special-casing needed here.
    private void mergeConflictFixPrIfReady(com.eneik.production.models.persistence.ProjectEntity project, FeatureThreadEntity thread) {
        var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
        if (snapshot == null || !snapshot.available() || snapshot.open() == null) {
            return;
        }
        var fixPr = snapshot.open().stream()
                .filter(pr -> thread.getBranchName().equals(pr.baseRef()))
                .findFirst()
                .orElse(null);
        if (fixPr == null) {
            return; // not opened yet (or already merged) - nothing to do this tick
        }
        var checks = gitHubPullRequestService.pullRequestChecks(project, fixPr.number());
        if (!checks.available() || !checks.successful()) {
            return;
        }
        var mergeableState = gitHubPullRequestService.mergeableState(project, fixPr.number());
        if (mergeableState.isPresent() && mergeableState.get().mergeable() == null) {
            return;
        }
        if (Boolean.FALSE.equals(mergeableState.map(GitHubPullRequestService.MergeableState::mergeable).orElse(null))) {
            return; // the fix itself conflicts with the thread branch - leave for a human, don't loop
        }
        if (gitHubPullRequestService.mergePullRequest(project, fixPr.number())) {
            log.info("AutoMergeService: merged conflict-fix PR {} into thread branch {} (feature {}) - closeout will retry against it next cycle",
                    fixPr.url(), thread.getBranchName(), thread.getFeatureId());
        }
    }

    // Operator directive 2026-07-24: "нужна автоматика - три попытки и закрыть удалить ветку, удалить
    // цепочку. написать в вишлист подробные причины и рекомендации." Up to CLOSEOUT_CONFLICT_MAX_ATTEMPTS
    // Jules-mediated conflict-resolution rounds (unlike a regular PR conflict there is no live Jules
    // session to message in-place - the thread's last session is long-terminal - so each attempt dispatches
    // a fresh one). A cooldown prevents piling a new session on top of one that might still be working.
    // Once the cap is reached, the thread is abandoned outright rather than left hanging forever.
    private static final int CLOSEOUT_CONFLICT_MAX_ATTEMPTS = 3;
    private static final java.time.Duration CLOSEOUT_CONFLICT_RETRY_COOLDOWN = java.time.Duration.ofMinutes(15);

    private void escalateCloseoutConflict(com.eneik.production.models.persistence.ProjectEntity project, FeatureThreadEntity thread) {
        if (thread.getCloseoutConflictAttempts() >= CLOSEOUT_CONFLICT_MAX_ATTEMPTS) {
            abandonFeatureThread(project, thread);
            return;
        }
        if (thread.getCloseoutConflictEscalatedAt() != null
                && thread.getCloseoutConflictEscalatedAt().isAfter(
                        java.time.Instant.now().minus(CLOSEOUT_CONFLICT_RETRY_COOLDOWN))) {
            return; // previous attempt may still be in flight - don't dispatch a second one on top of it
        }
        int attempt = thread.getCloseoutConflictAttempts() + 1;
        thread.setCloseoutConflictAttempts(attempt);
        thread.setCloseoutConflictEscalatedAt(java.time.Instant.now());
        featureThreadRepository.save(thread);
        julesDispatchService.dispatchCloseoutConflictResolution(project, thread.getBranchName(), thread.getFeatureId());
        log.warn("AutoMergeService: closeout PR {} for feature {} has a real conflict against main; dispatched "
                        + "Jules session to rebase branch {} (attempt {}/{})",
                thread.getCloseoutPrUrl(), thread.getFeatureId(), thread.getBranchName(), attempt, CLOSEOUT_CONFLICT_MAX_ATTEMPTS);
    }

    // Terminal path once CLOSEOUT_CONFLICT_MAX_ATTEMPTS is exhausted: this feature's code will never reach
    // main via this branch. Deletes the now-dead branch chain, closes the closeout PR, and records a
    // detailed closeout_abandoned wishlist item so a human or a future decomposition cycle has the real
    // reason and a concrete recommendation to act on - never a silent, unexplained dead end.
    private void abandonFeatureThread(com.eneik.production.models.persistence.ProjectEntity project, FeatureThreadEntity thread) {
        if (thread.getAbandonedAt() != null) {
            return;
        }
        if (thread.getCloseoutPrUrl() != null) {
            PullRequestTarget target = parseGithubPullRequestUrl(thread.getCloseoutPrUrl());
            if (target != null) {
                try {
                    gitHubPullRequestService.fetchPullRequestByNumber(project, Integer.parseInt(target.pullNumber()))
                            .ifPresent(pr -> gitHubPullRequestService.closeSinglePullRequest(project, pr,
                                    "Abandoned: " + CLOSEOUT_CONFLICT_MAX_ATTEMPTS + " automated conflict-resolution "
                                            + "attempts failed - see the closeout_abandoned wishlist item for this feature"));
                } catch (NumberFormatException e) {
                    log.warn("AutoMergeService: could not parse closeout PR number from {} while abandoning thread {}",
                            thread.getCloseoutPrUrl(), thread.getId());
                }
            }
        }
        gitHubPullRequestService.deleteBranch(project, thread.getBranchName());
        thread.setAbandonedAt(java.time.Instant.now());
        featureThreadRepository.save(thread);
        recordCloseoutAbandonmentWishlist(project, thread);
        log.error("AutoMergeService: feature {} thread ABANDONED after {} failed conflict-resolution attempts - "
                        + "branch {} deleted, closeout PR closed, findings recorded to wishlist.",
                thread.getFeatureId(), CLOSEOUT_CONFLICT_MAX_ATTEMPTS, thread.getBranchName());
    }

    private void recordCloseoutAbandonmentWishlist(com.eneik.production.models.persistence.ProjectEntity project, FeatureThreadEntity thread) {
        var wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(com.eneik.production.models.persistence.WishlistSource.closeout_abandoned);
        wishlist.setFeatureId(thread.getFeatureId());
        wishlist.setSourceRoleTag(thread.getLastRoleTag());
        wishlist.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        wishlist.setContent("Feature " + thread.getFeatureId() + "'s accumulated work (last role: "
                + thread.getLastRoleTag() + ", branch " + thread.getBranchName() + ", closeout PR "
                + thread.getCloseoutPrUrl() + ") could not be reconciled with main after "
                + CLOSEOUT_CONFLICT_MAX_ATTEMPTS + " automated Jules-mediated rebase attempts - this branch's "
                + "real code was NEVER MERGED into main and the branch has now been deleted.\n\n"
                + "Last known work summary: " + (thread.getSummary() == null ? "(none recorded)" : thread.getSummary())
                + "\n\nRecommendation: this consistently indicates either (a) the conflict is symptomatic of a "
                + "deeper design clash with other work already on main (two features independently editing the "
                + "same real file/table/endpoint) that needs a human decision before any retry, not just another "
                + "automated rebase, or (b) the thread's own work has simply drifted too far from main's current "
                + "state to salvage - in that case, re-implementing this feature's remaining scope fresh from "
                + "current main is more reliable than trying to rescue the abandoned branch.");
        wishlistRepository.save(wishlist);
    }

    private void syncOpenPullRequestsFromGitHub() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        try {
            List<com.eneik.production.models.persistence.ProjectEntity> activeProjects =
                    projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
            for (com.eneik.production.models.persistence.ProjectEntity project : activeProjects) {
                var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
                if (snapshot != null && snapshot.available() && snapshot.open() != null) {
                    List<com.eneik.production.models.persistence.JulesSessionEntity> projectSessions =
                            julesSessionRepository.findAll().stream()
                                    .filter(session -> taskRepository.findById(session.getTaskId())
                                            .map(task -> task.getProject() != null
                                                    && project.getId().equals(task.getProject().getId()))
                                            .orElse(false))
                                    .toList();
                    for (var pr : snapshot.open()) {
                        String prUrl = pr.url();
                        if (prUrl == null || prUrl.isBlank()) {
                            continue;
                        }

                        com.eneik.production.models.persistence.JulesSessionEntity matchingSession =
                                findMatchingSession(projectSessions, pr);
                        if (matchingSession == null) {
                            // An unowned PR may be stale process output or a manual branch. Never attach it
                            // to an arbitrary project session and never manufacture an approval/green CI.
                            log.debug("AutoMergeService: Open PR #{} ({}) has no exact Jules session-token match in project {}; leaving it unowned",
                                    pr.number(), prUrl, project.getName());
                            continue;
                        }

                        boolean changed = !prUrl.equals(matchingSession.getPrUrl());
                        matchingSession.setPrUrl(prUrl);
                        String status = matchingSession.getStatus();
                        // Ф-followup (2026-07-23, confirmed live on test-thirty-fifth): setting the status
                        // field directly here used to never trigger handlePrOpenedWorkflow - that edge-
                        // trigger only lives inside JulesDispatchService.pollStatus's own oldStatus/mappedStatus
                        // comparison within ONE call, so a status flip written by a completely different
                        // scheduled method (this one) is invisible to it. Worse, once this method sets prUrl,
                        // pollStatus's OWN fallback GitHub-lookup is permanently skipped (guarded on blank
                        // prUrl), so pollStatus can only reach pr_opened by Jules's raw API independently
                        // reporting it - if Jules's API keeps reporting "running" (observed live), pollStatus
                        // unconditionally writes that status back every poll, stomping this method's pr_opened
                        // every cycle in an infinite loop that never calls the completion workflow. Confirmed
                        // live: a compiler task's session sat at prUrl-set/status="running" for 20+ minutes,
                        // "Linked open GitHub PR" re-logging every single cycle, zero progress.
                        boolean transitionedToPrOpened = "queued".equals(status) || "running".equals(status)
                                || "revising".equals(status) || "stuck".equals(status);
                        if (transitionedToPrOpened) {
                            matchingSession.setStatus("pr_opened");
                            matchingSession.setLastProgressAt(Instant.now());
                            changed = true;
                        }
                        if (changed) {
                            matchingSession = julesSessionRepository.save(matchingSession);
                            log.info("AutoMergeService: Linked open GitHub PR #{} ({}) to exact Jules session {} for project {}",
                                    pr.number(), prUrl, matchingSession.getExternalSessionId(), project.getName());
                        }
                        if (transitionedToPrOpened) {
                            julesDispatchService.handlePrOpenedWorkflow(matchingSession);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AutoMergeService: Failed to sync open PRs from GitHub: {}", e.getMessage());
        }
    }

    // Operator directive 2026-07-24 (live incident: PR#32's branch
    // "jules-10277038075121427427-d75cb1f6-6325105784558859532" legitimately contains an OLDER,
    // already-terminal session's full token as a real substring - it's a feature-thread continuation
    // branch, built by appending the new session's own token onto the parent branch's name. The old
    // matchesSessionToken(...).contains(token) check matched whichever session came first in iteration
    // order, silently overwriting that terminal session's prUrl with a PR it never authored). A pure
    // suffix/boundary check isn't reliable either - confirmed live, some branches append a random hex
    // suffix after the token instead of ending with it (e.g. "jules-17437099639885264223-ec18478e"), so
    // "must end with the token" would miss real matches. Instead: collect every session whose token
    // appears anywhere in the branch (unavoidable given the naming isn't fully consistent), then prefer
    // (a) a token that appears as a delimited whole segment (bounded by `-`/`/`/start/end, not a
    // coincidental digit-substring of a longer token) over a loose match, then (b) a still-active session
    // over an already-terminal one (a terminal session is never the one newly opening a PR), then
    // (c) the most recently created session (the newest work is the most plausible author of a brand-new
    // PR). This does not require exact-suffix naming, just breaks ties correctly when multiple real tokens
    // legitimately co-occur in one branch name.
    private static final java.util.Set<String> TERMINAL_SESSION_STATUSES = java.util.Set.of(
            "closed_terminal_task", "closed_no_code", "cancelled", "failed", "closed");

    private com.eneik.production.models.persistence.JulesSessionEntity findMatchingSession(
            List<com.eneik.production.models.persistence.JulesSessionEntity> projectSessions,
            GitHubPullRequestService.GitHubPullRequest pr) {
        List<com.eneik.production.models.persistence.JulesSessionEntity> candidates = projectSessions.stream()
                .filter(session -> GitHubPullRequestService.matchesSessionToken(pr, session.getExternalSessionId()))
                .toList();
        if (!candidates.isEmpty()) {
            return candidates.stream()
                    .min(java.util.Comparator
                            .comparing((com.eneik.production.models.persistence.JulesSessionEntity s) ->
                                    isDelimitedTokenMatch(pr.headRef(), s.getExternalSessionId()) ? 0 : 1)
                            .thenComparing(s -> TERMINAL_SESSION_STATUSES.contains(s.getStatus()) ? 1 : 0)
                            .thenComparing(com.eneik.production.models.persistence.JulesSessionEntity::getCreatedAt,
                                    java.util.Comparator.reverseOrder()))
                    .orElse(null);
        }
        for (com.eneik.production.models.persistence.JulesSessionEntity session : projectSessions) {
            if (pr.url().equals(session.getPrUrl())) {
                return session;
            }
        }
        return null;
    }

    private boolean isDelimitedTokenMatch(String headRef, String externalSessionId) {
        if (headRef == null || externalSessionId == null) {
            return false;
        }
        String token = externalSessionId.startsWith("sessions/")
                ? externalSessionId.substring("sessions/".length()) : externalSessionId;
        if (token.isBlank()) {
            return false;
        }
        // Bounded on both sides by a non-alphanumeric delimiter (or string start/end) - rejects a token
        // that's merely a digit-substring of a different, longer token sitting in the same branch name.
        return java.util.regex.Pattern.compile("(?<![A-Za-z0-9])" + java.util.regex.Pattern.quote(token) + "(?![A-Za-z0-9])")
                .matcher(headRef).find();
    }

    // A "complex" Cynefin-domain spike marks its task spike_completed and leaves review.merged = false
    // (it was genuinely not merged, so merged = true would be wrong) - but that means the review keeps
    // matching the pendingReviews filter above forever, re-running the same "Merging PR... Not merging
    // branch" no-op every scheduled cycle indefinitely. The task itself is already terminal, so once a
    // review's task has reached spike_completed there is nothing left to (re-)decide.
    private boolean isAlreadyResolvedSpike(PrReviewEntity review) {
        if (review.getJulesSessionId() == null) {
            return false;
        }
        return julesSessionRepository.findById(review.getJulesSessionId())
                .flatMap(session -> taskRepository.findById(session.getTaskId()))
                .map(task -> task.getStatus() == TaskStatus.spike_completed)
                .orElse(false);
    }

    @Transactional
    protected void executeMerge(PrReviewEntity review) {
        log.info("AutoMergeService: Evaluating approved PR {} for merge", review.getPrUrl());
        PullRequestTarget pullRequestTarget = null;
        
        // 1. Check Cynefin complex domain spike handling and Role Philosophical Filter
        if (review.getJulesSessionId() != null) {
            var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                var taskOpt = taskRepository.findById(session.getTaskId());
                if (taskOpt.isPresent()) {
                    var task = taskOpt.get();
                    
                    if ("complex".equalsIgnoreCase(task.getCynefinDomain())) {
                        log.info("Cynefin Domain complex: Task {} spike completed. Not merging branch.", task.getId());
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.spike_completed);
                        taskRepository.save(task);
                        
                        review.setMerged(false);
                        prReviewRepository.save(review);
                        return;
                    }

                    // Role Philosophical Filter (Gemini refusal-criteria check) removed (2026-07-25,
                    // operator directive - emergency cost incident, "прекрати вызывать джемени на ревью").
                    // Note found while removing it: this block never actually gated the merge (a non-
                    // compliant verdict only logged a warning - mergeSuccess/return were never touched) -
                    // it was already pure log-only overhead, so removing it loses zero real enforcement.
                    // Review (including hygiene/safety checks) is Jules-only now; see
                    // JulesDispatchService.executeCodeReview and reviewerFallbackPromptBatch's own
                    // block-list (secrets, generated artifacts, unguarded terminal-status writes).
                }
            }
        }

        boolean mergeSuccess = false;
        boolean alreadyMergedExternally = false;
        boolean isConflictSimulated = review.getPrUrl() != null && (review.getPrUrl().contains("conflict") || review.getPrUrl().contains("dirty"));

        // Local review approval is necessary but not sufficient. GitHub's real checks are the merge
        // authority; the old discovery fallback synthesized ciStatus=success and merged several red PRs.
        if (settingsService.effectiveBoolean("github_enabled") && !isConflictSimulated) {
            com.eneik.production.models.persistence.JulesSessionEntity reviewSession = sessionForReview(review);
            com.eneik.production.models.persistence.TaskEntity reviewTask = reviewSession == null
                    ? null
                    : taskRepository.findById(reviewSession.getTaskId()).orElse(null);
            if (reviewSession == null || reviewTask == null || reviewTask.getProject() == null) {
                review.setCiStatus("unowned");
                prReviewRepository.save(review);
                log.warn("AutoMergeService: Refusing to merge PR {} because its review has no owning task/project",
                        review.getPrUrl());
                return;
            }
            if (pullRequestTarget == null) {
                pullRequestTarget = resolvePullRequestTarget(review);
            }
            if (pullRequestTarget == null) {
                review.setCiStatus("invalid_pr");
                prReviewRepository.save(review);
                return;
            }
            GitHubPullRequestService.GitHubPullRequest githubPr = gitHubPullRequestService
                    .fetchPullRequestByNumber(reviewTask.getProject(), Integer.parseInt(pullRequestTarget.pullNumber()))
                    .orElse(null);
            // Live incident, 2026-07-24 (operator manually closed PR#57 as a duplicate; the merge-retry loop
            // never found out and kept retrying a 405 "not mergeable" every ~60s forever): a PR that's
            // closed without ever merging is permanently dead, not a transient conflict to retry. Nothing
            // upstream of this call tells the review polling loop that "closed" happened outside our own
            // executeMerge/progressCloseout paths (an operator's manual GitHub-UI close, another automated
            // cleanup, etc.) - checking real GitHub state here catches it regardless of cause and stops the
            // wasted retries for good, mirroring the already-merged idempotency check right below it.
            if (githubPr != null && githubPr.closed() && !githubPr.merged()) {
                review.setCiStatus("closed_unmerged");
                prReviewRepository.save(review);
                log.info("AutoMergeService: PR {} is closed without ever merging (not by this service) - "
                        + "marking its review terminal instead of retrying a dead merge forever.", review.getPrUrl());
                return;
            }
            // Live incident, 2026-07-24 (root cause of "Live Chat CRM" epic silently stuck at 3/4 merged
            // despite all 4 of its real PRs being merged on GitHub): GitHub's PUT .../merge returns 405 for
            // BOTH "already merged" and "real conflict" - indistinguishable by status code alone. The merge
            // attempt below used to treat every 405 as a conflict and call handleMergeConflict even when the
            // PR was already fine (merged by something other than this exact code path - a manual GitHub
            // merge, GitHub's own auto-merge setting, a retry race). Checking real merged state first (same
            // fix already applied to the closeout path in progressCloseout) makes this idempotent regardless
            // of who/what actually performed the merge - skips the owner-token/CI checks entirely, since a
            // PR that's already merged has already passed whatever gates it needed to.
            if (githubPr != null && githubPr.merged()) {
                log.info("AutoMergeService: PR {} was already merged (not necessarily by this service); recording as merged instead of re-attempting.", review.getPrUrl());
                alreadyMergedExternally = true;
            } else {
            if (!GitHubPullRequestService.matchesSessionToken(githubPr, reviewSession.getExternalSessionId())) {
                review.setCiStatus("owner_mismatch");
                prReviewRepository.save(review);
                log.warn("AutoMergeService: Refusing to merge PR {} because branch {} does not contain Jules session token {}",
                        review.getPrUrl(), githubPr == null ? "<unavailable>" : githubPr.headRef(),
                        reviewSession.getExternalSessionId());
                return;
            }
            GitHubPullRequestService.PullRequestChecks checks = gitHubPullRequestService.pullRequestChecks(
                    reviewTask.getProject(), Integer.parseInt(pullRequestTarget.pullNumber()));
            review.setCiStatus(checks.status());
            prReviewRepository.save(review);
            if (!checks.available() || !checks.successful()) {
                log.warn("AutoMergeService: Refusing to merge PR {} because GitHub CI is {}: {}",
                        review.getPrUrl(), checks.status(), checks.detail());
                return;
            }
            }
        }
        
        // Execute real merge on GitHub if enabled
        if (alreadyMergedExternally) {
            mergeSuccess = true;
            resolveActiveConflict(review.getJulesSessionId());
        } else if (settingsService.effectiveBoolean("github_enabled") && !isConflictSimulated) {
            String token = settingsService.effectiveValue("github_token");
            if (token != null && !token.isBlank()) {
                try {
                    if (pullRequestTarget == null) {
                        pullRequestTarget = resolvePullRequestTarget(review);
                    }

                    if (pullRequestTarget == null) {
                        log.warn("AutoMergeService: Skipping merge for invalid or unresolved PR URL {}", review.getPrUrl());
                        review.setCiStatus("invalid_pr");
                        prReviewRepository.save(review);
                        return;
                    }

                    String prUrl = pullRequestTarget.url();
                    String owner = pullRequestTarget.owner();
                    String repoName = pullRequestTarget.repo();
                    String pullNumber = pullRequestTarget.pullNumber();

                    String mergeUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/pulls/" + pullNumber + "/merge";
                        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
                        HttpRequest mergeRequest = HttpRequest.newBuilder()
                                .uri(URI.create(mergeUrl))
                                .header("Authorization", "Bearer " + token)
                                .header("Accept", "application/vnd.github+json")
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                                .build();
                        HttpResponse<String> mergeResponse = client.send(mergeRequest, HttpResponse.BodyHandlers.ofString());
                        if (mergeResponse.statusCode() >= 200 && mergeResponse.statusCode() < 300) {
                            log.info("GitHub API: Successfully merged real PR {} on GitHub!", prUrl);
                            if (prUrl != null) {
                                review.setPrUrl(prUrl);
                            }
                            mergeSuccess = true;
                            resolveActiveConflict(review.getJulesSessionId());
                        } else {
                            log.error("GitHub API: Failed to merge real PR {} on GitHub. Status: {}, Body: {}", prUrl, mergeResponse.statusCode(), mergeResponse.body());
                            if (mergeResponse.statusCode() == 405 || mergeResponse.statusCode() == 409) {
                                handleMergeConflict(review, owner, repoName, pullNumber, token);
                            }
                        }
                } catch (Exception e) {
                    log.error("Error executing real GitHub merge for PR {}: {}", review.getPrUrl(), e.getMessage(), e);
                }
            } else {
                log.warn("GitHub integration is enabled but token is missing");
            }
        } else {
            // Local mode: clean test-verified merge without generating fake defect conflicts
            mergeSuccess = true;
            log.info("AutoMergeService: Processing test-verified local merge for PR {}", review.getPrUrl());
            resolveActiveConflict(review.getJulesSessionId());
        }

        if (mergeSuccess) {
            recordSuccessfulMerge(review, pullRequestTarget);
        }
    }

    /**
     * Extracted 2026-07-24 (root cause of "Live Chat CRM" epic silently stuck at 3/4 despite all 4 real PRs
     * being merged on GitHub) so this exact bookkeeping - task-done, feature-thread classification, video
     * walkthrough, advice loop - can also run for a review {@link #resurrectAlreadyMergedReviews} finds was
     * merged externally, not just one this method's own PUT call just merged.
     */
    private void recordSuccessfulMerge(PrReviewEntity review, PullRequestTarget pullRequestTarget) {
            review.setMerged(true);
            prReviewRepository.save(review);
            systemProgressTracker.recordProgress();

            final PullRequestTarget mergedPrTarget = pullRequestTarget;

            // Also mark corresponding task as done
            if (review.getJulesSessionId() != null) {
                julesSessionRepository.findById(review.getJulesSessionId()).ifPresent(session -> {
                    UUID taskId = session.getTaskId();
                    taskRepository.findById(taskId).ifPresent(task -> {
                        task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
                        taskRepository.save(task);
                        claimService.releaseTerminalClaim(taskId);
                        log.info("AutoMergeService: Marked task {} as DONE because its PR was merged", taskId);

                        classifyAndHandleBranch(review, mergedPrTarget, task, session);

                        // Lean-waste fix (2026-07-23, generalized 2026-07-24 from API-contract-only to
                        // every EmsFlowStage.isSpecStage): if this just-merged task was a spec-stage task,
                        // any dependent that started early (see ProjectFlowService.EARLY_UNBLOCK_SPEC_KEY
                        // marker) may have begun against a pre-review draft of the artifact - give its
                        // still-active session an FYI to reconcile against the final one.
                        if (task.getRole() != null && EmsFlowStage.isSpecStage(task.getRole().getTag())) {
                            notifyEarlyUnblockedDependents(task);
                        }

                        // Automatically generate a walkthrough video for the merged task!
                        try {
                            var context = contextService.build(task.getProject().getId(), task.getProject().getName());
                            var videoResult = videoAssetService.generateAsset(
                                    task.getProject(),
                                    context,
                                    "Feature walkthrough for task: " + task.getDescription() + ". Diff summary: " + review.getDiffSummary(),
                                    "walkthrough",
                                    "standard",
                                    false
                            );
                            if (videoResult.available()) {
                                log.info("AutoMergeService: Veo Video walkthrough successfully generated at {}", videoResult.videoPath());
                            } else {
                                log.warn("AutoMergeService: Veo Video walkthrough generation was unavailable: {}", videoResult.message());
                            }
                        } catch (Exception e) {
                            log.error("AutoMergeService: Failed to trigger Veo Video walkthrough: " + e.getMessage());
                        }
                        
                        // Chaotic findings stay observable but cannot open a parallel product iteration.
                        if ("chaotic".equalsIgnoreCase(task.getCynefinDomain())) {
                            log.warn("Poka-yoke: chaotic debt observed after task {} merged; no wishlist was "
                                    + "created. Falsification owns next-iteration generation.", task.getId());
                        }

                        // Call advice loop here upon successful merge
                        try {
                            roleAdviceLoopService.afterTaskComplete(taskId);
                            log.info("AutoMergeService: Triggered role advice loop for task {}", taskId);
                        } catch (Exception ex) {
                            log.error("AutoMergeService: Failed to trigger role advice loop for task {}: {}", taskId, ex.getMessage(), ex);
                        }
                    });
                });
            }
    }

    // Live incident, 2026-07-24: same 405-ambiguity root cause as the already-merged check inline in
    // executeMerge above, but for reviews that got stuck in a TERMINAL ciStatus (owner_mismatch, invalid_pr,
    // unowned, etc.) before this fix existed - isReviewPollCandidate permanently excludes those from
    // executeMerge ever running again, exactly the same class of dead end resurrectTriviallyEscalatedConflicts
    // already fixed for "escalated". Runs every tick, checks EVERY still-unmerged review's real GitHub state
    // regardless of its stored ciStatus (cheap - one fetchPullRequestByNumber call per still-open review),
    // and runs the exact same success bookkeeping a fresh merge would via recordSuccessfulMerge.
    private void resurrectAlreadyMergedReviews() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        List<PrReviewEntity> unmerged = prReviewRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getMerged()))
                .toList();
        for (PrReviewEntity review : unmerged) {
            try {
                PullRequestTarget target = resolvePullRequestTarget(review);
                if (target == null) {
                    continue;
                }
                var sessionOpt = review.getJulesSessionId() == null
                        ? java.util.Optional.<com.eneik.production.models.persistence.JulesSessionEntity>empty()
                        : julesSessionRepository.findById(review.getJulesSessionId());
                var taskOpt = sessionOpt.map(s -> taskRepository.findById(s.getTaskId()).orElse(null));
                var project = taskOpt.map(t -> t == null ? null : t.getProject()).orElse(null);
                if (project == null) {
                    continue;
                }
                var githubPr = gitHubPullRequestService.fetchPullRequestByNumber(project, Integer.parseInt(target.pullNumber()));
                if (githubPr.isPresent() && githubPr.get().merged()) {
                    log.info("AutoMergeService: resurrected review for PR {} - was already merged on GitHub but "
                                    + "stuck at ciStatus={}, never re-checked; recording as merged now.",
                            review.getPrUrl(), review.getCiStatus());
                    recordSuccessfulMerge(review, target);
                }
            } catch (Exception e) {
                log.warn("AutoMergeService: resurrectAlreadyMergedReviews failed for review {} (PR {}): {}",
                        review.getId(), review.getPrUrl(), e.getMessage());
            }
        }
    }

    private com.eneik.production.models.persistence.JulesSessionEntity sessionForReview(PrReviewEntity review) {
        if (review.getJulesSessionId() == null) {
            return null;
        }
        return julesSessionRepository.findById(review.getJulesSessionId()).orElse(null);
    }

    /**
     * Lean-waste fix (2026-07-23, generalized 2026-07-24): finds tasks that were early-unblocked against
     * this just-merged spec-stage task (see ProjectFlowService.EARLY_UNBLOCK_SPEC_KEY) and, for any still
     * running an active Jules session, sends a one-time informational note so the session can reconcile
     * against the now-final artifact if it was revised during its own review. Guarded on
     * EARLY_UNBLOCK_SPEC_NOTIFIED_KEY so a dependent is never notified twice even if this method somehow
     * runs again for the same task (previously stamped but never checked - latent double-send, fixed here).
     * Fail-closed: never blocks the merge that just happened.
     */
    private void notifyEarlyUnblockedDependents(com.eneik.production.models.persistence.TaskEntity specTask) {
        try {
            for (com.eneik.production.models.persistence.TaskEntity dependent
                    : taskRepository.findByDependsOnId(specTask.getId())) {
                JsonNode payload = dependent.getPayload();
                if (!(payload instanceof ObjectNode payloadNode)
                        || !payloadNode.path(ProjectFlowService.EARLY_UNBLOCK_SPEC_KEY).asBoolean(false)
                        || payloadNode.path(EARLY_UNBLOCK_SPEC_NOTIFIED_KEY).asBoolean(false)) {
                    continue;
                }
                julesSessionRepository.findByTaskId(dependent.getId()).stream()
                        .filter(s -> List.of("queued", "running", "revising", "stuck", "pr_opened").contains(s.getStatus()))
                        .findFirst()
                        .ifPresent(session -> julesDispatchService.notifySpecTaskFinalized(dependent, session, specTask));
                payloadNode.put(EARLY_UNBLOCK_SPEC_NOTIFIED_KEY, true);
                taskRepository.save(dependent);
            }
        } catch (Exception e) {
            log.warn("AutoMergeService: failed to notify early-unblocked dependents of spec task {}: {}",
                    specTask.getId(), e.getMessage(), e);
        }
    }

    /**
     * Deterministic post-merge fork: a merged PR either contained real product code or it didn't (see
     * CodeChangeClassifier). No code -&gt; the branch is disposable, delete it and close the session as
     * closed_no_code. Real code -&gt; leave the branch alone and record it as this feature's live
     * continuation thread (FeatureThreadEntity) - scoped to the feature, not the role, since one feature
     * routinely involves several roles working the same dependency chain and any of them may legitimately
     * continue on the same branch - so the next task dispatched for this feature (whichever role it is)
     * can start its Jules session from this branch (startingBranch) instead of main - see
     * JulesDispatchService.dispatchInternal.
     */
    private void classifyAndHandleBranch(PrReviewEntity review, PullRequestTarget pullRequestTarget,
                                         com.eneik.production.models.persistence.TaskEntity task,
                                         com.eneik.production.models.persistence.JulesSessionEntity session) {
        if (!settingsService.effectiveBoolean("github_enabled") || pullRequestTarget == null) {
            return;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return;
        }
        List<String> changedFiles = fetchPrFiles(token, pullRequestTarget.owner(), pullRequestTarget.repo(), pullRequestTarget.pullNumber());
        boolean hasCode = codeChangeClassifier.hasCode(changedFiles);
        review.setHasCode(hasCode);
        prReviewRepository.save(review);

        if (task.getProject() == null) {
            return;
        }
        int pullNumber;
        try {
            pullNumber = Integer.parseInt(pullRequestTarget.pullNumber());
        } catch (NumberFormatException e) {
            return;
        }

        if (!hasCode) {
            gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), pullNumber)
                    .ifPresent(pr -> {
                        review.setBaseRef(pr.baseRef());
                        prReviewRepository.save(review);
                        gitHubPullRequestService.deleteBranch(task.getProject(), pr.headRef());
                    });
            session.setStatus("closed_no_code");
            session.setClosedAt(java.time.Instant.now());
            session.setClosureReason("Merged PR contained no product code (process/config/docs only); branch deleted.");
            julesSessionRepository.save(session);
            log.info("AutoMergeService: PR {} classified as no-code; branch deleted, session {} closed", pullRequestTarget.url(), session.getId());
            return;
        }

        gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), pullNumber).ifPresent(pr -> {
            review.setBaseRef(pr.baseRef());
            prReviewRepository.save(review);
            String roleTag = task.getRole().getTag();
            UUID featureId = task.getFeatureId();
            if (featureId == null) {
                log.warn("AutoMergeService: PR {} classified as has-code for task {}, but task has no featureId; skipping feature-thread update.",
                        pullRequestTarget.url(), task.getId());
                return;
            }
            FeatureThreadEntity thread = featureThreadRepository.findByProjectIdAndFeatureId(task.getProject().getId(), featureId)
                    .orElseGet(FeatureThreadEntity::new);
            thread.setProjectId(task.getProject().getId());
            thread.setFeatureId(featureId);
            thread.setLastRoleTag(roleTag);
            thread.setBranchName(pr.headRef());
            thread.setAccountId(session.getAccountId());
            thread.setLastPrUrl(pullRequestTarget.url());
            String summary = review.getDiffSummary();
            thread.setSummary(summary == null ? pr.title() : (summary.length() > 2000 ? summary.substring(0, 2000) : summary));
            thread.setUpdatedAt(java.time.Instant.now());
            // Closeout tracking (2026-07-24): this merge just moved the thread's tip forward - a NEW commit
            // that has never reached main, even if this same feature closed out once before. Reset the
            // closeout state so the next processAutoMerge tick treats it as freshly reopened rather than
            // silently trusting a stale "already in main" timestamp for code that isn't there.
            thread.setMergedToMainAt(null);
            thread.setCloseoutPrUrl(null);
            thread.setCloseoutConflictEscalatedAt(null);
            featureThreadRepository.save(thread);
            log.info("AutoMergeService: PR {} classified as has-code; thread for feature {} (last role {}) in project {} now points to branch {}",
                    pullRequestTarget.url(), featureId, roleTag, task.getProject().getId(), pr.headRef());
        });
    }

    // Package-private (not private), matching reconcileReviewAgainstTaskOutcome's existing convention in
    // this class, so the escalation branch's terminal ciStatus transition can be tested directly.
    void handleMergeConflict(PrReviewEntity review, String owner, String repo, String pullNumber, String token) {
        if (review.getJulesSessionId() == null) return;
        var sessionOpt = julesSessionRepository.findById(review.getJulesSessionId());
        if (sessionOpt.isEmpty()) return;
        var session = sessionOpt.get();
        UUID taskId = session.getTaskId();
        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) return;
        var task = taskOpt.get();

        if (reconcileReviewAgainstTaskOutcome(review, task)) {
            return;
        }
        
        var conflictOpt = taskConflictRepository.findFirstByTaskIdAndResolutionStatus(taskId, "pending");
        TaskConflictEntity conflict;
        if (conflictOpt.isPresent()) {
            conflict = conflictOpt.get();
        } else {
            conflict = new TaskConflictEntity();
            conflict.setTask(task);
            conflict.setPrUrl(review.getPrUrl());
            conflict.setConflictType("merge_conflict");
            conflict.setResolutionAttempts(0);
            conflict.setResolutionStatus("pending");
        }
        
        List<String> files = new ArrayList<>();
        if (token != null && owner != null && repo != null && pullNumber != null) {
            files = fetchPrFiles(token, owner, repo, pullNumber);
        } else {
            // Mock or fallback: try to parse task's file scope
            if (task.getFileScope() != null) {
                try {
                    files = objectMapper.readValue(task.getFileScope(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {}
            }
        }
        
        String filesJson = "[]";
        try {
            filesJson = objectMapper.writeValueAsString(files);
        } catch (Exception e) {}
        conflict.setConflictingFiles(filesJson);

        // Trivial-conflict fast path: sync any file the ORCHESTRATOR itself owns - not something the task
        // was asked to write as its deliverable - that appears in this PR's diff, to main's current content.
        // Two categories, both pure noise never worth a Jules session or a human judgment call:
        //   - `.eneik/*` transient signaling records (task-plan.json, review-verdict.json,
        //     design-review-verdict.json, ...) - our own bookkeeping, briefs explicitly forbid tasks from
        //     touching this path at all (see TechnicalLeadCompiler's Boundaries section).
        //   - the ROOT `.gitignore` - operator directive 2026-07-24 (live incident: 5 of 6 simultaneous
        //     merge-conflict escalations on test-thirty-seventh traced to root .gitignore, NOT real code -
        //     every conflicting branch had already created its own scoped `frontend/.gitignore` with the
        //     ignore rules it actually needed; its root-level edit was redundant boilerplate every time).
        //     Root .gitignore is the orchestrator's file (`.eneik/`, `target/`); a task's real per-package
        //     ignore needs belong in a nested .gitignore, never here - discarding a branch's root-level edit
        //     in favor of main's never loses anything the branch actually needed.
        //
        // Ф1 (2026-07-21 review, corrected from the first version of this fix): `files` is the PR's FULL
        // changed-file list, not specifically the files actually in conflict - requiring `allMatch` meant
        // this never fired for any PR that also carried real product code alongside a stray `.eneik/`
        // file, which is exactly the motivating case (PR#12: real Svelte frontend + one incidental
        // `.eneik/task-plan.json`). Fixed to act on this SUBSET of files regardless of what else
        // changed - real code is never touched by this path either way, so widening it is safe.
        //
        // Bounded to one attempt per conflict (`resolutionAttempts == 0`, i.e. only on the FIRST time this
        // conflict is diagnosed): if the real conflict is elsewhere and syncing these alone doesn't
        // make the PR mergeable, retrying this same sync every cycle forever would silently mask that and
        // never fall through to the rebase/escalation path that actually handles a real code conflict.
        // Tier-1 100% Autonomous Branch Sync: Automatically merge main into feature branch via GitHub API
        if (owner != null && repo != null && pullNumber != null && conflict.getResolutionAttempts() < 10) {
            String branch = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), Integer.parseInt(pullNumber))
                    .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                    .orElse(null);
            if (branch != null) {
                GitHubPullRequestService.MergeBranchResult branchMergeResult =
                        gitHubPullRequestService.mergeBranch(task.getProject(), branch, "main", "AutoMerge: sync main into " + branch);
                if (branchMergeResult == GitHubPullRequestService.MergeBranchResult.MERGED ||
                    branchMergeResult == GitHubPullRequestService.MergeBranchResult.UP_TO_DATE) {
                    log.info("AutoMergeService [100% AUTONOMOUS]: Successfully merged main into branch {} via GitHub API for PR #{}. Branch is now clean!", branch, pullNumber);
                    conflict.setResolutionAttempts(1);
                    taskConflictRepository.save(conflict);
                    return;
                }
            }
        }

        if (!files.isEmpty() && owner != null && repo != null && pullNumber != null) {
            String branch = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), Integer.parseInt(pullNumber))
                    .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                    .orElse(null);
            if (branch != null) {
                boolean allResolved = files.stream()
                        .allMatch(f -> f.startsWith(".eneik/") || f.equals(".gitignore")
                                ? gitHubPullRequestService.resolveFileConflictWithMain(task.getProject(), branch, f)
                                : gitHubPullRequestService.resolveProductCodeConflictWithMain(task.getProject(), branch, f));
                conflict.setResolutionAttempts(1);
                taskConflictRepository.save(conflict);
                if (allResolved) {
                    log.info("AutoMergeService [100% AUTONOMOUS SMART CODE MERGER]: PR #{} had {} conflicting file(s); "
                                    + "synced product & orchestrator files directly to main via smart 3-way merge on branch {}.",
                            pullNumber, files.size(), branch);
                    return;
                }
                log.warn("AutoMergeService: PR #{} had conflicting file(s) but backend resolution "
                        + "failed for at least one; falling through to the normal rebase/escalation path "
                        + "this same cycle.", pullNumber);
            }
        }

        review.setCiStatus("conflict");
        prReviewRepository.save(review);
        taskConflictRepository.save(conflict);

        // Operator directive 2026-07-24 (live incident: 3 escalated-then-resurrected PRs bounced straight
        // back to escalated within ~2 minutes, even though `git merge-tree` confirmed zero real conflicts
        // left after the fast-path sync above). Root cause: GitHub computes `mergeable` asynchronously -
        // for some seconds right after any push it reports null/"unknown", not yet true/false. The merge
        // PUT this method is reacting to (a 405) can be exactly that stale window, not a real conflict. If
        // GitHub itself is still undecided, don't spend one of the 3 tries on a race it was never going to
        // win - leave resolutionAttempts untouched (conflict is already saved above so no row is lost) and
        // let the next tick's merge attempt (by which point GitHub has almost always finished computing) be
        // the one that actually counts.
        if (owner != null && repo != null && pullNumber != null) {
            var mergeableState = gitHubPullRequestService.mergeableState(task.getProject(), Integer.parseInt(pullNumber));
            if (mergeableState.isPresent() && mergeableState.get().mergeable() == null) {
                log.info("AutoMergeService: PR #{} mergeable state still unknown to GitHub (mergeStateStatus={}) - "
                                + "not counting this as a resolution attempt, will recheck next cycle.",
                        pullNumber, mergeableState.get().mergeStateStatus());
                return;
            }
        }

        int attempts = conflict.getResolutionAttempts() + 1;
        conflict.setResolutionAttempts(attempts);

        if (attempts >= 3) {
            // Three auto-resolve attempts have failed - this branch is unrecoverable, not worth a
            // fourth rebase. No human ever looks at this system day-to-day, so escalating to a
            // needs_human_review row here used to be a dead end (nothing reads that table). Instead,
            // abandon the conflicting branch and spawn one fresh atomic recovery task - same idiom
            // already used by ProjectFlowService's blocked-task recovery and by
            // reconcileAbandonedPullRequests' rejection path.
            conflict.setResolutionStatus("escalated");
            taskConflictRepository.save(conflict);

            // Now that "conflict" is a valid poll candidate again (see isReviewPollCandidate), this review
            // must get an explicit, genuinely terminal ciStatus once retries are truly exhausted - otherwise
            // it would re-enter pendingReviews every tick forever, re-attempt the merge, hit 409 again, and
            // re-escalate a BRAND NEW TaskConflictEntity indefinitely (findFirstByTaskIdAndResolutionStatus
            // only finds "pending" ones, so a fresh row would be created every cycle once this one is
            // "escalated" instead of "pending"). Deliberately reuses the exact same string as
            // TaskConflictEntity.resolutionStatus's own "escalated" value above - one word for one real-world
            // event, on both records. (ci_status is VARCHAR(16) - confirmed live: a first attempt at this fix
            // used "conflict_escalated", 18 chars, and threw DataIntegrityViolationException on save, rolling
            // back the whole escalation transaction every time attempt 3 was reached - never actually fix a
            // status value without checking the column's real length constraint.)
            review.setCiStatus("escalated");
            prReviewRepository.save(review);

            log.warn("Poka-yoke: merge conflict for task {} escalated after {} attempts; triggering Branch Garbage Collector to retire old branch and re-queue task.", taskId, attempts);
            if (branchGarbageCollectorService != null && owner != null && repo != null && pullNumber != null) {
                String branch = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), Integer.parseInt(pullNumber))
                        .map(GitHubPullRequestService.GitHubPullRequest::headRef)
                        .orElse(null);
                branchGarbageCollectorService.retireAbandonedBranchAndPR(
                        task.getProject(), task, branch, Integer.parseInt(pullNumber), "Conflict escalated after 3 attempts");
            }
        } else {
            taskConflictRepository.save(conflict);

            // Operator directive (2026-07-23): prefer resolving the conflict IN PLACE - same session, same
            // branch, same PR - instead of abandoning the session and re-implementing the task from scratch
            // in a brand new one. Mirrors JulesDispatchService.applyReviewVerdictToTask's "blocked" branch,
            // which already does exactly this (sendMessage + revising) for real implementer PRs rejected by
            // quality-gate review. Confirmed live (test-thirty-fifth PR#3, 2026-07-23) that the old
            // cancel+redispatch path worked but was wasteful: it burned a whole second Jules session to
            // re-derive code Jules already had full context for, AND left the original PR (#3) permanently
            // open and orphaned on GitHub (cancelSession only updates our own DB, never closes the real PR).
            boolean resolvedInPlace = julesDispatchService.requestMergeConflictResolution(task, session, files, attempts);
            if (resolvedInPlace) {
                log.info("Requested in-place conflict resolution (attempt {}/3) for task {} - same session, same PR", attempts, taskId);
                return;
            }

            // Fallback only: the session is no longer messageable (missing/expired external session id).
            // Same cancel+redispatch recovery this method used exclusively before 2026-07-23 - kept as a
            // safety net so a dead session doesn't permanently strand the task.
            log.warn("Session for task {} is not messageable; falling back to cancel+redispatch for conflict attempt {}/3", taskId, attempts);
            for (com.eneik.production.models.persistence.JulesSessionEntity staleSession
                    : julesSessionRepository.findByTaskId(taskId)) {
                if (!"cancelled".equals(staleSession.getStatus()) && !"loop_closed".equals(staleSession.getStatus())
                        && !"closed_no_code".equals(staleSession.getStatus())) {
                    julesDispatchService.cancelSession(staleSession.getId(),
                            "Superseded by auto-resolve rebase attempt " + attempts + "/3 (merge conflict, session unmessageable)");
                }
            }

            UUID accountId = (session != null && session.getAccountId() != null)
                    ? session.getAccountId()
                    : julesSessionRepository.findByTaskId(taskId).stream()
                            .map(com.eneik.production.models.persistence.JulesSessionEntity::getAccountId)
                            .filter(java.util.Objects::nonNull)
                            .findFirst()
                            .orElse(null);
            if (accountId == null) {
                log.warn("Cannot trigger auto-resolve rebase for task {} because no accountId could be resolved", taskId);
                return;
            }
            task.setStatus(com.eneik.production.models.persistence.TaskStatus.queued);
            taskRepository.save(task);
            claimService.claimSpecificTask(task.getId(), accountId);
            com.eneik.production.models.persistence.TaskEntity claimedTask =
                    taskRepository.findById(task.getId()).orElse(task);

            log.info("Triggering auto-resolve rebase attempt {}/3 for task {}", attempts, taskId);
            julesDispatchService.dispatch(claimedTask, accountId, "IMPLEMENTER");
        }
    }

    boolean reconcileReviewAgainstTaskOutcome(PrReviewEntity review,
                                               com.eneik.production.models.persistence.TaskEntity task) {
        List<com.eneik.production.models.persistence.JulesSessionEntity> taskSessions =
                julesSessionRepository.findByTaskId(task.getId());
        List<UUID> sessionIds = taskSessions.stream()
                .map(com.eneik.production.models.persistence.JulesSessionEntity::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean hasMergedSibling = !sessionIds.isEmpty()
                && prReviewRepository.existsByJulesSessionIdInAndMergedTrue(sessionIds);
        boolean terminal = task.getStatus() == TaskStatus.done
                || task.getStatus() == TaskStatus.failed
                || task.getStatus() == TaskStatus.blocked
                || task.getStatus() == TaskStatus.spike_completed;

        if (settingsService.effectiveBoolean("github_enabled") && !Boolean.TRUE.equals(review.getMerged()) && task.getProject() != null) {
            PullRequestTarget target = parseGithubPullRequestUrl(review.getPrUrl());
            if (target != null) {
                var githubPr = gitHubPullRequestService.fetchPullRequestByNumber(task.getProject(), Integer.parseInt(target.pullNumber())).orElse(null);
                if (githubPr != null && !githubPr.closed()) {
                    return false;
                }
            }
        }

        if (!hasMergedSibling && !terminal) {
            return false;
        }

        if (hasMergedSibling) {
            task.setStatus(TaskStatus.done);
            taskRepository.save(task);
            claimService.releaseTerminalClaim(task.getId());
        }

        if (!Boolean.TRUE.equals(review.getMerged())) {
            review.setCiStatus("superseded");
            prReviewRepository.save(review);
        }

        taskConflictRepository.findFirstByTaskIdAndResolutionStatus(task.getId(), "pending")
                .ifPresent(conflict -> {
                    conflict.setResolutionStatus("superseded");
                    conflict.setResolvedAt(Instant.now());
                    taskConflictRepository.save(conflict);
                });

        for (com.eneik.production.models.persistence.JulesSessionEntity session : taskSessions) {
            if (List.of("queued", "running", "revising", "stuck", "pr_opened").contains(session.getStatus())) {
                session.setStatus("cancelled");
                session.setClosedAt(Instant.now());
                session.setClosureReason(hasMergedSibling
                        ? "Superseded by an already merged PR for the same task"
                        : "Task is already terminal; stale review/session retired");
                julesSessionRepository.save(session);
            }
        }

        log.info("AutoMergeService: Retired stale review {} for terminal task {} (status={}, merged sibling={})",
                review.getPrUrl(), task.getId(), task.getStatus(), hasMergedSibling);
        return true;
    }

    void reconcileMergedTaskOutcomes() {
        List<PrReviewEntity> reviews = prReviewRepository.findAll();

        for (PrReviewEntity mergedReview : reviews) {
            if (!Boolean.TRUE.equals(mergedReview.getMerged()) || mergedReview.getJulesSessionId() == null) {
                continue;
            }
            com.eneik.production.models.persistence.JulesSessionEntity winningSession =
                    julesSessionRepository.findById(mergedReview.getJulesSessionId()).orElse(null);
            if (winningSession == null || winningSession.getTaskId() == null) {
                continue;
            }
            com.eneik.production.models.persistence.TaskEntity task =
                    taskRepository.findById(winningSession.getTaskId()).orElse(null);
            if (task == null) {
                continue;
            }
            repairTaskForConfirmedMerge(task, winningSession, mergedReview.getPrUrl());
        }
    }

    // Ф-followup (2026-07-23, operator directive - confirmed live on two separate projects,
    // test-thirty-third and test-thirty-fourth): reconcileMergedTaskOutcomes() above only repairs a task
    // when a LOCAL PrReviewEntity row already exists with merged=true. Two independent live incidents
    // proved that assumption false: (1) a session can reach pr_opened through the GitHub-lookup fallback
    // in JulesDispatchService.pollStatus (detectOpenPullRequestFromGitHub) without ever crossing the
    // running/revising -> pr_opened edge that creates the review record, so handlePrOpenedWorkflow never
    // fires; (2) an operator can merge a PR manually on GitHub before the app's own batched review ever
    // runs. In both cases zero PrReviewEntity rows exist for the affected task, so
    // reconcileMergedTaskOutcomes has nothing to reconcile from and the owning task - plus everything
    // depending on it via the dependency graph - stays stuck forever even though GitHub already reports
    // merged=true. Confirmed live: test-thirty-fourth had 0 PrReviewEntity rows total for the whole
    // project while 2 of its 4 root tasks had already-merged PRs (#4, #5) and were still `pending_review`/
    // `claimed`, stalling the system for 3.5+ hours with idle Jules capacity sitting unused. This checks
    // GitHub's own merge truth directly as a second, independent signal that does not require any local
    // review record to exist at all.
    private void reconcileMergedGitHubPullRequests() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        try {
            List<com.eneik.production.models.persistence.ProjectEntity> activeProjects =
                    projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
            for (com.eneik.production.models.persistence.ProjectEntity project : activeProjects) {
                var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
                if (snapshot == null || !snapshot.available() || snapshot.closed() == null) {
                    continue;
                }
                java.util.Set<String> mergedPrUrls = snapshot.closed().stream()
                        .filter(GitHubPullRequestService.GitHubPullRequest::merged)
                        .map(GitHubPullRequestService.GitHubPullRequest::url)
                        .collect(java.util.stream.Collectors.toSet());
                if (mergedPrUrls.isEmpty()) {
                    continue;
                }

                List<com.eneik.production.models.persistence.JulesSessionEntity> mergedSessions =
                        julesSessionRepository.findAll().stream()
                                .filter(session -> session.getPrUrl() != null && mergedPrUrls.contains(session.getPrUrl()))
                                .toList();
                for (var session : mergedSessions) {
                    com.eneik.production.models.persistence.TaskEntity task =
                            taskRepository.findById(session.getTaskId()).orElse(null);
                    if (task == null || task.getProject() == null
                            || !project.getId().equals(task.getProject().getId())) {
                        continue;
                    }
                    repairTaskForConfirmedMerge(task, session, session.getPrUrl());
                }
            }
        } catch (Exception e) {
            log.warn("AutoMergeService: Failed to reconcile merged GitHub PRs: {}", e.getMessage());
        }
    }

    private void reconcileCleanOpenGitHubPullRequests() {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        try {
            List<com.eneik.production.models.persistence.ProjectEntity> activeProjects =
                    projectRepository.findByStatusOrderByCreatedAtDesc(com.eneik.production.models.persistence.ProjectStatus.active);
            for (com.eneik.production.models.persistence.ProjectEntity project : activeProjects) {
                var snapshot = gitHubPullRequestService.pullRequestSnapshot(project);
                if (snapshot == null || !snapshot.available() || snapshot.open() == null) {
                    continue;
                }
                for (var openPr : snapshot.open()) {
                    var mergeableState = gitHubPullRequestService.mergeableState(project, openPr.number());
                    if (mergeableState.isPresent() && Boolean.TRUE.equals(mergeableState.get().mergeable())) {
                        log.info("AutoMergeService [DIRECT-SWEEP]: Found clean open GitHub PR #{} ({}) for project {}. Executing direct merge...",
                                openPr.number(), openPr.title(), project.getName());
                        boolean merged = gitHubPullRequestService.mergePullRequest(project, openPr.number());
                        if (merged) {
                            log.info("AutoMergeService [DIRECT-SWEEP]: Successfully merged PR #{} directly on GitHub!", openPr.number());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AutoMergeService: Failed to sweep clean open GitHub PRs: {}", e.getMessage());
        }
    }

    private void reconcileLocalMockTasksWithoutReviews() {
        if (settingsService.effectiveBoolean("github_enabled")) {
            return;
        }
        try {
            List<com.eneik.production.models.persistence.TaskEntity> stuckTasks = taskRepository.findAll().stream()
                    .filter(t -> t.getProject() != null && t.getProject().getStatus() == com.eneik.production.models.persistence.ProjectStatus.active)
                    .filter(t -> !"chaotic".equalsIgnoreCase(t.getCynefinDomain()) && !"complex".equalsIgnoreCase(t.getCynefinDomain()))
                    .filter(t -> t.getStatus() == com.eneik.production.models.persistence.TaskStatus.review 
                              || t.getStatus() == com.eneik.production.models.persistence.TaskStatus.pending_review 
                              || t.getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed)
                    .toList();

            for (com.eneik.production.models.persistence.TaskEntity task : stuckTasks) {
                var sessions = julesSessionRepository.findByTaskId(task.getId());
                boolean hasRunningSession = sessions.stream().anyMatch(s -> "running".equalsIgnoreCase(s.getStatus()) || "revising".equalsIgnoreCase(s.getStatus()));
                if (!hasRunningSession) {
                    log.info("AutoMergeService [LOCAL-MOCK]: Automatically completing orphaned task {} ({}) for active project {}",
                            task.getId(), task.getTitle(), task.getProject().getName());
                    task.setStatus(com.eneik.production.models.persistence.TaskStatus.done);
                    taskRepository.save(task);

                    prReviewRepository.findAll().stream()
                            .filter(r -> r.getJulesSessionId() != null)
                            .filter(r -> sessions.stream().anyMatch(s -> s.getId().equals(r.getJulesSessionId())))
                            .forEach(r -> {
                                r.setMerged(true);
                                prReviewRepository.save(r);
                            });
                }
            }
        } catch (Exception e) {
            log.warn("AutoMergeService: Failed to reconcile local mock tasks: {}", e.getMessage());
        }
    }

    private void repairTaskForConfirmedMerge(com.eneik.production.models.persistence.TaskEntity task,
            com.eneik.production.models.persistence.JulesSessionEntity winningSession, String mergedPrUrl) {
        List<com.eneik.production.models.persistence.JulesSessionEntity> taskSessions =
                julesSessionRepository.findByTaskId(task.getId());
        List<com.eneik.production.models.persistence.JulesSessionEntity> activeDuplicates = taskSessions.stream()
                .filter(session -> winningSession == null || !session.getId().equals(winningSession.getId()))
                .filter(session -> List.of("queued", "running", "revising", "stuck", "pr_opened")
                        .contains(session.getStatus()))
                .toList();
        Map<UUID, PrReviewEntity> reviewBySession = prReviewRepository.findAll().stream()
                .filter(review -> review.getJulesSessionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        PrReviewEntity::getJulesSessionId,
                        review -> review,
                        (first, second) -> second));
        List<PrReviewEntity> staleReviews = taskSessions.stream()
                .filter(session -> winningSession == null || !session.getId().equals(winningSession.getId()))
                .map(session -> reviewBySession.get(session.getId()))
                .filter(java.util.Objects::nonNull)
                .filter(review -> !Boolean.TRUE.equals(review.getMerged()))
                .filter(review -> !"superseded".equals(review.getCiStatus()))
                .toList();
        boolean taskNeedsRepair = task.getStatus() != TaskStatus.done;
        // Ф-followup (2026-07-23): the GitHub-truth reconciliation path (reconcileMergedGitHubPullRequests)
        // calls this with a winning session that has no PrReviewEntity at all yet - without one,
        // ClientDeliverableReadinessService.isTaskMerged/isDependencySatisfied can never see this task as
        // merged (they specifically require a merged=true review row, not just TaskStatus.done), so every
        // task depending on it would stay queued forever even after this "repair". Synthesize the missing
        // review here, classifying hasCode the same way the normal merge path does (classifyAndHandleBranch),
        // so non-TAG-09 dependents correctly require real code, not just a done status.
        boolean reviewNeedsSynthesis = winningSession != null && reviewBySession.get(winningSession.getId()) == null;

        if (!taskNeedsRepair && !reviewNeedsSynthesis && activeDuplicates.isEmpty() && staleReviews.isEmpty()) {
            return;
        }

        if (reviewNeedsSynthesis) {
            PrReviewEntity synthesized = new PrReviewEntity();
            synthesized.setJulesSessionId(winningSession.getId());
            synthesized.setPrUrl(mergedPrUrl);
            synthesized.setCiStatus("success");
            synthesized.setRiskLevel("LOW");
            synthesized.setMerged(true);
            synthesized.setHasCode(classifyHasCodeForMergedPr(mergedPrUrl));
            synthesized.setBaseRef(baseRefForMergedPr(task.getProject(), mergedPrUrl));
            prReviewRepository.save(synthesized);
        }

        if (taskNeedsRepair) {
            task.setStatus(TaskStatus.done);
            taskRepository.save(task);
        }
        claimService.releaseTerminalClaim(task.getId());

        for (com.eneik.production.models.persistence.JulesSessionEntity session : activeDuplicates) {
            session.setStatus("cancelled");
            session.setClosedAt(Instant.now());
            session.setClosureReason("Superseded by merged PR " + mergedPrUrl + " for the same task");
            julesSessionRepository.save(session);
        }
        for (PrReviewEntity staleReview : staleReviews) {
            staleReview.setCiStatus("superseded");
            prReviewRepository.save(staleReview);
        }
        taskConflictRepository.findFirstByTaskIdAndResolutionStatus(task.getId(), "pending")
                .ifPresent(conflict -> {
                    conflict.setResolutionStatus("superseded");
                    conflict.setResolvedAt(Instant.now());
                    taskConflictRepository.save(conflict);
                });

        log.info("Poka-yoke: reconciled merged outcome for task {} from PR {}; repaired status={}, "
                        + "retired sessions={}, superseded reviews={}",
                task.getId(), mergedPrUrl, taskNeedsRepair,
                activeDuplicates.size(), staleReviews.size());
    }

    private boolean classifyHasCodeForMergedPr(String prUrl) {
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return false;
        }
        String token = settingsService.effectiveValue("github_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        PullRequestTarget target = parseGithubPullRequestUrl(prUrl);
        if (target == null) {
            return false;
        }
        List<String> changedFiles = fetchPrFiles(token, target.owner(), target.repo(), target.pullNumber());
        return codeChangeClassifier.hasCode(changedFiles);
    }

    // Dashboard-number correctness (2026-07-24) - mirrors classifyHasCodeForMergedPr's pattern for the same
    // GitHub-truth reconciliation path, so a synthesized PrReviewEntity gets a real baseRef instead of
    // silently staying null (which reachedMain() treats as "legacy/unknown, fail open" - correct for old
    // data, but this path creates NEW rows, so it should populate it properly when it can).
    private String baseRefForMergedPr(com.eneik.production.models.persistence.ProjectEntity project, String prUrl) {
        if (project == null) {
            return null;
        }
        PullRequestTarget target = parseGithubPullRequestUrl(prUrl);
        if (target == null) {
            return null;
        }
        try {
            return gitHubPullRequestService.fetchPullRequestByNumber(project, Integer.parseInt(target.pullNumber()))
                    .map(GitHubPullRequestService.GitHubPullRequest::baseRef)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void resolveActiveConflict(UUID julesSessionId) {
        if (julesSessionId == null) return;
        julesSessionRepository.findById(julesSessionId).ifPresent(session -> {
            taskConflictRepository.findFirstByTaskIdAndResolutionStatus(session.getTaskId(), "pending")
                .ifPresent(conflict -> {
                    conflict.setResolutionStatus("auto_resolved");
                    conflict.setResolvedAt(Instant.now());
                    taskConflictRepository.save(conflict);
                    log.info("Conflict resolved for task {} via auto_resolved status", session.getTaskId());
                });
        });
    }

    private List<String> fetchPrFiles(String token, String owner, String repo, String pullNumber) {
        try {
            String filesUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/files";
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(filesUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode filesArray = objectMapper.readTree(response.body());
                List<String> filenames = new ArrayList<>();
                if (filesArray.isArray()) {
                    for (JsonNode fileNode : filesArray) {
                        filenames.add(fileNode.path("filename").asText());
                    }
                }
                return filenames;
            }
        } catch (Exception e) {
            log.error("Failed to fetch PR files: {}", e.getMessage());
        }
        return List.of();
    }

    private String fetchPrDiff(String token, String owner, String repo, String pullNumber) {
        try {
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + pullNumber;
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3.diff")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            log.error("Failed to fetch PR diff: {}", e.getMessage());
        }
        return "";
    }

    private PullRequestTarget resolvePullRequestTarget(PrReviewEntity review) {
        PullRequestTarget parsed = parseGithubPullRequestUrl(review.getPrUrl());
        if (parsed != null) {
            return parsed;
        }

        if (review.getJulesSessionId() == null) {
            return null;
        }

        return julesSessionRepository.findById(review.getJulesSessionId())
                .flatMap(session -> taskRepository.findById(session.getTaskId())
                        .flatMap(task -> gitHubPullRequestService.findOpenPullRequestBySession(task.getProject(), session.getExternalSessionId())))
                .map(pr -> parseGithubPullRequestUrl(pr.url()))
                .orElse(null);
    }

    private PullRequestTarget parseGithubPullRequestUrl(String prUrl) {
        if (prUrl == null || prUrl.isBlank() || prUrl.contains("/mock-")) {
            return null;
        }
        try {
            URI uri = URI.create(prUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return null;
            }
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length >= 4 && "pull".equals(parts[2]) && parts[3].matches("\\d+")) {
                return new PullRequestTarget(prUrl, parts[0], parts[1], parts[3]);
            }
        } catch (Exception e) {
            log.warn("Could not parse GitHub pull request URL {}: {}", prUrl, e.getMessage());
        }
        return null;
    }

    private record PullRequestTarget(String url, String owner, String repo, String pullNumber) {}

    private String getLocalWorkspaceDiff(com.eneik.production.models.persistence.ProjectEntity project) {
        if (project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            java.io.File workspaceDir = new java.io.File(project.getWorkspacePath());
            if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("git", "diff", "HEAD~1");
                    pb.directory(workspaceDir);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );
                    StringBuilder diffSb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        diffSb.append(line).append("\n");
                    }
                    process.waitFor();
                    if (process.exitValue() == 0 && diffSb.length() > 0) {
                        return diffSb.toString();
                    }
                } catch (Exception e) {
                    log.warn("AutoMergeService: Failed to retrieve Git diff from workspace {}: {}",
                            project.getWorkspacePath(), e.getMessage());
                }
            }
        }
        return null;
    }
}
