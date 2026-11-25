-- WO #14 / Mechanism #16 (D010 ELVIN_GOLDMAN_01_RELIABILITY_CHAIN, D012 NUEL_BELNAP_03_TRUTH_STATUS_TABLE):
-- Add non-destructive consolidation lineage and Belnap BOTH conflict markers for wishlists
-- accumulated while a project is frozen/waiting and consolidated on unfreeze.
ALTER TABLE wishlist ADD COLUMN merged_into_wishlist_id UUID NULL;
ALTER TABLE wishlist ADD CONSTRAINT fk_wishlist_merged_into
    FOREIGN KEY (merged_into_wishlist_id) REFERENCES wishlist(id) ON DELETE SET NULL;
ALTER TABLE wishlist ADD COLUMN consolidated_from_ids TEXT NULL;
ALTER TABLE wishlist ADD COLUMN consolidation_conflict BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE wishlist ADD COLUMN consolidation_notes TEXT NULL;

CREATE INDEX idx_wishlist_project_merged_into ON wishlist(project_id, merged_into_wishlist_id);
