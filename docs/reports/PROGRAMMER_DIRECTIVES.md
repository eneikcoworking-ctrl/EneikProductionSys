# Директивы и наряд для L2 (2026-10-17 09:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов») полностью заверен и закрыт инженером (коммит `78aad400`, 251/251 green в 27 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Суточный цикл фальсификации и целостность кода (28 тестов, 1 сьют):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл фальсификации кода и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2: Связность свидетельств и релаксация графа (26 тестов, 2 сьюта):**
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Группа 3: Аудитор операций, детектор шума и самочувствие фабрики (23 теста, 3 сьюта):**
       - `OpsAuditorServiceTest` (11/11 green): аудит на независимо проверенных фактах с тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): отсечение внутризаводского шума от клиентского бэклога (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5 green): мониторинг собственного здоровья фабрики с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 4: Кайдзен, журнал дефектов и INUS-атрибуция корней (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10 green), `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): дедупликация предложений PDCA по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов и причинной изоляцией INUS (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007);
     * **Группа 5: Метрики потока и продвижение рычагов (15 тестов, 3 сьюта):**
       - `FlowMetricsServiceTest` (4/4 green): закон Литтла, WIP, throughput и cycle time с детекцией расхождений (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): продвижение рычагов от `observe_only` по согласованным наблюдениям (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 6: Восстановление упавших задач и единый тик оркестрации (31 тест, 2 сьюта):**
       - `PlannedWorkRecoveryServiceTest` (15/15 green): сохранение личностной идентичности задачи при сбоях и запрет паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `ContinuousOrchestrationServiceTest` (16/16 green): единый детерминированный тик оркестрации фабрики без сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 7: Операционная правда, хребет потока, Flow Core и деонтическая политика (125 тестов, 7 сьютов):**
       - `OperationalTruthServiceTest` (17/17 green), `FlowSpineServiceTest` (32/32 green): честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткая матрица состояний и демаркация переходов ядра потока (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): деонтический барьер переходов политики с объяснимым основанием отказа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 8: Снимки сигналов доверия и рыночные измерения (18 тестов, 3 сьюта):**
       - `TrustSnapshotServiceTest` (9/9 green): фиксация входных сигналов доверия с отложенным дозаполнением исходов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): сбор внешних рыночных измерений через каузальные задачи без прямой мутации рабочего кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D007, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка суточного цикла фальсификации (`FalsificationCycleServiceTest`), фальсификация ядра потока (`OperationalFlowCoreFalsificationTest`), рыночных измерений (`MarketResearchServiceFalsificationTest`) и отказов политики (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение каузальной цепи свидетельств от входа до фиксации снимков доверия (`TrustSnapshotServiceTest`), проверка закона Литтла (`FlowMetricsServiceTest`), независимый аудит фактов (`OpsAuditorServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Институциональный барьер переходов политики (`OperationalPolicyServiceTest`), матрица прав и обязанностей ядра потока (`OperationalFlowCoreServiceTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D002 Category error / Ryle & D004 Varzi]:**
  * Демаркация категорий: заводской шум фабрики не становится фактом о продукте клиента (`PlatformSelfReferenceDetectorTest`); сохранение личной идентичности задачи при восстановлении без дублирования (`PlannedWorkRecoveryServiceTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Incomplete truth / Gilbert & Mackie]:**
  * Институциональная фиксация дефектов и выявление достаточных нелишних условий причинности (INUS) в журнале дефектов и Кайдзен (`KaizenServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth value collapse / Belnap & Tarski]:**
  * Четырехзначная логика истинности операционной правды и ступеней рычагов: дефект или отсутствие измерения не подменяются ложным «ок».
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D013 / D011 Wittgenstein & Dretske]:**
  * Анти-зеркальная честность рантайма (`done_is_not_delivery`), публикация свидетельств против собственного благополучия (`FlowSpineServiceTest`), телеосемантическая релаксация графа (`EvidenceCoherenceServiceTest`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test \
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
FlowSpineServiceTest,\
OperationalFlowCoreServiceTest,\
OperationalFlowCoreFalsificationTest,\
OperationalPolicyServiceTest,\
OperationalPolicyReasonTest,\
OperationalPolicyDeniedExceptionFalsificationTest,\
TrustSnapshotServiceTest,\
MarketResearchServiceTest,\
MarketResearchServiceFalsificationTest
```

**Ожидаемый результат:** 25 сьютов, 290 тестов, 100% green, BUILD SUCCESS, строго 0 строк модификации рабочего кода фабрики.
После завершения: обновить `docs/reports/PROGRAMMER_STATE.md`, закоммитить в git и взвести такт ожидания.
