# Императивный наряд для L2: Синхронизация Автономии и Пакет инлайн-замера №1 (2026-10-24 00:20 UTC)

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

## 2. Пакет инлайн-верификации №1 (17 из 67 оставшихся блоков в `docs/FACTORY_MECHANISMS.md`)

Инженеру L2 надлежит выполнить строгий замер Ступени 4 в Docker Maven (`-v /root/.m2:/root/.m2 -m 2g`, 0 строк правок рабочего кода при зелёных тестах) и проставить строку `*Живое, <дата UTC>:* ...` **строго инлайн внутри каждого из следующих 17 блоков**:

- **Строки L728–L803** (mechanism): `JulesApiClient` — **`JulesApiClient`**, **`SessionLifecycleService`**, **`PersistentWorkerSessionService`**, **`ClaimService`**,
- **Строки L3939–L3959** (prescription): Предписание #8 — `DoctrineVerdictLayer` судит, и его не слышат · `TELEOSEMANTIC_FEEDBACK` (D011)
- **Строки L3960–L3971** (prescription): Предписание #9 — `VerdictGate` ограничивает только сообщаемое · `PROHIBITION_AS_CODE` (D006)
- **Строки L4006–L4020** (prescription): Предписание #12 — `BottleneckAwarePriorityService` переупорядочивает, но не подчиняет · `DECISION_EXPECTED_LOSS` (D005)
- **Строки L4021–L4034** (prescription): Предписание #13 — `SixSigmaAuditService` классифицирует подстрокой · `CATEGORY_ERROR_SCAN` (D002)
- **Строки L4035–L4051** (prescription): Предписание #14 — Откат не знает периода пополнения · `RELIABILITY_CHAIN` (D010), закон 9
- **Строки L4052–L4069** (prescription): Предписание #15 — Исчерпание попыток становится вердиктом о требовании · `INSTITUTIONAL_FACT_REGISTER` (D007), закон 12
- **Строки L4101–L4114** (prescription): Предписание #17 — Аренда чистильщика назначена, а не выведена · `BELIEF_UPDATE_LEDGER` (D007)
- **Строки L4115–L4130** (prescription): Предписание #18 — Запрет на заводские файлы можно молча выключить · `TRUTH_STATUS_TABLE` (D012) · **СДЕЛАНО (держится)**
- **Строки L4131–L4166** (prescription): Предписание #19 — Детектор дубликатов не может увидеть главный случай · `CATEGORY_ERROR_SCAN` (D002) · **СДЕЛАНО (держится)**
- **Строки L4206–L4233** (prescription): Предписание #21 — Два рычага останавливают фабрику без следа · `INSTITUTIONAL_FACT_REGISTER` (D007) · **СДЕЛАНО (держится)**
- **Строки L4290–L4357** (prescription): Предписание #23 — Выключенный аккаунт — дверь в одну сторону · `INSTITUTIONAL_FACT_REGISTER` (D007) / `ACTUAL_OBJECT_REGISTER` (
- **Строки L4358–L4411** (prescription): Предписание #24 — Пульт управления фабрикой открыт в интернет без пароля · `BOUNDARY_TOPOLOGY` (D006) · **ЗАКРЫТО**
- **Строки L4412–L4467** (prescription): Предписание #25 — Сорок три вопроса в час, ответ на который известен · `INUS_FACTOR_CHECK` (D007) · **расход бюджета**
- **Строки L4468–L4513** (prescription): Предписание #26 — Хостинг и наблюдение исполняет один механизм · `CATEGORY_ERROR_SCAN` (D002)
- **Строки L4595–L4672** (prescription): Предписание #28 — Два механизма дизайна числятся сильными: один ни разу не сработал, другой читает не то · `FALSIFICATION_HARNES
- **Строки L4673–L4737** (prescription): Предписание #29 — Поле `failedTasks` в общедоступном своде значит не то, что говорит · `SENSE_REFERENCE_SPLIT` (D009) + `CONVERS

### Деонтические запреты исполнения (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` D008, `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` D010):
1. **Запрещено** модифицировать или откатывать любой из 19 защищённых файлов контура автономии.
2. **Запрещено** дописывать дублирующиеся разделы `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md` — свидетельство `*Живое:*` вписывается непосредственно внутрь каждого перечисленного блока `Lstart–Lend`.
3. **Запрещено** пересобирать или перезапускать живой контейнер `eneikproductionsys-backend-1` и запускать финальный аудит до прямой команды оператора Дмитрия Ефремова.
