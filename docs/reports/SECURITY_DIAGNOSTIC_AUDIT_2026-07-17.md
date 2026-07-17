# Диагностический аудит: уязвимости, баги, слабая логика, эффективность фронтенда

Дата: 2026-07-17
Область: весь репозиторий EneikProductionSys (backend Spring Boot, frontend Svelte, миграции БД, docker-compose/Dockerfile)
Метод: ручной разбор ключевых точек входа + параллельный агентный анализ по доменам (SSRF/traversal/инъекции, frontend, слой данных)

## Резюме

Общий уровень риска системы: **КРИТИЧЕСКИЙ**.

Ключевая системная проблема — в backend полностью отсутствует какой-либо механизм аутентификации и авторизации (в `pom.xml` нет `spring-boot-starter-security`, ни в одном контроллере нет проверки токена/сессии, нет фильтров/интерсепторов). Все `/api/**` и `/internal/**` эндпоинты доступны любому, кто имеет сетевой доступ к порту 8080. На этом фоне в системе есть встроенный AI-агент-оператор с правом выполнять `git/docker/npm/node/python3/mvn` на хосте контейнера, у которого дополнительно примонтирован `/var/run/docker.sock` — то есть путь от неаутентифицированного HTTP-запроса до полного захвата хост-машины короткий и реалистичный.

Ниже — находки по убыванию критичности, со ссылками на файлы и конкретными сценариями эксплуатации, затем рекомендации.

---

## КРИТИЧНО

### C1. Полное отсутствие аутентификации и авторизации
`pom.xml` — нет зависимости `spring-boot-starter-security`; ни в одном из ~40 контроллеров нет проверки личности вызывающего. `config/WebConfig.java` настраивает только CORS (разрешены `localhost:3000`/`127.0.0.1:3000`/`[::1]:3000` с `allowCredentials(true)`), но CORS — это ограничение для браузера, а не контроль доступа: прямой HTTP-запрос (curl, серверный вызов, скрипт) обходит его полностью.
**Следствие:** все находки ниже (C2–C4, H1–H3) эксплуатируются без какой-либо аутентификации — достаточно сетевого доступа к порту 8080.

### C2. Неаутентифицированный оператор с возможностью выполнения команд на хосте + docker.sock в контейнере
`controllers/dashboard/ChatAssistantController.java` — `POST /api/dashboard/chat` без всякой авторизации. Метод `isOperatorQuestion(...)` (файл, строки ~90–150) содержит очень широкий список триггерных слов (`"docker"`, `"git"`, `"test"`, `"build"`, `"fix"`, `"код"`, `"репозиторий"`, `"исправ"`, `"почин"` и т.д.) — практически любое сообщение об проекте маршрутизируется в `ProjectOperatorService.answer(...)`.

`services/dashboard/ProjectOperatorService.java`:
- `runGenericCommand` (~2124–2145) принимает произвольный массив `command` из JSON-аргументов LLM-инструмента; единственная защита — `isAllowedOperatorCommand` (~2305–2308), проверяющая **только первый токен** команды против списка `git, docker, npm, node, mvn, ./mvnw, python, python3, pytest, rg, ls, pwd`.
- Это тривиально обходится: `python3 -c "import os,socket;...os.system('...')"`, `node -e "require('child_process').execSync('...')"`, `docker run -v /:/host --rm alpine chroot /host sh -c '...'`.
- Второй барьер — `mutating()` (~2147–2160): требует `allowMutatingTools=true` (в `docker-compose.yml` это **true по умолчанию**, `ENEIK_OPERATOR_ALLOW_MUTATING_TOOLS: ${...:-true}`) и `isExplicitMutatingRequest(userMessage)` — а это снова список бытовых слов (`"run"`, `"start"`, `"build"`, `"fix"`, `"выполни"`, `"запусти"` и т.д.), удовлетворяемый практически любой обычной репликой пользователя. Это не содержательная граница безопасности.
- `docker-compose.yml` (backend-сервис) монтирует `/var/run/docker.sock:/var/run/docker.sock` — при разрешённой команде `docker` это равносильно root-доступу к хосту (можно поднять привилегированный контейнер с смонтированным `/`).

**Сценарий эксплуатации:** любой, кто достучится до `POST /api/dashboard/chat`, отправляет `{"message": "почини сборку: docker run -v /:/host --rm -it alpine chroot /host id"}` (или эквивалент через `python3 -c`) → команда выполняется на хосте без единой проверки личности вызывающего.

### C3. Утечка всех интеграционных секретов через `/internal/settings/resolve`
`controllers/settings/InternalSettingsController.java:22-33` — эндпоинт вызывает `settingsService.effectiveValue(key)` напрямую, минуя `toDto()`, где применяется маскировка секретов (`services/settings/SystemSettingsService.java`, метод `mask()` вызывается только в `toDto`, не в `effectiveValue`). Проверка входа — только `isKnownKey(key)`, а известные ключи включают `github_token`, `linear_api_key`, `gemini_api_key`, `jules_api_key`.

**Сценарий эксплуатации:** `POST /internal/settings/resolve {"key":"github_token"}` без аутентификации возвращает полный `github_token` (с правами push/merge в приватные репозитории организации). Аналогично извлекаются `gemini_api_key`, `linear_api_key`, `jules_api_key` — то есть все интеграционные учётные данные системы.

### C4. `/internal/tasks/**` — неограниченный CRUD над производственным конвейером
`controllers/InternalTaskController.java` — комментарий в коде прямо признаёт отсутствие защиты: *«Restricted to localhost in production via filter/security (omitted for brevity in this task)»* — но фактически никакого фильтра нет. Любой вызывающий может:
- `GET /internal/tasks` — выгрузить все задачи всех проектов;
- `PATCH /internal/tasks/{id}` — изменить `status`/`title`/`linearIssueId` произвольной задачи (например, выставить `status=done`, обманув `AutoMergeService`, либо вызвать `TaskStatus.valueOf(...)` с мусорным значением → необработанное исключение → 500);
- `PATCH /internal/tasks/{id}/metadata` — переписать `blockers`/`dodText`/`prUrl` (SQL-запрос параметризован — SQL-инъекции здесь нет, но сама операция ничем не защищена).

---

## ВЫСОКИЙ

### H1. GitHub webhook принимается без проверки подписи
`controllers/github/GithubWebhookController.java:47-105` — обрабатывает `POST /api/webhooks/github`, читает `X-GitHub-Event`, но нигде не проверяет `X-Hub-Signature-256`/HMAC. В `application.properties` вообще нет свойства для webhook-секрета (есть только `github.webhook-url` — адрес для регистрации, не секрет для проверки).
**Сценарий:** произвольный POST с телом `{"action":"opened","pull_request":{...},"repository":{"name":"<имя реального проекта>"}}` без подписи заставляет сервер найти первую задачу проекта в статусе `claimed`, досрочно завершить клейм исполнителя (`claimService.complete`) и запустить ревьюера на сфабрикованных данных — порча состояния конвейера и трата слотов Jules-аккаунтов фальшивыми событиями.

### H2. Секреты в открытом виде в логах и телах запросов
`services/projectfactory/GitHubProjectFactoryClient.java` (строки ~154,160,260,265,274) — `System.out.println("DEBUG: ... token: " + ...)` пишет префикс токена и полные тела ответов GitHub API в stdout. `services/MLPredictionServiceClient.java` (методы `reviewPr`, `checkRefusalCriteria`, `chatWithTier`, `generateTaskMetadata`, `generateTaskSlices`, строки ~107-231) кладёт `apiKey` (Gemini) и `githubToken` открытым текстом в JSON-тело каждого запроса к отдельному ML-сервису — при включённом логировании на стороне ML-сервиса или компрометации сети между сервисами секреты утекают.

### H3. Path traversal в `/api/ai/resources/video-assets/{projectSlug}`
`controllers/ai/GoogleAiResourceController.java:98-118` — путь строится как `Paths.get("./data/video-assets", projectSlug)` без `normalize()`/`startsWith(root)`-проверки, в отличие от аналогичных мест в `DesignAssetService`/`VideoAssetService`/`ProjectOperatorService`, где такая защита есть. `GET /api/ai/resources/video-assets/..` листит содержимое `./data` вместо `./data/video-assets`, раскрывая структуру каталога вне предполагаемой директории.

### H4. Гонка при захвате задачи (race condition) в `ClaimService.claimSpecificTask`
`services/ClaimService.java:91-130, 229-254`, вызывается из `JulesDispatchService.java:158-171,175-177` и `ProjectFlowService.java:1124,1182`. В отличие от `claim()`/`claimForProject()`, использующих атомарный `SELECT ... FOR UPDATE SKIP LOCKED` (`TaskRepository.lockTaskByIdForUpdate`), `claimSpecificTask` делает обычный `findById` → проверку статуса в Java → `save`, без блокировки строки. Комментарий в коде указывает, что «компенсирующая» проверка `validateTaskAvailability()` нужна из-за отсутствия partial unique index в H2 — но этот метод **нигде не вызывается** (мёртвый код), а сам partial unique index на `claims(task_id) WHERE released_at IS NULL` закомментирован в `V3__core_agency_schema.sql`.
**Сценарий:** `ContinuousOrchestrationService` (планировщик раз в 60 сек) пересекается по времени с параллельным вызовом того же пути диспетчеризации → две транзакции видят `status=queued`, обе создают активный клейм на одну и ту же задачу → задача уходит в две параллельные Jules-сессии/PR, счётчики загрузки аккаунтов и последующая обработка "осиротевших" клеймов ломаются непредсказуемо.

### H5. Тавтологичный (фактически нерабочий) фильтр по capability/tag в `AccountRepository`
`repositories/AccountRepository.java` (4 метода: `existsOnlineWithCapability`, `lockNextIdleAccountForProjectAndCapability`, `lockNextJulesAccountWithCapacity`, `existsJulesAccountWithCapacity`, строки ~31-33, 82-85, 96-127) — условие `(:tag IS NULL OR :tag IS NOT NULL)` истинно всегда независимо от значения `tag`; реальной фильтрации по `capabilities` в запросе нет. Сейчас баг замаскирован тем, что миграции `V31`/`V32` принудительно выдали всем аккаунтам полный набор из 12 ролей — но это обход, а не исправление: при попытке ввести специализацию аккаунтов по ролям диспетчеризация начнёт присылать задачи аккаунтам без нужной capability.

---

## СРЕДНИЙ

### M1. Чат-оператор в UI не предупреждает пользователя о его возможностях
`frontend/src/dashboard/AdminDashboard.svelte:281-310, 531-561` — поле ввода `"Ask about the current project..."` отправляет любой текст в `POST /api/dashboard/chat` с `mode: 'operator'` без подтверждения или дисклеймера о том, что «ассистент» может выполнять команды в репозитории/Docker (см. C2). Усиливает риск C2 с точки зрения пользовательского опыта — человек не осознаёт, что печатает в консоль с правом исполнения.

### M2. `javascript:`-URI в `href`/`src` без валидации схемы (self-XSS через данные с бэкенда)
`frontend/src/client/ClientDeliveryView.svelte:106-121` (`<a href={link}>`, `<img src={url}>` для `prLinks`/`screenshots`) и `frontend/src/dashboard/CommandDashboardV2.svelte:388,394` (`<video src={video.url}>`, `<a href={video.url}>`) — Svelte не проверяет схему URL при биндинге. Прямого `{@html}`/`innerHTML` в проекте не найдено (это плюс), но если значение из БД (куда, с учётом C1/C4, потенциально может писать кто угодно) окажется `javascript:...`, клик по ссылке выполнит JS в контексте страницы.

### M3. N+1 запросы в дашбордах и фоновых сервисах
- `services/dashboard/SystemStatusService.java:158-168` (`linearCompleteness`) — `findById` в цикле вместо `findAllById`.
- `services/AutoMergeService.java:92-107` (`processAutoMerge`, планировщик раз в 60 сек) — на каждый `PrReviewEntity` отдельные `findById` для сессии и задачи (2N лишних запросов).
- `services/dashboard/SystemStatusService.java:401-403` (`conflictDpmo`) — `TaskConflictEntity.task` (LAZY) и `TaskEntity.project` (EAGER по умолчанию) порождают отдельные SELECT на каждый конфликт.
- `repositories/TaskRepository.java` (`hasFileScopeConflict`, вызывается из `lockNextQueuedTask*`) — на каждого кандидата с непустым `file_scope` заново выполняет `findActiveTasksByProject`, O(n) запросов при захвате одной задачи.

### M4. Необработанные fetch-ошибки во фронтенде
`frontend/src/dashboard/AdminDashboard.svelte` — `loadActiveProject` (153-159), `refreshCollaborators` (161-173), `updateAccount` (175-187), `saveSetting` (213-228) не обёрнуты в try/catch, в отличие от соседнего `loadStatus` (137-151). При сетевой ошибке — «тихий» отказ без обратной связи пользователю (unhandled promise rejection).

### M5. Историческая рассинхронизация длины VARCHAR и Java-enum значений
`V4__create_wishlist.sql` (`source VARCHAR(16)`) → `V14__fix_wishlist_status_length.sql` (24) → `V24__alter_wishlist_source_length.sql` (32); `WishlistEntity` не указывает `length` в `@Column`, поэтому Hibernate не валидирует соответствие на старте — рассинхрон проявляется только рантайм-исключением H2 «Value too long for column». История миграций — прямое доказательство, что это уже приводило к падениям в проде (`WishlistSource.role_mismatch_followup`, 22 симв., не помещалось в исходные 16). Риск повторения при добавлении новых длинных значений enum без сопутствующей миграции сохраняется.

### M6. Отсутствие индекса на `jules_sessions.status`
`V10__create_jules_sessions.sql` — таблица создана без индексов, кроме PK/FK. `JulesSessionRepository.findByStatus` вызывается регулярно из watchdog/оркестрации (раз в 60 сек), `ContinuousOrchestrationService.pollActiveJulesSessions` вообще делает `findAll()` и фильтрует в памяти (строки ~98-103) — full table scan на каждый цикл, деградирует с ростом истории сессий.

### M7. Polling каждые 10 сек без учёта видимости вкладки и без дельт
`frontend/src/dashboard/CommandDashboardV2.svelte:311-315` — `setInterval(fetchDashboard, 10000)` каждые 10 секунд заново запрашивает два полных датасета (`/dashboard`, `/command-dashboard`) целиком, включая весь список задач/агентов/wishlist/EMS-метрик, без проверки `document.hidden` и без ETag/дельт/пагинации. Таймеры корректно чистятся в `onDestroy` (утечки нет), но при нескольких открытых вкладках нагрузка на бэкенд линейно растёт без пользы.

---

## НИЗКИЙ / технический долг

- **L1.** Статусы (`JulesSessionEntity.status`, `TaskConflictEntity.resolutionStatus`) хранятся как обычный `String`, а не `@Enumerated` — литералы разбросаны по JPQL/native SQL/Java-коду (`AutoMergeService`, `JulesDispatchService`, `CommandDashboardService`), опечатка не ловится компилятором (тот же класс проблем, что и H5).
- **L2.** Все `{#each}`-циклы во фронтенде (список во всех `.svelte`-файлах: wishlist, tasks, agents, queue.byTag и др.) без keyed-синтаксиса `(item.id)` — при перезаписи данных каждые 10 сек (M7) Svelte сравнивает по индексу вместо id, лишние DOM-мутации и потеря состояния фокуса/ввода.
- **L3.** Широкое использование `any` вместо типов API-ответов (`CommandDashboardV2.svelte`, `MetricsView.svelte`, `ClientDeliveryView.svelte`) — нет runtime-валидации формы ответа; при несовпадении со схемой (`commandDashboard.acceptanceReadiness.uiColorToken` и т.п.) — необработанный `TypeError`, дашборд падает без явного экрана ошибки.
- **L4.** `V13__create_jules_sessions.sql` фактически создаёт таблицу `pr_reviews`, а не `jules_sessions` (copy-paste имени файла) — не ломает Flyway, но затрудняет аудит миграций.
- **L5.** Нет `Content-Security-Policy` (`frontend/index.html`, `vite.config.ts`) — в сочетании с M2 убирает последний рубеж защиты от инлайн-скриптов.
- **L6.** `frontend/.env` закоммичен в git (не в `.gitignore`); секретов сейчас не содержит (только `VITE_API_BASE_URL`), но при появлении в будущем любого `VITE_*`-ключа он неизбежно попадёт в git-историю и будет забандлен в клиентский JS.
- **L7.** URL строится строковой интерполяцией без `encodeURIComponent` (`MetricsView.svelte` и др.) — сейчас источник значений безопасен (UUID из уже загруженного списка), но паттерн хрупкий при расширении функциональности.

---

## Проверено — проблем не найдено

- **SQL-инъекции**: все `@Query`/native SQL/`JdbcTemplate` используют bind-параметры; конкатенации непроверенного ввода не обнаружено.
- **SSRF с контролем хоста атакующим**: все исходящие HTTP-клиенты (GitHub, Linear, Gemini, ML-сервис, Jules) берут хост из статического конфига приложения, не из пользовательского ввода; `AutoMergeService` дополнительно жёстко проверяет `host == "github.com"`.
- **Path traversal в `ProjectOperatorService`**: везде есть проверка `normalized.startsWith(workspaceRoot|systemRepoRoot)` — обхода не найдено (в отличие от H3).
- **XSS через `{@html}`/`innerHTML`**: не используется нигде во фронтенде; весь текст рендерится через безопасную интерполяцию Svelte.
- **H2-консоль**: `spring.h2.console.enabled` нигде не включена — эта поверхность атаки закрыта.

---

## Рекомендации (по приоритету)

### Немедленно, до любого выхода за пределы localhost
1. Добавить `spring-boot-starter-security` и минимум API-key/Basic-аутентификацию на все `/api/**`; вынести `/internal/**` на отдельный порт/интерфейс, недоступный извне docker-сети, либо тоже закрыть аутентификацией — комментарий «restricted to localhost… omitted for brevity» в `InternalTaskController` сейчас ничем не подкреплён (C1, C3, C4).
2. Исправить `InternalSettingsController.resolve` — использовать маскирующий `toDto()` вместо `effectiveValue()`, либо полностью закрыть эндпоинт аутентификацией (C3).
3. Убрать монтирование `/var/run/docker.sock` в backend-контейнер (`docker-compose.yml`), либо радикально сузить `run_command`: заменить allowlist «первый токен команды» на allowlist конкретных полных команд с фиксированными аргументами — исключить произвольные `python3 -c`, `node -e`, `docker run` (C2).
4. Добавить проверку HMAC-подписи (`X-Hub-Signature-256`) с общим секретом в `GithubWebhookController` (H1).

### Короткий срок
5. Исправить race condition в `ClaimService.claimSpecificTask` — использовать ту же блокировку `FOR UPDATE SKIP LOCKED`, что и в `claim()`/`claimForProject()`, либо `@Version`-оптимистичную блокировку; либо реально вызывать `validateTaskAvailability()`, либо удалить как мёртвый код (H4).
6. Исправить тавтологичное условие `(:tag IS NULL OR :tag IS NOT NULL)` в 4 методах `AccountRepository` на реальную проверку capability (H5).
7. Убрать логирование секретов (`System.out.println` с токеном, поля `apiKey`/`githubToken` в открытых JSON-телах запросов к ML-сервису) — использовать redacted-логирование (H2).
8. Исправить path traversal в `GoogleAiResourceController` (`normalize()` + `startsWith(root)`, по аналогии с `DesignAssetService`) (H3).

### Средний срок
9. Устранить N+1 в `SystemStatusService`, `AutoMergeService`, `TaskRepository.hasFileScopeConflict` — batch-загрузка / join fetch (M3).
10. Добавить индекс на `jules_sessions(status)`; заменить `pollActiveJulesSessions`-полный-скан на `findByStatus` (M6).
11. Явно указать `length` в `@Column` для VARCHAR-полей, связанных с enum, и/или добавить CI-проверку соответствия длины enum-значений и колонки (M5).
12. Заменить строковые статусы на `@Enumerated(EnumType.STRING)` там, где это ещё `String` (L1).
13. Фронтенд: обернуть все мутирующие fetch в try/catch (M4); валидировать схему (`http:`/`https:`) перед рендером в `href`/`src` (M2); добавить keyed `{#each}` (L2); заменить `any` на типизированные интерфейсы ответов (L3); учитывать `document.hidden` и/или перейти на дельта-обновления вместо полного polling каждые 10 сек (M7); добавить CSP (L5).
14. Добавить в UI чата явное предупреждение/подтверждение перед тем, как сообщение уйдёт в режим оператора с правом мутирующих действий (M1).

### Организационно
15. Переименовать/поправить содержимое `V13__create_jules_sessions.sql` для целостности аудита миграций (L4).
