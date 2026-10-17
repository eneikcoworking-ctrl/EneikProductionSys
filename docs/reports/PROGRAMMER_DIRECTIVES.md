# Директивы и наряд для L2 (2026-10-17 15:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, brownfield-разбор чужого репозитория, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач, Закон 20 и Закон 1») полностью подтверждён и закрыт инженером (коммит `e04b404a`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:06 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, вход к ресурсам модели, метрики качества Six Sigma, выметающий обход, заслон качества экрана, постоянный кэш корпуса, верёвка ТОС и очистка журнала проекта»)** целиком (**207 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1 — Секция XXXVII: Сторож ТОС (5 сьютов, 36 тестов):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста), `TocNodeTimeoutTest` (4 теста), `TocWelfordOnlineVarianceFalsificationTest` (6 тестов), `TocPipelinePhasesInstrumentationTest` (6 тестов): разделение чтения и наблюдения, динамический расчет периода watchdog по дисперсии Уэлфорда, заслон тайм-аутов узлов графа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 2 — Секция XXXVIII: Вход к ресурсам модели (2 сьюта, 15 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (9 тестов): деонтическая защита пограничного контроллера AI-ресурсов, fail-closed барьер авторизации и строгая изоляция мутирующих команд (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 3 — Секция XXXIX: Метрики качества Six Sigma (2 сьюта, 29 тестов):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов): трехзначная таблица истинности качества (passed/failed/undetermined), расчет DPMO и устранение `findAll()`-сканирований базы данных (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4 — Секция XL: Выметающий обход брошенных финализаций и циклов (4 сьюта, 23 теста):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (5 тестов), `StrandedDesignCycleSweepServiceTest` (4 теста), `GitHubSweepBudgetEfficiencyTest` (6 тестов): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по временной метке `finalizingSince`, каузальный след транзитных захватов, бюджетная санитария PR (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5 — Секция XLI: Заслон качества экрана и геометрии (6 сьютов, 45 тестов):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (5 тестов), `LayoutGeometryAuditServiceTest` (13 тестов), `DesignConsistencyAuditServiceTest` (4 теста), `DesignDriftMonitorServiceTest` (5 тестов), `DesignSystemFalsificationServiceTest` (6 тестов): проверка физических артефактов PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), Gestalt-близость (< 1.0), отсутствие коллизий bounding box и запрет ложного прохождения (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 6 — Секция XLII: Кэш постоянного корпуса (1 сьют, 5 тестов):**
       - `SystemStatusControllerTest` (5 тестов): подтверждение исключения дублирующего бэкенд-кэша `GeminiContextCacheManager` из classpath, нулевое присутствие `cachedContents` в кодовой базе Java, делегирование кэширования ML-сайдкару (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
     * **Группа 7 — Секция XLIII: Верёвка ТОС и регулирование допуска (5 сьютов, 44 теста):**
       - `TocOptimizerTest` (14 тестов), `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (17 тестов), `TocPriorityClaimTest` (4 теста), `TocPersistenceSnapshotTest` (4 теста): фальсификационный заслон ограничения допуска DBR (отсечение ложного «optimal» при неразмеченном графе), регулирование по узкому месту, обход для высокоприоритетных задач (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 8 — Секция XLIV: Очистка журнала проекта (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): очистка журнала с частой задержкой fixedDelay, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D007).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D011, D013, D004, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: строгая проверка на контрпримерах для ограничения верёвки DBR (`TocOptimizerTest`), заслона качества экранов (`DesignExcellenceGateFalsificationTest`, `DesignSystemFalsificationServiceTest`) и выметающего обхода финализаций (`StrandedFinalizingSweepServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепь надежности свидетельств: однострочная выборка границы очистки журнала `PageRequest.of(excess - 1, 1)` для защиты памяти кучи (`ProjectEventLogRetentionServiceTest`), строгие уровни DPMO Six Sigma (`SixSigmaAuditServiceTest`), изоляция вызовов AI-ресурсов (`GoogleAiResourceControllerTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов Google AI ресурсов (`GoogleAiResourceSecurityIntegrationTest`).
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth table / Tarski & Belnap]:**
  * Трехзначная логика истинности качества Six Sigma (passed, failed, undetermined).
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Clark & Dretske]:**
  * Гештальт-проверка близости экранных элементов (< 1.0) и телеосемантическая связь телеметрии допуска с реальными действиями.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия сторожа ТОС (чтение не изменяет наблюдаемый граф) и каузальный след выметающего обхода финализаций.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Varzi]:**
  * Топология единого монопольного владения счетчиками in-flight графа ТОС и ликвидация дублирующего бэкенд-кэша.
- **`AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` [D007 Incomplete record / Levi]:**
  * Обоснованный реестр исключений: сохранение журналов непринятых и замороженных проектов при очистке.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,TocOptimizerTest,TocNodeTimeoutTest,TocWelfordOnlineVarianceFalsificationTest,TocPipelinePhasesInstrumentationTest,TocSubordinationTruthTest,TocSubordinationLeverTest,TocPriorityClaimTest,TocPersistenceSnapshotTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,QualityMetricsControllerTest,SixSigmaAuditServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedDesignCycleSweepServiceTest,GitHubSweepBudgetEfficiencyTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,LayoutGeometryAuditServiceTest,DesignConsistencyAuditServiceTest,DesignDriftMonitorServiceTest,DesignSystemFalsificationServiceTest,SystemStatusControllerTest,ProjectEventLogRetentionServiceTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
