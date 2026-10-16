# Директивы и наряд для L2 (2026-10-16 23:45 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2») полностью заверены инженером (коммит `ca70d05c`, 224/224 green в 26 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического контура фабрики — **Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные механизмы, планировщик и читающие контроллеры, сайдкары вне бэкенда и ключевые хранилища потока»)** целиком (**158 тестов в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секции XVII и XVIIб — Обездвиженные механизмы, планировщик и читающие контроллеры (57 тестов, 14 сьютов):**
       - `DashboardControllerTest` (2/2 green), `CommandDashboardControllerTest` (4/4 green), `QualityGateControllerTest` (2/2 green), `QualityMetricsControllerTest` (3/3 green), `HomeControllerTest` (1/1 green), `LinearSyncControllerTest` (2/2 green), `SystemStatusControllerTest` (3/3 green): демаркация читающих GET-поверхностей строго без мутаций состояния (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `GlobalExceptionHandlerTest` (3/3 green): санитарный перехват непойманных исключений с изоляцией сетевых обрывов и клиентских ошибок (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `WebConfigCorsTest` (3/3 green): строгая топология CORS с запретом wildcard при credentials (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SchedulingConfigTest` (2/2 green): единый монопольный владелец пула планировщика фабрики (`poolSize = 10`, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green): деонтическая защита сервисных интерфейсов наблюдателя (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green), `GeminiProjectObserverServiceTest` (1/1 green): попперовская фальсификация вывода наблюдателя из эксплуатации и инертность стабов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` / D004).
     * **Группа 2: Секция XIX — Сайдкары: механизмы вне бэкенда (51 тест, 5 сьютов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29 green), `ObservationHostingDemarcationLaw26Test` (3/3 green): сайдкар `launcher.py` и `RuntimeLauncherClient`, сетевая изоляция хостинга наблюдений по Закону 26, memory bound и port remap (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6 green), `JudgmentAgentClientLaw17Test` (4/4 green): прокси суждений `judgment-proxy/server.js`, детерминированная валидация, теневой аудит и честный provenance без ложной подмены эвристики на суждение модели (`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` / D009, D017);
       - `MLPredictionServiceClientTest` (9/9 green): сайдкар предсказаний `PredictionService.py`, локальные эмбеддинги и эвристики узких мест без потери цепочки происхождения данных (`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XX — Хранилища, которые держат поток (50 тестов, 7 сьютов):**
       - `DesignShopCycleRepositoryIntegrationTest` (6/6 green): эксклюзивная аренда старта дизайн-цикла через атомарный CAS (`startCycleClaimedAt IS NULL` and `lastWasReady = false`), исключающая параллельные циклы генерации (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `TaskClaimServiceTest` (8/8 green), `ClaimServiceRaceGuardTest` (5/5 green), `ClaimServiceFalsificationTest` (6/6 green): детерминированная выборка `findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc`, CAS-изоляция и агрегированный подсчет протухших аренд (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProjectEventLogRetentionServiceTest` (10/10 green): постраничное отсечение по потолку емкости долговечного журнала проекта без подъема излишка в память JVM (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green): дедупликация и персистентность контекстных фрагментов по `contentHash` и `sourceRef` без наложения устаревших фрагментов (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GreetingControllerIntegrationTest` (5/5 green): строгая изоляция демонстрационного контура `greetings` от производственных метрик потока фабрики (`KIT_DEROUZ_13_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сайдкаров, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D017, D002, D007, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны вывода наблюдателя из эксплуатации (`V58AndV111ObserverDecommissionFalsificationTest`), изоляции рантайма (`ObservationHostingDemarcationLaw26Test`), гонок аренды задач (`ClaimServiceFalsificationTest`), коллизий контекстных чанков (`ContextChunkHashAndFileScopeConflictFalsificationTest`) и удержания журнала (`ProjectEventLogRetentionServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman & DeRose]:**
  * Лок уровней абстракции открытых читающих GET-контроллеров без побочных мутаций, надежная цепочка журнала проекта и строгая изоляция демо-контура от метрик хребта фабрики (`GreetingControllerIntegrationTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при зелёных тестах (exclusionary reasons);**
  * Деонтические заслоны доступа к API наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), запрет wildcard в CORS (`WebConfigCorsTest`) и изоляция хостинга по Закону 26 (`ObservationHostingDemarcationLaw26Test`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Shaffer & Varzi]:**
  * Атомарный захват циклов старта дизайн-цеха через CAS (`DesignShopCycleRepositoryIntegrationTest`), монопольное владение пулом планировщика (`SchedulingConfigTest`) и инертность стабов (`GeminiProjectObserverServiceTest`).
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009 / D017 Substitution proof failure / Church]:**
  * Честный provenance суждений и прозрачность фоллбэков прокси суждений (`JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Детерминированное различение факта существования активного притязания от требования единственности в схеме (`TaskClaimServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Разделение категорий сетевых обрывов, клиентских ошибок и внутренних аварий сервера в `GlobalExceptionHandlerTest`.
- **`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 Belief update without ledger / Levi]:**
  * Фиксация происхождения фактов и свидетельств узких мест в сайдкаре ML (`MLPredictionServiceClientTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XVII, XVIIб, XIX и XX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
   ```
2. Подтвердить 100% green результат (**158/158 green в 26 тест-сьютах**).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать отметки Ступени 4 по Кластеру Секций XVII, XVIIб, XIX и XX в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(sections-xvii-xx): complete Stage 4 batch verification (D008, D010, D006)`.
