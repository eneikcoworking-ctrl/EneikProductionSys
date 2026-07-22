package com.eneik.production.dto.dashboard;

public record ProductReadinessDto(
        int totalFeatures,
        int completeFeatures,
        int totalPlannedTasks,
        int mergedPlannedTasks,
        double mergedRatio,
        boolean decompositionComplete,
        double falsificationThreshold,
        boolean falsificationEligible,
        String status
) {
}
