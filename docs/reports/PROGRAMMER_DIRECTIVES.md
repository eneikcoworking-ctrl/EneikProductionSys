# Директивы и наряд для L2 (2026-10-23 23:31 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции III и IV («Отправка: задача уходит в работу» и «Ревью, гейт, слияние: код становится частью продукта») полностью заверены и закрыты инженером (коммит `0e6e417c`, 561/561 green в 65 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:17 min, строго 0 строк изменений рабочего кода). Секция VII (TOC) также ранее полностью заверена и закрыта.
   - Настоящая директива открывает следующую секцию механизмов стратегического ядра фабрики — **Секцию VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики»)** целиком (**290 тестов в едином непрерывном пакетном прогоне в Docker Maven [25 тест-сьютов]**):
     * **Группа 1 — Цикл фальсификации, связность свидетельств ECHO и аудит операций (6 сьютов, 77 тестов):**
       - `FalsificationCycleServiceTest` (28 тестов): суточный цикл фальсификации кода и недельный философский цикл, монопольное владение агрегатами `falsification_runs` и `code_integrity_findings`;
       - `EvidenceCoherenceServiceTest` (21 тест), `EvidenceCoherenceV80TerminationFalsificationTest` (5 тестов): релаксация графа свидетельств по алгоритму ECHO Тагарда, запрет ложной связности при отсутствии свидетельств, детерминированное прекращение релаксации;
       - `OpsAuditorServiceTest` (11 тестов): аудит операций фабрики исключительно на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий;
       - `PlatformSelfReferenceDetectorTest` (7 тестов): отсечение внутризаводского шума фабрики от продуктового бэклога заказчика (защита от ошибок категоризации Райла);
       - `FactorySelfHealthServiceTest` (5 тестов): мониторинг собственного здоровья фабрики (размер базы данных, пул соединений, очереди задач) с эскалацией в Кайдзен.
     * **Группа 2 — Кайдзен, журнал дефектов и анализ первопричин (4 сьюта, 24 теста):**
       - `KaizenServiceTest` (10 тестов): жизненный цикл предложений PDCA, дедупликация и изоляция предложений по проекту и компоненту `(projectId, category, targetComponent)`;
       - `DefectJournalServiceTest` (3 теста), `DefectJournalRootCauseAttributionTest` (6 тестов), `LogScopeAndDefectJournalInusFalsificationTest` (5 тестов): фиксация дефектов в журнале, INUS-анализ первопричин по Мэки, строгая изоляция контекста логов.
     * **Группа 3 — Метрики потока, рычаги ТОС, восстановление работ и непрерывная оркестрация (5 сьютов, 46 тестов):**
       - `FlowMetricsServiceTest` (4 теста): расчет времени цикла, WIP, throughput и проверка согласованности закона Литтла с варнингом при расхождениях;
       - `LeverPromotionServiceTest` (7 тестов), `LeverPromotionServiceFalsificationTest` (4 теста): лестница продвижения рычагов ТОС от `observe_only` к активным действиям на основе накопленных согласованных наблюдений;
       - `PlannedWorkRecoveryServiceTest` (15 тестов): атомарное восстановление упавших задач со строгим сохранением идентичности и категорическим запретом паразитного размножения задач/заявок;
       - `ContinuousOrchestrationServiceTest` (16 тестов): единый детерминированный тик оркестрации фабрики без сторонних неконтролируемых фоновых демонов.
     * **Группа 4 — Операционное ядро, политика, правда потока и рынок (10 сьютов, 143 теста):**
       - `OperationalFlowCoreServiceTest` (6 тестов), `OperationalFlowCoreFalsificationTest` (39 тестов): операционное ядро переходов потока и фальсификационные заслоны недопустимых операций;
       - `OperationalPolicyServiceTest` (17 тестов), `OperationalPolicyReasonTest` (5 тестов), `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов): деонтическая политика разрешений с типизированными причинами отказа (`OperationalPolicyReason`);
       - `TrustSnapshotServiceTest` (9 тестов): снимки входных сигналов доверия с отложенным дозаполнением исходов сдачи/отклонения;
       - `OperationalTruthServiceTest` (17 тестов), `FlowSpineServiceTest` (32 теста): операционная правда потока, хребет событий потока, инвариант `done_is_not_delivery`, публикация свидетельств против собственного благополучия;
       - `MarketResearchServiceTest` (3 теста), `MarketResearchServiceFalsificationTest` (6 тестов): детерминированный сбор рыночных измерений через каузальные задачи в контексте системы.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D013, D002, D007, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационные заслоны суточного цикла фальсификации (`FalsificationCycleServiceTest`), прекращения релаксации связности ECHO (`EvidenceCoherenceV80TerminationFalsificationTest`), рычагов TOC (`LeverPromotionServiceFalsificationTest`), операционного ядра (`OperationalFlowCoreFalsificationTest`) и рыночных гипотез (`MarketResearchServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств и лок уровней абстракции закона Литтла (`FlowMetricsServiceTest`), снимки доверия на основе накопленных фактов (`TrustSnapshotServiceTest`, `OperationalTruthServiceTest`), происхождение свидетельств в графе Тагарда.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей операционной политики (`OperationalPolicyServiceTest`, `OperationalPolicyDeniedExceptionFalsificationTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `MAJKL_BRATMAN_01_SHARED_PLANNING_AGENCY` [D004 Part-whole ownership & Shared planning agency / Varzi & Bratman]:**
  * Монопольное владение восстановлением работ без размножения задач (`PlannedWorkRecoveryServiceTest`) и координация детерминированного тика фабрики (`ContinuousOrchestrationServiceTest`).
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальная телеметрия здоровья и истинности потока (`FlowSpineServiceTest`), инвариант `done_is_not_delivery` — реальное состояние продукта и БД выше словесных рапортов.
- **`DZHILBERT_RAYL_01_CATEGORY_MISTAKE` [D002 Category mistake / Ryle]:**
  * Отсечение внутризаводского шума платформы от продуктового бэклога заказчика (`PlatformSelfReferenceDetectorTest`).
- **`DZHON_LESLI_MEKI_01_INUS_CONDITION` & `MARGARET_GILBERT_01_JOINT_COMMITMENT` [D007 Institutional fact & INUS attribution / Mackie & Gilbert]:**
  * Институциональный журнал дефектов и INUS-анализ первопричин (`DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка истинности состояний операционного ядра, рычагов ТОС и политики фабрики.

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 25 тест-сьютов Секции VIII внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. **Критерий приёмки:** Ровно 290/290 green в 25 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (290/290 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(self-check-and-flow): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
