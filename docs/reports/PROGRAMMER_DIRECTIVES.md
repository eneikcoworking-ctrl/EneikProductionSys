# Директивы и наряд для L2 (2026-10-17 10:11 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущий стратегический Кластер Секций XXXVII–XLIV полностью заверен и закрыт инженером (коммит `24fe17e3`, 143/143 green в 15 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Фальсификационный цикл, связность свидетельств и аудит (70 тестов, 5 сьютов):**
       - `FalsificationCycleServiceTest` (28): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21), `EvidenceCoherenceV80TerminationFalsificationTest` (5): связность цепочки свидетельств V80, терминация дрейфа (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OpsAuditorServiceTest` (11): независимый аудит операционной деятельности (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `PlatformSelfReferenceDetectorTest` (7): обнаружение паразитных самореференций (`BERTRAN_RASSEL_01_VICIOUS_CIRCLE_PRINCIPLE` / D002);
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (39 тестов, 5 сьютов):**
       - `FactorySelfHealthServiceTest` (5): мониторинг витальных метрик фабрики (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10): институциональные предложения непрерывного улучшения (`MARGARET_GILBERT_01_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `DefectJournalServiceTest` (3), `DefectJournalRootCauseAttributionTest` (6), `LogScopeAndDefectJournalInusFalsificationTest` (5): INUS-анализ первопричин дефектов, изоляция контекстных скоупов (`DZHON_LESLI_MEKI_01_INUS_CONDITION_ANALYZER` / D007, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (30 тестов, 4 сьюта):**
       - `FlowMetricsServiceTest` (4): закон Литтла, метрики времени цикла потока (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7), `LeverPromotionServiceFalsificationTest` (4): субординация рычагов ТОС, 4-значная решётка истинности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `PlannedWorkRecoveryServiceTest` (15): атомарное восстановление брошенных работ без изменения истинности (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `MAYKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004);
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (151 тест, 11 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16): единый координирующий такт оркестрации потока (`MAYKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004);
       - `OperationalTruthServiceTest` (17), `TrustSnapshotServiceTest` (9): снимки доверия операционной правды, защита от монотонного угасания (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `FlowSpineServiceTest` (32): инварианты позвоночника потока (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
       - `OperationalFlowCoreServiceTest` (6), `OperationalFlowCoreFalsificationTest` (39): ядро операционного потока, фальсификация пограничных условий (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `OperationalPolicyServiceTest` (17), `OperationalPolicyReasonTest` (5), `OperationalPolicyDeniedExceptionFalsificationTest` (9): права и обязанности, причины отказов политики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `MarketResearchServiceTest` (3), `MarketResearchServiceFalsificationTest` (6): верификация соответствия рыночному спросу (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
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
  * Лок уровней абстракции и цепочка надежности: разделение уровней метрик потока (Закон Литтла), каузальный след свидетельств V80, снимки доверия операционной правды без деградации кучи.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические барьеры операционной политики, топологическая защита границ и институциональная матрица прав и обязанностей.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * 4-значная решётка истинности исходов суждений и продвижения рычагов ТОС (passed, failed, undetermined, not-applied).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Телеметрия здоровья фабрики и операционной правды без скрытых побочных мутаций (`done_is_not_delivery`).
- **`DZHON_LESLI_MEKI_01_INUS_CONDITION_ANALYZER` & `MARGARET_GILBERT_01_INSTITUTIONAL_DEFECT_RECORD` [D007 Inadequate defect record / Mackie & Gilbert]:**
  * Долговечный журнал дефектов, институциональные записи и объективный INUS-анализ первопричин сбоев.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAYKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Concurrency conflict / Varzi & Bratman]:**
  * Монопольное владение `falsification_runs` и `code_integrity_findings`, атомарность восстановления работ и единый координационный такт оркестрации.

---

## 3. Протокол исполнения для L2

1. Провести единый непрерывный пакетный прогон 25 тест-сьютов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Убедиться в достижении целевого результата: **290/290 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Проверить инвариант: **строго 0 строк изменений рабочего кода фабрики**.
4. Актуализировать строки `*Живое:*` и итоговые блоки Секции VIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сделать семантический коммит:
   ```bash
   git commit -m "feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
