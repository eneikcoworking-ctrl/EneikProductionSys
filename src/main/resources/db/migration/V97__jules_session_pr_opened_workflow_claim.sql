-- V97: atomic mutual-exclusion claim for handlePrOpenedWorkflow (2026-08-14, bug-hunt sweep). Closes a
-- genuine double-processing race: two concurrent invocations of the same session's pr_opened completion
-- (AutoMergeService's reconciliation sweep racing pollStatus's own trigger, or two reconciliation passes
-- overlapping) could both read a completion handler's "already claimed?" check (e.g.
-- completePhilosophicalAudit's claimService.hasActiveClaim) as false before either one's later write
-- landed, both applying the same critiques/violations/merge-record twice. NULL means unclaimed/available
-- for a fresh attempt (including legitimate crash-recovery replay via reconcileStrandedPrOpenedWorkflows);
-- non-null means a session's completion is currently being processed.
ALTER TABLE jules_sessions ADD COLUMN pr_opened_workflow_claimed_at TIMESTAMP;
