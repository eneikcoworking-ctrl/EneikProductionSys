# Директивы и наряд для L2 (2026-10-16 13:30 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Реестр предписаний L2 и Цех дизайна») полностью заверены инженером (коммит `7ffaf95`, 272/272 green, 0 строк изменений рабочего кода). Ранее Секции V и VI (коммит `1dcdf20`), Секция VIII (коммит `3cd46d8`), Секция VII (TOC), Секции III–IV и Секции I–II также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Секций XXII и XXIII («Миграции базы: запреты, которые исполняет база, тождество в схеме, притязания на файлы, связность свидетельств, надзор за процессом и живой замер фабрики»: подразделы XXII, XXIIб–XXIIл и XXIII)** целиком (13 тест-сьютов, 116 тестов в едином непрерывном пакетном прогоне в Docker Maven):
     * **Группа 1: Схема Flyway, тождество в схеме и запреты базы (15 тестов, 4 сьюта):**
       - `FlywayMigrationValidationTest` (4/4): целостность и валидация чексумм V30–V143, validate-on-migrate=true, repair-on-startup=false (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `CompilerTaskIdentityTest` (3/3): тождество задачи от сущностного содержания работы, защита от размножения задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `V25MigrationFalsificationTest` (7/7): фальсификационный заслон миграции V25, устранение зависимостей и хотспотов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AgencySchemaTest` (1/1): топологическая демаркация ролевых границ агентской схемы V59 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Притязания на файлы, гонки и атомарный захват (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6): атомарный захват V104 через `SELECT FOR UPDATE`, защита от гонок владения файлами проекта (`DZHONATAN_SHEFFER_01_CAUSAL_GROUNDING` / D004, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Связность свидетельств, надзор за процессом и позвоночник потока (69 тестов, 5 сьютов):**
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств ECHO Тагарда, запрет ложной уверенности при расхождении данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_01_INFORMATION_CHANNEL_FIDELITY` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProcessControlServiceTest` (8/8), `ProcessControlServiceFalsificationTest` (3/3): карты Шухарта V89, фальсификационный детектор истинных сдвигов процесса (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `FlowSpineServiceTest` (32/32): наблюдаемость потока V66 без побочных эффектов (`observe_only`), криптографический хеш SHA-256 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 4: Защищенные инварианты схемы Ступени 5 (13 тестов, 1 сьют):**
       - `Stage5ProtectedInvariantsFalsificationTest` (13/13): нерушимость защищённых инвариантов схемы V30–V97 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D012, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: заслоны схемы БД (`FlywayMigrationValidationTest`), фальсификация гонок захвата (`ClaimServiceFalsificationTest`), фальсификация детекторов статистического контроля (`ProcessControlServiceFalsificationTest`), заслон терминации когерентности (`EvidenceCoherenceV80TerminationFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная надежность происхождения: тождество задачи от содержания работы (`CompilerTaskIdentityTest`), сеть доверия и связность свидетельств ECHO (`EvidenceCoherenceServiceTest`), аудит непрерывности в `FlowSpineServiceTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты схемы БД: ограничения V30–V143, блокировка repair миграций, запреты несанкционированного доступа к файловым ресурсам.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Varzi]:**
  * Мереологическая целостность захвата задач и топологическая демаркация ролевых границ агентов в `AgencySchemaTest`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService` и `ProcessControlService`: телеметрия читает состояние рантайма без побочных эффектов и скрытых мутаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка истинности статусов в защищённых инвариантах Ступени 5 (`Stage5ProtectedInvariantsFalsificationTest`).

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 13 тест-сьютов Секций XXII и XXIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest
   ```
2. Подтвердить строго **116/116 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
