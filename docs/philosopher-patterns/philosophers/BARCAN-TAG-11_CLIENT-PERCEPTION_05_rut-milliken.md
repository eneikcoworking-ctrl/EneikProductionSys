---
philosopher_id: "BARCAN-TAG-11_CLIENT-PERCEPTION:05:rut-milliken"
name_ru: "Рут Милликен"
barcan_tag: "BARCAN-TAG-11_CLIENT-PERCEPTION"
barcan_role: "Frontend Engineer"
source_file: "BARCAN-TAG-11_CLIENT-PERCEPTION.md"
source_line: 43
source_principle: "Принцип биосемантики сигналов"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Рут Милликен

**BARCAN-роль:** `BARCAN-TAG-11_CLIENT-PERCEPTION` — Феноменальный Верстальщик
**Инженерная роль:** Frontend Engineer
**Исходный принцип:** Принцип биосемантики сигналов
**Источник в проекте:** [`BARCAN-TAG-11_CLIENT-PERCEPTION.md:43`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md#L43)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Кодирование ошибок и успехов (цвет, иконка, тайминг) для однозначной реакции. Нарушение паттернов кодирования ведет к ложной интерпретации сигналов мозгом

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `RUT_MILLIKEN_01` — биосемантики сигналов · Hundred Millisecond Budget | бюджет 100 мс | интерфейс кажется сломанным из-за задержки | оптимизировать первичный отклик до порога мгновенности |
| 2 | `RUT_MILLIKEN_02` — биосемантики сигналов · Semantic Component Accessibility | семантическая доступность компонента | визуально красивый control невидим assistive tech | использовать native semantics/ARIA только по необходимости |
| 3 | `RUT_MILLIKEN_03` — биосемантики сигналов · Render-Data Separation | разделение рендера и данных | визуальный шум блокирует логику состояния | изолировать view state от domain state |
| 4 | `RUT_MILLIKEN_04` — биосемантики сигналов · Spatial Layer Map | карта пространственных слоёв | z-index становится случайной борьбой элементов | вести карту overlay/modal/popover layers |
| 5 | `RUT_MILLIKEN_05` — биосемантики сигналов · Signal Code Consistency | согласованность кода сигнала | цвет или иконка сообщает неверный статус | закреплять semantic color/icon/timing map |
| 6 | `RUT_MILLIKEN_06` — биосемантики сигналов · CLS Confidence Budget | бюджет уверенности CLS | скачки layout вызывают ошибочные клики | держать layout stable через reserved space |
| 7 | `RUT_MILLIKEN_07` — биосемантики сигналов · Skeleton Continuity Contract | контракт непрерывности skeleton | loading state обманывает о структуре результата | skeleton должен совпадать с будущей композицией |
| 8 | `RUT_MILLIKEN_08` — биосемантики сигналов · Optimistic Reconciliation Path | путь сверки optimistic update | UI показывает успех без механизма отката | каждый optimistic state иметь rollback/retry state |
| 9 | `RUT_MILLIKEN_09` — биосемантики сигналов · Focus Order Integrity | целостность порядка фокуса | keyboard user теряет маршрут | проверять tab order после каждого layout change |
| 10 | `RUT_MILLIKEN_10` — биосемантики сигналов · Motion Continuity Guard | страж непрерывности движения | анимация разрушает ментальную карту | использовать motion для объяснения перехода, а не декора |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-11_CLIENT-PERCEPTION`, используй этот файл для индивидуального акцента Рут Милликен: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-11_CLIENT-PERCEPTION.md`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md)
- Строка философского принципа: `BARCAN-TAG-11_CLIENT-PERCEPTION.md:43`
- Внешняя публикационная верификация: `pending`.
