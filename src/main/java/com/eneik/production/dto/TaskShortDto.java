package com.eneik.production.dto;

import java.util.UUID;

public record TaskShortDto(
    UUID id,
    String tag,
    String description
) {}
