# Директивы и наряд для L2 (2026-10-16 13:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции VII (TOC) и VIII (Flow & Metrics / Самопроверка) полностью замерены и закрыты инженером (коммит `3cd46d8`, 290/290 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера механизмов фабрики — **Секции V («Свидетельство доставки») и Секции VI («Суждение: решётка суждения, слой опровержений и продвижение рычагов»)** целиком (35 тест-сьютов, 274 теста в едином непрерывном пакетном прогоне Docker Maven):
     * **Группа 1: Свидетельство доставки и физическая реальность сдачи (Секция V — 13 сьютов):**
       - `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`, `DeliveryPredicateAgreementTest`: подтверждение готовности артефактов заказчика без галлюцинаций завершённости (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DeliveryRealityProducerServiceFalsificationTest`, `DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`, `DeliveryRealityLaw8SecondOrderRepairTest`, `DeliveryBriefZoneBoundaryTest`: производство свидетельств физической реальности, отсечение носителей, блокировка категориальных ошибок (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ProductLaunchabilityServiceTest`, `ProductLaunchabilityServiceFalsificationTest`: демаркация готовности продукта к запуску на честных внешних свидетельствах (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DeliveryQuestionRecordTest`, `DeliveryVerificationPredicateTest`, `TaskAcceptanceCriteriaGuardTest`: инварианты приёмочных критериев задач и моделей предикатов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
     * **Группа 2: Решётка суждений, слои вето и арбитраж (Секция VI — 11 сьютов):**
       - `VerdictGateTest`, `VerdictReconciliationTest`, `VerdictLayerFalsificationTest`, `AutonomousVerdictObservationServiceTest`: многослойная решётка вердиктов, примирение противоречий и протокол автономных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AcceptanceVerdictLayerTest`, `RuntimeVerdictLayerTest`, `InfrastructureVerdictLayerTest`: независимые слои вето приёмки, рантайма и инфраструктуры (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `DecompositionVerdictTest`, `UnsettledJudgmentGroundTest`, `UnsettledQuestionIsAskedAgainTest`, `UnsettledGroundNamesTheAskerTest`: строгая семантика открытых вопросов и оснований отказа (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
     * **Группа 3: Агенты суждения, селекция критериев и продвижение рычагов (Секция VI — 11 сьютов):**
       - `DeliveredWorkJudgmentServiceTest`, `DeliveredWorkJudgmentServiceFalsificationTest`: суждение по сданным результатам на основе эмпирических фактов (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `FactoryJudgmentServiceTest`, `FactoryJudgmentServiceFalsificationTest`: верховный фабричный арбитраж (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004);
       - `JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`: взаимодействие с агентом суждений под защитой закона максимальности информации Law 17 (`POL_GRAJS_01_CONVERSATIONAL_MAXIMS` / D007);
       - `CriteriaEvidenceSelectorFalsificationTest`, `EvidenceSelectedByCriteriaTest`: беспристрастный отбор свидетельств по строгим критериям (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`, `TocSubordinationTruthTest`: лестница продвижения рычагов подчинения TOC строго по согласованным наблюдениям (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D007, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны готовности сдачи (`ClientDeliverableReadinessServiceFalsificationTest`), фальсификация производства реальности (`DeliveryRealityProducerServiceFalsificationTest`), фальсификационные слои вердиктов (`VerdictLayerFalsificationTest`), фальсификация суждений фабрики (`FactoryJudgmentServiceFalsificationTest`, `DeliveredWorkJudgmentServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность каузальной цепочки свидетельств: цепочка подтверждения готовности сдачи `ClientDeliverableReadinessService`, независимые уровни вердиктов (acceptance, runtime, infrastructure), беспристрастная селекция свидетельств `CriteriaEvidenceSelector`, лестница рычагов `LeverPromotionService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические заслоны отказов и матрицы прав и обязанностей: `TaskAcceptanceCriteriaGuard`, разграничение зон брифов `DeliveryBriefZoneBoundaryTest`, защита от неправомочных вердиктов.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Отсечение категориальных ошибок: недопущение смешения задач-носителей с продуктовыми задачами (`DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`).
- **`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` [D004 Mereological collapse / Bratman]:**
  * Согласованное кооперативное действие арбитров и сервисов в `FactoryJudgmentService` и реконсиляция решётки вердиктов.
- **`POL_GRAJS_01_CONVERSATIONAL_MAXIMS` [D007 Institutional defect record / Grice]:**
  * Закон максимальности информации Law 17 в `JudgmentAgentClientLaw17Test` — полный и исчерпывающий контекст суждений без сокрытия дефектов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для вердиктов и предикатов доставки (`DeliveryVerificationPredicateTest`, `VerdictGateTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `ProductLaunchabilityService` и `AutonomousVerdictObservationService`: фиксация отказов и неподтверждённых состояний независимо от нарратива агента.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 35 тест-сьютов Секций V и VI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,DeliveryPredicateAgreementTest,DeliveryRealityProducerServiceFalsificationTest,DeliveryRealityLaw2CarrierChannelTest,DeliveryRealityLaw3CategoryErrorTest,DeliveryRealityLaw8SecondOrderRepairTest,DeliveryBriefZoneBoundaryTest,ProductLaunchabilityServiceTest,ProductLaunchabilityServiceFalsificationTest,DeliveryQuestionRecordTest,DeliveryVerificationPredicateTest,TaskAcceptanceCriteriaGuardTest,VerdictGateTest,VerdictReconciliationTest,VerdictLayerFalsificationTest,AutonomousVerdictObservationServiceTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,InfrastructureVerdictLayerTest,DeliveredWorkJudgmentServiceTest,DeliveredWorkJudgmentServiceFalsificationTest,FactoryJudgmentServiceTest,FactoryJudgmentServiceFalsificationTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,CriteriaEvidenceSelectorFalsificationTest,EvidenceSelectedByCriteriaTest,UnsettledJudgmentGroundTest,UnsettledQuestionIsAskedAgainTest,UnsettledGroundNamesTheAskerTest,DecompositionVerdictTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,TocSubordinationTruthTest
   ```
2. Подтвердить строго **274/274 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, миграций и сущностей (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(delivery-and-verdict): complete Stage 4 batch verification (D008, D010, D006)`.
