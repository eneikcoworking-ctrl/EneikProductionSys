package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One durable, DB-backed log line for one project (2026-07-26 restoration - operator directive: "лог
 * проекта должен независеть от деплоев" - survives container recreation, unlike stdout/docker logs, which
 * is the whole point). Written by {@link com.eneik.production.services.logging.DurableProjectLogAppender}
 * via {@link com.eneik.production.services.logging.ProjectLogFlushQueue}, read by
 * {@link com.eneik.production.services.ProjectEventLogService}. Never consumed by Gemini directly - this is
 * for external agents/operator forensic access only (see GeminiProjectObserverService's own evidence
 * snapshot + journal for what she actually sees).
 */
@Entity
@Table(name = "project_event_log")
public class ProjectEventLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(nullable = false, length = 255)
    private String logger;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String message;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getLogger() { return logger; }
    public void setLogger(String logger) { this.logger = logger; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
