-- payload.source_wishlist_id (JSON, set by TechnicalLeadCompiler.createAndSaveTask) already carries this
-- link, but a JSON text-search is the only way to query "which tasks came from which client wishlist
-- item" - too slow/fragile for the readiness rollup FalsificationCycleService and the BUILD/MAINTENANCE
-- phase gate both need. Promote it to a real, indexed FK column, populated alongside the JSON field going
-- forward (existing rows are left NULL - back-filling from payload JSON is a one-off data migration, not
-- schema work, and not needed for either new gate to function correctly from here on).
ALTER TABLE tasks ADD COLUMN source_wishlist_id UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_source_wishlist FOREIGN KEY (source_wishlist_id) REFERENCES wishlist(id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_source_wishlist_id ON tasks(source_wishlist_id);
