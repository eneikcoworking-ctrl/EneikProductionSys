package com.eneik.production.dto;

import com.eneik.production.models.persistence.WishlistItemStatus;
import com.eneik.production.models.persistence.WishlistItemType;
import java.time.Instant;
import java.util.UUID;

public record WishlistItemDto(
        UUID id,
        UUID projectId,
        String text,
        WishlistItemType type,
        WishlistItemStatus status,
        String sourceRoleTag,
        Instant createdAt
) {
}
