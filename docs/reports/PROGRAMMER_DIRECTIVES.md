# Директивы и наряд для L2 (2026-10-23 23:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции I и II («Приём требования, онбординг, рынок, топология проектов» и «Компиляция: требование становится задачами») полностью заверены и закрыты инженером (коммит `ff83b698`, 308/308 green в 46 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:13 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий исполнительный и интеграционный контур фабрики — **Кластер Секций III и IV («Отправка: задача уходит в работу» и «Ревью, гейт, слияние: код становится частью продукта»)** целиком (**561 тест в едином непрерывном пакетном прогоне в Docker Maven [65 тест-сьютов]**):
     * **Группа 1 — Секция III: Отправка: задача уходит в работу (32 сьюта, 318 тестов):**
       - `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`, `JulesDispatchServiceLaw4MergeEvidenceTest`, `JulesDispatchServiceTocDbrAdmissionTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `DispatchAttemptBudgetTest`, `DispatchRefusalObservabilityLaw8Law12Test`, `JulesRefusalKindsTest`: диспетчеризация задач воркерам Jules, отсечение дублирующих сессий, строгая изоляция фабрики и продукта (Закон 2), бюджет попыток и субординация барабан-буфер-верёвка (DBR);
       - `JulesApiClientTest`, `JulesApiClientFalsificationTest`: транспорт к внешнему API Google Jules, безопасные таймауты, префлайт и классификация кодов отказов;
       - `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `PersistentWorkerSessionServiceTest`, `PersistentWorkerSessionServiceFalsificationTest`: жизненный цикл сессий воркеров, предотвращение фиктивных локальных отмен и сборка брошенных сессий;
       - `TaskClaimServiceTest`, `ClaimServiceFalsificationTest`, `ClaimServiceRaceGuardTest`, `ClaimRightsDutiesMatrixTest`, `ClaimResultStatusTruthTableFalsificationTest`, `TocPriorityClaimTest`, `ReviewAdmissionLaw16Test`: монопольное владение притязаниями на задачи (CAS-захват), защита от гонок воркеров, матрица прав и обязанностей доступа;
       - `LeaseWatchdogServiceTest`, `LeaseWatchdogServiceFalsificationTest`: регулярная жатва просроченных аренд задач, предотвращение зависания задач в статусе `claimed`;
       - `AccountHealthServiceTest`, `AccountHealthServiceLaw14Test`, `AccountHealthServiceFalsificationTest`, `AccountStatusConjunctionTest`, `AccountLifecycleInvariantTest`: мониторинг здоровья, конъюнкция доступности и реабилитация пула аккаунтов воркеров;
       - `BottleneckAwarePriorityServiceTest`, `BottleneckAwarePriorityServiceFalsificationTest`, `BottleneckDetectionServiceTest`, `BottleneckDetectionServiceFalsificationTest`: выявление узких мест и динамическая TOC-приоритизация очереди задач.
     * **Группа 2 — Секция IV: Ревью, гейт, слияние и GitHub-инварианты (33 сьюта, 243 теста):**
       - `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`, `AutoMergePokaYokeTest`, `AutoMergeLaw20InvariantS4Test`: слияние PR, Poka-Yoke Закона 20 (недопустимость слияния без зеленых гейтов качества);
       - `GitHubPullRequestServiceTest`, `GitHubPullRequestServiceFalsificationTest`, `GitHubApiBudgetServiceTest`, `GitHubApiBudgetServiceFalsificationTest`, `GitHubApiBudgetLaw13Test`, `GitHubApiBudgetSpendTest`, `GithubAccessServiceTest`, `GithubAccessServiceFalsificationTest`, `GithubAccessControllerTest`: интеграция с GitHub API, суточный бюджет запросов по Закону 13, авторизация и проверка токенов;
       - `CodeChangeClassifierTest`, `CodeChangeClassifierFalsificationTest`: классификация диффов PR и защита от несанкционированных мутаций инфраструктуры;
       - `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`: оркестрация каскада гейтов качества;
       - `VerificationEvidenceGateTest`, `VerificationEvidenceGateFalsificationTest`, `BaseQualityGateTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `EpistemicLayerInvariantGateTest`, `EpistemicLayerInvariantGateFalsificationTest`: строгие гейты качества задач (артефакты Playwright, контракты API, эпистемическая куайновская граница ядра, доказательства верификации);
       - `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`: сборщик брошенных веток и поддержание инварианта `N_active_branches(taskId) <= 1`;
       - `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`, `PrReviewApprovalLaw1Test`, `PrReviewEntityLaw20Test`: пайплайн ревью PR, расчет уровня риска и необратимость статуса слияния.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны диспетчеризации сессий (`JulesDispatchServiceFalsificationTest`), гонок за притязание задач (`ClaimServiceRaceGuardTest`), просрочки аренд (`LeaseWatchdogServiceFalsificationTest`), Poka-Yoke автослияния (`AutoMergePokaYokeTest`), гейтов качества (`GateOrchestratorFalsificationTest`, `VerificationEvidenceGateFalsificationTest`, `DesignExcellenceGateFalsificationTest`), сборки мусора веток (`BranchGarbageCollectorServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств жизненного цикла сессий воркеров и здоровья аккаунтов (`AccountHealthServiceTest`), лок уровней абстракции внешнего сетевого транспорта Google Jules и GitHub API (`JulesApiClientTest`, `GitHubPullRequestServiceTest`), сохранение свидетельств слияния без подмены понятий.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет отправки задач с неопределенным таргетом (Закон 2);
  * Деонтический запрет слияния PR без полного набора подтвержденных свидетельств гейтов (Закон 20).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership & D006 Boundary topology / Varzi]:**
  * Монопольное владение жизненным циклом Jules-сессий (`SessionLifecycleService`), исключительное владение захватом задач и состоянием клеймов (`TaskClaimService`), топологическая демаркация веток в Git (`BranchGarbageCollectorService`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка истинности статусов целей (`TargetContext`: `PRODUCT_CODEBASE`, `ORCHESTRATOR_SYSTEM`, `UNDETERMINED`), статусов клеймов (`ClaimResultStatusTruthTableFalsificationTest`) и вердиктов риска ревью.
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальное чтение фактического состояния GitHub PR, ёмкости аккаунтов и рантайм-контейнеров без подмены словесным нарративом.

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 65 тест-сьютов Кластера Секций III и IV внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=JulesDispatchServiceTest,JulesDispatchServiceFalsificationTest,JulesDispatchServiceLaw4MergeEvidenceTest,JulesDispatchServiceTocDbrAdmissionTest,ProjectFlowServiceLaw1JulesDispatchTest,DispatchAttemptBudgetTest,DispatchRefusalObservabilityLaw8Law12Test,JulesRefusalKindsTest,JulesApiClientTest,JulesApiClientFalsificationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,PersistentWorkerSessionServiceTest,PersistentWorkerSessionServiceFalsificationTest,TaskClaimServiceTest,ClaimServiceFalsificationTest,ClaimServiceRaceGuardTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,TocPriorityClaimTest,ReviewAdmissionLaw16Test,LeaseWatchdogServiceTest,LeaseWatchdogServiceFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,AccountStatusConjunctionTest,AccountLifecycleInvariantTest,BottleneckAwarePriorityServiceTest,BottleneckAwarePriorityServiceFalsificationTest,BottleneckDetectionServiceTest,BottleneckDetectionServiceFalsificationTest,AutoMergeServiceTest,AutoMergeServiceFalsificationTest,AutoMergePokaYokeTest,AutoMergeLaw20InvariantS4Test,GitHubPullRequestServiceTest,GitHubPullRequestServiceFalsificationTest,GitHubApiBudgetServiceTest,GitHubApiBudgetServiceFalsificationTest,GitHubApiBudgetLaw13Test,GitHubApiBudgetSpendTest,GithubAccessServiceTest,GithubAccessServiceFalsificationTest,GithubAccessControllerTest,CodeChangeClassifierTest,CodeChangeClassifierFalsificationTest,GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,PrReviewPipelineServiceTest,PrReviewPipelineServiceFalsificationTest,PrReviewApprovalLaw1Test,PrReviewEntityLaw20Test
   ```
2. **Критерий приёмки:** Ровно 561/561 green в 65 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (561/561 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(dispatch-and-merge): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
