package com.eneik.production.models.persistence;

public enum TaskStatus {
    queued, claimed, in_progress, pending_review, review, done, failed, spike_completed, blocked;

    /**
     * Law 20 / Invariant S2: terminal(τ) ⟺ status(τ) ∈ {done, failed, spike_completed}.
     * Terminal tasks represent concluded attempts and their status is irreversible.
     */
    public boolean isTerminal() {
        return this == done || this == failed || this == spike_completed;
    }
}
