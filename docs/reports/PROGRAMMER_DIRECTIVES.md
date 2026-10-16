# Пакетная директива L1/L3 -> L2: Секция XXIV и XXV — Скрипты ручного запуска, Позвоночник потока, Ядро исполнения и Свод состояния фабрики

**Дата:** 2026-10-16 05:01 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XXII и XXIII («Миграции: запреты, которые исполняет база» и «Живой замер фабрики», 111/111 green в коммите `12b4bb8`) полностью заверены и закрыты инженером без единой строки правок рабочего кода. Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секцию XXIV («Скрипты: инструменты, которые запускает рука») и Секцию XXV («Позвоночник потока: чем фабрика отвечает, жив ли поток»)**:
   - **XXIV. Скрипты: инструменты, которые запускает рука:**
     * `generate_philosopher_patterns.py` (генератор 1720 паттернов корпуса, сверка с `03_PATTERN_STRENGTH.md`, `00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`, заслон `PhilosopherPatternCorpusConsistencyTest`);
     * `append_role_logic.py` (ролевые хартии BARCAN-TAG-00..12, fail-closed заслон верификации `tests/test_append_role_logic.py`);
     * `audit_pr.py` (PR boundary guard, контроль ролевых границ diff, fail-closed валидация);
     * `mock_test_runner.py` (ликвидация симуляции, верификация инвариантов доменных моделей `Greeting` и политик `PrivacyFilter`, тесты `tests/test_mock_test_runner.py`);
     * Вспомогательные скрипты оператора: `deploy.sh`, `linear_sync.py`, `linear_webhook.py`, `check_system_drift.ps1`, `generate_report.py`, `reset_project_data.sql`, модули `scripts/modules/*` (демаркация как ручной оснастки, исключение ложной выдачи за фоновые демоны фабрики).
   - **XXV. Позвоночник потока: чем фабрика отвечает, жив ли поток:**
     * `FlowSpineService` (ядро потока 1256 строк, строгое разведение чтения `build` и фиксации `observe`, дедупликация событий по SHA-256 хешу свидетельства, режим `observe_only`, заслон `FlowSpineServiceTest`);
     * `OperationalFlowCoreService` (заслоняющее ядро, режим `flow_core_enforced`, монополия переходов, заслоны `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`);
     * `SystemStatusService` (свод системы, ликвидация шестикратной загрузки через единый `projectTasks`, изоляция non-carrier задач Flyway V138, инвентарный заслон 76 вызовов `.findAll()`, заслон `SystemStatusServiceTest`);
     * `SystemSettingsService` и `SettingsController` (реестр настроек, маскирование секретов, отказ от пустых флагов, валидация Linear UUID, аудит мутаций, заслоны `SystemSettingsServiceTest`, `SettingsControllerIntegrationTest`);
     * `FlowSpineController`, `OperationalFlowCoreController`, `TocSentinelController`, `TocExecutionGraph` (строгое разведение чтения, наблюдения и телеметрии, идемпотентность событий `/observe`, ограничение выборок 1..500, in-memory семантика TOC графа с HTTP 429 при троттлинге, заслоны `TocSentinelServiceTest`, `TocSentinelControllerTest`, `SystemStatusControllerTest`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, скриптов и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XXIV и XXV находятся в стабильной рабочей форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven и Python unittest.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper / Tarski]:**
  * Фальсификация фиктивных прогонов тестов и ложных успехов (`mock_test_runner.py` обязан запускать проверяемые утверждения или падать; `PhilosopherPatternCorpusConsistencyTest` роняет сборку при любом расхождении 53 семейств или пропуске ACP-паттернов).
  * `FlowSpineServiceTest` и `OperationalFlowCoreFalsificationTest` фальсифицируют попытки записи дублирующих событий и несанкционированных переходов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции телеметрии: строгое разведение живого чтения (`build`), персистентного наблюдения (`observe`) и in-memory телеметрии управления (TOC graph/tokens).
  * Цепочка надежности источников скриптов оператора: происхождение, дата и проверяемость пути.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода при проходящих тестах;**
  * Матрица прав и обязанностей Flow Core (`OperationalFlowCoreService`): 19 состояний потока несут обязательный непустой список `forbiddenActions` с уникальным ключом `advisory.*`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * 4-значная логика истинности в статусах аккаунтов (`available` = `enabled && idle`) и доставке задач в `FlowSpineService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Принцип анти-зеркала: физическое состояние Actuator `UP/ok` и отсутствие мутаций важнее любого нарратива.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination, Church]:**
  * Граундинг корпуса паттернов: каждый паттерн извлекается с идентификатором, источником и классом дефекта, сверяясь с генератором без галлюцинаций.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XXIV и XXV в Docker Maven и Python:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemStatusControllerTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,TocSentinelServiceTest,TocSentinelControllerTest
   python3 scripts/generate_philosopher_patterns.py --verify
   python3 -m unittest tests/test_append_role_logic.py tests/test_mock_test_runner.py
   ```
2. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XXIV и XXV), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(flow-spine-and-scripts): complete Stage 4 batch verification (D008, D010, D006)`.
