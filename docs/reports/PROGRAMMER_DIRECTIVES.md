# Директивы и наряд для L2 (2026-10-16 21:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация фабрики») полностью заверена инженером (коммит `284738db`, 290/290 green в едином Docker Maven прогоне, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секции XVI («Реестр 59 архитектурных предписаний L2: что и как чинить») и Секции XXIX («Цех дизайна: порождение экранов и сверка их с маркой»)** целиком (**272 теста в 29 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XVI — Безопасность, ТОС-сторож, семантика значений и контекст (89 тестов, 9 сьютов):**
       - Топология безопасности и институциональный аудит внутреннего наблюдателя: `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17);
       - Адаптивный сторож ТОС и чистое анти-зеркальное чтение статуса: `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2);
       - 4-значная решётка истинности ценности работы и контекста: `LeanValueTest` (9/9), `TargetContextTest` (4/4);
       - Демаркация категориальных ошибок при разборе репозиториев: `RepositoryStackAnalyzerTest` (6/6);
       - Асимметричная динамика доверия операционной правды: `OperationalTruthServiceTest` (17/17).
     * **Группа 2: Секция XVI — Сходство вишлистов, настройки, изоляция пространств и Flyway (70 тестов, 7 сьютов):**
       - Фальсификация сходства вишлистов и дедупликация: `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6);
       - Неизменяемость системных настроек и интеграционный заслон: `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13);
       - Заслон валидации миграций схемы Flyway: `FlywayMigrationValidationTest` (4/4);
       - Закон 26 демаркации пространств имён продукта и фабрики: `ProductNamespaceLaw26Test` (16/16).
     * **Группа 3: Секция XXIX — Аудит геометрии, марка, гейты качества и дрейф дизайн-системы (59 тестов, 7 сьютов):**
       - Неизменяемость дизайн-ассетов и токенов марки: `DesignAssetServiceTest` (12/12);
       - Консистентность дизайн-системы и попиксельный аудит: `DesignConsistencyAuditServiceTest` (15/15);
       - Аудит геометрии раскладки и близости элементов вьюпорта: `LayoutGeometryAuditServiceTest` (13/13);
       - Фальсификационные заслоны дизайн-системы и мониторинг дрейфа: `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7);
       - Гейт совершенства дизайна и фальсификация ложных подтверждений: `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6).
     * **Группа 4: Секция XXIX — Оркестрация циклов дизайн-цеха, закон 15/3 и Stitch-клиент (54 теста, 6 сьютов):**
       - Атомарное хранилище циклов дизайн-шопа: `DesignShopCycleRepositoryIntegrationTest` (6/6);
       - Оркестрация дизайн-цеха и деонтический Закон 15: `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7);
       - Выметающий обход застрявших циклов дизайна: `StrandedDesignCycleSweepServiceTest` (4/4);
       - Замыкание петли реализации дизайна Закон 3: `DesignImplementationLoopClosureLaw3Test` (2/2);
       - Клиент генерации дизайна Stitch с защитой от деградации до картинок: `StitchClientTest` (10/10).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей, конфигураций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D013, D002, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка схожести вишлистов (`WishlistServiceFalsificationTest`), дрейфа дизайн-системы (`DesignSystemFalsificationServiceTest`), гейтов совершенства дизайна (`DesignExcellenceGateFalsificationTest`) и валидации схемы Flyway (`FlywayMigrationValidationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик вьюпорта и геометрии раскладки (`LayoutGeometryAuditServiceTest`), асимметричная динамика роста доверия (`OperationalTruthServiceTest`) и защита происхождения контентных ключей V137.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита путей внутреннего наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), демаркация пространств имён Закон 26 (`ProductNamespaceLaw26Test`), деонтический Закон 15 оркестрации дизайн-цеха (`DesignShopOrchestrationServiceLaw15Test`).
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D015 Modal actualism / D009 Substitution oracle / Barcan Marcus & Belnap]:**
  * Физическое присутствие дизайн-токенов марки в актуальном коде проекта (`DesignAssetServiceTest`), запрет подмены HTML-кода картинками и сохранение идентичности экранных сущностей (`StitchClientTest`, `DesignImplementationLoopClosureLaw3Test`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов `LeanValue` и `TargetContext`, предотвращающая разрушительную отправку задач в чужие репозитории.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чтение состояния сторожа ТОС (`TocSentinelServiceTest`) и мониторинг дрейфа дизайна без побочных мутаций физического состояния базы данных и артефактов.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Отсечение категориальной ошибки: запрет трансляции ошибок фабрики в ложные факты о репозитории заказчика (`RepositoryStackAnalyzerTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XVI и XXIX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить 100% green результат (272/272 green, 0 failures, 0 errors, 0 skipped, 29 тест-сьютов).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
