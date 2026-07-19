-- Corrected scoping: a thread's branch belongs to the FEATURE, not to (feature, role). A feature
-- routinely involves multiple roles (backend, frontend, design) working the same dependency chain, and
-- any of them may legitimately continue on the same branch as the chain progresses - only the owning
-- account is required to match, never the role. role_threads is still empty (nothing has run since the
-- prior experiment was wiped), so this is a clean rename, no data to migrate.
ALTER TABLE role_threads RENAME TO feature_threads;
ALTER TABLE feature_threads RENAME COLUMN role_tag TO last_role_tag;
ALTER TABLE feature_threads ALTER COLUMN last_role_tag SET NULL;
ALTER TABLE feature_threads ALTER COLUMN feature_id SET NOT NULL;
ALTER TABLE feature_threads DROP CONSTRAINT uq_role_threads_project_feature_role;
ALTER TABLE feature_threads ADD CONSTRAINT uq_feature_threads_project_feature UNIQUE (project_id, feature_id);
