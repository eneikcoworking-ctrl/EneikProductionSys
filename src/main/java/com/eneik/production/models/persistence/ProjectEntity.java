package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String repositoryName;

    @Column(name = "repo_url")
    private String repoUrl;

    private String repositoryUrl;

    private String linearProjectKey;

    @Column(name = "github_repository_status", length = 512)
    private String githubRepositoryStatus;

    @Column(name = "github_repository_id", length = 128)
    private String githubRepositoryId;

    // Atomic Flyway-version reservation counter (operator directive 2026-07-24, after two Data Schema
    // tasks born in the same decomposition burst both independently guessed V1 and collided on main). Null
    // until the first BARCAN-TAG-08 task in this project reserves a number - see
    // TechnicalLeadCompiler.reserveNextFlywayVersion, which lazily seeds it from the real repo state on
    // first use rather than assuming migrations start at 1.
    @Column(name = "next_flyway_version")
    private Integer nextFlywayVersion;

    @Column(name = "linear_project_status", length = 512)
    private String linearProjectStatus;

    @Column(name = "linear_project_id", length = 128)
    private String linearProjectId;

    @Column(name = "workspace_path", length = 512)
    private String workspacePath;

    @Column(name = "factory_status", length = 64)
    private String factoryStatus;

    @Column(name = "factory_report", columnDefinition = "TEXT")
    private String factoryReport;

    /**
     * Which markets this product actually serves, comma-separated ISO codes - "DE", "DE,US", "RU".
     *
     * Closes F19. The regulatory floor renders every duty the corpus holds for DE and US, and test-forty-
     * sixth was a Russian institution, so it was shown duties that cannot apply to it. A market is a fact
     * about the engagement that somebody knows; inferring it from the brief's wording would be the same
     * indicator-for-property substitution repaired everywhere else this week, so it is DECLARED here.
     *
     * Null means undeclared, and undeclared must keep rendering both DE and US. The asymmetry is the point:
     * showing a duty that does not apply costs wasted scope, omitting one that does costs a legal hole, and
     * those are not the same size of mistake.
     */
    @Column(name = "target_markets", length = 64)
    private String targetMarkets;

    // 2026-08-09 (Phase 0, client runtime observability plan): set once ProductLaunchabilityService has
    // checked whether this project has a documented way to run itself locally, so the check happens
    // exactly once per project (on the first Continuous Orchestration tick after it reaches "delivered"),
    // never re-fetched from GitHub on every tick forever.
    @Column(name = "launchability_checked_at")
    private java.time.Instant launchabilityCheckedAt;

    // 2026-08-11 (client runtime observability plan, bounded live-preview window): the periodic Stage 4
    // launch+health-check cycle used to tear the client stack down immediately after checking it - a real
    // dashboard link or a philosophical audit's live-fetch would always find it already dead. Instead of
    // tearing down instantly, ClientRuntimeObservabilityService now leaves a successful launch running and
    // records when/where here; anything still within the configured idle window (see
    // client-runtime-observability.live-preview-idle-minutes) can treat it as genuinely live. The NEXT
    // tick's reaper (same existing per-project tick, never a new cron) tears it down once the window
    // expires - never left running indefinitely.
    @Column(name = "last_runtime_preview_launched_at")
    private java.time.Instant lastRuntimePreviewLaunchedAt;

    @Column(name = "last_runtime_preview_port")
    private Integer lastRuntimePreviewPort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.active;

    @Column(name = "onboarding_mode", nullable = false)
    private String onboardingMode = "greenfield";

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch = "main";

    @Column(name = "baseline_commit_sha")
    private String baselineCommitSha;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant acceptedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getLinearProjectKey() { return linearProjectKey; }
    public void setLinearProjectKey(String linearProjectKey) { this.linearProjectKey = linearProjectKey; }
    public String getGithubRepositoryStatus() { return githubRepositoryStatus; }
    public void setGithubRepositoryStatus(String githubRepositoryStatus) { this.githubRepositoryStatus = githubRepositoryStatus; }
    public String getGithubRepositoryId() { return githubRepositoryId; }
    public void setGithubRepositoryId(String githubRepositoryId) { this.githubRepositoryId = githubRepositoryId; }
    public Integer getNextFlywayVersion() { return nextFlywayVersion; }
    public void setNextFlywayVersion(Integer nextFlywayVersion) { this.nextFlywayVersion = nextFlywayVersion; }
    public String getLinearProjectStatus() { return linearProjectStatus; }
    public void setLinearProjectStatus(String linearProjectStatus) { this.linearProjectStatus = linearProjectStatus; }
    public String getLinearProjectId() { return linearProjectId; }
    public void setLinearProjectId(String linearProjectId) { this.linearProjectId = linearProjectId; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public String getFactoryStatus() { return factoryStatus; }
    public void setFactoryStatus(String factoryStatus) { this.factoryStatus = factoryStatus; }

    public java.time.Instant getLaunchabilityCheckedAt() { return launchabilityCheckedAt; }
    public void setLaunchabilityCheckedAt(java.time.Instant launchabilityCheckedAt) { this.launchabilityCheckedAt = launchabilityCheckedAt; }
    public java.time.Instant getLastRuntimePreviewLaunchedAt() { return lastRuntimePreviewLaunchedAt; }
    public void setLastRuntimePreviewLaunchedAt(java.time.Instant lastRuntimePreviewLaunchedAt) { this.lastRuntimePreviewLaunchedAt = lastRuntimePreviewLaunchedAt; }
    public Integer getLastRuntimePreviewPort() { return lastRuntimePreviewPort; }
    public void setLastRuntimePreviewPort(Integer lastRuntimePreviewPort) { this.lastRuntimePreviewPort = lastRuntimePreviewPort; }
    public String getFactoryReport() { return factoryReport; }
    public void setFactoryReport(String factoryReport) { this.factoryReport = factoryReport; }
    public String getTargetMarkets() { return targetMarkets; }
    public void setTargetMarkets(String targetMarkets) { this.targetMarkets = targetMarkets; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public String getOnboardingMode() { return onboardingMode; }
    public void setOnboardingMode(String onboardingMode) { this.onboardingMode = onboardingMode; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getBaselineCommitSha() { return baselineCommitSha; }
    public void setBaselineCommitSha(String baselineCommitSha) { this.baselineCommitSha = baselineCommitSha; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
