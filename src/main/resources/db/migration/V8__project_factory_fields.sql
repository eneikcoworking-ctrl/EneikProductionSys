ALTER TABLE projects ADD COLUMN github_repository_status VARCHAR(512);
ALTER TABLE projects ADD COLUMN github_repository_id VARCHAR(128);
ALTER TABLE projects ADD COLUMN linear_project_status VARCHAR(512);
ALTER TABLE projects ADD COLUMN linear_project_id VARCHAR(128);
ALTER TABLE projects ADD COLUMN workspace_path VARCHAR(512);
ALTER TABLE projects ADD COLUMN factory_status VARCHAR(64);
ALTER TABLE projects ADD COLUMN factory_report TEXT;
