package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jules_sessions")
public class JulesSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID taskId;

    @Column(length = 128)
    private String externalSessionId;

    @Column(nullable = false)
    private String status = "queued";

    @Column(length = 256)
    private String prUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant lastStatusCheckAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public String getExternalSessionId() { return externalSessionId; }
    public void setExternalSessionId(String externalSessionId) { this.externalSessionId = externalSessionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastStatusCheckAt() { return lastStatusCheckAt; }
    public void setLastStatusCheckAt(Instant lastStatusCheckAt) { this.lastStatusCheckAt = lastStatusCheckAt; }
}
