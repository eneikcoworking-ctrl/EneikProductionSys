-- V131 (2026-08-30, plan 4.45): the two client requirements the grouper merged away re-enter the flow.
--
-- V130 tried to do this and matched nothing. Its predicate demanded that the epic have no task AT ALL,
-- and this epic has two - both `failed` (V129 recorded them the day before: "Data Subject Rights and
-- Privacy Consent  0 done, 2 FAILED"). Failed tasks are exactly the case where the requirement is still
-- unmet, so requiring their absence excluded the only rows this was written for. Corrected here rather
-- than edited in place: V130 has already run on the live database.
--
-- What happened, from the durable project log, verbatim:
--
--     2026-08-30T00:33:34.567Z  Dismissed duplicate wishlist item b8dca98a (merged into 2c3442ef)
--     2026-08-30T00:33:34.570Z  Dismissed duplicate wishlist item b8f32565 (merged into 2c3442ef)
--
-- All three are slices of ONE client brief (8aff0d75). The survivor belongs to the epic "Self-Service
-- Account Recovery" and now carries a three-part content and a semicolon-joined jtbd covering account
-- recovery, privacy compliance and privacy QA. The two dismissed rows are the client's Must-Be
-- requirement for data export, erasure and consent, and for those to be verified. Nothing was delivered,
-- nothing failed on their own account, and their epic - left with no planned item - then held the whole
-- project in DECOMPOSING (see plan 4.45 for the cycle).
--
-- The predicate that produced the merge is fixed forward in the same commit; this returns what it already
-- took. What survives is the REQUIREMENT, not the task identity - the rule V129 stands on.
--
-- Narrow on purpose: only slices of a brief the CLIENT wrote, which produced no task of their own, and
-- whose epic has produced no task that is anything other than `failed`.
UPDATE wishlist w
SET status = 'pending', compile_attempts = 0
WHERE w.status = 'dismissed'
  AND w.compiled_by_role IS NOT NULL
  AND w.feature_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id)
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.feature_id = w.feature_id AND t.status <> 'failed')
  AND EXISTS (
      SELECT 1 FROM features f
      JOIN wishlist root ON root.id = f.root_wishlist_id
      WHERE f.id = w.feature_id
        AND f.dismissed_at IS NULL
        AND root.source = 'client');
