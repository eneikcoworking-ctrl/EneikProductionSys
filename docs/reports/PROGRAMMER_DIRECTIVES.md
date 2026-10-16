# Директивы и наряд для L2 (2026-10-16 23:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена инженером (коммит `b3696c0c`, 290/290 green в 25 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента»)** целиком (**280 тестов в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция IX — Внешние системы и внешняя AI/RAG/Runtime-граница фабрики (110 тестов, 7 сьютов):**
       - `GoogleAiResourceSecurityIntegrationTest` (12/12 green), `GoogleAiResourceControllerTest` (6/6 green): безопасность API Google AI, закрытие path traversal за пределами `./data/video-assets`, key-gate и fail-closed защита (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `GeminiContextServiceTest` (28/28 green): RAG постоянного контекста, косинусная близость векторов `EmbeddingSimilarityUtil`, lazy-load содержимого документов (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `StitchClientTest` (10/10 green): JSON-RPC клиент к Stitch MCP, сохранение `screenId` и изоляция сессий (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `DesignAssetServiceTest` (12/12 green): оркестрация дизайн-артефактов, отсечение офф-токен экранов до коммита в git (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `GeminiObserverActionServiceTest` (17/17 green), `InternalGeminiObserverControllerTest` (16/16 green), `InternalGeminiObserverSecurityIntegrationTest` (9/9 green): авторизация действий наблюдателя через политики с категорическим запретом мутации кода (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Секция X — Цех дизайна: экраны, марка, геометрия и дизайн-система (91 тест, 11 сьютов):**
       - `DesignShopOrchestrationServiceTest` (13/13 green), `DesignShopOrchestrationServiceLaw15Test` (7/7 green), `DesignShopCycleRepositoryIntegrationTest` (6/6 green): полный жизненный цикл дизайн-цеха со строгим соблюдением Закона 15 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15/15 green): строгий аудит соответствия токенов дизайн-системы марке (`BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `LayoutGeometryAuditServiceTest` (13/13 green): геометрия экранов, взаимное расположение и отступы (`ENDI_KLARK_05_LAYOUT_PROXIMITY` / D011);
       - `DesignSystemFalsificationServiceTest` (6/6 green): фальсификационные заслоны целостности дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7/7 green): детектор дрейфа дизайн-токенов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `StrandedDesignCycleSweepServiceTest` (4/4 green): выметающий сборщик зависших дизайн-циклов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `DesignImplementationLoopClosureLaw3Test` (2/2 green): замыкание цикла реализации по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `DesignExcellenceGateTest` (12/12 green), `DesignExcellenceGateFalsificationTest` (6/6 green): гейт совершенства дизайна с попперовской фальсификацией ложных подтверждений (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 3: Секция XI — Наблюдение за живым продуктом клиента (79 тестов, 8 сьютов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29 green): демаркация сбоя инструмента наблюдения (`observed=false`) от сбоя продукта клиента (`answered=false`) (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `ObservationHostingDemarcationLaw26Test` (3/3 green): демаркация хостинга наблюдений по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FailureDemarcationLaw22Test` (5/5 green): фальсификация ложных отказов по Закону 22 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `BetaPosteriorTest` (9/9 green): байесовская апостериорная оценка жизнеспособности продукта (`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` / D005);
       - `RuntimeHealthShiftDetectorTest` (9/9 green): детектор статистического сдвига здоровья рантайма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `ProductCapabilityServiceTest` (18/18 green): эмпирическая способность продукта отвечать на запросы без подмены декларациями (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `RuntimeVerdictLayerTest` (6/6 green): вердиктный слой рантайма в многозначной решётке суждений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D004, D011, D005)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация гейтов совершенства дизайна (`DesignExcellenceGateFalsificationTest`), целостности дизайн-системы (`DesignSystemFalsificationServiceTest`) и демаркация сбоя инструмента наблюдения (`observed=false`) от дефекта клиента (`ClientRuntimeObservabilityServiceTest`, `FailureDemarcationLaw22Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции векторов и RAG-контекста (`GeminiContextServiceTest`), надежная каузальная цепочка свидетельств клиента Stitch (`StitchClientTest`) и отделение сырого корпуса от векторных проекций.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при зелёных тестах (exclusionary reasons);**
  * Деонтические заслоны Google AI API (`GoogleAiResourceSecurityIntegrationTest`), хостинга наблюдений Закон 26 (`ObservationHostingDemarcationLaw26Test`) и матрицы прав действий наблюдателя (`GeminiObserverActionServiceTest`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` [D015 Aesthetic drift / Barcan Marcus]:**
  * Физическое присутствие дизайн-токенов марки в актуальном мире проекта, аудит согласованности экранов (`DesignConsistencyAuditServiceTest`, `DesignShopOrchestrationServiceLaw15Test`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия открытого окна наблюдения без паразитных побочных эффектов записи (`ProductCapabilityServiceTest`, `DesignDriftMonitorServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности вердиктов рантайма (`RuntimeVerdictLayerTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Varzi]:**
  * Партитивное владение циклами дизайна и строгая топология границ файловой системы без path traversal.
- **`ENDI_KLARK_05_LAYOUT_PROXIMITY` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Clark & Dretske]:**
  * Геометрия макетов, близость элементов и телеосемантическая обратная связь замыкания цикла дизайна (`LayoutGeometryAuditServiceTest`, `DesignImplementationLoopClosureLaw3Test`, `RuntimeHealthShiftDetectorTest`).
- **`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` [D005 Bayesian posterior / Jeffrey]:**
  * Байесовская апостериорная оценка жизнеспособности продукта (`BetaPosteriorTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций IX, X и XI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. Подтвердить 100% green результат (**280/280 green в 26 тест-сьютах**).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать отметки Ступени 4 по Кластеру Секций IX, X и XI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(sections-ix-xi): complete Stage 4 batch verification (D008, D010, D006)`.
