# Директивы и наряд для L2 (2026-10-17 01:29 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, память об отказах») успешно заверена инженером (коммит `b26bb75b`, 133/133 green в 22 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций XXII и XXIII («Миграции базы данных, запреты схемы, тождество задач, притязания, связность свидетельств, надзор за процессом, FlowSpine и защищенные инварианты Ступени 5»)** целиком (**116 тестов в 13 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Схема Flyway, тождество задач и запреты схемы (15 тестов, 4 сьюта):**
       - `FlywayMigrationValidationTest` (4/4 green): неизменяемость миграций V1–V143, валидация контрольных сумм и структуры схемы без скрытого repair (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `CompilerTaskIdentityTest` (3/3 green): контентный ключ V137 тождества задач компилятора (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `V25MigrationFalsificationTest` (7/7 green): фальсификационные заслоны миграции V25 (зависимости, хотспоты) (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AgencySchemaTest` (1/1 green): целостность и связность базовой схемы фабрики (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Притязания на задачи/файлы и изоляция гонок (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8 green), `ClaimServiceRaceGuardTest` (5/5 green), `ClaimServiceFalsificationTest` (6/6 green): конкурентный захват задач, партитивные блокировки `SELECT ... FOR UPDATE`, детерминированная кардинальность и защита от гонок (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 3: Связность свидетельств, надзор за процессом и карты Шухарта (37 тестов, 4 сьюта):**
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): когерентность свидетельств ECHO Тагарда, защитная отсечка V80 при циклическом несогласии свидетельств (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProcessControlServiceTest` (8/8 green), `ProcessControlServiceFalsificationTest` (3/3 green): контрольные карты Шухарта Шесть сигм, разделение случайного разброса и истинного сдвига процесса (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4: Позвоночник потока и защищенные инварианты Ступени 5 (45 тестов, 2 сьюта):**
       - `FlowSpineServiceTest` (32/32 green): чистая наблюдаемость потока FlowSpine без побочных эффектов записи на пути чтения (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `Stage5ProtectedInvariantsFalsificationTest` (13/13 green): фальсификационная защита священных инвариантов Ступени 5 (защита сущностей, статусов, репозиториев и согласованности) (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, моделей, сущностей, миграций, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны миграции V25 (`V25MigrationFalsificationTest`), отсечки связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), контрольных карт надзора за процессом (`ProcessControlServiceFalsificationTest`) и защищенных инвариантов Ступени 5 (`Stage5ProtectedInvariantsFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Строгий лок уровней абстракции схемы базы данных, контентный ключ тождества задач V137 (`CompilerTaskIdentityTest`), непрерывная релайабилистская цепь свидетельств и чистая наблюдаемость потока FlowSpine.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты: валидация схемы Flyway без скрытого repair (`FlywayMigrationValidationTest`), матрица прав захвата задач и файлов (`TaskClaimServiceTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Shaffer & Varzi]:**
  * Монопольные атомарные блокировки `SELECT FOR UPDATE` для притязаний на задачи и файлы без взаимоблокировок и гонок.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Grounding loss / Dretske]:**
  * Релаксация графа свидетельств по алгоритму ECHO Тагарда, запрет ложной уверенности на пустых данных.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика истинности состояний потока и защищенных репозиториев Ступени 5.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Контрольные карты Шухарта Шесть сигм и чистая наблюдаемость FlowSpine без побочных эффектов записи.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Контентный ключ тождества компилятора: исключение категориальной путаницы между сущностью задачи и ее представлением.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXII и XXIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest
   ```
2. Подтвердить 100% green результат (**116/116 green в 13 тест-сьютах**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* и итоговый блок Секций XXII и XXIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
