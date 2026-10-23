# Директивы и наряд для L2 (2026-10-23 22:50 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Кластер Секций V и VI («Свидетельство доставки» и «Суждение: решётка вердиктов и рычаги») полностью заверен и закрыт инженером (коммит `75e7c7d4`, 279/279 green в 36 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 52 s, строго 0 строк изменений рабочего кода). Секция VII (TOC) также ранее полностью заверена и закрыта.
   - Настоящая директива открывает следующий исполнительный контур фабрики — **Секцию VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (5 сьютов, 72 теста):**
       - `FalsificationCycleServiceTest`: суточный цикл фальсификации кода и недельный философский цикл, монопольное владение агрегатами `falsification_runs` и `code_integrity_findings`;
       - `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`: релаксация графа свидетельств по алгоритму ECHO Тагарда, запрет ложной связности при отсутствии свидетельств, детерминированное прекращение релаксации;
       - `OpsAuditorServiceTest`: аудит операций фабрики исключительно на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий;
       - `PlatformSelfReferenceDetectorTest`: отсечение внутризаводского шума фабрики от продуктового бэклога заказчика (защита от ошибок категоризации Райла).
     * **Группа 2 — Здоровье фабрики, Кайдзен и журнал дефектов (5 сьютов, 29 тестов):**
       - `FactorySelfHealthServiceTest`: мониторинг собственного здоровья фабрики (размер базы данных, пул соединений, очереди задач) с эскалацией в Кайдзен;
       - `KaizenServiceTest`: жизненный цикл предложений PDCA, дедупликация и изоляция предложений по проекту и компоненту `(projectId, category, targetComponent)`;
       - `DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`: фиксация дефектов в журнале, INUS-анализ первопричин по Мэки, строгая изоляция контекста логов.
     * **Группа 3 — Метрики потока, рычаги ТОС и восстановление работ (4 сьюта, 30 тестов):**
       - `FlowMetricsServiceTest`: расчет времени цикла, WIP, throughput и проверка согласованности закона Литтла с варнингом при расхождениях;
       - `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`: лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений;
       - `PlannedWorkRecoveryServiceTest`: атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения задач/заявок.
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (11 сьютов, 159 тестов):**
       - `ContinuousOrchestrationServiceTest`: единый детерминированный тик оркестрации фабрики без сторонних неконтролируемых фоновых демонов;
       - `OperationalTruthServiceTest`, `FlowSpineServiceTest`: операционная правда потока, хребет событий потока, инвариант `done_is_not_delivery`, публикация свидетельств против собственного благополучия;
       - `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`: операционное ядро переходов потока и фальсификационные заслоны недопустимых операций;
       - `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`: деонтическая политика разрешений с типизированными причинами отказа (`OperationalPolicyReason`);
       - `TrustSnapshotServiceTest`: снимки входных сигналов доверия с отложенным дозаполнением исходов сдачи/отклонения;
       - `MarketResearchServiceTest`, `MarketResearchServiceFalsificationTest`: детерминированный сбор рыночных измерений через каузальные задачи в контексте системы.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские заслоны фальсификации: проверка суточного и недельного циклов выявления скрытых дефектов (`FalsificationCycleServiceTest`), заслоны прекращения зацикливания релаксации графа (`EvidenceCoherenceV80TerminationFalsificationTest`), фальсификационные барьеры недопустимых переходов операционного ядра (`OperationalFlowCoreFalsificationTest`), заслоны отказов политики (`OperationalPolicyDeniedExceptionFalsificationTest`), фальсификация рыночных сигналов (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Надежность каузальной цепочки свидетельств. Фиксация снимков доверия (`TrustSnapshotService`), где входные параметры изолированы от апостериорного исхода. Лок уровней абстракции: единичное наблюдение (`LeverObservation`) -> продвинутый рычаг (`LeverPromotionService`) -> операционное действие политики (`OperationalAction`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет сторонних фоновых кронов вне `ContinuousOrchestrationService`;
  * Деонтический заслон политик `OperationalPolicyService`: любое рискованное действие отклоняется с явным типом причины (`OperationalPolicyReason`), исключая скрытые мутации.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Varzi]:**
  * Монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` закреплено строго за `FalsificationCycleService`. Стабильная идентичность восстанавливаемых задач без каскадного порождения дубликатов (`PlannedWorkRecoveryService`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальный приоритет реальных данных: `FlowSpineService` публикует честные свидетельства против собственного благополучия (варнинг `done_is_not_delivery`), `FactorySelfHealthService` замеряет физические ресурсы фабрики (размер базы, пулы), исключая самообман системы.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Категориальная демаркация: `PlatformSelfReferenceDetector` отсекает внутризаводской технологический шум фабрики от продуктового бэклога заказчика; статус `TaskStatus.done` категориально отделен от поставки клиенту.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_MEKI_01_INUS_CONDITION` [D007 Incomplete audit & INUS / Gilbert & Mackie]:**
  * Институциональный журнал дефектов `DefectJournalService` фиксирует исчерпание пределов и отклонения; INUS-анализ выявляет достаточные условия первопричин без потери каузального контекста.

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 25 тест-сьютов Секции VIII внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. **Критерий приёмки:** Ровно 290/290 green в 25 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (290/290 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(self-check-and-flow): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
