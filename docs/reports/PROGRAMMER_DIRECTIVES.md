# Пакетная директива L1/L3 -> L2: Секции XXVI, XXVII и XXVIII («Служба контекста», «Операционная правда» и «Внешняя AI-граница фабрики: отказ от Gemini»)

**Дата:** 2026-10-15 20:45 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XXIV и XXV («Скрипты оператора и RAG-корпус», «Позвоночник потока и Свод состояния», 169/169 green в коммите `9d1c094`) полностью заверены и закрыты инженером. Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секции XXVI, XXVII и XXVIII («Служба контекста: чем определяется, что исполнитель прочтёт», «Операционная правда: чем фабрика оценивает доверие к себе» и «Отказ от Gemini: что подлежит переносу / внешняя AI-граница фабрики»)**:
   - **XXVI. Служба контекста: чем определяется, что исполнитель прочтёт:**
     * `GeminiContextService` (RAG-граундинг капсулы Чёрча `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, динамический порог близости `dynamicSimilarityFloor` / D010, изоляция размерностей векторов и отсечение рассогласованных эмбеддингов, стратификация источников продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES`, ограничение fallback-контекста, заслон `GeminiContextServiceTest`);
     * `InternalGeminiObserverController` (деонтические заслоны доступа к диагностическим эндпоинтам, авторизация по API-ключу `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, топологические границы Varzi D006, безопасная выборка аккаунтов и сессий без full scan и без падений, заслоны `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`);
     * `ContextChunkRepository` (хранилище контекстных чанков с контролем хеша SHA-256 и каузальной цепи происхождения Goldman D010).
   - **XXVII. Операционная правда: чем фабрика оценивает доверие к себе:**
     * `OperationalTruthService` (каузальная надежность происхождения свидетельств `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`: медленный ступенчатый рост доверия от подтвержденных свидетельств 1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0 и мгновенный сброс при подтвержденном отказе; 4-значная логика истинности Белнапа/Тарского `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012: строгое разделение `undetermined != blocked != trusted`, скользящее окно свежести `TRUST_RECENCY_WINDOW` 30 дней, изоляция не-дефектных аудитов от снижения доверия, заслон `OperationalTruthServiceTest`);
     * `OperationalTruthController` (строгий read-only endpoint `/api/projects/{projectId}/operational-truth`, режим `observe_only` без мутации доменных сущностей);
     * `OperationalTruthDto` (канонический DTO-пакет операционных фактов проекта);
     * `TrustSnapshotService` (персистенция снимков доверия с защитой от искажения телеметрии `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, заслон `TrustSnapshotServiceTest`);
     * `ClientDeliverableReadinessService` (фальсификационный гейт готовности deliverables для заказчика `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, заслоны `ClientDeliverableReadinessServiceTest`, `ClientDeliverableReadinessServiceFalsificationTest`).
   - **XXVIII. Отказ от Gemini: что подлежит переносу (внешняя AI/RAG/Runtime-граница фабрики):**
     * `GoogleAiResourceService` и `GoogleAiResourceController` (деонтическая матрица прав и обязанностей `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, защита мутирующих поверхностей 401/403, изоляция Nano Banana fallback строго при `requireImplementableHtml=false`, категорический запрет подмены HTML картинками `LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009, заслоны `GoogleAiResourceControllerTest`, `GoogleAiResourceSecurityIntegrationTest`);
     * `StitchClient` (JSON-RPC клиент к Stitch MCP, разделение интерфейсов и изоляция от Gemini-рантайма, заслон `StitchClientTest`);
     * `MLPredictionServiceClient` (надежная Java-дверь к FastAPI ML-сайдкару с fail-safe деградацией при сбоях вложений `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, заслон `MLPredictionServiceClientTest`);
     * Внешние сайдкары: `src/models/ml/PredictionService.py` (FastAPI ML-сайдкар) и `judgment-proxy/server.js` (прокси вердиктов) — четкая граница владения;
     * Декомиссионирование Gemini-наблюдателя: `V58AndV111ObserverDecommissionFalsificationTest` (V58/V111 инертные stubs, гарантия отсутствия фонового Gemini polling).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XXVI, XXVII и XXVIII находятся в стабильной рабочей форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper / Tarski]:**
  * Фальсификация фиктивных прогонов и ложных статусов готовности: `OperationalTruthServiceTest` фальсифицирует выдачу доверия при 0 подтвержденных свидетельств; `V58AndV111ObserverDecommissionFalsificationTest` фальсифицирует случайные реинкарнации Gemini-наблюдателя.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность происхождения свидетельств: каждый факт операционной правды обязан иметь источник, свежесть и путь проверки; лок уровней абстракции между доставкой, задачей, PR, заслоном и дефектом.
- **`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Goldman]:**
  * Асимметрия доверия: полномочия и доверие растут медленно и пакетно по накопленным свидетельствам, а падают мгновенно при первом подтвержденном отказе или блокировке.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов и контроллеров при проходящих тестах;**
  * Деонтические запреты рантайма: защита мутирующих эндпоинтов Google AI и Observer от неавторизованного доступа (401/403).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Belnap / Tarski]:**
  * Четырехзначная логика истинности в `OperationalTruthService`: проект без положительных свидетельств переводится в честный `undetermined`, а не в ложный `trusted` или деструктивный `blocked`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Wittgenstein]:**
  * Живая телеметрия рантайма и статус базы данных превыше словесных нарративов; наблюдающий эндпоинт операционной правды (`mode="observe_only"`) не создает артефактов и не искажает состояние.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D014 RAG hallucination, Church]:**
  * Граундинг RAG-выборки: каждый возвращенный чанк обязан цитировать `sourceRef`, оценку сходства и класс дефекта; отсечка близости рассчитывается динамически по распределению текущего запроса.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution failure, Floridi]:**
  * Запрет подмены артефактов: генераторы изображений (Nano Banana) не могут подменять генераторы верстки при требовании HTML.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXVI, XXVII и XXVIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. Зафиксировать результаты в `*Живое:*` блоков Секций XXVI, XXVII и XXVIII в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секции XXVI, XXVII и XXVIII`, обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(context-truth-and-ai-boundary): complete Stage 4 batch verification (D008, D010, D006)`.
