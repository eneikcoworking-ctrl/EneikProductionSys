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
    void genuinelyGlobalBlockersStillStopNewQueuedDispatch() {
        // Unlike task/review/stall blockers, these really do mean nothing in the project should move.
        FlowCoreDto rateLimited = core("GITHUB_RATE_LIMITED", "active", 5, 0, 0, 0, 0);
        assertFalse(service.authorize(rateLimited, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());

        FlowCoreDto frozen = core("FROZEN", "frozen", 5, 0, 0, 0, 0);
        assertFalse(service.authorize(frozen, OperationalAction.DISPATCH_QUEUED_TASKS).allowed());
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
