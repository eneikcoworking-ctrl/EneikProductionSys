-- V124 (2026-08-29, plan §4.31): the client's own brief is put back, verbatim, from the artifact that
-- still carries it.
--
-- Measured that day. The word "moodle" appears in no wishlist and no epic; of 37 epics only twelve have a
-- root brief and none is this one; the oldest surviving wishlist is from 28.08 01:51 while 322 tasks
-- predate 27.08, so wishlists were lost while tasks were kept. The client repository's own tree settles it
-- independently: 407 files, not one whose name contains moodle, oauth, oidc, lti or sso, and authentication
-- is local and password-based. The brief was never built and no longer existed to be built.
--
-- The text below is not written here, it is RETRIEVED: it survives word for word inside the descriptions of
-- three wishlist-compiler carrier tasks from 2026-08-26 (2058b47b, af9f82d8, d9b879d5), under "Brief #1:".
-- This codebase already recovers briefs this way and it has been observed doing it - "O-16 recovery:
-- compiler task ... rebuilt its N brief(s) from the plan already merged". Same instrument as V117, V122 and
-- V123: one correction, in the data, once.
--
-- Guarded twice: only for the one active project, and only while no client root brief exists - so a rerun
-- on a database that already has one changes nothing.
INSERT INTO wishlist (id, project_id, source, content, status, created_at, compile_attempts)
SELECT RANDOM_UUID(), p.id, 'client',
       'Интеграция с системой Moodle Института Эпидемиологии: 1. Единая сквозная аутентификация (SSO) через Moodle (OAuth2 / OIDC / LTI). 2. Синхронизация и отображение иерархии ролей Moodle в ролевую модель архива (Администратор, Старший научный сотрудник / Эпидемиолог с правом подписи досье, Исследователь / Аспирант с правом доступа к протоколам). 3. Автоматическое разграничение прав доступа к закрытым штаммам и аналитическим отчетам на основе кафедр и курсов Moodle. 4. Устойчивость к сбоям внешней LMS (автономный fallback).',
       'pending', CURRENT_TIMESTAMP, 0
FROM projects p
WHERE p.slug = 'test-fiftieth'
  AND NOT EXISTS (
      SELECT 1 FROM wishlist w
      WHERE w.project_id = p.id AND w.source = 'client' AND w.origin_wishlist_id IS NULL);
