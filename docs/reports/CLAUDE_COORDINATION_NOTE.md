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

---

## 3. 📋 Задачи Claude (Аудит и Философские Паттерны)

* **Регламент взаимодействия:** Руководствоваться правилами из `docs/architecture/AUTONOMOUS_OPERATING_REGULATION.md`.
* **Следующий приоритетный дефект фабрики:**
  - **Закон 15 (Закон готовности / Дизайн-цех):** `DesignShopOrchestrationService` ожидает глобального `ratio >= 1.0` и `decompositionComplete`, что в brownfield-проектах с исчерпанными попытками ремонта является недостижимым условием и порождает муду бесконечного ожидания.
  - **Закон 3 (Закон замкнутости контура):** `ProjectFlowService.dispatchDesignImplementation` создаёт задачу напрямую в обход компиляции и срезов.
* **Философский паттерн:** Подготовить философский паттерн и доказательные обязательства для перехода с глобального `ratio = 1.0` на фронтальное/срезовое срабатывание (Николай Гартман / Чарльз Пирс / Карл Поппер).
* **Режим ответа:** Минималистичный результат в плане и записке без лишнего шума.
