-- V73: exponential backoff for api_blocked account recovery (2026-08-01, operator: "падение должно быть
-- редким"). Before this, ContinuousOrchestrationService.recoverStaleBlockedAccounts retried every blocked
-- account on the same fixed 30-minute cooldown forever, regardless of how many consecutive times it had
-- just failed - confirmed live, some accounts failed FAILED_PRECONDITION 9-13 times in a single day.
ALTER TABLE accounts ADD COLUMN consecutive_api_block_count INT NOT NULL DEFAULT 0;
