package com.eneik.production.dto.tree;

import com.eneik.production.models.persistence.WishlistStatus;

import java.time.Instant;
import java.util.UUID;

public record SeedDto(
        UUID wishlistId,
        String content,
        WishlistStatus status,
        Instant createdAt,
        UUID featureId
) {
}
