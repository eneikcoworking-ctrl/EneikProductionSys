# Пакетная директива L1/L3 -> L2: Секция X (Внешние системы, сайдкары и дизайн-цех)

**Дата:** 2026-10-15 09:25 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, согласований и пауз.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция X объединяет внешние системы, сайдкары и дизайн-цех (Разделы IX и X `docs/FACTORY_MECHANISMS.md`, пункты 401–409 Ступени 4 `docs/ANTIGRAVITY_QUEUE.md`):
   - `GoogleAiResourceService` (пункт 401)
   - `EmbeddingSimilarityUtil` (пункт 402)
   - `StitchClient` (ядро, пункт 403)
   - `RuntimeLauncherClient` (ядро, пункт 404)
   - `GeminiObserverActionService` (ядро, пункт 405)
   - `DesignShopOrchestrationService` (пункт 406)
   - `DesignConsistencyAuditService` (пункт 407)
   - `DesignSystemFalsificationService` (пункт 408)
   - `DesignDriftMonitorService` (пункт 409)
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено модифицировать рабочий код сервисов при проходящих тестах!
   - Любая правка рабочего кода фабрики допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего инвариант. Если тесты зеленые — код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:** Принцип фальсификации. Внешние вызовы к Google AI, Stitch MCP и Runtime Launcher не должны маскировать сетевые или инструментальные сбои под «успешный запуск» или «продукт сломан». Заслон обязан фальсифицировать ложно-зеленые исходы: `observed=false` означает сбой инструмента наблюдения, а не дефект продукта заказчика.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:** Лок уровней абстракции и надежность происхождения данных. Строгое разделение уровней «внешняя модель / сайдкар — внутренняя фабрика — клиентский продукт». Запрет утечки фабричных маркеров или сырых JSON-RPC структур в продуктовый скоуп. Замер векторов схожести (`EmbeddingSimilarityUtil`) и аудит токенов (`DesignConsistencyAuditService`) должны сохранять строгую математическую чистоту без побочных мутаций.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:** Деонтический запрет как исключающая причина (`exclusionary reason`) и исполняемый код. Императивный запрет инженеру трогать рабочий код сервисов на Ступени 4 при проходящих тестах. Действия `GeminiObserverActionService` строго ограничены политикой `OperationalPolicyService` и лишены права напрямую писать в Git или модифицировать кодовую базу.

---

## 3. Пакетный наряд по механизмам Секции X (Ступени 3–4: «Замер в рантайме, не правка кода»)

### А. Внешняя граница AI и эмбеддингов: `GoogleAiResourceService`, `EmbeddingSimilarityUtil`
- **Действие:** Замерить типобезопасные контракты вызовов моделей Google AI, матрицу ресурсов и чистую функцию косинусного сходства эмбеддингов.
- **Инварианты:** Отсутствие API-ключа возвращает явный недоступный статус без скрытых мутаций; `EmbeddingSimilarityUtil` работает строго как детерминированная математическая функция без N+1 и побочных эффектов.
- **Заслон:** `GoogleAiResourceControllerTest`, `GoogleAiResourceSecurityIntegrationTest`, `GeminiContextServiceTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### Б. Клиенты сайдкаров: `StitchClient`, `RuntimeLauncherClient`
- **Действие:** Замерить JSON-RPC клиент к Stitch MCP и HTTP-клиент к сайдкару рантайм-лаунчера.
- **Инварианты:** `StitchClient` сохраняет `screenId` при отсутствии готового HTML; `RuntimeLauncherClient` четко разделяет `LaunchResult.unobserved` (инструмент не ответил) и `answered(false)` (продукт упал). Никакого прямого Docker-управления вне лаунчера.
- **Заслон:** `StitchClientTest`, `ClientRuntimeObservabilityServiceTest`, `ObservationHostingDemarcationLaw26Test`. Исходный код сервисов НЕ ТРОГАТЬ.

### В. Аудируемые действия наблюдателя: `GeminiObserverActionService`
- **Действие:** Замерить сервис операционных действий наблюдателя.
- **Инварианты:** Все действия требуют предварительной авторизации через `OperationalPolicyService`; фиксация каждого действия в `GeminiObserverActionEntity` (`success`, `skipped`, `failed`, `denied`); категорический запрет на прямое редактирование файлов или коммиты кода.
- **Заслон:** `GeminiObserverActionServiceTest`, `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### Г. Дизайн-цех и аудит соответствия: `DesignShopOrchestrationService`, `DesignConsistencyAuditService`
- **Действие:** Замерить атомарный захват цикла дизайн-цеха, проверку дизайн-токенов и геометрии макетов.
- **Инварианты:** Старт цикла дизайн-цеха строго по нарастающему фронту готовности (`isReady && !lastWasReady`); атомарный `claimStartCycle`; генерация требует валидный HTML (запрещен fall-back на чистую картинку при требовании кода); базовые токены фиксируются из первого экрана проекта; пустые токены возвращают честный `CANNOT_JUDGE`.
- **Заслон:** `DesignShopOrchestrationServiceTest`, `DesignShopOrchestrationServiceLaw15Test`, `DesignConsistencyAuditServiceTest`, `DesignShopCycleRepositoryIntegrationTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### Д. Фальсификация дизайн-систем и дрейф рантайма: `DesignSystemFalsificationService`, `DesignDriftMonitorService`
- **Действие:** Замерить фальсификацию дизайн-систем эпиков и мониторинг дрейфа живого экрана.
- **Инварианты:** `DesignSystemFalsificationService` пишет аудит-трейл как отклоненный wishlist-айтем (не создает pending-задач); `DesignDriftMonitorService` замеряет дрейф только внутри уже открытого здорового 2xx окна `ClientRuntimeObservabilityService` и не запускает собственных инстансов.
- **Заслон:** `DesignSystemFalsificationServiceTest`, `DesignDriftMonitorServiceTest`. Исходный код сервисов НЕ ТРОГАТЬ.

---

## 4. Регламент исполнения такта для инженера L2

1. Провести верификацию тестового заслона Секции X в изолированном Docker-контейнере (`maven:3.9.9-eclipse-temurin-21`, с ограничением памяти `-m 2g`).
2. Код сервисов фабрики не модифицировать: если тесты зеленые — признать механизмы идеальными (`ideal`).
3. Зафиксировать эмпирические результаты замеров (`*Живое:*`) в `docs/FACTORY_MECHANISMS.md` для механизмов Секции X.
4. Отметить закрытие пунктов 401–409 Ступени 4 в `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
