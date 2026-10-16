# Пакетная директива L1/L3 -> L2: Секция VIII — Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация

**Дата:** 2026-10-16 03:20 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции V и VI («Свидетельство доставки» и «Решётка суждения») успешно заверены и закрыты инженером (коммит `a899e4d`, 262/262 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Секцию VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)**:
   - **Механизмы ядра и сервисы контура:**
     * `FalsificationCycleService` (суточный цикл по дефектам кода и монопольное владение агрегатами);
     * `EvidenceCoherenceService` (алгоритм ECHO Тагарда на графе свидетельств);
     * `OpsAuditorService` (диагностика и независимые свидетельства с валидацией предусловий);
     * `PlatformSelfReferenceDetector` (отсечение внутризаводского шума от продуктового бэклога);
     * `FactorySelfHealthService` (мониторинг собственного здоровья фабрики);
     * `KaizenService` & `DefectJournalService` (дедупликация и запись институциональных дефектов);
     * `FlowMetricsService` (WIP, Throughput, Cycle Time и проверка закона Литтла);
     * `LeverPromotionService` (лестница продвижения рычагов от наблюдения к действию);
     * `PlannedWorkRecoveryService` (восстановление задач без мутации идентичности);
     * `ContinuousOrchestrationService` (единый детерминированный тик оркестрации);
     * `OperationalFlowCoreService`, `OperationalPolicyService`, `OperationalTruthService`, `OperationalAction`, `FlowSpineService` (деонтические заслоны политики, 4-значная логика истинности, инвариант `done_is_not_delivery`);
     * `TrustSnapshotService` (снимки сигналов доверия с отложенным дозаполнением исходов);
     * `MarketResearchService` (детерминированный сбор внешних измерений через каузальные задачи).
   - **Заслоны верификации (281 тест, 100% green в едином прогоне Docker Maven):**
     * `FalsificationCycleServiceTest` (28/28),
     * `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5),
     * `OpsAuditorServiceTest` (11/11),
     * `PlatformSelfReferenceDetectorTest` (7/7),
     * `FactorySelfHealthServiceTest` (5/5),
     * `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5),
     * `FlowMetricsServiceTest` (4/4),
     * `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4),
     * `PlannedWorkRecoveryServiceTest` (15/15),
     * `ContinuousOrchestrationServiceTest` (16/16),
     * `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32), `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39), `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9),
     * `TrustSnapshotServiceTest` (9/9),
     * `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, контроллеров и политик (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D013, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация метрик и дефектов: `FalsificationCycleService` суточно ищет нарушения инвариантов кода; `FlowMetricsService` фальсифицирует расхождение WIP и Cycle Time по закону Литтла; ложные зеленые статусы исключаются.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: `EvidenceCoherenceService` рассчитывает когерентность свидетельств ECHO Тагарда без субъективных эвристик; `TrustSnapshotService` сохраняет точные входные снимки доверия до подтверждения реального исхода; `FlowMetricsService` оперирует фактами завершенных задач строго внутри проекта.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Деонтические запреты в коде потока: `OperationalPolicyService` накладывает исключающие основания Раза, возвращая детерминированный `OperationalPolicyDeniedException` при попытке неразрешенных переходов; `OpsAuditorService` действует строго по независимо подтвержденным фактам.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Категориальная демаркация: `PlatformSelfReferenceDetector` отсекает внутризаводской шум фабрики от продуктового бэклога заказчика; `PlannedWorkRecoveryService` восстанавливает задачу с сохранением её исходной идентичности, не допуская паразитного размножения задач/заявок.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi]:**
  * Топология границ владения: монопольное владение `FalsificationCycleService` агрегатами `falsification_runs` и `code_integrity_findings`; строгое разграничение агрегатов состояния.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * Многозначная логика истинности в `OperationalTruthService` и `OperationalFlowCoreService`: отсутствие информации возвращает статус `undetermined`, исключая бинарный произвол и ложное благополучие.
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: `FlowSpineService` публикует честный статус `done_is_not_delivery`, отражая реальную сдачу клиенту, а не промежуточные отметки воркеров; `FactorySelfHealthService` замеряет физические параметры хоста и БД фабрики.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Gilbert]:**
  * Институциональная память об ошибках: дефекты в `DefectJournalService` и Кайдзен-предложения `KaizenService` дедуплицируются и сохраняются по неизменному ключу `(projectId, category, targetComponent)`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции VIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секции VIII в `docs/FACTORY_MECHANISMS.md`, подтвердить актуальность блока `### Итог Секции VIII`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md` (внести Секцию VIII в `## Дальше`).
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(flow-and-metrics): complete Section VIII batch verification (D008, D010, D006)`.
