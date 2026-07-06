package com.eneik.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountRequestDto(
        @NotBlank(message = "name is required")
        @Size(max = 255)
        String name,

        @NotBlank(message = "capabilities are required")
        String capabilities,

        @Size(max = 255)
        String githubUsername,

        String apiKey
) {
}
