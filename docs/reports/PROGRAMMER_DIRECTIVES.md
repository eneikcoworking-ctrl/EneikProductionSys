# Директивы и наряд для L2 (2026-10-17 19:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Клиент предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта, Хранилище задач») полностью заверен и закрыт инженером (коммит `779271b9`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:02 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, Вход к ресурсам модели, Метрики качества Six Sigma, Выметающий обход, Заслон качества экрана, Постоянный кэш корпуса, Верёвка ТОС и Очистка журнала проекта»)** целиком (**206 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (5 сьютов, 33 теста):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): чистое анти-зеркальное чтение `getDbrStatus()` без мутаций внутреннего состояния при HTTP-опросе (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TocNodeTimeoutTest` (4 теста), `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocPipelinePhasesInstrumentationTest` (4 теста): фальсификационная проверка тайм-аутов фаз графа, онлайн-алгоритм Уэлфорда для вычисления дисперсии и динамического периода сторожа (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтическая граница доступа к AI-ресурсам, fail-closed защита эндпоинтов, отсечение path traversal за пределы `./data/video-assets` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XXXIX — Метрики качества Six Sigma и операционная правда (2 сьюта, 29 тестов):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов): 4-значная решётка истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация дефекта «388 ложных сбоев» через лок уровней абстракции проверки и задачи, устранение `findAll()`-сканирований базы данных (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 4: Секция XL — Выметающий обход застрявших циклов и финализаций (4 сьюта, 25 тестов):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по временной метке `finalizingSince`, каузальный след транзитных захватов (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `StrandedDesignCycleSweepServiceTest` (4 теста), `GitHubSweepBudgetEfficiencyTest` (7 тестов): автоматическое высвобождение брошенных дизайн-циклов и бюджетирование вызовов GitHub API (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрии верстки (6 сьютов, 59 тестов):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): фальсификационная верификация физических артефактов PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), отсечение деклараций без свидетельств (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `LayoutGeometryAuditServiceTest` (13 тестов), `DesignConsistencyAuditServiceTest` (15 тестов), `DesignDriftMonitorServiceTest` (7 тестов), `DesignSystemFalsificationServiceTest` (6 тестов): Gestalt-близость (< 1.0), отсутствие наложений bounding box, проверка согласованности дизайн-токенов (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Постоянный кэш корпуса RAG и статус (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): контроль чистоты бэкенда, исключение мертвого кэша `GeminiContextCacheManager` (0 байт), делегирование кэширования RAG сайдкару `PredictionService.py` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 7: Секция XLIII — Верёвка ТОС и регулирование допуска DBR (5 сьютов, 29 тестов):**
       - `TocOptimizerTest` (7 тестов), `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `TocPriorityClaimTest` (1 тест), `TocPersistenceSnapshotTest` (4 теста): DBR-верёвка, статус `Flow unmeasured` при неразмеченном графе (защита от ложного оптимума), принудительное замедление низкоприоритетных задач и приоритетный обход (`priority >= 80`) (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010).
     * **Группа 8: Секция XLIV — Очистка журнала проекта (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): очистка устаревших событий по Закону 21, однострочная граница `PageRequest.of(excess - 1, 1)` для защиты памяти heap, сохранение логов непринятых проектов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D007).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D011, D004, D007, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны онбординга, дисперсии Уэлфорда (`TocWelfordOnlineVarianceFalsificationTest`), зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`), артефактов дизайна (`DesignExcellenceGateFalsificationTest`, `DesignSystemFalsificationServiceTest`) и DBR-субординации (`TocSubordinationTruthTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Разделение уровней абстракции проверки гейта и задачи (ликвидация 388 дефектов DPMO), расчет частоты сторожа ТОС по Уэлфорду, сохранение журналов в куче без OOM через derived PageRequest, надежность цепочки свидетельств AI-ресурсов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон сервисных эндпоинтов Google AI (`GoogleAiResourceSecurityIntegrationTest`), нормализация путей и fail-closed барьер.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистые чтения `getDbrStatus()` без мутации состояния при опросе; каузальный след CAS-переходов `finalizing -> pending` в БД; отсечение самоотчётов агентов при проверке экранов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Belnap 4-valued]:**
  * 4-значная решётка исходов Six Sigma и вердиктного допуска (`passed`, `failed`, `undetermined`, `not-applied`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Teleosemantic feedback / Dretske & Clark]:**
  * Телеосемантическая обратная связь при троттлинге DBR-верёвки, Gestalt-близость геометрии экранов (< 1.0).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Монопольное владение графом ТОС и сессиями, изоляция кэша корпуса RAG.

---

## 3. Регламент отчёта инженера L2

1. Провести единый непрерывный пакетный прогон всех 26 тест-сьютов Кластера Секций XXXVII–XLIV в Docker Maven 3.9.9 Temurin-21 (-m 2g).
2. Зафиксировать результат 206/206 green, 0 строк правок рабочего кода.
3. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит в фабрику: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
