# Директивы и наряд для L2 (2026-10-16 19:48 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты и философский корпус RAG; Позвоночник потока, статус системы и сторож ТОС») полностью заверены инженером (коммит `d3832813`, 169/169 green: 149 в Docker Maven и 20 в Python-рантайме, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXVI, XXVII и XXVIII («Служба контекста: чем определяется, что исполнитель прочтёт», «Операционная правда: чем фабрика оценивает доверие к себе» и «Внешняя AI-граница фабрики: отказ от Gemini»)** целиком (**215 тестов в 17 тест-сьютах единого пакетного прогона в Docker Maven**):
     * **Группа 1: Служба контекста и граундинг RAG (Секция XXVI — 80 тестов, 5 сьютов):**
       - `GeminiContextServiceTest` (28/28 green): RAG-граундинг капсулы Чёрча, расчет динамического порога сходства `dynamicSimilarityFloor`, изоляция векторных проекций без подъема полных сущностей из базы, стратификация продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES` (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `InternalGeminiObserverControllerTest` (16/16 green), `InternalGeminiObserverSecurityIntegrationTest` (9/9 green): деонтические заслоны доступа к диагностическим и наблюдаемым поверхностям по API-ключу (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green): заслоны хеширования кусков контекста и изоляции конфликтов файловых областей (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GeminiObserverActionServiceTest` (17/17 green): детерминированные действия наблюдателя без паразитных побочных эффектов записи (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 2: Операционная правда и готовность артефактов (Секция XXVII — 81 тест, 4 сьюта):**
       - `OperationalTruthServiceTest` (17/17 green): асимметричная динамика доверия `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` (медленный ступенчатый рост доверия от подтвержденных свидетельств 1..4 -> 0.50, 5..9 -> 0.65, 10..14 -> 0.75, 15..19 -> 0.85, >=20 -> 1.0 и мгновенный сброс при подтвержденном отказе), 4-значная логика истинности Белнапа/Тарского D012 (`undetermined != blocked != trusted`), скользящее окно свежести 30 дней, чистый `observe_only` режим DTO (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TrustSnapshotServiceTest` (9/9 green): надежная фиксация и персистентность снимков доверия по расписанию (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ClientDeliverableReadinessServiceTest` (49/49 green), `ClientDeliverableReadinessServiceFalsificationTest` (6/6 green): строгий фальсификационный гейт готовности клиентских артефактов, исключение подмены клиентской ценности внутренними задачами (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 3: Внешняя AI-граница фабрики и отказ от Gemini (Секция XXVIII — 54 теста, 8 сьютов):**
       - `GoogleAiResourceControllerTest` (6/6 green), `GoogleAiResourceSecurityIntegrationTest` (12/12 green): деонтическая матрица прав и обязанностей доступа к ресурсам AI (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, отказ 401/403, аудит вызывающего `AuditCallerResolver`), категорический запрет подмены HTML картинками Nano Banana (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009);
       - `StitchClientTest` (10/10 green): сохранение идентичности экранов Stitch (`screenId`, `getScreen`, in-place edit) без деградации контракта (`LUCHANO_FLORIDI_19_BOUNDARY_TOPOLOGY` / D006, `LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009);
       - `MLPredictionServiceClientTest` (9/9 green): fail-safe Java-клиент сайдкара FastEmbed с предохранителем (circuit breaker) и защитой от фиктивных векторов при сбоях (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6 green), `JudgmentAgentClientLaw17Test` (4/4 green): демаркация фабричного, продуктового и поставочного уровней суждения, структурное бюджетирование канала промпта и закон 17 Грайса (`LUCHANO_FLORIDI_02_CONSTRUCTIVE_PROOF_OBJECT` / D007, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `GeminiProjectObserverServiceTest` (1/1 green), `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green): полная декоммиссия наблюдателя Gemini (инертность фоновых циклов, V58/V111 stubs) (`DZHOZEF_RAZ_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D014, D013, D009, D012, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны коллизий контекста (`ContextChunkHashAndFileScopeConflictFalsificationTest`), готовности клиентских артефактов (`ClientDeliverableReadinessServiceFalsificationTest`) и нерушимости вывода наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции и надежная цепь вывода: изоляция векторных проекций в `GeminiContextService` без загрузки полных сущностей из базы, асимметричная динамика доверия в `OperationalTruthService` (рост строго по накоплению доказательств, немедленный сброс при отказе), защита происхождения ML-векторов без фиктивных mock-векторов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` & `DZHOZEF_RAZ_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов ресурсов AI (`GoogleAiResourceSecurityIntegrationTest`) и внутреннего входа наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), декоммиссия наблюдателя Gemini.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` & `LUCHANO_FLORIDI_19_BOUNDARY_TOPOLOGY` [D009 Substitution failure / D006 Boundary topology / Floridi]:**
  * Запрет подмены HTML картинками Nano Banana; сохранение непрерывности артефактов экранов Stitch (`screenId`, `getScreen`, in-place edit).
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Church]:**
  * RAG-граундинг капсулы Чёрча: цитирование извлеченных кусков с `sourceRef`, сходством и классом дефекта, динамический расчет порога `dynamicSimilarityFloor`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: чистый `observe_only` режим операционной правды и идемпотентность действий наблюдателя без побочных эффектов в базе.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности Белнапа/Тарского (`undetermined != blocked != trusted`).

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XXVI, XXVII и XXVIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. Подтвердить инвариант Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Зафиксировать такт семантическим коммитом: `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
