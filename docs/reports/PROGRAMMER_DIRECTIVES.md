# Директивы и наряд для L2 (2026-10-16 22:52 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена инженером (коммиты `eee547b9` / `632d8de1`, 290/290 green в едином Docker Maven прогоне в 25 сьютах, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, геометрия и дизайн-система»)** целиком (**272 теста в едином пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1: Секция XVI — Безопасность, ТОС-сторож, семантика значений и контекст (89 тестов, 9 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): деонтическая защита сервисных путей внутреннего наблюдателя и аудит акторов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): анти-зеркальное чтение статуса верёвки ТОС без паразитной мутации при опросе (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4): 4-значная решётка истинности контекста исполнения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6): сканирование категориальных ошибок стека без заражения платформы (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17): операционная правда фабрики с асимметричным ростом доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010).
     * **Группа 2: Секция XVI — Сходство вишлистов, настройки, изоляция пространств и Flyway (70 тестов, 7 сьютов):**
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): попперовский фальсификационный заслон схожести и отсечение дубликатов вишлиста (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): неизменяемость системных настроек и деонтический барьер (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4): верификация строгой миграционной схемы базы данных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16): демаркация продуктовых пространств имён по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XXIX — Аудит геометрии, марка, гейты качества и дрейф дизайн-системы (59 тестов, 7 сьютов):**
       - `DesignAssetServiceTest` (12/12): физическое присутствие дизайн-токенов марки в кодовой базе (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15/15): кросс-экранная согласованность Жаккара и аудит стилей (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D015);
       - `LayoutGeometryAuditServiceTest` (13/13): лок уровней абстракции метрик вьюпорта и геометрии (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7): фальсификационный мониторинг дрейфа дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): строгий гейт совершенства дизайна на реальных артефактах (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 4: Секция XXIX — Оркестрация циклов дизайн-цеха, закон 15/3 и Stitch-клиент (54 теста, 6 сьютов):**
       - `DesignShopCycleRepositoryIntegrationTest` (6/6): атомарное хранилище и CAS-клейм старта цикла (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7): оркестрация дизайн-цеха по Закону 15 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StrandedDesignCycleSweepServiceTest` (4/4): выметающий обход брошенных циклов дизайна (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2/2): замыкание цикла реализации дизайна по Закону 3 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StitchClientTest` (10/10): клиент Stitch с запретом деградации до картинок при требовании HTML (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D013, D002, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские фальсификационные заслоны сходства вишлистов (`WishlistServiceFalsificationTest`), дрейфа дизайн-системы (`DesignSystemFalsificationServiceTest`), строгих гейтов дизайна (`DesignExcellenceGateFalsificationTest`) и миграционной схемы Flyway (`FlywayMigrationValidationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции геометрии раскладки (`LayoutGeometryAuditServiceTest`), релайабилистская цепь доверия артефактов и асимметричная динамика доверия операционной правды (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Закон 26 продуктовых пространств имён, Закон 15 дизайн-цеха, Закон 3 замыкания циклов реализации.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` [D015 Semantic drift / Barcan Marcus]:**
  * Модальный актуализм физического присутствия дизайн-токенов марки в кодовой базе фабрики (`DesignAssetServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности LeanValue и TargetContext.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия ТОС-сторожа (чтение верёвки без паразитных пересчётов) и мониторинга дрейфа дизайна.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Сканирование категориальных ошибок стека заказчика без деградации фабрики.
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Drifting proxy / Belnap]:**
  * Запрет деградации Stitch-клиента от HTML до суррогатных картинок.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XVI и XXIX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить 100% green результат (272/272 green в 29 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
