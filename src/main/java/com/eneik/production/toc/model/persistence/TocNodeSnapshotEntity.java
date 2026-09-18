package com.eneik.production.toc.model.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable time series snapshot of a TOC Node execution state and Welford variance metrics.
 *
 * Grounding:
 * - DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT (D010 Data lineage loss):
 *   Identity and metrics of execution steps remain continuous across process restarts.
 *   Prevents cold-start single-sample bottleneck distortions where constraint decisions
 *   are made on a single observation rather than historical empirical evidence.
 */
@Entity
@Table(name = "toc_node_snapshots")
public class TocNodeSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "node_name", nullable = false, length = 128)
    private String nodeName;

    @Column(name = "completed_count", nullable = false)
    private long completedCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "total_duration_nanos", nullable = false)
    private long totalDurationNanos;

    @Column(name = "mean_duration_ms", nullable = false)
    private double meanDurationMs;

    @Column(name = "m2_ms", nullable = false)
    private double m2Ms;

    @Column(name = "std_dev_ms", nullable = false)
    private double stdDevMs;

    @Column(name = "dynamic_timeout_limit_ms", nullable = false)
    private double dynamicTimeoutLimitMs;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt = Instant.now();

    public TocNodeSnapshotEntity() {
    }

    public TocNodeSnapshotEntity(String nodeName,
                                 long completedCount,
                                 long errorCount,
                                 long totalDurationNanos,
                                 double meanDurationMs,
                                 double m2Ms,
                                 double stdDevMs,
                                 double dynamicTimeoutLimitMs,
                                 Instant snapshotAt) {
        this.nodeName = nodeName;
        this.completedCount = completedCount;
        this.errorCount = errorCount;
        this.totalDurationNanos = totalDurationNanos;
        this.meanDurationMs = meanDurationMs;
        this.m2Ms = m2Ms;
        this.stdDevMs = stdDevMs;
        this.dynamicTimeoutLimitMs = dynamicTimeoutLimitMs;
        this.snapshotAt = snapshotAt != null ? snapshotAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getTotalDurationNanos() {
        return totalDurationNanos;
    }

    public void setTotalDurationNanos(long totalDurationNanos) {
        this.totalDurationNanos = totalDurationNanos;
    }

    public double getMeanDurationMs() {
        return meanDurationMs;
    }

    public void setMeanDurationMs(double meanDurationMs) {
        this.meanDurationMs = meanDurationMs;
    }

    public double getM2Ms() {
        return m2Ms;
    }

    public void setM2Ms(double m2Ms) {
        this.m2Ms = m2Ms;
    }

    public double getStdDevMs() {
        return stdDevMs;
    }

    public void setStdDevMs(double stdDevMs) {
        this.stdDevMs = stdDevMs;
    }

    public double getDynamicTimeoutLimitMs() {
        return dynamicTimeoutLimitMs;
    }

    public void setDynamicTimeoutLimitMs(double dynamicTimeoutLimitMs) {
        this.dynamicTimeoutLimitMs = dynamicTimeoutLimitMs;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(Instant snapshotAt) {
        this.snapshotAt = snapshotAt;
    }
}
