# Директивы и наряд для L2 (2026-10-16 12:50 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции I и II («Приём требования и Компиляция») успешно заверены инженером (коммит `28d3f00`, 305/305 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Кластера Секций III и IV («Отправка и Ревью/Слияние: задача уходит в работу, AutoMerge, гейты качества и пайплайн ревью»)** целиком (48 тест-сьютов, 394 теста в едином непрерывном пакетном прогоне Docker Maven):
     * **Группа 1: Секция III — Отправка: задача уходит в работу (20 сьютов):**
       - `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`, `JulesDispatchServiceLaw4MergeEvidenceTest`, `JulesDispatchServiceTocDbrAdmissionTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `DispatchAttemptBudgetTest`, `DispatchRefusalObservabilityLaw8Law12Test`, `JulesRefusalKindsTest`: ядро диспетчеризации, бюджет попыток и наблюдаемость отказов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `JulesApiClientTest`, `JulesApiClientFalsificationTest`: клиент к внешнему API Google Jules с деонтическим запретом сетевых вызовов при выключенном ключе (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `PersistentWorkerSessionServiceTest`: жизненный цикл сессий воркеров и топологические границы (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `MARGRET_GILBERT_01_PLURAL_SUBJECT` / D004);
       - `TaskClaimServiceTest`, `ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`, `LeaseWatchdogServiceTest`: захват задач, защита от гонок и лизинг исполнения (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest`, `BottleneckAwarePriorityServiceTest`, `BottleneckDetectionServiceTest`: здоровье аккаунтов и приоритизация узких мест (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 2: Секция IV — Ревью, гейт, слияние и GitHub-инварианты (28 сьютов):**
       - `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`, `AutoMergePokaYokeTest`, `AutoMergeLaw20CarrierExclusionTest`, `AutoMergeLaw20InvariantS4Test`: автослияние со строгим соблюдением инварианта неизменяемости Закона 20 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GitHubPullRequestServiceTest`, `GitHubApiBudgetServiceTest`, `GithubAccessServiceTest`, `CodeChangeClassifierTest`: взаимодействие с GitHub API, бюджетирование квот и классификация изменений (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `GateOrchestratorTest`, `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`, `VerificationEvidenceGateTest`, `VerificationEvidenceGateFalsificationTest`, `BaseQualityGateTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `EpistemicLayerInvariantGateTest`, `EpistemicLayerInvariantGateFalsificationTest`: полный каскад гейтов качества с фальсификационными заслонами против фиктивных подтверждений (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`: безопасная очистка брошенных веток по Закону 20 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013);
       - `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`, `PrReviewApprovalLaw1Test`, `PrReviewEntityLaw20Test`: пайплайн ревью PR с принудительным форсированием высокого риска для критических путей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене условий гейтов (`BaseQualityGateFalsificationTest`, `VerificationEvidenceGateFalsificationTest`), попытке автослияния без аппрува (`AutoMergeServiceFalsificationTest`) или искажении контекста отказа (`JulesApiClientFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: строгий учет попыток диспетчеризации, сохранение каузального следа HTTP-ответов и бюджетов API без потери контекста.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет отправки сессий без валидного таргета, запрет сетевых вызовов при выключенной интеграции, безусловный форсинг уровня риска `high` при затрагивании критических модулей.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Mereological collapse / D006 Boundary / Varzi]:**
  * Мереологический инвариант владения ветками и задачами: $N_{\text{active\_branches}}(taskId) \le 1$, строгая топологическая изоляция сессий.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для вердиктов гейтов и статусов готовности.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Физическая телеметрия состояния PR и проверок CI первична по отношению к любым нарративам агента.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех тест-сьютов Секций III и IV в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=JulesDispatchServiceTest,JulesDispatchServiceFalsificationTest,JulesDispatchServiceLaw4MergeEvidenceTest,JulesDispatchServiceTocDbrAdmissionTest,ProjectFlowServiceLaw1JulesDispatchTest,DispatchAttemptBudgetTest,DispatchRefusalObservabilityLaw8Law12Test,JulesRefusalKindsTest,JulesApiClientTest,JulesApiClientFalsificationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,PersistentWorkerSessionServiceTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,LeaseWatchdogServiceTest,AccountHealthServiceTest,BottleneckAwarePriorityServiceTest,BottleneckDetectionServiceTest,AutoMergeServiceTest,AutoMergeServiceFalsificationTest,AutoMergePokaYokeTest,AutoMergeLaw20CarrierExclusionTest,AutoMergeLaw20InvariantS4Test,GitHubPullRequestServiceTest,GitHubApiBudgetServiceTest,GithubAccessServiceTest,CodeChangeClassifierTest,GateOrchestratorTest,GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,PrReviewPipelineServiceTest,PrReviewPipelineServiceFalsificationTest,PrReviewApprovalLaw1Test,PrReviewEntityLaw20Test
   ```
2. Подтвердить строго **394/394 green** (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(dispatch-and-merge): complete Stage 4 batch verification (D008, D010, D006)`.
