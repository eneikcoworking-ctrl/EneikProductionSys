package com.eneik.production.dto;

public record CollaboratorDto(
        String username,
        String status,
        String githubStatus,
        String detail,
        String statusLabel,
        String uiColorToken
) {
}
