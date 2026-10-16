# Пакетная директива L1/L3 -> L2: Секция VIII — Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация

**Дата:** 2026-10-16 05:54 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VII («TOC, Lean, Шесть сигм») и финальный контур (Секции XXXVII–XLIV и XVIII) полностью замерены и закрыты инженером. Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)**:
   - **`FalsificationCycleService`:** Суточный цикл фальсификации кода и монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (D008 Popper, D004 Varzi). Заслон: `FalsificationCycleServiceTest`.
   - **`EvidenceCoherenceService`:** Релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (D010 Goldman, D011 Dretske). Заслоны: `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`.
   - **`OpsAuditorService`:** Аудитор операций действует только на независимо проверенных фактах (`independently-verified facts`) с тройной валидацией предусловий (D006 Raz & Goldman, D010). Заслон: `OpsAuditorServiceTest`.
   - **`PlatformSelfReferenceDetector`:** Детектор отсекает внутризаводской шум и системные находки фабрики от клиентского бэклога (D002 Ryle). Заслон: `PlatformSelfReferenceDetectorTest`.
   - **`FactorySelfHealthService`:** Непрерывный мониторинг собственного здоровья фабрики (размер БД, пулы соединений, очереди) с эскалацией в Кайдзен (D013 Wittgenstein). Заслон: `FactorySelfHealthServiceTest`.
   - **`KaizenService` & `DefectJournalService`:** Дедупликация и изоляция жизненного цикла микро-улучшений по `(projectId, category, targetComponent)` с институциональной записью в журнале дефектов (D007 Gilbert & Searle). Заслоны: `KaizenServiceTest`, `DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`, `LogScopeAndDefectJournalInusFalsificationTest`.
   - **`FlowMetricsService`:** Расчет WIP, Cycle Time, Throughput и проверка закона Литтла строго в границах проекта (D008 Popper, D010 Goldman). Заслон: `FlowMetricsServiceTest`.
   - **`LeverPromotionService`:** Двухчасовой цикл оценки рычагов, продвижение кандидатов строго по накопленным наблюдениям, немедленное понижение при разногласии (D010 Goldman, D006 Raz). Заслоны: `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`.
   - **`PlannedWorkRecoveryService`:** Восстановление задач сохраняет стабильную идентичность исходной задачи, исключая тихое каскадное размножение задач/заявок (D002 Ryle, D004 Shaffer). Заслон: `PlannedWorkRecoveryServiceTest`.
   - **`ContinuousOrchestrationService`:** Единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (D004 Varzi). Заслон: `ContinuousOrchestrationServiceTest`.
   - **`OperationalFlowCoreService` & `OperationalPolicyService`:** Деонтический заслон переходов политики, действий `OperationalAction` и истинности потока с детерминированным отказом при нарушении инвариантов (D006 Raz, D013 Wittgenstein). Заслоны: `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`, `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`.
   - **`TrustSnapshotService`:** Снимки входных сигналов доверия с отложенным дозаполнением исходов сдачи/отклонения (D010 Goldman). Заслон: `TrustSnapshotServiceTest`.
   - **`OperationalTruthService` & `FlowSpineService`:** Анти-зеркало телеметрии рантайма, удержание принципа `done_is_not_delivery`, публикация свидетельства против собственного мнимого благополучия (D013 Wittgenstein, D010 Goldman). Заслоны: `OperationalTruthServiceTest`, `FlowSpineServiceTest`.
   - **`MarketResearchService`:** Очередь рыночных исследований через задачу `TargetContext.ORCHESTRATOR_SYSTEM` и детерминированный выбор carrier-проекта (D010 Goldman). Заслоны: `MarketResearchServiceTest`, `MarketResearchServiceFalsificationTest`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, шлюзов, контроллеров и политик (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты фальсификации цикла (`FalsificationCycleServiceTest`), закона Литтла (`FlowMetricsServiceTest`) и ядра потока (`OperationalFlowCoreFalsificationTest`) доказывают воспроизводимое падение при нарушении инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Защита родословной данных и уровней абстракции: расчет метрик потока и времени цикла без смешения проектов; релаксация графа в `EvidenceCoherenceService` по надежной цепочке свидетельств; сохранение снимков доверия в `TrustSnapshotService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: деонтические барьеры `OperationalPolicyService` на недопустимые переходы статусов и действий; жесткие предусловия в `OpsAuditorService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: `FlowSpineService` и `OperationalTruthService` честно публикуют факт расхождения выполненных задач и реальных доставок клиенту (`done_is_not_delivery`), запрещая фабрике зеркалить самообман.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership]:**
  * Монопольное владение агрегатами фальсификации за `FalsificationCycleService`, исключение кросс-проектных захватов и единство оркестрации в `ContinuousOrchestrationService`.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение категориальных ошибок: фабричный шум не выдается за продуктовые дефекты клиента (`PlatformSelfReferenceDetector`), а восстановление работы не плодит дубликаты сущностей (`PlannedWorkRecoveryService`).
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional defect record]:**
  * Фиксация институциональных фактов в `DefectJournalService` и жизненный цикл PDCA в `KaizenService` исключают бесследную утрату дефектов.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. Подтвердить соблюдение инварианта Ступени 4: 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
3. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секция VIII) и `docs/ANTIGRAVITY_QUEUE.md`.
4. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
5. Закоммитить с семантическим сообщением `feat(flow-and-self-checking): complete Stage 4 batch verification (D008, D010, D006)`.
