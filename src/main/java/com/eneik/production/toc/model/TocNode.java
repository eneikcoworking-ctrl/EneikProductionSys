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
        if (completedCount.get() < 2) {
            return defaultFloorMs;
        }
        return Math.max(defaultFloorMs, meanDurationMs + (sensitivityMultiplier * stdDevMs));
    }
}
