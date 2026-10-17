# Директивы и наряд для L2 (2026-10-17 11:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XVI и XXIX («Архитектурные предписания L2, Безопасность, ТОС и Цех дизайна») полностью заверены и закрыты инженером (коммит `96d78797`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг макро-цикла — **Кластер Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, brownfield-разбор репозитория клиента, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач и инварианты Закона 20 и Закона 1»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1 — Секция XXX: Клиент к предсказателю и внешняя ML-граница (9 тестов, 1 сьют):**
       - `MLPredictionServiceClientTest` (9 тестов): fail-safe клиент к сайдкару FastEmbed, circuit breaker предохранитель, деонтический запрет фиктивных mock-векторов при сбое, сохранение sentinel-недоступности в `chatCritical` без утечек в сырой Gemini (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 2 — Секция XXXI: Brownfield-разбор чужого репозитория (15 тестов, 3 сьюта):**
       - `RepositoryStackAnalyzerTest` (6 тестов), `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): трехзначный статус инспекции `InspectionStatus` (`YES`, `NO`, `UNCHECKED`), сбой авторизации или сети фабрики категорически не превращается в ложный дефект заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 3 — Секция XXXII: Завод репозиториев (22 теста, 5 сьютов):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста), `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): адрес репозитория возникает только по факту физического свидетельства GitHub (201 create или verified 200 на 422), при сбое возвращается `null` без подстановки фантомных URL (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
     * **Группа 4 — Секция XXXIII: Пульт управления фабрикой (8 тестов, 2 сьюта):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): 5-значная конъюнкция Клиини, запрет объявления готовности `ready` без клиентского свидетельства приемки (`client_acceptance_traversals` с `walked_by='client'`), сохранение трехзначной логики Белнапа `unknown` (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 5 — Секция XXXIV: Внутренний вход наблюдателя и авторизация API (45 тестов, 3 сьюта):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтический запрет неавторизованных мутаций через внутренний вход наблюдателя, защита сервисных путей авторизацией, институциональный аудит актора (`AuditCallerResolver`), безопасные GET-эндпоинты и возврат `UNDETERMINED_PROJECT` вместо 500 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6 — Секция XXXV: Выбор аккаунта и наказание порядком (60 тестов, 5 сьютов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов), `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание порядком ротации (`refusal run`), а не исключением из пула, атомарный CAS-захват (`FOR UPDATE SKIP LOCKED`), вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 7 — Секция XXXVI: Хранилище задач, Закон 20 и Закон 1 (15 тестов, 3 сьюта):**
       - `TaskRepositoryIntegrationTest` (4 теста), `TaskEntityLaw20Test` (6 тестов), `TaskEntityLaw1CarrierTest` (5 тестов): неизменяемость терминальных статусов (Закон 20), деонтический запрет перезаписи на уровне JPA-сущности (`TaskEntity.setStatus`) и на уровне SQL-репозитория (`TaskRepository.writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), динамическая синхронизация признака carrier (Закон 1) (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D009, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов онбординга (`OnboardingAuditServiceFalsificationTest`), провижининга фабрики проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), пула аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`) и хранилища задач.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности происхождения данных: изоляция клиентских репозиториев от внутренних фабричных метаданных, надежные свидетельства GitHub API, точная атрибуция здоровья аккаунтов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита сервисных эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), наказание порядком ротации аккаунтов (`refusal run`) вместо исключения из пула, запрет перезаписи терминальных статусов задач (Закон 20).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 Policy contradiction & D009 / Belnap]:**
  * Многозначная таблица истинности (3-значная и 4-значная логика) в пульте управления готовностью и онбординге; оракул запрета подмены физических сущностей фантомными URL/mock-ответами.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Недопущение категориальных ошибок: сбой авторизации или сетевой недоступности фабрики никогда не квалифицируется как дефект в проекте клиента.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия: пульт управления отражает фактические свидетельства приемки, а не самодекларированную готовность; каузальные следы переходов задач.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=MLPredictionServiceClientTest,\
RepositoryStackAnalyzerTest,\
OnboardingAuditServiceTest,\
OnboardingAuditServiceFalsificationTest,\
GitHubProjectFactoryClientTest,\
LinearProjectFactoryClientTest,\
ProjectFactoryServiceTest,\
ProjectFactoryServiceFalsificationTest,\
ProjectWorkspaceFactoryServiceFalsificationTest,\
CommandDashboardControllerTest,\
CommandDashboardServiceTest,\
InternalGeminiObserverControllerTest,\
InternalGeminiObserverSecurityIntegrationTest,\
ApiAuthorizationInterceptorTest,\
AccountRepositoryIntegrationTest,\
AccountRepositoryFalsificationTest,\
AccountHealthServiceTest,\
AccountHealthServiceLaw14Test,\
AccountHealthServiceFalsificationTest,\
TaskRepositoryIntegrationTest,\
TaskEntityLaw20Test,\
TaskEntityLaw1CarrierTest
```

---

## 4. Ожидаемый результат такта L2

1. Запустить единый непрерывный пакетный прогон 22 тест-сьютов Кластера Секций XXX–XXXVI (174 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**174/174 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: **строго 0 строк изменений рабочего кода фабрики**.
4. Актуализировать строки `*Живое:*` и итоговые блоки Кластера Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сделать семантический коммит:
   ```bash
   git commit -m "feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
