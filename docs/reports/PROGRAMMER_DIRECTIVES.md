# Директивы и наряд для L2 (2026-10-16 08:30 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Архитектурные предписания для L2 и Цех дизайна») успешно заверены инженером (коммит `e69133c`, 272/272 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином пакетном прогоне Docker Maven):
     * **Контур фальсификации и целостности:**
       - `FalsificationCycleServiceTest` (28/28), `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5), `OpsAuditorServiceTest` (11/11), `PlatformSelfReferenceDetectorTest` (7/7), `FactorySelfHealthServiceTest` (5/5);
     * **Контур Кайдзен и журнал дефектов:**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5);
     * **Контур потока, метрик и продвижения рычагов:**
       - `FlowMetricsServiceTest` (4/4), `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4), `PlannedWorkRecoveryServiceTest` (15/15), `ContinuousOrchestrationServiceTest` (16/16);
     * **Контур операционной правды и ядра потока:**
       - `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32), `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39);
     * **Контур операционных политик и снимков доверия:**
       - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9), `TrustSnapshotServiceTest` (9/9), `MarketResearchServiceTest` (3/3), `MarketResearchServiceFalsificationTest` (6/6).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть основательными, строгими и доказательными.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D013, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты заслонов обязаны воспроизводимо падать при нарушении инвариантов связности свидетельств, ложных рантайм-отчетах или искажении операционной правды (`OperationalFlowCoreFalsificationTest`, `EvidenceCoherenceV80TerminationFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка свидетельств: разграничение сырой телеметрии и агрегированных метрик надежности (`TrustSnapshotService`, `OperationalTruthService`); строгое сохранение происхождения фактов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимые деонтические заслоны доступа к операциям ядра фабрики через `OperationalPolicyService` и типизированные отказы `OperationalPolicyDeniedException`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole ownership / Varzi]:**
  * Топологическая монополия владения состоянием: каждый агрегат и таблица пишутся строго уполномоченным сервисом; ликвидация разделяемых мутаций.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Разделение категорий: факты о заказчике vs внутренняя телеметрия фабрики; исключение подмены категорий сбоя.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности (`TRUE`, `FALSE`, `UNDETERMINED`, `CONTRADICTORY`) в статусах проверок и вердиктов.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало: замеры состояния, опрос метрик и чтение операционной правды не оставляют побочных мутаций в базе данных.
- **`MARGRET_GILBERT_01_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional defect record / Gilbert]:**
  * Фиксация дефектов и инцидентов в долговечном институциональном реестре (`DefectJournalService`).

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секции VIII:**
   - **Docker Maven (290 тестов, 25 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
     ```
2. **Верификация результата:**
   - Строго **290/290 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секции VIII в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секции VIII в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
