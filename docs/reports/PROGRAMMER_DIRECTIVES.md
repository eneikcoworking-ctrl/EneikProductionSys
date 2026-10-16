# Директивы и наряд для L2 (2026-10-16 20:33 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секции VII (TOC), VIII (Flow & Metrics, Kaizen, Оркестрация), IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») полностью заверены инженером (коммиты `37702f45`, `7a275402`, 280/280 green в Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**32 тест-сьюта в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XII — Журнал, память, санитария (10 сьютов):**
       - `LogScopeBufferTest`, `LogScopeAndDefectJournalInusFalsificationTest`, `ScopedBufferAppenderFalsificationTest`: семантическая изоляция скоупов потока, фильтрация шума, долговечный аппендер, кольцевой буфер и INUS-атрибуция дефектов (`DURDZHON_MJEKKI_03_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProjectEventLogRetentionServiceTest`: безопасная подрезка логов без подъема всей таблицы в память кучи JVM (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest`: защита системных настроек фабрики от неавторизованной перезаписи (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RoleCapabilityLoaderTest`, `ProjectAuditPipelineServiceTest`, `IdleProjectAdviceServiceTest`, `RoleAdviceLoopServiceTest`, `RoleAdviceLoopServiceIntegrationTest`: правила ролей Jules, загрузчик возможностей, поке-йоке аудит и цикл рекомендаций без зависания потока (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 2: Секция XIIа — Управляющая поверхность API: чем поток останавливают снаружи (14 сьютов):**
       - `AccountControllerIntegrationTest`, `InternalTaskControllerTest`, `WishlistControllerIntegrationTest`, `SettingsControllerIntegrationTest`, `GreetingControllerIntegrationTest`, `LinearSyncControllerTest`, `GithubWebhookControllerTest`, `SystemStatusControllerTest`, `DashboardControllerTest`, `CommandDashboardControllerTest`: неизменяемость внешних контрактов API, чистое чтение без побочных эффектов мутации (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest`: типизированный отказ операционной политики с институциональным обоснованием (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ClaimRightsDutiesMatrixTest`, `ClaimResultStatusTruthTableFalsificationTest`, `ClaimServiceRaceGuardTest`: матрица прав и обязанностей Хофельда-Раза, 5-значная таблица истинности исходов притязаний, заслон от гонок на уровне хранилища (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние / Карта владения состоянием (8 сьютов):**
       - `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`: строгий конечный автомат жизненного цикла сессий Jules, отказ в недопустимых переходах (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`: автономное восстановление зависших финализаций без внешнего оператора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `AutoMergeLaw20InvariantS4Test`: Закон 20 — монопольное владение слиянием веток и защита от перезаписи терминальных состояний (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `AccountRepositoryFalsificationTest`, `AccountRepositoryIntegrationTest`, `TaskRepositoryIntegrationTest`: хранилищные заслоны целостности пула аккаунтов и задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D007, D004, D002, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация буферов логирования (`ScopedBufferAppenderFalsificationTest`), отказов политик (`OperationalPolicyDeniedExceptionFalsificationTest`), жизненных циклов сессий (`SessionLifecycleServiceFalsificationTest`), эвакуации зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`) и хранилищ аккаунтов (`AccountRepositoryFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепь надежности: подрезка журналов `ProjectEventLogRetentionService` без исчерпания памяти кучи JVM (`PageRequest.of(excess - 1, 1)`), семантическая изоляция скоупов логирования (`LogScopeBufferTest`), хранилищные заслоны репозиториев (`AccountRepositoryIntegrationTest`, `TaskRepositoryIntegrationTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Матрица прав и обязанностей Хофельда-Раза в притязаниях (`ClaimRightsDutiesMatrixTest`), неизменяемость внешних контрактов API, защита системных настроек (`SystemSettingsServiceTest`), отсечение неавторизованных мутаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * 5-значная таблица истинности исходов притязаний в `ClaimResultStatusTruthTableFalsificationTest` и многозначные статусы без булевых редукций.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистые GET-проекции без паразитных побочных эффектов мутации в управляющих контроллерах (`SystemStatusControllerTest`, `DashboardControllerTest`, `SettingsControllerIntegrationTest`).
- **`DURDZHON_MJEKKI_03_INUS_CONDITION` & `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` [D007 Fact distortion / Mackie & Searle]:**
  * Институциональный учет дефектов и строгая INUS-атрибуция первопричин в `LogScopeAndDefectJournalInusFalsificationTest`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereology & D006 Boundary / Varzi]:**
  * Закон 20 — монопольное владение слиянием веток и защита терминальных состояний (`AutoMergeLaw20InvariantS4Test`), атомарный захват гонок в `ClaimServiceRaceGuardTest`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic / Dretske]:**
  * Телеосемантическая обратная связь в цикле рекомендаций ролей (`RoleAdviceLoopServiceTest`, `IdleProjectAdviceServiceTest`).

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XII, XIIа и XIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,AccountControllerIntegrationTest,InternalTaskControllerTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,CommandDashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить инвариант Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Зафиксировать такт семантическим коммитом: `feat(sections-xii-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
