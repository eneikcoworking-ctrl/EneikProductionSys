package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Live ownership ledger for the cross-эпик file-collision guard (2026-07-31): one row per (task, path) pair
 * recorded whenever a task's fileScope is finalized (TechnicalLeadCompiler.createAndSaveTask), plus one row
 * per path deterministically committed at project bootstrap (taskId=null, featureId=null - a global,
 * project-wide claim). Checked before a NEW task's fileScope is finalized so no эпик is ever assigned a path
 * another эпик - or bootstrap - already owns. See TechnicalLeadCompiler.applyCrossEpicCollisionGuard.
 */
@Entity
@Table(name = "project_file_claims")
public class ProjectFileClaimEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getFeatureId() {
        return featureId;
    }

    public void setFeatureId(UUID featureId) {
        this.featureId = featureId;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }
}
