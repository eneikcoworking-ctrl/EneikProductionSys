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
        List<TransitionMatrixEntry> transitionMatrix,
        List<Bottleneck> bottlenecks,
        List<ForbiddenTransition> forbiddenTransitions,
        EvidenceVector evidence,
        FlowCounts counts,
        List<FlowInvariant> invariants,
        JournalSummary journal,
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

    public record TransitionMatrixEntry(
            int priority,
            String from,
            String condition,
            String to,
            String owner,
            List<String> evidenceRequired,
            String promotionMode
    ) {
    }

    public record Bottleneck(
            String type,
            String severity,
            String state,
            long ageMinutes,
            long slaMinutes,
            String slaStatus,
            String owner,
            String reason,
            String nextAction
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

    public record JournalSummary(
            UUID latestEventId,
            Instant lastObservedAt,
            String previousState,
            String currentState,
            String evidenceHash,
            boolean currentSnapshotRecorded,
            long eventCount
    ) {
    }

    public record FlowEvent(
            UUID id,
            UUID cycleId,
            Instant observedAt,
            String previousState,
            String currentState,
            String nextState,
            String valueStatus,
            String bottleneckType,
            String bottleneckSeverity,
            long ageInStateMinutes,
            String owner,
            String transitionAction,
            String evidenceHash,
            String blockingReason
    ) {
    }
}
