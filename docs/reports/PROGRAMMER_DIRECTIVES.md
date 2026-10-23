# Директивы и наряд для L2 (2026-10-23 11:03 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2» и «Цех дизайна: экраны, марка, геометрия, дизайн-система») полностью заверен и закрыт инженером (коммит `044e1c0c`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:45 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXX–XXXVI («Клиент предсказателя, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта, Хранилище задач»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1 — Клиент к предсказателю и ML-граница (1 сьют, 9 тестов):**
       - `MLPredictionServiceClientTest` (9 тестов): каузальная цепь контрактов ML-предсказателя и изоляция откатов (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 2 — Разбор чужого репозитория и аудит онбординга (3 сьюта, 15 тестов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): попперовская фальсификация критериев онбординга (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3 — Завод репозиториев и топология рабочих областей (5 сьютов, 22 теста):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста): надежность цепочки свидетельств интеграций (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов): заслон от фиктивных путей (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
       - `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): топологическая изоляция рабочих пространств по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 4 — Пульт управления и свидетельства готовности (2 сьюта, 8 тестов):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): многозначная таблица истинности готовности пульта без ложных деклараций (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 5 — Внутренний вход наблюдателя и заслон безопасности (3 сьюта, 45 тестов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтическая граница доступа к наблюдателю, институциональный запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 6 — Выбор аккаунта, Закон 14 и здоровье токенов (5 сьютов, 60 тестов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов): фальсификационный заслон хранилища аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание аккаунтов порядком в ротации по Закону 14 и учет лимитов de re (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 7 — Хранилище задач, Закон 20 и Закон 1 (3 сьюта, 15 тестов):**
       - `TaskRepositoryIntegrationTest` (4 теста): целостность переходов состояний задач в JPA;
       - `TaskEntityLaw20Test` (6 тестов): деонтическая неизменяемость терминальных статусов по Закону 20 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TaskEntityLaw1CarrierTest` (5 тестов): каузальный след carrier-задач без искажения метрик потока (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация критериев онбординга (`OnboardingAuditServiceFalsificationTest`), завода проектов (`ProjectFactoryServiceFalsificationTest`), аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`) и терминальных статусов задач (`TaskEntityLaw20Test`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции в ротации аккаунтов по квотам de re; надежная цепочка вызовов к ML-предсказателю и внешним клиентам GitHub/Linear.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая граница доступа к сервисам наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), неизменяемость терминальных статусов задач по Закону 20 и наказание деградировавших аккаунтов порядком ротации по Закону 14 без выброса ложных исключений.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Varzi]:**
  * Топологическая изоляция рабочих пространств проектов и строгое соблюдение Закона 26.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Демаркация внутренних сбоев анализатора стека от фактов о клиентском репозитории (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 / D009 Belnap]:**
  * 4-значная таблица истинности готовности пульта управления; заслон от фиктивных воркспейсов проектов.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистая телеметрия готовности пульта без сайд-эффектов; каузальный след carrier-задач по Закону 1 без искажения DPMO.

---

## 3. Критерии приемки наряда L2

1. Провести единый непрерывный пакетный прогон всех 22 тест-сьютов Кластера Секций XXX–XXXVI в Docker Maven 3.9.9 Temurin-21 (-m 2g):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
2. Подтвердить 100% результат: **174/174 green, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
3. Соблюсти деонтический запрет: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. Сделать семантический коммит в фабрику: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
7. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
