# Task Specification Schema (Eneik Management System Standard)

## Обязательная последовательность при компиляции задачи

Шаг 1 — Bottleneck Check (TOC)
Перед классификацией lean_value ОБЯЗАТЕЛЬНО проверить текущий bottleneck-дашборд. Если wishlist-пункт не снимает активный bottleneck и не относится к essential — рассмотреть отложить его явно, а не компилировать немедленно. Процесс опирается на `BottleneckAwarePriorityService` и поле `tasks.priority`.

Шаг 2 — Lean Classification
lean_value = essential | valuable | waste. Waste — Forbidden для компиляции (уже реализовано в TechnicalLeadCompiler).

Шаг 3 — JTBD
Одно предложение "Когда [ситуация], клиент хочет [мотивация], чтобы [результат]" — уже реализовано, здесь фиксируется как шаг процесса.

Шаг 4 — Six Sigma Metric
Измеримая дельта, не абстракция — уже реализовано.

Шаг 5 — Role-Grounded DoD
НОВОЕ ТРЕБОВАНИЕ: DoD не может быть произвольным текстом — каждый пункт DoD ОБЯЗАН явно ссылаться хотя бы на одно конкретное условие из Refusal Criteria роли-исполнителя (например: "должно пройти критерий #2 из BARCAN-TAG-03: has_both_screenshots"). Это гарантирует, что DoD не противоречит и не дублирует стандарт роли произвольно.

Шаг 6 — Design System Reference (для UI/design задач)
Если задача относится к UI-ролям (TAG-03, TAG-11) — DoD ОБЯЗАН ссылаться на конкретные токены из docs/DESIGN_SYSTEM.md (цвет, типографика, отступы), если такой документ существует в репозитории на момент компиляции; если документа ещё нет — явно пометить это поле как "pending: design system not yet defined".

Шаг 7 — Acceptance Criteria (Given/When/Then)
Уже реализовано, здесь фиксируется как финальный шаг перед созданием задачи.

## Definition of Ready (новое понятие, отличное от DoD)
Задача не может перейти в queued, если не пройдены шаги 1-7 выше — это чек-лист ГОТОВНОСТИ задачи к работе, отдельный от Definition of Done (готовности результата).
