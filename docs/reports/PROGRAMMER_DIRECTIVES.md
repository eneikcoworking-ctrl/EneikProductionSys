# Директивы и наряд для L2 (2026-10-17 01:38 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXII и XXIII («Миграции базы данных, запреты схемы, тождество задач, притязания, связность свидетельств, надзор за процессом, FlowSpine и защищенные инварианты Ступени 5») успешно заверен инженером (коммит `969cb87f`, 116/116 green в 13 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций XXIV и XXV («Скрипты: генерация философского корпуса, санитария PR, пришивка ролей; и Позвоночник потока: FlowSpine, операционное ядро, статус системы, защищенные настройки и адаптивный сторож ТОС»)** целиком (**174 теста: 154 теста в 11 тест-сьютах в Docker Maven + 20 тестов в Python-рантайме**):
     * **Группа 1: Скрипты и целостность философского корпуса RAG (Секция XXIV — 26 тестов):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): заземление и согласованность корпуса паттернов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `python3 scripts/generate_philosopher_patterns.py --verify` (6/6 green checks: 1720 паттернов, 53 семейства, 104 общих ACP, 0 коллизий) (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `scripts/audit_pr.py`: проверка чистоты диффов и защиты от несанкционированных изменений (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `tests/test_append_role_logic.py` / `scripts/append_role_logic.py` (10/10 green) и `tests/test_mock_test_runner.py` (10/10 green): детерминированная пришивка ролевых инструкций без загрязнения контекста и надежный прогон тестов (`GARET_EVANS_19_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Позвоночник потока и операционное ядро (Секция XXV — 77 тестов):**
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного продвижения потока, разделение чтения `build` и фиксации `observe` без паразитных мутаций на чтении (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): фальсификационный заслон ядра операционного потока, строгие инварианты переходов состояний (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 3: Статус системы, защищенные настройки и адаптивный сторож ТОС (Секция XXV — 71 тест):**
       - `SystemStatusServiceTest` (22/22 green), `SystemStatusControllerTest` (3/3 green), `SystemStatusControllerIntegrationTest` (5/5 green): консолидация загрузки задач проекта до ровно 1 вызова `projectTasks`, ноль `findAll()` на горячем пути, отсутствие утечек памяти (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest` (13/13 green), `SettingsControllerIntegrationTest` (8/8 green): институциональный аудит и маскирование системных настроек (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TocSentinelControllerTest` (2/2 green), `TocSentinelServiceTest` (18/18 green): адаптивный сторож ТОС, чистое чтение DBR-снимка без мутаций узлов и графа (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов пакетным замером в рантайме Docker Maven и Python без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны целостности философского корпуса образцов (`PhilosopherPatternCorpusConsistencyTest`) и переходов состояний операционного ядра (`OperationalFlowCoreFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: консолидация выборки задач проекта до 1 вызова `projectTasks` в `SystemStatusService`, изоляция non-carrier задач Flyway V138, 0 неклассифицированных full-table reads на горячем пути.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон скриптов аудита PR (`audit_pr.py`) и маскирование системных настроек (`SystemSettingsService`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: строгое разделение операций чтения (`build`) и фиксации (`observe`) в позвоночнике потока FlowSpine; чистое чтение DBR-снимка сторожем ТОС (`TocSentinelService`) без мутирующих побочных эффектов.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding gap / Church]:**
  * 100% заземление RAG-корпуса: генератор `scripts/generate_philosopher_patterns.py --verify` валидирует тождество 1720 публикационных паттернов, 53 семейств и 104 общих образцов без расхождений.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная решётка истинности статусов операционного ядра и состояний задач.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXIV и XXV в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemStatusControllerTest,SystemStatusControllerIntegrationTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,TocSentinelControllerTest,TocSentinelServiceTest
   ```
2. Выполнить проверку скриптов и ролевой логики в Python-рантайме:
   ```bash
   python3 scripts/generate_philosopher_patterns.py --verify
   python3 -m unittest discover -s tests/ -p "test_*.py"
   ```
3. Подтвердить 100% green результат (174/174 green: 154 в Docker Maven + 20 в Python).
4. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
5. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
6. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
7. Закоммитить результат с семантическим сообщением: `feat(sections-xxiv-xxv): complete Stage 4 batch verification (D008, D010, D006)`.
