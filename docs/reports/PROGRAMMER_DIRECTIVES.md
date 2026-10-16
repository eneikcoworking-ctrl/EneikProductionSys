# Пакетная директива L1/L3 -> L2: Секции III и IV — Отправка и Ревью/Гейт/Слияние

**Дата:** 2026-10-16 06:16 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции I и II («Приём требования и Компиляция») полностью заверены и закрыты инженером (коммит `457b800`, 305/305 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива открывает пакетный замер исполнительного и верификационного контуров фабрики — **Секция III («Отправка: задача уходит в работу»)** и **Секция IV («Ревью, гейт, слияние»)**:
   - **Контур III.Dispatch (`Отправка: задача уходит в работу: Jules, сессии, притязания, аренда, здоровье аккаунтов и узкие места`):**
     * Механизмы: `JulesDispatchService`, `JulesApiClient`, `SessionLifecycleService`, `PersistentWorkerSessionService`, `ClaimService`, `TaskClaimService`, `LeaseWatchdogService`, `AccountHealthService`, `BottleneckAwarePriorityService`, `BottleneckDetectionService`.
   - **Контур IV.ReviewGateMerge (`Ревью, гейт, слияние и GitHub-инварианты`):**
     * Механизмы: `AutoMergeService`, `GitHubPullRequestService`, `GitHubApiBudgetService`, `GithubAccessService`, `CodeChangeClassifier`, `GateOrchestrator`, `VerificationEvidenceGate`, `BaseQualityGate.*`, `BackendContractGate`, `DesignExcellenceGate`, `EpistemicLayerInvariantGate`, `BranchGarbageCollectorService`, `PrReviewPipelineService`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, клиентов, гейтов, пайплайнов и моделей (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты фальсификаторов обязаны воспроизводимо падать при подмене токенов, попытке автослияния без свидетельства гейта, обходе бюджета API или дублировании активных сессий Jules.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: изоляция внешних вызовов Jules API от локального жизненного цикла сессий, сохранение каузального следа PR и веток, неизменность доказательств проверки в `VerificationEvidenceGate`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: деонтические барьеры слияния неподтвержденных PR, запрет отправки задач с `TargetContext.UNDETERMINED`, исключающие основания отказа с объяснимой причиной.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Boundary / Varzi & Bratman]:**
  * Топология границ и монопольное владение: строгая привязка активных сессий к `taskId` и `accountId`, запрет гонок при захвате задач (`ClaimServiceRaceGuard`), гармонизация совместной активности воркеров.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued logic / Belnap]:**
  * 4-значная логика статусов истинности гейтов качества и контекстов задач: неизвестное (`undetermined`) никогда не интерпретируется как успех.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: физическое подтверждение состояния PR и веток на GitHub выше любого словесного отчета или промежуточного статуса в БД.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций III и IV в едином непрерывном запуске Docker Maven (все тесты контуров отправки, гейтов и слияния):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=JulesDispatchServiceTest,JulesDispatchServiceFalsificationTest,JulesDispatchServiceLaw4MergeEvidenceTest,JulesDispatchServiceTocDbrAdmissionTest,ProjectFlowServiceLaw1JulesDispatchTest,DispatchAttemptBudgetTest,DispatchRefusalObservabilityLaw8Law12Test,JulesRefusalKindsTest,JulesApiClientTest,JulesApiClientFalsificationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,PersistentWorkerSessionServiceTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,LeaseWatchdogServiceTest,AccountHealthServiceTest,BottleneckAwarePriorityServiceTest,BottleneckDetectionServiceTest,AutoMergeServiceTest,AutoMergeServiceFalsificationTest,AutoMergePokaYokeTest,AutoMergeLaw20CarrierExclusionTest,AutoMergeLaw20InvariantS4Test,GitHubPullRequestServiceTest,GitHubApiBudgetServiceTest,GithubAccessServiceTest,CodeChangeClassifierTest,GateOrchestratorTest,VerificationEvidenceGateTest,BaseQualityGateTest,BackendContractGateTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,EpistemicLayerInvariantGateTest,BranchGarbageCollectorServiceTest,PrReviewPipelineServiceTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции III и IV) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(dispatch-and-merge): complete Stage 4 batch verification (D008, D010, D006)`.
