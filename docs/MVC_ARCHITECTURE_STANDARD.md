# MVC Architecture Standard

## Три слоя и их границы

### MODEL — владеет всеми бизнес-правилами, состоянием, данными
Состоит из трёх подслоёв, каждый со своей обязанностью:
- **Entities** (models/persistence/*) — только структура данных,
  никакой бизнес-логики, никаких вызовов внешних сервисов.
- **Repositories** — только доступ к данным (SQL/JPA-запросы),
  Forbidden содержать условия, кодирующие бизнес-решение (например
  Forbidden: repository-метод, который решает "достаточно ли этот
  PR безопасен для merge" — это не доступ к данным, это бизнес-правило).
- **Domain Services** (ClaimService, GateOrchestrator,
  TechnicalLeadCompiler, RiskLevelCalculator, AutoMergeService,
  RoleCapabilityLoader, BottleneckAwarePriorityService и аналогичные)
  — здесь и только здесь живут решения "что должно произойти".
  Любое решение, определяющее исход (какой аккаунт получит задачу,
  мержить ли PR, компилировать ли wishlist) — Obligatory находится
  в Domain Service, Forbidden находиться в Controller, конфигурации
  (application.properties/yml) или во фронтенде.

### CONTROLLER — тонкий переводчик HTTP <-> Model
- Только @RestController классы.
- Obligatory: принимает HTTP-запрос, валидирует форму запроса,
  вызывает РОВНО ОДИН метод Model-слоя (или короткую композицию без
  собственной логики принятия решений), маппит результат в DTO ответа.
- Forbidden: любой if/else, кодирующий бизнес-смысл (пример:
  "if risk_level == low then merge" — это решение принадлежит
  Model, не Controller); прямой доступ к Repository, минуя Service;
  хардкод значений, влияющих на поведение (пример уже найденного
  дефекта: default accountId=null с fallback на глобальный
  jules_api_key/organization в application.properties — это
  бизнес-решение "какой аккаунт использовать", спрятанное в
  конфиг-файле вместо Model-слоя).
- Контроллер должен быть заменяем: тот же Model должен быть
  достижим через другой контроллер (CLI, scheduled job, другой API)
  без дублирования логики.

### VIEW — фронтенд Svelte + форма JSON, которую отдаёт backend
- DTO — это "view model": backend формирует данные специально для
  отображения.
- Svelte-компоненты Forbidden вычислять бизнес-решения самостоятельно
  (пример: Forbidden считать risk_level на клиенте, Forbidden решать
  "готов ли проект к принятию" через собственную логику на сырых
  булевых полях — это должно быть полем, которое backend уже посчитал
  и отдал).
- Любое "включена ли кнопка" — управляется полем, которое вернул
  backend, не клиентской бизнес-логикой.
- Компонент имеет право только на презентационную логику:
  форматирование, вёрстку, цвета из Design System.

## Фоновые процессы (scheduled jobs) — тоже часть Model
LeaseWatchdog, ContinuousOrchestrationService, AutoMergeService CI
polling — это Model-слой, работающий без HTTP. Obligatory: вызывать
ТЕ ЖЕ методы Domain Service, что вызвал бы Controller — Forbidden
дублировать или обходить бизнес-правило "для эффективности"
(пример уже найденного дефекта: orchestrate/dispatch обходил
ClaimService, вместо того чтобы использовать тот же атомарный
механизм выбора свободного аккаунта, что claim API использует для
задач).

## Известные подтверждённые нарушения (стартовый список для аудита,
не исчерпывающий)
1. ProjectFlowService.orchestrate() вызывал dispatch с accountId=null,
   полагаясь на хардкод default organization/jules_api_key в
   application.properties — бизнес-решение "какой аккаунт" жило в
   конфиге, не в Model. (Статус: исправление уже поставлено отдельной
   сессией — при аудите проверить, реально ли закрыто.)
2. RoleCapabilityLoader существовал в Model, но не вызывался
   JulesDispatchService при формировании промпта — Model не
   консультировал собственное закодированное знание (роль-хартию)
   перед выдачей результата. (Статус: исправление поставлено
   отдельной сессией — проверить.)
