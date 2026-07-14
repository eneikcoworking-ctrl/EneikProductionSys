package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BottleneckDetectionService {
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final ClaimRepository claimRepository;

    private static final long WAITING_THRESHOLD_MINUTES = 10;
    private static final long EXPIRED_SPIKE_THRESHOLD = 5;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;

    public BottleneckDetectionService(TaskRepository taskRepository,
                                      AccountRepository accountRepository,
                                      ClaimRepository claimRepository) {
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.claimRepository = claimRepository;
    }

    public List<BottleneckDto> detect() {
        return detect(null);
    }

    public List<BottleneckDto> detect(UUID projectId) {
        List<BottleneckDto> result = new ArrayList<>();

        var queuedByTag = projectId == null
                ? taskRepository.queuedGroupedByTag()
                : taskRepository.queuedGroupedByProjectAndTag(projectId);

        queuedByTag.forEach(row -> {
            boolean hasCapacity = accountRepository.existsJulesAccountWithCapacity(
                    row.tag(),
                    maxConcurrentJulesSessionsPerAccount
            );
            if (!hasCapacity && row.oldestWaitingMinutes() > WAITING_THRESHOLD_MINUTES) {
                result.add(new BottleneckDto(
                        "no_free_jules_slot",
                        row.tag(),
                        row.count(),
                        row.oldestWaitingMinutes(),
                        "All Jules accounts are universal role-capable; the shared account pool has no free session slot for queued " + row.tag() + " work"
                ));
            }
        });

        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        claimRepository.expiredCountByAccountSince(since24h).forEach(stat -> {
            if (stat.count() > EXPIRED_SPIKE_THRESHOLD) {
                result.add(new BottleneckDto(
                        "expired_lease_spike",
                        stat.accountId(),
                        stat.count(),
                        "Account has repeated expired leases in the last 24h"
                ));
            }
        });

        return result;
    }
}
