---
philosopher_id: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE:01:timoti-uilyamson"
name_ru: "Тимоти Уильямсон"
barcan_tag: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE"
barcan_role: "AppSec / DevSecOps Engineer"
source_file: "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md"
source_line: 30
source_principle: "Принцип примата знания (Knowledge First)"
pattern_count: 10
personal_patterns_unique: true
common_patterns_excluded: true
evidence_status: "draft_role_file_grounded"
publication_verification: "pending"
---

# Тимоти Уильямсон

**BARCAN-роль:** `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE` — Эпистемический Страж
**Инженерная роль:** AppSec / DevSecOps Engineer
**Исходный принцип:** Принцип примата знания (Knowledge First)
**Источник в проекте:** [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:30`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md#L30)

## Границы интерпретации

- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.
- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.
- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.

## Философская опора из роли

> Доступы в системе — абсолютный факт владения знанием. Zero-Knowledge Proofs: система проверяет факт знания без раскрытия самих данных

## 10 уникальных programming patterns

| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |
|---:|---|---|---|---|
| 1 | `TIMOTI_UILYAMSON_01` — примата знания · Knowledge Proof Gate | ворота доказательства знания | доступ выдан без доказательства полномочия | проверять факт права без раскрытия лишних данных |
| 2 | `TIMOTI_UILYAMSON_02` — примата знания · Trust Chain Audit | аудит цепочки доверия | токен принят без проверки источника | валидировать подпись, issuer, audience, expiry и context |
| 3 | `TIMOTI_UILYAMSON_03` — примата знания · Risk-Adaptive Challenge | адаптивный вызов при риске | одинаковые требования для безопасной и рискованной ситуации | усиливать MFA/re-auth при росте риска |
| 4 | `TIMOTI_UILYAMSON_04` — примата знания · Hostile Request Default | враждебный запрос по умолчанию | периметр считается безопасным | проверять каждый internal call как внешний |
| 5 | `TIMOTI_UILYAMSON_05` — примата знания · Metadata Secrecy Guard | защита метаданных | логи и headers раскрывают структуру системы | шифровать или редактировать служебные каналы |
| 6 | `TIMOTI_UILYAMSON_06` — примата знания · Scanner Blocking Covenant | завет блокирующего сканера | SAST/DAST остаётся рекомендацией | делать критичные findings merge-blocking |
| 7 | `TIMOTI_UILYAMSON_07` — примата знания · Secret Absence Proof | доказательство отсутствия секрета | ключ попадает в код или лог | сканировать commits, images и runtime env |
| 8 | `TIMOTI_UILYAMSON_08` — примата знания · Token Scope Diet | диета области токена | токен может больше, чем нужно действию | урезать scopes до конкретного use case |
| 9 | `TIMOTI_UILYAMSON_09` — примата знания · Privileged Re-Auth Moment | момент повторной авторизации | высокорисковое действие наследует старую сессию | требовать re-auth перед destructive/admin commands |
| 10 | `TIMOTI_UILYAMSON_10` — примата знания · Tamper-Evident Security Event | событие безопасности с защитой от подмены | атаку нельзя доказать после изменения логов | писать security events в неизменяемый audit stream |

## Общие аналитические паттерны

Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:

- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)

## RAG-инструкция агенту

Когда задача относится к `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, используй этот файл для индивидуального акцента Тимоти Уильямсон: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.

## Источники

- Ролевой источник: [`BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md`](../../BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md)
- Строка философского принципа: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md:30`
- Внешняя публикационная верификация: `pending`.
