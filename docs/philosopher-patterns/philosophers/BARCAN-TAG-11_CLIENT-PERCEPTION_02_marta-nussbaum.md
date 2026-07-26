---
philosopher_id: "BARCAN-TAG-11_CLIENT-PERCEPTION:02:marta-nussbaum"
name_ru: "Марта Нуссбаум"
barcan_tag: "BARCAN-TAG-11_CLIENT-PERCEPTION"
barcan_role: "Frontend Engineer"
source_file: "BARCAN-TAG-11_CLIENT-PERCEPTION.md"
source_line: 40
source_principle: "Принцип инклюзивного интерфейса"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Марта Нуссбаум

**BARCAN-роль:** `BARCAN-TAG-11_CLIENT-PERCEPTION` — Феноменальный Верстальщик
**Инженерная роль:** Frontend Engineer
**Исходный принцип:** Принцип инклюзивного интерфейса
**Источник в проекте:** [`BARCAN-TAG-11_CLIENT-PERCEPTION.md:40`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md#L40)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Семантическая вёрстка и ARIA-атрибуты обеспечивают доступность согласно **WCAG 2.1**. Когнитивная доступность — право каждого пользователя на понимание интерфейса

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `MARTA_NUSSBAUM_01` — инклюзивного интерфейса · Hundred Millisecond Budget | бюджет 100 мс | интерфейс кажется сломанным из-за задержки | оптимизировать первичный отклик до порога мгновенности |
| 2 | `MARTA_NUSSBAUM_02` — инклюзивного интерфейса · Semantic Component Accessibility | семантическая доступность компонента | визуально красивый control невидим assistive tech | использовать native semantics/ARIA только по необходимости |
| 3 | `MARTA_NUSSBAUM_03` — инклюзивного интерфейса · Render-Data Separation | разделение рендера и данных | визуальный шум блокирует логику состояния | изолировать view state от domain state |
| 4 | `MARTA_NUSSBAUM_04` — инклюзивного интерфейса · Spatial Layer Map | карта пространственных слоёв | z-index становится случайной борьбой элементов | вести карту overlay/modal/popover layers |
| 5 | `MARTA_NUSSBAUM_05` — инклюзивного интерфейса · Signal Code Consistency | согласованность кода сигнала | цвет или иконка сообщает неверный статус | закреплять semantic color/icon/timing map |
| 6 | `MARTA_NUSSBAUM_06` — инклюзивного интерфейса · CLS Confidence Budget | бюджет уверенности CLS | скачки layout вызывают ошибочные клики | держать layout stable через reserved space |
| 7 | `MARTA_NUSSBAUM_07` — инклюзивного интерфейса · Skeleton Continuity Contract | контракт непрерывности skeleton | loading state обманывает о структуре результата | skeleton должен совпадать с будущей композицией |
| 8 | `MARTA_NUSSBAUM_08` — инклюзивного интерфейса · Optimistic Reconciliation Path | путь сверки optimistic update | UI показывает успех без механизма отката | каждый optimistic state иметь rollback/retry state |
| 9 | `MARTA_NUSSBAUM_09` — инклюзивного интерфейса · Focus Order Integrity | целостность порядка фокуса | keyboard user теряет маршрут | проверять tab order после каждого layout change |
| 10 | `MARTA_NUSSBAUM_10` — инклюзивного интерфейса · Motion Continuity Guard | страж непрерывности движения | анимация разрушает ментальную карту | использовать motion для объяснения перехода, а не декора |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-11_CLIENT-PERCEPTION`, используй этот файл для индивидуального акцента Марта Нуссбаум: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-11_CLIENT-PERCEPTION.md`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md)
- Строка философского принципа: `BARCAN-TAG-11_CLIENT-PERCEPTION.md:40`
- Внешняя публикационная верификация: `pending`.
