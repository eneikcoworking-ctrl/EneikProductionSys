# Пакетная директива L1/L3 -> L2: Секции XXVI, XXVII и XXVIII — Служба контекста, Операционная правда и Внешняя AI-граница фабрики (Отказ от Gemini)

**Дата:** 2026-10-16 05:16 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XXIV и XXV («Скрипты ручного запуска, Позвоночник потока, Ядро исполнения и Свод состояния», 169/169 green в коммите `e508dd6`) полностью заверены и закрыты инженером без единой строки правок рабочего кода. Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секции XXVI («Служба контекста»), XXVII («Операционная правда») и XXVIII («Отказ от Gemini: внешняя AI-граница фабрики»)**:
   - **XXVI. Служба контекста: чем определяется, что исполнитель прочтёт:**
     * `GeminiContextService` (RAG-граундинг капсулы Чёрча D014, динамический порог близости `dynamicSimilarityFloor`, векторные проекции исключают утечки контекста фабрики и подъём полных сущностей из базы, изоляция несовпадающих размерностей векторов, стратификация продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES`, заслоны `GeminiContextServiceTest`, `ContextChunkHashAndFileScopeConflictFalsificationTest`);
     * `InternalGeminiObserverController` и `GeminiObserverActionService` (деонтические заслоны доступа к диагностическим эндпоинтам по API-ключу `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, топологические границы Varzi D006, заслоны `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`, `GeminiObserverActionServiceTest`).
   - **XXVII. Операционная правда: чем фабрика оценивает доверие к себе:**
     * `OperationalTruthService` и `OperationalTruthController` (каузальная надежность происхождения свидетельств Goldman D010, асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`: медленный ступенчатый рост доверия от подтвержденных свидетельств 1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0 и мгновенный сброс при подтвержденном отказе; 4-значная логика истинности Белнапа/Тарского D012 `undetermined != blocked != trusted`; скользящее окно свежести `TRUST_RECENCY_WINDOW` 30 дней, чистый `observe_only` режим DTO без побочных эффектов в базе данных `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, заслоны `OperationalTruthServiceTest`, `TrustSnapshotServiceTest`);
     * `ClientDeliverableReadinessService` (строгий фальсификационный гейт готовности клиентских артефактов `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, заслоны `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`).
   - **XXVIII. Отказ от Gemini: что подлежит переносу (внешняя AI/RAG/Runtime-граница фабрики):**
     * `GoogleAiResourceService` и `GoogleAiResourceController` (деонтическая матрица прав и обязанностей `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, защита мутирующих поверхностей 401/403, аудит вызывающего `AuditCallerResolver`, изоляция Nano Banana fallback строго при `requireImplementableHtml=false`, категорический запрет подмены HTML картинками `LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009, заслоны `GoogleAiResourceControllerTest`, `GoogleAiResourceSecurityIntegrationTest`);
     * `StitchClient` (JSON-RPC клиент к Stitch MCP, сохранение идентичности экранов `screenId`, отложенный синтез HTML `getScreen`, in-place редактирование без дрейфа артефактов, заслон `StitchClientTest`);
     * `MLPredictionServiceClient` (надежная Java-дверь к FastAPI ML-сайдкару FastEmbed с предохранителем breaker и защитой от нулевых/фиктивных векторов при сбоях `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, заслон `MLPredictionServiceClientTest`);
     * `JudgmentAgentClient` (разделение фабричного, продуктового и поставочного уровней суждения, закон 17, заслоны `JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`);
     * Декомиссионирование Gemini-наблюдателя (`GeminiProjectObserverService` инертный stub, V58/V111 stubs, гарантия отсутствия фонового Gemini polling, заслоны `GeminiProjectObserverServiceTest`, `V58AndV111ObserverDecommissionFalsificationTest`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XXVI, XXVII и XXVIII находятся в стабильной рабочей форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper / Tarski]:**
  * Фальсификация фиктивных прогонов и ложных статусов готовности: `OperationalTruthServiceTest` фальсифицирует выдачу доверия при 0 подтвержденных свидетельств; `V58AndV111ObserverDecommissionFalsificationTest` фальсифицирует случайные реинкарнации Gemini-наблюдателя; `ClientDeliverableReadinessServiceFalsificationTest` роняет приемку при неполных артефактах.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность происхождения свидетельств: каждый факт операционной правды обязан иметь источник, свежесть и путь проверки; лок уровней абстракции между доставкой, задачей, PR, заслоном и дефектом.
- **`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Goldman]:**
  * Асимметрия доверия: полномочия и доверие растут медленно и пакетно по накопленным свидетельствам (1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0), а падают мгновенно при первом подтвержденном отказе или блокировке.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров при проходящих тестах;**
  * Деонтические запреты рантайма: защита мутирующих эндпоинтов Google AI и Observer от неавторизованного доступа (401/403).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap]:**
  * Четырехзначная логика истинности в `OperationalTruthService`: проект без положительных свидетельств переводится в честный `undetermined`, а не в ложный `trusted` или деструктивный `blocked`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Wittgenstein]:**
  * Физическое состояние рантайма и статус Actuator UP превыше словесных нарративов; наблюдающий эндпоинт операционной правды (`mode="observe_only"`) не создает мутаций в базе данных.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D014 RAG hallucination, Church]:**
  * Граундинг RAG-выборки: каждый возвращенный чанк обязан цитировать `sourceRef`, оценку сходства и класс дефекта; отсечка близости рассчитывается динамически по распределению текущего запроса.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution failure, Floridi]:**
  * Запрет подмены артефактов: генераторы изображений (Nano Banana) не могут подменять генераторы верстки при требовании HTML.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXVI, XXVII и XXVIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XXVI, XXVII и XXVIII), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(context-truth-and-ai-boundary): complete Stage 4 batch verification (D008, D010, D006)`.
