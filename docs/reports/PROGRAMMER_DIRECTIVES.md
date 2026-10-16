# Пакетная директива L1/L3 -> L2: Ступень 4 — Заслоны качества задач, решётка вердиктов и цепь результатов фабрики проектов

**Дата:** 2026-10-16 02:08 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XXIX («Цех дизайна») успешно закрыта инженером (коммит `7b723e5`, 113/113 green в едином Docker Maven прогоне, 0 строк изменений кода). Настоящая директива охватывает целиком следующий стратегический контур фабрики — **финальный кластер Ступени 4 (строгие семейства «Task quality gates and verdict surfaces» и «Project factory provisioning result chain»), а также каноническое закрытие Секции VII («Lean, ТОС, Шесть сигм»)**:
   - **Контур IV.Gate (`Task Quality Gates & Gate Orchestrator`):**
     * Механизмы: `GateOrchestrator`, `GateCheck`, `GateStage`, `GateResult`, `BaseQualityGate`, `BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `EpistemicLayerInvariantGate`, `QualityGateController`;
     * Инварианты: пустой список применимых проверок никогда не признается пройденным гейтом (`applicableChecksByStage`), знаменатель формируется per-stage, реальные PR/file evidence превалируют над декларацией в payload, делегирование `/api/quality-gate/defect-rate` в `SixSigmaAuditService` без вызовов `findAll()`;
     * Заслоны: `GateOrchestratorIntegrationTest`, `GateOrchestratorFalsificationTest`, `BaseQualityGateTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`, `VerificationEvidenceGateTest`, `VerificationEvidenceGateFalsificationTest`, `EpistemicLayerInvariantGateTest`, `EpistemicLayerInvariantGateFalsificationTest`, `QualityGateControllerTest`.
   - **Контур IV.Verdict (`Verdict Surfaces & Lattice`):**
     * Механизмы: `VerdictGate`, `VerdictController`, `VerdictReconciliation`, `Verdict`, `Judgement`, `AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer`;
     * Инварианты: монотонность решётки готовности (гейт может только ужесточить суждение о готовности, но не ослабить его), трёхзначная логика Белнапа (`undetermined`), отказ при пустой решётке или отключённом флаге без ложных разрешений;
     * Заслоны: `VerdictGateTest`, `VerdictReconciliationTest`, `VerdictLayerFalsificationTest`.
   - **Контур I.Factory (`Project Factory Provisioning Result Chain`):**
     * Механизмы: `ProjectWorkspaceFactoryService`, `WorkspaceArtifacts`, `WorkspaceProvisioningResult`, `GitHubProjectFactoryClient`, `GitHubProvisioningResult`, `CollaboratorProvisioningResult`, `LinearProjectFactoryClient`, `LinearProvisioningResult`, `ProjectFactoryService`, `ProjectFactoryResult`;
     * Инварианты: адрес репозитория появляется только из физического GitHub-свидетельства (201 create / 200 GET), изоляция путей workspace (`boundaryTopologyRejectsPathTraversalOutsideRoot`), сохранение файлов существующего репозитория при brownfield, детерминированный отчёт `factoryReport` без маскировки отказов;
     * Заслоны: `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`.
   - **Контур VII.TOC (`Итог Секции VII: Lean, ТОС, Шесть сигм`):**
     * Механизмы: `ProcessControlService`, `ConstraintIdentificationService`, `BottleneckDetectionService`, `LaunchabilityConstraintService`, `TocSubordinationLever`, `FlowMetricsService`, `TaskWaitTimeService`, `SixSigmaAuditService`, `EmsMetricsService`, `SystemProgressTracker`, `AiHealthTracker`, `RiskLevelCalculator`;
     * Заслоны: подтверждение 119/119 green в едином прогоне и внесение канонического резюме `### Итог Секции VII` в `docs/FACTORY_MECHANISMS.md`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов и контроллеров (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D011, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper & Tarski]:**
  * Попперовская фальсификация ложноположительных прохождений гейтов качества, фантомных URL репозиториев и фиктивной готовности поставки: тест обязан краснеть при отсутствии реальных свидетельств в PR diff и файлах.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: проверки спецификаций задач (`TASK_SPEC`) никогда не смешиваются с верификацией поставки (`IMPLEMENTATION_RESULT`) и проектной готовностью; каузальная цепь происхождения артефактов гарантирует, что адрес репозитория возникает строго по факту свидетельства GitHub API.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов фабрики при проходящих тестах;**
  * Деонтический запрет на выдачу разрешения при отсутствии свидетельств: отказ без маскировки под успех.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Boundary topology, Varzi]:**
  * Топологический заслон границ: пути локального workspace надежно изолированы от path traversal; решётка вердиктов обладает свойством монотонности (может только сужать допуск, но никогда не расширять его вопреки первичным гейтам).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Многозначная логика истинности в отчётах проверок и контроллерах: явное разделение `passed`, `failed`, `undetermined` и `unapplied`, исключая скрытые NullPointerException и ложные нули.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Teleosemantic feedback, Dretske & Clark]:**
  * Проверка предметной близости и артефактов реализации в заслонах совершенства дизайна и контрактов бэкенда.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership, Shaffer]:**
  * Четкое владение жизненным циклом и целостностью артефактов проекта от локального workspace до удаленных репозиториев GitHub и трекеров Linear.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,QualityGateControllerTest,VerdictGateTest,VerdictReconciliationTest,VerdictLayerFalsificationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectWorkspaceFactoryServiceFalsificationTest,ProcessControlServiceTest,ConstraintIdentificationServiceTest,BottleneckDetectionServiceTest,LaunchabilityConstraintServiceFalsificationTest,TocSubordinationLeverTest,FlowMetricsServiceTest,SixSigmaAuditServiceTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков строгих семейств в `docs/FACTORY_MECHANISMS.md`, внести блок `### Итог Секции VII` и блок `### Итог строгих семейств Ступени 4` со сводкой зелёных тестов, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(stage-4-quality-gates-and-provisioning): complete Stage 4 batch verification (D008, D010, D006)`.
