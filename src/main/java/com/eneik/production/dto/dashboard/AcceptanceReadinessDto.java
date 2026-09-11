package com.eneik.production.dto.dashboard;

import java.util.List;

public record AcceptanceReadinessDto(
    String readiness, // "ready", "not ready", "unknown"
    Boolean allTasksDone,
    Boolean allQualityGatesPassed,
    Boolean allPrsMerged,
    Boolean githubAccessHealthy,
    Boolean clientAcceptanceWitnessed,
    List<String> unmetConditions,
    String statusLabel,
    String uiColorToken,
    String kanoRecommendation
) {
    public AcceptanceReadinessDto(
            String readiness,
            Boolean allTasksDone,
            Boolean allQualityGatesPassed,
            Boolean allPrsMerged,
            Boolean githubAccessHealthy,
            List<String> unmetConditions,
            String statusLabel,
            String uiColorToken,
            String kanoRecommendation
    ) {
        this(readiness, allTasksDone, allQualityGatesPassed, allPrsMerged, githubAccessHealthy, null, unmetConditions, statusLabel, uiColorToken, kanoRecommendation);
    }
}
