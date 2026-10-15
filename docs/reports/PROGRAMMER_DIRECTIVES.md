# Пакетная директива L1/L3 -> L2: Секции X и XXIX (Дизайн-цех, порождение экранов, токенизация бренда, дизайн-система и live drift)

**Дата:** 2026-10-15 18:42 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Flow & Metrics, Самопроверка, Операционная Правда, Кайдзен, Фальсификация и Оркестрация») полностью замерена и закрыта инженером (281/281 green в коммите `8fe70e7`). Настоящая директива охватывает целиком связанное семейство экранного конвейера фабрики — **Секции X и XXIX («Дизайн-цех, порождение экранов, токенизация бренда, дизайн-система и live drift»)**:
   - **Контур X/XXIX.1. `DesignShopOrchestrationService` & `DesignShopCycleRepository` (Barkan Marcus D002, Raz D006, Goldman D010):**
     * Суть до опций (`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` / D002): старт цикла строго по нарастающему фронту готовности (`isReady && !lastWasReady`), готовность вычисляется строго как декомпозиция + пропорции (`decompositionComplete`), а не голый счетчик задач;
     * Атомарный CAS-клейм на уровне базы данных (`claimStartCycle`: `startCycleClaimedAt IS NULL` и `lastWasReady = false`), исключающий гонки конкурентных тиков;
     * Деонтический запрет деградации: при требовании имплементируемого HTML категорически запрещена подмена на картинки (`mockup.html` обязателен).
   - **Контур X/XXIX.2. `DesignConsistencyAuditService` & `LayoutGeometryAuditService` (Belnap D015, Wittgenstein D015, Goldman D011):**
     * Единство трассировки токенов (`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015): экстракция hex/rgb/font, строгий расчет коэффициента покрытия токенов бренда и немедленное отклонение off-token экранов до коммита в git;
     * Межэкранная согласованность Витгенштейна (`LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` / D015): расчет Jaccard-сходства словарного запаса соседних экранов одного проекта;
     * Геометрия и группировка Голдмана (`ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE` / D011): аудит масштабируемости вьюпортов, отсутствие коллизий и соблюдение дистанций близости;
     * Анти-зеркало Витгенштейна: возврат честного статуса `CANNOT_JUDGE` при пустых SPA-оболочках или отсутствии базовой линии вместо ложных green/red.
   - **Контур X/XXIX.3. `DesignSystemFalsificationService` (Popper D008, Goldman D010):**
     * Попперовская фальсификация дизайн-систем (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008): обработка объединенных UI-эпиков с созданием аудит-трейла строго в виде отклоненной записи (`dismissed` `WishlistEntity`), категорический запрет порождения фоновых незапланированных `pending`-задач.
   - **Контур X/XXIX.4. `DesignDriftMonitorService` (Wittgenstein D013, Raz D006):**
     * Анти-зеркало телеметрии рантайма (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013): мониторинг дрейфа экрана выполняется строго внутри уже открытого 2xx окна наблюдения `ClientRuntimeObservabilityService`; категорический запрет на самостоятельный запуск или останов контейнеров продукта.
   - **Контур X/XXIX.5. `DesignAssetService` & `StitchClient` (Goldman D010, Belnap D009, Raz D006):**
     * Оркестрация MCP Stitch и генерации артефактов: сквозная передача токенов бренда в промпт, фиксация `declaredTokens` и `producerTokens` в метаданных;
     * Демаркация Nano Banana fallback: модель изображений допускается исключительно для non-HTML вызовов (`requireImplementableHtml=false`);
     * Сохранение `screenId` в `StitchClient` при ожидании генерации и строгая изоляция контекста проектов Stitch.
   - **Контур X/XXIX.6. `StrandedDesignCycleSweepService` & `DesignExcellenceGate` & `GitHubPullRequestService` & `JulesDispatchService` (Raz D006, Popper D008, Goldman D010):**
     * Санитарный выметающий обход брошенных притязаний цикла дизайна по TTL (15 мин) через CAS `compareAndReleaseStrandedClaim` с институциональной записью в `DefectJournalService`;
     * Заслон качества дизайна `DesignExcellenceGate`: строгая фальсификационная проверка артефактов перед сдачей;
     * Продвижение макетов (`JulesDispatchService.completeDesignReview`): перенос `mockup.html` из `design/draft` в `design/approved` только при явном вердикте `approve`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секций X и XXIX уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D015, D013, D011, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green, Popper / Williamson]:**
  * Попперовская фальсификация: тесты-заслоны гейта качества дизайна (`DesignExcellenceGate`), фальсификации дизайн-системы (`DesignSystemFalsificationService`) и инвариантов нарастающего фронта (`DesignShopOrchestrationServiceLaw15Test`) обязаны падать при нарушении спецификаций артефактов;
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная надежность: разделение артефактов на черновики `design/draft`, утвержденные макеты `design/approved`, метаданные токенов, идентификаторы Stitch (`stitchProjectId`, `stitchScreenId`) и факты готовности `ClientDeliverableReadinessService`;
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секций X и XXIX при проходящих тестах;**
  * Программные деонтические запреты: запрет деградации до картинок при требовании HTML, запрет коммита off-token экранов, запрет запуска runtime-контейнеров из монитора дрейфа;
- **`RUT_BARKAN_MARKUS_02_ESSENCE_BEFORE_OPTION` [D002 Invalid state, Barkan Marcus]:**
  * Суть до опций: обязательный валидный HTML до любой параметризации; нарастающий фронт готовности исключает паразитные повторные циклы;
- **`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D015 Aesthetic drift, Belnap / Wittgenstein]:**
  * Единство трассировки токенов бренда и Jaccard-сходство экранов одного проекта;
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Запрет субъективных нарративов: честный `CANNOT_JUDGE` для SPA-оболочек без базовой линии, замер дрейфа только в живом 2xx окне;
- **`ELVIN_GOLDMAN_20_GROUPING_PROXIMITY_GATE` [D011 Perception failure, Goldman]:**
  * Аудит геометрии, масштабируемости вьюпортов и проверка коллизий элементов;
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Substitution failure, Belnap]:**
  * Категорический запрет подмены типа артефакта: картинка не заменяет реализуемый HTML-макет.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций X и XXIX в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секций X и XXIX (113 тестов):
   - Оркестрация и база цикла: `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7), `DesignShopCycleRepositoryIntegrationTest` (6/6);
   - Аудит токенов и геометрия: `DesignConsistencyAuditServiceTest` (15/15), `LayoutGeometryAuditServiceTest` (13/13);
   - Фальсификация и мониторинг дрейфа: `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7);
   - Санитарная очистка брошенных циклов: `StrandedDesignCycleSweepServiceTest` (4/4);
   - Продвижение и носитель артефактов: `DesignImplementationLoopClosureLaw3Test` (2/2);
   - Гейт качества дизайна: `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6);
   - MCP Stitch и генератор: `StitchClientTest` (10/10), `DesignAssetServiceTest` (12/12).
3. Зафиксировать форму образца, актуализировать `### Итог Секции X` и `### Итог Секции XXIX` в `docs/FACTORY_MECHANISMS.md` (подтверждающие Ступень 4 closed, ideal, 113/113 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
