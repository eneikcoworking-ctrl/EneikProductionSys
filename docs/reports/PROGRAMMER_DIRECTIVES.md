# Пакетная директива L1/L3 -> L2: Секции XVI и XXIX — Реестр архитектурных предписаний L2 и Цех дизайна

**Дата:** 2026-10-16 02:30 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен и Оркестрация») успешно заверена инженером (коммит `515a553`, 281/281 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секцию XVI («Предписания для L2: что и как чинить: реестр архитектурных механизмов и заслонов качества»)** и **Секцию XXIX («Цех дизайна: порождение экранов и сверка их с маркой»)**:
   - **Контур XVI.Prescriptions (`Реестр 59 архитектурных предписаний фабрики`):**
     * Механизмы: `InternalGeminiObserverController`, `GeminiObserverActionService`, `TocSentinelService`, `TocSentinelController`, `LeanValue`, `TargetContext`, `RepositoryStackAnalyzer`, `OperationalTruthService`, `WishlistService`, `WishlistContentSimilarityMatcher`, `WishlistSimilarity`, `SettingsController`, `SystemSettingsService`, `FlywayMigrationValidation`, `ProductNamespaceLaw26`;
     * Инварианты: деонтический барьер наблюдателя (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`), нерушимость тянущего контура ТОС, изоляция проектных пространств (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`), четырехзначная таблица истинности Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE`), категориальное разделение внутреннего сбоя фабрики и факта о клиенте (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN`), детерминизм настроек и миграций базы данных;
     * Заслоны: `InternalGeminiObserverSecurityIntegrationTest`, `InternalGeminiObserverControllerTest`, `GeminiObserverActionServiceTest`, `TocSentinelServiceTest`, `TocSentinelControllerTest`, `LeanValueTest`, `TargetContextTest`, `RepositoryStackAnalyzerTest`, `OperationalTruthServiceTest`, `WishlistServiceFalsificationTest`, `WishlistContentSimilarityMatcherTest`, `WishlistSimilarityTest`, `SettingsControllerIntegrationTest`, `SystemSettingsServiceTest`, `FlywayMigrationValidationTest`, `ProductNamespaceLaw26Test`.
   - **Контур XXIX.DesignShop (`Цех дизайна: экраны, марка, геометрия и дизайн-система`):**
     * Механизмы: `DesignAssetService`, `DesignConsistencyAuditService`, `LayoutGeometryAuditService`, `DesignSystemFalsificationService`, `DesignDriftMonitorService`, `DesignExcellenceGate`, `DesignShopCycleRepository`, `DesignShopOrchestrationService`, `StrandedDesignCycleSweepService`, `DesignImplementationLoopClosureLaw3`, `StitchClient`;
     * Инварианты: запрет деградации до картинок при требовании HTML (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`, `LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE`), единство токенов марки (`RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY`), Jaccard-согласованность экранов (`LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE`), геометрический заслон вьюпорта Голдмана (`ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE`), фальсификационный заслон дизайн-системы Поппера/Уильямсона (`TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS`, `KARL_POPPER_01_FALSIFICATION_HARNESS`);
     * Заслоны: `DesignAssetServiceTest`, `DesignConsistencyAuditServiceTest`, `LayoutGeometryAuditServiceTest`, `DesignSystemFalsificationServiceTest`, `DesignDriftMonitorServiceTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `DesignShopCycleRepositoryIntegrationTest`, `DesignShopOrchestrationServiceTest`, `DesignShopOrchestrationServiceLaw15Test`, `StrandedDesignCycleSweepServiceTest`, `DesignImplementationLoopClosureLaw3Test`, `StitchClientTest`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, контроллеров и моделей (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D015, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green, Popper & Williamson]:**
  * Попперовская фальсификация: тест обязан падать при нарушении инвариантов геометрии, подмене дизайн-токенов или нарушении монопольного владения циклами дизайна.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: артефакты дизайна и токены марки не смешиваются со сгенерированным HTML-кодом; каузальная цепочка свидетельств гарантирует происхождение артефактов из проверяемых коммитов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет подмены HTML на изображения при установленном требовании `requireImplementableHtml=true`; деонтический заслон изменяющих операций наблюдателя.
- **`RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D015 Aesthetic drift, Barcan Marcus & Wittgenstein]:**
  * Единая трассировка токенов бренда и перекрёстная Jaccard-оценка экранов для защиты от визуальной и стилевой деградации.
- **`ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE` [D011 Perception failure, Goldman]:**
  * Геометрический заслон вьюпорта, проверка коллизий, перекрытий и близости элементов интерфейса.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Многозначная логика истинности в `OperationalTruthService` и гейтах верификации, исключающая приравнивание неизвестности к ложности.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Защита от категориальной ошибки: исключение подмены клиентских требований или дефектов продукта сбоями авторизации внутренней инфраструктуры фабрики.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Анти-зеркало телеметрии: ранняя фиксация дрейфа дизайна и расхождений живого рантайма против деклараций макета.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секций XVI и XXIX в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md`, внести/актуализировать блоки `### Итог Секции XVI`, `### Итог Секции XXIX` и `### Итог кластера Секций XVI и XXIX` со сводкой зелёных тестов, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(prescriptions-and-design-shop): complete Section XVI & XXIX batch verification (D008, D010, D006)`.
