CREATE TABLE flow_spine_events (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    cycle_id UUID NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    previous_state VARCHAR(64),
    current_state VARCHAR(64) NOT NULL,
    next_state VARCHAR(64),
    value_status VARCHAR(64) NOT NULL,
    bottleneck_type VARCHAR(64),
    bottleneck_severity VARCHAR(32),
    age_in_state_minutes BIGINT NOT NULL DEFAULT 0,
    owner VARCHAR(128),
    transition_action CLOB,
    evidence_hash VARCHAR(64) NOT NULL,
    evidence_summary CLOB NOT NULL,
    blocking_reason CLOB,
    mode VARCHAR(32) NOT NULL
);

CREATE INDEX idx_flow_spine_events_project_observed
    ON flow_spine_events (project_id, observed_at DESC);

CREATE INDEX idx_flow_spine_events_project_state
    ON flow_spine_events (project_id, current_state, observed_at DESC);
