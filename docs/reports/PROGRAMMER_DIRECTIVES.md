# Директивы и наряд для L2 (2026-10-17 19:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, марка, геометрия и дизайн-система») полностью заверен и закрыт инженером (коммит `2d9de376`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:40 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXX–XXXVI («Клиент предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта, Хранилище задач»)** целиком (**167 тестов в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1: Секция XXX — Клиент к предсказателю и ML-граница (1 сьют, 9 тестов):**
       - `MLPredictionServiceClientTest` (9 тестов): устойчивость к отказам сайдкара предсказаний, валидация контрактов и таймаутов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 2: Секция XXXI — Разбор чужого репозитория и аудит онбординга (3 сьюта, 26 тестов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (15 тестов), `OnboardingAuditServiceFalsificationTest` (5 тестов): попперовская фальсификация критериев онбординга, демаркация ошибок инфраструктуры (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XXXII — Завод репозиториев и топология рабочих областей (5 сьютов, 21 тест):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (3 теста): проверка подлинности внешних свидетельств существования репозиториев (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов): фальсификационный заслон против создания проектов без валидных метаданных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
       - `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): топологическая изоляция рабочих областей проектов по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 4: Секция XXXIII — Пульт управления и свидетельства готовности (2 сьюта, 8 тестов):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): 4-значная решётка истинности Белнэпа для готовности пульта без ложных деклараций (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя и заслон безопасности (3 сьюта, 45 тестов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтическая защита сервисных эндпоинтов, строгий запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 6: Секция XXXV — Выбор аккаунта, Закон 14 и здоровье токенов (5 сьютов, 53 теста):**
       - `AccountRepositoryIntegrationTest` (3 теста), `AccountRepositoryFalsificationTest` (6 тестов): фальсификационная проверка инвариантов хранилища аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание аккаунтов порядком ротации по Закону 14, отсечение деградировавших ключей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 7: Секция XXXVI — Хранилище задач, Закон 20 и Закон 1 (3 сьюта, 15 тестов):**
       - `TaskRepositoryIntegrationTest` (4 теста): институциональная проверка ограничений хранилища (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TaskEntityLaw20Test` (6 тестов): неизменяемость терминальных статусов по Закону 20 на уровне JPA-сущности (`TaskEntity.setStatus`) и SQL-репозитория (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `TaskEntityLaw1CarrierTest` (5 тестов): динамическая синхронизация признака carrier (Закон 1) (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Заслоны фальсификации онбординга (`OnboardingAuditServiceFalsificationTest`), завода проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), хранилища аккаунтов (`AccountRepositoryFalsificationTest`) и здоровья аккаунтов (`AccountHealthServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss / Goldman]:**
  * Надежность цепочки свидетельств внешних систем (`GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`), контракты ML-предсказателя (`MLPredictionServiceClientTest`) и фиксация локально выученных пределов емкости аккаунтов (de re, `AccountHealthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон сервисных эндпоинтов внутреннего наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), наказание аккаунтов порядком в ротации (`AccountHealthServiceLaw14Test`) и неизменяемость терминальных статусов по Закону 20 (`TaskEntityLaw20Test`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Отсечение категориальных ошибок стека без ложных обвинений заказчика (`RepositoryStackAnalyzerTest`): сбои платформы фабрики никогда не записываются как свойства клиентского кода.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap 4-valued]:**
  * Многозначная таблица истинности готовности в CommandDashboard (`CommandDashboardControllerTest`, `CommandDashboardServiceTest`): отсечение фиктивных заявлений о готовности без клиентских подтверждений.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Телеметрия рантайма и статус готовности строго по объективным свидетельствам без самоотчётов; каузальный след переходов статусов в базе данных (`TaskEntityLaw1CarrierTest`).
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Boundary topology / Varzi]:**
  * Топологическая изоляция рабочих областей проектов по Закону 26 (`ProjectWorkspaceFactoryServiceFalsificationTest`).
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D009 Substitution oracle / Belnap]:**
  * Строгий запрет подмены реальных рабочих директорий фиктивными путями (`ProjectFactoryServiceFalsificationTest`).

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить единый непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
6. Таймеры не взводятся по директиве оператора.
