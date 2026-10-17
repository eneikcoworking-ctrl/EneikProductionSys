# Директивы и наряд для L2 (2026-10-17 01:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики») полностью заверен инженером (коммит `39b935bc`, 193/193 green в 32 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2»)** целиком (**224 теста в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): согласованность RAG-корпуса из 86 файлов и 1720 философских паттернов, отсутствие галлюцинированных ссылок (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RequirementGroundingServiceTest` (6/6 green), `RequirementGroundingServiceFalsificationTest` (5/5 green): фальсификационное заземление требований компилятора в реальные сущности без вымышленных конструкций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHUDEA_PERL_01_CAUSAL_LADDER` / D014).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач» (48 тестов, 7 сьютов):**
       - `TaskDuplicateDetectorTest` (5/5 green), `SemanticDuplicateVetoTest` (4/4 green): обнаружение и вето семантических дубликатов задач, защита от тихого размножения (`DZHILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): безопасное восстановление зависших работ без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `DZHON_SHOFFER_01_GROUNDING_HIERARCHY` / D004);
       - `CompilerTaskIdentityTest` (3/3 green): персистентность тождества сгенерированных компилятором задач (Flyway V137) (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `TechnicalLeadCompilerIntegrationTest` (11/11 green), `TechnicalLeadCompilerFalsificationTest` (6/6 green), `TechnicalLeadCompilerTest` (4/4 green): компиляция требований в неделимые кванты задач со строгим допуском (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XVI — Предписания для L2: реестр 59 архитектурных предписаний (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green), `GeminiObserverActionServiceTest` (17/17 green): деонтический барьер и безопасность сервисных путей внутреннего наблюдателя (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): анти-зеркальное чтение статуса сторожа ТОС без побочной мутации при опросе (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `LeanValueTest` (9/9 green), `TargetContextTest` (4/4 green): 4-значная решётка истинности ценности и контекста исполнения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6 green): сканирование категориальных ошибок стека без заражения платформы (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда фабрики с асимметричным ростом доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6 green), `WishlistContentSimilarityMatcherTest` (8/8 green), `WishlistSimilarityTest` (6/6 green): попперовский фальсификационный заслон сходства и отсечение дубликатов вишлиста (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8 green), `SystemSettingsServiceTest` (13/13 green): неизменяемость системных настроек и деонтический барьер (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4 green): верификация строгой миграционной схемы базы данных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16 green): демаркация продуктовых пространств имён по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D002, D004, D007, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны целостности схемы Flyway, сходства вишлистов (`WishlistServiceFalsificationTest`), требований компилятора (`TechnicalLeadCompilerFalsificationTest`) и заземления требований (`RequirementGroundingServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Строгий лок уровней абстракции компилятора задач, согласованность RAG-корпуса (86 файлов, 1720 образцов), персистентность тождества задач (V137) и операционная правда с асимметричной динамикой доверия.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты: деонтическая изоляция внутреннего наблюдателя, неизменяемость системных настроек, Закон 26 продуктовых пространств имён.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистое чтение статуса сторожа ТОС без побочной записи в узлы при опросе.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности LeanValue и TargetContext (`essential`, `valuable`, `waste`, `undetermined`).
- **`DZHILBERT_RAYL_02_CATEGORY_ERROR_SCAN` & `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Разделение застрявших дубликатов задач и скорости их порождения во времени, изоляция уровней абстракции стека в `RepositoryStackAnalyzerTest`.
- **`DZHON_SHOFFER_01_GROUNDING_HIERARCHY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Schaffer & Varzi]:**
  * Монопольное владение восстановлением зависших работ без потери детерминизма.
- **`DZHUDEA_PERL_01_CAUSAL_LADDER` & `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG grounding / Pearl & Church]:**
  * Строгое заземление сущностей и требований без галлюцинаций и фантомных записей механизмов.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XIV, XV и XVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   - Заверить все 26 тест-сьютов Кластера (224 теста: 0 failures, 0 errors, 0 skipped).
2. Соблюсти фундаментальный инвариант Ступени 4:
   - **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать результаты замера в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит и обновить `docs/reports/PROGRAMMER_STATE.md`.
