CREATE TABLE github_access_status (
  id UUID DEFAULT random_uuid() PRIMARY KEY,
  project_id UUID NOT NULL,
  has_repo_access BOOLEAN NOT NULL,
  branch_protection_ok BOOLEAN NOT NULL,
  pr_permissions_ok BOOLEAN NOT NULL,
  webhooks_ok BOOLEAN NOT NULL,
  ci_status VARCHAR(16) NOT NULL,
  checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  raw_error TEXT,
  CONSTRAINT fk_github_access_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
