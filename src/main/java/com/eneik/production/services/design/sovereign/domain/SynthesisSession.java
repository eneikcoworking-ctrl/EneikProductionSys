package com.eneik.production.services.design.sovereign.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain aggregate root managing synthesis session state transitions, round iteration limits,
 * and deterministic audit loop completion.
 */
public class SynthesisSession {

    public static final int MAX_AUDIT_ROUNDS = 3;

    private final UUID id;
    private final ProductArchetype archetype;
    private final Instant createdAt;
    private final AtomicReference<SynthesisStatus> status;

    private volatile int currentRound;
    private volatile SynthesizedDomDocument latestDomDocument;
    private volatile VisionAuditReport latestAuditReport;
    private volatile String failureReason;

    public SynthesisSession(UUID id, ProductArchetype archetype, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.archetype = Objects.requireNonNull(archetype, "archetype cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.status = new AtomicReference<>(SynthesisStatus.INITIALIZED);
        this.currentRound = 0;
    }

    public static SynthesisSession create(ProductArchetype archetype, Instant createdAt) {
        return new SynthesisSession(UUID.randomUUID(), archetype, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public ProductArchetype getArchetype() {
        return archetype;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SynthesisStatus getStatus() {
        return status.get();
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public SynthesizedDomDocument getLatestDomDocument() {
        return latestDomDocument;
    }

    public VisionAuditReport getLatestAuditReport() {
        return latestAuditReport;
    }

    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Atomically transitions session from INITIALIZED or REFINING into SYNTHESIZING.
     */
    public synchronized void transitionToSynthesizing() {
        SynthesisStatus current = status.get();
        if (current != SynthesisStatus.INITIALIZED && current != SynthesisStatus.REFINING) {
            throw new IllegalStateException("Cannot transition to SYNTHESIZING from status " + current);
        }
        if (!status.compareAndSet(current, SynthesisStatus.SYNTHESIZING)) {
            throw new IllegalStateException("Concurrent status modification detected during transition to SYNTHESIZING");
        }
    }

    /**
     * Updates synthesized DOM document and transitions state to AUDITING.
     */
    public synchronized void recordDomAndTransitionToAuditing(SynthesizedDomDocument document) {
        Objects.requireNonNull(document, "SynthesizedDomDocument cannot be null");
        SynthesisStatus current = status.get();
        if (current != SynthesisStatus.SYNTHESIZING) {
            throw new IllegalStateException("Cannot transition to AUDITING from status " + current);
        }
        this.latestDomDocument = document;
        if (!status.compareAndSet(current, SynthesisStatus.AUDITING)) {
            throw new IllegalStateException("Concurrent status modification detected during transition to AUDITING");
        }
    }

    /**
     * Evaluates vision audit report and performs deterministic transition:
     * - COMPLETED_APPROVED if report passes score and deontic gates.
     * - REFINING if score < 9.2 and currentRound < 3 (increments round).
     * - COMPLETED_FALLBACK if currentRound == 3 and score < 9.2 (terminates loop).
     */
    public synchronized SynthesisStatus evaluateAuditAndTransition(VisionAuditReport report) {
        Objects.requireNonNull(report, "VisionAuditReport cannot be null");
        SynthesisStatus current = status.get();
        if (current != SynthesisStatus.AUDITING) {
            throw new IllegalStateException("Cannot evaluate audit report from status " + current);
        }

        this.latestAuditReport = report;

        SynthesisStatus nextStatus;
        if (report.isApproved()) {
            nextStatus = SynthesisStatus.COMPLETED_APPROVED;
        } else if (currentRound < MAX_AUDIT_ROUNDS) {
            currentRound++;
            nextStatus = SynthesisStatus.REFINING;
        } else {
            nextStatus = SynthesisStatus.COMPLETED_FALLBACK;
        }

        if (!status.compareAndSet(current, nextStatus)) {
            throw new IllegalStateException("Concurrent status modification detected during audit evaluation");
        }

        return nextStatus;
    }

    /**
     * Transitions session to FAILED state with failure reason.
     */
    public synchronized void transitionToFailed(String reason) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        SynthesisStatus current = status.get();
        if (current.isTerminal()) {
            throw new IllegalStateException("Cannot transition to FAILED from terminal status " + current);
        }
        this.failureReason = reason;
        status.set(SynthesisStatus.FAILED);
    }
}
