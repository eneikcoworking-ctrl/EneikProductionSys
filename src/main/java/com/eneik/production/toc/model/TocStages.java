package com.eneik.production.toc.model;

/**
 * Canonical names for factory pipeline stages instrumented by TOC Sentinel.
 * Enforces INUS_FACTOR_CHECK (D007, Mackie 1974):
 * Ensures the TOC execution graph contains multiple candidate nodes across the
 * production pipeline, avoiding single-sensor bottleneck bias where the primary
 * constraint was predetermined by sensor placement rather than comparative measurement.
 */
public final class TocStages {
    public static final String AUTOMERGE_PROCESSING = "AUTOMERGE_PROCESSING";
    public static final String ORCHESTRATION_PROCESSING = "ORCHESTRATION_PROCESSING";
    public static final String ORCHESTRATE_PROCESSING = "ORCHESTRATE_PROCESSING";
    public static final String DISPATCH_PROCESSING = "DISPATCH_PROCESSING";
    public static final String REVIEW_DISPATCH_PROCESSING = "REVIEW_DISPATCH_PROCESSING";
    public static final String JULES_DISPATCH_PROCESSING = "JULES_DISPATCH_PROCESSING";

    private TocStages() {
    }
}
