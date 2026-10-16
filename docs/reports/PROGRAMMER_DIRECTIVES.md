# Директивы и наряд для L2 (2026-10-16 17:36 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики») полностью заверен инженером (коммит `4b47eb5`, 143/143 green, 0 строк изменений рабочего кода).
   - Секция VII («Измерение: Lean, ТОС, Шесть сигм») ранее полностью замерена и закрыта.
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («VIII. Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (**290 тестов в 25 тест-сьютах** в едином пакетном прогоне в Docker Maven):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика фабрики (72 теста):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл по дефектам и недельный философский аудит, монопольное владение агрегатами (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): связность свидетельств ECHO Тагарда, пороги завершения V80 без холостых N+1 прогонов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): детектор самореференции и отсечение внутризаводского шума (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5 green): самопроверка здоровья узлов фабрики без подмены заглушками (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OpsAuditorServiceTest` (11/11 green): операционный аудит по активным проектам со строгой изоляцией `LogScope` и проверкой полномочий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 2: Кайдзен, журнал дефектов и анализ первопричин (24 теста):**
       - `KaizenServiceTest` (10/10 green): дедупликация и стандартизация микро-улучшений PDCA (`MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` / D007);
       - `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): институциональный учет отказов, INUS-условия первопричин и неподкупность журнала дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007).
     * **Группа 3: Метрики потока, рычаги и непрерывная оркестрация (42 теста):**
       - `FlowMetricsServiceTest` (4/4 green): проверка закона Литтла (`WIP ≈ Throughput × CycleTime`) и фальсификация ложных замеров (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): ступенчатая лестница продвижения рычагов от наблюдения к софт-гейту (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): восстановление зависших работ без потери детерминизма и паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `ContinuousOrchestrationServiceTest` (16/16 green): центральный цикл непрерывной оркестрации фабрики без сторонних кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 4: Операционное ядро, операционная политика, доверие и правда (152 теста):**
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткие фальсифицирующие заслоны переходов состояний потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): институциональная матрица прав и кодифицированные запреты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TrustSnapshotServiceTest` (9/9 green): периодические снимки доверия с динамическим накоплением свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда, вычисление блокеров и доверия строго по фактам (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного состояния потока без паразитных мутаций при чтении (`done_is_not_delivery`, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D012, D007, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: жесткие заслоны переходов потока (`OperationalFlowCore`), фальсификация ложных замеров закона Литтла (`FlowMetricsService`), суточный цикл поиска дефектов (`FalsificationCycleService`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: строгое разделение уровней в `EvidenceCoherenceService`, `TrustSnapshotService`, изоляция `LogScope` в `OpsAuditorService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Институциональная матрица прав и запретов переходов в `OperationalPolicyService`, демаркация полномочий аудитора (`OpsAuditorService`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary topology / Varzi]:**
  * Монопольное владение таблицами `falsification_runs` и `code_integrity_findings` в `FalsificationCycleService`, неделимость циклов оркестрации в `ContinuousOrchestrationService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистая телеметрия рантайма без побочных эффектов записи (`done_is_not_delivery` в `FlowSpineService`), самопроверка фабрики в `FactorySelfHealthService`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная логика истинности блокеров и доверия в `OperationalTruthService`, лестница состояний рычагов в `LeverPromotionService`.
- **`MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Evidence gap / Gilbert & Mackie]:**
  * Институциональный учет дефектов и INUS-анализ первопричин в `KaizenService` и `DefectJournalService`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Subsystem drift / Dretske]:**
  * Телеосемантическая обратная связь при восстановлении работ в `PlannedWorkRecoveryService`.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Категориальная демаркация внутреннего фабричного шума и клиентских продуктовых задач в `PlatformSelfReferenceDetector`.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,OpsAuditorServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest`
2. Зафиксировать результат (290/290 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
