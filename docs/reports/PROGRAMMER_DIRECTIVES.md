# Директивы и наряд для L2 (2026-10-16 23:28 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверены инженером (коммит `0288eaf6`, 280/280 green в 26 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического контура фабрики — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**193 теста в 32 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XII — Журнал, память, санитария (64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green), `ScopedBufferAppenderFalsificationTest` (13/13 green): кольцевой буфер журналов, семантическая изоляция скоупов потока, фильтрация шума и INUS-атрибуция дефектов (`DURDZHON_MJEKKI_03_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `ProjectEventLogRetentionServiceTest` (10/10 green): безопасная подрезка логов без подъема всей таблицы в память JVM, потолковое удержание и долговечный аппендер (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest` (13/13 green): защита системных настроек фабрики от неавторизованной перезаписи (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RoleCapabilityLoaderTest` (4/4 green), `ProjectAuditPipelineServiceTest` (4/4 green), `IdleProjectAdviceServiceTest` (6/6 green), `RoleAdviceLoopServiceTest` (2/2 green), `RoleAdviceLoopServiceIntegrationTest` (1/1 green): парсинг правил ролей Jules, загрузчик возможностей, поке-йоке аудит и цикл советов без зависания потока (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 2: Секция XIIа — Управляющая поверхность API: чем поток останавливают снаружи (78 тестов, 14 сьютов):**
       - `GreetingControllerIntegrationTest` (5/5 green), `LinearSyncControllerTest` (2/2 green), `InternalTaskControllerTest` (6/6 green), `AccountControllerIntegrationTest` (5/5 green), `SettingsControllerIntegrationTest` (8/8 green), `WishlistControllerIntegrationTest` (8/8 green), `GithubWebhookControllerTest` (9/9 green), `SystemStatusControllerTest` (3/3 green), `DashboardControllerTest` (2/2 green), `CommandDashboardControllerTest` (4/4 green): неизменяемость внешних контрактов API, чистое чтение без побочных эффектов мутации (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): типизированный отказ операционной политики с институциональным обоснованием (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ClaimRightsDutiesMatrixTest` (7/7 green), `ClaimResultStatusTruthTableFalsificationTest` (5/5 green), `ClaimServiceRaceGuardTest` (5/5 green): матрица прав и обязанностей Хофельда-Раза, 5-значная таблица истинности исходов притязаний, заслон от гонок на уровне хранилища (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние / Карта владения состоянием (51 тест, 8 сьютов):**
       - `SessionLifecycleServiceTest` (8/8 green), `SessionLifecycleServiceFalsificationTest` (6/6 green): строгий конечный автомат жизненного цикла сессий Jules, отказ в недопустимых переходах (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `StrandedFinalizingSweepServiceTest` (8/8 green), `StrandedFinalizingSweepServiceFalsificationTest` (6/6 green): автономное восстановление зависших финализаций без внешнего оператора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `AutoMergeLaw20InvariantS4Test` (3/3 green): закон 20 — монопольное владение слиянием веток и абсолютная защита от перезаписи терминальных состояний (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `AccountRepositoryFalsificationTest` (8/8 green), `AccountRepositoryIntegrationTest` (8/8 green), `TaskRepositoryIntegrationTest` (4/4 green): хранилищные заслоны целостности пула аккаунтов и задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D007, D004, D002, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация схем и инвариантов хранилища задач (`TaskRepositoryIntegrationTest`, `AccountRepositoryFalsificationTest`), фальсификация зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`) и жизненного цикла сессий (`SessionLifecycleServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции буфера логов (`LogScopeBufferTest`), семантическая изоляция контекста выполнения от шума, надежная каузальная цепочка ролевых возможностей (`RoleCapabilityLoaderTest`, `ProjectAuditPipelineServiceTest`) и хранилищных состояний.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при зелёных тестах (exclusionary reasons);**
  * Деонтические заслоны системных настроек (`SystemSettingsServiceTest`), отсечение недопустимых переходов сессий (`SessionLifecycleServiceTest`), матрица прав и обязанностей притязаний (`ClaimRightsDutiesMatrixTest`) и институциональный отказ политик (`OperationalPolicyDeniedExceptionFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная неизменяемость контроллеров API: чтение статусов, дашбордов и настроек не производит скрытых мутаций в базе данных (`SystemStatusControllerTest`, `DashboardControllerTest`, `SettingsControllerIntegrationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначная таблица истинности исходов притязаний на задачи (`ClaimResultStatusTruthTableFalsificationTest`).
- **`DZHON_MEKI_01_INUS_CONDITION` & `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` [D007 Evidence gap / Mackie & Searle]:**
  * INUS-условия атрибуции причин дефектов в логах (`LogScopeAndDefectJournalInusFalsificationTest`) и институциональная фиксация фактов финализации (`StrandedFinalizingSweepServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereology / D006 Varzi]:**
  * Монопольное владение слиянием по Закону 20 (`AutoMergeLaw20InvariantS4Test`) и топологическая изоляция гонок при захвате задач (`ClaimServiceRaceGuardTest`).
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок жизненного цикла сессий воркеров (`SessionLifecycleServiceTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Телеосемантический цикл советов без зависания потока (`IdleProjectAdviceServiceTest`, `RoleAdviceLoopServiceTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XII, XIIа и XIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,AccountControllerIntegrationTest,InternalTaskControllerTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,CommandDashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить 100% green результат (**193/193 green в 32 тест-сьютах**).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать отметки Ступени 4 по Кластеру Секций XII, XIIа и XIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(sections-xii-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
