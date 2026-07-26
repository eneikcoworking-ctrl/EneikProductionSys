-- 2026-07-26 operator directive ("даем ей все полномочия - кроме кода"): the observer moves from
-- report-only to a small set of real, reversible operational actions (dismiss dead wishlists, nudge stuck
-- sessions, abandon dead conflicts, boost priority, trigger an early falsification pass). Every action she
-- takes is persisted here as an independently-checkable audit trail (testimony vs evidence - her own
-- journal prose is never the only record of what she actually did).
CREATE TABLE gemini_observer_actions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    tool VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    reason CLOB,
    outcome VARCHAR(32) NOT NULL,
    detail VARCHAR(512)
);

CREATE INDEX idx_gemini_observer_actions_project_created
    ON gemini_observer_actions (project_id, created_at DESC);

-- Readiness ratio at the time of this journal entry, so stagnation ("has this number genuinely not moved
-- across the last N cycles") can be computed by comparing across journal rows instead of needing a separate
-- time-series table - nullable because older rows (pre-this-migration) never recorded it.
ALTER TABLE gemini_observer_journal ADD COLUMN readiness_ratio DOUBLE;
