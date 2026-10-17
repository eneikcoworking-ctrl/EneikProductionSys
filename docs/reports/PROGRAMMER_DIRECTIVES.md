# Директивы и наряд для L2 (2026-10-17 05:52 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Пакетный наряд в работу (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена инженером (коммит `607640ec`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1: Секция XVI — Реестр архитектурных предписаний L2 (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green): деонтическая защита внутреннего входа наблюдателя (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `GeminiObserverActionServiceTest` (17/17 green): верификация действий наблюдателя без побочных эффектов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): адаптивный сторож TOC, чтение DBR-снимка без мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `LeanValueTest` (9/9 green): типизация ценности по Lean без ложного одобрения муды (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `TargetContextTest` (4/4 green): явное значение `undetermined` контекста цели (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6 green): изоляция сбоев платформы от фактов о репозитории клиента (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда и асимметричная динамика доверия (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6 green), `WishlistContentSimilarityMatcherTest` (8/8 green), `WishlistSimilarityTest` (6/6 green): попперовская фальсификация схожести вишлистов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8 green), `SystemSettingsServiceTest` (13/13 green): неизменяемость защищенных системных настроек (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4 green): строгая валидация схем миграций без скрытого repair (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16 green): изоляция продуктовых пространств имён по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Секция XXIX — Цех дизайна, геометрия и дизайн-система (113 тестов, 13 сьютов):**
       - `DesignAssetServiceTest` (12/12 green): активы дизайна, строгое присутствие токенов бренда (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `DesignConsistencyAuditServiceTest` (15/15 green): кросс-экранная согласованность дизайна по метрике Жаккара (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `LayoutGeometryAuditServiceTest` (13/13 green): аудит геометрии макетов, близость элементов и вьюпорт (`ENDI_KLARK_05_LAYOUT_PROXIMITY` / D011, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6/6 green): фальсификационная оснастка дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7/7 green): мониторинг дрейфа дизайна без побочных эффектов записи (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12/12 green), `DesignExcellenceGateFalsificationTest` (6/6 green): гейт совершенства дизайна с фальсификационным заслоном (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignShopCycleRepositoryIntegrationTest` (6/6 green): персистентность циклов дизайн-цеха (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignShopOrchestrationServiceTest` (13/13 green), `DesignShopOrchestrationServiceLaw15Test` (7/7 green): оркестрация дизайн-цеха по Закону 15 готовности дизайна (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `StrandedDesignCycleSweepServiceTest` (4/4 green): выметающий обход брошенных циклов дизайна с атомарным CAS-релизом (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2/2 green): замыкание цикла реализации дизайна по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StitchClientTest` (10/10 green): взаимодействие со Stitch-клиентом без деградации HTML до скриншотов (`LUCHIANO_FLORIDI_03_SUBSTITUTION_ORACLE` / D009, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D002, D004, D009, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификация сходства вишлистов (`WishlistServiceFalsificationTest`), схемы Flyway (`FlywayMigrationValidationTest`), дизайн-системы (`DesignSystemFalsificationServiceTest`) и гейта совершенства дизайна (`DesignExcellenceGateFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: аудит геометрии вьюпорта (`LayoutGeometryAuditServiceTest`), асимметричный рост операционной правды (`OperationalTruthServiceTest`), целостность Stitch-клиента (`StitchClientTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), неизменяемость настроек (`SystemSettingsServiceTest`), Закон 26 пространств имён (`ProductNamespaceLaw26Test`), Закон 15 готовности дизайна и Закон 3 замыкания реализации.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D015 Conceptual drift / Barcan Marcus & Belnap]:**
  * Модальный актуализм: физическое присутствие токенов бренда в актуальном коде проекта (`DesignAssetServiceTest`), кросс-экранная согласованность Жаккара (`DesignConsistencyAuditServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: чистый опрос сторожа ТОС (`TocSentinelServiceTest`), действий наблюдателя (`GeminiObserverActionServiceTest`) и монитора дрейфа (`DesignDriftMonitorServiceTest`) без паразитных мутаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная решётка истинности LeanValue и TargetContext (`LeanValueTest`, `TargetContextTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок стека без заражения платформы (`RepositoryStackAnalyzerTest`).
- **`LUCHIANO_FLORIDI_03_SUBSTITUTION_ORACLE` [D009 Representation drift / Floridi]:**
  * Запрет подмены HTML изображениями и сохранение идентичности экранных ID (`StitchClientTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Varzi]:**
  * Партитивное владение: персистентность циклов дизайн-цеха и атомарный CAS-релиз брошенных циклов (`DesignShopCycleRepositoryIntegrationTest`, `StrandedDesignCycleSweepServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_05_LAYOUT_PROXIMITY` [D011 Semantic drift / Dretske & Clark]:**
  * Телеосемантическая обратная связь и воплощённый интерфейс: геометрия макетов и замыкание цикла реализации дизайна.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
```
