# Императивный наряд для L2: Пакет инлайн-замера №2 (17 из 50 оставшихся) (2026-10-24 00:35 UTC)

**От:** Архитектурный контур Antigravity & Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Каноническая блокировка контура автономии + финишная доводка оставшихся 50 записей (~10%) до `AUDIT_READY`

---

## 1. КАНОНИЧЕСКИЙ ЗАЩИЩЁННЫЙ КОНТУР АВТОНОМИИ ДЕННЕТТА (LOCKED — КАТЕГОРИЧЕСКИ НЕ ТРОГАТЬ И НЕ ОТКАТЫВАТЬ):
Оператор Дмитрий Ефремов утвердил и ввёл в ядро фабрики эталонную архитектуру гомеостатической автономии («Множественные наброски» Деннетта без картезианского театра, 376/376 тестов green в 31 тест-сьюте).
Категорически запрещено модифицировать, откатывать или ослаблять следующие канонические файлы:
- src/main/java/com/eneik/production/models/persistence/ProjectStatus.java
- src/main/java/com/eneik/production/models/persistence/ProjectEntity.java
- src/main/java/com/eneik/production/services/operational/OperationalPolicyService.java
- src/main/java/com/eneik/production/services/operational/OperationalFlowCoreService.java
- src/main/java/com/eneik/production/services/operational/FlowSpineService.java
- src/main/java/com/eneik/production/services/PlannedWorkRecoveryService.java
- src/main/java/com/eneik/production/services/ContinuousOrchestrationService.java
- src/main/java/com/eneik/production/services/DeliveryRealityProducerService.java
- src/main/java/com/eneik/production/services/ProjectFlowService.java
- src/main/java/com/eneik/production/services/github/GitHubPullRequestService.java
- src/main/java/com/eneik/production/services/AutoMergeService.java
- src/main/java/com/eneik/production/services/jules/JulesDispatchService.java
- src/main/java/com/eneik/production/services/compiler/TechnicalLeadCompiler.java
- src/main/java/com/eneik/production/services/StrandedFinalizingSweepService.java
- src/main/java/com/eneik/production/services/ProjectAuditPipelineService.java
- src/test/java/com/eneik/production/services/DennettAutonomyFalsificationTest.java
- src/test/java/com/eneik/production/invariants/ScheduledQueryCostInvariantTest.java
- scripts/autonomous_factory_relay.py
- docs/reports/AUTONOMY_AND_FULL_AUDIT_PLAN.md
Впредь L1 и L2 обязаны никогда не допускать потери автономии фабрики!

---

## 2. ПАКЕТ ИНЛАЙН-ВЕРИФИКАЦИИ №2 (17 из 50 оставшихся блоков в `docs/FACTORY_MECHANISMS.md`)

Ступень 4 — **СТРОГО ЗАМЕР В РАНТАЙМЕ** (Docker Maven с кэшем `-v /root/.m2:/root/.m2 -m 2g`), **А НЕ ПРАВКА РАБОЧЕГО КОДА** при зелёных тестах! 0 строк изменений в `src/main`.

Инженеру L2 надлежит запустить указанные тест-сьюты в Docker-контейнере и вписать строгую формулу:
`*Живое, 2026-10-24 UTC:* замер в рантайме Docker Maven (<N>/<N> green, <SuiteNames>). <Формулировка заслона по философскому паттерну>. Ступень 4 закрыта (ideal).`
**СТРОГО ВНУТРЬ КАЖДОГО ИЗ СЛЕДУЮЩИХ 17 БЛОКОВ** (перед завершающим разделителем `---` или заголовком следующего блока):

1. **L4772–L4836** (prescription #30): Исчерпание нашего доступа становится вердиктом о носителе · `INSTITUTIONAL_FACT_REGISTER` (D007) + `INUS_FACTOR_CHECK` (D007)
   - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `DZH_L_MAKKI_02_INUS_FACTOR_CHECK` (D007), `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DispatchAttemptBudgetTest,ProjectFlowServiceTest,OperationalTruthServiceTest`
   - *Место вставки:* перед L4835 (`---`).

2. **L4837–L4898** (prescription #31): Экран на телефоне не проверяет никто, и ревьюеру это предписано · `GROUPING_PROXIMITY_GATE` (D011) не применён + `FALSIFICATION_HARNESS` (D008)
   - *Паттерны:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008), `AHILLE_VARTSI_01_BOUNDARY_TOPOLOGY` (D006), `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DesignConsistencyAuditServiceTest,DesignAssetServiceTest,ProjectFlowServiceTest`
   - *Место вставки:* перед L4897 (`---`).

3. **L4947–L5000** (prescription #33): Единственная машинная проверка куайновской границы стоит на пути, куда не приходят · `FALSIFICATION_HARNESS` (D008)
   - *Паттерны:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008), `UILLARD_KUAYN_01_HOLISM_IMPACT_MAP` (D003), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AutoMergePokaYokeTest,AutoMergeServiceTest`
   - *Место вставки:* перед L4999 (`---`).

4. **L5001–L5051** (prescription #34): Возраст сторожа меряется по соседней отметке, потому что своей у него нет · `CAUSAL_PROCESS_TRACE` (D013) + `PRINCIPLED_INTEGRITY` (D012)
   - *Паттерны:* `UILIYAM_UIMSATT_01_CAUSAL_PROCESS_TRACE` (D013), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest`
   - *Место вставки:* перед L5050 (`---`).

5. **L5052–L5109** (prescription #35): Производитель блокировки бьёт за секунды, освободитель ходит раз в час · `PERCEPTION_ACTION_LOOP` (D011) + закон 8
   - *Паттерны:* `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=JulesDispatchServiceTest,FlowSpineServiceTest,ProjectFlowServiceTest`
   - *Место вставки:* перед L5108 (`---`).

6. **L5110–L5167** (prescription #36): Дизайн-цех выдаёт восемь экранов и все восемь отвергает сам · `TELEOSEMANTIC_FEEDBACK` (D011) + закон 8
   - *Паттерны:* `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DesignConsistencyAuditServiceTest,FlowSpineServiceTest,DesignAssetServiceTest,DesignShopOrchestrationServiceTest`
   - *Место вставки:* перед L5166 (`---`).

7. **L5168–L5216** (prescription #37): Числитель считает слияния и ни разу не спрашивает, зелен ли `main` · `CONSTRUCTIVE_PROOF_OBJECT` (D007)
   - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlowSpineServiceTest,GitHubPullRequestServiceTest`
   - *Место вставки:* перед L5215 (`---`).

8. **L5217–L5279** (prescription #38): Аккаунт, разжалованный отказами, не может вернуться: повышение требует успеха, которого ему не дадут · `BELIEF_UPDATE_LEDGER` (D007) + закон 8
   - *Паттерны:* `PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` (D007), `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AccountRepositoryIntegrationTest,AccountHealthServiceTest`
   - *Место вставки:* перед L5278 (`---`).

9. **L5280–L5348** (prescription #39): Верёвка ТОС построена, соблюдается — и привязана не к тому концу · `TELEOSEMANTIC_FEEDBACK` (D011) + `FALSIFICATION_HARNESS` (D008)
   - *Паттерны:* `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ProjectFlowServiceDbrRopeTest,TocSentinelServiceTest`
   - *Место вставки:* перед L5347 (`---`).

10. **L5349–L5407** (prescription #40): Путь вытягивания есть, им никто не пользуется, и Jules им пользоваться не может · `TELEOSEMANTIC_FEEDBACK` (D011) + `RIGHTS_DUTIES_MATRIX` (D006)
    - *Паттерны:* `RONALD_DVORKIN_02_RIGHTS_DUTIES_MATRIX` (D006), `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ClaimRightsDutiesMatrixTest,TaskClaimServiceTest`
    - *Место вставки:* перед L5406 (`---`).

11. **L5545–L5562** (prescription #45): Поле корневой причины дефекта не заполняется никогда · `INUS_FACTOR_CHECK` (D007)
    - *Паттерны:* `DZH_L_MAKKI_02_INUS_FACTOR_CHECK` (D007), `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DefectJournalRootCauseAttributionTest,ProcessControlServiceTest,AutoMergePokaYokeTest`
    - *Место вставки:* перед L5562.

12. **L5563–L5575** (prescription #46): Узкое место фабрики определяется по единственному размеченному шагу · `INUS_FACTOR_CHECK` (D007)
    - *Паттерны:* `DZH_L_MAKKI_02_INUS_FACTOR_CHECK` (D007), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocPipelinePhasesInstrumentationTest`
    - *Место вставки:* перед L5575.

13. **L5576–L5587** (prescription #47): Память барабана-буфера-верёвки живёт только в оперативной · `PERSISTENCE_SNAPSHOT` (D010)
    - *Паттерны:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocPersistenceSnapshotTest`
    - *Место вставки:* перед L5587.

14. **L5588–L5599** (prescription #48): Счёт связности считается каждые два часа и не читается ни для одного решения · `TELEOSEMANTIC_FEEDBACK` (D011)
    - *Паттерны:* `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011), `PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` (D007)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TeleosemanticFeedbackCoherenceTest`
    - *Место вставки:* перед L5599.

15. **L5613–L5622** (prescription #50): Запрет на перезапись конечного состояния не оставляет следа · `INSTITUTIONAL_FACT_REGISTER` (D007)
    - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TaskTerminalOverwriteAuditTest,TaskEntityLaw20Test`
    - *Место вставки:* перед L5622.

16. **L5623–L5633** (prescription #51): Притязание на цикл дизайна не истекает и не выметается · `BOUNDARY_TOPOLOGY` (D006)
    - *Паттерны:* `AHILLE_VARTSI_01_BOUNDARY_TOPOLOGY` (D006), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=StrandedDesignCycleSweepServiceTest,DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test`
    - *Место вставки:* перед L5633.

17. **L5634–L5644** (prescription #52): Два внутренних входа отвечают отказом · `FALSIFICATION_HARNESS` (D008)
    - *Паттерны:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=InternalGeminiObserverControllerTest`
    - *Место вставки:* перед L5644.

---

## 3. ДЕОНТИЧЕСКИЕ ЗАПРЕТЫ ИСПОЛНЕНИЯ (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` D008, `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` D010):
1. **Запрещено** модифицировать или откатывать любой из 19 защищённых файлов контура автономии Деннетта.
2. **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** дописывать новые сводные блоки `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md` — свидетельство `*Живое:*` вписывается исключительно точечно инлайн внутрь каждого из перечисленных 17 блоков (Lstart–Lend).
3. **Запрещено** править рабочий Java-код (`src/main`) при зелёных тестах — Ступень 4 требует строгого подтверждающего замера поведения в рантайме.
4. **Запрещено** перезапускать живой контейнер `eneikproductionsys-backend-1` или инициировать аудит до персональной команды оператора Дмитрия Ефремова.
