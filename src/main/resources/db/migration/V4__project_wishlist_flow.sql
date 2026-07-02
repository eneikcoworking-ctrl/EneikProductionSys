CREATE TABLE projects (
    id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    repository_name VARCHAR(128) NOT NULL,
    repository_url VARCHAR(256),
    linear_project_key VARCHAR(128),
    status VARCHAR(16) DEFAULT 'active' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    PRIMARY KEY (id)
);

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
    CONSTRAINT fk_wishlist_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_wishlist_source_role FOREIGN KEY (source_role_tag) REFERENCES roles(tag)
);

ALTER TABLE accounts ADD COLUMN project_id UUID;
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_project FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE tasks ADD COLUMN project_id UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id);

CREATE INDEX idx_accounts_project ON accounts(project_id);
CREATE INDEX idx_tasks_project_status_tag ON tasks(project_id, status, tag);
CREATE INDEX idx_wishlist_project_status ON wishlist_items(project_id, status);
