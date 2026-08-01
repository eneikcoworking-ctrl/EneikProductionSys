-- V71: unified Lean/TOC/Six Sigma system, u₄ (review concerns) data source (2026-08-01).
-- Before this, a reviewer's concern only ever reached a log line - unqueryable, no severity distinction.
CREATE TABLE review_concerns (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    feature_id UUID,
    task_id UUID,
    severity VARCHAR(32) NOT NULL,
    category VARCHAR(64),
    root_cause_pattern_id INT,
    text CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_review_concerns_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_review_concerns_feature ON review_concerns (feature_id);
