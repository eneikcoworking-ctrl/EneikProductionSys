package com.eneik.production.services;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BottleneckAwarePriorityService {
    private static final Logger log = LoggerFactory.getLogger(BottleneckAwarePriorityService.class);
    private static final int HIGH_PRIORITY = 100;
    private static final int DEFAULT_PRIORITY = 0;

    private final BottleneckDetectionService bottleneckDetectionService;
    private final TaskRepository taskRepository;
    private final ClaimService claimService;

    public BottleneckAwarePriorityService(BottleneckDetectionService bottleneckDetectionService,
                                          TaskRepository taskRepository,
                                          ClaimService claimService) {
        this.bottleneckDetectionService = bottleneckDetectionService;
        this.taskRepository = taskRepository;
        this.claimService = claimService;
    }

    public int computePriority(String tocConstraintRef) {
        if (tocConstraintRef == null || tocConstraintRef.isBlank()) {
            return DEFAULT_PRIORITY;
        }

        List<BottleneckDto> activeBottlenecks = bottleneckDetectionService.detect();
        for (BottleneckDto b : activeBottlenecks) {
            // Check if ref matches tag or accountId string representation
            if (tocConstraintRef.equals(b.tag()) ||
                (b.accountId() != null && tocConstraintRef.equals(b.accountId().toString()))) {
                return HIGH_PRIORITY;
            }
        }
        return DEFAULT_PRIORITY;
    }

    /**
     * Trigger for periodic priority refresh.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void scheduledPriorityRefresh() {
        log.trace("BottleneckAwarePriority: Triggering priority refresh");
        refreshQueuedTasksPriority();
    }

    @Transactional
    public void refreshQueuedTasksPriority() {
        List<BottleneckDto> activeBottlenecks = bottleneckDetectionService.detect();

        Set<String> bottleneckRefs = activeBottlenecks.stream()
            .flatMap(b -> {
                java.util.stream.Stream.Builder<String> builder = java.util.stream.Stream.builder();
                if (b.tag() != null) builder.add(b.tag());
                if (b.accountId() != null) builder.add(b.accountId().toString());
                return builder.build();
            })
            .collect(Collectors.toSet());

        claimService.refreshQueuedTasksPriority(bottleneckRefs, HIGH_PRIORITY, DEFAULT_PRIORITY);
    }
}
