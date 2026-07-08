CREATE TABLE falsification_runs (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    run_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    roles_checked_count INT NOT NULL,
    violations_found_count INT NOT NULL,
    tasks_created_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_falsification_runs_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
