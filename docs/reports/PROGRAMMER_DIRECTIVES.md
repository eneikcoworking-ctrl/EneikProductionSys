# Пакетная директива L1/L3 -> L2: Секция XVII и XVIIб («Обездвиженное, системные конфигурации и открытая поверхность чтения»)

**Дата:** 2026-10-16 01:50 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий кластер (Секции XIV, XV и XVI: «Сверка с корпусом философии, тихое размножение задач и реестр предписаний») полностью заварен и закрыт инженером (коммит `666f62b`, 224/224 green в едином прогоне в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает целиком следующий стратегический контур фабрики — **Секцию XVII («Что здесь мертво или обездвижено») и Секцию XVIIб («Двадцать один механизм, остававшийся неназванным»)**:
   - **Контур XVII.1. `GeminiProjectObserverService` как инертный совместимый stub (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006, `DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` / D004):**
     * Метод `runObserverCycle()` перманентно инертен, не содержит зависимостей, API-вызовов Gemini и шедулеров, сохраняя контракт совместимости Spring Context;
     * Заслон в `GeminiProjectObserverServiceTest` (1/1 green) и `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green).
   - **Контур XVII.2. `gemini_project_observer_enabled` и `V111__permanently_disable_gemini_project_observer.sql` (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006):**
     * Системный запрет зафиксирован миграцией V111 со значением `false` и реестром ключей в `SystemSettingsService`;
     * Заслон в `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green), `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green).
   - **Контур XVII.3. `ChessService` как пустой производственный носитель (`DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002):**
     * Файл `src/main/java/com/eneik/production/services/ChessService.java` имеет 0 строк и байт; логика исключена из фабричного пространства имён;
     * Заслон в `TechnicalLeadCompilerIntegrationTest` (линия 242: проверка исключения шахматного сервиса из scope задач).
   - **Контур XVII.4. Gemini PR review disablement and Jules fallback review (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011):**
     * Запрет вызова прямого Gemini review в `JulesDispatchService.executeCodeReview` с переводом в `PendingFallbackReview` и обязательным прогоном `dispatchReviewerFallbackBatch`.
   - **Контур XVIIб. Системные конфигурации и открытая поверхность чтения:**
     * `SchedulingConfig` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): единый пул планировщика (poolSize = 10) для 41 аннотации `@Scheduled` без голодания задач (`SchedulingConfigTest`, 2/2 green);
     * `TocAnomalyDetector` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008): детерминированное выявление застрявших токенов и циклических реентрантных аномалий;
     * `DefectJournalService` (`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007, `KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010): централизованная регистрация дефектов;
     * `GithubConfig` (`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D001): единый источник токена, API URL и организации;
     * `GlobalExceptionHandler` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): безопасный перехват 400/404/405/409 и сетевых обрывов без утечки стеков (`GlobalExceptionHandlerTest`, 3/3 green);
     * `WebConfig` & `GreetingMapper` (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006): CORS-топология с запретом wildcard при credentials (`WebConfigCorsTest`, 3/3 green);
     * Пятнадцать открытых read-поверхностей контроллеров (`ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `HomeController`, `InternalJulesActivitiesProbeController`, `JulesMonitorController`, `LinearSyncController`, `OperationalTruthController`, `QualityGateController`, `QualityMetricsController`, `RoleRulesController`, `SystemAuditController`, `SystemDriftController`, `VerdictController`, `SystemStatusController`): строгая демаркация GET/no-mutation проекций без смешения уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов и контроллеров (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секции XVII и XVIIб уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D007, D012, D011, D001)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны обездвиженных механизмов (`GeminiProjectObserverServiceTest`, `V58AndV111ObserverDecommissionFalsificationTest`) обязаны опровергать ложноположительные суждения; любая попытка оживить инертный цикл или снять запрет обязана завершаться отказом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Блокировка уровней абстракции: контроллеры чтения (`CommandDashboardController`, `DashboardController`, `HomeController`, `QualityGateController`, `QualityMetricsController`, `SystemStatusController`, `LinearSyncController`) являются проекциями состояния и не имеют права мутировать агрегаты.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz & von Wright]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров при проходящих тестах;**
  * Запрет как исполняемый код: V111 фиксирует `gemini_project_observer_enabled=false`; `InternalGeminiObserverSecurityIntegrationTest` пресекает несанкционированные вызовы; `WebConfigCorsTest` блокирует открытый CORS при credentials.
- **`DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership, Shaffer & Varzi]:**
  * Актуальный реестр объектов: сохранение `GeminiProjectObserverService` как совместимого stub в DI-контейнере без права на исполнение; исключение `ChessService` (0 байт) из компилятора задач; пулинг `SchedulingConfig`.
- **`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact, Tarski]:**
  * Регистрация системных сбоев и аномалий в `DefectJournalService` как институциональных фактов фабрики.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Четырехзначная логика истинности в `GlobalExceptionHandler` с детерминированным разделением клиентских ошибок (400 Bad Request, 404 Not Found, 409 Conflict) и отказов инфраструктуры (500 Internal Error) без утечки внутренних стеков.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback, Dretske]:**
  * Обратная связь ТОС в `TocAnomalyDetector` и Jules fallback code review при отключении Gemini.
- **`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` [D001 Semantic drift, Chalmers]:**
  * Разделение смысла и референции: `GithubConfig` как единый нормативный источник конфигурации GitHub API.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции XVII и XVIIб в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,SchedulingConfigTest,WebConfigCorsTest,GlobalExceptionHandlerTest,DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секции XVII и XVIIб в `docs/FACTORY_MECHANISMS.md`, подтвердить блок `### Итог Секции XVII и XVIIб` (57/57 green), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(decommission-and-surface): complete Stage 4 batch verification (D008, D010, D006)`.
