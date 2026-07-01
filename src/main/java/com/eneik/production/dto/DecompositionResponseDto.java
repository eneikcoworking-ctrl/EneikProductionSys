package com.eneik.production.dto;

import java.util.List;
import java.util.UUID;

public record DecompositionResponseDto(
    UUID requirementId,
    List<TaskShortDto> createdTasks
) {}
