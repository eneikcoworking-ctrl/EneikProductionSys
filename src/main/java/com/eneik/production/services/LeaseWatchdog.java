package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
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

@Service
public class LeaseWatchdog {

    private static final Logger log = LoggerFactory.getLogger(LeaseWatchdog.class);

    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;

    public LeaseWatchdog(ClaimRepository claimRepository, TaskRepository taskRepository, AccountRepository accountRepository) {
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void reapExpiredLeases() {
        Instant now = Instant.now();
        List<ClaimEntity> expired = claimRepository.findByReleasedAtIsNullAndLeaseExpiresAtBefore(now);

        for (ClaimEntity claim : expired) {
            log.warn("Lease expired for claim {}, task {}. Requeueing task and setting account {} to offline.",
                    claim.getId(), claim.getTask().getId(), claim.getAccount().getId());

            claim.setReleasedAt(now);
            claim.setResultStatus(ClaimResultStatus.expired);
            claimRepository.save(claim);

            TaskEntity task = claim.getTask();
            task.setStatus(TaskStatus.queued);
            taskRepository.save(task);

            AccountEntity account = claim.getAccount();
            account.setStatus(AccountStatus.offline);
            accountRepository.save(account);
        }
    }
}
