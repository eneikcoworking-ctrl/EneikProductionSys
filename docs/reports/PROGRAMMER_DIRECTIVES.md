# Директивы и наряд для L2 (2026-10-16 16:11 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна») полностью заверены инженером (коммит `a12b89c`, 272/272 green, 0 строк изменений рабочего кода). Ранее Секции VII и VIII также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXII и XXIII («Миграции базы: запреты базы, тождество в схеме, притязания, связность свидетельств, надзор за процессом и живой замер фабрики»)** целиком (13 тест-сьютов, 116 тестов в едином пакетном прогоне в Docker Maven):
     * **Группа 1: Схема Flyway, тождество в схеме и запреты базы (15 тестов, 4 сьюта):**
       - `FlywayMigrationValidationTest` (4/4): валидация неизменности миграций схемы (V30–V143), запрет silent repair (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `CompilerTaskIdentityTest` (3/3): тождество задач компилятора по контентному ключу V137 (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `V25MigrationFalsificationTest` (7/7): фальсификационный заслон топологии графа зависимостей V25 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AgencySchemaTest` (1/1): соответствие схемы агентства уставным ограничениям (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Притязания на файлы, гонки и атомарный захват (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8): целостность захвата задач и соблюдение прав/обязанностей (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `ClaimServiceRaceGuardTest` (5/5): защита от гонок при конкурентных притязаниях агентов (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `ClaimServiceFalsificationTest` (6/6): попперовская фальсификация состояний притязаний (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Связность свидетельств, надзор за процессом и позвоночник потока (69 тестов, 5 сьютов):**
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): сохранение каузальной цепи свидетельств и заслон завершения V80 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProcessControlServiceTest` (8/8), `ProcessControlServiceFalsificationTest` (3/3): статистический надзор за процессом без ложного приписывания причин (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `FlowSpineServiceTest` (32/32): телеметрия истинного продвижения потока (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 4: Защищенные инварианты схемы Ступени 5 (13 тестов, 1 сьют):**
       - `Stage5ProtectedInvariantsFalsificationTest` (13/13): четырёхзначная семантика истинности и нерушимость защищенных инвариантов схемы (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны миграции V25 (`V25MigrationFalsificationTest`), фальсификация гонок притязаний (`ClaimServiceFalsificationTest`), фальсификация остановки связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), заслон защищенных инвариантов (`Stage5ProtectedInvariantsFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: неизменность тождества задач компилятора по контентному ключу V137 (`CompilerTaskIdentityTest`), строгая каузальная цепь свидетельств (`EvidenceCoherenceServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон миграций Flyway: валидация неизменности контрольных сумм и структуры схемы без скрытого `repair` на старте.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004, D006 Mereology & Boundary / Varzi]:**
  * Мереология и топология границ схемы: атомарный захват задач, защита от коллизий в файловом скоупе, строгое соблюдение прав и обязанностей агентов при притязаниях.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Missing verification / Dretske]:**
  * Телеосемантическая обратная связь надзора за процессом: статистический контроль без искажения данных и ложной атрибуции причин.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырёхзначная семантика истинности в защищенных инвариантах (`Stage5ProtectedInvariantsFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: объективный замер продвижения потока через `FlowSpineServiceTest` независимо от деклараций агентов.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXII и XXIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest`
2. Зафиксировать результат (116/116 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
