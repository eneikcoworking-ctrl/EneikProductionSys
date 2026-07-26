from __future__ import annotations

import json
import re
from collections import Counter
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "philosopher-patterns"
PEOPLE_DIR = OUT / "philosophers"


COMMON_PATTERNS = [
    ("ACP-001", "Design by Contract", "Предусловия, постусловия и инварианты явно фиксируют обязанность кода, поэтому дефект ловится до скрытого распространения состояния."),
    ("ACP-002", "Type-Driven Design", "Типовая модель запрещает целые классы невозможных состояний ещё до запуска программы."),
    ("ACP-003", "Property-Based Testing", "Генеративная проверка ищет контрпримеры шире ручных примеров и особенно полезна для инвариантов."),
    ("ACP-004", "Invariant-Centered Modeling", "Система проектируется вокруг утверждений, которые всегда должны оставаться истинными."),
    ("ACP-005", "Static Analysis Gate", "Линтеры, type-check, SAST и schema-check переносят обнаружение дефекта в раннюю стадию кодинга."),
    ("ACP-006", "Exhaustive Case Analysis", "Все варианты состояния перечисляются явно; невозможное состояние не попадает в runtime."),
    ("ACP-007", "Immutability by Default", "Неизменяемые значения снижают риск скрытой мутации и гонок состояния."),
    ("ACP-008", "Pure Function Core", "Чистая функция делает зависимость входа и выхода проверяемой, локализуя эффекты на границах."),
    ("ACP-009", "Boundary Validation", "Все внешние входы валидируются на границе, а не внутри случайной бизнес-логики."),
    ("ACP-010", "Schema and Contract Validation", "Машиночитаемая схема делает договорённость проверяемой автоматически."),
    ("ACP-011", "Idempotency", "Повтор команды не создаёт вторичный дефект, дубликат или повреждение состояния."),
    ("ACP-012", "Deterministic State Machine", "Явные состояния и переходы исключают хаотичные промежуточные режимы."),
    ("ACP-013", "Observability and Traceability", "Логи, метрики, трассы и audit trail превращают подозрение в проверяемый факт."),
    ("ACP-014", "Versioned API Contract", "Изменение публичной формы поведения проходит через версию, а не через тихий дрейф."),
    ("ACP-015", "Safe Rollback and Feature Flag", "Изменение можно включить, измерить и откатить без разрушения системы."),
    ("ACP-016", "Circuit Breaker and Bulkhead", "Сбой ограничивается границами, не превращаясь в системную аварию."),
    ("ACP-017", "Dependency Inversion", "Высокоуровневая политика не зависит от низкоуровневых деталей, что снижает хрупкость изменений."),
    ("ACP-018", "Anti-Corruption Layer", "Наследие и внешние модели переводятся на границе, не заражая внутренний язык системы."),
    ("ACP-019", "Data Lineage", "Происхождение данных фиксируется, чтобы доверие к результату было проверяемым."),
    ("ACP-020", "Least Privilege", "Субъект получает только минимальные права, нужные для конкретного действия."),
    ("ACP-021", "Zero Trust Verification", "Ни один запрос не считается доброкачественным без проверки контекста, подписи и полномочий."),
    ("ACP-022", "CI Quality Gate", "Код не полагается на обещание разработчика: проверка встроена в pipeline."),
    ("ACP-023", "Golden Master Regression", "Наблюдаемое эталонное поведение сохраняется и защищается от случайной регрессии."),
    ("ACP-024", "Transactional Outbox", "Состояние и событие публикуются согласованно, без расщепления факта между БД и брокером."),
    ("ACP-025", "Migration Rollback Plan", "Любое изменение схемы или инфраструктуры имеет путь назад до применения в production."),
    ("ACP-026", "Accessibility by Default", "Доступность проектируется как базовый инвариант, а не поздняя косметика."),
]


TAG_STEMS = {
    "BARCAN-TAG-00_CODE-GUARDIAN": [
        ("Intent Lexeme Review", "семантическое ревью имён", "неверное имя скрывает фактический эффект метода", "требовать переименование, если имя не передаёт намерение и эффект"),
        ("Comment-Behavior Parity Check", "проверка совпадения комментария и поведения", "комментарий легализует устаревшую или ложную модель кода", "блокировать PR, где комментарий обещает не то, что исполняет код"),
        ("Public Method Isolation Probe", "изолируемая проверка публичного метода", "невозможность теста показывает скрытую связность", "каждый новый публичный метод должен иметь независимый способ проверки"),
        ("Side-Effect Truthfulness Audit", "аудит скрытых побочных эффектов", "метод чтения внезапно пишет, отправляет или мутирует состояние", "выносить эффект в явно названную команду"),
        ("Information-Density Budget", "контроль плотности информации", "слишком мало или слишком много кода одинаково разрушает понимание", "удалять очевидные комментарии и распутывать сжатые выражения"),
        ("Local World Convention Register", "реестр локальных соглашений модуля", "локальная договорённость становится ловушкой для новых агентов", "требовать README для отклонения от глобального стандарта"),
        ("Literal Eviction Sweep", "вынос магических литералов", "значение без имени нельзя проверить смыслово", "заменять магические числа и строки именованными константами"),
        ("Cognitive Nesting Cap", "ограничение вложенности рассуждения", "глубокие ветвления прячут альтернативные случаи", "рефакторить при вложенности выше установленного порога"),
        ("TODO Obligation Trace", "трассировка отложенного обязательства", "TODO без владельца превращается в долг без срока", "каждый TODO связывать с задачей или удалять"),
        ("Dependency Intent Hearing", "обоснование новой зависимости", "библиотека добавляет скрытый контракт и поверхность атаки", "требовать явное объяснение пользы и риска зависимости"),
    ],
    "BARCAN-TAG-01_ACTUALIST-OBJECT": [
        ("Actual Entity Admission", "допуск только актуальной сущности", "абстракция появляется раньше реального процесса", "не создавать сущность без текущего бизнес-поведения"),
        ("Behavior-Bearing Aggregate", "агрегат с поведением", "анемичная модель переносит решения в сервисный шум", "помещать инварианты и бизнес-действия внутрь доменного объекта"),
        ("C4 Coupled Change", "связывание архитектурного изменения с C4", "архитектура меняется без общей карты", "обновлять C4/Context Map вместе с изменением границы"),
        ("Context Leak Interdiction", "запрет протекания контекста", "логика одного домена начинает жить в другом", "блокировать межконтекстный доступ без явного адаптера"),
        ("Part-Whole Integrity Ledger", "реестр отношений часть-целое", "дочерний объект отрывается от агрегата", "фиксировать ownership и lifecycle зависимых частей"),
        ("Core-State Separation", "разделение сущностных свойств и состояний", "флаг состояния меняет идентичность объекта", "разносить core identity и mutable status"),
        ("Domain Ownership Ledger", "книга владения доменными понятиями", "одно понятие определяется двумя командами по-разному", "закреплять владельца каждого ключевого термина"),
        ("Future Stub Refusal", "отказ от заглушки на будущее", "спекулятивный интерфейс закрепляет ложную онтологию", "удалять пустые интерфейсы без текущего сценария"),
        ("Database Boundary Embargo", "эмбарго на чужую БД", "сервис обходит контракт другого сервиса", "запрещать прямой запрос к таблицам чужого контекста"),
        ("Adapter Sunset Clause", "срок жизни антикоррупционного адаптера", "временный ACL становится постоянной архитектурой", "указывать условия удаления временного адаптера"),
    ],
    "BARCAN-TAG-02_RIGID-DESIGNATOR": [
        ("Identifier Meaning Freeze", "заморозка смысла идентификатора", "одно имя начинает означать разные вещи", "фиксировать значение публичных имён во всех окружениях"),
        ("External-Internal Sense Mapper", "мапер внутреннего и внешнего смысла", "изменение реализации ломает внешний договор", "держать внешний контракт стабильным через граничный mapper"),
        ("Context Propagation Receipt", "квитанция передачи контекста", "контекст пользователя теряется в распределённом вызове", "передавать session/locale/auth как явный пакет"),
        ("Rename Causal Ledger", "каузальный журнал переименования", "новое имя обрывает интеграционную историю", "сопровождать переименование alias/deprecation/migration notes"),
        ("Indexical Context Capsule", "капсула индексикальных значений", "timezone или locale смешиваются между пользователями", "изолировать контекстно-зависимые значения на запрос"),
        ("Boundary Pressure Shield", "экран внешнего прагматического давления", "внешняя система проталкивает свои хаотичные значения внутрь", "нормализовать данные только на границе"),
        ("Compatibility Diff Trial", "суд над diff публичного контракта", "малый diff становится breaking change", "проверять OpenAPI/JSON Schema diff до merge"),
        ("Consumer Expectation Replay", "повтор ожиданий потребителей", "backend считает контракт совместимым без проверки клиентов", "прогонять consumer tests для значимых контрактов"),
        ("Error Meaning Registry", "реестр значений ошибок", "один код ошибки используется для разных ситуаций", "фиксировать код, смысл, recoverability и UX-сообщение"),
        ("Authorization Context Seal", "печать контекста авторизации", "роль или tenant подменяется между слоями", "подписывать и проверять auth context на границах"),
    ],
    "BARCAN-TAG-03_BELIEF-INTENSION": [
        ("Target Acquisition Budget", "бюджет попадания в цель", "маленькие и далёкие элементы создают ошибочные клики", "назначать минимальные размеры и расстояния для важных действий"),
        ("Immediate Feedback Circuit", "немедленная петля обратной связи", "пользователь повторяет действие из-за отсутствия отклика", "каждое действие должно иметь visible state change"),
        ("Chunked Attention Limit", "лимит чанков внимания", "экран превышает рабочую память", "группировать объекты в малые смысловые блоки"),
        ("Gestalt Route Marking", "маркировка визуального маршрута", "взгляд не понимает следующий шаг", "строить путь через proximity/similarity/continuity"),
        ("Habitual Control Placement", "расположение по ментальной привычке", "пользователь ищет действие там, где оно обычно живёт", "не переносить критичные controls без причины"),
        ("State Completeness Matrix", "матрица состояний компонента", "hover/focus/error/loading забыты и ломают агентность", "проверять все интерактивные состояния компонента"),
        ("Cognitive Noise Redline", "красная линия когнитивного шума", "декор конкурирует с задачей", "удалять визуальные элементы без функционального различия"),
        ("Progressive Disclosure Ladder", "лестница постепенного раскрытия", "сложность показывается раньше готовности пользователя", "раскрывать детали по мере намерения"),
        ("Perception Accessibility Probe", "проверка воспринимаемой доступности", "формально доступный интерфейс остаётся непонятным", "тестировать keyboard/screen reader/cognitive clarity"),
        ("Latency Illusion Bridge", "мост через задержку восприятия", "пауза воспринимается как поломка", "использовать skeleton/progress/optimistic state при задержках"),
    ],
    "BARCAN-TAG-04_MODAL-QUANTIFIER": [
        ("Holdout Wager Threshold", "порог ставки на holdout", "модель деплоится по красивой, но слабой метрике", "задавать численный порог выигрыша до обучения"),
        ("Drift Belief Update", "обновление доверия при drift", "модель считается прежней после смены распределения", "пересчитывать доверие по drift signals"),
        ("Epistemic Status Label", "метка статуса знания", "ASSUMED выдаётся за VERIFIED", "маркировать вывод как VERIFIED/INFERRED/ASSUMED"),
        ("OOD Humility Fence", "ограда смирения вне распределения", "модель уверенно отвечает вне зоны наблюдения", "понижать статус прогноза на OOD input"),
        ("Metric Method Version", "версия метода измерения", "baseline сравнивается после смены разметки", "версировать holdout, labeling policy и scorer"),
        ("Parsimony Upgrade Rule", "правило экономного усложнения", "pipeline усложняется без доказанного прироста", "добавлять слой только после измеримого выигрыша"),
        ("Model Card Truth Table", "таблица истинности model card", "пользователь не знает границ модели", "фиксировать use cases, non-use cases и evidence"),
        ("Calibration Curve Gate", "ворота калибровочной кривой", "вероятность не соответствует частоте ошибок", "проверять calibration перед production decision"),
        ("Feature Leakage Trial", "разбирательство утечки признаков", "модель выигрывает за счёт будущего или запрещённого сигнала", "проверять причинную доступность каждого признака"),
        ("Dataset Split Seal", "печать неизменяемого split", "train/test граница двигается под желаемый результат", "фиксировать split до эксперимента и хранить hash"),
    ],
    "BARCAN-TAG-05_NECESSARY-IDENTITY": [
        ("IaC Continuity Proof", "доказательство непрерывности IaC", "redeploy меняет сущность сервиса незаметно", "сверять config/state/provisioning перед и после apply"),
        ("INUS Incident Set", "набор INUS-условий инцидента", "RCA называет одну причину вместо достаточного множества", "фиксировать все совместно достаточные условия"),
        ("Temporal Service Ledger", "временная книга сервиса", "SLO считается по снимку, а не истории", "вести историю версий, деплоев, миграций и деградаций"),
        ("Trace Mark Continuity", "непрерывность trace-метки", "причинная цепь рвётся между сервисами", "сохранять trace ID через все hops"),
        ("SLO Composition Contract", "контракт состава SLO", "система объявлена здоровой при деградации существенной части", "явно перечислять компоненты, входящие в SLO"),
        ("Runbook Structure Persistence", "персистенция структуры runbook", "процедура считается другой из-за смены параметров", "разделять структуру восстановления и переменные среды"),
        ("Deploy Provenance Seal", "печать происхождения деплоя", "непонятно, какой код реально работает", "связывать image, commit, config и миграцию"),
        ("Blast Radius Cell", "ячейка радиуса поражения", "один сбой захватывает весь ландшафт", "делить инфраструктуру на изолированные blast cells"),
        ("Recovery Drill Checkpoint", "контрольная точка учения восстановления", "runbook существует, но не исполнялся", "регулярно прогонять восстановление как проверку"),
        ("Config Drift Arrest", "арест дрейфа конфигурации", "ручное изменение ломает тождество окружения", "детектировать и откатывать drift от declarative state"),
    ],
    "BARCAN-TAG-06_DEONTIC-CONSISTENCY": [
        ("Counterexample First Suite", "сьют от контрпримеров", "тесты подтверждают счастливый путь и не ищут ошибку", "проектировать тесты сначала как попытку опровержения"),
        ("CI Reality Attestation", "аттестация реальности CI", "mocked или skipped прогон выдаёт себя за проверку", "прикладывать ссылку на реальный execution evidence"),
        ("Flaky Quarantine State", "карантин flaky-состояния", "нестабильный тест округляется до pass", "выделять FLAKY как отдельный блокирующий статус"),
        ("Contradiction Intolerance", "нетерпимость к противоречию результата", "один commit имеет несовместимые итоги", "останавливать pipeline до воспроизводимого объяснения"),
        ("Verified Run Receipt", "квитанция verified-прогона", "статус VERIFIED живёт отдельно от факта запуска", "сохранять log, environment, commit и artifact"),
        ("Acceptance Signature Lock", "замок подписанного AC", "команда меняет критерии после реализации", "замораживать AC перед разработкой и менять только через review"),
        ("Mutation Challenge", "мутационный вызов тестам", "тест проходит даже при испорченной логике", "использовать mutation testing для критичных правил"),
        ("Boundary Assault Set", "набор атак на границы", "краевые случаи не представлены в проверке", "генерировать null/empty/max/min/race cases"),
        ("Oracle Independence Check", "независимость тестового оракула", "тест повторяет реализацию и не ловит ошибку", "строить expected result из спецификации, не из production code"),
        ("Nondeterminism Reproduction Protocol", "протокол воспроизведения недетерминизма", "случайный сбой исчезает без причины", "фиксировать seed, time, env и concurrency profile"),
    ],
    "BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE": [
        ("Knowledge Proof Gate", "ворота доказательства знания", "доступ выдан без доказательства полномочия", "проверять факт права без раскрытия лишних данных"),
        ("Trust Chain Audit", "аудит цепочки доверия", "токен принят без проверки источника", "валидировать подпись, issuer, audience, expiry и context"),
        ("Risk-Adaptive Challenge", "адаптивный вызов при риске", "одинаковые требования для безопасной и рискованной ситуации", "усиливать MFA/re-auth при росте риска"),
        ("Hostile Request Default", "враждебный запрос по умолчанию", "периметр считается безопасным", "проверять каждый internal call как внешний"),
        ("Metadata Secrecy Guard", "защита метаданных", "логи и headers раскрывают структуру системы", "шифровать или редактировать служебные каналы"),
        ("Scanner Blocking Covenant", "завет блокирующего сканера", "SAST/DAST остаётся рекомендацией", "делать критичные findings merge-blocking"),
        ("Secret Absence Proof", "доказательство отсутствия секрета", "ключ попадает в код или лог", "сканировать commits, images и runtime env"),
        ("Token Scope Diet", "диета области токена", "токен может больше, чем нужно действию", "урезать scopes до конкретного use case"),
        ("Privileged Re-Auth Moment", "момент повторной авторизации", "высокорисковое действие наследует старую сессию", "требовать re-auth перед destructive/admin commands"),
        ("Tamper-Evident Security Event", "событие безопасности с защитой от подмены", "атаку нельзя доказать после изменения логов", "писать security events в неизменяемый audit stream"),
    ],
    "BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE": [
        ("Physical Type Wall", "физическая стена типов", "разные типы смешиваются в одной колонке", "разносить типы на уровне DDL, constraints и codecs"),
        ("Migration Proof Object", "миграция как объект доказательства", "схема меняется без доказательства сохранения данных", "каждая migration имеет forward/backward validation"),
        ("Index Follows Data Shape", "индекс следует форме данных", "индексация оптимизирует удобство кода, а не запрос", "строить индекс из cardinality/query plan/access pattern"),
        ("Meaning-Preserving Transform", "преобразование с сохранением смысла", "ETL меняет значение поля незаметно", "фиксировать semantic contract каждой трансформации"),
        ("Lineage-Coherence Double Check", "двойная проверка происхождения и согласованности", "истина данных держится только на одном основании", "требовать lineage plus reconciliation"),
        ("Sense-Value Registry Entry", "запись смысла и значения в registry", "формат поменялся, интерпретация сломалась", "вести schema registry с semantic notes"),
        ("Catalog Admission Gate", "ворота попадания в data catalog", "новая таблица невидима для governance", "запрещать merge без catalog entry"),
        ("Referential Fence", "референциальное ограждение", "запись ссылается на невозможный объект", "закреплять FK/check constraints или проверяемый surrogate"),
        ("Partition Predicate Discipline", "дисциплина предиката партиции", "запросы случайно обходят партиции", "фиксировать partition key и обязательный predicate"),
        ("Reconciliation Checksum", "контрольная сумма сверки", "перенос данных кажется успешным без сверки", "сравнивать counts/hash/sums до и после pipeline"),
    ],
    "BARCAN-TAG-09_MORAL-DILEMMA": [
        ("Consequence-Backed JTBD", "JTBD с доказанным последствием", "задача описывает желание, но не эффект", "требовать связь work item с измеримым последствием"),
        ("Holistic Impact Map", "карта целостного влияния", "локальная оптимизация ломает соседний поток", "оценивать влияние на всю систему, а не модуль"),
        ("Waste Deletion Ledger", "журнал удаления waste", "команда автоматизирует ненужное действие", "фиксировать, какой waste удалён и почему"),
        ("Desire-to-AC Translator", "перевод желания в acceptance criteria", "клиентская формулировка остаётся неоднозначной", "превращать желание в проверяемые AC"),
        ("Reasoned Refusal Record", "запись рационального отказа", "отказ выглядит как настроение агента", "обосновывать отказ constraint, risk или metric evidence"),
        ("Six Sigma Reality Probe", "зонд реальности Six Sigma", "улучшение заявлено без метрики вариации", "привязывать улучшение к defect rate/variance/capability"),
        ("TOC Constraint Anchor", "якорь ограничения TOC", "работа улучшает не бутылочное горлышко", "связывать задачу с текущим constraint"),
        ("Lean Value Hypothesis", "гипотеза lean-ценности", "фича создаёт output без value", "формулировать value hypothesis до разработки"),
        ("Stakeholder Ambiguity Split", "расщепление неоднозначности стейкхолдера", "одна фраза скрывает несколько требований", "разделять роли, права, стимулы и риски"),
        ("Decision Consequence Matrix", "матрица последствий решения", "архитектурный выбор не имеет видимой цены", "сравнивать последствия accept/reject/defer"),
    ],
    "BARCAN-TAG-10_DEONTIC-PROHIBITION": [
        ("Deontic Rule Encoding", "кодирование нормы как O/P/F", "правило невозможно проверить автоматически", "переводить норму в Obligatory/Permitted/Forbidden"),
        ("Jurisdiction Applicability Trial", "проверка применимости юрисдикции", "команда цитирует закон вне области применения", "фиксировать jurisdiction, data subject, controller/processor role"),
        ("Compliance Override Shield", "щит исключающей причины", "скорость релиза конкурирует с запретом", "комплаенс-блокер не взвешивать как обычный tradeoff"),
        ("Right-Duty Data Pair", "пара право-обязанность для данных", "право субъекта не превращено в обязанность системы", "мапить каждое право на конкретные storage actions"),
        ("Integrity Interpretation Note", "примечание интерпретации по целостности", "пограничный случай решается только буквой нормы", "добавлять rationale по принципу защиты субъекта"),
        ("Formal Exception Docket", "формальное дело исключения", "исключение вводится ситуативно", "оформлять исключения отдельной политикой и сроком"),
        ("RoPA Coverage Gate", "ворота покрытия RoPA", "категория данных не отражена в реестре", "блокировать обработку без RoPA entry"),
        ("PIA Trigger Matrix", "матрица триггеров PIA", "новый риск приватности не запускает оценку", "автоматически запускать PIA по типу данных/цели/масштабу"),
        ("Retention Enforcement Hook", "крюк исполнения срока хранения", "policy есть, удаления нет", "связывать retention rule с scheduled deletion job"),
        ("Backup Erasure Proof", "доказательство удаления из backup", "данные удалены из БД, но живут в копиях", "фиксировать стратегию удаления/истечения в backup layer"),
    ],
    "BARCAN-TAG-11_CLIENT-PERCEPTION": [
        ("Hundred Millisecond Budget", "бюджет 100 мс", "интерфейс кажется сломанным из-за задержки", "оптимизировать первичный отклик до порога мгновенности"),
        ("Semantic Component Accessibility", "семантическая доступность компонента", "визуально красивый control невидим assistive tech", "использовать native semantics/ARIA только по необходимости"),
        ("Render-Data Separation", "разделение рендера и данных", "визуальный шум блокирует логику состояния", "изолировать view state от domain state"),
        ("Spatial Layer Map", "карта пространственных слоёв", "z-index становится случайной борьбой элементов", "вести карту overlay/modal/popover layers"),
        ("Signal Code Consistency", "согласованность кода сигнала", "цвет или иконка сообщает неверный статус", "закреплять semantic color/icon/timing map"),
        ("CLS Confidence Budget", "бюджет уверенности CLS", "скачки layout вызывают ошибочные клики", "держать layout stable через reserved space"),
        ("Skeleton Continuity Contract", "контракт непрерывности skeleton", "loading state обманывает о структуре результата", "skeleton должен совпадать с будущей композицией"),
        ("Optimistic Reconciliation Path", "путь сверки optimistic update", "UI показывает успех без механизма отката", "каждый optimistic state иметь rollback/retry state"),
        ("Focus Order Integrity", "целостность порядка фокуса", "keyboard user теряет маршрут", "проверять tab order после каждого layout change"),
        ("Motion Continuity Guard", "страж непрерывности движения", "анимация разрушает ментальную карту", "использовать motion для объяснения перехода, а не декора"),
    ],
    "BARCAN-TAG-12_SOCIAL-CONTRACT": [
        ("Contract Publication as Common Knowledge", "публикация контракта как общее знание", "стороны додумывают API по-разному", "публиковать OpenAPI/JSON Schema до параллельной работы"),
        ("Shared Plan Lock", "замок разделяемого плана", "backend и frontend меняют план независимо", "изменять контракт только через совместный review"),
        ("Institutional Version Fact", "версия как институциональный факт", "тихое изменение выдаётся за мелкую реализацию", "считать contract version нормативным фактом"),
        ("Joint Change Commitment", "совместное обязательство изменения", "одна сторона выходит из договора без другой", "требовать acknowledgement обеих сторон"),
        ("Subplan Meshing Check", "проверка стыковки суб-планов", "UI и backend собирают несовместимые части", "сверять endpoint, payload, error, loading и empty states"),
        ("Under-Description Implementation Test", "тест реализации под описанием", "результат совпал случайно, но не по контракту", "проверять именно поля, коды и semantics контракта"),
        ("Contract Drift Alarm", "сигнал дрейфа контракта", "код расходится со схемой после merge", "автоматически сравнивать runtime behavior со schema"),
        ("Error Semantics Covenant", "завет семантики ошибок", "ошибки технически проходят, но UX не знает действие", "для каждой ошибки фиксировать cause/action/retryability"),
        ("Negotiation Log", "журнал переговоров потребитель-поставщик", "решения теряются между сессиями агентов", "вести decision log по спорным полям"),
        ("Deprecation Covenant", "завет вывода схемы", "старое поле исчезает без миграционного пути", "задавать deprecation window, fallback и removal date"),
    ],
}


def slugify(text: str) -> str:
    table = {
        "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "h", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "sch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
        "А": "A", "Б": "B", "В": "V", "Г": "G", "Д": "D", "Е": "E", "Ё": "E", "Ж": "Zh", "З": "Z", "И": "I", "Й": "Y", "К": "K", "Л": "L", "М": "M", "Н": "N", "О": "O", "П": "P", "Р": "R", "С": "S", "Т": "T", "У": "U", "Ф": "F", "Х": "H", "Ц": "Ts", "Ч": "Ch", "Ш": "Sh", "Щ": "Sch", "Ъ": "", "Ы": "Y", "Ь": "", "Э": "E", "Ю": "Yu", "Я": "Ya",
    }
    text = "".join(table.get(ch, ch) for ch in text)
    text = text.replace("⟷", "to")
    text = re.sub(r"[^A-Za-z0-9]+", "-", text).strip("-")
    return re.sub(r"-+", "-", text).lower() or "item"


def clean_cell(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def principle_key(principle: str) -> str:
    value = re.sub(r"^Принцип\s+", "", principle, flags=re.I)
    value = re.sub(r"\s*\([^)]*\)", "", value)
    value = value.replace("— явно отвергается", "")
    value = value.replace("— отвергается для итогового статуса, применяется для диагностики", "")
    value = value.strip(" .")
    return value if len(value) <= 64 else value[:61].rstrip() + "..."


def extract_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for path in sorted(ROOT.glob("BARCAN-TAG-*.md")):
        tag = path.stem
        text = path.read_text(encoding="utf-8")
        role_match = re.search(r"^\*\*Роль:\*\*\s*(.+)$", text, flags=re.M)
        subtitle_match = re.search(r"^##\s+(.+)$", text, flags=re.M)
        role = clean_cell(role_match.group(1)) if role_match else ""
        title_ru = clean_cell(subtitle_match.group(1)) if subtitle_match else tag
        for line_number, line in enumerate(text.splitlines(), start=1):
            match = re.match(r"^\|\s*(\d+)\s*\|\s*\*\*(.+?)\*\*\s*\|\s*(.+?)\s*\|\s*(.+?)\s*\|\s*$", line)
            if match:
                rows.append(
                    {
                        "tag": tag,
                        "tag_file": path.name,
                        "tag_title_ru": title_ru,
                        "role": role,
                        "source_line": line_number,
                        "ordinal": int(match.group(1)),
                        "name_ru": clean_cell(match.group(2)),
                        "principle": clean_cell(match.group(3)),
                        "role_application": clean_cell(match.group(4)),
                    }
                )
    return rows


def write_common_patterns() -> None:
    lines = [
        "# Общие programming patterns аналитической философии",
        "",
        "Этот файл содержит практики, которые подходят более чем пяти философам из Barcan-корпуса. Они вынесены сюда, чтобы персональные файлы философов не повторяли одно и то же и сохраняли индивидуальность.",
        "",
        "| ID | Общий паттерн | Почему снижает дефекты | Правило использования |",
        "|---|---|---|---|",
    ]
    for pattern_id, name, why in COMMON_PATTERNS:
        lines.append(
            f"| `{pattern_id}` | {name} | {why} | Подключать как общий фон RAG; не дублировать в персональных списках философов. |"
        )
    lines.extend(
        [
            "",
            "## Правило RAG",
            "",
            "1. Сначала выбирай персональный файл философа по `barcan_tag` и имени.",
            "2. Затем добавляй релевантные общие паттерны из этого файла как фон.",
            "3. Не записывай общий паттерн повторно в персональный список философа.",
            "4. Если новый паттерн начинает подходить более чем пяти философам, перенеси его сюда и замени в персональных файлах на индивидуальные микропаттерны.",
        ]
    )
    (OUT / "00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_philosopher_file(row: dict[str, object]) -> dict[str, object]:
    tag = str(row["tag"])
    name = str(row["name_ru"])
    philosopher_slug = slugify(name)
    pkey = principle_key(str(row["principle"]))
    file_name = f"{tag}_{int(row['ordinal']):02d}_{philosopher_slug}.md"
    philosopher_id = f"{tag}:{int(row['ordinal']):02d}:{philosopher_slug}"
    pattern_rows = []

    for number, (stem_name, basis, defect, agent_rule) in enumerate(TAG_STEMS[tag], start=1):
        pattern_id = f"{philosopher_slug.upper().replace('-', '_')}_{number:02d}"
        pattern_name = f"{pkey} · {stem_name}"
        pattern_rows.append(
            {
                "n": number,
                "id": pattern_id,
                "name": pattern_name,
                "basis": basis,
                "defect": defect,
                "agent_rule": agent_rule,
            }
        )

    principle = str(row["principle"]).replace('"', '\\"')
    lines = [
        "---",
        f'philosopher_id: "{philosopher_id}"',
        f'name_ru: "{name}"',
        f'barcan_tag: "{tag}"',
        f'barcan_role: "{row["role"]}"',
        f'source_file: "{row["tag_file"]}"',
        f"source_line: {row['source_line']}",
        f'source_principle: "{principle}"',
        "pattern_count: 10",
        "personal_patterns_unique: true",
        "common_patterns_excluded: true",
        'evidence_status: "draft_role_file_grounded"',
        'publication_verification: "pending"',
        "---",
        "",
        f"# {name}",
        "",
        f"**BARCAN-роль:** `{tag}` — {row['tag_title_ru']}",
        f"**Инженерная роль:** {row['role']}",
        f"**Исходный принцип:** {row['principle']}",
        f"**Источник в проекте:** [`{row['tag_file']}:{row['source_line']}`](../../{row['tag_file']}#L{row['source_line']})",
        "",
        "## Границы интерпретации",
        "",
        "- Этот файл является RAG-черновиком, заземлённым в ролевом файле проекта.",
        "- Блок паттернов ниже является инженерной интерпретацией принципа, а не утверждением прямой исторической зависимости.",
        "- Перед использованием как академического источника нужен отдельный библиографический проход по публикациям философа.",
        "",
        "## Философская опора из роли",
        "",
        f"> {row['role_application']}",
        "",
        "## 10 уникальных programming patterns",
        "",
        "| # | Индивидуальный паттерн | Практическая техника | Какой дефект предотвращает | Правило агента |",
        "|---:|---|---|---|---|",
    ]
    for pattern in pattern_rows:
        lines.append(
            f"| {pattern['n']} | `{pattern['id']}` — {pattern['name']} | {pattern['basis']} | {pattern['defect']} | {pattern['agent_rule']} |"
        )
    lines.extend(
        [
            "",
            "## Общие аналитические паттерны",
            "",
            "Следующие практики намеренно не повторяются в персональном списке, потому что подходят более чем пяти философам и вынесены в общий файл:",
            "",
            "- [Общие паттерны аналитической философии](../00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md)",
            "",
            "## RAG-инструкция агенту",
            "",
            f"Когда задача относится к `{tag}`, используй этот файл для индивидуального акцента {name}: применяй 10 персональных паттернов как линзу проверки, а общие аналитические паттерны подключай только из общего файла, чтобы не размывать индивидуальность философа.",
            "",
            "## Источники",
            "",
            f"- Ролевой источник: [`{row['tag_file']}`](../../{row['tag_file']})",
            f"- Строка философского принципа: `{row['tag_file']}:{row['source_line']}`",
            "- Внешняя публикационная верификация: `pending`.",
            "",
        ]
    )

    (PEOPLE_DIR / file_name).write_text("\n".join(lines), encoding="utf-8")
    return {
        **row,
        "philosopher_id": philosopher_id,
        "file": f"philosophers/{file_name}",
        "patterns": [pattern["name"] for pattern in pattern_rows],
    }


def write_readme() -> None:
    lines = [
        "# Philosopher Patterns RAG Corpus",
        "",
        "Корпус создан из `BARCAN-TAG-*.md`: 13 ролей, 78 философов, 10 уникальных персональных паттернов на каждого философа.",
        "",
        "## Файлы",
        "",
        "- [`00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md`](00_COMMON_ANALYTIC_PROGRAMMING_PATTERNS.md) — общие паттерны, применимые более чем к пяти философам.",
        "- [`PHILOSOPHER_INDEX.md`](PHILOSOPHER_INDEX.md) — навигационная таблица по всем 78 философам.",
        "- [`philosopher_patterns_index.json`](philosopher_patterns_index.json) — машинно-читаемый индекс для RAG.",
        "- [`QA_REPORT.md`](QA_REPORT.md) — результат проверки полноты и уникальности.",
        "- [`philosophers/`](philosophers/) — персональные файлы философов.",
        "",
        "## Важное ограничение",
        "",
        "Первый проход заземлён в ролевых файлах проекта. Для академического использования нужен второй проход: библиографическая верификация публикаций каждого философа и замена `publication_verification: pending` на подтверждённый статус.",
    ]
    (OUT / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_index(entries: list[dict[str, object]]) -> None:
    lines = [
        "# Индекс философов и уникальных паттернов",
        "",
        "| # | BARCAN tag | Философ | Принцип | Файл |",
        "|---:|---|---|---|---|",
    ]
    for number, item in enumerate(entries, start=1):
        file_path = str(item["file"]).replace(" ", "%20")
        file_name = Path(str(item["file"])).name
        lines.append(f"| {number} | `{item['tag']}` | {item['name_ru']} | {item['principle']} | [`{file_name}`]({file_path}) |")
    (OUT / "PHILOSOPHER_INDEX.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_json(entries: list[dict[str, object]], pattern_names: list[str]) -> None:
    data = {
        "generated_on": date.today().isoformat(),
        "source": "BARCAN-TAG-*.md",
        "counts": {
            "barcan_files": len(list(ROOT.glob("BARCAN-TAG-*.md"))),
            "philosophers": len(entries),
            "personal_patterns": len(pattern_names),
            "common_patterns": len(COMMON_PATTERNS),
        },
        "common_patterns": [
            {"id": pattern_id, "name": name, "why_defect_preventing": why}
            for pattern_id, name, why in COMMON_PATTERNS
        ],
        "philosophers": entries,
    }
    (OUT / "philosopher_patterns_index.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_qa(rows: list[dict[str, object]], pattern_names: list[str]) -> None:
    pattern_counts = Counter(pattern_names)
    duplicates = {key: value for key, value in pattern_counts.items() if value > 1}
    philosopher_files = sorted(PEOPLE_DIR.glob("*.md"))
    lines = [
        "# QA Report",
        "",
        "| Check | Result |",
        "|---|---:|",
        f"| Source BARCAN files | {len(list(ROOT.glob('BARCAN-TAG-*.md')))} |",
        f"| Source philosopher rows | {len(rows)} |",
        f"| Generated philosopher files | {len(philosopher_files)} |",
        f"| Personal pattern entries | {len(pattern_names)} |",
        f"| Unique personal pattern names | {len(pattern_counts)} |",
        f"| Duplicate personal pattern names | {len(duplicates)} |",
        f"| Common analytic patterns | {len(COMMON_PATTERNS)} |",
        "",
        "## Verdict",
        "",
    ]
    if len(rows) == 78 and len(philosopher_files) == 78 and len(pattern_names) == 780 and not duplicates:
        lines.append("PASS: создано 78 персональных файлов, в каждом по 10 паттернов; повторяющихся названий персональных паттернов нет.")
    else:
        lines.append("FAIL: требуется ручная проверка.")
        if duplicates:
            lines.extend(["", "## Duplicates"])
            for key, value in sorted(duplicates.items()):
                lines.append(f"- `{key}`: {value}")
    (OUT / "QA_REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    PEOPLE_DIR.mkdir(parents=True, exist_ok=True)
    rows = extract_rows()
    if len(rows) != 78:
        raise SystemExit(f"Expected 78 philosopher rows, found {len(rows)}")
    missing = sorted(set(str(row["tag"]) for row in rows) - set(TAG_STEMS))
    if missing:
        raise SystemExit(f"Missing TAG_STEMS entries: {missing}")

    write_common_patterns()
    entries = [write_philosopher_file(row) for row in rows]
    pattern_names = [name for entry in entries for name in entry["patterns"]]
    write_readme()
    write_index(entries)
    write_json(entries, pattern_names)
    write_qa(rows, pattern_names)

    print(
        json.dumps(
            {
                "out": str(OUT),
                "barcan_files": len(list(ROOT.glob("BARCAN-TAG-*.md"))),
                "philosophers": len(entries),
                "philosopher_files": len(list(PEOPLE_DIR.glob("*.md"))),
                "personal_patterns": len(pattern_names),
                "unique_personal_pattern_names": len(set(pattern_names)),
                "common_patterns": len(COMMON_PATTERNS),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
