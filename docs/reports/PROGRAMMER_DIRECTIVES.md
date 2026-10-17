# Директивы и наряд для L2 (2026-10-17 16:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Архитектурные предписания L2 и Цех дизайна») полностью заверен и закрыт инженером (коммит `7c46c69c`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:13 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, brownfield-разбор чужого репозитория, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач, Закон 20 и Закон 1»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Группа 1: Секция XXX — Клиент к предсказателю и внешняя ML-граница (1 сьют, 9 тестов):**
       - `MLPredictionServiceClientTest` (9 тестов): деонтическая защита пограничного HTTP-клиента к ML-сайдкару предсказаний, строгий контракт форматов и изоляция отказов инференса (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Секция XXXI — Brownfield-разбор чужого репозитория (3 сьюта, 15 тестов):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OnboardingAuditServiceTest` (5 тестов), `OnboardingAuditServiceFalsificationTest` (4 теста): строгий онбординг внешних проектов, фальсификационная проверка граничных условий и устойчивости к невалидным манифестам (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XXXII — Завод репозиториев (5 сьютов, 22 теста):**
       - `GitHubProjectFactoryClientTest` (3 теста), `LinearProjectFactoryClientTest` (4 теста): надежные свидетельства внешних интеграций GitHub и Linear без фантомных записей (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProjectFactoryServiceTest` (4 теста), `ProjectFactoryServiceFalsificationTest` (6 тестов), `ProjectWorkspaceFactoryServiceFalsificationTest` (5 тестов): фальсификационный заслон провижининга фабрики проектов, топологическая изоляция рабочих областей по Закону 26 (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009).
     * **Группа 4: Секция XXXIII — Пульт управления готовностью (2 сьюта, 8 тестов):**
       - `CommandDashboardControllerTest` (4 теста), `CommandDashboardServiceTest` (4 теста): многозначная конъюнкция истинности готовности в CommandDashboard, категорический запрет фиктивных статусов без эмпирического подтверждения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя (3 сьюта, 45 тестов):**
       - `InternalGeminiObserverControllerTest` (16 тестов), `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `ApiAuthorizationInterceptorTest` (20 тестов): деонтическая защита сервисных эндпоинтов внутреннего наблюдателя (строгие 401/403/404), изоляция служебных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 6: Секция XXXV — Выбор аккаунта и наказание порядком (5 сьютов, 60 тестов):**
       - `AccountRepositoryIntegrationTest` (8 тестов), `AccountRepositoryFalsificationTest` (8 тестов): атомарный захват аккаунтов (`FOR UPDATE SKIP LOCKED`), фальсификационная проверка целостности пула (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `AccountHealthServiceTest` (31 тест), `AccountHealthServiceLaw14Test` (7 тестов), `AccountHealthServiceFalsificationTest` (6 тестов): наказание порядком ротации (`refusal run`), а не исключением из пула, вытеснение глобальных догадок локально выученными пределами (de re), освобождение слотов для blocked/done задач (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
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

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D002, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка в `OnboardingAuditServiceFalsificationTest`, `ProjectFactoryServiceFalsificationTest`, `AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest` и `TaskEntityLaw20Test`: поиск контрпримеров, активное опровержение ложно-зелёных исходов и удержание семантических инвариантов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции в `AccountHealthServiceTest` (отсечение смешения локального контекста и глобальных лимитов); каузальная цепь надежности взаимодействия с сайдкарами и внешними системами GitHub/Linear (`MLPredictionServiceClientTest`, `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Авторизационные барьеры безопасности наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), неизменяемость терминальных статусов по Закону 20 (`TaskEntityLaw20Test`) и институциональное наказание порядком ротации (`AccountHealthServiceLaw14Test`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Invalid state / Ryle]:**
  * Отсечение категориальных ошибок при анализе чужого стека (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D012 Belnap 4-valued / D009 Substitution oracle]:**
  * 4-значная решётка истинности Белнэпа для пульта управления (`CommandDashboardControllerTest`, `CommandDashboardServiceTest`) и оракул нелокальной подмены в воркспейсах (`ProjectWorkspaceFactoryServiceFalsificationTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальный контроль истинного состояния рантайма без фиктивных проекций (`CommandDashboardServiceTest`, `TaskEntityLaw1CarrierTest`).

---

## 3. Регламент и формат отчёта L2

1. Провести единый непрерывный пакетный замер всех 22 тест-сьютов Кластера Секций XXX–XXXVI в Docker Maven 3.9.9 Temurin-21 (-m 2g).
2. Убедиться в достижении результата 174/174 green (0 failures, 0 errors, 0 skipped) при строго нулевом изменении рабочего кода фабрики.
3. Актуализировать отметки в `docs/reports/PROGRAMMER_STATE.md` (кратко, до 15-20 строк).
4. Зафиксировать результат коммитом: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
5. По регламенту оператора таймеры не взводятся.
