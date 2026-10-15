# Пакетная директива L1/L3 -> L2: Секция III (Отправка: задача уходит в работу)

**Дата:** 2026-10-15 18:17 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция II («Компиляция: требование становится задачами») полностью замерена и закрыта инженером (159/159 green в коммите `4c9a2f1`). Настоящая директива охватывает целиком следующий фундаментальный массив конвейера фабрики — **Секцию III («Отправка: задача уходит в работу»)**:
   - **Контур III.1. `JulesDispatchService` (Varzi D004, Belnap D012, Raz D006):**
     * Мереологическое разделение владения Варци (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): единое владение агрегатом сессий Jules, отсечение дублирующей диспетчеризации (`already dispatched, skipping duplicate`) при наличии активной сессии.
     * Решётка истинности Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): трёхзначный статус цели `TargetContext` (`PRODUCT_CODEBASE`, `ORCHESTRATOR_SYSTEM`, `UNDETERMINED`). Неизвестный контекст никогда не угадывается и детерминированно прерывает отправку.
     * Деонтические исключающие основания Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): Закон 2 изоляции фабрики и продукта — запрет отправки задач с неустановленной целью или отсутствующим проектом с фиксацией отказного статуса в `JulesSessionEntity`.
   - **Контур III.2. `JulesApiClient` (Belnap D012, Raz D006, Goldman D010):**
     * Многозначная логика Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): четырёхзначный префлайт источников `SourceAvailability` (`VISIBLE`, `MISSING`, `UNKNOWN`) и шестизначная классификация отказов API (`CONCURRENT_CAPACITY_EXHAUSTED`, `DAILY_LIMIT`, `REQUEST_REJECTED`, `PRECONDITION_BLOCKED`, `PRECONDITION_UNSPECIFIED`, `UNCLASSIFIED`).
     * Деонтический запрет Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): категорический запрет сетевого вызова при выключенном `jules_enabled`, отсутствии API-ключа или пустом URL репозитория.
     * Каузальная цепочка Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): Закон 12 (Ground of denial names what it is about) — фиксация длины промпта, источника и стартовой ветки при отказе; сохранение эмпирического HTTP 404 в `checkSessionRaw`.
   - **Контур III.3. `SessionLifecycleService` (Gilbert D004, Raz D006, Goldman D010):**
     * Совместное обязательство Гилберт (`MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` / D004): категорический отказ от локальных фикций cancel — статус отмены связывается с реальным подтверждением удаления во внешнем Jules API, сбой HTTP 500 оставляет `remoteDeletedAt == null`.
     * Деонтический запрет Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): разрыв длинных DB-транзакций через короткие `REQUIRES_NEW`-транзакции proxy `self` вокруг внешнего сетевого HTTP-вызова.
     * Надежность свидетельств Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): регламентная очистка затрагивает исключительно сессии терминальных задач или закрытых проектов, гарантируя неприкосновенность активных задач.
   - **Контур III.4. `PersistentWorkerSessionService` (Varzi D004, Raz D006, Goldman D010):**
     * Топологическая граница Варци (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): чистый сервис учета (bookkeeping) со строго нулевой зависимостью от сетевого транспорта `JulesApiClient`.
     * Деонтический запрет Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): предикат `isIdleAndFresh` категорически блокирует отправку работы при активном пакете (`isBatchInFlight`), исчерпании лимита циклов (`cycleCount >= maxCycles`) или возраста сессии (`maxAgeHours`).
     * Каузальная надежность Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): неразрушающая инспекция пакета (`peekCurrentBatch`) предотвращает утерю задач при авариях воркера.
   - **Контур III.5. `ClaimService` (Hohfeld D006, Belnap D012, Gilbert D007):**
     * Матрица прав и обязанностей Хофельда (`UESLI_HOHFELD_03_RIGHTS_DUTIES_MATRIX` / D006): притязание строго ограничено сроком аренды (`leaseExpiresAt`), атомарный захват строки (`SKIP LOCKED`) исключает двойной захват задачи.
     * Решётка истинности Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): трёхзначная логика допуска на ревью `evaluateReviewAdmission` (`ADMIT`, `DEFER`, `REJECT`), где отсутствие кода откладывает ревью (`DEFER`), а не бракует задачу.
     * Институциональный регистр Гилберт (`MARGARET_GILBERT_04_INSTITUTIONAL_FACT_REGISTER` / D007): фиксация институциональных фактов в едином журнале дефектов при исчерпании бюджета отправки и восстановлении емкости.
   - **Контур III.6. `LeaseWatchdogService` (Hohfeld D012, D006):**
     * Дефектируемое исключение Хофельда (`UESLI_HOHFELD_02_DEFEASIBLE_EXCEPTION_LEDGER` / D012): ежеминутная санитарная жатва просроченных аренд (`@Scheduled(fixedRate = 60000)`), продление аренды при активной сессии Jules, неприкосновенность терминальных задач (`done`) без их воскрешения в очередь.
   - **Контур III.7. `AccountHealthService` (Varzi D004, Belnap D012, Goldman D010):**
     * Агрегатное владение Варци (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): единственный владелец статусов здоровья аккаунтов (`idle`, `api_blocked`, `daily_limited`), строгий запрет на вмешательство в статус занятости `busy` (принадлежащий `ClaimService`).
     * Решётка Белнапа / закон Эшби (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): дефект фабричного промпта (`REQUEST_REJECTED`) категорически не вменяется в вину аккаунту и не блокирует его.
     * Каузальная надежность Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): эмпирический расчет времени кулдауна по выборке журнала дефектов (`median + z * stdDev`) без эвристических констант и меж-аккаунтного смешения.
   - **Контур III.8. `BottleneckAwarePriorityService` & `BottleneckDetectionService` (Dretske D011, Ramsey D005, Goldman D010):**
     * Телеосемантическая обратная связь Дрецке (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011): прямая связь между сигналами ограничений конвейера и приоритетом задач; алерты об аномалиях аренды несут адрес конкретного аккаунта.
     * Ожидаемые потери Рамсея (`FRENK_RAMSEY_01_DECISION_EXPECTED_LOSS` / D005): приоритизация переупорядочивает очередь, но строго не порождает решений об остановке, отказе или блокировке задач.
     * Закон различия факта и ожидания Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): однопроходный сбор статуса пула аккаунтов, немедленная фиксация структурного истощения пула без ожидания таймаута.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции III уже полностью реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004, D007, D011, D005)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны падать при попытке двойного захвата задачи, отправки задачи без проекта, игнорирования истечения аренды или ложного списания здорового аккаунта;
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность: сохранение точного контекста отправки (длина промпта, ветка, репозиторий), однопроходный сбор свидетельств об аккаунтах, эмпирический расчет кулдауна по формуле `median + z * stdDev`;
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции III при проходящих тестах;**
  * Программные запреты: запрет холостых HTTP-вызовов при выключенном Jules или отсутствии ключа, запрет отправки занятому воркеру, запрет остановки задач сервисом приоритетов;
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status ambiguity, Belnap]:**
  * Многозначные решётки истинности: четырёхзначный префлайт доступности репозиториев, шестизначная классификация ответов API, трёхзначный допуск на ревью (`ADMIT`, `DEFER`, `REJECT`), четырёхзначный статус притязания;
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary drift, Varzi]:**
  * Агрегатное владение: `AccountHealthService` не трогает статус занятости `busy`, `PersistentWorkerSessionService` не содержит сетевого транспорта, `BottleneckAwarePriorityService` не пишет в `TaskRepository`;
- **`UESLI_HOHFELD_03_RIGHTS_DUTIES_MATRIX` & `UESLI_HOHFELD_02_DEFEASIBLE_EXCEPTION_LEDGER` [D006, D012 Hohfeld]:**
  * Притязание строго ограничено сроком аренды, активная сессия выступает оправданным основанием продления, терминальные задачи защищены от воскрешения в очередь;
- **`MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` [D004 Joint commitment, Gilbert]:**
  * Запрет локальных фикций cancel: статус отмены синхронизирован с доказанным удалением на стороне внешнего API;
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift, Dretske]:**
  * Сигналы ограничений и аномалий несут точный адрес и семантику возмущения конвейера;
- **`FRENK_RAMSEY_01_DECISION_EXPECTED_LOSS` [D005 Decision loss, Ramsey]:**
  * Переупорядочивание очереди минимизирует системные заторы без искусственного голодания и остановок работы.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции III в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции III:
   - Диспетчеризация и сессии: `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`, `JulesDispatchServiceLaw4MergeEvidenceTest`, `JulesDispatchServiceTocDbrAdmissionTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `DispatchAttemptBudgetTest`, `DispatchRefusalObservabilityLaw8Law12Test`;
   - Внешний API-клиент и жизнь сессий: `JulesApiClientTest`, `JulesApiClientFalsificationTest`, `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `PersistentWorkerSessionServiceTest`, `PersistentWorkerSessionServiceFalsificationTest`;
   - Притязания, аренда и санитария: `ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`, `ClaimResultStatusTruthTableFalsificationTest`, `ClaimRightsDutiesMatrixTest`, `TaskClaimServiceTest`, `TocPriorityClaimTest`, `LeaseWatchdogServiceTest`, `LeaseWatchdogServiceFalsificationTest`;
   - Здоровье аккаунтов: `AccountHealthServiceTest`, `AccountHealthServiceFalsificationTest`, `AccountHealthServiceLaw14Test`;
   - Приоритеты и узкие места: `BottleneckAwarePriorityServiceTest`, `BottleneckAwarePriorityServiceFalsificationTest`, `BottleneckDetectionServiceTest`, `BottleneckDetectionServiceFalsificationTest`.
3. Зафиксировать форму образца и актуализировать статус Секции III в `docs/FACTORY_MECHANISMS.md` (добавить итоговую секцию `### Итог Секции III`, подтверждающую Ступень 4 closed, ideal, 100% green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
