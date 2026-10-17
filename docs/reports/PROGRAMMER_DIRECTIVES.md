# Директивы и наряд для L2 (2026-10-17 03:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверен инженером (коммит `8f84bab3`, 280/280 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**193 теста в едином непрерывном пакетном прогоне в Docker Maven [32 тест-сьюта]**):
     * **Группа 1: Секция XII — Журнал, память, санитария (64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `ScopedBufferAppenderFalsificationTest` (13/13): строгая демаркация контекстов MDC (`PROJECT:{id}` против `SYSTEM`), защита от утечки системного шума в проектный буфер, отсечение DEBUG/TRACE (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `ProjectEventLogRetentionServiceTest` (10/10): каузальная цепочка надёжности Голдмана, удаление по жизненному циклу (grace period для accepted) и потолковое удержание записей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `SystemSettingsServiceTest` (13/13): институциональный факт-регистр настроек, валидация ключей и типов, аудит мутаций в `defect_journal` с маскированием секретов (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `RoleCapabilityLoaderTest` (4/4): загрузка уставов ролей с кэшированием по mtime и сохранением сырого Markdown-текста для внешних LLM-агентов (`GARET_EVANS_01_RIGID_API_REFERENT` / D003);
       - `ProjectAuditPipelineServiceTest` (4/4): последовательный конвейер проектного аудита со строгой верификацией стадий (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `IdleProjectAdviceServiceTest` (6/6), `RoleAdviceLoopServiceTest` (2/2), `RoleAdviceLoopServiceIntegrationTest` (1/1): покэ-йокэ наблюдающий предохранитель простоя со строгим подавлением спекулятивных вишлистов (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Группа 2: Секция XIIа — Управляющая поверхность: чем поток останавливают снаружи (78 тестов, 14 сьютов):**
       - `AccountControllerIntegrationTest` (5/5): маскирование секретов в `AccountDto`, аудит мутаций и фиксация пула ролей (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9): деонтические запреты невалидных переходов (HTTP 409 Conflict) и факт-регистр смены статусов проектов (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ClaimRightsDutiesMatrixTest` (7/7), `ClaimResultStatusTruthTableFalsificationTest` (5/5), `ClaimServiceRaceGuardTest` (5/5): конструктивные доказательства терминальных исходов, матрица прав и обязанностей и атомарные CAS-предикаты (`PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` / D007, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `WishlistControllerIntegrationTest` (8/8): разграничение архивации (dismiss) и санитарного удаления фантомов с гарантией 404 NOT_FOUND (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `SettingsControllerIntegrationTest` (8/8), `SystemStatusControllerTest` (3/3): обязательная аутентификация `X-API-Key` (HTTP 401 при отсутствии ключа), институциональный аудит и маскирование токенов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `InternalTaskControllerTest` (6/6): пагинация Pageable, точный поиск по Linear ID (`findFirstByLinearIssueId`), исключение OOM (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4): защищенный опрос пула агентов и командный пульт статуса потока без N+1 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `GithubWebhookControllerTest` (9/9): безопасная валидация структуры событий PR/ping/push без падений при неизвестных типах (`KRISPIN_RAYT_01_SUBSTITUTION_ORACLE` / D009);
       - `GreetingControllerIntegrationTest` (5/5), `LinearSyncControllerTest` (2/2): служебный шлюз приветствий и разграничение синхронизации Linear метаданных (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние (51 тест, 8 сьютов):**
       - `SessionLifecycleServiceTest` (8/8), `SessionLifecycleServiceFalsificationTest` (6/6): монопольное владение жизненным циклом `JulesSessionEntity` с исключением гонок при захвате workflow-lease `pr_opened` (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `StrandedFinalizingSweepServiceTest` (8/8), `StrandedFinalizingSweepServiceFalsificationTest` (6/6): автономное восстановление зависших финализаций `finalizing -> pending` через CAS-предикаты (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AutoMergeLaw20InvariantS4Test` (3/3): монопольное владение слиянием веток по Закону 20 и защита от перезаписи терминальных состояний (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `AccountRepositoryFalsificationTest` (8/8), `AccountRepositoryIntegrationTest` (8/8), `TaskRepositoryIntegrationTest` (4/4): хранилищные заслоны целостности пула аккаунтов и задач (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, репозиториев, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D004, D012, D011, D009, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские фальсификационные заслоны восстановления финализаций (`StrandedFinalizingSweepServiceFalsificationTest`), жизненного цикла сессий (`SessionLifecycleServiceFalsificationTest`) и хранилищ аккаунтов (`AccountRepositoryFalsificationTest`). Защита от фиктивно-зеленых тестов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции журнала проекта (`ProjectEventLogRetentionServiceTest`), конвейера аудита (`ProjectAuditPipelineServiceTest`) и разделения метаданных Linear от продуктовой готовности (`LinearSyncControllerTest`); каузальная цепочка надёжности жизненного цикла Jules-сессий и опроса пула агентов (`DashboardControllerTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты невалидных переходов по Закону 20 (`AutoMergeLaw20InvariantS4Test`), деонтическая матрица прав и обязанностей доступа к клеймам (`ClaimRightsDutiesMatrixTest`), обязательная аутентификация API-настроек (HTTP 401) и изоляция контекстов MDC (`LogScopeBufferTest`).
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` & `PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` [D007 Institutional facts / Searle & Martin-Löf]:**
  * Институциональный факт-регистр смены настроек (`SystemSettingsServiceTest`) и смены статусов проектов (`OperationalPolicyDeniedExceptionFalsificationTest`); конструктивные объекты доказательств терминальных исходов задач (`ClaimRightsDutiesMatrixTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Boundary topology / Shaffer & Varzi]:**
  * Монопольное владение частями целого состояния фабрики: владение переходами задач, вишлистов и сессий Jules, атомарные CAS-предикаты; топологическая граница и изоляция открытых контроллеров от произвольных мутаций базы.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначная логика истинности статусов клеймов и терминальных исходов (`ClaimResultStatusTruthTableFalsificationTest`) без подмены неопределенности успехом.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Покэ-йокэ наблюдающие предохранители против спекулятивной генерации задач и вишлистов (`IdleProjectAdviceServiceTest`, `RoleAdviceLoopServiceTest`).
- **`KRISPIN_RAYT_01_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Wright]:**
  * Валидация подлинности событий GitHub вебхуков (`GithubWebhookControllerTest`) без слепого доверия внешним источникам.
- **`GARET_EVANS_01_RIGID_API_REFERENT` [D003 Rigid API referent / Evans]:**
  * Ригидная привязка публичных ролевых уставов и заголовков задач к каноническим типам (`RoleCapabilityLoaderTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XII, XIIа и XIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,InternalTaskControllerTest,AccountControllerIntegrationTest,SettingsControllerIntegrationTest,WishlistControllerIntegrationTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,CommandDashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить 100% зелёных тестов (**193/193 green в 32 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Соблюсти деонтический инвариант Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать запись секций в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (статус: ideal, замер в Docker Maven 193/193 green).
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк) и сделать коммит `feat(sections-xii-xiia-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
