-- V26: Add onboarding_mode, default_branch, baseline_commit_sha to projects and create onboarding_audit_findings table
ALTER TABLE projects ADD COLUMN onboarding_mode VARCHAR(64) DEFAULT 'greenfield' NOT NULL;
ALTER TABLE projects ADD COLUMN default_branch VARCHAR(256) DEFAULT 'main' NOT NULL;
ALTER TABLE projects ADD COLUMN baseline_commit_sha VARCHAR(64);

CREATE TABLE onboarding_audit_findings (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    role_tag VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    file_path VARCHAR(512),
    line_number INT,
    finding_text CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_findings_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
