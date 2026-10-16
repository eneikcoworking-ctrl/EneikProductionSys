# Директивы и наряд для L2 (2026-10-16 15:35 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и AI-граница фабрики») успешно заверены инженером (коммит `8e9e118`, 215/215 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XXX–XXXVI («Клиент к предсказателю, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач»)** целиком (18 тест-сьютов, 136 тестов в едином Docker Maven прогоне):
     * **Группа 1: Секция XXX — Клиент к предсказателю (`MLPredictionServiceClient`, 9 тестов):**
       - `MLPredictionServiceClientTest` (9/9): fail-safe клиент к сайдкару FastEmbed, breaker-предохранитель, возврат `null` при сбоях (запрет фиктивных/нулевых векторов), сохранение sentinel-недоступности в `chatCritical` без утечек в сырой Gemini (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 2: Секция XXXI — Разбор чужого репозитория (`RepositoryStackAnalyzer` & `OnboardingAuditService`, 15 тестов):**
       - `RepositoryStackAnalyzerTest` (6/6), `OnboardingAuditServiceTest` (5/5), `OnboardingAuditServiceFalsificationTest` (4/4): 3-значный статус инспекции `InspectionStatus` (`YES`, `NO`, `UNCHECKED`), запрет категориальной ошибки подмены факта о клиенте сбоем авторизации фабрики (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3: Секция XXXII — Завод репозиториев (`GitHubProjectFactoryClient` & `ProjectFactoryService`, 22 теста):**
       - `GitHubProjectFactoryClientTest` (3/3), `LinearProjectFactoryClientTest` (4/4), `ProjectFactoryServiceTest` (4/4), `ProjectFactoryServiceFalsificationTest` (6/6), `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5): URL и id репозитория появляются только по физическому свидетельству GitHub (201 create или verified 200 на 422), при сбое возвращается `null` без подстановки фантомных URL, топологическая изоляция воркспейса (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 4: Секция XXXIII — Пульт управления (`CommandDashboardService` & Controller, 8 тестов):**
       - `CommandDashboardControllerTest` (4/4), `CommandDashboardServiceTest` (4/4): 5-значная логика Клиини, категорический запрет объявления готовности `ready` без клиентского свидетельства приемки (`client_acceptance_traversals` с `walked_by='client'`), 3-значная логика Белнапа `unknown` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя (`ApiAuthorizationInterceptor`, 20 тестов):**
       - `ApiAuthorizationInterceptorTest` (20/20): деонтический запрет неавторизованных мутаций через внутренний вход наблюдателя, защита сервисных эндпоинтов авторизацией и аудит вызывающего (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6: Секция XXXV — Выбор аккаунта (`AccountHealthService` & `AccountRepository`, 47 тестов):**
       - `AccountHealthServiceTest` (31/31), `AccountRepositoryFalsificationTest` (8/8), `AccountRepositoryIntegrationTest` (8/8): наказание порядком ротации (`refusal run`), а не исключением из пула, атомарный CAS-захват (`FOR UPDATE SKIP LOCKED`), вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 7: Секция XXXVI — Хранилище задач (`TaskEntity` & `TaskRepository`, 15 тестов):**
       - `TaskEntityLaw20Test` (6/6), `TaskEntityLaw1CarrierTest` (5/5), `TaskRepositoryIntegrationTest` (4/4): неизменяемость терминальных статусов (Закон 20), деонтический запрет перезаписи на уровне сущности (`TaskEntity.setStatus`) и на уровне SQL-репозитория (`TaskRepository.writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), гарантирующий защиту от stale-read воскрешения (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны на стороне предсказателя, завода репозиториев (запрет фантомных URL), пульта управления (запрет готовности без свидетельств) и хранилища задач (неизменяемость терминалов).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: сохранение локально выученных лимитов аккаунтов de re, четкое разграничение сервисного уровня и хранилищной персистентности.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Наказание аккаунтов порядком ротации (`refusal run`), а не исключением из пула (`PENALTY_AS_ORDERING`);
  * Защита сервисных эндпоинтов авторизацией и институциональный аудит (`PROHIBITION_AS_CODE`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Запрет подмены категорий: ошибка авторизации фабрики к репозиторию клиента не может записываться как факт об отсутствии тестов у клиента.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная логика истинности в анализе репозиториев и готовности пульта управления (`unknown / unchecked != false / failed`).
- **`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` & `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Noë & Wittgenstein]:**
  * Каузальный след процессов в рантайме и запрет stale-read перезаписи состояния задач; объективная телеметрия базы превыше словесных нарративов.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXX–XXXVI в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,ApiAuthorizationInterceptorTest,AccountHealthServiceTest,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest,TaskRepositoryIntegrationTest`
2. Зафиксировать результат (136/136 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
4. Зафиксировать такт семантическим коммитом: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
