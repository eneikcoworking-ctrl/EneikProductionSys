package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.models.persistence.FlowSpineEventEntity;
import com.eneik.production.repositories.FlowSpineEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class OperationalFlowCoreService {
    static final String MODE = "flow_core_advisory";

    private final FlowSpineService flowSpineService;
    private final FlowSpineEventRepository flowSpineEventRepository;

    public OperationalFlowCoreService(FlowSpineService flowSpineService,
                                      FlowSpineEventRepository flowSpineEventRepository) {
        this.flowSpineService = flowSpineService;
        this.flowSpineEventRepository = flowSpineEventRepository;
    }

    @Transactional(readOnly = true)
    public FlowCoreDto build(UUID projectId) {
        return buildCore(projectId);
    }

    @Transactional
    public FlowCoreDto observe(UUID projectId) {
        FlowCoreDto core = buildCore(projectId);
        if (!core.journal().currentDecisionRecorded()) {
            saveDecisionEvent(core);
        }
        return buildCore(projectId);
    }

    @Transactional(readOnly = true)
    public List<FlowCoreDto.DecisionEvent> events(UUID projectId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        return flowSpineEventRepository
                .findByProjectIdAndModeOrderByObservedAtDesc(projectId, MODE, PageRequest.of(0, bounded))
                .stream()
                .map(this::toDecisionEvent)
                .toList();
    }

    private FlowCoreDto buildCore(UUID projectId) {
        FlowSpineDto snapshot = flowSpineService.build(projectId);
        FlowCoreDto.Decision decision = decide(snapshot);
        FlowCoreDto.Authorization authorization = authorization(snapshot, decision);
        FlowSpineEventEntity latestDecision = flowSpineEventRepository
                .findTop1ByProjectIdAndModeOrderByObservedAtDesc(projectId, MODE)
                .orElse(null);
        long eventCount = flowSpineEventRepository.countByProjectIdAndMode(projectId, MODE);
        return new FlowCoreDto(
                Instant.now(),
                MODE,
                snapshot.project(),
                snapshot,
                decision,
                authorization,
                mathematicalContract(),
                journal(latestDecision, decision, eventCount)
        );
    }

    private void saveDecisionEvent(FlowCoreDto core) {
        FlowSpineDto snapshot = core.snapshot();
        FlowCoreDto.Decision decision = core.decision();
        FlowCoreDto.Authorization authorization = core.authorization();
        FlowSpineEventEntity latestAny = flowSpineEventRepository
                .findTop1ByProjectIdOrderByObservedAtDesc(snapshot.project().id())
                .orElse(null);
        FlowSpineDto.Bottleneck primary = snapshot.bottlenecks().isEmpty() ? null : snapshot.bottlenecks().get(0);

        FlowSpineEventEntity event = new FlowSpineEventEntity();
        event.setProjectId(snapshot.project().id());
        event.setCycleId(UUID.randomUUID());
        event.setObservedAt(Instant.now());
        event.setPreviousState(latestAny == null ? null : latestAny.getCurrentState());
        event.setCurrentState(snapshot.currentState());
        event.setNextState(snapshot.nextRequiredTransition().to());
        event.setValueStatus(snapshot.valueStatus());
        event.setBottleneckType(primary == null ? null : primary.type());
        event.setBottleneckSeverity(primary == null ? null : primary.severity());
        event.setAgeInStateMinutes(primary == null ? 0 : primary.ageMinutes());
        event.setOwner(decision.owner());
        event.setTransitionAction(decision.action());
        event.setEvidenceHash(decision.evidenceHash());
        event.setEvidenceSummary(evidenceSummary(core));
        event.setBlockingReason(blankToNull(snapshot.blockingReason()));
        event.setMode(MODE);
        event.setDecisionHash(decision.decisionHash());
        event.setDecisionActionKey(decision.actionKey());
        event.setDecisionStatus(decision.status());
        event.setAuthorizationStatus(authorization.status());
        event.setRiskLevel(decision.riskLevel());
        event.setDecisionReason(decision.rationale());
        event.setDecisionPreconditions(join(decision.preconditions()));
        event.setExpectedOutcomes(join(decision.expectedOutcomes()));
        event.setForbiddenActions(join(decision.forbiddenActions()));
        flowSpineEventRepository.save(event);
    }

    static FlowCoreDto.Decision decide(FlowSpineDto snapshot) {
        FlowSpineDto.Transition transition = snapshot.nextRequiredTransition();
        ActionSpec spec = actionSpec(snapshot.currentState());
        String evidenceHash = evidenceHash(snapshot);
        String status = decisionStatus(snapshot.currentState());
        String riskLevel = riskLevel(snapshot);
        List<String> preconditions = stableUnion(transition.evidenceRequired(), spec.preconditions());
        String rationale = rationale(snapshot, transition);
        String decisionHash = decisionHash(
                snapshot.currentState(),
                transition.to(),
                spec.actionKey(),
                status,
                riskLevel,
                evidenceHash,
                preconditions,
                spec.expectedOutcomes(),
                spec.forbiddenActions()
        );
        return new FlowCoreDto.Decision(
                spec.actionKey(),
                status,
                transition.owner(),
                transition.action(),
                rationale,
                preconditions,
                spec.expectedOutcomes(),
                spec.forbiddenActions(),
                riskLevel,
                confidence(snapshot),
                evidenceHash,
                decisionHash
        );
    }

    static FlowCoreDto.Authorization authorization(FlowSpineDto snapshot, FlowCoreDto.Decision decision) {
        return new FlowCoreDto.Authorization(
                "ADVISORY_ONLY_AUTHORIZED",
                MODE,
                true,
                false,
                false,
                false,
                "Flow Core may append its deterministic decision to the journal only; project mutation, agent dispatch, and merge are disabled."
        );
    }

    static String decisionHash(FlowSpineDto snapshot, FlowCoreDto.Decision decision) {
        return decisionHash(
                snapshot.currentState(),
                snapshot.nextRequiredTransition().to(),
                decision.actionKey(),
                decision.status(),
                decision.riskLevel(),
                decision.evidenceHash(),
                decision.preconditions(),
                decision.expectedOutcomes(),
                decision.forbiddenActions()
        );
    }

    private static FlowCoreDto.MathematicalContract mathematicalContract() {
        return new FlowCoreDto.MathematicalContract(
                "FlowSpineDto is the authoritative immutable facts snapshot for Flow Core.",
                "decision = f(snapshot.currentState, snapshot.nextRequiredTransition, snapshot.bottlenecks, snapshot.forbiddenTransitions)",
                "project terminality > local hard blockers > live WIP > review > delivery evidence > scope > idle",
                "advisory_only forbids project mutation, agent dispatch, and merge regardless of recommended action.",
                List.of(
                        "A snapshot maps to exactly one currentState.",
                        "A currentState maps to exactly one advisory actionKey.",
                        "Forbidden transitions dominate recommended actions.",
                        "A decision is journaled only when evidenceHash or decisionHash changes.",
                        "The journal is append-only and does not advance project state."
                )
        );
    }

    private FlowCoreDto.Journal journal(FlowSpineEventEntity latestDecision,
                                        FlowCoreDto.Decision decision,
                                        long eventCount) {
        if (latestDecision == null) {
            return new FlowCoreDto.Journal(null, null, false, eventCount);
        }
        boolean recorded = decision.decisionHash().equals(latestDecision.getDecisionHash())
                && decision.evidenceHash().equals(latestDecision.getEvidenceHash());
        return new FlowCoreDto.Journal(latestDecision.getId(), latestDecision.getObservedAt(), recorded, eventCount);
    }

    private FlowCoreDto.DecisionEvent toDecisionEvent(FlowSpineEventEntity event) {
        return new FlowCoreDto.DecisionEvent(
                event.getId(),
                event.getCycleId(),
                event.getObservedAt(),
                event.getCurrentState(),
                event.getNextState(),
                event.getValueStatus(),
                event.getBottleneckType(),
                event.getBottleneckSeverity(),
                event.getDecisionActionKey(),
                event.getOwner(),
                event.getTransitionAction(),
                event.getDecisionStatus(),
                event.getAuthorizationStatus(),
                event.getRiskLevel(),
                split(event.getDecisionPreconditions()),
                split(event.getExpectedOutcomes()),
                split(event.getForbiddenActions()),
                event.getEvidenceHash(),
                event.getDecisionHash(),
                event.getDecisionReason()
        );
    }

    private static ActionSpec actionSpec(String state) {
        return switch (state) {
            case "FROZEN" -> spec("advisory.activate_canary_candidate",
                    List.of("operator selected this project as the only active canary"),
                    List.of("project becomes active before orchestration resumes"),
                    List.of("dispatch Jules while project.status=frozen", "merge delivery evidence from frozen project"));
            case "PROJECT_NOT_ACTIVE" -> spec("advisory.activate_project_candidate",
                    List.of("operator explicitly chooses this project for active flow"),
                    List.of("project enters active state or remains intentionally paused"),
                    List.of("autonomous dispatch before activation"));
            case "BLOCKED_BY_DUPLICATE_CONTENT" -> spec("advisory.collapse_duplicate_content",
                    List.of("duplicate task keys are inspected", "canonical task is selected"),
                    List.of("duplicate generation stops before new dispatch"),
                    List.of("queue more duplicate tasks", "promote duplicate work to review"));
            case "SYSTEM_STALLED" -> spec("advisory.inspect_scheduler_progress",
                    List.of("last progress marker is stale", "active scheduler token is identified"),
                    List.of("stalled node is made explicit before recovery"),
                    List.of("start a second scheduler for the same project"));
            case "BLOCKED_BY_TASK" -> spec("advisory.inspect_blocked_tasks",
                    List.of("blocked task has owner and unblocking condition"),
                    List.of("blocked task becomes queued or terminal"),
                    List.of("ignore blocked task and dispatch dependent work"));
            case "BLOCKED_BY_REVIEW" -> spec("advisory.repair_or_terminalize_failing_reviews",
                    List.of("each failing review has current GitHub status", "closed/unmergeable reviews are terminalized locally"),
                    List.of("failingReviews reaches 0 or unresolved reviews get one explicit recovery path"),
                    List.of("merge failing PR", "dispatch new overlapping work before review frontier is clean"));
            case "QUEUED" -> spec("advisory.check_dispatch_preconditions",
                    List.of("queued task has non-overlapping file scope", "dependency gates pass"),
                    List.of("one eligible task can be claimed by Jules"),
                    List.of("claim multiple conflicting tasks"));
            case "IMPLEMENTING" -> spec("advisory.monitor_agent_progress",
                    List.of("open session has recent progress or stale timeout evidence"),
                    List.of("agent continues, closes, or produces PR evidence"),
                    List.of("start replacement work without closing stale session"));
            case "UNDER_REVIEW" -> spec("advisory.wait_or_request_review_verdict",
                    List.of("open review artifact exists", "CI status is current"),
                    List.of("review becomes merged, failing, or terminalized"),
                    List.of("mark delivery without merge/readiness evidence"));
            case "DELIVERED" -> spec("advisory.acceptance_candidate",
                    List.of("feature readiness is complete", "merged deliverables match planned value"),
                    List.of("operator can accept the project"),
                    List.of("continue generating new work without new scope"));
            case "DECOMPOSING" -> spec("advisory.finish_decomposition",
                    List.of("wishlist item has testable deliverable boundaries"),
                    List.of("scope becomes queued implementation work"),
                    List.of("dispatch before decomposition is complete"));
            case "BLOCKED_BY_FAILED_FRONTIER" -> spec("advisory.create_recovery_plan",
                    List.of("failed tasks are grouped by root cause", "no live work is currently moving"),
                    List.of("one bounded recovery task is queued or failure is terminalized"),
                    List.of("requeue all failed tasks at once"));
            case "VERIFYING_DELIVERY" -> spec("advisory.reconcile_delivery_mapping",
                    List.of("done tasks and merged PRs are mapped to planned deliverables"),
                    List.of("value evidence becomes delivered or missing work is queued"),
                    List.of("declare delivery from done task count alone"));
            case "NO_SCOPE" -> spec("advisory.request_scope",
                    List.of("client-facing scope is absent"),
                    List.of("new wishlist item or explicit no-op decision exists"),
                    List.of("run agents without scope"));
            case "IDLE_NO_ACTIONABLE_WORK" -> spec("advisory.scope_or_acceptance_decision",
                    List.of("queue is empty", "active/review sessions are empty"),
                    List.of("project is accepted, scoped, or intentionally idle"),
                    List.of("treat idle as delivered without evidence"));
            case "ACCEPTED", "ARCHIVED" -> spec("advisory.no_action_terminal",
                    List.of("terminal project state is confirmed"),
                    List.of("no autonomous production action is recommended"),
                    List.of("mutate terminal project without operator decision"));
            default -> spec("advisory.no_action_unknown_state",
                    List.of("state is recognized by Flow Spine"),
                    List.of("operator inspects unsupported state"),
                    List.of("execute automated mutation from unknown state"));
        };
    }

    private static String decisionStatus(String state) {
        return switch (state) {
            case "ACCEPTED", "ARCHIVED" -> "NO_OPERATION_TERMINAL";
            case "DELIVERED" -> "AWAITING_ACCEPTANCE_DECISION";
            default -> "NEXT_ACTION_IDENTIFIED";
        };
    }

    private static String riskLevel(FlowSpineDto snapshot) {
        if (!snapshot.bottlenecks().isEmpty() && !isBlank(snapshot.bottlenecks().get(0).severity())) {
            return snapshot.bottlenecks().get(0).severity();
        }
        if ("value_blocked".equals(snapshot.valueStatus())) {
            return "medium";
        }
        if ("client_value_delivered".equals(snapshot.valueStatus())) {
            return "none";
        }
        return "low";
    }

    private static String confidence(FlowSpineDto snapshot) {
        boolean singleStatePass = snapshot.invariants().stream()
                .anyMatch(invariant -> "single_current_state".equals(invariant.key())
                        && "pass".equals(invariant.status()));
        if (singleStatePass && !isBlank(evidenceHash(snapshot)) && snapshot.nextRequiredTransition() != null) {
            return "high";
        }
        return "medium";
    }

    private static String rationale(FlowSpineDto snapshot, FlowSpineDto.Transition transition) {
        if (!isBlank(snapshot.blockingReason())) {
            return snapshot.blockingReason();
        }
        return transition.reason();
    }

    private static String evidenceHash(FlowSpineDto snapshot) {
        return snapshot.journal() == null ? "" : snapshot.journal().evidenceHash();
    }

    private static String decisionHash(String currentState,
                                       String nextState,
                                       String actionKey,
                                       String status,
                                       String riskLevel,
                                       String evidenceHash,
                                       List<String> preconditions,
                                       List<String> expectedOutcomes,
                                       List<String> forbiddenActions) {
        return sha256(String.join("|",
                nullToBlank(currentState),
                nullToBlank(nextState),
                nullToBlank(actionKey),
                nullToBlank(status),
                nullToBlank(riskLevel),
                nullToBlank(evidenceHash),
                String.join(",", preconditions),
                String.join(",", expectedOutcomes),
                String.join(",", forbiddenActions)
        ));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash Flow Core decision", e);
        }
    }

    private static List<String> stableUnion(List<String> left, List<String> right) {
        return Stream.concat(left.stream(), right.stream())
                .filter(value -> !isBlank(value))
                .distinct()
                .toList();
    }

    private String evidenceSummary(FlowCoreDto core) {
        FlowSpineDto snapshot = core.snapshot();
        FlowCoreDto.Decision decision = core.decision();
        return "snapshot.currentState=" + snapshot.currentState()
                + "; snapshot.valueStatus=" + snapshot.valueStatus()
                + "; counts=" + snapshot.counts()
                + "; evidence=" + snapshot.evidence()
                + "; decision.actionKey=" + decision.actionKey()
                + "; decision.status=" + decision.status()
                + "; authorization.status=" + core.authorization().status();
    }

    private String join(List<String> values) {
        return String.join("\n", values);
    }

    private List<String> split(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return value.lines()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static ActionSpec spec(String actionKey,
                                   List<String> preconditions,
                                   List<String> expectedOutcomes,
                                   List<String> forbiddenActions) {
        return new ActionSpec(actionKey, preconditions, expectedOutcomes, forbiddenActions);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ActionSpec(
            String actionKey,
            List<String> preconditions,
            List<String> expectedOutcomes,
            List<String> forbiddenActions
    ) {
    }
}
