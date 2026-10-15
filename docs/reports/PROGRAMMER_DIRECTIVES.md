# Пакетная директива L1/L3 -> L2: Секция VIII (Flow, Metrics, Continuous Orchestration & Self-Verification)

**Дата:** 2026-10-15 14:45 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VII (TOC) и Секции XXI/XXIб/XXII закрыты инженером в статусе ideal (100% green в Docker Maven). Настоящая директива охватывает целиком следующий открытый комплекс фабрики — **Секцию VIII: Flow, Metrics, Continuous Orchestration & Self-Verification (Самопроверка, метрики потока, операционная правда и непрерывная оркестрация)**:
   - **Метрики потока и статистический контроль (Flow & Lean Metrics):**
     * `FlowMetricsService` — расчет WIP, пропускной способности (throughput) и времени цикла (Little's Law consistency);
     * `TaskWaitTimeService` — хронометраж очередей и времени ожидания задач в потоке;
     * `SixSigmaAuditService` — расчет DPMO, индекса Cpk и обнаружение статистических выбросов;
     * `EmsMetricsService` — метрики потока EMS и индикаторы операционной стабильности.
   - **Самопроверка и связность свидетельств (Self-Verification & Coherence):**
     * `FalsificationCycleService` — суточный цикл фальсификации дефектов кода и недельный методологический цикл (монопольное владение `falsification_runs` и `code_integrity_findings`);
     * `EvidenceCoherenceService` — релаксация графа свидетельств ECHO Тагарда без ложной уверенности на разреженных данных;
     * `OpsAuditorService` — операционный диагноз и предикативная валидация предусловий перед исполнением исправительных действий;
     * `PlatformSelfReferenceDetector` — демаркация категориальных ошибок (D002) и отсечение фабричного шума от клиентского продуктового списка;
     * `FactorySelfHealthService` — непрерывная самодиагностика внутреннего здоровья фабрики (БД, пулы соединений, емкость очередей).
   - **Непрерывное улучшение и стабильность идентичности (Kaizen & Recovery):**
     * `KaizenService` — дедупликация и жизненный цикл предложений улучшений PDCA с фиксацией в журнале дефектов;
     * `PlannedWorkRecoveryService` — сохранение стабильной идентичности задач при восстановлении и деонтический запрет тихой генерации дубликатов.
   - **Хребет потока, операционная правда и оркестрация (Spine & Orchestration):**
     * `ContinuousOrchestrationService` — единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних демонов;
     * `FlowSpineService` — непрерывный хребет потока, публикация свидетельств и защита инварианта `done_is_not_delivery`;
     * `OperationalPolicyService` — матрица прав и обязанностей (Раз D006), авторизация операционных действий;
     * `OperationalFlowCoreService` — ядро детерминированных переходов состояний потока;
     * `OperationalTruthService` — операционная правда и градуированная шкала доверия;
     * `TrustSnapshotService` — фиксация сырых снимков доверия с отложенным дозаполнением исходов;
     * `OperationalAction` — замкнутая типология операционных действий.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и моделей при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсифицирующие заслоны верификации: `FlowSpineService` публикует свидетельства против собственного мнимого благополучия (строгий учет `done_is_not_delivery`);
  * `FalsificationCycleService` подтверждает обнаружение регрессий и проверку инвариантов целостности кода;
  * Заслоны `OperationalFlowCoreFalsificationTest` и `OperationalPolicyDeniedExceptionFalsificationTest` подтверждают немедленный отказ при попытке несанкционированного перехода.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгая демаркация входного факта, промежуточного свидетельского узла (evidence node), снимка доверия и управляющего действия;
  * `TrustSnapshotService` удерживает разделение между гипотезой и подтвержденным результатом до момента реальной сдачи заказчику;
  * `FlowMetricsService` изолирует расчеты пропускной способности в рамках проекта, не допуская смешивания межпроектных данных.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов Секции VIII;**
  * Деонтический запрет на обход политик авторизации через `OperationalPolicyService`;
  * Запрет порождения новых задач сервисом `PlannedWorkRecoveryService` при восстановлении сбоев.

---

## 3. Пакетный наряд по механизмам Секции VIII

1. **Метрики потока и статистический контроль (`FlowMetricsService`, `TaskWaitTimeService`, `SixSigmaAuditService`, `EmsMetricsService`):**
   - **Проба:** Замерить согласованность расчетов WIP, времени цикла, Cpk и DPMO в изолированном проектном скопе.
   - **Инвариант:** Рабочий код не трогать. Заслоны `FlowMetricsServiceTest`, `TaskWaitTimeServiceTest`, `SixSigmaAuditServiceTest`, `EmsMetricsServiceTest`.
2. **Самопроверка и связность свидетельств (`FalsificationCycleService`, `EvidenceCoherenceService`, `OpsAuditorService`, `PlatformSelfReferenceDetector`, `FactorySelfHealthService`):**
   - **Проба:** Замерить суточный цикл фальсификации, расчет связности ECHO, валидацию предусловий аудитора, демаркацию фабричного шума и мониторинг БД.
   - **Инвариант:** Рабочий код не трогать. Заслоны `FalsificationCycleServiceTest`, `EvidenceCoherenceServiceTest`, `OpsAuditorServiceTest`, `PlatformSelfReferenceDetectorTest`, `FactorySelfHealthServiceTest`.
3. **Непрерывное улучшение и стабильность идентичности (`KaizenService`, `PlannedWorkRecoveryService`):**
   - **Проба:** Замерить дедупликацию предложений улучшений в скопе `(projectId, category, targetComponent)` и сохранение идентичности задач без размножения.
   - **Инвариант:** Рабочий код не трогать. Заслоны `KaizenServiceTest`, `PlannedWorkRecoveryServiceTest`.
4. **Хребет потока, операционная правда и оркестрация (`ContinuousOrchestrationService`, `FlowSpineService`, `OperationalPolicyService`, `OperationalFlowCoreService`, `OperationalTruthService`, `TrustSnapshotService`, `OperationalAction`):**
   - **Проба:** Замерить детерминированный тик оркестрации, инвариант `done_is_not_delivery`, авторизацию действий политикой и фиксацию снимков доверия.
   - **Инвариант:** Рабочий код не трогать. Заслоны `ContinuousOrchestrationServiceTest`, `FlowSpineServiceTest`, `OperationalPolicyServiceTest`, `OperationalTruthServiceTest`, `TrustSnapshotServiceTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов Секции VIII в Docker Maven (`-m 2g`).
2. Внести отметку `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(flow-metrics): complete Section VIII batch verification (D008, D010, D006)`.
