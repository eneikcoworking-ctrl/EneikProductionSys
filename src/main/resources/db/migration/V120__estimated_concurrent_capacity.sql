-- 2026-08-29, action plan 4.4. Mirrors estimated_daily_capacity, which already carries the revisable
-- belief about how many sessions an account may open in a day. Concurrency was left out when that was
-- built, and the reason is written in AccountRepository: DispatchOutcome had no distinct signal for
-- "refused because too many sessions are open", so there was no channel to falsify the belief through.
-- The operator established on 2026-08-29 that the unnamed FAILED_PRECONDITION is exactly that refusal, so
-- the channel exists and the belief can stop being a constant.
--
-- NULL means "not yet falsified": the selector falls back to max_concurrent_sessions exactly as before,
-- so behaviour is unchanged until the first real observation moves it.
ALTER TABLE accounts ADD COLUMN estimated_concurrent_capacity INT;
