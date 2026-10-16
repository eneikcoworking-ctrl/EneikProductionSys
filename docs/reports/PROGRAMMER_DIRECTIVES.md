# Пакетная директива L1/L3 -> L2: Секции XIV, XV и XVI («Сверка с корпусом философии, тихое размножение задач и реестр предписаний»)

**Дата:** 2026-10-16 01:28 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий кластер (Секции XII, XIIа и XIII: «Журнал, память, санитария, управляющая поверхность API и карта владения состоянием») полностью заварен и закрыт инженером (коммит `d88b847`, 189/189 green в едином прогоне в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секции XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр архитектурных предписаний»)**:
   - **Секция XIV («Сверка с корпусом философских образцов»):**
     * `docs/philosopher-patterns` (XIV.1): корпус из 86 файлов, 1720 строк паттернов и 15 кодов дефектов как внешний словарь истины без галлюцинаций (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010);
     * `Defect-family application map` (XIV.2): демаркация семейств дефектов (D002, D004, D007, D008, D009, D012, D013, D014) без смешения уровней абстракции (`KIT_DEROUZ_13_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * `FACTORY_MECHANISMS grounding guard` (XIV.3): запрет на фантомные механизмы, строгая валидация требований и ссылочная целостность (`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
   - **Секция XV («Класс дефекта "тихое размножение задач": подпись, замер, случаи»):**
     * `TaskDuplicateDetector` (XV.1): категорическое разделение залипших дубликатов задач и скорости их генерации во времени (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * `ContinuousOrchestrationService.checkDuplicateGenerationVelocity` (XV.2): наблюдение скорости без деструктивного стопа, институциональная запись в журнал дефектов (`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007);
     * `PlannedWorkRecoveryService` (XV.3): безопасное восстановление залипших вишлистов и очистка мета-задач строго по критерию `ClientDeliverableReadiness` (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
     * `Compiler work identity & admission` (XV.4): тождество задач компилятора по контентному ключу (V137 `compile:<projectId>:<hash>`), вето семантических дубликатов, CAS-финализация и пространство имен Закона 26 (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
   - **Секция XVI («Предписания для L2: что и как чинить»):**
     * Полный реестр 59 архитектурных предписаний фабрики: деонтические запреты Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006), топология границ Варци (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006), четырехзначная истинность Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012), тянущий контур ТОС (`ELI_GOLDRATT_01_BOTTLENECK_FLOW` / D013) и институциональные факты Тарского (`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и репозиториев (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XIV, XV и XVI уже отлажены и покрыты заслонами. Задача инженера L2 — подтвердить нерушимость инвариантов строгим единым пакетным замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D007, D012, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификационные заслоны требований (`RequirementGroundingServiceFalsificationTest`), компилятора (`TechnicalLeadCompilerFalsificationTest`), вето дубликатов (`SemanticDuplicateVetoTest`) и изоляции пространства имен Закона 26 (`ProductNamespaceLaw26Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman & DeRose]:**
  * Удержание каузальной цепи свидетельств от корпуса философов до классов фабрики (`PhilosopherPatternCorpusConsistencyTest`); стабильность контентных ключей компилятора (`CompilerTaskIdentityTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity & boundary topology, Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов при проходящих тестах;**
  * Защита управляющих поверхностей (`InternalGeminiObserverSecurityIntegrationTest`, `SettingsControllerIntegrationTest`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state, Ryle]:**
  * Отсечение ошибки рода: разделение залипших задач и динамической скорости генерации (`TaskDuplicateDetectorTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` [D004 & D010, Shaffer]:**
  * Монопольное владение восстановлением состояния и тождеством задач компилятора (`PlannedWorkRecoveryServiceTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap]:**
  * Четырехзначная логика истинности и строгие статусы гейтов.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секций XIV, XV и XVI в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секций XIV, XV и XVI в `docs/FACTORY_MECHANISMS.md`, оформить блоки `### Итог Секции XIV`, `### Итог Секции XV` и общий итог кластера Секций XIV, XV и XVI, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(sections-xiv-xv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
