# Директивы и наряд для L2 (2026-10-16 18:51 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 предписаний L2») успешно заверен инженером (коммит `7c9471f1`, 224/224 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера механизмов — **Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные механизмы, планировщик и читающие контроллеры, сайдкары вне бэкенда и ключевые хранилища потока»)** целиком (**158 тестов в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секции XVII и XVIIб — Обездвиженные механизмы, планировщик и читающие контроллеры (57 тестов, 14 сьютов):**
       - Демаркация открытых поверхностей контроллеров strictly как GET/no-mutation проекций: `DashboardControllerTest` (2/2), `CommandDashboardControllerTest` (4/4), `QualityGateControllerTest` (2/2), `QualityMetricsControllerTest` (3/3), `HomeControllerTest` (1/1), `LinearSyncControllerTest` (2/2), `SystemStatusControllerTest` (3/3);
       - Санитарная изоляция исключений и веб-конфигурация CORS: `GlobalExceptionHandlerTest` (3/3), `WebConfigCorsTest` (3/3);
       - Пул планировщика фоновых задач фабрики: `SchedulingConfigTest` (2/2);
       - Декоммиссия наблюдателя Gemini и защищенные сервисные эндпоинты: `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `V58AndV111ObserverDecommissionFalsificationTest` (6/6), `GeminiProjectObserverServiceTest` (1/1).
     * **Группа 2: Секция XIX — Сайдкары: механизмы вне бэкенда (51 тест, 5 сьютов):**
       - Запуск живого продукта клиента и изоляция продуктового хостинга по Закону 26: `ClientRuntimeObservabilityServiceTest` (29/29), `ObservationHostingDemarcationLaw26Test` (3/3);
       - Клиент к прокси суждений и отбор свидетельств по Закону 17: `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4);
       - Клиент к предсказателю ML FastEmbed с предохранителем от фиктивных векторов: `MLPredictionServiceClientTest` (9/9).
     * **Группа 3: Секция XX — Хранилища, которые держат поток (50 тестов, 7 сьютов):**
       - Топология владения циклами дизайн-цеха: `DesignShopCycleRepositoryIntegrationTest` (6/6);
       - Притязания на задачи, атомарный захват и защита от гонок: `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6);
       - Удержание долговечного журнала проекта: `ProjectEventLogRetentionServiceTest` (10/10);
       - Защита от коллизий и хеширование контекстных чанков: `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10);
       - Демонстрационное хранилище приветствий: `GreetingControllerIntegrationTest` (5/5).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей, конфигураций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация вывода из эксплуатации наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`), фальсификация притязаний на задачи (`ClaimServiceFalsificationTest`) и заслон коллизий хешей контекста (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции: открытые поверхности контроллеров фабрики служат исключительно проекциями чтения без побочных эффектов мутации (`DashboardControllerTest`, `SystemStatusControllerTest`); надежность цепочки происхождения векторов ML без нулевых или фиктивных подмен (`MLPredictionServiceClientTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Закон 26 — категорическая демаркация хостинга наблюдений и продуктового рантайма (`ObservationHostingDemarcationLaw26Test`), топологическая защита эндпоинтов и отсечение неавторизованных вызовов.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER` [D004 Part-whole ownership / Varzi & Shaffer]:**
  * Топология владения частями целого и атомарный захват ресурсов в хранилище (`ClaimServiceRaceGuardTest`, `DesignShopCycleRepositoryIntegrationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначные таблицы истинности исходов притязаний и санитарный перехват ошибок без искажения статуса ответа.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: наблюдение за живым продуктом не искажает физическое состояние продукта и базы данных.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XVII, XVIIб, XIX и XX в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DashboardControllerTest,CommandDashboardControllerTest,QualityGateControllerTest,QualityMetricsControllerTest,HomeControllerTest,LinearSyncControllerTest,SystemStatusControllerTest,GlobalExceptionHandlerTest,WebConfigCorsTest,SchedulingConfigTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,V58AndV111ObserverDecommissionFalsificationTest,GeminiProjectObserverServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest
   ```
2. Подтвердить 100% green (158/158 green, 0 failures, 0 errors, 0 skipped).
3. Обеспечить строго 0 строк изменений рабочего кода сервисов, моделей, контроллеров, хранилищ и скриптов фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xvii-xx): complete Stage 4 batch verification (D008, D010, D006)`.
