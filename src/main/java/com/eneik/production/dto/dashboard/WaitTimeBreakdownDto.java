package com.eneik.production.dto.dashboard;

import java.util.List;

/**
 * Lean-waste task-waiting-time metric (2026-07-23, operator directive): breaks down currently queued
 * tasks by WHY they are waiting, not just how many exist. Deliberately excludes time spent claimed/in an
 * active Jules session - that time is trusted, never counted as waste (see TaskWaitTimeService).
 */
public record WaitTimeBreakdownDto(
    List<BucketDto> buckets,
    long totalQueued
) {
    public record BucketDto(
        String reason,
        long count,
        double avgWaitingMinutes,
        long oldestWaitingMinutes
    ) {}
}
