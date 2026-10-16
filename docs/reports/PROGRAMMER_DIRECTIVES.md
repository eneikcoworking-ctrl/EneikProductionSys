# Директивы и наряд для L2 (2026-10-16 14:58 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена инженером (коммит `e3a58d1`, 290/290 green, 0 строк изменений рабочего кода). Ранее Секция VII (TOC) также закрыта со 100% green.
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XVI и XXIX («Реестр архитектурных предписаний L2: 59 предписаний» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (29 тест-сьютов, 272 теста в едином пакетном прогоне в Docker Maven):
     * **Группа 1: Секция XVI — Реестр архитектурных предписаний L2 (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): деонтические заслоны доступа к эндпоинтам наблюдателя по API-ключу (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): адаптивная верёвка TOC, устранение холостых пересчётов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4): четырёхзначная семантика истинности без ложной уверенности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6): запрет превращения ошибок фабрики в ложные факты о заказчике (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17): асимметричная динамика доверия строго по подтверждённым свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): семантическое вето дубликатов требований и фальсификационный заслон схожести (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): строгое маскирование секретов и институциональный аудит мутаций (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `FlywayMigrationValidationTest` (4/4): валидация чексумм миграций схемы БД, запрет молчаливого repair (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProductNamespaceLaw26Test` (16/16): топологическая демаркация пространств имён продукта и фабрики (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Секция XXIX — Цех дизайна, экраны, марка, геометрия и дизайн-система (113 тестов, 13 сьютов):**
       - `DesignAssetServiceTest` (12/12): генерация экранных ассетов, категорический запрет подмены HTML картинками (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009);
       - `DesignConsistencyAuditServiceTest` (15/15), `LayoutGeometryAuditServiceTest` (13/13): аудит соответствия токенам марки и геометрии вьюпорта (`BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7): фальсификационный заслон целостности дизайн-системы и мониторинг дрейфа (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): строгий гейт совершенства дизайна на реальных физических артефактах (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `DesignShopCycleRepositoryIntegrationTest` (6/6), `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7): оркестрация цикла дизайн-цеха с защитой от брошенных циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `StrandedDesignCycleSweepServiceTest` (4/4), `DesignImplementationLoopClosureLaw3Test` (2/2): выметание зависших циклов дизайна и каузальное замыкание петли реализации по Закону 3 (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013);
       - `StitchClientTest` (10/10): клиент к Stitch со стабильным сохранением идентификаторов экранов и защитой от дрейфа идентификаторов (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D009, D004, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны дизайн-системы (`DesignSystemFalsificationServiceTest`), фальсификация гейта совершенства дизайна (`DesignExcellenceGateFalsificationTest`), фальсификационный заслон схожести вишлистов (`WishlistServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и релайабилистская цепь свидетельств: аудит геометрии макета без потери каузального контекста (`LayoutGeometryAuditServiceTest`), асимметричная динамика доверия (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Заслон несанкционированного доступа к внутреннему наблюдателю по API-ключу, матрица прав/обязанностей при смене настроек системы (`SystemSettingsService`).
- **`BARKAN_MARKUS_01_MODAL_ACTUALISM` [D015 Inconsistent system state / Marcus]:**
  * Модальный актуализм: согласованность дизайн-системы, токенов бренда и вьюпорта в реальном пространстве состояний.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: беспристрастный мониторинг дрейфа стилей и верёвки ТОС.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырёхзначная логика истинности в LeanValue и целевом контексте задачи.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Identity drift / Floridi]:**
  * Запрет подмены семантических сущностей суррогатами: сохранение identity экранов в `StitchClient`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereological confusion / D006 Boundary topology / Varzi]:**
  * Монопольное владение циклом дизайн-цеха (`DesignShopCycle`) и топологическая граница пространств имён (`ProductNamespaceLaw26Test`).

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 29 тест-сьютов Секций XVI и XXIX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить строго **272/272 green** (29 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
