package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.logging.LogScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eneik.production.services.MLPredictionServiceClient;

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

    @org.springframework.beans.factory.annotation.Value("${system-stall.threshold-minutes:45}")
    private int stallThresholdMinutes;

    @org.springframework.beans.factory.annotation.Value("${jules.blocked-account-recovery-cooldown-minutes:30}")
    private int blockedAccountRecoveryCooldownMinutes;

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
                                         PlannedWorkRecoveryService plannedWorkRecoveryService) {
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

                int resumedPlanTasks = plannedWorkRecoveryService.resumeNextFrontier(project);
                if (resumedPlanTasks > 0) {
                    log.warn("Continuous Orchestration: Resumed {} existing planned task(s) for project {}; "
                                    + "no new task or wishlist identity was created",
                            resumedPlanTasks, project.getName());
                }

                // Compile any pending wishlist items into tasks. This is the step that used to only run when a
                // human clicked "Orchestrate" or chatted with the operator — without it, autonomous operation
                // stalls the moment the queue drains down to a wishlist item that isn't a circuit-breaker
                // recovery follow-up. orchestrate() is self-rate-limited (5 min/project cooldown), so calling it
                // every cycle is cheap and safe; it's a separate try/catch so its cooldown never skips the
                // recovery/dispatch calls below.
                try {
                    OrchestrationResultDto orchestration = projectFlowService.orchestrate(project.getId());
                    if (orchestration.processedWishlistItems() > 0) {
                        log.info("Continuous Orchestration: Compiled {} wishlist item(s) into {} task(s) for project {}",
                                orchestration.processedWishlistItems(), orchestration.createdTasks().size(), project.getName());
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
                    projectFlowService.dispatchQueuedTasks(project.getId());
                    projectFlowService.dispatchReviewTasks(project.getId());
                } catch (OrchestrationCooldownException e) {
                    log.info("Continuous Orchestration: Skipping project {} for {} seconds because orchestration is on cooldown",
                            project.getId(), e.getRetryAfterSeconds());
                } catch (Exception e) {
                    log.error("Continuous Orchestration: Failed for project {}", project.getId(), e);
                }

                checkForDuplicateTaskTitles(project);
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
    private void checkForDuplicateTaskTitles(ProjectEntity project) {
        try {
            List<TaskEntity> recentTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                    .stream()
                    .limit(30)
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
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run duplicate-title check for project {}", project.getId(), e);
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

            boolean idleCapacityExists = accountRepository.findAll().stream()
                    .anyMatch(a -> a.isEnabled() && a.getStatus() == com.eneik.production.models.persistence.AccountStatus.idle)
                    || taskRepository.countByStatus(com.eneik.production.models.persistence.TaskStatus.queued) > 0
                    || wishlistRepository.findAll().stream()
                            .anyMatch(w -> w.getStatus() == com.eneik.production.models.persistence.WishlistStatus.pending);

            if (idleCapacityExists) {
                log.error("SYSTEM STALLED: no forward progress (dispatch/merge) for {} minutes while idle capacity or pending work exists", minutesSinceProgress);
                setSystemStatus("stalled");
            } else {
                setSystemStatus("idle_no_work");
            }
        } catch (Exception e) {
            log.error("Continuous Orchestration: Failed to run system stall check", e);
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
        int reset = accountRepository.resetDailyLimitedAccounts();
        if (reset > 0) {
            log.info("Continuous Orchestration: Reset {} Jules account(s) from daily_limited to idle", reset);
        }
        accountRepository.resetDailySessionCounts();
    }

    // api_blocked accounts otherwise stay blocked indefinitely once nothing left references them (e.g. the
    // project that blocked them finished or was abandoned) - no other scheduled job ever revisits them, so
    // a block picked up on one project silently starves every future project of that account forever. See
    // AccountRepository.recoverStaleBlockedAccounts for why the cooldown-then-retry approach is safe.
    @Scheduled(fixedRateString = "${jules.blocked-account-recovery-rate-ms:900000}")
    @Transactional
    public void recoverStaleBlockedAccounts() {
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(blockedAccountRecoveryCooldownMinutes));
        int recovered = accountRepository.recoverStaleBlockedAccounts(staleBefore);
        if (recovered > 0) {
            log.info("Continuous Orchestration: Reset {} Jules account(s) from api_blocked to idle after a {}-minute cooldown for retry", recovered, blockedAccountRecoveryCooldownMinutes);
        }
    }

    @Transactional
    public void repairMisclassifiedJulesAccountLimits() {
        int repaired = accountRepository.reclassifyPreconditionDailyLimitedAccounts();
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
