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
import com.eneik.production.models.persistence.WishlistStatus;
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
            if (resumeEligibleTask(task, project.getId(), resumed)) {
                resumed++;
            }
        }
        return resumed;
    }

    /**
     * Single-task entry point (2026-08-01) for GeminiObserverActionService's reviveFailedTask tool - same
     * atomic CAS/resume-count-cap/dependency-safety guarantees as resumeNextFrontier, just targeted at one
     * task instead of scanning the whole project. Returns false (with a specific reason logged) rather than
     * throwing when the task isn't actually eligible, so the caller can surface a clear denial instead of a
     * stack trace for what is, from Gemini's side, an ordinary "not applicable right now" outcome.
     */
    @Transactional
    public boolean resumeTask(java.util.UUID taskId) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getProject() == null) {
            return false;
        }
        return resumeEligibleTask(task, task.getProject().getId(), 0);
    }

    private boolean resumeEligibleTask(TaskEntity task, java.util.UUID projectId, int frontierIndexForLog) {
        if (!isEligibleRetiredPlanTask(task) || resumeCount(task) >= 1) {
            return false;
        }
        if (claimService.hasActiveClaim(task.getId()) || hasActiveSession(task)) {
            return false;
        }
        if (readinessService.isTaskMerged(task.getId())) {
            return false;
        }
        if (task.getDependsOn() != null && !readinessService.isDependencySatisfied(task.getDependsOn())) {
            return false;
        }

        // Atomic CAS, not task.setStatus()+save(): isEligibleRetiredPlanTask() and resumeCount() above
        // are both stale reads by the time this write executes - two overlapping scheduler ticks (or
        // this method racing the self-healing/lease-expiry paths in ClaimService) could otherwise both
        // pass the checks and resume the same failed task twice, producing two live claims/sessions for
        // one task identity (the exact IncorrectResultSizeDataAccessException incident, 2026-07-24).
        // The status flip only lands if the row is still exactly 'failed' at this instant; a concurrent
        // resume attempt sees 0 rows affected and correctly backs off instead of resurrecting it again.
        int revived = taskRepository.compareAndSetStatus(task.getId(), TaskStatus.failed, TaskStatus.queued);
        if (revived == 0) {
            log.info("PlannedWorkRecoveryService: skipped resume for task {} - it left 'failed' concurrently (already resumed elsewhere)", task.getId());
            return false;
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
        log.warn("PlannedWorkRecoveryService: resumed existing task {} for project {} (frontier {}); "
                        + "no new task or wishlist was created",
                task.getId(), projectId, frontierIndexForLog + 1);
        return true;
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
        // Widened 2026-08-01 (confirmed live, test-fortieth: tasks d9f35f4b/529e5252 both died this exact
        // way and had to be revived by hand via a raw status PATCH, bypassing this method's own atomic
        // CAS/resume-count/dependency safety entirely). The two original strings below cover one specific
        // historical incident; this new substring covers the GENERAL case - any task
        // reconcileClosedUnmergedPullRequest (JulesDispatchService) marked failed because its PR closed
        // without merging and nothing was left actively working it. Whether the underlying PR died from
        // infra flakiness or a real merge conflict, retrying once from clean main is the same safe default
        // action either way - this method's existing resume-count cap (max 1 auto-resume per task) already
        // protects against a repeatedly-failing task being retried forever, so widening the trigger
        // condition does not widen the blast radius.
        return reason.contains("auto-recovery is disabled; dependent task retired")
                || reason.contains("Blocked task retired; auto-recovery follow-up disabled during task-expansion incident")
                || reason.contains("left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)");
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

    /**
     * Self-healing: Recovers wishlists stuck in 'compiling' when no active compiler task exists.
     */
    @Transactional
    public int recoverStuckCompilingWishlists(ProjectEntity project) {
        if (project == null) return 0;
        List<WishlistEntity> compiling = wishlistRepository.findByProjectIdAndStatus(project.getId(), WishlistStatus.compiling);
        if (compiling.isEmpty()) return 0;

        List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        boolean hasActiveCompilerTask = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.queued || t.getStatus() == TaskStatus.claimed || t.getStatus() == TaskStatus.in_progress)
                .anyMatch(t -> t.getPayload() != null && t.getPayload().has("compilesWishlistIds"));

        if (hasActiveCompilerTask) return 0;

        int recovered = 0;
        List<WishlistEntity> allProjectWishlists = wishlistRepository.findByProjectId(project.getId());

        for (WishlistEntity w : compiling) {
            boolean hasCompiledTasks = projectTasks.stream().anyMatch(t -> w.getId().equals(t.getSourceWishlistId()));
            ClientDeliverableReadinessService.Readiness rootReadiness =
                    readinessService.computeForProject(project.getId(), w.getId());
            boolean hasCompiledFeatureGraph = rootReadiness.decompositionComplete()
                    && rootReadiness.totalFeatures() > 0;

            if (hasCompiledTasks || hasCompiledFeatureGraph) {
                w.setStatus(WishlistStatus.converted_to_task);
                wishlistRepository.save(w);
                log.info("[RECOVERY] Recovered stuck compiling wishlist {} -> converted_to_task for project {}", w.getId(), project.getName());
                recovered++;
            } else {
                w.setStatus(WishlistStatus.pending);
                wishlistRepository.save(w);
                log.info("[RECOVERY] Recovered stuck compiling wishlist {} -> pending for project {}", w.getId(), project.getName());
                recovered++;
            }
        }
        return recovered;
    }

    /**
     * Lean Cleanout Invariant: When all client/product feature tasks for a project are completed and merged (done),
     * automatically dismiss any remaining non-product / meta / repair tasks stranded in queued/claimed status
     * so active task count reaches 0 and triggers Coverage and Falsification Audits immediately.
     */
    @Transactional
    public int cleanoutOrphanedMetaTasksWhenProductComplete(ProjectEntity project) {
        if (project == null) return 0;
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(project.getId());
        if (!readiness.decompositionComplete() || readiness.totalFeatures() == 0) {
            return 0;
        }
        List<TaskEntity> projectTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        List<TaskEntity> productFeatureTasks = projectTasks.stream()
                .filter(t -> !isMetaTask(t))
                .toList();

        if (productFeatureTasks.isEmpty()) return 0;

        boolean allProductFeaturesFinished = productFeatureTasks.stream().allMatch(t ->
                t.getStatus() == TaskStatus.done ||
                t.getStatus() == TaskStatus.failed ||
                t.getStatus() == TaskStatus.blocked);
        if (!allProductFeaturesFinished) return 0;

        List<TaskEntity> activeMetaTasks = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.queued || t.getStatus() == TaskStatus.claimed || t.getStatus() == TaskStatus.in_progress)
                .filter(this::isMetaTask)
                .toList();

        if (activeMetaTasks.isEmpty()) return 0;

        int dismissedCount = 0;
        for (TaskEntity metaTask : activeMetaTasks) {
            metaTask.setStatus(TaskStatus.done);
            taskRepository.save(metaTask);
            claimService.releaseTerminalClaim(metaTask.getId());
            log.info("[LEAN-CLEANOUT] Completed orphaned meta task {} ({}) because all product features are 100% complete and merged in main.",
                    metaTask.getId(), metaTask.getTitle());
            dismissedCount++;
        }
        return dismissedCount;
    }

    private boolean isMetaTask(TaskEntity t) {
        if (t == null) return false;
        String text = ((t.getTitle() == null ? "" : t.getTitle()) + " " + (t.getDescription() == null ? "" : t.getDescription())).toLowerCase();
        return text.contains("stagnation") || text.contains("pr review fallback") || text.contains("compile 1 wishlist") ||
               t.getTargetContext() == com.eneik.production.models.persistence.TargetContext.ORCHESTRATOR_SYSTEM;
    }
}
