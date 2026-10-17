# Директивы и наряд для L2 (2026-10-17 06:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Интерфейсы внешней модели ML, brownfield-разбор репозиториев клиента, завод репозиториев, пульт управления фабрикой, внутренний защищённый вход наблюдателя, ротация и здоровье аккаунтов, хранилище задач и инварианты Закона 20 и Закона 1») полностью заверен инженером (коммит `83cbe44f`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш корпуса, верёвка TOC, очистка журналов и завершающий баланс фабрики»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС и адаптивный тайминг (20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): чистое чтение снимка сторожа ТОС `getDbrStatus()` без паразитных скрытых мутаций графа, изоляция вычисления от наблюдения, динамический триггер частоты (`min(meanDurationMs) / 2`), расслабление до 10 с при простое, инкапсуляция графа без утечки геттеров (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели и медиа-активам (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6/6 green), `GoogleAiResourceSecurityIntegrationTest` (12/12 green): деонтический барьер на все мутирующие пути AI-ресурсов (401 при отсутствии ключа, 403 при неверном, fail-closed при отсутствии `ENEIK_SECURITY_API_KEY`), институциональный аудит актора через `AuditCallerResolver`, нормализация путей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3: Секция XXXIX — Метрики качества и аудит Six Sigma (31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26 green), `QualityMetricsControllerTest` (3/3 green), `QualityGateControllerTest` (2/2 green): 4-значная решётка истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация смешения категорий «проверка» и «задача», расчет DPMO и Cpk без смешения категорий, делегирование гейта в SixSigma без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора веток (21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8 green), `BranchGarbageCollectorServiceTest` (13/13 green): восстановление брошенных финализаций `finalizing -> pending` без необходимости внешнего держателя, атомарный CAS-релиз, динамический расчет лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная очистка orphan-веток (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5: Секция XLI — Гейты совершенства дизайна и свидетельств верификации (33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12/12 green), `VerificationEvidenceGateTest` (8/8 green), `LayoutGeometryAuditServiceTest` (13/13 green): проверка реальных физических экранных артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо словесных самоотчётов исполнителя, строгие заслоны верификационных свидетельств (`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса:**
       - Подтверждение ликвидации дублирующего бэкенд-кэша `GeminiContextCacheManager` (0 байт), единое кэширование в сайдкаре `PredictionService.py`, RAG-граундинг капсулы Чёрча, изоляция от контекстного дрейфа (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 7: Секция XLIII — Верёвка TOC и оптимизатор (7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7/7 green): ограничитель выпуска DBR-верёвки, честный статус `Flow unmeasured` при отсутствии стадий (защита от ложного оптимума), приоритетный обход (`priority >= 80`) и троттлинг низкоприоритетных задач при заполнении буфера без ложных блокировок потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 8: Секция XLIV — Очистка журналов проекта (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10/10 green): безопасная ротация и очистка журнала проекта без подъёма памяти в JVM через `PageRequest.of(excess - 1, 1)`, выведение частоты очистки из темпа роста (`fixedDelay`), сохранение криминалистической истории непринятых проектов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER`);
     * **Группа 9: Секция XVIII — Завершающий баланс фабрики и читающие контроллеры (3 теста, 1 сьют):**
       - `SystemStatusControllerTest` (3/3 green): чистые read-only контракты системного статуса без скрытых мутаций, окончательное подтверждение архитектурного баланса и суверенитета фабрики (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские и тарскианские фальсификационные заслоны: отсечение ложных подтверждений в Six Sigma (`SixSigmaAuditServiceTest`), верёвке TOC (`TocOptimizerTest`), гейтах свидетельств (`VerificationEvidenceGateTest`) и выметающих обходах (`StrandedFinalizingSweepServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: изоляция слоев абстракции от AI-ресурсов до Six Sigma аудита и криминалистической истории журнала проекта (`ProjectEventLogRetentionServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон мутирующих эндпоинтов Google AI ресурсов (`GoogleAiResourceSecurityIntegrationTest`), нормализация путей и аудит актора.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная решётка истинности в аудите Six Sigma (`passed`, `failed`, `undetermined`, `not-applied`) и DBR-статусе верёвки TOC.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: опрос сторожа ТОС и системного статуса не производит побочных мутаций и отражает исключительно физическое состояние конвейера.
- **`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Clark & Dretske]:**
  * Верификация физических экранных артефактов и геометрии вьюпорта (`desktop-1440.png`, `mobile-375.png`) вместо словесных самоотчётов исполнителя.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole confusion / Varzi]:**
  * Четкое владение жизненным циклом выметающих обходов и инкапсуляция графа TOC.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D014 / D010 Church]:**
  * Капсулирование RAG-контекста и выведение частоты очистки журнала из темпа роста.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 15 тест-сьютов Кластера Секций XXXVII–XLIV и XVIII (143 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**143/143 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговые блоки Кластера Секций XXXVII–XLIV и XVIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(sections-xxxvii-xliv-xviii): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
