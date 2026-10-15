# Пакетная директива L1/L3 -> L2: Секция V (Свидетельство доставки)

**Дата:** 2026-10-15 17:15 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция IV («Ревью, гейт, слияние») полностью подтверждена и закрыта инженером (235/235 green в коммите `2c5f903`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию V («Свидетельство доставки»)**:
   - **Контур V.1. Измерение готовности поставки (Austin D002, Popper D008, Goldman D010):**
     * `ClientDeliverableReadinessService` — расчет клиентской ценности через иерархию фич (`feature-level ratio`), строгая демаркация `TaskStatus.done` и реальной сдачи с кодом (`hasRequiredMergeEvidence`, `reachedMain`), фильтрация вспомогательных (auxiliary/spike/decision) задач (`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
   - **Контур V.2. Продюсер операционной реальности и ограничение ремонта (Austin D002, Raz D006, Wittgenstein D013):**
     * `DeliveryRealityProducerService` — преобразование расхождений сдачи в долговечные улики (`EvidenceNodeEntity`), изоляция несущих задач фабрики в канале `CARRIER_CHANNEL` без заказа продуктового скоупа, дедуплицированные заявки на ремонт с наследованием продуктового эпика, терминальные факты журнала дефектов (`DefectJournalEntity`) при исчерпании `max_repair_depth` (`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
   - **Контур V.3. Запускаемость продукта и согласованность датастора (Popper D008, Wittgenstein D013, Raz D006):**
     * `ProductLaunchabilityService` — эмпирическая верификация репозитория клиента на запускаемость (`docker-compose.yml`, `Dockerfile`, `frontend/package.json`), проверка непротиворечивости датастора (`compose` vs `application.properties` vs DB contract), детекция фейковых фронтенд-записей, однократный гейт бутстрапа (`launchabilityCheckedAt`) и вызов из оркестратора `ContinuousOrchestrationService` (`OperationalAction.CHECK_LAUNCHABILITY`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы Секции V уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D013, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация готовности: запланированный пункт требования клиента не может быть закрыт чужой задачей в том же эпике (`sourceTaskId` / `originWishlistId`);
  * Фальсификация запускаемости: расхождение датастора между `docker-compose.yml` и `application.properties` либо наличие фейковых записей во фронтенде опровергают ложную запускаемость.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Строгий лок уровней абстракции: разделение фактов закрытия задачи фабрики, наличия реального кода в `main`, свидетельств запускаемости рантайма и клиентской приёмки;
  * Каузальная цепочка надежности: сохранение происхождения и свежести улик в `EvidenceNodeEntity`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секции V при проходящих тестах;**
  * Категорический деонтический запрет прямого вмешательства фабрики в код клиента: выявленные дефекты транслируются исключительно в дедуплицированные вишлисты через компилятор;
  * Идемпотентность улик: длящееся несоответствие обновляет метку свежести существующей улики без мультипликации строк.
- **`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` [D002 Category error, Austin]:**
  * `TaskStatus.done` не равен готовности поставки (delivery readiness);
  * Несущие задачи фабрики (carrier tasks) изолируются в `CARRIER_CHANNEL` и никогда не порождают продуктового скоупа.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Анти-зеркало рантайма: эмпирическая проверка реального содержимого репозитория в GitHub и физического рантайма продукта;
  * Тишина при сомнении и на нулевом дне (`zeroShippedDeliverablesStaysSilent`).
- **`KARL_POPPER_03_TRUTH_STATUS_TABLE` [D012 Popper Truth Status Table]:**
  * Трехзначная демаркация состояний поставки (`delivered`, `undelivered`, `unknown`); коэффициент ценности рассчитывается строго на уровне фич (`completeFeatures / totalFeatures`).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции V в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции V:
   - `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`;
   - `DeliveryPredicateAgreementTest`;
   - `DeliveryRealityLaw2CarrierChannelTest`, `DeliveryRealityLaw3CategoryErrorTest`, `DeliveryRealityLaw8SecondOrderRepairTest`;
   - `DeliveryRealityProducerServiceFalsificationTest`;
   - `ProductLaunchabilityServiceTest`, `ProductLaunchabilityServiceFalsificationTest`;
   - `DeliveryBriefZoneBoundaryTest`, `DeliveryQuestionRecordTest`, `DeliveryVerificationPredicateTest`.
3. Зафиксировать форму образца и актуализировать статус Секции V в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal, добавить `### Итог Секции V`).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
