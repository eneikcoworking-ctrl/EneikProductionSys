-- V125 (2026-08-29, plan §4.31): V124 inserted nothing, and its guard was the reason.
--
-- V124 refused to act while ANY client root brief existed, and one does - the 401/CI-red entry from 28.08.
-- But the question is not whether the client has ever spoken; it is whether THIS thing the client said is
-- still in the system. Guarding a restoration by the presence of some other brief is the same mistake as
-- reading "a reference exists" for "the referent exists" (§4.26): one client entry present says nothing
-- about another being absent. Measured after V124 applied: client root briefs = 1, and it is the 401 one;
-- Moodle still absent from every wishlist, every epic, and the client repository's own file tree.
--
-- The guard here is about this entry and nothing else: its own text. Idempotent by construction - a rerun
-- on a database that already carries it inserts nothing.
--
-- The text is retrieved, not written. It survives verbatim in docs/PROJECT_BRIEF.md of the client's own
-- repository at commit 5cd6c96c (2026-08-26), which held ten client entries and 12,349 bytes before the
-- factory rewrote it down to one, and identically inside the descriptions of three wishlist-compiler
-- carrier tasks from the same day.
INSERT INTO wishlist (id, project_id, source, content, status, created_at, compile_attempts)
SELECT RANDOM_UUID(), p.id, 'client',
       'Интеграция с системой Moodle Института Эпидемиологии: 1. Единая сквозная аутентификация (SSO) через Moodle (OAuth2 / OIDC / LTI). 2. Синхронизация и отображение иерархии ролей Moodle в ролевую модель архива (Администратор, Старший научный сотрудник / Эпидемиолог с правом подписи досье, Исследователь / Аспирант с правом доступа к протоколам). 3. Автоматическое разграничение прав доступа к закрытым штаммам и аналитическим отчетам на основе кафедр и курсов Moodle. 4. Устойчивость к сбоям внешней LMS (автономный fallback).',
       'pending', CURRENT_TIMESTAMP, 0
FROM projects p
WHERE p.slug = 'test-fiftieth'
  AND NOT EXISTS (
      SELECT 1 FROM wishlist w
      WHERE w.project_id = p.id AND CAST(w.content AS VARCHAR) LIKE '%Moodle Института Эпидемиологии%');
