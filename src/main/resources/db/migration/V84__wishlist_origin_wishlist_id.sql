-- Gricean quantity-optimal grounding (2026-08-07): immutable lineage pointer from a compiler-generated
-- slice wishlist back to the original client-authored wishlist it was sliced from, so the slice's task
-- description can retrieve only the relevant excerpt of the original brief instead of duplicating it
-- whole or truncating it at a fixed character count.
ALTER TABLE wishlist ADD COLUMN origin_wishlist_id UUID;
