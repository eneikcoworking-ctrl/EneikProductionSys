---
philosopher_id: "BARCAN-TAG-06_DEONTIC-CONSISTENCY:06:piter-stroson"
name_ru: "Питер Стросон"
barcan_tag: "BARCAN-TAG-06_DEONTIC-CONSISTENCY"
barcan_role: "QA Automation / Performance Engineer"
source_file: "BARCAN-TAG-06_DEONTIC-CONSISTENCY.md"
source_line: 43
source_principle: "Принцип истины как перформативного одобрения"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Питер Стросон

**BARCAN-роль:** `BARCAN-TAG-06_DEONTIC-CONSISTENCY` — Эмпирический Верификатор
**Инженерная роль:** QA Automation / Performance Engineer
**Исходный принцип:** Принцип истины как перформативного одобрения
**Источник в проекте:** [`BARCAN-TAG-06_DEONTIC-CONSISTENCY.md:43`](../../BARCAN-TAG-06_DEONTIC-CONSISTENCY.md#L43)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Подписание Acceptance Criteria на этапе Refinement — перформативный акт обязательства команды, а не просто фиксация факта. После подписания AC откат к «мы это не обсуждали» запрещён

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `PITER_STROSON_01` — истины как перформативного одобрения · Counterexample First Suite | сьют от контрпримеров | тесты подтверждают счастливый путь и не ищут ошибку | проектировать тесты сначала как попытку опровержения |
| 2 | `PITER_STROSON_02` — истины как перформативного одобрения · CI Reality Attestation | аттестация реальности CI | mocked или skipped прогон выдаёт себя за проверку | прикладывать ссылку на реальный execution evidence |
| 3 | `PITER_STROSON_03` — истины как перформативного одобрения · Flaky Quarantine State | карантин flaky-состояния | нестабильный тест округляется до pass | выделять FLAKY как отдельный блокирующий статус |
| 4 | `PITER_STROSON_04` — истины как перформативного одобрения · Contradiction Intolerance | нетерпимость к противоречию результата | один commit имеет несовместимые итоги | останавливать pipeline до воспроизводимого объяснения |
| 5 | `PITER_STROSON_05` — истины как перформативного одобрения · Verified Run Receipt | квитанция verified-прогона | статус VERIFIED живёт отдельно от факта запуска | сохранять log, environment, commit и artifact |
| 6 | `PITER_STROSON_06` — истины как перформативного одобрения · Acceptance Signature Lock | замок подписанного AC | команда меняет критерии после реализации | замораживать AC перед разработкой и менять только через review |
| 7 | `PITER_STROSON_07` — истины как перформативного одобрения · Mutation Challenge | мутационный вызов тестам | тест проходит даже при испорченной логике | использовать mutation testing для критичных правил |
| 8 | `PITER_STROSON_08` — истины как перформативного одобрения · Boundary Assault Set | набор атак на границы | краевые случаи не представлены в проверке | генерировать null/empty/max/min/race cases |
| 9 | `PITER_STROSON_09` — истины как перформативного одобрения · Oracle Independence Check | независимость тестового оракула | тест повторяет реализацию и не ловит ошибку | строить expected result из спецификации, не из production code |
| 10 | `PITER_STROSON_10` — истины как перформативного одобрения · Nondeterminism Reproduction Protocol | протокол воспроизведения недетерминизма | случайный сбой исчезает без причины | фиксировать seed, time, env и concurrency profile |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-06_DEONTIC-CONSISTENCY`, используй этот файл для индивидуального акцента Питер Стросон: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-06_DEONTIC-CONSISTENCY.md`](../../BARCAN-TAG-06_DEONTIC-CONSISTENCY.md)
- Строка философского принципа: `BARCAN-TAG-06_DEONTIC-CONSISTENCY.md:43`
- Внешняя публикационная верификация: `pending`.
