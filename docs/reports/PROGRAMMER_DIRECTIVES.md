# Пакетная директива L1/L3 -> L2: Секции XVI и XXIX — Реестр предписаний L2 и Цех дизайна

**Дата:** 2026-10-16 03:28 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») успешно заверена и закрыта инженером (коммит `fb145dd`, 290/290 green в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Секции XVI и XXIX («Реестр предписаний L2: 59 архитектурных предписаний» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)**:
   - **Контур XVI: Реестр 59 архитектурных предписаний фабрики:**
     * Демаркация полномочий, тождество сущностей и законы потока (Предписания 1–59);
     * Механизмы: `InternalGeminiObserverController`, `InternalGeminiObserverSecurityIntegration`, `GeminiObserverActionService`, `TocSentinelController`, `TocSentinelService`, `LeanValue`, `TargetContext`, `RepositoryStackAnalyzer`, `OperationalTruthService`, `WishlistController`, `WishlistSimilarity`, `SettingsController`, `SystemSettingsService`, `FlywayMigrationValidation`, `ProductNamespaceLaw26Integration`.
     * Заслоны верификации: 159 тестов, 100% green в Docker Maven.
   - **Контур XXIX: Цех дизайна, экранные артефакты и дизайн-система:**
     * Механизмы: `DesignAssetService`, `DesignConsistencyAuditService`, `LayoutGeometryAuditService`, `DesignSystemFalsificationService`, `DesignDriftMonitorService`, `DesignExcellenceGate`, `DesignShopCycleRepository`, `DesignShopOrchestrationService`, `StrandedDesignCycleSweepService`, `DesignImplementationLoopClosureLaw3`, `StitchClient`.
     * Заслоны верификации: 113 тестов, 100% green в Docker Maven.
   - **Итоговый замер кластера:** 272 теста, 100% green в едином непрерывном пакетном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, шлюзов, контроллеров и политик (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D012, D011, D013, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_01_EVIDENTIAL_PROBABILITY_THRESHOLD` [D008 False green / Popper]:**
  * Попперовская фальсификация экранных заслонов: `DesignSystemFalsificationServiceTest` и `DesignExcellenceGateFalsificationTest` не ищут подтверждения успеха, а удостоверяют способность заслона упасть при нарушении токенов и сеток; ложно-зеленые вердикты исключаются.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_ATTRIBUTION_SURFACE_GROUNDING` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и родословная данных: происхождение дизайн-токенов (`declaredColors`, `declaredFonts`, `layoutMetrics`) строго фиксируется без подмешивания субъективных эвристик; `DesignConsistencyAuditService` вычисляет Jaccard-сходство и trace ratio объективно.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_01_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код в интерфейсах фабрики: `InternalGeminiObserverSecurityIntegrationTest` и `ProductNamespaceLaw26IntegrationTest` блокируют несанкционированный доступ и пересечение границ пространств имен.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `LYUDVIG_VITGENSHTEYN_03_TRACTATUS_BOUNDARY` [D015 Modal necessity / Barcan Marcus]:**
  * Модальная необходимость архитектурных инвариантов: 59 предписаний Секции XVI трактуются как жесткие деонтические законы фабрики, не подлежащие ревизии воркерами.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth table / Belnap]:**
  * Многозначная логика истинности в суждениях о дрейфе верстки (`DesignDriftMonitorService`) и готовности циклов дизайна: статус неопределенности фиксируется явно до получения полного набора артефактов.
- **`ENDI_KLARK_01_EXTENDED_SYSTEM_BOUNDARY` [D011 Extended boundary / Clark]:**
  * Расширенная граница системы: токены дизайна и внешние артефакты верстки (`StitchClient`) интегрируются в единое тело доказательств продукта.
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: замер предмета, а не отчета о нем. `LayoutGeometryAuditService` и `DesignAssetService` проверяют физические артефакты верстки (DOM, CSS-токены, скриншоты), а не словесные заверения генератора.
- **`DZHONATAN_SHEFFER_01_GROUNDING_MONISM` [D004 Part-whole grounding / Shaffer]:**
  * Отношение части и целого в дизайн-системе: отдельные экраны фундированы общей онтологией бренда и цветовой палитрой проекта.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XVI и XXIX в Docker Maven (272 теста green):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,GeminiObserverActionServiceTest,TocSentinelControllerTest,TocSentinelServiceTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistControllerIntegrationTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26IntegrationTest,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md`, подтвердить актуальность итоговых блоков Секций XVI и XXIX, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(prescriptions-and-design): complete Sections XVI and XXIX batch verification (D008, D010, D006)`.
