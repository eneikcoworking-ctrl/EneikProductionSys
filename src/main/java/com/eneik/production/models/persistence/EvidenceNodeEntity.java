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
 * Shared evidence-node projection feeding the coherence engine (Thagard/ECHO, EvidenceCoherenceService).
 * Each independent signal source (ProcessControlService's statistical findings, the falsification auditor's
 * code-integrity findings, KaizenService's proposals, and - since V82 - AutoMergeService's detected
 * Jules-status-vs-GitHub-reality mismatches) additively writes one row here alongside its own existing
 * write - this is a new parallel projection for cross-source reconciliation, not a replacement for any
 * existing table. Exactly one of the 5 source id columns is set per row (enforced by a DB CHECK constraint,
 * V79 + widened in V82) - polarity is assigned structurally per source type, never guessed by an LLM.
 */
@Entity
@Table(name = "evidence_nodes")
public class EvidenceNodeEntity {

    public enum Polarity {
        NEGATIVE_FINDING,
        POSITIVE_CONFIRMATION,
        NEUTRAL_OBSERVATION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Nullable: some evidence (e.g. a global/factory-wide Kaizen systemic finding with no single owning
    // project) isn't scoped to any project - never a fabricated sentinel value.
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "feature_id")
    private UUID featureId;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(nullable = false, length = 24)
    private String polarity;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "defect_journal_id")
    private UUID defectJournalId;

    @Column(name = "code_integrity_finding_id")
    private UUID codeIntegrityFindingId;

    @Column(name = "kaizen_proposal_id")
    private String kaizenProposalId;

    @Column(name = "gemini_finding_id")
    private UUID geminiFindingId;

    @Column(name = "operational_reality_finding_id")
    private UUID operationalRealityFindingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getFeatureId() { return featureId; }
    public void setFeatureId(UUID featureId) { this.featureId = featureId; }

    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }

    public Polarity getPolarity() { return Polarity.valueOf(polarity); }
    public void setPolarity(Polarity polarity) { this.polarity = polarity.name(); }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public UUID getDefectJournalId() { return defectJournalId; }
    public void setDefectJournalId(UUID defectJournalId) { this.defectJournalId = defectJournalId; }

    public UUID getCodeIntegrityFindingId() { return codeIntegrityFindingId; }
    public void setCodeIntegrityFindingId(UUID codeIntegrityFindingId) { this.codeIntegrityFindingId = codeIntegrityFindingId; }

    public String getKaizenProposalId() { return kaizenProposalId; }
    public void setKaizenProposalId(String kaizenProposalId) { this.kaizenProposalId = kaizenProposalId; }

    public UUID getGeminiFindingId() { return geminiFindingId; }
    public void setGeminiFindingId(UUID geminiFindingId) { this.geminiFindingId = geminiFindingId; }

    public UUID getOperationalRealityFindingId() { return operationalRealityFindingId; }
    public void setOperationalRealityFindingId(UUID operationalRealityFindingId) { this.operationalRealityFindingId = operationalRealityFindingId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Which of the 5 exclusive source columns is set - used by EvidenceCoherenceService's AGM entrenchment
     * (distinct-sourceType corroboration count), never computed by guessing from summaryText. */
    public String sourceType() {
        if (defectJournalId != null) return "DEFECT_JOURNAL";
        if (codeIntegrityFindingId != null) return "CODE_INTEGRITY_FINDING";
        if (kaizenProposalId != null) return "KAIZEN_PROPOSAL";
        if (geminiFindingId != null) return "GEMINI_FINDING";
        if (operationalRealityFindingId != null) return "OPERATIONAL_REALITY_FINDING";
        throw new IllegalStateException("EvidenceNodeEntity " + id + " has no source set - violates chk_evidence_nodes_exactly_one_source");
    }
}
