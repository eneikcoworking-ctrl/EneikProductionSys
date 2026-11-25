-- V147 (WO #13, Mechanism #15, 2026-11-25):
-- 1. Retag sovereign local AGY profiles (remotecli3..10) from GOOGLE_JULES to LOCAL_AGY
--    before excluding GOOGLE_JULES from account selectors (preserving id, enabled, status, and history).
-- 2. Decommission all remaining cloud Google Jules accounts (including stray idle/disabled rows)
--    without deleting any rows from accounts or jules_sessions.

UPDATE accounts
SET provider = 'LOCAL_AGY'
WHERE LOWER(name) LIKE 'remotecli%'
  AND UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES';

UPDATE accounts
SET status = 'decommissioned',
    enabled = FALSE,
    status_changed_at = CURRENT_TIMESTAMP
WHERE UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES';
