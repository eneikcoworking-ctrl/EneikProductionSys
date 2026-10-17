# Директивы и наряд для L2 (2026-10-17 02:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Строгие семейства Ступени 4 («Заслоны качества задач, решётка вердиктов и провижининг проектов») успешно заверены инженером (коммит `10e9eb31`, 144/144 green в 21 тест-сьюте Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификация, связность свидетельств и самодиагностика платформы (83 теста, 6 сьютов):**
       - `FalsificationCycleServiceTest` (28/28): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств ECHO Тагарда (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11/11): аудит операций строго на независимо проверенных фактах (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7): фильтрация заводского шума от клиентского бэклога (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5): мониторинг здоровья фабрики с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов и INUS-атрибуция (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5): дедупликация дефектов и INUS-анализ первопричин (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_LESLI_MAKI_01_INUS_CONDITION` / D007).
     * **Группа 3: Метрики потока, рычаги ТОС и восстановление работ (53 теста, 5 сьютов):**
       - `FlowMetricsServiceTest` (4/4): расчет времени цикла, WIP, throughput и закон Литтла (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4): продвижение рычагов ТОС по согласованным наблюдениям (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15): сохранение идентичности восстанавливаемых задач (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `ContinuousOrchestrationServiceTest` (16/16): единый детерминированный тик оркестрации (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 4: Операционная правда, FlowSpine и деонтические политики (121 тест, 7 сьютов):**
       - `OperationalTruthServiceTest` (17/17): асимметричная динамика доверия и статус Белнапа (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32/32): разведение чтения и фиксации событий потока (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39): деонтическое ядро переходов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): классификация причин отказов политики (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
     * **Группа 5: Снимки доверия и рыночные исследования (18 тестов, 3 сьюта):**
       - `TrustSnapshotServiceTest` (9/9): снимки сигналов доверия с отложенным исходом (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): заслоны рыночных измерений (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D004, D007, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` [D008 False green / Popper]:**
  * Попперовский суточный цикл фальсификации (`FalsificationCycleServiceTest`), фальсификационные заслоны связности свидетельств (`EvidenceCoherenceV80TerminationFalsificationTest`), рычагов ТОС (`LeverPromotionServiceFalsificationTest`), ядра переходов (`OperationalFlowCoreFalsificationTest`), исключений деонтических политик (`OperationalPolicyDeniedExceptionFalsificationTest`) и рыночных исследований (`MarketResearchServiceFalsificationTest`). Строгая фальсифицируемость без имитации успешности.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока (`FlowMetricsServiceTest`) и позвоночника потока (`FlowSpineServiceTest`); каузальная цепь надежности для аудита операций (`OpsAuditorServiceTest`), релаксации свидетельств Тагарда (`EvidenceCoherenceServiceTest`) и снимков доверия (`TrustSnapshotServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты ядра переходов (`OperationalFlowCoreServiceTest`) и матрица деонтических политик фабрики (`OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Эмпирическая анти-зеркальная телеметрия рантайма: здоровье фабрики (`FactorySelfHealthServiceTest`), статус живого позвоночника потока (`FlowSpineServiceTest`), независимость фактических метрик базы данных от словесных нарративов агента.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности для операционной правды (`OperationalTruthServiceTest`) и продвижения рычагов ТОС (`LeverPromotionServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole ownership / Varzi & Bratman]:**
  * Мереологическая целостность сущностей фабрики и совместная деятельность в рамках единого детерминированного тика непрерывной оркестрации (`ContinuousOrchestrationServiceTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_LESLI_MAKI_01_INUS_CONDITION` [D007 Attribution error / Gilbert & Mackie]:**
  * Институциональный журнал дефектов (`DefectJournalServiceTest`), Кайдзен (`KaizenServiceTest`) и INUS-анализ первопричин (`DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Исключение категориальных ошибок: фильтрация заводского шума от клиентских задач (`PlatformSelfReferenceDetectorTest`) и сохранение идентичности восстанавливаемых работ (`PlannedWorkRecoveryServiceTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% зелёных тестов (**290/290 green в 25 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Соблюсти деонтический инвариант Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать запись секции в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (статус: ideal, замер в Docker Maven 290/290 green).
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк) и сделать коммит `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
