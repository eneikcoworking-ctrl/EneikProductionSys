# L1 Эпистемическая диагностика P0-инцидентов перехода на DeepSeek (2026-11-24)

**Аудитор:** L1 Менеджер-философ (`remotecli2`)  
**Основание:** Срочная P0-директива Оператора от `2026-11-24 19:46:00 UTC` ([OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md)) и [ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md)  
**Эмпирическая база:** живая телеметрия `eneikproductionsys-backend-1` и `eneikproductionsys-judgment-proxy-1` (`docker stats`, `/proc/1/task/*/stat`, `ps`, `docker logs`), `data/logs/backend.log*`, исходный код `DeepSeekPatchParser.java`, `LocalAgyOutputParser.java`, `DeepSeekApiClient.java`, `ClaimService.java`, `JulesDispatchService.java`, `ContinuousOrchestrationService.java`, `ClientDeliverableReadinessService.java`, `DeliveryRealityProducerService.java`, `judgment-proxy/server.js`, `docker-compose.yml`.

---

## 0. Верификация закрытия Work Order #9 (приёмка L1, Ступень 4)
Живой прогон `eneikproductionsys-backend-1` (uptime `3h` после коммитов `e09221c5`, `9bfd501f`, `3bfd4af8`):
- `SQL Error: 22001` (`SOURCE_ROLE_TAG`): **`0` вхождений** (было `177+/сутки`) → **принято**.
- `OutOfMemoryError: unable to create native thread`: **`0` вхождений** → **принято**.
- `Connection leak detection`: **`2` вхождения** за 3 часа (`18:55`, `21:10` — оба из `DesignShopOrchestrationService.tick -> startCycle -> build`, не из `AutoMerge`/`ClientDeliverable`/`TrustSnapshot`/`ProcessControl`) → основная утечка закрыта, остаточный хвост `DesignShopOrchestrationService` взят на карандаш отдельно.
- **Гигиена хоста и продуктов:** `/` = **`69%`** (`25G/38G`, `12G` свободно, `< 70%` соблюдено после `docker builder/image prune`); RAM `1.5 GiB` доступно; `eneikproductionsys-backend-1` (:8080) `UP`, `dmitryefremov-backend` (:18080) `UP`.

---

## 1. P0 #1 — D003 Contract Drift: `Illegal unquoted character (CTRL-CHAR, code 10)` при парсинге JSON ответов воркера

### 1.1. Эмпирические факты (анти-зеркало Витгенштейна, D013)
- **Живое воспроизведение (`eneikproductionsys-backend-1`, `2026-11-24T18:56:08.794Z` и др.):** `5` отказов за последние 3ч в текущем контейнере + `10` вхождений за 2ч по телеметрии Оператора:
  ```text
  ERROR c.e.p.s.w.deepseek.DeepSeekPatchParser : Failed to parse DeepSeek response JSON:
    Illegal unquoted character ((CTRL-CHAR, code 10)): has to be escaped using backslash to be included in string value
    at [Source: (StringReader); line: 29, column: 57]
  at com.eneik.production.services.worker.deepseek.DeepSeekPatchParser.parse(DeepSeekPatchParser.java:117)
  at com.eneik.production.services.worker.agy.LocalAgyOutputParser.parse(LocalAgyOutputParser.java:42)
  ```
- **Корень дефекта:**
  - `DeepSeekPatchParser.extractJson(String)` (`DeepSeekPatchParser.java:137-157`) извлекает JSON из markdown-ограждений или `{ ... }` и сразу передаёт строку в `objectMapper.readTree(cleanJson)` (`DeepSeekPatchParser.java:55`).
  - `LocalAgyOutputParser.sanitizeOutput(String)` (`LocalAgyOutputParser.java:48-78`) снимает только ANSI escape-последовательности и markdown fences, после чего делегирует в `DeepSeekPatchParser.parse(...)`.
  - Аналогично `DeepSeekApiClient.executeCall(...)` (`DeepSeekApiClient.java:185`) передаёт `choices[0].message.content` в тот же `DeepSeekPatchParser.parse(...)`.
  - При генерации многострочного кода в полях `"content"`, `"search"`, `"replace"`, `"rationale"` модели DeepSeek / `agy` периодически эмитят сырые управляющие символы ASCII `0x00..0x1F` (`\n` code 10, `\r` code 13, `\t` code 9) внутри строковых литералов JSON. Стандартный Jackson `ObjectMapper` отклоняет их, вызывая отказ задачи и повторный цикл диспетчеризации.

### 1.2. Канонический контракт санитайзера (решение L1)
- **Философское основание (RAG-корпус):**
  - `D003` (Сабина Леонелли — `Data Journeys` / реконтекстуализация контракта; Уиллард Куайн — `RIGID_API_REFERENT`): валидация и нормализация контракта обязана происходить в единой точке пересечения границы «внешний LLM → внутренний AST патча» (`DeepSeekPatchParser`), чтобы оба движка (`DEEPSEEK_WORKTREE` и `LOCAL_AGY`) подчинялись одному закону.
  - `D008` (Карл Поппер — `KARL_POPPER_01_FALSIFICATION_HARNESS`): заслон обязан фальсифицировать регрессию на реальном падающем ответе с сырым `LF (0x0A)`, `CR (0x0D)`, `TAB (0x09)` внутри строк, сохраняя действие `checkAntiTruncation`.
- **Требования к контракту `DeepSeekPatchParser`:**
  1. Детерминированный state-machine санитайзер `sanitizeJsonControlChars(String rawJson)` (посимвольный проход с флагами `inString` и `escaped`):
     - Вне строковых литералов (`!inString`): форматирование JSON (`\n`, `\r`, `\t`, пробелы) не изменяется.
     - Внутри строковых литералов (`inString == true`):
       - `0x0A` (`\n`) → `\\n`
       - `0x0D` (`\r`) → `\\r`
       - `0x09` (`\t`) → `\\t`
       - `0x08` (`\b`) → `\\b`, `0x0C` (`\f`) → `\\f`
       - прочие управляющие символы `< 0x20` → `\u00XX`.
     - Уже экранированные последовательности (`\\`, `\"`, `\n`, `\t`, `\uXXXX`) остаются строго нетронутыми (инвариант идемпотентности: для валидного JSON `sanitize(s).equals(s)`).
  2. Санитайзер выполняется строго **внутри `DeepSeekPatchParser.parse(...)`** после `extractJson(jsonContent)` и до `objectMapper.readTree(...)`, закрывая сразу оба контура (`DeepSeekApiClient` и `LocalAgyOutputParser`).
  3. Инвариант `Anti-Truncation Gate` (`LazyTruncationDetectedException`, D008) и парсинг `SCOPE_INSUFFICIENT` обязаны оставаться 100% активными поверх санитайзированного контента.

---

## 2. P0 #2 — D013 Runtime Drift: `backend` 113–144% CPU при лимите `1GiB` (`858–929MiB`) и горячий цикл ретраев

### 2.1. Эмпирические факты (анти-зеркало Витгенштейна, D013)
- **Живой замер `eneikproductionsys-backend-1` (`2026-11-24 21:26–21:28 UTC`):**
  - `CPU: 130.33%`, `MEM: 858.4MiB / 1GiB (83.82%)` (пики до `144% CPU`, `929MiB` по телеметрии Оператора).
  - Топ потоков JVM по `/proc/1/task/*/stat`: **`G1 Conc#0 (345803)`**, **`GC Thread#0 (205377)`**, **`GC Thread#1 (205260)`**, `C2 CompilerThread`, `MVStore background` — устойчивый GC-трашинг на границе cgroup-лимита `1GiB`.
  - Внутри контейнера накоплено **`185` зомби-процессов `git` (`STAT=Zs`, `PPID=1`)**.
  - Переменная окружения контейнера: `WORKER_ENGINE_MODE=HYBRID`, при этом `JulesDispatchService` маршрутизирует задачи `knowledge-base-crie` (`d64a3b6b-667e-4cda-aa69-8a7bf42b890f`) в `LOCAL_AGY`.
- **Выявлены две сцепленные ветви горячего цикла:**
  1. **Бесконечный минутный цикл `dispatch refused (attempt 0/34)` (сломанная вариант-функция в `ClaimService`):**
     - Каждую минуту (`21:24:07`, `21:25:09`, `21:26:08`, `21:27:11`, `21:28:20`) одни и те же 4 задачи `knowledge-base-crie` (`a6f64e24`, `4c4837ff`, `d976d831`, `c6f75610`) заново клеймятся, запускают `git fetch origin --prune` + `git worktree add` + `agy CLI`, мгновенно падают (`Individual quota reached` / `empty stdout` / `CTRL-CHAR`), освобождаются через `ClaimService.releaseClaimToQueue(taskId, reason)` и снова ставятся в `TaskStatus.queued`.
     - **Точный баг (`ClaimService.java:748-756`):**
       `refusedSessionCreations(taskId)` считает ТОЛЬКО строки `jules_sessions` с **`externalSessionId IS NULL` AND `status = 'failed'`**. Но `JulesDispatchService.java:717` и `LocalAgyWorktreeExecutionEngine.java:117` заранее присваивают суверенным сессиям непустой `externalSessionId = "local-agy-" + worktreeToken` (и аналогично `deepseek-*`) до исполнения! Поэтому счётчик отказов **заморожен на `0` (`attempt 0/34 against budget 34`)**, бюджет `retireForExhaustedDispatchBudget` никогда не срабатывает, а **backoff/cooldown на задачу отсутствует полностью**.
  2. **Перекрытие тиков оркестратора и тяжёлые проходы по `failed=1` требованиям (`ContinuousOrchestrationService` / `ClientDeliverableReadinessService` / `DeliveryRealityProducerService`):**
     - `ContinuousOrchestrationService.continuousOrchestrate()` (`ContinuousOrchestrationService.java:201`) использует `@Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")`. Поскольку каждый тик по `knowledge-base-crie` (415 задач, `108` с `nothing delivered`, `99+` требований с `{failed=1}`, `mergedReviews round trips: 59..265`) длится **`70–97 секунд`** (`[TOC-SENTINEL][STALL_DETECTED] ORCHESTRATION_PROCESSING for 97607 ms`), `fixedRate` запускает следующий тик **с нулевой паузой (0 мс)**, удерживая CPU > 113–144% и давя G1GC.
     - Дополнительно: при `Individual quota reached` от `LOCAL_AGY` аккаунт/профиль не получает cooldown, поэтому все 4 слота каждую минуту штурмуют квотированный CLI и порождают зомби-процессы `git` (`PID 1 = java` без reaper).

### 2.2. Канонический контракт анти-цикла и backoff (решение L1)
- **Философское основание (RAG-корпус):**
  - `D013` (Уэсли Сэлмон — `CAUSAL_PROCESS_TRACE`; Людвиг Витгенштейн — `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`): контур без строго убывающей вариант-функции и паузы обратной связи вырождается в холостой прожиг CPU/GC.
  - `D005` (Лара Бучак — `REU / Tail Risk Block`): хвостовой риск OOM-kill контейнера (`858–929MiB / 1024MiB`) при 130% CPU и накоплении зомби-процессов подлежит конструктивному вето.
- **Требования к контракту:**
  1. **Монотонная вариант-функция отказов в `ClaimService` (`refusedSessionCreations` / `releaseClaimToQueue`):** учитывать все неуспешные попытки диспетчеризации, включая суверенные сессии (`local-agy-*`, `deepseek-*`) без PR/результата, чтобы `attempt` строго рос (`1/N .. N/N`) и достигал потолка лимита ретраев (с жёстким cap на задачу вместо бесконечного `0/34`).
  2. **Экспоненциальный backoff (cooldown) при `releaseClaimToQueue` и квотах:** задача после отказа диспетчеризации не может быть повторно заклеймлена ранее `backoff(attempts)` (например, `2m -> 5m -> 15m -> 30m`, cap `60m`); при ошибке квоты (`Individual quota reached` / HTTP 429) выставлять cooldown также на источник/аккаунт.
  3. **Разрыв непрерывного горячего цикла в `ContinuousOrchestrationService`:**
     - Перевести `continuousOrchestrate()` с `fixedRateString` на `fixedDelayString` (гарантированная пауза между завершением и стартом тика).
     - Ограничить ретраи/повторные пересчёты для неизменившихся `failed=1` требований (`knowledge-base-crie`, 99+ требований) через per-project / per-task cooldown, избегая 200+ лишних `mergedReviews` round trips каждую минуту.
  4. **Гигиена процессов (`185` зомби `git`):** гарантировать `waitFor()`/`destroyForcibly()` в `GitWorktreeExecutionManager` и добавить `init: true` сервису `backend` в `docker-compose.yml`.

---

## 3. P0 #3 — D003/D010/D008: `judgment-proxy :8093` (`geminiAvailable=false` при `primary=gemini`)

### 3.1. Эмпирические факты
- **Живой `/health` (`http://127.0.0.1:8093/health`):**
  ```json
  {"ok":true,"primary":"gemini","shadowDir":"/shadow","geminiAvailable":false}
  ```
- **Живой лог `eneikproductionsys-judgment-proxy-1`:**
  ```text
  judgment-proxy (Gemini Primary + Shadow Fallback) listening on 8093, shadow=/shadow, geminiKey=false
  [judgment-proxy] Autonomous heuristic arbiter resolved da197b24e88511255eb7d1fe (text) -> TEXT
  ```
- **Корень дефекта:**
  - `judgment-proxy/server.js` (строки 11–15, 64–144, 290–315, 323–325) знает только Gemini (`primary: 'gemini'`) и при пустом `GEMINI_API_KEY` на 100% запросов проваливается в Tier 2 `evaluateAutonomousFallback` (`server.js:235-267`), возвращающий хардкодные заглушки (`ABSTAIN` в `schema`-режиме и `SATISFIED` в delivery `text`-режиме!).
  - В `docker-compose.yml:74-86` для `judgment-proxy` не прокинуты переменные DeepSeek (`DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL`, `JUDGMENT_PRIMARY`).

### 3.2. Канонический контракт `judgment-proxy` (решение L1)
- **Философское основание (RAG-корпус):**
  - `D010` (Элвин Голдман — `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`) и `D008` (Карл Поппер — `KARL_POPPER_01_FALSIFICATION_HARNESS`): заглушка `evaluateAutonomousFallback`, выдающая `SATISFIED` на любой delivery-запрос при выключенном LLM, является **симулякром суждения** (нарушение инварианта `JudgmentAgentClient.java:101`: «never a silent ABSTAIN / fake ruling»).
  - `D003` (Сабина Леонелли / Уиллард Куайн): телеметрия `/health` обязана честно отражать реального первичного провайдера и цепочку fallback.
- **Требования к контракту `judgment-proxy/server.js` и `docker-compose.yml`:**
  1. **Primary = DeepSeek:** поддержать `JUDGMENT_PRIMARY` (по умолчанию `deepseek`), `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL` (по умолчанию `https://api.deepseek.com`), `DEEPSEEK_MODEL` (по умолчанию `deepseek-chat`).
  2. **Реальный вызов DeepSeek (`callDeepSeekPrimary`):** `POST ${DEEPSEEK_BASE_URL}/chat/completions` (OpenAI-совместимый формат, как в `DeepSeekApiClient.java:121-143`, `temperature: 0.1`, таймаут 45с, `response_format: { type: 'json_object' }` в `schema`-режиме + санитаризация JSON ответа от markdown fences и управляющих символов).
  3. **Явная цепочка fallback без симулякров:**
     - `Primary (DeepSeek)` → `Secondary (Gemini, только если GEMINI_API_KEY задан)` → если оба провайдера недоступны/упали: возвращать явный fallback с честной маркировкой `provider: "heuristic-fallback"` (или `UNAVAILABLE`, если задан `JUDGMENT_ALLOW_HEURISTIC_FALLBACK=false`), а не маскировать под LLM-вердикт.
  4. **Честный `/health` контракт:**
     ```json
     {
       "ok": true,
       "primary": "deepseek",
       "activeProvider": "deepseek|gemini|heuristic-fallback|unavailable",
       "deepseekAvailable": true,
       "geminiAvailable": false,
       "fallbackChain": ["deepseek", "gemini", "heuristic-fallback"],
       "shadowDir": "/shadow"
     }
     ```
  5. **Compose-интеграция:** прокинуть `JUDGMENT_PRIMARY`, `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL` в сервис `judgment-proxy` в `docker-compose.yml`.

---

## 4. Ответственные механизмы Фабрики (`docs/FACTORY_MECHANISMS.md`)

| Приоритет | Дефект (RAG) | Ответственные механизмы и файлы Фабрики | Порядок выполнения L2 |
|---|---|---|---|
| **P0 #1** | `D003` (Леонелли / Куайн), `D008` (Поппер) | **Механизм №10 (Парсинг патчей суверенных воркеров):** `DeepSeekPatchParser.java`, `LocalAgyOutputParser.java`, `DeepSeekApiClient.java`, тесты `DeepSeekPatchParserTest.java` + `LocalAgyOutputParserTest.java` | **Такт 1 (сначала п.1)** |
| **P0 #2** | `D013` (Витгенштейн / Сэлмон), `D005` (Бучак), `D008` (Поппер) | **Механизм №11 (Контур диспетчеризации, backoff и анти-цикл оркестрации):** `ClaimService.java` (`releaseClaimToQueue`, `refusedSessionCreations`), `JulesDispatchService.java`, `ContinuousOrchestrationService.java`, `ClientDeliverableReadinessService.java`, `GitWorktreeExecutionManager.java`, `docker-compose.yml` (`init: true`) | **Такт 2 (затем п.2)** |
| **P0 #3** | `D003` (Куайн / Леонелли), `D010` (Голдман), `D008` (Поппер) | **Механизм №12 (Суверенный контур суждения `judgment-proxy`):** `judgment-proxy/server.js`, `judgment-proxy/server.test.js`, `docker-compose.yml` (`judgment-proxy`), `JudgmentAgentClient.java` | **Такт 3 (затем п.3)** |
