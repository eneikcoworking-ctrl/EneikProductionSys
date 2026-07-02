-- V9: Create Linear Issue Metadata table
-- This table stores additional fields for Linear synchronization

CREATE TABLE linear_issue_metadata (
  task_id UUID PRIMARY KEY,
  linear_issue_id VARCHAR(64),
  blockers TEXT,
  dod_text TEXT,
  pr_url VARCHAR(256),
  last_synced_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_linear_metadata_task FOREIGN KEY (task_id) REFERENCES tasks(id)
);
