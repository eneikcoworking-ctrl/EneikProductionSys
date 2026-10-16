# Директивы и наряд для L2 (2026-10-16 21:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущий кластер (Секции XVII, XVIIб, XIX и XX) успешно заверен инженером (коммит `5225365c`, 158/158 green в едином Docker Maven прогоне, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в 25 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Фальсификационные циклы, связность свидетельств и аудит операций (65 тестов, 4 сьюта):**
       - Суточный цикл фальсификации и целостность кода: `FalsificationCycleServiceTest` (28/28);
       - Связность свидетельств по алгоритму Тагарда ECHO и демаркация остановки: `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5);
       - Аудитор операций на независимо проверенных фактах: `OpsAuditorServiceTest` (11/11).
     * **Группа 2: Самопроверка здоровья фабрики, Кайдзен и журнал дефектов (36 тестов, 5 сьютов):**
       - Отсечение внутризаводского шума от задач заказчика: `PlatformSelfReferenceDetectorTest` (7/7);
       - Непрерывный мониторинг собственного здоровья фабрики: `FactorySelfHealthServiceTest` (5/5);
       - Кайдзен и институциональный журнал дефектов: `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5).
     * **Группа 3: Метрики потока, рычаги, восстановление и оркестрация (35 тестов, 4 сьюта):**
       - Расчет времени цикла, WIP и сходимости закона Литтла: `FlowMetricsServiceTest` (4/4);
       - Лестница продвижения рычагов от наблюдения к действию: `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4);
       - Восстановление упавших задач со строгим сохранением тождества: `PlannedWorkRecoveryServiceTest` (15/15);
       - Единый детерминированный тик оркестрации фабрики: `ContinuousOrchestrationServiceTest` (16/16).
     * **Группа 4: Операционная правда, позвоночник потока, политика и снимки доверия (145 тестов, 10 сьютов):**
       - Асимметричная лестница доверия и операционная правда: `OperationalTruthServiceTest` (17/17);
       - Позвоночник потока и демаркация сдачи: `FlowSpineServiceTest` (32/32);
       - Ядро операционного потока и заслоны фальсификации: `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39);
       - Деонтическая операционная политика и причины отказов: `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9);
       - Снимки входных сигналов доверия с отложенным исходом: `TrustSnapshotServiceTest` (9/9);
       - Сбор внешних рыночных измерений через каузальные задачи: `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей, конфигураций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка суточного цикла (`FalsificationCycleServiceTest`), заслоны остановки связности свидетельств (`EvidenceCoherenceV80TerminationFalsificationTest`), фальсификация рычагов (`LeverPromotionServiceFalsificationTest`) и рыночных задач (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока (WIP, throughput, cycle time в `FlowMetricsServiceTest`); надежность цепочки свидетельств по Тагарду (`EvidenceCoherenceServiceTest`); асимметричная динамика роста доверия (`OperationalTruthServiceTest`) и снимки сигналов с отложенным дозаполнением (`TrustSnapshotServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`), запрет самореферентных петель и сторонних кронов (`ContinuousOrchestrationServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole ownership / Varzi & Bratman]:**
  * Монопольное владение восстановлением запланированной работы со строгим сохранением тождества задач (`PlannedWorkRecoveryServiceTest`) и координация тиков оркестрации.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов операционной правды (`OperationalTruthServiceTest`), лестницы продвижения рычагов (`LeverPromotionServiceTest`) и различие `done_is_not_delivery` в позвоночнике потока (`FlowSpineServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: мониторинг собственного здоровья фабрики (`FactorySelfHealthServiceTest`) и операционной правды без побочных мутаций физического состояния базы данных и контейнеров.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Отсечение категориальной ошибки: демаркация платформенного шума от бизнес-требований заказчика (`PlatformSelfReferenceDetectorTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Gilbert & Mackie]:**
  * Институциональная регистрация дефектов в журнале Кайдзен и INUS-анализ первопричин (`KaizenServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% green результат (290/290 green, 0 failures, 0 errors, 0 skipped, 25 тест-сьютов).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
