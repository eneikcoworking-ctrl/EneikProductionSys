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
}
