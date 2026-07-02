CREATE TABLE pr_reviews (
  id UUID DEFAULT random_uuid() PRIMARY KEY,
  jules_session_id UUID NOT NULL,
  pr_url VARCHAR(256) NOT NULL,
  ci_status VARCHAR(16) NOT NULL,
  diff_summary TEXT,
  lines_changed INT,
  files_changed INT,
  has_test_changes BOOLEAN,
  touches_critical_path BOOLEAN,
  risk_level VARCHAR(8) NOT NULL,
  screenshot_urls TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
