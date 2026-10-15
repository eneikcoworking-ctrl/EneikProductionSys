# Пакетная директива L1/L3 -> L2: Секция VIII (Самопроверка, метрики потока, оркестрация и операционная политика)

**Дата:** 2026-10-15 15:52 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущие этапы (включая Секции VII, XXVIII, XXX–XLIV) успешно замерены и закрыты. Настоящая директива охватывает целиком следующий стратегический контур фабрики — **Секцию VIII: Самопроверка, метрики потока, оркестрация и операционная политика**:
   - **Контур VIII.1. Самопроверка, связность свидетельств и защита от шума:**
     * `FalsificationCycleService` — суточный/недельный цикл фальсификации, монопольное владение агрегатами `falsification_runs` и `code_integrity_findings` (D004 Varzi, D008 Popper);
     * `EvidenceCoherenceService` — расчет связности свидетельств ECHO Тагарда, релаксация графа без ложной уверенности, ограничение пула прогонов V80 (D007, D010 Goldman);
     * `PlatformSelfReferenceDetector` — отсечение фабричного шума и внутренних дефектов от продуктового бэклога заказчика (D002 Ryle).
   - **Контур VIII.2. Аудит операций, здоровье фабрики и кайдзен:**
     * `OpsAuditorService` — аудитор операций: действия строго на независимо проверенных фактах, верификация предусловий перед исполнением, воздержание при неполноте (D006 Goldman);
     * `FactorySelfHealthService` — непрерывная самодиагностика ресурсов фабрики (размер базы данных, пулы соединений, емкость очередей) с эскалацией в Кайдзен (D013 Wittgenstein);
     * `KaizenService` & `DefectJournalService` — жизненный цикл микро-улучшений PDCA, дедупликация предложений по `(projectId, category, targetComponent)` (D007, D012 Belnap).
   - **Контур VIII.3. Метрики потока, продвижение рычагов и исследование рынка:**
     * `FlowMetricsService` — верификация закона Литтла (WIP, throughput, cycle time), проектно-изолированная выборка без full-table scans (D010 Goldman);
     * `LeverPromotionService` — дисциплина продвижения рычагов от `observe_only` к действию строго по валидированным наблюдениям с немедленным понижением при расхождениях (D008 Popper);
     * `MarketResearchService` — исследование рынка через канонический `TargetContext.ORCHESTRATOR_SYSTEM` с детерминированным выбором несущего проекта (D002 Ryle).
   - **Контур VIII.4. Оркестрация, операционная правда и сохранение тождества:**
     * `PlannedWorkRecoveryService` — сохранение неизменной идентичности задачи при восстановлении, категорический запрет лавинообразного создания дубликатов (D002 Ryle, D010 Goldman);
     * `ContinuousOrchestrationService` — единый детерминированный тик оркестрации фабрики без несогласованных фоновых кронов (D004 Varzi);
     * `OperationalPolicyService`, `OperationalFlowCoreService`, `OperationalTruthService`, `OperationalAction`, `FlowSpineService` — операционная правда и хребет потока: деонтический заслон переходов, инвариант `done_is_not_delivery`, публикация свидетельств против собственного благополучия (D013 Wittgenstein, D006 Raz);
     * `TrustSnapshotService` — фиксация входных сигналов доверия с отложенным дозаполнением исходов (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается грубейшим нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Запрет поверхностных проверок («код компилируется — значит работает»). Заслоны обязаны проверять пограничные условия и инварианты отказа:
  * В `FalsificationCycleServiceTest` — доказательство недопустимости появления второго стороннего писателя у агрегатов `falsification_runs` и `code_integrity_findings`;
  * В `LeverPromotionServiceTest` — доказательство немедленного понижения рычага при противоречивом свидетельстве;
  * В `FlowMetricsServiceTest` — верификация согласованности метрик потока и отслеживание аномалий закона Литтла.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгое разделение проектных, продуктовых и общефабричных свидетельств;
  * Запрет подмены фактов о клиенте внутренними артефактами фабрики (`PlatformSelfReferenceDetector`);
  * Сохранение неразрывной цепочки свидетельств в `EvidenceCoherenceService`, `TrustSnapshotService` и `OpsAuditorService`: любое рискованное действие опирается на независимо собранные факты, а не на произвольные предположения.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секции VIII при проходящих тестах;**
  * Деонтический запрет лавинообразного порождения дубликатов задач и заявок (`PlannedWorkRecoveryService`);
  * Запрет произвольного перевода задач в статус выполненных без подтвержденного свидетельства доставки (`FlowSpineService`, `OperationalPolicyService`).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по всем ключевым классам-заслонам Секции VIII:
   - `FalsificationCycleServiceTest`, `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`;
   - `PlatformSelfReferenceDetectorTest`, `FactorySelfHealthServiceTest`, `KaizenServiceTest`;
   - `FlowMetricsServiceTest`, `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`, `MarketResearchServiceTest`, `MarketResearchServiceFalsificationTest`;
   - `PlannedWorkRecoveryServiceTest`, `ContinuousOrchestrationServiceTest`;
   - `OperationalTruthServiceTest`, `FlowSpineServiceTest`, `OperationalFlowCoreServiceTest`, `OperationalFlowCoreFalsificationTest`, `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`, `TrustSnapshotServiceTest`.
3. Зафиксировать форму образца и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md`.
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
