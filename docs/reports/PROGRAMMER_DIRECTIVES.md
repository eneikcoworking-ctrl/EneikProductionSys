# Директивы и наряд для L2 (2026-10-23 11:22 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV полностью заверен и закрыт инженером (коммит `35e9768d`, 206/206 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:40 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Секцию VIII («VIII. Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самодиагностика фабрики (6 сьютов, 77 тестов):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл по дефектам кода и недельный философский аудит, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): связность свидетельств ECHO Тагарда, релаксация графа свидетельств, отсечка холостых прогонов V80 без N+1 запросов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest` (11 тестов): операционный аудит на независимо проверенных фактах с тройной верификацией предусловий, изоляция `LogScope` (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7 тестов): отсечение фабричного шума от клиентского бэклога (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5 тестов): непрерывный мониторинг здоровья узлов фабрики без подмены заглушками (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов и анализ первопричин (4 сьюта, 24 теста):**
       - `KaizenServiceTest` (10 тестов): обработка сигналов отклонений, стандартизация улучшений (`MARGARET_GILBERT_01_PLURAL_SUBJECT_AUDIT` / D007);
       - `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): институциональный учет отказов, INUS-анализ первопричин и неподкупность журнала дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007).
     * **Группа 3: Метрики потока, рычаги ТОС, восстановление работ и оркестрация (5 сьютов, 46 тестов):**
       - `FlowMetricsServiceTest` (4 теста): закон Литтла (`WIP ≈ Throughput × CycleTime`), фальсификация искажений времени цикла (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста): лестница продвижения рычагов ТОС по согласованным наблюдениям (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): сохранение идентичности восстанавливаемых задач без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `ContinuousOrchestrationServiceTest` (16 тестов): центральный детерминированный тик непрерывной оркестрации фабрики (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004).
     * **Группа 4: Операционное ядро, операционная политика, доверие, правда потока и рынок (10 сьютов, 143 теста):**
       - `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов): деонтическое ядро и строгие фальсифицирующие заслоны переходов состояний потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): институциональная матрица прав и классификация отказов операционной политики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TrustSnapshotServiceTest` (9 тестов): периодические снимки доверия с отложенным исходом и динамическим накоплением свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalTruthServiceTest` (17 тестов): операционная правда, вычисление блокеров и доверия строго по фактам (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `FlowSpineServiceTest` (32 теста): телеметрия истинного состояния потока без паразитных мутаций при чтении, разведение чтения и фиксации (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): полевой аудит рыночного контекста и забор внешних измерений (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны суточного цикла (`FalsificationCycleServiceTest`), переходов состояний потока (`OperationalFlowCoreFalsificationTest`), рынка (`MarketResearchServiceFalsificationTest`) и закона Литтла (`FlowMetricsServiceTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции метрик потока Литтла; релайабилистская цепь свидетельств Тагарда; асимметричный рост операционного доверия строго по эмпирическим свидетельствам.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Varzi & Bratman]:**
  * Монопольное владение запуском суточных прогонов, восстановлением работ и координация тиков непрерывной оркестрации.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap]:**
  * 4-значная решётка истинности рычагов ТОС и операционной правды без скрытых бинарных упрощений.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия здоровья и истинности потока без паразитных побочных эффектов записи при чтении.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Демаркация внутризаводского шума от продуктового бэклога заказчика.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Gilbert & Mackie]:**
  * Журнал дефектов как неподкупный институциональный реестр и INUS-анализ первопричин инцидентов.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Dretske]:**
  * Телеосемантическая обратная связь при восстановлении работ и релаксации графа свидетельств.

---

## 3. Критерии приемки наряда L2

1. Провести единый непрерывный пакетный прогон всех 25 тест-сьютов Секции VIII в Docker Maven 3.9.9 Temurin-21 (-m 2g):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 100% результат: **290/290 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
3. Соблюсти деонтический запрет: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. Сделать семантический коммит в фабрику: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
7. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
