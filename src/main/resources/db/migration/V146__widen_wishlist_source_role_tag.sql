-- Mechanism #9 (2026-11-24): Widen wishlist.source_role_tag to VARCHAR(128) so canonical
-- multi-role BARCAN-TAG-XX lists (e.g. FalsificationCycleService clusters) never overflow
-- SQL 22001, while keeping wishlist_items.source_role_tag VARCHAR(16) FK -> roles(tag) intact.
ALTER TABLE wishlist ALTER COLUMN source_role_tag VARCHAR(128);
