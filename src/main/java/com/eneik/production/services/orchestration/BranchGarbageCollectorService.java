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

    // Same trust-window concept JulesDispatchService's own stall detection uses (DAVIDSON_TRUST_WINDOW_MINUTES)
    // - reused here rather than inventing a second number for "how long is silence before we distrust it".
    private static final int STALENESS_TRUST_WINDOW_MINUTES = 60;

    private final GitHubPullRequestService gitHubPullRequestService;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final com.eneik.production.repositories.JulesSessionRepository julesSessionRepository;
    private final com.eneik.production.services.jules.SessionLifecycleService sessionLifecycleService;
    private final com.eneik.production.services.ProjectFlowService projectFlowService;

    public BranchGarbageCollectorService(GitHubPullRequestService gitHubPullRequestService,
                                         TaskRepository taskRepository,
                                         TaskConflictRepository taskConflictRepository,
                                         com.eneik.production.repositories.JulesSessionRepository julesSessionRepository,
                                         com.eneik.production.services.jules.SessionLifecycleService sessionLifecycleService,
                                         com.eneik.production.services.ProjectFlowService projectFlowService) {
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.sessionLifecycleService = sessionLifecycleService;
        this.projectFlowService = projectFlowService;
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

        // Step 3.5: Close any non-terminal JulesSessionEntity still pointing at the branch/PR just retired
        // above (2026-08-01, confirmed live on test-fortieth/d9f35f4b). Without this, the OLD session sits
        // in "pr_opened" - one of JulesDispatchService.ACTIVE_SESSION_STATUSES - which its own dispatch()
        // duplicate-guard reads as "this task already has an active session in flight" and refuses to
        // create a new one, even though the PR/branch that session referred to was just closed/deleted a
        // moment ago. Step 4 re-queues the TASK, but a stale ACTIVE session silently swallows every
        // redispatch attempt that follows - the task sits "queued" forever with no real session working it,
        // until GitHub-truth reconciliation eventually notices the closed PR and marks it failed, hours
        // later, with nothing left to retry it.
        //
        // 2026-08-01: routed through SessionLifecycleService.retireSessionOnly instead of a local-only copy
        // of JulesDispatchService.cancelSession's status-flip - a second real bug this exact duplication
        // caused (found live): the review-liveness filter in FlowSpineService/OperationalTruthService only
        // ever excluded a review whose TASK reached a terminal status, never one whose SESSION was
        // individually retired this way while the task lived on for a fresh attempt (test-fortieth/PR#119,
        // task 72ec0f54) - a dead review kept BLOCKED_BY_REVIEW stuck indefinitely. That gap is now fixed at
        // the filter itself, but the duplication that made "cancelled" mean two slightly different things in
        // two places was the same charter #10 violation as the account-status one - one call site now, not two.
        // 2026-08-03 (confirmed live on test-forty-first, task 074efcb3): the loop below used to cancel
        // EVERY active session on the task, not just the one tied to the branch/PR actually being retired
        // above. A task can legitimately have more than one concurrent session/PR (e.g. an earlier attempt
        // still winding down while a later one already succeeded) - cancelling all of them here silently
        // orphaned a completely unrelated, genuinely successful PR (#38) the moment a DIFFERENT stale PR
        // (#37) on the same task got cleaned up. Its session was cancelled, which excludes it from every
        // other sweep in the system (all of them filter by active session status), so the real PR sat open
        // on GitHub, invisible, for hours. Scope this to only the session whose externalSessionId matches
        // the branch actually being retired - same token-substring scheme GitHubPullRequestService.
        // matchesSessionToken and this file's own cleanOrphanedAndStagnatedPullRequests already use. If
        // branchName is blank we have no way to know which session is ours to touch, so skip rather than
        // guess - guessing was exactly today's bug.
        if (branchName == null || branchName.isBlank()) {
            log.warn("[BRANCH-GC] No branch name available while retiring task {}; skipping session cleanup (Step 3.5) "
                    + "rather than guessing which session to cancel", task.getId());
        } else {
            for (JulesSessionEntity session : julesSessionRepository.findByTaskId(task.getId())) {
                if (!java.util.Set.of("running", "queued", "revising", "pr_opened", "stuck").contains(session.getStatus())) {
                    continue;
                }
                String externalId = session.getExternalSessionId();
                if (externalId == null || externalId.isBlank()) {
                    continue;
                }
                String token = externalId.startsWith("sessions/") ? externalId.substring("sessions/".length()) : externalId;
                if (token.isBlank() || !branchName.contains(token)) {
                    // A different session on the same task - e.g. another still-legitimate PR - not ours to touch.
                    continue;
                }
                String originalStatus = session.getStatus();
                sessionLifecycleService.retireSessionOnly(session.getId(), "Branch GC: superseded by fresh re-queue - " + reason);
                log.info("[BRANCH-GC] Cancelled stale session {} (was {}) for task {} so redispatch is not blocked",
                        session.getId(), originalStatus, task.getId());
            }
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

            // Token-based evidence, not a guessed clock (2026-07-31, replacing yesterday's own
            // MIN_PR_AGE_BEFORE_CLEANUP patch): the previous version matched a PR to its owning session by
            // exact `session.prUrl` string equality - a field that only gets written back to our DB in a
            // separate round trip AFTER Jules reports pr_opened, racing this very sweep (confirmed live: a
            // 13-second-old PR with real committed, mergeable work - PR#3, task ff03b176 - was closed by
            // this exact race before that field had a chance to populate). A fixed N-minute grace period
            // only worked around the symptom. The session's externalSessionId, by contrast, is written at
            // dispatch time - before Jules ever pushes a branch or opens a PR - so matching the PR's branch
            // name against it via the same session-token scheme every other reconciliation path in this
            // codebase already uses (GitHubPullRequestService.matchesSessionToken) has no such race at all:
            // by construction, if a PR exists, the session that will eventually own it already does too.
            Optional<JulesSessionEntity> sessionOpt = julesSessionRepository.findAll().stream()
                    .filter(s -> s.getExternalSessionId() != null && !s.getExternalSessionId().isBlank())
                    .filter(s -> GitHubPullRequestService.matchesSessionToken(pr, s.getExternalSessionId()))
                    .findFirst();

            if (sessionOpt.isPresent()) {
                JulesSessionEntity session = sessionOpt.get();
                Optional<TaskEntity> taskOpt = taskRepository.findById(session.getTaskId());
                if (taskOpt.isPresent()) {
                    TaskEntity task = taskOpt.get();
                    if (task.getStatus() == TaskStatus.done) {
                        continue;
                    }
                    // 2026-08-03 (confirmed live risk during philosophical-audit persistent-worker design):
                    // a persistent worker (compiler/review-fallback/philosophical-audit) deliberately parks
                    // at pr_opened for long stretches between follow-ups - by design, not stagnation. This
                    // sweep had no awareness of that at all and would destructively close the PR, delete the
                    // branch, and re-queue the carrier task the moment STALENESS_TRUST_WINDOW_MINUTES elapsed,
                    // silently wiping out an in-progress multi-cycle worker (already a live risk for the two
                    // pre-existing purposes, not just the new one).
                    if (projectFlowService.isPersistentWorkerCarrierTask(task)) {
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

            // No session anywhere - past or present - was ever dispatched with a token matching this PR's
            // branch name. Since a session's token exists from dispatch time onward, this is not a timing
            // gap: nothing will ever come along later to claim this PR. Genuinely orphaned.
            gitHubPullRequestService.closeSinglePullRequest(project, pr, "Branch GC: Orphaned PR without active task");
            gitHubPullRequestService.deleteBranch(project, headRef);
            log.info("[BRANCH-GC] Retired orphaned PR #{} ('{}') with branch '{}' on project {}", pullNumber, title, headRef, project.getName());
            cleaned++;
        }
        return cleaned;
    }

    /**
     * A GitHub PR whose owning session ended up in a terminal/inactive status (cancelled,
     * closed_terminal_task, failed) while the PR itself is still open - exactly the shape of the
     * 2026-08-03 incident (task 074efcb3/PR#38): real, successful work whose session got collaterally
     * cancelled and is now invisible to every status-filtered sweep in the system. Distinct from a
     * genuinely orphaned PR (no session anywhere ever matched it, handled by
     * cleanOrphanedAndStagnatedPullRequests's own "no session anywhere" branch) - here a session DID exist
     * and DID do the work, it just never got to finish being processed.
     */
    public record OrphanedPrCandidate(java.util.UUID taskId, int pullNumber, String pullUrl, String headRef,
                                       String sessionStatus, com.eneik.production.models.persistence.TaskEntity task) {
    }

    private static final java.util.Set<String> ORPHANED_CANDIDATE_SESSION_STATUSES =
            java.util.Set.of("cancelled", "closed_terminal_task", "failed");

    /**
     * Read-only detector reused by both GeminiProjectObserverService (surfacing this as an evidence signal)
     * and GeminiObserverActionService.resolveOrphanedPr (re-verifying at execution time) - one source of
     * truth for what counts as an orphan, not two copies of the same matching logic.
     */
    public List<OrphanedPrCandidate> findOrphanedPrCandidates(ProjectEntity project) {
        if (project == null) return List.of();
        var openPrs = gitHubPullRequestService.fetchOpenPullRequests(project);
        if (openPrs.isEmpty()) return List.of();

        List<JulesSessionEntity> allSessions = julesSessionRepository.findAll();
        List<OrphanedPrCandidate> candidates = new java.util.ArrayList<>();
        for (var pr : openPrs) {
            JulesSessionEntity match = allSessions.stream()
                    .filter(s -> s.getExternalSessionId() != null && !s.getExternalSessionId().isBlank())
                    .filter(s -> GitHubPullRequestService.matchesSessionToken(pr, s.getExternalSessionId()))
                    .findFirst()
                    .orElse(null);
            if (match == null || !ORPHANED_CANDIDATE_SESSION_STATUSES.contains(match.getStatus())) {
                continue;
            }
            TaskEntity task = taskRepository.findById(match.getTaskId()).orElse(null);
            if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
                continue;
            }
            if (task.getStatus() == TaskStatus.done) {
                // Consistent with cleanOrphanedAndStagnatedPullRequests's own done-task skip - a "done" task
                // is presumed already handled elsewhere (e.g. genuinely merged via a different PR); not this
                // detector's job to second-guess that.
                continue;
            }
            candidates.add(new OrphanedPrCandidate(task.getId(), pr.number(), pr.url(), pr.headRef(), match.getStatus(), task));
        }
        return candidates;
    }
}
