-- V132 (2026-08-30, plan 4.45): the two client requirements V129 returned and the grouper took back.
--
-- V130 and V131 both matched zero rows, and the epic-cleanup tick at 10:20:02 said why. It dismissed the
-- epic without withholding judgment, which means no non-terminal task exists under it - so the conjunct
-- that excluded these rows was the OTHER one: both slices HAVE tasks, and both of those tasks are
-- `failed`. V129 recorded them the day before: "Data Subject Rights and Privacy Consent  0 done, 2 FAILED".
--
-- That completes the history. V129 correctly returned these two requirements to the flow. The very next
-- orchestration pass begins with groupSimilarWishlistItems, and at 00:33:34 it merged them into a slice of
-- a different epic and dismissed them - inside the same night, before anything could be dispatched:
--
--     2026-08-30T00:33:34.567Z  Dismissed duplicate wishlist item b8dca98a (merged into 2c3442ef)
--     2026-08-30T00:33:34.570Z  Dismissed duplicate wishlist item b8f32565 (merged into 2c3442ef)
--
-- V129's predicate is therefore already exactly right for these rows and is re-applied here verbatim.
-- The reason it needs re-applying is not the predicate but the defect that undid it, and that defect is
-- fixed forward in the same session (ProjectFlowService.areWishlistItemsSimilar): two slices of one brief
-- are never duplicates of each other, and a compiled slice is compared on its jtbd and acceptance
-- criteria rather than on the header the factory generated for it.
--
-- The epic itself is soft-dismissed as of 10:20. That is correct and self-healing: it had no live work at
-- that moment, and unDismissFeatureIfNeeded restores it the moment a task dispatches under it again.
UPDATE wishlist w
SET status = 'pending', compile_attempts = 0
WHERE w.status IN ('dismissed', 'converted_to_task')
  AND EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id)
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id AND t.status <> 'failed')
  AND EXISTS (
      SELECT 1 FROM tasks t
      JOIN features f ON f.id = t.feature_id
      JOIN wishlist root ON root.id = f.root_wishlist_id
      WHERE t.source_wishlist_id = w.id AND root.source = 'client');
