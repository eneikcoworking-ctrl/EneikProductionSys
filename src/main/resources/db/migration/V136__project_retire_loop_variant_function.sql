-- V136 (Law 8 / Law 25a): Well-founded variant function for project retirement cycle.
--
-- With MAX_RETIRE_ATTEMPTS a declared finite budget and nu(P) = MAX_RETIRE_ATTEMPTS - retire_attempts,
-- every attempt that reaches the external systems either completes retirement (retired_at is set)
-- or decreases nu by exactly one.
--
-- Exhaustion of nu records a non-silent entry in defect_journal and sets retire_exhausted = true.
-- Idempotent: once retired_at is set or retire_exhausted is true, no further external calls are made.
ALTER TABLE projects ADD COLUMN retire_attempts INT DEFAULT 0 NOT NULL;
ALTER TABLE projects ADD COLUMN retire_exhausted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE projects ADD COLUMN retired_at TIMESTAMP;
