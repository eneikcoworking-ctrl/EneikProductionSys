# Пакетная директива L1/L3 -> L2: Секции XIIа, XIII и XX (Управляющая поверхность, карта владельцев состояний фабрики и хранилища потока)

**Дата:** 2026-10-15 15:02 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII (Flow, Metrics, Continuous Orchestration & Self-Verification) полностью замерена и закрыта инженером в статусе ideal (коммит `db89059`, 290/290 green в Docker Maven). Настоящая директива охватывает целиком следующий открытый комплекс фабрики — **Секцию XIIа (Управляющая поверхность), Секцию XIII (Взаимодействие: архитектурная карта владельцев состояний фабрики) и Секцию XX (Хранилища, держащие поток)**:
   - **Секция XIIа. Управляющая поверхность (Контроллеры, способные удержать или отпустить поток):**
     * `AccountController` — единственное место изменения аккаунтов, маскирование секретов в `AccountDto`, институциональный аудит `ACCOUNT_DELETION_RULE`, изолированный пул аккаунтов по активному проекту;
     * `ProjectController` — пауза, активация, приемка и сброс проектов как институциональный факт с фиксацией в аудит-логе, трансляция недопустимых переходов в HTTP 409 Conflict (`OperationalPolicyDeniedException`);
     * `ClaimController` — терминальные исходы `complete`, `close-failed`, `release` по правилам Белнапа (4-значная логика), атомарный CAS `writeStatusUnlessTerminal`;
     * `WishlistController` — удаление и мягкий `dismiss` с сохранением знаменателя доставки, санитарный purge фантомов с гарантией 404 NOT_FOUND на несуществующих записях;
     * `SettingsController` + `SystemStatusController` — деонтический запрет несанкционированного открытия `/sql`, институциональный факт-регистр настроек;
     * `InternalTaskController` — пагинация `Pageable`, точный поиск по Linear ID (`findFirstByLinearIssueId`), запрет unbounded raw task dumps;
     * `JulesSessionController` — квоты и бюджеты сессий, отмена и освобождение клеймов воркеров;
     * `GithubWebhookController` — валидация структуры событий pull_request, push и ping, идемпотентная обработка повторов.
   - **Секция XIII. Взаимодействие (Карта владельцев записываемого состояния фабрики):**
     * XIII.1 `Карта владельцев записываемого состояния фабрики` (D004 Shaffer) — запрет неименованных писателей, явное сопоставление 46 репозиториев и их методов мутации;
     * XIII.2 `Владение переходами заявок (WishlistStatus)` — переходы `pending` -> `compiling` -> `finalizing` -> `converted_to_task` / `dismissed`, монопольное владение лизом `finalizingSince`, атомарный CAS `compareAndSetStatus`;
     * XIII.3 `Владение переходами задач (TaskStatus)` — конечный автомат переходов задач, абсолютный заслон необратимости терминальных статусов `TaskStatus.isTerminal` (`done`, `failed`, `spike_completed`), атомарный `writeStatusUnlessTerminalAt`, coupling статуса с `updatedAt`, инвариант `done_is_not_delivery`;
     * XIII.4 `Владение жизнью Jules-сессии (JulesSessionEntity)` — единственная граница удаленной жизни сессии в `SessionLifecycleService` (`retireSessionOnly`), атомарный лиз `claimPrOpenedWorkflow`, исключение DB-транзакций при сетевом вызове к Jules API, четкое разделение локального cancel и удаленного `remoteDeletedAt`.
   - **Секция XX. Хранилища, которые держат поток:**
     * XX.1 `DesignShopCycleRepository` — аренда старта дизайн-цикла через CAS `claimStartCycle` (`startCycleClaimedAt IS NULL` and `lastWasReady = false`), симметричный безопасный release и sweep зависших лизов;
     * XX.2 `ClaimRepository` — категоризация запросов (D002 Ryle), детерминированная выборка `findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc`, агрегированный подсчет истекших лизов `expiredCountByAccountSince` без подъема сущностей в память;
     * XX.3 `ProjectEventLogRepository` — надежное удержание долговечного журнала проекта, постраничное отсечение по потолку емкости (`trimToCeiling`) и возрасту (`deleteBefore`), неприкосновенность журнала непринятых проектов;
     * XX.4 `ContextChunkRepository` — персистентность контекстных чанков, атомарное удаление по `sourceRef` перед повторным индексированием, сохранение идентичности RAG-корпуса по `contentHash`;
     * XX.5 `GreetingRepository` — строгая изоляция демо-контура `greetings` от производственных метрик Lean, ТОС и Six Sigma (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов, контроллеров и репозиториев при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается грубейшим нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификационные заслоны терминальности задач: `TaskRepositoryIntegrationTest` и `TaskEntityLaw20Test` подтверждают невозможность перезаписи статусов `done`, `failed`, `spike_completed`;
  * Фальсификация правки заявок: `WishlistServiceFalsificationTest` подтверждает отказ параллельных попыток компиляции одной и той же заявки;
  * Заслон `AccountRepositoryFalsificationTest` подтверждает CAS-изоляцию (`FOR UPDATE SKIP LOCKED`) при выборе аккаунтов под нагрузкой.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции хранилищ: строгая демаркация входных HTTP-команд, доменных сущностей, атомарных CAS-переходов репозиториев и аудит-логов;
  * Изоляция демо-метрик `GreetingRepository` от заводского статистического контроля Lean/ТОС;
  * Сохранение цепочки свидетельств в `ProjectEventLogRepository` без смешивания контекстов `PROJECT:{id}` и `SYSTEM`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода контроллеров, репозиториев и сервисов Секций XIIа, XIII, XX;**
  * Деонтический запрет на обход монопольного владения удаленной жизнью сессий вне `SessionLifecycleService`;
  * Запрет на несанкционированное изменение статуса заявок вне атомарных CAS-методов `WishlistRepository`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -m 2g ... mvn test -Dtest=...`).
2. Подтвердить 100% green статус по всем ключевым классам-заслонам Секций XIIа, XIII, XX:
   - `InternalTaskControllerTest`, `WishlistControllerIntegrationTest`, `AccountLifecycleInvariantTest`, `AccountControllerIntegrationTest`, `ClaimRightsDutiesMatrixTest`, `ClaimResultStatusTruthTableFalsificationTest`, `ClaimServiceRaceGuardTest`, `OperationalPolicyDeniedExceptionFalsificationTest`;
   - `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `TaskClaimServiceTest`, `TaskDuplicateDetectorTest`, `WishlistServiceFalsificationTest`, `TaskEntityLaw20Test`, `TaskAcceptanceCriteriaGuardTest`;
   - `DesignShopCycleRepositoryIntegrationTest`, `TaskRepositoryIntegrationTest`, `AccountRepositoryFalsificationTest`, `AccountRepositoryIntegrationTest`, `ProjectEventLogRetentionServiceTest`, `ContextChunkHashAndFileScopeConflictFalsificationTest`.
3. Зафиксировать форму образца и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md`.
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
