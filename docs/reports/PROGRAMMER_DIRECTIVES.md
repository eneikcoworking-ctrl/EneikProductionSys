# Директивы и наряд для L2 (2026-10-23 23:38 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `e3c5cc36`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:28 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий неразрывный стратегический кластер механизмов фабрики — **Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента»)** целиком (**280 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция IX — Внешние системы: внешняя AI/RAG/Runtime-граница фабрики (8 сьютов, 110 тестов):**
       - `GoogleAiResourceSecurityIntegrationTest` (12 тестов), `GoogleAiResourceControllerTest` (6 тестов): деонтическая граница доступа к ресурсам AI, fail-closed защита эндпоинтов, отсечение path traversal за пределы `./data/video-assets` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `GeminiContextServiceTest` (28 тестов): standing-knowledge RAG, проекции векторных строк, лок уровней абстракции векторов и содержимого, исключение мёртвого кэша (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `StitchClientTest` (10 тестов), `DesignAssetServiceTest` (12 тестов): каузальная цепь взаимодействия со Stitch UI, модальный актуализм физических ассетов экранов, запрет подмены кода изображениями (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
       - `GeminiObserverActionServiceTest` (17 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов): авторизация сервисных действий наблюдателя, инертность стабов, запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Секция X — Дизайн-цех: порождение экранов, токенизация, геометрия и live drift (11 сьютов, 91 тест):**
       - `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов), `DesignShopCycleRepositoryIntegrationTest` (6 тестов): фазовая дисциплина и деонтические запреты Закона 15, атомарный CAS-захват цикла дизайн-цеха (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `DesignConsistencyAuditServiceTest` (15 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): единство следа дизайн-токенов, фиксация уровней абстракции геометрии вьюпорта, Gestalt-близость (< 1.0) без наложений bounding box (`NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011);
       - `DesignSystemFalsificationServiceTest` (6 тестов), `DesignDriftMonitorServiceTest` (7 тестов): попперовская фальсификация согласованности дизайн-системы, анти-зеркальная телеметрия дрейфа интерфейса (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `StrandedDesignCycleSweepServiceTest` (4 теста), `DesignImplementationLoopClosureLaw3Test` (2 теста): выметающий обход зависших дизайн-циклов, телеосемантическое замыкание цикла имплементации по Закону 3 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): фальсификационный заслон совершенства дизайна и многозначная решётка вердиктов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 3: Секция XI — Наблюдение за живым продуктом клиента: апостериорный каданс и возможности продукта (7 сьютов, 79 тестов):**
       - `ClientRuntimeObservabilityServiceTest` (29 тестов): наблюдение в рамках тика оркестрации без собственного cron, ограниченное окно предварительного просмотра, самосознание рантайма (`ELVIN_GOLDMAN_07_SELF_MODEL_SANITY` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `ObservationHostingDemarcationLaw26Test` (3 теста), `FailureDemarcationLaw22Test` (5 тестов): демаркация хостинга наблюдений по Закону 26, демаркация сбоев инструмента от сбоев продукта по Закону 22 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `BetaPosteriorTest` (9 тестов), `RuntimeHealthShiftDetectorTest` (9 тестов): чистая математика апостериорного бета-распределения, точный биномиальный детектор сдвигов здоровья рантайма без категориальных ошибок контрольных карт (`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` / D005, `ELVIN_GOLDMAN_03_BELIEF_UPDATE_LEDGER` / D010, `LYUDVIG_VITGENSHTEYN_02_CATEGORY_ERROR_SCAN` / D002);
       - `ProductCapabilityServiceTest` (18 тестов): обнаружение возможностей продукта строго по контрактам OpenAPI (`docs/contracts/*.openapi.*`), демаркация 401/403 как барьеров инструмента без ложных дефектов продукта (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_13_TRUTH_STATUS_TABLE` / D012);
       - `RuntimeVerdictLayerTest` (6 тестов): 4-значная решётка продуктовых вердиктов рантайма (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D011, D012, D004, D002, D005, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны дизайн-системы (`DesignSystemFalsificationServiceTest`), гейтов качества экрана (`DesignExcellenceGateFalsificationTest`), демаркации инструмента и продукта по Закону 22 (`FailureDemarcationLaw22Test`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств внешних вызовов, лок уровней абстракции векторного корпуса RAG (`GeminiContextServiceTest`), геометрия вьюпорта (`LayoutGeometryAuditServiceTest`), апостериорный каданс наблюдений рантайма (`BetaPosteriorTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая граница доступа к ресурсам AI (`GoogleAiResourceSecurityIntegrationTest`), фазовая дисциплина дизайна по Закону 15 (`DesignShopOrchestrationServiceLaw15Test`), топологическая демаркация хостинга по Закону 26 (`ObservationHostingDemarcationLaw26Test`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D015 Modal actualism & Token trace unity / Barcan Marcus & Belnap]:**
  * Модальный актуализм физических ассетов экранов (`DesignAssetServiceTest`), единство следа дизайн-токенов (`DesignConsistencyAuditServiceTest`).
- **`LUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальная телеметрия дрейфа интерфейса (`DesignDriftMonitorServiceTest`), телеметрия живого рантайма продукта без паразитных мутаций (`ClientRuntimeObservabilityServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Teleosemantic feedback & Proximity gate / Dretske & Clark]:**
  * Замыкание цикла имплементации по Закону 3 (`DesignImplementationLoopClosureLaw3Test`), Gestalt-близость геометрии верстки (< 1.0) без наложений bounding box (`LayoutGeometryAuditServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap & Tarski]:**
  * Многозначные решётки продуктовых вердиктов рантайма (`RuntimeVerdictLayerTest`) и вердиктов совершенства дизайна (`DesignExcellenceGateTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Партитивное владение циклами дизайна и атомарный CAS-захват (`DesignShopCycleRepositoryIntegrationTest`, `StrandedDesignCycleSweepServiceTest`).
- **`DZHILBERT_RAYL_01_CATEGORY_MISTAKE` [D002 Category mistake / Ryle]:**
  * Строгое недопущение категориальных ошибок контрольных карт эпиков на временных рядах рантайма (`RuntimeHealthShiftDetectorTest`).
- **`RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` [D005 Bayesian decision / Jeffrey]:**
  * Апостериорная бета-оценка жизнеспособности продукта (`BetaPosteriorTest`).

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 26 тест-сьютов Кластера Секций IX, X и XI внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. **Критерий приёмки:** Ровно 280/280 green в 26 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (280/280 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(external-design-runtime): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
