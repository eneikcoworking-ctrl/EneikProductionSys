# Директивы и наряд для L2 (2026-10-16 07:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущая Секция XXI («Сущности, пространства состояний и алгебра решений») успешно заверена инженером (коммит `ac79385`, 133/133 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующей архитектурной секции фабрики — **Секции XXII («Миграции: запреты, которые исполняет база», Слой 3, 137 миграций Flyway, подразделы XXII, XXIIб–XXIIл) и Секции XXIII («Живой замер фабрики, 7 сентября 2026»)** целиком (111 тестов, 12 тест-сьютов):
     * **Целостность схемы базы и запреты Flyway (4 теста, 1 сьют):**
       - `FlywayMigrationValidationTest` (4/4) — нерушимость цепочки миграций Flyway (V30–V126): `spring.flyway.validate-on-migrate=true` форсирован, `repair-on-startup=false` исключает тихое примирение поврежденных чексумм миграций; любая несанкционированная модификация примененной миграции фальсифицируется `FlywayValidateException` (D008 Popper & Tarski).
     * **Тождество в схеме и откат (8 тестов, 2 сьюта):**
       - `CompilerTaskIdentityTest` (1/1) — миграция V137: задача компилятора получает стабильное тождество из `content_key` вместо генерации фантомных дубликатов на каждом обороте декомпозиции (D010 Goldman, D002 Ryle).
       - `V25MigrationFalsificationTest` (7/7) — проверка и фальсификация цепочки V25/V33–V40, откат и починки схемы задач и источников wishlist (D008 Popper, D010 Goldman).
     * **Агентская схема и чистка (1 тест, 1 сьют):**
       - `AgencySchemaTest` (1/1) — схема V28, V29, V77 агентской модели, изоляция метаданных и удаление устаревших артефактов (D006 Raz & Varzi).
     * **Притязания на файлы и изоляция гонок (19 тестов, 3 сьюта):**
       - `TaskClaimServiceTest` (8/8), `ClaimServiceRaceGuardTest` (1/1), `ClaimServiceFalsificationTest` (10/10) — миграция V128: притязания на файлы и задачи, атомарность блокировок `SELECT ... FOR UPDATE`, исключение race condition при захвате задач агентами и воркерами, fail-closed семантика (D004 Varzi & Shaffer, D006 Raz, D008 Popper).
     * **Связность свидетельств (21 тест, 1 сьют):**
       - `EvidenceCoherenceServiceTest` (21/21) — миграция V80: релаксация графа свидетельств по алгоритму ECHO Тагарда, связность фактов доставки без ложной уверенности на пустых данных (D010 Goldman, D011 Dretske).
     * **Надзор за процессом и Шесть сигм (11 тестов, 2 сьюта):**
       - `ProcessControlServiceTest` (8/8), `ProcessControlServiceFalsificationTest` (3/3) — миграция V89: статистический контроль Шесть сигм, карты Шухарта, строгое разграничение обычной вариации и истинного сдвига процесса (D013 Wittgenstein, D008 Popper).
     * **Позвоночник потока (32 теста, 1 сьют):**
       - `FlowSpineServiceTest` (32/32) — миграция V66: наблюдаемость состояния проекта без мутаций (`build` vs `observe`), детерминированные переходы, идемпотентность событий по хешу свидетельства (D010 Goldman, D013 Wittgenstein).
     * **Защищенные инварианты схемы базы (15 тестов, 1 сьют):**
       - `Stage5ProtectedInvariantsFalsificationTest` (15/15) — защищенные инварианты V30, V42, V44, V45, V55, V82, V92, V97: непреложные деонтические запреты схемы базы данных, защита от случайной регрессии (D006 Raz, D008 Popper).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D011, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при искажении чексумм Flyway (`FlywayMigrationValidationTest`), нарушении целостности миграций V25/V33-V40 (`V25MigrationFalsificationTest`), попытках двойного захвата файлов (`ClaimServiceRaceGuardTest`) и ложных зеленых вердиктах качества (`ProcessControlServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: тождество задачи компилятора выводится из содержания работы (`content_key`), исключая фантомное размножение задач (`CompilerTaskIdentityTest`); релаксация графа свидетельств ECHO опирается на подтвержденные факты доставки (`EvidenceCoherenceServiceTest`); снимки потока фиксируют неизменные события (`FlowSpineServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
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

1. **Единый пакетный запуск тестов Секции XXII и XXIII в Docker Maven:**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest
   ```
2. **Верификация результата:**
   - Строго **111/111 green** (12 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секции XXII и XXIII в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секции XXII и XXIII в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
