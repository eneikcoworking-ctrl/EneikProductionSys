package com.eneik.production.services;

import com.eneik.production.dto.ClaimDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing task claims and enforcing business rules.
 */
@Service
public class ClaimService {

    private static final Duration LEASE_TTL = Duration.ofHours(1);

    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;

    public ClaimService(ClaimRepository claimRepository,
                            TaskRepository taskRepository,
                            AccountRepository accountRepository) {
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
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
        task.setStatus(TaskStatus.review);
        taskRepository.save(task);

        updateAccountStatus(claim.getAccount(), AccountStatus.idle);
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
        account.setStatus(status);
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
}
