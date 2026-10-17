# Директивы и наряд для L2 (2026-10-17 02:25 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI, Six Sigma, сборка мусора, гейты экранов, оптимизатор TOC, журнал») успешно заверен инженером (коммит `3a0af91d`, 143/143 green в 15 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификация, связность свидетельств и аудит операций (83 теста, 6 сьютов):**
       - `FalsificationCycleServiceTest` (28/28): суточный цикл фальсификации и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
       - `OpsAuditorServiceTest` (11/11): аудит операций строго на независимо проверенных фактах с тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
       - `PlatformSelfReferenceDetectorTest` (7/7): категорический заслон отсечения внутризаводского шума от бэклога заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
       - `FactorySelfHealthServiceTest` (5/5): мониторинг здоровья фабрики (БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов и INUS-атрибуция (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5): дедупликация предложений PDCA по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов и INUS-анализом первопричин (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `D007 INUS condition`).
     * **Группа 3: Метрики потока, закон Литтла, продвижение рычагов и оркестрация (53 теста, 5 сьютов):**
       - `FlowMetricsServiceTest` (4/4): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
       - `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4): лестница продвижения рычагов от `observe_only` к активным действиям на основе накопленных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
       - `PlannedWorkRecoveryServiceTest` (15/15): восстановление задач со строгим сохранением тождества личности задачи без паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
       - `ContinuousOrchestrationServiceTest` (16/16): единый детерминированный тик оркестрации фабрики без сторонних неуправляемых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 4: Операционная правда, операционное ядро, FlowSpine и деонтические политики (121 тест, 7 сьютов):**
       - `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32), `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39), `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): деонтические заслоны переходов политики, честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная решётка истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 5: Снимки доверия и рыночные исследования (18 тестов, 3 сьюта):**
       - `TrustSnapshotServiceTest` (9/9): сохранение входных сигналов доверия с отложенным дозаполнением исходов сдачи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
       - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): детерминированный сбор внешних рыночных измерений без мутации клиентского кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D004, D002, D007, D012, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: суточный цикл фальсификации (`FalsificationCycleServiceTest`), заслоны связности (`EvidenceCoherenceV80TerminationFalsificationTest`), рыночные исследования (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и релайабилистская цепь: арифметика метрик потока (`FlowMetricsServiceTest`), снимки доверия без искажения входных факторов (`TrustSnapshotServiceTest`), аудит операций на знаниевом фундаменте (`OpsAuditorServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон операционной политики (`OperationalPolicyDeniedExceptionFalsificationTest`), исключающий произвольные действия в потоке.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия рантайма FlowSpine без паразитных мутаций в БД, честный статус `done_is_not_delivery`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole & Shared activity / Varzi & Bratman]:**
  * Монопольное владение агрегатами фальсификации, единый детерминированный тик оркестрации фабрики.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Категориальное разделение: отсечение внутреннего платформенного шума от задач заказчика (`PlatformSelfReferenceDetectorTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & INUS-анализ [D007 Institutional defect / Gilbert & Mackie]:**
  * Непрерывная фиксация дефектов в журнале с INUS-атрибуцией первопричин (`DefectJournalRootCauseAttributionTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов операционного ядра и вердиктов продвижения рычагов.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% green (**290/290 green, 25 тест-сьютов, 0 failures, 0 errors, BUILD SUCCESS**).
3. Обеспечить строго 0 строк изменений рабочего кода подсистем (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки `*Живое:*` и итоговые блоки Секции VIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчёт (15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
6. Выполнить коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
