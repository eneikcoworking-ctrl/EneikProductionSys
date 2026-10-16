# Директивы и наряд для L2 (2026-10-16 15:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXII и XXIII («Миграции базы: запреты схемы, тождество задач, притязания, связность свидетельств, надзор за процессом и живой замер фабрики») полностью закрыт инженером (коммит `84d7c3f`, 116/116 green, 0 строк изменений рабочего кода). Ранее Секции VII, VIII, XVI и XXIX также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего сектора фабрики — **Кластера Секций XXIV и XXV («Скрипты оператора, RAG-корпус образцов и Позвоночник потока: чем фабрика отвечает, жив ли поток»)** целиком (169 тестов: 149 тестов в Docker Maven по 10 тест-сьютам + 20 тестов в Python unittest, верификация 1720 паттернов):
     * **Группа 1: Секция XXIV — Скрипты оператора и RAG-корпус образцов (26 тестов, 2 сьюта Python + Docker Maven):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): сверка 53 семейств корпусов, 0 коллизий фабричных паттернов с индексом, полнота ACP-104 (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - Python scripts validation (`append_role_logic.py`, `audit_pr.py`, `generate_philosopher_patterns.py --verify`): 20/20 green, 1720 философских паттернов проверены без дрейфа (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XXV — Позвоночник потока, телеметрия и статус системы (143 теста, 9 сьютов в Docker Maven):**
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного продвижения потока без подмены артефактов словами (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): фальсификационный заслон ядра операционного потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `SystemStatusServiceTest` (22/22 green), `SystemStatusControllerTest` (5/5 green): консолидация загрузки задач до ровно 1 запроса, ноль `findAll()` на горячем пути, статус системы без дрейфа памяти (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest` (13/13 green), `SettingsControllerIntegrationTest` (8/8 green): институциональный аудит и маскирование системных настроек (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TocSentinelControllerTest` (2/2 green), `TocSentinelServiceTest` (18/18 green): адаптивная верёвка ТОС, чистый снимок DBR без мутаций на чтении (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация ядра потока (`OperationalFlowCoreFalsificationTest`) и сверка непротиворечивости RAG-корпуса (`PhilosopherPatternCorpusConsistencyTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции в `SystemStatusService`: консолидация загрузки задач до ровно 1 запроса за вызов, хранилищные агрегаты вместо подъёма таблиц в память JVM.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита системных настроек и управляющей поверхности API (`SystemSettingsService`, `SettingsControllerIntegrationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService`: физическое состояние базы и продвижение потока выше любых деклараций. Чтение снимка DBR без записи в узлы.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` & `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` [D014 Grounding drift & D008 Popper / Church]:**
  * Капсула обоснования RAG-корпуса и чистое функциональное редуцирование адаптивного сторожа ТОС.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная логика истинности в статусах готовности и здоровья потока.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXIV и XXV в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`) и Python unittest:
   - Docker Maven: `mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemStatusControllerTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,TocSentinelControllerTest,TocSentinelServiceTest` (149 тестов);
   - Python unittest: `python3 scripts/generate_philosopher_patterns.py --verify` и сьюты скриптов (20 тестов).
2. Зафиксировать результат (169/169 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(sections-xxiv-xxv): complete Stage 4 batch verification (D008, D010, D006)`.
