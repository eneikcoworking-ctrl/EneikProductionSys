# Директивы и наряд для L2 (2026-10-16 17:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты и Позвоночник потока») полностью заверены инженером (коммит `c8b89c2`, 169/169 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и Внешняя AI-граница фабрики: отказ от Gemini»)** целиком (215 тестов в едином непрерывном пакетном прогоне 17 тест-сьютов в Docker Maven):
     * **Группа 1: Секция XXVI — Служба контекста (80 тестов в Docker Maven, 5 сьютов):**
       - `GeminiContextServiceTest` (28/28): извлечение релевантного контекста ролей и философских образцов, динамический порог близости `dynamicSimilarityFloor`, изоляция продуктовых исполнителей `PRODUCT_WORKER_SOURCE_TYPES` (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
       - `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9): деонтическая авторизация диагностических эндпоинтов по API-ключу оператора, отказ 401/403, маскирование секретов и строгие границы доступа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10): исключение коллизий хешей контекстных чанков, идемпотентность обновления корпуса (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `GeminiObserverActionServiceTest` (17/17): детерминированная фиксация и изоляция действий наблюдателя, строгий контроль мутаций (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 2: Секция XXVII — Операционная правда и доверие (81 тест в Docker Maven, 4 сьюта):**
       - `OperationalTruthServiceTest` (17/17): ступенчатая асимметричная динамика доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010), четырехзначная истинность Белнапа/Тарского (`undetermined != blocked != trusted` / D012), скользящее окно свежести свидетельств 30 дней, исключение институционального аудита из штрафов доверия, чистый режим `observe_only` без побочных эффектов мутации базы данных (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TrustSnapshotServiceTest` (9/9): персистентность снимков доверия без мутации операционного графа (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ClientDeliverableReadinessServiceTest` (49/49), `ClientDeliverableReadinessServiceFalsificationTest` (6/6): строгий предикативный гейт и фальсификационный заслон готовности клиентских артефактов, запрет ложного рапорта о готовности при отсутствии реальных поставочных свидетельств (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 3: Секция XXVIII — Внешняя AI-граница фабрики: отказ от Gemini (54 теста в Docker Maven, 8 сьютов):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): разграничение прав доступа к AI-ресурсам, деонтический отказ 401/403, институциональный аудит вызывающего через `AuditCallerResolver` (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `StitchClientTest` (10/10): сохранение идентичности экранов Stitch (`screenId`, `getScreen`, in-place edit), контракт генерации имплементируемого HTML (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009);
       - `MLPredictionServiceClientTest` (9/9): fail-safe circuit breaker для сайдкара FastEmbed, защита от нулевых/фиктивных векторов при недоступности сайдкара (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `JudgmentAgentClientTest` (6/6), `JudgmentAgentClientLaw17Test` (4/4): разделение уровней суждения (фабричное, продуктовое, поставочное), закон 17 суждений (`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` / D009, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `GeminiProjectObserverServiceTest` (1/1), `V58AndV111ObserverDecommissionFalsificationTest` (6/6): фальсификационное подтверждение полной декоммиссии наблюдателя Gemini (инертность фоновых циклов, V58/V111 stubs) без нарушения контрактов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D012, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны готовности клиентских артефактов (`ClientDeliverableReadinessServiceFalsificationTest`), декоммиссии наблюдателя Gemini (`V58AndV111ObserverDecommissionFalsificationTest`) и исключения коллизий хешей чанков (`ContextChunkHashAndFileScopeConflictFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции в `ClientDeliverableReadinessService` и `GeminiObserverActionService`.
  * Ступенчатая асимметричная динамика доверия в `OperationalTruthService`: подтверждения накапливаются медленно, дефекты сбрасывают доверие мгновенно.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита эндпоинтов AI-ресурсов (`GoogleAiResourceSecurityIntegrationTest`) и наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`) с институциональным аудитом.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистый режим `observe_only` в `OperationalTruthService` без побочных эффектов записи в базу данных; факты физического мира выше деклараций.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding drift / Church]:**
  * Обоснование контекста и динамический расчет порога сходства `dynamicSimilarityFloor` в `GeminiContextService`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности (`undetermined != blocked != trusted`) в оценке операционной правды.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Floridi]:**
  * Разделение уровней суждения агента и сохранение стабильной идентичности экранов Stitch.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXVI, XXVII и XXVIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest` (215 тестов в 17 сьютах).
2. Зафиксировать результат (215/215 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
