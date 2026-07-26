---
philosopher_id: "BARCAN-TAG-10_DEONTIC-PROHIBITION:06:frederik-shauer"
name_ru: "Фредерик Шауэр"
barcan_tag: "BARCAN-TAG-10_DEONTIC-PROHIBITION"
barcan_role: "Data Governance / Compliance Engineer"
source_file: "BARCAN-TAG-10_DEONTIC-PROHIBITION.md"
source_line: 43
source_principle: "Принцип формализма правил (rule-following при кажущемся «промахе» правила)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Фредерик Шауэр

**BARCAN-роль:** `BARCAN-TAG-10_DEONTIC-PROHIBITION` — Нормативный Контролер
**Инженерная роль:** Data Governance / Compliance Engineer
**Исходный принцип:** Принцип формализма правил (rule-following при кажущемся «промахе» правила)
**Источник в проекте:** [`BARCAN-TAG-10_DEONTIC-PROHIBITION.md:43`](../../BARCAN-TAG-10_DEONTIC-PROHIBITION.md#L43)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Регуляторное правило применяется даже когда его буквальное соблюдение кажется избыточным для конкретного случая («это же тестовые данные») — исключения создаются формальным пересмотром политики, не ситуативным решением агента

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `FREDERIK_SHAUER_01` — формализма правил · Deontic Rule Encoding | кодирование нормы как O/P/F | правило невозможно проверить автоматически | переводить норму в Obligatory/Permitted/Forbidden |
| 2 | `FREDERIK_SHAUER_02` — формализма правил · Jurisdiction Applicability Trial | проверка применимости юрисдикции | команда цитирует закон вне области применения | фиксировать jurisdiction, data subject, controller/processor role |
| 3 | `FREDERIK_SHAUER_03` — формализма правил · Compliance Override Shield | щит исключающей причины | скорость релиза конкурирует с запретом | комплаенс-блокер не взвешивать как обычный tradeoff |
| 4 | `FREDERIK_SHAUER_04` — формализма правил · Right-Duty Data Pair | пара право-обязанность для данных | право субъекта не превращено в обязанность системы | мапить каждое право на конкретные storage actions |
| 5 | `FREDERIK_SHAUER_05` — формализма правил · Integrity Interpretation Note | примечание интерпретации по целостности | пограничный случай решается только буквой нормы | добавлять rationale по принципу защиты субъекта |
| 6 | `FREDERIK_SHAUER_06` — формализма правил · Formal Exception Docket | формальное дело исключения | исключение вводится ситуативно | оформлять исключения отдельной политикой и сроком |
| 7 | `FREDERIK_SHAUER_07` — формализма правил · RoPA Coverage Gate | ворота покрытия RoPA | категория данных не отражена в реестре | блокировать обработку без RoPA entry |
| 8 | `FREDERIK_SHAUER_08` — формализма правил · PIA Trigger Matrix | матрица триггеров PIA | новый риск приватности не запускает оценку | автоматически запускать PIA по типу данных/цели/масштабу |
| 9 | `FREDERIK_SHAUER_09` — формализма правил · Retention Enforcement Hook | крюк исполнения срока хранения | policy есть, удаления нет | связывать retention rule с scheduled deletion job |
| 10 | `FREDERIK_SHAUER_10` — формализма правил · Backup Erasure Proof | доказательство удаления из backup | данные удалены из БД, но живут в копиях | фиксировать стратегию удаления/истечения в backup layer |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-10_DEONTIC-PROHIBITION`, используй этот файл для индивидуального акцента Фредерик Шауэр: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-10_DEONTIC-PROHIBITION.md`](../../BARCAN-TAG-10_DEONTIC-PROHIBITION.md)
- Строка философского принципа: `BARCAN-TAG-10_DEONTIC-PROHIBITION.md:43`
- Внешняя публикационная верификация: `pending`.
