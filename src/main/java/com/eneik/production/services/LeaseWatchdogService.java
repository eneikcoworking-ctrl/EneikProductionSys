package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.ClaimResultStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background process for reaping expired task leases.
 */
@Service
public class LeaseWatchdogService {
    private static final Logger log = LoggerFactory.getLogger(LeaseWatchdogService.class);

    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;

    public LeaseWatchdogService(ClaimRepository claimRepository,
                                TaskRepository taskRepository,
                                AccountRepository accountRepository) {
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Periodically checks for expired claims and returns tasks to the queue.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void reapExpiredLeases() {
        List<ClaimEntity> expired = claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(Instant.now());

        for (ClaimEntity claim : expired) {
            log.warn("Watchdog: Lease expired for task {} held by account {}",
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

        if (!expired.isEmpty()) {
            log.warn("Watchdog: {} lease(s) expired and requeued", expired.size());
        }
    }
}
