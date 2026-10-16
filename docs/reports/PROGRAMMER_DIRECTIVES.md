# Директивы и наряд для L2 (2026-10-16 08:22 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и Внешняя AI-граница») успешно заверены инженером (коммит `c648e68`, 215/215 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секции XVI («Архитектурные предписания для L2») и Секции XXIX («Цех дизайна: экраны, марка, геометрия и дизайн-система»)** целиком (29 тест-сьютов, 272 теста в едином пакетном прогоне Docker Maven):
     * **Секция XVI: Реестр архитектурных предписаний L2 (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): деонтические заслоны доступа к диагностическим эндпоинтам по API-ключу (401/403), институциональный аудит вызывающего (D006 Raz, D006 Varzi);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): тянущий контур ТОС, ограничение WIP 1..500, HTTP 429 при перегрузке (D013 Goldratt, D006 Raz);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4): чистота бережливого потока создания ценности без муды (D013 Wittgenstein, D010 Goldman);
       - `RepositoryStackAnalyzerTest` (6/6): 3-значный статус инспекции репозитория без превращения сбоя фабрики в дефект клиента (D002 Ryle, D012 Belnap);
       - `OperationalTruthServiceTest` (17/17): операционная правда в режиме `observe_only` без мутаций базы данных (D013 Wittgenstein, D010 Goldman);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): заслоны фальсификации заявок, семантическое выявление дубликатов по косинусному сходству (D008 Popper, D010 Goldman);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): реестр системных настроек, маскирование секретов и деонтический запрет пустых флагов (D006 Raz);
       - `FlywayMigrationValidationTest` (4/4), `ProductNamespaceLaw26Test` (16/16): валидация миграций БД и изоляция пространств имён продукта по Закону 26 (D008 Tarski, D006 Varzi).
     * **Секция XXIX: Цех дизайна: экраны, марка, геометрия и дизайн-система (113 тестов, 13 сьютов):**
       - `DesignAssetServiceTest` (12/12): генерация артефактов дизайна, передача дизайн-токенов в промпты, блокировка подмены при `requireImplementableHtml=true` (D006 Raz, D009 Belnap);
       - `DesignConsistencyAuditServiceTest` (15/15): расчет коэффициента следования токенам, кросс-экранный Jaccard-индекс, честный статус `CANNOT_JUDGE` при отсутствии baseline (D015 Belnap, D010 Goldman);
       - `LayoutGeometryAuditServiceTest` (13/13): аудит геометрии вьюпорта, проверка близости и группировки элементов (D011 Clark, D010 Goldman);
       - `DesignSystemFalsificationServiceTest` (6/6), `DesignDriftMonitorServiceTest` (7/7): фальсификационный мониторинг дрейфа дизайн-системы (D008 Popper, D015 Wittgenstein);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): строгий гейт совершенства дизайна на реальных физических артефактах (D008 Popper, D011 Goldman);
       - `DesignShopCycleRepositoryIntegrationTest` (6/6), `DesignShopOrchestrationServiceTest` (13/13), `DesignShopOrchestrationServiceLaw15Test` (7/7): атомарный CAS-клейм старта цикла, оркестрация дизайн-цеха по Закону 15 (D006 Raz, D010 Goldman);
       - `StrandedDesignCycleSweepServiceTest` (4/4): выметающий сборщик зависших циклов дизайна с динамическим лизингом (D013 Noe, D004 Varzi);
       - `DesignImplementationLoopClosureLaw3Test` (2/2): замыкание цикла реализации дизайна по Закону 3, продвижение макетов из `design/draft` в `design/approved` (D006 Raz, D010 Goldman);
       - `StitchClientTest` (10/10): клиент к Stitch с сохранением идентичности экранов `screenId` и изоляцией проектов (D006 Raz, D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D013, D011, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при ложной готовности дизайн-системы (`DesignSystemFalsificationServiceTest`), симуляции гейта качества (`DesignExcellenceGateFalsificationTest`) или передаче нерелевантной заявки (`WishlistServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: разграничение сырых токенов и производных суждений; проверяемость фактов от источника к аудиту консистентности.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Деонтический запрет подмены HTML растровыми изображениями при флаге `requireImplementableHtml=true`; институциональный аудит прав доступа в контроллерах.
- **`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` [D015 Aesthetic drift / Belnap]:**
  * Единство трассировки токенов: каждый цвет и шрифт экрана обязан трассироваться к объявленному набору токенов проекта с вычислением соотношения покрытия.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D013 Runtime drift & D015 Aesthetic drift / Wittgenstein]:**
  * Анти-зеркало: аудит экранов и чтение операционной правды не оставляют побочных мутаций в базе данных; кросс-экранное сходство Жаккара для экранов одной системы.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Sensor disconnection / Clark]:**
  * Проверка физической геометрии вьюпорта, взаимного расположения и группировки элементов вместо слепой веры в самоотчёты модели.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Category substitution / Floridi]:**
  * Категорический запрет категориальной подмены типов артефактов в контуре дизайн-цеха.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов кластера Секций XVI и XXIX:**
   - **Docker Maven (272 теста, 29 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,DesignShopCycleRepositoryIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,StitchClientTest
     ```
2. **Верификация результата:**
   - Строго **272/272 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XVI и XXIX в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XVI и XXIX в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
