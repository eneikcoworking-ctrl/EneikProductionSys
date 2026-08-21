-- 2026-08-21: a refutation the factory has already judged must never be judged twice.
--
-- V105 made the factory's own refutations durable - a row per TRANSITION of a Charter invariant, never
-- per evaluation. Its own comment names what it is for: "the wake signal for factory-level judgment".
-- Nothing consumed that signal; findByObservedAtAfterOrderByObservedAtDesc had no callers.
--
-- FactoryJudgmentService is that consumer, and it calls a paid external model. What it must not do is
-- re-pay for a transition it has already ruled on. A timestamp cursor ("everything after T") cannot
-- express that: rows are written by whichever cycle observes the transition, so a row bearing an
-- observed_at earlier than the cursor can be inserted after the cursor has moved past it, and would be
-- skipped forever. Marking the ROW is exact where marking the CLOCK is approximate.
--
-- Charter invariant 4 - idempotency belongs at the write - the same reason V105 records only changes.
ALTER TABLE invariant_status_changes
    ADD COLUMN judged_at TIMESTAMP WITH TIME ZONE;

-- Deliberately NOT backfilled. The seven rows already in this table are real refutations of this
-- factory's own assertions (done_is_not_delivery moving to `warn` among them) and were never ruled on
-- by anything. Backfilling them as judged would discard exactly the evidence the agent exists to read.
-- FactoryJudgmentService bounds its own first run instead (judgment-agent.max-per-cycle), so a backlog
-- is drained over several cycles rather than in one burst.
CREATE INDEX idx_invariant_changes_unjudged ON invariant_status_changes (judged_at, observed_at);
