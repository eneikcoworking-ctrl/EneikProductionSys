package com.eneik.production.dto.operational;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OperationalTruthDto(
        Instant generatedAt,
        String mode,
        ProjectRef project,
        Delivery delivery,
        Trust trust,
        ActiveFlow activeFlow,
        BlockedValue blockedValue,
        EvidenceSummary evidence,
        DefectSummary defects,
        LearningSummary learning,
        List<SourceOfTruthEntry> sourceOfTruth,
        List<InvariantStatus> invariants,
        List<PromotionRule> promotionPolicy,
        List<FrontendTranslation> frontendTranslations,
        String recommendedNextAction
) {
    public record ProjectRef(UUID id, String name, String status, String repositoryName) {
    }

    public record Delivery(
            int totalFeatures,
            int completeFeatures,
            int totalPlannedTasks,
            int mergedPlannedTasks,
            double featureReadinessRatio,
            double mergedRatio,
            boolean decompositionComplete,
            String status,
            String headline
    ) {
    }

    public record Trust(
            double score,
            String level,
            List<String> positiveSignals,
            List<String> warnings
    ) {
    }

    public record ActiveFlow(
            long queued,
            long active,
            long review,
            long done,
            long failed,
            long pendingWishlist,
            long compilingWishlist,
            long openSessions,
            List<String> narrative
    ) {
    }

    public record BlockedValue(
            int count,
            String headline,
            List<Blocker> blockers
    ) {
    }

    public record Blocker(
            String type,
            String severity,
            String subjectId,
            String title,
            String reason
    ) {
    }

    public record EvidenceSummary(
            int mergedReviews,
            int openReviews,
            int pendingReviews,
            int failingReviews,
            int qualityGatePassed,
            int qualityGateFailed,
            int screenshots,
            List<EvidenceSignal> strongestSignals
    ) {
    }

    public record EvidenceSignal(
            String kind,
            int strength,
            String subject,
            String meaning
    ) {
    }

    public record DefectSummary(
            int recentDefects,
            List<DefectItem> items
    ) {
    }

    public record DefectItem(
            String severity,
            String category,
            String component,
            String defectType,
            String description
    ) {
    }

    public record LearningSummary(
            int candidateDefects,
            int invariantsObserved,
            List<String> unresolvedLearning
    ) {
    }

    public record SourceOfTruthEntry(
            String fact,
            String writeOwner,
            String operationalUse
    ) {
    }

    public record InvariantStatus(
            String key,
            String status,
            String statement,
            String evidence
    ) {
    }

    public record PromotionRule(
            String mode,
            String meaning
    ) {
    }

    public record FrontendTranslation(
            String backendFact,
            String userMeaning
    ) {
    }
}
