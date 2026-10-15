# Пакетная директива L1/L3 -> L2: Секция XXII и XXIII (Миграции, схема БД и онтологические запреты слоя 3)

**Дата:** 2026-10-15 20:20 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XXI («Сущности, пространства состояний и алгебра решений», 133/133 green в коммите `1d4ff46`) полностью заверена и закрыта инженером. Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секцию XXII («Миграции: запреты, которые исполняет база», подразделы XXII, XXIIб, XXIIв, XXIIг, XXIIд, XXIIе, XXIIж, XXIIз, XXIIи, XXIIк, XXIIл) и Секцию XXIII («Живой замер фабрики, 7 сентября 2026»)**:
   - **XXII. Миграции: запреты, которые исполняет база:** `V137__compiler_task_identity_from_work.sql` (детерминированное тождество задач компилятора от работы `content_key`, ограничение SHA-256), `V82__operational_reality_findings.sql` (расхождение отчета сессии и GitHub, ограничение ровно одного источника узла свидетельства), `V97__jules_session_pr_opened_workflow_claim.sql` (атомарное притязание завершения сессии), целостность цепочки миграций Flyway (`FlywayMigrationValidationTest`).
   - **XXIIб. Тождество в схеме:** `V132__return_the_two_requirements_the_grouper_took_from_V129.sql` (сохранение тождества требований), `V25__add_depends_on_and_hotspots.sql` (`V25MigrationFalsificationTest`: направленная мереология графа зависимостей `fk_tasks_depends_on`, каскад хотспотов `fk_hotspots_project`).
   - **XXIIв. Миграции, возвращающие отнятое:** `V19__restructure_accounts_and_projects.sql` (структурная изоляция аккаунтов и проектов, `AgencySchemaTest`), эволюция тождества фич `V42`, `V44`, `V45`, возврат бюджета задач `V126`.
   - **XXIIг. Изъятия из хранилища:** `V58` (удаление устаревших логов и вотермарок), `V121` (ликвидация ручного флага `needs_human_review`), `V123` (удаление висячих конфликтов без задачи).
   - **XXIIд. Наблюдатель (выключенное состояние):** `V59`, `V68`, `V111` (перманентное отключение Gemini observer, инертный стаб, безопасность схемы).
   - **XXIIе. Связность свидетельств:** `V80`, `V81` (`EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`: предел прогонов на проект, отсутствие утечек памяти).
   - **XXIIж. Надзор за процессом:** `V72`, `V85` (`ProcessControlServiceTest`, `ProcessControlServiceFalsificationTest`: контрольные карты Шухарта u-chart, правила Western Electric, залоченный бейзлайн).
   - **XXIIз. Притязания на файлы:** `V69`, `V99` (`TaskClaimServiceTest`, `ClaimServiceRaceGuardTest`, `ClaimServiceFalsificationTest`: топология файловых притязаний, изоляция веток конфликтов).
   - **XXIIи. Контекст исполнителя:** `V55`, `V62` (`GeminiContextServiceTest`: хеширование чанков контекста, дедупликация RAG).
   - **XXIIк. Приёмка заказчиком:** `V100__client_acceptance_traversals.sql` (`FlowSpineServiceTest`, `Stage5ProtectedInvariantsFalsificationTest`: демаркация сдачи клиенту `walked_by="client"`, инвариант `done_is_not_delivery`).
   - **XXIIл. Closure слоя миграций:** строгая демаркация 141 миграции Flyway на shape-only и behavior-changing, отсутствие дрейфа версий схемы.
   - **XXIII. Живой замер фабрики:** эмпирическая телеметрия рантайма Hetzner, статус базы данных PostgreSQL и здоровье контейнеров.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, миграций и конфигураций (`src/main/java`, `src/main/resources`) при проходящих тестах!
   - Механизмы Секции XXII и XXIII уже находятся в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper / Tarski]:**
  * Попперовская фальсификация: `FlywayMigrationValidationTest` гарантирует фальсификацию и аварийную остановку при модификации чексуммы примененной миграции (`FlywayValidateException`); `ProcessControlServiceFalsificationTest` фальсифицирует попытки пересчета базовых контрольных пределов в фазе дрейфа.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная цепь надежности: разделение формы схемы (shape-only) и поведенческих инвариантов (behavior-changing); строго воспроизводимый деноминатор миграций схемы без скрытого дрейфа версий.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сущностей, сервисов и миграций при проходящих тестах;**
  * Онтологические запреты схемы БД: `chk_evidence_nodes_exactly_one_source` (ровно один источник узла свидетельства), внешние ключи изоляции и каскадного удаления.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Boundary drift, Varzi]:**
  * Топология пространственных границ файловых притязаний (`V69`), монопольное владение задачами и зависимостями (`V25`).
- **`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` [D010 Parfit]:**
  * Непрерывность идентичности сущностей компилятора (`tasks.content_key` в V137) и фиксация снимков персистентности.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap]:**
  * Четырехзначная логика истинности в состояниях притязаний на сессии (`pr_opened_workflow_claimed_at`: NULL как допустимость повтора после отказа).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D013, D002 Wittgenstein / Ryle]:**
  * Эмпирическая телеметрия рантайма выше любых словесных отчетов; категорический запрет смешения готовности артефактов и клиентской приемки (`done_is_not_delivery`, V100).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XXII и XXIII в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlywayMigrationValidationTest,CompilerTaskIdentityTest,V25MigrationFalsificationTest,AgencySchemaTest,TaskClaimServiceTest,ClaimServiceRaceGuardTest,ClaimServiceFalsificationTest,EvidenceCoherenceServiceTest,ProcessControlServiceTest,ProcessControlServiceFalsificationTest,FlowSpineServiceTest,Stage5ProtectedInvariantsFalsificationTest`
2. Зафиксировать результаты в `*Живое:*` блоков Секции XXII и XXIII в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секции XXII и XXIII`, обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(migrations-and-schema): complete Stage 4 batch verification (D008, D010, D006)`.
