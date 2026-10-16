# Директивы и наряд для L2 (2026-10-16 11:25 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, лестница доверия, журнал дефектов и уплотнение хранилища») успешно заверена инженером (коммит `a2abaaf`, 133/133 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XXII и XXIII («Миграции базы: запреты, которые исполняет база, тождество в схеме, притязания на файлы, связность свидетельств, надзор за процессом и живой замер фабрики»: подразделы XXII, XXIIб–XXIIл и XXIII)** целиком (12 тест-сьютов, 111 тестов в едином пакетном прогоне в Docker Maven):
     * **Схема Flyway и запреты базы (4 теста, 1 сьют):**
       - `FlywayMigrationValidationTest` (4/4) — целостность цепочки миграций (V30–V138), валидация чексумм при старте (`validate-on-migrate=true`), запрет тихого примирения (`repair-on-startup=false`), фальсификация искажений схемы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Тождество в схеме и откат (8 тестов, 2 сьюта):**
       - `CompilerTaskIdentityTest` (1/1) — миграция V137: тождество задачи компилятора выводится из содержания работы (`tasks.content_key`), предотвращение тихого размножения задач (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `V25MigrationFalsificationTest` (7/7) — миграции V25/V33–V40: структурная нерушимость исторических миграций, фальсификация несанкционированного изменения схемы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Агентская схема (1 тест, 1 сьют):**
       - `AgencySchemaTest` (1/1) — миграция V59: изоляция агентских схем и демаркация ролевых границ (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Притязания на файлы и изоляция гонок (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (1/1), `ClaimServiceFalsificationTest` (10/10) — миграция V104: атомарный захват притязаний на задачи и файлы через `SELECT ... FOR UPDATE`, абсолютная защита от гонок и взаимных блокировок (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Связность свидетельств (21 тест, 1 сьют):**
       - `EvidenceCoherenceServiceTest` (21/21) — миграция V80: релаксация графа свидетельств по алгоритму ECHO Тагарда, запрет ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Надзор за процессом и Шесть сигм (11 тестов, 2 сьюта):**
       - `ProcessControlServiceTest` (8/8), `ProcessControlServiceFalsificationTest` (3/3) — миграция V89: статистический контроль Шесть сигм, карты Шухарта, строгое разграничение обычной вариации и истинного сдвига процесса (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Позвоночник потока (32 теста, 1 сьют):**
       - `FlowSpineServiceTest` (32/32) — миграция V66: наблюдаемость состояния проекта без мутаций (`build` vs `observe`), детерминированные переходы, идемпотентность событий по хешу свидетельства (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Защищенные инварианты схемы базы (15 тестов, 1 сьют):**
       - `Stage5ProtectedInvariantsFalsificationTest` (15/15) — защищенные инварианты V30, V42, V44, V45, V55, V82, V92, V97: непреложные деонтические запреты схемы базы данных (NOT NULL, UNIQUE, CHECK, FOREIGN KEY), защита от случайной регрессии (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при искажении чексумм Flyway (`FlywayMigrationValidationTest`), нарушении целостности миграций V25/V33–V40 (`V25MigrationFalsificationTest`), попытках двойного захвата файлов (`ClaimServiceRaceGuardTest`) и ложных зеленых вердиктах качества (`ProcessControlServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: тождество задачи компилятора выводится из содержания работы (`content_key`), исключая фантомное размножение задач (`CompilerTaskIdentityTest`); релаксация графа свидетельств ECHO опирается на подтвержденные факты доставки (`EvidenceCoherenceServiceTest`); снимки потока фиксируют неизменные события (`FlowSpineServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты на уровне схемы реляционной базы данных (NOT NULL, UNIQUE, CHECK, FOREIGN KEY) как неотменяемый исполняемый закон в миграциях V30, V42, V44, V45, V55, V82, V92, V97 (`Stage5ProtectedInvariantsFalsificationTest`); топологическая изоляция агентских структур (`AgencySchemaTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Shaffer & Varzi]:**
  * Монопольное транзакционное владение при захвате притязаний на задачи и файлы (`SELECT ... FOR UPDATE`), исключение гонок и взаимных блокировок в `TaskClaimServiceTest` и `ClaimServiceFalsificationTest`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `LYUDVIG_VITGENSHTEYN_01_FACT_STATE_TABLE` [D013 Runtime drift / Wittgenstein]:**
  * Карты Шухарта отделяют внутреннюю случайную вариацию от истинного сдвига производственного процесса (`ProcessControlServiceTest`); телеметрия потока в `FlowSpineServiceTest` не меняет состояние базы при вызове `build`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Dretske]:**
  * Телеосемантическая обратная связь: свидетельства доставки оцениваются по алгоритму ECHO Тагарда без ложной самоуспокоенности при неполных данных.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Семантическое разграничение категорий: тождество задачи отделено от временного идентификатора запуска в `CompilerTaskIdentityTest`.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXII и XXIII в Docker Maven (111 тестов, 12 сьютов):**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest
   ```
2. **Верификация результата:**
   - Строго **111/111 green** (12 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XXII и XXIII в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XXII и XXIII в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
