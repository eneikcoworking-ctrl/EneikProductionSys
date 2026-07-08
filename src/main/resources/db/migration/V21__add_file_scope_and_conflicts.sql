-- V21: Add file_scope to tasks and create task_conflicts and needs_human_review tables

ALTER TABLE tasks ADD COLUMN file_scope TEXT;

CREATE TABLE task_conflicts (
    id UUID NOT NULL,
    task_id UUID NOT NULL,
    pr_url VARCHAR(256),
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    conflict_type VARCHAR(32) NOT NULL,
    resolution_attempts INT DEFAULT 0 NOT NULL,
    resolved_at TIMESTAMP,
    resolution_status VARCHAR(32) NOT NULL,
    conflicting_files TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_conflicts_task FOREIGN KEY (task_id) REFERENCES tasks(id)
);

CREATE TABLE needs_human_review (
    id UUID NOT NULL,
    task_id UUID NOT NULL,
    reason VARCHAR(256) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_needs_human_review_task FOREIGN KEY (task_id) REFERENCES tasks(id)
);
