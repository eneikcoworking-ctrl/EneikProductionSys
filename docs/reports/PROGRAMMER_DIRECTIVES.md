# Пакетная директива L1/L3 -> L2: Секции XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием»)

**Дата:** 2026-10-16 01:20 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий кластер (Секции IX, X и XI: «Внешние системы, Дизайн-цех и Наблюдение за клиентом») полностью заварен и закрыт инженером (коммит `f13678c`, 280/280 green в едином прогоне в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секции XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)**:
   - **Секция XII («Журнал, память, санитария: буферизация логов, удержание памяти, факт-регистр настроек и предохранители»):**
     * `LogScope`, `ScopedBufferAppender`, `LogScopeBuffer`: MDC-демаркация контекстов `PROJECT:{id}` и `SYSTEM`, отсечение шума, циклическая защита памяти (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `ProjectLogFlushQueue`, `ProjectEventLogService`, `ProjectEventLogRetentionService`: безопасное дренирование очереди, удержание истории по смыслу и статусу проекта, исключение OOM (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * `SystemSettingsService`: институциональный факт-регистр настроек, валидация ключей, обязательный неизгладимый след в defect_journal с маскированием секретов (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007);
     * `IdleProjectAdviceService`, `RoleAdviceLoopService`: покэ-йокэ предохранители простоя и ролей с подавлением паразитных вишлистов вне суточного цикла (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * `RoleCapabilityLoader`, `RoleRulesParser`, `JulesRoleCapabilities`, `TaskTitleBuilder`, `ProjectTreeService`, `ProjectAuditPipelineService`: ригидная нормализация заголовков задач и неизменяемые capabilities ролей (`GARET_EVANS_01_RIGID_API_REFERENT` / D003).
   - **Секция XIIа («Управляющая поверхность: чем поток останавливают снаружи»):**
     * `AccountController`: маскирование секретов в DTO, деонтический запрет неавторизованного отключения аккаунтов (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * `ProjectController`: институциональная фиксация статусов, отказ 409 Conflict (`OperationalPolicyDeniedException`) при попытке нарушить переход (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007);
     * `ClaimController`: матрица прав и обязанностей, атомарные CAS-предикаты `writeStatusUnlessTerminal`, Закон 16 (Belnap 4-valued review admission) (`PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` / D007, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * `WishlistController`: разграничение мягкого архивирования (dismiss) с сохранением знаменателя и санитарного удаления фантомов (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * `SettingsController` + `SystemStatusController`: защита изменяющих поверхностей (X-API-Key, 401 при отсутствии ключа), изоляция `/sql` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `InternalTaskController`: пагинация Pageable, точный поиск по Linear ID, исключение неконтролируемых дампов задач (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
     * `DashboardController`, `GithubWebhookController`, `GreetingController`, `LinearSyncController`: четкое разграничение телеметрии и продуктовой готовности (`KRISPIN_RAYT_01_SUBSTITUTION_ORACLE` / D009, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
   - **Секция XIII («Взаимодействие: кто пишет каждое состояние»):**
     * `Factory Writable State Owner-Map` (XIII.1): монопольное владение пишущими репозиториями (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
     * `Wishlist Transition Boundary` (XIII.2): изоляция переходов требований через CAS (`MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` / D004);
     * `Task Transition Boundary & Law 20` (XIII.3): необратимость терминальных исходов задач, защита от воскрешения завершенных задач (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * `Jules Session Lifecycle Ownership` (XIII.4): монопольный жизненный цикл сессий через `SessionLifecycleService` (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * `Interacting Services Demarcation` (XIII.5): демаркация зон ответственности взаимодействующих сервисов без скрытых мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и репозиториев (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XII, XIIа и XIII уже отлажены и покрыты заслонами. Задача инженера L2 — подтвердить нерушимость инвариантов строгим единым пакетным замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D007, D012, D011, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификационные заслоны необратимости терминальных исходов задач (Закон 20: `AutoMergeLaw20InvariantS4Test`), циклических буферов (`ScopedBufferAppenderFalsificationTest`), и удержания сессий (`SessionLifecycleServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Каузальная цепочка свидетельств удержания журнала (`ProjectEventLogRetentionService`); сохранение знаменателя доставки при архивации требований (`WishlistController`); лок уровней абстракции от контекста логов до институциональных сущностей.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity & boundary topology, Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов при проходящих тестах;**
  * Авторизационные заслоны управляющей поверхности: защита эндпоинтов `/api/settings` и `/sql`, отказ без ключа 401 UNAUTHORIZED, изоляция контекстов `LogScope`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` [D004 Part-whole ownership, Shaffer & Gilbert]:**
  * Монопольное владение переходами сущностей фабрики; исключение несанкционированных пишущих путей мимо ответственных репозиториев (`TaskRepositoryIntegrationTest`, `AccountRepositoryIntegrationTest`, `DesignShopCycleRepositoryIntegrationTest`).
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` & `PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` [D007 Institutional facts & constructive proofs, Searle & Martin-Löf]:**
  * Статусы проектов и системные настройки как институциональные факты; терминальные исходы клеймов требуют конструктивных доказательств (Закон 16).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Четырехзначная логика исходов клеймов (`ClaimResultStatusTruthTableFalsificationTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D011 & D013, Dretske & Wittgenstein]:**
  * Предохранители простоя и ролей как наблюдающие фильтры; анти-зеркало рантайма без генерации фиктивной работы.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секций XII, XIIа и XIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,AccountControllerIntegrationTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,SystemStatusControllerTest,InternalTaskControllerTest,DashboardControllerTest,GithubWebhookControllerTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,TaskRepositoryIntegrationTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секций XII, XIIа и XIII в `docs/FACTORY_MECHANISMS.md`, оформить блоки `### Итог Секции XII`, `### Итог Секции XIIа`, `### Итог Секции XIII` (или общий итог кластера), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(logging-control-and-ownership): complete Stage 4 batch verification (D008, D010, D006)`.
