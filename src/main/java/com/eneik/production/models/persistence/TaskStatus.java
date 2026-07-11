package com.eneik.production.models.persistence;

public enum TaskStatus {
    queued, claimed, in_progress, review, done, failed, spike_completed, blocked, needs_human_review
}
