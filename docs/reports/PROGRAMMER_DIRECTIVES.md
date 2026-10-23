# Директивы и наряд для L2 (2026-10-23 22:45 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секции III и IV («Отправка: задача уходит в работу» и «Ревью, гейт, слияние») полностью заверены и закрыты инженером (коммит `e8c06c19`, 561/561 green в 64 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:08 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий исполнительный контур фабрики — **Кластер Секций V и VI («Свидетельство доставки» и «Суждение: решётка вердиктов, слой опровержений и продвижение рычагов»)** целиком (**279 тестов в едином непрерывном пакетном прогоне в Docker Maven [36 тест-сьютов]**):
     * **Группа 1 — Секция V: Свидетельство доставки (14 сьютов, 140 тестов):**
       - `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`, `DeliveryPredicateAgreementTest`: предикат готовности ценности клиенту, строгое отсечение завершённых задач от реального слияния фич в ветку `main`;
       - `DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`, `DeliveryRealityLaw8SecondOrderRepairTest`, `DeliveryRealityProducerServiceFalsificationTest`, `DeliveryBriefZoneBoundaryTest`: трансляция несоответствий поставки в долговечные свидетельства, изоляция сервисных carrier-задач фабрики от клиентского скоупа, недопущение бесконечного порождения заявок на ремонт;
       - `ProductLaunchabilityServiceTest`, `ProductLaunchabilityServiceFalsificationTest`, `LaunchabilityConstraintServiceFalsificationTest`: гейт запускаемости продукта, валидация рантайма и согласованности датастора без фейковых записей фронтенда;
       - `DeliveryQuestionRecordTest`, `DeliveryVerificationPredicateTest`, `TaskAcceptanceCriteriaGuardTest`: нерушимые предикаты приёмки и критерии верификации.
     * **Группа 2 — Секция VI: Суждение — решётка вердиктов и арбитраж (11 сьютов, 73 теста):**
       - `VerdictGateTest`, `VerdictReconciliationTest`, `VerdictLayerFalsificationTest`: арбитраж и примирение вердиктов, запрет маскировки разногласий слоев;
       - `AutonomousVerdictObservationServiceTest`, `AcceptanceVerdictLayerTest`, `RuntimeVerdictLayerTest`, `InfrastructureVerdictLayerTest`: автономные наблюдатели и специализированные слои вердиктов с дельта-записью в журнал дефектов;
       - `DecompositionVerdictTest`, `UnsettledJudgmentGroundTest`, `UnsettledQuestionIsAskedAgainTest`, `UnsettledGroundNamesTheAskerTest`: декомпозиция вердиктов и дисциплина воздержания от действий при неопределенном основании (`UNSETTLED`).
     * **Группа 3 — Секция VI: Суждение — агенты, селекция свидетельств и рычаги (11 сьютов, 66 тестов):**
       - `DeliveredWorkJudgmentServiceTest`, `DeliveredWorkJudgmentServiceFalsificationTest`: суждение о сданной работе по диффу и коммитам с порождением вишлиста при опровержении;
       - `FactoryJudgmentServiceTest`, `FactoryJudgmentServiceFalsificationTest`, `JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`: внешний арбитраж фабрики, фильтрация непрочитанных переходов и семантическая демаркация;
       - `CriteriaEvidenceSelectorFalsificationTest`, `EvidenceSelectedByCriteriaTest`: отбор свидетельств строго по критериям без перегрузки промпта;
       - `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`, `TocSubordinationTruthTest`: лестница продвижения рычагов ТОС, переход состояний только по свежим свидетельствам.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовские заслоны фальсификации поставки: нельзя закрывать клиентский пункт требования несвязанной задачей в том же эпике (`ClientDeliverableReadinessServiceFalsificationTest`), фальсификация бутстрап-гейта запускаемости (`ProductLaunchabilityServiceFalsificationTest`), заслоны слоев вердиктов и суждений (`VerdictLayerFalsificationTest`, `FactoryJudgmentServiceFalsificationTest`, `DeliveredWorkJudgmentServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Каузальная цепочка свидетельств и надежность процесса суждения. Строгий лок уровней абстракции: задача (task) -> фича/эпик (feature) -> поставка клиенту (deliverable). Доверие выстраивается исключительно на независимо проверяемых артефактах, а не на самоотчётах агентов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический запрет признания готовности поставки без объективных свидетельств слияния реального кода в `main`;
  * Вето гейтов сдачи при наличии непогашенных противоречий в слоях вердикта (`VerdictGate`).
- **`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` [D002 Category error scan / Austin & Ryle]:**
  * Категориальная демаркация: статус задачи `TaskStatus.done` не равен готовности поставки клиенту. Завершение технической задачи фабрики не подменяет поставку продукта заказчику. Вспомогательные задачи изолируются в канале `CARRIER_CHANNEL`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная решётка вердиктов (`ALLOWED`, `WITHHELD`, `PROHIBITED`, `UNSETTLED`). При статусе `UNSETTLED` фабрика строго воздерживается от действий и производит повторный запрос оснований (`UnsettledJudgmentGroundTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Anti-mirror telemetry / Wittgenstein]:**
  * Анти-зеркальный приоритет физического состояния рантайма продукта (контейнеры, порты, схемы БД) над словесными нарративами.

---

## 3. Регламент исполнения инженера L2

1. **Команда верификации пакета:** Запустить единый непрерывный пакетный прогон всех 36 тест-сьютов Секций V и VI внутри контейнера Docker Maven:
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,DeliveryPredicateAgreementTest,DeliveryRealityLaw2CarrierChannelTest,DeliveryRealityLaw3CategoryErrorTest,DeliveryRealityLaw8SecondOrderRepairTest,DeliveryRealityProducerServiceFalsificationTest,DeliveryBriefZoneBoundaryTest,ProductLaunchabilityServiceTest,ProductLaunchabilityServiceFalsificationTest,LaunchabilityConstraintServiceFalsificationTest,DeliveryQuestionRecordTest,DeliveryVerificationPredicateTest,TaskAcceptanceCriteriaGuardTest,VerdictGateTest,VerdictReconciliationTest,VerdictLayerFalsificationTest,AutonomousVerdictObservationServiceTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,InfrastructureVerdictLayerTest,DecompositionVerdictTest,UnsettledJudgmentGroundTest,UnsettledQuestionIsAskedAgainTest,UnsettledGroundNamesTheAskerTest,DeliveredWorkJudgmentServiceTest,DeliveredWorkJudgmentServiceFalsificationTest,FactoryJudgmentServiceTest,FactoryJudgmentServiceFalsificationTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,CriteriaEvidenceSelectorFalsificationTest,EvidenceSelectedByCriteriaTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,TocSubordinationTruthTest
   ```
2. **Критерий приёмки:** Ровно 279/279 green в 36 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода сервисов, сущностей, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` (279/279 green, ideal);
   - Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md`;
   - Зафиксировать результаты семантическим коммитом `feat(delivery-and-verdict): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры категорически не взводить!
