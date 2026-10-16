# Директивы и наряд для L2 (2026-10-16 10:59 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием») успешно заверен инженером (коммит `3b9a122`, 189/189 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластера Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2»)** целиком (26 тест-сьютов, 224 теста в едином пакетном прогоне в Docker Maven):
     * **Секция XIV: Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6) — сверка целостности RAG-корпуса образцов философов (`docs/philosopher-patterns/`), согласованность 53 семейств, 104 общих паттернов и фабрично-выведенных образцов без коллизий и галлюцинаций (D014 Church & Pearl, D008 Popper);
       - `RequirementGroundingServiceTest` (6/6), `RequirementGroundingServiceFalsificationTest` (5/5) — семантическое заземление требований, верификация соответствия артефактов уставным правилам, фальсификация неподтвержденных связей (D008 Popper, D010 Goldman).
     * **Секция XV: Класс дефекта «тихое размножение задач»: подпись, замер, случаи (48 тестов, 7 сьютов):**
       - `TaskDuplicateDetectorTest` (5/5) — детектирование семантических дубликатов задач, защита от циклической генерации дублей (D002 Ryle, D010 Goldman);
       - `PlannedWorkRecoveryServiceTest` (15/15) — безопасное восстановление запланированных работ без повторного порождения идентичных сущностей (D004 Shaffer);
       - `CompilerTaskIdentityTest` (3/3), `SemanticDuplicateVetoTest` (4/4) — неизменяемое тождество задач при компиляции требований и деонтическое вето на семантические дубликаты (D001 Kripke, D006 Raz);
       - `TechnicalLeadCompilerIntegrationTest` (11/11), `TechnicalLeadCompilerFalsificationTest` (6/6), `TechnicalLeadCompilerTest` (4/4) — декомпозиция эпиков в детерминированный граф задач без разрастания скоупа и потери контекста (D004 Varzi, D008 Popper).
     * **Секция XVI: Реестр 59 предписаний L2: что и как чинить (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17) — запрет неавторизованного доступа к внутренней диагностике и управляющим воздействиям модели (D006 Raz);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2) — защита потока по теории ограничений (ТОС), инкапсуляция графа ограничений и выявление аномалий буфера (D004 Varzi, D008 Popper);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4) — 4-значная логика Белнапа (`undetermined` при неизвестном значении), предотвращение ложных суждений о ценности и контексте задач (D012 Belnap);
       - `RepositoryStackAnalyzerTest` (6/6) — локализация сбоев анализатора, предотвращение ложных находок о заказчике при отказе авторизации (D002 Ryle, D010 Goldman);
       - `OperationalTruthServiceTest` (17/17) — асимметричная динамика доверия: доверие начинается с нуля и прирастает строго по свидетельствам (D010 Goldman, D013 Wittgenstein);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6) — сличение схожести элементов вишлиста, защита от дублирования требований клиента (D002 Ryle, D008 Popper);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13) — инварианты системных настроек и детерминированное поведение флагов (D006 Raz);
       - `FlywayMigrationValidationTest` (4/4) — защита от подделки и рассинхронизации миграций базы данных (D008 Popper, D010 Goldman);
       - `ProductNamespaceLaw26Test` (16/16) — демаркация пространств имен продукта и фабрики по закону 26 (D003 Quine, D006 Raz).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем компилятора, детектора дубликатов, философского корпуса и предписаний (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D001, D002, D004, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: воспроизводимые заслоны целостности корпуса (`PhilosopherPatternCorpusConsistencyTest`), строгие тесты компилятора (`TechnicalLeadCompilerFalsificationTest`), исключение ложных зеленых прогонов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: исключение разрыва связей требований и задач (`RequirementGroundingServiceTest`), сохранение динамики доверия (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимые деонтические заслоны доступа к наблюдателю модели и настройкам (`InternalGeminiObserverSecurityIntegrationTest`, `SettingsControllerIntegrationTest`).
- **`SOL_KRIPKE_01_RIGID_DESIGNATOR_CORE` [D001 Rigid designator / Kripke]:**
  * Неизменяемое жесткое обозначение сущностей: тождество задачи компилятора строится строго от контентного ключа исходной единицы (`CompilerTaskIdentityTest`).
- **`GILBERT_RAYL_01_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Предотвращение категориальных ошибок: недопущение смешения внешних требований и системных сбоев (`TaskDuplicateDetectorTest`, `RepositoryStackAnalyzerTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Топологическая монополия владения: безопасное восстановление незавершенных компиляций вишлистов без повторного размножения задач (`PlannedWorkRecoveryServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная логика истинности в суждениях о ценности Lean и целевом контексте задач (`LeanValueTest`, `TargetContextTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: замер правды доверия не мутирует внутреннее состояние и строится строго по проверяемым событиям (`OperationalTruthServiceTest`).
- **`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Pearl]:**
  * Защита от галлюцинаций: каждый паттерн в корпусе строго заземлен на конкретное семейство, якорный текст и проверяемое обязательство доказательства.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Кластера Секций XIV, XV и XVI:**
   - **Docker Maven (224 теста, 26 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
     ```
2. **Верификация результата:**
   - Строго **224/224 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XIV, XV и XVI в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XIV, XV и XVI в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xiv-xv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
