package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.dashboard.SystemStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final PrReviewRepository prReviewRepository;
    private final ClientDeliverableReadinessService readinessService;
    private final SystemStatusService systemStatusService;

    public FlowSpineService(ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            WishlistRepository wishlistRepository,
                            JulesSessionRepository julesSessionRepository,
                            PrReviewRepository prReviewRepository,
                            ClientDeliverableReadinessService readinessService,
                            SystemStatusService systemStatusService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.prReviewRepository = prReviewRepository;
        this.readinessService = readinessService;
        this.systemStatusService = systemStatusService;
    }

    @Transactional(readOnly = true)
    public FlowSpineDto build(UUID projectId) {
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

        return new FlowSpineDto(
                Instant.now(),
                "observe_only",
                new FlowSpineDto.ProjectRef(
                        project.getId(),
                        project.getName(),
                        project.getStatus() == null ? "unknown" : project.getStatus().name(),
                        project.getRepositoryName()),
                currentState,
                valueStatus(currentState, inputs),
                blockingReason(currentState, inputs),
                next,
                List.of(next),
                forbiddenTransitions(),
                evidence(inputs),
                counts(inputs),
                invariants(inputs),
                "deterministic precedence: project terminality > local hard blockers > live WIP > review > evidence > idle"
        );
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
        if ("stalled".equals(normalize(input.systemStatus()))) {
            return "SYSTEM_STALLED";
        }
        if (input.blockedTasks() > 0) {
            return "BLOCKED_BY_TASK";
        }
        if (input.failingReviews() > 0) {
            return "BLOCKED_BY_REVIEW";
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
        if (input.pendingWishlist() > 0 || input.compilingWishlist() > 0 || !input.decompositionComplete()) {
            return "DECOMPOSING";
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
                || "PROJECT_NOT_ACTIVE".equals(state) || "ARCHIVED".equals(state);
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
        int mergedReviews = (int) reviews.stream().filter(reviewEntity -> Boolean.TRUE.equals(reviewEntity.getMerged())).count();
        int openReviews = (int) reviews.stream().filter(reviewEntity -> !Boolean.TRUE.equals(reviewEntity.getMerged())).count();
        int failingReviews = (int) reviews.stream()
                .filter(reviewEntity -> FAILING_REVIEW_STATUSES.contains(normalize(reviewEntity.getCiStatus())))
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
                forbidden("FROZEN", "IMPLEMENTING", "Frozen projects must be explicitly activated before autonomous work.")
        );
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

    private boolean duplicateContent(List<TaskEntity> tasks) {
        Map<String, Long> counts = tasks.stream()
                .limit(30)
                .map(this::duplicateKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), HashMap::new, Collectors.counting()));
        return counts.values().stream().anyMatch(count -> count >= 3);
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

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
