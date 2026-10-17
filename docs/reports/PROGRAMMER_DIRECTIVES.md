# Директивы и наряд для L2 (2026-10-17 01:13 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2») полностью заверен инженером (коммит `059305fc`, 224/224 green в 26 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
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
       - `TaskClaimServiceTest` (8/8 green), `ClaimServiceRaceGuardTest` (5/5 green), `ClaimServiceFalsificationTest` (6/6 green): детерминированная выборка `findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc`, CAS-изоляция и агрегированный подсчет протухших аренд (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProjectEventLogRetentionServiceTest` (10/10 green): постраничное отсечение по потолку емкости долговечного журнала проекта без подъема излишка в память JVM (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green): дедупликация и персистентность контекстных фрагментов по `contentHash` и `sourceRef` без наложения устаревших фрагментов (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GreetingControllerIntegrationTest` (5/5 green): строгая изоляция демонстрационного контура `greetings` от производственных метрик потока фабрики (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сайдкаров, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D017, D002, D007, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны вывода устаревшего наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`), сетевой изоляции хостинга наблюдений по Закону 26 (`ObservationHostingDemarcationLaw26Test`), гонок аренды задач (`ClaimServiceRaceGuardTest`) и дедупликации контекста (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и демаркация читающих GET-контроллеров без скрытых мутаций, надежность цепи признаков ML-сайдкара, постраничная подрезка журнала проекта без подъема в память JVM, строгая изоляция демонстрационного контура `greetings`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты: топология CORS без wildcard при credentials, деонтическая изоляция интерфейсов наблюдателя, Закон 26 продуктовой изоляции.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Schaffer & Varzi]:**
  * Единый монопольный владелец пула планировщика фабрики (`poolSize = 10`), эксклюзивная аренда старта дизайн-цикла через атомарный CAS, инертность стабов.
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009 & D017 Substitution oracle / Church]:**
  * Честный provenance суждений и прозрачность фоллбэков прокси суждений без подмены эвристики на суждение модели.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Детерминированная кардинальность и выборка притязаний на задачи без категориального смешения активных и протухших аренд.
- **`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 Belief update ledger / Levi]:**
  * Сохранение цепочки происхождения признаков и метрик качества в ML-сайдкаре.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Санитарный перехват непойманных исключений и категоризация сетевых обрывов и клиентских ошибок.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XVII, XVIIб, XIX и XX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   - Заверить все 26 тест-сьютов Кластера (158 тестов: 0 failures, 0 errors, 0 skipped).
2. Соблюсти фундаментальный инвариант Ступени 4:
   - **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Зафиксировать результаты замера в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит и обновить `docs/reports/PROGRAMMER_STATE.md`.
