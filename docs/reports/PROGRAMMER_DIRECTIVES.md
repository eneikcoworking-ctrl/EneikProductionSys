# Директивы и наряд для L2 (2026-10-16 15:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш ML-сайдкара, верёвка DBR, очистка журналов и завершающий баланс фабрики») успешно заверены инженером (коммит `853e60e`, 143/143 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином Docker Maven прогоне):
     * **Группа 1: `FalsificationCycleService` (28 тестов):** `FalsificationCycleServiceTest` (28/28) — суточный цикл фальсификации и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2: `EvidenceCoherenceService` (26 тестов):** `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5) — релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Группа 3: `OpsAuditorService` (11 тестов):** `OpsAuditorServiceTest` (11/11) — аудитор операций на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 4: `PlatformSelfReferenceDetector` (7 тестов):** `PlatformSelfReferenceDetectorTest` (7/7) — отсечение внутризаводского шума от продуктового бэклога заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 5: `FactorySelfHealthService` (5 тестов):** `FactorySelfHealthServiceTest` (5/5) — непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 6: `KaizenService` & `DefectJournalService` (24 теста):** `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5) — дедупликация по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
     * **Группа 7: `FlowMetricsService` (4 теста):** `FlowMetricsServiceTest` (4/4) — расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 8: `LeverPromotionService` (11 тестов):** `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4) — лестница продвижения рычагов от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 9: `PlannedWorkRecoveryService` (15 тестов):** `PlannedWorkRecoveryServiceTest` (15/15) — восстановление упавших задач со строгим сохранением идентичности и запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
     * **Группа 10: `ContinuousOrchestrationService` (16 тестов):** `ContinuousOrchestrationServiceTest` (16/16) — единый детерминированный тик оркестрации фабрики без сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 11: `OperationalFlowCoreService`, `OperationalPolicyService`, `OperationalTruthService`, `OperationalAction`, `FlowSpineService` (125 тестов):** `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32), `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39), `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9) — деонтические заслоны переходов политики, честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 12: `TrustSnapshotService` (9 тестов):** `TrustSnapshotServiceTest` (9/9) — снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 13: `MarketResearchService` (9 тестов):** `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6) — детерминированный сбор внешних рыночных измерений через каузальные задачи без мутации клиентского кода (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D011, D007, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: честные заслоны на физических прогонах тестов и реальных фактах без ложного зеленого; 4-значное разделение истинности метрик потока и гейтов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: запрет смешивания проектного и фабричного скоупов, фиксация снимков доверия с отложенным исходом, расчет закона Литтла.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон неавторизованных переходов состояний и институциональный аудит предусловий действий (`OperationalPolicyService`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004, D006 Boundary topology / Varzi]:**
  * Монопольное владение агрегатами `falsification_runs` и `code_integrity_findings`, сохранение стабильной идентичности восстанавливаемых задач.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная логика истинности в лестнице продвижения рычагов (разделение истинной правоты `TRUE` и простого совпадения с политикой `BOTH`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: телеметрия хребта потока (`done_is_not_delivery`), честный мониторинг собственного здоровья фабрики (`FactorySelfHealthService`).
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Dretske]:**
  * Релаксация графа свидетельств ECHO Тагарда без ложной уверенности на пустых данных.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Institutional defect record / Gilbert]:**
  * Дедупликация и фиксация микро-улучшений PDCA в журнале дефектов.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение внутризаводского шума фабрики от продуктового бэклога заказчика.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest`
2. Зафиксировать результат (290/290 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
