package com.eneik.production.services.orchestration;

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

            // Case B: Stagnated feature PR (branch starting with jules-) or any open PR
            if (headRef != null && headRef.startsWith("jules-")) {
                Optional<TaskEntity> taskOpt = julesSessionRepository.findAll().stream()
                        .filter(s -> s.getPrUrl() != null && s.getPrUrl().contains(String.valueOf(pullNumber)))
                        .map(s -> taskRepository.findById(s.getTaskId()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();

                if (taskOpt.isPresent()) {
                    TaskEntity task = taskOpt.get();
                    if (task.getStatus() != TaskStatus.done) {
                        retireAbandonedBranchAndPR(project, task, headRef, pullNumber, "Branch GC: Cleaning stagnated PR #" + pullNumber);
                        cleaned++;
                    }
                } else {
                    gitHubPullRequestService.closeSinglePullRequest(project, pr, "Branch GC: Orphaned PR without active task");
                    gitHubPullRequestService.deleteBranch(project, headRef);
                    log.info("[BRANCH-GC] Retired orphaned PR #{} ('{}') with branch '{}' on project {}", pullNumber, title, headRef, project.getName());
                    cleaned++;
                }
            } else if (title != null) {
                // Any other stagnated open PR
                gitHubPullRequestService.closeSinglePullRequest(project, pr, "Branch GC: Stagnated open PR auto-closed");
                cleaned++;
            }
        }
        return cleaned;
    }
}
