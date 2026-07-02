ALTER TABLE projects ADD COLUMN slug VARCHAR(128);
ALTER TABLE projects ADD COLUMN repository_name VARCHAR(128);
ALTER TABLE projects ADD COLUMN repository_url VARCHAR(256);
ALTER TABLE projects ADD COLUMN linear_project_key VARCHAR(128);

UPDATE projects
SET slug = LOWER(REPLACE(name, ' ', '-'))
WHERE slug IS NULL;

UPDATE projects
SET repository_name = slug
WHERE repository_name IS NULL;

UPDATE projects
SET repository_url = repo_url
WHERE repository_url IS NULL AND repo_url IS NOT NULL;

UPDATE projects
SET linear_project_key = UPPER(REPLACE(slug, '-', '_'))
WHERE linear_project_key IS NULL;

ALTER TABLE projects ALTER COLUMN slug SET NOT NULL;
ALTER TABLE projects ALTER COLUMN repository_name SET NOT NULL;

CREATE UNIQUE INDEX idx_projects_slug ON projects(slug);

CREATE TABLE wishlist_items (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    text TEXT NOT NULL,
    type VARCHAR(32) DEFAULT 'client_wish' NOT NULL,
    status VARCHAR(16) DEFAULT 'open' NOT NULL,
    source_role_tag VARCHAR(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wishlist_items_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_wishlist_items_source_role FOREIGN KEY (source_role_tag) REFERENCES roles(tag)
);

ALTER TABLE accounts ADD COLUMN project_id UUID;
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_project_entity FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE tasks ADD COLUMN project_id UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id);

CREATE INDEX idx_accounts_project ON accounts(project_id);
CREATE INDEX idx_tasks_project_status_tag ON tasks(project_id, status, tag);
CREATE INDEX idx_wishlist_items_project_status ON wishlist_items(project_id, status);
