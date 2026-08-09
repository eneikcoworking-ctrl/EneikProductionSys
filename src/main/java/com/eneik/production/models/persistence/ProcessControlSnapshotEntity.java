package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One u-chart data point (2026-08-01, Layer 1 of the unified Lean/TOC/Six Sigma system - see
 * docs/ENGINEERING_INVARIANTS_CHARTER.md and ProcessControlService). Subgroup = эпик (featureId),
 * sequenced by completion order WITHIN one project (sequenceIndex) - never by calendar date, never
 * across projects. Before this, SixSigmaAuditService computed DPMO on demand with no history; this is
 * the durable time series a u-chart needs to lock a Phase 1 baseline and detect Phase 2 drift against it.
 */
@Entity
@Table(name = "process_control_snapshots")
public class ProcessControlSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    @Column(name = "stream", nullable = false, length = 32)
    private String stream;

    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    @Column(name = "u_value", nullable = false)
    private double u;

    @Column(name = "n_opportunities", nullable = false)
    private long n;

    @Column(name = "defects", nullable = false)
    private long defects;

    @Column(name = "center_line", nullable = false)
    private double centerLine;

    @Column(name = "upper_control_limit", nullable = false)
    private double upperControlLimit;

    @Column(name = "lower_control_limit", nullable = false)
    private double lowerControlLimit;

    @Column(name = "phase", nullable = false, length = 16)
    private String phase;

    @Column(name = "out_of_control", nullable = false)
    private boolean outOfControl;

    @Column(name = "western_electric_signal", length = 64)
    private String westernElectricSignal;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    // 2026-08-07 (Kaizen audit follow-on): the epic's own sixSigmaMetric text (FeatureEntity.sixSigmaMetric,
    // an operator/compiler-authored "operational definition" of what quality means for this epic) - purely
    // descriptive, never read by any u-chart math here. Closes the loop the audit found broken: the text was
    // computed and shown in prompts/dashboards but never attached to a real measured stream.
    @Column(name = "six_sigma_metric_label", columnDefinition = "TEXT")
    private String sixSigmaMetricLabel;

    public String getSixSigmaMetricLabel() { return sixSigmaMetricLabel; }
    public void setSixSigmaMetricLabel(String sixSigmaMetricLabel) { this.sixSigmaMetricLabel = sixSigmaMetricLabel; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public int getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(int sequenceIndex) { this.sequenceIndex = sequenceIndex; }

    public double getU() { return u; }
    public void setU(double u) { this.u = u; }

    public long getN() { return n; }
    public void setN(long n) { this.n = n; }

    public long getDefects() { return defects; }
    public void setDefects(long defects) { this.defects = defects; }

    public double getCenterLine() { return centerLine; }
    public void setCenterLine(double centerLine) { this.centerLine = centerLine; }

    public double getUpperControlLimit() { return upperControlLimit; }
    public void setUpperControlLimit(double upperControlLimit) { this.upperControlLimit = upperControlLimit; }

    public double getLowerControlLimit() { return lowerControlLimit; }
    public void setLowerControlLimit(double lowerControlLimit) { this.lowerControlLimit = lowerControlLimit; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public boolean isOutOfControl() { return outOfControl; }
    public void setOutOfControl(boolean outOfControl) { this.outOfControl = outOfControl; }

    public String getWesternElectricSignal() { return westernElectricSignal; }
    public void setWesternElectricSignal(String westernElectricSignal) { this.westernElectricSignal = westernElectricSignal; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
