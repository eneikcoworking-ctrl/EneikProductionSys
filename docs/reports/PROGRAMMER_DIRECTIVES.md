# Пакетная директива L1/L3 -> L2: Секции XXVIII и XXX–XLIV (Внешние AI/ML шлюзы, сайдкары, пульты управления, сторож ТОС, метрики качества и оптимизаторы)

**Дата:** 2026-10-15 15:18 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XIIа, XIII и XX полностью замерены и закрыты инженером в статусе ideal (коммит `31e6465`, 158/158 green в Docker Maven). Настоящая директива охватывает целиком следующий открытый комплекс фабрики — **Секцию XXVIII (Отказ от Gemini: изоляция внешних AI-провайдеров и доказательство эквивалентности) и Секции XXX–XLIV (Внешние AI/ML клиенты, шлюзы ресурсов, пульт управления, сторож ТОС, метрики качества и оптимизаторы)**:
   - **Секция XXVIII. Отказ от Gemini (Изоляция провайдеров и сохранение доказательств):**
     * XXVIII.1 / XXXVIII `GoogleAiResourceController` & `GoogleAiResourceService` — деонтический запрет мутирующих путей (401/403), институциональный аудит вызывающего (`AuditCallerResolver`), безопасное маскирование секретов и разделение матриц ресурсов;
     * XXVIII.2 `StitchClient` — сохранение идентичности экранов (`screenId`), отложенный синтез HTML, in-place edit без дрейфа и явный отказ (`null`/unavailable);
     * XXVIII.3 `JulesApiClient` — строгая изоляция сессионного жизненного цикла Jules от моделей генерации текста, валидация веток и бюджета;
     * XXVIII.4 / XXX `MLPredictionServiceClient` & `PredictionService.py` — единая дверь к FastEmbed и сайдкару, fail-closed предохранитель (breaker), запрет фиктивных mock-векторов при сбое, телеметрия в `AiHealthTracker`;
     * XXVIII.5 `JudgmentAgentClient` & `judgment-proxy/server.js` — демаркация фабричного, продуктового и поставочного уровней суждения, явное сохранение состояний `ABSTAIN`, `FINDING` и `UNAVAILABLE`;
     * XXVIII.6 / XXXIV `InternalGeminiObserverController` & `GeminiProjectObserverService` — инертность наблюдателя, защита сервисных эндпоинтов ключом авторизации, обработка пагинации без падений в HTTP 500;
     * XXVIII.7 `GeminiContextService` — RAG-выборка по хешу контента и источнику без утечек операционной истории воркерам.
   - **Секции XXX–XLIV. Периферийные шлюзы, пульт, сторож ТОС и оптимизаторы:**
     * XXXI `RepositoryStackAnalyzer` & `OnboardingAuditService` — категоризация ошибок доступа к GitHub отдельно от фактов о репозитории заказчика (D002 Ryle);
     * XXXII `GitHubProjectFactoryClient` & `ProjectFactoryService` — учреждение адреса проекта строго после проверяемого свидетельства GitHub;
     * XXXIII `CommandDashboardService` — пульт управления, запрет объявления готовности без клиентского свидетельства приемки;
     * XXXV `AccountHealthService` & `AccountRepository` — дисциплина выбора аккаунтов, наказание порядком ротации, а не фатальным исключением;
     * XXXVI `TaskRepository` & `InternalTaskController` — абсолютный заслон терминальности задач, постраничная выборка;
     * XXXVII / XLIII `TocSentinelService`, `TocAnomalyDetector`, `TocOptimizer` — непрерывный надзор за графом ограничений ТОС, выявление циклов и дедлоков, ограничитель выпуска (rope) без ложных оптимумов;
     * XXXIX `QualityMetricsController`, `SixSigmaAuditService`, `EmsMetricsService` — разделение счетов дефектов (388 vs 0), каноническая агрегация CTQ без смешения понятий;
     * XL / XLII / XLIV `StrandedFinalizingSweepService`, `GeminiContextCacheManager`, `ProjectEventLogRetentionService` — автономная очистка зависших притязаний по TTL, управление кэшем постоянного корпуса, удержание журнала проекта по емкости (`trimToCeiling`).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов, контроллеров и клиентов при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается грубейшим нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификационные заслоны предмета: проверка реальных функций эндпоинтов вместо поверхностного HTTP 200 на `/docs` или `/actuator/health`;
  * Фальсификация несанкционированного доступа: тесты проверяют возврат 401/403 при запросах к мутирующим путям `GoogleAiResourceController` и `InternalGeminiObserverController` без токена;
  * Опровержение в `TocSentinelServiceTest` и `TocOptimizerTest`: доказательство выявления аномалий и исключения ложно-оптимальных рекомендаций графа.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: строгое разделение внешних ответов LLM, локальных эмбеддингов FastEmbed, DTO планов, графов ТОС и фабричных метрик;
  * Запрет подмены смысловых фактов: при отказе эмбеддинга возвращается `null` с регистрацией в `AiHealthTracker`, а не фиктивный нулевой вектор;
  * Сохранение цепочки свидетельств в `OnboardingAuditService` и `CommandDashboardService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов, контроллеров и клиентов Секций XXVIII и XXX–XLIV;**
  * Деонтический запрет мутаций через внутренние эндпоинты без валидного `X-API-Key` или Bearer токена;
  * Запрет спекулятивной генерации задач и вишлистов в обход канонического цикла фальсификации.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по всем ключевым классам-заслонам Секций XXVIII и XXX–XLIV:
   - `GoogleAiResourceControllerTest`, `GoogleAiResourceSecurityIntegrationTest`, `StitchClientTest`, `JulesApiClientTest`;
   - `MLPredictionServiceClientTest`, `JudgmentAgentClientTest`, `JudgmentAgentClientLaw17Test`;
   - `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`;
   - `RepositoryStackAnalyzerTest`, `GitHubProjectFactoryClientTest`, `CommandDashboardServiceTest`, `AccountHealthServiceTest`;
   - `TocSentinelServiceTest`, `TocAnomalyDetectorTest`, `TocOptimizerTest`, `QualityMetricsControllerTest`, `ProjectEventLogRetentionServiceTest`.
3. Зафиксировать форму образца и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md`.
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
