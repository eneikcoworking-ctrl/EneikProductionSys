# Такт L2: Ведущий инженер (2026-09-29 00:12 UTC)

1. Фальсифицирующий замер Ступени 2: V19/Marcus Single Active Project & Wittgenstein Account Pool (D002, D013, D007):
   - Заслон RUT_BARKAN_MARKUS_01 (D002): подтверждён инвариант ровно одного активного проекта (строго новейший active по createdAt); `retirePriorActiveProjectsLocally` переводит предыдущий проект в frozen, закрывает незавершенные задачи с причиной суперсессии, сохраняет терминальные задачи (Law 20 immutability) и освобождает повисшие клеймы; `resolveProject(null)` strictly адресует активный проект (RUT_BARKAN_MARKUS_18).
   - Заслон LYUDVIG_VITGENSHTEYN_14 & DZHON_SERL_05 (D013, D007): проверена структура `AccountEntity` (enabled=true по умолчанию); статус `decommissioned` делает аккаунт не-оперативным и недоступным; квалификация пула отсекает проектные и выключенные аккаунты; доступность по проектам исключает чужие и декоммиссионированные записи.
   - Разработан тест-заслон `V19AccountPoolAndSingleActiveProjectFalsificationTest` (8 тестов: 3 Marcus Single Project, 5 Wittgenstein/Searle Account Pool).
2. Заслон качества (100% green в Docker Maven 3.9.9 Temurin-21, -m 2g):
   - Прогон `V19AccountPoolAndSingleActiveProjectFalsificationTest` (8/8) — BUILD SUCCESS (0 Failures, 0 Errors, 44s).
3. Инварианты хоста и рантайма:
   - Диск: 65% (<70%), память в норме (avail 558Mi). Фабрика (:8080) и продукт (:18080) UP (healthy, аптайм >11d).
4. Следующий такт:
   - Проверить `MANAGER_STATE.md`, взять следующий механизм из очереди Ступени 2, покрой тестом-заслоном.
