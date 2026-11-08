package com.eneik.production.services.design.sovereign.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain aggregate root managing synthesis session state transitions, round iteration limits,
 * captured empirical viewport screenshots, and deterministic audit loop completion.
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
    private volatile List<ViewportCapture> latestCaptures = List.of();
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

    public List<ViewportCapture> getLatestCaptures() {
        return latestCaptures;
    }

    public synchronized void recordCaptures(List<ViewportCapture> captures) {
        this.latestCaptures = captures != null ? List.copyOf(captures) : List.of();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public synchronized void transitionToSynthesizing() {
        SynthesisStatus current = status.get();
        if (current != SynthesisStatus.INITIALIZED && current != SynthesisStatus.REFINING) {
            throw new IllegalStateException("Cannot transition to SYNTHESIZING from status " + current);
        }
        if (!status.compareAndSet(current, SynthesisStatus.SYNTHESIZING)) {
            throw new IllegalStateException("Concurrent status modification detected during transition to SYNTHESIZING");
        }
    }

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

    public synchronized SynthesisStatus evaluateAuditAndTransition(VisionAuditReport report) {
        Objects.requireNonNull(report, "VisionAuditReport cannot be null");
        SynthesisStatus current = status.get();
        if (current != SynthesisStatus.AUDITING) {
            throw new IllegalStateException("Cannot evaluate audit report from status " + current);
        }

        this.latestAuditReport = report;

        if (report.isApproved()) {
            status.set(SynthesisStatus.COMPLETED_APPROVED);
            return SynthesisStatus.COMPLETED_APPROVED;
        }

        if (currentRound < MAX_AUDIT_ROUNDS) {
            currentRound++;
            status.set(SynthesisStatus.REFINING);
            return SynthesisStatus.REFINING;
        } else {
            status.set(SynthesisStatus.COMPLETED_FALLBACK);
            return SynthesisStatus.COMPLETED_FALLBACK;
        }
    }

    public synchronized void transitionToFailed(String reason) {
        this.failureReason = (reason != null && !reason.isBlank()) ? reason : "Unknown synthesis failure";
        this.status.set(SynthesisStatus.FAILED);
    }
}
