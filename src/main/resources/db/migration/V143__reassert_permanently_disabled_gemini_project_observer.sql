-- Flyway migration V143: Reassert permanently disabled gemini_project_observer_enabled (D013, Prescription 58)
-- Forensic audit showed row was overwritten to 'true' post-V111 on 2026-08-26 21:09:48.
DELETE FROM system_settings WHERE "key" = 'gemini_project_observer_enabled';
INSERT INTO system_settings ("key", "value", updated_at) VALUES ('gemini_project_observer_enabled', 'false', CURRENT_TIMESTAMP);
