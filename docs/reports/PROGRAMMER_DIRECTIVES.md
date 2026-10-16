# Директивы и наряд для L2 (2026-10-16 11:51 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и Внешняя AI-граница») успешно заверены инженером (коммит `6f98f25`, 215/215 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI** целиком (18 тест-сьютов, 136 тестов в едином пакетном прогоне Docker Maven):
     * **Секция XXX: Клиент к предсказателю (9 тестов, 1 сьют):**
       - `MLPredictionServiceClientTest` (9/9): fail-safe клиент к сайдкару FastEmbed, breaker-предохранитель, запрет фиктивных/нулевых mock-векторов при сбое, сохранение sentinel-недоступности в `chatCritical` без утечек в сырой Gemini (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Секция XXXI: Разбор чужого репозитория (15 тестов, 3 сьюта):**
       - `RepositoryStackAnalyzerTest` (6/6), `OnboardingAuditServiceTest` (5/5), `OnboardingAuditServiceFalsificationTest` (4/4): трёхзначный статус инспекции `InspectionStatus` (`YES`, `NO`, `UNCHECKED`), сбой авторизации фабрики никогда не превращается в ложный дефект заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Секция XXXII: Завод репозиториев (22 теста, 5 сьютов):**
       - `GitHubProjectFactoryClientTest` (3/3), `LinearProjectFactoryClientTest` (4/4), `ProjectFactoryServiceTest` (4/4), `ProjectFactoryServiceFalsificationTest` (6/6), `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5): адрес репозитория возникает только по факту физического свидетельства GitHub (201 create или verified 200 на 422), при сбое возвращается `null` без подстановки фантомных URL, изоляция топологии путей workspace (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Секция XXXIII: Пульт управления (8 тестов, 2 сьюта):**
       - `CommandDashboardControllerTest` (4/4), `CommandDashboardServiceTest` (4/4): пятизначная конъюнкция Клиини, запрет объявления готовности `ready` без клиентского свидетельства приемки (`client_acceptance_traversals` с `walked_by='client'`), сохранение трёхзначной логики Белнапа `unknown` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Секция XXXIV: Внутренний вход наблюдателя (20 тестов, 1 сьют):**
       - `ApiAuthorizationInterceptorTest` (20/20): деонтический запрет неавторизованных мутаций через внутренний вход наблюдателя (401/403), защита сервисных эндпоинтов авторизацией и аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Секция XXXV: Выбор аккаунта (47 тестов, 3 сьюта):**
       - `AccountHealthServiceTest` (31/31), `AccountRepositoryFalsificationTest` (8/8), `AccountRepositoryIntegrationTest` (8/8): наказание порядком ротации (`refusal run`), а не исключением из пула, атомарный CAS-захват (`FOR UPDATE SKIP LOCKED`), вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Секция XXXVI: Хранилище задач (15 тестов, 3 сьюта):**
       - `TaskEntityLaw20Test` (6/6), `TaskEntityLaw1CarrierTest` (5/5), `TaskRepositoryIntegrationTest` (4/4): неизменяемость терминальных статусов (Закон 20), деонтический запрет перезаписи на уровне сущности (`TaskEntity.setStatus`) и на уровне SQL-репозитория (`TaskRepository.writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), гарантирующий защиту от stale-read воскрешения (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной/идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при повреждении заслонов готовности клиентских артефактов, попытках подстановки фантомных URL репозиториев или пробитии защитных breakers.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: разделение фабричного, продуктового и поставочного уровней суждения; сохранение происхождения свидетельств от первичных HTTP-ответов до полей сущностей без подмены догадками.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Наказание аккаунтов порядком ротации (`refusal run`), а не исключением из пула; деонтический барьер на эндпоинты внутреннего наблюдателя.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * Четырёхзначная логика: явное разграничение состояний (`undetermined != blocked != trusted`, `unknown != failure`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Категориальное разделение: внутренняя техническая ошибка авторизации фабрики никогда не записывается как факт о дефекте кодовой базы заказчика.
- **`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` & `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Noë & Wittgenstein]:**
  * Принцип анти-зеркала и трассировка процесса: неизменяемость терминальных статусов задач (Закон 20), защита от перезаписи при stale reads на уровнях Java Entity и SQL Repository.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXX–XXXVI (136 тестов, 18 сьютов в Docker Maven):**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,ApiAuthorizationInterceptorTest,AccountHealthServiceTest,AccountRepositoryFalsificationTest,AccountRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest,TaskRepositoryIntegrationTest
   ```
2. **Соблюдение инварианта суверенитета и правила Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
   - Фиксация статуса в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. **Отчёт и фиксация:**
   - Обновить `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15–20 строк).
   - Сделать семантический коммит в фабрику: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
