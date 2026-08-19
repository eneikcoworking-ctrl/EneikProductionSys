-- 2026-08-19: an observation that never happened is not an observation of failure.
--
-- RuntimeLauncherClient.launch returned LaunchResult(success=false, ...) for two different facts: the
-- launcher answered and reported that `docker compose up` failed (a fact about the PRODUCT), and the
-- launcher never answered at all (a fact about the INSTRUMENT). Both were written to launch_success,
-- so an instrument fault entered the product's own history.
--
-- Measured consequence on test-forty-ninth: the 2026-08-19T16:42:04Z row records
-- "runtime-launcher unreachable: ... Read timed out". The product did not fail - it was never tried.
-- That row took the posterior from Beta(1,3) to Beta(1,4), which stretched the next check from 7.2 to
-- 9.7 hours. Every instrument fault therefore pushes the next attempt further away: feedback with the
-- wrong sign, and the reason the launch goal is unreachable by construction.
--
-- The row is kept, not deleted - the event did happen, only its subject was wrong. This column records
-- what the row is a fact ABOUT, and BetaPosterior stops counting the ones that are about the instrument.
ALTER TABLE client_runtime_observations
    ADD COLUMN instrument_failure BOOLEAN DEFAULT FALSE NOT NULL;

-- Backfill: every historical row whose error text is the launcher-unreachable signature written by
-- RuntimeLauncherClient's own catch block. Narrow on purpose - a compose failure reported BY the
-- launcher is a real product observation and must stay counted.
UPDATE client_runtime_observations
   SET instrument_failure = TRUE
 WHERE launch_success = FALSE
   AND error_text LIKE 'runtime-launcher unreachable:%';
