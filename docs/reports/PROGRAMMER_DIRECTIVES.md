# Пакетная директива L1/L3 -> L2: Ступень 3 («Провести пробу, кода не менять» — пакет из 8 механизмов ядра)

**Дата:** 2026-10-15 10:53 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Настоящая директива охватывает всю Ступень 3 `docs/ANTIGRAVITY_QUEUE.md` (все 8 механизмов ядра и периферии):
   - `ClaimResultStatus` (Секция XXIв)
   - `OperationalPolicyDeniedException` (ядро, Секция XXIв)
   - `AnomalyReport` (Секция XXIг)
   - `JulesRoleCapabilities` (Секция XXI)
   - `ScopedBufferAppender` (ядро, Секция XII)
   - `V25__add_depends_on_and_hotspots.sql` (Секция XXII)
   - `OperationalFlowCoreService` (ядро, Секция XXV)
   - `AccountRepository` (ядро, Секция XXXV)
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступень 3):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено трогать рабочий код при проходящих тестах!
   - «Форма: не мерено» — действие: провести названную пробу и зафиксировать её исход в `*Живое:*`. Правка кода на этой ступени признается грубой ошибкой.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:** Принцип фальсификации. Тестовые заслоны не ищут подтверждений («код написан — значит работает»), а фальсифицируют инварианты: неразличение `failed`/`expired` в `ClaimResultStatus`, тихий пропуск недозволенных действий в `OperationalPolicyDeniedException`, дедлоки и циклы в `AnomalyReport`, мутацию ролевого словаря в `JulesRoleCapabilities`, проникновение системных логов в `ScopedBufferAppender`, повисшие ghost-зависимости в схеме V25, нарушение матрицы прав в `OperationalFlowCoreService` и двойной захват строки в `AccountRepository`. Тесты обязаны падать при нарушении инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:** Лок уровней абстракции и надежность цепочки происхождения данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`). Разделение живого чтения, наблюдаемой телеметрии и персистентного хранения: `OperationalFlowCoreService` жестко делит события `observe_only` и `flow_core_enforced`; `AnomalyReport` сохраняет неизменяемый слепок инцидента; `ScopedBufferAppender` изолирует проектный контекст от системного.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:** Деонтический запрет как исключающая причина (`exclusionary reason`) и исполняемый код.
  * Категорический запрет инженеру L2 на изменение производственного кода на Ступени 3 при проходящих тестах.
  * В коде: типизированные отказы в `OperationalPolicyDeniedException`, матрица прав и обязанностей в `OperationalFlowCoreService` (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`), предикат закрытого мира в `JulesRoleCapabilities.isKnownRole`, взыскание как порядок в `AccountRepository` (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING`).

---

## 3. Пакетный наряд по механизмам Ступени 3 («Провести пробу, кода не менять»)

### Группа 1. Алгебра решений и статусы притязаний (Секция XXIв)
1. **`ClaimResultStatus` (пункт 1 очереди Ступени 3):**
   - **Проба:** Провести замер различимости исходов `done`, `failed`, `expired` через `ClaimResultStatusTruthTableFalsificationTest`.
   - **Инвариант:** `ClaimService` при `expired` возвращает задачу в очередь (`TaskStatus.queued`), а при `failed` переводит в терминальный статус; `BottleneckDetectionService` считает всплеск `expired_lease_spike` строго по `expired`. Исходный код НЕ ТРОГАТЬ.
2. **`OperationalPolicyDeniedException` (ядро, пункт 2 очереди Ступени 3):**
   - **Проба:** Провести замер типизированного отказа через `OperationalPolicyDeniedExceptionFalsificationTest`.
   - **Инвариант:** Недозволенные действия вызывают детерминированное исключение с типизированными `projectId`, `action`, `state`, `authorizationStatus`, транслируясь в HTTP 409 Conflict без повреждения состояния. Исходный код НЕ ТРОГАТЬ.

### Группа 2. Граф ограничений ТОС и десигнация ролей (Секции XXI, XXIг)
3. **`AnomalyReport` (пункт 3 очереди Ступени 3):**
   - **Проба:** Провести замер телеосемантического заслона через `AnomalyReportFalsificationTest`.
   - **Инвариант:** Неизменяемый snapshot аномалии сохраняет контекст (8 полей), универсум `AnomalyType` строго закрыт 4 типами, сигнал аномалии немедленно прерывает циклы (`CYCLE_ABORTED`) и разрешает дедлоки (`DEADLOCK_ABORTED`). Исходный код НЕ ТРОГАТЬ.
4. **`JulesRoleCapabilities` (пункт 4 очереди Ступени 3):**
   - **Проба:** Провести замер ролевой границы через `JulesRoleCapabilitiesFalsificationTest`.
   - **Инвариант:** Ролевой словарь жестко фиксирует 13 канонических десигнаторов (`BARCAN-TAG-00` .. `BARCAN-TAG-12`), предикат закрытого мира `isKnownRole` отсекает неизвестные роли, форсируется `ALL_CAPABILITIES`. Исходный код НЕ ТРОГАТЬ.

### Группа 3. Санитария логирования и реляционные ограничения (Секции XII, XXII)
5. **`ScopedBufferAppender` (ядро, пункт 5 очереди Ступени 3):**
   - **Проба:** Провести замер топологической изоляции логов через `ScopedBufferAppenderFalsificationTest`.
   - **Инвариант:** В in-memory буфер проекта попадают строго события с префиксом `PROJECT:{uuid}`; системный шум (`SYSTEM`), безымянные события (`null`) и посторонние префиксы безусловно отсекаются. Исходный код НЕ ТРОГАТЬ.
6. **`V25__add_depends_on_and_hotspots.sql` (пункт 6 очереди Ступени 3):**
   - **Проба:** Провести замер реляционных инвариантов схемы через `V25MigrationFalsificationTest`.
   - **Инвариант:** Внешний ключ `fk_tasks_depends_on` блокирует ghost-зависимости; реестр точек изменений `project_hotspot_files` гарантирует валидность владения и каскадное удаление при удалении проекта. Исходный код НЕ ТРОГАТЬ.

### Группа 4. Ядро потока и атомарный выбор исполнителя (Секции XXV, XXXV)
7. **`OperationalFlowCoreService` (ядро, пункт 7 очереди Ступени 3):**
   - **Проба:** Провести замер матрицы прав и обязанностей через `OperationalFlowCoreFalsificationTest`.
   - **Инвариант:** 19 состояний потока несут непустой список `forbiddenActions`; 7 состояний блокировки исполняют `ENFORCED_STOP_THE_LINE`; `decisionHash` (SHA-256) гарантирует дедупликацию журнала `flow_spine_events`. Исходный код НЕ ТРОГАТЬ.
8. **`AccountRepository` (ядро, пункт 8 очереди Ступени 3):**
   - **Проба:** Провести замер атомарного выбора и взыскания через `AccountSelectionFairnessTest`, `AccountRepositoryIntegrationTest`, `GeneralPoolAdmissionCoherenceIntegrationTest`, `ProjectFlowServiceLaw1JulesDispatchTest`.
   - **Инвариант:** Атомарный выбор `FOR UPDATE SKIP LOCKED`; отказ аккаунта упорядочивает выбор (`ORDER BY refusal_run_count`), а не исключает его; learned capacity имеет приоритет над глобальными лимитами. Исходный код НЕ ТРОГАТЬ.

---

## 4. Регламент отчета инженера L2

1. Провести пакетный запуск тестов-заслонов в Docker Maven (`-m 2g`).
2. Внести отметки `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` для всех 8 механизмов Ступени 3.
3. Отметить пункты Ступени 3 как закрытые в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
5. Зафиксировать результат коммитом `feat(stage3): complete Stage 3 batch runtime probe for mechanisms 1-8 (D008, D010, D006)`.
