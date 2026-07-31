package com.eneik.production.services.orchestration;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.github.GitHubPullRequestService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic Branch Garbage Collector Service.
 * Enforces the invariant: N_active_branches(taskId) <= 1.
 * When a branch/PR becomes dirty or stagnated beyond the dynamic 3-Sigma threshold,
 * this service closes the old PR, deletes the old branch from GitHub, supersedes
 * old review records, and re-queues the task off fresh main at Priority 100.
 */
@Service
public class BranchGarbageCollectorService {

    private static final Logger log = LoggerFactory.getLogger(BranchGarbageCollectorService.class);

    // Evidence-based grace period (2026-07-31): a PR younger than this is never touched, regardless of
    // task/session state - protects against acting before the orchestrator has even finished linking a
    // fresh PR to its session (confirmed live: a 36-second-old PR with real committed work was closed by
    // this sweep before any real evidence could exist either way).
    private static final Duration MIN_PR_AGE_BEFORE_CLEANUP = Duration.ofMinutes(10);
    // Same trust-window concept JulesDispatchService's own stall detection uses (DAVIDSON_TRUST_WINDOW_MINUTES)
    // - reused here rather than inventing a second number for "how long is silence before we distrust it".
    private static final int STALENESS_TRUST_WINDOW_MINUTES = 60;

    private final GitHubPullRequestService gitHubPullRequestService;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;

    public BranchGarbageCollectorService(GitHubPullRequestService gitHubPullRequestService,
                                         TaskRepository taskRepository,
                                         TaskConflictRepository taskConflictRepository,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository) {
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.julesSessionRepository = julesSessionRepository;
    }

    /**
     * Retires an abandoned or stagnated branch & PR for a task.
     * Deletes old branch, closes old PR, resets task status to queued with Priority 100.
     */
    @Transactional
    public boolean retireAbandonedBranchAndPR(ProjectEntity project,
                                             TaskEntity task,
                                             String branchName,
                                             Integer pullNumber,
                                             String reason) {
        if (project == null || task == null) {
            log.warn("[BRANCH-GC] Cannot retire branch: project or task is null");
            return false;
        }

        log.info("[BRANCH-GC][FSM-TRANSITION] Retiring abandoned/stagnated branch '{}' and PR #{} for task '{}' (ID: {}). Reason: {}",
                branchName, pullNumber, task.getTitle(), task.getId(), reason);

        // Step 1: Close PR on GitHub if pullNumber is provided
        if (pullNumber != null && pullNumber > 0) {
            try {
                Optional<GitHubPullRequestService.GitHubPullRequest> prOpt =
                        gitHubPullRequestService.fetchPullRequestByNumber(project, pullNumber);
                if (prOpt.isPresent()) {
                    gitHubPullRequestService.closeSinglePullRequest(
                            project, prOpt.get(), "Branch GC: " + reason);
                    log.info("[BRANCH-GC] Closed GitHub PR #{} for task {}", pullNumber, task.getId());
                }
            } catch (Exception e) {
                log.warn("[BRANCH-GC] Could not close PR #{}: {}", pullNumber, e.getMessage());
            }
        }

        // Step 2: Delete old branch from GitHub if branchName is provided
        if (branchName != null && !branchName.isBlank()) {
            try {
                boolean deleted = gitHubPullRequestService.deleteBranch(project, branchName);
                if (deleted) {
                    log.info("[BRANCH-GC] Successfully deleted branch '{}' from GitHub for task {}", branchName, task.getId());
                }
            } catch (Exception e) {
                log.warn("[BRANCH-GC] Could not delete branch '{}': {}", branchName, e.getMessage());
            }
        }

        // Step 3: Clear or mark TaskConflict records as resolved/superseded
        Optional<TaskConflictEntity> conflictOpt = taskConflictRepository.findFirstByTaskIdAndResolutionStatus(task.getId(), "pending");
        if (conflictOpt.isPresent()) {
            TaskConflictEntity c = conflictOpt.get();
            c.setResolutionAttempts(99);
            c.setResolutionStatus("superseded");
            taskConflictRepository.save(c);
        }

        // Step 4: Re-queue task off clean main with Priority 100
        task.setStatus(TaskStatus.queued);
        task.setPriority(100);
        taskRepository.save(task);

        log.info("[BRANCH-GC][SUCCESS] Task '{}' (ID: {}) re-queued off fresh main with Priority 100. Old branch retired.",
                task.getTitle(), task.getId());
        return true;
    }

    /**
     * Scans GitHub open PRs for a project, closing orphaned/superseded PRs and retiring stagnated ones.
     */
    @Transactional
    public int cleanOrphanedAndStagnatedPullRequests(ProjectEntity project) {
        if (project == null) return 0;
        var openPrs = gitHubPullRequestService.fetchOpenPullRequests(project);
        log.info("[BRANCH-GC] Found {} open PR(s) on GitHub for project {}", openPrs.size(), project.getName());
        if (openPrs.isEmpty()) return 0;

        Instant now = Instant.now();
        int cleaned = 0;
        for (var pr : openPrs) {
            String title = pr.title();
            String headRef = pr.headRef();
            int pullNumber = pr.number();
            log.info("[BRANCH-GC] Inspecting open PR #{} ('{}') headRef='{}'", pullNumber, title, headRef);

            // Case A: Closeout PR where the feature has already been merged via another PR
            if (title != null && title.startsWith("Closeout")) {
                gitHubPullRequestService.closeSinglePullRequest(project, pr, "Branch GC: Orphaned closeout PR superseded by main");
                log.info("[BRANCH-GC] Closed orphaned closeout PR #{} ('{}') on project {}", pullNumber, title, project.getName());
                cleaned++;
                continue;
            }

            // Grace period first, before anything else - a PR younger than this has not had a fair chance
            // to be linked to its session yet, regardless of what its branch happens to be named.
            if (pr.createdAt() == null || pr.createdAt().isAfter(now.minus(MIN_PR_AGE_BEFORE_CLEANUP))) {
                continue;
            }

            // Evidence-based decision (2026-07-31): look for a real, live session tied to this PR for
            // EVERY open PR, never gated on how Jules happened to name its branch (that naming-convention
            // check used to skip this lookup entirely for any branch not starting with "jules-", closing a
            // brand-new PR with real committed work purely because of its name).
            Optional<JulesSessionEntity> sessionOpt = julesSessionRepository.findAll().stream()
                    .filter(s -> s.getPrUrl() != null && s.getPrUrl().contains(String.valueOf(pullNumber)))
                    .findFirst();

            if (sessionOpt.isPresent()) {
                JulesSessionEntity session = sessionOpt.get();
                Optional<TaskEntity> taskOpt = taskRepository.findById(session.getTaskId());
                if (taskOpt.isPresent()) {
                    TaskEntity task = taskOpt.get();
                    if (task.getStatus() == TaskStatus.done) {
                        continue;
                    }
                    // Real staleness evidence - the session's own last observed progress - not a status
                    // snapshot alone. A task that is simply still in progress must never be retired just
                    // because its status isn't literally "done" yet.
                    Instant lastProgress = session.getLastProgressAt() != null ? session.getLastProgressAt() : session.getCreatedAt();
                    boolean stale = lastProgress != null && lastProgress.isBefore(now.minus(Duration.ofMinutes(STALENESS_TRUST_WINDOW_MINUTES)));
                    if (stale) {
                        retireAbandonedBranchAndPR(project, task, headRef, pullNumber, "Branch GC: Cleaning stagnated PR #" + pullNumber);
                        cleaned++;
                    }
                    continue;
                }
            }

            // No live session/task found anywhere for this PR, and it has already cleared the grace
            // period above - genuinely orphaned.
            gitHubPullRequestService.closeSinglePullRequest(project, pr, "Branch GC: Orphaned PR without active task");
            gitHubPullRequestService.deleteBranch(project, headRef);
            log.info("[BRANCH-GC] Retired orphaned PR #{} ('{}') with branch '{}' on project {}", pullNumber, title, headRef, project.getName());
            cleaned++;
        }
        return cleaned;
    }
}
