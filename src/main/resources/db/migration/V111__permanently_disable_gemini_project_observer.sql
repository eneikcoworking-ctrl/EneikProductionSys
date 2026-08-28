-- Flyway migration V111: Permanently disable GeminiProjectObserverService
DELETE FROM system_settings WHERE "key" = 'gemini_project_observer_enabled';
INSERT INTO system_settings ("key", "value", updated_at) VALUES ('gemini_project_observer_enabled', 'false', CURRENT_TIMESTAMP);
