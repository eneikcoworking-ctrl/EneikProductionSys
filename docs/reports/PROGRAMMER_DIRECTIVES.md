# Пакетная директива L1/L3 -> L2: Секция XXI / XXIб / XXII (Пространства состояний, поведение сущностей, санитария финализации и инварианты схемы базы данных)

**Дата:** 2026-10-15 14:12 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XVI/XXIX (Архитектурные предписания инвариантов владения и Цех дизайна) полностью замерена и зафиксирована инженером в статусе ideal (коммит `30d89fd`, 290/290 green в Docker Maven). Настоящая директива охватывает целиком следующий открытый комплекс фабрики — **Секцию XXIб (Пространства состояний и демаркация неизвестного)**, **Секцию XXI (Сущности, несущие поведение, и санитарные sweep-сервисы)** и **Секцию XXII (Инварианты схемы миграций базы данных)**:
   - **Пространства состояний (Секция XXIб, D012 Belnap, D010 Goldman):**
     * `AccountStatus` (`active`, `decommissioned`, `offline`, `daily_limited`) — конъюнкция емкости и статусов доступности диспетчеризации Jules-аккаунтов;
     * `ProjectStatus` (`active`, `paused`, `accepted`, `archived`) — институциональная таблица истинности состояний проектов, отсечение недопустимых переходов;
     * `TaskStatus` (`todo`, `in_progress`, `review`, `done`, `failed`, `cancelled`) — матрица жизненного цикла задач, терминальная неизменность `writeStatusUnlessTerminalAt`;
     * `Judgement` (`COMPLIANT`, `NON_COMPLIANT`, `UNDETERMINED`, `NOT_APPLICABLE`) — четырехзначная решетка суждений Белнапа, демаркация отсутствия измерения от соответствия;
     * `TocNode` / `TocEdge` — топология графа ТОС, демаркация шагов потока, буферных ограничений и емкости in-flight.
   - **Сущности и санитария финализации (Секции XXI, XII, XL, D004 Shaffer, D008 Popper):**
     * `DefectJournalEntity` / `LogScopeBuffer` — журнал дефектов, MDC-скопы контекстов (`PROJECT:{id}` против `SYSTEM`), защита от утечек логов;
     * `StrandedFinalizingSweepService` — безопасный автоматический сбор зависших финализаций `finalizing → compiling`, предотвращение блокировок конвейера;
     * `EneikProductionApplication` — валидация миграций при старте, запрет самовольного Flyway `repair()`.
   - **Инварианты схемы и миграции базы данных (Секция XXII, D006 Raz, D008 Popper):**
     * `V19__restructure_accounts_and_projects.sql` — изоляция пула аккаунтов по активному проекту;
     * `V21__add_file_scope_and_conflicts.sql` — матрица файловых конфликтов задач;
     * `V58__drop_project_event_log_and_watermark.sql` — необратимая санитария устаревших сущностей логов;
     * `V62__add_context_chunk_content_hash.sql` — детерминированный хэш содержимого для чанков контекста RAG;
     * `V80__coherence_runs.sql` — связность свидетельств и сохранение прогонов;
     * `V111__permanently_disable_gemini_project_observer.sql` — перманентное отключение устаревшего Gemini-наблюдателя;
     * `V132__return_the_two_requirements_the_grouper_took_from_V129.sql` — целостность требований вишлиста;
     * `V137__compiler_task_identity_from_work.sql` — тождество задач компилятора из единицы работы;
     * `V15__create_project_final_reports.sql` — долговечные финальные отчеты проектов;
     * `append_role_logic.py` — консистентность правил расширения логики ролей.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов, сущностей и миграций при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего кода признается нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсифицирующий заслон целостности миграций: тест `FlywayMigrationValidationTest` немедленно падает при любой модификации контрольных сумм примененных миграций;
  * Фальсифицирующий заслон `StrandedFinalizingSweepServiceFalsificationTest` подтверждает обнаружение и сброс зависших блокировок требований без потери данных;
  * Заслоны `AccountStatusConjunctionTest` и `ProjectStatusTruthTableTest` падают при несанкционированном переходе статусов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции пространств состояний: факт отсутствия свидетельства (Белнап `UNDETERMINED`) не может неявно сворачиваться в утвердительный результат (`COMPLIANT` / `done`);
  * Разделение схемы базы данных и рантайм-проекций: миграции Flyway фиксируют строгие реляционные ограничения (NOT NULL, FK, UNIQUE), исключающие появление невалидных состояний.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов, моделей перечислений и SQL-миграций Секций XXI, XXIб и XXII;**
  * Деонтический запрет на обход проверки миграций через `repair()` на старте (`FlywayMigrationValidationTest`);
  * Запрет мутаций терминальных состояний сущностей вне CAS-предикатов.

---

## 3. Пакетный наряд по механизмам Секции XXI / XXIб / XXII

1. **Пространства состояний и демаркация неизвестного (`AccountStatus`, `ProjectStatus`, `TaskStatus`, `Judgement`, `TocNode`, `TocEdge`):**
   - **Проба:** Замерить замкнутость пространств состояний, отсечение невалидных переходов, многозначность Белнапа и инварианты терминальности.
   - **Инвариант:** Рабочий код не трогать. Заслоны `AccountStatusConjunctionTest`, `ProjectStatusTruthTableTest`, `AccountLifecycleInvariantTest`, `Stage5ProtectedInvariantsFalsificationTest`.
2. **Поведение сущностей и санитария финализации (`DefectJournalEntity`, `LogScopeBuffer`, `StrandedFinalizingSweepService`, `EneikProductionApplication`):**
   - **Проба:** Замерить MDC-демаркацию логов, безопасную очистку зависших блокировок требований и валидацию запуска приложения без repair.
   - **Инвариант:** Рабочий код не трогать. Заслоны `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `LogScopeBufferTest`, `FlywayMigrationValidationTest`.
3. **Инварианты схемы и миграции базы данных (`V19`, `V21`, `V58`, `V62`, `V80`, `V111`, `V132`, `V137`, `V15`, `append_role_logic.py`):**
   - **Проба:** Замерить неизменность примененных миграций, валидацию Flyway, изоляцию пулов аккаунтов и корректность структуры данных.
   - **Инвариант:** SQL-миграции и скрипты не трогать. Заслоны `FlywayMigrationValidationTest`, `V25MigrationFalsificationTest`, `V19AccountPoolAndSingleActiveProjectFalsificationTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов Секций XXI / XXIб / XXII в Docker Maven (`-m 2g`).
2. Внести отметку `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(state-spaces): complete Section XXI/XXIб/XXII batch verification (D008, D010, D006)`.
