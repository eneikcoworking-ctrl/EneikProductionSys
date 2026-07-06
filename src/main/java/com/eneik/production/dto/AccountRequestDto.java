package com.eneik.production.dto;

public record AccountRequestDto(
        String name,
        String capabilities,
        String githubUsername,
        String apiKey
) {
}
