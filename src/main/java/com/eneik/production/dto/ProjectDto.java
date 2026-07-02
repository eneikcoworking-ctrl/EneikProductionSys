package com.eneik.production.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
    UUID id,
    String name,
    String repoUrl,
    String status,
    Instant createdAt,
    Instant acceptedAt,
    long accountCount
) {}
