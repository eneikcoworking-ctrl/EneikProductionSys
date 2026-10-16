# Директивы и наряд для L2 (2026-10-16 12:40 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Строгие семейства Ступени 4 («Заслоны качества задач, решётка вердиктов и провижининг проектов») успешно заверены инженером (коммит `e747af4`, 144/144 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **«Секции I и II: Приём требования и Компиляция (рыночный корпус, онбординг, сборка графа задач, декомпозиция и вишлист)» целиком** (44 тест-сьюта, 305 тестов в едином пакетном прогоне Docker Maven):
     * **Группа 1: Секция I — Приём требования и провижининг проектов (165 тестов, 22 сьюта):**
       - `ProjectFlowServiceTest` (44/44), `ProjectFlowServiceFalsificationTest` (6/6), `ProjectFlowServiceLaw1JulesDispatchTest` (4/4), `ProjectFlowServiceResetTest` (3/3), `ProjectFlowServiceDbrRopeTest` (4/4), `ProjectFlowIntegrationTest` (5/5): ядро сборки графа задач, DBR-верёвка и оркестрация потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `ProjectFactoryServiceTest` (4/4), `ProjectFactoryServiceFalsificationTest` (6/6), `GitHubProjectFactoryClientTest` (3/3), `LinearProjectFactoryClientTest` (4/4), `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5): детерминированный провижининг проектов с топологической изоляцией воркспейса (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `RequirementGroundingServiceTest` (6/6), `RequirementGroundingServiceFalsificationTest` (5/5): заземление требований на факты заказчика (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `MarketCorpusServiceTest` (19/19), `MarketCorpusServiceFalsificationTest` (6/6): рыночный корпус и онтология домена (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `MarketComplianceGateTest` (12/12), `MarketComplianceGateFalsificationTest` (5/5): гейт соответствия рыночным стандартам (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6): забор внешних измерений через системного носителя без искажения очередей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `OnboardingAuditServiceTest` (5/5), `OnboardingAuditServiceFalsificationTest` (4/4), `RepositoryStackAnalyzerTest` (6/6): аудит онбординга и анализ стека репозитория (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Секция II — Компиляция: требование становится задачами (140 тестов, 22 сьюта):**
       - `TechnicalLeadCompilerTest` (4/4), `TechnicalLeadCompilerFalsificationTest` (6/6), `TechnicalLeadCompilerIntegrationTest` (11/11), `WishlistCompileBudgetLaw9Test` (6/6): институциональный компилятор техлида и бюджет компиляции (`DZHON_SERL_01_INSTITUTIONAL_FACT` / D007, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FeatureServiceTest` (5/5), `FeatureServiceFalsificationTest` (6/6): управление фичами и потоками сущностей (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `EpistemicMetadataClassifierTest` (5/5), `EpistemicMetadataClassifierFalsificationTest` (5/5): эпистемическая классификация метаданных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `KanoClassTest` (5/5), `KanoClassFalsificationTest` (6/6): классификатор Кано как жесткий десигнатор ценности (`SOL_KRIPKE_01_RIGID_DESIGNATOR` / D001);
       - `EmsFlowStageTest` (5/5), `EmsFlowStageFalsificationTest` (6/6): строгие стадии потока EMS (`LYUDVIG_VITGENSHTEYN_01_CRITERIA_NOT_SYMPTOM` / D001);
       - `SelfFalsificationEpicMatcherTest` (6/6), `SelfFalsificationEpicMatcherFalsificationTest` (6/6): сличитель эпиков с защитой от самообмана (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `WishlistContentSimilarityMatcherTest` (8/8), `WishlistContentSimilarityMatcherFalsificationTest` (6/6), `WishlistSimilarityTest` (6/6), `WishlistSimilarityKripkeIntensionFalsificationTest` (5/5): семантическое сличение вишлиста с отсечением дубликатов (`GOTLOB_FREGE_01_SENSE_AND_REFERENCE` / D009);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistControllerIntegrationTest` (8/8): сервис и контроллер вишлиста со строгим деонтическим заслоном (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StrandedFinalizingSweepServiceTest` (8/8), `StrandedFinalizingSweepServiceFalsificationTest` (6/6), `StrandedFinalizingDynamicTimeoutFalsificationTest` (5/5): безопасная эвакуация застрявших притязаний без паразитного размножения (`UASLI_SALMON_01_CAUSAL_PROCESS` / D013, `RONALD_DVORKIN_01_HARD_CASES` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D001)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене требований (`RequirementGroundingServiceFalsificationTest`), нарушении соответствия рынку (`MarketComplianceGateFalsificationTest`), невалидном бюджете компиляции (`TechnicalLeadCompilerFalsificationTest`) или подмене идентичности застрявшего финализатора (`StrandedFinalizingSweepServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: `RequirementGroundingService`, `MarketCorpusService` и `FeatureService` обязаны сохранять строгую проверяемую родословную данных от сырых клиентских артефактов до графа задач.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет компиляции требований без валидного DoR, запрет провижининга без подтвержденного репозитория, строгая матрица прав переходов состояний вишлиста.
- **`DZHON_SERL_01_INSTITUTIONAL_FACT` [D007 Institutional fact / Searle]:**
  * Компилятор техлида чеканит задачи как институциональные факты фабрики: задача существует только при наличии оснований и строгого контракта.
- **`SOL_KRIPKE_01_RIGID_DESIGNATOR` [D001 Semantic drift / Kripke]:**
  * Жесткие десигнаторы Кано и стадий потока EMS: семантика стадий не может дрейфовать в зависимости от контекста выполнения.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер 44 тест-сьютов Секций I и II в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=ProjectFlowServiceTest,ProjectFlowServiceFalsificationTest,ProjectFlowServiceLaw1JulesDispatchTest,ProjectFlowServiceResetTest,ProjectFlowServiceDbrRopeTest,ProjectFlowIntegrationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectWorkspaceFactoryServiceFalsificationTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,MarketCorpusServiceTest,MarketCorpusServiceFalsificationTest,MarketComplianceGateTest,MarketComplianceGateFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,RepositoryStackAnalyzerTest,TechnicalLeadCompilerTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerIntegrationTest,WishlistCompileBudgetLaw9Test,FeatureServiceTest,FeatureServiceFalsificationTest,EpistemicMetadataClassifierTest,EpistemicMetadataClassifierFalsificationTest,KanoClassTest,KanoClassFalsificationTest,EmsFlowStageTest,EmsFlowStageFalsificationTest,SelfFalsificationEpicMatcherTest,SelfFalsificationEpicMatcherFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistContentSimilarityMatcherFalsificationTest,WishlistSimilarityTest,WishlistSimilarityKripkeIntensionFalsificationTest,WishlistServiceFalsificationTest,WishlistControllerIntegrationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedFinalizingDynamicTimeoutFalsificationTest
   ```
2. Подтвердить строго **305/305 green** (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(intake-and-compiler): complete Stage 4 batch verification (D008, D010, D006)`.
