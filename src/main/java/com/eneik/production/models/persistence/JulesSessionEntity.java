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

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "external_session_id", length = 128)
    private String externalSessionId;

    @Column(nullable = false, length = 24)
    private String status = "queued";

    @Column(name = "pr_url", length = 256)
    private String prUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_status_check_at")
    private Instant lastStatusCheckAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Lob
    @Column(name = "closure_reason")
    private String closureReason;

    // Distinct from updatedAt, which Hibernate bumps on every save regardless of real change (see
    // preUpdate() below) - lastProgressAt only moves on a genuine forward-progress signal (a real status
    // transition, or a newly-seen agent question), so staleness checks keyed on it can actually detect a
    // session that Jules keeps reporting "RUNNING" on while nothing real is happening.
    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    // Consecutive poll cycles where the session's activity log was too large to scan (activitiesOverflow).
    @Column(name = "blind_cycle_count", nullable = false)
    private int blindCycleCount = 0;

    // Count of deterministic "decide for yourself and make a PR" messages sent by forceUnblockOverflowedSessions.
    @Column(name = "forced_unblock_attempts", nullable = false)
    private int forcedUnblockAttempts = 0;

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

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }

    public Instant getLastProgressAt() { return lastProgressAt; }
    public void setLastProgressAt(Instant lastProgressAt) { this.lastProgressAt = lastProgressAt; }

    public int getBlindCycleCount() { return blindCycleCount; }
    public void setBlindCycleCount(int blindCycleCount) { this.blindCycleCount = blindCycleCount; }

    public int getForcedUnblockAttempts() { return forcedUnblockAttempts; }
    public void setForcedUnblockAttempts(int forcedUnblockAttempts) { this.forcedUnblockAttempts = forcedUnblockAttempts; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
