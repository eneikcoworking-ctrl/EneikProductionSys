# План ввода обновлённого ядра Автономии и Полного Аудита Фабрики и Сайта (`AUDIT_PLAN`)

**Статус:** ПОДГОТОВЛЕН И ЗАПЛАНИРОВАН — **ОЖИДАЕТ КОМАНДЫ ОПЕРАТОРА ДМИТРИЯ ЕФРЕМОВА**  
**Объекты аудита:**
1. Фабрика `EneikProductionSys` (`/opt/EneikProductionSys`, Brand-OS, 207 механизмов)
2. Продукт `dmitryefremov-site` (`1695f808-0df7-4393-ace9-d5a79e62ef41`, `/opt/dmitryefremov-site`)

---

## Фаза 0. Автоматический предстартовый гейт (`AUDIT_READY`)
Выполняется автономно эстафетой `eneik-autonomous-factory.service` (L1 Философ `remotecli2` <-> L2 Инженер `remotecli`) без участия человека:
- Доводка оставшихся **67** инлайн-блоков в `docs/FACTORY_MECHANISMS.md` (~10% корпуса документации) строгим замером в Docker Maven (`-v /root/.m2:/root/.m2 -m 2g`) при полной неприкосновенности защищённого контура автономии Деннетта (`LOCKED`).
- При достижении `scan_remaining_mechanisms() == 0` демон автоматически фиксирует баннер `# STATUS: AUDIT_READY` в `docs/reports/MANAGER_STATE.md`, прекращает холостые вызовы L1/L2 и остаётся в режиме бережного гомеостатического дозора над `dmitryefremov-site`.

---

## Фаза 1. Бесшовное обновление живого контейнера `eneikproductionsys-backend-1` (по команде оператора)
Так как новые 15 Java-механизмов автономии уже закоммичены в `/opt/EneikProductionSys` и проверены на **376/376 green**, но живой контейнер `eneikproductionsys-backend-1` намеренно не перезапускался во время активной сборки сайта:
1. Проверить отсутствие слияний в полёте (`GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/flow-core`).
2. Выполнить атомарную пересборку и перезапуск бэкенда:
   ```bash
   cd /opt/EneikProductionSys && docker compose up -d --build backend
   ```
3. Подтвердить готовность `/actuator/health -> UP` и чистоту снимка `GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/flow-core` (вступление в силу встроенного в JVM контура `ProjectStatus.isOperational()`, транзитивного `isDeadForGood`, экспоненциальных эпох `recoverStarvingFrontierForAutonomy` и `refreshFlowSnapshot`).

---

## Фаза 2. Полный фальсификационный и инвариантный аудит Фабрики (`EneikProductionSys`)
1. **Архитектурные и деонтические инварианты (`src/test/java/com/eneik/production/invariants/`):**
   - `NoHumanInTheLoopInvariantTest` (отсутствие ручных гейтов / картезианского театра)
   - `ScheduledTransactionSpanInvariantTest` и `ScheduledQueryCostInvariantTest` (`EXEMPT = Set.of()`, 0 исключений)
   - `PhilosopherPatternCorpusConsistencyTest` (100% консистентность 86 философов, 53 семейств и 104 ACP-образцов)
2. **Контур автономии, диспетчеризации, слияния и хребта потока:**
   - Полный прогон `mvn test` по всем сьютам ядра фабрики с фиксацией итогового отчёта в `docs/reports/FULL_FACTORY_AUDIT_REPORT.md`.
3. **Проверка целостности `docs/FACTORY_MECHANISMS.md`:**
   - Машинная проверка `scan_remaining_mechanisms() == 0` (100% механизмов и предписаний несут верифицированное свидетельство `*Живое:*`).

---

## Фаза 3. Продуктовый и приёмочный аудит сайта `dmitryefremov-site`
1. **Институциональный конвейер аудита продукта (`ProjectAuditPipelineService` + `DeliveredWorkJudgmentService`):**
   - Запуск `POST /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/falsification/run` и `POST /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/philosophical-falsification/run`.
   - Снятие полной сводки сдачи `GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/client-delivery`, `GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/verdict` и `GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/command-dashboard`.
2. **Живая проверка рантайма и приёмочного обхода (`ClientAcceptanceTraversalController`):**
   - Проверка `GET /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/runtime-health` и живого превью `http://127.0.0.1:18085` (HTTP 200, отсутствие ошибок консоли/рендера, соответствие `PROJECT_BRIEF.md`).
   - Запуск приёмочного обхода `POST /api/projects/1695f808-0df7-4393-ace9-d5a79e62ef41/acceptance-traversals`.
   - Выпуск финального вердикта приёмки с доказательной базой (или автоматическая генерация точечных финишных задач для Джулсов по найденным расхождениям).
