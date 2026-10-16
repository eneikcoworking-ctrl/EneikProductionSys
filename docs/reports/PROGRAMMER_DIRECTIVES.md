# Директивы и наряд для L2 (2026-10-16 11:35 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXII и XXIII («Миграции базы: запреты, которые исполняет база, тождество в схеме, притязания на файлы, связность свидетельств, надзор за процессом и живой замер фабрики») успешно заверены инженером (коммит `df40bde`, 111/111 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей архитектурной секции фабрики — **Секции XXIV («Скрипты: инструменты, которые запускает рука») и Секции XXV («Позвоночник потока: чем фабрика отвечает, жив ли поток»)** целиком (169 тестов: 149 в Docker Maven + 20 в Python unittest + валидация 1720 философских паттернов):
     * **Секция XXIV: Скрипты оператора и RAG-корпус (6 Maven тестов + 20 Python тестов + верификация 1720 паттернов):**
       - `generate_philosopher_patterns.py` — проверка целостности корпуса RAG через `PhilosopherPatternCorpusConsistencyTest` (6/6) и `scripts/generate_philosopher_patterns.py --verify` (1720 паттернов, 53 семейства, 104 ACP паттерна, 0 коллизий) (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `append_role_logic.py` и `audit_pr.py` — `tests/test_append_role_logic.py` (10/10) и fail-closed валидация 13 хартий BARCAN-TAG-00..12 с передаточным заслоном верификации корпуса (`GARET_EVANS_19_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `mock_test_runner.py` — `tests/test_mock_test_runner.py` (10/10): полная ликвидация симуляции тестов, исполняемые проверки инвариантов Greeting (TAG-01) и PrivacyFilter (TAG-10/TAG-07) (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - Вспомогательные скрипты оператора (`deploy.sh`, `linear_sync.py`, `linear_webhook.py`, `check_system_drift.ps1`, `generate_report.py`, `reset_project_data.sql`, `scripts/modules/*`) демаркированы как ручная оснастка, не подменяющая фоновые демоны (`GARET_EVANS_19_BOUNDARY_TOPOLOGY` / D006).
     * **Секция XXV: Позвоночник потока, свод состояния и TOC (143 Maven теста, 9 сьютов):**
       - `FlowSpineServiceTest` (32/32) — строгое разведение чтения (`build`) и записи (`observe`), SHA-256 хеш свидетельства, режим `observe_only` без ложных блокировок (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39) — режим `flow_core_enforced`, полная матрица прав и обязанностей Хофельда/Раза по всем 19 состояниям потока и 7 жестким блокировкам, запреты как код (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemStatusServiceTest` (22/22), `SystemStatusControllerTest` (3/3) — консолидация загрузки задач до 1 запроса (`projectTasks`), хранилищные агрегаты задач-носителей (`carrier`, Flyway V138), двухконъюнктная доступность аккаунтов (`enabled && idle`) с покраснением сводки при отказе (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `SystemSettingsServiceTest` (13/13), `SettingsControllerIntegrationTest` (8/8) — маскирование секретов, отказ при пустых флагах, аудит мутаций настроек (`GARET_EVANS_19_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2) — барабан/буфер ТОС, ограничение выборок 1..500, возврат HTTP 429 при троттлинге токенов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме, Docker Maven и Python unittest без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при повреждении RAG-корпуса (`PhilosopherPatternCorpusConsistencyTest`), несанкционированном переходе потока (`OperationalFlowCoreFalsificationTest`) или выключении аккаунтов при сохранении статуса `idle` (`allAccountsDisabledWithIdleStatusCausesSummaryToReddenAndNameCondition`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: ликвидация фиктивных тестов в `mock_test_runner.py` с переходом на исполняемые инварианты доменных классов; фиксация событий `FlowSpineService` только при реальном изменении хеша свидетельства с сохранением полной линии происхождения данных.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GARET_EVANS_19_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Evans]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты как код в `OperationalFlowCoreService` (19 состояний несут непустой список `forbiddenActions`); топологическая изоляция скриптов оператора от автономных демонов фабрики; валидация ролевых границ в `audit_pr.py`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * Четырёхзначная логика: явное разграничение `True`, `False`, `Unknown`, `Contradictory` в событиях потока и двухконъюнктной доступности аккаунтов.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Принцип анти-зеркала: чтение потока `FlowSpineService.build()` и опрос статуса `SystemStatusService` не оставляют следов и мутаций в базе данных.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Church]:**
  * Заземление корпуса: автоматическая проверка 1720 паттернов и 53 семейств исключает галлюцинации и рассинхронизацию словаря дефектов.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXIV и XXV:**
   - **Docker Maven (149 тестов, 10 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemStatusControllerTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,TocSentinelServiceTest,TocSentinelControllerTest
     ```
   - **Python unittest (20 тестов) и верификация RAG-корпуса (1720 паттернов):**
     ```bash
     python3 -m unittest tests/test_append_role_logic.py tests/test_mock_test_runner.py
     python3 scripts/generate_philosopher_patterns.py --verify
     ```
2. **Верификация результата:**
   - Строго **169/169 green** (149 Maven + 20 Python, 1720 паттернов проверено, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XXIV и XXV в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XXIV и XXV в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xxiv-xxv): complete Stage 4 batch verification (D008, D010, D006)`.
