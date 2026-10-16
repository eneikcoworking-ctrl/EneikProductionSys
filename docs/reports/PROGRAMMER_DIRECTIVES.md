# Директивы и наряд для L2 (2026-10-16 11:41 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты оператора и Позвоночник потока») успешно заверены инженером (коммит `6c73570`, 169/169 green: 149 Maven + 20 Python, 1720 философских паттернов верифицировано, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XXVI («Служба контекста: чем определяется, что исполнитель прочтёт»), XXVII («Операционная правда: чем фабрика оценивает доверие к себе») и XXVIII («Внешняя AI-граница фабрики: отказ от Gemini»)** целиком (17 тест-сьютов, 215 тестов в едином пакетном прогоне Docker Maven):
     * **Секция XXVI: Служба контекста (80 тестов, 5 сьютов):**
       - `GeminiContextServiceTest` (28/28): динамический порог близости `dynamicSimilarityFloor`, векторные проекции исключают подъем полного корпуса в память JVM, стратификация продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES` (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9): деонтические заслоны доступа к диагностическим эндпоинтам по API-ключу (401/403), институциональный аудит вызывающего (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10): дедупликация чанков по хешу содержимого, предотвращение коллизий file-scope (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GeminiObserverActionServiceTest` (17/17): типизированные безопасные действия, изоляция от сбоев внешней модели (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Секция XXVII: Операционная правда и доверие (81 тест, 4 сьюта):**
       - `OperationalTruthServiceTest` (17/17), `TrustSnapshotServiceTest` (9/9): асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (медленный ступенчатый рост 1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0; мгновенный сброс при подтвержденном отказе), окно свежести `TRUST_RECENCY_WINDOW` 30 дней, чистый `observe_only` режим DTO без побочных эффектов мутации базы данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `ClientDeliverableReadinessServiceTest` (49/49), `ClientDeliverableReadinessServiceFalsificationTest` (6/6): строгий гейт готовности клиентских артефактов, исключение ложной готовности без подтвержденных свидетельств (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Секция XXVIII: Внешняя AI-граница фабрики — отказ от Gemini (54 теста, 8 сьютов):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтическая матрица прав и обязанностей доступа к ресурсам AI (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006), аудит мутаций `AuditCallerResolver`;
       - `StitchClientTest` (10/10): категорический запрет подмены HTML картинками Nano Banana (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009), сохранение стабильной идентичности экранов Stitch (`screenId`);
       - `MLPredictionServiceClientTest` (9/9): fail-safe Java-клиент ML сайдкара, breaker-предохранитель, защита от сбоев и нулевых/фиктивных векторов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4): разделение фабричного, продуктового и поставочного уровней суждения, соблюдение коммуникативных максим Закона 17 (`LUCHANO_FLORIDI_04_LEVEL_OF_ABSTRACTION_LOCK` / D010, `LUCHANO_FLORIDI_02_CONSTRUCTIVE_PROOF_OBJECT` / D007);
       - `GeminiProjectObserverServiceTest` (1/1), `V58AndV111ObserverDecommissionFalsificationTest` (6/6): полная декоммиссия наблюдателя Gemini, инертность фоновых циклов, V58/V111 stubs (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной/идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при повреждении заслонов готовности клиентских артефактов (`ClientDeliverableReadinessServiceFalsificationTest`), попытках воскрешения декоммиссованного наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`) или коллизиях file-scope (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: разделение фабричного, продуктового и поставочного уровней суждения; асимметричный ступенчатый рост доверия от накопленных подтвержденных свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`) с мгновенным сбросом при доказанном отказе; сохранение источников и размерностей векторов без порождения ложных нулевых проекций.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей доступа к изменяющим ресурсам AI (`GoogleAiResourceSecurityIntegrationTest`) и диагностическим входам наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), исключающая несанкционированные мутации и нецелевой расход квот.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution failure / Floridi]:**
  * Оракул подмены: категорический запрет подмены HTML-макетов картинками Nano Banana при требовании имплементируемого HTML (`requireImplementableHtml=true`); сохранение идентичности артефактов экранов Stitch (`screenId`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * Четырёхзначная логика: явное разграничение состояний доверия (`undetermined != blocked != trusted`) в операционной правде.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Принцип анти-зеркала: замер операционной правды и статуса проекта работает в режиме строгого чтения (`observe_only`), не оставляя мутационных следов в базе данных.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Church]:**
  * Заземление контекста: динамический порог близости `dynamicSimilarityFloor` отсекает несвязанные куски корпуса, исключая галлюцинации и утечки внутренних данных оркестратора в подсказки продуктовых исполнителей.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXVI, XXVII и XXVIII (215 тестов, 17 сьютов в Docker Maven):**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. **Соблюдение инварианта суверенитета и правила Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
   - Фиксация статуса в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. **Отчёт и фиксация:**
   - Обновить `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15–20 строк).
   - Сделать семантический коммит в фабрику: `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
