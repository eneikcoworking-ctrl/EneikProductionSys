# Пакетная директива L1/L3 -> L2: Кластер Секций IX, X и XI — Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента

**Дата:** 2026-10-16 06:55 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую открытую секцию/кластер механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») успешно заверена инженером (коммит `e8edcb2`, 290/290 green в Docker Maven, 0 строк изменений кода). Секция VII (TOC) закрыта ранее. Настоящая директива открывает пакетный замер следующего фундаментального кластера фабрики — **Кластера Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»)** целиком (280 тестов, 26 сьютов):
   - **Секция IX: Внешние системы — внешняя AI/RAG/Runtime-граница фабрики (110 тестов, 8 сьютов):**
     * `GoogleAiResourceService` & `GoogleAiResourceController` (18 green: `GoogleAiResourceControllerTest` 6, `GoogleAiResourceSecurityIntegrationTest` 12) — типизированная граница к вызовам Google AI, ресурсная матрица, model probe, research/interaction, fail-closed изоляция секретов и квот (D010 Goldman, D006 Raz).
     * `GeminiContextService` & `EmbeddingSimilarityUtil` & `ContextChunkRepository` (28 green: `GeminiContextServiceTest` 28) — standing-knowledge RAG, фильтрация проекций векторов по `embeddingDims`, детерминированный расчет косинусного сходства без подъема сырого контента до отбора (D010 Goldman, D014 Church).
     * `StitchClient` (10 green: `StitchClientTest` 10) — JSON-RPC контракт Stitch MCP, сохранение `screenId` при отсутствии готового HTML, изоляция проектов и токенов (D006 Raz, D003 Evans).
     * `DesignAssetService` (12 green: `DesignAssetServiceTest` 12) — оркестрация Stitch/Nano Banana, аудит дизайн-токенов, локальные draft-файлы и коммит в GitHub без деградации до картинок при обязательном HTML (D002 Barkan Marcus, D006 Raz).
     * `GeminiObserverActionService` & `InternalGeminiObserverController` (42 green: `GeminiObserverActionServiceTest` 17, `InternalGeminiObserverControllerTest` 16, `InternalGeminiObserverSecurityIntegrationTest` 9) — строго авторизованные через `OperationalPolicyService` сервисные действия с записью в `GeminiObserverActionEntity` и деонтическим запретом прямого коммита в git/код (D006 Raz, D007 Gilbert).
   - **Секция X: Дизайн-цех — дизайн-система, генерация экранов и live drift (91 тест, 11 сьютов):**
     * `DesignShopOrchestrationService` (26 green: `DesignShopOrchestrationServiceTest` 13, `DesignShopOrchestrationServiceLaw15Test` 7, `DesignShopCycleRepositoryIntegrationTest` 6) — запуск цикла дизайна строго по нарастающему фронту готовности (`isReady && !lastWasReady`), атомарный `claimStartCycle` (D002 Barkan Marcus, D004 Varzi).
     * `DesignConsistencyAuditService` & `LayoutGeometryAuditService` (28 green: `DesignConsistencyAuditServiceTest` 15, `LayoutGeometryAuditServiceTest` 13) — кросс-экранное сходство Жаккара, валидация токенов бренда и геометрии вьюпортов с честным `CANNOT_JUDGE` при нехватке токенов (D015 Belnap & Wittgenstein, D011 Goldman).
     * `DesignSystemFalsificationService` (6 green: `DesignSystemFalsificationServiceTest` 6) — фальсификация дизайн-систем эпиков через аудит-трейл dismissed-вишлиста без порождения паразитных фоновых задач (D008 Popper, D004 Shaffer).
     * `DesignDriftMonitorService` (7 green: `DesignDriftMonitorServiceTest` 7) — мониторинг дрейфа экранов строго внутри открытого окна наблюдения `ClientRuntimeObservabilityService` без порождения сторонних инстансов (D013 Wittgenstein).
     * `StrandedDesignCycleSweepService` (4 green: `StrandedDesignCycleSweepServiceTest` 4) — освобождение брошенных притязаний цикла дизайна по TTL (15 мин) через CAS с записью в журнал дефектов (D004 Varzi, D007 Gilbert).
     * `DesignImplementationLoopClosureLaw3Test` (2 green) — замыкание цикла реализации дизайна и фиксация в репозитории (D003 Evans).
     * `DesignExcellenceGate` (18 green: `DesignExcellenceGateTest` 12, `DesignExcellenceGateFalsificationTest` 6) — фальсификационный гейт качества дизайна с валидацией геометрии без галлюцинаций LLM (D008 Popper).
   - **Секция XI: Наблюдение за живым продуктом клиента — posterior cadence & product capability (79 тестов, 7 сьютов):**
     * `ClientRuntimeObservabilityService` (37 green: `ClientRuntimeObservabilityServiceTest` 29, `ObservationHostingDemarcationLaw26Test` 3, `FailureDemarcationLaw22Test` 5) — наблюдение рантайма в рамках тика оркестрации без собственного cron, четкое отделение инструментального сбоя (`instrumentFailure=true`) от дефекта продукта, bounded preview window (D013 Goldman, D012 Belnap).
     * `BetaPosterior` (9 green: `BetaPosteriorTest` 9) — чистая математическая модель апостериорного бета-распределения каданса наблюдения, отсечение повторов по commit SHA (D010 Goldman).
     * `RuntimeHealthShiftDetector` (9 green: `RuntimeHealthShiftDetectorTest` 9) — точный биномиальный детектор сдвигов здоровья во времени без подмены картами Шухарта (D013 Wittgenstein, D002 Ryle).
     * `ProductCapabilityService` (18 green: `ProductCapabilityServiceTest` 18) — зондирование возможностей продукта строго по контрактам OpenAPI (`docs/contracts/*.openapi.*`), демаркация 401/403 как барьеров инструмента без ложных дефектов продукта (D010 Goldman, D008 Popper).
     * `RuntimeVerdictLayer` (6 green: `RuntimeVerdictLayerTest` 6) — чистая проекция продуктовых вердиктов рантайма, агрегация телеметрии (D013 Wittgenstein, D012 Belnap).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код внешних шлюзов, RAG, дизайн-цеха и наблюдателя рантайма (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013, D015, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: строгий заслон качества дизайна (`DesignExcellenceGate`), фальсификация дизайн-систем (`DesignSystemFalsificationService`), отделение инструментальных отказов от дефектов продукта, исключение ложных зеленых вердиктов.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Каузальная цепочка надежности данных и лок уровней абстракции: внешний факт принимается только через валидированный способ получения (`GoogleAiResourceService`), RAG изолирует слои (raw corpus -> vector projection -> selected content в `GeminiContextService`), запрещено смешивать попытки, запуски, артефакты и возможности продукта.
- **`ELVIN_GOLDMAN_07_SELF_MODEL_SANITY` & `ELVIN_GOLDMAN_03_BELIEF_UPDATE_LEDGER` [D013 Runtime drift / Goldman]:**
  * Самосознание рантайма и журнал обновления убеждений: апостериорный каданс наблюдений на основе бета-распределения (`BetaPosterior`), детерминированное обновление вердиктов по реальным запускам.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: авторизация мутирующих запросов через `ApiAuthorizationInterceptor` и `OperationalPolicyService`, исключающие основания для запуска действий наблюдателя (`GeminiObserverActionService`).
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Boundary Topology / Varzi]:**
  * Топология границ: единая точка знания о внешних сайдкарах (`RuntimeLauncherClient`, `StitchClient`), четкая изоляция Docker-контейнеров от бэкенда.
- **`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` [D002 Invalid state / Barkan Marcus]:**
  * Суть прежде опций: старт цикла дизайна строго по нарастающему фронту (`DesignShopOrchestrationService`), атомарный захват цикла через `claimStartCycle`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `LYUDVIG_VITGENSHTEYN_02_CATEGORY_ERROR_SCAN` [D013 Runtime drift, D002 / Wittgenstein]:**
  * Анти-зеркало телеметрии рантайма: здоровье продукта верифицируется реальными замерами контейнеров, устранена категориальная ошибка подмены временных рядов рантайма картами Шухарта.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * Многозначная логика истинности в демаркации сбоев инструмента (`observed=false`) и отказов продукта (`health=DOWN`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Evidence gap / Gilbert]:**
  * Институциональная фиксация действий наблюдателя и дефектов в `GeminiObserverActionEntity` и `DefectJournalService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Кластера Секций IX, X и XI в едином непрерывном запуске Docker Maven (все 26 тест-сьютов, 280 тестов):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Кластер Секций IX, X и XI) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(external-design-runtime): complete Stage 4 batch verification (D008, D010, D006)`.
