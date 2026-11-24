# L1 Эпистемическая диагностика и контракт: Memorilla ($K=16$) ↔ суверенные воркеры и динамическое принятие роли при захвате задачи (WO #12)

**Дата:** 2026-11-24 22:59 UTC  
**Аудитор:** L1 Менеджер-философ (`remotecli2`)  
**Основание:** Решение Оператора Дмитрия Ефремова в чате («Действуйте. Работать автономно пока не закончите») после архитектурной сверки по живому коду; спецификация Memorilla (§6 [PROGRAMMER_DIRECTIVES.md](PROGRAMMER_DIRECTIVES.md)).  
**Эмпирическая база (принцип анти-зеркала Витгенштейна, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, `D013`):** построчный аудит `GeminiContextService.java`, `ClaimService.java`, `JulesDispatchService.java`, `WorkerExecutionRouter.java`, `DeepSeekWorktreeExecutionEngine.java`, `DeepSeekApiClient.java`, `LocalAgyWorktreeExecutionEngine.java`, `LocalAgyCliClient.java`, `RoleCapabilityLoader.java`, `OpsAuditorService.java`, `FalsificationCycleService.java`, а также живые настройки БД (`gemini_context_learning_enabled=true`, `worker_engine_mode=HYBRID`).

---

## 1. Архитектурная истина: как воркеры принимают роль

1. **Каноническая модель Фабрики:** воркеры (`DeepSeek`, `LOCAL_AGY`, `Jules`) безлики до момента захвата/диспетчеризации задачи и принимают конкретную роль (`task.getRole()`, `BARCAN-TAG-00..12`) строго в момент claim/dispatch (`ClaimService.java:252,304,347,530`).
2. **Разрыв на живом суверенном пути (`JulesDispatchService.java:698-757`):**
   - При активном в живой БД режиме `worker_engine_mode=HYBRID` метод `JulesDispatchService.dispatch(...)` уходит в `workerExecutionRouter.dispatch(task, account, mode)` на строках `698-757` — **до** сборки ролевого контекста (строки `795-872`, где для облачного Jules подгружались `RoleCapabilityLoader.loadRawCharter(...)`, `CompactRoleGuide` и `GeminiContextService`).
   - В результате на живом суверенном трафике возникают три конкретных разрыва:
     1. **Memorilla ($K=16$) — мёртвый код в продакшне (`D013` / `D014`):** методы `GeminiContextService.buildProductWorkerLatentMemoryBlock(RoleEntity, String)` (`lines 994-1015`), `buildLatentSemanticMemoryBlock(String)` (`lines 974-988`) и `retrieveLatentMemorySlots(...)` (`lines 785-909`) имеют **0 вызовов в `src/main/java`** за пределами `GeminiContextService.java` (вызываются только в `GeminiContextServiceTest.java`). Даже при включённом `gemini_context_learning_enabled=true` ни один воркер и ни один оркестраторский вызов их не использует.
     2. **`DeepSeekWorktreeExecutionEngine` / `DeepSeekApiClient` (`lines 198-242`):** получают только `task.getRole().getTag()`, `task.getRole().getDescription()` и общую статическую таблицу `BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX`, но **не получают** ни живого устава роли (`RoleCapabilityLoader.loadRawCharter`), ни ортогональной памяти Memorilla ($K=16$, Tarski L0-stratified).
     3. **`LocalAgyWorktreeExecutionEngine` / `LocalAgyCliClient` (`lines 226-276`):** в `buildAgyPrompt(...)` **вообще не передаётся даже `task.getRole().getTag()` / `Role Description`**, не говоря о Role Charter и Memorilla — локальный воркер исполняет задачу полностью без ролевой идентичности.
     4. **Скрытый отказ пустой выборки (`D013`):** `GeminiContextService.retrieveLatentMemorySlots` при `embed(query) == null` или пустом индексе молча возвращает пустой список без диагностического следа в телеметрии.

---

## 2. Философское основание (верифицировано по `docs/philosopher-patterns/philosophers/`)

- `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (`D014 RAG hallucination`): передача ортогональных дистиллированных слотов Memorilla ($K=16$, $\lambda=0.65$, коллинеарный порог `0.96`, капсула $\le 240$ символов с `sourceRef`) вместо повторов сырого top-5.
- `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (`D013 Runtime drift`): устранение расхождения между документированной памятью Memorilla и реальным путём исполнения воркеров + честная телеметрия количества слотов и причины пустой выборки.
- `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` (`D010 Data lineage loss`): жёсткая стратификация Тарского L0 — для `PRODUCT_CODEBASE` допустим строго `buildProductWorkerLatentMemoryBlock(task.getRole(), query)` (`PRODUCT_WORKER_SOURCE_TYPES`, 0 утечек `observer_log` / `factory_mechanisms`); `buildLatentSemanticMemoryBlock(query)` допустим только для `ORCHESTRATOR_SYSTEM` и внутренних аудиторов Фабрики.
- `GILBERT_RAYL_02_INFERENTIAL_SCOREBOARD` (`D001 Semantic drift`): безликий воркер обязан принимать конкретную нормативную роль (`task.getRole()` + `RoleCapabilityLoader.loadRawCharter`) ровно в момент диспетчеризации задачи.
- `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (`D006 Authorization ambiguity`): сохранность константного префикса `BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX` в `DeepSeekApiClient` (серверный Context Caching `>1024` токенов не нарушается) и неприкосновенность `task.fileScope` как исключающей причины второго порядка.
- `KARL_POPPER_01_FALSIFICATION_HARNESS` (`D008 False green`) и `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (`D008 False green`): проверяемые фальсификационные тесты в Docker Maven (`-m 2g`) на каждый инвариант.

---

## 3. Порядок исполнения (эшелонирование без сбоя P0/P1)

1. **P0 (Такты 1–3, WO #10, `PROGRAMMER_DIRECTIVES.md` §12):** `DeepSeekPatchParser` CTRL-CHAR → `ClaimService`/`ContinuousOrchestrationService` backoff + `init: true` → `judgment-proxy :8093` `primary=deepseek`.
2. **P1 (Такт 4, WO #11, `PROGRAMMER_DIRECTIVES.md` §13):** `4A` анти-орфан mirror и запрет `local-repo/pulls` → `4B` терминация `SCOPE_INSUFFICIENT` (`crie_*.py`) → `4C` граница UI с `aesthetic-master`.
3. **P2 (Такт 5, WO #12, `PROGRAMMER_DIRECTIVES.md` §14):** подключение `SovereignWorkerContextAssembler` (динамическая роль `task.getRole()` + `RoleCapabilityLoader` + Memorilla $K=16$) в `DeepSeekWorktreeExecutionEngine`/`DeepSeekApiClient`, `LocalAgyWorktreeExecutionEngine`/`LocalAgyCliClient` и перевод оставшихся потребителей (`OpsAuditorService`, `FalsificationCycleService`, `JulesDispatchService` без ослабления защищённого контура автономии Деннетта).
