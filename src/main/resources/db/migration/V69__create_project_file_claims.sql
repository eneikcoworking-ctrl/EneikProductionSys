-- V69: live ownership ledger for the cross-эпик file-collision guard (smart decomposition v2, 2026-07-31).
-- taskId/featureId are nullable: a NULL featureId is a project-wide global claim (recorded by the
-- deterministic bootstrap scaffolds) that collides with every эпик, not just a specific one.
CREATE TABLE project_file_claims (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    task_id UUID,
    feature_id UUID,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_file_claims_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_file_claims_project_path ON project_file_claims (project_id, file_path);
