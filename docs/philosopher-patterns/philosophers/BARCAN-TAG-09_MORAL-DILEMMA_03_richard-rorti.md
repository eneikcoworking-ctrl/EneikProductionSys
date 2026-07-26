---
philosopher_id: "BARCAN-TAG-09_MORAL-DILEMMA:03:richard-rorti"
name_ru: "Ричард Рорти"
barcan_tag: "BARCAN-TAG-09_MORAL-DILEMMA"
barcan_role: "Systems Analyst / Technical Product Manager / Technical Lead"
source_file: "BARCAN-TAG-09_MORAL-DILEMMA.md"
source_line: 26
source_principle: "Неопрагматизм"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Ричард Рорти

**BARCAN-роль:** `BARCAN-TAG-09_MORAL-DILEMMA` — Прагматический Медиатор (Technical Lead)
**Инженерная роль:** Systems Analyst / Technical Product Manager / Technical Lead
**Исходный принцип:** Неопрагматизм
**Источник в проекте:** [`BARCAN-TAG-09_MORAL-DILEMMA.md:26`](../../BARCAN-TAG-09_MORAL-DILEMMA.md#L26)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Безжалостное удаление `waste`. Технологии — лишь инструменты для достижения `lean_value`.

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `RICHARD_RORTI_01` — Неопрагматизм · Consequence-Backed JTBD | JTBD с доказанным последствием | задача описывает желание, но не эффект | требовать связь work item с измеримым последствием |
| 2 | `RICHARD_RORTI_02` — Неопрагматизм · Holistic Impact Map | карта целостного влияния | локальная оптимизация ломает соседний поток | оценивать влияние на всю систему, а не модуль |
| 3 | `RICHARD_RORTI_03` — Неопрагматизм · Waste Deletion Ledger | журнал удаления waste | команда автоматизирует ненужное действие | фиксировать, какой waste удалён и почему |
| 4 | `RICHARD_RORTI_04` — Неопрагматизм · Desire-to-AC Translator | перевод желания в acceptance criteria | клиентская формулировка остаётся неоднозначной | превращать желание в проверяемые AC |
| 5 | `RICHARD_RORTI_05` — Неопрагматизм · Reasoned Refusal Record | запись рационального отказа | отказ выглядит как настроение агента | обосновывать отказ constraint, risk или metric evidence |
| 6 | `RICHARD_RORTI_06` — Неопрагматизм · Six Sigma Reality Probe | зонд реальности Six Sigma | улучшение заявлено без метрики вариации | привязывать улучшение к defect rate/variance/capability |
| 7 | `RICHARD_RORTI_07` — Неопрагматизм · TOC Constraint Anchor | якорь ограничения TOC | работа улучшает не бутылочное горлышко | связывать задачу с текущим constraint |
| 8 | `RICHARD_RORTI_08` — Неопрагматизм · Lean Value Hypothesis | гипотеза lean-ценности | фича создаёт output без value | формулировать value hypothesis до разработки |
| 9 | `RICHARD_RORTI_09` — Неопрагматизм · Stakeholder Ambiguity Split | расщепление неоднозначности стейкхолдера | одна фраза скрывает несколько требований | разделять роли, права, стимулы и риски |
| 10 | `RICHARD_RORTI_10` — Неопрагматизм · Decision Consequence Matrix | матрица последствий решения | архитектурный выбор не имеет видимой цены | сравнивать последствия accept/reject/defer |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## Антиконфликтный режим параллельной разработки

Для параллельной разработки этот философский файл не должен использоваться как изолированное правило. Сначала применяется общий charter:

- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

Минимальное правило агента: до изменения кода зафиксировать `touched_paths`, владельца поверхности, затронутый контракт и проверку, которая докажет отсутствие смыслового конфликта после merge.

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-09_MORAL-DILEMMA`, используй этот файл для индивидуального акцента Ричард Рорти: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-09_MORAL-DILEMMA.md`](../../BARCAN-TAG-09_MORAL-DILEMMA.md)
- Строка философского принципа: `BARCAN-TAG-09_MORAL-DILEMMA.md:26`
- Внешняя публикационная верификация: `pending`.
