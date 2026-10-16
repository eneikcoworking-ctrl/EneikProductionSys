# Пакетная директива L1/L3 -> L2: Кластер Секций XIV, XV и XVI — Философский корпус, защита от тихого размножения задач и реестр предписаний

**Дата:** 2026-10-16 03:55 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий стратегический кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием») успешно заверен, подтвержден и закрыт инженером (коммит `c16c70d`, 189/189 green в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов», «Класс дефекта "тихое размножение задач": подпись, замер, случаи» и «Реестр архитектурных предписаний для L2: 59 предписаний»)**:
   - **Контур XIV: Сверка с корпусом философских образцов (17 тестов, 100% green):**
     * Механизмы: `docs/philosopher-patterns` (86 файлов, 1720 строк, 15 кодов дефектов), `Defect-family application map`, `FACTORY_MECHANISMS grounding guard`.
     * Заслоны: `PhilosopherPatternCorpusConsistencyTest` (6/6), `RequirementGroundingServiceTest` (6/6), `RequirementGroundingServiceFalsificationTest` (5/5).
   - **Контур XV: Класс дефекта "тихое размножение задач" (48 тестов, 100% green):**
     * Механизмы: `TaskDuplicateDetector` (разделение залипших дубликатов и скорости генерации), `ContinuousOrchestrationService.checkDuplicateGenerationVelocity` (наблюдение скорости без стопа), `PlannedWorkRecoveryService` (восстановление вишлистов без OOM), `Compiler work identity & admission` (тождество задач V137, вето семантических дубликатов, CAS-финализация, Закон 26).
     * Заслоны: `TaskDuplicateDetectorTest` (5/5), `PlannedWorkRecoveryServiceTest` (15/15), `CompilerTaskIdentityTest` (4/4), `SemanticDuplicateVetoTest` (6/6), `TechnicalLeadCompilerIntegrationTest` (10/10), `TechnicalLeadCompilerFalsificationTest` (6/6), `TechnicalLeadCompilerTest` (2/2).
   - **Контур XVI: Реестр 59 архитектурных предписаний для L2 (159 тестов, 100% green):**
     * Механизмы: `InternalGeminiObserverSecurityIntegration`, `InternalGeminiObserverController`, `GeminiObserverActionService`, `TocSentinelService`, `TocSentinelController`, `LeanValue`, `TargetContext`, `RepositoryStackAnalyzer`, `OperationalTruthService`, `WishlistServiceFalsification`, `WishlistContentSimilarityMatcher`, `WishlistSimilarity`, `SettingsControllerIntegration`, `SystemSettingsService`, `FlywayMigrationValidation`, `ProductNamespaceLaw26`.
     * Заслоны: 159 тестов в Docker Maven, 100% green.
   - **Итоговый единый пакетный замер кластера:** 224 теста, 100% green в едином непрерывном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, контроллеров и репозиториев (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D012, D014, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: `PhilosopherPatternCorpusConsistencyTest`, `RequirementGroundingServiceFalsificationTest`, `TechnicalLeadCompilerFalsificationTest`, `WishlistServiceFalsificationTest` проверяют способность заслонов падать при нарушении целостности корпуса образцов, фантомных требованиях, семантических дубликатах или несанкционированном расширении скоупа.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение словаря истины философии, мета-модели дефектов и исполняемого кода; сохранение происхождения задач компилятора через тождество V137 (`compile:<projectId>:<hash>`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код в топологии системы: демаркация деонтических запретов Предписаний 1–59 Секции XVI, запрет неавторизованного доступа к AI-ресурсам и изоляция пространства имен Закона 26 (`ProductNamespaceLaw26Test`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Категорическое разделение залипших дубликатов задач и скорости их генерации во времени в `TaskDuplicateDetectorTest`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` [D004 Boundary drift / Shaffer & Gilbert]:**
  * Монопольное владение вишлистами и задачами в `PlannedWorkRecoveryService` с безопасным восстановлением без гонок.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная логика истинности в `LeanValueTest` и `TargetContextTest`, исключающая неоднозначные статусы задач.
- **`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` [D014 Grounding failure / Pearl]:**
  * Строгое заземление сущностей и требований в `RequirementGroundingServiceTest` без фантомных связей.
- **`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact / Tarski]:**
  * Неотменяемая фиксация фактов и скорости размножения задач в журнале дефектов фабрики.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XIV, XV и XVI в Docker Maven (224 теста green):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить статус `ideal` в блоках `### Итог Секции XIV`, `### Итог Секции XV`, `### Итог Секции XVI` и общего блока `### Итог кластера Секций XIV, XV и XVI` в `docs/FACTORY_MECHANISMS.md`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(sections-xiv-xv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
