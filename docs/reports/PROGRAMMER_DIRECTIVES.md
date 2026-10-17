# Директивы и наряд для L2 (2026-10-17 05:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Пакетный наряд в работу (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш корпуса, верёвка TOC, очистка журналов и завершающий баланс фабрики») полностью заверен инженером (коммит `10cfa05e`, 143/143 green в 15 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификация, связность и аудит (70 тестов, 5 сьютов):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный фальсификационный цикл, обнаружение дефектов кода и монопольное владение таблицами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): релаксация графа свидетельств по алгоритму Тагарда ECHO, порог отсечки V80 без ложной уверенности на пустых выборках (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11/11 green): аудит операций на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): отсечение внутризаводского шума от продуктового бэклога заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 2: Здоровье, Кайдзен и журнал дефектов (39 тестов, 5 сьютов):**
       - `FactorySelfHealthServiceTest` (5/5 green): мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10/10 green), `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): журнал дефектов, дедупликация по `(projectId, category, targetComponent)` и объективная INUS-атрибуция первопричин без субъективных гипотез (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007).
     * **Группа 3: Метрики потока, рычаги и восстановление (30 тестов, 4 сьюта):**
       - `FlowMetricsServiceTest` (4/4 green): расчет времени цикла, WIP, throughput и сверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): лестница продвижения рычагов подчинения TOC от `observe_only` к активным действиям строго по согласованным наблюдениям (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): восстановление зависших задач со строгим сохранением тождества по контентному ключу и запретом размножения дубликатов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 4: Операционное ядро, политика, правда потока и рынок (151 тест, 11 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16/16 green): единый детерминированный тик оркестрации фабрики без сторонних кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalTruthServiceTest` (17/17 green), `TrustSnapshotServiceTest` (9/9 green): операционная правда фабрики, снимки доверия с отложенным дозаполнением исходов сдачи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `FlowSpineServiceTest` (32/32 green): позвоночник потока, честное отражение метрик рантайма (`done_is_not_delivery`), разведение чтения `build` и записи `observe` (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): переходы операционного ядра, изоляция фаз потока (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): деонтическая защита операционной политики, 4-значная решётка истинности, типизированные причины отказа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): детерминированный сбор рыночных требований через изолированные каузальные задачи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D007, D011, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: заслоны фальсификационного цикла (`FalsificationCycleServiceTest`), заслон V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), фальсификация рычагов (`LeverPromotionServiceFalsificationTest`), фальсификационные заслоны операционного ядра (`OperationalFlowCoreFalsificationTest`), политики (`OperationalPolicyDeniedExceptionFalsificationTest`) и исследования рынка (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение уровней абстракции метрик потока (`FlowMetricsServiceTest`), проверка сходимости формулы Литтла, надежная фиксация свидетельств операционной правды (`OperationalTruthServiceTest`) и снимков доверия (`TrustSnapshotServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита операционной политики с типизированными причинами отказа (`OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`) и аудит операций (`OpsAuditorServiceTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок: изоляция внутренних дефектов платформы от продуктового бэклога заказчика (`PlatformSelfReferenceDetectorTest`) и исключение размножения дубликатов задач (`PlannedWorkRecoveryServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Varzi]:**
  * Партитивное владение и границы: монопольное владение таблицами фальсификаций (`FalsificationCycleServiceTest`) и сохранение тождества восстанавливаемых задач (`PlannedWorkRecoveryServiceTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Root-cause blindness / Gilbert & Mackie]:**
  * Институциональный журнал дефектов и INUS-условие достаточной причины: объективная причинно-следственная атрибуция инцидентов (`DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Semantic drift / Dretske]:**
  * Телеосемантическая обратная связь: релаксация графа свидетельств по алгоритму ECHO без ложной уверенности (`EvidenceCoherenceServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная логика истинности в продвижении рычагов (`LeverPromotionServiceTest`) и операционной политике (`OperationalPolicyServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: чистый опрос собственного здоровья фабрики (`FactorySelfHealthServiceTest`) и состояния позвоночника потока (`FlowSpineServiceTest`) без паразитных мутаций.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
```
