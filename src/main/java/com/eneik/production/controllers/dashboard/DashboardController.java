package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.*;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import com.eneik.production.services.dashboard.TaskWaitTimeService;
import com.eneik.production.services.task.TaskTitleBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AccountRepository accountRepository;
    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final BottleneckDetectionService bottleneckDetectionService;
    private final TaskWaitTimeService taskWaitTimeService;

    public DashboardController(AccountRepository accountRepository,
                               ClaimRepository claimRepository,
                               TaskRepository taskRepository,
                               BottleneckDetectionService bottleneckDetectionService,
                               TaskWaitTimeService taskWaitTimeService) {
        this.accountRepository = accountRepository;
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.bottleneckDetectionService = bottleneckDetectionService;
        this.taskWaitTimeService = taskWaitTimeService;
    }

    @GetMapping("/agents")
    public List<AgentDashboardDto> getAgents() {
        List<com.eneik.production.models.persistence.AccountEntity> accounts;
        try {
            accounts = accountRepository.findAllByOrderByNameAsc();
            if (accounts == null || accounts.isEmpty()) {
                accounts = accountRepository.findAll();
            }
        } catch (Exception ignored) {
            accounts = accountRepository.findAll();
        }
        if (accounts == null) {
            accounts = List.of();
        }

        java.util.Map<UUID, ClaimEntity> latestActiveClaimByAccountId = new java.util.HashMap<>();
        boolean batchLookupSucceeded = false;
        try {
            List<ClaimEntity> activeClaims = claimRepository.findByReleasedAtIsNull();
            if (activeClaims != null) {
                batchLookupSucceeded = true;
                for (ClaimEntity claim : activeClaims) {
                    if (claim.getAccount() != null && claim.getAccount().getId() != null) {
                        UUID accId = claim.getAccount().getId();
                        ClaimEntity existing = latestActiveClaimByAccountId.get(accId);
                        if (existing == null || (claim.getClaimedAt() != null && (existing.getClaimedAt() == null || claim.getClaimedAt().isAfter(existing.getClaimedAt())))) {
                            latestActiveClaimByAccountId.put(accId, claim);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        final boolean batchSuccess = batchLookupSucceeded;

        return accounts.stream().map(account -> {
            ClaimEntity activeClaim = latestActiveClaimByAccountId.get(account.getId());
            if (activeClaim == null && !batchSuccess) {
                try {
                    activeClaim = claimRepository
                            .findByAccountIdAndReleasedAtIsNullOrderByClaimedAtDesc(account.getId())
                            .stream()
                            .findFirst()
                            .orElse(null);
                } catch (Exception ignored) {
                }
            }
            return new AgentDashboardDto(
                account.getId(),
                account.getName(),
                account.getStatus(),
                activeClaim != null && activeClaim.getRole() != null ? activeClaim.getRole().getTag() : null,
                activeClaim != null ? TaskTitleBuilder.displayTitle(activeClaim.getTask()) : null,
                activeClaim != null ? activeClaim.getClaimedAt() : null,
                activeClaim != null ? activeClaim.getLeaseExpiresAt() : null,
                account.getLastHeartbeat()
            );
        }).collect(Collectors.toList());
    }

    @GetMapping("/queue")
    public QueueDashboardDto getQueue() {
        List<QueueDashboardDto.TagCountDto> byTag = taskRepository.queuedGroupedByTag();
        long totalQueued = taskRepository.countByStatus(TaskStatus.queued);
        return new QueueDashboardDto(byTag, totalQueued);
    }

    @GetMapping("/bottlenecks")
    public List<BottleneckDto> getBottlenecks() {
        return bottleneckDetectionService.detect();
    }

    @GetMapping("/wait-time")
    public WaitTimeBreakdownDto getWaitTime(@RequestParam UUID projectId) {
        return taskWaitTimeService.computeForProject(projectId);
    }

    @GetMapping("/pipeline")
    public PipelineDashboardDto getPipeline() {
        return new PipelineDashboardDto(
            taskRepository.countByStatus(TaskStatus.queued),
            taskRepository.countByStatus(TaskStatus.claimed),
            taskRepository.countByStatus(TaskStatus.in_progress),
            taskRepository.countByStatus(TaskStatus.review),
            taskRepository.countByStatus(TaskStatus.done),
            taskRepository.countByStatus(TaskStatus.failed)
        );
    }
}
