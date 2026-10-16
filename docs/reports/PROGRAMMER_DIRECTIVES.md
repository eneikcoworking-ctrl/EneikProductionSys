# Пакетная директива L1/L3 -> L2: Кластер Секций XVII, XVIIб, XIX и XX — Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища

**Дата:** 2026-10-16 04:07 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий стратегический кластер Секций XIV, XV и XVI («Философский корпус, защита от размножения задач и 59 предписаний») успешно заверен, подтвержден и закрыт инженером (коммит `08b42a6`, 224/224 green в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища»)**:
   - **Контур XVII и XVIIб: Обездвиженное ядро и неназванная периферия контроллеров (57 тестов, 100% green):**
     * Механизмы XVII: `GeminiProjectObserverService` как инертный stub, `gemini_project_observer_enabled & V111`, `ChessService` как пустой носитель, `Gemini PR review disablement and Jules fallback review`.
     * Механизмы XVIIб: `SchedulingConfig`, `TocAnomalyDetector`, `DefectJournalService`, `GithubConfig`, `GlobalExceptionHandler`, `WebConfig`, `GreetingMapper`, а также 15 читающих контроллеров (`ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `HomeController`, `InternalJulesActivitiesProbeController`, `JulesMonitorController`, `LinearSyncController`, `OperationalTruthController`, `QualityGateController`, `QualityMetricsController`, `RoleRulesController`, `SystemAuditController`, `SystemDriftController`, `VerdictController`, `SystemStatusController`) как строгие GET/no-mutation проекции.
     * Заслоны: `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4), `QualityGateControllerTest` (2/2), `QualityMetricsControllerTest` (3/3), `HomeControllerTest` (1/1), `LinearSyncControllerTest` (2/2), `SystemStatusControllerTest` (3/3), `GlobalExceptionHandlerTest` (3/3), `WebConfigCorsTest` (3/3), `SchedulingConfigTest` (2/2), `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `V58AndV111ObserverDecommissionFalsificationTest` (6/6), `GeminiProjectObserverServiceTest` (1/1) — 57/57 green.
   - **Контур XIX: Сайдкары вне бэкенда (51 тест, 100% green):**
     * Механизмы: `runtime-launcher/launcher.py` / `RuntimeLauncherClient`, `judgment-proxy/server.js` / `JudgmentAgentClient`, `src/models/ml/PredictionService.py` / `MLPredictionServiceClient`.
     * Заслоны: `ClientRuntimeObservabilityServiceTest` (29/29), `ObservationHostingDemarcationLaw26Test` (3/3), `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4), `MLPredictionServiceClientTest` (9/9) — 51/51 green.
   - **Контур XX: Хранилища, которые держат поток (50 тестов, 100% green):**
     * Механизмы: `DesignShopCycleRepository`, `ClaimRepository` / `TaskClaimService`, `ProjectEventLogRepository`, `ContextChunkRepository`, `GreetingRepository`.
     * Заслоны: `DesignShopCycleRepositoryIntegrationTest` (6/6), `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6), `ProjectEventLogRetentionServiceTest` (10/10), `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10), `GreetingControllerIntegrationTest` (5/5) — 50/50 green.
   - **Итоговый единый пакетный замер кластера:** 158 тестов (57 + 51 + 50), 100% green в едином непрерывном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, контроллеров, репозиториев, сайдкаров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D011, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: падение заслонов при попытке реактивации выключенных компонентов (`V58AndV111ObserverDecommissionFalsificationTest`), нарушении топологии хостинга наблюдений (`ObservationHostingDemarcationLaw26Test`), невыполнении максим суждения (`JudgmentAgentClientLaw17Test`), гонках притязаний на задачи (`ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`) и коллизиях кэша корпуса (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: 15 читающих контроллеров демаркированы как чистые GET/no-mutation проекции; строгая изоляция демо-контура `GreetingRepository` от Lean/TOC метрик хребта фабрики; сохранение происхождения данных в `MLPredictionServiceClientTest` и `ProjectEventLogRetentionServiceTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как исполняемый код и топология границ: выключение наблюдателя через миграцию V111, изоляция host docker socket в `RuntimeLauncherClient`, защита внутренних эндпоинтов в `InternalGeminiObserverSecurityIntegrationTest`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` [D004 Boundary drift / Shaffer]:**
  * Монопольное владение старт-циклами дизайн-цеха в `DesignShopCycleRepositoryIntegrationTest`, фиксация фактических объектов совместимости Spring Context (`ChessService`, `GeminiProjectObserverService`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Разделение категорий сущностей притязаний на задачи и владения сессиями в `TaskClaimServiceTest`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Корректная обработка исключений и отсутствие ложных положительных исходов в `GlobalExceptionHandlerTest`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Dretske]:**
  * Телеосемантическая обратная связь в детекторе аномалий потока TOC (`SchedulingConfigTest`).
- **`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact / Tarski]:**
  * Институциональный учет фактов дефектов в `DefectJournalService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XVII, XVIIб, XIX и XX в Docker Maven (158 тестов green):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
   ```
2. Подтвердить статус `ideal` в блоках `### Итог Секции XVII и XVIIб`, `### Итог Секции XIX`, `### Итог Секции XX` в `docs/FACTORY_MECHANISMS.md`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(sections-xvii-xix-xx): complete Stage 4 batch verification (D008, D010, D006)`.
