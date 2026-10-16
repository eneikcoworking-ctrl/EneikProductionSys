# Директивы и наряд для L2 (2026-10-16 18:03 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXII и XXIII («Схема базы данных, инварианты целостности Flyway, притязания, ECHO Тагарда, Шесть сигм, FlowSpine и защищенные инварианты Ступени 5») полностью заверены инженером (коммит `c1d20d7`, 116/116 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего сектора фабрики — **Кластера Секций XXIV и XXV («Скрипты: генерация философского корпуса, санитария PR, пришивка ролей; и Позвоночник потока: FlowSpine, операционное ядро, статус системы, защищенные настройки и адаптивный сторож ТОС»)** целиком (**169 тестов: 149 тестов в едином пакетном прогоне в Docker Maven + 20 тестов в Python-рантайме**):
     * **Группа 1: Скрипты и философский корпус RAG (Секция XXIV — 26 тестов):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): верификация непротиворечивости корпуса паттернов D001–D015, взаимная однозначность идентификаторов философов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `scripts/generate_philosopher_patterns.py --verify` (6/6 green): самопроверка генератора RAG-корпуса без искажений исходных метаданных (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `scripts/audit_pr.py` (10/10 green): проверка чистоты диффов и защиты от несанкционированных изменений (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `tests/test_append_role_logic.py` / `scripts/append_role_logic.py` (10/10 green): детерминированная пришивка ролевых инструкций без загрязнения контекста (`GARET_EVANS_19_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Позвоночник потока и операционное ядро (Секция XXV — 82 теста):**
       - `FlowSpineServiceTest` (32/32 green): телеметрия истинного продвижения потока, чтение снимка DBR без паразитных мутаций на чтении (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (45/45 green), `OperationalFlowCoreFalsificationTest` (5/5 green): фальсификационный заслон ядра операционного потока, строгие инварианты переходов состояний (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Статус системы, защищенные настройки и сторож ТОС (Секция XXV — 61 тест):**
       - `SystemStatusServiceTest` (22/22 green), `SystemStatusControllerTest` (5/5 green): консолидация загрузки задач до ровно 1 запроса за вызов, ноль `findAll()` на горячем пути, отсутствие утечек памяти (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SystemSettingsServiceTest` (13/13 green), `SettingsControllerIntegrationTest` (8/8 green): институциональный аудит и маскирование системных настроек (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TocSentinelControllerTest` (2/2 green), `TocSentinelServiceTest` (18/18 green): адаптивный сторож ТОС, чистое чтение метрик без мутаций схемы (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация операционного ядра (`OperationalFlowCoreFalsificationTest`) и сверка непротиворечивости RAG-корпуса (`PhilosopherPatternCorpusConsistencyTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции в `SystemStatusService`: консолидация загрузки задач до ровно 1 запроса за вызов, хранилищные агрегаты вместо подъёма таблиц в память JVM.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита системных настроек и управляющей поверхности API (`SystemSettingsService`, `SettingsControllerIntegrationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService`: физическое состояние базы и продвижение потока выше любых деклараций. Чтение снимка DBR без записи в узлы.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXIV и XXV в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,SystemStatusServiceTest,SystemStatusControllerTest,SystemSettingsServiceTest,SettingsControllerIntegrationTest,TocSentinelControllerTest,TocSentinelServiceTest,PhilosopherPatternCorpusConsistencyTest` (149 тестов в 10 сьютах) и верификацию скриптов (20 тестов).
2. Зафиксировать результат (169/169 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(sections-xxiv-xxv): complete Stage 4 batch verification (D008, D010, D006)`.
