---
philosopher_id: "BARCAN-TAG-10_DEONTIC-PROHIBITION:02:gerbert-hart"
name_ru: "Герберт Харт"
barcan_tag: "BARCAN-TAG-10_DEONTIC-PROHIBITION"
barcan_role: "Data Governance / Compliance Engineer"
source_file: "BARCAN-TAG-10_DEONTIC-PROHIBITION.md"
source_line: 39
source_principle: "Принцип правила признания (rule of recognition)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Герберт Харт

**BARCAN-роль:** `BARCAN-TAG-10_DEONTIC-PROHIBITION` — Нормативный Контролер
**Инженерная роль:** Data Governance / Compliance Engineer
**Исходный принцип:** Принцип правила признания (rule of recognition)
**Источник в проекте:** [`BARCAN-TAG-10_DEONTIC-PROHIBITION.md:39`](../../BARCAN-TAG-10_DEONTIC-PROHIBITION.md#L39)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Прежде чем требовать соответствия конкретной статье 152-ФЗ/GDPR, агент верифицирует, что эта норма действительно применима к юрисдикции и актуальна — цитата закона без проверки применимости не считается VERIFIED-основанием

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `GERBERT_HART_01` — правила признания · Deontic Rule Encoding | кодирование нормы как O/P/F | правило невозможно проверить автоматически | переводить норму в Obligatory/Permitted/Forbidden |
| 2 | `GERBERT_HART_02` — правила признания · Jurisdiction Applicability Trial | проверка применимости юрисдикции | команда цитирует закон вне области применения | фиксировать jurisdiction, data subject, controller/processor role |
| 3 | `GERBERT_HART_03` — правила признания · Compliance Override Shield | щит исключающей причины | скорость релиза конкурирует с запретом | комплаенс-блокер не взвешивать как обычный tradeoff |
| 4 | `GERBERT_HART_04` — правила признания · Right-Duty Data Pair | пара право-обязанность для данных | право субъекта не превращено в обязанность системы | мапить каждое право на конкретные storage actions |
| 5 | `GERBERT_HART_05` — правила признания · Integrity Interpretation Note | примечание интерпретации по целостности | пограничный случай решается только буквой нормы | добавлять rationale по принципу защиты субъекта |
| 6 | `GERBERT_HART_06` — правила признания · Formal Exception Docket | формальное дело исключения | исключение вводится ситуативно | оформлять исключения отдельной политикой и сроком |
| 7 | `GERBERT_HART_07` — правила признания · RoPA Coverage Gate | ворота покрытия RoPA | категория данных не отражена в реестре | блокировать обработку без RoPA entry |
| 8 | `GERBERT_HART_08` — правила признания · PIA Trigger Matrix | матрица триггеров PIA | новый риск приватности не запускает оценку | автоматически запускать PIA по типу данных/цели/масштабу |
| 9 | `GERBERT_HART_09` — правила признания · Retention Enforcement Hook | крюк исполнения срока хранения | policy есть, удаления нет | связывать retention rule с scheduled deletion job |
| 10 | `GERBERT_HART_10` — правила признания · Backup Erasure Proof | доказательство удаления из backup | данные удалены из БД, но живут в копиях | фиксировать стратегию удаления/истечения в backup layer |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## Антиконфликтный режим параллельной разработки

Для параллельной разработки этот философский файл не должен использоваться как изолированное правило. Сначала применяется общий charter:

- [Parallel Development Conflict Prevention Charter](../01_PARALLEL_DEVELOPMENT_CONFLICT_PREVENTION.md)

Минимальное правило агента: до изменения кода зафиксировать `touched_paths`, владельца поверхности, затронутый контракт и проверку, которая докажет отсутствие смыслового конфликта после merge.

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-10_DEONTIC-PROHIBITION`, используй этот файл для индивидуального акцента Герберт Харт: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-10_DEONTIC-PROHIBITION.md`](../../BARCAN-TAG-10_DEONTIC-PROHIBITION.md)
- Строка философского принципа: `BARCAN-TAG-10_DEONTIC-PROHIBITION.md:39`
- Внешняя публикационная верификация: `pending`.
