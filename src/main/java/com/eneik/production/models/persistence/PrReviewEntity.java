package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pr_reviews")
public class PrReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID julesSessionId;

    @Column(nullable = false, length = 256)
    private String prUrl;

    @Column(nullable = false, length = 16)
    private String ciStatus;

    @Column(columnDefinition = "TEXT")
    private String diffSummary;

    private Integer linesChanged;
    private Integer filesChanged;
    private Boolean hasTestChanges;
    private Boolean touchesCriticalPath;

    @Column(nullable = false, length = 8)
    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String screenshotUrls;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Boolean merged = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getJulesSessionId() { return julesSessionId; }
    public void setJulesSessionId(UUID julesSessionId) { this.julesSessionId = julesSessionId; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public String getCiStatus() { return ciStatus; }
    public void setCiStatus(String ciStatus) { this.ciStatus = ciStatus; }
    public String getDiffSummary() { return diffSummary; }
    public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }
    public Integer getLinesChanged() { return linesChanged; }
    public void setLinesChanged(Integer linesChanged) { this.linesChanged = linesChanged; }
    public Integer getFilesChanged() { return filesChanged; }
    public void setFilesChanged(Integer filesChanged) { this.filesChanged = filesChanged; }
    public Boolean getHasTestChanges() { return hasTestChanges; }
    public void setHasTestChanges(Boolean hasTestChanges) { this.hasTestChanges = hasTestChanges; }
    public Boolean getTouchesCriticalPath() { return touchesCriticalPath; }
    public void setTouchesCriticalPath(Boolean touchesCriticalPath) { this.touchesCriticalPath = touchesCriticalPath; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getScreenshotUrls() { return screenshotUrls; }
    public void setScreenshotUrls(String screenshotUrls) { this.screenshotUrls = screenshotUrls; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Boolean getMerged() { return merged; }
    public void setMerged(Boolean merged) { this.merged = merged; }
}
