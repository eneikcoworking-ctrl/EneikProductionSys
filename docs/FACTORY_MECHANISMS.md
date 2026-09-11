# Механизмы фабрики

Всё, из чего состоит Eneik Production System, одним родом записи. Не хроника и не план: **что есть, с чем
связано, чего стоит, и что об этом следует по философии.**

## Как читать

Механизмы выстроены **по оси потока** — от требования клиента до подтверждённой сдачи, — а не по алфавиту.
У каждого пять полей, всегда в одном порядке:

* **Связи** — измерено по графу вызовов: кто его зовёт, кого зовёт он, какие хранилища состояния пишет.
  Только связи **между механизмами**; упоминания в комментариях и сущности отброшены. Это поле и есть предмет
  «идеального взаимодействия»: механизм без вызывающих может быть мудой, механизм, пишущий много хранилищ, —
  источником спора.
* **Ценность** — какая потеря происходила бы без него. Там, где механизм родился из измеренного убытка, он
  назван: в этом коде почти ничего не написано «на всякий случай».
* **Комментарий** — моя оценка: ядро или периферия по единственному вопросу *может ли механизм удержать
  поток*, и что с ним не так или чем он образцов.
* **Философия** — образец из корпуса `docs/philosopher-patterns` с кодом дефекта D001–D015, форма по
  `03_PATTERN_STRENGTH.md` (**сильная** — подтверждена падающим заслоном или живым замером; **слабая** —
  недостаток измерен; **не мерено** — судил по чтению), и **опровержение**: наблюдение, решающее вопрос.

Ссылка на образец даётся идентификатором, а не именем философа: имя — жест, идентификатор проверяем.

## Две опасности, которые здесь разыскиваются

**Категориальная ошибка** — механизм принимает одно рода за другое. Самый дорогой дефект, потому что не
выглядит ошибкой: цифра есть, статус есть, всё сходится, и только предмет другой. Подтверждённые в этом коде:

    сделано(задача)      принято за   доставлено(ценность)
    статус задачи        принят за    свидетельство доставки
    строка лога          принята за   факт о работе системы
    наше «отменено»      принято за   состояние чужой системы
    число слияний        принято за   показанное покупателю
    «не смог проверить»  принято за   «проверил, всё хорошо»
    носитель фабрики     принят за    требование клиента
    проекция данных      принята за   предмет

**Помеха** — два механизма мешают друг другу: пишут одно, отменяют работу друг друга или держат друг друга в
ожидании. Подтверждённые: четыре писателя статуса аккаунта; два парсера класса Кано; три `switch` о порядке
стадий; восстановление, возвращавшее заявку в одно состояние, и завершение, умевшее брать её только из
другого; уборка, сносившая носителя, который один и мог опровергнуть её посылку.

---

# I. Приём требования

**`ProjectFlowService`** (6809 строк) — главный цех: принимает проект и заявки клиента, собирает граф задач,
ставит в очередь, отправляет, считает блокеры.
*Связи:* вызывают 12 механизмов; зовёт 29; **пишет 13 хранилищ из 46**.
*Ценность:* единственная точка, где требование становится исполнимой работой.
*Комментарий:* **ядро, и самая тяжёлая проблема фабрики.** Тринадцать хранилищ у одного класса означают, что
почти всякая правка ядра — правка здесь, и потому критерий Куайна нарушается почти каждым коммитом. Это не
придирка к размеру: пока приём, компиляция, отправка и подсчёт блокеров живут вместе, «не менять ядро»
физически невозможно.
*Философия:* `PART_WHOLE_OWNERSHIP` (D004) — **слабая**. Опровержение: найти поле, которое пишут два пути
внутри него. Разделять начинать нельзя, пока владение не объявлено — образец требует именно этого порядка.


*Живое, 9 сентября 2026, Codex: 10-такт 3/10 — project flow/orchestration cluster, без правки кода.* Третий кластер десятиактного прохода заполнен как lifecycle/orchestration mechanism, not as eight scattered `findAll()` lines. The common subject is the project work loop: `ProjectFlowService` creates and lists work, `ContinuousOrchestrationService` chooses the next tick, `AutoMergeService` reconciles PR truth, `BranchGarbageCollectorService` retires dead branches, and `StrandedFinalizingSweepService` releases a transient claim when the original holder is gone.

*Идеальная форма кластера:* every lifecycle decision is made from a named boundary: selected project, selected task set, selected session set, active projects, or PR repository identity. A read may span the factory only when the endpoint is explicitly an operator directory or a maintenance sweep with a named bound/order. Reconciliation mechanisms must preserve the event law: GitHub truth changes review/task/session state only through the owner that owns that transition, and cleanup must never close a live worker's PR or free a live holder's claim.

*Граница и взаимодействия:* upstream owners are `ProjectRepository`, `TaskRepository`, `JulesSessionRepository`, `PrReviewRepository`, `AccountRepository`, `WishlistRepository`, `GitHubPullRequestService`, `OperationalPolicyService`, `SystemProgressTracker`, `SessionLifecycleService`, and `FeatureThreadRepository`. Downstream readers include `ProjectController`, `ContinuousOrchestrationService`, `AutoMergeService`, `BranchGarbageCollectorService`, `GeminiObserverActionService`, `JulesDispatchService`, operational policy/flow, and dashboard/context services. The cluster orders and reconciles work; it must not become a second owner for account capacity, session lifecycle, review approval, or delivery truth.

*Инварианты:* (1) active project iteration uses the active-project predicate; (2) session matching for a project begins with that project's task ids or an explicit PR-token lineage, not all historical sessions; (3) GitHub PR truth is matched by repository identity and session token before spend or destructive cleanup; (4) `finalizing` is a transient guard and is released only by CAS after a named age bound; (5) system stall says `stalled` only when actionable work and capacity are both proven by the same capacity language used elsewhere; (6) project list is an operator directory with explicit ordering/bound, not an accidental full-table permission; (7) every destructive cleanup is rechecked fresh by the action owner.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `ProjectFlowService.java:1755` in `selectBadSession(tasksById, sessionId)` | `JulesSessionRepository` / sessions, bounded by caller-provided task map | recovery/follow-up selection inside project flow | `task-set session selection` | Replacement-known. If `sessionId` is absent, the method needs active sessions whose `taskId` is in `tasksById`. Refutation: bad-session selection materializes sessions from unrelated projects/tasks before filtering. Closure: acquisition uses `findByTaskIdIn(taskIds)` or a task+status predicate and preserves `badSessionRisk` ordering plus `updatedAt` nulls-first tie-break. |
| `ProjectFlowService.java:5036` in `highestMergedPrNumber(project)` | `JulesSessionRepository` + `TaskRepository`, with GitHub PR snapshot | coverage-audit freshness and product-code merge watermark | `project PR/session lineage` | Replacement-needed. The project boundary is `project.id`; current code reads all sessions and performs per-session task lookup. Refutation: coverage audit for project A observes sessions/tasks from project B or does N+1 task lookups. Closure: project task ids scope session acquisition, `isSystemRecordPr` keeps the same exclusion, and highest merged PR number is unchanged on fixture. |
| `ProjectFlowService.java:6429` in `listProjects()` | `ProjectRepository` / projects | `ProjectController` list and current-project fallback route | `operator project directory` | Allowed only if documented as bounded operator list. Not yet ideal because ordering/pagination/visibility contract is implicit. Refutation: endpoint returns nondeterministic order or unbounded growth as normal API data. Closure: explicit order and bound/pagination decision, or a documented reason that the full project directory is intentionally operator-wide. |
| `ContinuousOrchestrationService.java:433` in `checkForSystemStall()` | `AccountRepository` / accounts plus `SystemProgressTracker` and `systemWorkSnapshot` | every orchestration tick before auto-recovery | `stall capacity witness` | Replacement-needed. The question is existence of enabled idle capacity, but the broader cluster requires dispatcher-aligned capacity semantics. Refutation: stall status becomes `ok/stalled` because any idle account exists, while dispatcher would reject it for key/status/project/capacity. Closure: use an exists/projection predicate that names the same eligibility level the stall claim means. |
| `AutoMergeService.java:368` in `belongsToActiveProject(review)` fallback | `ProjectRepository` / projects, repository identity from PR URL | automerge spend guard for reviews not traceable by session→task | `PR-to-project attribution fallback` | Replacement-needed, not deletable. It protects spend=0 for unattributable reviews, but reads all projects to match repository. Refutation: a review with PR URL for one repo scans every project or matches archived/duplicate repository identity ambiguously. Closure: repository identity lookup by owner/repo is explicit, deterministic across statuses, and preserves the unattributable-review rule. |
| `StrandedFinalizingSweepService.java:101` in `sweep()` | `ProjectRepository` / projects | one-minute scheduled sweep | `active-project maintenance sweep` | Replacement-known. Existing `findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` states the intended population. Refutation: sweep loads accepted/frozen/archived projects before filtering. Closure: active-project predicate drives iteration; CAS release semantics in `sweepProject` are unchanged. |
| `BranchGarbageCollectorService.java:253` in `cleanOrphanedAndStagnatedPullRequests(project)` | `JulesSessionRepository` / sessions matched by external session token, with task/project check | called by orchestration tick, automerge, observer action | `GitHub PR session-token lineage` | Replacement-needed and destructive-path sensitive. `findByExternalSessionIdIsNotNull()` is only a partial improvement; ideal acquisition is project-task scoped and token-match capable. Refutation: branch GC sees sessions from other projects before deciding whether to close/delete a PR. Closure: open PR matching uses project-scoped sessions with external ids, preserves persistent-worker skip and stale `lastProgressAt` rule. |
| `BranchGarbageCollectorService.java:344` in `findOrphanedPrCandidates(project)` | same session-token lineage plus orphan status set | read-only detector for Gemini observer and action re-verification | `orphaned PR candidate detector` | Replacement-needed. It caches all sessions once, then matches project PRs; correct semantics require only sessions that could belong to this project's open PRs. Refutation: candidate detector loads historical sessions for unrelated projects or misses terminal-session PRs after query rewrite. Closure: project-scoped external-session acquisition preserves `ORPHANED_CANDIDATE_SESSION_STATUSES`, done-task skip and candidate fields. |

*Сильная форма:* every row above either has a scoped repository predicate or an explicit operator-directory/maintenance reason. Lifecycle transitions remain owned by their original owner: ProjectFlow admits work, ContinuousOrchestration orders, AutoMerge reconciles merged truth, BranchGC retires branch/session evidence, StrandedFinalizing releases only stale transient guards by CAS. A fixture with two projects, overlapping PR numbers, unrelated sessions and mixed project statuses produces identical chosen sessions, watermarks, stall status and cleanup candidates before/after any later query rewrite.

*Слабая форма:* the cluster remains behaviorally plausible because Java filters eventually narrow the answer, but acquisition is global. That makes dangerous paths depend on irrelevant rows: a cleanup path can scan unrelated sessions before deciding to close a PR, a stall path can use a weaker account-capacity witness, and an attribution fallback can read every project before withholding or spending external API work.

*Опровержение:* seed two active projects and one archived project with distinct repositories, tasks, sessions, PR URLs/head refs and finalizing wishlists. Run the selection/detection methods with SQL logging or repository spies. If any decision path materializes rows outside its declared project/session/repository/status boundary, or if replacing acquisition changes destructive cleanup decisions, the mechanism is not ideal.

*Критерий закрытия:* this record is complete when all eight full-table call sites above name owner, boundary, refutation and closure. The implementation becomes ideal only when active-project sweeps use status predicates, task/session reads are project-scoped, branch/PR token matching is project-bound, project directory semantics are explicit, and tests prove GitHub cleanup/stall/watermark decisions unchanged except for acquisition cost.

*Кандидат на будущую реализацию Codex:* after explicit code approval, safe first candidates are `StrandedFinalizingSweepService.sweep()` and `ProjectFlowService.selectBadSession` because existing repository predicates cover their boundaries. `ProjectFlowService.highestMergedPrNumber`, `BranchGarbageCollectorService` and `AutoMergeService.belongsToActiveProject` need fixture protection first; they are not safe as isolated edits because they sit on destructive or spend-guard paths.

*Текущий статус:* not ideal. The connected lifecycle/orchestration record is now explicit, but implementation still contains eight global acquisitions in this cluster, including destructive cleanup paths and one implicit operator-directory endpoint.

*Свидетельства такта:* `grep -n "\.findAll()"` across `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, `StrandedFinalizingSweepService`, `BranchGarbageCollectorService`; focused `nl -ba` contexts around lines 1755, 5036, 6429, 433, 368, 101, 253 and 344; repository grep for `ProjectRepository.findByStatusOrderByCreatedAtDesc`, `JulesSessionRepository.findByTaskIdIn`, `findByExternalSessionIdIsNotNull`, and `TaskRepository.findByProjectIdOrderByCreatedAtDesc`; caller grep for `cleanOrphanedAndStagnatedPullRequests`, `retireAbandonedBranchAndPR`, `reconcileMergedGitHubPullRequests`, `listProjects`, `repairMisclassifiedJulesAccountLimits`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, `StrandedFinalizingSweepService` or `BranchGarbageCollectorService` как отдельные query-cleanups: сначала сохрани единый lifecycle law — admission creates work, orchestration orders work, automerge/branch-gc reconcile GitHub truth, stranded sweep restores transient claims — and prove by fixture that project/session/PR identities and event transitions stay identical except for acquisition cost. Следующий такт 4/10 — Jules operations cluster: `JulesDispatchService`, `JulesSessionController`, `JulesMonitorController`, `InternalJulesActivitiesProbeController`, `JulesConfigController`, GitHub webhook lineage and session/activity freshness. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для владения частями дополнительно держать `PART_WHOLE_OWNERSHIP`.*

**`ProjectFactoryService`**, **`GitHubProjectFactoryClient`**, **`LinearProjectFactoryClient`**,
**`ProjectWorkspaceFactoryService`** — провижининг: репозиторий, проект в Linear, рабочее пространство.
*Связи:* цепочка от `ProjectFlowService` вниз; наружу пишет только `ProjectHotspotFileRepository`.
*Ценность:* приём требования не должен зависеть от того, ответил ли GitHub.
*Комментарий:* **ядро.** Провижининг вынесен из транзакции приёма намеренно; отказ пишется в `factoryStatus`,
а проект принимается.
*Философия:* `BOUNDARY_TOPOLOGY` (D006) — **сильная**, заслонена структурно: во всём транзитивном графе
вызовов приёма нет сетевых клиентов. Опровержение: внести сетевой вызов в граф приёма — тест обязан покраснеть.

**`RequirementGroundingService`** — сверяет текст клиента с корпусом строгих понятий и **дописывает** контекст.
*Связи:* вызывает `ProjectFlowService`; зовёт `GeminiContextService`; ничего не пишет.
*Ценность:* обостряет расплывчатое требование до декомпозиции, не заменяя слов клиента.
*Комментарий:* **периферия**, и самый аккуратный механизм из тех, кто трогает клиентский текст: он добавляет,
а не переписывает. Правка чужого намерения своими словами была бы подменой предмета.
*Философия:* `INTENSION_COMPATIBILITY` (D001) — **сильная**. Опровержение: найти случай, где исходный текст
клиента не дошёл до промпта целиком.

**`MarketCorpusService`** — что продукт данного класса обязан содержать независимо от того, что клиент вспомнил.
*Связи:* вызывают четверо, включая слой приёмки; ничего не пишет.
*Ценность:* компилятор строит только описанное в брифе; корпус закрывает разрыв между «что просили» и «что
обязано быть».
*Комментарий:* **периферия.** Риск, которого он пока избегает: корпус — чужое убеждение о классе продуктов, и
если он начнёт **заказывать** объём, а не сообщать о нём, фабрика примется строить не то, что просил клиент.
Граница выдержана: корпус читают, решает компилятор.
*Философия:* `RELIABILITY_CHAIN` (D010) — **не мерено**. Опровержение: назвать возраст записи корпуса и
правило её свежести.

**`MarketComplianceGate`** — называет **уставные** требования, которых план не покрывает.
*Связи:* вызывает `ProjectFlowService`; зовёт `MarketCorpusService`.
*Ценность:* начинать надо там, где ошибиться нельзя.
*Комментарий:* **периферия, образцовое самоограничение.** Он мог бы судить о полноте вообще и стал бы
источником бесконечного шума. Ограничение законом выбрано потому, что это единственная область, где «не
покрыто» есть факт, а не мнение.
*Философия:* `PROHIBITION_AS_CODE` (D006) — **сильная**. Опровержение: найти пункт, где гейт судит о вкусе.

**`MarketResearchService`** — превращает непроверенные записи корпуса в измеренные: сессия смотрит на реально
существующие продукты и коммитит найденное файлом.
*Связи:* вызывающих нет — идёт от контроллера; пишет `TaskRepository`.
*Ценность:* ответ модели в чате — свидетельство слуха.
*Комментарий:* **периферия; философски один из лучших механизмов системы.** Он воплощает главное правило всей
модели — разницу между «кто-то написал» и «так устроено».
*Философия:* `RAG_GROUNDING_CAPSULE` (D014) — **сильная**. Опровержение: найти запись корпуса без источника.

**`OnboardingAuditService`**, **`RepositoryStackAnalyzer`**, **`StackProfile`** — разбор чужого репозитория при
brownfield-приёме.
*Связи:* от `ProjectFlowService` вниз; пишет `ProjectRepository`.
*Ценность:* фабрика умеет входить в чужой проект, а не только рождать свой.
*Комментарий:* **ядро по демаркации** — ошибочный разбор стека остановит весь поток проекта.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06
DEONTIC-CONSISTENCY`, принцип четырёхзначной логики (True / False / Both / Neither), anchor *A Useful
Four-Valued Logic / how a computer should think*. Сильная форма дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно… **третий исход невозможно проигнорировать на стороне вызывающего**».
Слабая: «**булево плюс `null`, трактуемый по месту**». Опровержение образца: «найти вызывающего, который
компилируется, **не обработав „неизвестно“**».

*Замер 2026-09-06 — форма **слабая**, и совпадает со слабой формой образца дословно.* Механизм возвращает
`StackProfile` — запись из одиннадцати полей, где `hasCI`, `hasTests`, `isMonorepo` суть **простые булевы**, а
остальное строки, которые могут быть `null`. Третьего значения нет ни у одного поля. Значит **«CI не найден»
и «не удалось определить, есть ли CI» — это одно и то же `false`**, и различить их снаружи нечем.

*Опровержение выполнено, и сильнее, чем требовалось.* Образец просит найти вызывающего, который
компилируется, не обработав «неизвестно». Найден вызывающий, который не обрабатывает **ничего**:
`ProjectFlowService:384` — `onboardingAuditService.runOnboardingAudit(saved);`, результат выброшен, это
оператор, а не присваивание. Второй вызывающий, `ProjectController:114`, значение принимает.

*Почему это не мелочь.* Онбординг — то место, где фабрика впервые узнаёт о продукте клиента, есть ли у него
сборка и тесты. Схлопывание «не знаю» в «нет» здесь стоит рядом с пунктом 37: числитель никогда не
спрашивает, зелен ли `main`, а механизм, который мог бы это установить при заведении проекта, отвечает
`false` и когда не нашёл, и когда не смог посмотреть. Это ровно тот дефект, который записан паттерном такта:
**«я не знаю» ≢ «всё хорошо»**, только вывернутый — здесь «я не знаю» стало «всё плохо», и обе подмены
одинаково лишают решения основания.

*Что делать:* три булевых поля заменить трёхзначным исходом (`есть / нет / не определено`), и заставить
вызывающего с ним считаться. Выброшенный результат в `ProjectFlowService:384` — отдельная строка работы: либо
он не нужен, и тогда вызов должен это объявлять, либо нужен, и тогда его надо читать.

*Опровержение, назначенное вперёд:* завести проект с недоступным репозиторием и посмотреть, чем `hasCI`
будет отличаться от проекта без CI. Ничем — пункт подтверждён окончательно.
# II. Компиляция: требование становится задачами

**`TechnicalLeadCompiler`** (1758 строк) — превращает заявку в типизированные задачи с ролями, наследующие эпик.
*Связи:* вызывают `ProjectFlowService` и `DeliveredWorkJudgmentService`; зовёт 9 механизмов; пишет 4 хранилища.
*Ценность:* единственная законная дорога от намерения к работе. Задача в обход компиляции эпика не наследует,
а завершённость эпика — то, чем меряется ценность; такая задача исполнится, сольётся и не сдвинет ничего.
*Комментарий:* **ядро.** Закон 3 держится и заслонён. Но добавленное сегодня ограничение области он ещё не
исполняет: наблюдение «не заехало» о требовании, уже заказанном и не отменённом, есть факт о доставке, а не
новое требование.
*Философия:* `INSTITUTIONAL_FACT_REGISTER` (D007) — **сильная** по созданию задачи (создаётся правилом,
наследует эпик), **не держится** по ограничению области. Опровержение: провалить доставку дважды и посчитать
заявки — вторая означает, что ограничение не введено.

**`FeatureService`** — выдаёт стабильную личность эпика; эпик чеканится лениво, один раз, при превращении
заявки в настоящую работу.
*Связи:* вызывают `ProjectFlowService`, `TechnicalLeadCompiler`; пишет `FeatureRepository`, `WishlistRepository`.
*Ценность:* без стабильной личности эпика нельзя измерить завершённость, а значит и ценность.
*Комментарий:* **ядро.** Ленивая чеканка верна: эпик, заведённый на заявку, которая работой не станет,
навсегда испортит знаменатель. Заявка без эпика становится эпиком самой себе — механизм не падает, а лишь не
находит причины для продолжения. Это правильная форма третьего исхода.
*Философия:* `ACTUAL_OBJECT_REGISTER` (D002) — **сильная**. Опровержение: найти эпик, заведённый на заявку,
не ставшую задачей.

**`EpistemicMetadataClassifier`** — извлекает домен по Кеневину и класс по Кано из свободного текста.
*Связи:* зовёт только `FeatureService`; ничего не пишет.
*Ценность:* до него путь компиляции звал формулу с двумя `null`, и все эпики получали одинаковую константу.
*Комментарий:* **периферия.** Случай назван прямо: **постоянная оценка не различает ничего** — самый коварный
род муды, потому что цифра на дашборде была, а различения не было.
*Философия:* `LEVEL_OF_ABSTRACTION_LOCK` (D010) — **сильная**. Опровержение: посчитать дисперсию оценок; ноль
означает возврат к видимости измерения.

**`KanoClass`** — единственное правило чтения класса Кано из плана компилятора.
*Связи:* вызывают четверо, включая оба парсера плана; ничего не пишет.
*Ценность:* правило жило в двух парсерах, и они разошлись — все пять эпиков проекта вышли одного класса.
*Комментарий:* **ядро** (класс влияет на очерёдность). Чистое исполнение устава: два независимо
поддерживаемых экземпляра одного вычисления запрещены.
*Философия:* `ANCHOR_BOUND_NAME` (D001) — **сильная**. Опровержение: найти второй парсер класса Кано.

**`EmsFlowStage`** — единственный источник истины о стадии каждой роли.
*Связи:* вызывают 7 механизмов; ничего не пишет.
*Ценность:* заменил три независимых `switch`, разошедшихся во мнениях. Порядок в графе решает, что идёт
параллельно, а что зависит.
*Комментарий:* **ядро.** Тот же урок, что у `KanoClass`, и это уже закономерность: **всякий раз, когда правило
записано в двух местах, они расходятся — вопрос лишь во времени.**
*Философия:* `ANCHOR_BOUND_NAME` (D001) — **сильная**. Опровержение: найти второй источник порядка стадий.

**`SelfFalsificationEpicMatcher`** — не покрывает ли существующий эпик ту же клиентскую задачу, прежде чем
заводить новый.
*Связи:* вызывает `ProjectFlowService`; зовёт `MLPredictionServiceClient`.
*Ценность:* куайновская паутина убеждений — не добавляй структуру в ядро, если старая объясняет наблюдение.
*Комментарий:* **периферия; детерминированный, не-ИИ.** Существенно, что это не запрос к модели: суждение о
тождестве требований воспроизводимо.
*Философия:* `SUBSTITUTION_ORACLE` (D009) — **сильная**. Опровержение: подать два описания одного требования
разными словами и посмотреть, завёл ли он второй эпик.

**`WishlistContentSimilarityMatcher`** — ловит семантические дубликаты заявок.
*Связи:* вызывают `FalsificationCycleService`, `JulesDispatchService`; ничего не пишет.
*Ценность:* один и тот же лимитер сообщений был опознан как пробел дважды и дважды реализован — целый цикл
работы в мусор.
*Комментарий:* **периферия.** Неудобное надо назвать: он **лечит следствие**. Причина того, что одно
требование опознаётся дважды, — в том, что порождающая сторона не помнит, о чём уже сообщала. Ловля на входе
дешевле памяти на выходе, и решение принято верно; но по модели это уборка, родня той, которую пришлось
переносить в точку записи.
*Философия:* `SUBSTITUTION_ORACLE` (D009) — **сильная**. Опровержение: подать дубликат и посчитать заявки.

**`WishlistService`**, **`StrandedFinalizingSweepService`** — ведение заявок и освобождение притязания,
застрявшего в `finalizing`.
*Связи:* вызывающих нет — оба идут от контроллера и по расписанию; оба пишут `WishlistRepository`.
*Ценность:* `finalizing` — сторож, а не место отдыха; если поток умер посреди работы, строку не двигает никто.
*Комментарий:* **ядро.** Закон 8 держится и заслонён. Остаток назван честно: **аренда в три минуты не
продлевается**, пока под ней идёт работа. Пока разбор занимает секунды — запас двукратный по порядку; при
росте графа дефект вернётся молча.
*Философия:* `BELIEF_UPDATE_LEDGER` (D007) — **сильная** по освобождению, **слабая** по сроку: предел
назначен, а не выведен. Опровержение: задержать работу под арендой дольше трёх минут.

---

# III. Отправка: задача уходит в работу

**`JulesDispatchService`** (5752 строки) — отправляет задачи, ведёт жизненный цикл сессии, принимает
завершения, строит граф задач из срезов плана.
*Связи:* вызывают 6; **зовёт 27 механизмов; пишет 7 хранилищ**.
*Ценность:* здесь фабрика встречается с внешним миром.
*Комментарий:* **ядро; вторая по тяжести проблема после `ProjectFlowService`, и по той же причине.** Почти
все дефекты потока за смену жили тут, и это не совпадение: **где больше всего мест, там больше всего
возможностей спутать своё убеждение с чужим ответом.** Естественная граница уже видна: транспорт отделён
верно — надо продолжить линию и отделить **завершение** сессии от **отправки**, потому что спорили именно они.
*Философия:* `PART_WHOLE_OWNERSHIP` (D004) — **слабая**. Опровержение: найти поле, которое пишут два пути
внутри него.


*Живое, 9 сентября 2026, Codex: 10-такт 4/10 — Jules operations cluster, без правки кода.* Четвёртый кластер десятиактного прохода заполнен как Jules operations surface, not as controller cleanup. Общий предмет: task dispatch spends external Jules capacity, status polling turns external session evidence into local lifecycle, manual/internal endpoints expose session evidence for repair, and GitHub webhooks must be attributed to the right task before they trigger a reviewer.

*Идеальная форма кластера:* every Jules-facing read declares which contract it serves: exact lookup by session/task/token; bounded operator/admin list; role catalogue for persistent philosophical audit; account API-key owner for dispatch; or webhook PR→task lineage. Exact lookup must never load a full table; operator lists must be explicitly bounded/ordered/masked; external-spend paths must prove the account/key, task, project and branch/PR refer to the same work before dispatching.

*Граница и взаимодействия:* upstream owners are `JulesSessionRepository`, `AccountRepository`, `RoleRepository`, `JulesConfigRepository`, `TaskRepository`, `ProjectRepository`, `PrReviewPipelineService`, `ClaimService`, `BranchGarbageCollectorService`, `SystemSettingsService`, `JulesApiClient`, and GitHub webhook payload. Downstream effects include Jules API spend, claim completion, reviewer dispatch, persistent philosophical-audit closure, operator cancellation/patching, diagnostic activity probes and branch-GC verification. The controllers may expose or trigger operations; they do not own session lifecycle truth.

*Инварианты:* (1) dispatch uses the account key that owns the session/work, not an arbitrary usable key; (2) a manual all-session view is labelled and bounded as operator/admin data; (3) token lookup preserves the same token matching used by BranchGC/GitHub PR evidence; (4) active role catalogue order/freshness is the same one used by falsification records; (5) API keys are never returned unmasked; (6) webhook processing never chooses a task by repo-name heuristic when branch/session/task evidence exists; (7) status polling remains the owner that turns Jules evidence into local session state.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `JulesDispatchService.java:359` in `dispatchAdHocSessionToBranch` | `AccountRepository` / accounts and API keys | manual/ad-hoc branch dispatch through controller and recovery callers | `external-spend account-key selection` | Replacement-needed. The method needs one usable key for a known active project/branch, but all-account scan can pick a key without proving enabled/status/project/key semantics. Refutation: ad-hoc dispatch materializes unrelated/decommissioned accounts or spends through a key the normal dispatcher would reject. Closure: acquisition uses an explicit usable-key predicate or delegates to the account selector semantics, while preserving the rare-tail no-session/no-task nature. |
| `JulesDispatchService.java:2992` in `completePersistentPhilosophicalAuditCycle` | `RoleRepository` / role catalogue | persistent philosophical-audit completion path | `active role catalogue` | Unresolved by existing `AGY_ASKS`: the mechanism needs active roles and their order/freshness before deciding all critiques are covered. Refutation: completion loads role entities just to derive tags, or closes the discussion with a role order/count different from the corpus. Closure: `findByActiveTrueOrderByTagAsc`/`countByActiveTrue` or documented static catalogue, after the active-role order question is answered. |
| `JulesSessionController.java:46` in `GET /api/jules-sessions` | `JulesSessionRepository` / sessions | public/manual operator endpoint with optional `taskId` | `session list API` | Not ideal. `taskId` path is scoped; no-param path is an implicit all-session list. Refutation: endpoint returns unbounded historical sessions as ordinary API data. Closure: no-param mode is documented as bounded operator list with order/page/filter, or route requires explicit task/project/status scope. |
| `JulesMonitorController.java:23` in `GET /api/monitor/sessions` | `JulesSessionRepository` / sessions | monitor dashboard endpoint | `monitor session list` | Replacement-needed or explicit admin-list decision. It duplicates all-session exposure separate from `JulesSessionController`. Refutation: two session-list surfaces diverge in ordering/bounds/filtering. Closure: shared bounded query/DTO contract, or one surface is removed/declared diagnostic-only. |
| `InternalJulesActivitiesProbeController.java:65` in `probe(sessionId, pageSize, pageToken)` | `JulesSessionRepository` + `AccountRepository` | localhost diagnostic for Jules activities pagination | `exact external-session lookup` | Replacement-needed. The probe needs one session by external id to get the owning account key. Refutation: exact probe loads all sessions before matching a single externalSessionId. Closure: repository exact lookup by externalSessionId preserves fallback to `jules_api_key` when no session/account key exists. |
| `InternalJulesActivitiesProbeController.java:143` in `sessionByToken(token)` | `JulesSessionRepository` / sessions with external ids | localhost diagnostic for PR/token ownership | `token-to-session diagnostic` | Replacement-needed but token-sensitive. Existing `findByExternalSessionIdIsNotNull()` is a partial boundary; ideal query should not load sessions without tokens and may remain diagnostic with explicit cap/order. Refutation: token probe scans tokenless/historical rows or reports matches without status/task evidence. Closure: only external-id sessions are acquired, response is bounded, and fields preserve sessionId/taskId/status/prUrl/createdAt. |
| `JulesConfigController.java:24` in `GET /api/jules-configs` | `JulesConfigRepository` / Jules configs | admin config list | `small admin configuration list` | Allowed only as explicitly bounded admin reference data. Not ideal until order/bound and masking test are documented. Refutation: endpoint returns raw API key or nondeterministic/unbounded config rows. Closure: masked DTO contract is tested and list order/bound is explicit. |
| `GithubWebhookController.java:78` in PR opened webhook | `TaskRepository` / tasks plus GitHub payload | external webhook triggers review dispatch | `webhook PR-to-task lineage` | Replacement-needed and semantic, not just cost. The current heuristic reads all tasks, matches repository name, and picks first `claimed` task. Refutation: two claimed tasks in the same repo allow the webhook to complete the wrong claim or dispatch reviewer for the wrong task. Closure: webhook derives task from branch/session/PR lineage or a repository+branch predicate, and signature/attribution is handled before any dispatch spend. |

*Сильная форма:* exact lookups use exact repository predicates; lists are bounded operator/admin surfaces; token diagnostics use only token-bearing sessions; role-catalogue reads share the same active-role source/order as falsification; webhook dispatch is derived from PR branch/session/task evidence and not from first claimed task in a repo.

*Слабая форма:* the UI and diagnostic endpoints work on a small database because Java streams eventually filter to the right row, while external-spend paths can still acquire unrelated accounts/sessions/tasks before making a decision. This is dangerous here because the output is not just a report: it may spend Jules quota, complete a claim, dispatch a reviewer, or close a persistent audit discussion.

*Опровержение:* create two sessions with different account keys, two claimed tasks in the same repository, a tokenless historical session set, and a role catalogue fixture. If exact probes/list routes materialize unrelated rows, if webhook chooses by repo-only heuristic, if an API key is unmasked, or if philosophical-audit completion disagrees with the role catalogue order/count, the mechanism is not ideal.

*Критерий закрытия:* this record is complete when all eight Jules-operation full-table reads name contract, owner and closure. The implementation becomes ideal only when exact lookup/list/admin/webhook contracts have tests or probes, the role catalogue question in `AGY_ASKS.md` is answered, and external-spend paths cannot choose account/task/session evidence outside their declared boundary.

*Кандидат на будущую реализацию Codex:* after explicit code approval, safest first candidates are exact lookup replacement for `InternalJulesActivitiesProbeController.probe` and bounded/read-only DTO work for session/config list surfaces. `GithubWebhookController` and `JulesDispatchService.dispatchAdHocSessionToBranch` need attribution/account-key fixtures first; the role read is blocked by the existing active-role order question.

*Текущий статус:* not ideal. The connected Jules operations record is now explicit; implementation still has eight full-table acquisitions in the cluster and one pre-existing unresolved role-catalogue contract.

*Свидетельства такта:* `grep -n "\.findAll()"` across `JulesDispatchService`, `JulesSessionController`, `JulesMonitorController`, `InternalJulesActivitiesProbeController`, `JulesConfigController`, `GithubWebhookController`; focused `nl -ba` contexts around lines 359, 2992, 46, 23, 65, 143, 24 and 78; repository reads for `JulesSessionRepository`, `AccountRepository`, `RoleRepository`, `JulesConfigRepository`, `TaskRepository`; caller grep for session/controller/webhook/pollStatus surfaces; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `JulesDispatchService` or Jules controllers как отдельные query-cleanups: сначала раздели three surface contracts — exact session lookup, bounded/operator session list, and dispatch/webhook lineage — and preserve account-key ownership, active-role catalogue order, token-to-session evidence and API-secret masking. Следующий такт 5/10 — quality gate/process-control cluster: `ProcessControlService`, `QualityGateController`, already-recorded `QualityMetricsController`/`SixSigmaAuditService`, and aggregate-vs-detail contract boundaries. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для отправки во внешний мир дополнительно держать `TELEOSEMANTIC_FEEDBACK` and `PART_WHOLE_OWNERSHIP`.*

**`JulesApiClient`** — единственное место HTTP-вызова к Jules.
*Связи:* вызывают двое; зовёт `AccountHealthService`, `TaskTitleBuilder`; ничего не пишет.
*Ценность:* один транспорт, много решающих.
*Комментарий:* **ядро; доведён до правильного рода.** Две вещи образцовы. Проверка источника различает три
исхода, и «не смог проверить» больше не проходит как «всё хорошо». Отказ называет **что было послано** —
длину промпта, источник, ветку; именно этот замер позже опроверг мою же гипотезу о длине промпта. **Механизм,
сделавший возможным опровержение своего автора, — лучшее, что даёт наблюдаемость.**
*Философия:* `TRUTH_STATUS_TABLE` (D012) — **сильная**, заслон `JulesApiClientTest`. Опровержение: уронить
листинг источников и посмотреть, ушёл ли POST на создание сессии.

**`SessionLifecycleService`** — знает ли сам Jules, что сессия закончена.
*Связи:* вызывают двое; зовёт `JulesApiClient`; пишет `JulesSessionRepository`.
*Ценность:* наше «отменено» было местной выдумкой.
*Комментарий:* **ядро.** Тот же дефект, что «готово» без доставки, только на внешней стороне: **наше слово о
чужом состоянии — не свидетельство о нём.** Проверено настоящим вызовом: последующий GET даёт подлинный 404.
*Философия:* `PERFORMATIVE_COMMIT` (D003) — **сильная**. Опровержение: отменить сессию и спросить Jules.

**`PersistentWorkerSessionService`** — одна долгоживущая сессия на (проект, назначение) вместо новой на цикл.
*Связи:* вызывают 4; зовёт только своё хранилище; **не пишет** — учёт ведёт через сущность сессии.
*Ценность:* новая сессия каждый оборот — новая ветка и новый PR каждый оборот, то есть прямая трата внешнего
бюджета на то, что можно не тратить.
*Комментарий:* **ядро (ёмкость).** Граница проведена строго: говорит только со своей таблицей, никогда с
транспортом Jules напрямую.
*Философия:* `PART_WHOLE_OWNERSHIP` (D004) — **сильная**. Опровержение: найти в нём обращение к транспорту.

**`ClaimService`** — притязания: кто взял, на какой срок, кто освободил; здесь же счёт попыток против предела.
*Связи:* вызывают 6; зовёт 4; пишет 4 хранилища.
*Ценность:* без притязания две части фабрики делают одну работу дважды.
*Комментарий:* **ядро.** Один открытый вопрос назван: предел `A_max` списывается и на отказах, ничего не
установивших о требовании. Считать потраченную ёмкость **верно** — запрос был сделан. Неверно превращать
исчерпание в вердикт о требовании. Отдельно измерено: его «самоисцеление» — освобождение притязания у задачи,
не держащей сессии — дало **одну повторную отправку на семь**, то есть 14% внешнего бюджета.
*Философия:* `INSTITUTIONAL_FACT_REGISTER` (D007) — **сильная** по притязанию, **слабая** по последствию
исчерпания. Опровержение: исчерпать предел одними внешними отказами и посмотреть на итоговый статус требования.

**`LeaseWatchdogService`** (29 строк) — жнёт истёкшие аренды.
*Связи:* вызывающих нет — идёт по расписанию; зовёт `ClaimService`.
*Ценность:* притязание без срока — застрявшая навсегда задача.
*Комментарий:* **ядро.** Двадцать девять строк, держащие свойство, ради которого написан закон 8. **Размер
механизма не говорит о его месте в ядре** — это стоит помнить, глядя на шеститысячные классы рядом.
*Философия:* `DEFEASIBLE_EXCEPTION_LEDGER` (D012) — **сильная**. Опровержение: найти притязание без срока.

**`AccountHealthService`** — единственный владелец здоровья аккаунта; выученная суточная ёмкость,
экспоненциальный откат, пересмотр убеждения по свидетельству.
*Связи:* вызывают трое; зовёт лестницу продвижения решателя; пишет 3 хранилища.
*Ценность:* статус писали четыре разных места, включая сырые запросы, молча терявшие отметку времени.
*Комментарий:* **ядро (ёмкость); самое поучительное за смену.** Механизм был **полон и правилен** — и не
срабатывал ни разу, потому что распознаватель искал слова, которых внешняя сторона не шлёт, а чинящий это
мост читал строку сессии, которую отказ и не дал создать. **Путь свидетельства зависел от объекта, чьё
несоздание и было свидетельством.** Исправлено и заслонено.
*Философия:* `TRUTH_STATUS_TABLE` (D012) — **сильная**, заслон `AccountHealthServiceLaw14Test`. Остаток:
откат удваивается, ничего не зная о периоде пополнения. Опровержение: пережить обнуление суточной квоты и
замерить, сколько фабрика простояла после него.

**`BottleneckAwarePriorityService`**, **`BottleneckDetectionService`** — приоритет с оглядкой на узкое место.
*Связи:* вызывает `TechnicalLeadCompiler`; ничего не пишут.
*Ценность:* очередь без приоритета обслуживает не то, чем ограничен поток.
*Комментарий:* **периферия** с оговоркой: приоритет не может **остановить** работу, только переупорядочить.
Это половина Голдратта; вторую половину держит `TocSubordinationLever`, и он до сих пор в тени.
*Философия:* `DECISION_EXPECTED_LOSS` (D005) — **слабая**. Опровержение: показать случай, где неограничение
уступило ограничению.

---

# IV. Ревью, гейт, слияние

**`AutoMergeService`** (2967 строк) — привратник слияния; единственный, кому позволено решить, что
продуктовый код едет в main.
*Связи:* вызывающих нет — идёт по расписанию; **зовёт 18 механизмов; пишет 8 хранилищ**.
*Ценность:* всякий путь к main проходит здесь.
*Комментарий:* **ядро.** Его заслон — **образец правильного рода во всей системе**, и на него следует
равняться остальным: не список запретных имён, а **множество** мест слияния, ломающееся на любом новом, как
бы то ни называлось. Первая версия была списком имён и зеленела бы, стоило написать тот же обход под другим
названием. Разница между этими версиями и есть разница между заслоном и его видимостью. Но восемнадцать
вызываемых и восемь хранилищ — третий по тяжести случай нераспределённого владения.
*Вытягивание, внесено 2026-09-06 по указанию оператора (предписание 41, пункт 1 — сделан).* Здесь, в `recordSuccessfulMerge`, стоит **единственная точка потребления во всей фабрике**: PR с продуктовым кодом лёг в `main`, и задача помечается сделанной именно поэтому. По Lean карточка возвращается ровно тут — не на провале задачи и не на закрытии носителя, это фабрика разговаривает сама с собой. Возврат карточки есть право произвести следующее, то есть немедленный `projectFlowService.dispatchQueuedTasks(projectId)` без ожидания тика. **И слияние здесь не по смыслу, а по тексту запроса — вопрос оператора «при чём тут автомерж».** Занятость слота
считается так: сессия в `queued/running/revising/stuck` **И** задача **не** в `done/failed/blocked`
(`AccountRepository`, подзапрос ёмкости). То есть слот держится **терминальностью задачи**, а не жизнью
сессии. `recordSuccessfulMerge` ставит `task.setStatus(done)` — значит именно здесь карточка и освобождается.
Потребление клиентом и освобождение ёмкости совпадают в одной точке, и это проверяемо по тексту запроса, а не
по рассуждению. **Нового предела намеренно не введено:** число карточек уже задано ёмкостью аккаунта и
блюдётся `lockNextJulesAccountWithCapacity` — не хватало не ограничения, а возврата. Тик в 60 секунд остаётся страховкой, а не источником выпуска. Вызов обёрнут так же, как соседний цикл советов: слияние — настоящая работа, и её следствие не имеет права ей навредить. *Проверено живым событием 2026-09-06 06:11:12, и оно поправило мою же обработку.* Слияние PR 992 дошло до
точки потребления, возврат карточки **сработал**, и политика его отвергла: «DISPATCH_QUEUED_TASKS … there is
nothing for it to act on right now» — очередь была пуста. Это верный исход возврата, а не сбой; мой первый
перехват объявил его «failed» и тем самым сделал работающий механизм похожим на сломанный. Отказ политики
теперь ловится отдельно и пишется как «карточка вернулась, отправлять нечего». **Отдельный вывод для потока:**
в момент потребления очередь пуста, потому что наполняет её компиляция, а она сама на таймере — тянущий
контур длиной в одно звено.

*Заслон:* `LeanPullReleaseTest` — счётное утверждение по множеству точек потребления, а не поиск одного вызова: закрепляет множество на единице, требует, чтобы возврат стоял **внутри** точки потребления, чтобы он не достигался только из метода по расписанию, и запрещает второй самостоятельный предел. Красный без правки проверен: `dispatchQueuedTasks` встречался в этом классе **ноль раз**.
*Философия:* `FALSIFICATION_HARNESS` (D008) — **сильная**, заслон `AutoMergeLaw20InvariantS4Test`.
Опровержение: добавить место слияния и посмотреть, покраснел ли счётный инвариант.

**`GitHubPullRequestService`** (2108 строк) — транспорт к GitHub: PR, файлы, ветки, слияния.
*Связи:* **вызывают 17 механизмов**; зовёт `CodeChangeClassifier`, `GitHubApiBudgetService`; **не пишет** — у
него нет своего состояния, только чужой репозиторий.
*Ценность:* четыре точки записи отвергают заводские записи на месте.
*Комментарий:* **ядро.** До сегодня разбиение обеспечивалось **уборкой на выходе**, и это надо назвать прямо:
**уборка есть свидетельство нарушения, а не его отсутствия.** К моменту снятия работа на порождение файла уже
потрачена, diff испорчен, окно с заводской записью в PR клиента состоялось. Заслон, удаляющий файл, и канал,
неспособный его пронести, — разные вещи.
*Философия:* `PROHIBITION_AS_CODE` (D006) — **сильная**, заслон
`DeliveryRealityLaw2CarrierChannelTest`. Остаток: классификатор внедряется «необязательно», и отсутствие бина
выключило бы запрет молча. Опровержение: убрать бин и посмотреть, отличается ли поведение точки записи.

**`GitHubApiBudgetService`** — бюджет обращений к GitHub.
*Связи:* вызывают 4; ничего не зовёт и не пишет.
*Ценность:* исчерпание лимита однажды остановило фабрику на часы.
*Комментарий:* **ядро (бюджет).** Формулировка, ради которой он переписан, стоит того, чтобы её помнить:
**цена обхода ограничена остатком работы, а не историей.** Опрашивать терминальные записи вечно — платить за
прошлое.
*Философия:* `RELIABILITY_CHAIN` (D010) — **сильная**. Опровержение: посчитать обращения на терминальные PR.

**`GithubAccessService`** — доступы и приглашения соавторов.
*Связи:* вызывающих нет — идёт от контроллера; зовёт бюджет GitHub.
*Ценность:* без доступа не поедет ничто.
*Комментарий:* **ядро по демаркации**, заслонов не видел.
*Философия:* `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006) — Джозеф Раз, `BARCAN-TAG-10
DEONTIC-PROHIBITION`, принцип **исключающих причин**, anchor *Practical Reason and Norms / The Authority of
Law*. Сильная форма дословно: «до реализации полномочий составлена матрица прав, обязанностей, привилегий и
власти, и **на каждое отношение есть тест разрешённого и запрещённого**». Слабая: «роли перечислены,
**проверки написаны по месту**». Опровержение образца: «**найти отношение, у которого нет теста запрета**».

*Замер 2026-09-06.* Механизм объявляет **11 действий**: `abandonConflict`, `boostPriority`,
`collapseDuplicateTask`, `dismissWishlist`, `nudgeStuckSession`, `resolveOrphanedPr`, `retireStuckWorker`,
`retryAbandonedCloseout`, `reviveFailedTask`, `triggerCodeDefectFalsificationRun`, `triggerFalsificationRun`.
Заслон `GeminiObserverActionServiceTest` (463 строки) упоминает восемь из них. **Три не упоминаются вовсе:**
`boostPriority`, `nudgeStuckSession`, `triggerCodeDefectFalsificationRun`. Контрольная проба встроена в сам
счёт: восемь ненулевых значений в той же колонке доказывают, что ноль означает отсутствие, а не поломку
грепа.

*Уточнение, без которого вывод был бы вдвое резче, чем факт.* Все три бесстестовых действия проходят через
общий `execute(action, OperationalAction.…, project, targetId, reason, …)`, то есть **полномочие у них
опосредовано операционной политикой**, а `boostPriority` вдобавок сам проверяет неверный идентификатор и
чужой проект. Значит отсутствует **тест** запрета, а не запрет. Это ровно слабая форма образца: «проверки
написаны по месту».

*По Разу это и есть суть.* Власть работает **исключающими причинами**: она не перевешивает доводы, а
запрещает их учитывать. Полномочие, у которого не показан запрещённый случай, исключающей причиной не
является — оно остаётся доводом среди прочих, и различить «разрешено» от «не проверено» снаружи нельзя.

*Что делать:* три теста запрета, по одному на действие, на тот случай, который политика обязана отвергнуть.
Не список имён — утверждение о свойстве: действие, поданное вне своего проекта либо при отсутствующей цели,
обязано вернуть отказ.

*Опровержение, назначенное вперёд:* добавить действие двенадцатым и посмотреть, упадёт ли что-нибудь. Не
упало — матрицы по-прежнему нет, есть перечень.
**`CodeChangeClassifier`** — детерминированный, не-ИИ ответ на вопрос «есть ли здесь настоящий продуктовый
код». Список-запрет, а не список-разрешение.
*Связи:* вызывают гейт слияния и транспорт; ничего не зовёт.
*Ценность:* перечень расширений устаревал бы с каждым новым стеком клиента.
*Комментарий:* **ядро; самое аккуратное рассуждение об асимметрии ошибок во всём коде.** Ложное «это не код»
удалило бы настоящую работу; ложное «это код» безвредно. Поэтому запрет, а не разрешение — **сторона ошибки
выбрана по цене ошибки, а не по удобству.** Ровно этого не хватало многим механизмам, разрешавшим себе
оптимистичный исход.
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **сильная**. Опровержение: подать процессный файл нового стека и
посмотреть, признан ли он кодом.

**`GateOrchestrator`**, **`BaseQualityGate`**, **`BackendContractGate`**, **`DesignExcellenceGate`**,
**`VerificationEvidenceGate`**, **`EpistemicLayerInvariantGate`** — гейты качества по стадиям.
*Связи:* оркестратор пишет `TaskGateLogRepository` и `TaskRepository`; отдельные гейты вызываются из
`ClientDeliverableReadinessService` и `JulesDispatchService`. **Прежнее утверждение «`BaseQualityGate` и
`EpistemicLayerInvariantGate` не вызывает никто» замером снято:** `EpistemicLayerInvariantGate` помечен
`@Service` и реализует `GateCheck`, а оркестратор внедряет `List<GateCheck>` — Spring собирает его вместе со
всеми. Вызывающий есть. Верно другое, и оно хуже: путь, на котором он стоит, не достигается — предписание 33.
Без `@Service` остаётся только `BaseQualityGate`.
*Ценность:* стадийная проверка перед признанием работы сделанной.
*Комментарий:* **ядро.** `EpistemicLayerInvariantGate` — единственное место, где куайновская демаркация
**исполняется машиной**: периферии запрещено менять файлы ядра. И его никто не зовёт. `DesignExcellenceGate` —
уже исправленный случай, который стоит помнить: он читал поле, которого никто в продакшене не писал, то есть
был зелен всегда.
*Пустой квантор, уже исправленный, но обязательный к памяти:* `allMatch` на пустом списке истинна, а у
восьми ролей из тринадцати нет ни одной применимой проверки — такая задача записывалась как прошедшая **все**
проверки, не пройдя ни одной. Знаменатель обязан называться и у булевых величин, не только у долей.
*Философия:* `FALSIFICATION_HARNESS` (D008) — **слабая**: два гейта из шести без вызывающих. Опровержение:
нарушить инвариант эпистемического слоя и посмотреть, остановил ли кто-нибудь работу.

**`BranchGarbageCollectorService`** — не более одной живой ветки на задачу.
*Связи:* вызывают трое; зовёт 5; пишет `TaskConflictRepository`, `TaskRepository`.
*Ценность:* грязная или застоявшаяся ветка — работа, которая никогда не доедет.
*Комментарий:* **периферия** по демаркации, но решение суровое: закрыть PR, удалить ветку, поставить задачу
заново. Оправдание верное — переделать от чистого main дешевле, чем чинить мёртвое, — и порог динамический,
по трём сигмам, а не назначенный. Это правильная форма.
*Философия:* `DECISION_EXPECTED_LOSS` (D005) — **сильная**. Опровержение: найти порог, заданный константой.

**`PrReviewPipelineService`** — конвейер ревью PR.
*Связи:* зовёт оценку риска; **вызывающих трое, замер 2026-09-06**: `GithubWebhookController:74`, `JulesDispatchService:4011`, `AutoMergeService`. Прежняя запись «вызывающих в коде нет» — ложь.
*Ценность:* последовательность ревью PR: без неё вердикт о коде выносился бы вне порядка и без общего состояния.
*Комментарий:* **ядро по демаркации**, заслонов не видел. **Осторожно с утверждениями «вызывающих нет» во всём этом файле.** Два проверенных поимённо оказались ложными: этот и `EpistemicLayerInvariantGate` (он `@Service`, а оркестратор внедряет `List<GateCheck>`). Оба служили основанием для формы образца «слабая», то есть **ложная посылка порождала ложный статус**. Остальные подобные утверждения **не проверены**: беглый греп по именам рядом с такой строкой подхватывает имена из `Связи` и предметом утверждения не является. Прежде чем опираться на любое «вызывающих нет» — перепроверить поимённо.
*Философия:* `ELIZABET_ENSKOM_02_PLANNING_CONSISTENCY` (D004) — Элизабет Энском, `BARCAN-TAG-12
SOCIAL-CONTRACT`, интенциональное действие **«под описанием»**, anchor *Intention*. Сильная форма дословно:
«планы задач, PR, ветки и притязания складываются в **одно** состояние плана; дашборд, GitHub и рантайм
согласны о следующем действии». Слабая: «**каждый источник согласован сам с собой**». Опровержение образца:
«спросить у трёх источников следующее действие и сравнить ответы» — оно и стало планом замера.

*Опыт 2026-09-06, 11:28:32 UTC, три источника в один момент.* Свод: `openReviews = 2`, `reviewTasks = 2`,
следующее действие — «Produce a PR or terminal failure evidence». Сводка доставки: `prLinks = 0`. GitHub, как
его видит сама фабрика (`[BRANCH-GC] Found N open PR(s)`): **3**. Три ответа на один вопрос: 2, 0, 3. И
названное действие — «произвести PR» — противоречит тому, что три PR уже открыты.

*Контроль, чтобы расхождение не оказалось временем.* Между замерами прошла только отправка задачи: ни один
PR не открывался, не закрывался и не сливался. Свод опрошен дважды подряд — оба раза 2. Расхождение
устойчиво.

*Две версии, и предпочитаемая названа.* **Первая:** источники считают разные множества — BRANCH-GC берёт все
открытые PR репозитория, включая **носители**, а свод считает только продуктовые задачи с ревью. Замер её
поддерживает: за полчаса фабрика слила два **record PR** (1016 и 1018, «reason=wishlist compiler plan
parsed» и «reason=PR review fallback verdict»), то есть носители в репозитории есть и их считает только
GitHub. **Вторая:** один из источников устарел. Различающее наблюдение: перечислить три открытых PR поимённо
и проверить, есть ли среди них record PR. Не выполнено — **версия поддержана, но не установлена**.

*Форма: **слабая**, и подтверждена именно в том виде, как её описывает образец.* Дефект не в том, что число
неверно, а в том, что **каждый источник согласован сам с собой** и ни один не говорит, какое множество он
считает. По Энском: действие существует **под описанием**, и здесь одно и то же положение дел описано тремя
способами без судьи между ними. Оператор указывал мне на Энском отдельно — образец назван его именем не для
украшения: «следующее действие» тут не одно, их три.

*Что делать:* каждое число о PR обязано нести имя множества — «открытых продуктовых ревью», «всех открытых
PR репозитория», — и свод обязан называть источник, из которого взял. Пока имени нет, согласие трёх
источников проверить нельзя даже в принципе.
рантайма следующее действие по одному PR и сравнить ответы.

---

# V. Свидетельство доставки

**`ClientDeliverableReadinessService`** (1626 строк) — меряет сданный объём по настоящей иерархии: корневая
заявка → эпики → плановые пункты → задачи → свидетельство слияния.
*Связи:* **вызывают 11 механизмов**; зовёт 9; пишет `FeatureRepository`.
*Ценность:* эпик завершён, только когда у каждого пункта есть **своё** слияние. Слияние от посторонней задачи
в том же эпике пункт не закрывает.
*Комментарий:* **ядро, и главный носитель различия между «сделано» и «доставлено».** Сегодня именно его свод
спас: уборщик мета-задач считал продукт готовым по статусам задач, а этот механизм в ту же секунду говорил
«20 из 22». **Механизм, у которого спросили, оказался прав; ошибся тот, кто не спросил.**
*Философия:* `SUBSTITUTION_ORACLE` (D009) — **сильная**. Опровержение: закрыть пункт слиянием посторонней
задачи того же эпика.

**`DeliveryRealityProducerService`** — превращает обнаружение расхождения в свидетельство.
*Связи:* вызывающих нет — идёт по расписанию; зовёт 8; пишет 4 хранилища, включая журнал дефектов.
*Ценность:* обнаружение существовало с июля и упиралось в поле дашборда.
*Комментарий:* **ядро.** Формула, ради которой он написан, — **сигнал без читателя не есть наблюдение** — и
она приложима шире: это рабочее определение муды для всякого измеряющего механизма. Но за ним же числится
**накопленное перепроизводство**: 196 заявок одного источника, 155 отброшено при 15 клиентских.
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — **сильная** по читателю, **не держится** по области находки.
Опровержение: провалить доставку заказанного требования дважды и посчитать заявки.

**`ProductLaunchabilityService`** — есть ли у проекта задокументированный способ запуститься.
*Связи:* вызывает общий тик; зовёт 4; пишет `ProjectRepository`, `WishlistRepository`.
*Ценность:* самое дешёвое первое действие; всё остальное бессмысленно, если продукт не стартует.
*Комментарий:* **ядро** — подчинение ТОС гейтит на запускаемости. Проверять сначала самое дешёвое и самое
разрушительное при отказе — инженерная экономия внимания.
*Философия:* `KNOWLEDGE_FIRST_GATE` (D006) — **сильная**. Опровержение: объявить проект запускаемым без
файла запуска.

---

# VI. Суждение

**Общий суд по разделу.** Философски самая зрелая часть фабрики и самая **бездействующая**. Решётка честна до
редкости: трёхзначность вместо двузначности, объявление предмета до суждения, обязанность воздержаться при
невозможности обосновать. И при этом связи показывают: **у всех пяти слоёв ноль вызывающих** — их собирает
Spring, единственный потребитель `VerdictReconciliation` — `VerdictGate`, а его зовёт **дашборд**. То есть
самый строгий судья системы слышен только тогда, когда кто-то откроет экран.

**`VerdictLayer`**, **`Verdict`**, **`Judgement`**, **`VerdictReconciliation`**, **`VerdictGate`** — решётка:
слой объявляет **заранее** конечный набор утверждений, о которых судит, и лишь затем судит.
*Связи:* пять реализаций собираются Spring; `VerdictReconciliation` → `VerdictGate` → дашборд.
*Ценность:* область суждения не растёт по ходу — условие Баркан, по которому фабрика названа.
*Комментарий:* **ядро по назначению, периферия по действию.** Трёхзначность заложена в тип, а не введена
руками, как её пришлось вводить в двух других местах за смену. Это разница между продуманным и написанным по
случаю.
*Философия:* `TRUTH_STATUS_TABLE` (D012) — **сильная** по устройству. Опровержение: найти вызывающего,
округлившего «не установлено» до «в порядке».

**`AcceptanceVerdictLayer`** — видел ли **заплативший** купленное в работе?
*Связи:* вызывающих нет; зовёт корпус рынка и вердикт.
*Ценность:* каждый ценностный путь корпуса описывает дорогу конечного пользователя, и ни один — дорогу
покупателя.
*Комментарий:* **ядро.** Самый глубокий вопрос системы: фабрика доходила до «сдано» по числу слияний, то есть
**подменяла утверждение о показанном утверждением о построенном**. Та же подстановка, что «готово» вместо
«доставлено», но уровнем выше и дороже: там теряется задача, здесь сделка.
*Философия:* `SUBSTITUTION_ORACLE` (D009) — **сильная** по предмету, **не мерена** по читателю.
Опровержение: назвать решение, изменённое его вердиктом.

**`RuntimeVerdictLayer`** — работает ли поставленный продукт на самом деле?
*Связи:* вызывающих нет; зовёт транспорт GitHub и вердикт.
*Ценность:* фабрика однажды сама нашла два дефекта сборки, оба починила и **не пересмотрела вердикт** — весь
день философия подчинялась замеру, снятому до починок.
*Комментарий:* **ядро.** **Хранимое «упало» выдавалось за текущий факт** — зеркало главного дефекта системы:
там объявленный успех принимался без проверки, здесь объявленная неудача.
*Философия:* `RELIABILITY_CHAIN` (D010) — **сильная** после починки. Опровержение: назвать возраст вердикта
о запуске.

**`DoctrineVerdictLayer`** — что говорят тринадцать ролевых доктрин.
*Связи:* вызывающих нет; зовёт метрики и вердикт.
*Ценность:* тринадцать независимых мнений о состоянии проекта.
*Комментарий:* **ядро по назначению, живой пример муды по действию.** Слой писал «отказано», две роли
возражали прямо, семь мягко — а конвейер отправлял задачи и рапортовал 82% готовности. **Слой, созданный
удерживать приёмку, удерживал её в пустоту.** Это и есть определение муды для судящего механизма: не «неверно
судит», а «судит, и никто не слышит».
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — **слабая**. Опровержение: привести слой к отказу и посмотреть,
изменилось ли хоть одно действие.

**`InfrastructureVerdictLayer`** — в состоянии ли **сама фабрика** давать осмысленные ответы о продукте?
*Связи:* вызывающих нет; зовёт самодиагностику фабрики.
*Ценность:* запускатель лежал, и никто не сказал; философия подчинена запускаемости, значит она либо
пропускалась, либо шла вслепую.
*Комментарий:* **ядро.** Философски необходимый слой, которого почти нигде не бывает: прежде чем судить о
предмете, проверь исправность инструмента. Отдельно записано, что именно у запускателя **не было блока
healthcheck вовсе** — то есть отсутствие проверки и было причиной.
*Философия:* `SELF_MODEL_SANITY` (D013) — **сильная**. Опровержение: погасить зависимость и посмотреть,
изменился ли вердикт.

**`SixSigmaVerdictLayer`** — качество процесса; слой, обязанный **воздержаться**, а не назвать число.
*Связи:* вызывающих нет; зовёт только вердикт.
*Ценность:* необоснованное число неотличимо от обоснованного и портит всё, что на него обопрётся.
*Комментарий:* **периферия; и самый честный механизм системы.** Он мог бы выдать число и был бы «полезнее».
*Философия:* `TRUTH_STATUS_TABLE` (D012) — **сильная**, третий исход исполнен буквально.

**`JudgmentAgentClient`** — одно ограниченное суждение по одному опровержению.
*Связи:* вызывают трое судящих; зовёт отбор свидетельства.
*Ценность:* суждение на подписке, а не на потокенном ключе.
*Комментарий:* **периферия.** Урок о том, как обещание в плане подменяет замер: первая версия звала метрический
API, а план обещал «плоскую цену». **Замена одного счётчика другим не была улучшением**, и это выяснилось лишь
настоящим вызовом.
*Философия:* `DECISION_EXPECTED_LOSS` (D005) — **сильная**. Опровержение: назвать цену вызова и её источник.

**`FactoryJudgmentService`** — суждение, движимое **опровержением**, а не часами.
*Связи:* вызывающих нет — идёт по расписанию; зовёт клиента суждения и кайдзен.
*Ценность:* подтверждений сорок в день, они бесплатны и ничего не сообщают; опровержений около трёх, и каждое
информативно.
*Комментарий:* **периферия; лучшее приложение Поппера во всём коде.** Модель зовут на опровержение, а не по
расписанию — прямая экономия дорогого ресурса, выведенная из философии, а не из бюджета.
*Философия:* `FALSIFICATION_HARNESS` (D008) — **сильная**. Опровержение: найти вызов модели, сделанный по
таймеру, а не по опровержению.

**`DeliveredWorkJudgmentService`** — удовлетворяет ли слитый diff тому утверждению, на котором задача обещала
проверяться?
*Связи:* вызывает общий тик; зовёт 8; пишет `TaskRepository`, `WishlistRepository`.
*Ценность:* двадцать шесть задач, тринадцать слитых PR, **ноль проверенных доставок** — самый громкий замер в
истории системы.
*Комментарий:* **ядро.** Диагноз точен: **гейты были не слабы, они были недостижимы.** Разница существенная:
слабый гейт чинят ужесточением, недостижимый — только тем, что ставят на путь, по которому работа идёт.
*Философия:* `CONSTRUCTIVE_PROOF_OBJECT` (D007) — **сильная**. Опровержение: слить diff, не отвечающий
критерию задачи, и посмотреть, признана ли доставка.

**`CriteriaEvidenceSelector`** — закон 17: судить по механически обрезанному свидетельству нельзя.
*Связи:* вызывают двое судящих; ничего не зовёт.
*Ценность:* обрезание по длине есть выбор свидетельства по положению, а не по отношению к делу.
*Комментарий:* **ядро (суждение).** Куски diff режутся по границам файлов, порядок сохраняется, **пропущенное
называется** — последнее важнее всего: названное пропущенное остаётся проверяемым.
*Философия:* `CONVERSATION_MAXIM` (D007) — **сильная**. Опровержение: найти суждение, где пропущенное не
названо.

**`LeverPromotionService`**, **`LeverStage`**, **`LeverAgreement`** — лестница продвижения решателя.
*Связи:* вызывают 5 механизмов, включая здоровье аккаунта и хребет потока.
*Ценность:* новый решатель получает право действовать, **лишь пережив накопленное свидетельство, а не деплой
и не таймер**.
*Комментарий:* **периферия по предмету, ядро по последствиям** — решатель на жёстком гейте держит поток.
`LeverAgreement` держит четырёхзначность Белнапа: «нет свидетельства» — не «ложь». Тот же отказ от
оптимистичной склейки, что и всюду, доведённый до четырёх значений.
*Философия:* `BELIEF_UPDATE_LEDGER` (D007) — **сильная**. Опровержение: найти продвижение, случившееся по
времени или деплою.

---

# VII. Измерение: Lean, ТОС, Шесть сигм

**Общий суд по разделу.** Здесь живёт самая частая ошибка измеряющих систем — **считать не то, что
называешь**, — и раздел от неё в основном защищён явно: почти в каждом механизме оговорено, что является
подгруппой и почему нельзя взять соседнюю.

**`ProcessControlService`** — u-карты; подгруппа эпик, последовательность **только** по порядку завершения
внутри одного проекта.
*Связи:* вызывают двое; зовёт 4; ничего не пишет.
*Ценность:* эпики разных проектов — не однородные единицы; складывать их в одну карту значит мерить смесь.
*Комментарий:* **периферия.** Пределы считаются один раз по первым эпикам и держатся неизменными: **карта,
подстраивающаяся под дрейф, перестаёт дрейф видеть.** Защита от самого коварного самообмана измерения.
*Философия:* `LEVEL_OF_ABSTRACTION_LOCK` (D010) — **сильная**. Опровержение: найти пересчёт пределов после
первых эпиков.


*Живое, 9 сентября 2026, Codex: 10-такт 5/10 — quality gate/process-control cluster, без правки кода.* Пятый кластер десятиактного прохода заполнен как единый quality-measurement mechanism. `ProcessControlService` stores u-chart snapshots, `QualityGateController` exposes a defect-rate API, `QualityMetricsController` exposes quality aggregates/details, and `SixSigmaAuditService` owns reusable Six Sigma/CTQ counts. These cannot be treated as separate query cleanups because the same quality-gate reports, PR-review lineage and conflict evidence feed dashboard, audit, Kaizen and u-chart decisions.

*Идеальная форма кластера:* quality has one measurement language at each level. Quality-gate check rows become aggregate counts and CTQ breakdown through one owner; process-control u-charts consume a project-scoped evidence packet ordered by completed epics; REST endpoints either return aggregates or explicitly bounded/paged detail rows. A reader can name whether it is seeing factory-wide, project-wide, feature/epic, stream, or item-level truth, and which owner computed it.

*Граница и взаимодействия:* upstream owners are `TaskRepository`/qualityGateReport, `PrReviewRepository`, `TaskConflictRepository`, `JulesSessionRepository`, `OnboardingAuditFindingRepository`, `FeatureRepository`, `ReviewConcernRepository`, `DefectJournalRepository`, `ProcessControlSnapshotRepository`, and `SixSigmaAuditService`. Downstream readers include `SystemStatusService`, `KaizenService`, `ProcessControlService`, `ProjectTreeService`, `SystemAuditController`, `QualityMetricsController`, `QualityGateController`, and future operator/UI consumers. The cluster measures and records quality; it must not decide delivery, merge, task lifecycle or account capacity.

*Инварианты:* (1) factory-wide, project-wide and feature/epic scopes are never mixed without an explicit transform; (2) aggregates are acquired as counts/projections, not full rows; (3) detail lists have a declared bound or diagnostic status; (4) u-chart baseline is locked to the first completed epics in one project and never recomputed from later drift; (5) ProcessControl and SixSigma share the same defect/opportunity definitions for quality gate and PR conflict streams; (6) review→session→task lineage remains intact for PR-conflict counts; (7) Kaizen proposals from u-chart signals cite the stream and underlying pattern evidence.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `ProcessControlService.java:160` in `recomputeForProject(projectId)` | `PrReviewRepository` with review→session→task lineage | scheduled 2h recompute for one active project; per-feature `computePrConflictCounts` and `reviewConcernCounts` | `u-chart evidence packet` | Replacement-needed but not by per-epic queries. Current pre-read is better than N+1 but still factory-wide. Refutation: recomputing one project materializes PR reviews from other projects. Closure: one project-scoped review evidence packet covers completed epic ids and preserves quality/conflict/reviewConcern counts. |
| `ProcessControlService.java:161` in `recomputeForProject(projectId)` | `TaskConflictRepository` with task/feature/project identity | same recompute, PR conflict stream | `u-chart evidence packet` | Replacement-needed. The answer is conflicts for task ids under this project's completed epics, not all conflicts. Refutation: u-chart for one project reads conflicts whose task belongs to another project or no live task. Closure: task/feature scoped conflict evidence preserves `computePrConflictCounts` outputs and lazy-reference safety. |
| `QualityGateController.java:25` in `GET /api/quality-gate/defect-rate` | `TaskRepository` quality-gate reports; conceptual owner `SixSigmaAuditService.computeQualityGateCounts` | quality-gate API | `duplicate quality aggregate` | Replacement-needed as ownership cleanup. It recomputes global DPMO directly from all tasks, duplicating the Six Sigma quality-gate aggregate. Refutation: `/api/quality-gate/defect-rate` and `SixSigmaAuditService.computeQualityGateCounts(null,null)` disagree on attempts/opportunities/defects. Closure: endpoint delegates to the shared owner or uses the same report-only acquisition and returns the same denominator contract. |
| Already-recorded `QualityMetricsController.java:40,41,47,76,133,135,154` | PR reviews, conflicts, sessions, projects, tasks, onboarding findings | `/api/quality/conflict-dpmo`, `/api/quality/defect-summary` | `aggregate-vs-detail API boundary` | Existing line-level registry remains authoritative. Cluster closure depends on `AGY_ASKS.md`: `defect-summary.items` must be declared diagnostic all-rows, paginated/top-N/project-scoped, or obsolete. Refutation: totals can be acquired as aggregates while item lists remain unbounded and undocumented. |
| Already-recorded `SixSigmaAuditService.java:105,109,242,355,394,425` | projects, onboarding findings, quality reports, PR/conflict lineage | audit endpoints, SystemStatus, Kaizen, ProcessControl, ProjectTree | `quality owner and layer transform` | Existing line-level registry remains authoritative. Cluster closure depends on active-project fallback answer and lineage-preserving projections. Refutation: ProcessControl/QualityGate/QualityMetrics compute the same quality fact with different scope or denominator. |

*Сильная форма:* one owner computes each quality aggregate; endpoints expose aggregate truth separately from bounded detail truth; u-chart recompute uses one scoped evidence packet for the project's completed epic sequence; Six Sigma and ProcessControl agree on defect/opportunity counts; CTQ/Pareto and Kaizen targeting cite the same source rows and abstraction level.

*Слабая форма:* every component can produce a plausible number, but some do so by loading whole tables and some by recomputing the same denominator locally. That lets `defect-rate`, Six Sigma, quality metrics and u-chart signals drift apart even while each individual method returns a coherent-looking map.

*Опровержение:* create a fixture with two projects, completed epics in different orders, qualityGateReport checks, merged PR reviews, task conflicts, onboarding findings and review concerns. If ProcessControl sees another project's evidence, if `/api/quality-gate/defect-rate` disagrees with SixSigma's quality-gate counts, or if `defect-summary.items` has no bound while totals are aggregate, the mechanism is not ideal.

*Критерий закрытия:* this record is complete when the three not-yet-clustered full-table call sites above are classified and tied to the existing QualityMetrics/SixSigma registries. The implementation becomes ideal only when ProcessControl uses scoped quality evidence packets, QualityGate delegates to or matches the SixSigma owner, detail-list contracts are answered, active-project fallback is answered, and fixture tests prove no denominator/scope drift between API, audit, dashboard, Kaizen and u-chart outputs.

*Кандидат на будущую реализацию Codex:* after explicit code approval, `QualityGateController` is a candidate only if delegated to the shared Six Sigma owner with fixture parity. `ProcessControlService` should not be touched as a tiny read replacement until the project/feature review-conflict evidence packet is specified; `QualityMetricsController` detail lists and `SixSigmaAuditService.getActiveProjectId` remain blocked by existing questions.

*Текущий статус:* not ideal. The connected quality/process-control record is now explicit; implementation still contains the three unclustered full-table acquisitions plus the already-recorded QualityMetrics and SixSigma acquisition defects/questions.

*Свидетельства такта:* `grep -n "\.findAll()"` across `ProcessControlService`, `QualityGateController`, `QualityMetricsController`, `SixSigmaAuditService`; focused `nl -ba` contexts around `ProcessControlService` lines 160-161 and `QualityGateController` line 25; existing registries at `docs/FACTORY_MECHANISMS.md` QualityMetrics/SixSigma sections; `docs/reports/AGY_ASKS.md` questions for `defect-summary.items` and `SixSigmaAuditService.getActiveProjectId`; repository grep for quality report/count/project/feature methods; caller grep for `ProcessControlService`, `QualityGateController`, `QualityMetricsController`, `SixSigmaAuditService`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `ProcessControlService`, `QualityGateController`, `QualityMetricsController` or `SixSigmaAuditService` как отдельные query-cleanups: сначала закрепи один quality-measurement contract — aggregate truth separately from bounded detail truth, u-chart evidence packet scoped to one project/epic sequence, and one owner for quality-gate DPMO — then answer existing `AGY_ASKS` on `defect-summary.items` and active-project fallback before implementation. Следующий такт 6/10 — Kaizen/lean/lever/market cluster: `KaizenService`, `FlowMetricsService`, `LeverPromotionService`, `MarketResearchService`, and improvement-signal lifecycle. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для u-chart levels additionally hold `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`.*

**`ConstraintIdentificationService`** — находит текущий барабан среди ресурсов фабрики.
*Связи:* вызывает кайдзен; зовёт 3.
*Ценность:* барабан переезжает по ходу проекта.
*Комментарий:* **периферия по предмету, ядро по последствиям.** Барабан **определяется, а не назначается**;
назначенный барабан — категориальная ошибка о самой системе: убеждение о ней принято за её устройство.
*Философия:* `CAUSAL_PROCESS_TRACE` (D013) — **сильная**. Опровержение: найти зашитый список ресурсов-кандидатов.


*Живое, 9 сентября 2026, Codex: 10-такт 2/10 — constraint/coherence cluster, без правки кода.* Второй кластер десятиактного прохода заполнен как один поток constraint truth: `ConstraintIdentificationService` names the drum, `BottleneckDetectionService` exposes operator-facing bottlenecks, and `EvidenceCoherenceService` calibrates whether evidence sources deserve trust. These mechanisms must not be optimized separately, because all three can change what the factory believes is constraining throughput.

*Идеальная форма кластера:* the factory has one explainable constraint story. Project drum pressure is acquired by project-scoped task/session evidence and dispatcher-owned account capacity; dashboard bottlenecks use the same account-capacity semantics instead of a private summary; coherence reliability uses bounded counts/projections over the declared evidence window/source type. A caller can ask why a bottleneck or low-confidence source was reported and get source, timestamp/window, freshness rule and validation path.

*Граница и взаимодействия:* upstream owners are `TaskRepository`, `AccountRepository`, `JulesSessionRepository`, `GitHubApiBudgetService`, `EvidenceNodeRepository`, `CoherenceRunRepository`, `CoherenceRunNodeResultRepository`, and `KaizenProposalRepository`. Downstream readers are `KaizenService` for constraint targeting, `BottleneckAwarePriorityService`, dashboard/API context through `BottleneckDetectionService.detect(projectId)`, project graph endpoints through `EvidenceCoherenceService.graphSnapshot`, and the falsification/quality/operational mechanisms that write evidence nodes. This cluster reports constraints; it must not become the owner of dispatch locking, task status or Kaizen proposal lifecycle.

*Инварианты:* (1) drum candidates are comparable pressures with named denominators; (2) project drum reads only this project's tasks/sessions plus explicitly factory-wide capacity/budget signals; (3) buffer capacity is based on completed tasks from the same project and its own cycle-time distribution; (4) bottleneck account depletion and drum session-slot pressure speak the same account-capacity language as dispatch; (5) coherence cycles use the reconciliation window and source type, not accumulated history without bound; (6) source reliability falls back from specific data to pooled evidence to prior without fabricating confidence; (7) dashboard bottlenecks and project context cannot contradict the dispatcher on whether capacity exists.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `ConstraintIdentificationService.java:68` in `identifyDrum(projectId)` | `TaskRepository` / tasks | `KaizenService`, TOC reads, project constraint diagnosis | `project drum task counts` | Replacement-needed. The answer needs queued/review counts for one project, not every task entity. Existing `findByProjectIdOrderByCreatedAtDesc` can preserve behavior; stronger form is repository counts/projection by project/status. Refutation: project A drum materializes project B tasks. Closure: queued/review counts match current fixture while SQL is project-scoped. |
| `ConstraintIdentificationService.java:77` in `identifyDrum` | `AccountRepository` / accounts | same drum calculation; shared with bottleneck capacity language | `factory capacity denominator` | Replacement-needed at mechanism level. It currently sums enabled accounts' `maxConcurrentSessions` or default, while dispatcher capacity uses enabled/api key/status/project/capability/daily/concurrent predicates. Refutation: drum says capacity exists or not using rows the dispatcher would reject/admit differently. Closure: capacity denominator is either explicitly factory-wide reference data with row-count bound, or a dispatcher-aligned projection; the choice is named. |
| `ConstraintIdentificationService.java:132` in `recommendedBufferCapacity(projectId, zFactor)` | `TaskRepository` / done tasks | buffer recommendation for one project | `project cycle-time sample` | Replacement-needed. The method needs completed tasks with timestamps for one project, not all tasks. Refutation: buffer sizing observes other projects or status rows before filtering. Closure: acquisition is project+done+timestamps scoped and preserves sample size, mean, std dev, throughput and ceil formula on fixture. |
| `BottleneckDetectionService.java:53` in `detect(projectId)` | `AccountRepository` / accounts | dashboard/project context bottleneck detection per queued tag | `operator bottleneck capacity summary` | Replacement-needed. It repeats full account read inside each queued-tag row and counts daily/api/working accounts separately from `existsJulesAccountWithCapacity`. Refutation: two tags cause the same account table to be loaded twice, or bottleneck reason contradicts dispatcher-owned eligibility. Closure: one bounded account-capacity summary is acquired once per detect call and uses the same statuses/capacity semantics as the capacity predicate. |
| `EvidenceCoherenceService.java:518` in `sourceReliability(KAIZEN_PROPOSAL)` | `KaizenProposalRepository` / kaizen proposals | called during `runCoherenceCycle`, scheduled every 2h and manual graph work | `source reliability count` | Replacement-needed. It needs count of `STANDARDIZED` proposals, not proposal entities. Refutation: reliability calibration reads all proposals when status count would answer. Closure: repository count/projection gives same standardized count for the fixture. |
| `EvidenceCoherenceService.java:520` in `sourceReliability(KAIZEN_PROPOSAL)` | `KaizenProposalRepository` / kaizen proposals | same reliability branch | `source reliability count` | Replacement-needed. Same as line 518 for `REVERTED`; the two statuses form one denominator. Refutation: standardized+reverted denominator changes after query rewrite. Closure: both counts are acquired in one bounded status query or aggregate and preserve fallback to prior when below `minReliabilitySamples`. |
| `EvidenceCoherenceService.java:529` in `sourceReliability(sourceType)` | `EvidenceNodeRepository` + `CoherenceRunNodeResultRepository` | reliability for non-Kaizen source types during coherence confidence calculation | `evidence-source reliability history` | Replacement-needed but not a count-only rewrite. Source type is derived from five exclusive source-id columns, and acceptance comes from result history. Refutation: reliability for one source type scans all evidence nodes or performs N history queries without a source/window bound. Closure: source-type acquisition is expressed by explicit predicates/projection over the exclusive source columns and preserves accepted/total semantics under `minReliabilitySamples`. |

*Сильная форма:* all seven acquisitions are either bounded by project/status/source type/window or explicitly documented as small reference data; the same account-capacity predicate family explains dispatch, drum and dashboard bottlenecks; coherence reliability names which data tier produced the value and when it fell back to 0.5.

*Слабая форма:* each service calculates a locally plausible value after global reads. The values can be numerically right on today's small database but epistemically weak: unrelated tasks influence acquisition cost, account capacity has multiple languages, and source reliability silently depends on accumulated history and derived `sourceType` filtering in memory.

*Опровержение:* build a fixture with two projects, distinct task queues, distinct done cycle-time samples, multiple account statuses/capacities, two queued role tags, and evidence nodes from at least two source types. If drum, bottleneck or coherence reliability needs rows outside the declared project/source/window, or if dashboard capacity disagrees with dispatcher eligibility, the mechanism is not ideal.

*Критерий закрытия:* this record is complete when the seven full-table call sites above name owner, boundary and closure. The mechanism becomes ideal only when tests/probes prove: project drum and buffer are project-scoped; account capacity semantics are shared with dispatch; bottleneck detection reads account summary once per call; coherence reliability uses bounded status/source projections and preserves specific-data → pooled-data → prior fallback.

*Кандидат на будущую реализацию Codex:* low-risk later code work exists only after test fixtures are written. Task reads can first reuse project-scoped repository methods; Kaizen proposal counts need count methods; bottleneck/account capacity should be changed as one capacity-summary mechanism; evidence source reliability needs an explicit source-type projection and should not be attempted as a local count-only patch.

*Текущий статус:* not ideal. The connected record is now explicit, but implementation still has seven full-table acquisitions in this cluster and one adjacent N+1/project-lineage issue in `identifyDrum` through active session task lookup.

*Свидетельства такта:* `nl -ba src/main/java/com/eneik/production/services/toc/ConstraintIdentificationService.java | sed -n '1,190p'`; `nl -ba src/main/java/com/eneik/production/services/coherence/EvidenceCoherenceService.java | sed -n '60,245p'`; `sed -n '485,545p'`; `nl -ba src/main/java/com/eneik/production/services/dashboard/BottleneckDetectionService.java | sed -n '1,140p'`; repository reads for `TaskRepository`, `AccountRepository`, `EvidenceNodeRepository`, `KaizenProposalRepository`, `CoherenceRunNodeResultRepository`; caller grep for the three services; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `sed -n '1,35p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `ConstraintIdentificationService`, `EvidenceCoherenceService` или `BottleneckDetectionService` как отдельные query-cleanups: сначала сохрани общий закон constraint truth — один project-scoped drum, one coherence window, one dispatcher-owned account-capacity semantics — and prove by fixture that bottleneck, buffer and coherence results do not change except for acquisition cost. Следующий такт 3/10 — project flow/orchestration cluster: `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, stranded finalizing sweep, branch garbage collection and lifecycle/event invariants. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для самой ТОС-семантики дополнительно держать `CAUSAL_PROCESS_TRACE`.*

**`LaunchabilityConstraintService`** — единственное место, где определяется ограничение запускаемости.
*Связи:* вызывают двое; пишет `WishlistRepository`.
*Ценность:* прежде оно жило внутри философского цикла за пятью воротами, на кроне раз в двое суток.
*Комментарий:* **ядро.** Здесь была **помеха в чистом виде**: то, чему обязана подчиняться вся архитектура,
определялось побочным эффектом того, что само обязано подчиняться. Инверсия подчинения — редкий род дефекта:
не «неверно посчитано», а «посчитано не тем и не тогда».
*Философия:* `BOUNDARY_TOPOLOGY` (D006) — **сильная** после выноса. Опровержение: найти второе место, где
ограничение определяется.

**`TocSubordinationLever`** — третий шаг ТОС, «подчинить», пока в тени.
*Связи:* вызывает общий тик; зовёт лестницу продвижения; пишет `LeverObservationRepository`.
*Ценность:* ограничение — не пункт с высоким приоритетом, а то, чем ограничена пропускная способность целого.
*Комментарий:* **ядро по назначению, бездействует по факту.** Диагноз в его собственном комментарии точнее
моего: фабрика находит ограничение и **кладёт его в общую очередь ждать**. Положить ограничение в очередь есть
категориальная ошибка — **предел системы принят за элемент системы**. Механизм это знает и всё ещё в тени.
*Философия:* `PRINCIPLED_INTEGRITY` (D012) — **слабая**. Опровержение: назвать случай, где неограничение
уступило ограничению.

**`FlowMetricsService`** — время цикла, время выполнения, WIP, закон Литтла как живая сверка.
*Связи:* **вызывающих нет ни одного**; зовёт три хранилища.
*Ценность:* если `WIP ≈ Throughput × CycleTime` не сходится, **врёт один из трёх замеров**, и это узнаётся
арифметикой, без единого внешнего вызова.
*Комментарий:* **периферия; и мёртв.** Подключал только тот коммит, что его создал; сверка не выполнялась
**ни разу**. Самый дешёвый честный механизм раздела не имеет читателя.
*Философия:* `SUPERVENIENCE_WATCH` (D013) — **слабая**: механизм исправен и не вызван. Опровержение: подать
три несогласованные величины и посмотреть, промолчит ли фабрика.

**`TaskWaitTimeService`** — время ожидания как потеря.
*Связи:* вызывающих нет; зовёт `ProjectFlowService` и хранилище задач.
*Ценность:* черта, проведённая оператором: время в живой сессии не потеря, сколько бы ни длилось; время в
очереди **до** этого — настоящая потеря.
*Комментарий:* **периферия.** Сложить их вместе значило бы объявить работу простоем — отказ от категориальной
ошибки прямо в определении метрики.
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **сильная** по определению, **не мерена** по читателю.

**`SixSigmaAuditService`** — DPMO, выход с первого раза, уровень сигмы.
*Связи:* вызывают 4; зовёт 5.
*Ценность:* сводная мера качества по доставке, гейтам и рантайму.
*Комментарий:* **периферия.** Риск назван: его классификация однажды уже чинилась — `isDefectWork`
сопоставлял свободный текст подстрокой. **Подстрочное сопоставление свободного текста есть заготовка
категориальной ошибки**: совпадение слов принимается за совпадение предмета.
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **слабая**. Опровержение: подать текст, где нужное слово стоит в
другом смысле.

**`EmsMetricsService`**, **`SystemStatusService`**, **`CommandDashboardService`**,
**`ProjectOperationalContextService`**, **`ClientDeliveryService`** — своды для дашборда и оператора.
*Связи:* `SystemStatusService` вызывают трое, зовёт 6; `CommandDashboardService` — единственный потребитель
`VerdictGate`.
*Ценность:* окно оператора в состояние фабрики.
*Комментарий:* **периферия.** Общий риск назван прямо: **свод не есть свидетельство.** `systemStatus: ok`
показывался, пока контур стоял намертво. Дашборд обязан быть проекцией уже вычисленного; там, где он считает
своё, появляется второй источник истины — помеха по определению.
*Размер:* `EmsMetricsService` — 849 строк в одном классе-считалке, столько же, за сколько разделены два
ядерных класса. Предписания 3–4 распространяются и сюда, срочность ниже.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, **private-language argument***. Сильная форма дословно: «утверждение о работе системы
опирается на логи, метрики, проверки здоровья или состояние свода, **и ссылка приведена**». Слабая:
«утверждение опирается на **собственный рассказ**». Опровержение образца: «потребовать команду, которой снят
замер».

*Замер 2026-09-06 по `ProjectOperationalContextService` (736 строк). Первое подозрение опровергнуто, второе
подтверждено.*

**Опровергнуто:** я предположил, что свод слеп к `enabled`. Нет — он фильтрует по `isEnabled` в строках 441,
485, 491, 496, отдаёт `enabled` по каждому аккаунту (466) и число включённых (511). Подозрение снято
следующей же командой.

**Подтверждено, и это второй источник истины:** общая ёмкость считается формулой
`sharedSlotsTotal = enabledAccounts.size() * maxConcurrentJulesSessionsPerAccount` (строка 499), то есть
**число включённых умножается на глобальный предел** `jules.max-concurrent-sessions-per-account:3`. Личный
предел аккаунта при этом существует и различается: живой замер — `eneikdru` 15, остальные шесть по 3, **сумма
личных пределов 33**, а формула свода даёт **7 × 3 = 21**. Свод занижает ёмкость на треть и делает это
молча.

Диспетчер считает иначе и он владелец: `lockNextJulesAccountWithCapacity` требует `enabled = true`,
непустой `api_key`, статус не из четырёх блокирующих, привязку проекта, способность по тегу, и
`sessions_dispatched_today < COALESCE(estimated_daily_capacity, :maxDailySessions)`. Восемь условий против
двух у списка аккаунтов свода (`status <> decommissioned` и привязка проекта, строки 115–117).

**По Витгенштейну это ровно частный язык:** у слова «свободная ёмкость» в своде нет внешнего критерия
правильности — он считает по своему правилу и сам себя проверяет. Критерий, который применяет только один
говорящий, критерием не является.

*Что при этом не наблюдается сейчас:* расхождения по статусам нет — все семь аккаунтов включены и ни один не
в блокирующем статусе, так что по этому пути свод и диспетчер сегодня совпадают. Расхождение по личным
пределам наблюдается **прямо сейчас**: 21 против 33.

*Опровержение, назначенное вперёд:* сделать личный предел равным глобальному у всех аккаунтов; если после
этого `sharedSlotsTotal` совпадёт с суммой, значит расхождение было только в личных пределах, и остаётся
разобрать статусы отдельно.


*Живое, 9 сентября 2026, Codex: 10-такт 1/10 — Project operational context cluster, без правки кода.* Первый кластер нового десятиактного плана заполнен как связанный механизм, а не как отдельный Java-файл. `ProjectOperationalContextService.build(projectId, fallbackProjectName)` собирает `selected_project_only` fact pack for downstream workers; его четыре `findAll()` опасны не сами по себе, а потому что стоят внутри контекста, который потом читает AutoMerge, project flow, design shop and AI resource paths как свидетельство о проекте.

*Идеальная форма кластера:* selected-project context is a proof-carrying fact pack. It begins from one resolved `ProjectEntity`, acquires every subordinate row by that project's task/session/account boundary, and emits facts with enough source identity to prove absence as absence-in-this-project, not absence-in-the-factory. Global rows may enter only through explicitly global submechanisms (`systemStatusProjectOnly` still receives the same project id; GitHub PR snapshot comes from the resolved repository), and every list handed to the worker is either project-scoped and bounded by project history or deliberately named as a live external snapshot.

*Граница и взаимодействия:* upstream owners are `ProjectRepository`, `TaskRepository`, `JulesSessionRepository`, `PrReviewRepository`, `WishlistRepository`, `AccountRepository`, `TaskConflictRepository`, `GitHubPullRequestService`, `SystemStatusService`, `BottleneckDetectionService` and `EmsMetricsService`. Downstream consumers found in code are `GoogleAiResourceController`, `DesignShopOrchestrationService`, `AutoMergeService`, `ProjectFlowService`, plus design/video asset services that carry the `ProjectOperationalContext` value. The context is not allowed to become a second dispatcher: it reports account/session capacity and conflict evidence, while actual account locking remains with the dispatch path.

*Инварианты:* (1) `scope` remains `selected_project_only`; (2) tasks are acquired by `project.id` first and become the join boundary for sessions, reviews and conflicts; (3) review facts preserve review→session→task lineage; (4) conflict facts preserve conflict→task lineage without loading unrelated project conflicts; (5) account availability uses the same project/free-account predicate as the repository method, not a looser dashboard predicate; (6) capacity facts must not contradict the dispatch owner of truth; (7) `systemStatusProjectOnly` is called with this project id and cannot smuggle global dashboard truth into project advice; (8) absent facts are emitted as absent, not guessed by the caller.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `ProjectOperationalContextService.java:95` in `build(projectId, fallbackProjectName)` | `JulesSessionRepository` / `jules_sessions` | hot context for `AutoMergeService`, `ProjectFlowService`, `DesignShopOrchestrationService`, `GoogleAiResourceController` | `project session lineage` | Replacement-known. `taskRepository.findByProjectIdOrderByCreatedAtDesc(project.id)` already defines `taskIds`; repository already declares `findByTaskIdIn(List<UUID>)`. Refutation: building context for project A materializes sessions for project B before filtering. Closure: acquisition uses `findByTaskIdIn(taskIds)` and preserves `updatedAt` desc with nulls last. |
| `ProjectOperationalContextService.java:101` in `build` | `PrReviewRepository` / `pr_reviews` | same context, after sessions are known | `review-by-session lineage` | Replacement-known. Repository declares `findByJulesSessionIdIn(List<UUID>)`. Refutation: project context reads PR reviews for sessions outside the selected project. Closure: acquisition uses session ids from line 95 output only and preserves `createdAt` desc with nulls last. |
| `ProjectOperationalContextService.java:110` in `build` | `TaskConflictRepository` / `task_conflicts` with `TaskEntity` boundary | same context; feeds `conflicts`, bottleneck interpretation and project guidance | `conflict-by-task lineage` | Replacement-known but lineage-sensitive. Repository declares `findByTaskIdIn(List<UUID>)`; implementation must avoid lazy task surprises and preserve `conflict.getTask()` facts needed by `conflictFact`. Refutation: unrelated project conflicts are loaded or task relation disappears after replacement. Closure: query is task-id scoped and returns conflicts with enough task identity for role/status facts, sorted by `detectedAt` desc nulls last. |
| `ProjectOperationalContextService.java:115` in `build` | `AccountRepository` / `accounts` | same context; feeds `accountsAvailableForProject`, `julesUniversalRoleCapacity`, and downstream capacity advice | `project/free account availability` | Replacement-known for acquisition: repository declares `findAvailableForProjectOrderByNameAsc(projectId)` with `status <> decommissioned` and `(currentProjectId is null or = projectId)`. Refutation: context materializes decommissioned or other-project accounts before filtering. Closure: call the repository predicate and preserve case-insensitive name order; separately, capacity semantics must be reconciled with account-specific limits and dispatcher locking before the mechanism can be called ideal. |

*Сильная форма:* the cost and truth boundary of each context sublist is proportional to the selected project answer: sessions by project task ids, reviews by selected session ids, conflicts by selected task ids, accounts by explicit free-or-project predicate. The emitted fact pack states which live snapshot or repository predicate produced each block, and a fixed project fixture returns the same task/session/review/conflict/account identities before and after any implementation change.

*Слабая форма:* the method loads whole tables and then streams them down to the right answer inside one transaction. It appears correct on small data and can even emit `selected_project_only`, but the acquisition process is not reliable: unrelated rows are observed, capacity can be computed by a dashboard-only rule, and downstream mechanisms cannot tell whether a missing fact was absent from the project or merely filtered after a global read.

*Опровержение:* seed two projects with distinct tasks, sessions, reviews, conflicts and accounts; build context for one project. If repository calls or SQL logs show rows from the other project entering the acquisition path, or if `sharedSlotsTotal/freeSessionSlots` disagrees with dispatcher-owned account capacity semantics, the mechanism is not ideal.

*Критерий закрытия:* record is complete for this tact when all four call sites name owner, lineage, replacement and closure. Mechanism becomes ideal only when project fixture tests prove identity-preserving output for `tasks`, `julesSessions`, `databasePrReviews`, `conflicts`, `accountsAvailableForProject` and `julesUniversalRoleCapacity`, and when account capacity uses the same owner-of-truth semantics as dispatch/account locking rather than a private dashboard formula.

*Кандидат на будущую реализацию Codex:* after operator approval for code, the four acquisition replacements are low-risk candidates because the repository predicates already exist. Not safe as a blind patch: the task-conflict relation and account capacity facts need fixture coverage first, and the capacity formula remains a separate mechanism issue.

*Текущий статус:* not ideal. Documentation for the project operational context cluster is now explicit enough to prevent a harmful isolated replacement, but implementation still has four global acquisitions and one already-recorded capacity-language mismatch.

*Свидетельства такта:* `nl -ba src/main/java/com/eneik/production/services/dashboard/ProjectOperationalContextService.java | sed -n '1,160p'`; `sed -n '356,560p'`; `grep -RIn "operationalContextService\|ProjectOperationalContextService\|build(project" src/main/java src/test/java --exclude-dir=target | head -120`; repository reads for `JulesSessionRepository.findByTaskIdIn`, `PrReviewRepository.findByJulesSessionIdIn`, `TaskConflictRepository.findByTaskIdIn`, `AccountRepository.findAvailableForProjectOrderByNameAsc`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `sed -n '1,80p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити: механизм не идеален. Не правь четыре `findAll()` в `ProjectOperationalContextService` как отдельный срез: сначала сохрани весь selected-project fact-pack contract и докажи, что downstream `AutoMergeService`, `ProjectFlowService`, `DesignShopOrchestrationService` and Google AI resource paths получают те же project-only facts, а account capacity не остаётся частным языком свода. Следующий такт 2/10 — constraint/coherence cluster: `ConstraintIdentificationService`, `EvidenceCoherenceService`, bottleneck/constraint truth and falsification/quality cross-links. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.*

**`SystemProgressTracker`** — сердцебиение **настоящего** продвижения: успешная отправка, успешное слияние.
*Связи:* вызывают 4 механизма; ничего не зовёт и не пишет — состояние в памяти.
*Ценность:* пишется не на каждом тике, а только там, где есть выход.
*Комментарий:* **ядро.** Тонкое и верное различение: **«фабрика занята» и «фабрика движется» — разные
предметы**, и путать их значит объявлять холостой ход работой.
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — **сильная**. Опровержение: найти запись, сделанную без выхода.

**`AiHealthTracker`** — успешны ли вызовы моделей, по каждому месту вызова отдельно.
*Связи:* вызывает клиент предсказаний.
*Ценность:* наблюдение за активностью процессов давало **ноль сигнала**, пока генерация срезов часами молча
подставляла выдуманную заглушку.
*Комментарий:* **периферия по предмету, ядро по последствиям.** Активность процесса принята за успешность
работы — категориальная ошибка, и дорогая: работа шла, PR открывались, содержания не было.
*Философия:* `SUPERVENIENCE_WATCH` (D013) — **сильная**. Опровержение: заставить модель отвечать заглушкой и
посмотреть, узнала ли фабрика.

**`RiskLevelCalculator`**, **`MLPredictionServiceClient`** — оценка риска и предсказание узких мест.
*Связи:* клиент предсказаний вызывают трое; зовёт трекер здоровья и клиент суждения.
*Ценность:* оценка риска и предсказание узкого места до того, как оно остановит поток.
*Комментарий:* **периферия.** Их выводы идут через лестницу продвижения, а не действуют сразу — правильный
порядок.
*Философия:* `KNOWLEDGE_FIRST_GATE` (D006) — **сильная**. Опровержение: найти предсказание, действующее без
продвижения.

---

# VIII. Самопроверка

**`FalsificationCycleService`** (1728 строк) — суточный цикл по дефектам кода, недельный философский.
*Связи:* вызывают двое; **зовёт 15 механизмов**; пишет 5 хранилищ.
*Ценность:* единственный, кому позволено порождать замену навсегда провалившейся задаче.
*Комментарий:* **ядро по последствиям.** Помеха, которая здесь была и снята: внутри него жило определение
ограничения запускаемости, за пятью чужими воротами. **Механизм, делающий две несвязанные работы, связывает их
расписания** — и та, что реже, начинает управлять той, что важнее.
*Философия:* `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004) — Ахилле Варци, `BARCAN-TAG-01
ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and Places / formal
ontology of boundaries and spatial parts*. Сильная форма дословно: «**до разделения модулей объявлено, какой
агрегат вправе менять каждую часть**». Слабая: «классы разделены по размеру или по слоям». Опровержение
образца: «**найти поле, которое пишут два сервиса**».

*Замер 2026-09-06 по агрегатам, а не по именам методов.* Первый мой счёт был грубым: я считал совпадения
имени `setStatus` и получил «28 сервисов», но `setStatus` есть у пяти разных сущностей, и счёт по имени их
смешивает. По агрегатам — точно, `grep -rl` по `<repo>.save|saveAll|delete`:

    wishlistRepository              18 пишущих
    taskRepository                  15 пишущих
    evidenceNodeRepository           5 пишущих
    falsificationRunRepository       1 пишущий — только этот механизм
    codeIntegrityFindingRepository   1 пишущий — только этот механизм

*Форма раздваивается, и это полезнее, чем одна оценка.* По **своим двум агрегатам** — прогоны фальсификации
и находки целостности кода — владение **фактически исключительное**: пишет только он. Это и есть сильная
форма, но **не объявленная**: нигде не сказано, что он их хозяин, и завтрашний второй писатель ничему не
противоречит. По **трём общим** — заявка, задача, узлы свидетельства — форма **слабая**: он один из
восемнадцати, из пятнадцати и из пяти, хозяин не объявлен ни у одного.

*По Варци это ровно вопрос границы.* Часть принадлежит целому не потому, что лежит рядом, а потому, что
граница проведена и названа. У двух агрегатов граница фактически есть и не названа; у трёх её нет вовсе.

*Дешёвая половина, которую стоит сделать первой:* объявить хозяином `FalsificationCycleService` два агрегата,
которые он и так пишет один. Это стоит одной строки в записи и одного заслона, считающего число писателей, и
превращает случайное положение дел в объявленное — то, чего образец и требует.

*Опровержение, назначенное вперёд:* появление второго писателя у `falsification_runs` или
`code_integrity_findings`. Появится — исключительность была случайной, и её надо закреплять кодом, а не
записью.

*Перепроверка прежнего утверждения:* раньше в этом файле стояло «`WishlistRepository` — 20 писателей».
Сегодняшний замер даёт **18**. Расхождение не объяснено: прежний счёт снят другим способом и мог включать
чтение или сам файл сущности. До объяснения верным считать сегодняшний, снятый названной командой.

**`EvidenceCoherenceService`** — ECHO Тагарда на собственном графе свидетельств: настоящая релаксация, а не
эвристика со ссылкой.
*Связи:* вызывает `FeatureService`; **ничего не зовёт**; пишет два своих хранилища.
*Ценность:* сводит находки трёх независимых источников, которые иначе не сверялись между собой вообще.
*Комментарий:* **периферия; философски самый амбициозный механизм системы.** Объяснительная связность — сверка
по широте, а не по громкости. Оговорка честности: правильное название метода не значит, что его результат
кто-то читает.
*Философия:* `INUS_FACTOR_CHECK` (D007) — **сильная** по устройству, **не мерена** по читателю. Опровержение:
назвать решение, изменённое его выводом.

**`OpsAuditorService`** — собрать состояние, поставить диагноз, применить узкое доказательное исправление.
*Связи:* вызывающих нет — по расписанию; зовёт 4; **пишет задачи и заявки**.
*Ценность:* решает, **какое** собранное свидетельство достойно действия, без зашитого «если X, то всегда Y».
*Комментарий:* **ядро по последствиям.** Верно по замыслу и опасно по природе: механизм, правящий фабрику по
собственному диагнозу и пишущий задачи и заявки, обязан подчиняться тем же трём статусам, что и я. Не
проверено, воздерживается ли он при неустановленном.
*Философия:* `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006) — Элвин Голдман, `BARCAN-TAG-07
SECOND-ORDER-KNOWLEDGE`, принцип релайабилизма процессов, anchor *A Causal Theory of Knowing / Epistemology
and Cognition*. Сильная форма дословно: «рискованное действие требует свидетельства знаниевого качества, а не
убеждения или намерения; **приложена проверка или источник полномочия**». Слабая: «действие разрешено, потому
что мы уверены». Опровержение образца: «потребовать источник; **ссылка на собственное убеждение и есть
дефект**» — оно и стало планом замера.

*Форма по написанному: **сильная**, замер 2026-09-06.* Источник полномочия назван в самом классе: «the
auditor only ever acts on **independently-verified facts gathered by this class itself**, never on the LLM's
own unverified assertions» (javadoc, строки 41–42), и запись свидетельства снабжена тем же условием: «One
piece of gathered, independently-verified evidence — **never the LLM's own claim**» (строка 125). Пустое
свидетельство — воздержание до обращения к Gemini (строка 131). Перед записью задачи проверка повторяется
трижды: неверный `subjectId` — отказ; предусловие больше не держится — отказ; живая замена или уже идущее
восстановление — отказ (строки 494–516). По Голдману это ровно то, что требуется: судится **надёжность
процесса, породившего убеждение**, а не само убеждение.

*Форма в живом образе: **не проверена**, и это отдельная величина.* За жизнь контейнера механизм отработал
**4** раза, и все четыре кончились «gathered N evidence item(s), Gemini returned no actionable decisions» —
то есть воздержалась **языковая модель**, а не заслон. Созданий восстановительной задачи — **0**, отказов по
неподтверждённому предусловию — **0**. Контрольная проба: строк с именем механизма ровно 4, столько же,
сколько воздержаний, — прибор видит то, что есть. Значит **путь до заслона ни разу не дошёл**: он не
опровергнут и не подтверждён живьём.

*Опровержение, назначенное вперёд:* прогон, в котором Gemini вернула решение, а предусловие к моменту
исполнения уже не держится. Заслон обязан отказать, и это будет видно строкой «precondition no longer holds».


**`PlatformSelfReferenceDetector`** — не про саму ли фабрику эта находка, поданная как находка о продукте?
*Связи:* **ни вызывающих, ни вызываемых — мёртв.**
*Ценность:* четырнадцать «эпиков» из восемнадцати в одном проекте оказались заводским шумом в списке клиента.
*Комментарий:* **ядро по назначению; самая дорогая потеря из мёртвых.** Осиротел вместе с выведенным
наблюдателем, но класс дефекта с ним не ушёл: заявки порождают восемь механизмов, и ни один этой проверки не
делает.
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **слабая**: механизм исправен и не вызван. Опровержение: создать
заявку из заводской находки и посмотреть, попала ли она в список клиента.

**`FactorySelfHealthService`** — фабрика следила за продуктами клиентов и не следила за собой.
*Связи:* вызывает слой инфраструктурного вердикта; зовёт кайдзен.
*Ценность:* файл базы 1678 МБ при 96 МБ данных, пул исчерпан шестнадцать часов, все задачи падают, и ни одного
слова наружу.
*Комментарий:* **ядро.** Фраза из его javadoc — готовый закон: **система, диагностирующая других и слепая к
себе, всегда узнаёт об этом одинаково** — по тому, что оператору покажется, будто машина тормозит.
*Философия:* `SELF_MODEL_SANITY` (D013) — **сильная**. Опровержение: довести базу до нездоровья и посмотреть,
сказал ли кто-нибудь.

**`KaizenService`** и журнал дефектов — предложения улучшений и постоянная запись.
*Связи:* вызывают 5; зовёт 8; пишет 3 хранилища.
*Ценность:* **исчерпание предела обязано быть записано, а не наступать молча.**
*Комментарий:* **периферия.** Молча прекратившееся требование — та самая потеря, против которой написан
закон 3.
*Философия:* `BELIEF_UPDATE_LEDGER` (D007) — **сильная**. Опровержение: исчерпать предел и не найти записи.



*Живое, 9 сентября 2026, Codex: 10-такт 6/10 — Kaizen/lean/lever/market cluster, без правки кода.* Шестой кластер десятиактного прохода заполнен как единый improvement-signal lifecycle: `MarketResearchService` добывает внешние измерения как обычную задачу; `FlowMetricsService` должен арифметически проверять поток; `KaizenService` превращает дефекты и дрейф в предложения/свидетельства/ограниченные действия; `LeverPromotionService` повышает или понижает кандидатов только по накопленным наблюдениям. Эти механизмы нельзя чинить отдельными query-cleanups, потому что все они отвечают за один вопрос: какое улучшение действительно имеет право изменить поведение фабрики.

*Идеальная форма кластера:* every improvement signal has a named scope (`factory`, `project`, `product`, `market`), owner, source rows or external sample, evidence window, freshness rule, actionability class and terminal condition. Market facts enter only as committed research tasks with URLs/method/sample; flow facts enter as project-scoped WIP/throughput/cycle/lead/waste measurements; quality/TOC defects enter through the defect journal and Kaizen proposal store; candidate levers enter through observation pairs and can act only after promotion. A proposal is either review-only evidence, a bounded autonomous action, or a standardized/reverted improvement with baseline/post metric. No layer may silently turn testimony into measurement, a project signal into factory truth, or an observation count into permission to act.

*Граница и взаимодействия:* upstream owners are `ProjectRepository`, `RoleRepository`, `TaskRepository`, `WishlistRepository`, `JulesSessionRepository`, `DefectJournalRepository`, `KaizenProposalRepository`, `EvidenceNodeRepository`, `ProcessControlService`, `SixSigmaAuditService`, `TocSentinelService`, `ConstraintIdentificationService`, `LeverObservationRepository` and `LeverPromotionStateRepository`. Downstream readers include `KaizenController`, `ProjectTreeService`, `EvidenceCoherenceService`, `SystemStatusService`, `ProcessControlService`, `SixSigmaAuditService`, TOC/account/flow levers, market-corpus controller/status, and the normal Jules dispatch path. The cluster records and verifies improvement signals; it must not bypass account capacity, dispatch work directly, mutate client code from market research, or apply review-only systemic/product defects as if a safe actuator existed.

*Инварианты:* (1) improvement identity is stable across storage, evidence nodes, UI lists and recurrence updates; (2) `(projectId, category, targetComponent, status)` is part of proposal truth whenever scope is project-specific; (3) `createdAt`, `lastSeenAt`, `baselineMetric`, `postMetric` and `observedAt` keep freshness and refutation visible; (4) `MarketResearchService` queues work through `TargetContext.ORCHESTRATOR_SYSTEM` and normal task/session accounting, never direct dispatch; (5) `FlowMetricsService` compares WIP, throughput and cycle time for one project and must have a real consumer before it can protect anything; (6) `LeverPromotionService` starts candidates at `observe_only`, promotes only from recent resolved evidence, and demotes immediately on real disagreement; (7) review-only categories (`SYSTEMIC_DEFECT`, `KNOWN_PATTERN_VIOLATION`, `ROLE_QUALITY_DRIFT`, `PRODUCT_RUNTIME_DEFECT`) remain evidence unless a bounded autonomous action is explicitly named.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `KaizenService.java:78` in private `allProposals()` | `KaizenProposalRepository` / `kaizen_proposals` | public Kaizen UI/history, project/factory proposal lists, scan dedupe, auto-action sweep | `proposal lifecycle list / mixed scope helper` | Mixed. Allowed only for explicitly bounded/admin all-proposal views; replacement-needed for project/factory/history/scheduled paths that know scope/status/category. Refutation: project opportunities or scheduled auto-action materialize unrelated project/factory proposals before filtering. Closure: UI all-list has an explicit diagnostic bound, while project/factory/auto paths use repository predicates preserving dedupe order and obsolete standardized/reverted pruning. |
| `KaizenService.java:143` in `findOpenSibling(incoming)` | `KaizenProposalRepository` / `kaizen_proposals` | every new proposal write/recurrence | `open-sibling identity check` | Replacement-needed. The equivalence class is exact and already stated: not same id, `PROPOSED`, same category, target component and project. Refutation: one recurrence scans all proposal rows or finds a sibling from the wrong project. Closure: repository predicate returns the same sibling/null for fixtures with cross-project same category/component and APPLIED/STANDARDIZED rows. |
| `KaizenService.java:158` in `deleteMatching(category,targetComponent,excludeId)` | `KaizenProposalRepository` / `kaizen_proposals` | before writing 2-hour proposals and after standardization | `duplicate cleanup / destructive scope` | Not safe as a local read cleanup until scope is fixed. It deletes by category+component and excludes id, but not by project/status; this can erase another project's proposal with the same component. Refutation: two projects hold `DEFECT_ELIMINATION:QualityGate`, standardizing one deletes the other. Closure: cleanup contract names project/factory scope and statuses, then uses a predicate/delete that cannot cross that boundary. |
| `FlowMetricsService.java:72` in `computeForProject(projectId)` | `TaskRepository` / tasks, with `JulesSessionRepository` cycle-time evidence and `WishlistRepository` waste counts | currently no production caller found; intended Lean consistency check | `project flow measurement` | Replacement-needed and currently inert. The method already knows `projectId`; it needs project-scoped task acquisition and count/projection support, plus a real consumer that records or exposes Little's Law inconsistency. Refutation: computing metrics for project A materializes project B tasks, or no mechanism ever calls the check when WIP/throughput/cycle time disagree. Closure: project fixture preserves wip/done/cycle/lead/throughput outputs without full-table reads, and a named caller records/exposes persistent inconsistency. |
| `LeverPromotionService.java:105` in scheduled `evaluatePromotions()` | `LeverPromotionStateRepository` and `LeverObservationRepository` | scheduled every 2h, cross-cutting lever promotion | `lever-state sweep` | Conditionally allowed only if the lever-key registry is intentionally finite and state count is observed/bounded. Otherwise replacement-needed by active lever registry/status. Refutation: stale or typo-created lever keys accumulate and every 2h promotion evaluates dead state rows forever. Closure: state sweep is bounded to known active lever keys or proves live cardinality/freshness; recent observations remain acquired by `findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc`. |
| `MarketResearchService.java:69` in `createResearchTask(profileId,market,sampleSize)` | `ProjectRepository` carrier project, `RoleRepository` research role, `TaskRepository` queued task | `/internal/market-corpus/research`, operator-triggered | `orchestrator-system carrier selection` | Replacement-needed. The method needs one deterministic carrier project policy, not arbitrary first project materialization. Refutation: the first row is archived/wrong project, or two DB orders create different carrier tasks for identical research requests. Closure: carrier source is named (configured factory project, active/latest deterministic project, or explicit error) and task remains `TargetContext.ORCHESTRATOR_SYSTEM` with bounded sample and normal dispatch accounting. |

*Сильная форма:* improvement signals form one reliability chain: acquisition source, timestamp/window, freshness rule, identity key, owner and validation path are visible from market observation to queued research task, from defect journal to Kaizen proposal/evidence node, from flow metrics to a recorded inconsistency, and from lever observation to promoted/demoted stage. A future implementation can change acquisition cost only after a fixture proves the same proposals, metrics, evidence nodes, lever stages and carrier tasks.

*Слабая форма:* the services contain many correct local ideas, but the chain is not ideal: Kaizen mixes all-proposal helper use with scoped lifecycle questions, duplicate cleanup has destructive cross-project risk, FlowMetrics is created but not called, lever-state sweep assumes bounded state without proving it, and market research chooses a carrier project by database row accident.

*Опровержение:* build a fixture with two projects, same Kaizen category/component in both projects, a factory-scope proposal, standardized/reverted old proposals, three done tasks with sessions for one project, another project's tasks, wishlist waste rows, one stale lever key, one live lever with recent TRUE/FALSE/NEITHER observations, and an archived/new active carrier project. The mechanism is false if scoped Kaizen views or auto-actions read/delete across projects, if FlowMetrics changes numbers after project-scoped acquisition, if the lever sweep evaluates dead keys as active truth, or if market research carrier selection is nondeterministic while still claiming normal dispatch accounting.

*Критерий закрытия:* this record is complete for tact 6 when all six `.findAll()` acquisitions in the cluster are named with owner, boundary, refutation and closure. The implementation becomes ideal only when Kaizen proposal reads/deletes are scoped by declared lifecycle predicates, FlowMetrics has a named live consumer and project-scoped acquisition, LeverPromotion has a bounded active-key registry or measured sweep contract, MarketResearch has deterministic carrier selection, and fixtures prove proposal identity, evidence-node linkage, flow arithmetic, lever promotion/demotion and market task creation remain semantically identical except for the intentional boundary fixes.

*Кандидат на будущую реализацию Codex:* after explicit code approval, the safest candidates are documentation-backed predicate additions for `findOpenSibling` and project-scoped `FlowMetricsService.computeForProject`; both still need focused fixtures first. `deleteMatching` is not safe until its project/status scope is decided. `MarketResearchService` carrier selection is tied to the existing active-project/fallback policy question. `LeverPromotionService` should not be changed until active lever-key cardinality is measured or declared.

*Текущий статус:* not ideal. The connected improvement-signal lifecycle record is now explicit, but implementation still has six full-table acquisitions in this cluster and one dead measurement mechanism (`FlowMetricsService` has no caller found by source grep).

*Свидетельства такта:* `grep -n "\.findAll()"` across `KaizenService`, `FlowMetricsService`, `LeverPromotionService`, `MarketResearchService`; focused `nl -ba` contexts around `KaizenService` lines 77-163, 214-326, 628-858; `FlowMetricsService` lines 71-143; `LeverPromotionService` lines 50-141; `MarketResearchService` lines 49-140; `KaizenController` lines 25-78; `MarketResearchController` lines 35-57; repository grep for `KaizenProposalRepository`, `WishlistRepository`, `TaskRepository`, `LeverObservationRepository`, `ProjectRepository`; caller grep for `computeForProject`, `createResearchTask`, `recordObservation`, `currentStage`, `getDeduplicatedProposals`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`; `grep -n "RELIABILITY_CHAIN" docs/philosopher-patterns/03_PATTERN_STRENGTH.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `KaizenService`, `FlowMetricsService`, `LeverPromotionService` or `MarketResearchService` как отдельные query-cleanups: сначала сохрани один improvement-signal lifecycle — stable proposal identity, scoped duplicate cleanup, live flow-metric consumer, bounded active lever keys, deterministic orchestrator-system carrier project, and normal dispatch/accounting for research tasks — then prove by fixture that proposals, evidence nodes, flow arithmetic, lever stages and market tasks keep their meaning. Следующий такт 7/10 — operational truth/audit/retention cluster: `TrustSnapshotService`, `FlowSpineService`, `OpsAuditorService`, `DeliveryRealityProducerService`, `ProjectEventLogRetentionService`, and retention/freshness invariants. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для scope переходов дополнительно держать `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`.*

**`PlannedWorkRecoveryService`** — возвращает ту же личность задачи, рубеж за рубежом, и **никогда** не создаёт
задачу, заявку, ветку или сессию.
*Связи:* вызывают 5; зовёт 5; **пишет задачи и заявки**.
*Ценность:* восстановление, создающее новое, есть второй заказ, а не восстановление.
*Комментарий:* **ядро; здесь закрыт худший круг смены.** Его гарантия сдержана буквально — он не создал ни
одной задачи. И при этом из-за него создано шестьдесят четыре. **Гарантия сформулирована о собственных
действиях, а не о следствиях**, и потому истинна и бесполезна одновременно. Отсюда правило для всякого
механизма: гарантия вида «я не делаю X» ничего не обещает о том, будет ли сделано X; проверка — назвать
механизм, который сделает X вследствие моего действия.
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **сильная** после починки посылки уборки. Опровержение: сбросить
состояние и посчитать строки, созданные другими вследствие сброса.

**`ContinuousOrchestrationService`** — общий тик: что делать сейчас, кого восстановить, кого разблокировать.
*Связи:* вызывающих нет — по расписанию; **зовёт 18 механизмов**; пишет `AccountRepository`.
*Ценность:* одно место, решающее очерёдность, вместо десятка независимых кронов.
*Комментарий:* **ядро.** Его ценность прежде всего в **предотвращении помех**. Почти каждый механизм
наблюдения отдельно оговаривает, что **не заводит своего крона**, а встраивается сюда, — редкая дисциплина.
*Философия:* `PLANNING_CONSISTENCY` (D004) — **сильная**. Опровержение: найти второй крон, решающий
очерёдность действий проекта.

**`OperationalPolicyService`**, **`OperationalFlowCoreService`**, **`OperationalTruthService`**,
**`OperationalAction`**, **`FlowSpineService`** — операционная политика и хребет потока.
*Связи:* политику вызывают 5; хребет зовёт 10 и пишет свой журнал событий.
*Ценность:* единственное детерминированное состояние проекта и разрешённые переходы.
*Комментарий:* **ядро.** `FlowSpineService` — то, чем я меряю фабрику каждый такт, и он держит инвариант
`done_is_not_delivery`, честно показывая его нарушение предупреждением. Редкое: **механизм публикует
свидетельство против собственного благополучия.** Свод мог бы молчать о том, что 430 задач дали 20 сдач; он не
молчит.
*Философия:* `ANTI_MIRROR_TELEMETRY` (D013) — **сильная**. Опровержение: найти величину, которую хребет
показывает лучше, чем она есть.

**`TrustSnapshotService`** — снимки входных сигналов доверия с дозаполнением настоящего исхода.
*Связи:* вызывающих нет — по расписанию; зовёт двоих.
*Ценность:* сбор данных для решателя, который **ещё не имеет права решать**.
*Комментарий:* **периферия.** Правильный порядок: сперва накопить свидетельство, потом дать полномочия.
*Философия:* `PERSISTENCE_SNAPSHOT` (D010) — **сильная**. Опровержение: найти снимок без дозаполненного исхода.

---



*Живое, 9 сентября 2026, Codex: 10-такт 7/10 — operational truth/audit/retention cluster, без правки кода.* Седьмой кластер десятиактного прохода заполнен как один механизм operational truth retention: `FlowSpineService` строит детерминированное состояние потока и события; `TrustSnapshotService` сохраняет входы доверия до будущего исхода; `OpsAuditorService` превращает проверенное операционное свидетельство в ограниченное действие; `DeliveryRealityProducerService` делает недоставленную работу видимой как evidence/scope; `ProjectEventLogRetentionService` удерживает долговечный журнал в границах. Это не пять соседних scheduled cleanup paths, а одна память фабрики о том, что она видела, решила, сделала и сколько этого можно хранить.

*Идеальная форма кластера:* every operational fact has a durable level and lifetime. Hot flow state is computed from project-scoped repositories and may emit a bounded event; trust snapshots preserve input signals until a real delivered/abandoned outcome exists; auditor actions require independently gathered evidence and re-verified preconditions; delivery-reality findings speak only about product delivery or carrier channel, never both; project logs are retained by meaning, accepted-state grace and per-project ceiling, with cadence tied to observed growth. A reader can recover source, timestamp, scope, freshness rule, action owner and retention rule for each fact.

*Входы и выходы:* inputs are project rows/status, tasks, wishlists, Jules sessions, PR reviews, system status, readiness, operational reality findings, evidence nodes, trust snapshots, invariant transitions and project event logs. Outputs are `FlowSpineDto`/`FlowSpineEventEntity`, `TrustSignalSnapshotEntity` with eventual outcome, auditor-created/dismissed wishlist/task corrections, `OperationalRealityFindingEntity` plus `EvidenceNodeEntity`, defect-journal rows, and deleted/retained project-log rows. The cluster does not own GitHub truth, dispatch capacity, quality math, product runtime health or market facts; it cites those owners.

*Граница и взаимодействия:* downstream consumers include `OperationalPolicyService`, `OperationalFlowCoreService`, `ContinuousOrchestrationService`, dashboard controllers, `SystemStatusService`, `KaizenService`, `EvidenceCoherenceService`, Gemini observer/action paths, dispatch/admission code and operator audit surfaces. Shared upstream owners include `ProjectRepository`, `TaskRepository`, `WishlistRepository`, `JulesSessionRepository`, `PrReviewRepository`, `FlowSpineEventRepository`, `TrustSignalSnapshotRepository`, `OperationalRealityFindingRepository`, `EvidenceNodeRepository`, `ProjectEventLogRepository`, `ClientDeliverableReadinessService`, `OperationalTruthService`, `SystemStatusService`, `GeminiContextService` and `MLPredictionServiceClient`. The cluster may observe, record, audit and bound; it must not invent delivery, hide unresolved states, or let retention erase the only witness of unfinished work.

*Инварианты:* (1) active-project sweeps name their active-project acquisition and never silently scan archived/frozen projects as live work; (2) read models do not write except through explicit `observe`/producer/snapshot paths; (3) evidence rows keep project/product/factory/carrier scopes disjoint; (4) a sweep that abstains says so, because silence is not liveness; (5) auditor writes are tool-whitelisted and re-validate their own precondition; (6) snapshots are not predictions until outcomes are backfilled; (7) event-log retention never deletes unaccepted project history by age, and its ceiling/frequency must keep table size within the declared bound between sweeps.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `TrustSnapshotService.java:58` in `captureAndBackfillSnapshots()` | `ProjectRepository` active projects; `TrustSignalSnapshotRepository` unresolved outcomes | scheduled every 2h | `trust-signal snapshot sweep` | Replacement-needed. The method needs active project ids for snapshot capture, not every project entity. Refutation: archived/frozen project rows are materialized before being skipped. Closure: `findByStatusOrderByCreatedAtDesc(active)` or equivalent id projection captures the same active projects and backfill still handles unresolved snapshots separately. |
| `FlowSpineService.java:1015` in `shadowCheckEmbeddingDuplicatesAcrossActiveProjects()` | `ProjectRepository` active projects; `TaskRepository` project tasks; `LeverPromotionService` D3 observations | scheduled every 15min shadow check | `active-project lever observation sweep` | Replacement-needed but only as part of the D3 evidence contract. The active sweep should read active project ids, then project-scoped task candidates capped by `MAX_CANDIDATES_PER_PROJECT`. Refutation: a non-active project is materialized or a shadow tick creates observations from outside active work. Closure: active-id acquisition plus fixture proves same D3 observations and no hot-path embedding call. |
| `OpsAuditorService.java:108` in `runAuditCycle()` | `ProjectRepository` active projects; gathered evidence from project wishlists/tasks/readiness | scheduled every 30min when `ops_auditor_enabled` | `auditor admission sweep` | Replacement-needed. The auditor needs active projects only, then must log abstain/action per project. Refutation: disabled/frozen/archived projects are loaded as candidates, or a sweep with no evidence leaves no liveness record. Closure: active acquisition preserves per-project `LogScope`, gathered evidence, abstain logs and whitelisted/revalidated tool execution. |
| `DeliveryRealityProducerService.java:941` in `produce()` | `ProjectRepository` active projects; product-delivery evidence owners | scheduled hourly at minute 20 | `delivery-reality evidence producer` | Replacement-needed. It should acquire active projects directly; the inner producer already uses project-scoped tasks/wishlists and shared readiness predicates. Refutation: archived project rows are read as live delivery candidates, or product/carrier evidence mixes scopes. Closure: active acquisition preserves findings, evidence nodes, carrier-channel rows and missing-work scope filings on a two-project fixture. |
| `ProjectEventLogRetentionService.java:79` in `enforceRetention()` | `ProjectRepository` all project lifetimes; `ProjectEventLogRepository` counts/deletes | scheduled daily 03:17 by default | `retention lifetime sweep` | Not a simple active-project rewrite. Retention legitimately reasons over accepted, active, frozen and archived projects, because ceiling applies per project and age deletion applies only after accepted grace. The defect is cadence/bound proof, not row semantics. Refutation: table grows far above `maxEntriesPerProject` between daily sweeps, or unaccepted history is deleted by age. Closure: retention cadence follows observed growth and the project sweep is either declared bounded reference data or replaced by status/id projections preserving accepted-grace and per-project ceiling rules. |

*Сильная форма:* operational truth is a reliability chain with durable witnesses: every sweep names which projects it is allowed to touch, every produced/audited fact has project/factory/product/carrier scope, every no-op/abstain that matters is externally visible, and retention cannot erase unfinished evidence. `RELIABILITY_CHAIN` supplies source/timestamp/freshness/validation; `PERSISTENCE_SNAPSHOT` supplies recoverable identity over time; `ANTI_MIRROR_TELEMETRY` forbids the system from describing itself better than its logs/evidence allow.

*Слабая форма:* each scheduled service can be locally correct, but several start by materializing all projects and then filtering. That is tolerable only while project count is small and semantics are obvious; as a mechanism it leaves active-work sweeps, retention sweeps and evidence producers without one declared acquisition language. The result can be clean logs with hidden acquisition drift or a project log that is bounded only once per day while growing for the other twenty-three hours.

*Опровержение:* create a fixture with active, frozen, archived and accepted projects; unresolved trust snapshots; duplicate task candidates; auditor evidence and no-evidence cases; delivery-missing product tasks plus carrier tasks; and project logs over the ceiling. The mechanism is false if any active-only sweep reads non-active projects as candidates, if auditor no-evidence produces silence, if DeliveryReality mixes product and carrier scope, if trust snapshots cannot be backfilled to delivered/abandoned, or if retention deletes unaccepted history by age or allows size to exceed the declared bound between sweeps.

*Критерий закрытия:* this record is complete for tact 7 when all five `.findAll()` acquisitions in the operational truth/audit/retention cluster are named with owner, boundary, refutation and closure. The implementation becomes ideal only when active-only sweeps share a project-id/status acquisition contract, retention cadence is proven against observed growth, fixtures prove scope separation and liveness logs, and every produced fact can be traced to source rows, timestamp, freshness rule and validation path.

*Кандидат на будущую реализацию Codex:* after explicit code approval, `TrustSnapshotService`, `OpsAuditorService`, `DeliveryRealityProducerService` and the FlowSpine D3 shadow sweep are plausible candidates for an active-project finder/id-projection plus parity fixtures. `ProjectEventLogRetentionService` is not a query cleanup candidate until the cadence/growth bound is chosen; its all-project semantics are part of the retention mechanism.

*Текущий статус:* not ideal. The operational record is explicit, but implementation still has four active-only sweeps using project `findAll()` and one retention sweep whose documented frequency lags the measured log growth.

*Свидетельства такта:* `grep -R "class ProjectEventLogRetentionService\|\.findAll()" -n src/main/java/com/eneik/production | grep -E "TrustSnapshotService|FlowSpineService|OpsAuditorService|DeliveryRealityProducerService|ProjectEventLogRetentionService"`; `nl -ba` contexts for `TrustSnapshotService` lines 55-68 and 142-165, `FlowSpineService` lines 161-186 and 1013-1035, `OpsAuditorService` lines 103-185 and 350-460, `DeliveryRealityProducerService` lines 939-1045, `ProjectEventLogRetentionService` lines 69-130; repository reads for `ProjectRepository`, `ProjectEventLogRepository`, `TrustSignalSnapshotRepository`, `FlowSpineEventRepository`, `OperationalRealityFindingRepository`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`; `grep -n "RELIABILITY_CHAIN\|PERSISTENCE_SNAPSHOT\|ANTI_MIRROR_TELEMETRY" docs/philosopher-patterns/03_PATTERN_STRENGTH.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `TrustSnapshotService`, `FlowSpineService`, `OpsAuditorService`, `DeliveryRealityProducerService` or `ProjectEventLogRetentionService` как отдельные query-cleanups: сначала сохрани один operational truth retention contract — active-only sweeps share project acquisition, auditor abstain remains visible, delivery/product/carrier scopes stay separated, trust snapshots backfill real outcomes, and log retention cadence matches measured growth without deleting unaccepted history by age. Следующий такт 8/10 — Gemini observer/context cluster: `InternalGeminiObserverController`, already-recorded `GeminiContextService`, Linear sync if it participates in context freshness, and observer grounding contracts. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; additionally hold `PERSISTENCE_SNAPSHOT` and `ANTI_MIRROR_TELEMETRY` for recoverable evidence and self-reporting.*

# IX. Внешние системы

**`GoogleAiResourceService`**, **`GeminiContextService`**, **`GeminiContextCacheManager`** — модели, RAG-слой,
кэш статичного корпуса.
*Связи:* контекстный слой **вызывают 8 механизмов** — самый востребованный в разделе; кэш не вызывает никто.
*Ценность:* постоянное знание индексируется один раз, а не пересылается сырым текстом на каждый вызов; кэш дал
время до первого токена с ~4.5 с до ~0.8 с и входную цену −76%.
*Комментарий:* **периферия.** Решение не строить дообучение и векторную базу принято по размеру корпуса, а не
по моде — верный порядок рассуждения. **Замер до и после — то, чем оптимизация отличается от веры в неё.**
*Философия:* `RAG_GROUNDING_CAPSULE` (D014) — **сильная**. Опровержение: найти вызов, пересылающий корпус
сырым текстом.

*Живое, 8 сентября 2026, Codex:* начат срез очереди по `GeminiContextService.retrieveFiltered`: если query embedding не построился, retrieval теперь возвращает пустой результат до подъёма корпуса через `repository.findAll()`. Следующим тактом успешный путь перестал поднимать `content` всех chunks: similarity считается по projection `findAllVectorRows()` (`id/sourceType/sourceRef/embedding/embeddingDims`), а полный `content` читается только через `findAllById(...)` для выбранных top-k. Срез source-type retrieval больше не читает vector rows всего корпуса: `retrieveRelevantContextBySourceTypes(...)` использует `findVectorRowsBySourceTypeIn(...)`. Срез простого sourceRef-prefix retrieval теперь использует `findVectorRowsBySourceRefStartingWith(...)`. Срез `buildPhilosopherPatternContext` теперь использует projection query `findVectorRowsBySourceTypeAndSourceRefStartingWith(...)` по `philosopher_pattern` и тегу роли. Это не закрывает весь дефект: unscoped retrieval и более сложный product-worker фильтр всё ещё читают все embeddings. Проверка: `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 maven:3.9-eclipse-temurin-17 mvn -q -Dtest=GeminiContextServiceTest test` — пройдена.

**`EmbeddingSimilarityUtil`** (31 строка) — общий дом для векторной арифметики.
*Связи:* вызывают ровно двое — и это весь смысл.
*Ценность:* два независимо поддерживаемых экземпляра одного вычисления запрещены уставом.
*Комментарий:* **периферия; предотвращение помехи в чистом виде.** Третье подтверждение одного правила после
`KanoClass` и `EmsFlowStage`: **дублированное правило расходится, вопрос лишь во времени.**
*Философия:* `ANCHOR_BOUND_NAME` (D001) — **сильная**. Опровержение: найти вторую копию косинуса.

**`StitchClient`**, **`DesignAssetService`**, **`VideoAssetService`** — генерация экранов, ассетов, видео.
*Связи:* `StitchClient` вызывают трое; наружу не зовёт никого.
*Ценность:* Stitch тарифицируется отдельно от основного баланса и используется как бесплатная замена дорогой
генерации.
*Комментарий:* **периферия.** **Знание о том, из какого кармана платится вызов, — часть инженерного решения, а
не бухгалтерии.**
*Философия:* `DECISION_EXPECTED_LOSS` (D005) — **сильная**. Опровержение: найти генерацию, идущую по дорогому
пути без причины.

**`RuntimeLauncherClient`** — единственное в бэкенде, что знает о существовании сайдкара.
*Связи:* вызывают 4; наружу не зовёт.
*Ценность:* бэкенд **никогда не трогает Docker сам**, только узкий контракт «запусти / проверь / погаси».
*Комментарий:* **периферия по предмету, ядро по последствиям.** Одна точка знания о внешнем — то же правило,
что один транспорт к Jules и один к GitHub.
*Философия:* `BOUNDARY_TOPOLOGY` (D006) — **сильная**. Опровержение: найти второе место, знающее о сайдкаре.

**`GeminiObserverActionService`** — настоящие некодовые полномочия наблюдателя.
*Связи:* вызывающих нет — наблюдатель выведен; **зовёт 10 механизмов и пишет 4 хранилища**.
*Ценность:* каждый метод — безопасная дверь к уже существующей операции, ничего нового.
*Комментарий:* **периферия, но с оговоркой, которую надо назвать:** механизм без вызывающих, зовущий десять
других и пишущий четыре хранилища, есть **заряженное ружьё на стене**. Полномочий он не потерял, потерял
только того, кто их применял.
*Философия:* `RIGHTS_DUTIES_MATRIX` (D006) — **слабая**: полномочия живы, владелец отсутствует. Опровержение:
назвать, кто сегодня вправе их применить.

**`GeminiProjectObserverService`** — выведен навсегда как муда, заперт миграцией; заглушка ради контрактов.
*Комментарий:* **правильно похоронен, и правильно оставлен след.** Заглушка вместо удаления хранит запись о
том, что здесь было и почему ушло; молча исчезнувший механизм оставляет вопрос, на который через месяц никто
не ответит.
*Философия:* `DEFEASIBLE_EXCEPTION_LEDGER` (D012) — **сильная**: вечное исключение объявлено правилом.

---

# X. Дизайн-цех

**`DesignShopOrchestrationService`** — параллельный департамент, вписанный в конвейер **без правки самого
конвейера**; срабатывает на каждом переходе готовности из «нет» в «да».
*Связи:* вызывающих нет — по расписанию; зовёт 6; пишет свой цикл и заявки.
*Ценность:* добавить цех, не тронув поток.
*Комментарий:* **периферия, и образец того, как расширять фабрику** — прямое исполнение критерия Куайна:
периферия достраивается правками, ядра не касающимися. Второе тонко и верно: полная готовность здесь
**недостижима по устройству**, потому что циклы фальсификации добавляют работу, пока проект жив; значит «100%
готово» означает «готово собрать этот круг». Принять одно за другое было бы категориальной ошибкой о
собственном устройстве.
*Философия:* `RUT_BARKAN_MARKUS`-семейство, `ESSENCE_BEFORE_OPTION` (D002) — Рут Баркан Маркус,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип квантифицированного актуализма, anchor *A Functional Calculus of
First Order Based on Strict Implication*. Сильная форма дословно: «до добавления настройки назван инвариант,
истинный во **всех** допустимых режимах, и ветви конфигурации либо его хранят, либо **падают закрыто**».
Слабая: «настройка добавлена, режимы описаны в README». Опровержение образца: «**найти комбинацию значений,
при которой инвариант не назван и не проверен**».

*Замер 2026-09-06. Первое подозрение опровергнуто.* Настройка `design_shop_readiness_threshold` в живой
фабрике не имеет значения нигде — `source=none`. Я предположил дефект и ошибся: обе точки применения задают
инвариант прямо в вызове, `effectiveDouble("design_shop_readiness_threshold", **0.80**)`, и живьём в журнале
стоит `threshold=0.800`. Порог назван там, где применяется.

*Комбинация, которой требует опровержение, найдена — и она другая.* `isReadinessReached` начинается с
жёсткого предусловия: `if (!readiness.decompositionComplete()) return false;`, **до** всякого порога. Живой
замер: `decompositionComplete=false`, `ratio=0.900` и `0.909` при пороге `0.800`. То есть **фронт готовности
по доле пройден, а цех стоит**, потому что раньше него сработал булев флаг. За семь часов жизни контейнера
цех не произвёл ни одного экрана: аудитов согласованности **0**, отказов **0**.

*И это условие модель уже признала недостижимым.* «Опровергнутое», пункт 2: *«Фаза декомпозиции должна
завершаться». **Не должна и не может**: девять сервисов порождают вишлисты непрерывно, множество открытых
брифов не опустеет никогда.* Счёт по журналу подтверждает: `decompositionComplete` — **два false на один
true**, и сейчас `false`.

*Ирония, которую стоит записать целиком.* Javadoc метода объясняет замысел: оценивать фронт по доле,
«**rather than stalling on unreachable 1.0**» — чтобы не встать на недостижимой единице. Одно недостижимое
условие убрали и оставили другое: цех встал на недостижимом **булевом**.

*Форма: **слабая**.* Инвариант «цех работает, когда объём перешёл фронт готовности» назван — через долю; но
комбинация «доля выше порога **и** декомпозиция не завершена» инвариантом не покрыта: фронт объявляется
пройденным, а прежний флаг молча накладывает вето. Ветвь не хранит инвариант и не падает закрыто — она
возвращает `false` без объяснения, какое из двух условий не выполнилось.

*Что делать:* либо `decompositionComplete` перестаёт быть предусловием и входит в ту же долю, либо сообщение
о выдержке обязано называть, **какое именно** условие не выполнилось. Сейчас оно печатает оба числа и не
говорит, что решило.

*Опровержение, назначенное вперёд:* довести долю до 1.0 при незавершённой декомпозиции. Цех обязан либо
пойти, либо назвать причину отказа именем.
*Второго разрешения цех не производит:* задание ревьюеру дизайна (`ProjectFlowService:5688–5700`) прямо говорит «this is a single generated screen, not a desktop/mobile pair — do NOT reject it solely for missing a second resolution». Пока генерируется один экран на одно разрешение, упрекать ревьюера не в чем: вопрос ему не задан. Предписание 31.

**`DesignConsistencyAuditService`** — язык экрана либо восходит к объявленной дизайн-системе, либо нет.
*Связи:* вызывает генератор ассетов.
*Ценность:* три экрана под одним идентификатором пришли в трёх несовместимых цветовых регистрах.
*Комментарий:* **периферия.** **Передача идентификатора не есть согласованность** — идентификатор принят за
свойство; снова категориальная ошибка, и снова ловится машиной, а не глазом оператора.
*Философия:* `CROSS_SCREEN_JACCARD_GATE` (D015) — **сильная**: экраны сверяются между собой, а не только с
системой. Опровержение: сверить два экрана одной системы и посчитать пересечение словаря.
*Телефон — не его предмет, и это замер, а не упрёк:* `extractUsedTokens(html)` берёт только hex-цвета, `rgb()` и `font-family`. Геометрии — расстояний, перекрытий, порядка наложения — он не касается вовсе, поэтому наползание меню на узком экране он пропустит при любом качестве работы. Разбор и что делать — предписание 31, образец `GROUPING_PROXIMITY_GATE` (D011), в фабрике не применён ни разу.

**`DesignSystemFalsificationService`** — применяет систему к **уже слитому** настоящему UI, а не к догадке.
*Связи:* вызывающих нет — по расписанию; пишет заявки.
*Ценность:* фальсификация требует предмета, который уже существует.
*Комментарий:* **периферия.** Разница между «уточнить реальное» и «предположить будущее» здесь и есть весь смысл.
*Философия:* `FALSIFICATION_HARNESS` (D008) — **сильная**. Опровержение: найти применение системы к мокапу.

**`DesignDriftMonitorService`** — дрейф против живого продукта, **в чужом уже открытом окне наблюдения**.
*Связи:* вызывает наблюдение рантайма; зовёт запускатель.
*Ценность:* два независимых окна над одним продуктом мешали бы друг другу и удваивали расход.
*Комментарий:* **периферия; предотвращение помехи.**
*Философия:* `PLANNING_CONSISTENCY` (D004) — **не мерено; прежняя оценка «сильная» снята замером 2026-09-05 — механизм ни разу не выполнил своё сравнение, см. предписание 28**. Опровержение: найти второй цикл запуска.

---

# XI. Наблюдение за живым продуктом клиента

**`ClientRuntimeObservabilityService`** — решает на общем тике, пора ли потратить один настоящий цикл
«запустить и проверить».
*Связи:* вызывают двое; зовёт 8; пишет `ProjectRepository`.
*Ценность:* момент проверки выводится из накопленного свидетельства, а не из расписания.
*Комментарий:* **периферия.** Дважды оговорено и дважды верно: никогда не новый крон, и **не притворяться
сравнением там, где сравнивать не с чем** — прямо объяснено, почему это не решатель на лестнице продвижения.
Редкая аккуратность.
*Философия:* `SELF_MODEL_SANITY` (D013) — **сильная**. Опровержение: найти у него собственный крон.

**`BetaPosterior`** (112 строк) — вера в вероятность успешного запуска как бета-апостериор; следующая проверка
выводится из ширины доверительного интервала.
*Связи:* вызывает наблюдение рантайма; ничего не зовёт.
*Ценность:* **предел выводится, а не назначается.**
*Комментарий:* **периферия; самый чистый пример правильной формы во всём коде.** Ровно то, чего не хватало
сегодняшним константам: там число взяли из воздуха, здесь оно следует из накопленного свидетельства. Сто
двенадцать строк, показывающие, как надо.
*Философия:* `BELIEF_UPDATE_LEDGER` (D007) — **сильная**. Опровержение: найти в нём назначенную константу срока.

**`RuntimeHealthShiftDetector`** — «настоящий сдвиг или невезение» — математически другой вопрос, чем оценка
стабильной доли.
*Связи:* вызывает наблюдение рантайма.
*Ценность:* попытка переиспользовать u-карты здесь была бы ошибкой рода.
*Комментарий:* **периферия; прямой отказ от категориальной ошибки.** Там подгруппа эпик, здесь временной ряд
наблюдений. **Разные роды единиц нельзя вести одной машинерией только потому, что обе называются «контроль
качества».**
*Философия:* `CATEGORY_ERROR_SCAN` (D002) — **сильная**. Опровержение: найти общий код с u-картами.

**`ProductCapabilityService`** — ценность продукта, сделанная счётной: число возможностей, **которые продукт
сам о себе заявляет** в контракте OpenAPI и которые подтверждены нижней доверительной границей.
*Связи:* вызывает наблюдение рантайма; зовёт транспорт GitHub и запускатель.
*Ценность:* знаменатель берётся из утверждений продукта, а не из нашей декомпозиции.
*Комментарий:* **периферия по месту, ядро по смыслу.** Считать по собственной декомпозиции значит мерить свою
работу своей же меркой. **Внешний знаменатель — единственная защита от самоаттестации.**
*Философия:* `SUBSTITUTION_ORACLE` (D009) — **сильная**. Опровержение: найти знаменатель, взятый из графа задач.

---

# XII. Журнал, память, санитария

**`LogScope`**, **`ScopedBufferAppender`**, **`LogScopeBuffer`**, **`DurableProjectLogAppender`**,
**`ProjectLogFlushQueue`**, **`ProjectEventLogService`**, **`ProjectEventLogRetentionService`** — разметка
всякой строки областью, кольцевой буфер, долговечный журнал проекта и его ограничение по смыслу.
*Связи:* `LogScope` **вызывают 13 механизмов** — самая широкая связь в системе; долговечная часть идёт от
логбэка и по расписанию.
*Ценность:* заводской шум не имеет права протечь в контекст, который читают роли проекта.
*Комментарий:* **периферия; лучшее разделение областей во всей системе.** Это тот же закон 2 (носитель
отдельно от продукта), применённый к **записям**. Три решения образцовы: точка исполнения запрета названа
явно; ограничение журнала **по смыслу, а не по возрасту** — из непринятого проекта не удаляется ничего,
сколько бы лет ни прошло, а удаление по возрасту было бы категориальной ошибкой, где время принято за
незначимость; очередь сброса ограничена, чтобы отказ базы вырождался в «недавняя история потеряна», а не в
падение.
*Философия:* `PROHIBITION_AS_CODE` (D006) — **сильная**. Опровержение: найти заводскую строку в проектном
буфере.

**`SystemSettingsService`** — настройки, база выше конфигурации.
*Связи:* вызывающих в коде почти нет — идёт от контроллера; читают многие через `effectiveValue`.
*Ценность:* умеет докладывать о **булевых флагах без значения** — настройка, которую никто не читает, есть муда.
*Комментарий:* **ядро по последствиям** — настройка может остановить поток, что сегодня и произошло. И вот
измеренный изъян: **настройки пишет одна строка кода, и только `system_stall_status`**. Всё остальное — включая
привязку компилятора — меняется снаружи и **не оставляет следа о том, кто и когда**.
*Философия:* `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` (D007) — Джон Сёрл, `BARCAN-TAG-12
SOCIAL-CONTRACT`, статусные функции и институциональные факты («X считается Y в контексте C»), anchor
*Speech Acts / The Construction of Social Reality*. Сильная форма дословно: «статус создаётся **правилом**, и
есть **запись аудита** о том, что правило применилось». Слабая: «статус присваивается в коде там, где
показалось уместным». Опровержение образца: «назвать правило, создающее статус; если названо место, а не
правило — регистра нет».

*Форма: **слабая**, и замером 2026-09-06 она разложена надвое — половина держится, половина отсутствует.*

**Правило есть и применяется.** `save(key, value)` начинается с `requireDefinition(key)` и
`rejectIfMalformed`: неизвестный ключ не пройдёт. Проверено опытом снаружи —
`PUT /api/settings {"key":"kljuch_kotorogo_net"}` даёт `HTTP 400 {"error":"unknown setting key"}`. Это ровно
сёрловское «X считается Y в контексте C»: значение становится настройкой не потому, что его записали, а
потому, что оно прошло определение.

**Записи о применении правила нет.** Тело `save` — один `UPDATE system_settings SET "value" = ?,
updated_at = CURRENT_TIMESTAMP`, и при нуле затронутых строк `INSERT`. Ни строки в журнал, ни записи о том,
**кто** сменил, **откуда** и **каково было прежнее значение**. Единственный след — `updated_at`, то есть
**время без действующего лица**.

*Опыт, поставленный вместо рассуждения.* Записал настройке `project_event_log_enabled` её же значение:
`HTTP 200`, журнал не вырос **ни на строку**. Две контрольные пробы, чтобы ноль не оказался неисправностью
прибора: неизвестный ключ отвергается `400` — путь исполняется и различает; журнал за 70 секунд вырос на 16
строк — прибор видит записи, и ни одна из них не про настройки.

*Что чинить, и это меньше, чем «завести регистр».* Правило строить не нужно, оно есть. Нужна **запись о его
применении**: ключ, прежнее и новое значение, время, источник запроса. Одна строка в журнал дефектов или в
`project_event_log` закрывает половину, которой недостаёт, и делает выполнимым пункт 21.

*Опровержение, назначенное вперёд:* сменить настройку и найти запись, называющую действующее лицо. Найдётся —
форма становится сильной.

**`ProjectTreeService`** — «живое дерево»: **только новая проекция уже существующих вычислений**, без единого
нового.
*Связи:* вызывающих нет — от контроллера; зовёт 7 механизмов.
*Ценность:* вид, который считает своё, становится вторым источником истины — помехой по определению.
*Комментарий:* **периферия; образцовое самоограничение.**
*Философия:* `SUPERVENIENCE_WATCH` (D013) — **сильная**. Опровержение: найти в нём собственное вычисление.

**`IdleProjectAdviceService`**, **`RoleAdviceLoopService`** — советы при простое.
*Связи:* вызывающих нет.
*Ценность:* проект, стоящий без работы, получает названную причину и предложение, а не молчание.
*Комментарий:* **периферия.** Вопрос о читателе открыт: совет, который никто не исполняет, — муда, как бы верен
он ни был.
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — **не мерено**. Опровержение: назвать действие, изменённое советом.

**`RoleCapabilityLoader`**, **`RoleRulesParser`**, **`JulesRoleCapabilities`**, **`TaskTitleBuilder`** —
загрузка уставов, разбор правил, канонические возможности ролей, заголовки.
*Связи:* `TaskTitleBuilder` вызывают 7 механизмов.
*Ценность:* роль без загруженного устава не знает своих обязанностей, а заголовок сверх меры внешняя система отвергает.
*Комментарий:* **ядро** — `JulesRoleCapabilities` решает, что роль вообще умеет. `TaskTitleBuilder` мелочь, но
показательная: ограничение внешней системы вынесено в одну точку, а не размазано по вызовам.
*Философия:* `RIGID_API_REFERENT` (D003) — **сильная**. Опровержение: найти второе место, режущее заголовок.

**`ProjectAuditPipelineService`** — конвейер аудита, свёрнутый с пяти стадий до двух.
*Связи:* вызывает общий тик; зовёт 4.
*Ценность:* две снятые стадии были чистыми «записать и пойти дальше» — они **ничего не проверяли**.
*Комментарий:* **периферия; лучший пример вычистки муды в коде.** Самая опасная разновидность муды: стадия
существует, лог пишется, отчёт зелен, работы нет. Третья снятая была бы **помехой**: настоящий философский трек
идёт своим кроном, и вторая точка отправки конкурировала бы с ним за один предмет.
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — **сильная**. Опровержение: найти стадию без проверки.

---

# XIIа. Управляющая поверхность: чем поток останавливают снаружи

Этот раздел появился после замера, а не из полноты: 5 сентября поток стоял семь часов, и держал его
**контроллер**, а не сервис. Демаркация всего файла — вопрос «может ли механизм удержать поток», и
изменяющий эндпоинт на него отвечает «да». Значит он механизм. Ниже — 21 контроллер с изменяющими методами,
разобраны те, чей вызов способен остановить или отпустить поток.

**Общее для всех.** Аутентификации нет ни у одного: в `pom.xml` нет `spring-boot-starter-security`, в
`src/main` нет ни одного `SecurityFilterChain`, порт 8080 опубликован как `0.0.0.0`, фаервола на хосте нет.
`GET /api/accounts` с внешнего адреса отвечает `200`. Журнала вызовов не ведёт ни один из них. Поэтому у
каждой записи ниже подразумевается одно и то же *Ценность*-следствие: **механизм есть, ответственного нет**.

**`AccountController`** — `POST/PATCH/DELETE /api/accounts/{id}`: единственное место во всём `src/main`, где
пишется `enabled`, ключ Jules и статус аккаунта.
*Связи:* пишет `accounts` | читают его запись `lockNextJulesAccountWithCapacity`, `lockAccountByNameWithCapacity`, `AccountHealthService`
*Ценность:* без него аккаунт нельзя завести и починить; с ним, открытым наружу, поток останавливается одним вызовом
*Комментарий:* **ядро**. Доказано опытом, а не рассуждением: 13:08–20:45, пять аккаунтов, семь часов простоя. Обратного хода нет — `setEnabled(true)` не вызывает ни один механизм (предписание 23)
*Философия:* `BOUNDARY_TOPOLOGY` (D006) — форма: **не мерено, границы нет**; опровержение: закрыть контур и повторить вызов снаружи

**`ProjectController`** — `POST /api/projects/{id}/pause`, `/activate`, `/accept`, `/reset-for-redecomposition`.
*Связи:* пишет `projects.status` | читает `ContinuousOrchestrationService` перед каждым тактом
*Ценность:* приём и остановка проекта как решение человека, а не как следствие сбоя
*Комментарий:* **ядро**. `pause` останавливает весь такт оркестрации; `reset-for-redecomposition` возвращает проект к разложению, то есть отменяет уже сделанную компиляцию
*Философия:* `INSTITUTIONAL_FACT_REGISTER` (D007) — форма: **слабая**; статус проекта есть институциональный факт, а кто его установил, нигде не записано

**`ClaimController`** — `POST /api/tasks/{id}/fail`, `/complete`, `/close-failed`, `/release`.
*Связи:* пишет `tasks.status`, `claims` | читают гейт, слияние, свидетельство доставки
*Ценность:* внешний агент сообщает исход своей работы
*Комментарий:* **ядро**. `complete` и `close-failed` ставят необратимый статус (закон 20), и ставят его **по слову вызывающего**, без доказательства. Заслон необратимости стоит внутри, но предмет доверия — снаружи
*Философия:* `CONSTRUCTIVE_PROOF_OBJECT` (D007) — форма: **слабая**; терминальный исход обязан нести предъявляемое доказательство, а не утверждение о нём

**`WishlistController`** — `DELETE /api/wishlist/{id}`, `DELETE /api/wishlist/ghosts`, `PATCH /{id}/dismiss`.
*Связи:* пишет `wishlist` | читают компилятор, аудит покрытия, свод доставки
*Ценность:* убрать требование, которое перестало быть требованием
*Комментарий:* **ядро**. Жёсткое удаление требования меняет **знаменатель** доставки: «сдано 21 из 24» превращается в «21 из 23» без единой строки в журнале. Это единственный известный способ улучшить показатель, ничего не сделав
*Философия:* `LEVEL_OF_ABSTRACTION_LOCK` (D010) — форма: **не мерено**; удаление предмета и удаление записи о предмете здесь неразличимы

**`SettingsController` + `SystemStatusController`** — `PUT /api/settings` и `POST /api/system-status/sql`.
*Связи:* `PUT /api/settings` пишет `system_settings` | `effectiveBoolean` читает оттуда же гейт эндпоинта `/sql`
*Ценность:* отладочный доступ к базе, намеренно выключенный по умолчанию
*Комментарий:* **ядро, и здесь замер самый жёсткий.** `/sql` защищён не паролем, а **настройкой**
`debug_sql_endpoint_enabled`. Эта настройка входит в `DEFINITIONS`, значит `isKnownKey` для неё истинна,
значит её меняет `PUT /api/settings` — тот самый, у которого аутентификации нет. То есть замок и ключ лежат
за одной и той же незапертой дверью: два анонимных вызова подряд открывают произвольный SQL к живой базе,
включая изменяющий (`jdbcTemplate.update`). Комментарий в коде утверждает, что эндпоинт «никогда не может
быть живым там, где его не включили намеренно», — **это сообщение, утверждающее больше, чем делает
механизм**
*Философия:* `PRINCIPLED_INTEGRITY` (D012) — форма: **нарушена**; охрана, отпираемая охраняемым интерфейсом, не есть охрана

**`InternalTaskController`** — `PATCH /internal/tasks/{id}`.
*Связи:* пишет `tasks` | читают все механизмы потока
*Ценность:* ручная правка задачи, когда автомат ошибся
*Комментарий:* **ядро**. Правит задачу в обход гейтов, которыми задача обычно проходит
*Философия:* `PART_WHOLE_OWNERSHIP` (D004) — форма: **слабая**; у `tasks` пятнадцать пишущих, и этот шестнадцатый не объявлен нигде



*Живое, 9 сентября 2026, Codex: 10-такт 9/10 — dashboard/accounts/API edge cluster, без правки кода.* Девятый кластер десятиактного прохода заполнен как один API-edge/account-task boundary mechanism: `DashboardController` показывает account/task-operational state, `AccountController` читает и меняет account pool, `InternalTaskController` открывает служебную правку и дамп задач, `LinearSyncController` считает completeness of Linear metadata. Это нельзя чинить как пять разрозненных `findAll()`: эти входы являются границей между оператором/UI/скриптом и владельцами истины `accounts`, `tasks`, `claims` and Linear metadata.

*Идеальная форма кластера:* an API edge never becomes a private owner of truth. Read endpoints either return aggregate summaries, exact lookups or bounded/paged diagnostic lists; write endpoints are authorized, audited and delegate to the same owner semantics as automated flow; account API masks secrets and cannot silently break dispatch capacity; task API cannot expose all project bodies as a monitoring shortcut; Linear reports separate sync completeness from task delivery truth. Every response names scope (`factory`, `project`, `account`, `task`, `linear issue`) and every changing request has authority, before/after witness and a refusal path.

*Граница и взаимодействия:* upstream owners are `AccountRepository`, `TaskRepository`, `ClaimRepository`, `LinearIssueMetadataRepository`, `BottleneckDetectionService`, `TaskWaitTimeService`, `SystemStatusService` and the dispatch/account-health mechanisms that interpret account status/capacity. Downstream readers include admin dashboard, metrics/system-status screens, synchronization scripts, manual recovery workflows, dispatch/account-capacity decisions and the operator. The controllers may translate and expose state; they must not invent capacity language, bypass task lifecycle invariants, leak secrets, or turn a raw entity dump into a stable public contract.

*Инварианты:* (1) account secrets are writeable but never returned raw; (2) account status/enabled/maxConcurrentSessions changes are authority-bearing because dispatch reads them; (3) agent dashboard uses the same active-claim meaning as `ClaimRepository`; (4) raw task bodies are never the repeated status-monitoring path; (5) exact task lookup by Linear id must define duplicate behavior; (6) Linear completeness is about sync metadata, not delivery readiness; (7) internal/manual endpoints require executable protection rather than javadoc promises; (8) summary counts and detail lists are kept as different API contracts.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `DashboardController.java:45` in `/api/dashboard/agents` | `AccountRepository` account pool plus `ClaimRepository` active claims | dashboard polling/admin UI | `account pool dashboard list` | Conditionally allowed only if account pool is declared bounded and endpoint is secured; otherwise replacement-needed by projection/batched active claims. Refutation: dashboard reads secrets/full account entities or performs one claim query per account while an account-summary owner already exists. Closure: one secured account-pool DTO/projection returns masked account facts and active claim summaries with bounded cardinality and no capacity-language drift. |
| `AccountController.java:37` in `GET /api/accounts` | `AccountRepository` / accounts | admin account table; same controller writes create/update/status/heartbeat/delete | `account administration list` | Not ideal until rights/audit boundary is executable. Full account list may be acceptable as small account-pool reference data, but the same surface can change `enabled`, `status`, api key and `maxConcurrentSessions`. Refutation: unauthenticated caller lists or mutates account state, or list/update leaks raw api keys. Closure: endpoint is authorized/audited, returns DTO only, account-pool cardinality is bounded/declared, and write semantics match dispatch/account-health ownership. |
| `InternalTaskController.java:50` in `GET /internal/tasks` | `TaskRepository` / tasks | internal sync/manual diagnostic | `unbounded raw task dump` | Replacement-needed. Existing `/status-counts` proves repeated monitoring needs counts, not full task bodies. Refutation: external/internal caller can fetch every task of every project as raw entities, reproducing the documented large dump/OOM path. Closure: endpoint is removed, secured emergency-only, or paged/project-scoped; repeated status checks use counts/projections. |
| `InternalTaskController.java:148` in `/internal/tasks/by-linear-id/{linearIssueId}` | `TaskRepository` task Linear id | exact sync lookup | `exact lookup disguised as scan` | Replacement-needed. The question is one Linear id, and `TaskRepository` already has Linear-id catalogue methods but no exact finder/duplicate rule. Refutation: exact lookup materializes all tasks or silently picks one when duplicate Linear ids exist. Closure: repository exact lookup plus duplicate policy/constraint returns 404, one task, or a named conflict without changing status/update semantics. |
| `LinearSyncController.java:31` in `/api/linear-sync/completeness-report` | `TaskRepository` tasks with Linear ids plus `LinearIssueMetadataRepository` | API report; frontend currently consumes Linear completeness through `SystemStatusService` | `sync completeness report` | Replacement-needed. The report needs tasks with nonblank Linear ids and their metadata, not all tasks and not N metadata queries. Existing `findByLinearIssueIdIsNotNull` is the first acquisition boundary; stronger closure is one projection/join. Refutation: report scans non-Linear tasks or calls metadata once per issue while claiming sync completeness. Closure: acquisition is linear-id scoped, metadata is batched/project-scoped if needed, and the report explicitly remains sync metadata, not delivery readiness. |

*Сильная форма:* API edges are a reliability and rights boundary: the acquisition process is reliable for the question being asked, and the caller's authority matches the mutation it requests. `RELIABILITY_CHAIN` supplies source/timestamp/freshness/validation; `RIGHTS_DUTIES_MATRIX` names who may read/write; `LEVEL_OF_ABSTRACTION_LOCK` keeps account capacity, task status, Linear sync and delivery readiness from being collapsed into one number; `ACP-061` requires precondition/command/postcondition before changing protected endpoints.

*Слабая форма:* the UI and scripts get useful answers, but several endpoints read global entities and some mutable/internal routes rely on comments or conventions for protection. A dashboard can be fast enough today while still teaching clients that raw account/task tables are the API, and a Linear sync report can look complete while measuring metadata presence rather than delivered value.

*Опровержение:* create a fixture with multiple accounts, secrets, enabled/offline/daily-limited states, active/released claims, tasks across two projects, carrier tasks, duplicate/nonblank/blank Linear ids and Linear metadata rows. The mechanism is false if account secrets leak, unauthenticated mutation succeeds, agent dashboard disagrees with active-claim owner, `/internal/tasks` returns all raw tasks as a normal path, by-linear-id scans all tasks or hides duplicates, or Linear completeness changes delivery readiness language.

*Критерий закрытия:* this record is complete for tact 9 when the five API-edge `.findAll()` acquisitions are named with owner, boundary, refutation and closure. The implementation becomes ideal only when account/dashboard lists are secured/projection-backed or bounded by declared account-pool size, internal task dump is removed/secured/paged, Linear exact and report acquisitions are repository-scoped with duplicate/batch semantics, and tests/probes prove no secret leak, no authority bypass and no task/account/Linear scope drift.

*Кандидат на будущую реализацию Codex:* after explicit code approval, `InternalTaskController.byLinearId` and `LinearSyncController.getCompletenessReport` are plausible first candidates because exact acquisition and report scope are clear. `GET /internal/tasks` and account mutation/list surfaces must be handled as an authorization/API-contract mechanism, not as a tiny query cleanup. `DashboardController.getAgents` needs a bounded/projection/active-claim batching fixture before code.

*Текущий статус:* not ideal. The API-edge record is explicit, but implementation still has five relevant full-table acquisitions, an unbounded raw task dump, exact Linear lookup by scan, unbatched Linear metadata reads, account/admin surfaces whose authority boundary is not proven here, and one dashboard account list with per-account active-claim queries.

*Свидетельства такта:* `grep -R "\.findAll()" -n src/main/java/com/eneik/production | grep -E "DashboardController|AccountController|InternalTaskController|LinearSyncController"`; `nl -ba` contexts for `DashboardController` lines 43-91, `AccountController` lines 35-155, `InternalTaskController` lines 48-199, `LinearSyncController` lines 29-90; repository reads for `TaskRepository`, `AccountRepository`, `ClaimRepository`, `LinearIssueMetadataRepository`; DTO reads for `AccountDto` and `AgentDashboardDto`; `SystemStatusService` Linear/account/task context lines; frontend/test grep for `/api/accounts`, `/api/system-status`, `/api/linear-sync`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`; `grep -n "RELIABILITY_CHAIN\|RIGHTS_DUTIES_MATRIX\|LEVEL_OF_ABSTRACTION_LOCK" docs/philosopher-patterns/03_PATTERN_STRENGTH.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `DashboardController`, `AccountController`, `InternalTaskController` or `LinearSyncController` как отдельные query-cleanups: сначала сохрани один API-edge/account-task contract — secured mutable account/task routes, masked account DTOs, bounded dashboard/admin lists, exact Linear-id lookup with duplicate policy, Linear completeness as sync metadata not delivery truth, and no raw all-task dump as monitoring API — then prove by fixture that account capacity, active claims, task identity and Linear reports keep their meaning. Следующий такт 10/10 — completion audit cluster: re-run the full `.findAll()` inventory, reconcile against `docs/FACTORY_MECHANISMS.md`, `docs/reports/AGY_NEXT.md`, and `docs/reports/AGY_ASKS.md`, and explicitly report whether any mechanism was missed. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для mutable API additionally hold `RIGHTS_DUTIES_MATRIX` and `LEVEL_OF_ABSTRACTION_LOCK`.*

**`JulesSessionController`** — `POST /api/jules-sessions/dispatch`, `/{id}/cancel`, `/dispatch-to-branch`.
*Связи:* пишет `jules_sessions` | читает `lockNextJulesAccountWithCapacity` при счёте занятости
*Ценность:* отправить или снять сессию вручную
*Комментарий:* **ядро по расходу**: ручная отправка тратит ту же внешнюю квоту, что и автоматическая, и в бюджете закона 9 не отличается от неё
*Философия:* `TELEOSEMANTIC_FEEDBACK` (D011) — форма: **слабая**; расход виден, источник расхода — нет

**`GithubWebhookController`** — `POST /api/webhooks/github`.
*Связи:* принимает событие GitHub | пишет через `GitHubPullRequestService`
*Ценность:* узнавать об изменении PR не опросом, а событием — прямая экономия бюджета закона 13
*Комментарий:* **периферия по решению, ядро по доверию**: подпись вебхука в коде не проверяется, значит событие о слиянии PR может прислать кто угодно
*Философия:* `SUBSTITUTION_ORACLE` (D009) — форма: **не мерено**; подделанное событие неотличимо от настоящего

**Остальные изменяющие поверхности** (`InternalRepairController`, `TocSentinelController`,
`KaizenController`, `JulesConfigController`, `GoogleAiResourceController`, `MarketResearchController`,
`InternalSettingsController`, `GithubAccessController`, `OperationalFlowCoreController`,
`FlowSpineController`, `InternalGeminiObserverController`, `GreetingController`) поток не держат: они пишут
наблюдения, справочники и снимки. Названы здесь, чтобы перечень был полным, а не чтобы казаться полным.

**Вывод раздела.** Опасность здесь не в отдельном эндпоинте, а в том, что **у фабрики нет внутреннего и
внешнего**. Все двадцать один изменяющий контроллер — одна поверхность без границы, и потому вопрос «кто
сделал» ни по одному из них не имеет ответа. Чинится это не по одному эндпоинту, а одним движением на
границе — предписание 24.

---

# XIII. Взаимодействие: кто пишет каждое состояние

Разделы выше судят механизмы по одному. Идеальный механизм при неидеальном взаимодействии бесполезен, и
корпус называет это прямо — `PART_WHOLE_OWNERSHIP` (D004):

> Сделать владение частями и инварианты целого **явными до** разделения модулей, таблиц или сервисов.
> Обязательство: показать, **какой агрегат вправе менять каждую часть**.

Обязательство здесь не «пусть пишет один», а «пусть будет **названо**, кто вправе». Много писателей само по
себе не дефект; дефект — когда не сказано, кто из них хозяин.

**Замер по всем 46 хранилищам состояния.** Метод: для каждого репозитория ищется вызов изменяющего метода
(`save`, `delete`, `compareAndSet…`, `mark…`, `reset…` и подобных) из любого класса `src/main`, кроме него
самого, с исключением строк `import`. Метод даёт ложные срабатывания на совпадении имён и не видит записи
через чужую обёртку — то есть числа ниже это **нижняя граница**, а не точная величина.

| Категория | Хранилищ |
|---|---|
| Только чтение, писателей нет | 17 |
| Один писатель — владение де-факто единственно | 12 |
| **Два и более писателей** | **17** |

**Владение объявлено словами лишь в трёх файлах** из всего кода: `SessionLifecycleService`,
`AccountHealthService`, `AccountEntity`. То есть из 29 записываемых хранилищ хозяин назван у трёх.

### Три самых спорных состояния

**`WishlistRepository` — 20 писателей.** `AutoMergeService`, `DeliveredWorkJudgmentService`,
`DeliveryRealityProducerService`, `DesignShopOrchestrationService`, `DesignSystemFalsificationService`,
`FalsificationCycleService`, `FeatureService`, `GeminiObserverActionService`,
`InternalGeminiObserverController`, `InternalRepairController`, `JulesDispatchService`, `KaizenService`,
`LaunchabilityConstraintService`, `OpsAuditorService`, `PlannedWorkRecoveryService`,
`ProductLaunchabilityService`, `ProjectFlowService`, `StrandedFinalizingSweepService`,
`TechnicalLeadCompiler`, `WishlistService`.
*Это не теория.* Взаимная блокировка, из-за которой фабрика стояла сегодня, была спором ровно двух из этих
двадцати: восстановление возвращало заявку в `pending`, а завершение умело брать её только из `compiling`.
Ни то, ни другое не было ошибкой само по себе — **не было названо, кто из них хозяин перехода**.

**`TaskRepository` — 15 писателей.** Второй сегодняшний круг жил здесь: уборка помечала задачу компилятора
выполненной, а отправка тут же создавала новую. Снова оба действия по отдельности осмысленны.

**`JulesSessionRepository` — 7 писателей**, включая два контроллера. `SessionLifecycleService` объявляет себя
единственным владельцем вопроса «знает ли Jules, что сессия закончена» — и это правильная форма, — но
остальные поля строки сессии пишут ещё шестеро.

### Что отсюда следует

Пока хозяин не назван, всякий новый механизм, пишущий заявки или задачи, **добавляет ребро в граф, которого
никто не проверяет**. Оба сегодняшних круга — не случайность и не небрежность конкретного автора: это то,
что происходит с состоянием, у которого двадцать писателей и ни одного объявленного владельца.

Отсюда же видно, почему разделение `ProjectFlowService` и `JulesDispatchService` нельзя начинать с
разрезания по размеру. Образец требует объявить владение **до** разделения; разрезать класс, у которого
владение не названо, значит превратить внутренние вызовы в межсервисные гонки и получить тот же спор,
только дороже.

**Порядок работы, вытекающий из замера, а не из вкуса:**
1. Назвать хозяина для `WishlistRepository` — 20 писателей и два измеренных инцидента.
2. Назвать хозяина для `TaskRepository` — 15 писателей и один измеренный инцидент.
3. Только после этого разделять большие классы ядра.

Продуктовый код по этому замеру не правился.

---

---

# XIV. Сверка с корпусом философских образцов

Всё выше — суд по модели. Здесь тот же разбор ведётся по **собственному корпусу фабрики**:
`docs/philosopher-patterns` — 86 философов, **1720 образцов**, у каждого назван предотвращаемый дефект из
таксономии D001–D015 и обязательство доказательства. Корпус читается прямо, а не через RAG-выборку: цитируя
образец, я обязан назвать его идентификатор, чтобы утверждение можно было проверить.

Ценность этой сверки в том, что корпус даёт **чужой словарь**. Когда собственная модель и внешняя
таксономия называют один и тот же дефект независимо — это не совпадение слов, а подтверждение по широте.

### D002 — Invalid state · образец `*_CATEGORY_ERROR_SCAN`

> Отвергать код, который принимает процесс за объект, наблюдение за полномочие, а политику за данные без
> переходника. Обязательство: указать тип, схему или переходник, удерживающий границу рода.

Прямое имя того, что оператор назвал главной опасностью. **Стерегут:** `PlatformSelfReferenceDetector`
(заводская находка не есть требование клиента), `CodeChangeClassifier` (процессный файл не есть код),
`EmsFlowStage` и `KanoClass` (одно правило — один дом).
**Нарушали, замерено сегодня:** уборка мета-задач в `PlannedWorkRecoveryService` принимала **статус задачи за
свидетельство доставки**; `AccountHealthService` принимал **неопознанный отказ за отсутствие события**.
Оба закрыты, оба заслонены.

### D004 — Concurrency conflict · образец `*_PART_WHOLE_OWNERSHIP`

> Сделать владение частями явным до разделения модулей, таблиц или сервисов. Обязательство: показать, какой
> агрегат вправе менять каждую часть.

Точное имя того, что здесь названо **помехой**. **Стерегут:** `AccountHealthService` (единственный владелец
здоровья аккаунта), `SessionLifecycleService` (единственный владелец вопроса о чужом состоянии),
`ClaimService`, `SystemSettingsService`, `ContinuousOrchestrationService` (единственный распорядитель
очерёдности).
**Нарушалось:** четыре писателя статуса аккаунта; два парсера класса Кано; три `switch` о порядке стадий;
восстановление и завершение, разошедшиеся в том, из какого состояния брать заявку.
**Замечание:** самые большие классы — `ProjectFlowService` (6809 строк) и `JulesDispatchService` (5752) —
владения не объявляют вовсе. Образец требует объявить его **до** разделения; здесь оно не объявлено и не
разделено, и потому всякая правка ядра рискует стать помехой.

### D007 — Evidence gap · образцы `*_CONSTRUCTIVE_PROOF_OBJECT`, `*_INSTITUTIONAL_FACT_REGISTER`

> Успешное завершение представлять значением, несущим свидетельство для следующего шага. — И: статусы вроде
> «одобрено», «слито», «готово» считать институциональными фактами, подкреплёнными правилом. Обязательство:
> показать правило, создающее статус, и запись аудита.

**Стерегут:** `ClientDeliverableReadinessService` (статус эпика создаётся правилом о слияниях, а не
объявлением), `CriteriaEvidenceSelector` (пропущенное названо), `Judgement` (для всего, кроме «разрешаю»,
причина обязательна: *отказ, который человек не может проверить, есть обвинение, а не свидетельство*),
`DeliveryRealityProducerService`.
**Нарушалось сегодня:** сообщение уборки утверждало «продукт готов на 100% и слит в main», не имея под собой
ни одного факта о доставке. По образцу это ровно отсутствие правила, создающего статус.

### D008 — False green · образец `*_FALSIFICATION_HARNESS`

> Написать проверку, которая опровергла бы утверждение агента, **прежде чем** утверждение принято.

Это в точности то, чем я занят каждый такт, и корпус называет это раньше меня. **Стерегут:**
`FalsificationCycleService`, `FactoryJudgmentService`, счётный инвариант мест слияния.
**Нарушалось:** `DesignExcellenceGate` читал поле, которого никто в продакшене не писал, — зелен всегда;
заслон закона 14 утверждал сверх своего закона и **удерживал слепоту**. Отсюда же моё сегодняшнее правило:
прежде чем чинить продукт по красному структурному тесту, проверь, что тест смотрит туда, куда думает —
фантом стоит столько же, сколько пропуск.

### D009 — Substitution failure · образец `*_SUBSTITUTION_ORACLE`

> Прежде чем заменить код, зависимость, модель или схему, доказать сохранение под значимыми наблюдениями.

**Стережёт:** `ClientDeliverableReadinessService` — слияние от посторонней задачи в том же эпике пункт не
закрывает. Это отказ от подстановки в самой строгой форме.
**Стоит открытым:** отношение 427 сделанных задач к 20 сдачам. Подстановка `сделано → доставлено` не
совершается механизмами, но продолжает жить в **числах, которыми фабрика себя описывает**, и `FlowSpineService`
честно помечает это предупреждением `done_is_not_delivery`.

### D012 — Policy contradiction · образцы `*_TRUTH_STATUS_TABLE`, `*_PRINCIPLED_INTEGRITY`

> Представлять истинное, ложное, неизвестное и противоречивое явно в статусе оркестрации. — И: отвергать
> местные починки, удовлетворяющие букве правила и нарушающие объявленный принцип системы.

Трёхзначность, к которой я приходил сегодня трижды, в корпусе стоит как отдельный образец, повторённый у
десятков философов. **Стерегут:** `Verdict` (три значения в типе), `LeverAgreement` (четыре, по Белнапу),
`SixSigmaVerdictLayer` (воздержание вместо числа).
**Нарушалось:** `JulesApiClient` до сегодня (`UNKNOWN` проходил как `VISIBLE`), `AccountHealthService` до
сегодня. Оба теперь несут третий исход явно.
Второй образец — про букву против принципа — описывает мою же ошибку прошлого такта: заслон удовлетворял
букве закона 14 и нарушал его принцип.

### D013 — Runtime drift · образец `*_ANTI_MIRROR_TELEMETRY`

> Предпочитать операционную телеметрию внутреннему рассказу агента о том, что делает система. Обязательство:
> сослаться на логи, метрики, проверки здоровья или состояние дашборда для **действительного** рантайма.

**Стерегут:** `FlowSpineService`, `SystemProgressTracker`, `AiHealthTracker`,
`ClientRuntimeObservabilityService`, `FactorySelfHealthService`.
**Нарушалось:** `RuntimeVerdictLayer` держал сохранённое «упало» как текущий факт. И — **мной**: за смену я
трижды выдал вывод за замер (застрявшие в `finalizing` заявки, причина отказов Jules, отсутствие счётчика
квоты). Каждый раз опровержение приходило одним запросом к живой системе. Образец адресован агенту прямо, и
я — тот самый агент.

### D014 — RAG hallucination

Относится к этому файлу в первую очередь. Поэтому каждое имя класса здесь сверено с исходниками
программно, а каждый образец корпуса цитируется с идентификатором. **Утверждение без проверяемой ссылки в
таком документе есть не описание, а рассказ о нём.**

### Чего корпус требует, а фабрика ещё не делает

* **`*_PART_WHOLE_OWNERSHIP`** — объявить владение частями **до** разделения. Два шеститысячных класса ядра
  владения не объявляют; это самая крупная невыполненная обязанность перед корпусом.
* **`*_CONSTRUCTIVE_PROOF_OBJECT`** — успешное завершение как значение, несущее свидетельство. Сегодня
  «готово» — это статус, а не носитель доказательства; отсюда и вся история с `done ≠ delivered`.
* **`*_INUS_FACTOR_CHECK`** — считать подозреваемую причину одним фактором достаточного набора, пока
  альтернативы не исключены. Это Таггард в формулировке корпуса, и это правило я нарушал чаще всего.

---

---

# XV. Класс дефекта «тихое размножение задач»: подпись, замер, случаи

Записано по указанию оператора, чтобы этот класс можно было **находить и предупреждать**, а не узнавать о нём
по исчерпанной квоте.

### Подпись

Механизм порождает задачи с одним и тем же содержанием, каждая отправляется, тратит сессию Jules и
**успешно завершается**. Внешне всё исправно: задачи идут, статусы `done`, дашборд спокоен. Расходуется
только внешний бюджет, а он не наш и потому невидим.

Дефект по корпусу — `CATEGORY_ERROR_SCAN` (D002): детектор измеряет «сколько дубликатов **застряло сейчас**»,
а называется и используется как «порождаются ли дубликаты». Потраченная сессия потрачена независимо от того,
чем кончилась задача.

Три признака, по которым класс узнаётся:

1. Число задач с одним ключом содержания много больше единицы, а **почти все терминальны**.
2. Детектор дубликатов молчит — по устройству, а не по настройке: он исключает терминальные и требует три
   одновременно живых.
3. Отказы внешней системы приходят **позже** размножения, иногда на часы, и выглядят как её сбой.

### Замер — воспроизводимый, снимается в любой момент

```
curl -s "http://localhost:8080/internal/tasks?projectId=<PROJECT_ID>" | python3 -c "
import sys,json,collections
rows=json.load(sys.stdin)
TERM={'done','failed','blocked','spike_completed'}
c=collections.Counter((t.get('title') or '') for t in rows)
for title,n in c.most_common():
    if n<3: break
    ts=sorted(t.get('createdAt','') for t in rows if (t.get('title') or '')==title)
    live=sum(1 for t in rows if (t.get('title') or '')==title and str(t.get('status')) not in TERM)
    print('%4d задач | живых %d | %s .. %s | %s'%(n,live,ts[0][:19],ts[-1][:19],title[:60]))
"
```

**Дату не отбрасывать.** Первая редакция этого замера печатала только время, `ts[0][11:19]`, и окно
«16:14 → 17:32» августовских задач читалось как сегодняшнее — я на этом поднял ложную тревогу о третьем
генераторе через час после того, как сам этот раздел написал. Срез `[:19]` выше даёт дату и время; менять
его нельзя. Прибор, теряющий измерение, которое различает случаи, показывает совпадение там, где его нет.

Второй срез, обязательный при вопросе «идёт ли это **сейчас**»: то же самое, но по задачам, созданным за
последние сутки. Ключ с тремя повторами за месяц и ключ с тремя повторами за десять минут — разные предметы,
и общий счёт их не различает.

Читать так: **много задач при малом числе живых** и есть подпись. Окно первая-последняя даёт темп и, что
важнее, **момент остановки** — по нему находится правка, которая генератор закрыла.

### Случаи, измеренные 5 сентября 2026

| Ключ | Задач | Окно | Темп | Чем закрыт |
|---|---|---|---|---|
| `c7fc10ad` | 33 | 01:05 → 09:47 | ~4 в час | Размыканием взаимной блокировки компиляции: восстановление возвращало заявку в `pending`, а завершение умело брать её только из `compiling`. |
| `71db39a2` | 5 | 13:05 → 13:15 | ~30 в час | Тем же размыканием; короткая вспышка на другой заявке. |
| `b1c27ea9` | 31 | 14:21 → 15:19 | ~21 в час | Правкой уборки «осиротевших» мета-задач: она считала продукт готовым по статусам задач и сносила носителя компиляции, после чего сторож не видел активного и создавался новый. |

Полный счёт за сутки: **создано 92 задачи, из них 69 — дубликаты четырёх предметов, то есть три четверти
всей дневной выработки**. Последний дубликат создан в 15:19:27; за последующие часы — ни одного.

Итого **69 задач на четыре единицы работы** — то есть до шестидесяти четырёх сессий Jules там, где нужно было
две. Этим и была выбрана суточная квота; отказы Jules, выглядевшие как внешний сбой, — следствие, а не
причина.

Последний дубликат `b1c27ea9` создан в 15:19:27, правка развёрнута в 15:19:35. Совпадение до секунд — это и
есть доказательство, что закрыт именно этот генератор, а не совпало по времени.

### Кто именно это устроил

Вопрос оператора, и ответ проверен по коду, а не по памяти.

**Сбрасывает состояние — `PlannedWorkRecoveryService`, оба раза.** Возврат заявки в `pending` —
`PlannedWorkRecoveryService:456`; снос мета-задачи компилятора как осиротевшей —
`PlannedWorkRecoveryService:522`.

**Создаёт задачи — `ProjectFlowService:3478`.**

Круг: восстановление сбрасывает состояние → отправка честно видит «активной задачи нет, заявки ждут» и
создаёт новую → новая не может завершиться → восстановление снова видит застрявшее → сбрасывает.

**Ни один из двух не ошибается в своих границах.** Восстановление обязано поднимать застрявшее; отправка
обязана компилировать ждущие заявки. Ошибка не в механизме, а в том, что **у состояния нет хозяина** и
потому никто не отвечает за переход целиком.

**Гарантия, которая истинна и бесполезна.** Javadoc восстановления обещает дословно: *«…и никогда не создаёт
задачу, заявку, ветку или сессию»*. Обещание сдержано буквально — он не создал ни одной. И при этом из-за
него создано шестьдесят четыре.

Гарантия сформулирована **о собственных действиях, а не о следствиях**. Это категориальная ошибка в самой
гарантии (`CATEGORY_ERROR_SCAN`, D002): утверждение о том, что делают руки механизма, подано как утверждение
о поведении системы. Отсюда правило, приложимое ко всякому механизму фабрики:

    гарантия вида «я не делаю X» ничего не обещает о том, будет ли сделано X
    обещание имеет силу, только если высказано о состоянии системы после действия

Проверка для всякой такой гарантии: назвать механизм, который сделает X **вследствие** моего действия. Если
такой есть — гарантия описывает не систему, а мои руки.

### Почему класс возвращается

Оба генератора устроены одинаково и **ни один из двух механизмов в паре не был неправ сам по себе**.
Восстановление законно возвращало заявку. Завершение законно требовало своё состояние. Уборка законно
сносила осиротевшее. Спор возник потому, что **у состояния не назван хозяин**: в заявки пишут двадцать
механизмов, в задачи пятнадцать (раздел XIV). Пока это так, третий генератор — вопрос времени, а не
возможности.

Отсюда порядок предупреждения, а не лечения:

* **Причина** — пункты 1 и 2 предписаний: назвать хозяина заявок и задач. Это единственное, что делает
  появление новой пары невозможным, а не редким.
* **Прибор** — пункт 19: считать **темп порождения** дубликатов, включая завершённые, и писать его в журнал
  дефектов. Блокировать им нельзя: блокировка по этому счёту уже давала неразрешимый тупик 4 августа.
* **Раннее наблюдение** — замер выше стоит снимать при всяком необъяснённом отказе внешней системы, **до**
  того как искать причину на её стороне. Сегодня я потратил половину смены на гипотезы о Jules, тогда как
  расход был наш.

---

# XVI. Предписания для L2: что и как чинить

## Указатель: 59 пунктов, сделан 1

Пункты 1–41 — прежние. Пункты **42–59 заведены 7 сентября 2026 как задачи для кодинга**, выведенные из
слабых форм образцов, найденных при описании механизмов. Оператора в системе нет: пункты, прежде
отложенные «на решение человека», либо переведены в задачи (59), либо сняты как относящиеся к истории —
перечень снятого приведён в конце раздела.

Куча предписаний — это запас, а не польза; запас, который никто не берёт, есть перепроизводство. Поэтому
здесь очередь, из которой можно тянуть по одному, а не список, который читают целиком.
**Замер 2026-09-06:** написано 41, помечено сделанным 1 (пункт 41). Коммитов за смену 138, из них тронувших
`src/main` — 20.

**Первыми — то, что стоит потока или денег прямо сейчас** (помечено `!` в разборах):
24 пульт открыт в интернет без пароля · 27 «internal» принято за полномочие · 25 сорок три вопроса в час с
известным ответом · 22 резервирование опустошает общий пул · 23 выключенный аккаунт без пути назад ·
19 детектор дубликатов слеп к главному случаю · 20 отказ выбора аккаунта называет не свою причину ·
35 блокировка за секунды, освобождение раз в час.

**Тянущий контур — 38, 39, 40, 41** — одна болезнь; 41 сделан, остальные три открыты и связаны.

**Владение и размер — 1, 2, 3, 4** — сначала объявить хозяина, потом делить; порядок обязателен.

**Мёртвое — 5, 6, 7** — убрать или воскресить, третьего не дано.

**Суждение, которое не слышат — 8, 9, 10, 12** — механизмы считают верно, и результат никуда не идёт.

**Остальное по одному предмету:** 11, 13, 14, 15, 16, 17, 18, 21, 26, 28, 29, 30, 31, 32, 33, 34, 36, 37.

Как читать пункт: замер, цепь механизмов с ролью каждого, образцы корпуса с формой, что делать, заслон,
опровержение. Пункт снимается опровержением, а не мнением.


Кодирует Antigravity. Здесь — что делать, из какого образца это следует и чем будет доказано.

**Область.** Предписания даны **всему, что не в сильной форме**: девяти слабым механизмам, трём мёртвым и
открытым долгам модели. Девяноста механизмам в сильной форме не предписано
ничего намеренно: добавлять структуру там, где существующая объясняет наблюдение, запрещено куайновским
принципом наименьшего увечья, на котором стоит `SelfFalsificationEpicMatcher`.

**Как читать пункт.** *Образец* — из чего следует. *Замер* — что установлено, а не предположено. *Делать* —
что именно. *Заслон* — что тест обязан утверждать. *Опровержение* — наблюдение, которое покажет, что пункт
не сделан, как бы он ни был назван.

---

### 1. Хозяин заявок · `PART_WHOLE_OWNERSHIP` (D004) · **первый по цене**

*Замер:* в `WishlistRepository` пишут **20 механизмов**. Два измеренных инцидента за одну смену — взаимная
блокировка `pending`/`compiling` и круг восстановления.

*Делать:* назвать **один** сервис хозяином перехода состояния заявки. Остальные девятнадцать меняют статус
только через него; читать вправе все. Хозяином логично сделать `ProjectFlowService` — он уже держит приём и
компиляцию, — но выбор обязан быть **объявлен в коде**, а не выведен читателем.

*Заслон:* структурный, по образцу заслона закона 25a: во всём `src/main`, кроме объявленного хозяина, нет ни
одного вызова изменяющего метода `wishlistRepository`. Утверждение о **множестве** мест, а не список имён.

*Опровержение:* добавить двадцать первого писателя; если тест не покраснел — пункт не сделан.

---

### 2. Хозяин задач · `PART_WHOLE_OWNERSHIP` (D004)

*Замер:* в `TaskRepository` пишут **15 механизмов**. Один измеренный инцидент: уборка помечала задачу
выполненной, отправка создавала новую.

*Делать:* то же, что в пункте 1. Отдельно назвать, кто вправе ставить `done`: сегодня это делают пять путей,
и именно поэтому «сделано» перестало значить «доставлено».

*Заслон:* счётный инвариант над множеством мест, пишущих `TaskStatus.done`, с пинованным числом.

*Опровержение:* новый путь к `done` не ломает тест.

---

### 3. Разделение `ProjectFlowService` · `PART_WHOLE_OWNERSHIP` (D004)

*Замер:* 6809 строк; приём требования, компиляция, отправка и подсчёт блокеров в одном классе; критерий
Куайна нарушается почти каждым коммитом.

*Делать:* **не начинать с разрезания по размеру.** Образец требует объявить владение **до** разделения.
Порядок: сперва пункты 1 и 2, затем разделение по объявленным границам владения, а не по строкам.

*Заслон:* после разделения — тот же структурный тест, что в пункте 1, для каждой выделенной части.

*Опровержение:* найти поле, которое после разделения пишут два новых сервиса. Это будет означать, что
разрезали раньше, чем назвали.

---

### 4. Разделение `JulesDispatchService` · `PART_WHOLE_OWNERSHIP` (D004)

*Замер:* 5752 строки; почти все дефекты потока за смену жили здесь.

*Делать:* то же и в том же порядке. Естественная граница видна уже сейчас: транспорт (`JulesApiClient`) от
решения об отправке отделён верно — надо продолжить ту же линию и отделить **завершение** сессии от
**отправки**, потому что спорили между собой именно они.

*Опровержение:* то же, что в пункте 3.

---

### 5. `PlatformSelfReferenceDetector` мёртв · `CATEGORY_ERROR_SCAN` (D002)

*Перепроверено 2026-09-06 четырьмя признаками, потому что два моих прежних «вызывающих нет» оказались
ложными:* бином не является (аннотации нет), файлов, ссылающихся на него, — **0**, `@Scheduled` — **0**,
`@EventListener` — **0**, строк в журнале контейнера — **0**. Мёртв, утверждение подтверждено.

*Замер:* производственных читателей ноль, только собственный тест. Осиротел вместе с выведенным
наблюдателем. Случай, ради которого написан: **14 «эпиков» из 18 были заводским шумом в списке клиента**.

*Делать:* вернуть проверку в порождение заявок. Заявки сейчас порождают восемь механизмов; проверка
принадлежит **точке создания заявки**, а не каждому из восьми — иначе завтра появится девятый без неё.

*Заслон:* попытка создать заявку с текстом, самоочевидно относящимся к фабрике, отвергается на месте.
Утверждение о свойстве, не список слов.

*Опровержение:* создать заявку из заводской находки в обход и посмотреть, прошла ли.

---

### 6. `FlowMetricsService` не мёртв, а **создан и не позван** · `SUPERVENIENCE_WATCH` (D013)

*Перепроверка 2026-09-06 уточнила формулировку.* Он помечен **`@Service`**, то есть Spring создаёт его при
старте: он живой бин, занимающий память, у которого отработал конструктор. Но ссылающихся файлов — **0**,
`@Scheduled` — **0**, `@EventListener` — **0**, строк в журнале — **0**. «Мёртв» было неточно: мёртвый код не
загружается, а этот загружается и молчит. Разница важна для решения: удалять придётся бин, а не файл.

*Замер:* подключал только тот коммит, что создал. Сверка по закону Литтла не выполнялась **ни разу**.

*Делать:* подключить к общему тику `ContinuousOrchestrationService` — не заводить свой крон, как принято
всюду в этой фабрике. Расхождение `WIP ≈ Throughput × CycleTime` писать в журнал дефектов.

*Заслон:* при заведомо несогласованных величинах расхождение обнаружено и записано.

*Опровержение:* подать три величины, не сходящиеся по Литтлу, и посмотреть, промолчит ли фабрика.

---

### 7. `BaseQualityGate` мёртв · `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008)

*Образец.* Альфред Тарский, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины
(T-схема: «P» истинно ⟺ P), anchor *The Concept of Truth in Formalized Languages*. Сильная форма дословно:
«проверка, способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она
**краснеет при дефекте**». Слабая: «зелёный тест рядом с изменением». Опровержение образца: «снять правку и
прогнать тест; **не покраснел — это не заслон**».

*Почему именно он.* `BaseQualityGate` покраснеть не может ни при каком дефекте: он не бин, в
`List<GateCheck>` не попадает, за жизнь контейнера в журнале ноль строк. По Тарскому «P истинно» имеет смысл
только вместе с условием, при котором мы признали бы P ложным. У этого гейта такого условия нет вовсе, и
потому его существование даёт **видимость** проверки — хуже, чем её отсутствие.


*Перепроверено 2026-09-06:* реализует `GateCheck`, но **аннотации не имеет**, значит бином не является и в
`List<GateCheck>` оркестратора не попадает. Ссылающихся файлов — **0**, `@Scheduled` — **0**, строк в журнале
— **0**. Именно отсутствие аннотации и есть причина, по которой оркестратор его не зовёт; ср. пункт 33, где
`EpistemicLayerInvariantGate` аннотацию имеет и потому собирается.

*Замер:* он и вложенные `BusinessValueGate`, `DoDGate` упоминаются в `src/main` единственный раз — в
комментарии.

*Делать:* решить одно из двух и **записать решение**: либо подключить к `GateOrchestrator`, либо удалить.
Третьего не дано — мёртвый гейт с зелёным тестом опаснее отсутствующего, потому что создаёт видимость
проверки.

*Опровержение:* через месяц класс на месте и по-прежнему без читателя.

---

### 8. `DoctrineVerdictLayer` судит, и его не слышат · `TELEOSEMANTIC_FEEDBACK` (D011)

*Замер:* слой писал «отказано», две роли возражали прямо, семь мягко — а конвейер отправлял задачи и
рапортовал 82% готовности. Единственный потребитель решётки — HTTP-эндпоинт: её слышно, только если спросят.

*Делать:* дать вердикту решётки **читателя внутри автономного потока**. Не гейт — чтение. Самое дешёвое и
безопасное: `ContinuousOrchestrationService` на своём тике запрашивает сведение вердиктов и, при отказе
любого слоя, пишет запись в журнал дефектов с именем слоя и его причиной. Никаких новых полномочий: отказ
пока только **записывается**, а не блокирует.

*Заслон:* при отказе хотя бы одного слоя в журнале дефектов появляется запись с именем слоя и причиной.

*Опровержение:* привести слой к отказу и посмотреть, узнал ли об этом кто-нибудь кроме HTTP-запроса.

*Почему именно так:* образец требует, чтобы сигнал менял следующее действие. Запись в журнал — минимальное
изменение, которое уже делает сигнал слышимым, и оно обратимо. Ставить гейт сразу нельзя: `VerdictGate` сам
предупреждает, что гейт честен настолько, насколько честны его входы, а часть слоёв ещё должна воздержание
вместо числа.

---

### 9. `VerdictGate` ограничивает только сообщаемое · `PROHIBITION_AS_CODE` (D006)

*Замер:* он не трогает приёмку намеренно, и это верно; но за пределами отчёта он не запрещает ничего.

*Делать:* **ничего, пока не сделан пункт 8.** Записано здесь как названный, а не забытый долг: сначала слои
перестают быть глухими, потом обсуждается, что им позволено запрещать. Обратный порядок дал бы гейт на
непроверенных входах.

*Опровержение:* если кто-то расширит его полномочия до пункта 8 — это нарушение порядка, а не улучшение.

---

### 10. `TocSubordinationLever` знает верное и стоит в тени · `PRINCIPLED_INTEGRITY` (D012)

*Замер:* фабрика находит ограничение и кладёт его в общую очередь ждать. Рычаг это знает — так написано в
его собственном комментарии — и остаётся в `observe_only`.

*Делать:* провести его по существующей лестнице продвижения на **накопленном свидетельстве**, а не деплоем.
Механизм для этого уже есть — `LeverPromotionService`; нужно, чтобы рычаг записывал парные наблюдения
(что решил бы он, что решает нынешний порядок) и продвигался по реальному расхождению исходов.

*Заслон:* пара наблюдений записывается на каждом обороте; продвижение происходит только по накопленному
числу, и это проверяется.

*Опровержение:* найти продвижение, случившееся по времени или по деплою, а не по свидетельству.

---

### 11. `SystemStatusService` расходился с живым · `ANTI_MIRROR_TELEMETRY` (D013)

*Замер:* показывал `systemStatus: ok`, пока контур стоял намертво.

*Делать:* свод обязан выводить своё «ок» из тех же величин, что показывают застой, а не из отдельного
счёта. Практически: связать его с `SystemProgressTracker`, который пишется **только** при настоящем выходе —
успешной отправке или слиянии. Нет выхода в окне — «ок» невозможен по построению, а не по проверке.

*Заслон:* при отсутствии выхода в окне свод не может вернуть `ok`.

*Опровержение:* остановить поток искусственно и посмотреть, что показывает свод.

---

### 12. `BottleneckAwarePriorityService` переупорядочивает, но не подчиняет · `DECISION_EXPECTED_LOSS` (D005)

*Замер:* приоритет меняет очерёдность, но не заставляет неограничения простаивать.

*Делать:* **не расширять до подчинения здесь.** Подчинение — предмет пункта 10; двум механизмам одно
решение принимать нельзя, это прямой путь к спору, измеренному в разделе XIV. Оставить как есть и записать
границу: приоритет ранжирует, подчинение решает `TocSubordinationLever`.

*Опровержение:* найти в приоритете решение простаивать — это будет второй хозяин одного вопроса.

---

### 13. `SixSigmaAuditService` классифицирует подстрокой · `CATEGORY_ERROR_SCAN` (D002)

*Замер:* `isDefectWork` сопоставлял свободный текст подстрокой; уже чинилось однажды.

*Делать:* заменить сопоставление текста на признак, который **несёт сама запись**: тип дефекта из журнала,
код категории. Совпадение слов не есть совпадение предмета — это буквально определение категориальной
ошибки.

*Заслон:* запись с текстом-омонимом и другим типом не засчитывается как дефект.

*Опровержение:* подать текст, где нужное слово стоит в другом смысле, и посмотреть, засчитан ли он.

---

### 14. Откат не знает периода пополнения · `RELIABILITY_CHAIN` (D010), закон 9

*Замер:* пауза удваивается 30 → 60 → 120 → 240 → 480 минут и ограничена восемью часами. Суточная квота
обновляется раз в сутки. Значит после обнуления фабрика может простоять ещё часы до следующей пробы. **Это
наша конструкция, а не внешнее условие**, и сегодня она уже стоила простоя.

*Делать:* следующая проба назначается по **раннему** из двух: собственный откат и начало нового периода
пополнения. Период берётся из наблюдения (когда отказы прекращались прежде), а не назначается константой —
образцовая форма уже есть в `BetaPosterior`, где срок выводится из накопленного свидетельства.

*Заслон:* после отказа, различённого как исчерпание внешнего бюджета, следующая проба назначена не позже
начала нового периода.

*Опровержение:* сдвинуть время за границу периода и посмотреть, ждёт ли аккаунт своего удвоения.

---

### 15. Исчерпание попыток становится вердиктом о требовании · `INSTITUTIONAL_FACT_REGISTER` (D007), закон 12

*Замер:* отказ по чужой квоте списывается с `A(τ)` наравне с прочими — «attempt N/14». Четверть часа
исчерпания обнулит предел четырнадцати требований подряд.

*Делать:* **счёт не трогать** — ёмкость действительно потрачена, запрос был сделан. Менять последствие:
запись об исчерпании обязана называть состав (сколько попыток пришлось на отказы, ничего не установившие о
требовании), а требование, чей предел съеден одними внешними отказами, переходит в возобновляемое состояние
«не испытано в пределах ёмкости», а не в поглощающее «не доставлено».

*Заслон:* задача, все отказы которой внешние, при исчерпании не получает поглощающего вердикта.

*Опровержение:* исчерпать предел одними внешними отказами и посмотреть на итоговый статус требования.

---

### 16. Круг самозаказа · `DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` (D002) · закон 3, ограничение области находки

*Образец.* Джон Остин, `BARCAN-TAG-00 CODE-GUARDIAN`, принцип перформативных высказываний, anchor *How to Do
Things with Words*. Сильная форма дословно: «назван тип, схема или переходник, удерживающий границу рода:
процесс не выдаётся за объект, **наблюдение за полномочие**, политика за данные». Слабая: «мы понимаем
разницу». Опровержение образца: «найти место, где значение одного рода присваивается полю другого **без
преобразования**».

*Почему именно он.* «Не заехало в main» — это **наблюдение о доставке** уже заказанного требования. Оно
записывается в поле рода «требование клиента» без всякого преобразования — ровно то место, которое ищет
опровержение. По Остину запись требования есть **перформатив**: она не описывает желание клиента, она его
учреждает. Учредить желание из собственной неудачи — значит выдать наблюдение за полномочие; отсюда 196
заявок при 15 просьбах клиента.


*Замер:* 196 заявок одного источника `delivery_never_reached_main`, 155 отброшены; клиент попросил 15.
Темп сейчас единицы в день — перепроизводство накопленное, а не текущее.

*Делать:* наблюдение «не заехало» о требовании, **уже заказанном и не отменённом**, есть факт о доставке, а
не новое требование: писать в журнал дефектов доставки, заявку не создавать.

*Заслон:* повторная неудача доставки уже заказанного требования новой заявки не создаёт.

*Опровержение:* провалить доставку дважды и посчитать заявки.

---

### 17. Аренда чистильщика назначена, а не выведена · `BELIEF_UPDATE_LEDGER` (D007)

*Замер:* три минуты, не продлеваются, пока идёт работа. Пока разбор занимает секунды — запас двукратный по
порядку; при росте графа дефект вернётся молча.

*Делать:* либо продлевать аренду на время работы, либо выводить её длительность из наблюдений, как это
сделано в `BetaPosterior`. Назначенная константа запрещена законом 8.

*Заслон:* работа, длящаяся дольше аренды, не теряет свой захват.

*Опровержение:* задержать работу под арендой и посмотреть, снесёт ли её чистильщик.

---

### 18. Запрет на заводские файлы можно молча выключить · `TRUTH_STATUS_TABLE` (D012)

*Замер:* классификатор внедряется как `@Autowired(required = false)`; в живом контексте бин есть и запрет
работает — проверено. Но отсутствие бина выключило бы запрет **молча**.

*Делать:* сделать отсутствие классификатора **видимым третьим исходом**: запись при старте либо отказ
запуска. «Не могу проверить» не имеет права выглядеть как «проверил».

*Заслон:* при отсутствующем классификаторе точка записи не ведёт себя так же, как при исправном.

*Опровержение:* убрать бин и посмотреть, отличается ли поведение.

---

### 19. Детектор дубликатов не может увидеть главный случай · `CATEGORY_ERROR_SCAN` (D002) · **СРОЧНО**

*Замер, снятый по указанию оператора:* из 512 задач проекта **78 приходятся на 7 предметов**. Худшее — **31
задача на компиляцию одной заявки** `b1c27ea9` и **30 на другую** `c7fc10ad`: шестьдесят одна задача на две
единицы работы. Каждая отправленная задача — сессия Jules. Распределение дубликатов по статусам: 357
`done`, 75 `failed`, 2 `queued`.

*Что не так с механизмом.* `ContinuousOrchestrationService.checkForDuplicateTaskContent` берёт последние 30
задач и **отфильтровывает терминальные** (`done`, `failed`, `blocked`, `spike_completed`), считая дубликатом
только то, что застряло сейчас; порог — три. Сужение сделано 4 августа и было верным: до него трёх давно
завершённых дубликатов хватало, чтобы заблокировать проект навсегда без выхода — блок запрещал ровно ту
отправку, которой только и можно было вытолкнуть старые записи из окна.

Но сужение **поменяло предмет**. На 31 задачу, отработавшую по очереди, детектор в любой момент видит одну —
ниже порога. Он не может сработать на этом случае **по устройству**, а не по настройке. Механизм измеряет
«сколько дубликатов застряло сейчас», а называется и используется как «порождаются ли дубликаты». Потраченная
сессия потрачена независимо от того, чем задача кончилась.

*Делать.* Развести две разные величины, не воскрешая старый тупик:
1. **Застрявшие дубликаты** — как сейчас, терминальные исключены, порог три, ведёт к жёсткому состоянию.
   Здесь ничего не менять.
2. **Темп порождения дубликатов** — новая величина: сколько задач с одним ключом содержания **создано** за
   окно, включая завершённые. Она не блокирует ничего, она **пишется в журнал дефектов** и видна в своде.
   Блокировать ею нельзя — именно блокировка и дала тупик 4 августа.

*Заслон:* последовательность из N задач с одним ключом, каждая из которых успешно завершилась, порождает
запись о темпе. На нынешнем коде тест обязан краснеть.

*Опровержение:* прогнать двадцать одинаковых задач до `done` подряд и посмотреть, узнала ли фабрика.

*Порядок:* этот пункт не отменяет пунктов 1 и 2 — он объясняет их цену. Тридцать одна задача на одну заявку
появилась потому, что у заявок двадцать писателей и ни одного хозяина; детектор лишь не поднял тревоги.

---

### 20. Отказ выбора аккаунта называет не свою причину · `PRINCIPLED_INTEGRITY` (D012), закон 12 · **СРОЧНО**

*Замер, стоивший половины смены.* `AccountRepository.lockAccountByNameWithCapacity` отвергает аккаунт по
четырём независимым условиям: `enabled = false`, статус в списке снятых, статус `api_blocked`/`daily_limited`,
число живых сессий не меньше предела. `ProjectFlowService:3606` пишет во всех четырёх случаях одно:
*«у аккаунта нет свободной ёмкости»*. Живой случай: шесть аккаунтов из семи были **выключены**, седьмой в
отдыхе, а журнал утверждал переполнение. Диагностика уходила искать сессии там, где стоял выключатель.

*Делать.* Отказ обязан называть **тот конъюнкт, который не выполнился**. Практически: запрос возвращает не
`Optional<AccountEntity>`, а причину отказа — выключен, снят, в отдыхе, исчерпаны сессии, — и сообщение
печатает её. Четыре причины уже существуют в самом запросе; их надо не изобретать, а перестать терять.

*Заслон:* при выключенном аккаунте отказ называет выключение, а не ёмкость; при исчерпанных сессиях — ёмкость,
а не выключение. Утверждение о соответствии причины условию, не о наличии текста.

*Опровержение:* выключить аккаунт и прочитать журнал. Слово «ёмкость» в нём — пункт не сделан.

*Почему срочно:* ложная причина дороже молчания. Молчание отправляет искать, ложная причина отправляет
искать **не там**, и цена измерена в часах.

---

### 21. Два рычага останавливают фабрику без следа · `INSTITUTIONAL_FACT_REGISTER` (D007)

*Замер.* `enabled` у аккаунта и настройка `task_compiler_account_name` — каждый способен остановить весь
поток. **Ни одну из них не пишет ни один механизм фабрики**: во всём `src/main` нет ни одного
`setEnabled(false)`, а настройки пишутся одной строкой и только `system_stall_status`. Значит обе меняются
снаружи, через API. И **ни у одной нет записи о том, кто и когда**: в журнале нет ни строки о выключении
аккаунта или смене привязки.

Практическое следствие уже наступило: аккаунт компилятора оказался выключен, привязка — переставлена на
другой аккаунт, и установить, кем и когда, невозможно ни мне, ни оператору.

*Делать.* Всякое изменение этих двух величин пишет запись: что изменилось, с чего на что, когда и по чьему
обращению. Образец требует именно этого — статус есть институциональный факт, подкреплённый правилом, и у
правила должна быть запись аудита.

*Заслон:* изменение `enabled` или привязки компилятора без появления записи невозможно.

*Опровержение:* сменить значение и не найти следа.

---

### 22. Резервирование единственного аккаунта опустошает общий пул · `PART_WHOLE_OWNERSHIP` (D004) · **СРОЧНО: держит поток**

*Замер.* Конвейер стоит: последняя отправка 19:05, простой заявлен фабрикой. Три PR открыты, два носителя
ревью созданы в 19:17 и 19:32 и **стоят в очереди — ни разу не отправлены**. В журнале: `No general-pool
account has free capacity right now`.

*Механизм, найденный в запросе, а не в сообщении.* `lockNextJulesAccountWithCapacity` содержит два условия,
о которых сообщение молчит:

    AND (:reservedName IS NULL OR a.name <> :reservedName)
    AND COALESCE(a.sessions_dispatched_today,0) < COALESCE(a.estimated_daily_capacity,:maxDailySessions)

Первое **исключает из общего пула зарезервированный аккаунт компилятора по имени**. Второе — суточный предел.

*Цепочка целиком.* Шесть аккаунтов из семи выключены. Единственный включённый и здоровый — `eneikdru`. Он же
назначен аккаунтом компилятора. Значит общий пул **пуст по построению**, и всякая роль, кроме компилятора,
голодает. Второе условие бьёт туда же: у него 501 сессия за сутки при пределе 15.

*Почему механизм разблокировки не помог.* Он написан верно: при пустом вердикте счётчик растёт, на третьем
разе задача помечается `blocked` и уходит в обычное восстановление. Но он требует **завершения** носителя
ревью, а носитель не стартовал. Сторож при этом считает цель «пробованной» по самому **факту существования**
носителя, не глядя, запускался ли тот. Существование принято за попытку — категориальная ошибка, и она
запирает цель навсегда, пока пул пуст.

*Делать, три части.*
1. **Резервирование не имеет права опустошать пул.** Если после исключения зарезервированного имени пул
   пуст, резервирование обязано уступить: компилятор и общий пул делят один аккаунт по очереди, а не так,
   что один голодает насмерть. Условие «исключить» верно только пока есть кого исключать.
2. **Сторож обязан различать «носитель создан» и «попытка была».** Цель помечается пробованной, только если
   носитель **стартовал**; иначе первый же несостоявшийся запуск запирает её навсегда.
3. **Сообщение обязано называть свою причину** — резервирование, суточный предел или ёмкость. Сегодня три
   разные причины печатаются одной фразой про ёмкость (см. пункт 20).

*Заслон:* при единственном включённом аккаунте, назначенном компилятору, общий пул не пуст. И: носитель,
созданный но не стартовавший, не помечает цель пробованной.

*Опровержение:* оставить один включённый аккаунт, назначить его компилятору и посмотреть, уйдёт ли задача
общего пула.

*Немедленно, без кода:* включить второй аккаунт — общий пул перестанет быть пустым. Это конфигурация
оператора и его квоты.

---

### 23. Выключенный аккаунт — дверь в одну сторону · `TRUTH_STATUS_TABLE` (D012) · **СРОЧНО: держит поток**

*Замер.* Пять аккаунтов из семи стояли `enabled = false`. Оператор их не выключал. Время выключения
зажато с двух сторон: в 13:06:44–13:08:05 (2026-09-05) все пять были **выбраны боевым запросом
`lockNextJulesAccountWithCapacity`, в котором первым же условием стоит `a.enabled = true`** — значит тогда
они были включены; после 13:08:05 они не появляются в журнале проекта ни разу. Пять часов подряд, с 13:08 до
18:24, весь поток тянул **один** `fivedmitr-sys` (41, 50, 64, 121, 50 обращений в час), пока не выгорел в
`api_blocked`. Все пять включены обратно 2026-09-05 в 20:4x.

*Что установлено точно.* Во всём `src/main` **нет ни одного `setEnabled(true)`**: включить аккаунт не может ни
один механизм фабрики. А восстановление ищет кандидатов запросом `findByStatusAndEnabledTrue` — то есть
**рассматривает только уже включённые**. Выключенный аккаунт невидим для восстановления по построению.
Следовательно `enabled = false` есть **поглощающее состояние без выхода**, и ни один сторож о нём не сообщает.

*Что установлено про причину.* Версия «наследство миграции V19» **опровергнута**: в 13:08 аккаунты
проходили условие `enabled = true`. Остаётся единственный путь — `PATCH /api/accounts/{id}` с телом
`{"enabled": false}`, единственное место в `src/main`, где это поле вообще пишется
(`AccountController.java:93`), и единственный его клиент в репозитории — флажок в `AdminDashboard.svelte:500`.

*Кто именно — установить нельзя и никогда будет нельзя.* Замер: `GET /api/accounts` **с внешнего адреса
отдаёт HTTP 200 без единого учётного данного**; Spring Security в проекте отсутствует, фаервола на хосте нет,
порт 8080 открыт в мир. Запись о вызове не ведётся нигде. То есть выключить аккаунты может кто угодно из
интернета, любой агент, любая вкладка браузера — и следа не останется. Это, а не сам факт выключения, есть
корневой дефект; см. новый пункт 24.

*Разбор по Энском.* Вопрос «кто выключил» уводит в сторону. Система действует под описанием
**«рассматриваю включённые аккаунты»**, и под этим описанием выключенного аккаунта не существует. Никто не
решал держать их выключенными — их **некому увидеть**. Поглощающее состояние, о котором никто не докладывает,
неотличимо от несуществующей сущности.

*Делать.*
1. **Выключенность обязана быть видимой.** Свод и детектор застоя обязаны называть число выключенных
   аккаунтов рядом с числом свободных; сегодня «шесть свободных» и «шесть выключенных» выглядят одинаково.
2. **У поглощающего состояния обязан быть выход или срок.** Либо выключение имеет причину и срок, по
   истечении которого аккаунт предлагается к возврату, либо оно объявляется окончательным — и тогда аккаунт
   переводится в `decommissioned`, где ему и место. Нынешнее «выключен и невидим» — третье, худшее.
3. **Восстановление обязано хотя бы сообщать о том, чего не видит.** Раз в цикл: «кандидатов ноль, при этом
   выключенных шесть» — одна строка, снимающая целый класс слепоты.
4. **Ротация обязана заметить, что несёт один.** Пять часов весь поток шёл через единственный аккаунт, и
   никто не сказал ни слова. Пул из семи, работающий одним, — это не «пул работает», это отказ шести,
   замаскированный успехом одного.

*Заслон:* при всех выключенных аккаунтах фабрика сообщает именно это, а не «нет свободной ёмкости».

*Опровержение:* выключить все аккаунты и прочитать журнал. Слово «ёмкость» вместо «выключены» — пункт не
сделан.

---

### 24. Пульт управления фабрикой открыт в интернет без пароля · `BOUNDARY_TOPOLOGY` (D006) · **СРОЧНО**

*Замер.* `curl http://2.28.123.162:8080/api/accounts` с внешнего адреса — **HTTP 200**. В `pom.xml` нет
`spring-boot-starter-security`, в `src/main` нет ни одного `SecurityFilterChain`, на хосте не поднят ни `ufw`,
ни правила `iptables`. Порт 8080 опубликован как `0.0.0.0:8080`.

*Что это значит.* Весь управляющий контур открыт анонимно: `PATCH /api/accounts/{id}` (включить/выключить
аккаунт, подменить ключ Jules), `DELETE /api/accounts/{id}`, смена статуса, весь `/internal/gemini-observer`.
Ключи Jules пишутся, читаются в маскированном виде — но **пишутся** без пароля.

*Философия.* `BOUNDARY_TOPOLOGY` (D006) в сильной форме требует, чтобы у системы была названная граница и
чтобы каждое пересечение границы было опознано. Здесь границы нет вовсе: внутреннее и внешнее неразличимы,
поэтому вопрос «кто это сделал» не просто без ответа — он **не может иметь ответа**. Отсюда прямое следствие
для `INSTITUTIONAL_FACT_REGISTER` (D007): институциональный факт («аккаунт выключен») существует, а
институция, которая могла бы за него отвечать, — нет.

*Делать.*
1. Закрыть 8080 фаерволом до списка адресов, либо перевести публикацию порта на `127.0.0.1:8080` и ходить
   через туннель. Это делается за минуту и снимает весь класс.
2. Затем — общий секрет на изменяющие методы (`POST`/`PATCH`/`DELETE`) и на весь `/internal/**`.
3. Каждое изменение `enabled`, `api_key`, `status` и привязки компилятора обязано писать строку в журнал: что,
   когда, с какого адреса. Пункт 21 без этого не выполним.
4. **Замок и ключ не могут лежать за одной дверью.** `/sql` защищён настройкой `debug_sql_endpoint_enabled`,
   а настройка меняется через `PUT /api/settings`, у которого охраны нет: `isKnownKey` для этого ключа
   истинна, потому что он объявлен в `DEFINITIONS`. Два анонимных вызова подряд открывают произвольный SQL к
   живой базе, включая изменяющий. Чинится любым из двух способов, и лучше обоими: убрать ключ из
   изменяемых через API (оставить только переменную окружения) **и** закрыть контур по пункту 1. Комментарий
   в коде, обещающий, что эндпоинт «никогда не может быть живым там, где его не включили намеренно», обязан
   быть исправлен вместе с механизмом — сейчас он утверждает больше, чем механизм делает.

*Заслон:* тест, который бьёт изменяющий метод без учётных данных и требует не-2xx.

*Опровержение:* повторить `curl` снаружи. Ответ 200 на `GET /api/accounts` — пункт не сделан.

---

### 25. Сорок три вопроса в час, ответ на которые известен · `INUS_FACTOR_CHECK` (D007) · **расход бюджета**

*Замер.* За три часа `GitHubPullRequestService` получил **128 ответов 404** на запросы файлов:
42 в 20:26, 43 в 21:28, 43 в 22:30 — ровно раз в час, залпом внутри одной минуты. Путей **43 разных**, и
каждый запрошен ровно **три раза**, по одному за час. То есть спрашивается один и тот же список, и ответ
каждый раз один и тот же: файла нет.

*Кто спрашивает.* `ProductCapabilityService` выводит путь контракта из названия возможности —
`docs/contracts/<название через дефис>.openapi.yaml` — и идёт за ним в репозиторий клиента. Ни одного из
сорока трёх файлов там нет и никогда не было.

*Чего это стоит.* 43 обращения в час к внешней системе — **больше тысячи в сутки**, целиком в тот же
бюджет, которым закон 13 распоряжается. Ответ известен заранее из предыдущего часа, и ничего в нём не
менялось.

*Второе следствие, и оно хуже первого.* Залп идёт **внутри удержания соединения к базе**: срабатывание
`leak-detection` в 22:28:56 и возврат соединения в 22:30:51 обрамляют залп 404 секунда в секунду, держатель
во всех трёх стеках один — `ContinuousOrchestrationService.continuousOrchestrate`. То есть соединение из
пула занято ~2 минуты, пока механизм ходит по сети. Пул на 24, поэтому голодания нет — но это сеть внутри
области соединения, а не медленный запрос.

*Философия.* `INUS_FACTOR_CHECK` (D007) в сильной форме требует, чтобы у каждого обращения был назван
вклад в вывод. Здесь вклад нулевой: отсутствие файла установлено час назад и не могло измениться без
события, которого механизм не ждёт. Рядом `BOUNDARY_TOPOLOGY` (D006): сетевой вызов не имеет права
находиться внутри области, занятой ресурсом пула, — то же правило, по которому провижининг вынесен из
транзакции приёма.

*Делать.*
1. **Помнить отрицательный ответ.** 404 на путь при неизменном `ref` — факт, годный до тех пор, пока не
   изменился коммит `main`. Спрашивать заново только после смены `main`, а не по расписанию.
2. **Спрашивать одним запросом.** Список файлов каталога берётся одним обращением к дереву; сорок три
   вопроса «есть ли файл» заменяются одним «что лежит в `docs/contracts`».
3. **Вынести сеть из области соединения.** Собрать нужное из базы, отпустить соединение, потом идти в сеть.

*Заслон:* тест, считающий число обращений к GitHub на один проход `ProductCapabilityService` при неизменном
`main`: второй проход обязан дать **ноль**.

*Опровержение:* посчитать `GitHub file fetch failed` за час. Больше нуля при неизменном `main` — пункт не
сделан.

*Проверено 2026-09-06 07:11: залп идёт, пункт подтверждён.* В 06:44 — **27** строк, в 06:45 — **16**, итого
**43** за один залп: то же число, что и в прежних замерах (42, 43, 43). Час отсчитывается от старта
контейнера, а не от круглой отметки: после пересборки в 06:15 залп пришёлся на :44–:45, а не на :26–:30 —
поэтому получасом раньше я видел 2 строки и правильно не сделал вывода. Две пересборки бэкенда залп не
затронули.

---

### 26. Хостинг и наблюдение исполняет один механизм · `CATEGORY_ERROR_SCAN` (D002)

*Замер.* Оператор сообщил: клиентского проекта не видно на хостинге. Контейнеров `test-fiftieth_backend`,
`_db`, `_backup` в системе не было вовсе. В журнале за три часа — два полных цикла: поднят и здоров
(`launchSuccess=true healthStatus=200`) в 21:28:51, снесён в 21:44:39; поднят в 22:30:51, снесён в 22:46:42.
Поднят вручную заново в 23:0x, отвечает `200` изнутри и снаружи на порту 18080.

*Кто в ответе.* `ClientRuntimeObservabilityService` — он решает, пора ли потратить цикл «запустить и
проверить», и он же сносит: `ClientRuntimeObservabilityService.java:161–168`, срок задан свойством
`client-runtime-observability.live-preview-idle-minutes`, **по умолчанию 15 минут**. Поднимает через
`RuntimeLauncherClient` — единственное в бэкенде, что знает о сайдкаре-лаунчере (`POST /launch`,
`/healthcheck`, `/teardown` на 8091).

*Что здесь неверно, и это не поломка.* Механизм работает ровно как задуман, и его собственная запись в этом
файле честно стоит `SELF_MODEL_SANITY` (D013) — **сильная**. Неверен не он, а **род задачи**, который на него
возложен. `CATEGORY_ERROR_SCAN` (D002) в сильной форме требует названного переходника, удерживающего границу
рода: «процесс не за состояние, наблюдение не за полномочие». Здесь **наблюдение принято за хостинг**.
Запуск на 15 минут — это инструмент, отвечающий на вопрос «стартует ли продукт и отвечает ли он о своём
здоровье». Вопрос «почему сайта нет» меряет инструмент назначением, которого у него нет, — это же и
`TELEOSEMANTIC_FEEDBACK` (D011): назначение пула наблюдения не «обслуживать посетителя».

*Решение оператора, 2026-09-05: сноса не менять* — «пусть сносит, не страшно». Постоянная выкладка не
заказана, растягивание окна отвергнуто вместе с ней. Пункт сжимается до одного действия.

*Делать.* **Снос обязан называть род.** Строка «live-preview window expired, torn down» верна и при этом
читается как поломка хостинга — на ней и был потерян час. Она обязана говорить, что снесено **наблюдение**, а
не выкладка, и что продукт при этом был здоров (`launchSuccess=true healthStatus=200`). Это ровно тот
переходник, которого требует сильная форма `CATEGORY_ERROR_SCAN`: род называется в самом сообщении.

*Не делать (записано, чтобы не всплывало снова).* Постоянный стек и растягивание `live-preview-idle-minutes`
отклонены оператором. Второе к тому же уничтожило бы сам инструмент: он меряет холодный старт, а на суточном
окне мерил бы давно работающий процесс.

*Заслон:* тест, требующий, чтобы постоянная выкладка не имела ссылки на `RuntimeLauncherClient`, а
наблюдение — на постоянный стек. Общий контейнер у двух родов и есть дефект.

*Опровержение:* через 20 минут после запуска спросить порт 18080. Ответ есть — значит рода разведены; нет —
пункт не сделан.

---

### 27. Слово «internal» принято за полномочие · `PRINCIPLED_INTEGRITY` (D012) + `CATEGORY_ERROR_SCAN` (D002) · **СРОЧНО**

*Как нашёл.* Из 98 записей файла у 28 форма образца стоит «слабая / не мерено / нарушена», и у 19 из них не
было ни одного предписания. Шесть из этих девятнадцати — изменяющие контроллеры; замер по ним развёл их
надвое, и это важнее самой находки.

*Что замер опроверг.* Четыре контроллера из шести — `ClaimController`, `WishlistController`,
`ProjectController`, `GithubWebhookController` — не пишут в репозитории вовсе, а идут **через свой сервис**:
`claimService`, `wishlistService`, `projectFlowService`, `prReviewPipelineService`. Это правильная форма, и
подозрение «контроллеры обходят владельца состояния» по ним **снято**. Больше того: `InternalTaskController`
при смене статуса **проверяет необратимость** (`InternalTaskController.java:97` — «Cannot overwrite terminal
task status»), то есть закон 20 на этом пути соблюдён. Догадка была моя, опровержение — замером.

*Что осталось, и оно тяжелее.* `GET /internal/tasks` (`InternalTaskController.getAllTasks`) отдаёт
**несегментированный дамп всех задач всех проектов**. Замер снаружи: `HTTP 200`, **21 907 622 байта**, без
единого учётного данного, с публичного адреса.

*Цепь механизмов, у каждого свой замер.*
1. `InternalTaskController.getAllTasks` — производит дамп. Его собственный javadoc гласит: *«Restricted to
   localhost in production via filter/security (omitted for brevity in this task)»*. Фильтр не написан.
2. Отсутствующий заслон — во всём `src/main` нет ни одного `SecurityFilterChain`, и нет
   `spring-boot-starter-security` в `pom.xml`. Заслон, на который ссылается комментарий, **не существует**.
3. H2, встроенный в процесс бэкенда. Здесь это не деталь: по записи в этом же файле повторный опрос именно
   этого эндпоинта (тогда 60 МБ, ~1100 строк) **уже приводил к `OutOfMemoryError` и падению базы** —
   инцидент 2026-08-11, ради которого и появился `/status-counts`. База живёт в том же процессе, поэтому
   исчерпание памяти убивает не запрос, а хранилище.

*Философия — два образца, главный первый.*
`PRINCIPLED_INTEGRITY` (D012): комментарий объявляет ограничение, которого в коде нет. Это ровно то, что
такт запрещает считать заслоном — **сообщение, утверждающее больше, чем делает механизм**; и оно опаснее
молчания, потому что читающий код видит защиту и не ищет её.
`CATEGORY_ERROR_SCAN` (D002): приставка `/internal` — это **имя**, принятое за **полномочие**. Сильная форма
образца требует переходника, удерживающего границу рода; здесь границы нет, а есть слово в пути.

*Делать.*
1. Ограничить `/internal/**` по адресу — это и обещано комментарием. Одно правило, минута работы.
2. `getAllTasks` обязан быть сегментирован проектом и ограничен по числу строк. Несегментированный дамп не
   нужен ни одному потребителю: для наблюдения есть `/status-counts`, появившийся после того самого падения.
3. Комментарий исправить вместе с механизмом. Пока фильтра нет, javadoc обязан говорить, что защиты нет.

*Заслон:* тест, бьющий `/internal/tasks` без учётных данных и требующий не-2xx; и тест, требующий, что
ответ `getAllTasks` ограничен проектом и пределом строк.

*Опровержение:* повторить запрос снаружи. `HTTP 200` с многомегабайтным телом — пункт не сделан.

---

### 28. Два механизма дизайна числятся сильными: один ни разу не сработал, другой читает не то · `FALSIFICATION_HARNESS` (D008) + `LEVEL_OF_ABSTRACTION_LOCK` (D010)

*Повод.* Оператор: «лично мне не нравятся механизмы дизайна и фронтенда… может они и хорошие, я сужу очень
поверхностно по результатам проекта». Судить по результату — верный критерий, и он измерим.

*Замер 1. Дрейф не считается никогда.* `DesignDriftMonitorService` каждый цикл забирает живую страницу
клиента — в журнале «fetched live page for project … (70408 chars)» — и сразу за этим: «drift comparison
skipped, no established per-project design-system baseline yet». Это не сбой:
`DesignDriftMonitorService.java:62–67` объявляет прямо, что сравнение «intentionally not run until a real
per-project baseline exists». Механизм тратит полную загрузку страницы за цикл и **не выносит ни одного
суждения**. Правило модели на этот случай уже записано: *механизм, зарегистрированный, но ни разу не
сработавший, считается неработающим.*

*Замер 2 был про не тот предмет и снят 2026-09-06.* Я написал, что аудит согласованности читает отданный
HTML продукта и потому видит пустоту. **Он его вовсе не читает.** `DesignAssetService:461–465` подаёт ему
`htmlFileBytes` — файл **сгенерированного экрана**, ещё до того, как тот попадёт в репозиторий. Проверка
живьём: аудит исправно выдаёт числа, `consistency audit traceRatio=… crossScreenJaccard=…`, дважды за час.
Мой упрёк был адресован не тому механизму.

*Что при этом показал его собственный замер, и это уже настоящее.* `traceRatio ≈ 0,095` в обоих
наблюдениях: **к объявленной дизайн-системе восходит около девяти с половиной процентов значений
сгенерированного экрана**, остальные девяносто — вне набора. `crossScreenJaccard = 1,0`: экраны согласованы
**между собой** и рассогласованы **с системой**. **И это утверждение я снял через час собственным замером — см. предписание 36.** Порог есть, отказ есть, и
механизм отвергает **каждый** экран.

*Дефект в этом файле, а не только в коде.* Обе записи стояли с формой **«сильная»**. Для дрейфа это ложь по
определению самой модели, и статус снят. Ложный статус хуже отсутствующего: он снимает вопрос.

*Философия — два образца, главный первый.*
`FALSIFICATION_HARNESS` (D008): сильная форма требует, чтобы у проверяющего механизма была своя проверка, а
проверка, которая не может сработать, проверкой не является. Сравнение, отложенное до появления эталона,
которого никто не создаёт, — заслон, не способный упасть.
`LEVEL_OF_ABSTRACTION_LOCK` (D010): согласованность живёт на уровне **нарисованной** страницы, а меряется на
уровне **доставленного** HTML. Переход между уровнями не сделан. Я совершил ту же ошибку в этом же такте,
пытаясь судить о дизайне по ответу сервера, — и потому знаю ей цену.

*Делать.*
1. **Эталон обязан кто-то создавать.** Сравнение дрейфа ждёт `per-project design-system baseline`; ни один
   механизм его не производит. Либо назвать производителя эталона, либо признать дрейф неработающим и убрать
   загрузку страницы: сейчас это расход без вывода.
2. **Мерить нарисованное, а не отданное.** Аудит токенов обязан работать по исходникам экранов в репозитории
   клиента либо по странице после исполнения скрипта. По ответу сервера SPA он не даст верного ответа ни при
   каком качестве дизайна.
3. **Пока не сделано — статусы обоих держать «не мерено».**

*Заслон:* тест, подающий аудиту HTML-оболочку SPA без стилей и требующий вердикта «не могу судить», а не
«ноль токенов».

*Опровержение:* найти в журнале хоть одно сравнение дрейфа, которое **состоялось**. Есть — пункт снимается.

---

### 29. Поле `failedTasks` в общедоступном своде значит не то, что говорит · `SENSE_REFERENCE_SPLIT` (D009) + `CONVERSATION_MAXIM` (D007)

*Замер.* `GET /flow-spine` отдаёт `failedTasks = 0`. `GET /internal/tasks/status-counts` по тому же проекту
отдаёт `failed = 83`. Оба читают одну таблицу, и оба правы: свод считает **только те провалившиеся задачи, с
которыми назначенный решатель способен что-то сделать** — `FlowSpineService.countFailedTheResolverCanAct`,
строки 376–405, домен задан `PlannedWorkRecoveryService.isResumableInPrinciple`. Решение верное и обосновано
на месте: гейт и его решатель обязаны считать по одному множеству, иначе состояние недостижимо-из по
построению.

*Где дефект.* Не в счёте, а в **имени и месте**. Поле лежит в `FlowSpineDto` в одном ряду с `queuedTasks`,
`doneTasks`, `blockedTasks`, `reviewTasks` — и все они простые счётчики по статусу. Одно поле из пяти молча
значит другое. Внутренние потребители (`FlowSpineService:257`, `:668`, `OperationalPolicyService:150`) хотят
именно узкого смысла и получают верное. Внешний потребитель свода получает число, читающееся как «провалов
нет», при восьмидесяти трёх провалах.

*Доказательство, что дефект настоящий, а не придирка.* Внешним потребителем был я. Я докладывал оператору
«упавших ноль» несколько тактов подряд, читая это поле. Восемьдесят три провалившиеся задачи существовали всё
это время. Ошибка не в моей невнимательности: поле стоит среди простых счётчиков и названо как простой
счётчик.

*Философия — два образца, главный первый.*
`SENSE_REFERENCE_SPLIT` (D009): сильная форма требует, чтобы отображаемое имя, хранимый идентификатор и
сущность в API были разведены так, что перепутать нельзя; слабая — **одно поле служит всем сразу**. Здесь
одно поле служит и предикатом гейта, и публичным счётчиком.
`CONVERSATION_MAXIM` (D007): сильная форма — вывод однозначен **для следующего работника**; опровержение —
дать отчёт следующему работнику и посмотреть, сможет ли он действовать. Следующим работником был я, и я
действовал неверно. Это и есть выполненное опровержение, а не предположение о нём.

*Делать.*
1. Переименовать поле в своде так, чтобы имя называло домен: `failedTasksRecoveryCanResume`. Имя внутри
   `FlowSpineInput` может остаться коротким — там домен очевиден из соседнего комментария.
2. Рядом отдать простой счётчик `failedTasksTotal`. Два вопроса — два поля; это и есть разведение из
   сильной формы образца.
3. То же проверить у `doneTasks`: он равен `done + spike_completed`, то есть тоже не простой счёт по статусу.
   Сейчас числа совпадают, потому что `spike_completed` равен нулю, — совпадение, а не свойство.

*Заслон:* тест, требующий, чтобы для проекта с провалившейся невосстановимой задачей свод отдавал
`failedTasksTotal > 0`, а `failedTasksRecoveryCanResume` — ноль.

*Опровержение:* сравнить два эндпоинта на одном проекте. Расхождение при одинаково звучащих именах — пункт не
сделан.

---

### 30. Исчерпание нашего доступа становится вердиктом о носителе · `INSTITUTIONAL_FACT_REGISTER` (D007) + `INUS_FACTOR_CHECK` (D007)

*Откуда взято.* Запись `ClaimService` сама называла открытое место: образец стоит «**сильная по притязанию,
слабая по последствию**». Последствие теперь измерено.

*Замер за сутки 2026-09-05 по долговечному журналу проекта (20 371 строка).*
Задач выбыло из очереди отправки по исчерпанию бюджета в 14 попыток — **40 событий, 38 разных задач**.
Из них позже дошли до `DONE` — **ноль**. Списано в `blocked` без создания восстановления — 15.
Настоящих отказов Jules — **571**, из них без названного условия — **567 (99,3%)**. Успешных компиляций за
те же сутки — **74**. То есть на одну удачу приходится **7,7 отказа**.

*Гипотеза, которую замер опроверг.* Я предположил, что гибнут продуктовые требования. **Нет:** из 38
задач **33 — компиляторные носители**, одна — иной носитель, четыре не опознаны (одна из них — запасное
ревью PR). Продуктовая доставка за те же сутки выросла с 22 до 24 из 26. Носитель, гибнущий молча,
подчинён закону 2 и продуктовой работы не заказывает — поэтому числитель и не пострадал.

*Что остаётся дефектом.* Каждая смерть носителя стоит **до 14 попыток создания сессии** во внешний бюджет
закона 9, и носитель после этого создаётся заново. Тридцать три смерти за сутки — это расход, у которого
нет вывода. И решение о носителе принято по факту **о нашем доступе**, а не о носителе: Jules отказал
безымянно, значит носитель объявлен несостоятельным.

*Философия — два образца, главный первый.*
`INSTITUTIONAL_FACT_REGISTER` (D007): институциональный факт («носитель несостоятелен») обязан опираться на
основание, относящееся к предмету. Здесь основанием служит исчерпание **нашей** квоты — факт о нас, а не о
носителе. Именно это и значит «слабая по последствию» в записи `ClaimService`.
`INUS_FACTOR_CHECK` (D007): вклад каждой попытки в вывод обязан быть назван. Четырнадцать попыток с
тождественным безымянным отказом дают ровно столько же знания, сколько одна; тринадцать из них — расход без
вклада.

*Открытый вопрос, который я не закрываю догадкой.* Шторм отказов кончился между 14:00 и 15:00: по часам
00–09 держалось 50–60 отказов в час при 1–5 компиляциях, в 14:00 — 20 отказов при **21** компиляции, в
15:00 — **1 отказ при 9 компиляциях**. Две версии. Губернатор безымянного отказа (развёрнут в 14:50)
объясняет **падение числа отказов**, но не объясняет четырёхкратный рост успехов: он замедляет повторы, он
не заставляет Jules принимать. Ограничение длины компиляторных промптов (развёрнуто в 12:41) объясняет рост
успехов, но часы 12 и 13 всё ещё дали 31 и 33 отказа. **Что именно сняло причину — не установлено.**
Наблюдение, которое решит: отказы шли при `promptLength` 26 565, 38 307 и 47 755; если ни один отказ после
15:00 не имеет длины выше порога — причина в длине.

*Делать.*
1. **Бюджет попыток обязан различать вид отказа.** Четырнадцать попыток осмысленны при разных причинах и
   бессмысленны при тождественной безымянной. При повторе того же безымянного отказа бюджет обязан
   схлопываться до одной пробы за окно отката.
2. **Вердикт обязан называть, о ком он.** Сообщение «14 refused session creations against a budget of 14»
   говорит о нас; статус, который оно ставит, говорит о носителе. Разделить: носитель не «несостоятелен», а
   «не отправлен, внешняя система отказывает без причины».
3. **Смерть носителя обязана считаться.** Тридцать три за сутки не видны нигде, кроме журнала: ни свод, ни
   детектор застоя их не называют.

*Заслон:* тест, дающий диспетчеру подряд тождественные безымянные отказы и требующий, чтобы попыток было не
14, а одна за окно отката.

*Опровержение:* посчитать за сутки события «left the dispatch queue» и сложить их попытки. Больше одной
попытки на окно при тождественном отказе — пункт не сделан.

---

### 31. Экран на телефоне не проверяет никто, и ревьюеру это предписано · `GROUPING_PROXIMITY_GATE` (D011) не применён + `FALSIFICATION_HARNESS` (D008)

*Повод.* Оператор: «сейчас в проекте наползают меню друг на друга на телефоне. не знаю, есть ли механизм,
который за это отвечает». Механизм есть. Он не смотрит, и не по недосмотру.

*Цепь, у каждого звена свой замер.*

1. **`ProjectFlowService:1174` — шаблон загрузки.** Фабрика кладёт в новый продукт
   `<meta name="viewport" content="width=device-width, initial-scale=1.0">`. Основание для телефона даётся
   один раз, при создании проекта. Это единственное место во всей фабрике, где слово «viewport» вообще
   встречается в производимом коде.

2. **`ProjectFlowService:5688–5700` — задание ревьюеру дизайна. Здесь дефект.** Текст задания дословно:
   *«This is a single generated screen, not a desktop/mobile pair — do NOT reject it solely for missing a
   second resolution; judge what is actually checkable from this one file»*, и следом: *«Be lenient by
   design: work must never stall waiting on your opinion. Reject ONLY for a small set of genuinely severe
   problems»*. То есть ревьюеру **предписано не требовать второго разрешения**. Наложение меню на телефоне
   не входит в его предмет по заданию, а не по слабости.

3. **`DesignConsistencyAuditService`** читает из HTML только hex-цвета, `rgb()` и `font-family`
   (`extractUsedTokens`). Геометрии — расстояний, перекрытий, порядка наложения — он не касается вовсе.

4. **`DesignDriftMonitorService`** сравнения не выполняет ни разу (предписание 28).

5. **Образца, который это ловит, в фабрике нет.** В корпусе он есть и назван точно:
   `GROUPING_PROXIMITY_GATE` (D011), сильная форма — «связанные элементы **измеримо ближе** друг к другу,
   чем к несвязанным», опровержение — «измерить отношение расстояний». В файле механизмов он упоминается
   **ноль раз**. Наложение — предельный случай этого отношения: расстояние ушло в отрицательное.

*Отдельный замер по продукту.* Отданная страница несёт
`viewport … maximum-scale=1.0, user-scalable=no` — продукт переопределил шаблон фабрики и **запретил
масштабирование**. Значит когда меню наползают, пользователь не может даже свести пальцы, чтобы отдалить.

*Чего я не измерил и не выдаю за замер.* Есть ли в приложении медиазапросы и как оно выглядит на телефоне —
**не установлено**. Продукт рисуется скриптом, в ответе сервера 74 249 байт при 53 символах CSS; судить по
нему о нарисованном экране нельзя, и я эту ошибку в этом файле уже допускал (предписание 28).

*Философия — два образца, главный первый.*
`GROUPING_PROXIMITY_GATE` (D011): у фабрики есть корпусный образец ровно на этот дефект, с готовым
обязательством доказательства, и он не применён ни в одном механизме. Дефект не в том, что проверка слаба, а
в том, что её **нет**.
`FALSIFICATION_HARNESS` (D008): ревьюер, которому предписано быть снисходительным и судить по одному файлу,
**не может упасть** на дефекте двух разрешений. Его «одобрено» не несёт сведений о телефоне — не потому, что
он ошибся, а потому, что вопрос ему не задан. Отсутствие отказа здесь не есть свидетельство качества.

*Делать.*
1. **Задать вопрос, прежде чем требовать ответа.** Пока продукт производит один экран на одно разрешение,
   упрекать ревьюера не в чем. Либо генерация даёт пару «десктоп/телефон», либо проверка переносится туда,
   где второе разрешение существует.
2. **Мерить отношение расстояний, а не мнение.** `GROUPING_PROXIMITY_GATE` требует числа: связанные ближе
   несвязанных. Перекрытие — отрицательное расстояние, и это проверяется машиной, без вкуса.
3. **Запрет масштабирования обязан быть замечен.** `user-scalable=no` в продукте, который фабрика
   загрузила без него, — это регресс относительно её же шаблона, и его никто не видит.

*Заслон:* тест, дающий проверке разметку с двумя элементами, чьи прямоугольники пересекаются, и требующий
отказа; и тест, требующий отказа при `user-scalable=no`.

*Опровержение:* найти в задании ревьюеру дизайна хоть одну строку, требующую второго разрешения. Есть —
пункт снимается.

---

### 32. План продукта пишется в пространстве имён фабрики · `INDEXICAL_CONTEXT_LOCK` (D006) · закон 26

*Живое, 7 сентября 2026 — заслон выложен и работает.* `docker logs eneikproductionsys-backend-1 | grep
"Law 26 (product namespace)"` даёт две сработки за сутки: в 02:42 компилятор предсказал
`src/main/java/com/eneik/production/services/InternalService.java`, в 08:43 —
`.../services/security/InternalSecurityService.java`, оба **внутри собственного пакета фабрики**, и оба
отвергнуты «before the collision ledger». Отсюда два вывода. Заслон исполняется. И компилятор **по-прежнему
производит** пути фабрики для проекта заказчика — значит путь заражения, закрытый 9 августа изъятием
журнала из подсказки (раздел XXIе), был не единственным. Пункт остаётся открытым: заслон ловит след, корень
не назван.

*Это и есть то самое наблюдение из такта, ставшее законом.* Стоячее наблюдение звучало так: ревьюер
регулярно отклоняет PR за утечку заводского в продукт клиента, гейт ловит, но порождающая сторона продолжает
выпускать — **потери происходят ДО заслона**. Теперь измерено, где именно.

*Цепь, у каждого звена свой замер.*
1. **`TechnicalLeadCompiler`** предсказывает `fileScope` задачи. Замер по контейнерному журналу: шесть
   срабатываний, пути `com/eneik/production/services/InternalService.java` (4 раза),
   `com/eneik/production/models/persistence/InternalEntity.java`,
   `com/eneik/production/services/security/InternalSecurityService.java`,
   `src/main/resources/db/migration/V_NEXT__internal.sql`.
2. **Продукт.** Его настоящее пространство имён — `com.eneik.epidemiology`, прочитано прямо из
   работающего `app.jar` (`BOOT-INF/classes/com/eneik/epidemiology/…`). Значит предсказанные пути не просто
   заводские — их **нет ни у кого**: у фабрики файлов с такими именами тоже ноль.
3. **Страж коллизий между эпиками** — тот, кто их убрал. Но он убирает путь потому, что его занял другой
   эпик. Два эпика предсказали один и тот же несуществующий заводской путь, и страж их **согласовал**.
   Совпадение выхода не есть выполнение закона: заслона на чужое пространство имён нет.

*Философия.* `INDEXICAL_CONTEXT_LOCK` (D006): сильная форма требует явного объекта контекста для «здесь» и
теста против утечки чужого; слабая — контекст из потока или статики. Планировщик берёт «здесь» из своей
статики — из кодовой базы фабрики — и переносит в план для чужого репозитория. Отсюда и вся стоячая
наблюдаемая утечка: она рождается **в плане**, а до кода и до ревьюера доезжает уже как готовая привычка.

*Делать.*
1. Пространство имён продукта установить один раз при загрузке и хранить как факт о проекте.
2. Всякий предсказанный путь сверять с ним. Путь вне пространства имён **роняет план**, а не вычищается:
   вычистка оставляет остальной план построенным о неверном предмете.
3. Страж коллизий оставить как есть — он про другое; но его срабатывание на несуществующем пути обязано
   подниматься как отдельное происшествие, а не гаситься.

*Заслон:* тест, дающий компилятору продукт с известным пространством имён и путь вне него, требующий отказа
плана.

*Опровержение:* посчитать в журнале строки «stripped» с путями вне пространства имён продукта. Больше нуля —
пункт не сделан.

---

### 33. Единственная машинная проверка куайновской границы стоит на пути, куда не приходят · `FALSIFICATION_HARNESS` (D008)

*Как нашёл.* Взял `VerificationEvidenceGate` из очереди — у него форма образца стояла «слабая» с
объяснением «два гейта из шести без вызывающих». Проверил объяснение и **опроверг его**.

*Что опровергнуто.* `EpistemicLayerInvariantGate` помечен `@Service` и реализует `GateCheck`;
`GateOrchestrator` внедряет `List<GateCheck>`, то есть Spring подаёт ему все реализации. Вызывающий есть.
Без аннотации остаётся один `BaseQualityGate` — он бином не является и потому действительно не собирается.
Моя запись говорила про два, замер даёт один.

*Что осталось, и оно тяжелее.* Ответ лежит в самом коде, замером от 2026-08-28 по 365 задачам
`test-fiftieth` (`TaskEntity.isVerifiedForDelivery`, строки 310–320): **гейтовый инструмент применился к
НУЛЮ из 365 задач**, тогда как инструмент критериев вынес суждение по 127 (82 удовлетворено, 45 опровергнуто).
Дословно: «the gate is not weak, it is unreachable — `GateOrchestrator.runQualityGate` stands on one of the
five paths that write `TaskStatus.done` and covers five of the thirteen roles». Единственный живой вызов —
`ClaimService:349`, в ветке допуска к ревью.

*Почему это касается именно нас с оператором.* `EpistemicLayerInvariantGate` — **единственное место, где
куайновская демаркация исполняется машиной**: периферии запрещено менять файлы ядра. Он применим к
периферийным ролям (`BARCAN-TAG-11`, `-05`, `-06`), и задачи таких ролей за сутки были — 3, 17 и 20
упоминаний. То есть предмет для проверки есть, а проверка до него не доходит. Я меряю критерий Куайна
вручную каждый такт ровно потому, что машинный вариант написан и не исполняется.

*Философия.* `FALSIFICATION_HARNESS` (D008): сильная форма требует, чтобы у проверяющего механизма была своя
проверка, а проверка, стоящая на пути, куда не приходят, **упасть не может**. Это тот же род, что и
предписание 28 про дрейф дизайна, но здесь предмет — сама граница ядра и периферии.

*Что при этом сделано верно и трогать нельзя.* Фабрика **знает** об этой недостижимости и построила союз двух
инструментов: `isVerifiedForDelivery` признаёт доставку либо по вердикту критериев, либо по гейту, и там же
закрыт пустой квантор — `deliveryChecksApplied() > 0`. Это честный обход, а не заплатка: у каждого
инструмента своя область, и союз объявлен в комментарии. Дефект не в союзе, а в том, что одна из его половин
пуста.

*Делать.*
1. **Назвать, на скольких из пяти путей к `done` стоит прогон гейтов**, и записать это число рядом с гейтом.
   Сейчас оно живёт в комментарии к другому классу.
2. **Куайновскую проверку вынести с пути `done` на путь слияния** — там, где меняются файлы, а не там, где
   задача признаётся сделанной. Периферийная задача, тронувшая ядро, обязана останавливаться до слияния.
3. **Пока не вынесена — статус гейта держать «не мерено», а не «слабая»**: слабый работает плохо, а этот не
   работает вовсе.

*Заслон:* тест, дающий периферийной задаче изменение файла ядра и требующий, чтобы её остановили **на том
пути, которым такие задачи действительно ходят**.

*Опровержение:* найти в журнале гейтов хоть одну запись с проверкой эпистемического слоя. Есть — пункт
снимается.

---

### 34. Возраст сторожа меряется по соседней отметке, потому что своей у него нет · `CAUSAL_PROCESS_TRACE` (D013) + `PRINCIPLED_INTEGRITY` (D012)

*Взято из очереди.* У записи `WishlistService` / `StrandedFinalizingSweepService` форма образца стояла
«сильная по освобождению, слабая по сроку: предел назначен, а не выведен», и прежде замер длительности снять
не удавалось. Снят.

*Замер.* Предел — `stranded-finalizing.max-age-minutes:3`, сторож ходит **каждую минуту**
(`@Scheduled(cron = "0 * * * * ?")`). За сутки освобождение случилось **один раз**, и в журнале записано:
«released wishlist 92e0209f… from finalizing **after 65 minutes**». При пределе в три минуты и обходе раз в
минуту освобождение обязано происходить на четвёртой минуте. Шестьдесят пять — это в двадцать один раз
больше.

*Почему так.* Возраст считается не от того, что охраняется. В коде прямо:
`Instant since = w.getLastCompileDispatchedAt() != null ? … : w.getCreatedAt()`, и комментарий рядом честно
говорит, что это «the closest available referent for “when this claim was taken”». **Своей отметки у сторожа
нет:** в `WishlistEntity` есть `createdAt`, `lastCompileDispatchedAt`, `lastCompileReachedAt` — и ни одной,
означающей «когда статус стал `finalizing`».

*Две версии, и различить их сегодня нечем — это и есть находка.*
Первая: заявка вошла в `finalizing` недавно, но её `lastCompileDispatchedAt` был уже старым, поэтому сторож
снял охрану почти сразу после того, как её поставили, и напечатал «65 минут» про **чужую** величину.
Вторая: заявка правда простояла 65 минут, а сторож её не видел.
Наблюдение, которое их различит, **не существует в данных**: нет отметки смены статуса. Пока её нет, вопрос
неразрешим не по недостатку усердия, а по устройству.

*Философия — два образца, главный первый.*
`CAUSAL_PROCESS_TRACE` (D013): сильная форма требует причинной цепи — спусковой крючок, механизм, смена
состояния, следствие; слабая — «назван соседний симптом». Здесь возраст берётся у **соседней** отметки, а не
у той смены состояния, которую он должен мерить. Опровержение образца дословно: «попросить показать звено
между названной причиной и следствием»; звена нет.
`PRINCIPLED_INTEGRITY` (D012): сообщение «the transient guard outlived any work it could cover» утверждает
то, чего механизм знать не может — возраст **сторожа**. Буква правила выполнена (три минуты от чего-то
прошли), принцип — не снимать охрану, пока под ней идёт работа — не сохранён.

*Делать.*
1. **Завести отметку смены статуса** (`finalizingSince`), выставлять при переходе в `finalizing` и по ней
   считать возраст. Одна колонка закрывает и дефект, и неразрешимость.
2. **Сообщение обязано называть, что именно измерено.** Пока считается по `lastCompileDispatchedAt`, текст
   обязан говорить «с последней отправки компиляции», а не «сторож пережил работу».
3. **Предел вывести, а не назначить.** Комментарий обосновывает три минуты тем, что «plan parse takes < 2
   seconds», — это верное обоснование, но записано в комментарии, а не в замере. Считать наблюдаемую
   длительность и держать предел кратным ей.

*Заслон:* тест, ставящий заявке `finalizing` при заведомо старом `lastCompileDispatchedAt` и требующий, что
она **не** освобождается раньше срока, отсчитанного от смены статуса.

*Опровержение:* найти в журнале освобождение, где названное число минут совпадает с временем пребывания в
`finalizing`. Совпадает — значит референт верен и пункт снимается.

---

### 35. Производитель блокировки бьёт за секунды, освободитель ходит раз в час · `PERCEPTION_ACTION_LOOP` (D011) + закон 8 · **ЖИВОЙ ЗАТОР**

*Замер 2026-09-06, 02:42.* Фабрика стоит и говорит это сама: `systemStatus: idle_no_actionable_work`,
состояние `BLOCKED_BY_REVIEW`, причина «2 failing/conflicted review(s) exist». В `BLOCKED_BY_REVIEW` она с
**01:50:33** — пятьдесят две минуты. Очередь пуста, в работе ноль, сдано 25 из 29 и не двигается.

*Что повторяется.* С 01:50:59 каждые пятнадцать минут, пять раз подряд: «PR review fallback was already
attempted for task 028aa7e0… PR 983 **at this content revision**; automatic retry is …». Сторож ключуется по
хешу содержимого правки — и это **верно**: ревью старой ревизии не покрывает новую.

*Почему круг.* Производитель новых ревизий — **этот же цикл**. Комментарий в коде
(`JulesDispatchService:3263–3271`) говорит это прямо: «the producer of new revisions is this same loop, so
every round lifted the bound: a limit the limited thing can raise». Отказал — новой ревизии не появится —
значит и сторож не отопрётся никогда.

*Кто мог бы разомкнуть и почему не размыкает.* Тот же комментарий называет:
«Only `reconcileTaskStatusAgainstGitHubTruth` could write that conclusion down, and it runs hourly: **the
producer of the block fires in seconds, the releaser once an hour**». Замер подтверждает и уточняет: он
ходит по `cron 0 0 * * * ?`, **ходил в 02:00** — и этих двух задач не коснулся, разобрав другую (PR#205).
То есть даже часовой освободитель их не берёт: дело не только в частоте.

*Философия — два образца, главный первый.*
`PERCEPTION_ACTION_LOOP` (D011): сильная форма — у всякого действия есть воспринимаемая обратная связь,
**включая отказ**; слабая — «успех виден, отказ молчит». Здесь отказ не молчит, он печатается пятый раз
подряд — и **ни один механизм его не воспринимает**. Опровержение образца дословно: «вызвать отказ и
посмотреть, узнал ли о нём действующий». Никто не узнал.
**Закон 8 модели** (вариантная функция): повторять тождественный отказ каждые пятнадцать минут — это виток
цикла без убывающей величины. Пять витков, величина не убыла.

*Делать.*
1. **Отказ сторожа обязан быть событием, а не строкой.** Пятый тождественный отказ подряд по одной цели —
   это факт о заторе, и он обязан подниматься туда, где его кто-то читает.
2. **У ключа по хешу обязан быть выход.** Либо цель, чью ревизию некому изменить, выбывает из множества
   допуска окончательно (как уже сделано для закрытых-без-слияния), либо сторож обязан пропустить одну
   попытку по истечении срока — но тогда срок обязан быть выведен, а не назначен.
3. **Освободитель обязан ходить не реже производителя.** Час против секунд — это не запас прочности, это
   гарантия простоя длиной до часа при каждом таком случае.

*Заслон:* тест, дающий цели тождественный отказ дважды подряд и требующий, чтобы состояние проекта
изменилось — вышло из `BLOCKED_BY_REVIEW` либо подняло происшествие, — а не осталось прежним.

*Опровержение состоялось, и пункт смягчён (замер 2026-09-06 03:11).* `BLOCKED_BY_REVIEW` держался с
01:50:33 по **02:45:06 — 54 минуты**, снялся **без человека** и **не часовым освободителем**: в 02:00 и в
03:00 тот разбирал другую задачу (PR#205). Сняли потиковые сверщики — в 02:43:57 своду стало видно
`reviewTasksWithPr=0, queuedTasks=2`, и состояние ушло в `IMPLEMENTING`. Сдано стало 26 из 29.

Отсюда **пункт 3 «освободитель обязан ходить не реже производителя» снимается как поставленный не по адресу**:
путей освобождения несколько, потиковые работают, а часовой отвечает лишь за один частный вывод —
запись терминального исхода для PR, закрытого без слияния. Пункты 1 и 2 **остаются**: отказ сторожа
повторился пять раз, и его не воспринял никто; у ключа по хешу выхода по-прежнему нет.

---

### 36. Дизайн-цех выдаёт восемь экранов и все восемь отвергает сам · `TELEOSEMANTIC_FEEDBACK` (D011) + закон 8

*Сначала — моя ошибка, снятая через час.* В предписании 28 я написал, что число согласованности «никуда не
ведёт: порога нет, отказа нет». **Неверно.** Порог есть: `DesignConsistencyAuditService.MIN_TRACE_RATIO = 0.9`.
Отказ есть: `DesignAssetService:468` возвращает `DesignAssetResult(false, "aesthetic_drift", …)` с текстом
«Screen rejected: token_trace_ratio=… below required 0.90». Я дважды подряд судил об этом механизме, не
дочитав его до места, где он действует.

*Замер за жизнь контейнера.* Аудитов согласованности — **8**. Значения `traceRatio`: 0,079; 0,079; 0,090;
0,093; 0,095; 0,095; 0,095; 0,098 — **все до одного ниже одной десятой** при требуемых девяти десятых.
Отказов «Screen rejected» — **8 разных моментов** (строк в журнале 16: сообщение печатается **дважды** на
каждый отказ — мелочь, но она едва не дала мне вдвое завышенное число, и проверена сверкой по отметкам
времени). То есть отвергнуты **все восемь из восьми**.
`crossScreenJaccard = 1,0` во всех замерах.

*Что это значит, и это не про слабый механизм.* Аудит работает **правильно и строго**: он меряет, у него
жёсткий порог, он отказывает. Дизайн-система объявлена, экраны между собой согласованы полностью
(`Jaccard = 1,0`) — и расходятся с системой на девяносто процентов. **Порождающая сторона не способна
попасть в собственную дизайн-систему ни разу.**

*Дефект — в том, что 0 из 8 никем не замечено.* Отказ возвращается вызывающему как результат одной попытки;
доли принятых экранов не считает никто, в свод она не попадает, порога «если подряд отвергнуто N — это уже
не дрейф, а неспособность» нет. Оператор видит следствие («дизайн не нравится»), а не число 0/8.

*Философия — два образца, главный первый.*
`TELEOSEMANTIC_FEEDBACK` (D011): назначение цикла — выдать **принятый** экран, а не «выдать экран». Механизм,
считающий свои попытки, но не свою долю успеха, назначения не наблюдает. Опровержение: спросить у системы,
сколько экранов принято, — ответить нечем.
**Закон 8** (вариантная функция): восемь витков «породить — отвергнуть» без убывающей величины. Каждый виток
тратит внешний вызов Stitch, то есть бюджет закона 9.

*Что трогать нельзя.* Порог 0,9 и `CROSS_SCREEN_JACCARD_GATE` (D015) держатся **сильно** и снижать порог
ради зелёного — это подгонка заслона под предмет, прямо запрещённая моделью. Чинить надо генератор, а не
мерку.

*Делать.*
1. **Считать долю принятых экранов** и класть её в свод рядом с числителем доставки. 0 из 8 обязано быть
   видно без чтения логов.
2. **Подряд отвергнутое — отдельный род события.** Два отказа подряд по одной дизайн-системе означают не
   дрейф отдельного экрана, а неспособность генератора; это обязано подниматься, а не повторяться.
3. **Назвать, откуда берутся объявленные токены** и сверить с тем, что генератор вообще получает на вход:
   расхождение в девяносто процентов при полной согласованности экранов между собой указывает, что генератор
   работает по другому набору, а не «неаккуратно».

*Заслон:* тест, дающий аудиту экран с долей 0,5 и требующий отказа, и тест, требующий, чтобы доля принятых
экранов присутствовала в своде.

*Опровержение:* найти в журнале хоть один экран с `traceRatio ≥ 0,9`. Есть — пункт смягчается.

---

### 37. Числитель считает слияния и ни разу не спрашивает, зелен ли `main` · `CONSTRUCTIVE_PROOF_OBJECT` (D007)

*Два моих подозрения по дороге — оба опровергнуты замером, записаны чтобы не возвращаться.*
Первое: «фабрика не читает исход CI, а только определяет версию Java» — **неверно**.
`GitHubPullRequestService:1195` читает настоящие check-runs GitHub, и `AutoMergeService` смотрит на `ciStatus`.
Второе: «пустой список check-runs даёт „прошло“» — **неверно**. `evaluateCheckRuns:1234` на пустом списке
возвращает `"pending"` с текстом «No GitHub check-runs exist for the PR head», а не успех. Пустой квантор
здесь закрыт правильно — редкий случай, и его стоит помнить как образец.

*Что действительно отсутствует.* В своде потока нет **ни одного** поля о состоянии сборки на `main`. Замер —
полный перечень: `evidence` = compilingWishlist, duplicateContentDetected, failingReviews, mergedReviews,
openReviews, openSessions, pendingWishlist, qualityGateFailed, qualityGatePassed, systemStatus;
`counts` = activeTasks, blockedTasks, completeFeatures, decompositionComplete, doneTasks, failedTasks,
mergedDeliverables, queuedTasks, reviewTasks, totalDeliverables, totalFeatures. Ни `ci`, ни `build`, ни
`green`. CI проверяется **у каждого PR по отдельности**, а состояние ветки, в которую всё слито, не
спрашивается никогда.

*Почему это важно именно для числителя.* «Сдано 26 из 29» значит «слито 26 PR». Оно **не** значит «продукт
собирается». Требование клиента от 2026-08-28 (`status=converted_to_task`) утверждает обратное: «CI on main
has been red for every merge since 2026-08-26… 12 tests expecting 200 receive 401, 10 expecting 201 receive
401, 15 failures in total». **Верно ли это сегодня — я не мерил и не утверждаю.** Но проверить это фабрика
не может по устройству: величины «зелен ли main» у неё нет.

*Философия.* `CONSTRUCTIVE_PROOF_OBJECT` (D007): сильная форма требует предъявляемого объекта доказательства
у всякого утверждения о готовности. Слияние — доказательство того, что **изменение принято**, и оно ничего
не говорит о том, что **сборка проходит**. Числитель доставки опирается на первое и молчит о втором, поэтому
доля может расти, пока продукт не собирается.

*Делать.*
1. **Внести в свод состояние сборки `main`** — одно поле рядом с `mergedDeliverables`. Механизм чтения
   check-runs уже есть, добавлять нечего, кроме вызова по ветке.
2. **Доставку считать конъюнкцией**: слито И сборка зелена. Пока второго нет, писать честно «слито N»,
   а не «сдано N».
3. **Красный `main` обязан быть состоянием потока**, как `BLOCKED_BY_REVIEW`. Сейчас он не блокирует ничего.

*Заслон:* тест, требующий, чтобы при красной сборке `main` свод не показывал рост доставленного.

*Опровержение:* найти в ответе свода поле, отвечающее на вопрос «зелен ли main». Есть — пункт снимается.

---

### 38. Аккаунт, разжалованный отказами, не может вернуться: повышение требует успеха, которого ему не дадут · `BELIEF_UPDATE_LEDGER` (D007) + закон 8

*Вопрос оператора:* «почему фабрика использует теперь только один аккаунт?»

*Замер за сутки (журнал проекта, 42 отправки).* По аккаунтам: **`eneikdru` — 23 (55%)**,
`eneikcoworking-ctrl` — 8, `fivedmitr-sys` — 5, `dmitriieneik-rgb` — 2, а
**`EneikGroup`, `sixdmitrsix-ops`, `dmitrefrem-eneik` — по нулю**. У этих трёх сердцебиение застыло на
13:07:13, 13:07:23 и 13:08:05 предыдущего дня — то есть их последняя работа пришлась на шторм отказов.

*Отбор ни при чём — спросил сам диспетчер.* `/internal/gemini-observer/dispatch-eligibility-detail`
показывает по всем семи: `enabled=true`, ключ есть, статус `idle`, тег подходит, сессий сегодня 0–5 при
пределе 3–15, привязки к проекту нет. **Все семь пригодны прямо сейчас.**

*Дело в порядке.* Первый ключ сортировки в `lockNextJulesAccountWithCapacity` — это
**число отказов создания сессии, случившихся после последней принятой сессии этого аккаунта**. Комментарий
рядом говорит это прямо: «small for an account that sometimes accepts, **the whole run for one that does
not**». Дальше `LIMIT 1`.

*Отсюда круг.* Аккаунт, набравший серию отказов и **не получивший принятой сессии после неё**, несёт всю
серию как свой ранг, встаёт последним и не выбирается. Не выбираясь, он не может получить принятую сессию.
Счётчик не обнуляется никогда. **Разжалование одностороннее** — та же форма, что у выключенного аккаунта в
пункте 23, только не флагом, а порядком.

*Почему это не проявлялось раньше и проявилось сейчас.* При большой нагрузке хвост очереди всё равно
получает своё: ведущий занят, `LIMIT 1` отдаёт следующему. Но при **42 отправках в сутки** и трёх слотах у
каждого ведущий не занят никогда — и хвост не получает ни одного хода. Дефект дремлет при загрузке и
просыпается при простое.

*Философия — два образца, главный первый.*
`BELIEF_UPDATE_LEDGER` (D007): ранг аккаунта есть убеждение о нём, и сильная форма требует, чтобы убеждение
пересматривалось по свидетельству. Здесь свидетельство, способное улучшить ранг, **добывается только тем
действием, которое ранг и запрещает**. Пересмотр вверх невозможен по устройству. Опровержение: назвать путь,
которым ранг улучшается без внешнего вмешательства, — такого пути нет.
**Закон 8** (вариантная функция): у разжалованного аккаунта нет убывающей величины, ведущей обратно.

*Делать.*
1. **У серии отказов обязан быть срок.** Считать не «все отказы после последней удачи», а отказы за окно —
   тогда молчание само возвращает аккаунт в строй.
2. **Либо давать пробу принудительно.** Раз в N отправок брать самый разжалованный аккаунт вместо лучшего:
   одна проба стоит один вызов, а вечное разжалование стоит трети пула.
3. **Простой пула обязан быть виден.** Три аккаунта из семи с нулём отправок за сутки — это событие, а
   сейчас его не показывает ни свод, ни детектор застоя (то же, что пункт 28 «Опровергнутого» модели).

*Заслон:* тест, дающий аккаунту серию отказов без последующей удачи и требующий, что он **всё же** будет
выбран не позже чем через N попыток.

*Опровержение:* найти в журнале отправку на аккаунт, у которого после последней принятой сессии стоит
непустая серия отказов. Есть — пункт снимается.

*Проверка обещанной гипотезы, 2026-09-06 06:41 — выборка мала, вывода нет.* Я обещал сравнить долю отправок
на самый нагруженный аккаунт до и после возврата карточки. Замер: **до — 9 отправок, самый нагруженный 44%**
(`eneikdru` 4, `eneikcoworking-ctrl` 3, `dmitriieneik-rgb` 2); **после — 2 отправки, обе на
`dmitriieneik-rgb`, 100%**. Две отправки не доказывают ничего ни в одну сторону. Отмечу и то, что «до» за
сегодня (44%) само отличается от вчерашних 55% — величина гуляет. Порог для вывода: **не меньше тридцати
отправок после правки**.

---

### 39. Верёвка ТОС построена, соблюдается — и привязана не к тому концу
*(Оговорка: оператор словом «вытягивание» имел в виду не это, а то, что Jules сам берёт задачу. Разбор
того — пункт 40. Здесь речь о Drum-Buffer-Rope, и находка остаётся верной сама по себе.)* · `TELEOSEMANTIC_FEEDBACK` (D011) + `FALSIFICATION_HARNESS` (D008)

*Оператор указал, что механизм вытягивания должен был быть реализован. Он реализован.* Это
**Drum-Buffer-Rope**: `TocOptimizer.shouldAdmit(scenario, priority)` (строка 113), верёвка включается по
`ropeThrottlingActive = bufferSize >= maxBufferCapacity` (строка 81), а вход в систему идёт через
`TocSentinelService.startExecutionWithId` (строка 52), который при отказе возвращает токен со статусом
`THROTTLED`. Рядом стоят `ConstraintIdentificationService`, `BottleneckDetectionService`,
`BottleneckAwarePriorityService`, `TocExecutionGraph`, `DbrStatus`.

*И он соблюдается — единственным своим потребителем.* `AutoMergeService:164–168` берёт токен
`startExecution("AUTOMERGE_CYCLE", 40)`, проверяет `THROTTLED` и **выходит из цикла**. Это правильная форма,
и её трогать нельзя.

*Дефект — в том, к чему верёвка привязана.* Перемерено по всему дереву, без глобов, после вопроса оператора
«ты не перепутал механизмы?»: у `ContinuousOrchestrationService`, `ProjectFlowService`, `ClaimService` и
`JulesDispatchService` — **ноль** упоминаний `tocSentinel`/`TocOptimizer`/`DbrStatus`, **ноль** вызовов
`startExecution`, **ноль** `shouldAdmit`.

*Уточнение, важнее самого счёта.* ТОС всё же достаёт до конвейера — но не тем концом.
`BottleneckAwarePriorityService:44,82` читает `tocSentinelService.getCurrentConstraintName()` и вычисляет
**приоритет**; зовёт его `TechnicalLeadCompiler.computePriority` **при компиляции**. То есть сведения
тянущей системы используются, чтобы **упорядочить** работу, и ни разу — чтобы **ограничить** её выпуск.
Порядок без предела — это не вытягивание, это просто более умное проталкивание. То есть **отправка работы в Jules через
верёвку не проходит вовсе**. В ТОС верёвка связывает **выпуск новой работы** с темпом ограничения; здесь она
поставлена на **слияние**, то есть на выход, а вход остаётся неограниченным. Механизм построен верно и
подключён к противоположному концу конвейера.

*Замер работы верёвки.* Строк `START_EXECUTION` за жизнь контейнера — **187**, строк
«THROTTLED by DBR Rope» — **ноль**. Прибор проверен контрольным счётом: сами события входа он видит, значит
ноль означает «не душила ни разу», а не «нечем измерить».

*Связь с предписанием 38 — и здесь я обязан снять собственное преувеличение.* Я писал, что возврат карточки
заодно поправит распределение: ведущий станет занят чаще, `LIMIT 1` провалится к хвосту, разжалование
перестанет быть смертельным. Это **гипотеза, а не следствие**, и я подал её как следствие. Возврат карточки
решает **когда** выпускать; порядок решает **кому** отдать. Это два разных механизма и две разные болезни.
Проверю гипотезу замером: доля отправок на самый нагруженный аккаунт до и после возврата карточки. Пока не
проверена — распределение считать нечинёным, предписание 38 остаётся открытым.

*Философия — два образца, главный первый.*
`TELEOSEMANTIC_FEEDBACK` (D011): назначение верёвки — ограничивать **выпуск**. Привязанная к выходу, она
своего назначения не наблюдает и наблюдать не может, как бы верно ни была написана. Опровержение: показать
место, где решение о выпуске новой работы зависит от состояния буфера ограничения, — такого места нет.
`FALSIFICATION_HARNESS` (D008): ограничитель, ни разу не сработавший на 187 входах, **не может быть показан
работающим**. Он не опровергнут — он не проверен.

*Делать.*
1. **Провести отправку через верёвку.** `dispatchQueuedTasks` обязан брать токен и уважать `THROTTLED`
   так же, как это делает автослияние. Кода писать почти не нужно — образец уже есть в
   `AutoMergeService:164–168`.
2. **Буфер измерять по внешней ёмкости**, а не по внутренней очереди: ограничение фабрики — квота Jules, и
   `maxBufferCapacity` обязана выводиться из неё (`estimatedDailyCapacity` уже существует).
3. **Ноль срабатываний обязан быть виден.** Ограничитель, не сработавший ни разу, обязан сообщать о себе как
   непроверенный, а не молчать наравне с работающим.

*Заслон:* тест, наполняющий буфер выше `maxBufferCapacity` и требующий, чтобы **отправка** была отклонена,
а не только слияние.

*Опровержение:* найти вызов `startExecution` на пути отправки. Есть — пункт снимается.

---

### 40. Путь вытягивания есть, им никто не пользуется, и Jules им пользоваться не может · `TELEOSEMANTIC_FEEDBACK` (D011) + `RIGHTS_DUTIES_MATRIX` (D006)

*Вопрос оператора, поставленный точно:* работник должен **сам брать** задачу, а не получать её толчком.
Механизм для этого написан.

*Что есть.* `POST /api/tasks/claim` → `ClaimService.claim(accountId, capableTags)` →
`taskRepository.lockNextQueuedTask(capableTags)`; рядом `POST /api/projects/{id}/claim` → `claimForProject`
и `claimSpecificTask`. Это и есть вытягивание: свободный работник просит следующую задачу по своим ролям, и
блокировка строки отдаёт её ровно одному.

*Кто это зовёт.* **Только контроллеры** — `ClaimController:23` и `ProjectController:252`. Внутри фабрики
не зовёт никто. То есть путь работает лишь тогда, когда **кто-то снаружи постучится**.

*Замер за сутки.* В журнале проекта **все** строки `ClaimService` — это `releaseClaimToQueue`, возврат
притязания в очередь (13, 12, 10, 10, 4 раза по разным задачам). **Ни одного взятого притязания.** Путь
вытягивания за сутки не использован ни разу.

*И он не может быть использован Jules — по устройству.* Код говорит это сам
(`ClaimService:33–36`): «Claims made outside `JulesDispatchService` (`claim()`/`claimForProject()`/
`claimSpecificTask()`) **never get a `JulesSessionEntity`**». Jules — внешняя служба, к которой ходит
фабрика; постучаться к нам он не может. Значит вытягивание рассчитано на **другого** работника —
самостоятельного агента или человека, — и такого работника нет.

*Отсюда прямое следствие для вопроса «почему всё на одном аккаунте».* Раз никто не тянет, распределение
целиком лежит на **толкающем** пути: фабрика сама выбирает аккаунт запросом
`lockNextJulesAccountWithCapacity`, а его порядок односторонне разжалует отказавшего и не возвращает
(пункт 38). У выбора нет противовеса: некому взять задачу «мимо» порядка.

*Философия — два образца, главный первый.*
`TELEOSEMANTIC_FEEDBACK` (D011): назначение притязания — дать работнику взять работу по своей готовности.
Механизм, к которому за сутки не обратились ни разу, своего назначения не выполняет; и «он же есть» тут не
довод — реестр, ни разу не сработавший, по правилу модели считается неработающим.
`RIGHTS_DUTIES_MATRIX` (D006): у права «взять задачу» обязан быть носитель. Здесь право объявлено, а
носителя нет: единственный работник фабрики — Jules — этим правом воспользоваться не способен.

*Делать.*
1. **Решить, кто носитель права.** Либо появляется агент, который тянет (и тогда `claim` — его дверь), либо
   вытягивание объявляется мёртвым и убирается, а распределение чинится в толкающем пути (пункт 38).
   Держать неиспользуемую дверь открытой наружу без пароля — отдельный риск, см. пункт 24.
2. **Пока носителя нет — не называть это вытягиванием.** Фабрика толкает, и говорить надо так.
3. **Ноль притязаний за сутки обязан быть виден.** Сейчас это не показывает ни свод, ни детектор застоя.

*Заслон:* тест, требующий, что при непустой очереди и свободном аккаунте `claim` отдаёт задачу; и счётчик
взятых притязаний в своде.

*Опровержение:* найти в журнале за сутки хоть одно взятое притязание. Есть — пункт снимается.

---

### 41. Вытягивания нет вовсе: сорок часов и один сигнал, который не приходит · `PERCEPTION_ACTION_LOOP` (D011) + `CAUSAL_PROCESS_TRACE` (D013) · **корень пунктов 38, 39, 40**

*Живое, 7 сентября 2026 — возврат карточки выложен и срабатывает.* В журнале за 29 часов три строки
`consumed a unit - released the next dispatch`, то есть слияние действительно освобождает следующую раздачу
не дожидаясь тика. Это закрывает **часть** пункта: путь вытягивания теперь имеет живого производителя.
Само же вытягивание по-прежнему не наблюдается — доминирующая строка суток остаётся «dispatch not
authorized … there is nothing for it to act on», то есть очередь пуста, и тянуть нечего.

*Что такое вытягивание по Lean, чтобы не спорить о словах.* Не «кто просит работу», а **что разрешает её
произвести**: ниже по потоку потребили — карточка вернулась наверх — только тогда верхняя ступень
производит. Незавершённое ограничено числом карточек, и сигнал идёт **от потребителя вверх**. Толкание — это
когда ступень производит по расписанию или по прогнозу, независимо от того, потребили ли предыдущее.

*Замер, отвечающий на вопрос одним числом.* В `src/main` **сорок** аннотаций `@Scheduled` и **один**
событийный вход — вебхук GitHub. Каждая ступень просыпается по своим часам и спрашивает «есть ли что
делать»: оркестрация раз в 60 секунд, автослияние раз в 60 секунд, дизайн-цех раз в 5 минут, узкое место раз
в 5 минут, фальсификация по часам, чистка эпиков в 20 минут каждого часа, сверка с GitHub в начале часа.
Опрос с проверкой предусловий выглядит как вытягивание и им не является: **условие спрашивают часы, а не
потребление**.

*Единственное настоящее ребро вытягивания не работает.* Вебхук `POST /api/webhooks/github` действительно
устроен как сигнал снизу вверх: при `action=opened` он заводит запись ревью и **отправляет ревьюера**
(`julesDispatchService.dispatch(task, account, "REVIEWER")`). Но за всю жизнь контейнера строк со словом
`webhook` — **ноль**, а в самом коде стоят «Simulate PR Data extraction» и «For the test, we find a task
associated with this repo»: это заготовка, а не боевая проводка.

*Доказательство от противного, которое сильнее счёта строк.* Утверждать «GitHub к нам не стучится» по
отсутствию строк я не вправе — путь пишет в журнал только при сбое. Но фабрика **опрашивает** GitHub каждую
минуту: `[CI-SYNC]` и `[BRANCH-GC]` в каждом тике. Так делают ровно тогда, когда событий нет. Система с
работающими вебхуками не опрашивает источник шестьдесят раз в час.

*Что из этого следует для потока — три уже записанных пункта оказываются одной болезнью.*
**Пункт 38** (аккаунт разжалован навсегда): раз никто не тянет, аккаунт выбирает толкающий запрос, и его
порядок — единственный распределитель, без противовеса.
**Пункт 39** (верёвка ТОС на выходе): ограничитель выпуска поставлен на слияние, потому что выпуска как
управляемого события просто нет — есть тик.
**Пункт 40** (притязание никем не взято): дверь для тянущего работника открыта, а карточки, которая дала бы
ему право, не существует.

*И главное следствие для числителя.* Сигнал потребителя вверх не идёт **вообще**: слияние в `main` не
освобождает ничего и ничего не запускает; зелёная сборка не наблюдается (пункт 37). Значит незавершённое
ограничено не потреблением, а ёмкостью аккаунтов — это `CONWIP` по ресурсу, а не канбан по работе. При
загрузке разница незаметна; при простое, как сейчас, фабрика просто перестаёт производить, и никто не
замечает, потому что замечать положено потреблению.

*Философия — два образца, главный первый.*
`PERCEPTION_ACTION_LOOP` (D011): сильная форма требует воспринимаемой обратной связи у всякого действия.
Здесь не воспринимается **потребление** — самое главное событие потока. Опровержение: показать место, где
факт потребления клиентом освобождает право произвести следующее. Такого места нет.
`CAUSAL_PROCESS_TRACE` (D013): сильная форма требует причинной цепи «спусковой крючок → механизм → смена
состояния → следствие». Срабатывание таймера крючком не является: между ним и потреблением связи нет,
совпадение по времени — не причина.

*Делать.*
1. ~~**Завести карточку.**~~ **Сделано 2026-09-06.** Карточка возвращается в точке потребления —
   `AutoMergeService.recordSuccessfulMerge` зовёт `dispatchQueuedTasks(projectId)` сразу после того, как
   слитый PR отметил задачу сделанной. Нового предела не введено: число карточек уже задано ёмкостью
   аккаунта. Заслон — `LeanPullReleaseTest`, счётное утверждение по множеству точек потребления.
2. **Включить вебхук по-настоящему** либо честно записать, что фабрика опросная, и убрать заготовку. Сейчас
   она создаёт видимость события, которого нет.
3. **Считать незавершённое по работе, а не по ресурсу**, и показывать его в своде рядом с числителем.

*Заслон:* тест, требующий, что при полном отсутствии таймерных тиков одно завершённое слияние **само**
приводит к выпуску следующей задачи.

*Опровержение:* найти в коде путь, где событие завершения работы освобождает право на выпуск новой. Есть —
пункт снимается.

---

## Задачи для кодинга, 42–59 (заведены 7 сентября 2026)

Выведены из слабых форм образцов, найденных при описании механизмов. Правило то же, что у пунктов 1–41:
названы механизм, место в коде, чем проверяется исправление и **что его опровергнет**. Оператора в системе
нет, поэтому пункты, прежде отложенные «на решение человека», переведены в задачи или сняты как ненужные —
это отмечено отдельно.

### 42. Неразобранный ответ модели становится утверждением «работа ценна» · `TRUTH_STATUS_TABLE` (D012)
**Механизм:** `LeanValue`, `JulesDispatchService.parseLeanValue:4492-4498`.
**Что не так:** `catch (IllegalArgumentException) { return LeanValue.valuable; }` — неизвестное приводится к
утвердительному значению. Молчание модели и её ошибка неотличимы от одобрения. Замер: восемь из девяти
писателей пишут `essential`, `waste` не пишет ни один механизм, а оба места, где значение решает, сравнивают
со **строкой**, а не со значением типа (`BaseQualityGate:25`, `JulesDispatchService:4406`).
**Чинить:** неизвестное обязано быть представимым и не совпадать ни с одним утвердительным исходом.
**Проверка:** подать неразбираемое значение и убедиться, что заслон муды не пропускает работу как ценную.
**Опровергнет:** вызывающий, который компилируется, не обработав неизвестное.

### 43. У цели задачи нет значения «не установлено» · `TRUTH_STATUS_TABLE` (D012) · закон 2
**Механизм:** `TargetContext`; `TaskEntity:102,391`, `WishlistEntity:69,400`, `JulesDispatchService:868`.
**Что не так:** «неизвестно» уничтожено трижды — инициализатором поля, геттером, гасящим `null`, и читающим,
приравнивающим `null` к продукту заказчика. Требование, цель которого **никогда не измеряли**, неотличимо от
требования, у которого её измерили. Полем решается, **в чей репозиторий уйдёт задача**
(`JulesDispatchService:601,604`).
**Чинить:** сделать «не установлено» представимым и заставить читающего его обработать.
**Проверка:** задача с неустановленной целью не должна раздаваться ни в один репозиторий.
**Опровергнет:** путь, на котором неустановленная цель молча становится продуктом заказчика.

### 44. Доступность аккаунта — конъюнкция, а тип знает один конъюнкт · `TRUTH_STATUS_TABLE` (D012)
**Механизм:** `AccountStatus`; `lockNextJulesAccountWithCapacity`, `AccountHealthService.recoverEligibleAccounts`.
**Что не так:** доступность есть `status` **и** `enabled`, но «отключён» не является состоянием аккаунта.
5 сентября пять аккаунтов имели `idle` при `enabled = false`; выбор отбрасывал их первым условием молча, а
восстановление их не видит вовсе (`findByStatusAndEnabledTrue`). Живое 7 сентября: у всех `enabled = true`,
то есть дефект не проявлен, но устройство не изменилось.
**Чинить:** отказ обязан называть сработавшее условие; недоступность по любому конъюнкту должна быть видна
одним чтением.
**Проверка:** отключить аккаунт и убедиться, что свод называет причину его отсутствия в пуле.
**Опровергнет:** отказ выбора, не называющий условия.

### 45. Поле корневой причины дефекта не заполняется никогда · `INUS_FACTOR_CHECK` (D007)
**Механизм:** `DefectJournalEntity.rootCausePatternId`; `AutoMergeService:2650` передаёт `null` явно,
`ProjectFlowService:424,515` пользуются коротким конструктором без этого поля.
**Что не так:** поле заведено, чтобы Парето считался **по причине**, а не по тому, какая подсистема заметила
симптом. Оно пусто всегда, и `ProcessControlService:357` сам это записывает в свою же выдачу.
**Чинить:** заполнять причину там, где она известна на месте записи.
**Проверка:** доля дефектов с непустой причиной перестаёт быть нулевой.
**Опровергнет:** разбор по Парето, который по-прежнему называет самую громкую подсистему, а не частую причину.

### 46. Узкое место фабрики определяется по единственному размеченному шагу · `INUS_FACTOR_CHECK` (D007)
**Механизм:** `TocNode`, `TocExecutionGraph:27`, `TocOptimizer:44-58`; разметка — только
`AutoMergeService:164,170,173,174` (сценарий `AUTOMERGE_CYCLE`).
**Что не так:** узлы заводятся лениво при первом входе, а входят из одного места. Множество кандидатов равно
множеству размеченного, поэтому «главное ограничение» предопределено датчиком, а не найдено сравнением. При
этом имя ограничения читают пятеро, и `BottleneckAwarePriorityService:44,82` **расставляет по нему приоритеты
работ**. Контроль: аспектов и перехватчиков в `src/main` нет ни одного.
**Чинить:** разметить остальные шаги потока либо перестать называть результат узким местом фабрики.
**Проверка:** в графе больше одного узла с ненулевой историей.
**Опровергнет:** прогон, в котором ограничением назван шаг, отличный от размеченного.

### 47. Память барабана-буфера-верёвки живёт только в оперативной · `PERSISTENCE_SNAPSHOT` (D010)
**Механизм:** `TocNode`, `TocEdge`; замер `grep -rln "@Entity" toc/` даёт ноль (контроль: тот же греп
находит `TaskEntity` в 55 файлах).
**Что не так:** среднее и разброс по Уэлфорду обнуляются при каждом перезапуске, и первое решение после
перезапуска принимается по выборке из одного наблюдения. Сравнить: `V72` завела долговечный ряд именно
затем, чтобы границы вычислялись из истории.
**Чинить:** сохранять ряд так же, как это сделано для контрольной карты.
**Проверка:** после перезапуска границы вычисляются из прежней истории, а не с нуля.
**Опровергнет:** решение об ограничении, принятое по выборке из одного наблюдения.

### 48. Счёт связности считается каждые два часа и не читается ни для одного решения · `TELEOSEMANTIC_FEEDBACK` (D011)
**Механизм:** `V80__coherence_runs.sql`, `EvidenceCoherenceService.periodicCoherenceCycle`.
**Что не так:** счёт объявлен «внешним, не самоотчётным якорем» против доверия к словам модели. Живое
7 сентября: `COHERENCE_RUNS` 52, `COHERENCE_RUN_NODE_RESULTS` **9884**, а единственное упоминание
`coherence_score` вне пакета — комментарий в `MLPredictionServiceClient:303`. Названный будущий потребитель
выключен `V111`.
**Чинить:** привязать счёт к решению — либо к прекращению цикла, либо к заслону готовности.
**Проверка:** назвать действие, которое изменил бы счёт.
**Опровергнет:** сигнал без читателя.

### 49. Сверка применённых миграций отключена дважды · `FALSIFICATION_HARNESS` (D008)
**Механизм:** `EneikProductionApplication.flywayMigrationStrategy` (`repair()` перед каждым `migrate()`) и
`src/main/resources/application.properties:55` (`spring.flyway.validate-on-migrate=false`).
**Что не так:** изменение уже применённого файла миграции не может остановить запуск. Это не значит, что
такое происходило; это значит, что заметить было бы нечем. Файлов миграций 137, повторяемых — ноль.
**Чинить:** оставить `repair()` только там, где он нужен, и вернуть сверку.
**Проверка:** правка применённой миграции роняет запуск.
**Опровергнет:** запуск, прошедший при несовпадении контрольной суммы.

### 50. Запрет на перезапись конечного состояния не оставляет следа · `INSTITUTIONAL_FACT_REGISTER` (D007)
**Механизм:** `TaskStatus.isTerminal`, `TaskEntity.setStatus:175-181`.
**Что не так:** правило исполнимо и объяснимо — исключение называет задачу и оба состояния, — но записи о
применении правила нет ни в журнале дефектов, ни в событиях проекта. Правило есть, регистра нет.
**Чинить:** записывать сработавший запрет как событие.
**Проверка:** попытка перезаписи оставляет строку, по которой её можно найти.
**Опровергнет:** сработавший запрет, не оставивший следа.

### 51. Притязание на цикл дизайна не истекает и не выметается · `BOUNDARY_TOPOLOGY` (D006)
**Механизм:** `DesignShopCycleRepository.claimStartCycle` (сравнение-с-обменом),
`releaseStartCycleClaim` (безусловное).
**Что не так:** ни срока, ни выметающего обхода. Контроль: у требований такой обход есть —
`StrandedFinalizingSweepService`.
**Чинить:** дать притязанию срок и обход, как у требований.
**Проверка:** брошенное притязание освобождается без вмешательства.
**Опровергнет:** цикл, застрявший в притязании дольше названного срока.

### 52. Два внутренних входа отвечают отказом · `FALSIFICATION_HARNESS` (D008)
**Механизм:** `InternalGeminiObserverController` — `/dispatch-capacity-probe` и `/persistent-workers`.
**Что не так:** живое 7 сентября — `HTTP 500`, «An unexpected error occurred», при том что `/db-table-sizes`
на том же контроллере отвечает исправно. Поверхность разбора у выключенного механизма сломана, и этого никто
не заметил.
**Чинить:** починить или снять оба входа.
**Проверка:** оба отвечают либо отсутствуют.
**Опровергнет:** вход, дающий 500 в обычной работе.

### 53. Описание механизма пережило удаление читателя · `CONVERSATION_MAXIM` (D007)
**Механизм:** `LogScopeBuffer`, javadoc класса.
**Что не так:** javadoc утверждает, что буфер читает цикл фальсификации. Замер: единственный внешний
читатель — `ProjectController:315`, отладочная выдача для человека; чтение из цикла изъято 9 августа.
**Чинить:** привести описание в соответствие с замером.
**Проверка:** имена в javadoc совпадают с найденными грепом читателями.
**Опровергнет:** ещё одно описание, называющее несуществующего читателя.

### 54. Тождество задачи компилятора введено только вперёд · `PERSISTENCE_SNAPSHOT` (D010)
**Механизм:** `V137__compiler_task_identity_from_work.sql`, столбец `tasks.content_key`.
**Что не так:** строки до миграции ключа не несут, поэтому задача, заведённая до неё, и та же работа после
не опознаются как одна. Сама миграция это признаёт.
**Чинить:** дозаполнить ключ для прежних строк там, где работа выводима.
**Проверка:** повтор по старой работе находит прежнюю строку.
**Опровергнет:** дубль, созданный по работе, у которой ключ выводим.

### 55. Заявление о сохранении поведения не подкреплено проверкой · `SUBSTITUTION_ORACLE` (D009)
**Механизм:** `V62__add_context_chunk_content_hash.sql`, `GeminiContextService.reindexStandingKnowledge`.
**Что не так:** объявлено «real cost, zero behavior change for anything that reads the chunks» — наблюдение
названо верно, доказательства сохранения под ним нет.
**Чинить:** тест, сравнивающий выдачу выборки до и после пропуска по хешу.
**Проверка:** тест краснеет, если пропуск меняет выдачу.
**Опровергнет:** различие в выдаче при совпавших хешах.

### 56. Прежнее суждение не хранится · `BELIEF_UPDATE_LEDGER` (D007)
**Механизм:** `Judgement`, `VerdictReconciliation.reconcile`.
**Что не так:** поле `evidence` несёт, на чём суждение стоит, но суждения строятся заново на каждый запрос,
и сравнить «во что верили час назад» не с чем. Оставшаяся неопределённость при этом считается —
`outstandingByLayer`.
**Чинить:** хранить прежнее суждение вместе со свидетельством, изменившим его.
**Проверка:** по любому утверждению видно, что и когда его изменило.
**Опровергнет:** вердикт, изменившийся без записи о причине.

### 57. У проекта нет состояния «поток встал сам» · `TRUTH_STATUS_TABLE` (D012)
**Механизм:** `ProjectStatus`, `ProjectEntity:97` (умолчание `active`).
**Что не так:** `SYSTEM_STALLED` встречается во всём `main` **только в комментариях**; ближайшее значение
`frozen` есть решение, а не обнаруженный затор. Проект, стоящий сорок часов, читается как `active`
двадцатью восемью читающими.
**Чинить:** представить обнаруженный затор состоянием, а не только телеметрией.
**Проверка:** стоящий проект отличим от идущего одним чтением состояния.
**Опровергнет:** читающий, для которого стоящий проект неотличим от идущего.

### 58. Живое значение флага наблюдателя расходится с миграцией · `ANTI_MIRROR_TELEMETRY` (D013)
**Механизм:** `gemini_project_observer_enabled`; `V111` вставляет `'false'` и является последней из двух
миграций, трогающих ключ; живой замер 7 сентября даёт `true`.
**Что не так:** причина расхождения **не установлена**. Две гипотезы: значение переписали после `V111` через
изменяющий путь настроек, либо поле `enabled` в ответе не отражает хранимое значение.
**Чинить:** сперва установить, потом чинить — прямое чтение строки `system_settings` по ключу.
**Проверка:** хранимое значение и ответ входа совпадают.
**Опровергнет:** ключ, у которого они расходятся.

### 59. Пульт управления открыт без проверки полномочий · `BOUNDARY_TOPOLOGY` (D006) · **было решением человека, стало задачей**
**Механизм:** `InternalGeminiObserverController` по пути `/internal/gemini-observer` и прочие изменяющие
входы; пункт 24 и пункт 27 перечня.
**Что не так:** слово «internal» в пути принято за полномочие. У выключенного наблюдателя остались четыре
изменяющих запроса — снятие застрявшего работника, освобождение требования, обнуление дневных счётчиков — и
сохранение сессии.
**Почему это теперь задача:** оператора в системе нет, значит «решить, закрывать ли порт» решать некому.
Единственный исполнимый путь — проверка полномочий в коде.
**Чинить:** ввести проверку на изменяющих входах.
**Проверка:** изменяющий запрос без полномочия отвергается и называет причину.
**Опровергнет:** изменяющее действие, прошедшее без полномочия.

### Что задачами не является

Пять слабых форм относятся к **истории, уже исправленной**, и задач не порождают; они оставлены в записях
как свидетельство, а не как долг.

- `V132` — ошибка рода со сравнением по сгенерированному заголовку исправлена вперёд в
  `ProjectFlowService.areWishlistItemsSimilar`; что исправление применено, миграция утверждает, а я не
  проверял — это входит в задачу 45 как смежная проверка, а не отдельным пунктом.
- `V58` — изъятие журнала отменено `V61` на следующий день.
- `V19` — состав пула, заданный списком имён, есть свершившийся факт; переписывать историю нельзя, а живой
  пул сегодня из пяти действующих аккаунтов, все включены.
- `V21` — область файлов как предсказание, а не владение: покрыто существующим реестром `V69`, отдельной
  задачи не требует.
- `TocEdge` — отсутствие читателей у счётчика переходов следует из задачи 46 и исчезнет вместе с ней.

### Итог

Сорок один пункт, все со своим образцом, замером, заслоном и опровержением. Порядок работы задан
замером, а не вкусом: **пункты 1 и 2 первыми** — пока у заявок двадцать писателей, а у задач пятнадцать и ни
одного объявленного хозяина, всякая другая правка добавляет ребро в граф, который никто не проверяет.
Пункты 3 и 4 — только после них.

Три пункта предписывают **не делать**: 9, 12 и частично 3 — там, где действие в неверном порядке хуже
бездействия.

---

---

# XVII. Что здесь мертво или обездвижено

* **`GeminiProjectObserverService`** — выведен навсегда, заперт миграцией.
* **`ChessService`** — пустой файл в одну строку.
* **Ревью Gemini** — отключено директивой оператора после происшествия со стоимостью; всякий PR идёт в
  запасное ревью Jules. Строка лога «Gemini review unavailable» — след того решения, а не поломка.

Записи этого раздела намеренно без четырёх полей: у мёртвого механизма нет ни связей, ни ценности.

---

# XVIIб. Двадцать один механизм, остававшийся неназванным

Замер 2026-09-06: классов-механизмов в коде **149**, названо в этом файле было **128**. Ниже — оставшийся
двадцать один, чтобы перечень стал полным. Пятнадцать из них — читающие контроллеры: `GET`-методы, ноль
изменяющих, поток удержать не могут, поэтому разбираются одной записью. Шесть остальных — не контроллеры и
разобраны поимённо.

**`SchedulingConfig`** (32 строки) — задаёт пул потоков планировщика для всей фабрики.
*Связи:* вызывающих нет по имени; реализует `SchedulingConfigurer`, Spring подхватывает сам | им пользуются **все 40 `@Scheduled`**
*Ценность:* без объявленного пула таймеры делят поток по умолчанию и выстраиваются в очередь друг за другом
*Комментарий:* **ядро по влиянию, невидимое по вызовам.** Тридцать две строки, к которым никто не обращается по имени, определяют, сколько тиков фабрики идёт одновременно. Это же и предел, за которым сорок таймеров начинают мешать друг другу — прямо связано с пунктом 41
*Философия:* `PART_WHOLE_OWNERSHIP` (D004) — форма: **измерена 2026-09-06, дефекта нет**. Замер: `poolSize`
объявлен равным **10**, аннотаций `@Scheduled` в `src/main` — **41**. Отношение 41 к 10 тревожно на вид, но
насыщения не наблюдается: в журнале за всю жизнь контейнера видны потоки `scheduling-task-1 … 9`, десятый
не понадобился ни разу, а нагрузка распределена неровно (379 строк на самом занятом против 94 на самом
свободном). Владелец расписания объявлен явно и один. *Опровержение:* найти тик, чей период сдвинулся из-за
занятости всех десяти потоков; такого не найдено.
*Следствие для пункта 41, важное, чтобы не чинить не то:* сорок один таймер **не голодает**. Значит «сорок
часов» — не про нехватку ёмкости планировщика, а про род устройства: производство запускают часы, а не
потребление. Увеличение пула ничего не изменит

**`TocAnomalyDetector`** (328 строк) — ищет застрявшие токены и повторные входы в узел графа ТОС.
*Связи:* зовёт его один `TocSentinelService` | читает `TocExecutionGraph`
*Ценность:* застрявший токен есть работа, о которой все забыли; без обхода она не находится
*Комментарий:* **периферия.** Триста двадцать восемь строк на обнаружение аномалий в графе, который, по замеру пункта 41, обслуживает только цикл автослияния: отправка через него не идёт. Мощный детектор на узком участке
*Философия:* `PERCEPTION_ACTION_LOOP` (D011) — форма: **не мерено**; находит ли он что-нибудь и слышит ли его кто-то, замером не установлено

**`DefectJournalService`** (78 строк) — единственная точка записи дефекта фабрики о самой себе.
*Связи:* зовут четверо — `AutoMergeService`, `ProjectFlowService`, `KaizenService`, `OperationalTruthService` | пишет `DefectJournalRepository`
*Ценность:* дефект, никуда не записанный, повторится, и никто не узнает, что это повтор
*Комментарий:* **ядро по памяти.** Четыре вызывающих на всю фабрику — мало для механизма, который есть её память о собственных отказах: губернатор безымянного отказа пишет сюда, а десятки других мест не пишут никуда
*Философия:* `INSTITUTIONAL_FACT_REGISTER` (D007) — форма: **слабая**; реестр есть, но обязанность в него писать не объявлена нигде, и потому соблюдается выборочно

**`GithubConfig`** (43 строки) — токен, организация, адрес API, флаг включения GitHub.
*Связи:* читают `GithubAccessService` и `GitHubPullRequestService`
*Ценность:* один источник учётных данных вместо двух расходящихся
*Комментарий:* **периферия.** Форма верная: значения из настроек, потребителей двое, оба транспортные
*Философия:* `RIGID_API_REFERENT` (D001) — форма: **сильная**; адрес и организация заданы в одном месте

**`GlobalExceptionHandler`** (93 строки) — последний рубеж: непойманное исключение превращается в ответ.
*Связи:* Spring зовёт сам | пишет в журнал
*Ценность:* без него клиент видит стек, а фабрика молчит
*Комментарий:* **периферия, и полезная сверх задачи.** Именно он показал, что обрыв моего чтения через `head -c` порождает `ClientAbortException` — то есть он различает чужую ошибку и собственный след наблюдателя, если читать его внимательно
*Философия:* `PARACONSISTENT_QUARANTINE` (D012) — форма: **слабая**; всё непойманное сваливается в одну корзину, и `Broken pipe` от оборванного чтения неотличим по уровню от настоящего сбоя

**`WebConfig`** (33 строки) и **`GreetingMapper`** (37 строк) — CORS с раздачей статики и преобразование DTO.
*Связи:* `WebConfig` — Spring; `GreetingMapper` — один вызывающий
*Ценность:* панель оператора открывается; ответ имеет форму
*Комментарий:* **периферия.** Поток удержать не могут ни при каких условиях
*Философия:* `BOUNDARY_TOPOLOGY` (D006) у `WebConfig` — форма: **не мерено**; правила CORS не сверены с тем, что порт открыт наружу без пароля (пункт 24)

**Пятнадцать читающих контроллеров** — `ClientDeliveryController`, `CommandDashboardController`,
`DashboardController`, `HomeController`, `InternalJulesActivitiesProbeController`, `JulesMonitorController`,
`LinearSyncController`, `OperationalTruthController`, `QualityGateController`, `QualityMetricsController`,
`RoleRulesController`, `SystemAuditController`, `SystemDriftController`, `VerdictController` и уже
разобранный отдельно `SystemStatusController`.
*Связи:* каждый зовёт свой сервис свода; изменяющих методов **ноль** у всех
*Ценность:* окно в состояние фабрики для оператора и для внешних наблюдателей
*Комментарий:* **периферия по демаркации** — вопрос «может ли механизм удержать поток» даёт «нет» для каждого: они только читают. Но все они открыты наружу без пароля наравне с изменяющими, поэтому в пункт 24 входят как поверхность утечки сведений, а не как рычаг
*Философия:* `LEVEL_OF_ABSTRACTION_LOCK` (D010) — форма: **не мерено**; ни один из них не объявляет, что отдаёт проекцию, а не предмет, — та самая ошибка, на которой я попадался трижды

---

# XIX. Сайдкары: механизмы вне бэкенда

Три отдельных контейнера со своим кодом. До 2026-09-06 в файле были описаны только **клиенты** к ним со
стороны бэкенда: про дверь написано, про то, что за дверью, — ничего.

**`launcher.py`** (`runtime-launcher/`, 567 строк, `uvicorn launcher:app`, порт 8091) — поднимает и сносит
продукт клиента: `POST /launch` клонирует репозиторий и делает `docker compose up --build -d`,
`POST /teardown` — `docker compose down -v --remove-orphans`, плюс `/healthcheck` и `/fetch`.
*Связи:* зовёт его один `RuntimeLauncherClient` из бэкенда | держит **docker-сокет хоста**; его собственный
докстринг: «The **ONLY** component in the whole factory that ever holds the host docker socket».
*Ценность:* без него продукт клиента негде запустить и не на чем проверить, что он вообще стартует; на нём
стоит всё наблюдение за живым продуктом.
*Комментарий:* **ядро по власти, а не по потоку.** Поток он не держит — фабрика работает и без него, — но
он единственный, кто исполняет чужой код на хосте. Внутри есть аккуратности: `_bound_memory` ограничивает
память контейнеров клиента, потому что они **соседи** сборщику на том же хосте; `_resolve_topology`
переносит порты, чтобы продукт не занял 8080, где стоит сама фабрика. Оговорки верные и измеренные.
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци, `BARCAN-TAG-01 ACTUALIST-OBJECT`,
принцип топологии пространственно-временных границ, anchor *Parts and Places / formal ontology of boundaries
and spatial parts*. Сильная форма дословно: «**названа точка, где меняется владелец проверки, полномочия или
сохранения, и на неё есть тест**». Слабая: «граница „понятна из структуры пакетов“». Опровержение образца:
«**удалить проверку на границе; если ни один тест не покраснел, границы нет**».
**Форма: нарушена.** Удалять нечего: слов `auth`, `token`, `Authorization`, `verify`, `allowlist` в файле —
**ноль**. Замер снаружи: `POST http://<хост>:8091/healthcheck` отвечает **422**, то есть запрос **дошёл до
обработчика** и отвергнут лишь за форму тела, а не за отсутствие полномочий; контрольная проба —
несуществующий путь даёт **404**, значит прибор различает. Шесть тестов в `test_launcher.py` проверяют
перенос портов и предел памяти — **ни одного про границу**. То есть точка, где меняется владелец полномочий,
не названа и не заслонена, а за ней стоит docker-сокет хоста.
*Опровержение, назначенное вперёд:* закрыть порт 8091 либо ввести общий секрет и посмотреть, покраснеет ли
хоть один тест. Не покраснеет — границы по-прежнему нет, есть только новая привычка.

---

**`server.js`** (`judgment-proxy/`, 338 строк, `node server.js`, порт 8093) — посредник, через которого
фабрика получает **суждение**, когда своего основания у неё нет.
*Связи:* зовёт его `JudgmentAgentClient` из бэкенда | ходит в Gemini
(`generativelanguage.googleapis.com`, `temperature 0.1`, предел 300 токенов для схемы и 600 для текста,
список моделей с откатом), пишет и читает каталог `/shadow`: `inbox`, `verdicts`, `served`.
*Ценность:* одно место, где решается, чем считать наблюдение, — вместо суждения, размазанного по вызывающим.

*Комментарий:* **ядро по последствиям, и самая опасная вещь из описанных.** У него **три слоя отката**, и
третий подменяет суждение вычислением.

Первый слой — Gemini. Второй — **ручной вердикт**: запрос кладётся в `/shadow/inbox` под хешем от промпта и
схемы, и до **240 секунд** механизм опрашивает `/shadow/verdicts/<хеш>.json`, ожидая, что кто-то положит
туда ответ. Это честно: человек или иной судья вписывает вердикт, и он помечается временем в `served`.

Третий слой — `evaluateAutonomousFallback`, и здесь **вердикт изготавливается сопоставлением строк**. В
режиме схемы он всегда возвращает `ABSTAIN` с неизменной причиной: *«The observed status transition is an
expected consequence of active decomposition and delivery flow»* — готовое объяснение на любой случай. В
текстовом режиме, для классификатора сессий Jules: если в промпте встречается любое из слов `contradiction`,
`table`, `schema`, `rejected` — возвращается *«VERDICT: REASONED_BLOCKER — The session identified an
architectural or spec contradiction with the environment»*; иначе — *«VERDICT: PROGRESSING»*.

То есть утверждение о том, **что сессия обнаружила**, выводится из того, **какие слова стояли в вопросе**.
Это ровно тот дефект, который этот файл разыскивает у фабрики повсюду: **надпись принята за данные**, — но
здесь он стоит в том самом механизме, чья работа — отличать одно от другого.

*Философия:* `ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` (D009) — Алонзо Чёрч, `BARCAN-TAG-05
SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda calculus and Church's
thesis*. Сильная форма дословно: «**до замены** кода, зависимости, модели или схемы **доказано сохранение под
значимыми наблюдениями**». Слабая: «заменили, тесты зелёные». Опровержение образца: «назвать наблюдение, под
которым доказывалось сохранение».

**Форма: нарушена.** Замена произведена — сопоставление строк подставлено вместо суждения модели, — а
наблюдения, под которым доказывалось бы сохранение, не назвал никто. Хуже: замена **неотличима**. Слов
`source`, `provenance`, `origin`, `autonomous` в пути ответа — **ноль**; вердикт третьего слоя приходит
вызывающему в том же виде, что и вердикт Gemini или человека. Подстановка *salva veritate* требует, чтобы
замена сохраняла истинность в тех наблюдениях, которые важны; здесь она не сохраняет даже **различимость**.

*Что делать (это не предписание, а часть записи о механизме):* ответ обязан нести имя своего слоя — модель,
человек или автономный откат. Одно поле закрывает весь класс: тогда всякое суждение, построенное на вердикте,
сможет спросить, чем оно порождено, и `INSTITUTIONAL_FACT_REGISTER` наверху перестанет опираться на
неизвестное.

*Опровержение, назначенное вперёд:* остановить Gemini и не класть ручной вердикт, затем найти в фабрике
место, которое отличит полученный ответ от настоящего. Не найдётся — форма подтверждена окончательно.

---

**`PredictionService.py`** (`src/models/ml/`, 576 строк, `uvicorn PredictionService:app`, порт 8000) —
предсказывает узкое место, отвечает на вопросы помощника и выдаёт векторные представления.
*Связи:* зовёт его `MLPredictionServiceClient` из бэкенда | ходит в Gemini (73 упоминания в файле), читает
уставы ролей из смонтированного `/project/BARCAN-TAG-*_*.md` | своего хранилища нет.
*Ценность:* одно место, где числа о потоке превращаются в оценку риска, и одно — где текст превращается в
вектор для поиска по корпусу.

*Комментарий:* **периферия по потоку и ядро по доверию.** Поток он не держит: при его падении фабрика
работает. Но его ответ входит в суждение о заторе, а имя обещает больше, чем механизм делает.

Заголовок файла гласит **«Bayesian predictor»**. Замер: обучения нет — `fit`, `train`, `joblib`, `sklearn`,
`torch` встречаются дважды и не в предсказании; весов и данных нет вовсе. Внутри три разных вещи под одним
именем «предсказание»:
`predict_bottleneck` **спрашивает Gemini** («You are a Lean Six Sigma Delivery Manager AI…») и при любом
исключении падает на арифметику — `(wip_factor + time_factor) / 2`, порог `0.7`;
`predict_bottleneck_logistic` — рукописная логистика с коэффициентами `b0 = -2.0, b_wip = 2.0, b_time = 2.0`,
и комментарий рядом честно называет их оценкой;
оба возвращаются вызывающему рядом, как «основной» и «кандидат».

Байесовского здесь нет ничего: ни априорного распределения, ни свидетельства, ни пересмотра. Есть мнение
языковой модели, среднее двух отношений и три назначенные константы.

*Философия:* `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` (D007) — Айзек Леви, `BARCAN-TAG-04 MODAL-QUANTIFIER`,
принцип фиксации доксастических состояний, anchor *The Fixation of Belief and Its Undoing / Enterprise of
Knowledge*. Сильная форма дословно: «**записано, какое свидетельство изменило убеждение и какая
неопределённость осталась**; приложены уверенность до и после и неразрешённые гипотезы». Слабая: «новое
убеждение изложено без старого». Опровержение образца: «**спросить, во что агент верил час назад и что
именно это изменило**».

**Форма: нарушена.** На вопрос «во что верил час назад» ответить нечем: коэффициенты не менялись никогда и
меняться не могут — их никто не пересматривает по исходу. «Кандидат» рядом с «основным» выглядит как выбор
модели, но выбирать не из чего: обе величины назначены, ни одна не выведена, и сравнение их между собой
никакого свидетельства не даёт.

**И это тем заметнее, что в той же фабрике есть правильная форма того же образца.** `BetaPosterior` (112
строк) выводит предел из накопленного свидетельства и записан в этом файле как «самый чистый пример
правильной формы во всём коде». Один и тот же образец, две противоположные формы, оба механизма живы.
Разница ровно в том, что требует Леви: у одного есть запись, какое свидетельство что изменило, у другого —
константа.

*Опровержение, назначенное вперёд:* найти в фабрике место, где `b0`, `b_wip` или `b_time` меняются по
наблюдённому исходу. Нет такого — форма подтверждена окончательно.

---

# XX. Хранилища, которые держат поток

Из 46 хранилищ **10 несут собственные запросы**, остальные — простой CRUD и механизмами не являются по
демаркации «может ли механизм удержать поток». Замер: `@Query` по каждому файлу каталога `repositories`.
Пять из десяти уже описаны выше (`AccountRepository`, `TaskRepository`, `JulesSessionRepository`,
`ProjectRepository`, `WishlistRepository`); ниже — остальные.

**`DesignShopCycleRepository`** (34 строки) — держит **аренду цикла дизайн-цеха**: взять исключительное право
запустить цикл и отпустить его.
*Связи:* зовёт `DesignShopOrchestrationService` (`claimStartCycle` при входе в цикл, `releaseStartCycleClaim`
при неудаче генерации) | пишет `design_shop_cycle.start_cycle_claimed_at`.
*Ценность:* без неё два тика оркестрации могут войти в один цикл одновременно и оба потратят вызов Stitch.

*Комментарий:* **ядро для своего цеха, и взято правильно наполовину.** Притязание — настоящий
compare-and-swap: `UPDATE … SET start_cycle_claimed_at = :now WHERE project_id = :p AND
start_cycle_claimed_at IS NULL AND last_was_ready = false`, возвращает число затронутых строк, так что
вызывающий знает, выиграл он или нет. Условие `last_was_ready = false` перепроверяет решение о фронте
готовности **против базы** в момент взятия — защита от устаревшего чтения в памяти. Это верно и написано с
пониманием.

**Асимметрия в другой половине.** Освобождение безусловно: `UPDATE … SET start_cycle_claimed_at = NULL WHERE
project_id = :p` — без проверки, кто держит. Взятие защищено, отпускание нет: снять чужое притязание может
кто угодно.

**И у притязания нет срока.** Если процесс умер после взятия и до освобождения, поле остаётся заполненным
навсегда, а `claimStartCycle` требует `IS NULL` — значит цикл не запустится больше никогда. Замер: слово
`startCycleClaimedAt` встречается в `src/main` только в самой сущности и в комментарии сервиса; **сторожа,
чистящего застрявшее притязание по возрасту, нет**. Контрольная проба: для заявок такой сторож
существует — `StrandedFinalizingSweepService`, 8 упоминаний. То есть фабрика этот урок уже усвоила и здесь
не применила.

*Оговорка честности:* застряло ли притязание **сейчас**, я не мерил — прямого доступа к базе у меня нет.
Утверждаю лишь устройство: взятие с условием, отпускание без условия, срока нет, сторожа нет.

*Философия:* `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` (D004) — Джонатан Шаффер, `BARCAN-TAG-01
ACTUALIST-OBJECT`, принцип приоритетного монизма, anchor *Monism: The Priority of the Whole*. Сильная форма
дословно: «**до разделения модулей объявлено, какой агрегат вправе менять каждую часть**». Слабая: «классы
разделены по размеру или по слоям». Опровержение образца: «**найти поле, которое пишут два сервиса**».

**Форма: слабая по асимметрии.** Взятие объявляет владельца — это делает `WHERE … IS NULL`. Отпускание
владельца не спрашивает, поэтому право менять поле принадлежит всякому, кто знает `projectId`. По Шафферу
приоритет принадлежит **целому**, и часть определяется через него; здесь целое (цикл) объявлено при входе и
забыто при выходе, и часть остаётся без основания.

*Опровержение, назначенное вперёд:* добавить в освобождение условие на того же держателя и посмотреть,
покраснеет ли хоть один тест. Не покраснеет — владения не было, была привычка.

---

**`ClaimRepository`** (47 строк) — притязания на задачу: кто держит, кто отпустил, сколько истекло.
*Связи:* зовут пятеро — `ProjectFlowService`, `ClaimService`, `InternalTaskController`, `DashboardController`,
`BottleneckDetectionService` | пишет `claims`.
*Ценность:* без счёта истёкших притязаний здоровье аккаунта нечем мерить: `expiredCountByAccountSince`
группирует истечения по аккаунту за окно.

*Комментарий:* **ядро, и несёт в себе разобранное происшествие, которое стоит целого урока.** Запрос
`findByTaskIdAndReleasedAtIsNull` был выведенным (derived) и потому требовал, чтобы совпала **не более чем
одна** строка. Схема этого не гарантирует. Замер живьём на `test-fiftieth`: у задачи оказалось два
неотпущенных притязания, и `IncorrectResultSizeDataAccessException` дошло до `GlobalExceptionHandler`
**шесть раз за десять минут**, уронив панель оператора и сверщик, который вытаскивает застрявшие
`pr_opened`. Из десяти вызывающих **ни один** не спрашивал, ровно ли одно; все спрашивали, есть ли активное.
Правило, записанное в самом коде: **«запрос, чья кардинальность строже вопроса, на который он отвечает,
превращает обычные данные в аварию»**. Починено на `findFirst` с явным порядком — тотально и
детерминированно.

*Философия:* `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — Гилберт Райл, `BARCAN-TAG-00 CODE-GUARDIAN`,
принцип различия «знать что» и «знать как», anchor *The Concept of Mind — knowing-how versus knowing-that,
category mistakes*. Сильная форма дословно: «назван тип, схема или переходник, **удерживающий границу
рода**». Слабая: «мы понимаем разницу». Опровержение образца: «найти место, где **значение одного рода
присваивается полю другого без преобразования**».
**Форма: сильная после починки, и род ошибки назван точно.** Множественность строк присваивалась
`Optional` — значение одного рода в поле другого, без преобразования, ровно как требует опровержение. Райл
о том и писал: категориальная ошибка не в том, что утверждение ложно, а в том, что оно принадлежит не тому
роду. Здесь «сколько строк» подменяло «есть ли строка».

**`ProjectEventLogRepository`** (34 строки) — долговечный журнал проекта, переживающий пересоздание
контейнера.
*Связи:* зовут двое — `ProjectEventLogService` (пишет) и `ProjectEventLogRetentionService` (чистит) | пишет
`project_event_log`.
*Ценность:* это единственный журнал, по которому можно разобрать вчерашнее происшествие: контейнерный лог
исчезает при пересборке, а этот нет. Весь мой разбор семичасового простоя аккаунтов опирался на него.

*Комментарий:* **периферия по потоку, ядро по разбираемости.** В нём записано собственное происшествие:
таблица **не имела пути удаления вовсе**, писалась каждые 5 секунд пачками по 500 и росла без предела —
162 тысячи строк. Комментарий честно отделяет причину от совпадения: базу уронило не это (94% из 1,7 ГБ
файла составляли невозвращённые страницы MVStore из-за незакрытия), но «журнал только на дозапись без
политики есть вторая, медленная версия того же отказа».

*Философия:* `RELIABILITY_CHAIN` (D010). Сильная форма дословно: «данным верят только когда процесс их
добычи надёжен для **этого** класса дефекта; названы источник, отметка времени, **правило свежести** и путь
проверки». Слабая: «данные из базы, значит верны». Опровержение образца: «**назвать возраст значения**;
неизвестный возраст делает свежесть недоказуемой».
**Форма: сильная.** Источник назван (`logger`), отметка времени есть у каждой строки, правило свежести
введено — `deleteByProjectIdAndCreatedAtBefore` с порогом, и есть отдельный механизм, который его применяет.
Возраст любого значения называется одним запросом. Это редкий в этом файле случай, когда образец держится
целиком.

**`ContextChunkRepository`** (27 строк) и **`GreetingRepository`** (26 строк) — куски контекста для поиска и
остатки демонстрационного контура.
*Связи:* первый зовёт `GeminiContextService`, второй — только `GreetingController`.
*Ценность:* первый обеспечивает переиндексацию без хвостов: переиндексация есть удаление-и-вставка по
`sourceRef`, поэтому правка документа не оставляет обрывков прежней, более длинной версии. Второй считает
среднее время цикла по таблице `greetings` — величина из демонстрационного контура.
*Комментарий:* **периферия оба.** У первого форма верная и объявлена в комментарии; второй — единственный
известный мне механизм, чья ценность для нынешней фабрики не установлена: `greetings` к продукту клиента
отношения не имеет, а средним временем цикла по ней кто-то может воспользоваться как метрикой.
*Философия:* `PERSISTENCE_SNAPSHOT` (D010) у `ContextChunkRepository` — **сильная**: личность источника
сохраняется через `sourceRef`, и переиндексация не оставляет двух версий одного документа. У
`GreetingRepository` — **не мерено**: чтобы назвать форму, надо сперва установить, читает ли кто-нибудь эту
величину как метрику фабрики.

---

# XXI. Сущности, несущие поведение

Из 58 файлов каталога `models/persistence` **шесть** несут методы, не являющиеся аксессорами. Замер: подсчёт
публичных методов, чьё имя не начинается на `get`/`set`/`is`.

**Прежнее «трое из шести уже описаны (`TaskEntity`, `WishlistEntity`, `JulesSessionEntity`)» снято как
ложное** — замер 6 сентября: записи не было ни у одной из трёх. `TaskEntity` встречался в файле ровно один
раз, и это была та самая строка, утверждавшая, что он описан; `WishlistEntity` и `JulesSessionEntity` — по
одному упоминанию в чужой прозе. Это третий случай одной и той же подмены за день: упоминание имени принято
за разбор механизма. Все три описаны ниже.

**Прежнее «остальные — данные и механизмами по демаркации не являются» снято как ошибочное.** Признаком
механизма был взят метод, а перечисление методов не имеет — и девять перечислений, очерчивающих пространства
состояний, отсеклись признаком, к ним неприменимым. Они разобраны в разделе XXIб: тип, в котором нет
значения, делает соответствующее состояние невыразимым для всех своих читающих сразу, а это и есть удержание
или потеря потока. Данными остаются те файлы `models/persistence`, которые не несут ни методов, ни закрытого
пространства состояний.

**`TaskEntity`** (408 строк) — носитель единицы работы, и вместе с тем **место, где задан вопрос о доставке**:
не «отмечено ли сделанным», а «спросил ли кто-нибудь, сделано ли то, что заказывали».
*Связи:* `initializeStatus` зовут 5 механизмов (`MarketResearchService`, `OpsAuditorService`,
`PlannedWorkRecoveryService`, `ProjectFlowService`, `TechnicalLeadCompiler`); `carrierTaskType` — 2
(`DeliveryRealityProducerService`, `JulesDispatchService`); четыре предиката доставки читает
**`FlowSpineService`**, `isVerifiedForDelivery` — ещё и `GateOrchestrator`. Замер по
`grep -rlE "[.:]<имя>\b"`, включая ссылки на метод.
*Ценность:* без этих предикатов «сделано» — поле, а не свидетельство. С ними у свода есть чем отличить
проверенную доставку от неспрошенной.
*Комментарий:* **ядро.** Главное здесь — **три исхода разведены там, где обычно один**:
`deliveryRuledByCriteria()` — инструмент вынес суждение; `deliveryQuestionPutButUnsettled()` — вопрос был
задан и ответа не дал; `isDeliveryVerificationAbsent()` — не спрашивал никто. Javadoc называет причину
прямо: «a verdict field with no place to put „not measured“ starts reporting silence as a result», и
`UNDECIDABLE`/`NOT_JUDGED_NO_DIFF` намеренно исключены из «вынес суждение» как записанное незнание.

`isVerifiedForDelivery()` несёт замер, объясняющий, почему инструментов два: 28 августа на 365 задачах
заслон качества применился к **нулю**, а критериальный инструмент вынес суждение по 127 (82 удовлетворено,
45 опровергнуто). Заслон не слаб — он недостижим: стоит на одном из пяти путей, пишущих `TaskStatus.done`.
Там же снят подлог: массив `stages` записывает **запрошенные** стадии, а `allMatch` по пустому списку даёт
`true`, — то есть задача, для роли которой заслона нет, числилась прошедшей все проверки, когда не
применилась ни одна. Теперь отвечает счётчик применённых.

Единственная слабость по замеру: `deliveryRuledByCriteria()` вне самой сущности не зовёт никто — он
работает только изнутри, как отрицание в `deliveryQuestionPutButUnsettled()`. Предикат есть, спрашивать
его наружу пока некому.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“».
**Форма: сильная.** Неизвестное не просто представлено — оно **разделено надвое** (спросили и не решили /
не спрашивали), и javadoc отдельно запрещает считать их одним, потому что первое указывает на критерии, а
второе на охват. Опровержение выполнить не удалось: единственный внешний читающий, `FlowSpineService`,
зовёт все три предиката, а не один.
Второй образец: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007). Сильная дословно: «успешное завершение —
**значение**, которое не может существовать без выполненных предусловий, и оно несёт свидетельство для
следующего шага». Слабая: «статус `done` в поле и запись в лог». Опровержение: «сконструировать результат
успеха, не имея свидетельства; если это удаётся — форма слабая». **Форма: слабая, и опровержение
выполняется замером самой фабрики** — 365 задач достигли `done`, имея свидетельство от заслона в нуле
случаев. Предикаты читают свидетельство, но не мешают его отсутствию: `done` по-прежнему ставится
независимо от них.

**`WishlistEntity`** (416 строк) — носитель требования, и **место, где различены три причины, по которым
требование не стало задачами**.
*Связи:* `movable()` читают **6 механизмов** (`ClientDeliverableReadinessService`,
`ContinuousOrchestrationService`, `FlowSpineService`, `LaunchabilityConstraintService` и др.) —
самый читаемый предикат из всех измеренных; `decompositionRefused()` — 3 (`ProjectFlowService`,
`ClientDeliverableReadinessService`, `FlowSpineService`); `decompositionUnreached()` — 1,
`ProjectFlowService.restoreUnreachedBriefs:2596`, через ссылку на метод; `effectiveCompileCeiling()` — 1.
*Ценность:* без `movable()` каждый потребитель считает `pending`/`compiling` сам, и требование, на которое
компилятор ничего не ответил, вечно сидит во всех подсчётах сразу.
*Комментарий:* **ядро, и лучший образец различения во всём коде, что я мерил.** Бюджет попыток исчерпан —
дальше три разных факта, а не один:

- `decompositionRefused()` — бюджет потрачен **и компилятор был достигнут**: спросили, ответа не дали.
  Поглощающее состояние, из него не выходят.
- `decompositionUnreached()` — бюджет потрачен и компилятор **не был достигнут ни разу**: о требовании не
  установлено ничего, установлено лишь то, что фабрика была занята. Намеренно **не** поглощающее и
  намеренно **не** исключённое из знаменателей: требование, которого не читали, ещё может дойти до конца.
- `decompositionExhausted()` — общая часть обоих; наружу его не зовёт никто, он живёт внутри двух верхних.

Javadoc называет цену смешения поимённо: 28 августа шесть требований потратили весь бюджет, пока
единственный постоянный работник-компилятор был занят, — ничего не было отправлено, а фабрика заключила,
что «требование нуждается в человеке». Это утверждение о требовании, выведенное из свидетельства о фабрике.

Различение опирается на **два разных источника**, и это существеннее самого различения: `compileAttempts` —
показание самого отправителя, что он пытался; `lastCompileReachedAt` — запись канала о том, что требование
действительно дошло. Первое — рассказ о себе, второе — свидетельство со стороны.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы и опровержение — дословно в записи
`TaskEntity` выше. **Форма: сильная.** Третий исход не только представлен, но и разделён на два по признаку
«о чём это свидетельство», и показано, как каждый разрешается: `refused` — никак, он поглощающий; `unreached`
— возвратом бюджета в `restoreUnreachedBriefs`.
Второй образец: `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается
на собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер
снят; её отсутствие и есть нарушение». **Форма: сильная.** Требовать нечего: различение самопоказания и
записи канала **вшито в два разных столбца**, и предикат, стоящий на самопоказании, специально не
поглощающий — фабрике не позволено закрыть вопрос собственным рассказом о том, что она пыталась.

**`JulesSessionEntity`** (169 строк) — носитель одной попытки внешнего исполнителя над задачей.
*Связи:* `ACTIVE_STATUSES` и `isActive()` читает `JulesDispatchService` | пишется при заводке сессии;
`preUpdate` проставляет `updatedAt`.
*Ценность:* без единого определения «сессия ещё чем-то занята» каждый читающий называет своё подмножество
из шести состояний.
*Комментарий:* **периферия по радиусу, ядро по устройству.** Ценность вся в одной строке —
`ACTIVE_STATUSES = {running, queued, revising, pr_opened, stuck}` — и в её обосновании: 1 августа
(`test-fortieth`, PR#119) сессия оказалась вытеснена свежей переотправкой, не достигнув конечного
состояния, и её отзыв был свидетельством о вытесненной попытке. Вопрос «активна ли сессия» закрывает этот
случай и все прочие завершённые разом, **вместо перечисления одного состояния из шести** — то есть ровно
та же болезнь, что лечит `movable()` у требования, и то же лекарство: одно определение вместо десяти
подсчётов по месту.
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — **форма не мерена.** Единственный внешний
читающий один, и я не разбирал, действительно ли `JulesDispatchService` спрашивает `isActive()` везде, где
решает о вытеснении, или где-то всё ещё сравнивает состояние по имени. Пока это не проверено, называть
форму нельзя.

**`WishlistSource`** (104 строки, 11 значений) — перечисление источников, из которых требование вообще
может возникнуть.
*Связи:* читают **25 файлов** | ничего не пишет: это тип.
*Ценность:* это единственное место, где записано, **что вправе учредить требование**. Без него любой
механизм, наткнувшийся на непорядок, мог бы завести заявку.

*Комментарий:* **ядро, и перечислением его называть неверно — это политика допуска, отлитая в тип.** Каждое
значение снабжено обоснованием, и обоснования однородны: «bounded to exactly one dedup-guarded item per
project», «triggered only by a concrete verifiable fact», «never an invented improvement», «created only
from a structured triage record a Jules session already produced». То есть источник добавляется, только если
он опирается на проверяемый факт, ограничен одним пунктом и защищён от повтора.

Главное в нём — **чего в нём нет**. Значение `idle_generation` удалено по прямой директиве оператора
(«система сама придумывает улучшения… убрать. опасно»), и комментарий отдельно оговаривает: оно было мёртвым
кодом, ничем не производилось, и удалено **как постоянный запрет когда-либо это подключить**, а не как
уборка. Запрет теперь исполним: значения нет в типе, значит его нельзя ни назвать, ни собрать.

*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная форма
дословно: «запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет
записан в документе или комментарии». Опровержение образца: «**совершить запрещённое действие; если оно
прошло — запрета нет, есть пожелание**».
**Форма: сильная, и притом сильнейшим из возможных способов.** Совершить запрещённое здесь нельзя не потому,
что проверка отвергнет, а потому, что **выражение не соберётся**: удалённого значения не существует. По Разу
это и есть исключающая причина в чистом виде — не довод против самозаказа, который можно перевесить другим
доводом, а изъятие самой возможности его рассматривать. Ср. пункт 16 «Круг самозаказа»: там наблюдение о
доставке всё же попадает в поле рода «требование», и разница в том, что для **того** пути значение в типе
осталось.

**`EvidenceNodeEntity`** (117 строк) — узел свидетельства: чем подтверждено, что работа сделана.
*Связи:* читают **9 механизмов**, среди них `AutoMergeService`, `DefectJournalService`,
`FalsificationCycleService`, `KaizenService` | пишется через `EvidenceNodeRepository`.
*Ценность:* без узлов свидетельство остаётся рассказом: они дают предъявляемый предмет, на который можно
сослаться.
*Комментарий:* **ядро по доказательности.** Единственный метод не-аксессор — `sourceType()`, то есть узел
сам умеет назвать род своего источника. Это правильная форма: род свидетельства принадлежит свидетельству, а
не тому, кто его читает.
*Философия:* `CONSTRUCTIVE_PROOF_OBJECT` (D007) — **не мерено**. Чтобы назвать форму, надо проверить, всякое
ли утверждение о доставке несёт узел, а не только те, что его завели. Опровержение: найти задачу,
признанную доставленной, у которой узла свидетельства нет.

**`JulesConfigEntity`** (51 строка) — прежняя таблица настроек аккаунтов Jules, из которой миграция V19
перенесла данные в `accounts`.
*Связи:* читают двое | `preUpdate()` — единственный метод, обновляет отметку времени.
*Ценность:* сегодня — историческая: именно из неё V19 копировала поле `enabled`, и это одна из двух версий
происхождения выключенных аккаунтов, которую я разбирал.
*Комментарий:* **периферия.** Живая таблица пуста (`GET /api/jules-configs` отдаёт `[]`), но тип и
контроллер живы, а `JulesConfigController` **пишет** в неё.
*Философия:* `WORLD_VERSION_MAP` (D003) — **слабая**: два мира настроек аккаунта сосуществуют — старый
`jules_configs` и новый `accounts`, — и матрицы, объявляющей, какой из них истинный, нет. Опровержение:
записать значение в старый мир и посмотреть, изменится ли поведение диспетчера.

---

**`PrivacyFilter`** (`controllers/policy/`, 29 строк) — по собственному описанию «Regulatory and Security
filter for data transmission»: маскирует персональные данные перед выходом за границу и проверяет
полномочие по токену.
*Связи:* **вызывающих ноль**. Замер: `grep -rl "\bPrivacyFilter\b" src/main/java` минус собственный файл —
пусто. Контрольная проба: тем же способом `TocToken` даёт пять файлов, значит греп находит пользователей,
когда они есть.
*Ценность:* заявленная — не выпустить персональные данные наружу. Действительная — нулевая: механизм не
включён ни в один путь.

*Комментарий:* **ядро по заявке, ничто по действию, и это худший из встреченных случаев расхождения между
именем и делом.** Тело `maskData` заменяет значение **единственного ключа с буквальным именем `"pii"`** —
любые персональные данные под любым другим ключом проходят нетронутыми. `verifyKnowledge` сравнивает токен с
**зашитой строкой** `"VALID_KNOWLEDGE_TOKEN"`; комментарий рядом называет это «Simplified Second-Order
Knowledge check». Оба метода статические, оба недостижимы.

Опасность не в том, что он плох, а в том, что он **есть**: читающий код видит класс с таким именем и таким
описанием и заключает, что фильтрация персональных данных в фабрике существует. Ср. пункт 27, где javadoc
обещал ограничение по localhost, которого нет: та же форма, но здесь обещание несёт само имя класса.

*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная форма
дословно: «запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет
записан в документе или комментарии». Опровержение образца: «**совершить запрещённое действие; если оно
прошло — запрета нет, есть пожелание**».
**Форма: нарушена.** Запрещённое действие — выпустить персональные данные — проходит всегда, потому что
проверять нечему: ни один путь сюда не заходит. И это тем нагляднее, что **в той же фабрике тот же образец
держится сильнейшим способом**: `WishlistSource` изъял запрещённое значение из типа, и запрещённое стало
несобираемым. Один философ, один образец, два механизма — и вся разница между запретом и пожеланием.

**`AgencyApplication`** (13 строк) и **`EneikProductionApplication`** (81 строка) — точки входа.
*Связи:* первая делегирует `main` второй и объявлена в собственном комментарии как «compatibility launcher
kept for older docs/scripts»; вторая несёт `@SpringBootApplication` и настраивает бины.
*Ценность:* без второй фабрика не поднимается; первая сохраняет старые команды запуска работающими.
*Комментарий:* **периферия обе.** Форма верная и редкая: совместимость сделана **делегированием**, а не
копией, поэтому двух путей запуска с расходящимся поведением не возникает.
*Философия:* `ANCHOR_BOUND_NAME` (D001) — **сильная**: старое имя сохранено, а референт у него один и тот
же. Опровержение: найти различие в поведении между запуском через `AgencyApplication` и через
`EneikProductionApplication`; тело первой — единственный вызов второй, различия нет по построению.

**`TocToken`**, **`TocNode`**, **`TocEdge`**, **`AnomalyReport`**, **`DbrStatus`** (`toc/model`, 145 / 146 /
33 / 24 / 18 строк) — граф исполнения ТОС: токен проходит узлы, узел копит наблюдения.
*Связи:* `TocToken` читают 5 файлов | `TocNode.recordExecution(durationNanos, success)` копит длительности,
`incrementInFlight`/`decrementInFlight` считают незавершённое, `hasObservedDuration` отвечает, есть ли
наблюдение вообще.
*Ценность:* это единственное место, где у фабрики есть **наблюдённая** длительность шага, а не назначенная.
*Комментарий:* **периферия, и устроена лучше, чем то, что её использует.** `hasObservedDuration` —
образцовая мелочь: узел различает «длительность ноль» и «наблюдений не было», то есть третий исход не
схлопнут (ср. `OnboardingAuditService`, где схлопнут). Методы `pushNode`/`popNode` синхронизированы, счёт
незавершённого атомарен. Беда не в графе, а в том, что через него, по замеру пункта 41, ходит только цикл
автослияния.
*Философия:* `TRUTH_STATUS_TABLE` (D012) у `TocNode` — **сильная** в части `hasObservedDuration`: неизвестное
представлено явно и отдельно от нуля. Опровержение: найти вызывающего, который читает среднюю длительность,
не спросив `hasObservedDuration`.

---

**`KaizenProposal`** (129 строк), **`KaizenProposalEntity`** (146) и **`DefectJournalEntity`** (106) —
предложение об улучшении в доменном виде, оно же в хранимом, и запись о дефекте.
*Связи:* `KaizenProposal` читают 5 файлов, `KaizenProposalEntity` — 3, `DefectJournalEntity` — 9 |
`KaizenProposalEntity.fromDomain(KaizenProposal)` и `toDomain()` — единственные не-аксессоры во всей тройке.
*Ценность:* без раздельных видов доменное решение и способ его хранения срастаются, и всякая правка схемы
становится правкой решения.

*Комментарий:* **периферия, и форма правильная.** Два представления одного предмета обычно означают два
источника истины; здесь — не означают, потому что **переход между ними назван и написан явно**: одна пара
методов, оба направления, ни одного места, где поля перекладываются вручную. Это ровно то, чего не хватает
`JulesConfigEntity`, где два мира настроек аккаунта сосуществуют без объявленного перехода.

*Философия:* `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — Дэвид Чалмерс, `BARCAN-TAG-02
RIGID-DESIGNATOR`, принцип двумерной семантики, anchor *Two-Dimensional Semantics — primary and secondary
intensions*. Сильная форма дословно: «отображаемое имя, сохраняемый идентификатор и сущность в API
**разведены так, что перепутать их нельзя**». Слабая: «одно поле служит всем трём». Опровержение образца:
«изменить отображаемое имя и посмотреть, не поехали ли ссылки».
**Форма: сильная.** У Чалмерса первичная интенсия — то, как предмет дан, вторичная — что он есть; здесь это
разведено буквально: доменный вид отвечает за смысл, хранимый — за ссылку, и переход между ними
единственный. Опровержение неисполнимо по построению: поменять хранимое поле, не пройдя через `toDomain`,
негде.

**Тридцать простых хранилищ** — `AccountRoleSuccessStatsRepository`, `CapabilityObservationRepository`,
`ClientAcceptanceTraversalRepository`, `ClientRuntimeObservationRepository`, `CodeIntegrityFindingRepository`,
`CoherenceRunNodeResultRepository`, `CoherenceRunRepository`, `FalsificationRunRepository`,
`FeatureThreadRepository`, `FlowSpineEventRepository`, `GeminiFindingRepository`,
`GeminiObserverActionRepository`, `GeminiObserverJournalRepository`, `InvariantStatusChangeRepository`,
`JulesActivityResponseRepository`, `JulesConfigRepository`, `LeverPromotionStateRepository`,
`LinearIssueMetadataRepository`, `OnboardingAuditFindingRepository`, `OperationalRealityFindingRepository`,
`PersistentWorkerSessionRepository`, `PrReviewRepository`, `ProcessControlSnapshotRepository`,
`ProjectFileClaimRepository`, `ProjectFinalReportRepository`, `ProjectGenerationStateRepository`,
`ReviewConcernRepository`, `RoleRepository`, `TrustSignalSnapshotRepository`, `WishlistItemRepository`.
*Связи:* каждое — производные методы Spring Data поверх одной таблицы; **собственных запросов нет ни у
одного**. Замер: `@Query` не встречается ни в одном из тридцати файлов; контрольная проба — у
`AccountRepository` тот же греп даёт 15, значит греп находит запросы, когда они есть.
*Ценность:* доступ к таблице без рукописного SQL, то есть без второго места, где живёт схема.
*Комментарий:* **не механизмы по демаркации.** На вопрос «может ли механизм удержать поток» каждое отвечает
«нет»: они не решают, они достают. Решение живёт у вызывающего, и именно там оно и разбирается в этом файле.
Названы поимённо, чтобы перечень был полон и чтобы появление `@Query` в любом из них было заметно как
изменение рода.
*Философия:* не применяется. Образец предполагает решение, а здесь решения нет; приписать образец складу
значило бы сделать ровно то, за что этот файл ругает механизмы фабрики — назвать больше, чем есть.

---

# XXIб. Девять пространств состояний: где у фабрики нет слова «неизвестно»

Девять перечислений в `models/persistence` не хранят данные — они **очерчивают, какие состояния вообще
мыслимы**. Значения в типе нет — состояние невыразимо; ни один механизм не сможет его ни назвать, ни
записать, ни на него отреагировать. Это делает их механизмами по демаркации «может ли механизм удержать
поток»: пространство состояний держит поток тем, что не даёт ему принять состояние, которого не бывает, и
роняет его там, где нужного состояния не предусмотрели.

**Общий замер, один на весь раздел.** Прогон по всем 13 перечислениям каталога `models` на значение со
смыслом «неизвестно» (`unknown|unspecified|indeterminate|undetermined|not_measured`):

```
for f in $(grep -rl "^public enum" --include=*.java models/); do … done
→ ЯВНОЕ НЕИЗВЕСТНО: (ни одного)
--- всего enum в models/: 13
```

Контрольная проба обязательна, и она здесь спасла замер: первая версия грепа с якорем `^\s*` вернула ноль
и на контрольном значении `decommissioned`, которое в файле есть, — значения перечисляются в одну строку,
и якорь их не видел. Инструмент был слеп, отрицательный ответ был бы ложным. После снятия якоря контроль
находит `AccountStatus.java`, и только тогда ноль стал замером.

**Ноль из тринадцати.** Ни одно пространство состояний фабрики не умеет сказать «я не знаю». Дальше — что
это стоило, поимённо.

---

**`TargetContext`** (2 значения) — единственное место, где фабрика отличает работу над продуктом заказчика
от работы над самой собой.
*Связи:* читают `JulesDispatchService:601,604` (**выбирает имя и URL репозитория, куда уйдёт задача**),
`JulesDispatchService:868`, `PlannedWorkRecoveryService:533`, `TechnicalLeadCompiler:348` (переносит с
требования на задачу) | пишет `ORCHESTRATOR_SYSTEM` **ровно один механизм на весь `main`** —
`MarketResearchService:77`.
*Ценность:* на этом поле целиком держится закон 2 (изоляция носителя). Без него нет вопроса «в чей
репозиторий», есть один репозиторий.
*Комментарий:* **ядро.** И одновременно — место, где «неизвестно» уничтожено трижды подряд:

1. инициализатор поля: `TaskEntity:102` и `WishlistEntity:69` — `= TargetContext.PRODUCT_CODEBASE`;
2. геттер гасит `null`: `TaskEntity:391`, `WishlistEntity:400` — `targetContext == null ? PRODUCT_CODEBASE`;
3. читающий приравнивает `null` к продукту: `JulesDispatchService:868` — `== null || == PRODUCT_CODEBASE`.

Требование, у которого цель **никогда не измеряли**, неотличимо от требования, у которого её измерили и
получили «продукт заказчика». Причём вызывающий не просто не обрабатывает «неизвестно» — он **не может**:
геттер физически не способен его вернуть.

*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная форма дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение образца: «**найти вызывающего, который компилируется, не обработав „неизвестно“**».
**Форма: слабая, и слабее собственной слабой формы.** У Белнапа слабая форма — это `null`, трактуемый по
месту; здесь `null` не доживает до места трактовки, его снимает геттер. Опровержение выполняется не поиском
такого вызывающего, а тем, что **все четыре вызывающих таковы и иными быть не могут**.

*Гипотеза, не замер:* пункт 32 (план продукта пишется в пространстве имён фабрики, закон 26) может корениться
здесь — цель не измерена, умолчание говорит «репозиторий заказчика», и файлы фабрики планируются заказчику.
Соперничающее объяснение, которое я не могу отвергнуть: компилятор берёт пути фабрики из своего контекста
независимо от цели, и тогда умолчание ни при чём. Что разделило бы: случай столкновения по закону 26 у
требования, которому цель **присвоили явно**. Такого случая у меня нет; моя правка в `TechnicalLeadCompiler`
фильтрует пути по пространству имён — она лечит след, а не корень, и корнем её называть я не вправе.

---

**`WishlistStatus`** (5 значений, 31 читающий) — жизненный путь требования от заявки до задачи.
*Связи:* самое читаемое пространство состояний фабрики | ключевой писатель —
`JulesDispatchService.admitWishlistCompilationCompletion`.
*Ценность:* без него нельзя отличить «требование ждёт» от «требование уже разбирают».
*Комментарий:* **ядро — и единственное из девяти, где третье состояние сделано явным, потому что за его
отсутствие фабрика заплатила.** В типе живёт датированная запись о происшествии: 7 августа 2026 (`test-forty-third`)
одно и то же требование трижды за минуту разложили в графы задач, дубликаты уткнулись в
`BLOCKED_BY_DUPLICATE_CONTENT` и остановили проект целиком. Между `compiling` и терминальными
`converted_to_task`/`dismissed` не было состояния, означающего «этим уже кто-то занят прямо сейчас», —
и повтор завершения (retry опроса, дубль вебхука) видел `compiling`, то есть «свободно», и строил граф
заново. Починка — не проверка и не блокировка, а **новое значение `finalizing`**: завершение обязано
выиграть compare-and-swap `compiling → finalizing` **до** медленной работы с GitHub, и опоздавший видит
состояние, которого раньше не существовало, и отступает.

*Философия:* тот же `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы и опровержение — дословно выше.
**Форма: сильная.** Третий исход представлен явно, показано, как он хранится (колонка состояния), как
разрешается (CAS) и что делает вызывающий, который его увидел (отступает). Проигнорировать его на стороне
вызывающего нельзя: игнорирование означало бы проиграть CAS и всё равно не пройти дальше.
**Это не украшение образцом, а его подтверждение ценой происшествия:** предписание Белнапа «представить
третье состояние явно» здесь было выведено фабрикой самостоятельно, из отказа, а не из корпуса. Один
случай из девяти, где сильная форма есть, — и он единственный, который сначала сломался.

---

**`AccountStatus`** (6 значений: `idle busy offline daily_limited api_blocked decommissioned`, 14 читающих) —
пространство состояний носителя, по которому выбирается, кому отдать работу.
*Связи:* читают 14 файлов, среди них `lockNextJulesAccountWithCapacity` и `AccountHealthService`.
*Ценность:* без него нет пула — есть один аккаунт.
*Комментарий:* **ядро, и в нём отсутствует не «неизвестно», а целый конъюнкт.** Доступность аккаунта —
это `status` **и** `enabled`, но в типе записан только первый. Второй живёт отдельным булевым полем, и
тип о нём ничего не знает. 5 сентября это стоило семи часов простоя: пять аккаунтов имели `status: idle`,
то есть «свободен», при `enabled = false`, и `lockNextJulesAccountWithCapacity` отбрасывал их первым же
условием `WHERE a.enabled = true` молча — потому что «отключён» не является состоянием аккаунта, а значит
не может быть ни показано, ни объяснено. `AccountHealthService.recoverEligibleAccounts` выбирает через
`findByStatusAndEnabledTrue` и отключённый аккаунт **не видит вовсе**, то есть восстановление до него не
доходит по устройству.

*Живое, 7 сентября 2026* (`curl -s http://localhost:8080/api/accounts`): в пуле пять действующих записей —
`eneikdru`, `dmitrefrem-eneik`, `sixdmitrsix-ops`, `fivedmitr-sys` в состоянии `idle` и
`eneikcoworking-ctrl` в состоянии `busy`; **у всех `enabled = true`**. Причины сентябрьского простоя сейчас
нет. Рядом лежат 15 записей в состоянии `decommissioned` с повторяющимися именами — след списка имён из
`V19` (раздел XXIIг). Наблюдаемое положение: работа идёт на одном аккаунте из пяти, четыре свободны.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы выше. **Форма: слабая.** Состояние «отключён»
не представлено; вызывающий, читающий `idle`, компилируется, не обработав его, — что и есть опровержение
образца, выполненное на живом простое. Вторичный образец: `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006),
опровержение «найти отношение, у которого нет теста запрета» — отказ по `enabled` не называет сработавшего
условия, поэтому вопрос «кто и почему это сделал» не имеет ответа, а не просто остаётся без ответа.

---

**`LeanValue`** (3 значения: `essential valuable waste`, 15 читающих) — классификация ценности требования,
на которой стоит отбраковка муды.
*Связи:* читают `ProjectFlowService:2137,3123`, `TechnicalLeadCompiler:1194`, `BaseQualityGate:25`,
`JulesDispatchService:4406` | пишут девять мест, из них **восемь пишут `essential`**
(`OpsAuditorService:412`, `ProjectFlowService:895,5554`, `FalsificationCycleService:1413`,
`DeliveryRealityProducerService:221`, `LaunchabilityConstraintService:105`, …), одно —
`valuable` (`FalsificationCycleService:1066`), и **ни одно не пишет `waste`**.
*Ценность:* единственный тип, которым фабрика вправе назвать собственную работу лишней.
*Комментарий:* **ядро по замыслу, периферия по замеру.** Вердикт «это муда» не производит ни один
механизм фабрики — он может прийти только текстом от модели, и оба места, где он что-то решает, сравнивают
со **строкой**, а не со значением типа: `BaseQualityGate:25` — `LeanValue.waste.name().equals(leanValue)`,
`JulesDispatchService:4406` — `!"waste".equalsIgnoreCase(e.leanValue())`. Заслон против муды срабатывает
ровно тогда, когда модель по буквам напишет нужное слово.

А неузнанное слово превращается в похвалу. `JulesDispatchService.parseLeanValue:4492-4498`:

```java
try { return LeanValue.valueOf(raw == null ? "" : raw.toLowerCase(Locale.ROOT)); }
catch (IllegalArgumentException e) { return LeanValue.valuable; }
```

Ответ, которого тип не знает — то есть буквально «неизвестно», — становится **утверждением, что работа
ценна**. Не отказом, не `waste`, не пустым значением: положительным вердиктом. Молчание модели и её ошибка
неотличимы от её одобрения.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы выше. **Форма: слабая, в самом чистом виде,
какой я встретил в этом коде.** Слабая форма у Белнапа — «булево плюс `null`, трактуемый по месту»; здесь
даже `null` не появляется: неизвестное приводится к утвердительному значению в одной строке `catch`.
Опровержение образца («найти вызывающего, который компилируется, не обработав „неизвестно“») выполняется
самим `catch`: он не обрабатывает неизвестное, он его переименовывает. Вторичный образец:
`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — заслон, который при неразборчивом входе отвечает
«годно», зелен не потому, что проверил, а потому, что не смог проверить.

---

**`ProjectStatus`** (6 значений: `active analyzing waiting frozen accepted archived`, 28 читающих) —
пространство состояний проекта, второе по читаемости.
*Связи:* восемь мест записи, все в `ProjectFlowService` (302, 304, 328, 370, 562, 578, 676, 6128) |
умолчание `ProjectEntity:97` — `= ProjectStatus.active`.
*Ценность:* по нему внешние поверхности отвечают на вопрос «что с проектом».
*Комментарий:* **ядро, и в нём нет состояния «поток встал сам».** Замерено: `SYSTEM_STALLED` во всём `main`
встречается **только в комментариях** (`TocSubordinationLever:40`, `FlowMetricsService:28`,
`OperationalPolicyService:99,107,113`) — это вычисляемая телеметрия, а не состояние проекта. Ближайшее
похожее значение `frozen` состоянием затора не является: `ProjectFlowService:328` — это
`retirePriorActiveProjectsLocally`, то есть **решение** отправить прежний проект на покой при заводке
нового, а `578` — тоже явное действие. Значит проект, стоящий сорок часов, читается вызывающим как
`active`, и это не сбой отчётности, а отсутствие слова: сказать иначе тип не позволяет.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы выше. **Форма: слабая.** Различие «идёт» и
«числится идущим» не представлено; двадцать восемь читающих компилируются, не обработав его. Вторичный
образец: `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — состояние, которое механизм себе
приписывает, здесь по устройству не может разойтись с наблюдаемым, потому что наблюдаемого состояния в
словаре нет.

---

Оставшиеся четыре описаны короче не по небрежности, а потому что замер не даёт больше: у них мало
читающих и нет происшествий.

**`PersistentWorkerPurpose`** (3 значения: `WISHLIST_COMPILER REVIEW_FALLBACK PHILOSOPHICAL_AUDIT`,
6 читающих) — зачем заведён постоянный работник.
*Связи:* читают 6 файлов | пишется при заводке работника.
*Ценность:* без него постоянные работники неразличимы и их нельзя ни сосчитать по назначению, ни ограничить.
*Комментарий:* **ядро малого радиуса.** Это тип, в котором записано, **ради чего фабрике вообще разрешено
держать работника постоянно** — список закрыт, и четвёртой причины не существует, пока её сюда не впишут.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно: «запрет — **исполнимый путь
отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет записан в документе или комментарии».
Опровержение: «**совершить запрещённое действие; если оно прошло — запрета нет, есть пожелание**».
**Форма: сильная по механике, слабая по свидетельству** — назвать четвёртое назначение нельзя, выражение не
соберётся, но теста, закрепляющего закрытость списка, я не нашёл, а образец его требует.

**`ClaimResultStatus`** (3 значения: `done failed expired`, 2 читающих) — исход попытки удержать притязание.
*Связи:* 2 читающих.
*Ценность:* различает «сделано», «отказ» и «истекло» — три исхода, которые нельзя сливать, потому что
повторять надо только третий.
*Комментарий:* **периферия по радиусу, ядро по смыслу:** это единственное из девяти, где различены отказ
и истечение, то есть где «не получилось» не свалено в одно слово.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), формы выше. **Форма: не мерено.** Двух читающих
я не разбирал; чтобы назвать форму, нужно показать, что оба различают `failed` и `expired` в поведении,
а не только в записи.

**`WishlistItemStatus`** (3 значения: `open converted ignored`, 3 читающих) и **`WishlistItemType`**
(2 значения: `client_wish role_advice`, 3 читающих) — состояние и происхождение отдельного пункта требования.
*Связи:* по 3 читающих у каждого; `switch` по ним нет ни одного.
*Ценность:* `WishlistItemType` отделяет пожелание заказчика от совета роли — то есть **чьё это хотение**;
без него совет фабрики самой себе неотличим от просьбы клиента.
*Комментарий:* **периферия.** Оба малы, оба читаются в трёх местах, поток удержать не могут. Но
`WishlistItemType` стоит на той же границе, что и `TargetContext`, только на уровне пункта, и та же
проверка на явное «неизвестно» даёт тот же ноль: пункт неизвестного происхождения выразить нечем.
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — форма **не мерена**: я не проверял, что все три
читающих каждого типа действительно различают значения, а не читают их для отображения.

---

**Что из этого следует для перечня.** Девять пространств состояний — не данные, а границы мыслимого, и
восемь из девяти не умеют сказать «неизвестно». Девятое, `WishlistStatus`, умеет — потому что 7 августа
за это заплатили остановкой проекта. Дешевле было бы взять образец `TRUTH_STATUS_TABLE` из корпуса до
происшествия, а не вывести его из происшествия; корпус лежит в репозитории с самого начала.

# XXIв. Алгебра решений: чем фабрика отказывает

Три типа без аннотации, вместе составляющие единственное место, где фабрика умеет ответить «я этого не
установила» и не быть понятой как «всё хорошо». Прямое продолжение раздела XXIб: там показано, что
пространства состояний третьего исхода не имеют; здесь — что он есть, отдельным типом.

**`Verdict`** (77 строк, 3 значения) — что один слой говорит об одном объявленном утверждении: можно ли
проекту продвинуться по этому счёту.
*Связи:* читают `CommandDashboardService` и `VerdictGate` (`services/verdict`, `@Service`) | пять слоёв
выносят его: `RuntimeVerdictLayer`, `SixSigmaVerdictLayer`, `AcceptanceVerdictLayer`,
`InfrastructureVerdictLayer`, `DoctrineVerdictLayer` — замер: `grep -rln "implements VerdictLayer"`,
контроль на самом интерфейсе прошёл.
*Ценность:* без него слои говорят числами разных родов, и их приходится усреднять.
*Комментарий:* **ядро, и лучший механизм из всех, что я разобрал.** Значения три, а не два, и причина
записана замером: 15 августа четыре доктринальные роли из тринадцати стояли в «неизвестно», пока поток
раздавал задачи и сообщал 82% готовности, — у потока не было способа сказать «не решено», и он вёл себя
так, будто не решено значит разрешено.

`and(Verdict)` — **сильная трёхзначная конъюнкция Клини, а не взвешенная сумма**:
`WITHHOLD ∧ что угодно = WITHHOLD`, `PERMIT ∧ ABSTAIN = ABSTAIN`. Отсюда три свойства, каждое чинит дефект
устройством, а не по месту: одобрение не перевешивает отказ; воздержание не равно разрешению; добавление
слоя может сделать продвижение только труднее, никогда легче, — поэтому фабрика вправе наращивать
собственную проверку без риска, что новая проверка что-нибудь случайно разблокирует.

Отдельно записан отказ усреднять как **ошибку рода**: пять слоёв сообщали о одном проекте `82%`, `blocked`,
`954545`, `0.57` и `launchSuccess=false`; конвейер говорит о действительности, доктрина деонтически, шесть
сигм о частоте, граф о строении. Объединение — **не одно число, а один тип**: каждый слой отвечает на
вопрос, на который может ответить, в своих терминах, отображая свою меру в одно из трёх значений своим
объявленным правилом. Пороги не исчезают — они становятся местными и проверяемыми внутри слоя-владельца
вместо тайного глобального балла.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“».
**Форма: сильная, и это единственное место на фабрике, где образец исполнен буквально.** Проигнорировать
третий исход вызывающий не может не по договорённости, а по арифметике: `ABSTAIN` поглощает `PERMIT` в
конъюнкции. Замечу и расхождение с философом: у Белнапа четыре значения, здесь три — «противоречивое»
отсутствует, и слои, сказавшие противоположное об одном утверждении, дадут `WITHHOLD`, а не «противоречие».
Это осознанное сужение (сам принцип в корпусе помечен как «отвергается для итогового статуса, применяется
для диагностики»), но означает, что различить «один слой отказал» и «слои противоречат друг другу» нечем.
Второй образец: `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — Гилберт Райл, `BARCAN-TAG-00 CODE-GUARDIAN`,
принцип различия «знать что» и «знать как», anchor *The Concept of Mind — knowing-how versus knowing-that,
category mistakes*. Сильная дословно: «назван тип, схема или переходник, удерживающий границу рода: процесс
не выдаётся за объект, наблюдение за полномочие, политика за данные». Слабая: «мы понимаем разницу».
Опровержение: «найти место, где значение одного рода присваивается полю другого без преобразования».
**Форма: сильная.** Переходник назван и это сам тип: каждый слой обязан отобразить свою меру в `Verdict`
своим правилом, и складывать частоту с деонтикой становится невыразимо.

**`Judgement`** (42 строки) — суждение одного слоя об одном объявленном утверждении, вместе с основанием и
свидетельством, на котором стоит.
*Связи:* производится тремя способами — `permit`, `withhold`, `abstain`; собирается в
`VerdictReconciliation.reconcile` (`@Service`, аннотация записана полным именем — мой прежний фильтр бинов
её не видел, см. поправку к знаменателю) | наружу выходит через `VerdictController`.
*Ценность:* без основания и свидетельства отказ нельзя ни проверить, ни пересмотреть.
*Комментарий:* **ядро.** Основание обязательно для всего, кроме `PERMIT`, и javadoc называет причину:
«a refusal a human cannot check is an accusation, not evidence» — отказ, который человек не может
проверить, есть обвинение, а не свидетельство. У воздержания основание тоже обязательно, и по отдельной
причине: воздержание без сказанной причины неотличимо от слоя, который просто не запускался.

Это то же различение, что несут `TaskEntity` и `WishlistEntity`, и здесь оно доведено до конца в устройстве
свёртки: слой, **упавший с исключением**, записывается как воздержание, а не исчезает из счёта
(«the observer must never become the outage it exists to prevent»); объявленное утверждение, по которому
слой **промолчал**, тоже становится воздержанием — «молчание о том, что слой обещал рассудить, обязано
считаться против продвижения, а не за». Это прямая защита от того самого подлога, что записан в
`TaskEntity`: `allMatch` по пустому списку даёт «прошло всё».
*Философия:* `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` (D007) — Айзек Леви, `BARCAN-TAG-04 MODAL-QUANTIFIER`,
принцип фиксации доксастических состояний, anchor *The Fixation of Belief and Its Undoing / Enterprise of
Knowledge — doxastic commitment*. Сильная дословно: «записано, какое свидетельство изменило убеждение и
какая неопределённость осталась; приложены уверенность до и после и неразрешённые гипотезы». Слабая: «новое
убеждение изложено без старого». Опровержение: «спросить, во что агент верил час назад и что именно это
изменило». **Форма: слабая.** Поле `evidence` несёт, на чём суждение стоит, и javadoc прямо говорит зачем —
чтобы вердикт можно было пересмотреть, когда его предмет изменился, вместо того чтобы вечно стоять на
факте, который с тех пор устарел. Но **прежнего суждения не хранится**: сравнить «во что верили час назад»
не с чем, суждения строятся заново на каждый запрос. Оставшаяся неопределённость, впрочем, записана —
`outstandingByLayer` считает невынесенные по каждому слою.

**`OperationalPolicyDeniedException`** (39 строк) — отказ операционной политики, несущий, **что именно**
было отказано и на каком основании.
*Связи:* бросает `OperationalPolicyService`; ловят `AutoMergeService` и `ProjectController`.
*Ценность:* без переносимого основания отказ виден только как отсутствие действия.
*Комментарий:* **ядро, и прежнее его исключение из перечня было ошибкой.** Раздел XVIII относил его к
двенадцати «типам результатов и исключений, у которых нет собственного поведения». По замеру он несёт
четыре поля — проект, действие, состояние, статус полномочия — плюс причину в самом сообщении. Признак
«есть ли методы, которые что-то делают» здесь не годится: **удержание потока состоит не в том, чтобы
что-то сделать, а в том, чтобы остановка была объяснима**. Это ровно то, чего не было при простое 5 сентября:
отказ по `enabled` не назвал сработавшего условия, и вопрос «кто и почему это сделал» не мог иметь ответа.
Здесь он ответ имеет по устройству. Судить о том, всегда ли ловящие этот отказ его основание показывают, я
не могу — двух ловящих я не разбирал.
*Философия:* `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«до реализации полномочий составлена матрица прав, обязанностей, привилегий и власти, и на каждое отношение
есть тест разрешённого и запрещённого». Слабая: «роли перечислены, проверки написаны по месту».
Опровержение: «найти отношение, у которого нет теста запрета». **Форма: не мерено.** Тип несёт действие и
статус полномочия, то есть материал для матрицы есть; но существует ли на каждое отношение тест
разрешённого и запрещённого, я не проверял, а без этого называть форму — догадка.

## Где эта алгебра стоит, и почему её недостаточно

Заслон существует — `VerdictGate` (`@Service`, 133 строки) — и он **не читающая поверхность**: он
ограничивает готовность, которую фабрика **заявляет**. Намеренно не трогает приёмку проекта (приёмка есть
акт заказчика, завершающий работу, и решётка, которая воздерживается, не вправе мешать человеку закончить
своё дело) и намеренно не заслоняет раздачу задач (слой, говорящий «продукт не запускается», выдвигает
довод **за** починку, и заслонять раздачу на этом основании значило бы лишить фабрику способности чинить
именно то, в чём ей отказано). Оба решения записаны и оба верны.

Заслон требует непустой `verdict_gating_project_slug`: пока он пуст, `activeFor` возвращает ложь. **Замер
7 сентября опроверг моё прежнее утверждение, будто он пуст:** ярлык равен `test-fiftieth`, то есть заслон
привязан к живому проекту и применяется. Прежняя ошибка была моя и инструментальная — я читал поле
`enabled`, годное для булевых настроек, тогда как строковое значение лежит в `maskedValue`. Значение по
умолчанию действительно пусто — и это тоже осознано, как ступенчатое включение («значение области видимости, по
умолчанию означающее „все“, превратило бы первую же небрежную выкладку в общефабричное изменение»).
`Decision.applied` отдельно сообщает, участвовала ли решётка, «потому что „заслон сработал и согласился“ и
„заслон не запускался“ — разные факты, и по неразличимым выкладку не рассудить». Установлен ли ярлык на
этой машине — **не мерено**: это состояние живой фабрики, а не репозитория.

Итог суждения: механизм устроен строже всего, что здесь описано, и при этом действует над одним
утверждением — над тем, что фабрика **заявляет** о готовности. Между «фабрика не вправе утверждать, что
готово» и «фабрика не вправе действовать так, будто готово» лежит вся разница, и вторую половину эта
алгебра по построению не покрывает.

# XXIг. Граф ограничения: чем фабрика находит своё узкое место

Четыре типа без аннотации в `toc/model` — вся память барабана-буфера-верёвки. Замер, определяющий всё
остальное в этом разделе: **весь граф питает один механизм**.

```
grep -rn "\.enterStep(\|\.exitStep(\|\.startExecution(\|\.endExecution(" --include=*.java . | grep -v "toc/"
→ AutoMergeService.java:164  startExecution("AUTOMERGE_CYCLE", 40)
  AutoMergeService.java:170  enterStep(token, "AUTOMERGE_PROCESSING")
  AutoMergeService.java:173  exitStep(token, "AUTOMERGE_PROCESSING", true)
  AutoMergeService.java:174  endExecution(token, true)
→ итого мест разметки вне пакета toc: 4
```

Контрольная проба на неявную разметку: `@Aspect|HandlerInterceptor|@Around` — **ни одного файла** во всём
`src/main`, при том что тот же греп по `@Service` находит 94 бина, то есть инструмент видит. Значит скрытого
источника наблюдений нет: граф видит один сценарий и один шаг.

**`TocNode`** (147 строк) — узел графа исполнения: держит число работ в полёте, завершения, ошибки, среднее
время и его разброс по Уэлфорду, загрузку и два признака — «затор» и «главное ограничение».
*Связи:* пишут двое, оба внутри пакета — `TocAnomalyDetector:80` (`incrementInFlight`) и
`TocSentinelService:100-101` (`decrementInFlight`, `recordExecution`); признаки ставят `TocOptimizer:57,66`
и `TocAnomalyDetector:91,134`. Наружу состояние выходит через `DbrStatus`, и его читают **пятеро**:
`BottleneckAwarePriorityService:44,82` (**приоритизирует работу по имени текущего ограничения**),
`SixSigmaAuditService:278`, `KaizenService:239,731`, `SystemAuditController:64`, `TocSentinelController:32,37`.
*Ценность:* без него у фабрики нет ни одного места, где сказано, какой шаг держит поток.
*Комментарий:* **ядро по замыслу и по числу читателей — и при этом инструмент, который может назвать
ограничением только то, что сам же и размечает.** Узлы заводятся лениво, при первом входе
(`TocExecutionGraph:27`, `computeIfAbsent`), а входят только из цикла автослияния. `TocOptimizer:44-58`
выбирает узел с наибольшим счётом среди **имеющихся** узлов. Множество кандидатов, таким образом, равно
множеству размеченного, а размечен один шаг. «Главное ограничение» не находится сравнением — оно
предопределено тем, где поставили датчик.

Отсюда следующий шаг, и он важнее: пятеро читающих принимают это имя за факт о фабрике. Приоритизация
работы идёт по имени, которое иначе как `AUTOMERGE_PROCESSING` получиться не может. Механизм при этом
исправен: он честно меряет то, что ему дали мерить.

Второе свойство — вся эта память **живёт только в оперативной**: `AtomicLong` и `volatile`, а замер
`grep -rln "@Entity" toc/` даёт **ноль** файлов (контроль: тот же греп по `TaskEntity` находит 55 файлов,
инструмент работает). Среднее и разброс, накопленные Уэлфордом, обнуляются при каждом перезапуске
контейнера, и первое решение после перезапуска принимается по выборке из одного наблюдения.
*Живое, 7 сентября 2026:* сторож работает — в журнале идут строки `[TOC-SENTINEL][STEP_EXIT]` и
`[END_EXECUTION]` с отметками секундной давности. То есть счётчики наполняются, и наполняет их по-прежнему
единственный размеченный шаг: сценарий `AUTOMERGE_CYCLE`. Замер разметки, приведённый выше, за прошедшее
время не изменился.

*Философия:* `DONALD_DEVIDSON_15_INUS_FACTOR_CHECK` (D007) — Дональд Дэвидсон, `BARCAN-TAG-09 MORAL-DILEMMA`,
принцип радикальной интерпретации, anchor *Truth and Meaning / radical interpretation — interpretation and
coherence*. Сильная дословно: «подозреваемая причина считается **одним фактором достаточного набора**, пока
альтернативы не исключены; со-факторы перечислены со свидетельством присутствия или отсутствия каждого».
Слабая: «названо первое объяснение, совпавшее с наблюдением». Опровержение: «назвать вторую гипотезу,
дающую то же наблюдение; её отсутствие означает, что сравнения не было».
**Форма: слабая, и опровержение выполняется без усилий** — второй гипотезы нет не потому, что её отвергли,
а потому, что второго узла в графе не бывает. Альтернативы не исключены, они не попали в рассмотрение.
Второй образец: `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: слабая.** Снимка нет: ноль `@Entity` в пакете, восстанавливать нечего.
Третий образец: `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — Фред Дрецке,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип информационной пропускной способности каналов, anchor
*Knowledge and the Flow of Information — informational epistemology*. Сильная дословно: «сигнал признан
годным лишь если меняет следующее действие и предотвращает ошибочное». Слабая: «сигнал есть и он верен».
Опровержение: «назвать действие, которое сигнал изменил; **сигнал без читателя не есть наблюдение**».
**Форма: сильная — и это единственное, что здесь сильно.** Действие называется:
`BottleneckAwarePriorityService` меняет порядок работ по имени ограничения. Сигнал читателя имеет. Беда не
в том, что его не слушают, а в том, что слушают безоговорочно сигнал, снятый с одного датчика.

**`TocEdge`** (34 строки) — направленное ребро между двумя узлами со счётчиком переходов.
*Связи:* заводится и увеличивается в одном месте — `TocExecutionGraph:60` | читателей счётчика вне пакета
по замеру нет.
*Ценность:* без рёбер граф исполнения — набор несвязанных счётчиков, и обнаружение цикла невозможно.
*Комментарий:* **периферия, и по той же причине, что и узел, только резче.** Ребро возникает при переходе
между шагами, а размеченный шаг один — переходить не между чем. Тип исправен и, насколько я измерил,
пуст в работе.
*Философия:* `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011), формы и опровержение — дословно выше.
**Форма: слабая.** Назвать действие, которое изменил бы счётчик переходов, я не смог: читателей вне пакета
замер не нашёл. Сигнал без читателя не есть наблюдение.

**`DbrStatus`** (19 строк, 9 полей) — свод состояния барабана-буфера-верёвки: имя ограничения, длина
очереди, загрузка, среднее время, размер буфера и его предел, включена ли верёвка, время оценки и
рекомендация.
*Связи:* производит `TocSentinelService.getDbrStatus` | читают пятеро, перечислены в записи `TocNode`.
*Ценность:* единственная переносимая форма ответа «что сейчас держит поток».
*Комментарий:* **ядро по употреблению.** Устроен добросовестно: несёт и время оценки, и предел буфера, и
признак того, включено ли ограничение выпуска, — то есть даёт читателю отличить «верёвка натянута» от
«верёвка есть». Но каждое поле наследует ту же узость: они описывают единственный размеченный шаг.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается
на собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная по форме, но предмет подменён.** Свод опирается на
настоящие замеры и приводит время их снятия — придраться не к чему. Подмена в имени: `primaryConstraintNode`
читается как «узкое место фабрики», а означает «единственный шаг, за которым мы наблюдаем». Это не ложь
свода, а ошибка рода на стороне читателя, и её пятеро и совершают.

**`AnomalyReport`** (25 строк) — запись об аномалии, найденной сторожем: цикл, затор, взаимная блокировка
или переполнение буфера, с указанием узла, ресурса и **предпринятого действия**.
*Связи:* производит `TocAnomalyDetector` | потребителей вне пакета замер не выявил.
*Ценность:* без него обнаруженная аномалия остаётся в логе и не имеет ни личности, ни следа.
*Комментарий:* **периферия по замеру, ядро по замыслу.** Устройство хорошее: тип различает четыре рода
аномалий вместо одного «что-то не так» и отдельным полем несёт `actionTaken` — то есть отвечает не только
«что случилось», но и «что с этим сделали», а это ровно то, чего не хватало отказу при простое 5 сентября.
Но обнаруживать он может лишь то, что видно на одном размеченном шаге: `CYCLE_DETECTED` требует рёбер,
которых не возникает, `DEADLOCK_DETECTED` — конкуренции за ресурсы, которую один шаг не создаёт.
*Философия:* `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P),
anchor *The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно:
«проверка, способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что
она краснеет при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и
прогнать тест; не покраснел — это не заслон». **Форма: не мерено.** Я не проверял, краснеет ли обнаружение
цикла на подстроенном цикле; без такой пробы называть форму — догадка. Что измерено — это область
наблюдения, а не чувствительность.

## Суждение по разделу

Механизм барабана-буфера-верёвки построен целиком и работает исправно. Ни один из четырёх типов не сломан.
Сломано отношение между тем, что он измеряет, и тем, за что его показания принимают: **один датчик на
цикле автослияния, пятеро читающих, и приоритизация работ по имени, которое другим быть не может**.
Прибавить сюда нечего, кроме датчиков, а это правка продуктового кода, и здесь я её не предлагаю.

# XXIд. Лестница доверия: чем фабрика решает, кому дать власть

Два типа без аннотации в `services/lever` — словарь и мера того, как решающий механизм получает право
что-то запрещать. Здесь же — прямой ответ на раздел XXIг: **этот механизм устроен ровно так, как граф
ограничения устроен не был**.

**`LeverAgreement`** (47 строк, 4 значения) — диагностическая оценка одного наблюдения: оказался ли
кандидат прав против настоящей действительности, и был ли при этом прав действующий.
*Связи:* читают **шестеро** — `AccountHealthService`, `FlowSpineService`, `KaizenService`,
`SixSigmaAuditService`, `TocSubordinationLever`, `LeverPromotionService` | наблюдения **сохраняются**
через `observationRepository`.
*Ценность:* без него «кандидат ошибся» и «свидетельства ещё нет» — одно и то же, и новый механизм получает
власть за то, что о нём ничего не известно.
*Комментарий:* **ядро, и единственное место, где четыре значения Белнапа взяты полностью.** В разделе XXIв
я отметил у `Verdict` сужение до трёх: «противоречивое» отсутствует. Здесь оно есть — `BOTH` означает, что
кандидат и действующий согласились и друг с другом, и с действительностью: свидетельство настоящее, но о
собственной ценности кандидата не говорящее, и потому к порогу повышения не засчитывается **ни в какую
сторону**. `NEITHER` отдельно оговорено как «свидетельства пока нет», а **не** «неизвестное, посчитанное
как ложь».

Замечательнее всего, что тип **сам ссылается на корпус**: в javadoc написано «Belnap's four-valued
diagnostic states (BARCAN-TAG-06, philosopher 3)», и это в точности `NUEL_BELNAP` из
`docs/philosopher-patterns`. И ссылка соблюдена не только по букве: корпус помечает принцип как
«отвергается для итогового статуса, применяется для диагностики», а код пишет «explicitly a DIAGNOSTIC
status, never itself the final promotion verdict (that stays single-valued)». То есть образец применён с
его собственным ограничением, а не вопреки ему.

Есть и осознанный отказ от универсальности: `compare` объявлен помощником только для рычагов, чьи решения
буквально сравнимы с действительностью, и javadoc запрещает прогонять через него остальные — «forcing every
lever through one generic string-equality rule would silently misjudge those cases». Случай «оба неправы»
свёрнут в `NEITHER` с объяснением: настоящее свидетельство есть, но о **относительной** ценности кандидата
оно молчит, а порог спрашивает именно о ней.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“».
**Форма: сильная, и полнее, чем у `Verdict`.** Все четыре состояния представлены явно; показано, как каждое
хранится (столбец `agreement` наблюдения), как отображается (в журнале повышения) и как разрешается
(`TRUE`/`FALSE` идут в счёт, `BOTH`/`NEITHER` не идут ни в какую сторону). Опровержение выполнить не
удалось: вызывающий, который бы посчитал `NEITHER` за согласие, отсутствует — счёт ведётся фильтром по
двум разрешённым значениям, а не отрицанием.

**`LeverStage`** (50 строк, 5 значений) — лестница полномочий решающего механизма: только наблюдать,
предупреждать, мягко заслонять, жёстко заслонять, чинить самому.
*Связи:* читают `KaizenService`, `SixSigmaAuditService`, `TocSubordinationLever`,
`LeverPromotionStateEntity`, `LeverPromotionService` | ступень **сохраняется** в `stateRepository`.
*Ценность:* без неё новый решающий механизм либо не действует вовсе, либо действует сразу в полную силу.
*Комментарий:* **ядро.** Три вещи здесь сделаны правильно, и каждая измерена.

Первое: **неизвестное лишает власти, а не даёт её.** `fromWireValue` на нераспознанной строке возвращает
`OBSERVE_ONLY`, и `currentStage` для рычага, которого нет в базе, — тоже; javadoc называет это «zero live
effect». Сравнить с `parseLeanValue` из раздела XXIб, где неразобранный ответ становился `valuable`, то есть
утверждением в пользу. Одна и та же ситуация, противоположные умолчания.

Второе: **власть даётся только за накопленное свидетельство.** Повышение на ступень требует не менее 20
разрешённых наблюдений при доле согласия не ниже 0,80 в окне 14 дней, проверка раз в два часа
(`MIN_RESOLVED_SAMPLES`, `AGREEMENT_THRESHOLD`, `RECENCY_WINDOW`, `@Scheduled(fixedRate = 7200000)`).
Javadoc отдельно оговаривает: «based on real accumulated evidence, never on a deploy or a timer».

Третье, и самое важное: **лестница несимметрична, и несимметрична в верную сторону.** Повышение медленное и
пакетное; понижение — немедленное, при первом же настоящем расхождении, не дожидаясь следующего цикла, со
ссылкой на надёжностный процессуализм Гоулдмана: одна подтверждённая ошибка сама по себе есть свидетельство,
что процесс на этой ступени ещё не надёжен. Отмечу связь с пунктом 38 предписаний: там аккаунт, разжалованный
отказами, пути назад не имеет, потому что повышение требует успеха, которого ему не дадут. Здесь путь есть в
обе стороны, и разность скоростей задана намеренно.

Одно место, где я не соглашусь с механизмом: при доле согласия **ниже** порога в `evaluateOne` не
происходит ничего — состояние просто сохраняется. Понижение живёт только в записи наблюдения. Рычаг,
который систематически неправ, но не выдал ни одного `FALSE` (например, потому что его наблюдения
разрешаются в `NEITHER`), останется на своей ступени сколь угодно долго. Это не дефект устройства, а
незаполненный случай; называть его нарушением я не буду, потому что не мерил, случается ли он.

*Живое, 7 сентября 2026:* лестница работала в обе стороны за один час.
`docker logs eneikproductionsys-backend-1 | grep LEVER-PROMOTION`:
`19:17:32 WARN 'F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY' demoted auto_remediate -> hard_gate`, затем
`20:17:48 INFO ... promoted hard_gate -> auto_remediate (recent resolved=358)`. Одно подтверждённое
расхождение сняло рычаг с верхней ступени **немедленно**, а вернулся он через час по 358 разрешённым
наблюдениям. Накоплено `LEVER_OBSERVATIONS` — 5338 строк (замер `db-table-sizes`). Мой прежний открытый
вопрос — доходил ли хоть один рычаг доверху — закрыт: доходил, и не удержался с первой же ошибки.
**Реализация здесь сильнее образцов корпуса, и по этому случаю заведён новый:**
`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` в
`docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md`. Корпус говорит о **пороге** доверия
(`KNOWLEDGE_FIRST_GATE`, `RELIABILITY_CHAIN`) и молчит о **скорости его изменения**; здесь скоростей две и
они намеренно разные.
*Философия:* `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006) — Элвин Голдман,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип релайабилизма процессов, anchor *A Causal Theory of Knowing
/ Epistemology and Cognition — reliabilism*. Сильная дословно: «рискованное действие требует свидетельства
знаниевого качества, а не убеждения или намерения; приложена проверка или источник полномочия». Слабая:
«действие разрешено, потому что „мы уверены“». Опровержение: «потребовать источник; ссылка на собственное
убеждение и есть дефект». **Форма: сильная.** Источник полномочия предъявляется по требованию: ступень
хранится, наблюдения хранятся, порог назван числом, окно назван сроком. Убеждению здесь взяться неоткуда.
Второй образец: `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` (D010), тот же философ и якорь. Сильная дословно:
«данным верят только когда процесс их добычи надёжен для **этого** класса дефекта; названы источник,
отметка времени, правило свежести и путь проверки». Слабая: «данные из базы, значит верны». Опровержение:
«назвать возраст значения; неизвестный возраст делает свежесть недоказуемой». **Форма: сильная.** Возраст
называется: наблюдения отбираются по `observedAt` позже отметки `Instant.now().minus(RECENCY_WINDOW)`, то
есть свежесть не предполагается, а вычисляется. Это ровно то, чего нет у графа ограничения из раздела XXIг,
где среднее по Уэлфорду не имеет ни возраста, ни снимка.

**`TaskTitleBuilder`** (167 строк) — выводит отображаемое имя задачи: из своего заголовка, а если его нет —
из полезной нагрузки, роли или ключевых слов, и всегда приводит к двум-трём словам.
*Связи:* зовут **восемь механизмов** — `DashboardController`, `ProjectFlowService`,
`DeliveryRealityProducerService`, `JulesApiClient`, `JulesDispatchService`, `TechnicalLeadCompiler`,
`ProjectOperationalContextService`, `OperationalTruthService` | ничего не пишет, чистая функция.
*Ценность:* без него задача без заголовка показывается пустой строкой или сырым описанием во всех восьми
местах по-разному.
*Комментарий:* **периферия по демаркации — поток он удержать не может, — но с оговоркой.** Устройство
верное: имя **вычисляется, а не хранится**, личность задачи остаётся за её идентификатором, и восемь
потребителей получают одно правило вместо восьми. Оговорка в том, что при отсутствии заголовка имя берётся
из умолчания по роли (13 значений, по одному на `BARCAN-TAG-NN`), и тогда две разные задачи одной роли
показываются одинаково. Имя в этом случае сообщает роль, а не предмет.
*Философия:* `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — Дэвид Чалмерс,
`BARCAN-TAG-02 RIGID-DESIGNATOR`, принцип двумерной семантики, anchor *Two-Dimensional Semantics —
primary and secondary intensions*. Сильная дословно: «отображаемое имя,
сохраняемый идентификатор и сущность в API разведены так, что перепутать их нельзя». Слабая: «одно поле
служит всем трём». Опровержение: «изменить отображаемое имя и посмотреть, не поехали ли ссылки».
**Форма: сильная.** Опровержение выполнить нельзя по устройству: отображаемое имя не хранится, менять
нечего, ссылки идут по идентификатору. По Чалмерсу это и есть развод двух интенсионалов: как задача
предъявляется читателю и что она есть — разные вопросы, и второй решается идентификатором.

**`JulesRoleCapabilities`** (35 строк) — закрытый список тринадцати ролевых меток и признак «известна ли
роль».
*Связи:* читают `AccountController` и `ProjectOperationalContextService`.
*Ценность:* без него список ролей повторяется в каждом месте, где заводится аккаунт, и расходится.
*Комментарий:* **периферия, но это та периферия, которой держится словарь.** Тринадцать меток
`BARCAN-TAG-00…12` — те же, что именуют семейства в корпусе философов и в умолчаниях `TaskTitleBuilder`.
То есть словарь ролей фабрики и словарь корпуса — один словарь, и `isKnownRole` делает принадлежность к
нему проверяемой.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет записан
в документе или комментарии». Опровержение: «совершить запрещённое действие; если оно прошло — запрета
нет, есть пожелание». **Форма: не мерено.** `isKnownRole` даёт исполнимый путь отказа, но зовут его двое, и
проверял ли я, что аккаунт с неизвестной ролью действительно не заводится, — нет. Без этой пробы форму
называть нельзя.

# XXIе. Журнал проекта: граница, которая держала — и отвечала не на тот вопрос

Три типа без аннотации в `services/logging`. Вместе они — точка исполнения правила «в то, что читают о
проекте клиента, попадает только то, что помечено этим проектом». Здесь же записан самый дорогой урок
перечня: **верно проведённая граница не спасает, если она отвечает на другой вопрос, чем тот, ради
которого её проводили.**

**`ScopedBufferAppender`** (31 строка) — приёмник Logback, отбирающий события с меткой `PROJECT:{id}` и
складывающий их в кольцевой буфер проекта.
*Связи:* подключён в `resources/logback-spring.xml:8` как приёмник `SCOPED_BUFFER` (контроль: в том же
файле 5 объявлений `<appender>`, инструмент видит) | пишет в `LogScopeBuffer` | метку ставят **34 места**
в коде, больше всего `JulesDispatchService` (7), `ContinuousOrchestrationService` (6),
`IdleProjectAdviceService` (4), `AutoMergeService` (3).
*Ценность:* без него нет ни одного места, где решается, что считается «событием этого проекта».
*Комментарий:* **ядро.** Событие без метки или с меткой `SYSTEM` отбрасывается целиком, и javadoc называет
это точкой исполнения правила, а не удобством. В отличие от графа ограничения из раздела XXIг, питающие
места здесь не единичны: тридцать четыре, в разных механизмах.
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци, `BARCAN-TAG-01 ACTUALIST-OBJECT`,
принцип топологии пространственно-временных границ, anchor *Parts and Places / formal ontology of boundaries
and spatial parts*. Сильная дословно: «названа точка, где меняется владелец проверки, полномочия или
сохранения, и на неё есть тест». Слабая: «граница „понятна из структуры пакетов“». Опровержение: «удалить
проверку на границе; если ни один тест не покраснел, границы нет». **Форма: не мерено.** Точка названа
однозначно — один `if` в одном приёмнике, — но опровержение я не выполнял: убрать проверку и посмотреть,
покраснеет ли хоть один тест, значило бы править продуктовый код. Без этой пробы форму называть нельзя.

**`LogScopeBuffer`** (47 строк) — кольцевой буфер в памяти, до 200 последних строк на проект.
*Связи:* пишет `ScopedBufferAppender` | читает **один** механизм — `ProjectController:315`, отладочная
выдача для человека.
*Ценность:* дешёвое окно «что только что происходило по этому проекту», не требующее запроса к базе.
*Комментарий:* **периферия по нынешнему замеру, и это результат сознательного изъятия, а не упадка.**
Здесь два расхождения, оба измеренные.

Первое, мелкое: **собственный javadoc устарел.** Он говорит, что буфер читает цикл фальсификации, «to give
roles more current operational context». Замер: во всём `main` вне пакета `logging` буфер читает только
`ProjectController`. Контроль на слепоту инструмента: в `FalsificationCycleService` тот же греп находит
5 упоминаний `LogScope`, то есть файл он видит, — но все они в комментарии, а не в коде. Описание
механизма пережило удаление читателя.

Второе — то, ради чего этот раздел написан. Читателя убрали 9 августа 2026, и причина записана в
`FalsificationCycleService:1701-1718`: **около 38 часов накопленного заражения**, прослеженных до точного
источника. Буфер отдавался сессии Jules под заголовком «RECENT PROJECT OPERATIONAL ACTIVITY». Буфер при
этом был очерчен **правильно** — только строки с меткой этого проекта, никакого общесистемного шума. Но
каждая такая строка есть запись о работе **самой фабрики** над этим проектом: раздача задач, сверка
запросов на слияние, уборка веток, учёт сессий — дословно `event.getLoggerName()` и
`event.getFormattedMessage()`, скопированные приёмником. Собственного времени исполнения поставленного
продукта фабрика не видит вовсе.

Дальше произошло следующее, и это записано как подтверждённая цепочка: Jules читал настоящие фразы о
`JulesApiClient`, `PipelineTelemetryService`, Flow Core и сверке задач, — и, не имея куда деть «починку»
прочитанного, **сочинял соответствующие классы внутри репозитория заказчика**. Право на запись у той сессии
было только туда.

Изъяли начисто, а не отфильтровали и не переименовали, с прямо названным основанием: «there is no version
of „here is Eneik's own orchestration log“ that belongs in a brief for a session that can only write to the
client's product code».
*Философия:* `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — Гилберт Райл, `BARCAN-TAG-00 CODE-GUARDIAN`,
принцип различия «знать что» и «знать как», anchor *The Concept of Mind — knowing-how versus knowing-that,
category mistakes*. Сильная дословно: «назван тип, схема или переходник, удерживающий границу рода: процесс
не выдаётся за объект, наблюдение за полномочие, политика за данные». Слабая: «мы понимаем разницу».
Опровержение: «найти место, где значение одного рода присваивается полю другого без преобразования».
**Форма: слабая на момент происшествия, сильная после изъятия — и главное здесь не оценка, а урок.**
Граница по проекту работала безупречно и отвечала на вопрос «о чьём проекте эта строка». Нужен же был ответ
на другой вопрос: «эта строка о носителе или о продукте». Два рода — проект и носитель — пересекаются, и
фильтр по первому ничего не говорит о втором. Опровержение образца выполняется буквально: значение рода
«наблюдение за работой фабрики» присваивалось полю рода «недавняя деятельность продукта» без
преобразования, и преобразования не существует.
Второй образец: `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006), формы дословно выше. **Форма: сильная и
недостаточная одновременно** — точка смены владельца названа и работала; беда в том, что владельцев два
рода, а граница проведена по одному.

**`DurableProjectLogAppender`** (44 строки) — тот же отбор по метке проекта, но события уходят в очередь на
сохранение, а не в память.
*Связи:* подключён в `logback-spring.xml:14` как `DURABLE_PROJECT_LOG` | передаёт в
`ProjectLogFlushQueue` | оттуда `ProjectEventLogService` пакетами пишет в таблицу; читают
`SystemStatusController` и `SystemSettingsService`.
*Ценность:* без него история проекта исчезает при пересоздании контейнера.
*Комментарий:* **ядро, и заведён по прямому указанию оператора** — в javadoc сохранена дата и сама фраза:
26 июля 2026, «лог проекта должен независеть от деплоев». Устройство осторожное: берутся только записи
уровня INFO и выше, «forensic project history, not a full trace log»; строка с непарсируемым
идентификатором молча отбрасывается вместо исключения в потоке, который пишет лог.

Здесь же — правильно решённый случай того же рода, что в разделе XXIб был решён неправильно. Очередь
ограничена 20 000 записей, и при переполнении новая запись **отбрасывается**, а не роняет систему: javadoc
называет цену прямо — «a DB outage degrades to „recent history missing“, never an OOM». Это осознанный
выбор потерять свидетельство, а не поток. Сравнить с `parseLeanValue`, где неизвестное превращалось в
утвердительный вердикт: там потеря маскировалась под знание, здесь потеря названа потерей.
*Философия:* `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: сильная по устройству, не мерена по свидетельству.** Снимок есть —
таблица, переживающая пересоздание контейнера, с идентификатором проекта и отметкой времени события. Но
самого восстановления на прошлый момент я не проводил, а образец требует именно свидетельства
воспроизведения. Что измерено — наличие пути сохранения; что не измерено — что по нему действительно
восстанавливается история.

## Что этот раздел меняет в моих прежних утверждениях

В разделе XXIб я предположил, что пункт 32 предписаний — план продукта в пространстве имён фабрики — может
корениться в умолчании `TargetContext`, и назвал соперничающее объяснение: компилятор берёт имена фабрики
из своего контекста независимо от поля цели. **Найденное здесь — свидетельство в пользу соперника, и
довольно сильное**: подтверждённый случай, когда имена механизмов фабрики попали в репозиторий заказчика
именно через контекст запроса, а не через поле цели. Своё прежнее предпочтение снимаю; ни одна из двух
гипотез не подтверждена, но у второй теперь есть задокументированный случай, а у первой нет.

Остаётся вопрос, на который у меня ответа нет: этот путь заражения закрыт 9 августа, а нарушения закона 26
наблюдались позже. Значит либо есть второй путь, которым имена фабрики попадают в контекст, либо позднейшие
случаи имеют другую причину. Не мерено.

# XXIж. Память о собственных отказах: журнал дефектов, предложения, цикл дизайна

Три сущности без стереотипной аннотации. Все три хранят то, что фабрика знает **о себе**, и все три несут в
себе датированные записи о происшествиях, которые их и породили.

**`DefectJournalEntity`** (107 строк) — одна запись о дефекте: тяжесть, разряд, подсистема-источник, род
дефекта, описание, числовое значение, и отдельно — эпик и **номер корневого образца** из
`ENGINEERING_INVARIANTS_CHARTER.md` (1–12).
*Связи:* заводят четыре подсистемы — `AccountHealthService` (6 прямых заводов),
`KaizenService` (3 вызова `recordDefect`), `ProjectFlowService` (2), `AutoMergeService` (1); служебный вход
`DefectJournalService.recordDefect` | читают **двенадцать**: `ProcessControlService`, `KaizenService`,
`OperationalTruthService`, `EvidenceCoherenceService`, `DeliveryRealityProducerService`,
`ProjectFlowService`, `AutoMergeService`, `AccountHealthService`, `InternalGeminiObserverController`,
`EvidenceNodeEntity`, `DefectJournalService`, `DefectJournalRepository`.
*Ценность:* без него отказ существует только как строка в логе, у которой нет ни тяжести, ни рода, ни
возможности быть посчитанной.
*Комментарий:* **ядро по замыслу и по числу читателей, и при этом с пустым главным полем.**

Замысел записан в самой сущности: `rootCausePatternId` связывает дефект с пронумерованным образцом устава,
«или null, когда корневая причина ещё не разобрана», — и javadoc объясняет, ради чего: это то, «what makes
Pareto analysis by CAUSE possible instead of only by which subsystem happened to notice the symptom».
Различение верное и важное: Парето по тому, кто заметил, называет самую громкую подсистему, а не самую
частую причину.

Замер: во всех измеренных местах поле не заполняется. `AutoMergeService:2650` передаёт `null` явно;
`ProjectFlowService:424,515` пользуется коротким конструктором, где этого поля нет вовсе. И сама фабрика это
записала — `ProcessControlService:357` выводит в описание строку «No underlying defect event carries a
rootCausePatternId yet - candidate new invariant pattern, uncategorized». То есть поле, заведённое, чтобы
сделать незнание явным, находится в состоянии незнания всегда, и механизм, который на него опирается, об
этом честно сообщает.

Второе, что видно из связей: четыре пишущих против двенадцати читающих, и шесть из тринадцати заводов —
из одной подсистемы, здоровья аккаунтов. Это тот же перекос, что в разделе XXIг у графа ограничения, только
мягче: там датчик был один, здесь их четыре при двенадцати потребителях. Любой разбор по Парето над таким
журналом сначала опишет, за чем наблюдают, и лишь потом — что ломается.
*Философия:* `DONALD_DEVIDSON_15_INUS_FACTOR_CHECK` (D007) — Дональд Дэвидсон,
`BARCAN-TAG-09 MORAL-DILEMMA`, принцип радикальной интерпретации, anchor *Truth and Meaning / radical
interpretation — interpretation and coherence*. Сильная дословно: «подозреваемая причина считается **одним
фактором достаточного набора**, пока альтернативы не исключены; со-факторы перечислены со свидетельством
присутствия или отсутствия каждого». Слабая: «названо первое объяснение, совпавшее с наблюдением».
Опровержение: «назвать вторую гипотезу, дающую то же наблюдение; её отсутствие означает, что сравнения не
было». **Форма: слабая, и по двум причинам сразу.** Место для причины в схеме есть, но пусто, поэтому
со-факторы не перечисляются ни для одного дефекта; а множество наблюдаемых подсистем перекошено, поэтому
вторая гипотеза чаще всего и не может возникнуть — о ней некому донести. Схема сильной формы готова, данных
под неё нет.

**`KaizenProposalEntity`** (147 строк) — сохраняемое предложение об улучшении: заголовок, разряд, целевой
механизм, описание действия, ожидаемая выгода, состояние, базовая метрика и **счётчик повторов**.
*Связи:* `KaizenService:112` пишет через `fromDomain`, `KaizenService:78,154` читает через `toDomain` |
остальной код (`KaizenController`, `ProjectTreeService`) продолжает работать с простым доменным объектом:
преобразование живёт на границе хранения.
*Ценность:* без него предложения жили в памяти службы и стирались при каждом перезапуске, оставляя после
себя одну строку в логе, — это записано в javadoc как закрытая 5 августа 2026 брешь.
*Комментарий:* **ядро, и здесь сделана вещь, которой в перечне больше нигде нет: улучшение сделано
опровержимым.**

`recurrenceCount` несёт свой замер: до 20 августа 2026 путь записи не имел личности, тогда как путь чтения
сводил дубликаты по паре «разряд + целевой механизм», — и **347 строк несли 10 настоящих проблем**, а
повторение было неотличимо от новой беды. Смысл счётчика назван прямо: «the count is what makes an applied
improvement refutable — if it keeps rising after a micro-step was applied, the improvement did not hold».

Это редкий случай, когда механизм несёт собственное условие опровержения. Предложение об улучшении обычно
закрывается тем, что кто-то объявил его выполненным; здесь у него есть наблюдение, способное сказать
«не подействовало», и это наблюдение снимается само, без участия того, кто улучшал.
*Философия:* `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — это не заслон». **Форма: сильная по устройству, не мерена по покраснению.** Проверка написана
до принятия утверждения — счётчик растёт независимо от того, объявлено ли улучшение применённым. Но я не
показывал, что она краснеет: подстроить неподействовавшее улучшение и увидеть рост счётчика значило бы
трогать продуктовый код. Что измерено — что признак существует и снимается не тем, кто улучшал.

**`DesignShopCycleEntity`** (118 строк) — одна строка на проект: готовность дизайн-цеха по фронту, стадия
цикла, путь черновика и **объявленные цвета и шрифты проекта**.
*Связи:* читают `DesignShopOrchestrationService`, `DesignSystemFalsificationService`, `JulesDispatchService`,
`DesignShopCycleRepository`.
*Ценность:* без неё проект, много тактов подряд остающийся готовым к сборке, запускал бы по циклу дизайна
на каждый такт.
*Комментарий:* **ядро малого радиуса, и две вещи здесь сделаны верно.**

Первое — **срабатывание по фронту, а не по уровню**: поле `lastWasReady` хранится, поэтому один цикл
начинается на один переход из «не готов» в «готов», и новый цикл возможен, только когда готовность
по-настоящему падала и поднималась снова. Это то же средство, что `finalizing` у требования из раздела XXIб,
приложенное к другому месту: состояние заводится специально, чтобы повтор не был неотличим от первого раза.

Второе — **основание берётся, а не выдумывается**. Цвета и шрифты захватываются из **первой** генерации
этого проекта, и комментарий говорит «never invented», чтобы последующая проверка сравнивала с собственной
маркой проекта, а не с подставкой. Оговорю границу этого достоинства: основание верно как **происхождение**,
но ничем не подтверждено как **правильное**; если первая генерация была не в марке, оснований для сомнения
у механизма нет. Прежде измеренное мною отвержение восьми экранов из восьми при доле прослеживания около
0,09 против требуемых 0,9 (замер прошлой смены, здесь не повторён) с этим согласуется, но причиной я его не
называю: чтобы связать, нужно сравнить захваченное основание с настоящей маркой заказчика, а такого замера
у меня нет.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается на
собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная.** Основание — не рассказ механизма о том, какой марки
проект, а сохранённые идентификаторы настоящей первой генерации; предъявить источник можно, он в двух
столбцах. Слабой формы здесь нет ровно потому, что выдумывание запрещено явно и заменено захватом.

# XXIз. Состояние задачи и точка входа: последнее несущее слоя 2

**`TaskStatus`** (9 значений: `queued claimed in_progress pending_review review done failed spike_completed
blocked`) — пространство состояний задачи, **самое широкое на фабрике**: имя встречается в 40 файлах `main`.
*Связи:* читают 40 файлов | правило `isTerminal()` исполняется в `TaskEntity.setStatus:175-181` | обход
`initializeStatus` зовут 5 механизмов (`OpsAuditorService`, `PlannedWorkRecoveryService`,
`TechnicalLeadCompiler`, `ProjectFlowService` — 9 мест, `MarketResearchService`).
*Ценность:* без него нет ни понятия «попытка завершена», ни защиты от возврата завершённой задачи в работу.
*Комментарий:* **ядро, и здесь запрет исполнен полностью — редкий случай в этом перечне.**

Правило записано в самом типе как двусторонняя связь: конечное состояние ⟺ статус принадлежит
{`done`, `failed`, `spike_completed`}, и конечность необратима (закон 20, инвариант S2). Существенно, что
это **не осталось javadoc'ом**: `setStatus` бросает `IllegalStateException`, если нынешнее состояние
конечно и новое от него отлично, и сообщение называет задачу и оба состояния, то есть отказ объясним.

Обходной путь есть — `initializeStatus` пишет поле напрямую, минуя проверку. Замер по всем найденным
местам вызова: **каждое ставит `TaskStatus.queued`**, то есть обход употребляется ровно так, как объявлен
в его javadoc, — при заведении новой сущности. Ни одного места, где им ставилось бы конечное или
промежуточное состояние, замер не нашёл.

Заслон существует и снаружи типа: `test/java/com/eneik/production/models/persistence/TaskEntityLaw20Test.java`
(контроль: в дереве 175 тестовых классов, инструмент видит), плюс `PrReviewEntityLaw20Test` и
`AutoMergeLaw20InvariantS4Test`.

Чего в типе **нет** — того же, чего нет во всех девяти пространствах состояний раздела XXIб: явного
«неизвестно». Задача, о которой ничего не установлено, и задача, поставленная в очередь, обе суть `queued`.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет записан в
документе или комментарии». Опровержение: «совершить запрещённое действие; если оно прошло — запрета нет,
есть пожелание». **Форма: сильная, и все три условия выполнены порознь**: путь отказа исполним (исключение),
причина объяснима (сообщение называет оба состояния и задачу), тест есть и назван. Опровержение выполнить
не удалось: единственный путь мимо проверки во всех измеренных местах ставит начальное состояние.
Второй образец: `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` (D007) — Джон Сёрл,
`BARCAN-TAG-12 SOCIAL-CONTRACT`, статусные функции и институциональные факты («X считается Y в контексте C»),
anchor *Speech Acts / The Construction of Social Reality — institutional facts*. Сильная дословно: «статус
создаётся **правилом**, и есть запись аудита о том, что правило применилось». Слабая: «статус присваивается
в коде там, где показалось уместным». Опровержение: «назвать правило, создающее статус; если названо место,
а не правило — регистра нет». **Форма: слабая.** Правило назвать можно — оно в типе, и это уже больше, чем
слабая форма обычно даёт. Но записи аудита о применении правила нет: отказ бросает исключение и не
оставляет следа в журнале дефектов или в событиях проекта. Правило есть, регистра нет.

**`EneikProductionApplication`** (82 строки) — точка входа, и в ней два решения, переживающие каждый
запуск: как исполняются миграции и что происходит с базой при остановке.
*Связи:* объявляет бин `FlywayMigrationStrategy` | `@PreDestroy` сжимает хранилище H2 | источник данных
внедряется необязательным, чтобы срезовые тесты поднимались без него.
*Ценность:* без стратегии миграций запуск определяется умолчанием Spring; без сжатия файл базы растёт
безвозвратно.
*Комментарий:* **ядро, и две половины его прямо противоположны по качеству.**

Первая половина — сжатие — сделана образцово, и её основание измерено, а не предположено: MVStore не
возвращает страницы файлу, когда процесс умирает, не закрыв хранилище, а этот контейнер умирал так
неоднократно (нехватка памяти, `wsl --shutdown`, зависший движок Docker); файл вырос до **1,84 ГБ**,
перестал помещаться в страничный кэш, и отказы чтения положили конвейер. Отдельно верно решены два случая:
любая ошибка сжатия **намеренно проглатывается** с названной ценой — «база, которая не сжалась, это
медленная база; исключение из `@PreDestroy` прерывает остановку, а это ровно то состояние, которое и
порождает разрастание»; и повторный вызов защищён сравнением-с-обменом, потому что 28 августа второй вызов
нашёл хранилище уже закрытым и оставил предупреждение, читающееся как отказ, — «a warning that is not a
problem teaches the reader to ignore warnings».

Вторая половина — стратегия миграций — `flyway.repair()` перед каждым `flyway.migrate()`. Замер:
`src/main/resources/application.properties:55` содержит `spring.flyway.validate-on-migrate=false`. То есть
сверка того, что применённая миграция не изменилась с тех пор, отключена **дважды и независимо**: настройкой
и вызовом восстановления, переписывающим контрольные суммы истории. Файлов миграций 137, повторяемых
(`R__`) — ноль (замер: `ls src/main/resources/db/migration | grep -c "^R__"`).

Следствие называю осторожно, потому что живую базу я не трогал: **изменение уже применённого файла миграции
не может остановить запуск.** Это не значит, что такое изменение происходило; это значит, что если бы оно
произошло, узнать об этом при запуске было бы нечем.
*Философия:* `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — это не заслон». **Форма: отсутствует, а не слабая.** Проверка, способная опровергнуть
утверждение «схема базы соответствует файлам», в этом механизме существует у Flyway и выключена в двух
местах. Опровергать нечем не потому, что заслон слаб, а потому, что его выключили намеренно.
Второй образец: `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: слабая.** Миграции есть и их 137, но их применённость не сверяется, а
значит «состояние на прошлый момент» определено файлами лишь до тех пор, пока файлы не менялись, — и
проверить это допущение механизм не даёт.

## Чем закрывается слой 2

Замер остатка: **119 классов** без записи, разложенные по признаку «несёт ли что-нибудь, кроме хранения».

    DTO и записи-переносчики        55
    прочее без поведения            42
    репозитории-интерфейсы          10
    ещё несут набор значений         6
      PrReviewEntity, GateStage, GreetingStatus, Status, WishlistItemType, TaskStatus

`TaskStatus` описан выше. `WishlistItemType` уже разобран в разделе XXIб — он назван в общей строке с
`WishlistItemStatus`, и признак записи, считающий только первое имя строки, его не видит; это оговорка к
признаку, а не пропуск.

Основание не считать остальные механизмами — то же, по которому в перечень попали девять перечислений и не
попали DTO: **удержать поток может лишь то, чьё изменение меняет поведение другого механизма.** У
переносчика нет ни правила, ни закрытого набора: переименование его поля ломает сборку, но не решение.
У репозитория-интерфейса поведение порождается Spring из имени метода и живёт в вызывающем. Это суждение,
а не замер, и я называю его суждением.

Одна поправка к признаку разделения слоёв, третья по счёту: он не видел `@SpringBootApplication`, поэтому
точка входа числилась классом без аннотации. Затрагивает один класс — тот, что описан выше.

# XXII. Миграции: запреты, которые исполняет база

Слой 3, 137 файлов. Первое, что нужно было измерить, — **чем они вообще различаются как механизмы**, потому
что «добавили столбец» и «запретили состояние» — разные вещи, и большая часть слоя есть первое.

    признак                        файлов
    NOT NULL                          65
    CREATE TABLE                      46
    FOREIGN KEY / REFERENCES          35
    CREATE INDEX                      30
    UPDATE (заполнение задним числом) 28
    UNIQUE                            12
    INSERT                             9
    DROP                               6
    CHECK (...)                        4

Контроль на слепоту грепа: тот же вызов находит `CREATE TABLE` в `V1__initial_schema.sql`, где он заведомо
есть. Признак «повторяемых миграций (`R__`) ноль» замерен отдельно.

Демаркация для этого слоя: миграция есть механизм, когда она **меняет множество возможных состояний базы**,
а не только её форму. Столбец, индекс и внешний ключ формы касаются; уникальность, проверка и заполнение
задним числом — множества состояний. По этому признаку из 137 механизмами оказываются немногие, и три из
них несут собственные замеры.

**`V137__compiler_task_identity_from_work.sql`** — даёт задаче компилятора тождество, выводимое из работы,
которую она делает, вместо нового тождества на каждый оборот.
*Связи:* добавляет `tasks.content_key` и индекс по нему | опирается на единственное место заведения таких
задач, а не на ограничение уникальности.
*Ценность:* без неё счётчик попыток закона 8 считает не то: каждый оборот заводит новую строку со своим
счётчиком, равным единице.
*Комментарий:* **ядро, и лучший разбор причины из всего, что я читал в этом хранилище.** Миграция несёт свой
замер: 5 сентября 2026 на проекте `test-fiftieth` заведено **92 задачи за день, 69 из них — дубли четырёх
предметов**; тридцать одна задача компилировала одно требование, тридцать — другое. Каждая отправленная
задача есть сессия внешнего исполнителя, поэтому дневной внешний бюджет был потрачен на четыре единицы
работы шестьюдесятью девятью запросами.

Разбор причины сделан так, как я обязан делать сам и часто не делаю: **прежний заслон не назван виноватым**.
«The existing guard was never wrong: it honestly saw no live carrier, because a sweep had marked the
previous one done.» Виновато отсутствие тождества: новое тождество на каждый оборот делает повторение
невидимым, и граница попыток «измеряла ничто». С тождеством от работы тот же запрос находит **ту же строку**
и увеличивает **её** счётчик — «so the bound finally measures what it names».

Отдельно отмечу отказ от уникального ограничения, объявленный с основанием: строки, заведённые до миграции,
ключа не несут, а законная история содержит много завершённых задач по одной и той же работе. Уникальность
обеспечивается тем, что место заведения одно, и это закреплено структурной проверкой — тем же способом,
каким закон 20 закрепляет места слияния.

Здесь же — связь через весь перечень. В разделе XXIж записан замер от 20 августа: у пути записи предложений
кайдзен **не было личности**, тогда как путь чтения сводил дубликаты, и 347 строк несли десять настоящих
проблем. Тут — 5 сентября, 92 строки на четыре предмета, та же болезнь в другой подсистеме, **шестнадцатью
днями позже**. Один класс дефекта, два независимых проявления, и во второй раз он стоил внешнего бюджета.
*Философия:* `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «до разделения модулей
объявлено, какой агрегат вправе менять каждую часть». Слабая: «классы разделены по размеру или по слоям».
Опровержение: «найти поле, которое пишут два сервиса». **Форма: сильная в объявлении, не мерена в
исполнении.** Владелец объявлен однозначно — заведение таких задач происходит в одном месте, и это названо
условием, на котором держится уникальность. Но опровержение образца я не выполнял: искать, нет ли второго
пишущего в `content_key`, значило бы отдельный обход, которого в этот такт не было.
Второй образец: `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: слабая, и миграция сама это признаёт.** Строки до неё ключа не несут:
задача, заведённая 4 сентября, и та же работа 6 сентября не опознаются как одна. Непрерывность введена
вперёд, а не назад, и это названо в тексте миграции, а не умолчано.

**`V82__operational_reality_findings.sql`** — заводит запись о расхождении между тем, что сессия сообщает о
себе, и тем, что показывает GitHub; и переписывает проверку «ровно один источник» с четырёх слагаемых на
пять.
*Связи:* заводит таблицу `operational_reality_findings` | добавляет пятый внешний ключ в `evidence_nodes` |
переписывает ограничение `chk_evidence_nodes_exactly_one_source`, введённое в
`V79__kaizen_proposals_and_evidence_nodes.sql`.
*Ценность:* без ограничения узел свидетельства может ссылаться на два источника сразу или ни на один, и
тогда связность свидетельств считается по объекту, которого нет.
*Комментарий:* **ядро, и это единственный измеренный мною случай, где онтологическое правило исполняет
база, а не код.** Проверка суммирует пять условных единиц и требует, чтобы сумма равнялась единице: у узла
свидетельства ровно один источник — ни двух, ни нуля. Нарушить это нельзя ошибкой в приложении: отвергнет
хранилище.

Основание тоже измерено и записано в самой миграции: происшествие 6 августа 2026, когда простой фабрики
остался незамеченным, потому что ни конвейер слияния, ни наблюдатель не имели **структурного** способа
увидеть расхождение «что заявляет исполнитель» и «что показывает GitHub». Пятый источник добавлен тем же
приёмом, что и четыре прежних, — то есть у механизма есть форма роста, а не только состояние.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет записан в
документе или комментарии». Опровержение: «совершить запрещённое действие; если оно прошло — запрета нет,
есть пожелание». **Форма: сильная, и сильнее, чем в коде.** Запрет исполняет хранилище, поэтому обойти его
нельзя даже намеренно из приложения; причина объяснима — имя ограничения её называет. Оговорю недостающее:
отдельного теста на это ограничение я не искал, а образец его требует, — так что «сильная» здесь опирается
на исполнимость и объяснимость, а третье условие **не мерено**.

**`V97__jules_session_pr_opened_workflow_claim.sql`** — вводит атомарное притязание на обработку завершения
сессии, закрывая гонку двойной обработки.
*Связи:* добавляет `jules_sessions.pr_opened_workflow_claimed_at` | названы оба гонщика: выметающий обход
`AutoMergeService` против собственного пуска `pollStatus`, либо два обхода внахлёст.
*Ценность:* без него оба вызова читали проверку «уже занято?» как «нет» прежде, чем чья-либо запись
ложилась, и одни и те же замечания, нарушения и запись о слиянии применялись дважды.
*Комментарий:* **ядро, и третий случай одного и того же лекарства в перечне.** То же средство, что
`finalizing` у требования (раздел XXIб) и что срабатывание по фронту у цикла дизайна (раздел XXIж):
завести состояние, которого раньше не было, чтобы опоздавший увидел его и отступил.

Отдельно отмечу, что здесь **пустому значению придан явный смысл**, и не один: `NULL` означает «не занято,
доступно для новой попытки», причём в это включён законный повтор после падения. То есть пустота не
«неизвестно» и не «ошибка», а названное третье состояние — ровно то, чего не хватало девяти пространствам
состояний из раздела XXIб.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“». **Форма: сильная по
хранению, не мерена по вызывающим.** Показано, как состояние хранится (отметка времени) и как разрешается
(притязание снимается по завершении либо возвращается обходом застрявших). Но опровержение требует
проверить всех читающих этот столбец, а я их не обходил.

## Суждение по слою

Из 137 миграций подавляющее большинство меняет форму хранилища, а не множество его возможных состояний, и
механизмами по принятой демаркации не является. Те, что являются, отличаются одним общим свойством:
**каждая несёт в себе замер происшествия, которое её вызвало, и разбор причины**. Это не украшение — это
единственное место в фабрике, где причина изменения хранится рядом с изменением и переживает его.

Из этого следует и главная слабость слоя, уже измеренная в разделе XXIз: сверка применённых миграций
отключена дважды. Механизмы, которые точнее прочих объясняют, **почему** база устроена так, лежат в файлах,
соответствие которых базе при запуске никто не проверяет.

# XXIIб. Тождество в схеме: чем фабрика решает, что две вещи — одна

Продолжение слоя 3. Двенадцать миграций несут уникальность, и это не техническая подробность: **ограничение
уникальности есть объявление о том, что фабрика считает одним и тем же предметом.** Ошибиться здесь — не
ошибка в коде, а неверная онтология, и лечится она не заплатой, а переопределением тождества.

Ниже — четыре такие миграции, из них три составляют последовательность, в которой фабрика **дважды подряд
исправила саму себя**.

**`V42__add_pr_review_has_code_and_role_threads.sql`** — заводит ветку-продолжение: одну живую нить на пару
«проект и роль», чтобы следующая задача той же роли начиналась с накопленной ветки, а не с `main`.
*Связи:* заводит таблицу `role_threads` с ограничением `UNIQUE (project_id, role_tag)` | добавляет
`pr_reviews.has_code` | по замеру таблицу (уже под новым именем) читают `FeatureThreadEntity` и
`ProjectFlowService`.
*Ценность:* без неё каждая задача роли начинает с нуля и теряет всё, что роль уже сделала.
*Комментарий:* **ядро по замыслу, и здесь же — первая формулировка тождества, оказавшаяся неверной.**
Отмечу отдельно верное решение рядом: `has_code` объявлен допускающим пустоту, и основание названо —
«historical rows are left unclassified rather than guessed at retroactively». То есть прошлое не
дописывается задним числом догадкой; неизвестное остаётся неизвестным. Это ровно то, чего не хватало
пространствам состояний из раздела XXIб, и сделано здесь на семь десятков миграций раньше.
*Философия:* `AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` (D002) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «у объекта есть владелец,
личность, жизненный цикл и семантика удаления, и он привязан к агрегату или каноническому реестру».
Слабая: «класс с полями и репозиторием, у которого нет ответа на вопрос „кто вправе его удалить“».
Опровержение: «назвать две точки кода, удаляющие объект по разным правилам; если они есть — реестра нет».
**Форма: сильная по объявлению.** Владелец назван внешним ключом на проект, семантика удаления объявлена
каскадом, личность задана ограничением. Опровержение не выполнялось: две точки удаления по разным правилам
я не искал.

**`V44__add_features.sql`** — вводит эпик как устойчивую единицу группировки всего, что разложено из одного
корневого пожелания клиента, и **сужает тождество нити** до тройки «проект, эпик, роль».
*Связи:* заводит `features`; добавляет `feature_id` в `wishlist`, `tasks` и `role_threads`; снимает прежнее
ограничение и ставит `UNIQUE (project_id, feature_id, role_tag)` | эпик чеканится лениво, один раз, в
`TechnicalLeadCompiler.createAndSaveTask` через `FeatureService.resolveOrCreateFeatureId` | таблицу читают
не менее пяти механизмов, среди них `AutoMergeService`, `ClientDeliverableReadinessService`,
`CommandDashboardService`.
*Ценность:* без эпика нет единицы, по которой меряется завершённость, и нет способа отличить одну работу
роли от другой её работы.
*Комментарий:* **ядро.** Основание исправления названо предметно, а не технически: прежний ключ «слишком
груб, поскольку одна роль за жизнь проекта делает много несвязанных эпиков». Это суждение о предметной
области, а не о коде, — и именно так и должно выглядеть исправление тождества.

Ленивая чеканка здесь существенна и согласуется с уже описанным в перечне `FeatureService`: эпик заводится
не на всякую строку пожеланий, а только на ту, что действительно стала работой. Эпик, заведённый на
пожелание, которое работой не станет, навсегда испортил бы знаменатель завершённости.
*Философия:* `ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` (D009) — Алонзо Чёрч,
`BARCAN-TAG-08 SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda
calculus and Church's thesis — formal computability*. Сильная дословно: «до замены кода, зависимости, модели
или схемы доказано сохранение под значимыми наблюдениями». Слабая: «заменили, тесты зелёные». Опровержение:
«назвать наблюдение, под которым доказывалось сохранение; „все тесты“ ответом не является, если тесты не
покрывают предмет замены». **Форма: сильная, и ответ на опровержение дан в самой миграции.** Наблюдение
названо прямо: `role_threads` на этот момент пуста, весь прежний опытный прогон удалён, — «so narrowing the
key to include feature_id needs no data migration». Сохранение доказано не тестами, а тем, что сохранять
нечего, и это сказано вслух, а не подразумевается.

**`V45__rename_role_threads_to_feature_threads.sql`** — второе исправление подряд: ветка принадлежит
**эпику**, а не паре «эпик и роль».
*Связи:* переименовывает таблицу; `role_tag` становится `last_role_tag` и допускает пустоту; `feature_id`
становится обязательным; ключ сужается до `UNIQUE (project_id, feature_id)`.
*Ценность:* без этого исправления две роли, ведущие одну цепочку зависимостей, вынуждены расходиться на
разные ветки.
*Комментарий:* **ядро, и самое поучительное место слоя.** Основание снова предметное: «эпик обыкновенно
вовлекает несколько ролей — бэкенд, фронтенд, дизайн, — работающих одну цепочку зависимостей, и любая из
них вправе продолжать на той же ветке; совпадать обязан только владеющий аккаунт, но никогда не роль».

Существенно, что роль не выброшена, а **переведена в другой разряд**: из части тождества она стала следом —
`last_role_tag`, «кто трогал последним». Это в точности развод смысла и référens: одно и то же поле сначала
отвечало на вопрос «что это за нить», а теперь отвечает на «кто её последним вёл», и второй вопрос ответа
на первый не даёт.

Две поправки подряд к одному тождеству — это не признак небрежности, а признак того, что тождество здесь
**подлежит проверке опытом**, и опыт его дважды опроверг. Оба раза миграция честно указывает, что таблица
пуста, то есть цена исправления была нулевой — и оба раза это сказано, а не умолчано.
*Философия:* `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — Дэвид Чалмерс,
`BARCAN-TAG-02 RIGID-DESIGNATOR`, принцип двумерной семантики, anchor *Two-Dimensional Semantics — primary
and secondary intensions*. Сильная дословно: «отображаемое имя, сохраняемый идентификатор и сущность в API
разведены так, что перепутать их нельзя». Слабая: «одно поле служит всем трём». Опровержение: «изменить
отображаемое имя и посмотреть, не поехали ли ссылки». **Форма: сильная, и достигнута именно этой
миграцией.** До неё роль служила и признаком тождества, и сведением о последнем участнике — одно поле на
два назначения, то есть слабая форма дословно. После неё тождество несут проект и эпик, а роль осталась
сведением, и перепутать их нельзя: имя поля изменено вместе с назначением.
Второй образец: `AHILLE_VARTSI_04_ESSENCE_BEFORE_OPTION` (D002), тот же философ и якорь. Сильная дословно:
«до добавления настройки назван инвариант, истинный во **всех** допустимых режимах, и ветви конфигурации
либо его хранят, либо падают закрыто». Слабая: «настройка добавлена, режимы описаны в README».
Опровержение: «найти комбинацию значений, при которой инвариант не назван и не проверен». **Форма: не
мерено.** Инвариант назван словами («совпадать обязан только владеющий аккаунт»), но проверяется ли
совпадение аккаунта где-либо в коде, я не измерял; без этого называть форму нельзя.

**`V30__create_jules_activity_responses.sql`** — хранит вопросы исполнителя и ответы на них, отождествляя
вопрос **по хешу его содержания** внутри сессии.
*Связи:* заводит `jules_activity_responses` с `UNIQUE (jules_session_id, activity_hash)` | читают
`JulesActivityResponseEntity` и `ProjectFlowService`.
*Ценность:* без него один и тот же вопрос, заданный дважды, порождает две строки, и ответ на него
приходится искать угадыванием.
*Комментарий:* **периферия по радиусу, ядро по приёму.** Приём тот же, что через сто с лишним миграций
применит `V137`: **тождество выводится из содержания, а не чеканится заново**. Разница в том, что здесь оно
введено сразу и подкреплено уникальным индексом, а в `V137` — задним числом и без ограничения, потому что
прежние строки ключа не несут. То есть фабрика знала это средство с самого начала и не применила его там,
где оно стоило дневного внешнего бюджета.
*Философия:* `AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` (D002), формы и опровержение дословно выше.
**Форма: сильная.** Личность объекта задана содержанием и областью (сессией), владелец назван внешним
ключом на сессию, а уникальный индекс делает нарушение невозможным, а не нежелательным.

## Суждение по этой части слоя

Двенадцать ограничений уникальности — это двенадцать заявлений фабрики о том, что считать одним предметом.
Три из них показывают, что такое заявление **может быть ошибочным и проверяется опытом**: тождество нити
переопределялось дважды, оба раза по доводу из предметной области, и оба раза с честным указанием цены.

Отмечу и обратное: дважды фабрика **сознательно отказалась** от уникальности с названным основанием —
`V48` (у отставленного работника строка остаётся) и `V137` (законная история содержит много завершённых
задач по одной работе). Отказ с основанием — тоже механизм, и он лучше, чем ограничение, поставленное
наугад.

# XXIIв. Миграции, возвращающие отнятое: восемь починок подряд по одному предмету

Двадцать восемь миграций дописывают прошлое. Разложив их по тому, **что именно** они присваивают старым
строкам, я нашёл не разрозненные правки, а один сюжет: **восемь файлов подряд возвращают в поток требования
заказчика, которые фабрика сама же отвергла.** По именам файлов:

    V122  void_verdicts_reached_without_a_readable_reason
    V126  return_the_budget_of_a_brief_refused_without_a_readable_reason
    V129  reopen_client_requirements_whose_every_task_failed
    V130  return_client_slices_that_never_became_a_task
    V131  return_client_slices_the_grouper_merged_away
    V132  return_the_two_requirements_the_grouper_took_from_V129
    V133  return_the_two_requirements_the_dead_task_veto_took_back
    V134  restore_the_delivery_findings_the_grouper_merged_away

Имя `V132` говорит главное: **починка, отнятая следующей починкой.** Это не список правок, это след того,
как требование заказчика теряется, и каждый раз возвращается рукой.

**`V126__return_the_budget_of_a_brief_refused_without_a_readable_reason.sql`** — возвращает бюджет разбора
требованию, отвергнутому по свидетельству, которого никто не может прочесть.
*Связи:* обновляет `wishlist.compile_attempts` | опирается на отсутствие следа отказа в `tasks` |
повторяет исправление `V122` «по тому же основанию, на том же правиле».
*Ценность:* без него требование заказчика остаётся в поглощающем отказе навсегда, а основания отказа не
существует.
*Комментарий:* **ядро, и лучший образец осторожной починки во всём хранилище.** Замер приведён с точностью
до минуты: 29 августа 16:57 собственное требование заказчика (Moodle/LMS, возвращённое `V125` часом
раньше) стояло при трёх попытках из трёх с отметкой достижения компилятора — то есть в состоянии
`decompositionRefused()`, поглощающем и более не отправляемом. Три его задачи-носителя названы
идентификаторами, и **ни одна из трёх не оставила читаемого следа отказа**, потому что все три шли на
образах, собранных до починки, сохраняющей условие.

Причина названа отдельно и точно: отвергающее условие «вычислялось, отправлялось исполнителю внутри
сообщения о правке, печаталось в журнал и не сохранялось нигде, так что жило ровно столько, сколько
процесс, который его решил».

Главное же в устройстве починки: она **сама себя ограничивает**. Условие срабатывания — отсутствие следа
отказа; как только носители начинают его записывать, предикат перестаёт совпадать, и вердикт, вынесенный с
читаемым основанием, остаётся нетронутым. То есть это не разрешение перезапускать вечно, а исключение с
собственным сроком истечения, вшитым в его же условие.
*Философия:* `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` (D012) — Айзек Леви,
`BARCAN-TAG-04 MODAL-QUANTIFIER`, принцип фиксации доксастических состояний, anchor *The Fixation of Belief
and Its Undoing / Enterprise of Knowledge — doxastic commitment*. Сильная дословно: «у исключения есть срок,
область, утвердивший и компенсирующая проверка». Слабая: «флаг `skipValidation` с комментарием».
Опровержение: «найти исключение без срока — оно вечное, а вечное исключение есть новое правило».
**Форма: сильная.** Срок есть и он не календарный, а условный — исключение истекает само, как только
появляется след отказа; область сужена до требований клиента одного проекта без записанной причины
отклонения; утвердивший назван пунктами плана; компенсирующая проверка — тот самый предикат отсутствия
следа. Опровержение выполнить не удалось: исключения без срока здесь нет.

**`V132__return_the_two_requirements_the_grouper_took_from_V129.sql`** — возвращает два требования
заказчика, которые вернула `V129` и **отнял обратно свод похожих пунктов** в ту же ночь.
*Связи:* повторяет предикат `V129` дословно | указывает на `ProjectFlowService.areWishlistItemsSimilar` как
на исправленное вперёд место | ссылается на `unDismissFeatureIfNeeded` как на самовосстановление эпика.
*Ценность:* без него два требования заказчика остаются отвергнутыми как «дубликаты», не будучи ими.
*Комментарий:* **ядро, и самая поучительная запись слоя, потому что в ней разобрана не поломка, а
взаимодействие двух исправных механизмов.**

История восстановлена по журналу с точностью до секунды и приведена дословно: `V129` вернула требования в
поток; ближайший же оборот оркестрации начинается со свода похожих пунктов, и в 00:33:34 он слил их в срез
другого эпика и отверг — **внутри той же ночи, прежде чем что-либо успело быть отправлено**. Обе строки
журнала процитированы.

Разбор причины сделан так, как я обязан делать и часто не делаю: предикат `V129` **признан верным** и
переприменён дословно, а виновным назван не он, а дефект, который его отменил. И сам дефект назван по
существу: два среза одного требования никогда не дубликаты друг друга, а сравнивать скомпилированный срез
надо по его задаче и условиям приёмки — **а не по заголовку, который сгенерировала сама фабрика**.

Последнее и есть ошибка рода, третий раз встреченная мною в этом перечне: собственное изделие механизма
принимается за свидетельство о предмете. В разделе XXIе так журнал фабрики был выдан за деятельность
продукта; в разделе XXIг так единственный размеченный шаг стал «узким местом фабрики»; здесь так
сгенерированный фабрикой заголовок стал признаком тождества требования заказчика.
*Философия:* `AYZEK_LEVI_15_PRINCIPLED_INTEGRITY` (D012), тот же философ и якорь. Сильная дословно:
«отвергнута местная починка, удовлетворяющая букве правила и нарушающая объявленный принцип; названы
принцип и поведение, его хранящее». Слабая: «правило выполнено буквально». Опровержение: «спросить, какой
принцип стоит за правилом, и проверить, сохранён ли он». **Форма: сильная.** Местная починка — просто
вернуть строки ещё раз — здесь недостаточна и прямо названа недостаточной: «the reason it needs re-applying
is not the predicate but the defect that undid it», и указано поведение, хранящее принцип, — сравнение по
задаче и условиям приёмки вместо сравнения по собственному заголовку. Правило («не отвергай требование
заказчика без основания») исполнено не буквально, а по стоящему за ним принципу.
Второй образец: `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — Гилберт Райл,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип различия «знать что» и «знать как», anchor *The Concept of Mind —
knowing-how versus knowing-that, category mistakes*. Сильная дословно: «назван тип, схема или переходник,
удерживающий границу рода: процесс не выдаётся за объект, наблюдение за полномочие, политика за данные».
Слабая: «мы понимаем разницу». Опровержение: «найти место, где значение одного рода присваивается полю
другого без преобразования». **Форма: слабая до исправления, и об исправлении у меня замера нет.**
Значение рода «заголовок, сочинённый фабрикой» служило признаком рода «тождество требования заказчика».
Что исправление вперёд действительно применено, миграция утверждает, а я не проверял.

**`V104__observation_instrument_failure.sql`** — отделяет наблюдение о продукте от наблюдения о собственном
приборе.
*Связи:* добавляет `client_runtime_observations.instrument_failure` | заполняет его для прежних строк
**узко** — только по подписи «пускатель недостижим», которую пишет собственный перехватчик
`RuntimeLauncherClient` | потребитель различения — `BetaPosterior`, перестающий считать строки о приборе.
*Ценность:* без него отказ прибора входит в историю продукта как его собственная неудача.
*Комментарий:* **ядро, и здесь измерена обратная связь с неверным знаком.** Первая строка файла и есть
формулировка: «наблюдение, которого не было, не есть наблюдение неудачи». Возвращаемое значение пускателя
несло два разных факта одним полем: пускатель ответил и сообщил, что сборка не поднялась (факт о
**продукте**), и пускатель не ответил вовсе (факт о **приборе**).

Последствие названо числами: строка от 19 августа 16:42:04Z записывает «runtime-launcher unreachable: Read
timed out»; продукт не падал — его не пробовали. Эта строка сдвинула апостериорное распределение с
Beta(1,3) на Beta(1,4), что растянуло следующую проверку с 7,2 до 9,7 часа. То есть **каждый отказ прибора
отодвигает следующую попытку дальше** — и это названо причиной, по которой цель запуска недостижима по
построению.

Отдельно отмечу решение, совпадающее с правилом, по которому я обязан работать: строка **не удалена, а
помечена** — «событие произошло, неверен был лишь его предмет». И заполнение задним числом сделано узко и с
основанием: отказ сборки, сообщённый самим пускателем, есть настоящее наблюдение о продукте и обязан
остаться посчитанным.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается
на собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер
снят; её отсутствие и есть нарушение». **Форма: сильная.** Столбец отвечает ровно на вопрос «о чём эта
строка есть факт», источник признака назван поимённо — подпись перехватчика в клиенте пускателя, — и
предъявить его можно. Это то самое различение, которое `WishlistEntity` делает предикатами
`decompositionRefused` и `decompositionUnreached` (раздел XXI): свидетельство о предмете против
свидетельства о себе, здесь введённое в хранилище.

## Суждение по этой части слоя

Заполнение прошлого задним числом на этой фабрике устроено лучше, чем я ожидал: из прочитанных ни одна
миграция не додумывает неизвестное. `V42` оставляет старые строки неразобранными, `V104` помечает, а не
удаляет, `V126` истекает сама. Догадки задним числом я не нашёл — но оговорю: прочитал я не все двадцать
восемь, и это утверждение о прочитанных.

Тревожно другое, и это замер, а не впечатление: **восемь миграций подряд заняты возвращением требований
заказчика**, а одна из них возвращает отнятое предыдущей починкой. Каждая написана рукой, каждая точна, и
каждая есть свидетельство, что поток теряет предмет своей работы быстрее, чем механизмы успевают этому
помешать.

# XXIIг. Изъятия: три раза, когда из хранилища убирали навсегда

Шесть миграций из 137 содержат удаление. Три из них — замена ограничения, уже описанная в разделе XXIIб.
Остаются три настоящих изъятия, и все три поучительны, потому что **сделаны по тому самому правилу, по
которому обязан работать и я: ничто не удаляется без доказательства.** Один раз правило было соблюдено — и
изъятие всё равно оказалось ошибкой.

**`V19__restructure_accounts_and_projects.sql`** — заводит общий пул аккаунтов: переносит записи из
настроек в аккаунты, вводит признак `enabled` и **выводит из строя все аккаунты, кроме двух названных
поимённо**.
*Связи:* добавляет `accounts.api_key`, `accounts.github_username`, `accounts.enabled` | переносит данные
`INSERT INTO accounts ... SELECT ... FROM jules_configs` | архивирует все проекты, кроме новейшего |
удаляет таблицу `jules_configs` и ссылку на неё.
*Ценность:* без неё нет общего пула — аккаунт привязан к проекту, и работу нельзя раздать.
*Комментарий:* **ядро, и здесь же рождён признак, стоивший семичасового простоя.** Столбец `enabled`
появляется именно тут, со значением по умолчанию «истина», и старое значение переносится из прежней
таблицы. Это тот самый признак, который 5 сентября был ложным у пяти аккаунтов при состоянии «свободен»,
и полсмены ушло на выяснение, кто их выключил (раздел XXIб, запись `AccountStatus`).

Но существеннее другое, и это видно только в тексте миграции: состав пула задан **списком имён**:

    UPDATE accounts SET status = 'decommissioned'
    WHERE project_id IS NOT NULL OR (name NOT IN ('eneikdru', 'eneikcoworking-ctrl'));

То есть право участвовать в работе фабрики получено не по правилу, а по перечислению двух строк в файле
миграции. Всякий аккаунт, чьё имя в этот список не попало, выведен из строя навсегда — и узнать, почему
именно эти два, из схемы нельзя.
*Философия:* `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` (D007) — Джон Сёрл,
`BARCAN-TAG-12 SOCIAL-CONTRACT`, статусные функции и институциональные факты («X считается Y в контексте
C»), anchor *Speech Acts / The Construction of Social Reality — institutional facts*. Сильная дословно:
«статус создаётся **правилом**, и есть запись аудита о том, что правило применилось». Слабая: «статус
присваивается в коде там, где показалось уместным». Опровержение: «назвать правило, создающее статус; если
названо место, а не правило — регистра нет». **Форма: слабая, и опровержение выполняется дословно.**
Назвать правило, по которому аккаунт остаётся в пуле, нельзя — можно назвать только место: строку
`name NOT IN (...)` в `V19`. Статус «выведен из строя» создан перечислением, а не признаком.

**`V58__drop_project_event_log_and_watermark.sql`** — убирает журнал событий проекта и отметку наблюдателя
после того, как наблюдатель перестал читать собственный лог бэкенда.
*Связи:* удаляет таблицы `project_event_log` и `project_observer_watermark`, заведённые `V56` и `V57` в тот
же день.
*Ценность:* изъятие снимает две таблицы, которые на тот момент никто не читал.
*Комментарий:* **периферия по действию, ядро по уроку — и урок этот прямо про моё собственное правило.**

Миграция соблюдает всё, что положено. Она **не удаляет применённый файл миграции**, а отменяет вперёд, и
основание названо: «never delete an already-applied migration file». Она предъявляет доказательство:
«nothing else in the codebase reads either table (confirmed via grep before removing their producing
code)» — то есть проба проведена и названа поимённо.

И тем не менее изъятие было ошибкой, исправленной **на следующий день**. Причина, названная в `V61`,
разделяет две вещи, слитые в одну: «That fix was about Gemini no longer READING the backend's own log, not
about deleting the log itself.» Область правки была расширена сверх её предмета.

Отсюда урок, который я записываю прежде всего для себя. Греп доказал отсутствие **нынешних** читателей. Он
не мог доказать отсутствие **ценности**, потому что ценность журнала не в том, что его кто-то сейчас
читает, а в том, что он есть, когда понадобится. Доказательство «этого никто не использует» и
доказательство «это не нужно» — разные доказательства, и первое я привык принимать за второе.
*Философия:* `AHILLE_VARTSI_04_ESSENCE_BEFORE_OPTION` (D002) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «до добавления настройки назван
инвариант, истинный во **всех** допустимых режимах, и ветви конфигурации либо его хранят, либо падают
закрыто». Слабая: «настройка добавлена, режимы описаны в README». Опровержение: «найти комбинацию значений,
при которой инвариант не назван и не проверен». **Форма: слабая.** Инвариант — «журнал проекта не зависит
от выкладок» — не был назван до изъятия; он был произнесён после, оператором, и стал основанием возврата.
Изъятие проверяло режим «кто читает сейчас», а инвариант относился к режиму «что понадобится потом».

**`V61__create_project_event_log.sql`** — заводит журнал заново, теперь как долговечное основание, не
зависящее от выкладок.
*Связи:* создаёт `project_event_log` с внешним ключом на проект и каскадом удаления, индекс по проекту и
времени | включает настройку `project_event_log_enabled` со значением «истина» | в него пишет
`DurableProjectLogAppender` через `ProjectLogFlushQueue` (раздел XXIе).
*Ценность:* без него история проекта исчезает при пересоздании контейнера.
*Комментарий:* **ядро, и в тексте сохранены слова оператора и разбор ошибки.** Указание приведено дословно:
«лог проекта должен независеть от деплоев!! это огромное упущение», и назначение очерчено с обеих сторон —
для внешних агентов и разбора оператором, **никогда не подаётся наблюдателю напрямую**. То есть при
восстановлении сохранено и то, ради чего убирали: причина изъятия (не кормить наблюдателя собственным
логом) осталась в силе, а вместе с водой не выплеснут журнал.

Отдельно отмечу строку «On by default - this is now baseline infrastructure, not an opt-in experiment»:
настройка заводится сразу включённой, потому что вещь перестала быть опытом. Это редкий случай, когда
переход из опыта в основание объявлен явно, а не случается молча.
*Философия:* `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: сильная по устройству, не мерена по свидетельству.** Снимок есть и
пересоздание контейнера переживает — это и было целью. Но восстановления на прошлый момент я не проводил, а
образец требует именно свидетельства воспроизведения; и оговорю прямо: **промежуток между `V58` и `V61`
восстановить нельзя**, те записи изъяты.

**`V121__drop_needs_human_review.sql`** — убирает таблицу, в которой ветвь работы заканчивалась ожиданием
человека.
*Связи:* удаляет `needs_human_review` | называет оба прежних пишущих пути и то, чем они заменены —
задача, исчерпавшая бюджет отправки, остаётся `blocked`, откуда её снимает
`createRecoveryWishlistForOrphanedBlockedTasks` и однажды возобновляет `PlannedWorkRecoveryService`;
носитель-компилятор, не давший годного плана, закрывает свой запрос и завершается.
*Ценность:* изъятие убирает тупик — состояние, из которого фабрика сама выйти не может.
*Комментарий:* **ядро, и это изъятие сделано образцово по всем трём условиям.** Основание — прямое
указание оператора, приведённое по смыслу: фабрика автономна, и ветвь, кончающаяся ожиданием человека,
есть тупик. Доказательство ненужности предъявлено дважды: `AutoMergeService` уже записал рядом со своей
удалённой записью, что «строка `needs_human_review` тут была тупиком, эту таблицу никто не читает», и
замер того дня: **18 строк, из них 8 — о задачах, уже сделанных**. То есть таблица не просто не читалась,
она вводила в заблуждение почти наполовину.

И главное отличие от `V58`: здесь **названо, что происходит вместо**. Изъятие не оставляет дыры, оно
переносит обязанность на уже существующее поведение, и оба пути перечислены поимённо. Это и есть разница
между «это никто не использует» и «это не нужно, потому что нужное делает вот что».
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«запрет — **исполнимый путь отказа** с объяснимой причиной, и на него есть тест». Слабая: «запрет записан в
документе или комментарии». Опровержение: «совершить запрещённое действие; если оно прошло — запрета нет,
есть пожелание». **Форма: сильная по механике, не мерена по тесту.** Запрещённое действие — «завершить
ветвь ожиданием человека» — стало невыразимым: таблицы, в которую это писалось, не существует, и запись в
неё не соберётся. Это тот же способ, каким удаление значения `idle_generation` сделало невыразимым
самопридуманное улучшение (раздел XXI, `WishlistSource`). Теста, закрепляющего отсутствие, я не искал.

## Суждение по этой части слоя

Три изъятия — три разных качества, и различает их не осторожность, а то, **что именно доказывали**.

`V121` доказал, что убираемое не нужно, назвав поведение, которое делает нужное вместо него. `V58` доказал
лишь, что убираемое никем не читается, — и был отменён через день. `V19` не изымал, а **выводил из строя по
списку имён**, не назвав правила вовсе, и последствие этого решения дожило до сентябрьского простоя.

Для меня отсюда следует поправка к собственному правилу. «Ничто не удаляется без доказательства, что оно
есть в другом месте либо ложно» — этого мало. Доказательство отсутствия читателей не есть доказательство
отсутствия нужды: первое — факт о нынешнем коде, второе — суждение о том, ради чего вещь заводилась.
`V58` соблюла букву моего правила и всё равно ошиблась.

# XXIIд. Наблюдатель: механизм, построенный четырьмя миграциями и выключенный одной

В хранилище 52 таблицы (замер по всем `CREATE TABLE`). Часть из них заводит не столбцы, а **новые роды
фактов**. Самая поучительная связка — та, что строит стороннего наблюдателя проекта: `V59`, `V60`, `V65`,
`V68` возводят его, а `V111` выключает навсегда.

**`V59__create_gemini_observer_journal.sql`** — заводит наблюдателю **собственную память**: по одной записи
на цикл наблюдения, для его же будущей надобности.
*Связи:* создаёт `gemini_observer_journal` с внешним ключом на проект и каскадом удаления, индекс по
проекту и убыванию времени | таблицу читают `InternalGeminiObserverController`, `ProjectController`,
`GeminiObserverActionService`.
*Ценность:* без неё у наблюдателя нет непрерывности: каждый цикл начинается с чистого места и не знает, о
чём говорил прошлый.
*Комментарий:* **ядро по замыслу, и замысел точен.** Заведена она в тот же день, что и изъятие `V58`
(раздел XXIIг), и это одна перестройка: наблюдатель перестаёт читать внутренний лог бэкенда и получает
**свою** запись вместо чужой. То есть память агента отделена от журнала носителя — ровно то различение,
отсутствие которого через две недели дало 38 часов заражения (раздел XXIе). Здесь оно проведено верно и
заранее.
*Философия:* `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: сильная по устройству, не мерена по свидетельству.** Непрерывность
наблюдателя задана явно и хранится в базе, а не в процессе. Но восстановить его состояние на прошлый момент
я не пробовал, а образец требует именно этого.

**`V68__gemini_observer_journal_continuity_and_action_verification.sql`** — добавляет три признака:
вызывали ли модель на самом деле, отпечатки замеченных отклонений и **проверено ли предпринятое действие**.
*Связи:* `gemini_observer_journal.gemini_called`, `gemini_observer_journal.anomaly_fingerprints`,
`gemini_observer_actions.verified`.
*Ценность:* без первого «наблюдатель отработал и ничего не нашёл» неотличимо от «наблюдатель не
запускался»; без третьего «действие предложено» неотличимо от «действие подействовало».
*Комментарий:* **ядро, и это тот же приём, что я нахожу по всей фабрике, здесь применённый дважды в одном
файле.** `gemini_called` — различение, тождественное `decompositionUnreached` у требования (раздел XXI) и
`instrument_failure` у наблюдений запуска (раздел XXIIв): свидетельство о предмете против свидетельства о
себе. `verified` — различение, тождественное `Decision.applied` у заслона вердиктов (раздел XXIв):
«заслон сработал и согласился» и «заслон не запускался» суть разные факты.

То есть механизм был устроен так, чтобы делать **собственные пробелы видимыми**. Это и делает его
последующее выключение особенно достойным записи.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“». **Форма: сильная по
хранению, не мерена по вызывающим.** Показано, как хранится третий исход — отдельным столбцом, а не
пропуском в данных. Но обойти всех читающих и убедиться, что никто не считает «не вызывали» за «ничего не
нашли», я не мог: механизм выключен, и живых читающих у признака нет.
Второй образец: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007), тот же философ и якорь. Сильная
дословно: «успешное завершение — **значение**, которое не может существовать без выполненных предусловий, и
оно несёт свидетельство для следующего шага». Слабая: «статус `done` в поле и запись в лог». Опровержение:
«сконструировать результат успеха, не имея свидетельства; если это удаётся — форма слабая». **Форма:
слабая.** Признак `verified` заведён как булев столбец со значением «ложь» по умолчанию, то есть действие
может существовать непроверенным; свидетельство приложено рядом, а не встроено в само существование
результата.

**`V111__permanently_disable_gemini_project_observer.sql`** — выключает наблюдателя навсегда, записывая
настройке значение «ложь».
*Связи:* удаляет и заново вставляет строку `gemini_project_observer_enabled` в `system_settings` | на неё
ссылается заглушка `GeminiProjectObserverService`.
*Ценность:* закрепляет решение в базе, а не в конфигурации выкладки, — то есть переживает пересоздание
контейнера.
*Комментарий:* **ядро по действию, и здесь у меня была ошибка, которую я исправил замером в тот же такт.**

Сперва я записал, что это единственная прочитанная мною миграция **без основания вовсе**: три строки, ни
слова почему, — и на фоне `V121`, называющей указание, замер и замену, это выглядело провалом. Замер
показал иное: основание есть, но лежит **в другом месте** — в javadoc заглушённого класса: «permanently
decommissioned as Muda (2026-08-26, operator directive: „удали его безвозвратно как муда“)», и там же
названо, чем работа делается вместо: `ContinuousOrchestrationService`, `PlannedWorkRecoveryService`,
`OpsAuditorService`.

Так что верное суждение не «основания нет», а «основание и исполнение разнесены»: читающий слой миграций
не получает причины, читающий слой кода не получает исполнения. Ссылка при этом односторонняя — заглушка
называет `V111`, а `V111` о заглушке молчит.

Заглушка, к слову, оставлена намеренно и с объяснением: «to preserve Spring Context dependency injection
contracts without dragging in 23 heavy repositories».

Что осталось живым после выключения — замерено: писателей у журнала наблюдателя в коде **нет**
(контроль: у сравнимого журнала дефектов их 22), а читающих трое. И отдельно: контроллер этого выключенного
механизма, `InternalGeminiObserverController` по пути `/internal/gemini-observer`, несёт не только чтение,
но и **четыре изменяющих запроса** — снятие застрявшего работника, освобождение требования в состоянии
завершения, обнуление дневных счётчиков сессий, — и сохраняет сессию. Это относится к уже записанному
пункту 27 перечня («слово „internal“ принято за полномочие»), и я лишь отмечаю замер, ничего нового не
предписывая.
*Философия:* `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` (D007) — Айзек Леви, `BARCAN-TAG-04 MODAL-QUANTIFIER`,
принцип фиксации доксастических состояний, anchor *The Fixation of Belief and Its Undoing / Enterprise of
Knowledge — doxastic commitment*. Сильная дословно: «записано, какое свидетельство изменило убеждение и
какая неопределённость осталась; приложены уверенность до и после и неразрешённые гипотезы». Слабая: «новое
убеждение изложено без старого». Опровержение: «спросить, во что агент верил час назад и что именно это
изменило». **Форма: слабая на стороне миграции, сильная при чтении обоих мест вместе.** Сама `V111`
излагает новое убеждение без старого и без свидетельства — дословно слабая форма. Свидетельство и замена
записаны в заглушке. Опровержение образца («спросить, во что верили час назад») из слоя миграций ответа не
получает, потому что связи от `V111` к заглушке нет.

## Суждение по этой части слоя

Наблюдатель был построен добротно и с редким качеством: он делал свои собственные пробелы видимыми —
отдельно «не вызывали» и отдельно «не проверено». Выключен он по прямому указанию оператора, с названной
заменой, и решение закреплено в базе, а не в настройках выкладки. Возражать тут не против чего.

Записи достойно другое: **причина решения хранится не там, где решение исполняется**. Миграция —
единственное место на фабрике, где причина изменения обыкновенно лежит рядом с изменением (раздел XXII), и
именно здесь это правило нарушено. Тот, кто читает историю схемы, видит выключение без объяснения; тот, кто
читает код, видит объяснение без исполнения.

# XXIIе. Связность свидетельств: якорь, у которого нет читателя

Три миграции заводят не факт, а **способ считать факты согласованными**. Это самое философски нагруженное
место хранилища: код здесь называет философов по имени, и оба они есть в корпусе `docs/philosopher-patterns`
с точными якорями — третий случай, когда фабрика пользуется собственным корпусом (после четырёхзначной
логики в `LeverAgreement` и D-кодов в `V137`).

**`V79__kaizen_proposals_and_evidence_nodes.sql`** — заводит общую проекцию свидетельств, куда три
независимых источника пишут по одной строке рядом со своей собственной записью.
*Связи:* создаёт `kaizen_proposals` и `evidence_nodes` с ограничением «ровно один источник» (переписанным
позже в `V82`, раздел XXII) | питают её надзор за процессом, фальсификация кода и кайдзен | читает
`EvidenceCoherenceService` (613 строк, `services/coherence`).
*Ценность:* без общей схемы каждый источник остаётся отдельной несведённой уликой, и сопоставить их нечем.
*Комментарий:* **ядро, и здесь два решения, которые стоит назвать порознь.**

Первое: `id` предложения кайдзен намеренно оставлен строкой с приставкой, «matching the existing
KaizenProposal.id shape exactly (not a raw UUID) — no external reference format changes». То есть форма
внешней ссылки признана обязательством перед теми, кто на неё ссылается, и сохранена при переносе из памяти
в базу.

Второе, и оно важнее: `project_id` допускает пустоту, потому что часть свидетельств действительно ни к
какому проекту не относится — общефабричное системное наблюдение. И основание отказа от заглушки названо
дословно: «A fake sentinel UUID here would be exactly the kind of fabricated data this system's own
falsification track exists to catch». Механизм отказывается сочинять значение, ловлей которого сам же и
занят.
*Философия:* `AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` (D002) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «у объекта есть владелец,
личность, жизненный цикл и семантика удаления, и он привязан к агрегату или каноническому реестру».
Слабая: «класс с полями и репозиторием, у которого нет ответа на вопрос „кто вправе его удалить“».
Опровержение: «назвать две точки кода, удаляющие объект по разным правилам; если они есть — реестра нет».
**Форма: сильная.** Узел свидетельства привязан к каноническому реестру по построению — ограничение «ровно
один источник» и есть объявление владельца, а каскадное удаление задаёт семантику: узел живёт ровно столько,
сколько его источник. Опровержение выполнить не удалось: вторая точка удаления по другому правилу
невозможна, удаление наследуется от источника.

**`V80__coherence_runs.sql`** — заводит запись прогона связности: сколько узлов рассмотрено, сколько принято
и **общий счёт связности**, плюс исход по каждому узлу с его конечной активацией.
*Связи:* создаёт `coherence_runs` и `coherence_run_node_results` с каскадом от прогона и от узла | пишет
`EvidenceCoherenceService.periodicCoherenceCycle`, запускаемый **раз в два часа** по всем деятельным
проектам (`@Scheduled(fixedRate = 7200000, initialDelay = 120000)`).
*Ценность:* без внешнего счёта решение «узнали ли мы что-то новое» принимается со слов самой модели.
*Комментарий:* **ядро по замыслу, и здесь измерена самая горькая вещь этого слоя.**

Назначение объявлено в первой же строке файла и объявлено безупречно: `coherence_score` есть «the external,
non-self-reported anchor future termination logic … will check against, instead of trusting the LLM's own
claim that it „learned something new“ this round». То есть якорь заводился именно затем, чтобы прекращение
работы не зависело от самоотчёта.

Замер: **счёт вычисляется каждые два часа и не читается никем для решения.** Единственное упоминание
`coherence_score` вне пакета `coherence` — комментарий в `MLPredictionServiceClient:303`, а не чтение
(контроль: внутри самой службы поле встречается 6 раз, то есть инструмент не слеп). Названный будущий
потребитель — agentic-цикл наблюдателя, Phase 5, — это тот самый наблюдатель, которого `V111` выключил
навсегда (раздел XXIIд).

Так замыкается связка двух тактов: механизм построил объективный якорь против самоотчёта, а тот, кто должен
был на него опереться, был упразднён. Якорь остался, опираться на него некому.
*Живое, 7 сентября 2026:* механизм работает и накопил результат, которым никто не пользуется. Замер
`db-table-sizes`: `COHERENCE_RUNS` — 52 прогона, `COHERENCE_RUN_NODE_RESULTS` — **9884 строки**,
`EVIDENCE_NODES` — 1003. То есть почти десять тысяч посчитанных исходов по узлам лежат в таблице, и ни один
не читается для решения. Это не «механизм не запустили» — он идёт исправно каждые два часа; это **сигнал,
у которого нет читателя**, подтверждённый числом.

*Философия:* `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — Фред Дрецке,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип информационной пропускной способности каналов, anchor
*Knowledge and the Flow of Information — informational epistemology*. Сильная дословно: «сигнал признан
годным лишь если меняет следующее действие и предотвращает ошибочное». Слабая: «сигнал есть и он верен».
Опровержение: «назвать действие, которое сигнал изменил; **сигнал без читателя не есть наблюдение**».
**Форма: слабая, и опровержение выполнено.** Назвать действие, которое изменил бы счёт связности, я не
могу: читающего для решения нет. Сигнал верен и вычисляется исправно — и по Дрецке наблюдением не является.
Второй образец: `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается на
собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная по замыслу и нереализованная.** Механизм построен именно
как средство против слабой формы — против рассказа агента о себе — и предъявить источник по требованию
может. Но раз никто на него не опирается, ни одно утверждение фабрики на него и не опирается тоже.

**`V81__coherence_confidence.sql`** — добавляет байесовскую оценку подкреплённости узла в духе Бовенса и
Хартманна, **допускающую пустоту**.
*Связи:* добавляет `coherence_run_node_results.confidence` | вычисляется после пересмотра убеждений (AGM)
в `EvidenceCoherenceService`.
*Ценность:* без неё принятый узел и подкреплённый другими узел неразличимы.
*Комментарий:* **ядро малого радиуса, и образцовое обращение с неизвестным.** Пустота здесь не пропуск
данных, а названное состояние с основанием: значение вычисляется «only for nodes that are part of an
accepted, agreeing cluster», а одинокому отвергнутому узлу или узлу без скопления **не с чем себя
сверять**. То есть отсутствие оценки означает не «мы не посчитали», а «считать не по чему», и это записано.

Сравню с уже описанным: `parseLeanValue` (раздел XXIб) при неизвестном возвращал утвердительное значение;
здесь при неизвестном не возвращается ничего, и сказано почему. Одна и та же развилка, противоположные
решения, и разница ровно в том, названо ли основание.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“». **Форма: сильная по
хранению, слабая по вызывающим — и слаба она по той же причине, что и весь этот куст.** Показано, как
неизвестное хранится и почему оно неизвестно; но «трактуемый по месту» здесь не опровергнуть и не
подтвердить, потому что мест трактовки нет: решающих читателей у этих строк не существует.

## Суждение по этой части слоя

Этот куст — самая тщательная работа, какую я нашёл в хранилище. Служба связности честно перечисляет
собственные упрощения относительно источника («a deliberate, documented simplification», «Simplified
relative to full ECHO in one more way, honestly»), три источника сведены в одну схему вместо трёх
несведённых улик, заглушка вместо пустого значения отвергнута с названным основанием, а неизвестное
отличено от непосчитанного.

И весь этот куст не влияет ни на одно решение фабрики. Счёт связности идёт раз в два часа, ложится в
таблицу и остаётся там. Потребителя, ради которого якорь строился, выключили отдельной миграцией, и связи
между этими двумя решениями в схеме нет — так же, как нет её между `V111` и заглушкой (раздел XXIIд).

Это, по-моему, точный портрет главной болезни фабрики, какой она видна из слоя миграций: **строится
хорошо, а связывается плохо**. Отдельные механизмы здесь лучше своих образцов; ломается то, что между ними.

# XXIIж. Надзор за процессом: чем отличают обычный разброс от настоящего сдвига

Три миграции заводят способ **не называть причину там, где её нет**. Это прямая противоположность тому, что
я нашёл в графе ограничения (раздел XXIг): там единственный датчик всегда объявлялся узким местом, здесь
отклонение обязано выйти за границы, вычисленные из собственной истории, прежде чем его признают событием.

**`V72__create_process_control_snapshots.sql`** — заводит долговечный временной ряд контрольной карты: по
строке на «проект, эпик, поток», с центральной линией, верхней и нижней границами, признаком выхода
из-под контроля и меткой правила Western Electric.
*Связи:* пишет `ProcessControlService` | таблицу знают `ProcessControlSnapshotEntity` и
`ProcessControlSnapshotRepository` | питается журналом дефектов (раздел XXIж).
*Ценность:* без истории нельзя ни закрепить исходный уровень, ни обнаружить сдвиг относительно него.
*Комментарий:* **ядро, и причина заведения названа точно.** До неё показатель дефектности вычислялся по
запросу и не хранился: «no Phase 1 baseline could be locked and no Phase 2 drift could be detected against
it». То есть механизм существовал, а сравнивать было не с чем — тот же изъян, что у графа ограничения, где
среднее по Уэлфорду обнуляется при перезапуске (раздел XXIг). Здесь он закрыт хранением.

Существенно, что границы вычисляются, а не назначаются, и что отдельным столбцом хранится **какое именно
правило** сработало. Это и есть отказ от одной общей тревоги в пользу названного признака: механизм
сообщает не «плохо», а «сработало такое-то правило на такой-то последовательности».
*Живое, 7 сентября 2026:* ряд действительно накапливается — `PROCESS_CONTROL_SNAPSHOTS` 184 строки,
`DEFECT_JOURNAL` 445 (замер: `db-table-sizes`). То есть у карты есть история, из которой границы можно
вычислить, а не назначить, — ровно то, чего не хватало графу ограничения.

*Философия:* `DONALD_DEVIDSON_15_INUS_FACTOR_CHECK` (D007) — Дональд Дэвидсон,
`BARCAN-TAG-09 MORAL-DILEMMA`, принцип радикальной интерпретации, anchor *Truth and Meaning / radical
interpretation — interpretation and coherence*. Сильная дословно: «подозреваемая причина считается **одним
фактором достаточного набора**, пока альтернативы не исключены; со-факторы перечислены со свидетельством
присутствия или отсутствия каждого». Слабая: «названо первое объяснение, совпавшее с наблюдением».
Опровержение: «назвать вторую гипотезу, дающую то же наблюдение; её отсутствие означает, что сравнения не
было». **Форма: сильная по устройству.** Вторая гипотеза здесь встроена в сам прибор и называется «обычный
разброс»: пока точка внутри границ, объяснению причины отказано. Оговорю границу похвалы: со-факторы
поимённо не перечисляются, столбец правила говорит, **какой** признак сработал, но не какие условия при
этом присутствовали.
Второй образец: `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010) — Дерек Парфит,
`BARCAN-TAG-05 NECESSARY-IDENTITY`, принцип психологической непрерывности идентичности, anchor *Reasons and
Persons — psychological continuity and identity*. Сильная дословно: «личность долгоживущей сущности
сохраняется через снимки и миграции, есть свидетельство воспроизведения». Слабая: «идентификатор стабилен,
пока никто не пересоздаёт». Опровержение: «восстановить состояние на прошлый момент; если сущность не
опознаётся — снимка нет». **Форма: сильная по устройству, не мерена по свидетельству.** Порядковый номер в
последовательности и хранимые границы позволяют восстановить, каким процесс виделся в прошлый момент; сам я
такого восстановления не проводил.

**`V85__process_control_snapshot_metric_label.sql`** — привязывает к снимку словесное определение того, что
его числа означают, и **объявляет это определение чисто описательным**.
*Связи:* добавляет `process_control_snapshots.six_sigma_metric_label` | в вычислениях карты не участвует
(центральная линия, границы и распознавание правил не затронуты).
*Ценность:* без него числа снимка не имеют операционного определения, и читающий домысливает, что именно
считали.
*Комментарий:* **периферия по действию, ядро по уроку, и урок этот про метку, принятую за данные.**
Основание — живая проверка, приведённая в самом файле: показатель «вычислялся на каждый эпик при компиляции
и показывался в подсказках и на панелях, но **никогда не привязывался к тому ряду контрольной карты,
который должен был описывать**», и заслон, который должен был это ловить, оказался «presence-only gate,
content never read semantically» — проверял наличие, а не смысл.

То есть метка присутствовала, читалась людьми как объяснение чисел, и ни с какими числами связана не была.
Это ровно тот подлог, на котором я сам попался, приняв упоминание имени в тексте за разбор механизма
(раздел XVIII), и тот же, что в разделе XXIе выдал журнал фабрики за деятельность продукта.

Починка сделана скупо и верно: метку привязали к ряду и **сразу оговорили, что в арифметику она не входит**.
Описание объявлено описанием, а не признаком.
*Философия:* `POL_GRAYS_01_CONVERSATION_MAXIM` (D007) — Пол Грайс, `BARCAN-TAG-00 CODE-GUARDIAN`, принцип
кооперативного дискурса, anchor *Logic and Conversation — cooperative principle and conversational maxims*.
Сильная дословно: «вывод достаточно информативен, истинен, уместен и однозначен для следующего работника;
названы минимальные поля статуса, ссылки на свидетельство и следующее действие». Слабая: «отчёт длинный и
подробный». Опровержение: «дать отчёт следующему работнику и спросить, что делать; невозможность ответить и
есть дефект». **Форма: сильная после починки, слабая до неё.** Сама миграция и называет себя гриcевой —
«Gricean quantity-optimal grounding follow-on», — и это четвёртый случай, когда код фабрики ссылается на
корпус. До починки метка была уместна и неоднозначна одновременно: следующий работник не мог сказать, к
какому ряду она относится, потому что она не относилась ни к какому.

**`V90__trust_signal_snapshots.sql`** — копит наблюдения, на которых когда-нибудь можно будет подобрать веса
оценки доверия вместо нынешних, выбранных рукой.
*Связи:* пишет `TrustSnapshotService.captureAndBackfillSnapshots`, `@Scheduled(fixedRate = 7200000)` —
раз в два часа, он же дозаписывает исход через `findByEventualOutcomeIsNull` и `setEventualOutcome` |
столбцы `eventual_outcome` и `outcome_recorded_at` допускают пустоту по устройству.
*Ценность:* без размеченной истории подбор весов невозможен, и остаются штрафы, назначенные рукой.
*Комментарий:* **ядро, и это лучший отказ во всём хранилище.** Миграция намеренно **не** заводит ни
вычисления весов-кандидатов, ни таблицы подобранных коэффициентов, и основание названо дословно: размеченной
истории пока недостаточно, «and inventing placeholder weights here would repeat the exact mistake this whole
lever exists to fix».

Сопоставлю с уже описанным в перечне. `PredictionService` в сайдкарах (раздел XIX) содержит логистическую
оценку с коэффициентами, выбранными рукой, и обучения у неё нет. Здесь — та же задача, тот же соблазн и
прямо противоположное решение: **не сочинять число, которого нечем обосновать, и вместо этого завести
предпосылку для настоящего обоснования.** Одна фабрика, одно правило, два разных исполнения.

Замер о нынешнем положении: сбор идёт, исходы записываются, а вот подбора весов **не существует** — ни
таблицы коэффициентов, ни кода подбора; единственные упоминания рычага во всём коде суть сама служба
снимков и эта миграция (контроль: греп по имени рычага находит один файл). Достаточно ли накопилось
размеченной истории, я **не мерил**: это состояние живой базы, а не репозитория. Поэтому «этап второй не
наступил» я утверждаю как факт о коде, а не как упрёк.
*Философия:* `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006) — Элвин Голдман,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип релайабилизма процессов, anchor *A Causal Theory of Knowing
/ Epistemology and Cognition — reliabilism*. Сильная дословно: «рискованное действие требует свидетельства
знаниевого качества, а не убеждения или намерения; приложена проверка или источник полномочия». Слабая:
«действие разрешено, потому что „мы уверены“». Опровержение: «потребовать источник; ссылка на собственное
убеждение и есть дефект». **Форма: сильная, и в редком виде — через воздержание.** Рискованное действие
здесь — назначить веса, — и оно не совершено именно потому, что свидетельства знаниевого качества нет.
Опровержение образца выполнить нельзя: источник не подменён убеждением, он объявлен отсутствующим.

## Суждение по этой части слоя

Эти три миграции показывают фабрику с лучшей стороны и притом ровно в том месте, где я привык находить у
неё худшее. `V72` даёт истории, без которой сравнивать не с чем. `V85` признаёт, что метка, показываемая
людям, ни к чему не привязана, и привязывает её, сразу оговорив её бессилие. `V90` отказывается сочинить
коэффициенты и заводит вместо них предпосылку.

Общее у всех трёх — **отказ выдавать наличие за смысл**: точка внутри границ не событие, метка не признак,
собранные строки не модель. Это и есть та самая дисциплина, отсутствие которой в других местах дало и
единственный датчик, объявленный узким местом, и заголовок фабрики, принятый за тождество требования
заказчика.

# XXIII. Живой замер фабрики, 7 сентября 2026

По указанию оператора каждая запись обязана нести **живое описание**, а не только вывод из исходников.
Отныне форма записи дополняется строкой *Живое:* — что этот механизм делает на работающей фабрике, с
командой и временем замера. Записи, у которых такой строки ещё нет, не считаются законченными.

Здесь — общий снимок, к которому отдельные записи отсылаются.

**Контейнеры и здоровье.** `docker ps`: все четыре подняты — бэкенд 29 часов, пускатель 47 часов,
ML и судейский посредник по двое суток. `curl -s localhost:8080/actuator/health` →
`{"springBootStatus":"UP","status":"ok"}`. Часы хоста и контейнера совпадают (оба UTC), поэтому отметки
времени в журнале сравнимы напрямую.

**Поток.** Состояние проекта `test-fiftieth` — `IMPLEMENTING`, требуемый следующий переход:
исполнитель обязан дать запрос на слияние либо свидетельство окончательного отказа. Слияния за сутки идут:
последние в 00:37, 01:11, 02:40 и **04:40**, после чего к 11:35 — тишина, то есть около семи часов без
слияния при прежнем ритме раз в час-полтора.

**Раздача.** Доминирующая строка журнала за все 29 часов — 2209 повторений «queued-task dispatch not
authorized». Прочитанная целиком, она **называет своё основание**: «its own precondition is unmet — there
is nothing for it to act on right now». Это не блокировка: очередь пуста. Первая такая строка — в 06:16
6 сентября, то есть с самого запуска контейнера.

Отмечу это отдельно, потому что легко ошибиться: отказ здесь **не молчаливый**. Он называет условие, и это
именно то, чего не было у отказа по `enabled` при простое 5 сентября (раздел XXIб, `AccountStatus`).

**Объёмы, замер `curl -s localhost:8080/internal/gemini-observer/db-table-sizes`:**

    PROJECT_EVENT_LOG            26735      TASKS                  665
    COHERENCE_RUN_NODE_RESULTS    9884      PR_REVIEWS             812
    LEVER_OBSERVATIONS            5338      DEFECT_JOURNAL         445
    CLAIMS                        1805      WISHLIST               329
    JULES_SESSIONS                1767      PROCESS_CONTROL_SNAPSHOTS 184
    CONTEXT_CHUNKS                1518      COHERENCE_RUNS          52
    EVIDENCE_NODES                1003

Оговорка к этому замеру, обязательная: вход отдаёт **двадцать таблиц по величине**, а не все 52. Поэтому
про `TRUST_SIGNAL_SNAPSHOTS`, `LEVER_PROMOTION_STATE`, `KAIZEN_PROPOSALS` и прочие сказать «строк нет»
нельзя — их просто нет в двадцатке. Отсутствие в проекции есть факт о проекции.

**Два входа отвечают отказом.** `/internal/gemini-observer/dispatch-capacity-probe` и
`/internal/gemini-observer/persistent-workers` → `HTTP 500`, «An unexpected error occurred». При этом
`/db-table-sizes` на том же контроллере отвечает исправно, то есть дело не в контроллере целиком. Это
поверхность разбора у механизма, выключенного `V111` (раздел XXIIд): наблюдателя нет, вход остался, и
часть его сломана незамеченной.

**Что этот снимок меняет в прежних записях.** Три открытых вопроса закрыты замером, и все три записаны в
соответствующих местах: рычаг доверия действительно доходил до верхней ступени и терял её с первой ошибки;
счёт связности накопил 9884 исхода по узлам и по-прежнему не читается ни для одного решения; контрольная
карта имеет настоящую историю в 184 снимка, то есть её границы вычисляются, а не назначаются.

# XXIIз. Притязания на файлы: кто вправе трогать что

Четыре миграции решают, кто из исполнителей вправе тронуть какой файл, и что делать, когда двое взялись за
одно. Живое основание для всего раздела: `CLAIMS` — 1805 строк (замер
`curl -s localhost:8080/internal/gemini-observer/db-table-sizes`, 7 сентября).

**`V21__add_file_scope_and_conflicts.sql`** — вводит область файлов у задачи и заводит учёт столкновений:
род конфликта, число попыток разрешения, исход и перечень конфликтующих файлов.
*Связи:* добавляет `tasks.file_scope` | заводит `task_conflicts` с внешним ключом на задачу | заводит
`needs_human_review`, удалённую позже `V121` (раздел XXIIг).
*Ценность:* без области файлов невозможно узнать заранее, что две задачи возьмутся за одно и то же.
*Комментарий:* **ядро, и это первая попытка ответить на вопрос владения.** Отмечу, что число попыток
разрешения хранится **отдельным столбцом**, а не выводится из журнала: значит бюджет попыток измерим и
ограничим. Именно это и позволило `V99` заметить, чем исчерпание бюджета оборачивалось.
*Живое, 7 сентября 2026:* столкновения происходят и разрешаются на входе. В журнале за 29 часов 29 строк
стража столкновений, из них последние: `stripped [.github/workflows/ci.yml, Dockerfile.backend,
docker-compose.yml]` и `stripped [src/main/resources/db/migration/V_NEXT__internal.sql] from predicted
fileScope`. То есть механизм не декоративен: он вычёркивает общие файлы из предсказанной области **до**
раздачи, а не разбирает конфликт после.
*Философия:* `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «до разделения модулей
объявлено, какой агрегат вправе менять каждую часть». Слабая: «классы разделены по размеру или по слоям».
Опровержение: «найти поле, которое пишут два сервиса». **Форма: слабая.** Область файлов объявляется
**предсказанием компилятора**, а не правилом владения: она говорит, чего задача, вероятно, коснётся, а не
что ей позволено. Опровержение выполняется по существу: два исполнителя могут взяться за один файл, и
именно поэтому нужна таблица столкновений.

**`V69__create_project_file_claims.sql`** — заводит живой реестр владения файлами для стража столкновений
между эпиками.
*Связи:* создаёт `project_file_claims` с индексом по проекту и пути | `task_id` и `feature_id` допускают
пустоту, и пустой эпик означает **общепроектное притязание**, сталкивающееся с каждым эпиком | реестр
читает `TechnicalLeadCompiler` перед выдачей области файлов.
*Ценность:* без него столкновение обнаруживается после того, как оба исполнителя уже начали работать.
*Комментарий:* **ядро, и здесь пустое значение снова несёт смысл, а не пробел.** Пустой эпик — это не
«неизвестно чей», а «ничей в отдельности, значит всехний»: притязание, поставленное каркасом при заводке
проекта, обязано сталкиваться со всеми. Это осознанное расширение области, а не потеря сведений.
*Живое, 7 сентября 2026:* реестр участвует в решениях — страж столкновений между эпиками срабатывает и
вычёркивает пути (строки выше). Числа строк самого реестра у меня **нет**: `PROJECT_FILE_CLAIMS` не входит
в двадцатку крупнейших таблиц, которую отдаёт вход `db-table-sizes`, а отсутствие в проекции есть факт о
проекции, не о базе.
*Философия:* `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004), формы и опровержение дословно выше.
**Форма: сильная по объявлению, не мерена по исполнению.** Здесь, в отличие от `V21`, объявляется именно
владение: строка реестра говорит, что этот путь занят этим эпиком. Но опровержение — найти файл, который
пишут два исполнителя вопреки реестру, — требует обхода живых веток, которого я не делал.

**`V99__task_conflict_preserved_branch.sql`** — записывает ветку, которая была **сохранена**, когда бюджет
автоматических попыток разрешения конфликта исчерпан.
*Связи:* добавляет `task_conflicts.preserved_branch` | отменяет прежнее поведение, при котором исчерпание
бюджета вызывало `BranchGarbageCollectorService` с головной ссылкой.
*Ценность:* без него исчерпание попыток **уничтожало работу**, а не откладывало её.
*Комментарий:* **ядро, и самая важная запись этого раздела.** Первая строка файла формулирует правило:
«an automated path must not perform an irreversible action to resolve uncertainty». Подтверждение живое и
названное: на `test-forty-sixth` «PR#12 vanished and the plan shrank from 21 tasks to 19, with no counter
anywhere distinguishing „conflict resolved“ from „conflict removed along with the work“».

Основание сформулировано в самой миграции точнее, чем сформулировал бы я: **исчерпание бюджета не есть
свидетельство, что ветка невосстановима; это свидетельство, что стольких автоматических попыток не
хватило.** Это третье появление одного различения в перечне — рядом с «спросили и не ответили против не
спрашивали» у требования и «отказ продукта против отказа прибора» у наблюдений запуска.
*Живое, 7 сентября 2026:* `docker logs … | grep BranchGarbageCollector` даёт 1873 строки за 29 часов,
среди них есть `preserved for recovery` и обычные `deleted branch 'jules-…'`. Путь сохранения существует и
срабатывает; удаление осталось там, где оно законно. Что **все** удаления законны, я не проверял — это
отдельный обход по каждой ветке.
*Философия:* `AYZEK_LEVI_02_DECISION_EXPECTED_LOSS` (D005) — Айзек Леви, `BARCAN-TAG-04 MODAL-QUANTIFIER`,
принцип фиксации доксастических состояний, anchor *The Fixation of Belief and Its Undoing / Enterprise of
Knowledge — doxastic commitment*. Сильная дословно: «действие ремонта или слияния выбрано минимизацией
ожидаемой цены дефекта; названы вероятность отказа, радиус поражения и цена отката». Слабая: «выбрано
удобное на месте». Опровержение: «назвать отвергнутую альтернативу и её цену; отсутствие альтернативы
означает, что выбора не было». **Форма: не применима, и это тот случай, ради которого оператор велел
заводить новые образцы.** Образец обращает необратимость в **слагаемое** — «цену отката»; здесь же откат
невозможен, у удалённой ветки нет цены, у неё есть отсутствие. Образец, считающий необратимость дорогой, а
не запретной, разрешит её при достаточной ожидаемой выгоде — и именно так работа и была уничтожена.
**Реализация сильнее образца, и по этому случаю заведён новый:**
`AYZEK_LEVI_21_IRREVERSIBILITY_REQUIRES_CERTAINTY` в
`docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md`. По нему **форма: сильная** — необратимое
действие как способ разрешения неопределённости запрещено, отложенное состояние записано так, что его можно
найти, и два исхода различены столбцом.

**`V123__drop_task_conflicts_without_a_task.sql`** — удаляет записи о конфликтах, у которых нет задачи.
*Связи:* удаляет строки `task_conflicts`, не соединяющиеся ни с одной строкой `tasks` | тот же приём, что
в `V117` и `V122`.
*Ценность:* без изъятия эти строки вечно считались дефектами и в числителе, и в знаменателе собственной
меры качества фабрики.
*Комментарий:* **ядро по последствию, и образец добросовестного замера.** Способ замера назван и он
строгий: считали **при остановленном бэкенде, на копии, чья контрольная сумма совпала при двух чтениях**.
Результат: 92 строки, **ни одна** не соединяется с задачей, все написаны между 10 и 12 июля, с тех пор
producer молчит. Внешний ключ существует и **проверен в живую в той же сессии** — вставка с неизвестной
задачей была отвергнута схемой.

И дальше — то, чему я учусь у этой миграции: «How they were orphaned is not established, and is not
guessed at here.» Причина осиротения **не установлена и не додумана**. Это ровно та дисциплина, нарушение
которой стоило мне семи ошибок за смену.

Обоснование изъятия — не «мусор», а инвариант: элемент, структурно неспособный получить вердикт, обязан
**покинуть множество, по которому принимается решение**. И место починки названо: один раз, в данных, а не
фильтром, который каждый читающий обязан повторять, — «that mistake was made before and the defect returned
through the reader that had not been patched».
*Живое, 7 сентября 2026:* `TASK_CONFLICTS` не входит в двадцатку крупнейших таблиц, а в журнале за 29 часов
33 строки со словом `conflict`. Утверждать по этому, что сирот больше нет, я не могу: нужен тот же счёт по
базе, что делала миграция, а его я не делал.
*Философия:* `AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` (D002) — тот же философ и якорь, что выше. Сильная
дословно: «у объекта есть владелец, личность, жизненный цикл и семантика удаления, и он привязан к агрегату
или каноническому реестру». Слабая: «класс с полями и репозиторием, у которого нет ответа на вопрос „кто
вправе его удалить“». Опровержение: «назвать две точки кода, удаляющие объект по разным правилам; если они
есть — реестра нет». **Форма: сильная после починки.** Владелец конфликта — задача, и внешний ключ это
исполняет; строки без владельца изъяты один раз в данных, а не отфильтровываются каждым читающим порознь.
Оговорю: две точки удаления по разным правилам я не искал, так что опровержение не выполнялось.

## Суждение по этой части слоя

Владение файлами на фабрике объявлено дважды и по-разному: `V21` объявляет **предсказание** компилятора о
том, чего задача коснётся, а `V69` — **притязание** на путь. Первое слабее второго, и таблица столкновений
существует ровно потому, что предсказание владением не является.

А `V99` — лучшее место всего слоя миграций. Не потому, что чинит редкую поломку, а потому, что чинит её
правильным способом: не добавляет ещё одну проверку перед удалением, а **запрещает удаление как способ
разрешения неопределённости** и заводит место, куда откладывать. Отсюда и новый образец.

# XXIIи. Контекст: что исполнитель видит перед работой

Три миграции решают, **какое знание попадёт в подсказку**. Это то самое место, через которое 9 августа
пришло 38-часовое заражение (раздел XXIе), поэтому живое описание здесь особенно нужно.

**`V55__create_context_chunks.sql`** — заводит выборку знаний: куски постоянных документов с их вложениями,
чтобы в каждый вызов уходили только несколько самых близких по смыслу, а не весь свод.
*Связи:* создаёт `context_chunks` (тип источника, ссылка на источник, номер куска, содержимое, вложение,
размерность) с индексом по источнику | читает `GeminiContextService`, ранжируя по точной косинусной близости
| переиндексация идемпотентна: удаление и вставка по ссылке на источник, поэтому правка документа не
оставляет устаревших кусков.
*Ценность:* без ранжирования в каждый вызов уходит весь свод, и расход на внешнюю модель ничем не ограничен.
*Комментарий:* **ядро, и здесь важно, что именно объявлено рычагом.** Указание оператора приведено в файле
дословно: «нужно чтобы Джемини постоянно училась контексту моей системы и в каждом вызове была максимально
компетентна… мат. выверенные недорогие по токенам решения». И рычагом названо **ранжирование**, а не урезание:
компетентность не понижается, понижается объём переданного. Это разные вещи, и их часто путают.

Отмечу связь, важнее прочих: среди индексируемых источников — **корпус философских образцов**
(назван в `V62` прямо: «and now the new philosopher-patterns corpus»). То есть корпус не лежит мёртвым
грузом в репозитории: он подаётся в подсказки через ту же выборку.
*Живое, 7 сентября 2026:* `CONTEXT_CHUNKS` — 1518 строк (замер `db-table-sizes`), то есть выборка
наполнена. При этом за 29 часов журнала **ни одной строки** от `GeminiContextService`, `reindex`,
`embedding` (контроль: слово `Gemini` в журнале встречается 59 раз, `TOC-SENTINEL` — 7332, значит греп не
слеп). Переиндексация назначена на `cron 0 0 3 * * ?`, и 03:00 в окно работы контейнера попадало.

Две гипотезы, между которыми я **не выбираю**: либо переиндексация прошла и промолчала, потому что по
хешам содержимого (`V62`) ничего не изменилось и переembedding не понадобился, — и тогда молчание есть
признак исправности; либо она не отработала вовсе. Что разделило бы: строка журнала на входе в
`reindexStandingKnowledge` до проверки хешей, либо отметка времени последней записи в `context_chunks`.
Ни того, ни другого у меня нет.
*Философия:* `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (D014) — Алонзо Чёрч,
`BARCAN-TAG-08 SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda
calculus and Church's thesis — formal computability*. Сильная дословно: «правило хранится извлекаемым куском с источником, оценкой и классом дефекта, и цитируется
идентификатором». Слабая: «правило пересказано по памяти». Опровержение: «потребовать идентификатор образца;
отсутствие ссылки и есть галлюцинация». **Форма: сильная по хранению, не мерена по цитированию.** Куски
хранятся с источником и извлекаются по близости — это ровно «извлекаемый кусок с источником». Но требует ли
подсказка от исполнителя **цитировать идентификатор образца**, я не проверял; без этого сильную форму
целиком называть нельзя, а образец её требует.

**`V62__add_context_chunk_content_hash.sql`** — хранит хеш содержимого источника, чтобы не пересчитывать
вложения для того, что не менялось.
*Связи:* добавляет `context_chunks.content_hash` | меняет поведение `reindexStandingKnowledge`, которая
прежде удаляла и пересчитывала **каждый** источник на **каждый** тик.
*Ценность:* без него неизменные документы — уставы ролей, инварианты, корпус образцов — оплачиваются заново
при каждой переиндексации.
*Комментарий:* **периферия по действию, ядро по разряду.** Основание — указание оператора «общая цифра
быстро кончается», и в файле оговорено главное: «real cost, zero behavior change for anything that reads the
chunks». То есть заявлено, что правка касается **только** цены, а не того, что видит читающий. Это редкая
и правильная формулировка: изменение объявлено сохраняющим поведение под названным наблюдением.

Оговорю, чего в файле нет: **свидетельства** этого сохранения. Заявление сделано, проверка не названа.
*Живое:* отдельного признака работы хешей в журнале нет; см. две гипотезы в записи `V55` — если
переиндексация молчит потому, что хеши совпали, то это и есть работающий `V62`, но различить я не могу.
*Философия:* `ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` (D009) — Алонзо Чёрч,
`BARCAN-TAG-08 SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda calculus
and Church's thesis — formal computability*. Сильная дословно: «до замены кода, зависимости, модели или схемы
доказано сохранение под значимыми наблюдениями». Слабая: «заменили, тесты зелёные». Опровержение: «назвать
наблюдение, под которым доказывалось сохранение; „все тесты“ ответом не является, если тесты не покрывают
предмет замены». **Форма: слабая.** Наблюдение названо верно и точно — «что видит читающий куски», — но
доказательства сохранения под ним нет: ни теста, ни сверки выдачи до и после. Утверждение правильной формы
без исполнения.

**`V25__add_depends_on_and_hotspots.sql`** — вводит зависимость одной задачи от другой и перечень
«горячих» файлов проекта.
*Связи:* добавляет `tasks.depends_on` с внешним ключом на саму таблицу задач | заводит
`project_hotspot_files` с каскадом от проекта.
*Ценность:* без зависимости порядок работ не выразим; без перечня горячих файлов страж столкновений не
знает, какие пути опасны заранее.
*Комментарий:* **периферия по содержанию, ядро по последствиям.** Самоссылающийся внешний ключ — это
объявление, что зависимости образуют граф на задачах, а не список. Отмечу, чего здесь **нет**: ограничения,
запрещающего цикл. Внешний ключ на ту же таблицу циклы не исключает, и обнаружение цикла остаётся заботой
кода, а не хранилища. Сравнить с `chk_evidence_nodes_exactly_one_source` (раздел XXII), где онтологическое
правило исполняет база.
*Живое, 7 сентября 2026:* `PROJECT_HOTSPOT_FILES` в двадцатку крупнейших таблиц не входит, поэтому числа
строк у меня нет — и это факт о проекции, а не о базе. Косвенно механизм наблюдаем: страж столкновений
вычёркивает из предсказанной области именно общие пути — файлы сборки и миграции (раздел XXIIз).
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци, `BARCAN-TAG-01 ACTUALIST-OBJECT`,
принцип топологии пространственно-временных границ, anchor *Parts and Places / formal ontology of boundaries
and spatial parts*. Сильная дословно: «названа точка, где меняется владелец проверки, полномочия или
сохранения, и на неё есть тест». Слабая: «граница „понятна из структуры пакетов“». Опровержение: «удалить
проверку на границе; если ни один тест не покраснел, границы нет». **Форма: не мерено.** Перечень горячих
файлов есть объявление границы опасного, но теста на неё я не искал, а удалять проверку ради опровержения
означало бы править продуктовый код.

## Живое, требующее поправки к прежней записи

Замер настроек 7 сентября (`curl -s localhost:8080/api/settings`, поле `enabled`, источник `database`):

    gemini_context_learning_enabled   true
    gemini_project_observer_enabled   true
    verdict_gating_enabled            true
    verdict_gating_project_slug       (пусто)
    project_event_log_enabled         true

Первое, и здесь я **ошибся и исправляюсь**: я прочёл область заслона вердиктов как пустую и написал, что он
не применяется ни к одному проекту. Ошибка инструментальная: у строковой настройки значение лежит в поле
`maskedValue`, а я смотрел `enabled`, годное лишь для булевых. Перезамер того же дня даёт
`verdict_gating_project_slug = 'test-fiftieth'`, и живая выдача пульта управления это подтверждает: среди
непройденных условий стоят строки от слоёв вердиктов, а они попадают туда только когда решётка
**применилась**. Заслон включён и действует на живом проекте.

Второе, и это поправка к моей записи в разделе XXIIд: там сказано, что `V111` «закрепляет решение в базе».
Живое значение флага наблюдателя — **`true`**, тогда как `V111` вставляет `'false'`, и она последняя из
двух миграций, трогающих этот ключ (`V65` ставила `true`; замер: `grep -l gemini_project_observer_enabled`
по всем 137 файлам). Причину расхождения я **не устанавливаю и не додумываю**. Две гипотезы: значение
переписали после `V111` через изменяющий путь настроек, либо поле `enabled` в этом ответе не отражает
хранимое значение для данного ключа. Что разделило бы: прямое чтение строки `system_settings` по этому
ключу. Такого замера у меня нет, и до него утверждение «закреплено в базе» из раздела XXIIд следует считать
**неподтверждённым**.

На поведение это, по-видимому, не влияет: сам механизм — заглушка, «permanently inert», и включённый флаг
включать нечего. Но запись о том, что миграция что-то закрепила, замером не подтверждена, и я её не
оставляю без оговорки.

## Живое, общее для раздела

Смежный механизм, питающийся тем же контекстом, работает вхолостую: `OpsAuditorService` за 29 часов
**59 раз** собрал по 45–47 единиц свидетельств и **каждый раз** получил «Gemini returned no actionable
decisions» (замер: `grep -c "Gemini returned" `). Это не отказ и не ошибка — механизм исправен и отвечает
исправно; но пятьдесят девять одинаковых исходов подряд суть тот случай, который в разделе XXIг назван
сигналом без читателя, только с другой стороны: здесь есть читатель и нет сигнала.

# XXIIк. Приёмка: чем доказывают, что сделано для заказчика, а не для фабрики

**`V100__client_acceptance_traversals.sql`** — хранит свидетельство того, что заявленная цепочка ценности
была **пройдена на развёрнутом образце, заказчиком, на настоящем содержимом**.
*Связи:* создаёт `client_acceptance_traversals` с индексом по проекту и убыванию времени | цепочка
опознаётся `profile_id` и **дословным текстом звена**, а не порядковым номером | поле `walked_by` различает
проход заказчика и проход фабрики | поле `evidence` несёт то, что можно перепроверить.
*Ценность:* без неё «доставлено» вычисляется из числа слияний.
*Комментарий:* **ядро, и формулировка задачи в самой миграции точнее моей.** Дефект назван так: каждая
цепочка в корпусе рынка говорит, что должно быть **возможно**, и ничто не говорило, что что-либо было
**сделано**, — «so DELIVERED was computed from merge counts: a claim about what was built standing in for a
claim about what was shown».

Три решения здесь верны и каждое стоит назвать. Опознание по тексту звена, а не по номеру: номер «молча
пере-указал бы на другую цепочку, стоит профилю приобрести или переставить путь». Различение прохода: проход
со стороны фабрики «есть свидетельство о другом утверждении и обязан быть отличим, а не тихо посчитан тем
же» — **четвёртое появление** того же различения в перечне. И требование перепроверяемости: «a witness nobody
can re-check is an assertion, not evidence».
*Живое, 7 сентября 2026:* **свидетельств не производится.** В журнале за 42159 строк — ноль упоминаний
`traversal`/`traversed` (контроль: слово `acceptance` встречается 19 раз, и все 19 — о запасном разборе
запросов на слияние, то есть греп не слеп; `DELIVERED`/`delivered` — 2352 раза, из них 2309 пишет
`DeliveredWorkJudgmentService`). То есть суждение о доставке выносится непрерывно, а свидетель, ради
которого таблица заведена, молчит.
**Задача для кодинга:** производить запись обхода при живой проверке продукта и различать в ней проход
заказчика от прохода фабрики; до тех пор «доставлено» остаётся утверждением о построенном, а не о
показанном. Проверка: доля заявленных цепочек, имеющих обход, перестаёт быть нулевой. Опровергнет:
утверждение о доставке, вынесенное без единого обхода.
*Философия:* `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики, anchor *A Useful Four-Valued Logic / how
a computer should think — many-valued diagnostics*. Сильная дословно: «успешное завершение — **значение**,
которое не может существовать без выполненных предусловий, и оно несёт свидетельство для следующего шага».
Слабая: «статус `done` в поле и запись в лог». Опровержение: «сконструировать результат успеха, не имея
свидетельства; если это удаётся — форма слабая». **Форма: слабая, и опровержение выполняется живым замером.**
Схема сильной формы заведена — свидетельство отделено от статуса и снабжено проверяемым следом, — но
результат успеха конструируется без него: 2309 суждений о доставке при нуле обходов.

**`V92__client_runtime_observations.sql`** — заводит наблюдение за живым продуктом заказчика: удался ли
запуск, сколько длился, что ответила проверка здоровья.
*Связи:* создаёт `client_runtime_observations` с индексом по проекту и времени | позже `V104` добавляет
сюда различение отказа продукта и отказа прибора (раздел XXIIв) | потребитель — `BetaPosterior`.
*Ценность:* без неё о поставленном продукте фабрика не знает ничего, кроме того, что сама же его собрала.
*Комментарий:* **ядро, и это единственное место, где фабрика смотрит на продукт снаружи.** Отмечу
устройство: таблица только дополняется, наблюдение есть событие в момент времени, и продукт, изменившийся
после, его не отменяет.
*Живое, 7 сентября 2026:* наблюдения идут — 34 упоминания `launch_success` в журнале за 29 часов.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается на
собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная.** Наблюдение снимается извне, с живого образца, и его
источник предъявим — код ответа и задержка проверки здоровья хранятся в самой строке.

**`V15__create_project_final_reports.sql`** — хранит итоговый отчёт по проекту: сколько задач завершено,
сколько пунктов пожеланий, и содержимое отчёта.
*Связи:* создаёт `project_final_reports` | числа берутся из подсчёта завершённых задач и пунктов.
*Ценность:* без него итог проекта нигде не закреплён.
*Комментарий:* **периферия, и здесь тот самый счёт, который `V100` объявляет негодным.** Итог описан двумя
числами — сколько задач завершено и сколько было пожеланий, — то есть утверждением о **построенном**.
Показанного заказчику в этой схеме нет вовсе.
*Живое:* отдельных строк об итоговом отчёте в журнале за сутки нет; `PROJECT_FINAL_REPORTS` в двадцатку
крупнейших таблиц не входит, поэтому числа строк у меня нет — факт о проекции, не о базе.
*Философия:* `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007), формы дословно выше. **Форма: слабая.**
Итог есть счёт статусов, а не носитель свидетельства: отчёт можно составить, не имея ни одного обхода.

# XXIIл. Чем закрывается слой 3

Замер по всем 137 файлам (`python3`: описанные — те, чьё имя встречается в этом файле; остальные разложены
по признаку «есть ли в файле `UNIQUE`, `CHECK`, `UPDATE`, `DELETE` или `INSERT`»):

    всего миграций                    137
    описано полными записями           31
    осталось                          106
      из них меняют множество состояний 29
      из них меняют только форму        77

**Семьдесят семь меняют только форму** — добавляют столбец, таблицу, индекс или внешний ключ. По принятой в
разделе XXII демаркации они механизмами не являются: множество возможных состояний базы они не меняют, и
поведение другого механизма от их изменения не меняется иначе как через код, который их читает. Это
суждение, а не замер, и я называю его суждением.

**Двадцать девять оставшихся меняют множество состояний**, и они распадаются на семьи, у каждой из которых
уже есть описанный представитель:

- **Возвращающие отнятое** (`V122`, `V124`, `V125`, `V129`, `V130`, `V131`, `V133`, `V134`) — представители
  `V126` и `V132` описаны в разделе XXIIв; предикаты и основания у остальных того же рода, и `V132` прямо
  переприменяет предикат `V129` дословно.
- **Чистки и прекращения** (`V112`, `V113`, `V117`, `V119`) — тот же приём, что у описанной `V123`: элемент,
  структурно неспособный получить вердикт, покидает множество, по которому принимается решение, один раз в
  данных, а не фильтром у каждого читающего.
- **Полномочия аккаунтов** (`V31`, `V32`, `V46`, `V50`, `V89`) — перечисление ролевых меток и пределов
  одновременной работы; словарь тот же, что у описанного `JulesRoleCapabilities`, и та же слабость, что у
  описанной `V19`: состав задаётся перечислением, а не правилом.
- **Основания** (`V2`, `V3`, `V6`, `V10`, `V16`, `V17`, `V29`) — начальная схема и настройки; их предмет
  описан через сущности и типы, которые они заводят (`TaskEntity`, `WishlistEntity`, `AccountStatus`,
  `JulesSessionEntity`, `SystemSettingsService`).
- **Уже описанные по своему поводу** (`V48`, `V65`, `V93`, `V106`, `V114`) — упомянуты в разделах XXIIб,
  XXIIд, XXIIж и XXIж как части соответствующих механизмов.

**Итог по слою.** Механизмами в слое миграций являются немногие, и все они описаны. Общее у них названо в
разделе XXII: каждая несёт замер происшествия, которое её вызвала, и разбор причины, — и это единственное
место на фабрике, где причина изменения хранится рядом с изменением. Слабость слоя тоже названа и остаётся
открытой задачей 49: соответствие этих файлов живой базе при запуске никто не проверяет.

# XXIV. Скрипты: инструменты, которые запускает рука

Последний слой. Общее у него одно и оно решающее: **бэкенд не вызывает отсюда ничего**. Замер — точный греп
по именам файлов во всём дереве, кроме самого каталога: `append_role_logic.py`,
`generate_philosopher_patterns.py`, `mock_test_runner.py` — ноль упоминаний; `deploy.sh`,
`linear_webhook.py`, `generate_report.py`, `reset_project_data.sql` — по одному; `linear_sync.py` и
`check_system_drift.ps1` — по два; `audit_pr.py` — шесть (контроль: тот же греп находит
`FACTORY_MECHANISMS` в одном файле, значит он видит).

Это инструменты, которые запускает человек. Оператора в системе нет, и потому первый вопрос к каждому из
них — не «что он делает», а **«запускается ли он вообще»**.

**`generate_philosopher_patterns.py`** (самый крупный файл слоя) — порождает корпус философских образцов:
файлы `00`, `01`, `02`, указатель, отчёт о качестве и весь каталог `philosophers/`.
*Связи:* читает исходные уставы ролей | **стирает `philosophers/` целиком** (`shutil.rmtree`) и пишет
заново | ожидает 13 файлов BARCAN, 86 философов, не менее 20 личных образцов у каждого | вызывающих в коде
фабрики нет.
*Ценность:* без него корпус не существует, а вместе с ним — весь язык, которым описан этот перечень.
*Комментарий:* **ядро, и его положение странное: он производит то, на что фабрика опирается, и сам ничем
не связан с фабрикой.** Корпус попадает в подсказки исполнителям через выборку знаний (`V55`, `V62`), то
есть влияет на работу; но порождается он рукой, вне всякого расписания.

Главное, что я здесь измерил, — **корпус состоит из двух половин с разным происхождением**. Список образцов,
их идентификаторы, философы и якоря — порождены этим скриптом. А `03_PATTERN_STRENGTH.md`, откуда я каждый
такт беру сильную и слабую формы и опровержение, **этим скриптом не пишется вовсе** (контроль: `00_COMMON`
упоминается в скрипте пять раз, `03_PATTERN` — ноль). Он написан рукой.

Сейчас половины согласованы: в файле форм пятьдесят три семьи, в порождённом указателе те же пятьдесят три,
расхождений нет ни в одну сторону. Но **согласие это ничем не проверяется**: перезапуск генератора может
переименовать или перенумеровать образцы, а файл форм останется прежним, и ссылки вида
`ФАМИЛИЯ_NN_ОБРАЗЕЦ` начнут указывать не туда — молча, потому что сверять их некому.
**Задача для кодинга:** сверять две половины корпуса — семьи в файле форм против семей в порождённом
указателе — и ронять сборку при расхождении. Проверка, которую я провёл вручную, должна проводиться сама.
Опровергнет: переименованный образец, на который продолжают ссылаться по старому имени.
*Живое:* генератор последний раз правился 4 сентября, файл форм — 5-го, то есть **на день позже**; кто и чем
его правил, из репозитория не видно. Запусков генератора за сутки работы фабрики нет и быть не может: его
никто не вызывает.
*Философия:* `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (D014) — Алонзо Чёрч,
`BARCAN-TAG-08 SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda calculus
and Church's thesis — formal computability*. Сильная дословно: «правило хранится извлекаемым куском с
источником, оценкой и классом дефекта, и цитируется идентификатором». Слабая: «правило пересказано по
памяти». Опровержение: «потребовать идентификатор образца; отсутствие ссылки и есть галлюцинация».
**Форма: сильная по хранению и хрупкая по ссылке.** Куски извлекаемы, у каждого есть источник, оценка и
класс дефекта, и цитируются они идентификатором — всё как требует образец. Но идентификатор порождается
одной половиной корпуса, а его смысл живёт в другой, и связь между ними держится ни на чём.

**`append_role_logic.py`** — дописывает в уставы ролей раздел с математическим и логическим аппаратом роли.
*Связи:* пишет файлы уставов BARCAN | вызывающих нет.
*Ценность:* без него уставы ролей не несут формального аппарата, на который ссылается корпус.
*Комментарий:* **периферия по употреблению, ядро по предмету.** Он правит те самые уставы, из которых
генератор корпуса выводит образцы. То есть цепочка такова: этот скрипт правит уставы, тот скрипт выводит
из уставов корпус, корпус попадает в подсказки, подсказки определяют работу исполнителя. Вся цепочка
приводится в движение рукой и нигде не замкнута.
*Живое:* запусков нет, вызывающих нет.
*Философия:* `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци, `BARCAN-TAG-01 ACTUALIST-OBJECT`,
принцип топологии пространственно-временных границ, anchor *Parts and Places / formal ontology of boundaries
and spatial parts*. Сильная дословно: «названа точка, где меняется владелец проверки, полномочия или
сохранения, и на неё есть тест». Слабая: «граница „понятна из структуры пакетов“». Опровержение: «удалить
проверку на границе; если ни один тест не покраснел, границы нет». **Форма: слабая.** Точка, где рукописная
правка устава превращается в машинный корпус, границей не обставлена: нет ни проверки, ни следа о том, что
устав менялся после последнего порождения.

**`audit_pr.py`** — страж границ запроса на слияние; из всех скриптов на него ссылаются чаще прочих.
*Связи:* шесть упоминаний вне каталога — больше, чем у любого другого скрипта слоя.
*Ценность:* без него граница того, что запрос на слияние вправе трогать, проверяется только внутри фабрики.
*Комментарий:* **периферия по демаркации.** Поток он не держит: это проверка, запускаемая рядом, а не в
пути работы. Судить о его силе не берусь — содержимое я разбирал бегло, и это честнее, чем выдать беглый
взгляд за разбор.
*Философия:* **не мерено.**

**Остальные шесть — обслуга, механизмами не являются.** `deploy.sh` (полтора десятка строк, выкладка),
`linear_sync.py` и `linear_webhook.py` (связь с внешним трекером), `check_system_drift.ps1` (сверяет ветку и
контейнер), `generate_report.py` (собирает отчёт по одному давнему прогону — в имени файла отчёта стоит
`test-fourteenth`, тогда как живой проект `test-fiftieth`), `mock_test_runner.py` (три десятка строк,
печатает, что «проверяет» логику приветствия, ничего не проверяя), `reset_project_data.sql` (сброс данных
проекта). Каталог `modules` — три вспомогательных файла к ним.

Основание не считать их механизмами то же, что и в прочих слоях: их изменение не меняет поведения других
механизмов иначе как через руку, которая их запустит. Это суждение, а не замер, и я называю его суждением.
Отмечу отдельно `mock_test_runner.py`: он **делает вид, что проверяет**, и печатает успех, ничего не
исполнив. Как механизм он не считается, но как ложный сигнал он опаснее пустого места.

## Чем закрывается слой и весь перечень

Скриптов в каталоге дюжина с небольшим. Механизмами из них являются два — генератор корпуса и правщик
уставов, — и оба замечательны одним: **это единственные механизмы фабрики, которые не может запустить сама
фабрика.** Всё остальное здесь обслуга либо, в одном случае, имитация проверки.

Отсюда последнее наблюдение по перечню целиком. Язык, которым фабрика судит о себе, — корпус образцов, —
порождается вне её, рукой, и никакой механизм не следит ни за тем, чтобы он порождался, ни за тем, чтобы
две его половины сходились. Всё, что описано в этом файле, опирается на эти образцы. Опора же держится на
том, что кто-то не забудет запустить скрипт.

# XXV. Позвоночник потока: чем фабрика отвечает, жив ли поток

Слой бинов в исходном списке слоёв числился закрытым — «149 из 149». Это была моя ошибка, снятая замером в
разделе XVIII: записей у служб оказалось меньше, чем служб. Здесь описываются самые крупные из оставшихся —
те, на которые я ссылался всю смену, ни разу не разобрав.

**`FlowSpineService`** (1256 строк) — строит связный ответ на вопрос «в каком состоянии поток проекта, что
должно произойти дальше и кто это должен сделать», и записывает событие, когда ответ меняется.
*Связи:* зовут **шестнадцать механизмов** — среди них `OperationalPolicyService`,
`ClientDeliverableReadinessService`, `DeliveredWorkJudgmentService`, `PlannedWorkRecoveryService`,
`TrustSnapshotService`, `BranchGarbageCollectorService`, `GeminiContextService` | пишет
`FlowSpineEventRepository` | наружу выходит через `FlowSpineController`; отдельно раз в пятнадцать минут
идёт теневая проверка повторов по вложениям.
*Ценность:* без него у фабрики нет ни одного места, где состояние потока названо целиком, а не по частям.
*Комментарий:* **ядро, и по числу читателей — самое читаемое ядро фабрики.** Три вещи здесь сделаны верно.

Первое: **чтение и запись разведены**. `build` только считает и ничего не меняет; `observe` считает и
записывает. Механизм, который спрашивают о состоянии, не оставляет следов от самого вопроса — то есть
наблюдение не создаёт того, что наблюдает. Это прямая противоположность случаю, который я сам же и совершил
однажды, когда мой оборванный запрос породил ошибку в журнале и я о ней доложил.

Второе: **событие записывается только при изменении**, и признаком изменения служит не время, а состояние
плюс **хеш свидетельства**. То есть тождество здесь снова выводится из содержания, как в `V30` и `V137`
(раздел XXIIб), и одинаковые наблюдения не порождают строк.

Третье, и оно главное для суждения: каждое событие помечается режимом, и режим этот **жёстко задан строкой
`observe_only`**. Замер: во всём `main` режим задаётся ровно в двух местах — здесь и в
`OperationalFlowCoreService`, где он равен `flow_core_enforced`. То есть позвоночник, который называет узкое
место, требуемый переход и его владельца, **сам ничего не заслоняет по построению**; заслоняет отдельный
механизм ядра потока, а позвоночник только смотрит.

Это не дефект. Это осознанное разделение: тот, кто описывает состояние, и тот, кто на основании описания
запрещает, — разные механизмы. Но помнить это обязан всякий, кто читает позвоночник: его ответ **не есть
разрешение и не есть запрет**.
*Живое, 7 сентября 2026:* работает и отчитывается. В журнале за сутки более двух тысяч упоминаний, и строка
проверки доставки выглядит так: `passed=394 failed=0 REFUTED=45 unverified=244 (of which
asked-without-ruling=150, never asked=94)`. Здесь видно сразу два измеренных мною прежде обстоятельства.
Различение трёх исходов, описанное у `TaskEntity` (раздел XXI), **живое и попадает в отчёт**: непроверенные
разделены на «спросили и не получили решения» и «не спрашивал никто». И заслон качества по-прежнему не
работает: `failed=0` при `REFUTED=45` — то есть отвергает только критериальный инструмент, а заслон не
отверг ничего.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики (True/False/Both/Neither), anchor *A Useful Four-Valued Logic / how a computer
should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное** и
противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий исход
невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“». **Форма: сильная, и
это лучшее её исполнение из живых.** Неизвестное не только представлено, но и разделено надвое, и обе
половины **выведены в отчёт числами**: сто пятьдесят и девяносто четыре. Проигнорировать третий исход
читающий не может — он напечатан.

**`OperationalFlowCoreService`** (490 строк) — тот механизм, который на основании состояния потока
**запрещает**, а не описывает.
*Связи:* зовёт `FlowSpineService` | зовут `OperationalPolicyService`, `ContinuousOrchestrationService`,
`ClientDeliverableReadinessService`, `OperationalFlowCoreController` | пишет события с режимом
`flow_core_enforced`.
*Ценность:* без него описание состояния остаётся описанием, и ни одно действие им не ограничено.
*Комментарий:* **ядро, и здесь стоит настоящая граница полномочий.** Два режима в одном хранилище событий —
`observe_only` и `flow_core_enforced` — это записанное различие между «мы это видели» и «мы на этом
основании отказали». Различие полезное: по журналу можно отличить наблюдение от применения, не гадая.
*Живое, 7 сентября 2026:* работает и отказывает объяснимо. Доминирующая строка суток — отказ раздачи с
названным основанием: «not blocked by Flow Core state IMPLEMENTING; its own precondition is unmet — there is
nothing for it to act on right now». То есть механизм различает «запрещено состоянием потока» и «нечего
делать», и говорит, которое из двух.
*Философия:* `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006) — Джозеф Раз, `BARCAN-TAG-10 DEONTIC-PROHIBITION`,
принцип исключающих причин, anchor *Practical Reason and Norms / The Authority of Law*. Сильная дословно:
«до реализации полномочий составлена матрица прав, обязанностей, привилегий и власти, и на каждое отношение
есть тест разрешённого и запрещённого». Слабая: «роли перечислены, проверки написаны по месту».
Опровержение: «найти отношение, у которого нет теста запрета». **Форма: не мерено.** Матрица есть по
существу — действие, состояние потока и исход, — и отказ называет своё основание, что уже больше слабой
формы. Но есть ли на каждое отношение тест разрешённого и запрещённого, я не проверял, а без этого называть
форму нельзя.

**`SystemStatusService`** (832 строки) — сводит состояние всей системы в один ответ.
*Связи:* зовут `FlowSpineService` и другие; выходит наружу через `SystemStatusController`.
*Ценность:* без него нет единого ответа на вопрос «жива ли фабрика».
*Комментарий:* **ядро по употреблению, и на нём лежит доказанная вина.** Пятого сентября именно этот
механизм вместе с позвоночником сообщал `ok` все семь часов, пока пять аккаунтов были выключены и работа не
раздавалась. Вина не в том, что он солгал: он честно сообщал то, что умел проверить. Вина в том, что
**состав его проверки не покрывал того конъюнкта, который отказал**, — доступность аккаунта есть `status`
и `enabled`, а видел он первое.
*Живое, 7 сентября 2026:* `curl localhost:8080/actuator/health` отвечает
`{"springBootStatus":"UP","status":"ok"}`. Сегодня это правда: контейнеры подняты, оркестрация тикает. Но
проверить, что «ok» **умеет становиться не-ok** по той же причине, что и в сентябре, я не могу, не выключая
аккаунт, — то есть не трогая живую фабрику.
*Философия:* `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — это не заслон». **Форма: слабая, и опровержение выполнено историей.** Дефект был, длился семь
часов, и сводка не покраснела. Что она краснеет на каком-либо другом дефекте — возможно, но не показано.
**Задача для кодинга** (уже заведена как пункт 44): недоступность аккаунта по любому из конъюнктов обязана
быть видна в сводке одним чтением, и отказ обязан называть сработавшее условие.

**Вторая задача для кодинга, найденная замером нагрузки 8 сентября.** Эта служба делает **двенадцать
подъёмов целых таблиц** за один свой вызов (`grep -c "\.findAll()"` по файлу), а вызывают её через свод
потока на каждом обороте оркестрации — **раз в минуту** (12147 строк оркестрации за сутки). То есть каждую
минуту в память поднимается почти вся база, чтобы ответить на вопрос «как дела». Замер таблиц: задачи 665,
сессии 1767, разборы 812, журнал проекта 37467.

Чинить: спрашивать у базы **счётчики, а не строки** — двенадцать подсчётов на стороне хранилища вместо
двенадцати подъёмов. Место: двенадцать вызовов `findAll()` в `SystemStatusService`. Проверка: занятая
контейнером память перестаёт держаться у потолка, а время ответа свода состояния падает. Опровергнет: замер,
показывающий, что подъёмы дёшевы, потому что таблицы малы, — но по нынешним числам это не так.

*Живое, 8 сентября 2026, Codex:* частично снят один путь подъёма строк: `julesSessions(null)` теперь отвечает через `count()` и `countByStatus(...)` (`2cc3cfe`), проектный путь `julesSessions(projectId)` больше не читает все сессии, а берёт только `findByTaskIdIn(...)` для задач проекта (`75b0318`), и проектный `conflictDpmo(projectId)` больше не поднимает все ревью, конфликты и сессии перед фильтрацией, а читает `findByTaskIdIn(...)`, `findByJulesSessionIdIn(...)` и `findByTaskIdIn(...)` по уже найденным id. `linearCompleteness` больше не начинает с `taskRepository.findAll()`: глобально берёт `findByLinearIssueIdIsNotNull()`, а проектно — `findByProjectIdAndLinearIssueIdIsNotNull(...)`. В `operationalBlockers` снят общий подъём `julesSessionRepository.findAll()` для проверки открытых сессий в закрытых проектах: теперь каждый проект читает только `findByTaskIdIn(...)` по своим задачам. Глобальный `conflictDpmo(null)` больше не поднимает все `PrReview` ради двух чисел merged-review: all-time и last-7-days считаются через `countByMergedTrue()` и `countByMergedTrueAndCreatedAtAfter(...)`. Следующим тактом снят и общий подъём `TaskConflict` в том же глобальном пути: total/last-7-days считаются через `count()` и `countByDetectedAtAfter(...)`, Pareto — через grouped repository queries, active list — через отдельный active-only finder. Проектный `accounts(projectId)` больше не поднимает все аккаунты перед фильтром, а использует уже существующий `findAvailableForProjectOrderByNameAsc(...)`. Проектный `qualityGate(projectId)` больше не поднимает все задачи перед фильтром по проекту, а читает `findByProjectIdOrderByCreatedAtDesc(...)`. Глобальный `qualityGate(null)` больше не поднимает все задачи ради quality-report свода, а читает только строки с непустым `qualityGateReport` через `findByQualityGateReportIsNotNull()`. Проверка: `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 maven:3.9-eclipse-temurin-17 mvn -q -Dtest=SystemStatusServiceTest test` — пройдена. Механизм не закрыт: в `SystemStatusService` остаются другие `findAll()`-пути.


*Живое, 9 сентября 2026, Codex: такт заполнения записи, без правки кода.* Операторская поправка: предыдущие записи Codex описывали частные снятия нагрузки и тем самым оставляли опасную двусмысленность — срез улучшен, а механизм всё ещё не идеален. Для этого механизма целая форма важнее следующего `findAll`-среза, потому что свод одновременно является API для оператора, входом `FlowSpineService`/`OperationalTruthService` и зеркалом живого рантайма.

*Идеальная форма механизма:* `SystemStatusService` — read-only проекция состояния фабрики, а не второй владелец истины. Он имеет право собирать один ответ `getStatus(projectId)`, но каждое поле ответа должно быть либо: (1) прямым снимком владельца истины (`SystemProgressTracker`, `AiHealthTracker`, `GitHubApiBudgetService`, settings/runtime), либо (2) repository-level агрегатом/проекцией, либо (3) явно ограниченным списком для UI с названной причиной, почему нужны строки. Любой `ok` в ответе обязан быть выводим из тех же фактов, которые показывают застой, блокер или недоступность, а не из отдельного рассказа самого свода.

*Граница:* сервис читает состояние и возвращает карту секций; он не принимает решений диспетчера, не пишет состояние, не чинит аккаунты, не меняет задачи, не назначает истину по качеству и не подменяет владельцев (`FlowSpineService`, `OperationalTruthService`, `SystemProgressTracker`, `SixSigmaAuditService`, репозитории и внешние health/budget-снимки). `safeSection` может изолировать падение секции, но не имеет права превращать отсутствие доказательства в общий успех.

*Входы:* `projectId` или глобальный режим; настройки интеграций и `system_stall_status`; аккаунты; задачи; сессии Jules; Linear metadata; GitHub access/budget; PR reviews; task conflicts; wishlist; проекты; EMS metrics; Google AI resources; progress/AI health/runtime build metadata; Six Sigma CTQ breakdown.

*Выход:* 16 секций `getStatus`: `integrations`, `accounts`, `githubAccess`, `githubApiBudget`, `linearCompleteness`, `julesSessions`, `qualityGate`, `tasks`, `conflictDpmo`, `emsMetrics`, `sixSigma`, `aiResources`, `systemHealth`, `operationalBlockers`, `runtimeSource`, `aiHealth`. Для каждой секции запись механизма должна назвать класс ответа: summary, bounded UI list, owned snapshot, secondary projection, или blocker list.

*Связи и владельцы истины:* наружу выводит `SystemStatusController`; как внутренний свидетель используется `ProjectOperationalContextService`, `FlowSpineService` и `OperationalTruthService`. Истина о прогрессе принадлежит `SystemProgressTracker`, истина о блокерах — источникам блокеров и `system_stall_status`, истина о Six Sigma CTQ — `SixSigmaAuditService`, истина о runtime — build/env и health/budget snapshots. Свод не может объявить более сильное утверждение, чем дают эти владельцы.

*Инварианты:* (1) read-only: вызов `getStatus` не пишет в БД и не инициирует внешние побочные эффекты; (2) `systemHealth.status` и `operationalBlockers.status` не могут противоречить одному и тому же stall/progress свидетельству; (3) глобальные summary-секции не поднимают таблицы строк ради счётчиков; (4) списки строк существуют только там, где UI действительно показывает строки, и имеют явную границу/причину; (5) проектный режим не читает чужие проекту строки перед фильтрацией; (6) `sixSigma` не должен повторно расходиться с `qualityGate`/`conflictDpmo`, потому что он вторичная сборка их CTQ; (7) carrier-задачи исключаются из `tasks` тем же смыслом, который использует `EmsMetricsService`, без строкового угадывания JSON.

*Сильная форма:* все 16 секций классифицированы; у каждой секции указан владелец истины и источник данных; `ok` опровергается отсутствием выхода в окне или явным блокером; все summary/aggregate ответы считаются на стороне хранилища или владельца снимка; оставшиеся списки строк доказаны как UI-контракт и ограничены; есть focused test/command, который краснеет при возврате к старому поведению.

*Текущая форма:* не закрыта. Старые Codex-коммиты уменьшили часть нагрузки, но не доказали идеальность механизма. На текущем коде остаются `findAll()` в глобальных `accounts(null)`, `operationalBlockers(null)`, `tasks(null)` и `emsMetrics(null)`; часть из них может быть законным списком, часть — summary/secondary projection. Без классификации секций следующая кодовая правка будет гаданием.

*Опровержение:* для правды свода — искусственно или тестом создать окно без реального выхода при `system_stall_status=ok` и убедиться, что свод не может вернуть общий `ok`; для веса — команда по файлу не должна находить неклассифицированный `findAll()` в `SystemStatusService`; для договоров UI — snapshot/contract должен показать, какие секции реально требуют `items`.

*Критерий закрытия:* запись считается заполненной, когда все 16 секций имеют класс ответа, владельца истины, допустимый источник и проверку; механизм считается идеальным только когда каждый оставшийся подъём строк либо заменён агрегатом/проекцией, либо доказан как bounded UI list, а тесты покрывают невозможность `ok` без прогресса и невозможность скрыть blocker. До этого писать «считаю механизм идеальным» запрещено.


*Классификация 16 секций, 9 сентября 2026:* эта таблица закрывает именно запись механизма, а не реализацию. В ней видно, где строковый подъём законен как UI-список, где нужен агрегат, а где свод обязан только показать чужой снимок.

| Секция | Класс ответа | Владелец истины | Допустимый источник | Статус записи / что опровергнет |
|---|---|---|---|---|
| `integrations` | owned snapshot / editable config list | `SystemSettingsService` | `listSettings()` и ответы `PUT /api/settings` | запись заполнена; опровергнет настройка, сохранённая владельцем, но отсутствующая в секции |
| `accounts` | summary + UI list | `AccountRepository` / account admin API | список аккаунтов нужен `AdminDashboard.svelte` для таблицы и действий; проектный путь — project-available finder | класс указан; реализация идеальна только при явной границе пула или пагинации; опровергнет рост пула без bound/limit |
| `githubAccess` | latest health snapshot | таблица `github_access_status` | `ORDER BY checked_at DESC LIMIT 1` | запись заполнена; опровергнет более свежая строка, не попавшая в `latest` |
| `githubApiBudget` | owned external-budget snapshot | `GitHubApiBudgetService` | `snapshot().asMap()` | запись заполнена; опровергнет расход GitHub API, не меняющий snapshot |
| `linearCompleteness` | summary + diagnostic issue list | `TaskRepository` + `LinearIssueMetadataRepository` | только задачи с `linearIssueId` и metadata по task id | запись заполнена; опровергнет task без Linear id в расчёте или Linear task без metadata-пробы |
| `julesSessions` | summary | `JulesSessionRepository` | global `count/countByStatus`, project sessions by project task ids | запись заполнена; опровергнет глобальный `findAll()` ради счётчиков |
| `qualityGate` | summary + bounded defect list + secondary CTQ | `TaskRepository` + `SixSigmaAuditService` | tasks with `qualityGateReport`, project tasks, `defectItems` capped at 20, shared CTQ breakdown | запись заполнена; опровергнет uncapped defect list или CTQ, расходящийся с `SixSigmaAuditService` |
| `tasks` | summary only | `TaskRepository` with the same carrier definition as `EmsMetricsService` | status counts over real non-carrier work | класс указан; реализация не закрыта: `tasks(null)` всё ещё нуждается в доказанном carrier-source; см. `AGY_ASKS.md` |
| `conflictDpmo` | summary + active blocker list | `PrReviewRepository` + `TaskConflictRepository` | merged/conflict counts, Pareto aggregates, active-only conflicts for UI | класс указан; реализация идеальна только при явном bound/limit active list; опровергнет много активных конфликтов, возвращённых без ограничения |
| `emsMetrics` | secondary dashboard projection | `EmsMetricsService` | scoped task/wishlist evidence; role readiness fixed to 13 BARCAN roles | класс указан; реализация не закрыта: global path подаёт полные `tasks`/`wishlist`; опровергнет возможность получить те же DTO из агрегатов/проекций |
| `sixSigma` | secondary control projection | `SixSigmaAuditService`, `qualityGate`, `conflictDpmo` | already computed CTQ/merge evidence for the same request | класс указан; реализация идеальна только если не расходится и не перечитывает соседние секции; опровергнет different values in `qualityGate`/`conflictDpmo` and `sixSigma` |
| `aiResources` | owned resource snapshot | `GoogleAiResourceService` | `resourceMatrix()` | запись заполнена; опровергнет live resource state, absent from owner snapshot |
| `systemHealth` | progress/stall snapshot | `SystemProgressTracker` + `system_stall_status` owner | `lastProgressAt`, `minutesSinceProgress`, effective stall status | класс указан; реализация исторически не закрыта: `ok` должен быть невозможен без fresh progress; опровергнет stale progress with `status=ok` |
| `operationalBlockers` | blocker list | blocker source owners + project/task/session/wishlist stores | scalar stall status, GitHub budget, duplicate content, terminal-project WIP | класс указан; реализация идеальна только при bound или aggregate path для global project scan/list; опровергнет hidden blocker or contradiction with `systemHealth` |
| `runtimeSource` | build/runtime snapshot | deployment/build environment | `eneik.build.git-sha`, `git-dirty`, `time` | запись заполнена; опровергнет running SHA that differs from reported build properties |
| `aiHealth` | owned AI call-health snapshot | `AiHealthTracker` | `snapshot()` | запись заполнена; опровергнет recorded AI call failure absent from snapshot |

*Итог классификации:* запись `SystemStatusService` теперь заполнена по 16 секциям. Реализация механизма всё ещё не идеальна: открытые места — `tasks(null)` carrier-source, global `emsMetrics`, возможная неограниченность `accounts`/`conflictDpmo.activeConflicts`/`operationalBlockers`, и исторический `systemHealth ok` без fresh progress. Дальше код трогать нельзя, пока оператор не разрешит реализацию по этим уже названным границам.

*Свидетельства такта:* `grep -n "\.findAll()\|getStatus(\|private Map\|public Map\|SystemProgressTracker\|ok" src/main/java/com/eneik/production/services/dashboard/SystemStatusService.java` показал текущие точки чтения и секции; `grep -RIn "SystemStatusService" src/main/java | head -80` показал прямых потребителей; `sed -n '1,120p' docs/philosopher-patterns/02_RAG_MATHEMATICAL_ASSIGNMENT_MODEL.md`, `sed -n '1,80p' docs/philosopher-patterns/philosophers/BARCAN-TAG-00_CODE-GUARDIAN_01_lyudvig-vitgenshteyn.md` и `sed -n '250,272p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md` подтвердили применимый образец.

*комментарий для Антигравити:* `SystemStatusService` пока не идеален, но следующий правильный ход — не новая мелкая правка, а таблица классификации всех 16 секций: summary / bounded UI list / owned snapshot / secondary projection / blocker list, с владельцем истины и опровержением для каждой. Философия: `BARCAN-TAG-00_CODE-GUARDIAN`, Людвиг Витгенштейн, publication anchor `Philosophical Investigations - language-games, meaning as use, private-language argument`, pattern `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, family `ANTI_MIRROR_TELEMETRY`, defect `D013 Runtime drift`. Дополнительный заслон для carrier-задач — `LYUDVIG_VITGENSHTEYN_20_RELIABILITY_CHAIN` / D010: считать можно только по источнику с сохранённым происхождением, не по совпадению строки в JSON.

*Живое, 10 сентября 2026, Antigravity (L2): такт консолидации горячего пути getStatus(projectId) и когерентности sixSigma.*
Консолидирован центральный горячий путь опроса состояния фабрики:
1. В `getStatus(UUID projectId)` (вызывается каждую минуту оркестрацией) устранено 8-кратное повторное вычитывание `taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` в подсекциях `julesSessions`, `qualityGate`, `operationalBlockers`, `tasks`, `emsMetrics`, `conflictDpmo`. Задачи проекта теперь загружаются строго 1 раз в заголовке `getStatus` и передаются в секции через перегруженные методы `(projectId, scopedTasks)`, сохраняя обратную совместимость с рефлексивными тестами и внешними потребителями.
2. Секция `sixSigma` переведена на прямое потребление уже рассчитанных данных `qualitySection` и `conflictSection` (через хелпер `extractSectionData`), ликвидировав повторный синхронный перезапуск расчёта качества и конфликтов и устранив риск рассинхронизации CTQ-метрик.
3. В `accounts(null)` устранён неограниченный in-memory sort через переход на `accountRepository.findAllByOrderByNameAsc()`.
4. Добавлены заслоняющие тесты `getStatusConsolidatesTaskAcquisitionToOneQuery` (проверяет ровно 1 обращение к `taskRepository` на горячем пути) и `getStatusNullProjectDoesNotQueryByProjectId` (проверяет отсутствие проектных запросов и вызов сортированного поиска на холодном пути) в `SystemStatusServiceTest`. Все 11 тестов сервиса и 4 интеграционных теста `SystemStatusControllerIntegrationTest` успешно пройдены.
*Философский заслон:* `BARCAN-TAG-00_CODE-GUARDIAN`, Людвиг Витгенштейн (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013), `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).

Это частный случай общего: **84 места в коде поднимают таблицу целиком**, из них таблицу задач — 15,
таблицу сессий — 14. Одна привычка, повторённая восемьдесят четыре раза, и она же — главная причина, почему
лёгкая по замыслу система держится у гигабайта.


**Общий механизм: дисциплина full-table reads** — не всякий `findAll()` дефект, но каждый неограниченный подъём строк обязан иметь владельца, класс пути и проверяемую причину.

*Живое, 9 сентября 2026, Codex: такт заполнения записи, без правки кода.* Очередь называла 84 места; текущий замер после старых Codex-срезов даёт 76 вызовов `.findAll()` в `src/main/java`. Это не означает 76 дефектов: часть путей может быть admin-list, maintenance sweep, tiny lookup или deliberate cache/static build. Ошибка старого метода была в том, что число воспринималось как приглашение уменьшать счётчик, а не как механизм, требующий классификации.

*Идеальная форма механизма:* любой полный подъём таблицы находится в реестре с пятью полями: owner of truth, caller/cadence, table/cardinality evidence, class (`hot summary`, `bounded UI list`, `project-scoped list`, `maintenance sweep`, `admin/export`, `static/cache build`, `small reference table`), и refutation. Summary/count/project-filter paths обязаны спрашивать хранилище ровно о нужной величине; row-list paths обязаны быть ограничены назначением UI, проектом, статусом, временем или явным maintenance budget. Уменьшение общего числа `findAll()` само по себе не является успехом.

*Граница:* механизм описывает дисциплину чтения repository rows across backend. Он не запрещает все `findAll()`, не заменяет бизнес-логику, не требует менять callers без их записи механизма и не разрешает ломать API shape ради экономии памяти. Он запрещает только неклассифицированный полный подъём строк в hot path или подъём строк ради ответа, который по смыслу является счётчиком/агрегатом.

*Входы:* текущий grep-inventory `.findAll()`; таблицы и их размер/cadence из живых замеров; список hot callers (`SystemStatusService`, context retrieval, orchestration/dashboard/metrics paths); UI/API contract evidence; repository methods that already express count, status, project, time or active-only predicates.

*Выходы:* ranked inventory of remaining full-table reads; keep/replace decision with source evidence; per-path closure criterion; questions in `AGY_ASKS.md` where the safe predicate is unknowable; future implementation tasks only after the path is classified.

*Связи и взаимодействия:* первый зависимый механизм — `SystemStatusService`, потому его record уже отделяет summary from bounded UI list. Второй — `GeminiContextService`, потому его record отделяет hot retrieval from reindex/static cache. Далее эта дисциплина касается controllers and services by evidence, not by shame: `FalsificationCycleService`, `QualityMetricsController`, `SixSigmaAuditService`, `ProjectOperationalContextService`, `ConstraintIdentificationService`, `EvidenceCoherenceService`, `ProjectFlowService`, `KaizenService`, internal probe/admin controllers and maintenance services.

*Инварианты:* (1) each number has a command; (2) nonzero `findAll()` count is not a defect until classified; (3) hot summary cannot fetch rows just to count/filter them; (4) project-specific answer cannot fetch all projects' rows and filter in memory; (5) UI list cannot be called bounded unless the UI/API bound is named; (6) maintenance/cache full read must name cadence, owner and why it is outside hot path; (7) behavior compatibility must be defined before replacing a read; (8) if carrier/meta/task identity is needed, it must come from a reliable field/source, not string coincidence.

*Сильная форма:* all 76 current `.findAll()` call sites are classified in a live inventory with owner, class, cadence, table evidence, keep/replace decision and refutation; every hot summary/project path has a repository aggregate/projection or a recorded blocker; future commits may say `считаю механизм идеальным` only when no unclassified hot full-table read remains.

*Слабая форма:* grep count decreases, but remaining calls are unknown; or a call is replaced because it looks large, without proving caller semantics and API/UI contract. This is the harmful form the operator rejected.

*Текущая форма:* не закрыта. Current evidence: `grep -RIn "\.findAll()" src/main/java --exclude-dir=target | wc -l` returns 76. Top files by current count: `FalsificationCycleService` 7, `QualityMetricsController` 7, `SixSigmaAuditService` 6, `SystemStatusService` 5, `ProjectOperationalContextService` 4. The record is now strong enough to prevent blind slices, but the full 76-row inventory is not yet filled.

*Опровержение:* ask for any remaining `.findAll()` call site and require its registry row. If none exists, mechanism record is incomplete. Ask whether a changed path preserves UI/API output; if no fixture/snapshot/contract exists, implementation is not allowed. Ask whether a count/summary path still loads rows; if yes and not classified as bounded list/maintenance, the mechanism is violated.

*Критерий закрытия:* fill the 76-row inventory, then classify each row as allowed or replacement-needed; open `AGY_ASKS.md` only for predicates that cannot be stated safely from evidence; only then allow code tacts for rows whose ideal replacement is known. The mechanism itself is ideal only when every remaining full-table read has a documented, checkable reason or has been replaced without changing the owning mechanism's contract.

*Свидетельства такта:* `grep -RIn "\.findAll()" src/main/java --exclude-dir=target | wc -l`; `grep -RIn "\.findAll()" src/main/java --exclude-dir=target | awk -F: '{count[$1]++} END {for (f in count) print count[f], f}' | sort -nr | head -25`; `grep -RIn "taskRepository\.findAll()\|julesSessionRepository\.findAll()" src/main/java --exclude-dir=target`; `grep -n "ACP-025\|ACP-060\|ACP-061\|ACP-077" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `sed -n '80,88p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`.

*комментарий для Антигравити:* механизм не идеален. Следующий такт — не правка кода, а первая страница реестра: классифицировать top-10 `.findAll()` call sites by owner/cadence/class/refutation, starting with `FalsificationCycleService`, `QualityMetricsController`, `SixSigmaAuditService`, `SystemStatusService`, `ProjectOperationalContextService`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: первая страница реестра hotspot-владельцев, без правки кода.* Заполнена не реализация и не новый `findAll`-срез, а верхний слой реестра: какие владельцы дают первые 44 из 76 текущих вызовов и какой класс работы нужен до любой замены. Это не закрывает механизм, потому что сильная форма требует 76 строк на уровне call site; эта страница только фиксирует порядок, класс и опровержение для самых плотных владельцев.

| Owner / evidence | Sites | Caller / cadence | Class | Keep / replace decision and refutation |
| --- | ---: | --- | --- | --- |
| `FalsificationCycleService` (`grep -n "\.findAll()" ...`: 164, 239, 245, 275, 358, 1197, 1371) | 7 | scheduled daily, 15-minute continuation, two-day philosophical cycle, audit-violation apply path | `maintenance sweep` + `small reference table` candidates | Нельзя править вслепую: active projects and active roles are whole-factory scheduler inputs, but each row still needs line-level proof. Refutation: if project/role tables grow or scheduler cadence makes inactive rows visible in the hot window, require `findByStatus` / active-role repository predicates or `countByActive`. |
| `QualityMetricsController` (`/api/quality`, lines 40, 41, 47, 76, 133, 135, 154) | 7 | operator/API reads `/conflict-dpmo` and `/defect-summary` | `hot summary` with unbounded detail lists | Replacement-needed before code: counts, last-7-days and project breakdown are aggregates, while returned defect items need an explicit UI bound. Refutation: a GET request loads full review/conflict/session/task/onboarding tables to answer numbers, or returns an unbounded item list without named UI contract. |
| `SixSigmaAuditService` (105, 109, 242, 355, 394, 425) | 6 | factory/project Six Sigma audit, reusable CTQ/defect calculations | `audit summary` + `project-scoped summary` | Replacement-needed where project-scoped counts start from whole tables; `getActiveProjectId()` may be a small project resolver only if project cardinality/cadence is declared. Refutation: project audit asks all tasks/findings/reviews/conflicts and filters in memory when repository predicates can express project/feature/time/status. |
| `SystemStatusService` (124, 367, 467, 481, 484) | 5 | operator dashboard and `FlowSpineService`/truth consumers | already classified `hot dashboard summary` + bounded-list candidates | Mechanism record exists; remaining code is not ideal. Refutation is already named there: global accounts/tasks/wishlist/project reads must prove list semantics/bounds or move to aggregates/projections without changing dashboard shape. |
| `ProjectOperationalContextService` (95, 101, 110, 115) | 4 | project context for AutoMerge, DesignShop, AI/resource consumers | `hot project-scoped context` | Replacement-needed at record level: sessions, reviews, conflicts and accounts are project-scoped questions but read whole tables first. Refutation: a project context build observes rows belonging to other projects before filtering, or cannot state a bound for the context block it hands to downstream mechanisms. |
| `ConstraintIdentificationService` (68, 77, 132) | 3 | TOC drum/buffer calculations for one project | `project-scoped metric` + capacity reference table | Replacement-needed for task reads; account read may be a small reference-table candidate only with row-count evidence. Refutation: `identifyDrum(projectId)` or `recommendedBufferCapacity(projectId)` loads all tasks before filtering to one project/status. |
| `EvidenceCoherenceService` (518, 520, 529) | 3 | reliability calibration inside evidence-coherence engine | `reliability summary` | Replacement-needed if called per run/request: standardized/reverted proposal counts and source-type evidence-node selection are aggregate/filter questions. Refutation: source reliability loads all proposals/nodes when `countByStatus` or `findBySourceType` can express the acquisition process. |
| `ProjectFlowService` (1755, 5036, 6429) | 3 | worker selection, PR audit freshness, project list endpoint | mixed: `hot worker project-scope` + `bounded UI list` candidate | First two are replacement-needed candidates because they filter sessions by project after full read; `listProjects()` can be allowed only as an explicit bounded operator list. Refutation: worker decision sees sessions outside its task/project scope, or projects endpoint lacks a documented bound/pagination decision. |
| `KaizenService` (78, 143, 158) | 3 | proposal list, open-sibling dedupe, delete matching duplicates | `admin/list` + `maintenance/dedup predicate` | `allProposals()` may be UI/admin only if bounded; sibling/delete paths are replacement-needed because exact category/target/project predicates are known. Refutation: dedup reads every proposal instead of the one equivalence class it is checking. |
| `InternalGeminiObserverController` (278, 305, 311) | 3 | explicit internal diagnostic endpoints | `admin/diagnostic` candidate | Keep only if documented as manual diagnostic and outside hot path; otherwise replacement-needed. Refutation: any scheduler or ordinary UI path calls these diagnostics, or account-capacity loads all sessions/accounts when account/status predicates are available. |

*Свидетельства реестра:* `grep -RIn "\.findAll()" src/main/java --exclude-dir=target | awk -F: '{count[$1]++} END {for (f in count) print count[f], f}' | sort -nr | head -10`; exact `grep -n "\.findAll()"` for each of the ten files above; focused `sed -n` context around the listed lines; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `sed -n '227,235p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`.

*комментарий для Антигравити:* механизм не идеален. Следующий такт — перевести первую hotspot-страницу из owner-level в line-level registry: начать с семи строк `FalsificationCycleService`, для каждой указать exact method, repository/table, owner of truth, cadence, allowed/replacement decision, missing cardinality evidence if any, and closure criterion. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: line-level registry for `FalsificationCycleService`, без правки кода.* Первая hotspot-строка owner-level registry раскрыта до семи конкретных call sites. Это по-прежнему не разрешение на код: запись фиксирует, где идеальный механизм уже знает замену, а где честно требует доказать малую/статичную таблицу ролей.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `FalsificationCycleService.java:164` in `runDailyFalsificationCycle()` | `ProjectRepository` / projects | scheduled `falsification-cycle.cron:0 0 2 * * ?` | `maintenance sweep`, active-project filter | Replacement-known: ask repository for active projects, not all projects. Refutation: inactive/frozen/retired project is loaded before line 165. Closure: `runDailyFalsificationCycle` obtains only active projects (`findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` already exists) and still sets/clears `LogScope` per visited project. |
| `FalsificationCycleService.java:239` in `advanceInProgressPhilosophicalDiscussions()` | `RoleRepository` / role catalogue | scheduled `philosophical-falsification-continuation.cron:0 */15 * * * ?` | `small reference table` candidate in a 15-minute path | Not ideal until role cardinality/freshness is named. The method needs active role charters for continuation, but `RoleRepository` currently has no active predicate. Refutation: role table size or mutation cadence is unknown, or inactive roles are loaded every 15 minutes just to discard them. Closure: either document bounded/static role catalogue with row-count evidence, or introduce an active-role finder/count before code. |
| `FalsificationCycleService.java:245` in `advanceInProgressPhilosophicalDiscussions()` | `ProjectRepository` / projects | same 15-minute continuation scheduler | `maintenance sweep`, active-project filter | Replacement-known: existing `findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` expresses the predicate. Refutation: every 15 minutes the path loads non-active projects before line 246. Closure: continuation sweep iterates only active projects and preserves the invariant that it never starts a new discussion, only advances an existing worker. |
| `FalsificationCycleService.java:275` in `runWeeklyPhilosophicalFalsificationCycle()` | `ProjectRepository` / projects | scheduled `philosophical-falsification.cron:0 0 3 */2 * ?` | `maintenance sweep`, active-project filter | Replacement-known: existing active-project finder can preserve order and predicate. Refutation: two-day philosophical cycle reads non-active projects before line 276. Closure: the cycle asks storage for active projects only and still delegates feature-flag enforcement to `executePhilosophicalCycleForProject`. |
| `FalsificationCycleService.java:358` in `executePhilosophicalCycleForProject(project, force)` | `RoleRepository` / role catalogue | manual `ProjectController`, `GeminiObserverActionService`, or scheduled project cycle | `small reference table` candidate / active-role corpus | Not ideal until role catalogue evidence is recorded. It needs active role batch input; if roles are a fixed 13-role corpus, full read may be allowed as bounded reference data, but that fact is not yet in the registry. Refutation: inactive roles are loaded for a single-project forced run, or the role count/freshness rule is absent. Closure: bounded role-catalogue proof or repository active-role finder. |
| `FalsificationCycleService.java:1197` in `executeCycleForProject(project)` | `RoleRepository` / role catalogue | daily code-defect self-falsification, after readiness and no-open-wishlist guards | `small reference table` candidate / audit prompt input | Not ideal until role cardinality/freshness is named. The prompt likely needs all active role charters, so changing behavior is unsafe before preserving prompt content. Refutation: role entities are fetched all-table after every ready project gate without a bounded-role proof. Closure: active-role source is explicit and prompt fixture proves same role set/order before any implementation. |
| `FalsificationCycleService.java:1371` in `applyAuditViolations(...)` | `RoleRepository` / role catalogue | async Jules completion path via `JulesDispatchService.applyAuditViolations` | `summary/count` | Replacement-needed in ideal form: this line counts active roles and should not materialize entities just for a denominator. Refutation: count-only path loads roles before counting. Closure: active-role count comes from `countByActiveTrue` or from dispatch metadata, and tests keep violation/follow-up semantics unchanged. |

*FalsificationCycleService current status:* not ideal. Three project reads have known repository-level replacements; one count-only role path is replacement-needed in principle; three role-corpus reads are unresolved until the registry records role table cardinality, freshness and prompt-order contract. No code change is allowed from this table alone until the relevant closure row is satisfied.

*Свидетельства реестра:* `nl -ba src/main/java/com/eneik/production/services/FalsificationCycleService.java | sed -n '150,252p'`; `nl -ba ... | sed -n '268,366p'`; `nl -ba ... | sed -n '1158,1228p'`; `nl -ba ... | sed -n '1364,1376p'`; `grep -RIn "runDailyFalsificationCycle\|advanceInProgressPhilosophicalDiscussions\|runWeeklyPhilosophicalFalsificationCycle\|executePhilosophicalCycleForProject\|applyAuditViolations" src/main/java src/test/java --exclude-dir=target | head -120`; `grep -RIn "interface ProjectRepository\|findByStatusOrderByCreatedAtDesc" src/main/java/com/eneik/production/repositories src/main/java | head -80`; `grep -RIn "interface RoleRepository\|findBy.*Active\|countBy.*Active" src/main/java/com/eneik/production/repositories src/main/java/com/eneik/production/models/persistence/RoleEntity.java | head -80`.

*комментарий для Антигравити:* механизм не идеален. Следующий такт — добавить роль-catalogue evidence before code: exact live/fixture role count, whether roles are intended as bounded static reference data, required order for audit prompts, and whether `RoleRepository` needs `findByActiveTrue` / `countByActiveTrue`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: role-catalogue evidence for `FalsificationCycleService`, без правки кода.* Проверил, можно ли честно объявить role `findAll()` малой статичной таблицей. Источник схемы говорит: `V3__core_agency_schema.sql` создаёт `roles.active BOOLEAN DEFAULT TRUE NOT NULL` and seeds 12 roles; `V46__add_role_barcan_tag_12.sql` adds the 13th role. Source tree has exactly 13 top-level `BARCAN-TAG-*.md` charters, and runtime `/api/roles/{tag}/rules` returns HTTP 200 for all tags `BARCAN-TAG-00` through `BARCAN-TAG-12`. Search for role deactivation found only `RoleEntity.setActive(...)`, no application/migration call that sets roles inactive. Direct live SQL active-count was deliberately not forced: read-only H2 Shell against Docker volume failed with file lock while backend is running, and stopping the backend is outside this tact.

*Вывод по role-corpus rows in `FalsificationCycleService`:* bounded-reference evidence is strong enough to treat the corpus as small in cardinality (13 charters), but not strong enough to call the mechanism ideal. The hidden defect is order, not size: philosophical start/continue uses the `findAll()` result order directly for `subList(0, 3)` and for remaining-role batches; tests also mock repository order rather than asserting canonical BARCAN order. Therefore lines 239, 358 and 1197 remain unresolved until order is made explicit. Line 1371 is not a corpus read at all; it is count-only and should be a count predicate or preserved dispatch metadata in the ideal implementation.

*Question recorded in `AGY_ASKS.md`:* should the active-role sweep order be canonical `BARCAN-TAG-00` through `BARCAN-TAG-12`, or is current database/repository order intended to carry priority? Until this is answered, future code must not silently replace `roleRepository.findAll().stream().filter(RoleEntity::isActive)` in philosophical batching with an unordered predicate, because that could change which roles speak in each turn.

*Свидетельства role-catalogue:* `sed -n '1,85p' src/main/resources/db/migration/V3__core_agency_schema.sql`; `sed -n '1,12p' src/main/resources/db/migration/V46__add_role_barcan_tag_12.sql`; `find . -maxdepth 2 -name 'BARCAN-TAG-*.md' -printf '%f\n' | sort | wc -l`; runtime loop `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/roles/$tag/rules` for tags 00..12; `grep -RIn "UPDATE roles\|setActive\|active = false\|active=false" src/main/java src/main/resources/db/migration scripts docs --exclude-dir=target`; failed read-only H2 Shell command with `ACCESS_MODE_DATA=r` showing `Database may be already in use`.

*комментарий для Антигравити:* механизм не идеален. Не правь role-corpus reads в `FalsificationCycleService`, пока не решён порядок активных ролей; применимая поправка — сделать active-role acquisition reliable by source, freshness and order, then use `findByActiveTrueOrderByTagAsc`/`countByActiveTrue` or an explicitly documented static catalogue. Следующий независимый documentation tact can continue the 76-row inventory with `QualityMetricsController` line-level registry. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: line-level registry for `QualityMetricsController`, без правки кода.* Раскрыта вторая hotspot-строка: семь full-table reads в `/api/quality`. Здесь почти нет законных row-list причин: `/conflict-dpmo` возвращает только агрегаты and `byProject`, while `/defect-summary` returns totals plus unbounded `items` arrays. Runtime today is small (`/conflict-dpmo`: HTTP 200, 384 bytes, `byProject=1`, `totalMergeAttempts=688`, `conflicts=5`; `/defect-summary`: HTTP 200, 877 bytes, `totalDefects=5`, item counts `5/0/0`), but that is a current observation, not a bound.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `QualityMetricsController.java:40` in `getConflictDpmo()` | `PrReviewRepository` / PR reviews | REST `GET /api/quality/conflict-dpmo`; no frontend caller found by grep | `hot summary` | Replacement-needed. The answer is counts: merged all-time, merged last-7-days and project breakdown. Refutation: endpoint materializes every `PrReviewEntity` to count merged rows. Closure: global counts use existing `countByMergedTrue*`; per-project merged counts come from an explicit session/project projection or grouped query with the same `merged` and time predicates. |
| `QualityMetricsController.java:41` in `getConflictDpmo()` | `TaskConflictRepository` / task conflicts | same endpoint | `hot summary` | Replacement-needed. The answer is counts, not conflict rows. Refutation: endpoint materializes all conflicts for all-time/last-7-days and per-project counts. Closure: use `count()`, existing `countByDetectedAtAfter(...)`, and grouped/project-scoped conflict counts; no full conflict entity list unless a bounded detail view is declared. |
| `QualityMetricsController.java:47` in `getConflictDpmo()` | `JulesSessionRepository` + `TaskRepository` / session-to-project lineage | same endpoint | `data-lineage projection` | Replacement-needed. The map exists only to attribute PR reviews to projects, and currently adds N task reads after full session read. Refutation: session rows unrelated to reviewed PRs are loaded, or task/project lineage is reconstructed by per-session `findById`. Closure: one reliable projection maps reviewed `julesSessionId -> projectId` with source timestamp/freshness and no unrelated sessions. |
| `QualityMetricsController.java:76` in `getConflictDpmo()` | `ProjectRepository` / projects | same endpoint `byProject` section | `bounded operator breakdown` candidate | Not ideal until project-list contract is named. Current runtime shows `byProject=1`, but the endpoint has no pagination/filter parameter and no frontend consumer found. Refutation: project table grows and endpoint still loops all projects with no bound or scope rule. Closure: document `byProject` as all-project operator summary with a measurable project-count bound, or add explicit scope/pagination before code. |
| `QualityMetricsController.java:133` in `getDefectSummary()` | `TaskConflictRepository` / conflicts | REST `GET /api/quality/defect-summary`; no frontend caller found by grep | `summary + unbounded detail list` | Replacement-needed for totals; unresolved for `items`. Refutation: endpoint loads all conflicts even when only `total` is consumed, or returns all conflict items without a named UI/API bound. Closure: count uses aggregate; item list is either paged/top-N/status-scoped or explicitly accepted as a bounded diagnostic. |
| `QualityMetricsController.java:135` in `getDefectSummary()` | `TaskRepository` / quality-gate reports | same endpoint | `report summary + detail extraction` | Replacement-known for acquisition, unresolved for item bound. Refutation: tasks with no `qualityGateReport` are loaded only to skip them. Closure: read only `findByQualityGateReportIsNotNull()` or equivalent projection, then preserve exact check parsing and define whether `qualityGate.items` is bounded/paged/all. |
| `QualityMetricsController.java:154` in `getDefectSummary()` | `OnboardingAuditFindingRepository` / onboarding findings | same endpoint | `summary + unbounded detail list` | Replacement-needed unless declared diagnostic. Refutation: full onboarding finding rows are loaded to answer a count, or all findings are returned without bound. Closure: total uses `count()`/global aggregate; items are paged/project-scoped/top-N or the endpoint is documented as a bounded internal diagnostic with row-count evidence. |

*QualityMetricsController current status:* not ideal. The aggregate parts have clear replacement direction; the detail-list contract is unknown. `docs/reports/AGY_ASKS.md` now carries the required question, because replacing reads without knowing whether callers need all `items` would be another blind code slice.

*Свидетельства реестра:* `nl -ba src/main/java/com/eneik/production/controllers/QualityMetricsController.java | sed -n '1,185p'`; `grep -RIn "conflict-dpmo\|defect-summary\|/api/quality" frontend src/main/java src/test/java --exclude-dir=target | head -160`; focused repository grep for `PrReviewRepository`, `TaskConflictRepository`, `JulesSessionRepository`, `TaskRepository`, `OnboardingAuditFindingRepository`; runtime `curl` for `/api/quality/conflict-dpmo` and `/api/quality/defect-summary` plus Python JSON item counts.

*комментарий для Антигравити:* механизм не идеален. Не правь `QualityMetricsController` до ответа о contract for `defect-summary.items`: whether those lists are internal diagnostic all-rows, paginated UI data, or obsolete. Применимая поправка — separate aggregate truth from bounded detail truth, then encode counts/projections before implementation. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: line-level registry for `SixSigmaAuditService`, без правки кода.* Раскрыта третья hotspot-строка: шесть grep-lines in the Six Sigma mechanism. Важное различие: factory layer legitimately spans all projects, but project/delivery/product callers must not acquire all rows before applying project/feature scope. Runtime today proves the endpoint works but not that reads are bounded: `/api/audit/six-sigma?layer=factory` returned HTTP 200, 702 bytes, `FACTORY_WIDE_ALL_PROJECTS`, opportunities 11312, defects 56, sigma 4.21; default delivery returned HTTP 200, 653 bytes, project `test-fiftieth`, opportunities 2188, defects 5, sigma 4.66.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `SixSigmaAuditService.java:105` in `getActiveProjectId()` | `ProjectRepository` / projects | `SystemAuditController` default `/api/audit/six-sigma`, `ProcessControlService`, `KaizenService` fallback | `active-project resolver` | Replacement-needed. Existing `ProjectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` expresses the active half; `orchestrated` cannot be proven from current `ProjectStatus` enum. Refutation: resolver loads archived/waiting/frozen projects before choosing an active id. Closure: active lookup uses repository predicate and fallback ordering is explicitly documented. |
| `SixSigmaAuditService.java:109` in `getActiveProjectId()` fallback | `ProjectRepository` / projects | same resolver when no active/orchestrated project exists | `fallback project resolver` | Unresolved because fallback semantics are a business contract: first arbitrary DB project may be backward compatibility or a bug. Refutation: no active project exists and the method picks an undetermined project id. Closure: `AGY_ASKS.md` answers whether fallback is allowed, and if allowed, defines deterministic order/status scope. |
| `SixSigmaAuditService.java:242` in `calculateSixSigmaAuditInternal(projectId)` | `OnboardingAuditFindingRepository` / onboarding findings | factory audit, delivery/project audit, `/api/audit/full` | `audit summary/count` | Replacement-known. The code only needs `size()` for defects and `max(size*5, baseline)` for opportunities, not entities. Refutation: project audit materializes all onboarding findings before filtering to `targetProjectId`. Closure: global uses `count()`, project uses existing `countByProjectId(projectId)`, and layer baselines stay identical. |
| `SixSigmaAuditService.java:355` in `computeQualityGateCounts(projectId, null)` | `TaskRepository` / tasks with quality reports | delivery audit, factory audit, `ProcessControlService` callbacks when featureId absent | `report summary` | Replacement-known but split by scope. Global can use existing `findByQualityGateReportIsNotNull()`; project scope needs a project+report predicate or projection. Refutation: tasks with null `qualityGateReport`, or tasks from other projects, are loaded only to be skipped. Closure: report parsing sees the same checks but acquisition is global-report-only or project-report-only. |
| `SixSigmaAuditService.java:394` in `computeCtqBreakdown(projectId)` | `TaskRepository` / quality-gate reports | `SystemStatusService` dashboard and `KaizenService` CTQ targeting | `hot dashboard/kaizen summary` | Replacement-needed. It is the same quality-report corpus as line 355 but now grouped by check name; SystemStatus already calls it on dashboard load. Refutation: dashboard/Kaizen CTQ path loads tasks with no report or outside project scope. Closure: same report-only acquisition as line 355, preserving sort by defect count descending and the no-report empty behavior in tests. |
| `SixSigmaAuditService.java:425` in `computePrConflictCounts(projectId, featureId)` | `PrReviewRepository` + `TaskConflictRepository`, with `JulesSessionRepository` lineage in `taskIdOfReview` | factory/delivery/product sigma, `ProjectTreeService`, `ProcessControlService` | `audit summary with data-lineage projection` | Replacement-needed for default overload. The existing overload accepts pre-read evidence for `ProcessControlService`, but normal callers still load both whole tables and then call per-review session lookup. Refutation: project/feature sigma reads reviews/conflicts outside scope or reconstructs review->session->task lineage one row at a time. Closure: scoped/grouped repository queries or one lineage projection preserve defect/opportunity counts under factory, project and feature scopes. |

*SixSigmaAuditService current status:* not ideal. Known-safe direction exists for onboarding and quality-report acquisition; `getActiveProjectId` fallback/order and `orchestrated` status need an explicit answer; PR/conflict counts need a lineage-preserving aggregate/projection, not a local count-only rewrite.

*Свидетельства реестра:* `nl -ba src/main/java/com/eneik/production/services/audit/SixSigmaAuditService.java | sed -n '90,115p'`; `sed -n '232,248p'`; `sed -n '340,402p'`; `sed -n '423,510p'`; `grep -RIn "calculateFullSixSigmaAudit\|calculateProjectSixSigmaAudit\|computeQualityGateCounts\|computeCtqBreakdown\|computePrConflictCounts\|getActiveProjectId" src/main/java src/test/java --exclude-dir=target | head -180`; `grep -RIn "six-sigma\|SixSigmaAudit" frontend src/main/java src/test/java --exclude-dir=target | head -180`; repository grep for existing count/project/report predicates; runtime `curl` for factory and delivery `/api/audit/six-sigma` plus JSON summary.

*комментарий для Антигравити:* механизм не идеален. Не правь `SixSigmaAuditService.getActiveProjectId()` до ответа: should `orchestrated` still be a valid status, and what deterministic fallback is allowed when no active project exists? For the rest, applicable philosophy says separate layer-wide truth from scoped acquisition and preserve the review->session->task lineage before changing reads. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: 10-такт 10/10 — completion audit cluster, без правки кода.* Десятый такт закрывает именно аудит десятиактного прохода, а не объявляет реализацию идеальной. Текущий воспроизводимый знаменатель: `grep -RIn "\.findAll[[:space:]]*(" src/main/java --exclude-dir=target | wc -l` даёт 76 настоящих `.findAll()` call sites. Сверка по 36 файлам с десятиактной таблицей дала `missing_files []`, `extra_expected_files []`, `mismatch []`; значит, в текущем `.findAll()` denominator не пропущен ни один механизм-файл. Наивный парсер даёт 78 только если считать два комментария в `AutoMergeService` как вызовы, поэтому source denominator остаётся 76.

*Идеальная форма completion-audit mechanism:* каждый дальнейший такт сначала заново строит source inventory, затем сравнивает его с реестром механизмов. Если появляется новый файл/строка, он не считается мелкой задачей: сначала записывается owner, boundary, inputs, outputs, interactions, invariant, refutation and closure. Если новых строк нет, выбирается только тот future implementation candidate, whose ideal replacement, fixture and API/UI contract already have a complete record; code remains forbidden until operator explicitly authorizes it.

*Граница:* этот аудит охватывает backend source `.findAll()` acquisition discipline and adjacent full-vector/full-entity reads already named in records. Он не покрывает frontend, generated/target code, repositories as data containers, migrations outside their mechanism families, or unrelated mechanisms without full-table acquisition evidence. Он также не меняет code/runtime/deployment state.

*Входы:* clean git state at `2922286`; protocol from `/home/remotecli/codex-mechanisms-session/SESSION.md`; `docs/HOW_TO_READ_BEFORE_FIXING.md`; current `docs/ANTIGRAVITY_QUEUE.md`; current source grep inventory; `docs/reports/AGY_NEXT.md`; `docs/reports/AGY_ASKS.md`; philosopher row `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`; common pattern `ACP-061 Hoare Triple Review`.

*Выходы:* ten-tact audit status; explicit missed-mechanism result; existing blockers; candidate order for later authorized implementation; next autonomous tact rule.

*Сверка 76 строк:* pre-ten records cover 27 source calls: `FalsificationCycleService` 7, `QualityMetricsController` 7, `SixSigmaAuditService` 6, `SystemStatusService` 5, `GeminiContextService` 2. The ten connected tacts cover the remaining 49: tact 1 `ProjectOperationalContextService` 4; tact 2 constraint/coherence 7; tact 3 flow/orchestration 8; tact 4 Jules operations 8; tact 5 quality/process-control new calls 3; tact 6 improvement lifecycle 6; tact 7 operational truth/audit/retention 5; tact 8 observer diagnostics 3; tact 9 API edge/account/task/Linear 5. Sum: 27 + 49 = 76, with file-level mismatch check empty.

| Status class | Mechanisms | Audit result |
| --- | --- | --- |
| Covered but blocked by explicit question | `SystemStatusService.tasks(null)` carrier predicate; `FalsificationCycleService` active-role order; `/api/quality/defect-summary` detail-list contract; `SixSigmaAuditService.getActiveProjectId()` fallback/orchestrated semantics | Do not implement until `AGY_ASKS.md` is answered. |
| Covered and plausible after future code authorization | `InternalTaskController.getTaskByLinearId`, `LinearSyncController.getCompletenessReport`, `KaizenService.findOpenSibling/deleteMatching`, project-scoped context reads, active-project sweeps, report-only quality acquisitions | Candidate only because boundary/refutation/closure are named; still requires focused fixture before code. |
| Covered but needs boundary hardening first | mutable `AccountController`/`InternalTaskController` surfaces, unbounded admin/operator lists, observer diagnostics, Jules admin/session listings, project list endpoints | Not a safe implementation candidate until authorization, masking, pagination/scope or internal-diagnostic contract is explicit. |
| Covered and may be allowed rather than replaced | small/static role catalogue if order is answered; deliberate maintenance/cache/sweep reads with cadence and budget; bounded operator lists once their bound is documented | Can be called ideal only after evidence proves owner, freshness, bound and refutation. |

*Инварианты:* (1) no future tact begins from a code edit; (2) a green grep mismatch check is not a permission to change code; (3) a source count has to name the command; (4) missing mechanism means file/line absent from the registry, not a feeling; (5) implementation candidates are ordered by complete mechanism proof, not by how easy the query replacement looks.

*Сильная форма:* the audit itself is now strong for the current 76-call denominator: all current source files with `.findAll()` are mapped to a record cluster, all open unknowns are visible in `AGY_ASKS.md` or the mechanism record, and the next tact rule prevents silent drift.

*Слабая форма:* report "76 covered" while forgetting that code remains not ideal, or start implementing a low-risk-looking query before checking whether a new mechanism appeared. This weak form is explicitly forbidden by the operator's correction.

*Текущий статус:* completion audit complete for documentation; implementation remains not ideal. No `.findAll()` mechanism was missed in the current denominator. The post-10 autonomous mode is now: rerun inventory each tact, repair any missed record first, otherwise choose a safe Codex implementation candidate only as a recommendation unless fresh operator permission allows code.

*Опровержение:* add or expose a `.findAll()` call site that is absent from this audit map; or show a changed source count whose file-level mismatch check is non-empty; or show a later tact editing code before doing inventory and mechanism-boundary check.

*Критерий закрытия:* this ten-tact audit is closed when `git diff --check` passes, `AGY_NEXT.md` records the result, the tact note is written, and the source-vs-expected check still reports 76/76 with no missing, extra, or mismatched files. The full full-table acquisition mechanism becomes ideal only after blocked questions are answered and every remaining full-table read is either justified with evidence or replaced under its mechanism fixture.

*Свидетельства такта:* `git status --short`; `git log -1 --oneline`; `sed -n '1,240p' /home/remotecli/codex-mechanisms-session/SESSION.md`; `sed -n '1,220p' docs/HOW_TO_READ_BEFORE_FIXING.md`; `grep -n "findAll\|10-такт\|FACTORY_MECHANISMS\|mechanism" docs/ANTIGRAVITY_QUEUE.md | head -60`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN\|ACP-061\|RELIABILITY_CHAIN\|D010" ...`; `grep -RIn "\.findAll[[:space:]]*(" src/main/java --exclude-dir=target | wc -l`; per-file awk count; Python expected-vs-actual file-count check returning `actual_total 76`, `expected_total 76`, `file_count 36`, `missing_files []`, `extra_expected_files []`, `mismatch []`; `tail -80 docs/reports/AGY_ASKS.md`; `tail -90 docs/reports/AGY_NEXT.md`.

*комментарий для Антигравити:* механизм в реализации не идеален, но десятиактная документационная сверка текущего `.findAll()` denominator завершена: пропущенных механизмов не найдено. Следующий такт — сначала снова прогнать inventory and mismatch check; если появится пропуск, заполнить его как целый механизм; если пропуска нет, выбрать только такой future code candidate, где already stated exact boundary/refutation/closure, starting with exact lookup/projection candidates like `InternalTaskController.getTaskByLinearId`, `LinearSyncController.getCompletenessReport` or `KaizenService` sibling/delete predicates. Код не править без нового явного разрешения оператора. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 1 — Linear sync report candidate audit, без правки кода.* Post-10 rule выполнено: перед выбором кандидата заново перемерен source denominator. `grep -RIn "\.findAll[[:space:]]*(" src/main/java --exclude-dir=target | wc -l` still returns 76, per-file inventory still matches the 36-file audit map, and no missed `.findAll()` mechanism-file was found. Поэтому этот такт не добавляет новый механизм, а выбирает first future implementation candidate under the existing API-edge/account-task record.

*Кандидат:* `LinearSyncController.getCompletenessReport()` is safer than `InternalTaskController.getTaskByLinearId()` for a later authorized Codex code tact. Reason: report acquisition already has a repository boundary in `TaskRepository.findByLinearIssueIdIsNotNull()`, and metadata batching can use inherited `JpaRepository.findAllById(taskIds)`. The exact lookup endpoint still lacks duplicate Linear-id policy, so changing it first could silently change which task is returned.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism appears, endpoint remains a sync metadata report rather than delivery readiness, and list order is not treated as a semantic contract unless a test says otherwise. Command after explicit code approval only: replace `taskRepository.findAll().stream().filter(nonblank linearIssueId)` with `taskRepository.findByLinearIssueIdIsNotNull().stream().filter(nonblank)`, batch metadata by task ids with `metadataRepository.findAllById(...)`, and keep the same response keys `totalIssues`, `fullyComplete`, `completeness_rate`, `issues`. Postcondition: fixture with tasks having null, blank and nonblank Linear ids plus present/missing metadata returns the same totals/missingFields as before, while non-Linear tasks are not acquired and metadata is not queried once per issue.

*Why this is not a harmful slice:* the candidate sits inside a fully recorded mechanism boundary: API-edge sync completeness. It does not redefine account authority, task lifecycle, Linear duplicate identity, or delivery readiness. It only changes the acquisition process for the same report, and its refutation is already stated: if non-Linear tasks are scanned, or metadata is N+1 while the report claims completeness, the mechanism remains false.

*Not chosen this tact:* `InternalTaskController.getTaskByLinearId` remains blocked by duplicate policy; `GET /internal/tasks` remains an authorization/API-contract problem; account/dashboard endpoints remain rights/bounds/projection problems. Those should not be taken just because they look mechanically easy.

*Свидетельства такта:* current inventory count and per-file mismatch check; `grep -RIn "linear-sync\|completeness-report\|by-linear-id" frontend src/main/java src/test/java scripts docs --exclude-dir=target | head -160`; `nl -ba src/main/java/com/eneik/production/controllers/LinearSyncController.java | sed -n '1,110p'`; `nl -ba src/main/java/com/eneik/production/controllers/InternalTaskController.java | sed -n '145,153p'`; `nl -ba src/main/java/com/eneik/production/repositories/TaskRepository.java | sed -n '16,22p'`; `nl -ba src/main/java/com/eneik/production/repositories/LinearIssueMetadataRepository.java | sed -n '1,20p'`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; следующий безопасный future candidate после явного разрешения на код — `LinearSyncController.getCompletenessReport`: сначала заменить acquisition на Linear-id-scoped task read and batched metadata read, with fixture preserving sync-report totals/missingFields and keeping delivery readiness out of scope. Не трогай `InternalTaskController.byLinearId`, пока не решён duplicate Linear-id policy. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for sync metadata vs delivery readiness; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 2 — Kaizen open-sibling candidate audit, без правки кода.* Post-10 check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found, so this tact evaluates the next candidate inside the already recorded Kaizen/lean/lever/market mechanism.

*Кандидат:* `KaizenService.findOpenSibling(incoming)` is a plausible later Codex implementation candidate; `KaizenService.deleteMatching(...)` is not. The sibling check has a complete equivalence class in source and docs: not the same id, status `PROPOSED`, same category, same target component, same project id including factory-scope null. The destructive cleanup still lacks a project/status boundary and can erase another project's proposal, so it remains off-limits.

*Precondition / command / postcondition for the future code tact:* precondition: inventory still has no missed mechanism, and the test fixture names factory-null, project A/project B, same category/component, APPLIED/STANDARDIZED rows, and same-id exclusion. Command after explicit code approval only: add a repository predicate that asks only for open siblings in that equivalence class and route `findOpenSibling` through it, preserving null project semantics and same-id exclusion. Postcondition: recurrence revises only the intended open sibling, cross-project rows are invisible, non-PROPOSED rows are never revived, and evidence-node writes still attach to the persisted proposal id.

*Why this candidate is safe later:* it follows the mechanism's own identity law instead of changing the proposal lifecycle. It does not alter `allProposals()` UI/admin semantics, autonomous apply cadence, lever promotion, or destructive standardization cleanup. Its refutation is exact: if a recurrence scans all proposals or can select the wrong project/status, the mechanism is still false.

*Not chosen this tact:* `deleteMatching` must wait for project/factory/status scope; `allProposals()` list paths must wait for UI/admin bound decisions; FlowMetrics and MarketResearch candidates are less safe until live-consumer and deterministic carrier-project contracts are settled.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 6/10\|KaizenService.java:78\|KaizenService.java:143\|KaizenService.java:158" docs/FACTORY_MECHANISMS.md`; `nl -ba src/main/java/com/eneik/production/kaizen/service/KaizenService.java | sed -n '75,163p'`; `nl -ba src/main/java/com/eneik/production/kaizen/repository/KaizenProposalRepository.java`; `nl -ba src/test/java/com/eneik/production/kaizen/KaizenServiceTest.java | sed -n '261,304p'`; caller grep for `findOpenSibling`, `deleteMatching`, `allProposals`, and `getDeduplicatedProposals`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; следующий future candidate после явного разрешения на код — `KaizenService.findOpenSibling`: replace global proposal scan with a repository predicate for the exact open-sibling equivalence class, while fixture proves project/factory-null separation, same-id exclusion, PROPOSED-only recurrence and persisted evidence id. Не трогай `deleteMatching`, пока не зафиксирован project/factory/status scope for destructive cleanup. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for factory/project scope; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 3 — active-project sweep candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates the connected active-project acquisition candidate inside the operational truth/audit/retention record.

*Кандидат:* active-project sweeps in `TrustSnapshotService.captureAndBackfillSnapshots`, `OpsAuditorService.runAuditCycle`, and `DeliveryRealityProducerService.produce` are plausible later Codex implementation candidates as one contract: acquire active projects from `ProjectRepository` by status before the loop, then preserve each mechanism's own per-project work. `FlowSpineService.shadowCheckEmbeddingDuplicatesAcrossActiveProjects` is adjacent but should wait until its D3 evidence fixture is explicit, because it couples active acquisition with candidate caps and lever observations.

*Precondition / command / postcondition for the future code tact:* precondition: inventory still has no missed mechanism, and tests include at least two active projects plus frozen/accepted/archived projects. Command after explicit code approval only: use `projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` or an equivalent active-only repository method in the three scheduled sweeps, without changing `LogScope`, exception handling, backfill, evidence-node creation, or readiness predicates. Postcondition: inactive projects are never materialized as live candidates; active projects still produce the same snapshots/audits/delivery findings; `backfillResolvedOutcomes()` still runs once after snapshot capture; audit abstain/action liveness remains visible.

*Why this is not a blind bulk edit:* the shared ideal is not "reduce three lines". It is the operational-memory boundary: only active client projects enter live truth production, while retention/backfill/history rules remain separate. The future code would change only the acquisition process for that already-stated boundary and leave each downstream mechanism's own truth owner intact.

*Not chosen this tact:* `ProjectEventLogRetentionService` is not an active-only candidate because its record says unaccepted history must not be deleted by age; its problem is cadence/bound proof. `FlowSpineService` is deferred until the D3 fixture proves candidate caps, no hot embedding call and lever observation semantics.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 7/10\|TrustSnapshotService.java:58\|FlowSpineService.java:1015\|OpsAuditorService.java:108\|DeliveryRealityProducerService.java:941" docs/FACTORY_MECHANISMS.md`; `nl -ba` contexts for those four source locations; `nl -ba src/main/java/com/eneik/production/repositories/ProjectRepository.java | sed -n '1,45p'`; `nl -ba src/main/java/com/eneik/production/models/persistence/ProjectStatus.java`; test grep for existing `projectRepository.findAll()` and `findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` around the four services; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — active-project acquisition for `TrustSnapshotService`, `OpsAuditorService` and `DeliveryRealityProducerService`: replace all-project materialization with status-scoped active project acquisition while fixture proves inactive projects are invisible, active project order is acceptable/deterministic, `LogScope` and liveness logs stay intact, and retention/backfill rules are not collapsed into the active sweep. Не трогай `ProjectEventLogRetentionService`; не трогай `FlowSpineService` до D3 evidence fixture. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for active/live vs historical/retention levels; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 4 — falsification active-project candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates only the active-project acquisition part of `FalsificationCycleService`; role-catalogue reads stay blocked by the recorded active-role order question.

*Кандидат:* `FalsificationCycleService.runDailyFalsificationCycle`, `advanceInProgressPhilosophicalDiscussions` project loop, and `runWeeklyPhilosophicalFalsificationCycle` are plausible later Codex implementation candidates as one falsification-admission contract. All three ask the same storage question: which projects are active now? `ProjectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` already exists, while the source currently materializes all projects and filters in Java.

*Precondition / command / postcondition for the future code tact:* precondition: inventory still has no missed mechanism, feature flags and role-order blocker are untouched, and fixtures include active plus frozen/waiting/accepted/archived projects. Command after explicit code approval only: replace the three project `findAll().stream().filter(active)` acquisitions with the existing active-project finder, without touching `roleRepository.findAll()`, `continuePhilosophicalDiscussion`, `LogScope`, feature-flag placement, stale-Jules-turn guard, or dispatch synthesis. Postcondition: inactive projects are never acquired as cycle candidates; active project order is deterministic by `createdAt desc`; daily, continuation and two-day philosophical cycles keep their different admission semantics.

*Why this is one mechanism and not three slices:* the shared subject is falsification admission, not query cleanup. Daily formal audit, 15-minute continuation and two-day philosophical start all decide whether the factory may spend falsification attention on a project. The implementation candidate changes only how live project candidates are acquired; it does not alter which philosopher roles speak, when a new discussion starts, or how a live worker advances.

*Not chosen this tact:* `RoleRepository.findAll()` in the same service is not safe until `AGY_ASKS.md` answers canonical BARCAN order versus database priority. Line `1371` active-role count is count-only and should be handled with the role-order/count contract, not bundled into the project acquisition candidate.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "FalsificationCycleService.java:164\|FalsificationCycleService.java:245\|FalsificationCycleService.java:275\|role-catalogue" docs/FACTORY_MECHANISMS.md docs/reports/AGY_ASKS.md`; `nl -ba src/main/java/com/eneik/production/services/FalsificationCycleService.java | sed -n '156,282p'`; `nl -ba src/main/java/com/eneik/production/repositories/ProjectRepository.java | sed -n '15,20p'`; test grep for `runDailyFalsificationCycle`, `advanceInProgressPhilosophicalDiscussions`, `runWeeklyPhilosophicalFalsificationCycle`, `projectRepository.findAll`, and `findByStatusOrderByCreatedAtDesc`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — active-project acquisition in `FalsificationCycleService` lines 164, 245 and 275: use the existing active-project finder while preserving daily/continuation/two-day admission differences, `LogScope`, feature flags, stale-turn guard and dispatch behavior. Не трогай role-catalogue reads or active-role count until the active-role order question in `AGY_ASKS.md` is answered. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 5 — project operational context candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates the selected-project fact-pack candidate under `ProjectOperationalContextService`.

*Кандидат:* all four acquisitions in `ProjectOperationalContextService.build(projectId, fallbackProjectName)` are plausible later Codex implementation candidates as one context-pack contract, not as four query cleanups. Existing repositories already express the needed boundaries: project tasks via `TaskRepository.findByProjectIdOrderByCreatedAtDesc`, sessions via `JulesSessionRepository.findByTaskIdIn`, reviews via `PrReviewRepository.findByJulesSessionIdIn`, conflicts via `TaskConflictRepository.findByTaskIdIn`, and available accounts via `AccountRepository.findAvailableForProjectOrderByNameAsc`.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism, and a fixture has project A/B tasks, sessions, PR reviews, conflicts with task relation, decommissioned accounts, free accounts and current-project accounts. Command after explicit code approval only: replace the four full-table reads with the existing lineage/scoped repository methods, preserving the fact-pack response keys, `updatedAt/createdAt/detectedAt` null-last descending order, case-insensitive account order, and downstream calls to GitHub, EMS metrics and `SystemStatusService`. Postcondition: context for project A never acquires project B sessions/reviews/conflicts/accounts, yet `sessionFacts`, `reviewFacts`, `conflictFacts`, `accountsAvailableForProject` and `julesUniversalRoleCapacity` are semantically identical for visible rows.

*Why this is one mechanism and not a set of slices:* the selected-project context is consumed by AutoMerge, ProjectFlow, DesignShop and Google AI resource paths as one reliability packet. Replacing only sessions, reviews, conflicts or accounts independently would risk changing what the downstream worker believes about the project. The later candidate must preserve the whole `scope=selected_project_only` packet.

*Remaining caution:* account capacity language is still not globally ideal until reconciled with dispatcher locking and account-specific limits. That does not block scoped acquisition, but it blocks calling the entire capacity mechanism ideal.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 1/10\|ProjectOperationalContextService.java:95\|ProjectOperationalContextService.java:101\|ProjectOperationalContextService.java:110\|ProjectOperationalContextService.java:115" docs/FACTORY_MECHANISMS.md`; `nl -ba src/main/java/com/eneik/production/services/dashboard/ProjectOperationalContextService.java | sed -n '70,135p'`; repository reads for `JulesSessionRepository`, `PrReviewRepository`, `TaskConflictRepository`, `AccountRepository`; caller grep for `ProjectOperationalContextService`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — `ProjectOperationalContextService.build` as one selected-project fact-pack: use existing task/session/review/conflict/account scoped acquisitions and a fixture proving project A cannot see project B facts while downstream context keys and ordering remain unchanged. Не правь эти четыре строки раздельно and не объявляй account-capacity идеальной, пока она не reconciled with dispatcher locking/account limits. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for selected-project scope; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 6 — quality-gate scalar candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates the factory-wide quality-gate scalar candidate under the quality/process-control record.

*Кандидат:* `QualityGateController.getDefectRate()` is a plausible later Codex implementation candidate only as part of the shared quality-gate report-corpus contract with `SixSigmaAuditService.computeQualityGateCounts(null, null)`. The endpoint asks for a scalar report: attempts, opportunities, defects, DPMO. It does not need task rows without `qualityGateReport`, and `TaskRepository.findByQualityGateReportIsNotNull()` already expresses the global report corpus.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism, and a fixture has tasks with null reports, reports without `checks`, reports with passed and failed checks, and the Six Sigma owner returns the same defect/opportunity denominator. Command after explicit code approval only: route the controller through the shared quality-gate counting owner or at minimum through the same report-only acquisition, preserving response keys `totalAttempts`, `totalOpportunities`, `defects`, `dpmo`. Postcondition: `/api/quality-gate/defect-rate` no longer materializes reportless tasks, and it cannot disagree with the Six Sigma quality-gate denominator for factory-wide data.

*Why this is one mechanism:* this is not a task-status count and not a dashboard shortcut. It is the quality-gate report corpus: the same JSON checks feed dashboard, Six Sigma, Kaizen CTQ targeting and the public scalar endpoint. A future code tact is safe only if it keeps those levels aligned and does not collapse project, feature or factory scopes.

*Not chosen this tact:* `ProcessControlService.recomputeForProject` and project-scoped `SixSigmaAuditService.computeQualityGateCounts(projectId, null)` are not included because their safe form needs a project/feature evidence packet, not just global report-only acquisition. `QualityMetricsController.defect-summary.items` remains blocked by its detail-list contract question.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 5/10\|QualityGateController.java:25\|ProcessControlService.java:160\|ProcessControlService.java:161" docs/FACTORY_MECHANISMS.md docs/reports/AGY_ASKS.md`; `nl -ba src/main/java/com/eneik/production/controllers/QualityGateController.java | sed -n '1,60p'`; `nl -ba src/main/java/com/eneik/production/services/audit/SixSigmaAuditService.java | sed -n '350,378p'`; repository grep for `findByQualityGateReportIsNotNull`, `countByStatus`, and project/status counts; caller grep for `defect-rate`, `QualityGateController`, and `taskRepository.findAll`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — factory-wide quality-gate scalar: make `QualityGateController.getDefectRate` use the same report-only corpus/owner as `SixSigmaAuditService.computeQualityGateCounts(null, null)`, with fixture proving attempts/opportunities/defects/DPMO parity and no reportless task acquisition. Не трогай project-scoped u-chart/SixSigma paths or `QualityMetricsController.defect-summary.items` until their evidence-packet/detail-list contracts are settled. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for factory/project/feature denominator levels; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 7 — constraint task-evidence candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates only the task-evidence part of the constraint/coherence record.

*Кандидат:* `ConstraintIdentificationService.identifyDrum(projectId)` line 68 and `recommendedBufferCapacity(projectId,zFactor)` line 132 are plausible later Codex implementation candidates as one project task-evidence contract. The drum needs project-local queued and review counts; the buffer recommendation needs project-local done tasks with timestamps for cycle-time variance. Existing `TaskRepository.countByProjectIdAndStatus(...)`, `findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(...)`, and `findByProjectIdOrderByCreatedAtDesc(...)` already provide safe acquisition building blocks.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism, and fixture includes two projects, queued/review/pending_review/done tasks, done rows with missing timestamps, and GitHub budget/session pressure held constant. Command after explicit code approval only: replace the project task `findAll()` acquisitions with project/status-scoped counts and project/status done-task acquisition, while leaving account capacity, active-session lineage and GitHub budget semantics unchanged. Postcondition: drum resource/pressure and buffer capacity match the old behavior for project-visible rows, while project B rows are never acquired for project A.

*Why this is not the whole constraint mechanism:* account-capacity at line 77 and `BottleneckDetectionService` remain coupled to dispatcher eligibility and cannot be fixed as a local list/count change. This candidate covers only the reliable project task evidence needed by drum and buffer math; it does not declare the capacity language ideal.

*Not chosen this tact:* account reads in `ConstraintIdentificationService`/`BottleneckDetectionService` wait for a shared dispatcher-aligned capacity contract. The adjacent active-session N+1 in `identifyDrum` is not a `.findAll()` call but should be handled with the same project-lineage fixture when code is eventually authorized.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 2/10\|ConstraintIdentificationService.java:68\|ConstraintIdentificationService.java:77\|ConstraintIdentificationService.java:132\|BottleneckDetectionService.java:53" docs/FACTORY_MECHANISMS.md`; `nl -ba src/main/java/com/eneik/production/services/toc/ConstraintIdentificationService.java | sed -n '50,145p'`; repository grep for `countByProjectIdAndStatus`, `findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc`, `findByProjectIdOrderByCreatedAtDesc`; test grep for `ConstraintIdentificationServiceTest`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — task-evidence acquisition in `ConstraintIdentificationService`: make drum counts and buffer sample project/status-scoped while fixture proves queued/review pressure and cycle-time buffer are unchanged for the selected project and other projects are invisible. Не трогай account-capacity reads or `BottleneckDetectionService` until dispatcher-aligned capacity semantics are written as one contract. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for project-local task evidence vs factory capacity; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 8 — coherence Kaizen reliability candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact evaluates only the Kaizen-outcome reliability branch of `EvidenceCoherenceService.sourceReliability`.

*Кандидат:* `EvidenceCoherenceService.sourceReliability("KAIZEN_PROPOSAL")` lines 518 and 520 are a plausible later Codex implementation candidate as one reliability-denominator contract. The method needs two counts, `STANDARDIZED` and `REVERTED`, before falling back to pooled coherence history if the sample is below `minReliabilitySamples`. `KaizenProposalRepository.findByStatusIn(...)` already narrows the status set; stronger later form is count-by-status or grouped status counts, but the semantic denominator is already exact.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism, and fixture includes 8 standardized, 2 reverted, proposed/applied noise, and a below-threshold case proving fallback still happens. Command after explicit code approval only: replace the two full proposal scans with one status-scoped acquisition or aggregate for `STANDARDIZED`/`REVERTED`, preserving the exact `standardized / (standardized + reverted)` formula and the `minReliabilitySamples` fallback. Postcondition: proposal rows in other statuses are never acquired for the Kaizen reliability denominator, and source reliability remains 0.8 in the existing fixture.

*Why this candidate is bounded:* it does not touch the more difficult non-Kaizen evidence-node branch at line 529. That branch is currently an explicit invariant exemption because `EvidenceNodeEntity.sourceType()` is derived from exclusive source-id columns, not a stored column; pushing it down without a new projection/window contract would change what reliability means. The candidate is only the outcome-grounded Kaizen branch.

*Not chosen this tact:* `evidenceNodeRepository.findAll()` in the same method remains blocked by the derived-source-type/window problem named in `ScheduledQueryCostInvariantTest`; it should not be hidden inside a quick repository method. Constraint and bottleneck account capacity also remain excluded until dispatcher semantics are unified.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "10-такт 2/10\|EvidenceCoherenceService.java:518\|EvidenceCoherenceService.java:520\|EvidenceCoherenceService.java:529" docs/FACTORY_MECHANISMS.md`; `nl -ba src/main/java/com/eneik/production/services/coherence/EvidenceCoherenceService.java | sed -n '490,545p'`; `nl -ba src/main/java/com/eneik/production/kaizen/repository/KaizenProposalRepository.java | sed -n '1,40p'`; `grep -n "sourceReliability\|STANDARDIZED\|REVERTED\|minReliabilitySamples\|KAIZEN_PROPOSAL\|findAll" src/test/java/com/eneik/production/services/coherence/EvidenceCoherenceServiceTest.java`; `nl -ba src/test/java/com/eneik/production/invariants/ScheduledQueryCostInvariantTest.java | sed -n '50,115p'`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — `EvidenceCoherenceService.sourceReliability("KAIZEN_PROPOSAL")`: replace the two full proposal scans with one status-scoped/aggregate acquisition for `STANDARDIZED` and `REVERTED`, with fixture preserving `minReliabilitySamples`, 0.8 reliability and fallback behavior. Не трогай `evidenceNodeRepository.findAll()` in the non-Kaizen branch until the derived `sourceType()` projection/window contract is explicitly solved. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for Kaizen outcome truth vs pooled coherence history; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 9 — Linear exact-lookup blocker audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact deliberately does not promote `InternalTaskController.getTaskByLinearId` to a safe code candidate, because its exact-lookup semantics are still under-specified.

*Blocker found:* `/internal/tasks/by-linear-id/{linearIssueId}` answers a single-issue lookup used by `scripts/modules/db_utils.py:update_task_status_by_linear_id`, but source evidence does not define duplicate behavior. Schema evidence shows `tasks.linear_issue_id VARCHAR(64)` without a visible unique constraint in migrations; `TaskRepository` has `findByLinearIssueIdIsNotNull()` and `findByProjectIdAndLinearIssueIdIsNotNull(...)`, but no exact finder; current controller uses `findAll().stream().findFirst()`, which hides duplicate rows by repository order.

*Question recorded in `AGY_ASKS.md`:* if multiple tasks have the same nonblank Linear issue id, should the endpoint return `409 conflict`, choose a deterministic row by status/project/createdAt, or should `tasks.linear_issue_id` be unique at the database level? Until this is answered, a repository exact lookup would only make the scan cheaper while preserving or hardening an unknown lie.

*Candidate status:* not safe for Codex implementation yet. The previously selected `LinearSyncController.getCompletenessReport` remains the safe Linear candidate because it is a report over all Linear-linked tasks and can tolerate multiple rows as rows; exact lookup cannot.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "InternalTaskController.java:148\|LinearSyncController.java:31\|duplicate Linear-id\|by-linear-id" docs/FACTORY_MECHANISMS.md docs/reports/AGY_ASKS.md docs/reports/AGY_NEXT.md`; `nl -ba src/main/java/com/eneik/production/controllers/InternalTaskController.java | sed -n '140,154p'`; `nl -ba src/main/java/com/eneik/production/repositories/TaskRepository.java | sed -n '16,22p'`; `grep -RIn "linear_issue_id\|linearIssueId\|by-linear-id" src/main/java src/main/resources/db/migration scripts docs --exclude-dir=target | head -220`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `InternalTaskController.getTaskByLinearId` нельзя править до ответа в `AGY_ASKS.md`: duplicate Linear issue id must become either explicit `409`, deterministic domain selection, or database uniqueness. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for Linear issue id vs task row identity; common background `ACP-061 Hoare Triple Review`.

*Живое, 9 сентября 2026, Codex: post-10 tact 10 — FlowMetrics live-consumer blocker audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact audits `FlowMetricsService.computeForProject` as a candidate and rejects implementation until its live consumer contract is answered.

*Blocker found:* source grep shows `FlowMetricsService` has no production injection/caller: in `src/main/java` only the class itself appears; exact `flowMetricsService.computeForProject` calls are absent and only `FlowMetricsServiceTest` instantiates it. The method's math is meaningful, and project-scoped acquisition is technically available, but changing its query would optimize an inert instrument. The ideal mechanism says Little's Law inconsistency must be recorded or exposed when WIP/throughput/cycle-time disagree; today that closure is not wired.

*Question recorded in `AGY_ASKS.md`:* should `FlowMetricsService.computeForProject` become a live signal, and if so who consumes it: dashboard/system-status, Kaizen proposal writer, ProcessControl/operational truth, or a scheduled evidence writer? Or is it a retired diagnostic that should stay documented but not implemented? Until this is answered, Codex should not spend a code tact on its `.findAll()`.

*Candidate status:* not safe for implementation yet. A future code candidate exists only after a live consumer is named; then the acquisition can become project-scoped (`TaskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` or status-specific counts plus `JulesSessionRepository.findByTaskIdIn`) with the existing FlowMetrics fixture preserving WIP, throughput, cycle time, lead time, Little's Law deviation and waste ratio.

*Свидетельства такта:* current 76/36 inventory mismatch check; `grep -n "FlowMetricsService.java:72\|FlowMetricsService has no caller\|dead measurement" docs/FACTORY_MECHANISMS.md docs/reports/AGY_NEXT.md docs/reports/AGY_ASKS.md`; `nl -ba src/main/java/com/eneik/production/services/lean/FlowMetricsService.java | sed -n '1,150p'`; `nl -ba src/test/java/com/eneik/production/services/lean/FlowMetricsServiceTest.java | sed -n '1,160p'`; exact main-source usage grep for `FlowMetricsService`; repository grep for project task/session/wishlist acquisition methods; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `FlowMetricsService.computeForProject` нельзя править как query cleanup: first answer in `AGY_ASKS.md` who consumes Little's Law inconsistency or whether this service is retired diagnostic. После ответа future implementation may project-scope task/session acquisition and preserve the existing flow-math fixture. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for measured flow signal vs inert diagnostic; common background `ACP-061 Hoare Triple Review`.


*Живое, 9 сентября 2026, Codex: post-10 tact 11 — LeverPromotion key-registry blocker audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact audits `LeverPromotionService.evaluatePromotions` and rejects implementation until the lever-key registry/cardinality contract is explicit.

*Blocker found:* `LeverPromotionService.evaluatePromotions` uses `stateRepository.findAll()` at line 105, then evaluates every state row and fetches recent observations per `leverKey`. The surrounding mechanism accepts arbitrary `String leverKey` in `recordObservation(...)` and `currentStage(...)`; code grep finds named callers for `F1_KAIZEN_CTQ_TARGETING`, `F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY`, `P1_ROLE_DRIFT_EWMA`, `D3_EMBEDDING_DUPLICATE_DETECTION` and `T1_TOC_SUBORDINATION`, but no canonical registry source that says these are the complete finite set. Migration `V88__lever_promotion_ladder.sql` also defines `lever_key` as the rigid identifier, not as a foreign key to a declared registry. Therefore Codex cannot prove whether this scheduled full-table read is intentionally bounded or silently accumulates typo/dead lever state.

*Question recorded in `AGY_ASKS.md`:* is `lever_promotion_state` bounded by a declared finite lever-key registry, and if so which source owns that registry and its freshness? Or can arbitrary runtime keys grow over time, requiring `evaluatePromotions()` to read only active/recent observation-bearing states and separately quarantine stale/unknown keys?

*Candidate status:* not safe for implementation yet. If the answer is finite curated registry, the ideal mechanism may keep a bounded sweep but must enforce/measure the registry and expose unknown keys. If the answer is runtime-growth, future code should add repository acquisition for active/recent observation-bearing state rows, preserve immediate demotion in `recordObservation`, preserve one-stage promotion, and prove `observe_only`, `warn_only`, stale-row and unknown-key behavior in fixtures.

*Связи:* this blocker belongs to the same improvement-signal lifecycle as `KaizenService`, `FlowMetricsService`, `MarketResearchService`, `SixSigmaAuditService`, `AccountHealthService`, `FlowSpineService` and `TocSubordinationLever`: all of them may write or consult lever evidence, but only `LeverPromotionService` is allowed to revise the stage. The boundary is not table size alone; it is whether stage truth is keyed by a closed vocabulary or by open runtime strings.

*Свидетельства такта:* current 76/36 inventory mismatch check; `nl -ba src/main/java/com/eneik/production/services/lever/LeverPromotionService.java | sed -n '50,141p'`; grep for `recordObservation`, `currentStage` and known lever constants; `nl -ba src/main/java/com/eneik/production/repositories/LeverPromotionStateRepository.java`; `nl -ba src/main/java/com/eneik/production/repositories/LeverObservationRepository.java`; `nl -ba src/main/resources/db/migration/V88__lever_promotion_ladder.sql | sed -n '1,36p'`; `nl -ba src/test/java/com/eneik/production/services/lever/LeverPromotionServiceTest.java | sed -n '1,141p'`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `LeverPromotionService.evaluatePromotions` нельзя править как query cleanup до ответа в `AGY_ASKS.md`: сначала определить, является ли `lever_key` закрытым реестром рычагов или открытым runtime-ключом. Если закрытый — закрепи владельца реестра и проверку неизвестных ключей; если открытый — читай только active/recent observation-bearing state rows and quarantine stale/unknown keys. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for lever-key vocabulary vs promotion-state row identity; common background `ACP-061 Hoare Triple Review`.



*Живое, 9 сентября 2026, Codex: post-10 tact 12 — MarketResearch carrier-policy blocker audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact audits `MarketResearchService.createResearchTask` as the remaining improvement-signal carrier acquisition and rejects implementation until the ORCHESTRATOR_SYSTEM carrier policy is explicit.

*Blocker found:* `MarketResearchService.createResearchTask(profileId, market, sampleSize)` correctly keeps market research inside normal task/session accounting: it requires role `BARCAN-TAG-09`, clamps sample size to 5..40, sets `TargetContext.ORCHESTRATOR_SYSTEM`, initializes `queued`, and leaves dispatch to the ordinary queue. The unsafe part is only carrier acquisition: line 69 uses `projectRepository.findAll().stream().findFirst()` while the comment says the most recent project is picked. `findAll()` has no status, no order, and no factory-carrier identity; therefore an archived, accepted, stale or arbitrary row can carry a factory-owned research task.

*Question recorded in `AGY_ASKS.md`:* what is the deterministic carrier source for `ORCHESTRATOR_SYSTEM` market research tasks: configured factory project, newest active project, a dedicated carrier project, or explicit refusal when no suitable project exists? The answer must also state fallback order and whether archived/accepted projects may ever carry such tasks.

*Candidate status:* not safe for implementation yet. After the carrier policy is answered, this is a compact future Codex candidate: replace the full-table acquisition with the declared deterministic repository method, keep `TargetContext.ORCHESTRATOR_SYSTEM`, keep role `BARCAN-TAG-09`, preserve 5..40 sample bounds and queued-task accounting, and add a fixture with active/archived/accepted projects proving the chosen carrier and no client repository dispatch.

*Связи:* this is not a project-selection convenience; it connects market corpus truth to Jules dispatch. `MarketResearchController` is operator-facing and returns only the created task id; `JulesDispatchService` uses `TargetContext.ORCHESTRATOR_SYSTEM` to dispatch against the factory repository rather than `task.project`; `SystemSettingsService` owns the `system_orchestrator_repository_name` setting. So the project row is UI/accounting carrier only, while repo target comes from settings and target context.

*Свидетельства такта:* current 76/36 inventory mismatch check; `nl -ba src/main/java/com/eneik/production/services/market/MarketResearchService.java | sed -n '49,87p'`; `nl -ba src/main/java/com/eneik/production/controllers/market/MarketResearchController.java | sed -n '47,57p'`; `nl -ba src/main/java/com/eneik/production/repositories/ProjectRepository.java | sed -n '14,19p'`; `nl -ba src/main/java/com/eneik/production/services/jules/JulesDispatchService.java | sed -n '595,606p'`; `nl -ba src/main/java/com/eneik/production/services/settings/SystemSettingsService.java | sed -n '353,361p'`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `MarketResearchService.createResearchTask` нельзя править как query cleanup: сначала ответь, какой проект является deterministic carrier for `ORCHESTRATOR_SYSTEM` market research tasks and what fallback is legal. После ответа future implementation should replace `findAll().stream().findFirst()` with that policy while preserving normal queued dispatch, `BARCAN-TAG-09`, sample bounds 5..40, and factory-repo target resolution. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for carrier project identity vs factory repository target; common background `ACP-061 Hoare Triple Review`.



*Живое, 9 сентября 2026, Codex: post-10 tact 13 — StrandedFinalizing active-project candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact audits the first project-flow/orchestration candidate that was already marked safe: `StrandedFinalizingSweepService.sweep()`.

*Кандидат:* `StrandedFinalizingSweepService.sweep()` is safe for later Codex implementation after explicit code approval. The current outer acquisition at lines 101-103 loads all projects and filters active in memory; `ProjectRepository` already exposes `findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`, exactly matching the intended population. The candidate changes only acquisition, not release semantics.

*Precondition / command / postcondition for the future code tact:* precondition: inventory still has no missed mechanism and `StrandedFinalizingSweepServiceTest` protects old stranded release, within-lease no-op, active/archived project separation and CAS release. Command after explicit code approval only: replace the outer `projectRepository.findAll().stream().filter(p -> p.getStatus() == ProjectStatus.active).toList()` with `projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` and update the sweep test to assert the repository predicate. Postcondition: archived/frozen/accepted projects are never materialized for the sweep, active projects are still swept, `LogScope.project`, self-proxy transaction, max-age cutoff and `compareAndSetStatus(finalizing,pending)` behavior remain unchanged.

*Why this candidate is bounded:* the destructive act is not being changed. `sweepProject(project)` already uses `wishlistRepository.findByProjectIdAndStatus(project.getId(), WishlistStatus.finalizing)`, checks `lastCompileDispatchedAt`/`createdAt` against the cutoff, and releases only through CAS. Therefore the proof obligation is narrow and checkable: active-project acquisition must move from in-memory filtering to the repository without changing the state machine.

*Not chosen this tact:* `ContinuousOrchestrationService.checkForSystemStall` remains blocked by dispatcher-aligned account-capacity semantics. `BranchGarbageCollectorService` remains destructive-path sensitive because PR/session-token lineage must be project-bound. `AutoMergeService.belongsToActiveProject` still needs fixture protection around PR truth and active-project identity.

*Свидетельства такта:* current 76/36 inventory mismatch check; `nl -ba src/main/java/com/eneik/production/services/StrandedFinalizingSweepService.java | sed -n '99,141p'`; `nl -ba src/test/java/com/eneik/production/services/StrandedFinalizingSweepServiceTest.java | sed -n '38,104p'`; `grep -n "findByStatusOrderByCreatedAtDesc" src/main/java/com/eneik/production/repositories/ProjectRepository.java`; `grep -n "compareAndSetStatus\|findByProjectIdAndStatus" src/main/java/com/eneik/production/repositories/WishlistRepository.java`; project-flow/orchestration record lines for `StrandedFinalizingSweepService.java:101`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален, но это safe future candidate после явного разрешения на код: in `StrandedFinalizingSweepService.sweep()`, replace all-project acquisition plus in-memory active filter with `ProjectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`. Do not touch `sweepProject`, CAS release, max-age, LogScope or self-proxy transaction. Fixture must prove active projects are swept, archived/frozen/accepted projects are not materialized, and finalizing-to-pending release remains CAS-bound. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for project status vs wishlist claim state; common background `ACP-061 Hoare Triple Review`.



*Живое, 9 сентября 2026, Codex: post-10 tact 14 — ProjectFlow bad-session candidate audit, без правки кода.* Post-10 inventory check выполнен: current source inventory remains 76 real `.findAll()` calls across 36 Java files, with no missing/extra/mismatched file against the completion-audit map. No missed mechanism was found. This tact audits the second safe project-flow/orchestration candidate from the 10-tact record: `ProjectFlowService.selectBadSession`.

*Кандидат:* `ProjectFlowService.selectBadSession(tasksById, sessionId)` is safe for later Codex implementation after explicit code approval. `closeBadJulesSession(projectId, sessionId, reason)` already builds `tasksById` from `taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())`, so the project boundary exists before session selection. When the operator passes a concrete `sessionId`, the code already uses `julesSessionRepository.findById(sessionId)` and checks membership in `tasksById`. Only the automatic path at lines 1755-1761 still loads every Jules session before filtering to project tasks and active statuses.

*Precondition / command / postcondition for the future code tact:* precondition: no new missed mechanism, and a focused fixture exists for `closeBadJulesSession` or a package-visible selector helper covering one project task, a foreign-project active session, inactive session statuses, explicit-session lookup, empty project tasks, and the existing risk ordering by status/update/activity count. Command after explicit code approval only: for `sessionId == null`, return empty when `tasksById` is empty, otherwise acquire sessions via `julesSessionRepository.findByTaskIdIn(new ArrayList<>(tasksById.keySet()))` before applying the same `isActiveJulesSession`, `badSessionRisk` sorting and `findFirst`. Postcondition: sessions from other projects are never materialized for bad-session closure, explicit session id behavior is unchanged, active statuses remain exactly `queued/running/revising`, and the closure still writes the same follow-up/reason semantics.

*Why this candidate is bounded:* the mechanism is already project-scoped at the task layer and the repository already has the needed method. This is not a destructive GitHub or dispatch path; it chooses which active Jules session may be closed as bad for one operator-selected project. The change only moves the same task-membership filter into acquisition and leaves closure policy, risk ranking and activity-count evidence intact.

*Not chosen this tact:* `ProjectFlowService.highestMergedPrNumber` remains excluded because coverage-audit watermark semantics depend on PR/task/system-record classification. The `wishlistRepository.findAllById(...)` lines are not `.findAll()` scans and belong to batch identity; they should not be counted as full-table mechanism debt. `ContinuousOrchestrationService.checkForSystemStall` remains blocked by dispatcher-aligned account-capacity semantics.

*Свидетельства такта:* current 76/36 inventory mismatch check; `nl -ba src/main/java/com/eneik/production/services/ProjectFlowService.java | sed -n '1626,1762p'`; `grep -n "findByTaskIdIn" src/main/java/com/eneik/production/repositories/JulesSessionRepository.java`; grep for `closeBadJulesSession` tests showing no existing focused fixture; project-flow/orchestration record lines for `ProjectFlowService.java:1755`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061`.

*комментарий для Антигравити:* механизм в реализации не идеален, но это safe future candidate после явного разрешения на код: in `ProjectFlowService.selectBadSession`, replace all-session acquisition for the automatic path with `JulesSessionRepository.findByTaskIdIn(tasksById.keySet())`, preserving explicit `sessionId` lookup, active statuses `queued/running/revising`, risk ordering, activity-count evidence and bad-session closure side effects. Fixture must prove foreign-project sessions are not materialized and same-project highest-risk active session is still selected. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for project task identity vs Jules session identity; common background `ACP-061 Hoare Triple Review`.



*Живое, 9 сентября 2026, Codex: factory-wide denominator tact 1 — scope correction and denominator v1, без правки кода.* Operator correction accepted: the target is all factory mechanisms, not the narrower full-table-acquisition layer. This tact creates `docs/reports/FACTORY_MECHANISM_DENOMINATOR.md` as the new audit map and demotes the earlier technical inventory to a symptom, not a completion criterion.

*Что доказано сейчас:* rough top-level source scan found service/controller/kaizen/toc/config layers and separated `recorded`, `mentioned only`, and `missing name`. Snapshot: services 139 files = 126 rough records, 5 mentioned-only, 8 missing-name candidates; controllers 35 = 12 rough records, 23 mentioned-only; kaizen 8 = 5 rough records, 3 mentioned-only; toc 10 = 8 rough records, 2 mentioned-only; config 3 = 3 rough records. Scheduled Java files are 28/28 recorded by rough name, but this does not prove ideal records.

*Что ещё не доказано:* rough record presence does not mean ideal mechanism record. Controllers are command surfaces until classified; result carriers/exceptions may be mechanism parts rather than mechanisms; 137 SQL migrations still need behavioral-family classification; sidecars/scripts need a fresh filesystem pass. Therefore this is denominator v1, not completion.

*Критерий закрытия широкого аудита:* every behavior-changing unit must be classified as whole mechanism, connected mechanism-family member, mechanism part, excluded data/result type, dead/inert but operationally relevant, or migration family. Every whole mechanism/family then needs ideal form, boundary, interactions, invariants, strong/weak form, refutation, closure, evidence and Antigravity advice.

*Свидетельства такта:* `git status --short`; `git log -1 --oneline`; protocol/philosophy greps; Python source scan over top-level Java files; scheduled-file scan; migration count under `src/main/resources/db/migration`; new report `docs/reports/FACTORY_MECHANISM_DENOMINATOR.md`.

*комментарий для Антигравити:* механизм-документация пока не идеальна. Не используй technical acquisition inventory as proof that all mechanisms are described. Next tact must classify mentioned-only and missing-name candidates as mechanism / mechanism part / excluded data type / dead but operationally relevant, then start filling whole records with ideal form. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for mechanism identity vs source-file/class identity; common background `ACP-061 Hoare Triple Review`.



*Живое, 10 сентября 2026, Codex: factory-wide denominator tact 2 — candidate classification v2, без правки кода.* This tact continues the scope correction: it classifies the v1 mentioned-only and missing-name candidates, without pretending the factory is closed.

*Что доказано сейчас:* the 8 missing-name service candidates are mechanism parts, not standalone whole mechanisms: `OrchestrationCooldownException` belongs to orchestration cooldown semantics; `GateResult` belongs to gate closure; `JulesDispatchResult` belongs to dispatch accounting; `CollaboratorProvisioningResult`, `LinearProvisioningResult`, `ProjectFactoryResult`, `WorkspaceArtifacts` and `WorkspaceProvisioningResult` belong to the project-factory provisioning family. Mentioned-only services classify as: `ChessService` excluded-with-reason because the file is empty; `GeminiProjectObserverService` dead/inert but operationally relevant; `GateCheck`/`GateStage` gate-family parts; `GitHubProvisioningResult` project-factory part.

*Что стало обязательным дальше:* all 23 mentioned-only controllers remain mechanism-surface work. They should be filled by connected families: public/basic ingress; dashboards/projections; operational command surfaces; settings/configuration; runtime self-reporting. Kaizen repositories are data owners inside Kaizen lifecycle, while `TocExecutionGraph` is stateful TOC graph behavior and must be recorded with TOC runtime, not excluded as data.

*Strict-record quality check:* rough record presence is still not enough. Current scan: 166 rough record lines in `FACTORY_MECHANISMS.md` and only 33 Antigravity-comment occurrences. This is not a remaining count, but it proves strict ideal-form completion is false.

*Свидетельства такта:* `sed -n '1,220p' docs/reports/FACTORY_MECHANISM_DENOMINATOR.md`; Python top-level source classifier; source summaries for mentioned-only/missing candidates; grep for result-carrier callers; `grep -ci "комментарий для Антигравити" docs/FACTORY_MECHANISMS.md`; rough-record scanner; protocol/philosophy rows.

*комментарий для Антигравити:* механизм-документация пока не идеальна. Do not count result carriers as standalone mechanisms, and do not exclude controller surfaces just because they are “only endpoints”. Next tact should fill one connected whole family, recommended operational command/controller surfaces, with ideal form, boundaries, refutation and closure. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for mechanism identity vs class/endpoint identity; common background `ACP-061 Hoare Triple Review`.



*Живое, 10 сентября 2026, Codex: factory-wide tact 3 — settings/configuration surfaces, без правки кода.* This tact fills one connected controller-surface family instead of another technical symptom: `SettingsController`, `InternalSettingsController`, `JulesConfigController`, `SystemSettingsService`, `system_settings`, and the legacy `jules_configs` world.

*Идеальная форма механизма:* configuration is a single institutional fact register. A setting becomes real only through a known key definition, validated value, source precedence, durable timestamp, actor/source audit, and a reader-visible source label. Public/operator views may expose only masked secrets; raw secret resolution exists only for trusted local/internal machinery with an explicit boundary. Jules account configuration has one canonical world: either modern `accounts` or legacy `jules_configs`, never both as live writers.

*Граница, входы и выходы:* `SettingsController` exposes `GET/PUT /api/settings`: input is `SettingUpdateRequest(key,value)`, output is `SettingDto(key, enabled, maskedValue, source)`. `InternalSettingsController` exposes `POST /internal/settings/resolve`: input is a key, output is raw value plus source for local scripts/mechanisms. `JulesConfigController` exposes `GET/POST/PUT/DELETE /api/jules-configs`: input is an untyped payload with `name`, `apiKey`, `enabled`, output is `JulesConfigDto` with masked key. `SystemSettingsService` owns definitions and source precedence; `JulesConfigRepository`/`JulesConfigEntity` still own the old Jules config table path.

*Владельцы данных и взаимодействия:* `system_settings` is read by `effectiveValue`, `effectiveBoolean`, `effectiveInt` and `effectiveDouble` across GitHub, Linear, Jules, Gemini, design, verdict, falsification, market and runtime mechanisms. Database nonblank values outrank environment/properties; otherwise source is `env` or `none`. `JulesConfigController` writes `jules_configs`, while the active dispatch/account pool has moved to `accounts`; migrations `V17` and `V19` show this split by creating then dropping/migrating the old table. Therefore this family controls both capability switches and the danger of two account-configuration worlds.

*Инварианты:* (1) unknown keys are refused at the boundary; (2) boolean flags may not be stored blank or unrecognised; (3) secrets are masked in normal API responses and DTO `toString`; (4) `linear_team_id` must be a UUID, not a display name; (5) `source` must say `database`, `env` or `none`; (6) a debug or raw-secret path cannot be enabled by the same unauthenticated surface it is meant to guard; (7) legacy Jules config writes must either be impossible, bridged into the canonical account pool, or visibly declared inert; (8) setting changes must be auditable by actor/source/old/new value class, not only `updated_at`.

*Сильная форма:* settings are decisions with provenance. The registry defines which strings count as settings; validation preserves identifier meaning; masked DTOs preserve confidentiality; raw resolution has a named trust boundary; every mutation leaves a durable audit witness; the Jules account world has one canonical state owner. In that form a feature cannot turn off because a value was blank, a secret cannot leak through a normal read, and a stale controller cannot create a second dispatch reality.

*Слабая форма сегодня:* the known-key registry, masking, blank-flag refusal and `linear_team_id` UUID guard are real and tested. But the command boundary is still weak: the earlier controller audit found no authentication or call journal; `PUT /api/settings` can change the same registry that gates `POST /api/system-status/sql`; `InternalSettingsController` returns raw secret values; `SystemSettingsService.save` records only `updated_at`; and `JulesConfigController` still writes the legacy `jules_configs` world after V19 moved dispatch truth into `accounts`.

*Опровержение:* the mechanism is false if an unknown setting is stored, a secret appears unmasked in `GET /api/settings`, a blank boolean stores successfully, a non-UUID `linear_team_id` stores successfully, a raw secret is reachable outside the trusted internal boundary, `debug_sql_endpoint_enabled` can be opened by the same unauthenticated API surface it guards, a Jules config POST changes no dispatch behavior while appearing authoritative, or a setting change cannot name who/what changed it.

*Критерий закрытия:* strict closure requires tests or live evidence for: unknown-key refusal, masking, blank-flag refusal, Linear UUID refusal, source precedence, audited setting mutation, raw resolve boundary, debug SQL independent guard, and one explicit fate for `JulesConfigController` (`retired`, `accounts` bridge, or documented inert legacy admin surface). Until then the family is described but not ideal.

*Текущий статус:* mechanism record filled; implementation not ideal. `SettingsController`/`InternalSettingsController`/`JulesConfigController` are no longer only mentioned in the denominator. `SystemSettingsService` remains partially strong as a registry/validator and weak as an unaudited mutation register; `JulesConfigController` remains blocked by old-world/new-world policy.

*Вопрос записан в `AGY_ASKS.md`:* should `/api/jules-configs` be retired, bridged into `accounts`, or kept as an explicitly inert legacy surface? This must be decided before any code tact touches it.

*Свидетельства такта:* `nl -ba` for `SettingsController`, `InternalSettingsController`, `JulesConfigController`, `SystemSettingsService`, `JulesConfigRepository`, `JulesConfigEntity`; `SettingsControllerIntegrationTest`; `SystemSettingsServiceTest`; migration grep for `V16`, `V17`, `V19`; denominator v2 classification; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, plus prior local patterns `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER`, `PRINCIPLED_INTEGRITY`, and `WORLD_VERSION_MAP` already used by the existing records.

*комментарий для Антигравити:* механизм-документация этой семьи теперь заполнена, но реализация не идеальна. Не правь `SettingsController`, `InternalSettingsController` or `JulesConfigController` as isolated endpoint cleanup. First decide the legacy `/api/jules-configs` fate in `AGY_ASKS.md`, then enforce one canonical configuration world, actor/source audit for setting writes, independent guard for raw SQL/debug paths, and trusted-boundary rule for raw secret resolution. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; local governing patterns: `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER`, `PRINCIPLED_INTEGRITY`, `WORLD_VERSION_MAP`; common background `ACP-061 Hoare Triple Review`.



*Живое, 10 сентября 2026, Codex: factory-wide tact 4 — flow observability and TOC telemetry surfaces, без правки кода.*

**`FlowSpineController`**, **`OperationalFlowCoreController`**, **`TocSentinelController`**, **`TocExecutionGraph`** — командно-наблюдательная поверхность потока: оператор или внутренний клиент может прочитать состояние, записать наблюдение потока, запросить журнал событий, провести TOC-token через шаг/ресурс и увидеть граф исполнения.
*Связи:* `FlowSpineController` зовёт `FlowSpineService`; `OperationalFlowCoreController` зовёт `OperationalFlowCoreService`, который строится поверх `FlowSpineService`; `TocSentinelController` зовёт `TocSentinelService`; `TocExecutionGraph` хранит nodes/tokens/edges and is used by `TocSentinelService`, `TocOptimizer`, `TocAnomalyDetector`. Data owners include `FlowSpineEventRepository`, project/task/wishlist/session/review owners behind `FlowSpineService`, and in-memory TOC graph maps.

*Идеальная форма механизма:* observation surfaces must distinguish read, observe, and control telemetry. A read endpoint can report only what the owner service can prove. An observe endpoint may write exactly one durable event for the current project decision/state and must be idempotent by evidence/decision hash. A TOC event endpoint may mutate only the in-memory execution graph/token/resource state and must expose admitted/throttled/not-found/deadlock outcomes without pretending it changed project truth. Every endpoint has bounded output, project/token scope, owner service, freshness source and audit/refutation path.

*Граница, входы и выходы:* `/api/projects/{projectId}/flow-spine` returns `FlowSpineDto`; `/observe` records a flow-spine event only through `FlowSpineService.observe(projectId)`; `/events?limit=N` returns at most 1..500 flow events. `/api/projects/{projectId}/flow-core` returns `FlowCoreDto`; `/observe` records a mode `flow_core_enforced` decision event only when current decision is not already recorded; `/events` is bounded 1..500. `/api/toc/status`, `/constraint`, `/graph`, `/anomalies` read TOC status; `/event/enter`, `/event/exit`, `/resource/acquire`, `/resource/wait`, `/resource/release` mutate only TOC token/resource telemetry.

*Владельцы данных и взаимодействия:* `FlowSpineService.build` owns deterministic project-flow state and uses project-scoped task/wishlist/session/review/readiness/system-status sources. `OperationalFlowCoreService` owns the enforceable decision overlay and writes `FlowSpineEventEntity` with mode `flow_core_enforced`. `TocSentinelService` owns admission/throttle, step enter/exit, resource wait/acquire/release and the two-second watchdog; `TocExecutionGraph` owns active tokens, nodes, transitions, arrival rate and reset semantics. Controllers do not decide state themselves; they are doors into those owners.

*Инварианты:* (1) controller methods stay thin and do not reimplement flow/TOC decisions; (2) read endpoints do not write; (3) observe endpoints write only through owner services and remain idempotent by current evidence/decision hash; (4) event lists are bounded to 1..500; (5) flow-core events are separated by mode from ordinary flow-spine events; (6) TOC throttling returns `429` and does not register a token; (7) missing TOC tokens return visible `NOT_FOUND` or false/OK telemetry, not project-state mutation; (8) graph collections are unmodifiable views, while graph mutation goes through named methods.

*Сильная форма:* the surface is a reliable observation chain: project id or token id enters at the controller, the owner service computes or records exactly one level of truth, persistent events carry project/mode/evidence hash, and TOC telemetry stays in its in-memory runtime graph. In this form an operator can tell whether a value is a live read, a recorded observation, or a control-telemetry event, and can falsify each separately.

*Слабая форма сегодня:* `FlowSpineController` and `OperationalFlowCoreController` are structurally close to ideal: bounded event reads, owner-service delegation and mode separation exist. But the earlier controller audit still applies: no authentication/call journal is proven. TOC controller is weaker: it mutates runtime token/resource state by unauthenticated HTTP calls, `/event/exit` does not end/unregister execution, `acquire/release` return `OK` even when token is missing, and the graph is in-memory, so restart erases evidence while the endpoint name may look like durable truth.

*Опровержение:* the mechanism is false if `GET` writes an event, if `/observe` writes duplicate identical flow decisions, if limit can exceed 500 or drop below 1, if flow-core and flow-spine events mix modes, if a throttled TOC token appears in `activeTokens`, if unknown-token TOC calls look successful in a way downstream treats as evidence, or if any controller starts computing decisions instead of delegating to the owner service.

*Критерий закрытия:* strict closure requires tests or live evidence for bounded events, observe idempotency, mode separation, no read writes, 429 throttling without token registration, unknown-token semantics, graph reset/restart non-durability being documented to operators, and an authentication/audit boundary for mutation endpoints. Until then the family is documented but not ideal.

*Текущий статус:* mechanism record filled; implementation not ideal. `FlowSpineController`, `OperationalFlowCoreController`, `TocSentinelController` and `TocExecutionGraph` move from mentioned-only denominator work into a whole-family record. The safest later implementation candidate is not a controller rewrite; it is to add/verify tests for observe idempotency and TOC unknown-token/throttle semantics before any behavioral change.

*Свидетельства такта:* denominator scan after tact 3; `nl -ba` for `FlowSpineController`, `OperationalFlowCoreController`, `TocSentinelController`, `TocExecutionGraph`, `TocSentinelService`; `grep` for `FlowSpineService.build/observe/events`; `grep` for `OperationalFlowCoreService.build/observe/events`; `FlowSpineEventRepository` bounded event methods; TOC controller/service tests grep; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and common `ACP-061`.

*комментарий для Антигравити:* механизм-документация этой семьи теперь заполнена, но реализация не идеальна. Do not patch these controllers as isolated endpoint cleanup. First preserve the whole observation/control distinction: read endpoints do not write, observe endpoints write one idempotent event through owner services, flow-core mode stays separate, and TOC HTTP events mutate only runtime telemetry with explicit throttle/not-found semantics. Applicable philosophy: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for live read vs durable observation vs in-memory telemetry; common background `ACP-061 Hoare Triple Review`.


# XXVI. Служба контекста: чем определяется, что исполнитель прочтёт

**`GeminiContextService`** (740 строк) — выдаёт роли её устав и подходящие образцы, отбирая из выборки
знаний только близкое к запросу, вместо того чтобы слать целиком.
*Связи:* зовут **пятнадцать механизмов** — среди них `JulesDispatchService`, `TechnicalLeadCompiler`,
`FalsificationCycleService`, `AutoMergeService`, `DeliveredWorkJudgmentService`, `OpsAuditorService`,
`RequirementGroundingService` | читает и пишет `ContextChunkRepository`, считает близость через
`MLPredictionServiceClient` | переиндексация по расписанию в три часа ночи, плюс отдельная —
`reindexIfEmbeddingModelChanged`, когда сменилась модель вложений.
*Ценность:* без него каждый, кому нужен устав роли, читает файлы сам, целиком и по-своему.
*Комментарий:* **ядро, и заведено оно как починка настоящего происшествия.** До 7 августа этот приём был
independently переписан «по месту» **не менее чем в четырёх местах** — в трёх сборщиках подсказок цикла
фальсификации и в раздаче задач, — и ни одно из них не пользовалось уже проиндексированным корпусом.
Последствие названо числом: аудит кодовых дефектов один давал подсказку **в полтора-два мегабайта** из
полных уставов тринадцати ролей и полных файлов образцов, и исполнитель отвергал её ошибкой на каждой
попытке.

Три решения здесь верны, и одно из них сильнее своего образца.

Первое: **запасной путь сужен намеренно.** Если выборка недоступна — флаг снят, указатель пуст, вложения не
посчитались, — читается сырьём только собственный устав роли, с ограничением по размеру, и **никогда не
безразмерный оригинал и никогда корпус образцов**. Смысл записан прямо: вызывающий не должен получить
пустоту только оттого, что индексация для этой роли ещё не прошла. То есть отказ инструмента не превращается
ни в молчание, ни в возврат к тому, что уже однажды положило раздачу.

Второе: **порог близости вычисляется, а не назначается.** `dynamicSimilarityFloor` выводит его из разброса
оценок **этого запроса** — по межклассовому расхождению между близким и далёким, — и падает на постоянный
порог, только когда различать нечего (меньше двух разных оценок). Причина названа: неподвижная отсечка
слишком строга для широкого запроса и слишком слаба для узкого. И отдельно оговорено, зачем вообще нужен
нижний предел: чтобы «заведомо неподходящий корпус не попадал внутрь просто потому, что что-то обязано быть
первым в ранжировании».

Третье, мелкое, но говорящее: в перечне источников, которые видит работник продукта, значится журнал
наблюдателя, и рядом признано, что **его пропуск был ошибкой, пойманной тестом**, который всё это время
утверждал его извлекаемость. Тест был прав, а список неверен, — и это записано, а не замолчано.
*Живое, 7 сентября 2026:* выборка работает. В журнале за сутки 583 упоминания извлечения и 181 —
контекста; `CONTEXT_CHUNKS` держит 1518 кусков (замер `db-table-sizes`). Собственных строк служба почти не
пишет, поэтому судить о ней приходится по следам вызывающих.

Отдельно оговорю то, что мог бы приписать сюда ошибочно: в журнале 42 ошибки `HTTP 400`, то есть той самой
породы, ради которой служба и заведена. **Они не отсюда.** Замер: все 42 идут от `ProjectFlowService` и
`AccountHealthService` и несут причину `jules_precondition_unspecified` — исполнитель ссылается на
неуказанное предусловие. Размер подсказки тут ни при чём, и связывать их со службой контекста было бы
догадкой, выданной за замер.
*Философия:* `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (D014) — Алонзо Чёрч,
`BARCAN-TAG-08 SUBSTITUTIVITY-SALVA-VERITATE`, принцип формального лямбда-исчисления, anchor *Lambda calculus
and Church's thesis — formal computability*. Сильная дословно: «правило хранится извлекаемым куском с
источником, оценкой и классом дефекта, и цитируется идентификатором». Слабая: «правило пересказано по
памяти». Опровержение: «потребовать идентификатор образца; отсутствие ссылки и есть галлюцинация».
**Форма: сильная.** Правило хранится куском, у него есть источник и оценка близости, и выдаётся оно
идентификатором образца, а не пересказом; запасной путь тоже читает **источник**, а не память.
**Реализация сильнее образца в одном: образец ничего не говорит о том, откуда берётся отсечка
относимости.** По этому случаю заведён новый образец —
`ALONZO_CHERCH_21_DERIVED_CUTOFF` в `docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md`.


*Живое, 9 сентября 2026, Codex: такт заполнения записи, без правки кода.* Операторская поправка применена ко второму пункту очереди: `GeminiContextService` нельзя продолжать как набор мелких vector-scope правок. Механизм целиком состоит из двух половин — поддержание индексированного корпуса и выдача caller-specific контекста, — и ломается, если чинить стоимость retrieval без сохранения источника, fallback-границы и различия между factory/delivery/product слоями.

*Идеальная форма механизма:* `GeminiContextService` — источник проверяемого контекста, а не генератор истины. Он индексирует только объявленные источники, хранит каждый chunk с `sourceType`, `sourceRef`, `chunkIndex`, `contentHash`, `embedding` и dimension, а на запрос возвращает только chunks, прошедшие source-scope, dimension check, cosine ranking, dynamic cutoff и top-k. `content` читается только после выбора id; до отбора допускается только vector projection. Любой formatted block обязан нести `sourceRef`, чтобы downstream worker мог отличить извлечённое свидетельство от пересказа агента.

*Граница:* сервис имеет право читать документы репозитория, хранилище chunks, настройки и embedding client; имеет право писать `ContextChunkEntity` только в reindex/index paths; не имеет права принимать решение за Jules/Claude/Gemini callers, расширять raw fallback до всего корпуса, смешивать method corpus с client/project corpus или выдавать пустую выборку как утверждение, что подходящего знания нет. Empty retrieval означает только `nothing extra to add` или explicit retrieval precondition failure.

*Входы:* `gemini_context_learning_enabled`; `repoRoot`; role tag/rules path; query/topK/sourceRefPrefix/sourceTypes; standing files (`OBSERVER_LOG` tail, invariants, AI review, Claude operator notes, operational failure patterns, BARCAN charters, philosopher-patterns, parallel-development charter); current embedding model/dimension; stored context chunks.

*Выходы:* `RetrievedChunk(sourceRef, content, similarity)`; role-scoped, philosopher-pattern, common-pattern, product-worker and method context blocks; static corpus for prompt caching; index rows in `CONTEXT_CHUNKS`; log warnings for empty/incomparable/stale corpus states.

*Связи и потребители:* прямые вызывающие: `JulesDispatchService`, `ProjectFlowService`, `FalsificationCycleService`, `TechnicalLeadCompiler`, `DeliveredWorkJudgmentService`, `OpsAuditorService`, `AutoMergeService`, `RequirementGroundingService`, `GeminiContextCacheManager`, и `SystemStatusController` для ручного reindex. Вызываемые зависимости: `ContextChunkRepository`, `MLPredictionServiceClient`, `SystemSettingsService`, файловая система под `repoRoot` и cache manager через controller-triggered refresh.

*Инварианты:* (1) opt-in flag off means no retrieval cost and no embedding call; (2) blank source clears its chunks; changed source is delete-then-insert and content-hash/idempotency prevents paid re-embedding of unchanged sources; (3) stored embedding dimension mismatch is visible and excluded, not silently ranked as unrelated; (4) fallback is source-narrow and char-count capped; (5) role/method/product callers have source scopes before ranking; (6) vector rows exclude `content` until selected top-k ids are known; (7) dynamic cutoff is derived from this query distribution and bounded by min/max; (8) formatted output cites source refs; (9) product-worker context excludes orchestrator-internal operational history; (10) caller-facing retrieval does not throw on missing corpus/embedding failure.

*Public surface classification, 9 сентября 2026:* `reindexIfEmbeddingModelChanged` = startup maintenance, writes only when probe proves stale dimensions; `reindexStandingKnowledge` = scheduled/manual indexer, gated by setting and repo root; `indexDocument` = source-owned delete/insert, idempotent by hash+dimension; `retrieveRelevantContext(query, topK)` = unscoped vector retrieval over full index, acceptable only for deliberately global callers; `retrieveRelevantContext(query, topK, sourceRefPrefix)` = prefix-scoped retrieval; `retrieveRelevantContextBySourceTypes` = type-scoped retrieval; `buildRoleAndPatternContext` = role + common method context; `buildRoleScopedContext` = role charter/pattern context with capped raw role fallback; `buildPhilosopherPatternContext` = philosopher-pattern only; `buildCommonPatternContext` = common/parallel context; `buildProductWorkerContextBlock` = product-worker doctrine context, currently scoped by in-memory predicate after full vector-row read; `buildContextBlock` = method corpus context; `buildStaticCorpus` = static prompt-cache corpus and therefore a deliberate full-text path, not per-call retrieval.

*Сильная форма:* every returned rule/chunk carries sourceRef + similarity + defect/source identity; every caller chooses its corpus before ranking; raw fallback is bounded and source-specific; top-k retrieval reads content only for selected ids; dimension drift and embedding failure are explicit states; tests prove off/empty/failing retrieval does not read corpus and scoped callers do not call `findAllVectorRows()`.

*Текущая форма:* semantic grounding is strong, but implementation is not ideal for weight/scope. Old Codex work already moved source-type, sourceRef-prefix and philosopher-pattern paths onto repository vector projections; `retrieveFiltered` still reads all vector rows for the unscoped path by design, and `buildProductWorkerContextBlock(role, query)` still supplies an in-memory predicate through the overload that starts from `findAllVectorRows()`. Startup stale-dimension cleanup also uses full entity `findAll()` as maintenance, not per-call retrieval. `buildStaticCorpus` intentionally reads full charters/patterns for prompt caching, so it must not be counted as the per-call retrieval defect.

*Опровержение:* (1) feature flag off, empty corpus, or query embedding failure must perform no corpus vector read and return empty; (2) role/prefix/type/philosopher paths must use repository-scoped vector queries and never `findAllVectorRows()`; (3) product-worker role path must not rank project/client/internal history before method corpus and must not need full index vector rows when its source predicate is known; (4) a fixed corpus fixture must return the same selected sourceRefs before/after a performance rewrite; (5) if stored dimensions differ, retrieval must report invisibility and exclude them rather than treating them as low similarity.

*Критерий закрытия:* запись считается заполненной, когда у каждого public entry point выше названы purpose, owner, source scope, fallback policy и refutation. Механизм считается идеальным только когда the remaining product-worker predicate has a repository projection or documented reason for full vector-row scan, per-call retrieval never reads content outside selected top-k, source scopes are covered by tests, and startup/static-corpus full reads are explicitly classified as maintenance/cache paths rather than hot retrieval.

*Свидетельства такта:* `grep -n "class GeminiContextService\|retrieveFiltered\|findAll\|findAllVectorRows\|findVectorRows\|buildPhilosopherPatternContext\|buildProductWorkerContextBlock\|dynamicSimilarityFloor" src/main/java/com/eneik/production/services/GeminiContextService.java`; `grep -RIn "GeminiContextService" src/main/java`; `grep -n "buildProductWorkerContextBlock\|buildContextBlock\|findAllVectorRows\|findVectorRows" src/test/java/com/eneik/production/services/GeminiContextServiceTest.java`; `grep -n "ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE" docs/philosopher-patterns/philosophers/BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE_04_alonzo-cherch.md`; `sed -n '108,120p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`; `sed -n '130,185p' docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md`.

*комментарий для Антигравити:* `GeminiContextService` не идеален в реализации: следующий правильный факт перед кодом — записать exact repository predicate for `buildProductWorkerContextBlock(role, query)` (`sourceType in PRODUCT_WORKER_SOURCE_TYPES` plus role-prefix/common/engineering/parallel exceptions) and compare sourceRefs against current behavior on a fixed fixture. Философия: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Алонзо Чёрч, publication anchor `Lambda calculus and Church's thesis - formal computability`, pattern `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`, family `RAG_GROUNDING_CAPSULE`, defect `D014 RAG hallucination`; для стоимости/происхождения данных дополнительно применим `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.



*Живое, 9 сентября 2026, Codex: 10-такт 8/10 — Gemini observer/context cluster, без правки кода.* Восьмой кластер десятиактного прохода заполнен как один observer grounding mechanism: `GeminiContextService` решает, какое знание попадает в подсказку; `InternalGeminiObserverController` делает видимыми журнал, действия, evidence graph and diagnostic facts; `GeminiObserverActionService` остаётся downstream actuator; `GeminiProjectObserverService` уже выведен как permanently inert. `LinearSyncController` проверен на участие в context freshness: source grep нашёл только `/api/linear-sync/completeness-report` and `SystemStatusService` Linear summary, so it belongs to tact 9 API edge, not to this grounding cluster.

*Идеальная форма кластера:* observer statements are grounded or they do not count. A worker prompt gets retrieved doctrine chunks with source refs, similarity, source type and defect identity; raw fallback is source-narrow and capped; startup reindex proves embedding dimensions before retrieval; internal observer endpoints expose only scoped/bounded evidence about what was seen and done; changing observer/manual endpoints require executable internal authorization, not a comment. A diagnosis can be reconstructed from stored journal/action rows and evidence nodes, not from an agent's later prose.

*Входы и выходы:* inputs are `CONTEXT_CHUNKS`, source files under `repoRoot`, embedding model dimension, role/query/source filters, Gemini observer journal/action rows, evidence/coherence/operational-reality repositories, dispatch/session/account diagnostic repositories and Linear task metadata only in the later API-edge report. Outputs are retrieved context blocks, reindexed/deleted context chunks, internal diagnostic JSON, manual recovery/reset actions through existing services, observer journal/action/evidence views and explicit empty/failure states. The cluster does not own task lifecycle, Linear completeness truth, account capacity eligibility or delivery truth; it exposes or cites those owners.

*Граница и взаимодействия:* `GeminiContextService` is used by `JulesDispatchService`, `ProjectFlowService`, `FalsificationCycleService`, `TechnicalLeadCompiler`, `DeliveredWorkJudgmentService`, `OpsAuditorService`, `AutoMergeService`, `RequirementGroundingService`, cache manager and status controller. `InternalGeminiObserverController` exposes `/internal/gemini-observer` views and three manual state-changing operations; it reads journal/action/coherence/evidence/session/account/task/project/persistent-worker tables. `GeminiObserverActionService` is the only real non-code observer actuator. `LinearSyncController` has no direct context-retrieval or observer-grounding caller found in source and is deferred to the dashboard/accounts/API tact.

*Инварианты:* (1) every retrieved doctrine item cites `sourceRef`; (2) retrieval failure is empty/explicit, never a claim that no relevant knowledge exists; (3) embedding dimension mismatch is excluded and repaired by maintenance, not ranked as low similarity; (4) product-worker context excludes orchestrator operational history unless explicitly whitelisted by source type; (5) internal observer diagnostics are bounded or named diagnostic dumps and cannot be a hot-path dependency; (6) manual observer endpoints write only by delegating to already-audited services or compare-and-set operations; (7) observer journal/action/evidence rows are the witness, later prose is not; (8) Linear sync completeness is an API-edge/reporting problem unless it becomes an input to prompt/context freshness.

| Call site | Owner of truth / table | Caller / cadence | Class | Ideal decision, refutation, closure |
| --- | --- | --- | --- | --- |
| `GeminiContextService.java:104` in `reindexIfEmbeddingModelChanged()` | `ContextChunkRepository` / `context_chunks` dimensions | application-ready maintenance | `startup stale-dimension count` | Replacement-needed but not hot retrieval. It needs count of chunks whose stored dimension differs from the probe, not full entities and parsed embeddings. Refutation: startup materializes every chunk just to count stale dimensions. Closure: repository count/projection by `embeddingDims != probe.length` preserves stale count and reindex trigger. |
| `GeminiContextService.java:121` in `reindexIfEmbeddingModelChanged()` | `ContextChunkRepository` source refs for stale/orphaned chunks | application-ready maintenance after reindex | `orphaned stale-source cleanup` | Replacement-needed as maintenance projection. It needs distinct `sourceRef`s still at the wrong dimension, then `deleteBySourceRef`. Refutation: cleanup loads every chunk's text/embedding after reindex. Closure: distinct stale source refs are selected by dimension and deleting them preserves the current rebuilt-source/orphan rule. |
| `GeminiContextService.java:627` through `retrieveFiltered(query, topK, Predicate)` | `ContextChunkRepository.VectorRow` doctrine corpus | product-worker prompt construction from `JulesDispatchService` | `source-scoped retrieval with in-memory predicate` | Already recorded as the remaining context defect. Replacement-needed after fixture. Refutation: product-worker context ranks full vector rows before applying known source-type/role/common exceptions. Closure: repository projection expresses `sourceType in PRODUCT_WORKER_SOURCE_TYPES` plus role-prefix/common/engineering/parallel exceptions and returns the same sourceRefs as current behavior. |
| `InternalGeminiObserverController.java:278` in `/dispatch-eligibility-detail` | `AccountRepository` account diagnostic rows | manual internal diagnostic | `dispatch eligibility diagnostic` | Conditionally allowed only as a bounded secured diagnostic; otherwise replacement-needed. Refutation: ordinary UI/scheduler depends on this dump, or decommissioned/all accounts are materialized when the question is one tag's dispatch eligibility. Closure: endpoint is internal-authorized and either declared diagnostic with a bound or backed by a projection matching dispatch predicates without exposing api keys. |
| `InternalGeminiObserverController.java:305` in `/account-capacity` | `JulesSessionRepository` open sessions by account/status | manual internal diagnostic | `account capacity diagnostic` | Replacement-needed. Existing repository methods can ask `findByStatusIn` or `countByAccountIdAndStatusIn`; the endpoint needs current open session evidence, not every session. Refutation: historical closed sessions are loaded before being skipped, or `/account-capacity` 500s while adjacent diagnostic endpoints work. Closure: open-session acquisition preserves including/excluding-blocked counts and task-status detail for fixture accounts. |
| `InternalGeminiObserverController.java:311` in `/account-capacity` | `AccountRepository` account rows plus session counts | manual internal diagnostic | `capacity table diagnostic` | Same diagnostic boundary as line 278. The mechanism is not ideal until account rows are either deliberately bounded admin reference data or projected to fields needed for capacity. Refutation: account capacity details expose unrelated secrets or rely on full account entities when only name/status/max sessions are needed. Closure: secured endpoint returns same rows without api keys and with session counts from scoped open-session acquisition. |
| `LinearSyncController.java:31` in `/api/linear-sync/completeness-report` | `TaskRepository` tasks with Linear ids + `LinearIssueMetadataRepository` | API edge completeness report, not observer context | `deferred API-edge full-table read` | Not part of this cluster by evidence. Source grep found no caller from `GeminiContextService` or observer grounding; it belongs to tact 9. Refutation: if a prompt/context builder starts consuming this report, it must be pulled into observer freshness. Closure for this tact: explicit deferral prevents hiding the line; tact 9 must classify it with dashboard/API boundaries. |

*Сильная форма:* RAG grounding and observer diagnostics share one rule: cite the witness or abstain. `RAG_GROUNDING_CAPSULE` requires retrievable chunks with source, score and defect class; `RELIABILITY_CHAIN` requires source, timestamp/freshness and validation path; `ACP-061` forbids changing a critical path before stating precondition/command/postcondition. Internal observer endpoints only help if they expose the same stored facts the acting mechanism uses and are protected as internal tools.

*Слабая форма:* context retrieval is semantically careful but still has maintenance/full-vector acquisition defects; internal observer diagnostics give useful facts but some are unbounded, two were already observed as HTTP 500 in the queue, and localhost restriction is documented as prose rather than proven authorization. Linear completeness remains visible as a later API-edge read, not a context freshness mechanism.

*Опровержение:* build a fixture with context chunks of two dimensions, role/common/product-worker source refs, observer journal/action/evidence rows, accounts with multiple statuses/capabilities, open/closed Jules sessions and Linear-linked tasks. The mechanism is false if retrieval loses sourceRefs, if product-worker context returns different doctrine refs after scoped projection, if dimension cleanup needs full entities, if observer diagnostics 500 or expose mutable/internal data without authorization, or if Linear completeness is consumed by prompts without being freshness-scoped.

*Критерий закрытия:* this record is complete for tact 8 when the context service's remaining maintenance/product-worker acquisitions, the observer controller's three diagnostic full-table reads, and Linear's deferral are all named with owner, boundary, refutation and closure. The implementation becomes ideal only when context maintenance uses dimension/source projections, product-worker retrieval has a repository predicate plus sourceRef parity test, observer diagnostics are secured and bounded/projected, the two broken internal endpoints are either fixed or removed, and Linear completeness is classified in tact 9.

*Кандидат на будущую реализацию Codex:* after explicit code approval, `GeminiContextService` product-worker predicate and stale-dimension projection are plausible candidates because fixture parity can be stated exactly. `InternalGeminiObserverController` must first be fixed as an internal-authorized diagnostic surface, not as a quick `findAll` cleanup. `LinearSyncController` is held for the next API-edge tact.

*Текущий статус:* not ideal. The observer/context grounding record is explicit; implementation still has two GeminiContext maintenance entity reads, one product-worker full-vector scan by in-memory predicate, three observer diagnostic full-table reads, and one Linear API-edge read deferred to tact 9.

*Свидетельства такта:* `grep -n "\.findAll()"` across `InternalGeminiObserverController`, `GeminiContextService`, `LinearSyncController`, `GeminiObserverActionService`, `GeminiProjectObserverService`; `nl -ba` contexts for `InternalGeminiObserverController` lines 1-120, 120-250, 250-325, 325-432; `GeminiContextService` lines 80-130 and 616-650; `LinearSyncController` lines 1-91; repository reads for `ContextChunkRepository`, `AccountRepository`, `JulesSessionRepository`, `GeminiObserverJournalRepository`, `GeminiObserverActionRepository`, `PersistentWorkerSessionRepository`, `EvidenceNodeRepository`, `CoherenceRunRepository`; source grep for `LinearSyncController|linear-sync|findByLinearIssueId`; `grep -n "ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE" docs/philosopher-patterns/philosophers/BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE_04_alonzo-cherch.md`; `grep -n "ELVIN_GOLDMAN_01_RELIABILITY_CHAIN" docs/philosopher-patterns/philosophers/BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE_02_elvin-goldman.md`; `grep -n "ACP-061" docs/philosopher-patterns/00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`; `sed -n '113,119p' docs/philosopher-patterns/03_PATTERN_STRENGTH.md`.

*комментарий для Антигравити: механизм не идеален. Не правь `InternalGeminiObserverController`, `GeminiContextService` or `LinearSyncController` как отдельные query-cleanups: сначала сохрани один observer grounding contract — retrieved context cites sourceRefs, product-worker retrieval is scoped before ranking, dimension maintenance uses projection evidence, internal diagnostics are authorized/bounded, broken observer endpoints are fixed or removed, and Linear completeness stays in API-edge scope unless it becomes prompt freshness input. Следующий такт 9/10 — dashboard/accounts/API edge cluster: `DashboardController`, `AccountController`, `InternalTaskController`, `LinearSyncController`, and account/task boundary semantics not already closed. Философия: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Алонзо Чёрч, publication anchor `Lambda calculus and Church's thesis - formal computability`, pattern `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`, family `RAG_GROUNDING_CAPSULE`, defect `D014 RAG hallucination`; дополнительно `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.*

# XXVII. Операционная правда: чем фабрика оценивает доверие к себе

**`OperationalTruthService`** (675 строк) — сводит доставку, доверие, деятельный поток и помехи в один
ответ о том, чего стоит нынешнее состояние проекта.
*Связи:* зовут `TrustSnapshotService`, `LeverPromotionService`, `ClientDeliverableReadinessService`,
`BranchGarbageCollectorService`, `OperationalTruthController` | наружу — единственная дверь `build(projectId)`
| он же держит `promotionPolicy()`, тот словарь из пяти ступеней, по которому ходит лестница доверия
(раздел XXIд).
*Ценность:* без него оценка состояния собирается каждым читающим заново и по-своему.
*Комментарий:* **ядро, и в нём найден дефект, который стоит назвать прямо.**

Оценка доверия начинается с **единицы** и дальше только **вычитается**. Замер: в теле расчёта шесть мест
вида `score -= …` и **ни одного прибавления** (0,35 или 0,25 за плохое состояние системы, 0,30 за повтор
содержания, 0,20 за неудачные разборы, 0,15, и два ограниченных вычитания за помехи и свежие дефекты).
Уровни: от 0,85 — «доверенный», от 0,65 — «наблюдать», от 0,40 — «ухудшен», ниже — «заблокирован».

Отсюда следует то, чего в коде никто не объявлял: **проект, о котором не известно ничего, получает полное
доверие.** Нет слияний, нет пройденных заслонов, нет вообще свидетельств — оценка остаётся единицей, и
уровень выходит «доверенный». Доверие здесь выдаётся по умолчанию и лишь отнимается.

Сопоставление внутри одной фабрики делает это особенно наглядным. Лестница доверия к решающим механизмам
(раздел XXIд) устроена ровно наоборот: незнакомый рычаг падает в «только наблюдать» с нулевым действием и
обязан **заслужить** полномочия двадцатью разрешёнными наблюдениями. Один и тот же вопрос — насколько мы
чему-то доверяем — решён в двух местах противоположными умолчаниями.

**Задача для кодинга.** Оценка доверия обязана начинаться не с полного доверия, а с неустановленного, и
расти по свидетельству, как растёт ступень рычага. Место: `OperationalTruthService.trust()`, начальное
`double score = 1.0` и шесть вычитаний ниже; уровни — `trustLevel()`. Проверка: проект без единого
свидетельства не должен получать уровень «доверенный». Опровергнет: путь, на котором отсутствие
свидетельств даёт положительную оценку.

Отдельно оговорю, чего здесь **нет** и в чём вины механизма нет: сами веса вычитаний назначены рукой, и это
уже признано в самой фабрике — миграция `V90` (раздел XXIIж) заведена ровно затем, чтобы когда-нибудь
подобрать их по настоящим исходам, и она честно отказалась сочинять их заранее.
*Живое, 7 сентября 2026* (`curl -s localhost:8080/api/projects/<id>/operational-truth`): доверие **0,7,
уровень «наблюдать»**. Предупреждений два, и оба настоящие: «388 задач имеют свидетельство непройденного
заслона качества» и «33 записи журнала дефектов за последние сутки». То есть механизм работает, считает и
называет основания — дефект не в его исправности, а в том, откуда он начинает счёт.
*Философия:* `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006) — Элвин Голдман,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип релайабилизма процессов, anchor *A Causal Theory of Knowing
/ Epistemology and Cognition — reliabilism*. Сильная дословно: «рискованное действие требует свидетельства
знаниевого качества, а не убеждения или намерения; приложена проверка или источник полномочия». Слабая:
«действие разрешено, потому что „мы уверены“». Опровержение: «потребовать источник; ссылка на собственное
убеждение и есть дефект». **Форма: слабая.** Уровень «доверенный» достигается **отсутствием** свидетельств
против, то есть источником доверия оказывается неведение. Потребовать источник у полной оценки нечего:
единица не выведена ниоткуда, она задана.
Второй образец: `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (D010, выведен из фабрики,
`04_FACTORY_DERIVED_PATTERNS.md`). Сильная форма требует, чтобы полномочия росли медленно по накопленному
свидетельству и терялись быстро. **Форма: слабая, и зеркально.** Здесь доверие не растёт вовсе — оно дано
целиком заранее и только убывает; путь вверх отсутствует так же, как у разжалованного аккаунта из пункта 38.

# XXVIII. Отказ от Gemini: что подлежит переносу

Указание оператора 7 сентября 2026: **от служб, завязанных на Gemini, надлежит отказаться** и перенести их
либо на сессии Claude, либо на бэкенд без языковой модели вовсе. Помечаю здесь, что именно этим затронуто,
и разделяю по замеру, а не по звучанию имени.

Сперва оговорка, без которой список был бы вдвое длиннее и вдвое неверней. Слово `Gemini` встречается в
58 файлах `src/main`, и из них 39 — бины. Но **упоминание не есть зависимость**: большинство лишь называет
ключ настройки, поле сущности или пишет о нём в комментарии. Замер по настоящему вызову
(`grep -rn "generativelanguage\|googleapis.com\|generateContent"`) даёт **четыре файла**.

**Прямо вызывают модель — переносить в первую очередь:**

- `GoogleAiResourceService` (4 места вызова) — основной путь к модели.
- `StitchClient` (3) — порождение экранов дизайна; его же следы видны в живом журнале.
- `GeminiContextCacheManager` (1) — кэш контекста на стороне поставщика.
- `JulesApiClient` (1) — один вызов; предмет требует отдельной проверки, потому что основная работа этого
  клиента к модели не относится.

**Ходят к модели через посредника — переносить вместе с ним:**

- `MLPredictionServiceClient` и сам сайдкар `src/models/ml/PredictionService.py`: предсказание узкого места
  спрашивает модель и падает на среднее арифметическое при отказе (раздел XIX).
- Сайдкар `judgment-proxy/server.js`: сочиняет вердикты по ключевым словам, когда модель недоступна
  (там же).

**Уже мертво, переносить нечего** — `GeminiProjectObserverService` есть заглушка, «permanently inert»
(раздел XXIIд); его входы и таблицы остались, и два входа отвечают отказом (раздел XXIII).

**Особый случай, требующий решения раньше прочих:** вложения для выборки знаний считает
`MLPredictionServiceClient`, то есть **весь путь подсказок к исполнителям опирается на модель**
(раздел XXVI). Перенос выборки — не замена клиента, а выбор: считать вложения без внешней модели или
отказаться от ранжирования по смыслу в пользу иного отбора. Это решение о механизме, а не о вызове.

Сюда же относится уже описанное: связность свидетельств и её счёт (раздел XXIIе) назначением своим имели
служить «внешним якорем против самоотчёта модели». Если модель уходит, вопрос, ради которого якорь
существовал, меняется, и якорь надо перенаправлять, а не просто сохранять.

Всё перечисленное — **пометка о принятом решении**, а не предписание и не план. Ни одной строки продуктового
кода я по нему не трогал.

# XXIX. Цех дизайна: порождение экранов и сверка их с маркой

**`DesignAssetService`** (611 строк) — порождает образы дизайна, складывает их в черновики и сверяет
готовые с объявленным набором цветов и шрифтов проекта.
*Связи:* зовут `DesignShopOrchestrationService`, `JulesDispatchService`, `ProjectFlowService`,
`ClientDeliverableReadinessService`, `ProjectAuditPipelineService`, `GoogleAiResourceController` | зовёт
`GoogleAiResourceService` — то есть **это один из четырёх прямых путей к модели**, помеченных к переносу
(раздел XXVIII) | держит два корня, `design/draft` и `design/approved`, и оба лежат в репозитории заказчика,
а не у фабрики | из черновиков в утверждённое переводят двое: `DesignShopOrchestrationService:426` и
`JulesDispatchService:4291`.
*Ценность:* без него экраны не порождаются вовсе, а порождённые не с чем сверить.
*Комментарий:* **ядро, и здесь одно решение образцовое, а одно наблюдение тревожное.**

Образцовое — **отказ подменять**. Живая строка суток: «Stitch generation failed (unavailable) and the
caller requires implementable HTML; **NOT falling back to nano-banana**, which cannot produce…». То есть
когда основной порождатель недоступен, служба не подставляет более слабый, который выдаёт картинку вместо
пригодного к реализации кода. Она отказывается и говорит почему. Сравнить с `parseLeanValue` из раздела
XXIб, где неизвестное подменялось утвердительным значением: одна и та же развилка, противоположные решения.

Тревожное — в паре чисел, которые сами по себе понятны, а вместе означают больше. Сверка выдаёт
`traceRatio` около **0,07–0,10** при требуемых 0,9 — то есть почти ни один цвет на экране не восходит к
объявленному набору. И одновременно `crossScreenJaccard = 1.0` — экраны **согласны между собой полностью**.

Вместе это значит: экраны сделаны в одной палитре, но не в той, которая объявлена. Разнобоя нет, есть
**единодушное отклонение**. Две гипотезы, между которыми у меня нет замера. Либо основание, захваченное из
первой генерации проекта (`DesignShopCycleEntity`, раздел XXIж), не то, с чем сверяет аудит, — тогда
сверяют с одним, а порождают по другому. Либо порождатель вовсе не получает объявленных токенов на вход, и
тогда сверка меряет то, чего никто не пытался соблюсти. Что разделило бы: сравнить набор, переданный на
вход порождателю, с набором, по которому считает сверка.

**Задача для кодинга.** Показать в самой записи сверки, **с каким набором** она сравнивала и **какой набор**
получил порождатель, чтобы единодушное отклонение отличалось от несовпадения оснований. Место: расчёт
`traceRatio` в `DesignAssetService` (строка вывода «consistency audit traceRatio=… offTokens=[…]») и
передача объявленных цветов и шрифтов в `generateAsset`. Проверка: по одной строке журнала видно оба
набора. Опровергнет: прогон, где отклонение объяснимо одним лишь несовпадением оснований, а сверка об этом
молчит.
*Живое, 7 сентября 2026:* служба работает — 508 строк за сутки. Но **порождатель недоступен всё это время**:
410 отказов `Stitch generation failed`, первый в 06:20 шестого сентября, последний в 20:30 седьмого, то есть
непрерывно тридцать восемь часов. Сверка при этом продолжает считать по уже лежащим черновикам и выдаёт
названные выше числа. Отмечу отдельно: **служба при недоступности порождателя не портит ничего** — она
отказывается и пишет причину.
*Философия:* `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` (D015) — Нуэль Белнап, `BARCAN-TAG-06 DEONTIC-CONSISTENCY`,
принцип четырёхзначной логики, anchor *A Useful Four-Valued Logic / how a computer should think —
many-valued diagnostics*. Сильная дословно: «всякий цвет и шрифт на экране восходит к объявленному набору
токенов, отношение прослеживания посчитано». Слабая: «дизайн-система приложена к заданию». Опровержение:
«посчитать долю значений вне набора». **Форма: сильная как измерение, отрицательная как результат.** Образец
требует, чтобы отношение было **посчитано**, — и оно посчитано, названо числом и сопровождено перечнем
значений вне набора. Опровержение образца выполняется самим механизмом, добросовестно: доля вне набора
посчитана и она почти полная.
Второй образец: `NUEL_BELNAP_18_CROSS_SCREEN_JACCARD_GATE` (D015), тот же философ и якорь. Сильная
дословно: «экраны одной дизайн-системы сверяются **между собой**, а не только с объявленной системой; мера
названа». Слабая: «каждый экран сверен с системой по отдельности». Опровержение: «взять два экрана одной
системы и посчитать пересечение словаря; проходили порознь — не значит согласованы». **Форма: сильная.**
Экраны сверяются между собой, мера названа и выведена числом, и живой замер даёт 1,0. Именно эта сильная
форма и позволила увидеть, что беда не в разнобое: без неё низкое прослеживание читалось бы как «экраны
разные», а на деле они одинаковые и одинаково не те.

# XXX. Клиент к предсказателю: единственная дверь фабрики к внешней модели

**`MLPredictionServiceClient`** (486 строк) — единственный путь бэкенда к ML-сайдкару: считает вложения,
ведёт разговор с моделью, гоняет цикл с инструментами и спрашивает предсказание узкого места.
*Связи:* зовут **одиннадцать механизмов** — `GeminiContextService`, `JulesDispatchService`,
`ProjectFlowService`, `AutoMergeService`, `FlowSpineService`, `OpsAuditorService`,
`ContinuousOrchestrationService`, `InfrastructureVerdictLayer`, `OnboardingAuditService`,
`SelfFalsificationEpicMatcher`, `GreetingController` | ходит в `http://<ml>/api/v1/embed` и далее |
докладывает исходы `AiHealthTracker`.
*Ценность:* через него проходит **весь смысловой отбор фабрики**: выборка знаний для подсказок считает
близость его вложениями (раздел XXVI). Без него подсказки теряют ранжирование по смыслу, а не только
предсказание.
*Комментарий:* **ядро, и притом самое узкое место всей постройки — одна дверь на одиннадцать механизмов.**
Два решения здесь сделаны заметно лучше обычного.

Первое: **отказ отдаёт пустоту, а не правдоподобие.** При разомкнутом предохранителе, при снятом флаге, при
пустом или неразборчивом ответе `embed` возвращает `null` и записывает неудачу. Он **не сочиняет вектор**.
Это важнее, чем кажется: поддельное вложение не отличить от настоящего на глаз, оно тихо испортило бы
ранжирование, и выборка продолжала бы выдавать «самое близкое» из бессмыслицы. Сравнить с `parseLeanValue`
(раздел XXIб), где неизвестное превращалось в утвердительный вердикт.

Второе: **предохранитель устроен несимметрично, и это записано с обоснованием.** Он размыкается после пяти
подряд неудач, остывает пятнадцать минут, а после остывания пропускается **одна** проба, причём счётчик
намеренно не сбрасывается — «so a still-dead dependency reopens the breaker on that single failure instead
of after another five». То есть доверие к зависимости теряется быстро и возвращается медленно, ровно как у
лестницы доверия к решающим механизмам (раздел XXIд). Это **четвёртый случай** одного приёма на фабрике и
лишнее подтверждение образцу `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`.
*Живое, 7 сентября 2026:* сайдкар поднят двое суток и по собственной проверке здоров
(`docker inspect eneikproductionsys-ml-1` → `healthy`, пять проб, последняя в 20:48 с кодом 0). Неудач
вложений в журнале бэкенда за сутки нет.

Но **проверка здоровья меряет не то.** Её команда — `urllib.request.urlopen('http://127.0.0.1:8000/docs')`,
то есть открытие страницы документации. Это доказывает, что процесс жив и отвечает по HTTP, и **ничего не
говорит о том, считаются ли вложения**: страница документации отдаётся без всякого обращения к модели.
Сайдкар может числиться здоровым при полностью неработающем счёте вложений, и первым, кто это заметит,
будет предохранитель — после пяти неудач, то есть уже внутри работы.

**Задача для кодинга.** Проверка здоровья ML-сайдкара обязана проверять предмет, а не присутствие: считать
вложение постоянной короткой строки и сверять размерность ответа. Место: `Healthcheck` контейнера `ml` в
`docker-compose.yml` (нынешняя команда открывает `/docs`). Проверка: при сломанном счёте вложений контейнер
перестаёт числиться здоровым. Опровергнет: состояние `healthy` при неработающем `/api/v1/embed`.

Это ровно та же болезнь, что записана в `V85` (раздел XXIIж): заслон проверял **наличие**, а не смысл —
«presence-only gate, content never read semantically».
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики (True/False/Both/Neither), anchor *A
Useful Four-Valued Logic / how a computer should think — many-valued diagnostics*. Сильная дословно:
«истинное, ложное, **неизвестное** и противоречивое представлены явно, и показано, как каждое хранится,
отображается и разрешается. Третий исход невозможно проигнорировать на стороне вызывающего». Слабая:
«булево плюс `null`, трактуемый по месту». Опровержение: «найти вызывающего, который компилируется, не
обработав „неизвестно“». **Форма: сильная на стороне производителя, не мерена на стороне вызывающих.**
Неизвестное представлено явно и честно — пустотой вместо подделки. Но `null` здесь и есть тот самый «булево
плюс `null`», и обошёл ли я всех одиннадцать читающих, чтобы убедиться, что никто не принимает пустой ответ
за пустой смысл, — нет, не обошёл. До этого обхода сильной формой считается только производство.
Второй образец: `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — не заслон». **Форма: слабая, и относится она к проверке здоровья, а не к самому клиенту.**
Утверждение «сайдкар здоров» проверкой на открытие страницы документации опровергнуть нельзя: она зелена и
при мёртвом счёте вложений.

# XXXI. Разбор чужого репозитория: неудача, ставшая утверждением о заказчике

**`RepositoryStackAnalyzer`** (432 строки) — обходит репозиторий заказчика через GitHub и составляет
описание его устройства: основной язык, каркас, база, есть ли непрерывная сборка и тесты, одиночный ли это
репозиторий, ветка по умолчанию, отметка исходного слепка и сколько файлов разобрано.
*Связи:* вызывающий **один** — `OnboardingAuditService` | ходит в GitHub за содержимым файлов | отдаёт
`StackProfile` вместе со списком файлов к разбору.
*Ценность:* без него фабрика берётся за чужой продукт, ничего о нём не зная.
*Комментарий:* **ядро по замыслу — это первое, что фабрика узнаёт о заказчике, — и в нём тот же дефект,
что я нахожу по всей фабрике, здесь в самой чистой форме.**

`StackProfile` состоит из трёх строк и трёх признаков-булевых. Замер: профиль строится в **трёх** местах с
одними и теми же значениями `"Unknown", "None", "None", false, false, false` — при отсутствии токена
GitHub (строка 52, с записью «GitHub token not configured, returning empty StackProfile»), при неудаче
обхода (100) и при ошибке (290). То есть **«мы не смогли посмотреть» и «в репозитории этого нет» дают одно
и то же значение**.

Со строками ещё честно: `primaryLanguage` получает «Unknown», и это слово различимо. С признаками нечестно
по устройству: `hasCI = false` означает разом и «непрерывной сборки нет», и «мы не проверяли».

И читающий на этом действует. Замер по `OnboardingAuditService`: строка 130 — `if (!stackProfile.hasCI())`
заводит находку; строка 103 — на `!hasTests()` при упоминании production заводит находку; строки 216–217
печатают в отчёт «Has CI: No», «Has Tests: No». Значит **неудача фабрики превращается в утверждение о
репозитории заказчика**, и утверждение это попадает в отчёт как установленный факт.

Это тот же подлог, что уже записан в перечне трижды: журнал фабрики, выданный за деятельность продукта
(раздел XXIе); единственный размеченный шаг, названный узким местом фабрики (XXIг); сгенерированный самой
фабрикой заголовок, принятый за тождество требования заказчика (XXIIв). Здесь он в самой чистой форме,
потому что предмет утверждения — прямо чужая собственность.

**Задача для кодинга.** Признаки профиля обязаны различать три исхода вместо двух: есть, нет, не проверено.
Место: `RepositoryStackAnalyzer` — три конструктора `new StackProfile("Unknown", "None", "None", false,
false, false, …)` на строках 52, 100 и 290, и сама запись `StackProfile`; читающий —
`OnboardingAuditService:103,130,216-217`. Проверка: при отсутствии токена GitHub отчёт не должен содержать
ни одной находки о заказчике. Опровергнет: находка «нет тестов», заведённая при неудавшемся обходе.
*Живое, 7 сентября 2026:* **механизм не работает.** В журнале за 45 тысяч строк — ноль упоминаний
`OnboardingAudit`, `StackProfile` и «GitHub token not configured» (контроль: слово `github` встречается
7952 раза, значит греп видит). Разбор нового репозитория на живой фабрике не запускался, потому что новых
проектов не заводили. Дефект от этого не исчезает, а становится отложенным: он сработает при первом же
заказчике, к репозиторию которого не будет доступа.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики (True/False/Both/Neither), anchor *A
Useful Four-Valued Logic / how a computer should think — many-valued diagnostics*. Сильная дословно:
«истинное, ложное, **неизвестное** и противоречивое представлены явно, и показано, как каждое хранится,
отображается и разрешается. Третий исход невозможно проигнорировать на стороне вызывающего». Слабая:
«булево плюс `null`, трактуемый по месту». Опровержение: «найти вызывающего, который компилируется, не
обработав „неизвестно“». **Форма: слабая, и опровержение выполнено дословно.** Вызывающий найден —
`OnboardingAuditService`, — он компилируется и не обрабатывает неизвестное, потому что обрабатывать нечего:
у булева поля третьего значения не бывает. Строковые поля здесь сильнее булевых ровно потому, что строка
вмещает «Unknown», а `boolean` — нет.
Второй образец: `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — Гилберт Райл,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип различия «знать что» и «знать как», anchor *The Concept of Mind —
knowing-how versus knowing-that, category mistakes*. Сильная дословно: «назван тип, схема или переходник,
удерживающий границу рода: процесс не выдаётся за объект, наблюдение за полномочие, политика за данные».
Слабая: «мы понимаем разницу». Опровержение: «найти место, где значение одного рода присваивается полю
другого без преобразования». **Форма: слабая.** Значение рода «состояние нашего доступа к GitHub»
присваивается полю рода «свойство репозитория заказчика», и преобразования между ними не существует —
как не существовало его между журналом фабрики и деятельностью продукта.

# XXXII. Завод репозиториев: адрес, который существует раньше репозитория

**`GitHubProjectFactoryClient`** (403 строки) — заводит на GitHub репозиторий под новый проект, заливает
начальные файлы, настраивает его и **выдаёт доступ исполнителям**.
*Связи:* зовут `ProjectFactoryService` и `ProjectFlowService:358-363` | ходит в GitHub за созданием
репозитория, загрузкой файлов, настройкой и приглашением соучастников | отдаёт `GitHubProvisioningResult`
из пяти полей: исход строкой, адрес, идентификатор, предупреждения и список приглашённых.
*Ценность:* без него у проекта нет ни места для работы, ни исполнителей с правом писать в него.
*Комментарий:* **ядро — это дверь, через которую фабрика вообще получает право что-то делать, — и в нём
одно решение верное, а одно опасное.**

Верное: **отказы не глотаются**. Неудачная загрузка файлов и неудачная настройка складываются в
`warnings`, приглашения соучастников несут каждое свой исход, а `ProjectFactoryService:126` их вычитывает и
кладёт в отчёт. Прерывание и исключение записываются как `SYSTEM CRITICAL`. Ни один из этих путей не
притворяется успехом: исход всегда начинается со слова — `skipped:`, `failed:`, `exists or blocked`.

Опасное — в первой же строке метода. `fallbackUrl` собирается из имени организации и имени проекта
**до всякого обращения к GitHub** и возвращается **во всех без исключения исходах**: и когда заведение
пропущено настройкой, и когда нет токена, и когда GitHub ответил ошибкой, и когда всё упало с исключением.
То есть **адрес репозитория существует раньше самого репозитория и переживает его несоздание**.

Сам по себе это был бы пустяк, если бы читающий сверялся с исходом. Замер показывает, что нет:
`ProjectFlowService:360-361` записывает адрес в проект двумя полями **безусловно**, и лишь строкой ниже, в
`362`, кладёт исход в отдельное поле. Сведения не потеряны — но всякий, кто читает у проекта только адрес,
получает правдоподобную ссылку на репозиторий, которого может не быть.

**Задача для кодинга.** Адрес обязан появляться только вместе с созданным репозиторием, а при пропуске и
отказе быть пустым — либо запись адреса в проект обязана быть обусловлена исходом. Место:
`GitHubProjectFactoryClient.provision` (сборка `fallbackUrl` в первой строке и его возврат в четырёх ветвях
отказа) и `ProjectFlowService:360-361`. Проверка: у проекта, чьё заведение отказало, поле адреса пусто.
Опровергнет: проект с непустым адресом и исходом, начинающимся на `failed:` или `skipped:`.

Отмечу и то, что относится к уже открытому вопросу о закрытости репозиториев: приватность задаётся здесь
одним полем `body.put("private", reposPrivate)` из настройки, то есть решение это одноместное и меняется в
одном месте.
*Живое, 7 сентября 2026:* механизм **не работал** — в журнале за сутки ни одной строки о заведении
репозитория, потому что новых проектов не заводили (тот же случай, что у разборщика чужого репозитория в
разделе XXXI: живой проект один и давний). Косвенно работоспособность подтверждается тем, что у нынешнего
проекта репозиторий есть и в него идут слияния — 38 упоминаний слияния за сутки. Проверить ветви отказа на
живой фабрике нельзя, не заводя проект.
*Философия:* `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики (True/False/Both/Neither), anchor *A
Useful Four-Valued Logic / how a computer should think — many-valued diagnostics*. Сильная дословно:
«успешное завершение — **значение**, которое не может существовать без выполненных предусловий, и оно несёт
свидетельство для следующего шага». Слабая: «статус `done` в поле и запись в лог». Опровержение:
«сконструировать результат успеха, не имея свидетельства; если это удаётся — форма слабая». **Форма:
слабая, и опровержение выполняется буквально одной строкой кода.** Адрес репозитория и есть то самое
значение, которое **не должно существовать без выполненного предусловия**, — а он конструируется первой же
строкой, до всякой проверки, и возвращается при любом исходе.
Второй образец: `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — Дэвид Чалмерс,
`BARCAN-TAG-02 RIGID-DESIGNATOR`, принцип двумерной семантики, anchor *Two-Dimensional Semantics — primary
and secondary intensions*. Сильная дословно: «отображаемое имя, сохраняемый идентификатор и сущность в API
разведены так, что перепутать их нельзя». Слабая: «одно поле служит всем трём». Опровержение: «изменить
отображаемое имя и посмотреть, не поехали ли ссылки». **Форма: сильная в замысле, слабая в употреблении.**
Разведение сделано правильно: адрес, идентификатор и исход — три разных поля, и идентификатор при отказе
остаётся пустым, как и должно. Но читающий волен взять одно поле из трёх, и берёт именно то, которое
заполнено всегда.

# XXXIII. Пульт управления: механизм, который сам записал свой изъян

**`CommandDashboardService`** (348 строк) — собирает сводку по проекту и выносит единственное видимое
человеку суждение: готов продукт к сдаче, не готов или неизвестно.
*Связи:* зовут `CommandDashboardController`, `ClientDeliveryService` и **`VerdictGate`** | ходит в базу
напрямую через `JdbcTemplate`, минуя репозитории | наружу — одна дверь `getDashboard(projectId)`.
*Ценность:* без него нет ни одного места, где разрозненные признаки сходятся в один ответ о готовности.
*Комментарий:* **ядро, и редкий случай: механизм несёт в себе собственную критику, записанную его же
автором.**

Готовность считается по четырём условиям — все задачи сделаны, все заслоны качества пройдены, все запросы
слиты, доступ к GitHub жив, — и сводятся они **тремя значениями**: неизвестно, если хоть одно неизмеримо;
не готов, если хоть одно отказало; готов, только если держатся все. Это конъюнкция Клини, и в коде она
названа своим именем.

Дальше — сама критика, дословно из кода: «Every one of these four is about CONSTRUCTION — tasks done, gates
passed, PRs merged, GitHub reachable. **None is about the product running or having been shown to anyone**,
which is why `ready` could be reached on merge counts alone». То есть механизм прямо объявляет, что его
собственное «готово» говорит о построенном, а не о работающем, — и это тот же изъян, который отдельно
записан в `V100` (раздел XXIIк), где сказано, что «доставлено» вычислялось из числа слияний.

Второе решение, тоже верное и тоже объяснённое: воздержание отображается словом «неизвестно», и в коде
сказано, почему это не мелочь оформления — «an unestablished claim is not a refuted one, and the two must
not be shown the same way to someone deciding whether to keep working or to accept». Неустановленное и
опровергнутое не должны выглядеть одинаково для того, кто решает, работать дальше или принимать.

**Задача для кодинга.** К четырём условиям построения обязано добавиться пятое, о показанном: наличие хотя
бы одного обхода ценности заказчиком (`client_acceptance_traversals`, `V100`). Место: расчёт `construction`
в `CommandDashboardService` — четыре проверки `allTasksDone`, `allQualityGatesPassed`, `allPrsMerged`,
`githubAccessHealthy`. Проверка: проект без единого обхода не достигает «готов». Опровергнет: «готов»,
выставленный при нуле обходов. Изъян этот механизм осознаёт сам; чего у него нет — так это отказа на его
основании.
*Живое, 7 сентября 2026* (`curl -s localhost:8080/api/projects/<id>/command-dashboard`): готовность —
**«не готов»**. Среди непройденных условий четыре, и две последние пришли **от слоёв решётки вердиктов**:
«runtime cannot say: the delivered product launches and reports healthy» и «six-sigma cannot say: process
defect density is within a declared bound — no bound has been…». То есть слои честно воздерживаются, и их
воздержание блокирует, как и задумано.

**Здесь же я исправляю собственную ошибку двух прежних записей.** Я утверждал, что заслон вердиктов включён,
но привязан ни к одному проекту, и потому не применяется. Живая выдача это опровергла: строки от слоёв
попадают в непройденные условия **только когда решётка применилась**. Перезамер настроек показал
`verdict_gating_project_slug = 'test-fiftieth'`. Ошибка была инструментальная и моя: у строковой настройки
значение лежит в поле `maskedValue`, а я читал `enabled`, годное лишь для булевых. Обе прежние записи
исправлены.
*Философия:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики (True/False/Both/Neither), anchor *A
Useful Four-Valued Logic / how a computer should think — many-valued diagnostics*. Сильная дословно:
«истинное, ложное, **неизвестное** и противоречивое представлены явно, и показано, как каждое хранится,
отображается и разрешается. Третий исход невозможно проигнорировать на стороне вызывающего». Слабая:
«булево плюс `null`, трактуемый по месту». Опровержение: «найти вызывающего, который компилируется, не
обработав „неизвестно“». **Форма: сильная.** Показано всё, чего требует образец: как третий исход
вычисляется (любое неизмеримое условие даёт воздержание), как хранится (значение решётки) и как
**отображается** — отдельным словом и отдельным цветом, с записанным основанием, почему его нельзя
показывать как отказ.
Второй образец: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007), тот же философ и якорь. Сильная
дословно: «успешное завершение — **значение**, которое не может существовать без выполненных предусловий, и
оно несёт свидетельство для следующего шага». Слабая: «статус `done` в поле и запись в лог». Опровержение:
«сконструировать результат успеха, не имея свидетельства; если это удаётся — форма слабая». **Форма:
слабая, и признана слабой самим механизмом.** «Готов» достижимо на свидетельствах о построенном, без
единого свидетельства о показанном. Редкость случая в том, что опровержение образца выполнено автором кода
заранее и записано рядом с самим кодом.

# XXXIV. Внутренний вход наблюдателя: запрет, записанный в комментарии

**`InternalGeminiObserverController`** (433 строки) — открывает наружу собственный журнал наблюдателя, его
действия, граф свидетельств и целый набор служебных справок о работе фабрики.
*Связи:* путь `/internal/gemini-observer` | **17 входов: 14 читающих и 3 изменяющих** —
`/retire-stuck-worker-now`, `/release-finalizing-wishlist`, `/reset-daily-session-counts-now`, плюс запись
сессии внутри | читает `GeminiObserverJournalRepository`, `GeminiObserverActionRepository`,
`CoherenceRunRepository`, журнал дефектов и размеры таблиц.
*Ценность:* без него узнать, что наблюдатель на самом деле видел и делал, можно было лишь восстановлением
по логам контейнера, теряемым при каждом перезапуске.
*Комментарий:* **периферия по замыслу и ядро по последствиям, потому что три его входа меняют состояние
фабрики.**

Замысел был верный, и он записан: заводился вход по указанию оператора — «Gemini has her own log, you could
have looked there», — чтобы настоящая запись действий стала **достижимой**, а не выводимой из кода.
Отдельно оговорено, что запись действий есть свидетельство «regardless of what she later claims in her
journal prose», то есть сделанное отделено от рассказанного о сделанном.

Дальше — то, ради чего эта запись существует. В javadoc класса написано: **«Restricted to localhost in
production via filter/security, same as InternalTaskController»**. Это запрет, записанный в комментарии.
Замер 7 сентября:

    curl -s -4 -o /dev/null -w '%{http_code}' http://2.28.123.162:8080/actuator/health            → 200
    curl -s -4 -o /dev/null -w '%{http_code}' http://2.28.123.162:8080/internal/gemini-observer/db-table-sizes → 200

Контроль пройден: обычный путь здоровья по тому же внешнему адресу тоже отвечает 200, значит проба
работает, а не молчит. Прежде та же проба по IPv6 дала ноль и по контролю тоже — я её отбросил как негодную,
а не принял за доказательство закрытости.

Оговорю границу этого замера честно: проба сделана **с самой машины** по её внешнему адресу, поэтому она
доказывает, что **приложение не ограничивает путь**, но сама по себе не доказывает, что до него дойдёт
удалённый хост — ответ мог вернуться петлёй, не выходя наружу. Для полного доказательства нужна проба со
стороннего хоста; её у меня в этот такт не было.

**Задача для кодинга.** Ограничение, объявленное в javadoc, должно стать исполнимым: проверка источника
запроса на всех входах пути `/internal/**`, и в первую очередь на трёх изменяющих. Место:
`InternalGeminiObserverController` и одноимённая оговорка у `InternalTaskController`. Проверка: запрос
`/internal/gemini-observer/db-table-sizes` со стороннего хоста получает отказ с названной причиной.
Опровергнет: успешный ответ на изменяющий запрос без полномочия. Пункты 24 и 27 перечня о том же; здесь
названо конкретное место и конкретная ложная строка.

Второе, помельче: два входа этого контроллера — `/dispatch-capacity-probe` и `/persistent-workers` —
отвечают `HTTP 500` (раздел XXIII), тогда как `/db-table-sizes` на том же контроллере исправен. Поверхность
разбора у выключенного механизма частично сломана, и заметить это некому.
*Живое, 7 сентября 2026:* контроллер работает и отвечает; сам наблюдатель, которому он служит, —
заглушка, «permanently inert» (раздел XXIIд). То есть **вход пережил механизм**: смотреть через него не на
что, а изменять через него по-прежнему можно.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз,
`BARCAN-TAG-10 DEONTIC-PROHIBITION`, принцип исключающих причин, anchor *Practical Reason and Norms / The
Authority of Law*. Сильная дословно: «запрет — **исполнимый путь отказа** с объяснимой причиной, и на него
есть тест». Слабая: «**запрет записан в документе или комментарии**». Опровержение: «совершить запрещённое
действие; если оно прошло — запрета нет, есть пожелание». **Форма: слабая, дословно по определению слабой
формы, и опровержение выполнено.** Запрет записан в комментарии; запрещённое действие — обращение не с
localhost — прошло и вернуло 200. По Разу это не запрет, а пожелание.
Второй образец: `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «названа точка, где меняется
владелец проверки, полномочия или сохранения, и на неё есть тест». Слабая: «граница „понятна из структуры
пакетов“». Опровержение: «удалить проверку на границе; если ни один тест не покраснел, границы нет».
**Форма: слабая.** Точка смены полномочий обозначена **словом в пути** — `/internal` — и больше ничем.
Удалять проверку для опровержения не нужно: её нет, удалять нечего.

# XXXV. Выбор аккаунта: наказание порядком, а не исключением

**`AccountRepository`** (306 строк) — хранилище аккаунтов, и в нём же живёт запрос, которым фабрика решает,
кому отдать следующую работу.
*Связи:* `lockNextJulesAccountWithCapacity` берёт строку с настоящей блокировкой — `FOR UPDATE SKIP LOCKED`,
то есть два раздатчика не возьмут один аккаунт | рядом лежат изменяющие запросы: сброс дневных счётчиков,
возврат из дневного предела и из блокировки со стороны службы | зовут `ProjectFlowService`,
`AccountHealthService` и раздача задач.
*Ценность:* без него пул аккаунтов не пул, а список: некому решить, чья очередь.
*Комментарий:* **ядро, и это лучшее место кода, что я прочёл за смену — не по устройству, а по тому, как
в нём записан ход мысли.**

Порядок выбора состоит из трёх ключей: сперва счёт отказов аккаунта с момента его последней **принятой**
сессии, затем число открытых сессий, затем давность отклика. И к первому ключу приложен разбор двух
измеренных происшествий.

Первое: **отказ ничего не создаёт**, поэтому аккаунт, отказывающий на всё, держит ноль открытых сессий и
сортируется **первым** — «preferred for carrying nothing». Замер того дня приведён поимённо: один аккаунт
свободен, ноль открытых сессий, потолок 15, **206 отказов подряд** и ни одной принятой сессии с 28 августа
21:36, тогда как шесть работавших аккаунтов имели по сессии-другой и потолок 3.

Второе: сначала признак был **одним битом**, и бит насытился. Под нагрузкой — 10 раздач против 37 отказов —
свежий отказ оказался у каждого, бит сравнялся у всех, и решать стал следующий ключ, то есть снова «меньше
всего открытых сессий», то есть снова тот, кто ничего не несёт. Итог замерен: этот аккаунт выбирали
**первым одиннадцать раз за пятнадцать минут** при нуле принятых из пятнадцати. Бит заменили счётом.

И вот принцип, ради которого я эту запись и пишу, — он сформулирован в самом коде и лучше, чем сформулировал
бы я. Ведущий ключ выбран так, что **отказом его улучшить нельзя**: отказ пишет строку без внешнего
идентификатора, а это поднимает счёт и двигает аккаунт **назад**. Обнулить его можно ровно одним событием —
принятой сессией, — «an event Jules produces and the factory cannot». То есть снять с себя наказание
наказанный не может, и подделать его фабрика тоже не может.

При этом: «**It is an ordering, never an exclusion** — if every account is in a refusal run the order is the
previous one and work still goes out. It attributes no fault: it touches neither the status of the account
nor its health counters». Никто не выбывает, никому не приписывается вина, и при поголовном отказе поток не
встаёт.

Третье, мелкое и поучительное: в комментариях внутри этого запроса **намеренно нет ни одного апострофа** —
Spring Data просматривает всё содержимое аннотации на предмет кавычек, не исключая комментарии SQL, и один
апостроф стоил **сорока трёх перезапусков подряд** 29 августа.
*Живое, 7 сентября 2026:* пул из пяти действующих аккаунтов, четыре свободны, один занят, у всех признак
включённости истинен (`curl -s localhost:8080/api/accounts`). Выбор работает: слияния идут, а раздача
отказывает с объяснимым основанием «раздавать нечего» (раздел XXIII), а не по недостатку аккаунтов.
*Философия:* `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006) — Джозеф Раз,
`BARCAN-TAG-10 DEONTIC-PROHIBITION`, принцип исключающих причин, anchor *Practical Reason and Norms / The
Authority of Law*. Сильная дословно: «до реализации полномочий составлена матрица прав, обязанностей,
привилегий и власти, и на каждое отношение есть тест разрешённого и запрещённого». Слабая: «роли
перечислены, проверки написаны по месту». Опровержение: «найти отношение, у которого нет теста запрета».
**Форма: не мерено.** Права здесь выражены порядком, а не разрешениями, и тестов запрета я не искал.
**Реализация сильнее всех образцов корпуса в одном: ни один из них не говорит, какой ФОРМЫ должно быть
взыскание и кто вправе его снять.** По этому случаю заведён новый образец —
`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` в `docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md`.
По нему **форма: сильная**: взыскание есть порядок, а не исключение; снимается событием, которого
наказанный не производит и фабрика не подделывает; вины не приписывает; при поголовном взыскании
вырождается в прежний порядок и поток не останавливает.

# XXXVI. Хранилище задач: запрет, стоящий на обоих уровнях

**`TaskRepository`** (270 строк) — хранилище задач, и в нём же атомарные переходы состояния, которыми
единственно разрешено оживлять и закрывать задачу.
*Связи:* восемь собственных запросов, из них два изменяющих | `compareAndSetStatus` — сравнение-с-обменом:
запись ложится, только если строка **в тот же миг** ещё в ожидаемом состоянии | `writeStatusUnlessTerminal`
— то же для тех, кто знает лишь «не конечное» | зовут `ClaimService` (`fail`, `releaseClaimToQueue`,
`reopenWithAmendedBrief`, `closeTaskAsFailed`, `closeTaskAsBlocked`), `PlannedWorkRecoveryService`, самолечение
и истечение аренды.
*Ценность:* без этих двух запросов оживление задачи есть чтение с последующей записью, между которыми
помещается чужая транзакция.
*Комментарий:* **ядро, и здесь редкий для этой фабрики случай: один и тот же запрет исполнен дважды, на
разных уровнях, и оба раза по-настоящему.**

В сущности `TaskEntity.setStatus` бросает исключение при перезаписи конечного состояния (раздел XXIз). Но
массовое обновление **минует жизненный цикл сущности по определению**, и никакой перехватчик тут не
сработает, — поэтому тот же инвариант вписан прямо в условие SQL: строка обновляется, только если её
состояние **не входит** в множество конечных. Запись становится пустой операцией в тот самый миг, когда
другая транзакция уже увела строку в конечное. Отсюда прямое следствие, сформулированное в коде: конечную
задачу **нельзя ни воскресить, ни перезаписать**, кто бы ни успел раньше.

Причина заведения названа и она про гонку: самолечение, истечение аренды и восстановление плана — все трое
читали состояние в Java, а писали новое **позже, без повторной проверки**, и в этот промежуток чужая
транзакция успевала сделать задачу конечной. Закрыто на уровне движка базы, а не доверием к устаревшему
чтению в памяти. И там же правило для будущих: всякое место, оживляющее задачу, обязано пользоваться этим
запросом, а не сеттером с сохранением.

Второе, и оно из тех находок, что переворачивают доверие к целому семейству проверок. Отметка движения
пишется **той же атомарной записью**, что и состояние. Раньше не писалась — и не могла: массовое обновление
минует жизненный цикл, так что и хук бы не помог. Замер того дня: **271 задача из 412 несла отметку
изменения, в точности равную отметке создания**, включая 257 из 375 завершённых. Значит всякий предикат вида
«не двигалась дольше стольких-то часов» читал **возраст, а не движение**. Половина застойных проверок фабрики
меряла не то, и обнаружилось это только когда посчитали.
*Живое, 7 сентября 2026:* хранилище работает — в базе 665 задач (замер `db-table-sizes`), и живой свод
потока печатает разбор доставки по ним: 394 проверено, 45 отвергнуто, 244 не проверено (раздел XXV). Отказов
на перезаписи конечного состояния в журнале за сутки нет, что согласуется с тем, что запрет исполняется
тихо: он не бросает, а просто не меняет ни одной строки.
*Философия:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006) — Джозеф Раз,
`BARCAN-TAG-10 DEONTIC-PROHIBITION`, принцип исключающих причин, anchor *Practical Reason and Norms / The
Authority of Law*. Сильная дословно: «запрет — **исполнимый путь отказа** с объяснимой причиной, и на него
есть тест». Слабая: «запрет записан в документе или комментарии». Опровержение: «совершить запрещённое
действие; если оно прошло — запрета нет, есть пожелание». **Форма: сильная, и вдвойне.** Запрещённое
действие исполнимо ровно в двух местах — через сеттер и через массовое обновление, — и оба закрыты: первое
исключением, второе условием запроса. Тесты названы в разделе XXIз (`TaskEntityLaw20Test` и соседние).
Сравнить с разделом XXXIV, где запрет был записан в комментарии и опровергался одним запросом: здесь тот же
философ, противоположный исход.
Второй образец: `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` (D013) — Альва Ноэ,
`BARCAN-TAG-03 BELIEF-INTENSION`, принцип энактивизма, anchor *Action in Perception — enactive perception*.
Сильная дословно: «происшествие объяснено причинной цепью: спусковой крючок, механизм, смена состояния,
наблюдаемое следствие». Слабая: «назван соседний симптом». Опровержение: «попросить показать звено между
названной причиной и следствием; разрыв означает, что названо совпадение». **Форма: сильная.** Цепь
приведена целиком и без разрывов: массовое обновление минует жизненный цикл — отметка движения не пишется —
271 задача из 412 несёт отметку изменения, равную созданию, — предикаты застоя читают возраст вместо
движения. Каждое звено названо и измерено, и починка приложена к первому звену, а не к последнему.

# XXXVII. Сторож ТОС: чтение, которое пересчитывает

**`TocSentinelService`** (193 строки) — ведёт жетон исполнения через шаги, наполняет счётчики графа и раз в
две секунды пересматривает, что считать узким местом.
*Связи:* зовут **семеро** — `AutoMergeService` (единственный, кто размечает шаги),
`BottleneckAwarePriorityService`, `SixSigmaAuditService`, `KaizenService`, `ConstraintIdentificationService`,
`SystemAuditController`, `TocSentinelController` | внутрь ходит к `TocExecutionGraph`, `TocAnomalyDetector`
и `TocOptimizer`, и **отдаёт их наружу целиком** тремя геттерами | пишет `TocNode` и `TocEdge`
(раздел XXIг).
*Ценность:* без него граф не наполняется вовсе: жетон, шаги и их завершение проходят только через него.
*Комментарий:* **ядро по положению, и два замечания к устройству.**

Первое: **сторож работает раз в две секунды** — `@Scheduled(fixedRate = 2000)`, и каждый раз просматривает
заторы и заново определяет ограничение. Отсюда и объём в журнале: строк с меткой сторожа за сутки 7332,
больше, чем у любого другого механизма, кроме оркестрации. Сам по себе частый обход не порок; порок был бы,
если бы он что-то стоил, а при одном размеченном шаге он дёшев. Но частота эта задана числом в коде и ни из
чего не выведена — тот же разряд, что назначенные пороги рычага (образец
`ALONZO_CHERCH_21_DERIVED_CUTOFF`).

Второе, и оно существеннее: **`getDbrStatus()` — не чтение.** Он вызывает `evaluateConstraintsAndDbr()`, а
тот в цикле пишет в узлы: проставляет загрузку и признак главного ограничения (`TocOptimizer:44-58`,
раздел XXIг). То есть **всякий, кто спрашивает состояние, тем самым его меняет**, и таких спрашивающих
пятеро.

Сопоставление внутри одной фабрики опять разительное. `FlowSpineService` (раздел XXV) намеренно разводит
`build`, который только считает, и `observe`, который считает и записывает, — чтобы сам вопрос о состоянии
не оставлял следов. Здесь ровно наоборот: следов не оставляет только тот, кто не спрашивает.

**Задача для кодинга.** Разделить чтение и пересчёт, как это сделано у свода потока: `getDbrStatus()` обязан
возвращать последнее вычисленное сторожем состояние, а пересчёт остаётся за расписанием. Место:
`TocSentinelService.getDbrStatus` и `TocOptimizer.evaluateConstraintsAndDbr` (записи `setUtilization` и
`setPrimaryConstraint` внутри цикла). Проверка: два подряд запроса состояния при остановленном сторже дают
один и тот же ответ и ничего не меняют. Опровергнет: изменение признака главного ограничения, вызванное
чтением.

Третье, помельче: три геттера отдают наружу сам граф, обнаружитель и оптимизатор целиком, так что любой
вызывающий может писать в них напрямую, минуя сторожа. Владение здесь не объявлено ничем, кроме соглашения.
*Живое, 7 сентября 2026:* сторож работает непрерывно — в журнале строки `[TOC-SENTINEL][STEP_EXIT]` и
`[END_EXECUTION]` с отметками секундной давности, всего 7332 за сутки. Питает его по-прежнему один
размеченный шаг: сценарий `AUTOMERGE_CYCLE` (замер разметки — раздел XXIг, за прошедшие сутки не изменился).
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается на
собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная по источнику, испорченная наблюдателем.** Показания
берутся из настоящих замеров времени исполнения, а не из рассказа механизма о себе, — и предъявить их можно.
Но наблюдение здесь **меняет наблюдаемое**: спросив состояние, спрашивающий переставил признак ограничения.
Образец требует опираться на замер; он не предполагал, что снятие замера будет его же изменять.
Второй образец: `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «до разделения модулей
объявлено, какой агрегат вправе менять каждую часть». Слабая: «классы разделены по размеру или по слоям».
Опровержение: «найти поле, которое пишут два сервиса». **Форма: слабая.** Владение графом не объявлено:
сторож отдаёт его наружу целиком, и опровержение выполняется без поиска — писать в узлы вправе и сторож, и
обнаружитель, и оптимизатор, и всякий, кто взял граф геттером.

**`VideoAssetService`** (229 строк) — порождает видео-образы через тот же путь к внешней модели.
*Связи:* одна дверь `generateAsset`, вызывающих два | ходит в `GoogleAiResourceService`.
*Ценность:* без него нет видео-образов; на поток это не влияет.
*Комментарий:* **периферия.** Одна дверь, два вызывающих, поток удержать не может. Входит в перечень
помеченных к переносу с Gemini (раздел XXVIII). Разбирать подробнее не стал намеренно: при одной двери и
двух вызывающих подробный разбор был бы объёмом без предмета.
*Живое:* строк службы в журнале за сутки нет.
*Философия:* **не мерено** — по той же причине.

# XXXVIII. Вход к ресурсам модели: пять изменяющих запросов на открытом пути

**`GoogleAiResourceController`** (189 строк) — открывает наружу работу с внешней моделью: порождение образов
дизайна и видео, заведение дизайн-системы, сверку согласованности, опрос доступности моделей и **уборку
черновиков дизайна**.
*Связи:* путь `/api/ai/resources` — **не `/internal`, а обычный публичный** | девять входов, из них
**пять изменяющих**: `/design-drafts-cleanup`, `/probe-models`, `/design-assets`, `/stitch-design-system`,
`/video-assets` | зовёт `GoogleAiResourceService`, `DesignAssetService`, `VideoAssetService` — то есть
прямой путь к модели, помеченный к переносу (раздел XXVIII).
*Ценность:* без него порождение образов запускается только изнутри, по расписанию цеха дизайна.
*Комментарий:* **периферия по замыслу и ядро по последствиям, и здесь нашлось то, что подводит итог
разделу XXXIV.**

Там я записал, что запрет доступа объявлен в комментарии и опровергается одним запросом. Теперь измерено,
**почему**: проверки полномочий не существует нигде. Замер по всему `src/main` на
`SecurityFilterChain|WebSecurityConfig|OncePerRequestFilter|HandlerInterceptor` — **ноль файлов** (контроль:
тот же греп находит три файла с `@Configuration`, значит он видит). В сборке нет и самой библиотеки:
`spring-boot-starter-security` в `pom.xml` отсутствует (контроль: пять стартеров греп находит).

То есть javadoc из раздела XXXIV, обещавший ограничение «via filter/security», ссылается на фильтр, которого
**никогда не было**. Это не устаревшее описание — это описание того, чего не существовало.

Живая проба, сделанная намеренно самым безобидным из пяти изменяющих входов — опрос доступности моделей, а
не удаление черновиков и не платное порождение:

    curl -X POST http://2.28.123.162:8080/api/ai/resources/probe-models  → 200

Отсюда следствие, которое стоит назвать без смягчения. Через этот путь снаружи можно **удалить черновики
дизайна проекта** (`/design-drafts-cleanup` принимает идентификатор проекта и список имён) и **тратить
деньги на внешнюю модель** (`/design-assets`, `/video-assets`). Ни одно из этих действий не требует
предъявить что-либо.

**Задача для кодинга.** Ввести проверку полномочий на изменяющих входах; порядок очевиден из вреда:
сперва `/design-drafts-cleanup` (необратимая потеря), затем `/design-assets` и `/video-assets` (расход),
затем прочие. Место: `GoogleAiResourceController` и отсутствующий фильтр — его надо завести, а не починить.
Проверка: `POST /api/ai/resources/probe-models` без полномочия получает отказ с названной причиной.
Опровергнет: любой изменяющий запрос, прошедший без предъявления полномочия. Пункты 24, 27 и 59 перечня о
том же; здесь впервые названа **причина**: фильтра нет ни одного, и библиотеки для него в сборке тоже нет.

Оговорю границу пробы честно, как и в разделе XXXIV: запрос сделан **с самой машины** по её внешнему адресу,
поэтому доказано, что приложение не спрашивает полномочий, а не то, что до него дойдёт удалённый хост.
Разница в том, что теперь известна причина: спрашивать нечем.
*Живое, 7 сентября 2026:* вход отвечает — 200 на изменяющий запрос снаружи. Собственных строк контроллера в
журнале за сутки нет: его никто не звал, работа цеха дизайна идёт изнутри, по расписанию.
*Философия:* `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` (D006) — Джозеф Раз,
`BARCAN-TAG-10 DEONTIC-PROHIBITION`, принцип исключающих причин, anchor *Practical Reason and Norms / The
Authority of Law*. Сильная дословно: «до реализации полномочий составлена матрица прав, обязанностей,
привилегий и власти, и на каждое отношение есть тест разрешённого и запрещённого». Слабая: «роли
перечислены, проверки написаны по месту». Опровержение: «найти отношение, у которого нет теста запрета».
**Форма: отсутствует, а не слабая.** Слабая форма предполагает, что роли хотя бы перечислены, а проверки
написаны по месту. Здесь нет ни ролей, ни проверок, ни места, где они могли бы быть написаны: опровержение
образца выполняется не поиском отношения без теста, а тем, что **тестов нет ни у одного отношения**.
Второй образец: `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` (D006) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «названа точка, где меняется
владелец проверки, полномочия или сохранения, и на неё есть тест». Слабая: «граница „понятна из структуры
пакетов“». Опровержение: «удалить проверку на границе; если ни один тест не покраснел, границы нет».
**Форма: отсутствует.** Точка смены полномочий не названа нигде — ни путём, как в разделе XXXIV, где хотя бы
было слово `internal`, ни фильтром. Здесь путь публичный и по имени, и по устройству.

# XXXIX. Метрики качества: два счёта одного слова, 388 против нуля

**`QualityMetricsController`** (188 строк) — отдаёт наружу меру дефектности слияний и поимённую сводку
дефектов; только чтение, изменяющих входов нет.
*Связи:* путь `/api/quality`, два входа — `/conflict-dpmo` и `/defect-summary` | читает пять хранилищ:
разборы запросов, конфликты задач, сессии, задачи, проекты и находки первичного разбора.
*Ценность:* без него мера качества считается каждым читающим заново и по-своему.
*Комментарий:* **периферия по устройству — поток он не держит, — и ядро по тому, что показал живой замер.**

Сперва поправка к моему же беглому чтению. Увидев `findAll()` по двум хранилищам, я записал было, что мера
считается без разбивки и без окна. Живой ответ это опровергает: в нём есть и разбивка по проекту, и окно в
семь дней. Загружается всё, а группируется в памяти — это вопрос цены, а не верности.

Теперь то, ради чего запись стоит читать. Живой ответ `/defect-summary` даёт **`qualityGate: total 0`** —
ни одной проваленной проверки заслона качества. А служба операционной правды в тот же час предупреждает:
**«388 задач имеют свидетельство непройденного заслона качества»** (раздел XXVII).

Одно слово, два счёта, разница в 388. Замер обоих:

- здесь считаются задачи, у которых в отчёте заслона есть **проверка с признаком „не пройдена“**
  (`report.has("checks")`, затем `!check.path("passed")`). Итог — ноль.
- там считаются задачи, у которых отчёт **не пуст** и признак `qualityGatePassed` ложен
  (`getQualityGateReport() != null` и `!isQualityGatePassed()`). Итог — 388.

Отсюда следует то, чего ни один из двух механизмов не говорит: **388 задач имеют отчёт заслона, в котором
нет ни одной проваленной проверки, и при этом помечены непройденными.** То есть заслон отработал и не
применил ни одной применимой проверки, а его признак записал это как «не пройдено».

Это в точности то, что записано у `TaskEntity` (раздел XXI): массив стадий хранит **запрошенные** стадии, и
проверка «все пройдены» по пустому списку даёт истину, поэтому отвечать на вопрос о доставке может лишь
счётчик **применённых** проверок. Здесь видна обратная сторона той же монеты: там пустота давала ложный
успех, тут — ложную неудачу.

Правым в этой паре я считаю счёт этого контроллера: ноль проваленных проверок есть правда, потому что
проваленных проверок действительно нет. Число 388 — не ложь, но его подпись лжёт: это счёт **отчётов без
применимых проверок**, названный «свидетельством непройденного заслона».

**Задача для кодинга.** Признак `qualityGatePassed` обязан различать три исхода: проверки применялись и
пройдены, применялись и провалены, **не применялось ни одной**. Место: расчёт `qualityGateFailed` в
`OperationalTruthService:274-276` и подпись предупреждения в строке 436; на стороне сущности —
`TaskEntity.isQualityGatePassed` и счётчик применённых проверок, уже существующий там же. Проверка: сумма
«пройдено» и «провалено» плюс «не применялось» равна числу задач с отчётом, и ни одно предупреждение не
называет неприменённое проваленным. Опровергнет: предупреждение о непройденном заслоне при нуле проваленных
проверок в отчётах.
*Живое, 7 сентября 2026* (`curl -s localhost:8080/api/quality/conflict-dpmo` и `/defect-summary`): мера
дефектности слияний — 6299 на миллион по всей истории и 13745 за последние семь дней при 635 попытках
слияния и **четырёх** конфликтах. Конфликтов именно четыре, а не девяносто два: **починка `V123` держится**
(раздел XXIIз), сироты, вечно считавшиеся дефектами и в числителе, и в знаменателе, из меры ушли. Сводка
дефектов: всего 4, все — конфликты слияния; заслон качества и первичный разбор дают по нулю.
*Философия:* `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — Дэвид Чалмерс,
`BARCAN-TAG-02 RIGID-DESIGNATOR`, принцип двумерной семантики, anchor *Two-Dimensional Semantics — primary
and secondary intensions*. Сильная дословно: «отображаемое имя, сохраняемый идентификатор и сущность в API
разведены так, что перепутать их нельзя». Слабая: «одно поле служит всем трём». Опровержение: «изменить
отображаемое имя и посмотреть, не поехали ли ссылки». **Форма: слабая, и по неожиданной причине.** Разведены
здесь не имя и предмет, а **два разных предмета под одним именем**: «непройденный заслон» означает у одного
механизма проваленную проверку, у другого — отсутствие применимых. Поле `qualityGatePassed` служит обоим
смыслам сразу, что и есть слабая форма дословно.
Второй образец: `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — Нуэль Белнап,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип четырёхзначной логики, anchor *A Useful Four-Valued Logic /
how a computer should think — many-valued diagnostics*. Сильная дословно: «истинное, ложное, **неизвестное**
и противоречивое представлены явно, и показано, как каждое хранится, отображается и разрешается. Третий
исход невозможно проигнорировать на стороне вызывающего». Слабая: «булево плюс `null`, трактуемый по месту».
Опровержение: «найти вызывающего, который компилируется, не обработав „неизвестно“». **Форма: слабая, и
опровержение выполнено обоими вызывающими сразу.** Третий исход — «не применялось ни одной проверки» —
существует в данных и не представлен в типе; один читающий трактует его как ложь, другой не считает вовсе,
и оба компилируются.

# XL. Выметающий обход: восстановление, которому не нужен держатель

**`StrandedFinalizingSweepService`** (143 строки) — раз в минуту освобождает притязание требования,
застрявшее в переходном состоянии «завершается».
*Связи:* `@Scheduled(cron = "0 * * * * ?")` — каждую минуту | `sweep()` по всем проектам, `sweepProject()`
по одному | вызывающих в коде нет: механизм приводится в движение только расписанием | опирается на
`finalizing` из `WishlistStatus` (раздел XXIб).
*Ценность:* без него одна застрявшая строка останавливает проект целиком.
*Комментарий:* **ядро, и лучшее рассуждение о восстановлении, что я встретил в этом коде.**

Состояние «завершается» есть **страж, а не место отдыха**: раздача ставит его перед медленной работой с
GitHub, чтобы повторное завершение отступило, и снимает, когда работа кончилась. Обе смены принадлежат
одному пути исполнения. Если путь умрёт посередине — падение, перезапуск, убитая по памяти машина, — строку
**не сдвинет больше никто**, и код говорит это в двух местах прямо: «leaving the wishlist permanently stuck
in `finalizing` with no other recovery path» и «which would make every future admission's compare-and-swap
fail forever».

Последствие не местное, и цепь приведена целиком: застрявший корень делает ложным «все корни
скомпилированы», это делает ложным «разложение завершено», это держит проект в состоянии разложения, а в
нём отказано и восстановлению провалившегося рубежа, и раздаче задач, и раздаче разборов. **Одна строка
останавливает весь проект.** Замер: требование застряло 16 августа в 09:39 и стояло **38 часов**, всё это
время ноль задач в работе, и каждый оборот оркестрации писал отказы, ссылавшиеся на это одно состояние.

Почему прежнее средство восстановления не доставало: оно освобождает притязания, перечисленные в наборе
постоянного работника, а настоящая принадлежность завершения компилятора лежит **в другом месте** — в
пометке на полезной нагрузке самой задачи-компилятора. «So the tool looks in the wrong place, which is why
the same failure was investigated by hand five days earlier and left unfixed». То есть беду разбирали
руками, не нашли и оставили — потому что искали не там.

И вот решение, ради которого запись стоит читать. Обход **не ищет держателя вовсе**, и это названо его
сутью. Состояние «завершается» описано как покрывающее одну ограниченную работу; значит притязание старше,
чем такая работа может длиться, застряло **по определению самого состояния**, кто бы его ни поставил. В коде
это названо проверкой предмета: спросить, существует ли ещё то, что состояние поставило, — и ответ
получается **без обратной ссылки, которой в схеме никогда не было**.

Это редкий образец починки: вместо того чтобы заводить недостающую связь и потом её поддерживать, взяли
свойство, которое у состояния уже есть, — его ограниченность во времени.
*Живое, 7 сентября 2026:* обход работает и **молчит**: строк службы в журнале за сутки ноль при контроле в
20 упоминаний самого состояния `finalizing` — то есть греп видит, переходы происходят, а выметать нечего.
Здесь молчание есть здоровье, и это тот редкий случай, когда отсутствие записей я толкую как исправность, а
не как незапуск: расписание раз в минуту, и одна застрявшая строка дала бы запись немедленно.
*Философия:* `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` (D012) — Айзек Леви,
`BARCAN-TAG-04 MODAL-QUANTIFIER`, принцип фиксации доксастических состояний, anchor *The Fixation of Belief
and Its Undoing / Enterprise of Knowledge — doxastic commitment*. Сильная дословно: «у исключения есть срок,
область, утвердивший и компенсирующая проверка». Слабая: «флаг `skipValidation` с комментарием».
Опровержение: «найти исключение без срока — оно вечное, а вечное исключение есть новое правило». **Форма:
сильная.** Временное отступление от обычного хода — притязание — имеет срок (предельный возраст), область
(одно состояние одного вида строк), обоснование в коде и **компенсирующую проверку**, которой и является сам
обход. Опровержение выполнить нельзя: исключения без срока здесь нет, срок и есть признак срабатывания.
Второй образец: `AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` (D002) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «у объекта есть владелец,
личность, жизненный цикл и семантика удаления, и он привязан к агрегату или каноническому реестру».
Слабая: «класс с полями и репозиторием, у которого нет ответа на вопрос „кто вправе его удалить“».
Опровержение: «назвать две точки кода, удаляющие объект по разным правилам; если они есть — реестра нет».
**Форма: слабая по владению, сильная по жизненному циклу — и обход существует именно потому, что первое
слабо.** Владельца притязания назвать нельзя: обратной ссылки в схеме нет, и прежнее средство искало его не
там. Жизненный цикл же задан полностью, вплоть до предельного возраста, — и этого хватило, чтобы обойтись
без владельца.

# XLI. Заслон качества экрана: проверка предмета, а не отчёта о нём

**`DesignExcellenceGate`** (154 строки) — проверяет, что задача по внешнему виду действительно дала два
настоящих снимка экрана, и что они не одинаковы.
*Связи:* зовут **пятеро** — `ProjectFlowService`, `JulesDispatchService`, `ClientDeliverableReadinessService`,
`DesignSystemFalsificationService`, `VerificationEvidenceGate` | ходит в GitHub за настоящим содержимым
изменений и за размером файлов | освобождает от проверки на время сборочной поры (`isBuildPhaseExempt`).
*Ценность:* без него «экран сделан» есть слово исполнителя о самом себе.
*Комментарий:* **ядро, и его история — лучший пример заслона, который был хуже, чем отсутствие заслона.**

До 3 августа он читал поле полезной нагрузки, куда исполнитель должен был **сам записать** ссылки на снимки
и их размеры. Замер, приведённый в самом коде: греп по всему хранилищу **не нашёл ни одного пишущего** в это
поле, а стандартное задание исполнителю **прямо запрещает** коммитить снимки. То есть заслон требовал
ровно того, что задание запрещало, и ждал поля, которое никто не заполняет.

Последствие названо там же и оно хуже обычной дыры: как только проект выходит из сборочной поры, заслон
**отвергал бы всякую задачу по внешнему виду безусловно и навсегда** — за изъян площадки, а не за
недоработку исполнителя. Заслон, который всегда красен, не заслон ровно так же, как и тот, что всегда зелен:
ни тот ни другой ничего не различает.

Починка сделана по правильному признаку и он назван: «Charter Pattern #12 — independent verification, not
self-attestation». Задачам по внешнему виду добавили в задание отдельную оговорку — дать два настоящих
снимка в оговорённое место, — а заслон теперь проверяет, что файлы **действительно попали в изменения**
(«not a claim»), и берёт их **настоящий размер через API GitHub** («not a number the implementer wrote»).
Доверия к отчёту исполнителя не осталось нигде.

Две проверки стоит назвать порознь, потому что вторая умнее первой. Первая — нижний предел размера
(`MIN_SCREENSHOT_BYTES = 1024`), отсекающий пустую картинку. Вторая — **размеры двух снимков обязаны
различаться**: одинаковый размер означает, что страница на двух ширинах отрисовалась одинаково, то есть
отзывчивости нет. Подделать её, приложив один файл дважды, нельзя — именно потому, что размер берётся не со
слов.
*Живое, 7 сентября 2026:* прямого замера работы заслона у меня **нет**: собственных строк он в журнал не
пишет — ноль упоминаний за сутки. Контроль показывает, что это свойство всех заслонов, а не этого одного:
слово «gate» встречается во всём журнале 48 тысяч строк лишь 38 раз. Косвенно работа по снимкам идёт:
99 упоминаний снимков экрана. Утверждать по этому, что заслон срабатывал, я не буду — не мерено.
*Философия:* `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (D013) — Людвиг Витгенштейн,
`BARCAN-TAG-00 CODE-GUARDIAN`, принцип языковых игр, anchor *Philosophical Investigations — language-games,
meaning as use, private-language argument*. Сильная дословно: «утверждение о работе системы опирается на
логи, метрики, проверки здоровья или состояние свода, и ссылка приведена». Слабая: «утверждение опирается на
собственный рассказ агента о том, что он сделал». Опровержение: «потребовать команду, которой замер снят;
её отсутствие и есть нарушение». **Форма: сильная после починки, и до неё она была не слабой, а
невозможной.** Слабая форма опирается на рассказ агента — а здесь рассказ был о поле, которое агенту прямо
запретили заполнять, так что опереться было не на что вовсе. После починки источник предъявим: содержимое
изменений и размер файла, взятые у GitHub.
Второй образец: `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — не заслон». **Форма: сильная по устройству, не мерена по покраснению.** Проверка способна
опровергнуть и написана до утверждения; что она краснеет именно при дефекте, показано рассуждением о
различии размеров, но не прогоном — подкладывать одинаковые снимки на живой фабрике я не стал. И отмечу
обратную сторону образца, которой в нём нет прямо: заслон, краснеющий **всегда**, опровергающей силы имеет
не больше, чем зелёный, — и до 3 августа этот был именно таков.

# XLII. Кэш постоянного корпуса: два механизма на одно дело, работает один

**`GeminiContextCacheManager`** (162 строки) — заводит на стороне поставщика модели кэш постоянного корпуса:
двенадцати уставов ролей и семидесяти восьми файлов образцов, примерно сорок-пятьдесят тысяч единиц текста.
*Связи:* вызывающий **один** — `SystemStatusController:72`, то есть кнопка на своде состояния |
`getOrCreateStaticCorpusCache()` и `invalidateCache()` | это один из четырёх прямых путей к модели,
помеченных к переносу (раздел XXVIII).
*Ценность:* заявлено в самом файле: время до первого ответа с ~4,5 до ~0,8 секунды, расход входного текста
меньше примерно на три четверти.
*Комментарий:* **периферия, и вот почему — заведённым кэшем никто не пользуется.**

Замер по всему `src/main` на `cachedContent|cacheName|getOrCreateStaticCorpusCache` вне самого менеджера даёт
два попадания: строку в `SystemStatusController`, где кэш **создаётся**, и **комментарий** в
`GeminiContextService:698`. Ни одного места, где созданное имя кэша передавалось бы в запрос к модели
(контроль: внутри самого менеджера термин встречается семь раз, значит греп видит).

Прежде чем утверждать, я проверил вторую сторону — вызовы к модели идут через сайдкар. И там оказалось
обратное: **сайдкар имеет собственный кэш и действительно им пользуется** — `ensure_cached_content`,
`ask_gemini_cached`, и в теле запроса передаётся `"cachedContent": cached_content_name`
(`src/models/ml/PredictionService.py:117,154,158`). Там же приведён замер, сделанный до постройки:
«Verified live against the real API before building this».

Итог: **на одно дело два механизма, и работает не тот, что в бэкенде.** Java-овский заводит кэш по нажатию
на своде и остаётся ни с чем связанным; питоновский заводит свой и подставляет в каждый запрос. Заявленная
выгода достигается вторым, а не первым.

**Задача для кодинга.** Либо связать java-овский кэш с путём запросов, либо снять его как дублирующий —
второе вероятнее, раз вызовы к модели идут через сайдкар, у которого кэш свой. Место:
`GeminiContextCacheManager` целиком и его единственный вызов `SystemStatusController:72`. Проверка: греп по
`cachedContent` в `src/main` находит либо использование, либо ничего — но не создание без использования.
Опровергнет: найденное место, где имя из этого менеджера всё же попадает в запрос, — тогда механизм нужен, а
неверен мой замер.

Отмечу связь с указанием об отказе от Gemini (раздел XXVIII): при переносе этот механизм переносить не надо
вовсе, его надо снять. Это первый случай в перечне, где ответ на «куда переносить» — «никуда».
*Живое, 7 сентября 2026:* собственных строк в журнале за сутки нет; кэш заводится только по нажатию на
своде состояния, а нажатий не было. Проверить, существует ли кэш на стороне поставщика, я не могу, не делая
запроса к модели, — а это расход, и на живой фабрике я его тратить не стал.
*Философия:* `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — Фред Дрецке,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип информационной пропускной способности каналов, anchor
*Knowledge and the Flow of Information — informational epistemology*. Сильная дословно: «сигнал признан
годным лишь если меняет следующее действие и предотвращает ошибочное». Слабая: «сигнал есть и он верен».
Опровержение: «назвать действие, которое сигнал изменил; **сигнал без читателя не есть наблюдение**».
**Форма: слабая, и опровержение выполнено.** Назвать действие, которое изменило бы созданное здесь имя
кэша, я не могу: читающего нет. Это третий такой случай в перечне — после счёта связности (раздел XXIIе) и
счётчика переходов графа (XXIг), — и во всех трёх механизм исправен, а связи с решением нет.
Второй образец: `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` (D004) — Ахилле Варци,
`BARCAN-TAG-01 ACTUALIST-OBJECT`, принцип топологии пространственно-временных границ, anchor *Parts and
Places / formal ontology of boundaries and spatial parts*. Сильная дословно: «до разделения модулей
объявлено, какой агрегат вправе менять каждую часть». Слабая: «классы разделены по размеру или по слоям».
Опровержение: «найти поле, которое пишут два сервиса». **Форма: слабая.** Владение кэшем постоянного корпуса
не объявлено нигде, и потому его завели дважды — в бэкенде и в сайдкаре. Опровержение выполняется в своей
обобщённой форме: не поле, которое пишут двое, а **предмет, который двое заводят порознь**.

# XLIII. Верёвка: ограничитель выпуска, который не может сработать

**`TocOptimizer`** (144 строки) — выбирает главное ограничение, считает состояние барабана-буфера-верёвки и
**решает, допускать ли новое исполнение**.
*Связи:* зовёт его `TocSentinelService` — пересчёт из сторожа раз в две секунды и `shouldAdmit` при каждом
запуске исполнения (`TocSentinelService:52`) | пишет в `TocNode` загрузку и признак ограничения | наружу
состояние отдаётся через `DbrStatus` (раздел XXIг).
*Ценность:* без него граф остаётся набором счётчиков: некому назвать ограничение и некому придержать выпуск.
*Комментарий:* **ядро по замыслу, и оно замыкает разбор всего графа ограничения.**

Верёвка здесь настоящая, а не декоративная: `shouldAdmit` вызывается **на каждом запуске исполнения**, и
при затянутой верёвке работа с приоритетом ниже порога обхода (`HIGH_PRIORITY_BYPASS = 80`) получает отказ с
записью в журнал. Единственный размеченный сценарий идёт с приоритетом 40 (`AutoMergeService:164`), то есть
**придержать его верёвка вправе**.

И тем не менее она не срабатывает никогда, и причина устройственная. Верёвка натягивается при переполнении
буфера у ограничения; предел буфера — 15 (`maxBufferCapacity`), а буфером служит число работ в полёте у
узла-ограничения. Узел же размечен один, и работа в нём идёт по одной за раз, так что число в полёте не
превосходит единицы. **Пятнадцать недостижимо при одном шаге.**

Отсюда замыкающее суждение по всему графу. Раздел XXIг показал, что ограничение предопределено единственным
датчиком; здесь видно продолжение: **и ограничитель выпуска предопределён тем же — он не может сработать,
потому что мерить ему нечего.** Причём если бы он сработал, то придержал бы **ровно тот механизм, который
единственный и питает граф**: меньше циклов автослияния — меньше наблюдений — меньше оснований судить о
переполнении. Обратная связь с неверным знаком, того же рода, что записана в `V104` (раздел XXIIв), где
отказ прибора отодвигал следующую попытку дальше.

**Задача для кодинга.** Разметить шаги потока, чтобы буфер имел смысл, — либо снять предел, выведя его из
наблюдаемого, а не назначив числом. Место: `TocOptimizer.maxBufferCapacity = 15` и `shouldAdmit`, плюс
разметка в `AutoMergeService:164-174` как единственный источник наблюдений. Проверка: в журнале появляется
хотя бы одна запись придержания или обхода при настоящей нагрузке. Опровергнет: наблюдение переполнения
буфера при одном размеченном шаге — тогда неверен мой разбор, а не механизм.

Отмечу и то, что здесь сделано верно: обход по высокому приоритету **записывается в журнал**, а не молчит,
и придержание тоже. То есть если бы верёвка дёрнулась, узнать об этом можно было бы сразу.
*Живое, 7 сентября 2026:* **ноль придержаний и ноль обходов** за сутки (`grep -c 'DBR_THROTTLE'` и
`DBR_BYPASS`), при контроле в 10402 строки с меткой сторожа — то есть греп видит, механизм работает
непрерывно, а верёвка не натягивалась ни разу.
*Философия:* `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) — Альфред Тарский,
`BARCAN-TAG-06 DEONTIC-CONSISTENCY`, принцип семантической теории истины (T-схема: «P» истинно ⟺ P), anchor
*The Concept of Truth in Formalized Languages — semantic conception of truth*. Сильная дословно: «проверка,
способная **опровергнуть** утверждение, написана **до** принятия утверждения, и показано, что она краснеет
при дефекте». Слабая: «зелёный тест рядом с изменением». Опровержение: «снять правку и прогнать тест; не
покраснел — не заслон». **Форма: слабая.** Проверка написана и вызывается, но покраснеть не может: условие
её срабатывания недостижимо при нынешней разметке. Это тот же разряд, что заслон качества экрана до
починки (раздел XLI), только зеркальный: там проверка не могла позеленеть, здесь не может покраснеть, и обе
одинаково ничего не различают.
Второй образец: `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — Фред Дрецке,
`BARCAN-TAG-07 SECOND-ORDER-KNOWLEDGE`, принцип информационной пропускной способности каналов, anchor
*Knowledge and the Flow of Information — informational epistemology*. Сильная дословно: «сигнал признан
годным лишь если меняет следующее действие и предотвращает ошибочное». Слабая: «сигнал есть и он верен».
Опровержение: «назвать действие, которое сигнал изменил; сигнал без читателя не есть наблюдение».
**Форма: слабая, и по обратной причине, чем обычно.** Читатель у сигнала как раз есть — `shouldAdmit`
спрашивают на каждом запуске. Нет самого сигнала: величина, которую читают, не достигает порога никогда.
Прежде я находил сигналы без читателя трижды; это первый случай читателя без сигнала.

# XLIV. Очистка журнала проекта: предел настоящий, срабатывает раз в сутки

**`ProjectEventLogRetentionService`** (132 строки) — держит долговечный журнал проекта в границах, и держит
**по смыслу, а не по возрасту**.
*Связи:* `@Scheduled(cron = "0 17 3 * * ?")` — раз в сутки в 03:17 | `deleteBefore` по сроку и
`trimToCeiling` по потолку, каждый в своей короткой сделке | пишет `ProjectEventLogRepository` | сам себя
зовёт через прокси, чтобы сделка действительно открылась.
*Ценность:* без него таблица растёт неограниченно по построению — в javadoc записано, что она уже доходила
до **162 тысяч строк** при всех замороженных проектах и полном отсутствии работы.
*Комментарий:* **ядро, и устроено оно правильно, а вот частота не отвечает скорости роста.**

Правило сделано по смыслу, и это верно: указание оператора от 26 июля требует полного журнала **от начала
проекта до приёмки**, поэтому у **непринятого** проекта не удаляется ничего, каким бы старым оно ни было.
Чего указание не требует — хранить журнал вечно после того, как работа сдана; отсюда два независимых
предела, и они названы порознь, потому что отказывают по-разному: тридцать дней после приёмки и потолок в
двадцать тысяч записей на проект.

Теперь замер. Живой проект **не принят** (состояние «деятельный»), значит срок не применяется вовсе, и всё
держится на одном потолке. В свой единственный за сутки заход, 7 сентября в 03:17, служба удалила **17592
записи сверх потолка** и ноль по сроку. Спустя двадцать один час в таблице снова **37467 строк**.

То есть таблица ходит по кругу: раз в сутки её подрезают до двадцати тысяч, и к следующему заходу она
набирает почти столько же снова. Предел настоящий и работает, но **между двумя срабатываниями таблица почти
удваивается**, и всё это время лишние семнадцать тысяч строк лежат в базе, которая живёт внутри той же
памяти, что и приложение.

**Задача для кодинга.** Частоту очистки надо привести в соответствие со скоростью роста, а не с сутками:
при семнадцати с половиной тысячах записей в день суточный заход означает, что половину времени таблица
вдвое больше предела. Место: `@Scheduled(cron = "${project-event-log.retention-cron:0 17 3 * * ?}")` и
потолок `project-event-log.max-entries-per-project:20000`. Проверка: измеренное число строк не превышает
потолок более чем на дневной прирост, делённый на число заходов. Опровергнет: замер, показывающий, что
прирост неравномерен и суточного захода достаточно.

Отмечу сделанное верно и редкое: два предела **названы порознь с объяснением, почему их два** — «because
they fail differently». Один защищает от вечного хранения после сдачи, другой — от того, чтобы один шумный
проект заполнил базу. Это не два числа для надёжности, а два разных отказа, каждый со своим средством.
*Живое, 7–8 сентября 2026:* служба отработала **один раз** за сутки — одна строка в журнале, и в ней оба
числа названы: 0 по сроку, 17592 по потолку. Замер таблицы `db-table-sizes`: было 26735 днём, стало
**37467** к ночи, то есть около семисот-восьмисот строк в час.
*Философия:* `ALVA_NOE_01_PERCEPTION_ACTION_LOOP` (D011) — Альва Ноэ, `BARCAN-TAG-03 BELIEF-INTENSION`,
принцип энактивизма, anchor *Action in Perception — enactive perception*. Сильная дословно: «у всякого
действия есть воспринимаемая обратная связь, включая отказ». Слабая: «успех виден, отказ молчит».
Опровержение: «вызвать отказ и посмотреть, узнал ли о нём действующий». **Форма: сильная.** Действие
докладывает оба своих исхода числами, и отдельно ловится отказ по проекту — `log.error` с указанием, какому
именно проекту очистка не удалась. Молчащего отказа здесь нет.
Второй образец: `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` (D012) — Айзек Леви,
`BARCAN-TAG-04 MODAL-QUANTIFIER`, принцип фиксации доксастических состояний, anchor *The Fixation of Belief
and Its Undoing / Enterprise of Knowledge — doxastic commitment*. Сильная дословно: «у исключения есть срок,
область, утвердивший и компенсирующая проверка». Слабая: «флаг `skipValidation` с комментарием».
Опровержение: «найти исключение без срока — оно вечное, а вечное исключение есть новое правило». **Форма:
сильная.** Исключение — «у непринятого проекта не удаляем ничего» — имеет область (один проект), срок
(до приёмки), утвердившего (указание оператора, приведённое датой) и компенсирующую проверку (потолок,
работающий независимо от приёмки). Именно потолок и не даёт этому исключению стать вечным правилом.

# XVIII. Чего в этом перечне нет

## Фронтенда здесь нет, и это не пропуск

Слой `frontend/src/*.svelte` **исключён из разбора целиком** по указанию оператора 7 сентября, и основание
названо им точнее, чем я формулировал сам: **фронтенд есть отображение механизмов, а не механизм.**

Это не поблажка и не откладывание, а прямое следствие принятой здесь демаркации. Механизмом называется то,
чьё изменение само по себе меняет поведение другого механизма, — то, что способно удержать или уронить
поток. Экран не удерживает ничего: он показывает состояние, которое уже определено где-то ещё. Уберите
экран — поток пойдёт как шёл; уберите пространство состояний задачи, и поток встанет весь.

Отсюда и вывод, который стоит записать отдельно: **дефект, увиденный на экране, никогда не есть дефект
экрана.** Он есть дефект того механизма, чьё состояние экран отображает, и чинить его на экране значит
чинить отражение. Именно это и происходило с числом `failedTasks` в общедоступном своде (пункт 29): поле на
экране значило не то, что говорило, потому что неверен был счёт под ним, а не подпись над ним.

Побочно: нынешний фронтенд будет переписан заново, поэтому разбирать его — ещё и муда. Но даже если бы его
не переписывали, в этом перечне ему места нет.


**Это утверждение было ложным и снято 2026-09-06.** Я писал «описаны все 149 классов-механизмов,
неназванных ноль». Верно оно было ровно для **одного слоя из шести**: бинов Spring в бэкенде. Мой отбор брал
только `@Service`/`@Component`/`@RestController`/`@Configuration` — и отсекал, среди прочего,
`BaseQualityGate`, который **именно потому и мёртв, что не аннотирован**: прибор отсеивал тот самый класс
дефектов, который искал.

**Полный знаменатель, перемерян 6 сентября — 522 единицы, записей 205.**

Пять слоёв исходного списка закрыты. Открытым остаётся слой бинов Spring, который в том списке
числился готовым ошибочно: записей у него меньше, чем механизмов, и самые крупные механизмы фабрики —
именно там.

Прежняя строка «521 единица, названо 214» снята: колонка «названо» считалась по упоминанию имени в тексте,
а не по наличию записи. Признак теперь машинный и тот же, что в разделе XVIII: строка `**\`Имя\`**` и
`*Связи:*` в пределах четырёх строк ниже. Бины отделены от прочих классов грепом по
`@Service|@Component|@RestController|@Controller|@Repository|@Configuration`.

Одна поправка к этому признаку, найденная 6 сентября: он не видит аннотацию, записанную полным именем.
Замер — `grep -rlE "@org\.springframework\.stereotype\.(Service|Component|Repository)"` — даёт ровно один
такой класс, `VerdictReconciliation`; он бин, а числился среди классов без аннотации. Разбивка ниже
исправлена на эту единицу.

    слой                                          всего   записей
    бэкенд, бины Spring                             189      117  ← открыт, 72 без записи
    бэкенд, классы без аннотации                    160       48
    сайдкары (launcher.py 567, server.js 338,         3        3
      ML PredictionService 576)
    миграции базы                                   137       34  (слой закрыт: остальные 103 —
      77 меняют только форму, 26 входят в описанные семьи)
    фронтенд (svelte)                                21        —  ИСКЛЮЧЁН ИЗ РАЗБОРА
    скрипты                                          12        3  (слой закрыт: остальные 9 —
      обслуга, запускаемая рукой)
                                                   ----     ----
                                                    522      205

Расхождение с прежней разбивкой (149/200 против 187/162) — не рост фабрики, а другой признак: раньше бином
считалось не то. Расхождение по колонке записей больше и важнее: **214 против 125**, и вся разница — это
имена, которые я упомянул в прозе и счёл описанными. Фронтенд особенно нагляден: числилось «названо 4», а
слово `svelte` встречается во всём файле **дважды**, записей — ноль.

Единственный закрытый слой — сайдкары: три из трёх, раздел XIX.

Про сайдкары в файле описаны только **клиенты к ним** со стороны бэкенда — `RuntimeLauncherClient`,
`JudgmentAgentClient`, `MLPredictionServiceClient`: про дверь написано, про то, что за дверью, — ничего. А
`launcher.py` — единственный механизм, который трогает продукт клиента напрямую.

Миграции — тоже механизмы, а не файлы: V19 переносила поле `enabled`, и полсмены ушло на выяснение, кто
выключил аккаунты.

Проверка знаменателя воспроизводима: перечислить файлы каждого слоя и поискать каждое имя в этом файле.

Репозитории (46), модели и DTO (105) поимённо не расписаны: они хранилища, а не механизмы. Исключения,
которые механизмами являются и названы выше, — `CodeChangeClassifier`, `EmsFlowStage`, `KanoClass`,
`BetaPosterior`, `LeverStage`, `Verdict`.

Прежнее утверждение «контроллеры не механизмы, а поверхности» **опровергнуто замером** и снято: 5 сентября
поток семь часов держал `AccountController`. Двадцать один изменяющий контроллер разобран в разделе XIIа;
читающие контроллеры остаются вне перечня — остановить поток они не могут.

**Прежнее «из 139 файлов каталога `services` описаны 127» — ложь, и вот замер, который её вскрыл**
(6 сентября). Считались записи в обязательной форме: строка `**\`Имя\`**` с `*Связи:*` в пределах
четырёх строк ниже; контрольная проба — `TechnicalLeadCompiler`, запись у которого заведомо есть, найдена.

```
всего файлов в services: 139
записей в файле:         125   (это все записи документа, не только сервисы)
из них имена сервисов:    89
сервисов БЕЗ записи:      50
сверх объявленных двенадцати исключений: 38
```

Записей у сервисов **89, а не 127**. Одиннадцать из двенадцати объявленных исключений — типы результатов — записи действительно не
требуют. Двенадцатое, `OperationalPolicyDeniedException`, из этого списка **изъято по замеру**: оно несёт
проект, действие, состояние и статус полномочия, то есть основание отказа, и описано в разделе XXIв.
Признак «нет методов, которые что-то делают» отнёс его к данным, но удержание потока состоит не в том,
чтобы что-то сделать, а в том, чтобы остановка была объяснима. Остальные **38 не описаны**, и среди них несущие:
`FlowSpineService` (1255 строк), `SystemStatusService` (831), `GeminiContextService` (739),
`OperationalTruthService` (674), `DesignAssetService` (610), `MLPredictionServiceClient` (485),
`OperationalFlowCoreService` (489), `RepositoryStackAnalyzer` (431), `GitHubProjectFactoryClient` (402),
`CommandDashboardService` (347), `VideoAssetService` (229), `TaskTitleBuilder` (166),
`DesignExcellenceGate` (153), `StrandedFinalizingSweepService` (143), `VerdictReconciliation` (136),
`VerdictGate` (133), `LinearProjectFactoryClient` (130), `ProjectEventLogRetentionService` (131),
`ClientDeliveryService` (125), `BackendContractGate` (113), `BottleneckDetectionService` (96),
`BaseQualityGate` (93), `EpistemicLayerInvariantGate` (89), `RoleRulesParser` (87),
`ProjectEventLogService` (83), `Verdict` (76), `LeverStage` (49), `LeverAgreement` (46),
`LogScopeBuffer` (46), `DurableProjectLogAppender` (43), `Judgement` (41), `JulesRoleCapabilities` (34),
`GeminiProjectObserverService` (31), `ScopedBufferAppender` (30), `RoleAdviceLoopService` (19),
`StackProfile` (15), `ChessService` (0), `GeminiContextCacheManager` (161).

**Как возникла ложь — это важнее самого числа.** Заодно снято утверждение, что `LeverStage` и `Verdict`
«являются механизмами и названы выше»: замер даёт `LeverStage` — 2 упоминания, 0 записей; `Verdict` —
17 упоминаний, 0 записей. Я считал описанным всё, чьё имя встречается в тексте. Упоминание в прозе — факт о
том, что я это слово написал, а не о том, что механизм разобран по обязательной форме. Ровно этот подлог —
метка вместо предмета — уже стоил ложного «все 149 механизмов описаны». Признак «описан» отныне machine-checkable:
запись существует, если есть строка имени и `*Связи:*` под ней; всё прочее — упоминание.

Каждое имя, названное в этом файле, сверено с исходниками: выдуманных механизмов здесь нет. Но названо —
не значит описано, и разница теперь измеряется, а не утверждается.

Полное состояние законов и то, какие из них держатся, — в `ENGINEERING_PHILOSOPHY_ACTION_PLAN.md`.
Здесь описано **что есть**; там — **каким это обязано быть**.

---

## 2026-09-10 Codex strict family record: task quality gates and verdict surfaces

*Живое, 10 сентября 2026, Codex: factory-wide tact — task quality gates and verdict surfaces, без правки кода.* Этот такт закрывает не один класс, а связанное семейство заслонов. `GateCheck`, `GateStage` and `GateResult` были названы как части механизма; `QualityGateController` and `VerdictController` оставались только упомянутыми поверхностями. Здесь они описаны вместе, потому что один вопрос проходит через все эти части: **имеет ли фабрика право считать утверждение проверенным, и что именно было проверено**.

**`GateCheck`**, **`GateStage`**, **`GateResult`**, **`GateOrchestrator`**, **`BaseQualityGate`**, **`BackendContractGate`**, **`DesignExcellenceGate`**, **`VerificationEvidenceGate`**, **`EpistemicLayerInvariantGate`**, **`QualityGateController`**, **`VerdictController`**, **`VerdictGate`** — семейство заслонов качества и заявленной готовности.
*Связи:* `TechnicalLeadCompiler` вызывает `GateOrchestrator.runTaskSpecGate` сразу после создания задачи; `ClaimService` вызывает `GateOrchestrator.runQualityGate` при допуске результата к review; Spring собирает реализации `GateCheck` через `List<GateCheck>`; проверки читают `TaskEntity`, `RoleEntity`, `ProjectEntity`, `FeatureEntity`, `JulesSessionEntity`, PR diff and GitHub file contents; оркестратор пишет `TaskEntity.qualityGatePassed`, `TaskEntity.qualityGateReport` and `TaskGateLogEntity`; `TaskEntity.isVerifiedForDelivery` читает stage-specific denominator; `QualityGateController` публикует scalar DPMO view; `VerdictController` публикует `VerdictReconciliation`; `VerdictGate` через `SystemSettingsService` ограничивает только заявленную готовность проекта.

*Идеальная форма:* заслон никогда не превращает молчание в разрешение. Каждый результат обязан нести предмет проверки (`TASK_SPEC` или `IMPLEMENTATION_RESULT`), имя проверки, причины отказа, число реально применённых проверок по стадии и источник свидетельства. Задачная проверка не должна становиться доказательством доставки, доставка не должна опираться на self-attested payload when real PR evidence exists, and project-readiness verdict must be monotone: it may subtract permission from a readiness claim, never add it.

*Граница:* `GateOrchestrator` отвечает за task-level specification/result checks and their persisted report; он не принимает проект, не решает клиентскую поставку and does not own Six Sigma aggregates. `VerdictGate` отвечает only for the proposition “the factory may claim this project is ready”; it intentionally does not gate project acceptance or task dispatch. `QualityGateController` is an observation surface, not a decision owner. `VerdictController` is a read-only surface over the lattice, not an actuator.

*Входы:* task payload (`lean_value`, `dod`, `acceptance_criteria`/`acceptanceCriteria`), role tag and active flag, project repo URL/build phase, feature epistemic layer and file scope, Jules session/PR URL/status, GitHub PR diff/head ref/file bytes, verification report JSON, `SystemSettingsService` flags `verdict_gating_enabled` and `verdict_gating_project_slug`, and existing `qualityGateReport` when readers ask whether delivery was actually verified.

*Выходы:* `qualityGatePassed`, JSON `qualityGateReport` with `passed`, `stages`, `applicableChecksByStage` and per-check failures, rows in `task_gate_logs`, retry/queue/block decisions in `ClaimService` after a failed gate, `/api/quality-gate/defect-rate` aggregate, `/api/projects/{projectId}/verdict` readout, and `VerdictGate.Decision` with `applied` plus reasons.

*Владельцы истины и состояния:* source of gate applicability is `GateCheck.stage/supports/isBuildPhaseExempt`; persisted task verdict is `tasks.quality_gate_passed` and `tasks.quality_gate_report` from migration `V12`; historical gate evidence is `task_gate_logs` from `V28`; external implementation evidence is GitHub PR diff/content through `GitHubPullRequestService`; project-readiness lattice truth belongs to `VerdictReconciliation`, `Verdict`, `Judgement` and the five `VerdictLayer` implementations; rollout scope belongs to `system_settings` through `SystemSettingsService`.

*Инварианты:* empty applicable check list is never delivery verification; stage count is per stage, not total; `supports()` decides applicability, so a check must not encode “not applicable means passed”; build phase may exempt mechanical polish gates, not structural layer boundaries or QA evidence; real PR/file evidence outranks payload claims; `VerdictGate` declines on flag off, blank scope, other project, empty lattice or reconciliation failure; if it applies, it can only make a readiness claim stricter.

*Сильная форма сейчас:* `GateOrchestrator` records `applicableChecksByStage` and `stages`; `TaskEntity.isVerifiedForDelivery` requires an implementation-stage count, so a spec gate cannot masquerade as delivery proof; tests pin empty-denominator behavior and positive-denominator behavior. `BackendContractGate`, `DesignExcellenceGate` and `VerificationEvidenceGate` use real PR evidence instead of implementer self-report. `VerdictGateTest` pins monotonicity, scoped rollout, empty-lattice stand-aside, abstention, refusal reasons and outage-safe decline.

*Слабая/неидеальная форма сейчас:* implementation-reachability is not closed by docs: the historical measurement says the gate instrument applied to zero of 365 tasks on `test-fiftieth`, while criterion judgement handled 127; that must be remeasured before claiming factory-wide coverage. `BaseQualityGate` as an outer class is only a wrapper, while its static nested gates are live `@Component` checks; future text must not call the whole file dead without naming that distinction. `QualityGateController.getDefectRate` still computes its own all-task aggregate instead of delegating to the shared Six Sigma/report-corpus owner. `EpistemicLayerInvariantGate` still depends on task/file-scope facts and can pass when session or feature evidence is absent; before implementation, decide whether that absence is non-applicability or an explicit abstention/refusal case.

*Опровержение:* create or identify a task whose role has no implementation-result gate and show `isVerifiedForDelivery()==true`; if that happens, the denominator invariant failed. Create a UI/backend/QA PR with self-reported payload evidence but missing real PR evidence; if the gate passes, reliability-chain evidence failed. Set verdict gating to a project and make the lattice refuse while the construction verdict permits; if `VerdictGate` returns `PERMIT` or `applied=false`, monotonic gating failed. Compare `/api/quality-gate/defect-rate` with the shared Six Sigma report-corpus computation; disagreement proves duplicate aggregate ownership.

*Критерий закрытия:* this family can be called ideal only after a fresh reachability measurement names which task/status paths call `runQualityGate`, `QualityGateController` uses the same aggregate owner/denominator as the Six Sigma report corpus, `BaseQualityGate` is documented or renamed as a wrapper around live nested components, and the epistemic-layer gate has an explicit rule for absent feature/session/file evidence. Until then the mechanism is partially strong, not ideal.

*Свидетельства такта:* `git log -1 --oneline` showed `cfc0bdc`; `nl -ba` on `GateCheck.java`, `GateStage.java`, `GateResult.java`, `GateOrchestrator.java`, `BaseQualityGate.java`, `BackendContractGate.java`, `DesignExcellenceGate.java`, `VerificationEvidenceGate.java`, `QualityGateController.java`, `VerdictController.java`, `VerdictGate.java`, `TaskEntity.java`, `TaskGateLogEntity.java`, `TaskGateLogRepository.java`, `TechnicalLeadCompiler.java`, `ClaimService.java`; `grep -RIn "runTaskSpecGate|runQualityGate|isVerifiedForDelivery|qualityGateReport|TaskGateLog" src/main/java`; focused reads of `GateOrchestratorIntegrationTest`, gate unit tests and `VerdictGateTest`; philosophy rows `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061`.

*Текущий статус:* strict family record filled. Mechanism is not ideal as implemented; its best parts are strong, but coverage/reachability and aggregate ownership remain open.

*комментарий для Антигравити:* механизм не идеален. Не правь один gate, endpoint or enum отдельно. Preserve three subjects first: task specification, implementation-result verification, and project-readiness claim. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` to keep real PR/file evidence above self-attestation; apply `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` so task-stage checks cannot become project-readiness or delivery proof; use `ACP-061 Hoare Triple Review` before touching code. First remeasure reachability and denominator, then unify `QualityGateController` with the Six Sigma report-corpus owner, and only then decide the epistemic absent-evidence rule.

*Живое, 11 сентября 2026, Antigravity (L2): такт 2 — объединение QualityGateController и SixSigmaAuditService.*
Устранено архитектурное расхождение и дублирование в расчёте агрегатов Quality Gate:
1. `QualityGateController.getDefectRate` больше не выполняет собственный `taskRepository.findAll()` и дублирующий цикл подсчёта; он полностью делегирован единому владельцу истины `SixSigmaAuditService.computeQualityGateDefectRate(projectId)`.
2. Ликвидирован дефект ложного «да» и NullPointerException при отсутствии поля `passed` в проверке: реализована 4-значная логика Белнапа / 3 исхода (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012). Проверки без поля `passed` больше не крашат контроллер и не считаются пройденными по умолчанию (`asBoolean(true)` устранено), а явно классифицируются и учитываются в поле `undetermined`.
3. Подъём задач переведён на точечные derived finders: `findByProjectIdAndQualityGateReportIsNotNull(projectId)` и `findByQualityGateReportIsNotNull()`. Устранён `findAll()` и фильтрация в памяти в `computeCtqBreakdown`.
4. Создан `QualityGateControllerTest` (проверка делегирования и контракта), обновлён `SixSigmaAuditServiceTest` (проверка трёхзначного исхода `undetermined` и заслон `never().findAll()`). Все 18 тестов сервиса и контроллера пройдены.
*Философский заслон:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` (D010).


