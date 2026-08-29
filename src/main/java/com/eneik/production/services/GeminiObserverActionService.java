package com.eneik.production.services;

import com.eneik.production.models.persistence.GeminiObserverActionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskConflictEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The observer's real, non-code powers (2026-07-26, operator directive: "we give her every authority - except
 * code"). Every method here is a thin, guarded wrapper over an operation that already exists elsewhere in
 * the codebase for a human/scheduled caller - nothing here is a new capability, only a new, safe entry
 * point for {@link GeminiProjectObserverService} to reach it. Deliberately excludes anything that writes
 * source code, touches git, or dispatches a fresh coding session against unreviewed judgment - those stay
 * Jules-on-client-projects-only or human/Claude-only, per operator directive ("only you and I deal with
 * that", 2026-07-26 - system-repo code stays off-limits to any autonomous agent).
 *
 * Every call is scoped to the SAME project the observer is currently evaluating (never trusts a
 * cross-project id), and every outcome - success, skipped, or failed - is persisted to
 * {@link GeminiObserverActionEntity} regardless of what she later claims in her journal prose. That table,
 * not her journal, is the audit trail (testimony vs evidence).
 */
@Service
public class GeminiObserverActionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiObserverActionService.class);

    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final TaskConflictRepository taskConflictRepository;
    private final JulesDispatchService julesDispatchService;
    private final FalsificationCycleService falsificationCycleService;
    private final GeminiObserverActionRepository actionRepository;
    private final OperationalPolicyService operationalPolicyService;
    private final PlannedWorkRecoveryService plannedWorkRecoveryService;
    private final com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService;
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository;
    private final com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService;
    private final com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService;

    public GeminiObserverActionService(WishlistRepository wishlistRepository,
                                        TaskRepository taskRepository,
                                        TaskConflictRepository taskConflictRepository,
                                        JulesDispatchService julesDispatchService,
                                        FalsificationCycleService falsificationCycleService,
                                        GeminiObserverActionRepository actionRepository,
                                        OperationalPolicyService operationalPolicyService,
                                        PlannedWorkRecoveryService plannedWorkRecoveryService,
                                        com.eneik.production.services.orchestration.BranchGarbageCollectorService branchGarbageCollectorService,
                                        com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository,
                                        com.eneik.production.services.github.GitHubPullRequestService gitHubPullRequestService,
                                        com.eneik.production.services.PersistentWorkerSessionService persistentWorkerSessionService) {
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.taskConflictRepository = taskConflictRepository;
        this.julesDispatchService = julesDispatchService;
        this.falsificationCycleService = falsificationCycleService;
        this.actionRepository = actionRepository;
        this.operationalPolicyService = operationalPolicyService;
        this.plannedWorkRecoveryService = plannedWorkRecoveryService;
        this.branchGarbageCollectorService = branchGarbageCollectorService;
        this.featureThreadRepository = featureThreadRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.persistentWorkerSessionService = persistentWorkerSessionService;
    }

    /** Cancel a dead/duplicate/stale wishlist item instead of letting it wait in the queue forever. */
    public String dismissWishlist(ProjectEntity project, String targetId, String reason) {
        return execute("dismissWishlist", OperationalAction.DISMISS_WISHLIST, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            WishlistEntity wishlist = wishlistRepository.findById(id).orElse(null);
            if (wishlist == null || !project.getId().equals(wishlist.getProjectId())) {
                return "not found in this project";
            }
            if (wishlist.getStatus() != WishlistStatus.pending && wishlist.getStatus() != WishlistStatus.compiling) {
                return "already resolved (status=" + wishlist.getStatus() + "), nothing to dismiss";
            }
            wishlist.setStatus(WishlistStatus.dismissed);
            wishlistRepository.save(wishlist);
            return null;
        });
    }

    /** Push through a stagnant session right now instead of waiting for the normal poll timer. */
    public String nudgeStuckSession(ProjectEntity project, String targetId, String reason) {
        return execute("nudgeStuckSession", OperationalAction.NUDGE_SESSION, project, targetId, reason, () -> {
            requireTaskWithLiveSession(project, targetId);
            return null;
        });
    }

    /**
     * Retire a wedged PersistentWorkerSessionEntity by hand (2026-08-11, operator directive: "give Gemini this
     * tool as well" after a confirmed live incident - worker 924b2c9f stayed batch-in-flight for
     * 14+ hours after its carrier session died, because isIdleAndFresh's isBatchInFlight() check
     * short-circuits before the age/cycle-count rotation safety net is ever reached). targetId is the
     * CARRIER TASK id (same convention as nudgeStuckSession), not the worker row id - she never sees the
     * worker table directly. JulesDispatchService.closeSessionForTerminalTask now does this automatically
     * the instant a carrier task goes terminal, so this tool is deliberately a narrower backstop: for any
     * OTHER shape of the same desync (e.g. a carrier session that never reaches a terminal task status but
     * has clearly gone dark) that the code-level fix doesn't cover, so she can act without waiting for a
     * human to notice and intervene by hand again.
     *
     * 2026-08-13 (live incident, test-forty-fourth): retiring the worker row alone left its claimed
     * wishlists wedged in `finalizing` forever - Gemini's own journal shows her calling this tool
     * repeatedly, correctly getting "already retired, nothing to do" every time, while the project stayed
     * stuck, because retire() never clears currentBatchIds and nothing else ever released the claim (same
     * root cause as closeSessionForTerminalTask's gap, fixed the same way there). The release now runs
     * unconditionally, BEFORE the already-retired check - retire() only sets retiredAt, so an
     * already-retired worker's currentBatchIds is still sitting there un-released, and this is precisely
     * the shape that needs a manual unstick.
     */
    public String retireStuckWorker(ProjectEntity project, String targetId, String reason) {
        return execute("retireStuckWorker", OperationalAction.RETIRE_STUCK_WORKER, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            TaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
                return "not found in this project";
            }
            var worker = persistentWorkerSessionService.findByCarrierTaskId(id).orElse(null);
            if (worker == null) {
                return "no persistent worker registered for this carrier task";
            }
            int released = 0;
            for (UUID wishlistId : persistentWorkerSessionService.peekCurrentBatch(worker)) {
                if (wishlistRepository.compareAndSetStatus(wishlistId, WishlistStatus.finalizing, WishlistStatus.pending) == 1) {
                    released++;
                }
            }
            if (worker.getRetiredAt() != null) {
                return released == 0 ? "already retired, nothing to do"
                        : "already retired; released " + released + " stranded wishlist claim(s)";
            }
            persistentWorkerSessionService.retire(worker, "Gemini observer: " + reason);
            return released == 0 ? null : "released " + released + " stranded wishlist claim(s)";
        });
    }

    /** Give up on a conflict that has been resurrected/retried past the point of being worth it. */
    public String abandonConflict(ProjectEntity project, String targetId, String reason) {
        return execute("abandonConflict", OperationalAction.ABANDON_CONFLICT, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            TaskConflictEntity conflict = taskConflictRepository.findById(id).orElse(null);
            if (conflict == null) return "not found";
            // conflict.getTask() is a LAZY proxy - .getId() is always safe (no session needed), but a
            // full load must go through the repository, not the proxy (see AutoMergeService's
            // resurrectEscalatedConflictsWithRealCode fix from earlier tonight for the exact failure mode).
            UUID conflictTaskId = conflict.getTask() == null ? null : conflict.getTask().getId();
            TaskEntity task = conflictTaskId == null ? null : taskRepository.findById(conflictTaskId).orElse(null);
            if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
                return "not found in this project";
            }
            if (conflict.getResolvedAt() != null) {
                return "already resolved, nothing to abandon";
            }
            conflict.setResolutionStatus("abandoned_by_gemini_observer");
            conflict.setResolvedAt(Instant.now());
            taskConflictRepository.save(conflict);
            return null;
        });
    }

    /** Boost a genuinely stuck queued task above the normal bottleneck-detection priority floor. */
    public String boostPriority(ProjectEntity project, String targetId, String reason) {
        return execute("boostPriority", OperationalAction.BOOST_PRIORITY, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            TaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
                return "not found in this project";
            }
            if (task.getStatus() != TaskStatus.queued) {
                return "not queued (status=" + task.getStatus() + "), boosting priority would have no effect";
            }
            int boosted = Math.max(task.getPriority(), 100);
            if (boosted == task.getPriority()) {
                return "already at or above boost level, no change";
            }
            task.setPriority(boosted);
            taskRepository.save(task);
            return null;
        });
    }

    /**
     * Revive one specific failed task via PlannedWorkRecoveryService.resumeTask's atomic, rate-limited
     * resume path (2026-08-01) - never a raw status edit. Confirmed live gap (test-fortieth, tasks
     * d9f35f4b/529e5252): a task that dies because its PR closed without merging on GitHub (infra flakiness
     * or a real conflict, either way nothing is left retrying it) previously had no path back except an
     * operator noticing and PATCHing it by hand. Eligibility, the 1-attempt-per-task cap, and the
     * dependency/claim/session safety checks all live in PlannedWorkRecoveryService, not here - this is
     * only the guarded entry point.
     */
    public String reviveFailedTask(ProjectEntity project, String targetId, String reason) {
        return execute("reviveFailedTask", OperationalAction.REVIVE_FAILED_TASK, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            TaskEntity task = taskRepository.findById(id).orElse(null);
            if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
                return "not found in this project";
            }
            if (task.getStatus() != TaskStatus.failed) {
                return "not failed (status=" + task.getStatus() + "), nothing to revive";
            }
            boolean revived = plannedWorkRecoveryService.resumeTask(id);
            return revived ? null : "not eligible for automatic revival (wrong failure cause, already resumed once, "
                    + "or an active claim/session/dependency still blocks it)";
        });
    }

    /** Pull the philosophical falsification cycle forward instead of waiting for its own cron. */
    public String triggerFalsificationRun(ProjectEntity project, String targetId, String reason) {
        return execute("triggerFalsificationRun", OperationalAction.RUN_PROJECT_AUDIT_PIPELINE, project, targetId, reason, () -> {
            // Runs through the exact same gates as the cron (readiness, pending cap, feature flag) - this
            // is a nudge to check now, not a bypass. Safe to call even if it turns out not ready; it just
            // logs and returns.
            falsificationCycleService.executePhilosophicalCycleForProject(project);
            return null;
        });
    }

    /**
     * Pull the REGULAR (code-defect) self_falsification cycle forward instead of waiting for its own daily
     * cron - the ONLY mechanism authorized to create replacement work for a task the iteration-admission
     * poka-yoke retired without one (see ClientDeliverableReadinessService.Readiness.selfFalsificationReadyRatio's
     * own javadoc for the full deadlock this closes). Before this (2026-08-06), only the philosophical track
     * had a Gemini-callable tool for this general shape of problem - a project stuck behind THIS gate had no
     * autonomous recovery path at all, only a human noticing and waiting for 2am. Runs through the exact
     * same gate the cron uses (readiness) - a nudge to check now, not a bypass.
     */
    public String triggerCodeDefectFalsificationRun(ProjectEntity project, String targetId, String reason) {
        return execute("triggerCodeDefectFalsificationRun", OperationalAction.RUN_PROJECT_AUDIT_PIPELINE, project, targetId, reason, () -> {
            falsificationCycleService.executeCycleForProject(project);
            return null;
        });
    }

    /**
     * Close+requeue a PR whose owning session ended up terminal (cancelled/closed_terminal_task/failed)
     * while the PR itself is still open on GitHub - see BranchGarbageCollectorService.
     * findOrphanedPrCandidates (2026-08-03, confirmed live gap: task 074efcb3/PR#38 on test-forty-first, a
     * session that did real successful work got collaterally cancelled by an unrelated cleanup and its PR
     * sat orphaned for hours, invisible to every status-filtered sweep). targetId is the TASK id, not the
     * PR - re-verifies against the real candidate list at execution time rather than trusting the snapshot
     * she was shown, which may already be stale by the time she acts on it.
     */
    public String resolveOrphanedPr(ProjectEntity project, String targetId, String reason) {
        return execute("resolveOrphanedPr", OperationalAction.RESOLVE_ORPHANED_PR, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            var candidate = branchGarbageCollectorService.findOrphanedPrCandidates(project).stream()
                    .filter(c -> id.equals(c.taskId()))
                    .findFirst()
                    .orElse(null);
            if (candidate == null) {
                return "no orphaned PR found for this task - already resolved, or never actually orphaned";
            }
            boolean retired = branchGarbageCollectorService.retireAbandonedBranchAndPR(
                    project, candidate.task(), candidate.headRef(), candidate.pullNumber(),
                    "Gemini observer: resolving orphaned PR - " + reason);
            return retired ? null : "retireAbandonedBranchAndPR could not complete";
        });
    }

    /**
     * Re-opens a feature thread's closeout PR after it was closed by something other than the real, bounded
     * 3-attempt conflict-resolution escalation (2026-08-08, engineering invariant #14, confirmed live
     * incident: feature 1ad15184 on test-forty-third - BranchGarbageCollectorService's Case A used to close
     * ANY open PR titled "Closeout", including a genuinely live one, under two minutes after it opened).
     * targetId is the FEATURE id, not the PR - AutoMergeService.progressCloseout already knows how to open a
     * fresh closeout PR whenever thread.closeoutPrUrl is null, so the real fix here is simply clearing the
     * stale pointer and letting that existing, already-correct cycle pick the thread back up - never a new,
     * separate PR-opening code path. Deliberately refuses (not "retries anyway") the two cases where a real
     * retry would be unsafe or pointless: the thread was formally ABANDONED (its branch was actually
     * deleted by abandonFeatureThread - the code is gone, no retry can recover it, see the recorded
     * closeout_abandoned wishlist item for the real recommendation), or the feature is already merged.
     */
    public String retryAbandonedCloseout(ProjectEntity project, String targetId, String reason) {
        return execute("retryAbandonedCloseout", OperationalAction.RETRY_FEATURE_CLOSEOUT, project, targetId, reason, () -> {
            UUID featureId = parseUuid(targetId);
            if (featureId == null) return "invalid id";
            var thread = featureThreadRepository.findByProjectIdAndFeatureId(project.getId(), featureId).orElse(null);
            if (thread == null) {
                return "no feature thread record found for this feature id";
            }
            if (thread.getMergedToMainAt() != null) {
                return "feature is already merged to main - nothing to retry";
            }
            if (thread.getAbandonedAt() != null) {
                return "feature thread was formally abandoned after " + thread.getCloseoutConflictAttempts()
                        + " failed conflict-resolution attempt(s) and its branch was deleted - the code is gone, "
                        + "this cannot be safely retried automatically; see the closeout_abandoned wishlist item "
                        + "for this feature for the real recommendation: re-implement the remaining scope from "
                        + "current main";
            }
            if (!gitHubPullRequestService.branchExists(project, thread.getBranchName())) {
                return "feature thread branch '" + thread.getBranchName() + "' no longer exists on GitHub - "
                        + "cannot retry a closeout with no branch to close out";
            }
            thread.setCloseoutPrUrl(null);
            featureThreadRepository.save(thread);
            return null;
        });
    }

    /**
     * Blocks one confirmed-duplicate QUEUED task so it stops counting toward the 3+ same-titled-non-terminal
     * threshold that trips BLOCKED_BY_DUPLICATE_CONTENT (see FlowSpineService.duplicateContent) - the ONLY
     * way that hard-stop can clear, since it denies DISPATCH_QUEUED_TASKS itself, so nothing in it can
     * otherwise reach a terminal status on its own (2026-08-07, confirmed live gap: test-forty-third sat
     * halted for 3+ hours - the observer could already see and report the duplicate cluster, but had no
     * tool that could act on it). targetId is the task to collapse, NOT the one being kept - re-verifies
     * against the live task list that it is genuinely still part of a >=3 same-key non-terminal cluster
     * (the exact FlowSpineService.duplicateKey convention: payload.slice_title, falling back to
     * description) rather than trusting the snapshot she was shown, which may already be stale, and refuses
     * to collapse the LAST remaining member of a cluster (that would just be deleting real, unique work).
     */
    public String collapseDuplicateTask(ProjectEntity project, String targetId, String reason) {
        return execute("collapseDuplicateTask", OperationalAction.COLLAPSE_DUPLICATE_TASK, project, targetId, reason, () -> {
            UUID id = parseUuid(targetId);
            if (id == null) return "invalid id";
            TaskEntity target = taskRepository.findById(id).orElse(null);
            if (target == null || target.getProject() == null || !project.getId().equals(target.getProject().getId())) {
                return "not found in this project";
            }
            if (target.getStatus() != TaskStatus.queued) {
                return "not queued (status=" + target.getStatus() + "), nothing to collapse";
            }
            // 2026-08-07 (same-morning follow-on incident): a recovery task (OpsAuditorService.
            // createTargetedRecoveryTask) deliberately deep-copies a dead task's payload verbatim, including
            // slice_title, as the only way ClientDeliverableReadinessService.isDependencySatisfied can match
            // it to the dependent it's meant to unblock - collapsing it would just re-orphan that dependent.
            if (target.getPayload() != null && target.getPayload().has("recoversFailedTaskId")) {
                return "this is a deliberate recovery task for a dead dependency, not generation noise - refusing to collapse it";
            }
            String targetKey = duplicateKey(target);
            if (targetKey == null || targetKey.isBlank()) {
                return "no comparable content key on this task";
            }
            long siblingCount = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                    .limit(30)
                    .filter(t -> t.getStatus() != TaskStatus.done && t.getStatus() != TaskStatus.failed
                            && t.getStatus() != TaskStatus.blocked && t.getStatus() != TaskStatus.spike_completed)
                    .filter(t -> t.getPayload() == null || !t.getPayload().has("recoversFailedTaskId"))
                    .filter(t -> targetKey.equals(duplicateKey(t)))
                    .count();
            if (siblingCount < 3) {
                return "not part of a genuine 3+ duplicate cluster (found " + siblingCount
                        + " matching non-terminal task(s) in the last 30) - refusing to collapse real work";
            }
            target.setStatus(TaskStatus.blocked);
            taskRepository.save(target);
            return null;
        });
    }

    /** Same key convention as FlowSpineService.duplicateKey - kept in sync deliberately, not shared, since
     * that method is private to the Flow Core state machine and this is a one-way read for verification. */
    private static String duplicateKey(TaskEntity task) {
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle;
            }
        }
        return task.getDescription();
    }

    private void requireTaskWithLiveSession(ProjectEntity project, String targetId) {
        UUID id = parseUuid(targetId);
        if (id == null) {
            throw new ActionFailure("invalid id");
        }
        TaskEntity task = taskRepository.findById(id).orElse(null);
        if (task == null || task.getProject() == null || !project.getId().equals(task.getProject().getId())) {
            throw new ActionFailure("not found in this project");
        }
        boolean nudged = julesDispatchService.nudgeStuckSession(task);
        if (!nudged) {
            throw new ActionFailure("no live session found for this task");
        }
    }

    private static final class ActionFailure extends RuntimeException {
        ActionFailure(String message) { super(message); }
    }

    private interface Attempt {
        /** @return null on success, or a short human-readable reason it was skipped/failed. */
        String run();
    }

    private String execute(String tool, OperationalAction action, ProjectEntity project, String targetId,
                            String reason, Attempt attempt) {
        // Same gate every other mutating path in the system now goes through (2026-07-30) - she gets no
        // side door. If Flow Core has the project on a hard stop, her action is denied here exactly like a
        // scheduled orchestration tick or a manual operator command would be, with the same reason text.
        var decision = operationalPolicyService.authorize(project.getId(), action);
        if (!decision.allowed()) {
            GeminiObserverActionEntity denied = new GeminiObserverActionEntity();
            denied.setProjectId(project.getId());
            denied.setCreatedAt(Instant.now());
            denied.setTool(tool);
            denied.setTargetId(targetId);
            denied.setReason(reason);
            denied.setOutcome("denied");
            denied.setDetail(decision.reason());
            // Already a complete, final answer delivered synchronously in this same response - unlike a
            // real mutation's outcome, there is nothing further to learn later, so this never needs to
            // force a follow-up cycle.
            denied.setVerified(true);
            actionRepository.save(denied);
            log.info("GeminiObserverActionService: {} for project {} target {} -> denied by policy: {}",
                    tool, project.getId(), targetId, decision.reason());
            return "denied: " + decision.reason();
        }

        String outcome;
        String detail = null;
        try {
            String failure = attempt.run();
            if (failure == null) {
                outcome = "success";
            } else {
                outcome = "skipped";
                detail = failure;
            }
        } catch (ActionFailure e) {
            outcome = "skipped";
            detail = e.getMessage();
        } catch (Exception e) {
            outcome = "failed";
            detail = e.getMessage();
            log.warn("GeminiObserverActionService: {} failed for project {} target {}: {}",
                    tool, project.getId(), targetId, e.getMessage(), e);
        }

        GeminiObserverActionEntity record = new GeminiObserverActionEntity();
        record.setProjectId(project.getId());
        record.setCreatedAt(Instant.now());
        record.setTool(tool);
        record.setTargetId(targetId);
        record.setReason(reason);
        record.setOutcome(outcome);
        record.setDetail(detail);
        actionRepository.save(record);

        log.info("GeminiObserverActionService: {} for project {} target {} -> {}{}",
                tool, project.getId(), targetId, outcome, detail != null ? " (" + detail + ")" : "");
        return outcome + (detail != null ? ": " + detail : "");
    }

    private static UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
