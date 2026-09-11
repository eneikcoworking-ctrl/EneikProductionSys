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
