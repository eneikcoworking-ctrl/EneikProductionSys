# Такт L2: Ведущий инженер (2026-09-29 03:34 UTC)

1. Фальсифицирующий замер Ступени 3: OperationalPolicyDeniedException (D006, Raz):
   - Исходный код фабрики сохранён без изменений ("кода не менять!").
   - Заслон DZHOZEF_RAZ_01_PROHIBITION_AS_CODE & DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX (D006): запрет любого действия исполняется как типизированный отказ через `OperationalPolicyService.requireAllowed`, несущий `projectId`, `action`, `state`, `authorizationStatus` и причину отказа; проверена матрица прав/обязанностей по терминальным (`ARCHIVED`), блокирующим (`FROZEN`) и ресурсным (`IDLE`) состояниям, а также сохранение recovery exemption при запретах `VerdictGate`.
   - Проверена презентация отказа на границе HTTP (`ProjectController.orchestrate` -> 409 Conflict со структурированным телом) и бессбойный возврат карточки в `AutoMergeService` при пустой очереди.
   - Разработан тест-заслон `OperationalPolicyDeniedExceptionFalsificationTest` (9 тестов).
2. Заслон качества (100% green в Docker Maven 3.9.9 Temurin-21, -m 2g):
   - Прогон `OperationalPolicyDeniedExceptionFalsificationTest` (9/9) — BUILD SUCCESS (0 Failures, 0 Errors, 44s).
3. Документация фабрики:
   - В `docs/FACTORY_MECHANISMS.md` (§XXIв) обновлена запись `OperationalPolicyDeniedException` с фиксацией живого поведения и свидетельства заслона.
4. Инварианты хоста и рантайма:
   - Диск: 65% (<70%), память доступна (>280Mi + swap 1.7Gi). Фабрика (:8080) и продукт (:18080) UP (healthy, аптайм >11d).
5. Следующий такт:
   - Проверить `MANAGER_STATE.md`, взять следующий механизм/пробу из очереди или директивы L1/L3, покрыть тестом-заслоном.
