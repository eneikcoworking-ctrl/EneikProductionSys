# Ответы Клода

## 2026-09-08 первое слово, до первого вопроса

Очередь готова: `docs/ANTIGRAVITY_QUEUE.md`, двадцать пунктов, упорядочены по весу и вреду.

**Начинать с пункта 1** — свод состояния поднимает всю базу раз в минуту. Причина именно такого начала: пока
бэкенд держится у гигабайта из отведённого гигабайта, всякая другая починка идёт в тесноте, а проверить её
замером нельзя — память искажает всё.

Порядок первых трёх пунктов не случаен и менять его не стоит: они об одном и том же — привычке спрашивать у
хранилища строки вместо счёта. Починив первый, второй и третий делаются тем же приёмом.

**Замер на 8 сентября, от которого считать улучшение:** контейнер бэкенда занимает 956 МБ из 1024
(99,2% предела), собственной памяти процесса 933 МБ, файлового кэша 75. Оркестрация — 12147 оборотов за
сутки. Если после починки первого пункта занятая память не сдвинулась, значит причина не в нём, и это надо
записать, а не искать оправдание.

## 2026-09-08 07:16Z handoff: обязательный комментарий для Антигравити

подтверждаю / вместо этого: формат обмена расширен; каждый следующий ответ должен содержать строку `комментарий для Антигравити`.
замечание: комментарий не заменяет замер и проверку; он объясняет, как Антигравити чинить или почему править не надо.
комментарий для Антигравити: если механизм требует правки — пиши конкретный следующий шаг и применимую философию/паттерн; если по опровержению механизм держится, пиши ровно: «считаю механизм идеальным».

## 2026-09-08 12:17Z Codex: SystemStatusService session-count slice

подтверждаю / вместо этого: подтверждаю пункт 1; Codex закрыл только срез `julesSessions` внутри `SystemStatusService`, не весь механизм.
замечание: `julesSessions(null)` перешёл на `count()`/`countByStatus(...)`, проектный путь перешёл на `findByTaskIdIn(...)`; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: продолжать пункт 1 по одному count-only срезу. Если секция возвращает только счётчики, не поднимать строки через `findAll()`: добавить репозиторный count/aggregate и тест, что глобальный или проектный путь не вызывает `findAll()`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 12:29Z Codex: SystemStatusService conflict-DPMO project slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл только проектный срез `conflictDpmo(projectId)` внутри `SystemStatusService`.
замечание: проектный путь больше не читает все `PrReview`, `TaskConflict` и `JulesSession` перед фильтрацией; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: если берёшь следующий срез SystemStatusService, сначала проверь, что секция действительно отвечает малым summary, а не обязана вернуть список объектов. Для summary-секции перенеси фильтр в repository query/derived finder и добавь Mockito-тест `never().findAll()`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 12:44Z Codex: SystemStatusService Linear completeness slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл срез `linearCompleteness` внутри `SystemStatusService`.
замечание: метод больше не вызывает `taskRepository.findAll()` перед фильтром Linear, а читает только задачи с `linearIssueId`; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: следующий срез выбирай так же: если итоговая секция строит список, допустимо читать ровно этот список, но нельзя начинать с полной таблицы и потом отбрасывать почти всё. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService operational blockers session slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл срез `operationalBlockers`, где открытые сессии закрытого проекта считались через общий подъём всей таблицы.
замечание: теперь для каждого проекта сессии читаются по id его задач через `findByTaskIdIn(...)`; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: этот механизм ещё не идеален: в `SystemStatusService` остаются другие `findAll()`-пути. Следующий безопасный ход — брать только один оставшийся путь и сначала решить, является ли он summary-вопросом или настоящим списком для UI. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService global conflict-review count slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл только глобальный срез merged-review внутри `conflictDpmo(null)`.
замечание: all-time и last-7-days merged PR review теперь считаются через `countByMergedTrue()` и `countByMergedTrueAndCreatedAtAfter(...)`; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: `conflictDpmo(null)` всё ещё поднимает все `TaskConflict`, потому что строит Pareto и active list. Следующий безопасный ход — отделить агрегаты конфликтов от списка активных конфликтов, добавив репозиторные count/group queries и оставив список только там, где UI действительно показывает записи. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService global conflict aggregate slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл глобальный срез `TaskConflict` внутри `conflictDpmo(null)`.
замечание: global conflict DPMO больше не вызывает `taskConflictRepository.findAll()`: total/last-7-days идут через count, Pareto — через grouped queries, active list — через active-only finder; `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: в `SystemStatusService` остаются другие `findAll()`-пути вне `conflictDpmo`. Следующий безопасный ход — взять один оставшийся путь, где UI возвращает summary, и перенести ровно его вопрос в repository count/finder с Mockito-заслоном `never().findAll()`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService project accounts slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл только проектный срез `accounts(projectId)` внутри `SystemStatusService`.
замечание: проектный путь больше не вызывает `accountRepository.findAll()` и не фильтрует все аккаунты в памяти; он использует существующий `findAvailableForProjectOrderByNameAsc(projectId)`, который несёт тот же predicate. `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: глобальный `accounts(null)` всё ещё отдаёт список `items`, поэтому его нельзя механически заменить счётчиками без решения по UI. Следующий безопасный ход — брать summary-only путь, а если путь отдаёт список, сначала доказать, что список действительно нужен. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService project quality-gate slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл только проектный срез `qualityGate(projectId)` внутри `SystemStatusService`.
замечание: проектный путь больше не вызывает `taskRepository.findAll()` и не фильтрует все задачи в памяти; он использует `findByProjectIdOrderByCreatedAtDesc(projectId)`. `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: глобальный `qualityGate(null)` всё ещё читает все задачи, потому что разбирает `qualityGateReport` и строит defect items. Следующий безопасный ход — проверить, можно ли читать только задачи с непустым `qualityGateReport`; если да, добавить finder и Mockito-заслон `never().findAll()`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: SystemStatusService global quality-gate report slice

подтверждаю / вместо этого: продолжаю пункт 1; Codex закрыл только глобальный срез `qualityGate(null)` по задачам с quality-report.
замечание: глобальный путь больше не вызывает `taskRepository.findAll()` ради quality-gate свода; он читает только задачи с непустым `qualityGateReport` через `findByQualityGateReportIsNotNull()`. `SystemStatusServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: в `SystemStatusService` остаются `tasks(null)` и `emsMetrics(null)`, где глобальный путь всё ещё читает все задачи. Следующий безопасный ход — проверить, является ли `tasks(null)` чистым summary; если да, считать статусы в repository с сохранением исключения carrier-задач. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: GeminiContextService embedding-failure guard

подтверждаю / вместо этого: после блокера по `tasks(null)` Codex начал пункт 2 очереди, но закрыл только failure-path срез `GeminiContextService.retrieveFiltered`.
замечание: если query embedding не построился, retrieval теперь возвращает пустой результат до `repository.findAll()`; успешный путь всё ещё читает корпус целиком и не считается закрытым. `GeminiContextServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: главный успешный путь `retrieveFiltered` всё ещё поднимает все chunks и парсит все embeddings. Следующий безопасный ход — ввести vector/projection read без `content`, ранжировать по embedding, а `content` брать только для выбранных top-k id; до замены сравнить выдачу старого и нового пути на фиксированном наборе. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: GeminiContextService top-k content fetch slice

подтверждаю / вместо этого: продолжаю пункт 2; Codex закрыл только срез «не читать content всех chunks» в `GeminiContextService.retrieveFiltered`.
замечание: successful retrieval теперь ранжирует projection `id/sourceType/sourceRef/embedding/embeddingDims` через `findAllVectorRows()`, а `content` читает только для выбранных top-k id через `findAllById(...)`; `GeminiContextServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: все embeddings всё ещё читаются и парсятся в JVM, потому без vector index или предварительно нормированной формы similarity остаётся O(N). Следующий безопасный ход — сохранить поведение выдачи на фиксированном корпусе и отдельно измерить, даёт ли H2-compatible нормализованный столбец/таблица возможность сократить parse cost. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: GeminiContextService source-type vector scope

подтверждаю / вместо этого: продолжаю пункт 2; Codex закрыл только source-type срез `retrieveRelevantContextBySourceTypes`.
замечание: вызов по source types больше не берёт все vector rows и не фильтрует их в JVM; добавлен repository projection query `findVectorRowsBySourceTypeIn(...)`. `GeminiContextServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: unscoped retrieval и role/sourceRef-prefix retrieval всё ещё читают все embeddings. Следующий безопасный ход — вынести prefix/sourceRef фильтры в repository projection queries, но не менять ранжирование; затем отдельным замером решать parse/vector-index стоимость. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: GeminiContextService sourceRef-prefix vector scope

подтверждаю / вместо этого: продолжаю пункт 2; Codex закрыл только простой sourceRef-prefix срез `retrieveRelevantContext(query, topK, sourceRefPrefix)`.
замечание: вызов по sourceRef prefix больше не берёт все vector rows и не фильтрует их в JVM; добавлен repository projection query `findVectorRowsBySourceRefStartingWith(...)`. `GeminiContextServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: unscoped retrieval и составные role/prefix/type фильтры всё ещё читают все embeddings. Следующий безопасный ход — вынести составной фильтр `buildPhilosopherPatternContext` в repository projection query по `(sourceType, sourceRefPrefix)`, не меняя scoring. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-08 Codex: GeminiContextService philosopher-pattern vector scope

подтверждаю / вместо этого: продолжаю пункт 2; Codex закрыл только срез `buildPhilosopherPatternContext`.
замечание: role philosopher-pattern retrieval больше не берёт все vector rows и не фильтрует `(sourceType, sourceRefPrefix)` в JVM; добавлен repository projection query `findVectorRowsBySourceTypeAndSourceRefStartingWith(...)`. `GeminiContextServiceTest` пройден в Maven-контейнере.
комментарий для Антигравити: механизм ещё не идеален: unscoped retrieval и `buildProductWorkerContextBlock(role, query)` всё ещё читают все embeddings. Следующий безопасный ход — вынести product-worker predicate в один явный repository query, сохранив точное условие sourceType/sourceRef/common-charter исключений. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-09 Codex: SystemStatusService full mechanism record tact

подтверждаю / вместо этого: прежний стиль Codex был неверным — частные кодовые срезы уменьшали нагрузку, но не закрывали механизм. Новый такт заполнил запись `SystemStatusService` как целый механизм: идеальная форма, граница, входы/выход, владельцы истины, инварианты, сильная/текущая форма, опровержение и критерий закрытия.
замечание: код не менялся; Java, миграции, деплой и рестарты не трогались. Текущий статус механизма — не идеален, потому что 16 секций ещё нужно классифицировать, а оставшиеся `findAll()` должны быть доказаны как bounded UI list либо заменены агрегатом/проекцией.
комментарий для Антигравити: следующий такт по `SystemStatusService` должен заполнить таблицу классификации 16 секций, а не править очередной срез. Для каждой секции указать класс ответа, владельца истины, допустимый источник данных, опровержение и критерий закрытия. Философия: `BARCAN-TAG-00_CODE-GUARDIAN`, Людвиг Витгенштейн, publication anchor `Philosophical Investigations - language-games, meaning as use, private-language argument`, pattern `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, family `ANTI_MIRROR_TELEMETRY`, defect `D013 Runtime drift`.

## 2026-09-09 Codex: SystemStatusService 16-section classification

подтверждаю / вместо этого: заполнена таблица классификации всех 16 секций `SystemStatusService`: какие секции являются owned snapshot, summary, bounded UI list, secondary projection или blocker list, кто владелец истины и что опровергает каждую секцию.
замечание: код не менялся. Запись механизма стала пригодной для будущей реализации без случайных срезов; сам механизм в коде не объявлен идеальным.
комментарий для Антигравити: `SystemStatusService` не идеален в реализации, но запись теперь показывает точные границы. Следующие факты для добавления перед кодом: доказанный carrier-source для `tasks(null)`, bound/limit для `accounts` и `conflictDpmo.activeConflicts`, агрегатный путь для global `emsMetrics`, и опровержение `systemHealth.status=ok` при stale progress. Философия: `BARCAN-TAG-00_CODE-GUARDIAN`, Людвиг Витгенштейн, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, family `ANTI_MIRROR_TELEMETRY`, defect `D013 Runtime drift`.

## 2026-09-09 Codex: GeminiContextService full mechanism record

подтверждаю / вместо этого: заполнена целая запись `GeminiContextService`, а не новый vector-scope срез. В запись добавлены идеальная форма, граница, входы/выходы, связи, инварианты, public surface classification, сильная/текущая форма, опровержение и критерий закрытия.
замечание: код не менялся. Запись различает hot retrieval, maintenance reindex cleanup и deliberate static prompt-cache corpus, чтобы будущая реализация не сломала взаимодействия.
комментарий для Антигравити: `GeminiContextService` не идеален в реализации. Следующий факт перед кодом — exact repository predicate для `buildProductWorkerContextBlock(role, query)` и fixture-сравнение sourceRefs с текущим поведением. Философия: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Алонзо Чёрч, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`, family `RAG_GROUNDING_CAPSULE`, defect `D014 RAG hallucination`; дополнительно `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, defect `D010 Data lineage loss`.

## 2026-09-09 Codex: full-table read discipline mechanism record

подтверждаю / вместо этого: заполнена запись общего механизма full-table read discipline. Запись запрещает считать уменьшение grep-count успехом и требует классификации каждого `findAll()` по owner/cadence/class/refutation перед любой реализацией.
замечание: код не менялся. Текущий замер после старых срезов: 76 вызовов `.findAll()` в `src/main/java`; это inventory input, не список дефектов.
комментарий для Антигравити: механизм не идеален. Следующий такт — первая страница реестра: классифицировать top-10 `.findAll()` call sites по owner/cadence/class/refutation, начиная с `FalsificationCycleService`, `QualityMetricsController`, `SixSigmaAuditService`, `SystemStatusService`, `ProjectOperationalContextService`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: full-table hotspot owner registry

подтверждаю / вместо этого: Codex продолжил только свою documentation-only работу и добавил первую страницу реестра для общего механизма full-table reads: top-10 hotspot-владельцев, 44 из 76 текущих `.findAll()` вызовов, с cadence/class/refutation на уровне owner.
замечание: код не менялся. Это не объявлено закрытием механизма: сильная форма всё ещё требует line-level registry для всех 76 call sites.
комментарий для Антигравити: механизм не идеален. Следующий такт — перевести первую hotspot-страницу из owner-level в line-level registry: начать с семи строк `FalsificationCycleService`, для каждой указать exact method, repository/table, owner of truth, cadence, allowed/replacement decision, missing cardinality evidence if any, and closure criterion. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: FalsificationCycleService line-level full-table registry

подтверждаю / вместо этого: Codex продолжил только документацию общего механизма full-table reads и раскрыл hotspot `FalsificationCycleService` до семи line-level строк: project reads, role-corpus reads and active-role count, с refutation/closure для каждой.
замечание: код не менялся. Три project reads имеют известный repository predicate; role-corpus reads не объявлены идеальными, пока не записаны cardinality/freshness/order facts.
комментарий для Антигравити: механизм не идеален. Следующий такт — добавить роль-catalogue evidence before code: exact live/fixture role count, whether roles are intended as bounded static reference data, required order for audit prompts, and whether `RoleRepository` needs `findByActiveTrue` / `countByActiveTrue`. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: FalsificationCycleService role-catalogue evidence

подтверждаю / вместо этого: Codex продолжил только документацию общего full-table mechanism и добавил role-catalogue evidence для unresolved `FalsificationCycleService` role reads.
замечание: код не менялся. Зафиксировано: source fixture has 13 roles, runtime rules endpoint answers 200 for all 13 tags, no application/migration deactivation path was found, but direct live active-count is blocked by H2 file lock and role order remains an open mechanism question.
комментарий для Антигравити: механизм не идеален. Не правь role-corpus reads в `FalsificationCycleService`, пока не решён порядок активных ролей; applicable fix is reliable active-role acquisition with source/freshness/order, then `findByActiveTrueOrderByTagAsc`/`countByActiveTrue` or documented static catalogue. Следующий независимый documentation tact: `QualityMetricsController` line-level registry. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: QualityMetricsController line-level full-table registry

подтверждаю / вместо этого: Codex продолжил только документацию общего full-table mechanism и раскрыл `QualityMetricsController` до семи line-level строк для `/api/quality/conflict-dpmo` and `/api/quality/defect-summary`.
замечание: код не менялся. Aggregate paths have clear replacement direction; detail-list paths remain unresolved because no UI/API bound is documented and no frontend consumer was found by grep.
комментарий для Антигравити: механизм не идеален. Не правь `QualityMetricsController` до ответа о contract for `defect-summary.items`: internal diagnostic all-rows, paginated UI data, or obsolete. Применимая поправка — separate aggregate truth from bounded detail truth, then encode counts/projections before implementation. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: SixSigmaAuditService line-level full-table registry

подтверждаю / вместо этого: Codex продолжил только документацию общего full-table mechanism и раскрыл `SixSigmaAuditService` до line-level registry: active-project resolver, onboarding count, quality-report counts, CTQ breakdown and PR/conflict lineage counts.
замечание: код не менялся. Known-safe direction exists for onboarding and quality-report acquisition; active-project fallback/order and `orchestrated` status were moved to `AGY_ASKS.md` instead of guessing.
комментарий для Антигравити: механизм не идеален. Не правь `SixSigmaAuditService.getActiveProjectId()` до ответа про `orchestrated` and deterministic fallback; for counts, separate layer-wide truth from scoped acquisition and preserve review->session->task lineage before implementation. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: ten-tact 1/10 project operational context cluster

подтверждаю / вместо этого: Codex начал десятиактный кластерный проход и заполнил `ProjectOperationalContextService` как связанный selected-project fact-pack mechanism, not as four isolated `findAll()` lines.
замечание: код не менялся. Зафиксированы upstream/downstream consumers, four line-level acquisitions, known repository replacements, refutation fixture and the separate capacity-language mismatch that cannot be hidden by a local query replacement.
комментарий для Антигравити: механизм не идеален. Не правь четыре `findAll()` в `ProjectOperationalContextService` как отдельный срез: сначала сохрани весь selected-project fact-pack contract и докажи, что downstream `AutoMergeService`, `ProjectFlowService`, `DesignShopOrchestrationService` and Google AI resource paths получают те же project-only facts, а account capacity не остаётся частным языком свода. Следующий такт 2/10 — constraint/coherence cluster: `ConstraintIdentificationService`, `EvidenceCoherenceService`, bottleneck/constraint truth and falsification/quality cross-links. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: ten-tact 2/10 constraint coherence cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил связанный constraint/coherence record: `ConstraintIdentificationService`, `BottleneckDetectionService`, `EvidenceCoherenceService` and their shared constraint-truth/account-capacity/reliability boundaries.
замечание: код не менялся. Зафиксированы семь full-table call sites, one adjacent N+1 active-session lineage risk, fixture refutation and what is or is not safe for later authorized implementation.
комментарий для Антигравити: механизм не идеален. Не правь `ConstraintIdentificationService`, `EvidenceCoherenceService` или `BottleneckDetectionService` как отдельные query-cleanups: сначала сохрани общий закон constraint truth — один project-scoped drum, one coherence window, one dispatcher-owned account-capacity semantics — and prove by fixture that bottleneck, buffer and coherence results do not change except for acquisition cost. Следующий такт 3/10 — project flow/orchestration cluster: `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, stranded finalizing sweep, branch garbage collection and lifecycle/event invariants. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для самой ТОС-семантики дополнительно держать `CAUSAL_PROCESS_TRACE`.

## 2026-09-09 Codex: ten-tact 3/10 project flow orchestration cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил lifecycle/orchestration record: `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, `StrandedFinalizingSweepService`, `BranchGarbageCollectorService` and their shared project/session/PR/event boundaries.
замечание: код не менялся. Зафиксированы eight full-table call sites, destructive-path refutations, active-project/operator-directory decisions and which candidates are safe only after fixture protection.
комментарий для Антигравити: механизм не идеален. Не правь `ProjectFlowService`, `ContinuousOrchestrationService`, `AutoMergeService`, `StrandedFinalizingSweepService` or `BranchGarbageCollectorService` как отдельные query-cleanups: сначала сохрани единый lifecycle law — admission creates work, orchestration orders work, automerge/branch-gc reconcile GitHub truth, stranded sweep restores transient claims — and prove by fixture that project/session/PR identities and event transitions stay identical except for acquisition cost. Следующий такт 4/10 — Jules operations cluster: `JulesDispatchService`, `JulesSessionController`, `JulesMonitorController`, `InternalJulesActivitiesProbeController`, `JulesConfigController`, GitHub webhook lineage and session/activity freshness. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для владения частями дополнительно держать `PART_WHOLE_OWNERSHIP`.

## 2026-09-09 Codex: ten-tact 4/10 Jules operations cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил Jules operations record: `JulesDispatchService`, session/admin/monitor/internal controllers and GitHub webhook lineage as one external-spend/session-truth surface.
замечание: код не менялся. Зафиксированы eight full-table call sites, exact lookup/list/admin/webhook contracts, role-catalogue blocker and which candidates are safe only after fixture protection.
комментарий для Антигравити: механизм не идеален. Не правь `JulesDispatchService` or Jules controllers как отдельные query-cleanups: сначала раздели three surface contracts — exact session lookup, bounded/operator session list, and dispatch/webhook lineage — and preserve account-key ownership, active-role catalogue order, token-to-session evidence and API-secret masking. Следующий такт 5/10 — quality gate/process-control cluster: `ProcessControlService`, `QualityGateController`, already-recorded `QualityMetricsController`/`SixSigmaAuditService`, and aggregate-vs-detail contract boundaries. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для отправки во внешний мир дополнительно держать `TELEOSEMANTIC_FEEDBACK` and `PART_WHOLE_OWNERSHIP`.

## 2026-09-09 Codex: ten-tact 5/10 quality gate process-control cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил quality/process-control record: `ProcessControlService`, `QualityGateController`, already-recorded `QualityMetricsController`/`SixSigmaAuditService`, aggregate-vs-detail and u-chart evidence-packet boundaries.
замечание: код не менялся. Зафиксированы three new full-table call sites plus the two existing blockers: `defect-summary.items` contract and active-project fallback.
комментарий для Антигравити: механизм не идеален. Не правь `ProcessControlService`, `QualityGateController`, `QualityMetricsController` or `SixSigmaAuditService` как отдельные query-cleanups: сначала закрепи один quality-measurement contract — aggregate truth separately from bounded detail truth, u-chart evidence packet scoped to one project/epic sequence, and one owner for quality-gate DPMO — then answer existing `AGY_ASKS` on `defect-summary.items` and active-project fallback before implementation. Следующий такт 6/10 — Kaizen/lean/lever/market cluster: `KaizenService`, `FlowMetricsService`, `LeverPromotionService`, `MarketResearchService`, and improvement-signal lifecycle. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для u-chart levels additionally hold `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`.

## 2026-09-09 Codex: ten-tact 6/10 Kaizen lean lever market cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил improvement-signal lifecycle record: `KaizenService`, `FlowMetricsService`, `LeverPromotionService`, `MarketResearchService` and their shared proposal/evidence/flow/lever/market boundaries.
замечание: код не менялся. Зафиксированы six full-table call sites, destructive cross-project cleanup refutation, dead FlowMetrics consumer gap, carrier-project nondeterminism and which candidates are safe only after fixture protection.
комментарий для Антигравити: механизм не идеален. Не правь `KaizenService`, `FlowMetricsService`, `LeverPromotionService` or `MarketResearchService` как отдельные query-cleanups: сначала сохрани один improvement-signal lifecycle — stable proposal identity, scoped duplicate cleanup, live flow-metric consumer, bounded active lever keys, deterministic orchestrator-system carrier project, and normal dispatch/accounting for research tasks — then prove by fixture that proposals, evidence nodes, flow arithmetic, lever stages and market tasks keep their meaning. Следующий такт 7/10 — operational truth/audit/retention cluster: `TrustSnapshotService`, `FlowSpineService`, `OpsAuditorService`, `DeliveryRealityProducerService`, `ProjectEventLogRetentionService`, and retention/freshness invariants. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для scope переходов дополнительно держать `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`.

## 2026-09-09 Codex: ten-tact 7/10 operational truth audit retention cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил operational truth/audit/retention record: `TrustSnapshotService`, `FlowSpineService`, `OpsAuditorService`, `DeliveryRealityProducerService`, `ProjectEventLogRetentionService` and their shared evidence/liveness/retention boundaries.
замечание: код не менялся. Зафиксированы five full-table call sites: four active-only project sweeps and one all-project retention sweep whose defect is cadence/bound proof rather than simple row filtering.
комментарий для Антигравити: механизм не идеален. Не правь `TrustSnapshotService`, `FlowSpineService`, `OpsAuditorService`, `DeliveryRealityProducerService` or `ProjectEventLogRetentionService` как отдельные query-cleanups: сначала сохрани один operational truth retention contract — active-only sweeps share project acquisition, auditor abstain remains visible, delivery/product/carrier scopes stay separated, trust snapshots backfill real outcomes, and log retention cadence matches measured growth without deleting unaccepted history by age. Следующий такт 8/10 — Gemini observer/context cluster: `InternalGeminiObserverController`, already-recorded `GeminiContextService`, Linear sync if it participates in context freshness, and observer grounding contracts. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; additionally hold `PERSISTENCE_SNAPSHOT` and `ANTI_MIRROR_TELEMETRY` for recoverable evidence and self-reporting.

## 2026-09-09 Codex: ten-tact 8/10 Gemini observer context cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил observer/context grounding record: `InternalGeminiObserverController`, already-recorded `GeminiContextService`, `GeminiObserverActionService` boundary, inert `GeminiProjectObserverService`, and Linear participation decision.
замечание: код не менялся. Зафиксированы six current/full-vector/full-entity acquisitions plus explicit deferral of `LinearSyncController` to tact 9 because source grep did not show it participating in context freshness.
комментарий для Антигравити: механизм не идеален. Не правь `InternalGeminiObserverController`, `GeminiContextService` or `LinearSyncController` как отдельные query-cleanups: сначала сохрани один observer grounding contract — retrieved context cites sourceRefs, product-worker retrieval is scoped before ranking, dimension maintenance uses projection evidence, internal diagnostics are authorized/bounded, broken observer endpoints are fixed or removed, and Linear completeness stays in API-edge scope unless it becomes prompt freshness input. Следующий такт 9/10 — dashboard/accounts/API edge cluster: `DashboardController`, `AccountController`, `InternalTaskController`, `LinearSyncController`, and account/task boundary semantics not already closed. Философия: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, Алонзо Чёрч, publication anchor `Lambda calculus and Church's thesis - formal computability`, pattern `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`, family `RAG_GROUNDING_CAPSULE`, defect `D014 RAG hallucination`; дополнительно `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-09 Codex: ten-tact 9/10 dashboard accounts API edge cluster

подтверждаю / вместо этого: Codex продолжил десятиактный кластерный проход и заполнил dashboard/accounts/API-edge record: `DashboardController`, `AccountController`, `InternalTaskController`, `LinearSyncController` and account/task/Linear boundary semantics not already closed.
замечание: код не менялся. Зафиксированы five full-table call sites, raw all-task dump risk, exact Linear lookup by scan, Linear completeness/report boundary, masked account DTO requirement and mutable account/task authority boundary.
комментарий для Антигравити: механизм не идеален. Не правь `DashboardController`, `AccountController`, `InternalTaskController` or `LinearSyncController` как отдельные query-cleanups: сначала сохрани один API-edge/account-task contract — secured mutable account/task routes, masked account DTOs, bounded dashboard/admin lists, exact Linear-id lookup with duplicate policy, Linear completeness as sync metadata not delivery truth, and no raw all-task dump as monitoring API — then prove by fixture that account capacity, active claims, task identity and Linear reports keep their meaning. Следующий такт 10/10 — completion audit cluster: re-run the full `.findAll()` inventory, reconcile against `docs/FACTORY_MECHANISMS.md`, `docs/reports/AGY_NEXT.md`, and `docs/reports/AGY_ASKS.md`, and explicitly report whether any mechanism was missed. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, publication anchor `A Causal Theory of Knowing / Epistemology and Cognition - reliabilism`, pattern `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`; для mutable API additionally hold `RIGHTS_DUTIES_MATRIX` and `LEVEL_OF_ABSTRACTION_LOCK`.
## 2026-09-09 Codex: ten-tact 10/10 completion audit cluster

подтверждаю / вместо этого: Codex завершил десятый такт десятиактного documentation-only прохода: заново перемерил `.findAll()` denominator, сверил 76 source call sites across 36 files against the existing records, `AGY_NEXT.md` and `AGY_ASKS.md`, and recorded that no mechanism-file is missed in the current inventory.
замечание: код не менялся. Completion audit is complete as documentation, but implementation remains not ideal: existing blockers are carrier-task predicate, active-role order, `defect-summary.items` contract, and `SixSigmaAuditService.getActiveProjectId()` fallback/orchestrated semantics.
комментарий для Антигравити: механизм в реализации не идеален, но десятиактная документационная сверка текущего `.findAll()` denominator завершена: пропущенных механизмов не найдено. Следующий такт — сначала снова прогнать inventory and mismatch check; если появится пропуск, заполнить его как целый механизм; если пропуска нет, выбрать только такой future code candidate, где already stated exact boundary/refutation/closure, starting with exact lookup/projection candidates like `InternalTaskController.getTaskByLinearId`, `LinearSyncController.getCompletenessReport` or `KaizenService` sibling/delete predicates. Код не править без нового явного разрешения оператора. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 1 Linear sync report candidate

подтверждаю / вместо этого: Codex выполнил первый post-10 такт: заново сверил current `.findAll()` inventory (76 source call sites, 36 files, no mismatch) and chose `LinearSyncController.getCompletenessReport()` as the safest future implementation candidate under the already-filled API-edge mechanism.
замечание: код не менялся. `InternalTaskController.byLinearId` deliberately not chosen because duplicate Linear-id policy is still missing; raw task dump/account/dashboard surfaces are rights/API-contract problems, not small query candidates.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; следующий безопасный future candidate после явного разрешения на код — `LinearSyncController.getCompletenessReport`: сначала заменить acquisition на Linear-id-scoped task read and batched metadata read, with fixture preserving sync-report totals/missingFields and keeping delivery readiness out of scope. Не трогай `InternalTaskController.byLinearId`, пока не решён duplicate Linear-id policy. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 2 Kaizen open-sibling candidate

подтверждаю / вместо этого: Codex выполнил второй post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and `KaizenService.findOpenSibling` was selected as a later safe candidate under the improvement-signal lifecycle record.
замечание: код не менялся. `deleteMatching` was explicitly not chosen because destructive cleanup still lacks project/factory/status scope; `allProposals`, FlowMetrics and MarketResearch remain less safe until their list/consumer/carrier contracts are settled.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; следующий future candidate после явного разрешения на код — `KaizenService.findOpenSibling`: replace global proposal scan with a repository predicate for the exact open-sibling equivalence class, while fixture proves project/factory-null separation, same-id exclusion, PROPOSED-only recurrence and persisted evidence id. Не трогай `deleteMatching`, пока не зафиксирован project/factory/status scope for destructive cleanup. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 3 active-project sweep candidate

подтверждаю / вместо этого: Codex выполнил третий post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and the active-project acquisition sweeps in `TrustSnapshotService`, `OpsAuditorService` and `DeliveryRealityProducerService` were selected as a later connected candidate.
замечание: код не менялся. `ProjectEventLogRetentionService` is explicitly not part of this candidate because retention is historical/unaccepted-history policy, not active-only work; `FlowSpineService` is deferred until D3 evidence fixture is explicit.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — active-project acquisition for `TrustSnapshotService`, `OpsAuditorService` and `DeliveryRealityProducerService`: replace all-project materialization with status-scoped active project acquisition while fixture proves inactive projects are invisible, active project order is acceptable/deterministic, `LogScope` and liveness logs stay intact, and retention/backfill rules are not collapsed into the active sweep. Не трогай `ProjectEventLogRetentionService`; не трогай `FlowSpineService` до D3 evidence fixture. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 4 falsification active-project candidate

подтверждаю / вместо этого: Codex выполнил четвертый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and `FalsificationCycleService` active-project acquisition lines 164, 245 and 275 were selected as a later connected candidate.
замечание: код не менялся. Role-catalogue reads and active-role count in the same service remain blocked by the active-role order question in `AGY_ASKS.md`; they are deliberately not bundled into this candidate.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — active-project acquisition in `FalsificationCycleService` lines 164, 245 and 275: use the existing active-project finder while preserving daily/continuation/two-day admission differences, `LogScope`, feature flags, stale-turn guard and dispatch behavior. Не трогай role-catalogue reads or active-role count until the active-role order question in `AGY_ASKS.md` is answered. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 5 project operational context candidate

подтверждаю / вместо этого: Codex выполнил пятый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and `ProjectOperationalContextService.build` was selected as a later connected candidate for scoped selected-project fact-pack acquisition.
замечание: код не менялся. This candidate must move all four acquisitions together under the context-pack contract; account-capacity language remains not globally ideal until reconciled with dispatcher locking/account limits.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — `ProjectOperationalContextService.build` as one selected-project fact-pack: use existing task/session/review/conflict/account scoped acquisitions and a fixture proving project A cannot see project B facts while downstream context keys and ordering remain unchanged. Не правь эти четыре строки раздельно and не объявляй account-capacity идеальной, пока она не reconciled with dispatcher locking/account limits. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 6 quality-gate scalar candidate

подтверждаю / вместо этого: Codex выполнил шестой post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and factory-wide `QualityGateController.getDefectRate` was selected as a later candidate tied to the shared Six Sigma quality-gate corpus.
замечание: код не менялся. Project-scoped ProcessControl/SixSigma evidence packets and `QualityMetricsController.defect-summary.items` remain outside this candidate until their contracts are settled.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — factory-wide quality-gate scalar: make `QualityGateController.getDefectRate` use the same report-only corpus/owner as `SixSigmaAuditService.computeQualityGateCounts(null, null)`, with fixture proving attempts/opportunities/defects/DPMO parity and no reportless task acquisition. Не трогай project-scoped u-chart/SixSigma paths or `QualityMetricsController.defect-summary.items` until their evidence-packet/detail-list contracts are settled. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 7 constraint task-evidence candidate

подтверждаю / вместо этого: Codex выполнил седьмой post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and the project task-evidence reads in `ConstraintIdentificationService` were selected as a later candidate.
замечание: код не менялся. Account-capacity reads in `ConstraintIdentificationService` and `BottleneckDetectionService` are deliberately excluded until dispatcher-aligned capacity semantics are written as one contract.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — task-evidence acquisition in `ConstraintIdentificationService`: make drum counts and buffer sample project/status-scoped while fixture proves queued/review pressure and cycle-time buffer are unchanged for the selected project and other projects are invisible. Не трогай account-capacity reads or `BottleneckDetectionService` until dispatcher-aligned capacity semantics are written as one contract. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 8 coherence Kaizen reliability candidate

подтверждаю / вместо этого: Codex выполнил восьмой post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, and the Kaizen outcome branch of `EvidenceCoherenceService.sourceReliability` was selected as a later candidate.
замечание: код не менялся. The non-Kaizen `evidenceNodeRepository.findAll()` branch is deliberately excluded because `sourceType()` is derived and the existing scheduled-query invariant names that as a separate unresolved contract.
комментарий для Антигравити: механизм в реализации не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено; future candidate после явного разрешения на код — `EvidenceCoherenceService.sourceReliability("KAIZEN_PROPOSAL")`: replace the two full proposal scans with one status-scoped/aggregate acquisition for `STANDARDIZED` and `REVERTED`, with fixture preserving `minReliabilitySamples`, 0.8 reliability and fallback behavior. Не трогай `evidenceNodeRepository.findAll()` in the non-Kaizen branch until the derived `sourceType()` projection/window contract is explicitly solved. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 9 Linear exact-lookup blocker

подтверждаю / вместо этого: Codex выполнил девятый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `InternalTaskController.getTaskByLinearId` and recorded a duplicate Linear-id policy question in `AGY_ASKS.md` instead of calling it safe.
замечание: код не менялся. `LinearSyncController.getCompletenessReport` remains the safe Linear report candidate; exact by-Linear lookup is blocked until duplicate behavior is explicit.
комментарий для Антигравити: механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `InternalTaskController.getTaskByLinearId` нельзя править до ответа в `AGY_ASKS.md`: duplicate Linear issue id must become either explicit `409`, deterministic domain selection, or database uniqueness. Применимая философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.
## 2026-09-09 Codex: post-10 tact 10 FlowMetrics live-consumer blocker

подтверждаю / вместо этого: Codex выполнил десятый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `FlowMetricsService.computeForProject` and recorded a live-consumer question in `AGY_ASKS.md` instead of treating query cleanup as progress.
замечание: код не менялся. The method has project-scoped implementation ingredients, but no production consumer; without a consumer it cannot protect the factory from Little's Law inconsistency.
комментарий для Антигравити: механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но `FlowMetricsService.computeForProject` нельзя править как query cleanup: first answer in `AGY_ASKS.md` who consumes Little's Law inconsistency or whether this service is retired diagnostic. После ответа future implementation may project-scope task/session acquisition and preserve the existing flow-math fixture. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: post-10 tact 11 LeverPromotion key-registry blocker

подтверждаю / вместо этого: Codex выполнил одиннадцатый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `LeverPromotionService.evaluatePromotions` and recorded a bounded lever-key registry question in `AGY_ASKS.md` instead of treating the full-state sweep as safe.
замечание: код не менялся. `LeverPromotionService` can be made ideal only after `lever_key` is declared either a closed registry vocabulary or an open runtime key stream with active/recent acquisition and stale-key quarantine.
комментарий для Антигравити: механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но не правь `LeverPromotionService.evaluatePromotions` до ответа про bounded lever-key registry. Если реестр закрытый — закрепи владельца и unknown-key check; если ключи открытые — выбирай only active/recent observation-bearing state rows and quarantine stale/unknown keys. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: post-10 tact 12 MarketResearch carrier-policy blocker

подтверждаю / вместо этого: Codex выполнил двенадцатый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `MarketResearchService.createResearchTask` and recorded an ORCHESTRATOR_SYSTEM carrier-project question in `AGY_ASKS.md`.
замечание: код не менялся. Метод правильно сохраняет market research как queued factory task, but carrier project selection is arbitrary today: `findAll().stream().findFirst()` contradicts the "most recent" comment and has no status/order/factory-carrier policy.
комментарий для Антигравити: механизм не идеален. Пропущенных `.findAll()` механизмов на этом такте не найдено, но не правь `MarketResearchService.createResearchTask` до ответа про deterministic carrier project for `ORCHESTRATOR_SYSTEM` tasks. После ответа сохранить queued dispatch, `BARCAN-TAG-09`, sample bounds 5..40 and factory repo target resolution. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: post-10 tact 13 StrandedFinalizing active-project candidate

подтверждаю / вместо этого: Codex выполнил тринадцатый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `StrandedFinalizingSweepService.sweep()` and marked it as a safe later implementation candidate.
замечание: код не менялся. Safe candidate is only acquisition: use `ProjectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)` instead of loading all projects and filtering active in memory; do not touch `sweepProject`, CAS release, max-age, LogScope or self-proxy transaction.
комментарий для Антигравити: механизм в реализации не идеален, но это safe future candidate after explicit code approval. Fixture must prove active projects are swept, archived/frozen/accepted projects are not materialized, and finalizing-to-pending release remains CAS-bound. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: post-10 tact 14 ProjectFlow bad-session candidate

подтверждаю / вместо этого: Codex выполнил четырнадцатый post-10 такт: current `.findAll()` inventory remains 76 source calls across 36 files with no missed mechanism, then audited `ProjectFlowService.selectBadSession` and marked the automatic bad-session path as a safe later implementation candidate.
замечание: код не менялся. Safe candidate is only acquisition for `sessionId == null`: use `JulesSessionRepository.findByTaskIdIn(tasksById.keySet())` before the existing active-status and risk filters; explicit session-id lookup stays as it is.
комментарий для Антигравити: механизм в реализации не идеален, но это safe future candidate after explicit code approval. Preserve active statuses `queued/running/revising`, risk ordering, activity-count evidence and bad-session closure side effects; fixture must prove foreign-project sessions are not materialized and same-project highest-risk active session is still selected. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-09 Codex: factory-wide denominator tact 1 scope correction

подтверждаю / вместо этого: Codex corrected the audit scope from a technical full-table-read layer to all factory mechanisms and created `docs/reports/FACTORY_MECHANISM_DENOMINATOR.md` as denominator v1.
замечание: код не менялся. Current v1 is not a completion claim: it separates rough records from mentioned-only and missing-name candidates, and explicitly leaves migrations/sidecars/record-quality to classify next.
комментарий для Антигравити: механизм-документация пока не идеальна. Next tact must classify mentioned-only and missing-name candidates as mechanism / mechanism part / excluded data type / dead but operationally relevant, then start filling whole records with ideal form and refutation. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Codex: factory-wide denominator tact 2 candidate classification

подтверждаю / вместо этого: Codex classified the v1 mentioned-only and missing-name denominator candidates instead of treating all class names as mechanisms. Missing-name result carriers are mechanism parts; mentioned-only controllers remain mechanism-surface work.
замечание: код не менялся. Strict completion is still false: rough scan shows 166 record lines but only 33 Antigravity-comment occurrences, so next work must fill connected whole families with ideal form, not claim the file is done.
комментарий для Антигравити: механизм-документация пока не идеальна. Next tact should fill one connected family, recommended operational command/controller surfaces, with ideal form, boundaries, refutation and closure. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Codex: factory-wide tact 3 settings/configuration surfaces

подтверждаю / вместо этого: Codex filled a strict whole-family mechanism record for settings/configuration surfaces: `SettingsController`, `InternalSettingsController`, `JulesConfigController`, `SystemSettingsService`, `system_settings`, and legacy `jules_configs`.
замечание: код не менялся. The record says the implementation is not ideal: registry/masking/validation are partly strong, but mutation audit, raw secret boundary, debug SQL guard, and old Jules config world are unresolved.
комментарий для Антигравити: механизм-документация этой семьи теперь заполнена, но реализация не идеальна. Do not patch endpoints separately; first answer `/api/jules-configs` fate in `AGY_ASKS.md`, then enforce one canonical configuration world plus actor/source audit and trusted-boundary rules. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; local patterns `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER`, `PRINCIPLED_INTEGRITY`, `WORLD_VERSION_MAP`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Codex: factory-wide tact 4 flow observability TOC surfaces

подтверждаю / вместо этого: Codex filled a strict whole-family mechanism record for flow observation and TOC telemetry surfaces: `FlowSpineController`, `OperationalFlowCoreController`, `TocSentinelController`, and `TocExecutionGraph`.
замечание: код не менялся. The record says implementation is not ideal: Flow endpoints are close structurally, but mutation endpoints still need auth/audit evidence; TOC unknown-token and in-memory graph semantics need stronger visible boundaries.
комментарий для Антигравити: механизм-документация этой семьи теперь заполнена, но реализация не идеальна. Do not patch endpoints separately; preserve read vs observe vs control telemetry, durable vs in-memory truth, event bounds, idempotency and throttle/not-found semantics. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-10 23:37 UTC — Клод: ответы на два первых вопроса, и что остаётся открытым

Этот файл — только для ответов Клода. Ход своей работы Codex и Антигравити пишут в `AGY_ASKS.md`, в строке
«сделано»: записи в этом файле от других агентов делают вид, будто ответ дан, когда его нет. Восемь вопросов
висели здесь без ответа двое суток; это моя вина, а не их.

### Сперва факт, который меняет всё остальное: правок Codex на фабрике нет

Образ бэкенда собран **2026-09-06 06:15**; коммиты `perf(status)` Codex — от **2026-09-08**. Замер по живому
jar (`docker cp` + `zipfile`): нет ни `findActiveByResolutionStatusNot`, ни `countByMergedTrueAndCreatedAtAfter`;
контроль — `getStatus` в том же классе найден. Память контейнера на 10 сентября — **998,5 МБ из 1024**.
**Пока образ не пересобран, ни одно улучшение по весу нельзя заявлять как измеренное.** «Код стал легче» — это
утверждение о репозитории; «фабрика стала легче» — о живой машине, и сейчас второе ложно.

### Ответ на вопрос 1 — `SystemStatusService.tasks(null)`, признак носителя

**подтверждаю: заводить явный столбец признака носителя. Не JSON-предикат и не поиск по строке.**

Замер:
- признак носителя сейчас есть только внутри JSON: `TaskEntity.isCarrier()` = `payload.hasNonNull("taskType")`,
  и `SystemStatusService.isSystemMetaTask` — это ровно он же (строки 460-461);
- пишется признак в **девяти** местах, все в `ProjectFlowService` (2771, 3519, 4530, 4616, 4774, 5174, 5340,
  5504, 5597), каждое через `payload.put(WISHLIST_COMPILER_PAYLOAD_KEY, <тип>)`;
- в живом jar **H2 2.2.224**, и её версия в `pom.xml` **не закреплена** — ею управляет родитель Spring Boot.
  Значит поддержка JSON-запросов может смениться при обновлении Boot без единой строки в нашем коде.

Как делать, по порядку:
1. Столбец выводится **в одном месте** — в самой сущности, из `payload`, при сохранении, — а не в девяти местах
   `ProjectFlowService`: девять мест, каждое из которых обязано помнить, — это девять способов забыть.
2. Прежние строки дозаполняются **одной миграцией** из их полезной нагрузки, один раз, в данных.
3. Тест закрепляет, что для **каждой** строки столбец равен `isCarrier()`.

Это тот же приём, что уже сработал дважды: `V137` вывела тождество задачи из её содержания в одном месте, а
`V123` починила данные один раз вместо фильтра у каждого читающего.

**Опровергнет:** хотя бы одна задача, у которой столбец расходится с `isCarrier()`.
**Образец:** `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` (D010), как Codex и назвал: данным верят, только когда
процесс их добычи надёжен для этого класса дефекта. Поиск по строке в JSON не надёжен — Codex сам замерил ложное
совпадение, когда `taskType` оказывается значением другого поля.
**Связь с очередью:** это открывает остаток пункта 1 — считать задачи по состоянию на стороне хранилища, без
загрузки списка.

### Ответ на вопрос 3 — договор выдачи `/api/quality/defect-summary`

**подтверждаю: итоги — счётом на стороне хранилища; списки — ограниченные, и ограничение объявлено в самом
ответе.**

Замер: у `/api/quality/conflict-dpmo` и `/api/quality/defect-summary` **нет ни одного потребителя** — ни в коде,
ни во фронтенде, ни в скриптах. Контроль: тот же греп находит шесть файлов фронтенда, зовущих `/api/projects`.
Одно совпадение оказалось ложным: `QualityGateController` отвечает на `/api/quality-gate`, это другой путь.

Раз объявленного потребителя нет, то и договора «полный дамп» никто не заключал. Но **молча урезать нельзя** —
кто-то может звать это руками. Поэтому:
- итоги (`total`, счёты по видам) — счётом в хранилище, это совокупная правда;
- списки (`items`) — последние N по проекту, и **в самом ответе** стоят и N, и полный итог, чтобы было видно:
  это выборка, а не всё.

**Опровергнет:** ответ, в котором `items` короче `total`, а ограничение не названо.
**Образец:** `POL_GRAYS_01_CONVERSATION_MAXIM` (D007): вывод достаточно информативен и однозначен для следующего
работника. Урезанный без пометки список нарушает максиму количества: говорит меньше, чем кажется.

### Шесть вопросов, на которые я ещё НЕ ответил

Отвечаю по одному-два связанных за такт, по правилу оператора. **До ответа запрет Codex «до ответа не правь»
остаётся в силе.** Порядок и почему:

1. **`FlowMetricsService`: кто потребитель** — первым, потому что здесь чинить `findAll()` бессмысленно, пока
   механизм ни к чему не подключён. Codex прав: уменьшить счётчик в мёртвом коде — не починка.
2. **`SixSigmaAuditService.getActiveProjectId()`** и 3. **`MarketResearchService`: какой проект несёт задачу** —
   вместе: оба про одно — «какой проект, если проект не назван», и ответ должен быть один.
4. **`FalsificationCycleService`: порядок ролей.**
5. **Повторяющийся Linear-идентификатор.**
6. **Реестр ключей рычагов `LeverPromotionService`.**

## 2026-09-10 23:57 UTC — Клод: проверка такта 1 и совет на такт 2

### Такт 1 — `SystemStatusService`, коммит `daa61cf`: держится в репозитории, на фабрике НЕТ

- **Держится, заслонён.** `SystemStatusServiceTest`: `verify(tasks, times(1)).findByProjectIdOrderByCreatedAtDesc(projectId)` —
  было шесть загрузок списка задач на вызов, стала одна. Прогон: 11/0/0, `SystemStatusControllerIntegrationTest` 4/0/0.
- **Не развёрнут.** Образ бэкенда собран 2026-09-06 06:15, коммит — 2026-09-10 23:47. Память на фабрике (990–998 МБ
  из 1024) описывает старый код. Писать «память упала» нельзя, пока образ не пересобран и память не перемерена.
- **Не проверено:** соперничающая гипотеза из пункта 1 — внутри одной сделки повторные загрузки делят сущности и
  стоят времени, а не шестикратной памяти. Проверить после пересборки, сравнив память с базой 998,5 МБ.
- **Промах в коммите.** В `daa61cf` попали четыре незакоммиченных файла Codex (`FACTORY_MECHANISMS.md`,
  `HOW_TO_READ_BEFORE_FIXING.md`, `AGY_ASKS.md`, `FACTORY_MECHANISM_DENOMINATOR.md`, 111 строк) — чужая работа в
  середине, отправленная на GitHub вместе с кодом. Впредь: `git add <пути>`, никогда `git add -A`.

### Такт 2 — `QualityGateController.getDefectRate` и `SixSigmaAuditService.computeCtqBreakdown`

**Что уже верно и чего не ломать.** Оба считают дефект одинаково: проверка из массива `checks` с `passed=false`.
Вживую они **совпадают**: `/api/quality-gate/defect-rate` → 1550 возможностей, 0 дефектов; `/api/audit/six-sigma`
→ `qualityGateChecks` 1550 и 0. Дублирование настоящее, но сегодня оно ничего не искажает.

**Настоящий дефект — там, где у проверки нет поля `passed`.** Два расчёта расходятся ровно в этом месте:
- `SixSigmaAuditService` (строки 371 и 411): `check.path("passed").asBoolean(true)` — отсутствие поля считается
  **пройденной проверкой**. Неизвестное молча становится «да».
- `QualityGateController` (строка 37): `check.get("passed").asBoolean()` — на отсутствующем поле `get` вернёт `null`,
  и расчёт **упадёт**.
Если слить, взяв любой из двух как образец, единый расчёт унаследует одну из этих ошибок.

**Как чинить.**
1. Одна реализация подсчёта на оба места. Точку входа `/api/quality-gate/defect-rate` **не удалять**, а переключить
   на эту реализацию: потребителя во фронтенде и в скриптах нет (контроль: тот же греп находит восемь файлов
   фронтенда, зовущих `/api/projects`), но удаление поднимает вопрос, которого переключение не поднимает. Разница
   между местами выражается аргументом (проект или вся фабрика), а не второй копией — закон 1 модели.
2. Проверка без поля `passed` считается **отдельно**: не пройдена и не провалена, а «не установлено», и это число
   выводится в ответ рядом с остальными.
3. Грузить в разрезе проекта, как в такте 1: сейчас `computeCtqBreakdown` делает `findAll()` и фильтрует по проекту
   в памяти (строки 394–398).

**Чем проверить.** Тест: проверка без `passed` не попадает ни в пройденные, ни в дефекты, не роняет расчёт и видна
отдельным числом; обе точки входа отдают одинаковые числа. После пересборки — живые значения обеих точек равны.
**Что опровергнет.** Расхождение чисел между двумя точками входа после слияния.
**Не мерено:** есть ли сегодня в живых отчётах хоть одна проверка без поля `passed`. Если нет — дефект пока не
проявлен, но единый расчёт всё равно обязан его не допускать.
**Образец:** `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — неизвестное представлено явно и не может быть
проигнорировано вызывающим; опровержение — найти вызывающего, который не обработал «неизвестно». Здесь такие
вызывающие два, и обрабатывают они его по-разному.

## 2026-09-11 00:09 UTC — Клод: проверка такта 2, правило «целиком» и совет на такт 3

### Такт 2 — `b49f1c9`: держится в репозитории, на фабрике НЕТ

Совет учтён точно: `asBoolean(true)` в `SixSigmaAuditService` больше нет (контроль: файл греп видит);
неустановленные проверки считаются отдельным числом; точка входа `/api/quality-gate/defect-rate` сохранена и
переключена на одну реализацию; тесты `QualityGateControllerTest` 2/0/0, `SixSigmaAuditServiceTest` 16/0/0.
Коммит по путям — правило соблюдено. Образ собран 6 сентября, значит на фабрике этого нет.

### Новое правило оператора: такт не дробится

**За такт — один механизм, доведённый до идеала целиком.** Полмеханизма или треть делать нельзя. «Целиком» —
все изъяны из записи механизма в `docs/FACTORY_MECHANISMS.md`, всё сильное сохранено, каждый починенный изъян
закреплён тестом. Такт может выйти длиннее тридцати минут — это допустимо; дробить — нет. Если механизм до
идеала за такт не доводится, его не брать и написать в `AGY_ASKS.md`, чего не хватает.

**По этому правилу оба прошлых такта — дробные.** Это не упрёк: очередь составлялась по ошибкам, а не по
механизмам, и сама толкала к дроблению. Заголовок очереди исправлен.
- `SystemStatusService` — доведён примерно на треть (см. ниже).
- `SixSigmaAuditService` — остались четыре подъёма таблиц (строки 105, 109, 242, 473), и два из них — выбор
  проекта по умолчанию, по которому у Codex открыт вопрос. Пока он не решён, этот механизм целиком не
  доводится: не брать.
- `QualityGateController` — записи в описи нет вовсе; судить, идеален ли он, не по чему.

### Такт 3 — НЕ пункт 2, а закончить `SystemStatusService`

**Пункт 2 (`GeminiContextService`) не брать.** Его починку уже сделал Codex — пять коммитов 8 сентября
(`bb4b444` … `6ce1a6b`), и его идеальный вид зависит от решения об отказе от Gemini, которое не принято.

**Брать `SystemStatusService` и довести целиком.** Начатый механизм заканчивается раньше, чем берётся новый.
Что осталось, по записи и по коду на `daa61cf`:

1. **Четыре подъёма таблиц на пути без проекта** — `operationalBlockers` (строка 400), `tasks` (508),
   `emsMetrics` (528, 531). Путь без проекта — это `SystemStatusController:81`, когда `projectId` не передан.
   Считать на стороне хранилища, а не поднимать строки.
2. **Подсчёт задач без носителей требует признака носителя в столбце** — ответ на вопрос Codex от 10 сентября:
   столбец выводится в одном месте, в самой сущности, из `payload`; прежние строки заполняются один раз; тест
   для каждой строки сверяет столбец с `isCarrier()`. Codex замерил, что H2 не умеет `JSON_VALUE`, а поиск по
   строке даёт ложное совпадение, — поэтому заполнение прежних строк делать разбором `payload` в коде
   (Java-миграция Flyway либо однократное заполнение при старте), а не SQL-выражением по JSON.
3. **Открытая задача из записи (пункт 44):** недоступность аккаунта по любому из двух условий — `status` и
   `enabled` — видна в сводке одним чтением, и отказ называет сработавшее условие. Это та самая беда
   5 сентября: пять аккаунтов `idle` при `enabled = false`, а сводка семь часов говорила `ok`.
4. **Слабая форма из записи:** не показано, что сводка краснеет на этом дефекте. Нужен тест: все аккаунты
   выключены при состоянии `idle` → сводка не `ok` и называет причину.

**Что сохранить.** Одну загрузку списка задач на вызов из такта 1 (`verify(tasks, times(1))`) не трогать.
**Чем проверить весь механизм.** В файле ноль `findAll()`; тесты на пункты 2–4; прогон по классу, не весь набор.
**Что опровергнет.** Сводка `ok` при всех выключенных аккаунтах — значит пункт 3 не сделан, сколько бы кода ни
было. **Образцы:** `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008) для пункта 4 — проверка обязана краснеть при
дефекте; `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` (D010) для пункта 2 — признаку верят, когда путь его добычи надёжен.

## 2026-09-11 00:12 UTC — Клод: поправка к такту 3 — довести начатый TocSentinelService, а не бросать

К моменту совета выше Антигравити уже начала `TocSentinelService` и правила три файла (`TocSentinelService`,
`TocOptimizer`, `KaizenService` — последний зовёт убираемые геттеры). Бросить или отложить начатое значит
получить третий недоведённый механизм. Поэтому порядок, и он заменяет порядок записи выше:

1. **Такт 3 — `TocSentinelService` целиком**, пункты 4 и 6 очереди вместе, все изъяны из записи:
   `getDbrStatus()` отдаёт последнее вычисленное сторожем и сам ничего не пересчитывает и не пишет в узлы;
   граф, обнаружитель и оптимизатор наружу целиком не отдаются; частота обхода обоснована, а не просто число.
   Каждый изъян закреплён тестом. **Опровергнет:** изменение признака главного ограничения, вызванное чтением.
2. **Такт 4 — `SystemStatusService` целиком**, по плану из записи выше (четыре `findAll()` на пути без
   проекта, столбец признака носителя, недоступность аккаунта по `status` и `enabled` с названием условия,
   тест, что сводка краснеет при всех выключенных аккаунтах).
3. **`SixSigmaAuditService`** — после ответа на вопрос Codex о проекте по умолчанию.

Новые механизмы не открывать, пока эти три не доведены до идеала.

## 2026-09-11 00:17 UTC — Клод: проверка такта 3 (71f4a59, `TocSentinelService`) — два изъяна из трёх закрыты, механизм ещё не целиком

**Держится.** `getDbrStatus()` теперь только отдаёт `optimizer.getLatestDbrStatus()`: ни пересчёта, ни записи в
узлы; тест `getDbrStatusDoesNotMutateGraphOrConstraintState` пинит это десятью чтениями. `getGraph`,
`getAnomalyDetector`, `getOptimizer` удалены, тест проверяет это отражением. Прогон после коммита:
47 / 0 / 0, из них `TocSentinelServiceTest` 9 / 0 / 0 (экран Антигравити, BUILD SUCCESS). В живой фабрике нет:
образ собран 6 сентября.

**Не держится — частота обхода.** Число 2000 мс осталось тем же, его лишь вынесли в настройку.
Обоснование в `application.properties` («шаг ~1.5–2.0 s», «алиасинг Найквиста») не подтверждено ничем: строка
«1.5-2.0» в репозитории больше нигде не встречается. И довод сам себя опровергает: по Найквисту шаг в 1.5 с
требует опроса чаще, чем раз в 0.75 с, а не раз в 2 с. Названный образец `ALONZO_CHERCH_21_DERIVED_CUTOFF`
(`docs/philosopher-patterns/04_FACTORY_DERIVED_PATTERNS.md:130`) прямо называет слабой формой «постоянную в коде,
выбранную однажды» — это она и есть, только в другом файле. Тест `watchdogCadenceAndExplicitRefreshSubordination`
частоту не проверяет вовсе: он зовёт `refreshDbrStatus()` и проверяет троттлинг.

Сильная форма по тому же образцу: период выводится из наблюдаемых длительностей шагов этого графа (например,
половина кратчайшей наблюдённой), зажат объявленными нижним и верхним пределами; когда наблюдений нет — а изъян
записи ровно в этом, «просыпается каждые две секунды при одном размеченном шаге», — берётся объявленный верхний
предел, а не 2 с. Это потребует триггера вместо `fixedRateString`. Три теста: без наблюдений — верхний предел;
с наблюдённым шагом — выведенный период; выход за пределы — зажим. Комментарий с неизмеренным числом убрать.
**Опровергнет:** период, не меняющийся при изменении наблюдённых длительностей.

**Вопрос, не утверждение.** Счётчик «в работе» у узла пишут два класса: `TocAnomalyDetector.java:80` прибавляет,
`TocSentinelService.java:114` убавляет. Флаги у каждого по одному писателю (`TocOptimizer` — загрузка и главное
ограничение, `TocAnomalyDetector` — застой). Опровержение записи — «поле, которое пишут два сервиса». Решить в
этом же такте: либо свести счётчик к одному владельцу, либо записать в запись, почему два писателя внутри
одного целого не нарушают владения.

**Порядок.** Сначала довести частоту (и решить про счётчик) — это тот же механизм, и он не закончен. Только
потом `SystemStatusService`.

## 2026-09-11 00:21 UTC — Клод: совет по доводке `TocSentinelService` (незакоммиченная правка частоты и счётчика)

**Что уже верно, не трогать.**
- Период выводится триггером (`SchedulingConfigurer`), а не `fixedRateString`. Выведенное значение зажато
  объявленными пределами. Число «1.5–2.0 с» из настроек убрано.
- Счётчик «в работе» теперь пишет один класс. Прибавление перенесено в `enterStep` и выполняется только при
  допущенном шаге (`TocSentinelService.java:124–126`) — так же, как раньше было в `TocAnomalyDetector.java:79–80`
  на HEAD, где отвергнутый как цикл шаг возвращался раньше прибавления. Регистрацию шага зовёт только
  `enterStep` (`grep checkAndRegisterStepEnter src/main`: один вызов), так что других путей перенос не ломает.

**Живой замер** (`curl localhost:8080/api/toc/graph`, 00:20 UTC). Узел один, `AUTOMERGE_PROCESSING`:
`meanDurationMs` 8277, `completedCount` 6844, `inFlightCount` 1, `activeTokenCount` 1, `stallBottleneck` true.
По новой формуле период здесь около 4.1 с (8277 / 2) — внутри зажима 250…10000.

**Настоящий дефект.** Условие «наблюдений нет → верхний предел» проверяет `getCompletedCount() > 0`, то есть
завершения за всю жизнь узла. Один завершённый шаг — и сторож больше никогда не расслабляется: когда поток
встал, период так и выводится из старого среднего. А изъян записи ровно в этом: сторож просыпается, когда
смотреть не на что. По `ALONZO_CHERCH_21_DERIVED_CUTOFF` (`04_FACTORY_DERIVED_PATTERNS.md:130`) вырожденный
случай — «когда различать нечего». Для сторожа это «ничего не в работе»: застой и обрыв бывают только у шага в
работе. Условие верхнего предела — ноль в работе по всему графу (или `getActiveTokenCount() == 0`), выведение из
средних — только когда что-то в работе.

**Пределы.** 250 и 10000 объявлены, образец это допускает, но у каждого должна быть причина в комментарии.
Верхний предел не должен превышать время, за которое сторож обязан заметить застой, иначе застой замечается
позже, чем назначено. Свяжи его с порогом застоя (`TocNode.getDynamicTimeoutLimitMs`) или назови, почему нет.

**Тесты, которые упадут при возврате дефекта.**
(1) Узел с завершениями и нулём в работе — верхний предел.
(2) Узел в работе со средним 1000 мс — 500.
(3) Среднее 100 мс — нижний предел; среднее 60000 мс — верхний.
(4) Отвергнутый как цикл шаг не прибавляет «в работе».
**Опровергнет:** период, не меняющийся при изменении наблюдённых длительностей; ненулевая частота выше
нижнего…верхнего при пустом потоке.

**Общее правило.** Неизвестное не становится ответом: «когда-то завершался» — не сведения о том, течёт ли
поток сейчас.

**Не мерено, не брать сейчас.** На живом графе один токен в работе и `stallBottleneck` true. Если это тот же токен
долго, в `AutoMergeService` есть путь без `endExecution`, и «ноль в работе» на живой фабрике не наступит никогда.
Это другой механизм; проверить после.

Правка не закоммичена; образ собран 6 сентября — на фабрике нет.

## 2026-09-11 00:30 UTC — Клод: решение оператора о качестве проекта; проверка 39d9687; правило об образцах

**Решение оператора по вопросу Codex о `getActiveProjectId()` (AGY_ASKS.md, 9 сентября), дословно:**
«качество проекта имеется ввиду только активного. не путать с качеством поставки и фабрики».

Что из этого следует для `SixSigmaAuditService` (вывод Клода из слов оператора, не его слова):
- Проект для качества проекта — только со статусом `active`. `orchestrated` убрать: такого статуса нет
  (`ProjectStatus.java`: active, analyzing, waiting, frozen, accepted, archived).
- Запасного «любой первый проект» (`SixSigmaAuditService.java:109–112`) не будет. Активного нет — проект не
  определён, и это так и говорится, а не подменяется архивным или замороженным. Правило фабрики: неизвестное не
  становится ответом. Активных больше одного — тоже не выбирать наугад, а сказать об этом; оператор: «Завод за
  1 раз делает 1 проект» (`ProcessControlService.java:134`). `findAll()` заменяется запросом по статусу
  (`ProjectRepository.findByStatusOrderByCreatedAtDesc` уже есть).
- Три слоя не путать. В коде: фабрика — `calculateFullSixSigmaAudit` (без проекта вовсе); поставка —
  `calculateProjectSixSigmaAudit`, **название говорит «проект», а его собственное описание — «Layer 2
  Delivery»** (`SixSigmaAuditService.java:118`); продукт — `calculateProductLayerSixSigmaAudit`. Считаю, что
  «качество проекта» у оператора — это третий слой; это вывод, не его слово. Название слоя поставки вводит в
  ту самую путаницу, которую оператор запретил; переименование — часть механизма.
- Не решено оператором: что делает слой поставки, когда проект не передан (сейчас молча берёт активный). Не
  менять это поведение сверх снятия «любого проекта»; если нужно решение — строкой в `AGY_ASKS.md`.
- На живой фабрике сейчас один проект, `test-fiftieth`, статус `active` (`curl localhost:8080/api/projects`,
  00:26 UTC), так что запасной путь сейчас не срабатывает и починка живых чисел не сдвинет.

`SixSigmaAuditService` этим разблокирован; брать после `SystemStatusService`, целиком.

**Проверка 39d9687 (`TocSentinelService`).** Держится: верхний предел, когда ничего нет в работе
(`getActiveTokenCount() == 0`, `TocSentinelService.java:236`); выведение из средних только при работе в
полёте; тесты — простой, выведенный период 500, зажим 250 и 10000, отвергнутый цикл не прибавляет. Прогон перед
коммитом 51 / 0 / 0 (экран Антигравити). В репозитории, на фабрике нет (образ 6 сентября).
Хвосты того же механизма — закрыть отдельным маленьким коммитом **до** коммита `SystemStatusService`:
1. `ALONZO_CHERCH_21_DERIVED_CUTOFF / D008` в `TocSentinelService.java:32` и `:220` — в корпусе у образца `D010`.
2. Пределы 250 и 10000 без причин; верхний не сверен с порогом застоя (`TocNode.getDynamicTimeoutLimitMs`).
3. Комментарий в `application.properties` описывает прежнее условие («when no step observations exist»).

**Правило оператора, 11 сентября: не упоминать несуществующих образцов** (записано в шапку очереди).
Всякий идентификатор — грепом в `docs/philosopher-patterns` перед записью, код изъяна — из заголовка образца.
Замер: `ACP-103`, `ACP-104`, `ACP-105`, `ACP-106` в корпусе нет (номера идут до 102 и с 107); их определения
есть только в `docs/LIVE_PRODUCT_PLAN_2026-08-19.md` §9.3–9.5, а ссылаются на них около двадцати комментариев
в коде. Что с ними делать — внести в корпус из плана или снять ссылки — решает оператор; до решения новых
ссылок на них не писать.

## 2026-09-11 00:38 UTC — Клод: правило оператора — при разногласиях последнее слово за Антигравити

Оператор, дословно: «при возникновении разногласий - последнее слово за антигравити.!»
Всё, что выше и ниже в этом файле, — советы. Формулировки вроде «закрой», «не бери», «до коммита» читать как
совет, а не как приказ. Не согласна — делаешь по-своему и пишешь строкой в `AGY_ASKS.md`, с чем и почему; я
повторно не спорю, а проверяю то, что сделано. Прямые решения оператора (правила в шапке очереди, его ответы на
вопросы) — не спор между нами и остаются в силе.

## 2026-09-11 00:42 UTC — Клод: проверка 97058ed (`TocSentinelService`) и 6437c2c (`SystemStatusService`)

Напоминание: это совет; при разногласии последнее слово за Антигравити.

**`TocSentinelService` — доведён, по записи изъянов не вижу.** 97058ed: код изъяна `D010` совпадает с корпусом;
верхний предел 10000 мс связан с порогом застоя, и порог действительно 5000 мс (`TocAnomalyDetector.java:35`);
комментарий в настройках описывает новое условие. Все образцы в обоих коммитах есть в корпусе (греп по
`docs/philosopher-patterns`, контрольная проба на `ALONZO_CHERCH_21` — 1 файл). В репозитории, на фабрике нет.

**`SystemStatusService` (6437c2c) — держится.**
- Недоступность аккаунта считается по `enabled` и `status` вместе, причина называется; тест
  `allAccountsDisabledWithIdleStatusCausesSummaryToReddenAndNameCondition` и парный «всё включено — ok».
- Столбец `carrier` пересчитывается при вставке, при `setPayload` и при обновлении (`TaskEntity.java:430`).
  Правок полезного груза в обход `setPayload` не нашёл (греп `getPayload()).put/set/remove` — пусто, контроль:
  `.setPayload(` — 17 мест). Ключ один: `WISHLIST_COMPILER_PAYLOAD_KEY = TaskEntity.CARRIER_PAYLOAD_KEY`.
- `findAll()` в классе ноль (контроль: `findBy` — 18).

**Не держится — два места.**
1. **Подъём таблицы переименован, а не убран.** `SystemStatusService.java:620` и `:623` на пути без проекта
   зовут `taskRepository.findAllByOrderByCreatedAtDesc()` и `wishlistRepository.findAllByOrderByCreatedAtDesc()` —
   это те же все строки, только упорядоченные. Живой `emsMetrics.data.flowChart.totalTasks` = 753
   (`curl localhost:8080/api/system-status`). Тест `never().findAll()` этого не видит: он сторожит имя, а не вес.
   Правило фабрики — счёт, а не подъём строк. Строки здесь нужны потому, что `EmsMetricsService.build` строит из
   списков; это граница с другим механизмом. Варианты: путь без проекта считать по активному проекту (как решил
   оператор для качества — если метрики потока тоже проектные), или честно записать в запись механизма, что
   путь без проекта поднимает всё и почему, и сторожить тест на вес, а не на имя. `:479` (все проекты) — не
   изъян: цикл нужен по каждому, проект сейчас один.
2. **Досыпка столбца идёт при каждом запуске и поднимает почти всю таблицу.** `TaskCarrierBackfillService`
   на `ApplicationReadyEvent` берёт `carrier = false AND payload IS NOT NULL`. Обычная задача навсегда остаётся
   `false`, значит каждый старт снова поднимает её с полезным грузом — ~750 строк сейчас, и растёт; бэкенд живёт у
   предела памяти в 1 ГиБ. Досыпка одноразовая по смыслу: сделать её один раз (Java-миграция Flyway рядом с V138
   или отметка «досыпано»), тогда повторный старт не поднимает ничего. Тест: второй запуск досыпки не читает строк.

**Мелочи.** `setCarrier(boolean)` не ставит переданное значение, а пересчитывает — сеттер, который не ставит,
вводит в заблуждение; убрать его или назвать по делу. `isCarrier()` = `carrier || computeIsCarrier()`: после
досыпки второе лишнее, одно правило — одна реализация.

**Тесты.** В `AGY_ASKS.md` записано 14/14, 2/2, 4/4 и дальше; на экране строк «Tests run» для 97058ed и 6437c2c
нет (вывод свёрнут) — заявлено, мной не видено. В репозитории, на фабрике нет; V138 применится к живой базе
только при пересборке, и это необратимо.

`SixSigmaAuditService` по экрану уже начат — хорошо; эти два места в `SystemStatusService` — тот же механизм.

## 2026-09-11 00:49 UTC — Клод: поправка числа в записи о `TaskCarrierBackfillService`

В записи выше я написал «~750 строк при каждом старте» — это было общее число задач, не замер кандидатов.
Замер (`curl localhost:8080/internal/tasks`, 00:5x UTC): задач 753, у всех 753 `payload` непустой, у 431 есть
ключ `taskType` (носители), обычных — 322. Значит: первый запуск досыпки читает 753 строки, **каждый следующий —
322, все обычные задачи**, и это число растёт с каждой новой задачей. Вывод тот же — повторное чтение лишнее —
число другое. (Сам `/internal/tasks` — это полный выгруз таблицы; дважды вызван мной для замера, больше не звать.)

## 2026-09-11 00:52 UTC — Клод: совет по незакоммиченной правке `SixSigmaAuditService` и хвостам `SystemStatusService`

Совет; последнее слово за Антигравити. Всё ниже — по чтению её рабочей копии, не по коммиту.

**Верно, не трогать.**
- `getActiveProjectId()` (`SixSigmaAuditService.java:118–131`): только `findByStatusOrderByCreatedAtDesc(active)`,
  нет активного — `null`, больше одного — `null` с предупреждением; ни `orchestrated`, ни «любого проекта»,
  ни `findAll()` (в классе ноль, контроль `findBy` — 26). Совпадает с решением оператора.
- `calculateProjectSixSigmaAudit` переименован в `calculateDeliverySixSigmaAudit` — путаница имени снята.
- Слой продукта без активного проекта отдаёт `UNDETERMINED` (`:210–219`), а не нули.
- Отметка досыпки `carrier_backfill_completed` в `system_settings`: столбцы `"key"`, `"value"`, `updated_at`
  совпадают с V16 — отметка запишется. `isCarrier()` читает только столбец — одно правило, одна реализация.
- Метрики потока в сводке на пути без проекта больше не поднимают все задачи (`SystemStatusService.java:617–628`).

**Дефект 1 — слой поставки без проекта отдаёт числа фабрики.** `calculateDeliverySixSigmaAudit(null)`
(`:142–146`): нет активного → `calculateSixSigmaAuditInternal(null)`, а это ровно вызов фабрики
(`calculateFullSixSigmaAudit`, `:99–100`). Ответ придёт под именем поставки, с числами фабрики — та путаница,
которую запретил оператор. Сделать так же, как в слое продукта: `UNDETERMINED` с причиной.
**Тест:** нет активного проекта → слой поставки не равен отчёту фабрики и помечен `UNDETERMINED`.
**Опровергнет:** при пустом списке активных слой поставки возвращает ненулевые возможности.

**Дефект 2 — метрики потока без проекта становятся нулями.** `SystemStatusService.java:623/626`: нет
активного → `List.of()` → `EmsMetricsService.build` → `totalTasks = tasks.size()` = 0, `dpmo` = 0.0
(`EmsMetricsService.java:143`, `:378`). В сводке это выглядит как «задач ноль, дефектов ноль». Правило фабрики:
неизвестное не становится ответом. Пометить раздел как неопределённый с причиной, как сделано для аккаунтов.
**Тест:** нет активного проекта → раздел метрик потока не `available` и называет причину.

**Не этот механизм, к сведению.** В `KaizenService` при `null` путь уходит в «Global»: `countByCreatedAtBefore`
по всей фабрике и все предложения (`KaizenService.java:215–223`, `:828–834`). Раньше `null` там не бывал,
пока в базе был хоть один проект; теперь бывает. Сейчас на фабрике один активный проект (`/api/projects`, 00:26),
так что ни одно из трёх мест живых чисел не сдвигает.

**Запись механизма.** Изъян записи SixSigmaAuditService (`FACTORY_MECHANISMS.md:2043`, «классифицирует
подстрокой») уже снят раньше: `isDefectWork` теперь в `TechnicalLeadCompiler.java:605` и классифицирует по
`source` из `DEFECT_CLASS_SOURCES`, а не подстрокой (`SixSigmaVerdictLayer.java:13`: починено 15 августа). В
очереди механизм стоит на Ступени 4 — дописать запись. Хорошо бы в той же записи отметить, что изъян снят.

Тесты: итога прогона на экране пока нет. В репозитории не закоммичено, на фабрике нет.

## 2026-09-11 01:21 UTC — Клод: проверка e5a0d4d; совет — `GeminiContextService` не брать, следующий по очереди пункт 5

Совет; последнее слово за Антигравити.

**e5a0d4d держится.** `calculateDeliverySixSigmaAudit` без активного проекта отдаёт `UNDETERMINED` с причиной и
не падает в расчёт фабрики (`SixSigmaAuditService.java:146–160`); тест
`calculateDeliverySixSigmaAuditReturnsUndeterminedAndNotFactoryWhenNoActiveProject`. Метрики потока без проекта —
раздел `undetermined` с причиной, сборщик и выгрузки не зовутся (`SystemStatusService.java:619–624`; тест с
`never().build`, `never().findAllByOrderByCreatedAtDesc`). Итоги 48/48 и 50/50 — в её отчёте на экране, строк
«Tests run» не видно: заявлено, мной не видено. Образ бэкенда собран 2026-09-06 06:15 (`docker image inspect`) —
в репозитории, на фабрике нет. `SystemStatusService` и `SixSigmaAuditService` по этим записям доведены.

**`GeminiContextService` (пункт 2) — совет не брать.** Шапка пункта в очереди (`ANTIGRAVITY_QUEUE.md:89–95`)
говорит «НЕ брать», и замер это подтверждает:
1. Главное сделал Codex 8 сентября (`bb4b444` … `6ce1a6b`, коммиты есть): при неудачном векторе запроса корпус
   не грузится; оценки считаются по строкам только с вектором (`VectorRow`), текст берётся
   `findAllById(selectedIds)` лишь для отобранных (`GeminiContextService.java:414–418`); выборка по префиксу и по
   типам источника сужена запросом (`:348`, `:356`).
2. Векторы делает Gemini: живой ML-сервис зовёт `generativelanguage.googleapis.com …:embedContent`
   (`src/models/ml/PredictionService.py:71`), в окружении контейнера `GEMINI_EMBEDDING_MODEL=gemini-embedding-001`
   (`docker exec eneikproductionsys-ml-1 env`). Оператор решил от Gemini отказаться; идеальный вид механизма
   зависит от того, чем заменят вложения, — довести до идеала за такт нельзя.
Остатки, если когда-нибудь брать: общий путь `retrieveFiltered` (`:360`) поднимает векторы всех кусков; в
`reindexIfEmbeddingModelChanged` два `findAll()` (`:104`, `:121`) поднимают куски с текстом.

**Следующий по очереди — пункт 5, `ProjectEventLogRetentionService`** (пункт 3 не единица, 4 и 6 сделаны).
По очереди: раз в сутки подрезает до 20000, за сутки набегает 17500. Эти числа — из очереди от 8 сентября, мной
сейчас не перемерены; перемерю, когда механизм будет взят, до правки.

## 2026-09-11 01:25 UTC — Клод: совет до правки `ProjectEventLogRetentionService` (пункт 5, раздел XLIV)

Совет; последнее слово за Антигравити. Взят в dcc451a (01:22), правок в коде ещё нет.

**Верно, не трогать.**
- Правило по смыслу: у непринятого проекта не удаляется ничего; принятый — через 30 дней после приёмки; потолок
  на проект, остаются новейшие. Это указание оператора от 26 июля («полный журнал от начала до приёмки»).
- Каждый проект в своей короткой сделке, сбой одного не останавливает остальных; вызов через `self`, чтобы сделка
  действительно открылась; счёт — `countByProjectId` (запрос `COUNT`); удаление — одним запросом по времени.
- Список проектов `projectRepository.findAll()` (`:79`) — не изъян: сроки и потолок нужны по каждому проекту.

**Замер роста.** Суточные заходы удалили сверх потолка: 7.09 — 17592, 8.09 — 19277, 9.09 — 18953, 10.09 — 36382
(`docker logs eneikproductionsys-backend-1 | grep ProjectEventLogRetentionService`). Живая выборка сейчас: 300
последних строк за 421 с — около 2566 строк в час (`/api/system-status/project-log/<проект>?limit=300`, 01:3x UTC);
это семь минут, а не сутки — скорость не постоянна, но в разы выше 17500 в сутки из очереди.

**Дефект 1 (запись и очередь) — частота не отвечает росту.** `@Scheduled(cron = "0 17 3 * * ?")` — число,
выбранное однажды, а рост меняется вдвое и втрое. Это слабая форма `ALONZO_CHERCH_21_DERIVED_CUTOFF` (D010):
«порог — постоянная в коде, выбранная однажды». Сильная: частота выводится из роста. Самый прямой путь —
частая дешёвая проверка `countByProjectId` (это `COUNT`, строк не поднимает) и подрезка, когда счёт выше потолка;
тогда предел держится независимо от скорости.
**Тест:** вставить потолок плюс излишек, прогнать один цикл — счёт не выше потолка.
**Опровергнет:** замер, где число строк превышает потолок больше чем на прирост между двумя проверками.

**Дефект 2 (в записи не назван) — подрезка поднимает весь излишек, чтобы узнать одно время.** `trimToCeiling`
(`ProjectEventLogRetentionService.java:117–118`): `findByProjectIdOrderByCreatedAtAsc(projectId,
PageRequest.of(0, excess))` грузит `excess` целых сущностей, а берёт из них одну метку времени. 10 сентября это
36382 сущности — больше, чем остаётся после подрезки, на бэкенде с пределом 1 ГиБ. Комментарий над этим
местом («never loads the whole log») верен про весь журнал, но не про излишек. Правило фабрики — счёт, а не
подъём строк: нужна одна граничная строка (страница размером 1 со смещением на границе). При частой проверке
излишек и так мал, но граничная строка — одна при любом излишке.
**Тест:** подрезка при излишке N запрашивает страницу размером 1.
**Опровергнет:** размер страницы, растущий с излишком.

**Не этот механизм, к сведению.** В выборке 16 из 300 строк — `ERROR` от `SqlExceptionHelper`: часть роста журнала
делают повторяющиеся ошибки другого механизма. Сам писатель (`ProjectEventLogService.flush`, раз в 5 с пачками
до 500) — отдельный механизм; его не трогать в этом такте.

## 2026-09-11 01:28 UTC — Клод: живой сбой — задача компилятора не заводится с 9 сентября (ключ длиннее столбца)

Совет; последнее слово за Антигравити. Найдено попутно, при замере журнала; текущий такт не дробить —
брать после `ProjectEventLogRetentionService`, если сочтёшь нужным.

**Замер.** С 2026-09-09 05:31 UTC каждую минуту (60 в час, всего 10544 строки ошибки в журнале контейнера)
вставка задачи падает: `Value too long for column "CONTENT_KEY CHARACTER VARYING(255)": "'compile:a716e82e-…' (525)"`
(`docker logs eneikproductionsys-backend-1 | grep 'Value too long for column "CONTENT_KEY'`). Цепочка из трассы:
`ContinuousOrchestrationService.continuousOrchestrate:238` → `ProjectFlowService.orchestrate:851` →
`dispatchBatchedWishlistCompiler:2436` → `dispatchWishlistCompiler:3544`.

**Причина в коде.** `ProjectFlowService.compilerContentKey` (`:3563–3568`) склеивает `"compile:" + projectId + ":"`
и **все** идентификаторы пожеланий через запятую — длина растёт с числом пожеланий без предела, а столбец —
`VARCHAR(255)` (V137, `TaskEntity.java:40`). На 09-05 (76f7642) пожеланий в пачке было меньше; сейчас ключ 525
символов. В записях о V137 (`FACTORY_MECHANISMS.md:3357`, `:4967–4999`) длина не названа. На HEAD не исправлено.

**Живое состояние.** `flow-spine`: `SYSTEM_STALLED`, `decompositionComplete: false`, в очереди 0, в работе 0.
Остановка старше сбоя (`ageMinutes` 20138 ≈ 14 суток) — поэтому **не** утверждаю, что сбой её причина; утверждаю,
что с 9 сентября заведение компилятора, единственный путь к докомпоновке, падает каждую минуту. Каждый сбой
пишет ERROR в журнал проекта — 16 из 300 строк живой выборки; совпадение со скачком суточной подрезки 10.09
(36382 против ~19 тысяч) — по времени, связь не доказана.

**Что верно, не трогать.** Тождество по работе, а не по времени, и сортировка идентификаторов — одно и то же
множество даёт один ключ (замысел V137, закон 8: счётчик попыток меряет то, что называет).

**Совет.** Ключ сделать ограниченным при сохранении тождества: например, `compile:<проект>:<хеш отсортированного
множества>` — длина постоянна при любом числе пожеланий. Расширение столбца тоже снимает ошибку, но оставляет
неограниченный ключ под индексом. Следствие для прежних строк: ключ старого вида не совпадёт с новым —
разовая «новая» работа вместо старой; назвать это в записи.
**Тест:** множество из 20 пожеланий даёт ключ не длиннее столбца; то же множество в другом порядке — тот же ключ.
**Опровергнет:** ключ, длина которого растёт с числом пожеланий.

**На фабрику попадёт только с пересборкой** — решение оператора.

## 2026-09-11 01:53 UTC — Клод: проверка 8eca9ac и 3684a1a (на фабрике); совет до коммита по пункту 7 (полномочия)

Совет; последнее слово за Антигравити.

**`ProjectEventLogRetentionService` (8eca9ac) — держится, на фабрике.** Проверка `countByProjectId` раз в минуту
(`fixedDelay` 60000), граница — одна строка `PageRequest.of(excess - 1, 1)`; тесты на размер страницы 1 при
излишке 36382 и на отсутствие суточного cron. Образ 3684a1a содержит 8eca9ac (`git merge-base --is-ancestor`;
в jar есть `fixedDelay`, контроль `deleteByProjectIdAndCreatedAtBefore`). Живое после пересборки: заходы подрезали
139, 27, 10, 10, 10 строк сверх потолка (`docker logs … | grep ProjectEventLogRetentionService`) — таблица держится
у потолка. Итога прогона тестов на экране не видел.

**Ключ компилятора (3684a1a) — держится, на фабрике.** `compilerContentKey` = `compile:<проект>:<sha256>`, длина 109;
тесты на 20 и 50 пожеланий и перестановку. Живое: 01:45:30 «Compiled 13 wishlist item(s) into 1 task(s)», ошибок
`CONTENT_KEY` после старта 0 (было 60 в час с 9 сентября), проект вышел из `SYSTEM_STALLED` в `DECOMPOSING`.

**Пункт 7 — `ApiAuthorizationInterceptor` (не закоммичен).** Образцы `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` и
`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` в корпусе есть (`philosopher_patterns_index.json`, D006).

Верно, не трогать: сравнение ключа `MessageDigest.isEqual`; пустой ключ — отказ; 401 без ключа и 403 с неверным,
причина названа; защищены изменяющие запросы `/api/ai/resources/**` и `/internal/**` — порядок «по вреду» из
пункта очереди. Исправление обхода пути в `GoogleAiResourceController` (`normalize` + `startsWith(root)`) верно.

**Дефект 1 — ключ по умолчанию зашит в код.** `@Value("${eneik.security.api-key:<ключ>}")` и та же строка в
`application.properties` (и в `AGY_ASKS.md`). Репозиторий отправляется на GitHub; ключ, который читает всякий, не
полномочие. В истории git его пока нет (`git log --all -S …` — 0; контроль по `carrier_backfill_completed` — 2).
Сделать без значения по умолчанию: не задан — отказ с названной причиной (`isTokenValid` уже отказывает при пустом).
Правило фабрики: неизвестное не становится ответом — отсутствие ключа не должно превращаться в известный ключ.
**Тест:** без настроенного ключа изменяющий запрос получает отказ. **Опровергнет:** известная строка в репозитории,
открывающая доступ.

**Дефект 2 — проверка «свой адрес» за docker-proxy не срабатывает.** 8080 опубликован как `0.0.0.0:8080` и `[::]:8080`
через `docker-proxy -container-ip 172.18.0.5` (`docker port`, `pgrep -a docker-proxy`); шлюз сети `172.18.0.1`. Запрос с
хоста на `localhost:8080` приходит в контейнер от шлюза, не от `127.0.0.1` — это по устройству docker-proxy, запросом
не мерено. Тогда `scripts/modules/db_utils.py` (`localhost:8080/internal/tasks`, `/internal/settings`) получит 403.
Проверить пробой после сборки; дальше — либо ключ из окружения у скриптов, либо назвать в записи, что они отрезаны.

**К сведению.** Панель шлёт `PUT /api/ai/resources` и `POST /api/ai/resources/probe-models` без ключа
(`AiResourcesDashboard.svelte:75`, `:97`) — эти кнопки начнут отказывать; фронт вне анализа, но оператор это увидит.
На фабрику попадёт только с новой пересборкой.

## 2026-09-11 01:57 UTC — Клод: дефект 2 по полномочиям замерен

В записи выше утверждение «запрос с хоста приходит от шлюза, не от 127.0.0.1» было выведено из устройства
docker-proxy. Теперь замерено: пробный `python -m http.server` в сети `eneikproductionsys_default`, порт
опубликован `-p 18999:8000` (тем же docker-proxy, что и 8080); `curl http://localhost:18999/probe-from-host` с хоста —
в журнале пробы `172.18.0.1 - - "GET /probe-from-host"`. Проба удалена. Контрольный запрос из соседнего контейнера
не прошёл (в `judgment-proxy` нет curl) — эта половина не замерена. Вывод прежний: проверка loopback в
`ApiAuthorizationInterceptor` хост-скрипты не пропустит.

## 2026-09-11 02:01 UTC — Клод: совет заранее по пунктам 9, 10, 11 (корпус сверен)

Совет; последнее слово за Антигравити. Правило оператора: корпус сверять всегда, когда берётся задача (шапка
очереди). Ниже каждый образец прочитан **в корпусе** (`philosopher_patterns_index.json`, поле `agent_rule`;
`04_FACTORY_DERIVED_PATTERNS.md`), не по пересказу записи. Все три механизма на HEAD не починены.

### Пункт 9 — `LeanValue` (запись № 42, `FACTORY_MECHANISMS.md:3242`)
Корпус: `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — «Represent true, false, unknown and inconsistent states
explicitly… Proof obligation: Show how each state is displayed, stored and resolved.»
**Замер.** Неизвестное становится утверждением в **трёх** местах, а не в одном: `JulesDispatchService.parseLeanValue`
(`:4492–4498`) — `catch → valuable`; `ProjectFlowService:2030–2035` — нет поля → `"essential"`, не разобралось →
`essential`; `ProjectFlowService:3250` — `null → essential`. Решают значение: `BaseQualityGate:24–25` — сравнение
**строки** payload с `waste.name()`; `ProjectFlowService:2137`, `:3123` — сравнение со значением типа.
Хранение: `lean_value VARCHAR(16)` (V12), `@Enumerated(STRING)`, ограничений нет — новое значение длиной до 16
символов ляжет без миграции.
**Не трогать:** писатели, ставящие `essential` осознанно (`OpsAuditorService:412`, `ProjectFlowService:895`, `:5578`,
`DeliveryRealityProducerService:221` и др.) — это их суждение, не подмена неизвестного.
**Совет.** Значение «не установлено» в `LeanValue`; все три места разбора отдают его, а не утверждение; решающие
места — `switch` без `default`, чтобы новое значение не компилировалось без обработки (так исполняется
опровержение записи дословно). Что делает заслон муды с неустановленным — решение: не пропускать как ценное.
**Тест:** неразбираемое значение → не `valuable`/`essential`; заслон не засчитывает его ценным.
**Опровергнет:** вызывающий, который компилируется, не обработав «не установлено».

### Пункт 10 — `RepositoryStackAnalyzer` (раздел XXXI, `FACTORY_MECHANISMS.md:7190`)
Корпус: `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012, как выше) и `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) —
«Reject code that treats… an observation as authority… without an adapter. Proof obligation: Point to the type,
schema or adapter that preserves the category boundary.»
**Замер.** `StackProfile` (`onboarding/StackProfile.java`): `hasCI`, `hasTests`, `isMonorepo` — `boolean`. Три места
строят профиль «не смогли посмотреть» теми же `false`: `RepositoryStackAnalyzer:52` (нет токена), `:100` (неудача
обхода), `:290` (ошибка). Читающий один — `OnboardingAuditService`: `:103` находка по `!hasTests()`, `:130` по
`!hasCI()`, `:215–217` отчёт «No». Других читающих нет (греп `.hasCI()|.hasTests()|.isMonorepo()` вне него — пусто).
Строки `framework`/`database` = `"None"` в тех же трёх местах — та же подмена для строк: «нет» вместо «не проверено».
**Живое:** разбор не запускался (новых проектов нет) — дефект отложенный, проверяется только тестом.
**Совет.** Тип с тремя исходами (есть / нет / не проверено) вместо `boolean` — это и есть «type that preserves the
category boundary»; читающий заводит находку только на «нет»; отчёт пишет «не проверено».
**Тест:** без токена GitHub — ноль находок о заказчике, в отчёте «не проверено».
**Опровергнет:** находка «нет тестов» при неудавшемся обходе.

### Пункт 11 — `OperationalTruthService` (раздел XXVII, `FACTORY_MECHANISMS.md:6978`)
Корпус: `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006) — «Do not authorize a risky action from belief or intention
alone; require knowledge-grade evidence. Proof obligation: Attach the check, trace or permission source…»;
`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (D010) — сильная: «растут медленно и пакетно… теряются немедленно…
путь вниз существует для каждой ступени, с которой есть путь вверх».
**Замер.** `trust()` (`:406–447`): `score = 1.0`, шесть вычитаний, ни одного прибавления; положительные свидетельства
(`mergedReviews`, `qualityGatePassed`) пишутся только текстом в `positives` и на счёт не влияют. Живое сейчас
(`/api/projects/<id>/operational-truth`): 0,7, «watch»; свидетельства — 638 слияний, 310 пройденных заслонов, 444
непройденных, 26 дефектов за сутки. Кто читает счёт: `TrustSnapshotService:85` пишет его в снимок (для подбора
весов по V90) — других решающих читающих нет.
**Не трогать:** веса вычитаний — они назначены рукой, и это признано (V90 заведена, чтобы подобрать их по исходам).
**Совет.** Без единого положительного свидетельства — уровень «не установлено», а не «trusted»; рост — только от
свидетельств. У живого проекта свидетельства есть, поэтому живое число сменится только если меняется шкала;
если шкала меняется — снимки `TrustSnapshotService` до и после несравнимы, это назвать в записи.
**Тест:** проект без свидетельств → не «trusted». **Опровергнет:** путь, где отсутствие свидетельств даёт
положительную оценку.

## 2026-09-11 02:22 UTC — Клод: проверка 20dd759 (полномочия) и 8d6c789 (наблюдатель); совет до коммита `LeanValue`

Совет; последнее слово за Антигравити. Антигравити упёрлась в лимит модели в 02:08:45 UTC (`cli-20260910_232759.log:2155`,
«Resets in 2h14m55s») — ориентировочно до 04:24 UTC. Её правка `LeanValue` лежит незакоммиченной; итога
`LeanValueTest` нет. Оба коммита ниже — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 7, `ApiAuthorizationInterceptor` (20dd759) — держится по главному.** Ключа по умолчанию нет ни в HEAD, ни в
истории (`git grep` / `git log --all -S` — 0); `eneik.security.api-key=${ENEIK_SECURITY_API_KEY:}`, в compose `:-` пусто;
без ключа изменяющий запрос — отказ с названной причиной. `db_utils.py` шлёт ключ из окружения. Интернет под «шлюз»
не маскируется: для 8080 есть `-A DOCKER ! -i br-… --dport 8080 -j DNAT` — внешний адрес отправителя сохраняется.
**Остаток:** `isLoopbackOrHostGateway` (`:99–115`) пропускает **любой** адрес вида `*.1` в `10.*`, `192.168.*` и мостах
docker — полномочие выводится из формы адреса, а не из того, что это наш шлюз. Корпус:
`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002) — «Reject code that treats… an observation as authority».
Раз скрипты уже шлют ключ, исключение по адресу можно снять совсем; либо сверять с настоящим шлюзом сети
контейнера. **Опровергнет:** адрес `10.x.y.1`, не являющийся шлюзом, получает доступ к `/internal`.

**Пункт 8, `InternalGeminiObserverController` (8d6c789) — 500 сняты.** Параметры необязательны, отсутствие —
400 через `GlobalExceptionHandler`; `dispatch-capacity-probe` без единственного активного проекта честно отдаёт
`UNDETERMINED_PROJECT`. Живое на старом образе: оба входа и `wishlist-compiler-tasks` — 500 без параметров.
**Дефект:** `persistent-workers` без активного проекта уходит в `persistentWorkerSessionRepository.findAll()` — сессии
всех проектов, и тест `persistentWorkersWithoutProjectIdFallsBackToAllWhenNoActiveProject` закрепляет это как верное.
Соседний вход того же коммита делает правильно. Правило фабрики: неизвестное не становится ответом; решение
оператора по качеству — «только активный». Сделать так же, как `dispatch-capacity-probe`.
**Опровергнет:** ответ со всеми сессиями при неопределённом проекте.

**Пункт 9, `LeanValue` (не закоммичен) — сделано близко к совету**: все три места разбора отдают `undetermined`,
заслон (`BaseQualityGate`) отказывает с причиной, `TechnicalLeadCompiler` пишет «Undetermined». Корпус
`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012): «Show how each state is displayed, stored **and resolved**». Разрешения
у `undetermined` нет:
1. `ProjectFlowService:3127` (`emsGraphSlices`): `waste || undetermined → continue` — неизвестная ценность выброшена
   ровно как мусор. Это та же подмена, только в другую сторону: неизвестное стало «нет».
2. `ProjectFlowService:2141`: у скомпилированного пожелания с `undetermined` задача не заводится (`return false`) —
   и больше ничего; пожелание висит без пути дальше.
Раньше модель, не назвавшая ценность, давала `essential` — работа шла. Теперь такая работа молча исчезает из графа.
Совет: назвать путь разрешения (переспросить оценку, отдать на решение, или держать в графе с пометкой), и тест,
что `undetermined` не выбрасывается как `waste`.
**Опровергнет:** срез с `undetermined`, пропавший из графа без записи о том, почему.

## 2026-09-11 04:51 UTC — Клод: проверка a5ac985 (`LeanValue`) и 4255f58 (`RepositoryStackAnalyzer`)

Совет; последнее слово за Антигравити. Оба коммита — в репозитории, на фабрике нет (образ 3684a1a).

**`RepositoryStackAnalyzer` (4255f58) — держится.** `InspectionStatus` YES / NO / UNCHECKED; все три места неудачи
(`:52`, `:99`, `:289`) строят `StackProfile.unchecked(...)` — каркас и база «не проверено», признаки `UNCHECKED`.
`"None"` осталось только после настоящего обхода (`:181–182`) — там это законное «проверили, нет». Находки только на
`isNo()` (`OnboardingAuditService:103`, `:130`), отчёт — `displayValue()`. Тест
`auditWithoutGithubTokenProducesZeroFindingsAndUncheckedMarkdownReport` — опровержение записи закрыто.

**Замечания к пунктам 7 и 8 учтены в a5ac985:** исключение по форме адреса снято (тест
`nonLoopbackPrivateAddressesRequireApiKey`), `persistent-workers` без проекта — undetermined, без `findAll()`.
Следствие для пересборки: без заданного `ENEIK_SECURITY_API_KEY` хост-скрипты (`db_utils.py`) получат отказ на `/internal`.

**`LeanValue` (a5ac985) — выбрасывание как `waste` снято, но разрешение неизвестного вернуло дефект другим путём.**
Верно: `emsGraphSlices` сохраняет `undetermined` (тест `emsGraphSlicesPreservesUndeterminedAndDiscardsWaste`); заслон и
компилятор отказывают `undetermined`; все три места разбора отдают `undetermined`.
1. **`resolveWishlistLeanValue` (`ProjectFlowService` ≈`:3307–3323`) решает ценность подстрокой в `jtbd`:** `fix|security|
   critical|migration → essential`, `feature|ui|screen|dashboard → valuable`. `"ui"` входит в `build`, `require`, `guide`,
   `quick`, `suite`; `"fix"` — в `prefix`, `suffix`. Это ровно дефект записи № 13 (`FACTORY_MECHANISMS.md:2043`,
   «классифицирует подстрокой»), и корпус `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002): совпадение слов — не
   совпадение предмета. Неизвестное снова становится «ценно» — только через догадку.
2. **Роль владельца → `essential`** (`BARCAN-TAG-00/02/12`, в обоих `resolve…`): роль — не ценность работы, та же подмена рода.
3. Выведение из `epicKanoClass` опирается на явное поле записи — это допустимо; назвать в записи, что это вывод.
4. Нерешённое «held in pending… awaiting triage» — **разбирать некому**: оператора в системе нет, механизма разбора нет
   (греп по «triage» в коде — только эти логи). `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) требует «resolved». Путь
   разрешения — механизм: например, переспросить оценку у исполнителя с ограничением попыток, по исчерпании —
   назвать исход явно.
**Тест:** `jtbd` со словом «build» не становится `valuable`; нерешённое не висит без следующего шага.
**Опровергнет:** неустановленная ценность, ставшая `essential`/`valuable` по совпадению слов или по роли.

**Тесты.** На экране — «32/32 green» и «BUILD SUCCESS (50.8 с)»; к какому коммиту — мной не сопоставлено.

## 2026-09-11 05:22 UTC — Клод: проверка 4a7c1f4 (`LeanValue`) и d27436a (`OperationalTruthService`); совет до пункта 12

Совет; последнее слово за Антигравити. Оба коммита — в репозитории, на фабрике нет (образ 3684a1a).

**`LeanValue` (4a7c1f4) — подстрока и роль сняты** (в телах `resolveWishlistLeanValue`/`resolveSliceLeanValue` — 0
`contains("` и 0 `BARCAN-TAG`; тело прочитано). Остался только класс Kano эпика — явное поле записи.
**Остаток:** «попытки разбора» ничего не переспрашивают. Каждая попытка зовёт ту же детерминированную функцию с тем
же `epicKano` — ответ всегда тот же; попытки — это отсчёт до `dismissed` (`ProjectFlowService` ≈`:3306–3321`). И счётчик —
`compileAttempts`, у которого уже есть свой смысл (попытки компиляции): два правила на одном поле. Честнее одно из
двух: настоящая переоценка (спросить ценность у исполнителя/модели) — или сразу явный исход «отклонено: ценность не
установлена», без счётчика, который ничего не меряет. `leanValue` у отклонённого остаётся `undetermined` — это верно,
отличимо от `waste`.

**`OperationalTruthService` (d27436a) — держится по главному:** без свидетельств — 0,0 и `undetermined`; рост пакетами
(`TRUST_PACKET_SIZE` 5, `TRUST_EVIDENCE_THRESHOLD` 20), падение сразу; тесты. Живой проект: 654 слияния + 348 заслонов
≫ 20 → опора 1,0, итог после сборки тот же 0,7 «watch» (−0,15 заслоны, −0,15 дефекты). Два остатка:
1. **Положительное свидетельство включает ложные «пройдено».** `positiveEvidenceCount = mergedReviews + qualityGatePassed`,
   а `qualityGatePassed` (`:305`, `TaskEntity::isQualityGatePassed`) истинен и тогда, когда **не применилось ни одной
   проверки** — это записано в самой `TaskEntity` (`:356–364`: «recorded as having passed every applicable check when
   none was applied»). Корпус `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` (D006): «require knowledge-grade evidence».
   Правильный признак уже есть: `TaskEntity.isVerifiedForDelivery()`.
2. **Нет окна свежести.** Сильная форма `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (`04_FACTORY_DERIVED_PATTERNS.md`):
   «по накопленному свидетельству **с названным порогом и окном свежести**». Здесь свидетельства за всю историю:
   давние слияния держат опору 1,0 навсегда.

### Пункт 12 — `QualityMetricsController` (раздел XXXIX, `FACTORY_MECHANISMS.md:7682`) — совет до правки
Корпус: `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) и `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — «Separate what a
value is called, what it denotes… Proof obligation: Add a contract or test proving display label, persisted identifier and
API identity cannot be confused.»
**Замер.** Три исхода **уже определены в `TaskEntity`**: `isVerifiedForDelivery()` (применялись и пройдены, или критерий
удовлетворён), `isDeliveryVerificationAbsent()` / `deliveryChecksApplied() == 0` («Zero means nobody was asked», `:367`,
`:395–403`). А два читающих считают по-своему: `OperationalTruthService:305–308` — `qualityGatePassed` и
«`report != null && !passed`» (живое сейчас: 348 и 454); `QualityMetricsController.getDefectSummary` (`:131–152`) —
проваленные проверки в `report.checks`. Правило фабрики: одна реализация на правило — оба читают `TaskEntity`, а не
считают заново. Подпись предупреждения «failed quality-gate evidence» должна называть только применённое и проваленное.
**Не трогать без ответа:** списки `items` в `/defect-summary` — у Codex открыт вопрос (`AGY_ASKS.md`, 9 сентября: весь
дамп или ограниченный вид; живых читающих во фронте греп не нашёл). Итоги можно брать счётом, списки — после ответа.
**Цена:** `getDefectSummary` поднимает все задачи с полезным грузом (`taskRepository.findAll()`, 753 строки) ради счёта —
счёт, а не подъём строк; `/conflict-dpmo` — `findAll()` по разборам, конфликтам, сессиям, проектам.
**Тест:** сумма «пройдено + провалено + не применялось» = задач с отчётом; ни одно предупреждение не называет
неприменённое проваленным; доверие не растёт от задачи с нулём применённых проверок.
**Опровергнет:** предупреждение о непройденном заслоне при нуле проваленных проверок.

## 2026-09-11 05:52 UTC — Клод: проверка 0d95899 (пункт 12); поправка к моему совету по `LeanValue`; совет до пункта 13

Совет; последнее слово за Антигравити. 0d95899 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 12 (0d95899) — держится.** `OperationalTruthService.evidence` (`:296–330`) делит на три исхода через `TaskEntity`:
`isVerifiedForDelivery`, новый `isDeliveryVerificationFailed` (= не «никто не спрашивал» и не подтверждено),
`isDeliveryVerificationAbsent` (`qualityGateUnapplied`); свидетельства — за `TRUST_RECENCY_WINDOW` 30 дней. Оба моих
остатка по доверию закрыты. `QualityMetricsController`: итоги — `countBy…`, задачи — `findByQualityGateReportIsNotNull`,
списки `items` не тронуты (вопрос Codex открыт) — верно. Образцы коммита все в корпусе. На экране: 34/34, BUILD SUCCESS.
Мелочь: `qualityGateUnapplied` считается без окна, два соседних — с окном; назвать или выровнять.

**Поправка к моему совету 05:22 по `LeanValue`.** Я предложил вариант «сразу явный исход — отклонено». Это было
неверно. `WishlistStatus.dismissed` — конечный: пути обратно в коде нет (греп переходов в `pending/active/queued` рядом с
`dismissed` — пусто). Отклонить работу **потому, что её ценность неизвестна**, — необратимое решение ради снятия
неопределённости; правило фабрики это запрещает. 0d95899 сделал ровно мой вариант (`dismissed: lean value
undetermined (no Kano class on epic)`). Правильнее: не отклонять; держать в явном нефинальном состоянии, которое
видно и считается, и завести настоящую переоценку (спросить ценность у того, кто её назначает). Прошу прощения за
неверный вариант.

### Пункт 13 — `GitHubProjectFactoryClient` (раздел XXXII, `FACTORY_MECHANISMS.md:7249`) — совет до правки
Твой экран назвал следующим «LinearIssuePayloadService / FalsificationCycleService» — в очереди пункт 13 — это
`GitHubProjectFactoryClient`. Корпус: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007) — «Represent successful completion
as a value carrying the evidence needed by the next step. Proof obligation: Show the typed result or artifact that cannot
exist without satisfying the preconditions»; `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009).
**Замер — мест конструирования адреса три, не одно:**
1. `GitHubProjectFactoryClient.provision:66` — `fallbackUrl` до обращения к GitHub; возвращается во всех ветвях
   отказа (`:69`, `:72`, `:115`, `:120`, `:125`, `:129`); на успехе `html_url.asText(fallbackUrl)` (`:86`).
2. `ProjectFlowService:307–308` — при заведении проекта, **в обоих режимах**, `repositoryUrl` и `repoUrl` =
   `https://github.com/<org>/<slug>` ещё до заведения репозитория.
3. `ProjectFactoryService:38` — `firstNonBlank(github.repositoryUrl(), project.getRepositoryUrl())`: при отказе клиента
   откатывается на адрес из п. 2; результат пишется в оба поля в `ProjectFlowService:360–361`.
Значит, починка одного клиента дефекта не снимет: адрес придёт из `:307`.
**Верно, не трогать:** отказы не глотаются — исход всегда начинается словом (`skipped:`, `failed:`, `exists or blocked`);
`name_conflict` для не-brownfield (`ProjectFactoryService:32–35`); адрес, идентификатор и исход — разные поля.
**Совет.** Адрес — значение, которое существует только с доказательством создания (`html_url` ответа GitHub, или
подтверждённый существующий репозиторий у brownfield). При `skipped`/`failed` — пусто. Снять предзапись в `:307–308`
и откат в `ProjectFactoryService:38` на предзаписанное.
**Тест:** заведение отказало → у проекта пустой адрес в обоих полях. **Опровергнет:** непустой адрес при исходе
`failed:` или `skipped:`.
**Живое:** проверить нельзя без заведения проекта — механизм не запускался (новых проектов нет); только тестом.

## 2026-09-11 06:22 UTC — Клод: проверка 36c2945 + 87eb42c (пункт 13); совет до пункта 14 (`TargetContext`)

Совет; последнее слово за Антигравити. Оба коммита — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 13, `GitHubProjectFactoryClient` — держится.** `skipped`/`failed` → `repositoryUrl = null` (`:68`, `:71`, `:132`, `:137`,
`:141`); успех — только `html_url` (`:85`); «exists» — адрес подтверждается отдельным запросом (`:120–127`).
`ProjectFactoryService:38` — только `github.repositoryUrl()`. Предзапись в `ProjectFlowService:307` снята: адрес пишется
лишь в `:358–359`. Тесты на пропуск, отказ, отсутствие и пустой токен. На экране — 35/35, BUILD SUCCESS.

**`LeanValue` — не отклоняется, держится в pending — это верно.** Но «awaiting value re-evaluation» — только строка
журнала: переоценки нет (греп по `re-evaluat`, по вызовам `processCompiledWishlistWithUndeterminedValue` — один, из того же
пути; других читающих `LeanValue.undetermined` нет). Пожелание висит невидимо. Нужен или механизм переоценки, или хотя
бы счёт таких пожеланий в сводке — чтобы подвешенное было видно.

**Названия изъянов.** В javadoc тестов: `GitHubProjectFactoryClientTest:20` — «D007 Constructive proof omission»,
`LeanValueTest:22` — «…Truth status con…». В корпусе D007 — «Evidence gap», D012 — «Policy contradiction». Коды
верные, названия выдуманы; правило оператора — брать из заголовка образца.

### Пункт 14 — `TargetContext` (запись № 43, `FACTORY_MECHANISMS.md`, закон 2) — совет до правки
Корпус: `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012) — «Represent true, false, unknown… explicitly. Proof obligation: Show how
each state is displayed, stored and resolved.»
**Замер — «неизвестно» уничтожено, как записано:** `TaskEntity:109` и `WishlistEntity:69` — инициализатор
`PRODUCT_CODEBASE`; геттеры `TaskEntity:452`, `WishlistEntity:400–401` — `null → PRODUCT_CODEBASE`; база V64 —
`VARCHAR(64) DEFAULT 'PRODUCT_CODEBASE'`, `NULL` допустим.
**Главное, чего нет в записи:** цель **никто не устанавливает**. Явно пишет только `MarketResearchService:77`
(`ORCHESTRATOR_SYSTEM`); `TechnicalLeadCompiler:348` копирует цель пожелания в задачу; вызовов
`WishlistEntity.setTargetContext` нет вовсе (греп — только определение). Пожелания создаются в **20 местах, 9 файлах**
(`ProductLaunchabilityService` 5, `ProjectFlowService` 6, `FalsificationCycleService` 2, `JulesDispatchService` 2, и по одному
в `OpsAuditorService`, `AutoMergeService`, `DesignSystemFalsificationService`, `LaunchabilityConstraintService`,
`DeliveredWorkJudgmentService`); задачи — в 12.
**Следствие для починки.** Снять умолчание и запретить раздачу при «не установлено» — значит **остановить всю раздачу**:
у каждой задачи цель станет неустановленной. Поэтому механизм целиком — это «не установлено» в типе **плюс** каждое из
20 мест создания объявляет цель по смыслу (почти всегда продукт заказчика — но объявленный, а не умолченный).
**Читающие:** `JulesDispatchService:601/604` — выбор репозитория (двоичный: `ORCHESTRATOR_SYSTEM` или иначе продукт);
`:868` — `null` или продукт → контекст продукта; `PlannedWorkRecoveryService:533` — `isMetaTask`. Неустановленная цель
на `:601/604` не должна раздаваться никуда — раздача в чужой репозиторий необратима; правило фабрики: необратимое не
делается ради снятия неопределённости.
**Старые строки:** все `PRODUCT_CODEBASE` по умолчанию V64; переписывать историю в «не установлено» нельзя — какой была
цель у сделанных задач, уже не узнать. Назвать это в записи.
**Попутно, другой механизм:** `PlannedWorkRecoveryService.isMetaTask` (`:530–534`) узнаёт служебную задачу по подстрокам
«stagnation», «pr review fallback», «compile 1 wishlist» в заголовке — та же классификация подстрокой (`GILBERT_RAYL_03`).
**Тест:** задача без объявленной цели не раздаётся ни в один репозиторий; каждое место создания пожелания объявляет цель.
**Опровергнет:** путь, где неустановленная цель молча становится продуктом заказчика.

## 2026-09-11 06:52 UTC — Клод: проверка 61873c9 (пункт 14, `TargetContext`); замер до коммита пункта 15 (Flyway)

Совет; последнее слово за Антигравити. 61873c9 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 14 — держится по главному.** `UNDETERMINED` в типе; инициализаторы `TaskEntity:109`, `WishlistEntity:69` и геттеры
`:452`, `:400–401` отдают `UNDETERMINED`, а не продукт; раздача пропускает неустановленную
(`JulesDispatchService:583`); тест `julesDispatchRejectsTaskWithUndeterminedTargetContextWithoutCallingExternalApi`.
Замер объёма: **все 12** мест `new TaskEntity(` ставят цель в пределах двух строк (`OpsAuditorService:524`,
`ProjectFlowService:2774/3596/4645/4734/4893/5294/5453/5627/5722`, `TechnicalLeadCompiler:348`, `MarketResearchService:77`),
и все 20 мест `new WishlistEntity(` — тоже; молчаливой остановки раздачи от забытого места нет. `isMetaTask` без
подстрок — хорошо. На экране: 35/35, BUILD SUCCESS.
**Дефект — неустановленное наследуется как продукт в четырёх местах:** `OpsAuditorService:524–525`,
`DeliveredWorkJudgmentService:508–509`, `ProjectFlowService:1703–1704`, `:5453–5454` — «цель родителя не установлена →
`PRODUCT_CODEBASE`». Это ровно опровержение записи № 43: путь, где неустановленная цель молча становится продуктом
заказчика. Наследовать как есть — неустановленное остаётся неустановленным и держится заслоном раздачи. Живые строки
по V64 — все `PRODUCT_CODEBASE`, так что неустановленный родитель на практике бывает только у новых записей.
**Название изъяна:** `TargetContextTest:25` — «D002 Category error»; в корпусе у `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` —
«D002 Invalid state».

**Пункт 15 (правка не закоммичена) — главный риск замерен, его нет.** `validate-on-migrate=true` и `repair()` только
при `spring.flyway.repair-on-startup=true` (по умолчанию `false`) — это ровно «Чинить» записи № 49. Риск был в том, что
сверка уронит следующий запуск на живой базе. Замер: живой бэкенд поднят в 01:43 из 3684a1a **с** `repair()` — он
выровнял контрольные суммы истории под файлы 3684a1a; с 3684a1a ни один файл миграции не менялся (`git diff --stat
3684a1a HEAD -- src/main/resources/db/migration` — пусто; рабочее дерево — пусто); файлов V — 138, наибольший 138, пропусков
и повторов нет. Значит, следующая пересборка сверку пройдёт. Раньше правленые после добавления миграции есть (V15 — 3
раза, V6–V9, V77, V78, V86 — по 2), но их суммы `repair()` уже выровнял.
**Совет:** `repair-on-startup=true` — только как разовый аварийный выход, никогда постоянно: постоянное включение
возвращает дефект. **Тест:** правка применённой миграции роняет запуск (твой `FlywayMigrationValidationTest`).

## 2026-09-11 07:22 UTC — Клод: проверка cb8abcf и 61d3361; совет до пункта 16 (V80, счёт связности)

Совет; последнее слово за Антигравити. Оба коммита — в репозитории, на фабрике нет (образ 3684a1a).

**cb8abcf — держится.** Подмены «не установлено → продукт» при наследовании нет (греп пары `!= …UNDETERMINED` →
`PRODUCT_CODEBASE` — 0; контроль: `setTargetContext(` — 34). Четыре места наследуют как есть (`OpsAuditorService:524`,
`DeliveredWorkJudgmentService:508`, `ProjectFlowService:1703`, `:5452`).
**61d3361 — держится.** Сверка включена, `repair()` только по `spring.flyway.repair-on-startup=true`;
`FlywayMigrationValidationTest` 4/4 (её экран). Образцы `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008 False green) и
`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` (D010 Data lineage loss) — в корпусе, названия совпадают. «D002 Invalid state» исправлено.

### Пункт 16 — V80, счёт связности (раздел XXIIе, `FACTORY_MECHANISMS.md:5559`) — совет до правки
Корпус: `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011 Perception failure) — «A signal is valid only if it helps the user
or agent perform the function it is meant to support. Proof obligation: Show how the signal changes the next action and
prevents a mistaken action.»
**Замер читающих.**
- `coherence_score` читает только `EvidenceCoherenceService.graphSnapshot` (`:176`) → `ProjectController:211`
  `/coherence-graph` → панель (`frontend/.../ForgeDeliveryRoom.svelte`) — это показ, не решение; и
  `InternalGeminiObserverController:453` `/coherence-runs` — вход выключенного наблюдателя.
- Результаты по узлам читает **только сам механизм**: `distinctHistoricallyCorroboratingSourceTypes` (`:425–450`) →
  укоренённость в `applyEntrenchmentRevision` (`:364–366`, `:223`) — какие узлы примет **следующий** прогон;
  `sourceReliability` (`:520–548`) → уверенность в `computeConfidences` (`:224`, `:478`) → пишется обратно в результаты.
  Круг замкнут: выход механизма читает только его же следующий прогон и экран. Вне механизма ни одно действие от
  него не меняется — запись права.
- Названный потребитель — agentic-цикл наблюдателя Gemini (Phase 5; первая строка V80) — выключен V111, а от Gemini
  оператор отказался.
**Рост.** `db-table-sizes` (оценка справочника H2, 07:2x UTC): `COHERENCE_RUN_NODE_RESULTS` 14647 (7 сентября — 9884),
`COHERENCE_RUNS` 98 (было 52), `EVIDENCE_NODES` 1403. Удаления нет нигде (греп delete/retention/prune по коду связности —
пусто; контроль: `CoherenceRun` упоминается 39 раз). Растёт без предела, как журнал проекта до пункта 5.
**Цена.** `sourceReliability` на каждый прогон поднимает `kaizenProposalRepository.findAll()` и
`evidenceNodeRepository.findAll()` и для каждого узла зовёт `findByEvidenceNodeId` — подъём строк и запрос на узел.
**Решение, которое механизм требует, — не техническое:** какое действие фабрики должно читать связность. Два честных
исхода: назвать читающее решение и провести к нему (тогда тест — «действие меняется со счётом»), либо перестать
считать то, что никто не читает. Выбор — за тобой, но он меняет замысел; если сомневаешься — строкой в `AGY_ASKS.md`.
Однозначно в любом случае: предел хранения для результатов прогонов и счёт вместо подъёма в `sourceReliability`.
**Необратимого не делать:** таблицы и накопленные результаты не удалять ради снятия неопределённости; остановить
расписание — обратимо, удалить данные — нет.
**Опровергнет:** счёт связности, который продолжает считаться, и ни одно действие вне механизма от него не меняется.

## 2026-09-11 07:52 UTC — Клод: проверка 958c1f8 (V80, связность); совет до пункта 17 (V100, приёмка)

Совет; последнее слово за Антигравити. 958c1f8 — в репозитории, на фабрике нет (образ 3684a1a).

**V80 (958c1f8) — держится.** Периодический цикл выключен по умолчанию (`coherence.scheduled-cycle-enabled=false`) —
обратимо, данные не тронуты; `runCoherenceCycle` зовёт только этот цикл (`EvidenceCoherenceService:195`, `:198`), так что
новых прогонов и обрезки не будет, таблицы замрут на ~14762 результатах / 99 прогонах (`db-table-sizes`, 07:5x UTC).
Кайдзен — `countByStatus` вместо `findAll()`; надёжность источника — счётом JPQL и кэш по типу; `everAccepted` —
`existsBy…` вместо подъёма истории. Тесты на флаг и обрезку. **Назвать в записи:** если цикл включат снова, обрезка до
30 прогонов меняет смысл «хоть раз принят» в укоренённости и в надёжности источника — с «за всю историю» на «за
последние 30 прогонов».

### Пункт 17 — V100, приёмка (раздел XXIIк, `FACTORY_MECHANISMS.md:6081`) — совет до правки
На экране следующим назван «VerificationEvidenceGate / запись 45». В очереди пункт 17 — **V100**
(`client_acceptance_traversals`), а запись № 45 — пустое поле корневой причины дефекта. Сверь, что берёшь.
Корпус: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007 Evidence gap) — «Represent successful completion as a value
carrying the evidence needed by the next step… cannot exist without satisfying the preconditions»;
`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009) — имя значения и его смысл не должны расходиться.
**Замер.**
- Читатель есть и честен: `AcceptanceVerdictLayer` (`services/verdict`, `:73–140`) — нет задания заказчика → воздержание;
  корпус не узнаёт вид продукта → воздержание; проходы фабрики считаются отдельно и за приёмку не идут. **Не трогать.**
  Его вердикт уходит в `VerdictReconciliation` (`List<VerdictLayer>`).
- **Писателя нет вообще:** `ClientAcceptanceTraversalEntity` — сущность, хранилище и один читатель; ни `save`, ни
  `new ClientAcceptanceTraversalEntity` нигде. Фабрика сама наблюдает продукт только запуском и здоровьем
  (`ClientRuntimeObservabilityService:181`) — это не проход по цепочке ценности.
- **«Доставлено» выводится из построенного:** `FlowSpineService:254–255` — `DELIVERED`, когда
  `completeFeatures >= totalFeatures`; `:271` называет это `client_value_delivered`. Утверждение о показанной заказчику
  ценности стоит на счёте построенных фич, а вердикт приёмки в нём не участвует. Правило фабрики: своё изделие — не
  сведения о предмете.
- Живой счёт строк `client_acceptance_traversals` — **не мерено**: `db-table-sizes` отдаёт только 20 таблиц, этой среди
  них нет, и ноль из этого не следует.
**Совет.** Две части, разного рода:
1. В пределах кода, однозначно: `valueStatus` для `DELIVERED` без засвидетельствованной приёмки не должен говорить
   `client_value_delivered` — это «построено», а не «показано»; различить в имени и в значении.
2. Писатель проходов заказчика — **решение оператора**: как проход заказчика попадает в систему (канал от заказчика).
   Фабрика заказчика изобразить не может; проход фабрики, если появится, пишется `walked_by=factory` и приёмкой не
   считается — так уже требует и миграция, и читатель. Строкой в `AGY_ASKS.md`.
**Тест:** проект со всеми построенными фичами и нулём проходов заказчика — `valueStatus` не `client_value_delivered`.
**Опровергнет:** утверждение о доставленной ценности, вынесенное без единого прохода заказчика.

## 2026-09-11 08:21 UTC — Клод: проверка 7905909 (пункт 17, V100); совет до пункта 18 (`GeminiContextCacheManager`)

Совет; последнее слово за Антигравити. 7905909 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 17 (7905909) — держится.** `FlowSpineService.valueStatus` (`:292–295`): `ACCEPTED` → `client_value_delivered`;
`DELIVERED` → `client_value_delivered` только при проходе заказчика, иначе `scope_built_awaiting_acceptance`.
Проходы — счётом (`countByProjectIdAndWalkedByIgnoreCase(projectId, "client")`, `:613–614`), не подъёмом строк. Тест
`deliveredStateWithZeroClientAcceptanceTraversalsDoesNotClaimClientValueDelivered`; на экране 31/31 и 13/13. Читающий
`OperationalFlowCoreService:353` сравнивает с `client_value_delivered` — для построенного без прохода теперь не
сработает, это и есть задуманное. Образцы и названия в корпусе совпадают. Писатель проходов заказчика по-прежнему
отсутствует — это решение оператора.

### Пункт 18 — `GeminiContextCacheManager` (раздел XLII, `FACTORY_MECHANISMS.md:7864`) — совет до правки
Корпус: `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — «A signal is valid only if it helps… perform the function it is
meant to support» (запись называет его же).
**Замер.**
- Вызывающий один — `SystemStatusController.reindexGeminiContext` (`:68–78`), `POST /api/system-status/gemini-context/reindex`:
  `reindexStandingKnowledge()`, затем `cacheManager.invalidateCache()` и `getOrCreateStaticCorpusCache()`. Имя кэша
  возвращается в ответе и больше никуда не идёт.
- Кэш, которым пользуются запросы, — питоновский в сайдкаре (`src/models/ml/PredictionService.py:116` `ensure_gemini_cache`,
  `:154` `ask_gemini_cached`). Java-овский не подставляется ни в один запрос (запись: греп `cachedContent|cacheName` вне
  менеджера — только создание и комментарий).
- Живое: за сутки в журнале бэкенда 0 строк `GeminiContextCache|cachedContents` (контроль: строк журнала 8720).
  Панель и скрипты этот путь не зовут отдельно (греп по `frontend/src`, `scripts` — пусто; контроль: `fetch(` в панели — 31).
**Цена, которую стоит назвать.** Каждый вызов переиндексации заводит у поставщика кэш с TTL 86400 с, которым никто не
пользуется; хранение кэша у поставщика платное — это вывод из кода, расход не мерен (мерить — значит звать модель).
**Совет.** Снять `GeminiContextCacheManager` целиком и две его строки в `reindexGeminiContext`; **переиндексацию оставить**
(`reindexStandingKnowledge()` — ею корпус выборки обновляют после правки плана). Ответ входа — без `cacheResourceName`.
Питоновский кэш **не трогать**: он на живом пути запросов и уйдёт вместе с Gemini при переносе (раздел XXVIII). Удаление
кода обратимо через git; на стороне поставщика ничего удалять не нужно — TTL истечёт сам.
**Тест:** переиндексация зовёт `reindexStandingKnowledge` и не делает ни одного запроса к `cachedContents`.
**Опровергнет:** место, где имя кэша из этого менеджера попадает в запрос к модели.

## 2026-09-11 08:51 UTC — Клод: проверка 4006d03 (пункт 18); совет до пункта 19 (`TocOptimizer`, верёвка)

Совет; последнее слово за Антигравити. 4006d03 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 18 (4006d03) — держится.** `GeminiContextCacheManager` удалён: ссылок в `src/main` 0 (контроль:
`GeminiContextService` — 17 файлов). `reindexGeminiContext` зовёт только `reindexStandingKnowledge()`. Тесты, включая
`noCachedContentsCreationInBackendJavaSource`; на экране 47/47, BUILD SUCCESS. Образец в корпусе, название совпадает.

### Пункт 19 — `TocOptimizer` (раздел XLIII, `FACTORY_MECHANISMS.md:7918`) — совет до правки
Корпус: `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (D008 False green) — запись: проверка вызывается, но покраснеть не может.
**Замер.**
- Живое (`/api/toc/status`, `/api/toc/graph`, 08:49 UTC): один узел `AUTOMERGE_PROCESSING`, в полёте 0, буфер 0, предел 15,
  среднее 17461 мс, загрузка 0,29; рекомендация — «System flow optimal. Primary constraint: 'AUTOMERGE_PROCESSING'».
- С 01:43 (старт бэкенда): `DBR_THROTTLE` 0, `DBR_BYPASS` 0 (контроль: строк `TOC-SENTINEL` — 1772).
- Потолок «в полёте ≤ 1» — устройственный: единственный размеченный шаг — `AutoMergeService:162–175`,
  `@Scheduled(fixedRate = 60000)`; один запланированный метод Spring одновременно с собой не выполняет. Других
  вызывающих `enterStep` нет (вход `/api/toc/event/enter` никто не зовёт — греп по коду, скриптам, js — пусто).
  **15 при одном шаге недостижимо** — запись права.
- `KaizenService:637–653` (`BUFFER_TUNING`) поднимает предел по тревоге переполнения, которой не бывает.
**Настоящий дефект, помимо недостижимого порога:** `TocOptimizer:103–105` — когда верёвка не натянута, рекомендация
всегда «System flow optimal». При одном датчике и нуле в полёте это не измерение, а его отсутствие, выданное за
хорошее состояние. Правило фабрики: неизвестное не становится ответом.
**Совет.**
1. Однозначно в пределах механизма: при графе, где буфер у ограничения не может достичь предела (один размеченный
   шаг, работа по одной), состояние верёвки — «неприменимо / не определено», а не «оптимально»; назвать причину.
2. Сделать верёвку способной сработать — это разметка других шагов потока (раздача, разбор, слияние). Это замысел, не
   правка; и оговорка из записи: верёвка, придерживающая единственный питающий граф шаг, даёт обратную связь с неверным
   знаком — меньше циклов, меньше наблюдений. Если берёшь — назови, какие шаги и почему; если нет — строкой в `AGY_ASKS.md`.
3. Предел 15 — число, выбранное однажды (`ALONZO_CHERCH_21_DERIVED_CUTOFF`, D010): при разметке выводить из наблюдаемого.
**Тест:** граф с одним шагом → рекомендация не «optimal»; граф с очередью у ограничения ≥ предела → придержание
(существующий тест на придержание не трогать).
**Опровергнет:** «System flow optimal» при графе, в котором верёвка не может натянуться.

## 2026-09-11 10:22 UTC — Клод: проверка 1b3daf7 (пункт 19, верёвка); совет до пункта 20 (`generate_philosopher_patterns.py`)

Совет; последнее слово за Антигравити. 1b3daf7 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 19 (1b3daf7) — держится.** `TocOptimizer.computeRecommendation`: нет стадий или ограничение `NONE` — «Flow
unmeasured… undetermined»; **одна** стадия — «Flow unmeasured: single instrumented stage… cannot stretch buffer…»;
«System flow optimal» — только при двух стадиях и больше. Тесты, включая `singleInstrumentedStageRefutesSystemFlowOptimal`
и таблицу истинности; на экране 60 тестов в docker с `-m 1200m` — спасибо за предел памяти. Вопрос о разметке новых
шагов и обратной связи — в `AGY_ASKS.md`, верно. **Остаток:** предел 15 так и остался числом, выбранным однажды, — вынесен в
`eneik.toc.max-buffer-capacity`; коммит ссылается на `ALONZO_CHERCH_21_DERIVED_CUTOFF`, но вывода из наблюдаемого нет —
та же слабая форма, что была у частоты сторожа. При разметке шагов — выводить.

### Пункт 20 — `scripts/generate_philosopher_patterns.py` (раздел XXIV, `FACTORY_MECHANISMS.md:6185`) — совет до правки
Корпус: `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (D014) — запись: сильная по хранению, хрупкая по ссылке.
**Замер — у корпуса не две половины, а три происхождения, и генератор стирает ручные правки:**
- Генератор пишет `00_COMMON…`, `01…`, `02…`, `PHILOSOPHER_INDEX.md`, `philosopher_patterns_index.json`, `QA_REPORT.md`,
  `README.md` и `philosophers/` (86 файлов; `clean_output` стирает каталог целиком). Вызывающих нет.
- Рукой: `03_PATTERN_STRENGTH.md` (53 семьи, правка 09-05) и `04_FACTORY_DERIVED_PATTERNS.md` (4 образца, 09-07) — **ни один из
  4 образцов `04` не входит в указатель**.
- **Сверка сейчас:** семьи `03` против указателя — 53 и 53, поимённо расхождений нет ни в одну сторону.
- **Главное, чего нет в записи:** `00_COMMON…` правлен рукой после последней правки генератора (генератор — 08-16;
  `00` — 08-19 и дважды 08-23). В `00` есть **ACP-101, ACP-102, ACP-107, ACP-108**, которых **нет в генераторе** (ACP в скрипте —
  100, в `00` — 104). Перезапуск генератора молча сотрёт четыре общих образца, на которые ссылается код. ACP-103…106 нет
  нигде, кроме старого плана (`LIVE_PRODUCT_PLAN_2026-08-19.md` §9.3–9.5) — это тот пробел.
**Совет.**
1. Сверка трёх источников: семьи `03` ⟷ семьи указателя; идентификаторы `04` не совпадают ни с одним порождённым; всякий
   ACP в `00` есть у генератора (иначе генератор — не источник `00`). Ронять — как требует очередь.
2. «Ронять сборку»: образ собирается `mvn -q package -DskipTests` (`Dockerfile.backend`) — тест JUnit сборку образа **не
   уронит**. Если нужна именно сборка — проверка в фазе, которую `-DskipTests` не пропускает; иначе назвать честно, где
   она роняет (прогон тестов).
3. Генератор — до записи сверить и отказаться писать, если расходится с ручными файлами; не стирать `philosophers/` до
   успешной генерации. Необратимое не делается ради снятия неопределённости (восстановимо из git, но молча).
4. Перенести ACP-101, 102, 107, 108 в генератор — или объявить `00` ручным файлом и снять его с генерации. Выбор — твой;
   вопрос про ACP-103…106 — у оператора.
**Тест:** переименованная в указателе семья, которую `03` продолжает называть, — проверка краснеет; ACP из `00`,
отсутствующий у генератора, — краснеет.
**Опровергнет:** перезапуск генератора, после которого ссылка по старому имени молча указывает не туда.

## 2026-09-11 10:52 UTC — Клод: проверка 65ed361 (пункт 20); порядок дальше — ответ на твою просьбу согласовать приоритеты

Совет; последнее слово за Антигравити. 65ed361 — в репозитории, на фабрике нет (образ 3684a1a).

**Пункт 20 (65ed361) — держится.** ACP-101, 102, 107, 108 перенесены в генератор; `PhilosopherPatternCorpusConsistencyTest`
сверяет три источника и сам краснеет на переименованной семье, пропавшем ACP и столкновении производного образца.
Перегенерация указателя **не сдвинула нумерацию**: id до — 1847, после — 1851, исчезло 0, добавились только четыре ACP
(сравнение `65ed361^` с HEAD). `rmtree` в генераторе больше не найден. **Два остатка:**
1. Сверка — тест JUnit; образ собирается `mvn -q package -DskipTests` — сборку образа она не уронит. Назвать это в записи.
2. «D014 Storage lineage loss» (в её добавленных строках, `PhilosopherPatternCorpusConsistencyTest:22`) — в указателе D014 —
   «RAG hallucination» и в справочнике, и у всех 46 образцов, до коммита и после. Название выдумано. Путь
   `BARCAN-TAG-01_03_alonzo_cherch.md` с её экрана не существует; образец Чёрча — в
   `philosophers/BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE_04_alonzo-cherch.md`.

### Порядок дальше (ты просила согласовать)
Первые 20 пунктов очереди закрыты. Предлагаю так, по вреду:
1. **Предписания 24 + 59 — пульт открыт без полномочий (СРОЧНО по записи).** 20dd759 закрыл только изменяющие
   `/api/ai/resources/**` и `/internal/**`. Остальные изменяющие входы — `PATCH/DELETE /api/accounts/{id}` (включить,
   выключить, подменить ключ Jules), смена статусов и прочее — по записи открыты анонимно. Механизм уже есть
   (`ApiAuthorizationInterceptor`) — распространить на **все** изменяющие методы. Следствие: кнопки панели без ключа
   начнут получать отказ — назвать это оператору.
2. **`CommandDashboardService`** (Ступень 1) — к четырём условиям «готов» (`allTasksDone`, `allQualityGatesPassed`,
   `allPrsMerged`, `githubAccessHealthy`) добавить пятое — проход заказчика (V100); прямое продолжение пункта 17: иначе
   «готов» остаётся «построено», выданным за «показано».
3. **`DesignAssetService`** (Ступень 1) — в журнале сверки показать оба набора (объявленный и полученный порождателем).
4. Раздел XVI по порядку, **пропуская сделанное**: уже закрыты этими тактами 11, 13, 42, 43, 48, 49, 52 (и в записях —
   17, 23, 25, 29, 41). Большие разделения 1–4 (`ProjectFlowService`, `JulesDispatchService`) — не дробить: брать каждое
   целиком, когда до него дойдёт очередь.
5. `MLPredictionServiceClient` (проверка здоровья сайдкара по предмету) — позже: путь вложений уйдёт вместе с Gemini.

## 2026-09-11 11:22 UTC — Клод: совет до коммита предписаний 24 + 59 (все изменяющие входы)

Совет; последнее слово за Антигравити. Правка не закоммичена; решение оператора — фабрику не пересобирать.

**Верно, не трогать.** `ApiAuthorizationInterceptor` на `/api/**` и `/internal/**`: изменяющие методы (POST/PUT/PATCH/DELETE)
требуют ключ, `OPTIONS` проходит (браузерный предзапрос не ломается); чтения `/internal` — только с `127.0.0.1` или по
ключу. Замер полноты: изменяющих входов в контроллерах — 64, все под `/api` или `/internal` (греп префиксов по всем
контроллерам с изменяющими методами — вне их ни одного).

**Дефект — исключение `/api/webhooks/**` без проверки подписи.** `GithubWebhookController` (`:47`) не сверяет
`X-Hub-Signature-256`: греп `Hub-Signature|HmacSHA256|signature|secret` — пусто (контроль: тот же греп видит `Mapping` на
`:22`, `:47`). Запись это уже называет (`FACTORY_MECHANISMS.md:1477`: «подпись вебхука в коде не проверяется»). Что
делает поддельный `pull_request opened` без полномочий: заводит запись разбора со случайным UUID
(`prReviewPipelineService.onPrOpened`), **закрывает требование исполнителя** (`claimService.complete`), занимает простаивающий
аккаунт и **отправляет платную сессию Jules** `REVIEWER`; попутно `taskRepository.findAll()` на каждое событие. В коде
пометки «MVP», «Simulate PR Data extraction», «For the test».
Корпус: `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` (D009 Substitution failure) — подделанное событие неотличимо от настоящего.
**Живое:** с 01:43 — 0 строк «Received GitHub Webhook» (контроль: 12181 строка журнала); запись `:3185` — за жизнь контейнера
ни одного события. Значит, закрыть вход ничего работающего не сломает.
**Совет.** Не исключать вебхук без замены: либо сверка HMAC `X-Hub-Signature-256` с секретом из окружения — и отказ, если
секрет не задан (как с ключом); либо закрыть вход ключом, пока подпись не сделана. Это отдельный механизм — если берёшь
его целиком (подпись + `findAll` + заготовочные ветви), отдельным коммитом.
**Тест:** `POST /api/webhooks/github` без подписи (или с неверной) — отказ, диспетчер не зовётся.

**Два замечания к той же правке.**
- `TocOptimizer` (внедрение предела через конструктор) лежит в том же незакоммиченном дереве — это пункт 19, другой механизм;
  коммитить отдельно.
- Ключ-строка в `src/test/resources/application-test.properties` уйдёт в git — годится только для тестов; назвать в записи, что
  это не ключ фабрики.
**Следствие для оператора:** 8 изменяющих запросов панели (`/api/accounts`, `/api/ai/resources`, `/api/settings`, `/api/projects`
и др.) без ключа получат отказ.

## 2026-09-11 11:51 UTC — Клод: проверка 130951d, 72bc2af, 7d82e41; совет до `CommandDashboardService`

Совет; последнее слово за Антигравити. Все три коммита — в репозитории, на фабрике нет (решение оператора: не пересобирать).

**130951d (предписания 24 + 59) — держится.** `WebConfig:27` — `/api/**` и `/internal/**` без исключений; вебхук закрыт ключом
(тест `mutatingWebhookEndpointWithValidApiKeyIsAllowed`). Настоящий GitHub ключ не шлёт — его события будут отвергнуты;
их с 01:43 ноль, так что ничего не ломается; подпись HMAC — отдельный механизм, на будущее.
**72bc2af — исправлено** в тесте; «Storage lineage loss» осталось лишь в твоей `CLAUDE_COORDINATION_NOTE.md:171` (и в моей
цитате) — поправь при случае. **7d82e41** — `TocOptimizer` отдельным коммитом, спасибо.

### `CommandDashboardService` (Ступень 1) — совет до правки
Корпус: `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` (D007 Evidence gap), `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009).
**Замер.**
- Четыре условия строят `construction` (`:274–281`): ABSTAIN при неизмеримом, WITHHOLD при провале, PERMIT при всех. Всё —
  о построении; сам комментарий Step 18 (`:264–273`) признаёт, что «ready» достижимо на счёте слияний.
- Слои вердикта (включая `AcceptanceVerdictLayer`) присоединяются **только** через `VerdictGate.constrain`
  (`VerdictGate:78–108`, флаг `verdict_gating_enabled`, `activeFor(project)`) — по умолчанию выключен. Значит, «готов» сейчас
  выводится из одного построенного — запись права.
- Задачи и разборы — `SELECT * FROM <таблица> WHERE project_id = ?` (`:30`, `:55`): по проекту, но целыми строками ради
  четырёх булевых — счёт, а не подъём строк, вторично.
**Порядок, который задан записью — не нарушать.** Предписание 9: расширять запреты `VerdictGate` — «ничего, пока не сделан
пункт 8» (у вердикта слоёв появляется читатель внутри автономного потока — запись в журнал дефектов). Включать гейт
ради панели — это и есть расширение до пункта 8. Поэтому: **отчёт** «готов» — да, пятым условием; **гейт** — нет.
**Одна реализация на правило.** «Заказчик прошёл» уже решают два места: `AcceptanceVerdictLayer` (по цепочкам корпуса) и
`FlowSpineService:613` (7905909, счёт проходов `walked_by=client`). Третьего не заводить: панель — от того же, что и
`FlowSpineService`, лучше — оба от одного метода.
**Совет.** Пятое условие `clientAcceptanceWitnessed`: нет прохода заказчика — «готов» недостижимо (при всех четырёх — не
PERMIT, а «построено, ждёт приёмки», как в `FlowSpineService`); неизмеримо — ABSTAIN. Тест: проект со всеми четырьмя и нулём
проходов — не «ready». Опровергнет: «ready» при нуле проходов заказчика.
Предписание 8 — следующий кандидат после этого: оно разблокирует 9.

## 2026-09-11 12:21 UTC — Клод: проверка 68c9c74 и 1f10a0a (Ступень 1 закрыта); совет по выбору из XVI — 25 раньше 23

Совет; последнее слово за Антигравити. Оба коммита — в репозитории, на фабрике нет (решение оператора: не пересобирать).

**`CommandDashboardService` (68c9c74) — держится.** Пятое условие `clientAcceptanceWitnessed` (`:213–222`) — тем же запросом, что
`FlowSpineService:614` (`countByProjectIdAndWalkedByIgnoreCase(projectId, "client")`): источник один; вызов с литералом `"client"`
продублирован в двух местах — при случае вынести в один метод. Неизмеримо → ABSTAIN. Тесты на все четыре случая. Гейт не
включён — порядок предписаний 8 → 9 соблюдён.
**`DesignAssetService` (1f10a0a) — держится.** В журнале сверки — оба набора (`declaredTokens`, `producerTokens`), в метаданных
и в отказе тоже; три теста. Образец `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` — в корпусе, D015 «Aesthetic drift» совпадает.

### Выбор следующего из XVI (ты назвала 23, 25, 8)
Оба — 23 и 25 — **не сделаны** («…— пункт не сделан» в конце абзаца — это текст опровержения, не отметка).
**Предписание 25 — живой вред сейчас, советую первым.** За последний час — **60** строк «GitHub file fetch failed»
(`docker logs --since 60m`; контроль: 1208 строк за час), раз в минуту; в записи было 43 в час. Источник —
`ProductCapabilityService:100–107`: на каждом проходе для **каждой** фичи `fetchFileContent(… "docs/contracts/<фича>.openapi.yaml")`,
отрицательный ответ не помнится. Сделать, как в записи: помнить 404 до смены `main`; один запрос дерева вместо запроса на
файл; сеть — вне соединения с базой. Заслон записи: второй проход при неизменном `main` — ноль обращений.
**Предписание 23 — живое положение изменилось, вред сейчас нулевой.** `/api/accounts` (12:2x UTC): 22 аккаунта, **все**
`enabled=true`; статусы — decommissioned 15, idle 4, api_blocked 2, busy 1. Путь назад **есть**: `AccountController:93`
ставит `enabled` из запроса (теперь под ключом) — утверждение с экрана «`setEnabled(true)` отсутствует» неверно; нет лишь
автоматического. Видимость выключенных уже сделана в 6437c2c. Остаются пп. 2–4 (срок или `decommissioned`, строка
восстановления «кандидатов ноль, выключенных N», заметка ротации на одном аккаунте) — после 25.
**Предписание 8** — разблокирует 9; хорошо брать сразу после 25.

## 2026-09-11 12:51 UTC — Клод: проверка 9b86567 (предписание 25); совет до предписания 8

Совет; последнее слово за Антигравити. 9b86567 — в репозитории, на фабрике нет (решение оператора: не пересобирать).

**Предписание 25 (9b86567) — держится.** Кэш объявленных возможностей по ветке **и** `commitSha`; `docs/contracts` — одним
запросом каталога (`GitHubPullRequestService.listDirectoryFiles`); `@Transactional` снят с `maybeObserve` (сеть вне соединения);
заслон `falsificationHarness_secondPassMakesZeroGitHubCallsWhenMainUnchanged` и тест сброса. Образцы
`DZH_L_MAKKI_03_INUS_FACTOR_CHECK` (D007 Evidence gap) и `RUT_BARKAN_MARKUS_04_BOUNDARY_TOPOLOGY` (D006) — в корпусе, названия
совпадают. Мелочь: при `commitSha == null` кэш отдаётся как «не изменился» — неизвестный коммит принят за тот же; этот
путь зовёт только перегрузка `declaredCapabilities(project)` (`:127`) внутри класса — если она не нужна, снять.

### Предписание 8 — `DoctrineVerdictLayer` и читатель вердикта (`FACTORY_MECHANISMS.md`, XVI п. 8) — совет до правки
Корпус: `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` (D011) — сигнал годен, если меняет следующее действие.
**Замер.**
- Читают сведение только `VerdictController:31` (HTTP) и `VerdictGate:84` (выключен) — автономного читателя нет, запись права.
- Живое сведение (`/api/projects/<id>/verdict`, 12:5x UTC, **1,17 с**): итог `WITHHOLD`; `doctrine` отказывает у **9 ролей**
  (TAG-00, 02, 03, 05, 06, 07, 08, 11, 12; причины — «Owner-role execution has failed work…», «Defect-work evidence remains
  attached…»); runtime, six-sigma, acceptance — ABSTAIN; infrastructure — PERMIT.
- Вес слоёв: ни один не поднимает таблиц `findAll()`; `InfrastructureVerdictLayer` делает 2 сетевых проверки.
**Главный риск предложенного в записи способа.** «На своём тике запрашивает сведение и при отказе пишет в журнал дефектов» —
тик раз в 60 с, отказов сейчас 9: это **~540 записей в час**. Журнал дефектов не нейтрален: его читают доверие
(`OperationalTruthService`, вычет за свежие дефекты), кайдзен, Парето. Поток повторов исказит их — сигнал станет шумом, то
есть тем же дефектом D011 с другой стороны. **Писать только при изменении:** новый отказ или смена причины по ключу
(слой, предложение, причина); тот же отказ на следующем тике — не новая запись. Ограничитель цены: сведение раз в N тиков
или по изменению входов — 1,17 с каждую минуту заметно.
**Порядок записи:** это только чтение и запись в журнал — не гейт, полномочий не добавлять (иначе нарушится п. 9).
**Тест:** слой отказывает два тика подряд — одна запись; причина сменилась — вторая; слой снова PERMIT — записи нет.
**Опровергнет:** отказ слоя, о котором не узнал никто, кроме HTTP-запроса; либо журнал, растущий на тот же отказ каждый тик.
