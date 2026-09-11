# Очередь починки: один механизм за раз

Собрано 8 сентября 2026 из `FACTORY_MECHANISMS.md`. Каждый пункт указывает на полную запись механизма —
там замер, живое состояние, образец и суждение. Здесь только то, что нужно, чтобы взять и починить.

**Правило оператора, 11 сентября 2026: такт не дробится. За такт — один механизм, доведённый до идеала
ЦЕЛИКОМ.** Нельзя сделать полмеханизма или треть. «Целиком» значит: все изъяны из записи механизма в
`docs/FACTORY_MECHANISMS.md`, а не одна ошибка из пункта очереди; всё сильное в нём сохранено; тест
закрепляет каждый починенный изъян. Если механизм за такт до идеала не доводится — он не берётся, а в
`AGY_ASKS.md` пишется, чего не хватает (решения, ответа, замера).

**Правило оператора, 11 сентября 2026: при разногласиях последнее слово за Антигравити.** Записи Клода в
`AGY_NEXT.md` — советы, а не приказы. Если Антигравити с советом не согласна, она делает по-своему и одной
строкой пишет в `AGY_ASKS.md`, с чем не согласна и почему; Клод этого не оспаривает повторно. Прямые решения
самого оператора (правила в этой шапке, его ответы на вопросы) — не предмет разногласия между агентами.

**Правило оператора, 11 сентября 2026: корпус философии сверять всегда, когда берётся задача** — и
Антигравити, и Клоду. Прежде чем править механизм (и прежде чем давать по нему совет): найти грепом в
`docs/philosopher-patterns` образцы, названные в записи механизма, прочесть **в корпусе** их сильную форму,
слабую форму и опровержение, и чинить к сильной форме. Не по памяти и не по пересказу в записи.

**Правило оператора, 11 сентября 2026: не упоминать несуществующих образцов.** Всякий идентификатор
образца — в коде, в коммите, в записи механизма, в вопросе и в совете — перед записью ищется грепом в
`docs/philosopher-patterns`. Нет в корпусе — не упоминать. Код изъяна (`D0xx`) берётся из заголовка самого
образца, а не пишется от себя. Замер 11 сентября: `ACP-103`, `ACP-104`, `ACP-105`, `ACP-106` в корпусе нет
(номера ACP там идут до 102 и дальше с 107), они определены только в `docs/LIVE_PRODUCT_PLAN_2026-08-19.md`
§9.3–9.5; у `ALONZO_CHERCH_21_DERIVED_CUTOFF` в корпусе `D010`, а не `D008`.

**Пункт очереди — это механизм, а не ошибка.** Ниже в пункте названа одна ошибка, потому что очередь
составлялась по ошибкам; но брать надо запись механизма целиком. Начатый и не законченный механизм
заканчивается **раньше**, чем берётся новый.

**Правило оператора, 10 сентября 2026: за один такт — один механизм или два связанных, не больше.**
Причина названа им самим: иначе качество деградирует. Связанными считаются только механизмы, которые чинятся
одним и тем же приёмом в одном и том же месте (как пункты 1 и 3 ниже); «рядом в очереди» — не связь.

**Ответы Клода лежат в `docs/reports/AGY_NEXT.md` — читать перед тем, как брать пункт.** Вопросы писать в
`docs/reports/AGY_ASKS.md`. Ход своей работы — тоже в `AGY_ASKS.md`, в строке «сделано»: `AGY_NEXT.md` отведён
только под ответы Клода, и записи других агентов там делают вид, будто ответ уже дан, когда его нет.

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

### 1. Свод состояния грузит весь список задач проекта шесть раз за вызов · `SystemStatusService` · раздел XXV

**Исправлено 10 сентября — прежняя формулировка пункта была неверна и вела не туда.** Там было сказано
«двенадцать вызовов `findAll()`, вся база раз в минуту». Двенадцать было правдой на 7 сентября, но:

- **Codex уже убрал семь из двенадцати** — шесть коммитов `perf(status)` от 8 сентября (`461154a`…`1d0b179`):
  глобальные счёты конфликтов перенесены в хранилище, аккаунты и заслон привязаны к проекту. Оставшиеся пять
  `findAll()` стоят **только на пути без проекта** (строки 124, 367, 467, 481, 484) — это холодный путь панели,
  его зовут вручную.
- **Горячий путь идёт с проектом.** Свод потока, операционная правда и контекст проекта зовут
  `getStatus(projectId)`, и там `findAll()` не срабатывает.

**Настоящий остаток.** Один вызов `getStatus(projectId)` собирает шестнадцать разделов, и **шесть из них**
каждый сам грузит весь список задач проекта через `findByProjectIdOrderByCreatedAtDesc(projectId)`:
`julesSessions` (строка 241), `qualityGate` (280), `operationalBlockers` (370), `tasks` (466),
`emsMetrics` (482), `conflictDpmo` (571). Всё это ради подсчётов, и так на каждом обороте оркестрации.

**Правок Codex на работающей фабрике нет.** Образ бэкенда собран 6 сентября в 06:15, его коммиты — от
8 сентября; в живом jar нет ни `findActiveByResolutionStatusNot`, ни `countByMergedTrueAndCreatedAtAfter`
(контроль: `getStatus` в том же классе найден). Поэтому живая память — **998,5 МБ из 1024 на 10 сентября** —
описывает **старый** код. Пока образ не пересобран, улучшения этого пункта **измерить нельзя**, и записывать
«память упала» по коду без выкладки — значит выдавать догадку за замер.

**Чинить:** грузить список задач проекта **один раз** за вызов `getStatus` и отдавать его всем шести
разделам. Затем — считать на стороне хранилища там, где нужно исключать задачи-носители; для этого нужен
явный признак носителя в столбце, а не поиск по JSON — см. ответ на первый вопрос Codex в `AGY_NEXT.md`.
**Проверка:** число загрузок списка задач на один вызов падает с шести до одной — тестом, считающим обращения
к хранилищу за вызов. После пересборки — память контейнера против базы 998,5 МБ.
**Опровергнет:** если `getStatus` идёт внутри одной сделки, повторные загрузки делят одни и те же управляемые
сущности, и тогда шесть загрузок стоят в основном времени запросов, а не шестикратной памяти. Это
соперничающая гипотеза, и её надо проверить **до** того, как приписывать весь вес этому пункту.

**Состояние на 11 сентября, 00:10 — пункт 2 НЕ брать.** (1) Его починку уже сделал Codex: пять коммитов
8 сентября (`bb4b444`, `0c59464`, `abf41a1`, `c8e4c2c`, `6ce1a6b`) — корпус не грузится при неудачном векторе
запроса, текст кусков берётся только после отбора, выборка сужена по типу и источнику. Взять его — значит
переделать сделанное. (2) На фабрике этого нет: образ собран 6 сентября. (3) Идеальный вид механизма не
определён: выборка стоит на вложениях внешней модели, а оператор решил отказаться от Gemini (раздел XXVIII
описи); довести до идеала то, что может быть отменено, за такт нельзя.

### 2. Выборка знаний поднимает весь корпус на каждый запрос · `GeminiContextService` · раздел XXVI
`retrieveFiltered` делает `findAll()` по 1518 кускам, разбирает **каждый** вектор и складывает в список
**полный текст всех** кусков, отсекая лишнее уже после сортировки. Зовут пятнадцать механизмов.
**Чинить:** отбирать ближайшее, не поднимая корпус целиком; текст брать только для отобранных.
**Проверка:** память на вызов падает; выдача та же.
**Опровергнет:** различие в выдаче до и после.

**Пункт 3 как отдельная единица НЕ берётся.** Он не механизм, а привычка, разнесённая по многим механизмам;
«по одному месту за раз» — это дробление по определению. Подъёмы таблиц чинятся внутри того механизма, который
берётся целиком.

### 3. Восемьдесят четыре места поднимают таблицу целиком · весь бэкенд · раздел XXV
Задачи — 15 мест, сессии — 14, разборы — 5.
**Чинить:** по одному месту за раз, начиная с самых частых путей.
**Проверка:** число `findAll()` в коде убывает, поведение не меняется.

**Пункты 4 и 6 — один механизм, `TocSentinelService`, значит один такт.**

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
**Закрыто (Такт 18):** В `FlowSpineService.valueStatus` снято приравнивание построенного к показанному/принятому. Состояние `DELIVERED` без подтверждённого обхода заказчиком (`walked_by="client"`) возвращает строго `scope_built_awaiting_acceptance`. `client_value_delivered` возвращается только при `ACCEPTED` либо наличии обхода заказчика.

### 18. Кэш корпуса заведён дважды, работает питоновский · `GeminiContextCacheManager` · раздел XLII
**Чинить:** снять java-овский как дублирующий.
**Закрыто (Такт 19):** Снят `GeminiContextCacheManager` (162 строки) и его вызовы в `SystemStatusController.reindexGeminiContext`. Переиндексация `geminiContextService.reindexStandingKnowledge()` оставлена без изменений (для обновления RAG-корпуса). В ответе снято неиспользуемое поле `cacheResourceName`. Питоновский кэш в сайдкаре (`PredictionService.py:116, 154`) сохранён на живом пути. Заслонено в `SystemStatusControllerTest` и `SystemStatusControllerIntegrationTest`.

### 19. Верёвка не может сработать · `TocOptimizer` · раздел XLIII
Предел буфера 15 при одном шаге, где в полёте не больше одного.
**Чинить:** при недостижимом пределе верёвки (один размеченный шаг, работа по одной) состояние — «не определено / не измерено» с указанием причины, а не «System flow optimal». Сохранить придержание при действительном переполнении буфера. Сделать порог выводимым/конфигурируемым (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS`, D008; `ALONZO_CHERCH_21_DERIVED_CUTOFF`, D010).
**Закрыто (Такт 20):** В `TocOptimizer.computeRecommendation` устранена подмена отсутствия измерений на «System flow optimal». При графе с $\le 1$ шагом рекомендация возвращает честный статус `Flow unmeasured: single instrumented stage ('%s') with in-flight capacity <= 1 cannot stretch buffer capacity %d; flow status undetermined.`. При превышении предела буфера сохранено придержание `Throttling active!`. Порог `maxBufferCapacity` сделан конфигурируемым (`DEFAULT_MAX_BUFFER_CAPACITY = 15L`, `@Value("${eneik.toc.max-buffer-capacity:15}")`). Заслонено в `TocOptimizerTest` (7/7) и `TocSentinelServiceTest`.

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
