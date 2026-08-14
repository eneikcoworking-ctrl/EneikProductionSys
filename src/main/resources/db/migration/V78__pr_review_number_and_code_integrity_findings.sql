ALTER TABLE pr_reviews ADD COLUMN pr_number INT;

CREATE TABLE code_integrity_findings (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    feature_id UUID,
    falsification_run_id UUID NOT NULL,
    finding_type VARCHAR(32) NOT NULL,
    pr_number INT,
    reason TEXT NOT NULL,
    found_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_code_integrity_findings_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_code_integrity_findings_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE SET NULL,
    CONSTRAINT fk_code_integrity_findings_run FOREIGN KEY (falsification_run_id) REFERENCES falsification_runs(id) ON DELETE CASCADE
);
