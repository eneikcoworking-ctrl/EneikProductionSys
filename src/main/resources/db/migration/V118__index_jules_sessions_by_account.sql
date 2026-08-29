-- 2026-08-29, action plan 4.11. The account selector's ORDER BY already carried one correlated subquery
-- over jules_sessions per candidate row, and the table has no index on account_id at all - every selection
-- paid a full scan per candidate. The refusal-run term added in the same change reads the account's most
-- recent session by time, so the composite (account_id, created_at) serves both, and the existing
-- countByAccountIdAndStatusIn as well.
CREATE INDEX IF NOT EXISTS idx_jules_sessions_account_created
    ON jules_sessions (account_id, created_at);
