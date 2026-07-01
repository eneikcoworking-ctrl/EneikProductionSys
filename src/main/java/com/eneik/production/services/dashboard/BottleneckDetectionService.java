package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class BottleneckDetectionService {
    private final TaskRepository taskRepository;
    private final AccountRepository accountRepository;
    private final ClaimRepository claimRepository;

    private static final long WAITING_THRESHOLD_MINUTES = 10;
    private static final long EXPIRED_SPIKE_THRESHOLD = 5;
    private static final long ONLINE_THRESHOLD_MINUTES = 5;

    public BottleneckDetectionService(TaskRepository taskRepository,
                                      AccountRepository accountRepository,
                                      ClaimRepository claimRepository) {
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.claimRepository = claimRepository;
    }

    public List<BottleneckDto> detect() {
        List<BottleneckDto> result = new ArrayList<>();
        Instant onlineThreshold = Instant.now().minus(ONLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

        // Type 1: Tasks waiting but no capable agent online
        taskRepository.queuedGroupedByTag().forEach(row -> {
            boolean hasCapableOnline = accountRepository.existsOnlineWithCapability(row.tag(), onlineThreshold);
            if (!hasCapableOnline && row.oldestWaitingMinutes() > WAITING_THRESHOLD_MINUTES) {
                result.add(new BottleneckDto(
                    "no_capable_agent",
                    row.tag(),
                    row.count(),
                    row.oldestWaitingMinutes(),
                    "Нет ни одного online-аккаунта с capability " + row.tag()
                ));
            }
        });

        // Type 2: Account systematically failing/expiring
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        claimRepository.expiredCountByAccountSince(since24h).forEach(stat -> {
            if (stat.count() > EXPIRED_SPIKE_THRESHOLD) {
                result.add(new BottleneckDto(
                    "expired_lease_spike",
                    stat.accountId(),
                    stat.count(),
                    "Аккаунт регулярно не успевает/падает — возможно TTL мал или агент сбоит"
                ));
            }
        });

        return result;
    }
}
