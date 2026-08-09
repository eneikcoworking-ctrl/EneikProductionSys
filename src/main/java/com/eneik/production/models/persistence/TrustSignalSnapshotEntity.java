package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trust_signal_snapshots")
public class TrustSignalSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt = Instant.now();

    @Column(name = "merged_reviews", nullable = false)
    private int mergedReviews;

    @Column(name = "quality_gate_passed", nullable = false)
    private int qualityGatePassed;

    @Column(name = "quality_gate_failed", nullable = false)
    private int qualityGateFailed;

    @Column(name = "failing_reviews", nullable = false)
    private int failingReviews;

    @Column(name = "duplicate_content", nullable = false)
    private boolean duplicateContent;

    @Column(name = "recent_defects_count", nullable = false)
    private int recentDefectsCount;

    @Column(name = "computed_score", nullable = false)
    private double computedScore;

    @Column(name = "eventual_outcome", length = 32)
    private String eventualOutcome;

    @Column(name = "outcome_recorded_at")
    private Instant outcomeRecordedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Instant getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(Instant snapshotAt) { this.snapshotAt = snapshotAt; }

    public int getMergedReviews() { return mergedReviews; }
    public void setMergedReviews(int mergedReviews) { this.mergedReviews = mergedReviews; }

    public int getQualityGatePassed() { return qualityGatePassed; }
    public void setQualityGatePassed(int qualityGatePassed) { this.qualityGatePassed = qualityGatePassed; }

    public int getQualityGateFailed() { return qualityGateFailed; }
    public void setQualityGateFailed(int qualityGateFailed) { this.qualityGateFailed = qualityGateFailed; }

    public int getFailingReviews() { return failingReviews; }
    public void setFailingReviews(int failingReviews) { this.failingReviews = failingReviews; }

    public boolean isDuplicateContent() { return duplicateContent; }
    public void setDuplicateContent(boolean duplicateContent) { this.duplicateContent = duplicateContent; }

    public int getRecentDefectsCount() { return recentDefectsCount; }
    public void setRecentDefectsCount(int recentDefectsCount) { this.recentDefectsCount = recentDefectsCount; }

    public double getComputedScore() { return computedScore; }
    public void setComputedScore(double computedScore) { this.computedScore = computedScore; }

    public String getEventualOutcome() { return eventualOutcome; }
    public void setEventualOutcome(String eventualOutcome) { this.eventualOutcome = eventualOutcome; }

    public Instant getOutcomeRecordedAt() { return outcomeRecordedAt; }
    public void setOutcomeRecordedAt(Instant outcomeRecordedAt) { this.outcomeRecordedAt = outcomeRecordedAt; }
}
