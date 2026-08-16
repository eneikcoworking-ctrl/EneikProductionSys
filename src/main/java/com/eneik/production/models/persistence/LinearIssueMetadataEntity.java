package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "linear_issue_metadata")
public class LinearIssueMetadataEntity {

    @Id
    private UUID taskId;

    private String linearIssueId;

    @Column(columnDefinition = "TEXT")
    private String blockers;

    @Column(name = "dod_text", columnDefinition = "TEXT")
    private String dodText;

    private String prUrl;

    private Instant lastSyncedAt;

    @OneToOne
    @MapsId
    @JoinColumn(name = "task_id")
    private TaskEntity task;

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getLinearIssueId() {
        return linearIssueId;
    }

    public void setLinearIssueId(String linearIssueId) {
        this.linearIssueId = linearIssueId;
    }

    public String getBlockers() {
        return blockers;
    }

    public void setBlockers(String blockers) {
        this.blockers = blockers;
    }

    public String getDodText() {
        return dodText;
    }

    public void setDodText(String dodText) {
        this.dodText = dodText;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public TaskEntity getTask() {
        return task;
    }

    public void setTask(TaskEntity task) {
        this.task = task;
        if (task != null) {
            this.taskId = task.getId();
        }
    }
}
