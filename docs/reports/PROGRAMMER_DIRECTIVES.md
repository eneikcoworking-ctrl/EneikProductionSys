# Директивы и наряд для L2 (2026-10-16 19:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, память об отказах и хранилище») успешно заверена инженером (коммит `ebd3de12`, 133/133 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (**290 тестов в 25 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Контур фальсификации и связности свидетельств (54 теста, 3 сьюта):**
       - Суточный цикл фальсификации и монопольное владение агрегатами: `FalsificationCycleServiceTest` (28/28);
       - Релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности: `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5).
     * **Группа 2: Аудит операций, самореференция и собственное здоровье фабрики (23 теста, 3 сьюта):**
       - Аудитор операций на независимо проверенных фактах с тройной валидацией предусловий: `OpsAuditorServiceTest` (11/11);
       - Отсечение заводского шума от клиентского бэклога: `PlatformSelfReferenceDetectorTest` (7/7);
       - Мониторинг собственного здоровья фабрики (размер БД, пулы, очереди): `FactorySelfHealthServiceTest` (5/5).
     * **Группа 3: Цикл улучшений Кайдзен и журнал дефектов (24 теста, 4 сьюта):**
       - Жизненный цикл микро-улучшений PDCA: `KaizenServiceTest` (10/10);
       - Фиксация отказов и детерминированная каузальная атрибуция INUS: `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5).
     * **Группа 4: Метрики потока, рычаги и исследование рынка (24 теста, 4 сьюта):**
       - Арифметика потока Little's Law (WIP, Cycle Time, Throughput): `FlowMetricsServiceTest` (4/4);
       - Продвижение рычагов по накопленным свидетельствам: `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4);
       - Очередь задач исследования рынка через системный контекст: `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6).
     * **Группа 5: Оркестрация, восстановление и операционная правда потока (165 тестов, 11 сьютов):**
       - Восстановление запланированной работы с сохранением идентичности: `PlannedWorkRecoveryServiceTest` (15/15);
       - Единый детерминированный тик оркестрации: `ContinuousOrchestrationServiceTest` (16/16);
       - Операционная правда и асимметричная динамика доверия: `OperationalTruthServiceTest` (17/17);
       - Позвоночник потока с разделением чтения и записи: `FlowSpineServiceTest` (32/32);
       - Ядро потока в режиме принудительного исполнения: `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39);
       - Деонтическая операционная политика и отказы с обоснованием: `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9);
       - Долговечные снимки входных сигналов доверия: `TrustSnapshotServiceTest` (9/9).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, моделей, сущностей, перечислений, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004, D013, D011, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: суточный цикл фальсификации (`FalsificationCycleServiceTest`), фальсификация аномалий и отказов операционной политики (`OperationalPolicyDeniedExceptionFalsificationTest`), фальсификация исследований рынка (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: стратификация метрик качества и потока, сохранность данных снимков доверия (`TrustSnapshotServiceTest`), надежность источников операционной правды (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические заслоны переходов политики (`OperationalPolicyServiceTest`), матрица прав и обязанностей ядра потока (`OperationalFlowCoreFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначная логика истинности в статусах доверия и рычагов (`undetermined != blocked != trusted`), честное отражение неопределенности без подмены нулями.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Concurrency conflict & D006 / Varzi]:**
  * Монопольное владение агрегатами `falsification_runs` и `code_integrity_findings`, топологические границы сущностей и запрет коллизий.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: разделение чтения `build` и фиксации наблюдения `observe` в `FlowSpineServiceTest`, мониторинг здоровья фабрики (`FactorySelfHealthServiceTest`) выше любого нарратива агента.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Sensor disconnection / Dretske]:**
  * Релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`EvidenceCoherenceServiceTest`).
- **`DURDZHON_MJEKKI_03_INUS_CONDITION` [D007 Epistemic fabrication / Mackie]:**
  * Детерминированная каузальная атрибуция дефектов по методу INUS без субъективных домыслов (`DefectJournalRootCauseAttributionTest`).

---

## 3. Ожидаемый результат такта L2

1. Прогон единого пакетного замера всех 25 сьютов Секции VIII (290 тестов) в Docker Maven Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest
   ```
2. Подтверждение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализация строк `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Запись отчёта в `docs/reports/PROGRAMMER_STATE.md` и коммит с фиксацией замера Секции VIII.
