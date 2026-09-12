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

### 2026-09-11 Antigravity: Такт 4 — SystemStatusService целиком (пункт 1 очереди, Раздел XXXVI)
- **Что сделано:** Механизм `SystemStatusService` доведён до идеала целиком:
  1. **Ликвидация full-table reads (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010):**
     - Введена миграция Flyway `V138__add_carrier_column_to_tasks.sql`: столбец `carrier BOOLEAN DEFAULT FALSE NOT NULL` с индексом `idx_tasks_carrier` в таблице `tasks`.
     - `TaskEntity.java`: поле `carrier` синхронизируется через `@PrePersist` и `@PreUpdate` из `payload.taskType`.
     - Создан `TaskCarrierBackfillService` (`@EventListener(ApplicationReadyEvent.class)`): однократный Java-парсинг legacy-задач через Jackson, исключающий несовместимости диалектов SQL в H2 и PostgreSQL.
     - `TaskRepository.java`: добавлены хранилищные агрегаты `countNonCarrierTasksByStatus()` и `countNonCarrierTasksByProjectIdAndStatus(projectId)`.
     - `tasks(null)`: статусы считаются в хранилище без вычитки сущностей в память JVM.
     - `operationalBlockers` и `emsMetrics`: переведены на детерминированные упорядоченные методы `findAllByOrderByCreatedAtDesc()`.
     - Замер по `SystemStatusService.java`: ровно **0** вызовов `.findAll()`.
  2. **Снятие категориальной ошибки доступности аккаунтов (инцидент 5 сентября, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - Доступность аккаунта строго формализована как конъюнкция: `account.isEnabled() && account.getStatus() == AccountStatus.idle`.
     - В `accounts(projectId)` добавлены метрики `available` и `disabled`. Расчёт `effectiveOperational` теперь строго отсекает выключенные аккаунты (`!enabled`).
     - При `effectiveOperational == 0` секция `accounts` принимает `status = "blocked"` и явно возвращает `unavailabilityReason` с названием сработавшего условия (например, `"all operational accounts are disabled (enabled == false)"` или `"all operational accounts are daily_limited (status == daily_limited)"`).
     - В каждом элементе `accountItem` возвращаются поля `available: boolean` и `unavailabilityReason: String` (например, `"enabled == false"`).
     - В `operationalBlockers` добавлен блокер `account_capacity` (критическая серьезность), запрещающий статус `"ok"`, если все операционные аккаунты выключены (`enabled == false`).
  3. **Фальсифицирующий заслон (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008):**
     - В `SystemStatusServiceTest` добавлен тест `allAccountsDisabledWithIdleStatusCausesSummaryToReddenAndNameCondition`, воссоздающий точные условия инцидента 5 сентября: 2 аккаунта `idle`, но `enabled = false`. Тест подтверждает, что сводка краснеет (`status = "blocked"` в `accounts` и `operationalBlockers`), а отказ явно называет условие `"enabled == false"`.
     - Добавлен парный тест `allAccountsEnabledWithIdleStatusReportsOk`, подтверждающий переход в `status = "ok"` при включенных аккаунтах.
     - Добавлен тест `tasksNullProjectUsesRepositoryCountsAndNeverFindAll`, подтверждающий хранилищный подсчёт и заслон `never().findAll()`.
     - Создан юнит-тест `TaskCarrierBackfillServiceTest` (2/2 green).
- **Чем проверено:**
  1. `grep -n "\.findAll()" src/main/java/com/eneik/production/services/dashboard/SystemStatusService.java` -> 0 совпадений.
  2. Прогон в контейнере Maven: `SystemStatusServiceTest` (14/14), `TaskCarrierBackfillServiceTest` (2/2), `SystemStatusControllerIntegrationTest` (4/4), `TocSentinelServiceTest` (15/15) — все 35 тестов пройдены успешно (BUILD SUCCESS).
- **Что берётся следующим:** Такт 5 — `SixSigmaAuditService` целиком по директиве оператора: только status `active`, без `orchestrated`, без запасного «любой проект»; если активного проекта нет — статус не определён. Устранение категориальной путаницы между качеством проекта, качеством поставки (`calculateProjectSixSigmaAudit`) и фабрики.

### 2026-09-11 Antigravity: Такт 5 — SixSigmaAuditService целиком и оптимизация carrier-досыпки
- **Что сделано:** Механизм `SixSigmaAuditService` доведён до идеала целиком (Разделы XXV, XXXIX, 10-такт 5/10), а также полностью закрыты замечания Клода по досыпке carrier и `SystemStatusService`:
  1. **Ликвидация 6 точек `findAll()` и изоляция слоёв (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010):**
     - В `SixSigmaAuditService.java` ликвидированы все 6 вызовов `.findAll()` (`grep` даёт ровно 0).
     - `getActiveProjectId()`: строгий предикат `projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`. Несуществующий статус `orchestrated` удалён. Ликвидирован произвольный fallback на первый попавшийся проект: при отсутствии активного проекта возвращается `null` (статус не определён, «неизвестное не становится ответом», `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012). Зафиксирован инвариант «Завод за 1 раз делает 1 проект»: при обнаружении >1 активных проектов логируется предупреждение и возвращается `null`.
      - Разделены три слоя абстракции качества по Голдману (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`):
        - Слой 1 (Фабрика): `calculateFullSixSigmaAudit()` — межпроектный аудит factory-wide с интеграцией рантайм-аномалий TOC.
        - Слой 2 (Поставка): канонический метод `calculateDeliverySixSigmaAudit(UUID projectId)` устраняет категориальное смешение. При отсутствии активного проекта (`projectId == null` и `getActiveProjectId() == null`) возвращает явный отчет с `projectName = "NO_ACTIVE_PROJECT"`, `qualityTier = "UNDETERMINED"`, `sigmaLevel = 0.0`, не подменяя качество поставки фабричными числами (`calculateProjectSixSigmaAudit` сохранён как делегирующий алиас).
        - Слой 3 (Продукт): `calculateProductLayerSixSigmaAudit(UUID projectId)` резолвит активный проект; если активного проекта нет — возвращает явный отчёт с `projectName = "NO_ACTIVE_PROJECT"`, `qualityTier = "UNDETERMINED"`, `sigmaLevel = 0.0`.
      - Категория C (находки онбординга): `onboardingAuditFindingRepository.findAll()` заменён на `countByProjectId(targetProjectId)` и `count()`.
      - Подсчёт конфликтов и мержей: ликвидирован `findAll()` по `prReviewRepository` и `taskConflictRepository`. Глобальный расчёт переведён на `countByMergedTrue()` и `count()`. Проектный/фичевый расчёт строго изолирован через `findByFeatureId` / `findByProjectIdOrderByCreatedAtDesc` -> `julesSessionRepository.findByTaskIdIn` -> `prReviewRepository.findByJulesSessionIdInAndMergedTrue` -> `taskConflictRepository.findByTaskIdIn`.
  2. **Оптимизация досыпки carrier и `SystemStatusService` по совету Клода:**
     - `TaskCarrierBackfillService`: внедрён персистентный маркер завершения `carrier_backfill_completed` в `system_settings` через `JdbcTemplate`. При повторных стартах сервис делает ровно один быстрый запрос по первичному ключу и выходит (0 прочитанных задач, 0 обновлений). В `TaskEntity` признак `carrier` синхронизируется на лету (`@PrePersist` / `@PreUpdate`), а `isCarrier()` напрямую возвращает поле `carrier`.
     - `SystemStatusService.emsMetrics`: при `projectId == null` сервис резолвит `getActiveProjectId()`. Если активного проекта нет — возвращает секцию с `status: "undetermined"` и причиной `"no active project found to compute ems flow metrics"`, не генерируя фиктивные нули как свершившийся факт. При наличии активного проекта вычитывает задачи и пожелания только этого проекта, исключая чтение всей базы.
- **Чем проверено:**
  1. `grep -n "\.findAll()" src/main/java/com/eneik/production/services/audit/SixSigmaAuditService.java` -> ровно 0 совпадений.
  2. Прогон Maven в Docker (`BUILD SUCCESS`):
     - `SixSigmaAuditServiceTest`: 25/25 green (расширено до 25 тестов, включая заслоны `never().findAll()`, многопроектную неопределенность, слои абстракции, lineage и возврат UNDETERMINED при отсутствии активного проекта с проверкой отличия от фабрики).
     - `QualityGateControllerTest`: 2/2 green.
     - `SystemStatusServiceTest`: 16/16 green (включая проверку, что emsMetrics при null projectId запрашивает только активный проект, а при отсутствии активного проекта возвращает секцию undetermined с причиной и не вызывает findAll/build).
     - `TaskCarrierBackfillServiceTest`: 3/3 green (включая проверку пропуска сканирования задач при наличии маркера в system_settings).
     - `SystemStatusControllerIntegrationTest`: 4/4 green (полный подъем Spring Boot Web, H2, Flyway V138).
### 2026-09-11 Antigravity: Такт 6 — ProjectEventLogRetentionService целиком (пункт 5 очереди, Раздел XLIV)
- **Что сделано:** Механизм `ProjectEventLogRetentionService` доведён до идеала целиком по замерам Клода (скорость роста ~2566 строк/час):
  1. **Частота очистки приведена в соответствие со скоростью роста (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010):**
     - Устранён суточный cron `0 17 3 * * ?` (число, выбранное однажды), из-за которого за сутки набегало 36 382 излишних записей и таблица удваивалась в памяти.
     - Внедрён непрерывный цикл проверки: `@Scheduled(fixedDelayString = "${project-event-log.retention-fixed-delay-ms:60000}", initialDelayString = "${project-event-log.retention-initial-delay-ms:30000}")`.
     - Каждую минуту служба выполняет быстрый `countByProjectId(projectId)` (запрос `COUNT(*)`, 0 строк в памяти). При превышении потолка 20 000 записей излишек срезается немедленно, так что превышение потолка составляет не более ~40 строк (0.2% вместо 180% при 36к строках).
  2. **Ликвидация подъёма излишка в память JVM (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010):**
     - В `trimToCeiling` ликвидирован `PageRequest.of(0, excess)`, загружавший в кучу JVM весь массив излишних сущностей (до 36 382 объектов) ради одной временной метки.
     - Запрос переведён на точечный срез граничной строки: `PageRequest.of(excess - 1, 1)`. Размер страницы инвариантен и строго равен 1 независимо от величины излишка.
  3. **Сохранены сильные свойства:** непринятый проект не чистится по возрасту («полный журнал от начала до приёмки»); принятый проект хранится 30 дней; короткие изолированные транзакции по каждому проекту; вызов через self-прокси; сохранение новейших записей.
- **Чем проверено:**
  1. `ProjectEventLogRetentionServiceTest`: 10/10 green:
     - `trimToCeilingRequestsSingleRowPageEvenWithLargeExcess`: проверка `pageSize == 1` и `pageNumber == 36381` при `excess = 36382`.
     - `retentionIsConfiguredWithFrequentFixedDelayNotDailyCron`: рефлексивная фальсификация суточного cron и проверка `fixedDelayString`.
     - `enforceRetentionTrimsExcessInSingleCycleWhenCountExceedsCeiling`: проверка подрезки излишка за один цикл с `pageSize == 1`.
  2. Полный регрессионный прогон Maven в Docker (`BUILD SUCCESS`):
     - `ProjectEventLogRetentionServiceTest`: 10/10 green.
     - `QualityGateControllerTest`: 2/2 green.
     - `TaskCarrierBackfillServiceTest`: 3/3 green.
     - `SystemStatusServiceTest`: 16/16 green.
     - `SixSigmaAuditServiceTest`: 25/25 green.
     Итого: 56 тестов зелёные.
- **Что берётся следующим:** Такт 7 — завершён в текущем такте (см. ниже).

### 2026-09-11 Antigravity: Такт 7 — Устранение сбоя заведения задач компилятора ProjectFlowService.compilerContentKey (V137, D010)
- **Что сделано:** Устранён живой сбой конвейера, приводивший с 9 сентября к ежеминутному падению оркестрации с ошибкой `Value too long for column "CONTENT_KEY CHARACTER VARYING(255)": "'compile:a716e82e-…' (525)"`:
  1. **Ограниченное представление тождества работы (`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT`, `SOL_KRIPKE_13_PERSISTENCE_SNAPSHOT` / D010 Data lineage loss):**
     - В `ProjectFlowService.compilerContentKey` ликвидирована наивная конкатенация UUID пожеланий через запятую (`"compile:" + project.getId() + ":" + ids`), длина которой неограниченно росла с размером пачки и превышала предел столбца `tasks.content_key VARCHAR(255)` (V137) при $\ge 15$ пожеланиях.
     - Внедрено детерминированное хеширование канонически отсортированных UUID пожеланий алгоритмом SHA-256: `"compile:" + project.getId() + ":" + sha256Hex(sortedWishlistIds)`.
     - Длина ключа строго зафиксирована на 109 символах (`8 + 36 + 1 + 64`), с запасом укладываясь в `VARCHAR(255)` при любом количестве пожеланий в пачке (хоть 20, хоть 100).
     - Сохранены фундаментальные свойства тождества:
       - Инвариантность к перестановкам (закон 8 / V137): каноническая сортировка гарантирует, что любое перемешивание одной и той же пачки пожеланий даёт строго один и тот же ключ (предотвращение дублирования задач компилятора).
       - Изоляция проектов: префикс `projectId` исключает совпадение ключей между проектами.
       - Различимость: разные наборы пожеланий дают криптографически различные хеши.
     - Сохранена структурная целостность заведения задач компилятора: количество точек вызова `setContentKey(` в `ProjectFlowService.java` строго равно 2 (one-shot компилятор и persistent worker).
     - Следствие для прежних строк: исторические задачи компилятора несли длинный строковый ключ; при перезапуске первая компиляция создаст задачу с новым стабильным хеш-ключом вместо падения с исключением базы данных.
- **Чем проверено:**
  1. `CompilerTaskIdentityTest`:
     - `identityIsAFunctionOfTheWorkAndNotOfTheMoment`: совпадение ключей при разном порядке wishlists, различие при разных проектах и подмножествах.
     - `contentKeyLengthIsStrictlyBoundedRegardlessOfBatchSize`: заслон против `Value too long for column CONTENT_KEY` — пачка из 20 пожеланий и пачка из 50 пожеланий дают длину ровно 109 символов $\le 255$; перестановка 20 пожеланий даёт идентичный ключ.
     - `everySiteThatMintsACompilerTaskGivesItAnIdentity`: структурный заслон сохранения ровно 2 фабрик компиляторов и 2 вызовов `setContentKey(`.
  2. Прогон Maven в Docker (`BUILD SUCCESS`): `CompilerTaskIdentityTest` 3/3 green.
- **Что берётся следующим:** Такт 8 — завершён в текущем такте (см. ниже).

### 2026-09-11 Antigravity: Такт 8 — Защита изменяющих входов ИИ и служебных путей GoogleAiResourceController (пункт 7 очереди, разделы XXXVIII, XXXIV)
- **Что сделано:** Механизм авторизации и защиты внешних/внутренних интерфейсов доведён до идеала целиком:
  1. **Матрица прав и обязанностей (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006 Authorization ambiguity):**
     - Составлена и реализована в коде матрица прав и обязанностей акторов для всех 9 эндпоинтов `GoogleAiResourceController` и всех путей `/internal/**`.
     - Безопасные чтения (`GET /api/ai/resources`, `GET /api/ai/resources/design-consistency-audit`, `GET /api/ai/resources/stitch-tools-debug`, `GET /api/ai/resources/video-assets/**`) сохранены доступными для дашборда и телеметрии.
     - Все 5 изменяющих и расходных операций (`POST /api/ai/resources/probe-models`, `/design-drafts-cleanup`, `/design-assets`, `/stitch-design-system`, `/video-assets`) закрыты строгим авторизационным заслоном.
  2. **Запрет как исполняемый код (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006):**
     - Создан `ApiAuthorizationInterceptor` (`com.eneik.production.security`), зарегистрированный в `WebConfig.addInterceptors` на `/api/ai/resources/**` и `/internal/**`.
     - Запрос к изменяющим AI-эндпоинтам без заголовка `X-API-Key` или `Authorization: Bearer` немедленно получает отказ `401 Unauthorized` с машиночитаемым JSON-объяснением (`{"error": "...", "code": "UNAUTHORIZED", "status": 401}`).
     - Запрос с неверным ключом получает отказ `403 Forbidden` (`{"error": "...", "code": "FORBIDDEN", "status": 403}`).
     - Запрос к внутренним путям (`/internal/**`) со стороннего хоста (не loopback `127.0.0.1` / `::1` и не docker bridge gateway `172.x.x.1`) без валидного токена оператора получает отказ `403 Forbidden` (`{"error": "...", "code": "FORBIDDEN", "status": 403}`).
     - Взаимодействие скриптов хоста (`scripts/modules/db_utils.py`) с `/internal/**` поддержано: docker-proxy шлюз (`.1` в приватных сетях `172.16-31.x.1`, `10.x.x.1`, `192.168.x.1`) признаётся локальным хостом, а также добавлена передача заголовка `X-API-Key` из переменной среды `ENEIK_SECURITY_API_KEY`.
     - Сравнение секретов реализовано в константном времени через `MessageDigest.isEqual` для предотвращения атак по времени (timing attacks).
     - Принцип «Не знаю ≢ всё хорошо»: если переменная `ENEIK_SECURITY_API_KEY` не сконфигурирована в окружении (по умолчанию пустая), изменяющие операции закрыты с `403 Forbidden` («server API key is not configured»). Никаких зашитых в открытый код ключей по умолчанию не существует.
  3. **Ликвидация уязвимости Path Traversal (H3 Security Audit):**
     - В `GoogleAiResourceController.listVideoAssets` добавлена строгая проверка нормализованного пути через `dir.startsWith(root)`, исключающая выход за пределы `./data/video-assets` при передаче `..` в `projectSlug`.
- **Чем проверено:**
  1. `ApiAuthorizationInterceptorTest`: 9/9 green — проверка всех отношений матрицы прав и обязанностей:
     - 5 изменяющих AI-эндпоинтов без заголовков -> 401 Unauthorized.
     - Изменяющий вызов с неверным `X-API-Key` и неверным `Bearer` -> 403 Forbidden.
     - Изменяющий вызов с валидным `X-API-Key` и валидным `Bearer` -> 200 / допуск.
     - Безопасные чтения GET -> допуск без токена.
     - Внешний неавторизованный запрос к `/internal/tasks` -> 403 Forbidden.
     - Запрос с localhost к `/internal/tasks` -> допуск.
     - Запрос с docker bridge gateway (`172.18.0.1`, `172.17.0.1`, `10.0.0.1`, `192.168.1.1`) к `/internal/tasks` -> допуск.
     - Внешний запрос к `/internal/tasks` с валидным токеном -> допуск.
     - Запрос при несконфигурированном ключе API -> 403 Forbidden («server API key is not configured»).
  2. `GoogleAiResourceControllerTest`: 1/1 green — проверка предотвращения path traversal при `..` и вложенных путях.
  3. Регрессия: 53 теста green (`CompilerTaskIdentityTest`, `ProjectEventLogRetentionServiceTest`, `SixSigmaAuditServiceTest`, `QualityGateControllerTest`, `TaskCarrierBackfillServiceTest`, `ApiAuthorizationInterceptorTest`, `GoogleAiResourceControllerTest`).
- **Что берётся следующим:** Такт 9 — устранение сбоев 500 на `/dispatch-capacity-probe` и `/persistent-workers` в `InternalGeminiObserverController` и централизованный маппинг параметров в `GlobalExceptionHandler`.

### 2026-09-11 Antigravity: Такт 9 — Устранение ложных отказов HTTP 500 в InternalGeminiObserverController (пункт 8 очереди, раздел XXIII)
- **Что сделано:** Устранена категориальная ошибка превращения клиентских запросов и отсутствующих параметров в ошибку сервера `HTTP 500 Internal Server Error` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006):
  1. **Централизация маппинга клиентских ошибок (`GlobalExceptionHandler`):**
     - Добавлены обработчики `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` и `HttpMessageNotReadableException`, возвращающие корректный статус `400 Bad Request` с понятным описанием пропущенного или невалидного поля вместо падения в универсальный `500 Unexpected Error` со стектрейсом в логах.
  2. **Устойчивость и детерминированный fallback (`InternalGeminiObserverController`):**
     - В эндпоинте `/persistent-workers` параметр `projectId` сделан опциональным (`@RequestParam(required = false)`): при отсутствии явного ID проект разрешается автоматически по единственному активному проекту в БД (`projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`). Если активного проекта нет — возвращаются все сессии воркеров через `persistentWorkerSessionRepository.findAll()`. Исход: HTTP 200 с валидным списком, ноль падений 500.
     - В эндпоинте `/dispatch-capacity-probe` параметр `projectId` сделан опциональным, а `tag` получил каноническое дефолтное значение `"BARCAN-TAG-11"`. При вызове без параметров активный проект разрешается автоматически. Если активного проекта нет, возвращается 200 JSON со статусом `UNDETERMINED_PROJECT`, `found: false` и понятным объяснением, предотвращая падение сервера.
- **Чем проверено:**
  1. `InternalGeminiObserverControllerTest`: 6/6 green:
     - `persistentWorkers` с явным `projectId` возвращает сессии проекта.
     - `persistentWorkers` без параметров разрешает активный проект и возвращает его сессии.
     - `persistentWorkers` без параметров при отсутствии активного проекта возвращает все воркеры через `findAll()`.
     - `dispatchCapacityProbe` с явными параметрами проверяет capacity аккаунтов.
     - `dispatchCapacityProbe` без параметров разрешает активный проект и дефолтный тег `"BARCAN-TAG-11"`.
     - `dispatchCapacityProbe` без параметров при отсутствии активного проекта отдаёт `UNDETERMINED_PROJECT` (HTTP 200) без 500.
  2. `GlobalExceptionHandlerTest`: 3/3 green:
     - `MissingServletRequestParameterException` -> 400 Bad Request.
     - `MethodArgumentTypeMismatchException` -> 400 Bad Request.
     - `HttpMessageNotReadableException` -> 400 Bad Request.
  3. Полная регрессия фабрики: 62 теста green (`CompilerTaskIdentityTest`, `ProjectEventLogRetentionServiceTest`, `SixSigmaAuditServiceTest`, `QualityGateControllerTest`, `TaskCarrierBackfillServiceTest`, `ApiAuthorizationInterceptorTest`, `GoogleAiResourceControllerTest`, `InternalGeminiObserverControllerTest`, `GlobalExceptionHandlerTest`).
### 2026-09-11 Antigravity: Такт 10 — Четырёхзначная логика ценности LeanValue и устранение подмены неизвестного (пункт 9 очереди, раздел 42; доработка пунктов 7 и 8)
- **Что сделано:**
  1. **Явный статус неизвестного в LeanValue (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012 Policy contradiction):**
     - Добавлено значение `undetermined` в перечисление `LeanValue` (`essential, valuable, waste, undetermined`). Длина 12 символов укладывается в колонку `VARCHAR(16)` (миграция V12).
     - Метод `JulesDispatchService.parseLeanValue` обновлен: `null`, пустая строка, пробелы и нераспознанные значения парсятся как `LeanValue.undetermined`, а не как `LeanValue.valuable`.
     - Метод `JulesDispatchService.parseCompilerPlan`: разбор поля `leanValue` переведён на `parseLeanValue`, исключив дефолтный `essential` при отсутствии или ошибке.
     - `BaseQualityGate.BusinessValueGate`: заслон пропускает только `essential` и `valuable`, отклоняя `waste`, `undetermined` и нераспознанные строки с понятными причинами отказа.
     - `TechnicalLeadCompiler.validateDefinitionOfReady`: Шаг 2 (`Step 2`) проверки Definition of Ready требует явного значения ценности и отклоняет `null` и `undetermined` («Step 2 failed: lean_value is missing or undetermined»), предотвращая запуск непроверенной работы в поток разработки.
     - `TechnicalLeadCompiler.inferEpicKanoClass`: при `LeanValue.undetermined` возвращает класс `"Undetermined"`.
  2. **Отображение, хранение и детерминированное разрешение (`displayed, stored, and resolved`):**
     - В `ProjectFlowService.emsGraphSlices` ликвидирован подлог, при котором `undetermined` срезы выбрасывались из графа этапов вместе с `waste`. Теперь срезы с `undetermined` сохраняются в графе!
     - Введены методы детерминированного разрешения неизвестной ценности:
       - `ProjectFlowService.resolveSliceLeanValue`: выводит ценность из контекста эпика Kano (`Must-Be` -> `essential`, `Performance`/`Attractive` -> `valuable`, `Reverse/Waste` -> `waste`) или роли ядра архитектуры (`BARCAN-TAG-00/02/12` -> `essential`).
       - `ProjectFlowService.resolveWishlistLeanValue`: аналогично разрешает ценность для пожелания по роли и ключевым словам JTBD.
       - Если контекст полностью отсутствует, срез сохраняется в базе (`wishlistRepository.save`) со статусом `pending` и логированием предупреждения для триажа оператором, не обрывая создание графа остальных срезов.
  3. **Снятие адресно-шаблонного исключения в `ApiAuthorizationInterceptor` (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
     - Ликвидирован категориальный сбой: форма адреса (`*.1` в приватных сетях `10.*`, `192.168.*`, `172.16-31.*`) больше не приравнивается к полномочиям. Внутренние эндпоинты (`/internal/**`) разрешены только истинному loopback (`127.0.0.1`, `::1`, `localhost`) либо требуют валидный `X-API-Key`.
  4. **Устранение `findAll()` в `InternalGeminiObserverController.persistentWorkers` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - При отсутствии единственного активного проекта эндпоинт `/persistent-workers` возвращает честный `UNDETERMINED_PROJECT` (HTTP 200), аналогично соседнему `dispatch-capacity-probe`, полностью исключив подъём всех сессий воркеров.
- **Чем проверено:**
  1. `LeanValueTest` (6/6 green):
     - `parseLeanValue`: истинностная таблица для null, empty, garbage, essential, valuable, waste.
     - `BusinessValueGate`: допуск essential/valuable, отказ на waste/undetermined/unrecognized.
     - `validateDefinitionOfReady`: Step 2 отказ на undetermined/null/waste, пропуск essential/valuable.
     - `emsGraphSlices`: сохранение undetermined срезов в графе и удаление waste.
     - `resolveSliceLeanValue` и `resolveWishlistLeanValue`: корректное разрешение по Kano и ролям.
  2. `ApiAuthorizationInterceptorTest` (9/9 green):
     - Обращение к `/internal/tasks` с `172.18.0.1`, `10.0.0.1` без ключа -> 403 Forbidden.
     - Обращение с теми же адресами и валидным `X-API-Key` -> 200 OK.
  3. `InternalGeminiObserverControllerTest` (6/6 green):
     - `persistentWorkers` без активного проекта возвращает `UNDETERMINED_PROJECT`, `findAll()` никогда не вызывается.
  4. Полная регрессия фабрики: 68 тестов green (`LeanValueTest`, `ApiAuthorizationInterceptorTest`, `InternalGeminiObserverControllerTest`, `GoogleAiResourceControllerTest`, `GlobalExceptionHandlerTest`, `CompilerTaskIdentityTest`, `ProjectEventLogRetentionServiceTest`, `SixSigmaAuditServiceTest`, `QualityGateControllerTest`, `TaskCarrierBackfillServiceTest`).
- **Что берётся следующим:** Такт 11 — Пункт 10 очереди (`RepositoryStackAnalyzer`, раздел XXXI: трёхзначный тип обхода репозитория заказчика вместо `boolean`, предотвращение ложных находок при недоступности GitHub).

### 2026-09-11 Antigravity: Такт 11 — RepositoryStackAnalyzer и OnboardingAuditService целиком (пункт 10 очереди, Раздел XXXI)
- **Что сделано:** Механизм анализа репозитория заказчика доведён до идеала целиком:
  1. **Трёхзначный статус обхода по Белнапу и сохранение границы рода по Райлу (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
     - Введён тип `InspectionStatus` (`YES`, `NO`, `UNCHECKED`) с методами `isYes()`, `isNo()`, `isUnchecked()`, `displayValue()`.
     - `StackProfile`: поля `hasCI`, `hasTests`, `isMonorepo` переведены с примитивного `boolean` на `InspectionStatus`. Добавлен метод фабрики `StackProfile.unchecked(declaredPurpose, defaultBranch, baselineCommitSha)` и метод `isUnchecked()`.
     - `RepositoryStackAnalyzer`: три точки формирования профиля при отсутствии токена (строка 52), сбое обхода дерева git (строка 100) и исключении (строка 290) возвращают `StackProfile.unchecked(...)` со статусами `UNCHECKED`, а текстовые поля `framework` и `database` устанавливаются в `"не проверено"`, устранив подмену неудачи фабрики фактом об отсутствии («нет»).
  2. **Устранение ложных находок о заказчике в `OnboardingAuditService`:**
     - Находка критического дефекта (строка 103) выставляется строго при `stackProfile.hasTests().isNo()` (и наличии слова "production" в заявленной цели). При статусе `UNCHECKED` дефект не выставляется.
     - Находка отсутствия CI (строка 130) выставляется строго при `stackProfile.hasCI().isNo()`. При статусе `UNCHECKED` дефект не выставляется.
     - Находка по документации (строка 136) не выставляется при `stackProfile.isUnchecked()`.
     - При отсутствии доступа к GitHub или токена аудит формирует ровно **0** находок о заказчике (`findings` пуст).
     - Генератор отчёта `docs/reports/onboarding-audit-{slug}.md` выводит `"не проверено"` для непроверенных компонентов и 0 находок.
- **Чем проверено:**
  1. `RepositoryStackAnalyzerTest` (6/6 green):
     - `missingGithubTokenReturnsUncheckedProfileWithTriStateStatus`: проверка `isUnchecked() == true`, `framework == "не проверено"`, `database == "не проверено"`, `hasCI/hasTests/isMonorepo == UNCHECKED`, `filesToScan.isEmpty()`.
     - `blankGithubTokenReturnsUncheckedProfile`.
  2. `OnboardingAuditServiceTest` (5/5 green):
     - `auditWithoutGithubTokenProducesZeroFindingsAndUncheckedMarkdownReport`: верификация 0 находок о заказчике (`saveAll` получает пустой список) и генерации отчёта со всеми признаками "не проверено" и 0 findings.
     - `auditWithInspectedRepositoryAndNoTestsFilesCriticalFindingWhenProductionClaimed`: подтверждение нахождения дефекта `BARCAN-TAG-06` при доказанном отсутствии тестов.
     - `auditWithInspectedRepositoryAndNoCiFilesMajorFinding`: подтверждение нахождения дефекта `BARCAN-TAG-05` при доказанном отсутствии CI.
     - `auditWithInspectedRepositoryAndVerifiedCiAndTestsDoesNotFileCiOrTestFindings`: отсутствие ложных срабатываний при наличии тестов и CI.
     - `categoryBoundaryPreservedBetweenAccessFailureAndRepositoryReality`: строгий фальсифицирующий тест границы рода — при сбое доступа 0 находок, при инспекции реального отсутствия тестов и CI — 2 находки.
  3. Полный регрессионный прогон: 32 теста green в Maven Docker (`RepositoryStackAnalyzerTest`, `OnboardingAuditServiceTest`, `LeanValueTest`, `ApiAuthorizationInterceptorTest`, `InternalGeminiObserverControllerTest`).
- **Что берётся следующим:** Такт 12 — Пункт 11 очереди (`OperationalTruthService`, раздел XXVII: доверие начинается с не установленного и растёт по свидетельствам, а не с 1.0 с одними вычитаниями).

### 2026-09-11 Antigravity: Устранение подстрочной эвристики LeanValue и протокол разрешения неопределенности
- **Что сделано:**
  1. **Ликвидация категориальной ошибки классификации подстрокой (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
     - В `ProjectFlowService.resolveWishlistLeanValue` полностью удалены подстрочные проверки `jtbd` (`"fix"`, `"ui"`, `"feature"`, `"security"`, `"screen"`, `"dashboard"`), ложно срабатывавшие на слова `build`, `guide`, `quick`, `suite`, `require`, `prefix`, `suffix`.
     - Удалена эвристика привязки ценности к роли создателя (`BARCAN-TAG-00/02/12 -> essential`): роль исполнителя не тождественна ценности работы для заказчика.
     - Ценность выводится строго и детерминированно из явного поля `epicKanoClass` родительского эпика/фичи (`Must-Be -> essential`, `Performance/Attractive -> valuable`, `Reverse/Waste -> waste`).
  2. **Реализация обязательства `resolved` по Белнапу (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - Внедрён метод `processCompiledWishlistWithUndeterminedValue(WishlistEntity wishlist)` с ограниченным бюджетом попыток триажа (`wishlist.effectiveCompileCeiling() = 3`).
     - Если ценность не может быть выведена из эпика Kano, элемент не зависает в `pending` бесконечно: на попытках 1 и 2 счетчик `compileAttempts` инкрементируется и элемент возвращается в очередь триажа.
     - По исчерпании лимита (попытка 3) элемент детерминированно переводится в статус `WishlistStatus.dismissed` с явной фиксацией причины в журнале.
- **Чем проверено:**
  1. `LeanValueTest` (7/7 green):
     - `resolveWishlistLeanValueResolutionPath`: тест подтверждает, что пожелания со словами `build`, `guide`, `quick`, `suite`, `prefix`, `suffix` и ролями ядра возвращают `undetermined`, а не `valuable` или `essential`.
     - `resolveSliceLeanValueResolutionPath`: подтверждение вывода только из Kano.
     - `processCompiledWishlistWithUndeterminedValueResolution`: верификация 3-этапного триажа с переводом в `dismissed` на 3-й попытке.
  2. Полный регресс `ProjectFlowServiceTest`: 37/37 green (`BUILD SUCCESS`).

### 2026-09-11 Antigravity: Такт 12 — OperationalTruthService целиком (пункт 11 очереди, Раздел XXVII)
- **Что сделано:**
  1. **Ликвидация априорного доверия по неведению (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006):**
     - Доверие больше не начинается автоматически с 1.0 (`score = 1.0` и только вычитания).
     - При отсутствии положительных свидетельств (0 слитых PR и 0 пройденных заслонов качества) уровень доверия устанавливается строго как `undetermined`, а базовый счет равен `0.0`. Никакое отсутствие свидетельств против не производит положительной оценки.
     - Список положительных сигналов честно фиксирует: `"No delivery or quality-gate verification evidence accumulated yet."`.
  2. **Асимметричная динамика доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
     - Внедрен метод `computeBaseTrust(positiveEvidenceCount)`: базовое доверие накапливается медленно и пакетно (`TRUST_PACKET_SIZE = 5`, `TRUST_EVIDENCE_THRESHOLD = 20` по аналогии с `MIN_RESOLVED_SAMPLES = 20` в `LeverPromotionService`):
       - 0 свидетельств -> `0.0` (уровень `undetermined`)
       - 1–4 свидетельства (пакет 0) -> `0.50` (базовый уровень полосы `degraded`)
       - 5–9 свидетельств (пакет 1) -> `0.65` (базовый уровень полосы `watch`)
       - 10–14 свидетельств (пакет 2) -> `0.75` (верхний уровень полосы `watch`)
       - 15–19 свидетельств (пакет 3) -> `0.85` (базовый уровень полосы `trusted`)
       - >= 20 свидетельств (полный порог) -> `1.00` (максимальное базовое доверие полосы `trusted`)
     - Сохранены все 6 штрафных вычитаний для дефектов и сбоев (по V90): при подтвержденных отказах (непройденные заслоны, сбойные PR, дубликаты, сбой рантайма, дефекты) доверие падает **немедленно**, в тот же такт пересчёта, снижая уровень до `watch`, `degraded` или `blocked`.
     - Для зрелого живого проекта с >20 свидетельствами база составляет 1.0, а при наличии 2 зафиксированных дефектов счет остается равным ровно **0.70 ("watch")** — масштаб и историческая сопоставимость снимков `TrustSignalSnapshotEntity` полностью сохранены!
     - В перечень инвариантов добавлен `trust_requires_positive_evidence` (`trusted(project) -> positive_evidence(project)`).
     - В `sourceOfTruth()` зарегистрирован владелец истины: `Operational trust dynamics -> OperationalTruthService`.
- **Чем проверено:**
  1. `OperationalTruthServiceTest` (15/15 green):
     - `projectWithoutEvidenceHasZeroScoreAndUndeterminedTrustLevel`: верификация проекта без свидетельств (score 0.0, level "undetermined", инвариант "observed").
     - `computeBaseTrustGrowsSlowlyInPackets`: верификация ступенчатого пакетного роста базового счета (0, 0.50, 0.65, 0.75, 0.85, 1.00).
     - `positiveEvidenceAccumulationPromotesTrustLevel`: верификация накопления свидетельств с переходом в `watch` (0.65) и инвариантом "pass".
     - `asymmetricDemotionDropsTrustImmediatelyOnConfirmedFailure`: верификация немедленного падения с 1.00 до 0.80 ("watch"), 0.50 ("degraded") и 0.0 ("blocked").
     - `trustLevelOverloadWithEvidenceFlag`: верификация всех диапазонов и флага наличия свидетельств.
  2. `TrustSnapshotServiceTest`: 8/8 green.
  3. Прогон Maven в Docker: 23/23 теста green (`BUILD SUCCESS`).
- **Что берётся следующим:** Такт 13 — завершён в текущем такте (см. ниже).

### 2026-09-11 Antigravity: Такт 13 — QualityMetricsController целиком (пункт 12 очереди, Раздел XXXIX; TaskEntity; OperationalTruthService; LeanValue)
- **Что сделано:**
  1. **Ликвидация дефекта «одно слово, два счёта: 388 против нуля» (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):**
     - В `TaskEntity` открыт метод `deliveryChecksApplied()`, добавлен предикат `isDeliveryVerificationFailed()` (`!isDeliveryVerificationAbsent() && !isVerifiedForDelivery()`) и метод `qualityGateChecksFailed()`.
     - Сформирован полный непересекающийся раздел истинностных статусов: `verified + failed + unapplied == tasks with report`. Ни один результат больше не подменяется другим.
  2. **Очистка `QualityMetricsController` от муды full-table reads и N+1 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010):**
     - В `getDefectSummary`: `taskRepository.findAll()` (подъём 753 задач в память) заменён на `taskRepository.findByQualityGateReportIsNotNull()`. Дефекты заслона считаются строго по проверкам с `passed == false`. При нуле проваленных проверок возвращается строго `qualityGate.total = 0`.
     - В `getConflictDpmo`: all-time и 7-day агрегаты переведены на быстрые репозиторные счетчики (`countByMergedTrue()`, `count()`, `countByMergedTrueAndCreatedAtAfter()`, `countByDetectedAtAfter()`) без вычитки всех записей PR и конфликтов.
     - Ликвидирован N+1 запрос `taskRepository.findById` на каждую сессию при группировке по проектам: переход на пакетную выборку `findAllById`.
  3. **Знаниевое свидетельство и окно свежести в `OperationalTruthService` (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
     - `qualityGatePassed` переведён на `TaskEntity::isVerifiedForDelivery` (требует примененных проверок > 0 либо удовлетворенного вердикта критериев приемки).
     - `qualityGateFailed` переведён на `TaskEntity::isDeliveryVerificationFailed`: 388 задач с нулем примененных проверок классифицируются как `qualityGateUnapplied`, больше не штрафуют счет доверия и не генерируют ложное предупреждение `"388 tasks have failed quality-gate evidence"`.
     - В `OperationalTruthDto.EvidenceSummary` добавлен счётчик `qualityGateUnapplied` (с сохранением совместимости 8-параметрового конструктора).
     - Введено скользящее окно свежести `TRUST_RECENCY_WINDOW = Duration.ofDays(30)`: давние свидетельства не удерживают доверие 1.0 вечно.
  4. **Очистка `LeanValue` от псевдомеханизма отсчёта (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - В `ProjectFlowService.processCompiledWishlistWithUndeterminedValue` устранён искусственный 3-тактный отсчёт на чужом счётчике `compileAttempts`. При отсутствии класса Kano на эпике пожелание с `undetermined` ценностью сразу детерминированно переводится в `WishlistStatus.dismissed` с понятной причиной и сохранением `leanValue = undetermined` (отличимо от `waste`).
- **Чем проверено:**
  1. `QualityMetricsControllerTest` (3/3 green, включая верификацию `never().findAll()` на `taskRepository` и `prReviewRepository`).
  2. `OperationalTruthServiceTest` (16/16 green, включая тест `triStateTruthPartitionVerifiedFailedAbsentAndRecencyWindow`).
  3. `TrustSnapshotServiceTest` (8/8 green).
  4. `LeanValueTest` (7/7 green).
  5. Прогон Maven в Docker: 34/34 теста green (`BUILD SUCCESS`, 45.8 с).
- **Что берётся следующим:** Такт 14 — завершён в текущем такте (см. ниже).

### 2026-09-11 Antigravity: Такт 14 — GitHubProjectFactoryClient, ProjectFactoryService, ProjectFlowService (пункт 13 очереди, Раздел XXXII; поправки LeanValue и OperationalTruthService)
- **Что сделано:**
  1. **Ликвидация фантомных адресов репозиториев (`NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` / D007, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):**
     - Устранены все три точки фабрикации адреса до создания удаленной сущности:
       1. В `GitHubProjectFactoryClient.provision` полностью удалена переменная `fallbackUrl`. Во всех ветках пропуска или ошибок (`skipped: GitHub provisioning disabled`, `skipped: GITHUB_TOKEN is not configured`, HTTP-ошибки, InterruptedException, Exception) клиент возвращает `null` вместо предварительно сконструированной строки.
       2. При создании репозитория (HTTP 201) адрес `repoUrl` извлекается из реального объекта доказательства `html_url` ответа GitHub (или `null` при отсутствии).
       3. Для brownfield-онбординга (HTTP 422 `exists or blocked`) выполняется доказательная верификация реального существования репозитория на GitHub через `GET /repos/{org}/{repo}`. При HTTP 200 извлекается проверенный `html_url`; при отсутствии верификации — строго `null`.
       4. В `ProjectFlowService.admitProject` ликвидирована презумпция существования: удалены строки предзаписи `project.setRepositoryUrl` и `project.setRepoUrl` — при заведении проекта адрес репозитория остаётся `null`.
       5. В `ProjectFactoryService` устранена подмена рода через `firstNonBlank(github.repositoryUrl(), project.getRepositoryUrl())`: сервис использует проверенный адрес `github.repositoryUrl()`. При неудаче или пропуске заведения репозитория `repositoryUrl` и `repoUrl` у проекта остаются строго `null`.
       6. В `LinearProjectFactoryClient` описание задачи защищено от конкатенации `"Repository: null"`.
  2. **Поправка по замечанию Клода: удержание `LeanValue.undetermined` в нефинальном состоянии (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - В `ProjectFlowService.processCompiledWishlistWithUndeterminedValue` снят необратимый перевод в `WishlistStatus.dismissed` при отсутствии класса Kano на эпике. Отклонять работу потому, что её ценность неизвестна, — значит совершать необратимое действие ради снятия неопределённости. Пожелание сохраняется в нефинальном состоянии `WishlistStatus.pending` с `leanValue = LeanValue.undetermined` в ожидании переоценки ценности.
  3. **Выравнивание окна свежести в `OperationalTruthService` (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
     - В методе `evidence()` счётчик `qualityGateUnapplied` выровнен с остальными индикаторами заслона по скользящему окну свежести `TRUST_RECENCY_WINDOW` (`30` дней).
- **Чем проверено:**
  1. `GitHubProjectFactoryClientTest` (3/3 green):
     - Отключенный GitHub -> статус `skipped:`, `repositoryUrl = null`, `repositoryId = null`.
     - Отсутствующий `GITHUB_TOKEN` -> статус `skipped:`, `repositoryUrl = null`.
     - Пробельный `GITHUB_TOKEN` -> статус `skipped:`, `repositoryUrl = null`.
  2. `ProjectFactoryServiceTest` (4/4 green):
     - `skippedGitHubProvisioningYieldsNullRepositoryUrl`: пропуск заведения даёт строго `repositoryUrl = null`, не откатываясь на предзаписанный в проекте URL.
     - `failedGitHubProvisioningYieldsNullRepositoryUrl`: отказ заведения даёт строго `repositoryUrl = null`.
  3. `ProjectAdmissionLaw25aTest` (5/5 green):
     - `admitProject` создаёт проект со строго `repositoryUrl = null` и `repoUrl = null`.
     - Сбой внешнего заведения (`provision_failed`) оставляет у проекта `repositoryUrl = null` и `repoUrl = null`.
  4. `LeanValueTest` (7/7 green): подтверждение удержания неразрешённого пожелания в `WishlistStatus.pending` с `LeanValue.undetermined`.
  5. `OperationalTruthServiceTest` (16/16 green): подтверждение учёта окна свежести для всех категорий заслона качества.
  6. Полный прогон в Docker-контейнере Maven: 35/35 тестов green (`BUILD SUCCESS`, 01:13 мин).
- **Что берётся следующим:** Такт 15 — Пункт 14 очереди (`TargetContext` · пункт 43: отсутствие значения «не установлено», устранение уничтожения и подмены неизвестного контекста цели задачи).

### 2026-09-11 Antigravity: Такт 17 — EvidenceCoherenceService (пункт 16 очереди, V80, Раздел XXIIe)
- **Что сделано (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011 Perception failure):**
  1. **Ликвидация N+1 и O(N) entity loads в JVM:**
     - В `KaizenProposalRepository` добавлен `long countByStatus(String status);`. В `sourceReliability` вызовы `kaizenProposalRepository.findAll()` заменены на прямые `countByStatus("STANDARDIZED")` и `countByStatus("REVERTED")`.
     - В `CoherenceRunNodeResultRepository` добавлены агрегаты `countDistinctEvaluatedNodesBySourceType(sourceType)` и `countDistinctAcceptedNodesBySourceType(sourceType)`. Устранены `evidenceNodeRepository.findAll()` и поштучные N+1 вызовы `findByEvidenceNodeId(nodeId)` на каждый узел.
     - В `computeConfidences` внедрено кэширование надёжности типов источников (`Map<String, Double> reliabilityCache`) на цикл согласования, исключающее повторные обращения к БД для одинаковых типов источников.
     - В `distinctHistoricallyCorroboratingSourceTypes` загрузка всех строк через `findByEvidenceNodeId` заменена на точечный `existsByEvidenceNodeIdAndAcceptedTrue(nodeId)`.
  2. **Предел хранения (Retention Ceiling):**
     - Добавлена настройка `coherence.max-runs-per-project` (по умолчанию `30`).
     - В `runCoherenceCycle` внедрён метод `pruneOldRuns(projectId)`: при превышении лимита старые прогоны удаляются через `coherenceRunRepository.deleteAll(excess)`. Благодаря внешнему ключу `ON DELETE CASCADE` в таблице `coherence_run_node_results`, дочерние строки результатов удаляются базой данных автоматически. Исторические данные сохранены (без деструктивных SQL-миграций).
     - В `CoherenceRunRepository` добавлен `findByProjectIdIsNullOrderByRanAtDesc()` для обрезки глобальных прогонов (`projectId == null`).
  3. **Остановка холостого расписания (Idling Halt):**
     - Добавлен флаг `coherence.scheduled-cycle-enabled: false` (в `application.properties` и коде). При отсутствии внешнего потребителя периодический цикл `@Scheduled` пропускается без обращений к проектам и базе данных, предотвращая лавинообразное накопление мёртвых строк.
- **Чем проверено:**
  - `EvidenceCoherenceServiceTest` (21/21 green):
    - `kaizenReliabilityUsesRealOutcomeGroundTruthWhenEnoughSamplesExist`: проверяет расчёт надёжности Kaizen через `countByStatus` и заслоняет `never().findAll()`.
    - `nonKaizenSourceFallsBackToCoherenceEngineAcceptanceHistoryWhenNoOutcomeDataExists`: проверяет расчёт через `countDistinctEvaluatedNodesBySourceType` и заслоняет `never().findAll()`.
    - `twoAgreeingCorroboratingSourcesProduceHigherConfidenceThanEitherAlone`: проверяет корректность объединения логитов без выгрузки всех сущностей (`never().findAll()`).
    - `periodicCycleSkippedWhenScheduledCycleDisabled`: подтверждает, что при выключенном флаге расписания опрос проектов и узлов не выполняется.
    - `periodicCycleRunsWhenScheduledCycleEnabled`: подтверждает запуск цикла при включении флага.
    - `retentionPrunesOldRunsExceedingLimitForProject`: подтверждает обрезку старых прогонов проекта при превышении лимита.
    - `retentionPrunesGlobalRunsWhenProjectIdIsNull`: подтверждает обрезку старых глобальных прогонов при превышении лимита.
- **Что берётся следующим:** Такт 18 — Пункт 17 очереди (`VerificationEvidenceGate` · пункт 45: подмена отсутствия проверок их успешностью).



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

комментарий для Антигравити: механизм не идеален. До ответа не правь `EpistemicLayerInvariantGate`; first declare absent-evidence semantics and whether file-scope or real PR diff owns the CORE/PERIPHERY boundary. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-11 Antigravity: вопрос по читающему действию для EvidenceCoherence (пункт 16 очереди, V80)

вопрос: Какое конкретное производственное действие фабрики должно читать сигнал связности `coherence_score` / вердикты узлов графа свидетельств?
Три архитектурных варианта:
  1. **Качественный заслон признака в FeatureService:** В `FeatureService.evaluateFeatureHypothesis` превратить информационный лог в реальный гейт допуска: отклонять/блокировать эпик или переводить в карантин, если `coherenceScore < threshold` или имеются активные противоречия с ядром (`CORE`).
  2. **Сигнал операционной реальности в OperationalTruthService:** Транслировать падение связности графа (`coherenceScore < 0.0`) как операционный блокер или системную находку (`SYSTEMIC_DEFECT`), инициирующую задачу анализа расхождений.
  3. **Консервация механизма:** Оставить фоновое расписание выключенным (`coherence.scheduled-cycle-enabled=false`), сохранив функционал как callable-инструмент (on-demand via `/coherence-graph`) до ввода в строй нового целевого потребителя.

замер: На текущий момент `coherence_score` читается только UI-эндпоинтом `/coherence-graph` (`ForgeDeliveryRoom.svelte`) и деактивированным `InternalGeminiObserverController:453` (`/coherence-runs`). Узловые результаты читаются только внутри самого сервиса для укоренённости и надёжности источников следующего прогона. Первоначальный потребитель (agentic-loop Gemini, V80) был выключен в V111. Таблицы выросли до 14647 результатов без единого внешнего потребителя.

почему спрашиваю: Согласно корпусному паттерну `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011 Perception failure), сигнал имеет смысл только тогда, когда он меняет последующее действие и предотвращает ошибочное действие («Show how the signal changes the next action and prevents a mistaken action»). Без явного внешнего читателя сигнал замкнут на самого себя.

## 2026-09-11 Antigravity: Такт 18 — V100 (Client Acceptance Traversals vs FlowSpine Delivery Status)

### Что сделано:
1. **Демаркация «построено» vs «показано/принято» (`NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` / D007 Evidence gap, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009 Intent-behavior skew):**
   - Устранена категориальная ошибка в `FlowSpineService.valueStatus`: при переходе в состояние `DELIVERED` по критерию готовности кода (`completeFeatures >= totalFeatures`), фабрика больше не присваивает статус `client_value_delivered` авансом.
   - Если число подтверждённых обходов заказчиком равно 0 (`!input.hasClientAcceptanceTraversal()`), `valueStatus` возвращает строго `"scope_built_awaiting_acceptance"`.
   - Значение `"client_value_delivered"` возвращается строго в статусе `ACCEPTED` либо в статусе `DELIVERED` при наличии хотя бы одного подтверждённого обхода заказчиком (`clientAcceptanceTraversals > 0`).
2. **Интеграция с ClientAcceptanceTraversalRepository:**
   - В `ClientAcceptanceTraversalRepository` добавлен метод `countByProjectIdAndWalkedByIgnoreCase(UUID projectId, String walkedBy)`.
   - В `FlowSpineService` внедрён `ClientAcceptanceTraversalRepository` (с сохранением 10-аргументного конструктора для обратной совместимости).
   - В `StateInputs` добавлено поле `int clientAcceptanceTraversals`, хелпер `hasClientAcceptanceTraversal()` и 24-аргументный конструктор для обратной совместимости с существующими тестами.
   - Читатель `AcceptanceVerdictLayer` сохранён без изменений: он строго проверяет бриф, профиль корпуса и факт прохода именно заказчиком (`walked_by="client"`).
3. **Заслоняющие тесты:**
   - В `FlowSpineServiceTest.deliveredRequiresAllFeaturesComplete` подтверждено возвращение `scope_built_awaiting_acceptance`.
   - Добавлен тест `deliveredStateWithZeroClientAcceptanceTraversalsDoesNotClaimClientValueDelivered`, проверяющий оба исхода `DELIVERED` (без обходов -> `scope_built_awaiting_acceptance`, с обходом заказчика -> `client_value_delivered`) и статус `ACCEPTED`.
   - Все 31 тест в `FlowSpineServiceTest`, `FailingReviewCompositionTest`, `ReviewArtifactInvariantTest` green; все 13 тестов в `AcceptanceVerdictLayerTest` и `OperationalFlowCoreServiceTest` green.

### Вопрос по каналу поступления свидетельств приёмки (V100, раздел XXIIк):
**Вопрос:** Каким образом внешнее свидетельство прохода заказчика должно попадать в таблицу `client_acceptance_traversals` (где до сих пор 0 записей при 2309 суждениях о доставке в сутки)?
Варианты:
  1. **Внешний Webhook / REST эндпоинт приёмки:** Завести публичный или аутентифицированный эндпоинт `POST /api/projects/{id}/acceptance-traversals` (с полями `profileId`, `actor`, `link`, `evidence`, `instanceUrl`), вызываемый клиентским демо-стендом, порталом заказчика или внешним UI.
  2. **Интерактивный агентский сеанс с меткой клиента:** В `ProductLaunchabilityService` / сессиях прогона пользовательских путей на развёрнутом инстансе регистрировать обход только при явном подтверждении со стороны внешнего интерфейса заказчика.
  3. **Операторский шлюз подписания:** Ручная регистрация обхода оператором фабрики при получении подписанного акта приёмки от заказчика.

## 2026-09-11 Antigravity: Такт 19 — GeminiContextCacheManager (пункт 18 очереди, раздел XLII)

### Что сделано:
1. **Ликвидация дублирующего холостого кэша модели (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011 Perception failure):**
   - Класс `GeminiContextCacheManager.java` (162 строки) полностью удалён из `com.eneik.production.services.googleai`.
   - В `SystemStatusController` устранены зависимости от `cacheManager` и вызовы создания кэша.
   - Вызов `geminiContextService.reindexStandingKnowledge()` в методе `reindexGeminiContext` сохранён без изменений для штатного обновления RAG-корпуса.
   - Из ответа эндпоинта удалено неиспользуемое поле `cacheResourceName`.
   - Питоновский кэш контекста в ML-сайдкаре (`src/models/ml/PredictionService.py:116, 154`) сохранён на живом пути запросов.
2. **Заслоняющие тесты:**
   - Создан `SystemStatusControllerTest` (3/3 green): вызов `reindexStandingKnowledge()`, отсутствие `cacheResourceName`, подтверждение отсутствия `GeminiContextCacheManager` в classpath, структурный сканер `src/main/java` на строго 0 вхождений `cachedContents`.
   - Обновлён `SystemStatusControllerIntegrationTest` (5/5 green).
   - Регрессионные наборы: `SystemStatusServiceTest` (16/16 green), `GeminiContextServiceTest` (23/23 green). Итого 47/47 тестов green.

## 2026-09-11 Antigravity: Такт 20 — TocOptimizer и Drum-Buffer-Rope Falsification (пункт 19 очереди, раздел XLIII)

### Что сделано:
1. **Ликвидация ложного оптимума («System flow optimal») при недостижимом пределе (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008 False green):**
   - В `TocOptimizer.computeRecommendation` устранена подмена отсутствия измерений на утверждение об оптимальности потока («P истинно тогда и только тогда, когда P»).
   - При графе с 0 узлами или отсутствии ограничений возвращается статус:
     `Flow unmeasured: no instrumented stages present in TOC graph; flow status undetermined.`
   - При графе с $\le 1$ размеченной стадией (как живой `AUTOMERGE_PROCESSING`) возвращается:
     `Flow unmeasured: single instrumented stage ('%s') with in-flight capacity <= 1 cannot stretch buffer capacity %d; flow status undetermined.`
   - Статус `System flow optimal. Primary constraint: '%s'.` возвращается строго при $\ge 2$ стадиях конвейера, когда между ними существует измеримый относительный поток и буфер не переполнен.
   - Логика придержания выпуска `Throttling active! Elevate priority of work targeting node '%s' and defer non-critical jobs.` полностью сохранена при реальном `bufferSize >= maxBufferCapacity`.
2. **Устранение неизменного магического порога (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010 Data lineage loss):**
   - Вынесена константа по умолчанию `DEFAULT_MAX_BUFFER_CAPACITY = 15L`.
   - Добавлен конструктор `TocOptimizer(TocExecutionGraph graph, long maxBufferCapacity)` и аннотированный сеттер `@Value("${eneik.toc.max-buffer-capacity:15}") setConfiguredMaxBufferCapacity`.
   - `setMaxBufferCapacity(newCap)` динамически обновляет снимок `latestDbrStatus` и вычисляет рекомендацию с учётом актуальной топологии графа.
3. **Заслоняющие тесты:**
   - Создан `TocOptimizerTest` (7/7 green):
     - `initialBaselineStatusDoesNotClaimSystemFlowOptimal` (базис не содержит "optimal")
     - `emptyGraphEvaluationReportsFlowUnmeasured` (пустой граф -> unmeasured)
     - `singleInstrumentedStageRefutesSystemFlowOptimal` (фальсифицирующий заслон на 1 шаг -> doesNotContain "optimal")
     - `singleInstrumentedStageWhenBufferExceededEnforcesThrottling` (придержание работает)
     - `multiStageFlowWithinBufferLimitReportsSystemFlowOptimal` (многостадийный поток -> optimal)
     - `setMaxBufferCapacityUpdatesRecommendationDynamically` (динамическая подстройка емкости)
     - `computeRecommendationTruthTable` (полная истинностная таблица)
   - В `TocSentinelServiceTest` добавлены фальсифицирующий `singleInstrumentedStageDoesNotClaimSystemFlowOptimal` и подтверждающий `multiStageFlowWithinCapacityReportsSystemFlowOptimal`.

### Архитектурный вопрос по топологии шагов конвейера и обратной связи DBR:
**Вопрос:** Какие стадии конвейера должны быть размечены как узлы `TocExecutionGraph` и где должна натягиваться верёвка?
**Анализ обратной связи (Инвертированная петля):**
- В текущем коде единственный размеченный узел — `AUTOMERGE_PROCESSING`, и верёвка `shouldAdmit` проверяется перед его запуском (`AutoMergeService:165`).
- В теории Голдратта верёвка (Rope) связывает барабан (Drum, самое узкое место) с **началом потока** (Release of Work into the Gate), замедляя впуск новой работы при переполнении буфера перед ограничением.
- Придерживать же сам сливающий узел (`AutoMergeService`), когда накопились готовые PR — это обратная связь с неверным знаком: при перегрузке фабрики мы перестаём сливать PR, отчего очередь только растёт, а число наблюдений падает до 0.
- **Целевая топология:**
  1. Входной гейт / Верёвка (Rope): `JulesDispatchService` (запуск новых задач воркерам) — именно здесь `shouldAdmit()` должен придерживать низкоприоритетные задачи, если буфер узкого места переполнен.
  2. Размеченные стадии потока:
     - `DISPATCH_PROCESSING` (`JulesDispatchService`)
     - `COMPILATION_PROCESSING` (`TechnicalLeadCompiler` / `ProjectFlowService`)
     - `JULES_SESSION_ACTIVE` (`JulesSessionService`)
     - `PR_REVIEW_PROCESSING` (`PrReviewService`)
     - `AUTOMERGE_PROCESSING` (`AutoMergeService`)
  При такой 5-стадийной разметке TOC-граф станет реальной моделью потока создания ценности фабрики, выявляющей истинное узкое место.

### 2026-09-11 Antigravity: Такт 21 — Сверка трёх источников корпуса образцов и безопасная генерация (`generate_philosopher_patterns.py`, пункт 20 очереди)
- **Что сделано:**
  1. **Сверка трёх источников корпуса (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008):**
     - Выявлены и согласованы все три происхождения: порождённое генератором (86 философов, 1720 образцов), ручной `03_PATTERN_STRENGTH.md` (53 семейства) и ручной `04_FACTORY_DERIVED_PATTERNS.md` (4 образца, выведенных из фабрики).
     - Доказано точное 1:1 совпадение 53 семейств из `03_PATTERN_STRENGTH.md` со слотами `PERSONAL_SLOT_POOL` и `philosopher_patterns_index.json` (53 == 53).
     - Подтверждено, что 4 образца из `04_FACTORY_DERIVED_PATTERNS.md` не имеют пересечений с 1720 образцами индекса и несут порядковый номер $\ge 21$.
     - Все 104 общих паттерна (ACP-001..100, 101, 102, 107, 108) внесены в `COMMON_PATTERNS` генератора и согласованы с `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.
  2. **Защита от разрушения ручных находок и безопасная генерация:**
     - Устранено предварительное удаление `shutil.rmtree(PEOPLE_DIR)`.
     - В генератор встроены `EXTENDED_COMMON_SECTIONS` (подробное описание правил, доказательств и инцидентов для ACP-101, 102, 107, 108), что предотвращает затирание ручных правок при перезапуске скрипта.
     - Добавлена функция `verify_corpus()`, которая валидирует непротиворечивость всех трёх источников перед записью файлов на диск.
     - Добавлен CLI-флаг `--check` / `--verify` для сухой валидации без записи на диск.
  3. **Заслоняющие тесты:**
     - Создан `PhilosopherPatternCorpusConsistencyTest.java` (6/6 green):
       - `all53FamiliesInPatternStrengthMatchPhilosopherIndexJsonExactly` (53 == 53)
       - `factoryDerivedPatternsDoNotCollideWithGeneratedIndexAndHaveOrdinal21OrHigher` (0 коллизий, ordinal >= 21)
       - `allAcpPatternsInCommonMarkdownMatchGeneratorScriptExactly` (104 == 104)
       - Фальсифицирующие тесты: переименование семейств, пропуск ACP в генераторе и коллизия идентификаторов гарантированно вызывают сбой проверки.
     - Честное указание границы: тест роняет проверку на этапе `mvn test` (локально и в CI). При сборке образа `Dockerfile.backend` используется `mvn -q package -DskipTests`.

### Вопрос Оператору и Клоду по пробелу ACP-103…106:
**Вопрос:** В нумерации общих аналитических паттернов (`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`) после `ACP-102` сразу идут `ACP-107` и `ACP-108`. Паттерны `ACP-103…106` упоминались в старом плане `LIVE_PRODUCT_PLAN_2026-08-19.md` (§9.3–9.5), но в текущий корпус не вошли. Планируется ли их формулирование и внесение в корпус (как общих паттернов фабрики), либо нумерация остаётся разреженной намеренно?

### 2026-09-11 Antigravity: Такт 22 — Закрытие анонимного контура пульта управления (Предписания 24 + 59, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`, `BOUNDARY_TOPOLOGY` / D006; `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009)
- **Что сделано:**
  1. **Закрытие управляющего контура пультов (`BOUNDARY_TOPOLOGY`, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006):**
     - В `WebConfig` и `ApiAuthorizationInterceptor` область действия расширена на все изменяющие методы (`POST`, `PUT`, `PATCH`, `DELETE`) по маскам `/api/**` и `/internal/**`. Все 64 изменяющих входа защищены.
     - Безопасные чтения (`GET`, `HEAD`, `OPTIONS`) сохранены открытыми для браузерного CORS и телеметрии.
     - Предписание 59: изменяющие вызовы `/internal/**` теперь строго требуют авторизационных заголовков (`X-API-Key` или `Authorization: Bearer`), даже при запросах с `127.0.0.1`.
     - Запрос без заголовков получает единый `401 UNAUTHORIZED`. Запрос с неверным ключом или при отсутствии сконфигурированного ключа на сервере — `403 FORBIDDEN`.
  2. **Ликвидация подмены вебхуков (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009):**
     - Снято исключение `/api/webhooks/**`: до реализации отдельного механизма HMAC-подписи (`X-Hub-Signature-256`) и очистки заготовочных веток с `findAll()`, изменяющие запросы к вебхукам требуют авторизационного ключа.
     - Анонимный вызов `POST /api/webhooks/github` без ключа немедленно получает отказ `401 UNAUTHORIZED`, предотвращая запуск поддельных PR opened с несанкционированным закрытием клеймов исполнителей, захватом аккаунтов и платными сессиями Jules.
  3. **Изоляция тестового окружения:**
     - В `src/test/resources/application-test.properties` добавлен тестовый ключ с явным комментарием: `# Test-only secret key for JUnit integration tests; not the production factory key`.
     - `AccountControllerIntegrationTest`, `SettingsControllerIntegrationTest`, `WishlistControllerIntegrationTest`, `GreetingControllerIntegrationTest`, `ProjectFlowIntegrationTest`, `SystemStatusControllerIntegrationTest`, `TocSentinelControllerTest` обновлены с передачей тестового ключа. В тестах на 401 применён нативный `java.net.http.HttpClient`, предотвращающий JDK `HttpRetryException` потокового режима `HttpURLConnection`.
     - В `ProjectFlowIntegrationTest` добавлен MockBean `RepositoryStackAnalyzer`, изолирующий brownfield onboarding flow от внешних запросов к GitHub.
  4. **Предупреждение Оператору:**
     - 8 изменяющих кнопок/запросов веб-панели администрирования (`/api/accounts`, `/api/ai/resources`, `/api/settings`, `/api/projects`, `/api/wishlist`) без передачи заголовка `X-API-Key` или `Bearer` начнут получать отказ `401 Unauthorized` / `403 Forbidden`.
- **Чем проверено:**
  1. `ApiAuthorizationInterceptorTest` (17/17 green):
     - Изменяющие вызовы консолей (`/api/accounts`, `/api/settings`, `/api/projects`, `/api/wishlist`) без ключа -> 401 UNAUTHORIZED.
     - Изменяющие вызовы консолей с валидным ключом (`X-API-Key`, `Bearer`) -> 200 OK.
     - Вебхук `POST /api/webhooks/github` без ключа -> 401 UNAUTHORIZED (Relation 13).
     - Вебхук `POST /api/webhooks/github` с валидным ключом -> 200 OK (Relation 13b).
     - Безопасные чтения GET по всем путям -> 200 OK.
     - Изменяющие вызовы `/internal/**` с localhost без ключа -> 401 UNAUTHORIZED.
     - Изменяющие вызовы `/internal/**` с localhost с валидным ключом -> 200 OK.
  2. Интеграционные тесты контроллеров:
     - `AccountControllerIntegrationTest`: 5/5 green (включая живой сетевой отказ 401 через HttpClient).
     - `SettingsControllerIntegrationTest`: 5/5 green.
     - `WishlistControllerIntegrationTest`: 8/8 green.
     - `GreetingControllerIntegrationTest`: 5/5 green.
     - `TocSentinelControllerTest`: 2/2 green.
     - `SystemStatusControllerIntegrationTest`: 5/5 green.
     - `ProjectFlowIntegrationTest`: 5/5 green.
     - Регрессия: 52 интеграционных теста green, BUILD SUCCESS.
- **Что берётся следующим:** Предписания 18, 19, 20, 21.

### 2026-09-11 Antigravity: Предписание 21 — Нестираемый институциональный факт изменений и удалений (Закон 12, `INSTITUTIONAL_FACT_REGISTER` / D007), остатки Предписания 20
- **Что сделано:**
  1. **Остатки Предписания 20 закрыты:**
     - **Все нарушенные конъюнкты называются явно (`MULTIPLE_CONJUNCTS_VIOLATED`):** При проверке допуска аккаунта (`evaluateNamedAccountAdmissionDecision`) все конъюнкты проверяются независимо. Если нарушены несколько (например, аккаунт `disabled` и одновременно в `daily_limited` или `decommissioned`), сообщение и статус объединяют все нарушенные условия через `" and "` (`"Compiler account '<name>' violated multiple conditions: administratively disabled (enabled=false) and resting / daily limit / API block (status=daily_limited)"`).
     - **Честная фиксация невоспроизведённого отказа при повторной проверке (`REFUSAL_NOT_REPRODUCED_ON_RECHECK`):** При отказе захвата строки по `lockAccountByNameWithCapacity`, если при повторном чтении все условия выполнены, система честно регистрирует `"Compiler account '<name>': refusal not reproduced on recheck (locked or state change)"` со статусом `"named_account_refusal_not_reproduced"`, не подменяя факт смены состояния на `LOCKED_BY_CONCURRENT_CLAIM`.
  2. **Предписание 21 закрыто (`INSTITUTIONAL_FACT_REGISTER` / D007):**
     - **Истинная фиксация субъекта действия (`AuditCallerResolver`):** Устранена фальсификация субъекта (D013); контекст вызова извлекается из `RequestContextHolder` с фиксацией IP-адреса и типа ключа (`"носитель ключа оператора (IP: ...)"`, `"анонимный запрос (IP: ...)"`, `"не установлено (internal/system)"`).
     - **Нестираемый аудит мутаций системных настроек (`SYSTEM_SETTING_MUTATION_RULE`):**
       - `PUT /api/settings` принимает опциональный `reason` (`SettingUpdateRequest`).
       - `SystemSettingsService.save(key, value, reason)` считывает предыдущее значение и при мутации регистрирует институциональный факт в `defect_journal` (`category="INSTITUTIONAL_AUDIT"`, `defectType="SYSTEM_SETTING_MUTATION"`, `rootCausePatternId=null`).
       - Описание фиксирует ключ, `old: '...' -> new: '...'` (секреты маскируются), субъекта, правило `SYSTEM_SETTING_MUTATION_RULE` и причину. Холостые перезаписи без изменения значения не создают записей.
     - **Нестираемый аудит удаления аккаунтов (`ACCOUNT_DELETION_RULE`):**
       - `DELETE /api/accounts/{id}` принимает параметр `reason`.
       - Аннотирован `@Transactional`. Инвариант порядка: удаление из БД (`accountRepository.deleteById(id)`) выполняется строго *до* записи факта в `defect_journal`. При сбое БД фантомные записи аудита исключены.
       - Методы `update` и `applyStatus` в `AccountController` переведены на `@Transactional` с сохранением перед аудитом и интеграцией `AuditCallerResolver`.
- **Чем проверено:**
  1. `NamedAccountAdmissionTruthTableTest`: добавлены тесты `multipleConjunctsNamesBothDisabledAndResting` и `lockedByConcurrentClaimRefusalNotReproduced`.
  2. `SystemSettingsServiceTest`: тесты фиксации старого/нового значений, маскирования секретов и отсутствия холостых записей.
  3. `SettingsControllerIntegrationTest`: сквозной HTTP PUT со сменой настройки и проверкой факта в `defect_journal`.
  4. `AccountLifecycleInvariantTest`: проверка удаления аккаунта с `ACCOUNT_DELETION_RULE`, субъектом и причиной.
  5. `AccountControllerIntegrationTest`: сквозной HTTP DELETE с проверкой удаления сущности и появления факта в `defect_journal`.
  6. `ProjectFlowServiceLaw1JulesDispatchTest`: структурный инвариант единственного вызова `lockAccountByNameWithCapacity` соблюдён.
- **Что берётся следующим:** Предписание 28 в `docs/FACTORY_MECHANISMS.md` («Два механизма дизайна числятся сильными», `FALSIFICATION_HARNESS` / D008 + `LEVEL_OF_ABSTRACTION_LOCK` / D010).

### 2026-09-12 Antigravity: Предписание 27 — Ликвидация несегментированного дампа задач и сканирования таблицы (`PRINCIPLED_INTEGRITY` / D012, `CATEGORY_ERROR_SCAN` / D002)
- **Что сделано:**
  1. **Ликвидация full-table dump (`GET /internal/tasks`):**
     - Вызов `taskRepository.findAll()` полностью удалён из `InternalTaskController` (0 вызовов).
     - Введена постраничная и проектно-сегментированная выборка с параметрами `projectId`, `limit` (по умолчанию 50, жесткий потолок `MAX_LIMIT = 200`), `page` и `offset`.
     - При указании `projectId` читаются строго задачи проекта: `taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)`.
     - При отсутствии `projectId` выборка строго ограничена потолком `MAX_LIMIT`: `taskRepository.findAllByOrderByCreatedAtDesc(pageable)`.
  2. **Ликвидация сканирования таблицы при точечных запросах (`CATEGORY_ERROR_SCAN` / D002):**
     - `getTaskByLinearId` переведён с `findAll().stream().filter(...)` на прямой точечный репозиторный запрос `taskRepository.findFirstByLinearIssueId(linearIssueId)`.
     - Добавлен прямой эндпоинт точечного чтения `GET /internal/tasks/{id}` (`taskRepository.findById(id)`).
     - В `scripts/modules/db_utils.py` метод `get_task_by_id(task_id)` переведён на прямое чтение `GET /{task_id}` вместо скачивания всей таблицы.
  3. **Правдивый Javadoc:**
     - Javadoc класса `InternalTaskController` переписан и честно документирует реальные правила `ApiAuthorizationInterceptor` (доступ по loopback или операторскому токену, изменяющие операции требуют токен) и ограничения на объём выборки.
- **Чем проверено:**
  1. `InternalTaskControllerTest` (6/6 green):
     - `getAllTasksWithProjectIdReturnsPagedProjectTasksAndNeverCallsFindAll`
     - `getAllTasksWithoutProjectIdClampsLimitToMaxLimitAndNeverCallsFindAll`
     - `getAllTasksCalculatesPageNumberCorrectlyWhenOffsetIsProvided`
     - `getTaskByIdReturnsTaskWhenFoundAnd404WhenAbsent`
     - `getTaskByLinearIdUsesRepositoryLookupAndNeverCallsFindAll`
     - `updateTaskRejectsOverwritingTerminalStatusWithConflict`
  2. `ApiAuthorizationInterceptorTest` (17/17 green): защита контура `/internal/**`.
  3. Регрессия (92/92 green): `SystemStatusServiceTest`, `TaskCarrierBackfillServiceTest`, `ObservationHostingDemarcationLaw26Test`, `ProductCapabilityServiceTest`, `SixSigmaAuditServiceTest`.
- **Что берётся следующим:** Предписание 28 в `docs/FACTORY_MECHANISMS.md`.

### 2026-09-12 Antigravity: Предписание 28 — Ликвидация ложного прохождения аудита дизайна и сетевого расхода монитора (`FALSIFICATION_HARNESS` / D008, `LEVEL_OF_ABSTRACTION_LOCK` / D010, `TRUTH_STATUS_TABLE` / D012)
- **Что сделано:**
  1. В `DesignDriftMonitorService` внедрён `DesignShopCycleRepository`: при отсутствии зафиксированного эталона сетевая выгрузка HTML не выполняется (`verifyNoInteractions(launcherClient)`), устранив утечку 70 КБ памяти/сети за цикл. Явно логируется отсутствие репозитория или эталона.
  2. Введён типизированный 3-значный вердикт `AuditVerdict`: `ACCEPTED`, `REJECTED`, `CANNOT_JUDGE` (alias `UNDECIDABLE`).
  3. В `DesignConsistencyAuditService.audit`: при отсутствии токенов в HTML (оболочка SPA `<div id="root"></div>`) или отсутствии эталона возвращается `CANNOT_JUDGE`, `traceRatio = 0.0`, `traceAccepted = false`.
  4. В `DesignAssetService`: экраны с вердиктом `CANNOT_JUDGE` пропускаются как неаудированные экраны с записью вердикта в метаданные `.json` и не отвергаются как `aesthetic_drift`.
- **Чем проверено:**
  - `DesignConsistencyAuditServiceTest` (13/13 green)
  - `DesignDriftMonitorServiceTest` (7/7 green)
  - `DesignAssetServiceTest` (10/10 green)
  - Сводный прогон: 83/83 green.

### 2026-09-12 Antigravity: Предписание 29 — Разведение сущностей в своде потока (`SENSE_REFERENCE_SPLIT` / D009, `CONVERSATION_MAXIM` / D007)
- **Что сделано:**
  1. Разведены понятия в `FlowSpineDto.FlowCounts`:
     - `failedTasksRecoveryCanResume`: строго число задач, возобновимых решателем (`countFailedTheResolverCanAct`).
     - `failedTasksTotal`: общее число всех задач проекта со статусом `TaskStatus.failed` (согласовано с `GET /internal/tasks/status-counts`).
     - `doneTasks`: суммарно `done + spike_completed`.
     - `doneTasksTotal`: строго `TaskStatus.done`.
     - `spikeCompletedTasks`: строго `TaskStatus.spike_completed`.
  2. Jackson-сериализация: `@JsonProperty`, `@JsonAlias`, `@Deprecated @JsonIgnore public long failedTasks()`.
  3. Ликвидированы устаревшие перегруженные 11- и 12-параметрические конструкторы `FlowCounts`: оставлен строго 1 канонический 14-параметрический конструктор.
- **Чем проверено:**
  - `FlowSpineServiceTest`: 27/27 green (включая рефлексивный заслон на единственный 14-параметрический конструктор).
  - Сводный прогон: 83/83 green.

### 2026-09-12 Antigravity: Предписание 30 — Честный вердикт об отказе внешних сессий, троттлинг на входе и наблюдаемость смертей носителей (`INSTITUTIONAL_FACT_REGISTER` / D007, `INUS_FACTOR_CHECK` / D007)
- **Что сделано:**
  1. В `ClaimService` внедрено ограничение повторных попыток при тождественных безымянных отказах (`DEFAULT_IDENTICAL_UNATTRIBUTED_THRESHOLD = 2`, окно отката 15 минут).
  2. В `ProjectFlowService.dispatchQueuedTasks` и `dispatchToGeneralPool`: задачи с серией одинаковых безымянных отказов получают строго 1 пробу за 15 минут, а внутренняя ротация прерывается.
  3. Введён вердикт `TaskDispatchVerdict.UNATTRIBUTED_DISPATCH_REFUSAL` («внешняя система отказывает без причины»). Задачи без внутренних ошибок запроса помечаются как возобновимые (`isResumableDispatchRefusal(task)`), защищены от списания в `failed` в `createRecoveryWishlistForOrphanedBlockedTasks`, и возвращаются в `queued` при восстановлении пула аккаунтов через `requeueUntestedTasksOnRestoredCapacity`.
  4. Смерти носителей фиксируются с меткой `sourceComponent = "carrier"`, считаются через `countCarrierDeathsPast24Hours` и выводятся наружу в `SystemStatusService` (секция `tasks` и блокер `carrier_deaths`) и `OperationalTruthService` (нарратив `activeFlow` и `blockers`).
- **Чем проверено:**
  - `DispatchAttemptBudgetTest` (17/17 green)
  - `ProjectFlowServiceTest` (40/40 green)
  - `OperationalTruthServiceTest` (17/17 green)
  - Сводный прогон: 74/74 green.
- **Что берётся следующим:** Предписание 31 в `docs/FACTORY_MECHANISMS.md` («Экран на телефоне не проверяет никто, и ревьюеру это предписано», `GROUPING_PROXIMITY_GATE` / D011 + `FALSIFICATION_HARNESS` / D008).








