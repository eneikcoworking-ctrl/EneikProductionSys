# Директивы и наряд для L2 (2026-10-16 08:40 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») успешно заверена инженером (коммит `655fb6c`, 290/290 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»)** целиком (26 тест-сьютов, 280 тестов в едином пакетном прогоне Docker Maven):
     * **Секция IX («Внешние системы: внешняя AI/RAG/Runtime-граница фабрики», 110 тестов):**
       - `GoogleAiResourceSecurityIntegrationTest` (12/12), `GoogleAiResourceControllerTest` (6/6), `GeminiContextServiceTest` (28/28), `StitchClientTest` (10/10), `DesignAssetServiceTest` (12/12), `GeminiObserverActionServiceTest` (17/17), `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9);
     * **Секция X («Дизайн-цех: экраны, марка, геометрия и дизайн-система», 91 тест):**
       - `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7), `DesignShopCycleRepositoryIntegrationTest` (6/6), `DesignConsistencyAuditServiceTest` (15/15), `LayoutGeometryAuditServiceTest` (13/13), `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7), `StrandedDesignCycleSweepServiceTest` (4/4), `DesignImplementationLoopClosureLaw3Test` (2/2), `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6);
     * **Секция XI («Наблюдение за живым продуктом клиента», 79 тестов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29), `ObservationHostingDemarcationLaw26Test` (3/3), `FailureDemarcationLaw22Test` (5/5), `BetaPosteriorTest` (9/9), `RuntimeHealthShiftDetectorTest` (9/9), `ProductCapabilityServiceTest` (18/18), `RuntimeVerdictLayerTest` (6/6).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть основательными, строгими и доказательными.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D012, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты заслонов обязаны воспроизводимо падать при подмене настоящей верстки заглушками, симуляции готовности экранов или нарушении байесовских порогов сдвига здоровья рантайма (`DesignExcellenceGateFalsificationTest`, `DesignSystemFalsificationServiceTest`, `FailureDemarcationLaw22Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: строгая демаркация сырых наблюдений за рантаймом клиента, эмпирических свидетельств доступности возможностей и агрегированных вердиктов (`ClientRuntimeObservabilityService`, `ProductCapabilityService`, `RuntimeVerdictLayer`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимые деонтические заслоны доступа к AI-ресурсам и действиям наблюдателя через `ApiAuthorizationInterceptor` и `OperationalPolicyService` (`GoogleAiResourceSecurityIntegrationTest`, `InternalGeminiObserverSecurityIntegrationTest`).
- **`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` & `RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` [D015 Aesthetic drift / Barcan Marcus]:**
  * Модальный актуализм и единство дизайн-токенов: исключение коммита в GitHub экранов, нарушающих заявленную палитру или шрифты; запрет подмены HTML на статичные картинки при требовании живой верстки.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: четкая демаркация сбоя инструмента наблюдения (`observed=false`) от реального сбоя продуктового контейнера (`answered=false`); пассивное наблюдение не оставляет побочных мутаций в продукте.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности в состояниях циклов дизайна и вердиктах готовности экранов.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Varzi]:**
  * Топологическая монополия владения состоянием циклов дизайн-цеха (`DesignShopCycleRepository`) и атомарная аренда шагов через pessimistic lock/start_cycle_claim.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций IX, X и XI:**
   - **Docker Maven (280 тестов, 26 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignShopCycleRepositoryIntegrationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
     ```
2. **Верификация результата:**
   - Строго **280/280 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций IX, X и XI в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций IX, X и XI в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-ix-x-xi): complete Stage 4 batch verification (D008, D010, D006)`.
