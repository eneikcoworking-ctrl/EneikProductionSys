# Директивы и наряд для L2 (2026-10-16 19:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») успешно заверена инженером (коммит `770e21c2`, 290/290 green в 25 тест-сьютах единого Docker Maven прогона, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера механизмов — **Кластер Секций XVI и XXIX («Реестр архитектурных предписаний L2» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (**272 теста в 29 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XVI — Реестр архитектурных предписаний L2 (159 тестов, 16 сьютов):**
       - Защитные барьеры и эндпоинты внутреннего наблюдателя: `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17);
       - TOC-сторож и контроллер превентивного обнаружения узких мест: `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2);
       - Оценка бережливой ценности и целевой контекст потока: `LeanValueTest` (9/9), `TargetContextTest` (4/4);
       - Анализ стека внешнего репозитория без категориальных ошибок: `RepositoryStackAnalyzerTest` (6/6);
       - Операционная правда и демаркация пространства имен продукта: `OperationalTruthServiceTest` (17/17), `ProductNamespaceLaw26Test` (16/16);
       - Вишлисты требований, семантическая схожесть и фальсификация: `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6);
       - Системные настройки и неизменяемость схемы БД: `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13), `FlywayMigrationValidationTest` (4/4).
     * **Группа 2: Секция XXIX — Цех дизайна, экраны, марка, геометрия и дизайн-система (113 тестов, 13 сьютов):**
       - Активы дизайна и аудит согласованности стилей токенов марки: `DesignAssetServiceTest` (12/12), `DesignConsistencyAuditServiceTest` (15/15);
       - Аудит геометрии вьюпорта с защитой от выпадения элементов: `LayoutGeometryAuditServiceTest` (13/13);
       - Фальсификационный заслон целостности дизайн-системы и монитор дрейфа: `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7);
       - Гейт совершенства дизайна на физических артефактах: `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6);
       - Оркестрация цикла дизайн-цеха с защитой от коллизий владения: `DesignShopCycleRepositoryIntegrationTest` (6/6), `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7);
       - Выметание брошенных циклов и каузальное замыкание петли реализации: `StrandedDesignCycleSweepServiceTest` (4/4), `DesignImplementationLoopClosureLaw3Test` (2/2);
       - Интеграция со Stitch с сохранением стабильности экранных ID: `StitchClientTest` (10/10).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D004, D002, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация схожести вишлистов (`WishlistServiceFalsificationTest`), дрейфа дизайн-системы (`DesignSystemFalsificationServiceTest`) и гейта совершенства дизайна (`DesignExcellenceGateFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепь надежности: аудит геометрии вьюпорта (`LayoutGeometryAuditServiceTest`), асимметричный рост доверия (`OperationalTruthServiceTest`) и проверка артефактов дизайна.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодификация запретов доступа к сервисным эндпоинтам наблюдателя и неизменяемости Flyway-миграций схемы.
- **`BARKAN_MARKUS_01_MODAL_ACTUALISM` & `RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` [D015 Aesthetic drift / Barcan Marcus]:**
  * Модальный актуализм и единство трассировки дизайн-токенов: аудит согласованности стилей и токенов марки.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Физическое состояние базы и логов выше любых словесных самоописаний. Наблюдаемость TOC сторожа и монитора дрейфа дизайна без побочных эффектов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности в `LeanValueTest` и `TargetContextTest`.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D006 Boundary topology & D004 Part-whole ownership / Varzi]:**
  * Топологическая демаркация пространств имен (`ProductNamespaceLaw26Test`) и владение циклами дизайн-цеха.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Запрет трансляции ошибок фабрики в ложные факты о репозитории заказчика (`RepositoryStackAnalyzerTest`).
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Floridi]:**
  * Запрет подмены семантического HTML картинками и сохранение идентичности экранных сущностей Stitch.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XVI и XXIX в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить инвариант Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Зафиксировать такт семантическим коммитом: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
