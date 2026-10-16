# Пакетная директива L1/L3 -> L2: Секции XVI и XXIX — Реестр 59 архитектурных предписаний и Цех дизайна (экраны, марка, геометрия и дизайн-система)

**Дата:** 2026-10-16 06:32 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью заверена и закрыта инженером (коммит `c5c70bf`, 290/290 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива открывает пакетный замер следующего стратегического контура фабрики — **Секция XVI («Реестр 59 архитектурных предписаний L2») и Секция XXIX («Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком:
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
     * `DesignShopOrchestrationService`, `DesignShopOrchestrationServiceLaw15`, `DesignImplementationLoopClosureLaw3` (13 + 7 + 2 green) — управление фазами дизайн-цикла без утечки незавершённых сессий (D004 Varzi).
     * `StitchClient` & `StrandedDesignCycleSweepService` (10 + 4 green) — интеграция со Stitch MCP и автоматическая сборка зависших циклов (D010 Goldman).
     * `DesignShopCycleRepositoryIntegrationTest` (6 green) — персистентность и атомарность циклов дизайна в БД (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, контроллеров, политик и хранилищ (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D015)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене токенов, искажении геометрии, пропуске невалидных миграций или нарушении архитектурных предписаний.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: строгая изоляция проектных пространств имен, каузальная трассировка дизайн-токенов, сохранение источника и временной метки для каждого действия наблюдателя.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: деонтические барьеры доступа к изменяющим эндпоинтам контроллеров и наблюдателя (`ApiAuthorizationInterceptor`, `InternalGeminiObserverSecurityIntegrationTest`), исключающие мутации без авторизации.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary / Varzi]:**
  * Топологические границы и монопольное владение: изоляция продуктовых пространств имен (`ProductNamespaceLaw26`), атомарность переходов состояний дизайн-циклов и миграций базы данных.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: физические метрики рантайма продукта и сгенерированные артефакты разметки выше любого промежуточного отчета; расхождение макета и кода отслеживается на уровне DOM/CSS.
- **`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` & `NUEL_BELNAP_18_CROSS_SCREEN_JACCARD_GATE` [D015 Aesthetic drift / Belnap]:**
  * Единство трейса токенов и кросс-экранная связность: валидация соответствия каждого цвета и шрифта declared token set проекта, пресечение эстетического дрифта.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XVI и XXIX в едином непрерывном запуске Docker Maven (все 29 тест-сьютов, 272 теста):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,SettingsControllerIntegrationTest,TocSentinelServiceTest,TocSentinelControllerTest,FlywayMigrationValidationTest,WishlistServiceFalsificationTest,ProductNamespaceLaw26Test,WishlistSimilarityTest,SystemSettingsServiceTest,WishlistContentSimilarityMatcherTest,RepositoryStackAnalyzerTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,LeanValueTest,TargetContextTest,StitchClientTest,DesignImplementationLoopClosureLaw3Test,DesignShopOrchestrationServiceTest,DesignSystemFalsificationServiceTest,DesignConsistencyAuditServiceTest,DesignDriftMonitorServiceTest,DesignShopOrchestrationServiceLaw15Test,LayoutGeometryAuditServiceTest,DesignAssetServiceTest,StrandedDesignCycleSweepServiceTest,DesignExcellenceGateFalsificationTest,DesignExcellenceGateTest,DesignShopCycleRepositoryIntegrationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XVI и XXIX) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(prescriptions-and-design): complete Stage 4 batch verification (D008, D010, D006)`.
