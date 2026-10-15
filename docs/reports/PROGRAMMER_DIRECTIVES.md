# Пакетная директива L1/L3 -> L2: Секция XIX и XX (Сайдкары вне бэкенда и хранилища, держащие поток)

**Дата:** 2026-10-15 19:52 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XVII и XVIIб («Обездвиженное, системные конфигурации и открытая поверхность чтения») полностью заверена и закрыта инженером (57/57 green в коммите `d7d2ed4`). Настоящая директива охватывает целиком следующее открытое семейство конвейера фабрики — **Секцию XIX («Сайдкары: механизмы вне бэкенда») и Секцию XX («Хранилища, которые держат поток»)**:
   - **Контур XIX. Сайдкары вне бэкенда:**
     * **XIX.1. `launcher.py` и `RuntimeLauncherClient` (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008):** Точка передачи управления чужому коду и host docker socket; строгая топология портов, memory bound и teardown-семантика. Заслон в `ClientRuntimeObservabilityServiceTest` (29) и `ObservationHostingDemarcationLaw26Test` (3).
     * **XIX.2. `judgment-proxy/server.js` и `JudgmentAgentClient` (`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` / D009, D017):** Прокси суждений с теневым аудитом (`/shadow/inbox`, `/shadow/verdicts`), валидацией схемы и детерминированным graceful fallback. Заслон в `JudgmentAgentClientTest` (6) и `JudgmentAgentClientLaw17Test` (4).
     * **XIX.3. `src/models/ml/PredictionService.py` и `MLPredictionServiceClient` (`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010):** ML-предсказания узких мест и локальные эмбеддинги (`/api/v1/predict/bottleneck`, `/api/v1/embed`) без потери происхождения данных. Заслон в `MLPredictionServiceClientTest` (9).
   - **Контур XX. Хранилища, держащие поток:**
     * **XX.1. `DesignShopCycleRepository` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004):** Аренда старта цикла дизайн-цеха с CAS-изоляцией. Заслон в `DesignShopCycleRepositoryIntegrationTest` (6).
     * **XX.2. `ClaimRepository` (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004):** Притязания на задачу и их кардинальность, детерминированная выборка `findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc` и агрегированный подсчет истекших аренд без подъема сущностей в память JVM. Заслон в `TaskClaimServiceTest` (8), `ClaimServiceRaceGuardTest` (5), `ClaimServiceFalsificationTest` (6).
     * **XX.3. `ProjectEventLogRepository` (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010):** Долговечный журнал проекта и его удержание, безопасная очистка старых записей (`deleteByProjectIdAndCreatedAtBefore`) без разрушения доказательной базы инцидентов. Заслон в `ProjectEventLogRetentionServiceTest` (10).
     * **XX.4. `ContextChunkRepository` (`DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` / D001, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008):** Корпус контекста, векторные строки и проверка хэшей содержимого. Заслон в `ContextChunkHashAndFileScopeConflictFalsificationTest` (10).
     * **XX.5. `GreetingRepository` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010):** Демонстрационный greeting-поток с изоляцией метрик цикла от производственных контуров Lean/TOC. Заслон в `GreetingControllerIntegrationTest` (5).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, репозиториев и сайдкаров (`src/main/java`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы Секций XIX и XX уже находятся в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D007, D002, D001)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны сайдкаров и репозиториев (`ContextChunkHashAndFileScopeConflictFalsificationTest`, `ClaimServiceFalsificationTest`, `ObservationHostingDemarcationLaw26Test`) обязаны опровергать ложные зеленые результаты при нарушении контрактов или изоляции.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman / DeRose]:**
  * Лок уровней абстракции: строгая изоляция демо-потока `GreetingRepository` от промышленных метрик ТОС; доказательная цепочка происхождения данных в `ProjectEventLogRepository` и `MLPredictionServiceClient`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity, Raz / Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов, репозиториев и сайдкаров Секций XIX и XX при проходящих тестах;**
  * Топология границ: сайдкар `launcher.py` изолированно управляет docker socket хоста; клиентские порты и память строго ограничены.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Сканирование категориальных ошибок: вопрос «есть ли активная аренда» адресуется через детерминированный `findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc`, исключая падения от множественных сущностей.
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009 Substitution proof failure, Church]:**
  * Оракул подстановок: прокси суждений `server.js` явно маркирует происхождение ответа (primary vs fallback), предотвращая подмену модельного вердикта неразличимой эвристикой.
- **`AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 Belief update without ledger, Levi]:**
  * Обновление убеждений: предсказания узких мест обязаны сохранять базис свидетельств и не выдавать непараметризованные эвристики за байесовский вывод.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict, Varzi]:**
  * Владение частями целого: атомарные CAS-переходы в `DesignShopCycleRepository` и `ClaimRepository` исключают гонки за ресурсы.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XIX и XX в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,MLPredictionServiceClientTest,DesignShopCycleRepositoryIntegrationTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,ProjectEventLogRetentionServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GreetingControllerIntegrationTest`
2. Зафиксировать результаты в `*Живое:*` блоков Секций XIX и XX в `docs/FACTORY_MECHANISMS.md`, оформить блоки `### Итог Секции XIX` и `### Итог Секции XX`, обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(sidecars-and-storage): complete Stage 4 batch verification (D008, D010, D006)`.
