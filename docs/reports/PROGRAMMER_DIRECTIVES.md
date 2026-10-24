# Императивный наряд для L2: Синхронизация Автономии и Пакет инлайн-замера №1 (2026-10-24 00:25 UTC)

**От:** Архитектурный контур Antigravity & Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Каноническая блокировка контура автономии + финишная доводка оставшихся 67 записей (~10%) до `AUDIT_READY`

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

## 2. ПАКЕТ ИНЛАЙН-ВЕРИФИКАЦИИ №1 (17 из 67 оставшихся блоков в `docs/FACTORY_MECHANISMS.md`)

Ступень 4 — **СТРОГО ЗАМЕР В РАНТАЙМЕ** (Docker Maven с кэшем `-v /root/.m2:/root/.m2 -m 2g`), **А НЕ ПРАВКА РАБОЧЕГО КОДА** при зелёных тестах! 0 строк изменений в `src/main`.

Инженеру L2 надлежит запустить указанные тест-сьюты в Docker-контейнере и вписать строгую формулу:
`*Живое, 2026-10-24 UTC:* замер в рантайме Docker Maven (<N>/<N> green, <SuiteNames>). <Формулировка заслона по философскому паттерну>. Ступень 4 закрыта (ideal).`
**СТРОГО ВНУТРЬ КАЖДОГО ИЗ СЛЕДУЮЩИХ 17 БЛОКОВ** (перед завершающим разделителем `---` или заголовком следующего механизма):

1. **L728–L803** (mechanism): `JulesApiClient` — **`JulesApiClient`**, **`SessionLifecycleService`**, **`PersistentWorkerSessionService`**, **`ClaimService`**, `LeaseWatchdogService`, `AccountHealthService`, `BottleneckAwarePriorityService`, `BottleneckDetectionService`
   - *Паттерны:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008), `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=JulesApiClientTest,JulesRefusalKindsTest,SessionLifecycleServiceTest,AccountHealthServiceLaw14Test,DispatchRefusalObservabilityLaw8Law12Test,DeadDependencyEndsTheWaitTest,LeaseWatchdogServiceTest,LeaseWatchdogServiceFalsificationTest,BottleneckAwarePriorityServiceTest,BottleneckDetectionServiceTest`
   - *Место вставки:* перед L804 (`**LeaseWatchdogService**`).

2. **L3939–L3959** (prescription #8): `DoctrineVerdictLayer` судит, и его не слышат · `TELEOSEMANTIC_FEEDBACK` (D011)
   - *Паттерны:* `RUT_MILLIKEN_01_TELEOSEMANTIC_FEEDBACK` (D011), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AutonomousVerdictObservationServiceTest,VerdictReconciliationTest`
   - *Место вставки:* перед L3958 (`---`).

3. **L3960–L3971** (prescription #9): `VerdictGate` ограничивает только сообщаемое · `PROHIBITION_AS_CODE` (D006)
   - *Паттерны:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=VerdictGateTest`
   - *Место вставки:* перед L3970 (`---`).

4. **L4006–L4020** (prescription #12): `BottleneckAwarePriorityService` переупорядочивает, но не подчиняет · `DECISION_EXPECTED_LOSS` (D005)
   - *Паттерны:* `FRENK_RAMSEY_01_DECISION_EXPECTED_LOSS` (D005)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=BottleneckAwarePriorityServiceTest`
   - *Место вставки:* перед L4019 (`---`).

5. **L4021–L4034** (prescription #13): `SixSigmaAuditService` классифицирует подстрокой · `CATEGORY_ERROR_SCAN` (D002)
   - *Паттерны:* `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=SixSigmaAuditServiceTest`
   - *Место вставки:* перед L4033 (`---`).

6. **L4035–L4051** (prescription #14): Откат не знает периода пополнения · `RELIABILITY_CHAIN` (D010), закон 9
   - *Паттерны:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AccountHealthServiceLaw14Test,AccountHealthServiceTest`
   - *Место вставки:* перед L4050 (`---`).

7. **L4052–L4069** (prescription #15): Исчерпание попыток становится вердиктом о требовании · `INSTITUTIONAL_FACT_REGISTER` (D007), закон 12
   - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DispatchAttemptBudgetTest,DispatchRefusalObservabilityLaw8Law12Test`
   - *Место вставки:* перед L4068 (`---`).

8. **L4101–L4114** (prescription #17): Аренда чистильщика назначена, а не выведена · `BELIEF_UPDATE_LEDGER` (D007)
   - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest`
   - *Место вставки:* перед L4113 (`---`).

9. **L4115–L4130** (prescription #18): Запрет на заводские файлы можно молча выключить · `TRUTH_STATUS_TABLE` (D012) · **СДЕЛАНО (держится)**
   - *Паттерны:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
   - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DeliveryRealityLaw2CarrierChannelTest,GitHubPullRequestServiceTest`
   - *Место вставки:* перед L4129 (`---`).

10. **L4131–L4166** (prescription #19): Детектор дубликатов не может увидеть главный случай · `CATEGORY_ERROR_SCAN` (D002) · **СДЕЛАНО (держится)**
    - *Паттерны:* `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TaskDuplicateDetectorTest,FlowSpineServiceTest,ContinuousOrchestrationServiceTest`
    - *Место вставки:* перед L4165 (`---`).

11. **L4206–L4233** (prescription #21): Два рычага останавливают фабрику без следа · `INSTITUTIONAL_FACT_REGISTER` (D007) · **СДЕЛАНО (держится)**
    - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=SystemSettingsServiceTest,SettingsControllerIntegrationTest,AccountLifecycleInvariantTest,AccountControllerIntegrationTest`
    - *Место вставки:* перед L4232 (`---`).

12. **L4290–L4357** (prescription #23): Выключенный аккаунт — дверь в одну сторону · `INSTITUTIONAL_FACT_REGISTER` (D007) / `ACTUAL_OBJECT_REGISTER` (D002)
    - *Паттерны:* `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` (D007), `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=AccountLifecycleInvariantTest,GeneralPoolAdmissionCoherenceIntegrationTest,AccountHealthServiceTest,ProjectFlowServiceLaw1JulesDispatchTest`
    - *Место вставки:* перед L4356 (`---`).

13. **L4358–L4411** (prescription #24): Пульт управления фабрикой открыт в интернет без пароля · `BOUNDARY_TOPOLOGY` (D006) · **ЗАКРЫТО**
    - *Паттерны:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006), `AHILLE_VARTSI_01_BOUNDARY_TOPOLOGY` (D006)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ApiAuthorizationInterceptorTest,WebConfigCorsTest,SystemSettingsServiceTest`
    - *Место вставки:* перед L4410 (`---`).

14. **L4412–L4467** (prescription #25): Сорок три вопроса в час, ответ на который известен · `INUS_FACTOR_CHECK` (D007) · **расход бюджета**
    - *Паттерны:* `DZH_L_MAKKI_02_INUS_FACTOR_CHECK` (D007), `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ProductCapabilityServiceTest,SixSigmaAuditServiceTest`
    - *Место вставки:* перед L4466 (`---`).

15. **L4468–L4513** (prescription #26): Хостинг и наблюдение исполняет один механизм · `CATEGORY_ERROR_SCAN` (D002)
    - *Паттерны:* `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` (D002)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ObservationHostingDemarcationLaw26Test,ClientRuntimeObservabilityServiceTest`
    - *Место вставки:* перед L4512 (`---`).

16. **L4595–L4672** (prescription #28): Два механизма дизайна числятся сильными: один ни разу не сработал, другой читает не то · `FALSIFICATION_HARNESS` (D008) + `LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Паттерны:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008), `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DesignConsistencyAuditServiceTest,DesignDriftMonitorServiceTest,DesignAssetServiceTest`
    - *Место вставки:* перед L4671 (`---`).

17. **L4673–L4737** (prescription #29): Поле `failedTasks` в общедоступном своде значит не то, что говорит · `SENSE_REFERENCE_SPLIT` (D009) + `CONVERSATION_MAXIM` (D007)
    - *Паттерны:* `DEVID_CHALMERS_05_SENSE_REFERENCE_SPLIT` (D009), `POL_GRAYS_01_CONVERSATION_MAXIM` (D007)
    - *Тест-сьют:* `docker run --rm -v /root/.m2:/root/.m2 -v $(pwd):/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FlowSpineServiceTest`
    - *Место вставки:* перед L4736 (`---`).

---

## 3. ДЕОНТИЧЕСКИЕ ЗАПРЕТЫ ИСПОЛНЕНИЯ (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` D008, `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` D010):
1. **Запрещено** модифицировать или откатывать любой из 19 защищённых файлов контура автономии Деннетта.
2. **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** дописывать новые сводные блоки `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md` — свидетельство `*Живое:*` вписывается исключительно точечно инлайн внутрь каждого из перечисленных 17 блоков.
3. **Запрещено** править рабочий Java-код (`src/main`) при зелёных тестах — Ступень 4 требует строгого подтверждающего замера поведения в рантайме.
4. **Запрещено** перезапускать живой контейнер `eneikproductionsys-backend-1` или инициировать аудит до персональной команды оператора Дмитрия Ефремова.
