# Директивы и наряд для L2 (2026-10-17 00:54 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверен инженером (коммит `1d151487`, 280/280 green в 26 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**193 теста в 32 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XII — Журнал, память, санитария (64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green), `ScopedBufferAppenderFalsificationTest` (13/13 green): кольцевой буфер журнала, INUS-анализ первопричин дефектов, потокобезопасное накопление без переполнения памяти JVM (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHON_MEKI_01_INUS_CONDITION` / D007);
       - `ProjectEventLogRetentionServiceTest` (10/10 green): очистка журнала с частотой, выведенной из темпа роста, исключающая подъём излишка в память JVM (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SystemSettingsServiceTest` (13/13 green): неизменяемость ключевых системных настроек (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RoleCapabilityLoaderTest` (4/4 green): детерминированная загрузка ролевых полномочий (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProjectAuditPipelineServiceTest` (4/4 green): пайплайн аудита проектов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `IdleProjectAdviceServiceTest` (6/6 green), `RoleAdviceLoopServiceTest` (2/2 green), `RoleAdviceLoopServiceIntegrationTest` (1/1 green): контур ролевых советов для простаивающих проектов без зацикливания и деградации производительности (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Группа 2: Секция XIIа — Управляющая поверхность API (78 тестов, 14 сьютов):**
       - `AccountControllerIntegrationTest` (5/5 green), `InternalTaskControllerTest` (6/6 green), `WishlistControllerIntegrationTest` (8/8 green), `SettingsControllerIntegrationTest` (8/8 green), `GreetingControllerIntegrationTest` (5/5 green), `LinearSyncControllerTest` (2/2 green), `GithubWebhookControllerTest` (9/9 green), `SystemStatusControllerTest` (3/3 green), `DashboardControllerTest` (2/2 green), `CommandDashboardControllerTest` (4/4 green): чистое чтение без скрытых побочных эффектов мутации данных, сохранение контрактов API (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): отказ операционной политики с типизированной институциональной причиной (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ClaimRightsDutiesMatrixTest` (7/7 green), `ClaimResultStatusTruthTableFalsificationTest` (5/5 green), `ClaimServiceRaceGuardTest` (5/5 green): матрица прав и обязанностей при захвате задач, 5-значная решётка истинности исходов притязаний, защита от параллельных гонок (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XIII — Взаимодействие и карта владения состоянием (51 тест, 8 сьютов):**
       - `SessionLifecycleServiceTest` (8/8 green), `SessionLifecycleServiceFalsificationTest` (6/6 green): конечный автомат жизненного цикла сессий Jules без категориальных ошибок (`DZHILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `StrandedFinalizingSweepServiceTest` (8/8 green), `StrandedFinalizingSweepServiceFalsificationTest` (6/6 green): атомарный CAS-сброс зависших состояний финализации (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHON_SERL_01_SPEECH_ACT_REGISTRATION` / D007);
       - `AutoMergeLaw20InvariantS4Test` (3/3 green): Закон 20 монопольного владения слиянием PR (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `AccountRepositoryFalsificationTest` (8/8 green), `AccountRepositoryIntegrationTest` (8/8 green), `TaskRepositoryIntegrationTest` (4/4 green): строгое разграничение прав записи сущностей аккаунтов и задач (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D002, D004, D007, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны целостности схемы Flyway, сессий (`SessionLifecycleServiceFalsificationTest`), финализации (`StrandedFinalizingSweepServiceFalsificationTest`), буферов (`ScopedBufferAppenderFalsificationTest`) и исключений политик (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Строгий лок уровней абстракции кольцевого буфера журнала, семантическая изоляция скоупов потока, надежная очистка журнала без подъёма излишков в JVM и детерминированная загрузка ролевых уставов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты: отказ операционных политик с институциональной причиной, неизменяемость системных настроек, матрица прав и обязанностей при захвате задач (`ClaimRightsDutiesMatrixTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: неизменяемость внешних контрактов API, чистое чтение контроллеров GET без скрытых побочных эффектов записи.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 5-значная решётка истинности исходов притязаний на задачи (`ClaimResultStatusTruthTableFalsificationTest`).
- **`DZHILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Конечный автомат жизненного цикла Jules-сессий без категориальных ошибок и смешения стадий.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereology & D006 Boundary / Varzi]:**
  * Закон 20 монопольного владения слиянием PR (`AutoMergeLaw20InvariantS4Test`), атомарный CAS-сброс зависшей финализации, топологическая изоляция гонок аренды задач (`ClaimServiceRaceGuardTest`).
- **`DZHON_MEKI_01_INUS_CONDITION` & `DZHON_SERL_01_SPEECH_ACT_REGISTRATION` [D007 Institutional defect & fact register / Mackie & Searle]:**
  * Атрибуция первопричин дефектов в журнале дефектов (INUS-анализ) и институциональная регистрация фактов финализации.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Телеосемантическая обратная связь: цикл ролевых советов без зависания и деградации потока.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XII, XIIа и XIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   - Заверить все 32 тест-сьюта Кластера (193 теста: 0 failures, 0 errors, 0 skipped).
2. Соблюсти фундаментальный инвариант Ступени 4:
   - **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать результаты замера в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит и обновить `docs/reports/PROGRAMMER_STATE.md`.
