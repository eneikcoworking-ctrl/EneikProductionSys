package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.*;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AccountRepository accountRepository;
    private final ClaimRepository claimRepository;
    private final TaskRepository taskRepository;
    private final BottleneckDetectionService bottleneckDetectionService;

    public DashboardController(AccountRepository accountRepository,
                               ClaimRepository claimRepository,
                               TaskRepository taskRepository,
                               BottleneckDetectionService bottleneckDetectionService) {
        this.accountRepository = accountRepository;
        this.claimRepository = claimRepository;
        this.taskRepository = taskRepository;
        this.bottleneckDetectionService = bottleneckDetectionService;
    }

    @GetMapping("/agents")
    public List<AgentDashboardDto> getAgents() {
        return accountRepository.findAll().stream().map(account -> {
            ClaimEntity activeClaim = claimRepository.findByAccountIdAndReleasedAtIsNull(account.getId()).orElse(null);
            return new AgentDashboardDto(
                account.getId(),
                account.getName(),
                account.getStatus(),
                activeClaim != null ? activeClaim.getRole().getTag() : null,
                activeClaim != null ? activeClaim.getTask().getDescription() : null,
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
