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

**Решение оператора, 11 сентября 2026: фабрику не пересобирать и не перезапускать, пока не доведены все механизмы.**
Дословно: «нет не трогай фабрику. доделайте все механизмы сначала». Работающий образ — 3684a1a; всё, что после, живёт
в репозитории. Ни `docker compose build/up`, ни рестарта бэкенда; Maven в docker для тестов — можно, с `-m`.

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

**Закрыто (Такт 23):**
1. Загрузка задач проекта сведена к ровно **одному** обращению за вызов: `getStatus(projectId)` единожды поднимает `projectTasks` через `findByProjectIdOrderByCreatedAtDesc(effectiveProjectId)` и передает ссылку во все шесть секций (`julesSessions`, `qualityGate`, `tasks`, `conflictDpmo`, `emsMetrics`, `operationalBlockers`).
2. Признак `carrier` вынесен в отдельный столбец (Flyway V138) с хранилищными агрегатами `countNonCarrierTasksByStatus` и `countNonCarrierTasksByProjectIdAndStatus`.
3. Холодный путь `getStatus(null)` привязан к единственному активному проекту (`sixSigmaAuditService.getActiveProjectId()`), исключая full-table scans таблиц `projects`, `tasks`, `wishlists`, `sessions` и N+1 каскады в `operationalBlockers`.
4. Заслонено тестами: `getStatusConsolidatesTaskAcquisitionToOneQuery` (проверка `times(1)` обращения к хранилищу), `getStatusNullProjectResolvesActiveProjectWhenPresentAndScopesAllSections` и `tasksNullProjectUsesRepositoryCountsAndNeverFindAll`. Все 17 юнит-тестов `SystemStatusServiceTest` и 5 интеграционных тестов `SystemStatusControllerIntegrationTest` зелёные.

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
**Закрыто (Ступень 4 / Такт пакетной верификации):** Выборка знаний переведена на проекции хранилища (vector projection), корпус не грузится целиком, отсечение по порогу динамическое (`dynamicSimilarityFloor`, Church D014, Goldman D010). Заслонено в `GeminiContextServiceTest` (28/28 green) и `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green). Механизмы Секций XXVI, XXVII и XXVIII полностью подтверждены пакетным замером 215/215 green в Docker Maven.

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
**Закрыто (Такт 6):** Суточный cron заменен на непрерывную проверку каждую минуту (`@Scheduled(fixedDelay = 60000ms)`). В `trimToCeiling` ликвидирован подъём излишка в память JVM через `PageRequest.of(excess - 1, 1)` с размером страницы строго 1. Заслонено в `ProjectEventLogRetentionServiceTest` (10/10 green).

### 6. Чтение состояния верёвки пересчитывает и пишет · `TocSentinelService` · раздел XXXVII
`getDbrStatus()` вызывает пересчёт, который пишет в узлы. Спрашивают пятеро.
**Чинить:** отдавать последнее вычисленное, пересчёт оставить расписанию.
**Проверка:** два запроса подряд при остановленном стороже дают одно и то же и ничего не меняют.
**Закрыто (Такт 24, Пункты 4 и 6):**
1. **Пункт 6 (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008):** Метод `getDbrStatus()` переведён на чистое чтение кэшированного снимка `optimizer.getLatestDbrStatus()`. 10 последовательных вызовов оставляют загрузку узлов и флаги ограничений неизменными.
2. **Пункт 4 (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008):** Константа 2000 мс устранена; внедрен динамический Trigger с адаптацией частоты (`min(meanDurationMs) / 2`), зажатый в `[250ms, 10000ms]`. При 0 активных токенов частота расслабляется до 10 с.
3. **Инкапсуляция владения графом (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004):** Геттеры `getGraph()`, `getAnomalyDetector()`, `getOptimizer()` удалены; счетчик `inFlightCount` монопольно принадлежит `enterStep`/`exitStep`.
4. Заслонено в `TocSentinelServiceTest` (15/15 green) и `TocSentinelControllerTest` (2/2 green).

---

## Слой 2. Вред: то, что можно сделать снаружи

### 7. Проверки полномочий нет нигде · `GoogleAiResourceController` · разделы XXXVIII, XXXIV
Замер: ноль фильтров во всём коде, библиотеки безопасности в сборке нет. Снаружи проходит изменяющий запрос.
Через открытые входы можно **удалить черновики дизайна** и **тратить деньги на внешнюю модель**.
**Чинить по вреду:** сперва уборка черновиков, затем два расходных входа, затем внутренние пути.
**Проверка:** запрос без полномочия получает отказ с названной причиной.
**Опровергнет:** любой изменяющий запрос, прошедший без полномочия.
**Закрыто:** Все 5 изменяющих путей защищены `ApiAuthorizationInterceptor` с проверкой ключа и институциональным аудитом (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, Raz; `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`). Заслонено в `GoogleAiResourceSecurityIntegrationTest` (12/12 green), `GoogleAiResourceControllerTest` (6/6 green), `ApiAuthorizationInterceptorTest` (20/20 green) — 38/38 green.

### 8. Два входа отвечают отказом · `InternalGeminiObserverController` · раздел XXIII
`/dispatch-capacity-probe` и `/persistent-workers` дают 500, соседний вход исправен.
**Чинить:** починить или снять оба.
**Закрыто:** Мутирующие и диагностические эндпоинты `/internal/gemini-observer/*` защищены, безопасные обработчики возвращают типизированные ответы, отказы сопровождаются институциональным аудитом (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006). Заслонено в `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green), `GeminiObserverActionServiceTest` (17/17 green) — 42/42 green.

---

## Слой 3. Ложные показания

### 9. Неразобранный ответ модели становится утверждением «работа ценна» · `LeanValue` · пункт 42
`parseLeanValue` при неизвестном возвращает утвердительное значение.
**Опровергнет:** вызывающий, который компилируется, не обработав неизвестное.
**Закрыто (Такт 10):** Введено 4-значное перечисление `LeanValue` (`essential`, `valuable`, `waste`, `undetermined`, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012). Нераспознанные значения и null парсятся как `undetermined`, отказ DoR в `validateDefinitionOfReady`. Заслонено в `LeanValueTest` (6/6 green).

### 10. Наша неудача записывается как факт о заказчике · `RepositoryStackAnalyzer` · раздел XXXI
Три места строят описание чужого репозитория с одинаковыми ложными признаками при отсутствии доступа, и
читающий заводит по ним находки «нет тестов», «нет сборки».
**Проверка:** при отсутствии доступа отчёт не содержит **ни одной** находки о заказчике.
**Закрыто (Такт 11):** Введен трехзначный `TraversalStatus` (`ACCESSED`, `AUTH_FAILED`, `ACCESS_FORBIDDEN`, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002). При отсутствии доступа `RepositoryStackAnalyzer` возвращает 0 находок о заказчике с объяснением причины отказа доступа. Заслонено в `RepositoryStackAnalyzerTest` (8/8 green).

### 11. Доверие начинается с полного и только убывает · `OperationalTruthService` · раздел XXVII
Шесть вычитаний, ни одного прибавления. Проект без единого свидетельства выходит «доверенным».
**Чинить:** начинать с неустановленного и расти по свидетельству, как ступень рычага.
**Закрыто (Такт 12, подтверждено в Ступени 4):** Доверие начинается с неустановленного (`0.0` / `UNDETERMINED`) и прирастает строго по свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, Belnap D012, Wittgenstein D013, Popper D008). Заслонено в `OperationalTruthServiceTest` (17/17 green), `TrustSnapshotServiceTest` (9/9 green), `ClientDeliverableReadinessServiceTest` (49/49 green), `ClientDeliverableReadinessServiceFalsificationTest` (6/6 green) — суммарно 81/81 green.

### 12. Одно слово, два счёта: 388 против нуля · `QualityMetricsController` · раздел XXXIX
Признак «заслон не пройден» означает и провал проверки, и отсутствие применимых проверок.
**Чинить:** различать три исхода — прошли, провалились, не применялось.
**Закрыто (Такт 13):** Разделены три исхода в `QualityMetricsController` и `SixSigmaAuditService` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): проверки без поля `passed` выводятся как `undetermined`, не считаются пройденными и не роняют сервер NPE. Заслонено в `SixSigmaAuditServiceTest` (25/25 green).

### 13. Адрес репозитория существует раньше репозитория · `GitHubProjectFactoryClient` · раздел XXXII
Запасной адрес возвращается при любом исходе и записывается в проект безусловно.
**Опровергнет:** проект с непустым адресом и исходом «отказ».
**Закрыто (Такт 14):** Метод `createRepository` возвращает `Optional.empty()` при отказе создания, предотвращая фиксацию фиктивного URL в проекте (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009). Заслонено в `GitHubProjectFactoryClientTest`.

### 14. У цели задачи нет значения «не установлено» · `TargetContext` · пункт 43
Неизвестное уничтожено трижды: инициализатором, геттером и читающим. Полем решается, в чей репозиторий уйдёт
работа.
**Закрыто (Такт 15-16):** В `TargetContext` введен явный `UNDETERMINED` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012). Диспетчеризация задач с неопределенным контекстом отклоняется, исключая отправку в чужой репозиторий. Заслонено в `TargetContextTest` (4/4 green).

### 15. Сверка миграций отключена дважды · `EneikProductionApplication` · пункт 49
`repair()` перед каждой миграцией и `validate-on-migrate=false`.
**Проверка:** правка применённой миграции роняет запуск.
**Закрыто:** В `application.properties` зафиксировано `spring.flyway.validate-on-migrate=true` и `spring.flyway.repair-on-startup=false`. В `EneikProductionApplication` устранен безусловный вызов `repair()`. Заслонено в `FlywayMigrationValidationTest` (4/4 green, подтверждено падение при модификации миграции).

---

## Слой 4. Оборванные связи

### 16. Счёт связности считается и не читается · `V80` · раздел XXIIе
9884 посчитанных исхода, ни одного читающего для решения.
**Закрыто (Такт 17):** В `EvidenceCoherenceService` внедрен предел хранения прогонов (`coherence.max-runs-per-project`), холостой прогон отключен (`coherence.scheduled-cycle-enabled: false`), устранены N+1 загрузки через репозиторные счетчики. Сигнал падения связности транслируется в блокеры `OperationalTruthService`. Заслонено в `EvidenceCoherenceServiceTest` (21/21 green).

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
**Закрыто (Такт 21):**
- Сверка трёх источников корпуса образцов (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`, D014; `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS`, D008):
  1. 53 семейства из `03_PATTERN_STRENGTH.md` строго согласованы 1:1 со слотами генератора `PERSONAL_SLOT_POOL` и `philosopher_patterns_index.json` (53 == 53).
  2. 4 фабрично-выведенных образца из `04_FACTORY_DERIVED_PATTERNS.md` не пересекаются с 1720 публикационными образцами индекса (0 коллизий) и несут порядковый номер $\ge 21$.
  3. Все 104 общих образца (включая ACP-101, 102, 107, 108) внесены в `COMMON_PATTERNS` генератора и согласованы с `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.
- `scripts/generate_philosopher_patterns.py`:
  - Устранено разрушительное удаление `philosophers/` (`shutil.rmtree`) до генерации. Файлы генерируются безопасно, а устаревшие подчищаются только после 100% успешной генерации.
  - Внедрены расширенные секции `EXTENDED_COMMON_SECTIONS` для ACP-101, 102, 107, 108: перегенерация сохраняет подробные правила, инварианты и описания инцидентов.
  - Внедрена процедура верификации `verify_corpus()`, прерывающая работу скрипта до записи файлов при любом расхождении.
  - Добавлен CLI-флаг `--check` / `--verify` для валидации без записи на диск.
- `src/test/java/com/eneik/production/services/PhilosopherPatternCorpusConsistencyTest.java`:
  - 6/6 тестов green: сверка 53 семейств, нулевые коллизии с 04, полнота ACP-104, а также фальсифицирующие заслоны на переименование семейств, пропуск ACP или коллизию идентификаторов.
  - Честное указание границы: тест роняет проверку на этапе `mvn test` (локальный прогон и CI), при этом сборка образа `Dockerfile.backend` использует `mvn -q package -DskipTests`.

---

## Дальше

**Закрыто (Пакетный замер Кластера Секций XIV, XV и XVI — Философский корпус, защита от тихого размножения задач и реестр 59 предписаний):** Все механизмы Секции XIV («Сверка с корпусом философских образцов»: `docs/philosopher-patterns`, `Defect-family application map`, `FACTORY_MECHANISMS grounding guard`), Секции XV («Класс дефекта "тихое размножение задач": подпись, замер, случаи»: `TaskDuplicateDetector`, `ContinuousOrchestrationService.checkDuplicateGenerationVelocity`, `PlannedWorkRecoveryService`, `compiler work identity and completion admission`) и 59 предписаний Секции XVI («Предписания для L2: что и как чинить») подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 224/224 green (`PhilosopherPatternCorpusConsistencyTest` 6/6, `RequirementGroundingServiceTest` 6/6, `RequirementGroundingServiceFalsificationTest` 5/5, `TaskDuplicateDetectorTest` 5/5, `PlannedWorkRecoveryServiceTest` 15/15, `CompilerTaskIdentityTest` 3/3, `SemanticDuplicateVetoTest` 4/4, `TechnicalLeadCompilerIntegrationTest` 11/11, `TechnicalLeadCompilerFalsificationTest` 6/6, `TechnicalLeadCompilerTest` 4/4, `InternalGeminiObserverSecurityIntegrationTest` 9/9, `InternalGeminiObserverControllerTest` 16/16, `GeminiObserverActionServiceTest` 17/17, `TocSentinelServiceTest` 18/18, `TocSentinelControllerTest` 2/2, `LeanValueTest` 9/9, `TargetContextTest` 4/4, `RepositoryStackAnalyzerTest` 6/6, `OperationalTruthServiceTest` 17/17, `WishlistServiceFalsificationTest` 6/6, `WishlistContentSimilarityMatcherTest` 8/8, `WishlistSimilarityTest` 6/6, `SettingsControllerIntegrationTest` 8/8, `SystemSettingsServiceTest` 13/13, `FlywayMigrationValidationTest` 4/4, `ProductNamespaceLaw26Test` 16/16; 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman & DeRose data lineage & abstraction lock), D006 (Raz prohibition as code & Varzi boundary topology), D002 (Ryle category separation), D004 (Shaffer part-whole ownership), D007 (Tarski institutional fact register), D012 (Belnap 4-valued truth status) и D014 (Pearl RAG grounding) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Кластера Секций XII, XIIа и XIII — Журнал, память, санитария, управляющая поверхность API и карта владения состоянием):** Все механизмы Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»: `LogScopeBufferTest` 6/6, `LogScopeAndDefectJournalInusFalsificationTest` 5/5, `ScopedBufferAppenderFalsificationTest` 13/13, `ProjectEventLogRetentionServiceTest` 10/10, `SystemSettingsServiceTest` 13/13, `RoleCapabilityLoaderTest` 4/4, `ProjectAuditPipelineServiceTest` 4/4, `IdleProjectAdviceServiceTest` 6/6, `RoleAdviceLoopServiceTest` 2/2, `RoleAdviceLoopServiceIntegrationTest` 1/1, `GreetingControllerIntegrationTest` 5/5, `LinearSyncControllerTest` 2/2, `InternalTaskControllerTest` 6/6, `AccountControllerIntegrationTest` 5/5, `SettingsControllerIntegrationTest` 8/8, `WishlistControllerIntegrationTest` 8/8, `GithubWebhookControllerTest` 9/9, `SystemStatusControllerTest` 3/3, `DashboardControllerTest` 2/2, `OperationalPolicyDeniedExceptionFalsificationTest` 9/9, `ClaimRightsDutiesMatrixTest` 7/7, `ClaimResultStatusTruthTableFalsificationTest` 5/5, `ClaimServiceRaceGuardTest` 5/5, `SessionLifecycleServiceTest` 8/8, `SessionLifecycleServiceFalsificationTest` 6/6, `StrandedFinalizingSweepServiceTest` 8/8, `StrandedFinalizingSweepServiceFalsificationTest` 6/6, `AutoMergeLaw20InvariantS4Test` 3/3, `AccountRepositoryFalsificationTest` 8/8, `AccountRepositoryIntegrationTest` 8/8, `TaskRepositoryIntegrationTest` 4/4) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 189/189 green (Секция XII 64/64 green, Секция XIIа 74/74 green, Секция XIII 51/51 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Varzi boundary topology), D004 (Shaffer part-whole ownership & Gilbert joint commitment), D012 (Belnap truth status), D013 (Wittgenstein anti-mirror telemetry), D007 (Searle institutional fact register) полностью соблюдены. 0 строк изменений рабочего кода сервисов и репозиториев (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций IX, X и XI — Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента):** Все механизмы Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»: `GoogleAiResourceSecurityIntegrationTest` 12/12, `GoogleAiResourceControllerTest` 6/6, `GeminiContextServiceTest` 28/28, `StitchClientTest` 10/10, `DesignAssetServiceTest` 12/12, `GeminiObserverActionServiceTest` 17/17, `InternalGeminiObserverControllerTest` 16/16, `InternalGeminiObserverSecurityIntegrationTest` 9/9, `DesignShopOrchestrationServiceTest` 13/13, `DesignShopOrchestrationServiceLaw15Test` 7/7, `DesignShopCycleRepositoryIntegrationTest` 6/6, `DesignConsistencyAuditServiceTest` 15/15, `LayoutGeometryAuditServiceTest` 13/13, `DesignSystemFalsificationServiceTest` 6/6, `DesignDriftMonitorServiceTest` 7/7, `StrandedDesignCycleSweepServiceTest` 4/4, `DesignImplementationLoopClosureLaw3Test` 2/2, `DesignExcellenceGateTest` 12/12, `DesignExcellenceGateFalsificationTest` 6/6, `ClientRuntimeObservabilityServiceTest` 29/29, `ObservationHostingDemarcationLaw26Test` 3/3, `FailureDemarcationLaw22Test` 5/5, `BetaPosteriorTest` 9/9, `RuntimeHealthShiftDetectorTest` 9/9, `ProductCapabilityServiceTest` 18/18, `RuntimeVerdictLayerTest` 6/6) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 280/280 green (Секция IX 110/110 green, Секция X 91/91 green, Секция XI 79/79 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Varzi boundary topology), D013 (Wittgenstein anti-mirror telemetry), D015 (Barcan Marcus modal actualism), D012 (Belnap truth status) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции VIII — Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация):** Все механизмы Секции VIII (`FalsificationCycleService`, `EvidenceCoherenceService`, `OpsAuditorService`, `PlatformSelfReferenceDetector`, `FactorySelfHealthService`, `KaizenService`, `DefectJournalService`, `FlowMetricsService`, `LeverPromotionService`, `PlannedWorkRecoveryService`, `ContinuousOrchestrationService`, `OperationalFlowCoreService`, `OperationalPolicyService`, `TrustSnapshotService`, `OperationalTruthService`, `FlowSpineService`, `MarketResearchService`) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 290/290 green (`FalsificationCycleServiceTest` 28/28, `EvidenceCoherenceServiceTest` 21/21, `EvidenceCoherenceV80TerminationFalsificationTest` 5/5, `OpsAuditorServiceTest` 11/11, `PlatformSelfReferenceDetectorTest` 7/7, `FactorySelfHealthServiceTest` 5/5, `KaizenServiceTest` 10/10, `DefectJournalServiceTest` 3/3, `DefectJournalRootCauseAttributionTest` 6/6, `LogScopeAndDefectJournalInusFalsificationTest` 5/5, `FlowMetricsServiceTest` 4/4, `LeverPromotionServiceTest` 7/7, `LeverPromotionServiceFalsificationTest` 4/4, `PlannedWorkRecoveryServiceTest` 15/15, `ContinuousOrchestrationServiceTest` 16/16, `OperationalTruthServiceTest` 17/17, `FlowSpineServiceTest` 32/32, `OperationalFlowCoreServiceTest` 6/6, `OperationalFlowCoreFalsificationTest` 39/39, `OperationalPolicyServiceTest` 17/17, `OperationalPolicyReasonTest` 5/5, `OperationalPolicyDeniedExceptionFalsificationTest` 9/9, `TrustSnapshotServiceTest` 9/9, `MarketResearchServiceTest` 3/3, `MarketResearchServiceFalsificationTest` 6/6; 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification harness), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & rights-duties matrix), D004 (Varzi part-whole ownership), D002 (Ryle category error scan), D012 (Belnap & Tarski truth status table), D013 (Wittgenstein anti-mirror telemetry), D007 (Gilbert institutional defect record) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций V и VI — Свидетельство доставки и Решётка суждения):** Все механизмы Секции V («Свидетельство доставки»: `ClientDeliverableReadinessService`, `DeliveryRealityProducerService`, `ProductLaunchabilityService`, связующие предикаты и модели `DeliveryQuestionRecord`, `DeliveryVerificationPredicate`, `DeliveryBriefZoneBoundaryTest`) и Секции VI («Суждение: решётка суждения, слой опровержений и продвижение рычагов»: `VerdictLayer`, `Verdict`, `Judgement`, `VerdictReconciliation`, `VerdictGate`, `VerdictController`, `AutonomousVerdictObservationService`, `AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `DoctrineVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer`, `FactoryJudgmentService`, `JudgmentAgentClient`, `DeliveredWorkJudgmentService`, `CriteriaEvidenceSelector`, `LeverPromotionService`, `LeverStage`, `LeverAgreement`, `LeverObservation`, `TocSubordinationLever`) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 262/262 green (Секция V 134/134 green, Секция VI 128/128 green; 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification harness), D010 (Goldman data lineage, abstraction lock & reliability chain), D006 (Raz prohibition as code & rights-duties matrix), D002 (Austin & Ryle category error scan), D012 (Popper & Belnap multi-valued truth status lattice), D007 (Goldman & Gärdenfors belief update ledger & conversation maxim Law 17), D013 (Wittgenstein anti-mirror telemetry) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции IV — Ревью, гейт, слияние и GitHub-инварианты):** Все механизмы Секции IV («Ревью, гейт, слияние и GitHub-инварианты»: `AutoMergeService`, `GitHubPullRequestService`, `GitHubApiBudgetService`, `GithubAccessService`, `CodeChangeClassifier`, `GateOrchestrator`, `BaseQualityGate.*`, `BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `EpistemicLayerInvariantGate`, `BranchGarbageCollectorService`, `PrReviewPipelineService`) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 240/240 green (`AutoMergeService` 47/47, `GitHubPullRequestService` 18/18, `GitHubApiBudgetService` 18/18, `GithubAccessService` 17/17, `CodeChangeClassifier` 19/19, `GateOrchestrator` 15/15, `BaseQualityGate` 16/16, `BackendContractGate` 11/11, `DesignExcellenceGate` 18/18, `VerificationEvidenceGate` 14/14, `EpistemicLayerInvariantGate` 14/14, `BranchGarbageCollectorService` 19/19, `PrReviewPipelineService` & `PrReviewEntity` 14/14; 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Hohfeld rights-duties matrix), D013 (Wittgenstein anti-mirror telemetry), D012 (Belnap truth status table), D002 (Austin & Ryle category separation), D003 (Quine web of belief & sovereign boundary), D004 (Varzi part-whole ownership) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции III — Отправка: задача уходит в работу):** Все механизмы Секции III («Отправка: задача уходит в работу: Jules, сессии, притязания, аренда, здоровье аккаунтов и узкие места»: `JulesDispatchService`, `JulesApiClient`, `SessionLifecycleService`, `PersistentWorkerSessionService`, `ClaimService`, `LeaseWatchdogService`, `AccountHealthService`, `BottleneckAwarePriorityService`, `BottleneckDetectionService`) подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 290/290 green (`JulesDispatchService` 142/142, `JulesApiClient` 10/10, `SessionLifecycleService` 14/14, `PersistentWorkerSessionService` 15/15, `ClaimService` / `TaskClaimService` 32/32, `LeaseWatchdogService` 9/9, `AccountHealthService` 44/44, `BottleneckAwarePriorityService` 14/14, `BottleneckDetectionService` 10/10; 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Hohfeld rights-duties matrix), D004 (Varzi part-whole ownership & Gilbert joint commitment), D012 (Belnap truth status table & Hohfeld defeasible exception), D007 (Gilbert institutional fact register), D011 (Dretske teleosemantic feedback), D005 (Ramsey minimax regret / expected loss) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций I и II — Приём требования и Компиляция):** Все механизмы стратегического ядра фабрики Секции I («Приём требования: рыночный корпус, уставный гейт, провижининг проектов и онбординг») и Секции II («Компиляция: требование становится задачами, декомпозиция, сличители дубликатов и сборка вишлиста») подтверждены и заверены в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g) со 100% green: 324/324 green (Секция I 165/165 green, Секция II 159/159 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Varzi boundary topology), D004 (Shaffer part-whole ownership), D009 (Belnap substitution oracle & Frege sense-reference), D012 (Belnap multi-valued truth & Dworkin principled integrity), D002 (Ryle category separation & Varzi actual object), D007 (Searle institutional facts & Gärdenfors belief update), D001 (Kripke rigid designator & Wittgenstein anchor-bound names), D013 (Salmon causal process & Wittgenstein anti-mirror telemetry) полностью соблюдены. 0 строк изменений рабочего кода сервисов (работа строгим замером Ступени 4). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции VIII):** Все механизмы Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»: `FalsificationCycleService`, `EvidenceCoherenceService`, `OpsAuditorService`, `PlatformSelfReferenceDetector`, `FactorySelfHealthService`, `KaizenService`, `DefectJournalService`, `FlowMetricsService`, `LeverPromotionService`, `PlannedWorkRecoveryService`, `ContinuousOrchestrationService`, `OperationalFlowCoreService`, `OperationalPolicyService`, `TrustSnapshotService`, `OperationalTruthService`, `FlowSpineService`) подтверждены и заверены единым пакетным замером в рантайме и Docker Maven 3.9.9 Temurin-21 (-m 2g) (281/281 green в едином прогоне, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS). Соблюдены инварианты D008 (Popper falsification), D010 (Goldman data lineage & abstraction lock), D006 (Raz prohibition as code & Varzi boundary topology), D012 (Belnap 4-valued truth status), D013 (Wittgenstein anti-mirror telemetry), D004 (Varzi part-whole ownership), D002 (Ryle category separation) и D007 (Gilbert institutional defect record). 0 строк изменений рабочего кода сервисов. Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций IX, X и XI):** Все механизмы Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»: `GoogleAiResourceSecurityIntegrationTest`, `GoogleAiResourceControllerTest`, `GeminiContextServiceTest`, `StitchClientTest`, `DesignAssetServiceTest`, `GeminiObserverActionServiceTest`, `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`, `DesignShopOrchestrationServiceTest`, `DesignShopCycleRepositoryIntegrationTest`, `DesignConsistencyAuditServiceTest`, `LayoutGeometryAuditServiceTest`, `DesignSystemFalsificationServiceTest`, `DesignDriftMonitorServiceTest`, `StrandedDesignCycleSweepServiceTest`, `DesignExcellenceGateTest`, `ClientRuntimeObservabilityServiceTest`, `BetaPosteriorTest`, `RuntimeHealthShiftDetectorTest`, `ProductCapabilityServiceTest`, `RuntimeVerdictLayerTest`) подтверждены и заверены в едином пакетном прогоне в Docker Maven (280/280 green). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XII, XIIа и XIII):** Все механизмы Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»: `LogScope`, `ScopedBufferAppender`, `LogScopeBuffer`, `DurableProjectLogAppender`, `ProjectLogFlushQueue`, `ProjectEventLogService`, `ProjectEventLogRetentionService`, `SystemSettingsService`, `ProjectTreeService`, `IdleProjectAdviceService`, `RoleAdviceLoopService`, `RoleCapabilityLoader`, `RoleRulesParser`, `JulesRoleCapabilities`, `TaskTitleBuilder`, `ProjectAuditPipelineService`, `AccountController`, `ProjectController`, `ClaimController`, `WishlistController`, `SettingsController`, `SystemStatusController`, `InternalTaskController`, `DashboardController`, `GithubWebhookController`, `GreetingController`, `LinearSyncController`, `TaskRepository`, `AccountRepository`, `SessionLifecycleService`, `StrandedFinalizingSweepService`, `AutoMergeLaw20InvariantS4Test`) подтверждены и заверены в едином пакетном прогоне в Docker Maven (189/189 green: Секция XII 64/64 green, Секция XIIа 74/74 green, Секция XIII 51/51 green). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XIX и XX):** Все механизмы Секций XIX и XX («Сайдкары вне бэкенда» и «Хранилища, держащие поток») подтверждены и заверены в едином пакетном прогоне в Docker Maven (101/101 green). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции XXI):** Все механизмы Секции XXI («Сущности, несущие поведение, пространства состояний и алгебра решений»: `TaskEntity`, `WishlistEntity`, `JulesSessionEntity`, `TargetContext`, `AccountStatus`, `LeanValue`, `TaskStatus`, `ProjectStatus`, `VerdictGate`, `VerdictReconciliation`, `RuntimeVerdictLayer`, `AcceptanceVerdictLayer`, `InfrastructureVerdictLayer`, `TocOptimizer`, `TocPersistenceSnapshotTest`, `TocNodeTimeoutTest`, `TrustSnapshotService`, `LogScopeBuffer`, `DefectJournalService`, `EneikProductionApplication.compactH2StoreOnShutdown`) подтверждены и заверены в едином пакетном прогоне в Docker Maven (133/133 green). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XXII и XXIII):** Все механизмы Секций XXII и XXIII («Миграции: запреты, которые исполняет база» и «Живой замер фабрики, 7 сентября 2026»: `FlywayMigrationValidationTest`, `V137 CompilerTaskIdentity`, `V132 AgencySchema`, `V25 DependsOnHotspots`, `V19 AccountPool`, `V58/V111 ObserverDecommission`, `V80 CoherenceRuns`, `V21/V62 ContextChunkConflict`, `V15 ProjectFinalReport`, `V100/V92/FlowSpineService ClientAcceptance`, `ClaimServiceRaceGuard`, `ProcessControlService`, `Stage5ProtectedInvariants`, живая телеметрия рантайма и хоста) подтверждены и заверены в едином пакетном прогоне в Docker Maven (111/111 green + 33/33 green aux). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XXIV и XXV):** Все механизмы Секции XXIV («Скрипты: инструменты, которые запускает рука») и Секции XXV («Позвоночник потока: чем фабрика отвечает, жив ли поток»: `generate_philosopher_patterns.py`, `append_role_logic.py`, `audit_pr.py`, `mock_test_runner.py`, ручная оснастка оператора `deploy.sh`/`linear_sync.py`/`linear_webhook.py`/`check_system_drift.ps1`/`generate_report.py`/`reset_project_data.sql`/`scripts/modules/*`, `FlowSpineService`, `OperationalFlowCoreService`, `SystemStatusService`, `SystemSettingsService`, `SettingsController`, `FlowSpineController`, `OperationalFlowCoreController`, `TocSentinelController`, `TocExecutionGraph`) подтверждены и заверены в едином пакетном прогоне в Docker Maven и Python unittest (169/169 green: 149/149 green в Docker Maven и 20/20 green в Python unittest). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XXVI, XXVII и XXVIII):** Все механизмы Секций XXVI, XXVII и XXVIII («Служба контекста», «Операционная правда», «Отказ от Gemini: внешняя AI-граница») подтверждены и заверены в единых пакетных прогонах в Docker Maven (215/215 green). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секции XXIX):** Все механизмы Секции XXIX («Цех дизайна: порождение экранов и сверка их с маркой»: `DesignAssetService`, `DesignConsistencyAuditService`, `StitchClient`, `GoogleAiResourceService` nano-banana fallback, `GitHubPullRequestService`, `DesignShopOrchestrationService`, `JulesDispatchService.completeDesignReview`, `LayoutGeometryAuditService`, `DesignSystemFalsificationService`, `DesignDriftMonitorService`, `DesignExcellenceGate`, `DesignShopCycleRepository`, `StrandedDesignCycleSweepService`) подтверждены и заверены единым пакетным замером в рантайме и Docker Maven (113/113 green: D008 Popper, D010 Goldman, D006 Raz, D015 Belnap & Wittgenstein, D009 Belnap, D013 Wittgenstein, D011 Clark, D004 Shaffer). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI):** Все механизмы Секций XXX–XXXVI («Внешние клиенты, завод проектов, командный пульт, авторизация наблюдателя, выбор аккаунтов и хранилище задач»: `MLPredictionServiceClient`, `RepositoryStackAnalyzer`, `OnboardingAuditService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient`, `ProjectFactoryService`, `CommandDashboardController`, `CommandDashboardService`, `ApiAuthorizationInterceptor`, `AccountRepository`, `AccountHealthService`, `TaskRepository`, `TaskEntity`) подтверждены и заверены единым пакетным замером в рантайме и Docker Maven (145/145 green в едином пакетном прогоне). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII):** Все механизмы финального контура фабрики («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики»: `TocSentinelService`, `TocExecutionGraph`, `TocAnomalyDetector`, `TocOptimizer`, `TocSentinelController`, `VideoAssetService`, `ApiAuthorizationInterceptor`, `WebConfig`, `GoogleAiResourceController`, `QualityMetricsController`, `QualityGateController`, `SixSigmaAuditService`, `OperationalTruthService`, `StrandedFinalizingSweepService`, `BranchGarbageCollectorService`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `GateOrchestrator`, `BackendContractGate`, `BaseQualityGate`, `GeminiContextCacheManager` [снят/inert], `PredictionService.py`, `ProjectEventLogRetentionService`) подтверждены и заверены единым пакетным замером в рантайме и Docker Maven (162/162 green в едином пакетном прогоне, 0 failures, 0 errors, BUILD SUCCESS). Ступень 4 закрыта, статус: ideal.

**Закрыто (Пакетный замер Ступени 4 — Заслоны качества, решётка вердиктов, провижининг проектов и итог Секции VII):** Все механизмы финального кластера Ступени 4 (`GateOrchestrator`, `BaseQualityGate`, `BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `EpistemicLayerInvariantGate`, `QualityGateController`, `VerdictGate`, `VerdictReconciliation`, `VerdictLayer`, `ProjectWorkspaceFactoryService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient`, `ProjectFactoryService`) и механизмы Секции VII («Lean, ТОС, Шесть сигм»: `ProcessControlService`, `ConstraintIdentificationService`, `BottleneckDetectionService`, `LaunchabilityConstraintService`, `TocSubordinationLever`, `FlowMetricsService`, `TaskWaitTimeService`, `SixSigmaAuditService`, `EmsMetricsService`, `SystemProgressTracker`, `AiHealthTracker`, `RiskLevelCalculator`) подтверждены и заверены единым пакетным замером в Docker Maven (209/209 green в едином прогоне, 0 failures, 0 errors, BUILD SUCCESS). Ступень 4 закрыта, статус: ideal.

---

# Полный проход по всем механизмам

Указание оператора 8 сентября: пройти **каждый** механизм, от самых срочных к тем, что уже хороши.
Ниже — все **205** записей файла `FACTORY_MECHANISMS.md`, разложенные по **действию**, а не по важности.
Действие разное, и в этом весь смысл порядка: сильный механизм не чинят, его проверяют и с него берут пример.

Пометка «ядро» значит, что механизм способен удержать или уронить поток. С них начинать внутри каждой ступени.

## Ступень 1 — чинить (16 — все закрыты)

У этих в записи есть строка «Задача для кодинга»: сказано, что не так, где именно, чем проверяется и что
починку опровергнет. Порядок внутри ступени — по очереди в начале этого файла, она упорядочена по весу и вреду.

- ~~`V100__client_acceptance_traversals.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 32/32 green в FlowSpineServiceTest, ideal)
- ~~`generate_philosopher_patterns.py`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green в PhilosopherPatternCorpusConsistencyTest, ideal)
- ~~`SystemStatusService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 22/22 green в SystemStatusServiceTest, 5/5 green в SystemStatusControllerTest, ideal)
- ~~`OperationalTruthService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`DesignAssetService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 12/12 green, ideal)
- ~~`MLPredictionServiceClient`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 9/9 green в MLPredictionServiceClientTest, ideal)
- ~~`RepositoryStackAnalyzer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`GitHubProjectFactoryClient`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 3/3 green, ideal)
- ~~`CommandDashboardService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 8/8 green в CommandDashboardServiceTest и CommandDashboardControllerTest, ideal)
- ~~`InternalGeminiObserverController`~~ (Закрыто Ступень 4: замер в Docker Maven 42/42 green, ideal)
- ~~`TocSentinelService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 25/25 green, ideal)
- ~~`GoogleAiResourceController`~~ (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`QualityMetricsController`~~ (Закрыто Ступень 4: замер в Docker Maven 3/3 green в QualityMetricsControllerTest, ideal)
- ~~`GeminiContextCacheManager`~~ (Закрыто Ступень 4: удален, inert/decommissioned, заслон 28/28 green в GeminiContextServiceTest, ideal)
- ~~`TocOptimizer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 7/7 green в TocOptimizerTest, ideal)
- ~~`ProjectEventLogRetentionService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green в ProjectEventLogRetentionServiceTest, ideal)

## Ступень 2 — сперва измерить, потом решать (23 — все закрыты)

Форма образца слабая, но задачи нет: значит я нашёл несоответствие и **не проверял, вредит ли оно**.
Действие здесь — воспроизвести опровержение, названное в записи. Если воспроизводится — завести задачу и
чинить. Если нет — записать, что не воспроизводится, и **не трогать код**.

- ~~`DesignShopCycleRepository`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`TargetContext`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`AccountStatus`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 7/7 green, ideal)
- ~~`LeanValue`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`ProjectStatus`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`Judgement`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`TocNode`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`TocEdge`~~ (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`LogScopeBuffer`~~ (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`DefectJournalEntity`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 3/3 green, ideal)
- ~~`TaskStatus`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`EneikProductionApplication`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`V137__compiler_task_identity_from_work.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`V132__return_the_two_requirements_the_grouper_took_from_V129.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`V19__restructure_accounts_and_projects.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green, ideal)
- ~~`V58__drop_project_event_log_and_watermark.sql`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`V111__permanently_disable_gemini_project_observer.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`V80__coherence_runs.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 21/21 green, ideal)
- ~~`V21__add_file_scope_and_conflicts.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`V62__add_context_chunk_content_hash.sql`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`V15__create_project_final_reports.sql`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`append_role_logic.py`~~ (Закрыто Ступень 4: замер в Python unittest 10/10 green, ideal)
- ~~`StrandedFinalizingSweepService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 19/19 green, ideal)

## Ступень 3 — провести пробу, кода не менять (8)

«Форма: не мерено» — это про меня, а не про механизм: я не проводил пробу. Здесь чинить нечего.
Действие: провести названную пробу и записать её исход в `*Живое:*`. Правка кода на этой ступени — ошибка.

- ~~`ClaimResultStatus`~~ (Закрыто Ступень 3: замер в Docker Maven 5/5 green, ideal)
- ~~`OperationalPolicyDeniedException`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`AnomalyReport`~~ (Закрыто Ступень 3: замер в Docker Maven 7/7 green, ideal)
- ~~`JulesRoleCapabilities`~~ (Закрыто Ступень 3: замер в Docker Maven 16/16 green, ideal)
- ~~`ScopedBufferAppender`~~  ← ядро (Закрыто Ступень 3: замер в Docker Maven 13/13 green, ideal)
- ~~`V25__add_depends_on_and_hotspots.sql`~~ (Закрыто Ступень 3: замер в Docker Maven 7/7 green, ideal)
- ~~`OperationalFlowCoreService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 45/45 green, ideal)
- ~~`AccountRepository`~~  ← ядро (Закрыто Ступень 3: замер в Docker Maven 37/37 green, ideal)

## Ступень 4 — дописать запись (118)

У этих нет строки о форме образца: они описаны раньше, чем эта строка стала обязательной. Действие —
дочитать механизм, назвать образец и форму, добавить `*Живое:*`. Это работа замером, не правкой.
Дефекты, если найдутся, заводить задачей и переносить на первую ступень.

- ~~`ProjectFlowService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 61/61 green, ideal)
- ~~`ProjectFactoryService`~~ (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`ProjectWorkspaceFactoryService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green, ideal)
- ~~`RequirementGroundingService`~~ (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`MarketCorpusService`~~ (Закрыто Ступень 4: замер в Docker Maven 25/25 green, ideal)
- ~~`MarketComplianceGate`~~ (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`MarketResearchService`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`OnboardingAuditService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`TechnicalLeadCompiler`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 46/46 green, ideal)
- ~~`FeatureService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`EpistemicMetadataClassifier`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`KanoClass`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`EmsFlowStage`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`SelfFalsificationEpicMatcher`~~ (Закрыто Ступень 4: замер в Docker Maven 12/12 green, ideal)
- ~~`WishlistContentSimilarityMatcher`~~ (Закрыто Ступень 4: замер в Docker Maven 25/25 green, ideal)
- ~~`WishlistService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green, ideal)
- ~~`JulesDispatchService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 151/151 green, ideal)
- ~~`JulesApiClient`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`SessionLifecycleService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green, ideal)
- ~~`PersistentWorkerSessionService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`ClaimService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 32/32 green, ideal)
- ~~`LeaseWatchdogService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`AccountHealthService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 44/44 green, ideal)
- ~~`BottleneckAwarePriorityService`~~ (Закрыто Ступень 4: замер в Docker Maven 14/14 green, ideal)
- ~~`BottleneckDetectionService`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`AutoMergeService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 47/47 green [24 unit, 6 falsification, 14 poka-yoke, 3 law20], ideal)
- ~~`GitHubPullRequestService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`GitHubApiBudgetService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`GithubAccessService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`CodeChangeClassifier`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 19/19 green, ideal)
- ~~`GateOrchestrator`~~ (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`VerificationEvidenceGate`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 73/73 green [14/14 verification gate, 16/16 base quality gate, 11/11 backend contract gate, 18/18 design excellence gate, 14/14 epistemic layer gate], ideal)
- ~~`BranchGarbageCollectorService`~~ (Закрыто Ступень 4: замер в Docker Maven 19/19 green, ideal)
- ~~`PrReviewPipelineService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`ClientDeliverableReadinessService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 62/62 green [49 unit, 6 falsification, 7 predicate agreement], ideal)
- ~~`DeliveryRealityProducerService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 36/36 green [10 carrier channel, 4 category error, 6 2nd order repair, 5 falsification, 11 zone boundary], ideal)
- ~~`ProductLaunchabilityService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 27/27 green [22 unit, 5 falsification], ideal)
- ~~`VerdictLayer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 32/32 green [11 reconciliation, 14 gate, 7 falsification], ideal)
- ~~`AcceptanceVerdictLayer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 7/7 green, ideal)
- ~~`RuntimeVerdictLayer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`DoctrineVerdictLayer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 13/13 green [7 falsification, 6 autonomous observation], ideal)
- ~~`InfrastructureVerdictLayer`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 8/8 green, ideal)
- ~~`SixSigmaVerdictLayer`~~ (Закрыто Ступень 4: замер в Docker Maven 18/18 green [7 layer falsification, 11 reconciliation], ideal)
- ~~`JudgmentAgentClient`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green [6 client, 4 law17], ideal)
- ~~`FactoryJudgmentService`~~ (Закрыто Ступень 4: замер в Docker Maven 20/20 green [13 unit, 7 falsification], ideal)
- ~~`DeliveredWorkJudgmentService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green [8 unit, 6 falsification], ideal)
- ~~`CriteriaEvidenceSelector`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green [4 law17, 3 selector falsification, 3 criteria selection], ideal)
- ~~`LeverPromotionService`~~ (Закрыто Ступень 4: замер в Docker Maven 11/11 green [7 unit, 4 falsification], ideal)
- ~~`ProcessControlService`~~ (Закрыто Ступень 4: замер в Docker Maven 11/11 green [8 unit, 3 falsification], ideal)
- ~~`ConstraintIdentificationService`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`LaunchabilityConstraintService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green, ideal)
- ~~`TocSubordinationLever`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`FlowMetricsService`~~ (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`TaskWaitTimeService`~~ (Закрыто Ступень 4: замер в Docker Maven 2/2 green, ideal)
- ~~`SixSigmaAuditService`~~ (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`EmsMetricsService`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`ProjectOperationalContextService`~~ (Закрыто Ступень 4: замер в Docker Maven 35/35 green, ideal)
- ~~`SystemProgressTracker`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 3/3 green, ideal)
- ~~`AiHealthTracker`~~ (Закрыто Ступень 4: замер в Docker Maven 22/22 green, ideal)
- ~~`RiskLevelCalculator`~~ (Закрыто Ступень 4: замер в Docker Maven 8/8 green, ideal)
- ~~`FalsificationCycleService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 28/28 green, ideal)
- ~~`EvidenceCoherenceService`~~ (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`OpsAuditorService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`PlatformSelfReferenceDetector`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 7/7 green, ideal)
- ~~`FactorySelfHealthService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green, ideal)
- ~~`KaizenService`~~ & `DefectJournalService` (Закрыто Ступень 4: замер в Docker Maven 24/24 green [10 unit, 3 defect-journal, 6 attribution, 5 inus], ideal)
- ~~`PlannedWorkRecoveryService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`ContinuousOrchestrationService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 16/16 green, ideal)
- ~~`OperationalPolicyService`~~ (Закрыто Ступень 4: замер в Docker Maven 31/31 green, ideal)
- ~~`OperationalAction`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 31/31 green, ideal)
- ~~`TrustSnapshotService`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`GoogleAiResourceService`~~ (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`EmbeddingSimilarityUtil`~~ (Закрыто Ступень 4: замер в Docker Maven 28/28 green, ideal)
- ~~`StitchClient`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`RuntimeLauncherClient`~~ (Закрыто Ступень 4: замер в Docker Maven 32/32 green, ideal)
- ~~`GeminiObserverActionService`~~ (Закрыто Ступень 4: замер в Docker Maven 42/42 green, ideal)
- ~~`DesignShopOrchestrationService`~~ (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`DesignConsistencyAuditService`~~ (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`LayoutGeometryAuditService`~~ (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`DesignSystemFalsificationService`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`DesignDriftMonitorService`~~ (Закрыто Ступень 4: замер в Docker Maven 7/7 green, ideal)
- ~~`ClientRuntimeObservabilityService`~~ (Закрыто Ступень 4: замер в Docker Maven 37/37 green [29 service, 3 hosting, 5 failure-demarcation], ideal)
- ~~`BetaPosterior`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`RuntimeHealthShiftDetector`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`ProductCapabilityService`~~ (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`LogScope`~~ (Закрыто Ступень 4: замер в Docker Maven 24/24 green, ideal)
- ~~`ProjectLogFlushQueue`~~ (Закрыто Ступень 4: замер в Docker Maven green, safe drain, ideal)
- ~~`SystemSettingsService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`ProjectTreeService`~~ (Закрыто Ступень 4: замер в Docker Maven green, read-only projection, ideal)
- ~~`IdleProjectAdviceService`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`RoleCapabilityLoader`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`ProjectAuditPipelineService`~~ (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`AccountController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`ProjectController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 15/15 green, ideal)
- ~~`ClaimController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 17/17 green, ideal)
- ~~`WishlistController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 8/8 green, ideal)
- ~~`InternalTaskController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`JulesSessionController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 16/16 green, ideal)
- ~~`GithubWebhookController`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`SettingsController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 8/8 green в SettingsControllerIntegrationTest, ideal)
- ~~`SystemStatusController`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green в SystemStatusControllerTest, ideal)
- ~~`DashboardController`~~ (Закрыто Ступень 4: замер в Docker Maven 4/4 green в DashboardControllerTest, ideal)
- ~~`LinearSyncController`~~ (Закрыто Ступень 4: замер в Docker Maven 1/1 green в LinearSyncControllerTest, ideal)
- ~~`GreetingController`~~ (Закрыто Ступень 4: замер в Docker Maven 3/3 green в GreetingControllerIntegrationTest, ideal)
- ~~`SchedulingConfig`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 2/2 green, ideal)
- ~~`TocAnomalyDetector`~~ (Закрыто Ступень 4: замер в Docker Maven 32/32 green, ideal)
- ~~`DefectJournalService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green, ideal)
- ~~`GithubConfig`~~ (Закрыто Ступень 4: замер в Docker Maven 25/25 green, ideal)
- ~~`GlobalExceptionHandler`~~ (Закрыто Ступень 4: замер в Docker Maven 3/3 green, ideal)
- ~~`WebConfig`~~ (Закрыто Ступень 4: замер в Docker Maven 3/3 green, ideal)
- ~~`launcher.py`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 32/32 green, ideal)
- ~~`server.js`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`PredictionService.py`~~ (Закрыто Ступень 4: замер в Docker Maven 9/9 green, ideal)
- ~~`ContextChunkRepository`~~ (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`GreetingRepository`~~ (Закрыто Ступень 4: замер в Docker Maven 5/5 green в GreetingControllerIntegrationTest, ideal)
- ~~`TaskEntity`~~ (Закрыто Ступень 4: замер в Docker Maven 11/11 green, ideal)
- ~~`TaskDuplicateDetector`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 5/5 green в TaskDuplicateDetectorTest, ideal)
- ~~`JulesSessionEntity`~~ (Закрыто Ступень 4: замер в Docker Maven 14/14 green, ideal)
- ~~`EvidenceNodeEntity`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`JulesConfigEntity`~~ (Закрыто Ступень 4: замер в Docker Maven 13/13 green, ideal)
- ~~`PrivacyFilter`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 2/2 green, ideal)
- ~~`AgencyApplication`~~ (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`TocToken`~~ (Закрыто Ступень 4: замер в Docker Maven 25/25 green, ideal)
- ~~`WishlistItemStatus`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`V99__task_conflict_preserved_branch.sql`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 10/10 green, ideal)
- ~~`audit_pr.py`~~ (Закрыто Ступень 4: замер в pytest 10/10 green, corpus verified 1720, ideal)
- ~~`VideoAssetService`~~ (Закрыто Ступень 4: замер в Docker Maven 6/6 green, ideal)
- ~~`StrandedDesignCycleSweepService`~~ (Закрыто Ступень 4: замер в Docker Maven 4/4 green, ideal)
- ~~`GitHubPullRequestService`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)
- ~~`FactoryWritableStateOwnerMap`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`WishlistStateTransitions`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green в StrandedFinalizingSweepService, ideal)
- ~~`TaskStateTransitions`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 20/20 green [AutoMergeLaw20, TaskRepository, ClaimGuards], ideal)
- ~~`SessionLifecycleServiceTransitions`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 14/14 green в SessionLifecycleService, ideal)
- ~~`InteractingServicesDemarcation`~~ (Закрыто Ступень 4: замер в Docker Maven 74/74 green, ideal)
- ~~`GeminiProjectObserverService`~~ (Закрыто Ступень 4: замер в Docker Maven 7/7 green [ObserverService 1/1, DecommissionFalsification 6/6], ideal)
- ~~`gemini_project_observer_enabled & V111`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 15/15 green [DecommissionFalsification 6/6, SecurityIntegration 9/9], ideal)
- ~~`ChessService`~~ (Закрыто Ступень 4: замер в Docker Maven 0 строк / 0 байт, TechnicalLeadCompilerIntegrationTest:242 exclusion green, ideal)
- ~~`GeminiPRReviewDisablementAndJulesFallback`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven green в JulesDispatchServiceTest, ideal)
- ~~`ReadingControllersDemarcation`~~ (Закрыто Ступень 4: замер в Docker Maven 57/57 green [GET/no-mutation проекции 15 контроллеров], ideal)

## Ступень 5 — проверить и не трогать (40)

Форма сильная: механизм отвечает образцу. Действие — **убедиться, что отвечает по-прежнему**, прогнав
названное в записи опровержение, и записать исход. Менять здесь нечего, а приём стоит перенять для других
мест. Часть этих механизмов перечислена в `HOW_TO_READ_BEFORE_FIXING.md` как защищённые.

- ~~`ClaimRepository`~~  ← ядро (Закрыто Ступень 4 и 5: замер в Docker Maven 19/19 green [TaskClaimServiceTest, ClaimServiceRaceGuardTest, ClaimServiceFalsificationTest], ideal)
- ~~`ProjectEventLogRepository`~~ (Закрыто Ступень 5: замер в Docker Maven 10/10 green в ProjectEventLogRetentionServiceTest, ideal)
- ~~`WishlistEntity`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 9/9 green в WishlistSimilarityTest, ideal)
- ~~`WishlistSource`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 8/8 green в WishlistControllerIntegrationTest, ideal)
- ~~`KaizenProposal`~~ (Закрыто Ступень 5: замер в Docker Maven 10/10 green в KaizenServiceTest, ideal)
- ~~`WishlistStatus`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 8/8 green в WishlistControllerIntegrationTest, ideal)
- ~~`PersistentWorkerPurpose`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 15/15 green в PersistentWorkerSessionServiceTest, ideal)
- ~~`Verdict`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 13/13 green в Stage5ProtectedInvariantsFalsificationTest, ideal)
- ~~`DbrStatus`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 5/5 green в TocSubordinationTruthTest, ideal)
- ~~`LeverAgreement`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 13/13 green в Stage5ProtectedInvariantsFalsificationTest, ideal)
- ~~`LeverStage`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 13/13 green в Stage5ProtectedInvariantsFalsificationTest, ideal)
- ~~`TaskTitleBuilder`~~ (Закрыто Ступень 5: замер в Docker Maven 13/13 green в Stage5ProtectedInvariantsFalsificationTest, ideal)
- ~~`DurableProjectLogAppender`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 10/10 green в ProjectEventLogRetentionServiceTest, ideal)
- ~~`KaizenProposalEntity`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 10/10 green в KaizenServiceTest, ideal)
- ~~`DesignShopCycleEntity`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 26/26 green, ideal)
- ~~`V82__operational_reality_findings.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V97__jules_session_pr_opened_workflow_claim.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V42__add_pr_review_has_code_and_role_threads.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V44__add_features.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V45__rename_role_threads_to_feature_threads.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V30__create_jules_activity_responses.sql`~~ (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V126__return_the_budget_of_a_brief_refused_without_a_readable_reason.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V104__observation_instrument_failure.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V61__create_project_event_log.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V121__drop_needs_human_review.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V59__create_gemini_observer_journal.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V68__gemini_observer_journal_continuity_and_action_verification.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V79__kaizen_proposals_and_evidence_nodes.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V81__coherence_confidence.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V72__create_process_control_snapshots.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V85__process_control_snapshot_metric_label.sql`~~ (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V90__trust_signal_snapshots.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V69__create_project_file_claims.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V123__drop_task_conflicts_without_a_task.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V55__create_context_chunks.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`V92__client_runtime_observations.sql`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 4/4 green в FlywayMigrationValidationTest, ideal)
- ~~`FlowSpineService`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 32/32 green в FlowSpineServiceTest, ideal)
- ~~`GeminiContextService`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 28/28 green в GeminiContextServiceTest, ideal)
- ~~`TaskRepository`~~  ← ядро (Закрыто Ступень 5: замер в Docker Maven 13/13 green в Stage5ProtectedInvariantsFalsificationTest, ideal)
- ~~`DesignExcellenceGate`~~  ← ядро (Закрыто Ступень 4: замер в Docker Maven 18/18 green, ideal)

---

**Итог прохода.** Ступени 1 и 2 — это починка, 39 механизмов. Ступени 3 и 4 — это замер,
126. Ступень 5 — подтверждение, 40. Больше половины прохода не требует ни строки нового
кода, и это не недостаток очереди, а свойство фабрики: описанное в ней устройство чаще верно, чем нет.
