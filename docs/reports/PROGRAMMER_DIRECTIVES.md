# Директивы и наряд для L2 (2026-10-17 12:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `3cfe77ad`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:28 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг макро-цикла — **Кластер Секций XVI и XXIX («Архитектурные предписания, Безопасность, ТОС и Цех дизайна»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1 — Безопасность, наблюдатель, ТОС-сторож и демаркация ценности (52 теста, 8 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (10 тестов), `InternalGeminiObserverControllerTest` (8 тестов), `GeminiObserverActionServiceTest` (10 тестов): деонтическая защита внутренних маршрутов наблюдателя, строгий запрет неавторизованного доступа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (6 тестов), `TocSentinelControllerTest` (5 тестов): сторож ТОС, непрерывный пересчёт ограничений и инвариант не-ухудшения throughput (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeanValueTest` (6 тестов): операционная демаркация потерь (Muda) от прямой ценности для клиента (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `TargetContextTest` (4 теста), `RepositoryStackAnalyzerTest` (3 теста): контекстная изоляция задач и анализ технологического стека без категориальных ошибок (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 2 — Операционная правда, вишлисты, системные настройки и валидация миграций (69 тестов, 8 сьютов):**
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия, регистрация свидетельств исходов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `WishlistServiceFalsificationTest` (5 тестов), `WishlistContentSimilarityMatcherTest` (10 тестов), `WishlistSimilarityTest` (6 тестов): попперовская фальсификация схожести вишлистов и дедупликация (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `SettingsControllerIntegrationTest` (6 тестов), `SystemSettingsServiceTest` (8 тестов): деонтическая защита системных конфигураций фабрики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (10 тестов): проверка целостности и иммутабельности Flyway-миграций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (7 тестов): суверенная граница пространств имён продукта по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 3 — Цех дизайна, геометрия, консистентность и фальсификационные заслоны (83 теста, 6 сьютов):**
       - `DesignAssetServiceTest` (16 тестов): модальный актуализм физических ассетов экранов (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (14 тестов): кросс-экранная консистентность токенов и стилей через индекс Жаккара (`NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `LayoutGeometryAuditServiceTest` (15 тестов): аудит пространственной геометрии интерфейса (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (13 тестов): фальсификационные контрпримеры дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (12 тестов): анти-зеркальная телеметрия визуального и токенного дрейфа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (13 тестов): строгий гейт допуска экранных решений перед сдачей (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 4 — Оркестрация дизайн-цеха, Законы 15 и 3, выметающий обход и клиент Stitch (68 тестов, 7 сьютов):**
       - `DesignExcellenceGateFalsificationTest` (8 тестов): фальсификационный заслон гейта совершенства дизайна (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignShopCycleRepositoryIntegrationTest` (9 тестов): монопольное владение стартами дизайн-циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignShopOrchestrationServiceTest` (15 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов): оркестрация стадий цеха дизайна и соблюдение фазовых переходов Закона 15 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `StrandedDesignCycleSweepServiceTest` (11 тестов): атомарный CAS-релиз брошенных дизайн-циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (8 тестов): замыкание цикла имплементации дизайна по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `StitchClientTest` (10 тестов): клиент к сервису Stitch с сохранением контекста и категорическим запретом подмены HTML картинками (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D013, D002, D009, D004, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов схожести вишлистов (`WishlistServiceFalsificationTest`), миграций Flyway (`FlywayMigrationValidationTest`), инвариантов дизайн-системы (`DesignSystemFalsificationServiceTest`) и гейтов совершенства дизайна (`DesignExcellenceGateFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: аудит геометрии макета без категориальных ошибок (`LayoutGeometryAuditServiceTest`), надежная каузальная цепочка токенов и стилей в Stitch-клиенте (`StitchClientTest`), асимметричная динамика доверия в операционной правде (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита настроек (`SystemSettingsServiceTest`), внутренних маршрутов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), топологическая изоляция пространств имён по Закону 26 (`ProductNamespaceLaw26Test`) и соблюдение фазовых переходов Закона 15 в дизайн-цехе.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D015 Phantom artifact / Barcan Marcus & Belnap]:**
  * Модальный актуализм физических токенов бренда и артефактов экранов, единство следа токенов между экранами через индекс Жаккара (`DesignAssetServiceTest`, `DesignConsistencyAuditServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 Policy contradiction & D009 / Belnap]:**
  * 4-значная решётка истинности гейтов дизайна и оракул запрета подмены реализации картинками (`StitchClientTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Телеметрия дрейфа стилей и честное отражение операционной правды без декоративных зеркал (`DesignDriftMonitorServiceTest`, `OperationalTruthServiceTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Изоляция категорий Lean-ценности и отсечение инструментальных сбоев фабрики от свойств продукта клиента (`LeanValueTest`, `RepositoryStackAnalyzerTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D004 Concurrency & D011 Feedback / Varzi & Dretske]:**
  * Монопольное владение стартами дизайн-циклов, атомарный CAS-релиз брошенных циклов и телеосемантическая обратная связь по Закону 3 (`DesignShopCycleRepositoryIntegrationTest`, `StrandedDesignCycleSweepServiceTest`, `DesignImplementationLoopClosureLaw3Test`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test \
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

1. Запустить единый непрерывный пакетный прогон 29 тест-сьютов Кластера Секций XVI и XXIX (272 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**272/272 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: **строго 0 строк изменений рабочего кода фабрики**.
4. Актуализировать строки `*Живое:*` и итоговые блоки Кластера Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сделать семантический коммит:
   ```bash
   git commit -m "feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
