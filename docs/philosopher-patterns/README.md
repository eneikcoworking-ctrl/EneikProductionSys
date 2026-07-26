# Philosopher Patterns RAG Corpus

Корпус создан из `BARCAN-TAG-*.md`: 13 ролей, 78 философов, 10 уникальных персональных паттернов на каждого философа.

## Файлы

- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) — общие паттерны, применимые более чем к пяти философам.
- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) — навигационная таблица по всем 78 философам.
- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) — машинно-читаемый индекс для RAG.
- [`QA_REPORT.md`](QA_REPORT.md) — результат проверки полноты и уникальности.
- [`philosophers/`](philosophers/) — персональные файлы философов.

## Важное ограничение

Первый проход заземлён в ролевых файлах проекта. Для академического использования нужен второй проход: библиографическая верификация публикаций каждого философа и замена `publication_verification: pending` на подтверждённый статус.
