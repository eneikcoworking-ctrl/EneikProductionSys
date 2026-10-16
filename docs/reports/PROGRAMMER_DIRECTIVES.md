# Пакетная директива L1/L3 -> L2: Секция IV — Ревью, гейт, слияние и GitHub-инварианты

**Дата:** 2026-10-16 03:05 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция III («Отправка: задача уходит в работу») успешно закрыта инженером (коммит `fd9f2e6`, 290/290 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий фундаментальный контур фабрики — **Секцию IV («Ревью, гейт, слияние и GitHub-инварианты»)**:
   - **Механизмы ядра и сервисы контура:**
     * `AutoMergeService`, `GitHubPullRequestService`, `GitHubApiBudgetService`, `GithubAccessService`, `CodeChangeClassifier`, `GateOrchestrator`, `BaseQualityGate`, `BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `EpistemicLayerInvariantGate`, `BranchGarbageCollectorService`, `PrReviewPipelineService`;
     * Сущности и модели: `PrReviewEntity`, `TaskGateLogEntity`, `TaskEntity`, `TaskConflictEntity`, `GithubAccessStatus`, `CodeChangeClassifier`;
   - **Заслоны верификации (240 тестов, 100% green):**
     * `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`, `AutoMergePokaYokeTest`, `AutoMergeLaw20InvariantS4Test`, `GitHubPullRequestServiceTest`, `GitHubPullRequestServiceFalsificationTest`, `GitHubApiBudgetServiceTest`, `GitHubApiBudgetServiceFalsificationTest`, `GitHubApiBudgetSpendTest`, `GitHubApiBudgetLaw13Test`, `GithubAccessServiceTest`, `GithubAccessServiceFalsificationTest`, `GithubAccessControllerTest`, `CodeChangeClassifierTest`, `CodeChangeClassifierFalsificationTest`, `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`, `BaseQualityGateTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `VerificationEvidenceGateTest`, `VerificationEvidenceGateFalsificationTest`, `EpistemicLayerInvariantGateTest`, `EpistemicLayerInvariantGateFalsificationTest`, `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`, `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`, `PrReviewApprovalLaw1Test`, `PrReviewEntityLaw20Test`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, диспетчеров, контроллеров и гейтов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D002, D004, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green, Popper & Williamson]:**
  * Попперовская фальсификация гейтов и слияния: клиентский PR может быть слит только при наличии независимых свидетельств. Никакой самоотчет агента не подменяет байты GitHub. Пустые множества проверок (`allMatch` по пустому списку) категорически отвергаются (`applicableChecksByStage > 0`). Периферийная задача, модифицирующая ядро фабрики (`src/main/resources/db/migration/*` или безопасность), обязана фальсифицироваться и отвергаться (`EpistemicLayerInvariantGateTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: гейты (`BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`) инспектируют реальные байты PR diff, head-ref и файлы репозитория, а не субъективные самоотчеты агентов (payload claims); изоляция бюджетов GitHub API по отпечаткам токенов; нерушимая связь задачи, PR и ревью в `AutoMergeService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Исключающие основания Раза в рантайме: `AutoMergeService` требует предварительной авторизации от `OperationalPolicyService` (`authorize(projectId, OperationalAction.MERGE_PR)`); при запрете политики (`!decision.allowed()`) автослияние прерывается без единого сетевого вызова к GitHub с фиксацией `policy_denied`; `GithubAccessService` блокирует неавторизованные обращения; `GitHubApiBudgetService` останавливает вызовы при исчерпании лимитов токена.
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Эмпирическая истина внешнего мира: физическое состояние GitHub API имеет абсолютный приоритет над догадками бэкенда. Если PR закрыт без слияния во внешнем мире (`closed_unmerged`), бесплодные попытки слияния навсегда прекращаются; если PR уже слит (`alreadyMergedExternally = true`), сервис идемпотентно фиксирует факт слияния и завершает задачу, исключая ошибочные 405-вызовы `PUT /merge`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `DZHON_OSTIN_01_SPEECH_ACT_FORCE` / `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D012 Belnap / D002 Austin & Ryle]:**
  * 4-значная решетка истинности статусов проверок гейта; категориальная демаркация продуктового кода от служебных артефактов (`CodeChangeClassifier` отделяет код клиента от артефактов `.eneik/*`, runner-скриптов и записей фабрики).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006, Varzi]:**
  * Мереологическое владение ветками и сессиями: безопасный сборщик веток (`BranchGarbageCollectorService`) удаляет только те ветки и PR, владение которыми доказано токеном сессии или эксплицитными свидетельствами орфанности.
- **`UARD_KUAIN_01_ONTOLOGICAL_RELATIVITY` [D003 Contract drift, Quine]:**
  * Защита онтологического ядра системы от мутаций со стороны периферийных компонентов.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции IV в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AutoMergeServiceTest,AutoMergeServiceFalsificationTest,AutoMergePokaYokeTest,AutoMergeLaw20InvariantS4Test,GitHubPullRequestServiceTest,GitHubPullRequestServiceFalsificationTest,GitHubApiBudgetServiceTest,GitHubApiBudgetServiceFalsificationTest,GitHubApiBudgetSpendTest,GitHubApiBudgetLaw13Test,GithubAccessServiceTest,GithubAccessServiceFalsificationTest,GithubAccessControllerTest,CodeChangeClassifierTest,CodeChangeClassifierFalsificationTest,GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,PrReviewPipelineServiceTest,PrReviewPipelineServiceFalsificationTest,PrReviewApprovalLaw1Test,PrReviewEntityLaw20Test
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секции IV в `docs/FACTORY_MECHANISMS.md`, подтвердить актуальность блока `### Итог Секции IV`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md` (внести Секцию IV в `## Дальше`).
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(automerge-and-gates): complete Section IV batch verification (D008, D010, D006)`.
