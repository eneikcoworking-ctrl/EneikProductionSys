# Пакетная директива L1/L3 -> L2: Секция XIII (Взаимодействие: кто пишет каждое состояние)

**Дата:** 2026-10-15 19:24 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XIIа («Управляющая поверхность: чем поток останавливают снаружи») полностью заверена и закрыта инженером (80/80 green в коммите `29bc602`). Настоящая директива охватывает целиком следующее фундаментальное семейство конвейера фабрики — **Секцию XIII («Взаимодействие: кто пишет каждое состояние» / Factory Writable State Owner-Map, Transition Boundaries & Writers Demarcation)**:
   - **Контур XIII.1. Карта владельцев записываемого состояния фабрики (`PART_WHOLE_OWNERSHIP` / D004):**
     * Полная модель транзакционной изоляции и владения для всех 46 JPA-репозиториев (`src/main/java/com/eneik/production/repositories/*Repository.java`);
     * Каждое изменяемое состояние имеет строго объявленного монопольного владельца; запрещены неименованные писатели в обход CAS (`FOR UPDATE SKIP LOCKED`, `compareAndSetStatus`);
     * Заслоны в `TaskRepositoryIntegrationTest`, `AccountRepositoryIntegrationTest`, `AccountRepositoryFalsificationTest`, `DesignShopCycleRepositoryIntegrationTest`.
   - **Контур XIII.2. Владение переходами заявок (`WishlistRepository` / `WishlistEntity.status` / `finalizingSince`):**
     * Конструктивный объект доказательства (`PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` / D007): переходы статуса вишлиста (`pending -> compiling -> finalizing -> completed/failed`) происходят строго через атомарные CAS (`compareAndSetStatus`, `compareAndSetStatusWithTimestamp`);
     * Защита финализации и продление аренды: `renewFinalizingLeases`, `renewFinalizingLease`; безопасный подбор зависших задач через `StrandedFinalizingSweepService` и `StrandedFinalizingSweepServiceFalsificationTest`.
   - **Контур XIII.3. Владение переходами задач (`TaskRepository` / `TaskEntity.status` / `TaskStatus`):**
     * Таблица истинности и необратимость терминальных исходов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, Закон 20): монопольный метод `writeStatusUnlessTerminalAt` исключает воскрешение завершенных задач (`FAILED`, `COMPLETED`, `CANCELLED`);
     * Атомарный перевод статусов `compareAndSetStatusAt` защищает параллельные воркфлоу диспетчеризации от гонок.
   - **Контур XIII.4. Владение жизнью Jules-сессии (`SessionLifecycleService` / `JulesSessionRepository`):**
     * Совместное обязательство (`MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` / D004): монопольное владение жизненным циклом удаленной сессии закреплено за `SessionLifecycleService`;
     * Атомарный захват воркфлоу `claimPrOpenedWorkflow` предотвращает дублирование обработки открытых PR; демаркация удаленного статуса от локального факт-регистра (`remoteDeletedAt`, `closedAt`, `closureReason`).
   - **Контур XIII.5. Демаркация прав и обязанностей взаимодействующих сервисов:**
     * Лок уровней абстракции Голдмана (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010): сервисы свидетельств и арбитража (`DeliveredWorkJudgmentService`, `DeliveryRealityProducerService`, `KaizenService`, `ProductLaunchabilityService`) поставляют факты и суждения, но не подменяют монопольного владельца перехода жизненного цикла;
     * Диспетчеризация (`JulesDispatchService`), оркестрация (`AutoMergeService`), восстановление (`PlannedWorkRecoveryService`) и зачистка (`BranchGarbageCollectorService`) действуют строго через объявленные границы состояний.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов и репозиториев (`src/main/java`) при проходящих тестах!
   - Механизмы Секции XIII уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D007, D012, D011, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны владения состоянием (`SessionLifecycleServiceFalsificationTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `AccountRepositoryFalsificationTest`, `ClaimResultStatusTruthTableFalsificationTest`) обязаны опровергать ложноположительные суждения; любая попытка мутировать состояние в обход владельца или перезаписать терминальную задачу обязана завершаться отказом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгое разделение уровней агрегации суждений (evidence/verdict), сущностей базы данных и контроллеров переходов жизненного цикла;
  * Неизменность цепочки надежности: факты суждения о доставке не становятся автоматически мутацией статуса вишлиста без явного санкционированного перехода.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity, Raz / Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и репозиториев Секции XIII при проходящих тестах;**
  * Топологическая граница транзакций: изоляция вызовов `save/delete` под защитой CAS-инвариантов.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` [D004 Concurrency conflict, Shaffer / Gilbert]:**
  * Архитектурное владение частями целого: явный владелец каждого поля и перехода состояния исключает взаимные блокировки и гонки писателей.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Таблица истинности статусов: Закон 20 о необратимости терминальных исходов задач (`writeStatusUnlessTerminalAt`).
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` & `PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` [D007 Status falsification, Searle / Martin-Löf]:**
  * Конструктивное доказательство перехода статуса через предъявление свидетельства финализации и CAS-метки времени.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XIII в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TaskRepositoryIntegrationTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,DesignShopCycleRepositoryIntegrationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,ClaimServiceRaceGuardTest,ClaimResultStatusTruthTableFalsificationTest,ClaimRightsDutiesMatrixTest,AutoMergeLaw20InvariantS4Test`
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции XIII (79 тестов):
   - Границы репозиториев: `TaskRepositoryIntegrationTest` (4/4), `AccountRepositoryIntegrationTest` (8/8), `AccountRepositoryFalsificationTest` (8/8), `DesignShopCycleRepositoryIntegrationTest` (6/6);
   - Жизненный цикл сессий Jules: `SessionLifecycleServiceTest` (8/8), `SessionLifecycleServiceFalsificationTest` (6/6);
   - Уборка зависших финализаций: `StrandedFinalizingSweepServiceTest` (15/15), `StrandedFinalizingSweepServiceFalsificationTest` (4/4);
   - Предохранители гонок и матрица клеймов: `ClaimServiceRaceGuardTest` (5/5), `ClaimResultStatusTruthTableFalsificationTest` (5/5), `ClaimRightsDutiesMatrixTest` (7/7);
   - Необратимость терминальных исходов задач (Закон 20): `AutoMergeLaw20InvariantS4Test` (3/3).
3. Добавить канонический блок `### Итог Секции XIII` в `docs/FACTORY_MECHANISMS.md` (подтверждающий Ступень 4 closed, ideal, 79/79 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
