package com.eneik.production.dto.tree;

public record HealthDto(
        long defects,
        long opportunities,
        long prConflictDefects,
        long prConflictOpportunities,
        double dpmo,
        double sigmaLevel
) {
}
