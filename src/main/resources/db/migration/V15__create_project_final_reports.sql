CREATE TABLE project_final_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL,
  total_tasks_completed INT,
  total_wishlist_items INT,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  report_content JSONB
);
