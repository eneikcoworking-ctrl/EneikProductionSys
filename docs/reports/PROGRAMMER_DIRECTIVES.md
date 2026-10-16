# Пакетная директива L1/L3 -> L2: Кластер Секций XII, XIIа и XIII — Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики

**Дата:** 2026-10-16 07:05 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию/кластер механизмов целиком. Кластер Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента») успешно заверен инженером (коммит `7fbf0e8`, 280/280 green в Docker Maven, 0 строк изменений кода). Секция VII (TOC) и Секция VIII (Flow & Metrics) закрыты ранее. Настоящая директива открывает пакетный замер следующего фундаментального кластера фабрики — **Кластера Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (189 тестов, 30 сьютов):
   - **Секция XII: Журнал, память, санитария (64 теста, 10 сьютов):**
     * `LogScope`, `ScopedBufferAppender`, `LogScopeBuffer` (`LogScopeBufferTest` 6, `LogScopeAndDefectJournalInusFalsificationTest` 5, `ScopedBufferAppenderFalsificationTest` 13) — scoped буферизация логов, разделение системных и проектных журналов, изоляция MDC-контекстов, защита от потери INUS-причин сбоев (D010 Goldman, D007 Mackie).
     * `ProjectEventLogRetentionService` (`ProjectEventLogRetentionServiceTest` 10) — ротация и очистка журнала событий проекта с жесткими пределами хранения и детерминированным триммингом (D010 Goldman, D006 Raz).
     * `SystemSettingsService` (`SystemSettingsServiceTest` 13) — инварианты системных настроек и конфигурационных флагов (D006 Raz, D012 Belnap).
     * `RoleCapabilityLoader` (`RoleCapabilityLoaderTest` 4) — валидация прав и возможностей ролей Jules без смешения уровней ответственности (D010 Goldman).
     * `ProjectAuditPipelineService` (`ProjectAuditPipelineServiceTest` 4) — аудит пайплайна проекта и соответствие уставным требованиям (D008 Popper).
     * `IdleProjectAdviceService` (`IdleProjectAdviceServiceTest` 6) & `RoleAdviceLoopService` (`RoleAdviceLoopServiceTest` 2, `RoleAdviceLoopServiceIntegrationTest` 1) — контур рекомендаций для неактивных проектов и ролевых петель (D011 Dretske).
   - **Секция XIIа: Управляющая поверхность: чем поток останавливают снаружи (74 теста, 10 сьютов):**
     * `AccountControllerIntegrationTest` (5), `InternalTaskControllerTest` (6), `WishlistControllerIntegrationTest` (8), `SettingsControllerIntegrationTest` (8) — изменяющие эндпоинты контроллеров, авторизация мутаций, предотвращение несанкционированных остановок потока (D006 Raz, D004 Varzi).
     * `GreetingControllerIntegrationTest` (5), `LinearSyncControllerTest` (2), `GithubWebhookControllerTest` (9), `SystemStatusControllerTest` (3), `DashboardControllerTest` (2) — периферия внешних синхронизаций, безопасность вебхуков и проекции статусов (D006 Raz, D013 Wittgenstein).
     * `OperationalPolicyDeniedExceptionFalsificationTest` (9) & `ClaimRightsDutiesMatrixTest` (7) — матрица прав и обязанностей Хофельда/Раза, детерминированные отказы по операционной политике (D006 Raz & Hohfeld).
   - **Секция XIII: Взаимодействие: кто пишет каждое состояние (51 тест, 10 сьютов):**
     * `ClaimResultStatusTruthTableFalsificationTest` (5) & `ClaimServiceRaceGuardTest` (5) — защита от гонок захвата задач, атомарный CAS притязаний, 4-значная логика истинности статусов (D004 Varzi, D012 Belnap).
     * `SessionLifecycleServiceTest` (8) & `SessionLifecycleServiceFalsificationTest` (6) — жизненный цикл Jules-сессий, детерминированные переходы без повисших состояний (D004 Shaffer, D008 Popper).
     * `StrandedFinalizingSweepServiceTest` (8) & `StrandedFinalizingSweepServiceFalsificationTest` (6) — выметание застрявших состояний `finalizing` по таймауту через CAS (D004 Varzi, D007 Gilbert).
     * `AutoMergeLaw20InvariantS4Test` (3) — закон 20: терминальные статусы задач необратимы и защищены от перезаписи при автослиянии (D006 Raz, D012 Belnap).
     * `AccountRepositoryFalsificationTest` (8), `AccountRepositoryIntegrationTest` (8), `TaskRepositoryIntegrationTest` (4) — монопольное владение переходам в репозиториях задач и аккаунтов, защита от конкурентной перезаписи (D004 Shaffer).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем журнала, управляющей поверхности и репозиториев состояний (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: воспроизводимые тесты-заслоны на терминальные статусы (`AutoMergeLaw20InvariantS4Test`), сброс застрявших сессий (`StrandedFinalizingSweepServiceFalsificationTest`), исключение ложных зеленых прогонов.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Каузальная цепочка надёжности и жесткий лок уровней абстракции: изоляция системных логов от проектных журналов в `LogScopeBuffer`, разграничение ролевых профилей в `RoleCapabilityLoader`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код: жесткая матрица прав и обязанностей (`ClaimRightsDutiesMatrixTest`), исключающая возможность модификации терминальных состояний задачи и неавторизованного сброса настроек.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Монопольное владение состоянием агрегата: репозитории задач и аккаунтов исключают параллельную запись мимо CAS-заслонов (`TaskRepositoryIntegrationTest`, `AccountRepositoryFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная логика истинности в `ClaimResultStatusTruthTableFalsificationTest` и `AutoMergeLaw20InvariantS4Test` для предотвращения смысловой деградации состояний.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: верификация состояний по физическим фактам базы данных и логов хоста, а не декларациям в памяти.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKKI_03_INUS_FACTOR_CHECK` [D007 Evidence gap / Gilbert & Mackie]:**
  * Институциональный учет дефектов и сохранение достаточных условий (INUS) в `LogScopeAndDefectJournalInusFalsificationTest`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Кластера Секций XII, XIIа и XIII в едином непрерывном запуске Docker Maven (все 30 тест-сьютов, 189 тестов):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,InternalTaskControllerTest,AccountControllerIntegrationTest,SettingsControllerIntegrationTest,WishlistControllerIntegrationTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Кластер Секций XII, XIIа и XIII) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(journal-surface-state): complete Stage 4 batch verification (D008, D010, D006)`.
