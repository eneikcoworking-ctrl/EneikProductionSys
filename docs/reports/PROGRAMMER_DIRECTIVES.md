# Директивы и наряд для L2 (2026-10-17 12:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXXVII–XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов») полностью заверены и закрыты инженером (коммит `8d4e44ae`, 143/143 green в 15 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг макро-цикла — **Секцию VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (5 сьютов, 65 тестов):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OpsAuditorServiceTest` (11 тестов): аудит операций на независимо проверенных фактах с тройной валидацией предусловий (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7 тестов): отсечение внутризаводского шума от продуктового бэклога заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (5 сьютов, 39 тестов):**
       - `FactorySelfHealthServiceTest` (5 тестов): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10 тестов): дедупликация и жизненный цикл улучшений PDCA по `(projectId, category, targetComponent)` (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): институциональный журнал дефектов с INUS-анализом первопричин (`DZHON_MEKI_01_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (4 сьюта, 30 тестов):**
       - `FlowMetricsServiceTest` (4 теста): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста): лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (11 сьютов, 156 тестов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов): единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004);
       - `OperationalTruthServiceTest` (17 тестов), `TrustSnapshotServiceTest` (9 тестов): детерминированное состояние потока (`done_is_not_delivery`), публикация свидетельств против собственного благополучия, снимки входных сигналов доверия с отложенным дозаполнением исходов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `FlowSpineServiceTest` (32 теста): нерушимый позвоночник потока (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
       - `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов): ядро операционного потока и фальсификационные заслоны переходов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): деонтические заслоны переходов политики с типизированной причиной отказа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): детерминированный сбор рыночных измерений через каузальные задачи (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов суточного цикла фальсификации (`FalsificationCycleServiceTest`), алгоритма ECHO (`EvidenceCoherenceServiceTest`), ядра операционного потока (`OperationalFlowCoreFalsificationTest`), рынка (`MarketResearchServiceFalsificationTest`) и исключений операционной политики (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность цепи свидетельств: строгое разделение уровней закона Литтла (cycle time, WIP, throughput), сохранение каузального следа в снимках доверия (`TrustSnapshotServiceTest`) и непрерывная трассировка свидетельств.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`), институциональный запрет несанкционированных переходов состояний и типизированные причины отказа (`OperationalPolicyReasonTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status confusion / Belnap & Tarski]:**
  * Четырёхзначная логика истинности рычагов ТОС и переходов операционной политики (`passed`, `failed`, `undetermined`, `not-applied`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Телеметрия собственного здоровья фабрики (`FactorySelfHealthServiceTest`) и операционной правды потока (`done_is_not_delivery`) без скрытых паразитных мутаций.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Coupling creep / Varzi & Bratman]:**
  * Монопольное владение суточными прогонами фальсификации, восстановлением работ и единым координирующим тиком оркестрации.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Исключение категориальных ошибок: платформенный шум и внутренние сбои фабрики не переносятся в продуктовый бэклог заказчика.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Institutional defect record / Gilbert & Mackie]:**
  * Институциональный журнал дефектов и строгий INUS-анализ первопричин для предотвращения рецидивов.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=FalsificationCycleServiceTest,\
EvidenceCoherenceServiceTest,\
EvidenceCoherenceV80TerminationFalsificationTest,\
OpsAuditorServiceTest,\
PlatformSelfReferenceDetectorTest,\
FactorySelfHealthServiceTest,\
KaizenServiceTest,\
DefectJournalServiceTest,\
DefectJournalRootCauseAttributionTest,\
LogScopeAndDefectJournalInusFalsificationTest,\
FlowMetricsServiceTest,\
LeverPromotionServiceTest,\
LeverPromotionServiceFalsificationTest,\
PlannedWorkRecoveryServiceTest,\
ContinuousOrchestrationServiceTest,\
OperationalTruthServiceTest,\
TrustSnapshotServiceTest,\
FlowSpineServiceTest,\
OperationalFlowCoreServiceTest,\
OperationalFlowCoreFalsificationTest,\
OperationalPolicyServiceTest,\
OperationalPolicyReasonTest,\
OperationalPolicyDeniedExceptionFalsificationTest,\
MarketResearchServiceTest,\
MarketResearchServiceFalsificationTest
```

---

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 25 тест-сьютов Секции VIII (290 тестов) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**290/290 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Секции VIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом:
   ```bash
   git commit -m "feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
