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
    BOOST_PRIORITY,
    // 2026-08-01: revives a specific failed task via PlannedWorkRecoveryService.resumeTask's atomic,
    // rate-limited resume path - never a raw status edit. Confirmed live gap: GeminiObserverActionService
    // had no way to act on a failed task at all, so this exact class of bug (task d9f35f4b/529e5252 on
    // test-fortieth) sat unaddressed until an operator noticed and intervened by hand.
    REVIVE_FAILED_TASK,
    // 2026-08-03: closes+requeues a PR whose owning session ended up terminal (cancelled/closed_terminal_
    // task/failed) while the PR itself is still open on GitHub - see BranchGarbageCollectorService.
    // findOrphanedPrCandidates. Confirmed live gap (task 074efcb3/PR#38 on test-forty-first): a session
    // that did real, successful work got collaterally cancelled by an unrelated cleanup and its PR sat
    // orphaned, invisible to every status-filtered sweep, until an operator noticed and Claude traced it by
    // hand.
    RESOLVE_ORPHANED_PR,
    // 2026-08-07: collapses a confirmed-duplicate QUEUED task (same payload.slice_title as an earlier,
    // still-live task) by moving it to `blocked` - the one action that can actually clear
    // BLOCKED_BY_DUPLICATE_CONTENT, since nothing in that hard-stop state can otherwise reach a terminal
    // status (dispatch itself is what's denied). Confirmed live gap (test-forty-third): the observer could
    // already SEE and report a "DUPLICATE TASK WARNING", but had no tool that could act on it, so the
    // project sat fully halted for hours with no autonomous recovery path.
    COLLAPSE_DUPLICATE_TASK,
    // 2026-08-08: re-opens a feature thread's closeout PR after it was closed by something other than the
    // real, bounded 3-attempt conflict-resolution escalation (see BranchGarbageCollectorService's Case A
    // fix, same incident) - confirmed live gap, test-forty-third: real, never-yet-merged work sat
    // permanently stuck with no autonomous recovery path once its closeout PR was wrongly closed.
    RETRY_FEATURE_CLOSEOUT,
    // 2026-08-09 (Phase 0, client runtime observability plan): one-shot check, only meaningful once a
    // project first reaches DELIVERED - reuses the exact same gate as CHECK_COVERAGE_AUDITS/
    // RUN_PROJECT_AUDIT_PIPELINE (deliberately not excluded from the DELIVERED state itself, unlike
    // ORCHESTRATE).
    CHECK_LAUNCHABILITY,
    // 2026-08-09 (Phase 1, client runtime observability plan): the recurring (adaptive-cadence, never
    // schedule-hardcoded) observation step, distinct from the one-shot CHECK_LAUNCHABILITY above.
    OBSERVE_CLIENT_RUNTIME,
    // 2026-08-11: retires a wedged PersistentWorkerSessionEntity by hand - confirmed live incident, worker
    // 924b2c9f stayed batch-in-flight for 14+ hours after its carrier session died, because isIdleAndFresh's
    // isBatchInFlight() check short-circuits before the age/cycle-count rotation safety net is ever reached.
    // closeSessionForTerminalTask now retires the worker automatically the moment its carrier task goes
    // terminal, but that only covers ONE way a worker can get wedged; this gives the observer the same
    // power for any other shape of the same underlying desync she independently notices.
    RETIRE_STUCK_WORKER
}
