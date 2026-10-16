# Директивы и наряд для L2 (2026-10-16 16:28 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты оператора и Позвоночник потока») полностью заверены инженером (коммит `2dbf2f7`, 169/169 green, 0 строк изменений рабочего кода).
   - Секция VII («Измерение: Lean, ТОС, Шесть сигм») ранее замерена и закрыта.
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («VIII. Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (**290 тестов в 25 сьютах** в едином пакетном прогоне в Docker Maven):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика фабрики (72 теста):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл по дефектам и недельный философский аудит (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): связность свидетельств, пороги завершения V80 без холостых N+1 прогонов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): детектор самореференции и исключение циклических зависимостей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FactorySelfHealthServiceTest` (5/5 green): самопроверка здоровья узлов фабрики без подмены заглушками (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OpsAuditorServiceTest` (11/11 green): операционный аудит по активным проектам со строгой изоляцией `LogScope` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 2: Кайдзен, журнал дефектов и анализ первопричин (24 теста):**
       - `KaizenServiceTest` (10/10 green): стандартизация улучшений и обработка сигналов отклонений (`MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` / D007);
       - `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): институциональный учет отказов, INUS-условия первопричин и неподкупность журнала дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007).
     * **Группа 3: Метрики потока, рычаги и непрерывная оркестрация (42 теста):**
       - `FlowMetricsServiceTest` (4/4 green): проверка закона Литтла (`WIP ≈ Throughput × CycleTime`) и фальсификация ложных замеров (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): ступенчатая лестница продвижения рычагов от наблюдения к софт-гейту (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): восстановление зависших работ без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `ContinuousOrchestrationServiceTest` (16/16 green): центральный цикл непрерывной оркестрации фабрики (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 4: Операционное ядро, операционная политика, доверие и правда (152 теста):**
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткие фальсифицирующие заслоны переходов состояний потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): институциональная матрица прав и кодифицированные запреты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TrustSnapshotServiceTest` (9/9 green): периодические снимки доверия с динамическим накоплением свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда, вычисление блокеров и доверия строго по фактам (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного состояния потока без паразитных мутаций при чтении (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): полевой аудит рыночного контекста (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D007, D012, D004, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация операционного ядра (`OperationalFlowCoreFalsificationTest`), закона Литтла (`FlowMetricsServiceTest`) и полевого исследования (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепь надежности свидетельств: изоляция активных проектов в `OpsAuditorService`, агрегаты связности в `EvidenceCoherenceService`, снимки доверия в `TrustSnapshotService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодификация институциональных запретов и исключений (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Физическое состояние базы и логов выше любых словесных самоописаний сервисов. Наблюдаемость `FlowSpineService` без побочных эффектов.
- **`DZHON_MEKI_01_INUS_CONDITION` & `MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` [D007 Institutional defect record / Mackie & Gilbert]:**
  * Анализ первопричин отказов и целостность журнала дефектов (`DefectJournalRootCauseAttributionTest`, `KaizenServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности (`TRUE`, `FALSE`, `BOTH`, `NEITHER`) в статусах доверия `OperationalTruthService`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Строгое разграничение монопольного владения состоянием в `ContinuousOrchestrationService`.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   - Maven suite: `mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest` (290 тестов в 25 сьютах).
2. Зафиксировать результат (290/290 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
