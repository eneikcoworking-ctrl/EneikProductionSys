CREATE TABLE jules_sessions (
  id UUID DEFAULT random_uuid() PRIMARY KEY,
  account_id UUID NOT NULL,
  task_id UUID NOT NULL,
  external_session_id VARCHAR(128),
  status VARCHAR(24) NOT NULL DEFAULT 'queued',
     -- значения: queued | running | pr_opened | failed | stuck
  pr_url VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_status_check_at TIMESTAMP WITH TIME ZONE
);

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
