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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.active;

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
    public String getLinearProjectStatus() { return linearProjectStatus; }
    public void setLinearProjectStatus(String linearProjectStatus) { this.linearProjectStatus = linearProjectStatus; }
    public String getLinearProjectId() { return linearProjectId; }
    public void setLinearProjectId(String linearProjectId) { this.linearProjectId = linearProjectId; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public String getFactoryStatus() { return factoryStatus; }
    public void setFactoryStatus(String factoryStatus) { this.factoryStatus = factoryStatus; }
    public String getFactoryReport() { return factoryReport; }
    public void setFactoryReport(String factoryReport) { this.factoryReport = factoryReport; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
