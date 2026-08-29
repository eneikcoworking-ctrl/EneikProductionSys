-- V126 (2026-08-29, plan §4.23 + §4.35): a brief refused on evidence nobody can read gets its budget back
-- once - the same correction V122 made, for the same reason, on the same rule.
--
-- §4.23 states the rule this repairs: a refusal whose reason nobody can read cannot ground an absorbing
-- verdict. §4.35 measured why no reason can be read - the rejecting condition was computed, sent to Jules
-- inside the correction message, printed to the log, and stored nowhere, so it lived exactly as long as the
-- process that decided it.
--
-- Measured 2026-08-29 16:57, live: the client's own brief (Moodle/LMS integration, restored by V125 an hour
-- earlier) stood at compile_attempts = 3 against COMPILE_ATTEMPT_BUDGET = 3 with last_compile_reached_at
-- set, which is decompositionRefused() - absorbing, never dispatched again. Its three carrier tasks
-- (d2c7bef0, b32d08f5, 3fb00ac5) all read "Dispatched to Jules": not one of the three refusals left a
-- readable trace, because all three ran on images built before the fix that persists the condition.
--
-- Guarded by the absence of that trace, so this is self-limiting rather than a licence to retry forever:
-- it only touches briefs of a project where no carrier records a rejection reason at all. From the next
-- round on, carriers do record one, this predicate stops matching, and a verdict reached with a readable
-- reason stands untouched - as §4.23 says it should.
UPDATE wishlist w
SET compile_attempts = 0
WHERE w.source = 'client'
  AND w.origin_wishlist_id IS NULL
  AND w.status = 'pending'
  AND w.compile_attempts > 0
  AND NOT EXISTS (
      SELECT 1 FROM tasks t
      WHERE t.project_id = w.project_id
        AND (CAST(t.jules_dispatch_status AS VARCHAR) LIKE 'Plan rejected%'
             OR CAST(t.jules_dispatch_status AS VARCHAR) LIKE 'No usable plan%'));
