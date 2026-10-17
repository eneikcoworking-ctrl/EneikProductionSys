# Директивы и наряд для L2 (2026-10-17 09:54 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущий Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: экраны, марка, геометрия и дизайн-система») полностью заверен и закрыт инженером (коммит `b7ad274d`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXX–XXXVI («Клиент к предсказателю», «Разбор чужого репозитория», «Завод репозиториев», «Пульт управления», «Внутренний вход наблюдателя», «Выбор аккаунта», «Хранилище задач»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Секция XXX — Клиент к предсказателю: единственная дверь фабрики к внешней модели (9 тестов, 1 сьют):**
       - `MLPredictionServiceClientTest` (9): единственная точка выхода фабрики к внешней ML-модели, валидация контрактов и устойчивость к сетевым деградациям (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Секция XXXI — Разбор чужого репозитория: неудача фабрики не становится фактом о заказчике (15 тестов, 3 сьюта):**
       - `RepositoryStackAnalyzerTest` (6), `OnboardingAuditServiceTest` (5), `OnboardingAuditServiceFalsificationTest` (4): демаркация внутренних сбоев фабрики от фактов о заказчике (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXII — Завод репозиториев: адрес существует только после GitHub-свидетельства (22 теста, 5 сьютов):**
       - `GitHubProjectFactoryClientTest` (3), `LinearProjectFactoryClientTest` (4), `ProjectFactoryServiceTest` (4), `ProjectFactoryServiceFalsificationTest` (6), `ProjectWorkspaceFactoryServiceFalsificationTest` (5): физическое существование репозитория подтверждается строго внешним API-свидетельством (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXIII — Пульт управления: готовность нельзя объявить без клиентского свидетельства (8 тестов, 2 сьюта):**
       - `CommandDashboardControllerTest` (4), `CommandDashboardServiceTest` (4): готовность проекта не может быть объявлена без клиентского свидетельства, многозначная логика готовности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Секция XXXIV — Внутренний вход наблюдателя: запрет является исполнимой границей (45 тестов, 3 сьюта):**
       - `InternalGeminiObserverControllerTest` (16), `InternalGeminiObserverSecurityIntegrationTest` (9), `ApiAuthorizationInterceptorTest` (20): институциональная защита маршрутов внутреннего наблюдателя как жесткая исполнительная граница (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Секция XXXV — Выбор аккаунта: наказание порядком, а не исключением (60 тестов, 5 сьютов):**
       - `AccountRepositoryIntegrationTest` (8), `AccountRepositoryFalsificationTest` (8), `AccountHealthServiceTest` (31), `AccountHealthServiceLaw14Test` (7), `AccountHealthServiceFalsificationTest` (6): наказание аккаунтов порядком ротации (`refusal run`) без вылета фатальных исключений (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006);
     * **Секция XXXVI — Хранилище задач: запрет, стоящий на обоих уровнях (15 тестов, 3 сьюта):**
       - `TaskRepositoryIntegrationTest` (4), `TaskEntityLaw20Test` (6), `TaskEntityLaw1CarrierTest` (5): неизменяемость терминальных статусов по Закону 20 и сохранение каузального следа задач-носителей по Закону 1 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов онбординга (`OnboardingAuditServiceFalsificationTest`), фабрики проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), хранилища и здоровья аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение надежности клиента ML-предсказателя (`MLPredictionServiceClientTest`), разделение фактов о заказчике и внутренних сбоев фабрики, локально выученные de re лимиты аккаунтов вместо глобальных эвристик.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Институциональная защита маршрутов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), наказание аккаунтов упорядочиванием вместо исключений (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING`), запрет мутации терминальных статусов задач по Закону 20.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Категориальная демаркация: сбой внутренней инфраструктуры или инструментов фабрики не может приписываться как свойство проекта заказчика.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap]:**
  * Многозначная логика готовности на пульте управления и демаркация отсутствия данных от ошибки.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Телеметрия без побочных эффектов при мониторинге и непрерывное сохранение каузального следа задач-носителей.

---

## 3. Протокол исполнения для L2

1. Провести единый непрерывный пакетный прогон 22 тест-сьютов Кластера Секций XXX–XXXVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
2. Убедиться в достижении целевого результата: **174/174 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Проверить инвариант: **строго 0 строк изменений рабочего кода фабрики**.
4. Актуализировать строки `*Живое:*` и итоговые блоки Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сделать семантический коммит:
   ```bash
   git commit -m "feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
