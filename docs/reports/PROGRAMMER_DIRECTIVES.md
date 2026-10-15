# Пакетная директива L1/L3 -> L2: Секция XVII и XVIIб (Обездвиженное, системные конфигурации и открытая поверхность чтения)

**Дата:** 2026-10-15 19:37 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XIII («Взаимодействие: кто пишет каждое состояние») полностью заверена и закрыта инженером (74/74 green в коммите `d6ecc05`). Настоящая директива охватывает целиком следующее открытое семейство конвейера фабрики — **Секцию XVII («Что здесь мертво или обездвижено») и Секцию XVIIб («Двадцать один механизм, остававшийся неназванным»)**:
   - **Контур XVII.1. `GeminiProjectObserverService` как инертный совместимый stub (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006, `DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` / D004):**
     * Метод `runObserverCycle()` перманентно инертен, не содержит зависимостей, API-вызовов Gemini и шедулеров;
     * Заслон в `GeminiProjectObserverServiceTest` и `V58AndV111ObserverDecommissionFalsificationTest`.
   - **Контур XVII.2. `gemini_project_observer_enabled` и `V111__permanently_disable_gemini_project_observer.sql` (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006):**
     * Системный запрет зафиксирован миграцией V111 со значением `false` и registry в `SystemSettingsService`;
     * Заслон в `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green) и `InternalGeminiObserverSecurityIntegrationTest` (9/9 green).
   - **Контур XVII.3. `ChessService` как пустой производственный носитель (`DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` / D004):**
     * Файл `src/main/java/com/eneik/production/services/ChessService.java` имеет 0 строк и байт; продуктовая логика исключена из фабричного неймспейса;
     * Заслон в `TechnicalLeadCompilerIntegrationTest` (линия 242: проверка исключения шахматного сервиса из scope задач).
   - **Контур XVII.4. Gemini PR review disablement and Jules fallback review (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011):**
     * Запрет вызова прямого Gemini review в `JulesDispatchService.executeCodeReview` с переводом в `PendingFallbackReview` и обязательным прогоном `dispatchReviewerFallbackBatch`.
   - **Контур XVIIб. Системные конфигурации и открытая поверхность чтения:**
     * `SchedulingConfig` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): единый пул планировщика (poolSize = 10) для 41 аннотации `@Scheduled` без голодания задач (`SchedulingConfigTest`, 2/2 green);
     * `TocAnomalyDetector` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008): детерминированное выявление застрявших токенов и циклических реентрантных аномалий (`TocOptimizerTest`, `TocSentinelServiceTest`);
     * `DefectJournalService` (`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007, `KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010): централизованная регистрация дефектов (`DefectJournalServiceTest`);
     * `GithubConfig` (`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D001): единый источник токена, API URL и организации (`GithubConfigTest`);
     * `GlobalExceptionHandler` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): безопасный перехват 400/404/405/409 и сетевых обрывов без утечки стеков (`GlobalExceptionHandlerTest`, 3/3 green);
     * `WebConfig` & `GreetingMapper` (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006): CORS-топология с запретом wildcard при credentials (`WebConfigCorsTest`, 3/3 green);
     * Пятнадцать открытых read-поверхностей контроллеров (`ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `HomeController`, `InternalJulesActivitiesProbeController`, `JulesMonitorController`, `LinearSyncController`, `OperationalTruthController`, `QualityGateController`, `QualityMetricsController`, `RoleRulesController`, `SystemAuditController`, `SystemDriftController`, `VerdictController`, `SystemStatusController`): строгая демаркация GET/no-mutation проекций без смешения уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов и контроллеров (`src/main/java`) при проходящих тестах!
   - Механизмы Секции XVII и XVIIб уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D007, D012, D011, D001)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны обездвиженных механизмов (`GeminiProjectObserverServiceTest`, `V58AndV111ObserverDecommissionFalsificationTest`) обязаны опровергать ложноположительные суждения; любая попытка оживить инертный цикл или снять запрет обязана завершаться отказом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгое разделение уровней агрегации суждений, открытых GET-проекций контроллеров и сущностей базы данных; открытые read-поверхности отдают проекции, не подменяя доменные сущности.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz / von Wright]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров Секции XVII/XVIIб при проходящих тестах;**
  * Запрет как исполняемый код: инертность `GeminiProjectObserverService`, `V111` и исключение `ChessService`.
- **`DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict, Shaffer / Varzi]:**
  * Актуальный объектный реестр: код существует только для актуальных доменных объектов; пустые файлы и заглушки строго демаркированы.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Санитарная изоляция ошибок в `GlobalExceptionHandler` с разделением 4xx и 5xx без падения сервера.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Stale belief, Dretske]:**
  * Сигнал меняет действие: запрет Gemini PR review транслируется в обязательный Jules fallback batch review.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XVII и XVIIб в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,SchedulingConfigTest,WebConfigCorsTest,GlobalExceptionHandlerTest,DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest`
2. Зафиксировать результаты в `*Живое:*` блоков Секции XVII и XVIIб в `docs/FACTORY_MECHANISMS.md` и обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(decommission-and-surface): complete Stage 4 batch verification (D008, D010, D006)`.
