# Директивы и наряд для L2 (2026-10-16 13:52 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты оператора, корпус философских паттернов и Позвоночник потока») полностью заверены инженером (коммит `0519450`, 169/169 green, 0 строк изменений рабочего кода). Ранее Секции XXII и XXIII (`4df7a4a`), Секции XVI и XXIX (`7ffaf95`), Секции V и VI (`1dcdf20`), Секция VIII (`3cd46d8`), Секция VII (TOC), Секции III–IV и Секции I–II также закрыты со 100% green.
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Секций XXVI («Служба контекста: чем определяется, что исполнитель прочтёт»), XXVII («Операционная правда: чем фабрика оценивает доверие к себе») и XXVIII («Внешняя AI-граница фабрики: отказ от Gemini»)** целиком (17 тест-сьютов, 215 тестов в едином Docker Maven прогоне):
     * **Группа 1: Секция XXVI — Служба контекста и диагностика наблюдателя (80 тестов в Docker Maven, 5 сьютов):**
       - `GeminiContextServiceTest` (28/28): динамический расчет порога сходства `dynamicSimilarityFloor`, изоляция по dimension/sourceRef, предотвращение утечки фабричного контекста исполнителям (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9): деонтическая авторизация диагностических эндпоинтов наблюдателя, доступ только через localhost/internal-token, строгая проекция DTO без утечки секретов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10): фальсификационная защита от коллизий хешей чанков и конфликтов областей файлов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
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
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D009, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационный гейт готовности клиентских артефактов `ClientDeliverableReadinessServiceFalsificationTest`, проверка коллизий хешей чанков `ContextChunkHashAndFileScopeConflictFalsificationTest`, декоммиссия наблюдателя V58/V111.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная надежность происхождения: асимметричная динамика доверия от накопленных свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`), изоляция институционального аудита от штрафов доверия, защита от подмены векторов FastEmbed.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая матрица прав и обязанностей доступа к AI-ресурсам (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`), отказ 401/403, аудит вызывающего через `AuditCallerResolver`.
- **`LUCHANO_FLORIDI_01_SUBSTITUTION_ORACLE` [D009 Substitution failure / Floridi]:**
  * Оракул подмены: категорический запрет подмены HTML картинками Nano Banana при требовании имплементируемого HTML, сохранение идентичности экранов Stitch (`screenId`, `getScreen`, in-place edit).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Четырехзначная логика истинности Белнапа/Тарского (`undetermined != blocked != trusted`) в `OperationalTruthService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `OperationalTruthService`: чистый `observe_only` режим DTO без побочных эффектов мутации базы данных.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Semantic ungrounding / Church]:**
  * Заземление контекста RAG в `GeminiContextService`: строгая изоляция по `sourceRef`, динамический порог сходства `dynamicSimilarityFloor`, предотвращение утечек фабричного контекста.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех 17 тест-сьютов Секций XXVI, XXVII и XXVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=GeminiContextServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ContextChunkHashAndFileScopeConflictFalsificationTest,GeminiObserverActionServiceTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest
   ```
2. Подтвердить строго **215/215 green** (17 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
