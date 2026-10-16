# Директивы и наряд для L2 (2026-10-16 08:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты оператора и Позвоночник потока») успешно заверены инженером (коммит `463aad9`, 169/169 green: 149 Maven + 20 Python, 1720 паттернов верифицировано, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XXVI («Служба контекста»), XXVII («Операционная правда») и XXVIII («Внешняя AI-граница фабрики: отказ от Gemini»)** целиком (17 тест-сьютов, 215 тестов в едином прогоне Docker Maven):
     * **Секция XXVI: Служба контекста (80 тестов):**
       - `GeminiContextServiceTest` (28/28): динамический порог близости `dynamicSimilarityFloor`, векторные проекции исключают подъем полного корпуса в память JVM, стратификация продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES` (D014 Church, D010 Goldman);
       - `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9): деонтические заслоны доступа к диагностическим эндпоинтам по API-ключу (401/403), институциональный аудит вызывающего (D006 Raz);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10): дедупликация чанков по хешу содержимого, предотвращение коллизий file-scope (D010 Goldman, D008 Popper);
       - `GeminiObserverActionServiceTest` (17/17): типизированные безопасные действия, изоляция от сбоев внешней модели (D006 Raz).
     * **Секция XXVII: Операционная правда и доверие (81 тест):**
       - `OperationalTruthServiceTest` (17/17), `TrustSnapshotServiceTest` (9/9): асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (медленный ступенчатый рост 1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0; мгновенный сброс при подтвержденном отказе), окно свежести `TRUST_RECENCY_WINDOW` 30 дней, чистый `observe_only` режим DTO без побочных эффектов мутации базы данных (D010 Goldman, D012 Belnap, D013 Wittgenstein);
       - `ClientDeliverableReadinessServiceTest` (49/49), `ClientDeliverableReadinessServiceFalsificationTest` (6/6): строгий гейт готовности клиентских артефактов, исключение ложной готовности без подтвержденных свидетельств (D008 Popper, D010 Goldman).
     * **Секция XXVIII: Внешняя AI-граница фабрики — отказ от Gemini (54 теста):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтическая матрица прав и обязанностей доступа к ресурсам AI (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006), аудит мутаций `AuditCallerResolver`;
       - `StitchClientTest` (10/10): категорический запрет подмены HTML картинками Nano Banana (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009), сохранение стабильной идентичности экранов Stitch (`screenId`);
       - `MLPredictionServiceClientTest` (9/9): fail-safe Java-клиент ML сайдкара, защита от сбоев и нулевых/фиктивных векторов (D010 Goldman, D008 Tarski);
       - `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4): разделение фабричного, продуктового и поставочного уровней суждения, соблюдение коммуникативных максим Закона 17 (D007 Goldman, D010 Floridi);
       - `GeminiProjectObserverServiceTest` (1/1), `V58AndV111ObserverDecommissionFalsificationTest` (6/6): полная декоммиссия наблюдателя Gemini, инертность фоновых циклов, V58/V111 stubs (D008 Popper).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D014, D009, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при ложной готовности артефактов (`ClientDeliverableReadinessServiceFalsificationTest`), попытке реактивации выключенного наблюдателя Gemini (`V58AndV111ObserverDecommissionFalsificationTest`) или передаче поврежденных эмбеддингов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и асимметричная динамика доверия: доверие растет медленно и ступенчато только по верифицированным свидетельствам и мгновенно сбрасывается при доказанном сбое; сохранение строгой причинно-следственной цепочки происхождения данных от источника к вердикту.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код в `InternalGeminiObserverController` и `GoogleAiResourceController` (проверка ключа, запрет мутаций без прав, institutional audit).
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Church]:**
  * Заземление контекста: динамический порог близости и векторные проекции исключают утечку нерелевантного или фиктивного контекста в сессии Jules.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Category substitution / Floridi]:**
  * Оракул подстановки: категорический запрет подменять HTML-код продуктового экрана растровой картинкой Nano Banana при сбоях генератора.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * Четырёхзначная логика: явное разграничение состояний доверия (`undetermined != blocked != trusted`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Принцип анти-зеркала: чтение операционной правды не оставляет побочных следов в базе данных (`observe_only`).

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXVI, XXVII и XXVIII:**
   - **Docker Maven (215 тестов, 17 сьютов):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
     ```
2. **Верификация результата:**
   - Строго **215/215 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секций XXVI, XXVII и XXVIII в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секций XXVI, XXVII и XXVIII в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
