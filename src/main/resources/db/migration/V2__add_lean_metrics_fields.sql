-- V2__add_lean_metrics_fields.sql
-- Migration to add Lean/TOC metrics fields to the greetings table.

-- 1. Add processing_started_at for Cycle Time start tracking
ALTER TABLE greetings ADD COLUMN processing_started_at TIMESTAMP WITH TIME ZONE;

-- 2. Add completed_at for Lead/Cycle Time end tracking
ALTER TABLE greetings ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

-- 3. Add current_status for WIP monitoring and state machine
-- Defaulting to 'COMPLETED' for existing rows to maintain consistency with historical data
ALTER TABLE greetings ADD COLUMN current_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

-- 4. Update historical timestamps for existing records to ensure metrics can be calculated
UPDATE greetings SET
    processing_started_at = created_at,
    completed_at = created_at
WHERE processing_started_at IS NULL;
