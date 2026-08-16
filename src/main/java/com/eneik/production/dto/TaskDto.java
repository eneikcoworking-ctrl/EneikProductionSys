package com.eneik.production.dto;

import com.eneik.production.models.persistence.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record TaskDto(
    UUID id,
    String tag,
    String title,
    String description,
    TaskStatus status,
    JsonNode payload,
    String julesSessionName,
    String julesDispatchStatus,
    UUID dependsOn,
    boolean qualityGatePassed,
    int priority,
    String cynefinDomain
) {}
