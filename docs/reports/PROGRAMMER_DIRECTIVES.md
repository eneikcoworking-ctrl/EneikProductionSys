# Пакетная директива L1/L3 -> L2: Секция XXII и XXIII — Миграции, схема БД, онтологические запреты и живой замер фабрики

**Дата:** 2026-10-16 04:26 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XXI («Сущности, несущие поведение, пространства состояний и алгебра решений», 133/133 green, коммит `78d4aba`) полностью заверена и закрыта инженером без единой строки правок рабочего кода. Настоящая директива охватывает целиком следующий открытый масштабный контур фабрики — **Секцию XXII («Миграции: запреты, которые исполняет база», подразделы XXII, XXIIб, XXIIв, XXIIг, XXIIд, XXIIе, XXIIж, XXIIз, XXIIи, XXIIк, XXIIл) и Секцию XXIII («Живой замер фабрики, 7 сентября 2026»)**:
   - **Контур XXII (Миграции и запреты базы):** `V137__compiler_task_identity_from_work.sql` (детерминированное тождество задач компилятора от работы `content_key`, ограничение SHA-256), `V82__operational_reality_findings.sql` (расхождение отчета сессии и GitHub, ограничение ровно одного источника узла свидетельства), `V97__jules_session_pr_opened_workflow_claim.sql` (атомарное притязание завершения сессии), нерушимость чексумм цепочки Flyway (`FlywayMigrationValidationTest`).
   - **Контур XXIIб (Тождество в схеме):** `V132__return_the_two_requirements_the_grouper_took_from_V129.sql` (сохранение тождества требований), `V25__add_depends_on_and_hotspots.sql` (`V25MigrationFalsificationTest`: направленная мереология графа зависимостей `fk_tasks_depends_on`, каскад хотспотов `fk_hotspots_project`).
   - **Контур XXIIв (Миграции, возвращающие отнятое):** `V19__restructure_accounts_and_projects.sql` (структурная изоляция пула аккаунтов и проектов, `AgencySchemaTest`), эволюция тождества фич `V42`, `V44`, `V45`, возврат бюджета задач `V126`.
   - **Контур XXIIг (Изъятия из хранилища навсегда):** `V58` (удаление устаревших логов и вотермарок), `V121` (ликвидация ручного флага `needs_human_review`), `V123` (удаление висячих конфликтов без задачи).
   - **Контур XXIIд (Наблюдатель — выключенное состояние):** `V59`, `V68`, `V111` (перманентное отключение Gemini observer, инертный стаб, безопасность схемы).
   - **Контур XXIIе (Связность свидетельств):** `V80`, `V81` (`EvidenceCoherenceServiceTest`: предел прогонов на проект, отсутствие утечек памяти, строгая когерентность).
   - **Контур XXIIж (Надзор за процессом):** `V72`, `V85` (`ProcessControlServiceTest`, `ProcessControlServiceFalsificationTest`: контрольные карты Шухарта u-chart, правила Western Electric, залоченный бейзлайн).
   - **Контур XXIIз (Притязания на файлы):** `V69`, `V99` (`TaskClaimServiceTest`, `ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`: топология файловых притязаний, изоляция веток конфликтов).
   - **Контур XXIIи (Контекст исполнителя):** `V55`, `V62` (хеширование чанков контекста, дедупликация RAG).
   - **Контур XXIIк (Приёмка заказчиком):** `V100__client_acceptance_traversals.sql` (`FlowSpineServiceTest`, `Stage5ProtectedInvariantsFalsificationTest`: демаркация сдачи клиенту `walked_by="client"`, инвариант `done_is_not_delivery`).
   - **Контур XXIIл (Closure слоя миграций):** строгая демаркация всех 141 миграций Flyway на shape-only и behavior-changing, валидация миграций на старте.
   - **Контур XXIII (Живой замер фабрики):** эмпирическая телеметрия рантайма Hetzner, статус базы данных PostgreSQL и здоровье контейнеров.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код миграций, сущностей, сервисов и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов и SQL-файлов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D004, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: падение проверок при попытке тихой правки примененных Flyway-миграций (`FlywayMigrationValidationTest`), при нарушении целостности внешних ключей зависимостей задач (`V25MigrationFalsificationTest`), при симуляции гонок за клейм файла (`ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: тождество задач компилятора через SHA-256 хеш работы (`CompilerTaskIdentityTest`); разделение контекстов пула аккаунтов и проектов (`AgencySchemaTest`); сохранение непрерывности идентичности Парфита (`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет на уровне базы данных: ограничения внешних ключей, NOT NULL, CHECK-констрейнты и запреты множества недопустимых состояний как действующий закон фабрики.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Varzi]:**
  * Топология файловых притязаний и монополия владения путями проекта в `TaskClaimServiceTest`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная логика истинности в статусах притязаний и когерентности свидетельств (`EvidenceCoherenceServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Принцип анти-зеркала: физическое состояние базы данных и рантайм Actuator UP превыше любого нарратива.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XXII и XXIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest
   ```
2. Подтвердить статус `ideal` в блоке `### Итог Секции XXII и XXIII` в `docs/FACTORY_MECHANISMS.md`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(migrations-and-schema): complete Stage 4 batch verification (D008, D010, D006)`.
