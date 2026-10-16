# Директивы и наряд для L2 (2026-10-16 12:30 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр предписаний L2 и Цех дизайна») успешно заверен инженером (коммит `256a940`, 272/272 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **«Строгие семейства Ступени 4: Заслоны качества задач, решётка вердиктов и провижининг проектов» целиком** (21 тест-сьют, 144 теста в едином пакетном прогоне Docker Maven):
     * **Группа 1: Заслоны качества задач и оркестратор гейтов (Task Quality Gates & Gate Orchestrator, 90 тестов, 13 сьютов):**
       - `GateOrchestratorIntegrationTest` (9/9), `GateOrchestratorFalsificationTest` (6/6): оркестрация стадий гейта качества и фальсификационный заслон прохождения стадий (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `BaseQualityGateTest` (10/10), `BaseQualityGateFalsificationTest` (6/6): базовый гейт качества с отсечением симуляций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `BackendContractGateTest` (5/5), `BackendContractGateFalsificationTest` (6/6): заслон контрактов бэкенда и демаркация API (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6): строгий гейт совершенства дизайна на реальных артефактах (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `VerificationEvidenceGateTest` (8/8), `VerificationEvidenceGateFalsificationTest` (6/6): заслон наличия верификационных свидетельств (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `EpistemicLayerInvariantGateTest` (8/8), `EpistemicLayerInvariantGateFalsificationTest` (6/6): заслон эпистемических слоев и инвариантов фабрики (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
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
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене свидетельств (`VerificationEvidenceGateFalsificationTest`), нарушении инвариантов контракта (`BackendContractGateFalsificationTest`), попытке провижининга несуществующего репозитория (`ProjectFactoryServiceFalsificationTest`) или симуляции вердикта (`VerdictLayerFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: гейты качества не имеют права подменять сырые артефакты своими догадками; происхождение фактов (lineage) должно оставаться прозрачным и проверяемым от сырого выхода к агрегированному вердикту.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет появления фиктивных URL репозиториев без физического ответа GitHub API; строгая авторизация вызовов контроллеров гейта качества.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap multi-valued truth lattice]:**
  * Четырехзначная логика состояний гейта и вердиктов: `passed`, `failed`, `withhold`, `undetermined`. Отсутствие проверки никогда не интерпретируется как успех.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership & D006 Varzi]:**
  * Топология границ и монополия владения воркспейсом проекта: запрет выхода за пределы корневого каталога воркспейса при любых операциях провижининга.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер 21 тест-сьюта строгих семейств Ступени 4 в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=GateOrchestratorIntegrationTest,GateOrchestratorFalsificationTest,BaseQualityGateTest,BaseQualityGateFalsificationTest,BackendContractGateTest,BackendContractGateFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,VerificationEvidenceGateTest,VerificationEvidenceGateFalsificationTest,EpistemicLayerInvariantGateTest,EpistemicLayerInvariantGateFalsificationTest,QualityGateControllerTest,VerdictGateTest,VerdictReconciliationTest,VerdictLayerFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest
   ```
2. Подтвердить строго **144/144 green** (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(stage-4-core): complete Stage 4 batch verification (D008, D010, D006)`.
