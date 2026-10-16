# Пакетная директива L1/L3 -> L2: Секция VIII — Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация

**Дата:** 2026-10-16 06:23 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции III и IV («Отправка и Ревью/Гейт/Слияние») полностью заверены и закрыты инженером (коммит `f4b3053`, 394/394 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива открывает пакетный замер контура самопроверки и координации фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком:
   - **Механизмы Секции VIII:**
     * `FalsificationCycleService` (суточный цикл фальсификации, замещение провалившихся задач, владение `falsification_runs` и `code_integrity_findings`)
     * `EvidenceCoherenceService` (релаксация графа свидетельств Тагарда, доверие связности V80/V81)
     * `OpsAuditorService` (аудитор операций на независимо проверенных фактах с тройной валидацией)
     * `PlatformSelfReferenceDetector` (отсечение заводского шума и самореференции от клиентского бэклога)
     * `FactorySelfHealthService` (мониторинг собственного здоровья фабрики, размер БД, очереди, эскалация)
     * `KaizenService` & `DefectJournalService` (дедупликация инцидентов, фиксация первопричин в журнале дефектов)
     * `FlowMetricsService` (расчет WIP, Cycle Time, Throughput, сходимость закона Литтла)
     * `LeverPromotionService` (лестница продвижения решающих рычагов от `observe_only` к действиям)
     * `PlannedWorkRecoveryService` (восстановление сорванных задач со строгим сохранением идентичности)
     * `ContinuousOrchestrationService` (единый детерминированный тик оркестрации фабрики без сторонних кронов)
     * `OperationalFlowCoreService` & `OperationalFlowCoreController` (ядро переходов состояний потока)
     * `OperationalPolicyService` & `OperationalAction` (деонтические заслоны допустимых действий с объяснимой причиной)
     * `TrustSnapshotService` (снимки входных сигналов доверия с отложенным дозаполнением)
     * `OperationalTruthService` (расчет операционного доверия, блокеры и анти-зеркало рантайма)
     * `FlowSpineService` (позвоночник потока, статус клиентской ценности `done_is_not_delivery`)
     * `MarketResearchService` (детерминированный сбор рыночных измерений через каузальные задачи)
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, контроллеров, политик и хранилищ (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при подмене фазы базовых контрольных пределов, нарушении закона Литтла или генерации замены без регистрации отказа.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: строгая изоляция проектных выборок без `findAll()`, каузальная трассировка снимков доверия, сохранение источника и временной метки для каждого операционного факта.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: деонтические барьеры переходов политики (`OperationalPolicyDeniedException`), запрет выполнения действий при невалидных предусловиях.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Boundary / Varzi & Bratman]:**
  * Монопольное владение агрегатами: `FalsificationCycleService` единолично владеет `falsification_runs`, `PlannedWorkRecoveryService` сохраняет идентичность задач, `ContinuousOrchestrationService` гармонизирует общий цикл.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued logic / Belnap]:**
  * 4-значная решётка истинности: неизвестное или не измеренное состояние потока никогда не подменяется фиктивным успехом или «System flow optimal».
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: физические метрики рантайма продукта и завершённые поставки заказчику выше любого промежуточного статуса в БД (`done_is_not_delivery`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Категориальная демаркация: внутризаводские служебные события и платформенная самореференция изолированы от продуктового бэклога заказчика.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Evidence gap / Gilbert]:**
  * Институциональная регистрация дефектов: фиксация первопричин инцидентов в журнале дефектов (`DefectJournalEntity`) с гарантированной дедупликацией.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VIII в едином непрерывном запуске Docker Maven (все 25 тест-сьютов контура самопроверки и метрик потока):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секция VIII) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(self-check-and-flow): complete Stage 4 batch verification (D008, D010, D006)`.
