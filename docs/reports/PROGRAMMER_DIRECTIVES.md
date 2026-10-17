# Директивы и наряд для L2 (2026-10-17 14:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью подтверждена и закрыта инженером (коммит `589907c3`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:21 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, марка, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1 — Архитектурные предписания L2: авторизация наблюдателя, сторож ТОС, истинность LeanValue и TargetContext (7 сьютов, 69 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `GeminiObserverActionServiceTest` (17 тестов): деонтическая защита сервисных эндпоинтов наблюдателя, строгий запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): анти-зеркальное чистое чтение DBR-статуса и динамический триггер сторожа TOC без скрытых побочных эффектов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeanValueTest` (9 тестов), `TargetContextTest` (4 теста): 4-значная решётка истинности Белнэпа для Lean-ценностей и целевого контекста задачи (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 2 — Разбор репозитория, правда операций, семантика вишлистов, настройки и миграции (8 сьютов, 90 тестов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия строго на основе накопленных свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (8 тестов), `WishlistSimilarityTest` (6 тестов): семантическая дедупликация требований и фальсификационный заслон (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): деонтическая защита системных конфигураций фабрики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): проверка целостности и иммутабельности Flyway-миграций схемы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16 тестов): суверенная граница пространств имён продукта по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3 — Цех дизайна, геометрия, консистентность и фальсификационные заслоны (7 сьютов, 59 тестов):**
       - `DesignAssetServiceTest` (12 тестов): модальный актуализм физических ассетов экранов (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15 тестов): кросс-экранная консистентность токенов и стилей через индекс Жаккара (`NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `LayoutGeometryAuditServiceTest` (13 тестов): аудит пространственной геометрии интерфейса и вьюпорта (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6 тестов): фальсификационные контрпримеры дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7 тестов): анти-зеркальная телеметрия визуального и токенного дрейфа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): строгий гейт совершенства дизайна и фальсификационный заслон допуска (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4 — Оркестрация дизайн-цеха, Законы 15 и 3, выметающий обход и клиент Stitch (6 сьютов, 54 теста):**
       - `DesignShopCycleRepositoryIntegrationTest` (6 тестов): монопольное владение стартами дизайн-циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов): оркестрация стадий цеха дизайна и соблюдение фазовых переходов Закона 15 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `StrandedDesignCycleSweepServiceTest` (4 теста): атомарный CAS-релиз брошенных дизайн-циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2 теста): замыкание цикла имплементации дизайна по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `StitchClientTest` (10 тестов): клиент к сервису Stitch с сохранением контекста и категорическим запретом подмены HTML картинками (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D015, D004, D002, D009, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов схожести вишлистов (`WishlistServiceFalsificationTest`), иммутабельности схемы миграций (`FlywayMigrationValidationTest`), гейтов совершенства дизайна (`DesignExcellenceGateFalsificationTest`) и контрпримеров дизайн-системы (`DesignSystemFalsificationServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: аудит геометрии интерфейса и вьюпорта (`LayoutGeometryAuditService`), каузальный след свидетельств в клиенте Stitch (`StitchClient`), асимметричная динамика доверия операционной правды (`OperationalTruthService`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), неизменяемость системных настроек (`SystemSettingsService`), изоляция продуктовых пространств по Закону 26 (`ProductNamespaceLaw26Test`), дисциплина фаз цеха дизайна по Закону 15.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D012 Truth table & D015 Token loss / Belnap]:**
  * 4-значная решётка истинности Белнэпа для `LeanValue`, `TargetContext` и гейтов дизайна; согласованность дизайн-токенов через Жаккар-метрику.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистый замер состояния сторожа ТОС (`TocSentinelService`) и мониторинг визуального дрейфа дизайна (`DesignDriftMonitorService`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` [D015 Nominal existence / Barcan Marcus]:**
  * Модальный актуализм физических ассетов экранов в кодовой базе вместо фиктивных деклараций.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole confusion / Varzi]:**
  * Монопольное владение стартами дизайн-циклов и атомарный CAS-релиз зависших циклов (`StrandedDesignCycleSweepService`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Изоляция ошибок стека клиентских репозиториев от дефектов платформы фабрики.
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Representation drift / Belnap]:**
  * Категорический запрет подмены семантического HTML интерфейса статическими картинками.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Телеосемантическое замыкание цикла имплементации дизайна по Закону 3.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
