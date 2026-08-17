package com.eneik.production.models.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A real, detected mismatch between a JulesSessionEntity's self-reported status and the actual GitHub PR
 * reality (2026-08-06 incident: session stuck at "running" while a real, open, mergeable PR sat unreconciled
 * for 90+ minutes, invisible to both the merge pipeline and Gemini's observer). Written by
 * AutoMergeService.reconcileOpenGitHubPullRequests whenever it detects and corrects such a divergence -
 * feeds EvidenceCoherenceService (Thagard/ECHO) as a 5th evidence source, so this class of problem is
 * visible through the same coherence graph every other signal already flows through, not a bolted-on
 * special case.
 */
@Entity
@Table(name = "operational_reality_findings")
public class OperationalRealityFindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /**
     * The session whose self-reported status disagreed with reality, when the disagreement was found that
     * way. Nullable since V103: a session is one KIND of record, not the essence of the claim. The claim is
     * that the record disagrees with reality, and a task can assert `done` having never been dispatched at
     * all - no session, no PR, no work - which is the shape of the live blocking instance f163e834
     * "Runtime Contract 8becdc01" that no producer in the factory could express while this column was
     * NOT NULL. Requiring it made a property of the detector into a property of the fact.
     */
    @Column(name = "jules_session_id")
    private UUID julesSessionId;

    @Column(name = "expected_status", nullable = false, length = 32)
    private String expectedStatus;

    @Column(name = "actual_github_state", nullable = false, length = 64)
    private String actualGithubState;

    @Column(name = "pr_url", columnDefinition = "TEXT")
    private String prUrl;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public UUID getJulesSessionId() { return julesSessionId; }
    public void setJulesSessionId(UUID julesSessionId) { this.julesSessionId = julesSessionId; }

    public String getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(String expectedStatus) { this.expectedStatus = expectedStatus; }

    public String getActualGithubState() { return actualGithubState; }
    public void setActualGithubState(String actualGithubState) { this.actualGithubState = actualGithubState; }

    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
