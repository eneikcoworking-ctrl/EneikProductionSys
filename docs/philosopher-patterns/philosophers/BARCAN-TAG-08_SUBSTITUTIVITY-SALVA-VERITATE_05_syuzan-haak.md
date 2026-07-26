---
philosopher_id: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE:05:syuzan-haak"
name_ru: "Сьюзан Хаак"
barcan_tag: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE"
barcan_role: "Data Engineer / DBA"
source_file: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md"
source_line: 33
source_principle: "Принцип фундхерентизма (foundherentism)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Сьюзан Хаак

**BARCAN-роль:** `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` — Логистический Синтаксист
**Инженерная роль:** Data Engineer / DBA
**Исходный принцип:** Принцип фундхерентизма (foundherentism)
**Источник в проекте:** [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:33`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md#L33)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Данные считаются истинными только при сочетании двух условий разом: обоснованного происхождения (Data Lineage как фундамент) И согласованности с остальной моделью данных (coherence) — ни одно из двух по отдельности не достаточно

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `SYUZAN_HAAK_01` — фундхерентизма · Physical Type Wall | физическая стена типов | разные типы смешиваются в одной колонке | разносить типы на уровне DDL, constraints и codecs |
| 2 | `SYUZAN_HAAK_02` — фундхерентизма · Migration Proof Object | миграция как объект доказательства | схема меняется без доказательства сохранения данных | каждая migration имеет forward/backward validation |
| 3 | `SYUZAN_HAAK_03` — фундхерентизма · Index Follows Data Shape | индекс следует форме данных | индексация оптимизирует удобство кода, а не запрос | строить индекс из cardinality/query plan/access pattern |
| 4 | `SYUZAN_HAAK_04` — фундхерентизма · Meaning-Preserving Transform | преобразование с сохранением смысла | ETL меняет значение поля незаметно | фиксировать semantic contract каждой трансформации |
| 5 | `SYUZAN_HAAK_05` — фундхерентизма · Lineage-Coherence Double Check | двойная проверка происхождения и согласованности | истина данных держится только на одном основании | требовать lineage plus reconciliation |
| 6 | `SYUZAN_HAAK_06` — фундхерентизма · Sense-Value Registry Entry | запись смысла и значения в registry | формат поменялся, интерпретация сломалась | вести schema registry с semantic notes |
| 7 | `SYUZAN_HAAK_07` — фундхерентизма · Catalog Admission Gate | ворота попадания в data catalog | новая таблица невидима для governance | запрещать merge без catalog entry |
| 8 | `SYUZAN_HAAK_08` — фундхерентизма · Referential Fence | референциальное ограждение | запись ссылается на невозможный объект | закреплять FK/check constraints или проверяемый surrogate |
| 9 | `SYUZAN_HAAK_09` — фундхерентизма · Partition Predicate Discipline | дисциплина предиката партиции | запросы случайно обходят партиции | фиксировать partition key и обязательный predicate |
| 10 | `SYUZAN_HAAK_10` — фундхерентизма · Reconciliation Checksum | контрольная сумма сверки | перенос данных кажется успешным без сверки | сравнивать counts/hash/sums до и после pipeline |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## Антиконфликтный режим параллельной разработки

Для параллельной разработки этот философский файл не должен использоваться как изолированное правило. Сначала применяется общий charter:

- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

Минимальное правило агента: до изменения кода зафиксировать `touched_paths`, владельца поверхности, затронутый контракт и проверку, которая докажет отсутствие смыслового конфликта после merge.

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, используй этот файл для индивидуального акцента Сьюзан Хаак: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md)
- Строка философского принципа: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:33`
- Внешняя публикационная верификация: `pending`.
