package com.eneik.production.dto;

import com.eneik.production.models.persistence.WishlistItemType;

public record WishlistItemRequestDto(
        String text,
        WishlistItemType type,
        String sourceRoleTag
) {
}
