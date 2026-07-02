CREATE TABLE project_generation_state (
  project_id UUID PRIMARY KEY,
  is_generation_stopped BOOLEAN NOT NULL DEFAULT false,
  stopped_at TIMESTAMPTZ
);
