-- V134 (2026-08-30): the delivery findings destroyed twenty minutes after they were first filed.
--
-- The delivery-reality department was widened to speak about planned work that FAILED and that nothing
-- will retry, not only work that reported done. It filed sixteen findings at 14:30:58. At 14:36:26
-- groupSimilarWishlistItems merged fifteen of them into the sixteenth:
--
--     14:36:26.511  Dismissed duplicate wishlist item 0c1803d7 (merged into ef4ac56b)
--     14:36:26.514  Dismissed duplicate wishlist item 6eb0c22f (merged into ef4ac56b)
--     ... thirteen more, same instant, same survivor
--
-- These are findings about sixteen DIFFERENT tasks. Their texts differ only in a task title, because the
-- text is the factory's own boilerplate; what identifies such a finding is source_task_id, the column the
-- producer's own deduplication has used since V116. The grouper compared the prose instead, so two places
-- decided "is this the same finding" by different predicates - Charter invariant 10. Fixed forward in the
-- same commit (ProjectFlowService.areWishlistItemsSimilar).
--
-- They cannot come back on their own: the producer refuses to refile a finding whose source_task_id it has
-- already recorded, whatever status that row now has. Without this they are lost for the life of the
-- project, and with them the six client deliverables that never merged.
--
-- Bounded by the measurement, not by a chosen number: exactly the rows this department filed after the
-- widening was deployed, which is when the grouper first had a batch of them to collapse.
UPDATE wishlist w
SET status = 'pending'
WHERE w.status = 'dismissed'
  AND w.source = 'delivery_never_reached_main'
  AND w.source_task_id IS NOT NULL
  AND w.created_at >= TIMESTAMP '2026-08-30 14:25:00'
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id);
