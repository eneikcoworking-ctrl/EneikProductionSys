package com.eneik.production.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Repairs only the known containment incident that retired an existing product plan. It requeues the
 * same task identity once, frontier by frontier, and never creates a task, wishlist, branch, or session.
 */
@Service
public class PlannedWorkRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PlannedWorkRecoveryService.class);
    private static final String RESUME_COUNT_KEY = "ems_bounded_plan_resume_count";
    private static final Set<WishlistSource> PRODUCT_SOURCES = EnumSet.of(
            WishlistSource.client,
            WishlistSource.coverage_gap,
            WishlistSource.self_falsification
    );

    @org.springframework.beans.factory.annotation.Value("${project.failed-plan-frontier-resume-limit:3}")
    private int frontierResumeLimit;

    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final ClaimService claimService;
    private final ClientDeliverableReadinessService readinessService;
    private final ObjectMapper objectMapper;

    public PlannedWorkRecoveryService(TaskRepository taskRepository,
                                      WishlistRepository wishlistRepository,
                                      JulesSessionRepository julesSessionRepository,
                                      ClaimService claimService,
                                      ClientDeliverableReadinessService readinessService,
                                      ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.claimService = claimService;
        this.readinessService = readinessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int resumeNextFrontier(ProjectEntity project) {
        int resumed = 0;
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .sorted(java.util.Comparator.comparing(TaskEntity::getCreatedAt))
                .toList();
        for (TaskEntity task : tasks) {
            if (resumed >= Math.max(1, frontierResumeLimit)) {
                break;
            }
            if (!isEligibleRetiredPlanTask(task) || resumeCount(task) >= 1) {
                continue;
            }
            if (claimService.hasActiveClaim(task.getId()) || hasActiveSession(task)) {
                continue;
            }
            if (readinessService.isTaskMerged(task.getId())) {
                continue;
            }
            if (task.getDependsOn() != null && !readinessService.isDependencySatisfied(task.getDependsOn())) {
                continue;
            }

            ObjectNode payload = objectPayload(task.getPayload());
            payload.put(RESUME_COUNT_KEY, 1);
            payload.put("ems_bounded_plan_resume_at", Instant.now().toString());
            task.setPayload(payload);
            task.setStatus(TaskStatus.queued);
            task.setJulesSessionName(null);
            task.setJulesDispatchStatus("Poka-yoke bounded resume 1/1: reusing the original planned task identity");
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            resumed++;
            log.warn("PlannedWorkRecoveryService: resumed existing task {} for project {} (frontier {}/{}); "
                            + "no new task or wishlist was created",
                    task.getId(), project.getId(), resumed, Math.max(1, frontierResumeLimit));
        }
        return resumed;
    }

    private boolean isEligibleRetiredPlanTask(TaskEntity task) {
        if (task.getStatus() != TaskStatus.failed || task.getSourceWishlistId() == null
                || task.getFeatureId() == null) {
            return false;
        }
        WishlistEntity source = wishlistRepository.findById(task.getSourceWishlistId()).orElse(null);
        if (source == null || source.getCompiledByRole() == null || !PRODUCT_SOURCES.contains(source.getSource())) {
            return false;
        }
        String reason = task.getJulesDispatchStatus() == null ? "" : task.getJulesDispatchStatus();
        return reason.contains("auto-recovery is disabled; dependent task retired")
                || reason.contains("Blocked task retired; auto-recovery follow-up disabled during task-expansion incident");
    }

    private int resumeCount(TaskEntity task) {
        return task.getPayload() == null ? 0 : task.getPayload().path(RESUME_COUNT_KEY).asInt(0);
    }

    private boolean hasActiveSession(TaskEntity task) {
        return julesSessionRepository.findByTaskId(task.getId()).stream().anyMatch(session -> {
            String status = session.getStatus();
            return "queued".equals(status) || "running".equals(status) || "revising".equals(status)
                    || "pr_opened".equals(status) || "stuck".equals(status);
        });
    }

    private ObjectNode objectPayload(JsonNode payload) {
        if (payload != null && payload.isObject()) {
            return (ObjectNode) payload.deepCopy();
        }
        return objectMapper.createObjectNode();
    }
}
