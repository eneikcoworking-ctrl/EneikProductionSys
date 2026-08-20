package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per **transition** of a Charter invariant's status, never one per evaluation.
 *
 * 2026-08-20: `OperationalTruthService.invariants()` evaluates seven invariants every time it is called
 * and returns them in a DTO that only the dashboard controller reads. Confirmed across every reference
 * to `InvariantStatus`: nothing persisted them. With no stored previous value a move from `pass` to
 * `warn` is undetectable in principle - the factory computed its own refutation each cycle and forgot
 * it, so the one signal meaning "something this factory asserted about itself has stopped being true"
 * could not be acted on by anything.
 *
 * Writing only on change is deliberate and is the lesson from `KAIZEN_PROPOSALS`, measured the same day:
 * 347 rows carrying 10 distinct (category, target_component) pairs, because the write path had no
 * identity while the read path deduplicated. Charter invariant 4 belongs at the write. A repeated
 * evaluation of an unchanged status is not news and must leave no trace.
 *
 * Popper's asymmetry is the reason this table exists at all: a confirmation carries no information and
 * there are unboundedly many of them, while a refutation is exactly the event that says the system was
 * wrong. This table records only the second kind.
 */
@Entity
@Table(name = "invariant_status_changes")
public class InvariantStatusChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Null for a factory-wide invariant that is not scoped to one project. */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "invariant_key", nullable = false, length = 120)
    private String invariantKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** Null only for the first row ever recorded for this (project, invariant). */
    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    /** The invariant's own logical form, carried so a reader never has to look it up elsewhere. */
    @Column(name = "statement", length = 500)
    private String statement;

    @Column(name = "evidence", length = 2000)
    private String evidence;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getInvariantKey() { return invariantKey; }
    public void setInvariantKey(String invariantKey) { this.invariantKey = invariantKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
