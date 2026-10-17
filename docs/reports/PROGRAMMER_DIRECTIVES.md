# Директивы и наряд для L2 (2026-10-17 03:28 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные механизмы, планировщик и читающие контроллеры, сайдкары вне бэкенда и хранилища потока») полностью заверен инженером (коммит `e4620eba`, 158/158 green в 26 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций V и VI («Свидетельство доставки и Решётка суждений: готовность сдачи, реальность поставки, запуск продукта, вердикты, суждение модели и продвижение рычагов»)** целиком (**279 тестов в едином непрерывном пакетном прогоне в Docker Maven [34 тест-сьюта]**):
     * **Группа 1: Секция V — Свидетельство доставки (141 тест, 13 сьютов):**
       - `ClientDeliverableReadinessServiceTest` (49/49 green), `ClientDeliverableReadinessServiceFalsificationTest` (6/6 green): замер клиентской ценности через эпики продукта, запланированные элементы, попытки выполнения задач и мердж-свидетельства (`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DeliveryPredicateAgreementTest` (7/7 green), `DeliveryVerificationPredicateTest` (6/6 green), `DeliveryQuestionRecordTest` (3/3 green), `TaskAcceptanceCriteriaGuardTest` (1/1 green): когерентность предикатов готовности к сдаче (`hasRequiredMergeEvidence`, `reachedMain`, `isAuxiliaryTask`), защита критериев приемки (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `PITER_GERDENFORS_13_CONVERSATION_MAXIM` / D007);
       - `DeliveryRealityProducerServiceFalsificationTest` (5/5 green), `DeliveryRealityLaw2CarrierChannelTest` (10/10 green), `DeliveryRealityLaw3CategoryErrorTest` (4/4 green), `DeliveryRealityLaw8SecondOrderRepairTest` (6/6 green), `DeliveryBriefZoneBoundaryTest` (11/11 green): конвертация нестыковок доставки в долговременные свидетельства, изолированный канал задач-носителей по Закону 2, демаркация второго порядка починок по Закону 8, изоляция зон брифов (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductLaunchabilityServiceTest` (22/22 green), `ProductLaunchabilityServiceFalsificationTest` (5/5 green): строгая проверка реального пути запуска, консистентности хранилищ и отсутствия фейковых UI-доменных записей (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 2: Секция VI — Суждение: решётка вердиктов, слой опровержений и продвижение рычагов (138 тестов, 21 сьют):**
       - `VerdictReconciliationTest` (11/11 green), `VerdictGateTest` (14/14 green), `VerdictLayerFalsificationTest` (7/7 green): 4-значная решётка истинности (`PERMIT`, `WITHHOLD`, `ABSTAIN`, `UNSETTLED`), деонтическая блокировка действий (`VerdictGate.evaluateActionProhibition`) с безусловным исключением задач восстановления (`KARL_POPPER_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `AcceptanceVerdictLayerTest` (7/7 green), `RuntimeVerdictLayerTest` (6/6 green), `InfrastructureVerdictLayerTest` (8/8 green), `AutonomousVerdictObservationServiceTest` (6/6 green): специализированные слои вердиктов, автономный надзор за переходами инвариантов и регистрация дефектов в `DefectJournalService` (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `FactoryJudgmentServiceTest` (13/13 green), `FactoryJudgmentServiceFalsificationTest` (7/7 green): регламент вызова внешней модели суждения только по непрочитанным переходам инвариантов с фиксацией в кайдзен (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DeliveredWorkJudgmentServiceTest` (8/8 green), `DeliveredWorkJudgmentServiceFalsificationTest` (6/6 green): верификация выполненной работы, PR и diff, порождение `WishlistEntity` при опровержении (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `EvidenceSelectedByCriteriaTest` (3/3 green), `CriteriaEvidenceSelectorFalsificationTest` (3/3 green): точный отбор критериев и контекстных свидетельств для модели суждений (`ELVIN_GOLDMAN_11_CONVERSATION_MAXIM` / D007);
       - `JudgmentAgentClientTest` (6/6 green), `JudgmentAgentClientLaw17Test` (4/4 green): клиент сайдкара суждений, строгое соблюдение Закона 17 и provenance вердикта (`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` / D009, D017);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green), `TocSubordinationLeverTest` (17/17 green), `LaunchabilityConstraintServiceFalsificationTest` (5/5 green): перевод рычагов из тени в активное влияние при устойчивом согласии (`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` / D007, `ACP-061 Hoare Triple Review`);
       - `UnsettledJudgmentGroundTest` (2/2 green), `DecompositionVerdictTest` (5/5 green): санитарная изоляция неопределенности и вердиктов декомпозиции (`KARL_POPPER_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D007, D009, D017)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны готовности сдачи (`ClientDeliverableReadinessServiceFalsificationTest`), реальности поставки (`DeliveryRealityProducerServiceFalsificationTest`), запуска продукта (`ProductLaunchabilityServiceFalsificationTest`), слоев вердиктов (`VerdictLayerFalsificationTest`), суждений фабрики и выполненной работы (`FactoryJudgmentServiceFalsificationTest`, `DeliveredWorkJudgmentServiceFalsificationTest`), продвижения рычагов (`LeverPromotionServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность цепочки данных: разделение артефактов готовности и фейковых записей UI, предикаты верификации поставки (`DeliveryVerificationPredicateTest`), сохранение происхождения критериев (`DeliveryQuestionRecordTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая блокировка действий фабрики через `VerdictGate`, исключение задач восстановления из блокировок, запрет на несанкционированные переходы стадий рычагов.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` & `DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` [D002 Invalid state / Austin & Ryle]:**
  * Сканирование категориальных ошибок: статус задачи, свидетельство сдачи, задачи-носители по Закону 2 и повторные сбои по Закону 3 суть разнородные факты реальности, подмена которых строго исключена (`DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`).
- **`KARL_POPPER_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Popper & Belnap]:**
  * 4-значная решётка истинности вердиктов (`PERMIT`, `WITHHOLD`, `ABSTAIN`, `UNSETTLED`), исключающая булево упрощение и поглощение неопределенности.
- **`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` & `ELVIN_GOLDMAN_11_CONVERSATION_MAXIM` [D007 Belief update ledger / Gärdenfors & Goldman]:**
  * Журнал обновления убеждений при согласовании рычагов (`LeverPromotionServiceTest`), селектор свидетельств по критериям (`CriteriaEvidenceSelectorFalsificationTest`).
- **`ALONZO_CHERCH_01_SUBSTITUTION_ORACLE` [D009 / D017 Substitution proof failure / Church]:**
  * Сохранение provenance при суждении через сайдкар без подмены эвристики на суждение модели (`JudgmentAgentClientLaw17Test`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций V и VI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,DeliveryPredicateAgreementTest,DeliveryRealityProducerServiceFalsificationTest,DeliveryRealityLaw2CarrierChannelTest,DeliveryRealityLaw3CategoryErrorTest,DeliveryRealityLaw8SecondOrderRepairTest,DeliveryBriefZoneBoundaryTest,ProductLaunchabilityServiceTest,ProductLaunchabilityServiceFalsificationTest,DeliveryQuestionRecordTest,DeliveryVerificationPredicateTest,TaskAcceptanceCriteriaGuardTest,VerdictReconciliationTest,VerdictGateTest,VerdictLayerFalsificationTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,InfrastructureVerdictLayerTest,AutonomousVerdictObservationServiceTest,FactoryJudgmentServiceTest,FactoryJudgmentServiceFalsificationTest,DeliveredWorkJudgmentServiceTest,DeliveredWorkJudgmentServiceFalsificationTest,EvidenceSelectedByCriteriaTest,CriteriaEvidenceSelectorFalsificationTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,TocSubordinationLeverTest,LaunchabilityConstraintServiceFalsificationTest,UnsettledJudgmentGroundTest,DecompositionVerdictTest
   ```
2. Подтвердить 100% зелёных тестов (**279/279 green в 34 тест-сьютах**, 0 failures, 0 errors, 0 skipped).
3. Соблюсти деонтический инвариант Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать запись секций в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (статус: ideal, замер в Docker Maven 279/279 green).
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк) и сделать коммит `feat(cluster-v-vi): complete Stage 4 batch verification (D008, D010, D006)`.
