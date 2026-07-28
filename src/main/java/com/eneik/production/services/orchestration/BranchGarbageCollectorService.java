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

    public BranchGarbageCollectorService(GitHubPullRequestService gitHubPullRequestService,
                                         TaskRepository taskRepository,
                                         TaskConflictRepository taskConflictRepository) {
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
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
}
