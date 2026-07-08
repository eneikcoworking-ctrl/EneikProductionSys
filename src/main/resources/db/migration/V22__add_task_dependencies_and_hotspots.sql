ALTER TABLE tasks ADD COLUMN depends_on UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_task_depends_on FOREIGN KEY (depends_on) REFERENCES tasks(id) ON DELETE SET NULL;

CREATE TABLE project_hotspot_files (
    project_id UUID NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    PRIMARY KEY (project_id, file_path),
    CONSTRAINT fk_project_hotspot_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
