package com.eneik.production.dto;

import java.util.List;
import java.util.UUID;

public record OrchestrationResultDto(
        UUID projectId,
        int processedWishlistItems,
        List<TaskShortDto> createdTasks,
        String message
) {
}
