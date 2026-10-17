# Директивы и наряд для L2 (2026-10-17 19:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `1453f977`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 49.78 s, строго 0 строк изменений рабочего кода).
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

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D013, D011, D004, D002, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: тесты не подтверждают «успех», а активно пытаются опровергнуть инварианты дизайн-системы (`DesignSystemFalsificationServiceTest`), гейтов совершенства дизайна (`DesignExcellenceGateFalsificationTest`), семантики вишлистов (`WishlistServiceFalsificationTest`) и сокращения лямбда-ядра ТОС (`TocSentinelServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss & Abstraction lock / Goldman]:**
  * Жесткая фиксация уровней абстракции и цепь надежности: разделение визуальных уровней геометрии экрана (`LayoutGeometryAuditServiceTest`), асимметричная динамика доверия (`OperationalTruthServiceTest`) и непрерывность каузальной цепи схемы Flyway.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита сервисных эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), неизменяемость настроек (`SettingsControllerIntegrationTest`) и фазовая дисциплина дизайна по Закону 15.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D015 Visual ghost / Barcan Marcus & Belnap]:**
  * Модальный актуализм и единство следа дизайн-токенов: физическое присутствие дизайн-токенов в кодовой базе и аудит ассетов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth table / Belnap]:**
  * 4-значная решётка истинности для Lean-ценностей (`LeanValueTest`), контекста задач (`TargetContextTest`) и вердиктов дизайна.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: телеметрия рантайма и замеры дрейфа интерфейса (`DesignDriftMonitorServiceTest`), чистое чтение DBR-снимка сторожем ТОС без побочных эффектов мутации.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Телеосемантическое замыкание цикла имплементации по Закону 3.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Boundary topology & Mereology / Varzi]:**
  * Партитивное владение циклами дизайна, атомарный свип и топологическая изоляция продуктовых пространств имён (Закон 26).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Категориальная демаркация: исключение ложных обвинений заказчика при сбоях доступа к репозиторию.
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Belnap]:**
  * Запрет подмены рабочего UI-кода статическими изображениями в StitchClient.

---

## 3. Критерий закрытия такта L2

1. Прогон всех 29 сьютов Кластера Секций XVI и XXIX (272 теста) в единой команде в Docker-контейнере Maven (`docker run --rm -m 2g -v /root/.m2:/root/.m2 -v "$PWD":/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Результат: 100% green (`272/272 green`, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Строго **0 строк изменений рабочего кода** фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализация строк `*Живое:*` Кластера Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Краткий отчёт (15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
6. Фиксация коммитом с префиксом `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
