package com.eneik.production.dto;

import com.eneik.production.models.persistence.WishlistSource;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WishlistRequestDto(
    @NotNull UUID projectId,
    @NotNull WishlistSource source,
    String sourceRoleTag,
    @NotNull String content
) {}
