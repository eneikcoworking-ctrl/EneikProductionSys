CREATE TABLE project_final_reports (
  id UUID DEFAULT random_uuid() PRIMARY KEY,
  project_id UUID NOT NULL,
  total_tasks_completed INT,
  total_wishlist_items INT,
  generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  report_content JSON
);
