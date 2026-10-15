# Пакетная директива L1/L3 -> L2: Ступень 4 (Секции V и VI: Свидетельство доставки, решётка суждения, опровержения, рычаги и контроль процессов)

**Дата:** 2026-10-15 12:12 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Настоящая директива охватывает 15 механизмов Ступени 4 `docs/ANTIGRAVITY_QUEUE.md` (Секции V и VI `docs/FACTORY_MECHANISMS.md`), финализируя Ступень 4:
   - **Секция V: Свидетельство доставки (Delivery Reality & Launchability):**
     * `ClientDeliverableReadinessService` (ядро)
     * `DeliveryRealityProducerService` (ядро)
     * `ProductLaunchabilityService` (ядро)
   - **Секция VI: Решётка суждения, слой опровержений, продвижение рычагов и контроль процессов:**
     * `VerdictLayer` (ядро)
     * `AcceptanceVerdictLayer` (ядро)
     * `RuntimeVerdictLayer` (ядро)
     * `DoctrineVerdictLayer` (ядро)
     * `InfrastructureVerdictLayer` (ядро)
     * `SixSigmaVerdictLayer`
     * `JudgmentAgentClient`
     * `FactoryJudgmentService`
     * `DeliveredWorkJudgmentService` (ядро)
     * `CriteriaEvidenceSelector` (ядро)
     * `LeverPromotionService`
     * `ProcessControlService`
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено трогать рабочий код при проходящих тестах!
   - Любая модификация рабочего кода допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего системный инвариант. Если тесты зеленые — код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` / `KARL_POPPER_03_TRUTH_STATUS_TABLE` [D008 False green, D012 Policy contradiction, Popper]:**
  * Трехзначная решетка истины: `PERMIT`, `WITHHOLD`, `ABSTAIN`. Отсутствие свидетельств переводится строго в воздержание (`ABSTAIN`) или удержание (`WITHHOLD`), но никогда в ложное разрешение (`PERMIT`).
  * `VerdictReconciliation`: монотонное сведение слоев суждения — добавление нового слоя может только сохранить или сузить разрешение, но не расширить его поверх отказов.
  * `ProductLaunchabilityService` и `ClientDeliverableReadinessService`: фальсификационный заслон доставки исключает подмену реальной ценности формальным прохождением тестов или фиктивными фронтенд-записями.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Надежная цепочка происхождения: доверие к готовности поставки опирается на верифицируемую цепочку PR, сессий и гейтов качества.
  * Лок уровней абстракции: независимость пяти слоев суждения (`AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `DoctrineVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer`). Ни один слой не подменяет факты другого.
  * `CriteriaEvidenceSelector`: изоляция контекста суждения внешнего агента — доставка только релевантных критериев и доказательств без шума и информационного переполнения.
  * `LeverPromotionService` и `PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER`: лестница доверия рычагов продвигается строго на основании подтвержденного согласия с эмпирическим исходом.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов доставки, суждения и контроля процессов при проходящих тестах;**
  * Деонтический запрет прямого вмешательства в код клиента фабрикой: выявленные пробелы транслируются исключительно в дедуплицированные заявки вишлиста через компилятор;
  * Запрет `VerdictGate` на выдачу разрешений на действия (`ActionProhibition`), если реконсиляция выявила удержание или критический долг;
  * Запрет `ProcessControlService` на смешение разнородных метрик процессов и нарушение границ фаз контроля (Phase-1 vs Phase-2).

---

## 3. Пакетный наряд по механизмам Секций V и VI

### Группа 1. Секция V: Свидетельство доставки и запускаемость
1. **`ClientDeliverableReadinessService` (ядро):**
   - **Проба:** Замерить предикаты ценности клиента, `hasRequiredMergeEvidence`, `reachedMain`, отсутствие подмены реальных поставок вспомогательными задачами (`isAuxiliaryTask`).
   - **Инвариант:** Рабочий код не трогать. Заслоны `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`, `DeliveryPredicateAgreementTest`.
2. **`DeliveryRealityProducerService` (ядро):**
   - **Проба:** Замерить фиксацию несоответствий доставки в долговечные свидетельства (`EvidenceNodeEntity`), ограничение глубины починки (`max_repair_depth`) и изоляцию каналов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`, `DeliveryRealityLaw8SecondOrderRepairTest`, `DeliveryRealityProducerServiceFalsificationTest`.
3. **`ProductLaunchabilityService` (ядро):**
   - **Проба:** Замерить однократность бутстрап-проверки запускаемости (`launchabilityCheckedAt`), непрерывную валидацию датастора (`checkDatastoreAgreement`) и дедупликацию вишлистов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `ProductLaunchabilityServiceTest`, `ProductLaunchabilityServiceFalsificationTest`.

### Группа 2. Секция VI: Решётка суждения, слой опровержений, рычаги и контроль процессов
4. **`VerdictLayer` и специализированные слои (`AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `DoctrineVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer`) (ядро):**
   - **Проба:** Замерить изоляцию пяти слоев, трехзначную решетку вердиктов (`PERMIT`, `WITHHOLD`, `ABSTAIN`), монотонную реконсиляцию в `VerdictReconciliation` и заслоны в `VerdictGate`.
   - **Инвариант:** Рабочий код не трогать. Заслоны `AcceptanceVerdictLayerTest`, `RuntimeVerdictLayerTest`, `InfrastructureVerdictLayerTest`, `VerdictLayerFalsificationTest`, `VerdictReconciliationTest`, `VerdictGateTest`, `AutonomousVerdictObservationServiceTest`.
5. **`JudgmentAgentClient` и `CriteriaEvidenceSelector`:**
   - **Проба:** Замерить селекцию доказательств по критериям приемки задач и изоляцию вызова прокси суждения (`judgment-proxy :8093`) без переполнения промпта.
   - **Инвариант:** Рабочий код не трогать. Заслоны `JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`.
6. **`FactoryJudgmentService` и `DeliveredWorkJudgmentService` (ядро):**
   - **Проба:** Замерить аудит переходов инвариантов фабрики и суждение по сданным задачам (diff, PR, acceptance criteria) с созданием `delivery_refuted` вишлистов при опровержении.
   - **Инвариант:** Рабочий код не трогать. Заслоны `FactoryJudgmentServiceTest`, `FactoryJudgmentServiceFalsificationTest`, `DeliveredWorkJudgmentServiceTest`, `DeliveredWorkJudgmentServiceFalsificationTest`.
7. **`LeverPromotionService`:**
   - **Проба:** Замерить сбор наблюдений от TOC, аккаунтов, Six Sigma и Kaizen, лестницу стадий рычагов (`OBSERVE`, `SHADOW`, `CANARY`, `ACTIVE`) и откат при расхождениях.
   - **Инвариант:** Рабочий код не трогать. Заслоны `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`.
8. **`ProcessControlService`:**
   - **Проба:** Замерить контрольные карты процессов, границы Western Electric rules, изоляцию фаз мониторинга и запрет на произвольные сигналы вне установленных пределов.
   - **Инвариант:** Рабочий код не трогать. Заслоны `ProcessControlServiceTest`, `ProcessControlServiceFalsificationTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов в Docker Maven (`-m 2g`).
2. Внести отметки `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(judgment-delivery): complete Stage 4 batch verification (D008, D010, D006)`.
