---
philosopher_id: "BARCAN-TAG-11_CLIENT-PERCEPTION:03:ned-blok"
name_ru: "Нед Блок"
barcan_tag: "BARCAN-TAG-11_CLIENT-PERCEPTION"
barcan_role: "Frontend Engineer"
source_file: "BARCAN-TAG-11_CLIENT-PERCEPTION.md"
source_line: 41
source_principle: "Принцип разделения состояний"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Нед Блок

**BARCAN-роль:** `BARCAN-TAG-11_CLIENT-PERCEPTION` — Феноменальный Верстальщик
**Инженерная роль:** Frontend Engineer
**Исходный принцип:** Принцип разделения состояний
**Источник в проекте:** [`BARCAN-TAG-11_CLIENT-PERCEPTION.md:41`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md#L41)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Изоляция визуального шума от логики данных. Рендеринг не блокирует поток восприятия; анимации появления следуют **Гештальт-принципу непрерывности**

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `NED_BLOK_01` — разделения состояний · Hundred Millisecond Budget | бюджет 100 мс | интерфейс кажется сломанным из-за задержки | оптимизировать первичный отклик до порога мгновенности |
| 2 | `NED_BLOK_02` — разделения состояний · Semantic Component Accessibility | семантическая доступность компонента | визуально красивый control невидим assistive tech | использовать native semantics/ARIA только по необходимости |
| 3 | `NED_BLOK_03` — разделения состояний · Render-Data Separation | разделение рендера и данных | визуальный шум блокирует логику состояния | изолировать view state от domain state |
| 4 | `NED_BLOK_04` — разделения состояний · Spatial Layer Map | карта пространственных слоёв | z-index становится случайной борьбой элементов | вести карту overlay/modal/popover layers |
| 5 | `NED_BLOK_05` — разделения состояний · Signal Code Consistency | согласованность кода сигнала | цвет или иконка сообщает неверный статус | закреплять semantic color/icon/timing map |
| 6 | `NED_BLOK_06` — разделения состояний · CLS Confidence Budget | бюджет уверенности CLS | скачки layout вызывают ошибочные клики | держать layout stable через reserved space |
| 7 | `NED_BLOK_07` — разделения состояний · Skeleton Continuity Contract | контракт непрерывности skeleton | loading state обманывает о структуре результата | skeleton должен совпадать с будущей композицией |
| 8 | `NED_BLOK_08` — разделения состояний · Optimistic Reconciliation Path | путь сверки optimistic update | UI показывает успех без механизма отката | каждый optimistic state иметь rollback/retry state |
| 9 | `NED_BLOK_09` — разделения состояний · Focus Order Integrity | целостность порядка фокуса | keyboard user теряет маршрут | проверять tab order после каждого layout change |
| 10 | `NED_BLOK_10` — разделения состояний · Motion Continuity Guard | страж непрерывности движения | анимация разрушает ментальную карту | использовать motion для объяснения перехода, а не декора |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## Антиконфликтный режим параллельной разработки

Для параллельной разработки этот философский файл не должен использоваться как изолированное правило. Сначала применяется общий charter:

- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

Минимальное правило агента: до изменения кода зафиксировать `touched_paths`, владельца поверхности, затронутый контракт и проверку, которая докажет отсутствие смыслового конфликта после merge.

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-11_CLIENT-PERCEPTION`, используй этот файл для индивидуального акцента Нед Блок: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-11_CLIENT-PERCEPTION.md`](../../BARCAN-TAG-11_CLIENT-PERCEPTION.md)
- Строка философского принципа: `BARCAN-TAG-11_CLIENT-PERCEPTION.md:41`
- Внешняя публикационная верификация: `pending`.
