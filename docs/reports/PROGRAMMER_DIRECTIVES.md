# Пакетная директива L1/L3 -> L2: Секция VIII (Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен и Оркестрация)

**Дата:** 2026-10-15 16:42 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XVI полностью подтверждена и закрыта инженером (160/160 green в коммите `6c7c55e`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию VIII (Самопроверка: Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация)**:
   - **Контур VIII.1. Самопроверка, фальсификация и когерентность свидетельств (Varzi D004, Thagard/Goldman D010, Ryle D002):**
     * `FalsificationCycleService` — суточный цикл по дефектам кода, недельный философский, монопольное архитектурное владение агрегатами `falsification_runs` и `code_integrity_findings` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * `EvidenceCoherenceService` — ECHO Тагарда на графе свидетельств, чистая релаксация без ложной уверенности на пустых данных, калибровка надежности источников (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * `OpsAuditorService` — операционный диагноз и корректирующие действия строго на базе независимо проверенных фактов с валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006);
     * `PlatformSelfReferenceDetector` — отсечение внутреннего шума фабрики от продуктового бэклога заказчика (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
     * `FactorySelfHealthService` — непрерывная самодиагностика здоровья фабрики (размер БД, соединения, очереди) с эскалацией в Кайдзен (`SELF_MODEL_SANITY` / D013).
   - **Контур VIII.2. Метрики потока, сигналы улучшений и продвижение рычагов (Popper D008, Goldman D010, Jeffrey):**
     * `KaizenService` & `DefectJournalService` — дедупликация и жизненный цикл микро-улучшений PDCA со скоупом `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (`BELIEF_UPDATE_LEDGER` / D007);
     * `FlowMetricsService` — расчет WIP, Cycle Time, Lead Time и фальсифицирующая проверка сходимости закона Литтла (`WIP ≈ Throughput × CycleTime`) со строгой изоляцией по `projectId` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * `LeverPromotionService` — лестница продвижения рычагов ТОС (`observe_only` -> `soft_gate` -> `authoritative`) строго по накопленным объективным наблюдениям (`RELIABILITY_CHAIN` / D010);
     * `PlannedWorkRecoveryService` — сохранение стабильной идентичности исходной задачи при восстановлении, запрет каскадного размножения задач/заявок (`CATEGORY_ERROR_SCAN` / D002);
     * `ContinuousOrchestrationService` — единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (`PLANNING_CONSISTENCY` / D004).
   - **Контур VIII.3. Операционная политика, хребет потока и динамика доверия (Raz D006, Wittgenstein D013, Goldman D010):**
     * `OperationalPolicyService`, `OperationalFlowCoreService`, `OperationalTruthService`, `OperationalAction`, `FlowSpineService` — деонтический заслон переходов политики, инвариант `done_is_not_delivery`, публикация свидетельств против собственного благополучия (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `TrustSnapshotService` — фиксация входных сигналов доверия с отложенным дозаполнением исхода сдачи/отклонения (`PERSISTENCE_SNAPSHOT` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы Секции VIII уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D002, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация закона Литтла и консистентности потока в `FlowMetricsServiceTest`: расхождение между WIP, Throughput и Cycle Time сигнализирует о ложных замерах.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции метрик качества и потока по `projectId`;
  * Цепочка надежности свидетельств в `EvidenceCoherenceService` и продвижение рычагов в `LeverPromotionService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секции VIII при проходящих тестах;**
  * Деонтический заслон операционных переходов в `OperationalPolicyService` и `OperationalFlowCoreService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Принцип анти-зеркала: хребет потока `FlowSpineService` публикует честные свидетельства против собственного благополучия (`done_is_not_delivery`), запрет приватных языков и субъективных оценок.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary drift, Varzi] & `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Монопольное владение `FalsificationCycleService` своими агрегатами;
  * Отсечение ошибок категории в `PlatformSelfReferenceDetector` и `PlannedWorkRecoveryService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VIII в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции VIII:
   - `FalsificationCycleServiceTest`;
   - `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`;
   - `OpsAuditorServiceTest`;
   - `PlatformSelfReferenceDetectorTest`;
   - `FactorySelfHealthServiceTest`;
   - `KaizenServiceTest`;
   - `FlowMetricsServiceTest`;
   - `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`;
   - `PlannedWorkRecoveryServiceTest`;
   - `ContinuousOrchestrationServiceTest`;
   - `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`;
   - `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`;
   - `TrustSnapshotServiceTest`.
3. Зафиксировать форму образца и актуализировать статус Секции VIII в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal).
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
