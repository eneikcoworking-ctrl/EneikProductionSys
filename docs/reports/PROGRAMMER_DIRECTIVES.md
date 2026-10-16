# Пакетная директива L1/L3 -> L2: Секции XXX–XXXVI («Внешние клиенты, завод проектов, командный пульт, авторизация наблюдателя, выбор аккаунтов и хранилище задач»)

**Дата:** 2026-10-16 00:10 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XXVI, XXVII, XXVIII и XXIX полностью заверены и закрыты инженером (коммит `cf39773`, 215/215 green). Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секции XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI**:
   - **XXX. Клиент к предсказателю: единственная дверь фабрики к внешней модели:**
     * `MLPredictionServiceClient` (единая Java-дверь к FastAPI ML-сайдкару с предохранителем breaker, fail-safe возвратом `null` при сбоях и защитой от нулевых/фиктивных векторов `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * `src/models/ml/PredictionService.py` (FastAPI-сайдкар) и `AiHealthTracker` (`SystemStatusService.aiHealth`).
   - **XXXI. Разбор чужого репозитория: неудача фабрики не становится фактом о заказчике:**
     * `RepositoryStackAnalyzer.analyze` и `RepositoryStackAnalyzer.ownerFromRepositoryUrl` (сбои разбора стека фабрикой не атрибутируются заказчику как дефекты его репозитория `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * `OnboardingAuditService` и `OnboardingAuditFindingRepository` (строгая изоляция аудита онбординга `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`).
   - **XXXII. Завод репозиториев: адрес существует только после GitHub-свидетельства:**
     * `GitHubProjectFactoryClient.provision` и `LinearProjectFactoryClient` (адрес репозитория возникает строго по факту свидетельства GitHub, фантомные URL исключены `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ProjectFactoryServiceFalsificationTest`);
     * `ProjectFactoryService` и `ProjectWorkspaceFactoryService` (строгое разделение локального текста workspace, идентичности GitHub и статуса фабрики `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
   - **XXXIII. Пульт управления: готовность нельзя объявить без клиентского свидетельства:**
     * `CommandDashboardController` и `CommandDashboardService` (готовность поставки клиенту требует зафиксированного свидетельства приемки `acceptanceReadiness.clientAcceptanceWitnessed`, сохранение 3-значной логики Белнапа `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012: `unknown != not_ready != ready`, `CommandDashboardControllerTest`, `CommandDashboardServiceTest`).
   - **XXXIV. Внутренний вход наблюдателя: запрет является исполнимой границей:**
     * `WebConfig.addInterceptors` и `ApiAuthorizationInterceptor` (деонтические запреты доступа 401/403 по API-ключам `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, топологические границы Varzi D006, `ApiAuthorizationInterceptorTest`);
     * `InternalGeminiObserverController` (демаркация прав и изоляция наблюдателя).
   - **XXXV. Выбор аккаунта: наказание порядком, а не исключением:**
     * `AccountRepository.lockNextJulesAccountWithCapacity` (ротация аккаунтов по честности, пенализация переносом в хвост очереди без исключения из пула `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AccountSelectionFairnessTest`);
     * `AccountHealthService` (ошибки сторонних провайдеров не инкриминируются аккаунту как его вина `RequestRejectionIsNotTheAccountsFaultTest`, `AccountHealthServiceTest`, `AccountHealthServiceFalsificationTest`, `AccountLifecycleInvariantTest`).
   - **XXXVI. Хранилище задач: запрет, стоящий на обоих уровнях:**
     * `TaskStatus.isTerminal` и `TaskEntity.setStatus` (иммутабельность терминальных статусов задач, защита от перезаписи на уровне доменной модели и на уровне SQL-репозитория `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `TaskTerminalOverwriteAuditTest`, `TaskRepositoryIntegrationTest`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XXX–XXXVI находятся в полностью работоспособном состоянии (145/145 green в Docker Maven). Задача L2 — подтвердить нерушимость заслонов строгим замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация ложных статусов готовности и фантомных адресов: `ProjectFactoryServiceFalsificationTest` фальсифицирует появление несуществующего репозитория без ответа GitHub API; `OnboardingAuditServiceFalsificationTest` фальсифицирует подмену онбординговых находок.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность происхождения свидетельств: URL репозитория существует только после фиксации в GitHub;
  * Лок уровней абстракции: ошибки фабричного сканирования не превращаются в изъяны кода заказчика; статус аккаунта Jules не деградирует из-за сетевых сбоев сторонней платформы.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров при проходящих тестах;**
  * Деонтические запреты рантайма: строгая блокировка неавторизованного доступа к внутренним API наблюдателя (401/403 в `ApiAuthorizationInterceptor`); запрет перезаписи терминальных статусов задач (`TaskTerminalOverwriteAuditTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Трехзначная логика истинности в командном пульте `CommandDashboard`: состояние приемки заказчиком четко разделяет `unknown` (нет свидетельств), `not_ready` (свидетельства неполны) и `ready` (свидетельство получено).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Живая телеметрия рантайма и реальное состояние базы данных превыше словесных нарративов; пульты и наблюдатели работают в режиме чтения без побочных эффектов.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXX–XXXVI в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,CommandDashboardControllerTest,CommandDashboardServiceTest,ApiAuthorizationInterceptorTest,AccountLifecycleInvariantTest,AccountControllerIntegrationTest,AccountHealthServiceTest,AccountHealthServiceFalsificationTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountSelectionFairnessTest,TaskTerminalOverwriteAuditTest,TaskRepositoryIntegrationTest
   ```
2. Зафиксировать результаты в `*Живое:*` блоков Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секций XXX–XXXVI`, обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(clients-factory-and-tasks): complete Stage 4 batch verification (D008, D010, D006)`.
