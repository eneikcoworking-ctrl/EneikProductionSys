# Пакетная директива L1/L3 -> L2: Ступень 4 (Семейства III и IV: Диспетчеризация Jules, воркеры, ревью, гейты качества и автослияние)

**Дата:** 2026-10-15 11:58 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Настоящая директива охватывает 16 механизмов Ступени 4 `docs/ANTIGRAVITY_QUEUE.md` (Семейства III и IV `docs/FACTORY_MECHANISMS.md`):
   - **Семейство III (Отправка: задача уходит в работу — Jules, сессии, воркеры, клеймы и здоровье):**
     * `JulesDispatchService` (ядро)
     * `JulesApiClient` (ядро)
     * `SessionLifecycleService` (ядро)
     * `PersistentWorkerSessionService` (ядро)
     * `ClaimService` (ядро)
     * `LeaseWatchdogService` (ядро)
     * `AccountHealthService` (ядро)
   - **Семейство IV (Ревью, гейты качества, слияние и очистка веток):**
     * `AutoMergeService` (ядро)
     * `GitHubPullRequestService` (ядро)
     * `GitHubApiBudgetService` (ядро)
     * `GithubAccessService` (ядро)
     * `CodeChangeClassifier` (ядро)
     * `GateOrchestrator` (ядро)
     * `VerificationEvidenceGate` (ядро) [включая `GateCheck`/`BaseQualityGate.*`, `BackendContractGate`, `DesignExcellenceGate`, `EpistemicLayerInvariantGate`]
     * `BranchGarbageCollectorService` (ядро)
     * `PrReviewPipelineService` (ядро)
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено трогать рабочий код при проходящих тестах!
   - Любая модификация рабочего кода допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего системный инвариант. Если тесты зеленые — код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:** Принцип фальсификации заслонов качества:
  * `GateOrchestrator`: исключение порока квантификации над пустым множеством (`allMatch` на 0 проверок); вердикт несет строгий знаменатель `applicableChecksByStage`, а нулевой знаменатель переводит проверку поставки в `isDeliveryVerificationAbsent`;
  * `AutoMergeService`: слияние требует реального доказательства прохождения гейтов и авторизации политики (`MERGE_PR`); внешне закрытые PR (`closed_unmerged`) терминально гасятся навсегда, исключая 405-ретраи;
  * `GitHubPullRequestService`: проверка ветки `branchExists` требует физического HTTP 404 для констатации отсутствия; `evaluateCheckRuns` требует реальных завершенных проверок CI;
  * `JulesApiClient`: четырехзначный префлайт доступности источников (`SourceAvailability: VISIBLE, MISSING, UNKNOWN`), исключающий отправку сессий в невидимые репозитории.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:** Лок уровней абстракции и надежность цепочки происхождения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`):
  * Строгое разделение уровней: сервисы сетевого транспорта (`JulesApiClient`, `GitHubPullRequestService`) изолированы от логики агрегатного учета (`PersistentWorkerSessionService`, `AutoMergeService`);
  * Сервис постоянных воркеров `PersistentWorkerSessionService` не содержит сетевых зависимостей и обращений к `JulesApiClient`;
  * Расчет кулдауна восстановления аккаунтов `AccountHealthService` (`median + z * stdDev`) выполняется строго по собственной истории без меж-аккаунтного загрязнения;
  * Сохранение нерушимого институционального следа аудита в `task_gate_logs` и `github_access_status`;
  * Кеширование снимков PR (`PullRequestSnapshot`, TTL 20с) для устранения внутритактового дрифта.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:** Деонтический запрет как исключающая причина (`exclusionary reason`) и исполняемый код:
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов отправки, ревью и слияния при проходящих тестах;**
  * Запрет отправки задач с неопределенным контекстом (`TargetContext.UNDETERMINED`) в `JulesDispatchService`;
  * Запрет выполнения исходящих сетевых вызовов при отключенной интеграции (`jules_enabled=false`, `github_enabled=false`) или пустом токене;
  * Запрет записи служебных артефактов фабрики (`.eneik/*`, `_temp_submit*.sh`) в ветки продукта (`CodeChangeClassifier`, Закон 2);
  * Запрет слияния PR при отказе операционной политики `OperationalPolicyService.authorize(MERGE_PR)`.

---

## 3. Пакетный наряд по механизмам Семейств III и IV

### Группа 1. Семейство III: Отправка, диспетчеризация Jules, сессии, воркеры и аренда
1. **`JulesDispatchService` (ядро):**
   - **Проба:** Замерить дедупликацию активных сессий, отсечение `UNDETERMINED` контекста и запрет отправки без проекта.
   - **Инвариант:** Рабочий код не трогать. Заслоны `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`.
2. **`JulesApiClient` (ядро):**
   - **Проба:** Замерить префлайт источников, таймауты (20с), классификацию отказов (Ashby variety) и запрет вызовов без ключа.
   - **Инвариант:** Рабочий код не трогать. Заслоны `JulesApiClientTest`, `JulesApiClientFalsificationTest`.
3. **`SessionLifecycleService` (ядро):**
   - **Проба:** Замерить исключение локальных фикций отмены, признание HTTP 404 за удаление и регламентный свип терминальных сессий.
   - **Инвариант:** Рабочий код не трогать. Заслоны `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`.
4. **`PersistentWorkerSessionService` (ядро):**
   - **Проба:** Замерить нулевую зависимость от транспорта Jules, предикат `isIdleAndFresh` и недеструктивный `peekCurrentBatch`.
   - **Инвариант:** Рабочий код не трогать. Заслоны `PersistentWorkerSessionServiceTest`, `PersistentWorkerSessionServiceFalsificationTest`.
5. **`ClaimService` (ядро):**
   - **Проба:** Замерить атомарный захват задач `SKIP LOCKED`, срок аренды `leaseExpiresAt`, трехзначную решетку допуска `evaluateReviewAdmission` и институциональный журнал.
   - **Инвариант:** Рабочий код не трогать. Заслоны `ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`, `ClaimResultStatusTruthTableFalsificationTest`.
6. **`LeaseWatchdogService` (ядро):**
   - **Проба:** Замерить ежеминутную жатву просроченных клеймов, продление при живой сессии Jules и неприкосновенность терминальных задач.
   - **Инвариант:** Рабочий код не трогать. Заслоны `LeaseWatchdogServiceTest`, `LeaseWatchdogServiceFalsificationTest`.
7. **`AccountHealthService` (ядро):**
   - **Проба:** Замерить единственное владение здоровьем аккаунтов, защиту от ложных обвинений при ошибках промпта (`REQUEST_REJECTED`) и расчет кулдауна.
   - **Инвариант:** Рабочий код не трогать. Заслоны `AccountHealthServiceTest`, `AccountHealthServiceLaw14Test`, `AccountHealthServiceFalsificationTest`.

### Группа 2. Семейство IV: Ревью, гейты качества, автослияние и очистка веток
8. **`AutoMergeService` (ядро):**
   - **Проба:** Замерить авторизацию политики `MERGE_PR`, терминальное закрытие `closed_unmerged`, идемпотентное слияние внешне смерженных PR и изоляцию бюджета.
   - **Инвариант:** Рабочий код не трогать. Заслоны `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`, `AutoMergeLaw20InvariantS4Test`.
9. **`GitHubPullRequestService` (ядро):**
   - **Проба:** Замерить кеш снимков PR (TTL 20с), fail-closed валидацию CI-чеков, проверку ветки по 404 и запрет записи файлов фабрики.
   - **Инвариант:** Рабочий код не трогать. Заслоны `GitHubPullRequestServiceTest`, `GitHubPullRequestServiceFalsificationTest`.
10. **`GitHubApiBudgetService` (ядро):**
    - **Проба:** Замерить изоляцию бюджета по SHA-256 отпечаткам токенов, превентивный `guard` кулдауна и парсинг заголовков `x-ratelimit-*`.
    - **Инвариант:** Рабочий код не трогать. Заслоны `GitHubApiBudgetServiceTest`, `GitHubApiBudgetLaw13Test`, `GitHubApiBudgetSpendTest`, `GitHubApiBudgetServiceFalsificationTest`.
11. **`GithubAccessService` (ядро):**
    - **Проба:** Замерить эмпирический зонд 5 аспектов репозитория, матрицу прав/обязанностей и расчет Six Sigma DPMO.
    - **Инвариант:** Рабочий код не трогать. Заслоны `GithubAccessServiceTest`, `GithubAccessControllerTest`, `GithubAccessServiceFalsificationTest`.
12. **`CodeChangeClassifier` (ядро):**
    - **Проба:** Замерить deny-list служебных скриптов фабрики (`_temp_submit*.sh`, `.eneik/*`), распознавание произвольных клиентских стеков и fail-closed поведение.
    - **Инвариант:** Рабочий код не трогать. Заслоны `CodeChangeClassifierTest`, `CodeChangeClassifierFalsificationTest`.
13. **`GateOrchestrator` (ядро):**
    - **Проба:** Замерить разделение стадий `TASK_SPEC` и `IMPLEMENTATION_RESULT`, расчет знаменателя `applicableChecksByStage` и персистенцию в `task_gate_logs`.
    - **Инвариант:** Рабочий код не трогать. Заслоны `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`.
14. **`VerificationEvidenceGate` и специализированные гейты (ядро):**
    - **Проба:** Замерить чтение реального PR diff через `resolveSessionWithPr` в `EpistemicLayerInvariantGate`, `BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate` и базовые проверки `BaseQualityGate.*`.
    - **Инвариант:** Рабочий код не трогать. Заслоны `EpistemicLayerInvariantGateTest`, профильные гейт-тесты.
15. **`BranchGarbageCollectorService` (ядро):**
    - **Проба:** Замерить безопасную очистку осиротевших веток без кода продукта, учет прогресса сессии и защиту активных задач.
    - **Инвариант:** Рабочий код не трогать. Заслоны `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`.
16. **`PrReviewPipelineService` (ядро):**
    - **Проба:** Замерить пайплайн создания/обновления сущностей ревью PR и расчет уровня риска (`RiskLevelCalculator`).
    - **Инвариант:** Рабочий код не трогать. Заслоны `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов в Docker Maven (`-m 2g`).
2. Внести отметки `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(dispatch-merge): complete Stage 4 batch verification (D008, D010, D006)`.
