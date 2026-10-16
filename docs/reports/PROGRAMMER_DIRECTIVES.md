# Пакетная директива L1/L3 -> L2: Секции I и II — Приём требования и Компиляция

**Дата:** 2026-10-16 02:40 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XVI и XXIX («Реестр предписаний L2 и Цех дизайна») успешно заверены инженером (коммит `ebb1c09`, 272/272 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает ключевой фундаментальный кластер жизненного цикла фабрики — **Секцию I («Приём требования: рыночный корпус, уставный гейт, провижининг проектов и онбординг»)** и **Секцию II («Компиляция: требование становится задачами, декомпозиция, сличители дубликатов и сборка вишлиста»)**:
   - **Контур I.Intake (`Приём требования и провижининг проектов`):**
     * Механизмы: `ProjectFlowService`, `ProjectFactoryService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient`, `ProjectWorkspaceFactoryService`, `RequirementGroundingService`, `MarketCorpusService`, `MarketComplianceGate`, `MarketResearchService`, `MarketResearchController`, `OnboardingAuditService`, `RepositoryStackAnalyzer`, `StackProfile`, `InspectionStatus`, `OnboardingAuditFindingRepository`;
     * Инварианты: приоритет целого над частями (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP`), каузальная цепочка надёжности данных и ликвидация N+1 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`), запрет фиктивных репозиториев (phantom URLs, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE`), топология границ файловой системы и проектных пространств (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`), защита от категориальной ошибки фабричного доступа против качества кода клиента (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN`);
     * Заслоны: `ProjectFlowServiceTest`, `ProjectFlowServiceFalsificationTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `ProjectFlowServiceResetTest`, `ProjectFlowServiceDbrRopeTest`, `ProjectFlowIntegrationTest`, `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`, `ProjectWorkspaceFactoryServiceTest`, `RequirementGroundingServiceTest`, `MarketCorpusServiceTest`, `MarketComplianceGateTest`, `MarketResearchServiceTest`, `MarketResearchControllerTest`, `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`, `RepositoryStackAnalyzerTest`.
   - **Контур II.Compilation (`Компиляция: требование становится задачами`):**
     * Механизмы: `TechnicalLeadCompiler`, `FeatureService`, `EpistemicMetadataClassifier`, `KanoClass`, `EmsFlowStage`, `SelfFalsificationEpicMatcher`, `WishlistContentSimilarityMatcher`, `WishlistSimilarity`, `WishlistService`, `StrandedFinalizingSweepService`;
     * Инварианты: институциональный факт и защита от тихого размножения задач (`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER`), оракул подстановок и детерминизм ключей контента (`GOTLOB_FREGE_01_SENSE_REFERENCE_ORACLE`, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE`), жесткие десигнаторы эпиков и признаков (`SOL_KRIPKE_01_RIGID_DESIGNATOR_CORE`), запрет компиляции невалидного DoR (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`), лок уровня абстракции Голдмана (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`), принципиальная целостность и CAS-атомарность освобождения зависших притязаний (`RONALD_DVORKIN_04_PRINCIPLED_INTEGRITY`);
     * Заслоны: `TechnicalLeadCompilerTest`, `TechnicalLeadCompilerIntegrationTest`, `TechnicalLeadCompilerFalsificationTest`, `TechnicalLeadCompilerDbrRopeTest`, `FeatureServiceTest`, `EpistemicMetadataClassifierTest`, `KanoClassTest`, `EmsFlowStageTest`, `SelfFalsificationEpicMatcherTest`, `WishlistContentSimilarityMatcherTest`, `WishlistSimilarityTest`, `WishlistServiceTest`, `WishlistServiceFalsificationTest`, `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `StrandedFinalizingDynamicTimeoutFalsificationTest`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, компилятора, контроллеров и моделей (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D012, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green, Popper & Williamson]:**
  * Попперовская фальсификация: тесты обязаны падать при попытке подстановки фиктивного URL репозитория, при пропуске невалидного DoR, при нарушении проектной изоляции или дублировании компиляционных задач.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная цепочка: изоляция выборок по ID проекта, полное устранение N+1 запросов, детерминированная сортировка каталогов оператора.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Деонтический запрет компиляции заявок со статусом DoR `undetermined`/`waste`, запрет сетевых вызовов при приёме в обход провижининга.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006, Shaffer & Varzi]:**
  * Мереологический приоритет целого над частями: границы владения состоянием задач и проектов; изоляция файловой системы в пределах `workspaceRoot`.
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` & `GOTLOB_FREGE_01_SENSE_REFERENCE_ORACLE` [D009 Phantom substitution, Belnap & Frege]:**
  * Запрет фантомных подстановок: при сбое провижининга в GitHub фабрика строго сохраняет `null` и не подменяет URL фиктивной строкой.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Категориальное разделение: ошибка авторизации или сетевой недоступности фабрики никогда не записывается как дефект репозитория заказчика.
- **`RONALD_DVORKIN_04_PRINCIPLED_INTEGRITY` [D012 Principled integrity, Dworkin]:**
  * Освобождение зависших притязаний вишлиста через атомарный CAS, исключающий гонки с живыми воркерами.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секций I и II в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ProjectFlowServiceTest,ProjectFlowServiceFalsificationTest,ProjectFlowServiceLaw1JulesDispatchTest,ProjectFlowServiceResetTest,ProjectFlowServiceDbrRopeTest,ProjectFlowIntegrationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectWorkspaceFactoryServiceTest,RequirementGroundingServiceTest,MarketCorpusServiceTest,MarketComplianceGateTest,MarketResearchServiceTest,MarketResearchControllerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,RepositoryStackAnalyzerTest,TechnicalLeadCompilerTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerDbrRopeTest,FeatureServiceTest,EpistemicMetadataClassifierTest,KanoClassTest,EmsFlowStageTest,SelfFalsificationEpicMatcherTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,WishlistServiceTest,WishlistServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedFinalizingDynamicTimeoutFalsificationTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секций I и II в `docs/FACTORY_MECHANISMS.md`, внести/актуализировать блоки `### Итог Секции I`, `### Итог Секции II` и `### Итог кластера Секций I и II` со сводкой зелёных тестов, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(intake-and-compiler): complete Section I & II batch verification (D008, D010, D006)`.
