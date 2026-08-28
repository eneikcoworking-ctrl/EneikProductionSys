package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OperationalPolicyServiceTest {

    private final OperationalPolicyService service = new OperationalPolicyService(mock(OperationalFlowCoreService.class));

    @Test
    void duplicateContentDeniesCapacityConsumingActionsButStillAllowsClientWishlistAdmission() {
        FlowCoreDto core = core("BLOCKED_BY_DUPLICATE_CONTENT", "active", 3, 0, 0, 1, 0);

        assertTrue(service.authorize(core, OperationalAction.ADD_WISHLIST).allowed());
        assertFalse(service.authorize(core, OperationalAction.ORCHESTRATE).allowed());
        assertFalse(service.authorize(core, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        assertFalse(service.authorize(core, OperationalAction.MERGE_PR).allowed());
    }

    @Test
    void queuedStateAllowsOnlyMatchingDispatchWork() {
        FlowCoreDto core = core("QUEUED", "active", 2, 0, 0, 1, 0);

        assertTrue(service.authorize(core, OperationalAction.ORCHESTRATE).allowed());
        assertTrue(service.authorize(core, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        assertFalse(service.authorize(core, OperationalAction.DISPATCH_REVIEW_TASKS).allowed());
    }

    /**
     * Runtime observation is a JIT reactive probe on a live product, not a post-assembly acceptance step.
     *
     * <p>History, because this test asserted the exact opposite until 2026-08-27 and the reversal was
     * deliberate. The F1 rule (2026-08-23) permitted OBSERVE_CLIENT_RUNTIME only at DELIVERED, reasoning
     * that a sample taken mid-assembly is bias rather than noise - the answer to "does the product answer"
     * is necessarily no while the product is still being assembled, and V_p fell monotonically (0.2, 0.167,
     * 0.143) across three such samples on test-fiftieth. That reasoning was sound about the SAMPLE and
     * wrong about the CONSEQUENCE: gating on completeness meant a product under construction could not be
     * observed at all, so the factory held no running product under permanent falsification
     * (LIVE_PRODUCT_PLAN_2026-08-19.md §1-2). The bias is handled where it belongs - in the adaptive
     * Beta-posterior cadence of ClientRuntimeObservabilityService.maybeObserve - instead of by refusing to
     * look.
     *
     * <p>What the predicate now says: observe on any active project EXCEPT the states where observing is
     * either impossible or meaningless. Both halves are asserted below, so a regression in either
     * direction fails this test.
     */
    @Test
    void runtimeObservationRunsContinuouslyOnActiveProjectsButNotInTerminalOrBlockedStates() {
        // Mid-assembly is now exactly when a live product most needs falsifying.
        FlowCoreDto building = core("BLOCKED_BY_TASK", "active", 5, 0, 0, 0, 0);
        assertTrue(service.authorize(building, OperationalAction.OBSERVE_CLIENT_RUNTIME).allowed(),
                "a product under construction is still a running product and must be observable");

        FlowCoreDto delivered = core("DELIVERED", "active", 0, 0, 0, 0, 0);
        assertTrue(service.authorize(delivered, OperationalAction.OBSERVE_CLIENT_RUNTIME).allowed(),
                "at DELIVERED the observation is exactly what decides whether the product answers");

        // The other half of the predicate. ACCEPTED is here because acceptance means the client ended the
        // engagement - a different axis from readiness - so the factory stops touching the product.
        for (String haltedState : List.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT")) {
            FlowCoreDto halted = core(haltedState, "active", 0, 0, 0, 0, 0);
            assertFalse(service.authorize(halted, OperationalAction.OBSERVE_CLIENT_RUNTIME).allowed(),
                    "runtime must not be probed in state " + haltedState);
        }
    }

    @Test
    void deliveredStateDoesNotGenerateMoreScopeWithoutPendingDemand() {
        FlowCoreDto core = core("DELIVERED", "active", 0, 0, 0, 0, 0);

        assertFalse(service.authorize(core, OperationalAction.ORCHESTRATE).allowed());
        assertTrue(service.authorize(core, OperationalAction.CHECK_COVERAGE_AUDITS).allowed());
    }

    @Test
    void terminalProjectDeniesOrdinaryMutationAndDispatch() {
        FlowCoreDto core = core("ACCEPTED", "accepted", 3, 1, 1, 1, 1);

        assertFalse(service.authorize(core, OperationalAction.ADD_WISHLIST).allowed());
        assertFalse(service.authorize(core, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        assertTrue(service.authorize(core, OperationalAction.CLEANUP_TERMINAL_PROJECT).allowed());
    }

    @Test
    void oneFailingReviewNoLongerBlocksDispatchOfUnrelatedQueuedTasks() {
        // Direct regression test for the 2026-07-31 incident: one task's failing/conflicted review held
        // 11 other, fully independent queued tasks hostage for 9+ hours because DISPATCH_QUEUED_TASKS used
        // to share the same hard-blocked set as every other action in the project - a task/review/stall-
        // specific blocker carries no evidence that a brand-new, never-dispatched task is unsafe to start.
        FlowCoreDto blockedByReview = core("BLOCKED_BY_REVIEW", "active", 5, 0, 0, 0, 0);
        assertTrue(service.authorize(blockedByReview, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
        // ORCHESTRATE stays blocked - unchanged, still genuinely entangled with the failing review.
        assertFalse(service.authorize(blockedByReview, OperationalAction.ORCHESTRATE).allowed());

        FlowCoreDto blockedByTask = core("BLOCKED_BY_TASK", "active", 5, 0, 0, 0, 0);
        assertTrue(service.authorize(blockedByTask, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());

        FlowCoreDto stalled = core("SYSTEM_STALLED", "active", 5, 0, 0, 0, 0);
        assertTrue(service.authorize(stalled, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
    }

    @Test
    void systemStalledNoLongerBlocksTheActionsThatWouldClearTheStall() {
        // Direct regression test for the 2026-07-31 incident (second pass, same day): SYSTEM_STALLED means
        // "no dispatch/merge progress despite actionable work" - using it to block the very actions that
        // would create that progress is circular. Confirmed live on test-fortieth: a task stuck in
        // pending_review sat blocked for 80+ minutes because DISPATCH_REVIEW_TASKS still used the broad
        // hardBlocked check, and CHECK_COVERAGE_AUDITS/RUN_PROJECT_AUDIT_PIPELINE were still blocked despite
        // an earlier same-day narrowing attempt that left a dead `!hardBlocked &&` prefix in front of the
        // narrower Set check, silently defeating it.
        FlowCoreDto stalled = core("SYSTEM_STALLED", "active", 0, 1, 1, 1, 0);
        assertTrue(service.authorize(stalled, OperationalAction.ORCHESTRATE).allowed());
        assertTrue(service.authorize(stalled, OperationalAction.DISPATCH_REVIEW_TASKS).allowed());
        assertTrue(service.authorize(stalled, OperationalAction.CHECK_COVERAGE_AUDITS).allowed());
        assertTrue(service.authorize(stalled, OperationalAction.RUN_PROJECT_AUDIT_PIPELINE).allowed());
    }

    @Test
    void orchestrateStaysBlockedByGenuinelyEntangledReviewAndTaskBlockersUnlikeStall() {
        // ORCHESTRATE deliberately keeps its original entanglement with BLOCKED_BY_REVIEW/BLOCKED_BY_TASK
        // (new decomposition genuinely waits its turn behind those) - only SYSTEM_STALLED was carved out.
        FlowCoreDto blockedByReview = core("BLOCKED_BY_REVIEW", "active", 5, 0, 0, 1, 0);
        assertFalse(service.authorize(blockedByReview, OperationalAction.ORCHESTRATE).allowed());

        FlowCoreDto blockedByTask = core("BLOCKED_BY_TASK", "active", 5, 0, 0, 1, 0);
        assertFalse(service.authorize(blockedByTask, OperationalAction.ORCHESTRATE).allowed());
    }

    @Test
    void genuinelyGlobalBlockersStillStopNewQueuedDispatch() {
        // Unlike task/review/stall blockers, these really do mean nothing in the project should move.
        FlowCoreDto rateLimited = core("GITHUB_RATE_LIMITED", "active", 5, 0, 0, 0, 0);
        assertFalse(service.authorize(rateLimited, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());

        FlowCoreDto frozen = core("FROZEN", "frozen", 5, 0, 0, 0, 0);
        assertFalse(service.authorize(frozen, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
    }

    @Test
    void reviveFailedTaskIsUnaffectedByAnUnrelatedTaskOrReviewBlockerButStopsForGenuinelyGlobalOnes() {
        // 2026-08-01: GeminiObserverActionService.reviveFailedTask's gate - reviving ONE specific already-
        // dead task carries no dependency on an unrelated task/review/stall elsewhere in the project.
        FlowCoreDto blockedByReview = core("BLOCKED_BY_REVIEW", "active", 0, 0, 0, 0, 0);
        assertTrue(service.authorize(blockedByReview, OperationalAction.REVIVE_FAILED_TASK).allowed());

        FlowCoreDto stalled = core("SYSTEM_STALLED", "active", 0, 0, 0, 0, 0);
        assertTrue(service.authorize(stalled, OperationalAction.REVIVE_FAILED_TASK).allowed());

        FlowCoreDto rateLimited = core("GITHUB_RATE_LIMITED", "active", 0, 0, 0, 0, 0);
        assertFalse(service.authorize(rateLimited, OperationalAction.REVIVE_FAILED_TASK).allowed());
    }

    @Test
    void oneFailingReviewNoLongerBlocksMergingADifferentAlreadyApprovedReview() {
        // Direct regression test for the 2026-07-31 incident: PR#13 (task 529e5252), already approved by
        // review with nothing wrong of its own, sat unmerged for hours because BLOCKED_BY_REVIEW is
        // project-wide - it fired on account of a DIFFERENT, unrelated review's failure. That carries no
        // evidence THIS review is unsafe to merge, exactly the same reasoning already applied to
        // DISPATCH_QUEUED_TASKS above.
        FlowCoreDto blockedByReview = core("BLOCKED_BY_REVIEW", "active", 0, 0, 1, 0, 0);
        assertTrue(service.authorize(blockedByReview, OperationalAction.MERGE_PR).allowed());

        FlowCoreDto blockedByTask = core("BLOCKED_BY_TASK", "active", 0, 0, 1, 0, 0);
        assertTrue(service.authorize(blockedByTask, OperationalAction.MERGE_PR).allowed());
    }

    @Test
    void genuinelyGlobalBlockersStillStopMerging() {
        FlowCoreDto rateLimited = core("GITHUB_RATE_LIMITED", "active", 0, 0, 1, 0, 0);
        assertFalse(service.authorize(rateLimited, OperationalAction.MERGE_PR).allowed());

        FlowCoreDto frozen = core("FROZEN", "frozen", 0, 0, 1, 0, 0);
        assertFalse(service.authorize(frozen, OperationalAction.MERGE_PR).allowed());
    }

    private FlowCoreDto core(String state,
                             String projectStatus,
                             long queuedTasks,
                             long reviewTasks,
                             int openReviews,
                             long pendingWishlist,
                             long compilingWishlist) {
        FlowSpineDto.Transition transition = new FlowSpineDto.Transition(
                state, "NEXT", "owner", "action", List.of("evidence"), "reason");
        FlowSpineDto snapshot = new FlowSpineDto(
                Instant.EPOCH,
                "observe_only",
                new FlowSpineDto.ProjectRef(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "test", projectStatus, "test"),
                state,
                "value_in_progress",
                state.startsWith("BLOCKED_") ? "blocked" : "",
                transition,
                List.of(transition),
                List.of(),
                List.of(),
                List.of(),
                new FlowSpineDto.EvidenceVector(0, openReviews, 0, 0, 0,
                        pendingWishlist, compilingWishlist, 0, "ok", state.equals("BLOCKED_BY_DUPLICATE_CONTENT")),
                new FlowSpineDto.FlowCounts(queuedTasks, 0, reviewTasks, 0, 0, 0,
                        1, 0, 1, 0, pendingWishlist == 0 && compilingWishlist == 0),
                List.of(),
                new FlowSpineDto.JournalSummary(null, null, null, null, "hash", false, 0),
                "deterministic precedence"
        );
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);
        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);
        return new FlowCoreDto(
                Instant.EPOCH,
                "flow_core_enforced",
                snapshot.project(),
                snapshot,
                decision,
                authorization,
                new FlowCoreDto.MathematicalContract("facts", "decision", "precedence", "safety", List.of()),
                new FlowCoreDto.Journal(null, null, false, 0)
        );
    }
}
