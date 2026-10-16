# Директивы и наряд для L2 (2026-10-16 19:56 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Кластер Секций XXVI, XXVII и XXVIII («Служба контекста, Операционная правда и Внешняя AI-граница») успешно заверен инженером (коммит `c4c34d41`, 215/215 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Кластера Секций XXX, XXXI, XXXII, XXXIII, XXXIV, XXXV и XXXVI («Внешние клиенты, Разбор чужого репозитория, Завод репозиториев, Командный пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач»)** целиком:
     * **Группа 1: Секция XXX — Клиент к предсказателю (ML-граница фабрики):**
       - `MLPredictionServiceClientTest` (9/9 green): fail-safe клиент сайдкара fastembed/onnx, предохранитель от фиктивных векторов, тайм-аутные заслоны (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XXXI — Разбор чужого репозитория (неудача фабрики не становится фактом о заказчике):**
       - `RepositoryStackAnalyzerTest` (6/6 green), `OnboardingAuditServiceTest` (9/9 green), `OnboardingAuditServiceFalsificationTest` (6/6 green): категорический запрет трансляции ошибок парсинга репозитория в дисквалифицирующие факты о клиенте (`GILBERT_RAYL_01_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XXXII — Завод репозиториев (адрес существует только после GitHub-свидетельства):**
       - `GitHubProjectFactoryClientTest` (3/3 green), `LinearProjectFactoryClientTest` (7/7 green), `ProjectFactoryServiceTest` (4/4 green), `ProjectFactoryServiceFalsificationTest` (6/6 green), `ProjectWorkspaceFactoryServiceFalsificationTest` (5/5 green): жесткая онтологическая фиксация репозитория — адрес существует только после получения реального свидетельства из GitHub/Linear API (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4: Секция XXXIII — Пульт управления (готовность нельзя объявить без клиентского свидетельства):**
       - `CommandDashboardControllerTest` (4/4 green), `CommandDashboardServiceTest` (4/4 green): оперативная готовность не объявляется без внешнего подтверждения, чистый режим чтения UI без мутаций (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
     * **Группа 5: Секция XXXIV — Внутренний вход наблюдателя (запрет является исполнимой границей):**
       - `InternalGeminiObserverControllerTest` (16/16 green), `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `ApiAuthorizationInterceptorTest` (4/4 green): деонтический заслон API-ключа, отказ 401/403 при несанкционированном доступе (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 6: Секция XXXV — Выбор аккаунта (наказание порядком, а не исключением):**
       - `AccountRepositoryIntegrationTest` (15/15 green), `AccountRepositoryFalsificationTest` (12/12 green), `AccountHealthServiceTest` (20/20 green), `AccountHealthServiceLaw14Test` (6/6 green), `AccountHealthServiceFalsificationTest` (6/6 green): алгоритм мягкого деградационного упорядочивания аккаунтов по здоровью без жесткого выкашивания из пула (`DZHOZEF_RAZ_06_PENALTY_AS_ORDERING` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 7: Секция XXXVI — Хранилище задач (запрет, стоящий на обоих уровнях):**
       - `TaskRepositoryIntegrationTest` (15/15 green), `TaskEntityLaw20Test` (6/6 green), `TaskEntityLaw1CarrierTest` (5/5 green): двухуровневый инвариант (в JPA-сущности и в базе данных) на переходы состояний и изоляцию задач-носителей (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны онбординга (`OnboardingAuditServiceFalsificationTest`), завода проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), здоровья аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`) и законов задач-носителей (`TaskEntityLaw20Test`, `TaskEntityLaw1CarrierTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции и надежная цепь вывода: изоляция происхождения ML-векторов (`MLPredictionServiceClient`), разделение сущностей и проекций при выборе аккаунтов (`AccountHealthService`), цепочка свидетельств создания репозитория только после фиксации на GitHub/Linear.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_06_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические границы доступа к внутренним поверхностям (`ApiAuthorizationInterceptor`, `InternalGeminiObserverSecurityIntegrationTest`);
  * Наказание аккаунтов порядком и понижением ранжирования, а не аварийным исключением (`AccountHealthServiceLaw14Test`);
  * Двухуровневый запрет на мутацию задач (`TaskRepositoryIntegrationTest`).
- **`GILBERT_RAYL_01_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Запрет трансляции внутренних ошибок анализатора стека в ложные вердикты о заказчике (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная таблица истинности состояний пульта управления и статусов задач.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия командного пульта: чистый режим считывания без паразитных мутаций базы данных.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XXX–XXXVI в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=MLPredictionServiceClientTest,RepositoryStackAnalyzerTest,OnboardingAuditServiceTest,OnboardingAuditServiceFalsificationTest,GitHubProjectFactoryClientTest,LinearProjectFactoryClientTest,ProjectFactoryServiceTest,ProjectFactoryServiceFalsificationTest,ProjectWorkspaceFactoryServiceFalsificationTest,CommandDashboardControllerTest,CommandDashboardServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,ApiAuthorizationInterceptorTest,AccountRepositoryIntegrationTest,AccountRepositoryFalsificationTest,AccountHealthServiceTest,AccountHealthServiceLaw14Test,AccountHealthServiceFalsificationTest,TaskRepositoryIntegrationTest,TaskEntityLaw20Test,TaskEntityLaw1CarrierTest
   ```
2. Подтвердить инвариант Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Зафиксировать такт семантическим коммитом: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)`.
