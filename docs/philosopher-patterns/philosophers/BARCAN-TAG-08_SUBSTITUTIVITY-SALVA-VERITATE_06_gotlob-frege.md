---
philosopher_id: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE:06:gotlob-frege"
name_ru: "Готлоб Фреге"
barcan_tag: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE"
barcan_role: "Data Engineer / DBA"
source_file: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md"
source_line: 34
source_principle: "Принцип разграничения смысла и значения"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Готлоб Фреге

**BARCAN-роль:** `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` — Логистический Синтаксист
**Инженерная роль:** Data Engineer / DBA
**Исходный принцип:** Принцип разграничения смысла и значения
**Источник в проекте:** [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:34`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md#L34)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> MDM и Schema Registry: изменение формата хранения (значения) не ломает логику интерпретации данных (смысл) другими сервисами

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `GOTLOB_FREGE_01` — разграничения смысла и значения · Physical Type Wall | физическая стена типов | разные типы смешиваются в одной колонке | разносить типы на уровне DDL, constraints и codecs |
| 2 | `GOTLOB_FREGE_02` — разграничения смысла и значения · Migration Proof Object | миграция как объект доказательства | схема меняется без доказательства сохранения данных | каждая migration имеет forward/backward validation |
| 3 | `GOTLOB_FREGE_03` — разграничения смысла и значения · Index Follows Data Shape | индекс следует форме данных | индексация оптимизирует удобство кода, а не запрос | строить индекс из cardinality/query plan/access pattern |
| 4 | `GOTLOB_FREGE_04` — разграничения смысла и значения · Meaning-Preserving Transform | преобразование с сохранением смысла | ETL меняет значение поля незаметно | фиксировать semantic contract каждой трансформации |
| 5 | `GOTLOB_FREGE_05` — разграничения смысла и значения · Lineage-Coherence Double Check | двойная проверка происхождения и согласованности | истина данных держится только на одном основании | требовать lineage plus reconciliation |
| 6 | `GOTLOB_FREGE_06` — разграничения смысла и значения · Sense-Value Registry Entry | запись смысла и значения в registry | формат поменялся, интерпретация сломалась | вести schema registry с semantic notes |
| 7 | `GOTLOB_FREGE_07` — разграничения смысла и значения · Catalog Admission Gate | ворота попадания в data catalog | новая таблица невидима для governance | запрещать merge без catalog entry |
| 8 | `GOTLOB_FREGE_08` — разграничения смысла и значения · Referential Fence | референциальное ограждение | запись ссылается на невозможный объект | закреплять FK/check constraints или проверяемый surrogate |
| 9 | `GOTLOB_FREGE_09` — разграничения смысла и значения · Partition Predicate Discipline | дисциплина предиката партиции | запросы случайно обходят партиции | фиксировать partition key и обязательный predicate |
| 10 | `GOTLOB_FREGE_10` — разграничения смысла и значения · Reconciliation Checksum | контрольная сумма сверки | перенос данных кажется успешным без сверки | сравнивать counts/hash/sums до и после pipeline |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## Антиконфликтный режим параллельной разработки

Для параллельной разработки этот философский файл не должен использоваться как изолированное правило. Сначала применяется общий charter:

- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

Минимальное правило агента: до изменения кода зафиксировать `touched_paths`, владельца поверхности, затронутый контракт и проверку, которая докажет отсутствие смыслового конфликта после merge.

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, используй этот файл для индивидуального акцента Готлоб Фреге: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md)
- Строка философского принципа: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:34`
- Внешняя публикационная верификация: `pending`.
