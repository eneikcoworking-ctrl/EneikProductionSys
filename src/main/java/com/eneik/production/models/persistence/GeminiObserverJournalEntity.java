package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One cycle's self-written journal entry for {@code GeminiProjectObserverService} (2026-07-25 redesign,
 * operator directive: "она вообще должна была создать свой отдельный лог, а не работать с твоим! ты должен
 * оставаться внешним наблюдателем, а джемини справляется сама"). Gemini authors this entry herself each
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
}
