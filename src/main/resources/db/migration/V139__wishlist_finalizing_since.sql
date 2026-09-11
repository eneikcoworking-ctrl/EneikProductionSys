-- V139: Add finalizing_since column to wishlist table (Prescriptions 17 & 34: CAUSAL_PROCESS_TRACE / D013, PRINCIPLED_INTEGRITY / D012)
-- Dedicated status change timestamp for the transient finalizing guard:
-- Prevents premature release caused by measuring age from older adjacent timestamps (createdAt or lastCompileDispatchedAt),
-- and enables active workers to renew their lease during parse/compilation.

ALTER TABLE wishlist ADD COLUMN finalizing_since TIMESTAMP WITH TIME ZONE NULL;
CREATE INDEX idx_wishlist_finalizing_since ON wishlist(finalizing_since);
