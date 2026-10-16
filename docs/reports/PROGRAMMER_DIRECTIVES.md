# Директивы и наряд для L2 (2026-10-16 10:52 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») успешно заверен инженером (коммит `a8ffd6d`, 280/280 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием»)** целиком (31 тест-сьют, 189 тестов в едином пакетном прогоне в Docker Maven):
     * **Секция XII («Журнал, память, санитария», 64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `ScopedBufferAppenderFalsificationTest` (13/13) — буферизация логов по скоупам, разделение системных и проектных журналов, изоляция MDC-контекстов, защита от потери INUS-причин сбоев (D006 von Wright & Raz, D010 Goldman);
       - `ProjectEventLogRetentionServiceTest` (10/10) — ротация и очистка журнала событий проекта с жесткими пределами хранения и детерминированным триммингом (D010 Church & Goldman);
       - `SystemSettingsServiceTest` (13/13) — инварианты системных настроек и конфигурационных флагов (D007 Searle);
       - `RoleCapabilityLoaderTest` (4/4) — валидация прав и возможностей ролей Jules без смешения уровней ответственности (D003 Evans);
       - `ProjectAuditPipelineServiceTest` (4/4) — аудит пайплайна проекта и соответствие уставным требованиям (D011 Dretske);
       - `IdleProjectAdviceServiceTest` (6/6), `RoleAdviceLoopServiceTest` (2/2), `RoleAdviceLoopServiceIntegrationTest` (1/1) — покэ-йокэ контур рекомендаций для неактивных проектов и ролевых петель (D011 Dretske).
     * **Секция XIIа («Управляющая поверхность: чем поток останавливают снаружи», 74 теста, 11 сьютов):**
       - `AccountControllerIntegrationTest` (5/5), `InternalTaskControllerTest` (6/6), `WishlistControllerIntegrationTest` (8/8), `SettingsControllerIntegrationTest` (8/8) — изменяющие эндпоинты контроллеров, авторизация мутаций, предотвращение несанкционированных остановок потока (D006 Raz, D004 Shaffer);
       - `GreetingControllerIntegrationTest` (5/5), `LinearSyncControllerTest` (2/2), `GithubWebhookControllerTest` (9/9), `SystemStatusControllerTest` (3/3), `DashboardControllerTest` (2/2) — периферия внешних синхронизаций, безопасность вебхуков и проекции статусов (D010 Goldman, D013 Wittgenstein);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9), `ClaimRightsDutiesMatrixTest` (7/7) — матрица прав и обязанностей Хофельда/Раза, детерминированные отказы по операционной политике (D006 Raz).
     * **Секция XIII («Взаимодействие: кто пишет каждое состояние», 51 тест, 10 сьютов):**
       - `ClaimResultStatusTruthTableFalsificationTest` (5/5), `ClaimServiceRaceGuardTest` (5/5) — защита от гонок захвата задач, атомарный CAS притязаний, 4-значная логика истинности статусов (D012 Belnap, D004 Varzi);
       - `SessionLifecycleServiceTest` (8/8), `SessionLifecycleServiceFalsificationTest` (6/6) — жизненный цикл Jules-сессий, детерминированные переходы без повисших состояний (D004 Shaffer, D010 Goldman);
       - `StrandedFinalizingSweepServiceTest` (8/8), `StrandedFinalizingSweepServiceFalsificationTest` (6/6) — выметание застрявших состояний `finalizing` по таймауту через CAS (D013 Noe, D004 Varzi);
       - `AutoMergeLaw20InvariantS4Test` (3/3) — закон 20: терминальные статусы задач необратимы и защищены от перезаписи при автослиянии (D006 Raz, D008 Popper);
       - `AccountRepositoryFalsificationTest` (8/8), `AccountRepositoryIntegrationTest` (8/8), `TaskRepositoryIntegrationTest` (4/4) — монопольное владение переходами в репозиториях задач и аккаунтов, защита от конкурентной перезаписи (D004 Varzi & Shaffer, D006 Raz).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: воспроизводимые тесты-заслоны на терминальные статусы (`AutoMergeLaw20InvariantS4Test`), сброс застрявших сессий (`StrandedFinalizingSweepServiceFalsificationTest`), исключение ложных зеленых прогонов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: изоляция системных логов от проектных журналов в `LogScopeBuffer`, разграничение ролевых профилей в `RoleCapabilityLoader`, отсечение лишних записей в `ProjectEventLogRetentionService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимые деонтические заслоны изменяющих эндпоинтов в `AccountController`, `ClaimController`, `ProjectController` и `SettingsController`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Топологическая монополия владения переходов состояний в репозиториях (`TaskRepository`, `AccountRepository`, `WishlistRepository`, `JulesSessionRepository`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная логика истинности в статусах притязаний на задачи (`ClaimResultStatusTruthTableFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: чтение статусов и контроллеров не мутирует внутреннее состояние и не искажает замеры рантайма.
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact register / Searle]:**
  * Настройки становятся институциональным фактом только через валидацию и аудит в `defect_journal`.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций XII, XIIа и XIII:**
   - **Docker Maven (189 тестов, 31 сьют):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,AccountControllerIntegrationTest,InternalTaskControllerTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
     ```
2. **Верификация результата:**
   - Строго **189/189 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XII, XIIа и XIII в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XII, XIIа и XIII в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(journal-surface-state): complete Stage 4 batch verification (D008, D010, D006)`.
