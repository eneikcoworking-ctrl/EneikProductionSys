# Такт L2: Ведущий инженер (2026-09-18 11:10 UTC)

1. Предписание 54 закрыто (PERSISTENCE_SNAPSHOT / D010 Data lineage loss, Derek Parfit 1984):
   - Восстановлена непрерывность тождества сущности задачи во времени для исторических строк (до V137).
   - Создан `TaskContentKeyBackfillService`, на старте (`ApplicationReadyEvent`) находящий задачи с `content_key IS NULL` и детерминированно выводящий ключ из payload: `"compile:<projectId>:<sha256Hex>"` для пачек пожеланий и `"compile-worker:<projectId>"` для постоянных воркеров.
   - Метод вычисления ключа централизован в `ProjectFlowService.compilerContentKeyFromIds`.
   - Идемпотентность защищена маркером `content_key_backfill_completed` в `system_settings`.
2. Заслон (100% green в Docker Maven 3.9.9 Temurin-21, -m 2g):
   - `TaskContentKeyBackfillServiceTest` (3/3 green): вывод ключа из payload, предотвращение дубликатов при повторе старой работы, проверка однократности запуска и пропуска сканирования.
   - `CompilerTaskIdentityTest` (3/3 green), `TaskCarrierBackfillServiceTest` (3/3 green).
3. Следующий такт:
   - Проверить `MANAGER_STATE.md`, взять следующий механизм из очереди (Предписание 55).
