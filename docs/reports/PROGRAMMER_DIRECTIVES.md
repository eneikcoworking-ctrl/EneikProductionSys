# Директивы и наряд для L2 (2026-10-16 09:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию/кластер механизмов целиком.
   - Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 предписаний») успешно заверен инженером (коммит `767c8b2`, 224/224 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища»)** целиком (26 тест-сьютов, 158 тестов в едином пакетном прогоне Docker Maven):
     * **Секция XVII и XVIIб: Обездвиженные компоненты, планировщик и читающие контроллеры (57 тестов, 14 сьютов):**
       - `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4), `QualityGateControllerTest` (2/2), `QualityMetricsControllerTest` (3/3), `HomeControllerTest` (1/1), `LinearSyncControllerTest` (2/2), `SystemStatusControllerTest` (3/3) — демаркация открытых поверхностей чтения строго как GET-проекций без мутаций состояния (D010 Goldman, D006 Varzi);
       - `GlobalExceptionHandlerTest` (3/3) — санитарный перехват исключений без утечки стеков и смешения категорий сбоев (D012 Belnap);
       - `WebConfigCorsTest` (3/3) — строгая топология CORS с запретом wildcard при credentials (D006 Raz);
       - `SchedulingConfigTest` (2/2) — единый пул планировщика (`poolSize = 10`), отсутствие голодания фоновых таймеров фабрики (D004 Shaffer);
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9) & `InternalGeminiObserverControllerTest` (16/16) — деонтический запрет неавторизованного доступа к внутреннему наблюдателю (D006 Raz);
       - `V58AndV111ObserverDecommissionFalsificationTest` (6/6) & `GeminiProjectObserverServiceTest` (1/1) — попперовская фальсификация вывода наблюдателя из эксплуатации через миграцию V111 и превращение сервиса в инертный stub (D008 Popper, D006 Raz).
     * **Секция XIX: Сайдкары вне бэкенда (51 тест, 5 сьютов):**
       - `ClientRuntimeObservabilityServiceTest` (29/29) & `ObservationHostingDemarcationLaw26Test` (3/3) — изоляция хостинга наблюдений и запуск живого продукта клиента через `RuntimeLauncherClient` / `launcher.py` (D006 Raz, D008 Popper);
       - `JudgmentAgentClientTest` (6/6) & `JudgmentAgentClientLaw17Test` (4/4) — заслоны прокси суждений `judgment-proxy/server.js` с соблюдением деонтических правил и принципов кооперации Грайса (D008 Popper, D006 Raz);
       - `MLPredictionServiceClientTest` (9/9) — обслуживание эндпоинтов предсказаний и эмбеддингов сайдкара ML без потери цепочки происхождения данных (D010 Goldman, D013 Wittgenstein).
     * **Секция XX: Хранилища, которые держат поток (50 тестов, 7 сьютов):**
       - `DesignShopCycleRepositoryIntegrationTest` (6/6) — атомарное взятие и освобождение старт-цикла дизайн-цеха с защитой от гонок (D004 Shaffer);
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6) — защита от гонок при захвате задач и строгое разделение категорий притязаний (D002 Ryle, D004 Varzi, D006 Raz, D008 Popper);
       - `ProjectEventLogRetentionServiceTest` (10/10) — политика удержания журнала событий и персистентность следов потока (D010 Goldman);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10) — защита от коллизий контекстных чанков и фальсификация конфликтов файлового скоупа (D008 Popper, D010 Goldman);
       - `GreetingControllerIntegrationTest` (5/5) — изоляция вспомогательного хранилища `GreetingRepository` от доменных структур ядра (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, сайдкаров, репозиториев и контроллеров (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны воспроизводимо падать при реактивации выключенного наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`), нарушении демаркации хостинга (`ObservationHostingDemarcationLaw26Test`), гонках притязаний (`ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`) и коллизиях чанков контекста (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: читающие контроллеры демаркированы строго как GET-проекции без изменения состояния (ноль мутаций); сохранение происхождения данных в `MLPredictionServiceClientTest` и `ProjectEventLogRetentionServiceTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты на уровне кода: деонтический запрет неавторизованного доступа к внутреннему наблюдателю (`InternalGeminiObserverSecurityIntegrationTest`), строгая топология CORS (`WebConfigCorsTest`), демаркация хостинга наблюдений (`ObservationHostingDemarcationLaw26Test`).
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Недопущение категориальных ошибок: строгое разграничение категорий притязаний на задачи (`TaskClaimServiceTest`, `ClaimServiceFalsificationTest`), исключение смешения ошибок рантайма и бизнес-отказов.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHON_SHEFER_02_PARTIAL_ORDER_METRICS` [D004 Part-whole ownership / Varzi & Shaffer]:**
  * Мереология и инкапсуляция: атомарный цикл старта дизайн-цеха (`DesignShopCycleRepositoryIntegrationTest`), изоляция пула потоков планировщика (`SchedulingConfigTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная логика истинности в обработчиках исключений (`GlobalExceptionHandlerTest`): отказ, ошибка клиента, неопределенность и внутренняя авария разделены без усечения категорий.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: телеметрия сайдкаров (`MLPredictionServiceClientTest`, `ClientRuntimeObservabilityServiceTest`) фиксирует физическое поведение сервисов, а не догадки о них.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций XVII, XVIIб, XIX и XX:**
   - **Docker Maven (158 тестов, 26 сьютов):**
     ```bash
     docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
     ```
2. **Верификация результата:**
   - Строго **158/158 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать замер и статус `ideal` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
   - Записать краткий отчет (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Сделать семантический коммит `feat(sections-xvii-xix-xx): complete Stage 4 batch verification (D008, D010, D006)`.
