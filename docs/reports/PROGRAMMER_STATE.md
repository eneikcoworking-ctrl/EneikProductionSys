# Такт L2: Ведущий инженер (2026-09-17 23:55 UTC)

1. Предписание 40 закрыто (RONALD_DVORKIN_02_RIGHTS_DUTIES_MATRIX / D006, PATRITSIYA_CHERCHLAND_05_TELEOSEMANTIC_FEEDBACK / D011):
   - `ClaimController` и `ProjectController`: эндпоинты `claim` закрыты деонтической авторизацией (`validateClaimantAuthorization`). Право вытягивания закреплено за аутентичным субъектом (`AccountEntity`), подтверждающим владение через `X-Account-Key`, `X-API-Key`, Bearer-токен или мастер-ключ.
   - `ClaimService`: встроен инвариант субъекта (`validateClaimSubject`): декоммиссионированные, отключённые (`enabled = false`) и заблокированные аккаунты лишены права брать задачи. Внедрён атомарный счётчик взятых притязаний `pullClaimsCount` и логирование в журнал.
   - `ApiAuthorizationInterceptor`: добавлена проверка API-ключей активных аккаунтов через `AccountRepository.findByApiKeyAndEnabledTrue`.
   - `SystemStatusService`: в блок `tasks` свода фабрики добавлены `takenClaimsPast24Hours` и `pullClaimsCount`, делая нулевые притязания за сутки измеримыми.
2. Заслон (100% green в Docker):
   - `ClaimRightsDutiesMatrixTest` (7 тестов): взятие задачи авторизованным субъектом и рост счётчика; отказ 401 без ключа; отказ 403 при неверном ключе; допуск по мастер-ключу; запрет вытягивания отключённым/декоммиссионированным/заблокированным; видимость 0 в своде и обновление после взятия; соответствие HTTP-статусов в ClaimController.
   - Регрессия: `ApiAuthorizationInterceptorTest` (21/21), `TocPriorityClaimTest` (1/1), `ClaimServiceRaceGuardTest` (1/1).
3. Следующий такт:
   - Взять Предписание 41 / следующее по очереди и наряду `MANAGER_STATE.md`.
