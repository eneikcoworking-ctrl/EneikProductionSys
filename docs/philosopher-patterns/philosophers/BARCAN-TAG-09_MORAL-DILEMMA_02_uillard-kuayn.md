---
philosopher_id: "BARCAN-TAG-09_MORAL-DILEMMA:02:uillard-kuayn"
name_ru: "Уиллард Куайн"
barcan_tag: "BARCAN-TAG-09_MORAL-DILEMMA"
barcan_role: "Systems Analyst / Technical Product Manager / Technical Lead"
source_file: "BARCAN-TAG-09_MORAL-DILEMMA.md"
source_line: 25
source_principle: "Архитектурный холизм"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Уиллард Куайн

**BARCAN-роль:** `BARCAN-TAG-09_MORAL-DILEMMA` — Прагматический Медиатор (Technical Lead)
**Инженерная роль:** Systems Analyst / Technical Product Manager / Technical Lead
**Исходный принцип:** Архитектурный холизм
**Источник в проекте:** [`BARCAN-TAG-09_MORAL-DILEMMA.md:25`](../../BARCAN-TAG-09_MORAL-DILEMMA.md#L25)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Любая фича меняет всю систему. `toc_constraint_ref` защищает систему от локальных оптимизаций.

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `UILLARD_KUAYN_01` — Архитектурный холизм · Consequence-Backed JTBD | JTBD с доказанным последствием | задача описывает желание, но не эффект | требовать связь work item с измеримым последствием |
| 2 | `UILLARD_KUAYN_02` — Архитектурный холизм · Holistic Impact Map | карта целостного влияния | локальная оптимизация ломает соседний поток | оценивать влияние на всю систему, а не модуль |
| 3 | `UILLARD_KUAYN_03` — Архитектурный холизм · Waste Deletion Ledger | журнал удаления waste | команда автоматизирует ненужное действие | фиксировать, какой waste удалён и почему |
| 4 | `UILLARD_KUAYN_04` — Архитектурный холизм · Desire-to-AC Translator | перевод желания в acceptance criteria | клиентская формулировка остаётся неоднозначной | превращать желание в проверяемые AC |
| 5 | `UILLARD_KUAYN_05` — Архитектурный холизм · Reasoned Refusal Record | запись рационального отказа | отказ выглядит как настроение агента | обосновывать отказ constraint, risk или metric evidence |
| 6 | `UILLARD_KUAYN_06` — Архитектурный холизм · Six Sigma Reality Probe | зонд реальности Six Sigma | улучшение заявлено без метрики вариации | привязывать улучшение к defect rate/variance/capability |
| 7 | `UILLARD_KUAYN_07` — Архитектурный холизм · TOC Constraint Anchor | якорь ограничения TOC | работа улучшает не бутылочное горлышко | связывать задачу с текущим constraint |
| 8 | `UILLARD_KUAYN_08` — Архитектурный холизм · Lean Value Hypothesis | гипотеза lean-ценности | фича создаёт output без value | формулировать value hypothesis до разработки |
| 9 | `UILLARD_KUAYN_09` — Архитектурный холизм · Stakeholder Ambiguity Split | расщепление неоднозначности стейкхолдера | одна фраза скрывает несколько требований | разделять роли, права, стимулы и риски |
| 10 | `UILLARD_KUAYN_10` — Архитектурный холизм · Decision Consequence Matrix | матрица последствий решения | архитектурный выбор не имеет видимой цены | сравнивать последствия accept/reject/defer |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-09_MORAL-DILEMMA`, используй этот файл для индивидуального акцента Уиллард Куайн: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-09_MORAL-DILEMMA.md`](../../BARCAN-TAG-09_MORAL-DILEMMA.md)
- Строка философского принципа: `BARCAN-TAG-09_MORAL-DILEMMA.md:25`
- Внешняя публикационная верификация: `pending`.
