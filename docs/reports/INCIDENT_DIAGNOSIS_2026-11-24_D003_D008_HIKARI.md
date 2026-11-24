# L1 Эпистемическая диагностика инцидентов фабрики (2026-11-24)

**Аудитор:** L1 Менеджер-философ (`remotecli2`)  
**Основание:** Срочная директива Оператора от `2026-11-24 15:17:15 UTC` ([OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md)) и [ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md)  
**Эмпирическая база:** живые логи `eneikproductionsys-backend-1`, архив `data/logs/backend.log*` (20–24 ноября 2026), миграции Flyway `V3`/`V4`/`V6`/`V145`, исходный код `src/main/java/com/eneik/production/**`, `docker-compose.yml`, `application.properties`.

---

## 1. Инцидент №1: `SQL Error: 22001` в `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008 / D003 / D002)

### 1.1. Эмпирические факты (анти-зеркало Витгенштейна, D013)
- **Живое воспроизведение прямо сейчас (`2026-11-24T18:00:45.532Z`):**
  ```
  Value too long for column "SOURCE_ROLE_TAG CHARACTER VARYING(16)": "'KARL_POPPER_01_FALSIFICATION_HARNESS' (36)"
  Continuous Orchestration: Karl Popper D008 Main CI recovery failed for project d64a3b6b-667e-4cda-aa69-8a7bf42b890f
  ```
- **Масштаб в логах:** `894` отказа на `2026-11-20` + `177` отказов в `2026-11-24.0.gz` + продолжается на каждом 60-секундном тике оркестратора.
- **Точка дефекта в коде:** `ContinuousOrchestrationService.java:1098` передаёт имя философского паттерна (36 символов):
  `wishlist.setSourceRoleTag("KARL_POPPER_01_FALSIFICATION_HARNESS");`
- **Схема БД:** `V4__create_wishlist.sql:8` задаёт `wishlist.source_role_tag VARCHAR(16)` (и `V6__project_wishlist_flow_extensions.sql:33` — `wishlist_items.source_role_tag VARCHAR(16)` с `FK REFERENCES roles(tag)`). Ни одна из последующих миграций (`V5..V145`) не расширяла колонку. В `WishlistEntity.java:21` отсутствует `@Column(length = ...)` и валидация до записи.
- **Скрытый дефект-двойник, выявленный аудитом L1:**
  `FalsificationCycleService.java:1052–1061` записывает в `wishlist.source_role_tag` список нескольких ролей через запятую (`distinctRoles`, например `"BARCAN-TAG-01, BARCAN-TAG-06"` — 28 символов), и `TechnicalLeadCompiler.java:929–951` (`doctrinePressure` / `singleDoctrinePressure`) явно поддерживает парсинг comma-separated `BARCAN-TAG-XX`. При текущей длине `VARCHAR(16)` любой мульти-ролевой философский кластер также падает с `SQL Error: 22001`.

### 1.2. Канонический онтологический контракт `SOURCE_ROLE_TAG` (решение L1)
- **Философское основание (RAG-корпус):**
  - `D002` (Гилберт Райл — категориальная ошибка, `CATEGORY_ERROR_SCAN`): смешение категории **онтологической роли** (`roles.tag`) и категории **философского паттерна** (`KARL_POPPER_01_FALSIFICATION_HARNESS`).
  - `D003` (Уиллард Куайн / Сабина Леонелли — `RIGID_API_REFERENT`, `HOLISM_IMPACT_MAP`): разрыв между контрактом БД (`VARCHAR(16)`), производителями (`ContinuousOrchestrationService`, `FalsificationCycleService`) и потребителем (`TechnicalLeadCompiler.doctrinePressure`).
  - `D008` (Карл Поппер — `KARL_POPPER_01_FALSIFICATION_HARNESS`): заслон восстановления `main` CI обязан быть проверен реальной вставкой в H2 с полными миграциями Flyway, а не моком репозитория.
- **Канон поля `wishlist.source_role_tag`:**
  1. **Семантика:** Хранит **исключительно канонический идентификатор роли `BARCAN-TAG-XX`** (`BARCAN-TAG-00`..`BARCAN-TAG-12` из `roles.tag`), либо (для мульти-ролевых кластеров `philosophical_falsification`) отсортированный дедуплицированный comma-separated список `BARCAN-TAG-XX, BARCAN-TAG-YY`, либо `NULL` при `source = client`.
  2. **Запрет:** Категорически запрещено записывать в `source_role_tag` имена философских паттернов (`KARL_POPPER_01_FALSIFICATION_HARNESS`), произвольные строки или имена сервисов. Имя паттерна и код дефекта хранятся в `content`, `dod` и/или `toc_constraint_ref`.
  3. **Канонический тег для D008 Main CI Recovery (`ContinuousOrchestrationService.recoverFailingMainCi`):** **`BARCAN-TAG-06`** (`QA Automation` / `BARCAN-TAG-06_DEONTIC-CONSISTENCY`, канонический владелец `KARL_POPPER_01_FALSIFICATION_HARNESS` по `V3__core_agency_schema.sql:68` и `docs/FACTORY_MECHANISMS.md:1150`; в `TechnicalLeadCompiler.singleDoctrinePressure` получает максимальное доктринальное давление `0.25`).
  4. **Контракт схемы (D003):** Миграция **`V146__widen_wishlist_source_role_tag.sql`** расширяет `wishlist.source_role_tag` до `VARCHAR(128)` (достаточно для всех 13 тегов через запятую при сохранении компактного индекса; `wishlist_items.source_role_tag VARCHAR(16)` с FK на `roles(tag)` остаётся строгим одиночным тегом).
  5. **Единая точка контроля (Charter Invariant 10 / `RIGID_API_REFERENT`):** Входная валидация/нормализация `sourceRoleTag` (regex `^BARCAN-TAG-(0[0-9]|1[0-2])(,\s*BARCAN-TAG-(0[0-9]|1[0-2]))*$`, проверка длины `<= 128`) до обращения к БД, чтобы любое нарушение контракта отсекалось явно до `SQL 22001`.

---

## 2. Инцидент №2: Исчерпание `HikariPool-1` и каскадный коллапс потоков (D005 / D013 / D010)

### 2.1. Эмпирическая реконструкция сбоя (20–24 ноября 2026)
Форензика архивов `data/logs/backend.log.2026-11-20..24*` вскрыла **полную каузальную цепочку** (более `105 000` таймаутов `HikariPool-1` за 4 дня до рестарта `2026-11-24 10:24 UTC`):

1. **Точный триггер тотальной блокировки пула (`2026-11-20T15:54:00.009Z`):**
   - За 12 секунд до первого таймаута `HikariPool-1` (`15:54:12.077Z`) JVM выдала **`122` ошибки**:
     `java.lang.OutOfMemoryError: unable to create native thread: possibly out of memory or process/resource limits reached`
     непосредственно в `com.zaxxer.hikari.pool.HikariPool.addBagItem(HikariPool.java:332)` (из `StrandedDesignCycleSweepService.sweep`).
   - После отказа создания нативного потока `HikariPool-1` (при `mem_limit: 1g`, текущем RSS `793 MB` и `79` потоках сразу после свежего рестарта) пул потерял способность восполнять соединения и оставался мёртвым 4 суток подряд (`9 374` таймаутов 20.11 → `25 178` 21.11 → `25 207` 22.11 → `25 216` 23.11 → `20 245` 24.11).
   - **Найденные утечки нативных потоков / `HttpClient` в коде:**
     - `ProjectFlowService.java:2288`: создаёт новый `HttpClient.newBuilder()...build()` **на каждый вызов метода** (каждый `HttpClient` порождает собственный `SelectorManager` + внутренний пул потоков и не закрывается).
     - `DefaultPlaywrightVisionHarness.java:64`: неограниченный `Executors.newCachedThreadPool(...)` без lifecycle-остановки.
     - `LocalAgyCliClient.java:270–271`: создание сырых `new Thread(...)` на каждый запуск CLI вместо ограниченного executor-пула.
     - 12 разрозненных экземпляров `HttpClient` по сервисам, каждый со своим нативным `SelectorManager`.

2. **Хронические длинные транзакции (>30 с), подтверждённые стеками `Connection leak detection`:**
   - `ClientDeliverableReadinessService.deleteValuelessEpics()` (`@Scheduled(cron = "0 20 * * * ?") @Transactional`, строки 909–910): **главный хроник** — `20x` в текущем логе, `16x` 20.11, `3x` 24.11 (каждый час в `:20:30` удерживает writable-соединение >30 с на перебор проектов, `authorize`, загрузку всех вишлистов и `listEpicDiagnostics`).
   - `TrustSnapshotService.captureAndBackfillSnapshots()` (`@Scheduled @Transactional`, строки 56–57): `9x` в текущем логе, `6x` 20.11, `2x` 24.11 — держит одну транзакцию на весь `operationalTruthService.build(projectId)` + `backfillResolvedOutcomes()`.
   - `ProcessControlService.recomputeForProject(...)` (`@Transactional`): `6x` в текущем логе, `4x` 20.11, `2x` 24.11 — держит транзакцию на N+1 запросы (`allFeatureIds.stream().flatMap(fId -> taskRepository.findByFeatureId(fId).stream())`, строки 164–167) и расчёт u-карт.
   - `AutoMergeService.executeMerge(PrReviewEntity)` (`@Transactional`, строка 1056): **критический дефект границ транзакции** — внутри `@Transactional` выполняет синхронные сетевые запросы к GitHub API (`fetchPullRequestByNumber`, `mergeableState`, `pullRequestChecks`, `PUT /pulls/{n}/merge`, `handleMergeConflict`), удерживая соединение БД на всё время внешнего сетевого I/O и вызывая `STALL_BOTTLENECK` на `AUTOMERGE_PROCESSING` (`818x` 24.11).
   - Дополнительно 24.11 зафиксированы утечки >30 с в `ProjectFlowService.addWishlistItem` (4x), `TechnicalLeadCompiler.createTaskFromWishlist` (1x), `OperationalPolicyService.snapshot` (1x), `ProjectOperationalContextService.build` (1x), а в `application.properties` **не отключён `spring.jpa.open-in-view=false`** (по умолчанию `true` — удерживает JDBC-соединение на всю длительность HTTP-запросов).

### 2.2. Философский вердикт (RAG-корпус)
- **`D005` (Лара Бучак — `REU / Tail Risk Block`, Уиллард Куайн — `DECISION_EXPECTED_LOSS`):** Внешний watchdog (`eneik-factory-watchdog.timer`) — лишь паллиатив; хвостовой риск исчерпания нативных потоков и пула Hikari при `DB_POOL_SIZE=12` + `SCHEDULING_POOL_SIZE=10` + `mem_limit=1g` должен быть исключён конструктивно (вынос I/O из транзакций, декомпозиция батч-транзакций по проектам, единый/переиспользуемый `HttpClient`, явное `spring.jpa.open-in-view=false`).
- **`D013` (Людвиг Витгенштейн — `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, Уэсли Сэлмон — `CAUSAL_PROCESS_TRACE`):** Живая телеметрия `/actuator/health` остаётся `UP` даже при системном сбое D008 и скрытых утечках >30 с; верификация закрытия инцидента обязана опираться на ноль записей `SQL Error: 22001`, ноль `Connection leak detection` и ноль `unable to create native thread` в логах живого рантайма.
- **`D010` (Элвин Голдман — `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`):** Ликвидация N+1 в `ProcessControlService.acquireEvidencePacket` (`findByFeatureIdIn`) и разрыв удержания БД-соединения на сетевом I/O в `AutoMergeService.executeMerge`.

---

## 3. Ответственные механизмы Фабрики (`docs/FACTORY_MECHANISMS.md`)

| Контур | Дефект (RAG) | Ответственные механизмы и классы |
|---|---|---|
| **Контракт БД и валидация `SOURCE_ROLE_TAG`** | `D003`, `D002`, `D006` | `FlywayMigrationValidationTest` + миграции `src/main/resources/db/migration/` (`V4`, `V6`, новая `V146`), `WishlistEntity`, `WishlistService`, `ProjectFlowService` (приём вишлистов), `FalsificationCycleService` (мульти-ролевые кластеры), `TechnicalLeadCompiler` (`doctrinePressure`) |
| **D008 Main CI Recovery** | `D008`, `D002` | `ContinuousOrchestrationService` (`isMainCiRecoveryNeeded`, `recoverFailingMainCi`, `extractFailingMainCiTests`), `LocalVerificationRunner` |
| **Целостность пула `HikariPool-1`, транзакционные границы и нативные потоки** | `D005`, `D010`, `D013` | `AutoMergeService` (`executeMerge` / `AUTOMERGE_PROCESSING`), `ClientDeliverableReadinessService` (`deleteValuelessEpics`), `TrustSnapshotService` (`captureAndBackfillSnapshots`), `ProcessControlService` (`recomputeForProject` / `acquireEvidencePacket`), `ProjectFlowService` (строка 2288 `HttpClient`), `DefaultPlaywrightVisionHarness`, `application.properties` (`spring.jpa.open-in-view=false`), `docker-compose.yml` |

---

## 4. Ресурсная гигиена хоста (замер `2026-11-24 18:03 UTC`)
- **Диск `/`:** `26G / 38G` (**71%**, превышает порог `< 70%` из `GEMINI.md` на 1%; `docker builder prune -f` и `docker image prune -f` высвободили `0B`). В `data/logs/` накоплено `426 MB`, включая 8 несжатых `.tmp`-архивов ротации logback (`2026-11-13..23`), оставшихся после сбоев OOM/рестартов.
- **Память хоста:** `3.7 GiB` RAM (`1.5 GiB` available), swap `2.1 / 4.0 GiB`. Контейнер `eneikproductionsys-backend-1`: `UP 8h`, `VmRSS = 793 MB / 1024 MB`, `Threads = 79`, `/actuator/health = UP`.
- **Продукты:** `dmitryefremov-backend` (:18080) — `{"status":"UP"}`; `eneikproductionsys-backend-1` (:8080) — `UP`.
