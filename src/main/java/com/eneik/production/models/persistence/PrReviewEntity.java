package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pr_reviews")
public class PrReviewEntity {
    public static final String APPROVAL_TOKEN = "CORE ARCHITECTURE VERIFIED. APPROVED.";
    public static final String REJECTION_PREFIX = "REVIEW REJECTED";

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

    @Column(name = "has_code")
    private Boolean hasCode;

    // Dashboard-number correctness (2026-07-24): which branch this PR actually merged into - "main" or a
    // feature-thread branch (see FeatureThreadEntity). null = predates this fix, or local/mock-merge mode.
    // See ClientDeliverableReadinessService.reachedMain - a task's work only really "shipped" once this is
    // main, or the owning feature's thread has itself since closed into main.
    @Column(name = "base_ref", length = 256)
    private String baseRef;

    // The real GitHub PR number, captured at the same moment prUrl is set (never regexed from the URL
    // string after the fact - see the setPrUrl call sites, the number is already a local value there).
    // Nullable: predates this field, or a review was created before its PR number was ever known.
    @Column(name = "pr_number")
    private Integer prNumber;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getJulesSessionId() { return julesSessionId; }
    public void setJulesSessionId(UUID julesSessionId) { this.julesSessionId = julesSessionId; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public boolean isTerminal() {
        return "closed_unmerged".equalsIgnoreCase(ciStatus) || Boolean.TRUE.equals(merged);
    }

    public String getCiStatus() { return ciStatus; }
    public void setCiStatus(String ciStatus) {
        if ("closed_unmerged".equalsIgnoreCase(this.ciStatus)
                && !"closed_unmerged".equalsIgnoreCase(ciStatus)) {
            // Law 20 / Invariant S2: terminal review outcome is irreversible
            return;
        }
        this.ciStatus = ciStatus;
    }
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
    public Boolean getHasCode() { return hasCode; }
    public void setHasCode(Boolean hasCode) { this.hasCode = hasCode; }
    public String getBaseRef() { return baseRef; }
    public void setBaseRef(String baseRef) { this.baseRef = baseRef; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public boolean isApproved() {
        return diffSummary != null && diffSummary.contains(APPROVAL_TOKEN);
    }

    public boolean isRejected() {
        return diffSummary != null && diffSummary.startsWith(REJECTION_PREFIX);
    }
}
