-- Durable, deploy-independent project event log (2026-07-26, operator directive: "лог проекта должен
-- независеть от деплоев!! это огромное упущение" - a real project log from start to acceptance, restored
-- after being incorrectly deleted alongside the observer's Gemini-consumption fix (V58, 2026-07-25). That
-- fix was about Gemini no longer READING the backend's own log, not about deleting the log itself. This
-- table is DB-backed (survives any container recreate) and is for external agents/operator forensic
-- access (e.g. tracing the PR#45 mis-attribution bug), never fed to Gemini directly.
CREATE TABLE project_event_log (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    level VARCHAR(16) NOT NULL,
    logger VARCHAR(255) NOT NULL,
    message CLOB NOT NULL
);

CREATE INDEX idx_project_event_log_project_created
    ON project_event_log (project_id, created_at DESC);

-- On by default - this is now baseline infrastructure, not an opt-in experiment.
INSERT INTO system_settings ("key", "value", updated_at) VALUES ('project_event_log_enabled', 'true', CURRENT_TIMESTAMP);
