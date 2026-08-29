package com.eneik.production.toc.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an atomic step / operation node in the execution state machine graph.
 * Tracks throughput, execution statistics, queue length, and constraint status.
 */
public class TocNode {
    private final String name;
    private final AtomicLong inFlightCount = new AtomicLong(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalDurationNanos = new AtomicLong(0);

    // Welford's algorithm online variance calculation
    private volatile double meanDurationMs = 0.0;
    private volatile double m2Ms = 0.0;
    private volatile double stdDevMs = 0.0;

    private volatile double throughput = 0.0; // completed per sec
    private volatile double utilization = 0.0; // lambda * mu

    private volatile boolean isStallBottleneck = false;
    private volatile boolean isPrimaryConstraint = false;

    public TocNode(String name) {
        this.name = name;
    }

    public synchronized void recordExecution(long durationNanos, boolean success) {
        if (!success) {
            errorCount.incrementAndGet();
        }
        long count = completedCount.incrementAndGet();
        totalDurationNanos.addAndGet(durationNanos);

        double durationMs = durationNanos / 1_000_000.0;

        // Online mean and variance (Welford's method)
        double delta = durationMs - meanDurationMs;
        meanDurationMs += delta / count;
        double delta2 = durationMs - meanDurationMs;
        m2Ms += delta * delta2;

        if (count > 1) {
            double variance = m2Ms / (count - 1);
            stdDevMs = Math.sqrt(Math.max(0.0, variance));
        } else {
            stdDevMs = 0.0;
        }
    }

    public String getName() {
        return name;
    }

    public long getInFlightCount() {
        return inFlightCount.get();
    }

    public void incrementInFlight() {
        inFlightCount.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlightCount.updateAndGet(current -> Math.max(0, current - 1));
    }

    public long getCompletedCount() {
        return completedCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public double getMeanDurationMs() {
        return meanDurationMs;
    }

    public double getStdDevMs() {
        return stdDevMs;
    }

    public double getThroughput() {
        return throughput;
    }

    public void setThroughput(double throughput) {
        this.throughput = throughput;
    }

    public double getUtilization() {
        return utilization;
    }

    public void setUtilization(double utilization) {
        this.utilization = utilization;
    }

    public boolean isStallBottleneck() {
        return isStallBottleneck;
    }

    public void setStallBottleneck(boolean stallBottleneck) {
        isStallBottleneck = stallBottleneck;
    }

    public boolean isPrimaryConstraint() {
        return isPrimaryConstraint;
    }

    public void setPrimaryConstraint(boolean primaryConstraint) {
        isPrimaryConstraint = primaryConstraint;
    }

    public double getDynamicTimeoutLimitMs(double sensitivityMultiplier, double defaultFloorMs) {
        // Fall back to the floor only when NOTHING has been observed (2026-08-29). The condition was
        // `< 2`, and with exactly one completed pass it threw away a mean it already had: the counters are
        // Welford, so at count one the mean IS that observation and the deviation is legitimately zero.
        // Measured that day on the live circuit: AUTOMERGE_PROCESSING logged `Dynamic Limit: 5000.00 ms
        // (mu: 41146.00 ms, stdDev: 0.00 ms)` five times and flagged dwells of 11950, 11989, 13952 and
        // 13989 ms as stalls - a third of the node's own measured mean. That node calls GitHub; ten
        // seconds there is normal. Eleven false stall flags in twenty minutes, the loudest warning in the
        // log.
        //
        // With one sample stdDev is zero, so the limit becomes max(floor, mean) - the mean itself. No
        // number is introduced; one is removed from the path where evidence already exists, and
        // defaultFloorMs goes on being what its name says, a floor. Charter invariant 15: a limit standing
        // for an unknown duration is revised by observation, not held at an unrevised constant.
        if (completedCount.get() == 0) {
            return defaultFloorMs;
        }
        return Math.max(defaultFloorMs, meanDurationMs + (sensitivityMultiplier * stdDevMs));
    }
}
