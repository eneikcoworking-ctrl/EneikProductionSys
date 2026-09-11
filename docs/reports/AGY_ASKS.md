# Вопросы Антигравити

## Выполненные задачи Antigravity (L2)

### 2026-09-10 Antigravity: Консолидация SystemStatusService
- **Что сделано:** Оптимизирован горячий путь `getStatus(UUID projectId)` (ежеминутная оркестрация): устранено 8-кратное повторное вычитывание `projectTasks` в секциях (`julesSessions`, `qualityGate`, `operationalBlockers`, `tasks`, `emsMetrics`, `conflictDpmo`). Задачи берутся строго один раз и пробрасываются в секции. Секция `sixSigma` теперь напрямую потребляет данные `qualitySection` и `conflictSection`, исключая дублирующий перезапуск `qualityGate` и `conflictDpmo`. В `accounts(null)` внедрён `findAllByOrderByNameAsc()`.
- **Заслоняющие тесты:** В `SystemStatusServiceTest` добавлены тесты `getStatusConsolidatesTaskAcquisitionToOneQuery` (проверка вызова `times(1)`) и `getStatusNullProjectDoesNotQueryByProjectId`. Все 11 юнит-тестов и 4 интеграционных теста `SystemStatusControllerIntegrationTest` пройдены успешно.
- **Статус открытых вопросов:** Вопрос по `tasks(null)` carrier-предикату остаётся открытым до решения оператора (json key vs column marker); до этого момента `tasks(null)` в коде не искажался неточными json-парсерами.

### 2026-09-11 Antigravity: Такт 2 — QualityGateController и SixSigmaAuditService
- **Сделано:** 
  1. `QualityGateController.getDefectRate` делегирован в `SixSigmaAuditService.computeQualityGateDefectRate(projectId)`. Ликвидирован дублирующий `taskRepository.findAll()` и расхождение формул.
  2. Внедрена 3-значная классификация проверок по Белнапу (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): проверки без поля `passed` больше не роняют контроллер (NPE) и не засчитываются молча как пройденные (`asBoolean(true)`), а явно выводятся в ответе как `undetermined`.
  3. В `TaskRepository` добавлен `findByProjectIdAndQualityGateReportIsNotNull(projectId)`. В `computeCtqBreakdown` и `computeQualityGateCounts` устранены `findAll()` и фильтрация в памяти.
- **Чем проверено:**
  1. `QualityGateControllerTest` (2/2 green): проверка делегирования в глобальном и проектном разрезе.
  2. `SixSigmaAuditServiceTest` (16/16 green): тест `computeQualityGateDefectRateCategorizesMissingPassedFieldAsUndetermined` проверяет точный подсчёт `undetermined` без падения и заслоняет `never().findAll()`.
  3. `SystemStatusServiceTest` (11/11 green): регрессионная целостность статусного свода сохранена.

### 2026-09-11 Antigravity: Такт 3 — TocSentinelService целиком (пункты 4 и 6 очереди, Раздел XXXVII)
- **Что сделано:**
  1. **Анти-зеркальная телеметрия (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013):** `TocOptimizer` сохраняет кэшированный снимок `latestDbrStatus`. Метод `TocSentinelService.getDbrStatus()` теперь выполняет чистое чтение без вызова `evaluateConstraintsAndDbr()`, не рассчитывает arrival rate, не перезаписывает загрузку узлов (`setUtilization`) и не сбрасывает признаки главного ограничения (`setPrimaryConstraint`). Пересчёт выполняется только сторожем или при явном вызове `refreshDbrStatus()`.
  2. **Инкапсуляция владения графом и единственный владелец счётчиков (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004):** Ликвидирована утечка внутренних мутируемых компонентов — геттеры `getGraph()`, `getAnomalyDetector()`, `getOptimizer()` полностью удалены из `TocSentinelService`. Необходимые вызовы проброшены через методы фасада: `getToken(id)`, `getNode(name)`, `getAllNodes()` (немодифицируемая коллекция), `getEdges()` (немодифицируемая коллекция), `getActiveTokenCount()`, `getGlobalArrivalRatePerSec()`, `getCompletedCountAllNodes()`, `getMaxBufferCapacity()`, `setMaxBufferCapacity(capacity)`. Вызывающие сервисы (`KaizenService`, `SixSigmaAuditService`, `TocSentinelController`) переведены на публичные методы сервиса. Инкремент `inFlightCount` узла перенесён из `TocAnomalyDetector` в `TocSentinelService.enterStep`, устранив ситуацию двух сервисов-писателей для одного поля.
  3. **Выведенная динамическая частота обхода (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008):** Фиксированная константа 2000 мс и необоснованный комментарий полностью устранены. `TocSentinelService` реализует `SchedulingConfigurer` через динамический Spring `Trigger`: период вычисляется как половина кратчайшей наблюдённой длительности шага в графе (`min(meanDurationMs) / 2` по критерию Найквиста–Шеннона), зажат объявленными границами `[minCadenceMs, maxCadenceMs]`. При отсутствии активной работы в графе (`getActiveTokenCount() == 0`) сторож расслабляется до верхнего предела (`maxCadenceMs = 10000ms`), даже если узлы имеют историю завершений. При появлении активных токенов в работе частота динамически адаптируется под реальную скорость шагов.
- **Чем проверено:**
  1. `TocSentinelServiceTest`:
     - `getDbrStatusDoesNotMutateGraphOrConstraintState`: 10 последовательных вызовов `getDbrStatus()` оставляют загрузку узла, признак главного ограничения и `lastEvaluatedAt` неизменными.
     - `partWholeEncapsulationEnforcedWithoutLeakyComponentGetters`: рефлексивный запрет `getGraph`, `getAnomalyDetector`, `getOptimizer`; проверка неизменяемости коллекций узлов и ребер (`UnsupportedOperationException`).
     - `nodeWithCompletionsAndZeroWorkInFlightRelaxesToMaxBound`: узел имеет 5 завершений с mean 1000ms, но 0 активных токенов в работе -> сторож расслабляется до maxCadenceMs (10000 мс).
     - `nodeInFlightWithMean1000msDerivesCadence500ms`: при наличии токена в работе на узле с mean 1000ms выводится период 500 мс (1000/2).
     - `activeWorkClampsToLowerAndUpperBounds`: быстрый шаг с mean 100ms зажимается в lower bound (250 мс); медленный шаг с mean 60000ms зажимается в upper bound (10000 мс).
     - `rejectedCycleStepDoesNotIncrementInFlightCounter`: шаг, отклоненный по циклу (CYCLE_ABORTED), не увеличивает `inFlightCount` узла.
     - `derivedCadenceAdaptsDynamicallyToObservedStepDurations`: при наблюдении шага 1200 мс период равен 600 мс; при появлении более быстрого шага 500 мс период перестраивается на 250 мс (опровержение: период динамически меняется).
     - `inFlightCounterOwnedExclusivelyBySentinelServiceLifecycle`: единый владелец `inFlightCount` через `enterStep`/`exitStep`.
     - `watchdogThrottlingSubordinationAndExplicitRefresh`: проверка пересчёта по `refreshDbrStatus()` и `periodicWatchdog()`, срабатывание DBR-троттлинга при превышении буфера.
  2. `TocSentinelControllerTest` (2/2 green): корректность работы REST-эндпоинтов `/api/toc/status`, `/constraint`, `/graph`, `/anomalies`, `/event/enter`, `/event/exit`, `/resource/*` через инкапсулированный фасад.
  3. `KaizenServiceTest` (9/9 green): адаптивная подстройка DBR-буфера через `tocSentinelService.getMaxBufferCapacity()` / `setMaxBufferCapacity()`.
  4. `SixSigmaAuditServiceTest` (16/16 green): расчет возможностей через `tocSentinelService.getCompletedCountAllNodes()`.
  5. `SystemStatusServiceTest` (11/11 green): полная регрессионная целостность статусного свода.
- **Что берётся следующим:** Такт 4 — `SystemStatusService` целиком по директиве из `docs/reports/AGY_NEXT.md` (четыре `findAll()` на пути без проекта, столбец признака носителя, недоступность аккаунта по `status` и `enabled` с фиксацией сработавшего условия, падающий тест на краснеющую сводку при выключенных аккаунтах).



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

## 2026-09-10 Codex: вопрос по absent evidence in `EpistemicLayerInvariantGate`

вопрос: when `EpistemicLayerInvariantGate` supports a periphery-role task, but feature/session/file evidence is absent or unreadable, what is the intended ideal result: pass as non-applicable, abstain/unverified with explicit reason, or fail/refuse until real PR/file evidence exists?

замер: `EpistemicLayerInvariantGate.check` returns passed when task/project/feature data is missing, when the feature row is absent or not marked `PERIPHERY`, and when no Jules session exists; unlike `BackendContractGate`, `DesignExcellenceGate` and `VerificationEvidenceGate`, it currently evaluates `task.fileScope` rather than the real PR diff. Historical docs also record that the gate instrument applied to zero of 365 tasks on `test-fiftieth` while criterion judgement handled 127.

почему спрашиваю: changing this gate without the policy can either block repair work unnecessarily or continue treating missing evidence as permission. The ideal mechanism needs one declared meaning for absence before code changes.

комментарий для Антигравити: механизм не идеален. До ответа не правь `EpistemicLayerInvariantGate`; first declare absent-evidence semantics and whether file-scope or real PR diff owns the CORE/PERIPHERY boundary. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
