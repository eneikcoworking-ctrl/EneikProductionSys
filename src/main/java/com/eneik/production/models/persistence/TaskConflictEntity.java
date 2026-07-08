package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_conflicts")
public class TaskConflictEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @Column(name = "pr_url", length = 256)
    private String prUrl;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "conflict_type", nullable = false, length = 32)
    private String conflictType;

    @Column(name = "resolution_attempts", nullable = false)
    private int resolutionAttempts = 0;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_status", nullable = false, length = 32)
    private String resolutionStatus = "pending";

    @Column(name = "conflicting_files", columnDefinition = "TEXT")
    private String conflictingFiles;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public TaskEntity getTask() { return task; }
    public void setTask(TaskEntity task) { this.task = task; }

    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    public int getResolutionAttempts() { return resolutionAttempts; }
    public void setResolutionAttempts(int resolutionAttempts) { this.resolutionAttempts = resolutionAttempts; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public String getConflictingFiles() { return conflictingFiles; }
    public void setConflictingFiles(String conflictingFiles) { this.conflictingFiles = conflictingFiles; }
}
