-- Redesign (2026-07-25): GeminiProjectObserverService no longer consumes the backend's own internal
-- Logback log. Both tables were live-applied by today's earlier deploy (V56/V57) - never delete an
-- already-applied migration file, so this drops them forward instead. Nothing else in the codebase reads
-- either table (confirmed via grep before removing their producing code).
DROP TABLE IF EXISTS project_event_log;
DROP TABLE IF EXISTS project_observer_watermark;
