-- Durable, per-project event log (2026-07-25, operator directive): the existing LogScopeBuffer is
-- explicitly in-memory-only and resets on every backend restart - not the "uninterrupted from project
-- creation to final acceptance" log the operator asked the Gemini observer to read. This table is the
-- durable version: populated by a batched flush (see ProjectEventLogService), not written synchronously
-- inside the logging call path, so it never adds a DB round-trip to a hot log.info() call.
CREATE TABLE project_event_log (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    occurred_at TIMESTAMP NOT NULL,
    level VARCHAR(16) NOT NULL,
    logger_name VARCHAR(256),
    message CLOB NOT NULL
);

CREATE INDEX idx_project_event_log_project_occurred ON project_event_log (project_id, occurred_at);
