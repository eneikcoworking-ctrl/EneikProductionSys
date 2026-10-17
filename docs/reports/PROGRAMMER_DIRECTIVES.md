# Директивы и наряд для L2 (2026-10-17 07:30 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов») полностью заверен инженером (коммит `21e7df56`, 251/251 green в 27 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**25 тест-сьютов, 290 тестов в едином непрерывном пакетном прогоне в Docker Maven**):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика платформы (70 тестов, 5 сьютов):**
       - `FalsificationCycleServiceTest` (28 тестов), `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов), `OpsAuditorServiceTest` (11 тестов), `PlatformSelfReferenceDetectorTest` (7 тестов): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings`, релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных, аудит операций строго на независимо проверенных фактах с тройной валидацией предусловий, фильтрация заводского шума (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2: Здоровье фабрики, Кайдзен и журнал дефектов (39 тестов, 5 сьютов):**
       - `FactorySelfHealthServiceTest` (5 тестов), `KaizenServiceTest` (10 тестов), `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен, стандартизация улучшений, дедупликация дефектов по `(projectId, category, targetComponent)` и объективный INUS-анализ первопричин (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007);
     * **Группа 3: Метрики потока, рычаги ТОС и восстановление работ (30 тестов, 4 сьюта):**
       - `FlowMetricsServiceTest` (4 теста), `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста), `PlannedWorkRecoveryServiceTest` (15 тестов): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с предупреждением при расхождениях, ступенчатая лестница продвижения рычагов подчинения TOC от `observe_only` к активным действиям строго по согласованным наблюдениям, безопасное восстановление зависших задач со строгим сохранением тождества по контентному ключу и запретом размножения дубликатов (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 4: Операционное ядро, политика, правда потока и рынок (151 тест, 11 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов), `OperationalTruthServiceTest` (17 тестов), `TrustSnapshotServiceTest` (9 тестов), `FlowSpineServiceTest` (32 теста), `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов), `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов), `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): центральный детерминированный тик оркестрации фабрики без сторонних кронов, снимки доверия с отложенным дозаполнением исходов сдачи, честное отражение метрик рантайма (`done_is_not_delivery`), разведение чтения `build` и записи `observe`, переходы операционного ядра и изоляция фаз потока, деонтическая защита операционной политики с 4-значной решёткой истинности и типизированными причинами отказа, детерминированный сбор рыночных требований через изолированные каузальные задачи системного носителя (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D007, D004, D002, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: суточный фальсификационный цикл дефектов (`FalsificationCycleServiceTest`), фальсификация порогов связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), фальсификационные заслоны операционного ядра потока (`OperationalFlowCoreFalsificationTest`), фальсификация рычагов (`LeverPromotionServiceFalsificationTest`), фальсификация полевого рынка (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение макро-метрик потока (закон Литтла в `FlowMetricsService`) от микро-таймингов БД; снимки доверия (`TrustSnapshotService`) с защитой от преждевременного доверия; семантическая изоляция `LogScope`; объективная каузальная цепочка свидетельств.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Институциональная матрица прав и обязанностей операционной политики (`OperationalPolicyService`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`) с типизированными причинами отказа и запретом произвольных действий.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Телеметрия Flow Spine и здоровья фабрики без паразитных мутаций состояния при чтении; разведение чистого наблюдения от акта модификации; инвариант `done_is_not_delivery`.
- **`DZHON_MEKI_01_INUS_CONDITION` & `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Mackie & Gilbert]:**
  * Неподкупный журнал дефектов фабрики, объективное установление INUS-причин сбоев без домыслов и коллективная ответственность институциональных акторов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Ambiguity of truth / Belnap]:**
  * 4-значная решётка истинности (`true`, `false`, `undetermined`, `unapplied`) в операционной правде, политике и ступенчатой лестнице продвижения рычагов TOC.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole ownership / Varzi & Bratman]:**
  * Партитивное владение и координация единого детерминированного тика непрерывной оркестрации без гонок и внешних сторонних кронов.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
```

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 25 тест-сьютов Секции VIII (290 тестов) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Строго соблюсти инвариант Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Зафиксировать статус (290/290 green, ideal) в `docs/reports/PROGRAMMER_STATE.md`, обновить строки *Живое:* и итоговые блоки Секции VIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
