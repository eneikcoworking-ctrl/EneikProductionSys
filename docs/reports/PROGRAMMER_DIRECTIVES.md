# Директивы и наряд для L2 (2026-10-16 18:30 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций IX, X и XI («Внешние системы: AI/RAG/Runtime-граница фабрики, Цех дизайна и Наблюдение за живым продуктом клиента») успешно заверен инженером (коммит `12bc0b4a`, 280/280 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики»)** целиком (**189 тестов в 31 тест-сьюте в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XII — Журнал, память, санитария (64 теста, 10 сьютов):**
       - `LogScopeBufferTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green), `ScopedBufferAppenderFalsificationTest` (13/13 green): кольцевой буфер журналов, семантическая изоляция скоупов потока, фильтрация шума и INUS-атрибуция дефектов (`DURDZHON_MJEKKI_03_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `ProjectEventLogRetentionServiceTest` (10/10 green): безопасная подрезка логов без подъема всей таблицы в JVM-память, долговечный аппендер (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest` (13/13 green): защита системных настроек фабрики от неавторизованной перезаписи (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RoleCapabilityLoaderTest` (4/4 green), `ProjectAuditPipelineServiceTest` (4/4 green), `IdleProjectAdviceServiceTest` (6/6 green), `RoleAdviceLoopServiceTest` (2/2 green), `RoleAdviceLoopServiceIntegrationTest` (1/1 green): парсинг правил ролей Jules, загрузчик возможностей, поке-йоке аудит и цикл советов без зависания потока (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 2: Секция XIIа — Управляющая поверхность API: чем поток останавливают снаружи (74 теста, 13 сьютов):**
       - `AccountControllerIntegrationTest` (5/5 green), `InternalTaskControllerTest` (6/6 green), `WishlistControllerIntegrationTest` (8/8 green), `SettingsControllerIntegrationTest` (8/8 green), `GreetingControllerIntegrationTest` (5/5 green), `LinearSyncControllerTest` (2/2 green), `GithubWebhookControllerTest` (9/9 green), `SystemStatusControllerTest` (3/3 green), `DashboardControllerTest` (2/2 green): неизменяемость внешних контрактов API, чистое чтение без побочных эффектов мутации (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): типизированный отказ операционной политики с институциональным обоснованием (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ClaimRightsDutiesMatrixTest` (7/7 green), `ClaimResultStatusTruthTableFalsificationTest` (5/5 green), `ClaimServiceRaceGuardTest` (5/5 green): матрица прав и обязанностей Хофельда-Раза, 5-значная таблица истинности исходов притязаний, заслон от гонок на уровне хранилища (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XIII — Взаимодействие: кто пишет каждое состояние / Карта владения состоянием (51 тест, 8 сьютов):**
       - `SessionLifecycleServiceTest` (8/8 green), `SessionLifecycleServiceFalsificationTest` (6/6 green): строгий конечный автомат жизненного цикла сессий Jules, отказ в недопустимых переходах (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `StrandedFinalizingSweepServiceTest` (8/8 green), `StrandedFinalizingSweepServiceFalsificationTest` (6/6 green): автономное восстановление зависших финализаций без внешнего оператора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `AutoMergeLaw20InvariantS4Test` (3/3 green): закон 20 — монопольное владение слиянием веток и защита от перезаписи терминальных состояний (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `AccountRepositoryFalsificationTest` (8/8 green), `AccountRepositoryIntegrationTest` (8/8 green), `TaskRepositoryIntegrationTest` (4/4 green): хранилищные заслоны целостности пула аккаунтов и задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация заслонов буферизации логов (`ScopedBufferAppenderFalsificationTest`), изоляции сессий (`SessionLifecycleServiceFalsificationTest`), гонок притязаний (`ClaimServiceRaceGuardTest`) и хранилищных инвариантов (`AccountRepositoryFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность цепочки происхождения данных: строгое разграничение оперативной памяти журнала (`LogScopeBuffer`), долговечного журнала (`ProjectEventLogRepository`), правил ролей и состояний репозиториев.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты неавторизованных действий в эндпоинтах управления, строгая матрица прав и обязанностей при притязаниях на файлы.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в контроллерах управляющей поверхности API: чтение не должно мутировать состояние; физические события в базе первичны по отношению к отчетам.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued Truth Status / Belnap]:**
  * Многозначные таблицы истинности для статусов притязаний (`ClaimResultStatus`) и жизненного цикла сессий.
- **`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` & `DURDZHON_MJEKKI_03_INUS_CONDITION` [D007 Lost Defect Lineage / Searle & Mackie]:**
  * Институциональный реестр фактов о событиях проекта и INUS-условия дефектов в `LogScopeAndDefectJournalInusFalsificationTest`.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XII, XIIа и XIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,AccountControllerIntegrationTest,InternalTaskControllerTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить 100% green (189/189 green, 0 failures, 0 errors).
3. Обеспечить строго 0 строк изменений рабочего кода сервисов, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xii-xiii): complete Stage 4 batch verification (D008, D010, D006)`.
