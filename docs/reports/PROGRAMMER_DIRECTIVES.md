# Пакетная директива L1/L3 -> L2: Секции I и II — Приём требования и Компиляция (требование становится задачами)

**Дата:** 2026-10-16 06:10 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XVI и XXIX («Реестр 59 предписаний L2 и Цех дизайна») полностью заверены и закрыты инженером (коммит `48c79f6`, 272/272 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива открывает пакетный замер ключевого входного кластера жизненного цикла фабрики — **Секция I («Приём требования: рыночный корпус, уставный гейт, провижининг проектов и онбординг»)** и **Секция II («Компиляция: требование становится задачами, декомпозиция, сличители дубликатов и сборка вишлиста»)**:
   - **Контур I.Intake (`Приём требования и провижининг проектов`):**
     * Механизмы: `ProjectFlowService`, `ProjectFactoryService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient`, `ProjectWorkspaceFactoryService`, `RequirementGroundingService`, `MarketCorpusService`, `MarketComplianceGate`, `MarketResearchService`, `OnboardingAuditService`, `RepositoryStackAnalyzer`, `StackProfile`, `InspectionStatus`, `OnboardingAuditFindingRepository`.
   - **Контур II.Compilation (`Компиляция: требование становится задачами`):**
     * Механизмы: `TechnicalLeadCompiler`, `FeatureService`, `EpistemicMetadataClassifier`, `KanoClass`, `EmsFlowStage`, `SelfFalsificationEpicMatcher`, `WishlistContentSimilarityMatcher`, `WishlistSimilarity`, `WishlistService`, `StrandedFinalizingSweepService`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, компилятора, гейтов, контроллеров и моделей (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D012, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green / Popper & Williamson]:**
  * Попперовская фальсификация: тесты фальсификаторов обязаны воспроизводимо падать при подстановке фиктивного URL репозитория, при пропуске невалидного DoR, при нарушении проектной изоляции или дублировании компиляционных задач.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: изоляция выборок по ID проекта, устранение N+1 запросов, сохранение родословной требований от сырого рыночного сигнала до гранулированных задач.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: деонтические барьеры компиляции невалидного DoR, топологическая изоляция каталогов и репозиториев воркспейса.
- **`GOTLOB_FREGE_01_SENSE_REFERENCE_ORACLE` & `SOL_KRIPKE_01_RIGID_DESIGNATOR_CORE` [D009 Reference opacity / Frege & Kripke]:**
  * Оракул тождества смысла и десигнаторы: устойчивая дедупликация эпиков и вишлиста, исключающая размножение задач-дубликатов при сохранении семантического ядра.
- **`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` & `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D007 Gilbert & Searle / D002 Ryle]:**
  * Институциональная регистрация требований и защита от категориальной ошибки: демаркация статусов готовности фабрики от внешнего репозитория заказчика.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций I и II в едином непрерывном запуске Docker Maven (все тесты контуров приёма и компиляции):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ProjectFlowServiceTest,ProjectFlowServiceFalsificationTest,ProjectFlowServiceLaw1JulesDispatchTest,ProjectFlowServiceResetTest,ProjectFlowServiceDbrRopeTest,ProjectFlowIntegrationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectWorkspaceFactoryServiceFalsificationTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,MarketCorpusServiceTest,MarketCorpusServiceFalsificationTest,MarketComplianceGateTest,MarketComplianceGateFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,RepositoryStackAnalyzerTest,TechnicalLeadCompilerTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,FeatureServiceTest,FeatureServiceFalsificationTest,EpistemicMetadataClassifierTest,EpistemicMetadataClassifierFalsificationTest,KanoClassTest,KanoClassFalsificationTest,EmsFlowStageTest,EmsFlowStageFalsificationTest,SelfFalsificationEpicMatcherTest,SelfFalsificationEpicMatcherFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistContentSimilarityMatcherFalsificationTest,WishlistSimilarityTest,WishlistSimilarityKripkeIntensionFalsificationTest,WishlistServiceFalsificationTest,WishlistControllerIntegrationTest,WishlistCompileBudgetLaw9Test,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedFinalizingDynamicTimeoutFalsificationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции I и II) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(intake-and-compiler): complete Stage 4 batch verification (D008, D010, D006)`.
