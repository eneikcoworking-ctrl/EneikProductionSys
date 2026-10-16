# Пакетная директива L1/L3 -> L2: Секции XVI и XXIX — Реестр 59 архитектурных предписаний и Цех дизайна (экраны, марка, геометрия и дизайн-система)

**Дата:** 2026-10-16 06:00 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена и закрыта инженером (290/290 green, коммит d96e634). Настоящая директива открывает следующий масштабный пакетный контур фабрики — **Секция XVI («Реестр 59 архитектурных предписаний L2») и Секция XXIX («Цех дизайна: экраны, марка, геометрия и дизайн-система»)**:
   - **Секция XVI (Архитектурные предписания L2: 159 тестов):**
     * `InternalGeminiObserverController` & `InternalGeminiObserverSecurityIntegrationTest` (16 + 9 green) — деонтическая защита внутреннего контура и проверка ролевого доступа (D006 Raz).
     * `GeminiObserverActionService` (17 green) — аудит действий с верификацией непрерывности журнала (D010 Goldman).
     * `TocSentinelService` & `TocSentinelController` (18 + 2 green) — адаптивный сторож ТОС с кэшированным чтением DBR без побочных эффектов записи (D013 Wittgenstein, D008 Church).
     * `LeanValue` (9 green) — классификация ценности по Lean и отсечение муды (D010 Goldman).
     * `TargetContext` (4 green) — демаркация системных и продуктовых контекстов выполнения (D010 Goldman).
     * `RepositoryStackAnalyzer` (6 green) — детерминированный аудит технологического стека (D004 Varzi).
     * `OperationalTruthService` (17 green) — честная проекция телеметрии рантайма и заслон `done_is_not_delivery` (D013 Wittgenstein).
     * `WishlistService`, `WishlistContentSimilarityMatcher`, `WishlistSimilarity` (6 + 8 + 6 green) — фальсификационная дедупликация и семантический матчинг требований (D007 Gilbert & Searle, D008 Popper).
     * `SettingsControllerIntegrationTest` & `SystemSettingsService` (8 + 13 green) — неприкосновенность системных настроек и конфигурации (D006 Raz).
     * `FlywayMigrationValidation` & `ProductNamespaceLaw26` (4 + 16 green) — структурная целостность миграций БД и изоляция пространств имён продуктов (D004 Varzi).
   - **Секция XXIX (Цех дизайна: экраны, марка, геометрия и дизайн-система: 113 тестов):**
     * `DesignAssetService` (12 green) — хранение, версионирование и валидация дизайн-артефактов (D010 Goldman).
     * `DesignConsistencyAuditService` & `LayoutGeometryAuditService` (15 + 13 green) — аудит геометрии разметки, иерархии слоев и токенов марки (D015 Barcan Marcus, D008 Popper).
     * `DesignSystemFalsificationService` (6 green) — фальсификационный заслон устойчивости компонентов и стилей (D008 Popper).
     * `DesignDriftMonitorService` (7 green) — детектор расхождения между дизайн-макетом и кодом экранов (D013 Wittgenstein).
     * `DesignExcellenceGate` & `DesignExcellenceGateFalsification` (12 + 6 green) — гейт превосходства дизайна и фальсификатор пропуска брака (D008 Popper, D006 Raz).
     * `DesignShopCycleRepository` & `DesignShopOrchestrationService` (6 + 13 + 7 green) — аренда цикла дизайна и оркестрация по Закону 15 (D004 Varzi, D006 Raz).
     * `StrandedDesignCycleSweepService` (4 green) — выметающий обход зависших циклов дизайна (D004 Varzi).
     * `DesignImplementationLoopClosureLaw3` & `StitchClient` (2 + 10 green) — замыкание петли реализации дизайна и интеграция генератора макетов (D012 Belnap, D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, контроллеров и гейтов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость архитектурных заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D013, D004, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_01_KNOWLEDGE_BOUNDARY` [D008 False green / Popper & Williamson]:**
  * Попперовская фальсификация: тесты фальсификации гейта дизайна (`DesignExcellenceGateFalsificationTest`), требований (`WishlistServiceFalsificationTest`) и дизайн-системы (`DesignSystemFalsificationServiceTest`) доказывают воспроизводимое падение при нарушении инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Защита родословной данных и уровней абстракции: демаркация контекстов (`TargetContext`), анализ стека (`RepositoryStackAnalyzer`), неизменяемость журнала (`GeminiObserverActionService`), сохранение дизайн-артефактов (`DesignAssetService`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: ролевой заслон в `InternalGeminiObserverController`, непроницаемость настроек в `SettingsController`, жесткие деонтические барьеры в `DesignShopOrchestrationService`.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NAPS_BELNAP_03_FOUR_VALUED_LOGIC` [D015 Trace discontinuity / Barcan Marcus & D012 Belnap]:**
  * Модальный актуализм токенов и стилей дизайна: токены марки не подменяются произвольными константами; четырехзначная логика вердиктов исключает смешение неопределенности с успехом.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: честный мониторинг дрейфа дизайна (`DesignDriftMonitorService`) и расхождения сдачи и доставки (`OperationalTruthService`), запрещающий фабрике скрывать расхождения.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D004 Varzi & D007 Gilbert]:**
  * Монопольное владение циклами дизайна (`DesignShopOrchestrationService`) и изоляция продуктовых пространств (`ProductNamespaceLaw26`), институциональная фиксация требований в `WishlistService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XVI и XXIX в едином непрерывном запуске Docker Maven (272 теста):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XVI и XXIX) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(prescriptions-and-design): complete Stage 4 batch verification (D008, D010, D006)`.
