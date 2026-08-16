package com.eneik.production.models.persistence;

/**
 * Represents the production status of a greeting in the Lean pipeline.
 */
public enum Status {
    /** Task received but not yet started. */
    RECEIVED,
    /** Task is currently being processed. */
    IN_PROGRESS,
    /** Task has been successfully completed. */
    COMPLETED,
    /** Task is stuck due to internal or external dependencies. */
    BLOCKED
}
