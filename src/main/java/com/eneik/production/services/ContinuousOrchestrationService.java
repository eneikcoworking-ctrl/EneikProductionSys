package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eneik.production.services.MLPredictionServiceClient;

import com.eneik.production.models.persistence.TaskStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ContinuousOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(ContinuousOrchestrationService.class);

    private final ProjectRepository projectRepository;
    private final ProjectFlowService projectFlowService;
    private final AccountRepository accountRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.services.jules.JulesDispatchService julesDispatchService;
    private final com.eneik.production.repositories.WishlistRepository wishlistRepository;
    private final com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final com.eneik.production.repositories.TaskRepository taskRepository;
    private final com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker;
    private final com.eneik.production.services.settings.SystemSettingsService settingsService;
    private final PlannedWorkRecoveryService plannedWorkRecoveryService;
    private final OperationalPolicyService operationalPolicyService;

    @org.springframework.beans.factory.annotation.Value("${system-stall.threshold-minutes:45}")
    private int stallThresholdMinutes;

    private final com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectAuditPipelineService projectAuditPipelineService;

    // 2026-08-01: account-health ownership (status transitions, backoff math, statusChangedAt correctness)
    // moved entirely to AccountHealthService - this class only triggers it on schedule now, matching the
    // "single choke point for a shared invariant" fix for the same class of bug found live today.
    private final com.eneik.production.services.accounts.AccountHealthService accountHealthService;
    // 2026-08-09 (Phase 0, client runtime observability plan) - see ProductLaunchabilityService's own
    // javadoc. Deliberately called from this existing per-project tick, not a new @Scheduled cron.
    private final com.eneik.production.services.runtime.ProductLaunchabilityService productLaunchabilityService;
    private final com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService;

    public ContinuousOrchestrationService(ProjectRepository projectRepository,
                                         ProjectFlowService projectFlowService,
                                         AccountRepository accountRepository,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                         com.eneik.production.services.jules.JulesDispatchService julesDispatchService,
                                         com.eneik.production.repositories.WishlistRepository wishlistRepository,
                                         com.eneik.production.services.compiler.TechnicalLeadCompiler technicalLeadCompiler,
                                         MLPredictionServiceClient mlPredictionServiceClient,
                                         com.eneik.production.repositories.TaskRepository taskRepository,
                                         com.eneik.production.services.monitor.SystemProgressTracker systemProgressTracker,
                                         com.eneik.production.services.settings.SystemSettingsService settingsService,
                                         PlannedWorkRecoveryService plannedWorkRecoveryService,
                                         com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService,
                                         com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                                         OperationalPolicyService operationalPolicyService,
                                         com.eneik.production.services.accounts.AccountHealthService accountHealthService,
                                         com.eneik.production.services.runtime.ProductLaunchabilityService productLaunchabilityService,
                                         com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService) {
        this.projectRepository = projectRepository;
        this.projectFlowService = projectFlowService;
        this.accountRepository = accountRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.julesDispatchService = julesDispatchService;
        this.wishlistRepository = wishlistRepository;
        this.technicalLeadCompiler = technicalLeadCompiler;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.taskRepository = taskRepository;
        this.systemProgressTracker = systemProgressTracker;
        this.settingsService = settingsService;
        this.plannedWorkRecoveryService = plannedWorkRecoveryService;
        this.branchGarbageCollectorService = branchGarbageCollectorService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.operationalPolicyService = operationalPolicyService;
        this.accountHealthService = accountHealthService;
        this.productLaunchabilityService = productLaunchabilityService;
        this.clientRuntimeObservabilityService = clientRuntimeObservabilityService;
    }

    @Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")
    public void continuousOrchestrate() {
        LogScope.system();
        try {
            repairMisclassifiedJulesAccountLimits();

            // runSessionSafetyMaintenance (stuck-session detection, forced blind-overflow unblock, abandoned-
            // PR reconciliation) is NOT called here - it already has its own independent @Scheduled trigger
            // (JulesDispatchService.detectStuck(), same ~60s rate). Calling it a second time from this
            // unrelated scheduled method meant two independent Spring scheduled tasks (which Spring does not
            // mutually exclude - overlap prevention only applies to a task and itself) could both find the
            // same session eligible before either committed its update, sending the same "forced unblock"
            // message to Jules twice within a couple of seconds. Confirmed live on test-thirty-second,
            // operator noticed the literal duplicate message in the Jules UI.

            pollActiveJulesSessions();
            checkForSystemStall();
        } finally {
            LogScope.clear();
        }

        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        for (ProjectEntity project : activeProjects) {
            LogScope.project(project.getId());
            try {
                log.info("Continuous Orchestration: Processing project {}", project.getName());
                if (gitHubPullRequestService != null && isAllowed(project, OperationalAction.SYNC_GITHUB)) {
                    gitHubPullRequestService.syncCiWorkflow(project);
                }
                if (branchGarbageCollectorService != null) {
                    branchGarbageCollectorService.cleanOrphanedAndStagnatedPullRequests(project);
                }
                checkForDuplicateTaskContent(project);

                if (isAllowed(project, OperationalAction.CHECK_LAUNCHABILITY)) {
                    try {
                        productLaunchabilityService.checkOnce(project);
                    } catch (Exception e) {
                        log.error("Continuous Orchestration: launchability check failed for project {}", project.getId(), e);
                    }
                }
                if (isAllowed(project, OperationalAction.OBSERVE_CLIENT_RUNTIME)) {
                    try {
                        clientRuntimeObservabilityService.maybeObserve(project);
                    } catch (Exception e) {
                        log.error("Continuous Orchestration: runtime observability failed for project {}", project.getId(), e);
                    }
                }

                if (isAllowed(project, OperationalAction.RECOVER_FAILED_FRONTIER)) {
                    int resumedPlanTasks = plannedWorkRecoveryService.resumeNextFrontier(project);
                    if (resumedPlanTasks > 0) {
                        log.warn("Continuous Orchestration: Resumed {} existing planned task(s) for project {}; "
                                        + "no new task or wishlist identity was created",
                                resumedPlanTasks, project.getName());
                    }
                }

                plannedWorkRecoveryService.recoverStuckCompilingWishlists(project);
                plannedWorkRecoveryService.cleanoutOrphanedMetaTasksWhenProductComplete(project);

                // Compile any pending wishlist items into tasks. This is the step that used to only run when a
                // human clicked "Orchestrate" or chatted with the operator — without it, autonomous operation
                // stalls the moment the queue drains down to a wishlist item that isn't a circuit-breaker
                // recovery follow-up. orchestrate() is self-rate-limited (5 min/project cooldown), so calling it
                // every cycle is cheap and safe; it's a separate try/catch so its cooldown never skips the
                // recovery/dispatch calls below.
                try {
                    if (isAllowed(project, OperationalAction.ORCHESTRATE)) {
                        OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
                        if (orchestration.processedWishlistItems() > 0) {
                            log.info("Continuous Orchestration: Compiled {} wishlist item(s) into {} task(s) for project {}",
                                    orchestration.processedWishlistItems(), orchestration.createdTasks().size(), project.getName());
                        }
                    } else {
                        log.warn("Continuous Orchestration: Flow policy denies wishlist compilation for project {}; skipping",
                                project.getName());
                    }
                } catch (OrchestrationCooldownException e) {
                    log.debug("Continuous Orchestration: Orchestration on cooldown for project {} ({}s remaining)",
                            project.getId(), e.getRetryAfterSeconds());
                } catch (Exception e) {
                    log.error("Continuous Orchestration: Failed to compile wishlist for project {}", project.getId(), e);
                }

                try {
                    int recovered = projectFlowService.recoverBlockedWork(project.getId());
                    if (recovered > 0) {
                        log.info("Continuous Orchestration: Recovered {} blocked work item(s) for project {}",
                                recovered, project.getName());
                    }
                    if (isAllowed(project, OperationalAction.DISPATCH_QUEUED_TASKS)) {
                        projectFlowService.dispatchQueuedTasks(project.getId());
                    } else {
                        log.warn("Continuous Orchestration: Flow policy denies queued-task dispatch for project {}; skipping",
                                project.getName());
                    }
                    if (isAllowed(project, OperationalAction.DISPATCH_REVIEW_TASKS)) {
                        projectFlowService.dispatchReviewTasks(project.getId());
                    } else {
                        log.warn("Continuous Orchestration: Flow policy denies review dispatch for project {}; skipping",
                                project.getName());
                    }
                } catch (OrchestrationCooldownException e) {
                    log.info("Continuous Orchestration: Skipping project {} for {} seconds because orchestration is on cooldown",
                            project.getId(), e.getRetryAfterSeconds());
                } catch (Exception e) {
                    log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
                }

                try {
                    if (isAllowed(project, OperationalAction.CHECK_COVERAGE_AUDITS)) {
                        projectFlowService.checkAndDispatchCoverageAudits(project.getId());
                    }
                    if (projectAuditPipelineService != null && isAllowed(project, OperationalAction.RUN_PROJECT_AUDIT_PIPELINE)) {
                        projectAuditPipelineService.executeSequentialAuditPipeline(project.getId());
                    }
                } catch (Exception e) {
                    log.error("Continuous Orchestration: Failed to check coverage-audit eligibility for project {}", project.getId(), e);
                }

            } finally {
                LogScope.clear();
            }
        }
    }

    /**
     * Content-truthfulness signal, not process activity: flags when a project's recent tasks share
     * suspiciously repeated titles. This is exactly the failure mode a Gemini-generation fallback bug
     * produced for hours across two separate runs (generic titles like "recommendation based task
     * completion slice 1" repeated verbatim across many unrelated tasks) - visible instantly to a human
     * skimming PR titles, but invisible to every process-activity metric this system had (dispatch
     * counts, merge counts, stall detection). Direct, cheap, catches the exact class of regression.
     */
    // Ф-followup (2026-07-21, operator directive): TaskEntity.getTitle() is a generic role-template string
    // ("API Slice" for any BARCAN-TAG-02 slice, "UI Slice" for any BARCAN-TAG-11 slice) - confirmed live to
    // false-positive on genuinely distinct work that just happens to share a role (3 unrelated tasks - an
    // original CRUD endpoint slice, an unrelated auth-endpoints follow-up, and a dead-code cleanup - all
    // flagged as "duplicates" purely because all three were BARCAN-TAG-02). Use the actual distinguishing
    // content instead: payload.slice_title carries the real per-slice semantic content ("Internal work
    // item N from wishlist X: <the real JTBD-derived description>"), falling back to the full description
    // for any task that didn't go through the epic-slice compiler path.
    private boolean isAllowed(ProjectEntity project, OperationalAction action) {
        try {
            var decision = operationalPolicyService.authorize(project.getId(), action);
            if (!decision.allowed()) {
                log.info("Continuous Orchestration: policy denied {} for project {} in state {}: {}",
                        action, project.getName(), decision.state(), decision.reason());
            }
            return decision.allowed();
        } catch (Exception e) {
            log.warn("Continuous Orchestration: policy check failed for {} on project {}; failing closed: {}",
                    action, project.getId(), e.getMessage());
            return false;
        }
    }

    // 2026-08-04 (live incident, test-forty-first): this used to count ALL of the last 30 tasks regardless
    // of status, including ones already done/failed/blocked long ago. That created a permanent deadlock -
    // once 3+ historical (fully resolved) duplicates existed in that window, BLOCKED_BY_DUPLICATE_CONTENT
    // (a hard-stop state, see OperationalFlowCoreService.isHardStopState) blocked wishlist compilation AND
    // task dispatch alike, so no NEW task could ever be created to push the old duplicates out of the
    // window - the only way out was more dispatch, which was exactly what the block forbade. Confirmed
    // live: 4 duplicate pairs, all long since `done`, kept the entire project stuck for hours with no
    // recovery path (this state has no Gemini lever either). The detector's real purpose is catching a
    // generation fallback actively wasting capacity RIGHT NOW - a duplicate that already completed spent
    // its waste once and is not still spending it, so it must not count toward the block.
    private static final java.util.Set<TaskStatus> TERMINAL_STATUSES = java.util.Set.of(
            TaskStatus.done, TaskStatus.failed, TaskStatus.blocked, TaskStatus.spike_completed);

    private boolean checkForDuplicateTaskContent(ProjectEntity project) {
        try {
            List<TaskEntity> recentTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                    .stream()
                    .limit(30)
                    .filter(task -> !TERMINAL_STATUSES.contains(task.getStatus()))
                    .toList();
            java.util.Map<String, Long> contentCounts = recentTasks.stream()
                    .map(this::duplicateDetectionKey)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(key -> key, java.util.stream.Collectors.counting()));
            contentCounts.forEach((content, count) -> {
                if (count >= 3) {
                    log.error("DUPLICATE TASK CONTENT: a task with this exact content appears {} times among the "
                                    + "last {} tasks for project {} - likely sign of a generation fallback producing "
                                    + "generic/fabricated content instead of real derived work. Content: '{}'. "
                                    + "Investigate before trusting recent throughput numbers.",
                            count, recentTasks.size(), project.getName(), preview(content));
                }
            });
            boolean duplicated = contentCounts.values().stream().anyMatch(count -> count >= 3);
            if (duplicated) {
                setSystemStatus("content_defect");
            }
            return duplicated;
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run duplicate-title check for project {}", project.getId(), e);
            return false;
        }
    }

    private String duplicateDetectionKey(TaskEntity task) {
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle;
            }
        }
        return task.getDescription();
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /**
     * Flags SYSTEM-level stall: idle capacity clearly exists (an idle/enabled account, or a project
     * with pending wishlist/queued work) but nothing genuinely progressed (dispatch, merge) for the
     * configured window. This is what the first 8h unattended run had no way to surface - hours of
     * quiet with idle Jules accounts and open work looked identical to "nothing left to do".
     */
    private void checkForSystemStall() {
        try {
            long minutesSinceProgress = java.time.Duration.between(
                    systemProgressTracker.lastProgressAt(), java.time.Instant.now()).toMinutes();
            if (minutesSinceProgress < stallThresholdMinutes) {
                setSystemStatus("ok");
                return;
            }

            SystemWorkSnapshot work = systemWorkSnapshot();
            if (!work.hasActionableWork()) {
                setSystemStatus("idle_no_actionable_work");
                return;
            }

            boolean idleCapacityExists = accountRepository.findAll().stream()
                    .anyMatch(a -> a.isEnabled() && a.getStatus() == com.eneik.production.models.persistence.AccountStatus.idle);

            if (idleCapacityExists) {
                log.error("SYSTEM STALLED: no forward progress (dispatch/merge) for {} minutes with actionable work present: {}.", minutesSinceProgress, work.describe());
                setSystemStatus("stalled");

                if (branchGarbageCollectorService != null && work.reviewTasksWithPr() > 0) {
                    List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
                    for (ProjectEntity project : activeProjects) {
                        List<TaskEntity> reviewTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.review);
                        for (TaskEntity t : reviewTasks) {
                            var prOpt = julesSessionRepository.findByTaskId(t.getId()).stream()
                                    .filter(s -> s.getPrUrl() != null && !s.getPrUrl().isBlank())
                                    .findFirst();
                            if (prOpt.isPresent()) {
                                String prUrl = prOpt.get().getPrUrl();
                                String prNumStr = prUrl.substring(prUrl.lastIndexOf("/") + 1);
                                try {
                                    int prNum = Integer.parseInt(prNumStr);
                                    String branch = gitHubPullRequestService.fetchPullRequestByNumber(project, prNum)
                                            .map(com.eneik.production.services.github.GitHubPullRequestService.GitHubPullRequest::headRef)
                                            .orElse(null);
                                    branchGarbageCollectorService.retireAbandonedBranchAndPR(project, t, branch, prNum, "System Stall auto-recovery (> " + minutesSinceProgress + "m idle)");
                                } catch (Exception ex) {
                                    log.warn("Continuous Orchestration: Failed to parse/retire PR #{} for task {}: {}", prNumStr, t.getId(), ex.getMessage());
                                }
                            }
                        }
                    }
                } else {
                    log.warn("SYSTEM STALLED: Branch Garbage Collector not triggered because no review task with a PR URL is actionable.");
                }
            } else {
                setSystemStatus("busy_with_actionable_work");
            }
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run system stall check", e);
        }
    }

    SystemWorkSnapshot systemWorkSnapshot() {
        List<ProjectEntity> activeProjects = projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active);
        List<TaskEntity> activeProjectTasks = activeProjects.stream()
                .flatMap(project -> taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream())
                .toList();
        long queuedTasks = activeProjectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.queued)
                .count();
        long pendingWishlists = activeProjects.stream()
                .flatMap(project -> wishlistRepository.findByProjectId(project.getId()).stream())
                .filter(w -> w.getStatus() == com.eneik.production.models.persistence.WishlistStatus.pending
                        || w.getStatus() == com.eneik.production.models.persistence.WishlistStatus.compiling)
                .count();
        long activeNonTerminalTasks = activeProjectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.claimed
                        || task.getStatus() == TaskStatus.in_progress
                        || task.getStatus() == TaskStatus.pending_review
                        || task.getStatus() == TaskStatus.review)
                .count();
        long reviewTasksWithPr = activeProjectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.review)
                .filter(task -> julesSessionRepository.findByTaskId(task.getId()).stream()
                        .anyMatch(session -> session.getPrUrl() != null && !session.getPrUrl().isBlank()))
                .count();
        return new SystemWorkSnapshot(queuedTasks, pendingWishlists, activeNonTerminalTasks, reviewTasksWithPr);
    }

    record SystemWorkSnapshot(long queuedTasks, long pendingWishlists, long activeNonTerminalTasks, long reviewTasksWithPr) {
        boolean hasActionableWork() {
            return queuedTasks > 0 || pendingWishlists > 0 || activeNonTerminalTasks > 0;
        }

        String describe() {
            return "queuedTasks=" + queuedTasks
                    + ", pendingOrCompilingWishlists=" + pendingWishlists
                    + ", activeNonTerminalTasks=" + activeNonTerminalTasks
                    + ", reviewTasksWithPr=" + reviewTasksWithPr;
        }
    }

    private void setSystemStatus(String status) {
        // Best-effort only: system_status is observational, never let a settings write break orchestration.
        try {
            settingsService.save("system_stall_status", status);
        } catch (Exception e) {
            log.debug("Continuous Orchestration: could not persist system status '{}': {}", status, e.getMessage());
        }
    }

    @Scheduled(cron = "${jules.daily-limit-reset-cron:0 5 0 * * ?}")
    @Transactional
    public void resetDailyLimitedAccounts() {
        int reset = accountHealthService.resetDailyLimitedAccounts();
        if (reset > 0) {
            log.info("Continuous Orchestration: Reset {} Jules account(s) from daily_limited to idle", reset);
        }
        accountRepository.resetDailySessionCounts();
    }

    // api_blocked accounts otherwise stay blocked indefinitely once nothing left references them (e.g. the
    // project that blocked them finished or was abandoned) - no other scheduled job ever revisits them, so
    // a block picked up on one project silently starves every future project of that account forever.
    // 2026-08-01: the actual recovery math (data-driven, median+z*sigma of observed recovery durations,
    // falling back to exponential backoff when too few samples exist) now lives entirely in
    // AccountHealthService - this is just the scheduled trigger.
    @Scheduled(fixedRateString = "${jules.blocked-account-recovery-rate-ms:900000}")
    @Transactional
    public void recoverStaleBlockedAccounts() {
        int recovered = accountHealthService.recoverEligibleAccounts();
        if (recovered > 0) {
            log.info("Continuous Orchestration: Reset {} Jules account(s) from api_blocked to idle for retry", recovered);
        }
    }

    @Transactional
    public void repairMisclassifiedJulesAccountLimits() {
        int repaired = accountHealthService.reclassifyPreconditionDailyLimitedAccounts();
        if (repaired > 0) {
            log.warn("Continuous Orchestration: Reclassified {} Jules account(s) from daily_limited to api_blocked because the latest evidence is precondition/API refusal, not quota", repaired);
        }
    }

    private void pollActiveJulesSessions() {
        try {
            List<com.eneik.production.models.persistence.JulesSessionEntity> activeSessions = julesSessionRepository.findAll().stream()
                    .filter(s -> "running".equals(s.getStatus())
                            || "queued".equals(s.getStatus())
                            || "revising".equals(s.getStatus())
                            || "stuck".equals(s.getStatus()))
                    .toList();
            for (com.eneik.production.models.persistence.JulesSessionEntity session : activeSessions) {
                taskRepository.findById(session.getTaskId())
                        .map(task -> task.getProject())
                        .ifPresentOrElse(
                                project -> LogScope.project(project.getId()),
                                LogScope::system
                        );
                try {
                    julesDispatchService.pollStatus(session.getId());
                } catch (Exception e) {
                    log.error("Failed to poll status for Jules session {}", session.getId(), e);
                } finally {
                    LogScope.clear();
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll active Jules sessions", e);
        }
    }
}
