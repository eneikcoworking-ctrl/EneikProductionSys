ALTER TABLE jules_sessions ADD COLUMN last_progress_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE jules_sessions ADD COLUMN blind_cycle_count INT DEFAULT 0 NOT NULL;
ALTER TABLE jules_sessions ADD COLUMN forced_unblock_attempts INT DEFAULT 0 NOT NULL;
