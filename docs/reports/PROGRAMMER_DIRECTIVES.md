# Директивы и наряд для L2 (2026-10-16 08:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента») успешно заверен инженером (коммит `1fb4450`, 280/280 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием»)** целиком (31 тест-сьют, 189 тестов в едином пакетном прогоне Docker Maven):
     * **Секция XII («Журнал, память, санитария», 64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `ScopedBufferAppenderFalsificationTest` (13/13) — буферизация логов по скоупам, разделение системных и проектных журналов, изоляция MDC-контекстов, защита от потери INUS-причин сбоев;
       - `ProjectEventLogRetentionServiceTest` (10/10) — ротация и очистка журнала событий проекта с жесткими пределами хранения и детерминированным триммингом;
       - `SystemSettingsServiceTest` (13/13) — инварианты системных настроек и конфигурационных флагов;
       - `RoleCapabilityLoaderTest` (4/4) — валидация прав и возможностей ролей Jules без смешения уровней ответственности;
       - `ProjectAuditPipelineServiceTest` (4/4) — аудит пайплайна проекта и соответствие уставным требованиям;
       - `IdleProjectAdviceServiceTest` (6/6), `RoleAdviceLoopServiceTest` (2/2), `RoleAdviceLoopServiceIntegrationTest` (1/1) — контур рекомендаций для неактивных проектов и ролевых петель.
     * **Секция XIIа («Управляющая поверхность: чем поток останавливают снаружи», 74 теста, 11 сьютов):**
       - `AccountControllerIntegrationTest` (5/5), `InternalTaskControllerTest` (6/6), `WishlistControllerIntegrationTest` (8/8), `SettingsControllerIntegrationTest` (8/8) — изменяющие эндпоинты контроллеров, авторизация мутаций, предотвращение несанкционированных остановок потока;
       - `GreetingControllerIntegrationTest` (5/5), `LinearSyncControllerTest` (2/2), `GithubWebhookControllerTest` (9/9), `SystemStatusControllerTest` (3/3), `DashboardControllerTest` (2/2) — периферия внешних синхронизаций, безопасность вебхуков и проекции статусов;
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9), `ClaimRightsDutiesMatrixTest` (7/7) — матрица прав и обязанностей Хофельда/Раза, детерминированные отказы по операционной политике.
     * **Секция XIII («Взаимодействие: кто пишет каждое состояние», 51 тест, 10 сьютов):**
       - `ClaimResultStatusTruthTableFalsificationTest` (5/5), `ClaimServiceRaceGuardTest` (5/5) — защита от гонок захвата задач, атомарный CAS притязаний, 4-значная логика истинности статусов;
       - `SessionLifecycleServiceTest` (8/8), `SessionLifecycleServiceFalsificationTest` (6/6) — жизненный цикл Jules-сессий, детерминированные переходы без повисших состояний;
       - `StrandedFinalizingSweepServiceTest` (8/8), `StrandedFinalizingSweepServiceFalsificationTest` (6/6) — выметание застрявших состояний `finalizing` по таймауту через CAS;
       - `AutoMergeLaw20InvariantS4Test` (3/3) — закон 20: терминальные статусы задач необратимы и защищены от перезаписи при автослиянии;
       - `AccountRepositoryFalsificationTest` (8/8), `AccountRepositoryIntegrationTest` (8/8), `TaskRepositoryIntegrationTest` (4/4) — монопольное владение переходами в репозиториях задач и аккаунтов, защита от конкурентной перезаписи.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть основательными, строгими и доказательными.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: воспроизводимые тесты-заслоны на терминальные статусы (`AutoMergeLaw20InvariantS4Test`), сброс застрявших сессий (`StrandedFinalizingSweepServiceFalsificationTest`), исключение ложных зеленых прогонов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: изоляция системных логов от проектных журналов в `LogScopeBuffer`, разграничение ролевых профилей в `RoleCapabilityLoader`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические заслоны доступа контроллеров к мутациям состояния и матрица прав и обязанностей (`AccountControllerIntegrationTest`, `ClaimRightsDutiesMatrixTest`, `OperationalPolicyDeniedExceptionFalsificationTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHON_SHEFER_02_PARTIAL_ORDER_METRICS` [D004 Part-whole ownership / Varzi & Shaffer]:**
  * Монопольное владение переходами состояний в репозиториях задач и аккаунтов, защита от гонок захвата задач (`ClaimServiceRaceGuardTest`, `AccountRepositoryFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная логика истинности статусов притязаний и системных настроек (`ClaimResultStatusTruthTableFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: телеметрия внешних вебхуков и проекций статусов отражает реальное физическое состояние без домыслов (`GithubWebhookControllerTest`, `SystemStatusControllerTest`).
- **`DZHON_SERL_01_STATUS_FUNCTION_DECLARATION` [D007 Intentionality gap / Searle]:**
  * Институциональный факт перехода состояния сессий и финализации задач.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций XII, XIIа и XIII:**
   - **Docker Maven (189 тестов, 31 сьют):**
     ```bash
     docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,InternalTaskControllerTest,AccountControllerIntegrationTest,SettingsControllerIntegrationTest,WishlistControllerIntegrationTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
     ```
2. **Верификация результата:**
   - Строго **189/189 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать замер и статус `ideal` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
   - Записать краткий отчет (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Сделать семантический коммит `feat(journal-surface-state): complete Stage 4 batch verification (D008, D010, D006)`.
