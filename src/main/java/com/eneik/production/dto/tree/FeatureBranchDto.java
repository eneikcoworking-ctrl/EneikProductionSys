package com.eneik.production.dto.tree;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureBranchDto(
        UUID featureId,
        String title,
        UUID rootWishlistId,
        Instant createdAt,
        String kanoClass,
        String cynefinDomain,
        String tocConstraintRef,
        boolean complete,
        int codeProducingItemCount,
        int mergedItemCount,
        boolean livePulse,
        HealthDto health,
        List<AnnotationDto> annotations
) {
}
