# Директивы и наряд для L2 (2026-10-17 02:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация фабрики») успешно заверена инженером (коммит `88860070`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента»)** целиком (**280 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция IX — Внешние системы и внешняя AI/RAG/Runtime-граница фабрики (110 тестов, 8 сьютов):**
       - `GoogleAiResourceSecurityIntegrationTest` (12/12), `GoogleAiResourceControllerTest` (6/6): типобезопасная безопасность API Google AI, закрытие path traversal за пределами `./data/video-assets`, key-gate и fail-closed защита (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `GeminiContextServiceTest` (28/28): RAG постоянного контекста, косинусная близость векторов `EmbeddingSimilarityUtil`, lazy-load содержимого документов (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `StitchClientTest` (10/10): JSON-RPC клиент к Stitch MCP, сохранение `screenId` и изоляция сессий (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `DesignAssetServiceTest` (12/12): оркестрация дизайн-артефактов, отсечение офф-токен экранов до коммита в git (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `GeminiObserverActionServiceTest` (17/17), `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9): авторизация действий наблюдателя через политики с категорическим запретом мутации кода (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Секция X — Цех дизайна: экраны, марка, геометрия и дизайн-система (91 тест, 11 сьютов):**
       - `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7), `DesignShopCycleRepositoryIntegrationTest` (6/6): полный жизненный цикл дизайн-цеха со строгим соблюдением Закона 15 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15/15): строгий аудит соответствия токенов дизайн-системы марке (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `LayoutGeometryAuditServiceTest` (13/13): геометрия экранов, взаимное расположение и отступы (`ENDI_KLARK_05_LAYOUT_PROXIMITY` / D011);
       - `DesignSystemFalsificationServiceTest` (6/6): фальсификационные заслоны целостности дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7/7): детектор дрейфа дизайн-токенов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `StrandedDesignCycleSweepServiceTest` (4/4): выметающий сборщик зависших дизайн-циклов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `DesignImplementationLoopClosureLaw3Test` (2/2): замыкание цикла реализации по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): гейт совершенства дизайна с попперовской фальсификацией ложных подтверждений (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 3: Секция XI — Наблюдение за живым продуктом клиента (79 тестов, 7 сьютов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29): демаркация сбоя инструмента наблюдения (`observed=false`) от сбоя продукта клиента (`answered=false`) (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `ObservationHostingDemarcationLaw26Test` (3/3): демаркация хостинга наблюдений по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FailureDemarcationLaw22Test` (5/5): фальсификация ложных отказов по Закону 22 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `BetaPosteriorTest` (9/9): байесовская апостериорная оценка жизнеспособности продукта (`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` / D005);
       - `RuntimeHealthShiftDetectorTest` (9/9): детектор статистического сдвига здоровья рантайма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `ProductCapabilityServiceTest` (18/18): эмпирическая способность продукта отвечать на запросы без подмены декларациями (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `RuntimeVerdictLayerTest` (6/6): вердиктный слой рантайма в многозначной решётке суждений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D015, D012, D004, D011, D005)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны совершенства дизайна (`DesignExcellenceGateFalsificationTest`), целостности дизайн-системы (`DesignSystemFalsificationServiceTest`), демаркации отказов по Закону 22 (`FailureDemarcationLaw22Test`). Защита от фиктивно-зеленых тестов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции геометрии макетов (`LayoutGeometryAuditServiceTest`) и векторных фрагментов RAG (`GeminiContextServiceTest`); каузальная цепь надежности для клиента Stitch MCP (`StitchClientTest`) и байесовского апостериорного оценивания (`BetaPosteriorTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты эндпоинтов Google AI ресурсов (path traversal lock), демаркация хостинга наблюдений по Закону 26 (`ObservationHostingDemarcationLaw26Test`), Закон 15 готовности циклов дизайна (`DesignShopOrchestrationServiceLaw15Test`), Закон 3 замыкания реализации дизайна (`DesignImplementationLoopClosureLaw3Test`) и матрица деонтических политик наблюдателя (`GeminiObserverActionServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Эмпирическая анти-зеркальная телеметрия рантайма живого продукта (`ProductCapabilityServiceTest`, `ClientRuntimeObservabilityServiceTest`), демаркация сбоя инструмента (`observed=false`) от сбоя продукта (`answered=false`), детектор дрейфа дизайн-токенов (`DesignDriftMonitorServiceTest`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` [D015 Modal actualism / Barcan Marcus]:**
  * Физическое присутствие дизайн-токенов марки в репозитории проекта (`DesignAssetServiceTest`, `DesignConsistencyAuditServiceTest`) — фактическое соответствие бренду в актуальном мире проекта.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка вердиктов рантайма (`RuntimeVerdictLayerTest`) без подмены неопределенности успехом.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Boundary topology / Varzi]:**
  * Атомарный CAS-клейм старта циклов дизайн-цеха (`DesignShopCycleRepositoryIntegrationTest`), выметающий обход брошенных циклов (`StrandedDesignCycleSweepServiceTest`), топологическая защита путей активов.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_05_LAYOUT_PROXIMITY` [D011 Teleosemantic feedback / Dretske & Clark]:**
  * Геометрия макетов, отступы и телеосемантический детектор сдвига здоровья рантайма (`RuntimeHealthShiftDetectorTest`).
- **`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` [D005 Bayesian decision loss / Jeffrey]:**
  * Байесовский расчет ожидаемых потерь при оценке жизнеспособности продукта.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций IX, X и XI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. Подтвердить 100% зелёных тестов (**280/280 green в 26 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Соблюсти деонтический инвариант Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать запись секций в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (статус: ideal, замер в Docker Maven 280/280 green).
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк) и сделать коммит `feat(sections-ix-x-xi): complete Stage 4 batch verification (D008, D010, D006, D015, D013)`.
