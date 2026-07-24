ALTER TABLE feature_threads ADD COLUMN merged_to_main_at TIMESTAMP NULL;
ALTER TABLE feature_threads ADD COLUMN closeout_pr_url VARCHAR(256) NULL;
ALTER TABLE feature_threads ADD COLUMN closeout_conflict_escalated_at TIMESTAMP NULL;
