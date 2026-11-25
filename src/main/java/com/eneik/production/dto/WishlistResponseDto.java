package com.eneik.production.dto;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import java.time.Instant;
import java.util.UUID;

public record WishlistResponseDto(
    UUID id,
    UUID projectId,
    WishlistSource source,
    String sourceRoleTag,
    String content,
    WishlistStatus status,
    Instant createdAt,
    UUID featureId,
    UUID mergedIntoWishlistId,
    String consolidatedFromIds,
    boolean consolidationConflict
) {
    public WishlistResponseDto(
        UUID id,
        UUID projectId,
        WishlistSource source,
        String sourceRoleTag,
        String content,
        WishlistStatus status,
        Instant createdAt,
        UUID featureId
    ) {
        this(id, projectId, source, sourceRoleTag, content, status, createdAt, featureId, null, null, false);
    }

    public static WishlistResponseDto fromEntity(WishlistEntity entity) {
        return new WishlistResponseDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getSource(),
                entity.getSourceRoleTag(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getFeatureId(),
                entity.getMergedIntoWishlistId(),
                entity.getConsolidatedFromIds(),
                entity.isConsolidationConflict()
        );
    }
}
