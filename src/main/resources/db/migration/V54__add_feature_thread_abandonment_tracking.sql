ALTER TABLE feature_threads ADD COLUMN closeout_conflict_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE feature_threads ADD COLUMN abandoned_at TIMESTAMP NULL;
