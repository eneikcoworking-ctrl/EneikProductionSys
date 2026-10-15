# Пакетная директива L1/L3 -> L2: Секция XII (Журнал, память, санитария)

**Дата:** 2026-10-15 19:03 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция XI («Наблюдение за живым продуктом клиента, апостериорный каданс и доказательства возможностей продукта») полностью замерена и закрыта инженером (79/79 green в коммите `db6a1ee`). Настоящая директива охватывает целиком следующее фундаментальное семейство конвейера фабрики — **Секцию XII («Журнал, память, санитария» / Log scope, durable event log retention, institutional settings, poka-yoke advice guards, role capabilities and audit pipeline)**:
   - **Контур XII.1. Область лога и изолированные буферы (`LogScope`, `ScopedBufferAppender`, `LogScopeBuffer`):**
     * Демаркация контекстов по Фон Вригту и Разу (`GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` / D006): строгое разделение `PROJECT:{id}` и `SYSTEM`;
     * Строгое отсечение заводского шума и логов уровня DEBUG/TRACE от проектных буферов;
     * Защита от переполнения памяти циклическим буферированием (`LogScopeBuffer`).
   - **Контур XII.2. Долговечный журнал проекта и удержание памяти (`ProjectLogFlushQueue`, `ProjectEventLogService`, `ProjectEventLogRetentionService`):**
     * Каузальная цепочка надёжности Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010): очистка журнала retention удаляет строго по смыслу и жизненному циклу проекта, а не по голому возрасту;
     * Активные и замороженные проекты сохраняют историю в пределах `per-project ceiling`; принятые (`accepted`) проекты подлежат очистке только по истечении льготного периода (`grace period`);
     * Безопасное дренирование очереди сброса (`ProjectLogFlushQueue`) при выключенном долговечном логе без блокировки потоков и без риска OOM.
   - **Контур XII.3. Институциональный факт-регистр настроек (`SystemSettingsService`):**
     * Институциональный факт-регистр Серла (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007): значение настройки принимается только для зарегистрированных ключей с валидацией формы;
     * Любая мутация настроек оставляет обязательный неизгладимый институциональный след в `defect_journal` (`SYSTEM_SETTING_MUTATION_RULE`) с маскированием секретов.
   - **Контур XII.4. Покэ-йокэ наблюдающие предохранители простоя и ролей (`IdleProjectAdviceService`, `RoleAdviceLoopService`):**
     * Телеосемантическая обратная связь Дрецке (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011): честное снятие претензии на генеративный совет;
     * Механизмы формализованы как наблюдающие предохранители (poka-yoke) со строгим подавлением паразитных и спекулятивных вишлистов/задач в обход суточного цикла фальсификации;
     * Возврат строго типизированных вердиктов (`IdleGuardVerdict`, `RoleAdviceGuardVerdict`). Единственный легитимный источник новой продуктовой работы — суточный цикл фальсификации (`FalsificationCycleService`).
   - **Контур XII.5. Уставы ролей, канонические capabilities, дерево и конвейер аудита (`RoleCapabilityLoader`, `RoleRulesParser`, `JulesRoleCapabilities`, `TaskTitleBuilder`, `ProjectTreeService`, `ProjectAuditPipelineService`):**
     * Ригидный API-референт Эванса (`GARET_EVANS_01_RIGID_API_REFERENT` / D003): централизованная нормализация заголовков задач из 2–3 слов (`TaskTitleBuilder`);
     * Сохранение сырого Markdown-текста устава ролей наряду с распарсенными полями и инвалидация кэша по mtime (`RoleCapabilityLoader`);
     * Супервентность Перла (`DZHUDA_PERL_13_SUPERVENIENCE_WATCH` / D013): `ProjectTreeService` является строго read-only проекцией существующих фактов без выдумывания вторичной истины;
     * Конвейер проектного аудита (`ProjectAuditPipelineService`) ограничен реально верифицирующими стадиями (`COVERAGE_AUDIT`, `STITCH_DESIGN`) без холостых переключений.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции XII уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D011, D013, D003)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны изоляции контекста (`LogScopeAndDefectJournalInusFalsificationTest`, `ScopedBufferAppenderFalsificationTest`) обязаны опровергать ложноположительные суждения; попадание системного шума в проектный буфер или стирание незавершенных проектов признается дефектом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная надежность: строгое разделение уровней системного события, проектного лога, институциональной настройки и узла дерева;
  * Долговечный журнал сохраняет неискаженный след событий; удаление по retention опирается на статус проекта, grace-период и лимит строк на проект.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `GEORG_HENRIK_FON_VRIGT_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz / von Wright]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции XII при проходящих тестах;**
  * Программные деонтические запреты: запрет заводского шума в проектных буферах; запрет записи незарегистрированных ключей настроек; запрет порождения спекулятивной работы из простоя.
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` [D007 Status falsification, Searle]:**
  * Институциональный факт-регистр: настройка фабрики становится легитимным фактом только через объявленное определение в `DEFINITIONS` и обязательный аудит мутации в `defect_journal`.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Noise amplification, Dretske]:**
  * Телеосемантическая обратная связь: совет годен только если предотвращает ошибку или меняет действие; покэ-йокэ предохранители `IdleProjectAdviceService` и `RoleAdviceLoopService` отсекают паразитный шум и спекулятивные вишлисты.
- **`DZHUDA_PERL_13_SUPERVENIENCE_WATCH` [D013 Runtime drift, Pearl]:**
  * Супервентность: верхнеуровневый срез дерева `ProjectTreeService` полностью опирается на нижние факты базы данных (readiness, tasks, sessions, wishlist) и не имеет собственного состояния.
- **`GARET_EVANS_01_RIGID_API_REFERENT` [D003 Contract drift, Evans]:**
  * Ригидный референт: публичные контракты и теги ролей жестко привязаны к каноническим capabilities; внешние заголовки нормализованы в единой точке `TaskTitleBuilder`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XII в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,ScopedBufferAppenderFalsificationTest,ProjectEventLogRetentionServiceTest,SystemSettingsServiceTest,IdleProjectAdviceServiceTest,RoleAdviceLoopServiceTest,RoleAdviceLoopServiceIntegrationTest,RoleCapabilityLoaderTest,ProjectAuditPipelineServiceTest`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции XII (64 теста):
   - Буфер логов и MDC: `LogScopeBufferTest` (6/6);
   - Изоляция контекста логов и дефектного журнала: `LogScopeAndDefectJournalInusFalsificationTest` (5/5);
   - Аппендер изолированного буфера: `ScopedBufferAppenderFalsificationTest` (13/13);
   - Удержание и очистка долговечного журнала: `ProjectEventLogRetentionServiceTest` (10/10);
   - Институциональный факт-регистр настроек: `SystemSettingsServiceTest` (13/13);
   - Предохранитель простоя проекта: `IdleProjectAdviceServiceTest` (6/6);
   - Предохранитель завершения задач: `RoleAdviceLoopServiceTest` (2/2);
   - Интеграция предохранителя ролей: `RoleAdviceLoopServiceIntegrationTest` (1/1);
   - Загрузка уставов ролей: `RoleCapabilityLoaderTest` (4/4);
   - Конвейер аудита проекта: `ProjectAuditPipelineServiceTest` (4/4).
3. Добавить канонический блок `### Итог Секции XII` в `docs/FACTORY_MECHANISMS.md` (подтверждающий Ступень 4 closed, ideal, 64/64 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
