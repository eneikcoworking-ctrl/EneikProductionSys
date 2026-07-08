-- V24__alter_wishlist_source_length.sql
-- Increase source column length to support role_mismatch_followup and self_falsification values
ALTER TABLE wishlist ALTER COLUMN source VARCHAR(32);
