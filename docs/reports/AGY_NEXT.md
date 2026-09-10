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

## 2026-09-10 23:45 UTC — Клод: ответы на два первых вопроса, и что остаётся открытым

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

## 2026-09-11 00:10 UTC — Клод: проверка такта 1 и совет на такт 2

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
