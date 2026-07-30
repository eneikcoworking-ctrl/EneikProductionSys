package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalFlowCoreServiceTest {

    @Test
    void reviewBlockMapsToOneAdvisoryRepairDecision() {
        FlowSpineDto snapshot = snapshot(
                "BLOCKED_BY_REVIEW",
                "value_blocked",
                "3 failing/conflicted review(s) exist.",
                bottleneck("review_bottleneck", "high"),
                transition("BLOCKED_BY_REVIEW", "UNDER_REVIEW", "AutoMergeService / Gemini review")
        );

        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        assertEquals("advisory.repair_or_terminalize_failing_reviews", decision.actionKey());
        assertEquals("NEXT_ACTION_IDENTIFIED", decision.status());
        assertEquals("high", decision.riskLevel());
        assertTrue(decision.preconditions().contains("each failing review has current GitHub status"));
        assertTrue(decision.forbiddenActions().contains("merge failing PR"));
    }

    @Test
    void advisoryAuthorizationNeverAllowsProjectMutationDispatchOrMerge() {
        FlowSpineDto snapshot = snapshot(
                "QUEUED",
                "value_in_progress",
                "",
                null,
                transition("QUEUED", "IMPLEMENTING", "JulesDispatchService")
        );
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);

        assertEquals("ADVISORY_ONLY_AUTHORIZED", authorization.status());
        assertTrue(authorization.journalAppendAllowed());
        assertFalse(authorization.projectMutationAllowed());
        assertFalse(authorization.agentDispatchAllowed());
        assertFalse(authorization.mergeAllowed());
    }

    @Test
    void deliveredStateIsAcceptanceCandidateNotMoreGeneration() {
        FlowSpineDto snapshot = snapshot(
                "DELIVERED",
                "client_value_delivered",
                "",
                null,
                transition("DELIVERED", "ACCEPTED", "ProjectFlowService.acceptProject")
        );

        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);

        assertEquals("advisory.acceptance_candidate", decision.actionKey());
        assertEquals("AWAITING_ACCEPTANCE_DECISION", decision.status());
        assertEquals("none", decision.riskLevel());
        assertTrue(decision.forbiddenActions().contains("continue generating new work without new scope"));
    }

    @Test
    void decisionHashIsStableForSameSnapshot() {
        FlowSpineDto snapshot = snapshot(
                "BLOCKED_BY_FAILED_FRONTIER",
                "value_blocked",
                "28 failed task(s) exist and no live work is moving.",
                bottleneck("failed_frontier_bottleneck", "high"),
                transition("BLOCKED_BY_FAILED_FRONTIER", "QUEUED", "PlannedWorkRecoveryService")
        );

        FlowCoreDto.Decision first = OperationalFlowCoreService.decide(snapshot);
        FlowCoreDto.Decision second = OperationalFlowCoreService.decide(snapshot);

        assertEquals(first.decisionHash(), second.decisionHash());
        assertEquals(first.decisionHash(), OperationalFlowCoreService.decisionHash(snapshot, first));
    }

    private FlowSpineDto snapshot(String state,
                                  String valueStatus,
                                  String blockingReason,
                                  FlowSpineDto.Bottleneck bottleneck,
                                  FlowSpineDto.Transition transition) {
        List<FlowSpineDto.Bottleneck> bottlenecks = bottleneck == null ? List.of() : List.of(bottleneck);
        return new FlowSpineDto(
                Instant.EPOCH,
                "observe_only",
                new FlowSpineDto.ProjectRef(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "test", "active", "test"),
                state,
                valueStatus,
                blockingReason,
                transition,
                List.of(transition),
                List.of(),
                bottlenecks,
                List.of(new FlowSpineDto.ForbiddenTransition("BLOCKED_BY_REVIEW", "MERGED",
                        "Failing/conflicted PR evidence cannot be promoted.")),
                new FlowSpineDto.EvidenceVector(0, 0, 0, 0, 0, 0, 0, 0, "ok", false),
                new FlowSpineDto.FlowCounts(0, 0, 0, 0, 0, 0, 1, 0, 1, 0, true),
                List.of(new FlowSpineDto.FlowInvariant("single_current_state", "pass",
                        "Every project maps to exactly one flow state.", "test evidence")),
                new FlowSpineDto.JournalSummary(null, null, null, null,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", false, 0),
                "deterministic precedence"
        );
    }

    private FlowSpineDto.Bottleneck bottleneck(String type, String severity) {
        return new FlowSpineDto.Bottleneck(
                type,
                severity,
                "state",
                60,
                30,
                "breached",
                "owner",
                "reason",
                "next action"
        );
    }

    private FlowSpineDto.Transition transition(String from, String to, String owner) {
        return new FlowSpineDto.Transition(
                from,
                to,
                owner,
                "Take the next deterministic advisory action.",
                List.of("transition evidence exists"),
                "Transition reason"
        );
    }
}
