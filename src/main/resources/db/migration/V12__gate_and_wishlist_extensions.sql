-- V9__gate_and_wishlist_extensions.sql

ALTER TABLE wishlist ADD COLUMN jtbd TEXT;
ALTER TABLE wishlist ADD COLUMN lean_value VARCHAR(16);
ALTER TABLE wishlist ADD COLUMN toc_constraint_ref TEXT;
ALTER TABLE wishlist ADD COLUMN six_sigma_metric TEXT;
ALTER TABLE wishlist ADD COLUMN dod TEXT;
ALTER TABLE wishlist ADD COLUMN acceptance_criteria TEXT;
ALTER TABLE wishlist ADD COLUMN compiled_by_role VARCHAR(16);

ALTER TABLE tasks ADD COLUMN quality_gate_passed BOOLEAN DEFAULT false;
ALTER TABLE tasks ADD COLUMN quality_gate_report JSON; -- Using JSON for H2 compatibility as seen in V3

CREATE TABLE project_generation_state (
  project_id UUID PRIMARY KEY,
  is_generation_stopped BOOLEAN NOT NULL DEFAULT false,
  stopped_at TIMESTAMP WITH TIME ZONE
);
