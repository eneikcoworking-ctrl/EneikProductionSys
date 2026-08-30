-- V133 (2026-08-30, plan 4.45): the same two client requirements, returned a third time - now that the
-- thing which kept taking them back is fixed.
--
-- V132 put them into `pending` at 10:23. At 10:24 the compiler dismissed both again, and this time it said
-- why, because the branch that had recorded nothing now names the task it collapses into:
--
--     10:24:27.712  wishlist b8dca98a collapsed into the existing semantic duplicate task d55621e0
--     10:24:28.122  wishlist b8f32565 collapsed into the existing semantic duplicate task 40dff79f
--
-- Both of those tasks are dead - d55621e0 was marked failed at 19:00:38 the night before (PR#437 closed
-- without merge), 40dff79f was retired behind it - and the semantic-duplicate veto counted them anyway. It
-- no longer does (TechnicalLeadCompiler.findLiveSemanticTask, same session): a task that can never reach
-- done does not occupy the slot that blocks its own replacement.
--
-- The grouper did NOT touch them this time - its fix held, measured in the same window. This is the third
-- and last link of the chain, so this reopen is the one that can actually be verified through to a task.
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
