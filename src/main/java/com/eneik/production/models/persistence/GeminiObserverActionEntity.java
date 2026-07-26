package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Independently-checkable audit row for a real action the observer took (2026-07-26, operator directive:
 * "даем ей все полномочия - кроме кода"). Never trust her journal prose alone as the record of what she
 * did - this table is written by {@code GeminiObserverActionService} itself, only after the underlying
 * operation actually ran, so it reflects what really happened, not what she claims happened.
 */
@Entity
@Table(name = "gemini_observer_actions")
public class GeminiObserverActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String tool;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(columnDefinition = "CLOB")
    private String reason;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(length = 512)
    private String detail;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
