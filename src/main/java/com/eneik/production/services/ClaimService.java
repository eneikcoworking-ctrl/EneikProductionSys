package com.eneik.production.services;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.gate.GateOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing task claims and enforcing business rules.
 */
@Service
public class ClaimService {
    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private static final Duration LEASE_TTL = Duration.ofHours(1);

    /**
     * Claims made outside JulesDispatchService (claim()/claimForProject()/claimSpecificTask()) never get a
     * JulesSessionEntity, so the self-healing pass below must not treat "no session yet" as "stuck" until a
     * claim has had a fair chance to either produce a session or be worked without one.
     */
    private static final Duration SELF_HEALING_GRACE_PERIOD = Duration.ofMinutes(5);

    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final GateOrchestrator gateOrchestrator;
    private final com.eneik.production.services.ClientDeliverableReadinessService readinessService;
    private final com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository;

    public ClaimService(ClaimRepository claimRepository,
                            TaskRepository taskRepository,
                            AccountRepository accountRepository,
                            JulesSessionRepository julesSessionRepository,
                            GateOrchestrator gateOrchestrator,
                        com.eneik.production.services.ClientDeliverableReadinessService readinessService,
                        com.eneik.production.repositories.NeedsHumanReviewRepository needsHumanReviewRepository) {
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.gateOrchestrator = gateOrchestrator;
        this.readinessService = readinessService;
        this.needsHumanReviewRepository = needsHumanReviewRepository;
    }

    /**
     * Atomically claims the next available task for the given account and tags.
     */
    @Transactional
    public ClaimDto claim(UUID accountId, List<String> capableTags) {
        // 1. Lock one suitable task, SKIP LOCKED
        TaskEntity task = taskRepository.lockNextQueuedTask(capableTags).orElse(null);

        if (task == null) return null;

        // 2. Find account
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        // 3. Create claim and update task status
        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(task.getRole());
        claim.setClaimedAt(Instant.now());
        claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));

        claimRepository.save(claim);

        task.setStatus(TaskStatus.claimed);
        taskRepository.save(task);

        // 4. Update account status
        account.setStatus(AccountStatus.busy);
        accountRepository.save(account);

        return new ClaimDto(
                claim.getId(),
                task.getId(),
                task.getRole().getTag(),
                task.getDescription(),
                task.getPayload(),
                claim.getLeaseExpiresAt()
        );
    }

    @Transactional
    public ClaimDto claimSpecificTask(UUID taskId, UUID accountId) {
        // Atomic SELECT ... FOR UPDATE SKIP LOCKED: only one concurrent caller can lock a still-queued
        // task by id, closing the read-then-write race that used to let two threads both claim it.
        TaskEntity task = taskRepository.lockTaskByIdForUpdate(taskId)
                .orElseThrow(() -> {
                    boolean exists = taskRepository.existsById(taskId);
                    return exists
                            ? new IllegalStateException("Task is not in queued status or is already locked: " + taskId)
                            : new IllegalArgumentException("Task not found: " + taskId);
                });
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        String taskTag = task.getRole().getTag();
        boolean isCapable = "*".equals(account.getCapabilities()) ||
                java.util.Arrays.asList(account.getCapabilities().split(",")).contains(taskTag);
        if (!isCapable) {
            throw new IllegalArgumentException("Account " + accountId + " does not have capability for role " + taskTag);
        }

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(task.getRole());
        claim.setClaimedAt(Instant.now());
        claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));
        claimRepository.save(claim);

        task.setStatus(TaskStatus.claimed);
        taskRepository.save(task);

        account.setStatus(AccountStatus.busy);
        account.setLastHeartbeat(Instant.now());
        accountRepository.save(account);

        return new ClaimDto(
                claim.getId(),
                task.getId(),
                task.getRole().getTag(),
                task.getDescription(),
                task.getPayload(),
                claim.getLeaseExpiresAt()
        );
    }

    @Transactional
    public ClaimDto claimForProject(UUID projectId, UUID accountId) {
        TaskEntity task = taskRepository.lockNextQueuedTaskForProject(projectId).orElse(null);
        if (task == null) return null;

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (account.getProject() != null && !projectId.equals(account.getProject().getId())) {
            throw new IllegalArgumentException("Account is attached to another project: " + account.getProject().getId());
        }
        if (task.getProject() == null || !projectId.equals(task.getProject().getId())) {
            throw new IllegalStateException("Task is not attached to project: " + projectId);
        }

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(task.getRole());
        claim.setClaimedAt(Instant.now());
        claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));
        claimRepository.save(claim);

        task.setStatus(TaskStatus.claimed);
        taskRepository.save(task);

        account.setStatus(AccountStatus.busy);
        account.setLastHeartbeat(Instant.now());
        accountRepository.save(account);

        return new ClaimDto(
                claim.getId(),
                task.getId(),
                task.getRole().getTag(),
                task.getDescription(),
                task.getPayload(),
                claim.getLeaseExpiresAt()
        );
    }

    @Transactional
    public void heartbeat(UUID taskId) {
        ClaimEntity claim = findActiveClaimByTaskId(taskId);
        claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));
        claimRepository.save(claim);
    }

    @Transactional
    public void complete(UUID taskId) {
        ClaimEntity claim = findActiveClaimByTaskId(taskId);

        TaskEntity task = claim.getTask();
        // If it was already in review (AI Reviewer finished), then mark as done
        if (task.getStatus() == TaskStatus.review) {
            // 2026-08-23 (poka-yoke, not inspection). This is the only transition into done, and it is
            // reached from the GitHub merge webhook - so at this point the merge either happened or it did
            // not, and asking costs nothing. A role that must deliver code and has no merge on main has
            // not delivered, and recording done for it would make the row itself the only evidence that
            // the work exists. Measured: `Runtime Contract 9b58412d` held the client's own epic at 6 of 7
            // for fourteen hours in exactly that state.
            //
            // Scoped by role, deliberately: EmsFlowStage.requiresCodeForDelivery says which roles owe code,
            // and spec roles deliver documents. Refusing them would deadlock them for ever, which is the
            // repair that damages. The task stays in review and the flow's own recovery paths keep working
            // on it, rather than being closed on a status nothing backs.
            if (readinessService.requiresCodeForDelivery(task) && !readinessService.hasRequiredMergeEvidence(task)) {
                log.warn("Task {} ({}) was not marked done: its role must deliver code and nothing reached "
                        + "main for it. Left in review - done would be the only evidence the work exists.",
                        task.getId(), task.getRole() != null ? task.getRole().getTag() : "?");
                return;
            }
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.done);
            claimRepository.save(claim);

            task.setStatus(TaskStatus.done);
            taskRepository.save(task);
            refreshAccountStatusAfterClaimRelease(claim.getAccount());
        } else {
            // Implementer finished, move to review stage
            task.setStatus(TaskStatus.review);
            gateOrchestrator.runQualityGate(task);

            if (!task.isQualityGatePassed()) {
                // Task failed the quality gate. Increment retry count.
                task.setRetryCount(task.getRetryCount() + 1);
                if (task.getRetryCount() >= 3) {
                    task.setStatus(TaskStatus.blocked);
                } else {
                    task.setStatus(TaskStatus.queued);
                }
                taskRepository.save(task);

                // Re-use logic similar to fail() to properly clean up the failed claim
                claim.setReleasedAt(Instant.now());
                claim.setResultStatus(ClaimResultStatus.failed);
                claimRepository.save(claim);
                refreshAccountStatusAfterClaimRelease(claim.getAccount());
            } else {
                // Task passed the quality gate. Move to manual/AI review normally.
                claim.setReleasedAt(Instant.now());
                claim.setResultStatus(ClaimResultStatus.done);
                claimRepository.save(claim);

                taskRepository.save(task);
                refreshAccountStatusAfterClaimRelease(claim.getAccount());
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean hasActiveClaim(UUID taskId) {
        return claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).isPresent();
    }

    @Transactional
    public ClaimDto claimReviewer(UUID taskId, UUID accountId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        ClaimEntity claim = new ClaimEntity();
        claim.setTask(task);
        claim.setAccount(account);
        claim.setRole(task.getRole());
        claim.setClaimedAt(Instant.now());
        claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));
        claimRepository.save(claim);

        account.setStatus(AccountStatus.busy);
        accountRepository.save(account);

        return new ClaimDto(
                claim.getId(),
                task.getId(),
                task.getRole().getTag(),
                task.getDescription(),
                task.getPayload(),
                claim.getLeaseExpiresAt()
        );
    }

    @Transactional
    public void fail(UUID taskId) {
        TaskEntity currentTask = taskRepository.findById(taskId).orElse(null);
        if (currentTask != null && isTerminal(currentTask.getStatus())) {
            releaseTerminalClaim(taskId);
            log.info("Poka-yoke: ignored fail transition for terminal task {} ({})", taskId, currentTask.getStatus());
            return;
        }
        ClaimEntity claim = findActiveClaimByTaskId(taskId);
        claim.setReleasedAt(Instant.now());
        claim.setResultStatus(ClaimResultStatus.failed);
        claimRepository.save(claim);

        int revived = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        if (revived == 0) {
            log.info("ClaimService.fail: skipped requeue for task {} - it reached a terminal status concurrently", taskId);
        }

        refreshAccountStatusAfterClaimRelease(claim.getAccount());
    }

    @Transactional
    public void closeTaskAsBlocked(UUID taskId, String reason) {
        TaskEntity currentTask = taskRepository.findById(taskId).orElse(null);
        if (currentTask != null && isTerminal(currentTask.getStatus())) {
            releaseTerminalClaim(taskId);
            log.info("Poka-yoke: ignored blocked transition for terminal task {} ({})", taskId, currentTask.getStatus());
            return;
        }
        claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).ifPresent(claim -> {
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.failed);
            claimRepository.save(claim);
            refreshAccountStatusAfterClaimRelease(claim.getAccount());
        });

        int updated = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        if (updated == 0) {
            log.info("ClaimService.closeTaskAsBlocked: skipped for task {} - it reached a terminal status concurrently", taskId);
        } else {
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setJulesDispatchStatus(reason);
                taskRepository.save(task);
            });
        }
    }

    // Deliberately TaskStatus.failed, not .blocked: a blocked task is picked up by
    // ProjectFlowService.recoverBlockedWork and re-decomposed/re-dispatched, which is exactly what an
    // operator cancelling a stray/duplicate task wants to NOT happen. failed is inert - nothing revisits it.
    @Transactional
    public void closeTaskAsFailed(UUID taskId, String reason) {
        TaskEntity currentTask = taskRepository.findById(taskId).orElse(null);
        if (currentTask != null && isTerminal(currentTask.getStatus())) {
            releaseTerminalClaim(taskId);
            log.info("Poka-yoke: ignored failed transition for terminal task {} ({})", taskId, currentTask.getStatus());
            return;
        }
        claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).ifPresent(claim -> {
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.failed);
            claimRepository.save(claim);
            refreshAccountStatusAfterClaimRelease(claim.getAccount());
        });

        int updated = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.failed);
        if (updated == 0) {
            log.info("ClaimService.closeTaskAsFailed: skipped for task {} - it reached a terminal status concurrently", taskId);
        } else {
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setJulesDispatchStatus(reason);
                taskRepository.save(task);
            });
        }
    }

    @Transactional
    public void releaseClaimToQueue(UUID taskId, String reason) {
        TaskEntity currentTask = taskRepository.findById(taskId).orElse(null);
        if (currentTask != null && isTerminal(currentTask.getStatus())) {
            releaseTerminalClaim(taskId);
            log.info("Poka-yoke: ignored requeue transition for terminal task {} ({})", taskId, currentTask.getStatus());
            return;
        }
        claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).ifPresent(claim -> {
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.failed);
            claimRepository.save(claim);

            refreshAccountStatusAfterClaimRelease(claim.getAccount());
        });

        long refusals = refusedSessionCreations(taskId);
        long budget = dispatchAttemptBudget();
        if (refusals >= budget) {
            retireForExhaustedDispatchBudget(taskId, refusals, budget, reason);
            return;
        }

        int revived = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        if (revived == 0) {
            log.info("ClaimService.releaseClaimToQueue: skipped requeue for task {} - it reached a terminal status concurrently", taskId);
        } else {
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setJulesDispatchStatus(reason);
                taskRepository.save(task);
            });
        }
    }

    // Same requeue mechanics as releaseClaimToQueue, but also rewrites the task's own brief. Used when a
    // Jules session honestly rejected the task over a concrete external fact (e.g. a Flyway version
    // collision) rather than looping or going silent: the task keeps its ID (so nothing depending on it
    // needs rewiring), but the next session starts already knowing the blocker instead of re-discovering
    // it from scratch. See JulesDispatchService.closeLoopAndCreateFollowUps's REASONED_BLOCKER branch.
    @Transactional
    public void reopenWithAmendedBrief(UUID taskId, String amendedDescription, String reason) {
        TaskEntity currentTask = taskRepository.findById(taskId).orElse(null);
        if (currentTask != null && isTerminal(currentTask.getStatus())) {
            releaseTerminalClaim(taskId);
            log.info("Poka-yoke: ignored amended-brief requeue for terminal task {} ({})", taskId, currentTask.getStatus());
            return;
        }
        claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).ifPresent(claim -> {
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.failed);
            claimRepository.save(claim);

            refreshAccountStatusAfterClaimRelease(claim.getAccount());
        });

        int revived = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued);
        if (revived == 0) {
            log.info("ClaimService.reopenWithAmendedBrief: skipped requeue for task {} - it reached a terminal status concurrently", taskId);
        } else {
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setDescription(amendedDescription);
                task.setJulesDispatchStatus(reason);
                taskRepository.save(task);
            });
        }
    }

    private ClaimEntity findActiveClaimByTaskId(UUID taskId) {
        return claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId)
                .orElseThrow(() -> new IllegalStateException("No active claim for task " + taskId));
    }

    private void refreshAccountStatusAfterClaimRelease(AccountEntity account) {
        refreshAccountStatusAfterClaimRelease(account, AccountStatus.idle);
    }

    private void refreshAccountStatusAfterClaimRelease(AccountEntity account, AccountStatus noActiveClaimStatus) {
        claimRepository.flush();
        accountRepository.findById(account.getId()).ifPresent(current -> {
            if (current.getStatus() == AccountStatus.daily_limited || current.getStatus() == AccountStatus.api_blocked) {
                return;
            }
            AccountStatus next = claimRepository.existsByAccountIdAndReleasedAtIsNull(account.getId())
                    ? AccountStatus.busy
                    : noActiveClaimStatus;
            current.setStatus(next);
            current.setLastHeartbeat(Instant.now());
            accountRepository.save(current);
        });
    }

    @Transactional
    public void releaseTerminalClaim(UUID taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (!isTerminal(task.getStatus())) {
                return;
            }
            claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId)
                    .ifPresent(claim -> closeClaimForTerminalTask(claim, task, "task reached terminal status"));
        });
    }

    // 2026-08-29, action plan 4.1. This method is where every failed dispatch returns to the queue - four
    // call sites in ProjectFlowService and JulesDispatchService, all of them the "Jules would not take it"
    // path. Until now the return was unconditional, and that made the dispatch loop
    //
    //     queued -> claim an account -> Jules refuses to create the session -> requeue -> queued
    //
    // one along which nothing decreases. retryCount does not move here: it is incremented only after a
    // session existed (the quality-gate branch above, and the compiler's correction round), so a task whose
    // every attempt is refused before creation carries retryCount 0 forever. Measured on the live database
    // 2026-08-29: one review-fallback carrier held 67 refused sessions across 7 accounts over four and a
    // half days at retryCount 0, one compiler task held 123, and 375 of the 775 sessions ever recorded were
    // refusals that produced nothing.
    //
    // The bound below is a variant function, which is the only thing that makes the loop terminate.
    private static final int DISPATCH_ATTEMPTS_PER_LIVE_ACCOUNT = 2;

    // A(task): refusals already recorded. Monotone, and raised by exactly one per iteration - see the
    // JulesSessionRepository query's own note for why both hold.
    long refusedSessionCreations(UUID taskId) {
        return julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed");
    }

    // A_max = 2 * live accounts. Derived rather than picked. A refusal can be a property of the account
    // rather than of the request, so "no account will take this" is not established until every living
    // account has refused it - the independent-witness shape of invariant 12, testified by each account
    // instead of asserted once. Each gets a second attempt because an unnamed FAILED_PRECONDITION can be
    // transient. Past that, repetition carries no new information and is only cost. Measured against the
    // live data (7 live accounts, so 14): of the 78 tasks that ever saw a refusal, this bound would have
    // stopped exactly the two runaways, the next highest sitting at 13.
    long dispatchAttemptBudget() {
        return DISPATCH_ATTEMPTS_PER_LIVE_ACCOUNT * Math.max(1L, accountRepository.countLiveAccounts());
    }

    // The budget attributes no fault, deliberately. Which side's precondition failed is decided elsewhere
    // (AccountHealthService.reportDispatchOutcome) and is not consulted here: 52 of those 67 refusals were
    // unattributable, and a budget that spent only on attributable ones would not terminate - which is
    // precisely what was observed for four days. So the exit is `blocked` plus a needs-human-review row,
    // the same one the wishlist compiler already takes at its own retry cap, and not `failed`, which would
    // state a verdict nobody reached. `blocked` keeps the task recoverable by a human; what it does not do
    // is put the task back in front of the selector.
    private void retireForExhaustedDispatchBudget(UUID taskId, long refusals, long budget, String reason) {
        int written = taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
        if (written == 0) {
            log.info("ClaimService: task {} reached a terminal status concurrently; dispatch-budget retirement skipped", taskId);
            return;
        }
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setJulesDispatchStatus(reason);
            taskRepository.save(task);
            if (!needsHumanReviewRepository.existsByTaskId(taskId)) {
                NeedsHumanReviewEntity review = new NeedsHumanReviewEntity();
                review.setTask(task);
                String text = "Jules refused to create a session for this task " + refusals
                        + " time(s), the budget for " + budget / DISPATCH_ATTEMPTS_PER_LIVE_ACCOUNT
                        + " live account(s). Whose precondition failed is not established; the capacity spent is. Last: "
                        + (reason == null ? "" : reason);
                review.setReason(text.length() > 256 ? text.substring(0, 256) : text);
                needsHumanReviewRepository.save(review);
            }
        });
        log.warn("Task {} left the dispatch queue: {} refused session creations against a budget of {}. "
                + "Routed to human review rather than requeued.", taskId, refusals, budget);
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.done || status == TaskStatus.failed || status == TaskStatus.spike_completed;
    }

    private void closeClaimForTerminalTask(ClaimEntity claim, TaskEntity task, String trigger) {
        claim.setReleasedAt(Instant.now());
        claim.setResultStatus(task.getStatus() == TaskStatus.failed
                ? ClaimResultStatus.failed
                : ClaimResultStatus.done);
        claimRepository.save(claim);
        refreshAccountStatusAfterClaimRelease(claim.getAccount());
        log.info("Maintenance: Released claim for terminal task {} ({}) without changing task status {}",
                task.getId(), trigger, task.getStatus());
    }

    /**
     * Enforces the business rule that a task cannot have more than one active claim.
     * This compensates for the lack of a partial unique index in the H2 test environment.
     */
    public void validateTaskAvailability(UUID taskId) {
        boolean hasActiveClaim = claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId).isPresent();

        if (hasActiveClaim) {
            throw new IllegalStateException("Task " + taskId + " already has an active claim.");
        }
    }

    /**
     * Maintenance: Periodically checks for expired claims and returns tasks to the queue.
     */
    @Transactional
    public void reapExpiredLeases() {
        List<ClaimEntity> expired = claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(Instant.now());

        for (ClaimEntity claim : expired) {
            TaskEntity task = claim.getTask();
            if (isTerminal(task.getStatus())) {
                closeClaimForTerminalTask(claim, task, "lease expired");
                continue;
            }
            if (hasActiveExternalJulesSession(claim.getTask().getId())) {
                claim.setLeaseExpiresAt(Instant.now().plus(LEASE_TTL));
                claimRepository.save(claim);
                log.info("Maintenance: Extended lease for task {} because Jules session is still active",
                        claim.getTask().getId());
                continue;
            }

            log.warn("Maintenance: Lease expired for task {} held by account {}",
                claim.getTask().getId(), claim.getAccount().getId());

            // 1. Release the claim as expired
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.expired);
            claimRepository.save(claim);

            // 2. Return task to the queue - atomic CAS (see compareAndSetStatus javadoc), not
            // task.setStatus()+save(): the isTerminal() check above is a stale read by the time this
            // write executes if a concurrent transaction terminal-izes the task in between.
            int revived = taskRepository.compareAndSetStatus(task.getId(), TaskStatus.claimed, TaskStatus.queued);
            if (revived == 0) {
                log.info("Maintenance: skipped requeue for task {} on lease expiry - it left 'claimed' concurrently (already terminal or reclaimed elsewhere)", task.getId());
            }

            // 3. Mark the account offline only if no other concurrent claim is still active.
            refreshAccountStatusAfterClaimRelease(claim.getAccount(), AccountStatus.offline);
        }

        // Self-healing: If a task has status 'claimed' but the session is 'skipped' or failed or missing,
        // requeue the task and release the claim so it can be re-dispatched.
        List<ClaimEntity> activeClaims = claimRepository.findByReleasedAtIsNull();
        for (ClaimEntity claim : activeClaims) {
            if (claim.getClaimedAt() != null
                    && claim.getClaimedAt().isAfter(Instant.now().minus(SELF_HEALING_GRACE_PERIOD))) {
                continue;
            }

            TaskEntity task = claim.getTask();
            if (isTerminal(task.getStatus())) {
                closeClaimForTerminalTask(claim, task, "session closed, failed, or missing");
                continue;
            }
            List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
            boolean isAlive = false;
            for (JulesSessionEntity s : sessions) {
                if (isActiveExternalJulesSession(s)) {
                    isAlive = true;
                }
            }
            if (!isAlive) {
                log.warn("Self-healing: Releasing stuck claim for task {} because session is skipped, failed, or missing", task.getId());
                claim.setReleasedAt(Instant.now());
                claim.setResultStatus(ClaimResultStatus.failed);
                claimRepository.save(claim);

                // Atomic CAS, not task.setStatus()+save(): the isTerminal() check above only proves the
                // task was non-terminal at that read - a concurrent transaction (e.g. the task's own
                // completion callback) can still terminal-ize it before this write lands. The requeue must
                // only apply if the row is still exactly 'claimed' at this instant, or a terminal task can
                // be silently resurrected and redispatched as a duplicate (live incident, 2026-07-24).
                int revived = taskRepository.compareAndSetStatus(task.getId(), TaskStatus.claimed, TaskStatus.queued);
                if (revived == 0) {
                    log.info("Self-healing: skipped requeue for task {} - it left 'claimed' concurrently (already terminal or reclaimed elsewhere)", task.getId());
                }

                refreshAccountStatusAfterClaimRelease(claim.getAccount());
            }
        }

        if (!expired.isEmpty()) {
            log.warn("Maintenance: {} lease(s) expired and requeued", expired.size());
        }
    }

    /**
     * Maintenance: Detects and marks Jules sessions that are stuck.
     *
     * Covers "revising" as well as "running": a session sent back for fixes after a review rejection
     * (see JulesDispatchService's REVIEW REJECTED handling) has no other liveness check pointed at it -
     * forceUnblockOverflowedSessions only acts once blindCycleCount has climbed via the oversized-activity-
     * log path, which a quietly-stalled revising session with a normal-sized log never triggers. Without
     * "revising" here, such a session (and its task) can sit claimed/revising forever with zero automatic
     * recovery. Confirmed live in test-twenty-sixth: two tasks stuck >40 minutes with no path back.
     */
    @Transactional
    public void detectStuckSessions(int stuckThresholdMinutes) {
        Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
        List<JulesSessionEntity> activeSessions = julesSessionRepository.findByStatusIn(List.of("running", "revising", "queued"));

        for (JulesSessionEntity session : activeSessions) {
            // lastProgressAt (real forward progress) rather than updatedAt, which Hibernate refreshes on
            // every save regardless of whether anything actually changed - updatedAt alone can never go
            // stale while polling keeps succeeding, even for a session that is silently blocked.
            Instant reference = session.getLastProgressAt() != null ? session.getLastProgressAt() : session.getUpdatedAt();
            if (reference.isBefore(threshold)) {
                log.warn("Maintenance: Session {} is stuck (no real progress for {} minutes)", session.getId(), stuckThresholdMinutes);
                session.setStatus("stuck");
                julesSessionRepository.save(session);
            }
        }
    }

    private boolean hasActiveExternalJulesSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .anyMatch(this::isActiveExternalJulesSession);
    }

    private boolean isActiveExternalJulesSession(JulesSessionEntity session) {
        if (session.getExternalSessionId() == null || "skipped".equals(session.getExternalSessionId())) {
            return false;
        }
        String status = session.getStatus();
        return "queued".equals(status)
                || "running".equals(status)
                || "revising".equals(status)
                || "pr_opened".equals(status);
    }

    // Was a flat overwrite to highPriority-or-defaultPriority for every queued task on every 5-minute
    // tick, regardless of current value. That silently destroyed the Kano/Cynefin-informed criticality
    // score TechnicalLeadCompiler computes once at task-creation time (task.priority can start well above
    // 100, e.g. a client Must-Be item) - a non-bottleneck task got flattened to 0 right alongside genuine
    // low-value cosmetic work, erasing the very signal this method exists to act on. This is now purely
    // additive: bottleneck status can only ever boost a task's priority (never below what it already had),
    // never silently lower it. There is no reliable "original" value to revert to once overwritten, so
    // the safe default is to leave a task's priority alone unless there's a concrete reason to raise it.
    @Transactional
    public void refreshQueuedTasksPriority(Set<String> bottleneckRefs, int highPriority) {
        log.info("Refreshing priority for queued tasks based on current bottlenecks...");
        List<TaskEntity> queuedTasks = taskRepository.findByStatus(TaskStatus.queued);

        int updatedCount = 0;
        for (TaskEntity task : queuedTasks) {
            String tocRef = null;
            if (task.getPayload() != null && task.getPayload().has("toc_constraint_ref")) {
                tocRef = task.getPayload().get("toc_constraint_ref").asText();
            }
            boolean isBottleneck = tocRef != null && bottleneckRefs.contains(tocRef);
            if (!isBottleneck) {
                continue;
            }
            int newPriority = Math.max(task.getPriority(), highPriority);
            if (task.getPriority() != newPriority) {
                task.setPriority(newPriority);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            log.info("Updated priority for {} tasks", updatedCount);
        }
    }
}
