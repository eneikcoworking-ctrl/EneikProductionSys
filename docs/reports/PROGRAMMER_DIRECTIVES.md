# Директивы и наряд для L2 (2026-10-16 12:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная правда, Кайдзен, Фальсификация и Оркестрация») успешно заверена инженером (коммит `f7cd4e7`, 290/290 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XVI и XXIX («Реестр предписаний L2 и Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (29 тест-сьютов, 272 теста в едином пакетном прогоне Docker Maven):
     * **Секция XVI: Реестр архитектурных предписаний L2 (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): деонтическая защита диагностических эндпоинтов, аудит мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): адаптивный сторож верёвки TOC, чистое чтение DBR без мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeanValueTest` (9/9): классификация потока создания ценности Lean без категориальных ошибок (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `TargetContextTest` (4/4): контекст цели задачи со строгой изоляцией проектных пространств (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `RepositoryStackAnalyzerTest` (6/6): анализ технологического стека без искажения продуктовой реальности (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OperationalTruthServiceTest` (17/17): 4-значная динамика операционного доверия потока (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): сличитель сходства вишлиста с фальсификационным заслоном (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): хранение и управление настройками системы (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `FlywayMigrationValidationTest` (4/4): непрерывный заслон чистоты схемы базы данных и миграций Flyway (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16): изоляция пространства имён продукта по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Секция XXIX: Цех дизайна, экраны, марка и геометрия (113 тестов, 13 сьютов):**
       - `DesignAssetServiceTest` (12/12): порождение артефактов дизайна и HTML-экранов с проверкой полноты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015);
       - `DesignConsistencyAuditServiceTest` (15/15): проверка соответствия токенам марки и кросс-экранного сходства Жаккара (`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015, `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` / D015);
       - `LayoutGeometryAuditServiceTest` (13/13): аудит физической геометрии вьюпорта и взаимного расположения элементов (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7): фальсификационный мониторинг дрейфа дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): строгий гейт совершенства дизайна на реальных физических артефактах (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `DesignShopCycleRepositoryIntegrationTest` (6/6), `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7): атомарный CAS-клейм старта цикла, оркестрация дизайн-цеха по Закону 15 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `StrandedDesignCycleSweepServiceTest` (4/4): выметающий сборщик зависших циклов дизайна с динамическим лизингом (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2/2): замыкание цикла реализации дизайна по Закону 3, продвижение макетов из `design/draft` в `design/approved` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `StitchClientTest` (10/10): клиент к Stitch с сохранением идентичности экранов `screenId` и изоляцией проектов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D011, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при ложной готовности дизайн-системы (`DesignSystemFalsificationServiceTest`), симуляции гейта качества (`DesignExcellenceGateFalsificationTest`) или передаче нерелевантной заявки (`WishlistServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: строгая сегрегация сырых токенов и производных суждений; проверяемость фактов от источника к аудиту консистентности без искажения цепочки доверия.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет подмены HTML растровыми изображениями при флаге `requireImplementableHtml=true`; институциональный аудит прав доступа в контроллерах.
- **`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` [D015 Aesthetic drift / Belnap]:**
  * Единство трассировки токенов: каждый цвет и шрифт экрана обязан трассироваться к объявленному набору токенов проекта с вычислением соотношения покрытия.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D013 Runtime drift & D015 Aesthetic drift / Wittgenstein]:**
  * Анти-зеркало: аудит экранов и чтение операционной правды не оставляют побочных мутаций в базе данных; кросс-экранное сходство Жаккара для экранов одной системы.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Sensor disconnection / Clark]:**
  * Проверка физической геометрии вьюпорта, взаимного расположения и группировки элементов вместо слепой веры в самоотчёты модели.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Category substitution / Floridi]:**
  * Категорический запрет категориальной подмены типов артефактов в контуре дизайн-цеха.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер 29 тест-сьютов Секций XVI и XXIX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить строго **272/272 green** (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
