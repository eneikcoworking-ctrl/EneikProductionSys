# Директивы и наряд для L2 (2026-10-17 15:28 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXXVII–XLIV («Сторож ТОС, вход к ресурсам модели, метрики качества Six Sigma, выметающий обход, заслон качества экрана, постоянный кэш корпуса, верёвка ТОС и очистка журнала проекта») полностью подтверждён и закрыт инженером (коммит `ec4cc206`, 206/206 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:55 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (5 сьютов, 72 теста):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл фальсификации, обнаружение ложно-зеленых и дрейфа допущений (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `EvidenceCoherenceServiceTest` (21 тест): граф связности свидетельств ECHO, отсечение N+1 запросов через счетчики (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): терминальные состояния связности и фальсификационные контрпримеры (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `OpsAuditorServiceTest` (11 тестов): аудит операционных инцидентов и инвариантов потока (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `PlatformSelfReferenceDetectorTest` (7 тестов): демаркация внутренних системных сбоев платформы от дефектов продукта (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (5 сьютов, 29 тестов):**
       - `FactorySelfHealthServiceTest` (5 тестов): мониторинг здоровья компонентов фабрики без паразитных мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `KaizenServiceTest` (10 тестов): непрерывное совершенствование и управление инициативами Кайдзен (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DefectJournalServiceTest` (3 теста): неподкупная фиксация институциональных дефектов (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `DefectJournalRootCauseAttributionTest` (6 тестов): строгое атрибутирование первопричин дефектов (`DZHON_MEKI_01_INUS_CONDITION` / D007);
       - `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): фальсификационные заслоны на область логирования и INUS-анализ дефектов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (4 сьюта, 30 тестов):**
       - `FlowMetricsServiceTest` (4 теста): математический расчет пропускной способности и времени цикла по закону Литтла (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7 тестов): 4-значная решётка истинности переходов рычагов ТОС (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `LeverPromotionServiceFalsificationTest` (4 теста): фальсификационная оснастка субординации рычагов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): монопольное восстановление плановых работ и защита от дублирования (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (11 сьютов, 159 тестов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов): ядро непрерывной оркестрации фабрики (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия строго по свидетельствам с базы 0.0 (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `TrustSnapshotServiceTest` (9 тестов): снимки доверия без паразитной мутации рантайма (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `FlowSpineServiceTest` (32 теста): позвоночник потока, различие `done_is_not_delivery` и подтверждение обхода заказчиком (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6 тестов): транзитивное выполнение стадий операционного ядра (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalFlowCoreFalsificationTest` (39 тестов): строгие фальсификационные заслоны инвариантов ядра потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17 тестов): деонтическая матрица прав и обязанностей операционной политики (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `OperationalPolicyReasonTest` (5 тестов): типизированные причины отказов политики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): фальсификационные заслоны исключений запрета политики (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `MarketResearchServiceTest` (3 теста): полевые исследования рыночного корпуса требований (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `MarketResearchServiceFalsificationTest` (6 тестов): фальсификационный заслон валидации рыночных гипотез (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка суточного цикла (`FalsificationCycleServiceTest`), терминальных состояний связности (`EvidenceCoherenceV80TerminationFalsificationTest`), инвариантов ядра потока (`OperationalFlowCoreFalsificationTest`), исключений политики (`OperationalPolicyDeniedExceptionFalsificationTest`) и рыночных гипотез (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции закона Литтла (`FlowMetricsServiceTest`), каузальная цепь надежности свидетельств (`OperationalFlowCoreServiceTest`, `EvidenceCoherenceServiceTest`), асимметричная динамика доверия строго с базы 0.0 (`OperationalTruthServiceTest`) и изоляция рыночных требований (`MarketResearchServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`) и типизированные отказы (`OperationalPolicyReasonTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Concurrency conflict / Varzi & Bratman]:**
  * Монопольное владение восстановлением плановых работ (`PlannedWorkRecoveryServiceTest`) и разделяемая кооперативная координация тиков оркестрации (`ContinuousOrchestrationServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности переходов рычагов ТОС (`LeverPromotionServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия здоровья компонентов (`FactorySelfHealthServiceTest`), снимки доверия без паразитной мутации (`TrustSnapshotServiceTest`) и фундаментальный инвариант `done_is_not_delivery` с подтверждением обхода заказчиком (`FlowSpineServiceTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Демаркация категориальных ошибок: системные сбои платформы не атрибутируются как дефекты клиентского продукта (`PlatformSelfReferenceDetectorTest`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Evidence gap / Gilbert & Mackie]:**
  * Институциональный реестр дефектов (`DefectJournalServiceTest`), аудит операционных инцидентов (`OpsAuditorServiceTest`) и строгий INUS-анализ первопричин (`DefectJournalRootCauseAttributionTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Dretske]:**
  * Телеосемантическая обратная связь графа ECHO с защитой от N+1 через счетчики (`EvidenceCoherenceServiceTest`).

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
