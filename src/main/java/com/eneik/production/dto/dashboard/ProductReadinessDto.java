package com.eneik.production.dto.dashboard;

import java.util.List;

/**
 * What this record counts is ASSEMBLY: features closed and planned tasks merged. It does not measure
 * whether the delivered product runs, answers, or fits its purpose - those are the other two axes and are
 * never merged with this one.
 *
 * 2026-08-23 (F5): the name says product readiness and the numbers say assembly, and on 2026-08-23 that
 * gap was read aloud to the operator as though 0.78 meant the product was three-quarters ready while its
 * compose declared no application at all. A name asserting something the value does not carry is a claim
 * like any other and is false here (ACP-106). Renaming the record touches the controller and the operator
 * UI and is not in this batch, so until it happens the record states its own subject in a field that
 * travels with every response.
 */
public record ProductReadinessDto(
        int totalFeatures,
        int completeFeatures,
        int totalPlannedTasks,
        int mergedPlannedTasks,
        double mergedRatio,
        double featureReadinessRatio,
        boolean decompositionComplete,
        double falsificationThreshold,
        boolean falsificationEligible,
        String status,
        List<BlockedItemDto> blockedItems,
        String measures
) {
}
