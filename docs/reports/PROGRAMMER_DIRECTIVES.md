# Директивы и наряд для L2 (2026-10-17 04:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI («Службы внешних клиентов, репозиториев, наблюдателя, пульта управления, выбора аккаунта и хранилища задач») полностью заверены инженером (коммит `ed9aa885`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны качества экрана, кэш постоянного корпуса, верёвка ТОС, очистка журнала проекта и завершающий баланс фабрики: Секции XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV, XVIII»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож TOC и адаптивный тайминг (20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): чистое чтение кэшированного снимка `getDbrStatus()` без паразитной мутации графа, адаптивная частота таймера (`min(meanDurationMs) / 2`), партитивное владение графом (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 2: Секция XXXVIII — Авторизация и безопасность AI-ресурсов (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтический барьер на все мутирующие пути AI-ресурсов (401/403), нормализация путей и институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XXXIX — Аудит Six Sigma и метрики качества (31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): 4-значная решётка истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), полная ликвидация ложного дефекта «388 сбоев» разделением уровней абстракции проверки и задачи, расчёты DPMO без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора (21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (13/13): атомарный CAS-релиз брошенных финализаций `finalizing -> pending`, динамический расчёт лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 5: Секция XLI — Гейты совершенства дизайна и свидетельств верификации (33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12/12), `VerificationEvidenceGateTest` (8/8), `LayoutGeometryAuditServiceTest` (13/13): проверка физических экранных артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя (`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Кэш постоянного корпуса (декоммиссионирован / inert):**
       - Ликвидация бэкенд-кэша `GeminiContextCacheManager` (0 байт), сохранение единого кэширования векторов в сайдкаре `PredictionService.py` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 7: Секция XLIII — Оптимизатор TOC и DBR-верёвка (7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7/7): ограничение выпуска DBR rope, честный статус `Flow unmeasured` при отсутствии стадий (защита от ложного оптимума), приоритетный обход (`priority >= 80`) и троттлинг низкоприоритетных задач при заполнении буфера (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 8: Секция XLIV — Удержание журнала событий проекта (13 тестов, 2 сьюта):**
       - `ProjectEventLogRetentionServiceTest` (10/10), `SystemStatusControllerTest` (3/3): непрерывная очистка журнала fixedDelay, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 9: Секция XVIII — Архитектурный баланс и демаркация суверенитета фабрики:**
       - Институциональное равновесие и гармония всех 205 механизмов фабрики пяти слоев.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификационные заслоны гейта дизайна (`DesignExcellenceGateTest`), свидетельств верификации (`VerificationEvidenceGateTest`), Six Sigma (`SixSigmaAuditServiceTest`) и DBR-оптимизатора TOC (`TocOptimizerTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: строгое разделение уровня единичной проверки гейта (`check-level`) и уровня готовности задачи (`task-level`); безопасная очистка журнала проекта без подъёма массивов строк в память JVM.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон сервисных и управляющих путей AI-ресурсов (`GoogleAiResourceSecurityIntegrationTest`), разграничение прав и обязанностей акторов при генерации ассетов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для предотвращения смешения неприменённых проверок с дефектами.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: чтение статуса верёвки `getDbrStatus()` является чистым чтением кэшированного снимка и не модифицирует наблюдаемые сущности.
- **`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` [D011 Semantic drift / Clark]:**
  * Телеосемантическая обратная связь: проверка физических файлов скриншотов и DOM-геометрии вместо текстовых деклараций агента.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Mereology & Topology / Varzi]:**
  * Партитивное владение графом TOC и топологическая изоляция границ авторизации.
- **`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Noë]:**
  * Физический след каузальных процессов: атомарный CAS-переход брошенных задач и безопасная зачистка веток Git.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XXXVII–XLIV и XVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
   ```
2. Подтвердить 100% green результат (**143/143 green в 15 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
