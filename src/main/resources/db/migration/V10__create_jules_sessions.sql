CREATE TABLE jules_sessions (
  id UUID PRIMARY KEY,
  account_id UUID,
  task_id UUID NOT NULL,
  external_session_id VARCHAR(128),
  status VARCHAR(24) NOT NULL DEFAULT 'queued',
  pr_url VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  last_status_check_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_jules_sessions_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_jules_sessions_task FOREIGN KEY (task_id) REFERENCES tasks(id)
);
