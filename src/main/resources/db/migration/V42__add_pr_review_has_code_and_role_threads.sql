-- has_code: deterministic classification of a merged implementer PR's diff (see CodeChangeClassifier),
-- set once at merge time in AutoMergeService. Nullable - historical rows are left unclassified rather
-- than guessed at retroactively; this is one-off analytics, not business logic anything depends on.
ALTER TABLE pr_reviews ADD COLUMN has_code BOOLEAN;

-- role_threads: one live "continuation" branch per (project, role) - the most recently merged
-- code-containing PR for that role, deliberately left undeleted so the next task for the same role can
-- start its Jules session from this branch (startingBranch) instead of main, carrying its history
-- forward instead of starting from scratch every time.
CREATE TABLE role_threads (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role_tag VARCHAR(16) NOT NULL REFERENCES roles(tag),
    branch_name VARCHAR(256) NOT NULL,
    last_pr_url VARCHAR(256),
    summary VARCHAR(2000),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_role_threads_project_role UNIQUE (project_id, role_tag)
);
