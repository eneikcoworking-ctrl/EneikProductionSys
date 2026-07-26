-- Gemini-context RAG knowledge base (2026-07-25, operator directive: "нужно чтобы Джемини постоянно
-- училась контексту моей системы и в каждом вызове была максимально компетентна... мат. выверенные
-- недорогие по токенам решения"). Stores embedded chunks of standing project knowledge (OBSERVER_LOG,
-- engineering invariants charter, BARCAN role charters) so GeminiContextService can retrieve only the
-- top-k most relevant chunks per call (exact cosine-similarity ranking) instead of ever re-sending the
-- whole corpus - that ranking is the token-cost lever. Re-indexing a source is idempotent (delete-then-
-- insert by source_ref), so editing a doc never leaves stale chunks behind.
CREATE TABLE context_chunks (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    source_ref VARCHAR(256) NOT NULL,
    chunk_index INT NOT NULL,
    content CLOB NOT NULL,
    embedding CLOB NOT NULL,
    embedding_dims INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_context_chunks_source_ref ON context_chunks (source_ref);
