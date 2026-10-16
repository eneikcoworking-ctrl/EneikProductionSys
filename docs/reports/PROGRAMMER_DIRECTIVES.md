# Директивы Ведущему инженеру фабрики (L2)

**Дата постановки наряда:** 2026-10-16 23:10 UTC  
**Эмитент:** Менеджер-философ фабрики EneikProductionSys (L1/L3)  
**Инвариант оператора Дмитрия Ефремова:** Полная автономия без ожидания внешних подтверждений, пакетный режим (Batching), деонтический запрет мутации рабочего кода при зелёных тестах (Ступени 3 и 4 — строго замер в рантайме, а не правка кода).

---

## 1. Пакетный Work Order: Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация фабрики»)

1. **Цель такта L2:** Провести непрерывный пакетный замер всей совокупности механизмов Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») в едином прогоне в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. **Состав механизмов пакета Секции VIII (290 тестов, 25 сьютов):**
   * **Группа 1: Фальсификация, связность свидетельств и самодиагностика платформы (77 тестов, 6 сьютов):**
     - `FalsificationCycleServiceTest` (28/28): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств ECHO Тагарда (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     - `OpsAuditorServiceTest` (11/11): аудит операций строго на независимо проверенных фактах (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     - `PlatformSelfReferenceDetectorTest` (7/7): фильтрация заводского шума от клиентского бэклога (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     - `FactorySelfHealthServiceTest` (5/5): мониторинг здоровья фабрики с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
   * **Группа 2: Кайдзен, журнал дефектов и INUS-атрибуция (24 теста, 4 сьюта):**
     - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5): дедупликация дефектов и INUS-анализ первопричин (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_LESLI_MAKI_01_INUS_CONDITION` / D007).
   * **Группа 3: Метрики потока, рычаги ТОС и восстановление работ (37 тестов, 4 сьюта):**
     - `FlowMetricsServiceTest` (4/4): расчет времени цикла, WIP, throughput и закон Литтла (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     - `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4): продвижение рычагов ТОС по согласованным наблюдениям (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     - `PlannedWorkRecoveryServiceTest` (15/15): сохранение идентичности восстанавливаемых задач (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
   * **Группа 4: Непрерывная оркестрация, операционное ядро и политики (103 теста, 9 сьютов):**
     - `ContinuousOrchestrationServiceTest` (16/16): единый детерминированный тик оркестрации (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     - `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39): деонтическое ядро переходов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): классификация причин отказов политики (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
     - `TrustSnapshotServiceTest` (9/9): снимки сигналов доверия с отложенным исходом (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): заслоны рыночных измерений (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
   * **Группа 5: Позвоночник потока и операционная правда (49 тестов, 2 сьюта):**
     - `FlowSpineServiceTest` (32/32): разведение чтения и фиксации событий потока (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     - `OperationalTruthServiceTest` (17/17): асимметричная динамика доверия и статус Белнапа (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны суточного цикла фальсификации (`FalsificationCycleServiceTest`), сходимости связности (`EvidenceCoherenceV80TerminationFalsificationTest`), ядра переходов потока (`OperationalFlowCoreFalsificationTest`) и рыночных гипотез (`MarketResearchServiceFalsificationTest`). Опровержение любой симуляции или ложной зелени.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока (`FlowMetricsServiceTest`), каузальная цепочка свидетельств (`EvidenceCoherenceServiceTest`, `TrustSnapshotServiceTest`) и асимметричная динамика доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические заслоны переходов политики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`), независимые свидетельства аудитора (`OpsAuditorServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальное разведение чистого чтения DTO и телеметрии рантайма от побочных эффектов записи (`FlowSpineServiceTest`, `FactorySelfHealthServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов доверия и вердиктов (`LeverPromotionServiceTest`, `OperationalTruthServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole ownership / Varzi & Bratman]:**
  * Мереологическое монопольное владение сущностями и координация единого такта оркестрации (`ContinuousOrchestrationServiceTest`, `PlannedWorkRecoveryServiceTest`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Отсечение категориальных ошибок платформенного шума от продуктового бэклога (`PlatformSelfReferenceDetectorTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_LESLI_MAKI_01_INUS_CONDITION` [D007 Unanchored mutation / Gilbert & Mackie]:**
  * Институциональный журнал дефектов и INUS-анализ первопричин сбоев (`DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% green результат (290/290 green в 25 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(section-viii): complete Section VIII Flow & Metrics and Self-Check verification (D008, D010, D006)`.
