---
philosopher_id: "BARCAN-TAG-06_DEONTIC-CONSISTENCY:05:pol-horvich"
name_ru: "Пол Хорвич"
barcan_tag: "BARCAN-TAG-06_DEONTIC-CONSISTENCY"
barcan_role: "QA Automation / Performance Engineer"
source_file: "BARCAN-TAG-06_DEONTIC-CONSISTENCY.md"
source_line: 42
source_principle: "Принцип дефляционизма об истине (минимализм)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Пол Хорвич

**BARCAN-роль:** `BARCAN-TAG-06_DEONTIC-CONSISTENCY` — Эмпирический Верификатор
**Инженерная роль:** QA Automation / Performance Engineer
**Исходный принцип:** Принцип дефляционизма об истине (минимализм)
**Источник в проекте:** [`BARCAN-TAG-06_DEONTIC-CONSISTENCY.md:42`](../../BARCAN-TAG-06_DEONTIC-CONSISTENCY.md#L42)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Пометка теста как VERIFIED не добавляет содержания сверх факта, что тест реально исполнен и результат зафиксирован в CI-логе. VERIFIED не существует отдельно от самого прогона — «верифицировано по опыту» не считается

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `POL_HORVICH_01` — дефляционизма об истине · Counterexample First Suite | сьют от контрпримеров | тесты подтверждают счастливый путь и не ищут ошибку | проектировать тесты сначала как попытку опровержения |
| 2 | `POL_HORVICH_02` — дефляционизма об истине · CI Reality Attestation | аттестация реальности CI | mocked или skipped прогон выдаёт себя за проверку | прикладывать ссылку на реальный execution evidence |
| 3 | `POL_HORVICH_03` — дефляционизма об истине · Flaky Quarantine State | карантин flaky-состояния | нестабильный тест округляется до pass | выделять FLAKY как отдельный блокирующий статус |
| 4 | `POL_HORVICH_04` — дефляционизма об истине · Contradiction Intolerance | нетерпимость к противоречию результата | один commit имеет несовместимые итоги | останавливать pipeline до воспроизводимого объяснения |
| 5 | `POL_HORVICH_05` — дефляционизма об истине · Verified Run Receipt | квитанция verified-прогона | статус VERIFIED живёт отдельно от факта запуска | сохранять log, environment, commit и artifact |
| 6 | `POL_HORVICH_06` — дефляционизма об истине · Acceptance Signature Lock | замок подписанного AC | команда меняет критерии после реализации | замораживать AC перед разработкой и менять только через review |
| 7 | `POL_HORVICH_07` — дефляционизма об истине · Mutation Challenge | мутационный вызов тестам | тест проходит даже при испорченной логике | использовать mutation testing для критичных правил |
| 8 | `POL_HORVICH_08` — дефляционизма об истине · Boundary Assault Set | набор атак на границы | краевые случаи не представлены в проверке | генерировать null/empty/max/min/race cases |
| 9 | `POL_HORVICH_09` — дефляционизма об истине · Oracle Independence Check | независимость тестового оракула | тест повторяет реализацию и не ловит ошибку | строить expected result из спецификации, не из production code |
| 10 | `POL_HORVICH_10` — дефляционизма об истине · Nondeterminism Reproduction Protocol | протокол воспроизведения недетерминизма | случайный сбой исчезает без причины | фиксировать seed, time, env и concurrency profile |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-06_DEONTIC-CONSISTENCY`, используй этот файл для индивидуального акцента Пол Хорвич: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-06_DEONTIC-CONSISTENCY.md`](../../BARCAN-TAG-06_DEONTIC-CONSISTENCY.md)
- Строка философского принципа: `BARCAN-TAG-06_DEONTIC-CONSISTENCY.md:42`
- Внешняя публикационная верификация: `pending`.
