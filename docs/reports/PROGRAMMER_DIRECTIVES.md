# Директивы и наряд для L2 (2026-10-16 10:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXII и XXIII («Миграции базы данных и живой замер») успешно заверены инженером (коммиты `938221f` / `25d2a20`, 125/125 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином пакетном прогоне в Docker Maven):
     * **Самопроверка, суточный цикл фальсификации и здоровье фабрики (38 тестов, 3 сьюта):**
       - `FalsificationCycleServiceTest` (28/28) — суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (D008 Popper, D004 Varzi).
       - `PlatformSelfReferenceDetectorTest` (7/7) — отсечение внутризаводского шума от клиентского продуктового бэклога (D002 Ryle).
       - `FactorySelfHealthServiceTest` (5/5) — непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (D013 Wittgenstein).
     * **Связность свидетельств (ECHO) (26 тестов, 2 сьюта):**
       - `EvidenceCoherenceServiceTest` (21/21) и `EvidenceCoherenceV80TerminationFalsificationTest` (5/5) — релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (D010 Goldman, D011 Dretske).
     * **Аудит операций и восстановление задач (26 тестов, 2 сьюта):**
       - `OpsAuditorServiceTest` (11/11) — аудит операций исключительно на независимо проверенных фактах с тройной валидацией предусловий (D006 Raz, D006 Goldman).
       - `PlannedWorkRecoveryServiceTest` (15/15) — восстановление упавших задач со строгим сохранением тождества задачи и запретом паразитного размножения (D004 Varzi, D002 Ryle).
     * **Кайдзен и журнал дефектов (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5) — дедупликация предложений PDCA по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (D007 Gilbert, D007 Mackie/INUS).
     * **Метрики потока и продвижение рычагов (15 тестов, 3 сьюта):**
       - `FlowMetricsServiceTest` (4/4) — расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (D008 Church, D010 Goldman).
       - `LeverPromotionServiceTest` (7/7) и `LeverPromotionServiceFalsificationTest` (4/4) — лестница продвижения рычагов от `observe_only` к активным действиям на основе согласованных наблюдений (D012 Belnap, D010 Goldman).
     * **Оркестрация и операционная политика потока (141 тест, 8 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16/16) — единый детерминированный тик оркестрации фабрики без сторонних фоновых кронов (D004 Bratman, D006 Raz).
       - `OperationalTruthServiceTest` (17/17) — 4-значная логика истинности потока, динамика доверия от неустановленного (D012 Belnap, D010 Goldman).
       - `FlowSpineServiceTest` (32/32) — детерминированный позвоночник потока, честное отражение метрик рантайма (`done_is_not_delivery`), хеширование свидетельств SHA-256 (D010 Goldman, D013 Wittgenstein).
       - `OperationalFlowCoreServiceTest` (6/6) и `OperationalFlowCoreFalsificationTest` (39/39) — деонтические заслоны переходов политики, матрица прав и обязанностей Flow Core (D006 Raz).
       - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9) — деонтические отказы с проверяемой причиной (D006 Raz).
     * **Снимки доверия и рыночные исследования (20 тестов, 3 сьюта):**
       - `TrustSnapshotServiceTest` (9/9) — снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (D010 Goldman).
       - `MarketResearchServiceTest` (3/3) и `MarketResearchServiceFalsificationTest` (6/6) — детерминированный сбор внешних рыночных измерений через каузальные задачи без мутации клиентского кода (D010 Goldman, D008 Popper).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты-заслоны обязаны воспроизводимо падать при нарушении инвариантов суточного цикла фальсификации (`FalsificationCycleServiceTest`), сбоях рыночных исследований (`MarketResearchServiceFalsificationTest`) и расхождении закона Литтла (`FlowMetricsServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение происхождения данных в снимках доверия (`TrustSnapshotService`), релаксация графа свидетельств Тагарда (`EvidenceCoherenceService`) и SHA-256 хеширование событий потока (`FlowSpineService`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Полная матрица прав и обязанностей Flow Core (`OperationalFlowCoreService`): деонтический запрет мутаций неактивных проектов (`ENFORCED_PROJECT_NOT_MUTABLE`) и остановка конвейера при блокерах (`ENFORCED_STOP_THE_LINE`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: телеметрия `FlowSpineService` публикует честное свидетельство против собственного благополучия (`done_is_not_delivery`), а `FactorySelfHealthService` отслеживает физические ресурсы сервера и БД.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Neutral fallback / Belnap]:**
  * Четырехзначная логика истинности в `OperationalTruthService` и `LeverPromotionService`: доверие начинается строго с неустановленного (`0.0` / `UNDETERMINED`) и прирастает только по фактам.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Varzi]:**
  * Монопольное владение агрегатами: `FalsificationCycleService` единолично владеет таблицами `falsification_runs` и `code_integrity_findings`; планировщик `PlannedWorkRecoveryService` не плодит паразитных сущностей.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение категориальных ошибок: детектор `PlatformSelfReferenceDetector` исключает попадание заводского шума в продуктовый бэклог клиента.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Gilbert]:**
  * Фиксация дефектов и предложений улучшений в `DefectJournalService` и `KaizenService` по `(projectId, category, targetComponent)`.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер 25 тест-сьютов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить 290/290 green (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
