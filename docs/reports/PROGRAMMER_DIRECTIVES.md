# Директивы и наряд для L2 (2026-10-17 09:36 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества Six Sigma и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов») полностью заверен и закрыт инженером (коммит `ce01f6aa`, 251/251 green в 27 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификационный цикл, связность и аудит (70 тестов, 5 сьютов):**
       - `FalsificationCycleServiceTest` (28), `EvidenceCoherenceServiceTest` (21), `EvidenceCoherenceV80TerminationFalsificationTest` (5), `OpsAuditorServiceTest` (11), `PlatformSelfReferenceDetectorTest` (7): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings`, расчет объяснительной связности ECHO Тагарда без ложной уверенности, доказательный аудит операций на независимо собранных фактах, отсечение внутризаводского шума от требований заказчика (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2: Здоровье фабрики, Кайдзен и журнал дефектов (39 тестов, 5 сьютов):**
       - `FactorySelfHealthServiceTest` (5), `KaizenServiceTest` (10), `DefectJournalServiceTest` (3), `DefectJournalRootCauseAttributionTest` (6), `LogScopeAndDefectJournalInusFalsificationTest` (5): непрерывный мониторинг собственного здоровья фабрики (размер БД, пулы соединений, емкость очередей), дедупликация и жизненный цикл микро-улучшений PDCA по `(projectId, category, targetComponent)`, институциональная фиксация дефектов и INUS-анализ первопричин (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `BELIEF_UPDATE_LEDGER` / D007, `INUS_FACTOR_CHECK` / D007);
     * **Группа 3: Метрики потока, продвижение рычагов и восстановление работ (30 тестов, 4 сьюта):**
       - `FlowMetricsServiceTest` (4), `LeverPromotionServiceTest` (7), `LeverPromotionServiceFalsificationTest` (4), `PlannedWorkRecoveryServiceTest` (15): закон Литтла и арифметика потока (WIP, throughput, cycle time), продвижение рычагов от `observe_only` к действию только по накопленным подтверждениям, монопольное владение восстановлением зависших работ без паразитных перезапусков (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 4: Операционное ядро, политика, правда потока и рыночный поиск (151 тест, 11 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16), `OperationalTruthServiceTest` (17), `TrustSnapshotServiceTest` (9), `FlowSpineServiceTest` (32), `OperationalFlowCoreServiceTest` (6), `OperationalFlowCoreFalsificationTest` (39), `OperationalPolicyServiceTest` (17), `OperationalPolicyReasonTest` (5), `OperationalPolicyDeniedExceptionFalsificationTest` (9), `MarketResearchServiceTest` (3), `MarketResearchServiceFalsificationTest` (6): координация тиков непрерывной оркестрации, операционная правда потока, чистая наблюдаемость FlowSpine без побочных эффектов записи, исполнимая матрица прав и обязанностей операционной политики, постановка рыночных исследований через штатные задачи оркестратора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D002, D007, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка суточного цикла фальсификации (`FalsificationCycleServiceTest`), отсечки связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), рычагов (`LeverPromotionServiceFalsificationTest`), ядра потока (`OperationalFlowCoreFalsificationTest`) и рыночных задач (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик потока (закон Литтла, WIP/Throughput/Lead time в `FlowMetricsServiceTest`), надежная каузальная цепь свидетельств связности (`EvidenceCoherenceServiceTest`) и динамические снимки доверия (`TrustSnapshotServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D006 Deontic prohibition as code & D004 Part-whole ownership / Raz, Varzi, Shaffer]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`), монопольное владение восстановлением зависших задач (`PlannedWorkRecoveryServiceTest`) и прогонами фальсификации (`FalsificationCycleServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистая наблюдаемость FlowSpine без побочных эффектов мутации (`FlowSpineServiceTest`), телеметрия собственного здоровья фабрики (`FactorySelfHealthServiceTest`), эмпирический факт о рантайме выше любого словесного нарратива (`done_is_not_delivery`).
- **`GILBERT_RYLE_01_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Строгое отсечение внутризаводского шума и фабричных находок от продуктового бэклога заказчика (`PlatformSelfReferenceDetectorTest`).
- **`DZHON_MEKI_01_INUS_CONDITION` & `MARGRET_GILBERT_01_SHARED_COGNITIVE_FRAME` [D007 Institutional defect record / Mackie & Gilbert]:**
  * Институциональный реестр фактов дефектов и объективный INUS-анализ первопричин сбоев в журнале Кайдзен (`KaizenServiceTest`, `DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`).
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Tarski & Belnap]:**
  * 4-значная решётка истинности операционной политики и статусов продвижения рычагов.

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

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 25 тест-сьютов Секции VIII (290 тестов) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**290/290 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Секции VIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
