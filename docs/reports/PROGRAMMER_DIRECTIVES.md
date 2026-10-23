# Директивы и наряд для L2 (2026-10-23 11:14 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Клиент предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта, Хранилище задач») полностью заверен и закрыт инженером (коммит `d7455c59`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:41 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXXVII–XLIV («Сторож ТОС, Вход к ресурсам модели, Метрики качества Six Sigma, Выметающий обход, Заслон качества экрана, Постоянный кэш корпуса, Верёвка ТОС и Очистка журнала проекта»)** целиком (**206 тестов в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС (5 сьютов, 33 теста):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста), `TocNodeTimeoutTest` (4 теста): анти-зеркальное чтение состояния без скрытых пересчетов и мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocPipelinePhasesInstrumentationTest` (4 теста): онлайн-алгоритм Уэлфорда для дисперсии задержек фаз и попперовская фальсификация аномалий (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели и граница доступа (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтический барьер на мутирующие операции генерации видео/модели (401/403) и изоляция эндпоинтов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 3: Секция XXXIX — Метрики качества Six Sigma и операционная правда (2 сьюта, 29 тестов):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов): 4-значная таблица истинности качества (DPMO, Cpk, дефекты) без категориального смешения счетчиков (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 4: Секция XL — Выметающий обход (4 сьюта, 25 тестов):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов): CAS-свип брошенных заявок в состоянии finalizing без внешнего держателя (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `StrandedDesignCycleSweepServiceTest` (4 теста), `GitHubSweepBudgetEfficiencyTest` (7 тестов): экономная очистка циклов дизайна и удержание квот API (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрии дизайна (6 сьютов, 59 тестов):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): попперовская фальсификация качества артефактов и экранов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `LayoutGeometryAuditServiceTest` (13 тестов), `DesignConsistencyAuditServiceTest` (15 тестов): телеосемантическая сверка физических файлов, DOM-геометрии и дизайн-токенов (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` / D015);
       - `DesignDriftMonitorServiceTest` (7 тестов), `DesignSystemFalsificationServiceTest` (6 тестов): анти-зеркальная телеметрия дрейфа и фальсификационные заслоны правил оформления (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Постоянный кэш корпуса знаний (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): изоляция RAG standing-knowledge и сайдкар-кэша (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014).
     * **Группа 7: Секция XLIII — Верёвка ТОС: предел буфера и динамическая субординация (5 сьютов, 29 тестов):**
       - `TocOptimizerTest` (7 тестов), `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `TocPriorityClaimTest` (1 тест), `TocPersistenceSnapshotTest` (4 теста): статус ненагруженного потока (`undetermined`), субординация рычагов DBR по Голдману и персистентные снимки состояния (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 8: Секция XLIV — Очистка журнала проекта: скорость роста против частоты очистки (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): непрерывный минутный цикл очистки журнала событий без подъёма избыточных записей в память JVM через `PageRequest.of(excess - 1, 1)` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D011, D012, D013, D004, D015, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны гейтов дизайна (`DesignExcellenceGateFalsificationTest`), дизайна оформления (`DesignSystemFalsificationServiceTest`), субординации ТОС (`TocOptimizerTest`), онлайн-дисперсии (`TocWelfordOnlineVarianceFalsificationTest`) и свипа зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции проверки и задачи; безопасная потоковая очистка логов без подъёма heap через `PageRequest.of(excess - 1, 1)`; снимки персистентности и адаптивный триггер Уэлфорда.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический барьер на мутирующие пути AI-ресурсов (401/403) и институциональный аудит полномочий актора.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `RUT_BARKAN_MARKUS_03_MODAL_ACTUALISM` [D011 / D015 Dretske & Marcus]:**
  * Проверка физических файлов экранов и DOM-геометрии, Gestalt-близость элементов вместо текстовых самоотчетов модели.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap]:**
  * 4-значная решётка истинности метрик качества Six Sigma (DPMO, Cpk).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистый снимок верёвки `getDbrStatus()` без мутации графа; атомарный CAS-релиз финализаций.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Varzi]:**
  * Партитивное владение графом TOC и узлами конвейера.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Church]:**
  * Изоляция standing-knowledge RAG и сайдкар-кэша.

---

## 3. Критерии приемки наряда L2

1. Провести единый непрерывный пакетный прогон всех 26 тест-сьютов Кластера Секций XXXVII–XLIV в Docker Maven 3.9.9 Temurin-21 (-m 2g):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,TocNodeTimeoutTest,TocWelfordOnlineVarianceFalsificationTest,TocPipelinePhasesInstrumentationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,QualityMetricsControllerTest,SixSigmaAuditServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedDesignCycleSweepServiceTest,GitHubSweepBudgetEfficiencyTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,LayoutGeometryAuditServiceTest,DesignConsistencyAuditServiceTest,DesignDriftMonitorServiceTest,DesignSystemFalsificationServiceTest,SystemStatusControllerTest,TocOptimizerTest,TocSubordinationTruthTest,TocSubordinationLeverTest,TocPriorityClaimTest,TocPersistenceSnapshotTest,ProjectEventLogRetentionServiceTest
   ```
2. Подтвердить 100% результат: **206/206 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
3. Соблюсти деонтический запрет: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. Сделать семантический коммит в фабрику: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
7. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
