-- V140: Capability observation instrument failure indicator (TRUTH_STATUS_TABLE / D012).
-- Distinguishes an observation instrument refusal / authentication denial (401, 403, connection refused)
-- from a product capability defect. An unauthenticated probe rejected by product SecurityConfig
-- is not evidence of a broken capability; it is an instrument failure.

ALTER TABLE capability_observations ADD COLUMN instrument_failure BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE capability_observations
SET instrument_failure = TRUE
WHERE status_code IS NULL OR status_code IN (401, 403);
