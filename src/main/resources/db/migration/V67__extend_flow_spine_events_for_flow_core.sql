ALTER TABLE flow_spine_events ADD COLUMN decision_hash VARCHAR(64);
ALTER TABLE flow_spine_events ADD COLUMN decision_action_key VARCHAR(128);
ALTER TABLE flow_spine_events ADD COLUMN decision_status VARCHAR(64);
ALTER TABLE flow_spine_events ADD COLUMN authorization_status VARCHAR(64);
ALTER TABLE flow_spine_events ADD COLUMN risk_level VARCHAR(32);
ALTER TABLE flow_spine_events ADD COLUMN decision_reason CLOB;
ALTER TABLE flow_spine_events ADD COLUMN decision_preconditions CLOB;
ALTER TABLE flow_spine_events ADD COLUMN expected_outcomes CLOB;
ALTER TABLE flow_spine_events ADD COLUMN forbidden_actions CLOB;

CREATE INDEX idx_flow_spine_events_project_mode_observed
    ON flow_spine_events (project_id, mode, observed_at DESC);
