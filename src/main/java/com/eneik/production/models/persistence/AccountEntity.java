package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.idle;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(nullable = false)
    private String capabilities; // Comma-separated strings

    private Instant lastHeartbeat;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "current_project_id")
    private UUID currentProjectId;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sessions_dispatched_today", nullable = false)
    private int sessionsDispatchedToday = 0;

    // Per-account override for the global jules.max-concurrent-sessions-per-account default - null means
    // "use the global default". Lets one account (e.g. a higher-tier Jules account) run more concurrent
    // sessions than the rest of the pool without changing the shared default for everyone.
    @Column(name = "max_concurrent_sessions")
    private Integer maxConcurrentSessions;

    // 2026-08-01 (operator: "падение должно быть редким" - failures should become rare): before this,
    // ContinuousOrchestrationService.recoverStaleBlockedAccounts retried every api_blocked account on a
    // single FIXED cooldown (30 min) forever, regardless of how many times in a row it had just failed -
    // confirmed live, one account failed FAILED_PRECONDITION 9-13 times over a single day, every ~15-45
    // minutes, because nothing ever slowed the retry cadence down. This counter drives an exponential
    // backoff instead: incremented every time THIS account gets marked api_blocked, reset to 0 the moment
    // it successfully creates a session again (real proof of recovery, not just elapsed time).
    @Column(name = "consecutive_api_block_count", nullable = false)
    private int consecutiveApiBlockCount = 0;

    // Engineering invariant #15 (2026-08-08, live incident: jules.max-daily-sessions-per-account's
    // hardcoded default of 15 throttled every account identically, well below at least one account's real
    // Jules quota, while dispatch starved for hours). NULL = never falsified yet, capacity queries fall
    // back to the global config default. AccountHealthService.reportDispatchOutcome is the sole writer:
    // grows this (Popperian bold conjecture, BARCAN-TAG-06 philosopher #1) on a real SUCCESS that reaches
    // the current ceiling, shrinks it ONLY on a real DAILY_LIMIT rejection from Jules (the one event that
    // counts as falsification) - never adjusted from our own unverified belief, matching the Bayesian
    // revision-by-evidence discipline already used by EvidenceCoherenceService's Bovens-Hartmann pillar
    // (BARCAN-TAG-04 philosopher #8).
    @Column(name = "estimated_daily_capacity")
    private Integer estimatedDailyCapacity;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) {
        if (status != this.status) {
            this.statusChangedAt = Instant.now();
        }
        this.status = status;
    }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getCurrentProjectId() { return currentProjectId; }
    public void setCurrentProjectId(UUID currentProjectId) { this.currentProjectId = currentProjectId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getSessionsDispatchedToday() { return sessionsDispatchedToday; }
    public void setSessionsDispatchedToday(int sessionsDispatchedToday) { this.sessionsDispatchedToday = sessionsDispatchedToday; }
    public Integer getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public void setMaxConcurrentSessions(Integer maxConcurrentSessions) { this.maxConcurrentSessions = maxConcurrentSessions; }
    public int getConsecutiveApiBlockCount() { return consecutiveApiBlockCount; }
    public void setConsecutiveApiBlockCount(int consecutiveApiBlockCount) { this.consecutiveApiBlockCount = consecutiveApiBlockCount; }
    public Integer getEstimatedDailyCapacity() { return estimatedDailyCapacity; }
    public void setEstimatedDailyCapacity(Integer estimatedDailyCapacity) { this.estimatedDailyCapacity = estimatedDailyCapacity; }
}
