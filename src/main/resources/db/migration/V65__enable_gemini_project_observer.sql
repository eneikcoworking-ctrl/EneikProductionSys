-- Delete existing setting if present to prevent unique constraint failure, then insert true
DELETE FROM system_settings WHERE "key" = 'gemini_project_observer_enabled';
INSERT INTO system_settings ("key", "value", updated_at) VALUES ('gemini_project_observer_enabled', 'true', CURRENT_TIMESTAMP);
