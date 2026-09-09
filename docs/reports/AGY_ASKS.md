# Вопросы Антигравити

Пусто. Первая запись — за ней.

## 2026-09-08 Codex: вопрос по `tasks(null)` и carrier-задачам

вопрос: разрешать ли для `SystemStatusService.tasks(null)` отдельный materialized carrier marker/column или repository projection, чтобы считать статусы без `findAll()` и без JSON-предиката по `payload.taskType`?
замер: H2 2.2.224 не знает `JSON_VALUE(payload, '$.taskType')`; строковый fallback по `CAST(payload AS VARCHAR)` даёт ложное совпадение, если `taskType` находится значением другого поля.
почему спрашиваю: `tasks(null)` — чистый summary, но обязан сохранить исключение carrier-задач; неточный JSON-предикат изменит смысл метрики.
комментарий для Антигравити: механизм ещё не идеален, но этот срез нельзя чинить строковым поиском по JSON. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`. Следующий верный ход — либо добавить явный carrier-признак, либо найти проверенный H2/Postgres-совместимый JSON-key predicate и закрепить его интеграционным тестом.

## 2026-09-09 Codex: вопрос по порядку активных ролей `FalsificationCycleService`

вопрос: активные роли в философском и кодовом falsification-cycle должны идти в каноническом порядке `BARCAN-TAG-00` ... `BARCAN-TAG-12`, или текущий порядок `RoleRepository.findAll()`/БД намеренно несёт приоритет и его надо сохранить как есть?
замер: migrations define 13 roles (`V3` seeds 12, `V46` adds `BARCAN-TAG-12`), source tree has 13 top-level charter files, and runtime `/api/roles/{tag}/rules` returns HTTP 200 for all 13 tags. Direct live SQL active-count was not forced because read-only H2 Shell against the Docker volume hits the live file lock while backend is running.
почему спрашиваю: `FalsificationCycleService` uses repository list order for `subList(0, 3)` and remaining-role batches, so changing acquisition to an unordered active finder can alter which philosophers speak in each turn even if count stays 13.
комментарий для Антигравити: механизм не идеален. До ответа не правь role-corpus reads; сначала закрепи reliable acquisition: source, freshness, active predicate and order. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: вопрос по detail-list contract `/api/quality/defect-summary`

вопрос: `GET /api/quality/defect-summary` должен возвращать все `conflicts.items`, `qualityGate.items` and `onboarding.items` как внутренний диагностический дамп, или эти списки должны быть paginated/top-N/project-scoped because UI/operator consumes only a bounded detail view?
замер: grep found no frontend caller for `/api/quality/conflict-dpmo` or `/api/quality/defect-summary`; runtime today returns small payloads (`defect-summary`: 877 bytes, total 5, item counts 5/0/0), but this is not a future bound.
почему спрашиваю: totals are aggregate truth and can be acquired by counts/projections; `items` are row truth and changing them without a declared consumer contract may silently remove evidence an operator expects.
комментарий для Антигравити: механизм не идеален. До ответа не правь `QualityMetricsController` detail lists; сначала отдели aggregate truth от bounded detail truth. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.
