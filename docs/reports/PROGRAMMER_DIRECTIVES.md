# Пакетная директива L1/L3 -> L2: Секция XXI — Сущности, несущие поведение, пространства состояний и алгебра решений

**Дата:** 2026-10-16 07:30 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущий Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные компоненты, читающие контроллеры, сайдкары и хранилища потока») успешно заверен инженером (коммит `1fae68f`, 158/158 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующей ключевой секции фабрики — **Секции XXI («Сущности, несущие поведение, девять пространств состояний и алгебра решений»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)** целиком (133 теста, 22 тест-сьюта):
     * **Подраздел XXI: Поведенческие сущности ядра (27 тестов, 6 сьютов):**
       - `TaskEntityLaw20Test` (6/6), `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `TaskEntityLaw1CarrierTest` (5/5), `DeliveryVerificationPredicateTest` (6/6) — предикаты доставки задачи (`isVerifiedForDelivery`, `isCarrier`, `isDoneNotDelivery`), отделение сделанного от доставленного, предикаты верификации артефактов и изоляция задач-носителей (D010 Goldman, D008 Popper, D006 Raz).
       - `EneikProductionApplicationShutdownCompactTest` (6/6) — чистая компактизация персистентного хранилища H2 на шатдауне без повреждения таблиц (D010 Parfit & Goldman).
     * **Подраздел XXIб: Девять пространств состояний (25 тестов, 4 сьюта):**
       - `LeanValueTest` (9/9) — четырёхзначная решётка ценности (`essential`, `valuable`, `waste`, `undetermined`), отказ при неизвестном значении без ложного оптимизма (D012 Belnap).
       - `AccountStatusConjunctionTest` (7/7) — двухконъюнктная доступность аккаунтов (конъюнкция флага активности и отсутствия блокировок) (D012 Belnap, D002 Wittgenstein).
       - `TargetContextTest` (4/4) — четырёхзначный целевой контекст с явным `UNDETERMINED`, категорический отказ диспетчеризации при неопределённом контексте для защиты изоляции репозиториев (D006 Raz Law 2, D012 Belnap).
       - `DecompositionVerdictTest` (5/5) — типизированные исходы декомпозиции требований (D012 Belnap).
     * **Подраздел XXIв: Алгебра решений и решётка вердиктов (46 тестов, 5 сьютов):**
       - `VerdictGateTest` (14/14) — монотонность решётки готовности (гейт может только ужесточить требование, но не ослабить его), трёхзначная семантика и безопасный отказ при сбое (D008 Popper, D012 Belnap).
       - `VerdictReconciliationTest` (11/11) — согласование вердиктов нескольких независимых слоёв суждения (D010 Goldman).
       - `InfrastructureVerdictLayerTest` (8/8), `AcceptanceVerdictLayerTest` (7/7), `RuntimeVerdictLayerTest` (6/6) — слои инфраструктурных, приёмочных и рантайм-вердиктов (D010 Goldman, D008 Popper).
     * **Подразделы XXIг–XXIз: Граф ограничений, персистентность, журнал дефектов и санитарный буфер (35 тестов, 7 сьютов):**
       - `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4) — оптимизатор TOC, таймауты узлов и снимки персистентности графа верёвки DBR (D008 Popper, D010 Shaffer & Parfit).
       - `LogScopeBufferTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `DefectJournalRootCauseAttributionTest` (6/6), `DefectJournalServiceTest` (3/3) — кольцевой буфер журналов, атрибуция первопричин INUS в журнале дефектов и институциональный аудит мутаций (D007 Gilbert, D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D007, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене предикатов доставки (`DeliveryVerificationPredicateTest`), нарушении монотонности решётки готовности (`VerdictGateTest`), ложных оптимистичных оценках TOC (`TocOptimizerTest`) и несанкционированной перезаписи терминальных статусов (`TaskEntityLaw20Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: предикаты доставки задачи оценивают физические факты приёмки, а не просто статус 'done'; персистентные снимки состояния Парфита и Шаффера (`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT`, `DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT`) фиксируют истинное состояние графа TOC и буферов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код: блокировка диспетчеризации задач с неопределённым целевым контекстом (Law 2 Carrier isolation в `TargetContextTest`), институциональный запрет на перезапись терминальных статусов задач (`TaskTerminalOverwriteAudit`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырёхзначная логика истинности: неизвестное остаётся `UNDETERMINED` и никогда не превращается в молчаливое согласие в `LeanValueTest`, `TargetContextTest`, `AccountStatusConjunctionTest` и слоях вердиктов.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` & `LYUDVIG_VITGENSHTEYN_01_FACT_STATE_TABLE` [D002 Category error / Ryle & Wittgenstein]:**
  * Семантическое разграничение категорий: флаг активности аккаунта отделён от наличия ограничений API (`AccountStatusConjunctionTest`), факт сделанной задачи отделён от доставленной ценности.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 / Gilbert]:**
  * Постоянная институциональная запись дефектов и атрибуция корневых причин в `DefectJournalServiceTest` и `LogScopeAndDefectJournalInusFalsificationTest`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Shaffer]:**
  * Монопольное владение состоянием графа ограничений и транзакционная изоляция компактизации хранилища в `EneikProductionApplicationShutdownCompactTest`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции XXI в едином непрерывном запуске Docker Maven (все 22 тест-сьюта, 133 теста):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,EneikProductionApplicationShutdownCompactTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalRootCauseAttributionTest,InfrastructureVerdictLayerTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,VerdictReconciliationTest,VerdictGateTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DefectJournalServiceTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секция XXI) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
