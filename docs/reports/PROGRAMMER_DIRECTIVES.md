# Директивы и наряд для L2 (2026-10-17 10:57 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `1d36d91e`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, время 01:16 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1: Секция XVI — Безопасность, ТОС-сторож, семантика значений и контекст (89 тестов, 9 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов): безопасность внутренних путей наблюдателя, деонтический заслон неавторизованного доступа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `GeminiObserverActionServiceTest` (42 теста): детерминированная фиксация и верификация действий наблюдателя (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `TocSentinelServiceTest` (15 тестов), `TocSentinelControllerTest` (5 тестов): сторож ТОС, непрерывный мониторинг и контроль узких мест (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeanValueTest` (1 тест): семантика ценности Lean, защита от псевдоактивности (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `TargetContextTest` (1 тест): точность адресации контекста исполнителя (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 2: Секция XVI — Сходство вишлистов, настройки, изоляция пространств и Flyway (70 тестов, 7 сьютов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): изоляция сбоев стека фабрики от свойств продукта клиента (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия операционной правды (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (25 тестов), `WishlistSimilarityTest` (9 тестов): фальсификация схожести вишлистов, семантическая дедупликация (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): неизменяемость настроек и деонтический заслон системных конфигураций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): строгая валидация схемы Flyway-миграций без скрытого repair (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16 тестов): топологическая изоляция пространств имён продукта по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 3: Секция XXIX — Аудит геометрии, марка, гейты качества и дрейф дизайн-системы (59 тестов, 7 сьютов):**
       - `DesignAssetServiceTest` (12 тестов): материализация токенов бренда и коммиты макетов (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `DesignConsistencyAuditServiceTest` (15 тестов): кросс-экранная согласованность дизайн-токенов Жаккара (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `LayoutGeometryAuditServiceTest` (13 тестов): аудит физической геометрии вьюпорта без ложных срабатываний (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6 тестов): фальсификационная верификация инвариантов дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7 тестов): мониторинг дрейфа стилей между экранами (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): заслон совершенства дизайна и проверка соответствия марке (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 4: Секция XXIX — Оркестрация циклов дизайн-цеха, закон 15/3 и Stitch-клиент (54 теста, 6 сьютов):**
       - `DesignShopCycleRepositoryIntegrationTest` (6 тестов): персистентность и атомарный CAS старта дизайн-циклов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов): оркестрация фаз дизайн-цеха и строгая верификация готовности по Закону 15 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `StrandedDesignCycleSweepServiceTest` (4 теста): сборщик брошенных циклов дизайна с атомарным CAS-релизом (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2 теста): замыкание цикла реализации дизайна по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
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
