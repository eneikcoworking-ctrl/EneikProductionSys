-- 2026-08-29, §9 of ENGINEERING_PHILOSOPHY_ACTION_PLAN.md.
--
-- DeliveryRealityProducerService built one string, `marker = "task " + task.getId()`, and used it for two
-- unrelated purposes: as the factory's own deduplication key (does a brief for this task already exist?)
-- and as text handed to an agent working inside the CLIENT's codebase. The second use leaked a factory
-- identifier across the zone boundary that §2's first invariant draws, and the agent - having no such
-- entity - did the only thing available to it: found a client table with a subject_id column and wrote
-- UPDATE ... SET status = 'RESOLVED' WHERE subject_id = '<factory task uuid>'. Seven review sessions were
-- then spent on the pull request that came out of it.
--
-- Splitting the two uses needs somewhere for the factory-side one to live. This column is that place, and
-- it is strictly better than what it replaces: deduplication stops matching a substring inside prose and
-- becomes an equality on an identifier. Nullable because only this producer sets it; every other origin
-- keeps its own existing linkage (origin_feature_id, origin_wishlist_id).
ALTER TABLE wishlist ADD COLUMN source_task_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_wishlist_source_task_id ON wishlist(source_task_id);
