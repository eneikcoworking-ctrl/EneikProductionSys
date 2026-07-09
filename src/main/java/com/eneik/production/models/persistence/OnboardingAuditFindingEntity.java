package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_audit_findings")
public class OnboardingAuditFindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ProjectEntity project;

    @Column(name = "role_tag", nullable = false, length = 64)
    private String roleTag;

    @Column(nullable = false, length = 32)
    private String severity; // 'critical'|'major'|'minor'

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "finding_text", nullable = false, columnDefinition = "CLOB")
    private String findingText;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public String getRoleTag() { return roleTag; }
    public void setRoleTag(String roleTag) { this.roleTag = roleTag; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getFindingText() { return findingText; }
    public void setFindingText(String findingText) { this.findingText = findingText; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
