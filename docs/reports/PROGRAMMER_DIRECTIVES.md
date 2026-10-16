# Директивы и наряд для L2 (2026-10-16 13:42 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXII и XXIII («Миграции базы и живой замер фабрики») полностью заверены инженером (коммит `4df7a4a`, 116/116 green, 0 строк изменений рабочего кода). Ранее Секции XVI и XXIX (`7ffaf95`), Секции V и VI (`1dcdf20`), Секция VIII (`3cd46d8`), Секция VII (TOC), Секции III–IV и Секции I–II также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Секций XXIV («Скрипты: инструменты, которые запускает рука») и XXV («Позвоночник потока: чем фабрика отвечает, жив ли поток»)** целиком (10 тест-сьютов, 169 тестов: 149 в Docker Maven и 20 в Python unittest, плюс 1720 философских паттернов корпуса):
     * **Группа 1: Секция XXIV — Скрипты оператора и корпус философских паттернов (26 тестов, 1720 паттернов):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 в Docker Maven): сверка трех источников корпуса (53 семейства, 4 фабричных паттерна, 104 общих паттерна ACP-101..108, нулевые коллизии идентификаторов; `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `scripts/generate_philosopher_patterns.py --verify` (1720 паттернов в Python): проверка детерминизма генератора, предотвращение деструктивного `rmtree`, валидация расширенных секций ACP (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `scripts/tests/test_append_role_logic.py` (10/10 в Python unittest): 13 ролевых хартий BARCAN-TAG-00..12, детерминированное добавление логики ролей (`GART_EVANS_01_DE_RE_DISCRIMINATION` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `scripts/tests/test_mock_test_runner.py` (10/10 в Python unittest): ликвидация симуляции, заземление на доменные инварианты Greeting и PrivacyFilter (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - Институциональная демаркация ручной оснастки оператора (`deploy.sh`, `linear_sync.py`, `linear_webhook.py`, `check_system_drift.ps1`, `generate_report.py`, `reset_project_data.sql`, `scripts/modules/*` — `GART_EVANS_01_DE_RE_DISCRIMINATION` / D006).
     * **Группа 2: Секция XXV — Позвоночник потока: операционный контроль, статус и настройки (143 теста в Docker Maven, 9 сьютов):**
       - `FlowSpineServiceTest` (32/32): наблюдаемость потока V66 без побочных эффектов (`observe_only`), криптографический SHA-256 хеш свидетельства, разведение чтения и записи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39): режим `flow_core_enforced`, запреты как код по 19 состояниям и 7 блокерам, валидация переходов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SystemStatusServiceTest` (22/22), `SystemStatusControllerTest` (3/3): единый подъем задач `projectTasks` за вызов, изоляция non-carrier задач Flyway V138, двухконъюнктная готовность `enabled && idle`, отсечение N+1 каскадов (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `SystemSettingsServiceTest` (13/13), `SettingsControllerIntegrationTest` (8/8): маскирование секретов (`***`), отказ сохранения пустых флагов, институциональный аудит мутаций через `AuditCallerResolver` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): барабан/буфер TOC, лимиты 1..500, HTTP 429 при троттлинге, защита от переполнения графа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме, Docker Maven и Python unittest без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны переходов `OperationalFlowCoreFalsificationTest`, сверка коллизий и целостности паттернов `PhilosopherPatternCorpusConsistencyTest`.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная надежность происхождения: хеш доказательства SHA-256 в `FlowSpineService`, защита от поддельных тестовых результатов в `test_mock_test_runner.py`, аудит настроек в `SystemSettingsService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты потока: режим `flow_core_enforced` блокирует недопустимые переходы, запрет сохранения пустых значений системных настроек.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService` и `SystemStatusService`: телеметрия читает состояние рантайма без побочных эффектов (`observe_only`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка истинности статусов в `SystemStatusService` и `FlowSpineService`.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Semantic ungrounding / Church]:**
  * Заземление корпуса философских паттернов, предотвращение коллизий идентификаторов и неконтролируемого дрейфа онтологии.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 10 тест-сьютов Секций XXIV и XXV в Docker Maven 3.9.9 Temurin-21 (`-m 2g`) и Python unittest:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,FlowSpineControllerTest,OperationalFlowCoreControllerTest,TocSentinelControllerTest,PhilosopherPatternCorpusConsistencyTest
   python3 -m unittest discover -s scripts/tests -p "*_test.py"
   python3 scripts/generate_philosopher_patterns.py --verify
   ```
2. Подтвердить строго **169/169 green** (149 Maven + 20 Python, 1720 паттернов верифицировано, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xxiv-xxv): complete Stage 4 batch verification (D008, D010, D006)`.
