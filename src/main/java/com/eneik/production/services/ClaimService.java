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

    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final GateOrchestrator gateOrchestrator;

    public ClaimService(ClaimRepository claimRepository,
                            TaskRepository taskRepository,
                            AccountRepository accountRepository,
                            JulesSessionRepository julesSessionRepository,
                            GateOrchestrator gateOrchestrator) {
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.gateOrchestrator = gateOrchestrator;
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
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (task.getStatus() != TaskStatus.queued) {
            throw new IllegalStateException("Task is not in queued status: " + task.getStatus());
        }

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
        claim.setReleasedAt(Instant.now());
        claim.setResultStatus(ClaimResultStatus.done);
        claimRepository.save(claim);

        TaskEntity task = claim.getTask();
        // If it was already in review (AI Reviewer finished), then mark as done
        if (task.getStatus() == TaskStatus.review) {
            task.setStatus(TaskStatus.done);
        } else {
            // Implementer finished, move to review stage
            task.setStatus(TaskStatus.review);
            gateOrchestrator.runQualityGate(task);
        }
        taskRepository.save(task);

        updateAccountStatus(claim.getAccount(), AccountStatus.idle);
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
        ClaimEntity claim = findActiveClaimByTaskId(taskId);
        claim.setReleasedAt(Instant.now());
        claim.setResultStatus(ClaimResultStatus.failed);
        claimRepository.save(claim);

        TaskEntity task = claim.getTask();
        task.setStatus(TaskStatus.queued);
        taskRepository.save(task);

        updateAccountStatus(claim.getAccount(), AccountStatus.idle);
    }

    private ClaimEntity findActiveClaimByTaskId(UUID taskId) {
        return claimRepository.findByTaskIdAndReleasedAtIsNull(taskId)
                .orElseThrow(() -> new IllegalStateException("No active claim for task " + taskId));
    }

    private void updateAccountStatus(AccountEntity account, AccountStatus status) {
        accountRepository.updateStatus(account.getId(), status);
    }

    /**
     * Enforces the business rule that a task cannot have more than one active claim.
     * This compensates for the lack of a partial unique index in the H2 test environment.
     */
    public void validateTaskAvailability(UUID taskId) {
        boolean hasActiveClaim = claimRepository.findByTaskIdAndReleasedAtIsNull(taskId).isPresent();

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
            log.warn("Maintenance: Lease expired for task {} held by account {}",
                claim.getTask().getId(), claim.getAccount().getId());

            // 1. Release the claim as expired
            claim.setReleasedAt(Instant.now());
            claim.setResultStatus(ClaimResultStatus.expired);
            claimRepository.save(claim);

            // 2. Return task to the queue
            TaskEntity task = claim.getTask();
            task.setStatus(TaskStatus.queued);
            taskRepository.save(task);

            // 3. Mark the account as offline
            accountRepository.updateStatus(claim.getAccount().getId(), AccountStatus.offline);
        }

        // Self-healing: If a task has status 'claimed' but the session is 'skipped' or failed or missing,
        // requeue the task and release the claim so it can be re-dispatched.
        List<ClaimEntity> activeClaims = claimRepository.findByReleasedAtIsNull();
        for (ClaimEntity claim : activeClaims) {
            TaskEntity task = claim.getTask();
            List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
            boolean isAlive = false;
            for (JulesSessionEntity s : sessions) {
                if (!"skipped".equals(s.getExternalSessionId()) && !"failed".equals(s.getStatus()) && !"stuck".equals(s.getStatus())) {
                    isAlive = true;
                }
            }
            if (!isAlive) {
                log.warn("Self-healing: Releasing stuck claim for task {} because session is skipped, failed, or missing", task.getId());
                claim.setReleasedAt(Instant.now());
                claim.setResultStatus(ClaimResultStatus.failed);
                claimRepository.save(claim);

                task.setStatus(TaskStatus.queued);
                taskRepository.save(task);

                accountRepository.findById(claim.getAccount().getId()).ifPresent(acc -> {
                    acc.setStatus(AccountStatus.idle);
                    accountRepository.save(acc);
                });
            }
        }

        if (!expired.isEmpty()) {
            log.warn("Maintenance: {} lease(s) expired and requeued", expired.size());
        }
    }

    /**
     * Maintenance: Detects and marks Jules sessions that are stuck.
     */
    @Transactional
    public void detectStuckSessions(int stuckThresholdMinutes) {
        Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
        List<JulesSessionEntity> runningSessions = julesSessionRepository.findByStatus("running");

        for (JulesSessionEntity session : runningSessions) {
            if (session.getUpdatedAt().isBefore(threshold)) {
                log.warn("Maintenance: Session {} is stuck (no update for {} minutes)", session.getId(), stuckThresholdMinutes);
                session.setStatus("stuck");
                julesSessionRepository.save(session);
            }
        }
    }

    @Transactional
    public void refreshQueuedTasksPriority(Set<String> bottleneckRefs, int highPriority, int defaultPriority) {
        log.info("Refreshing priority for queued tasks based on current bottlenecks...");
        List<TaskEntity> queuedTasks = taskRepository.findByStatus(TaskStatus.queued);

        int updatedCount = 0;
        for (TaskEntity task : queuedTasks) {
            String tocRef = null;
            if (task.getPayload() != null && task.getPayload().has("toc_constraint_ref")) {
                tocRef = task.getPayload().get("toc_constraint_ref").asText();
            }
            int newPriority = (tocRef != null && bottleneckRefs.contains(tocRef)) ? highPriority : defaultPriority;

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
