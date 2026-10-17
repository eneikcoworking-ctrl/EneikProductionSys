# Директивы и наряд для L2 (2026-10-17 11:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна: экраны, марка, геометрия и дизайн-система») полностью заверен и закрыт инженером (коммит `ac1813c1`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:21 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, разбор чужого репозитория, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Секция XXX — Клиент к предсказателю (1 сьют, 9 тестов):**
       - `MLPredictionServiceClientTest` (9 тестов): единственная дверь фабрики к внешней модели, устойчивость к сбоям сайдкара (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Секция XXXI — Разбор чужого репозитория и аудит онбординга (3 сьюта, 15 тестов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): ошибка инструментов фабрики не может приписываться как свойство проекта заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): попперовская фальсификационная проверка онбординга чужих проектов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXII — Завод репозиториев (5 сьютов, 22 теста):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста), `ProjectFactoryServiceTest` (4 теста): адрес и репозиторий существуют только после подтверждённого свидетельства платформы (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
       - `ProjectFactoryServiceFalsificationTest` (6 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): фальсификационная устойчивость завода и изоляция воркспейсов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXIII — Пульт управления и готовность продукта (2 сьюта, 8 тестов):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): готовность нельзя объявить без клиентского свидетельства, многозначная логика готовности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Секция XXXIV — Внутренний вход наблюдателя и авторизация (3 сьюта, 45 тестов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): институциональная безопасность внутренних путей наблюдателя, запрет как исполнимая граница (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Секция XXXV — Выбор аккаунта и здоровье квот (5 сьютов, 60 тестов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов): строгое хранилище аккаунтов и фальсификационный заслон (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание порядком ротации, а не исключением по Закону 14 (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006);
     * **Секция XXXVI — Хранилище задач и терминальные инварианты (3 сьюта, 15 тестов):**
       - `TaskRepositoryIntegrationTest` (4 теста): хранилищные инварианты задач;
       - `TaskEntityLaw20Test` (6 тестов): неизменяемость терминальных статусов задач по Закону 20 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TaskEntityLaw1CarrierTest` (5 тестов): сохранение каузального следа задач-носителей по Закону 1 (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D009, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификационная оснастка: строгая проверка на отрицательных и искажённых входах в онбординге (`OnboardingAuditServiceFalsificationTest`), фабрике проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), хранилище аккаунтов (`AccountRepositoryFalsificationTest`) и здоровье квот (`AccountHealthServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение каузальной надежности клиента ML-предсказателя (`MLPredictionServiceClientTest`), изоляция сбоев внутренней инфраструктуры фабрики от фактов о заказчике, деривативный срез и надежное отслеживание квот аккаунтов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), наказание аккаунтов порядком ротации вместо исключений (`refusal run` по Закону 14), деонтическая неизменяемость терминальных статусов задач по Закону 20 (`TaskEntityLaw20Test`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Semantic drift / Ryle]:**
  * Демаркация категорий: внутренняя сетевая или авторизационная ошибка фабрики никогда не проецируется как дефект заказчика (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D009 & D012 / Belnap]:**
  * Оракул запрета подмены: адрес репозитория не возникает до реального подтверждения платформы (`GitHubProjectFactoryClientTest`). Многозначная решётка истинности готовности на командном пульте (`CommandDashboardServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Честное отражение фактов без декоративных зеркал (`CommandDashboardServiceTest`) и непрерывный каузальный след переходов статусов задач и признака carrier по Закону 1 (`TaskEntityLaw1CarrierTest`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
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

1. Провести единый непрерывный пакетный прогон 22 тест-сьютов Кластера Секций XXX–XXXVI (174 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**174/174 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом:
   ```bash
   git commit -m "feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
