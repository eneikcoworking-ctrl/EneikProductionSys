-- V74: SessionLifecycleService (2026-08-01, operator: "те сессии джулс, которые не продуктовые...
-- можно убирать в архив или удалять"). Confirmed live against Jules's real API that DELETE
-- /v1alpha/sessions/{session} genuinely removes a session server-side - before this our own "cancelled"
-- convention never told Jules anything. Non-null means the remote deletion was actually confirmed, not
-- just attempted.
ALTER TABLE jules_sessions ADD COLUMN remote_deleted_at TIMESTAMP;
