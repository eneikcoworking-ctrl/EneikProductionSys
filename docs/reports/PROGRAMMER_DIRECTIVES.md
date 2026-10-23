# Директивы и наряд для L2 (2026-10-23 11:32 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена и закрыта инженером (коммит `dfe6b9f4`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:04 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»)** целиком (**280 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция IX — Внешняя AI/RAG/Runtime-граница фабрики (8 сьютов, 110 тестов):**
       - `GoogleAiResourceSecurityIntegrationTest` (12 тестов), `GoogleAiResourceControllerTest` (6 тестов): деонтические барьеры доступа (401/403) на мутирующие запросы ресурсов модели, типобезопасная матрица ресурсов Google AI (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `GeminiContextServiceTest` (28 тестов): RAG standing-knowledge по векторным проекциям без загрузки полного корпуса в память (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `StitchClientTest` (10 тестов): JSON-RPC клиент к Stitch MCP, сохранение screenId при неготовом HTML, проектная изоляция (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `DesignAssetServiceTest` (12 тестов): оркестрация Stitch/Nano Banana, аудит дизайн-токенов, запрет подмены HTML на изображения при обязательном HTML (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009, `RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` / D015);
       - `GeminiObserverActionServiceTest` (17 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов): авторизация некодовых операционных действий наблюдателя строго через `OperationalPolicyService` с фиксацией в `GeminiObserverActionEntity` и деонтическим запретом прямого изменения кода/коммитов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
     * **Группа 2: Секция X — Дизайн-цех, порождение экранов, токенизация бренда, геометрия и live drift (11 сьютов, 91 тест):**
       - `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов), `DesignShopCycleRepositoryIntegrationTest` (6 тестов): оркестрация дизайн-цеха по активным проектам, Закон 15, атомарный CAS-старт цикла (`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` / D002, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignConsistencyAuditServiceTest` (15 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): кросс-экранное сходство Жаккара, аудит геометрии и Gestalt-близости вьюпортов (`LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` / D015, `ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE` / D011);
       - `DesignSystemFalsificationServiceTest` (6 тестов), `DesignDriftMonitorServiceTest` (7 тестов): попперовская фальсификация деклараций дизайн-системы и анти-зеркальный мониторинг live drift экранов (`TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `StrandedDesignCycleSweepServiceTest` (4 теста), `DesignImplementationLoopClosureLaw3Test` (2 теста): выметающий обход брошенных циклов и телеосемантическое замыкание цикла имплементации по Закону 3 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): строгий фальсификационный гейт качества дизайна с проверкой физических скриншотов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` / D015).
     * **Группа 3: Секция XI — Наблюдение за живым продуктом клиента, апостериорный каданс и доказательства возможностей (7 сьютов, 79 тестов):**
       - `ClientRuntimeObservabilityServiceTest` (29 тестов), `ObservationHostingDemarcationLaw26Test` (3 теста), `FailureDemarcationLaw22Test` (5 тестов): анти-зеркальное наблюдение в рамках тика оркестрации, демаркация сбоя инструмента (`observed=false`) от сбоя продукта (`answered=false`) по Закону 22 и изоляция хостинга по Закону 26 (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `BetaPosteriorTest` (9 тестов): чистая модель апостериорного бета-распределения, отсечение повторных commit SHA и определение частоты проверок по свидетельствам (`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` / D005, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `RuntimeHealthShiftDetectorTest` (9 тестов): точный биномиальный детектор сдвигов здоровья рантайма продукта без заимствования контрольных карт (`ELVIN_GOLDMAN_07_SELF_MODEL_SANITY` / D013);
       - `ProductCapabilityServiceTest` (18 тестов): обнаружение возможностей продукта по OpenAPI контрактам, зондирование через лаунчер, демаркация 401/403 как барьеров инструмента без ложных дефектов продукта (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RuntimeVerdictLayerTest` (6 тестов): 4-значная логика продуктовых вердиктов рантайма и оценка жизнеспособности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D004, D012, D011, D005, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green / Popper & Williamson]:**
  * Фальсификационные заслоны дизайна (`DesignExcellenceGateFalsificationTest`, `DesignSystemFalsificationServiceTest`), демаркация сбоя инструмента и продукта по Закону 22 (`FailureDemarcationLaw22Test`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE` [D010 / D011 Goldman]:**
  * Каузальная цепочка свидетельств внешних систем и сайдкаров; фиксация абстракций RAG векторных проекций; Gestalt-близость геометрии экранов (`LayoutGeometryAuditServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита эндпоинтов Google AI и внутреннего наблюдателя от неавторизованных мутаций (`GoogleAiResourceSecurityIntegrationTest`, `InternalGeminiObserverSecurityIntegrationTest`).
- **`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` & `RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` & `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` [D002 / D015 Barcan Marcus]:**
  * Модальный актуализм физических артефактов экранов, инвариантность дизайн-токенов бренда перед опциями стилизации.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D013 / D015 Wittgenstein]:**
  * Анти-зеркальная телеметрия наблюдения рантайма без побочных эффектов записи при чтении; кросс-экранное Жаккар-сходство страниц.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D006 / D004 Varzi]:**
  * Топологическая изоляция хостинга рантайма по Закону 26; монопольное владение жизненным циклом дизайна (`DesignShopCycleRepositoryIntegrationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 / D009 Belnap]:**
  * 4-значная решётка истинности вердиктов рантайма; оракул запрета подмены обязательного HTML на графические заглушки.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Dretske]:**
  * Телеосемантическое замыкание контура имплементации по Закону 3.
- **`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` [D005 Jeffrey]:**
  * Байесовская апостериорная адаптация частоты проверок рантайма по накопленным свидетельствам (`BetaPosteriorTest`).

---

## 3. Критерии приемки наряда L2

1. Провести единый непрерывный пакетный прогон всех 26 тест-сьютов Кластера Секций IX, X и XI в Docker Maven 3.9.9 Temurin-21 (-m 2g):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. Подтвердить 100% результат: **280/280 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
3. Соблюсти деонтический запрет: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. Сделать семантический коммит в фабрику: `feat(sections-ix-x-xi): complete Stage 4 batch verification (D008, D010, D006)`.
7. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
