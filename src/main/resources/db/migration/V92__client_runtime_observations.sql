CREATE TABLE client_runtime_observations (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL,
    observed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    launch_success BOOLEAN NOT NULL,
    launch_duration_ms BIGINT,
    health_status_code INT,
    health_latency_ms BIGINT,
    error_text VARCHAR(4000)
);

CREATE INDEX idx_client_runtime_observations_project_id ON client_runtime_observations (project_id, observed_at);
