# Директивы и наряд для L2 (2026-10-23 23:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверен и закрыт инженером (коммит `eb44e75f`, 280/280 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:55 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий неразрывный стратегический кластер механизмов фабрики — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария», «Управляющая поверхность API: чем поток останавливают снаружи», «Взаимодействие: кто пишет каждое состояние / Карта владения состоянием»)** целиком (**193 теста в едином непрерывном пакетном прогоне в Docker Maven [32 тест-сьюта]**):
     * **Группа 1: Секция XII — Журнал, память, санитария (10 сьютов, 64 теста):**
       - `LogScopeBufferTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов), `ScopedBufferAppenderFalsificationTest` (13 тестов): строгая демаркация контекстов MDC (`PROJECT:{id}` против `SYSTEM`), защита от утечки системного шума в проектный буфер, отсечение DEBUG/TRACE (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProjectEventLogRetentionServiceTest` (10 тестов): каузальная цепочка надёжности Голдмана, удаление по смыслу жизненного цикла проекта (grace period для accepted) и соблюдение per-project ceiling без OOM и подъема избытка в память JVM (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `SystemSettingsServiceTest` (13 тестов): институциональный факт-регистр настроек, валидация ключей и типов, аудит мутаций в `defect_journal` с маскированием секретов (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `RoleCapabilityLoaderTest` (4 теста): загрузка уставов ролей с кэшированием по mtime и сохранением сырого Markdown-текста устава (`GARET_EVANS_01_RIGID_API_REFERENT` / D003);
       - `ProjectAuditPipelineServiceTest` (4 теста): последовательный аудит-конвейер, ограниченный стадиями с реальной проверкой (`COVERAGE_AUDIT`, `STITCH_DESIGN`) без холостых переключений (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `IdleProjectAdviceServiceTest` (6 тестов), `RoleAdviceLoopServiceTest` (2 теста), `RoleAdviceLoopServiceIntegrationTest` (1 тест): покэ-йокэ наблюдающие предохранители простоя и завершения задач (`IdleGuardVerdict`, `RoleAdviceGuardVerdict`) со строгим подавлением спекулятивных вишлистов в обход фальсификации (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Группа 2: Секция XIIа — Управляющая поверхность API: чем поток останавливают снаружи (14 сьютов, 78 тестов):**
       - `GreetingControllerIntegrationTest` (5 тестов), `LinearSyncControllerTest` (2 теста), `InternalTaskControllerTest` (6 тестов), `AccountControllerIntegrationTest` (5 тестов), `SettingsControllerIntegrationTest` (8 тестов), `WishlistControllerIntegrationTest` (8 тестов), `GithubWebhookControllerTest` (9 тестов), `SystemStatusControllerTest` (3 теста), `DashboardControllerTest` (2 теста), `CommandDashboardControllerTest` (4 теста): внешние интерфейсы управления фабрикой, строгое сохранение контрактов без побочных эффектов (`GARET_EVANS_01_RIGID_API_REFERENT` / D003, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): фальсификационный заслон отказа операционных политик и деонтических запретов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ClaimRightsDutiesMatrixTest` (7 тестов), `ClaimResultStatusTruthTableFalsificationTest` (5 тестов), `ClaimServiceRaceGuardTest` (5 тестов): матрица прав и обязанностей притязаний, многозначная таблица истинности исходов притязаний и защита от гонок владения (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние / Карта владения состоянием (8 сьютов, 51 тест):**
       - `SessionLifecycleServiceTest` (8 тестов), `SessionLifecycleServiceFalsificationTest` (6 тестов): управление жизненным циклом сессий Jules, корреляция состояний и взаимные обязательства (`MARGARITA_GILBERT_01_PLURAL_SUBJECT` / D007, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов): автоматический выметающий обход зависших финализаций с соблюдением инварианта владения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AutoMergeLaw20InvariantS4Test` (3 теста): монопольное владение слиянием по Закону 20 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `AccountRepositoryFalsificationTest` (8 тестов), `AccountRepositoryIntegrationTest` (8 тестов), `TaskRepositoryIntegrationTest` (4 теста): фальсификационные заслоны и целостность ключевых репозиториев фабрики (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D013, D012, D004, D011, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны сессий (`SessionLifecycleServiceFalsificationTest`), зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`), хранилищ (`AccountRepositoryFalsificationTest`), политик (`OperationalPolicyDeniedExceptionFalsificationTest`) и буферов (`ScopedBufferAppenderFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств журнала событий (`ProjectEventLogRetentionServiceTest`), фиксация уровней абстракции контекстов логов MDC, целостность хранилищ задач и аккаунтов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов управления фабрикой, матрица прав и обязанностей притязаний (`ClaimRightsDutiesMatrixTest`), монопольное владение слиянием по Закону 20 (`AutoMergeLaw20InvariantS4Test`).
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Institutional fact & INUS / Searle & Mackie]:**
  * Институциональный факт-регистр системных настроек (`SystemSettingsServiceTest`), INUS-атрибуция дефектов логов (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`LUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальная телеметрия управляющей поверхности API без паразитных мутаций рабочего состояния.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 5-значная таблица истинности исходов притязаний (`ClaimResultStatusTruthTableFalsificationTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Монопольное партитивное владение слиянием по Закону 20 и атомарный захват зависших финализаций (`StrandedFinalizingSweepServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Покэ-йокэ наблюдающие предохранители простоя без спекулятивного порождения чужой работы (`IdleProjectAdviceServiceTest`, `RoleAdviceLoopServiceTest`).
- **`GARET_EVANS_01_RIGID_API_REFERENT` [D003 Rigid API referent / Evans]:**
  * Ригидная привязка публичных интерфейсов контроллеров и уставов ролей (`RoleCapabilityLoaderTest`).

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 32 тест-сьютов Кластера Секций XII, XIIа и XIII внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,InternalTaskControllerTest,AccountControllerIntegrationTest,SettingsControllerIntegrationTest,WishlistControllerIntegrationTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,CommandDashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. **Критерий приёмки:** Ровно 193/193 green в 32 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (193/193 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(sections-xii-xiia-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
