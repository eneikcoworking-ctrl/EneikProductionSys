-- V138: Add carrier column to tasks table (ELVIN_GOLDMAN_01_RELIABILITY_CHAIN / D010)
-- Bounded materialization: carrier(τ) ⟺ payload(τ).taskType ≠ ∅ is mirrored in an indexed column
-- to enable O(1) repository-level grouping and counting without full-table JSON scans or in-memory deserialization.

ALTER TABLE tasks ADD COLUMN carrier BOOLEAN DEFAULT FALSE NOT NULL;
CREATE INDEX idx_tasks_carrier ON tasks(carrier);
