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

## 2026-09-09 Codex: вопрос по `SixSigmaAuditService.getActiveProjectId()` fallback

вопрос: `getActiveProjectId()` должен всё ещё считать `orchestrated` допустимым статусом, хотя текущий `ProjectStatus` enum содержит только `active/analyzing/waiting/frozen/accepted/archived`; и если активных проектов нет, можно ли выбирать любой проект как fallback, или надо возвращать null/ошибку/детерминированный статусный порядок?
замер: `SystemAuditController` calls `getActiveProjectId()` when `/api/audit/six-sigma` receives no projectId; `ProjectStatus.java` has no `orchestrated`; `ProjectRepository` already exposes `findByStatusOrderByCreatedAtDesc(ProjectStatus status)`; runtime default delivery endpoint today resolves to project `test-fiftieth`.
почему спрашиваю: changing this resolver to a repository predicate is easy, but changing fallback/order can silently alter Kaizen, ProcessControl and audit endpoints.
комментарий для Антигравити: механизм не идеален. До ответа не правь resolver; сначала зафиксируй reliable active-project acquisition and deterministic fallback. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: вопрос по duplicate Linear id policy for `/internal/tasks/by-linear-id`

вопрос: если несколько `TaskEntity` rows have the same nonblank `linearIssueId`, что является правильным механизмом для `/internal/tasks/by-linear-id/{linearIssueId}`: вернуть `409 conflict`, выбрать детерминированную строку по status/project/createdAt, or enforce database uniqueness for `tasks.linear_issue_id`?
замер: `InternalTaskController.getTaskByLinearId` currently does `taskRepository.findAll().stream().filter(...).findFirst()`; migrations show `tasks.linear_issue_id VARCHAR(64)` without a visible unique constraint; `TaskRepository` has only `findByLinearIssueIdIsNotNull()` and `findByProjectIdAndLinearIssueIdIsNotNull(...)`, no exact finder; `scripts/modules/db_utils.py:update_task_status_by_linear_id` consumes this endpoint for status updates.
почему спрашиваю: replacing the scan with a simple exact repository finder before defining duplicate behavior would only make an under-specified answer faster and could hide or freeze the wrong task identity.
комментарий для Антигравити: механизм не идеален. До ответа не правь `InternalTaskController.getTaskByLinearId`; сначала зафиксируй duplicate Linear issue id policy as conflict, deterministic domain selection or database uniqueness. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: вопрос по live consumer for `FlowMetricsService`

вопрос: `FlowMetricsService.computeForProject(projectId)` должен стать live-механизмом, и если да, кто обязан потреблять Little's Law inconsistency: dashboard/system-status, Kaizen proposal writer, ProcessControl/operational truth, or a scheduled evidence writer? Or is this service a retired diagnostic whose code should not be optimized until it is wired?
замер: exact main-source grep for `FlowMetricsService` returns only the class declaration/constructor/logger; no production `flowMetricsService.computeForProject(...)` caller exists. `FlowMetricsServiceTest` verifies the math, and repository methods exist for project-scoped task/session/wishlist acquisition.
почему спрашиваю: changing `taskRepository.findAll()` inside an unwired service reduces a counter but does not improve a mechanism; the ideal record requires a named consumer that records/exposes persistent Little's Law inconsistency.
комментарий для Антигравити: механизм не идеален. До ответа не правь `FlowMetricsService.computeForProject`; first decide whether Little's Law inconsistency is dashboard truth, Kaizen evidence, ProcessControl evidence, scheduled evidence, or retired diagnostic. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: вопрос по bounded lever-key registry for LeverPromotionService

Вопрос: is `lever_promotion_state` bounded by a declared finite lever-key registry, and if so which source owns the registry and freshness check? Or can arbitrary runtime keys grow over time, requiring `LeverPromotionService.evaluatePromotions()` to read only active/recent observation-bearing state rows and separately quarantine stale/unknown keys?

Почему это blocker: `recordObservation(String leverKey, ...)` and `currentStage(String leverKey)` accept open strings, while `evaluatePromotions()` currently scans every state row. Without this answer a repository predicate could make the scan cheaper but still preserve a false mechanism: stale typo-created keys could keep participating in promotion cadence.


## 2026-09-09 Codex: вопрос по ORCHESTRATOR_SYSTEM carrier project for MarketResearchService

вопрос: какой источник проекта должен нести `ORCHESTRATOR_SYSTEM` market-research tasks in `MarketResearchService.createResearchTask`: configured factory project, newest active project, dedicated carrier project, or explicit error when none exists? What is the legal fallback order, and may archived/accepted projects ever carry those tasks?

замер: line 69 currently uses `projectRepository.findAll().stream().findFirst()` while the comment says "most recent". `JulesDispatchService` chooses the real repository from `TargetContext.ORCHESTRATOR_SYSTEM` and `system_orchestrator_repository_name`, so this project row is bookkeeping/UI/accounting carrier, not the client repo target.

почему спрашиваю: replacing the full-table read before this policy is defined can still attach factory-owned research to the wrong project identity and make normal task/session views misleading.

комментарий для Антигравити: механизм не идеален. До ответа не правь `MarketResearchService.createResearchTask`; first declare the carrier project policy and fallback, then replace the acquisition while preserving queued dispatch, role `BARCAN-TAG-09`, sample bounds 5..40 and `TargetContext.ORCHESTRATOR_SYSTEM`. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Codex: вопрос по legacy `/api/jules-configs` surface

вопрос: what is the intended fate of `JulesConfigController` and `/api/jules-configs`: retire/delete the legacy surface, bridge it into the canonical `accounts` pool, or keep it as an explicitly inert legacy admin surface? If kept, what operator-visible text or status proves it does not control dispatch?

замер: `V17__create_jules_configs.sql` created `jules_configs`; `V19__restructure_accounts_and_projects.sql` migrated data into `accounts` and drops `jules_configs`, while `JulesConfigController` still exposes list/create/update/delete and `JulesConfigEntity`/`JulesConfigRepository` still exist. Dispatch capacity now reads account records, not this old table.

почему спрашиваю: without a declared fate, a POST/PUT to `/api/jules-configs` can appear to configure Jules while not changing the mechanism that actually dispatches sessions, creating two configuration worlds.

комментарий для Антигравити: механизм не идеален. До ответа не правь `JulesConfigController`; first choose retired / accounts bridge / explicitly inert legacy surface, then test that operator-visible behavior matches the chosen world. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; local pattern `WORLD_VERSION_MAP`; common background `ACP-061 Hoare Triple Review`.
