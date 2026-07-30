package com.eneik.production.dto.operational;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlowCoreDto(
        Instant generatedAt,
        String mode,
        FlowSpineDto.ProjectRef project,
        FlowSpineDto snapshot,
        Decision decision,
        Authorization authorization,
        MathematicalContract mathematicalContract,
        Journal journal
) {
    public record Decision(
            String actionKey,
            String status,
            String owner,
            String action,
            String rationale,
            List<String> preconditions,
            List<String> expectedOutcomes,
            List<String> forbiddenActions,
            String riskLevel,
            String confidence,
            String evidenceHash,
            String decisionHash
    ) {
    }

    public record Authorization(
            String status,
            String mode,
            boolean journalAppendAllowed,
            boolean projectMutationAllowed,
            boolean agentDispatchAllowed,
            boolean mergeAllowed,
            String reason
    ) {
    }

    public record MathematicalContract(
            String factsSource,
            String decisionFunction,
            String precedenceOrder,
            String safetyRule,
            List<String> invariants
    ) {
    }

    public record Journal(
            UUID latestDecisionEventId,
            Instant lastObservedAt,
            boolean currentDecisionRecorded,
            long eventCount
    ) {
    }

    public record DecisionEvent(
            UUID id,
            UUID cycleId,
            Instant observedAt,
            String currentState,
            String nextState,
            String valueStatus,
            String bottleneckType,
            String bottleneckSeverity,
            String actionKey,
            String owner,
            String transitionAction,
            String decisionStatus,
            String authorizationStatus,
            String riskLevel,
            List<String> preconditions,
            List<String> expectedOutcomes,
            List<String> forbiddenActions,
            String evidenceHash,
            String decisionHash,
            String decisionReason
    ) {
    }
}
