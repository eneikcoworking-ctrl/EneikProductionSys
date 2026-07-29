-- Default gemini_project_observer_enabled to true for continuous background observation
INSERT INTO system_settings ("key", "value", updated_at) VALUES ('gemini_project_observer_enabled', 'true', CURRENT_TIMESTAMP);
