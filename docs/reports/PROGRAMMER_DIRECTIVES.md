# Директивы и наряд для L2 (2026-10-17 17:48 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV («Сторож ТОС, вход к ресурсам модели, метрики Six Sigma, выметающий обход, экран, RAG, верёвка ТОС, журнал») полностью заверен и закрыт инженером (коммит `6f4f9614`, 206/206 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:55 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Секцию VIII: «Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (5 сьютов, 72 теста):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл фальсификации и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11 тестов): аудит операций на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7 тестов): отсечение внутризаводского шума от продуктового бэклога заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (5 сьютов, 29 тестов):**
       - `FactorySelfHealthServiceTest` (5 тестов): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10 тестов), `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): дедупликация и жизненный цикл улучшений PDCA по `(projectId, category, targetComponent)` с INUS-анализом первопричин (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (4 сьюта, 30 тестов):**
       - `FlowMetricsServiceTest` (4 теста): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста): лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (11 сьютов, 159 тестов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов): единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalTruthServiceTest` (17 тестов), `FlowSpineServiceTest` (32 теста): честное отражение метрик рантайма (`done_is_not_delivery`), публикация свидетельств против собственного благополучия, 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TrustSnapshotServiceTest` (9 тестов): снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов), `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): ядро операционного потока, деонтические заслоны переходов политики с типизированной причиной отказа, защита от неавторизованных смен статусов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): детерминированный сбор рыночных измерений через каузальные задачи без мутации клиентского кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка суточного цикла фальсификации (`FalsificationCycleServiceTest`), фальсификационные контрпримеры ядра потока (`OperationalFlowCoreFalsificationTest`), рыночных измерений (`MarketResearchServiceFalsificationTest`) и исключений политики (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss & Abstraction lock / Goldman]:**
  * Жесткий лок уровней абстракции метрик потока Литтла (`FlowMetricsServiceTest`), цепь надежности снимков доверия (`TrustSnapshotServiceTest`) и связности свидетельств Тагарда (`EvidenceCoherenceServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity & Prohibitions / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Заслон авторизации и прав/обязанностей в `OperationalPolicyService` и `OpsAuditorService`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Belnap & Tarski multi-valued logic]:**
  * 4-значная решётка истинности в `OperationalTruthService` и `FlowSpineService` (true, false, both, neither / passed, failed, undetermined).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Непрерывный мониторинг собственного здоровья фабрики (`FactorySelfHealthService`), честная публикация свидетельств потока против собственного благополучия (`done_is_not_delivery`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D004 Varzi & D002 Ryle]:**
  * Монопольное владение агрегатами фальсификации, стабильная идентичность задач при восстановлении (`PlannedWorkRecoveryService`) и отсечение внутризаводского шума (`PlatformSelfReferenceDetector`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Gilbert & Mackie]:**
  * Непреложная институциональная фиксация дефектов в Кайдзен с INUS-анализом первопричин.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводятся по директиве оператора.
