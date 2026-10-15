# Пакетная директива L1/L3 -> L2: Секция XVI/XXIX (Архитектурные предписания инвариантов владения и Цех дизайна: экранные артефакты, токены и продвижение mockup)

**Дата:** 2026-10-15 13:58 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII (Flow & Metrics / Самопроверка) полностью замерена и зафиксирована инженером в статусе ideal (коммит `6c5ef27`, 270/270 green в Docker Maven). Настоящая директива охватывает целиком следующий открытый комплекс фабрики — **Секцию XVI (Архитектурные предписания инвариантов владения, тянущий контур и гейты качества)** и **Секцию XXIX (Цех дизайна: экранные артефакты, аудит дизайн-токенов и продвижение mockup)**:
   - **Архитектурные границы и владение состоянием (Предписания 1–4, D004 Shaffer):**
     * `WishlistRepository` / `ProjectFlowService` — детерминированный хозяин переходов заявок, атомарный CAS `compareAndSetStatus`, исключение гонок `pending`/`compiling`;
     * `TaskRepository` — монопольный хозяин переходов задач, инвариант `done_is_not_delivery`, атомарный заслон `writeStatusUnlessTerminalAt`;
     * `JulesSessionRepository` / `SessionLifecycleService` — единый хозяин жизни сессий Jules, атомарный захват лиза `claimPrOpenedWorkflow`, исключение зомби-сессий.
   - **Тянущий контур и доменные гейты (Предписания 5–7, 38–41, D008 Popper, D002 Ryle):**
     * `PlatformSelfReferenceDetector` — надежное отсечение внутризаводского шума от продуктового бэклога заказчика (D002 Category Error);
     * `GateOrchestrator` / `VerificationEvidenceGate` — демаркация отсутствия проверок от успешного прохождения, фальсифицирующие гейты качества;
     * `LeanValue`, `RepositoryStackAnalyzer`, `TargetContext` — Белнап-многозначная логика, нулевой шум о заказчике при отказе авторизации, изоляция контекста исполнителя.
   - **Цех дизайна, артефакты экранов и марка (Секция XXIX, D015 Belnap, D009 Belnap):**
     * `DesignAssetService` (`generateAsset`, `generateViaStitch`, `auditExistingDrafts`) — запрет деградации до картинок при `requireImplementableHtml=true`, отсечение off-token HTML до коммита в GitHub;
     * `DesignConsistencyAuditService` — вычисление коэффициента трассировки токенов `tokenTraceRatio` и Jaccard-сходства экранов, честный `CANNOT_JUDGE` при отсутствии базовых токенов;
     * `StitchClient` — JSON-RPC клиент к Stitch MCP, сохранение `screenId`, изоляция проектов;
     * `LayoutGeometryAuditService` — заслон коллизий, масштабируемости вьюпортов и близости элементов;
     * `DesignShopOrchestrationService` — старт цикла строго по нарастающему фронту готовности (`isReady && !lastWasReady`), атомарный `claimStartCycle`;
     * `JulesDispatchService.completeDesignReview` — продвижение утвержденного черновика из `design/draft` в `design/approved` strictly по вердикту `approve` с байтовым тождеством.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и сущностей при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего кода признается вредительством и архитектурным дефектом D006.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Верификация через фальсифицирующие заслоны: тест падает, если попытка перевести терминальную задачу в работу принимается хранилищем;
  * Заслон `DesignAssetService` падает, если генератор пытается вернуть картинку вместо затребованного `mockup.html`;
  * Заслон `DesignConsistencyAuditService` отвергает офф-токен экраны и фиксирует `CANNOT_JUDGE` на пустых SPA-оболочках вместо фальшивого `green`.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Защита родословной данных: артефакты дизайна трассируются до объявленных токенов бренда и коммитятся в целевой репозиторий заказчика, а не оседают локально;
  * Лок уровней абстракции: факт наличия черновика на диске бэкенда не подменяет статус утвержденного артефакта в ветке `design/approved`;
  * Детерминированное владение переходами состояний: четкое разделение роли регистратора фактов и владельца жизненного цикла (Шаффер D004).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов Секций XVI и XXIX;**
  * Деонтический запрет на обход атомарных CAS-переходов `TaskRepository.writeStatusUnlessTerminalAt` и `WishlistRepository.compareAndSetStatus`;
  * Запрет на неавторизованные мутации состояний вне объявленного сервиса-хозяина.

---

## 3. Пакетный наряд по механизмам Секции XVI/XXIX

1. **Владение состоянием и жизненным циклом (`WishlistRepository`, `TaskRepository`, `JulesSessionRepository`, `SessionLifecycleService`):**
   - **Проба:** Замерить атомарность переходов состояний, CAS-заслоны и неизменность терминальных статусов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `TaskRepositoryTest`, `WishlistRepositoryTest`, `SessionLifecycleServiceTest`.
2. **Тянущий контур и доменная демаркация (`PlatformSelfReferenceDetector`, `VerificationEvidenceGate`, `TargetContext`):**
   - **Проба:** Замерить отсечение заводских находок от бэклога заказчика, фальсифицируемость гейтов и многозначность Белнапа.
   - **Инвариант:** Рабочий код не трогать. Заслоны `PlatformSelfReferenceDetectorTest`, `VerificationEvidenceGateTest`, `TargetContextTest`.
3. **Цех дизайна и генерация артефактов (`DesignAssetService`, `StitchClient`, `GoogleAiResourceService`):**
   - **Проба:** Замерить генерацию с `requireImplementableHtml=true`, запрет отката на image-only модели и передачу токенов в prompt.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DesignAssetServiceTest`, `StitchClientTest`.
4. **Аудит консистентности и геометрия вьюпортов (`DesignConsistencyAuditService`, `LayoutGeometryAuditService`):**
   - **Проба:** Замерить подсчет `tokenTraceRatio`, кросс-экранный Jaccard и статус `CANNOT_JUDGE` при нехватке базовых токенов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DesignConsistencyAuditServiceTest`, `LayoutGeometryAuditServiceTest`.
5. **Оркестрация цикла дизайна и продвижение утвержденных экранов (`DesignShopOrchestrationService`, `JulesDispatchService.completeDesignReview`, `DesignExcellenceGate`):**
   - **Проба:** Замерить атомарный захват цикла `claimStartCycle`, строгий вердикт `approve` для переноса в `design/approved` и проверку Playwright-скриншотов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DesignShopOrchestrationServiceTest`, `JulesDispatchServiceTest`, `DesignExcellenceGateTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов Секции XVI/XXIX в Docker Maven (`-m 2g`).
2. Внести отметку `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(design-shop): complete Section XVI/XXIX batch verification (D008, D010, D006)`.
