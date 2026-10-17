# Директивы и наряд для L2 (2026-10-17 17:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `fce7291f`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:18 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, марка, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1 — Архитектурные предписания L2: авторизация наблюдателя, сторож ТОС, истинность LeanValue и TargetContext (7 сьютов, 75 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `GeminiObserverActionServiceTest` (17 тестов): деонтическая защита сервисных эндпоинтов наблюдателя, строгий запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): анти-зеркальное чистое чтение DBR-статуса и динамический триггер сторожа TOC без скрытых побочных эффектов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeanValueTest` (9 тестов), `TargetContextTest` (4 теста): 4-значная решётка истинности Белнэпа для Lean-ценностей и целевого контекста задачи (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 2 — Разбор репозитория, правда операций, семантика вишлистов, настройки, миграции и границы пространств имён (9 сьютов, 84 теста):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия строго на основе накопленных свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (8 тестов), `WishlistSimilarityTest` (6 тестов): семантическая дедупликация требований и фальсификационный заслон (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): деонтический запрет мутации неизменяемых системных параметров (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): целостность структуры и нерушимость схемы БД фабрики (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProductNamespaceLaw26Test` (16 тестов): топологическая изоляция продуктовых пространств по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3 — Цех дизайна, геометрия, консистентность и фальсификационные заслоны (7 сьютов, 71 тест):**
       - `DesignAssetServiceTest` (12 тестов): модальный актуализм физических ассетов экранов (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15 тестов): единство следа дизайн-токенов (`NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `LayoutGeometryAuditServiceTest` (13 тестов): фиксация уровней абстракции геометрии вьюпорта (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6 тестов): попперовская фальсификация согласованности дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7 тестов): анти-зеркальная телеметрия дрейфа интерфейса (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): многозначная оценка совершенства дизайна и заслон против фиктивных вердиктов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4 — Оркестрация дизайн-цеха, Законы 15 и 3, выметающий обход и клиент Stitch (6 сьютов, 42 теста):**
       - `DesignShopCycleRepositoryIntegrationTest` (6 тестов), `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов): владение переходами и деонтическая фазовая дисциплина дизайна по Закону 15 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StrandedDesignCycleSweepServiceTest` (4 теста): атомарный CAS-свип зависших циклов дизайна (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2 теста): телеосемантическое замыкание цикла имплементации по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `StitchClientTest` (10 тестов): каузальная цепь взаимодействия со Stitch UI и запрет подмены кода изображениями (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D015, D013, D004, D002, D011, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификационная оснастка в `WishlistServiceFalsificationTest`, `DesignSystemFalsificationServiceTest` и `DesignExcellenceGateFalsificationTest`: поиск контрпримеров, активное опровержение ложно-положительных исходов и удержание защитных инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Защита уровней абстракции в геометрии интерфейсов (`LayoutGeometryAuditServiceTest`), каузальная цепь взаимодействия со Stitch UI (`StitchClientTest`), каузальный след схемной миграции (`FlywayMigrationValidationTest`) и асимметричная динамика доверия (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Авторизационные барьеры безопасности эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `InternalGeminiObserverControllerTest`), неизменяемость настроек (`SettingsControllerIntegrationTest`, `SystemSettingsServiceTest`) и фазовая дисциплина дизайна по Закону 15 (`DesignShopOrchestrationServiceLaw15Test`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D012 Belnap 4-valued / D015 Token trace unity]:**
  * 4-значная решётка истинности Белнэпа (`True`, `False`, `Both`, `Neither`) для `LeanValueTest`, `TargetContextTest`, `DesignExcellenceGateTest`; единство следа токенов марки в `DesignConsistencyAuditServiceTest` и `WishlistSimilarityTest`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальный сбор объективной телеметрии без скрытых сайд-эффектов и самооправданий (`DesignDriftMonitorServiceTest`, `TocSentinelServiceTest`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` [D015 Modal actualism / Barcan Marcus]:**
  * Модальный актуализм физических ассетов экранов (`DesignAssetServiceTest`): требование физически существующих артефактов верстки.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereological collapse / D006 Boundary topology / Varzi]:**
  * Мереологическая целостность владения циклами дизайна (`DesignShopCycleRepositoryIntegrationTest`), атомарный CAS-свип (`StrandedDesignCycleSweepServiceTest`) и топологическая изоляция продуктовых пространств по Закону 26 (`ProductNamespaceLaw26Test`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Демаркация внутренних системных сбоев платформы от внешних характеристик клиентского репозитория (`RepositoryStackAnalyzerTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Информационная телеосемантическая обратная связь замыкания цикла имплементации по Закону 3 (`DesignImplementationLoopClosureLaw3Test`).
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Belnap]:**
  * Неподкупный оракул подстановки: строгий запрет подмены реального кода визуальными заглушками и картинками (`StitchClientTest`).

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводятся по директиве оператора.
