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
    // 2026-08-01 (live incident, test-fortieth/PR#119): a session Branch GC or JulesDispatchService.
    // cancelSession individually retires (status="cancelled") - because the task is being fast-tracked to a
    // FRESH re-dispatch, not because the task itself reached a terminal status - was still treated as "live"
    // by the terminal-task-only filter below, since the task goes straight back to `queued`, never
    // `done`/`failed`/`spike_completed`. Its review (e.g. a real "closed_unmerged" GitHub conflict) then kept
    // failingReviews > 0 and BLOCKED_BY_REVIEW permanently stuck, even though the task had already moved on
    // to a brand new attempt. Both retirement paths deliberately use "cancelled" for exactly this reason -
    // see BranchGarbageCollectorService.retireAbandonedBranchAndPR's Step 3.5 comment ("not in
    // ACTIVE_SESSION_STATUSES, so it's fully inert going forward") - the review-liveness filter just never
    // learned to read that signal.
    private static final String SUPERSEDED_SESSION_STATUS = "cancelled";

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
        long failed = countStatus(tasks, TaskStatus.failed);
        long blocked = countStatus(tasks, TaskStatus.blocked);
        long pendingWishlist = wishlist.stream().filter(item -> item.getStatus() == WishlistStatus.pending).count();
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
        Set<UUID> liveSessionIds = sessions.stream()
                .filter(session -> session.getTaskId() == null || !terminalTaskIds.contains(session.getTaskId()))
                .filter(session -> !SUPERSEDED_SESSION_STATUS.equals(session.getStatus()))
                .map(JulesSessionEntity::getId)
                .collect(Collectors.toSet());
        int mergedReviews = (int) reviews.stream().filter(reviewEntity -> Boolean.TRUE.equals(reviewEntity.getMerged())).count();
        int openReviews = (int) reviews.stream()
                .filter(reviewEntity -> !Boolean.TRUE.equals(reviewEntity.getMerged()))
                .filter(reviewEntity -> liveSessionIds.contains(reviewEntity.getJulesSessionId()))
                .count();
        int failingReviews = (int) reviews.stream()
                .filter(reviewEntity -> FAILING_REVIEW_STATUSES.contains(normalize(reviewEntity.getCiStatus())))
                .filter(reviewEntity -> liveSessionIds.contains(reviewEntity.getJulesSessionId()))
                .count();
        int qualityGatePassed = (int) tasks.stream().filter(TaskEntity::isQualityGatePassed).count();
        int qualityGateFailed = (int) tasks.stream()
                .filter(task -> task.getQualityGateReport() != null)
                .filter(task -> !task.isQualityGatePassed())
                .count();

        return new StateInputs(
                projectStatus, queued, active, review, done, failed, blocked,
                pendingWishlist, compilingWishlist, openSessions, mergedReviews, openReviews, failingReviews,
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
            case "BLOCKED_BY_REVIEW" -> input.failingReviews() + " failing/conflicted review(s) exist.";
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
                invariant("review_requires_artifact", input.reviewTasks() > 0 && input.openReviews() == 0 ? "warn" : "pass",
                        "review(state) requires PR/review artifact evidence.",
                        input.reviewTasks() + " review task(s), " + input.openReviews() + " open review artifact(s)."),
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
        return sessionIds.isEmpty() ? List.of() : prReviewRepository.findAll().stream()
                .filter(review -> sessionIds.contains(review.getJulesSessionId()))
                .toList();
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
}
