# Директивы и наряд для L2 (2026-10-17 12:51 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `687d90fc`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:16 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг макро-цикла — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1 — Безопасность, наблюдатель, ТОС-сторож и демаркация ценности (9 сьютов, 89 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `GeminiObserverActionServiceTest` (17 тестов): деонтическая защита внутренних маршрутов наблюдателя, строгий запрет неавторизованного доступа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): сторож ТОС, непрерывный пересчёт ограничений и инвариант не-ухудшения throughput (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeanValueTest` (9 тестов): операционная демаркация потерь (Muda) от прямой ценности для клиента (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `TargetContextTest` (4 теста), `RepositoryStackAnalyzerTest` (6 тестов): контекстная изоляция задач и анализ технологического стека без категориальных ошибок (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия, регистрация свидетельств исходов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2 — Операционная правда, вишлисты, системные настройки и валидация миграций (7 сьютов, 70 тестов):**
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (8 тестов), `WishlistSimilarityTest` (6 тестов): попперовская фальсификация схожести вишлистов и дедупликация (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): деонтическая защита системных конфигураций фабрики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): проверка целостности и иммутабельности Flyway-миграций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
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

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D002, D009, D004, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификация схожести вишлистов (`WishlistServiceFalsificationTest`), миграций Flyway (`FlywayMigrationValidationTest`), контрпримеров дизайн-системы (`DesignSystemFalsificationServiceTest`) и гейта совершенства дизайна (`DesignExcellenceGateFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции геометрии вьюпорта (`LayoutGeometryAuditServiceTest`), сохранение каузального следа свидетельств и надежность цепи Stitch (`StitchClientTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), неизменяемость настроек (`SystemSettingsServiceTest`), Закон 26 суверенитета пространств имён продукта (`ProductNamespaceLaw26Test`), Закон 15 фазовых переходов дизайна (`DesignShopOrchestrationServiceLaw15Test`) и запрет подмены HTML картинками.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D015 Drift from real world / Barcan Marcus & Belnap]:**
  * Физическое присутствие дизайн-токенов марки в актуальном коде (`DesignAssetServiceTest`) и кросс-экранная согласованность Жаккара (`DesignConsistencyAuditServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия сторожа TOC (`TocSentinelServiceTest`) и монитора дрейфа дизайна (`DesignDriftMonitorServiceTest`) без паразитных побочных эффектов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status confusion / Belnap]:**
  * Многозначная решётка истинности `LeanValue`, `TargetContext` и гейтов совершенства дизайна.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Изоляция категориальных ошибок стека без заражения платформы (`RepositoryStackAnalyzerTest`, `LeanValueTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Coupling creep / Varzi]:**
  * Монопольное владение стартами дизайн-циклов и атомарный CAS-клейм брошенных циклов (`StrandedDesignCycleSweepServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Замыкание цикла имплементации дизайна по Закону 3 (`DesignImplementationLoopClosureLaw3Test`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=InternalGeminiObserverSecurityIntegrationTest,\
InternalGeminiObserverControllerTest,\
GeminiObserverActionServiceTest,\
TocSentinelServiceTest,\
TocSentinelControllerTest,\
LeanValueTest,\
TargetContextTest,\
RepositoryStackAnalyzerTest,\
OperationalTruthServiceTest,\
WishlistServiceFalsificationTest,\
WishlistContentSimilarityMatcherTest,\
WishlistSimilarityTest,\
SettingsControllerIntegrationTest,\
SystemSettingsServiceTest,\
FlywayMigrationValidationTest,\
ProductNamespaceLaw26Test,\
DesignAssetServiceTest,\
DesignConsistencyAuditServiceTest,\
LayoutGeometryAuditServiceTest,\
DesignSystemFalsificationServiceTest,\
DesignDriftMonitorServiceTest,\
DesignExcellenceGateTest,\
DesignExcellenceGateFalsificationTest,\
DesignShopCycleRepositoryIntegrationTest,\
DesignShopOrchestrationServiceTest,\
DesignShopOrchestrationServiceLaw15Test,\
StrandedDesignCycleSweepServiceTest,\
DesignImplementationLoopClosureLaw3Test,\
StitchClientTest
```

---

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 29 тест-сьютов Кластера Секций XVI и XXIX (272 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**272/272 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом:
   ```bash
   git commit -m "feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
