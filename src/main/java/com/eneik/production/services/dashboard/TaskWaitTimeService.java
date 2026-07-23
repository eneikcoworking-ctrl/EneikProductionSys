package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.WaitTimeBreakdownDto;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.ProjectFlowService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lean-waste task-waiting-time metric (2026-07-23, operator directive): the operator drew an explicit
 * line between time a task spends actively assigned to a Jules session (trusted, never counted as waste,
 * however long it takes) and time a task spends queued/blocked BEFORE that point (real Lean waste). This
 * classifies every currently queued task by the exact same predicates ProjectFlowService.dispatchQueuedTasks
 * itself evaluates each tick, so the metric can never drift from real dispatch behavior - it is not a
 * separate model of the pipeline, it is an observation of the same one.
 */
@Service
public class TaskWaitTimeService {

    private enum WaitReason { BLOCKED_BY_DEPENDENCY, HELD_BUILD_PHASE, WAITING_FOR_CAPACITY }

    private final TaskRepository taskRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final ProjectFlowService projectFlowService;

    public TaskWaitTimeService(TaskRepository taskRepository,
                                ClientDeliverableReadinessService readinessService,
                                ProjectFlowService projectFlowService) {
        this.taskRepository = taskRepository;
        this.readinessService = readinessService;
        this.projectFlowService = projectFlowService;
    }

    public WaitTimeBreakdownDto computeForProject(UUID projectId) {
        List<TaskEntity> queued = taskRepository
                .findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(projectId, TaskStatus.queued);
        boolean buildPhase = readinessService.isBuildPhase(projectId);
        Instant now = Instant.now();

        Map<WaitReason, List<TaskEntity>> byReason = new EnumMap<>(WaitReason.class);
        for (WaitReason reason : WaitReason.values()) {
            byReason.put(reason, new ArrayList<>());
        }
        for (TaskEntity task : queued) {
            byReason.get(classify(task, buildPhase)).add(task);
        }

        List<WaitTimeBreakdownDto.BucketDto> buckets = new ArrayList<>();
        for (WaitReason reason : WaitReason.values()) {
            buckets.add(toBucket(reason, byReason.get(reason), now));
        }
        return new WaitTimeBreakdownDto(buckets, queued.size());
    }

    private WaitReason classify(TaskEntity task, boolean buildPhase) {
        TaskEntity dependency = task.getDependsOn();
        if (dependency != null && !readinessService.isDependencySatisfied(dependency)
                && !readinessService.isApiContractPrOpenButUnmerged(dependency)) {
            return WaitReason.BLOCKED_BY_DEPENDENCY;
        }
        if (buildPhase && projectFlowService.isSelfGeneratedWork(task)) {
            return WaitReason.HELD_BUILD_PHASE;
        }
        // Everything else is otherwise dispatch-eligible this tick but for account/WIP capacity - the
        // residual bucket. Explicitly does NOT include time spent claimed/in an active Jules session
        // (that is a separate, trusted concept - see AgentDashboardDto.claimedAt) and never will.
        return WaitReason.WAITING_FOR_CAPACITY;
    }

    private WaitTimeBreakdownDto.BucketDto toBucket(WaitReason reason, List<TaskEntity> tasks, Instant now) {
        if (tasks.isEmpty()) {
            return new WaitTimeBreakdownDto.BucketDto(reason.name().toLowerCase(), 0, 0.0, 0);
        }
        long totalMinutes = 0;
        long oldestMinutes = 0;
        for (TaskEntity task : tasks) {
            long minutes = Duration.between(task.getCreatedAt(), now).toMinutes();
            totalMinutes += minutes;
            oldestMinutes = Math.max(oldestMinutes, minutes);
        }
        double avgMinutes = (double) totalMinutes / tasks.size();
        return new WaitTimeBreakdownDto.BucketDto(reason.name().toLowerCase(), tasks.size(), avgMinutes, oldestMinutes);
    }
}
