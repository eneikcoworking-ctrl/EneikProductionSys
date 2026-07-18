package com.eneik.production.services.monitor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory heartbeat of genuine factory forward progress (a successful Jules dispatch, a successful
 * merge) - deliberately NOT written on every 60s orchestration tick, only at points that represent
 * real output. A stall here (idle capacity + no progress for the configured window) is what
 * distinguishes "quiet because there's nothing to do" from "quiet because something is broken", which
 * is exactly what circuit-breaker bugs like activity_log_overflow made impossible to see during the
 * first unattended run.
 *
 * Single JVM, single instance (this app is not horizontally scaled), so in-memory is sufficient - no
 * migration or settings-table entry needed. Initialized to app-start time so a fresh restart isn't
 * immediately flagged as stalled.
 */
@Service
public class SystemProgressTracker {
    private final AtomicReference<Instant> lastProgressAt = new AtomicReference<>(Instant.now());

    public void recordProgress() {
        lastProgressAt.set(Instant.now());
    }

    public Instant lastProgressAt() {
        return lastProgressAt.get();
    }

    public Duration sinceLastProgress() {
        return Duration.between(lastProgressAt.get(), Instant.now());
    }
}
