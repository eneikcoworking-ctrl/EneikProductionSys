# Директивы и наряд для L2 (2026-10-17 10:48 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV («Сторож ТОС», «Вход к ресурсам модели», «Метрики качества: два счёта одного слова», «Выметающий обход: восстановление», «Заслон качества экрана: проверка предмета», «Кэш постоянного корпуса», «Верёвка: ограничитель выпуска», «Очистка журнала проекта») полностью заверен и закрыт инженером (коммит `4e2e15c9`, 143/143 green в 15 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (65 тестов, 5 сьютов):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл фальсификации и монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11 тестов): аудит операций исключительно на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7 тестов): отсечение внутризаводского шума от продуктового бэклога заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (39 тестов, 5 сьютов):**
       - `FactorySelfHealthServiceTest` (5 тестов): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10 тестов): дедупликация и жизненный цикл улучшений PDCA по `(projectId, category, targetComponent)` (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): фиксация в журнале дефектов, INUS-анализ первопричин сбоев и изоляция скоупов логов (`DZHON_MEKI_01_INUS_CONDITION` / D007, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (30 тестов, 4 сьюта):**
       - `FlowMetricsServiceTest` (4 теста): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста): лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (156 тестов, 11 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов): единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalTruthServiceTest` (17 тестов), `TrustSnapshotServiceTest` (9 тестов): снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения без деградации кучи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `FlowSpineServiceTest` (32 теста): детерминированное состояние потока, инварианты `done_is_not_delivery`, публикация свидетельств против собственного благополучия (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов): ядро операционного потока, фальсификация пограничных условий переходов состояний (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): деонтические заслоны переходов политики, институциональная матрица прав и обязанностей, детерминированный отказ с объяснимой причиной (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): детерминированный сбор внешних рыночных измерений через каузальные задачи без мутации клиентского кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов суточного цикла (`FalsificationCycleServiceTest`), ядра операционного потока (`OperationalFlowCoreFalsificationTest`), рычагов ТОС (`LeverPromotionServiceFalsificationTest`) и рыночного соответствия (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D010 Data lineage loss / Goldman & Church]:**
  * Лок уровней абстракции и цепочка надежности: строгое разделение уровней метрик потока (Закон Литтла), каузальный след свидетельств V80, снимки доверия операционной правды без деградации кучи.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические барьеры операционной политики, топологическая защита границ и институциональная матрица прав и обязанностей.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * 4-значная решётка истинности исходов суждений и продвижения рычагов ТОС (`passed`, `failed`, `undetermined`, `not-applied`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Телеметрия здоровья фабрики и операционной правды без скрытых побочных мутаций (`done_is_not_delivery`).
- **`DZHON_MEKI_01_INUS_CONDITION` & `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Inadequate defect record / Mackie & Gilbert]:**
  * Долговечный журнал дефектов, институциональные записи и объективный INUS-анализ первопричин сбоев.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Concurrency conflict / Varzi & Bratman]:**
  * Монопольное владение `falsification_runs` и `code_integrity_findings`, атомарность восстановления работ и единый координационный такт оркестрации.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -m 2g -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test \
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
5. Зафиксировать результат коммитом: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
