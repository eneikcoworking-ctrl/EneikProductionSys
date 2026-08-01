-- V72: unified Lean/TOC/Six Sigma system, u-chart durable time series (2026-08-01).
-- Before this, SixSigmaAuditService computed DPMO on demand with no history - no Phase 1 baseline could
-- be locked and no Phase 2 drift could be detected against it. One row per (project, эпик, stream).
CREATE TABLE process_control_snapshots (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    stream VARCHAR(32) NOT NULL,
    sequence_index INT NOT NULL,
    u_value DOUBLE NOT NULL,
    n_opportunities BIGINT NOT NULL,
    defects BIGINT NOT NULL,
    center_line DOUBLE NOT NULL,
    upper_control_limit DOUBLE NOT NULL,
    lower_control_limit DOUBLE NOT NULL,
    phase VARCHAR(16) NOT NULL,
    out_of_control BOOLEAN NOT NULL,
    western_electric_signal VARCHAR(64),
    computed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_process_control_snapshots_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_process_control_snapshots_project_stream ON process_control_snapshots (project_id, stream, sequence_index);
CREATE INDEX idx_process_control_snapshots_feature ON process_control_snapshots (feature_id, stream);
