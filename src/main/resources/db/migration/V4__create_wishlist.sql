-- V4__create_wishlist.sql
-- Pure CRUD storage for wishlist items

CREATE TABLE wishlist (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL, -- Temporary: no FK until projects table is merged
  source VARCHAR(16) NOT NULL,        -- 'client' | 'role'
  source_role_tag VARCHAR(16),        -- NULL if source='client'
  content TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
       -- pending | converted_to_task | dismissed
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_wishlist_project_status ON wishlist(project_id, status);

-- COMMENT: project_id UUID NOT NULL REFERENCES projects(id)
-- The foreign key is omitted because the projects table is not yet present in this branch.
