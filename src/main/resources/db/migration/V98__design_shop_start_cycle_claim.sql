-- V98: atomic mutual-exclusion claim for DesignShopOrchestrationService.startCycle (2026-08-14, bug-hunt
-- sweep). Closes a genuine double-dispatch race: tick() takes no row lock and has no per-project claim, so
-- if one invocation is still inside startCycle's real Stitch generation (up to 20x Thread.sleep(15_000),
-- i.e. up to 5 real minutes) when the next 5-minute cron fires, two overlapping invocations can both read
-- lastWasReady=false for the same project and both start a real design cycle for the same round. Kept
-- deliberately separate from last_was_ready itself: that field is only set true AFTER a successful Stitch
-- generation (so a failed attempt correctly retries next tick) - this claim exists purely to serialize
-- concurrent attempts, not to record the outcome. NULL means no attempt currently in flight (including
-- after a failed attempt released it for retry); non-null means one is in progress right now.
ALTER TABLE design_shop_cycles ADD COLUMN start_cycle_claimed_at TIMESTAMP;
