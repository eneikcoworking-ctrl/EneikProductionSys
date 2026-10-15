# Пакетная директива L1/L3 -> L2: Секция XXIX (Цех дизайна: порождение экранов и сверка их с маркой)

**Дата:** 2026-10-15 12:45 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII (Самопроверка) полностью заверена и закрыта инженером (коммит `05c9768`, 158/158 green). Настоящая директива охватывает целиком следующую открытую секцию фабрики — **Секцию XXIX (Цех дизайна: порождение экранов и сверка их с маркой)** и связанное семейство дизайн-цеха:
   - `DesignAssetService` (`generateAsset`, `generateViaStitch`, `auditExistingDrafts`) — оркестрация порождения экранных артефактов, Stitch vs fallback, аудит черновиков;
   - `DesignConsistencyAuditService` — расчет trace ratio, cross-screen Jaccard, исключение aesthetic drift (D015);
   - `StitchClient` — JSON-RPC клиент к Stitch MCP, сохранение screenId при неготовом HTML;
   - `GoogleAiResourceService` (nano-banana fallback) — генератор изображений, строго изолированный для non-HTML вызовов;
   - `GitHubPullRequestService.commitFile/deleteFile/copyFile` — репозиторный носитель артефактов дизайна (`design/draft` -> `design/approved`);
   - `DesignShopOrchestrationService` (`hasImplementableHtml`, `startCycle`) — планировщик дизайн-цикла, атомарная аренда старта;
   - `JulesDispatchService.completeDesignReview` — продвижение утвержденных макетов при вердикте `approve`;
   - `LayoutGeometryAuditService` — аудит геометрии, коллизий и viewport;
   - `DesignSystemFalsificationService` — фальсификация дизайн-системы с фиксацией в dismissed audit-trail;
   - `DesignDriftMonitorService` — наблюдение за live drift работающего продукта через окно runtime-наблюдения;
   - `DesignExcellenceGate` — заслон качества экранов;
   - `DesignShopCycleRepository` — атомарный CAS-клейм старта цикла (`claimStartCycle`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов при проходящих тестах!
   - Любая модификация рабочего кода допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего системный инвариант. Если тесты зеленые — рабочий код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * `DesignSystemFalsificationService` и `DesignExcellenceGate`: фальсифицирующие заслоны качества макетов и дизайн-систем;
  * Категорический отказ от фальшивых подтверждений: статус `CANNOT_JUDGE` при пустых SPA-оболочках или отсутствии baseline вместо ложного green/red;
  * Строгая выбраковка off-token артефактов до их фиксации в репозитории клиента.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Непрерывная цепочка свидетельств: удаленный экран Stitch -> локальный черновик на диске -> репозиторный артефакт в GitHub (`design/draft/.../mockup.html`) -> продвижение в `design/approved`;
  * Лок уровней абстракции: визуальные токены проекта извлекаются из реального baseline репозитория клиента, а не из глобальных заводских констант;
  * Геометрический аудит (`LayoutGeometryAuditService`) строго изолирован от бизнес-приемки.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов при проходящих тестах;**
  * Запрет подмены артефакта: когда вызывающему требуется исполняемый HTML (`requireImplementableHtml=true`), категорически запрещено подменять HTML картинкой Nano Banana;
  * Запрет коммита: off-token экраны категорически запрещено коммитить в репозиторий клиента;
  * Запрет продвижения: продвижение макета из `draft` в `approved` допустимо исключительно при явном вердикте `approve`.

---

## 3. Пакетный наряд по механизмам Секции XXIX (Цех дизайна)

1. **`DesignAssetService` (ядро):**
   - **Проба:** Замерить генерацию через Stitch, передачу токенов бренда в промпт, фиксацию `declaredTokens` и `producerTokens` в метаданных, отсечение off-token экранов без коммита.
   - **Инвариант:** Рабочий код не трогать. Заслон `DesignAssetServiceTest`.
2. **`DesignConsistencyAuditService`:**
   - **Проба:** Замерить расчет коэффициента следования токенам (`tokenTraceRatio`), Jaccard-сходство между экранами (`crossScreenJaccard`) и возврат `CANNOT_JUDGE` при отсутствии baseline.
   - **Инвариант:** Рабочий код не трогать. Заслон `DesignConsistencyAuditServiceTest`.
3. **`StitchClient` (ядро):**
   - **Проба:** Замерить сохранение `screenId` при отсутствии готового HTML, JSON-RPC взаимодействие с MCP и загрузку готовых артефактов.
   - **Инвариант:** Рабочий код не трогать. Заслон `StitchClientTest`.
4. **`GoogleAiResourceService` (Nano Banana fallback):**
   - **Проба:** Замерить вызов генератора изображений строго при `requireImplementableHtml=false` и блокировку при требовании HTML.
   - **Инвариант:** Рабочий код не трогать. Заслон в `DesignAssetServiceTest`.
5. **`GitHubPullRequestService` (дизайн-носитель):**
   - **Проба:** Замерить коммит черновиков в `design/draft`, удаление отклоненных черновиков и копирование в `design/approved`.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DesignAssetServiceTest`, `DesignImplementationLoopClosureLaw3Test`.
6. **`DesignShopOrchestrationService` (ядро):**
   - **Проба:** Замерить запуск цикла только по фронту готовности (false-to-true edge), атомарный CAS-клейм `startCycleClaimedAt`, дедупликацию циклов на проект.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DesignShopOrchestrationServiceTest`, `DesignShopOrchestrationServiceLaw15Test`, `StrandedDesignCycleSweepServiceTest`.
7. **`JulesDispatchService.completeDesignReview`:**
   - **Проба:** Замерить продвижение утвержденных макетов строго при вердикте `approve` с переносом `mockup.html`.
   - **Инвариант:** Рабочий код не трогать. Заслон `DesignImplementationLoopClosureLaw3Test`.
8. **`LayoutGeometryAuditService`:**
   - **Проба:** Замерить аудит viewport, обнаружение коллизий элементов и дистанций группировки.
   - **Инвариант:** Рабочий код не трогать. Заслон `LayoutGeometryAuditServiceTest`.
9. **`DesignSystemFalsificationService`:**
   - **Проба:** Замерить отдельный такт фальсификации для объединенных UI-эпиков и запись аудит-трейла в виде dismissed wishlist.
   - **Инвариант:** Рабочий код не трогать. Заслон `DesignSystemFalsificationServiceTest`.
10. **`DesignDriftMonitorService`:**
    - **Проба:** Замерить мониторинг дрейфа живого продукта исключительно внутри уже открытого окна observability без перезапуска контейнеров.
    - **Инвариант:** Рабочий код не трогать. Заслон `DesignDriftMonitorServiceTest`.
11. **`DesignExcellenceGate`:**
    - **Проба:** Замерить заслон качества артефактов экрана перед отправкой в приемку.
    - **Инвариант:** Рабочий код не трогать. Заслоны `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`.
12. **`DesignShopCycleRepository`:**
    - **Проба:** Замерить атомарность `claimStartCycle` и проверку `lastWasReady = false` на уровне БД.
    - **Инвариант:** Рабочий код не трогать. Заслон `DesignShopCycleRepositoryIntegrationTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов дизайн-цеха в Docker Maven (`-m 2g`).
2. Внести отметку `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` (Секция XXIX) и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(design-shop): complete Section XXIX batch verification (D008, D010, D006)`.
