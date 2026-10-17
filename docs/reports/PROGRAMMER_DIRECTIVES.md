# Директивы и наряд для L2 (2026-10-17 02:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна») успешно заверен инженером (коммит `4823abc2`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика фабрики (72 теста, 6 сьютов):**
       - `FalsificationCycleServiceTest` (28/28): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств ECHO Тагарда (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11/11): аудит операций строго на независимо проверенных фактах (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7): демаркация заводского шума от клиентского бэклога (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5): анти-зеркальный мониторинг здоровья фабрики с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов и INUS-анализ первопричин (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5): институциональная фиксация дефектов и INUS-анализ первопричин (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_LESLI_MAKI_01_INUS_CONDITION` / D007).
     * **Группа 3: Метрики потока, рычаги ТОС и восстановление работ (42 теста, 5 сьютов):**
       - `FlowMetricsServiceTest` (4/4): лок уровней абстракции метрик потока (время цикла, WIP, throughput) и закон Литтла (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4): 4-значная решётка истинности Белнапа при согласовании наблюдений ТОС (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15): сохранение идентичности восстанавливаемых задач и категориальная чистота (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `ContinuousOrchestrationServiceTest` (16/16): детерминированная координация единого кооперативного действия фабрики (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 4: Операционное ядро, операционная политика, доверие и правда (152 теста, 10 сьютов):**
       - `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39): деонтическое ядро переходов и барьеры исключающих оснований (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): матрица прав и обязанностей операционной политики (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TrustSnapshotServiceTest` (9/9): цепь свидетельств снимков доверия с отложенным исходом (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalTruthServiceTest` (17/17): асимметричная динамика доверия и статус Белнапа (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32/32): анти-зеркальное разведение чтения и фиксации событий потока (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): попперовский заслон фальсификации рыночных гипотез (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` [D008 False green / Popper & Church]:**
  * Фальсификационные заслоны суточного цикла, графа свидетельств, рыночных измерений и редукции формул метрик потока к детерминированному ядру.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока и связности событий; строгая цепь свидетельств снимков доверия; асимметричный рост доверия операционной правды.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Матрица прав и обязанностей операционной политики фабрики и деонтический барьер аудита операций.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Boundary violation / Varzi & Bratman]:**
  * Монопольное владение восстановлением незавершенных работ и детерминированная координация единого cooperative loop оркестрации.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов суждений ТОС и операционной правды (`{T, F, Both, Neither}`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистый опрос здоровья фабрики и событий позвоночника потока без паразитных мутаций базы данных.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок: фильтрация платформенного шума от пользовательских задач заказчика.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_LESLI_MAKI_01_INUS_CONDITION` [D007 Attribution error / Gilbert & Mackie]:**
  * Институциональный журнал дефектов фабрики и анализ первопричин по критерию INUS.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Dretske]:**
  * Телеосемантическая обратная связь при релаксации графа согласованности свидетельств V80.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,OpsAuditorServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% green результат (290/290 green в 25 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
