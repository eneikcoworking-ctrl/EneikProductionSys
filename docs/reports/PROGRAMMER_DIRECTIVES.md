# Пакетная директива L1/L3 -> L2: Секция XIIа (Управляющая поверхность: чем поток останавливают снаружи)

**Дата:** 2026-10-15 19:14 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XII («Журнал, память, санитария») полностью заверена и закрыта инженером (64/64 green в коммите `3370d2b`). Настоящая директива охватывает целиком следующее фундаментальное семейство конвейера фабрики — **Секцию XIIа («Управляющая поверхность: чем поток останавливают снаружи» / Rest API Control Surface, Mutating Endpoints, Operational Boundary, Authorization & Audit Guards)**:
   - **Контур XIIа.1. Управление пулом воркеров и ключами (`AccountController`):**
     * Топологическая граница и маскирование секретов (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006): `AccountDto` никогда не возвращает сырые API-ключи воркеров наружу;
     * Институциональный аудит мутаций (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007): любое изменение `enabled`, статуса или удаление аккаунта фиксируется в `defect_journal` (`ACCOUNT_DELETION_RULE`);
     * Канонический пул 13 ролей: диспетчеризация защищена от несанкционированного дрейфа емкости.
   - **Контур XIIа.2. Управление жизненным циклом проекта (`ProjectController`):**
     * Институциональный факт-регистр Серла (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007): статус проекта меняется только авторизованными действиями (`pause`, `activate`, `accept`, `reset-for-redecomposition`);
     * Деонтические запреты Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): невалидные и конфликтные переходы жестко транслируются в HTTP 409 Conflict (`OperationalPolicyDeniedException`) со структурированным телом отказа.
   - **Контур XIIа.3. Достоверность терминальных исходов задач (`ClaimController`):**
     * Конструктивный объект доказательства (`PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` / D007): терминальные переходы (`fail`, `complete`, `close-failed`, `release`) защищены матрицей прав и обязанностей (`ClaimRightsDutiesMatrix`);
     * Необратимость терминального статуса (Закон 20 / Белнап D012): атомарный CAS `writeStatusUnlessTerminal` исключает гонки и воскрешение завершенных задач.
   - **Контур XIIа.4. Санитария и архивирование требований (`WishlistController`):**
     * Лок уровней абстракции Голдмана (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010): удаление требования не искажает знаменатель доставки;
     * Разделение мягкого архивирования (`dismiss`) с сохранением истории и санитарного удаления фантомов (`purge ghosts`) с гарантией HTTP 404 NOT_FOUND на несуществующих записях.
   - **Контур XIIа.5. Безопасность настроек и отладочных интерфейсов (`SettingsController`, `SystemStatusController`):**
     * Принципиальная целостность (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): запрет несанкционированного открытия `/api/system-status/sql`;
     * Обязательная аутентификация по `X-API-Key` (HTTP 401 при отсутствии ключа), маскирование секретов и фиксация аудит-лога мутаций настроек.
   - **Контур XIIа.6. Служебная поверхность задач и синхронизация (`InternalTaskController`, `LinearSyncController`):**
     * Архитектурное владение частями целого (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004): защищенная пагинация `Pageable`, точный поиск по Linear ID (`findFirstByLinearIssueId`), исключение OOM при чтении задач;
     * Разграничение полноты синхронизации метаданных Linear и продуктовой готовности доставки.
   - **Контур XIIа.7. Оперативный дашборд и жизненный цикл сессий (`DashboardController`, `JulesSessionController`, `GithubWebhookController`):**
     * Пул сессий и учет емкости (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011): учет квот аккаунтов, корректное освобождение клеймов при отмене;
     * Валидация структуры входящих GitHub Webhook (`SUBSTITUTION_ORACLE` / D009): идемпотентная обработка событий PR, безопасные ответы 200/202 без падений при неизвестных типах событий.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код контроллеров и сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции XIIа уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D004, D009, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация ложных допущений: тесты-заслоны контроллеров (`OperationalPolicyDeniedExceptionFalsificationTest`, `ClaimResultStatusTruthTableFalsificationTest`) обязаны опровергать ложноположительные ответы; попытка обойти политику или перезаписать закрытую задачу обязана завершаться отказом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгое разделение уровней REST-трансляции HTTP, доменных сервисов, сущностей БД и аудита;
  * Запрет искажения знаменателя delivery readiness при удалении/архивировании вишлистов (`WishlistController`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity, Raz / Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров Секции XIIа при проходящих тестах;**
  * Программные деонтические запреты: обязательный HTTP 401 при отсутствии API-ключа к отладочным эндпоинтам, HTTP 409 при нарушении политик перехода проекта.
- **`PER_MARTIN_LEF_01_CONSTRUCTIVE_PROOF_OBJECT` & `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` [D007 Status falsification, Martin-Löf / Searle]:**
  * Терминальный исход задачи требует предъявляемого свидетельства (`ClaimRightsDutiesMatrix`);
  * Мутация состояния проекта или настроек становится институциональным фактом только через авторизованную операцию с записью в аудит-журнал.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict, Shaffer]:**
  * Монопольное владение переходами и защита от гонок: `writeStatusUnlessTerminal`, атомарные CAS и пагинация выборок.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Четырехзначная логика истинности терминальных исходов задач и защита от несанкционированного переопределения состояния.
- **`KRISPIN_RAYT_01_SUBSTITUTION_ORACLE` [D009 Substitution oracle, Wright]:**
  * Защита от подделки внешних событий: валидация подписи и структуры событий GitHub Webhook.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XIIа в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AccountControllerIntegrationTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,WishlistControllerIntegrationTest,SettingsControllerIntegrationTest,SystemStatusControllerTest,InternalTaskControllerTest,DashboardControllerTest,GithubWebhookControllerTest,GoogleAiResourceControllerTest,GreetingControllerIntegrationTest,LinearSyncControllerTest`
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции XIIа (80 тестов):
   - Управление аккаунтами: `AccountControllerIntegrationTest` (5/5);
   - Политика переходов проекта: `OperationalPolicyDeniedExceptionFalsificationTest` (9/9);
   - Матрица прав и обязанностей клеймов: `ClaimRightsDutiesMatrixTest` (7/7);
   - Таблица истинности статусов клеймов: `ClaimResultStatusTruthTableFalsificationTest` (5/5);
   - Предохранитель гонок клеймов: `ClaimServiceRaceGuardTest` (5/5);
   - Санитария и архивирование вишлистов: `WishlistControllerIntegrationTest` (8/8);
   - Безопасность настроек и ключей: `SettingsControllerIntegrationTest` (8/8);
   - Свод системного статуса: `SystemStatusControllerTest` (5/5);
   - Служебные задачи и Linear ID: `InternalTaskControllerTest` (6/6);
   - Оперативный дашборд: `DashboardControllerTest` (4/4);
   - Вебхуки GitHub: `GithubWebhookControllerTest` (9/9);
   - Ресурсы AI: `GoogleAiResourceControllerTest` (6/6);
   - Служебный контроллер: `GreetingControllerIntegrationTest` (3/3);
   - Синхронизация Linear: `LinearSyncControllerTest` (1/1).
3. Добавить канонический блок `### Итог Секции XIIа` в `docs/FACTORY_MECHANISMS.md` (подтверждающий Ступень 4 closed, ideal, 80/80 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
