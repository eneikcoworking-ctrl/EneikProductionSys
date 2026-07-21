-- Persistent worker sessions: lets the wishlist compiler and PR review fallback reuse ONE long-lived
-- Jules session per (project, purpose) across many cycles instead of spawning a fresh session/branch/PR
-- every time - see ProjectFlowService/JulesDispatchService persistent-worker dispatch logic. carrier_task_id
-- points at a TaskEntity that is deliberately never marked done/failed while the worker is active; its
-- session oscillates between pr_opened (idle) and revising (processing a new batch) instead of being
-- closed and recreated each cycle. No unique constraint on (project_id, purpose): a retired worker's row
-- is kept (retired_at set) rather than deleted, and a new row is inserted for the replacement worker -
-- "the active one" is always looked up as retired_at IS NULL, enforced in application code, not the schema.
CREATE TABLE persistent_worker_sessions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    carrier_task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    current_jules_session_id UUID REFERENCES jules_sessions(id) ON DELETE SET NULL,
    current_batch_ids JSON,
    cycle_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_sent_at TIMESTAMP,
    retired_at TIMESTAMP
);

CREATE INDEX idx_persistent_worker_project_purpose_active
    ON persistent_worker_sessions (project_id, purpose, retired_at);
