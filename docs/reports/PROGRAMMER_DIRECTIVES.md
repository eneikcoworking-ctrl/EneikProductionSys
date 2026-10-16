# Директивы и наряд для L2 (2026-10-16 21:58 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 предписаний L2» и «Цех дизайна: экраны, марка, геометрия и дизайн-система») полностью заверен инженером (коммит `28c302bd`, 272/272 green в 29 тест-сьютах единого Docker Maven прогона, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Внешние клиенты, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач»)** целиком (**174 теста в едином пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1: Секция XXX — Клиент к предсказателю внешней модели (9 тестов, 1 сьют):**
       - `MLPredictionServiceClientTest` (9/9): fail-safe клиент к сайдкару FastEmbed, circuit breaker, запрет фиктивных/нулевых mock-векторов при сбое, сохранение sentinel-недоступности в `chatCritical` без утечек в сырой Gemini (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XXXI — Разбор чужого репозитория и аудит онбординга (15 тестов, 3 сьюта):**
       - `RepositoryStackAnalyzerTest` (6/6), `OnboardingAuditServiceTest` (5/5), `OnboardingAuditServiceFalsificationTest` (4/4): трёхзначный статус инспекции `InspectionStatus` (`YES`, `NO`, `UNCHECKED`), принцип Райла: сбой авторизации фабрики никогда не превращается в ложный дефект заказчика («нет тестов», «нет сборки») (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XXXII — Завод репозиториев и рабочие пространства (22 теста, 5 сьютов):**
       - `GitHubProjectFactoryClientTest` (3/3), `LinearProjectFactoryClientTest` (4/4), `ProjectFactoryServiceTest` (4/4), `ProjectFactoryServiceFalsificationTest` (6/6), `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5): адрес репозитория возникает строго по факту свидетельства GitHub (201 create или verified 200 на 422), при сбое возвращается `Optional.empty()` / `null` без подстановки фантомных fallback-URL (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 4: Секция XXXIII — Пульт управления и свидетельства приёмки (8 тестов, 2 сьюта):**
       - `CommandDashboardControllerTest` (4/4), `CommandDashboardServiceTest` (4/4): пятизначная конъюнкция Клиини, запрет объявления готовности `ready` без клиентского свидетельства приёмки (`client_acceptance_traversals` с `walked_by='client'`), сохранение 3-значной логики Белнапа `unknown` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя и авторизация API (45 тестов, 3 сьюта):**
       - `InternalGeminiObserverControllerTest` (16/16), `InternalGeminiObserverSecurityIntegrationTest` (9/9), `ApiAuthorizationInterceptorTest` (20/20): деонтический запрет неавторизованных мутаций через внутренний вход наблюдателя, защита сервисных эндпоинтов авторизацией и институциональный аудит актора (`AuditCallerResolver`), возврат `UNDETERMINED_PROJECT` вместо 500 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XXXV — Выбор аккаунта и наказание порядком (60 тестов, 5 сьютов):**
       - `AccountRepositoryIntegrationTest` (8/8), `AccountRepositoryFalsificationTest` (8/8), `AccountHealthServiceTest` (31/31), `AccountHealthServiceLaw14Test` (7/7), `AccountHealthServiceFalsificationTest` (6/6): наказание порядком ротации (`refusal run`), а не исключением из пула, атомарный CAS-захват (`FOR UPDATE SKIP LOCKED`), вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 7: Секция XXXVI — Хранилище задач, Закон 20 и Закон 1 (15 тестов, 3 сьюта):**
       - `TaskRepositoryIntegrationTest` (4/4), `TaskEntityLaw20Test` (6/6), `TaskEntityLaw1CarrierTest` (5/5): неизменяемость терминальных статусов (Закон 20), деонтический запрет перезаписи на уровне JPA-сущности (`TaskEntity.setStatus`) и на уровне SQL-репозитория (`TaskRepository.writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), динамическая синхронизация признака carrier (Закон 1) (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: заслоны фальсификации онбординга (`OnboardingAuditServiceFalsificationTest`), завода проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), хранилища аккаунтов (`AccountRepositoryFalsificationTest`) и здоровья аккаунтов (`AccountHealthServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности свидетельств: изоляция рабочих пространств проектов, строгое происхождение контентных ключей, расчет деградации здоровья аккаунтов de re без смешения уровней моделирования.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Наказание аккаунтов порядком ротации (`penalty as ordering`), а не уничтожением слотов; деонтический запрет неавторизованных запросов к внутреннему наблюдателю (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`); неизменяемость терминальных статусов (Закон 20).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Отсечение категориальной ошибки: внутренний сбой авторизации фабрики никогда не превращается в ложный дефект о кодовой базе заказчика (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Множественная логика истинности: 3-значный статус инспекции репозитория и 5-значная конъюнкция Клиини на пульте управления. Запрет фантомных URL при создании репозиториев (`GitHubProjectFactoryClientTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия: пульт управления отражает реальные приёмочные обходы заказчика (`walked_by='client'`), а каузальный след переходов задач строго фиксирует моменты движения через JPQL `updatedAt`.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXX–XXXVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
2. Подтвердить 100% green результат (174/174 green, 0 failures, 0 errors, 0 skipped, 22 тест-сьюта).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
