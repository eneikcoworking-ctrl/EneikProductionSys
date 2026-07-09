-- V25: Add depends_on to tasks and create project_hotspot_files table
ALTER TABLE tasks ADD COLUMN depends_on UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_depends_on FOREIGN KEY (depends_on) REFERENCES tasks(id);

CREATE TABLE project_hotspot_files (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    project_id UUID NOT NULL,
    file_path VARCHAR(256) NOT NULL,
    CONSTRAINT fk_hotspots_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
