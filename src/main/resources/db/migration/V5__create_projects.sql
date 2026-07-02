-- V4: Create projects table and link accounts
CREATE TABLE projects (
  id UUID DEFAULT random_uuid() PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  repo_url VARCHAR(256),
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  accepted_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE accounts ADD COLUMN current_project_id UUID;
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_project FOREIGN KEY (current_project_id) REFERENCES projects(id);
