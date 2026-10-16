# Директивы и наряд для L2 (2026-10-16 14:23 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики») полностью заверены инженером (коммит `c49a37d`, 143/143 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином Docker Maven прогоне):
     * **Группа 1: Цикл фальсификации и связность свидетельств (FalsificationCycleService & EvidenceCoherenceService):**
       - `FalsificationCycleServiceTest` (28/28): монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004), суточный цикл фальсификации (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Группа 2: Аудит операций и самодиагностика фабрики (OpsAuditorService, PlatformSelfReferenceDetector, FactorySelfHealthService):**
       - `OpsAuditorServiceTest` (11/11): аудит на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7): отсечение внутризаводского шума от продуктового бэклога заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 3: Кайдзен, журнал дефектов и метрики потока (KaizenService, DefectJournalService, FlowMetricsService):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5): дедупликация по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `FlowMetricsServiceTest` (4/4): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4: Продвижение рычагов и восстановление задач (LeverPromotionService, PlannedWorkRecoveryService):**
       - `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4): лестница продвижения рычагов от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15): восстановление упавших задач со строгим сохранением идентичности исходной задачи и запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 5: Непрерывная оркестрация (ContinuousOrchestrationService):**
       - `ContinuousOrchestrationServiceTest` (16/16): единый детерминированный тик оркестрации фабрики без сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 6: Операционная политика, ядро потока и правда (OperationalFlowCoreService, OperationalPolicyService, OperationalTruthService, FlowSpineService):**
       - `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32), `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39), `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): деонтические заслоны переходов политики, честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 7: Снимки доверия и рыночные исследования (TrustSnapshotService, MarketResearchService):**
       - `TrustSnapshotServiceTest` (9/9): снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): детерминированный сбор внешних рыночных измерений через каузальные задачи без мутации клиентского кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: воспроизводимые тесты-заслоны циклов фальсификации кода, честный статус готовности потока, проверка сходимости закона Литтла и внешних измерений.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепь свидетельств: надежный расчет метрик потока без N+1 запросов и тяжелых подъемов в heap; сбор снимков доверия с гарантией дозаполнения исходов; аудит действий исключительно на независимо установленных фактах.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Заслон несанкционированных переходов состояний в операционной политике и монопольный контроль тиков оркестрации.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: честное свидетельство против собственного благополучия (`done_is_not_delivery`), беспристрастный мониторинг здоровья фабрики (`FactorySelfHealthService`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная таблица истинности в `OperationalPolicyService` и `LeverPromotionService`: разграничение истинных, ложных, противоречивых и неустановленных статусов.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Mereological confusion / Varzi & Bratman]:**
  * Четкое владение агрегатами `falsification_runs` и `code_integrity_findings`; сохранение идентичности задачи при восстановлении (`PlannedWorkRecoveryService`); скоординированная работа оркестрации.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение фабричных находок и внутреннего технического шума от продуктового бэклога заказчика (`PlatformSelfReferenceDetector`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Gilbert]:**
  * Фиксация дефектов и предложений улучшений в долговечном институциональном реестре (`KaizenService`, `DefectJournalService`).

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 25 тест-сьютов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить строго **290/290 green** (25 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
