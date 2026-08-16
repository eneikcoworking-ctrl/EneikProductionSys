package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One cycle's self-written journal entry for {@code GeminiProjectObserverService} (2026-07-25 redesign,
 * operator directive: "she should have created her own separate log rather than working with yours! you must
 * remain an outside observer while Gemini manages on her own"). Gemini authors this entry herself each
 * cycle for her own cross-cycle continuity - it replaces the backend's internal Logback capture pipeline as
 * the observer's source of "what did I already know", so the backend stays a pure evidence-gatherer/external
 * observer rather than curating its own technical log as if it WERE the project.
 */
@Entity
@Table(name = "gemini_observer_journal")
public class GeminiObserverJournalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String entry;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount;

    // Nullable (2026-07-26 addition): older rows predate this column. Lets stagnation detection compare
    // this cycle's readiness against her own last few journal entries without a separate time-series table.
    @Column(name = "readiness_ratio")
    private Double readinessRatio;

    // 2026-07-30: true for a row Gemini actually authored (a real call happened); false for a cheap,
    // code-only marker written on a cycle that was skipped for cost reasons. Skip markers still record
    // readinessRatio and anomalyFingerprints so the stagnation/new-evidence math keeps accumulating real
    // history even during a long silent stretch - only her own continuity text (the journal prose shown
    // back to her, and "since my last visit") is restricted to real rows. Defaults true so pre-existing
    // rows (all of which were real, this column didn't exist before) read correctly without a backfill.
    @Column(name = "gemini_called", nullable = false)
    private boolean geminiCalled = true;

    // 2026-07-30: JSON array of "id:status" fingerprints for every stuck-task/stale-wishlist candidate
    // visible at this checkpoint. Comparing the current cycle's fingerprints against the most recent row's
    // (real OR skip) is what decides whether a candidate is genuinely new/changed, instead of a hardcoded
    // re-notify interval - lets a background item stay silent indefinitely once shown, until something
    // about it actually changes.
    @Column(name = "anomaly_fingerprints", columnDefinition = "CLOB")
    private String anomalyFingerprints;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getEntry() { return entry; }
    public void setEntry(String entry) { this.entry = entry; }
    public int getFindingsCount() { return findingsCount; }
    public void setFindingsCount(int findingsCount) { this.findingsCount = findingsCount; }
    public Double getReadinessRatio() { return readinessRatio; }
    public void setReadinessRatio(Double readinessRatio) { this.readinessRatio = readinessRatio; }
    public boolean isGeminiCalled() { return geminiCalled; }
    public void setGeminiCalled(boolean geminiCalled) { this.geminiCalled = geminiCalled; }
    public String getAnomalyFingerprints() { return anomalyFingerprints; }
    public void setAnomalyFingerprints(String anomalyFingerprints) { this.anomalyFingerprints = anomalyFingerprints; }
}
