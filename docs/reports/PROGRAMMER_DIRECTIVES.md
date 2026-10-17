# Директивы и наряд для L2 (2026-10-17 14:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы: ML-клиент, онбординг, завод репозиториев, пульт готовности, наблюдатель, пул аккаунтов, хранилище задач») полностью подтверждён и закрыт инженером (коммит `15493a06`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:08 min, строго 0 строк изменений рабочего кода).
   - Секция VII (TOC) полностью замерена и закрыта.
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Секцию VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (5 сьютов, 65 тестов):**
       - `FalsificationCycleServiceTest` (28 тестов), `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов), `OpsAuditorServiceTest` (11 тестов), `PlatformSelfReferenceDetectorTest` (7 тестов): суточный цикл фальсификации и монопольное владение `falsification_runs` и `code_integrity_findings`, релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных, аудит операций на независимо проверенных фактах с тройной валидацией предусловий, отсечение внутризаводского шума от продуктового бэклога заказчика (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (5 сьютов, 39 тестов):**
       - `FactorySelfHealthServiceTest` (5 тестов), `KaizenServiceTest` (10 тестов), `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен, дедупликация и жизненный цикл улучшений PDCA по `(projectId, category, targetComponent)` с INUS-анализом первопричин (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (4 сьюта, 30 тестов):**
       - `FlowMetricsServiceTest` (4 теста), `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста), `PlannedWorkRecoveryServiceTest` (15 тестов): расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях, лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений, атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (11 сьютов, 156 тестов):**
       - `ContinuousOrchestrationServiceTest` (16 тестов), `OperationalTruthServiceTest` (17 тестов), `TrustSnapshotServiceTest` (9 тестов), `FlowSpineServiceTest` (32 теста), `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов), `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов), `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов, снимки входных сигналов доверия с отложенным дозаполнением исходов, детерминированное состояние потока (`done_is_not_delivery`), публикация свидетельств против собственного благополучия, ядро операционного потока, деонтические заслоны переходов политики с типизированной причиной отказа, детерминированный сбор рыночных измерений через каузальные задачи (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004, D002, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка суточного цикла фальсификации (`FalsificationCycleServiceTest`), фальсификационные заслоны операционного ядра (`OperationalFlowCoreFalsificationTest`), рыночных исследований (`MarketResearchServiceFalsificationTest`), исключений операционной политики (`OperationalPolicyDeniedExceptionFalsificationTest`), связности свидетельств ECHO (`EvidenceCoherenceV80TerminationFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D010 Data lineage loss / Goldman & Church]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение уровней операций, задач и свидетельств, асимметричный рост доверия строго по свидетельствам (`OperationalTruthService`, `TrustSnapshotService`), строгий расчет метрик потока по закону Литтла (`FlowMetricsService`), лестница продвижения рычагов ТОС (`LeverPromotionService`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон на переходы операционной политики с обязательной типизированной фиксацией причины отказа (`OperationalPolicyService`, `OperationalPolicyReason`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth table / Belnap]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для операционной правды и свидетельств готовности, исключающая догадки и фиктивные состояния.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистый мониторинг здоровья фабрики (`FactorySelfHealthService`), операционная правда без паразитных проекций и самообмана.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Part-whole confusion / Varzi & Bratman]:**
  * Топология частей и целого: ядро непрерывной совместной активности фабрики (`ContinuousOrchestrationService`) и сохранение целостности восстанавливаемых задач (`PlannedWorkRecoveryService`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Категориальная демаркация: исключение смешения внутрифабричных платформенных сбоев с продуктовыми результатами клиента.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Institutional fact / Gilbert & Mackie]:**
  * Неподкупная институциональная память отказов фабрики и INUS-анализ первопричин дефектов в Кайдзен.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Representation drift / Dretske]:**
  * Телеосемантическая обратная связь в алгоритме связности свидетельств и восстановлении работ.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
