package com.eneik.production.kaizen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence for {@link KaizenProposal} - added 2026-08-05 to close a real gap: proposals used to live
 * only in KaizenService's in-memory ConcurrentHashMap, wiped on every backend restart with no trace beyond a
 * log line. This entity is a pure storage mapping; the rest of the codebase (KaizenController,
 * ProjectTreeService) keeps using the plain {@link KaizenProposal} domain object unchanged - KaizenService
 * converts at its own storage boundary.
 */
@Entity
@Table(name = "kaizen_proposals")
public class KaizenProposalEntity {
    @Id
    private String id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "target_component")
    private String targetComponent;

    /**
     * How many times this same finding has been raised. 2026-08-20: before this the write path had no
     * identity while the read path deduplicated by (category, target_component), so 347 rows carried 10
     * real problems and a recurrence was indistinguishable from a new problem. The count is what makes an
     * applied improvement refutable - if it keeps rising after a micro-step was applied, the improvement
     * did not hold.
     */
    @Column(name = "recurrence_count", nullable = false)
    private int recurrenceCount = 1;

    @Column(name = "last_seen_at")
    private java.time.Instant lastSeenAt;

    @Column(name = "action_description", columnDefinition = "TEXT")
    private String actionDescription;

    @Column(name = "expected_gain_percent")
    private Double expectedGainPercent;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "baseline_metric")
    private Double baselineMetric;

    @Column(name = "post_metric")
    private Double postMetric;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTargetComponent() { return targetComponent; }
    public void setTargetComponent(String targetComponent) { this.targetComponent = targetComponent; }

    public int getRecurrenceCount() { return recurrenceCount; }
    public void setRecurrenceCount(int recurrenceCount) { this.recurrenceCount = recurrenceCount; }

    public java.time.Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(java.time.Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }

    public Double getExpectedGainPercent() { return expectedGainPercent; }
    public void setExpectedGainPercent(Double expectedGainPercent) { this.expectedGainPercent = expectedGainPercent; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getBaselineMetric() { return baselineMetric; }
    public void setBaselineMetric(Double baselineMetric) { this.baselineMetric = baselineMetric; }

    public Double getPostMetric() { return postMetric; }
    public void setPostMetric(Double postMetric) { this.postMetric = postMetric; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public static KaizenProposalEntity fromDomain(KaizenProposal p) {
        KaizenProposalEntity e = new KaizenProposalEntity();
        e.setId(p.getId());
        e.setTitle(p.getTitle());
        e.setCategory(p.getCategory().name());
        e.setTargetComponent(p.getTargetComponent());
        e.setActionDescription(p.getActionDescription());
        e.setExpectedGainPercent(p.getExpectedGainPercent());
        e.setProjectId(p.getProjectId());
        e.setProjectName(p.getProjectName());
        e.setStatus(p.getStatus().name());
        e.setBaselineMetric(p.getBaselineMetric());
        e.setPostMetric(p.getPostMetric());
        e.setCreatedAt(p.getCreatedAt());
        e.setAppliedAt(p.getAppliedAt());
        return e;
    }

    public KaizenProposal toDomain() {
        KaizenProposal p = new KaizenProposal(id, title, KaizenProposal.KaizenCategory.valueOf(category),
                targetComponent, actionDescription,
                expectedGainPercent == null ? 0.0 : expectedGainPercent, projectId, projectName, createdAt);
        p.setStatus(KaizenProposal.ProposalStatus.valueOf(status));
        p.setBaselineMetric(baselineMetric);
        p.setPostMetric(postMetric);
        p.setAppliedAt(appliedAt);
        return p;
    }
}
