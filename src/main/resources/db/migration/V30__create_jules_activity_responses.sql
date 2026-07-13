CREATE TABLE jules_activity_responses (
  id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
  jules_session_id UUID NOT NULL,
  activity_name VARCHAR(256) NOT NULL,
  activity_hash VARCHAR(64) NOT NULL,
  question CLOB NOT NULL,
  response CLOB,
  sent BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  responded_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_jules_activity_responses_session FOREIGN KEY (jules_session_id) REFERENCES jules_sessions(id)
);

CREATE UNIQUE INDEX ux_jules_activity_response_session_hash
  ON jules_activity_responses (jules_session_id, activity_hash);
