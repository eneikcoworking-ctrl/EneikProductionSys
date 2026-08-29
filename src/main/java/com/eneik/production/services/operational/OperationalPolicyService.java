package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationalPolicyService {

    /**
     * The states in which nothing in the project should move at all.
     *
     * <p>Written out eleven times in this file until 2026-08-29 - one invariant copied into eleven places,
     * which Charter invariant 10 names as the guarantee that they will drift apart. Naming it also gives
     * denialReason below something to ask, which is what made the false message findable.
     */
    static final Set<String> GLOBALLY_BLOCKING_STATES = Set.of(
            "FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
            "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT");

    private final OperationalFlowCoreService flowCoreService;

    public OperationalPolicyService(OperationalFlowCoreService flowCoreService) {
        this.flowCoreService = flowCoreService;
    }

    /**
     * The snapshot taken for the tick currently running on this thread, if there is one.
     *
     * <p>Not a cache: it has no lifetime and no size, only the frame it was opened in. The orchestration
     * opens it once per project and closes it in a finally; every other caller - controllers, other
     * threads - finds nothing here and builds its own exactly as before.
     */
    private static final ThreadLocal<Map.Entry<UUID, FlowCoreDto>> TICK_SNAPSHOT = new ThreadLocal<>();

    @Transactional(readOnly = true)
    public OperationalDecision authorize(UUID projectId, OperationalAction action) {
        Map.Entry<UUID, FlowCoreDto> open = TICK_SNAPSHOT.get();
        if (open != null && open.getKey().equals(projectId)) {
            return authorize(open.getValue(), action);
        }
        return authorize(flowCoreService.build(projectId), action);
    }

    /**
     * One snapshot, asked many times.
     *
     * <p>Building the model reads the project's whole task history, its wishlists, the sessions on those
     * tasks and the reviews on those sessions, then walks the task list about fifteen times. The overload
     * above does that once per QUESTION, and ContinuousOrchestrationService asks ten of them per tick.
     * Measured 2026-08-29: fifty-two rebuilds in twenty minutes, byte-identical results
     * ({@code passed=92 failed=0 REFUTED=45 NEVER ASKED=245 of 382 tasks}), up to five inside one second.
     *
     * <p>The overload taking a FlowCoreDto already existed; it only needed a way to obtain one. Callers
     * that ask more than once about the same tick take a snapshot here and pass it, which makes the
     * snapshot's lifetime the tick rather than the question.
     */
    @Transactional(readOnly = true)
    public FlowCoreDto snapshot(UUID projectId) {
        FlowCoreDto core = flowCoreService.build(projectId);
        TICK_SNAPSHOT.set(Map.entry(projectId, core));
        return core;
    }

    /**
     * Closes the tick opened by {@link #snapshot(UUID)}. Must run in a finally: a snapshot left open on a
     * pooled scheduler thread would answer the next tick with the previous one.
     *
     * <p>Measured 2026-08-29 before this existed: four full model builds per tick - the snapshot, plus
     * three requireAllowed calls inside the very actions the snapshot had just authorized, microseconds
     * apart with nothing happening in between. Seventy six identical rebuilds in twenty minutes while the
     * factory had no work at all: 356 done, 27 failed, two queued behind a failed dependency, zero open
     * sessions.
     */
    public void closeTick() {
        TICK_SNAPSHOT.remove();
    }

    public OperationalDecision authorize(FlowCoreDto core, OperationalAction action) {
        FlowSpineDto snapshot = core.snapshot();
        FlowCoreDto.Authorization authorization = core.authorization();
        boolean activeProject = "active".equalsIgnoreCase(core.project().status());
        boolean hardBlocked = isHardBlocked(snapshot.currentState());
        boolean terminal = isTerminal(snapshot.currentState());
        boolean hasPendingScope = snapshot.evidence().pendingWishlist() > 0
                || snapshot.evidence().compilingWishlist() > 0
                || !snapshot.counts().decompositionComplete();

        boolean allowed = switch (action) {
            case OBSERVE -> authorization.journalAppendAllowed();
            case ADD_WISHLIST -> activeProject && !terminal;
            // Deliberately narrower than hardBlocked (2026-07-31): a task/review/stall-specific blocker
            // (BLOCKED_BY_TASK, BLOCKED_BY_REVIEW, SYSTEM_STALLED, BLOCKED_BY_FAILED_FRONTIER) means
            // something ELSEWHERE in the project needs attention - it carries no evidence that a brand-new,
            // never-dispatched queued task is unsafe to start. Confirmed live: one task with a broken
            // review link held 11 other, fully independent queued tasks hostage for 9+ hours under the old
            // "any hard-blocked state stops everything" rule. Genuinely global conditions (frozen/archived/
            // accepted/not-active, GitHub itself unavailable, a content-generation quality gate) still stop
            // new dispatch - those really do mean nothing in this project should move. Extended same-day
            // (2026-07-31, second pass) to ORCHESTRATE, DISPATCH_REVIEW_TASKS, CHECK_COVERAGE_AUDITS and
            // RUN_PROJECT_AUDIT_PIPELINE: SYSTEM_STALLED itself is defined as "no dispatch/merge progress
            // despite actionable work" (ContinuousOrchestrationService.checkForSystemStall) - using it to
            // block the very dispatch/audit actions that would CREATE that progress is circular and
            // self-perpetuating. Confirmed live on test-fortieth: a task stuck in pending_review (not the
            // `review` status Branch GC's own auto-recovery path checks for) sat blocked for 80+ minutes
            // because DISPATCH_REVIEW_TASKS still used the broad hardBlocked check, and coverage audits
            // were STILL blocked under SYSTEM_STALLED despite an earlier same-day narrowing attempt here
            // that left a redundant `!hardBlocked &&` prefix in front of the narrower Set check below,
            // silently defeating it - removed that dead prefix along with widening the narrowing to the
            // two actions that never got it at all.
            // ORCHESTRATE keeps its original, deliberate entanglement with BLOCKED_BY_REVIEW/BLOCKED_BY_TASK
            // (new decomposition genuinely waits its turn behind those) - only SYSTEM_STALLED is carved out
            // here, since that state is specifically "no dispatch/merge progress despite actionable work"
            // and a pending wishlist never getting decomposed is itself part of what keeps a project stalled.
            case ORCHESTRATE -> activeProject && !terminal
                    && (!hardBlocked || "SYSTEM_STALLED".equals(snapshot.currentState()))
                    && (!"DELIVERED".equals(snapshot.currentState()) || hasPendingScope);
            // Narrowed 2026-08-28, the same correction already made twice in this file for
            // DISPATCH_QUEUED_TASKS and MERGE_PR, for the same reason. Gating on state EQUALITY meant
            // recovery was authorized only when "failed tasks" was the project's single most salient
            // condition - and BLOCKED_BY_FAILED_FRONTIER is the LAST of fifteen states in
            // FlowSpineService.decideState. It is reached only when there is no pending or compiling
            // wishlist, decomposition is complete, and nothing is queued, active, in session or in review.
            // A factory that continuously generates wishlists from its own sources never satisfies that,
            // so the state was unreachable and the recovery mechanism behind it never ran once.
            //
            // Measured on test-fiftieth, 2026-08-28: 50 failed tasks, DPMO 331521, and no live path out -
            // this gate was one of the two exits, and the other (GeminiObserverActionService.reviveFailedTask)
            // belongs to a service decommissioned by V111. Failed tasks were a sink.
            //
            // DECOMPOSING is a project-wide condition and carries no evidence that THIS failed task is
            // unsafe to resume - the identical argument this file already records against BLOCKED_BY_REVIEW
            // blocking an unrelated approved PR. Safe to widen because the resolver is itself guarded:
            // PlannedWorkRecoveryService.resumeEligibleTask refuses on an active claim, an active session,
            // a merged task, an unsatisfied dependency, or a prior resume (bounded at one), and moves the
            // status by compare-and-set. Authorizing it more often cannot produce double work.
            case RECOVER_FAILED_FRONTIER -> activeProject && snapshot.counts().failedTasks() > 0
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            case DISPATCH_QUEUED_TASKS -> activeProject && snapshot.counts().queuedTasks() > 0
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            case DISPATCH_REVIEW_TASKS -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState())
                    && (snapshot.counts().reviewTasks() > 0 || snapshot.evidence().openReviews() > 0);
            case CHECK_COVERAGE_AUDITS, RUN_PROJECT_AUDIT_PIPELINE, CHECK_LAUNCHABILITY,
                    JUDGE_DELIVERED_WORK -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            // Deliberately narrower than hardBlocked (2026-07-31, same shape as DISPATCH_QUEUED_TASKS
            // above): confirmed live, PR#13 (task 529e5252) was already approved by review with no
            // problems of its own, but sat unmerged for hours because BLOCKED_BY_REVIEW is project-wide -
            // it fired on account of a DIFFERENT, unrelated review's failure, which carries no evidence
            // that THIS already-approved PR is unsafe to merge. Genuinely global conditions still stop
            // merging (frozen/archived/accepted/not-active, GitHub itself unavailable, a content-generation
            // quality gate) - those really do mean nothing in this project should move.
            case MERGE_PR -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState())
                    && (authorization.mergeAllowed() || snapshot.evidence().openReviews() > 0);
            // 2026-08-27 (Eneik Actualism restoration): Operability is sampled continuously on active projects
            // via adaptive Beta-Posterior cadence (ClientRuntimeObservabilityService.maybeObserve), matching
            // §1 and §2 of LIVE_PRODUCT_PLAN_2026-08-19.md ("The factory keeps a running product under permanent
            // falsification. Gating observation on completeness meant the product could not be observed at all
            // while it is being built").
            case OBSERVE_CLIENT_RUNTIME -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            case SYNC_GITHUB -> activeProject && !"GITHUB_RATE_LIMITED".equals(snapshot.currentState());
            case CLEANUP_TERMINAL_PROJECT -> true;
            case NUDGE_SESSION, BOOST_PRIORITY -> activeProject && !hardBlocked;
            case DISMISS_WISHLIST, ABANDON_CONFLICT -> activeProject && !terminal;
            // Same narrowing reasoning as DISPATCH_QUEUED_TASKS/MERGE_PR above (2026-08-01): reviving ONE
            // specific already-dead task carries no dependency on an unrelated task/review/stall elsewhere
            // in the project - only genuinely global conditions should stop it.
            case REVIVE_FAILED_TASK -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            // Same narrowing reasoning as REVIVE_FAILED_TASK/MERGE_PR (2026-08-03): resolving one specific
            // already-dead-ended task's orphaned PR carries no dependency on an unrelated task/review/stall
            // elsewhere in the project - only genuinely global conditions should stop it.
            case RESOLVE_ORPHANED_PR -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            // Deliberately excludes BLOCKED_BY_DUPLICATE_CONTENT from its own exclusion set (2026-08-07,
            // unlike every other action above): this IS the recovery action for that exact state - nothing
            // else can reach a terminal task status while dispatch itself is what BLOCKED_BY_DUPLICATE_CONTENT
            // denies, so gating this action the same way as the others would make the hard-stop permanent
            // (confirmed live, test-forty-third: stuck for 3+ hours with no autonomous recovery path).
            // The one set written out rather than read from GLOBALLY_BLOCKING_STATES, because it is
            // deliberately that set MINUS BLOCKED_BY_DUPLICATE_CONTENT - see the paragraph above. Left
            // literal so the difference is visible at the point where it is intended.
            case COLLAPSE_DUPLICATE_TASK -> activeProject
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                            "GITHUB_RATE_LIMITED").contains(snapshot.currentState());
            // Same narrowing reasoning as REVIVE_FAILED_TASK/RESOLVE_ORPHANED_PR (2026-08-08): retrying one
            // specific feature's closeout carries no dependency on an unrelated task/review/stall elsewhere
            // in the project - only genuinely global conditions should stop it.
            case RETRY_FEATURE_CLOSEOUT -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
            // Same narrowing reasoning as REVIVE_FAILED_TASK/RESOLVE_ORPHANED_PR/RETRY_FEATURE_CLOSEOUT
            // (2026-08-11): retiring one specific carrier task's wedged persistent worker carries no
            // dependency on an unrelated task/review/stall elsewhere in the project - only genuinely global
            // conditions should stop it.
            case RETIRE_STUCK_WORKER -> activeProject
                    && !GLOBALLY_BLOCKING_STATES.contains(snapshot.currentState());
        };

        String reason = allowed
                ? "Operational action " + action + " is allowed in state " + snapshot.currentState() + "."
                : denialReason(action, core);
        return new OperationalDecision(
                core.project().id(),
                action,
                allowed,
                snapshot.currentState(),
                authorization.status(),
                reason,
                snapshot.bottlenecks().stream().map(FlowSpineDto.Bottleneck::type).toList(),
                core
        );
    }

    @Transactional(readOnly = true)
    public void requireAllowed(UUID projectId, OperationalAction action) {
        OperationalDecision decision = authorize(projectId, action);
        if (!decision.allowed()) {
            throw new OperationalPolicyDeniedException(
                    projectId,
                    action,
                    decision.state(),
                    decision.authorizationStatus(),
                    decision.reason()
            );
        }
    }

    /**
     * Whether the current state is why anything at all is being refused.
     *
     * <p>If it is not, then a denial came from the action's own conjuncts, which are its counters - is
     * there anything for it to act on. The project being inactive is covered here too, because
     * PROJECT_NOT_ACTIVE is itself one of the globally blocking states.
     */
    static boolean stateBlocks(String state, String projectStatus) {
        return GLOBALLY_BLOCKING_STATES.contains(state)
                || isHardBlocked(state)
                || !"active".equalsIgnoreCase(projectStatus);
    }

    /**
     * A denial says what actually denied it.
     *
     * <p>Measured 2026-08-29: DISPATCH_REVIEW_TASKS was refused once a tick, fifteen ticks running, with
     * the text "denied by Flow Core state DECOMPOSING" - while CHECK_COVERAGE_AUDITS, whose set of blocking
     * states is byte-for-byte the same, was allowed in those same ticks. So DECOMPOSING was blocking
     * neither, and the message named a cause that was not one. It cost this session an hour: the state was
     * investigated as the thing holding the flow, because the message said so. The real conjunct was the
     * action's own - reviewTasks > 0 || openReviews > 0 - and its plain meaning is that there is nothing
     * to review, which is a normal condition and not a fault of the state.
     */
    private static String denialReason(OperationalAction action, FlowCoreDto core) {
        FlowSpineDto snapshot = core.snapshot();
        return denialReason(action, snapshot.currentState(), core.project().status(),
                snapshot.blockingReason(), core.authorization().status());
    }

    // Takes the five values it actually reads rather than the whole model, so the sentence it produces can
    // be asserted without building one.
    static String denialReason(OperationalAction action, String state, String projectStatus,
            String blockingReason, String authorizationStatus) {
        if (!stateBlocks(state, projectStatus)) {
            return "Operational action " + action + " is not blocked by Flow Core state " + state
                    + "; its own precondition is unmet - there is nothing for it to act on right now.";
        }
        if (blockingReason != null && !blockingReason.isBlank()) {
            return "Operational action " + action + " denied by Flow Core state " + state
                    + ": " + blockingReason;
        }
        return "Operational action " + action + " denied by Flow Core state " + state
                + " with authorization " + authorizationStatus + ".";
    }

    private static boolean isHardBlocked(String state) {
        return Set.of(
                "FROZEN",
                "PROJECT_NOT_ACTIVE",
                "BLOCKED_BY_DUPLICATE_CONTENT",
                "GITHUB_RATE_LIMITED",
                "SYSTEM_STALLED",
                "BLOCKED_BY_TASK",
                "BLOCKED_BY_REVIEW",
                "BLOCKED_BY_FAILED_FRONTIER",
                "ACCEPTED",
                "ARCHIVED"
        ).contains(state);
    }

    private static boolean isTerminal(String state) {
        return Set.of("ACCEPTED", "ARCHIVED", "FROZEN", "PROJECT_NOT_ACTIVE").contains(state);
    }

    public record OperationalDecision(
            UUID projectId,
            OperationalAction action,
            boolean allowed,
            String state,
            String authorizationStatus,
            String reason,
            List<String> blockers,
            FlowCoreDto core
    ) {
    }
}
