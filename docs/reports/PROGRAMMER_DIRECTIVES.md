# Директивы и наряд для L2 (2026-10-17 08:10 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна: экраны, марка, геометрия и дизайн-система») полностью заверен инженером (коммит `1f375589`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXX–XXXVI («Внешние клиенты, наблюдатель, разбор чужого репозитория, пульт готовности, выбор аккаунта, хранилище задач и Законы 1, 14, 20»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1: Секция XXX — Клиент к предсказателю (9 тестов, 1 сьют):**
       - `MLPredictionServiceClientTest` (9 тестов): единственная дверь фабрики к внешней модели, circuit-breaker при деградации сайдкара, защита от сбоев оркестрации (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 2: Секция XXXI — Разбор чужого репозитория (15 тестов, 3 сьюта):**
       - `RepositoryStackAnalyzerTest` (6 тестов), `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): внутренняя неудача фабрики (сеть, токен, недоступность) не становится фактом о заказчике, 3-значный статус инспекции, строгая демаркация категорий (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 3: Секция XXXII — Завод репозиториев (22 теста, 5 сьютов):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста), `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): адрес репозитория возникает только по факту физического свидетельства GitHub (201 create или verified 200 на 422), при сбое возвращается `null` без подстановки фантомных URL (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 4: Секция XXXIII — Пульт управления фабрикой (8 тестов, 2 сьюта):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): 5-значная конъюнкция Клиини, запрет объявления готовности `ready` без клиентского свидетельства приемки (`client_acceptance_traversals` с `walked_by='client'`), сохранение трехзначной логики Белнапа `unknown` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя и авторизация API (45 тестов, 3 сьюта):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтический запрет неавторизованных мутаций через внутренний вход наблюдателя, институциональный аудит актора (`AuditCallerResolver`), возврат `UNDETERMINED_PROJECT` вместо 500 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6: Секция XXXV — Выбор аккаунта и наказание порядком (60 тестов, 5 сьютов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов), `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание порядком ротации (`refusal run`), а не исключением из пула, атомарный CAS-захват (`FOR UPDATE SKIP LOCKED`), вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 7: Секция XXXVI — Хранилище задач, Закон 20 и Закон 1 (15 тестов, 3 сьюта):**
       - `TaskRepositoryIntegrationTest` (4 теста), `TaskEntityLaw20Test` (6 тестов), `TaskEntityLaw1CarrierTest` (5 тестов): неизменяемость терминальных статусов (Закон 20), деонтический запрет перезаписи на уровне JPA-сущности (`TaskEntity.setStatus`) и на уровне SQL-репозитория (`TaskRepository.writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), динамическая синхронизация признака carrier (Закон 1) (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификация онбординга (`OnboardingAuditServiceFalsificationTest`), завода проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), здоровья аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`), терминальных статусов Закона 20 (`TaskEntityLaw20Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение надежности ML-предсказателя (`MLPredictionServiceClientTest`), разделение фактов о заказчике и внутренних сбоев фабрики, локально выученные de re лимиты аккаунтов вместо глобальных эвристик.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), наказание аккаунтов порядком ротации (`refusal run`), неизменяемость терминальных статусов (Закон 20).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Демаркация категорий: внутренняя сетевая или авторизационная ошибка фабрики никогда не проецируется как дефект заказчика (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth value collapse / Belnap]:**
  * 3-значный статус онбординга и 5-значная конъюнкция Клиини в пульте готовности (`CommandDashboardServiceTest`).
- **`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Noë]:**
  * Каузальный след переходов статусов задач и признака carrier по Закону 1 (`TaskEntityLaw1CarrierTest`).

---

## 3. Критерий закрытия такта инженером L2

1. Прогон всех 22 тест-сьютов Кластера Секций XXX–XXXVI в едином пакетном запуске внутри контейнера Docker Maven 3.9.9 Temurin-21 (`-m 2g`): результат **174/174 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
2. Строго **0 строк изменений рабочего кода** (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Фиксация результата в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк) и семантический коммит в репозиторий фабрики.
