CREATE TABLE defect_journal (
    id UUID PRIMARY KEY,
    project_id UUID,
    severity VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    source_component VARCHAR(128) NOT NULL,
    defect_type VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    metric_value DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_defect_journal_project_created ON defect_journal(project_id, created_at);
