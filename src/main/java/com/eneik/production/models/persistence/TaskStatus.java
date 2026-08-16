package com.eneik.production.models.persistence;

public enum TaskStatus {
    queued, claimed, in_progress, pending_review, review, done, failed, spike_completed, blocked
}
