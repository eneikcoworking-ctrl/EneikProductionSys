# Директивы и наряд для L2 (2026-10-17 08:46 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 предписаний L2» и «Цех дизайна: экраны, марка, геометрия») полностью заверен и закрыт инженером (коммит `d72a1b21`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Внешние клиенты предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1: Секция XXX и XXXI — Внешние клиенты предсказателя и Разбор чужого репозитория (24 теста, 4 сьюта):**
       - `MLPredictionServiceClientTest` (9 тестов): сохранение цепочки происхождения признаков ML (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `RepositoryStackAnalyzerTest` (6 тестов): изоляция категориальных ошибок стека без деградации фабрики (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): попперовская фальсификация онбординга (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 2: Секция XXXII и XXXIII — Завод репозиториев и Пульт управления (30 тестов, 7 сьютов):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста), `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): фальсификационный заслон провижининга и топология воркспейса (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): 5-значная конъюнкция Клиини пульта готовности без фантомных URL (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 3: Секция XXXIV и XXXV — Внутренний вход наблюдателя и Выбор аккаунта (105 тестов, 8 сьютов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтическая защита эндпоинтов и заслон авторизации (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов): целостность хранилища аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание аккаунтов порядком ротации вместо исключений по Закону 14 (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4: Секция XXXVI — Хранилище задач, Закон 20 и Закон 1 (15 тестов, 3 сьюта):**
       - `TaskRepositoryIntegrationTest` (4 теста): инварианты хранилища задач (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `TaskEntityLaw20Test` (6 тестов): деонтическая неизменяемость терминальных статусов по Закону 20 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TaskEntityLaw1CarrierTest` (5 тестов): каузальный след и изоляция задач-носителей по Закону 1 (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
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

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
```

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 22 тест-сьютов Кластера Секций XXX–XXXVI (174 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**174/174 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
