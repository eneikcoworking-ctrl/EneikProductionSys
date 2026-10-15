# Пакетная директива L1/L3 -> L2: Ступень 5 («Проверить и не трогать» — 40 механизмов ядра и инвариантов базы)

**Дата:** 2026-10-15 13:02 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Ступени 3 и 4 очереди `ANTIGRAVITY_QUEUE.md` (все 126 механизмов) успешно пройдены и закрыты на 100%. Настоящая директива охватывает целиком следующий завершающий рубеж очереди — **Ступень 5 («Проверить и не трогать» — 40 механизмов ядра и инвариантов базы)**:
   - **Репозитории и агрегаты ядра:** `ClaimRepository`, `ProjectEventLogRepository`, `TaskRepository`;
   - **Пространства состояний и сущности:** `WishlistEntity`, `WishlistStatus`, `WishlistSource`, `TaskTitleBuilder`, `DurableProjectLogAppender`, `KaizenProposal`, `KaizenProposalEntity`, `PersistentWorkerPurpose`, `DesignShopCycleEntity`;
   - **Алгебра вердиктов и лестница доверия:** `Verdict`, `DbrStatus`, `LeverAgreement`, `LeverStage`;
   - **Базовые службы потока и контекста:** `FlowSpineService`, `GeminiContextService`, `DesignExcellenceGate`;
   - **Нерушимые инварианты миграций Flyway:** `V30` (activity responses), `V42` (PR review code/threads), `V44` (features), `V45` (feature threads), `V55` (context chunks), `V59` (observer journal), `V61` (project event log), `V68` (action verification), `V69` (file claims), `V72` (process control snapshots), `V79` (Kaizen & evidence nodes), `V81` (coherence confidence), `V82` (operational reality findings), `V85` (metric label), `V90` (trust signal snapshots), `V92` (client runtime observations), `V97` (workflow claim), `V104` (instrument failure), `V121` (drop needs human review), `V123` (drop orphan conflicts), `V126` (brief budget recovery).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступень 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ И ЗАЩИТА СИЛЬНЫХ ФОРМ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и сущностей при проходящих тестах!
   - Механизмы Ступени 5 обладают сильной формой. Любая попытка «улучшить» или «отрефакторить» сильный механизм признается порчей архитектуры и дефектом D006.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Верификация через опровержения, сформулированные в описи каждого механизма Ступени 5;
  * Заслон `Stage5ProtectedInvariantsFalsificationTest` проверяет невозможность ложных разрешений: `WITHHOLD` невозможно переголосовать (`withholdCannotBeOutvoted`), `ABSTAIN` блокирует продвижение (`abstainBlocksAdvancement`), `null` редуцируется в воздержание;
  * Лестница доверия `LeverAgreement.compare` честно возвращает `NEITHER` при отсутствии объективной истины, а `MLPredictionServiceClient` возвращает `null` (fail-closed) при отключенном Gemini.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Защита репозиторных и схемных границ: цепочка надежности Flyway-миграций от V30 до V126 гарантирует целостность схемы PostgreSQL без дрейфа типов;
  * Лок уровней абстракции: проекции `TaskEntity`, `WishlistEntity`, `EvidenceNodeEntity` сохраняют строгую типизацию без неконтролируемого кросс-модульного загрязнения;
  * Хребет потока `FlowSpineService` обеспечивает надежную передачу контекста без потери истории происхождения данных.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода защищенных механизмов;**
  * Неизменяемость терминальных состояний задачи (Law 20 / Invariant S2): запрет перезаписи статусов `done`, `failed` и `spike_completed` (`taskEntityRefusesOverwritingDoneStatus`);
  * Деонтический запрет на обход лестницы продвижения рычагов ТОС (`LeverStage Ladder is bounded`).

---

## 3. Пакетный наряд по механизмам Ступени 5 («Проверить и не трогать»)

1. **Репозитории ядра (`ClaimRepository`, `ProjectEventLogRepository`, `TaskRepository`):**
   - **Проба:** Замерить сохранение проектных границ, изоляцию SQL-запросов и атомарность операций слияния/выборки.
   - **Инвариант:** Рабочий код не трогать. Заслоны `TaskRepositoryIntegrationTest`, `ClaimServiceTest`.
2. **Пространства состояний и сущности ядра (`TaskStatus`, `TaskEntity`, `WishlistEntity`, `WishlistStatus`):**
   - **Проба:** Замерить закон неизменяемости терминального статуса задачи (Terminal Immutability Law 20), проверку `isTerminal()` и запрет перехода `done -> queued`.
   - **Инвариант:** Рабочий код не трогать. Заслон `Stage5ProtectedInvariantsFalsificationTest`.
3. **Алгебра вердиктов и суждений (`Verdict`, `Judgement`):**
   - **Проба:** Замерить трёхзначную конъюнкцию Клини/Тарского: `WITHHOLD` доминирует над любым числом `PERMIT`, `ABSTAIN` блокирует продвижение, `null` редуцируется в `ABSTAIN`.
   - **Инвариант:** Рабочий код не трогать. Заслон `Stage5ProtectedInvariantsFalsificationTest`.
4. **Лестница доверия рычагов ТОС (`LeverStage`, `LeverAgreement`):**
   - **Проба:** Замерить четырёхзначную логику Белнапа (`TRUE`, `FALSE`, `BOTH`, `NEITHER`), асимметричное продвижение рычагов и строгие границы лестницы `[OBSERVE_ONLY, AUTO_REMEDIATE]`.
   - **Инвариант:** Рабочий код не трогать. Заслоны `Stage5ProtectedInvariantsFalsificationTest`, `TocSubordinationTruthTest`.
5. **Клиент предсказателя (`MLPredictionServiceClient`):**
   - **Проба:** Замерить строгое fail-closed поведение: возврат `null` при отключенном флаге `gemini_enabled` или пустом API ключе без падения процесса.
   - **Инвариант:** Рабочий код не трогать. Заслон `Stage5ProtectedInvariantsFalsificationTest`.
6. **Хребет потока (`FlowSpineService`):**
   - **Проба:** Замерить детерминированное сохранение пульса потока и трансляцию статусов.
   - **Инвариант:** Рабочий код не трогать. Заслон `FlowSpineServiceTest`.
7. **Инварианты миграций Flyway (V30–V126):**
   - **Проба:** Замерить валидность структуры БД в работающем контейнере PostgreSQL, отсутствие дрейфа схемы и целостность внешних ключей.
   - **Инвариант:** Миграции не менять. Заслон качества схемы БД.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов Ступени 5 в Docker Maven (`-m 2g`).
2. Внести отметку `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (Ступень 5).
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(stage5): complete Stage 5 batch verification (D008, D010, D006)`.
