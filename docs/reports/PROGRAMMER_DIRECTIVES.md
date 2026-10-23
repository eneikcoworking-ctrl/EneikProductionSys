# Директивы и наряд для L2 (2026-10-23 11:43 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверен и закрыт инженером (коммит `3acc2082`, 280/280 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:59 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**193 теста в едином непрерывном пакетном прогоне в Docker Maven [32 тест-сьюта]**):
     * **Группа 1: Секция XII — Журнал, память, санитария (10 сьютов, 64 теста):**
       - `LogScopeBufferTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов), `ScopedBufferAppenderFalsificationTest` (13 тестов): кольцевой буфер журналов, семантическая демаркация контекстов `PROJECT:{id}` против `SYSTEM`, фильтрация шума, отсечение DEBUG/TRACE и INUS-атрибуция дефектов (`DURDZHON_MJEKKI_03_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProjectEventLogRetentionServiceTest` (10 тестов): безопасная подрезка логов retention без подъема всей таблицы в память JVM, соблюдение per-project ceiling для active/frozen и grace period для accepted (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `SystemSettingsServiceTest` (13 тестов): защита системных настроек фабрики, институциональный факт-регистр, валидация ключей и типов, аудит мутаций в `defect_journal` с маскированием секретов (`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RoleCapabilityLoaderTest` (4 теста): кэширование уставов ролей по mtime, сохранение исходного Markdown-текста для внешних LLM без потерь (`GARET_EVANS_01_RIGID_API_REFERENT` / D003);
       - `ProjectAuditPipelineServiceTest` (4 теста): конвейер проектного аудита, ограниченный реально верифицирующими стадиями (`COVERAGE_AUDIT`, `STITCH_DESIGN`) без холостых переключений (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `IdleProjectAdviceServiceTest` (6 тестов), `RoleAdviceLoopServiceTest` (2 теста), `RoleAdviceLoopServiceIntegrationTest` (1 тест): покэ-йокэ наблюдающие предохранители против паразитной спекулятивной работы с типизированными вердиктами (`IdleGuardVerdict`, `RoleAdviceGuardVerdict`) (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Группа 2: Секция XIIа — Управляющая поверхность API: чем поток останавливают снаружи (14 сьютов, 78 тестов):**
       - `AccountControllerIntegrationTest` (5 тестов), `InternalTaskControllerTest` (6 тестов), `WishlistControllerIntegrationTest` (8 тестов), `SettingsControllerIntegrationTest` (8 тестов), `GreetingControllerIntegrationTest` (5 тестов), `LinearSyncControllerTest` (2 теста), `GithubWebhookControllerTest` (9 тестов), `SystemStatusControllerTest` (3 теста), `DashboardControllerTest` (2 теста), `CommandDashboardControllerTest` (4 теста): неизменяемость внешних контрактов API, чистое анти-зеркальное чтение без побочных эффектов мутации при GET-запросах (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): типизированный отказ операционной политики с институциональным обоснованием и HTTP 409 Conflict (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ClaimRightsDutiesMatrixTest` (7 тестов), `ClaimResultStatusTruthTableFalsificationTest` (5 тестов), `ClaimServiceRaceGuardTest` (5 тестов): деонтическая матрица прав и обязанностей Хофельда-Раза, 5-значная таблица истинности исходов притязаний Белнапа, хранилищный заслон от состояний гонки (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние / Карта владения состоянием (8 сьютов, 51 тест):**
       - `SessionLifecycleServiceTest` (8 тестов), `SessionLifecycleServiceFalsificationTest` (6 тестов): строгий конечный автомат жизненного цикла сессий Jules, единственное владение удалением сессий, отказ в недопустимых переходах (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов): автономное восстановление зависших финализаций `finalizing -> pending` по временной метке без внешнего оператора, CAS-предикаты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AutoMergeLaw20InvariantS4Test` (3 теста): Закон 20 — монопольное владение слиянием веток и деонтический запрет перезаписи терминальных состояний задач (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `AccountRepositoryFalsificationTest` (8 тестов), `AccountRepositoryIntegrationTest` (8 тестов), `TaskRepositoryIntegrationTest` (4 теста): хранилищные заслоны целостности пула аккаунтов и задач, лок уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D004, D012, D007, D011, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны инвариантов сессий (`SessionLifecycleServiceFalsificationTest`), выметающего обхода (`StrandedFinalizingSweepServiceFalsificationTest`), аппендера логов (`ScopedBufferAppenderFalsificationTest`), демаркации хранилищ (`AccountRepositoryFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock / Goldman]:**
  * Четкое разделение уровней абстракции при хранении проектных логов (`LogScope`, `ProjectEventLogRetentionService`), отсутствие смешивания системных и проектных контекстов, каузальная целостность владения сущностями задач и аккаунтов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов управления (`OperationalPolicyDeniedExceptionFalsificationTest`), матрица прав и обязанностей Хофельда-Раза в притязаниях (`ClaimRightsDutiesMatrixTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Управляющая поверхность API и контроллеры чтения не должны иметь побочных эффектов мутации при GET/read запросах.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership & D006 / Varzi]:**
  * Монопольное владение жизненным циклом сессий Jules, Закон 20 нереверсивности терминальных статусов (`AutoMergeLaw20InvariantS4Test`), атомарность CAS-лизинга финализаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Status ambiguity / Belnap]:**
  * 5-значная решетка исходов притязаний (`ClaimResultStatusTruthTableFalsificationTest`).
- **`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` & `DURDZHON_MJEKKI_03_INUS_CONDITION` [D007 Fact registry & INUS / Searle & Mackie]:**
  * Аудит мутаций настроек в `SystemSettingsService`, INUS-атрибуция сбоев в дефект-журнале (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Предохранители простоя `IdleProjectAdviceService` и `RoleAdviceLoopService` без порождения паразитной работы.
- **`GARET_EVANS_01_RIGID_API_REFERENT` [D003 Rigid referent / Evans]:**
  * Неизменность публичных контрактов и сохранение неискаженного текста уставов ролей (`RoleCapabilityLoaderTest`).

---

## 3. Регламент исполнения наряда инженером L2

1. Провести единый непрерывный пакетный прогон 32 тест-сьютов Кластера Секций XII, XIIа и XIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,AccountControllerIntegrationTest,InternalTaskControllerTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,CommandDashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить 193/193 green (0 failures, 0 errors, 0 skipped).
3. Обеспечить соблюдение правила Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
6. Закоммитить: `feat(sections-xii-xiia-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
7. **ВНИМАНИЕ:** Не взводить таймеры и не вызывать schedule. Завершить такт штатно.
