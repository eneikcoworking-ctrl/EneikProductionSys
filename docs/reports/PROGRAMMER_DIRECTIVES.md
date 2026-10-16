# Директивы и наряд для L2 (2026-10-16 16:52 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр предписаний L2» и «Цех дизайна») полностью заверен инженером (коммит `7e925d0`, 272/272 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего сектора фабрики — **Кластера Секций XXII и XXIII («Схема базы данных, инварианты целостности Flyway, притязания, ECHO Тагарда, Шесть сигм, FlowSpine и защищенные инварианты Ступени 5»)** целиком (**116 тестов в 13 тест-сьютах** в едином пакетном прогоне в Docker Maven):
     * **Группа 1: Схема Flyway и целостность базы данных (15 тестов, 4 сьюта):**
       - `FlywayMigrationValidationTest` (4/4): валидация цепочки миграций схемы (V30–V143), проверка чексумм при старте (`validate-on-migrate=true`), категорический запрет молчаливого repair (`repair-on-startup=false`) (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `CompilerTaskIdentityTest` (3/3): тождество задачи компилятора выводится строго из контентного ключа V137 (`tasks.content_key`), предотвращение тихого размножения задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `V25MigrationFalsificationTest` (7/7): структурная нерушимость исторических миграций схемы V25/V33–V40 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AgencySchemaTest` (1/1): изоляция агентских схем и топологическая демаркация ролевых границ V59 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 2: Притязания на задачи/файлы и изоляция гонок (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (5/5), `ClaimServiceFalsificationTest` (6/6): атомарный захват притязаний на задачи и файлы через `SELECT ... FOR UPDATE`, строгая изоляция гонок и деонтическая матрица прав (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Связность свидетельств и статистический контроль процесса (37 тестов, 4 сьюта):**
       - `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5): релаксация графа свидетельств по алгоритму ECHO Тагарда, пороги завершения V80 без холостых N+1 циклов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProcessControlServiceTest` (8/8), `ProcessControlServiceFalsificationTest` (3/3): статистический контроль процесса Шесть сигм V89, карты Шухарта, разделение обычной вариации и истинного сдвига (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4: Позвоночник потока и защищенные инварианты Ступени 5 (45 тестов, 2 сьюта):**
       - `FlowSpineServiceTest` (32/32): позвоночник потока V66, наблюдаемость проекта без мутаций (`build` vs `observe`), идемпотентность событий по SHA-256 хешу (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `Stage5ProtectedInvariantsFalsificationTest` (13/13): защищенные инварианты схемы БД V30, V42, V44, V45, V55, V82, V92, V97: непреложные деонтические запреты схемы (NOT NULL, UNIQUE, CHECK, FOREIGN KEY), исключение регрессии (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны миграции V25 (`V25MigrationFalsificationTest`), фальсификация гонок притязаний (`ClaimServiceFalsificationTest`), фальсификация отсечки связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), заслон защищенных инвариантов (`Stage5ProtectedInvariantsFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: неизменность тождества задач компилятора по контентному ключу V137 (`CompilerTaskIdentityTest`), строгая каузальная цепь свидетельств (`EvidenceCoherenceServiceTest`), разделение наблюдения и мутаций в позвоночнике потока (`FlowSpineServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон миграций Flyway: валидация неизменности контрольных сумм и структуры схемы без скрытого `repair` на старте;
  * Матрица прав и обязанностей при захвате задач и защищенных инвариантах схемы БД.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004, D006 Mereology & Boundary / Varzi]:**
  * Мереология и топология границ схемы: атомарный захват задач, защита от коллизий в файловом скоупе, демаркация агентских схем V59.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Missing verification / Dretske]:**
  * Телеосемантическая обратная связь графа свидетельств: пороги завершения ECHO Тагарда без бесконечной релаксации.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырёхзначная семантика истинности в защищенных инвариантах (`Stage5ProtectedInvariantsFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: объективный замер продвижения потока через `FlowSpineServiceTest` и карты Шухарта в `ProcessControlServiceTest` независимо от деклараций агентов.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Разделение категорий при определении тождества задач компилятора и предотвращение категориальной путаницы.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXII и XXIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest` (116 тестов в 13 сьютах).
2. Зафиксировать результат (116/116 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
