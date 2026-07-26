---
philosopher_id: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE:01:bertran-rassel"
name_ru: "Бертран Рассел"
barcan_tag: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE"
barcan_role: "Data Engineer / DBA"
source_file: "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md"
source_line: 29
source_principle: "Принцип теории типов"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Бертран Рассел

**BARCAN-роль:** `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE` — Логистический Синтаксист
**Инженерная роль:** Data Engineer / DBA
**Исходный принцип:** Принцип теории типов
**Источник в проекте:** [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:29`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md#L29)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Жёсткая типизация на уровне хранения. Данные разных типов физически не могут быть смешаны или неверно интерпретированы СУБД

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `BERTRAN_RASSEL_01` — теории типов · Physical Type Wall | физическая стена типов | разные типы смешиваются в одной колонке | разносить типы на уровне DDL, constraints и codecs |
| 2 | `BERTRAN_RASSEL_02` — теории типов · Migration Proof Object | миграция как объект доказательства | схема меняется без доказательства сохранения данных | каждая migration имеет forward/backward validation |
| 3 | `BERTRAN_RASSEL_03` — теории типов · Index Follows Data Shape | индекс следует форме данных | индексация оптимизирует удобство кода, а не запрос | строить индекс из cardinality/query plan/access pattern |
| 4 | `BERTRAN_RASSEL_04` — теории типов · Meaning-Preserving Transform | преобразование с сохранением смысла | ETL меняет значение поля незаметно | фиксировать semantic contract каждой трансформации |
| 5 | `BERTRAN_RASSEL_05` — теории типов · Lineage-Coherence Double Check | двойная проверка происхождения и согласованности | истина данных держится только на одном основании | требовать lineage plus reconciliation |
| 6 | `BERTRAN_RASSEL_06` — теории типов · Sense-Value Registry Entry | запись смысла и значения в registry | формат поменялся, интерпретация сломалась | вести schema registry с semantic notes |
| 7 | `BERTRAN_RASSEL_07` — теории типов · Catalog Admission Gate | ворота попадания в data catalog | новая таблица невидима для governance | запрещать merge без catalog entry |
| 8 | `BERTRAN_RASSEL_08` — теории типов · Referential Fence | референциальное ограждение | запись ссылается на невозможный объект | закреплять FK/check constraints или проверяемый surrogate |
| 9 | `BERTRAN_RASSEL_09` — теории типов · Partition Predicate Discipline | дисциплина предиката партиции | запросы случайно обходят партиции | фиксировать partition key и обязательный predicate |
| 10 | `BERTRAN_RASSEL_10` — теории типов · Reconciliation Checksum | контрольная сумма сверки | перенос данных кажется успешным без сверки | сравнивать counts/hash/sums до и после pipeline |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE`, используй этот файл для индивидуального акцента Бертран Рассел: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md`](../../BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md)
- Строка философского принципа: `BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md:29`
- Внешняя публикационная верификация: `pending`.
