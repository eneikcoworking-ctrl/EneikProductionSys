# Пакетная директива L1/L3 -> L2: Секции XVI и XXIX — Реестр предписаний L2 и Цех дизайна

**Дата:** 2026-10-16 05:31 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий пакет Секций XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и Внешняя AI-граница фабрики», 215/215 green в коммите `b6f7f7d`) полностью заверен и закрыт инженером без единой строки правок рабочего кода. Настоящая директива охватывает следующий масштабный стратегический контур фабрики — **Секции XVI и XXIX («Реестр предписаний L2: 59 архитектурных предписаний» и «Цех дизайна: экраны, марка, геометрия и дизайн-система»)**:
   - **Контур XVI: Реестр 59 архитектурных предписаний фабрики:**
     * Демаркация полномочий, тождество сущностей и законы потока (Предписания 1–59);
     * Механизмы: `InternalGeminiObserverController`, `InternalGeminiObserverSecurityIntegrationTest`, `GeminiObserverActionService`, `TocSentinelController`, `TocSentinelService`, `LeanValue`, `TargetContext`, `RepositoryStackAnalyzer`, `OperationalTruthService`, `WishlistController`, `WishlistSimilarity`, `SettingsController`, `SystemSettingsService`, `FlywayMigrationValidation`, `ProductNamespaceLaw26Test`.
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
  * Лок уровней абстракции и родословная данных: происхождение дизайн-токенов (`declaredColors`, `declaredFonts`, `layoutMetrics`) строго фиксируется без подмешивания субъективных эвристик; `DesignConsistencyAuditService` вычисляет Jaccard-сходство и trace ratio объективно; разделение слоев: экранный драфт != утвержденный макет != клиентская поставка.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_01_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код в интерфейсах фабрики: блокировка деградации до картинок при требовании HTML (`requireImplementableHtml=true`), `InternalGeminiObserverSecurityIntegrationTest` и `ProductNamespaceLaw26Test` блокируют несанкционированный доступ и пересечение границ пространств имен.
- **`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` & `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` [D015 Modal necessity & aesthetic drift / Barcan Marcus & Belnap]:**
  * Модальная необходимость архитектурных инвариантов: 59 предписаний Секции XVI трактуются как жесткие деонтические законы фабрики, не подлежащие ревизии;
  * Единство трассировки токенов бренда: каждый визуальный элемент макета обязан соотноситься с объявленными токенами проекта.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика: пустые SPA-оболочки без базовых линий возвращают честный `CANNOT_JUDGE`, исключая искажение истинности.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Физическое состояние рантайма выше словесных нарративов: мониторинг дрейфа экрана живого продукта строго внутри открытого окна runtime-наблюдения без деструктивных перезапусков.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XVI и XXIX в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XVI и XXIX), актуализировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(prescriptions-and-design): complete Stage 4 batch verification (D008, D010, D006)`.
