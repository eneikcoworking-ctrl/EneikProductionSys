# Директивы и наряд для L2 (2026-10-16 17:26 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI («Клиент к предсказателю, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач») полностью заверены инженером (коммит `9617395`, 136/136 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики»)** целиком (143 теста в 14 тест-сьютах единого пакетного прогона в Docker Maven):
     * **Группа 1: Секция XXXVII — Сторож ТОС (`TocSentinelService` & `TocSentinelController`, 20 тестов):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): чистые чтения `getDbrStatus()`, отсутствие мутации при чтении, динамический шедулинг частоты и обнаружение дедлоков (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели и медиа-активам (`GoogleAiResourceController` & `GoogleAiResourceSecurityIntegrationTest`, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтический барьер на все 5 мутирующих путей AI-ресурсов (401/403), нормализация путей и институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3: Секция XXXIX — Метрики качества и аудит Six Sigma (`SixSigmaAuditService`, `QualityMetricsController`, `QualityGateController`, 31 тест):**
       - `SixSigmaAuditServiceTest` (26/26), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): 4-значная логика истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), полная ликвидация дефекта «388 ложных сбоев» разделением уровней абстракции проверки и задачи, делегирование гейта в SixSigma без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора веток (`StrandedFinalizingSweepService` & `BranchGarbageCollectorService`, 21 тест):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (13/13): атомарный CAS-релиз брошенных финализаций `finalizing -> pending`, динамический расчет лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрия (`DesignExcellenceGate`, `VerificationEvidenceGate`, `LayoutGeometryAuditService`, 33 теста):**
       - `DesignExcellenceGateTest` (12/12), `VerificationEvidenceGateTest` (8/8), `LayoutGeometryAuditServiceTest` (13/13): проверка реальных физических артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя (`ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса:**
       - Снят/decommissioned как дублирующий (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008); питоновский кэш в сайдкаре FastEmbed/PredictionService сохранен на живом пути;
     * **Группа 7: Секция XLIII — Верёвка TOC (`TocOptimizer`, 7 тестов):**
       - `TocOptimizerTest` (7/7): ограничение выпуска DBR rope, честный статус `Flow unmeasured` при отсутствии стадий (защита от ложного оптимума), приоритетный обход (`priority >= 80`) и троттлинг низкоприоритетных задач при заполнении буфера (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 8: Секция XLIV — Очистка журнала проекта (`ProjectEventLogRetentionService` & `SystemStatusController`, 13 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10/10), `SystemStatusControllerTest` (3/3): частая задержка `fixedDelay`, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
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
  * Лок уровней абстракции и надежная цепочка вывода: разделение уровней абстракции проверки и задачи в Six Sigma (ликвидация дефекта 388 ложных сбоев), защита памяти кучи при ротации журналов (`PageRequest.of(excess - 1, 1)`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон на 5 мутирующих эндпоинтов AI-ресурсов (401/403) и институциональный аудит актора.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Boundary topology / Varzi]:**
  * Четкая топология владения ресурсами, изоляция путей контроллеров и разграничение зон ответственности сервисов.
- **`ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` & `FRED_DRETSKE_01_TELEOSEMANTIC_FEEDBACK` [D011 Subsystem drift / Clark & Dretske]:**
  * Физические артефакты среды как свидетельства качества верстки вместо нарративных самоотчетов.
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Tarski & Belnap]:**
  * 4-значная логика истинности в Six Sigma (`passed`, `failed`, `undetermined`, `not-applied`) и трехзначный статус в верёвке ТОС.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Каузальный след процессов в рантайме, анти-зеркало телеметрии в `getDbrStatus()` без паразитных мутаций.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXXVII–XLIV в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest`
2. Зафиксировать результат (143/143 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
