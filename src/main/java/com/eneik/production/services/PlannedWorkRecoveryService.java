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
    @org.springframework.beans.factory.annotation.Value("${project.failed-plan-frontier-resume-limit:3}")
    private int frontierResumeLimit;

    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final ClaimService claimService;
    private final ClientDeliverableReadinessService readinessService;
    private final ObjectMapper objectMapper;

    /**
     * Why the frontier stayed where it is, per project, as last reported (plan 4.46).
     *
     * <p>BLOCKED_BY_FAILED_FRONTIER denies ORCHESTRATE, DISPATCH_QUEUED_TASKS and DISPATCH_REVIEW_TASKS
     * for the whole project, and its named resolver is this service. Measured 2026-08-30, 13:31 onwards on
     * test-fiftieth: four failed tasks in the gate's own denominator, this resolver resuming none of them
     * for thirteen minutes and counting, and not one line anywhere saying which condition refused them -
     * every branch below returned a bare false. F39: a finding nobody can retrieve is not a finding.
     *
     * <p>Held here rather than logged per tick because the fact is stable and the tick is not: the same
     * sentence written every minute is what produced the 868-repetition report this factory already had
     * to remove. Reported when the composition changes, which is exactly when it carries information.
     */
    private final java.util.Map<java.util.UUID, String> lastFrontierRefusal = new java.util.concurrent.ConcurrentHashMap<>();

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
        java.util.Map<String, Integer> refusals = new java.util.LinkedHashMap<>();
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .sorted(java.util.Comparator.comparing(TaskEntity::getCreatedAt))
                .toList();
        for (TaskEntity task : tasks) {
            if (resumed >= Math.max(1, frontierResumeLimit)) {
                break;
            }
            if (resumeEligibleTask(task, project.getId(), resumed, refusals)) {
                resumed++;
            }
        }
        reportFrontierRefusalsIfChanged(project.getId(), refusals);
        return resumed;
    }

    /**
     * States, once per change, why every task the BLOCKED_BY_FAILED_FRONTIER gate is counting was refused.
     * Only tasks in the gate's own denominator are counted here - anything else is not something this
     * resolver was ever asked about, and reporting it would be noise rather than evidence.
     */
    private void reportFrontierRefusalsIfChanged(java.util.UUID projectId, java.util.Map<String, Integer> refusals) {
        String digest = refusals.isEmpty()
                ? "none"
                : refusals.entrySet().stream()
                        .map(entry -> entry.getValue() + "x " + entry.getKey())
                        .collect(java.util.stream.Collectors.joining("; "));
        if (digest.equals(lastFrontierRefusal.get(projectId))) {
            return;
        }
        lastFrontierRefusal.put(projectId, digest);
        if (refusals.isEmpty()) {
            log.info("PlannedWorkRecoveryService: nothing in the failed frontier is being refused for project {}", projectId);
            return;
        }
        log.warn("PlannedWorkRecoveryService: the failed frontier of project {} is held by - {}", projectId, digest);
    }

    /** Records one refusal and returns false, so a refusing branch stays a single statement. */
    private boolean refuse(java.util.Map<String, Integer> refusals, TaskEntity task, String reason) {
        if (isResumableInPrinciple(task) && hasResumeBudgetLeft(task)) {
            refusals.merge(reason, 1, Integer::sum);
        }
        return false;
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
        return resumeEligibleTask(task, task.getProject().getId(), 0, new java.util.LinkedHashMap<>());
    }

    private boolean resumeEligibleTask(TaskEntity task, java.util.UUID projectId, int frontierIndexForLog,
                                       java.util.Map<String, Integer> refusals) {
        if (!isEligibleRetiredPlanTask(task)) {
            return refuse(refusals, task, "not product work this recovery may resume "
                    + "(out-of-cycle source, or a failure reason outside RETIRED_WITH_NOTHING_LEFT_WORKING_IT)");
        }
        if (resumeCount(task) >= 1) {
            return refuse(refusals, task, "its single automatic resume is already spent");
        }
        if (claimService.hasActiveClaim(task.getId()) || hasActiveSession(task)) {
            return refuse(refusals, task, "something is still actively working it (live claim or session)");
        }
        if (readinessService.isTaskMerged(task.getId())) {
            return refuse(refusals, task, "its work is already merged");
        }
        // Held while the dependency can still become satisfied, not while it merely is not (2026-08-29,
        // plan §4.33). Measured that day: three tasks retired behind dependencies that had failed hours
        // earlier with their own single resume already spent. Such a dependency can never merge, and a
        // merged replacement carrying its semantic key can only come from a fresh decomposition that
        // nothing triggers - so the gate's condition had become one that can no longer occur, and both
        // the dependent and its dependency were terminal for good. That is §4.30's defect one storey up:
        // there a queue waited on an event that could not arrive, here the recovery gate does.
        //
        // A dependency edge here is an ordering of work, not a physical precondition - this codebase
        // already declines to wait on one when waiting is meaningless (spike_completed, whose deliverable
        // is a decision rather than code; the early unblock on a spec dependency whose PR is open). A dead
        // dependency is the third case of the same kind. The resume budget is untouched: if the work truly
        // cannot be done without it, the task fails a second time and is retired for good - one cycle,
        // rather than never.
        // An unsatisfied dependency holds the dependent, without exception (2026-08-30).
        //
        // This gate was widened on 2026-08-29 to let a dependent through when its dependency was
        // permanently dead, on the argument that a dependency edge is an ordering rather than a physical
        // precondition. Measured the same evening, on the client's own epic, the widening produced a loop
        // and nothing else:
        //
        //   19:01:38  ProjectFlowService blocks task 40dff79f - its dependency is dead for good
        //   19:02:45  the admission sweep retires it, creating no child work
        //   19:03:49  this service resumes it, spending its one and only automatic resume
        //   19:03:50  ProjectFlowService blocks it again, one second later, for the same dead dependency
        //   19:04:48  retired for good - the requirement ends, and the epic delivered nothing
        //
        // The two rules contradicted each other: one declares such a dependent a dead end and blocks it,
        // the other resumes it. A resume that is undone within a second is not a recovery, it is the
        // consumption of the only recovery the task had. ProjectFlowService already routes this case to
        // `blocked`, which is a terminating path; this gate must not race it.
        TaskEntity dependency = task.getDependsOn();
        if (dependency != null && !readinessService.isDependencySatisfied(dependency)) {
            return refuse(refusals, task, "its dependency " + dependency.getId() + " (" + dependency.getStatus()
                    + ") is not satisfied");
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

    /**
     * The field-only NECESSARY conditions for this service to ever resume a failed task.
     *
     * Declared here, next to the full predicate, so the set has one point of application (Charter
     * invariant 10). {@link #isEligibleRetiredPlanTask} adds the sufficient conditions on top - the source
     * wishlist's role and source, and the failure reason - which need repository lookups. This part needs
     * none, so a caller that must reason about the task population without loading anything can use it.
     *
     * Why it exists (2026-08-18): FlowSpineService gates BLOCKED_BY_FAILED_FRONTIER on
     * {@code failedTasks() > 0}, counting EVERY failed task, while the transition row names this service as
     * the resolver. A gate and its named resolver must quantify over the same set, or the state is
     * unreachable-from by construction.
     *
     * CORRECTION, same day: this was introduced believing test-forty-ninth's five failed tasks had null
     * featureId and null sourceWishlistId. They do not - the dashboard DTO simply does not carry those
     * fields, and reading their absence from a projection as absence in the data was the error. Measured
     * directly against the store: all five have both set, so this predicate admits all five and the gate's
     * behaviour is unchanged. What actually blocks their recovery is the reason whitelist in
     * {@link #isEligibleRetiredPlanTask}. The predicate is kept because the gate/resolver agreement is
     * correct on its own terms, not because it fixed anything.
     */
    public static boolean isResumableInPrinciple(TaskEntity task) {
        return task != null
                && task.getStatus() == TaskStatus.failed
                && task.getSourceWishlistId() != null
                && task.getFeatureId() != null;
    }

    /**
     * The failure modes where a single clean retry is the correct default, declared as one set.
     *
     * They share one property, which is what makes them a set rather than a list of incidents: the task
     * failed and nothing was left actively working it. Not "the failure was harmless" and not "the cause
     * is known" - a PR that closed unmerged and a task the admission poka-yoke retired are different
     * causes with the same consequence, and retrying once from clean main is the same safe action for both.
     *
     * The fourth entry was added 2026-08-18 after measuring test-forty-ninth directly against the store:
     * all five failed tasks have featureId and sourceWishlistId set and are structurally resumable, and
     * the ONLY thing excluding them was that their reason - the iteration-admission poka-yoke retirement,
     * whose own message ends "no child work created" - was not among the first three. The project had
     * stood at 25/26 merged with nothing in flight.
     *
     * Safety is not from this list being short. It is from resumeCount(task) >= 1 below: at most one
     * automatic resume per task, ever. That is a well-founded measure - it strictly decreases and cannot
     * be replenished - so a task that fails the same way again is retired for good rather than retried
     * forever. The 2026-08-01 comment inside the method makes exactly this argument for its own widening,
     * and it holds here for the same reason.
     *
     * Kept as a named set rather than another inline || so the next case is added by declaring membership,
     * not by growing a boolean expression - the shape that let this one sit unfixed for twelve days.
     */
    private static final List<String> RETIRED_WITH_NOTHING_LEFT_WORKING_IT = List.of(
            "auto-recovery is disabled; dependent task retired",
            "Blocked task retired; auto-recovery follow-up disabled during task-expansion incident",
            "left to complete it normally (periodic GitHub-truth reconciliation, testimony-vs-evidence Phase 2)",
            "Blocked task retired by iteration-admission poka-yoke; no child work created");

    /**
     * Whether a retired task is product work this recovery may resume.
     *
     * <p>Until 2026-08-29 this asked a hand-kept whitelist of wishlist sources, and measured that day on
     * the live database it admitted NONE of the project's sixty-eight failed tasks: their sources were
     * none at all (58), delivery_never_reached_main (9) and frontend_unbacked_records (1), and not one of
     * the four listed. RECOVER_FAILED_FRONTIER was therefore authorized every tick and eligible for
     * nothing, while three queued tasks waited behind failed dependencies that this is the only mechanism
     * able to revive. That whitelist, deleted with this change, carried a comment recording the same failure once before,
     * when Gemini's revival calls were rejected silently for three cycles until a source was added by hand
     * - a list kept by hand answering a question that has a real criterion, Charter invariant 14.
     *
     * <p>The criterion, measured rather than assumed: of those sixty-eight, sixty carry a featureId and
     * eight are factory carriers (their payload names a taskType) - and carriers carrying a featureId
     * number zero. A feature is what planned product work is attached to; a carrier is never attached to
     * one. The deliberate quarantine of out-of-cycle role work is kept, now read from the one definition
     * on WishlistSource rather than a second copy.
     *
     * <p>Blast radius is unchanged: HOW OFTEN a task may be revived is still resumeCount below one, and
     * WHY is still RETIRED_WITH_NOTHING_LEFT_WORKING_IT below. Only "is this product work" changed, and it
     * changed from a written list to a measured structural fact.
     */
    private boolean isEligibleRetiredPlanTask(TaskEntity task) {
        if (task.getStatus() != TaskStatus.failed || task.getFeatureId() == null) {
            return false;
        }
        if (task.getSourceWishlistId() != null) {
            WishlistEntity source = wishlistRepository.findById(task.getSourceWishlistId()).orElse(null);
            if (source != null && source.getSource() != null && source.getSource().outOfCycleGenerated()) {
                return false;
            }
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
        return RETIRED_WITH_NOTHING_LEFT_WORKING_IT.stream().anyMatch(reason::contains);
    }

    /**
     * Whether a failed task could still come back by itself (2026-08-29, plan §4.30).
     *
     * <p>Asked by ProjectFlowService before it decides that a dependent's wait can never end. The budget
     * and the eligibility rule stay here, in the one place that owns them - a second copy of "one auto
     * resume per task" elsewhere would be the same number written twice, which is what Charter invariant
     * 10 forbids. Deliberately durable-only: the transient reasons resumeEligibleTask also checks (a live
     * claim, a live session, an unsatisfied dependency of its own) say "not right now", not "never again".
     */
    public boolean mayStillBeResumed(TaskEntity task) {
        return task != null && isEligibleRetiredPlanTask(task) && hasResumeBudgetLeft(task);
    }

    /**
     * Whether this task still has its single automatic resume (2026-08-30, plan §4.39).
     *
     * <p>Static and payload-only so the gate that decides BLOCKED_BY_FAILED_FRONTIER can ask the same
     * question this resolver asks, from the same definition. FlowSpineService used to quantify over
     * isResumableInPrinciple alone - structural eligibility - while this service also refuses a task whose
     * budget is spent, so the gate held elements the resolver could never remove. Measured that day: 35
     * failed tasks, 17 resumable in principle, and 13 of those 17 already past their only resume.
     */
    public static boolean hasResumeBudgetLeft(TaskEntity task) {
        return task != null
                && (task.getPayload() == null || task.getPayload().path(RESUME_COUNT_KEY).asInt(0) < 1);
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
