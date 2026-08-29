-- V123 (2026-08-29, plan §4.26): a conflict about a task that does not exist leaves the decision set.
--
-- Measured with the backend stopped, on a copy whose md5 was stable across two reads: 92 rows in
-- task_conflicts, ZERO of them joining to any row in tasks. All 92 were written between 2026-07-10 and
-- 2026-07-12; nothing has been written since, so the producer is not active. FK_TASK_CONFLICTS_TASK exists
-- and is enforced today - an insert with an unknown task_id was rejected by the live schema in the same
-- session - so these rows cannot have been orphaned under the constraint as it now stands. How they were
-- is not established, and is not guessed at here.
--
-- Two things follow from their presence, and both are the same invariant. SixSigmaAuditService counted
-- every one of them as a defect in both the numerator and the denominator of the factory's own quality
-- measure, forever: a conflict whose task cannot be found can never be resolved and can never be
-- attributed to a project or an epic. Charter invariant 8 - an element structurally incapable of reaching
-- a verdict must LEAVE the set the decision is made over. And §5.9 of the plan says where: once, in the
-- data, not as a filter re-applied by every reader - that mistake was made before and the defect returned
-- through the reader that had not been patched.
--
-- Same instrument as V117 and V122.
DELETE FROM task_conflicts
WHERE task_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.id = task_conflicts.task_id);
