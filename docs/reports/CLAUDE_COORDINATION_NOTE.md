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

## 4. 💡 Философские подсказки и варианты решений (на усмотрение Клода)

*Инженерное решение и выбор конкретной реализации остаются полностью за тобой. Ниже приведены возможные варианты устранения онтологического расхождения:*

1. **Устранение Truth-Value Gap в `DeliveryRealityProducerService.java` (Остин & Дамит):**
   * *Проблема:* `existsByProjectIdAndSourceAndSourceTaskId` считает ремонт «уже заказанным навсегда», даже если ремонт завершился терминально и не принёс кода.
   * *Вариант А (Инвариант §8.21, счетчик глубины):* Разрешить заказ ремонта следующего порядка $k+1$ для терминальных попыток, если текущая глубина цепи меньше предельной ($k < K$).
   * *Вариант Б (Снятие перформативного `done`):* Если PR задачи слит без кода (пустой блокер), не считать такую задачу де-факто закрывающей бриф ремонта.

2. **Мереологическая непрерывность срезов (Смит & Саймонс, §8.18):**
   * *Проблема:* Задачи ремонта привязаны к срезам (`originWishlistId`), которые теряют `sourceTaskId`.
   * *Подсказка:* При обходе графа улик (`epicOfRequirement` или `repairClosure`) учитывать транзитивность: задача $\to$ срез $\to$ родительский бриф ремонта $\to$ исходная задача $\to$ продуктовый эпик. Это восстановит целостность цепи без необходимости масштабной миграции базы данных.

3. **Преодоление комы ожидания (Поппер & Гэрденфорс):**
   * *Проблема:* При `failedTasks = 0` (потому что все отказы исчерпали прямые воскрешения) оркестратор засыпает в `VERIFYING_DELIVERY`, не давая шанса генерации и компиляции новых ремонтов.
   * *Подсказка:* Проверить предписания `OperationalPolicyService` для действий `CHECK_COVERAGE_AUDITS` / `ORCHESTRATE` в состоянии `VERIFYING_DELIVERY`: если сдача не завершена (`14/19`), фабрика должна генерировать фальсифицирующие или ремонтные стимулы, а не простаивать в ожидании.


