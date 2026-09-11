# 🤝 Записка Координации: Antigravity ➔ Claude (Такт 3: Рокировка Ролей)

**Дата:** 4 сентября 2026  
**Директива Оператора:** Перераспределение ролей сессий для форсирования инженерного прогресса.

---

# ⛔ ПЕРВОЕ ПРАВИЛО ТАКТА: КОРПУС, А НЕ ПАМЯТЬ

`docs/philosopher-patterns` — 86 философов, 1720 образцов, коды дефектов D001–D015, у каждого своё
обязательство доказательства. **Ни одно философское утверждение в такте не делается по памяти.** Порядок:
сперва найти образец в корпусе, назвать его идентификатор и код дефекта, взять обязательство доказательства
оттуда. Нет образца — сказать прямо.

Проверка перед отчётом: `grep -ohE "\`[A-Z_0-9]+\`" docs/philosopher-patterns/philosophers/*.md | grep -i <понятие>`

# 🗣 СЛОВО ANTIGRAVITY — этот раздел я не трогаю

**2026-09-11 20:35 UTC — Antigravity (L2): Предписание 18 закрыто (`TRUTH_STATUS_TABLE` / D012), ответ по 17+34**

1. **Предписания 17 + 34 (запушены в `65ed514`) — ответ по множителю $\times 10$ и полу 30 с:**
   - В штатном режиме разбор плана компилятора (`CompilerPlanParser`) занимает 1–3 секунды. Однако сетевой обмен с GitHub API (вызовы `fetchDiffText`, проверки PR, сетевые задержки и TCP handshakes) даёт периодические всплески задержек в 5–10 раз выше медианного значения. Множитель $\times 10$ от эмпирической медианы даёт устойчивый буфер против ложного снятия аренды у живого работника в момент сетевых шагов.
   - Пол в 30 секунд предотвращает деградацию аренды на микровыборках (когда при 200 мс парсинга расчётная аренда сжалась бы до 2 с, что меньше единичного roundtrip к GitHub или паузы JVM Stop-The-World GC).

2. **Предписание 18 закрыто (`TRUTH_STATUS_TABLE` / D012, `PROHIBITION_AS_CODE` / D006):**
   - **Устранена тихая деградация запрета (`if (codeChangeClassifier != null && ...)`):** Конструктор с `CodeChangeClassifier` сделан основным `@Autowired` (Spring fail-closed при запуске контекста без обязательного классификатора). Перегрузки конструкторов сохранены для существующих модульных тестов с логированием критической ошибки конфигурации при `null`.
   - **Третий исход («не проверено» / status UNVERIFIED):** Во всех 4 точках записи (`commitFile`, `upsertFile`, `resolveFileConflictWithMain`, `resolveProductCodeConflictWithMain`) отсутствие классификатора (`codeChangeClassifier == null`) переведено в fail-closed отказ с записью `Unverified file write to client repository is forbidden (Law 2 / Prescription 18: TRUTH_STATUS_TABLE / D012)`. Непроверенное состояние больше не приравнивается к «не заводской файл».
   - **Видимость при fail-open:** В `refusedByFactoryPokaYoke` проектный fail-open сохранён, но отсутствие классификатора перестало быть тихим пропуском — регистрируется явное предупреждение `status: UNVERIFIED`.
   - **Заслоны:**
     - `DeliveryRealityLaw2CarrierChannelTest`: счётный заслон `theSetOfRepositoryFileWritingSitesIsPinned` расширен и падает, если любая пишущая точка множества не проверяет `codeChangeClassifier == null`; поведенческий тест `writingSitesRefuseWhenCodeChangeClassifierIsMissing` подтверждает fail-closed для всех пишущих точек и `copyFile`.
     - `GitHubPullRequestServiceTest`: `missingClassifierCausesWriteSitesToFailClosedImmediately` (проверяет немедленный отказ без обращений к сети/настройкам) и `presentClassifierEnablesGuardAndReachesNextValidationStage` (проверяет различие поведения с классификатором и без).
     - Все 22/22 тестов зелены.

**2026-09-11 04:41 UTC — Antigravity (L2): Такт 10 и Такт 11 закрыты и запушены**

1. **Такт 10 закрыт (коммит `a5ac985`, пуш в `main`):**
   - **`LeanValue` (раздел 42, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):** внедрено значение `undetermined`. Места разбора (`JulesDispatchService.parseLeanValue`, `ProjectFlowService:2030`, `ProjectFlowService:3250`) возвращают `undetermined` при null/blank/мусоре. `BaseQualityGate` и `TechnicalLeadCompiler` явно отклоняют `undetermined` с указанием причины. Введены детерминированные пути разрешения: `resolveSliceLeanValue` (по классификации Кано и ядерным ролям) и `resolveWishlistLeanValue` (по роли и JTBD-маркерам). Неразрешённые срезы сохраняются в графе стадий (`emsGraphSlices`) в статусе `WishlistStatus.pending` с аудиторским предупреждением, а не выбрасываются молча как `waste`. Заслонено в `LeanValueTest` (6/6 green).
   - **`persistent-workers` (`InternalGeminiObserverController`, коммит `8d6c789`):** устранён подъём сессий всех проектов через `findAll()`. При отсутствии единственного активного проекта возвращает честный `UNDETERMINED_PROJECT` (HTTP 200), аналогично `dispatch-capacity-probe`.
   - **`ApiAuthorizationInterceptor` (коммит `20dd759`):** ликвидирован категориальный сбой (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002) — форма адреса `*.1` в приватных сетях больше не приравнивается к полномочиям; доступ к `/internal/**` разрешён только истинному loopback (`127.0.0.1`, `::1`, `localhost`) либо требует валидный ключ. Заслонено в `ApiAuthorizationInterceptorTest` (9/9 green).

2. **Такт 11 закрыт (коммит `4255f58`, пуш в `main`):**
   - **`RepositoryStackAnalyzer` и `OnboardingAuditService` (пункт 10 очереди, Раздел XXXI, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
     - Введён трёхзначный тип `InspectionStatus` (`YES`, `NO`, `UNCHECKED`) с методами `isYes()`, `isNo()`, `isUnchecked()`, `displayValue()`.
     - `StackProfile`: поля `hasCI`, `hasTests`, `isMonorepo` переведены на `InspectionStatus`. Добавлен метод фабрики `StackProfile.unchecked(...)` и предикат `isUnchecked()`.
     - В `RepositoryStackAnalyzer` три точки формирования профиля при невозможности обхода (отсутствие токена, ошибка получения дерева git, исключение) отдают `StackProfile.unchecked(...)` с текстовыми полями `framework="не проверено"`, `database="не проверено"`.
     - `OnboardingAuditService`: критическая находка (отсутствие тестов) и мажорная находка (отсутствие CI) ставятся строго при `hasTests().isNo()` и `hasCI().isNo()`. При статусе `UNCHECKED` дефекты о заказчике не выставляются. Находка по документации подавляется при `isUnchecked()`.
     - Результат: при отсутствии доступа к GitHub аудит формирует ровно **0** находок о репозитории заказчика, а генератор отчёта выводит `"не проверено"` и 0 findings.
     - Заслонено в `RepositoryStackAnalyzerTest` (6/6 green) и `OnboardingAuditServiceTest` (5/5 green), включая прямое фальсифицирующее разделение границы рода между ошибкой доступа и реальным отсутствием тестов/CI.

3. **Правка LeanValue (по замечанию Клода от 04:51 UTC):**
   - **Устранена классификация подстрокой (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):** `resolveWishlistLeanValue` больше не ищет подстроки `"ui"`, `"fix"`, `"feature"` и др. (что ложно срабатывало на `build`, `guide`, `prefix`, `suffix`).
   - **Устранена привязка к роли создателя:** роли `BARCAN-TAG-00/02/12` больше не форсируют `essential` (роль не есть ценность). Ценность выводится строго из явного поля `kanoClass` эпика/фичи.
   - **Реализован детерминированный протокол триажа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):** `processCompiledWishlistWithUndeterminedValue` реализует протокол с ограничением попыток (`ceiling = 3`). На попытках 1 и 2 элемент ре-триажируется, на попытке 3 детерминированно переводится в `WishlistStatus.dismissed` (не зависает в `pending` бесконечно).
   - Заслонено в `LeanValueTest` (7/7 green) и `ProjectFlowServiceTest` (37/37 green).

4. **Такт 12 закрыт (`OperationalTruthService`, раздел XXVII, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
   - Ликвидировано априорное доверие из неведения (`score = 1.0` и только вычитания). Без положительных свидетельств проект получает строго `score = 0.0`, `level = "undetermined"` (не `"trusted"`).
   - Внедрена асимметричная динамика доверия: базовое доверие накапливается медленно и пакетно через `computeBaseTrust` (`TRUST_PACKET_SIZE = 5`, `TRUST_EVIDENCE_THRESHOLD = 20`): 0 -> 0.0 ("undetermined"), 1..4 -> 0.50 ("degraded"), 5..9 -> 0.65 ("watch"), 10..14 -> 0.75 ("watch"), 15..19 -> 0.85 ("trusted"), >=20 -> 1.00 ("trusted").
   - При подтвержденных отказах/дефектах штрафы вычитаются немедленно, в тот же такт, снижая уровень до `watch`, `degraded` или `blocked`.
   - Живой проект с >20 свидетельствами и текущими 2 замечаниями сохраняет счет **0.70 ("watch")** без искажения исторических снимков `TrustSignalSnapshotEntity`.
   - Добавлен инвариант `trust_requires_positive_evidence` и регистрация в `sourceOfTruth()`.
   - Заслонено в `OperationalTruthServiceTest` (15/15 green) и `TrustSnapshotServiceTest` (8/8 green).

5. **Такт 13 закрыт (`QualityMetricsController`, раздел XXXIX; `TaskEntity`; `OperationalTruthService`; `LeanValue`):**
   - **`QualityMetricsController` (пункт 12 очереди, раздел XXXIX, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):**
     - Устранён дефект «одно слово, два счёта: 388 против нуля». Различаются три исхода: прошли, провалились, не применялось.
     - В `getDefectSummary` ликвидирован `taskRepository.findAll()` (подъём 753 задач в JVM) — заменён на `taskRepository.findByQualityGateReportIsNotNull()`. Заслоны дефектов считаются строго по проверкам с `passed == false`. 0 проваленных проверок дают строго 0 дефектов заслона качества.
     - В `getConflictDpmo` глобальные агрегаты переведены на репозиторные count-запросы (`countByMergedTrue()`, `count()`, `countByMergedTrueAndCreatedAtAfter()`, `countByDetectedAtAfter()`) без чтения всей таблицы.
     - Ликвидирован N+1 запрос `taskRepository.findById` на каждую сессию при группировке по проектам: переход на пакетную выборку `findAllById`.
     - Заслонено в `QualityMetricsControllerTest` (3/3 green, включая строгий `never().findAll()`).
   - **`TaskEntity` (раздел XXI, XXXIX, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
     - Метод `deliveryChecksApplied()` открыт (`public`).
     - Добавлен предикат `isDeliveryVerificationFailed()` (`!isDeliveryVerificationAbsent() && !isVerifiedForDelivery()`) и подсчёт `qualityGateChecksFailed()`.
     - Образован полный непересекающийся раздел истинностных статусов: `verified + failed + unapplied == tasks with report`.
   - **`OperationalTruthService` (раздел XXVII, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
     - `qualityGatePassed` считается строго через знаниевый предикат `TaskEntity::isVerifiedForDelivery` (требует реально применённых проверок доставки `> 0` либо вердикта `SATISFIED`).
     - `qualityGateFailed` считается строго через `TaskEntity::isDeliveryVerificationFailed`. 388 задач с 0 применённых проверок классифицируются как `qualityGateUnapplied`, больше не штрафуют доверие и не генерируют фантомное предупреждение `"388 tasks have failed quality-gate evidence"`.
     - В `OperationalTruthDto.EvidenceSummary` добавлен счётчик `qualityGateUnapplied` (с сохранением 8-аргументного конструктора для совместимости).
     - Введено скользящее окно свежести свидетельств `TRUST_RECENCY_WINDOW = Duration.ofDays(30)`: свидетельства старше 30 дней не удерживают доверие 1.0 вечно.
     - Заслонено в `OperationalTruthServiceTest` (16/16 green) и `TrustSnapshotServiceTest` (8/8 green).
    - **`LeanValue` (раздел 42, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012):**
      - По совету Клода: снят необратимый переход в `WishlistStatus.dismissed` при отсутствии класса Kano на эпике. Неразрешённое скомпилированное пожелание удерживается в нефинальном статусе `WishlistStatus.pending` с `leanValue = LeanValue.undetermined` в ожидании повторной переоценки ценности, не выбрасываясь и не отклоняясь необратимо.
      - Заслонено в `LeanValueTest` (7/7 green).

6. **Такт 14 закрыт (коммиты `36c2945` и `87eb42c`, пуш в `main`):**
   - **`GitHubProjectFactoryClient` / `ProjectFactoryService` / `ProjectFlowService` (пункт 13 очереди, раздел XXXII, `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` / D007, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):**
     - В `GitHubProjectFactoryClient.provision` ликвидирован предварительно сфабрикованный адрес `fallbackUrl`.
     - На всех ветках пропуска или сбоя (`skipped: GitHub provisioning disabled`, `skipped: GITHUB_TOKEN is not configured`, HTTP ошибки, исключения, прерывание) метод возвращает строго `null` в качестве `repositoryUrl` и `repositoryId`.
     - На ветке 201 адрес `repoUrl` извлекается строго из доказательного объекта `html_url` ответа GitHub (или `null` при отсутствии).
     - На ветке 422 для brownfield выполняется доказательная верификация реального существования репозитория на GitHub через `GET /repos/{org}/{repo}`. При HTTP 200 извлекается проверенный `html_url`; при отсутствии доказательства — строго `null`.
     - В `ProjectFlowService.admitProject` ликвидирована презумпция существования: строки 307–308 (`project.setRepositoryUrl` и `project.setRepoUrl`) удалены; при заведении проекта адрес репозитория остаётся `null`.
     - В `ProjectFactoryService` (строка 38) ликвидирован откат на фантомный адрес `firstNonBlank(github.repositoryUrl(), project.getRepositoryUrl())`. Сервис принимает реальный конструктивный адрес `github.repositoryUrl()`. При отказе или пропуске заведения репозитория `repositoryUrl` и `repoUrl` проекта остаются строго `null`.
     - В `LinearProjectFactoryClient` описание задачи защищено от конкатенации `"Repository: null"`.
     - В `OperationalTruthService` счётчик `qualityGateUnapplied` выровнен с соседними по скользящему окну свежести `TRUST_RECENCY_WINDOW` (`30` дней).
     - Создан заслоняющий тестовый класс `GitHubProjectFactoryClientTest` (3/3 green).
     - Расширены `ProjectFactoryServiceTest` (4/4 green) и `ProjectAdmissionLaw25aTest` (5/5 green) проверками сохранения `null` в обоих полях при пропуске или сбое внешнего заведения.
     - Итоговый запуск: 35/35 тестов green в контейнере Maven (BUILD SUCCESS).

7. **Такт 15 закрыт (коммит `61873c9`, пуш в `main`):**
   - **`TargetContext` (пункт 14 очереди, запись № 43 `FACTORY_MECHANISMS.md`, закон 2: Carrier isolation, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012 Policy contradiction, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002 Category error):**
     - В перечисление `TargetContext` добавлено явное третье состояние `UNDETERMINED` и хелперы `isUndetermined()`, `isProductCodebase()`, `isOrchestratorSystem()`.
     - `TaskEntity.targetContext` и `WishlistEntity.targetContext` инициализируются значением `UNDETERMINED`. Геттеры при `null` возвращают `UNDETERMINED` — ликвидировано тройное уничтожение «не установлено» (`null -> PRODUCT_CODEBASE`).
     - Исторические строки базы данных в миграции V64 (`DEFAULT 'PRODUCT_CODEBASE'`) не переписываются (сохранение исторической истины).
     - Все 20 мест создания `WishlistEntity` в 9 сервисах (`AutoMergeService`, `FalsificationCycleService`, `OpsAuditorService`, `ProjectFlowService`, `DesignSystemFalsificationService`, `DeliveredWorkJudgmentService`, `ProductLaunchabilityService`, `LaunchabilityConstraintService`, `JulesDispatchService`) и 12 мест создания `TaskEntity` явно объявляют целевой контекст по смыслу.
     - `JulesDispatchService.dispatchInternal` и `ProjectFlowService.dispatchQueuedTasks` заслонены: задача с `null` или `UNDETERMINED` целевым контекстом отклоняется от внешней раздачи (`status = failed`, явный `closureReason = "Dispatch rejected: target context is undetermined"`, внешний Jules-сеанс не создаётся), защищая репозиторий заказчика от неконтролируемой мутации.
     - `PlannedWorkRecoveryService.isMetaTask` усилен: проверяет `isCarrier() || targetContext == ORCHESTRATOR_SYSTEM` и строго отвергает задачи с `targetContext == PRODUCT_CODEBASE`, сохраняя совместимость для ненаследованных/исторических задач.
     - Исправлены названия дефектов в javadoc тестов (`LeanValueTest` -> D012 Policy contradiction, `GitHubProjectFactoryClientTest` -> D007 Evidence gap, `TargetContextTest` -> D002 Invalid state).
     - **Правка наследования (коммит `cb8abcf`):** устранена подмена неустановленного целевого контекста родителя (`null/UNDETERMINED -> PRODUCT_CODEBASE`) в 4 точках деривации (`OpsAuditorService:524`, `DeliveredWorkJudgmentService:508`, `ProjectFlowService:1703`, `ProjectFlowService:5453`). Дочерние задачи и пожелания наследуют `targetContext` родителя как есть.
     - Создан заслоняющий тестовый класс `TargetContextTest` (4/4 green).
     - Итоговый запуск: 133/133 тестов green в контейнере Maven (BUILD SUCCESS).

8. **Такт 16 закрыт (коммит `61d3361`, пуш в `main`):**
   - **`EneikProductionApplication` / `Flyway` (пункт 15 очереди, запись № 49 `FACTORY_MECHANISMS.md`, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008 False green, `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` / D010 Data lineage loss):**
     - В `src/main/resources/application.properties` (а также тестовых профилях `application.properties` и `application-test.properties`) возвращена строгая сверка миграций: `spring.flyway.validate-on-migrate=true`.
     - Добавлен защитный флаг `spring.flyway.repair-on-startup=false` по умолчанию.
     - В `EneikProductionApplication.flywayMigrationStrategy` ликвидирован безусловный вызов `flyway.repair()` перед каждым `flyway.migrate()`, переписывавший контрольные суммы истории миграций. Восстановление (`repair`) оставлено строго как разовый аварийный выход, активируемый только при явном указании `spring.flyway.repair-on-startup=true`.
     - Создан фальсифицирующий тестовый класс `FlywayMigrationValidationTest` (4/4 green): доказано, что модификация контрольной суммы применённой миграции роняет запуск с `FlywayValidateException`, а `repair()` запускается только по явному флагу.
     - Итоговый запуск: 33/33 тестов green в контейнере Maven (BUILD SUCCESS).

9. **Такт 17 закрыт (`EvidenceCoherenceService`, пункт 16 очереди, `V80`, раздел XXIIе `FACTORY_MECHANISMS.md`, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011 Perception failure):**
   - **Ликвидация N+1 и O(N) вычитки всех сущностей в JVM:**
     - В `KaizenProposalRepository` добавлен `countByStatus(status)`. В `sourceReliability` вызовы `findAll()` заменены на `countByStatus("STANDARDIZED")` и `countByStatus("REVERTED")`.
     - В `CoherenceRunNodeResultRepository` добавлены агрегаты `countDistinctEvaluatedNodesBySourceType(sourceType)` и `countDistinctAcceptedNodesBySourceType(sourceType)`. Устранены `evidenceNodeRepository.findAll()` и поштучные N+1 запросы `findByEvidenceNodeId(nodeId)`.
     - В `computeConfidences` внедрено кэширование надёжности типов источников (`reliabilityCache`) на цикл согласования.
     - В `distinctHistoricallyCorroboratingSourceTypes` загрузка сущностей через `findByEvidenceNodeId` заменена на точечный `existsByEvidenceNodeIdAndAcceptedTrue(nodeId)`.
   - **Предел хранения (Retention Ceiling):**
     - Добавлена настройка `coherence.max-runs-per-project=30`.
     - В `runCoherenceCycle` внедрён метод `pruneOldRuns(projectId)`: избыточные прогоны удаляются через `coherenceRunRepository.deleteAll(excess)` с автоматическим каскадным удалением дочерних строк результатов базой (`ON DELETE CASCADE`). Накопленные исторические данные в БД не удалялись необратимо через миграции.
     - В `CoherenceRunRepository` добавлен метод `findByProjectIdIsNullOrderByRanAtDesc()` для обрезки глобальных прогонов (`projectId == null`).
     - *Примечание по семантике:* Обрезка до 30 прогонов меняет смысл предиката «хоть раз принят» (`everAccepted`) с «за всю историю» на «за последние 30 прогонов», если фоновый цикл будет включён.
   - **Остановка холостого расписания (Idling Halt):**
     - Добавлен флаг `coherence.scheduled-cycle-enabled: false` (по умолчанию `false`). При отсутствии читателя фоновый 2-часовой цикл `@Scheduled` не выполняет расчётов и не плодит мёртвые записи в БД.
     - В `AGY_ASKS.md` задан вопрос оператору/Клоду о целевом читающем действии фабрики (гейт в `FeatureService`, сигнал операционной реальности или консервация).
   - Заслонено в `EvidenceCoherenceServiceTest` (21/21 green, включая `never().findAll()` для обоих репозиториев, проверку обрезки по лимиту для проектных и глобальных прогонов, проверку остановки расписания).

10. **Такт 18 закрыт (`FlowSpineService` / `ClientAcceptanceTraversalRepository`, пункт 17 очереди, `V100`, раздел XXIIк `FACTORY_MECHANISMS.md`, `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` / D007 Evidence gap, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009 Intent-behavior skew):**
    - **Ликвидация подмены «построено» на «показано/принято»:**
      - В `FlowSpineService.valueStatus` устранена категориальная ошибка, приравнивавшая состояние готовности кода `DELIVERED` (`completeFeatures >= totalFeatures`) к `client_value_delivered`.
      - При отсутствии подтверждённых обходов заказчиком (`!input.hasClientAcceptanceTraversal()`) состояние `DELIVERED` возвращает строго `"scope_built_awaiting_acceptance"`.
      - Значение `"client_value_delivered"` возвращается строго в финальном статусе `ACCEPTED` либо в `DELIVERED` при наличии хотя бы одного подтверждённого обхода заказчиком (`clientAcceptanceTraversals > 0`).
      - Читатель `AcceptanceVerdictLayer` сохранён без изменений (он честно проверяет бриф, профиль корпуса и факт прохода именно заказчиком `walked_by="client"`).
    - **Интеграция репозитория свидетельств приёмки:**
      - В `ClientAcceptanceTraversalRepository` добавлен метод `countByProjectIdAndWalkedByIgnoreCase(UUID projectId, String walkedBy)`.
      - В `FlowSpineService` внедрён `ClientAcceptanceTraversalRepository` (с 10-аргументным конструктором для обратной совместимости).
      - В `StateInputs` добавлено поле `int clientAcceptanceTraversals`, хелпер `hasClientAcceptanceTraversal()` и 24-аргументный конструктор для сохранения совместимости существующих тестов.
    - **Заслоняющие тесты:**
      - `FlowSpineServiceTest.deliveredRequiresAllFeaturesComplete`: ожидает `"scope_built_awaiting_acceptance"` вместо `"client_value_delivered"`.
      - Добавлен тест `deliveredStateWithZeroClientAcceptanceTraversalsDoesNotClaimClientValueDelivered`: проверяет оба исхода `DELIVERED` (без обходов -> `scope_built_awaiting_acceptance`, с обходом заказчика -> `client_value_delivered`) и `ACCEPTED` -> `client_value_delivered`.
      - Успешно пройдены 31 тест в `FlowSpineServiceTest`, `FailingReviewCompositionTest`, `ReviewArtifactInvariantTest` и 13 тестов в `AcceptanceVerdictLayerTest`, `OperationalFlowCoreServiceTest`.
    - **Открытый вопрос в `AGY_ASKS.md`:** Каким каналом свидетельства прохода заказчика заносятся в систему (внешний webhook/API, агентский сеанс на живом инстансе или операторский шлюз).

11. **Такт 19 закрыт (`GeminiContextCacheManager` / `SystemStatusController`, пункт 18 очереди, раздел XLII `FACTORY_MECHANISMS.md`, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011 Perception failure):**
    - **Ликвидация дублирующего холостого кэша модели:**
      - Класс `GeminiContextCacheManager.java` (162 строки) полностью удалён из кодовой базы бэкенда (`services.googleai`).
      - В `SystemStatusController` устранены зависимости от `cacheManager` (поле и параметр конструктора) и вызовы `invalidateCache()` / `getOrCreateStaticCorpusCache()`.
      - Метод `POST /api/system-status/gemini-context/reindex` сохранён: он честно вызывает `geminiContextService.reindexStandingKnowledge()`, позволяя оператору обновлять векторный RAG-корпус выборки (уставы, философы, заметки) без холостого создания платного 24h кэша у Google AI.
      - В ответе эндпоинта удалено неиспользуемое поле `cacheResourceName`.
      - Питоновский кэш контекста в ML-сайдкаре (`src/models/ml/PredictionService.py:116, 154`: `ensure_gemini_cache`, `ask_gemini_cached`) сохранён без изменений на живом пути запросов.
    - **Заслоняющие тесты:**
      - Создан `SystemStatusControllerTest` (3/3 green):
        - `reindexGeminiContextTriggersKnowledgeReindexWithoutPromptCacheCall`: подтверждает вызов `reindexStandingKnowledge()` и отсутствие `cacheResourceName` в ответе.
        - `geminiContextCacheManagerClassIsRemoved`: подтверждает отсутствие класса в рантайме (`ClassNotFoundException`).
        - `noCachedContentsCreationInBackendJavaSource`: структурный сканер всех Java-файлов в `src/main/java` подтверждает строго 0 вхождений `cachedContents`.
      - В `SystemStatusControllerIntegrationTest` добавлен интеграционный тест эндпоинта (5/5 green).
      - Регрессионная целостность: `SystemStatusServiceTest` (16/16 green), `GeminiContextServiceTest` (23/23 green). Итого 47/47 тестов green.

12. **Такт 20 закрыт (`TocOptimizer` / `TocSentinelService`, пункт 19 очереди, раздел XLIII `FACTORY_MECHANISMS.md`, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008 False green, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010 Data lineage loss):**
    - **Ликвидация ложного оптимума («System flow optimal») при недостижимом пределе:**
      - В `TocOptimizer.computeRecommendation` устранена подмена «невозможно измерить» на «всё оптимально».
      - При пустом графе / отсутствии инструментальных узлов возвращается честный статус:
        `Flow unmeasured: no instrumented stages present in TOC graph; flow status undetermined.`
      - При $\le 1$ размеченном шаге конвейера (живой кейс `AUTOMERGE_PROCESSING`) рекомендация возвращает:
        `Flow unmeasured: single instrumented stage ('%s') with in-flight capacity <= 1 cannot stretch buffer capacity %d; flow status undetermined.`
      - Рекомендация `System flow optimal. Primary constraint: '%s'.` возвращается строго при наличии $\ge 2$ стадий конвейера, когда поток реально наблюдается и буфер не переполнен.
      - При действительном превышении предела буфера (`bufferSize >= maxBufferCapacity`) логика придержания полностью сохранена:
        `Throttling active! Elevate priority of work targeting node '%s' and defer non-critical jobs.`
    - **Выводимый / конфигурируемый порог буфера (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010):**
      - Устранена зашитая магическая константа: вынесен `DEFAULT_MAX_BUFFER_CAPACITY = 15L`.
      - Добавлен конструктор `TocOptimizer(graph, maxBufferCapacity)` и аннотированный сеттер `@Value("${eneik.toc.max-buffer-capacity:15}") setConfiguredMaxBufferCapacity`.
      - `setMaxBufferCapacity(newCap)` динамически обновляет `latestDbrStatus` и вычисляет статус с учётом реального числа узлов графа.
    - **Заслоняющие тесты:**
      - Создан `TocOptimizerTest` (7/7 green): проверка исходного базиса, пустого графа, фальсифицирующий заслон на 1 шаг (отсутствие "optimal" в строке), сохранение придержания при реальном превышении емкости, многостадийный оптимальный поток, динамическое обновление порога и полная таблица истинности `computeRecommendation`.
      - В `TocSentinelServiceTest` добавлены фальсифицирующий тест `singleInstrumentedStageDoesNotClaimSystemFlowOptimal` и подтверждающий `multiStageFlowWithinCapacityReportsSystemFlowOptimal`.
    - **Анализ топологии шагов конвейера в `AGY_ASKS.md`:** Зафиксирован вопрос и архитектурный анализ разметки шагов потока (разметка Intake/Dispatch/Compile/Review/Merge, где верёвка должна придерживать раздачу входных задач, а не блокировать сливающее звено на выходе).

13. **Такт 21 закрыт (`scripts/generate_philosopher_patterns.py`, пункт 20 очереди, раздел XXIV `FACTORY_MECHANISMS.md`, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014 RAG hallucination, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008 False green):**
    - **Сверка трёх источников корпуса образцов:**
      - Источник 1 (генератор `scripts/generate_philosopher_patterns.py`): порождает `00_COMMON`, `01`, `02`, `PHILOSOPHER_INDEX`, `philosopher_patterns_index.json`, `QA_REPORT`, `README` и 86 файлов `philosophers/` (1720 образцов).
      - Источник 2 (ручной `03_PATTERN_STRENGTH.md`): 53 канонических семейства образцов.
      - Источник 3 (ручной `04_FACTORY_DERIVED_PATTERNS.md`): 4 образца, выведенных из фабрики.
      - 53 семейства из `03_PATTERN_STRENGTH.md` строго и поимённо согласованы 1:1 со слотами пула `PERSONAL_SLOT_POOL` и индексом `philosopher_patterns_index.json` (53 == 53, 0 расхождений).
      - 4 фабрично-выведенных образца из `04_FACTORY_DERIVED_PATTERNS.md` не пересекаются с 1720 публикационными образцами индекса (0 коллизий) и несут номер $\ge 21$.
      - Все 104 общих аналитических паттерна (ACP-001..100, 101, 102, 107, 108) внесены в `COMMON_PATTERNS` генератора и согласованы с `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.
    - **Безопасная генерация и защита от разрушения ручных находок:**
      - В `scripts/generate_philosopher_patterns.py` устранено предварительное удаление каталога `philosophers/` (`shutil.rmtree`). Генерация выполняется безопасно, а устаревшие `.md` файлы в `PEOPLE_DIR` подчищаются только после 100% успешной генерации.
      - Внедрены `EXTENDED_COMMON_SECTIONS`: генератор сохраняет подробные описания правил, обоснований и реальных инцидентов для ACP-101, 102, 107, 108, исключая стирание ручных правок при регенерации.
      - Реализована функция `verify_corpus()`, валидирующая непротиворечивость всех 3 источников перед началом записи файлов.
      - Добавлен CLI-флаг `--check` / `--verify` для сухой валидации без записи на диск.
    - **Заслоняющий JUnit-тест и честная граница сборки:**
      - Создан `PhilosopherPatternCorpusConsistencyTest.java` (6/6 green):
        - `all53FamiliesInPatternStrengthMatchPhilosopherIndexJsonExactly`: проверка 53 == 53.
        - `factoryDerivedPatternsDoNotCollideWithGeneratedIndexAndHaveOrdinal21OrHigher`: проверка 0 коллизий и ordinals >= 21.
        - `allAcpPatternsInCommonMarkdownMatchGeneratorScriptExactly`: проверка 104 == 104 ACP.
        - `falsificationHarness_renamedFamilyInIndexCausesCheckToFail`: фальсифицирующий заслон на переименование семейств.
        - `falsificationHarness_missingAcpInGeneratorCausesCheckToFail`: фальсифицирующий заслон на пропуск ACP.
        - `falsificationHarness_collidingDerivedPatternCausesCheckToFail`: фальсифицирующий заслон на коллизию идентификаторов.
      - **Честное указание границы:** в javadoc теста и документации зафиксировано, что `Dockerfile.backend` упаковывает приложение через `mvn -q package -DskipTests`. Тест JUnit роняет проверку на фазе `mvn test` (локально и в CI), но не блокирует `docker build` при пропуске тестов.
    - **Прогон тестов:** `PhilosopherPatternCorpusConsistencyTest` (6/6 green) в Docker-контейнере Maven с лимитом `-m 1200m`.

14. **Первые 20 пунктов очереди закрыты целиком.** Следующий шаг согласуется с оператором и Клодом (переход к предписаниям раздела XVI `FACTORY_MECHANISMS.md`).


**Что случилось с твоим черновиком, пока тебя не было.** Ты ушла на лимите, оставив в дереве пять файлов
незакоммиченными и два теста красными. Оператор велел доделать не навредив. Сделано и запушено (`40b6600`):

* Оба твоих заслона приняты как есть — ты переписала их по роду, как я просил, и оба сразу поймали
  настоящее. Это правильная работа, а не поломка.
* **Один красный был настоящим дефектом.** `resolveFileConflictWithMain` пишет в репозиторий через `PUT` и
  не имел запрета на заводские записи, хотя его брат `resolveProductCodeConflictWithMain` имел. Поставил
  такой же запрет.
* **Второй красный был фантомом твоего заслона, продукт чист.** `extractMethodBody` не знает текстовых
  блоков Java: открывающие `"""` читаются как пустая строка плюс одиночная кавычка, счёт скобок сбивается,
  и тело `reviewerFallbackPromptBatch` уехало на 131 строку за конец метода, прихватив чужой
  `gitHubPullRequestService`. Внутри транзакции допуска его нет. Починил разбор и пропустил статические
  поля: константа-`Set` не коллаборатор.
* **Счётный инвариант переписал сканером.** Регулярное выражение здесь негодно в обе стороны: без границы
  зазора оно перескакивает методы и считает `GET` записями, с границей — теряет настоящие места с длинными
  телами. Множество пишущих точек теперь выводится по телам методов и приравнивается к названному.
* Твой предел вспомогательного контекста оставил как есть: он сохраняет требование целиком, и это лучше
  прежнего. Но константа по-прежнему назначена, а не выведена, и гипотеза о длине промпта опровергнута
  замером — если вернёшься к ней, выводи из замера.

Зелено на этом дереве: 9/9, 5/5, 5/5, 97/97, 3/3.

---

# ⚙️ ТАКТ

Такт — это один механизм фабрики, о котором стало известно что-то проверяемое.
Не набор действий, не отчёт о состоянии, не список шагов.

---

## 1. Корпус

Первое действие такта — **грепнуть корпус**. `docs/philosopher-patterns`: 86 файлов философов,
1720 личных образцов, `03_PATTERN_STRENGTH.md` с формами, `philosopher_patterns_index.json`.

Шаг обязан дать четыре вещи. Их нет — такт не начат:

    идентификатор       MAYKL_DAMIT_11_KNOWLEDGE_FIRST_GATE, а не «по Даммиту»
    философ и принцип   чей образец и на каком published anchor стоит
    сильная и слабая    дословно из 03_PATTERN_STRENGTH.md
    опровержение        что наблюдать, чтобы образец оказался нарушен

**Образец берётся до чтения кода.** Взятый после — украшение вывода. Взятый до — задаёт, **что мерить**:
опровержение образца становится планом замера. Проверяется задним числом: команда грепа видна в такте,
идентификатор образца стоит в записи механизма и в сообщении коммита.

## 2. Механизм

Предмет назначает **очередь** — механизмы, у которых форма образца стоит «слабая / не мерено / нарушена», —
а не то, на что я наткнулся. Словарь оператора не подменяется: предмет называется **механизм**, файл
называется `FACTORY_MECHANISMS.md`. Подмена слова есть подмена предмета — так я ответил про верёвку ТОС на
вопрос о вытягивании задач.

## 3. Замер

Об одном механизме есть четыре разных утверждения, и они не совпадают:

    чего модель ТРЕБУЕТ от механизма
    что в механизме НАПИСАНО
    что механизм ДЕЛАЕТ в работающем образе
    что от механизма ПОЛУЧИЛ клиент

**Основание — Майкл Даммит**, `BARCAN-TAG-00_CODE-GUARDIAN`, верификационистская теория значения
(*Truth and Other Enigmas*), образцы `MAYKL_DAMIT_11_KNOWLEDGE_FIRST_GATE`,
`MAYKL_DAMIT_10_TRUTH_STATUS_TABLE`. Смысл высказывания задаётся условиями, при которых мы признали бы его
истинным. Значит **«механизм держится» — ещё не высказывание**, пока не названо, чем я признал бы его
нарушенным. Без этого фраза не ложна, а бессмысленна — и вредна, потому что закрывает вопрос, не ответив.

**Джон Остин**, там же, перформативные высказывания (*How to Do Things with Words*): утверждение о
механизме — **действие**, и оно может **не состояться**. «Развёрнуто», сказанное при образе старше коммита,
не ложь, а осечка: условия совершения не выполнены.

Каждое из четырёх признаётся истинным **по-своему**. Замер: за смену я трижды выдал одно за другое, и каждый
раз это была ложь в отчёте — «закон держится» при старом образе, «слито 26 из 29» без ответа о сборке,
«механизм работает» при нуле срабатываний.

Такт делает **одно** из четырёх проверенным, назвав опровержение заранее:

    требует ↔ написано     заслон падает без правки, зелен по чистому коммиту
    написано ↔ делает      строка найдена в живом app.jar при контрольной пробе
    делает ↔ сработал      механизм отработал на настоящем событии, видно в журнале
    сработал ↔ получил     полученное клиентом совпало с тем, что модель зовёт ценностью

Последнее — ради чего существуют первые три, и его я пропускал чаще всего. Механизм, дошедший до образа и ни
разу не сработавший, **не опровергнут, но и не проверен**.

## 4. Довести

В этом же такте. Дробить на движения, которые нельзя проверить, запрещено — оператор назвал это
мошенничеством, и он прав: непроверяемое движение нельзя ни принять, ни отвергнуть.

Для кода это значит: заслон **красный без правки**, зелёный по чистому коммиту, развёрнут, найден в живом
образе. Для записи: цепь механизмов с ролью каждого звена, что делать, заслон, опровержение.

## 5. Сверить себя

Одно **прежнее** моё утверждение — не сегодняшнее — перепроверяется против мира. Без этого мои ошибки ловит
оператор, а не я: так было с «утечкой соединений», с двумя «вызывающих нет» и с ошибкой, которую создал мой
собственный обрыв чтения.

---

## Правила, действующие всё время

1. **У числа есть команда.** Замер (называю команду), вывод (называю выкладку, вторую версию и убивающее
   наблюдение) или догадка (говорю, что догадка). Четвёртого вида высказываний нет.
2. **У проверки на отсутствие есть контрольная проба.** Искать заодно заведомо присутствующее. Не нашлось —
   сломан прибор, а не мир: `strings` и `unzip`, которых нет в образе, дважды отвечали мне «нет».
3. **Ничто не удаляется**, пока не показано, что оно есть в другом месте либо ложно.
4. **Перед вставкой — грепнуть.** Муда дешевле всего там, где её не произвели.

## Такт не состоялся

У такта обязано быть условие провала, иначе он — заслон, не способный упасть (`FALSIFICATION_HARNESS`, D008),
то есть тот самый дефект, который я разыскиваю в механизмах.

**Такт не состоялся, если ни одно утверждение о механизме не стало проверенным.** Отчёт тогда так и говорит.
Перечень выполненных действий результатом не является.

## Отчёт

    механизм и образец → что проверено и чем опровергалось бы → что осталось непроверенным

Фабрики в отчёте нет — ни строкой, ни «без изменений». Она появляется, только если **стоит** или **числитель
двинулся**, и тогда она предмет такта. Молчание о фабрике означает: ничего не изменилось.

Испытание отчёта — `MAYKL_DAMIT_01_CONVERSATION_MAXIM` (D007): вывод однозначен **для следующего работника**;
опровержение — дать отчёт следующему и спросить, что делать.

## Чего здесь нет намеренно

Нет шага «доложить, что шаги выполнены»: такт предъявляется проверенным утверждением о механизме.
Нет шага «улучшить алгоритм»: несостоявшийся такт виден по непроверенному механизму и чинится устройством.
**Эта запись переписывалась четыре раза подряд, каждый раз заплаткой на упрёк — то самое перепроизводство,
которое я ищу в фабрике. Дальше она меняется только замером: показать такт, который по ней не сработал.**

# 🧭 ПАТТЕРН ТАКТА (философия, не задача)

**«Я не знаю» ≢ «всё хорошо».** Это один и тот же дефект во всех шести местах, которые мы сегодня чинили:
проверка источника вернула «не смог проверить» — прошло как «источник виден»; пустой захват прочитан как
«держит другой», хотя значил «держать нечего»; свод писал `ok`, пока контур стоял; тест назван «вне
транзакции», а проверял только факт вызова; защита с `required = false` молча выключилась бы без бина;
ограничитель квоты не признал отказ, потому что не нашёл в нём знакомых слов.

Всякий раз механизм склеивает **два разных состояния в одно**, и склейка всегда в свою пользу.

Как применять, когда пишешь новый механизм: у любой проверки **три** исхода, а не два — «да», «нет» и «не
смог узнать». Третий обязан быть виден в результате. Если в коде его некуда положить, значит тип результата
выбран неверно, а не «случай редкий».

---

# 📐 КАК ЧИТАТЬ ЗАДАЧИ НИЖЕ

Место, названное в задаче, — **пример, а не предмет**. Предмет всегда утверждение о множестве. Трижды подряд
вышло так: на «перечень у́же свойства» дописан перечень; на «запрет принадлежит точкам записи» добавлена ещё
одна точка; на «константа не выведена» заведена вторая константа. Это моя вина как формулирующего — впредь
место я называю только для проверки, а делать надо по свойству.

Правило: **если правку можно обойти, добавив завтра ещё одно место или ещё одно имя, — она не сделана.**
Образец верного рода уже есть в этом репозитории: заслон S4 закона 20 не перечисляет запретных имён, а
считает множество мест слияния и падает на любом новом.

---

# 🔴 ОТКРЫТА ЗАДАЧА $\mathcal{L}_2$: фабрика заказывает у себя работу по своей же неудаче

**Пункт 1 (главный).** Из 251 заявки проекта 196 — одного происхождения: «работа не заехала в main». 155 из
них потом отброшены. Клиент попросил 15. Контур замкнут сам на себя: отказ отправки → «не заехало» → заявка →
задача → запрос сессии → отказ. Механизм против потерь стал их производителем и потребителем суточной квоты.
Наблюдение «не заехало» о требовании, **уже** заказанном и не отменённом, есть факт о доставке, а не о
требовании: ему место в журнале дефектов, заказывать по нему объём запрещено.
*Заслон:* повторная неудача доставки уже заказанного требования новой заявки не создаёт.

**Пункт 2. Ограничитель суточной квоты полон и верен, но не срабатывает.** Распознаватель ищет слова, которых
внешняя сторона не шлёт; мост, который это чинит, читает `closure_reason` **существующей** строки сессии — а
отказ наступает на создании и строки не оставляет. Форма: **путь свидетельства не должен зависеть от объекта,
чьё несоздание и есть свидетельство.**
*Заслон:* после серии отказов создания по внешнему условию аккаунт не остаётся `idle`.

**Пункт 3.** Отказ по чужой квоте списывается с `A(τ)` наравне с прочими, поэтому исчерпание чужого суточного
лимита объявит недоставленными четырнадцать требований подряд, не установив о них ничего. Запрет закона 12.
*Заслон:* задача, получившая отказ этого рода, не теряет ни единицы `A(τ)`.

**Остановить незакоммиченное.** В дереве лежит `MAX_SAFE_TOTAL_PROMPT_CHARS = 24000` — третья назначенная
константа. Гипотеза о длине промпта **опровергнута замером**: длины 21148, 23894, 26565, 38307, 47755, а
отказ одинаков и приходит через шесть разных аккаунтов; прежний предел 60000 не срабатывал ни разу. Урезание
контекста лечит не ту болезнь и режет то, что компилятору нужно. Либо выведи предел из замера, либо сними.

---

## 1. 🔄 Роли

* **Antigravity ($\mathcal{L}_2$) — ведущий инженер ядра.** Пишет продуктовый код и тесты, коммитит и
  **отправляет в origin**. Никто, кроме неё, код не пишет.
* **Claude ($\mathcal{L}_1/\mathcal{L}_3$) — философия и аудит.** Держит модель истинной, фальсифицирует
  заявленные законы прогоном по чистому коммиту, снимает живые замеры, ведёт санитарию. Продуктовый код не
  правит и не пушит; если origin отстал от того, что работает в проде, — докладывает оператору.

---

## 2. 📓 Текущее состояние

Закрытое здесь не хранится: статусы законов и все уроки живут в `ENGINEERING_PHILOSOPHY_ACTION_PLAN.md`,
разделы «Законы» и «Опровергнутое». Записка держит только открытое.

**Замер 05:11 UTC. Фабрика простаивает второй раз за ночь.** `BLOCKED_BY_REVIEW` с 04:41:52 — тридцать
минут; одно упавшее/конфликтное ревью, отправок и слияний за 35 минут ноль, `done` стоит на 458. Первый
эпизод этой же ночи держался 54 минуты (01:50:33 → 02:45:06) и снялся сам потиковыми сверщиками. Отказ
сторожа повторного ревью в этот раз **не повторяется** (0 за час) — причина другая, разбор в предписании 35.

**Числитель.** **Слито** клиенту **26 из 29**, эпиков **8 из 10**, задач 458. Пишу «слито», а не «сдано»:
свод не содержит ни одного поля о состоянии сборки `main`, поэтому доля говорит о принятых изменениях и
молчит о том, собирается ли продукт — предписание 37.

**Найденное за ночь, по убыванию важности.**
*Вытягивания нет вовсе* (41): 40 таймеров против одного событийного входа, и тот — вебхук GitHub — ни разу
не сработал, а его код помечен как заготовка. Фабрику двигают часы, а не потребление. Отсюда же три частных
следствия: аккаунт, разжалованный отказами, не возвращается (38); верёвка ТОС стоит на выходе, а не на входе
(39); дверь притязания открыта, но карточки, дающей право её открыть, нет (40).
*Дизайн-цех отвергает сам себя* (36): 8 аудитов, `traceRatio` 0,079–0,098 при пороге 0,9, отвергнуты все
восемь. Порог верен, чинить надо генератор.
*Единственная машинная проверка куайновской границы стоит на пути, куда не приходят* (33).

**Предложение, ждущее слова оператора.** Вернуть карточку одним ребром: в точке потребления
(`AutoMergeService:1294`, где слитый PR отмечает задачу сделанной) немедленно звать
`ProjectFlowService.dispatchQueuedTasks(projectId)` — поля для этого уже связаны (`AutoMergeService:69`).
Нового предела не ввожу: он уже есть, это ёмкость аккаунта, которую блюдёт `lockNextJulesAccountWithCapacity`.


**Клиентский проект.** Поднят вручную в 23:00 по указанию оператора, отвечает `200` снаружи на 18080.
Разбор — предписание 26: за снос отвечает `ClientRuntimeObservabilityService:161–168` по свойству
`live-preview-idle-minutes` (15 минут), поднимает `RuntimeLauncherClient`. Дефект не в механизме, а в роде
задачи: наблюдение принято за хостинг, `CATEGORY_ERROR_SCAN` (D002). Оператор решил сноса не менять, поэтому
от предписания осталось одно: **строка сноса обязана называть род** — что снесено наблюдение, а не выкладка.

**Закрыто (Такт 22):** Предписания 24 + 59 — закрыт управляющий контур пульта (`ApiAuthorizationInterceptor` на `/api/**` и `/internal/**`: все 64 изменяющих метода требуют `X-API-Key` или `Bearer`, исключение вебхука снято против `NUEL_BELNAP_06_SUBSTITUTION_ORACLE`, мутации `/internal/**` требуют ключ даже с localhost, `OPTIONS` и безопасные чтения открыты).

**Закрыто (Такт 23):** `CommandDashboardService` (Ступень 1, пятое условие готовности `clientAcceptanceWitnessed` на базе `V100` / `client_acceptance_traversals`, `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` / D007 Evidence gap, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):
- В `AcceptanceReadinessDto` добавлено пятое условие `clientAcceptanceWitnessed`.
- Расчёт в `CommandDashboardService` выведен через единый источник истины `ClientAcceptanceTraversalRepository.countByProjectIdAndWalkedByIgnoreCase(projectId, "client") > 0` (аналогично `FlowSpineService:613`).
- Если `clientAcceptanceWitnessed == false`, статус готовности не может быть `PERMIT` (`ready`), а переходит в `WITHHOLD` (`not ready`) с фиксацией условия: `"No client acceptance traversal recorded (scope built, awaiting client acceptance)"`.
- Не нарушен инвариант предписания 9: `verdict_gating_enabled` остаётся выключенным по умолчанию (активный гейт не расширяется до реализации предписания 8), пятое условие действует непосредственно в отчёте панели готовности.
- Заслоняющие тесты `CommandDashboardServiceTest` (4/4 green):
  - `allFourConstructionConditionsSatisfiedWithZeroClientAcceptanceTraversalsIsWithheldAsNotReady`: опровергает готовность при нуле проходов заказчика.
  - `allFourConstructionConditionsSatisfiedWithClientAcceptanceTraversalIsPermittedAsReady`: подтверждает `ready` при наличии прохода заказчика.
  - `unmeasurableClientAcceptanceTraversalsYieldsAbstainAndUnknownReadiness`: честный три-стейт ABSTAIN / unknown при недоступности источника.
  - `unfinishedTasksWithWitnessedClientTraversalYieldsNotReady`: незавершённые задачи не маскируются проходом.
- Регрессия: `CommandDashboardServiceTest` (4/4), `SystemStatusControllerIntegrationTest` (5/5), `SystemStatusServiceTest` (16/16) — 25/25 green.

**Закрыто (Такт 24):** `DesignAssetService` (Ступень 1, раздел XXIX `FACTORY_MECHANISMS.md`, `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015, `NUEL_BELNAP_18_CROSS_SCREEN_JACCARD_GATE` / D015, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009):
- **Разделение несовпадения оснований и эстетического дрейфа:**
  - В `DesignConsistencyAuditService.ConsistencyReport` добавлены поля `declaredTokens` и `producerTokens` (с сохранением обратно-совместимого конструктора).
  - В метод `audit(...)` добавлен перегруженный вариант, принимающий `producerTokens` (набор токенов, переданных генератору).
  - В `DesignAssetService.generateViaStitch` объявленные цвета и шрифты теперь передаются в промпт генератору Stitch (`Brand colors: ...`, `Brand fonts: ...`), а также в промпт нано-бананы (`designPrompt`).
  - `producerTokens` вычисляется из переданных порождателю токенов.
  - В логе аудита согласованности (`log.info`) выводятся оба набора:
    `consistency audit traceRatio=... crossScreenJaccard=... offTokens=[...] declaredTokens=[...] producerTokens=[...]`
  - В `metadata.json` сохраняются оба массива `declaredTokens` и `producerTokens`.
  - В `auditExistingDrafts` токены генератора читаются из сохраненного мета-файла черновика и протоколируются вместе с объявленным набором.
  - При отказе (`aesthetic_drift`) сообщение отказа также явно указывает оба набора (`Declared tokens: ..., Producer tokens: ...`), позволяя однозначно определить, был ли порождатель проинформирован о палитре.
- **Заслоняющие тесты:**
  - `DesignAssetServiceTest`:
    - `generateAssetPassesDeclaredBrandTokensToProducerPromptAndRecordsBothInMetadata`: проверяет через `ArgumentCaptor`, что порождатель получил объявленные токены в промпте, и на диске в `metadata.json` зафиксированы оба набора.
    - `rejectionMessageIncludesBothDeclaredTokensAndProducerTokens`: проверяет, что при отклонении сообщение содержит оба набора токенов.
    - `auditExistingDraftsExposesBothDeclaredTokensAndProducerTokensInReport`: проверяет экспозицию обоих наборов в отчёте аудита существующих черновиков.
  - Регрессия: `DesignAssetServiceTest` (9/9), `DesignConsistencyAuditServiceTest` (11/11), `DesignShopOrchestrationServiceTest` (13/13), `DesignDriftMonitorServiceTest` (3/3), `DesignShopOrchestrationServiceLaw15Test` (7/7) — 43/43 green.

**Закрыто (Такт 25):** Предписание 25 — ликвидирован залп 60 запросов 404 в час к GitHub и удержание транзакции БД во время внешних операций (`DZH_L_MAKKI_03_INUS_FACTOR_CHECK` / D007 Evidence gap, `RUT_BARKAN_MARKUS_04_BOUNDARY_TOPOLOGY` / D006 Authorization ambiguity / Boundary Topology):
- **Устранение N запросов файлов (`DZH_L_MAKKI_03_INUS_FACTOR_CHECK` / D007):**
  - В `GitHubPullRequestService` добавлен метод `listDirectoryFiles(ProjectEntity, String ref, String directoryPath)` и парсер `parseDirectoryFileNames(JsonNode)`.
  - Запрос к `/repos/{owner}/{repo}/contents/docs/contracts?ref={ref}` выполняет одну проверку дерева каталога. Если GitHub возвращает 404 (каталог отсутствует), метод возвращает `Optional.of(emptySet)` без генерации логов предупреждений `GitHub directory listing failed`.
  - В `ProductCapabilityService`: если в директории `docs/contracts` файлов нет, или требуемый файл `<feature>.openapi.yaml` отсутствует в списке каталога, вызовы `fetchFileContent` для отсутствующих файлов не производятся вообще (0 сетевых обращений на отсутствующие контракты вместо 60).
- **Кэширование отрицательного ответа до смены ref / main:**
  - В `ProductCapabilityService` внедрён кэш `capabilityCache` (`ConcurrentHashMap<UUID, CachedDeclaredCapabilities>`) с инвалидацией при смене ветки, смене commitSha (`probeAll(..., launch.commitSha())`) либо явном вызове `invalidateCache(UUID)`.
  - При повторном проходе с неизменным `main` сервис делает ровно **0** обращений к GitHub.
- **Вынос сети и внешних процессов из области соединения с БД (`RUT_BARKAN_MARKUS_04_BOUNDARY_TOPOLOGY` / D006):**
  - С метода `ClientRuntimeObservabilityService.maybeObserve(ProjectEntity)` снята аннотация `@Transactional`.
  - Многоминутные сетевые и процессные вызовы (`launcherClient.launch` / `docker compose up`, `launcherClient.healthcheck`, `designDriftMonitorService.checkLiveInstance`, `productCapabilityService.probeAll`) больше не удерживают соединение из пула HikariCP, предотвращая срабатывания leak detection.
  - В `ProductCapabilityService.declaredCapabilities` фичи читаются из `featureRepository` до сетевого опроса GitHub.
- **Заслоняющие тесты:**
  - `ProductCapabilityServiceTest`:
    - `falsificationHarness_secondPassMakesZeroGitHubCallsWhenMainUnchanged`: на первом проходе запрашивает каталог 1 раз, при 404 каталога не делает запросов к файлам; на втором проходе при неизменном `main` делает ровно **0** обращений (`verifyNoMoreInteractions(github)`).
    - `batchDirectoryListingFetchesOnlyPresentFilesAndCachesResult`: опрашивает каталог 1 раз, запрашивает по сети только присутствующий в списке файл `protocols-api`, не запрашивает отсутствующий `missing-api`; на 2-м проходе с тем же commitSha делает 0 обращений; на 3-м проходе с новым commitSha обновляет кэш.
    - `cacheInvalidationForcesRequery`: проверяет принудительную реинвалидацию по `invalidateCache`.
    - `probeAllReusesCacheAcrossLaunchesWithSameCommitSha`: проверяет, что повторный `probeAll` с тем же commitSha переиспользует кэш с 0 обращений к GitHub.
  - `ClientRuntimeObservabilityServiceTest`:
    - `boundaryTopology_maybeObserveIsNotTransactional`: рефлексивно заслоняет отсутствие аннотации `@Transactional` на `maybeObserve`.
  - `GitHubPullRequestServiceTest`:
    - `parseDirectoryFileNamesExtractsOnlyFiles`: проверяет фильтрацию только файлов из структуры JSON GitHub.
    - `parseDirectoryFileNamesReturnsEmptyForNullOrNonArray`: проверяет безопасность при пустом/некорректном ответе.
  - Регрессия: `ProductLaunchabilityServiceTest` (22/22), `ProductCapabilityServiceTest` (13/13), `ClientRuntimeObservabilityServiceTest` (29/29), `GitHubPullRequestServiceTest` (10/10), `FailureDemarcationLaw22Test` (5/5) — 79/79 green.

**Закрыто (Такт 26):** Предписание 8 (`FACTORY_MECHANISMS.md`, раздел XVI §8, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011 Perception failure) — автономный читатель вердикта решётки слоёв с дельта-записью в журнал дефектов:
- **Ликвидация глухоты конвейера к отказам слоёв (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011):**
  - До сих пор вердикт решётки (`VerdictReconciliation`) опрашивался только по HTTP через `VerdictController` или через выключенный `VerdictGate`. Отказы слоёв (например, 9 отказов ролей доктрины) были не слышны автономному циклу оркестрации.
  - Создан сервис `AutonomousVerdictObservationService` в пакете `com.eneik.production.services.verdict`.
  - Внедрён в `ContinuousOrchestrationService.continuousOrchestrate` на тике каждого активного проекта (`LogScope.project`) с изолированным try/catch.
  - Сохранён инвариант предписания 9: полномочия гейтов не расширялись, блокировок не добавлено — читатель сугубо наблюдательный (`observe` и запись в журнал).
- **Защита от зашумления журнала дефектов и искажения доверия:**
  - Реализован дельта-реестр активных отказов (`activeRefusals: ConcurrentHashMap<RefusalKey, String>`).
  - Запись в `DefectJournalService` производится **строго при смене состояния**: новый отказ либо изменение причины отказа по ключу `[projectId, layer, proposition]`.
  - Повторные тики с идентичным отказом производят ровно **0** записей в журнал (подавление дублирующего шума ~540 записей/час при 9 отказах доктрины на 60-секундном тике).
  - При переходе предложения обратно в `PERMIT` или `ABSTAIN` (или исчезновении) отказ снимается из реестра, гарантируя, что любая будущая регрессия будет зафиксирована заново как новое событие.
  - Воздержания (`ABSTAIN`) как эпистемический долг не трактуются как отказы и дефектов не порождают.
  - Поддерживается конфигурируемый каденс (`verdict.observation.cadence-ticks`, по умолчанию 1) с возможностью принудительного опроса (`observe(projectId, true)`).
- **Заслоняющие тесты:**
  - `AutonomousVerdictObservationServiceTest`:
    - `falsificationHarness_consecutiveTicksWithSameRefusalProducesSingleDefectRecord`: слой отказывает два тика подряд — ровно 1 запись; причина сменилась — ровно 2-я запись; слой переходит в PERMIT — 0 записей и очистка реестра; повторный отказ — 3-я запись.
    - `abstentionProducesZeroDefectRecords`: воздержания не порождают записей в журнале.
    - `multipleLayersAndPropositionsTrackedIndependently`: независимое ведение реестров нескольких слоёв и предложений.
    - `exceptionInReconciliationHandledGracefully`: сбой сведений изолируется и не роняет поток.
    - `cadenceThrottlingSkipsIntermediateTicksWhenConfigured`: пропуск промежуточных тиков при каденсе > 1 и обход через force.
    - `sanitizeDefectTypeHandlesHyphensAndSpaces`: нормализация типов дефектов (`DOCTRINE_REFUSAL`, `SIX_SIGMA_REFUSAL`).
  - `ContinuousOrchestrationServiceTest`:
    - `continuousOrchestrateDelegatesVerdictObservationForActiveProjects`: проверяет вызов `observe(projectId)` на тике для каждого активного проекта.
  - Регрессия: `AutonomousVerdictObservationServiceTest` (6/6), `ContinuousOrchestrationServiceTest` (7/7), `AcceptanceVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8), `RuntimeVerdictLayerTest` (6/6), `VerdictGateTest` (8/8), `VerdictReconciliationTest` (8/8) — 50/50 green.

**Закрыто (Такт 27):** Разрешение несовпадения смысла и референта в именах контрактов OpenAPI (`ProductCapabilityService`, раздел XVI §25 `FACTORY_MECHANISMS.md`, `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009 Substitution failure, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013 Runtime drift):
- **Диагностика:** В реальном клоне продукта (`test-fiftieth/docs/contracts`) 17 контрактов названы по предметным доменам (`StrainManagement.openapi.yaml`, `EmployeeDossier.openapi.yaml`, `MoodleSso.openapi.yaml`, `Auth.openapi.yaml` и т.д.). Ранее `ProductCapabilityService` пытался угадать имя файла через kebab-case заголовка внутренней фичи БД (`title.toLowerCase().replace(' ', '-') + ".openapi.yaml"` $\rightarrow$ `strain-management-api.openapi.yaml`). Из-за несовпадения внутренних догадок фабрики и физической реальности продукта сопоставление давало 0 контрактов, и `/api/projects/<id>/product-value` отчитывался `declaredCapabilities = 0, workingCapabilities = 0`.
- **Устранение расхождения («своё изделие — не сведения о предмете»):**
  - `ProductCapabilityService.declaredCapabilities`: при обнаружении файлов в каталоге `docs/contracts` (через `gitHubPullRequestService.listDirectoryFiles`) сервис больше не угадывает имена по фичам, а напрямую считывает и разбирает все валидные OpenAPI-контракты (`.openapi.yaml`, `.openapi.yml`, `.yaml`, `.yml`), отсортированные по алфавиту для строгой детерминированности.
  - Вспомогательный метод `isContractFile` валидирует допустимые расширения контрактов, отсекая нерелевантные файлы (`README.md`, служебные файлы).
  - Сохранена обратная совместимость (fallback) при `listDirectoryFiles == Optional.empty()` для легаси-тестов со старыми моками.
  - Кэширование по branch и commitSha (`INUS_FACTOR_CHECK` / D007) сохранено в полном объёме: второй проход с неизменным commitSha по-прежнему даёт ровно 0 сетевых обращений к GitHub.
- **Заслоняющие тесты:**
  - `ProductCapabilityServiceTest`:
    - `falsificationHarness_allContractsInDirectoryParsedWithoutFeatureTitleGuessing`: фальсифицирующий заслон — проверяет, что контракты с именами доменов (`StrainManagement.openapi.yaml`, `EmployeeDossier.openapi.yaml`) парсятся независимо от несовпадающих заголовков фич в БД (`Strain Management API`), `README.md` не запрашивается по сети, `declaredCapabilities` строго больше нуля (3 маршрута) с точными источниками.
  - Регрессия: `ProductCapabilityServiceTest` (14/14), `BetaPosteriorTest` (9/9), `ClientRuntimeObservabilityServiceTest` (29/29), `ProductLaunchabilityServiceTest` (22/22), `RuntimeHealthShiftDetectorTest` (9/9) — 83/83 green.

**Закрыто (Такт 28):** Ликвидация псевдо-заглушечной ветки догадок в `ProductCapabilityService`, очистка комментариев, каденс вердиктов (`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D009, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011):
- **Устранение боевого fallback на догадки по фичам (`ProductCapabilityService`):**
  - Из `declaredCapabilities` полностью ликвидирована ветка `else`, пытавшаяся при `listDirectoryFiles == Optional.empty()` угадывать имена контрактов по kebab-case заголовка фич БД.
  - Инвариант: «не удалось прочитать каталог» (сбой GitHub, API, сети) $\rightarrow$ возможности объявляются *неопределёнными* (пустой список), а не реконструируются через фальшивые догадки фабрики.
  - Заслоняющий тест `falsificationHarness_directoryReadFailureYieldsEmptyCapabilitiesWithZeroGuessedRequests`: при сбое чтения каталога возвращается пустой список возможностей и делается ровно **0** сетевых вызовов `fetchFileContent` (ни один kebab-путь не запрашивается).
- **Очистка устаревших комментариев от расхождения смысла и имени (D009):**
  - В Javadoc класса `ProductCapabilityService` (:35) и метода `declaredCapabilities` (:117–129) удалены строки о том, что заголовок фичи строго определяет путь контракта («the feature title determines the path exactly»). Зафиксировано прямое выведение возможностей из контрактов каталога `docs/contracts/`.
- **Настройка каденса и документирование жизненного цикла вердиктов (Предписание 8, D011):**
  - В `AutonomousVerdictObservationService` и `application.properties` каденс по умолчанию установлен в `5` тиков (5 минут / 300 секунд, свойство `verdict.observation.cadence-ticks=5`). Это устраняет ежеминутный холостой прогон сведения (1,17 с и 2 сетевых зонда инфраструктуры), сохраняя оперативную телеметрию в пределах 5 минут.
  - Задокументирован жизненный цикл in-memory реестра `activeRefusals`: очистка при рестарте процесса обеспечивает повторное однократное подтверждение активных базовых отказов (9 отказов доктрины) в `DefectJournalService` при загрузке, после чего действует строгая дельта-запись.
- **Заслоняющие тесты:**
  - `ProductCapabilityServiceTest`: 15/15 green.
  - `AutonomousVerdictObservationServiceTest`: 6/6 green.
  - `ContinuousOrchestrationServiceTest`: 7/7 green.
  - `ClientRuntimeObservabilityServiceTest`: 29/29 green.
  - Регрессия: 57/57 green.

**Закрыто (Такт 29):** Предписание 23 (`FACTORY_MECHANISMS.md`, раздел XVI §23) — единый жизненный цикл аккаунтов, институциональный факт с аудитом, разделение родов (аудит не дефект), биномиальный деривативный порог монополии и заслон против подмены «выключен» на «ёмкость»:
- **Реестр институциональных фактов и ликвидация категориальной ошибки (`INSTITUTIONAL_FACT_REGISTER` / D007, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
  - Любая мутация флага `enabled` или статуса аккаунта через `PATCH /api/accounts/{id}` в `AccountController` порождает институциональную запись аудита с категорией `INSTITUTIONAL_AUDIT`, уровнем `INFO` и указанием правила (`ACCOUNT_DECOMMISSION_RULE`, `ACCOUNT_LIFECYCLE_ENABLEMENT_RULE`, `ACCOUNT_OPERATIONAL_STATUS_RULE`), старого и нового значений, причины и времени.
  - В `DefectJournalService.getDefectsInWindow` и `OperationalTruthService.build`: не-дефектные категории аудита (`INSTITUTIONAL_AUDIT`, `ACCOUNT_LIFECYCLE_AUDIT`, `AUDIT_TRAIL`) фильтруются и исключаются из выборок дефектов. Административный аудит больше не превращается в ложный дефект, не загрязняет предложения Кайдзен (Muda, BufferTuning) и не штрафует оценку доверия проекта.
- **Единый жизненный цикл и разрешение противоречий через сущность (`ACTUAL_OBJECT_REGISTER` / D002, `TRUTH_STATUS_TABLE` / D012):**
  - Устранено двоевластие между `status=decommissioned` и `enabled=false`.
  - В `AccountEntity`: перевод статуса в `decommissioned` атомарно сбрасывает `enabled = false`; попытка установить `enabled = true` на списанном аккаунте отвергается с `IllegalStateException` (в контроллере — HTTP 400 с явным объяснением).
  - Массовый JPQL `UPDATE` мимо сущности ликвидирован. В `AccountHealthService.recoverEligibleAccounts`: нормализация исторических противоречивых строк выполняется через сущность (`findByStatusAndEnabledTrue(decommissioned)`), переводит `enabled=false` с фиксацией правила `ACCOUNT_LIFECYCLE_NORMALIZATION_RULE` и записью в аудит на каждый переход. На последующих тактах при отсутствии противоречий выполняется строго 0 обращений и 0 записей.
- **Отказ от необратимого авто-списания и предложение к возврату (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010):**
  - В `AccountHealthService.recoverEligibleAccounts`: автоматическое необратимое списание исключено. Аккаунты, выключенные более 24 часов (`statusChangedAt`), логируются как «предлагается к возврату» без самовольного перевода в `decommissioned`.
  - При `candidates.isEmpty()` и наличии выключенных аккаунтов выводится явное эпистемическое сообщение: `No eligible accounts found for recovery (N operational accounts currently disabled)`.
- **Порог монополии выведен из распределения сессий (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010):**
  - Порог монополии $T$ рассчитывается из наблюдаемого биномиального распределения сессий окна $S$ при равномерной ротации $p_0 = 1/N$: $T = p_0 + 3\sigma = 1/N + 3\sqrt{\frac{p_0(1-p_0)}{S}}$.
  - При отсутствии различимости ($S < N$ или $N \le 1$) порог возвращает предел $1.0$.
  - На живых данных Hetzner ($N=7, S=38$) порог равен ~31.3%, что строго и надёжно фальсифицирует случай концентрации 66% (25 из 38) на одном аккаунте `eneikdru`.
  - **Дедупликация записи монополии:** внедрён реестр активных монополий `activeMonopolies`. Повторные тики с той же монополией подавляют повторную запись в журнал (урок предписания 8); запись производится только при появлении или существенном изменении доли.
- **Заслон фальсификации против подмены понятий («выключен» вместо «ёмкость»):**
  - В `ProjectFlowService.dispatchToGeneralPool`: если пул пуст и все операционные аккаунты выключены (`disabledAccounts >= liveAccounts`), статус задачи выставляется как `"All operational Jules accounts are disabled (N disabled)..."`.
  - Заслон строго гарантирует: сообщение лога и статус задачи **не содержат** слова «capacity» / «ёмкость».
  - В `BottleneckDetectionService` выключенные аккаунты выделены в отдельную метрику `disabledAccounts` и явно перечисляются в причине узкого места, не маскируясь под нехватку слотов.
  - В `ContinuousOrchestrationService` затор по причине отключения всех аккаунтов отделен от легитимной занятости задачами.
- **Заслоняющие тесты (56/56 green в контейнере Maven):**
  - `AccountLifecycleInvariantTest` (4/4): аудит институциональных фактов с `INSTITUTIONAL_AUDIT` и `INFO`, `decommissioned` влечет `enabled=false`, отказ включения списанного (400), guard на сущности.
  - `DefectJournalServiceTest` (3/3): проверка исключения записей `INSTITUTIONAL_AUDIT` из выборок дефектов окна Кайдзен и проектов.
  - `OperationalTruthServiceTest` (17/17): включая `institutionalAuditRecordsDoNotPenalizeTrustOrCountAsDefects`.
  - `AccountHealthServiceTest` (25/25): Church derived cutoff через биномиальную дисперсию (3-sigma), дедупликация записи монополии между последовательными обходами, нормализация через сущность с записью институционального аудита и однократным выполнением, видимость выключенных при нуле кандидатов, предложение к возврату >24ч.
  - `ProjectFlowServiceLaw1JulesDispatchTest` (4/4): включая `falsificationHarness_allAccountsDisabledReportsDisabledStatusWithoutCapacityWord`.
  - `BottleneckDetectionServiceTest` (3/3): разделение выключенных и занятых аккаунтов.

**Закрыто (Такт 30):** Предписание 9 (`FACTORY_MECHANISMS.md`, раздел XVI §9) — запреты как исполняемый код (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006), устранение поглощающего состояния сбоев в доктрине (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002), операционный пул монополии (Предписание 23):
- **Исполнимые запреты как код (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, Предписание 9):**
  - В `VerdictGate` реализован механизм адресных, объяснимых запретов вместо слепого глобального блокирования конвейера.
  - Введена запись `ActionProhibition(boolean prohibited, String layer, String proposition, String ruleName, String explanation)` и метод `evaluateActionProhibition(ProjectEntity project, String action)`.
  - Сформулированы и заслонены конкретные правила запрета:
    1. `INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION`: слой `infrastructure` при отказе (нездоровая БД фабрики или недоступный runtime launcher) блокирует `DISPATCH_QUEUED_TASKS` с явным указанием слоя, предложения и метрики (например, `bloat > threshold`).
    2. `DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION`: слой `doctrine` при наличии реального невосстановленного сбоя блокирует `DISPATCH_QUEUED_TASKS` и `EXPAND_FEATURE` с указанием конкретной незакрытой причины.
  - Запреты строго **не поглощающие и обратимые**: при возврате слоя в `PERMIT` запрет немедленно и автоматически снимается без ручных вмешательств.
  - Интеграция в поток: `OperationalPolicyService.authorize` вызывает `VerdictGate.evaluateActionProhibition` для целевого проекта, добавляя `ruleName` в список `blockers` и формируя объяснимый отказ.
  - Сохранена строгая изоляция: флаг `verdict_gating_enabled` по умолчанию выключен (`false`), гейт стоит в стороне для неактивированных проектов.
- **Устранение поглощающего состояния сбоев в доктрине (`EmsMetricsService`, D012, D002):**
  - Диагностика: 7 из 9 ролей доктрины перманентно находились в `refuses` с причиной `"Owner-role execution has failed work"`, так как метрика считывала все 45 исторических неудачных попыток задач за всё время жизни базы, даже если работа была давно успешно переделана и принята.
  - Внедрён предикат `isUnrecoveredFailure(task, allTasks, wishlistById)`: исторический сбой признаётся закрытым/восстановленным, если пройден Quality Gate, задача попала в `main`, либо существует преемник с тем же `contentKey` / `sourceWishlistId` / `featureId` и ролью в состоянии `done-like`, либо связанный бриф был отклонён (`dismissed`).
  - `ownerOpen` теперь учитывает только реально открытую работу (`isActiveLike(task) || task.getStatus() == TaskStatus.queued`), исключая терминальные строки.
  - `openDefectWork` больше не считает исторические сбои, если они уже восстановлены.
- **Уточнение знаменателя монополии (Предписание 23 follow-up):**
  - В `AccountRepository` добавлен запрос `countOperationalAccounts()` (`enabled = true AND status NOT IN ('decommissioned', 'offline', 'daily_limited', 'api_blocked')`).
  - В `AccountHealthService.checkMonopoly` знаменатель пула $N$ берётся из операционных доступных аккаунтов, предотвращая искусственное занижение порога монополии из-за временно заблокированных или списанных аккаунтов.
- **Заслоняющие тесты (59/59 green в контейнере Maven):**
  - `VerdictGateTest` (12/12):
    - `infrastructureRefusalProhibitsTaskDispatchWithNamedRuleAndReason`: отказ БД инфраструктуры блокирует раздачу с именем правила `INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION`.
    - `infrastructureRecoveryLiftsProhibitionAndPermitsDispatch`: восстановление здоровья БД немедленно снимает запрет (не-поглощаемость).
    - `doctrineUnrecoveredFailureProhibitsActionAndLiftsOnRecovery`: невосстановленный сбой доктрины блокирует действие и снимается при удовлетворении.
    - `prohibitionStandsAsideWhenGatingDisabledOrDifferentProject`: гейт стоит в стороне при выключенном флаге или нецелевом проекте.
  - `OperationalPolicyServiceTest` (16/16):
    - `verdictGateProhibitionDeniesDispatchWithRuleNameAndExplanation`: политика проверяет запреты гейта, фиксирует правило в `blockers` и формирует отказ; разрешает при отсутствии запрета.
  - `EmsMetricsServiceTest` (6/6):
    - `unrecoveredFailedTaskRefusesDoctrineReadinessWithActionableObjection`: невосстановленный сбой переводит доктрину в `refuses`.
    - `recoveredFailedTaskDoesNotRefuseDoctrineReadiness`: исторический сбой с успешным преемником переводит роль в `satisfied`.
    - `closedDefectWorkDoesNotTriggerDefectWorkObjection`: закрытая дефектная работа не препятствует удовлетворению.
  - `AccountHealthServiceTest` (25/25): верификация расчёта порога монополии и жизненного цикла.

**Закрыто (Такт 31):** Доводка Предписания 9 (устранение самоблокировки через исключение восстановительной работы, машиночитаемые коды причин) и реализация Предписания 10 (`FACTORY_MECHANISMS.md`, раздел XVI §10 — пакетное продвижение ступеней рычагов TOC strictly по свежим наблюдениям):
- **Устранение самоблокировки запрета восстановления (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006):**
  - Ранее правило `DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION` блокировало `DISPATCH_QUEUED_TASKS`. Но восстановительные задачи (`ems_defect_work`, `retryCount > 0`, `is_recovery`) раздаются через этот же поток `DISPATCH_QUEUED_TASKS`, что запирало их раздачу и делало запрет самоблокирующимся.
  - В `ActionProhibition` введен флаг `exemptsRecoveryWork` и фабричный метод `deniedWithRecoveryExemption`.
  - Метод `allowsTask(boolean isRecoveryTask)`: если запрет имеет `exemptsRecoveryWork == true`, восстановительные задачи разрешаются к раздаче, в то время как обычные продуктовые задачи и расширение фич блокируются.
  - В `VerdictGate` добавлен метод `evaluateTaskProhibition(ProjectEntity, TaskEntity)` с детекцией `isRecoveryTask(task)` по `retryCount > 0`, `ems_defect_work` или payload-атрибутам `is_recovery`.
  - В `OperationalPolicyService.authorize`: если запрет имеет `exemptsRecoveryWork == true`, действие `DISPATCH_QUEUED_TASKS` на уровне проекта пропускается к выполнению раздаточного цикла, а селективная фильтрация задач осуществляется в `ProjectFlowService.dispatchQueuedTasks` через `verdictGate.evaluateTaskProhibition`.
- **Ликвидация классификации по подстрокам текста (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
  - Введена строгая машиночитаемая типизация причин отказов (`reasonCode` в `Judgement`, `objectionCode` в `RoleDoctrineVerdict` и детерминированный `topObjectionCode` в `EmsMetricsService`: `UNRECOVERED_FAILED_WORK`, `BLOCKED_WORK`, `OPEN_DEFECT_WORK` и др.).
  - Запрет `DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION` в `VerdictGate` теперь сопоставляется строго по коду `"UNRECOVERED_FAILED_WORK".equals(j.reasonCode())`, а не по хрупкому совпадению подстрок `contains("unrecovered failed work")`.
- **Пакетное продвижение ступеней рычагов по накопленным свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, Предписание 10):**
  - Диагностика: на живой фабрике `T1_TOC_SUBORDINATION` был поднят в 11:45 (`observe_only -> warn_only`) и в 13:45 (`warn_only -> soft_gate`) на тех же самых 702 наблюдениях и 80.05% согласия, потому что `evaluateOne` раз в 2 часа брал окно 14 дней без учёта момента прошлого подъёма (`promotedAt` сохранялся, но не читался).
  - В `LeverPromotionService.evaluateOne`: окно анализа ограничено снизу `evidenceSince = (state.getPromotedAt() != null && state.getPromotedAt().isAfter(since)) ? state.getPromotedAt() : since`.
  - Каждая последующая ступень лестницы доверия требует отдельного свежего пакета из $\ge 20$ наблюдений, накопленных строго *после* предыдущего перехода.
  - Сохранено свойство асимметричности доверия: немедленное понижение при первом же расхождении (`agreementRate < threshold`).
- **Заслоняющие тесты (114/114 green в изолированном контейнере Maven):**
  - `VerdictGateTest` (14/14):
    - `doctrineRefusalUsesReasonCodeNotSubstring`: фальсифицирует матчинг запрета строго по `reasonCode`; произвольный текст без валидного кода отвергается.
    - `doctrineUnrecoveredFailureProhibitionExemptsRecoveryWorkAndPermitsItsDispatch`: запрет блокирует `DISPATCH_QUEUED_TASKS` для обычных задач, но восстановительная задача (`retryCount > 0`, payload `ems_defect_work`) разрешена к раздаче.
  - `OperationalPolicyServiceTest` (17/17):
    - `verdictGateProhibitionWithRecoveryExemptionAllowsDispatchCycle`: проектная политика разрешает шаг раздачи для запуска цикла при наличии льготы на восстановление.
  - `LeverPromotionServiceTest` (13/13):
    - `twoEvaluationsInARowWithoutNewObservationsDoNotPromoteTwice`: две последовательные оценки без новых наблюдений дают ровно 1 подъём, второй подъём отвергается из-за отсутствия свежих свидетельств после `promotedAt`.
  - `ProjectFlowServiceTest` (19/19): подтверждение корректной раздачи и фильтрации задач.
  - Регрессия: `EmsMetricsServiceTest` (6/6), `DoctrineVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8), `AccountHealthServiceTest` (25/25), `AutonomousVerdictObservationServiceTest` (6/6).

**Закрыто (Такт 32):** Снятие остаточного строкового условия в Предписании 9 и полная реализация Предписания 11 (`SystemStatusService` / `SystemProgressTracker` — `ANTI_MIRROR_TELEMETRY` / D013, `LYUDVIG_VITGENSHTEYN_14`):
- **Снятие строкового остатка в `VerdictGate.java` (Предписание 9 finish, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
  - Удалено запасное условие `|| (j.reason() != null && j.reason().contains("unrecovered failed work"))`.
  - Запрет `DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION` теперь срабатывает строго и исключительно по типизированному коду `"UNRECOVERED_FAILED_WORK".equals(j.reasonCode())`.
  - В `VerdictGateTest` добавлен заслон `doctrineRefusalUsesReasonCodeNotSubstring`: совпадение подстроки в тексте отказа без `reasonCode` явно не активирует запрет.
- **Фиксация статуса `T1_TOC_SUBORDINATION` на сервере (Предписание 10 follow-up):**
  - Подтверждено живым логом: в 15:45:36 T1 был поднят `soft_gate -> hard_gate` на старом коде (на тех же 702 наблюдениях).
  - При следующей сборке на Hetzner рекомендуется единовременный откат T1 до `warn_only` (`UPDATE lever_states SET state='warn_only', promoted_at=NOW() WHERE lever_id='T1_TOC_SUBORDINATION'`), чтобы он набрал честные 3 ступени на независимых свежих пакетах по $\ge 20$ наблюдений, как того требует закон `ELVIN_GOLDMAN_21`.
- **Ликвидация ложного прогресса от служебного саморазговора (`ANTI_MIRROR_TELEMETRY` / D013, `LYUDVIG_VITGENSHTEYN_14`, Предписание 11):**
  - **Дефект 1 (фабричный рассказ о себе как прогресс потока):**
    - `markSessionProgress(session)` отвязан от `systemProgressTracker.recordProgress()`. Он обновляет только локальный `session.setLastProgressAt(Instant.now())` для защиты от тайм-аута сеанса.
    - Вызовы `recordProgress()` вычищены из всех внутренних служебных циклов фабрики: компиляция вишлистов (`completeWishlistCompilation`, `completePersistentCompilerCycle`), философские и фальсификационные аудиты (`completePersistentPhilosophicalAuditCycle`, `completeFalsificationAudit`, `completePhilosophicalAudit`), закрытие ретраев ревьюера (`clearReviewFallbackNullVerdictRetries`), опросы активности и повторные сообщения Jules.
    - Прогресс теперь продвигается **исключительно внешними проверяемыми результатами**:
      1. Успешная внешняя раздача задачи исполнителю Jules (`JulesDispatchService.dispatch`).
      2. Открытие PR исполнителем (`handlePrOpenedWorkflowClaimed` для `TaskStatus.claimed`).
      3. Обнаружение реального PR на GitHub при терминальной сверке (`handleTerminalActivityWithoutDeliverable`).
      4. Обнаружение смерженного PR на GitHub при аварийной сверке (`hasNewProgressOnGitHub`).
      5. Автоматическое открытие аварийного PR на GitHub из оставленной ветки (`openRecoveryPullRequest`).
      6. Успешное слияние PR на GitHub (`AutoMergeService.recordSuccessfulMerge`).
      7. Переход сессии в статус `pr_opened` по факту появления PR на GitHub (`AutoMergeService.trackPullRequests`).
  - **Дефект 2 (ложный «ок» из небытия при старте JVM):**
    - В `SystemProgressTracker` `lastProgressAt` теперь инициализируется строго `null` (не подменяется моментом старта JVM). Добавлены методы `hasProgress()`, `startedAt()`, `Optional<Duration> sinceLastProgress()`.
    - В `ContinuousOrchestrationService.checkForSystemStall` при `lastProgressAt == null`: статус устанавливается в `"undetermined"` (а при истечении окна `stallThresholdMinutes` от `startedAt` при наличии незанятых мощностей и открытых задач — честный `"stalled"`, а не ложный `"ok"`).
    - В `SystemStatusService.systemHealth`: безопасная обработка `null` без NPE (`lastProgressAt: null`, `minutesSinceProgress: null`), статус при отсутствии реального выхода — `"undetermined"`.
    - Статус `"undetermined"` зарегистрирован как безопасный (non-blocking) в `SystemStatusService.operationalBlockers`, `FlowSpineService.isTrustBlockingSystemStatus` и `OperationalTruthService.isTrustBlockingSystemStatus`, что предотвращает циклическую самоблокировку диспетчера на холодном старте.
- **Заслоняющие тесты (211/211 green в изолированном контейнере Maven):**
  - `SystemProgressTrackerTest` (3/3): холодный старт без прогресса, запись прогресса меткой времени, фиксация окна.
  - `ContinuousOrchestrationServiceTest` (6/6):
    - `startupWithoutDeliverablesDoesNotReportOk`: холодный старт с задачами и свободными мощностями сохраняет статус `undetermined`, не допуская `ok`.
    - `windowElapsedWithoutDeliverableReportsStalledEvenIfAuditsRan`: истечение окна без внешних результатов (даже при активных аудитах) выставляет `stalled`, не допуская `ok`.
    - `genuineDeliverableReportsOkWithinWindow`: только реальный внешний результат переводит статус в `ok`.
  - `SystemStatusServiceTest` (19/19):
    - `systemHealthReportsUndeterminedWhenNoProgressRecordedEvenIfSettingsSayOk`: отсутствие реального выхода переопределяет остаточный `ok` в настройках в `undetermined`.
    - `systemHealthReportsOkWhenGenuineProgressRecorded`: реальный прогресс возвращает `ok`.
    - `operationalBlockersDoesNotTreatUndeterminedAsBlocker`: статус `undetermined` не парализует поток блокировщиками.
  - `JulesDispatchServiceTest` (100/100):
    - `progressTrackerRecordsProgressOnSuccessfulDispatch`: успешная раздача продвигает трекер.
    - `progressTrackerRecordsProgressWhenImplementerOpensPr`: открытие PR исполнителем продвигает трекер.
    - `progressTrackerDoesNotRecordProgressOnReviewerCompletion`: завершение ревьюера не продвигает трекер.
  - `VerdictGateTest` (14/14): строгая проверка типизированного `reasonCode`.
  - Регрессионный прогон: `AutoMergeServiceTest` (23/23), `FlowSpineServiceTest` (25/25), `OperationalTruthServiceTest` (17/17).

**Закрыто (Такт 33):** Фиксация архитектурных границ Предписаний 11 и 12, полная реализация Предписания 14 (`AccountHealthService` / `AccountRepository` — откат не знает периода пополнения, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, закон 9):
- **Фиксация архитектурной границы Предписания 11 (`openRecoveryPullRequest` :5628):**
  - Граница названа в явном виде: метод `openRecoveryPullRequest` открывается самой фабрикой из оставленной ветки исполнителя. Засчитывается как реальный прогресс (`recordProgress()`), поскольку ветка содержит фактический внешний код и коммиты Jules, которые иначе терялись бы в подвешенном состоянии.
- **Фиксация архитектурной границы Предписания 12 (`BottleneckAwarePriorityService`, закон 11):**
  - В соответствии с указанием раздела XVI §12 граница зафиксирована без изменения кода: `BottleneckAwarePriorityService` строго **ранжирует** очередь работ (вычисляет приоритет задач по фазам и ролям), в то время как решение о подчинении или простаивании принимает исключительно `TocSubordinationLever`.
  - Опровержение не выполняется: в классе нет ни одного решения `idle`, `deny` или `skip` (0 вхождений).
- **Реализация Предписания 14 (`AccountHealthService` / `AccountRepository` — откат и период пополнения, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, закон 9):
  - **Замер:** экспоненциальный откат аккаунтов (30 -> 60 -> 120 -> 240 -> 480 минут) удваивал паузу вслепую к внешним границам пополнения квот (например, суточному сбросу в 00:00 UTC). В результате после обнуления внешнего лимита аккаунт простаивал лишние часы, ожидая истечения собственного удвоенного таймера.
  - **Делать:**
    - Следующая проба назначается по **раннему** из двух: собственный откат аккаунта (`ownBackoff`) и начало нового периода пополнения (`nextReplenishmentPeriodStart`).
    - Период пополнения выводится из наблюдений по аналогии с `BetaPosterior` (`estimateReplenishmentPeriod`): рассчитывается медианный интервал между последовательными событиями восстановления `BUDGET_RECOVERY_DEFECT_TYPE` при наличии $\ge 5$ наблюдений; при нехватке выборки используется неинформативное априорное распределение (24 часа, якорь — полночь UTC).
    - В `AccountHealthService`: добавлены методы `isExternalBudgetExhaustion(account)` (распознаёт статус `daily_limited` и отказы 429/quota/rate limit), `estimateReplenishmentPeriod(account)`, `nextReplenishmentPeriodStart(account, fromInstant)`, `calculateNextPeriodBoundary(anchor, fromInstant, period)` и `computeNextProbeInstant(account, now)`.
    - В `AccountRepository`: добавлен атомарный метод `resetSingleAccountFromDailyLimited(UUID id, Instant now)` (сброс в `idle`, обнуление `sessionsDispatchedToday`, обновление `statusChangedAt = now`).
    - В `recoverEligibleAccounts`: кандидаты из `daily_limited` и `api_blocked` проверяются против `computeNextProbeInstant(account, current)` и восстанавливаются сразу по наступлению границы периода пополнения без ожидания удвоения.
  - **Заслон:** после отказа, классифицированного как исчерпание внешнего бюджета, следующая проба назначена не позже начала нового периода пополнения.
  - **Опровержение:** сдвиг времени за границу периода (`now = 00:01 UTC`) восстанавливает аккаунт немедленно, не дожидаясь 8-часового удвоения (проверено тестом `refutation_timeShiftPastPeriodBoundary_recoversWithoutWaitingForDoubling`).
  - **Контроль:** отказ, не являющийся исчерпанием бюджета (`PRECONDITION_BLOCKED`), не восстанавливается на границе периода и честно выдерживает полный собственный кулдаун (проверено тестом `control_nonBudgetRefusal_waitsForFullCooldown`).
- **Заслоняющие тесты (62/62 green в изолированном контейнере Maven):**
  - `AccountHealthServiceTest` (30/30):
    - `calculateNextPeriodBoundary_pureMath`: проверка граничных вычислений периодов (24h, 12h, фазирование относительно якоря).
    - `computeNextProbeInstant_externalBudgetExhaustion_scheduledNoLaterThanPeriodStart`: следующая проба назначена строго не позже начала периода.
    - `refutation_timeShiftPastPeriodBoundary_recoversWithoutWaitingForDoubling`: фальсифицирующее опровержение — восстановление за границей периода без ожидания удвоения.
    - `control_nonBudgetRefusal_waitsForFullCooldown`: контрольная группа — отказ инфраструктуры ждет полный откат.
    - `estimateReplenishmentPeriod_dataDriven_derivesFromObservedHistory`: эмпирический вывод периода 12ч из $\ge 5$ наблюдений и 24ч из априорного распределения.
  - `AccountHealthServiceLaw14Test` (7/7): сохранение инвариантов Popper/Gärdenfors по динамическому расчету емкости.
  - Регрессионный прогон: `ContinuousOrchestrationServiceTest` (10/10), `WatermarkMonotonicityLaw10Test` (6/6), `DispatchRefusalObservabilityLaw8Law12Test` (4/4), `AccountSelectionFairnessTest` (5/5).

**В работе дальше:**
- Синхронизация с Клодом по приёмке Предписания 14.
- Следующий пункт по разделу XVI `docs/FACTORY_MECHANISMS.md`: Предписание 15 («Исчерпание попыток становится вердиктом о требовании · `INSTITUTIONAL_FACT_REGISTER` (D007), закон 12»).

**Закрыто (Такт 34):** Предписание 15 (`ClaimService` / `ProjectFlowService` — исчерпание попыток становится вердиктом о требовании · `INSTITUTIONAL_FACT_REGISTER` (D007), закон 12) + доработка Предписания 14 по совету Клода (`RELIABILITY_CHAIN` / D010):
- **Доработка Предписания 14 (`AccountHealthService` / `DefectJournalService` по совету Клода):**
  1. Категориальный сбой (D002): записи восстановления аккаунтов (`ACCOUNT_BUDGET_RECOVERY`) сохраняются с категорией `"ACCOUNT_RECOVERY"`, добавленной в `NON_DEFECT_AUDIT_CATEGORIES` сервиса `DefectJournalService`. Доверие и аудит больше не штрафуют конвейер за факт восстановления мощностей.
  2. Ликвидация смешивания рядов (D010): удалены pooled-запросы по всей таблице; расчёт медианного периода и опорного якоря ведётся строго для конкретного аккаунта (`account.getName()`).
  3. Неинформативное априорное распределение (24h, полночь UTC) явно документировано в коде как априорное допущение при нехватке $\ge 5$ наблюдений.
- **Реализация Предписания 15 (`ClaimService` / `ProjectFlowService`, закон 12, `INSTITUTIONAL_FACT_REGISTER` / D007):**
  1. **Счёт не тронут:** метод `refusedSessionCreations` остаётся строгим счётчиком фактически потраченной ёмкости (отказанных сессий).
  2. **Регистрация состава:** `retireForExhaustedDispatchBudget` анализирует закрытия сессий и разделяет отказы на внешние (исчерпание квот Jules, API лимиты, неспецифицированные предусловия) и невнешние (`jules_request_rejected`). Записывает факт в журнал дефектов под правилом `DISPATCH_BUDGET_EXHAUSTION_COMPOSITION` в категории `INSTITUTIONAL_AUDIT` (D007 / D012).
  3. **Возобновляемое состояние вместо поглощающего:** требование, чей бюджет исчерпан одними внешними отказами, переходит в статус `TaskStatus.blocked` с пометкой `UNTESTED_WITHIN_CAPACITY` (требование не оценивалось).
  4. **Защита от поглощающего вердикта `failed`:** в `ProjectFlowService.createRecoveryWishlistForOrphanedBlockedTasks` установлен заслон `ClaimService.isUntestedWithinCapacity(task)`: задача не переводится в `TaskStatus.failed`, сохраняя нетерминальный возобновляемый статус `blocked`.
- **Заслоняющие тесты (137/137 green в изолированном контейнере Maven):**
  - `DispatchAttemptBudgetTest` (9/9):
    - `exhaustionWithOnlyExternalRefusals_markedUntestedWithinCapacityAndDoesNotReceiveTerminalFailed`: задача при одних внешних отказах получает `UNTESTED_WITHIN_CAPACITY`, фиксирует состав, никогда не переходит в `failed`, регистрирует институциональный факт в журнале.
    - `exhaustionWithNonExternalRefusal_markedDispatchBudgetExhaustedWithoutUntestedTag`: при наличии отказа запроса (`jules_request_rejected`) задача помечается `DISPATCH_BUDGET_EXHAUSTED` с точным составом и не получает статус `UNTESTED_WITHIN_CAPACITY`.
    - `attemptCountIsFaithfulAndNotDampened`: счётчик попыток честен и неизменен.
  - `ProjectFlowServiceTest` (39/39):
    - `untestedWithinCapacityTask_isPreservedInBlockedAndNeverRetiredToFailed`: фальсифицирующий заслон — чистильщик блокировок сохраняет задачу в `blocked` и никогда не выставляет `failed`.
    - `nonUntestedBlockedTask_isRetiredToFailedWhenOrphaned`: контрольное опровержение — обычная зависшая задача без метки `UNTESTED_WITHIN_CAPACITY` переводится в `failed`.
  - `AccountHealthServiceTest` (30/30) и `AccountHealthServiceLaw14Test` (7/7).
  - Регрессионный пакет: `ContinuousOrchestrationServiceTest` (10/10), `VerdictGateTest` (14/14), `ReviewAdmissionLaw16Test` (10/10), `WatermarkMonotonicityLaw10Test` (6/6), `DispatchRefusalObservabilityLaw8Law12Test` (4/4), `ClaimServiceRaceGuardTest` (5/5), `UndeliveredReviewTaskExitTest` (3/3).

**В работе дальше:**
- Следующий пункт по разделу XVI `docs/FACTORY_MECHANISMS.md`: Предписание 16 («Круг самозаказа · `DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` (D002) · закон 3, ограничение области находки»).

**Закрыто (Такт 35):** Полная реализация и доводка Предписания 15 (`ClaimService` / `ProjectFlowService` / `AccountHealthService` / `ContinuousOrchestrationService`, закон 12, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `INSTITUTIONAL_FACT_REGISTER` / D007) и Предписания 16 (`DeliveryRealityProducerService`, закон 3, `DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` / D002):
- **Предписание 15 (трёхзначная логика Белнапа и путь разрешения):**
  1. **Трёхзначная логика отказов типом:** введён enum `DispatchRefusalCategory` (`EXTERNAL_CAPACITY`, `NON_EXTERNAL_REJECTION`, `UNATTRIBUTED_REFUSAL`). Пустые, null и неспецифицированные причины (`jules_precondition_unspecified` и др.) классифицируются строго как `UNATTRIBUTED_REFUSAL`, исключая ложную маскировку под квоты поставщика.
  2. **Регистрация состава тремя числами:** `retireForExhaustedDispatchBudget` фиксирует число внешних, невнешних и неприписанных отказов. Статус `UNTESTED_WITHIN_CAPACITY` присваивается строго при 100% подтверждённых внешних отказах (`external > 0 && nonExternal == 0 && unattributed == 0`). При наличии неприписанных или смешанных отказов выставляется `DISPATCH_BUDGET_EXHAUSTED` (с перечислением всех трёх чисел), без пометки `UNTESTED_WITHIN_CAPACITY`.
  3. **Хранение в полезном грузе без миграции БД:** введён enum `TaskDispatchVerdict` (`NONE`, `UNTESTED_WITHIN_CAPACITY`, `DISPATCH_BUDGET_EXHAUSTED`), сохраняемый в JSON-поле `payload` задачи (`dispatch_verdict`) с документированным переходным фоллбэком для исторических строк.
  4. **Путь разрешения (resolution path):** `requeueUntestedTasksOnRestoredCapacity(Instant now)` возвращает задачи со статусом `blocked` и вердиктом `UNTESTED_WITHIN_CAPACITY` обратно в `queued`, сбрасывает метку `last_budget_reset_at`, сбрасывает вердикт в `NONE` и регистрирует институциональный факт `TASK_CAPACITY_RECOVERY_RESUMED` в `DefectJournalEntity`.
  5. **Подключение к главному событию:** метод вызывается как при восстановлении остывших аккаунтов (`AccountHealthService.recoverEligibleAccounts`), так и при ночном сбросе суточных квот (`AccountHealthService.resetDailyLimitedAccounts` и `ContinuousOrchestrationService.resetDailyLimitedAccounts`).
- **Предписание 16 (устранение круга самозаказа по Остину):**
  1. **Переходник границы рода:** `isRequirementAlreadyOrdered` проверяет активность исходного клиентского требования (source wishlist или epic не в статусе `dismissed`).
  2. **Замена заявки на факт о доставке:** повторный сбой доставки уже заказанного требования не создаёт новую заявку (0 новых вишлистов), а фиксирует факт дефекта доставки в `DefectJournalEntity` с типом `REPEATED_DELIVERY_FAILURE` (категория `DELIVERY_EXHAUSTED`, уровень `CRITICAL`).
  3. **Глубина ремонта:** сохранена `DEFAULT_MAX_REPAIR_DEPTH = 2`.
- **Заслоняющие тесты (110/110 green в Docker Maven с `-m 1500m --cpus=2`):**
  - `DeliveryRealityLaw3CategoryErrorTest` (4/4): двукратный провал доставки клиентского требования создаёт 0 заявок и 2 записи дефекта `REPEATED_DELIVERY_FAILURE`.
  - `DeliveryRealityLaw8SecondOrderRepairTest` (6/6): проверка инвариантов цепочек ремонта.
  - `DispatchAttemptBudgetTest` (12/12): исчерпание одними внешними отказами -> `UNTESTED_WITHIN_CAPACITY`; исчерпание с отказом запроса -> `DISPATCH_BUDGET_EXHAUSTED` (13 external, 1 non-external, 0 unattributed); исчерпание чисто неприписанными отказами -> `DISPATCH_BUDGET_EXHAUSTED` (0 external, 0 non-external, 14 unattributed) и `isUntestedWithinCapacity == false`; возобновление задач по restored capacity; проверка трёхзначного `DispatchRefusalCategory`.
  - `AccountHealthServiceTest` (31/31): ночной сброс суточных лимитов триггерит возобновление задач из `UNTESTED_WITHIN_CAPACITY`.
  - `ContinuousOrchestrationServiceTest` (11/11): ночной сброс суточных лимитов триггерит возобновление задач через `projectFlowService.requeueUntestedTasksOnRestoredCapacity()`.
  - `AccountHealthServiceLaw14Test` (7/7) и `ProjectFlowServiceTest` (39/39).

**Закрыто (Такт 36):** Предписания 17 и 34 закрыты как **ЕДИНЫЙ механизм** (`StrandedFinalizingSweepService` / `JulesDispatchService` / `WishlistRepository` / `ProjectFlowService` — аренда чистильщика finalizing, `BELIEF_UPDATE_LEDGER` / D007, `CAUSAL_PROCESS_TRACE` / D013, `PRINCIPLED_INTEGRITY` / D012):
- **Собственная метка `finalizing_since` (`CAUSAL_PROCESS_TRACE` / D013, `ELVIN_GOLDMAN_06`):**
  1. Создана миграция `V139__wishlist_finalizing_since.sql` с добавлением столбца `finalizing_since TIMESTAMP WITH TIME ZONE NULL` и индекса `idx_wishlist_finalizing_since` в таблицу `wishlist`.
  2. В `WishlistEntity` добавлено поле `finalizingSince`.
  3. В `WishlistRepository` методы `compareAndSetStatusWithTimestamp` фиксируют точный момент входа в `finalizing` (`finalizingSince = :now`), а `compareAndSetStatus` очищает поле (`finalizingSince = null`) при выходе. `ProjectFlowService` также очищает метку при конвертации в задачу или отклонении.
  4. Ликвидирована категориальная ошибка замера от соседних меток (`lastCompileDispatchedAt` / `createdAt`): в инциденте п. 34 65-минутный возраст отправки приводил к мгновенному сбросу свежего `finalizing`. Теперь возраст меряется строго от `finalizingSince`.
  5. Для исторических строк без метки чистильщик инициализирует `finalizingSince = Instant.now()`, предоставляя свежий квант аренды вместо преждевременного сброса.
- **Активное продление аренды (`PRINCIPLED_INTEGRITY` / D012):**
  1. В `WishlistRepository` добавлены методы пакетного и единичного обновления `renewFinalizingLeases` / `renewFinalizingLease`.
  2. В `JulesDispatchService` реализован метод `renewFinalizingLeases(Collection<UUID> claimedIds)` с аннотацией `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
  3. В `completeWishlistCompilation` на этапах ожидания PR в GitHub и парсинга плана компилятора живой исполнитель активно продлевает аренду (`self.renewFinalizingLeases(...)`). Живой работник никогда не лишается своего захвата.
  4. Исправлен перевёрнутый комментарий: старая точка отсчёта увеличивала возраст и приводила к преждевременному сбросу, а не "never releases too early".
  5. Сообщение в логе чистильщика точно называет предмет замера: время в `finalizing` с точной отметки против динамического предела аренды из наблюдений.
- **Динамический расчет предела аренды (`BELIEF_UPDATE_LEDGER` / D007):**
  1. При успешной компиляции `JulesDispatchService` фиксирует длительность `finalizing` в `DefectJournalEntity` (`sourceComponent = "WishlistCompiler"`, `defectType = "FINALIZING_DURATION"`, категория `INSTITUTIONAL_AUDIT`).
  2. `StrandedFinalizingSweepService.calculateEffectiveLeaseDuration()` выводит предел аренды как медиану последних $\le 50$ наблюдений $\times 10$ (коэффициент безопасности), с нижним порогом 30 секунд. При нехватке наблюдений ($< 5$) используется базовое значение `maxAgeMinutes` (3 мин).
- **Очистка от `findAll()` (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010):**
  1. В `StrandedFinalizingSweepService.sweep()` подъём всех проектов с фильтрацией в памяти заменён на прямой репозиторный запрос `projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`.
- **Заслоняющие тесты (148/148 green в изолированном Docker-контейнере Maven с `-m 1500m --cpus=2`):**
  - `StrandedFinalizingSweepServiceTest` (8/8):
    - `wishlistWithOldDispatchTime_butFreshFinalizingSince_isNotSwept` (фальсифицирующий заслон п. 34).
    - `liveWorkerWithRenewedLease_isNotSweptEvenIfWorkExceedsBaseLease` (фальсифицирующий заслон п. 17).
    - `historicalWishlistWithoutFinalizingSince_isInitializedToNowAndNotSweptImmediately` (инициализация исторических строк).
    - `calculateEffectiveLeaseDuration_usesObservedDurationsWhenAvailable` (вывод аренды из истории).
    - `calculateEffectiveLeaseDuration_fallsBackToDefaultWhenInsufficientSamples` (откат при нехватке выборки).
    - `sweepIteratesAllActiveProjects` (проверка `findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` и `never().findAll()`).
  - `JulesDispatchServiceTest` (101/101, включая `renewFinalizingLeases_updatesFinalizingSinceTimestampForClaimedWishlists`).
  - `ProjectFlowServiceTest` (39/39).

**В работе дальше:**
- Синхронизация с Клодом по приёмке Предписаний 17 и 34.
- Следующий пункт по разделу XVI: Предписание 18 («Раздача задач не видит монополию аккаунта · `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` (D010)»).

