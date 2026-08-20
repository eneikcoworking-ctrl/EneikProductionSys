-- 2026-08-20: Kaizen's identity lived on the read side only.
--
-- Measured: KAIZEN_PROPOSALS holds 347 rows carrying 10 distinct (category, target_component) pairs -
-- 34 rows of storage per unit of signal. getDeduplicatedProposals already keys on exactly that pair, so
-- the operator's view is correct while the table is not, and those rows are part of the store growth.
-- Charter invariant 4, idempotency, belongs at the write: a recurring finding must revise its row, not
-- insert a second one.
--
-- What this buys beyond storage, and it is the larger part:
--   * recurrence_count is a measure of severity that is invisible today, because every recurrence is
--     indistinguishable from a new problem;
--   * it makes an applied improvement REFUTABLE - after a micro-step is applied, a counter that keeps
--     rising says the improvement did not hold. A non-refutable improvement is not an improvement;
--   * it gives SDCA something to prove standardisation with: "the counter has not risen since X".
ALTER TABLE kaizen_proposals ADD COLUMN recurrence_count INT DEFAULT 1 NOT NULL;
ALTER TABLE kaizen_proposals ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

UPDATE kaizen_proposals SET last_seen_at = created_at WHERE last_seen_at IS NULL;
