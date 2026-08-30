-- V130 (2026-08-30, plan 4.45): compiled slices of a client brief that were dismissed without ever
-- producing a task re-enter the flow.
--
-- V129 returned client requirements whose tasks ALL ended `failed`. It required at least one task to
-- exist, so it could not see the other half of the same loss: a slice discarded before any task was
-- created at all. Measured on the live circuit, test-fiftieth, 2026-08-30:
--
--     epic f3f8e658  "Data Subject Rights and Privacy Consent"   Must-Be, from the client's Moodle brief
--         slice b8dca98a  "Privacy Compliance Backend and Integration"          dismissed, no task
--         slice b8f32565  "QA: Verification of Data Subject Rights and Consent" dismissed, no task
--         codeProducingItemCount = 0, mergedItemCount = 0, no task of any status
--
-- The client asked for data export, erasure and consent, and for those to be verified. Nothing was
-- delivered, nothing failed, and nothing was recorded - the two rows simply stopped being work. Their
-- epic then held the whole project in DECOMPOSING, because an epic with no planned item pins
-- everyFeaturePlanned false forever (see plan 4.45 for the full cycle).
--
-- What survives is the REQUIREMENT, not the task identity - the same rule V129 stands on. Charter
-- invariant 8: an element that can still reach a verdict must not leave the decision set. These can: the
-- compiler works, the epic exists, and the client's own brief still names the scope.
--
-- Bounded by construction - a migration runs once - and narrow on purpose: only slices of a brief the
-- CLIENT wrote, which produced no task at all, and whose epic has produced no task at all either. An epic
-- with any task is covered work and is not touched here.
UPDATE wishlist w
SET status = 'pending', compile_attempts = 0
WHERE w.status = 'dismissed'
  AND w.compiled_by_role IS NOT NULL
  AND w.feature_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id)
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.feature_id = w.feature_id)
  AND EXISTS (
      SELECT 1 FROM features f
      JOIN wishlist root ON root.id = f.root_wishlist_id
      WHERE f.id = w.feature_id
        AND f.dismissed_at IS NULL
        AND root.source = 'client');
