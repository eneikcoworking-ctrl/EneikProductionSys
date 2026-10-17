# Директивы и наряд для L2 (2026-10-17 00:35 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VII (TOC), а также кластеры Секций XXI, XXII и XXIII успешно заверены инженером (коммиты `b9178ba8`, `77025a19`, 116/116 green в 13 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в 25 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика фабрики (72 теста, 6 сьютов):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл фальсификации по дефектам и недельный философский аудит (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): связность свидетельств ECHO Тагарда, пороги завершения V80 без холостых N+1 прогонов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): детектор самореференции и исключение циклических зависимостей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5 green): самопроверка здоровья узлов фабрики без подмены заглушками (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OpsAuditorServiceTest` (11/11 green): операционный аудит по активным проектам со строгой изоляцией `LogScope` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Кайдзен, журнал дефектов и анализ первопричин (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10 green): стандартизация улучшений и обработка сигналов отклонений (`MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` / D007);
       - `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): институциональный учет отказов, INUS-условия первопричин и неподкупность журнала дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007).
     * **Группа 3: Метрики потока, рычаги и непрерывная оркестрация (42 теста, 5 сьютов):**
       - `FlowMetricsServiceTest` (4/4 green): проверка закона Литтла (`WIP ≈ Throughput × CycleTime`) и фальсификация ложных замеров (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): ступенчатая лестница продвижения рычагов от наблюдения к софт-гейту (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): восстановление зависших работ без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `ContinuousOrchestrationServiceTest` (16/16 green): центральный цикл непрерывной оркестрации фабрики (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004).
     * **Группа 4: Операционное ядро, операционная политика, доверие и правда (152 теста, 10 сьютов):**
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткие фальсифицирующие заслоны переходов состояний потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): институциональная матрица прав и кодифицированные деонтические запреты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TrustSnapshotServiceTest` (9/9 green): периодические снимки доверия с динамическим накоплением свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда, вычисление блокеров и доверия строго по фактам (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного состояния потока без паразитных мутаций при чтении (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): полевой аудит рыночного контекста (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, моделей, сущностей, миграций, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские фальсификационные заслоны суточного цикла (`FalsificationCycleServiceTest`), переходов состояний операционного ядра (`OperationalFlowCoreFalsificationTest`), закона Литтла (`FlowMetricsServiceTest`) и рыночных задач (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Строгий лок уровней абстракции метрик потока (`FlowMetricsServiceTest`), операционный аудит со скоупами логов (`OpsAuditorServiceTest`), релайабилистская цепь свидетельств и асимметричная динамика доверия (`TrustSnapshotServiceTest`, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты: операционные политики с явными отказами (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`), детектор недопустимой самореференции (`PlatformSelfReferenceDetectorTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Mereology & Coordination / Varzi & Bratman]:**
  * Монопольное владение восстановлением работ (`PlannedWorkRecoveryServiceTest`) и координация тиков непрерывной оркестрации (`ContinuousOrchestrationServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Релаксация графа свидетельств по алгоритму ECHO Тагарда и телеосемантическая обратная связь восстановления работ.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов операционной правды (`OperationalTruthServiceTest`) и вердиктов продвижения рычагов (`LeverPromotionServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистая наблюдаемость потока FlowSpine без паразитных побочных эффектов записи в БД (`FlowSpineServiceTest`), объективная самопроверка здоровья фабрики (`FactorySelfHealthServiceTest`).
- **`DZHON_MEKI_01_INUS_CONDITION` & `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Fact record / Mackie & Gilbert]:**
  * Объективная INUS-атрибуция дефектов фабрики и институциональный журнал дефектов (`DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   - Заверить все 25 тест-сьютов Секции VIII (290 тестов: 0 failures, 0 errors, 0 skipped).
2. Соблюсти фундаментальный инвариант Ступени 4:
   - **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать результаты замера в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит и обновить `docs/reports/PROGRAMMER_STATE.md`.
