# Директивы и наряд для L2 (2026-10-17 01:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXIV и XXV («Скрипты: генерация философского корпуса, санитария PR, пришивка ролей; и Позвоночник потока: FlowSpine, операционное ядро, статус системы, защищенные настройки и адаптивный сторож ТОС») успешно заверен инженером (коммит `12e77918`, 174/174 green: 154 теста в 11 тест-сьютах в Docker Maven + 20 тестов в Python-рантайме, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Кластер Секций XXVI, XXVII и XXVIII («Служба контекста: чем определяется, что исполнитель прочтёт; Операционная правда: чем фабрика оценивает доверие к себе; и Внешняя AI-граница: отказ от Gemini»)** целиком (**215 тестов в 17 тест-сьютах в Docker Maven 3.9.9 Temurin-21 (-m 2g)**):
     * **Группа 1: Служба контекста (Секция XXVI — 80 тестов, 5 сьютов):**
       - `GeminiContextServiceTest` (28/28 green): RAG-граундинг капсулы Чёрча D014, динамический порог сходства `dynamicSimilarityFloor`, векторные проекции без подъема полных сущностей из БД;
       - `InternalGeminiObserverControllerTest` (16/16 green) и `InternalGeminiObserverSecurityIntegrationTest` (9/9 green): деонтический барьер и безопасность сервисных эндпоинтов по API-ключу (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green): фальсификационный заслон коллизий контентных хэшей и конфликтов скоупа файлов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GeminiObserverActionServiceTest` (17/17 green): анти-зеркальная телеметрия без побочных мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Операционная правда (Секция XXVII — 81 тест, 4 сьюта):**
       - `OperationalTruthServiceTest` (17/17 green): асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (медленный ступенчатый рост и мгновенный сброс при подтвержденном сбое) и 4-значная решётка истинности Белнапа D012;
       - `TrustSnapshotServiceTest` (9/9 green): скользящее окно свежести 30 дней и чистый `observe_only` DTO режим без побочных эффектов в базе данных (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `ClientDeliverableReadinessServiceTest` (49/49 green), `ClientDeliverableReadinessServiceFalsificationTest` (6/6 green): попперовский фальсификационный гейт готовности клиентских артефактов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 3: Внешняя AI-граница фабрики: отказ от Gemini (Секция XXVIII — 54 теста, 8 сьютов):**
       - `GoogleAiResourceControllerTest` (6/6 green), `GoogleAiResourceSecurityIntegrationTest` (12/12 green): деонтическая матрица прав и обязанностей доступа к AI-ресурсам (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `StitchClientTest` (10/10 green): категорический запрет подмены HTML картинками Nano Banana (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009) и сохранение идентичности экранов Stitch;
       - `MLPredictionServiceClientTest` (9/9 green): fail-safe Java-клиент ML сайдкара с предохранителем (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6 green), `JudgmentAgentClientLaw17Test` (4/4 green): разделение фабричного, продуктового и поставочного уровней суждения;
       - `GeminiProjectObserverServiceTest` (1/1 green), `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green): полная декоммиссия фонового наблюдателя Gemini (инертность фоновых циклов, V58/V111 заглушки).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D009, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны коллизий хэшей и файлов контекста, гейта готовности артефактов и вывода устаревшего Gemini Observer.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепь свидетельств: динамический расчет порога сходства `dynamicSimilarityFloor`, изоляция контекста фабрики от векторных утечек, асимметричный рост доверия строго по подтвержденным свидетельствам (1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей доступа к AI-ресурсам и внутренним эндпоинтам наблюдателя.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистый режим чтения `observe_only` DTO без паразитных мутаций в БД.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding gap / Church]:**
  * 100% заземление RAG-контекста без галлюцинаций.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution failure / Floridi]:**
  * Запрет подмены HTML картинок Nano Banana и сохранение экранных идентификаторов Stitch (`screenId`, `getScreen`, in-place edit).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности статусов доверия и готовности (`undetermined != blocked != trusted`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXVI, XXVII и XXVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. Подтвердить 100% green результат (215/215 green в 17 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xxvi-xxvii-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
