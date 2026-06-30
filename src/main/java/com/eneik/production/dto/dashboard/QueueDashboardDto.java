package com.eneik.production.dto.dashboard;

import java.util.List;

public record QueueDashboardDto(
    List<TagCountDto> byTag,
    long totalQueued
) {
    public record TagCountDto(
        String tag,
        long count,
        long oldestWaitingMinutes
    ) {}
}
