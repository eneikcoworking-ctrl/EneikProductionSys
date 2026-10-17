# Директивы и наряд для L2 (2026-10-17 20:45 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Клиент предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта, Хранилище задач») полностью заверен и закрыт инженером (коммит `4a10080f`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:03 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXXVII–XLIV («Сторож ТОС, Вход к ресурсам модели, Метрики качества Six Sigma, Выметающий обход, Заслон качества экрана, Постоянный кэш корпуса, Верёвка ТОС и Очистка журнала проекта»)** целиком (**206 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (5 сьютов, 33 теста):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): чистое анти-зеркальное чтение `getDbrStatus()` без мутаций внутреннего состояния графа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TocNodeTimeoutTest` (4 теста), `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocPipelinePhasesInstrumentationTest` (4 теста): фальсификационная проверка тайм-аутов фаз графа, онлайн-алгоритм Уэлфорда для вычисления дисперсии и динамического периода сторожа (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтическая граница доступа к AI-ресурсам, fail-closed защита эндпоинтов, отсечение path traversal за пределы `./data/video-assets` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XXXIX — Метрики качества Six Sigma и операционная правда (2 сьюта, 29 тестов):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов): 4-значная решётка истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация дефекта «388 ложных сбоев» через лок уровней абстракции проверки и задачи, устранение `findAll()`-сканирований базы данных (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 4: Секция XL — Выметающий обход застрявших циклов и финализаций (4 сьюта, 25 тестов):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по временной метке `finalizingSince`, каузальный след транзитных захватов (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `StrandedDesignCycleSweepServiceTest` (4 теста), `GitHubSweepBudgetEfficiencyTest` (7 тестов): автоматическое высвобождение брошенных дизайн-циклов и бюджетирование вызовов GitHub API (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 5: Секция XLI — Заслон качества экрана: дизайн-система, геометрия и дрейф (6 сьютов, 59 тестов):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): попперовская фальсификация гейтов совершенства дизайна (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `LayoutGeometryAuditServiceTest` (13 тестов), `DesignConsistencyAuditServiceTest` (15 тестов): телеосемантическая проверка DOM-геометрии и целостности токенов дизайн-системы (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` / D015);
       - `DesignDriftMonitorServiceTest` (7 тестов), `DesignSystemFalsificationServiceTest` (6 тестов): объективная телеметрия дрейфа стилей и фальсификационные заслоны правил оформления (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Постоянный кэш корпуса знаний (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): изоляция RAG standing-knowledge и сайдкар-кэша (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014).
     * **Группа 7: Секция XLIII — Верёвка ТОС: предел буфера и динамическая субординация (5 сьютов, 29 тестов):**
       - `TocOptimizerTest` (7 тестов), `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `TocPriorityClaimTest` (1 тест), `TocPersistenceSnapshotTest` (4 теста): честный статус ненагруженного потока (`undetermined`), субординация рычагов DBR по правилам Голдмана и персистентные снимки состояния (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 8: Секция XLIV — Очистка журнала проекта: скорость роста против частоты очистки (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): непрерывный минутный цикл очистки журнала событий без подъёма избыточных записей в память JVM через `PageRequest.of(excess - 1, 1)` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D011, D012, D013, D004, D015, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны гейтов дизайна (`DesignExcellenceGateFalsificationTest`), дизайна оформления (`DesignSystemFalsificationServiceTest`), субординации ТОС (`TocOptimizerTest`), онлайн-дисперсии (`TocWelfordOnlineVarianceFalsificationTest`) и свипа зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Надежность цепочки свидетельств AI-ресурсов (`GoogleAiResourceSecurityIntegrationTest`), лок уровней абстракции проверки и задачи в Six Sigma (`SixSigmaAuditServiceTest`), потоковая очистка логов без подъёма в память JVM (`ProjectEventLogRetentionServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон сервисных эндпоинтов Google AI-ресурсов (401/403, `GoogleAiResourceControllerTest`), изоляция каталога `./data/video-assets`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` [D011 Teleosemantic drift & D015 Modal actualism]:**
  * Проверка физических файлов экранов и DOM-геометрии (`LayoutGeometryAuditServiceTest`), модальный актуализм дизайн-токенов (`DesignConsistencyAuditServiceTest`).
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Belnap/Tarski truth lattice]:**
  * 4-значная решётка истинности качества Six Sigma (`passed`, `failed`, `undetermined`, `not-applied`) в `QualityMetricsControllerTest`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистое анти-зеркальное чтение снимка верёвки ТОС `getDbrStatus()` без мутации графа (`TocSentinelServiceTest`), каузальный след и CAS-релиз брошенных финализаций (`StrandedFinalizingSweepServiceTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole mereology / Varzi]:**
  * Партитивное владение графом ТОС и бюджетом обхода GitHub API (`GitHubSweepBudgetEfficiencyTest`).
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG grounding / Church]:**
  * Изоляция standing-knowledge корпуса и сайдкар-кэша (`SystemStatusControllerTest`).

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,TocNodeTimeoutTest,TocWelfordOnlineVarianceFalsificationTest,TocPipelinePhasesInstrumentationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,QualityMetricsControllerTest,SixSigmaAuditServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedDesignCycleSweepServiceTest,GitHubSweepBudgetEfficiencyTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,LayoutGeometryAuditServiceTest,DesignConsistencyAuditServiceTest,DesignDriftMonitorServiceTest,DesignSystemFalsificationServiceTest,SystemStatusControllerTest,TocOptimizerTest,TocSubordinationTruthTest,TocSubordinationLeverTest,TocPriorityClaimTest,TocPersistenceSnapshotTest,ProjectEventLogRetentionServiceTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводятся по директиве оператора.
