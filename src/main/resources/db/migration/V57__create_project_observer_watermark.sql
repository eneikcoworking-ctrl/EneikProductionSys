-- Fixes a real cost bug (2026-07-25, operator directive: "мы же построили систему чтобы недорого
-- передавать контекст?"): GeminiProjectObserverService was resending the ENTIRE growing project log on
-- every 30-minute cycle instead of only what's new since the last run - directly contradicting this same
-- session's own GeminiContextService (RAG via retrieval, never full-context resend). This watermark table
-- lets the observer send only the incremental window each cycle.
CREATE TABLE project_observer_watermark (
    project_id UUID PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    last_observed_at TIMESTAMP NOT NULL
);
