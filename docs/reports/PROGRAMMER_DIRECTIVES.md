# Директивы и наряд для L2 (2026-10-16 22:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV и XVIII полностью заверен инженером (коммит `fdf25b8d`, 143/143 green в 15 тест-сьютах единого Docker Maven прогона, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификация, связность свидетельств и самодиагностика платформы (77 тестов, 6 сьютов):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл фальсификации, генерация заменяющих задач, монопольное владение реестром находок целостности кода (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): релаксация графа свидетельств по алгоритму ECHO Тагарда, жесткий лимит истории прогонов, отсечка холостых циклов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11/11 green): аудит операций на независимо подтвержденных фактах с тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): демаркация платформенного шума от реальных дефектов продукта (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5 green): самодиагностика узлов фабрики без ложных срабатываний (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов и анализ первопричин INUS (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10 green): стандартизация предложений улучшений (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): институциональный учет отказов, INUS-условия первопричин и неподкупность журнала дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007).
     * **Группа 3: Метрики потока, рычаги TOC и оркестрация (42 теста, 5 сьютов):**
       - `FlowMetricsServiceTest` (4/4 green): проверка закона Литтла (`WIP ≈ Throughput × CycleTime`) и фальсификация ложных замеров (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): ступенчатая лестница продвижения рычагов от наблюдения к софт-гейту (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): восстановление зависших работ без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `ContinuousOrchestrationServiceTest` (16/16 green): центральный цикл непрерывной оркестрации фабрики (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004).
     * **Группа 4: Операционное ядро, операционная политика и снимки доверия (98 тестов, 7 сьютов):**
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткие фальсифицирующие заслоны переходов состояний потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): институциональная матрица прав и кодифицированные запреты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TrustSnapshotServiceTest` (9/9 green): периодические снимки доверия с динамическим накоплением свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): полевой аудит рыночного контекста (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 5: Позвоночник потока и операционная правда (49 тестов, 2 сьюта):**
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного состояния потока без паразитных мутаций при чтении (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда, вычисление блокеров и доверия строго по фактам (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D004, D012, D007, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны суточного цикла (`FalsificationCycleServiceTest`), отсечки связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), инвариантов ядра потока (`OperationalFlowCoreFalsificationTest`), рычагов TOC (`LeverPromotionServiceFalsificationTest`) и рыночных гипотез (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока и снимков состояния; релайабилистская цепь связности свидетельств; асимметричный рост доверия операционной правды строго по фактам.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Матрица прав и обязанностей операционной политики фабрики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: объективные факты рантайма и базы выше словесных деклараций; чтение метрик и позвоночника FlowSpine без побочных эффектов записи.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAYKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Boundary dissolution / Varzi & Bratman]:**
  * Монопольное владение восстановлением брошенных работ (`PlannedWorkRecoveryServiceTest`) и координация тиков непрерывной оркестрации (`ContinuousOrchestrationServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов ядра потока, доверия и исходов вердиктов.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Unanchored defect record / Gilbert & Mackie]:**
  * Институциональный журнал дефектов и каузальная атрибуция первопричин по условию INUS (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Drifting proxy / Dretske]:**
  * Телеосемантическая обратная связь в релаксации графа свидетельств Тагарда.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Сканирование категориальных ошибок: демаркация внутренних шумов платформы от внешних дефектов продукта заказчика.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest,FlowSpineServiceTest,OperationalTruthServiceTest
   ```
2. Подтвердить 100% green результат (290/290 green в 25 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
