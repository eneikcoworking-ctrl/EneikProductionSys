# Директивы и наряд для L2 (2026-10-17 03:12 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики») полностью заверен инженером (коммит `5fc85bb5`, 193/193 green в 32 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2: что и как чинить»)** целиком (**224 теста в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6): согласованность RAG-корпуса из 86 файлов и 1720 детерминированных философских паттернов, отсутствие галлюцинированных ссылок (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RequirementGroundingServiceTest` (6/6), `RequirementGroundingServiceFalsificationTest` (5/5): фальсификационное заземление требований компилятора в реальные сущности без вымышленных конструкций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHUDEA_PERL_01_CAUSAL_LADDER` / D014).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач» (48 тестов, 7 сьютов):**
       - `TaskDuplicateDetectorTest` (5/5), `SemanticDuplicateVetoTest` (4/4): обнаружение и вето семантических дубликатов задач, защита от тихого размножения (`DZHILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlannedWorkRecoveryServiceTest` (15/15): безопасное восстановление зависших работ без потери детерминизма (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `DZHON_SHOFFER_01_GROUNDING_HIERARCHY` / D004);
       - `CompilerTaskIdentityTest` (3/3): персистентность тождества сгенерированных компилятором задач (Flyway V137) (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `TechnicalLeadCompilerIntegrationTest` (11/11), `TechnicalLeadCompilerFalsificationTest` (6/6), `TechnicalLeadCompilerTest` (4/4): компиляция требований в неделимые кванты задач со строгим допуском (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Секция XVI — Предписания для L2: реестр 59 архитектурных предписаний (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): деонтический барьер и безопасность сервисных путей внутреннего наблюдателя (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): анти-зеркальное чтение статуса сторожа ТОС без побочной мутации при опросе (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4): 4-значная решётка истинности ценности и контекста исполнения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6): сканирование категориальных ошибок стека без заражения платформы (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17): операционная правда фабрики с асимметричным ростом доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): попперовский фальсификационный заслон сходства и отсечение дубликатов вишлиста (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): неизменяемость системных настроек и деонтический барьер (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4): верификация строгой миграционной схемы базы данных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16): демаркация продуктовых пространств имён по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, репозиториев, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D007, D004, D013, D011, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские фальсификационные заслоны семантического сходства вишлистов (`WishlistServiceFalsificationTest`), тождества задач компилятора (`CompilerTaskIdentityTest`), заземления требований (`RequirementGroundingServiceFalsificationTest`) и схем миграций базы (`FlywayMigrationValidationTest`). Защита от фиктивно-зеленых тестов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции философского корпуса (`PhilosopherPatternCorpusConsistencyTest`), персистентного тождества компилятора по ключу V137 и конвейера задач (`TechnicalLeadCompilerIntegrationTest`); каузальная цепочка надёжности операционной правды и асимметричной динамики доверия (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический барьер и безопасность сервисных путей внутреннего наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), неизменяемость системных настроек (`SystemSettingsServiceTest`) и демаркация пространств имён по Закону 26 (`ProductNamespaceLaw26Test`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` & `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок стека репозитория (`RepositoryStackAnalyzerTest`) и вето семантических дубликатов задач (`TaskDuplicateDetectorTest`, `SemanticDuplicateVetoTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная решётка истинности ценности Lean и целевого контекста (`LeanValueTest`, `TargetContextTest`) без подмены неопределенности успехом.
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D007 Institutional facts / Searle & Tarski]:**
  * Институциональная регистрация дефектов и динамика скорости дубликатов без остановки потока фабрики.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Boundary topology / Shaffer & Varzi]:**
  * Монопольное владение восстановлением зависших работ (`PlannedWorkRecoveryServiceTest`) и топологическая граница пространств имён.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Telemetry / Wittgenstein]:**
  * Анти-зеркальное чтение статуса сторожа ТОС без побочных эффектов мутации базы при чтении (`TocSentinelServiceTest`, `TocSentinelControllerTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Телеосемантическая обратная связь и покэ-йокэ предохранитель зависания компилируемых вишлистов (`PlannedWorkRecoveryServiceTest`).
- **`DZHUDEA_PERL_01_CAUSAL_LADDER` & `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding capsule / Pearl & Church]:**
  * Каузальное заземление требований на реальные факты и исключение вымышленных артефактов (`RequirementGroundingServiceTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XIV, XV и XVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,SemanticDuplicateVetoTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить 100% зелёных тестов (**224/224 green в 26 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Соблюсти деонтический инвариант Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать запись секций в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (статус: ideal, замер в Docker Maven 224/224 green).
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк) и сделать коммит `feat(sections-xiv-xv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
