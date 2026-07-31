ALTER TABLE gemini_observer_journal ADD COLUMN gemini_called BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE gemini_observer_journal ADD COLUMN anomaly_fingerprints CLOB;

ALTER TABLE gemini_observer_actions ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
