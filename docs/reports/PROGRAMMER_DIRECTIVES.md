# Директивы и наряд для L2 (2026-10-16 22:08 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI полностью заверены инженером (коммит `881660d8`, 174/174 green в 22 тест-сьютах единого Docker Maven прогона, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш корпуса, верёвка TOC, очистка журналов и завершающий баланс фабрики»)** целиком (**143 теста в едином пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС (`TocSentinelService` & `TocSentinelController`, 20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): чистые чтения `getDbrStatus()`, отсутствие мутации при чтении, динамический шедулинг частоты и обнаружение дедлоков (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008).
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели и медиа-активам (`GoogleAiResourceController` & `GoogleAiResourceSecurityIntegrationTest`, 18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтический барьер на все 5 мутирующих путей AI-ресурсов (401/403), нормализация путей и институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XXXIX — Метрики качества и аудит Six Sigma (`SixSigmaAuditService`, `QualityMetricsController`, `QualityGateController`, 31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): 4-значная логика истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация дефекта «388 ложных сбоев» разделением уровней абстракции проверки и задачи, делегирование гейта в SixSigma без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора веток (`StrandedFinalizingSweepService` & `BranchGarbageCollectorService`, 21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (13/13): атомарный CAS-релиз брошенных финализаций `finalizing -> pending`, динамический расчет лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрия (`DesignExcellenceGate`, `VerificationEvidenceGate`, `LayoutGeometryAuditService`, 33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12/12), `VerificationEvidenceGateTest` (8/8), `LayoutGeometryAuditServiceTest` (13/13): проверка реальных физических артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя (`ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Кэш постоянного корпуса:**
       - Снят как дублирующий (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008); сохранено единое кэширование FastEmbed в сайдкаре `PredictionService.py`.
     * **Группа 7: Секция XLIII — Верёвка TOC (`TocOptimizer`, 7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7/7): ограничение выпуска DBR rope, честный статус `Flow unmeasured` при отсутствии стадий (защита от ложного оптимума), приоритетный обход (`priority >= 80`) и троттлинг низкоприоритетных задач при заполнении буфера (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 8: Секция XLIV — Очистка журнала проекта (`ProjectEventLogRetentionService` & `SystemStatusController`, 13 тестов, 2 сьюта):**
       - `ProjectEventLogRetentionServiceTest` (10/10), `SystemStatusControllerTest` (3/3): частая задержка `fixedDelay`, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 9: Секция XVIII — Чего в этом перечне нет: завершающий баланс фабрики:**
       - Полная картина архитектурных границ, демаркация исключённых сущностей, отсутствие фронтенда и окончательное подтверждение суверенитета фабрики. Все 205 механизмов фабрики закрыты (ideal).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны экранного качества (артефакты PNG/JSON), верёвки TOC (запрет ложного оптимума при пустом потоке), сборки мусора веток и очистки журналов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции метрик Six Sigma (разведение уровней проверки и задачи, устранение дефекта «388 ложных сбоев») и цепочка надежности свидетельств очистки логов (`ProjectEventLogRetentionServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон всех мутирующих путей AI-ресурсов (401/403) и топология архитектурных границ фабрики (`GoogleAiResourceSecurityIntegrationTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` [D011 Teleosemantic drift / Dretske & Clark]:**
  * Средовые заслоны и телеосемантическая обратная связь: проверка физических файлов геометрии и скриншотов PR вместо субъективных отчетов исполнителя.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap & Tarski]:**
  * 4-значная таблица истинности качества Six Sigma (`passed`, `failed`, `undetermined`, `not-applied`) и честный статус DBR rope при отсутствии стадий.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия сторожа ТОС: чтение `getDbrStatus()` без паразитных пересчетов и мутаций графа, и каузальный след лизинга финализаций.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXXVII–XLIV и XVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
   ```
2. Подтвердить 100% green результат (143/143 green, 0 failures, 0 errors, 0 skipped, 15 тест-сьютов).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
