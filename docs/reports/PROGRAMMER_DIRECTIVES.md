# Директивы и наряд для L2 (2026-10-16 12:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секции XXXVII–XLIV («Сторож ТОС, Ресурсы модели, Метрики качества, Выметающий обход, Заслон экрана, Кэш корпуса, Верёвка ТОС, Очистка журнала») успешно заверены инженером (коммит `4a3a2dc`, 213/213 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции VIII («Самопроверка, Flow & Metrics, Операционная правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (25 тест-сьютов, 290 тестов в едином пакетном прогоне Docker Maven):
     * **Самопроверка, суточный цикл фальсификации и здоровье фабрики (38 тестов, 3 сьюта):**
       - `FalsificationCycleServiceTest` (28/28) — суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
       - `PlatformSelfReferenceDetectorTest` (7/7) — отсечение внутризаводского шума от клиентского продуктового бэклога (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002).
       - `FactorySelfHealthServiceTest` (5/5) — непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Связность свидетельств (ECHO) (26 тестов, 2 сьюта):**
       - `EvidenceCoherenceServiceTest` (21/21) и `EvidenceCoherenceV80TerminationFalsificationTest` (5/5) — релаксация графа свидетельств по алгоритму ECHO Тагарда без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Аудит операций и восстановление задач (26 тестов, 2 сьюта):**
       - `OpsAuditorServiceTest` (11/11) — аудит операций исключительно на независимо проверенных фактах с тройной валидацией предусловий (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006).
       - `PlannedWorkRecoveryServiceTest` (15/15) — восстановление упавших задач со строгим сохранением тождества задачи и запретом паразитного размножения (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002).
     * **Кайдзен и журнал дефектов (24 теста, 4 сьюта):**
       - `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5) — дедупликация предложений PDCA по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (`MARKUS_DZHILBERT_01_DEFECT_RECORD` / D007, `DZHON_MEKI_01_INUS_CONDITION` / D007).
     * **Метрики потока и продвижение рычагов (15 тестов, 3 сьюта):**
       - `FlowMetricsServiceTest` (4/4) — расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
       - `LeverPromotionServiceTest` (7/7) и `LeverPromotionServiceFalsificationTest` (4/4) — лестница продвижения рычагов от `observe_only` к активным действиям на основе согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Оркестрация и операционная политика потока (141 тест, 8 сьютов):**
       - `ContinuousOrchestrationServiceTest` (16/16) — единый детерминированный тик оркестрации фабрики без сторонних фоновых кронов (`MAYKL_BRATMAN_01_INTENTION_CONTINUITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
       - `OperationalTruthServiceTest` (17/17) — 4-значная логика истинности потока, динамика доверия от неустановленного (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010).
       - `FlowSpineServiceTest` (32/32) — детерминированный позвоночник потока, честное отражение метрик рантайма (`done_is_not_delivery`), хеширование свидетельств SHA-256 (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
       - `OperationalFlowCoreServiceTest` (6/6) и `OperationalFlowCoreFalsificationTest` (39/39) — деонтические заслоны переходов политики, матрица прав и обязанностей Flow Core (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
       - `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9) — деонтические отказы с проверяемой причиной (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Снимки доверия и рыночные исследования (20 тестов, 3 сьюта):**
       - `TrustSnapshotServiceTest` (9/9) — снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
       - `MarketResearchServiceTest` (3/3) и `MarketResearchServiceFalsificationTest` (6/6) — детерминированный сбор внешних рыночных измерений через каузальные задачи без мутации клиентского кода (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной/идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при повреждении заслонов готовности, попытках объявить «поток жив» без клиентского подтверждения или при рассинхроне закона Литтла.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и релайабилизм: четкая сегрегация уровней доказательств; свидетельства не поднимаются на уровень суждения без сохранения непрерывной каузальной цепочки порождения (lineage).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Запреты как код в операционной политике потока (`OperationalPolicyService`, `OperationalFlowCoreService`) — отказ с именованным деонтическим основанием.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * 4-значная логика истинности операционной правды: разделение `true`, `false`, `undetermined`, `not-applicable`. Отсутствие свидетельств означает `undetermined`, а не сбой.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Телеметрия рантайма и физическое состояние хоста всегда выше любого словесного нарратива; мониторинг здоровья фабрики (`FactorySelfHealthService`) фиксирует реальные факты рантайма.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary drift / Varzi]:**
  * Строгое монопольное владение: `FalsificationCycleService` единолично владеет агрегатами фальсификации; оркестратор владеет тиком цикла.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секции VIII (290 тестов, 25 сьютов в Docker Maven):**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest
   ```
2. **Нулевая мутация кода:**
   - 0 строк изменений в `src/main/java`, `src/main/resources`, `scripts/`.
3. **Фиксация результатов:**
   - Зафиксировать подтверждение идеального статуса в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
   - Записать отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
   - Сделать семантический коммит в фабрику: `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
