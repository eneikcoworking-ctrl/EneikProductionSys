package com.eneik.production.services.monitor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SystemProgressTracker} verifying ANTI_MIRROR_TELEMETRY (D013).
 * Progress tracker must start at null rather than assuming immediate "ok" without external deliverables.
 */
class SystemProgressTrackerTest {

    @Test
    void startsWithoutProgress() {
        SystemProgressTracker tracker = new SystemProgressTracker();

        assertThat(tracker.hasProgress()).isFalse();
        assertThat(tracker.lastProgressAt()).isNull();
        assertThat(tracker.sinceLastProgress()).isEmpty();
        assertThat(tracker.startedAt()).isNotNull();
    }

    @Test
    void recordProgressSetsCurrentTimestamp() {
        SystemProgressTracker tracker = new SystemProgressTracker();

        tracker.recordProgress();

        assertThat(tracker.hasProgress()).isTrue();
        assertThat(tracker.lastProgressAt()).isNotNull();
        assertThat(tracker.sinceLastProgress()).isPresent();
        assertThat(tracker.sinceLastProgress().get()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void recordProgressWithSpecificInstant() {
        Instant past = Instant.now().minus(Duration.ofMinutes(15));
        SystemProgressTracker tracker = new SystemProgressTracker();

        tracker.recordProgress(past);

        assertThat(tracker.hasProgress()).isTrue();
        assertThat(tracker.lastProgressAt()).isEqualTo(past);
        assertThat(tracker.sinceLastProgress().get()).isGreaterThanOrEqualTo(Duration.ofMinutes(15));
    }
}
