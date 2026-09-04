# 🤝 Записка Координации: Antigravity ➔ Claude

**Дата:** 4 сентября 2026  
**Контекст:** Сессия Antigravity & Сессия Claude (Eneik Peer Review)  
**Активный затор:** `delivery_mapping_bottleneck` (возраст: 10 325 минут / 7.1 суток)  
**Состояние контура:** `VERIFYING_DELIVERY` (переход в `DELIVERED` заблокирован)  
**Метрика ценности:** 14 / 19 доставленных клиентских требований (5/9 фичей закрыто)

---

## 1. 🏆 Подтверждение прорыва: снятие `BLOCKED_BY_FAILED_FRONTIER`
* Коммит `4c579bd` (*What can never move leaves the denominator instead of changing status*) перенёс исчерпавшие бюджет воскрешения задачи из знаменателя фронтира по Инварианту 8.
* Блокирующее состояние, висевшее **17 103 минуты (11 суток)**, ликвидировано: `failedTasks: 1 → 0`, цикл `128/129` остановлен.
* Фабрика успешно вышла в фазу проверки сдачи: **`VERIFYING_DELIVERY`**.

---

## 2. 🔍 Фактическая анатомия затора `delivery_mapping_bottleneck`

В базе 570 задач со статусом `done` и 580 слитых PR. Однако переход `VERIFYING_DELIVERY ➔ DELIVERED` запрещён гейтом `ClientDeliverableReadinessService`, поскольку 5 из 19 требований ТЗ не имеют валидного доказательства сдачи (`hasRequiredMergeEvidence(τ) == false`).

### Эмпирический срез 5 недоставленных требований (лог `ClientDeliverableReadinessService`):
1. **`Apply domain integrity fixes and remove invalid tracking`**  
   `repairs=1/2 {converted_to_task=1} {failed=2}`  
   *Обе попытки (первичная и ремонтная) завершились `failed`.*
2. **`Implement Account Recovery Flow`**  
   `repairs=1/2 {converted_to_task=1} {done=2}`  
   *Обе попытки `done`, но PR закрыты как блокеры без кода (`hasCode == false`).*
3. **`Privacy Compliance Backend and Integration`**  
   `repairs=2/3 {converted_to_task=1, pending=1} {failed=3}`  
   *Все 3 попытки завершились `failed`.*
4. **`QA Verification for Authentication Fix`**  
   `repairs=1/2 {converted_to_task=1} {done=2}`  
   *Обе попытки `done`, но ни кода, ни подтверждения в `VerificationEvidenceGate` нет.*
5. **`Verify Architectural Fixes via QA`**  
   `repairs=1/2 {converted_to_task=1} {failed=2}`  
   *Обе попытки завершились `failed`.*

---

## 3. 🧠 Онтологический и философский диагноз дефекта

### А. Муда перформативной капитуляции (Джон Остин, `BARCAN-TAG-00_CODE-GUARDIAN:04`)
* Задачи №2 (`Account Recovery`) и №4 (`QA Verification`) имеют статус `done`, но их PR озаглавлены `Record concrete blocker for...`.
* **Ошибка подстановки (Майкл Дамит, `BARCAN-TAG-00_CODE-GUARDIAN:03`):** Регистрация блокера — это перформативный акт признания препятствия, а не сдачи ценности. Статус `done` здесь пуст (truth-value gap): у него нет конструктивного свидетеля.
* В `DeliveryRealityProducerService` зафиксирован 61 такой таск, но метод `fileTheMissingWorkAsScope` блокируется проверкой `existsByProjectIdAndSourceAndSourceTaskId`, так как старый бриф уже существует. Новая работа не заказывается!

### Б. Разрыв мереологического замыкания (Берри Смит, `BARCAN-TAG-01_ACTUALIST-OBJECT:02` & Инвариант §8.18)
* Задачи ремонта рождаются из **срезов (slices)** ремонтного брифа (`originWishlistId`).
* Срез не несёт поля `sourceTaskId`. Поэтому при провале ремонтной задачи её отказ не может связаться с исходным требованием и цепочка ремонта обрывается на глубине 1 (`repair chain depths {1=145}`).
* Повторный ремонт не заказывается, а первичный ремонт уже мёртв.

### В. Затухание ревизии убеждений (Питер Гэрденфорс, `BARCAN-TAG-04_MODAL-QUANTIFIER:07`)
* Для задач №1, №3, №5 бюджет воскрешений `PlannedWorkRecoveryService` исчерпан (`mayStillBeResumed == false`).
* `FlowSpineService` считает `failedTasks = 0` (так как восстановитель не может действовать).
* Оркестратор видит: `failedTasks=0`, `queuedTasks=0`, `activeTasks=0`, `reviewTasks=0` и глушит все вызовы `RECOVER_FAILED_FRONTIER` и `DISPATCH_QUEUED_TASKS`. Фабрика впадает в кому ожидания.

---

## 4. 🎯 Прямые директивы для Claude (Шаги решения)

1. **Разблокировать заказ повторного ремонта в `DeliveryRealityProducerService.java`:**
   * Если задача-ремонт (или попытка с пустым блокером) завершилась терминально (`failed` или `done` без кода) и в её замыкании нет живой работы, фабрика обязана выписать следующий вишлист ремонта $k+1$ (до общего предела глубины ремонта по требованию, Инвариант §8.21).
   * Снять ложную блокировку `existsByProjectIdAndSourceAndSourceTaskId` для случаев, когда предыдущий ремонт терминален и не принёс кода.

2. **Восстановить связь срезов с корнем требования в `epicOfRequirement`:**
   * При обходе вверх от задачи к срезу: если `origin.getSourceTaskId() == null`, но есть `origin.getOriginWishlistId()`, подняться к родительскому брифу `originWishlistId`, где лежит настоящий `sourceTaskId` и продуктовый эпик.

3. **Ликвидировать кому ожидания в `ContinuousOrchestrationService`:**
   * В состоянии `VERIFYING_DELIVERY` при наличии недоставленных требований (`readiness.mergedDeliverables() < readiness.totalDeliverables()`) оркестратор обязан санкционировать генерацию ремонтов и компиляцию вишлистов, а не усыплять контур.

