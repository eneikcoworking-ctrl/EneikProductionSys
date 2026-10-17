# Директивы и наряд для L2 (2026-10-17 17:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы фабрики») полностью заверен и закрыт инженером (коммит `da5e68fb`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:01 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, вход к ресурсам модели, метрики качества Six Sigma, выметающий обход, заслон качества экрана, постоянный кэш корпуса, верёвка ТОС и очистка журнала проекта»)** целиком (**206 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1 — Секция XXXVII: Сторож ТОС (5 сьютов, 33 теста):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста), `TocNodeTimeoutTest` (4 теста), `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocPipelinePhasesInstrumentationTest` (4 теста): разделение чтения и наблюдения, динамический расчет периода watchdog по дисперсии Уэлфорда, заслон тайм-аутов фаз пайплайна (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 2 — Секция XXXVIII: Вход к ресурсам модели (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтическая защита пограничного контроллера AI-ресурсов, fail-closed барьер авторизации и строгая изоляция мутирующих команд (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 3 — Секция XXXIX: Метрики качества Six Sigma (2 сьюта, 29 тестов):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов): трехзначная таблица истинности качества (passed/failed/undetermined), расчет DPMO и устранение `findAll()`-сканирований базы данных (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4 — Секция XL: Выметающий обход застрявших циклов и финализаций (4 сьюта, 25 тестов):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов), `StrandedDesignCycleSweepServiceTest` (4 теста), `GitHubSweepBudgetEfficiencyTest` (7 тестов): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по временной метке `finalizingSince`, каузальный след транзитных захватов, бюджетная санитария PR (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5 — Секция XLI: Заслон качества экрана и геометрии верстки (6 сьютов, 59 тестов):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов), `LayoutGeometryAuditServiceTest` (13 тестов), `DesignConsistencyAuditServiceTest` (15 тестов), `DesignDriftMonitorServiceTest` (7 тестов), `DesignSystemFalsificationServiceTest` (6 тестов): проверка физических артефактов PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), Gestalt-близость (< 1.0), отсутствие коллизий bounding box и запрет ложного прохождения (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 6 — Секция XLII: Постоянный кэш корпуса и статус (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): подтверждение исключения дублирующего бэкенд-кэша `GeminiContextCacheManager` из classpath, нулевое присутствие `cachedContents` в Java-коде, делегирование кэширования ML-сайдкару (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
     * **Группа 7 — Секция XLIII: Верёвка ТОС и регулирование допуска (5 сьютов, 29 тестов):**
       - `TocOptimizerTest` (7 тестов), `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `TocPriorityClaimTest` (1 тест), `TocPersistenceSnapshotTest` (4 теста): фальсификационный заслон ограничения допуска DBR (отсечение ложного «optimal» при неразмеченном графе), регулирование по узкому месту, обход для высокоприоритетных задач (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 8 — Секция XLIV: Очистка журнала проекта (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): очистка журнала с частой задержкой fixedDelay, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D007).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D011, D004, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка в `DesignExcellenceGateFalsificationTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `DesignSystemFalsificationServiceTest`, `TocWelfordOnlineVarianceFalsificationTest` и `TocSubordinationTruthTest`: активный поиск контрпримеров, опровержение ложно-зеленых исходов и удержание семантических инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции в `SixSigmaAuditServiceTest` и `ProjectEventLogRetentionServiceTest` (отсечение смешения локального контекста и глобальных лимитов, однострочная выборка границы `PageRequest.of(excess - 1, 1)`); каузальная цепь надежности взаимодействия с сайдкарами и внешними системами.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Авторизационные барьеры безопасности модели (`GoogleAiResourceSecurityIntegrationTest`), защита терминальных статусов и CAS-переходов.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальный контроль истинного состояния рантайма без фиктивных проекций (`TocSentinelServiceTest`, `StrandedFinalizingSweepServiceTest`).
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Boundary leak / Clark & Dretske]:**
  * Гештальт-близость и геометрический заслон верстки (`LayoutGeometryAuditServiceTest`, `DesignExcellenceGateTest`), телеосемантическая обратная связь ограничений ТОС.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D004 Part-whole ownership / D010 Derived cutoff]:**
  * Монопольное владение токенами и узлами в графе ТОС, вывод динамических интервалов и периодов ретеншна из фактического роста потока.

---

## 3. Регламент и формат отчёта L2

1. Провести единый непрерывный пакетный замер всех 26 тест-сьютов Кластера Секций XXXVII–XLIV в Docker Maven 3.9.9 Temurin-21 (-m 2g).
2. Убедиться в достижении результата 206/206 green (0 failures, 0 errors, 0 skipped) при строго нулевом изменении рабочего кода фабрики.
3. Актуализировать отметки в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк).
4. Зафиксировать результат коммитом: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
5. По регламенту оператора таймеры не взводятся.
