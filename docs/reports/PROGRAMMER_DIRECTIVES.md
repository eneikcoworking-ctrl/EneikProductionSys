# Директивы и наряд для L2 (2026-10-16 13:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции III и IV («Отправка и Ревью/Слияние») успешно заверены инженером (коммит `ab661c6`, 450/450 green, 0 строк изменений рабочего кода).
   - Секция VII (TOC) также полностью замерена и закрыта.
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином непрерывном пакетном прогоне Docker Maven):
     * **Группа 1: Фальсификация, когерентность и аудит операций (6 сьютов, 77 тестов):**
       - `FalsificationCycleServiceTest`: суточный цикл фальсификации и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`: релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `OpsAuditorServiceTest`: аудит операций на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `PlatformSelfReferenceDetectorTest`: отсечение внутризаводского шума от клиентского бэклога заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest`: мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Кайдзен, журнал дефектов, метрики потока и рычаги (7 сьютов, 48 тестов):**
       - `KaizenServiceTest`, `DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`: дедупликация по `(projectId, category, targetComponent)` с институциональной записью в журнал дефектов (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `FlowMetricsServiceTest`: расчет WIP, cycle time, throughput и проверка сходимости закона Литтла (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`: лестница продвижения рычагов от `observe_only` к активным действиям строго по согласованным наблюдениям (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Оркестрация, операционная правда и восстановление потока (12 сьютов, 165 тестов):**
       - `PlannedWorkRecoveryServiceTest`: восстановление упавших задач со стабильной идентичностью и защитой от паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `ContinuousOrchestrationServiceTest`: единый детерминированный тик оркестрации фабрики без сторонних кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`, `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`, `OperationalTruthServiceTest`, `FlowSpineServiceTest`: деонтические заслоны переходов политики, честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TrustSnapshotServiceTest`: снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MarketResearchServiceTest`, `MarketResearchServiceFalsificationTest`: детерминированный сбор внешних рыночных измерений через системного носителя без искажения очередей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D011, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: циклы фальсификации `FalsificationCycleServiceTest`, фальсификация переходов ядра потока `OperationalFlowCoreFalsificationTest`, отказ от самообмана при согласовании свидетельств.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность каузальной цепочки: расчет Flow Metrics (`FlowMetricsService`), снимки доверия `TrustSnapshotService`, релаксация графа свидетельств ECHO `EvidenceCoherenceService`, строгое проектное квантование без утечек через `ScopedEvidencePacket`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон отказов `OperationalPolicyDeniedExceptionFalsificationTest`, `OperationalPolicyService`, независимая верификация фактов в `OpsAuditorServiceTest` (строго `independently-verified facts`, запрет действий на непроверенных утверждениях LLM).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereological collapse / Varzi]:**
  * Монопольное владение `FalsificationCycleService` над своими агрегатами (`falsification_runs`, `code_integrity_findings`), стабильная идентичность восстанавливаемой задачи в `PlannedWorkRecoveryService`.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение внутризаводского шума от клиентского бэклога в `PlatformSelfReferenceDetectorTest`, демаркация полезной работы от потерь простоя.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback / Dretske]:**
  * Фиксация продвижения потока строго по реальному выходу артефактов (`SystemProgressTracker`, `EvidenceCoherenceService`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для операционной правды и переходов рычагов (`LeverPromotionService`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService`, честная публикация свидетельств против собственного благополучия (`done_is_not_delivery`), мониторинг здоровья в `FactorySelfHealthService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 25 тест-сьютов Секции VIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,OperationalTruthServiceTest,FlowSpineServiceTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить строго **290/290 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(flow-and-self-check): complete Stage 4 batch verification (D008, D010, D006)`.
