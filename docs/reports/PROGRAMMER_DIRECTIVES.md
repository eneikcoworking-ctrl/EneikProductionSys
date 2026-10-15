# Пакетная директива L1/L3 -> L2: Секция IX (Внешние системы: AI/RAG/Runtime-граница)

**Дата:** 2026-10-15 17:30 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция V («Свидетельство доставки») полностью подтверждена и закрыта инженером (134/134 green в коммите `2a26f2f`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию IX («Внешние системы: внешняя AI/RAG/Runtime-граница фабрики»)**:
   - **Контур IX.1. Типобезопасная граница Google AI & Vector RAG (Goldman D010, Varzi D006):**
     * `GoogleAiResourceService` & `GoogleAiResourceController` — типизированный фасад Google AI, ресурсная матрица, probeModels, grounded/URL research, fail-closed защита, key-gate (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * `GeminiContextService` & `EmbeddingSimilarityUtil` & `ContextChunkRepository` — RAG постоянного знания, чистое косинусное сходство, фильтрация размерности векторов, двухфазная выборка (сначала скоуп векторов, затем `findAllById` контента), запрет загрузки всего корпуса при ошибке эмбеддинга, запрет восстановления `GeminiContextCacheManager` (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
   - **Контур IX.2. Генерация артефактов и UI-дизайн MCP (Popper D008, Goldman D010):**
     * `StitchClient` — JSON-RPC MCP контракт Stitch, сохранение `screenId` при незавершенном HTML, edit/get/download, изоляция контекстов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * `DesignAssetService` & `VideoAssetService` — оркестрация Stitch/Nano Banana, аудит токенов, отсечение офф-токен экранов, категорический запрет деградации до картинок при `requireImplementableHtml=true`, коммит драфтов в GitHub, сохранение метаданных видео даже для `no_video` (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
   - **Контур IX.3. Сайдкар рантайма и операционная власть наблюдателя (Popper D008, Raz D006):**
     * `RuntimeLauncherClient` — строгая демаркация сбоя инструмента (`observed=false`) от сбоя продукта клиента (`answered=false`), изоляция управления контейнерами (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * `GeminiObserverActionService` & `InternalGeminiObserverController` — власть без кода: авторизация через `OperationalPolicyService`, запись аудита в `GeminiObserverActionEntity` (`success/skipped/failed/denied`), категорический деонтический запрет прямого кода и git-коммитов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции IX уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация ложной работоспособности: демаркация сбоя измерительного прибора (`observed=false`) от дефекта продукта клиента; отсечение невалидных артефактов дизайна и запрет деградации до картинок при обязательном HTML.
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная цепочка надежности внешних источников данных: каждый внешний факт входит через известную typed-границу с фиксацией происхождения;
  * Строгий лок уровней абстракции: запрет смешивать raw corpus, vector projection, selected chunk content, asset path и runtime observation.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции IX при проходящих тестах;**
  * Деонтический запрет наблюдателю на прямое вмешательство в репозитории: операционные действия строго ограничены белым списком и авторизуются через `OperationalPolicyService` без кодовых правок.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Boundary Topology, Varzi]:**
  * Единая точка знания о внешнем AI/RAG/Runtime сайдкаре без размытия по фабрике.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Анти-зеркало рантайма: эмпирическая проверка реального ответа сторонних провайдеров и сайдкаров вместо синтетических догадок.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции IX в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции IX (142 теста):
   - `GoogleAiResourceSecurityIntegrationTest`, `GoogleAiResourceControllerTest` (18 тестов);
   - `GeminiContextServiceTest` (28 тестов);
   - `StitchClientTest` (10 тестов);
   - `DesignAssetServiceTest` (12 тестов);
   - `ClientRuntimeObservabilityServiceTest`, `ObservationHostingDemarcationLaw26Test` (32 теста);
   - `GeminiObserverActionServiceTest`, `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest` (42 теста).
3. Зафиксировать форму образца и актуализировать статус Секции IX в `docs/FACTORY_MECHANISMS.md` (добавить `### Итог Секции IX`, Ступень 4 closed, ideal, 142/142 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
