-- Gemini's own continuity record (2026-07-25 redesign): she writes one entry per observation cycle for her
-- own future reference, replacing the backend's internal log as the observer's continuity mechanism.
CREATE TABLE gemini_observer_journal (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    entry CLOB NOT NULL,
    findings_count INT NOT NULL
);

CREATE INDEX idx_gemini_observer_journal_project_created
    ON gemini_observer_journal (project_id, created_at DESC);
