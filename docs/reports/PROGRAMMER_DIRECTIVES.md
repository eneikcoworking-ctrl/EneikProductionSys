# Пакетная директива L1/L3 -> L2: Кластер Секций XVII, XVIIб, XIX и XX — Обездвиженные компоненты, читающие контроллеры, сайдкары и хранилища потока

**Дата:** 2026-10-16 07:20 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию/кластер механизмов целиком. Предыдущий Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2») успешно заверен инженером (коммит `85b4c34`, 224/224 green в Docker Maven, 0 строк изменений кода). Настоящая директива открывает пакетный замер следующего кластера фабрики — **Кластера Секций XVII, XVIIб, XIX и XX («Обездвиженные компоненты, читающие контроллеры и планировщик, внешние сайдкары и хранилища потока»)** целиком (158 тестов, 26 сьютов):
   - **Секция XVII и XVIIб: Обездвиженные компоненты, планировщик и читающие контроллеры (57 тестов, 14 сьютов):**
     * `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4), `QualityGateControllerTest` (2/2), `QualityMetricsControllerTest` (3/3), `HomeControllerTest` (1/1), `LinearSyncControllerTest` (2/2), `SystemStatusControllerTest` (3/3) — демаркация открытых поверхностей чтения строго как GET-проекций без мутаций (D010 Goldman, D006 Varzi).
     * `GlobalExceptionHandlerTest` (3/3) — санитарный перехват исключений без утечки стеков и смешения категорий сбоев (D012 Belnap).
     * `WebConfigCorsTest` (3/3) — строгая топология CORS с запретом wildcard при credentials (D006 Raz).
     * `SchedulingConfigTest` (2/2) — единый пул планировщика (`poolSize = 10`), отсутствие голодания фоновых таймеров фабрики (D004 Shaffer).
     * `InternalGeminiObserverSecurityIntegrationTest` (9/9) & `InternalGeminiObserverControllerTest` (16/16) — деонтический запрет неавторизованного доступа к внутреннему наблюдателю (D006 Raz).
     * `V58AndV111ObserverDecommissionFalsificationTest` (6/6) & `GeminiProjectObserverServiceTest` (1/1) — попперовская фальсификация вывода наблюдателя из эксплуатации через миграцию V111 и превращение сервиса в инертный stub (D008 Popper, D006 Raz).
   - **Секция XIX: Сайдкары вне бэкенда (51 тест, 5 сьютов):**
     * `ClientRuntimeObservabilityServiceTest` (29/29) & `ObservationHostingDemarcationLaw26Test` (3/3) — изоляция хостинга наблюдений и запуск живого продукта клиента через `RuntimeLauncherClient` / `launcher.py` (D006 Raz, D008 Popper).
     * `JudgmentAgentClientTest` (6/6) & `JudgmentAgentClientLaw17Test` (4/4) — заслоны прокси суждений `judgment-proxy/server.js` с соблюдением деонтических правил (D009 Church, D017 Church).
     * `MLPredictionServiceClientTest` (9/9) — обслуживание эндпоинтов предсказаний и эмбеддингов сайдкара ML без потери цепочки происхождения данных (D010 Goldman, D007 Levi).
   - **Секция XX: Хранилища, которые держат поток (50 тестов, 7 сьютов):**
     * `DesignShopCycleRepositoryIntegrationTest` (6/6) — атомарное взятие и освобождение старт-цикла дизайн-цеха с защитой от гонок (D004 Shaffer).
     * `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6) — защита от гонок при захвате задач и строгое разделение категорий притязаний (D002 Ryle, D004 Varzi, D006 Raz, D008 Popper).
     * `ProjectEventLogRetentionServiceTest` (10/10) — политика удержания журнала событий и персистентность следов потока (D010 Goldman).
     * `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10) — защита от коллизий контекстных чанков и фальсификация конфликтов файлового скоупа (D008 Popper, D010 Goldman).
     * `GreetingControllerIntegrationTest` (5/5) — изоляция вспомогательного хранилища `GreetingRepository` от доменных структур ядра (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, сайдкаров, репозиториев и контроллеров (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D009, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при реактивации выключенного наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`), нарушении демаркации хостинга (`ObservationHostingDemarcationLaw26Test`), гонках притязаний (`ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`) и коллизиях чанков контекста (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: читающие контроллеры демаркированы строго как GET-проекции без изменения состояния (ноль мутаций); сохранение происхождения данных в `MLPredictionServiceClientTest` и `ProjectEventLogRetentionServiceTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код: эндпоинты защищены авторизационными заслонами, Docker socket изолирован, доступ ограничен.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Shaffer & Varzi]:**
  * Монопольное владение старт-циклами дизайн-цеха в `DesignShopCycleRepositoryIntegrationTest` и управление потоками в `SchedulingConfigTest`.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Семантическое разграничение сущностей притязаний и владения задачами в `TaskClaimServiceTest`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Корректная 4-значная семантика статусов и перехват исключений в `GlobalExceptionHandlerTest`.
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009, D017 / Church]:**
  * Заслоны прокси суждений в `JudgmentAgentClientLaw17Test`.
- **`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 / Levi]:**
  * Обновление убеждений и протоколирование отказов в сервисах ML и журнала.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Кластера Секций XVII, XVIIб, XIX и XX в едином непрерывном запуске Docker Maven (все 26 тест-сьютов, 158 тестов):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Кластер Секций XVII, XVIIб, XIX и XX) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(sections-xvii-xix-xx): complete Stage 4 batch verification (D008, D010, D006)`.
