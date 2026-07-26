package com.eneik.production.dto.dashboard;

import java.util.UUID;

/**
 * Dashboard visibility for the "N of M not merged" gate + a "blocked for N hours" signal (2026-07-25,
 * operator directive after a session spent manually SQL-hunting for exactly this kind of hidden blocker).
 * Two distinct reasons a task shows up here - see reason: "stale_in_progress" (non-terminal status, no
 * active claim, hasn't moved in over the threshold) or "done_not_reached_main" (task's own status says
 * done, but ClientDeliverableReadinessService.reachedMain says its work never actually landed in main -
 * the exact shape of the 44%-orphaned-PR incident this dashboard field was built to make visible).
 */
public record BlockedItemDto(
        UUID id,
        String title,
        String status,
        double hoursSinceUpdate,
        String roleTag,
        String reason
) {
}
