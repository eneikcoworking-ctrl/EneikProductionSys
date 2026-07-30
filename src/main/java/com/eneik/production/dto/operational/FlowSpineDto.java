package com.eneik.production.dto.operational;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlowSpineDto(
        Instant generatedAt,
        String mode,
        ProjectRef project,
        String currentState,
        String valueStatus,
        String blockingReason,
        Transition nextRequiredTransition,
        List<Transition> allowedTransitions,
        List<ForbiddenTransition> forbiddenTransitions,
        EvidenceVector evidence,
        FlowCounts counts,
        List<FlowInvariant> invariants,
        String deterministicRule
) {
    public record ProjectRef(UUID id, String name, String status, String repositoryName) {
    }

    public record Transition(
            String from,
            String to,
            String owner,
            String action,
            List<String> evidenceRequired,
            String reason
    ) {
    }

    public record ForbiddenTransition(
            String from,
            String to,
            String reason
    ) {
    }

    public record EvidenceVector(
            int mergedReviews,
            int openReviews,
            int failingReviews,
            int qualityGatePassed,
            int qualityGateFailed,
            long pendingWishlist,
            long compilingWishlist,
            long openSessions,
            String systemStatus,
            boolean duplicateContentDetected
    ) {
    }

    public record FlowCounts(
            long queuedTasks,
            long activeTasks,
            long reviewTasks,
            long doneTasks,
            long failedTasks,
            long blockedTasks,
            int totalFeatures,
            int completeFeatures,
            int totalDeliverables,
            int mergedDeliverables,
            boolean decompositionComplete
    ) {
    }

    public record FlowInvariant(
            String key,
            String status,
            String statement,
            String evidence
    ) {
    }
}
