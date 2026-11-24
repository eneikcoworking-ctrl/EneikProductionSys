# L1 Эпистемическая диагностика автономного закрытия Фабрики: P0 #1–#3 (WO #10) + P1 #4 DeepSeek PR / Scope / UI Boundary (WO #11)

**Дата:** 2026-11-24 22:51 UTC  
**Аудитор:** L1 Менеджер-философ (`remotecli2`)  
**Основание:** Срочная директива Оператора от `2026-11-24 22:45:00 UTC` ([OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md)) и контекст экосистемы ([ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md)).  
**Эмпирическая база (анти-зеркало Витгенштейна D013):** живые замеры хоста и контейнеров (`df`, `free`, `docker stats`, `/actuator/health`, `:8093/health`), живые логи `eneikproductionsys-backend-1` (`21:10–22:48 UTC`), прямая инспекция bare-mirror `/var/lib/eneik/mirrors/knowledge-base-crie.git` и аудит исходного кода Фабрики (`DeepSeekWorktreeExecutionEngine.java`, `GitWorktreeExecutionManager.java`, `GitHubPullRequestService.java`, `JulesDispatchService.java`, `ClaimService.java`, `FileScopeEnforcer.java`, `LocalVerificationRunner.java`, `DesignShopOrchestrationService.java`, `DeepSeekApiClient.java`).

---

## 0. Живая телеметрия хоста и контура (2026-11-24 22:49 UTC)

- **Ресурсы хоста:** диск `/` = **`63%`** (`23G/38G`, `14G` свободно — инвариант `< 70%` соблюдён); RAM `991 MiB` доступно (`2.8GiB/3.7GiB`), swap `2.3GiB/4.0GiB`.
- **Контейнеры и продукты:**
  - `eneikproductionsys-backend-1` (:8080): `UP`, но **`998.6MiB / 1GiB` (`97.52% RAM` — на грани OOM-kill!)**, **`581 PIDs`**, **`82.66% CPU`** — коммиты L2 по WO #10 (P0 #1–#3) ещё не влиты; горячий цикл ретраев (`attempt 0/34`) продолжает прожигать память, CPU и DeepSeek API каждую минуту.
  - `eneikproductionsys-judgment-proxy-1` (:8093): `UP`, но по-прежнему `{"ok":true,"primary":"gemini","shadowDir":"/shadow","geminiAvailable":false}` (работает эвристическая заглушка).
  - `dmitryefremov-backend` (:18080): `UP` (`0.08% CPU`, `28.55MiB`).
- **Вывод по P0:** Work Order #10 (`docs/reports/PROGRAMMER_DIRECTIVES.md` §12, Такты 1–3: `DeepSeekPatchParser` CTRL-CHAR → `ClaimService`/`ContinuousOrchestrationService` backoff + `init: true` → `judgment-proxy :8093` `primary=deepseek`) **сохраняется без изменений как безусловный P0** и обязан быть доведён до живого рантайма перед Тактом 4 (P1 #4).

---

## 1. P1 #4A — Ложный успех PR (`422 no history in common with main`) и симулякр `local-repo/pulls`

### 1.1. Эмпирическая форензика (живой лог `22:41 UTC` + live bare-mirror)
1. **Лог `eneikproductionsys-backend-1` (`2026-11-24T22:41:03–22:42:52 UTC`):**
   ```text
   22:41:03.733Z INFO  GitWorktreeExecutionManager : Pushing branch worker/c6f75610-27b4-4289-b0a8-537e0f9c3e9e with authenticated GitHub token
   22:41:07.517Z WARN  GitHubPullRequestService    : GitHub create-PR failed for eneikdru/knowledge-base-crie head=worker/c6f75610-27b4-4289-b0a8-537e0f9c3e9e base=main: status=422 body={"message":"Validation Failed","errors":[{"resource":"PullRequest","code":"custom","message":"The worker/c6f75610-27b4-4289-b0a8-537e0f9c3e9e branch has no history in common with main"}]}
   22:41:07.525Z INFO  DeepSeekWorktreeExecutionEngine : DeepSeekWorktreeEngine: Task c6f75610-27b4-4289-b0a8-537e0f9c3e9e COMPLETED with PR: https://github.com/local-repo/pulls/task-c6f75610-27b4-4289-b0a8-537e0f9c3e9e
   22:42:52.199Z WARN  JulesDispatchService : Replaying stranded pr_opened workflow for task c6f75610-27b4-4289-b0a8-537e0f9c3e9e / session deepseek-63129554-... / PR https://github.com/local-repo/pulls/task-c6f75610-27b4-4289-b0a8-537e0f9c3e9e
   ```
2. **Прямая инспекция `/var/lib/eneik/mirrors/knowledge-base-crie.git` (причина `no history in common with main`):**
   - `git log --format="%H parents=[%P] %s" worker/c6f75610-27b4-4289-b0a8-537e0f9c3e9e`:
     **`60cdcae76af8055062b9d64207d7de6eb5627745 parents=[]`** — **ORPHAN ROOT COMMIT** с **нулевым списком родителей** и единственным файлом `.eneik/records/design-review-verdict-...json`!
   - `git merge-base main worker/c6f75610-27b4-4289-b0a8-537e0f9c3e9e` → exit 2 (общий предок отсутствует).
   - `git config --get-all remote.origin.fetch` → **`+refs/*:refs/*`** (результат `git clone --mirror` в `GitWorktreeExecutionManager.java:98`).
   - **Точная каузальная цепочка (D013 Сэлмон):**
     1. `GitWorktreeExecutionManager.getOrCreateBareMirror` (`GitWorktreeExecutionManager.java:108-113`) запускает `git fetch origin --prune` на общем bare-mirror **параллельно из нескольких потоков пула `pool-2-thread-1..8` без блокировки**.
     2. Поскольку в `--mirror`-репозитории настроен refspec `+refs/*:refs/*`, каждый конкурентный `git fetch origin --prune` **удаляет все локальные ветки `refs/heads/worker/<taskId>`**, которые ещё не запушены на `origin` — **прямо из-под живого worktree другого потока!**
     3. Когда поток воркера после генерации DeepSeek вызывает `git add -f . && git commit` на удалённой ветке (`HEAD -> refs/heads/worker/<taskId>`), Git создаёт **сиротский корневой коммит (`parents=[]`)**, который затем форс-пушится на GitHub и закономерно отвергается `HTTP 422: no history in common with main`.
     4. Далее `DeepSeekWorktreeExecutionEngine.openPullRequest` (`DeepSeekWorktreeExecutionEngine.java:300-318`) перехватывает пустой `Optional` от `GitHubPullRequestService.createPullRequest` и **фабрикует фиктивную ссылку `https://github.com/local-repo/pulls/task-<id>`**, после чего строки `207-214` помечают сессию `status = "pr_opened"`, `belnapStatus = TRUE` и возвращают `WorkerDispatchResult.completed(...)` (в отличие от `LocalAgyWorktreeExecutionEngine.java:271-273`, где null PR уже считается отказом).
     5. Вдобавок `GitWorktreeExecutionManager.push` (`line 303`) игнорирует ненулевой exit code, а `DeepSeekWorktreeExecutionEngine:200-204` глотает исключения `push`; кроме того, `JulesDispatchService:708-722` заранее создаёт `local-agy-*` сессию, а `DeepSeekWorktreeExecutionEngine:86-98` создаёт вторую отдельную `deepseek-*` сессию, оставляя первую «осиротевшей» (`Orphaned local sovereign session`, `22:41:14 UTC`).

### 1.2. Канонический контракт честности PR и bare-mirror (решение L1)
- **Философское основание (RAG-корпус):**
  - `D010` (**Элвин Голдман** — `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`): запрет подмены реального артефакта симулякром (`https://github.com/local-repo/pulls/...`). Статус `COMPLETED` / `pr_opened` / `Belnap=TRUE` эпистемически допустим **только** при наличии подтверждённого реального URL PR от GitHub API (HTTP 201).
  - `D003` (**Сабина Леонелли** — `Data Journeys` / целостность контракта при пересечении границ) и `D013` (**Уэсли Сэлмон** — `CAUSAL_PROCESS_TRACE`): bare-mirror не имеет права разрушать локальную ветку живого worktree при параллельном fetch.
- **Обязательные инварианты контракта:**
  1. **Нулевая терпимость к фиктивным PR URL (`DeepSeekWorktreeExecutionEngine`):** полностью удалить fallback `"https://github.com/local-repo/pulls/task-" + task.getId()`. Любой отказ `push` или `GitHubPullRequestService.createPullRequest` (HTTP 422 / пустой `Optional` / исключение) переводит сессию в честный отказ (`status = "pr_creation_failed"` или `"closed_no_code"`, `belnapStatus = FALSE`, `prUrl = null`, явный `closureReason`) и возвращает `WorkerDispatchResult.failed(...)`.
  2. **Потокобезопасная синхронизация bare-mirror без сноса `worker/*` (`GitWorktreeExecutionManager`):**
     - Ввести per-mirror `ReentrantLock` (`ConcurrentHashMap<Path, ReentrantLock>`) вокруг `clone`/`fetch`/`resync`/`createWorktree`/`removeWorktree`.
     - Заменить разрушительный `--mirror` fetch (`+refs/*:refs/* --prune`, сносящий живые `refs/heads/worker/*`) на безопасный refspec `+refs/heads/*:refs/remotes/origin/*` (сохраняя `refs/remotes/origin/main` свежим и не трогая локальные ветки `refs/heads/worker/*`), а `createWorktree` всегда базировать от `origin/<startingBranch>` (если есть).
     - Ввести явный метод `resyncMirrorWithOriginMain(ProjectEntity project)` (force-обновление `origin/main` и `main`, при повреждении/несовместимости — переинициализация зеркала) и вызывать его автоматически при HTTP 422 `no history in common with main` или при провале проверки `git merge-base`.
     - Перед `push` выполнять пре-чек предка (`git merge-base HEAD origin/<base>` / отсутствие `parents=[]`) и проверять exit code `git push` (ненулевой код = исключение, без молчаливого проглатывания).

---

## 2. P1 #4B — Отравленные задачи `SCOPE_INSUFFICIENT` (`crie_*.py` в Java-репозитории)

### 2.1. Эмпирическая форензика (живой лог `22:44–22:48 UTC`)
- Каждую минуту (4 последовательных цикла в окне `22:44–22:48 UTC`) задачи `knowledge-base-crie` (`4c4837ff-9e90-4394-84a2-bff574e8232e`, `d976d831-8206-4052-9fb0-dbeb433a8f7f`, `a6f64e24-a0ea-4975-aad1-ab7847a87399`) уходят в DeepSeek API и мгновенно возвращают:
  ```text
  WARN DeepSeekWorktreeExecutionEngine : Task 4c4837ff-...: DeepSeek returned SCOPE_INSUFFICIENT. Missing files: [crie_persistent_memory.py, crie_document_parser.py, crie_assistant_api.py, crie_smart_consultant.py]
  WARN ClaimService : ClaimService.releaseClaimToQueue: task 4c4837ff-... dispatch refused (attempt 0/34 against budget 34): SCOPE_INSUFFICIENT: [crie_persistent_memory.py, ...]
  ```
- **Три корня дефекта:**
  1. `DeepSeekWorktreeExecutionEngine.java:116-148` (в отличие от `LocalAgyWorktreeExecutionEngine.java:141-150`) **не передаёт дерево файлов репозитория (`git ls-files`)** в промпт и не проверяет, существуют ли запрошенные `missingFiles` в репозитории и совместимы ли они со стеком проекта (`pom.xml` / Java vs `*.py`).
  2. `DeepSeekWorktreeExecutionEngine.java:143-147` сохраняет `session.setStatus("scope_insufficient")` и возвращает `WorkerDispatchResult.failed(...)`.
  3. `JulesDispatchService.java:728-733` → `ClaimService.releaseClaimToQueue` сбрасывает задачу обратно в `TaskStatus.queued` без эскалации повторного `SCOPE_INSUFFICIENT`, создавая бесконечный цикл траты токенов DeepSeek и CPU.

### 2.2. Канонический контракт терминации отравленных `SCOPE_INSUFFICIENT` (решение L1)
- **Философское основание (RAG-корпус):**
  - `D003` (**Сабина Леонелли** — валидация контекста данных при реконтекстуализации) + `D002` (**Гилберт Райл** / **Дюэм-Куайн** — разрыв незавершаемого категориального цикла) + `D010` (**Элвин Голдман** — отсутствие ложной надежды на несуществующие файлы).
- **Обязательные инварианты контракта:**
  1. `DeepSeekWorktreeExecutionEngine` обязан передавать реальный список файлов репозитория (`worktreeManager.listRepositoryFiles(worktreePath)`) в контекст промпта (по образцу `LocalAgyWorktreeExecutionEngine`).
  2. Классификация `SCOPE_INSUFFICIENT` по реальному дереву `git ls-files`:
     - **Кросс-стековый / несуществующий poison-сигнал:** если все (или ключевые) `missingFiles` отсутствуют в `git ls-files` и принадлежат чужому стеку (например, `*.py` в Java/Maven-репозитории с `pom.xml` и 0 `*.py` файлов, либо отсутствующие legacy-пути) и/или задача повторно (`>= 2` раз подряд) возвращает `SCOPE_INSUFFICIENT` на несуществующие в репозитории файлы:
     - Переводить задачу в **терминальный статус отказа / перекомпиляции** (вывести из `queued` → `TaskStatus.blocked` с явным маркером `POISONED_SCOPE_INSUFFICIENT` / `NEEDS_RECOMPILATION` в `julesDispatchStatus` и записью в `DefectJournal` / `needs_human_review`, не допуская повторного автоматического claim).
  3. Счётчик `scope_insufficient` сессий обязан учитываться в `ClaimService` (в единой монотонной вариант-функции отказов).

---

## 3. P1 #4C — Граница ответственности с `aesthetic-master` (UI vs Backend для `knowledge-base-crie`)

### 3.1. Эмпирическая форензика
- По директиве Оператора (`2026-11-24 22:45 UTC`) и [ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md):
  - Для `knowledge-base-crie` **Фабрика отвечает исключительно** за **Java 21 / Spring Boot 3 бэкенд**, **SQLite (`data/agent_memory.sqlite`)** и **REST API**:
    - `/crie/api/chat`
    - `/crie/api/documents/upload`
    - `/crie/api/dialogues/history`
    - `/crie/api/catalog/search`
    - `/crie/api/download/...`
  - Фронтенд **`src/main/resources/static/index.html`** эксклюзивно ведёт изолированный демон **`aesthetic-master`**.
- **Что сейчас нарушает эту границу в коде Фабрики (проверено в рантайме):**
  1. `DesignShopOrchestrationService.java:113-120` продолжает запускать автономный цех дизайна для `knowledge-base-crie` (в `21:10:39 UTC` создан `20261124211039-mockup`, породивший задачу `c6f75610` `Design review`, а также оба остаточных `Connection leak detection` из отчёта WO #9!).
  2. `LocalVerificationRunner.java:74, 169, 388-550` безусловно вызывает `compileAndSyncFrontendToStatic` и `verifyLevelOfAbstractionLock`, которые **перезаписывают или блокируют `src/main/resources/static/index.html`**.
  3. `FileScopeEnforcer.java` не содержит деонтического запрета (Raz D006), защищающего `src/main/resources/static/index.html` от правок воркеров Фабрики в проектах с внешним владельцем UI.
  4. Промпты `DeepSeekApiClient.java` / `LocalAgyCliClient.java` не передают воркерам явную границу владения UI/Backend.

### 3.2. Канонический контракт разграничения ответственности (решение L1)
- **Философское основание (RAG-корпус):**
  - `D006` (**Джозеф Раз** — `BARCAN-TAG-10` / `PROHIBITION_AS_CODE`, исключающее основание второго порядка): защита `src/main/resources/static/index.html` обязана быть кодовым физическим заслоном до коммита, а не «пожеланием в промпте».
  - `D001` (**Анжела Поточник** — `The Aims of Engineering` / разграничение несовместимых проекций без слияния в монолит) и `D003` (**Сабина Леонелли** — строгий контракт интерфейса `REST API ↔ UI`).
  - **Суверенный барьер (`GEMINI.md §0`):** реализуется строго как **конфигурируемый механизм Фабрики** (без ручных правок репозитория продукта).
- **Обязательные элементы механизма:**
  1. **Реестр проектов с внешним владельцем UI (`SystemSettingsService` / конфигурация Фабрики):** например, настройка `external_ui_owned_projects` (default включает `knowledge-base-crie` → защищённый путь `src/main/resources/static/index.html`, владелец `aesthetic-master`, контракт бэкенда: Java 21 / Spring Boot 3, SQLite `data/agent_memory.sqlite`, REST API `/crie/api/chat`, `/crie/api/documents/upload`, `/crie/api/dialogues/history`, `/crie/api/catalog/search`, `/crie/api/download/...`).
  2. **Физический заслон в `FileScopeEnforcer` (`enforce`):** для проектов с внешним владельцем UI любое изменение `src/main/resources/static/index.html` (даже если путь по ошибке попал в `task.fileScope` или `fileScope` пустой) немедленно откатывается (`rollbackWorktree`) и отвергается `ExclusionaryReasonViolationException` с причиной `EXTERNAL_UI_OWNED_BY_AESTHETIC_MASTER`.
  3. **Исключение мутации в `LocalVerificationRunner`:** для проектов с внешним владельцем UI не выполнять `compileAndSyncFrontendToStatic` и `verifyLevelOfAbstractionLock` поверх `src/main/resources/static/index.html`.
  4. **Отключение двойного владения в `DesignShopOrchestrationService`:** пропускать генерацию/ревью дизайн-макетов (`tick`) для проектов с внешним владельцем UI (с явным логом причины `EXTERNAL_UI_OWNER`).
  5. **Явный контракт в промптах (`DeepSeekApiClient` / `LocalAgyCliClient`):** для проектов с внешним UI добавлять в системный/задачный блок строгую границу: редактировать только backend/SQLite/REST API и тесты; `src/main/resources/static/index.html` запрещён к изменению (`aesthetic-master`).

---

## 4. Сводная матрица ответственных механизмов Фабрики (WO #10 + WO #11)

| Очередность | Приоритет | Дефект (RAG) | Ответственный механизм и файлы Фабрики |
|---|---|---|---|
| **Такт 1** | **P0 #1** (WO #10) | `D003` (Леонелли/Куайн), `D008` (Поппер) | **Механизм №10:** `DeepSeekPatchParser.java` (state-machine санитайзер `CTRL-CHAR`), `LocalAgyOutputParser.java`, `DeepSeekPatchParserTest.java` |
| **Такт 2** | **P0 #2** (WO #10) | `D013` (Витгенштейн/Сэлмон), `D005` (Бучак), `D008` (Поппер) | **Механизм №11:** `ClaimService.java` (монотонный бюджет + exponential backoff), `ContinuousOrchestrationService.java` (`fixedDelay` + cooldown `failed=1`), `GitWorktreeExecutionManager.java`, `docker-compose.yml` (`init: true`) |
| **Такт 3** | **P0 #3** (WO #10) | `D003` (Куайн/Леонелли), `D010` (Голдман), `D008` (Поппер) | **Механизм №12:** `judgment-proxy/server.js` (`primary=deepseek` + явная цепочка fallback + честный `/health`), `judgment-proxy/server.test.js`, `docker-compose.yml` |
| **Такт 4** | **P1 #4** (WO #11) | `D010` (Голдман), `D003` (Леонелли), `D006` (Раз), `D001` (Поточник), `D008` (Поппер) | **Механизм №13:** `DeepSeekWorktreeExecutionEngine.java` (запрет `local-repo/pulls`, честный отказ PR, repo tree + терминация `SCOPE_INSUFFICIENT`), `GitWorktreeExecutionManager.java` (lock + безопасный refspec без сноса `worker/*` + `resyncMirrorWithOriginMain` + проверка `merge-base`/`push`), `GitHubPullRequestService.java` (детекция `422 no history in common`), `JulesDispatchService.java` / `ClaimService.java` (терминация poisoned `SCOPE_INSUFFICIENT`), `FileScopeEnforcer.java` + `LocalVerificationRunner.java` + `DesignShopOrchestrationService.java` + `DeepSeekApiClient.java` + `SystemSettingsService.java` (граница UI `aesthetic-master` ↔ Backend/API) |
