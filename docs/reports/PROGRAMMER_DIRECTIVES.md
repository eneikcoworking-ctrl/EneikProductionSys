# Директивы и наряд для L2 (2026-10-16 15:08 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Реестр архитектурных предписаний L2: 59 предписаний» и «Цех дизайна») полностью заверены инженером (коммит `8f3931b`, 272/272 green, 0 строк изменений рабочего кода). Ранее Секции VII и VIII также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего сектора фабрики — **Кластера Секций XXII и XXIII («Миграции базы: запреты базы, тождество в схеме, притязания, связность свидетельств, надзор за процессом и живой замер фабрики»)** целиком (13 тест-сьютов, 116 тестов в едином пакетном прогоне в Docker Maven):
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
       - `Stage5ProtectedInvariantsFalsificationTest` (13/13): четырёхзначная семантика истинности и нерушимость защищенных инвариантов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D011, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация схемных инвариантов (`V25MigrationFalsificationTest`, `ClaimServiceFalsificationTest`, `ProcessControlServiceFalsificationTest`, `EvidenceCoherenceV80TerminationFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и релайабилистская цепь свидетельств: связность свидетельств в `EvidenceCoherenceServiceTest`, тождество задач компилятора в `CompilerTaskIdentityTest`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz & von Wright]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты базы данных через валидацию Flyway (`FlywayMigrationValidationTest`), недопустимость тихого искажения миграций.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Shaffer & Varzi]:**
  * Монопольное владение притязаниями на задачи и защита от гонок конкурентных агентов (`TaskClaimServiceTest`, `ClaimServiceRaceGuardTest`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Dretske]:**
  * Телеосемантическая обратная связь статистического надзора за процессом в `ProcessControlServiceTest`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырёхзначная логика истинности в защищенных инвариантах Ступени 5 (`Stage5ProtectedInvariantsFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineServiceTest`: физическое состояние базы и продвижение потока выше любых деклараций.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон 13 тест-сьютов Секций XXII и XXIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать результат (116/116 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(sections-xxii-xxiii): complete Stage 4 batch verification (D008, D010, D006)`.
