-- Engineering invariant #15 (2026-08-08): the account's real daily Jules quota is an unknown external fact,
-- not the hardcoded jules.max-daily-sessions-per-account config value. NULL means "never falsified yet" -
-- AccountHealthService.reportDispatchOutcome grows this on each real SUCCESS that reaches the current
-- ceiling (Popperian bold conjecture, not yet refuted) and only ever shrinks it on a real DAILY_LIMIT
-- rejection from Jules (the one event that counts as falsification). The capacity query falls back to the
-- global config default via COALESCE for any account that has never been tested this way.
ALTER TABLE accounts ADD COLUMN estimated_daily_capacity INT NULL;
