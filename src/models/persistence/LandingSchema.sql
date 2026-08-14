-- @file LandingSchema.sql
-- @agent TAG-08 (Substitutivity Salva Veritate)
-- @description Persistence schema for Greetings with strict type theory.

CREATE TABLE IF NOT EXISTS greetings (
    id UUID PRIMARY KEY,
    message TEXT NOT NULL CHECK (char_length(message) > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Substitutivity check: Ensure no duplicate messages for same identity
CREATE UNIQUE INDEX idx_greetings_message ON greetings(message);
