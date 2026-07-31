package com.eneik.production.services.operational;

public enum OperationalAction {
    OBSERVE,
    ADD_WISHLIST,
    ORCHESTRATE,
    RECOVER_FAILED_FRONTIER,
    DISPATCH_QUEUED_TASKS,
    DISPATCH_REVIEW_TASKS,
    CHECK_COVERAGE_AUDITS,
    RUN_PROJECT_AUDIT_PIPELINE,
    MERGE_PR,
    SYNC_GITHUB,
    CLEANUP_TERMINAL_PROJECT,
    // Gemini observer's own action toolkit (2026-07-30) - routed through this same gate so she can never
    // act outside what the enforced Flow Core already permits everyone else. triggerFalsificationRun
    // deliberately reuses RUN_PROJECT_AUDIT_PIPELINE below rather than getting its own case - it IS that
    // same audit pipeline, just pulled forward on her request instead of waiting for its own cron.
    NUDGE_SESSION,
    DISMISS_WISHLIST,
    ABANDON_CONFLICT,
    BOOST_PRIORITY
}
