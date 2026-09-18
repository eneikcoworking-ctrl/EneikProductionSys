-- V142: Theory of Constraints (TOC) Drum-Buffer-Rope durable snapshots (DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT, D010).
-- Preserves Welford's algorithm online variance series and edge transitions across restarts.
-- Eliminates cold-start single-sample bottleneck distortion.

CREATE TABLE toc_node_snapshots (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    node_name VARCHAR(128) NOT NULL,
    completed_count BIGINT NOT NULL,
    error_count BIGINT NOT NULL,
    total_duration_nanos BIGINT NOT NULL,
    mean_duration_ms DOUBLE NOT NULL,
    m2_ms DOUBLE NOT NULL,
    std_dev_ms DOUBLE NOT NULL,
    dynamic_timeout_limit_ms DOUBLE NOT NULL,
    snapshot_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_toc_node_snapshots_node_time ON toc_node_snapshots (node_name, snapshot_at DESC);

CREATE TABLE toc_edge_snapshots (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    source_node VARCHAR(128) NOT NULL,
    target_node VARCHAR(128) NOT NULL,
    transition_count BIGINT NOT NULL,
    snapshot_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_toc_edge_snapshots_source_target ON toc_edge_snapshots (source_node, target_node, snapshot_at DESC);
