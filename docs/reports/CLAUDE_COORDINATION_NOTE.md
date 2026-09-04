# 🤝 Записка Координации: Antigravity ➔ Claude (Такт 3: Рокировка Ролей)

**Дата:** 4 сентября 2026  
**Директива Оператора:** Перераспределение ролей сессий для форсирования инженерного прогресса.

---

## 1. 🔄 Официальная рокировка зон ответственности

По прямому указанию пользователя зоны ответственности меняются местами:

### 🛠️ Зона Antigravity ($\mathcal{L}_2$ / Core Engine, Architecture & Code):
* **Ведущий инженер ядра фабрики.**
* Прямое кодовое исправление ядра `EneikProductionSys` по законам модели:
  1. **Закон 4 (Предмет слияния):** замена слабых предикатов `reachedMain` на `hasRequiredMergeEvidence` в `ProjectFlowService` и `JulesDispatchService` (ликвидация слепоты к блокерам без кода).
  2. **Закон 2 (Необратимость статуса):** защита 36 мест мутации статуса от перезаписи терминальных состояний через `writeStatusUnlessTerminal`.
  3. **Законы 1, 3, 5, 8:** наведение порядка в точках вызова, замыкание контура цеха и повторных ремонтов.
* Написание юнит-тестов на исправленную логику.

### 🧪 Зона Claude ($\mathcal{L}_1 / \mathcal{L}_3$ / Infra, Build, Verification & Telemetry):
* **Инженер среды, верификации и телеметрии.**
* Запуск точечных проверок Maven по классам (`mvn test -Dtest=...`).
* Контроль Docker-контейнеров, проверка портов (18080, 5432, 8080) и туннелей.
* Мониторинг памяти и защита от зависаний процессов.
* Фиксация замеров и аудит выполнения инвариантов.

---

## 2. 🎯 Выполненные задачи Antigravity

1. **Закон 4 (Закон предмета слияния) — коммит `c344c23`:**
   * Сведение предикатов приёмки в `ProjectFlowService.computeBlockedItems` и `JulesDispatchService.reconcileDoneTasksNotReachedMain` к строгому предикату `readinessService.hasRequiredMergeEvidence(task)`.
   * Добавлены тесты `computeBlockedItemsFlagsDoneTaskWithMergedPrLackingCodeAsBlockedUnderLaw4` и `computeBlockedItemsAcceptsDoneTaskWithRequiredMergeEvidenceUnderLaw4`.

2. **Закон 20 / S2 (Закон необратимости статуса) — коммит `87d24f3`:**
   * `TaskStatus`: добавлен метод `isTerminal()` (`done`, `failed`, `spike_completed`).
   * `TaskEntity`: добавлены `isTerminal()`, строгий защитный инвариант в `setStatus(...)` (выбрасывает `IllegalStateException` при попытке перетереть терминальный статус другим), и `initializeStatus(...)` для безопасной инициализации новых сущностей.
   * Разделение 36 вызовов на инициализацию сущностей (`initializeStatus`) и мутации жизненного цикла (`setStatus` с терминальными проверками и `writeStatusUnlessTerminal`).
   * Защищены: `AutoMergeService`, `ClaimService`, `GeminiObserverActionService`, `JulesDispatchService`, `OpsAuditorService`, `PlannedWorkRecoveryService`, `ProjectFlowService`, `TechnicalLeadCompiler`, `BranchGarbageCollectorService`, `InternalTaskController`.
   * Добавлен модульный тест `TaskEntityLaw20Test`.

3. **Закон 16 (Закон предмета ревью / Вход в review) — коммит `2bb5787`:**
   * Реализован паттерн Нуэля Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE`, `NUEL_BELNAP_05_LAMBDA_CORE_REDUCTION`, `NUEL_BELNAP_08_INSTITUTIONAL_FACT_REGISTER`):
     - Чистая функция допуска `evaluateReviewAdmission`:
       - `ADMIT` (`told-true`): артефакт PR присутствует либо роль не требует кода (спецификации, документация) $\implies$ допуск в `review` и вызов quality gate.
       - `DEFER` (`told-neither`): сессия активна, но PR ещё не зарегистрирован $\implies$ решение откладывается, задача остаётся в работе, заявка не сбрасывается (защита от отказа по гонке, Закон 11).
       - `REJECT` (`told-false`): все сессии терминальны (или отсутствуют) и артефакта нет $\implies$ перевод в `failed` (состояние с выходами) и освобождение заявки.
   * Внедрён `PrReviewRepository` в `ClaimService`.
   * Создан модульный тест-гарнитур `ReviewAdmissionLaw16Test` (7 тестов).

4. **Закон 13 (Закон ёмкости / Бюджет GitHub API) — коммит `20830c9`:**
   * Реализован паттерн Сола Крипке (`SOL_KRIPKE_02_INDEXICAL_CONTEXT_LOCK`, `SOL_KRIPKE_01_RIGID_API_REFERENT`):
     - Введён детерминированный отпечаток токена `fingerprint(token)` (необратимый 16-значный SHA-256 хэш без утечки секретов).
     - Состояние бюджета шардировано в `ConcurrentHashMap<String, TokenBudget>`, изолируя величины `remaining`, `limit`, `resetAt`, `spend(o)` по токенным контекстам.
     - Исчерпание одного аккаунта больше не блокирует фабрику под другими токенами (`GITHUB_RATE_LIMITED` изолирован).
     - Ключи операций нормализуются (`stripQueryString`), что гарантирует конечность множества ключей.
     - Проброшен токен контекста в `GitHubPullRequestService`, `GithubAccessService` и `AutoMergeService`.
     - Создан тестовый гарнитур `GitHubApiBudgetLaw13Test`.

5. **Закон 1 (Закон единственной точки применения / Предикат носителя `carrier(τ)`) — текущий такт:**
   * Реализован паттерн Оккама и Тарского (`|impl(I)| = 1`):
     - В `TaskEntity` вынесены канонические методы `isCarrier()`, `carrierTaskType()`, `isWishlistCompiler()`, `isHousekeepingCarrier()`.
     - Ликвидированы разрозненные и семантически расходящиеся проверки `.has("taskType")` vs `.hasNonNull("taskType")`.
     - Все потребители (`ProjectFlowService`, `FlowSpineService`, `SystemStatusService`, `EmsMetricsService`, `InternalGeminiObserverController`, `JulesDispatchService`) сведены к прямому вызову метода сущности.
     - Создан тест-гарнитур `TaskEntityLaw1CarrierTest`, включающий структурный тест на единственность точки реализации проверки `taskType`.

6. **Синхронизация удерживаемых законов:**
   * **Закон 17 (Закон свидетельства):** подтверждено удержание в коде (`DeliveredWorkJudgmentService.evidenceForCriteria` ранжирует diff по словарю критериев, `groundNamingWhoWasLimited` фиксирует ограничение канала на стороне фабрики) — покрыто `EvidenceSelectedByCriteriaTest` и `UnsettledGroundNamesTheAskerTest`.
   * **Закон 18 (Закон неразрешённого вопроса):** подтверждено удержание (`stillWorthAsking` переспрашивает `UNDECIDABLE`, пока основание не повторилось) — покрыто `UnsettledQuestionIsAskedAgainTest`.
   * **Закон 20 / S2 (Необратимость статуса):** актуализирован статус удержания в плане действий.

7. **Закон 15 (Закон готовности / Дизайн-цех) и Закон 3 (Закон замкнутости контура):**
   * **Закон 15:**
     - В `SystemSettingsService` добавлена регистрация и типизированный доступ `design_shop_readiness_threshold` (метод `effectiveDouble(key, defaultValue)`).
     - В `DesignShopOrchestrationService` логика готовности вынесена в метод `isReadinessReached(Readiness)`: оценивает `decompositionComplete` и настраиваемый порог $\theta$ (`design_shop_readiness_threshold`, default 0.80) либо терминальный фронт фальсификации (`selfFalsificationReadyRatio >= 1.0`), ликвидируя муду бесконечного ожидания недостижимой единицы в brownfield.
     - Сохранено строгое срабатывание на нарастающем фронте (`isReady && !cycle.isLastWasReady()`) с перевзводом при расширении скоупа и информативным логированием причины удержания.
     - Создан тестовый гарнитур `DesignShopOrchestrationServiceLaw15Test` (6 тестов) и адаптирован `DesignShopOrchestrationServiceTest`.
   * **Закон 3:**
     - В `ProjectFlowService.dispatchDesignImplementation` создание сырой `TaskEntity` с ролью `BARCAN-TAG-11` заменено на сохранение `WishlistEntity` со статусом `pending`, источником `WishlistSource.design_review_concern_pattern` и ролью `DESIGN_IMPLEMENTATION_ROLE`.
     - Теперь утверждённый дизайн попадает в компилятор (`TechnicalLeadCompiler`), декомпозируется, наследует эпик и замыкает контур доставки ценности.
     - Создан модульный и структурный тестовый гарнитур `DesignImplementationLoopClosureLaw3Test`.

8. **Закон 1 (Закон единственной точки применения / Отправка в Jules):**
   * В `ProjectFlowService`:
     - Три разрозненных метода отправки с дублированием запросов к БД сведены к единому `dispatchToGeneralPool(TaskEntity, Set<String>, String mode, String exactAccountName)`.
     - `dispatchReviewTasks` делегирует в `dispatchToGeneralPool` с аргументом `mode = "REVIEWER"`.
     - `dispatchCompilerTask` делегирует в `dispatchToGeneralPool` с аргументом `exactAccountName = taskCompilerAccountName()`.
     - Запросы `accountRepository.lockNextJulesAccountWithCapacity` и `accountRepository.lockAccountByNameWithCapacity` теперь вызываются ровно из одного места в кодовой базе `ProjectFlowService.java`.
     - Создан тестовый гарнитур `ProjectFlowServiceLaw1JulesDispatchTest` со структурным контролем единственности точки вызова запросов блокировки аккаунтов и проверкой поведения.

9. **Закон 14 (Закон убеждения о внешней системе / Естественное зондирование и пересмотр убеждений ёмкости):**
   * Реализовано доказательное различие Карнапа и Поппера/Гэрденфорса:
     - `CreateSessionResult` в `JulesApiClient.java` дополнен явным предикатом `concurrentCapacityExhausted()` (выделение сигналов "concurrent", "too many open sessions", "active session limit" и т.д.).
     - Добавлен метод взаимно непересекающегося разбиения `classifyOutcome()`, возвращающий ровно один член `DispatchOutcome`, а неразобранные отказы отправляющий в `UNCLASSIFIED`.
     - `JulesDispatchService` использует `classifyOutcome()`, протоколируя сессию и передавая чистый исход в `AccountHealthService`.
     - В `AccountHealthService`:
       * `CONCURRENT_CAPACITY_EXHAUSTED` пересматривает `estimatedConcurrentCapacity` строго вниз от фактически наблюдённой точки занятости (`countOpenSessions`) с коэффициентом отката (`concurrentCapacityBackoffFactor`).
       * Настоящий `SUCCESS` на потолке зондирует и пересматривает оценку вверх с шагом `concurrentCapacityProbeStep`.
       * `PRECONDITION_UNSPECIFIED` и `UNCLASSIFIED` не двигают потолок ёмкости ни вниз, ни вверх и никого не обвиняют (различие Карнапа: незнание не маскируется под каузальное знание).
       * Конфигурация не меняет убеждения.
       * Любое движение ёмкости фиксируется в `DefectJournalEntity` (до, после, основание, занятость, оставшаяся неопределённость).
     - Разработан полный гарнитур тестов `AccountHealthServiceLaw14Test` (проверяющий все 7 доказательных обязательств) и расширен `JulesRefusalKindsTest`.

10. **Законы 2, 7, 8 (Категориальная изоляция носителей, принадлежность эпика и оборот ремонта второго порядка):**
    * Реализован паттерн Гилберта Райла (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN`) и Питера Саймонса (`PITER_SAYMONS_01_ACTUAL_OBJECT_REGISTER`, мереологическая целостность части и целого):
      - **Закон 2 (Категориальная изоляция носителей):**
        * В `produceForProject` (строка 948) и `fileTheMissingWorkAsScope` (строка 150) внедрена строгая проверка `task.isCarrier()`.
        * Внутренние фабричные задачи (`TechnicalLeadCompiler`, housekeeping, review carriers, audits) больше никогда не помечаются как `NO_MERGE_EVIDENCE` и не заказывают продуктовый скоуп.
      - **Закон 7 (Принадлежность ремонта требованию):**
        * `epicOfRequirement(task, projectFallback)`:
          - Немедленный возврат `null` для любых `carrier`-задач (`carrier(τ) → epic(τ) = ∅`).
          - Безопасный fallback на `project.getId()` при отсутствии `task.getProject()`.
          - Разрешение канонических эпиков через `readinessService.canonicalFeatureId(epicId)` (union-find).
      - **Закон 8 (Вариантная функция и поглощающее условие ремонта):**
        * Реализован рекурсивный обход глубины `repairDepthForTask` и `repairDepthOfWishlist`, проходящий через срезы (`originWishlistId`) и цепочки ремонтов.
        * Задачи первого порядка ремонта получают `depth = 1`.
        * Неудавшиеся задачи первого порядка порождают второй порядок ремонта (`depth = 2`) с наследованием исходного продуктового эпика клиента.
        * При превышении `maxRepairDepth` (по умолчанию 2, настраивается через `SystemSettingsService.effectiveInt("max_repair_depth", 2)`) цикл прекращается, а поглощающее терминальное состояние записывается в `DefectJournalEntity` (`defectType = "REPAIR_BUDGET_EXHAUSTED"`, `severity = "CRITICAL"`).
        * Продуктовые задачи без достижимого эпика регистрируются в журнале (`PRODUCT_EPIC_UNREACHABLE`, `severity = "CRITICAL"`) вместо немого сброса в лог.
      - Разработан исчерпывающий гарнитур юнит-тестов `DeliveryRealityLaw8SecondOrderRepairTest` (6 тестов) и актуализирован `DeliveryBriefZoneBoundaryTest` и `DeliveryPredicateAgreementTest`.

---

## 3. 📋 Задачи Claude (Аудит и Философские Паттерны)

* **Регламент взаимодействия:** Руководствоваться правилами из `docs/architecture/AUTONOMOUS_OPERATING_REGULATION.md`.
* **Текущий приоритетный фокус фабрики:**
  - **Закон 9 (Закон бюджета / Возврат бюджета брифа):**
    * В `ENGINEERING_PHILOSOPHY_ACTION_PLAN.md` зафиксировано: попытка компиляции брифа, не дошедшая до компилятора или отвергнутая нашей собственной валидацией (пустой ответ), не должна расходовать бюджет брифа `compileAttempts(w)`.
    * Нужно: константа пустого ответа в `JulesDispatchService.compilerPlanRejection`, метод `returnCompileAttempt(Set<UUID>)` в `ProjectFlowService` и вызов в `completeWishlistCompilation`.
  - **Закон 8 (Вариантная функция декомпозиции):**
    * Предел декомпозиции `A(w)` и временные окна (`ORCHESTRATION_COOLDOWN_SECONDS`, `BLOCKED_ITEM_STALE_THRESHOLD_HOURS`).
* **Философский паттерн:** Исследовать паттерны для Закона 9 (возврат ресурсов / справедливое вменение вины: Нэльсон Гудман / Дж. Л. Макки — различение каузального фактора системы и внешнего сбоя).
* **Режим ответа:** Минималистичный результат в плане и записке без лишнего шума.

