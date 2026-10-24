# Императивный наряд для L2: Финальный пакет инлайн-замера №4 (16 из 16 оставшихся) (2026-10-24 00:56 UTC)

**От:** Архитектурный контур Antigravity & Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Каноническая блокировка контура автономии + завершающая доводка финальных 16 блоков до 100% `AUDIT_READY`

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

## 2. ФИНАЛЬНЫЙ ПАКЕТ ИНЛАЙН-ВЕРИФИКАЦИИ №4 (16 из 16 оставшихся блоков в `docs/FACTORY_MECHANISMS.md`)

Ступень 4 — **СТРОГО ЗАМЕР В РАНТАЙМЕ** (Docker Maven с кэшем `-v /root/.m2:/root/.m2 -m 2g`), **А НЕ ПРАВКА РАБОЧЕГО КОДА** при зелёных тестах! 0 строк изменений в `src/main`.
КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО дописывать новые сводные блоки `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md` — только точечная инлайн-простановка `*Живое:*` строго внутри перечисленных блоков перед их завершением!

Инженеру L2 надлежит подтвердить результаты тест-сьютов в Docker-контейнере Maven (замерено L1/L3: 88/88 green в 13 тест-сьютах) и вписать строгую формулу свидетельства:
`*Живое, 2026-10-24 UTC:* Замер Ступени 4 в Docker Maven 3.9.9 Temurin-21 (-m 2g): <N>/<N> green в <SuiteNames>. <Формулировка заслона по философскому паттерну>. Ступень 4 закрыта, статус: ideal.`
**СТРОГО ВНУТРЬ КАЖДОГО ИЗ СЛЕДУЮЩИХ 16 БЛОКОВ**:

1. **L7975–L8025**: `KaizenService.scanForOpportunities/recordUnderTheHoodDefects`
   - *Паттерн:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `KaizenServiceTest` (10/10 green)
   - *Место вставки:* перед строкой 8025 (в конце блока `KaizenService.scanForOpportunities/recordUnderTheHoodDefects`).

2. **L8026–L8074**: `KaizenService.applyMicroStep/evaluateAndStandardize/periodicKaizenCycle`
   - *Паттерн:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `KaizenServiceTest` (10/10 green)
   - *Место вставки:* перед строкой 8074 (в конце блока `KaizenService.applyMicroStep/evaluateAndStandardize/periodicKaizenCycle`).

3. **L8075–L8103**: `DesignShopCycleEntity`
   - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
   - *Тест-сьюты:* `DesignShopCycleRepositoryIntegrationTest` (6/6 green), `DesignShopOrchestrationServiceLaw15Test` (7/7 green)
   - *Место вставки:* перед строкой 8103 (в конце блока `DesignShopCycleEntity`).

4. **L8104–L8125**: `DesignShopCycleRepository`
   - *Паттерн:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
   - *Тест-сьюты:* `DesignShopCycleRepositoryIntegrationTest` (6/6 green), `DesignShopOrchestrationServiceTest` (13/13 green)
   - *Место вставки:* перед строкой 8125 (в конце блока `DesignShopCycleRepository`).

5. **L8126–L8155**: `DesignShopOrchestrationService`
   - *Паттерн:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006), `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьюты:* `DesignShopOrchestrationServiceTest` (13/13 green), `DesignShopOrchestrationServiceLaw15Test` (7/7 green)
   - *Место вставки:* перед строкой 8155 (в конце блока `DesignShopOrchestrationService`).

6. **L8156–L8181**: `DesignSystemFalsificationService`
   - *Паттерн:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
   - *Тест-сьют:* `DesignSystemFalsificationServiceTest` (6/6 green)
   - *Место вставки:* перед строкой 8181 (в конце блока `DesignSystemFalsificationService`).

7. **L8182–L8232**: `DesignDriftMonitorService`
   - *Паттерны:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008), `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012)
   - *Тест-сьюты:* `DesignDriftMonitorServiceTest` (7/7 green), `DesignConsistencyAuditServiceTest` (15/15 green)
   - *Место вставки:* перед строкой 8232 (в конце блока `DesignDriftMonitorService`).

8. **L8323–L8345**: `TaskEntity.initializeStatus`
   - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
   - *Тест-сьюты:* `TaskEntityLaw20Test` (6/6 green), `TaskAcceptanceCriteriaGuardTest` (1/1 green), `TaskEntityLaw1CarrierTest` (5/5 green)
   - *Место вставки:* перед строкой 8345 (в конце блока `TaskEntity.initializeStatus`).

9. **L8346–L8366**: `TaskRepository.compareAndSetStatus`
   - *Паттерн:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
   - *Тест-сьюты:* `TaskRepositoryIntegrationTest` (4/4 green), `TaskTerminalOverwriteAuditTest` (4/4 green)
   - *Место вставки:* перед строкой 8366 (в конце блока `TaskRepository.compareAndSetStatus`).

10. **L8367–L8412**: `TaskRepository.writeStatusUnlessTerminal`
    - *Паттерн:* `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
    - *Тест-сьюты:* `TaskRepositoryIntegrationTest` (4/4 green), `TaskTerminalOverwriteAuditTest` (4/4 green)
    - *Место вставки:* перед строкой 8412 (в конце блока `TaskRepository.writeStatusUnlessTerminal`).

11. **L8770–L8790**: `V42__add_pr_review_has_code_and_role_threads.sql`
    - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `FlywayMigrationValidationTest` (4/4 green)
    - *Место вставки:* перед строкой 8790 (в конце блока `V42__add_pr_review_has_code_and_role_threads.sql`).

12. **L8791–L8816**: `V44__add_features.sql`
    - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `FlywayMigrationValidationTest` (4/4 green)
    - *Место вставки:* перед строкой 8816 (в конце блока `V44__add_features.sql`).

13. **L8817–L8849**: `V45__rename_role_threads_to_feature_threads.sql`
    - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `FlywayMigrationValidationTest` (4/4 green)
    - *Место вставки:* перед строкой 8849 (в конце блока `V45__rename_role_threads_to_feature_threads.sql`).

14. **L8896–L8926**: `V126__return_the_budget_of_a_brief_refused_without_a_readable_reason.sql`
    - *Паттерны:* `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (D012), `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (D006)
    - *Тест-сьют:* `FlywayMigrationValidationTest` (4/4 green)
    - *Место вставки:* перед строкой 8926 (в конце блока `V126__return_the_budget_of_a_brief_refused_without_a_readable_reason.sql`).

15. **L9153–L9173**: `V59__create_gemini_observer_journal.sql`
    - *Паттерн:* `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` (D010)
    - *Тест-сьют:* `FlywayMigrationValidationTest` (4/4 green)
    - *Место вставки:* перед строкой 9173 (в конце блока `V59__create_gemini_observer_journal.sql`).

16. **L9265–L9293**: `V79__kaizen_proposals_and_evidence_nodes.sql`
    - *Паттерн:* `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008)
    - *Тест-сьюты:* `FlywayMigrationValidationTest` (4/4 green), `KaizenServiceTest` (10/10 green)
    - *Место вставки:* перед строкой 9293 (в конце блока `V79__kaizen_proposals_and_evidence_nodes.sql`).

---

## 3. ИТОГОВАЯ ЦЕЛЬ ТАКТА L2
После закрытия этих 16 блоков каталог `docs/FACTORY_MECHANISMS.md` достигает **100% готовности к аудиту (`AUDIT_READY`)** без единого пропущенного механизма.
