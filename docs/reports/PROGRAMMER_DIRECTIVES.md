# Директивы и наряд для L2 (2026-10-17 14:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, марка, геометрия и дизайн-система») полностью подтверждён и закрыт инженером (коммит `05c09a6a`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:30 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, brownfield-разбор чужого репозитория, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач, Закон 20 и Закон 1»)** целиком (**175 тестов в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1 — Секция XXX: Клиент к предсказателю (1 сьют, 9 тестов):**
       - `MLPredictionServiceClientTest` (9 тестов): деонтическая защита пограничного HTTP-клиента к ML-сайдкару предсказаний, строгий контракт форматов и изоляция отказов инференса (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 2 — Секция XXXI: Разбор чужого репозитория (3 сьюта, 21 тест):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (9 тестов), `OnboardingAuditServiceFalsificationTest` (6 тестов): строгий онбординг внешних проектов, фальсификационная проверка граничных условий и устойчивости к невалидным манифестам (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 3 — Секция XXXII: Завод репозиториев (5 сьютов, 48 тестов):**
       - `GitHubProjectFactoryClientTest` (11 тестов), `LinearProjectFactoryClientTest` (13 тестов): надежные свидетельства внешних интеграций GitHub и Linear без фантомных записей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProjectFactoryServiceTest` (11 тестов), `ProjectFactoryServiceFalsificationTest` (7 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (6 тестов): фальсификационный заслон провижининга фабрики проектов, изоляция рабочих областей по Закону 26 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
     * **Группа 4 — Секция XXXIII: Пульт управления готовностью (2 сьюта, 12 тестов):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (8 тестов): многозначная конъюнкция истинности готовности в CommandDashboard, категорический запрет фиктивных статусов без эмпирического подтверждения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 5 — Секция XXXIV: Внутренний вход наблюдателя (3 сьюта, 30 тестов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (5 тестов): деонтическая защита сервисных эндпоинтов внутреннего наблюдателя (строгие 401/403/404), изоляция служебных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 6 — Секция XXXV: Выбор аккаунта и наказание порядком (5 сьютов, 40 тестов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов): атомарный захват аккаунтов (`FOR UPDATE SKIP LOCKED`), фальсификационная проверка целостности пула (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (12 тестов), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (5 тестов): наказание порядком ротации (`refusal run`), а не исключением из пула, вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 7 — Секция XXXVI: Хранилище задач, Закон 20 и Закон 1 (3 сьюта, 15 тестов):**
       - `TaskRepositoryIntegrationTest` (4 теста): институциональная проверка ограничений хранилища (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TaskEntityLaw20Test` (6 тестов): неизменяемость терминальных статусов по Закону 20 на уровне JPA-сущности (`TaskEntity.setStatus`) и SQL-репозитория (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `TaskEntityLaw1CarrierTest` (5 тестов): динамическая синхронизация признака carrier (Закон 1) (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D009, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов онбординга (`OnboardingAuditServiceFalsificationTest`), провижининга фабрики проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), пула аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`) и хранилища задач (`TaskEntityLaw20Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности происхождения данных: изоляция клиентских репозиториев от внутренних фабричных метаданных, надежные свидетельства GitHub API, точная атрибуция здоровья аккаунтов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов внутреннего наблюдателя (401/403/404), наказание аккаунтов порядком в очереди вместо исключения из пула, институциональная неизменяемость терминальных статусов задач (Закон 20).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Ошибка категории: сбой инфраструктуры фабрики (нехватка прав, лимиты сети) никогда не интерпретируется как факт о клиентском коде («нет сборки», «нет тестов»).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Representation drift / Belnap]:**
  * 5-значная конъюнкция истинности готовности в CommandDashboard; категорический запрет фиктивных/предварительных URL репозитория до подтверждённого свидетельства создания GitHub/Linear.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия: статус готовности строится по объективным клиентским свидетельствам, а не самоотчетам; каузальный след переходов жизненного цикла задач в БД.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
