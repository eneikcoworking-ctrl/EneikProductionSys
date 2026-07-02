CREATE TABLE jules_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL,
  task_id UUID NOT NULL,
  external_session_id VARCHAR(128),
  status VARCHAR(24) NOT NULL DEFAULT 'queued',
     -- значения: queued | running | pr_opened | failed | stuck
  pr_url VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_status_check_at TIMESTAMPTZ
);

CREATE TABLE pr_reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
