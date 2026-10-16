# Пакетная директива L1/L3 -> L2: Кластер Секций XIV, XV и XVI — Философский корпус, защита от тихого размножения задач и реестр 59 предписаний L2

**Дата:** 2026-10-16 07:12 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию/кластер механизмов целиком. Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием») успешно заверен инженером (коммит `fc3ce61`, 189/189 green в Docker Maven, 0 строк изменений кода). Настоящая директива открывает пакетный замер следующего фундаментального кластера фабрики — **Кластера Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2»)** целиком (224 теста, 26 сьютов):
   - **Секция XIV: Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
     * `PhilosopherPatternCorpusConsistencyTest` (6/6) — сверка целостности RAG-корпуса образцов философов (`docs/philosopher-patterns/`), согласованность 53 семейств, 104 общих паттернов и фабрично-выведенных образцов без коллизий и галлюцинаций (D014 Church & Pearl, D008 Popper).
     * `RequirementGroundingServiceTest` (6/6) & `RequirementGroundingServiceFalsificationTest` (5/5) — семантическое заземление требований, верификация соответствия артефактов уставным правилам, фальсификация неподтвержденных связей (D008 Popper, D010 Goldman).
   - **Секция XV: Класс дефекта «тихое размножение задач»: подпись, замер, случаи (48 тестов, 7 сьютов):**
     * `TaskDuplicateDetectorTest` (5/5) — детектирование семантических дубликатов задач, защита от циклической генерации дублей (D002 Ryle, D010 Goldman).
     * `PlannedWorkRecoveryServiceTest` (15/15) — безопасное восстановление запланированных работ без повторного порождения идентичных сущностей (D004 Shaffer).
     * `CompilerTaskIdentityTest` (3/3) & `SemanticDuplicateVetoTest` (4/4) — неизменяемое тождество задач при компиляции требований и вето на семантические дубликаты (D001 Kripke, D006 Raz).
     * `TechnicalLeadCompilerIntegrationTest` (11/11), `TechnicalLeadCompilerFalsificationTest` (6/6), `TechnicalLeadCompilerTest` (4/4) — декомпозиция эпиков в детерминированный граф задач без разрастания скоупа и потери контекста (D004 Varzi, D008 Popper).
   - **Секция XVI: Реестр 59 предписаний L2: что и как чинить (159 тестов, 16 сьютов):**
     * `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17) — запрет неавторизованного доступа к внутренней диагностике и управляющим воздействиям модели (D006 Raz).
     * `TocSentinelServiceTest` (18/18) & `TocSentinelControllerTest` (2/2) — защита потока по теории ограничений (ТОС), инкапсуляция графа ограничений и выявление аномалий буфера (D004 Varzi, D008 Popper).
     * `LeanValueTest` (9/9) & `TargetContextTest` (4/4) — 4-значная логика Белнапа (`undetermined` при неизвестном значении), предотвращение ложных суждений о ценности и контексте задач (D012 Belnap).
     * `RepositoryStackAnalyzerTest` (6/6) — локализация сбоев анализатора, предотвращение ложных находок о заказчике при отказе авторизации (D002 Ryle, D010 Goldman).
     * `OperationalTruthServiceTest` (17/17) — асимметричная динамика доверия: доверие начинается с нуля и прирастает строго по свидетельствам (D010 Goldman, D013 Wittgenstein).
     * `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6) — сличение схожести элементов вишлиста, защита от дублирования требований клиента (D002 Ryle, D008 Popper).
     * `SettingsControllerIntegrationTest` (8/8) & `SystemSettingsServiceTest` (13/13) — инварианты системных настроек и детерминированное поведение флагов (D006 Raz).
     * `FlywayMigrationValidationTest` (4/4) — защита от подделки и рассинхронизации миграций базы данных (D008 Popper, D010 Goldman).
     * `ProductNamespaceLaw26Test` (16/16) — демаркация пространств имен продукта и фабрики по закону 26 (D003 Quine, D006 Raz).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем компилятора, детектора дубликатов, философского корпуса и предписаний (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D012, D014, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны воспроизводимо падать при подмене миграций, неавторизованном доступе или расхождении философского корпуса (`RequirementGroundingServiceFalsificationTest`, `TechnicalLeadCompilerFalsificationTest`, `WishlistServiceFalsificationTest`, `FlywayMigrationValidationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Каузальная цепочка надёжности данных: строгое заземление сущностей от требования до задачи, асимметричный рост доверия в `OperationalTruthServiceTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код: эндпоинты `InternalGeminiObserverController` и `SettingsControllerIntegrationTest` защищены авторизационными заслонами и вето на уровне типов.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category separation / Ryle]:**
  * Предотвращение категориальных ошибок: недоступность репозитория не трактуется как дефект кода заказчика (`RepositoryStackAnalyzerTest`); синтаксическое подобие строк не приравнивается к семантическому совпадению задач (`TaskDuplicateDetectorTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Целостность графа компиляции и монопольное владение подзадачами (`PlannedWorkRecoveryServiceTest`, `TechnicalLeadCompilerIntegrationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * 4-значная таблица истинности: явное сохранение состояния `undetermined` в `LeanValueTest` и `TargetContextTest`.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` & `DZHUDEYA_PERL_01_CAUSAL_DAG_VALIDATION` [D014 Grounding gap / Church & Pearl]:**
  * Заземление RAG-корпуса: `PhilosopherPatternCorpusConsistencyTest` верифицирует согласованность 53 семейств паттернов и предотвращает галлюцинаторный дрейф.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Кластера Секций XIV, XV и XVI в едином непрерывном запуске Docker Maven (все 26 тест-сьютов, 224 теста):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Кластер Секций XIV, XV и XVI) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(prescriptions-and-duplicates): complete Stage 4 batch verification (D008, D010, D006)`.
