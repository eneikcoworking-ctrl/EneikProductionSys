package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted review concern (2026-08-01, u₄ of the unified Six Sigma layer - see
 * docs/ENGINEERING_INVARIANTS_CHARTER.md). Before this, a reviewer's non-blocking concern
 * ("unverified auth header spoofing risk" vs "px vs rem") only ever reached a log line
 * (JulesDispatchService: "Poka-yoke: recorded non-blocking review concern...") - unqueryable, gone after log
 * rotation, and with no severity distinction at all. featureId is the u-chart subgroup (эпик); never a
 * calendar-date bucket or cross-project aggregate.
 */
@Entity
@Table(name = "review_concerns")
public class ReviewConcernEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "root_cause_pattern_id")
    private Integer rootCausePatternId;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getRootCausePatternId() { return rootCausePatternId; }
    public void setRootCausePatternId(Integer rootCausePatternId) { this.rootCausePatternId = rootCausePatternId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
