# Директивы и наряд для L2 (2026-10-16 11:08 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2») успешно заверен инженером (коммит `1f0fd75`, 224/224 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища»)** целиком (26 тест-сьютов, 158 тестов в едином пакетном прогоне в Docker Maven):
     * **Секции XVII и XVIIб: Что здесь мертво/обездвижено и 21 неназванный механизм (57 тестов, 14 сьютов):**
       - `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4), `QualityGateControllerTest` (2/2), `QualityMetricsControllerTest` (3/3), `HomeControllerTest` (1/1), `LinearSyncControllerTest` (2/2), `SystemStatusControllerTest` (3/3) — демаркация открытых читающих поверхностей строго как GET-проекций без изменения состояния (ноль мутаций), исключая дрейф контрактов и смешение уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `GlobalExceptionHandlerTest` (3/3) — параконсистентная изоляция исключений и санитарный перехват без утечки внутренних стеков наружу (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `WebConfigCorsTest` (3/3) — строгая топология CORS с запретом wildcard при credentials (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `SchedulingConfigTest` (2/2) — монопольное владение пулом планировщика (poolSize=10, префикс 'scheduling-task-'), гарантия отсутствия голодания 41 аннотации `@Scheduled` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `V58AndV111ObserverDecommissionFalsificationTest` (6/6), `GeminiProjectObserverServiceTest` (1/1) — попперовская фальсификация вывода старого наблюдателя из эксплуатации: инертный stub, запрет в миграции V111 (`gemini_project_observer_enabled=false`) и отказ в неавторизованном доступе (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006).
     * **Секция XIX: Сайдкары вне бэкенда (51 тест, 5 сьютов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29), `ObservationHostingDemarcationLaw26Test` (3/3) — запуск и изоляция живого продукта клиента через `launcher.py` / `RuntimeLauncherClient`, memory bound, port remap и изоляция host docker socket (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4) — заслоны прокси суждений `judgment-proxy/server.js` / `JudgmentAgentClient`, теневой аудит (`/shadow`), детерминированная валидация вердиктов и оракул подстановок (`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` / D009, D017);
       - `MLPredictionServiceClientTest` (9/9) — обслуживание предсказаний узких мест и эмбеддингов сайдкара ML (`PredictionService.py`) без потери цепочки происхождения данных и смешения эвристик с байесовским обучением (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` / D007).
     * **Секция XX: Хранилища, которые держат поток (50 тестов, 7 сьютов):**
       - `DesignShopCycleRepositoryIntegrationTest` (6/6) — атомарный CAS старта цикла дизайн-цеха (`claimStartCycle`), исключающий конкурентный запуск генераций для одного проекта (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6) — детерминированный выбор активного притязания (`findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc`), сканирование категориальных ошибок кардинальности и изоляция гонок аренды (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProjectEventLogRetentionServiceTest` (10/10) — долговечное удержание и постраничная санитарная обрезка журнала событий проекта без утечки сущностей в память JVM (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10) — защита от наложения и коллизий контекстных чанков по `contentHash` и `sourceRef` (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GreetingControllerIntegrationTest` (5/5) — строгая изоляция вспомогательного demo-контура greetings от производственных метрик потока (`KIT_DEROUZ_13_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, контроллеров, репозиториев, сайдкаров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D009, D007, D012, D017)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: воспроизводимые заслоны целостности декоммиссионирования наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`), гонок аренды задач (`ClaimServiceFalsificationTest`), отсечения журнала и коллизий чанков (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности данных: демаркация читающих контроллеров как GET-проекций без мутаций, сохранение происхождения данных в предсказаниях сайдкара ML и персистентность контекстных чанков.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz & von Wright]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимые деонтические заслоны доступа к наблюдателю, сетевые границы сайдкаров и строгие правила CORS.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Монополия владения частями целого: атомарный CAS аренды старта дизайн-цикла (`DesignShopCycleRepository`), монопольный пул планировщика (`SchedulingConfig`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Предотвращение категориальных ошибок: детерминированная выборка `findFirst` вместо ложного ожидания единственной строки при параллельных притязаниях на задачу (`ClaimRepository`).
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009 & D017 Substitution oracle / Church]:**
  * Прозрачность подстановок: прокси суждения явно маркирует источник вердикта (Gemini, manual, fallback), исключая подмену доказательств.
- **`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 Belief update without ledger / Levi]:**
  * Разделение эвристик и байесовского обучения: предсказания узких мест не выдаются за обученные распределения без журнала наблюдений.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Paraconsistent quarantine / Belnap]:**
  * Параконсистентная изоляция сбоев: перехват необработанных исключений (`GlobalExceptionHandler`) без падения фабрики и раскрытия стеков.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций XVII, XVIIб, XIX и XX:**
   - **Docker Maven (158 тестов, 26 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
     ```
2. **Верификация результата:**
   - Строго **158/158 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XVII, XVIIб, XIX и XX в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XVII, XVIIб, XIX и XX в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xvii-xix-xx): complete Stage 4 batch verification (D008, D010, D006)`.
