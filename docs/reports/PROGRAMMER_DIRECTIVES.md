# Пакетная директива L1/L3 -> L2: Кластер Секций XII, XIIа и XIII — Журнал, санитария, управляющая поверхность API и карта владения состоянием

**Дата:** 2026-10-16 03:47 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий стратегический кластер Секций IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента») успешно заварен, подтвержден и закрыт инженером (коммит `b404dbe`, 280/280 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Кластер Секций XII, XIIа и XIII («Журнал, память, санитария», «Управляющая поверхность: чем поток останавливают снаружи» и «Взаимодействие: кто пишет каждое состояние»)**:
   - **Контур XII: Журнал, память, санитария (64 теста, 100% green):**
     * Механизмы: `LogScopeBuffer`, `LogScopeAndDefectJournalInusFalsification`, `ScopedBufferAppenderFalsification`, `ProjectEventLogRetentionService`, `SystemSettingsService`, `RoleCapabilityLoader`, `ProjectAuditPipelineService`, `IdleProjectAdviceService`, `RoleAdviceLoopService`, `RoleAdviceLoopServiceIntegration`.
   - **Контур XIIа: Управляющая поверхность API (74 теста, 100% green):**
     * Механизмы: `GreetingControllerIntegration`, `LinearSyncController`, `InternalTaskController`, `AccountControllerIntegration`, `SettingsControllerIntegration`, `WishlistControllerIntegration`, `GithubWebhookController`, `SystemStatusController`, `DashboardController`, `OperationalPolicyDeniedExceptionFalsification`, `ClaimRightsDutiesMatrix`, `ClaimResultStatusTruthTableFalsification`, `ClaimServiceRaceGuard`.
   - **Контур XIII: Карта владения состоянием фабрики (51 тест, 100% green):**
     * Механизмы: `SessionLifecycleService`, `SessionLifecycleServiceFalsification`, `StrandedFinalizingSweepService`, `StrandedFinalizingSweepServiceFalsification`, `AutoMergeLaw20InvariantS4`, `AccountRepositoryFalsification`, `AccountRepositoryIntegration`, `TaskRepositoryIntegration`.
   - **Итоговый единый пакетный замер кластера:** 189 тестов, 100% green в едином непрерывном прогоне Docker Maven 3.9.9 Temurin-21 (-m 2g).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, контроллеров и репозиториев (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D007, D011, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация заслонов: `LogScopeAndDefectJournalInusFalsificationTest`, `ScopedBufferAppenderFalsificationTest`, `OperationalPolicyDeniedExceptionFalsificationTest`, `AutoMergeLaw20InvariantS4Test`, `StrandedFinalizingSweepServiceFalsificationTest`, `SessionLifecycleServiceFalsificationTest`, `AccountRepositoryFalsificationTest` не ищут подтверждений («код написан»), а доказывают способность заслона упасть при нарушении инвариантов памяти, неавторизованном доступе, попытке мутации завершенной задачи (Law 20) или дрейфе аренды финализации.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: `ProjectEventLogRetentionServiceTest` удерживает журнал проекта без OOM по жесткой границе `PageRequest.of(excess - 1, 1)`, отделяя оперативные логи от архивных; `WishlistControllerIntegrationTest` отделяет архивирование требований (dismiss) от физического удаления; `SystemSettingsServiceTest` валидирует ключи настроек и маскирует секреты.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как код в топологии системы: демаркация управляющей поверхности API (`AccountControllerIntegrationTest`, `SettingsControllerIntegrationTest`, `ClaimRightsDutiesMatrixTest`) и топологическая изоляция монопольного владения состоянием (`Factory Writable State Owner-Map`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `MARGARET_GILBERT_02_JOINT_COMMITMENT_LOCK` [D004 Boundary drift / Shaffer & Gilbert]:**
  * Монопольное владение частями целого и совместное обязательство: каждый репозиторий фабрики закреплен за единственным сервисом-владельцем; `SessionLifecycleService` монопольно управляет жизненным циклом Jules-сессий, исключая гонки и рассинхронизацию.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная логика истинности в `ClaimResultStatusTruthTableFalsificationTest` исключает неоднозначность review/merge переходов.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секций XII, XIIа и XIII в Docker Maven (189 тестов green):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,GreetingControllerIntegrationTest,LinearSyncControllerTest,InternalTaskControllerTest,AccountControllerIntegrationTest,SettingsControllerIntegrationTest,WishlistControllerIntegrationTest,GithubWebhookControllerTest,SystemStatusControllerTest,DashboardControllerTest,OperationalPolicyDeniedExceptionFalsificationTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,ClaimServiceRaceGuardTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,AutoMergeLaw20InvariantS4Test,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskRepositoryIntegrationTest
   ```
2. Подтвердить статус `ideal` в блоках `### Итог Секции XII`, `### Итог Секции XIIа`, `### Итог Секции XIII` и общего блока `### Итог кластера Секций XII, XIIа и XIII` в `docs/FACTORY_MECHANISMS.md`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(logging-control-and-ownership): complete Stage 4 batch verification (D008, D010, D006)`.
