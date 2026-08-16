package com.eneik.production.dto;

import java.util.UUID;

public record JulesConfigDto(
    UUID id,
    String name,
    String apiKeyMasked,
    boolean enabled
) {}
