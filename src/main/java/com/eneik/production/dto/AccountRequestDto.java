package com.eneik.production.dto;

public record AccountRequestDto(
        String name,
        String capabilities,
        String githubUsername,
        String apiKey,
        String provider
) {
    public AccountRequestDto(String name, String capabilities, String githubUsername, String apiKey) {
        this(name, capabilities, githubUsername, apiKey, "GOOGLE_JULES");
    }
}
