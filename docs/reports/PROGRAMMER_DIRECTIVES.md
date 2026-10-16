# Директивы и наряд для L2 (2026-10-16 21:33 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты оператора, RAG-корпус, Позвоночник потока, статус системы, защищенные настройки и адаптивный сторож ТОС») полностью заверены инженером (коммит `4356b770`, 174/174 green: 154 в Docker Maven + 20 в Python-рантайме, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXVI, XXVII и XXVIII («Служба контекста: чем определяется, что исполнитель прочтёт; Операционная правда: чем фабрика оценивает доверие к себе; и Внешняя AI-граница: отказ от Gemini»)** целиком (**215 тестов в едином пакетном прогоне в Docker Maven [17 сьютов]**):
     * **Группа 1: Служба контекста и семантический RAG (Секция XXVI — 38 тестов):**
       - `GeminiContextServiceTest` (28/28 green): динамический порог схожести (`dynamicSimilarityFloor`), векторная выборка без выгрузки всего корпуса в память JVM (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
       - `ContextChunkHashAndFileScopeConflictFalsificationTest` (10/10 green): заслон на коллизии хэшей кусков и разграничение областей видимости файлов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Операционная правда и готовность поставки (Секция XXVII — 81 тест):**
       - `OperationalTruthServiceTest` (17/17 green): асимметричная динамика доверия: доверие начинается с 0.0/неустановленного и растет строго по подтвержденным свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `TrustSnapshotServiceTest` (9/9 green): сохранение снимков доверия с отложенным дозаполнением фактических исходов сдачи (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ClientDeliverableReadinessServiceTest` (49/49 green), `ClientDeliverableReadinessServiceFalsificationTest` (6/6 green): критерии готовности deliverables заказчику, строгая демаркация выполненных задач и подтвержденной ценности (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 3: Внешняя AI-граница и вывод Gemini (Секция XXVIII — 96 тестов):**
       - `GoogleAiResourceControllerTest` (6/6 green), `GoogleAiResourceSecurityIntegrationTest` (12/12 green): деонтическая защита эндпоинтов Google AI, аудит квот и изоляция ресурсов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StitchClientTest` (10/10 green): типобезопасный клиент дизайн-сборки (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `MLPredictionServiceClientTest` (9/9 green): интеграция с Python-сайдкаром предсказаний (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `JudgmentAgentClientTest` (6/6 green), `JudgmentAgentClientLaw17Test` (4/4 green): судейский прокси с соблюдением постулатов Грайса (Закон 17) (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
       - `GeminiProjectObserverServiceTest` (1/1 green), `V58AndV111ObserverDecommissionFalsificationTest` (6/6 green): заслоны окончательного вывода устаревшего Gemini Project Observer (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `InternalGeminiObserverControllerTest` (16/16 green), `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `GeminiObserverActionServiceTest` (17/17 green): защита внутренних путей наблюдателя и институциональный аудит действий (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D014, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны на коллизии контекста и скоупов файлов (`ContextChunkHashAndFileScopeConflictFalsificationTest`), готовности поставки (`ClientDeliverableReadinessServiceFalsificationTest`) и вывода устаревшего наблюдателя (`V58AndV111ObserverDecommissionFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции векторного RAG-корпуса (запрет подъема полного текста кусков до отбора); асимметричная динамика доверия (старт с 0.0/неустановленного и приращение строго по свидетельствам).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон изменяющих путей Google AI (`GoogleAiResourceSecurityIntegrationTest`) и внутренних путей наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: эмпирические факты рантайма и базы выше словесных отчетов; разделение выполненной работы и принятой ценности (`done_is_not_delivery`).
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding gap / Church]:**
  * 100% заземление RAG-контекста: динамический порог схожести и проекции исключают галлюцинации и утечки памяти.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначная решётка истинности статусов готовности, доверия и исходов вердиктов.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXVI, XXVII и XXVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GeminiContextServiceTest,ContextChunkHashAndFileScopeConflictFalsificationTest,OperationalTruthServiceTest,TrustSnapshotServiceTest,ClientDeliverableReadinessServiceTest,ClientDeliverableReadinessServiceFalsificationTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,StitchClientTest,MLPredictionServiceClientTest,JudgmentAgentClientTest,JudgmentAgentClientLaw17Test,GeminiProjectObserverServiceTest,V58AndV111ObserverDecommissionFalsificationTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,GeminiObserverActionServiceTest
   ```
2. Подтвердить 100% green результат (215/215 green в 17 тест-сьютах).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xxvi-xxviii): complete Stage 4 batch verification (D008, D010, D006)`.
