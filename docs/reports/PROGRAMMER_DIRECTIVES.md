# Директивы и наряд для L2 (2026-10-23 22:33 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секции I и II («Приём требования» и «Компиляция: требование становится задачами») полностью заверены и закрыты инженером (коммит `1e27d494`, 308/308 green в 46 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:01 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий исполнительный и интеграционный контур фабрики — **Кластер Секций III и IV («Отправка: задача уходит в работу» и «Ревью, гейт, слияние»)** целиком (**450+ тестов в едином непрерывном пакетном прогоне в Docker Maven [46 тест-сьютов]**):
     * **Группа 1 — Секция III: Отправка: задача уходит в работу (20 сьютов, 258 тестов):**
       - `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`, `JulesDispatchServiceLaw4MergeEvidenceTest`, `JulesDispatchServiceTocDbrAdmissionTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `DispatchAttemptBudgetTest`, `DispatchRefusalObservabilityLaw8Law12Test`, `JulesRefusalKindsTest`: ядро диспетчеризации задач, отсечение дублирующих сессий, изоляция фабрики и продукта (Закон 2), бюджет попыток и субординация барабан-буфер-верёвка (DBR);
       - `JulesApiClientTest`, `JulesApiClientFalsificationTest`: транспорт к внешнему API Google Jules, префлайт источников, безопасные таймауты и классификация кодов отказов;
       - `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `PersistentWorkerSessionServiceTest`, `PersistentWorkerSessionServiceFalsificationTest`: жизненный цикл сессий воркеров, предотвращение фиктивных локальных отмен, очистка отработанных сессий;
       - `TaskClaimServiceTest`, `ClaimServiceTest`, `ClaimServiceFalsificationTest`, `ClaimServiceRaceGuardTest`, `ClaimRightsDutiesMatrixTest`, `ClaimResultStatusTruthTableFalsificationTest`: монопольное владение притязаниями на задачи (CAS-захват), защита от гонок воркеров;
       - `LeaseWatchdogServiceTest`, `LeaseWatchdogServiceFalsificationTest`: регулярная жатва просроченных аренд задач, предотвращение застревания в статусе `claimed`;
       - `AccountHealthServiceTest`, `AccountHealthServiceFalsificationTest`, `AccountHealthServiceLaw14Test`, `AccountStatusConjunctionTest`, `AccountLifecycleInvariantTest`: мониторинг здоровья и реабилитация пула аккаунтов воркеров;
       - `BottleneckAwarePriorityServiceTest`, `BottleneckAwarePriorityServiceFalsificationTest`, `BottleneckDetectionServiceTest`, `BottleneckDetectionServiceFalsificationTest`: выявление узких мест и динамическая приоритизация очереди задач.
     * **Группа 2 — Секция IV: Ревью, гейт, слияние (26 сьютов, 192 теста):**
       - `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`, `AutoMergePokaYokeTest`, `AutoMergeLaw20StatusPrecedenceTest`: слияние PR, Poka-Yoke Закона 20 (недопустимость слияния без зеленых гейтов качества);
       - `GitHubPullRequestServiceTest`, `GitHubPullRequestServiceFalsificationTest`, `GitHubApiBudgetServiceTest`, `GitHubApiBudgetServiceFalsificationTest`, `GitHubApiBudgetLaw13Test`, `GitHubApiBudgetSpendTest`, `GithubAccessServiceTest`, `GithubAccessServiceFalsificationTest`, `CodeChangeClassifierTest`, `CodeChangeClassifierFalsificationTest`: работа с GitHub PR, бюджетирование квот API, классификация изменений кода;
       - `GateOrchestratorTest`, `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`: оркестрация каскада проверок качества;
       - `VerificationEvidenceGateTest`, `VerificationEvidenceGateFalsificationTest`, `BaseQualityGateTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `EpistemicLayerInvariantGateTest`, `EpistemicLayerInvariantGateFalsificationTest`: многоуровневый заслон свидетельств верификации (базовое качество, контракт бэкенда, дизайн, эпистемические инварианты);
       - `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`: безопасная очистка смерженных и заброшенных веток;
       - `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`, `PrReviewApprovalLaw1Test`, `PrReviewEntityLaw20Test`, `ReviewAdmissionLaw16Test`: пайплайн ревью PR, демаркация вердиктов и деонтическая защита свидетельств сдачи.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны диспетчеризации сессий (`JulesDispatchServiceFalsificationTest`), гонок за притязание задач (`ClaimServiceRaceGuardTest`), просрочки аренд (`LeaseWatchdogServiceFalsificationTest`), Poka-Yoke автослияния (`AutoMergePokaYokeTest`), гейтов качества (`GateOrchestratorFalsificationTest`, `VerificationEvidenceGateFalsificationTest`), сборки мусора веток (`BranchGarbageCollectorServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств жизненного цикла сессий воркеров и здоровья аккаунтов (`AccountHealthServiceTest`), лок уровней абстракции внешнего сетевого транспорта Google Jules и GitHub API (`JulesApiClientTest`, `GitHubPullRequestServiceTest`), сохранение свидетельств слияния без подмены понятий.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет отправки задач с неопределенным таргетом (Закон 2);
  * Деонтический запрет слияния PR без полного набора подтвержденных свидетельств гейтов (Закон 20).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership & D006 Boundary topology / Varzi]:**
  * Монопольное владение жизненным циклом Jules-сессий (`SessionLifecycleService`), исключительное владение захватом задач и состоянием клеймов (`ClaimService`), топологическая демаркация веток в Git.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка истинности статусов целей (`TargetContext`: `PRODUCT_CODEBASE`, `ORCHESTRATOR_SYSTEM`, `UNDETERMINED`) и результатов ревью PR.
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальное чтение фактического состояния GitHub PR, емкости аккаунтов и рантайм-контейнеров без подмены словесным нарративом.

---

## 3. Регламент исполнения инженера L2

1. Запустить единый непрерывный пакетный прогон всех 46 тест-сьютов Секций III и IV внутри контейнера Docker Maven (`docker run --rm -m 2g ... mvn test -Dtest=...`).
2. Строго соблюсти инвариант Ступени 4: 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Зафиксировать статус идеальной формы (*Живое:* 450+/450+ green, ideal) в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`.
5. Зафиксировать результаты семантическим коммитом `feat(dispatch-and-merge): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводить по директиве оператора.
