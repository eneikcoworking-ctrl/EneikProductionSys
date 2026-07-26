---
philosopher_id: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:02:elvin-goldman"
name_ru: "Элвин Голдман"
barcan_tag: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE"
barcan_role: "AppSec / DevSecOps Engineer"
source_file: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md"
source_line: 31
source_principle: "Принцип релайабилизма процессов"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Элвин Голдман

**BARCAN-роль:** `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` — Эпистемический Страж
**Инженерная роль:** AppSec / DevSecOps Engineer
**Исходный принцип:** Принцип релайабилизма процессов
**Источник в проекте:** [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:31`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md#L31)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Верификация надёжности источников запросов. Перед доступом — проверка всей цепочки: подписи, токены, контекст вызова

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `ELVIN_GOLDMAN_01` — релайабилизма процессов · Knowledge Proof Gate | ворота доказательства знания | доступ выдан без доказательства полномочия | проверять факт права без раскрытия лишних данных |
| 2 | `ELVIN_GOLDMAN_02` — релайабилизма процессов · Trust Chain Audit | аудит цепочки доверия | токен принят без проверки источника | валидировать подпись, issuer, audience, expiry и context |
| 3 | `ELVIN_GOLDMAN_03` — релайабилизма процессов · Risk-Adaptive Challenge | адаптивный вызов при риске | одинаковые требования для безопасной и рискованной ситуации | усиливать MFA/re-auth при росте риска |
| 4 | `ELVIN_GOLDMAN_04` — релайабилизма процессов · Hostile Request Default | враждебный запрос по умолчанию | периметр считается безопасным | проверять каждый internal call как внешний |
| 5 | `ELVIN_GOLDMAN_05` — релайабилизма процессов · Metadata Secrecy Guard | защита метаданных | логи и headers раскрывают структуру системы | шифровать или редактировать служебные каналы |
| 6 | `ELVIN_GOLDMAN_06` — релайабилизма процессов · Scanner Blocking Covenant | завет блокирующего сканера | SAST/DAST остаётся рекомендацией | делать критичные findings merge-blocking |
| 7 | `ELVIN_GOLDMAN_07` — релайабилизма процессов · Secret Absence Proof | доказательство отсутствия секрета | ключ попадает в код или лог | сканировать commits, images и runtime env |
| 8 | `ELVIN_GOLDMAN_08` — релайабилизма процессов · Token Scope Diet | диета области токена | токен может больше, чем нужно действию | урезать scopes до конкретного use case |
| 9 | `ELVIN_GOLDMAN_09` — релайабилизма процессов · Privileged Re-Auth Moment | момент повторной авторизации | высокорисковое действие наследует старую сессию | требовать re-auth перед destructive/admin commands |
| 10 | `ELVIN_GOLDMAN_10` — релайабилизма процессов · Tamper-Evident Security Event | событие безопасности с защитой от подмены | атаку нельзя доказать после изменения логов | писать security events в неизменяемый audit stream |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, используй этот файл для индивидуального акцента Элвин Голдман: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md)
- Строка философского принципа: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:31`
- Внешняя публикационная верификация: `pending`.
