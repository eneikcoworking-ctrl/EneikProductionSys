# Очередь починки: один механизм за раз

Собрано 8 сентября 2026 из `FACTORY_MECHANISMS.md`. Каждый пункт указывает на полную запись механизма —
там замер, живое состояние, образец и суждение. Здесь только то, что нужно, чтобы взять и починить.

**Прежде чем взять первый пункт — прочесть `docs/HOW_TO_READ_BEFORE_FIXING.md`.** Там сказано, чего не
трогать и почему сильных форм в описаниях больше, чем слабых. Кто прочтёт записи как список поломок,
сломает больше, чем починит.

**Правило работы: один пункт за раз, сверху вниз.** Взял — почини — прогони проверку — запиши в
`*Живое:*` записи механизма, что изменилось. Не бери следующий, пока предыдущий не проверен.

**Порядок задан не важностью, а зависимостью и весом.** Сперва то, от чего система стала тяжёлой: пока
бэкенд держится у гигабайта, всё остальное чинится в тесноте.


## Поправка 2026-09-09: очередь не заменяет запись механизма

Текущий режим Codex — **заполнять записи механизмов корректно, по одному такту на механизм, без правок кода**. Пункты ниже остаются свидетельством веса и порядка, но они не дают разрешения на частичный кодовый срез. Перед любой будущей правкой должна существовать полная запись механизма: идеальная форма, границы, входы/выходы, владельцы истины, связи, инварианты, сильная/слабая форма, опровержение и критерий закрытия.

комментарий для Антигравити: для пункта 1 не продолжай дробить `SystemStatusService` на новые `findAll`-срезы, пока запись целого механизма не заполнена и не видно, какие секции являются summary, какие списками UI, а какие вторичными проекциями чужой истины. Применимая философия: `BARCAN-TAG-00_CODE-GUARDIAN`, Людвиг Витгенштейн, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, family `ANTI_MIRROR_TELEMETRY`, defect `D013 Runtime drift`.

---

## Слой 1. Вес системы

### 1. Свод состояния поднимает всю базу раз в минуту · `SystemStatusService` · раздел XXV
Двенадцать вызовов `findAll()` за один вызов службы; её дёргает свод потока на каждом обороте оркестрации.
Замер: 12147 оборотов за сутки; таблицы — задачи 665, сессии 1767, разборы 812, журнал проекта 37467.
**Чинить:** спрашивать у хранилища счётчики, а не строки.
**Проверка:** занятая контейнером память перестаёт держаться у потолка; ответ свода быстрее.
**Опровергнет:** замер, показывающий, что подъёмы дёшевы.

### 2. Выборка знаний поднимает весь корпус на каждый запрос · `GeminiContextService` · раздел XXVI
`retrieveFiltered` делает `findAll()` по 1518 кускам, разбирает **каждый** вектор и складывает в список
**полный текст всех** кусков, отсекая лишнее уже после сортировки. Зовут пятнадцать механизмов.
**Чинить:** отбирать ближайшее, не поднимая корпус целиком; текст брать только для отобранных.
**Проверка:** память на вызов падает; выдача та же.
**Опровергнет:** различие в выдаче до и после.

### 3. Восемьдесят четыре места поднимают таблицу целиком · весь бэкенд · раздел XXV
Задачи — 15 мест, сессии — 14, разборы — 5.
**Чинить:** по одному месту за раз, начиная с самых частых путей.
**Проверка:** число `findAll()` в коде убывает, поведение не меняется.

### 4. Сторож просыпается каждые две секунды при одном размеченном шаге · `TocSentinelService` · раздел XXXVII
Сорок три тысячи пробуждений в сутки ради графа из одного узла.
**Чинить:** либо разметить шаги потока, либо снизить частоту.
**Опровергнет:** замер, показывающий пользу нынешней частоты.

### 5. Очистка журнала отстаёт от роста · `ProjectEventLogRetentionService` · раздел XLIV
Раз в сутки подрезает до 20000, за сутки набегает 17500. Половину времени в базе вдвое больше предела.
**Чинить:** привести частоту к скорости роста.

### 6. Чтение состояния верёвки пересчитывает и пишет · `TocSentinelService` · раздел XXXVII
`getDbrStatus()` вызывает пересчёт, который пишет в узлы. Спрашивают пятеро.
**Чинить:** отдавать последнее вычисленное, пересчёт оставить расписанию.
**Проверка:** два запроса подряд при остановленном стороже дают одно и то же и ничего не меняют.

---

## Слой 2. Вред: то, что можно сделать снаружи

### 7. Проверки полномочий нет нигде · `GoogleAiResourceController` · разделы XXXVIII, XXXIV
Замер: ноль фильтров во всём коде, библиотеки безопасности в сборке нет. Снаружи проходит изменяющий запрос.
Через открытые входы можно **удалить черновики дизайна** и **тратить деньги на внешнюю модель**.
**Чинить по вреду:** сперва уборка черновиков, затем два расходных входа, затем внутренние пути.
**Проверка:** запрос без полномочия получает отказ с названной причиной.
**Опровергнет:** любой изменяющий запрос, прошедший без полномочия.

### 8. Два входа отвечают отказом · `InternalGeminiObserverController` · раздел XXIII
`/dispatch-capacity-probe` и `/persistent-workers` дают 500, соседний вход исправен.
**Чинить:** починить или снять оба.

---

## Слой 3. Ложные показания

### 9. Неразобранный ответ модели становится утверждением «работа ценна» · `LeanValue` · пункт 42
`parseLeanValue` при неизвестном возвращает утвердительное значение.
**Опровергнет:** вызывающий, который компилируется, не обработав неизвестное.

### 10. Наша неудача записывается как факт о заказчике · `RepositoryStackAnalyzer` · раздел XXXI
Три места строят описание чужого репозитория с одинаковыми ложными признаками при отсутствии доступа, и
читающий заводит по ним находки «нет тестов», «нет сборки».
**Проверка:** при отсутствии доступа отчёт не содержит **ни одной** находки о заказчике.

### 11. Доверие начинается с полного и только убывает · `OperationalTruthService` · раздел XXVII
Шесть вычитаний, ни одного прибавления. Проект без единого свидетельства выходит «доверенным».
**Чинить:** начинать с неустановленного и расти по свидетельству, как ступень рычага.

### 12. Одно слово, два счёта: 388 против нуля · `QualityMetricsController` · раздел XXXIX
Признак «заслон не пройден» означает и провал проверки, и отсутствие применимых проверок.
**Чинить:** различать три исхода — прошли, провалились, не применялось.

### 13. Адрес репозитория существует раньше репозитория · `GitHubProjectFactoryClient` · раздел XXXII
Запасной адрес возвращается при любом исходе и записывается в проект безусловно.
**Опровергнет:** проект с непустым адресом и исходом «отказ».

### 14. У цели задачи нет значения «не установлено» · `TargetContext` · пункт 43
Неизвестное уничтожено трижды: инициализатором, геттером и читающим. Полем решается, в чей репозиторий уйдёт
работа.

### 15. Сверка миграций отключена дважды · `EneikProductionApplication` · пункт 49
`repair()` перед каждой миграцией и `validate-on-migrate=false`.
**Проверка:** правка применённой миграции роняет запуск.

---

## Слой 4. Оборванные связи

### 16. Счёт связности считается и не читается · `V80` · раздел XXIIе
9884 посчитанных исхода, ни одного читающего для решения.

### 17. Свидетельств приёмки не производится вовсе · `V100` · раздел XXIIк
Ноль обходов при 2309 суждениях о доставке за сутки.

### 18. Кэш корпуса заведён дважды, работает питоновский · `GeminiContextCacheManager` · раздел XLII
**Чинить:** снять java-овский как дублирующий.

### 19. Верёвка не может сработать · `TocOptimizer` · раздел XLIII
Предел буфера 15 при одном шаге, где в полёте не больше одного.

### 20. Две половины корпуса образцов не сверяются · `generate_philosopher_patterns.py` · раздел XXIV
Список порождается скриптом, формы написаны рукой, согласие не проверяется ничем.
**Чинить:** сверять при сборке и ронять её при расхождении.

---

## Дальше

Пункты 21 и далее — пятьдесят девять пронумерованных предписаний в разделе XVI того же файла. Они старше и
подробнее; браться за них после того, как закрыты первые двадцать.

---

# Полный проход по всем механизмам

Указание оператора 8 сентября: пройти **каждый** механизм, от самых срочных к тем, что уже хороши.
Ниже — все **205** записей файла `FACTORY_MECHANISMS.md`, разложенные по **действию**, а не по важности.
Действие разное, и в этом весь смысл порядка: сильный механизм не чинят, его проверяют и с него берут пример.

Пометка «ядро» значит, что механизм способен удержать или уронить поток. С них начинать внутри каждой ступени.

## Ступень 1 — чинить (16)

У этих в записи есть строка «Задача для кодинга»: сказано, что не так, где именно, чем проверяется и что
починку опровергнет. Порядок внутри ступени — по очереди в начале этого файла, она упорядочена по весу и вреду.

- `V100__client_acceptance_traversals.sql`  ← ядро
- `generate_philosopher_patterns.py`  ← ядро
- `SystemStatusService`  ← ядро
- `OperationalTruthService`  ← ядро
- `DesignAssetService`  ← ядро
- `MLPredictionServiceClient`  ← ядро
- `RepositoryStackAnalyzer`  ← ядро
- `GitHubProjectFactoryClient`  ← ядро
- `CommandDashboardService`  ← ядро
- `InternalGeminiObserverController`
- `TocSentinelService`  ← ядро
- `GoogleAiResourceController`
- `QualityMetricsController`
- `GeminiContextCacheManager`
- `TocOptimizer`  ← ядро
- `ProjectEventLogRetentionService`  ← ядро

## Ступень 2 — сперва измерить, потом решать (23)

Форма образца слабая, но задачи нет: значит я нашёл несоответствие и **не проверял, вредит ли оно**.
Действие здесь — воспроизвести опровержение, названное в записи. Если воспроизводится — завести задачу и
чинить. Если нет — записать, что не воспроизводится, и **не трогать код**.

- `DesignShopCycleRepository`  ← ядро
- `TargetContext`  ← ядро
- `AccountStatus`  ← ядро
- `LeanValue`  ← ядро
- `ProjectStatus`  ← ядро
- `Judgement`  ← ядро
- `TocNode`  ← ядро
- `TocEdge`
- `LogScopeBuffer`
- `DefectJournalEntity`  ← ядро
- `TaskStatus`  ← ядро
- `EneikProductionApplication`  ← ядро
- `V137__compiler_task_identity_from_work.sql`  ← ядро
- `V132__return_the_two_requirements_the_grouper_took_from_V129.sql`  ← ядро
- `V19__restructure_accounts_and_projects.sql`  ← ядро
- `V58__drop_project_event_log_and_watermark.sql`
- `V111__permanently_disable_gemini_project_observer.sql`  ← ядро
- `V80__coherence_runs.sql`  ← ядро
- `V21__add_file_scope_and_conflicts.sql`  ← ядро
- `V62__add_context_chunk_content_hash.sql`
- `V15__create_project_final_reports.sql`
- `append_role_logic.py`
- `StrandedFinalizingSweepService`  ← ядро

## Ступень 3 — провести пробу, кода не менять (8)

«Форма: не мерено» — это про меня, а не про механизм: я не проводил пробу. Здесь чинить нечего.
Действие: провести названную пробу и записать её исход в `*Живое:*`. Правка кода на этой ступени — ошибка.

- `ClaimResultStatus`
- `OperationalPolicyDeniedException`  ← ядро
- `AnomalyReport`
- `JulesRoleCapabilities`
- `ScopedBufferAppender`  ← ядро
- `V25__add_depends_on_and_hotspots.sql`
- `OperationalFlowCoreService`  ← ядро
- `AccountRepository`  ← ядро

## Ступень 4 — дописать запись (118)

У этих нет строки о форме образца: они описаны раньше, чем эта строка стала обязательной. Действие —
дочитать механизм, назвать образец и форму, добавить `*Живое:*`. Это работа замером, не правкой.
Дефекты, если найдутся, заводить задачей и переносить на первую ступень.

- `ProjectFlowService`  ← ядро
- `ProjectFactoryService`
- `ProjectWorkspaceFactoryService`  ← ядро
- `RequirementGroundingService`
- `MarketCorpusService`
- `MarketComplianceGate`
- `MarketResearchService`
- `OnboardingAuditService`  ← ядро
- `TechnicalLeadCompiler`  ← ядро
- `FeatureService`  ← ядро
- `EpistemicMetadataClassifier`
- `KanoClass`  ← ядро
- `EmsFlowStage`  ← ядро
- `SelfFalsificationEpicMatcher`
- `WishlistContentSimilarityMatcher`
- `WishlistService`  ← ядро
- `JulesDispatchService`  ← ядро
- `JulesApiClient`  ← ядро
- `SessionLifecycleService`  ← ядро
- `PersistentWorkerSessionService`  ← ядро
- `ClaimService`  ← ядро
- `LeaseWatchdogService`  ← ядро
- `AccountHealthService`  ← ядро
- `BottleneckAwarePriorityService`
- `AutoMergeService`  ← ядро
- `GitHubPullRequestService`  ← ядро
- `GitHubApiBudgetService`  ← ядро
- `GithubAccessService`  ← ядро
- `CodeChangeClassifier`  ← ядро
- `GateOrchestrator`
- `VerificationEvidenceGate`  ← ядро
- `BranchGarbageCollectorService`
- `PrReviewPipelineService`  ← ядро
- `ClientDeliverableReadinessService`  ← ядро
- `DeliveryRealityProducerService`  ← ядро
- `ProductLaunchabilityService`  ← ядро
- `VerdictLayer`  ← ядро
- `AcceptanceVerdictLayer`  ← ядро
- `RuntimeVerdictLayer`  ← ядро
- `DoctrineVerdictLayer`  ← ядро
- `InfrastructureVerdictLayer`  ← ядро
- `SixSigmaVerdictLayer`
- `JudgmentAgentClient`
- `FactoryJudgmentService`
- `DeliveredWorkJudgmentService`  ← ядро
- `CriteriaEvidenceSelector`  ← ядро
- `LeverPromotionService`
- `ProcessControlService`
- `ConstraintIdentificationService`
- `LaunchabilityConstraintService`  ← ядро
- `TocSubordinationLever`  ← ядро
- `FlowMetricsService`
- `TaskWaitTimeService`
- `SixSigmaAuditService`
- `EmsMetricsService`
- `ProjectOperationalContextService`
- `SystemProgressTracker`  ← ядро
- `AiHealthTracker`
- `RiskLevelCalculator`
- `FalsificationCycleService`  ← ядро
- `EvidenceCoherenceService`
- `OpsAuditorService`  ← ядро
- `PlatformSelfReferenceDetector`  ← ядро
- `FactorySelfHealthService`  ← ядро
- `KaizenService`
- `PlannedWorkRecoveryService`  ← ядро
- `ContinuousOrchestrationService`  ← ядро
- `OperationalPolicyService`
- `OperationalAction`  ← ядро
- `TrustSnapshotService`
- `GoogleAiResourceService`
- `EmbeddingSimilarityUtil`
- `StitchClient`
- `RuntimeLauncherClient`
- `GeminiObserverActionService`
- `DesignShopOrchestrationService`
- `DesignConsistencyAuditService`
- `DesignSystemFalsificationService`
- `DesignDriftMonitorService`
- `ClientRuntimeObservabilityService`
- `BetaPosterior`
- `RuntimeHealthShiftDetector`
- `ProductCapabilityService`
- `LogScope`
- `ProjectLogFlushQueue`
- `SystemSettingsService`  ← ядро
- `ProjectTreeService`
- `IdleProjectAdviceService`
- `RoleCapabilityLoader`  ← ядро
- `ProjectAuditPipelineService`
- `AccountController`  ← ядро
- `ProjectController`  ← ядро
- `ClaimController`  ← ядро
- `WishlistController`  ← ядро
- `InternalTaskController`  ← ядро
- `JulesSessionController`  ← ядро
- `GithubWebhookController`
- `SchedulingConfig`  ← ядро
- `TocAnomalyDetector`
- `DefectJournalService`  ← ядро
- `GithubConfig`
- `GlobalExceptionHandler`
- `WebConfig`
- `launcher.py`  ← ядро
- `server.js`  ← ядро
- `PredictionService.py`
- `ContextChunkRepository`
- `TaskEntity`
- `JulesSessionEntity`
- `EvidenceNodeEntity`  ← ядро
- `JulesConfigEntity`
- `PrivacyFilter`  ← ядро
- `AgencyApplication`
- `TocToken`
- `WishlistItemStatus`
- `V99__task_conflict_preserved_branch.sql`  ← ядро
- `audit_pr.py`
- `VideoAssetService`

## Ступень 5 — проверить и не трогать (40)

Форма сильная: механизм отвечает образцу. Действие — **убедиться, что отвечает по-прежнему**, прогнав
названное в записи опровержение, и записать исход. Менять здесь нечего, а приём стоит перенять для других
мест. Часть этих механизмов перечислена в `HOW_TO_READ_BEFORE_FIXING.md` как защищённые.

- `ClaimRepository`  ← ядро
- `ProjectEventLogRepository`
- `WishlistEntity`  ← ядро
- `WishlistSource`  ← ядро
- `KaizenProposal`
- `WishlistStatus`  ← ядро
- `PersistentWorkerPurpose`  ← ядро
- `Verdict`  ← ядро
- `DbrStatus`  ← ядро
- `LeverAgreement`  ← ядро
- `LeverStage`  ← ядро
- `TaskTitleBuilder`
- `DurableProjectLogAppender`  ← ядро
- `KaizenProposalEntity`  ← ядро
- `DesignShopCycleEntity`  ← ядро
- `V82__operational_reality_findings.sql`  ← ядро
- `V97__jules_session_pr_opened_workflow_claim.sql`  ← ядро
- `V42__add_pr_review_has_code_and_role_threads.sql`  ← ядро
- `V44__add_features.sql`  ← ядро
- `V45__rename_role_threads_to_feature_threads.sql`  ← ядро
- `V30__create_jules_activity_responses.sql`
- `V126__return_the_budget_of_a_brief_refused_without_a_readable_reason.sql`  ← ядро
- `V104__observation_instrument_failure.sql`  ← ядро
- `V61__create_project_event_log.sql`  ← ядро
- `V121__drop_needs_human_review.sql`  ← ядро
- `V59__create_gemini_observer_journal.sql`  ← ядро
- `V68__gemini_observer_journal_continuity_and_action_verification.sql`  ← ядро
- `V79__kaizen_proposals_and_evidence_nodes.sql`  ← ядро
- `V81__coherence_confidence.sql`  ← ядро
- `V72__create_process_control_snapshots.sql`  ← ядро
- `V85__process_control_snapshot_metric_label.sql`
- `V90__trust_signal_snapshots.sql`  ← ядро
- `V69__create_project_file_claims.sql`  ← ядро
- `V123__drop_task_conflicts_without_a_task.sql`  ← ядро
- `V55__create_context_chunks.sql`  ← ядро
- `V92__client_runtime_observations.sql`  ← ядро
- `FlowSpineService`  ← ядро
- `GeminiContextService`  ← ядро
- `TaskRepository`  ← ядро
- `DesignExcellenceGate`  ← ядро

---

**Итог прохода.** Ступени 1 и 2 — это починка, 39 механизмов. Ступени 3 и 4 — это замер,
126. Ступень 5 — подтверждение, 40. Больше половины прохода не требует ни строки нового
кода, и это не недостаток очереди, а свойство фабрики: описанное в ней устройство чаще верно, чем нет.
