# Анализ текущего флоу и рекомендации по исправлению

## 1. Фактический флоу софта на данный момент

Текущая логика в проекте разделилась на два параллельных и конфликтующих механизма обработки пожеланий (Wishlist) и задач (Tasks), из-за чего новые архитектурные решения фактически обходятся стороной.

**A. Старый флоу (Client Wishes):**
* При нажатии "Add Wish" на фронтенде (`CommandDashboardV2.svelte`), запрос идет на старый эндпоинт `POST /api/projects/{projectId}/wishlist`.
* `ProjectFlowService.addWishlistItem` сохраняет пожелание в устаревшую таблицу `wishlist_items` (сущность `WishlistItemEntity`).
* При нажатии "Orchestrate" (или при периодическом запуске `ContinuousOrchestrationService`), вызывается метод `ProjectFlowService.orchestrate()`.
* Этот метод берет `wishlist_items`, сам определяет роли (через хардкод `chooseBusinessNecessaryRoles`) и **напрямую создает `TaskEntity`**. При этом он **полностью игнорирует `TechnicalLeadCompiler`** и новые строгие правила формализации задач (JTBD, Lean Value, TOC Constraint, Six Sigma Metric, DoD, Acceptance Criteria).

**B. Новый флоу (Onboarding Findings и Continuous Orchestration):**
* При добавлении найденной проблемы (onboarding finding) в Wishlist, запрос идет на новый эндпоинт `POST /api/wishlist`.
* `WishlistService` сохраняет пожелание в новую таблицу `wishlist` (сущность `WishlistEntity`).
* В `ContinuousOrchestrationService` (запускается раз в минуту) есть блок кода, который берет записи из новой таблицы `wishlist` со статусом `pending`.
* Для каждой такой записи он вызывает метод `technicalLeadCompiler.compile(...)`, но **передает туда захардкоженные заглушки** (например, `JTBD = "Когда задача завершена, мы хотим получить совет роли..."`, `Six Sigma = "Defect Rate <= 5%"` и т.д.). Это полностью ломает идею интеллектуальной декомпозиции и компиляции задачи Техлидом.
* Затем вызывается `technicalLeadCompiler.createTaskFromWishlist()`, который валидирует эти хардкодные поля и создает задачи, опираясь на захардкоженную в Java логику (например, `if (isChess) { ... }`).

**Итог текущего состояния:**
Огромное количество недавних правок (введение роли BARCAN-TAG-09, `TechnicalLeadCompiler`, обязательные 6 полей формализации, валидация `DefinitionOfReady`) **фактически не работает для бизнес-задач**. Клиентские фичи идут по старому пути, а системные — заливаются бессмысленными заглушками, чтобы пройти валидацию.

---

## 2. Идеально правильный флоу при создании нового проекта

1. **Инициализация:** Создание проекта (Greenfield или Brownfield). В случае Brownfield проводится аудит (`onboardingAuditService`), и найденные проблемы могут быть добавлены в Wishlist.
2. **Единая точка входа пожеланий (Unified Wishlist):** Все пожелания (от клиента, системные долги, onboarding findings) попадают строго в **одну** таблицу `wishlist` (`WishlistEntity`).
3. **Компиляция Техлидом (Technical Lead Compilation):**
   * Пожелание в статусе `pending` берется в работу AI-агентом (роль Техлида, `BARCAN-TAG-09`).
   * Агент (а не хардкод в Java) анализирует сырое пожелание клиента и заполняет 6 обязательных бизнес-полей: `jtbd`, `lean_value`, `toc_constraint_ref`, `six_sigma_metric`, `dod` (со ссылками на `BARCAN-TAG-XX`), `acceptance_criteria`.
   * Результат сохраняется в `WishlistEntity`, статус меняется.
4. **Генерация задач (Task Creation):**
   * `TechnicalLeadCompiler.createTaskFromWishlist()` проверяет `validateDefinitionOfReady()`.
   * Если валидация пройдена, компилятор разбивает пожелание на конкретные задачи для разных ролей (обязательно включая интеграционную задачу для `BARCAN-TAG-00 Code Guardian`).
   * Задачи сохраняются в таблицу `tasks` со статусом `queued`.
5. **Оркестрация и Выполнение (Dispatch):**
   * `JulesDispatchService` по одной берет `queued` задачи (с учетом цепочки зависимостей `depends_on`), назначает им свободных агентов и отправляет на выполнение (PR).

---

## 3. Рекомендации по починке (Action Plan)

Чтобы вернуть систему в работоспособное состояние и заставить работать новые архитектурные механизмы, необходимо:

1. **Удалить старый Wishlist:** Полностью выпилить сущность `WishlistItemEntity`, репозиторий `WishlistItemRepository` и удалить таблицу `wishlist_items` из схемы.
2. **Перевести фронтенд на новый API:**
   * В `CommandDashboardV2.svelte` метод `addWish()` должен отправлять запрос на `POST /api/wishlist`, используя структуру DTO для `WishlistEntity`.
   * В дашборде загружать список пожеланий через `GET /api/wishlist?projectId=...`.
3. **Удалить старую логику оркестрации:**
   * Убрать метод прямого создания задач через `buildTechnicalLeadTaskSpec()` в `ProjectFlowService.orchestrate()`.
   * "Orchestrate" должен просто инициировать процесс обработки записей из таблицы `wishlist` через `TechnicalLeadCompiler`.
4. **Убрать хардкод из `ContinuousOrchestrationService`:**
   * Код, вызывающий `technicalLeadCompiler.compile(...)` с заглушками, должен быть заменен на вызов реального AI-сервиса (или агента), который на основе промптов для роли `BARCAN-TAG-09` сможет сгенерировать осмысленные `JTBD` и `Acceptance Criteria`.
5. **Убрать хардкод "шахмат" из `TechnicalLeadCompiler`:**
   * Очистить метод `createTaskFromWishlist()` от конструкций вида `if (isChess) { ... }`. Декомпозиция задач должна стать абстрактной и динамической, опирающейся на сгенерированные агентом поля, а не на конкретные фичи в Java-коде.
