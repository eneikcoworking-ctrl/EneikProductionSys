# НАРЯД L1 → L2: WO #13–#14 (ОБНОВЛЕНО С ФОРЕНЗИКОЙ И КОНТРАКТАМИ: 2026-11-25 13:05 UTC)
**Основание:** директива Оператора 2026-11-25 11:55 UTC: «Джулсы полностью убрать из цепочки. Вишлисты для замороженных проектов накапливать и при разморозке — группировать и обобщать. Остальное пока не трогать.»
**Полная диагностика L1 и карта ответственных механизмов:** [INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md](INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md)  
**Детальные спецификации, контракты, миграции `V147`/`V148` и заслоны-тесты:** см. **§15 (Такт 1 — WO #13 / Механизм №15)** и **§16 (Такт 2 — WO #14 / Механизм №16)** в конце этого документа.
**Правило (`GEMINI.md §0–§1`):** строго **один механизм за такт** (сначала §15, затем §16); на каждый такт — попперовский тест-заслон в Docker Maven (`-m 2g`), сохранение `100% green` `DennettAutonomyFalsificationTest`, пересборка `eneikproductionsys-backend-1`, живая проверка, семантический коммит и отчёт `<= 20` строк в `docs/reports/PROGRAMMER_STATE.md`. Ручных правок продуктов нет.

- [x] **Такт 1 — WO #13 (Механизм №15, §15 ниже, `D013`/`D006`/`D003`):** полное исключение Google Jules из цепочки исполнения + `V147` — **ПРИНЯТО L1 (`1c408a66`, 2026-11-25 14:32 UTC)**.
- [x] **Такт 2 — WO #14 (Механизм №16, §16 ниже, `D010`/`D012`/`D002`):** накопление вишлистов замороженных проектов (`deferred_frozen`, разрешить `ADD_WISHLIST` для `frozen` без открытия `isOperational`) + `V148` + `FrozenWishlistConsolidationService` при разморозке (`activateProject` / `executeOrchestrate`) с lineage (`merged_into_wishlist_id`, `consolidated_from_ids`), явной детекцией конфликтов и идемпотентностью — **ПРИНЯТО L1 (`06dac76e`, 2026-11-25 15:36 UTC)**.
- **Вне наряда (НЕ ТРОГАТЬ):** порядок `DeepSeek → LOCAL_AGY`, ОС-профили `remotecli3..10`, потерю отклонённых вишлистов `00:50 UTC`, исторические строки `jules_sessions`/`accounts`, 3 существующих `pending`-вишлиста `knowledge-base-crie` вручную, ресурсные настройки.

---

# ИМПЕРАТИВНЫЙ НАРЯД ДЛЯ L2 (ИНЖЕНЕР): ВЕРХОВЕНСТВО КЛИЕНТСКОЙ ЦЕННОСТИ И КОНТУР КОГНИТИВНОЙ ОЧИСТКИ (COGNITIVE FLUSH)
**Дата:** 2026-10-27 07:05 UTC+4 (03:05 UTC)  
**От:** Оператор Дмитрий Ефремов & Архитектурный контур Antigravity / Менеджер-философ (L1)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** ОБЯЗЫВАЮЩАЯ ДИРЕКТИВА АВТОНОМИИ (PRIORITY #1)

---

## 1. ФУНДАМЕНТАЛЬНЫЙ ПРИНЦИП: ЭЛИМИНАЦИЯ КАРТЕЗИАНСКОГО ТЕАТРА И ПРИЗРАКА В МАШИНЕ
Вся фабрика строится строго на материализме и теории сознания Деннетта, Кларка и Хёрли.
Категорически запрещено создавать искусственные надстройки («призрак в машине»), которые пытаются вручную зарегулировать конвейер и замораживают задачи. Система обязана работать как естественный самосогласованный кибернетический контур.

---

## 2. МЕХАНИЗМ №1: БЕЗУСЛОВНЫЙ ПРИОРИТЕТ КЛИЕНТСКОЙ ЦЕННОСТИ (TOC CLIENT VALUE PRIMACY)

### 2.1. Инвариант деонтического превосходства (`D006: CLIENT_VALUE_DOMINANCE`)
* **Суть дефекта прошлого:** Из 110 задач проекта 62% были рождены дизайн-ролью на полировку мокапа (`mockup.html`), который даже не поставлялся клиенту. Пул Jules сжигал ресурсы на внутреннюю бюрократию, а задачи ТЗ клиента мертво стояли в очереди.
* **Требование к L2 в `JulesDispatchService` и `ProjectFlowService`:**
  1. **Приоритет магистрали (Priority Preemption):** Задачи источника `WishlistSource.client` обладают абсолютным квантовым приоритетом. Пока в очереди есть хотя бы одна продуктовая задача ТЗ клиента, 100% свободных слотов пула Jules (из 7 доступных) выделяются исключительно под продуктовый код (`src/main`, `src/components`).
  2. **TOC-квота на ролевые задачи (Kaizen Throttling):** Задачи источника `WishlistSource.role` (дизайн-полировка, мета-ревью) классифицируются как фоновый кайдзен. На них разрешено тратить **не более 1 слота пула**, и только при абсолютно пустой клиентской очереди.
  3. **Запрет самопорождения на ветке `APPROVED`:** В `processDesignVerdict()` при `status == APPROVED` категорически **запретить** создание новых `WishlistEntity` по консернам. Консерны одобренного дизайна фиксируются как информационные заметки, но не превращаются в блокирующие задачи.

---

## 3. МЕХАНИЗМ №2: КОНТУР КОГНИТИВНОЙ ОЧИСТКИ (COGNITIVE FLUSH / ATTENTION HYGIENE)

Оператор Дмитрий Ефремов утвердил внедрение механизма «когнитивной очистки» — сброса всего мусора и шума, который отвлекает агентов и рассеивает их контекстное внимание.

### 3.1. Три уровня когнитивной очистки:

#### Уровень 1: Сброс контекстного шума в промптах агентов (Prompt Tabula Rasa)
* **Проблема:** При повторных попытках решения задачи (retry) агент часто получает накопленную историю прошлых ошибок, логи сбоев и споры с оркестратором. Агент начинает «галлюцинировать вокруг ошибки», а не решать задачу.
* **Решение:** При перезапуске сессии контур когнитивной очистки производит **Prompt Flush**:
  - Стирается весь шлейф предыдущих диалоговых реплик и оправданий.
  - Агенту передается кристально чистый контекст: **Атомарная цель (Atomic Goal) + Критерии приемки (DoD) + Актуальный diff ветки `origin/main`**.
  - Никакого когнитивного мусора из прошлых итераций.

#### Уровень 2: Сброс файлового мусора (Workspace Hygiene Flush)
* **Проблема:** Генерация локальных отчетов (`playwright-report`, `coverage`, `test-results`, `node_modules`, `.next`) засоряет рабочую директорию. Агент делает `git status`, пугается сотен сгенерированных файлов, коммитит их и попадает в бан гигиены репозитория.
* **Решение:** Перед запуском сессии агента и сразу после ее завершения запускается автоматический хук очистки:
  `git clean -fdX --exclude=!node_modules` (или целевое удаление временных отчетов). В рабочей области агента не должно быть ни одного отвлекающего временного артефакта.

#### Уровень 3: Сброс сессионного шума (Zombie & Dormant Review Flush)
* **Проблема:** 24 задачи fallback-ревью висели в очереди на проверку PR, которые уже были смерджены часами ранее.
* **Решение:** Механизм **Cascading Terminal Flush**:
  - Как только продуктовая фича смерджена в `main` и подтверждена системным тестом, все сопутствующие вспомогательные задачи ревью, запросы мнений и драфты вишлистов мгновенно аннулируются (`FLUSHED_AS_SUPERSEDED`).
  - Система не тратит ни секунды внимания агентов на то, что уже потеряло актуальность.



## 4. МЕХАНИЗМ №3: ЭТАП ОЧИСТКИ ДИЗАЙНА ОТ ВИЗУАЛЬНОГО ШУМА (VISUAL NOISE DETOXIFICATION GATE)

Оператор Дмитрий Ефремов ввел категорический запрет на псевдотехнический и декоративный визуальный мусор:

### 4.1. Деонтический гейт BARCAN-TAG-11 (CLIENT-PERCEPTION & SENSORY PURITY):
* **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО (Forbidden):**
  1. **Псевдотехнические надписи на фото/портретах:** Запрет любых характеристик оптики (LEICA, 50MM, f/1.4), ISO, фейковых GPS-координат, псевдостатусов (STATUS: ACTIVE, SYS.01), технических городов и дат на медиа-контенте.
  2. **Искажающие монохромные фильтры:** Запрет на агрессивную постобработку студийных портретов (grayscale, contrast-125). Человек должен подаваться естественно и аутентично.
  3. **Псевдофутуристический декор:** Запрет на декоративные видоискатели, фальшивые угловые скобки (corner brackets), рамки со штрих-пунктиром вокруг живых фотографий.
  4. **Фальшивая пульсация:** Запрет на nimate-pulse, если элемент не отображает реальный физический процесс (например, активный аудиострим).
* **ОБЯЗАТЕЛЬНО (Obligatory):**
  1. Чистая пространственная архитектура (Bauhaus / Mies van der Rohe).
  2. Достоинство студийного портрета: лаконичный контейнер (
rounded-2xl, чистая микро-граница, естественная мягкая глубина).
  3. Типографическая выразительность и функциональный минимализм.

### 4.2. Место в конвейере Stitch Design Workflow:
В docs/STITCH_DESIGN_WORKFLOW.md закрепляется **Шаг 2.5: Visual Noise Detoxification**. Макет Stitch не допускается к декомпозиции на Svelte-компоненты Джулсами, пока автоматический фильтр или ревьюер не вычистит псевдотехнические плашки и фильтры.

---

## 5. МЕХАНИЗМ №4: СОБСТВЕННЫЙ СУВЕРЕННЫЙ ДИЗАЙН-АГЕНТ ФАБРИКИ (SOVEREIGN MULTIMODAL DESIGN SYNTHESIZER)

Оператор Дмитрий Ефремов дал прямое обязывающее указание: **построить собственного дизайн-агента без платного Stitch силами самой Фабрики**. Фабрика обязана быть полностью автономной и генерировать UI/UX мирового уровня (Stripe, Linear, Apple, Vercel, Swiss Bauhaus) для любых продуктов с нулевыми затратами на внешние API.

### 5.1. Архитектурная отвязка от StitchClient в `DesignAssetService.java`:
- При запросе `requireImplementableHtml == true` и отсутствии/отключении `stitch_api_key`, `DesignAssetService` **запрещено** выбрасывать `DesignAssetResult.unavailable(...)`, загоняя конвейер в бесконечный retry.
- Вызов автоматически маршрутизируется во встроенный механизм Фабрики: **`SovereignDesignSynthesisService`**.

### 5.2. Инварианты Суверенного Дизайн-Агента:
1. **Domain-Aware Art Direction (4 архетипа продукта):**
   - **DevTools / SaaS (Linear, Vercel):** Интерактивная консоль/код, архитектурные схемы, плотная матричная сетка, моноширинные акценты. Без портретов людей.
   - **FinTech / Enterprise (Stripe, Mercury):** Высокоточные субпиксельные финансовые поверхности, микро-границы `border-white/[0.07]`, строгие таблицы.
   - **Founder / Editorial (McKinsey, Monocle):** Журнальная асимметрия, мощная типографика `clamp(...)`. Фото основателя/клиента используются **ТОЛЬКО ЕСЛИ** предоставлены в ТЗ (`assets/`). Если фото нет — не выдумывать.
   - **Consumer / Hardware (Apple, Teenage Engineering):** Продукт как главный герой, тактильные микро-контроллеры, минимализм.
2. **Тотальный Anti-Slop фильтр (Deontic Forbidden):**
   - Запрещены фиолетово-синие градиенты (`from-purple-600 to-indigo-600`), размытые цветные тени.
   - Запрещен псевдотехнический мусор: параметры оптики («Leica 50mm»), выдуманные GPS, псевдостатусы `STATUS: ACTIVE`.
   - Запрещены одинаковые 3-карточные Bootstrap-сетки и синтетические AI-лица.
3. **Мультимодальный цикл визуальной доводки (Closed-Loop Vision Feedback):**
   - Синтез самодостаточного HTML5 + Tailwind CDN макета (`mockup.html`).
   - Локальный рендер в Playwright на 1440px и 375px (`has_both_screenshots`).
   - Автоматический Vision-аудит (сетка, контраст, отсутствие переполнений, соответствие токенам `E(f) >= 0.9`).
   - До 3 автоматических итераций доводки при наличии дефектов верстки.
4. **Результат:**
   - Готовый, эстетически безупречный `mockup.html`, сохраняемый в `design/approved/<version>/mockup.html` и готовый к декомпозиции Джулсами.

---

## 6. МЕХАНИЗМ №5: ЛАТЕНТНАЯ СЕМАНТИЧЕСКАЯ ПАМЯТЬ СТЭНФОРДА (STANFORD MEMORILLA LATENT SEMANTIC MEMORY SLOTS, K=16)

По прямому указанию Оператора Дмитрия Ефремова совместно с Менеджером-философом (L1) и Ведущим инженером (L2) в ядро памяти фабрики (`GeminiContextService.java`) введён двухэтапный механизм латентного семантического сжатия контекста **Memorilla** (*Memorilla: Latent Semantic Memory for LLMs*, Stanford University, `arXiv:2610.21375`):

### 6.1. Философское и математическое основание:
- **Гилберт Райл (`D002`, против комбинаторного взрыва и переполнения контекста):** Запрет на инъекцию 5–15 сырых абзацев из одного документа, которые дублируют друг друга и вытесняют ортогональные ограничения из других уставов.
- **Энди Кларк (`Extended Mind`) & Готлоб Фреге (`D009: Salva Veritate`):** Инкрементальная память логов (`indexAppendOnlyDocument`) вычисляет SHA-256 хеш каждого чанка отдельно и доиндексирует **только новые дописанные абзацы**, сохраняя 100% исторических векторов (`OBSERVER_LOG.md`, `docs/FACTORY_MECHANISMS.md`) при нулевых повторных затратах на эмбеддинги.
- **Алонзо Чёрч (`D014: ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE`):** Двухэтапный выбор $K=16$ ортогональных семантических слотов (`retrieveLatentMemorySlots`) по формуле подпространственной проекции:
  $$\text{orthogonalScore}(v_i) = \lambda \cdot \cos(v_i, q) - (1 - \lambda) \cdot \max_{s \in S} \cos(v_i, v_s), \quad \lambda = 0.65$$
  с подавлением коллинеарных клонов ($\cos(v_i, v_s) > 0.96$) и детерминированной дистилляцией каждого слота в капсулу $\le 240$ символов (`distillLatentMemoryToken`) с сохранением `sourceRef`, кодов дефектов `D001..D016` и тегов `BARCAN-TAG-`.
- **Альфред Тарский (трёхслойная стратификация):** Вызов `buildProductWorkerLatentMemoryBlock(role, query)` для суверенных продуктовых воркеров (`LOCAL_AGY` / `LocalAgyWorktreeExecutionEngine`, профили `remotecli3..remotecli10`) жестко ограничен слоем `PRODUCT_WORKER_SOURCE_TYPES` и никогда не допускает утечки `observer_log` или `factory_mechanisms` в продуктовые задачи.

---

## 7. ПОРЯДОК ВНЕДРЕНИЯ И ПРИЕМКИ ДЛЯ ИНЖЕНЕРА (L2)
1. Разработать сервисные методы и заслоны в соответствии с 5 утвержденными механизмами:
   - Механизм №1: Превосходство клиентской ценности (`TOC Client Value Dominance`).
   - Механизм №2: Контур когнитивной очистки (`Cognitive Flush`).
   - Механизм №3: Заслон детоксикации дизайна от визуального шума (`Visual Noise Elimination Gate`).
   - Механизм №4: Суверенный дизайн-агент Фабрики (`SovereignDesignSynthesisService`).
   - Механизм №5: Латентная семантическая память Стэнфорда (`Memorilla Latent Semantic Memory Slots K=16` в `GeminiContextService`).
2. Покрыть фальсификационными тестами (с проверкой невозможности повторения дедлоков `test-twenty-eighth`, сбоев генерации макетов без Stitch и коллинеарного засорения слотов памяти).
3. Зафиксировать замеры в рантайме Docker Maven 3.9.9 (`-m 2g`) без превышения лимитов памяти.
4. Доложить оператору в `docs/reports/ENGINEER_EXPLANATION_AND_PROPOSAL.md`.


---

## 8. МЕХАНИЗМ №6: СУВЕРЕННЫЙ ЦЕХ ДИЗАЙНА БЕЗ ЗАГЛУШЕК И ФИЛОСОФСКИЙ ЗАСЛОН ГОЛДМАНА (D010) ПРОТИВ СИМУЛЯКРОВ

По прямому указанию Оператора Дмитрия Ефремова («доделайте вместе с инженером и философом... будущие проекты фабрики не должны страдать детскими болезнями заглушек, а если заглушки остаются, то философский аудит должен их находить»):

### 8.1. Ликвидация заглушек в Цехе Дизайна:
1. **Генеративный синтез по брифу и Memorilla (K=16):** `DefaultSemanticLayoutSynthesizer.java` синтезирует целевой HTML5/Tailwind DOM на основе смыслового ядра брифа и $K=16$ ортогональных слотов памяти. Захардкоженный шаблон («Explore Dashboard») ликвидирован; генерируются доменные модули (Ledger, Editorial Stream, Telemetry Matrix).
2. **Реальные скриншоты PNG:** `ViewportCapture.java` и `DefaultPlaywrightVisionHarness.java` передают реальные байты снимка экрана из Playwright Chromium и растеризованного `BufferedImage` в `mockup.png` в ветку репозитория, полностью устранив прозрачную 1x1 заглушку.
3. **Честный математический замер контраста WCAG 2.1 AAA:** `DefaultVisionAuditLoop.java` производит динамический расчёт контраста по относительной яркости (`ContrastRule.calculateContrastRatio`) с порогом $\ge 7.0:1$, бутафорская проверка `data-contrast-fail` удалена.
4. **Анти-слоп палитра:** `DefaultDesignTokenCompiler.java` очищен от шаблонов `indigo/violet` во всех 9 архетипах.

### 8.2. Философский заслон Голдмана (D010) и Детектор симулякров Бодрийяра:
- Создан компонент `SimulationStubDetector.java` и тест-заслон `SimulationStubDetectorTest.java` (4/4 green).
- Заслон встроен в `LocalVerificationRunner.java`: любой воркер (`LOCAL_AGY`), чьё решение содержит 1x1 пиксели, фиктивные домены (`assets.eneik.com`), тавтологические заглушки тестов или шаблонную рыбу («Lorem ipsum», «Explore Dashboard»), немедленно блокируется с ошибкой `D010_SIMULACRA_DETECTED`.
- Проект `designer-eneik` (`ef264aea-dd30-46e2-bfac-e7e40918d5ab`) полностью принят в статус `ACCEPTED`.


---

## 9. МЕХАНИЗМ №7: ЛИКВИДАЦИЯ РАЗРЫВА ПОСТАВКИ ФРОНТЕНДА (SVELTE -> STATIC) И CI-ЗАСЛОН (2026-11-10)

По Директиве Оператора Дмитрия Ефремова от 10.11.2026 10:50 UTC (16 слотов Memorilla K=16):

### 9.1. Устранение разрыва сборки Svelte (SLOT-06, D010):
- **Проблема:** Воркеры `LOCAL_AGY` написали компонентный интерфейс в `frontend/src/App.svelte` без заглушек под реальные специальности ЦНИИЭ (31.08.36, 31.08.35, 31.08.19), но в сборочном конвейере `pom.xml` отсутствует этап транспиляции Svelte в `static/index.html`. В рантайме (:8097) отдаётся устаревший макет со статическим «пакетом из 15 документов».
- **Требование:** Внедрить в механизм фабричной сборки и верификации автоматическую компиляцию фронтенда (`frontend-maven-plugin` или сопряжённый билд-шаг в `LocalVerificationRunner`), обеспечивающий целостность поставки по `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` (D010).

### 9.2. Разрешение блокировки BLOCKED_BY_MAIN_CI (SLOT-05, D008):
- **Проблема:** На ветке `main` продукта падают 3 теста телеметрии (`TelemetryMetricsServiceTest`, `InternalServiceTest`), из-за чего `ContinuousOrchestrationService` блокирует взятие новых задач.
- **Требование:** Реализовать контур автоматического таргетированного рекавери ветки main в оркестраторе по паттерну `KARL_POPPER_01_FALSIFICATION_HARNESS` (D008) с зелёным модульным тестом-заслоном.

---

## 10. МЕХАНИЗМ №8: НЕПРЕРЫВНАЯ АВТОНОМНАЯ ЭВОЛЮЦИЯ, ГЕНЕРАТИВНЫЙ СИНТЕЗ И ИНСТИТУЦИОНАЛЬНАЯ ЧИСТОТА (2026-11-11)

По Директиве Оператора Дмитрия Ефремова от 11.11.2026 11:00 UTC:
Ликвидировать состояние пассивного ожидания («standby-симулякр»). Фабрика обязана непрерывно и автономно совершенствовать продукт без участия человека.

### 10.1. Устранение утечки абстракций из UI (D010 Goldman):
- **Дефект:** В интерфейсе `knowledge-base-crie` присутствуют технические маркеры («DeepSeek», счетчик «1713 документов», статический баннер скачивания архива). Это разработческий шум (Leaky Abstraction), снижающий авторитет института.
- **Требование:** Очистить интерфейс от технического жаргона и названий сторонних моделей. Позиционирование строго институциональное: «Интеллектуальный консультант Учебно-методического управления ФБУН ЦНИИ Эпидемиологии Роспотребнадзора». Удалить паразитные счетчики файлов.

### 10.2. Темпоральная непрерывность диалога (D002 Invalid State):
- **Дефект:** Чат сбрасывает историю при перезагрузке страницы.
- **Требование:** Реализовать сохранение контекста сессии пользователя (LocalStorage в браузере + сессионная привязка диалога на бэкенде). Пользователь обязан иметь возможность прокручивать историю и продолжать прерванную сессию.

### 10.3. Генеративный синтез документов по запросу (D003 Contract Drift):
- **Дефект:** Статический пакет из 15 заранее заготовленных файлов («советский архив») вместо генерации под задачу.
- **Требование:** Расширить бэкенд модулем динамической генерации (`/api/generate/docx`, `/api/generate/xlsx`, `/api/generate/pdf`) по ГОСТ Р 7.0.97-2016. Модель в чате обязана уметь по запросу пользователя извлекать реквизиты и формировать готовый целевой документ (выписка, приказ, ведомость нагрузки, протокол).

### 10.4. Целевое пакетирование по намерению (D007 Teleological Action):
- **Дефект:** Кнопка «Скачать архив» на главной скачивает неизменный общий zip.
- **Требование:** Скачивание архива должно быть результатом целевого сценария в диалоге (например, «Сформировать пакет документов для аккредитации ординатуры по эпидемиологии» -> динамическая сборка целевого архива для скачивания).

### 10.5. Инвариант непрерывной автономии (D014 Perpetual Autopoiesis):
- Оркестратор фабрики (`ContinuousOrchestrationService`) и L2 инженер не имеют права останавливаться при пустой очереди. При отсутствии внешних задач контур автономного фальсификатора обязан непрерывно генерировать тесты надежности, искать контрастные дефекты UI и синтезировать эволюционные улучшения.

---

## 11. МЕХАНИЗМ №9 (URGENT WORK ORDER 2026-11-24): КАНОНИЗАЦИЯ КОНТРАКТА `SOURCE_ROLE_TAG` (D003/D008) И ЛИКВИДАЦИЯ УТЕЧЕК `HikariPool-1` / НАТИВНЫХ ПОТОКОВ (D005/D013)

**Статус:** СРОЧНЫЙ ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (`STANDBY СНЯТ`)  
**Основание:** Срочная директива Оператора от `2026-11-24 15:17:15 UTC` (`OPERATOR_DIRECTIVES.md`) и форензика L1 (`docs/reports/INCIDENT_DIAGNOSIS_2026-11-24_D003_D008_HIKARI.md`).  
**Квантование (GEMINI.md §1):** Выполнять последовательно по двум чётким квантовым шагам (Шаг A → коммит + тест, Шаг B → коммит + тест), затем пересобрать контейнер `eneikproductionsys-backend-1` и подтвердить Ступени 3–4 в живом рантайме.

### 11.1. Шаг A — Устранение `SQL Error: 22001` и закрепление канона `SOURCE_ROLE_TAG` (D003 / D008 / D002)
**Диагностированный дефект:**
- `ContinuousOrchestrationService.java:1098` передаёт `"KARL_POPPER_01_FALSIFICATION_HARNESS"` (36 символов — имя паттерна, а не роли) в `wishlist.SOURCE_ROLE_TAG VARCHAR(16)`, срывая D008 Main CI Recovery на каждом 60-секундном цикле (`d64a3b6b-667e-4cda-aa69-8a7bf42b890f`).
- `FalsificationCycleService.java:1061` передаёт comma-separated мульти-ролевые теги (`"BARCAN-TAG-01, BARCAN-TAG-06"`), которые также переполняют `VARCHAR(16)`, хотя `TechnicalLeadCompiler.doctrinePressure` явно рассчитан на список `BARCAN-TAG-XX`.

**Обязательные изменения L2:**
1. **Каноническая роль в D008 Main CI Recovery (`ContinuousOrchestrationService.java:1098`):**
   - Заменить `"KARL_POPPER_01_FALSIFICATION_HARNESS"` на канонический тег роли **`"BARCAN-TAG-06"`** (`QA Automation` / `BARCAN-TAG-06_DEONTIC-CONSISTENCY`).
   - Имя паттерна `"KARL_POPPER_01_FALSIFICATION_HARNESS"` сохранить в `content` и `dod`, а `"MAIN_CI_RECOVERY"` — в `tocConstraintRef`.
2. **Миграция схемы БД (`src/main/resources/db/migration/V146__widen_wishlist_source_role_tag.sql`):**
   - `ALTER TABLE wishlist ALTER COLUMN source_role_tag VARCHAR(128);`
   - В `WishlistEntity.java:21` обновить аннотацию: `@Column(name = "source_role_tag", length = 128)`.
   - Колонку `wishlist_items.source_role_tag VARCHAR(16)` с FK на `roles(tag)` НЕ ослаблять.
3. **Единый валидатор контракта `SOURCE_ROLE_TAG` (Charter Invariant 10 / `RIGID_API_REFERENT` D003):**
   - Ввести централизованную проверку/нормализацию (например, статический метод `WishlistEntity.validateCanonicalSourceRoleTag(String)` или `@PrePersist`/`@PreUpdate` + вызов в `ProjectFlowService`/конструкторе):
     - Допустимо `null` (для `source == client`), либо один или несколько comma-separated канонических тегов формата `BARCAN-TAG-(0[0-9]|1[0-2])` общей длиной `<= 128`.
     - Любая попытка записать имя паттерна (`KARL_POPPER_...`) или строку длиннее 128 символов немедленно отвергается `IllegalArgumentException` до SQL-слоя.
4. **Тесты-заслоны (Поппер D008, Docker Maven `-m 2g`):**
   - Интеграционный тест на реальной H2 с Flyway-миграциями (`V1..V146`): воспроизвести полный вызов `ContinuousOrchestrationService.recoverFailingMainCi(...)` и подтвердить успешную вставку `MAIN_CI_RECOVERY` wishlist с `source_role_tag = "BARCAN-TAG-06"` (0 ошибок `SQL 22001`).
   - Тест на мульти-ролевой `source_role_tag` (`"BARCAN-TAG-01, BARCAN-TAG-06, BARCAN-TAG-08"` — успешно сохраняется и корректно оценивается `TechnicalLeadCompiler.doctrinePressure`).
   - Фальсификационный тест: попытка записать `"KARL_POPPER_01_FALSIFICATION_HARNESS"` или >128 символов в `source_role_tag` детерминированно падает на входном контракте.
   - Подтвердить зелёный прогон `FlywayMigrationValidationTest` и `ContinuousOrchestrationServiceTest`.

### 11.2. Шаг B — Устранение коренных причин утечек `HikariPool-1` и OOM нативных потоков (D005 / D010 / D013)
**Диагностированные дефекты (из `data/logs/backend.log.2026-11-20..24*`):**
1. **OOM нативных потоков → смерть `HikariPool.addBagItem` (`122x` `2026-11-20T15:54:00Z`):**
   - `ProjectFlowService.java:2288` создаёт `HttpClient.newBuilder()...build()` внутри метода на каждый вызов (утечка `SelectorManager` и worker-потоков).
   - `DefaultPlaywrightVisionHarness.java:64` использует неограниченный `Executors.newCachedThreadPool(...)`.
   - `LocalAgyCliClient.java:270–271` спавнит сырые `new Thread(...)` на каждый вызов CLI.
2. **Хронические длинные транзакции (>30 с `Connection leak detection`):**
   - `ClientDeliverableReadinessService.deleteValuelessEpics()` (`20x` утечек — одна `@Transactional` на цикл по всем проектам).
   - `TrustSnapshotService.captureAndBackfillSnapshots()` (`9x` утечек — одна `@Transactional` на `operationalTruthService.build` + `backfillResolvedOutcomes`).
   - `ProcessControlService.recomputeForProject(...)` (`6x` утечек — N+1 `findByFeatureId` в цикле, строки 164–167).
   - `AutoMergeService.executeMerge(...)` (`@Transactional`, строка 1056) — держит JDBC-соединение открытым на время синхронных GitHub API запросов (`fetchPullRequestByNumber`, `mergeableState`, `pullRequestChecks`, `PUT /merge`, `handleMergeConflict`), провоцируя `STALL_BOTTLENECK` на `AUTOMERGE_PROCESSING` (`818x`).
   - `spring.jpa.open-in-view` не выключен явно (`application.properties`), удерживая соединения на длительность HTTP-запросов.

**Обязательные изменения L2:**
1. **Запрет сетевого I/O внутри `@Transactional` в `AutoMergeService.executeMerge`:**
   - Снять `@Transactional` с `executeMerge` (которая вызывает GitHub API); выделить короткие БД-транзакции (через `TransactionTemplate` или проксируемые короткие методы) строго на чтение состояния до HTTP-вызова и фиксацию результата после HTTP-вызова.
2. **Декомпозиция длинных батч-транзакций (исключить `Connection leak detection` >30 с):**
   - `ClientDeliverableReadinessService.deleteValuelessEpics()`: снять `@Transactional` с внешнего `@Scheduled`-метода; выполнять чтение/диагностику и запись короткими транзакциями per-project / per-unit (без удержания одного соединения на весь цикл).
   - `TrustSnapshotService.captureAndBackfillSnapshots()`: снять `@Transactional` с внешнего `@Scheduled`-метода; вычислять `operationalTruthService.build(projectId)` вне долгой write-транзакции и сохранять снапшот / переходы инвариантов короткой транзакцией.
   - `ProcessControlService.acquireEvidencePacket(...)` (строки 164–167): устранить N+1 (`findByFeatureId` в цикле → батч-выборка `findByFeatureIdIn(allFeatureIds)`) и ограничить транзакцию строго сохранением снапшотов.
   - `src/main/resources/application.properties`: явно установить `spring.jpa.open-in-view=false` (проверив отсутствие `LazyInitializationException` в контроллерах/тестах).
3. **Устранение утечек нативных потоков (D005 Buchak Tail Risk Block):**
   - `ProjectFlowService.java:2288`: заменить per-call `HttpClient.newBuilder()...build()` на единый `private final HttpClient sharedHttpClient` (поле бина, как в `AutoMergeService:49`).
   - `DefaultPlaywrightVisionHarness.java:64`: заменить `Executors.newCachedThreadPool` на ограниченный пул (например, `Executors.newFixedThreadPool(4, ...)` с daemon-потоками и `@PreDestroy` shutdown).
   - `LocalAgyCliClient.java:270–271`: перевести чтение stdout/stderr на ограниченный daemon `ExecutorService` вместо неограниченного `new Thread(...)`.
4. **Тесты-заслоны (Поппер D008 + Голдман D010 + Бучак D005, Docker Maven `-m 2g`):**
   - Модульные/интеграционные тесты, подтверждающие:
     - `AutoMergeService.executeMerge` НЕ держит открытую DB-транзакцию во время вызова `GitHubPullRequestService` / `sendGitHub` (`TransactionSynchronizationManager.isActualTransactionActive() == false` в момент сетевого вызова).
     - `ClientDeliverableReadinessService.deleteValuelessEpics` и `TrustSnapshotService.captureAndBackfillSnapshots` не открывают монолитную внешнюю транзакцию на весь `@Scheduled`-проход.
     - `ProcessControlService` использует батч-выборку вместо N+1 `findByFeatureId`.
     - Зелёный прогон затронутых сьютов (`AutoMergeServiceTest`, `ClientDeliverableReadinessServiceTest`, `ProcessControlServiceTest`, `ProjectFlowServiceTest`, `ContinuousOrchestrationServiceTest`, `FlywayMigrationValidationTest`).

### 11.3. Доведение до 100% в живом рантайме (Ступени 3–4) и приёмка L1
1. Пересобрать и перезапустить контейнер `eneikproductionsys-backend-1` (`docker compose up -d --build backend`) с чистым graceful shutdown H2 (`stop_grace_period: 90s`).
2. Проверить в живых логах `eneikproductionsys-backend-1` минимум за 3 последовательных цикла оркестрации + 1 часовой тик `:20` / `:42`:
   - `0` ошибок `SQL Error: 22001` / `SOURCE_ROLE_TAG` (D008 Main CI Recovery создаёт/подхватывает `MAIN_CI_RECOVERY` wishlist без падения).
   - `0` записей `Connection leak detection triggered` и `0` `HikariPool-1 - Connection is not available`.
   - `0` ошибок `unable to create native thread`; стабильный `Threads` и `VmRSS` в `/proc/1/status`.
3. Очистить/проверить ротацию зависших `data/logs/backend.log.*.tmp`, вернув диск `/` ниже `< 70%`.
4. Зафиксировать доказательные замеры (SHA коммитов, результаты Docker Maven, живую телеметрию контейнера) в `docs/reports/PROGRAMMER_STATE.md`.

---

## 12. МЕХАНИЗМЫ №10–12 (P0 WORK ORDER #10 ОТ 2026-11-24 — ПЕРЕХОД НА DEEPSEEK: ПАРСЕР JSON, АНТИ-ЦИКЛ CPU/BACKOFF, JUDGMENT-PROXY)

**Статус:** P0 ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (`STANDBY СНЯТ`)  
**Основание:** Срочная P0-директива Оператора от `2026-11-24 19:46:00 UTC` (`OPERATOR_DIRECTIVES.md`) и форензика L1 (`docs/reports/INCIDENT_DIAGNOSIS_2026-11-24_DEEPSEEK_P0.md`).  
**Жёсткий регламент квантования (GEMINI.md §0–§1):**
- Выполнять **строго последовательно: сначала п.1, затем п.2, затем п.3** — ровно **один механизм за такт**.
- На каждый шаг обязателен воспроизводимый **попперовский тест-заслон** (`mvn test -Dtest=...` в Docker Maven `-m 2g` для Java и `node --test` для `judgment-proxy`), семантический коммит в фабрику и отчёт в `docs/reports/PROGRAMMER_STATE.md`.
- **Категорический запрет:** никаких ручных правок репозитория продукта (`knowledge-base-crie` и др.).

### 12.1. Такт 1 — P0 #1 (`D003 Contract Drift` / `D008 Popper`): Санитайзер `CTRL-CHAR` в `DeepSeekPatchParser` (Механизм №10)
**Диагностированный дефект:**
- `DeepSeekPatchParser.parse` (`DeepSeekPatchParser.java:55,117`, вызывается из `LocalAgyOutputParser.java:42` и `DeepSeekApiClient.java:185`) падает с `JsonParseException: Illegal unquoted character ((CTRL-CHAR, code 10))` (`10x` за 2ч / `5x` за 3ч в текущем контейнере), когда модель выводит сырой перенос строки / табуляцию внутри строковых литералов JSON (`"content"`, `"search"`, `"replace"`, `"rationale"`).

**Обязательные изменения L2:**
1. В `src/main/java/com/eneik/production/services/worker/deepseek/DeepSeekPatchParser.java`:
   - Добавить детерминированный state-machine метод `sanitizeJsonControlChars(String rawJson)` (вызывается строго после `extractJson(jsonContent)` и до `objectMapper.readTree(...)`), отслеживающий состояния `inString` и `escaped`:
     - Внутри JSON-строковых литералов (`inString == true`): экранировать сырые `\n` (`0x0A`) → `\\n`, `\r` (`0x0D`) → `\\r`, `\t` (`0x09`) → `\\t`, `\b` (`0x08`) → `\\b`, `\f` (`0x0C`) → `\\f`, прочие `< 0x20` → `\u00XX`.
     - Вне строковых литералов (`!inString`): символы форматирования не изменять.
     - Сохранять нетронутыми уже валидные escape-последовательности (`\"`, `\\`, `\n`, `\t`, `\uXXXX`) — обеспечить строгую идемпотентность на валидном JSON.
   - Убедиться, что `LocalAgyOutputParser.parse` проходит через этот же единый санитайзер без дублирования логики.
   - Сохранить 100% действие `checkAntiTruncation` (`LazyTruncationDetectedException`, D008) после санитаризации.
2. **Тест-заслон (Docker Maven `-m 2g`):**
   - Создать `src/test/java/com/eneik/production/services/worker/deepseek/DeepSeekPatchParserTest.java` (и расширить `LocalAgyOutputParserTest.java`):
     - Воспроизвести реальный падающий ответ с неэкранированным `LF (code 10)`, `CR (code 13)`, `TAB (code 9)` внутри `"content"` и `"patch_blocks"` — убедиться, что парсер успешно извлекает многострочный код без `JsonParseException`.
     - Проверить идемпотентность на валидном JSON с `\\n`, `\"`, `\\t` (Java-код и regex внутри строк не искажаются).
     - Проверить, что `LazyTruncationDetectedException` (D008) по-прежнему срабатывает на стоп-маркер внутри многострочного санитайзированного кода.
3. Зафиксировать коммит (`fix(d003): sanitize unescaped control chars in DeepSeekPatchParser`) и обновить `docs/reports/PROGRAMMER_STATE.md`.

### 12.2. Такт 2 — P0 #2 (`D013 Runtime Drift` / `D005 Buchak` / `D008 Popper`): Ликвидация горячего цикла `130% CPU`, backoff и лимит ретраев (Механизм №11)
**Диагностированный дефект:**
- Контейнер `eneikproductionsys-backend-1` жжёт **`113–144% CPU`** (`130.3%` на замере `21:26 UTC`) при **`858–929MiB / 1GiB`** RAM (GC-трашинг: `G1 Conc#0`, `GC Thread#0/1` на вершине CPU) и накопил **`185` зомби-процессов `git`**.
- **Корень 1 (`ClaimService.java:748-756`):** `refusedSessionCreations(taskId)` считает только `jules_sessions` с `externalSessionId IS NULL`, тогда как `JulesDispatchService:717` и `LocalAgyWorktreeExecutionEngine:117` заранее задают `externalSessionId = "local-agy-..."` (и `deepseek-...`). В результате счётчик застревает на **`attempt 0/34`**, бюджет никогда не исчерпывается, а 4 задачи `knowledge-base-crie` (`a6f64e24`, `4c4837ff`, `d976d831`, `c6f75610`) каждую минуту без backoff заново создают `git worktree` и падают по квоте (`exit 3`).
- **Корень 2 (`ContinuousOrchestrationService.java:201` + `ClientDeliverableReadinessService`):** `@Scheduled(fixedRateString = "60000")` при длительности тика `70–97с` (`ORCHESTRATION_PROCESSING STALL_BOTTLENECK`, 99+ `failed=1` требований `knowledge-base-crie`, до `265` round trips `mergedReviews`) запускает следующий тик без паузы.

**Обязательные изменения L2:**
1. **`ClaimService.java` (строго монотонная вариант-функция + backoff):**
   - Исправить `refusedSessionCreations(taskId)` (и сопутствующую запись/подсчёт отказов при `releaseClaimToQueue`): учитывать неуспешные суверенные сессии (`local-agy-*`, `deepseek-*` со статусом `failed` без открытого PR), чтобы `attempt` рос на каждой неудачной попытке и упирался в лимит ретраев (`retireForExhaustedDispatchBudget`).
   - Ввести экспоненциальный backoff/cooldown на возвращаемую в очередь задачу (`2m -> 5m -> 15m -> 30m`, cap `60m`), не позволяя `ContinuousOrchestrationService`/`ProjectFlowService`/`ClaimService` брать её в работу до истечения окна backoff.
   - При отказе по квоте (`Individual quota reached` / HTTP 429) выставлять cooldown на аккаунт/провайдер через `AccountHealthService`, прекращая минутный штурм квотированного профиля.
2. **`ContinuousOrchestrationService.java` и горячие пересчёты (`knowledge-base-crie`, 99+ требований):**
   - Перевести `@Scheduled(fixedRateString = "${orchestration.rate-ms:60000}")` на `fixedDelayString` (гарантированная пауза между тиками).
   - Ограничить повторные ретраи/пересчёты неизменившихся `failed=1` требований проекта через cooldown/лимит ретраев, устранив постоянную нагрузку >100% CPU и GC-трашинг.
3. **Устранение зомби `git` (`185 Zs`):**
   - Проверить `GitWorktreeExecutionManager` на гарантированный `waitFor()`/`destroyForcibly()` дочерних процессов в `finally`.
   - Добавить `init: true` сервису `backend` в `docker-compose.yml` (активация Docker `tini` reaper).
4. **Тесты-заслоны (Docker Maven `-m 2g`):**
   - Фальсификационный тест в `ClaimServiceTest`: суверенный отказ (`local-agy-*` / `deepseek-*` с непустым `externalSessionId`) **увеличивает** счётчик отказов (не остаётся `0/N`), применяет exponential backoff (повторный claim внутри окна блокируется) и при достижении лимита выводит задачу из горячего цикла.
   - Тест `ContinuousOrchestrationServiceTest`: проверка соблюдения backoff/лимита ретраев и отсутствия бесконечной немедленной передиспетчеризации.
   - Пересобрать `eneikproductionsys-backend-1` и подтвердить в живом рантайме: падение CPU ниже `< 50%` в спокойном интервале, `0` минутных повторов `attempt 0/34`, `0` зомби `git`.
5. Зафиксировать коммит (`fix(d013,d005): bound sovereign retry loop with monotonic budget, backoff, and fixedDelay orchestration`) и обновить `docs/reports/PROGRAMMER_STATE.md`.

### 12.3. Такт 3 — P0 #3 (`D003` / `D010 Goldman` / `D008 Popper`): Переключение `judgment-proxy :8093` на DeepSeek primary с явным fallback (Механизм №12)
**Диагностированный дефект:**
- `http://127.0.0.1:8093/health` возвращает `{"ok":true,"primary":"gemini","shadowDir":"/shadow","geminiAvailable":false}`, из-за чего 100% запросов `/judge` молча обслуживаются хардкодной заглушкой `evaluateAutonomousFallback` (`ABSTAIN` / `SATISFIED` — симулякр суждения D010).

**Обязательные изменения L2:**
1. **`judgment-proxy/server.js`:**
   - Добавить поддержку DeepSeek как первичного провайдера (`JUDGMENT_PRIMARY` default `'deepseek'`, `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL` default `'https://api.deepseek.com'`, `DEEPSEEK_MODEL` default `'deepseek-chat'`).
   - Реализовать `callDeepSeekPrimary(payload, mode)` (`POST ${DEEPSEEK_BASE_URL}/chat/completions`, `temperature: 0.1`, таймаут 45с, `response_format: { type: 'json_object' }` в `schema`-режиме, устойчивый парсинг JSON с очисткой markdown fences и управляющих символов).
   - Выстроить явную цепочку провайдеров: `primary` (`deepseek` или `gemini` по `JUDGMENT_PRIMARY`) → второй доступный LLM-провайдер → явный fallback (`heuristic-fallback` с честным полем/логом источника, либо `UNAVAILABLE` при `JUDGMENT_ALLOW_HEURISTIC_FALLBACK=false`).
   - Обновить `GET /health`: честно возвращать `ok`, `primary`, `activeProvider`, `deepseekAvailable`, `geminiAvailable`, `fallbackChain`, `shadowDir`.
2. **`docker-compose.yml` (сервис `judgment-proxy`):**
   - Прокинуть `JUDGMENT_PRIMARY: ${JUDGMENT_PRIMARY:-deepseek}`, `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}`, `DEEPSEEK_BASE_URL: ${DEEPSEEK_BASE_URL:-https://api.deepseek.com}`, `DEEPSEEK_MODEL: ${DEEPSEEK_MODEL:-deepseek-chat}` (сохранив `GEMINI_API_KEY` как опциональный fallback).
3. **Тест-заслон (`node --test` + живой smoke):**
   - Создать `judgment-proxy/server.test.js`:
     - Проверить `/health` при `JUDGMENT_PRIMARY=deepseek` (с ключом и без ключа — честный `activeProvider` и `deepseekAvailable`).
     - Проверить `/judge` (`schema` и `text` режимы) против локального mock DeepSeek HTTP-сервера: успешный `FINDING`/`ABSTAIN`/`TEXT`, а также явный fallback при HTTP 500/429 от primary.
   - Пересобрать и перезапустить `eneikproductionsys-judgment-proxy-1` (`docker compose up -d --build judgment-proxy`), проверить `curl -s http://127.0.0.1:8093/health` (должен показывать `primary: "deepseek"`).
4. Зафиксировать коммит (`fix(d003,d010): switch judgment-proxy primary to DeepSeek with explicit fallback chain`) и обновить `docs/reports/PROGRAMMER_STATE.md`.

---

## 13. МЕХАНИЗМ №13 (P1 WORK ORDER #11 ОТ 2026-11-24 — ТАКТ 4 ПОСЛЕ ЗАКРЫТИЯ P0 #1–#3): ЧЕСТНОСТЬ PR / АНТИ-ОРФАН MIRROR (`D010`/`D003`), ТЕРМИНАЦИЯ ОТРАВЛЕННЫХ `SCOPE_INSUFFICIENT` (`D003`/`D002`) И РАЗГРАНИЧЕНИЕ UI С `AESTHETIC-MASTER` (`D006`/`D001`)

**Статус:** P1 ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (ВЫПОЛНЯТЬ СТРОГО ПОСЛЕ ЗАКРЫТИЯ И ЖИВОЙ ВЕРИФИКАЦИИ P0 #1–#3 ИЗ §12)  
**Основание:** Срочная директива Оператора от `2026-11-24 22:45:00 UTC` ([OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md)), контекст экосистемы ([ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md](ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md)) и форензика L1 ([INCIDENT_DIAGNOSIS_2026-11-24_DEEPSEEK_P1_AUTONOMOUS_CLOSURE.md](INCIDENT_DIAGNOSIS_2026-11-24_DEEPSEEK_P1_AUTONOMOUS_CLOSURE.md)).  
**Жёсткий регламент (`GEMINI.md` §0–§1):**
- **Сначала полностью закрыть §12 (Такты 1 → 2 → 3)** и довести их до живого рантайма.
- Затем в **Такте 4** реализовать Механизм №13 с попперовскими тестами-заслонами в Docker Maven (`-m 2g`), пересобрать `eneikproductionsys-backend-1` и подтвердить живую телеметрию.
- **Категорический запрет:** никаких ручных правок репозитория продукта (`knowledge-base-crie` и др.).

### 13.1. Подмеханизм 4A — Ликвидация ложного успеха PR (`422 no history in common with main`) и симулякра `local-repo/pulls` (`D010` Голдман + `D003` Леонелли + `D013` Сэлмон)
**Диагностированный дефект (`22:41 UTC` + живой bare-mirror `/var/lib/eneik/mirrors/knowledge-base-crie.git`):**
- `DeepSeekWorktreeExecutionEngine.java:317` при отказе `GitHubPullRequestService.createPullRequest` (HTTP 422 `no history in common with main`) молча возвращает фиктивный URL `"https://github.com/local-repo/pulls/task-" + task.getId()` и помечает задачу `COMPLETED` (`status=pr_opened`, `Belnap=TRUE`), отравляя `JulesDispatchService` (`Replaying stranded pr_opened workflow`).
- Корень 422 доказан на живом зеркале: коммит `60cdcae` ветки `worker/c6f75610-...` имеет **`parents=[]` (orphan root commit)**, т.к. `GitWorktreeExecutionManager.java:98,110` использует `--mirror` (`+refs/*:refs/*`) + конкурентный безблокировочный `git fetch origin --prune`, который **сносит локальные ветки `refs/heads/worker/*` прямо из-под активных worktree других потоков**. Кроме того, `GitWorktreeExecutionManager.push` (`line 303`) игнорирует exit code, а `DeepSeekWorktreeExecutionEngine:200-204` глотает исключения `push`.

**Обязательные изменения L2:**
1. **`DeepSeekWorktreeExecutionEngine.java`:**
   - **Полностью удалить** fallback `"https://github.com/local-repo/pulls/task-" + task.getId()`.
   - Не глотать ошибку `worktreeManager.push(...)`: неуспешный `push` обязан прерывать публикацию PR.
   - Перед открытием PR проверять, что `HEAD` ветки worktree имеет реального общего предка с `origin/<startingBranch>` (не `parents=[]`).
   - При отказе создания PR в `GitHubPullRequestService` (HTTP 422 / `no history in common with main` / пустой `Optional` / исключение):
     - Автоматически вызвать `worktreeManager.resyncMirrorWithOriginMain(task.getProject())`.
     - Пометить сессию честным статусом отказа (`status = "pr_creation_failed"` / `"closed_no_code"`, `belnapStatus = BelnapStatus.FALSE.name()`, `prUrl = null`, явный `closureReason` с кодом/причиной ошибки) и вернуть `WorkerDispatchResult.failed(...)`. **Никогда** не возвращать `COMPLETED` / `pr_opened` без реального GitHub PR URL.
   - Синхронизировать переиспользование/закрытие предсохранённой сессии из `JulesDispatchService:708-722`, чтобы при маршрутизации/fallback в `DeepSeekWorktreeExecutionEngine` не оставалось осиротевшей `running`-сессии `local-agy-*`.
2. **`GitWorktreeExecutionManager.java` (потокобезопасный bare-mirror без орфан-коммитов):**
   - Добавить per-mirror блокировку (`ConcurrentHashMap<Path, ReentrantLock>`) вокруг операций синхронизации зеркала и создания/удаления worktree.
   - Устранить снос локальных `refs/heads/worker/*`: настроить/использовать безопасный refspec `+refs/heads/*:refs/remotes/origin/*` при fetch (не запускать `--mirror` prune поверх локальных `refs/heads/worker/*`), базируя `createWorktree` от `origin/<startingBranch>` (с явным fallback на `main`).
   - Реализовать `resyncMirrorWithOriginMain(ProjectEntity project)` (force-обновление `origin/main` и `main`, при повреждении/расхождении истории — безопасная переинициализация bare-mirror) и метод `hasCommonHistoryWithBase(Path worktreePath, String baseBranch)`.
   - В `push(Path worktreePath, String branch)` проверять exit code команды `git push` и выбрасывать исключение при `exit != 0`.
3. **`GitHubPullRequestService.java` (при необходимости расширенный результат/диагностика):**
   - Обеспечить распознавание HTTP 422 (`no history in common with main` / validation failed) для детерминированного вызова `resyncMirrorWithOriginMain` в `DeepSeekWorktreeExecutionEngine`.

### 13.2. Подмеханизм 4B — Терминация отравленных задач `SCOPE_INSUFFICIENT` (`crie_*.py` в Java-репозитории) (`D003` Леонелли + `D002` Райл/Дюэм-Куайн + `D010` Голдман)
**Диагностированный дефект (`22:44–22:48 UTC`):**
- Задачи `knowledge-base-crie` (`4c4837ff`, `d976d831`, `a6f64e24`) каждую минуту возвращают `SCOPE_INSUFFICIENT: [crie_persistent_memory.py, crie_document_parser.py, crie_assistant_api.py, crie_smart_consultant.py]` в Java/Maven-репозитории, не имеют дерева файлов в промпте `DeepSeekWorktreeExecutionEngine` и бесконечно возвращаются в `TaskStatus.queued`.

**Обязательные изменения L2:**
1. **`DeepSeekWorktreeExecutionEngine.java`:**
   - Подгружать и передавать в промпт актуальное дерево файлов репозитория `worktreeManager.listRepositoryFiles(worktreePath)` (единообразно с `LocalAgyWorktreeExecutionEngine.java:141-150`).
   - При получении `ScopeStatus.SCOPE_INSUFFICIENT` классифицировать `missingFiles` относительно `repoFiles`:
     - Если отсутствующие файлы не существуют в `repoFiles` и относятся к несовместимому стеку (например, `*.py` при наличии `pom.xml` и отсутствии `*.py` в репозитории), пометить отказ явным кодом **`POISONED_CROSS_STACK_SCOPE_INSUFFICIENT`** (сохранив `closureReason`).
2. **`ClaimService.java` и `JulesDispatchService.java`:**
   - При `POISONED_CROSS_STACK_SCOPE_INSUFFICIENT` (или при повторном `>= 2` возврате `SCOPE_INSUFFICIENT` на несуществующие в репозитории файлы) **немедленно выводить задачу из бесконечного цикла ретраев**: переводить в терминальный отказ / перекомпиляцию (`TaskStatus.blocked` с `julesDispatchStatus = "NEEDS_RECOMPILATION: POISONED_SCOPE_INSUFFICIENT..."` + запись в `DefectJournal` / `needs_human_review`, без возврата в `queued`).
   - Учитывать сессии со статусом `scope_insufficient` в монотонной вариант-функции отказов `ClaimService`.

### 13.3. Подмеханизм 4C — Граница ответственности с `aesthetic-master` (UI vs Backend/API) (`D006` Раз + `D003` Леонелли + `D001` Поточник)
**Диагностированный дефект:**
- `DesignShopOrchestrationService` продолжает запускать цикл макетов (`20261124211039-mockup`) для `knowledge-base-crie`; `LocalVerificationRunner` перезаписывает/гейтит `src/main/resources/static/index.html`; `FileScopeEnforcer` не блокирует модификацию внешне управляемого UI.

**Обязательные изменения L2 (строго на уровне конфигурируемых механизмов Фабрики, без ручных правок продукта):**
1. **`SystemSettingsService.java` (реестр внешнего владения UI):**
   - Зарегистрировать настройку Фабрики (например, `external_ui_owned_projects`, по умолчанию включающую `knowledge-base-crie`), фиксирующую, что для указанных проектов фронтенд `src/main/resources/static/index.html` эксклюзивно ведёт `aesthetic-master`, а Фабрика отвечает строго за Java 21 / Spring Boot 3 backend, SQLite (`data/agent_memory.sqlite`) и REST API (`/crie/api/chat`, `/crie/api/documents/upload`, `/crie/api/dialogues/history`, `/crie/api/catalog/search`, `/crie/api/download/...`).
2. **`FileScopeEnforcer.java` (Raz Deontic Gate D006):**
   - Для проектов с внешним владельцем UI безусловно запрещать модификацию `src/main/resources/static/index.html` (и защищённых UI-путей), даже если путь указан в `task.fileScope` или `fileScope` пуст: немедленный `rollbackWorktree` и `ExclusionaryReasonViolationException` (`EXTERNAL_UI_OWNED_BY_AESTHETIC_MASTER`).
3. **`LocalVerificationRunner.java`:**
   - Для проектов с внешним владельцем UI пропускать `compileAndSyncFrontendToStatic` и `verifyLevelOfAbstractionLock` в отношении `src/main/resources/static/index.html`, чтобы верификатор никогда не перезаписывал и не блокировал UI `aesthetic-master`.
4. **`DesignShopOrchestrationService.java`:**
   - В `tick()` пропускать проекты с внешним владельцем UI (`EXTERNAL_UI_OWNER`), полностью устраняя двойное владение UI и паразитные дизайн-ревью задачи (`c6f75610`).
5. **`DeepSeekApiClient.java` / `LocalAgyCliClient.java`:**
   - Для проектов с внешним владельцем UI добавлять в промпт явную границу ответственности (backend Java 21 / Spring Boot 3, SQLite `data/agent_memory.sqlite`, REST API `/crie/api/*`; `src/main/resources/static/index.html` — read-only / `aesthetic-master`).

### 13.4. Тесты-заслоны (Поппер D008, Docker Maven `-m 2g`) и живая приёмка L1
1. **`DeepSeekWorktreeExecutionEngineTest.java` + `GitWorktreeExecutionManagerTest.java`:**
   - **Заслон 4A-1 (D010):** при отказе `GitHubPullRequestService.createPullRequest` (`Optional.empty()` / HTTP 422 `no history in common with main`) `DeepSeekWorktreeExecutionEngine.dispatch` **НЕ возвращает `COMPLETED`** и **НЕ записывает `https://github.com/local-repo/pulls/...`**, вызывает `resyncMirrorWithOriginMain`, помечает сессию отказом (`Belnap=FALSE`, `prUrl == null`) и возвращает `WorkerDispatchResult.failed(...)`.
   - **Заслон 4A-2 (D013):** воспроизвести конкурентный fetch зеркала при активном worktree — ветка `worker/<id>` не удаляется, коммит сохраняет родителя `main` (`merge-base` не пуст, не orphan `parents=[]`).
   - **Заслон 4B (D003/D002):** задача в Java/Maven-репозитории (`pom.xml`), вернувшая `SCOPE_INSUFFICIENT` с несуществующими `crie_*.py` (или повторно `>= 2` раз на несуществующие файлы), переводится в терминальный отказ / `NEEDS_RECOMPILATION` и не возвращается в `TaskStatus.queued`.
2. **`FileScopeEnforcerTest.java` + `LocalVerificationRunnerTest.java` + `DesignShopOrchestrationServiceTest.java`:**
   - **Заслон 4C (D006/D001):** для проекта с внешним владельцем UI (`knowledge-base-crie`):
     - попытка воркера изменить `src/main/resources/static/index.html` блокируется `FileScopeEnforcer` с физическим rollback;
     - `LocalVerificationRunner` не перезаписывает `src/main/resources/static/index.html`;
     - `DesignShopOrchestrationService.tick()` пропускает проект без создания дизайн-цикла.
3. **Фиксация и живой рантайм:**
   - Коммит `fix(d010,d003,d006): block fake local-repo PRs, terminate cross-stack SCOPE_INSUFFICIENT, enforce aesthetic-master UI boundary`.
   - Пересобрать `eneikproductionsys-backend-1`, проверить в живом рантайме: `0` ссылок `local-repo/pulls`, `0` бесконечных повторов `crie_*.py`, `0` новых дизайн-циклов по `knowledge-base-crie`, CPU `< 50%`, RAM с запасом до `1GiB`, диск `/` `< 70%`.
   - Записать доказательный отчёт в `docs/reports/PROGRAMMER_STATE.md` для финальной приёмки L1.



---

## 14. МЕХАНИЗМ №14 (P2 WORK ORDER #12 ОТ 2026-11-24 — ТАКТ 5 ПОСЛЕ ЗАКРЫТИЯ P0 #1–#3 И P1 #4): ПОДКЛЮЧЕНИЕ MEMORILLA ($K=16$) И ДИНАМИЧЕСКОЙ РОЛИ (`task.getRole()`) В СУВЕРЕННЫЕ ВОРКЕРЫ (`DeepSeek`, `LOCAL_AGY`) И ОРКЕСТРАТОР

**Статус:** P2 ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (ВЫПОЛНЯТЬ СТРОГО ПОСЛЕ ЗАКРЫТИЯ И ЖИВОЙ ВЕРИФИКАЦИИ §12 P0 #1–#3 И §13 P1 #4)  
**Основание:** Автономное поручение Оператора Дмитрия Ефремова (`2026-11-24 22:56 UTC`), спецификация Memorilla (§6 выше) и форензика L1 ([INCIDENT_DIAGNOSIS_2026-11-24_MEMORILLA_WORKERS_ROLE_WIRING.md](INCIDENT_DIAGNOSIS_2026-11-24_MEMORILLA_WORKERS_ROLE_WIRING.md)).  
**Философский корпус (верифицировано в `docs/philosopher-patterns/philosophers/`):**
- `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` (`D014 RAG hallucination`)
- `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (`D013 Runtime drift`)
- `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` (`D010 Data lineage loss`)
- `GILBERT_RAYL_02_INFERENTIAL_SCOREBOARD` (`D001 Semantic drift`)
- `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (`D006 Authorization ambiguity`)
- `KARL_POPPER_01_FALSIFICATION_HARNESS` (`D008 False green`) / `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` (`D008 False green`)

**Жёсткий регламент (`GEMINI.md` §0–§1):**
- **Сначала полностью закрыть §12 (Такты 1–3) и §13 (Такт 4)** в живом рантайме.
- **Затем в Такте 5** реализовать Механизм №14 целиком с попперовскими тестами-заслонами в Docker Maven (`-m 2g`), не ослабляя защищённый контур автономии Деннетта (`DennettAutonomyFalsificationTest` — 100% green) и не внося ручных правок в репозитории продуктов.

### 14.1. Диагностированные разрывы в живом коде
1. **`GeminiContextService.java:785-1015` (`D013`/`D014`):** `buildProductWorkerLatentMemoryBlock(RoleEntity, String)` и `buildLatentSemanticMemoryBlock(String)` имеют **0 вызовов в `src/main/java`** (вызываются только в `GeminiContextServiceTest`), несмотря на включённый в живой БД флаг `gemini_context_learning_enabled=true`.
2. **`JulesDispatchService.java:698-757` (`D001`/`D013`):** при `worker_engine_mode=HYBRID` суверенный роутинг уходит в `WorkerExecutionRouter` **до** загрузки `RoleCapabilityLoader.loadRawCharter(task.getRole().getTag())` и `GeminiContextService`.
3. **`DeepSeekApiClient.java:198-242`:** получает только `roleTag` + `roleDescription` и общий статический префикс `BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX`, без реального устава роли и без ортогональных слотов Memorilla ($K=16$).
4. **`LocalAgyCliClient.java:226-276`:** в `buildAgyPrompt(...)` вообще не передаёт `task.getRole().getTag()`, `Role Description`, Role Charter и Memorilla — локальный воркер работает без ролевой идентичности.
5. **`GeminiContextService.retrieveLatentMemorySlots` (`lines 804-828`):** при `embed(query) == null` или пустом индексе молча возвращает `List.of()` без телеметрии причины.

### 14.2. Обязательные изменения L2
1. **Единый сборщик ролевого контекста и Memorilla — `SovereignWorkerContextAssembler` (`src/main/java/com/eneik/production/services/worker/SovereignWorkerContextAssembler.java`):**
   - Принимает `@Autowired(required = false) GeminiContextService` и `@Autowired(required = false) RoleCapabilityLoader` (safe-null для юнит-тестов).
   - **Динамическое принятие роли в момент диспетчеризации (`task.getRole()`, `D001` Райл):**
     - Извлекает `roleTag` и `roleDescription` из `task.getRole()`.
     - Для не-carrier задач загружает сырой устав роли через `roleCapabilityLoader.loadRawCharter(roleTag)` (кэширован по `mtime`, без лишнего I/O; ограничить бюджет устава до `<= 4000` символов, **никогда не обрезая `task.getDescription()`**).
   - **Подключение Memorilla ($K=16$) со стратификацией Тарского (`D010` Голдман + `D014` Чёрч):**
     - Формирует сфокусированный `retrievalQuery` из `mode`, `roleTag`, `roleDescription`, `task.getTitle()`, компактного среза `task.getDescription()` (`<= 600` символов) и `task.getFileScope()`.
     - **Жёсткая демаркация Тарского L0:** для `TargetContext.ORCHESTRATOR_SYSTEM` вызывает `geminiContextService.buildLatentSemanticMemoryBlock(retrievalQuery)`; для `TargetContext.PRODUCT_CODEBASE` (и любого `null`/прочего значения — fail-safe default!) вызывает строго `geminiContextService.buildProductWorkerLatentMemoryBlock(task.getRole(), retrievalQuery)` (`PRODUCT_WORKER_SOURCE_TYPES`, 0 утечек `observer_log` и `factory_mechanisms`).
     - Возвращает структурированный контекстный блок (Role Charter + Memorilla) и пишет диагностический лог с `taskId`, `roleTag`, `targetContext`, количеством инъектированных слотов Memorilla (`slots=N`) и размером блока.
2. **`DeepSeekWorktreeExecutionEngine.java` и `DeepSeekApiClient.java` (СОХРАНИТЬ CONTEXT CACHING `D006`):**
   - **Инвариант кэша DeepSeek:** константный префикс `BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX` (`DeepSeekApiClient.getOntologicalCachePrefix()`) обязан оставаться **побайтово неизменным в самом начале `systemPrompt`** (`offset 0`) для всех задач и ролей (гарантия `prompt_cache_hit_tokens`).
   - Вызывать `SovereignWorkerContextAssembler.assemble(task, mode)` **один раз** перед циклом `attempt = 1..2` в `DeepSeekWorktreeExecutionEngine.dispatch(...)` и передавать собранный блок в перегруженный `DeepSeekApiClient.generateSolution(task, fileContentsContext, account, reflectionFeedback, workerDoctrineContext)` (сохранив существующую 4-аргументную сигнатуру как делегирующую с `null`).
   - В `DeepSeekApiClient.buildUserPrompt(...)` (или в хвосте `buildSystemPrompt` строго **после** `BARCAN_TAG_ONTOLOGICAL_CACHE_PREFIX`) вставлять блок `### ACTIVE ROLE CHARTER & MEMORILLA DOCTRINE MEMORY (K=16)` без обрезки `task.getDescription()`.
3. **`LocalAgyWorktreeExecutionEngine.java` и `LocalAgyCliClient.java` (устранение слепоты к роли):**
   - Вызывать `SovereignWorkerContextAssembler.assemble(task, mode)` в `LocalAgyWorktreeExecutionEngine.dispatch(...)` один раз до цикла попыток и передавать в перегруженный `LocalAgyCliClient.generateSolution(...)` / `buildAgyPrompt(...)` (сохранив обратную совместимость существующих конструкторов и 3-аргументного `buildAgyPrompt`).
   - В `LocalAgyCliClient.buildAgyPrompt(...)` обязательно выводить `Role Tag` и `Role Description` (если `task.getRole() != null`) + блок Role Charter и Memorilla ($K=16$) от `SovereignWorkerContextAssembler`.
4. **Переключение оркестраторских/облачных потребителей с legacy top-5 на Memorilla ($K=16$):**
   - `OpsAuditorService.java:163` и `FalsificationCycleService.java:646`: перевести на `geminiContextService.buildLatentSemanticMemoryBlock(...)` (с сохранением no-throw контракта).
   - `JulesDispatchService.java:1027-1029` (облачный резерв): перевести `buildProductWorkerContextBlock` → `buildProductWorkerLatentMemoryBlock` и `buildContextBlock` → `buildLatentSemanticMemoryBlock` (строго минимальный 2-строчный точечный патч, не затрагивающий ни одной строки защищённого контура автономии Деннетта).
5. **Телеметрия деградации Memorilla в `GeminiContextService.java` (`D013` Витгенштейн):**
   - В `retrieveLatentMemorySlots(...)` добавить явное логирование (`log.debug`/`log.warn`) причины пустого результата (`learning_disabled`, `empty_corpus`, `embed_unavailable`, `below_similarity_floor`), чтобы немой провал памяти был виден в живых логах.

### 14.3. Тесты-заслоны (`KARL_POPPER_01_FALSIFICATION_HARNESS` D008, Docker Maven `-m 2g`) и живая приёмка L1
1. Создать `src/test/java/com/eneik/production/services/worker/SovereignWorkerMemorillaWiringTest.java`:
   - **Заслон 5-1 (DeepSeek Context Cache Invariance, `D006`):** `DeepSeekApiClient.buildSystemPrompt` для двух разных ролей (`BARCAN-TAG-02` и `BARCAN-TAG-06`) с разными Memorilla-блоками начинается с побайтово идентичного `DeepSeekApiClient.getOntologicalCachePrefix()`.
   - **Заслон 5-2 (Dynamic Role + Memorilla in DeepSeek, `D001`/`D014`):** при диспетчеризации задачи `BARCAN-TAG-02` промпт содержит тег роли, фрагмент Role Charter из `RoleCapabilityLoader` и секцию `MEMORILLA PRODUCT DOCTRINE MEMORY`.
   - **Заслон 5-3 (Dynamic Role + Memorilla in LOCAL_AGY, `D001`/`D013`):** `LocalAgyCliClient.buildAgyPrompt` для задачи `BARCAN-TAG-06` включает `BARCAN-TAG-06`, Role Charter и блок Memorilla (фальсифицирует прежнюю слепоту к роли).
   - **Заслон 5-4 (Tarski L0 Stratification, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` D010):** для задачи `TargetContext.PRODUCT_CODEBASE` (и `null`) вызывается **только** `buildProductWorkerLatentMemoryBlock(task.getRole(), query)` и **никогда** `buildLatentSemanticMemoryBlock`; `observer_log` и `factory_mechanisms` физически не могут попасть в промпт воркера. Для `TargetContext.ORCHESTRATOR_SYSTEM` вызывается `buildLatentSemanticMemoryBlock`.
   - **Заслон 5-5 (Graceful Degradation & Full Description Preservation):** при недоступности `GeminiContextService` / `RoleCapabilityLoader` (или пустых слотах) сборка промпта не бросает исключений и на 100% сохраняет `task.getDescription()`.
2. Прогнать в Docker Maven (`-m 2g`): `SovereignWorkerMemorillaWiringTest`, `GeminiContextServiceTest`, `DeepSeekApiClientTest`, `LocalAgyCliClientTest`, `DeepSeekWorktreeExecutionEngineTest`, `LocalAgyWorktreeExecutionEngineTest` и регрессионный контрольный прогон `DennettAutonomyFalsificationTest` (100% green).
3. Пересобрать `eneikproductionsys-backend-1`, проверить в живом логе появление записей сборки ролевого контекста и Memorilla ($K=16$) при диспетчеризации суверенного воркера, коммит `feat(d014,d010,d001): wire Memorilla K=16 and dynamic BARCAN role charter into sovereign workers`, отчёт в `docs/reports/PROGRAMMER_STATE.md` для финальной приёмки L1.


---

## 15. МЕХАНИЗМ №15 (СРОЧНЫЙ WORK ORDER #13 ОТ 2026-11-25 — ТАКТ 1): ПОЛНОЕ ИСКЛЮЧЕНИЕ GOOGLE JULES ИЗ ЦЕПОЧКИ ИСПОЛНЕНИЯ (`D013` / `D006` / `D003` / `D008`)

**Статус:** СРОЧНЫЙ ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (`STANDBY СНЯТ`, ВЫПОЛНЯТЬ ПЕРВЫМ — ТАКТ 1)  
**Основание:** Срочная директива Оператора Дмитрия Ефремова (`2026-11-25 11:55:41 UTC`, [OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md#L110-L112)) и форензика L1 ([INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md](INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md)).  
**Философский корпус (верифицировано в `docs/philosopher-patterns/philosophers/`):**
- `ELVIN_GOLDMAN_06_CAUSAL_PROCESS_TRACE` (`D013 Runtime drift`): ни один триггер (роутер, fallback-ревью, планировщик, селектор аккаунтов) не должен каузально достигать Google Jules API.
- `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` (`D006 Authorization ambiguity`): деонтический запрет `eneik.jules.enabled=false` по умолчанию в коде и конфигурации без пути включения через UI/БД.
- `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` (`D013 Runtime drift`): верификация по живым логам контейнера.
- `KARL_POPPER_01_FALSIFICATION_HARNESS` (`D008 False green`): попперовские тесты-заслоны в Docker Maven (`-m 2g`).

### 15.1. Критические инварианты безопасности (НЕ СЛОМАТЬ СУВЕРЕННЫЙ КОНВЕЙЕР!)
1. **КРИТИЧЕСКАЯ НАХОДКА ПО `accounts` (`remotecli3..10`):** в живой БД профили `remotecli3..10` (суверенные воркеры `LOCAL_AGY`) сейчас имеют `provider = 'GOOGLE_JULES'`. Перед тем как исключить `GOOGLE_JULES` из селекторов аккаунтов, **ОБЯЗАТЕЛЬНО** перевести `remotecli3..10` на `provider = 'LOCAL_AGY'` миграцией `V147`, сохранив их `id`, `enabled=true`, `status='idle'` и историю.
2. **КРИТИЧЕСКАЯ НАХОДКА ПО `JulesDispatchService`:** класс `JulesDispatchService` (несмотря на историческое имя) обслуживает общий суверенный конвейер (`DeepSeek` + `LOCAL_AGY`): маршрутизацию через `WorkerExecutionRouter` (`lines 698-785`), `reconcileStrandedPrOpenedWorkflows` (`@Scheduled`, `line 2231`), `processPendingReviewBatch` (`@Scheduled`, `line 2472`), `detectStuck` (`@Scheduled`, `line 5262`), `reconcileTaskStatusAgainstGitHubTruth` (`@Scheduled`, `line 5664`). **НЕЛЬЗЯ** отключать бин `JulesDispatchService` или эти 4 общих расписания — нужно хирургически отсечь **только** облачные ветви Google Jules (`julesApiClient.*`, `PendingFallbackReview` на Jules, fallthrough после `WorkerExecutionRouter`).
3. **КРИТИЧЕСКАЯ НАХОДКА ПО `SessionLifecycleService`:** бин инъектируется в `BranchGarbageCollectorService:45` и `JulesDispatchService` для локальной отмены (`retireSessionOnly` / `applyLocalCancelStatus`). Оставить локальную отмену доступной, но полностью отключить удалённый Jules-планировщик (`@Scheduled cleanupEligibleSessions`) и любые HTTP DELETE к Jules API при `eneik.jules.enabled=false`.
4. **Контур Деннетта (`SLOT-14`):** `DennettAutonomyFalsificationTest` обязан оставаться **100% green**. Исторические таблицы (`jules_sessions`, `accounts`, PR-история) **НЕ удалять**.

### 15.2. Обязательные изменения L2
1. **Жёсткий флаг запрета без UI-обхода (`application.properties` + `SystemSettingsService.java` + `JulesApiClient.java`):**
   - В `src/main/resources/application.properties` явно добавить:
     `eneik.jules.enabled=${ENEIK_JULES_ENABLED:false}`
   - В `SystemSettingsService.java`: закрыть возможность включать `jules_enabled` через UI/API (`effectiveBoolean("jules_enabled")` при `eneik.jules.enabled=false` всегда возвращает `false`; попытка сохранить `jules_enabled=true` через API/UI отвергается либо деонтически игнорируется с явным предупреждением).
   - В `JulesApiClient.java`: внедрить `@Value("${eneik.jules.enabled:false}") private boolean julesFeatureEnabled;` и гарантировать short-circuit (0 исходящих HTTP-запросов к `jules.googleapis.com`) во всех методах (`createSessionDetailed`, `sendMessage`, `getSessionStatus`, `getSessionPrUrl`, `getSessionActivities`, `deleteSession`, `checkSessionRaw`, `sourceAvailability`, `isEnabled`) при `!julesFeatureEnabled`.
2. **Удаление Tier 3 `GOOGLE_JULES` из `WorkerExecutionRouter.java` (`lines 24, 75-193`) и `JulesExecutionEngine.java`:**
   - В `WorkerExecutionRouter`:
     - Удалить fallback на `julesEngine` из `resolveEngine`, `dispatch`, `dispatchLocalAgyOrJules` (переименовать в `dispatchLocalAgyOnly`), `poll`, `cancel`.
     - Маршрут в `HYBRID`: строго **DeepSeek → LOCAL_AGY** (порядок НЕ менять!).
     - Если `worker_engine_mode=JULES` — не вызывать `julesEngine`; возвращать `WorkerDispatchResult.failed("GOOGLE_JULES_DECOMMISSIONED: Google Jules is removed from the execution chain", WorkerEngineType.JULES)` (или при `resolveEngine` не возвращать `julesEngine`).
     - Если оба суверенных движка (`DeepSeek` и `LOCAL_AGY`) недоступны или вернули отказ — вернуть `WorkerDispatchResult.failed("NO_SOVEREIGN_WORKER_AVAILABLE: ...", ...)` с явной причиной, чтобы задача осталась в очереди (освободилась в `queued` через `ClaimService`), а **не** ушла в Google Jules.
   - В `JulesExecutionEngine.dispatch`: при `!julesFeatureEnabled` немедленно возвращать `WorkerDispatchResult.failed("GOOGLE_JULES_DECOMMISSIONED", WorkerEngineType.JULES)` без вызова `julesDispatchService`.
3. **Отсечение Jules API в планировщиках и обработчиках:**
   - `SessionLifecycleService.java` (`lines 105-192`): вынести/отключить `@Scheduled cleanupEligibleSessions` при `eneik.jules.enabled=false` (например, `@ConditionalOnProperty(name = "eneik.jules.enabled", havingValue = "true")` на scheduler-компоненте или ранний выход в `cleanupEligibleSessions` и `deleteRemote` без вызова `julesApiClient.deleteSession`), сохранив `retireSessionOnly` / `applyLocalCancelStatus` для локального закрытия сессий.
   - `JulesDispatchService.java`:
     - В `dispatch(...)` (`lines 698-785`): если `workerExecutionRouter` не обработал задачу или режим `JULES` — **не проваливаться** в legacy `julesApiClient.createSessionDetailed` (`lines 787-980`), а завершать сессию с `closureReason = "GOOGLE_JULES_DECOMMISSIONED"` и возвращать отказ.
     - В `dispatchInternal` (`line 396`) и `processPendingReviewBatch` (`PendingFallbackReview`, `lines 2489+`): не создавать облачные Jules-сессии при `!julesFeatureEnabled`.
     - В `pollStatus` (`lines 1188-1240`) и `ContinuousOrchestrationService.pollActiveJulesSessions()` (`lines 1036-1060`): пропускать non-sovereign legacy Jules-сессии без вызова `julesApiClient`.
   - `InternalJulesActivitiesProbeController.java`: возвращать `410 Gone` при `eneik.jules.enabled=false`.
4. **Миграция `V147` и очистка селекторов пула аккаунтов (БЕЗ удаления истории и БЕЗ поломки `remotecli3..10`!):**
   - Создать `src/main/resources/db/migration/V147__decommission_jules_cloud_accounts_and_retag_local_agy.sql`:
     - Перетегировать суверенные локальные профили: `UPDATE accounts SET provider = 'LOCAL_AGY' WHERE LOWER(name) LIKE 'remotecli%' AND UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES';`
     - Декомиссировать все оставшиеся облачные Jules-аккаунты (включая stray `eneikcoworking-ctrl` со `status='idle', enabled=false`): `UPDATE accounts SET status = 'decommissioned', enabled = FALSE WHERE UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES';`
     - **НИ ОДНОЙ строки из `accounts` или `jules_sessions` НЕ удалять!**
   - `AccountRepository.java`: во всех селекторах выбора/подсчёта рабочих аккаунтов (`lockNextJulesAccountWithCapacity`, `lockNextIdleAccountForProject`, `lockNextIdleAccountForProjectAndCapability`, `existsJulesAccountWithCapacity`, `findAvailableForProjectOrderByNameAsc`, `countOperationalAccounts`, `countLiveAccounts`) исключить `UPPER(COALESCE(provider, 'GOOGLE_JULES')) = 'GOOGLE_JULES'`.
   - `AccountHealthService.java`: игнорировать `GOOGLE_JULES` в `recoverEligibleAccounts`, `resetDailyLimitedAccounts`, `reclassifyPreconditionDailyLimitedAccounts` и `logStaleDisabledAccounts` (прекратить 15-минутный спам про `eneikcoworking-ctrl`, сохранив восстановление `DEEPSEEK` и `LOCAL_AGY`).

### 15.3. Тесты-заслоны (`KARL_POPPER_01_FALSIFICATION_HARNESS`, Docker Maven `-m 2g`) и живая приёмка
1. Создать `src/test/java/com/eneik/production/services/worker/JulesDecommissioningFalsificationTest.java` (и обновить `DennettCranesHomeostasisTest.java` на суверенный failover `DeepSeek → LOCAL_AGY`):
   - **Заслон 13-1 (Router Never Picks Jules):** при любом режиме (`HYBRID`, `JULES`, `LOCAL_AGY`, `DEEPSEEK_WORKTREE`) и при недоступности/падении обоих суверенных движков `WorkerExecutionRouter` **никогда** не вызывает `JulesExecutionEngine`, возвращает `WorkerDispatchResult.failed(...)` с явной причиной, а при доступности DeepSeek / LOCAL_AGY сохраняет точный порядок `DeepSeek → LOCAL_AGY`.
   - **Заслон 13-2 (Zero Jules API Calls & No UI Re-enable):** `JulesApiClient` при `eneik.jules.enabled=false` (даже если в `system_settings` записано `jules_enabled=true`) не совершает ни одного HTTP-вызова; `SessionLifecycleService.cleanupEligibleSessions()` и `JulesDispatchService` не вызывают `julesApiClient`.
   - **Заслон 13-3 (Account Pool & `V147` Integrity):** интеграционный тест на H2 + Flyway (`V1..V147`) подтверждает, что `remotecli3..10` получили `provider='LOCAL_AGY'` и выбираются, а `GOOGLE_JULES`-аккаунты не выбираются ни одним селектором `AccountRepository`, при этом исторические записи `accounts` и `jules_sessions` сохранены.
2. Прогнать в Docker Maven (`-m 2g`): `JulesDecommissioningFalsificationTest`, `DennettCranesHomeostasisTest`, `DennettAutonomyFalsificationTest` (100% green), `FlywayMigrationValidationTest`, `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `AccountHealthServiceTest`, `JulesDispatchServiceTest`.
3. Пересобрать `eneikproductionsys-backend-1` (`docker compose up -d --build backend`), подтвердить в живом логе за 15+ мин: `0` обращений к Jules API, `0` запусков `SessionLifecycleService` remote cleanup, `0` предупреждений `AccountHealthService` про `eneikcoworking-ctrl`, `remotecli3..10` и `deepseek-worker-1..8` доступны.
4. Коммит `fix(d013,d006): decommission Google Jules from execution chain and retag remotecli pool to LOCAL_AGY`, отчёт `<= 20` строк в `docs/reports/PROGRAMMER_STATE.md`.

---

## 16. МЕХАНИЗМ №16 (СРОЧНЫЙ WORK ORDER #14 ОТ 2026-11-25 — ТАКТ 2 ПОСЛЕ ПРИЁМКИ/ЗАКРЫТИЯ WO #13): НАКОПЛЕНИЕ И КОНСОЛИДАЦИЯ ВИШЛИСТОВ ЗАМОРОЖЕННЫХ ПРОЕКТОВ (`D010` / `D012` / `D002` / `D008`)

**Статус:** СРОЧНЫЙ ОБЯЗЫВАЮЩИЙ НАРЯД L1 → L2 (ВЫПОЛНЯТЬ ВТОРЫМ — ТАКТ 2, СТРОГО ПОСЛЕ ЗАКРЫТИЯ §15)  
**Основание:** Срочная директива Оператора Дмитрия Ефремова (`2026-11-25 11:55:41 UTC`, [OPERATOR_DIRECTIVES.md](OPERATOR_DIRECTIVES.md#L110-L112)) и форензика L1 ([INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md](INCIDENT_DIAGNOSIS_2026-11-25_WO13_WO14_JULES_REMOVAL_FROZEN_WISHLISTS.md)).  
**Философский корпус (верифицировано в `docs/philosopher-patterns/philosophers/`):**
- `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` (`D010 Data lineage loss`): источник, роль, временная метка и неразрывная ссылка `merged_into_wishlist_id` / `consolidated_from_ids` у каждой записи.
- `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` (`D012 Policy contradiction` / `D002 Invalid state`): явные статусы `deferred_frozen` и `consolidated`; противоречащие директивы (`BOTH`) никогда не затираются молча.
- `KARL_POPPER_01_FALSIFICATION_HARNESS` (`D008 False green`): попперовские тесты-заслоны на накопление, группировку, lineage, идемпотентность и сохранение конфликтов.

### 16.1. Обязательные изменения L2
1. **Схема БД и модель (`V148__wishlist_frozen_consolidation_lineage.sql` + `WishlistStatus.java` + `WishlistEntity.java` + `WishlistResponseDto.java`):**
   - В `WishlistStatus.java`: добавить `deferred_frozen` (15 симв.) и `consolidated` (12 симв.) — оба укладываются в `VARCHAR(24)` (`V14`).
   - В `V148__wishlist_frozen_consolidation_lineage.sql`:
     - `ALTER TABLE wishlist ADD COLUMN merged_into_wishlist_id UUID NULL REFERENCES wishlist(id) ON DELETE SET NULL;`
     - `ALTER TABLE wishlist ADD COLUMN consolidated_from_ids TEXT NULL;`
     - `ALTER TABLE wishlist ADD COLUMN consolidation_conflict BOOLEAN NOT NULL DEFAULT FALSE;`
     - `ALTER TABLE wishlist ADD COLUMN consolidation_notes TEXT NULL;`
     - `CREATE INDEX idx_wishlist_project_merged_into ON wishlist(project_id, merged_into_wishlist_id);`
   - В `WishlistEntity.java`: добавить поля + геттеры/сеттеры; убедиться, что `movable()` возвращает `false` для `deferred_frozen` и `consolidated`.
   - В `WishlistResponseDto.java` (с сохранением обратной совместимости конструктора): пробросить `mergedIntoWishlistId`, `consolidatedFromIds`, `consolidationConflict` (при необходимости).
2. **Приём вишлистов в замороженный проект без отклонения и без декомпозиции (`OperationalPolicyService.java` + `ProjectFlowService.java`):**
   - `ProjectStatus.frozen.isOperational()` (`ProjectStatus.java:40`) **НЕ МЕНЯТЬ** (должен оставаться `false`, `DennettAutonomyFalsificationTest` обязан остаться green!).
   - `OperationalPolicyService.java:116`: разрешить `OperationalAction.ADD_WISHLIST` также для `ProjectStatus.frozen` (и `waiting`), оставив запрет для `accepted`, `archived`, `analyzing` и сохранив полный запрет `ORCHESTRATE`, `DISPATCH_*`, `MERGE_PR`, `RECOVER_*` для `frozen`.
   - `ProjectFlowService.addWishlistItem` (`lines 804-832`): если `project.getStatus() == ProjectStatus.frozen` (или `ProjectStatus.waiting`), сохранять запись в статусе **`WishlistStatus.deferred_frozen`** (сохраняя `content`, `source`, `sourceRoleTag`, `createdAt`, `targetContext`), **НЕ** запуская декомпозицию и **НЕ** создавая задач.
3. **Сервис консолидации при разморозке (`FrozenWishlistConsolidationService.java`):**
   - Новый выделенный сервис (подключается в `ProjectFlowService.activateProject` при переходе с `frozen` → `active`, а также вызывается как идемпотентный защитный этап в `ProjectFlowService.executeOrchestrate` строго **до** любой декомпозиции).
   - Алгоритм `consolidateForProject(UUID projectId)` (в короткой транзакции БД, без сетевого I/O, `HikariPool`-safe):
     1. Загружает все записи проекта в статусе `WishlistStatus.deferred_frozen`, а также нескомпилированные корневые `pending`-записи, у которых `originWishlistId == null`, `mergedIntoWishlistId == null`, `consolidatedFromIds == null` (это автоматически подхватит 3 существующие записи в `knowledge-base-crie` `caa495f4`, `e41fc341`, `62f73c58` при разморозке, без ручного вмешательства!).
     2. Если кандидатов `0` — немедленный no-op (строгая идемпотентность).
     3. Группирует кандидатов по смыслу (связные компоненты по нормализованному сходству токенов/якорей + совместимость `targetContext`, с соблюдением запрета слияния записей с разными непустыми `sourceTaskId` и разных скомпилированных слайсов).
     4. **Детекция противоречий (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE`, D012):** внутри каждой группы проверяет пары на полярные/конфликтующие директивы (например, пары «добавить/включить/разрешить/оставить/использовать» vs «удалить/убрать/отключить/запретить/не использовать» в отношении общего объекта, или взаимоисключающие целевые значения). При обнаружении противоречия:
        - записи **НЕ** сливаются молча;
        - в обобщённой записи выставляется `consolidationConflict = true`, в `consolidationNotes` и в тексте `content` добавляется явный раздел `[CONFLICT DETECTED — REQUIRES RESOLUTION]` с цитированием обеих сторон, их `id`, источников/ролей и временных меток `createdAt`.
     5. Для каждой группы формирует **одну** обобщённую запись (`WishlistStatus.pending`):
        - `content`: структурированное обобщение группы + явный блок `Lineage / Source Wishlists` с перечнем всех исходных `id`, `source`, `sourceRoleTag` и `createdAt`;
        - `consolidatedFromIds`: сериализованный JSON-массив исходных UUID (в детерминированном хронологическом порядке);
        - `source` и `sourceRoleTag`: канонические (если среди исходников есть `client` → `source = client`, `sourceRoleTag = null`; если только `role` → объединённый валидный `BARCAN-TAG-XX` через `WishlistEntity.validateCanonicalSourceRoleTag`).
     6. Каждую исходную запись группы переводит в `WishlistStatus.consolidated` с `mergedIntoWishlistId = consolidated.getId()` (**не удаляет** и не переписывает её исходный `content`/`createdAt`!).
     7. В декомпозицию (`executeOrchestrate`) идут строго обобщённые `pending`-записи.
4. **Согласованность знаменателей (`ClientDeliverableReadinessService` + `FlowSpineService` + `OperationalTruthService`):**
   - Исключить `WishlistStatus.consolidated` из незакрытых знаменателей (аналогично `dismissed`), а `deferred_frozen` не считать активным `pendingWishlist` для запуска декомпозиции.

### 16.2. Тесты-заслоны (`KARL_POPPER_01_FALSIFICATION_HARNESS`, Docker Maven `-m 2g`) и живая приёмка
1. Создать `src/test/java/com/eneik/production/services/FrozenWishlistConsolidationTest.java`:
   - **Заслон 14-1 (Frozen Accumulation, No Tasks):** `addWishlistItem` для `ProjectStatus.frozen` сохраняет запись в `WishlistStatus.deferred_frozen` (сохранены текст, источник/роль, `createdAt`), не отклоняется `OperationalPolicyService` и создаёт `0` задач; при этом `ORCHESTRATE` / `DISPATCH_QUEUED_TASKS` для `frozen` по-прежнему запрещены.
   - **Заслон 14-2 (Semantic Grouping + Lineage):** при разморозке (`activateProject`) $N$ близких накопленных записей сворачиваются в `1` обобщённую `pending`-запись с `consolidatedFromIds` на все $N$ UUID, а все $N$ исходных записей сохраняются в БД со статусом `consolidated` и `mergedIntoWishlistId = <id обобщённой>`.
   - **Заслон 14-3 (Idempotency):** повторная заморозка/разморозка и повторный вызов `consolidateForProject` не создают дубликатов обобщённых записей (`count == 1`).
   - **Заслон 14-4 (Conflict Preservation, Belnap BOTH):** противоречащие накопленные записи (например, «включить X» и «полностью отключить и удалить X») не сливаются молча — `consolidationConflict == true`, обе позиции и оба исходных `id` явно присутствуют в обобщённой записи.
   - **Заслон 14-5 (H2 + Flyway `V1..V148`):** миграция `V148` проходит `FlywayMigrationValidationTest`, а существующие `pending`-записи на замороженном проекте подхватываются консолидацией при разморозке.
2. Прогнать в Docker Maven (`-m 2g`): `FrozenWishlistConsolidationTest`, `FlywayMigrationValidationTest`, `DennettAutonomyFalsificationTest` (100% green), `ProjectFlowIntegrationTest` / `ProjectFlowServiceTest`, `ClientDeliverableReadinessServiceTest`.
3. Пересобрать `eneikproductionsys-backend-1`, проверить на живом рантайме `UP`, `0` ошибок старта/Flyway, 3 существующих вишлиста `knowledge-base-crie` не тронуты вручную.
4. Коммит `feat(d010,d012): accumulate wishlists for frozen projects and consolidate with lineage on unfreeze`, отчёт `<= 20` строк в `docs/reports/PROGRAMMER_STATE.md`.
