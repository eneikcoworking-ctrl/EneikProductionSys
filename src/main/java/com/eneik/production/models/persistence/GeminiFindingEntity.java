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
 * An assertion made by the Gemini observer, persisted as an assertion.
 *
 * The referent {@code evidence_nodes.gemini_finding_id} was created for in V79 and never given - V79's own
 * comment records why: "Gemini findings become WishlistEntity today". Nothing ever set the column, so her
 * claims entered the evidence graph only as what was DONE about them (a wishlist, or a KAIZEN_PROPOSAL),
 * and {@link EvidenceNodeEntity#sourceType()} - which reads whichever FK is set - therefore typed her
 * testimony by the channel that stored it rather than by where it came from.
 *
 * Why that matters, measured 2026-08-17: 10 of the 26 KAIZEN_PROPOSAL nodes in her own 24-hour read window
 * were her own prior findings. EvidenceCoherenceService's sourceReliability() keys on sourceType, so her
 * prose inherited the reliability earned by measurement-derived proposals (FactorySelfHealthService's
 * database-bloat finding is typed identically), and distinctHistoricallyCorroboratingSourceTypes() counted
 * her restatement as a second independent source corroborating her own position. A claim that manufactures
 * its own corroboration strengthens regardless of the world - which is why a claim true of 1 task in 33 was
 * asserted as "nearly all", with no individually invalid inference anywhere in the chain.
 *
 * The evidence algebra in OPERATIONAL_MATH_ARCHITECTURE.md already draws the distinction the schema could
 * not: agent prose is strength 1, "intent or claim, not delivery", and "agent claims are never final
 * evidence". Charter invariant 12 requires independent verification rather than self-attestation. This
 * entity is the adapter that preserves the category boundary those two rules assume - the proof obligation
 * DZHON_OSTIN_02_CATEGORY_ERROR_SCAN states as "point to the type, schema or adapter that preserves the
 * category boundary."
 *
 * projectId is nullable on purpose: a platform-scope finding is about EneikProductionSys and belongs to no
 * client project, the same actualist rule already applied to factory-scope Kaizen proposals.
 */
@Entity
@Table(name = "gemini_findings")
public class GeminiFindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Null for a platform-scope finding - the factory is not a project. */
    @Column(name = "project_id")
    private UUID projectId;

    /** Her own scope self-classification, recorded as she declared it, never overwritten. */
    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
