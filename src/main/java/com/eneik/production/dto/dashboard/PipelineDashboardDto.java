package com.eneik.production.dto.dashboard;

public record PipelineDashboardDto(
    long queued,
    long claimed,
    long in_progress,
    long review,
    long done,
    long failed
) {}
