---
philosopher_id: "BARCAN-TAG-12_SOCIAL-CONTRACT:04:margaret-gilbert"
name_ru: "Маргарет Гилберт"
barcan_tag: "BARCAN-TAG-12_SOCIAL-CONTRACT"
barcan_role: "API Contract Designer"
source_file: "BARCAN-TAG-12_SOCIAL-CONTRACT.md"
source_line: 41
source_principle: "Совместное обязательство (joint commitment, plural subject)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Маргарет Гилберт

**BARCAN-роль:** `BARCAN-TAG-12_SOCIAL-CONTRACT` — Заключатель Соглашений
**Инженерная роль:** API Contract Designer
**Исходный принцип:** Совместное обязательство (joint commitment, plural subject)
**Источник в проекте:** [`BARCAN-TAG-12_SOCIAL-CONTRACT.md:41`](../../BARCAN-TAG-12_SOCIAL-CONTRACT.md#L41)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Backend и frontend образуют общий "план-субъект" вокруг контракта: ни одна сторона не выходит из обязательства в одиночку — расхождение требует пересмотра контракта обеими сторонами, не тихого дрейфа одной из них

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `MARGARET_GILBERT_01` — Совместное обязательство · Contract Publication as Common Knowledge | публикация контракта как общее знание | стороны додумывают API по-разному | публиковать OpenAPI/JSON Schema до параллельной работы |
| 2 | `MARGARET_GILBERT_02` — Совместное обязательство · Shared Plan Lock | замок разделяемого плана | backend и frontend меняют план независимо | изменять контракт только через совместный review |
| 3 | `MARGARET_GILBERT_03` — Совместное обязательство · Institutional Version Fact | версия как институциональный факт | тихое изменение выдаётся за мелкую реализацию | считать contract version нормативным фактом |
| 4 | `MARGARET_GILBERT_04` — Совместное обязательство · Joint Change Commitment | совместное обязательство изменения | одна сторона выходит из договора без другой | требовать acknowledgement обеих сторон |
| 5 | `MARGARET_GILBERT_05` — Совместное обязательство · Subplan Meshing Check | проверка стыковки суб-планов | UI и backend собирают несовместимые части | сверять endpoint, payload, error, loading и empty states |
| 6 | `MARGARET_GILBERT_06` — Совместное обязательство · Under-Description Implementation Test | тест реализации под описанием | результат совпал случайно, но не по контракту | проверять именно поля, коды и semantics контракта |
| 7 | `MARGARET_GILBERT_07` — Совместное обязательство · Contract Drift Alarm | сигнал дрейфа контракта | код расходится со схемой после merge | автоматически сравнивать runtime behavior со schema |
| 8 | `MARGARET_GILBERT_08` — Совместное обязательство · Error Semantics Covenant | завет семантики ошибок | ошибки технически проходят, но UX не знает действие | для каждой ошибки фиксировать cause/action/retryability |
| 9 | `MARGARET_GILBERT_09` — Совместное обязательство · Negotiation Log | журнал переговоров потребитель-поставщик | решения теряются между сессиями агентов | вести decision log по спорным полям |
| 10 | `MARGARET_GILBERT_10` — Совместное обязательство · Deprecation Covenant | завет вывода схемы | старое поле исчезает без миграционного пути | задавать deprecation window, fallback и removal date |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-12_SOCIAL-CONTRACT`, используй этот файл для индивидуального акцента Маргарет Гилберт: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-12_SOCIAL-CONTRACT.md`](../../BARCAN-TAG-12_SOCIAL-CONTRACT.md)
- Строка философского принципа: `BARCAN-TAG-12_SOCIAL-CONTRACT.md:41`
- Внешняя публикационная верификация: `pending`.
