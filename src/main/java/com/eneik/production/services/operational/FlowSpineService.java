package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.FlowSpineEventEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.FlowSpineEventRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.EmbeddingSimilarityUtil;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.dashboard.SystemStatusService;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverPromotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FlowSpineService {
    private static final Set<String> OPEN_SESSION_STATUSES = Set.of("queued", "running", "pr_opened", "revising", "stuck");
    private static final Set<String> FAILING_REVIEW_STATUSES = Set.of(
            "failure", "failing", "conflict", "escalated", "closed_unmerged", "invalid_pr", "unowned"
    );

    /**
     * Failing statuses from which no resolver can move the review (model rule 8.6: an element with no path
     * to done leaves the decision set).
     *
     * <p>BLOCKED_BY_REVIEW names AutoMergeService as its resolver, and a pull request that is closed on
     * GitHub without merging cannot be merged or repaired - there is nothing left to act on. Measured
     * 2026-09-02, once the state was made to name its own composition: "5 failing/conflicted review(s)
     * exist {closed_unmerged=5}", holding a bottleneck that had been breached for 6841 minutes, four and
     * three quarter days. Scoping the count to live sessions was an earlier attempt at this same problem
     * and is not enough: the session outlives the pull request.
     *
     * <p>Only the measured status is excluded. `escalated`, `invalid_pr` and `unowned` are the same shape
     * and are deliberately left in until they are observed holding - changing what has not been measured is
     * how a repair becomes a guess. The task's own non-delivery is not lost either way: it is carried by
     * hasRequiredMergeEvidence and by the delivery-reality department.
     */
    private static final Set<String> UNRESOLVABLE_REVIEW_STATUSES = Set.of("closed_unmerged");

    /**
     * A task the factory created to carry its own process - the wishlist compiler, a review fallback, an
     * audit, a design review. Every one of them is stamped with a taskType in its payload when it is built,
     * and no product task ever is. Measured 2026-08-29 across the project's 68 failed tasks: 60 carried a
     * feature and 8 were carriers, and carriers carrying a feature numbered zero.
     */
    static boolean isFactoryCarrierTask(TaskEntity task) {
        return task.getPayload() != null && task.getPayload().hasNonNull("taskType");
    }

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;
    private final FlowSpineEventRepository flowSpineEventRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final SystemStatusService systemStatusService;
    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final LeverPromotionService leverPromotionService;

    private static final Logger log = LoggerFactory.getLogger(FlowSpineService.class);

    public FlowSpineService(ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            WishlistRepository wishlistRepository,
                            JulesSessionRepository julesSessionRepository,
                            PrReviewRepository prReviewRepository,
                            FlowSpineEventRepository flowSpineEventRepository,
                            ClientDeliverableReadinessService readinessService,
                            SystemStatusService systemStatusService,
                            MLPredictionServiceClient mlPredictionServiceClient,
                            LeverPromotionService leverPromotionService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.flowSpineEventRepository = flowSpineEventRepository;
        this.readinessService = readinessService;
        this.systemStatusService = systemStatusService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.leverPromotionService = leverPromotionService;
    }

    @Transactional(readOnly = true)
    public FlowSpineDto build(UUID projectId) {
        return buildModel(projectId).dto();
    }

    @Transactional
    public FlowSpineDto observe(UUID projectId) {
        FlowModel model = buildModel(projectId);
        FlowSpineEventEntity latest = model.latestEvent();
        boolean changed = latest == null
                || !model.currentState().equals(latest.getCurrentState())
                || !model.evidenceHash().equals(latest.getEvidenceHash());
        if (changed) {
            FlowSpineEventEntity event = new FlowSpineEventEntity();
            event.setProjectId(projectId);
            event.setCycleId(UUID.randomUUID());
            event.setObservedAt(Instant.now());
            event.setPreviousState(latest == null ? null : latest.getCurrentState());
            event.setCurrentState(model.currentState());
            event.setNextState(model.nextTransition().to());
            event.setValueStatus(model.valueStatus());
            FlowSpineDto.Bottleneck primary = model.bottlenecks().isEmpty() ? null : model.bottlenecks().get(0);
            event.setBottleneckType(primary == null ? null : primary.type());
            event.setBottleneckSeverity(primary == null ? null : primary.severity());
            event.setAgeInStateMinutes(model.ageInStateMinutes());
            event.setOwner(model.nextTransition().owner());
            event.setTransitionAction(model.nextTransition().action());
            event.setEvidenceHash(model.evidenceHash());
            event.setEvidenceSummary(model.evidenceSummary());
            event.setBlockingReason(blankToNull(model.blockingReason()));
            event.setMode("observe_only");
            flowSpineEventRepository.save(event);
        }
        return buildModel(projectId).dto();
    }

    @Transactional(readOnly = true)
    public List<FlowSpineDto.FlowEvent> events(UUID projectId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        return flowSpineEventRepository.findByProjectIdOrderByObservedAtDesc(projectId, PageRequest.of(0, bounded))
                .stream()
                .map(this::toEventDto)
                .toList();
    }

    private FlowModel buildModel(UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<WishlistEntity> wishlist = wishlistRepository.findByProjectId(projectId);
        List<JulesSessionEntity> sessions = sessionsForTasks(tasks);
        List<PrReviewEntity> reviews = reviewsForSessions(sessions);
        ClientDeliverableReadinessService.Readiness readiness = readinessService.computeForProject(projectId);
        String systemStatus = systemStallStatus(systemStatusService.getStatus(projectId));
        boolean duplicateContent = duplicateContent(tasks);

        StateInputs inputs = inputs(project.getStatus(), tasks, wishlist, sessions, reviews, readiness,
                systemStatus, duplicateContent);
        String currentState = decideState(inputs);
        FlowSpineDto.Transition next = nextTransition(currentState, inputs);
        String valueStatus = valueStatus(currentState, inputs);
        String blockingReason = blockingReason(currentState, inputs);
        FlowSpineDto.EvidenceVector evidence = evidence(inputs);
        FlowSpineDto.FlowCounts counts = counts(inputs);
        long ageInStateMinutes = ageInStateMinutes(currentState, project, tasks, wishlist, sessions, reviews);
        List<FlowSpineDto.Bottleneck> bottlenecks = bottlenecks(currentState, inputs, next, ageInStateMinutes, blockingReason);
        String evidenceSummary = evidenceSummary(evidence, counts, blockingReason);
        String evidenceHash = evidenceHash(currentState, valueStatus, next.to(), evidenceSummary);
        FlowSpineEventEntity latestEvent = flowSpineEventRepository.findTop1ByProjectIdOrderByObservedAtDesc(projectId)
                .orElse(null);
        long eventCount = flowSpineEventRepository.countByProjectId(projectId);

        FlowSpineDto dto = new FlowSpineDto(
                Instant.now(),
                "observe_only",
                new FlowSpineDto.ProjectRef(
                        project.getId(),
                        project.getName(),
                        project.getStatus() == null ? "unknown" : project.getStatus().name(),
                        project.getRepositoryName()),
                currentState,
                valueStatus,
                blockingReason,
                next,
                List.of(next),
                transitionMatrix(),
                bottlenecks,
                forbiddenTransitions(),
                evidence,
                counts,
                invariants(inputs),
                journalSummary(latestEvent, currentState, evidenceHash, eventCount),
                "deterministic precedence: project terminality > local hard blockers > external truth availability > client-scope decomposition > live WIP > review > delivery evidence > idle"
        );
        return new FlowModel(dto, currentState, valueStatus, next, bottlenecks, blockingReason,
                evidenceHash, evidenceSummary, ageInStateMinutes, latestEvent);
    }

    static String decideState(StateInputs input) {
        if (input.projectStatus() == ProjectStatus.frozen) {
            return "FROZEN";
        }
        if (input.projectStatus() == ProjectStatus.accepted) {
            return "ACCEPTED";
        }
        if (input.projectStatus() == ProjectStatus.archived) {
            return "ARCHIVED";
        }
        if (input.projectStatus() == ProjectStatus.analyzing || input.projectStatus() == ProjectStatus.waiting) {
            return "PROJECT_NOT_ACTIVE";
        }
        if (input.duplicateContentDetected()) {
            return "BLOCKED_BY_DUPLICATE_CONTENT";
        }
        if ("github_rate_limited".equals(normalize(input.systemStatus()))) {
            return "GITHUB_RATE_LIMITED";
        }
        if ("stalled".equals(normalize(input.systemStatus()))) {
            return "SYSTEM_STALLED";
        }
        if (input.blockedTasks() > 0) {
            return "BLOCKED_BY_TASK";
        }
        if (input.failingReviews() > 0) {
            return "BLOCKED_BY_REVIEW";
        }
        if (input.pendingWishlist() > 0 || input.compilingWishlist() > 0 || !input.decompositionComplete()) {
            return "DECOMPOSING";
        }
        if (input.queuedTasks() > 0) {
            return "QUEUED";
        }
        if (input.activeTasks() > 0 || input.openSessions() > 0) {
            return "IMPLEMENTING";
        }
        if (input.reviewTasks() > 0 || input.openReviews() > 0) {
            return "UNDER_REVIEW";
        }
        if (input.totalFeatures() > 0 && input.completeFeatures() >= input.totalFeatures()) {
            return "DELIVERED";
        }
        if (input.failedTasks() > 0) {
            return "BLOCKED_BY_FAILED_FRONTIER";
        }
        if (input.doneTasks() > 0 || input.mergedReviews() > 0 || input.mergedDeliverables() > 0) {
            return "VERIFYING_DELIVERY";
        }
        if (input.totalFeatures() == 0) {
            return "NO_SCOPE";
        }
        return "IDLE_NO_ACTIONABLE_WORK";
    }

    static String valueStatus(String state, StateInputs input) {
        return switch (state) {
            case "DELIVERED", "ACCEPTED" -> "client_value_delivered";
            case "UNDER_REVIEW", "VERIFYING_DELIVERY" -> "value_evidence_pending";
            case "QUEUED", "IMPLEMENTING", "DECOMPOSING" -> "value_in_progress";
            case "NO_SCOPE", "IDLE_NO_ACTIONABLE_WORK" -> "no_current_value_flow";
            default -> "value_blocked";
        };
    }

    static boolean isBlockingState(String state) {
        return state.startsWith("BLOCKED_") || "FROZEN".equals(state) || "SYSTEM_STALLED".equals(state)
                || "GITHUB_RATE_LIMITED".equals(state) || "PROJECT_NOT_ACTIVE".equals(state) || "ARCHIVED".equals(state);
    }

    static List<FlowSpineDto.TransitionMatrixEntry> transitionMatrix() {
        return List.of(
                matrix(10, "ANY", "project.status=frozen", "FROZEN", "ProjectFlowService",
                        List.of("project.status"), "observe_only"),
                matrix(20, "ANY", "project.status in {accepted, archived}", "ACCEPTED_OR_ARCHIVED", "ProjectFlowService",
                        List.of("project.status", "acceptedAt when accepted"), "observe_only"),
                matrix(30, "ANY", "duplicate recent task content >= 3", "BLOCKED_BY_DUPLICATE_CONTENT", "ContinuousOrchestrationService",
                        List.of("recent task duplicate key counts"), "hard_gate_existing"),
                matrix(40, "ANY", "githubApiBudget.available=false", "GITHUB_RATE_LIMITED", "GitHubApiBudgetService",
                        List.of("githubApiBudget.remaining=0 or 403/429 rate-limit response", "cooldownUntil or resetAt"), "degraded_read"),
                matrix(45, "ANY", "system_stall_status=stalled", "SYSTEM_STALLED", "ContinuousOrchestrationService",
                        List.of("system status", "lastProgressAt"), "observe_only"),
                matrix(50, "ANY", "blockedTasks > 0", "BLOCKED_BY_TASK", "ProjectFlowService",
                        List.of("TaskStatus.blocked"), "observe_only"),
                matrix(60, "ANY", "failingReviews > 0", "BLOCKED_BY_REVIEW", "AutoMergeService",
                        List.of("PrReview.ciStatus in failing set"), "observe_only"),
                matrix(70, "ACTIVE", "pending/compiling wishlist or decomposition incomplete", "DECOMPOSING", "TechnicalLeadCompiler",
                        List.of("Wishlist status", "decompositionComplete=false"), "observe_only"),
                matrix(80, "ACTIVE", "queuedTasks > 0", "QUEUED", "JulesDispatchService",
                        List.of("TaskStatus.queued", "dependency/file-scope checks"), "observe_only"),
                matrix(90, "ACTIVE", "activeTasks > 0 or openSessions > 0", "IMPLEMENTING", "Jules agent",
                        List.of("TaskStatus claimed/in_progress", "Jules session open"), "observe_only"),
                matrix(100, "ACTIVE", "reviewTasks > 0 or openReviews > 0", "UNDER_REVIEW", "AutoMergeService / Gemini review",
                        List.of("PR URL or review task"), "observe_only"),
                matrix(110, "ACTIVE", "completeFeatures=totalFeatures and totalFeatures>0", "DELIVERED", "ClientDeliverableReadinessService",
                        List.of("feature readiness", "merged deliverable mapping"), "observe_only"),
                matrix(120, "ACTIVE", "failedTasks > 0 and no live work", "BLOCKED_BY_FAILED_FRONTIER", "PlannedWorkRecoveryService",
                        List.of("TaskStatus.failed", "no queued/active/review"), "observe_only"),
                matrix(130, "ACTIVE", "done/merged evidence exists but readiness incomplete", "VERIFYING_DELIVERY", "ClientDeliverableReadinessService",
                        List.of("merged reviews", "done tasks", "readiness mismatch"), "observe_only"),
                matrix(140, "ACTIVE", "totalFeatures=0", "NO_SCOPE", "ProjectFlowService",
                        List.of("no FeatureEntity scope"), "observe_only"),
                matrix(150, "ACTIVE", "no actionable work", "IDLE_NO_ACTIONABLE_WORK", "ContinuousOrchestrationService",
                        List.of("empty queue", "empty active flow"), "observe_only")
        );
    }

    static String bottleneckType(String state, String systemStatus) {
        return switch (state) {
            case "FROZEN" -> "frozen_project_bottleneck";
            case "PROJECT_NOT_ACTIVE" -> "project_activation_bottleneck";
            case "BLOCKED_BY_DUPLICATE_CONTENT" -> "duplicate_content_bottleneck";
            case "GITHUB_RATE_LIMITED" -> "github_rate_limit_bottleneck";
            case "SYSTEM_STALLED" -> "no_progress_bottleneck";
            case "BLOCKED_BY_TASK" -> "task_blocker_bottleneck";
            case "BLOCKED_BY_REVIEW" -> "review_bottleneck";
            case "BLOCKED_BY_FAILED_FRONTIER" -> "failed_frontier_bottleneck";
            case "QUEUED" -> "dispatch_bottleneck";
            case "IMPLEMENTING" -> "agent_progress_bottleneck";
            case "UNDER_REVIEW" -> "review_bottleneck";
            case "VERIFYING_DELIVERY" -> "delivery_mapping_bottleneck";
            default -> isTrustBlockingSystemStatus(systemStatus) ? "runtime_status_bottleneck" : "";
        };
    }

    static SlaSpec slaForState(String state) {
        return switch (state) {
            case "BLOCKED_BY_DUPLICATE_CONTENT" -> new SlaSpec(0, "critical");
            case "GITHUB_RATE_LIMITED" -> new SlaSpec(0, "high");
            case "SYSTEM_STALLED" -> new SlaSpec(45, "high");
            case "BLOCKED_BY_REVIEW" -> new SlaSpec(30, "high");
            case "BLOCKED_BY_FAILED_FRONTIER" -> new SlaSpec(60, "high");
            case "BLOCKED_BY_TASK" -> new SlaSpec(60, "medium");
            case "QUEUED" -> new SlaSpec(15, "medium");
            case "IMPLEMENTING" -> new SlaSpec(90, "medium");
            case "UNDER_REVIEW" -> new SlaSpec(45, "medium");
            case "VERIFYING_DELIVERY" -> new SlaSpec(30, "medium");
            case "FROZEN", "PROJECT_NOT_ACTIVE" -> new SlaSpec(-1, "medium");
            default -> new SlaSpec(-1, "none");
        };
    }

    static boolean isTrustBlockingSystemStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return !Set.of("ok", "idle_no_actionable_work", "busy_with_actionable_work")
                .contains(normalize(status));
    }

    private StateInputs inputs(ProjectStatus projectStatus,
                               List<TaskEntity> tasks,
                               List<WishlistEntity> wishlist,
                               List<JulesSessionEntity> sessions,
                               List<PrReviewEntity> reviews,
                               ClientDeliverableReadinessService.Readiness readiness,
                               String systemStatus,
                               boolean duplicateContent) {
        long queued = countStatus(tasks, TaskStatus.queued);
        long active = tasks.stream().filter(task -> Set.of(TaskStatus.claimed, TaskStatus.in_progress).contains(task.getStatus())).count();
        long review = tasks.stream().filter(task -> Set.of(TaskStatus.pending_review, TaskStatus.review).contains(task.getStatus())).count();
        long done = countStatus(tasks, TaskStatus.done) + countStatus(tasks, TaskStatus.spike_completed);
        // 2026-08-18: count only failed tasks the named resolver for this state can ever act on. The
        // transition row below names PlannedWorkRecoveryService as the resolver for
        // BLOCKED_BY_FAILED_FRONTIER, and that service declares its own domain in
        // isResumableInPrinciple. Counting every failed task instead let five tasks with null featureId and
        // null sourceWishlistId - not planned deliverables, outside totalPlannedTasks entirely - hold the
        // project in a state whose only resolver was structurally unable to clear it. The gate and its
        // resolver must quantify over the same set, or the state is unreachable-from by construction.
        // 2026-08-30 (plan §4.39): and only those the resolver can still act on. The requirement was already
        // written above - the gate and its resolver must quantify over the same set - but stated one level
        // too shallow: "in principle" is structural eligibility, while PlannedWorkRecoveryService also
        // refuses a task whose single automatic resume is spent. Measured that day on the live circuit: 35
        // failed tasks, 17 resumable in principle, 13 of those already past their only resume - so the gate
        // held thirteen elements its resolver could never remove, and after the remaining four were used
        // the state would have been unleavable for good, denying ORCHESTRATE, DISPATCH_QUEUED_TASKS and
        // DISPATCH_REVIEW_TASKS forever.
        // 2026-08-30 (model rule 8.6, third correction and the last one this predicate needs). "In
        // principle" and "has budget" are both computed from the task's own fields, while the resolver's
        // domain also depends on the task's SOURCE BRIEF and on why it failed - out-of-cycle generated work
        // it never resumes, and a failure reason outside RETIRED_WITH_NOTHING_LEFT_WORKING_IT it cannot
        // act on. Measured on the live circuit that day: four failed tasks held this gate, the resolver
        // refused all four durably, and the project sat in BLOCKED_BY_FAILED_FRONTIER - denying ORCHESTRATE
        // and both dispatch actions - with no path out at all.
        //
        // The predicate is not re-implemented here: PlannedWorkRecoveryService.isProductWorkThisMayResume
        // is the one definition, called with the brief this method already loaded (Charter invariant 10).
        // A task whose source brief is absent from that list is NOT excluded - nothing is established about
        // it, and 8.6's second line forbids removing what is merely unknown.
        java.util.Map<java.util.UUID, WishlistEntity> briefById = wishlist.stream()
                .collect(java.util.stream.Collectors.toMap(WishlistEntity::getId, item -> item, (a, b) -> a));
        long failed = countFailedTheResolverCanAct(tasks, briefById);
        long blocked = countStatus(tasks, TaskStatus.blocked);
        // Charter invariant 8: an element that can structurally never reach done leaves the denominator,
        // or any code deciding on this metric blocks silently. A brief whose budget is spent AND which
        // the compiler was actually reached for will never be dispatched again (F42) - measured
        // 2026-08-28, six such briefs held test-fiftieth in DECOMPOSING permanently, and DECOMPOSING
        // denies RECOVER_FAILED_FRONTIER while 27 tasks waited behind it. A brief the compiler was
        // never reached for is NOT excluded: nothing is established about it and it is restored
        // instead (ProjectFlowService.restoreUnreachedBriefs).
        long pendingWishlist = wishlist.stream()
                .filter(item -> item.getStatus() == WishlistStatus.pending)
                .filter(WishlistEntity::movable)
                .count();
        long refusedWishlist = wishlist.stream().filter(WishlistEntity::decompositionRefused).count();
        long compilingWishlist = wishlist.stream().filter(item -> item.getStatus() == WishlistStatus.compiling).count();
        long openSessions = sessions.stream().filter(session -> OPEN_SESSION_STATUSES.contains(normalize(session.getStatus()))).count();
        // Live-evidence scoping (2026-07-31): `tasks` above is the project's ENTIRE history
        // (findByProjectIdOrderByCreatedAtDesc, unbounded), by design - done/failed counts are meant to be
        // cumulative for progress reporting. But `reviews` cascades from every session of every one of
        // those tasks, including ones that reached a terminal status days ago, and openReviews/
        // failingReviews below feed live blocking decisions (BLOCKED_BY_REVIEW, MERGE_PR, DISPATCH_REVIEW_
        // TASKS), not reporting. Without this scope, a single task that ever failed with a terminal review
        // status (closed_unmerged/escalated/invalid_pr/unowned) inflates failingReviews FOREVER - nothing
        // in the codebase ever re-derives this count excluding it, so BLOCKED_BY_REVIEW becomes a one-way
        // ratchet toward permanently unresolvable as a project accumulates ordinary history. Confirmed live
        // (2026-07-31, test-fortieth): task 529e5252's long-since-terminal `failed` review kept the whole
        // project in BLOCKED_BY_REVIEW indefinitely, blocking dispatch/merge/orchestration for everything
        // else, with no code path that could ever clear it. A task whose own fate is already decided
        // (done/failed/spike_completed) cannot be live evidence of an in-progress review problem - only
        // sessions still attached to a non-terminal task represent work anyone could still act on. Sessions
        // with no resolvable task are kept (fail toward counting, not silently hiding a real problem).
        Set<TaskStatus> terminalTaskStatuses = Set.of(TaskStatus.done, TaskStatus.failed, TaskStatus.spike_completed);
        Set<UUID> terminalTaskIds = tasks.stream()
                .filter(task -> terminalTaskStatuses.contains(task.getStatus()))
                .map(TaskEntity::getId)
                .collect(Collectors.toSet());
        // "Still doing something" is asked of the session, in the one place that defines it (2026-08-29,
        // plan §4.29). It was asked here as "not literally cancelled", which counted every finished
        // session as live - measured that day, 1088 of 1130. PlannedWorkRecoveryService reuses the
        // original task identity when it revives a failed task, so the previous attempt's closed_unmerged
        // review kept the project in BLOCKED_BY_REVIEW a minute after the new attempt was already running.
        Set<UUID> liveSessionIds = sessions.stream()
                .filter(session -> session.getTaskId() == null || !terminalTaskIds.contains(session.getTaskId()))
                .filter(JulesSessionEntity::isActive)
                .map(JulesSessionEntity::getId)
                .collect(Collectors.toSet());
        // Model rule 8.20: a task in review needs ITS OWN artifact. The invariant below used to compare two
        // project-wide aggregates - "are there review tasks" against "are there open reviews" - which warns
        // on the ordinary case of a review task whose pull request has just merged, and stays silent on a
        // task that has no artifact at all. Different quantifiers, so the check could not answer the
        // question it was asked. Counted per task here, from the session that links the two.
        Set<UUID> sessionIdsCarryingAReview = reviews.stream()
                .map(PrReviewEntity::getJulesSessionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> taskIdsCarryingAReview = sessions.stream()
                .filter(session -> sessionIdsCarryingAReview.contains(session.getId()))
                .map(JulesSessionEntity::getTaskId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        long reviewTasksWithoutArtifact = tasks.stream()
                .filter(task -> Set.of(TaskStatus.pending_review, TaskStatus.review).contains(task.getStatus()))
                .filter(task -> !taskIdsCarryingAReview.contains(task.getId()))
                .count();
        int mergedReviews = (int) reviews.stream().filter(reviewEntity -> Boolean.TRUE.equals(reviewEntity.getMerged())).count();
        int openReviews = (int) reviews.stream()
                .filter(reviewEntity -> !Boolean.TRUE.equals(reviewEntity.getMerged()))
                .filter(reviewEntity -> liveSessionIds.contains(reviewEntity.getJulesSessionId()))
                .count();
        // A factory carrier's pull request is a process record, never product code - the compiler's own path
        // says so in its own words, merging a good plan through mergeRecordPullRequest and closing the
        // session with closeSessionAsNoCode, "process/metadata only by design". So a carrier PR closed
        // without a merge is an outcome of the process, not a failing review OF THE PRODUCT, and it carries
        // no evidence that the product is unwell.
        //
        // Measured 2026-08-29: of 108 reviews with a failing status, 107 had no session at all and were
        // already filtered out here; the single one that counted was PR#416 of compiler task ad7879a8,
        // "Compile 6 wishlist(s) into tasks". Its plan had been rejected and its PR closed, which put the
        // project into BLOCKED_BY_REVIEW, which denies ORCHESTRATE, which is what dispatches compilation -
        // so the compiler's own failure locked the only mechanism that could retry it, and the factory made
        // no dispatch of any kind for seven ticks running.
        //
        // Charter invariant 11: a blocking condition must justify WHAT it protects and may not be wider
        // than that. Product reviews still count exactly as before, failing ones included.
        Set<UUID> carrierTaskIds = tasks.stream()
                .filter(FlowSpineService::isFactoryCarrierTask)
                .map(TaskEntity::getId)
                .collect(Collectors.toSet());
        Set<UUID> carrierSessionIds = sessions.stream()
                .filter(session -> session.getTaskId() != null && carrierTaskIds.contains(session.getTaskId()))
                .map(JulesSessionEntity::getId)
                .collect(Collectors.toSet());
        // Model rule 8.11 O8: a hold leaves a readable record of its REASON. This state said "5
        // failing/conflicted review(s) exist" and nothing else, while the kinds it lumps together have
        // entirely different resolvers - `conflict` has a bounded resolution path, `escalated` and
        // `closed_unmerged` are terminal and, on a session that stays live, would be counted forever.
        // Measured 2026-09-02: this bottleneck had been breached for 6841 minutes, four and three quarter
        // days, and the number alone could not say which of those it was.
        java.util.Map<String, Long> failingByStatus = reviews.stream()
                .filter(reviewEntity -> FAILING_REVIEW_STATUSES.contains(normalize(reviewEntity.getCiStatus())))
                .filter(reviewEntity -> !UNRESOLVABLE_REVIEW_STATUSES.contains(normalize(reviewEntity.getCiStatus())))
                .filter(reviewEntity -> liveSessionIds.contains(reviewEntity.getJulesSessionId()))
                .filter(reviewEntity -> !carrierSessionIds.contains(reviewEntity.getJulesSessionId()))
                .collect(Collectors.groupingBy(reviewEntity -> normalize(reviewEntity.getCiStatus()),
                        java.util.TreeMap::new, Collectors.counting()));
        int failingReviews = (int) failingByStatus.values().stream().mapToLong(Long::longValue).sum();
        String failingReviewComposition = failingByStatus.isEmpty() ? "" : failingByStatus.toString();
        // 2026-08-22: these counted a boolean that answers two different questions. GateOrchestrator
        // writes the same flag from runTaskSpecGate at task CREATION and from runQualityGate when an
        // implementer FINISHES, and measured on test-forty-ninth every task in the project carries a gate
        // log written 2-5 seconds after creation. So "72 passed" was 72 well-specified tasks, not 72
        // verified deliveries, and "114 failed" mixed real failures with tasks nothing had ever asked -
        // a primitive boolean defaulting to false has no place to put "not measured", which is O-10 at
        // the level of acceptance. The subject was already in the report's `stages`; this is the reader
        // that asks it.
        int qualityGatePassed = (int) tasks.stream().filter(TaskEntity::isVerifiedForDelivery).count();
        // Refuted deliveries are excluded here and reported separately below (2026-08-28). Without the
        // exclusion the first run of the union predicate printed failed=45 REFUTED=45 - one fact counted
        // twice, which is how a reader concludes there are ninety problems where there are forty-five.
        int qualityGateFailed = (int) tasks.stream()
                .filter(task -> !task.isDeliveryVerificationAbsent())
                .filter(task -> !task.isVerifiedForDelivery())
                .filter(task -> !task.deliveryRefuted())
                .count();
        int deliveryVerificationAbsent = (int) tasks.stream()
                .filter(TaskEntity::isDeliveryVerificationAbsent)
                .count();
        // Refuted is reported as its own number, not folded into "failed" (2026-08-28). A task whose
        // merged diff was judged NOT to satisfy its own acceptance criteria is a different fact from a
        // task that failed a mechanical gate, and it was invisible until now: measured 45 of 365 on
        // test-fiftieth, every one of them sitting in status `done`. DeliveredWorkJudgmentService files
        // scope for each rather than rewriting history, which is correct - but a correction nobody counts
        // is a correction nobody can act on.
        int deliveryRefuted = (int) tasks.stream().filter(TaskEntity::deliveryRefuted).count();
        // "Never asked" and "asked, no ruling" are two different defects and were reported as one number.
        // Nobody asking means no witness ran (rule 8.15); a verdict recorded without a ruling means the
        // witness ran and could not decide, which points at the criteria, not at the coverage. Both leave
        // delivery unverified, so both stay inside `deliveryVerificationAbsent` - but naming them together
        // as "never asked" states something false about whichever of the two it is (rule 8.11 O8), and it
        // did: 385 of 665 read as a coverage gap while an unknown share of them had been asked already.
        int deliveryQuestionUnsettled = (int) tasks.stream()
                .filter(TaskEntity::deliveryQuestionPutButUnsettled)
                .count();
        // And WHY each of them came back without a ruling. The judgment records its ground, and the three
        // it can record are three different defects needing three different repairs: criteria that no
        // delivery could falsify (the claim was substituted before dispatch), an input the channel could
        // not carry, and an answer that did not come in the declared form. Reported as one number they are
        // a mystery; named, each points at its own place. Verificationism, applied to the factory's own
        // record: a count whose meaning has no method of settling it says nothing.
        java.util.Map<String, Long> unsettledGrounds = tasks.stream()
                .filter(TaskEntity::deliveryQuestionPutButUnsettled)
                .collect(java.util.stream.Collectors.groupingBy(
                        FlowSpineService::groundOfUnsettledJudgment,
                        java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        // And of those never asked, how many are simply not finished. A task that has not closed has
        // nothing to be asked about yet, and counting it beside work that closed unasked states a coverage
        // gap where there is none - the same misreading "never asked" already caused once (rule 8.11 O8).
        // Measured 04.09: of 118 never asked, 112 had not closed.
        int neverAsked = deliveryVerificationAbsent - deliveryQuestionUnsettled;
        int neverAskedAndClosed = countNeverAskedAndClosed(tasks);
        log.info("FlowSpine: delivery verification - passed={} failed={} REFUTED={} unverified={} "
                        + "(of which asked-without-ruling={} {}, never asked={} [closed and unasked={}, "
                        + "not finished yet={}]) of {} tasks",
                qualityGatePassed, qualityGateFailed, deliveryRefuted, deliveryVerificationAbsent,
                deliveryQuestionUnsettled, unsettledGrounds, neverAsked, neverAskedAndClosed,
                neverAsked - neverAskedAndClosed, tasks.size());
        // The count of briefs the compiler answered with nothing travels in StateInputs below and reaches
        // the dashboard through the model, which is where F39 wanted it retrievable. It used to be written
        // here as a warning as well, on every build of the model - measured 2026-08-29, eleven identical
        // lines in thirty minutes about a fact that cannot change - and it ended by asking for a human
        // reading, which is a branch this factory no longer has (operator, same day).

        return new StateInputs(
                projectStatus, queued, active, review, done, failed, blocked,
                pendingWishlist, compilingWishlist, openSessions, mergedReviews, openReviews,
                reviewTasksWithoutArtifact, failingReviewComposition, failingReviews,
                qualityGatePassed, qualityGateFailed, readiness.totalFeatures(), readiness.completeFeatures(),
                readiness.totalDeliverables(), readiness.mergedDeliverables(), readiness.decompositionComplete(),
                systemStatus, duplicateContent);
    }

    private FlowSpineDto.Transition nextTransition(String state, StateInputs input) {
        return switch (state) {
            case "FROZEN" -> transition(state, "ACTIVE", "ProjectFlowService.activateProject",
                    "Activate this canary project; existing activate semantics may freeze other active projects.",
                    List.of("project.status becomes active"), "Frozen projects do not move through autonomous flow.");
            case "PROJECT_NOT_ACTIVE" -> transition(state, "ACTIVE", "ProjectFlowService.activateProject",
                    "Move project into active state when its intake/onboarding preconditions are satisfied.",
                    List.of("project.status=active"), "Only active projects are admitted to continuous orchestration.");
            case "BLOCKED_BY_DUPLICATE_CONTENT" -> transition(state, "DECOMPOSING", "TechnicalLeadCompiler / ProjectFlowService",
                    "Collapse or dismiss duplicate generated work before admitting more work.",
                    List.of("duplicate recent task content below threshold", "no semantic duplicate live set"),
                    "Duplicate content is negative evidence for value flow.");
            case "GITHUB_RATE_LIMITED" -> transition(state, "UNDER_REVIEW_OR_WAITING", "GitHubApiBudgetService / AutoMergeService",
                    "Pause GitHub-dependent orchestration until rate-limit reset; do not infer PR truth from unavailable GitHub state.",
                    List.of("githubApiBudget.available=true", "cooldownUntil elapsed or resetAt elapsed"),
                    "GitHub state is unavailable, so PR, CI, and merge truth cannot be trusted.");
            case "SYSTEM_STALLED" -> transition(state, "QUEUED_OR_IMPLEMENTING", "ContinuousOrchestrationService",
                    "Restore forward progress or prove there is no actionable work.",
                    List.of("lastProgressAt advances", "or system_stall_status=idle_no_actionable_work"),
                    "A stalled system cannot be trusted to advance value.");
            case "BLOCKED_BY_TASK" -> transition(state, "QUEUED", "ProjectFlowService.recoverBlockedWork",
                    "Recover or terminalize blocked tasks using existing bounded recovery.",
                    List.of("blocked task has explicit recovery/terminal evidence"), "Blocked tasks stop the local flow.");
            case "BLOCKED_BY_REVIEW" -> transition(state, "UNDER_REVIEW", "AutoMergeService / Gemini review",
                    "Repair failing/conflicted review evidence or close it as non-delivery.",
                    List.of("CI/review status becomes mergeable", "or review becomes closed_unmerged"),
                    "Failing PR evidence is not merge evidence.");
            case "BLOCKED_BY_FAILED_FRONTIER" -> transition(state, "QUEUED", "PlannedWorkRecoveryService.resumeNextFrontier",
                    "Resume a bounded failed frontier item without creating duplicate wishlist identity.",
                    List.of("failed task compare-and-set to queued", "resume count within limit"),
                    "There is historical failed work and no live work.");
            case "DECOMPOSING" -> transition(state, "QUEUED", "TechnicalLeadCompiler",
                    "Compile pending wishlist scope into typed, role-owned tasks.",
                    List.of("wishlist status converted_to_task", "created task graph has source_wishlist_id"),
                    "Wishlist intent is not executable until compiled.");
            case "QUEUED" -> transition(state, "IMPLEMENTING", "JulesDispatchService",
                    "Dispatch the highest-priority conflict-free queued task.",
                    List.of("task claimed", "Jules session created", "file-scope/dependency checks pass"),
                    "Queued work needs agent execution.");
            case "IMPLEMENTING" -> transition(state, "UNDER_REVIEW", "Jules agent",
                    "Produce a PR or terminal failure evidence.",
                    List.of("PR URL recorded", "task moves to review/pending_review"),
                    "Implementation activity is weak evidence until a reviewable artifact exists.");
            case "UNDER_REVIEW" -> transition(state, "VERIFYING_DELIVERY", "AutoMergeService / GitHub checks",
                    "Merge approved code or record a terminal non-delivery review.",
                    List.of("merged PR evidence", "CI success", "review decision accepted"),
                    "Review is the gate between activity and delivery evidence.");
            case "VERIFYING_DELIVERY" -> transition(state, "DELIVERED", "ClientDeliverableReadinessService",
                    "Reconcile merged PRs against planned deliverables and feature readiness.",
                    List.of("completeFeatures equals totalFeatures", "mergedDeliverables equals totalDeliverables"),
                    "Merged work must map back to client-facing planned value.");
            case "DELIVERED" -> transition(state, "ACCEPTED", "ProjectFlowService.acceptProject",
                    "Accept the project only when client-facing evidence is complete.",
                    List.of("delivery readiness complete", "no high-severity blockers"),
                    "Acceptance stops new production work.");
            case "NO_SCOPE" -> transition(state, "DECOMPOSING", "ProjectFlowService.addWishlistItem",
                    "Add a concrete client wishlist item before orchestration.",
                    List.of("client wishlist exists", "wishlist.status=pending"),
                    "A project without scope cannot produce value.");
            case "IDLE_NO_ACTIONABLE_WORK" -> transition(state, "NO_SCOPE_OR_ACCEPTED", "Operator / ProjectFlowService",
                    "Either add new scope or accept the delivered state if evidence is complete.",
                    List.of("new wishlist", "or accepted project decision"),
                    "Idle is only healthy when intentionally scoped.");
            default -> transition(state, state, "none", "No automated transition is allowed from this state.",
                    List.of("operator decision"), "Terminal or unsupported state.");
        };
    }

    private String blockingReason(String state, StateInputs input) {
        if (!isBlockingState(state)) {
            return "";
        }
        return switch (state) {
            case "FROZEN" -> "Project status is frozen; continuous orchestration ignores it.";
            case "PROJECT_NOT_ACTIVE" -> "Project is not in active state.";
            case "BLOCKED_BY_DUPLICATE_CONTENT" -> "Local duplicate-content threshold is active.";
            case "GITHUB_RATE_LIMITED" -> "GitHub API budget is exhausted; GitHub-dependent PR truth is unavailable.";
            case "SYSTEM_STALLED" -> "System status is stalled.";
            case "BLOCKED_BY_TASK" -> input.blockedTasks() + " blocked task(s) exist.";
            case "BLOCKED_BY_REVIEW" -> input.failingReviews() + " failing/conflicted review(s) exist"
                    + (input.failingReviewComposition() == null || input.failingReviewComposition().isBlank()
                            ? "." : " " + input.failingReviewComposition() + ".");
            case "BLOCKED_BY_FAILED_FRONTIER" -> input.failedTasks() + " failed task(s) exist and no live work is moving.";
            case "ARCHIVED" -> "Archived projects are terminal.";
            default -> "Flow is blocked by " + state + ".";
        };
    }

    private FlowSpineDto.EvidenceVector evidence(StateInputs input) {
        return new FlowSpineDto.EvidenceVector(
                input.mergedReviews(),
                input.openReviews(),
                input.failingReviews(),
                input.qualityGatePassed(),
                input.qualityGateFailed(),
                input.pendingWishlist(),
                input.compilingWishlist(),
                input.openSessions(),
                input.systemStatus(),
                input.duplicateContentDetected()
        );
    }

    private FlowSpineDto.FlowCounts counts(StateInputs input) {
        return new FlowSpineDto.FlowCounts(
                input.queuedTasks(),
                input.activeTasks(),
                input.reviewTasks(),
                input.doneTasks(),
                input.failedTasks(),
                input.blockedTasks(),
                input.totalFeatures(),
                input.completeFeatures(),
                input.totalDeliverables(),
                input.mergedDeliverables(),
                input.decompositionComplete()
        );
    }

    private List<FlowSpineDto.FlowInvariant> invariants(StateInputs input) {
        return List.of(
                invariant("single_current_state", "pass",
                        "Every project maps to exactly one flow state.",
                        "decideState returns one deterministic precedence winner."),
                invariant("done_is_not_delivery", input.doneTasks() > input.mergedDeliverables() ? "warn" : "pass",
                        "done(task) is not equivalent to delivered(value).",
                        input.doneTasks() + " done-like task(s), " + input.mergedDeliverables() + " merged deliverable(s)."),
                invariant("frozen_has_no_autonomous_flow", input.projectStatus() == ProjectStatus.frozen ? "observed" : "pass",
                        "frozen(project) forbids autonomous orchestration.",
                        "project.status=" + input.projectStatus()),
                invariant("review_requires_artifact", input.reviewTasksWithoutArtifact() > 0 ? "warn" : "pass",
                        "review(state) requires PR/review artifact evidence.",
                        input.reviewTasksWithoutArtifact() + " of " + input.reviewTasks()
                                + " review task(s) carry no review artifact of their own."),
                invariant("delivery_requires_full_mapping", input.totalFeatures() > 0
                                && input.completeFeatures() >= input.totalFeatures()
                                && input.mergedDeliverables() < input.totalDeliverables() ? "warn" : "pass",
                        "delivered(value) requires complete feature and deliverable evidence.",
                        input.completeFeatures() + "/" + input.totalFeatures() + " features, "
                                + input.mergedDeliverables() + "/" + input.totalDeliverables() + " deliverables.")
        );
    }

    private List<FlowSpineDto.ForbiddenTransition> forbiddenTransitions() {
        return List.of(
                forbidden("IMPLEMENTING", "DELIVERED", "Implementation activity is not delivery evidence."),
                forbidden("QUEUED", "DELIVERED", "Queued work has no artifact, review, merge, or readiness evidence."),
                forbidden("UNDER_REVIEW", "DELIVERED", "Review must first produce merge/readiness evidence."),
                forbidden("BLOCKED_BY_REVIEW", "MERGED", "Failing/conflicted PR evidence cannot be promoted."),
                forbidden("BLOCKED_BY_DUPLICATE_CONTENT", "QUEUED", "Duplicate generated work must be collapsed before more dispatch."),
                forbidden("GITHUB_RATE_LIMITED", "MERGED", "Unavailable GitHub state cannot prove merge readiness."),
                forbidden("GITHUB_RATE_LIMITED", "CLOSED_UNMERGED", "Unavailable GitHub state cannot prove terminal PR state."),
                forbidden("FROZEN", "IMPLEMENTING", "Frozen projects must be explicitly activated before autonomous work.")
        );
    }

    private List<FlowSpineDto.Bottleneck> bottlenecks(String state,
                                                      StateInputs input,
                                                      FlowSpineDto.Transition next,
                                                      long ageMinutes,
                                                      String blockingReason) {
        SlaSpec sla = slaForState(state);
        boolean breached = sla.minutes() >= 0 && ageMinutes >= sla.minutes();
        boolean activeBottleneck = isBlockingState(state) || breached;
        if (!activeBottleneck) {
            return List.of();
        }
        String type = bottleneckType(state, input.systemStatus());
        if (type.isBlank()) {
            return List.of();
        }
        String reason = blockingReason == null || blockingReason.isBlank()
                ? "State `" + state + "` exceeded its operational SLA."
                : blockingReason;
        return List.of(new FlowSpineDto.Bottleneck(
                type,
                severity(sla.severity(), breached),
                state,
                ageMinutes,
                sla.minutes(),
                slaStatus(sla, ageMinutes),
                next.owner(),
                reason,
                next.action()
        ));
    }

    private String severity(String base, boolean breached) {
        if (!breached || "critical".equals(base) || "high".equals(base)) {
            return base;
        }
        return "high";
    }

    private String slaStatus(SlaSpec sla, long ageMinutes) {
        if (sla.minutes() < 0) {
            return "no_sla";
        }
        return ageMinutes >= sla.minutes() ? "breached" : "within_sla";
    }

    private long ageInStateMinutes(String state,
                                   ProjectEntity project,
                                   List<TaskEntity> tasks,
                                   List<WishlistEntity> wishlist,
                                   List<JulesSessionEntity> sessions,
                                   List<PrReviewEntity> reviews) {
        Instant since = stateObservedSince(state, project, tasks, wishlist, sessions, reviews);
        return since == null ? 0 : Math.max(0, Duration.between(since, Instant.now()).toMinutes());
    }

    private Instant stateObservedSince(String state,
                                       ProjectEntity project,
                                       List<TaskEntity> tasks,
                                       List<WishlistEntity> wishlist,
                                       List<JulesSessionEntity> sessions,
                                       List<PrReviewEntity> reviews) {
        return switch (state) {
            case "QUEUED" -> earliestTaskTime(tasks, Set.of(TaskStatus.queued));
            case "IMPLEMENTING" -> earliest(earliestTaskTime(tasks, Set.of(TaskStatus.claimed, TaskStatus.in_progress)),
                    earliestSessionTime(sessions, OPEN_SESSION_STATUSES));
            case "UNDER_REVIEW", "BLOCKED_BY_REVIEW" -> earliest(earliestTaskTime(tasks, Set.of(TaskStatus.pending_review, TaskStatus.review)),
                    earliestReviewTime(reviews));
            case "DECOMPOSING" -> earliestWishlistTime(wishlist, Set.of(WishlistStatus.pending, WishlistStatus.compiling));
            case "BLOCKED_BY_TASK" -> earliestTaskTime(tasks, Set.of(TaskStatus.blocked));
            case "BLOCKED_BY_FAILED_FRONTIER" -> earliestTaskTime(tasks, Set.of(TaskStatus.failed));
            default -> project.getAcceptedAt() != null ? project.getAcceptedAt() : project.getCreatedAt();
        };
    }

    private Instant earliestTaskTime(List<TaskEntity> tasks, Set<TaskStatus> statuses) {
        return tasks.stream()
                .filter(task -> statuses.contains(task.getStatus()))
                .map(task -> firstNonNull(task.getUpdatedAt(), task.getCreatedAt()))
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant earliestWishlistTime(List<WishlistEntity> wishlist, Set<WishlistStatus> statuses) {
        return wishlist.stream()
                .filter(item -> statuses.contains(item.getStatus()))
                .map(WishlistEntity::getCreatedAt)
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant earliestSessionTime(List<JulesSessionEntity> sessions, Set<String> statuses) {
        return sessions.stream()
                .filter(session -> statuses.contains(normalize(session.getStatus())))
                .map(session -> firstNonNull(session.getLastProgressAt(), session.getUpdatedAt(), session.getCreatedAt()))
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant earliestReviewTime(List<PrReviewEntity> reviews) {
        return reviews.stream()
                .filter(review -> !Boolean.TRUE.equals(review.getMerged()))
                .map(PrReviewEntity::getCreatedAt)
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant earliest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private Instant firstNonNull(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String evidenceSummary(FlowSpineDto.EvidenceVector evidence,
                                   FlowSpineDto.FlowCounts counts,
                                   String blockingReason) {
        return "counts=" + counts
                + "; evidence=" + evidence
                + "; blockingReason=" + (blockingReason == null ? "" : blockingReason);
    }

    private String evidenceHash(String currentState, String valueStatus, String nextState, String evidenceSummary) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((currentState + "|" + valueStatus + "|" + nextState + "|" + evidenceSummary)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash Flow Spine evidence", e);
        }
    }

    private FlowSpineDto.JournalSummary journalSummary(FlowSpineEventEntity latest,
                                                       String currentState,
                                                       String evidenceHash,
                                                       long eventCount) {
        if (latest == null) {
            return new FlowSpineDto.JournalSummary(null, null, null, null, evidenceHash, false, eventCount);
        }
        boolean recorded = currentState.equals(latest.getCurrentState()) && evidenceHash.equals(latest.getEvidenceHash());
        return new FlowSpineDto.JournalSummary(
                latest.getId(),
                latest.getObservedAt(),
                latest.getPreviousState(),
                latest.getCurrentState(),
                evidenceHash,
                recorded,
                eventCount
        );
    }

    private FlowSpineDto.FlowEvent toEventDto(FlowSpineEventEntity event) {
        return new FlowSpineDto.FlowEvent(
                event.getId(),
                event.getCycleId(),
                event.getObservedAt(),
                event.getPreviousState(),
                event.getCurrentState(),
                event.getNextState(),
                event.getValueStatus(),
                event.getBottleneckType(),
                event.getBottleneckSeverity(),
                event.getAgeInStateMinutes(),
                event.getOwner(),
                event.getTransitionAction(),
                event.getEvidenceHash(),
                event.getBlockingReason()
        );
    }

    private static FlowSpineDto.TransitionMatrixEntry matrix(int priority,
                                                             String from,
                                                             String condition,
                                                             String to,
                                                             String owner,
                                                             List<String> evidenceRequired,
                                                             String promotionMode) {
        return new FlowSpineDto.TransitionMatrixEntry(
                priority, from, condition, to, owner, evidenceRequired, promotionMode);
    }

    private FlowSpineDto.FlowInvariant invariant(String key, String status, String statement, String evidence) {
        return new FlowSpineDto.FlowInvariant(key, status, statement, evidence);
    }

    private FlowSpineDto.ForbiddenTransition forbidden(String from, String to, String reason) {
        return new FlowSpineDto.ForbiddenTransition(from, to, reason);
    }

    private FlowSpineDto.Transition transition(String from,
                                               String to,
                                               String owner,
                                               String action,
                                               List<String> evidenceRequired,
                                               String reason) {
        return new FlowSpineDto.Transition(from, to, owner, action, evidenceRequired, reason);
    }

    private List<JulesSessionEntity> sessionsForTasks(List<TaskEntity> tasks) {
        List<UUID> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        return taskIds.isEmpty() ? List.of() : julesSessionRepository.findByTaskIdIn(taskIds);
    }

    private List<PrReviewEntity> reviewsForSessions(List<JulesSessionEntity> sessions) {
        List<UUID> sessionIds = sessions.stream().map(JulesSessionEntity::getId).toList();
        // Pushed into the query (2026-08-28): the filter below WAS the question, so asking for exactly
        // these sessions costs the answer instead of every review ever written.
        return sessionIds.isEmpty() ? List.of() : prReviewRepository.findByJulesSessionIdIn(sessionIds);
    }

    // 2026-08-04 (live incident: test-forty-first stuck for hours in BLOCKED_BY_DUPLICATE_CONTENT with no
    // recovery path): this hard-stop gate exists to catch an ACTIVE generation fallback churning out
    // repeated fabricated content, not to permanently flag work that has already been resolved. Without
    // excluding terminal tasks, once 3+ historical (fully resolved) duplicates existed in the last-30-tasks
    // window, the block could never clear on its own. Mirrors the same terminal-status exclusion applied to
    // ContinuousOrchestrationService's own duplicate-content check.
    private static final Set<TaskStatus> DUPLICATE_CONTENT_TERMINAL_STATUSES = Set.of(
            TaskStatus.done, TaskStatus.failed, TaskStatus.blocked, TaskStatus.spike_completed);

    private boolean duplicateContent(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .limit(30)
                .filter(task -> !DUPLICATE_CONTENT_TERMINAL_STATUSES.contains(task.getStatus()))
                .filter(task -> !isDeliberateRecoveryTask(task))
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));
        return counts.values().stream().anyMatch(count -> count >= 3);
    }

    // 2026-08-08 (ML-update patch, Phase 2 / lever D3_EMBEDDING_DUPLICATE_DETECTION): incumbent above is
    // exact slice_title/description match only - live incident 2026-08-07 found ~half of one decomposition's
    // 41 tasks were 2x near-duplicates ACROSS different titles, invisible to exact match until 3 literal
    // collisions happened (Hacking's point on historically constructed measurement categories, BARCAN-TAG-04
    // philosopher 5: exact-title-match is itself an arbitrary category, not the real "same work" relation).
    // Deliberately runs on its OWN schedule, decoupled from duplicateContent()'s hot call path
    // (OperationalPolicyService.authorize -> OperationalFlowCoreService.build -> FlowSpineService.build runs
    // on every dispatch decision) - a real network embed() call inside that path would reproduce exactly the
    // "lock/coupling held across a network call" mistake fixed elsewhere tonight (engineering invariant #11),
    // just for embeddings instead of a DB transaction. Bounded to MAX_CANDIDATES_PER_PROJECT tasks per active
    // project per tick so worst-case embed-call volume stays predictable.
    static final String D3_LEVER_KEY = "D3_EMBEDDING_DUPLICATE_DETECTION";
    private static final double SIMILARITY_DETECTION_THRESHOLD = 0.90;
    // Stricter self-contained confirmation bar used ONLY as this lever's ground-truth proxy (real evidence,
    // just interpreted at a higher confidence level) - honestly documented limitation: without a wired
    // rejection signal (e.g. Gemini/operator declining to collapseDuplicateTask a flagged pair), this lever
    // can currently only accumulate TRUE/NEITHER observations, never FALSE, so it can be promoted but not
    // yet demoted by this mechanism alone.
    private static final double SIMILARITY_CONFIRMATION_THRESHOLD = 0.95;
    private static final int MAX_CANDIDATES_PER_PROJECT = 15;

    @Scheduled(fixedRate = 900000, initialDelay = 300000)
    public void shadowCheckEmbeddingDuplicatesAcrossActiveProjects() {
        for (ProjectEntity project : projectRepository.findAll()) {
            if (project.getStatus() != ProjectStatus.active) {
                continue;
            }
            try {
                shadowCheckEmbeddingDuplicatesForProject(project.getId());
            } catch (Exception e) {
                log.warn("[D3-SHADOW] embedding duplicate shadow check failed for project {}: {}", project.getId(), e.getMessage());
            }
        }
    }

    private void shadowCheckEmbeddingDuplicatesForProject(UUID projectId) {
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        boolean incumbentDuplicate = duplicateContent(tasks);

        List<TaskEntity> candidates = tasks.stream()
                .limit(30)
                .filter(task -> !DUPLICATE_CONTENT_TERMINAL_STATUSES.contains(task.getStatus()))
                .filter(task -> !isDeliberateRecoveryTask(task))
                .toList();

        Map<String, Long> exactCounts = candidates.stream()
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));

        // Only tasks whose exact key is UNIQUE in this window are worth comparing pairwise - anything
        // already sharing an exact key is already visible to the incumbent, not new candidate evidence.
        List<String> uniqueKeyedTexts = candidates.stream()
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank() && exactCounts.getOrDefault(key, 0L) == 1)
                .distinct()
                .limit(MAX_CANDIDATES_PER_PROJECT)
                .toList();

        if (uniqueKeyedTexts.size() < 2) {
            return;
        }

        double bestSimilarity = 0.0;
        for (int i = 0; i < uniqueKeyedTexts.size(); i++) {
            float[] vectorI = mlPredictionServiceClient.embed(uniqueKeyedTexts.get(i));
            if (vectorI == null) {
                continue;
            }
            for (int j = i + 1; j < uniqueKeyedTexts.size(); j++) {
                float[] vectorJ = mlPredictionServiceClient.embed(uniqueKeyedTexts.get(j));
                if (vectorJ == null) {
                    continue;
                }
                bestSimilarity = Math.max(bestSimilarity, EmbeddingSimilarityUtil.cosineSimilarity(vectorI, vectorJ));
            }
        }

        if (bestSimilarity < SIMILARITY_DETECTION_THRESHOLD) {
            return;
        }

        String incumbentDecision = incumbentDuplicate ? "duplicate" : "not_duplicate";
        LeverAgreement agreement = bestSimilarity >= SIMILARITY_CONFIRMATION_THRESHOLD
                ? LeverAgreement.TRUE : LeverAgreement.NEITHER;
        leverPromotionService.recordObservation(D3_LEVER_KEY, projectId.toString(), incumbentDecision, "duplicate",
                agreement, agreement == LeverAgreement.TRUE ? "high_confidence_semantic_duplicate" : null);
        log.info("[D3-SHADOW] project {} - candidate found a semantic-duplicate pair (similarity={}) that exact-match {} catch",
                projectId, String.format("%.3f", bestSimilarity), incumbentDuplicate ? "ALSO did" : "did NOT");
    }

    // 2026-08-07 (same-morning follow-on incident, test-forty-third): OpsAuditorService.createTargetedRecoveryTask
    // deliberately deep-copies a dead task's ENTIRE payload verbatim - including slice_title - because
    // ClientDeliverableReadinessService.isDependencySatisfied only recognizes a replacement via an EXACT
    // match on role/featureId/ems_semantic_key, not by title. That is a single, deliberate, audited
    // replacement for one specific dead task, not evidence of an uncontrolled generation loop - counting it
    // toward this threshold retripped the exact same hard-stop the recovery mechanism exists to route
    // around, within the same orchestration cycle that created it.
    private static boolean isDeliberateRecoveryTask(TaskEntity task) {
        return task.getPayload() != null && task.getPayload().has("recoversFailedTaskId");
    }

    private String duplicateKey(TaskEntity task) {
        if (task.getPayload() != null) {
            String sliceTitle = task.getPayload().path("slice_title").asText("");
            if (!sliceTitle.isBlank()) {
                return sliceTitle;
            }
        }
        return task.getDescription();
    }

    private long countStatus(List<TaskEntity> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    private String systemStallStatus(Map<String, Object> systemStatus) {
        Object githubBudget = systemStatus.get("githubApiBudget");
        Object githubData = dataSection(githubBudget);
        if (githubData instanceof Map<?, ?> map) {
            Object available = map.get("available");
            Object status = map.get("status");
            if (Boolean.FALSE.equals(available) || "exhausted".equals(normalize(status == null ? "" : status.toString()))) {
                return "github_rate_limited";
            }
        }

        Object systemHealth = systemStatus.get("systemHealth");
        Object data = dataSection(systemHealth);
        if (data instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status == null ? "" : status.toString();
        }
        return "";
    }

    private Object dataSection(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record FlowModel(
            FlowSpineDto dto,
            String currentState,
            String valueStatus,
            FlowSpineDto.Transition nextTransition,
            List<FlowSpineDto.Bottleneck> bottlenecks,
            String blockingReason,
            String evidenceHash,
            String evidenceSummary,
            long ageInStateMinutes,
            FlowSpineEventEntity latestEvent
    ) {
    }

    record SlaSpec(long minutes, String severity) {
    }

    record StateInputs(
            ProjectStatus projectStatus,
            long queuedTasks,
            long activeTasks,
            long reviewTasks,
            long doneTasks,
            long failedTasks,
            long blockedTasks,
            long pendingWishlist,
            long compilingWishlist,
            long openSessions,
            int mergedReviews,
            int openReviews,
            /** Tasks in a review status that carry no review artifact of their own (model rule 8.20). */
            long reviewTasksWithoutArtifact,
            /** Which kinds of failing review are holding the state, by ciStatus (model rule 8.11 O8). */
            String failingReviewComposition,
            int failingReviews,
            int qualityGatePassed,
            int qualityGateFailed,
            int totalFeatures,
            int completeFeatures,
            int totalDeliverables,
            int mergedDeliverables,
            boolean decompositionComplete,
            String systemStatus,
            boolean duplicateContentDetected
    ) {
    }
    /**
     * Work that finished and was never asked whether it delivered - the part of "never asked" that names a
     * real gap.
     *
     * <p>A task that has not closed has nothing to be asked about yet. Counting it beside work that closed
     * unasked states a coverage gap where there is none, and that misreading has already been made once on
     * this number (rule 8.11 O8). Measured 04.09: of 118 never asked, 112 had simply not finished.
     */
    static int countNeverAskedAndClosed(java.util.List<TaskEntity> tasks) {
        return (int) tasks.stream()
                .filter(TaskEntity::isDeliveryVerificationAbsent)
                .filter(task -> !task.deliveryQuestionPutButUnsettled())
                .filter(task -> task.getStatus() == com.eneik.production.models.persistence.TaskStatus.done)
                .count();
    }

    /**
     * Failed work the gate's own resolver can actually act on (model rule 8.6, Charter invariant 8).
     *
     * <p>BLOCKED_BY_FAILED_FRONTIER names PlannedWorkRecoveryService as its resolver, so the gate asks that
     * service's own predicates rather than re-implementing them. Two things leave the denominator: work the
     * resolver never resumes or whose single resume is spent, and work waiting on a dependency of that same
     * kind - resumable on its own terms and still unable to run. Counting the second held the whole project
     * in a globally blocking state for 17103 minutes with no path out, and the attempt to fix it by writing
     * the task's status instead produced 128 blocks against 129 retirements of the same row.
     */
    static long countFailedTheResolverCanAct(java.util.List<TaskEntity> tasks,
            java.util.Map<java.util.UUID, WishlistEntity> briefById) {
        return tasks.stream()
                .filter(task -> com.eneik.production.services.PlannedWorkRecoveryService.isProductWorkThisMayResume(
                        task, task.getSourceWishlistId() == null ? null : briefById.get(task.getSourceWishlistId())))
                .filter(com.eneik.production.services.PlannedWorkRecoveryService::hasResumeBudgetLeft)
                .filter(task -> !com.eneik.production.services.PlannedWorkRecoveryService.isDeadForGood(
                        task.getDependsOn(),
                        task.getDependsOn() == null || task.getDependsOn().getSourceWishlistId() == null
                                ? null : briefById.get(task.getDependsOn().getSourceWishlistId())))
                .count();
    }

    /**
     * Which of the judgment's three recordable grounds this unsettled verdict carries.
     *
     * <p>Read from the ground the judgment itself wrote, not re-derived: the three are
     * "criteria no delivery can falsify" (the client's claim was substituted before dispatch),
     * "input the channel could not carry" (the diff does not fit the sidecar's limit), and
     * "answer not in the declared form". A ground that matches none of them is named `other` rather than
     * folded into one of the three - putting an unreadable row into a named bucket is how a count stops
     * answering (rule 8.11 O8).
     */
    static String groundOfUnsettledJudgment(TaskEntity task) {
        String ground = task.acceptanceVerdictReason();
        if (ground == null || ground.isBlank()) {
            return "no ground recorded";
        }
        if (ground.contains("no delivery can falsify")) {
            return "criteria nothing can falsify";
        }
        if (ground.contains("channel could not carry")) {
            return "input too large for the channel";
        }
        if (ground.contains("did not answer in the declared form")) {
            return "answer not in the declared form";
        }
        return "other";
    }

}
