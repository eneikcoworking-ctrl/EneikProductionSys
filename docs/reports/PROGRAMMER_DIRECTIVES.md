# Пакетная директива L1/L3 -> L2: Секции XXX–XXXVI — Внешние клиенты, завод проектов, командный пульт, авторизация наблюдателя, выбор аккаунтов и хранилище задач

**Дата:** 2026-10-16 05:38 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий пакет Секций XVI и XXIX («Реестр предписаний L2 и Цех дизайна», 272/272 green в коммите `20898d5`) полностью заверен и закрыт инженером без единой строки правок рабочего кода. Настоящая директива охватывает следующий масштабный стратегический контур фабрики — **Секции XXX–XXXVI («Внешние клиенты, завод проектов, командный пульт, авторизация наблюдателя, выбор аккаунтов и хранилище задач»)**:
   - **Секция XXX: Клиент к предсказателю (MLPredictionServiceClient):**
     * Единая Java-дверь к ML-сайдкару: fail-closed предохранитель (breaker), возврат строго null при сбоях без фиктивных mock-векторов, изоляция от несанкционированного Gemini fallback;
     * Заслоны: `MLPredictionServiceClientTest`.
   - **Секция XXXI: Разбор чужого репозитория (RepositoryStackAnalyzer & OnboardingAuditService):**
     * Неудача фабрики не становится фактом о заказчике: трехзначный `TraversalStatus` (`ACCESSED`, `AUTH_FAILED`, `ACCESS_FORBIDDEN`), возврат 0 находок о заказчике при отсутствии доступа;
     * Заслоны: `RepositoryStackAnalyzerTest`, `OnboardingAuditServiceTest`.
   - **Секция XXXII: Завод репозиториев (ProjectFactoryService & Clients):**
     * Адрес репозитория существует только после физического GitHub-свидетельства (201 create / 200 GET); при ошибке/пропуске пишется строгий null (запрет фантомных URL); изоляция корня workspace (`boundaryTopologyRejectsPathTraversalOutsideRoot`);
     * Заслоны: `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`, `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`.
   - **Секция XXXIII: Пульт управления (CommandDashboardService & Controller):**
     * Готовность нельзя объявить без клиентского свидетельства, детерминированные проекции агрегатов без N+1 и сбоев;
     * Заслоны: `CommandDashboardServiceTest`, `CommandDashboardControllerTest`.
   - **Секция XXXIV: Внутренний вход наблюдателя (ApiAuthorizationInterceptor):**
     * Запрет является исполнимой границей: деонтический барьер на все 5 мутирующих путей AI-ресурсов (401/403), нормализация путей и институциональный аудит актора;
     * Заслоны: `ApiAuthorizationInterceptorTest`.
   - **Секция XXXV: Выбор аккаунта (AccountRepository & AccountHealthService):**
     * Наказание порядком, а не исключением; детерминированная привязка и распределение емкости с защитой от деградации пула;
     * Заслоны: `AccountRepositoryFalsificationTest`, `AccountRepositoryIntegrationTest`, `AccountHealthServiceTest`.
   - **Секция XXXVI: Хранилище задач (TaskRepository & TaskEntity):**
     * Запрет, стоящий на обоих уровнях: защита терминальных состояний (CAS), разделение признака carrier, заслон на утечки идентификаторов и гонки притязаний;
     * Заслоны: `TaskRepositoryIntegrationTest`, `TaskEntityLaw20Test`, `TaskEntityLaw1CarrierTest`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, шлюзов, контроллеров и политик (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004, D009, D002, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация заслонов: `ProjectWorkspaceFactoryServiceFalsificationTest` и `ProjectFactoryServiceFalsificationTest` удостоверяют способность заслона упасть при попытке path traversal или подстановке фиктивного адреса; `AccountRepositoryFalsificationTest` фальсифицирует некорректное распределение емкости.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная родословная данных: происхождение адреса репозитория подтверждается только внешним HTTP-свидетельством GitHub; разделение слоев: фабричный сбой онбординга != факт об отсутствии тестов у заказчика; выбор аккаунтов детерминирован по метрикам здоровья.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: `ApiAuthorizationInterceptor` защищает эндпоинты модели и черновики дизайна; топология пространственно-временных границ Варци жестко ограничивает операции пределами `workspaceRoot`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 Truth status & D009 Substitution / Belnap]:**
  * Четырехзначная логика истинности: статус доступа `TraversalStatus` четко отделяет отказ доступа от продуктовых дефектов; при отсутствии репозитория пишется строгий `null`, исключая подмену оракула (phantom URL).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership]:**
  * Владение частями и целым: хранилище задач `TaskRepository` и сущность `TaskEntity` защищают терминальные статусы через CAS-методы (`writeStatusUnlessTerminalAt`).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXX–XXXVI в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardServiceTest,CommandDashboardControllerTest,ApiAuthorizationInterceptorTest,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,AccountHealthServiceTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
2. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XXX–XXXVI), актуализировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(external-clients-and-task-store): complete Stage 4 batch verification (D008, D010, D006)`.
