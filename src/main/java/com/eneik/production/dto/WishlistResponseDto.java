package com.eneik.production.dto;

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
    Instant createdAt
) {}
