# Директивы и наряд для L2 (2026-10-23 22:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна») полностью заверены и закрыты инженером (коммит `ef57f399`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:35 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает фундаментальное начало производственного контура фабрики — **Кластер Секций I и II («Приём требования и Компиляция: требование становится задачами»)** целиком (**305 тестов в едином непрерывном пакетном прогоне в Docker Maven [44 тест-сьюта]**):
     * **Группа 1 — Секция I: Приём требования, онбординг, рынок и топология проектов (22 сьюта, 165 тестов):**
       - `ProjectFlowServiceTest`, `ProjectFlowServiceFalsificationTest`, `ProjectFlowServiceResetTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `ProjectFlowServiceDbrRopeTest`, `ProjectFlowIntegrationTest`: непрерывный поток проектов, субординация верёвки DBR и защита от застревания в финальных состояниях;
       - `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`: онтологический заслон создания репозиториев (адрес существует только после GitHub-свидетельства), топологическая изоляция рабочих директорий;
       - `RequirementGroundingServiceTest`, `RequirementGroundingServiceFalsificationTest`: семантическое приземление требований и защита от абстрактных галлюцинаций;
       - `MarketCorpusServiceTest`, `MarketCorpusServiceFalsificationTest`, `MarketComplianceGateTest`, `MarketComplianceGateFalsificationTest`, `MarketResearchServiceTest`, `MarketResearchServiceFalsificationTest`: соответствие рыночному корпусу и уставный гейт соответствия;
       - `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`: аудит онбординга репозитория заказчика;
       - `RepositoryStackAnalyzerTest`: категоризация ошибок чужого стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002).
     * **Группа 2 — Секция II: Компиляция: требование становится задачами (22 сьюта, 140 тестов):**
       - `TechnicalLeadCompilerTest`, `TechnicalLeadCompilerIntegrationTest`, `TechnicalLeadCompilerFalsificationTest`, `CompilerTaskIdentityTest`: детерминированная компиляция требований в атомарные задачи, контентный ключ задачи sha256 (`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` / D010);
       - `FeatureServiceTest`, `EpistemicMetadataClassifierTest`: классификация эпистемических метаданных и поддержка трассируемости фич;
       - `KanoClassTest`, `KanoClassFalsificationTest`: модель Кано (Must-have, Performance, Delighter, Reverse) с фальсификацией ложных дефолтов;
       - `EmsFlowStageTest`, `EmsFlowStageFalsificationTest`: стадии графа EMS и лок абстракций потока;
       - `SelfFalsificationEpicMatcherTest`, `SelfFalsificationEpicMatcherFalsificationTest`: связывание задач с эпиками под заслоном самофальсификации;
       - `WishlistContentSimilarityMatcherTest`, `WishlistContentSimilarityMatcherFalsificationTest`, `WishlistSimilarityTest`, `WishlistSimilarityKripkeIntensionFalsificationTest`: семантическая дедупликация требований, устойчивость к синонимии;
       - `WishlistServiceTest`, `WishlistServiceFalsificationTest`, `WishlistControllerIntegrationTest`, `WishlistCompileBudgetLaw9Test`: управление жизненным циклом требований, лимит бюджета по Закону 9;
       - `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `StrandedFinalizingDynamicTimeoutFalsificationTest`: CAS-свип зависших в финализации вишлистов с динамическим таймаутом без держателя.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D007, D002, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны приземления требований (`RequirementGroundingServiceFalsificationTest`), соответствия рыночному корпусу (`MarketComplianceGateFalsificationTest`), полевых исследований рынка (`MarketResearchServiceFalsificationTest`), компилятора (`TechnicalLeadCompilerFalsificationTest`), классификации Кано (`KanoClassFalsificationTest`), сопоставления эпиков (`SelfFalsificationEpicMatcherFalsificationTest`), схожести вишлистов (`WishlistContentSimilarityMatcherFalsificationTest`), динамических таймаутов зависших финализаций (`StrandedFinalizingDynamicTimeoutFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств компиляции задач (`TechnicalLeadCompilerTest`), нерушимость идентичности скомпилированных задач по sha256 хэшу и контент-ключу (`CompilerTaskIdentityTest`), лок уровней абстракции декомпозиции и стадий EMS (`EmsFlowStageTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет загрязнения продуктового бэклога внутренними пространствами фабрики (Закон 26);
  * Деонтический запрет повторной декомпозиции уже скомпилированных вишлистов и превышения бюджета по Закону 9 (`WishlistCompileBudgetLaw9Test`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership & D006 Boundary topology / Varzi]:**
  * Монопольное владение стадиями компиляции и финализации, CAS-свип зависших вишлистов без держателя (`StrandedFinalizingSweepServiceTest`).
- **`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTRATION` [D007 Fabricated evidence / Searle]:**
  * Регистрация институционального факта завершения компиляции строго при наличии верифицированных эпиков и задач, а не словесного нарратива.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Категориальная демаркация ошибок инфраструктуры от ошибок спецификации требований заказчика (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырёхзначная решётка истинности статусов готовности требований и вишлистов (`Neither`, `Both`, `True`, `False`).

---

## 3. Регламент исполнения инженера L2

1. Запустить единый непрерывный пакетный прогон всех 44 тест-сьютов Секций I и II внутри контейнера Docker Maven (`docker run --rm -m 2g ... mvn test -Dtest=...`).
2. Строго соблюсти инвариант Ступени 4: 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Зафиксировать статус идеальной формы (*Живое:* 305/305 green, ideal) в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`.
5. Зафиксировать результаты семантическим коммитом `feat(intake-and-compiler): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводить по директиве оператора.
