-- V144: Sovereign DeepSeek worker engine and token accounting (GEMINI.md §0, BARCAN-TAG-00..12).
-- Dennett Autonomy Architecture (Cranes not Skyhooks, Homuncular Functionalism, Belnap 4-valued states D012).

ALTER TABLE accounts ADD COLUMN provider VARCHAR(32) DEFAULT 'GOOGLE_JULES' NOT NULL;
ALTER TABLE accounts ADD COLUMN tpm_limit INT DEFAULT 60000;
ALTER TABLE accounts ADD COLUMN rpm_limit INT DEFAULT 60;
ALTER TABLE accounts ADD COLUMN tokens_used_today BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE jules_sessions ADD COLUMN worker_engine VARCHAR(32) DEFAULT 'JULES' NOT NULL;
ALTER TABLE jules_sessions ADD COLUMN model_name VARCHAR(64);
ALTER TABLE jules_sessions ADD COLUMN cached_tokens INT DEFAULT 0;
ALTER TABLE jules_sessions ADD COLUMN prompt_tokens INT DEFAULT 0;
ALTER TABLE jules_sessions ADD COLUMN completion_tokens INT DEFAULT 0;
ALTER TABLE jules_sessions ADD COLUMN worktree_path VARCHAR(256);
ALTER TABLE jules_sessions ADD COLUMN severe_test_passed BOOLEAN DEFAULT FALSE;
ALTER TABLE jules_sessions ADD COLUMN belnap_status VARCHAR(32) DEFAULT 'NEITHER';

CREATE INDEX idx_jules_sessions_worker_engine ON jules_sessions (worker_engine);
CREATE INDEX idx_jules_sessions_belnap_status ON jules_sessions (belnap_status);
