-- V1__initial_schema.sql
-- Based on LandingSchema.sql but adapted for JPA and Flyway

CREATE TABLE IF NOT EXISTS greetings (
    id UUID PRIMARY KEY,
    message TEXT NOT NULL CHECK (char_length(message) > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_greetings_message ON greetings(message);
