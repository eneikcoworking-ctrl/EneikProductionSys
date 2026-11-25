# L1 Эпистемическая диагностика, контракты и карта ответственных механизмов: WO #13 (исключение Google Jules) и WO #14 (накопление и консолидация вишлистов замороженных проектов)

**Дата:** 2026-11-25 13:05 UTC  
**Аудитор:** L1 Менеджер-философ (`remotecli2`)  
**Основание:** Срочная директива Оператора Дмитрия Ефремова от `2026-11-25 11:55:41 UTC` ([OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md#L110-L112)):
> «Джулсы полностью убрать из цепочки. Вишлисты для замороженных проектов накапливать и при разморозке — группировать и обобщать. Остальное пока не трогать.»

**Контекст экосистемы:** [ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md) (ядро Фабрики — 207 механизмов, BARCAN-TAG, Memorilla $K=16$, суверенный барьер `GEMINI.md §0`, запрет ручных правок продуктов, защита контура Деннетта `SLOT-14`).

---

## 1. Эмпирическая телеметрия хоста, Фабрики и продукта (`2026-11-25 13:00 UTC`)

Принцип анти-зеркала Витгенштейна (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, `D013`): все выводы основаны строго на живых замерах хоста, контейнеров, HTTP API и исходного кода:

- **Ресурсная гигиена хоста (`GEMINI.md §1`):**
  - Диск `/`: **`64%`** (`23G / 38G`, `14G` свободно — строго `< 70%`).
  - RAM: `1.1Gi` доступно (`3.7Gi` total, `Swap 2.0Gi / 4.0Gi`).
- **Контейнеры и health-пробы:**
  - `eneikproductionsys-backend-1` (`:8080/actuator/health`): `UP` (`Up 13 hours`).
  - `eneikproductionsys-judgment-proxy-1` (`:8093/health`): `ok=true`, `primary="deepseek"`, `activeProvider="deepseek"`, `deepseekAvailable=true`, `geminiAvailable=false`.
  - `eneikproductionsys-ml-1` (`:8000`): `healthy`.
  - `dmitryefremov-backend` (`:18080/actuator/health`): `UP` (`healthy`), `dmitryefremov-db` `healthy`.
- **Живые дефекты, обнаруженные в рантайме и БД (`11:39–13:00 UTC`):**
  1. **Jules не убран из живого рантайма:**
     - `SessionLifecycleService.cleanupEligibleSessions` (`@Scheduled` каждые 30 мин) продолжает отрабатывать в живом логе (`11:54:07Z`, `12:24:07Z`, `12:54:07Z`: `processed 30 eligible session(s) for remote cleanup this cycle`).
     - `AccountHealthService` каждые 15 мин (`11:39Z..12:54Z`) пишет `WARN` по legacy Jules-аккаунту `'eneikcoworking-ctrl'` (`disabled for 563h ... предлагается к возврату в пул оператором`).
     - В `/api/settings` ключ `jules_enabled` (`source=database`, `enabled=false`) и `jules_api_key` (`****iGlA`) остаются редактируемыми через API/UI; `worker_engine_mode=HYBRID` всё ещё имеет Tier 3 fallback на `GOOGLE_JULES` в `WorkerExecutionRouter`.
  2. **Критическая находка в таблице `accounts` (потенциальная мина для L2!):**
     - `deepseek-worker-1..8`: `provider="DEEPSEEK"`, `status=idle`, `enabled=true`.
     - **`remotecli3..10` (суверенные воркеры `LOCAL_AGY`!):** в живой БД имеют **`provider="GOOGLE_JULES"`**, `status=idle`, `enabled=true`!
     - 25 старых облачных Jules-аккаунтов (`EneikGroup`, `dmitrefrem-eneik`, `eneikdru`, `fivedmitr-sys`, `sixdmitrsix-ops`, `dmitriieneik-rgb`, `eneikcoworking-ctrl`): `provider="GOOGLE_JULES"`, `status=decommissioned` (кроме 1 строки `eneikcoworking-ctrl` со `status=idle, enabled=false`).
     - **Следствие:** наивная фильтрация `provider <> 'GOOGLE_JULES'` без предварительной миграции `remotecli3..10` на `provider='LOCAL_AGY'` **сломала бы суверенный пул `LOCAL_AGY`**!
  3. **Состояние проектов и вишлистов в живой БД:**
     - `d64a3b6b` (`knowledge-base-crie`): `status=frozen`, 231 вишлист (`220 converted_to_task`, `8 dismissed`, **`3 pending`**):
       - `caa495f4` (`product_not_launchable`, `BARCAN-TAG-00`, `2026-11-13`)
       - `e41fc341` (`client`, `2026-11-24 22:29Z`: `[Java 21 / Spring Boot 3 Production Parity Gate] Целевой стек бэкенда...`)
       - `62f73c58` (`client`, `2026-11-24 22:50Z`: `[Java 21 Spring Boot 3 Backend | Live Production Parity Gate] Implement the missing Java 21 / Spring...`)
       — живой эмпирический пример двух смыслово пересекающихся клиентских брифов на замороженном проекте!
     - `3e9c8cad` (`test-fifty-first`): `status=frozen`; `a716e82e` (`test-fiftieth`): `status=frozen`; `ef264aea` (`designer-eneik`): `status=accepted`; `1695f808` (`dmitryefremov-site`): `status=accepted`.
     - Бэкап всех 1185 вишлистов до правок зафиксирован: `docs/reports/wishlist-backups/wishlist_all_projects_20261125T1152Z.json` (`4fec21df`).

---

## 2. Философский корпус (верифицировано в `docs/philosopher-patterns/philosophers/`)

| Код дефекта | Верифицированный паттерн | Инженерный смысл и обязательство доказательства |
|---|---|---|
| `D013` Runtime drift | `ELVIN_GOLDMAN_06_CAUSAL_PROCESS_TRACE` | Полная каузальная трасса `trigger → mechanism → state change`: ни один роутер, cron, fallback-ревью или селектор аккаунтов не может дотянуться до Google Jules API. |
| `D006` Authorization ambiguity | `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` | Исполняемый запрет на уровне кода: `eneik.jules.enabled=false` по умолчанию, отсутствие пути включения через UI/DB `system_settings`, мгновенный отказ в `JulesApiClient` и `WorkerExecutionRouter`. |
| `D013` Runtime drift | `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` | Приёмка по живым логам контейнера: `0` вызовов Jules API, `0` запусков `SessionLifecycleService` remote cleanup, `0` предупреждений `AccountHealthService` по Jules-аккаунтам. |
| `D010` Data lineage loss | `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` | Каждое обобщённое пожелание после разморозки хранит полную цепочку происхождения (`source_wishlist_ids`, источники, роли, временные метки); исходные записи не удаляются и ссылаются через `merged_into_wishlist_id`. |
| `D012` Policy contradiction / `D002` Invalid state | `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` | Явное разграничение состояний: `deferred_frozen` (накопление без декомпозиции), `consolidated` (поглощённый исходник), `pending` (обобщённый бриф к декомпозиции); противоречащие пожелания (`BOTH`) никогда не сливаются молча, а помечаются явным маркером конфликта. |
| `D008` False green | `KARL_POPPER_01_FALSIFICATION_HARNESS` | На каждый инвариант — воспроизводимый фальсификационный тест в Docker Maven (`-m 2g`) с сохранением 100% зелёного `DennettAutonomyFalsificationTest`. |

---

## 3. Диагностика и канонический контракт WO #13 (Механизм №15: полное исключение Google Jules)

### 3.1. Диагностированные точки утечки к Google Jules в коде

1. **`WorkerExecutionRouter.java` (`lines 24, 75-193`):**
   - `resolveEngine` / `dispatch` / `dispatchLocalAgyOrJules` / `poll` / `cancel` всё ещё используют Tier 3 `JulesExecutionEngine` (`GOOGLE_JULES`).
   - При сбое/недоступности `DeepSeek` и `LOCAL_AGY` роутер проваливается в `julesEngine.dispatch(task, null, mode)`.
2. **`JulesDispatchService.java` (КРИТИЧЕСКАЯ АРХИТЕКТУРНАЯ ГРАНИЦА!):**
   - Несмотря на историческое имя класса, **`JulesDispatchService` обслуживает суверенный конвейер (`DeepSeek` + `LOCAL_AGY`)**:
     - `dispatch(...)` (`lines 698-785`) маршрутизирует суверенные задачи в `WorkerExecutionRouter`.
     - `@Scheduled reconcileStrandedPrOpenedWorkflows` (`line 2231`), `@Scheduled processPendingReviewBatch` (`line 2472`), `@Scheduled detectStuck` (`line 5262`), `@Scheduled reconcileTaskStatusAgainstGitHubTruth` (`line 5664`) обслуживают **все** задачи фабрики (включая `DeepSeek` и `LOCAL_AGY`).
   - **Нельзя** отключать бин `JulesDispatchService` целиком или его 4 общих `@Scheduled`-цикла!
   - **Нужно** хирургически отсечь именно облачные вызовы Google Jules внутри него:
     - fallthrough после `WorkerExecutionRouter` (`lines 787-980`, `julesApiClient.createSessionDetailed`);
     - `dispatchInternal` / `createSessionDetailed` (`line 396`);
     - `PendingFallbackReview` в `processPendingReviewBatch` (`lines 2489, 2530+`) — не создавать Jules-сессии ревью;
     - `pollStatus` (`lines 1200-1240`) — не опрашивать legacy non-sovereign сессии через `julesApiClient`;
     - все вызовы `julesApiClient.sendMessage` / `getSessionActivities` / `checkSessionRaw` (`lines 272, 306, 1426, 1498, 2902, 3221, 4304, 5150, 5215, 5968, 6060`).
3. **`SessionLifecycleService.java` (`lines 105-192`):**
   - `@Scheduled cleanupEligibleSessions` (`line 167`) продолжает слать/пытаться слать remote DELETE каждые 30 мин.
   - При этом `SessionLifecycleService` инъектируется в `BranchGarbageCollectorService` (`line 45`) и `JulesDispatchService` для локальной отмены (`retireSessionOnly` / `applyLocalCancelStatus`), поэтому сам бин должен оставаться доступным для локальной отмены, а удалённый Jules-планировщик и `deleteRemote` — выключены при `eneik.jules.enabled=false`.
4. **`JulesApiClient.java` (`lines 80-727`) и `SystemSettingsService.java` (`line 472`):**
   - `jules_enabled` сейчас управляется через БД `system_settings` (есть путь включения через UI/API).
   - Требуется жёсткий конфиг-заслон `eneik.jules.enabled=false` (в `application.properties`, по умолчанию `false`), запрет включения `jules_enabled=true` через `SystemSettingsService`/UI и безусловный short-circuit всех HTTP-методов в `JulesApiClient`.
5. **`AccountRepository.java` (`lines 27-345`) + `AccountHealthService.java` (`lines 513-532`) + `ContinuousOrchestrationService.java` (`lines 993-1060`):**
   - **Миграция `V147` обязательна ДО фильтрации `GOOGLE_JULES`:** перевести `remotecli3..10` с `provider='GOOGLE_JULES'` на `provider='LOCAL_AGY'`, а оставшиеся legacy `GOOGLE_JULES`-записи (включая `eneikcoworking-ctrl`) пометить `status='decommissioned', enabled=false` **без удаления строк**.
   - Селекторы в `AccountRepository` исключают `provider='GOOGLE_JULES'`; `AccountHealthService` не пытается реанимировать и не предупреждает о `GOOGLE_JULES`-аккаунтах; `ContinuousOrchestrationService.resetDailyLimitedAccounts` и `recoverStaleBlockedAccounts` продолжают работать для `DEEPSEEK` и `LOCAL_AGY`, а `pollActiveJulesSessions` пропускает non-sovereign legacy Jules-сессии.
6. **`InternalJulesActivitiesProbeController.java` (`line 81`):** возвращает `410 Gone` / отказ при `eneik.jules.enabled=false`.
7. **Тестовый контур Деннетта:**
   - `DennettAutonomyFalsificationTest` (`6/6`) должен остаться **100% green** без ослабления.
   - `DennettCranesHomeostasisTest` (сейчас проверяет failover DeepSeek → `JulesExecutionEngine`) обязан быть обновлён под суверенный failover `DeepSeek → LOCAL_AGY` и честный отказ без вызова `JulesExecutionEngine` при недоступности обоих суверенных движков.

### 3.2. Ответственные механизмы WO #13
- `WorkerExecutionRouter` + `JulesExecutionEngine` + `WorkerEngineType` (маршрутизация строго `DeepSeek → LOCAL_AGY`).
- `JulesApiClient` + `SystemSettingsService` + `application.properties` (единая точка деонтического запрета `eneik.jules.enabled=false`, 0 путей включения из UI).
- `SessionLifecycleService` + `JulesDispatchService` + `ContinuousOrchestrationService` + `InternalJulesActivitiesProbeController` (отсечение всех Jules API расписаний и вызовов без нарушения суверенного конвейера).
- `V147__decommission_jules_cloud_accounts_and_retag_local_agy.sql` + `AccountRepository` + `AccountHealthService` (сохранение истории, защита `remotecli3..10`, исключение `GOOGLE_JULES` из claim/lease).

---

## 4. Диагностика и канонический контракт WO #14 (Механизм №16: накопление и консолидация вишлистов замороженных проектов)

### 4.1. Диагностированные разрывы в коде и схеме БД

1. **Статусы проекта (`ProjectStatus.java:13-20`):**
   - Перечисление содержит ровно 7 значений: `active, analyzing, waiting, frozen, accepted, archived, stalled`. Отдельного статуса `paused` в enum нет — операторская пауза (`ProjectFlowService.pauseProject`, `line 631`) переводит проект именно в `ProjectStatus.frozen`!
   - **Контракт:** накопление действует для `ProjectStatus.frozen` (а также `waiting`, если проект временно не операционный; `accepted`, `archived`, `analyzing` по-прежнему отклоняют новые вишлисты).
2. **Блокировка приёма вишлистов на замороженный проект:**
   - `ProjectFlowService.addWishlistItem` (`line 805`): `requireActiveProject(projectId)` (`line 7365`) уже пропускает `frozen` (отклоняет только `accepted` и `analyzing`), но на строке `806` вызов `operationalPolicyService.requireAllowed(projectId, OperationalAction.ADD_WISHLIST)` **блокирует** приём, т.к. `OperationalPolicyService.java:116` требует `case ADD_WISHLIST -> activeProject && !terminal;`!
   - При этом `ProjectStatus.frozen.isOperational()` (`ProjectStatus.java:40`) обязан оставаться **`false`** (инвариант `DennettAutonomyFalsificationTest:88-115`: для `frozen` запрещены `ORCHESTRATE`, `DISPATCH_*`, `MERGE_PR`, `RECOVER_FAILED_FRONTIER`).
3. **Статусы вишлиста и колонка `wishlist.status` (`WishlistStatus.java`, `V14`):**
   - Сейчас `WishlistStatus` = `{pending, compiling, finalizing, converted_to_task, dismissed}` (колонка `VARCHAR(24)`).
   - Требуется добавить 2 статуса в `WishlistStatus` (нижний регистр, единый стиль enum, длина `<= 24`):
     - **`deferred_frozen`** (15 симв.) — вишлист принят в замороженный проект и накапливается (НЕ декомпозируется, НЕ отклоняется; `WishlistEntity.movable()` возвращает `false`, поэтому ни компилятор, ни `FlowSpineService` не запускают декомпозицию).
     - **`consolidated`** (12 симв.) — исходный накопленный вишлист, поглощённый обобщённой записью при разморозке (НЕ удаляется, сохраняет `content`, `source`, `source_role_tag`, `created_at`).
   - Новые колонки lineage (Flyway **`V148__wishlist_frozen_consolidation_lineage.sql`**):
     - `merged_into_wishlist_id UUID NULL REFERENCES wishlist(id) ON DELETE SET NULL` (ссылка от поглощённого исходника на обобщённую запись — формализованный аналог `MERGED_INTO=<id>`).
     - `consolidated_from_ids TEXT NULL` (JSON-массив / CSV UUID исходных записей в обобщённой записи — прямая трасса lineage).
     - `consolidation_conflict BOOLEAN NOT NULL DEFAULT FALSE` (явный флаг обнаруженного противоречия по Белнапу `BOTH`).
     - `consolidation_notes TEXT NULL` (обоснование группировки и список обнаруженных противоречий).
     - Индекс `idx_wishlist_project_merged_into ON wishlist(project_id, merged_into_wishlist_id)`.
4. **Недостаточность существующего `ProjectFlowService.groupSimilarWishlistItems` (`lines 7528-7745`):**
   - Существующий метод `groupSimilarWishlistItems` использует связные компоненты графа по Jaccard-сходству (`>= 0.25`) и уже защищает скомпилированные слайсы (`originWishlistId`), **но**:
     - он помечает дубли как `dismissed` без `merged_into_wishlist_id` (потеря lineage, `D010`);
     - он мутирует первый элемент `survivor` вместо создания чистого обобщённого пожелания с явными ссылками на все исходные `id`, авторы/роли и таймстампы;
     - он не обнаруживает противоречия между пожеланиями (молча склеивает через `---`, нарушая Белнапа `D012`);
     - он не вызывается на границе разморозки (`activateProject`) и не обрабатывает `deferred_frozen`.
5. **Точка разморозки и идемпотентность (`ProjectFlowService.activateProject`, `lines 575-605` + `executeOrchestrate`, `line 871`):**
   - При переходе проекта из `frozen` в `active` (в `activateProject`) — и как страховочный идемпотентный этап в `executeOrchestrate` **до** любой декомпозиции — запускается **`FrozenWishlistConsolidationService.consolidateForProject(projectId)`**:
     1. Выбирает все `deferred_frozen` записи проекта + любые старые нескомпилированные `pending` записи проекта, у которых `originWishlistId == null` и `consolidatedFromIds == null` (это автоматически и безопасно подхватит 3 ожидающие записи в `knowledge-base-crie` `caa495f4`, `e41fc341`, `62f73c58` без ручного вмешательства!).
     2. Группирует записи по смыслу (нормализованное сходство токенов / доменных якорей + совместимость `source`/`targetContext`, строго сохраняя защиту от слияния разных `sourceTaskId` и разных скомпилированных слайсов).
     3. Проверяет каждую группу на явные противоречия (антонимичные директивы: например, «добавить/включить/оставить X» vs «удалить/отключить/запретить X», конфликтующие численные/технологические требования по одной сущности). Противоречащие записи **НЕ сливаются молча**: в обобщённой записи выставляется `consolidationConflict = true` и явный блок `[CONFLICT DETECTED]` с цитированием конфликтующих `id`, дат и позиций (или разделением с перекрёстной ссылкой — ни одна позиция не теряется).
     4. Для каждой группы (включая одиночную, чтобы статус перешёл из `deferred_frozen` в `pending` с прослеженным lineage) создаёт **одну** обобщённую запись со статусом `pending`, `consolidatedFromIds = [...]`, сводным содержанием, метаданными источников/ролей/дат; все исходные записи переводит в `WishlistStatus.consolidated` с `mergedIntoWishlistId = consolidated.id` (ничего не удаляется).
     5. **Идемпотентность:** повторный вызов консолидации (повторная разморозка или повторный тик `executeOrchestrate`) видит `0` записей в `deferred_frozen` и все обобщённые записи уже имеют `consolidatedFromIds != null` → возвращает `0`, дублей не создаётся.
   - **Согласованность знаменателей (`ClientDeliverableReadinessService` + `FlowSpineService` + `OperationalTruthService`):**
     - `WishlistStatus.consolidated` (как и `dismissed`) считается закрытым/поглощённым и исключается из активных знаменателей незавершённого скоупа.
     - `WishlistStatus.deferred_frozen` не считается `movable()`, не запускает декомпозицию и отображается в API (`WishlistController` / `WishlistResponseDto` с полями `mergedIntoWishlistId`, `consolidatedFromIds`, `consolidationConflict`).

### 4.2. Ответственные механизмы WO #14
- `V148__wishlist_frozen_consolidation_lineage.sql` + `WishlistStatus` + `WishlistEntity` + `WishlistRepository` + `WishlistResponseDto`.
- `OperationalPolicyService` (`ADD_WISHLIST` разрешён для `frozen` без открытия остальных действий) + `ProjectFlowService.addWishlistItem` (сохраняет в `deferred_frozen` для замороженных проектов).
- `FrozenWishlistConsolidationService` (новый выделенный сервис группировки, детекции конфликтов, формирования обобщённого пожелания с lineage и идемпотентной пометки исходников `consolidated` / `mergedIntoWishlistId`).
- `ProjectFlowService.activateProject` + `ProjectFlowService.executeOrchestrate` (триггер консолидации при разморозке и до первой декомпозиции).
- `ClientDeliverableReadinessService` + `FlowSpineService` + `OperationalTruthService` (учёт `deferred_frozen` и `consolidated` в знаменателях без поломки `DennettAutonomyFalsificationTest`).

---

## 5. Жёсткая граница скоупа (Директива «Остальное пока не трогать»)

Категорически **НЕ ИЗМЕНЯТЬ** в рамках WO #13–#14:
1. Порядок и квотную политику маршрутизации `DeepSeek → LOCAL_AGY`.
2. Пул ОС-профилей `remotecli3..10` и механику `LocalAgyCliClient` (в `V147` меняется только тег `provider` с `'GOOGLE_JULES'` на `'LOCAL_AGY'` в БД, чтобы не сломать их выборку).
3. Исторические данные `jules_sessions`, `accounts`, PR-историю и инцидент отклонённых вишлистов `00:50 UTC`.
4. Существующие 3 pending-вишлиста в `knowledge-base-crie` вручную (их подхватит WO #14 при разморозке).
5. Репозитории продуктов (суверенный барьер `GEMINI.md §0`).
