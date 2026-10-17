# Директивы и наряд для L2 (2026-10-17 02:40 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна») успешно заверен инженером (коммит `81128be1`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Строгие семейства Ступени 4 («Заслоны качества задач, решётка вердиктов и провижининг проектов»)** целиком (**144 теста в едином непрерывном пакетном прогоне в Docker Maven [21 тест-сьют]**):
     * **Группа 1: Заслоны качества задач и оркестратор гейтов (Task Quality Gates & Orchestrator, 90 тестов, 13 сьютов):**
       - `GateOrchestratorIntegrationTest` (9/9), `GateOrchestratorFalsificationTest` (6/6): оркестрация заслонов качества задач без пропуска дефектов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `BaseQualityGateTest` (10/10), `BaseQualityGateFalsificationTest` (6/6): базовый гейт качества и попперовская фальсификация (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `BackendContractGateTest` (5/5), `BackendContractGateFalsificationTest` (6/6): заслон контракта бэкенда и лок уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): гейт совершенства дизайна на реальных артефактах (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `VerificationEvidenceGateTest` (8/8), `VerificationEvidenceGateFalsificationTest` (6/6): заслон свидетельств верификации и многозначная решётка истинности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `EpistemicLayerInvariantGateTest` (8/8), `EpistemicLayerInvariantGateFalsificationTest` (6/6): гейт эпистемических инвариантов слоев (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `QualityGateControllerTest` (2/2): контроллер гейта качества с проверкой деонтических прав (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Решётка вердиктов и согласование исходов (Verdict Surfaces & Lattice, 32 теста, 3 сьюта):**
       - `VerdictGateTest` (14/14): решётка вердиктов качества с монотонным сужением допусков (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `VerdictReconciliationTest` (11/11): детерминированное согласование конфликтующих слоев вердиктов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `VerdictLayerFalsificationTest` (7/7): фальсификация ложных вердиктов готовности (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Цепочка результатов провижининга фабрики проектов (Project Factory Provisioning Result Chain, 22 теста, 5 сьютов):**
       - `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5): топологическая изоляция путей воркспейса проекта (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProjectFactoryServiceTest` (4/4), `ProjectFactoryServiceFalsificationTest` (6/6): детерминированный провижининг проектов без маскировки сбоев (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GitHubProjectFactoryClientTest` (3/3): фиксация URL репозитория строго при наличии GitHub-свидетельства (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `LinearProjectFactoryClientTest` (4/4): клиент фабрики проектов Linear с изоляцией контекста (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские фальсификационные заслоны гейтов качества задач (`GateOrchestratorFalsificationTest`, `BaseQualityGateFalsificationTest`, `BackendContractGateFalsificationTest`, `DesignExcellenceGateFalsificationTest`, `VerificationEvidenceGateFalsificationTest`, `EpistemicLayerInvariantGateFalsificationTest`), фальсификация ложных вердиктов (`VerdictLayerFalsificationTest`) и провижининга воркспейсов (`ProjectWorkspaceFactoryServiceFalsificationTest`, `ProjectFactoryServiceFalsificationTest`). Опровержение любой симуляции или ложной зелени.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции контрактов бэкенда (`BackendContractGateTest`), эпистемических инвариантов (`EpistemicLayerInvariantGateTest`) и релайабилистская каузальная цепь свидетельств приемки артефактов (`VerdictReconciliationTest`, `ProjectFactoryServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Кодифицированные запреты контроллера гейтов (`QualityGateControllerTest`), демаркация прав вызовов и топологическая изоляция путей файловой системы воркспейса проекта (`ProjectWorkspaceFactoryServiceFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка вердиктов качества (`VerdictGateTest`, `VerificationEvidenceGateTest`) и монотонное сужение допусков без категориального смешения.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Мереологическая целостность сущностей фабрики и нерушимость границ владения переходами состояний.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Строгих Семейств Ступени 4 в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,QualityGateControllerTest,VerdictGateTest,VerdictReconciliationTest,VerdictLayerFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest
   ```
2. Подтвердить 100% green результат (144/144 green в 21 тест-сьюте, 0 failures, 0 errors, BUILD SUCCESS).
3. Обеспечить строго 0 строк изменений рабочего кода подсистем (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(stage-4-strict-families): complete Stage 4 batch verification (D008, D010, D006)`.
