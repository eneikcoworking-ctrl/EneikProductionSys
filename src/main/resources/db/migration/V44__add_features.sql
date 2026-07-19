-- Feature: a stable grouping unit for everything decomposed from one root client wishlist item.
-- Introduced because role_threads (V42/V43) keyed continuation by (project, role) alone - too coarse,
-- since one role does many unrelated features over a project's lifetime. Minted lazily, once, the first
-- time a wishlist item is actually compiled into a task (TechnicalLeadCompiler.createAndSaveTask via
-- FeatureService.resolveOrCreateFeatureId) - not for every wishlist row, only ones that really become work.
CREATE TABLE features (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    root_wishlist_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE wishlist ADD COLUMN feature_id UUID REFERENCES features(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN feature_id UUID REFERENCES features(id) ON DELETE SET NULL;

-- role_threads is empty at this point (the entire prior experiment run was deleted earlier in this
-- session), so narrowing the key to include feature_id needs no data migration.
ALTER TABLE role_threads ADD COLUMN feature_id UUID REFERENCES features(id) ON DELETE SET NULL;
ALTER TABLE role_threads DROP CONSTRAINT uq_role_threads_project_role;
ALTER TABLE role_threads ADD CONSTRAINT uq_role_threads_project_feature_role UNIQUE (project_id, feature_id, role_tag);
