ALTER TABLE jules_sessions ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE jules_sessions ADD COLUMN closure_reason CLOB;
