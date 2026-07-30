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
